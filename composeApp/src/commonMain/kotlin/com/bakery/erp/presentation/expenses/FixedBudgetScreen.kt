package com.bakery.erp.presentation.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.bakery.erp.model.ExpenseCategory
import com.bakery.erp.model.FixedBudgetItem
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.branches.BranchViewModel

// ─── List ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedBudgetListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToForm: () -> Unit,
    onNavigateToEdit: (itemId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: FixedBudgetViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }
    var itemToDelete by remember { mutableStateOf<FixedBudgetItem?>(null) }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) viewModel.loadItems(companyId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) viewModel.clearDeleted()
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Eliminar costo fijo") },
            text = { Text("¿Eliminar \"${item.name}\" de ${item.branchName}?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteItem(companyId, item.id); itemToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Costos Fijos", fontWeight = FontWeight.SemiBold) },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToForm,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo costo fijo", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.itemList.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.itemList.isEmpty() ->
                    FbEmptyPlaceholder()

                else -> {
                    val byBranch = state.itemList
                        .filter { it.active }
                        .groupBy { it.branchName.ifEmpty { "Sin sucursal" } }
                        .toSortedMap()

                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        byBranch.forEach { (branchName, items) ->
                            item(key = "hdr_$branchName") {
                                Text(
                                    text = branchName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            items(items, key = { it.id }) { item ->
                                FbItemCard(
                                    item = item,
                                    onEdit = { onNavigateToEdit(item.id) },
                                    onDelete = { itemToDelete = item }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FbItemCard(item: FixedBudgetItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                CategoryBadge(item.category)
                if (!item.notes.isNullOrEmpty()) {
                    Text(
                        text = item.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "Q ${"%.2f".format(item.amount)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FbEmptyPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Payments,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.padding(6.dp))
        Text("Sin costos fijos definidos", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Toca + para agregar un costo recurrente", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

// ─── Form ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedBudgetFormScreen(
    itemId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: FixedBudgetViewModel,
    branchViewModel: BranchViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val branchState by branchViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    val existing = remember(itemId, state.itemList) {
        if (itemId != null) state.itemList.find { it.id == itemId } else null
    }
    val branches = branchState.branchList.filter { it.active }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && branchState.branchList.isEmpty()) {
            branchViewModel.loadBranches(companyId)
        }
    }

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var amountText by rememberSaveable {
        mutableStateOf(if (existing != null && existing.amount > 0) "%.2f".format(existing.amount) else "")
    }
    var selectedCategory by remember { mutableStateOf(existing?.category ?: ExpenseCategory.OTROS) }
    var selectedBranch by remember {
        mutableStateOf(if (existing != null) branches.find { it.id == existing.branchId } else null)
    }

    var nameError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var branchError by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var branchExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(branches) {
        if (existing != null && selectedBranch == null) {
            selectedBranch = branches.find { it.id == existing.branchId }
        }
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    fun buildItem() = FixedBudgetItem(
        id = existing?.id ?: "",
        branchId = selectedBranch?.id ?: "",
        branchName = selectedBranch?.name ?: "",
        name = name.trim().replaceFirstChar { it.uppercase() },
        category = selectedCategory,
        amount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0,
        notes = notes.trim().ifEmpty { null },
        active = true
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (itemId == null) "Nuevo Costo Fijo" else "Editar Costo Fijo",
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
                // ── Sucursal ──────────────────────────────────────────────────
                FbSectionHeader("Sucursal")

                ExposedDropdownMenuBox(
                    expanded = branchExpanded,
                    onExpandedChange = { branchExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedBranch?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sucursal *") },
                        placeholder = { Text("Selecciona una sucursal") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        isError = branchError,
                        supportingText = if (branchError) ({ Text("La sucursal es requerida") }) else null
                    )
                    ExposedDropdownMenu(expanded = branchExpanded, onDismissRequest = { branchExpanded = false }) {
                        branches.forEach { branch ->
                            DropdownMenuItem(
                                text = { Text(branch.name) },
                                onClick = { selectedBranch = branch; branchError = false; branchExpanded = false }
                            )
                        }
                        if (branches.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No hay sucursales activas", color = MaterialTheme.colorScheme.outline) },
                                onClick = { branchExpanded = false }
                            )
                        }
                    }
                }

                // ── Datos del costo ───────────────────────────────────────────
                FbSectionDivider()
                FbSectionHeader("Costo fijo recurrente")

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Descripción *") },
                    placeholder = { Text("Ej: Alquiler local, Planilla") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError,
                    supportingText = if (nameError) ({ Text("La descripción es requerida") }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; amountError = false },
                    label = { Text("Monto mensual (Q) *") },
                    placeholder = { Text("0.00") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = amountError,
                    supportingText = if (amountError) ({ Text("Ingresa un monto válido") }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                // ── Categoría ─────────────────────────────────────────────────
                FbSectionDivider()
                FbSectionHeader("Categoría")

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        ExpenseCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.label) },
                                onClick = { selectedCategory = cat; categoryExpanded = false }
                            )
                        }
                    }
                }

                // ── Notas ─────────────────────────────────────────────────────
                FbSectionDivider()
                FbSectionHeader("Notas")

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(Modifier.padding(8.dp))

                Button(
                    onClick = {
                        var hasError = false
                        if (name.isBlank()) { nameError = true; hasError = true }
                        if (selectedBranch == null) { branchError = true; hasError = true }
                        val amount = amountText.replace(",", ".").toDoubleOrNull()
                        if (amount == null || amount <= 0) { amountError = true; hasError = true }
                        if (hasError) return@Button
                        viewModel.saveItem(companyId, buildItem())
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
                        Text("Guardar Costo Fijo", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun FbSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun FbSectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
