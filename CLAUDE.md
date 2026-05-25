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
  ingredients/{id}                         ← doc plano (sin currentStock)
    purchaseFormats/{id}                   ← subcollección
    movements/{id}                         ← Kardex por insumo
  branchStocks/{branchId}_{ingredientId}   ← colección PLANA (no subcoll.) para query única
  purchases/{id}/lines/{id}
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

### Productos
Campos: `name`, `description`, `category` (Panadería/Repostería/Pastelería), `imageUrl`, `salePrice`, `unit`, `weightPerUnit` (oz), `rawWeightGrams` (peso en crudo para escalar masa), `shelfLife`, `storageType`, `characteristics` (organolépticas, **desnormalizadas como campos planos**), `decorations: List<ProductDecoration>` (`{preparationId, name, gramsPerUnit}`).

### Ingredientes (sin `currentStock`)
Stock vive solo en `BranchStock`. Form no tiene campo de stock — solo se genera al registrar compras. `PurchaseFormat` (presentación de compra) en subcolección.

### Stock por Sucursal (`BranchStock`)
ID compuesto `{branchId}_{ingredientId}`. `IngredientListScreen` tiene chips de sucursal (auto-selección si hay solo una accesible). No se suma entre sucursales.

### Compras
3 pasos: sucursal destino → proveedor → cantidades. Por línea: actualiza `BranchStock` + registra `StockMovement` (Kardex). Solo sucursales con `CAN_PRODUCE` o `IS_WAREHOUSE`. Permiso: `RECEIVE_STOCK`.

### Proveedores
CRUD simple (nombre, contacto, teléfono, email, dirección, notas).

### Preparaciones + Lotes (`Preparation` / `PreparationBatch`)

- `usage: PreparationUsage` — `DOUGH` (esponja para masa) | `DECORATION` (concha, etc.).
- Ingredientes con `quantityPerBatch + unit`; peso del lote = Σ `toBaseUnit`.
- `flourFractionPct` denormalizado al guardar.
- **Esponja en producción = decisión del PANADERO, no de la encargada.** No se configura al crear lote. Vive en el panel de receta escalada del Detalle de Lote ("Lista de Producción"), por línea.
- Input en **libras** (`lb`); internamente gramos vía `toBaseUnit`.
- `ProductionViewModel.calculateLineConsumption` resta los gramos que la esponja aporta por `ingredientId` (fracción = `quantityPerBatch_i / batchWeightGrams`). Si esponja aporta más de lo que pide la receta, se descuenta solo lo presente.
- **Persistencia de cambios de esponja**: van a `editableLotLines` (memoria). Se persisten al "Iniciar producción" (`startLot` → `saveLotLine`) o "Completar" (`completeLot`). Si el panadero navega fuera sin tocar esos botones, se pierden. Trade-off para evitar escrituras por keystroke.
- `consumeFromBatch(batchId, grams)`: baja `remainingWeightGrams`, marca `DEPLETED` cuando ≤5g (±5g tolerancia). Falla si saldo < grams−5.
- Adornos: `Product.decorations` snapshotteados en `ProductionLotLine.decorationsSnapshot` con batch pre-seleccionado FIFO; se descuenta `gramsPerUnit × producedQuantity` al cerrar.
- `PreparationBatch`: producir N lotes en sucursal `CAN_PRODUCE` descuenta insumos del `BranchStock` + Kardex `PRODUCCION`. Doc plano.
- Permisos: `VIEW_PREPARATIONS`, `EDIT_PREPARATIONS`, `MANAGE_PREPARATION_BATCHES`.

### Recetas — receta plana, solo pan principal

**Sin etapas, sin tipos.** Lista plana de componentes + pasos + productos que genera. `RecipeStage`/`RecipeStageType` solo para deserializar legacy. Esponja NO va en receta — vive en Preparaciones y se declara en el lote.

**Campos clave**: `components: List<RecipeComponent>` plano, `steps: List<RecipeStep>` plano, `stages` solo legacy. Helpers `allComponents`/`allSteps` aplanan legacy si necesario.

**Modos de ingreso:**
- `PERCENTAGE` — % del panadero (harinas base suman 100%).
- `WEIGHT` — pesos reales; al guardar convierte a % usando Σ pesos base como 100%.

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

**Modelos**: `ProductionOrder` (+`shiftId/shiftName`, `isLateOrder`, subcolección `lines/` con `requestedQuantity`); `ProductionLot` (+`shiftId`, `sourceOrderIds`, subcoll `lines/` con `ProductionLotLine` que tiene `rawWeightGrams`, `plannedQuantity`, `producedQuantity`, `recipeId`, `spongeBatchId/Name`, `spongeGramsUsed`, `decorationsSnapshot`).

**`ProductionOrderFormScreen`** (3 pasos creación, 2 edición):
- Step 1: sucursal solicitante + planta + turno + fecha + notas. Turno requerido si planta tiene turnos activos. `rememberSaveable` con IDs (no objetos).
- Step 2: productos + cantidades. En edición + ≥800dp: dos columnas (info izq fija, productos der).
- Step 3 (`OrderReviewStep`): resumen read-only + banner amarillo + botones "Editar pedido" / "Enviar pedido".
- **Detección de duplicado**: combinación `(sellingBranch, producingBranch, date, shiftId)` con pedido PENDING:
  - Standalone → dialog "Editar el existente".
  - Ya en lote OPEN/IN_PROGRESS → permite crear con `isLateOrder=true` (sin dialog).
- **Permisos de edición**:
  - Sin lote → sucursal solicitante con `SEND_PRODUCTION_ORDER`.
  - Lote OPEN → solo la planta. Sucursal ve banner "Contacta al supervisor de la planta productora (X)".
  - IN_PROGRESS/COMPLETED → nadie edita.

**`ProductionOrderListScreen`**: tabs "Mis pedidos" / "Recibidos" / "Consolidado".
- Filtro de mes (chevrons + `MonthPickerDialog`), default = mes en curso, `rememberSaveable`.
- Chip turno en "Recibidos".
- Tabla en ≥720dp con columnas; cards en <720dp. Sucursal solicitante en chip azul `#123456`.
- **Side panel ≥1000dp**: tap a fila abre `ProductionOrderDetailContent` (sin Scaffold) en columna derecha; en móvil navega a screen completo.
- **Listener vivo** (`observeOrdersForMonth`): combina `observeOrders` + `observeLots` vía `Query.snapshots`. Un listener por colección, no polling.
- Tab Consolidado: planta + chips turno + chevrons fecha. Tabla con bordes y filas alternadas. Si ya existe lote para `(planta, fecha, turno)` muestra banner verde + link al detalle.

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
- Selección de fila → panel derecho con `ScaledRecipePanel` + `SpongeControlsForLine` (checkbox + picker batch + libras).
- Botones: "Iniciar producción" (OPEN→IN_PROGRESS, persiste editableLotLines) y "Completar y descontar stock" (IN_PROGRESS→COMPLETED).

**`ProductionLotCompleteScreen`**: confirma cantidades reales + dispara `completeLot` (consumo insumos Kardex + consumo batches esponja/adornos + marca `sourceOrderIds` como CONFIRMED + auto-crea `DispatchNote` DRAFT por cada source order).

**Cancelación de pedido (`cancelOrder`)**:
- Sin lote → sucursal solicitante cancela libre.
- Lote OPEN → solo planta; `removeOrderFromLot` resta líneas (elimina las que queden en 0) y quita `orderId` de `sourceOrderIds`.
- IN_PROGRESS/COMPLETED → bloqueado.

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

**Pre-llenado**:
- Empaque: `packedQuantity = requestedQuantity`. Si difiere → `packedReason` obligatorio.
- Recepción: `receivedQuantity = packedQuantity`. Si difiere → `discrepancyType` + `receivedReason`.

**Bloqueos**:
- DRAFT→READY bloqueado si alguna línea tiene `priceSource=NONE`.
- `cancelNote` bloqueado en IN_TRANSIT o terminales.
- Discrepancia NO devuelve stock a planta (queda como pérdida en tránsito en Kardex).

**Self-dispatch** (`markAsLocalDispatch`): si `originBranchId==destinationBranchId` y READY, botón único **"Marcar como recibida en mostrador"** salta a DELIVERED sin reparto. Suma a `FinishedProductStock` de la planta.

**Permiso `VIEW_DISPATCH_VALUE`**: oculta `unitWholesalePrice` y `totalValueWholesale` en todas las pantallas (solo OWNER por defecto).

**Pantallas**: `DispatchNoteListScreen` (tabs por status, filtro mes, listener vivo), `DispatchNotePackScreen` (input por producto, razón si difiere, bloquea si precio NONE), `DispatchNoteReceiveScreen` (UI alterna por status), `DispatchNoteDetailScreen` (read-only con timeline).

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

Form `FinishedStockAdjustScreen` con 4 opciones (Merma, Ajuste menor/mayor, Venta directa), valida no negativo, razón obligatoria, preview "Nuevo stock: X".

KPIs ocultan valor de inventario sin `VIEW_DISPATCH_VALUE`. Permiso: `MANAGE_FINISHED_STOCK`.

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

## Paleta (HomeScreen)

- **Deep Sea Blue** `#123456` — primario, TopAppBar, textos principales
- **Warm Ivory** `#FDFBF7` — fondo
- **Burnt Orange** `#CC5500` — acento sutil (notificaciones, bordes, líneas)

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

### Self-dispatch como flujo separado
Planta que vende en mostrador no debe pasar por reparto (no hay vehículo). Forzar el flujo completo añade 3-4 taps. Botón único "Marcar como recibida en mostrador" salta a DELIVERED, preservando auditoría.

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
| Compras | `RECEIVE_STOCK` |
| Producción | `VIEW_PRODUCTION`, `SEND_PRODUCTION_ORDER`, `MANAGE_PRODUCTION_LOTS`, `MANAGE_PRODUCTION_SHIFTS` |
| Preparaciones | `VIEW_PREPARATIONS`, `EDIT_PREPARATIONS`, `MANAGE_PREPARATION_BATCHES` |
| Distribución | `DISPATCH_PACK`, `MANAGE_DELIVERY_RUNS`, `DRIVE_DELIVERY`, `RECEIVE_DISPATCH`, `MANAGE_FINISHED_STOCK`, `VIEW_DISPATCH_VALUE` |
| Gastos | `VIEW_EXPENSES`, `ADD_VARIABLE_EXPENSE`, `MANAGE_FIXED_BUDGET` |
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
- `ExpenseRepository.getExpensesForPeriod(period)` + `aggregateExpensesForPeriod(...): ExpenseSummary` (period = "YYYY-MM")
- `PayrollRepository.getPayrollForPeriod(from, until)` + `aggregatePayrollForPeriod(...): PayrollSummary` — itera empleados (no `collectionGroup`) para evitar requerir índice nuevo. APPROVED+PAID solamente.
- `FinishedStockRepository.aggregateMovementsInRange(branchId, from, until): StockMovementSummary`
- `BreadCountRepository.aggregateMermasForMonth(branchId, yearMonth): MermaSummary`

### Reportes (`presentation/reports/`)
- **SalesReport**: día/semana/mes + sucursal. KPIs (ingresos, tickets, ticket promedio, unidades), barras por día, horizontales por método de pago, top productos, cajeros, histograma horario.
- **ProductionReport**: día/semana/mes + planta. KPIs (lotes completados, unidades, % eficiencia, merma en tránsito), paired bars planeado vs producido, por turno, por producto (badge de variación %), discrepancias por sucursal destino.
- **FinancialReport**: mensual. KPIs (ingresos, egresos, utilidad operativa, margen %). P&L card. Ingresos = `SalesSummary.totalRevenue`. Egresos = `ExpenseSummary.total + PayrollSummary.totalEmployerCost`. Utilidad = ingresos − egresos − planilla − mermas. Por sucursal y mermas por razón.

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
