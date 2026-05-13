package com.bakery.erp.presentation.employees

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakery.erp.model.Branch
import com.bakery.erp.model.Employee
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeFormScreen(
    employeeId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: EmployeeViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val isNew = employeeId == null

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && state.branches.isEmpty()) {
            viewModel.loadEmployees(companyId)
        }
    }

    LaunchedEffect(employeeId, companyId) {
        if (employeeId != null && companyId.isNotEmpty()) {
            viewModel.loadEmployeeDetail(companyId, employeeId)
        }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }

    if (!isNew && state.isDetailLoading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Editar Colaborador", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF123456),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            },
            containerColor = Color(0xFFFDFBF7)
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val existing = state.selectedEmployee?.takeIf { it.id == employeeId }

    EmployeeFormContent(
        existing = existing,
        isNew = isNew,
        companyId = companyId,
        branches = state.branches,
        isLoading = state.isLoading,
        error = state.error,
        onNavigateBack = onNavigateBack,
        onSave = { employee -> viewModel.saveEmployee(companyId, employee) },
        onDelete = if (!isNew) { { viewModel.deleteEmployee(companyId, employeeId!!) } } else null,
        isDeleted = state.isDeleted,
        clearDeleted = { viewModel.clearDeleted() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeFormContent(
    existing: Employee?,
    isNew: Boolean,
    companyId: String,
    branches: List<Branch>,
    isLoading: Boolean,
    error: String?,
    onNavigateBack: () -> Unit,
    onSave: (Employee) -> Unit,
    onDelete: (() -> Unit)?,
    isDeleted: Boolean,
    clearDeleted: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var position by rememberSaveable { mutableStateOf(existing?.position ?: "") }
    var phone by rememberSaveable { mutableStateOf(existing?.phone ?: "") }
    var phoneAux by rememberSaveable { mutableStateOf(existing?.phoneAux ?: "") }
    var email by rememberSaveable { mutableStateOf(existing?.email ?: "") }
    var address by rememberSaveable { mutableStateOf(existing?.address ?: "") }
    var hireDateMillis by rememberSaveable {
        mutableStateOf<Long?>(existing?.hireDate?.takeIf { it > 0L })
    }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }

    var selectedBranch by remember { mutableStateOf<Branch?>(null) }
    var branchDropdownExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(branches, existing) {
        if (selectedBranch == null && branches.isNotEmpty()) {
            selectedBranch = when {
                existing != null -> branches.find { it.id == existing.branchId }
                branches.size == 1 -> branches.first()
                else -> null
            }
        }
    }

    LaunchedEffect(isDeleted) {
        if (isDeleted) { clearDeleted(); onNavigateBack() }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar colaborador") },
            text = { Text("¿Eliminar a \"${existing?.name}\"? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(); showDeleteDialog = false }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = hireDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hireDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNew) "Nuevo Colaborador" else "Editar Colaborador",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    if (!isNew && onDelete != null) {
                        TextButton(onClick = { showDeleteDialog = true }) {
                            Text("Eliminar", color = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF123456),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFFDFBF7)
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ── Información Personal ──────────────────────────────────────
                FormSectionHeader("Información Personal")

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre completo *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    )
                )

                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it },
                    label = { Text("Cargo") },
                    placeholder = { Text("Ej: Panadero, Vendedor, Encargado") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    )
                )

                // ── Sucursal ──────────────────────────────────────────────────
                FormSectionHeader("Asignación")

                when {
                    branches.size == 1 -> OutlinedTextField(
                        value = branches.first().name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sucursal asignada") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    )
                    branches.size > 1 -> ExposedDropdownMenuBox(
                        expanded = branchDropdownExpanded,
                        onExpandedChange = { branchDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedBranch?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Sucursal asignada *") },
                            placeholder = { Text("Seleccionar sucursal") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(branchDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            isError = selectedBranch == null
                        )
                        ExposedDropdownMenu(
                            expanded = branchDropdownExpanded,
                            onDismissRequest = { branchDropdownExpanded = false }
                        ) {
                            branches.forEach { branch ->
                                DropdownMenuItem(
                                    text = { Text(branch.name) },
                                    onClick = { selectedBranch = branch; branchDropdownExpanded = false }
                                )
                            }
                        }
                    }
                }

                // ── Contacto ──────────────────────────────────────────────────
                FormSectionHeader("Contacto")

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Teléfono") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    OutlinedTextField(
                        value = phoneAux,
                        onValueChange = { phoneAux = it },
                        label = { Text("Tel. auxiliar") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Dirección") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                // ── Contratación ──────────────────────────────────────────────
                FormSectionHeader("Contratación")

                OutlinedTextField(
                    value = hireDateMillis?.let { formatEmployeeDate(it) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fecha de ingreso") },
                    placeholder = { Text("Seleccionar fecha") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, "Seleccionar fecha", tint = Color(0xFF123456))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                )

                // ── Notas ─────────────────────────────────────────────────────
                FormSectionHeader("Notas")

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observaciones") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(8.dp))

                // ── Guardar ───────────────────────────────────────────────────
                val branch = if (branches.size == 1) branches.first() else selectedBranch

                Button(
                    onClick = {
                        val b = branch ?: return@Button
                        if (name.isBlank()) return@Button
                        val employee = Employee(
                            id = existing?.id ?: "",
                            name = name.trim(),
                            branchId = b.id,
                            branchName = b.name,
                            position = position.trim(),
                            phone = phone.trim(),
                            phoneAux = phoneAux.trim(),
                            email = email.trim(),
                            address = address.trim(),
                            hireDate = hireDateMillis ?: 0L,
                            notes = notes.trim(),
                            active = true,
                            createdAt = existing?.createdAt ?: 0L
                        )
                        onSave(employee)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123456)),
                    enabled = name.isNotBlank() && branch != null && !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                    else Text("Guardar colaborador", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FormSectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = Color(0xFF123456),
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp),
            color = Color(0xFF123456).copy(alpha = 0.15f)
        )
    }
}
