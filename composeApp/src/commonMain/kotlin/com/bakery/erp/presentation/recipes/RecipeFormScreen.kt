package com.bakery.erp.presentation.recipes

import com.bakery.erp.ui.BrandTopAppBar

import com.bakery.erp.util.fmt
import com.bakery.erp.util.formatBakerWeight
import com.bakery.erp.presentation.ingredients.IngredientPickerDialog
import com.bakery.erp.model.usableInRecipes
import com.bakery.erp.model.GRAMS_PER_POUND as GRAMS_PER_LB_RECIPE
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Product
import com.bakery.erp.model.toGramsOrNull
import com.bakery.erp.model.Recipe
import com.bakery.erp.model.RecipeComponent
import com.bakery.erp.model.RecipeStep
import com.bakery.erp.model.RecipeYield
import com.bakery.erp.model.RECIPE_MODE_BATCH
import com.bakery.erp.presentation.pos.ProductPickerDialog
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.ingredients.IngredientViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.ImagePickerButton
import com.bakery.erp.ui.LoadingOverlay
import kotlin.random.Random

// ── Tipos internos del formulario ─────────────────────────────────────────────

private data class ComponentEntry(
    val id: String,
    val ingredientId: String = "",
    val ingredientName: String = "",
    val ingredientUnit: String = "",
    val rawInput: String = "",
    val isBase: Boolean = false,
    val order: Int = 0,
    /** Receta por tanda: producto del inventario (pan molde) en vez de insumo. */
    val productId: String = "",
    val productName: String = ""
)

private data class StepEntry(
    val id: String,
    val text: String = "",
    val photoUrl: String? = null,
    val newBytes: ByteArray? = null
)

private data class YieldEntry(
    val id: String,
    val productId: String = "",
    val productName: String = ""
)

// ── Conversiones desde modelos de dominio ─────────────────────────────────────

private fun RecipeStep.toStepEntry() = StepEntry(
    id = id,
    text = text,
    photoUrl = photoUrl,
    newBytes = null
)

/**
 * 4 decimales, no 2: con 2, un insumo de traza guardado en 0.625% volvía al campo
 * como 0.63 y ese redondeo se persistía en el siguiente guardado. La receta se
 * degradaba de a poco cada vez que se editaba.
 */
private fun Double.asInputText(): String =
    if (this == toLong().toDouble()) toLong().toString()
    else fmt(4).trimEnd('0').trimEnd('.')

/**
 * En modo peso el campo debe mostrar la cantidad que se tecleó ([inputQuantity]),
 * no el porcentaje: reinterpretar un % como peso en la unidad del insumo degrada
 * la receta en cada guardado. Recetas viejas (sin `inputQuantity`) caen al
 * porcentaje, que es lo único que tienen.
 */
private fun RecipeComponent.toComponentEntry(inputMode: String) = ComponentEntry(
    id = id,
    ingredientId = ingredientId,
    ingredientName = ingredientName,
    ingredientUnit = ingredientUnit,
    rawInput = if ((inputMode == "WEIGHT" || inputMode == RECIPE_MODE_BATCH) && inputQuantity > 0.0)
                   inputQuantity.asInputText()
               else percentage.asInputText(),
    isBase = isBase,
    order = order,
    productId = productId,
    productName = productName
)

private fun RecipeYield.toYieldEntry() = YieldEntry(
    id = id,
    productId = productId,
    productName = productName
)

/** Construye los RecipeStep persistibles (descarta pasos totalmente vacíos). */
private fun List<StepEntry>.toRecipeSteps(): List<RecipeStep> =
    filter { it.text.isNotBlank() || it.photoUrl != null || it.newBytes != null }
        .mapIndexed { i, s ->
            RecipeStep(id = s.id, stageId = "", order = i, text = s.text.trim(), photoUrl = s.photoUrl)
        }

// ── Conversión peso → porcentaje ──────────────────────────────────────────────

/**
 * Peso en gramos de lo que se escribió en la fila, o null si no se puede saber
 * (insumo contado sin peso unitario). [unitWeightById] trae `unitWeightGrams`
 * de cada insumo: es lo que convierte "6 unidades" en "300 g" y permite que el
 * huevo participe del porcentaje del panadero, que es una razón entre pesos.
 */
private fun ComponentEntry.gramsOrNull(unitWeightById: Map<String, Double>): Double? =
    toGramsOrNull(
        rawInput.replace(',', '.').toDoubleOrNull() ?: 0.0,
        ingredientUnit,
        unitWeightById[ingredientId] ?: 0.0
    )

private fun toPercentageComponents(
    components: List<ComponentEntry>,
    inputMode: String,
    unitWeightById: Map<String, Double>
): List<RecipeComponent> {
    if (components.isEmpty()) return emptyList()
    // Por tanda no hay %: se guarda lo que se escribió, para una tanda entera.
    if (inputMode == RECIPE_MODE_BATCH) return components.mapIndexed { i, c ->
        RecipeComponent(
            id = c.id, stageId = "",
            ingredientId = if (c.productId.isNotEmpty()) "" else c.ingredientId,
            ingredientName = if (c.productId.isNotEmpty()) c.productName else c.ingredientName,
            ingredientUnit = c.ingredientUnit,
            inputQuantity = c.rawInput.replace(',', '.').toDoubleOrNull() ?: 0.0,
            productId = c.productId, productName = c.productName,
            isBase = false, order = i
        )
    }
    return if (inputMode == "WEIGHT") {
        // El 100% = suma de pesos de las harinas marcadas como base.
        val baseNorm = components.filter { it.isBase }
            .sumOf { it.gramsOrNull(unitWeightById) ?: 0.0 }
        if (baseNorm == 0.0) return emptyList()
        components.mapIndexed { i, c ->
            val norm = c.gramsOrNull(unitWeightById) ?: 0.0
            RecipeComponent(
                id = c.id, stageId = "",
                ingredientId = c.ingredientId, ingredientName = c.ingredientName,
                ingredientUnit = c.ingredientUnit,
                percentage = (norm / baseNorm) * 100.0,
                inputQuantity = c.rawInput.replace(',', '.').toDoubleOrNull() ?: 0.0,
                isBase = c.isBase, order = i
            )
        }
    } else {
        components.mapIndexed { i, c ->
            RecipeComponent(
                id = c.id, stageId = "",
                ingredientId = c.ingredientId, ingredientName = c.ingredientName,
                ingredientUnit = c.ingredientUnit,
                percentage = c.rawInput.replace(',', '.').toDoubleOrNull() ?: 0.0,
                isBase = c.isBase, order = i
            )
        }
    }
}

/**
 * Recalcula [ComponentEntry.rawInput] al cambiar el modo de ingreso — `rawInput`
 * es un solo campo que significa peso en modo WEIGHT y % en modo PERCENTAGE, así
 * que cambiar el modo sin tocar el número deja el valor de un modo interpretado
 * como si fuera del otro (500g de harina pasa a leerse como "500%"). Antes no se
 * recalculaba nada acá, que era exactamente el bug.
 *
 * WEIGHT → PERCENTAGE: mismo cálculo que al guardar (peso de cada uno ÷ suma de
 * las harinas base × 100), así lo que se ve en pantalla ya es el % real.
 *
 * PERCENTAGE → WEIGHT: no hay inversa posible — un % es una razón, no un peso
 * absoluto, así que no hay forma de "adivinar" cuántos gramos representa sin que
 * la encargada diga el tamaño real de la amasada. Se limpia el campo para forzar
 * a reescribir pesos reales en vez de dejar el % viejo mal interpretado como peso.
 */
private fun convertComponentsForModeSwitch(
    components: List<ComponentEntry>,
    fromMode: String,
    toMode: String,
    unitWeightById: Map<String, Double>
): List<ComponentEntry> {
    // Entre "por peso" y "por tanda" las cantidades son reales en los dos: se conservan.
    // Los productos del inventario solo existen en la receta por tanda.
    if (toMode == RECIPE_MODE_BATCH) {
        return if (fromMode == "WEIGHT") components else components.map { it.copy(rawInput = "") }
    }
    val ingredientsOnly = components.filter { it.productId.isEmpty() }
    if (toMode == "WEIGHT") {
        return if (fromMode == RECIPE_MODE_BATCH) ingredientsOnly
        else ingredientsOnly.map { it.copy(rawInput = "") }
    }
    if (fromMode == RECIPE_MODE_BATCH) return ingredientsOnly.map { it.copy(rawInput = "") }
    val baseNorm = components.filter { it.isBase }.sumOf { it.gramsOrNull(unitWeightById) ?: 0.0 }
    if (baseNorm <= 0.0) return components.map { it.copy(rawInput = "") }
    return components.map { c ->
        val norm = c.gramsOrNull(unitWeightById) ?: 0.0
        c.copy(rawInput = ((norm / baseNorm) * 100.0).asInputText())
    }
}

// ── Validación de ingredientes base ───────────────────────────────────────────

private fun validateBase(
    comps: List<ComponentEntry>,
    inputMode: String,
    unitWeightById: Map<String, Double>
): String? {
    if (comps.size < 2) return "Una receta debe tener al menos 2 ingredientes"
    val missing = comps.filter {
        val v = it.rawInput.replace(',', '.').toDoubleOrNull()
        v == null || v <= 0.0
    }
    if (missing.isNotEmpty()) {
        val label = if (inputMode == "PERCENTAGE") "porcentaje" else "cantidad"
        val names = missing.joinToString(", ") { it.ingredientName.ifBlank { "(sin nombre)" } }
        return "Falta $label en: $names"
    }
    val bases = comps.filter { it.isBase }
    if (bases.isEmpty())
        return "Marca al menos un ingrediente como base (harina = 100%)"
    return if (inputMode == "WEIGHT") {
        // Un insumo que se cuenta y no tiene peso unitario no se puede convertir a
        // gramos. Antes entraba como "6 gramos" y salía con 0.2% — falla ruidosa.
        val noWeight = comps.filter { it.gramsOrNull(unitWeightById) == null }
        if (noWeight.isNotEmpty()) {
            val names = noWeight.joinToString(", ") { it.ingredientName.ifBlank { "(sin nombre)" } }
            return "Definí el peso de una unidad en Insumos para: $names. " +
                "Sin ese dato no se puede calcular el porcentaje por peso."
        }
        val sum = bases.sumOf { it.gramsOrNull(unitWeightById) ?: 0.0 }
        if (sum <= 0.0) "El peso de las harinas base debe ser mayor a 0" else null
    } else {
        val sum = bases.sumOf { it.rawInput.replace(',', '.').toDoubleOrNull() ?: 0.0 }
        if (kotlin.math.abs(sum - 100.0) > 0.5)
            "Los % de las harinas base deben sumar 100 (suman ${sum.fmt(1)})" else null
    }
}

/**
 * Receta por tanda: cantidades > 0, lo que rinde, y cada insumo en una unidad que se pueda
 * pasar a peso — si no, al completar el lote no hay forma de descontarlo del inventario.
 */
private fun validateBatch(
    comps: List<ComponentEntry>,
    fullYield: Double?,
    unitWeightById: Map<String, Double>
): String? {
    if (comps.isEmpty()) return "Agrega lo que lleva la receta"
    val missing = comps.filter { (it.rawInput.replace(',', '.').toDoubleOrNull() ?: 0.0) <= 0.0 }
    if (missing.isNotEmpty())
        return "Falta cantidad en: " + missing.joinToString(", ") { it.productName.ifEmpty { it.ingredientName } }
    val noWeight = comps.filter { it.productId.isEmpty() && it.gramsOrNull(unitWeightById) == null }
    if (noWeight.isNotEmpty())
        return "Definí el peso de una unidad en Insumos para: " +
            noWeight.joinToString(", ") { it.ingredientName } + ". Sin eso no se puede descontar."
    if (fullYield == null || fullYield <= 0.0) return "Indica cuántos salen de una tanda"
    return null
}

// ── Pantalla principal ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeFormScreen(
    recipeId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: RecipeViewModel,
    ingredientViewModel: IngredientViewModel,
    productViewModel: ProductViewModel,
    onNavigateToProducts: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""

    LaunchedEffect(recipeId, companyId) {
        if (companyId.isNotEmpty()) {
            if (recipeId != null) viewModel.loadRecipe(companyId, recipeId)
            ingredientViewModel.loadIngredients(companyId)
            productViewModel.loadProducts(companyId)
        }
    }

    val selectedRecipe = state.selectedRecipe?.takeIf { it.id == recipeId }
    val needsLoad = recipeId != null && selectedRecipe == null

    if (needsLoad && state.isDetailLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    RecipeFormContent(
        recipeId = recipeId,
        existing = if (recipeId != null) selectedRecipe else null,
        onNavigateBack = onNavigateBack,
        companyId = companyId,
        viewModel = viewModel,
        ingredientViewModel = ingredientViewModel,
        productViewModel = productViewModel,
        state = state,
        onNavigateToProducts = onNavigateToProducts
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeFormContent(
    recipeId: String?,
    existing: Recipe?,
    onNavigateBack: () -> Unit,
    companyId: String,
    viewModel: RecipeViewModel,
    ingredientViewModel: IngredientViewModel,
    productViewModel: ProductViewModel,
    state: RecipeUiState,
    onNavigateToProducts: () -> Unit
) {
    val ingredientState by ingredientViewModel.uiState.collectAsState()
    val productState by productViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Form state (flat: components + steps, sin etapas) ─────────────────────
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    // Recetas por peso guardadas antes de que se persistiera `inputQuantity`: lo
    // único que queda de ellas son los porcentajes. Se abren en modo porcentaje
    // porque es lo que realmente tienen — reinterpretarlos como pesos en la unidad
    // de cada insumo los degradaba en cada guardado.
    val legacyWeightRecipe = existing != null && existing.inputMode == "WEIGHT" &&
        existing.allComponents.none { it.inputQuantity > 0.0 }
    var inputMode by rememberSaveable {
        mutableStateOf(
            if (legacyWeightRecipe) "PERCENTAGE" else existing?.inputMode ?: "PERCENTAGE"
        )
    }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    // Receta por tanda: cuánto rinde y cómo se llama la tanda.
    var batchYield by rememberSaveable {
        mutableStateOf(existing?.batchYield?.takeIf { it > 0.0 }?.asInputText() ?: "")
    }
    var batchUnitName by rememberSaveable { mutableStateOf(existing?.batchUnitName ?: "") }
    var showProductComponentPicker by remember { mutableStateOf(false) }
    var active by rememberSaveable { mutableStateOf(existing?.active ?: true) }
    // Mínimo de la amasadora, en libras (internamente gramos). Vacío = sin mínimo.
    var minBatchFlourLb by rememberSaveable {
        mutableStateOf(
            existing?.minBatchFlourGrams
                ?.takeIf { it > 0.0 }
                ?.let { (it / GRAMS_PER_LB_RECIPE).fmt(2).trimEnd('0').trimEnd('.') }
                ?: ""
        )
    }
    // Tiempo de amasado en minutos (internamente segundos). Vacío = sin timer.
    var kneadingMinutes by rememberSaveable {
        mutableStateOf(
            existing?.kneadingTimeSeconds
                ?.takeIf { it > 0 }
                ?.let { (it / 60.0).fmt(1).trimEnd('0').trimEnd('.') }
                ?: ""
        )
    }
    var components by remember {
        mutableStateOf(
            existing?.allComponents?.sortedBy { it.order }
                ?.map { it.toComponentEntry(if (legacyWeightRecipe) "PERCENTAGE" else existing.inputMode) }
                ?: emptyList<ComponentEntry>()
        )
    }
    // `unitWeightGrams` por insumo: el puente conteo → peso. Se relee de la lista
    // de insumos (no se congela en la fila) para que corregir el peso del huevo
    // en Insumos se refleje sin tener que rearmar la receta.
    val unitWeightById = remember(ingredientState.ingredientList) {
        ingredientState.ingredientList.associate { it.id to it.unitWeightGrams }
    }
    var steps by remember {
        mutableStateOf(
            existing?.allSteps?.sortedBy { it.order }?.map { it.toStepEntry() }
                ?: emptyList<StepEntry>()
        )
    }
    var yields by remember { mutableStateOf(existing?.yields?.map { it.toYieldEntry() } ?: emptyList<YieldEntry>()) }

    var showIngredientPicker by remember { mutableStateOf(false) }
    var showYieldDialog by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    // ── Bloqueo por falta de productos ────────────────────────────────────────
    // Una receta debe generar al menos un producto; si la panadería aún no tiene
    // productos creados, no tiene sentido crear recetas. `productsProbed` evita
    // que el diálogo parpadee antes de que termine la carga de productos.
    var productsProbed by remember { mutableStateOf(false) }
    LaunchedEffect(productState.isLoading) {
        if (productState.isLoading) productsProbed = true
    }
    val noProductsExist = recipeId == null && productsProbed &&
        !productState.isLoading && productState.productList.isEmpty()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(formError) {
        formError?.let { snackbarHostState.showSnackbar(it); formError = null }
    }

    fun buildRecipe(): Recipe {
        val builtComponents = toPercentageComponents(components, inputMode, unitWeightById)
        val builtSteps = steps.toRecipeSteps()
        val builtYields = yields.map { y ->
            RecipeYield(id = y.id, productId = y.productId, productName = y.productName)
        }
        return Recipe(
            id = existing?.id ?: "",
            name = name.trim().replaceFirstChar { it.uppercase() },
            description = description.trim().ifEmpty { null },
            category = "Pan Principal",
            inputMode = inputMode,
            notes = notes.trim().ifEmpty { null },
            minBatchFlourGrams = minBatchFlourLb.replace(',', '.').toDoubleOrNull()
                ?.takeIf { it > 0.0 && inputMode != RECIPE_MODE_BATCH }
                ?.let { it * GRAMS_PER_LB_RECIPE }
                ?: 0.0,
            kneadingTimeSeconds = kneadingMinutes.replace(',', '.').toDoubleOrNull()
                ?.takeIf { it > 0.0 }
                ?.let { (it * 60).toInt() }
                ?: 0,
            batchYield = if (inputMode == RECIPE_MODE_BATCH)
                batchYield.replace(',', '.').toDoubleOrNull() ?: 0.0 else 0.0,
            batchUnitName = if (inputMode == RECIPE_MODE_BATCH) batchUnitName.trim() else "",
            active = active,
            components = builtComponents,
            steps = builtSteps,
            yields = builtYields
        )
    }

    if (showIngredientPicker) {
        IngredientPickerDialog(
            // Los suministros de operación (cloro, papel) no van a una receta.
            ingredients = ingredientState.ingredientList.usableInRecipes(),
            alreadyAddedIds = components.map { it.ingredientId }.toSet(),
            keepAddingNote = true,
            onPick = { ing ->
                // Alta múltiple: NO cerramos el diálogo — el ingrediente queda marcado
                // como "ya agregado" y el usuario puede seguir seleccionando más.
                components = components + ComponentEntry(
                    id = generateEntryId(),
                    ingredientId = ing.id,
                    ingredientName = ing.name,
                    ingredientUnit = ing.allowedUnits.firstOrNull() ?: "",
                    isBase = inputMode != RECIPE_MODE_BATCH && components.none { it.isBase },
                    order = components.size
                )
            },
            onDismiss = { showIngredientPicker = false }
        )
    }

    if (showProductComponentPicker) {
        val yieldIds = yields.map { it.productId }.toSet()
        val usedIds = components.map { it.productId }.toSet()
        ProductPickerDialog(
            title = "¿Qué producto del inventario lleva?",
            // Lo que sale de esta receta no puede ser también su ingrediente.
            products = productState.productList
                .filter { it.active && it.id !in yieldIds && it.id !in usedIds }
                .sortedBy { it.name },
            stockOf = null,
            showSearch = true,
            onDismiss = { showProductComponentPicker = false },
            onSelect = { p ->
                components = components + ComponentEntry(
                    id = generateEntryId(),
                    ingredientUnit = p.unit.ifBlank { "unidad" },
                    order = components.size,
                    productId = p.id,
                    productName = p.name
                )
                showProductComponentPicker = false
            }
        )
    }

    if (showYieldDialog) {
        YieldDialog(
            existingYieldIds = yields.map { it.productId }.toSet(),
            products = productState.productList,
            onConfirm = { nuevos ->
                yields = yields + nuevos
                showYieldDialog = false
            },
            onDismiss = { showYieldDialog = false }
        )
    }

    if (noProductsExist) {
        NoProductsDialog(
            onGoToProducts = onNavigateToProducts,
            onBack = onNavigateBack
        )
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = {
                    Text(
                        if (recipeId == null) "Nueva Receta" else "Editar Receta",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                // Agrega espacio inferior igual al alto del teclado para que se pueda
                // hacer scroll hasta el final del formulario (incluidos los botones)
                // cuando el IME está abierto.
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader("Información Básica")

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("Nombre *") },
                placeholder = { Text("Ej: Masa de Pan Francés") },
                colors = recipeFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                isError = nameError,
                supportingText = if (nameError) ({ Text("El nombre es requerido") }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 3
            )

            Column {
                Text("Modo de ingreso de ingredientes", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InputModeButton(
                        label = "Porcentaje",
                        selected = inputMode == "PERCENTAGE",
                        onClick = {
                            if (inputMode != "PERCENTAGE") {
                                components = convertComponentsForModeSwitch(components, inputMode, "PERCENTAGE", unitWeightById)
                                inputMode = "PERCENTAGE"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    InputModeButton(
                        label = "Por peso",
                        selected = inputMode == "WEIGHT",
                        onClick = {
                            if (inputMode != "WEIGHT") {
                                components = convertComponentsForModeSwitch(components, inputMode, "WEIGHT", unitWeightById)
                                inputMode = "WEIGHT"
                                formError = "Los % no traen un peso absoluto — reescribe los pesos reales de esta amasada."
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    InputModeButton(
                        label = "Por tanda",
                        selected = inputMode == RECIPE_MODE_BATCH,
                        onClick = {
                            if (inputMode != RECIPE_MODE_BATCH) {
                                components = convertComponentsForModeSwitch(components, inputMode, RECIPE_MODE_BATCH, unitWeightById)
                                    .map { it.copy(isBase = false) }
                                inputMode = RECIPE_MODE_BATCH
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = when (inputMode) {
                        "PERCENTAGE" -> "Ingresa porcentajes del panadero (base = 100%)"
                        RECIPE_MODE_BATCH -> "Para lo que no es masa de pan (torta de leche): escribe la " +
                            "receta de una tanda tal como la haces. Puede llevar productos del " +
                            "inventario, como el pan molde."
                        else -> "Ingresa pesos reales; se guardará en % automáticamente"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (legacyWeightRecipe) {
                    Text(
                        text = "Esta receta se había ingresado por peso, pero solo se guardaron " +
                            "los porcentajes. Se abre en porcentaje para no alterarlos. Si querés " +
                            "volver a trabajarla por peso, cambiá el modo y reescribí las cantidades.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Activa", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = active, onCheckedChange = { active = it })
            }

            SectionDivider()

            // ── Ingredientes ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ingredientes", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Text(
                    text = when (inputMode) {
                        "PERCENTAGE" -> "(% panadero)"
                        RECIPE_MODE_BATCH -> "(para una tanda)"
                        else -> "(peso → se convierte a %)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (components.isEmpty()) {
                Text(
                    text = if (inputMode == RECIPE_MODE_BATCH) "Sin ingredientes."
                           else "Sin ingredientes. Marca como base la(s) harina(s): su suma = 100%.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                components.forEach { comp ->
                    if (comp.productId.isNotEmpty()) {
                        ProductComponentRow(
                            comp = comp,
                            onUpdate = { updated ->
                                components = components.map { c -> if (c.id == updated.id) updated else c }
                            },
                            onDelete = { components = components.filter { it.id != comp.id } }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        return@forEach
                    }
                    ComponentRow(
                        comp = comp,
                        inputMode = inputMode,
                        ingredients = ingredientState.ingredientList
                            .usableInRecipes(keepIds = setOf(comp.ingredientId)),
                        otherSelectedIds = components.filter { it.id != comp.id }.map { it.ingredientId }.toSet(),
                        onUpdate = { updated ->
                            components = components.map { c -> if (c.id == updated.id) updated else c }
                        },
                        onDelete = { components = components.filter { it.id != comp.id } }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }

            OutlinedButton(
                onClick = { showIngredientPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Agregar ingrediente")
            }
            if (inputMode == RECIPE_MODE_BATCH) {
                OutlinedButton(
                    onClick = { showProductComponentPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar producto del inventario (pan molde…)")
                }
                BatchYieldSection(
                    fullYield = batchYield,
                    onFullYieldChange = { batchYield = it },
                    unitName = batchUnitName,
                    onUnitNameChange = { batchUnitName = it }
                )
            }

            SectionDivider()

            // ── Instrucciones paso a paso ────────────────────────────────────
            StepListEditor(steps = steps, onStepsChange = { steps = it })

            SectionDivider()

            // ── Productos que genera ──────────────────────────────────────────
            SectionHeader("Tipos de productos que genera *")

            if (yields.isEmpty()) {
                Text(
                    text = "Agrega al menos un producto que sale de esta masa. La cantidad exacta se maneja en producción.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                yields.forEach { yld ->
                    YieldFormCard(
                        yield_ = yld,
                        onDelete = { yields = yields.filter { it.id != yld.id } }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            OutlinedButton(
                onClick = { showYieldDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Agregar producto")
            }

            // Mínimo de amasadora y timer son de masa de pan: la tanda no los usa.
            if (inputMode != RECIPE_MODE_BATCH) {
            SectionDivider()

            SectionHeader("Mínimo de la amasadora")

            Text(
                "Harina mínima que la máquina puede amasar con esta receta. Si un día " +
                    "los pedidos de todos los productos de esta masa no llegan a ese peso, " +
                    "la planta decide si hornea el mínimo o anula el producto. Déjalo vacío " +
                    "si esta masa no tiene mínimo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = minBatchFlourLb,
                onValueChange = { txt ->
                    if (txt.isEmpty() || txt.matches(Regex("^\\d*[.,]?\\d*$"))) {
                        minBatchFlourLb = txt
                    }
                },
                label = { Text("Harina mínima por amasada (lb)") },
                placeholder = { Text("Ej. 2.5") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    val lb = minBatchFlourLb.replace(',', '.').toDoubleOrNull()
                    if (lb != null && lb > 0.0) {
                        Text("= ${(lb * GRAMS_PER_LB_RECIPE).fmt(0)} g de harina base")
                    } else {
                        Text("Sin mínimo: se hornea cualquier cantidad que pidan")
                    }
                },
                colors = recipeFieldColors()
            )

            SectionDivider()

            SectionHeader("Tiempo de amasado")

            Text(
                "Si esta masa tiene un tiempo estándar de amasado, la Lista de Producción " +
                    "muestra un timer para esta receta: el panadero lo toca al encender la " +
                    "amasadora y suena cuando se cumple el tiempo. Déjalo vacío si no aplica.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = kneadingMinutes,
                onValueChange = { txt ->
                    if (txt.isEmpty() || txt.matches(Regex("^\\d*[.,]?\\d*$"))) {
                        kneadingMinutes = txt
                    }
                },
                label = { Text("Tiempo de amasado (minutos)") },
                placeholder = { Text("Ej. 12") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(
                        if (kneadingMinutes.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true)
                            "El timer sonará al cumplirse este tiempo"
                        else "Sin timer para esta masa"
                    )
                },
                colors = recipeFieldColors()
            )
            }

            SectionDivider()

            SectionHeader("Notas")

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notas adicionales") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 5
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    var hasError = false
                    if (name.isBlank()) { nameError = true; hasError = true }
                    val err = if (inputMode == RECIPE_MODE_BATCH) validateBatch(
                        components,
                        batchYield.replace(',', '.').toDoubleOrNull(),
                        unitWeightById
                    ) else validateBase(components, inputMode, unitWeightById)
                    if (err != null) { formError = err; hasError = true }
                    if (yields.isEmpty()) {
                        formError = "Agrega al menos un producto que genera la receta"
                        hasError = true
                    }
                    if (hasError) return@Button
                    val stepPhotos: Map<String, ByteArray> =
                        steps.mapNotNull { s -> s.newBytes?.let { s.id to it } }.toMap()
                    viewModel.saveRecipe(companyId, buildRecipe(), stepPhotos)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar Receta", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }
}

// ── Card de producto que genera ──────────────────────────────────────────────

@Composable
private fun YieldFormCard(yield_: YieldEntry, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = yield_.productName.ifEmpty { "Sin producto" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Fila de ingrediente ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComponentRow(
    comp: ComponentEntry,
    inputMode: String,
    ingredients: List<Ingredient>,
    otherSelectedIds: Set<String>,
    onUpdate: (ComponentEntry) -> Unit,
    onDelete: () -> Unit
) {
    var showIngredientPicker by remember { mutableStateOf(false) }
    val inputLabel = when (inputMode) {
        "PERCENTAGE" -> "%"
        RECIPE_MODE_BATCH -> "Cant."
        else -> "Peso"
    }
    val realQuantities = inputMode == "WEIGHT" || inputMode == RECIPE_MODE_BATCH

    if (showIngredientPicker) {
        IngredientPickerDialog(
            ingredients = ingredients,
            alreadyAddedIds = otherSelectedIds,
            onPick = { ing ->
                onUpdate(
                    comp.copy(
                        ingredientId = ing.id,
                        ingredientName = ing.name,
                        ingredientUnit = ing.allowedUnits.firstOrNull() ?: ""
                    )
                )
                showIngredientPicker = false
            },
            onDismiss = { showIngredientPicker = false }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IngredientPickerField(
                value = comp.ingredientName,
                onClick = { showIngredientPicker = true },
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = comp.rawInput,
                onValueChange = { onUpdate(comp.copy(rawInput = it)) },
                label = { Text(inputLabel) },
                colors = recipeFieldColors(),
                modifier = Modifier.width(96.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            if (realQuantities && comp.ingredientUnit.isNotEmpty()) {
                var unitExpanded by remember { mutableStateOf(false) }
                // Un insumo contado con peso unitario acepta también gr/oz/lb:
                // "6 unidad" y "10.6 oz" de huevo son lo mismo y dan el mismo %.
                val unitOptions = ingredients.find { it.id == comp.ingredientId }?.recipeUnitOptions
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(comp.ingredientUnit)
                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { unitExpanded = it },
                    modifier = Modifier.width(84.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.width(84.dp).height(56.dp).menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                comp.ingredientUnit,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (unitOptions.size > 1)
                                Icon(
                                    Icons.Default.ArrowDropDown, null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                        }
                    }
                    if (unitOptions.size > 1) {
                        ExposedDropdownMenu(
                            expanded = unitExpanded,
                            onDismissRequest = { unitExpanded = false }
                        ) {
                            unitOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = { onUpdate(comp.copy(ingredientUnit = opt)); unitExpanded = false },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }
            }
        }

        // Equivalencia en vivo: confirma que el insumo contado sí entró como peso,
        // y avisa de una vez si le falta el peso unitario en lugar de dejar que
        // el error aparezca hasta el guardado.
        if (realQuantities && comp.ingredientUnit.isNotEmpty()) {
            val ing = ingredients.find { it.id == comp.ingredientId }
            val typed = comp.rawInput.replace(',', '.').toDoubleOrNull()
            val grams = if (typed != null && typed > 0.0)
                toGramsOrNull(typed, comp.ingredientUnit, ing?.unitWeightGrams ?: 0.0)
            else null
            when {
                typed == null || typed <= 0.0 -> Unit
                grams == null -> Text(
                    text = "Falta el peso de una unidad de ${comp.ingredientName} " +
                        "(configuralo en Insumos)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp)
                )
                comp.ingredientUnit !in listOf("oz", "lb") -> Text(
                    text = "≈ ${formatBakerWeight(grams)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Por tanda no hay harina base: el borrar queda solo, a la derecha.
            if (inputMode == RECIPE_MODE_BATCH) Spacer(Modifier.weight(1f)) else
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        if (comp.isBase) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Checkbox(
                    checked = comp.isBase,
                    onCheckedChange = { onUpdate(comp.copy(isBase = it)) },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (comp.isBase) "Harina base" else "Marcar como base",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (comp.isBase) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Receta por tanda ─────────────────────────────────────────────────────────

/** Fila de un producto del inventario dentro de la receta por tanda (el pan molde). */
@Composable
private fun ProductComponentRow(
    comp: ComponentEntry,
    onUpdate: (ComponentEntry) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(comp.productName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "Producto · sale del inventario de la planta",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = comp.rawInput,
            onValueChange = { onUpdate(comp.copy(rawInput = it)) },
            label = { Text("Cant.") },
            colors = recipeFieldColors(),
            modifier = Modifier.width(96.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        Text(comp.ingredientUnit, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Cuánto rinde la tanda. Siempre se hace entera: las sucursales piden los pedazos que quieren y
 * al armar el lote la encargada reparte lo que sobra de completar la bandeja.
 */
@Composable
private fun BatchYieldSection(
    fullYield: String,
    onFullYieldChange: (String) -> Unit,
    unitName: String,
    onUnitNameChange: (String) -> Unit
) {
    val decimal = Regex("^\\d*[.,]?\\d*$")
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rinde", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = unitName,
            onValueChange = onUnitNameChange,
            label = { Text("¿Cómo se llama la tanda?") },
            placeholder = { Text("bandeja") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = recipeFieldColors()
        )
        val unit = unitName.trim().ifEmpty { "tanda" }
        OutlinedTextField(
            value = fullYield,
            onValueChange = { if (it.isEmpty() || it.matches(decimal)) onFullYieldChange(it) },
            label = { Text("Salen de 1 $unit *") },
            placeholder = { Text("35") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = recipeFieldColors()
        )
        Text(
            "Siempre se hace la $unit entera. Las sucursales piden los que quieran; al armar el " +
                "lote, si lo pedido no completa la $unit, la encargada reparte lo que sobra " +
                "o lo deja en la planta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Campo selector de ingrediente (con look activo, no deshabilitado) ─────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientPickerField(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEmpty = value.isBlank()
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ingrediente",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isEmpty) "Seleccionar…" else value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isEmpty) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isEmpty) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1
                )
            }
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Colores de campos ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun recipeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
)

// ── Diálogo de productos ─────────────────────────────────────────────────────

@Composable
private fun YieldDialog(
    existingYieldIds: Set<String>,
    products: List<Product>,
    onConfirm: (List<YieldEntry>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }

    val sorted = remember(products) { products.sortedBy { it.name.lowercase() } }
    val filteredProducts = remember(searchQuery, sorted) {
        if (searchQuery.isBlank()) sorted
        else sorted.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Agregar productos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (products.isEmpty()) {
                    Text(
                        text = "No hay productos terminados registrados. Agrégalos primero en el módulo de Productos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Buscar producto") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        if (filteredProducts.isEmpty()) {
                            item {
                                Text(
                                    text = "Sin resultados para \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        } else {
                            items(filteredProducts, key = { it.id }) { product ->
                                val alreadyAdded = existingYieldIds.contains(product.id)
                                val checked = alreadyAdded || selectedIds.contains(product.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !alreadyAdded) {
                                            selectedIds = if (selectedIds.contains(product.id))
                                                selectedIds - product.id
                                            else selectedIds + product.id
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = if (alreadyAdded) null else { _ ->
                                            selectedIds = if (selectedIds.contains(product.id))
                                                selectedIds - product.id
                                            else selectedIds + product.id
                                        },
                                        enabled = !alreadyAdded
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = product.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (alreadyAdded) MaterialTheme.colorScheme.outline
                                                    else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (product.category.isNotEmpty()) {
                                            Text(
                                                text = if (alreadyAdded) "${product.category} • ya agregado"
                                                       else product.category,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val nuevos = sorted
                                .filter { selectedIds.contains(it.id) }
                                .map { YieldEntry(generateEntryId(), it.id, it.name) }
                            onConfirm(nuevos)
                        },
                        enabled = selectedIds.isNotEmpty()
                    ) { Text("Agregar (${selectedIds.size})") }
                }
            }
        }
    }
}

// ── Diálogo: no hay productos ────────────────────────────────────────────────

@Composable
private fun NoProductsDialog(
    onGoToProducts: () -> Unit,
    onBack: () -> Unit
) {
    Dialog(onDismissRequest = onBack) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "No puedes crear recetas todavía",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Toda receta debe generar al menos un producto, pero aún no has creado ninguno en el módulo de Productos. Crea primero tus productos terminados y luego regresa a crear la receta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onGoToProducts,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ir a Productos", fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Volver")
                }
            }
        }
    }
}

// ── Editor de instrucciones paso a paso ──────────────────────────────────────

@Composable
private fun StepListEditor(
    steps: List<StepEntry>,
    onStepsChange: (List<StepEntry>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Instrucciones (opcional)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        if (steps.isEmpty()) {
            Text(
                text = "Opcional. Agrega los pasos uno a uno; cada paso admite una foto opcional.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            steps.forEachIndexed { idx, step ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${idx + 1}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedTextField(
                            value = step.text,
                            onValueChange = { newVal ->
                                onStepsChange(steps.toMutableList().apply { set(idx, step.copy(text = newVal)) })
                            },
                            placeholder = { Text("Descripción del paso…") },
                            colors = recipeFieldColors(),
                            modifier = Modifier.weight(1f),
                            minLines = 1,
                            maxLines = 3
                        )
                        IconButton(
                            onClick = { onStepsChange(steps.filterIndexed { i, _ -> i != idx }) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar paso",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    ImagePickerButton(
                        currentImageUrl = step.photoUrl,
                        selectedBytes = step.newBytes,
                        onImageSelected = { bytes ->
                            onStepsChange(
                                steps.toMutableList().apply { set(idx, step.copy(newBytes = bytes)) }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { onStepsChange(steps + StepEntry(id = generateEntryId())) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Agregar paso")
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun InputModeButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label, style = MaterialTheme.typography.labelMedium) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

private fun generateEntryId(): String {
    val hex = "0123456789abcdef"
    return Random.nextBytes(8).joinToString("") { b ->
        hex[(b.toInt() shr 4) and 0xF].toString() + hex[b.toInt() and 0xF]
    }
}
