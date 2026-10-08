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

## Módulos — índice (el detalle vive en `arquitectura/`)

**Antes de tocar un módulo, leer su archivo en `arquitectura/`**: ahí está el "porqué" de cada decisión y las trampas ya resueltas. Al terminar un cambio importante, documentarlo en ESE archivo (no en este CLAUDE.md, que tiene límite de tamaño) y dejar aquí solo una línea si cambia una regla transversal.

| Archivo | Cubre |
|---|---|
| `arquitectura/produccion.md` | Agrupado por masa, pedidos especiales, clima/sugerencias, impresión de orden (ESC/POS), peso en crudo, flujo completo pedido→lote→completeLot, material de planta (`PlantIssue`), merma/pan de más (`ProductionAdjustment`), mínimo de amasada, reparto por turnos, ajuste de pedido post-reparto |
| `arquitectura/catalogo.md` | Productos, adornos/rellenos, insumos (`usage`, `tracksStock`, `unitWeightGrams`), `BranchStock`, preparaciones + esponja, recetas planas |
| `arquitectura/compras.md` | Factura con `lineKind`, suministros, conceptos de compra, historial por proveedor, reventa con presentaciones, compra rápida, proveedor ocasional, editar/borrar/abonos, formulario, lotes FEFO |
| `arquitectura/caja-gastos.md` | "Anotar gasto" (`CaptureKindStep`, `QuickPurchaseFormScreen`), compra pagada de caja, movimientos/correcciones de caja, autocierre, permisos y categorías de gasto |
| `arquitectura/distribucion.md` | Listas de precios, despacho (`DispatchNote`), tablero de Reparto, cadena de entrega/canastas, `DeliveryRun`, inventario de tienda |
| `arquitectura/pos-ventas.md` | Venta por grupo de precio, control de cajas (horarios, bitácora, cadena de turnos), cobro, último cobro, créditos, anticipos |
| `arquitectura/fel.md` | FEL Guatemala / Digifact: servidor, configuración, facturar al cobrar, ticket con QR, anular/reemitir, restaurante (ComandaYa) |
| `arquitectura/reportes.md` | Agregaciones `aggregateXxxInRange`, reportes, Home y banner de alertas |
| `arquitectura/otros.md` | Notificaciones/push, activos fijos |
| `arquitectura/seguridad-accesos.md` | Reglas Firestore/Storage (se publican desde el repo, pruebas en `rules-tests/`), roles, plan cuenta de sucursal + PIN |
| `arquitectura/decisiones-y-paleta.md` | Paleta completa, `BrandChip`, colores del Home, decisiones de diseño históricas |

### Reglas transversales que no se pueden olvidar

- **Un solo camino por efecto.** Inventario de insumos solo por `IngredientRepository.updateBranchStock` (mantiene FEFO); compras por `applyStockForPurchase`/`applyStockDelta`; gastos de compra por `syncPurchaseSideEffects`; efectivo de compra por `syncPurchaseCashMovement`; línea de lote especial por `specialLotLine`; línea de reventa por `resalePurchaseLine`; insumo→tipo de línea por `ingredientPurchaseLineKind`.
- **Corregir = escribir la diferencia, nunca reescribir/borrar historial** (Kardex, caja, créditos). Borrar reutiliza el mismo camino con lista vacía.
- **Escrituras parciales** cuando otro proceso reescribe con `set()` (`updateRawWeights`, `updateProductSortOrder`, `addPurchaseFormat`, `assignBranches`…). Los forms que hacen `set()` deben arrastrar los campos que no editan (`BranchFormScreen.build()`).
- **Cachés denormalizadas se RECALCULAN sumando la fuente**, no sumando/restando (`paidAmount`, `CreditAccount.balance`, `packedUnits`).
- **Encabezado antes que stock** (`receivePurchase`, `cancelNote`, vales de planta): un reintento choca con la guarda de estado en vez de duplicar.
- **Fechas**: filtrar en memoria (pueden ser `Timestamp` o número); "mismo día" con `toLocalDateTime(tz).date`.
- **Conversiones que no se pueden hacer devuelven null** (`toGramsOrNull`, `packagesToBaseUnitOrNull`, `looseToBaseUnitOrNull`), nunca pasan el número tal cual.
- **`applyMovement` lee-modifica-escribe**: agrupar por producto y llamar en serie.
- **Valores viejos en Firestore se siguen leyendo** (`MARKET_RUN` = QUICK; enums viejos de categoría se conservan).
- Barras inferiores fijas llevan `navigationBarsPadding()` (edge-to-edge Android 15).
- Esperas largas contra Firestore muestran `LoadingOverlay` (evita doble toque = doble registro).
- Scripts admin (firebase-admin + ADC) en `functions/tools/`.

### Auth
Email/password + Google. Multi-empresa: al registrarse crea Organization; `JoinOrgScreen` para unirse por código. Roles: OWNER, ADMIN, MANAGER, EMPLOYEE.

### Empleados
CRUD perfil + documentos + historial + nómina. Modo kiosko (reloj de turno biométrico en tablet). Historial de asistencia con mes/año navegable.

---

## Paleta (resumen — detalle en `arquitectura/decisiones-y-paleta.md`)

`ui/theme/AppTheme.kt::BrandColors` es la **única** fuente de color; nada de `Color(0xFF...)` en pantallas. `Primary` #005B8D (estructura), `Accent` #FE7F03 (estado/atención, **siempre relleno, nunca texto**; encima va `OnAccent`), `AccentDeep` #A85200 (único naranja admisible como texto, o relleno con letra blanca), verde = hecho, rojo = roto. Chips de estado con `BrandChip(text, tone)`; ningún par baja de 4.5:1 (`BrandChipContrastTest`).

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
