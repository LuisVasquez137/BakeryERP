package com.bakery.erp.presentation.expenses

import com.bakery.erp.ui.BrandTopAppBar

import com.bakery.erp.model.splitsStore
import com.bakery.erp.util.fmt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.bakery.erp.model.Branch
import com.bakery.erp.model.CostCenter
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.model.Expense
import com.bakery.erp.model.ExpenseCategory
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.branches.BranchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormScreen(
    expenseId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ExpenseViewModel,
    branchViewModel: BranchViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val branchState by branchViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    val existing = remember(expenseId, state.expenseList) {
        if (expenseId != null) state.expenseList.find { it.id == expenseId } else null
    }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && branchState.branchList.isEmpty()) {
            branchViewModel.loadBranches(companyId)
        }
    }

    val branches = branchState.branchList.filter { it.active }

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var amountText by rememberSaveable { mutableStateOf(if (existing != null && existing.amount > 0) existing.amount.fmt(2) else "") }
    var period by rememberSaveable { mutableStateOf(existing?.period ?: state.selectedPeriod) }
    var selectedCategory by remember { mutableStateOf(existing?.category ?: ExpenseCategory.OTROS) }
    var selectedBranch by remember {
        mutableStateOf(
            if (existing != null) branches.find { it.id == existing.branchId }
            else null
        )
    }

    var costCenter by rememberSaveable { mutableStateOf(existing?.costCenter ?: CostCenter.BRANCH) }

    var nameError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var branchError by remember { mutableStateOf(false) }
    var periodError by remember { mutableStateOf(false) }

    var categoryExpanded by remember { mutableStateOf(false) }
    var branchExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // Update selectedBranch once branches load
    LaunchedEffect(branches) {
        if (existing != null && selectedBranch == null) {
            selectedBranch = branches.find { it.id == existing.branchId }
        }
    }

    fun build(): Expense {
        val branch = selectedBranch
        val me = authState.user
        // Editar parte del gasto existente: el repositorio hace set(), y los campos que este
        // formulario no muestra (compra de origen, caja, foto, autor, alta) se perdían.
        val base = existing ?: Expense(
            createdByUserId = me?.id.orEmpty(),
            createdByUserName = me?.displayName.orEmpty().ifBlank { me?.email.orEmpty() },
            createdByEmployeeId = me?.operatorEmployeeId.orEmpty()
        )
        return base.copy(
            id = existing?.id ?: "",
            branchId = branch?.id ?: "",
            branchName = branch?.name ?: "",
            costCenter = costCenter,
            name = name.trim().replaceFirstChar { it.uppercase() },
            category = selectedCategory,
            amount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0,
            period = period.trim(),
            notes = notes.trim().ifEmpty { null }
        )
    }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = {
                    Text(
                        if (expenseId == null) "Nuevo Gasto" else "Editar Gasto",
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
        AdaptiveContainer(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── Sucursal ──────────────────────────────────────────────────
                SectionHeader("Sucursal")

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
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        isError = branchError,
                        supportingText = if (branchError) ({ Text("La sucursal es requerida") }) else null
                    )
                    ExposedDropdownMenu(expanded = branchExpanded, onDismissRequest = { branchExpanded = false }) {
                        branches.forEach { branch ->
                            DropdownMenuItem(
                                text = { Text(branch.name) },
                                onClick = {
                                    selectedBranch = branch
                                    branchError = false
                                    branchExpanded = false
                                }
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

                CostCenterPicker(
                    selected = costCenter,
                    branchLabel = selectedBranch?.name.orEmpty(),
                    splitsStore = selectedBranch?.splitsStore == true,
                    onSelect = { costCenter = it }
                )

                // ── Datos del gasto ───────────────────────────────────────────
                SectionDivider()
                SectionHeader("Gasto")

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Descripción *") },
                    placeholder = { Text("Ej: Alquiler mes de mayo") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError,
                    supportingText = if (nameError) ({ Text("La descripción es requerida") }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

                OutlinedTextField(
                    value = period,
                    onValueChange = { period = it; periodError = false },
                    label = { Text("Período (AAAA-MM) *") },
                    placeholder = { Text("Ej: 2025-05") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = periodError,
                    supportingText = if (periodError) ({ Text("Ingresa el período en formato AAAA-MM") }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; amountError = false },
                    label = { Text("Monto (Q) *") },
                    placeholder = { Text("0.00") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = amountError,
                    supportingText = if (amountError) ({ Text("Ingresa un monto válido") }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                // ── Categoría ─────────────────────────────────────────────────
                SectionDivider()
                SectionHeader("Categoría")

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
                        // Acá SÍ se ofrece la lista entera, a diferencia de la captura: esta
                        // pantalla solo edita gastos que ya existen, incluidos los que nacieron
                        // como compromiso del mes, y recortarla dejaría sin forma de corregir
                        // uno mal clasificado.
                        ExpenseCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.label) },
                                onClick = { selectedCategory = cat; categoryExpanded = false }
                            )
                        }
                    }
                }

                // ── Notas ─────────────────────────────────────────────────────
                SectionDivider()
                SectionHeader("Notas")

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
                        if (period.isBlank() || !period.matches(Regex("\\d{4}-\\d{2}"))) { periodError = true; hasError = true }
                        if (hasError) return@Button
                        viewModel.saveExpense(companyId, build())
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
                        Text("Guardar Gasto", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.padding(16.dp))
            }
        }
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
