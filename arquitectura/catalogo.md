# Catálogo: productos, insumos, recetas, preparaciones

> Detalle movido desde CLAUDE.md. Leer antes de tocar este módulo.

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
