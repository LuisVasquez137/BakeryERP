# BakeryERP — CLAUDE.md

Contexto del proyecto para sesiones futuras. Actualizar cuando se agreguen módulos o cambien patrones importantes.

---

## Descripción

SaaS ERP para panaderías artesanales (multi-empresa con `companyId`). Gestión de recetas, insumos, productos terminados, proveedores, producción, despacho, reparto e inventario de tienda. Usuario final: dueño o encargado de la panadería.

**Dev:** Fernando / luisvasquez1307@gmail.com — **Nombre comercial:** Bagueto

---

## Tech Stack

- **Kotlin Multiplatform (KMP)** + **Compose Multiplatform** (Android, iOS, JS/wasmJs)
- **Firebase (GitLive SDK):** Auth, Firestore, Storage
- **Koin 4.0.3** (DI), Compose Navigation, AndroidX ViewModel + StateFlow

**Targets activos:** Android (principal) + JS/browser. iOS configurado.

---

## Arquitectura: Clean Architecture

```
commonMain/    model/  domain/  presentation/  navigation/  ui/  di/
androidMain/   data/   di/      auth/
nonWasmMain/   data/   di/   (Firebase impls compartidas Android+iOS+JS)
```

**Reglas:**
- Composables solo conocen ViewModels.
- ViewModels solo conocen interfaces de `domain/`.
- Implementaciones Firebase viven en `data/` (nunca en commonMain).
- Modelos en `model/` son data classes puras.

---

## Patrones de Firestore (CRÍTICO)

### GitLive SDK no serializa data classes ni listas de objetos

Toda escritura debe usar `Map<String, Any?>` explícito.

```kotlin
// ✅ docRef.set(mapOf("id" to id, "name" to name, ...))
// ❌ docRef.set(myDataClass)
// ❌ mapOf("formats" to listOf(PurchaseFormat(...)))   // listas de objetos fallan
```

### Colecciones Firestore

```
companies/{c}/
  products/{id}                            ← doc plano
  ingredients/{id}                         ← doc plano (sin currentStock); usage=PRODUCTION|SUPPLY
    purchaseFormats/{id}                   ← subcollección
    movements/{id}                         ← Kardex por insumo
  branchStocks/{branchId}_{ingredientId}   ← colección PLANA (no subcoll.) para query única
  stockLots/{id}                           ← lotes FEFO, solo insumos perecederos
  plantIssues/{id}                         ← vales de salida de material de planta (por empaque)
  purchases/{id}/lines/{id}                ← lineKind: INGREDIENT|RESALE_PRODUCT|SUPPLY|SERVICE
                                           header: inventoryAmount/expensedAmount = corte para el reporte financiero
                                           kind del header: INVOICE|QUICK (compra rápida: sucursal y lugar por
                                           línea; el valor viejo "MARKET_RUN" se sigue leyendo)
    payments/{id}                          ← abonos; paidAmount denormalizado en el header
  suppliers/{id}
  recipes/{id}                             ← doc plano (stageCount/yieldCount denormalizados)
    components/{id}                        ← subcoll. nivel receta (campo stageId)
    steps/{id}                             ← subcoll. nivel receta (foto opcional)
    yields/{id}
    stages/{id}                            ← LEGACY, solo lectura
  preparations/{id}/ingredients/{id}
  preparationBatches/{id}                  ← lotes con remainingWeightGrams
  priceLists/{id}                          ← RETAIL|WHOLESALE|CUSTOM, isDefault, priceFactor
  productPrices/{productId}_{listId}       ← override plano por producto+lista
  dispatchNotes/{id}/lines/{id}            ← auto-creadas al completeLot
  deliveryRuns/{id}                        ← dispatchNoteIds array
  finishedStocks/{branchId}_{productId}    ← stock terminado por sucursal (avgUnitCost)
  finishedMovements/{id}                   ← Kardex terminado
  assets/{id}/history/{id}                 ← Activos fijos + historial
```

**Por qué `branchStocks` es plana**: 1 query filtrando en memoria por `branchId`, vs N queries por ingrediente.
**Por qué `components` está bajo recipe, no bajo stage**: Firestore no recomienda >2 niveles de subcolección. Campo `stageId` resuelve la pertenencia.

### Deserialización de Timestamps

Fechas pueden llegar como `Timestamp` (docs viejos) o `Long` (nuevos). Siempre:

```kotlin
private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
    runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
        ?: runCatching { get<Long?>(field) }.getOrNull() ?: 0L
```

---

## Estructura de Archivos (alto nivel)

```
commonMain/com/bakery/erp/
  model/         — Recipe, RecipeStage/Step/Component/Yield, Product, Ingredient,
                   BranchStock, StockMovement, Purchase[Line], Supplier, PriceList,
                   ProductPrice, DispatchNote[Line], DeliveryRun, FinishedProductStock,
                   FinishedStockMovement, Preparation[Ingredient], PreparationBatch,
                   ProductionOrder/Lot[Line], Permission, User, Organization, Branch,
                   FixedAsset, AssetHistoryEntry, Employee, ...
  domain/        — *Repository interfaces, RecipeImageUploader, AssetImageUploader
  presentation/  — auth, home, recipes, products, ingredients, purchases, suppliers,
                   pricing, dispatch, delivery, finishedStock, production (orders/lots/shifts),
                   preparations, branches, expenses, users, employees, assets
  navigation/    — AppNavigation.kt (NavHost + sealed Screen)
  ui/            — AdaptiveContainer, ImagePickerButton, ProductImage, KeepScreenOn (expect)

androidMain/  data/ (Firebase impls que requieren Context/Storage), di/, auth/
nonWasmMain/  data/ (impls compartidas), di/AppModule.kt
```

---

## Módulos Implementados

### Auth
Email/password + Google. Multi-empresa: al registrarse crea Organization; `JoinOrgScreen` para unirse por código. Roles: OWNER, ADMIN, MANAGER, EMPLOYEE.

### Agrupado por masa (transversal a Producción)

`presentation/production/MasaGrouping.kt` — una receta genera varios productos, y verlos juntos importa en cada paso: al pedir (no buscarlos desperdigados), al consolidar (cuánta masa se moja de cada tipo), al hornear (la amasada es una sola).

- `groupByMasa(items, recipeIdOf, recipeNameOf, nameOf, groupOrderIndex, itemOrderIndex)` — genérico; lo usan el form de pedido (pasos 2 y 3), el tab Consolidado, la lista consolidada del lote y la orden de producción.
- Lo que no tiene receta cae en "Otros productos" **al final**.
- `recipeOrderIndexFrom(productSortOrder, recipeIdOfProduct)` traduce el orden guardado **por producto** a orden **por masa**: la posición de una masa es la del primero de sus productos. Subir "Barcas" sube toda la hojaldre. Se eligió esto en vez de un segundo campo `recipeSortOrder` para no duplicar configuración ni migrar la existente.

### Pedidos especiales — pan fuera de catálogo, misma masa

Un cliente pide "un pirujo del doble de tamaño". Es la masa que ya se hace, en otra figura. Se anota en el propio pedido de la sucursal (`ProductionOrderFormScreen`, paso 2, sección arriba de la lista de productos), sin dar de alta un `Product`.

**No se crea producto a propósito**: es un encargo de un día, y darlo de alta lo metería en el POS, en las listas de precios y en `ProductWeightsScreen` para siempre.

- `ProductionOrderLine.isSpecial` + `specialRecipeId/Name`, `specialRawWeightGrams`, `specialPhotoUrl`, `specialUnitPrice`. `productName` lleva la descripción, y el `productId` es sintético (`newSpecialProductId()` → `esp_<base36>`): todo el flujo de producción agrupa por `productId`, así que dos especiales distintos nunca se funden en una línea de lote.
- **Masa + peso en crudo son los dos campos que lo hacen funcionar**, no burocracia: la masa lo mete en el grupo de su receta (entra a la amasada del día en vez de quedar huérfano) y el peso es lo único que permite escalar la receta y descontar insumos en `completeLot`. Sin ellos el pan se hornea igual pero sale gratis del inventario. El diálogo los exige.
- **El peso se teclea en ONZAS** (`SpecialOrderDialog`), que es la unidad en la que se pide y se piensa el pan acá; se guarda en gramos en `specialRawWeightGrams` con equivalencia en vivo abajo del campo. Guardarlo en onzas obligaría a convertir en la receta escalada y en el descuento de insumos, que trabajan en gramos.
- **El especial viaja al despacho con su masa y su peso**: `DispatchNoteLine` lleva `specialRawWeightGrams` + `specialRecipeId/Name` como snapshot. No es redundante con el pedido — el agrupado por masa de las 4 pantallas de despacho resuelve la receta contra `recipeRefByProductId`, que se arma del catálogo, y el `productId` sintético de un especial no está ahí: sin el snapshot **todo especial caía en "Otros productos"**, separado de la masa con la que se horneó. `dispatchLineRecipeId/Name` (en `presentation/dispatch/DispatchLineLabels.kt`) resuelven la precedencia — el especial manda sobre el catálogo — y `dispatchLineLabel` arma el "★ Nombre (6 oz)" que ve quien empaca y quien cuenta en la sucursal. Notas creadas antes de esto siguen funcionando: sin los campos, cae al comportamiento viejo.
- `ProductionLotLine.isSpecial` + `specialPhotoUrl`. `recipeId`/`rawWeightGrams` vienen **copiados de la línea del pedido**, no de `productsById` — de ahí en adelante la línea es una más (receta escalada, mínimo de amasada, Kardex).
- `ProductionViewModel.specialLotLine(source, quantity, allocations)` es el **único** lugar que arma una línea de lote especial. Son cuatro los caminos que crean líneas (`consolidateOrderLines`, `recomputeLotLinesFromSources`, `applySplitLotLine` del reparto por turnos, `addLateOrderToCurrentLot`) y al que se le olvide el peso deja al panadero con una masa sin escalar.
- **Fuera del patrón de sugerencias** (`recordOrderInPattern` / `rebuildOrderPatternFromHistory` filtran `!isSpecial`): su `productId` no se repite nunca y llenaría la sugerencia de productos fantasma. Por lo mismo `applySuggestedQuantities` ignora los especiales al decidir si el borrador "ya tiene cantidades", y `clearSuggestedQuantities` los conserva.
- **Precio**: `specialUnitPrice` es opcional. La línea de despacho sale con `PriceSourceTag.SPECIAL_ORDER`, que a diferencia de `NONE` **no cuenta para `hasPriceMissing` ni bloquea `markReady`** — un encargo de 5 panes no puede dejar parado el reparto del día entero.
- **Foto** opcional vía `SpecialOrderImageUploader` (interfaz en `domain/`, `NoOp` en appModule, `Android`/`Js` override — mismo patrón que `RecipeImageUploader`). Ruta `specialOrders/{companyId}/{productId sintético}.jpg`, así reeditar sobreescribe en vez de dejar huérfanos. Se sube al confirmar el diálogo (antes de que exista el pedido); si falla, el especial se guarda sin foto.
- El badge de unidades por masa del paso 2 **suma los especiales de esa receta** aunque no tengan fila propia: es el mismo número que decide si la amasada llega al mínimo.
- Se marcan con ★ dorado en consolidado, form de lote, detalle de pedido y las 4 pantallas de despacho. En `ProductionLotDetailScreen` el chip va crema con borde dorado y, si trae foto, se toca para verla en grande (`SpecialPhotoDialog`) — la descripción sola no alcanza para decidir cómo bolear.

### Clima y sugerencias: diálogo de entrada, no banner fijo

El aviso de cantidades sugeridas y la tarjeta de lluvia ocupaban dos tarjetas fijas encima del formulario: lo que la encargada viene a hacer —escribir cantidades— quedaba abajo del pliegue todos los días, para leer algo que solo importa la primera vez.

Ahora es `OrderInsightsDialog`, que se abre una sola vez al llegar al paso 2 (cuando la sugerencia terminó de cargar y hay algo que decir) con "Aceptar" / "Empezar de cero". El ícono de nube del TopAppBar lo reabre. `insightsAcknowledged` va llaveado por `rainForecastKey` (`sucursal|fecha`): cambiar la fecha y volver lo muestra de nuevo, porque el pronóstico es otro.

**Lo que NO se movió al diálogo**: la recomendación por lluvia de cada producto ("Te recomendamos aumentar 3 (+15%)") sigue en su fila — es accionable justo donde está la caja de cantidad, y sacarla obligaría a recordar un número por producto.

### Imprimir la orden de producción — papel para el panadero

La tablet queda lejos y con harina; el papel es el formato de consulta real mientras se
hornea. Botón de impresora en el TopAppBar de `ProductionLotDetailScreen`
(`ProductionOrderPrintButton`), sin permiso extra: es la misma pantalla que ya se abrió.

- **`DocumentPrinter` (domain) generaliza `ReceiptPrinter`**, que quedó atado a
  `ReceiptData`. Recibe un `PrintableDocument` con las **dos** representaciones —HTML y
  ESC/POS ya renderizado por ancho de rollo (58/80)— y cada plataforma elige cuál usar.
  Se renderizan ambos anchos por adelantado en vez de pasar un lambda para que el
  documento sea un valor puro, construible y testeable fuera de la impresora.
- **Android**: `AndroidDocumentPrinter` manda ESC/POS por socket RFCOMM (SPP) si hay
  térmica configurada; si falla o no hay ninguna, cae al Print Framework (PDF, WiFi,
  RawBT). `AndroidReceiptPrinter` y `JsReceiptPrinter` ahora **delegan** en él — el
  transporte estaba duplicado. **Web**: `JsDocumentPrinter`, iframe oculto +
  `window.print()`, hoja normal. **iOS**: `IosDocumentPrinter` vía AirPrint —
  iOS no expone SPP sin accesorio MFi, así que en iPhone no hay térmica Bluetooth.
- **El permiso de "dispositivos cercanos"** (`BLUETOOTH_CONNECT`) lo pide
  `BluetoothPrinterPickerDialog`, reutilizado del POS: al fallar el listado por permiso
  lanza el diálogo del sistema solo. El diálogo de elección (térmica / hoja o PDF)
  aparece **una sola vez** en Android; con impresora ya guardada, imprimir es un toque.
  Cambiar de impresora se ofrece en el snackbar posterior — es el momento en que uno
  descubre que salió por la equivocada, no antes.
- **El papel dice exactamente lo mismo que la pantalla**: `buildProductionOrderPrintData`
  vive en la screen y combina los helpers del ViewModel igual que `MasaProductionCard`
  (mismo agrupado por masa, mismo total, mismas libras). Un segundo cálculo paralelo
  dejaría el papel mintiendo en cuanto se arregle algo en pantalla.
- **La térmica se alimenta a ritmo, no de un tirón.** Un solo `write()` con toda la
  orden la imprimía **cortada a la mitad**: el rollo tiene un búfer de 1–4 KB y no
  negocia control de flujo por SPP, así que descarta lo que llega con el búfer lleno —
  y el `write()` de Android igual reporta éxito porque los bytes sí salieron del
  teléfono. `chunkAtLineBreaks` (en `util/EscPos.kt`, lógica pura y con tests) parte el
  flujo en envíos de ~256 B que **cierran en `0x0A`**: ningún comando ESC/POS que
  emitimos lleva `0x0A` de argumento, así que cortar ahí nunca parte una secuencia de
  control. `AndroidDocumentPrinter` los manda con 40 ms de pausa entre uno y otro.
  Con un ticket de venta esto nunca se notó — cabe entero en el búfer.
- **No cerrar el socket apenas termina el `flush()`.** `close()` retorna de inmediato y
  descarta la cola de transmisión pendiente: se perdía el último tramo, corte de papel
  incluido. Hay 1.5 s de espera antes de colgar, y el `outputStream` no se envuelve en
  `use { }` por lo mismo.
- **Tamaños en térmica** (`buildProductionOrderEscPos`): nombre de masa a doble ancho y
  alto, producto + cantidad a doble altura, el resto normal. Cada línea se imprime a un
  solo tamaño porque la impresora rellena por caracteres, no por ancho: mezclarlos dentro
  de una línea descuadra el margen derecho. `wrapToWidth` corta entre palabras — la
  térmica ya corta sola, pero al ras de la columna ("...sin ajonjol / i").
  `ProductionOrderPrintTest` verifica anchos, no contenido: ése es el modo de fallo real.
- En hoja (`buildProductionOrderHtml`) la cantidad va en 34pt y cada masa lleva casilla
  de "Hecho" — en papel el avance se marca con lápiz.

### Peso en crudo — dato del que cuelga todo

`Product.rawWeightGrams` (gramos de una pieza **antes de hornear**) es requisito de la receta escalada, del mínimo de amasada y del descuento de insumos. Sin él esas funciones no pueden calcular nada.

`ProductWeightsScreen` (`products/weights`, permiso `EDIT_PRODUCTS`) es la carga masiva: agrupada por masa, filtro "solo los que faltan", entrada en gramos con equivalencia en oz en vivo, IME Next entre campos y guardado en lote vía `ProductRepository.updateRawWeights` (escritura parcial — `updateProduct` reescribiría todo el documento). Se llega desde el icono de balanza en Productos, con badge del número de faltantes.

En la orden de producción los productos sin peso salen **en rojo con "sin peso"** dentro de su masa y no entran al cálculo, en vez de fallar en silencio.

### Productos
Campos: `name`, `description`, `category` (Panadería/Repostería/Pastelería), `imageUrl`, `salePrice`, `unit`, `weightPerUnit` (oz), `rawWeightGrams` (peso en crudo para escalar masa), `shelfLife`, `storageType`, `characteristics` (organolépticas, **desnormalizadas como campos planos**), `decorations: List<ProductDecoration>`.

### Adornos y rellenos — dos fuentes posibles

De una misma masa (sheca, gusanito) salen productos con distinto relleno (frijol, queso, ninguno). El relleno **es del producto, no de la receta** — misma decisión que la concha del gusanito. `ProductDecoration` admite exactamente una de dos fuentes (`isIngredientSourced` distingue cuál):

- **`preparationId`** — algo que preparamos nosotros (concha, relleno de esponja). Se consume de un `PreparationBatch` elegido FIFO: hay que producir el lote de relleno *antes* de poder producir el pan que lo lleva. Sin batch disponible al completar el lote, `completeLot` ahora avisa (`"... sin lote disponible — no se descontó"`) en vez de fallar en silencio.
- **`ingredientId`** — algo que se compra ya hecho (relleno de higo/queso crema en manga, mermeladas). No pasa por Preparación/Batch: se compra en el módulo de Compras como cualquier insumo (`BranchStock` + Kardex) y `completeLot` lo descuenta **junto con la receta**, sumándolo al mismo `aggregatedGramsByIngredient` — reutiliza el descuento+Kardex que ya existía, sin mecanismo nuevo. A diferencia del consumo de receta, este descuento NO depende de que la línea tenga receta/peso crudo resueltos (un producto sin receta puede llevar igual un relleno comprado).

`ProductFormScreen` → tab "Adornos": un solo picker con dos secciones (Preparaciones / Insumos), buscador compartido. `buildDecorationsSnapshot` en `ProductionViewModel` es el único lugar que arma el snapshot de línea (antes estaba triplicado en 3 sitios).

### Ingredientes (sin `currentStock`, con `usage`)
Stock vive solo en `BranchStock`. Form no tiene campo de stock — solo se genera al registrar compras. `PurchaseFormat` (presentación de compra) en subcolección.

`tracksStock` (solo suministros) decide si además se cuentan existencias — ver "El suministro
que no se cuenta" en Compras. `usage` separa materia prima de suministro de operación (ver "Compras — una factura, cuatro tipos de línea"). El form elige las categorías según el tipo (`INGREDIENT_CATEGORIES` vs `SUPPLY_CATEGORIES`) y limpia la categoría al cambiarlo: dejarla puesta guardaría "Harinas" en un cloro. La lista solo ofrece separar los dos catálogos **si la empresa realmente tiene suministros** — un filtro que siempre muestra lo mismo es ruido. `barcode` es opcional.

### Insumos que se cuentan — `unitWeightGrams`

El huevo se compra, se cuenta y se descuenta **por unidad**, pero la receta lo necesita **por peso**: el % del panadero es una razón entre pesos y "6" no es un peso. `Ingredient.unitWeightGrams` (peso de UNA unidad, en gramos) es el único puente entre ambos mundos. `0` = sin configurar.

- El campo aparece en el form de Insumos solo si se marcó una unidad de conteo (`countUnitsPer(unit) != null` → `unidad`, `docena`). `saco` no tiene equivalencia fija — eso lo resuelve el formato de compra.
- `toGramsOrNull(value, unit, unitWeightGrams)` y `gramsToUnitOrNull(...)` en `model/UnitConversion.kt` son las conversiones count-aware. **Devuelven null cuando no se puede convertir** en vez de pasar el número tal cual: `toBaseUnit` hacía `else -> value` y 6 huevos entraban a la receta como 6 gramos → 0.2%.
- Usarlas en todo cálculo receta ↔ stock. `toBaseUnit` sigue existiendo para conversiones ya conocidas de peso/volumen.
- Un insumo de conteo con peso unitario acepta también `gr/oz/lb` al escribirlo en una receta (`Ingredient.recipeUnitOptions`): "6 unidad" y "10.6 oz" de huevo dan el mismo %.
- La receta escalada muestra el peso en oz/lb (`util/formatBakerWeight`) y debajo la equivalencia "≈ 6 u" — el panadero cuenta el huevo, no lo pesa, pero el peso es el dato exacto.
- `PreparationIngredient.unitWeightGrams` es un **snapshot** del insumo al guardar, porque `Preparation.batchWeightGrams` es una propiedad derivada sin acceso al catálogo de insumos.

### Stock por Sucursal (`BranchStock`)
ID compuesto `{branchId}_{ingredientId}`. `IngredientListScreen` tiene chips de sucursal (auto-selección si hay solo una accesible). No se suma entre sucursales.

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

### Preparaciones + Lotes (`Preparation` / `PreparationBatch`)

- `usage: PreparationUsage` — `DOUGH` (esponja para masa) | `DECORATION` (concha, etc.).
- Ingredientes con `quantityPerBatch + unit`; peso del lote = Σ `toBaseUnit`.
- `flourFractionPct` denormalizado al guardar.
- **Esponja en producción = decisión del PANADERO, no de la encargada.** No se configura al crear lote. Vive en el panel de receta escalada del Detalle de Lote ("Lista de Producción"), por masa (`SpongeControlsForMasa` en `ProductionLotDetailScreen.kt`) — la amasada es una sola, se declara un total y se reparte entre las líneas por proporción de masa (`ProductionViewModel.distributeSpongeGrams`).
- Input en **libras** (`lb`); internamente gramos vía `toBaseUnit`.
- `ProductionViewModel.calculateLineConsumption` resta los gramos que la esponja aporta por `ingredientId` (fracción = `quantityPerBatch_i / batchWeightGrams`). Si esponja aporta más de lo que pide la receta, se descuenta solo lo presente.
- **Se descuenta de inmediato al confirmar, no al completar el lote.** Flujo con 3 diálogos: (1) elegir cuál lote de esponja — `SpongeBatchPickerDialog`; (2) toda la masa o cantidad parcial en libras — `SpongeAmountDialog` (el campo de libras es estado local del diálogo, sin sincronizarse con ningún valor derivado del propio tecleo — antes eso causaba que el punto decimal "saltara" mientras se escribía); (3) confirmación final — `SpongeConfirmDialog`. Al confirmar, `ProductionViewModel.confirmSpongeForGroup` llama `consumeFromBatch` YA (no espera a `completeLot`) y persiste las líneas con `saveLotLine` de inmediato — así dos masas no pueden reclamar el mismo lote de esponja, y aunque el panadero salga sin tocar "Iniciar"/"Completar" el descuento queda registrado. `ProductionLotLine.spongeConsumed` marca la línea como ya confirmada: bloquea la UI (pasa a solo-lectura con botón "Quitar") y evita que `completeLot` la descuente otra vez.
- **"Quitar esponja"** (mientras el lote sigue OPEN/IN_PROGRESS): `removeSpongeFromGroup` devuelve los gramos al batch vía `returnToBatch` (inverso de `consumeFromBatch`, reactiva `DEPLETED`→`ACTIVE` si vuelve a tener saldo) y limpia la línea.
- `consumeFromBatch(batchId, grams)`: baja `remainingWeightGrams`, marca `DEPLETED` cuando ≤5g (±5g tolerancia). Falla si saldo < grams−5.
- Adornos: `Product.decorations` snapshotteados en `ProductionLotLine.decorationsSnapshot` con batch pre-seleccionado FIFO; se descuenta `gramsPerUnit × producedQuantity` al cerrar.
- `PreparationBatch`: producir N lotes en sucursal `CAN_PRODUCE` descuenta insumos del `BranchStock` + Kardex `PRODUCCION`. Doc plano.
- Permisos: `VIEW_PREPARATIONS`, `EDIT_PREPARATIONS`, `MANAGE_PREPARATION_BATCHES`.

### Recetas — receta plana, solo pan principal

**Sin etapas, sin tipos.** Lista plana de componentes + pasos + productos que genera. `RecipeStage`/`RecipeStageType` solo para deserializar legacy. Esponja NO va en receta — vive en Preparaciones y se declara en el lote.

**Campos clave**: `components: List<RecipeComponent>` plano, `steps: List<RecipeStep>` plano, `stages` solo legacy. Helpers `allComponents`/`allSteps` aplanan legacy si necesario.

**Modos de ingreso:**
- `PERCENTAGE` — % del panadero (harinas base suman 100%).
- `WEIGHT` — pesos reales; al guardar convierte a % usando Σ pesos base como 100%. `RecipeComponent.inputQuantity` guarda la cantidad tal como se tecleó (en `ingredientUnit`) — `percentage` sigue siendo el dato canónico, pero sin `inputQuantity` reabrir la receta releía los % como si fueran pesos y los degradaba en cada guardado. Recetas viejas sin ese campo se abren en modo porcentaje con un aviso, en vez de corromperse.

**Multi-base**: varios componentes `isBase`. `validateBase`: WEIGHT → Σ pesos base > 0; PERCENTAGE → Σ% base = 100 (±0.5).

**Pasos**: `RecipeStep(id, stageId="", order, text, photoUrl)`. Foto opcional vía `RecipeImageUploader` (split por plataforma). En recetas nuevas `stageId=""`.

**Legacy**: si existe `stages/` con `stageId≠""`, `FirebaseRecipeRepository.getRecipe` hidrata `recipe.stages` Y aplana en `components`/`steps`. Al editar y guardar, persiste plana; los docs viejos de `stages/` quedan huérfanos.

### Producción (flujo completo)

**Roles:**
- **Supervisor de sucursal solicitante**: crea `ProductionOrder` hacia una planta.
- **Supervisor de planta productora**: consolida pedidos en `ProductionLot`, aprueba pedidos tardíos, edita pedidos de cualquier sucursal mientras el lote sea `OPEN`.
- **Panadero**: abre el lote en "Lista de Producción", decide esponja por producto, ve receta escalada, marca "Completar y descontar stock".

**Chip de estado del pedido** (`effectiveOrderDisplay(orderStatus, lotStatus, isLateOrder)`):

| Real | Lote | Chip |
|---|---|---|
| PENDING | sin lote | "Pendiente" (naranja) |
| PENDING | OPEN | "Lote abierto" (azul) |
| PENDING | IN_PROGRESS | "En producción" (morado) |
| PENDING + isLateOrder | sin lote | "Pedido tardío · pend. aprobar" (rojo claro) |
| CONFIRMED | COMPLETED | "Confirmado" (verde) |
| CANCELLED | — | "Cancelado" (gris) |

**Identidad del lote** = `(producingBranchId, productionDate, shiftId)`. Cada turno tiene su propio lote — no se mezclan mañana y noche.

**Modelos**: `ProductionOrder` (+`shiftId/shiftName`, `isLateOrder`, subcolección `lines/` con `requestedQuantity` y, para los pedidos especiales, `isSpecial`/`specialRecipeId`/`specialRawWeightGrams`/`specialPhotoUrl`/`specialUnitPrice`); `ProductionLot` (+`shiftId`, `sourceOrderIds`, subcoll `lines/` con `ProductionLotLine` que tiene `rawWeightGrams`, `plannedQuantity`, `producedQuantity`, `recipeId`, `spongeBatchId/Name`, `spongeGramsUsed`, `decorationsSnapshot`).

**`ProductionOrderFormScreen`** (3 pasos creación, 2 edición):
- Step 1: sucursal solicitante + planta + turno + fecha + notas. Turno requerido si planta tiene turnos activos. `rememberSaveable` con IDs (no objetos).
- Step 2: productos + cantidades. En edición + ≥800dp: dos columnas (info izq fija, productos der).
  - **Agrupado por masa**: la lista se secciona por la receta de la que sale cada producto (barcas, cuadrados y milhojas juntos bajo "Hojaldre"). Alfabético dentro y entre masas; los productos sin receta (reventa, o falta el yield) caen en "Otros productos" al final. Secciones plegables — se guardan las **plegadas**, así una masa nueva aparece abierta sin tocar nada. El badge dorado del encabezado suma las unidades pedidas de esa masa: es el mismo número que después decide si la amasada llega al mínimo.
  - `ProductionUiState.recipeRefByProductId` se llena en `loadOrderFormData` con `getRecipesWithYields` y **se cachea** (el VM es singleton): esa consulta es N+1 y no vale la pena repetirla en cada pedido.
- Step 3 (`OrderReviewStep`): resumen read-only + banner amarillo + botones "Editar pedido" / "Enviar pedido".
- **Detección de duplicado**: combinación `(sellingBranch, producingBranch, date, shiftId)` con pedido PENDING:
  - Standalone → dialog "Editar el existente".
  - Ya en lote OPEN/IN_PROGRESS → permite crear con `isLateOrder=true` (sin dialog).
- **Permisos de edición**:
  - Sin lote → sucursal solicitante con `SEND_PRODUCTION_ORDER`.
  - Lote OPEN → solo la planta. Sucursal ve banner "Contacta al supervisor de la planta productora (X)".
  - IN_PROGRESS/COMPLETED → nadie edita.

**Cantidades sugeridas por día de la semana** (`OrderPattern`):
- Doc denormalizado `companies/{c}/orderPatterns/{selling}_{producing}_{shiftId|sinturno}_{isoWeekday}`.
- Campos: `recentOrderIds` (últimos `MAX_SAMPLES=4`, más reciente primero) + `quantitiesByOrder: Map<orderId, Map<productId, Double>>`.
- **Costo**: 1 lectura al abrir el form. El patrón se mantiene al día desde `saveOrder` / `updateOrder` (`recordOrderInPattern`) y se limpia en `updateOrderStatus(CANCELLED)` (`withoutOrder`). Solo si el doc no existe se hace `rebuildOrderPatternFromHistory` (query `where sellingBranchId ==`, filtro cliente por día/planta/turno, lee líneas de ≤4 pedidos) y se persiste.
- Se guarda por pedido (no acumulado) para que editar un pedido reemplace su muestra en vez de duplicarla.
- `suggestedQuantities()`: un producto se sugiere solo si apareció en ≥ la mitad de las muestras; el valor es la **mediana** de las cantidades no-cero, redondeada. Evita que un día de fiesta arrastre el resto.
- `ProductionViewModel.applySuggestedQuantities(...)` prellena `draftOrderLines` al entrar al step 2 (solo creación, solo si el draft está vacío). `suggestionPatternId` evita re-sugerir. Banner ámbar `SuggestionNotice` con "Empezar de cero" / "Ya las revisé".
- Los campos de cantidad usan `ImeAction.Next` + `moveFocus(Down)` (último = `Done`).

**Clima ("Mateo" = Open-Meteo) — recomienda subir el pedido si hay lluvia probable, no lo sube solo**:
- `domain/WeatherRepository.kt` + `data/OpenMeteoWeatherRepository.kt` (`commonMain`, sin API key, llamable directo desde el navegador — un solo archivo, no necesita variantes por plataforma salvo `httpGetText`). Nunca lanza: sin red/error → `null`, y el flujo de sugerencias sigue funcionando sin clima.
- `ProductionViewModel.refreshRainForecast(sellingBranchId, requestedDate, lat, lng)`: se dispara independiente de la sugerencia por historial (`LaunchedEffect` propio en `ProductionOrderFormScreen`), para que cambiar de fecha no deje pegado el pronóstico del primer día probado. Requiere `Branch.latitude/longitude` — se capturan una sola vez por GPS (`LocationRequester`) al crear el primer pedido de esa sucursal.
- `RAIN_PROBABILITY_THRESHOLD_PERCENT` (60%) y `RAIN_BUMP_FRACTION` (+15%) en `model/OrderPattern.kt::withRainBump`. **No se suma sola** al prellenar `draftOrderLines` (eso creaba una espiral: la mediana de `suggestedQuantities()` de la próxima semana lluviosa ya venía inflada con el bulto de la semana anterior, y el sistema sumaba OTRO +15% encima). `applySuggestedQuantities` calcula `rainDeltaByProduct` solo para mostrarlo como recomendación aparte junto a cada producto ("Te recomendamos aumentar X (+15%) por pronóstico de lluvia" en `ProductOrderRow`) — la encargada decide si lo escribe o no. Nunca inventa cantidad para un producto sin historial.
- **`DailyForecast.periods`**: 4 tramos fijos (Mañana 6-10h, Mediodía 11-13h, Tarde 14-17h, Noche 18-21h), cada uno con % máximo y la condición más "fuerte" (mapeo de `weathercode` WMO) de esas horas — reemplaza una sola "hora de inicio" (probabilidad diaria y detalle horario son cálculos independientes en Open-Meteo; una sola cifra + una sola hora daba una imagen falsamente uniforme del día). `RainForecastCard` los pinta como chips con ícono (`WbSunny`/`FilterDrama`/`Cloud`/`Grain`/`Umbrella`/`Thunderstorm` de `compose.materialIconsExtended`).
- **`ProductionOrderLine.rainBumpUnits`**: campo snapshot que queda en 0 mientras el bulto no se auto-aplique (caso de hoy). Existe para que, si en el futuro se agrega un botón "aplicar sugerencia" con un tap, `FirebaseProductionRepository.recordOrderInPattern` pueda seguir restándolo antes de guardar la muestra histórica (`(requestedQuantity - rainBumpUnits).coerceAtLeast(0.0)`) y la espiral no vuelva por esa puerta.

**`ProductionOrderListScreen`**: tabs "Mis pedidos" / "Recibidos" / "Consolidado".
- Chips de sucursal: `BranchChip` con `BranchChipRole.REQUESTING` (azul #005B8D, texto blanco) y `PRODUCING` (dorado #D9A441, texto #4A3400). Se usan igual en la tabla (≥720dp) y en las cards.
- Filtro de mes (chevrons + `MonthPickerDialog`), default = mes en curso, `rememberSaveable`.
- Chip turno en "Recibidos".
- Tabla en ≥720dp con columnas; cards en <720dp. Sucursal solicitante en chip azul `#123456`.
- **Side panel ≥1000dp**: tap a fila abre `ProductionOrderDetailContent` (sin Scaffold) en columna derecha; en móvil navega a screen completo.
- **Listener vivo** (`observeOrdersForMonth`): combina `observeOrders` + `observeLots` vía `Query.snapshots`. Un listener por colección, no polling.
- Tab Consolidado: planta + chips turno + chevrons fecha. Tabla con bordes y filas alternadas. Si ya existe lote para `(planta, fecha, turno)` muestra banner verde + link al detalle.
- **Orden de productos configurable**: `Branch.productSortOrder: List<String>` (productIds). Default vacío = alfabético; los productos fuera de la lista van al final alfabéticamente. Se edita con `ProductSortOrderDialog` (botones subir/bajar, no drag — se usa en tablet con las manos ocupadas) desde el tab Consolidado, gateado por `MANAGE_PRODUCTION_LOTS`. Se persiste con `BranchRepository.updateProductSortOrder` (escritura **parcial**: un `set()` desde ahí sobreescribiría la sucursal). `BranchFormScreen.build()` arrastra `productSortOrder = existing?.productSortOrder` porque `updateBranch` sí hace `set()`.

**`ProductionLotListScreen`**: filtro por día calendario (chevron, tap centro = hoy). Ordenado por nombre de turno.

**`ProductionLotFormScreen`** (supervisor arma lote):
- Acepta `presetBranchId/presetShiftId/presetDate` por nav args. Del Consolidado son labels (`InfoLabelCard`); del FAB son selectores.
- `loadLotFormData` toma `shiftId` y filtra `getPendingOrdersForProducingBranch`. Sin shiftId solo carga turnos (evita deadlock).
- Comparación por **día calendario** (`LocalDate` en TZ).
- Lista consolidada **NO editable** — se modifica editando pedidos fuente (lote se recalcula).
- NO muestra UI de esponja (decisión del panadero).

**`ProductionLotDetailScreen`** ("Lista de Producción", pantalla del panadero):
- **Listener vivo** (`observeLotDetail`): combina `observeLots` + `observeLotLines(lotId)` + `observeOrders`. Mergea `editableLotLines` para preservar selección de esponja.
- **`KeepScreenOn()`** (expect/actual en `ui/KeepScreenOn.kt`): Android `FLAG_KEEP_SCREEN_ON`; web `navigator.wakeLock.request('screen')` con re-solicitud en `visibilitychange`; iOS `UIApplication.idleTimerDisabled`; wasmJs no-op.
- Split-pane en ≥1000dp (lista 1f / receta escalada 1.4f); ModalBottomSheet en móvil.
- Líneas **read-only** (planta edita vía pedidos fuente).
- **Panel "Pedidos tardíos pendientes"** entre tabla y botones: muestra pedidos PENDING para `(planta, día, turno)` no incluidos en `sourceOrderIds`. `LateOrderCard` con preview de líneas. Botón verde "Aprobar pedido tardío" → `addOrderToLot` consolida por `productId`. Si lote IN_PROGRESS, dialog "¿coordinaste con el panadero?".
- Selección de fila → panel derecho con `ScaledRecipePanel` + `SpongeControlsForMasa` (checkbox → diálogos de lote/cantidad/confirmación, ver sección Preparaciones arriba).
- Botones: "Iniciar producción" (OPEN→IN_PROGRESS, persiste editableLotLines) y "Completar y descontar stock" (IN_PROGRESS→COMPLETED).
- **Conteo real obligatorio por masa antes de terminar el lote.** `RegisterProductionSheet` ("Registrar conteo" del TopAppBar = todas las masas, lápiz de cada `MasaProductionCard` = solo esa masa) agrupa por receta y prellena con lo ya registrado o, si nada, deja el campo en blanco mostrando "Pedido: N" como referencia. Al presionar "Guardar todo", **todas** las líneas de la(s) masa(s) mostradas quedan con `ProductionLotLine.producedCountConfirmed = true` — incluso las que no se tocaron porque el número ya era correcto, no solo las editadas — así "dejarlo en blanco porque ya estaba bien" cuenta como confirmado y "nunca abrí el conteo" no. El botón "Terminar lote y descontar stock" se deshabilita (con aviso listando las masas faltantes) mientras quede alguna masa con productos sin confirmar; el ícono de cada `MasaProductionCard` cambia de lápiz a ✓ verde cuando su masa ya está completa. Lotes que ya estaban IN_PROGRESS antes de este cambio arrancan con todo sin confirmar — necesitan pasar por "Guardar todo" al menos una vez por masa.

**`ProductionLotCompleteScreen`**: confirma cantidades reales + dispara `completeLot` (consumo insumos Kardex + consumo batches esponja/adornos + marca `sourceOrderIds` como CONFIRMED + auto-crea `DispatchNote` DRAFT por cada source order).

**Cancelación de pedido (`cancelOrder`)**:
- Sin lote → sucursal solicitante cancela libre.
- Lote OPEN → solo planta; `removeOrderFromLot` resta líneas (elimina las que queden en 0) y quita `orderId` de `sourceOrderIds`.
- IN_PROGRESS/COMPLETED → bloqueado.

**Borrado de raíz (`deleteOrderPermanently`, solo OWNER)**:

Cancelar es lo correcto para un pedido real — deja el rastro de que existió. Un pedido de **prueba** no debería dejar rastro, y `cancelOrder` además se bloquea en cuanto el lote arranca, que es justo donde queda atrapada una prueba que se llevó hasta producción. De ahí esta salida de emergencia, gateada por `user.role == UserRole.OWNER` (rol, no permiso: no es parte de la operación diaria y no se delega).

- Cascada, en orden: notas de despacho (`getNotesForOrder` + `deleteNote`, encabezado y líneas) → lotes → pedido (`deleteOrder`, que **también limpia la muestra del patrón**, mismo criterio que `updateOrderStatus(CANCELLED)`) → avisos de la campana (`NotificationRepository.deleteForTargets`, si no quedan sonando y llevan a una pantalla vacía).
- **El lote solo se borra si queda sin ninguna otra fuente** (`sourceOrderIds` vacío y `customerOrderIds` vacío); si sirve a otras sucursales se usa `removeOrderFromLot`, que ya resta la porción correcta en lotes de reparto por turnos. En datos reales el caso común es el segundo.
- Antes de borrar un lote se devuelve la esponja con `returnToBatch`: ese consumo se registra al **confirmarla**, no al cerrar el lote, así que sin esto los gramos quedan consumidos por un lote inexistente.
- **Guardas que abortan sin tocar nada**: lote `COMPLETED` (ya descontó insumos y escribió Kardex) o nota `IN_TRANSIT`/`ARRIVED`/`DELIVERED*` (documenta pan que ya viajó). Ahí el borrado dejaría movimientos apuntando a documentos que ya no existen — peor que el pedido de prueba que se quería limpiar.
- El gate es **solo de cliente**: `firestore.rules` deja escribir a cualquier usuario autenticado de la empresa (`companies/{c}/{document=**}`), igual que el resto de permisos de la app.
- Para lo que ya pasó las guardas queda el script admin — ver `functions/tools/cleanup-test-orders.js`.

**Recomputación automática** (`recomputeLotLinesFromSources`): al guardar un pedido en `lot.sourceOrderIds` (lote OPEN), re-suma `requestedQuantity` por `productId` de TODOS los sources, preservando esponja/adornos. Elimina líneas en 0 y crea nuevas si faltaban.

**`ScaledRecipePanel`**: tabla adaptativa:
- Sin esponja: `Ingrediente | % | A pesar`
- Con esponja: `Ingrediente | % | Receta | Esponja | A pesar`
- Formato pesos: `<28g→g`, `<453g→oz`, `≥453g→lb`.

**`ProductionViewModel`**:
- `buildScaledRecipeView(line)` / `diagnoseScaledRecipe(line)`: builder retorna null si falta data; diagnose retorna mensaje exacto.
- `effectiveRawWeightGrams(line)`: fallback a `productsById[line.productId]` si línea tiene `rawWeightGrams=null` (lotes legacy).
- Usa `producedQuantity` si >0, si no `plannedQuantity`.
- `productsById` cache refrescada por screens vía `updateProductsCache(productList)`.

**Métodos repo críticos**:
- `observeOrders(c)` / `observeLots(c)` / `observeLotLines(c, lotId)` — Flow vía `Query.snapshots`.
- `getOrdersForSellingBranchInRange` / `getOrdersForProducingBranchInRange` — filtro por rango (evita histórico).
- `findPendingOrderForCombination` — detecta duplicado para tardío.
- `updateOrder(c, order, lines)` — reemplaza header + subcolección (borra obsoletas).
- `getActiveLotForBranchDateShift` — lote no-CANCELLED para esa cuádrupla.
- `addOrderToLot(c, lotId, orderId, newLines)` — fusiona tardío por `productId`. NO marca CONFIRMED (eso es al `completeLot`).
- `removeOrderFromLot(c, lotId, orderId, lines)` — inverso.
- `deleteLotLine` (solo OPEN); `saveLotLine` (id vacío = genera nuevo); `consumeFromBatch`; `listActiveBatches`.

**Comparación de fechas**: todo "mismo día" usa `Instant.toLocalDateTime(tz).date` (no millis) para tolerar drifts TZ.

### Material de planta — lo que la receta no explica (`PlantIssue`)

**Se llama "Anotar material", no "Sacar material".** Lo que se nombra es el REGISTRO, no
la extracción: "sacar" describe el acto de llevarse algo y deja a quien deja constancia
como sujeto de sospecha — justo al revés del incentivo que el módulo necesita. Paralelo
exacto a "Anotar gasto", que ya vive en la misma sección de Operaciones y nadie lee como
"gastar plata de la empresa". El título del diálogo es "Material usado en planta" y el
botón de confirmar dice "Anotar".

`completeLot` solo descuenta lo que el % del panadero justifica (gramos por pieza ×
piezas). La harina que se le pone a la mesa para bolear, la manteca de las bandejas y el
espolvoreo se iban en silencio, y el faltante recién aparecía en el conteo físico —
cuando ya no había forma de saber si fue la mesa, un mal pesado o un robo.

**El empaque completo es el DEFAULT, no la única opción.** "Saqué un saco" es un hecho que
el panadero verifica sin balanza; "gasté 3.5 lb enharinando" es una estimación que a las
4am nadie hace bien, y del empaque sale el ritmo de rotación. `formatStockDisplay` ya
mostraba el stock en presentaciones ("5 × Saco 100 lb"), así que la app ya pensaba en sacos.

Pero **la cantidad suelta es una opción pareja, no una escondida detrás de la otra**: el
dueño agarra 8 oz de manteca del bote ya abierto, y obligarlo a declarar "1 bote" para
poder anotarlo lo haría mentirle al inventario por un dato que él mismo quiere correcto.
Dos chips —"Empaque completo" / "Cantidad suelta"— con el empaque preseleccionado.

- **La unidad de la cantidad suelta sale del insumo** (`Ingredient.looseUnitOptions()`),
  no de una lista fija: la manteca se agarra en onzas y la harina en libras. Se filtran
  las que no convierten a la unidad del stock — ofrecer una unidad que después rechaza el
  vale es peor que no ofrecerla. `looseToBaseUnitOrNull` hace la conversión y devuelve
  null cuando no hay puente, igual que `packagesToBaseUnitOrNull`. **Sin esa conversión,
  8 oz de manteca descontaban 8 lb** — 16 veces de más.
- **`looseQuantity` + `looseUnit` guardan lo tecleado**, no solo el equivalente en unidad
  base. Mismo criterio que `RecipeComponent.inputQuantity`: el dato canónico es
  `quantityInBaseUnit`, pero sin el original un vale de "8 oz" se relee como "0.5 lb" y
  deja de parecerse a lo que la persona anotó. Por eso `kardexNote` incluye SIEMPRE la
  cantidad — la columna del Kardex muestra la unidad base.

**El descuadre se acepta a sabiendas y está acotado.** El saco sale del sistema el día que
se abre y en la mesa dura tres, así que el inventario dice 0 mientras hay medio saco
abierto. Lo que lo hace tolerable: el descuadre es de **un empaque abierto por insumo** —un
número conocido— y **el hueco entre un vale y el siguiente ES el dato que se quería**
("¿cada cuánto toman otro saco?"). No hace falta que nadie declare cuándo se terminó, que
es justo el segundo registro diario que no se iba a hacer. Un modelo de dos baldes (bodega
→ "en uso", se gasta al declararlo terminado) daría inventario exacto y se puede montar
encima después sin migrar nada, porque los vales ya quedan registrados.

- **`companies/{c}/plantIssues/{id}`, colección plana.** Documento propio y no solo un
  `StockMovement` por la misma razón que `Purchase` lo es: el Kardex vive en subcolección
  por insumo, y contestar "cada cuánto sale un saco de harina suave" leyendo N
  subcolecciones no escala. Plana, como `branchStocks` y `stockLots`.
- **El vale NO mueve stock por su cuenta.** `PlantIssueViewModel` escribe el documento y
  después llama `IngredientRepository.updateBranchStock`, que ya es el único cuello de
  botella por el que pasan los cuatro caminos que bajan inventario (y que mantiene el
  libro FEFO). Meterlo dentro del repositorio habría creado el quinto camino, que es
  exactamente lo que ese método existe para evitar. El documento se escribe **antes** de
  mover stock, igual que `receivePurchase`.
- **Tipo de Kardex propio: `CONSUMO`** (`PLANT_ISSUE_MOVEMENT_TYPE`), no `AJUSTE`. El
  ajuste documenta una CORRECCIÓN (el conteo no cuadraba); esto documenta un CONSUMO real.
  Mezclarlos haría imposible separar "la harina que se fue en la mesa" del "saldo que
  estaba mal", que es el reporte entero. `stockMovementTypeLabel` traduce los tipos para
  el Kardex del insumo, que hasta ahora mostraba el string crudo.
- **Motivos de lista fija con salida a texto libre** (`PlantIssueReason`): enharinar mesa,
  engrasar bandejas, espolvorear, prueba de masa, derrame, limpieza, otro. Con motivo
  escrito a mano, a los tres meses "enharinar mesa", "harina mesa" y "p/ mesa" son tres
  renglones y no se puede sumar nada. Se guarda el nombre, no el ordinal.
- **`packagesToBaseUnitOrNull` devuelve null cuando no hay equivalencia** (un empaque
  contado sobre un insumo guardado en libras sin `unitWeightGrams`), y ahí el vale se
  rechaza con un mensaje. Mismo criterio que `toGramsOrNull`: antes `toBaseUnit` pasaba el
  valor tal cual y 30 huevos entraban como 30 libras. Lógica pura en `model/PlantIssue.kt`,
  con tests en `PlantIssueTest`.
- **El intervalo se mide entre vales consecutivos del mismo insumo**, no dividiendo el
  período entre el número de salidas: tres sacos la primera semana y ninguno las otras tres
  daría "uno cada 7 días" y perdería el dato real, que el consumo se concentró. Con un solo
  empaque `avgDaysBetween` es **null**, no 0 — 0 se leería como "abren uno diario".
- **Solo cuentan los EMPAQUES para el intervalo**, no todas las salidas. Agarrar 8 oz del
  bote ya abierto no abre un empaque nuevo: contarlas acorta el intervalo y hace ver una
  rotación que no ocurrió. Los totales de cantidad sí las incluyen — ese material salió.
  `packageIssueCount` separa unas de otras y `rhythmLabel()` resuelve los tres casos (ritmo
  medible / un solo empaque / solo sueltas) en un lugar, porque los comparten el reporte
  propio y el de Producción y el tercero es fácil de olvidar en uno de los dos.
- **La advertencia "puede que ese saco siga abierto" solo sale sobre empaques**
  (`daysSinceLastIssue(onlyPackages = true)`): sobre las onzas que alguien agarró del bote
  no aplica, y una alerta que suena cuando no corresponde deja de avisar.
- **La guarda contra anotar dos veces el mismo saco es el aviso, no una validación**: el
  diálogo dice "Última salida: hace 2 días · 1 × Saco de 100 lb · Mario · Enharinar mesa"
  antes de confirmar. Cuesta cero toques y es lo único que existe entre el vale correcto y
  el duplicado.
- **Deshacer devuelve el stock y borra el documento, pero NO limpia el Kardex**: escribe el
  reverso como `AJUSTE` con "Salida anulada: …". Mismo criterio que la corrección de un
  gasto de caja y que `applyStockDelta` — un movimiento borrado deja el saldo cuadrando
  contra una historia que ya no dice qué pasó. El diálogo lo avisa antes.

**Dónde se anota**: botón "Anotar material" en el TopAppBar de `ProductionLotDetailScreen`
—la pantalla que el panadero ya tiene abierta con las manos en la masa, con planta, lote y
turno prellenados— **y** el módulo "Sacar material" en Operaciones del Home, para la
limpieza de fin de turno o la harina puesta antes de que exista el lote. Exigir lote
dejaría esos casos sin registrar. El `lotId` es opcional por eso mismo.

**Permiso propio `ISSUE_PLANT_MATERIAL`**, no reuso de `EDIT_INGREDIENTS` ni de
`RECEIVE_STOCK`: el punto entero es que el panadero anote su saco sin poder además editar
el catálogo ni ajustar stock a mano.

**Reportes**: `PlantIssueReportScreen` (`production/plant-issues`) es a la vez el reporte y
el historial —quien mira "un saco cada 2.5 días" es quien quiere ver los vales que dan ese
número, y es el único lugar desde donde se corrige uno mal anotado. Se abre con
`ISSUE_PLANT_MATERIAL` **o** `VIEW_INVENTORY_REPORT` (que existía en `Permission.kt` sin
pantalla): el dueño quiere el reporte y no saca material. El reporte de Producción lleva
además una sección "Material de planta", porque comparar un turno con otro es información
de producción: dos turnos que hornean lo mismo y gastan el doble de harina de mesa es un
hallazgo, y ahí es donde se ve.

**No genera `Expense`**: ese dinero ya se gastó al comprar el insumo. Baja el valor del
inventario, que es lo correcto. Tampoco entra al costo del producto — el módulo de costeo
todavía no existe, y cuando llegue este consumo es costo indirecto de planta, no de una
pieza.

### Cuando el lote ya va andando: pan perdido y pan de más (`ProductionAdjustment.kt`)

Dos cosas que pasan a media producción y que el sistema no sabía recibir:

- **Se quemaron 40 panes de capa de 100.** No fue todo, ni todos los tipos, así que el
  panadero no podía repetir la receta completa — y **no sabía escalarla al 40%**. Ése era
  el bloqueo real: `buildScaledRecipeViewForGroup` ya escala cualquier receta a cualquier
  cantidad, pero no había forma de pedírselo con el lote abierto (las líneas son de solo
  lectura para el panadero y el único camino era editar el pedido de la sucursal).
- **Llega un encargo de 100 pirujos a media mañana.** Si la masa no se hizo, se suma a la
  amasada; si ya se hizo, es una tanda aparte. En los dos casos hay que saber la receta de
  ese incremento.

**El modelo colapsaba dos números en `producedQuantity`**: *cuánto se amasó* (base de la
receta escalada y del descuento de `completeLot`) y *cuánto salió bueno* (lo que se
entrega). Mientras todo sale bien son el mismo número; estos dos casos lo rompen desde
lados opuestos — el quemado descontaba insumos de menos (la harina de las 40 quemadas
aparecía después como faltante en el conteo físico), y el encargo horneaba pan que no
entraba a ningún inventario.

`ProductionLotLine.wasteQuantity` y `.extraQuantity` los separan, y tres funciones puras
los vuelven a juntar según para qué:

| Función | Qué cuenta | Para qué |
|---|---|---|
| `kneadedQuantity()` | bueno + perdido + de más | Consumo de insumos en `completeLot` |
| `quantityToKnead()` | bueno + de más | La lista del panadero y la receta escalada |
| `plantIngestQuantity()` | excedente del mínimo + de más | Ingreso al stock terminado de la planta |

**La asimetría entre merma y extra es deliberada**: lo que se hace de más todavía no se
amasó, así que entra a la lista; lo quemado ya se amasó una vez, y sumarlo mostraría "140"
— una amasada que nunca se hace de un solo golpe, y el panadero pesaría de más. La
reposición de lo perdido se calcula aparte, en el momento de declararla.

**El extra entra al stock de la planta por el mismo camino que `plantRetainedQuantity`**
(el excedente del mínimo de amasada): dos razones, un solo movimiento de Kardex.
`applyMovement` lee-modifica-escribe el stock, así que dos llamadas seguidas sobre el
mismo documento se pisan — por eso `plantIngestQuantity()` las suma en vez de emitir dos.

**El consumo de `completeLot` usa `kneadedQuantity(fallbackToPlanned = false)`.** Ahí el
conteo ya es obligatorio, así que un `producedQuantity` en cero significa "no se produjo",
no "todavía no se contó": con el fallback puesto se descontarían insumos de pan que nunca
se hizo.

#### `ProductionAdjustmentSheet` — "¿Qué pasó?"

Ícono de aviso en el encabezado de cada `MasaProductionCard` (no en el TopAppBar: lo que se
quema es una masa concreta, y la receta que hay que mostrar es la de esa receta). Se ofrece
desde que el lote está **OPEN**, no solo IN_PROGRESS — el encargo puede llegar antes de que
el panadero toque "Iniciar", y ahí sumarlo a la amasada es lo más barato.

**Pregunta la clase de cosa antes de mostrar un campo**, igual que `CaptureKindStep` de la
compra rápida: "Se perdió pan" pide motivo de merma y no toca los pedidos; "Hay que hacer
más" pide motivo de demanda y sí suma pan. Un formulario único obliga a leer las dos
etiquetas para saber cuál aplica, y a las 4am eso se resuelve llenando la equivocada.

**Lo que la hace útil no es el registro, es la receta.** Apenas se teclea una cantidad
aparece abajo `ScaledRecipePanel` escalado exactamente a esas unidades —"para reponer 40
panes de capa: harina 4.5 lb…"—, construido con líneas sintéticas que reusan
`buildScaledRecipeViewForGroup`. Un segundo cálculo paralelo dejaría esta receta mintiendo
apenas se corrija algo en la principal, mismo criterio que `buildProductionOrderPrintData`.
**La esponja se pone en cero** en esas líneas: la ya declarada se consumió en la amasada
anterior, y descontar su aporte daría una receta corta justo en lo que la esponja aporta.
La hoja lo avisa cuando la masa lleva esponja — ajustarla automáticamente exigiría tocar el
flujo de tres diálogos de `confirmSpongeForGroup`, y quedó pendiente a propósito.

**Las cantidades se SUMAN a lo ya declarado, no lo reemplazan**: si a las 5am se quemó una
bandeja y a las 7am otra, son dos declaraciones del mismo turno. La fila muestra "ya
anotado: N" para que quien declara la segunda sepa si la primera está incluida.

**Se persiste de inmediato**, sin esperar a "Iniciar" ni a "Completar" — mismo criterio que
`confirmSpongeForGroup`: el pan quemado es un hecho ya ocurrido, y si el panadero sale de la
pantalla ese consumo quedaría sin registrar.

**Declarar pan de más invalida el conteo confirmado** de esa línea (`producedCountConfirmed
= false`): cambió cuánto pan va a haber. La merma no lo invalida — lo bueno que se contó
sigue siendo lo bueno.

**El doble conteo es el modo de fallo obvio** y por eso la hoja lo dice con todas las
letras: *"Al final, en el conteo, contá solo el pan BUENO: lo perdido ya queda contado
aquí."* Un panadero que reste lo quemado del conteo **y** lo declare acá descontaría esa
masa dos veces. La diferencia entre reponer y no reponer la lleva el conteo final, no un
campo aparte: si repuso cuenta 100, si no cuenta 60, y el consumo cuadra en los dos casos.

**Permiso**: `MANAGE_PRODUCTION_LOTS`, el mismo que ya necesita para iniciar y completar.
Quien ve el pan quemado es quien lo anota, en el momento — pedir autorización a esa hora
significa que no se anota.

#### Dos recetas válidas a la vez: el selector del panel

Al declarar pan de más, el panel de la masa pasa a mostrar el total del día (300). Eso es
correcto si todavía no se amasó nada, y **le miente a quien ya amasó los 200**: pesaría de
más. Pero la receta del incremento solo existía dentro de la hoja mientras se tecleaba —
al cerrarla no había forma de volver a verla.

El sistema **no puede elegir cuál mostrar**: no existe un estado "ya amasé esta masa", y
deducirlo del cronómetro o de la esponja sería adivinar. Así que las dos vistas conviven y
lo decide el panadero con un toque: `RecipeScopeSelector` ("Todo el día" / "Solo lo
extra"), que aparece en `headerExtra` del `ScaledRecipePanel` **solo cuando hay producción
extra declarada** — sin extra sería una opción que no hace nada.

Las etiquetas dicen qué cubre cada una y no "total"/"parcial": lo que hay que decidir es
si ya se amasó o no, y "parcial" no contesta esa pregunta. El texto de ayuda lo dice
directo ("Usala si ya amasaste lo que pedían").

`buildExtraOnlyRecipeForGroup` arma la vista del incremento con líneas sintéticas de
`extraQuantity` y **suma los rellenos que exigen los productos extra de otras masas**: sin
eso, pedir 100 Pan de Capa de más mostraría la hojaldre pero no las 100 bolitas de Suave
que llevan adentro. La esponja va en cero, por lo mismo que en la hoja.

El selector arranca en "Todo el día" y se reinicia al cambiar de masa
(`remember(selectedGroupKey)`): arrancar en una vista parcial es el error caro —
alguien que no amasó nada pesaría solo el incremento.

#### Un pan de DOS masas (Pan de Capa)

El Pan de Capa es masa de Capa por fuera con una bolita de Suave adentro (ver
[DoughFilling]): son dos amasadas distintas para un solo producto. Es el caso que más
fácil queda mal, porque solo la masa propia sale de `calculateLineConsumption` — el
relleno, los adornos de preparación y los adornos comprados se calculan **en tres
recorridos aparte** dentro de `completeLot`.

**Los cuatro tienen que usar `kneadedQuantity`, no `producedQuantity`.** El pan que se
quemó ya estaba formado y relleno, así que su bolita de Suave y su concha se gastaron
igual. Corregir solo la receta principal dejaba la mitad del consumo sin descontar y el
faltante volvía a aparecer en el conteo físico — con la agravante de que el producto que
se arruinó se hace de dos masas y solo una estaba saliendo bien de la cuenta.

**La hoja muestra las DOS recetas.** `buildFillingRecipesForQuantities` escala cada
relleno a las mismas unidades y la hoja las pinta debajo de la principal, con un aviso
("este pan lleva dos masas — hay que amasar las dos") **antes** de la primera tabla: sin
él, el panadero lee la de arriba como *la* receta y no busca la segunda. Se agregan por
receta, así que dos productos del grupo con bolita de la misma masa dan una sola amasada.
Reusa `buildScaledRecipeViewForGroup` pasando los gramos como `extraDoughGrams` sobre una
lista vacía de líneas — exactamente el camino por el que ya se calcula la tarjeta de una
masa que nadie pidió y que existe solo por el relleno de otro producto.

En la lista principal el extra ya se propaga solo: `extraDoughGramsForRecipe` usa
`effectiveQuantity`, que ahora es `quantityToKnead()`. Agregar 100 Pan de Capa hace crecer
la tarjeta de Suave por las 100 bolitas, sin código nuevo.

#### Merma de horno en el reporte

`ProductionSummary.wasteTotal` / `.extraTotal` / `.wasteByProduct` y una sección "Pan
perdido en el horno" en el reporte de Producción, junto a "planeado vs producido" porque es
lo que explica la diferencia entre la masa que se gastó y el pan que salió. Es un dato que
**no existía**: había merma en tránsito (despacho) y merma de tienda
(`FinishedStockMovement`), pero lo que se quema en el horno no se registraba en ningún lado,
así que su costo aparecía como un faltante de insumos sin explicación.

`ProductionWasteSummary.wastePct` divide entre **lo amasado**, no entre lo bueno: la
pregunta es "de cada 100 que metí al horno, cuántas perdí", y dividir entre lo bueno da más
de 100% justo cuando se pierde más de la mitad, que es cuando más importa leerlo.

Lógica pura en `model/ProductionAdjustment.kt`, con tests en `ProductionAdjustmentTest`.

### Mínimo de amasada por receta

`Recipe.minBatchFlourGrams` (0 = sin mínimo). Es límite **de la máquina, no del producto**: por debajo de cierta harina el gancho no alcanza la masa.

- **Por receta, no por producto**: de una masa salen varios panes y a la amasadora le da igual en qué se corten. 5 bolitas solas no amasan; 5 bolitas + 30 pirujos de la misma masa, sí.
- **No se valida al pedir**: cuando la sucursal envía su pedido no se sabe qué van a pedir las demás. La decisión vive en la consolidación del lote (`ProductionLotFormScreen`), que es cuando ya está toda la demanda del día.
- Lógica pura en `model/RecipeBatchMinimum.kt`: `checkRecipeBatchMinimums(...)` agrupa por `recipeId`, calcula `harina = Σ(qty × rawWeightGrams) × 100 ÷ Σ%` (misma fórmula que `ScaledRecipePanel`) y compara. `minimumQuantitiesByProduct()` escala proporcionalmente y redondea hacia arriba.
- `MinimumBatchDecisionDialog`: la encargada reparte **solo el excedente** (lo pedido no se toca). Lo que no asigne a sucursales queda en la planta — ése es el default, porque mandarle a una sucursal 5× lo que pidió suele ser merma.
  - "Hornear el mínimo" → `resolveMinimumBatchByBaking`: sube `draftLotLines` + sube las líneas de los pedidos a los que se les asignó excedente (las notas de despacho salen de los pedidos, sin esto la sucursal no recibiría el extra).
  - "Anular" → `resolveMinimumBatchByCancelling`: saca el producto del borrador **y de los pedidos** (si quedara, el despacho prometería pan que nunca se horneó) + notifica.
- `ProductionLotLine.plantRetainedQuantity`: unidades horneadas que no se despachan. `completeLot` las ingresa al `FinishedProductStock` de la planta como `AJUSTE`. `recomputeLotLinesFromSources` las preserva (si no, editar un pedido borraría la decisión de la encargada).

### Reparto por turnos (planta de 2 turnos)

Una planta con exactamente 2 turnos activos no recibe un pedido por turno: las sucursales piden **sin turno** (`shiftId` vacío) y la planta reparte la demanda del día entre los dos. `ProductionShiftSplitScreen` + lógica pura en `model/ShiftSplit.kt`.

**El reparto es por sucursal, y el total del turno se deriva sumando.** Ésa es la dirección de la dependencia, y cuesta ver por qué hasta que se rompe:

- `defaultOrderAllocationsForShiftA` — **cada sucursal va mitad y mitad de lo suyo**, salvo que haya declarado un encargo (`ProductionOrderLine.earlyShiftQuantity`). El total de cada turno sale de sumar las sucursales.
- `pinOrderAllocation` — fijar una sucursal **no toca a las demás**: si Democracia necesita 150 de sus 200 en la mañana, la mañana hornea más, no se le recorta a Almolonga.
- `computeShiftSplit` balancea por peso de masa y **ya no manda sobre el reparto**. Quedó para detectar exclusiones/conflictos y para el aviso de harina por turno. Mandaba antes, y producía números que la encargada no podía auditar: con un encargo de 60 de una sucursal, a otra que no pidió nada le tocaban 90 y 110 de sus 200 — correcto para igualar el peso de las dos amasadas, imposible de explicar en la planta. Lo que se perdió al sacarlo es la compensación automática cuando un producto no se hace en un turno; eso ahora lo ajusta la encargada a mano, con la harina por masa y por turno a la vista en el encabezado de cada grupo.
- `ProductionShift.excludedProductIds` fuerza el 100% de un producto al otro turno y **manda sobre el encargo** (no se puede pedir temprano algo que ese turno no hornea). Excluido de ambos → va a A con aviso (nunca se pierden unidades por una config inconsistente).
- `ProductionShift.dispatchOrder` (1 = primer viaje, 2 = segundo, 0 = sin configurar) **ordena los turnos**. No se deriva de `startTime`: el turno de noche empieza 22:00 y su pan sale a las 5am, así que ordenar por hora de inicio da justo el orden inverso al del camión. `getShiftsForBranch` ordena por este campo (los 0 al final, alfabéticos) — antes salían en el orden arbitrario de Firestore y la columna izquierda del reparto podía cambiar entre cargas.

**Sucursales que solo reciben en un viaje.** `Branch.receivesOnlyInTrip` (`0` = los dos viajes, `1` = solo el primero, `2` = solo el segundo). Hay sucursales que abren tarde: no tienen a quién recibirle el pan del primer viaje, y repartirles mitad y mitad se lo deja en la puerta.

- **Vive en la sucursal, no en el pedido**: el horario de apertura no cambia de un día para otro, y pedirle a la encargada que lo declare en cada pedido es pedirle que se acuerde todos los días. Se configura en `BranchFormScreen` (sección "¿En qué viaje recibe el pan?", visible con `CAN_SELL`).
- Se guarda como **número de viaje** y no como `shiftId`, para que coincida con `ProductionShift.dispatchOrder`: la sucursal razona "solo el segundo viaje", no "solo el turno de día", y la config sobrevive a que la planta renombre o recree sus turnos.
- `tripRestrictionPins(...)` traduce la restricción a **cantidades fijadas del turno A**, el mismo formato que un encargo declarado — así viaja por los caminos donde ya se respetaba "esto no se mueve" (`defaultOrderAllocationsForShiftA`, `splitOrderAllocations`, `rebalanceOrderAllocation`) sin abrir un caso especial en cada uno.
- **Prioridad**: exclusión del producto (`ProductionShift.excludedProductIds`, restricción física) > restricción de viaje de la sucursal > encargo declarado (`earlyShiftQuantity`) > mitad y mitad. Cuando el producto no se hornea en el único viaje que la sucursal puede recibir no hay reparto que cumpla las dos cosas: manda la producción y `tripRestrictionConflicts` devuelve el caso para avisarlo como banner, en vez de que se descubra con el camión afuera.
- En pantalla: la fila del producto y el diálogo "Por sucursal" muestran la sucursal restringida **sin campos editables** (se cambia en su ficha, no en el reparto del día), y el form de pedido esconde el encargo por viaje y avisa "recibe todo su pedido en X".
- **El total del turno se deriva del desglose por sucursal** en `recomputeSplitLotLinesFromSources` cuando los dos lotes están abiertos (`finalOrderedA/B = suma de allocations`). Antes el total venía de una heurística por producto y el desglose se calculaba aparte: un encargo o una restricción movían unidades entre turnos en el desglose pero no en la línea del lote, y el turno horneaba una cantidad mientras las notas prometían otra. El desglose conserva por construcción (A + B = lo pedido), así que derivar de él no puede perder ni inventar unidades.

**Dos lotes, un pedido — de dónde sale la nota de despacho.** El reparto crea **dos `ProductionLot` que comparten `sourceOrderIds`**, cada uno produciendo solo una porción de cada pedido. Como la nota de despacho se arma por `sourceOrderId`, hace falta saber cuánto de cada línea es de cada sucursal en cada turno:

- `ProductionLotLine.sourceOrderAllocations` — `orderId → cantidad` de esa línea en ese turno. Se persiste en arreglos paralelos (`soaOrderIds`/`soaBranchNames`/`soaQuantities`), no como lista de objetos: GitLive no serializa `Any`.
- `ProductionLot.isShiftSplit` — el hecho de que el lote vino de un reparto. `createDispatchNotesForLot` lo usa para decidir si despacha el desglose o el pedido completo. **Antes esto era una heurística** (`lines.any { allocations no vacío }`) y fallaba en el peor sentido: un lote repartido cuyas líneas perdieron el desglose caía al camino de lote normal y **cada turno despachaba el pedido entero** — el pan salía duplicado en las dos notas. El `any { ... }` quedó solo como fallback para lotes anteriores al flag.
- **Todo lo que toca las líneas de un lote repartido tiene que mantener el desglose vivo**, o el conteo en despacho sale mal en silencio:
  - `recomputeSplitLotLinesFromSources` lo recalcula con `splitOrderAllocations` al editarse un pedido fuente.
  - `applySplitLotLine` lo escribe también en las líneas que crea (sin eso, un producto agregado después despachaba 0 a todos).
  - `removeOrderFromLot` resta la **porción de ese pedido en ese lote**, no la cantidad pedida completa (restar 200 de una línea de 100 vaciaba la línea y borraba de ese turno el pan de todas las demás sucursales).
- `splitOrderAllocations` (`model/ShiftSplit.kt`, con tests en `ShiftSplitAllocationTest`) preserva el reparto manual de la encargada: una sucursal cuyo pedido **no cambió** queda fijada en la cantidad que ya tenía en el turno A (si tenía un encargo y le dieron 125 de mañana, que otra sucursal edite su pedido no se las mueve). `previousA + previousB` reconstruye lo que pedía antes — por eso ambos lotes hermanos guardan su desglose, no solo el turno A. El turno B siempre se deriva restando, así los dos suman exactamente lo pedido.

### Notificaciones (`AppNotification`)

`companies/{c}/notifications/{id}` es la **fuente de verdad**; el push es solo un canal de entrega. Si la Cloud Function falla o no está desplegada, el aviso sigue en la campana.

- Destinatario por **sucursal + permiso**, no por usuario: quién está de turno cambia a diario, "la gente de la planta X que maneja lotes" no. `isVisibleTo(user)` filtra en cliente.
- Al autor no se le notifica su propia acción.
- Tipos: `ORDER_RECEIVED` (pedido → planta), `ORDER_IN_LOT` (planta tomó el pedido → sucursal), `ORDER_IN_PRODUCTION` (`startLot` → sucursal), `PRODUCT_CANCELLED_MIN_BATCH`.
- `NotificationTarget` es un string simbólico que resuelve `AppNavigation` — evita acoplar `model/` con `navigation/` (mismo criterio que `AlertTarget`).
- Campana con badge en el TopAppBar de HomeScreen (se suscribe ahí porque siempre está en el backstack) + `NotificationsSheet`.
- `notifySafely` en `ProductionViewModel`: un fallo al notificar nunca tumba la operación que lo provocó.
- **Push**: `PushTokenProvider` (interfaz en `domain/`, impl por plataforma). Android = `AndroidPushTokenProvider` (FCM). Web = `JsPushTokenProvider` (SDK modular `firebase/messaging` vía externals + `firebase-messaging-sw.js` en `jsMain/resources/`, que webpack copia a la raíz). iOS = `NoOpPushTokenProvider` hasta tener APNs.
- Tokens en `companies/{c}/deviceTokens/{token}` — el id ES el token, así re-registrar el mismo aparato sobreescribe en vez de duplicar.
- `functions/index.js` (Node 22, 2ª gen, us-central1) envía y borra tokens muertos. Ya desplegada. **Requiere plan Blaze** (activo).
- Android: el canal `bakeos_pedidos` se crea en `BakeryApp.onCreate` — en Android 8+ un push a un canal inexistente se descarta en silencio. El id debe coincidir en tres lugares: `NotificationChannels.kt`, el `default_notification_channel_id` del manifest y `functions/index.js`.
- Tap sobre el push: `MainActivity` rescata `targetScreen`/`targetId` de los extras del intent y los deja en `PendingDeepLink`; `AppNavigation` los consume cuando ya hay sesión Y terminó la navegación inicial (si no, el `popUpTo(0)` del arranque se lleva la pantalla por delante).
- Web: `WEB_PUSH_VAPID_KEY` en `FirebaseWeb.kt` (vacía = push web apagado, sin romper nada). Requiere HTTPS. El clic no aterriza en el pedido — la web no tiene rutas en la URL.

### Activos Fijos
Inventario valorado por sucursal: maquinaria, mobiliario, vehículos, herramientas. Permisos: `VIEW_ASSETS`, `EDIT_ASSETS`, `MANAGE_ASSETS`, `ADD_ASSET_HISTORY`.

- `FixedAsset` plano por sucursal con identificación, adquisición, status (`AssetStatus`), depreciación lineal opcional (`usefulLifeMonths`, `residualValue`), mantenimiento preventivo (`nextMaintenanceDate`, `maintenanceIntervalDays`), contadores denormalizados.
- `AssetHistoryEntry` en subcolección. `AssetEventType`: PURCHASE/MAINTENANCE/REPAIR/UPGRADE/INSPECTION/TRANSFER/DISPOSAL/SALE/NOTE.
- Crear activo → inserta PURCHASE auto en historial.
- `saveHistoryEntry` side-effects: TRANSFER actualiza `branchId`; DISPOSAL→`status=DISPOSED`; SALE→`SOLD`; MAINTENANCE con `nextMaintenanceDate` actualiza el campo del activo.
- `recalcAndPersistCounters` después de cada add/edit/delete de evento.
- `deleteAsset` borrado lógico (conserva historial).
- `util/DepreciationCalculator.kt::calculateBookValue(...)` con `kotlinx.datetime`.
- Lista con KPIs (total / inversión / valor en libros / mantenimientos 30 d), filtros sucursal+categoría+búsqueda, badge naranja si `nextMaintenanceDate ≤ +7 días`.
- `AssetImageUploader` default `NoOp` en appModule; overrides `AndroidAssetImageUploader` y `JsAssetImageUploader`.

### Listas de Precios — cascada
`PriceList` con `type` (RETAIL|WHOLESALE|CUSTOM) y `isDefault` por tipo. `Branch.defaultPriceListId` opcional; si no, default del tipo WHOLESALE.

**Cascada `resolveWholesalePrice`**:
1. `ProductPrice(productId, listId)` con `unitPrice>0` → override.
2. `priceFactor` (0..1+, ej. 0.80) × `product.salePrice` → calculado.
3. Nada → `unitPrice=0`, `source=NONE`. **Bloquea cierre del despacho.**

`ResolvedPrice.source` (PRODUCT_OVERRIDE|LIST_FACTOR|NONE) se serializa como `DispatchNoteLine.priceSource` (snapshot inmutable — auditoría).

**Seed**: primer ingreso a `PriceListAdminScreen` crea "Cliente final" (RETAIL default) y "Sucursales" (WHOLESALE default) vía `ensureSeedLists(companyId)`.

**Sync salePrice ↔ lista RETAIL default**: en tab "Precios" del form de producto, fila de RETAIL default es readonly y refleja el `salePrice` de tab General. `salePrice` es única fuente de verdad para retail; se persiste también como `ProductPrice` en RETAIL default.

**Override vacío**: pasa el listId a `priceListIdsToReset` y el VM borra el doc `ProductPrice` — vuelve a aplicar factor o "sin precio".

Permisos: `MANAGE_PRICE_LISTS`, `VIEW_DISPATCH_VALUE` para ver montos.

### Despacho (`DispatchNote`) — flujo post-producción

Al `completeLot` se crean **`DispatchNote` DRAFT por cada `sourceOrderId`** (`createDispatchNotesForLot`, idempotente).

**Ciclo:**
```
DRAFT → READY (empaque cerró) → IN_TRANSIT (salió a reparto)
       → ARRIVED (acuse de llegada) → DELIVERED / DELIVERED_WITH_DISCREPANCY
       o CANCELLED (solo DRAFT/READY)
```

**Recepción en 2 pasos** (refleja flujo real):
1. **`acknowledgeArrival`**: IN_TRANSIT→ARRIVED. La encargada verifica TIPOS de pan; libera al repartidor. Sin stock todavía.
2. **`confirmReception`**: ARRIVED→DELIVERED. La encargada cuenta con calma, ajusta cantidades y razones (FALTANTE/DAÑADO/SOBRANTE). Dispara `applyMovement` RECEPCION a `FinishedStockRepository`.

Acepta `confirmReception` también desde IN_TRANSIT (flujo rápido).

**Cerrar el conteo se acusa con diálogo, y ése es el que navega.** Al cerrarlo la nota pasa
a DELIVERED y `DispatchNoteReceiveScreen` vuelve a dibujar **el mismo formulario de conteo**
(su rama `else` cubre todo lo que no es IN_TRANSIT), así que quien contó no tenía señal de
que el ingreso hubiera quedado registrado — el único aviso era `lastReceptionSummary`, que
ninguna pantalla consumía. Ahora `DispatchUiState.receptionResult` lleva unidades, productos
y discrepancias hasta un `AlertDialog` sin descarte por fuera, y su botón es el que hace
`onNavigateBack()`. El acuse de llegada (paso 1) sigue saliendo solo con `isSaved`: ahí no
hay nada que reportar más allá de "el repartidor se puede ir", y por eso la condición es
`isSaved && receptionResult == null`.

**Pre-llenado**:
- Empaque: `packedQuantity = requestedQuantity`. Si difiere → `packedReason` obligatorio.
- Recepción: `receivedQuantity = packedQuantity`. Si difiere → `discrepancyType` + `receivedReason`.

**Bloqueos**:
- DRAFT→READY bloqueado si alguna línea tiene `priceSource=NONE`. Los pedidos especiales llevan `SPECIAL_ORDER` y quedan exentos (ver "Pedidos especiales").
- `cancelNote` bloqueado en IN_TRANSIT o terminales; `restoreNote` solo desde CANCELLED.
- Discrepancia NO devuelve stock a planta (queda como pérdida en tránsito en Kardex).

**Cancelar una nota no la borra, y se puede deshacer.**

Cancelar era un botón a un toque sin confirmación, y la nota cancelada no aparecía en
ninguna de las cinco pestañas: se leía como borrada, sin forma de ver qué se canceló ni de
volver atrás. Y el pan cancelado —que ya estaba horneado— desaparecía del sistema entero.

- **`cancelNote`** guarda `statusBeforeCancel`, `cancelledAt/By`, `cancelReason` (opcional) y
  `plantAbsorbedAtCancel`, y **suma `packedQuantity` al `FinishedProductStock` de la planta**
  como `AJUSTE`: ese pan existe y se queda físicamente ahí. No hay doble conteo con
  `absorbLeftoverIntoPlant` — aquél ingresa lo **no** empacado (`requested − packed`) y éste lo
  empacado. Tampoco con el cierre de empaque: `observePackingViewForBranchDateShift` y
  `closePackingForBranchDateShift` filtran `status == DRAFT`, así que la cancelada ya salió.
- **`restoreNote`** devuelve la nota a `statusBeforeCancel` (READY → "Listas", DRAFT → la matriz
  de empaque) y **descuenta de la planta** lo que la cancelación le sumó. Notas canceladas antes
  de esto no traen el campo: vuelven a DRAFT y no mueven inventario, porque tampoco lo movieron
  al cancelarse — para eso existe `plantAbsorbedAtCancel` como hecho guardado y no como algo
  deducido del estado.
- **Un solo helper para los dos sentidos** (`applyPlantAbsorption`, con `sign` ±1): un reverso
  escrito aparte termina usando otra cantidad y deja pan fantasma en la planta. Agrupa por
  `productId` porque una nota puede repetirlo (especiales consolidados) y `applyMovement`
  lee-modifica-escribe el stock — dos llamadas seguidas sobre el mismo doc se pisan.
- **El encabezado se escribe ANTES de mover stock**, igual que `receivePurchase`: si el
  movimiento falla a media aplicación, un reintento choca con la guarda de estado en vez de
  sumar el pan dos veces.
- **La pestaña "Canceladas" solo aparece cuando el día tiene alguna** — mismo criterio que
  Facturas/Compras rápidas en Compras. Con ella el `TabRow` fijo pasa a `ScrollableTabRow`: seis
  pestañas repartidas a lo fijo dejan "Pendiente conteo" ilegible en teléfono.
- El botón de restaurar vive en el **detalle** y no en la lista: mueve inventario, y quien lo
  toca tiene que estar viendo qué pan y cuánto. Los dos diálogos dicen el número de unidades y
  a qué sucursal le pega antes de confirmar.

**Self-dispatch** (`markAsLocalDispatch`): si `originBranchId==destinationBranchId` y READY, botón único **"Marcar como recibida en mostrador"** salta a DELIVERED sin reparto. Suma a `FinishedProductStock` de la planta.

**Permiso `VIEW_DISPATCH_VALUE`**: oculta `unitWholesalePrice` y `totalValueWholesale` en todas las pantallas (solo OWNER por defecto).

**Scroll en las pantallas de lista larga**: la lista de productos va en un `LazyColumn` con `Modifier.weight(1f)` dentro de la Column de la pantalla, con encabezado y botón de acción fijos arriba y abajo. Puesta en una Column plana, una nota con muchos productos empuja las últimas filas y el botón fuera de pantalla sin scroll que las alcance — le pasó al checklist de llegada de `DispatchNoteReceiveScreen`, que dejaba a la encargada sin poder firmar el acuse.

**Pantallas**: `DispatchNoteListScreen` (tabs por status, filtro mes, listener vivo), `DispatchNotePackScreen` (input por producto, razón si difiere, bloquea si precio NONE), `DispatchNoteReceiveScreen` (UI alterna por status), `DispatchNoteDetailScreen` (read-only con timeline).

**Agrupado por masa en empaque/recepción/detalle**: `DispatchNoteLine` no trae `recipeId` (viene de `ProductionOrderLine`, que tampoco lo tiene), así que en vez de snapshotearlo se resuelve en cliente reutilizando `ProductionUiState.recipeRefByProductId` (`ProductionViewModel.loadRecipeRefByProductIdIfNeeded`, ya cacheado) + `groupByMasa` de `presentation/production/MasaGrouping.kt` — ambos son `internal`, visibles igual desde `presentation/dispatch` porque `internal` en Kotlin es de módulo, no de paquete. `DispatchNotePackScreen`, las dos secciones de `DispatchNoteReceiveScreen` (`ArrivalChecklistSection`, `CountSection`), `DispatchNoteDetailScreen` (`LinesSection`) **y `PackingMatrixScreen.kt`** (`PackingMatrixTable` para tablet, `PackingBranchTabs` para móvil — la pestaña "Por empacar", que es la que se abre por defecto al entrar a Despacho, así que es la que más importa) reciben `productionViewModel` como parámetro nuevo solo para esto — mismo patrón que ya usaba `ProductWeightsScreen`. **Ojo**: "Despacho" tiene 4 pantallas con lista de productos, no 3 — la matriz `PackingMatrixContent` (pestaña "Por empacar" por defecto) es la que más se usa y es fácil pasarla por alto porque vive en un archivo separado (`PackingMatrixScreen.kt`) y no en `DispatchNote*Screen.kt`.

### Reparto (`DeliveryRun`)
Agrupa N `DispatchNote` para repartidor + vehículo + día.

**Status**: PENDING→IN_PROGRESS→COMPLETED/CANCELLED.

- **`saveRun`**: calcula diff `previousNoteIds` vs `newNoteIds`; añadidas → `dispatchRepository.markInTransit(noteId, runId)`; quitadas → `unassignFromRun`. Centraliza la transición.
- **Auto-cierre** (`tryAutoCompleteRun`): cuando todas las paradas están en "driver done" (ARRIVED|DELIVERED|DELIVERED_WITH_DISCREPANCY|CANCELLED), pasa a COMPLETED. Disparado desde `confirmReception` Y `acknowledgeArrival`.
- **Cancel bloqueado** si alguna parada está ARRIVED en adelante.
- **Vehículo = `FixedAsset`** con categoría que contenga "Vehículo"/"Vehiculo"/"Transporte", `active=true`, `status=ACTIVE`. Si solo hay uno, auto-preselección. Guarda `vehicleAssetId` + `vehicleInfo` denormalizado.
- Repartidor (`DRIVE_DELIVERY`) ve solo sus runs; supervisor (`MANAGE_DELIVERY_RUNS`) ve todas las de sus sucursales accesibles.

### Inventario de Tienda (`FinishedProductStock` + `FinishedStockMovement`)

Plano `companies/{c}/finishedStocks/{branchId}_{productId}` con `currentStock` + `avgUnitCost` (promedio ponderado).

**Recalculo `avgUnitCost` en `applyMovement`**:
- Ingreso (qty>0): `avg = (stockPrev × avgPrev + qty × unitPrice) / (stockPrev + qty)`.
- Salida (qty<0): stock baja, avg se mantiene.

**Tipos**: RECEPCION (auto), VENTA, MERMA, AJUSTE, TRANSFERENCIA.

**La lista no sale solo de los documentos de stock** (`model/StoreInventory.kt::buildStoreInventoryRows`,
tests en `StoreInventoryTest`). Un `FinishedProductStock` nace con el primer movimiento, así que
un producto de reventa recién dado de alta no aparecía en ninguna sucursal —ni en 0— y no había
cómo saber si la factura había ingresado. Ahora cada producto de reventa activo sale en cada
sucursal (0 = "sin movimientos todavía"), con filtro Pan propio / Reventa. El producto
respaldado por insumo (huevo por docena) muestra el `BranchStock` del insumo ÷
`ingredientUnitsPerSale` y **no ofrece Ajustar**: un ajuste acá escribiría un terminado paralelo
que la venta no descuenta. `FinishedStockUiState.stocks` lleva además registros sintéticos en 0
para la reventa, porque el formulario de ajuste elige producto de esa lista (conteo inicial de
lo que ya estaba en el estante). `functions/tools/inspect-resale-stock.js` es un diagnóstico de
solo lectura de compras de reventa vs. `finishedStocks`.

Form `FinishedStockAdjustScreen` con 4 opciones (Merma, Ajuste menor/mayor, Venta directa), valida no negativo, razón obligatoria, preview "Nuevo stock: X".

KPIs ocultan valor de inventario sin `VIEW_DISPATCH_VALUE`. Permiso: `MANAGE_FINISHED_STOCK`.

### POS: venta por grupo de precio ("Pan de Q0.50")

La mayor parte del pan cuesta Q0.50 (francés, sheca, pirujo…). Con cola en el mostrador, que la
cajera elija el tipo de cada pan es lento **y además da un dato malo**: con prisa toca lo primero
que ve. Una `POSCategory` con `sellAsGroup = true` se cobra como un solo botón: se teclea cuántos
panes, no cuáles, a `groupUnitPrice`. `groupLabel` es el nombre en ticket/carrito/reportes
(`groupDisplayName`) porque el `name` es el del chip y puede ser corto ("0.50").

**La venta NO descuenta ningún producto; lo reparte el conteo de cierre (`BreadCount`).** La
línea va con `productId = null` + `quickGroupId` (campos que ya existían del diseño viejo) y el
encabezado de la venta denormaliza `groupIds`/`groupUnits` en arreglos paralelos, para que el
conteo lea solo encabezados. Al cerrar, `allocateGroupSales` (`model/GroupSaleAllocation.kt`,
tests en `GroupSaleAllocationTest`) reparte lo vendido de cada grupo **en proporción al faltante**
de cada pan (mayor residuo, nunca pasa el faltante), escribe un `VENTA` por producto y solo el
resto como `MERMA`. El reparto por tipo sale del conteo físico, que es un hecho, en vez de lo que
tocó la cajera — por eso es más exacto que obligar a elegir.

- **Depende de que la tienda cuente todos los días.** Confirmado con el dueño. La ventana de ventas
  va del `closedAt` del conteo cerrado anterior de la sucursal hasta el cierre de éste
  (`groupSalesWindow`): un día sin contar se acumula al siguiente, no se pierde.
- **Antes de esto, todo faltante del conteo era merma** (`difference < 0`). Ahora la merma real es
  `BreadCountLine.effectiveWaste` (`wasteQuantity`, null en conteos viejos → cae al faltante
  entero), y `aggregateMermasForMonth` y la razón obligatoria del formulario usan eso: la
  encargada no tiene que justificar 150 francés que se vendieron.
- **Vendido de más que faltante** (`unexplainedGroupUnits`): entró pan sin registrar o el conteo
  está mal. Se avisa y no se inventa movimiento.
- VENTA y MERMA del mismo pan van **en serie**: `applyMovement` lee-modifica-escribe.
- **Solo entran productos cuyo `salePrice` = `groupUnitPrice`**: el precio del grupo nunca
  contradice el catálogo.
- Los panes de un grupo **no salen sueltos en "Todos"** del POS (invitaría a buscar el tipo, que es
  justo lo que se evita), y el POS arranca con el grupo seleccionado. En el resumen diario de
  ventas entran con clave `groupSaleKey` (`grp_<id>`) para que el reporte no los pierda.

**"Todos" va ordenado por lo más vendido** (unidades de los últimos 30 días de `salesDaily`,
en caché por sucursal y día en `POSViewModel`) y cada grupo de precio sale ahí como una ficha
más (`GroupTile`), que pregunta cuántos.

### POS: un cobro, varias ventas (cuenta del restaurante en el carrito) + "Último cobro"

Una cuenta de ComandaYa pendiente se puede **cobrar ahora** (lo de siempre: el dinero entra a
esta gaveta) o **agregar al carrito** para cobrarla junto con el pan: un solo pago y un solo
vuelto. Al confirmar, primero se recibe la cuenta (`collectPendingSale` con `CheckoutPayment`)
—si otra caja ya la recibió, se corta antes de mover inventario— y después se crea la venta de
panadería. Todas las ventas del cobro llevan el mismo `checkoutId`, `checkoutTotal`,
`tenderedAmount`, `changeAmount` y billetes, porque lo que se reclama es el cobro.

- Cobrada desde el carrito, la cuenta toma el **método del cobro** (se vacía el
  `paymentBreakdown` de ComandaYa): el cliente pagó en esta caja.
- Solo se ofrece sumar al carrito una cuenta **toda en efectivo**: si en el restaurante ya se
  pasó tarjeta, cobrarla otra vez le cobraría dos veces al cliente.
- Si otra caja recibe una cuenta que está en este carrito, el listener la saca y avisa.

**"Último cobro"** (ícono de reloj en la barra del POS): cuánto se cobró, cuánto dieron,
cuánto vuelto y **con qué billetes**, con flechas a los 10 anteriores. Los billetes salen de los
chips que la cajera toca en `PaymentDialog` (`billValues`/`billCounts`); si teclea el monto van
vacíos, a propósito: no se inventa con qué pagaron. Los cobros de este aparato viven en memoria;
si la app se reinició se rearman de las ventas de la sesión (`checkoutsFromSales`, test en
`CheckoutRecordTest`), sin el detalle de productos.

**Un producto está en una sola categoría** entre las que comparten sucursal
(`productCategoryConflicts`; vacío = todas). El formulario esconde los ya usados, con buscador y
un "Mover aquí" explícito que lo quita de la otra al guardar. `saveCategory` revalida contra lo
guardado: un producto en dos grupos dejaría sin saber a cuál repartirle la venta.

### Créditos — cuentas por cobrar y adelantos (reemplaza el cuaderno)

`CreditAccount` (`companies/{c}/creditAccounts`) es QUIÉN debe: persona, institución (el
colegio) o colaborador. La deuda es de la cuenta; una institución tiene **personas
autorizadas** (arreglos paralelos en el doc) y cada cargo anota cuál se llevó la mercadería.
Solo el administrador agrega personas: si llega alguien que no está en la lista no se le fía.
**Sin límite de crédito** (decisión del dueño).

`CreditMovement` (`companies/{c}/creditMovements`, plana con `accountId`) es el estado de
cuenta. `amount` con signo: + deuda (SALE, ADVANCE, ADJUSTMENT positivo), − pago (PAYMENT,
PAYROLL_DEDUCTION, ADJUSTMENT negativo). `CreditAccount.balance` es **caché**: todo lo que
escribe o borra un movimiento lo recalcula sumando la colección (`recalculateBalance`),
mismo criterio que `paidAmount` de compras. Nada se edita: los errores se corrigen con un
ajuste, así el estado de cuenta entregado sigue diciendo lo mismo. El saldo que traían del
cuaderno se carga como ajuste "Saldo anterior (cuaderno)".

- **Colaboradores no se capturan**: `CreditViewModel.load` crea la cuenta de cada colaborador
  activo con id fijo `emp_<employeeId>` (`ensureEmployeeAccount`).
- **Vender fiado** (`SalePaymentMethod.CREDIT`, permiso `SELL_ON_CREDIT`): el POS pide cuenta y,
  si es institución, quién se lo lleva. La venta mueve inventario igual; en la sesión suma a
  `totalCreditSales` y NO a `expectedCashNow`. El cargo se escribe después de la venta con
  `saleId`. No se ofrece con una cuenta del restaurante en el carrito.
- **Cobrar crédito en caja** (`COLLECT_CREDIT_PAYMENT`): abono PAYMENT + si es efectivo un
  `CashMovement` DEPOSIT ligado por `sourceCreditMovementId`; queda en "Último cobro".
- **Adelanto desde la gaveta** (`GIVE_EMPLOYEE_ADVANCE`): ADVANCE en la cuenta del colaborador +
  WITHDRAWAL de la caja, validado contra el efectivo recién leído. Los movimientos de caja con
  `sourceCreditMovementId` no se corrigen desde "Movimientos de caja" (`canAct`): se corrigen en
  la cuenta. Los adelantos ya NO se anotan en el historial del colaborador (la opción se quitó;
  los viejos se siguen viendo).
- **Planilla**: el campo "Créditos y adelantos" (`PayrollRecord.advances`) se propone con el
  saldo de la cuenta en planillas nuevas. Al quedar **PAID** escribe un PAYROLL_DEDUCTION con id
  fijo `payroll_<payrollId>` (`syncPayrollCreditDeduction`): corregirla reescribe el mismo
  renglón, volverla a borrador o borrarla lo quita. `savePayroll` ahora devuelve el registro.
- Administración (`MANAGE_CREDIT_ACCOUNTS`, módulo "Créditos" en Ventas): cuentas, pagos por
  transferencia, ajustes y adelantos de caja general. La ficha del colaborador tiene la pestaña
  "Créditos" con el mismo `CreditAccountPanel`.
- **Personas autorizadas**: se editan (mismo id) y se eliminan. Eliminarlas es seguro: cada cargo
  guarda `personName` como snapshot.
- **Antigüedad (FIFO)**: `openCharges` aplica cada pago / ajuste negativo al cargo MÁS VIEJO
  (un pago de más queda a favor y se come los cargos siguientes). `recalculateBalance` guarda los
  cargos abiertos en la cuenta (`openChargeDates/Amounts`), así el reporte
  (`CreditAgingReportScreen`, tramos 0–30/31–60/61–90/+90 vía `creditAgingOf`) y el aviso del
  Inicio (`AlertTarget.CREDIT_OVERDUE`, CRITICAL con saldo de +90) leen una sola colección.
  Cuentas con saldo anteriores al cálculo se rehacen una vez al cargar (`recalculate`).
- **Impresión**: `util/CreditDocuments.kt` arma estado de cuenta y reporte (HTML hoja + ESC/POS
  58/80) desde las mismas funciones que la pantalla. `DocumentPrintButton` (en
  `ProductionOrderPrintButton.kt`) es el botón genérico de imprimir; la orden de producción lo usa.
- **Descuento de colaborador al pagar en efectivo**: el % es **del producto**
  (`Product.employeeDiscountPercent`, pestaña Precios — mayormente empacados y reventa) y la
  persona solo tiene **derecho sí/no** (`CreditAccount.cashDiscountEligible`, en su cuenta; una
  versión anterior guardaba `cashDiscountPercent` por persona y se lee como "con derecho"). En el
  cobro con Efectivo aparece "¿Es un colaborador? Descuento de Q X" solo si algún producto del
  carrito lleva %. `POSViewModel.employeeDiscountAmount` → `employeeCashDiscount` suma línea por
  línea (medio centavo sube); el pan de grupo y el restaurante no descuentan. Va a
  `Sale.discount` + `discountAccountId/Label`. Lo fiado, con tarjeta o transferencia: sin descuento.

### Facturación electrónica (FEL Guatemala, Digifact) — en construcción

Réplica en BakeOS de lo que ComandaYa ya factura (mismo certificador, mismo NIT, mismos
códigos de establecimiento). Sucursales con el NIT 59325216: **Bombero (1), Pacaja (2),
Democracia (3)**; Calvario y Salida tienen otro NIT (después). Decisiones: facturar es
opcional con switch "por defecto" por sucursal; si Digifact falla se cobra igual y la factura
queda PENDIENTE con reintento automático; la cuenta del restaurante agregada al carrito se
factura JUNTO con el pan en un solo DTE y BakeOS le avisa a ComandaYa (que no la vuelva a
facturar y descuente el crédito del paquete compartido de DTEs).

**Fase 1 (hecha, sin desplegar): servidor** — `functions/fel/`:
- `digifactClient.js`: portado de ComandaYa (`OrderApp/functions/fel`), con `fetch` nativo y
  firebase-admin modular. Login con NIT a 12 dígitos, certificar NUC, anular (CANCEL FEL, no
  NCRE: la NCRE no acepta CF), GetDocument, consulta de NIT. `serieYNumero` lee del XML
  certificado porque Digifact devuelve `serial`/`batch` al revés.
- `felCredenciales.js`: credenciales POR NIT en la colección de nivel superior
  `FelCredentials/{companyId}__{profileId}` — las reglas no la abren (la de `companies/**`
  deja leer todo a cualquier empleado). El token se cachea ahí mismo.
- `felEmisor.js`: `companies/{c}/felProfiles/{pid}` (datos SAT, sin secretos) + en la sucursal
  `felProfileId` y `felCodigoEstablecimiento`.
- `nucXmlBuilder.js`: XML NUC desde líneas genéricas **con descuento por línea**
  (`<Discounts>`; ComandaYa no los mandaba). Total = Qty×Price−Descuento, IVA sobre eso, todo al
  centavo y totales sumados de los items. `prorateDiscount` reparte el descuento del encabezado
  de la venta. Referencia interna `BKOS-…` (no choca con ComandaYa) en modo VALIDAR. Validado
  contra `NUCSchema.xsd` oficial; tests en `nucXmlBuilder.test.js` (`npm test`).
- `felFunctions.js`: `felCertificarVenta` (varias ventas de un cobro → un DTE;
  `dte_<huella>` determinista + candado en transacción; duplicado 9022 → recupera el DTE),
  `felConsultarNit`, `felAnularDte`, `felObtenerPdf`, `felGuardarPerfil` (solo OWNER; prueba el
  login antes de guardar), `felReintentarPendientes` (cada 5 min, cola `felPendientes`,
  espera 2^n min, tope 12). Estados: CERTIFICANDO / CERTIFICADO / PENDIENTE (transitorio) /
  RECHAZADO (contenido o configuración) / ANULADO. La venta lleva `dteId` + `invoiceStatus` +
  serie/número.
- `tools/fel-probar.js`: prueba de punta a punta contra Digifact con credenciales por env;
  `fel-probar-sabropan.js` la corre con los datos de emisor de Sabro Pan y `fel-login.js` solo
  prueba el login. **Verificado en PRODUCCIÓN el 2026-09-25**: certificó una FPEQ de Q1.90 y la
  anuló. No hay ambiente de pruebas para este NIT: certificar es una factura real. En
  PowerShell la contraseña va con `$env:DIGIFACT_PASSWORD = '...'` (comillas simples por el `$`).

**Pendiente**: Fase 2 (perfiles y sucursales en la app), Fase 3 (cobro, ticket con QR,
anulación), Fase 4 (puente con ComandaYa y crédito compartido; y arreglar que
`ingestExternalSale` hace `set` sin merge y reabre una venta ya recibida en caja).

### Empleados
CRUD perfil + documentos + historial + nómina. Modo kiosko (reloj de turno biométrico en tablet). Historial de asistencia con mes/año navegable.

---

## Inyección de Dependencias (Koin)

```kotlin
// nonWasmMain/di/AppModule.kt
val appModule = module {
    single { Firebase.firestore }
    single<AuthRepository> { FirebaseAuthRepository(get(), get()) }
    single<IngredientRepository> { FirebaseIngredientRepository(get()) }
    single<RecipeImageUploader> { NoOpRecipeImageUploader() }  // override por plataforma
    single<PriceListRepository> { FirebasePriceListRepository(get()) }
    single<FinishedStockRepository> { FirebaseFinishedStockRepository(get()) }
    single<DispatchRepository> { FirebaseDispatchRepository(firestore, productionRepo, priceListRepo, finishedStockRepo) }
    single<DeliveryRepository> { FirebaseDeliveryRepository(firestore, dispatchRepo) }
    viewModel { RecipeViewModel(get(), get()) }
    viewModel { ProductionViewModel(get(), get(), get(), get(), get(), get(), get()) } // +PreparationRepo, +PreparationBatchRepo, +DispatchRepo
    viewModel { DispatchViewModel(dispatchRepo, deliveryRepo) }
    viewModel { DeliveryViewModel(deliveryRepo, dispatchRepo, authRepo, fixedAssetRepo) }
    viewModel { FinishedStockViewModel(finishedStockRepo, branchRepo) }
    ...
}

// androidModule — repos/uploaders que dependen de Context Android
single<ProductRepository> { FirebaseProductRepository(get(), get()) }
single<RecipeImageUploader> { AndroidRecipeImageUploader(get()) }  // override
```

`ProductRepository` está en `androidModule` (necesita Storage). Mismo patrón para `RecipeImageUploader`/`AssetImageUploader`: interfaz en `domain/`, NoOp en appModule, override Android/JS.

**Decisión**: `DispatchRepository` depende de `PriceList/Production/FinishedStock`. Dependencia inversa (auto-cierre Run al confirmar recepción) se resuelve a nivel **ViewModel** — `DispatchViewModel.confirmReception` llama `deliveryRepository.tryAutoCompleteRun`. Evita ciclo en datos.

**ViewModels compartidos** se instancian en `AppNavigation` (vía `createAppViewModels()` expect/actual) — evita recargas al navegar atrás.

---

## Navegación

```kotlin
sealed class Screen(val route: String) {
    // Catálogo: Product, Recipe, Ingredient, Supplier (List/Detail/Form), PriceListAdmin
    // Inventario: Purchase, Dispatch, Delivery, FinishedStock (variantes)
    // Producción: ProductionOrder/Lot (List/Form/Detail/Complete), Shift, Preparation, PreparationBatch
    // Administración: Branch, Expense, FixedBudget, User, Employee (variantes), Asset, AssetHistoryEntryForm
}
```

- Forms reciben `id` nullable como query param (null = alta).
- `RequirePermission(user, *permisos, onBack)` envuelve rutas restringidas (OR).

---

## Gestión de Estado

### UiState

`data class XxxUiState` con: `xxxList`, `selectedXxx`, `isLoading`, `isDetailLoading` (evita spinner de lista al abrir detalle), `error`, `isSaved`, `isDeleted`.

### Forms: remember vs rememberSaveable

```kotlin
// Primitivas → rememberSaveable (sobrevive rotación)
var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }

// Listas/objetos complejos → remember (no Parcelable)
var stages by remember { mutableStateOf(existing?.stages?.map { ... } ?: emptyList()) }
```

### Form de edición: patrón de dos composables

```kotlin
fun RecipeFormScreen(...) {
    if (needsLoad && state.isDetailLoading) { CircularProgressIndicator(); return }
    RecipeFormContent(existing = selectedRecipe, ...)
}
fun RecipeFormContent(existing: Recipe?, ...) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
}
```

---

## Responsive Design

`AdaptiveContainer` (en `ui/AdaptiveLayout.kt`) centra y limita a 800dp en pantallas grandes. Aplicar en TODOS los forms y pantallas nuevas/editadas.

---

## Paleta

`ui/theme/AppTheme.kt::BrandColors` es la **única** fuente de verdad. Ninguna pantalla vuelve a
declarar un `Color(0xFF...)` de marca: había 464 literales repartidos en 70 archivos y el mismo
estado salía de dos colores distintos en dos listas.

| Token | Hex | Para qué |
|---|---|---|
| `Primary` / `PrimaryDark` / `PrimaryContainer` | `#005B8D` / `#00486F` / `#DCEEFB` | Helvetia Blue — estructura, TopAppBar, FABs, identidad |
| `Accent` | `#FE7F03` | Naranja de marca, muestreado del croissant del logo. **Siempre relleno**, nunca texto |
| `OnAccent` | `#00344F` | Azul marino — la tinta que va **sobre** `Accent` (5.1:1) |
| `AccentDeep` | `#A85200` | Naranja oscuro: relleno con letra **blanca** (5.4:1), y el único naranja admisible como texto/icono sobre blanco (5.4:1) |
| `AccentSoft` | `#FFF3E0` | Relleno pálido de chip; lleva `Primary` encima (6.7:1) |
| `Ivory` / `Sand` | `#FDFBF7` / `#F5EFE6` | Fondo cálido y cards beige |
| `TextSlate` / `MutedSlate` / `Outline` | `#1A2B38` / `#5E6E7A` / `#C8A882` | Texto y bordes |

### Dos reglas que no se negocian

**1. El naranja claro nunca es texto.** `Accent` (`#FE7F03`) sobre blanco da **2.6:1**; el mínimo
legible es 4.5:1. El acento dorado anterior (`#D9A441`) se usaba justo así en decenas de sitios y
en una tablet enharinada bajo la luz de una panadería ese texto simplemente no está. Si el naranja
tiene que decir algo con letras, se rellena (`Accent` + `OnAccent`, o `AccentDeep` + blanco).
`AccentDeep` sí puede ser tinta sobre blanco — es el naranja oscurecido a propósito para eso.

**2. El naranja significa estado, no decoración.** Azul = estructura e identidad; naranja =
"esto necesita tus ojos / está en curso" (pendiente, por recibir, vencido, precio faltante, stock
bajo, mantenimiento próximo, sobreventa); verde = hecho; rojo = roto. Un FAB, un switch o un chip
de sucursal no son alertas y por eso no van en naranja — si todo fuera naranja, el naranja
dejaría de avisar.

### Chips: `ui/theme/BrandChip.kt`

`BrandChip(text, tone, emphasis)` es el chip de estado de toda la app. Antes había ~20
`StatusChip`/`Badge`/`Pill` privados, cada uno con su `Surface` y sus literales — la razón
estructural de que el color quedara disparejo. Se declara **qué significa** (`ChipTone`:
NEUTRAL / INFO / PROGRESS / ATTENTION / SUCCESS / DANGER / BRAND) y la paleta decide cómo se ve.

`chipColors(tone, emphasis)` es lógica pura y `BrandChipContrastTest` verifica que **ningún par
baje de 4.5:1**. No se relaja a 3:1 (el umbral de "texto grande") a propósito: los chips van en
`labelSmall` (11sp), que no califica por más negrita que lleve.

Ya migrados: `effectiveOrderDisplay` + `StatusChip` de pedidos (lista y detalle), despacho,
reparto, `PurchaseStatusPill` y el estado de pedidos de cliente. Los badges que quedan con
implementación propia (activos, empleados, productos, recetas, insumos, preparaciones) usan el
patrón "color al 15% de fondo + color pleno de texto", que es legible; migrarlos es limpieza
pendiente, no un bug.

### Color por sección en el lanzador del Home

`ModuleSection` lleva `ink` + `container` y **cada `ModuleItem` los hereda** — el color vive en la
sección, no en el módulo (antes cada una de las 26 fichas repetía su hex y los cuatro valores eran
variantes de azul, así que la rejilla se leía como un bloque uniforme y había que leer cada
etiqueta para encontrar "Compras").

| Sección | Tinta / contenedor | Por qué |
|---|---|---|
| Operaciones | `AccentDeep` / `AccentSoft` | Es donde pasa el día |
| Inventario | `Primary` / `PrimaryContainer` | El azul de estructura |
| Ventas | `#0D7A8C` / `#D4EFF3` | El teal que la app ya usa en categorías del POS |
| Administración | `MutedSlate` / `Sand` | Se toca una vez al mes; que se apague es correcto |

**Cuatro tonos y no uno por módulo (estilo Odoo).** Odoo puede permitirse 15 hues porque sus
módulos se venden por separado y no tienen relación entre sí; acá recetas → producción → despacho
→ reparto son **una sola cadena**, y pintarlos de colores distintos afirmaría lo contrario. Por
debajo de cuatro hues sigue leyéndose como herramienta; de ocho en adelante es app de consumo.

**El color por sección vive SOLO en este lanzador.** Dentro de las pantallas el icono sigue
diciendo estado (naranja = necesita tus ojos). Si los iconos se colorearan decorativamente esa
señal muere — es el mismo error que el banner de CRITICAL en dorado.

HomeScreen: saludo dinámico, gráfica de ventas (mock), carrusel de accesos, grid de módulos por sección (Operaciones / Inventario / Ventas / Administración).

---

## Compatibilidad KMP/JS (CRÍTICO)

NUNCA usar estas APIs en `commonMain` o `nonWasmMain`:

| ❌ Prohibido | ✅ Reemplazo |
|---|---|
| `System.currentTimeMillis()` | `Clock.System.now().toEpochMilliseconds()` |
| `java.util.Calendar` | `kotlinx.datetime.Instant`, `LocalDateTime`, `TimeZone` |
| `"%.2f".format(v)` / `String.format(...)` | `v.fmt(2)` de `com.bakery.erp.util.FormatUtils` |
| `toSortedMap()` | `.entries.sortedBy { it.key }.associate { ... }` |
| `LocalConfiguration.current` | `LocalWindowInfo.current` + `LocalDensity.current` |
| `NavBackStackEntry.arguments?.getString(key)` | `savedStateHandle.get<String>(key)` |

```bash
./gradlew composeApp:compileKotlinJs           # verificar compile JS
./gradlew composeApp:jsBrowserDevelopmentRun   # correr en localhost:8080
```

---

## Notas y Decisiones de Diseño (el "porqué")

### Masa Concha NO va dentro de la receta de Gusanito
La Masa Concha se prepara por separado (otro horario) y sirve para varios panes. La asociación "este pan lleva concha" pertenece al **producto terminado** (`Product.decorations`), no a la receta de masa.

### Yields no tienen cantidad ni unidad
La cantidad varía día a día según tamaño que decide el panadero. El yield solo dice *qué tipos* pueden salir de esa masa.

### `stageCount` / `yieldCount` en doc raíz de Recipe
Contadores denormalizados para mostrar badges en lista sin cargar subcolecciones. Actualizados al guardar.

### `components` a nivel de receta y no bajo stage
Firestore no recomienda >2 niveles de subcolección. `stageId` resuelve pertenencia.

### Ingredient sin `currentStock`
Stock es siempre por sucursal — global no tiene sentido en multi-sucursal. Antes de la primera compra, el insumo aparece "Sin stock registrado".

### Recepción del despacho en 2 pasos (ARRIVED → DELIVERED)
Flujo real: repartidor llega, encargada verifica TIPOS y firma acuse para liberar al repartidor; cuenta unidad por unidad después con calma. Combinarlos forzaría (a) repartidor esperando 20 min o (b) encargada confirmando a ciegas. Separar hace explícito que la integridad del transporte se transfiere en el acuse y el conteo correcto vive con la encargada después.

### `priceFactor` es ratio (0..1+), no descuento absoluto
Un factor escala automáticamente al subir precios al cliente final; un descuento fijo se desactualiza solo. Permite extender a "factor sobre costo + margen" cuando llegue el módulo de costos sin romper lo existente.

### Snapshot `priceSource` en línea del despacho
Auditoría: una nota de agosto debe seguir mostrando su precio histórico aunque el factor cambie en septiembre. Snapshot inmutable evita versionado complejo de listas.

### `cancelRun` bloquea cuando hay ARRIVED en adelante
ARRIVED ya significa que el repartidor entregó físicamente y firmaron acuse. Cancelar "borraría" un evento real. La hoja queda histórica; ajustes vía Kardex.

### Self-dispatch: el conteo del empaque ES la recepción
Planta que vende en mostrador no debe pasar por reparto (no hay vehículo). Forzar el flujo completo añade 3-4 taps.

**Al cerrar el empaque, la nota de la propia planta salta sola a DELIVERED** (`DispatchRepository.closeNoteAfterPacking`: `markReady` y, si `originBranchId == destinationBranchId`, `markAsLocalDispatch`). Antes quedaba en "Listas" esperando un reparto que nunca iba a existir — estancada ahí para siempre, y el pan que ya estaba físicamente en la tienda no entraba a su inventario. No hay transporte de por medio que pueda cambiar la cantidad, así que pedir un segundo conteo era pedir el mismo número dos veces.

La regla vive **solo** en `closeNoteAfterPacking`, que usan los dos caminos de cierre: el turno completo (`closePackingForBranchDateShift`, que cuenta las locales aparte en `PackingCloseResult.deliveredLocally` porque no aparecen en "Listas") y la nota suelta (`DispatchViewModel.confirmPacking`). El botón manual "Marcar como recibida en mostrador" sigue existiendo para las notas que ya quedaron en READY antes de esto.

No hay doble conteo con `absorbLeftoverIntoPlant`: ése ingresa lo **no empacado** (`requested − packed`) como AJUSTE y esto ingresa lo empacado como RECEPCION.

`getNotesForRunForm` **excluye las notas de la propia planta**: ofrecerlas como parada de una hoja de reparto las mandaba a un viaje que no existe y las dejaba IN_TRANSIT esperando un acuse que nadie firma, trabando además el auto-cierre de la hoja (`tryAutoCompleteRun` exige todas las paradas entregadas). Siguen visibles si ya están en ESA hoja, para poder quitarlas.

### Vehículos son Activos Fijos
Coherencia: el vehículo requiere mantenimiento, depreciación, garantía. Vincular permite atribuir combustible, kilometraje, viajes por vehículo. Evita duplicar registro.

### Faltantes en recepción NO regresan al stock de la planta
La planta no almacena terminado (produce y despacha el mismo día). Devolver implicaría inventario de terminado en planta. El faltante queda como pérdida en tránsito en Kardex — pérdida real, no retorno.

### Esponja decidida por el panadero (no la encargada)
La encargada solo consolida pedidos. El panadero decide por línea (un pan puede llevar esponja y otro no) en el momento de producción, no antes. Por eso la UI vive en el detalle del lote, no en el form del lote.

### Persistencia de esponja solo en botones del lote
Persistir en cada keystroke saturaría Firestore. `editableLotLines` en memoria + persistencia al "Iniciar producción" o "Completar". Trade-off: si el panadero navega fuera sin tocar esos botones, pierde cambios.

---

## Permisos (resumen)

`OWNER` tiene todos los permisos implícitos (`User.hasPermission` retorna true). Demás roles dependen del array `permissions` en doc del usuario.

| Grupo | Permisos |
|---|---|
| Catálogo | `VIEW_PRODUCTS`, `EDIT_PRODUCTS`, `VIEW_RECIPES`, `EDIT_RECIPES`, `MANAGE_PRICE_LISTS` |
| Insumos | `VIEW_INGREDIENTS`, `EDIT_INGREDIENTS`, `VIEW_SUPPLIERS`, `EDIT_SUPPLIERS` |
| Compras | `RECEIVE_STOCK`, `MANAGE_PURCHASE_PAYMENTS`, `EDIT_PURCHASES`, `DELETE_PURCHASES` |
| Producción | `VIEW_PRODUCTION`, `SEND_PRODUCTION_ORDER`, `MANAGE_PRODUCTION_LOTS`, `MANAGE_PRODUCTION_SHIFTS`, `ISSUE_PLANT_MATERIAL` |
| Preparaciones | `VIEW_PREPARATIONS`, `EDIT_PREPARATIONS`, `MANAGE_PREPARATION_BATCHES` |
| Distribución | `DISPATCH_PACK`, `MANAGE_DELIVERY_RUNS`, `DRIVE_DELIVERY`, `RECEIVE_DISPATCH`, `MANAGE_FINISHED_STOCK`, `VIEW_DISPATCH_VALUE` |
| Gastos | `VIEW_EXPENSES`, `ADD_VARIABLE_EXPENSE`, `ADD_GENERAL_EXPENSE`, `MANAGE_FIXED_BUDGET` |
| Administración | `VIEW_ALL_BRANCHES`, `MANAGE_BRANCHES`, `MANAGE_USERS`, `MANAGE_EMPLOYEES` |
| Activos Fijos | `VIEW_ASSETS`, `EDIT_ASSETS`, `MANAGE_ASSETS`, `ADD_ASSET_HISTORY` |

Lista completa en `model/Permission.kt`.

---

## Reportes y Dashboard

Pieza nueva (Fase 1 de reportería). Tres reportes formales + HomeScreen con datos vivos + banner de alertas.

### Permisos (`Permission.kt`, grupo "Reportes")
`VIEW_OPERATIONS_REPORT`, `VIEW_SALES_REPORT`, `VIEW_PRODUCTION_REPORT`, `VIEW_FINANCIAL_REPORT`, `VIEW_INVENTORY_REPORT`. OWNER los hereda implícito. Los permisos de monto (`VIEW_SALES_VALUE`, `VIEW_DISPATCH_VALUE`) siguen controlando si los $ se muestran dentro de cada reporte.

### Agregaciones (cliente, no servidor)
Firestore (y especialmente JS) no tiene group-by. Patrón: cada repo expone `aggregateXxxInRange(...)` que devuelve un `data class XxxSummary` ya agrupado. Viven en `domain/report/ReportSummaries.kt`:

- `SaleRepository.aggregateSalesInRange(companyId, branchIds?, from, until): SalesSummary`
- `ProductionRepository.aggregateProductionInRange(companyId, branchIds?, from, until): ProductionSummary` + `getLotsForBranchInRange(...)`
- `DispatchRepository.aggregateDispatchInRange(companyId, from, until): DispatchSummary` — `inTransitLoss = packedQty − receivedQty` cuando hay SHORT/DAMAGED
- `ExpenseRepository.getExpensesForPeriod(period)` + `aggregateExpensesForPeriod(...): ExpenseSummary` (period = "YYYY-MM"); corta `fromPurchases` vs `standalone`
- `PurchaseRepository.aggregatePurchasesInRange(companyId, from, until): PurchaseSummary` — filtra en memoria (mismo motivo que `getPurchases`: `date` puede ser `Timestamp` o `Long`)
- `PayrollRepository.getPayrollForPeriod(from, until)` + `aggregatePayrollForPeriod(...): PayrollSummary` — itera empleados (no `collectionGroup`) para evitar requerir índice nuevo. APPROVED+PAID solamente.
- `FinishedStockRepository.aggregateMovementsInRange(branchId, from, until): StockMovementSummary`
- `BreadCountRepository.aggregateMermasForMonth(branchId, yearMonth): MermaSummary`

### Reportes (`presentation/reports/`)
- **SalesReport**: día/semana/mes + sucursal. KPIs (ingresos, tickets, ticket promedio, unidades), barras por día, horizontales por método de pago, top productos, cajeros, histograma horario.
- **ProductionReport**: día/semana/mes + planta. KPIs (lotes completados, unidades, % eficiencia, merma en tránsito), paired bars planeado vs producido, por turno, por producto (badge de variación %), discrepancias por sucursal destino.
- **FinancialReport**: mensual. KPIs (ingresos, egresos, utilidad operativa, margen %, comprado, salió de caja). P&L card. Ingresos = `SalesSummary.totalRevenue`. Egresos = `ExpenseSummary.total + PayrollSummary.totalEmployerCost`. Utilidad = ingresos − egresos − planilla − mermas. Compras y flujo de caja en secciones aparte (ver abajo). Por sucursal y mermas por razón.

**Las compras entran como FLUJO DE CAJA, no como egreso del P&L.** El reporte no las conocía:
egresos eran gastos + planilla, así que la harina y el azúcar —la mayor salida de efectivo de
una panadería— no aparecían en ningún lado. Restarlas de la utilidad habría sido peor: son un
activo hasta que se venden, y un mes en el que se llena la bodega saldría en pérdida y el
siguiente en ganancia inventada. Por eso viven en dos secciones propias ("Compras del mes" y
"Salida de dinero del mes") y la utilidad operativa queda igual, con una nota que dice cuánto
se compró de inventario y por qué no está ahí.

**El doble conteo es el modo de fallo real, y se evita con dos cortes:**
- `Purchase.inventoryAmount` / `expensedAmount` denormalizan qué parte del total entró a
  inventario (INGREDIENT + RESALE_PRODUCT) y qué parte ya se gastó (SUPPLY + SERVICE, que
  además generaron su propio `Expense`). Van en el encabezado y no se calculan leyendo las
  líneas porque el reporte recorre un mes entero — mismo criterio que `totalAmount`.
- `ExpenseSummary.fromPurchases` / `.standalone` separan los gastos que nacieron de una compra
  de los capturados aparte. El flujo de caja resta `purchases.paidTotal + expenses.standalone`,
  así que el cloro no se resta dos veces.

`Purchase.amountSplit()` deduce el reparto del encabezado cuando los campos faltan: **sin
`lineKinds` es materia prima por definición** (era lo único que el módulo registraba), un solo
grupo de tipos va entero a su lado, y una compra MIXTA anterior al campo cae en
`unclassified` — que la pantalla muestra como "Sin desglosar" en vez de repartirlo a ojo.
Adivinar ahí sería mentir justo en el número que la sección existe para dar.
`functions/tools/backfill-purchases.js` los rellena desde las líneas y resuelve ese caso.

**`paidTotal` es lo abonado de las compras FECHADAS en el mes**, no el efectivo exacto que
salió: un abono de hoy a una factura de agosto cuenta en agosto. Se acepta a sabiendas —
lo contrario obliga a abrir la subcolección de abonos de cada compra— y la pantalla lo dice
en vez de dar a entender que es un flujo de caja exacto. Una compra `PENDING_RECEIPT` no
cuenta: no entró a la bodega ni generó gasto todavía.

Lógica pura en `domain/report/PurchaseAggregation.kt`, con tests en `PurchaseAggregationTest`.

- **Pedido vs. recibido** (`presentation/reports/branchsupply/`, permiso `VIEW_BRANCH_SUPPLY_REPORT`): por sucursal y producto, agrupado por masa — pedido → asignado por turno → empacado → recibido → vendido. Una sucursal a la vez (`BranchFilterRow(allowAll = false)`): sumar varias mezcla el faltante de una con el sobrante de otra. Permiso propio y no `VIEW_PRODUCTION_REPORT` porque ése es de la planta; la encargada de sucursal ve solo lo suyo.
  - **No hay colección nueva**: la nota de despacho es el puente, porque es lo único que sabe cuánto de un pedido le tocó a cada turno. `buildBranchSupplyReport` (`model/BranchSupplyReport.kt`, tests en `BranchSupplyReportTest`) es lógica pura; `loadBranchSupplyData` hace las lecturas (notas por `getNotesForOrder` + `getNote`, no la colección entera).
  - Enviado solo cuenta notas fuera de DRAFT; recibido y diferencia solo las ya contadas (DELIVERED*). Lo que salió y no se contó se muestra "sin contar" en vez de un 0 que se lea como faltante. Canceladas van aparte.
  - "Los turnos no suman lo pedido" se marca cuando el pedido ya tiene todas sus notas y no cuadran. Si falta la nota del segundo turno (su lote no arrancó; `expectedShiftsForOrder` = 2 para un pedido sin turno a planta de 2 turnos activos) la diferencia es "sin lote todavía", no un reparto roto.
  - **Una pestaña por planta productora** (`PlantSupply`, un `buildBranchSupplyReport` por planta): cada una despacha con sus propios turnos y juntas mezclaban columnas de turnos ajenos y alargaban la lista. KPIs y tabla siguen a la pestaña elegida; sin pestañas si la sucursal le pidió a una sola.
  - Vendido sale de `SalesSummary.unitsByProduct` (nuevo: `topProducts` se corta en 20). Los especiales no tienen ventas.
  - La misma tabla (`BranchSupplyTable`) sale como sección **"Seguimiento"** en el detalle del pedido (`ProductionViewModel.loadOrderSupply`), en cuanto existen notas.

Helpers compartidos en `presentation/reports/shared/ReportComponents.kt`: `ReportPeriodSelector`, `PeriodNavigator`, `BranchFilterRow`, `KpiTile`, `ReportSection`, `VerticalBars`, `PairedBars`, `HorizontalBars`, `EmptyDataNote`.

### HomeScreen renovado
- `HomeViewModel` combina sales/production/stock/dispatch/cash flows. KPIs vivos, gráfica de ventas reales de los últimos 7 días, banner de alertas filtrado por permisos del user.
- Sección "Reportes" — chips a Ventas / Producción / Financiero, visibles solo según `VIEW_*_REPORT`.
- Refresh manual vía `HomeViewModel.refresh(user)`; `ensureLoadedFor(user)` carga una vez al login.

### Banner de alertas (`presentation/home/AlertsBanner.kt`)
Banner rotatorio cada 6s entre alertas. Tap → navega a módulo correspondiente. Pager dots si hay >1. Color de borde por severity. Si `alerts.isEmpty()` no se renderiza.

**Modelos**: `domain/report/HomeAlert.kt` con `AlertSeverity` (INFO/WARNING/CRITICAL) y `AlertTarget` (enum que mapea a `Screen` desde HomeScreen — evita acoplar `domain/` con `navigation/`).

**Tipos de alerta** (cada uno gateado por permiso, computados in-memory en HomeViewModel):

| Alerta | Datos fuente | Permiso |
|---|---|---|
| Stock bajo | `BranchStock < ingredient.stockAlert` | `VIEW_INGREDIENTS` |
| Mantenimiento próximo | `FixedAsset.nextMaintenanceDate ≤ +7d` | `VIEW_ASSETS` |
| Despacho sin acuse | `DispatchNote.status==IN_TRANSIT && now − packedAt > 12h` | `MANAGE_DELIVERY_RUNS` o `DISPATCH_PACK` |
| Lote estancado | `ProductionLot.status==IN_PROGRESS && productionDate < hoy` | `MANAGE_PRODUCTION_LOTS` |
| Pedido tardío PENDING | `ProductionOrder.isLateOrder && status==PENDING` | `MANAGE_PRODUCTION_LOTS` |
| Caja abierta vencida | `CashSession.status==OPEN && now − openedAt > 14h` | `SPOT_CHECK_CASH` o `MANAGE_DAILY_CASH_CLOSE` |
| Precio sin definir | `DispatchNote.hasPriceMissing` en DRAFT/READY | `MANAGE_PRICE_LISTS` |

No hay colección `alerts/` en Firestore — son proyecciones derivadas. Evita sincronización y stale data.

---

## Pendiente / Futuro

- **Push web**: falta pegar la clave VAPID en `WEB_PUSH_VAPID_KEY` (`jsMain/FirebaseWeb.kt`). Se genera en Firebase Console → Cloud Messaging → Certificados push web. Todo lo demás ya está armado y compila. Ver `functions/README.md`.
- **Push iOS**: falta certificado APNs de la cuenta de Apple Developer + implementar `PushTokenProvider` para iOS (hoy usa `NoOpPushTokenProvider`).
- **Deep link del push en web**: el clic enfoca/abre la pestaña pero no aterriza en el pedido — la app web no tiene rutas en la URL (navegación en memoria). Requeriría routing por URL.

- Clientes externos (tiendas de barrio, cafeterías) — reparto a no-sucursales
- Cálculo automático de costo de producto (receta + precios insumos) — desbloquea margen por producto
- Reportes fase 2: comparativos mes-vs-mes, exportación PDF/Excel, margen por producto
- Notificaciones push de stock mínimo (hoy solo banner en HomeScreen)

---

## Comandos

```bash
./gradlew composeApp:compileDebugKotlin        # Android compile
./gradlew composeApp:compileKotlinJs           # JS compile
./gradlew composeApp:installDebug              # Instalar Android
./gradlew composeApp:jsBrowserDevelopmentRun   # Web localhost:8080
./gradlew clean
```
