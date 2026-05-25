package com.bakery.erp.presentation.ingredients

import com.bakery.erp.util.fmt
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
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
import com.bakery.erp.model.INGREDIENT_CATEGORIES
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.PurchaseFormat
import com.bakery.erp.model.Supplier
import com.bakery.erp.model.largestUnit
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.suppliers.SupplierViewModel
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientFormScreen(
    ingredientId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToSuppliers: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: IngredientViewModel,
    supplierViewModel: SupplierViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val supplierState by supplierViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    val existing = remember(ingredientId, state.ingredientList) {
        if (ingredientId != null) state.ingredientList.find { it.id == ingredientId }
            ?: state.selectedIngredient?.takeIf { it.id == ingredientId }
        else null
    }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) supplierViewModel.loadSuppliers(companyId)
    }

    // ── Form state ────────────────────────────────────────────────────────────
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var allowedUnits by remember { mutableStateOf(existing?.allowedUnits ?: emptyList<String>()) }
    var stockAlert by rememberSaveable { mutableStateOf(existing?.stockAlert?.toString() ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var active by rememberSaveable { mutableStateOf(existing?.active ?: true) }
    var purchaseFormats by remember { mutableStateOf(existing?.purchaseFormats ?: emptyList<PurchaseFormat>()) }

    var showFormatDialog by remember { mutableStateOf(false) }
    var editingFormat by remember { mutableStateOf<PurchaseFormat?>(null) }
    var nameError by remember { mutableStateOf(false) }
    var allowedUnitsError by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // ── Diálogo de formato de compra ──────────────────────────────────────────
    if (showFormatDialog) {
        PurchaseFormatDialog(
            format = editingFormat,
            allowedUnits = allowedUnits,
            suppliers = supplierState.supplierList,
            onNavigateToSuppliers = {
                showFormatDialog = false
                editingFormat = null
                onNavigateToSuppliers()
            },
            onConfirm = { newFormat ->
                purchaseFormats = if (newFormat.isDefault) {
                    // Solo uno puede ser predeterminado
                    purchaseFormats
                        .filter { it.id != newFormat.id }
                        .map { it.copy(isDefault = false) } + newFormat
                } else {
                    purchaseFormats.filter { it.id != newFormat.id } + newFormat
                }
                // Si solo hay uno, siempre es default
                if (purchaseFormats.size == 1) {
                    purchaseFormats = listOf(purchaseFormats.first().copy(isDefault = true))
                }
                showFormatDialog = false
                editingFormat = null
            },
            onDismiss = { showFormatDialog = false; editingFormat = null }
        )
    }

    fun buildIngredient() = Ingredient(
        id = existing?.id ?: "",
        name = name.trim().replaceFirstChar { it.uppercase() },
        category = category,
        allowedUnits = allowedUnits,
        purchaseFormats = purchaseFormats,
        stockAlert = stockAlert.toDoubleOrNull(),
        notes = notes.trim().ifEmpty { null },
        active = active
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (ingredientId == null) "Nuevo Ingrediente" else "Editar Ingrediente",
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // ── General ───────────────────────────────────────────────────
                SectionHeader("General")

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Nombre *") },
                    placeholder = { Text("Ej: Harina de trigo") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError,
                    supportingText = if (nameError) ({ Text("El nombre es requerido") }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
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
                        INGREDIENT_CATEGORIES.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt) },
                                onClick = { category = opt; categoryExpanded = false },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                // ── Unidades de medida (multi-selección) ─────────────────────
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Unidades de medida *",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (allowedUnitsError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Seleccioná en qué unidades se puede medir este ingrediente. Las mismas aparecerán al crear recetas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    // Grupo: Peso
                    Text("Peso", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("gr", "oz", "lb", "kg").forEach { unit ->
                            FilterChip(
                                selected = unit in allowedUnits,
                                onClick = {
                                    allowedUnits = if (unit in allowedUnits) allowedUnits - unit
                                              else allowedUnits + unit
                                    allowedUnitsError = false
                                },
                                label = { Text(unit) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // Grupo: Volumen
                    Text("Volumen", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ml", "lt").forEach { unit ->
                            FilterChip(
                                selected = unit in allowedUnits,
                                onClick = {
                                    allowedUnits = if (unit in allowedUnits) allowedUnits - unit
                                              else allowedUnits + unit
                                    allowedUnitsError = false
                                },
                                label = { Text(unit) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // Grupo: Conteo
                    Text("Conteo", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("unidad", "docena", "saco").forEach { unit ->
                            FilterChip(
                                selected = unit in allowedUnits,
                                onClick = {
                                    allowedUnits = if (unit in allowedUnits) allowedUnits - unit
                                              else allowedUnits + unit
                                    allowedUnitsError = false
                                },
                                label = { Text(unit) }
                            )
                        }
                    }
                    if (allowedUnitsError) {
                        Text(
                            text = "Seleccioná al menos una unidad",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Activo", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = active, onCheckedChange = { active = it })
                }

                SectionDivider()

                // ── Formatos de compra ─────────────────────────────────────────
                SectionHeader("Presentaciones de Compra")

                // Explicación
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp).padding(top = 1.dp)
                    )
                    Text(
                        text = "Registra cómo comprás este ingrediente: en qué presentación (saco, caja, unidad), con qué unidad y a qué precio. El sistema calcula automáticamente el costo por unidad.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Spacer(Modifier.height(2.dp))

                if (purchaseFormats.isEmpty()) {
                    Text(
                        text = "Todavía no hay presentaciones registradas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    purchaseFormats.sortedByDescending { it.isDefault }.forEach { format ->
                        PurchaseFormatItem(
                            format = format,
                            isOnlyOne = purchaseFormats.size == 1,
                            onEdit = { editingFormat = format; showFormatDialog = true },
                            onDelete = {
                                val remaining = purchaseFormats.filter { it.id != format.id }
                                // Si se eliminó el default y quedan otros, el primero pasa a ser default
                                purchaseFormats = if (format.isDefault && remaining.isNotEmpty()) {
                                    listOf(remaining.first().copy(isDefault = true)) + remaining.drop(1)
                                } else {
                                    remaining
                                }
                            },
                            onSetDefault = {
                                purchaseFormats = purchaseFormats.map {
                                    it.copy(isDefault = it.id == format.id)
                                }
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }

                OutlinedButton(
                    onClick = { editingFormat = null; showFormatDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar presentación de compra")
                }

                SectionDivider()

                // ── Control de stock ───────────────────────────────────────────
                SectionHeader("Control de Stock")

                OutlinedTextField(
                    value = stockAlert,
                    onValueChange = { stockAlert = it },
                    label = { Text("Alerta de stock mínimo") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    suffix = {
                        val defaultFormat = purchaseFormats.firstOrNull { it.isDefault }
                            ?: purchaseFormats.firstOrNull()
                        val label = if (defaultFormat != null)
                            defaultFormat.description
                        else
                            largestUnit(allowedUnits)
                        if (label.isNotEmpty()) Text(label)
                    },
                    supportingText = { Text("Recibirás una alerta cuando el stock baje de este nivel (en presentaciones o unidad mayor)") }
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(Modifier.padding(8.dp))

                Button(
                    onClick = {
                        var hasError = false
                        if (name.isBlank()) { nameError = true; hasError = true }
                        if (allowedUnits.isEmpty()) { allowedUnitsError = true; hasError = true }
                        if (hasError) return@Button
                        viewModel.saveIngredient(companyId, buildIngredient())
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
                        Text("Guardar Ingrediente", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.padding(16.dp))
            }
        }
    }
}

// ── Item de formato en la lista del formulario ────────────────────────────────

@Composable
private fun PurchaseFormatItem(
    format: PurchaseFormat,
    isOnlyOne: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (format.isDefault)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = format.description.ifEmpty { "Sin descripción" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (format.isDefault) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Predeterminado",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "predeterminado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (format.supplierName.isNotEmpty()) {
                        Text(
                            text = format.supplierName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Acciones
                if (!format.isDefault && !isOnlyOne) {
                    IconButton(onClick = onSetDefault, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Marcar como predeterminado",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(6.dp))

            // Desglose de precio
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Paquete: ${format.unitsPerPackage.toInt().let { if (format.unitsPerPackage == it.toDouble()) it.toString() else format.unitsPerPackage.fmt(2) }} ${format.unit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Costo del paquete: Q ${format.packageCost.fmt(2)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "= Q ${format.costPerUnit.fmt(2)} / ${format.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ── Diálogo para agregar/editar una presentación de compra ────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseFormatDialog(
    format: PurchaseFormat?,
    allowedUnits: List<String>,
    suppliers: List<Supplier>,
    onNavigateToSuppliers: () -> Unit,
    onConfirm: (PurchaseFormat) -> Unit,
    onDismiss: () -> Unit
) {
    var description by remember { mutableStateOf(format?.description ?: "") }
    var selectedSupplierId by remember { mutableStateOf(format?.supplierId ?: "") }
    var selectedSupplierName by remember { mutableStateOf(format?.supplierName ?: "") }
    var supplierExpanded by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf(format?.unit?.takeIf { it.isNotEmpty() } ?: allowedUnits.firstOrNull() ?: "") }
    var unitExpanded by remember { mutableStateOf(false) }
    var unitsPerPackage by remember { mutableStateOf(format?.unitsPerPackage?.takeIf { it != 1.0 || format.id.isNotEmpty() }?.let { it.fmt(2).trimEnd('0').trimEnd('.') } ?: "") }
    var packageCost by remember { mutableStateOf(format?.packageCost?.takeIf { it > 0 }?.let { it.fmt(2) } ?: "") }

    val units = unitsPerPackage.toDoubleOrNull() ?: 0.0
    val cost = packageCost.toDoubleOrNull() ?: 0.0
    val costPerUnit = if (units > 0) cost / units else 0.0

    // Si es el primer formato (nuevo sin ID), marcar como default automáticamente
    val isFirstFormat = format == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (format == null) "Agregar presentación de compra" else "Editar presentación") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Explicación
                Text(
                    text = "Una presentación es la forma en que comprás este ingrediente a un proveedor. Por ejemplo: \"Saco 50 lb\" de Distribuidora ABC a Q200.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción de la presentación") },
                    placeholder = { Text("Ej: Saco 50 lb, Caja 12 unid.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Unidad de medida de esta presentación
                if (allowedUnits.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = unitExpanded,
                        onExpandedChange = { unitExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unidad de esta presentación") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                            allowedUnits.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = { selectedUnit = opt; unitExpanded = false },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                } else if (allowedUnits.size == 1) {
                    // Solo una unidad: mostrar como texto informativo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Unidad", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(allowedUnits.first(), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                }

                // Proveedor
                if (suppliers.isEmpty()) {
                    // Sin proveedores registrados → redirigir
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No tenés proveedores registrados. Para vincular un proveedor, primero agrégalo en el módulo de Proveedores.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(
                            onClick = onNavigateToSuppliers,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Ir a Proveedores →")
                        }
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = supplierExpanded,
                        onExpandedChange = { supplierExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedSupplierName.ifEmpty { "Sin especificar" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Proveedor") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = supplierExpanded, onDismissRequest = { supplierExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Sin especificar") },
                                onClick = { selectedSupplierId = ""; selectedSupplierName = ""; supplierExpanded = false },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                            suppliers.forEach { s ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(s.company, fontWeight = FontWeight.Medium)
                                            if (s.salesperson.isNotEmpty()) {
                                                Text(s.salesperson, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    },
                                    onClick = { selectedSupplierId = s.id; selectedSupplierName = s.company; supplierExpanded = false },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }

                // Cantidad y costo
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unitsPerPackage,
                        onValueChange = { unitsPerPackage = it },
                        label = { Text("Cantidad en paquete") },
                        placeholder = { Text("50") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        suffix = { if (selectedUnit.isNotEmpty()) Text(selectedUnit) }
                    )
                    OutlinedTextField(
                        value = packageCost,
                        onValueChange = { packageCost = it },
                        label = { Text("Costo del paquete") },
                        placeholder = { Text("200.00") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        prefix = { Text("Q ") }
                    )
                }

                // Resultado calculado
                if (costPerUnit > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Resumen:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Q ${cost.fmt(2)} / paquete  →  Q ${costPerUnit.fmt(2)} / $selectedUnit",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        PurchaseFormat(
                            id = format?.id ?: generateFormatId(),
                            description = description.trim(),
                            unit = selectedUnit,
                            supplierId = selectedSupplierId,
                            supplierName = selectedSupplierName.trim(),
                            unitsPerPackage = unitsPerPackage.toDoubleOrNull() ?: 1.0,
                            packageCost = packageCost.toDoubleOrNull() ?: 0.0,
                            isDefault = format?.isDefault ?: isFirstFormat
                        )
                    )
                },
                enabled = description.isNotBlank()
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun generateFormatId(): String {
    val hex = "0123456789abcdef"
    return Random.nextBytes(8).joinToString("") { b ->
        hex[(b.toInt() shr 4) and 0xF].toString() + hex[b.toInt() and 0xF]
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
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
