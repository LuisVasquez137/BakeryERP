# Producción — detalle

> Detalle movido desde CLAUDE.md. Leer antes de tocar este módulo.

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

#### El conteo reparte lo contado (`reconcileCount`)

En 20 días de lotes reales nadie abrió "¿Qué pasó?": los panaderos corregían el número
en el conteo (388 pedidos → contaron 400; 460 → 440). Los 12 de más no entraban a ninguna
nota ni al stock de la planta, y los 20 de menos no decían si se quemaron. Por eso el
**conteo es físico** —todo el pan bueno que hay en la mesa, incluido el de más— y se reparte:

- `producedQuantity` = hasta lo pedido (va a las sucursales); lo que pasa va a
  `extraQuantity` (motivo `COUNTED_MORE`) y entra al stock de la planta como cualquier extra.
- Si sobra o falta contra lo esperado (`expectedGoodCount` = pedido + lo anotado de más), la
  hoja muestra un paso **"Revisá antes de guardar"**: lo de más solo se informa; lo de menos
  exige elegir por producto un motivo de merma (suma a `wasteQuantity` y descuenta la masa) o
  "No se perdió: se amasó menos" (`SHORTFALL_LESS_DOUGH`, no descuenta nada). La merma ya
  anotada en "¿Qué pasó?" cuenta como explicada y no se vuelve a preguntar.
- El prellenado y la pantalla "Completar" usan el mismo conteo físico (`physicalGoodCount`,
  `setPhysicalCount`), y `completeLot` pasa cualquier excedente que quede a extra
  (`withSurplusAsExtra`) como red de seguridad.

**Cierre con todo perdido**: `completeLot` toma las líneas por lo AMASADO
(`kneadedQuantity > 0`), no por lo bueno. Con 40 Pan de Capa quemados sin reponer el conteo
quedaba en 0 y la línea se saltaba entera: ni Capa, ni Suave, ni concha. Las notas de
despacho siguen tomando solo lo bueno.

**"Reponer lo perdido"** es la tercera vista del selector del panel de la masa (junto a
"Todo el día" y "Solo lo extra"): la receta de reposición solo existía dentro de la hoja
mientras se anotaba. Con el Pan de Capa, la masa Suave también la ofrece, con las bolitas de
las piezas perdidas. Los cargadores del detalle del lote traen también las recetas de los
rellenos: sin eso, un turno sin productos propios de Suave dejaba esa receta vacía.

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

**La exclusión de turno manda sobre todo, también al recalcular.** `splitOrderAllocations` y
`tripRestrictionPins` reciben `allowedInA/allowedInB`: si un turno no hornea un pan, todo va al
otro aunque la sucursal solo reciba en ese viaje o haya declarado encargo. Antes la restricción
de viaje le ganaba en el recálculo: el 5/10 el lote de mañana salió con Cubo pequeño para Pacajá
(excluido de la mañana) y el de noche lo perdió del empaque. `functions/tools/inspect-shift-exclusions.js`
lista lotes con productos excluidos de su turno (solo lectura).

#### Ajustar el pedido con los turnos ya repartidos (`model/OrderAdjustment.kt`)

La sucursal se confundió y se entera con el lote ya armado. Llama a la planta; la encargada
de planta usa **"Ajustar pedido"** en el detalle del pedido: producto, cuánto se le pone o se
le quita, y **en qué turno**. Solo sobre el pedido de ESA sucursal (no se pasa pan entre
sucursales) y nunca al inventario de la planta.

- **Es un ajuste por diferencia, no una edición del pedido entero.** Editar el pedido dispara
  el recálculo, que reparte solo y —con la noche ya en producción— cargaba todo al turno
  abierto aunque no hiciera ese pan. Por eso "Editar pedido" se apaga en cuanto algún turno
  arrancó: lo que la encargada dice es exactamente lo que se escribe.
- **Si la masa ya se hizo lo verifica ella con el panadero**, no el sistema: abrir la receta
  no prueba que se amasó (ni no abrirla que no). En un turno IN_PROGRESS el diálogo exige
  marcar "Confirmé con el panadero que la masa X todavía no se ha hecho", y queda registrado.
  Un turno OPEN se ajusta sin preguntar.
- **El sistema bloquea solo lo que sabe con certeza** (`orderAdjustmentProblem`): el turno no
  hace ese pan, la masa ya tiene conteo confirmado, se quita más de lo que esa sucursal tiene
  en ese turno, la nota ya no es DRAFT o esa línea ya se empacó, lote terminado. Avisa sin
  bloquear si la sucursal no recibe en ese viaje o si la masa queda bajo el mínimo.
- **Escribe en orden**: pedido (`adjustedBy` mueve `earlyShiftQuantity` con el turno del primer
  viaje, porque el recálculo respeta ese número siempre) → línea del lote (`adjustedFor` toca
  solo el desglose de esa sucursal, así A+B sigue dando lo pedido y un recálculo posterior no
  mueve nada) → nota DRAFT de esa sucursal en ese turno (`adjustNoteLineForOrder`; si no tenía
  nota en ese turno se crea con `createDispatchNotesForLot`) → registro en
  `companies/{c}/orderAdjustments` (plana, solo se agrega). Todo se relee y revalida antes de
  escribir.
- **El panadero ve "Cambió: +20 Democracia (1:20 AM)"** (`ProductionLotLine.adjustmentNote`)
  en su lista: pudo haber impreso la orden y el papel dice el número viejo. En un lote en
  producción una línea que queda en 0 no se borra, para que vea que ese pan se quitó.
- El detalle del pedido lista los ajustes hechos (quién, cuándo, si se verificó).
