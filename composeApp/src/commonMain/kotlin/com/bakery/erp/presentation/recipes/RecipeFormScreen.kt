package com.bakery.erp.presentation.recipes

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Edit
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
import com.bakery.erp.model.RECIPE_CATEGORIES
import com.bakery.erp.model.toBaseUnit
import com.bakery.erp.model.Recipe
import com.bakery.erp.model.RecipeComponent
import com.bakery.erp.model.RecipeStage
import com.bakery.erp.model.RecipeStageType
import com.bakery.erp.model.RecipeYield
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.ingredients.IngredientViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.ui.AdaptiveContainer
import kotlin.random.Random

// ── Tipos internos del formulario ─────────────────────────────────────────────

private data class ComponentEntry(
    val id: String,
    val ingredientId: String = "",
    val ingredientName: String = "",
    val ingredientUnit: String = "",
    val rawInput: String = "",
    val isBase: Boolean = false,
    val order: Int = 0
)

private data class StageEntry(
    val id: String,
    val name: String = "",
    val type: String = "MASA_PRINCIPAL",
    val instructions: String = "",
    val restTimeMinutes: String = "",
    val notes: String = "",
    val components: List<ComponentEntry> = emptyList()
)

private data class YieldEntry(
    val id: String,
    val productId: String = "",
    val productName: String = ""
)

// ── Conversiones desde modelos de dominio ─────────────────────────────────────

private fun RecipeStage.toStageEntry() = StageEntry(
    id = id,
    name = name,
    type = type,
    instructions = instructions ?: "",
    restTimeMinutes = restTimeMinutes?.toString() ?: "",
    notes = notes ?: "",
    components = components.sortedBy { it.order }.map { it.toComponentEntry() }
)

private fun RecipeComponent.toComponentEntry() = ComponentEntry(
    id = id,
    ingredientId = ingredientId,
    ingredientName = ingredientName,
    ingredientUnit = ingredientUnit,
    rawInput = if (percentage == percentage.toLong().toDouble()) percentage.toLong().toString()
               else "%.2f".format(percentage).trimEnd('0').trimEnd('.'),
    isBase = isBase,
    order = order
)

private fun RecipeYield.toYieldEntry() = YieldEntry(
    id = id,
    productId = productId,
    productName = productName
)

// ── Conversión peso → porcentaje ──────────────────────────────────────────────

private fun toPercentageComponents(
    components: List<ComponentEntry>,
    inputMode: String
): List<RecipeComponent> {
    if (components.isEmpty()) return emptyList()
    return if (inputMode == "WEIGHT") {
        val baseComp = components.firstOrNull { it.isBase } ?: return emptyList()
        val baseRaw  = baseComp.rawInput.toDoubleOrNull() ?: 0.0
        if (baseRaw == 0.0) return emptyList()
        val baseNorm = toBaseUnit(baseRaw, baseComp.ingredientUnit)
        if (baseNorm == 0.0) return emptyList()
        components.mapIndexed { i, c ->
            val norm = toBaseUnit(c.rawInput.toDoubleOrNull() ?: 0.0, c.ingredientUnit)
            RecipeComponent(
                id = c.id, stageId = "",
                ingredientId = c.ingredientId, ingredientName = c.ingredientName,
                ingredientUnit = c.ingredientUnit,
                percentage = (norm / baseNorm) * 100.0,
                isBase = c.isBase, order = i
            )
        }
    } else {
        components.mapIndexed { i, c ->
            RecipeComponent(
                id = c.id, stageId = "",
                ingredientId = c.ingredientId, ingredientName = c.ingredientName,
                ingredientUnit = c.ingredientUnit,
                percentage = c.rawInput.toDoubleOrNull() ?: 0.0,
                isBase = c.isBase, order = i
            )
        }
    }
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
    productViewModel: ProductViewModel
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
        state = state
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
    state: RecipeUiState
) {
    val ingredientState by ingredientViewModel.uiState.collectAsState()
    val productState by productViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Form state ────────────────────────────────────────────────────────────
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "Pan Principal") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var inputMode by rememberSaveable { mutableStateOf(existing?.inputMode ?: "PERCENTAGE") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var active by rememberSaveable { mutableStateOf(existing?.active ?: true) }
    var stages by remember { mutableStateOf(existing?.stages?.map { it.toStageEntry() } ?: emptyList<StageEntry>()) }
    var yields by remember { mutableStateOf(existing?.yields?.map { it.toYieldEntry() } ?: emptyList<YieldEntry>()) }
    // Estado para Cubierta / Relleno (preparación única, sin etapas)
    var simpleComponents by remember {
        mutableStateOf(
            if (existing != null && existing.category != "Pan Principal")
                existing.stages.firstOrNull()?.components?.map { it.toComponentEntry() } ?: emptyList()
            else emptyList<ComponentEntry>()
        )
    }
    var simpleSteps by remember {
        mutableStateOf(
            if (existing != null && existing.category != "Pan Principal")
                (existing.stages.firstOrNull()?.instructions ?: "").split("\n").filter { it.isNotEmpty() }
            else emptyList<String>()
        )
    }
    val isPanPrincipal = category == "Pan Principal"

    var showStageDialog by remember { mutableStateOf(false) }
    var editingStage by remember { mutableStateOf<StageEntry?>(null) }
    var showYieldDialog by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

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
        val builtStages: List<RecipeStage>
        val builtYields: List<RecipeYield>

        if (isPanPrincipal) {
            builtStages = stages.mapIndexed { stageIdx, entry ->
                val comps = toPercentageComponents(entry.components, inputMode)
                    .map { it.copy(stageId = entry.id) }
                RecipeStage(
                    id = entry.id, name = entry.name.trim(), order = stageIdx, type = entry.type,
                    instructions = entry.instructions.trim().ifEmpty { null },
                    restTimeMinutes = entry.restTimeMinutes.toIntOrNull(),
                    notes = entry.notes.trim().ifEmpty { null },
                    components = comps
                )
            }
            builtYields = yields.map { y ->
                RecipeYield(id = y.id, productId = y.productId, productName = y.productName)
            }
        } else {
            val stageId = existing?.stages?.firstOrNull()?.id ?: generateEntryId()
            val stageType = if (category == "Cubierta") "CUBIERTA" else "RELLENO"
            val comps = toPercentageComponents(simpleComponents, inputMode)
                .map { it.copy(stageId = stageId) }
            builtStages = listOf(
                RecipeStage(
                    id = stageId, name = category, order = 0, type = stageType,
                    instructions = simpleSteps.filter { it.isNotEmpty() }.joinToString("\n").ifEmpty { null },
                    components = comps
                )
            )
            builtYields = emptyList()
        }

        return Recipe(
            id = existing?.id ?: "",
            name = name.trim().replaceFirstChar { it.uppercase() },
            description = description.trim().ifEmpty { null },
            category = category, inputMode = inputMode,
            notes = notes.trim().ifEmpty { null }, active = active,
            stages = builtStages, yields = builtYields
        )
    }

    // ── Diálogos ──────────────────────────────────────────────────────────────
    if (showStageDialog) {
        StageDialog(
            stage = editingStage,
            inputMode = inputMode,
            ingredients = ingredientState.ingredientList,
            existingTypes = if (editingStage != null)
                stages.filter { it.id != editingStage!!.id }.map { it.type }
            else
                stages.map { it.type },
            onConfirm = { newStage ->
                stages = if (editingStage != null) {
                    stages.map { if (it.id == newStage.id) newStage else it }
                } else {
                    stages + newStage
                }
                showStageDialog = false; editingStage = null
            },
            onDismiss = { showStageDialog = false; editingStage = null }
        )
    }

    if (showYieldDialog) {
        YieldDialog(
            existingYieldIds = yields.map { it.productId }.toSet(),
            products = productState.productList,
            onConfirm = { newYield ->
                yields = yields + newYield
                showYieldDialog = false
            },
            onDismiss = { showYieldDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
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
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Información Básica ─────────────────────────────────────────────
            SectionHeader("Información Básica")

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("Nombre *") },
                placeholder = { Text("Ej: Masa de Pan Francés") },
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

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoría") },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    RECIPE_CATEGORIES.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt) },
                            onClick = { category = opt; categoryExpanded = false },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            // Modo de entrada
            Column {
                Text("Modo de ingreso de ingredientes", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InputModeButton(
                        label = "Porcentaje",
                        selected = inputMode == "PERCENTAGE",
                        onClick = { inputMode = "PERCENTAGE" },
                        modifier = Modifier.weight(1f)
                    )
                    InputModeButton(
                        label = "Por peso",
                        selected = inputMode == "WEIGHT",
                        onClick = { inputMode = "WEIGHT" },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = if (inputMode == "PERCENTAGE") "Ingresa porcentajes del panadero (base = 100%)"
                           else "Ingresa pesos reales; se guardará en % automáticamente",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
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

            if (isPanPrincipal) {
                // ── Etapas ─────────────────────────────────────────────────────
                SectionHeader("Etapas de Preparación")

                if (stages.isEmpty()) {
                    Text(
                        text = "Sin etapas. Agrega al menos una etapa con sus ingredientes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    stages.forEachIndexed { index, stage ->
                        StageFormCard(
                            stage = stage,
                            order = index + 1,
                            onEdit = { editingStage = stage; showStageDialog = true },
                            onDelete = { stages = stages.filter { it.id != stage.id } }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }

                if (stages.size < 2) {
                    OutlinedButton(
                        onClick = { editingStage = null; showStageDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (stages.isEmpty()) "Agregar etapa" else "Agregar pre-fermento")
                    }
                }

                SectionDivider()

                // ── Productos que genera ───────────────────────────────────────
                SectionHeader("Tipos de productos que genera")

                if (yields.isEmpty()) {
                    Text(
                        text = "Referencia qué tipos de pan salen de esta masa. La cantidad exacta se maneja en producción.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
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

                SectionDivider()

            } else {
                // ── Ingredientes (Cubierta / Relleno) ─────────────────────────
                SectionHeader("Ingredientes")

                if (simpleComponents.isEmpty()) {
                    Text(
                        text = "Sin ingredientes. El primero que agregues será la base (100%).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    simpleComponents.forEach { comp ->
                        ComponentRow(
                            comp = comp,
                            inputMode = inputMode,
                            ingredients = ingredientState.ingredientList,
                            onUpdate = { updated ->
                                simpleComponents = if (updated.isBase) {
                                    simpleComponents.map { c ->
                                        if (c.id == updated.id) updated else c.copy(isBase = false)
                                    }
                                } else {
                                    simpleComponents.map { c -> if (c.id == updated.id) updated else c }
                                }
                            },
                            onDelete = { simpleComponents = simpleComponents.filter { it.id != comp.id } }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }

                OutlinedButton(
                    onClick = {
                        simpleComponents = simpleComponents + ComponentEntry(
                            id = generateEntryId(),
                            isBase = simpleComponents.isEmpty(),
                            order = simpleComponents.size
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar ingrediente")
                }

                SectionDivider()

                // ── Instrucciones paso a paso ──────────────────────────────────
                StepListEditor(
                    steps = simpleSteps,
                    onStepsChange = { simpleSteps = it }
                )

                SectionDivider()
            }

            // ── Notas ─────────────────────────────────────────────────────────
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
                    if (isPanPrincipal && inputMode == "WEIGHT") {
                        for (stage in stages.filter { it.components.isNotEmpty() }) {
                            val base = stage.components.firstOrNull { it.isBase }
                            if (base == null || (base.rawInput.toDoubleOrNull() ?: 0.0) == 0.0) {
                                formError = "La etapa \"${stage.name.ifEmpty { "sin nombre" }}\" necesita un ingrediente base con peso mayor a 0"
                                hasError = true; break
                            }
                        }
                    } else if (!isPanPrincipal && inputMode == "WEIGHT" && simpleComponents.isNotEmpty()) {
                        val base = simpleComponents.firstOrNull { it.isBase }
                        if (base == null || (base.rawInput.toDoubleOrNull() ?: 0.0) == 0.0) {
                            formError = "Necesita un ingrediente base con peso mayor a 0"
                            hasError = true
                        }
                    }
                    if (hasError) return@Button
                    viewModel.saveRecipe(companyId, buildRecipe())
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
        } // AdaptiveContainer
    }
}

// ── Card de etapa en el formulario ────────────────────────────────────────────

@Composable
private fun StageFormCard(
    stage: StageEntry,
    order: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val stageTypeDisplay = RecipeStageType.entries.firstOrNull { it.name == stage.type }?.displayName ?: stage.type

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("$order", color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stage.name.ifEmpty { stageTypeDisplay },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallBadge(stageTypeDisplay)
                    SmallBadge("${stage.components.size} ingrediente${if (stage.components.size != 1) "s" else ""}")
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Card de producto que genera en el formulario ───────────────────────────────

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

// ── Diálogo de etapa ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StageDialog(
    stage: StageEntry?,
    inputMode: String,
    ingredients: List<Ingredient>,
    existingTypes: List<String>,
    onConfirm: (StageEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val allStageTypes = listOf(RecipeStageType.PREFERMENTO, RecipeStageType.MASA_PRINCIPAL)
    val availableTypes = allStageTypes.filter { it.name !in existingTypes }
    val forcedType: RecipeStageType? = if (stage == null && availableTypes.size == 1) availableTypes.first() else null
    var stageType by remember {
        mutableStateOf(
            when {
                stage != null -> stage.type
                forcedType != null -> forcedType.name
                else -> "MASA_PRINCIPAL"
            }
        )
    }
    var stageTypeExpanded by remember { mutableStateOf(false) }
    var restTime by remember { mutableStateOf(stage?.restTimeMinutes ?: "") }
    var steps by remember {
        mutableStateOf(
            (stage?.instructions ?: "").split("\n").filter { it.isNotEmpty() }
        )
    }
    var components by remember { mutableStateOf(stage?.components ?: emptyList<ComponentEntry>()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (stage == null) "Agregar Etapa" else "Editar Etapa",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Tipo de preparación — chip si está forzado, dropdown si hay opciones
                val typeLabel = forcedType?.displayName
                    ?: allStageTypes.firstOrNull { it.name == stageType }?.displayName
                    ?: stageType
                if (stage != null || forcedType != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Tipo de preparación",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = stageTypeExpanded,
                        onExpandedChange = { stageTypeExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = typeLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo de preparación") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = stageTypeExpanded, onDismissRequest = { stageTypeExpanded = false }) {
                            availableTypes.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.displayName) },
                                    onClick = { stageType = t.name; stageTypeExpanded = false },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }

                if (stageType == "PREFERMENTO") {
                    OutlinedTextField(
                        value = restTime,
                        onValueChange = { restTime = it },
                        label = { Text("Tiempo de reposo (minutos)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        suffix = { Text("min") }
                    )
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ingredientes", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Text(
                        text = if (inputMode == "PERCENTAGE") "(% panadero)" else "(peso → se convierte a %)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (components.isEmpty()) {
                    Text(
                        text = "Sin ingredientes. El primero que agregues será la base (100%).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    components.forEach { comp ->
                        ComponentRow(
                            comp = comp,
                            inputMode = inputMode,
                            ingredients = ingredients,
                            onUpdate = { updated ->
                                components = if (updated.isBase) {
                                    components.map { c ->
                                        if (c.id == updated.id) updated else c.copy(isBase = false)
                                    }
                                } else {
                                    components.map { c -> if (c.id == updated.id) updated else c }
                                }
                            },
                            onDelete = { components = components.filter { it.id != comp.id } }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }

                OutlinedButton(
                    onClick = {
                        val isFirstComponent = components.isEmpty()
                        components = components + ComponentEntry(
                            id = generateEntryId(),
                            isBase = isFirstComponent,
                            order = components.size
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Agregar ingrediente")
                }

                HorizontalDivider()

                StepListEditor(
                    steps = steps,
                    onStepsChange = { steps = it }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val derivedName = allStageTypes.firstOrNull { it.name == stageType }?.displayName ?: stageType
                            onConfirm(
                                StageEntry(
                                    id = stage?.id ?: generateEntryId(),
                                    name = derivedName,
                                    type = stageType,
                                    instructions = steps.filter { it.isNotEmpty() }.joinToString("\n"),
                                    restTimeMinutes = restTime,
                                    notes = "",
                                    components = components
                                )
                            )
                        }
                    ) { Text("Confirmar") }
                }
            }
        }
    }
}

// ── Fila de ingrediente dentro del StageDialog ────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComponentRow(
    comp: ComponentEntry,
    inputMode: String,
    ingredients: List<Ingredient>,
    onUpdate: (ComponentEntry) -> Unit,
    onDelete: () -> Unit
) {
    var ingredientExpanded by remember { mutableStateOf(false) }
    val inputLabel = if (inputMode == "PERCENTAGE") "%" else "Peso"

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = ingredientExpanded,
                onExpandedChange = { ingredientExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = comp.ingredientName.ifEmpty { "Ingrediente" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Ingrediente") },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    singleLine = true
                )
                ExposedDropdownMenu(expanded = ingredientExpanded, onDismissRequest = { ingredientExpanded = false }) {
                    if (ingredients.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Sin ingredientes registrados") },
                            onClick = { ingredientExpanded = false },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    } else {
                        ingredients.forEach { ing ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(ing.name, fontWeight = FontWeight.Medium)
                                        Text(ing.allowedUnits.joinToString(" / "), style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    onUpdate(comp.copy(
                                        ingredientId = ing.id,
                                        ingredientName = ing.name,
                                        ingredientUnit = ing.allowedUnits.firstOrNull() ?: ""
                                    ))
                                    ingredientExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = comp.rawInput,
                onValueChange = { onUpdate(comp.copy(rawInput = it)) },
                label = { Text(inputLabel) },
                modifier = Modifier.width(72.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            if (comp.ingredientUnit.isNotEmpty()) {
                var unitExpanded by remember { mutableStateOf(false) }
                val unitOptions = ingredients.find { it.id == comp.ingredientId }?.allowedUnits
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(comp.ingredientUnit)
                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { unitExpanded = it },
                    modifier = Modifier.width(60.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.width(60.dp).menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                comp.ingredientUnit,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (unitOptions.size > 1)
                                Icon(
                                    Icons.Default.ArrowDropDown, null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
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
                    text = if (comp.isBase) "Base (100%)" else "Marcar como base",
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

// ── Diálogo de producto con búsqueda ─────────────────────────────────────────

@Composable
private fun YieldDialog(
    existingYieldIds: Set<String>,
    products: List<Product>,
    onConfirm: (YieldEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf("") }
    var selectedProductName by remember { mutableStateOf("") }

    val filteredProducts = remember(searchQuery, products) {
        products.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Agregar producto",
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

                    LazyColumn(modifier = Modifier.height(260.dp)) {
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
                                val isSelected = selectedProductId == product.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !alreadyAdded) {
                                            selectedProductId = product.id
                                            selectedProductName = product.name
                                        }
                                        .background(
                                            when {
                                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                alreadyAdded -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                else -> MaterialTheme.colorScheme.surface
                                            }
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = product.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
                                    if (isSelected) {
                                        Text("✓", color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold)
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
                            if (selectedProductId.isEmpty()) return@Button
                            onConfirm(YieldEntry(
                                id = generateEntryId(),
                                productId = selectedProductId,
                                productName = selectedProductName
                            ))
                        },
                        enabled = selectedProductId.isNotEmpty()
                    ) { Text("Agregar") }
                }
            }
        }
    }
}

// ── Editor de instrucciones paso a paso ──────────────────────────────────────

@Composable
private fun StepListEditor(
    steps: List<String>,
    onStepsChange: (List<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Instrucciones",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        if (steps.isEmpty()) {
            Text(
                text = "Sin pasos. Agrégalos uno a uno para guiar la preparación.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            steps.forEachIndexed { idx, step ->
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
                        value = step,
                        onValueChange = { newVal ->
                            onStepsChange(steps.toMutableList().apply { set(idx, newVal) })
                        },
                        placeholder = { Text("Descripción del paso…") },
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
            }
        }

        OutlinedButton(
            onClick = { onStepsChange(steps + "") },
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
private fun SmallBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
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
