# Compras, proveedores y lotes FEFO

> Detalle movido desde CLAUDE.md. Leer antes de tocar este módulo.

### Compras — una factura, cuatro tipos de línea

La factura de la abarrotería trae huevos, tostadas y cloro en el mismo papel, con un solo
total, un solo crédito y un solo abono. Por eso los tres flujos viven en el **mismo**
documento `Purchase` y lo que decide el efecto es `PurchaseLine.lineKind` — partirla en tres
documentos con el mismo número de factura dejaría "¿ya le pagué a Molinos?" sin respuesta.

| `lineKind` | Catálogo | Qué mueve al guardar |
|---|---|---|
| `INGREDIENT` | Insumo `usage=PRODUCTION` | `BranchStock` + Kardex + lote FEFO si es perecedero |
| `RESALE_PRODUCT` | Producto `isInHouseProduced=false` | `FinishedProductStock` + movimiento |
| `SUPPLY` | Insumo `usage=SUPPLY`, `tracksStock=true` | `BranchStock` + Kardex **y** `Expense` |
| `SUPPLY_NO_STOCK` | Insumo `usage=SUPPLY`, `tracksStock=false` | solo `Expense` |
| `SERVICE` | ninguno (texto libre) | `Expense` + opcional `AssetHistoryEntry` |

`Purchase.lineKinds` denormaliza los tipos presentes — misma razón que `itemIds`: filtrar
"compras de administración" sin abrir la subcolección de cada factura. Vacío = compra vieja,
que por definición es de materia prima (`effectiveLineKinds`).

**El suministro reutiliza `Ingredient`, no un catálogo nuevo.** Un `Supply` paralelo habría
duplicado formatos de compra, stock por sucursal, Kardex, alertas de mínimo, la pantalla de
ajuste y la captura entera — cinco repos y una UI para obtener lo que `Ingredient` ya hace.
Lo único que de verdad difiere es dónde se ofrece, y eso lo resuelve
`Ingredient.usage: PRODUCTION | SUPPLY` (default `PRODUCTION`, sin migración). Los pickers
de receta y preparación filtran con `usableInRecipes(keepIds)`, que **conserva** los que la
receta ya tiene aunque estén mal clasificados: si alguien reclasifica por error, la fila
debe seguir mostrando su nombre para poder corregirla, no quedarse en blanco.

**El cloro se gasta el día que se compra.** Contablemente es inventario hasta consumirse,
pero nadie registra el consumo de cloro y ningún costo de venta lo recoge después: si no se
expensa al comprarlo, no aparece nunca en el estado de resultados. Su inventario existe para
contestar "¿me queda?", no para valorar nada, así que no hay doble conteo.

**El suministro que no se cuenta: `Ingredient.tracksStock`.** La bolsa y la bandeja
desechable se compran por fardo y se gastan de a una con cada venta —y ese consumo no lo va a
registrar nadie. Como suministro normal su `BranchStock` solo sube: a los tres meses dice 8.000
y en la tienda hay 400, la alerta de mínimo nunca suena, y un número que miente es peor que no
tener número. Como línea de servicio de texto libre se capturaba de a un diálogo por renglón y
sin presentación de compra.

`tracksStock` (default `true`, sin migración) lo resuelve **por ítem** y sin catalogo nuevo: el
ítem sigue siendo un `Ingredient` del catálogo del proveedor —aparece como **fila** en su
factura, con su presentación, su precio y su historial de precio— y lo único que no hace es
inventario. `ingredientPurchaseLineKind(ingredient)` es el **único** lugar que traduce insumo →
tipo de línea, y lo usan los cuatro caminos que arman una línea desde un insumo (las dos filas
del catálogo, el insumo suelto del proveedor ocasional y el gasto rápido).

- **El switch solo existe para suministros.** La materia prima siempre lleva conteo —de ese
  stock sale el descuento de producción— y `ingredientPurchaseLineKind` lo fuerza aunque el
  documento venga mal marcado.
- **Las guardas de inventario van por `purchaseLineTouchesStock(kind)`, no por `!= SERVICE`.**
  Una línea `SUPPLY_NO_STOCK` trae `ingredientId` lleno, así que pasaba por todos los filtros
  que miran ese campo: `applyStockForPurchase`, las DOS funciones de índice de `applyStockDelta`
  y `reconcileLotsPerBranch` la habrían metido al stock igual.
- **Al apagar el conteo se descarta la alerta de mínimo**: no hay stock contra el cual
  dispararla, y guardada quedaría esperando un evento que nunca llega.
- Sigue siendo un ítem con id, así que el historial del proveedor lo agrupa por `ingredientId`
  y la búsqueda de la lista de compras lo encuentra — a diferencia del texto libre.

**Bug que esto destapó: las filas del catálogo no ponían `lineKind`.** Todo suministro
capturado desde la factura entraba como `INGREDIENT`, o sea con stock pero **sin generar su
`Expense`** — el gasto del mes nunca aparecía. Solo la compra rápida y el insumo suelto lo
clasificaban bien. Por eso la traducción vive ahora en una función y no repetida en cada fila.

**El gasto rápido no da de alta conceptos.** Tuvo un switch "Lo compro seguido" y se quitó: lo
que se le compra seguido a un proveedor tiene catálogo y se captura por su factura, donde sale
como fila. En el renglón rápido solo se muestran cantidad y unidad cuando la línea **ya** venía
con un concepto (corregir un gasto viejo), para no borrar lo que ya estaba.

**El servicio es el único tipo sin catálogo.** La ferretería vende algo distinto cada vez;
dar de alta un ítem por reparación llenaría el catálogo de cosas que no se vuelven a
comprar. `conceptName` + `expenseCategory` + `assetId` opcional. Con activo, escribe también
su `AssetHistoryEntry` (MAINTENANCE por defecto) — el mantenimiento capturado desde la
factura actualiza la próxima fecha del horno sin teclearlo dos veces. El combustible de la
camioneta cae en el activo del vehículo, que es la razón por la que los vehículos son
activos fijos.

**Lo que SÍ se repite se le puede pegar un `PurchaseConcept`** (`model/PurchaseConcept.kt`,
colección plana `companies/{c}/purchaseConcepts`): bolsas, cajas de cartón, el flete del
lunes. Es un catálogo de nombres con unidad, categoría y último precio — **sin inventario de
ninguna clase**. No es un `Ingredient` con otra bandera a propósito: un insumo, aunque sea de
operación, trae `BranchStock`, Kardex, alerta de mínimo y pantalla de ajuste, que es justo lo
que acá se pidió no tener; y no es un `Product` porque no se vende.

La línea sigue siendo `SERVICE` —no mueve inventario y genera su `Expense`— y lo único que
agrega es `PurchaseLine.conceptId`. **Ese id es lo único que permite sumar el historial**: sin
él, "Bolsas #5", "bolsas 5" y "bolsa # 5" son tres cosas a los tres meses y "¿cuántas le
compré?" no tiene respuesta. `conceptName` se guarda igual como snapshot, así una factura
vieja sigue diciendo lo que decía aunque el concepto se renombre.

- **`PurchaseLine.quantityReceived` y `conceptUnit` ahora se capturan** en la línea de
  servicio (antes se guardaba siempre 1 con el monto entero en el costo unitario). La cantidad
  viene en 1: un flete o una reparación se dejan así y el formulario se comporta como siempre;
  las 200 bolsas son un campo de más, y sin él el historial dice cuánto se gastó pero nunca
  cuánto se compró. El monto tecleado sigue siendo el TOTAL del renglón y se guarda dividido,
  para que `lineTotal` lo devuelva exacto.
- **Se ofrece, no se exige.** El texto libre sigue siendo lo correcto para lo que no se vuelve
  a comprar. El switch "Recordar para el historial" nace **encendido en la factura** (lo que
  trae un proveedor con factura suele ser lo que trae siempre) y **apagado en el gasto anotado
  desde la caja** (ahí el caso típico es el taxi o la escoba de una sola vez).
- **Antes de crear se busca por nombre** (`findByName`, sin tildes ni mayúsculas): teclear de
  nuevo "Bolsas #5" engancha el concepto que ya existe en vez de abrir un segundo id para lo
  mismo — que es exactamente lo que el catálogo viene a evitar.
- El catálogo **no se gatea con `usesServicePurchases`**: esa bandera decide si la FACTURA
  dibuja su sección de servicios, y el gasto anotado desde la caja no pasa por ahí. Gatearlo
  dejaría al caso más común sin historial sumable.
- `syncPurchaseConcepts` (los tres caminos de guardado: factura, edición y compra rápida) deja
  en el concepto a quién se le compró, a cuánto y cuándo — escritura **parcial**
  (`recordPurchase`), porque quien registra la factura no tiene el concepto completo en la
  mano y un `set()` le borraría el nombre o la unidad a quien lo esté editando.

**La ficha del proveedor contesta "¿qué le compro y cuándo?"** (`SupplierDetailScreen` +
`model/SupplierPurchaseHistory.kt`, tests en `SupplierPurchaseHistoryTest`). Existía el dato y
no existía la pregunta: había que ir a Compras, filtrar por proveedor y abrir factura por
factura. Dos niveles, y la división es por costo de lectura:

- **Totales y lista de compras salen de los ENCABEZADOS** (`totalAmount`, `itemNames`,
  `balanceDue` ya denormalizados), así que se cargan al abrir la ficha sin leer una sola
  subcolección. Cada fila abre su factura.
- **"Ver qué le compro y cuánto" va detrás de un botón**: las cantidades viven en las líneas y
  eso es una consulta POR FACTURA. Se limita a `MAX_HISTORY_PURCHASES = 40` — con más que eso
  la respuesta ya está clara y seguir leyendo solo cuesta.
- `buildSupplierItemHistory` cuenta **compras, no renglones** (una factura que trae el mismo
  ítem dos veces es una compra), agrupa por `supplierHistoryKey` —concepto > texto normalizado
  > producto > insumo— y recalcula el precio unitario del importe, para que capturar con
  presentación y capturar suelto den el mismo número. El producto manda sobre el insumo para
  que la línea redirigida de la leche se agrupe con las otras compras de ese renglón.
- Se filtra en memoria sobre la colección completa, mismo motivo que la lista de Compras:
  `date` puede ser `Timestamp` en las viejas y número en las nuevas.

`presentation/purchases/PurchaseSideEffects.kt` (lógica pura, `PurchaseSideEffectsTest`)
proyecta la factura a `Expense`s y `AssetHistoryEntry`s. `syncPurchaseSideEffects` en el
ViewModel es el **único** lugar que los escribe, y lo llaman los cuatro caminos: registrar,
recibir, editar y borrar (borrar pasa lista vacía, que es exactamente "quitá todo lo que
esta factura dejó"). Reusar la misma reconciliación en vez de escribir un reverso aparte es
lo que hace que el borrado herede las guardas ya probadas — mismo criterio que
`applyStockDelta`. Se reconstruye entero y no por diferencia porque `updatePurchase`
reescribe la subcolección con ids nuevos: no hay línea vieja con la que emparejar
(`ExpenseRepository.replaceExpensesForPurchase` borra por `sourcePurchaseId` y reescribe).

Los gastos con `sourcePurchaseId` **no se editan ni se borran desde el módulo de Gastos**:
la compra los reconstruye completos al guardarse, así que el cambio duraría hasta la
siguiente corrección de esa factura. El detalle lo dice en vez de solo esconder los botones.

**Los gastos siguen la misma regla que el stock**: una factura `PENDING_RECEIPT` no gastó
nada todavía, y devolverla a pendiente lo revierte.

**No todas las panaderías usan los tres.** `Organization.usesResalePurchases` /
`usesSupplyPurchases` / `usesServicePurchases`, todas en `false` por default para que ninguna
empresa ya operando vea secciones nuevas hasta pedirlas. Se encienden en
`OrganizationSettingsScreen`. La materia prima no lleva bandera — es el flujo base. Se eligió
bandera explícita y no derivarlo del catálogo porque derivarlo lo vuelve indescubrible: una
panadería que quiera empezar a vender tostadas nunca vería la sección que se lo permite.

**Un producto puede vender del inventario de un insumo** (`Product.linkedIngredientId` +
`ingredientUnitsPerSale`). El caso es el huevo: entra a las recetas Y se vende por docena. Es
un solo pozo porque es una sola caja — comprarlo dos veces para tenerlo en dos inventarios es
lo que dejaba los conteos sin cuadrar. `SaleLine` lleva el vínculo como **snapshot** (igual
que `priceSource`): así `FirebaseSaleRepository.createSale` decide a qué inventario pegarle
sin conocer el catálogo de productos, y la venta sigue diciendo de dónde salió aunque mañana
se desvincule. La venta descuenta `BranchStock` y escribe Kardex tipo `VENTA`; `costAtSale`
sale de `Product.purchaseCost` porque no hay `avgUnitCost` que consultar. `POSViewModel.stockOf`
convierte existencias del insumo a piezas vendibles — sin eso el aviso de sobreventa marcaría
en rojo toda venta de huevo, y un aviso que siempre suena deja de avisar.

**Comprar ese producto NO crea un inventario paralelo: la línea se redirige al insumo.**
`resalePurchaseLine(..., linkedIngredient)` —el único lugar que arma una línea de reventa—
devuelve una línea `INGREDIENT` (o `SUPPLY`, según el `usage`) cuando el producto está
respaldado por un insumo, convirtiendo la cantidad con `ingredientUnitsPerSale`: una caja de
12 cartones de leche entra como 12 lt al `BranchStock` del insumo. Sin esa redirección la
compra escribía un `FinishedProductStock` que **`buildStoreInventoryRows` ni siquiera dibuja**
para estos productos y que ninguna venta descuenta: la leche desaparecía en silencio, y si
además se capturaba el insumo quedaba contada dos veces.

Lo que cambia es el destino, no el papel: `packagesReceived`, `packageCost`, `formatId` y
`productId` se conservan, así `lineTotal` sigue dando el importe exacto de la factura y la
presentación sigue actualizando su precio — por eso `formatsWithPaidPrices` y
`syncResaleFormatPrices` emparejan por **`productId`** y no por tipo de línea, y el bucle que
actualiza formatos de insumo salta las líneas que traen producto. `matchKey()` también pasó a
llaverse por producto cuando lo hay: si la fila redirigida compartiera clave con la fila del
insumo, capturar las dos se reemplazarían en silencio en vez de sumarse. La fila del catálogo
dice a dónde va ("Entra al inventario de Leche (insumo)") — quien captura la eligió creyendo
que surtía la tienda.

**Reventa con presentaciones de compra** (`Product.purchaseFormats`, lógica pura en
`model/ResalePurchaseFormat.kt`, tests en `ResalePurchaseFormatTest`). Antes un producto de
reventa tenía UN `supplierId` y un `purchaseCost` por unidad: la lata de Coca-Cola no podía
comprársele a la distribuidora y al mayorista, y la factura de "1 caja" había que teclearla
como 24 unidades con el costo dividido a mano. Ahora es el mismo modelo que los insumos: la
relación producto↔proveedor es de muchos a muchos y vive en la `PurchaseFormat`; el catálogo de
un proveedor se **deduce** (`suppliedBy`). El inventario sigue siendo uno por producto — es la
misma lata venga de quien venga, y `avgUnitCost` promedia los precios de ambos.

- **Arreglos paralelos en el documento del producto** (`purchaseFormatIds`, `...UnitsPerPackage`,
  etc., en `data/ProductPurchaseFormatFields.kt`, compartido entre el repo Android y el JS), **no
  subcolección** como en insumos: `getProducts` lo lee media app (POS incluido) y sería una
  lectura extra por producto, pan propio incluido.
- `PurchaseFormat.unit` = presentación de venta del producto; `unitsPerPackage` = cuántas trae.
  `resalePurchaseLine` es el **único** lugar que arma la línea: `quantityReceived` en unidades
  (lo que se cuenta en tienda), `packagesReceived` + `packageCost` para que `lineTotal` dé el
  importe exacto del papel, `unitCost = packageCost / unitsPerPackage`.
- La fila de captura ofrece las presentaciones del proveedor **más "Por unidad"** (latas
  sueltas). Al reabrir una compra manda el `formatId` de la línea, no la predeterminada de hoy.
- `purchaseCost`/`supplierId`/`supplierName` quedan como **caché derivada** de la presentación
  predeterminada (`withDerivedPurchaseReference`): el POS usa `purchaseCost` como costo de venta.
- **Producto viejo**: `suppliedBy` cae al `supplierId` único si no hay presentaciones (no
  desaparece de su proveedor), y el formulario lo convierte en su primera presentación al
  abrirlo (`purchaseFormatsForEditing`) — guardarlo migra sin script.
- **Proveedor ocasional**: `LooseResaleLineDialog` — producto + presentación existente (de cualquier proveedor) o "Por unidad" + TOTAL pagado. No crea presentación nueva (misma regla que el insumo suelto). La barra de total ahora también sale con proveedor ocasional (`hasSupplier`): antes solo con proveedor del catálogo, y como es el único botón de guardar esas compras no se podían registrar.
- En plena captura: "Agregar producto" en la sección de reventa y "Definí la presentación" en
  una fila sin ninguna → `addResaleSupplierFormat` (escritura parcial vía
  `ProductRepository.updatePurchaseFormats`). El precio pagado actualiza la presentación al
  registrar (`formatsWithPaidPrices`), igual que con insumos.

**Reventa que caduca** (`Product.isPerishable`, switch "Caduca" en Datos del producto, solo
reventa). Igual que el insumo perecedero: en la factura aparece en la sección "Vencimientos" con
lote y fecha, que se guardan en `PurchaseLine.expiryDate/lotCode` y se ven en el detalle de la
compra. **No hay libro de lotes de terminado ni aviso en el Home** para productos: el aviso de
insumos sale de `stockLots`, que consume FEFO en `updateBranchStock`; para productos haría falta
un libro análogo descontado por venta/merma/despacho, y quedó pendiente. `perishableLines` mira
también los catálogos completos (antes la línea suelta del proveedor ocasional nunca pedía fecha).

**El vencimiento no se pierde al corregir la cantidad.** Las filas del catálogo rearman la línea
en cada tecla sin conocer lote ni fecha; `updateDraftLine` los conserva de la línea anterior, y
solo `updateLineExpiry` (la sección Vencimientos) puede cambiarlos o vaciarlos. Antes, anotar la
fecha y luego tocar la cantidad la borraba en silencio — también en insumos.

**Cómo lo llama el proveedor: `PurchaseFormat.supplierItemName`, en los tres catálogos.**
Se intentó primero que en un insumo bastara la propia `description`, pero el diálogo que la
pide dice "Ej: Saco 50 lb": en la práctica esa `description` es el EMPAQUE en materia prima
igual que en reventa, así que el nombre del proveedor no tenía dónde vivir y la fila de la
factura no tenía qué mostrar. Ahora es un campo aparte en los tres, con la misma etiqueta
("Descripción de la presentación") y el mismo orden — el suministro ya venía bien porque
`IngredientFormScreen` le pasa el `ResalePurchaseFormatDialog` completo; lo que faltaba era el
diálogo de materia prima. En reventa se persiste como arreglo paralelo
(`purchaseFormatSupplierItemNames`) y en insumo como campo de su subcolección.

**Se dibuja en las tres filas del catálogo de la factura** (`InvoiceNameLine`, en cursiva y
azul, arriba del empaque): `IngredientTableRow` (tablet), `IngredientPurchaseRow` (teléfono) y
la fila de reventa. Las tres sacan la lista de `supplierItemNamesFor(supplierId)`
(`model/PurchaseSearch.kt`), que a diferencia de `invoiceNamesFor` **no incluye la
`description`**: el empaque ya va en su propio renglón de la fila y repetirlo arriba no agrega
nada. Mostrar y buscar quieren cosas distintas — al buscar conviene que enganche con lo que
sea, al mostrar conviene no decir dos veces lo mismo.

La captura tiene **una sola búsqueda** arriba de materia prima, suministros y reventa
(`matchesPurchaseSearch`, `model/PurchaseSearch.kt`, test `PurchaseSearchTest`): por nombre del
catálogo o por esas descripciones de ESE proveedor, sin tildes y por palabras en cualquier orden.

**Código de barras** opcional en `Ingredient.barcode` y `Product.barcode` (el mismo del
empaque sirve para comprar y para cobrar). `ui/BarcodeField.kt` es el campo reutilizable y
`ui/BarcodeScanner.kt` el expect/actual: Android usa
`com.google.android.gms:play-services-code-scanner`, que **no requiere declarar ni pedir el
permiso de cámara** —el escaneo corre en la UI de Play Services— y baja su módulo bajo
demanda; iOS/web/wasm devuelven `false` en `isBarcodeScanSupported()` y esconden el botón.
Un lector USB/Bluetooth funciona en todas las plataformas sin código: para la app es un
teclado que escribe el número y manda Enter, por eso `BarcodeField` filtra los espacios.
En el formulario de compra, escanear **busca**: deja el renglón solo en pantalla y la
cantidad se sigue tecleando, porque la factura dice cuántos vinieron, no el empaque.

`IngredientPickerDialog` vive en `presentation/ingredients/` y es `internal`: estaba
duplicado como `private` en receta y preparación y estaba por triplicarse con el producto
respaldado por insumo. Filtrar es del llamador, no del diálogo. Igual
`presentation/assets/AssetHistorySync.kt`, que sacó de `FixedAssetViewModel` los efectos
sobre el activo y el recálculo de contadores: ahora hay dos caminos que escriben historial.

### Compra rápida — un desembolso, muchos lugares, varias sucursales

Alguien sale una vez por semana y trae una brocha, trapos, una mesita para el teléfono,
salsa en sobre, fruta y papas — cada cosa de un lugar distinto y para sucursales distintas.
O al revés: llega el de las gaseosas a tocar la puerta de la panadería. El modelo de factura
tiene la forma equivocada en tres ejes: el proveedor está en el encabezado (pero la papa se
compra donde esté más barata ese día), la sucursal está en el encabezado (pero un desembolso
surte a varias), y casi todo lo demás —número de factura, crédito, vencimiento, abonos, IVA—
no aplica porque es efectivo y papel de cuaderno.

Mandarlo todo a Gastos tampoco sirve: la fruta y las papas se consumen produciendo, así que
ahí saldrían del inventario para siempre y quedarían fuera del costo del producto.

**Se llama por lo que se hace —anotar rápido— y no por quién se movió.** El nombre anterior
("vuelta de compras") solo describía salir a comprar; con el proveedor llegando a la puerta,
el caso más común quedaba sin lugar obvio y los gastos del día terminaban capturándose a mano
en Gastos, sin inventario y sin comprobante.

**En Firestore el valor guardado sigue siendo `"MARKET_RUN"`.** `PurchaseKind.QUICK` es el
valor nuevo y `PurchaseKind.isQuick(kind)` acepta los dos — renombrar el dato guardado
obligaría a migrar documentos para arreglar una etiqueta que el usuario nunca ve. Toda
comparación va por `Purchase.isQuick`, nunca por `kind ==`.

**Es el MISMO documento `Purchase`, con `kind = PurchaseKind.QUICK`.** No una colección
paralela: los cuatro `PurchaseLineKind`, el ingreso a inventario, la generación de gastos, la
foto y la corrección por diferencia al editar o borrar son exactamente los mismos. Un modelo
aparte habría duplicado `applyStockForPurchase`, `applyStockDelta`,
`syncPurchaseSideEffects` y la cascada de borrado — cuatro caminos que mueven inventario, que
es justo la duplicación que este módulo lleva evitando.

Lo único que cambia de forma: **sucursal y lugar bajan del encabezado a la línea**
(`PurchaseLine.branchId`/`branchName` como override, `placeName` como texto libre).
`effectiveBranchId(purchase)` resuelve la precedencia y `branchesTouched` lista las
sucursales de la compra.

**Tres cosas que hubo que corregir para que el multi-sucursal no mintiera en silencio:**
- `applyStockForPurchase` usa la sucursal de la LÍNEA, no la del encabezado.
- `applyStockDelta` agrupa por `StockKey(branchId, itemId)` y no solo por ítem: mover una papa
  del restaurante a la panadería se vería como "cero cambio" y ninguna de las dos se enteraría.
- `reconcileLotsPerBranch` llama a `reconcilePurchaseLots` **una vez por sucursal**. Ese método
  recibe UNA porque el libro de lotes está llaveado por `{branchId}_{ingredientId}`; llamarlo
  con la del encabezado dejaría la fruta de la otra sucursal sin lote y el aviso de caducidad
  apuntando a fruta que está en otro lado.
- `expensesForPurchase` pone el gasto en la sucursal de la línea: los trapos del restaurante no
  son gasto de la panadería, y el reporte financiero desglosa por sucursal.

**La precisión se decide fila por fila, sin configurar nada.** `QuickPurchaseDraftLine` se
anota con descripción + monto; pegarle un insumo o un producto de reventa es opcional y es lo
único que decide el `lineKind` (`toPurchaseLine`): materia prima → inventario, suministro →
inventario + gasto, reventa → inventario de tienda, nada → solo gasto. Obligar a elegir
catálogo por renglón haría que la compra no se registre nunca, que es peor que registrarla sin
inventario. La fila dice en pantalla si entra al inventario o es "solo gasto" — es la
diferencia que el usuario eligió y esconderla la vuelve invisible hasta que el conteo no cuadra.

**La reventa se ofrece desde `allResaleProducts`, no desde `supplierResaleProducts`.** Aquél
solo se llena en `selectSupplier` y una compra rápida no tiene proveedor: mientras se usó ése,
el picker recibía siempre una lista vacía y las tostadas compradas en el mercado caían como
gasto suelto sin entrar nunca al inventario de tienda.

**La categoría de gasto es POR FILA** (`QuickPurchaseDraftLine.expenseCategory`), no una sola
para todo el desembolso: una salida trae gasolina (TRANSPORTE) y trapos (OTROS), y con una
categoría única el reporte contesta "cuánto" pero nunca "en qué". Solo se pide donde aplica —
la materia prima y la reventa son inventario y no generan gasto.

**El monto tecleado es el TOTAL de la fila**, no el precio unitario ("las papas me costaron
Q25", no "Q5 la libra"). Se guarda dividido en `unitCost` para que `lineTotal` —la fórmula
única del importe— devuelva exactamente lo tecleado. La **cantidad muestra la unidad base del
insumo** cuando hay catálogo detrás: sin eso entraba 1 lb de sandía donde se compraron 20.

Nace **saldada** (`PurchaseStatus.PAID`, sin `dueDate`): es efectivo del momento, no genera
cuenta por pagar. `saveQuickPurchase` reutiliza `applyStockForPurchase` y
`syncPurchaseSideEffects` sin camino de escritura propio.

**Corregir una compra rápida tiene su PROPIA ruta** (`purchases/quick/{purchaseId}`), no
`PurchaseEdit`: aquel formulario es el de la factura, no sabe de lugar ni de sucursal por
línea, y reabrir ahí una compra rápida pedía proveedor y escondía justo sus datos.
`saveQuickPurchase` sirve al alta y a la corrección; el inventario se corrige por diferencia
con el mismo `applyStockDelta` de la factura, y `paidAmount` sigue al total (si quedara el
viejo, corregir el monto dejaría un saldo que nadie debe). **El `purchaseId` lo manda la
pantalla**, no se deduce de `editingPurchase`: el VM es singleton y abrir una corrección y
salirse sin guardar habría hecho que la siguiente compra nueva sobreescribiera aquélla.

**En la lista** hay pestañas "Facturas" / "Compras rápidas" (`Purchase.isQuick`), **siempre
las dos**. Se separan porque son dos preguntas distintas —"¿a quién le debo?" vs "¿en qué se
fue la semana?"— y mezcladas los KPIs de cuentas por pagar dejan de significar algo.

La de compras rápidas es de **consulta y corrección, no de alta**: el FAB sale solo en
"Facturas". Una compra rápida se anota donde salió el dinero —el acceso de Operaciones, o la
caja abierta del POS—, porque eso es lo que decide de qué gaveta se descuenta; un alta desde
Compras no tiene forma de saberlo y nacía siempre como caja general, que es exactamente el
faltante de caja que nadie podía explicar. La pestaña **no se quitó** porque es el único lugar
donde se ven completas: las líneas que entran a inventario no generan `Expense`, así que no
aparecen en Gastos, y un monto mal tecleado en una compra de tostadas no tendría dónde
corregirse. Lleva una línea que dice dónde se registran, para que la ausencia del FAB no se
lea como un bug. Una compra rápida se identifica por lo que trae (`Purchase.listTitle`, que
sale de `itemNames` ya denormalizado) y no como "Sin proveedor", que las hacía ver todas
iguales. Lógica pura con tests en `QuickPurchaseLineTest`.

### Costos Fijos — módulo del dueño, no sección de Gastos

`FixedBudgetItem` es el compromiso mensual (renta, luz, internet, teléfono): lo revisa el
dueño, no quien anota el gasto del día. Vivía como una sección arriba de la lista de Gastos y
**se sumaba al mismo total**, así que el mes se veía gastado desde el día 1 mezclando un plan
con desembolsos reales.

Ahora es módulo propio en Administración, gateado por `MANAGE_FIXED_BUDGET`, y la lista de
Gastos muestra un solo total: lo que de verdad se gastó en el período. Por lo mismo
`Screen.ExpenseList` ya no pide `MANAGE_FIXED_BUDGET` — quien solo administra los fijos no
necesita entrar ahí.

**Sigue siendo un catálogo que no genera nada**: nunca se convierte en `Expense` ni llega a
ningún reporte, así que no hay con qué comparar lo planeado contra lo ejecutado. Pendiente.

### Proveedor ocasional y línea suelta

`Purchase.supplierId` **puede ir vacío con `supplierName` lleno**: la tienda de la esquina, el
puesto del mercado. No se da de alta como `Supplier` a propósito — al que le comprás una vez
no es un proveedor, y el catálogo termina lleno de nombres que nadie vuelve a tocar.

Sin proveedor del catálogo no hay presentaciones que recorrer, así que el paso 3 cambia de
forma: en vez del catálogo aparece `LooseIngredientLineDialog` (insumo + cantidad + total) y
`LooseLinesList` para ver lo capturado. **No se registra una `PurchaseFormat`**: el formato es
el precio de referencia del insumo, y llenarlo con proveedores de una sola compra ensuciaría
el costeo. El `lineKind` lo decide el `usage` del insumo, no quien captura.

El borrador del nombre ocasional vive en la pantalla hasta que se confirma con "Usar": en el
ViewModel, cada tecla reiniciaría el catálogo del formulario.

### Compras — mecánica de inventario y cobranza

3 pasos: sucursal destino → proveedor → cantidades. Por línea: actualiza `BranchStock` + registra `StockMovement` (Kardex), o `FinishedProductStock` si es reventa. Permisos: `RECEIVE_STOCK` (registrar y recibir), `MANAGE_PURCHASE_PAYMENTS` (abonar).

**El encabezado guarda lo que la lista necesita.** `Purchase.totalAmount`, `itemIds` e `itemNames` se denormalizan desde las líneas al guardar. Sin ellos la lista tendría que abrir la subcolección `lines/` de cada factura para pintar un monto o para encontrar la que lleva "Aceite" adentro — una lectura por fila. `PurchaseLine.lineTotal` es la fórmula única del importe (prefiere `packageCost × paquetes`, que es lo que dice la factura; cae a `unitCost × cantidad` cuando no hay formato); antes el detalle descartaba el total entero si UNA línea no traía costo por presentación. El script `functions/tools/backfill-purchases.js` rellena estos campos en las compras viejas (idempotente, no pisa lo puesto a mano).

**"Por recibir" ya no ingresa stock.** `savePurchase` aplica inventario y Kardex solo si el estado no es `PENDING_RECEIPT`; el ingreso queda en `receivePurchase`, que marca `receivedAt` **antes** de mover stock para que un reintento tras un fallo a media aplicación no vuelva a sumar lo que sí entró. `applyStockForPurchase` es el único lugar que toca inventario, y lo llaman los dos caminos. El precio del formato (`updatePurchaseFormatCost`) sí se actualiza al registrar, no al recibir: es el precio que el proveedor cotizó.

**Estado guardado vs estado mostrado.** Firestore guarda tres valores (`PurchaseStatus`); "Vencida" y "Abonada" son proyecciones de `Purchase.displayStatus(now)` contra la fecha de hoy y el saldo. Guardarlas exigiría un job que reescriba documentos cada medianoche — mismo criterio que `effectiveOrderDisplay` en producción. `PENDING_RECEIPT` manda sobre todo: pagar por adelantado algo que no ha llegado no lo mete a la bodega.

**Editar una compra** (`PurchaseEdit`, `purchases/form/{purchaseId}` — el mismo formulario con `purchaseId != null`). El inventario se corrige **por diferencia**, no reescribiendo: `applyStockDelta` compara línea por línea contra `editingOriginalLines`, mueve solo el delta y deja un `StockMovement` tipo `AJUSTE` que documenta la corrección. Reaplicar la compra entera duplicaría el ingreso. Una compra en `PENDING_RECEIPT` no tocó inventario, así que editarla tampoco lo toca; cambiar de pendiente a recibida ingresa completa, y al revés revierte todo.

Tres trampas que resuelve `loadPurchaseForEdit` y conviene no deshacer:
- **No llama `loadFormData`** — pisaría la sucursal y el proveedor de la compra con los defaults del alta.
- **Une al catálogo del proveedor los insumos que la compra ya trae**: si a un insumo le quitaron la presentación de ese proveedor después de facturar, dejaría de tener fila y su línea quedaría sumando en el total sin poder tocarla.
- **`formKey`** (= `editingPurchase?.id`) va en las claves de `rememberSaveable` de cada fila. Sin él los campos se inicializan vacíos en la primera composición y no se reinicializan cuando las líneas terminan de cargar: editar mostraría todas las cantidades en blanco. Por lo mismo el precio de la fila se siembra desde `existingLine.packageCost` y no desde el formato del catálogo — una compra vieja pudo registrarse a otro precio, y reabrirla se lo cambiaría en silencio.

**Borrar una compra** (`deletePurchase`, permiso `DELETE_PURCHASES`) **revierte lo que hizo**: reutiliza `applyStockDelta` con la lista nueva vacía —que es exactamente "quitá todo lo que esta compra metió"—, deja sus lotes en saldo cero vía `reconcilePurchaseLots(emptyList())` y borra líneas, abonos y encabezado. Reusar el camino del ajuste por diferencia en vez de escribir un reverso aparte significa que el borrado hereda el mismo Kardex y las mismas guardas ya probadas. Los lotes se agotan en lugar de borrarse: eliminarlos dejaría huérfano el consumo que ya se hizo contra ellos. El diálogo dice explícitamente si va a mover inventario y si hay abonos que se perderán.

**Permisos propios**: `EDIT_PURCHASES` y `DELETE_PURCHASES`, separados de `RECEIVE_STOCK`. Registrar una compra es trabajo diario de bodega; corregir una ya contabilizada o borrarla mueve inventario y dinero hacia atrás, y no es lo mismo.

**Foto de la factura física** (`Purchase.invoicePhotoUrl`, opcional): cuando el total no cuadra o el proveedor reclama, lo que resuelve la discusión es el papel. Sube por `PurchaseInvoiceImageUploader` a `purchaseInvoices/{companyId}/{purchaseId}.jpg` —ruta determinista, así reeditar sobreescribe en vez de dejar huérfanas— con el patrón de siempre: interfaz en `domain/`, NoOp en appModule, override `Android`/`Js`. Si la subida falla, la compra se guarda igual.

En Android se puede **tomar la foto en el momento**: `ImagePickerButton` acepta `allowCamera` (apagado por defecto, para no cambiarle el comportamiento a recetas/productos/activos/pedidos especiales). Usa `TakePicture` + `FileProvider` (`${applicationId}.fileprovider`, `res/xml/file_paths.xml` limitado a `cache-path`) porque desde Android 7 pasar una Uri `file://` a otra app lanza `FileUriExposedException`. **No se declara el permiso `CAMERA` a propósito**: con `ACTION_IMAGE_CAPTURE` vía FileProvider no hace falta, y declararlo obligaría a pedirlo en tiempo de ejecución. El archivo destino se crea ANTES de abrir la cámara y su Uri se recuerda: `TakePicture` devuelve un booleano, no la imagen. En web solo hay galería; en iOS el selector de imágenes sigue sin implementarse (stub, igual que en el resto de la app).

Los lotes de una compra editada los reconcilia `IngredientRepository.reconcilePurchaseLots`, que **ajusta el lote existente por diferencia en vez de borrarlo y recrearlo** (un lote puede estar parcialmente consumido; recrearlo lo devolvería a su cantidad original). Por eso el delta de stock de la edición va con `skipLots = true`: si además se dispararan las reglas automáticas de `updateBranchStock`, la corrección se contaría dos veces.

**Cuentas por pagar.** `Supplier.paymentTermsDays` (0 = contado) es el default; cada compra guarda su snapshot en `paymentTermsDays` y calcula `dueDate = date + días`, porque un proveedor que cambia de condiciones no debe mover el vencimiento de las facturas viejas. Los abonos viven en `purchases/{id}/payments/{id}` con `paidAmount` denormalizado en el encabezado: la subcolección es la fuente de verdad y el campo la caché, así que `addPayment`/`deletePayment` **recalculan sumando de nuevo** en vez de sumar/restar sobre el valor guardado — un doble toque o un borrado no dejan el saldo corrido para siempre. Se guardan los pagos y no solo el saldo porque "¿ya le pagué a Molinos?" se contesta con "sí, el cheque 1042", no con un número.

**Filtros de la lista** (`model/PurchaseFilter.kt`, lógica pura con tests en `PurchaseFilterTest`): `PurchasePeriod` (default **Este mes**, con `ALL` para la pregunta que sí necesita el histórico), estados múltiples, sucursal, proveedor y orden. Todo en memoria sobre la colección completa — `getPurchases` **no** filtra por rango en el servidor a propósito: las compras viejas pueden tener `date` como `Timestamp` y las nuevas lo escriben como número, y Firestore ordena por tipo antes que por valor, así que un `where date >= x` numérico dejaría fuera en silencio justo las facturas más antiguas. Cuando el volumen lo pida, primero se normaliza el campo con un script.

La búsqueda alcanza proveedor, sucursal, número de factura, observaciones **e insumos de las líneas**. Si hay resultados fuera del período mostrado sale un aviso con "Ver todo" — sin él, buscar "Aceite" en un mes donde no se compró parecería decir que nunca se compró.

Fila de KPIs (Facturas · Comprado · Por pagar · Vencido · Por recibir) calculada sobre lo ya filtrado: si filtro un proveedor, los KPIs son de ese proveedor. Tocar "Vencido" o "Por recibir" aplica ese filtro.

**Las facturas con foto llevan un ícono junto al estado** que abre el visor sin entrar al detalle: al cotejar una tanda de facturas el papel se consulta muchas más veces de las que se abre la compra completa. El visor es `ui/ZoomableImageDialog` — pantalla completa con pellizco y doble toque, implementado con `detectTransformGestures` + `graphicsLayer` en vez de sumar una librería. El pan se bloquea en 1x para que la imagen no se escape fuera de la pantalla sin forma de recuperarla.

**El proveedor va con más peso que el resto de la fila** (bodyMedium/SemiBold contra bodySmall): es el dato por el que uno busca una factura, e iba igual que la sucursal y la fecha, sin dónde apoyar la vista al recorrer la lista.

**El estado se lee por el color de la fila**, no solo por el chip del final: barra de acento de 3dp a la izquierda más un fondo del mismo tono al 35% de un color ya de por sí pálido. El alpha es bajo a propósito — un fondo saturado convierte la tabla en un resaltador y vuelve ilegible la columna de montos. "Pendiente de pago" se queda sin tono: es el estado normal y teñir la mayoría de las filas anularía el aviso de las demás.

**Saldar vs. abonar.** El detalle ofrece los dos: "Saldar Q X" (botón lleno — el caso normal) y "Abonar" (outline — la excepción). Saldar **registra un abono por el saldo entero**, no cambia el estado a mano: `paidAmount` es una caché de la suma de los abonos y `recalculateBalance` la recomputa desde la subcolección, así que un estado PAGADA sin abono detrás volvería a "pendiente" en cuanto algo tocara la factura. El tope del abono se valida dos veces — el botón del diálogo se deshabilita, y `FirebasePurchaseRepository.addPayment` revalida contra el saldo **recién leído**, porque entre abrir el detalle y confirmar alguien pudo haber pagado desde otro dispositivo.

**Alerta en HomeScreen**: `AlertTarget.OVERDUE_PURCHASE` — facturas vencidas (CRITICAL con 3 o más) y, si no hay ninguna vencida, las que vencen dentro de 3 días como INFO. La lista además lleva un banner rojo propio cuando hay deuda vencida **fuera del período mostrado**: el default es "Este mes" y una factura de mayo sin pagar es justo la que hay que cobrar en septiembre, así que ni el banner ni el KPI "Vencido" pueden quedar acotados al mes (`showOverdue()` abre el histórico completo).

#### Formulario de compra

**El total va fijo abajo, no en el paso de revisión** (`PurchaseTotalsBar`). Antes el total solo existía en `PurchaseReviewContent`, que solo se muestra en teléfono — en tablet no había ningún total en toda la captura. Junto al total va **"Total según la factura"**: se teclea el número del papel y la barra dice "Cuadra" o cuánto sobra/falta. Es lo único que atrapa un cero de más ANTES de que la cantidad entre al inventario y al Kardex, donde corregirla ya cuesta; se guarda en `declaredTotal` para dejar constancia de que alguien lo cotejó.

**IVA: los precios se capturan CON impuesto incluido.** `taxBreakdownFromGross` despeja hacia atrás (Q112 = Q100 + Q12); sumar el 12% encima daría Q125.44 y la factura dejaría de cuadrar con el papel. El chip "IVA incluido" solo decide si se desglosa, nunca altera el total. `taxAmount`/`taxRatePercent` son snapshots por compra.

**La fecha es editable** (antes `Clock.System.now()` fijo): las facturas se capturan en tandas y con la fecha de hoy el vencimiento de una factura de la semana pasada nacía corrido. Los días de crédito se preponen del proveedor y se pueden pisar para esa factura.

**El paso 1 se colapsa** a una línea cuando el usuario tiene una sola sucursal (el ViewModel ya la elegía; lo que sobraba era el encabezado de paso).

**El catálogo va alfabético y con instrucciones a la vista.** Quien captura lee la factura de arriba abajo y busca cada renglón por nombre; el orden de llegada de Firestore es arbitrario. Y como la pantalla muestra el catálogo del proveedor y no la factura, arriba de la lista se explica que hay que ir tocando "Agregar" en cada producto que sí venga. El botón de cada fila es **lleno, azul de marca y con texto blanco**: en outline se leía como un borde más de la tabla y no se entendía que ahí se agrega el producto. El diálogo de cantidad **abre con el foco puesto, el texto seleccionado y el teclado numérico arriba**, y `Done` confirma — es un toque menos por cada producto de la factura.

**El catálogo del proveedor sigue siendo el formulario** — no se cambió por el picker de líneas de Odoo: con un proveedor de 5 insumos, teclear sobre la lista es más rápido que abrir un picker cinco veces. Lo que faltaba era lo otro que Odoo sí da: saber qué llevo. Eso lo resuelven el chip **"Solo lo que llevo (N)"** y el total fijo.

**`addSupplierFormat`** permite meter a la factura un insumo que el proveedor no tenía asociado sin salir de la captura (antes había que ir a crear la presentación y volver a empezar). La presentación **siempre** se guarda, sin preguntar: si se lo compraste a ese proveedor, eso es lo que el modelo llama una presentación suya. Usa `IngredientRepository.addPurchaseFormat` (escritura parcial) y no `updateIngredient`, que hace `set()` del insumo y reescribe toda la subcolección de formatos.

**Caducidad**: `Ingredient.isPerishable` decide quién la pide; `PurchaseLine.expiryDate` + `lotCode` la guardan. La captura vive en una sección **"Vencimientos"** aparte que aparece cuando ya hay algo perecedero anotado — un selector de fecha en cada uno de los 40 renglones del catálogo convertiría la pantalla más sensible a la velocidad de captura en un formulario largo. El encabezado denormaliza `nearestExpiryDate`/`nearestExpiryItemName` para mostrarlo en la compra; **el aviso, en cambio, sale de los lotes** (ver abajo).

**Selector de proveedor**: buscador a partir de seis proveedores y rejilla de fichas de 220dp en pantalla ancha. Una pila de tarjetas a todo el ancho obligaba a hacer scroll para elegir entre 25 y gastaba un renglón por proveedor en tablet.

### Lotes de insumos perecederos (FEFO)

`companies/{c}/stockLots/{id}` — colección plana con `branchIngredientKey` (`{branchId}_{ingredientId}`) para traer los lotes de un insumo con **una** consulta de igualdad, sin índice compuesto. Modelo y lógica pura en `model/StockLot.kt`, con tests en `StockLotFefoTest`.

**`BranchStock` sigue siendo la autoridad de "cuánto tengo".** Los lotes son una capa paralela, y esa separación es lo que hace seguro el cambio: **la matemática de consumo de producción no se tocó**. Si el libro de lotes se despega de la bodega, la producción sigue descontando igual que siempre — se pierde precisión en el aviso de caducidad, no el inventario. `allocateFefo` devuelve un `shortfall` en vez de fallar cuando los lotes no cubren el consumo (pasa si un insumo empezó a llevar lotes teniendo existencias previas).

**Solo los perecederos llevan lotes.** La harina, el azúcar y la sal rotan en días; lo que se queda parado y se vence es la levadura, el mejorador, el ponche de huevo y los rellenos comprados. El gate vive en un solo lugar: la compra manda `NewStockLot` únicamente para líneas de insumos con `isPerishable` (o que traigan fecha). Al resto nunca se le crea un lote, así que el consumo FEFO es un no-op para ellos.

**El manejo de lotes vive dentro de `IngredientRepository.updateBranchStock`, no en los ViewModels.** Hay cuatro caminos que bajan stock —producción (`completeLot`), lotes de preparación, ajuste manual y compras— y al que se le olvidara descontar el lote dejaría el aviso apuntando a un saco que ya no existe. Ese método es el único cuello de botella por el que pasan los cuatro: `delta < 0` descuenta FEFO, `newLot` presente registra el ingreso, y un `delta > 0` suelto sobre un insumo que ya lleva lotes entra como lote sin fecha para que un ajuste manual no deje el libro corto. El libro se toca **después** de escribir el stock: si falla, el inventario ya quedó bien.

**Orden de consumo** (`allocateFefo`): vence primero → sale primero; los lotes **sin fecha van al final**, ordenados por recepción. Si hay uno que vence la semana entrante y otro sin fecha, gastar el fechado es lo que evita la merma; si ninguno trae fecha la regla degrada sola a FIFO.

**FEFO automático, sin declarar lote.** El panadero no elige de qué saco agarró. Se eligió a sabiendas de que depende de una costumbre de bodega —lo nuevo atrás, lo viejo adelante— porque pedir el lote en cada amasada es un impuesto diario que en una panadería artesanal no devuelve nada; lo que se desvíe se corrige con el conteo periódico. `StockLot.unitCost` queda guardado aunque hoy nadie lo lea: es el dato que convierte esto en costeo real por lote cuando llegue el módulo de costo por producto, y capturarlo después es imposible.

**Aviso**: `AlertTarget.EXPIRING_INGREDIENT` sale de `expiringBefore(now + 15d)` sobre los lotes con saldo — un saco ya consumido tiene el lote agotado y deja de avisar solo. Antes colgaba del encabezado de la compra y seguía alertando por mercadería que ya no estaba. El detalle del insumo muestra los lotes en orden de consumo, en rojo a 7 días o menos.

### Proveedores
CRUD simple (nombre, contacto, teléfono, email, dirección, notas) **+ historial de compras**:
totales, qué se le compra ya sumado y las últimas facturas, cada una tocable. Ver "La ficha
del proveedor contesta..." en el módulo de Compras.
