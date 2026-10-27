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

### Bizcochos: masa + molde, contados por unidad (17 oct 2026)

El repostero de Calvario hornea días antes porque decorar el mismo día sale mal. Ve lo que le
piden, lo que tiene frío y decide cuánto hacer ("1 plancha, 6 de frutas, 6 de tres leches"); el
sistema le da la masa a batir. Lógica pura en `model/BakePlan.kt` (`BakePlanTest`).

- **Dos usos nuevos de `Preparation`**: `BATTER` (masa de bizcocho: tiene insumos, sin saldo
  propio) y `BASE` (el molde que se cuenta: sin insumos, con `batterPreparationId` +
  `batterGramsPerUnit` + `stockMinimumUnits`). El form pide la masa por molde en **onzas**
  (frutas = 22 oz) y la guarda en gramos; se puede cambiar cuando quieran.
- **Masa + molde, como el pan agrupado por masa**: plancha y bizcocho de frutas son de la misma
  masa de vainilla → `batterMixes` los bate juntos: masa total = Σ moldes × masa por molde, receta
  escalada = insumos × masa total ÷ peso de la receta de la masa.
- **El saldo es en unidades** (`PreparationBatch.producedUnits/remainingUnits`, `isCounted`). Un
  lote por molde y horneada, id `{bakeId}_{moldeId}`: ese id fijo es la guarda contra anotar la
  misma horneada dos veces (lotes primero, insumos después). `batterGramsPerUnit` del lote es el
  snapshot de esa tanda. `producedWeightGrams` = masa echada, solo referencia.
- **Hoja de horneado** (`BakeSheetScreen`, FAB de Lotes de preparación → "Hornear bizcochos"):
  pedidos a la planta para hoy/mañana/pasado (no cancelados) → bizcochos que piden (`baseDemand`),
  hay fríos (`baseStock`, con la edad del más viejo), sugerencia = pedido + mínimo − hay, en
  enteros. "Voy a hacer" lo decide él y vive en el ViewModel (sobrevive a irse a hornear). "Ya
  salieron" pide cuántos salieron de cada molde: entran esos; se descuenta la masa batida
  completa (lo planeado), Kardex `PRODUCCION` con referencia `bakeId`. Si el inventario no
  alcanza, avisa pero no bloquea (el bizcocho ya está hecho).
- **El pastel consume bizcochos** con un adorno contado: `ProductDecoration.unitsPerProduct`
  (pastel de frutas 1; pastelito "salen 24 de 1" → 1/24 de plancha). Va al snapshot
  (`ProductionLotDecoration.unitsPerProduct`, arreglo `decoUnitsPerProduct`) y `completeLot`
  saca de los más viejos de la planta (`consumeUnits` → `fifoUnitTakes`), sobre lo amasado. Si
  faltan, avisa "faltaron N", no falla. No se elige lote en el form del lote.
- **Costo**: el bizcocho cuesta su parte de la masa (`unitsPerProduct × masa por molde × Q/g de
  la masa`). De paso: `MaterialCostLoader` no hidrataba los insumos de las preparaciones, así que
  todo adorno salía "sin precio"; ahora lee completas las que usa algún producto.
- Las masas y bizcochos no salen en "Producir lote" (por peso) ni en los pickers de esponja/adorno
  por gramos.
- Pendiente: los pedidos de "hoy" cuyo lote de pasteles ya se completó cuentan doble (ya se
  descontaron); por eso la hoja abre en "Mañana".
- **Dentro del lote de producción (19 oct 2026)**, pedido por el dueño: "como la esponja".
  Los pasteles **sin receta** que llevan bizcocho contado se agrupan en la lista del lote por la
  **masa del bizcocho** (clave `bake:{batterId}`, `batterOfLine`), como el pan por su receta: la
  porción de tres leches y el selva negra de la misma premezcla salen juntos; los de vainilla en
  su propio grupo. Al tocar el grupo (lote abierto), la primera vez se abre `BakePlanDialog`:
  cada molde con piden / hay fríos / tener, "Voy a hacer" grande con − / + y "Sugerido", y la
  masa total en vivo. "Ver receta" → del otro lado `ScaledRecipePanel` (el mismo del pan) con la
  masa escalada (`bakeRecipeView`) y arriba `BakePlanSummary` ("Cambiar" vuelve al diálogo, "Ya
  salieron del horno" anota **solo esa masa**, `finishBake(batterId)`). Lo que piden sale de las
  líneas del lote (`lotBaseDemand`: snapshot de adornos × `kneadedQuantity`, lo mismo que
  descuenta `completeLot`). Instancia propia de `BakeSheetViewModel` (`AppViewModels.lotBake`,
  modo `lotId`); la hoja "Hornear bizcochos" queda para planear días adelante. Con el lote
  cerrado el grupo sigue, pero el panel solo dice que ya se descontaron (contaría doble).
- **Un pastel sobre bizcocho NO va en "Productos que genera" de ninguna receta**: la masa ya la
  pone el bizcocho; con receta se descontaría dos veces. `functions/tools/remove-cake-from-recipes.js`
  lo saca con respaldo. Sin receta no se le pide peso en crudo (`linesMissingRawWeight`).
- **"Porciones por bizcocho"** (antes "Salen de 1") en el adorno del producto: cuántos de este
  producto salen de UN bizcocho. El form del bizcocho dice cuántos moldes alcanza una receta de
  su masa (peso de la receta ÷ onzas por molde): nadie teclea ese número.
- **`Preparation.branchIds`** ("Plantas que la hacen", chips en el form). Vacío = todas, para que
  nada desaparezca mientras se asignan. Filtra la lista de Preparaciones (por plantas a las que
  entra el usuario), el form de Lotes de preparación, la hoja y el lote (`isMadeIn`).

### Recetas — receta plana, solo pan principal

**Sin etapas, sin tipos.** Lista plana de componentes + pasos + productos que genera. `RecipeStage`/`RecipeStageType` solo para deserializar legacy. Esponja NO va en receta — vive en Preparaciones y se declara en el lote.

**Campos clave**: `components: List<RecipeComponent>` plano, `steps: List<RecipeStep>` plano, `stages` solo legacy. Helpers `allComponents`/`allSteps` aplanan legacy si necesario.

**Modos de ingreso:**
- `PERCENTAGE` — % del panadero (harinas base suman 100%).
- `WEIGHT` — pesos reales; al guardar convierte a % usando Σ pesos base como 100%. `RecipeComponent.inputQuantity` guarda la cantidad tal como se tecleó (en `ingredientUnit`) — `percentage` sigue siendo el dato canónico, pero sin `inputQuantity` reabrir la receta releía los % como si fueran pesos y los degradaba en cada guardado. Recetas viejas sin ese campo se abren en modo porcentaje con un aviso, en vez de corromperse.

**Multi-base**: varios componentes `isBase`. `validateBase`: WEIGHT → Σ pesos base > 0; PERCENTAGE → Σ% base = 100 (±0.5).

**Pasos**: `RecipeStep(id, stageId="", order, text, photoUrl)`. Foto opcional vía `RecipeImageUploader` (split por plataforma). En recetas nuevas `stageId=""`.

**Receta por tanda (`inputMode = "BATCH"`, 20 oct 2026)** — torta de leche: no es masa de pan (sin harina base ni %), es una bandeja con productos del inventario adentro ("6 panes molde, 4 oz aceite, 1 lt leche… salen 35 pedazos"). Pedido del dueño: entrarla en el form normal de Recetas, no en el producto ("Transformar" quedaba incómodo). Lógica pura en `model/BatchRecipe.kt` (`BatchRecipeTest`).
- `Recipe.batchYield` (35) y `batchUnitName` ("bandeja"). En el doc raíz. **Siempre bandeja entera**: se probó media bandeja con − / + en el pedido y el dueño la descartó el mismo día — no la vuelvas a agregar.
- Componentes con cantidad real de una tanda en `inputQuantity`; `RecipeComponent.productId/Name` = producto del inventario (pan molde). `percentage` = 0, sin base.
- **Pedido**: la sucursal pide los pedazos que quiera (caja normal), porque hay tiendas que no quieren tantos.
- **Al armar el lote**: `checkRecipeBatchMinimums` trata la tanda como un mínimo — lo pedido se completa a bandejas enteras (`BatchSpec.roundUp`, 50 → 70) y sale el mismo aviso/diálogo del mínimo de amasada (`MinimumBatchDecisionDialog`, `RecipeBatchCheck.batch`): la encargada reparte el sobrante entre las sucursales que la pidieron, lo que no reparta queda en la planta (`plantRetainedQuantity`), o anula. En `RecipeBatchCheck` con `batch` los campos `currentFlourGrams`/`minFlourGrams` son **piezas**; los textos salen de `progressText()`/`shortfallText()`.
- **Lote**: sin peso en crudo (`linesMissingRawWeight` lo excluye), panel con "2 bandejas · salen 70" y cantidades en su unidad (`ScaledRecipeRow.displayQuantity`). Bandejas = `batchesFor` (la más cercana: contar 34 o 36 sigue siendo una). `completeLot` agrupa por receta, descuenta insumos al mismo agregado y el pan molde del terminado de la planta (`TRANSFORMACION` + referencia `PRODUCTION_LOT`, a su costo promedio; si no alcanza avisa, no bloquea).
- **Costo**: `productMaterialCost` = (insumos + productos a su costo de receta) ÷ `batchYield`.
- Pendiente: en planta de 2 turnos el reparto por turnos parte la torta y cada turno pide completar su bandeja.

**Legacy**: si existe `stages/` con `stageId≠""`, `FirebaseRecipeRepository.getRecipe` hidrata `recipe.stages` Y aplana en `components`/`steps`. Al editar y guardar, persiste plana; los docs viejos de `stages/` quedan huérfanos.

## Stock en transacción (16 oct 2026)

`FirebaseFinishedStockRepository.applyMovement` y `FirebaseIngredientRepository.updateBranchStock`
leían el saldo, calculaban y escribían por separado: dos cajas vendiendo el mismo pan a la vez
(o una venta mientras planta recibe/produce) leían el mismo saldo y la segunda escritura pisaba
a la primera. Ahora ambos van en `firestore.runTransaction`:

- **Pan terminado**: lectura del saldo, costo promedio, saldo nuevo y movimiento del Kardex en
  la MISMA transacción (quedan los dos o ninguno). El id del movimiento se fija fuera de la
  función porque Firestore la reintenta si el documento cambió.
- **Insumos**: el saldo va en transacción y la función devuelve el saldo previo leído dentro;
  los callers que escriben Kardex (venta, anulación, vales de planta, lotes de preparación,
  producción) lo usan como `balanceBefore` en vez de una lectura aparte.
- **Lotes FEFO** siguen fuera de la transacción (necesitan una consulta y las transacciones del
  SDK de cliente no consultan). Si fallan, el saldo ya quedó bien; solo se pierde precisión en
  el aviso de caducidad — mismo criterio que antes.
- Las transacciones requieren conexión. No se pierde nada: el cobro ya esperaba la confirmación
  del servidor en cada escritura, así que sin internet ya se quedaba esperando.
