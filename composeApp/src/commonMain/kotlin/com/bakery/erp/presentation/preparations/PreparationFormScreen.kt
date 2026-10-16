package com.bakery.erp.presentation.preparations

import com.bakery.erp.ui.BrandTopAppBar

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bakery.erp.presentation.ingredients.IngredientPickerDialog
import com.bakery.erp.model.usableInRecipes
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Preparation
import com.bakery.erp.model.PreparationIngredient
import com.bakery.erp.model.PreparationUsage
import com.bakery.erp.model.toGramsOrNull
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.ingredients.IngredientViewModel
import com.bakery.erp.ui.AdaptiveContainer
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Línea editable de insumo dentro del formulario. */
private data class IngredientEntry(
    val id: String,
    val ingredientId: String,
    val ingredientName: String,
    val quantity: String,
    val unit: String,
    val allowedUnits: List<String>
)

@Composable
fun PreparationFormScreen(
    preparationId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: PreparationViewModel,
    ingredientViewModel: IngredientViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""

    LaunchedEffect(preparationId, companyId) {
        if (companyId.isNotEmpty()) {
            if (preparationId != null) viewModel.loadPreparation(companyId, preparationId)
            ingredientViewModel.loadIngredients(companyId)
        }
    }

    val selected = state.selectedPreparation?.takeIf { it.id == preparationId }
    val needsLoad = preparationId != null && selected == null

    if (needsLoad && state.isDetailLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    PreparationFormContent(
        existing = if (preparationId != null) selected else null,
        onNavigateBack = onNavigateBack,
        companyId = companyId,
        viewModel = viewModel,
        ingredientViewModel = ingredientViewModel,
        isSaving = state.isLoading,
        isSaved = state.isSaved,
        error = state.error,
        clearSaved = { viewModel.clearSaved() },
        clearError = { viewModel.clearError() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreparationFormContent(
    existing: Preparation?,
    onNavigateBack: () -> Unit,
    companyId: String,
    viewModel: PreparationViewModel,
    ingredientViewModel: IngredientViewModel,
    isSaving: Boolean,
    isSaved: Boolean,
    error: String?,
    clearSaved: () -> Unit,
    clearError: () -> Unit
) {
    val ingredientState by ingredientViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var usage by remember { mutableStateOf(existing?.usage ?: PreparationUsage.DOUGH) }
    var entries by remember {
        mutableStateOf<List<IngredientEntry>>(
            existing?.ingredients?.map { pi ->
                IngredientEntry(
                    id = pi.id.ifEmpty { genId() },
                    ingredientId = pi.ingredientId,
                    ingredientName = pi.ingredientName,
                    quantity = if (pi.quantityPerBatch == 0.0) "" else pi.quantityPerBatch.toString(),
                    unit = pi.unit,
                    allowedUnits = emptyList()
                )
            } ?: emptyList()
        )
    }
    var nameError by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    // Rellenar unidades permitidas en modo edición cuando carga la lista de insumos
    LaunchedEffect(ingredientState.ingredientList) {
        if (ingredientState.ingredientList.isNotEmpty()) {
            entries = entries.map { e ->
                if (e.allowedUnits.isNotEmpty()) e
                else {
                    val ing = ingredientState.ingredientList.find { it.id == e.ingredientId }
                    if (ing != null) e.copy(allowedUnits = ing.allowedUnits) else e
                }
            }
        }
    }

    LaunchedEffect(isSaved) {
        if (isSaved) { clearSaved(); onNavigateBack() }
    }
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); clearError() }
    }

    // Peso de una unidad por insumo — traduce "6 huevos" a gramos. Sin esto el
    // peso del lote quedaba corto y la esponja descontaba mal de la receta.
    fun unitWeightOf(ingredientId: String): Double =
        ingredientState.ingredientList.find { it.id == ingredientId }?.unitWeightGrams ?: 0.0

    fun entryGrams(e: IngredientEntry): Double =
        toGramsOrNull(e.quantity.toDoubleOrNull() ?: 0.0, e.unit, unitWeightOf(e.ingredientId)) ?: 0.0

    // Peso total calculado en vivo (gramos)
    val totalGrams = entries.sumOf { e -> entryGrams(e) }

    fun save() {
        if (name.isBlank()) { nameError = true; return }
        val valid = entries.filter {
            it.ingredientId.isNotEmpty() && (it.quantity.toDoubleOrNull() ?: 0.0) > 0.0
        }
        if (valid.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("Agrega al menos un insumo con cantidad") }
            return
        }
        // Fracción de harina del lote: insumos categoría "Harinas" ÷ peso total
        val totalG = valid.sumOf { entryGrams(it) }
        val flourG = valid.filter { e ->
            ingredientState.ingredientList.find { it.id == e.ingredientId }?.category == "Harinas"
        }.sumOf { entryGrams(it) }
        val flourPct = if (totalG > 0.0) (flourG / totalG) * 100.0 else 0.0
        val prep = Preparation(
            id = existing?.id ?: "",
            name = name.trim().replaceFirstChar { it.uppercase() },
            description = description.trim().ifEmpty { null },
            usage = usage,
            flourFractionPct = flourPct,
            notes = notes.trim().ifEmpty { null },
            active = active,
            createdAt = existing?.createdAt ?: 0L,
            ingredients = valid.map {
                PreparationIngredient(
                    id = it.id,
                    ingredientId = it.ingredientId,
                    ingredientName = it.ingredientName,
                    quantityPerBatch = it.quantity.toDoubleOrNull() ?: 0.0,
                    unit = it.unit,
                    unitWeightGrams = unitWeightOf(it.ingredientId)
                )
            }
        )
        viewModel.savePreparation(companyId, prep)
    }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = {
                    Text(
                        if (existing == null) "Nueva preparación" else "Editar preparación",
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
        AdaptiveContainer(modifier = Modifier.padding(padding), maxContentWidth = 640.dp) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Nombre *") },
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text(
                        "¿Para qué se usa esta preparación?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UsageButton(
                            label = "Para masa",
                            description = "Esponja/masa madre que se incorpora a la masa",
                            selected = usage == PreparationUsage.DOUGH,
                            onClick = { usage = PreparationUsage.DOUGH },
                            modifier = Modifier.weight(1f)
                        )
                        UsageButton(
                            label = "Para adorno",
                            description = "Concha/relleno que se aplica al producto",
                            selected = usage == PreparationUsage.DECORATION,
                            onClick = { usage = PreparationUsage.DECORATION },
                            modifier = Modifier.weight(1f)
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

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Insumos por lote",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(onClick = { showPicker = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Agregar")
                    }
                }

                if (entries.isEmpty()) {
                    Text(
                        "Aún no hay insumos. Agrega los que lleva un lote estándar (ej. harina 10 lb).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    entries.forEach { entry ->
                        IngredientEntryRow(
                            entry = entry,
                            onQuantityChange = { q ->
                                entries = entries.map { if (it.id == entry.id) it.copy(quantity = q) else it }
                            },
                            onUnitChange = { u ->
                                entries = entries.map { if (it.id == entry.id) it.copy(unit = u) else it }
                            },
                            onRemove = { entries = entries.filter { it.id != entry.id } }
                        )
                    }
                }

                // Peso total calculado (no editable)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Peso por lote (calculado)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Suma de los insumos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            formatBatchWeight(totalGrams),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { save() },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Guardar preparación")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showPicker) {
        IngredientPickerDialog(
            ingredients = ingredientState.ingredientList.usableInRecipes().filter { ing ->
                entries.none { it.ingredientId == ing.id }
            },
            onPick = { ing ->
                entries = entries + IngredientEntry(
                    id = genId(),
                    ingredientId = ing.id,
                    ingredientName = ing.name,
                    quantity = "",
                    unit = ing.effectiveBaseUnit.ifEmpty { ing.allowedUnits.firstOrNull() ?: "gr" },
                    allowedUnits = ing.allowedUnits.ifEmpty { listOf("gr", "lb", "kg", "oz") }
                )
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientEntryRow(
    entry: IngredientEntry,
    onQuantityChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    var unitMenu by remember { mutableStateOf(false) }
    val units = entry.allowedUnits.ifEmpty { listOf(entry.unit).filter { it.isNotEmpty() } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                entry.ingredientName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = entry.quantity,
                onValueChange = { v -> onQuantityChange(v.filter { it.isDigit() || it == '.' }) },
                label = { Text("Cant.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(90.dp)
            )
            Box {
                OutlinedButton(onClick = { unitMenu = true }) {
                    Text(entry.unit.ifEmpty { "u." })
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                    units.forEach { u ->
                        DropdownMenuItem(
                            text = { Text(u) },
                            onClick = { onUnitChange(u); unitMenu = false }
                        )
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Quitar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun UsageButton(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = fg)
            Text(description, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.85f))
        }
    }
}

private fun genId(): String {
    val hex = "0123456789abcdef"
    return Random.nextBytes(16).joinToString("") { b ->
        hex[(b.toInt() shr 4) and 0xF].toString() + hex[b.toInt() and 0xF]
    }
}
