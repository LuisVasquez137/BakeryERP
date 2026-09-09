# Integración ComandaYa → BakeOS (solo Sabreopan)

Documento de arquitectura. v2 — 2026-09-07.
Reemplaza la v1: el alcance se redujo mucho tras las decisiones de Fernando.

---

## 1. Alcance decidido

**Esto es interno de Sabreopan. No es una función de producto.** Ningún otro cliente de
ComandaYa (40 restaurantes) ni de BakeOS ve nada de esto, y las dos apps siguen funcionando
exactamente igual para todos los demás.

Restaurante de Sabreopan en ComandaYa: `restaurantId = cUVIThV5fCkC3PKfDByD`

**Sucursales.** BakeOS tiene **5**; **3** de ellas son Restaurante-Panadería y existen también
en ComandaYa. Las otras 2 son solo panadería. Por eso el canal (`BAKERY` / `RESTAURANT`) es una
dimensión **dentro** de la sucursal y no una sucursal aparte: en esas 3, "cuánto vendió la
sucursal" y "cuánto de eso fue restaurante" son dos preguntas distintas sobre el mismo día y el
mismo local.

| Tema | Decisión |
|---|---|
| Facturación (FEL/DTE) del restaurante | **Se queda en ComandaYa.** BakeOS no factura nada de restaurante |
| Inventario de cocina (ingredientes del restaurante) | **Se queda en ComandaYa.** BakeOS ni lo ve ni lo toca |
| Inventario de insumos de panadería + Kardex de pan | **Se queda en BakeOS**, tal como está hoy |
| Ventas | **Se consolidan en BakeOS**: pan cobrado en el POS de BakeOS + restaurante cobrado en ComandaYa |
| Granularidad del cruce | **Por orden**, no por turno |
| Presupuesto | No se pueden disparar las lecturas de Firestore |

### Qué hace cada app, ya decidido

**ComandaYa** documenta las órdenes, corre el flujo de despacho del restaurante, descuenta el
inventario de ingredientes por receta **de la cocina** (no el de panadería) y factura (FEL). No
maneja arqueo de caja.

**BakeOS** lleva compras, proveedores, insumos de panadería, producción, Kardex de pan, el POS de
pan, **el arqueo de las dos gavetas de cada sucursal** y el consolidado de ventas.

Lo único que cruza es **la venta cerrada**. Nada más.

---

## 2. Hallazgo que cambia la prioridad

**El problema de lecturas ya existe en BakeOS y no lo causa la integración.** Traer las órdenes
del restaurante solo lo haría explotar antes.

`composeApp/src/nonWasmMain/.../FirebaseSaleRepository.kt`:

- **Línea 230** — `aggregateSalesInRange` hace `salesCol(companyId).get()`: **lee la colección
  completa de ventas, sin filtro de fecha**, desde el principio de los tiempos. El rango se
  aplica después, en memoria.
- **Líneas 241-243** — después lanza **una query por cada venta** para traer sus líneas.
- **Líneas 197 y 214** — `observeSalesForSession` y `observeSalesForBranchInRange` ponen un
  **listener sobre TODA la colección de ventas** y filtran en cliente.

Y `HomeViewModel` (líneas 112, 120 y 223) llama a `aggregateSalesInRange` **hasta tres veces**
cada vez que se abre la pantalla de inicio.

### Lo que eso cuesta (estimado, con supuestos explícitos)

Supongamos que en un año BakeOS acumula 100,000 ventas con ~3 líneas cada una:

| Acción | Lecturas | Costo aprox. |
|---|---|---|
| Un `aggregateSalesInRange` | 100,000 encabezados + ~300,000 líneas ≈ **400,000** | ~$0.24 |
| **Abrir HomeScreen una vez** (3 llamadas) | **~1,200,000** | **~$0.72** |
| Lo mismo leyendo resúmenes diarios | 5 sucursales × 30 días = **150** | ~$0.0001 |

*(Firestore ≈ $0.06 por 100k lecturas. Números aproximados, sirven para el orden de magnitud,
no como factura.)*

**Conclusión:** el costo no está en sincronizar, está en leer. Por eso el orden de trabajo se
invierte respecto a lo que uno esperaría — primero se arregla la lectura, después se conecta
ComandaYa.

---

## 3. Cuánto cuesta realmente la sincronización

Supuesto: ~400 órdenes cerradas por día entre las 3 sucursales con restaurante ≈ **12,000
órdenes/mes**.

| Concepto | Volumen/mes | Costo |
|---|---|---|
| Invocaciones del trigger en ComandaYa (~15 escrituras por orden) | ~180,000 | **$0** (capa gratis: 2M) |
| **Lecturas de Firestore para sincronizar** | **0** | **$0** |
| Invocaciones de la función receptora en BakeOS | 12,000 | **$0** (capa gratis) |
| Escrituras en BakeOS (1 venta + ~4 líneas + 1 resumen) | ~72,000 | ~**$0.13** |

**Total de la integración: menos de un dólar al mes.**

La clave del "0 lecturas": un trigger de Firestore **recibe el documento en el payload del
evento**. No cobra lectura. Por eso el push sale gratis y un pull con polling no.

---

## 4. Arquitectura

```
  ComandaYa (orderapp-e0c31)                 BakeOS (bakery-erp-saas)
  ──────────────────────────                 ────────────────────────
  Restaurants/cUVIThV5fCkC3PKfDByD/Orders/{orderId}
            │
            │  trigger onWrite con RUTA LITERAL
            │  (no puede dispararse para otro restaurante)
            ▼
  syncSabreopanSale()
     ¿pasó a venta firme? ──no──▶ return (0 lecturas, 0 escrituras)
            │ sí
            │  arma el DTO neutral
            ▼
      HTTPS + secreto compartido
            └──────────────────────────▶  ingestExternalSale()
                                                │  transacción:
                                                ├─▶ companies/{c}/sales/comandaya_{orderId}
                                                ├─▶ .../lines/*
                                                └─▶ companies/{c}/salesDaily/{branch}_{fecha}
                                                       (delta, no increment ciego)
```

### 4.1 El aislamiento es estructural, no un `if`

El trigger se registra con el id **literal** en la ruta:

```js
functions.firestore
  .document('Restaurants/cUVIThV5fCkC3PKfDByD/Orders/{orderId}')
  .onWrite(...)
```

No es `Restaurants/{restaurantId}/Orders/{orderId}` con un `if` adentro. Con la ruta literal la
función **físicamente no se invoca** para los otros 39 restaurantes: no consume su cuota, no
puede tener un bug que los afecte, no aparece en sus logs. Un `if` dependería de que el filtro
esté bien escrito; esto no depende de nada.

Se despliega sola, sin tocar el resto:
```
firebase deploy --only functions:syncSabreopanSale
```

### 4.2 Por qué la escritura la hace BakeOS y no ComandaYa

Se podría escribir directo en la base de BakeOS desde la función de ComandaYa (con una service
account cruzada) y ahorrarse una función. **No conviene**, por una razón concreta:

> El modelo `Sale` de BakeOS todavía está cambiando — el POS está a medio construir. Si la
> lógica de escritura vive en ComandaYa, **cada cambio de esquema de BakeOS obliga a
> redesplegar la app que le factura a 40 restaurantes que pagan.**

Con el endpoint en BakeOS, ComandaYa solo manda un DTO estable y no se vuelve a tocar nunca.
La función extra es gratis (capa gratuita). El riesgo que se evita, no.

---

## 5. Cuándo es "venta firme" (las trampas que hay que respetar)

Éste es el punto donde el consolidado puede quedar mal en silencio. El trigger **solo** debe
sincronizar cuando la orden es dinero real y completo:

| Campo de `Order` | Regla |
|---|---|
| `status == "Pagado"` | Condición base (`Constants.ORDER_STATUS_PAID`) |
| `isCredit && !creditSettled` | **NO sincronizar.** El propio código de ComandaYa dice: *"No entra a caja ni a ventas hasta que se salda"*. Se sincroniza cuando `creditSettled` pasa a `true` |
| `isPartiallyPaid` / `remainingAmount > 0` | **NO sincronizar** hasta que quede en 0, o el total consolidado queda corto |
| `status == "Cancelado"` después de sincronizada | Sincronizar **en negativo** (revierte lo que ya se sumó) |
| `payments[]` | Es la fuente del desglose por método. `paymentMethod` solo guarda el **último** cobro — en cuentas separadas los demás se pierden. Usar siempre el arreglo |
| Método de pago desconocido (apps de delivery) | **Entra a la lista de caja igual que la tarjeta.** Se intentó excluirlo —PedidosYa la liquida el banco días después, no entra efectivo a la gaveta— pero eso deja el ingreso en el aire: el ticket se imprime igual y alguien tiene que hacerse cargo de esa venta. Se cuadra como `TRANSFER` |
| `tipAmount` | **En Sabreopan no se cobra propina.** Nunca suma a `totalRevenue`. Se copia tal cual (llegará en 0) solo para que una orden vieja o un cambio futuro no queden mintiendo; no se construye reportería de propinas |
| `deliveryFee` | Suma al total cobrado; se guarda desagregado |
| `discountAmount` | Ya viene aplicado al total. No restarlo otra vez |

### DTO que viaja

```
ExternalSale {
  externalId    // "comandaya_{orderId}"   <- clave de idempotencia
  source        // "COMANDAYA"
  channel       // "RESTAURANT"
  externalBranchId            // branchId de ComandaYa
  businessDate  // yyyy-MM-dd (día operativo)
  closedAt      // epoch millis
  orderType     // DINE_IN | TAKEAWAY | DELIVERY
  externalUserId, externalUserName   // quién cerró la orden — con quién está el efectivo
  tableId                            // "Mesa 4", para que el cajero identifique la orden
  lines: [ { externalItemId, name, category, qty, unitPrice, lineTotal } ]
  payments: [ { method, amount } ]
  subtotal, discountAmount, deliveryFee, tipAmount, total
  status        // COMPLETED | CANCELLED
  dte?: { uuid, serie, numero, authNumber, emisorNit }   // informativo
}
```

---

## 6. Idempotencia: la regla que no se negocia

El documento en BakeOS se llama **`comandaya_{orderId}`** — id determinista. Un reintento
sobreescribe, nunca duplica.

Pero el resumen diario **no se puede actualizar con `increment()` a ciegas**: un reintento lo
contaría dos veces y el error queda invisible para siempre. Todo va en **una transacción**:

1. Leer `sales/comandaya_{orderId}`.
2. Si **no existe** → escribir la venta y sumar el monto completo al resumen diario.
3. Si **existe** → calcular el **delta** contra lo guardado y aplicar solo la diferencia.
4. Si llega `CANCELLED` → delta negativo por el monto completo.

Ese mismo mecanismo resuelve tres casos con un solo camino: reintento de red, orden editada
después de cobrada, y orden cancelada después de sincronizada. Escribir un reverso aparte es lo
que termina dejando ventas fantasma.

---

## 7. Los resúmenes diarios (esto arregla el presupuesto)

```
companies/{c}/salesDaily/{branchId}_{yyyy-MM-dd}
  branchId, businessDate
  byChannel:   { BAKERY: {revenue, orders, units}, RESTAURANT: {...} }
  byPayment:   { CASH: {amount, count}, CARD: {...}, TRANSFER: {...} }
  byHour:      { "07": revenue, "08": revenue, ... }
  topProducts: { productId: { name, qty, revenue } }
  discounts, deliveryFees
  updatedAt
```

- Los **reportes y el Home leen solo resúmenes**: un mes = sucursales × 30 documentos.
- El **detalle por orden sigue existiendo** en `sales/` — se lee únicamente cuando se abre un
  día concreto. Por eso "por orden" no cuesta caro: **se guarda por orden, se lee por día.**
- El resumen lo mantiene **un trigger de BakeOS sobre `companies/{c}/sales/{id}`**, así vale
  igual para la venta de pan del POS y para la del restaurante — un solo lugar que agrega,
  sin importar quién escribió la venta.

**Esta parte no es de Sabreopan: es una mejora de producto que le sirve a toda panadería que
use el POS.** Conviene tratarla como trabajo aparte de la integración.

---

## 7.bis. El cobro: dos gavetas por sucursal, y quien cobra es quien lo dice

**Realidad de la operación:** cada sucursal tiene **dos cajas**, y *cualquiera de las dos* puede
cobrar una orden de ComandaYa. Marcar la orden como "Pagado" en ComandaYa no dice en cuál gaveta
entró el dinero.

### No es un doble cobro: es una entrega de custodia

El flujo real es: el mesero recibe el dinero del cliente y marca "Pagado" en ComandaYa (puede ser
al tomar la orden o al terminarla, las dos formas existen). Después le entrega ese efectivo a
quien está en caja.

Son **dos hechos distintos sobre dos personas distintas**:

| Hecho | Quién | Qué afirma |
|---|---|---|
| "Pagado" en ComandaYa | el mesero | el cliente ya no debe nada — el ingreso existe |
| Seleccionar la orden en el POS | quien está en caja | el efectivo cambió de manos — ya está en esta gaveta |

No es registrar lo mismo dos veces. Es lo que se hace siempre que el dinero pasa por más de una
mano, y es lo que crea responsabilidad: si no llega, se sabe **quién lo tenía**.

**Por eso la pantalla no debe decir "cobrar".** Si dice cobrar, el personal va a creer que está
cobrando de nuevo. Tiene que decir **"Recibir de mesero"** o **"Entregado a caja"** — el nombre
correcto es lo que hace que se entienda sin explicarlo.

### No hay algoritmo que pueda resolver esto — solo una persona

Con dos gavetas indistintas, ninguna regla automática puede atribuir el efectivo: no hay campo,
timestamp ni heurística que sepa a cuál de las dos fue. **La única fuente de ese dato es quien
recibe el dinero.** Por eso el planteo de Fernando —que el POS de BakeOS muestre las órdenes ya
dadas por pagadas en ComandaYa y el cajero seleccione la que está cobrando— no es burocracia
agregada: es el único momento en que ese hecho existe.

Esto **reemplaza** la decisión anterior de mandar todo al cierre del día. Atribuir a la gaveta
es estrictamente mejor: `CashSession` es la rendición de una persona, y ahora sí se puede saber
de quién es cada quetzal.

### La orden llega "por cobrar", no como venta cerrada

El cambio estructural: lo que sincroniza ComandaYa **no entra directo como venta de una caja**.
Llega en estado *pendiente de cobro* y se convierte en venta atribuida cuando un cajero la
reclama desde el POS.

```
ComandaYa: orden "Pagado"
        │  (trigger + ingest, ya diseñados en §4 y §5)
        ▼
BakeOS: venta con source=COMANDAYA, channel=RESTAURANT
        status = PENDING_COLLECTION, sin sessionId/registerId/cashierId
        │
        │  el cajero la ve en la lista del POS y la selecciona al recibir el dinero
        ▼
        status = COMPLETED, con sessionId + registerId + cashierId de ESA caja
        → entra al arqueo de esa gaveta
```

### Ingreso y efectivo se cuentan en momentos distintos, y está bien

| Pregunta | Cuándo cuenta |
|---|---|
| ¿Cuánto vendí? (reportes, resumen diario) | **Al llegar** — ComandaYa ya dijo que se cobró; el ingreso es real |
| ¿Cuánto efectivo debe haber en esta gaveta? | **Al reclamarla** — antes de eso no está en ninguna |

Separarlos no es una inconsistencia: son dos hechos distintos que ocurren en momentos distintos.

### Lo que queda sin reclamar es un control, no un residuo

Al cerrar el día, "hay Q450 de restaurante que ninguna caja cobró" es exactamente el dinero que
se perdió entre el mesero y la gaveta. **Hoy nadie se enteraría.** Esa lista es la parte más
valiosa de todo el módulo, y por eso conviene mostrarla en el cierre, no esconderla.

Hace falta una salida para el caso legítimo: una orden que se cobró fuera del POS. Un
"marcar como recibida fuera de caja" con motivo, para que no ensucie la lista para siempre.

### La lista tiene que decir con quién está el dinero

El punto de todo esto es poder preguntar. Por eso el `ExternalSale` lleva el usuario de
ComandaYa que cerró la orden (`Order.userId`), y la fila del POS se lee
**"Mesa 4 · Q180 · con Juan"**. Sin el nombre, la lista dice que falta dinero pero no a quién
buscar, que es la mitad del valor.

### Todas las órdenes entran a la lista — cambia a qué total alimentan

El datáfono del banco **está en la caja**: ahí se pasa la tarjeta y ahí se guarda el voucher. Así
que una orden con tarjeta también ocurre físicamente en una gaveta concreta, y también hay algo
que cuadrar — el fajo de vouchers contra el total de tarjeta de esa caja.

Por eso **no se filtra por método**. Toda orden pagada entra a la lista de pendientes; lo que
cambia es qué total del arqueo alimenta al reclamarse:

| Método | Alimenta | Qué se cuadra contra eso |
|---|---|---|
| Efectivo | `totalCashSales` → efectivo esperado | El conteo físico de la gaveta |
| Tarjeta | `totalCardSales` | Los vouchers guardados en esa caja |
| Transferencia | `totalTransferSales` | El comprobante / la cuenta |

El efectivo es el único que mueve el conteo físico, pero **los tres necesitan saber en qué caja
ocurrieron**, o el arqueo de esa caja no cierra contra lo que tiene en el cajón.

### El arqueo vive en BakeOS — decidido

`CashRegisterShift` de ComandaYa (uno por sucursal por día, con conteo físico y diferencia) **no
se usa en Sabreopan**, y así se queda. Dos arqueos sobre las mismas dos gavetas no pueden cuadrar:
cada sistema vería solo una parte de lo que entró al cajón.

**BakeOS es el único lado que ve pan + restaurante y las dos gavetas**, así que ahí vive el
arqueo. No hay que tocar ComandaYa para esto: es dejar de usar una pantalla.

### Hueco conocido: los pagos parciales

Una orden a medio pagar no se sincroniza (§5), así que si un mesero ya recibió parte del dinero,
ese efectivo está fuera de la gaveta y fuera del sistema hasta que la orden se salde. Se acepta a
sabiendas: cubrirlo obligaría a sincronizar estado mutable, que es justo lo que §5 evita.

---

## 8. Lo que NO se toca

1. **Inventario de cocina**: BakeOS no lee ni escribe `ingredient_inventory` de ComandaYa.
2. **La venta de restaurante no mueve `FinishedProductStock` ni el Kardex de pan.** Sale
   escrita por la función, que no pasa por `FirebaseSaleRepository.createSale` — el descuento de
   stock del POS no se ejecuta. Es correcto: los platos no son productos de BakeOS.
3. **No se espeja el catálogo de platos como `Product`.** Solo viajan las líneas de venta con su
   nombre; alcanza para los reportes.
4. **BakeOS no emite DTEs.** Recibe el dato del DTE de ComandaYa como información.
5. **El pan que la panadería le manda al restaurante** sigue por el flujo de despacho existente
   (planta → sucursal). No se toca. Si además llegara como venta de ComandaYa se contaría dos
   veces — por eso el pan que el restaurante vende al público entra por ComandaYa como venta y
   sale del despacho como traslado, nunca por los dos caminos a la vez.

---

## 9. Orden de trabajo

| # | Paso | Alcance | Por qué en este orden |
|---|---|---|---|
| 1 | ~~**Acotar las queries de ventas**~~ — **HECHO** (sep 2026). Helper `salesInRange` con rango server-side; los dos listeners sobre la colección completa ahora van filtrados. Queda pendiente el N+1 de líneas, que lo elimina el paso 2 | Producto | Se leían todas las ventas de la historia 3 veces por apertura del Home |
| 2 | ~~**Resúmenes diarios**~~ — **HECHO** (sep 2026). `salesDaily/{branchId}_{yyyy-MM-dd}`, aporte en transacción al cobrar, `aggregateSalesInRange` leyendo de ahí, y `rebuildDailyRollups` + botón "Recalcular" en Configuración | Producto | Es lo que permite que "por orden" no cueste |
| 3 | ~~**`Sale.source` + `Sale.salesChannel`**~~ — **HECHO**, adelantado al paso 2. Se hizo junto porque el resumen diario tenía que nacer con el desglose por canal: agregarlo después obligaba a reconstruir todos los resúmenes | Producto | Sin esto no se puede separar el consolidado |
| 4 | ~~**`ingestExternalSale`**~~ — **HECHO y DESPLEGADO** (8-sep-2026). `functions/externalSales.js` | Sabreopan | El lado que puede cambiar |
| 5 | ~~**`syncSabreopanSale`**~~ — **HECHO y DESPLEGADO** (8-sep-2026). `OrderApp/functions/sabreopanBridge.js` + `retrySabreopanOutbox`. Backfill en `tools/backfill-sabreopan-sales.js` | Sabreopan | Se despliega solo, no toca nada más |
| 6 | **Reportes**: filtro por canal (pan / restaurante / todo) | Producto | El consolidado que querés ver |
| 7 | ~~**Recepción de cuentas desde el POS**~~ — **HECHO** (sep 2026). `PendingCollectionsSheet` + badge en el TopAppBar del POS, `observePendingCollections` y `collectPendingSale` con guarda transaccional. **Falta**: los no recibidos a la vista en el cierre del día | Sabreopan | Es lo que atribuye el efectivo a una gaveta (§7.bis) |

Los pasos 1-3 y 6 mejoran BakeOS para cualquier cliente. Solo el 4 y el 5 son de Sabreopan, y
son los más chicos.

---

## 9.bis. Cómo se despliega

Nada de esto funciona hasta hacer estos seis pasos, en este orden.

### 1. En BakeOS — documento de configuración

El emparejamiento ya está en el script, con los ids reales:

| Sucursal | BakeOS | ComandaYa |
|---|---|---|
| Democracia | `7c72132c7c97052f49228125523aa886` | `BUQ8IT24TFi30k3wejFS` |
| Salida de Almolonga | `d8d272024e5b75373ba09a7acced5d51` | `gUBoON5MANYwjNhXLdD3` |
| Bombero | `47824fd8d75f146b360080b66951ae99` | `FqwYhNkP63iCIwRnpDu8` |

```bash
cd functions
node tools/setup-comandaya-integration.js --company=<orgId>            # simula
node tools/setup-comandaya-integration.js --company=<orgId> --apply    # escribe
```

Verifica que las 3 sucursales de BakeOS existan antes de escribir: un id mal copiado dejaría
ventas entrando a una sucursal fantasma y eso se descubre semanas después.

Solo van las **3** con restaurante. Las otras 2 de BakeOS no existen en ComandaYa. Una
sucursal sin mapear hace que la función **rechace** la orden con error a la vista, en vez de
tragársela — ventas que nadie ve son peor que un error.

### 2. En BakeOS — el secreto

```bash
firebase functions:secrets:set COMANDAYA_INGEST_KEY
```
Pegar una cadena larga y aleatoria. Es un secreto de servidor a servidor, no una contraseña
de persona.

### 3. En BakeOS — desplegar

```bash
firebase deploy --only functions:ingestExternalSale
```
Anotar la URL que imprime.

### 4. En ComandaYa — variables de entorno

En `OrderApp/functions/.env` (el proyecto ya usa `dotenv`):
```
BAKEOS_INGEST_URL=<la URL del paso 3>
BAKEOS_INGEST_KEY=<la misma cadena del paso 2>
```

### 5. En ComandaYa — desplegar SOLO estas dos

```bash
firebase deploy --only functions:syncSabreopanSale,functions:retrySabreopanOutbox
```
No desplegar todo: las demás funciones les sirven a los otros 39 restaurantes y no hay razón
para moverlas.

### 6. Probar con una orden real

Cobrar una orden en ComandaYa y verificar, en orden:
1. Logs de `syncSabreopanSale` — debe decir `→ BakeOS (Q…)`.
2. Logs de `ingestExternalSale` — debe decir la sucursal y el día.
3. En Firestore de BakeOS: `companies/{c}/sales/comandaya_{orderId}` con
   `status = PENDING_COLLECTION`.
4. `companies/{c}/salesDaily/{branchId}_{fecha}` con el monto sumado en
   `channelRevenue.RESTAURANT`.
5. **Cobrar la misma orden dos veces** (reenviarla) y confirmar que el total del día **no**
   se duplica. Ésa es la prueba que importa.

Si algo falla, la orden queda en `Restaurants/{r}/bakeosOutbox/{orderId}` con el error, y se
reintenta sola cada 10 minutos.

---

## 9.ter. Lo que se aprendió corriéndolo con datos reales (8-sep-2026)

Tres cosas que solo aparecieron al mandar las órdenes de verdad. Vale la pena dejarlas escritas.

### Las rutas con punto no funcionan en `set()`

El resumen diario se actualizaba con claves tipo `channelRevenue.RESTAURANT`. Eso solo lo
interpreta `update()`; con `set(..., {merge:true})` Firestore crea un campo cuyo **nombre**
lleva el punto y deja el mapa real vacío. El síntoma fue el peor posible: `revenue` salía
correcto y todos los desgloses en cero — parecía que funcionaba. Ahora van como mapas
anidados, que `merge` sí fusiona en profundidad y donde `increment` opera a cualquier nivel.

### Los ids no le sirven a quien está en caja

`Order.tableId` es el id del documento de la mesa (el número vive en `Tables/{id}.number`), y
`Order.userId` es inconsistente en los datos reales: en unas órdenes trae el uid
(`zAcayfKUCEON…`) y en otras el nombre ya escrito ("Gladys Calderón"). Sin resolverlos, la
fila decía "Mesa rlG7MRmbDnFTXahRMSbQ · sin mesero". Se resuelven contra `Tables` y `Users`
con caché por instancia: dos lecturas por arranque en frío, ninguna por orden.

### Las apps de delivery sí van a la lista

Primero se excluyeron por un razonamiento que parecía correcto: PedidosYa lo liquida el banco
días después, no entra efectivo a ninguna gaveta. Pero el ticket se imprime igual y alguien
tiene que hacerse cargo de esa venta — si no aparece en la lista, **el ingreso queda en el
aire y no cuadra contra nada**. Entran como `TRANSFER`, igual que la tarjeta.

---

## 10. Decisiones

No queda nada abierto. Para referencia, lo que se resolvió y por qué:

| Decisión | Resuelto |
|---|---|
| ¿Quién factura el restaurante? | **ComandaYa.** BakeOS solo consolida el dato del DTE |
| ¿Inventario de cocina en BakeOS? | **No.** Se queda entero en ComandaYa |
| ¿Inventario de insumos y Kardex de pan? | **En BakeOS**, como está hoy |
| ¿Por orden o por turno? | **Por orden** — se guarda por orden, se lee por día (§7) |
| ¿Propinas al ingreso? | **No se cobra propina en Sabreopan.** Nunca suman |
| ¿Alcance? | **Solo Sabreopan** (`cUVIThV5fCkC3PKfDByD`), aislado por ruta literal (§4.1) |
| ¿Dónde entra al cuadre? | **A la gaveta que recibe el efectivo del mesero**, seleccionándola en el POS (§7.bis) |
| ¿Es doble cobro? | **No** — es entrega de custodia entre dos personas (§7.bis) |
| ¿Las tarjetas entran a la lista? | **Sí** — el datáfono y el voucher están en la caja (§7.bis) |
| ¿Arqueo en ComandaYa? | **No se usa.** El arqueo vive en BakeOS (§7.bis) |
