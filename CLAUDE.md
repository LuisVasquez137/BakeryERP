# BakeryERP — CLAUDE.md

Contexto completo del proyecto para sesiones futuras. Actualizar este archivo cuando se agreguen módulos o cambien patrones importantes.

---

## Descripción del Proyecto

SaaS ERP para panaderías artesanales. Gestión interna de recetas, insumos, productos terminados y proveedores. Multi-empresa (cada cliente tiene su propio `companyId`). El usuario final es el dueño o encargado de la panadería.

**App en desarrollo:** Fernando / luisvasquez1307@gmail.com  
**Nombre comercial de la app:** Bagueto

---

## Tech Stack

| Tecnología | Uso |
|---|---|
| Kotlin Multiplatform (KMP) | Código compartido entre Android, iOS, Web |
| Compose Multiplatform | UI declarativa multiplataforma |
| Firebase Auth (GitLive SDK) | Autenticación de usuarios |
| Firebase Firestore (GitLive SDK) | Base de datos en tiempo real |
| Firebase Storage (GitLive SDK) | Imágenes de productos |
| Koin 4.0.3 | Inyección de dependencias |
| Compose Navigation | Navegación entre pantallas |
| AndroidX ViewModel + StateFlow | Gestión de estado |

**Targets KMP configurados:** Android, iOS (arm64 + simulator), JS (browser), wasmJs (browser)  
**Target principal activo:** Android

---

## Arquitectura: Clean Architecture

```
commonMain/
  model/           ← Capa de Dominio: Entidades (data classes puras)
  domain/          ← Capa de Dominio: Interfaces de repositorios
  presentation/    ← Capa de Presentación: ViewModels + Composables
  navigation/      ← Navegación (AppNavigation.kt)
  ui/              ← Componentes UI reutilizables
  di/              ← Inicializador de Koin (expect/actual)

androidMain/
  data/            ← Capa de Datos: Implementaciones Firebase (Android)
  di/              ← Módulo Koin Android (androidModule)
  auth/            ← GoogleAuthHandler para Android

nonWasmMain/       ← Implementaciones Firebase compartidas (Android + iOS + JS)
  data/            ← FirebaseRecipeRepository, FirebaseIngredientRepository,
                      FirebaseSupplierRepository, FirebaseAuthRepository
  di/              ← AppModule con todas las dependencias comunes
```

### Reglas de Arquitectura

- **Las pantallas (Composables) solo conocen el ViewModel** — nunca acceden directamente a repositorios ni a Firebase.
- **Los ViewModels solo conocen las interfaces de repositorio** (`domain/`) — nunca importan clases Firebase.
- **Las implementaciones Firebase están en `data/`** — nunca en `commonMain`.
- **Los modelos (`model/`) son data classes puras** — sin lógica de negocio ni dependencias externas.

---

## Estructura de Archivos Clave

```
composeApp/src/commonMain/kotlin/com/bakery/erp/
│
├── model/
│   ├── Recipe.kt            — Recipe, RECIPE_CATEGORIES
│   ├── RecipeStage.kt       — RecipeStage, RecipeStageType (enum)
│   ├── RecipeComponent.kt   — RecipeComponent
│   ├── RecipeYield.kt       — RecipeYield
│   ├── Product.kt           — Product, ProductCharacteristics, PRODUCT_CATEGORIES
│   ├── Ingredient.kt        — Ingredient, PurchaseFormat (sin currentStock — stock vive en BranchStock)
│   ├── BranchStock.kt       — BranchStock (stock de un insumo por sucursal)
│   ├── StockMovement.kt     — StockMovement (Kardex: COMPRA | AJUSTE | PRODUCCION)
│   ├── Purchase.kt          — Purchase (cabecera), PurchaseLine (línea de insumo)
│   ├── Supplier.kt          — Supplier
│   ├── User.kt              — User, UserRole, canAccessBranch(), hasPermission()
│   └── Organization.kt      — Organization
│
├── domain/
│   ├── RecipeRepository.kt
│   ├── ProductRepository.kt
│   ├── IngredientRepository.kt  — incluye métodos de BranchStock y Kardex
│   ├── PurchaseRepository.kt
│   ├── SupplierRepository.kt
│   └── AuthRepository.kt
│
├── presentation/
│   ├── auth/       — LoginScreen, RegisterScreen, AuthChoiceScreen, JoinOrgScreen, AuthViewModel
│   ├── home/       — HomeScreen (dashboard con gráfica de barras)
│   ├── recipes/    — RecipeListScreen, RecipeDetailScreen, RecipeFormScreen, RecipeViewModel
│   ├── products/   — ProductListScreen, ProductDetailScreen, ProductFormScreen, ProductViewModel
│   ├── ingredients/— IngredientListScreen, IngredientDetailScreen, IngredientFormScreen, IngredientViewModel
│   ├── purchases/  — PurchaseListScreen, PurchaseFormScreen, PurchaseViewModel
│   └── suppliers/  — SupplierListScreen, SupplierDetailScreen, SupplierFormScreen, SupplierViewModel
│
├── navigation/
│   └── AppNavigation.kt    — NavHost + sealed class Screen
│
└── ui/
    ├── AdaptiveLayout.kt   — AdaptiveContainer (responsive para tablet/web)
    ├── ImagePickerButton.kt
    └── ProductImage.kt
```

---

## Patrones de Firestore (CRÍTICO)

### Restricción del GitLive SDK

El GitLive Firebase SDK **no serializa automáticamente** objetos Kotlin complejos ni listas de objetos. Toda escritura a Firestore debe hacerse con `Map<String, Any?>` explícito.

```kotlin
// ✅ CORRECTO — documento plano con Map explícito
docRef.set(mapOf(
    "id" to id,
    "name" to name,
    "price" to price
))

// ❌ INCORRECTO — el SDK no puede serializar la data class directamente
docRef.set(myDataClass)

// ❌ INCORRECTO — listas de objetos no se serializan
mapOf("formats" to listOf(PurchaseFormat(...)))  // falla
```

### Estructura de Colecciones Firestore

```
companies/{companyId}/
  products/{productId}                          ← documento plano
  ingredients/{ingredientId}                    ← documento plano (sin currentStock)
    purchaseFormats/{formatId}                  ← subcollección
    movements/{movementId}                      ← subcollección Kardex (por insumo)
  branchStocks/{branchId}_{ingredientId}        ← colección PLANA de stock por sucursal
  purchases/{purchaseId}                        ← cabecera de compra
    lines/{lineId}                              ← subcollección líneas de compra
  suppliers/{supplierId}                        ← documento plano
  recipes/{recipeId}                            ← documento plano (stageCount, yieldCount denormalizados)
    stages/{stageId}                            ← subcollección
    components/{componentId}                    ← subcollección (nivel receta, NO bajo stage)
    yields/{yieldId}                            ← subcollección
```

**¿Por qué `branchStocks` es colección plana y no subcollección?**
Permite cargar todos los stocks de una sucursal en una sola query filtrando en memoria por `branchId`, sin hacer N queries (una por ingrediente). El ID compuesto `{branchId}_{ingredientId}` garantiza unicidad.

**¿Por qué components está bajo recipe y no bajo stage?**  
Firestore no recomienda más de 2 niveles de subcollección. `components` tiene un campo `stageId` para saber a qué etapa pertenece.

### Deserialización de Timestamps

Los campos de fecha pueden llegar como `Timestamp` (docs viejos) o `Long` (docs nuevos). Siempre usar:

```kotlin
private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
    runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
        ?: runCatching { get<Long?>(field) }.getOrNull()
        ?: 0L
```

---

## Módulos Implementados

### Auth
- Email/password y Google Sign-In
- Multi-empresa: al registrarse, el usuario crea una organización (`orgId` en su perfil)
- `JoinOrgScreen` permite unirse a una empresa existente por código
- `UserRole`: OWNER, ADMIN, MANAGER, EMPLOYEE

### Productos (`Product`)
Campos principales: `id`, `name`, `description`, `category`, `imageUrl`, `active`, `salePrice`, `unit`, `weightPerUnit` (oz), `rawWeightGrams` (gramos en crudo, para calcular rendimiento de masa), `shelfLife`, `storageType`, `characteristics` (organolépticas: color corteza, miga, textura, forma, aroma, sabor), `notes`.

Categorías: Panadería, Repostería, Pastelería  
Las características organolépticas se guardan **desnormalizadas** como campos planos (no como objeto anidado).

### Ingredientes / Insumos (`Ingredient`)
Campos: `id`, `name`, `category`, `baseUnit`, `stockAlert`, `notes`, `active`, `purchaseFormats` (subcollección).

**No tiene `currentStock`** — el stock siempre se lee desde `BranchStock`. El formulario de alta/edición no tiene campo de stock; el stock se genera únicamente al registrar compras.

`PurchaseFormat`: cada presentación de compra del insumo (ej: saco 50 lb a Q200 con proveedor X). Almacenada en subcollección `/purchaseFormats/{formatId}`.

### Stock por Sucursal (`BranchStock`)
Campos: `id` (`{branchId}_{ingredientId}`), `branchId`, `branchName`, `ingredientId`, `ingredientName`, `currentStock`, `updatedAt`.

`IngredientListScreen` tiene un selector de sucursal (chips). Al elegir "Sucursal A" se muestra el stock de ESA sucursal para cada insumo — sin sumar entre sucursales. Si el usuario solo tiene una sucursal asignada, se auto-selecciona sin mostrar el selector.

### Compras (`Purchase` / `PurchaseLine`)
Flujo de 3 pasos: **sucursal destino → proveedor → cantidades por insumo**.

Al confirmar, por cada línea:
1. Se actualiza `BranchStock` (delta positivo)
2. Se registra un `StockMovement` en la subcollección `movements/` del ingrediente (Kardex)

Filtros de acceso: solo sucursales con `BranchCapability.CAN_PRODUCE` o `IS_WAREHOUSE`. El dueño ve todas; colaboradores solo sus sucursales asignadas (`User.canAccessBranch()`).

Permiso requerido: `Permission.RECEIVE_STOCK`.

### Proveedores (`Supplier`)
CRUD simple. Campos: nombre, contacto, teléfono, email, dirección, notas.

### Recetas (`Recipe`) — módulo más complejo

**Categorías de receta** (determinan la estructura del formulario):
- `Pan Principal` → tiene etapas + lista de productos que genera
- `Cubierta` → preparación única (una sola etapa implícita), sin productos yield
- `Relleno` → igual que Cubierta

**Tipos de etapa** (`RecipeStageType`): PREFERMENTO, MASA_PRINCIPAL, RELLENO, CUBIERTA, OTRO  
*Solo PREFERMENTO y MASA_PRINCIPAL aparecen en el formulario para Pan Principal.*

**Máximo 2 etapas por receta de Pan Principal** (una Masa Principal + opcionalmente un Pre-fermento). Lógica en `StageDialog`: cuando ya existe una etapa, solo se puede agregar la otra; el tipo se fuerza automáticamente.

**Modos de ingreso de ingredientes**:
- `PERCENTAGE` — porcentaje del panadero directo (harina = 100%)
- `WEIGHT` — pesos reales; el sistema convierte a % automáticamente al guardar

**Fórmula de conversión peso → %:**
```
porcentaje_ingrediente = (peso_ingrediente / peso_base) × 100
```

**Instrucciones paso a paso**: almacenadas como string delimitado por `\n` en el campo `instructions` de cada `RecipeStage`. El UI las divide/une con `split("\n")` / `joinToString("\n")`.

**Estructura interna (Firestore)**:
```
recipes/{recipeId}
  name, category, inputMode, stageCount, yieldCount, active, notes, ...

recipes/{recipeId}/stages/{stageId}
  id, name, order, type, instructions (newline-delimited), restTimeMinutes

recipes/{recipeId}/components/{compId}
  id, stageId, ingredientId, ingredientName, ingredientUnit,
  percentage, isBase, order

recipes/{recipeId}/yields/{yieldId}
  id, productId, productName
```

---

## Inyección de Dependencias (Koin)

### Patrón estándar

```kotlin
// nonWasmMain/di/AppModule.kt
val appModule = module {
    single { Firebase.firestore }
    single<AuthRepository> { FirebaseAuthRepository(get(), get()) }
    single<IngredientRepository> { FirebaseIngredientRepository(get()) }
    single<SupplierRepository> { FirebaseSupplierRepository(get()) }
    single<RecipeRepository> { FirebaseRecipeRepository(get()) }
    single<BranchRepository> { FirebaseBranchRepository(get()) }
    single<PurchaseRepository> { FirebasePurchaseRepository(get()) }
    viewModel { RecipeViewModel(get()) }
    viewModel { IngredientViewModel(get(), get(), get()) }  // ingredient, recipe, branch repos
    viewModel { PurchaseViewModel(get(), get(), get(), get()) }  // purchase, ingredient, supplier, branch
    viewModel { SupplierViewModel(get()) }
    viewModel { AuthViewModel(get(), get()) }
}

// androidMain/di/AndroidModule.kt  (depende de contexto Android)
val androidModule = module {
    single<GoogleAuthHandler> { AndroidGoogleAuthHandler(androidContext()) }
    single { Firebase.storage }
    single<ProductRepository> { FirebaseProductRepository(get(), get()) }
    viewModel { ProductViewModel(get()) }
}
```

`ProductRepository` está en `androidModule` (no en `appModule`) porque `FirebaseProductRepository` necesita `FirebaseStorage`, que requiere contexto Android.

### ViewModels compartidos en navegación

Los ViewModels se instancian una sola vez a nivel de `AppNavigation` y se pasan a cada pantalla:

```kotlin
// AppNavigation.kt
fun AppNavigation(
    recipeViewModel: RecipeViewModel = koinViewModel(),
    productViewModel: ProductViewModel = koinViewModel(),
    ...
)
```

Esto permite que la lista de recetas no se recargue al navegar atrás desde el detalle.

---

## Navegación

```kotlin
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object RecipeList   : Screen("recipes")
    object RecipeDetail : Screen("recipes/detail/{recipeId}")
    object RecipeForm   : Screen("recipes/form?recipeId={recipeId}")
    object ProductList  : Screen("products")
    object ProductDetail: Screen("products/detail/{productId}")
    object ProductForm  : Screen("products/form?productId={productId}")
    // ... ingredientes, proveedores, auth
}
```

`RecipeForm` y `ProductForm` reciben `recipeId`/`productId` como query param nullable — si es `null`, es alta; si tiene valor, es edición.

---

## Gestión de Estado

### Patrón UiState

Cada módulo tiene un `data class XxxUiState` con:
- `xxxList: List<Xxx>` — lista completa
- `selectedXxx: Xxx?` — ítem seleccionado (detalle/edición)
- `isLoading: Boolean` — spinner lista
- `isDetailLoading: Boolean` — spinner detalle (evita que el spinner de lista aparezca al abrir detalle)
- `error: String?`
- `isSaved: Boolean` — trigger para navegar atrás tras guardar
- `isDeleted: Boolean` — trigger para navegar atrás tras eliminar

### Formularios: remember vs rememberSaveable

```kotlin
// Primitivas → rememberSaveable (sobreviven rotación de pantalla)
var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }

// Listas y objetos complejos → remember (no son Parcelable)
var stages by remember { mutableStateOf(existing?.stages?.map { ... } ?: emptyList()) }
```

### Formulario de edición: patron de dos composables

El formulario de edición usa dos composables para evitar que `remember` inicialice con `null` antes de cargar:

```kotlin
fun RecipeFormScreen(...) {
    // Espera a que cargue el ítem
    if (needsLoad && state.isDetailLoading) { CircularProgressIndicator(); return }
    
    // Solo renderiza cuando el ítem ya está disponible
    RecipeFormContent(existing = selectedRecipe, ...)
}

fun RecipeFormContent(existing: Recipe?, ...) {
    // remember inicializa correctamente porque existing ya llegó
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
}
```

---

## Responsive Design

`AdaptiveContainer` (en `ui/AdaptiveLayout.kt`) centra y limita el contenido a 800dp de ancho en pantallas grandes. Se aplica en todos los formularios.

```kotlin
AdaptiveContainer(modifier = Modifier.padding(padding)) {
    Column(...) { /* contenido del formulario */ }
}
```

En pantallas pequeñas (< 800dp) ocupa todo el ancho disponible.

---

## Colores y Diseño (HomeScreen)

Paleta principal:
- **Deep Sea Blue** `#123456` — color primario, TopAppBar, textos principales
- **Warm Ivory** `#FDFBF7` — fondo general
- **Burnt Orange** `#CC5500` — acento sutil (notificaciones, borde avatar, línea de sección)

El HomeScreen tiene:
- Saludo dinámico con nombre y rol del usuario
- Gráfica de barras de ventas de la semana (datos mock por ahora)
- Carrusel de accesos directos (Pedidos, Recetas, Gastos, Ventas)
- Grid de módulos (Productos, Insumos, Proveedores, Recetas, Inventario)

---

## Estado Actual del Proyecto

### Completado
- [x] Autenticación (email/password + Google) con multi-empresa
- [x] CRUD completo de Proveedores
- [x] CRUD completo de Ingredientes (con formatos de compra en subcollección)
- [x] CRUD completo de Productos (con imagen en Storage, campos organolépticos, peso en crudo)
- [x] CRUD completo de Recetas:
  - Categorías: Pan Principal, Cubierta, Relleno
  - Pan Principal: hasta 2 etapas (Pre-fermento + Masa Principal), máx 1 de cada tipo
  - Cubierta/Relleno: formulario simplificado (ingredientes directos + pasos)
  - Instrucciones paso a paso numeradas
  - Modo porcentaje y modo por peso (conversión automática)
  - Selector de productos con búsqueda + scroll
- [x] Navegación completa entre módulos
- [x] HomeScreen con dashboard
- [x] Responsive design con AdaptiveContainer
- [x] Módulo de Compras (ingreso de materia prima por sucursal):
  - Stock por sucursal (`BranchStock`), no global
  - Kardex de movimientos por insumo (`StockMovement`)
  - Filtro de acceso por capacidad de sucursal (`CAN_PRODUCE`, `IS_WAREHOUSE`) y por usuario
  - Tab Inventario en detalle de insumo: desglose real por sucursal
  - Tab Kardex en detalle de insumo: tabla de movimientos reales
  - Permiso `RECEIVE_STOCK` implementado

### Pendiente / Futuro
- [ ] Módulo de Inventario / Mermas diarias
- [ ] Módulo de Pedidos
- [ ] Módulo de Ventas / Punto de venta
- [ ] Módulo de Gastos
- [ ] Cálculo automático de costo de producto (basado en receta + precios de insumos)
- [ ] Asociar sub-receta a producto terminado (ej: Gusanito tiene concha → referencia a Masa Concha)
- [ ] Módulo de Producción diaria (calcular cuántos panes salen de cada lote usando `rawWeightGrams`)
- [ ] Datos reales en la gráfica de ventas del HomeScreen
- [ ] Notificaciones de stock mínimo
- [ ] Roles y permisos por módulo (RECEIVE_STOCK ya implementado)
- [ ] Versión iOS y Web

---

## Notas y Decisiones de Diseño

### Por qué la Masa Concha NO va dentro de la receta de Gusanito
La Masa Concha se prepara por separado (distinto horario) y sirve para varios tipos de pan. La asociación "este pan lleva concha" pertenece al **producto terminado**, no a la receta de masa. Esta relación es una característica futura en el modelo de `Product`.

### Por qué yields no tienen cantidad ni unidad
La cantidad de panes que salen de una masa varía cada día según el tamaño que decide el panadero. El yield solo dice *qué tipos de pan* pueden salir de esa masa. La cantidad real se maneja en el módulo de Producción diaria (futuro).

### Por qué stageCount y yieldCount están en el documento raíz de Recipe
Son contadores desnormalizados para mostrar badges en la lista de recetas sin necesidad de cargar las subcollecciones. Se actualizan cada vez que se guarda la receta.

### Por qué components está a nivel de receta y no bajo stage
Firestore no recomienda subcollecciones con más de 2 niveles de profundidad. Los componentes tienen un campo `stageId` para saber a cuál etapa pertenecen.

### Por qué Ingredient no tiene currentStock
El stock siempre es por sucursal. Un valor global en el modelo no tiene sentido en un sistema multi-sucursal. El stock se lee desde `BranchStock` y solo existe cuando se registra una compra. Antes de la primera compra, el insumo aparece "Sin stock registrado".

---

## Comandos Útiles

```bash
# Compilar Android (verificar errores sin instalar)
./gradlew composeApp:compileDebugKotlin

# Instalar en dispositivo/emulador
./gradlew composeApp:installDebug

# Limpiar build
./gradlew clean
```
