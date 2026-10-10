# Anotar gasto, caja y categorías de gasto

> Detalle movido desde CLAUDE.md. Leer antes de tocar este módulo.

### Anotar un gasto: primero QUÉ, después el formulario que corresponde

`QuickPurchaseFormScreen` es la **única** pantalla desde la que se registra que salió dinero.
La abren el módulo "Anotar gasto" de Operaciones, el FAB de Gastos y la caja abierta del POS;
lo único que cambia entre las tres es si viene `sessionId`.

Antes eran dos pantallas que hacían casi lo mismo: `CashExpenseFormScreen` sabía de caja pero
**no tocaba inventario nunca**, y la compra rápida sabía de inventario pero no de caja. El
usuario tenía que elegir cuál abrir antes de saber qué iba a anotar, y un mismo desembolso con
una escoba y un galón de desinfectante no cabía en ninguna de las dos. `CashExpenseFormScreen`
se borró; `ExpenseViewModel` perdió su camino de alta (`saveCashExpense`) por lo mismo —
dejarlo habría sido un segundo lugar desde donde escribir gastos, y el que menos sabía.

- **Escribe un `Purchase` con `kind = QUICK`, siempre**, aunque no toque inventario. No es
  ceremonia: es el mismo camino de escritura, corrección por diferencia y borrado que ya
  estaba probado, y un segundo modelo de "gasto que a veces mueve stock" habría duplicado
  `applyStockForPurchase`, `applyStockDelta` y `syncPurchaseSideEffects`. Los `Expense` del mes
  salen de `expensesForPurchase` como con cualquier factura.
#### El primer paso pregunta la clase de cosa (`CaptureKindStep`)

Se pregunta **antes de mostrar un solo campo**, porque la respuesta cambia qué hay que llenar:
un taxi son tres datos, y una entrega de gaseosas es una factura con proveedor, catálogo,
presentaciones e IVA. Mientras fue un solo formulario para todo, el de la factura **no cabía**
(no había forma de elegir el catálogo del proveedor) y el del taxi pedía cantidad y unidad,
que para un taxi no existen.

| Opción | A dónde va |
|---|---|
| **Un gasto** | El editor de renglones de abajo. Varias cosas en un mismo desembolso, todas gasto. |
| **Insumos de un proveedor** | `PurchaseFormScreen` con `sessionId` — el formulario de Compras tal cual. |
| **Productos para revender** | Lo mismo. Se oculta si `usesResalePurchases` está apagado: el catálogo de reventa del proveedor llegaría vacío. |

Las dos de inventario van a la **misma** pantalla porque la factura del proveedor puede traer
insumos y reventa a la vez y ahí ya están los dos catálogos; se ofrecen por separado igual
porque el usuario no piensa "voy a capturar una factura", piensa "llegó el de las gaseosas".
Se ocultan sin `RECEIVE_STOCK` en vez de fallar después, que sería peor: ya habría capturado
la factura entera.

**Las opciones de `CaptureKindStep` salen juntas o no salen.** Cuáles hay depende de la
configuración de la empresa (`usesResalePurchases`), que es una lectura de red; las otras dos
tarjetas se dibujan con datos locales. Mientras la configuración se leía **al final** de la
cadena `loadFormData` (sucursales → proveedores → insumos → organización), "Productos para
revender" aparecía sola un par de segundos después, como si el toque anterior la hubiera
invocado. Ahora esa lectura va en su propia corrutina, en paralelo, y el paso espera a
`purchaseFlowsLoaded` antes de dibujar las de inventario. Con el ViewModel ya cargado —el caso
normal, porque es singleton— no llega a verse.

#### `PurchaseFormScreen` en modo "pagada de caja" (`sessionId`)

Es **la misma pantalla de Compras**, no una copia liviana: la factura que trae el repartidor
tiene proveedor, catálogo, presentaciones e IVA igual que cualquier otra, y una segunda
implementación se queda atrás en cuanto ésta cambie. Con `sessionId`:

- `purchaseStatus` se fuerza a `PAID` y **el selector de estado desaparece**. Dejar "Por
  recibir" ahí permitiría descontar el efectivo de una compra que no entró al inventario.
- **No se ofrece crédito** (`InvoiceHeaderFields(showCredit = false)`): no hay días que teclear
  sobre algo que ya se pagó, y hacerlo generaría una cuenta por pagar que nadie debe.
- **La sucursal se fuerza a la de la caja** y deja de ser un paso. Si se pudiera cambiar, el
  efectivo saldría de una gaveta y la mercadería entraría al inventario de otra sucursal, y las
  dos quedarían mal sin que nada lo señale.
- `savePurchase` estampa la atribución y llama `syncPurchaseCashMovement` — el mismo helper de
  la compra rápida, por eso dejó de llamarse `syncQuickPurchase...`. El efectivo sale de la
  gaveta **aunque lo comprado sea inventario y no gasto**: lo que se cuadra al cierre es el
  dinero, no el destino contable.
- La ruta es `purchases/form?sessionId={sessionId}`. Query param y no segmento, para no chocar
  con `PurchaseEdit` (`purchases/form/{purchaseId}`).

#### El editor de renglones (rama "Un gasto")

- **Cada cosa se escribe UNA vez: el renglón CRECE en su lugar.** Hubo una versión con dos
  campos en pantalla y un diálogo detrás para el resto, y el diálogo volvía a pedir qué
  compraste, cuánto costó y el tipo de gasto. Aunque llegaban prellenados, **leerlo dos veces
  con las mismas etiquetas se entiende como que hay que escribirlo dos veces** — fue lo primero
  que se reportó al probarlo. Ahora `QuickPurchaseLineEditor` es una tarjeta editable: qué y
  cuánto siempre; el tipo de gasto solo si el renglón se vuelve gasto; cantidad y vencimiento
  solo si entra a inventario; sucursal y lugar detrás de "Otra sucursal o lugar". Lo único que
  sigue siendo diálogo es **elegir del catálogo**, que es lo único que no se puede teclear.
- **Este editor NO elige catálogo.** Tuvo chips de "Solo gasto / Insumo / Reventa" y sobraban:
  la clase de cosa ya se decidió en `CaptureKindStep`, así que volver a ofrecerla es preguntar
  dos veces lo mismo — fue el segundo reporte al probarlo. Comprar inventario abre el
  formulario de Compras; acá todo renglón nuevo es gasto.
- **Lo único que queda del catálogo es conservar lo viejo.** Al corregir una compra rápida
  anterior, los renglones que sí venían pegados a un insumo o producto se muestran como un
  hecho (no como una opción) y se conservan. Si el editor los ignorara, guardar convertiría el
  renglón en gasto puro y `applyStockDelta` le revertiría el inventario **en silencio**.
- **Siempre hay un renglón vacío en pantalla.** Un formulario que arranca sin nada obliga a
  tocar "agregar" antes de poder escribir. Los renglones sin nombre o sin monto se descartan al
  guardar (`filled`) en vez de bloquear el botón: el renglón en blanco del final es parte del
  formulario, no un error.
- **Proveedor opcional en el encabezado** (`SupplierField`): texto libre que ofrece pegarse al
  catálogo si coincide. Al de gaseosas conviene atribuirle la compra —vuelve todas las
  semanas—; a la ferretería de una sola vez no, porque el catálogo termina lleno de nombres que
  nadie vuelve a tocar. Mismo criterio que el proveedor ocasional de la factura (`supplierId`
  vacío con `supplierName` lleno). El lugar por línea sigue siendo el override.
- El "sin elegir" del selector de sucursal por línea **nombra la sucursal** (`noneLabel`) en vez
  de decir "La de siempre": el usuario no tiene por qué acordarse de cuál eligió arriba, y una
  opción que no dice qué hace se lee como un error.
- **La caja no se elige: sale del contexto.** Con `sessionId` el desembolso se carga a esa
  gaveta; sin él es caja general. Un desplegable de cajas es una forma de marcar la equivocada
  sin darse cuenta, y ese error lo paga la cajera de la otra gaveta.
- El subtítulo de "Compras" pasó a *"Facturas de proveedor"*: las compras rápidas ya no se
  crean ahí.

#### De qué bolsa salió: `Purchase.paidFrom` / `cashSessionId` / `cashRegisterName`

Vive en el **encabezado de la compra** y no solo en los `Expense` que genera, porque una compra
rápida puede no generar ninguno: si todo lo que trae es materia prima o reventa es inventario, y
no hay gasto donde colgar la atribución. El efectivo igual salió de la gaveta y al cuadrar tiene
que faltar. `expensesForPurchase` copia los tres campos (y `invoicePhotoUrl` → `Expense.photoUrl`)
a cada gasto que produce.

`syncQuickPurchaseCashMovement` deja el `CashMovement` que descuenta la gaveta:

- **Al corregir escribe la DIFERENCIA, no reescribe el movimiento.** Mismo criterio que
  `applyStockDelta`: un movimiento reescrito borraría del historial de la caja que el monto
  cambió, que es justo lo que alguien quiere ver cuando el cuadre no da. Si el total bajó, la
  diferencia entra como `DEPOSIT` — ese efectivo nunca salió. `CashSessionRepository` no expone
  borrar ni editar movimientos, a propósito.
- **La gaveta se resuelve del `sessionId` de la pantalla y, si no viene, del guardado en la
  compra.** Corregir desde Compras no trae sesión; sin ese fallback la corrección no habría
  ajustado ninguna caja.
- **Si esa caja ya cerró, la atribución NO se reescribe** (`drawerLocked`): el movimiento vive
  dentro de un cierre que alguien firmó, y volver la compra "caja general" dejaría ese faltante
  sin nada que lo explique. Se corrige la compra, no se toca el efectivo, y se avisa.
- El aviso viaja en `state.message` y no en `error`: la pantalla navega hacia atrás apenas ve
  `isSaved`, y `resetForm()` limpia `error` pero no `message`.
- El concepto del movimiento se arma de las **líneas**, no de `purchase.listTitle()`: los
  nombres se denormalizan al escribir el encabezado, así que el objeto en memoria los trae
  vacíos y todos los movimientos habrían quedado como "Compra rápida".

#### No se paga de una gaveta con dinero que no está en ella

`assertCashAvailable` corta el guardado —en la compra rápida y en la factura pagada de caja—
cuando el monto pasa el efectivo de la sesión. La cajera lleva vendido Q300 y quiere anotar un
gasto de Q325: ese billete no existe. Dejarlo pasar no "documenta un faltante", **inventa
uno**: el cuadre pediría devolver dinero que nunca entró y el reclamo cae sobre quien anotó de
buena fe.

- El tope es `CashSession.expectedCashNow` (fondo + ventas en efectivo + movimientos), extraído
  al modelo porque la fórmula estaba copiada en cuatro pantallas —cierre, cuadre sorpresa (dos
  veces) y traslado— y ahora además decide si alcanza el dinero. `expectedCash`, el campo, solo
  se llena al cerrar: mientras la sesión está abierta vale 0 y no sirve.
- Se valida contra la sesión **recién leída**, no la que traía la pantalla: entre abrir la
  captura y guardar pudo entrar una venta o salir un retiro. Mismo criterio que `addPayment`.
- Al corregir, lo que esa misma compra ya le descontó a esa gaveta **vuelve a contar como
  disponible**: sin eso, subir de Q50 a Q60 se compararía contra una caja a la que ya se le
  quitaron los Q50.
- La UI apaga el botón y dice cuánto hay; el ViewModel revalida y lanza **antes de escribir
  nada**, así un rechazo no deja compra a medio guardar ni inventario movido.

#### Del faltante de la gaveta al documento que lo explica

`CashMovement.sourcePurchaseId` liga el movimiento con la compra que lo causó. Sin él, el
renglón de la caja era un texto suelto: la cajera veía "-Q325" y no tenía a dónde ir.

- `CashSessionDetailScreen` ("Movimientos de la caja", desde el menú de la caja abierta en el
  POS) muestra **"Corregir" y "Eliminar"** en cada gasto, mientras la sesión siga OPEN. Son dos
  botones y no una fila tocable: una de las dos acciones devuelve efectivo y revierte
  inventario, y un toque ambiguo deja al usuario adivinando cuál va a pasar.
- **Quien opera la caja puede corregir y borrar lo de su propia gaveta sin los permisos de
  Compras** (`canManageOwnDrawerPurchase`): compra rápida + atribuida a una caja + esa caja
  todavía abierta. Es el error del día —325 en vez de 32.50, o dinero que no salió de ahí— y
  exigir `EDIT_PURCHASES` obliga a llamar a alguien antes de cerrar, con lo que el desvío
  termina cuadrándose a mano. La guarda vive en el ViewModel (`saveQuickPurchase` y
  `deletePurchase`), no en la ruta, porque es la ruta la que no puede saber de qué gaveta se
  trata. Con la caja cerrada nadie la toca: ese cierre ya lo firmó alguien.
- **Nada se borra en silencio**: corregir deja un movimiento "Corrección: X" y eliminar uno
  "Eliminado: X" en el historial de la caja.
- El formulario rápido **se niega a corregir una factura** (`wrongKind`): se llega ahí desde un
  movimiento, que no distingue qué clase de compra pagó el efectivo, y `saveQuickPurchase`
  trataría una INVOICE como alta — creando una compra nueva y dejando la vieja intacta, con el
  inventario contado dos veces.
- El botón "Cerrar sesión" que tenía esta pantalla **se quitó**: hace lo mismo que "Cerrar
  caja" del POS, que es el único lugar desde donde se llega acá.
- **`deletePurchase` devuelve el efectivo** con el mismo `syncPurchaseCashMovement` que lo
  descontó, pasando total nuevo 0. Un reverso escrito aparte termina usando otra cantidad y
  deja la caja cuadrando contra un gasto que ya no existe. Con la caja cerrada no se toca y se
  avisa.

#### La caja que nadie cerró se cierra sola

`CashSessionRepository.autoCloseStaleSessions(companyId, todayStartMillis)` cierra las sesiones
OPEN de **días anteriores** con `countedCash = expectedCashNow` — nadie contó, así que se asume
pago exacto. Una caja abandonada bloquea su registradora (no se puede abrir otra sesión ahí) y
arrastra las ventas de ayer al cuadre de hoy.

- **`CashSession.autoClosed` marca que la cerró el sistema.** Sin ese campo, el reporte
  mostraría una caja perfectamente cuadrada y nadie sabría que nunca se contó — peor que un
  faltante declarado. Las notas de la sesión lo dicen también, en texto.
- **Solo días anteriores**, nunca la de hoy: un turno puede cruzar la medianoche.
- Se dispara al entrar al POS (`CashSessionViewModel.load`), **antes** de observar las sesiones
  abiertas, y nunca lanza: que falle no puede impedir abrir caja. Como no hay servidor que
  corra por reloj, una caja abandonada sigue abierta hasta que alguien entre al punto de venta
  — tarde, pero antes de que estorbe. **Un job en Cloud Functions a hora fija queda pendiente.**
- El filtro por fecha va **en memoria**: `openedAt` puede venir como `Timestamp` en sesiones
  viejas y como número en las nuevas, y Firestore ordena por tipo antes que por valor. Mismo
  motivo por el que `getPurchases` no filtra en el servidor.
- Quien llega a abrir caja ve un snackbar diciendo cuántas se cerraron y que no se contaron.

**Aviso de efectivo insuficiente en `PurchaseTotalsBar`: renglón propio, sin `weight`.** Vivía
dentro de la columna del botón con `Modifier.weight(1f)`; la barra se mide sin alto fijo, así
que el texto se estiraba hasta ocupar la pantalla y el formulario de arriba quedaba en cero
alto — se veía "corrido para arriba y trabado" justo cuando la factura pasaba lo que había en caja.

#### El cierre muestra QUÉ salió (`CashCloseMovementsCard`)

`CashSessionCloseScreen` decía "Gastos y retiros Q 385" sin detalle, y nadie acepta un faltante
que no sabe de dónde viene. Entre el resumen y el conteo va una tarjeta con un renglón por
salida (hora · qué · proveedor · quién · monto) y miniatura de 40dp de la foto del comprobante que
se abre con `ZoomableImageDialog`. Primeros 4 y "Ver las N salidas" — el cierre es para contar,
no para leer. Entradas que no son ventas, debajo y enteras.

- Renglones de `cashSessionDetailItems` (`model/CashMovementTotals.kt`), mismo recorrido que
  `cashMovementTotals`: cada corrección se pega a su gasto ("corregido") y lo eliminado (neto 0)
  no se lista, así la suma de la lista es exactamente el total del resumen. Test en
  `CashMovementTotalsTest`.
- El concepto y la foto viven en la compra, no en el movimiento: `CashSessionViewModel` lee las
  compras de los `sourcePurchaseId` en paralelo (`movementPurchases`). Si una falla, el renglón
  cae al texto del movimiento.

#### Anotar ingreso (`CashIncomeDialog`)

Existía el camino para pagar un gasto de la gaveta pero no el inverso, y el dinero que entra
pasa igual de seguido: la caja central manda cambio a media mañana, o alguien repone lo que
sacó. Sin registrarlo, al cuadrar aparece un **sobrante** que nadie explica — y un sobrante
inexplicado se investiga igual que un faltante.

- Se abre desde el menú de la caja abierta en el POS. Monto + razón + origen.
- **El origen son dos platas distintas**, no una etiqueta: lo que baja de la caja central ya
  era del negocio y solo cambió de bolsillo; lo que puso alguien de su cuenta todavía no. Se
  guardan con dos tipos que el modelo YA tenía —`FUND_ADDITION` ("De caja general") y `DEPOSIT`
  ("Otros")— en vez de un campo nuevo, así el cuadre y el cierre del día los suman sin
  enterarse del cambio.
- **No es un traslado entre cajas**: eso es `CashTransferDialog`, que escribe los dos
  movimientos espejo para que el dinero no aparezca duplicado. Acá el otro lado no es una
  gaveta del sistema.
- Al guardar se recarga el POS: el efectivo esperado cambió, y de ese número cuelga el tope de
  lo que se puede pagar de la caja.

#### "Movimientos de caja" no es un resumen de la sesión

Mientras la caja está abierta, la pantalla contesta **una** pregunta: qué entró y qué salió que
no fue una venta. El fondo inicial y las ventas del turno se sacaron de ahí — eso es el cuadre,
y mezclarlo dejaba un resumen a medias compitiendo con el cierre. `SessionHeaderCard` **solo se
dibuja con la sesión CERRADA**, porque ahí la misma pantalla es el comprobante del cierre (es a
donde navega `CashSessionCloseScreen.onClosed`). El título también cambia: "Movimientos de
caja" abierta, "Detalle de sesión" cerrada.

#### Totales de la caja: `model/CashMovementTotals.kt` (lógica pura, con tests)

Nada se borra del historial de una gaveta: eliminar o corregir escribe un movimiento que
compensa. Es correcto para auditar, pero rompe el conteo ingenuo — un gasto de Q100 eliminado
deja DOS renglones, y sumar negativos por un lado y positivos por el otro diría "gastos Q100,
ingresos Q100" el día que no se movió nada.

`cashMovementTotals` agrupa **cada ajuste con el movimiento que corrige** (`netCashEffect`) en
vez de contarlo aparte. El vínculo es `sourcePurchaseId` para los gastos —cuando se escriben, el
id del movimiento original no está a mano— y `sourceMovementId` para los ingresos, que no tienen
documento detrás. `isAdjustment()` se reconoce por el prefijo del motivo ("Eliminado:" /
"Corrección:"), y no por un flag, porque el texto ya tenía que decirlo y un booleano aparte
podría contradecirlo.

**Un ajuste huérfano sigue contando** (su original ya no está: dato viejo, o borrado a mano).
Filtrar por `isAdjustment()` lo descartaba y escondía efectivo que de verdad se movió — lo
atrapó `CashMovementTotalsTest`, no la prueba en dispositivo.

#### Corregir y eliminar un ingreso

`CashSessionViewModel.adjustMovement(...)` **escribe la diferencia, no reescribe el original** —
igual que la corrección de un gasto y que `applyStockDelta`. `newAmount = 0` lo revierte entero.
El tipo del ajuste se elige para que el neto dé exactamente el monto nuevo (sube → mismo sentido
que el original; baja → contrario).

- La fila muestra **"Corregido a Q X"** cuando el neto difiere del monto anotado: el original no
  se reescribe, así que sin esa línea seguiría mostrando el monto viejo y nadie entendería por
  qué no cuadra.
- **Un traslado entre cajas no se toca desde acá**: su espejo vive en la otra gaveta, y
  compensarlo solo de este lado haría aparecer dinero de la nada en la otra caja. Se deshace con
  otro traslado. Un ajuste de cuadre sorpresa tampoco: documenta un conteo ya firmado.
- El gasto se corrige **donde se anotó** (tiene renglones, catálogo y foto); el ingreso es un
  monto y una razón, así que se corrige en un diálogo sin cambiar de pantalla.

#### Detalles de la lista de movimientos

- **`CashMovementType.listLabel(movement)`** en vez de `label(type)`: "Depósito" no dice nada
  cuando el dinero volvió porque se eliminó un gasto —quien lee se pregunta quién depositó y
  por qué—, así que sale "Devuelto — gasto eliminado" / "Ajuste — gasto corregido". El texto se
  deduce del `reason`, que ya distingue los dos casos.
- **Un gasto ya revertido no se corrige ni se borra otra vez**: la fila sigue visible (así se ve
  qué pasó) pero sin botones y marcada "Ya eliminado". Se detecta buscando un movimiento con el
  mismo `sourcePurchaseId` cuyo motivo empieza con "Eliminado:" — sin campo nuevo. Borrar dos
  veces devolvería el dinero dos veces.
- El borrado lo hace `PurchaseViewModel`, que no conoce esta lista: `CashSessionDetailScreen`
  observa su `isDeleted` y **recarga** (`observeSession`). Sin eso el movimiento de devolución
  aparecía solo al salir y volver a entrar, y eliminar parecía no haber hecho nada.
- **`LoadingOverlay` en las tres esperas del flujo**, no solo en la carga: abrir una corrección
  (leer la compra y sus líneas), guardar un gasto (compra + líneas + inventario + gastos del mes
  + movimiento de caja) y eliminarlo (revertir todo eso). Son varios segundos cada una contra
  Firestore, y un botón deshabilitado no alcanza como señal porque la pantalla se ve igual: se
  lee como que el toque no hizo nada, **y el segundo toque es lo que produce el registro o el
  borrado doble** que las guardas después tienen que atajar.

#### `POSUiState.isCheckingOut`, separado de `isLoading`

El diálogo "Procesando venta… descontando inventario" colgaba de `isLoading`, que sube también
en `POSViewModel.load()` — o sea **cada vez que se vuelve al POS**, por ejemplo al terminar de
anotar un gasto. Anunciaba una venta que no existía. `isCheckingOut` se enciende solo en el
cobro.

#### Barras inferiores y edge-to-edge

Desde Android 15 la app dibuja de borde a borde y `Scaffold` **no** aplica insets al
`bottomBar`: el botón de guardar quedaba debajo de los botones del teléfono, sin poder tocarse.
Toda barra inferior fija lleva `navigationBarsPadding()` — hoy la de `QuickPurchaseFormScreen` y
`PurchaseTotalsBar`. El resto de la app todavía no lo aplica en ningún lado; conviene revisarlo
al tocar cada pantalla con barra fija.

#### Dos permisos, porque son dos riesgos distintos

| Permiso | Qué habilita |
|---|---|
| `ADD_VARIABLE_EXPENSE` | Anotar desde la caja que se está operando (POS). Sale de su gaveta y le tiene que faltar al cuadrar. |
| `ADD_GENERAL_EXPENSE` | Anotar de caja general — el acceso de Operaciones y el FAB de Gastos. No le descuenta a nadie. |

La ruta `Screen.CashExpenseForm` elige cuál pedir según venga o no `sessionId`. Con un solo
permiso, una cajera podía cargar a "caja general" un gasto que sí salió de su gaveta, y ahí el
faltante del cierre no lo explicaba nada. `ExpenseForm` (editar un gasto viejo) pide
`ADD_GENERAL_EXPENSE`: es trabajo de administración.

#### Categorías: dos listas curadas, un solo enum

`ExpenseCategory` sigue siendo una sola lista para que el reporte sume por un eje, pero **no se
ofrece entera en ningún formulario**: `FIXED_EXPENSE_CATEGORIES` (Alquiler, Planilla, Servicios,
Seguridad, Depreciación…) en Costos Fijos, `OPERATIONAL_EXPENSE_CATEGORIES` (Limpieza, Empaque,
Herramientas, Cocina/Cafetería, Papelería, Uniformes, Mantenimiento, Transporte, Otros) al
anotar el gasto del día. Mismo criterio que `INGREDIENT_CATEGORIES` vs `SUPPLY_CATEGORIES`, y
las operativas están calcadas de `SUPPLY_CATEGORIES` a propósito: quien anota "cloro" y quien da
de alta el insumo cloro clasifican la misma cosa.

Las entradas viejas **no se borran del enum** —los documentos guardados las traen—, y un
dropdown que recibe una categoría fuera de su lista la ofrece igual, para poder corregirla en
vez de mostrar la fila en blanco (mismo criterio que `usableInRecipes(keepIds)`).
`ExpenseFormScreen` sí ofrece la lista completa: solo edita gastos que ya existen.
`ExpenseCategoryListsTest` verifica que ninguna categoría quede sin formulario que la ofrezca.

**No hay categoría "restaurante"**: el restaurante o la cafetería entran como **sucursal**, así
que sus gastos ya se separan solos y lo único que faltaba era el renglón de en qué se fueron —
para eso está `COCINA`.

### A quién le corresponde un gasto: `CostCenter` (oct 2026)

Primer paso de la rentabilidad por sucursal (`arquitectura/propuesta-rentabilidad-por-sucursal.md`).
El dinero de todas las sucursales cae en un saco común y las facturas se pagan de la caja
general, así que `paidFrom` ("de qué caja salió") no dice quién gana y quién pierde. Cada gasto
lleva además `costCenter`: vacío = la sucursal del documento, `GENERAL` (contador, sueldo del
dueño, vehículo, publicidad; **no se reparte**) o `DELIVERY` (piloto y camión; se repartirá
entre las tiendas por entregas).

- **Campo aparte, no un `branchId` especial.** `branchId` sigue siendo dónde se anotó: de ahí
  cuelgan inventario, caja y permisos. Vacío = sucursal, así los documentos viejos se leen bien
  sin migrar (`CostCenter.normalize`).
- **Vive en la línea** (`PurchaseLine.costCenter`) y baja al `Expense` por `expensesForPurchase`.
  En la compra rápida es por renglón (la gasolina del camión y los trapos de la tienda van en el
  mismo desembolso). En la factura se elige UNA vez para el papel y `withExpenseCostCenter` lo
  estampa al guardar sobre las líneas que se gastan; las de inventario quedan vacías.
- **Cuándo se obliga.** Desde la caja general (sin `sessionId`) es donde se mezclan las facturas
  de todos: en la compra rápida cada renglón de gasto tiene que decirlo (el borrador arranca en
  `null`), y en la factura solo si trae líneas SERVICE (la luz, el contador). Un suministro casi
  siempre es de la sucursal que lo recibe y no se obliga. Desde la gaveta de una tienda se asume
  la tienda.
- **Reporte.** `aggregateExpensesForPeriod` agrupa con `costCenterReportKey`: Generales y Reparto
  salen en su propio renglón del "Por sucursal" del reporte financiero en vez de cargarse a la
  planta donde se pagaron.
- También en `FixedBudgetItem` (con compartido, `branchId` vacío y `branchName` = "Generales" /
  "Reparto" para que la lista agrupe sola), en `Employee` ("¿A quién se le carga su sueldo?") y en
  el formulario de gasto manual. Selector común: `presentation/expenses/CostCenterPicker.kt`.
  Tests: `CostCenterTest`.
- **Planta con tienda (oct 2026).** Cada planta tiene su mostrador, y el panadero es costo del pan
  mientras la cajera es gasto de la tienda. En una sucursal que produce Y vende (`Branch.splitsStore`)
  el selector ofrece "Planta X" (= `BRANCH`, vacío: lo ya anotado ahí queda como planta sin migrar),
  "Tienda X" (`STORE`) y "Local compartido" (`SITE`: renta, luz, agua), que se parte con
  `Branch.storeSharePercent` (lo pone el dueño en la ficha de la sucursal, "Tienda de la planta").
  La única regla de reparto es `plantShare(costCenter, branch)`; en una tienda pura los tres son lo
  mismo. Aplica a gastos, compras, costos fijos y colaboradores.
- **Costos fijos → gastos del mes.** `FixedBudgetItem` era solo un presupuesto. Ahora la lista tiene
  "Anotar los de {mes}": crea un `Expense` por renglón con id fijo `fijo_{item}_{YYYY-MM}`
  (`toMonthlyExpense`), así un segundo toque o un segundo teléfono no duplican. No toca ninguna caja.
  Botón y no automático: lo variable (la luz) se corrige al monto real. **Regla operativa:** lo que
  entra por factura de proveedor NO va en Costos fijos, o se cuenta dos veces.
