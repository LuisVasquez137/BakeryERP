# Análisis: Compras vs. Gastos en BakeOS

> **Estado — 9 sep 2026.** Ya se aplicó la primera tanda de cambios. Lo hecho está marcado
> con ✅ a lo largo del documento; lo que sigue pendiente, con ⬜. Ver
> [Qué se hizo y qué falta](#qué-se-hizo-y-qué-falta) al final.
>
> **La "Vuelta de compras" ahora se llama "Compra rápida"** — el nombre viejo solo describía
> salir a comprar y dejaba sin lugar el caso de que alguien llegue a vender a la panadería.
> En Firestore el valor guardado sigue siendo `MARKET_RUN` y se lee con `PurchaseKind.isQuick`,
> así que no hubo migración.

Fecha: 2026-09-09 · Rama: `feat/integracion-comandaya`

Revisión completa de cómo se registran hoy los desembolsos (facturas, vueltas de
mercado y gastos manuales), qué entra al inventario y al Kardex, qué está roto, y
qué conviene cambiar. Escrito a partir de la lectura del código, no de suposiciones.

---

## Índice

1. [¿La materia prima entra a inventario y al Kardex?](#1-la-materia-prima-entra-a-inventario-y-al-kardex)
2. [Lo que pedís para las sandías ya existe: "Vuelta de compras"](#2-lo-que-pedís-para-las-sandías-ya-existe-vuelta-de-compras)
3. [Bugs concretos en la vuelta](#3-bugs-concretos-en-la-vuelta)
4. [El problema estructural de fondo](#4-el-problema-estructural-de-fondo)
5. [Recomendación](#5-recomendación)
6. [Anexo: referencias de código](#anexo-referencias-de-código)

---

## 1. ¿La materia prima entra a inventario y al Kardex?

**Sí. Verificado en código, y bien resuelto.**

Hay un solo método que toca inventario en todo el módulo de compras:

```
PurchaseViewModel.applyStockForPurchase()
  → composeApp/src/commonMain/kotlin/com/bakery/erp/presentation/purchases/PurchaseViewModel.kt:1158
```

Lo llaman los cuatro caminos que pueden mover stock, y ninguno tiene su propia
copia de la lógica:

- `savePurchase` — registrar una factura nueva
- `receivePurchase` — recibir una que estaba "Por recibir"
- `applyStockDelta` — corregir por diferencia al editar o borrar
- `saveMarketRun` — guardar una vuelta de mercado

### Qué escribe cada tipo de línea

**`INGREDIENT` — materia prima (harina, azúcar, sal, levadura)**

Es el caso que preguntaste. Escribe tres cosas:

- `BranchStock` — actualiza la existencia de ese insumo en esa sucursal
  (documento `{branchId}_{ingredientId}`).
- `StockMovement` tipo `"COMPRA"` — el renglón del Kardex del insumo, con
  `balanceBefore`, `balanceAfter`, sucursal, referencia a la compra y fecha.
- Un **lote FEFO** en `stockLots/`, pero solo si el insumo está marcado como
  perecedero (`Ingredient.isPerishable`) o si la línea trae fecha de vencimiento.
  La harina y el azúcar no llevan lote a propósito: rotan en días y el libro de
  lotes sería puro ruido.

**`SUPPLY` — suministro de operación (cloro, papel, trapos)**

Hace exactamente lo mismo que la materia prima (`BranchStock` + Kardex) **y
además** genera un `Expense` del mes. Las dos cosas, y no es doble conteo: nadie
registra nunca el consumo de cloro, así que si no se expensa el día que se
compra no aparecería jamás en el estado de resultados. Su inventario existe para
contestar "¿me queda?", no para valorar nada.

**`RESALE_PRODUCT` — producto que se revende sin transformar**

No toca `BranchStock` (no es insumo). Escribe en `FinishedProductStock` de la
sucursal con un movimiento `RECEPCION`, que es el inventario de tienda.

**`SERVICE` — servicio o gasto sin inventario**

No toca ningún inventario. Genera un `Expense`, y si la línea declaró un activo
fijo, también una entrada en el historial de ese activo (el mantenimiento del
horno, el combustible de la camioneta).

### Dos detalles que están bien resueltos y conviene no romper

**El saldo del Kardex se lee fresco por línea.** Antes de escribir cada
movimiento, `applyStockForPurchase` vuelve a leer `getBranchStocksForIngredient`.
Eso significa que si una factura trae dos renglones del mismo insumo, los saldos
`balanceBefore` / `balanceAfter` encadenan correctamente en vez de repetirse.

**Una compra "Por recibir" no ingresa nada.** `savePurchase` aplica inventario
solo si el estado no es `PENDING_RECEIPT`. El ingreso queda en `receivePurchase`,
que marca `receivedAt` **antes** de mover stock — así, si algo falla a media
aplicación, un reintento choca con la guarda de estado en vez de sumar el mismo
saco de harina dos veces.

### Resumen en tabla

| Tipo de línea | `BranchStock` | Kardex | Lote FEFO | `FinishedProductStock` | `Expense` |
|---|:--:|:--:|:--:|:--:|:--:|
| `INGREDIENT` (harina, azúcar) | Sí | Sí | Solo perecederos | — | — |
| `SUPPLY` (cloro, trapos) | Sí | Sí | Solo perecederos | — | Sí |
| `RESALE_PRODUCT` (reventa) | — | — | — | Sí | — |
| `SERVICE` (mantenimiento, flete) | — | — | — | — | Sí |

---

## 2. Lo que pedís para las sandías ya existe: "Vuelta de compras"

`MarketRunFormScreen` es literalmente el caso que describiste. Anotás
"sandías, Q80, para el restaurante, del mercado" y listo:

- Sin proveedor del catálogo — el lugar es texto libre por línea.
- Sin número de factura, sin crédito, sin vencimiento. Nace pagada
  (`PurchaseStatus.PAID`) porque es efectivo del momento.
- **Sucursal por línea**: una sola salida puede surtir al restaurante y a la
  panadería, y cada fila dice a dónde va.
- **El inventario es opcional fila por fila**: si le pegás un insumo del catálogo,
  entra al inventario con Kardex; si la dejás como texto suelto, es gasto y nada
  más. La pantalla lo dice explícitamente en cada fila.

Y es el **mismo documento `Purchase`** con `kind = MARKET_RUN`, no una colección
paralela. Por eso hereda gratis el ingreso a inventario, la generación de gastos,
la foto del recibo y la corrección por diferencia al editar.

### El problema: nadie la encuentra

- El único punto de entrada a la **primera** vuelta es un ícono chiquito en el
  TopAppBar de Compras.
- Ese ícono **desaparece** apenas existe una vuelta
  (`PurchaseListScreen.kt:167` — `if (!hasMarketRuns)`), porque a partir de ahí
  la entrada pasa al FAB de la pestaña "Vueltas". Funciona, pero el punto de
  entrada se mueve de lugar justo después de usarlo por primera vez.
- En el Home, el módulo "Compras" tiene de subtítulo **"Ingreso de insumos"**
  (`HomeScreen.kt:93`). Le está diciendo al usuario exactamente lo contrario de
  lo que queremos que haga: si lo que compró no es un insumo, se va a Gastos.

---

## 3. Bugs concretos en la vuelta

Ordenados por impacto real.

### 3.1 ✅ La reventa es inalcanzable — CRÍTICO

Dos causas independientes, cada una suficiente por sí sola:

1. `MarketRunLineDialog` construye la fila con `product = null` **hardcodeado**
   (`MarketRunFormScreen.kt:441`).
2. El parámetro `products = state.supplierResaleProducts` **siempre llega vacío**:
   ese campo del state solo se llena dentro de `selectSupplier`
   (`PurchaseViewModel.kt:742`), y una vuelta no tiene proveedor. `loadFormData`
   nunca lo toca.

**Consecuencia:** comprás tostadas o gaseosas para revender en la vuelta y caen
como `SERVICE` (gasto suelto). Nunca entran a `FinishedProductStock`, así que el
POS no las va a tener y el inventario de tienda no se entera.

### 3.2 ✅ No hay fecha editable — ALTO

```kotlin
val date = remember { Clock.System.now().toEpochMilliseconds() }
```

A la factura se le puso fecha editable justamente porque se captura en tandas.
La vuelta se anota en la noche o al día siguiente todavía más seguido, y queda
fechada mal — lo que corre el período del gasto y la fecha del Kardex.

### 3.3 ✅ La categoría de gasto siempre es `OTROS` — ALTO

`MarketRunFormScreen` pasa `expenseCategory = ExpenseCategory.OTROS.name` fijo.
La vuelta contesta "¿cuánto gasté?" pero no "¿en qué?", que es exactamente la
pregunta para la que existe el desglose por categoría del reporte financiero.

### 3.4 ✅ Editar una vuelta abre el formulario de factura — ALTO

`PurchaseFormScreen` no tiene **una sola** referencia a `PurchaseKind`. Cero
conciencia de que existen dos formas de compra. Tocar el lápiz en el detalle de
una vuelta te lleva a un formulario que pide proveedor y recorre presentaciones,
sin campos para `placeName` ni para la sucursal por línea.

El `kind` sí se preserva (`updatePurchase` usa `original.copy(...)`), así que el
documento no se corrompe — pero la pantalla es la equivocada y los datos propios
de la vuelta no son editables.

### 3.5 ✅ Una vuelta se lista como "Sin proveedor" — MEDIO

`PurchaseListScreen.kt:550` y `:698` pintan `purchase.supplierName.ifEmpty { "Sin proveedor" }`.
El comentario en `saveMarketRun` dice que "el nombre visible en la lista lo arma
la pantalla a partir de los lugares que trae" — **no lo hace**. Todas las vueltas
se ven iguales en la lista.

### 3.6 ✅ Perecederos sin fecha de vencimiento — MEDIO

`MarketRunDraftLine.expiryDate` existe, y `applyStockForPurchase` lo usaría para
crear el lote FEFO con fecha. Pero el diálogo **no tiene UI** para capturarlo:
solo lo arrastra de `existing`, que siempre es 0.

**Consecuencia:** la fruta comprada en la vuelta crea un lote sin fecha, y en
FEFO los lotes sin fecha van al final de la cola — justo al revés de lo que
necesita la fruta.

### 3.7 ✅ La cantidad no muestra la unidad — MEDIO

El campo se llama "Cant." y arranca en `1`. Si pegás "sandías" a un insumo cuya
unidad base es `lb` y dejás el 1, entran **1 lb** al inventario habiendo comprado
20. La unidad base del insumo elegido no se muestra en ningún lado del diálogo.

---

## 4. El problema estructural de fondo

Tu intuición de que "está en dos lados" es correcta, pero conviene precisar
dónde está el duplicado — porque no está donde parece.

### En el modelo NO hay duplicado

Está bien resuelto:

- `Expense.sourcePurchaseId` marca los gastos que nacieron de una compra.
- `syncPurchaseSideEffects` es el **único** lugar que los escribe, y lo llaman
  los cuatro caminos (registrar, recibir, editar, borrar). Borrar pasa lista
  vacía, que es exactamente "quitá todo lo que esta factura dejó".
- Los gastos con origen en compra no se editan ni se borran desde Gastos, y el
  detalle explica por qué en vez de solo esconder los botones.

Eso es correcto y no hay que tocarlo.

### El duplicado está en la CAPTURA

Hay **tres puertas** para registrar que salió dinero:

| Puerta | Dónde vive | Inventario | Foto | Sucursal por línea | Proveedor |
|---|---|:--:|:--:|:--:|:--:|
| Factura | Compras | Sí | Sí | Sí | Sí |
| Vuelta | Compras (escondida) | Opcional | Sí | Sí | Texto libre |
| Nuevo Gasto | Gastos (en el Home) | **Nunca** | No | No | No |

La tercera es la **peor de las tres** y es la única que el usuario encuentra
fácil, porque está en el Home mientras la vuelta está escondida detrás de un
ícono. Es la definición del problema: la puerta más accesible es la que menos
puede hacer.

### Y hay un agujero mayor que ninguno de los dos tapa

**El reporte financiero no sabe que existen las compras.**
`presentation/reports/financial/` no menciona `Purchase` ni una sola vez.

```
Egresos = ExpenseSummary.total + PayrollSummary.totalEmployerCost
```

Entonces la harina y el azúcar — la mayor salida de efectivo del mes en una
panadería — no aparecen en ningún reporte de dinero.

Contablemente es defendible: es inventario hasta que se vende, y su costo entra
al resultado vía costo de venta. Pero el dueño que pregunta *"¿en qué se me fue
el mes?"* no obtiene respuesta ni en Gastos ni en Financiero. Y como BakeOS
todavía no tiene módulo de costo de producto, ese costo de venta no lo está
recogiendo nadie.

---

## 5. Recomendación

### 5.1 Gastos deja de ser un punto de captura y pasa a ser una vista

Todo desembolso entra por **Compras**, en tres formas según cuánto sabés:

1. **Factura de proveedor** — cuando hay papel, crédito, presentaciones e IVA.
2. **Vuelta** — anotás y ya; inventario opcional fila por fila. Cubre desde las
   sandías del restaurante hasta la brocha de la ferretería.
3. **Gasto recurrente** — alquiler, luz, internet. Es lo único que legítimamente
   no es una compra, y ya tiene su lugar en Presupuesto Fijo.

El FAB de "Nuevo gasto" pasa a abrir la vuelta con una línea prellenada. El
módulo de Gastos conserva su lista, sus filtros por período y sucursal, y su
presupuesto — que es donde de verdad aporta.

**Por qué así:** la vuelta ya hace todo lo que hace el gasto manual, **más**
inventario opcional, más foto del recibo, más sucursal por línea, más lugar de
compra. No se pierde ninguna capacidad y no hay que tocar el modelo de datos.

**Por qué sirve para el SaaS y no solo para vos:** una panadería que nunca
maneja inventario de nada puede usar solo vueltas y el sistema funciona completo.
Una que sí, va pegando filas al catálogo a medida que le importa. La precisión
es una decisión por fila, no una configuración que hay que entender antes de
empezar.

### 5.2 Tres cosas que no dependen de esa decisión

**a) Arreglar los bugs de la vuelta.** Los puntos 3.1, 3.2, 3.3 y 3.6 son los
que la vuelven inservible para el caso del restaurante. Los otros tres son
molestos pero no bloquean.

**b) Renombrar "Compras" en el Home.** El subtítulo "Ingreso de insumos" es lo
que está mandando a la gente a Gastos. Algo como "Facturas, vueltas y gastos".

**c) Agregar compras al reporte financiero como flujo de caja**, en una sección
aparte de la utilidad operativa — sin mezclarlo con el P&L, para no romper la
contabilidad de inventario ni contar dos veces cuando llegue el costo de
producto.

---

## Anexo: referencias de código

| Qué | Dónde |
|---|---|
| Único camino que mueve inventario | `presentation/purchases/PurchaseViewModel.kt:1158` (`applyStockForPurchase`) |
| Corrección por diferencia al editar/borrar | `PurchaseViewModel.kt:513` (`applyStockDelta`) |
| Guardado de la vuelta | `PurchaseViewModel.kt:800` (`saveMarketRun`) |
| Fila de la vuelta → línea de compra | `presentation/purchases/MarketRunViewModelExt.kt` (`toPurchaseLine`) |
| Pantalla de la vuelta y su diálogo | `presentation/purchases/MarketRunFormScreen.kt` |
| Proyección compra → gastos y activos | `presentation/purchases/PurchaseSideEffects.kt` (lógica pura, con tests) |
| Modelo de línea y sus cuatro tipos | `model/Purchase.kt` (`PurchaseLineKind`) |
| Gasto y su vínculo con la compra | `model/Expense.kt` (`sourcePurchaseId`, `isFromPurchase`) |
| Reporte financiero (sin compras) | `presentation/reports/financial/` |
| Entradas del Home | `presentation/home/HomeScreen.kt:93` y `:114` |

---

## Qué se hizo y qué falta

### ✅ Hecho (9 sep 2026)

**Renombre y reestructura**
- `PurchaseKind.MARKET_RUN` → `PurchaseKind.QUICK`, etiqueta **"Compra rápida"**. El valor
  guardado en Firestore no cambió: `PurchaseKind.isQuick()` acepta el viejo y el nuevo, así
  que las compras ya registradas siguen apareciendo donde deben, sin migración.
- Archivos renombrados: `MarketRunFormScreen.kt` → `QuickPurchaseFormScreen.kt`,
  `MarketRunViewModelExt.kt` → `QuickPurchaseDraft.kt`,
  `MarketRunLineTest.kt` → `QuickPurchaseLineTest.kt`.

**Los 7 bugs del formulario**
- La reventa se puede elegir: `PurchaseUiState.allResaleProducts` se carga sin proveedor de
  por medio, y el diálogo tiene su propio picker con buscador.
- Fecha editable con `DatePickerDialog`.
- Categoría de gasto **por fila** (`QuickPurchaseDraftLine.expenseCategory`), no una sola
  para todo el desembolso. Solo se pide donde aplica: lo que entra a inventario como materia
  prima o reventa no genera gasto y no la muestra.
- Corregir una compra rápida abre **su propio** formulario (`purchases/quick/{id}`) y no el
  de la factura. El inventario se corrige por diferencia con el mismo `applyStockDelta` que
  ya usaba la factura; `paidAmount` sigue al total para que corregir el monto no deje un
  saldo fantasma.
- La lista las identifica por lo que traen (`Purchase.listTitle`), no como "Sin proveedor".
- Fecha de vencimiento capturable cuando el insumo es perecedero.
- La cantidad muestra la unidad del insumo y dice cuánto entra al inventario.

**Accesibilidad**
- Las pestañas **Facturas / Compras rápidas** están siempre. Antes la segunda aparecía solo
  cuando ya existía una, y el acceso a la primera era un ícono que desaparecía justo después
  de usarse.
- Módulo **"Gasto rápido"** en Operaciones del Home: anotar pasó de cuatro toques a uno.
- "Compras" cambió su subtítulo de *"Ingreso de insumos"* a *"Facturas y gastos del día"*.

**Costos fijos**
- Salieron de adentro de Gastos y son módulo propio en Administración, gateado por
  `MANAGE_FIXED_BUDGET` (el dueño, no quien anota el gasto del día).
- La lista de Gastos ya no suma un plan mensual con desembolsos reales: muestra un solo
  total, el de lo que de verdad se gastó en el período.
- El FAB de Gastos abre la compra rápida. El formulario manual de gasto queda solo para
  **editar** los que ya existen.

**Endurecimiento**
- `saveQuickPurchase` recibe el `purchaseId` de la pantalla en vez de deducirlo del state:
  el ViewModel es singleton, y abrir una corrección y salirse sin guardar habría hecho que
  la siguiente compra nueva sobreescribiera aquélla.

Compilan Android y JS; 122 pruebas unitarias en verde (11 en `QuickPurchaseLineTest`,
tres de ellas nuevas: categoría por fila, ida y vuelta al corregir, e insumo dado de baja).

### ⬜ Pendiente

1. ~~Compras en el reporte financiero~~ — **hecho**, ver abajo.
2. **Los costos fijos no generan gasto real.** `FixedBudgetItem` sigue siendo un catálogo:
   nunca se convierte en `Expense` ni llega a ningún reporte. Es un plan que nadie compara
   contra lo ejecutado. Faltaría generar el gasto del mes y poder marcarlo como pagado.
3. **Permiso propio para la compra rápida.** Hoy usa `RECEIVE_STOCK`, que es el de bodega.
   Anotar un gasto sin inventario no debería necesitar el mismo permiso que recibir una
   factura de harina.

---

## Segunda tanda: compras en el reporte financiero (hecho)

**El problema.** Egresos eran `gastos + planilla`. La harina y el azúcar no aparecían en
ningún reporte de dinero, así que "¿en qué se me fue el mes?" no tenía respuesta.

**Por qué no se restan de la utilidad.** Son un activo hasta que se venden. Restarlas al
comprarlas haría que un mes en el que se llenó la bodega salga en pérdida y el siguiente en
ganancia inventada. La utilidad operativa quedó igual; lo que se agregó son dos secciones
nuevas y una nota que explica el hueco.

**Lo que ve el dueño ahora:**

- Dos KPIs nuevos: **Comprado** y **Salió de caja**.
- Sección **"Compras del mes"**: total comprado, desglosado en *A inventario* (materia prima
  y reventa — "pega al costo cuando se vende") y *Gasto directo* (suministros y servicios —
  "ya contados arriba en el P&L"), más ya pagado / queda debiendo y el conteo de facturas vs.
  compras rápidas.
- Sección **"Salida de dinero del mes"**: ventas cobradas − compras pagadas − gastos fuera de
  compras − planilla = efectivo neto.
- Una nota al pie del P&L diciendo cuánto de inventario no está ahí y por qué.

**El doble conteo era el riesgo real**, y se evita con dos cortes:

- `Purchase.inventoryAmount` / `expensedAmount`, denormalizados en el encabezado desde las
  líneas (el reporte recorre un mes entero; abrir la subcolección de cada compra sería una
  lectura por fila).
- `ExpenseSummary.fromPurchases` / `.standalone`, porque los suministros y servicios ya
  generaron su propio `Expense`. El flujo de caja resta `paidTotal + standalone`, así que el
  cloro no se resta dos veces.

**El histórico no necesita migración.** `Purchase.amountSplit()` deduce el reparto del
encabezado: sin `lineKinds` es materia prima por definición, y un solo grupo de tipos va
entero a su lado. Solo las compras **mixtas** anteriores al campo quedan como "Sin desglosar"
— y esa línea únicamente se dibuja si hay algo ahí. `functions/tools/backfill-purchases.js`
las resuelve (`node backfill-purchases.js` reporta, `--apply` escribe).

**Una honestidad declarada:** `paidTotal` es lo abonado de las compras *fechadas* en el mes,
no el efectivo exacto que salió — un abono de hoy a una factura de agosto cuenta en agosto.
La pantalla lo dice en vez de aparentar un flujo de caja exacto.

Lógica pura en `domain/report/PurchaseAggregation.kt`, con 10 pruebas en
`PurchaseAggregationTest` (mixtas, reventa, histórico sin campos, por recibir, crédito,
rangos, y compras rápidas guardadas con el nombre viejo). 132 pruebas en verde en total;
compilan Android y JS.
