package com.bakery.erp.presentation.pos.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Branch
import com.bakery.erp.model.CashRegister
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashRegisterFormScreen(
    registerId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CashRegisterAdminViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val user = authState.user
    val snackbar = remember { SnackbarHostState() }
    val isEdit = registerId != null
    val needsLoad = isEdit && (state.selectedRegister == null || state.selectedRegister?.id != registerId)
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(companyId, registerId) {
        if (companyId.isNotEmpty() && user != null) {
            if (state.branches.isEmpty()) viewModel.load(companyId, user)
            if (registerId != null) viewModel.loadRegister(companyId, registerId)
            else viewModel.clearSelected()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.isSaved, state.isDeleted) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
        if (state.isDeleted) { viewModel.clearDeleted(); onNavigateBack() }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar caja") },
            text = {
                Text(
                    "Si esta caja nunca se usó se eliminará por completo; si ya tiene ventas " +
                    "registradas se archivará (se conserva su historial). No se puede eliminar si " +
                    "tiene una sesión abierta o un cuadre del día pendiente."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.attemptDeleteRegister(companyId, registerId!!)
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Editar caja" else "Nueva caja", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (isEdit && state.selectedRegister != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, "Eliminar")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (needsLoad && state.isDetailLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            CashRegisterFormContent(
                existing = if (isEdit) state.selectedRegister else null,
                branches = state.branches,
                onSave = { reg -> viewModel.saveRegister(companyId, reg) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashRegisterFormContent(
    existing: CashRegister?,
    branches: List<Branch>,
    onSave: (CashRegister) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var branchId by rememberSaveable { mutableStateOf(existing?.branchId.orEmpty()) }
    var active by rememberSaveable { mutableStateOf(existing?.active ?: true) }
    val branch = remember(branches, branchId) { branches.firstOrNull { it.id == branchId } }
    var branchPickerOpen by remember { mutableStateOf(false) }
    // Horarios de cuadre de esta caja ("HH:mm"). Por caja y no por sucursal: cada caja cuadra a su hora.
    var schedules by remember { mutableStateOf(existing?.closeSchedules.orEmpty().sortedBy { com.bakery.erp.model.parseScheduleMinutes(it) ?: 0 }) }
    var toleranceText by rememberSaveable {
        mutableStateOf((existing?.closeToleranceMinutes ?: com.bakery.erp.model.DEFAULT_CLOSE_TOLERANCE_MINUTES).toString())
    }
    var timePickerOpen by remember { mutableStateOf(false) }
    val tolerance = toleranceText.toIntOrNull()?.coerceIn(0, 180) ?: com.bakery.erp.model.DEFAULT_CLOSE_TOLERANCE_MINUTES

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre de la caja") },
            placeholder = { Text("Ej. Caja 1, Caja Mostrador") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        ExposedDropdownMenuBox(
            expanded = branchPickerOpen,
            onExpandedChange = { branchPickerOpen = it }
        ) {
            OutlinedTextField(
                value = branch?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Sucursal") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchPickerOpen) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = branchPickerOpen,
                onDismissRequest = { branchPickerOpen = false }
            ) {
                branches.forEach { b ->
                    DropdownMenuItem(
                        text = { Text(b.name) },
                        onClick = { branchId = b.id; branchPickerOpen = false }
                    )
                }
            }
        }

        // ── Horarios de cuadre ──
        Text("Horarios de cuadre", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Text(
            "Entrar al cierre de esta caja fuera de estos horarios queda marcado en la bitácora, " +
                "pide confirmación y le avisa al administrador. Sin horarios, no se controla.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            schedules.forEach { t ->
                InputChip(
                    selected = true,
                    onClick = { schedules = schedules - t },
                    label = { Text(com.bakery.erp.model.closeScheduleLabel(listOf(t), tolerance).substringBefore(" (")) },
                    trailingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Close, "Quitar", Modifier.size(16.dp)) }
                )
            }
            AssistChip(onClick = { timePickerOpen = true }, label = { Text("Agregar horario") })
        }
        OutlinedTextField(
            value = toleranceText,
            onValueChange = { toleranceText = it.filter { c -> c.isDigit() }.take(3) },
            label = { Text("Tolerancia (minutos antes y después)") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (schedules.isNotEmpty()) {
            Text("Se permite cerrar: ${com.bakery.erp.model.closeScheduleLabel(schedules, tolerance)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = active, onCheckedChange = { active = it })
            Spacer(Modifier.width(8.dp))
            Text(if (active) "Activa" else "Inactiva")
        }

        Button(
            onClick = {
                val b = branch ?: return@Button
                onSave(
                    (existing ?: CashRegister()).copy(
                        id = existing?.id.orEmpty(),
                        branchId = b.id,
                        branchName = b.name,
                        name = name.trim(),
                        active = active,
                        closeSchedules = schedules,
                        closeToleranceMinutes = tolerance
                    )
                )
            },
            enabled = name.trim().isNotEmpty() && branch != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar")
        }
    }

    if (timePickerOpen) {
        com.bakery.erp.ui.TimePickerDialog(
            initialHour = 21,
            initialMinute = 0,
            title = "Horario de cuadre",
            onDismiss = { timePickerOpen = false },
            onConfirm = { h, m ->
                val t = com.bakery.erp.ui.formatHHmm(h, m)
                schedules = (schedules + t).distinct().sortedBy { com.bakery.erp.model.parseScheduleMinutes(it) ?: 0 }
                timePickerOpen = false
            }
        )
    }
}
