package com.bakery.erp.presentation.employees

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AccessTime
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import com.bakery.erp.model.EmployeeStatus
import com.bakery.erp.model.JobPosition
import com.bakery.erp.model.User
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.ImagePickerButton
import kotlin.time.Clock

private val PrimaryBlue = Color(0xFF004F7C)

private val DAY_LABELS = listOf("L", "M", "X", "J", "V", "S", "D")
private val DAY_NUMBERS = listOf(1, 2, 3, 4, 5, 6, 7)
private val DEPARTMENTS = listOf(
    "Panadería",
    "Pastelería",
    "Repostería",
    "Producción",
    "Ventas",
    "Caja",
    "Despacho y Entregas",
    "Limpieza",
    "Administración"
)
private val CONTRACT_TYPES = listOf("Indefinido", "Plazo Fijo", "Tiempo Parcial", "Por Obra", "Otro")
private val PAYMENT_TYPES = listOf("Quincenal", "Mensual", "Semanal", "Jornal")
private val GENDERS = listOf("Masculino", "Femenino", "Prefiero no decir")
private val MARITAL_STATUSES = listOf("Soltero/a", "Casado/a", "Unión Libre", "Divorciado/a", "Viudo/a")
private val BANKS = listOf("Banrural", "G&T Continental", "Industrial", "Agromercantil", "BAC Credomatic", "BAM", "BI", "Otro")

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
                        containerColor = PrimaryBlue,
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
        jobPositions = state.jobPositions,
        users = state.users,
        isLoading = state.isLoading,
        error = state.error,
        onNavigateBack = onNavigateBack,
        onSave = { employee, photoBytes -> viewModel.saveEmployee(companyId, employee, photoBytes) },
        onDelete = if (!isNew) { { viewModel.deleteEmployee(companyId, employeeId!!) } } else null,
        isDeleted = state.isDeleted,
        clearDeleted = { viewModel.clearDeleted() }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EmployeeFormContent(
    existing: Employee?,
    isNew: Boolean,
    companyId: String,
    branches: List<Branch>,
    jobPositions: List<JobPosition>,
    users: List<User>,
    isLoading: Boolean,
    error: String?,
    onNavigateBack: () -> Unit,
    onSave: (Employee, ByteArray?) -> Unit,
    onDelete: (() -> Unit)?,
    isDeleted: Boolean,
    clearDeleted: () -> Unit
) {
    // ── Sección 1: Personal ───────────────────────────────────────────────────
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var employeeCode by rememberSaveable {
        mutableStateOf(
            if (isNew) "EMP-${(Clock.System.now().toEpochMilliseconds() % 100000).toString().padStart(5, '0')}"
            else existing?.employeeCode ?: ""
        )
    }
    var dpi by rememberSaveable { mutableStateOf(existing?.dpi ?: "") }
    var nit by rememberSaveable { mutableStateOf(existing?.nit ?: "") }
    var birthDateMillis by rememberSaveable { mutableStateOf<Long?>(existing?.birthDate?.takeIf { it > 0L }) }
    var selectedGender by rememberSaveable { mutableStateOf(existing?.gender ?: "") }
    var selectedMarital by rememberSaveable { mutableStateOf(existing?.maritalStatus ?: "") }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }

    // ── Sección 2: Contacto ───────────────────────────────────────────────────
    var phone by rememberSaveable { mutableStateOf(existing?.phone ?: "") }
    var phoneAux by rememberSaveable { mutableStateOf(existing?.phoneAux ?: "") }
    var email by rememberSaveable { mutableStateOf(existing?.email ?: "") }
    var address by rememberSaveable { mutableStateOf(existing?.address ?: "") }
    var emergencyContact by rememberSaveable { mutableStateOf(existing?.emergencyContact ?: "") }
    var emergencyPhone by rememberSaveable { mutableStateOf(existing?.emergencyPhone ?: "") }

    // ── Sección 3: Empleo ─────────────────────────────────────────────────────
    var selectedBranch by remember { mutableStateOf<Branch?>(null) }
    var selectedPosition by remember { mutableStateOf<JobPosition?>(null) }
    var selectedDepartment by rememberSaveable { mutableStateOf(existing?.department ?: "") }
    var selectedSupervisorUser by remember { mutableStateOf<User?>(null) }
    var selectedLinkedUser by remember { mutableStateOf<User?>(null) }
    var selectedContractType by rememberSaveable { mutableStateOf(existing?.contractType ?: "") }

    // ── Sección 4: Horario ────────────────────────────────────────────────────
    var workDays by remember { mutableStateOf(existing?.workDays ?: emptyList<Int>()) }
    var startTime by rememberSaveable { mutableStateOf(existing?.startTime ?: "") }
    var endTime by rememberSaveable { mutableStateOf(existing?.endTime ?: "") }

    // ── Sección 5: Financiero ─────────────────────────────────────────────────
    var selectedPaymentType by rememberSaveable { mutableStateOf(existing?.paymentType ?: "") }
    var selectedBank by rememberSaveable { mutableStateOf(existing?.bank ?: "") }
    var bankAccount by rememberSaveable { mutableStateOf(existing?.bankAccount ?: "") }
    var baseSalaryStr by rememberSaveable { mutableStateOf(if ((existing?.baseSalary ?: 0.0) > 0) existing!!.baseSalary.toString() else "") }

    // ── Sección 6: Estado ─────────────────────────────────────────────────────
    var selectedStatus by rememberSaveable { mutableStateOf(existing?.status ?: EmployeeStatus.ACTIVE) }
    var hireDateMillis by rememberSaveable { mutableStateOf<Long?>(existing?.hireDate?.takeIf { it > 0L }) }
    var endDateMillis by rememberSaveable { mutableStateOf<Long?>(existing?.endDate?.takeIf { it > 0L }) }

    // ── Sección 7: Notas ──────────────────────────────────────────────────────
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }

    // ── Dropdown expand state ─────────────────────────────────────────────────
    var branchExpanded by remember { mutableStateOf(false) }
    var positionExpanded by remember { mutableStateOf(false) }
    var departmentExpanded by remember { mutableStateOf(false) }
    var supervisorExpanded by remember { mutableStateOf(false) }
    var linkedUserExpanded by remember { mutableStateOf(false) }
    var genderExpanded by remember { mutableStateOf(false) }
    var maritalExpanded by remember { mutableStateOf(false) }
    var contractExpanded by remember { mutableStateOf(false) }
    var paymentExpanded by remember { mutableStateOf(false) }
    var bankExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }

    // ── Date / time picker state ──────────────────────────────────────────────
    var showBirthPicker by remember { mutableStateOf(false) }
    var showHirePicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Initialize selects from existing data
    LaunchedEffect(branches, jobPositions, users, existing) {
        if (selectedBranch == null && branches.isNotEmpty()) {
            selectedBranch = when {
                existing != null -> branches.find { it.id == existing.branchId }
                branches.size == 1 -> branches.first()
                else -> null
            }
        }
        if (selectedPosition == null && jobPositions.isNotEmpty() && existing != null) {
            selectedPosition = jobPositions.find { it.id == existing.positionId }
        }
        if (selectedSupervisorUser == null && users.isNotEmpty() && existing != null && existing.supervisorId.isNotBlank()) {
            selectedSupervisorUser = users.find { it.id == existing.supervisorId }
        }
        if (selectedLinkedUser == null && users.isNotEmpty() && existing != null && existing.userId.isNotBlank()) {
            selectedLinkedUser = users.find { it.id == existing.userId }
        }
    }

    LaunchedEffect(isDeleted) {
        if (isDeleted) { clearDeleted(); onNavigateBack() }
    }

    // ── Time picker dialogs ───────────────────────────────────────────────────
    if (showStartTimePicker) {
        val parts = startTime.split(":").mapNotNull { it.toIntOrNull() }
        TimePickerDialog(
            initialHour = parts.getOrElse(0) { 8 },
            initialMinute = parts.getOrElse(1) { 0 },
            onDismiss = { showStartTimePicker = false },
            onConfirm = { h, m ->
                startTime = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
                showStartTimePicker = false
            }
        )
    }
    if (showEndTimePicker) {
        val parts = endTime.split(":").mapNotNull { it.toIntOrNull() }
        TimePickerDialog(
            initialHour = parts.getOrElse(0) { 17 },
            initialMinute = parts.getOrElse(1) { 0 },
            onDismiss = { showEndTimePicker = false },
            onConfirm = { h, m ->
                endTime = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
                showEndTimePicker = false
            }
        )
    }

    // ── Date pickers ──────────────────────────────────────────────────────────
    if (showBirthPicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = birthDateMillis)
        DatePickerDialog(
            onDismissRequest = { showBirthPicker = false },
            confirmButton = { TextButton(onClick = { birthDateMillis = dpState.selectedDateMillis; showBirthPicker = false }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { showBirthPicker = false }) { Text("Cancelar") } }
        ) { DatePicker(dpState) }
    }
    if (showHirePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = hireDateMillis)
        DatePickerDialog(
            onDismissRequest = { showHirePicker = false },
            confirmButton = { TextButton(onClick = { hireDateMillis = dpState.selectedDateMillis; showHirePicker = false }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { showHirePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(dpState) }
    }
    if (showEndPicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = endDateMillis)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = { TextButton(onClick = { endDateMillis = dpState.selectedDateMillis; showEndPicker = false }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancelar") } }
        ) { DatePicker(dpState) }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Dar de baja al colaborador") },
            text = { Text("¿Dar de baja a \"${existing?.name}\"? Se marcará con estado Despedido.") },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(); showDeleteDialog = false }) {
                    Text("Confirmar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Nuevo Colaborador" else "Editar Colaborador", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    if (!isNew && onDelete != null) {
                        TextButton(onClick = { showDeleteDialog = true }) {
                            Text("Dar de baja", color = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
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

                // ── 1. Foto de perfil ─────────────────────────────────────────
                EmpFormSection("Foto de Perfil")
                ImagePickerButton(
                    currentImageUrl = existing?.photoUrl?.takeIf { it.isNotBlank() },
                    selectedBytes = photoBytes,
                    onImageSelected = { photoBytes = it }
                )

                // ── 2. Datos Personales ───────────────────────────────────────
                EmpFormSection("Datos Personales")

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre completo *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = employeeCode,
                        onValueChange = { employeeCode = it },
                        label = { Text("Código empleado") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        supportingText = if (isNew) { { Text("Auto-generado", style = MaterialTheme.typography.labelSmall) } } else null
                    )
                    OutlinedTextField(
                        value = dpi,
                        onValueChange = { dpi = it },
                        label = { Text("DPI") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nit,
                        onValueChange = { nit = it },
                        label = { Text("NIT") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = birthDateMillis?.let { formatEmployeeDate(it) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha de nacimiento") },
                        trailingIcon = {
                            IconButton(onClick = { showBirthPicker = true }) {
                                Icon(Icons.Default.CalendarToday, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.weight(1f).clickable { showBirthPicker = true }
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmpDropdown(
                        label = "Género",
                        value = selectedGender,
                        options = GENDERS,
                        expanded = genderExpanded,
                        onExpandedChange = { genderExpanded = it },
                        onSelect = { selectedGender = it; genderExpanded = false },
                        modifier = Modifier.weight(1f)
                    )
                    EmpDropdown(
                        label = "Estado civil",
                        value = selectedMarital,
                        options = MARITAL_STATUSES,
                        expanded = maritalExpanded,
                        onExpandedChange = { maritalExpanded = it },
                        onSelect = { selectedMarital = it; maritalExpanded = false },
                        modifier = Modifier.weight(1f)
                    )
                }

                // ── 3. Contacto ───────────────────────────────────────────────
                EmpFormSection("Contacto")

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Dirección") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

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

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = emergencyContact,
                        onValueChange = { emergencyContact = it },
                        label = { Text("Contacto emergencia") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )
                    OutlinedTextField(
                        value = emergencyPhone,
                        onValueChange = { emergencyPhone = it },
                        label = { Text("Tel. emergencia") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }

                // ── 4. Empleo ─────────────────────────────────────────────────
                EmpFormSection("Empleo")

                when {
                    branches.size == 1 -> OutlinedTextField(
                        value = branches.first().name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sucursal asignada") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    )
                    branches.size > 1 -> EmpDropdownCustom(
                        label = "Sucursal asignada *",
                        displayValue = selectedBranch?.name ?: "",
                        expanded = branchExpanded,
                        onExpandedChange = { branchExpanded = it },
                        isError = selectedBranch == null
                    ) {
                        branches.forEach { branch ->
                            DropdownMenuItem(
                                text = { Text(branch.name) },
                                onClick = { selectedBranch = branch; branchExpanded = false }
                            )
                        }
                    }
                }

                if (jobPositions.isNotEmpty()) {
                    EmpDropdownCustom(
                        label = "Puesto",
                        displayValue = selectedPosition?.title ?: "",
                        expanded = positionExpanded,
                        onExpandedChange = { positionExpanded = it }
                    ) {
                        DropdownMenuItem(text = { Text("— Sin puesto —") }, onClick = {
                            selectedPosition = null
                            positionExpanded = false
                        })
                        jobPositions.forEach { pos ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(pos.title, style = MaterialTheme.typography.bodyMedium)
                                        if (pos.department.isNotBlank()) {
                                            Text(pos.department, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                onClick = {
                                    selectedPosition = pos
                                    if (selectedDepartment.isBlank()) selectedDepartment = pos.department
                                    if (baseSalaryStr.isBlank() && pos.minSalary > 0) baseSalaryStr = pos.minSalary.toString()
                                    positionExpanded = false
                                }
                            )
                        }
                    }
                }

                EmpDropdown(
                    label = "Departamento",
                    value = selectedDepartment,
                    options = DEPARTMENTS,
                    expanded = departmentExpanded,
                    onExpandedChange = { departmentExpanded = it },
                    onSelect = { selectedDepartment = it; departmentExpanded = false }
                )

                if (users.isNotEmpty()) {
                    EmpDropdownCustom(
                        label = "Jefe inmediato",
                        displayValue = selectedSupervisorUser?.displayName ?: "",
                        expanded = supervisorExpanded,
                        onExpandedChange = { supervisorExpanded = it }
                    ) {
                        DropdownMenuItem(text = { Text("— Sin jefe —") }, onClick = {
                            selectedSupervisorUser = null
                            supervisorExpanded = false
                        })
                        users.forEach { user ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(user.displayName, style = MaterialTheme.typography.bodyMedium)
                                        Text(user.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = { selectedSupervisorUser = user; supervisorExpanded = false }
                            )
                        }
                    }
                }

                EmpDropdown(
                    label = "Tipo de contrato",
                    value = selectedContractType,
                    options = CONTRACT_TYPES,
                    expanded = contractExpanded,
                    onExpandedChange = { contractExpanded = it },
                    onSelect = { selectedContractType = it; contractExpanded = false }
                )

                // ── 5. Horario ────────────────────────────────────────────────
                EmpFormSection("Horario")

                Text("Días laborales", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DAY_NUMBERS.forEachIndexed { index, dayNum ->
                        FilterChip(
                            selected = dayNum in workDays,
                            onClick = {
                                workDays = if (dayNum in workDays) workDays - dayNum else workDays + dayNum
                            },
                            label = { Text(DAY_LABELS[index], fontWeight = FontWeight.Medium) }
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hora entrada") },
                        placeholder = { Text("08:00") },
                        trailingIcon = {
                            IconButton(onClick = { showStartTimePicker = true }) {
                                Icon(Icons.Default.AccessTime, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.weight(1f).clickable { showStartTimePicker = true }
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hora salida") },
                        placeholder = { Text("17:00") },
                        trailingIcon = {
                            IconButton(onClick = { showEndTimePicker = true }) {
                                Icon(Icons.Default.AccessTime, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.weight(1f).clickable { showEndTimePicker = true }
                    )
                }

                // ── 6. Financiero ─────────────────────────────────────────────
                EmpFormSection("Financiero")

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmpDropdown(
                        label = "Tipo de pago",
                        value = selectedPaymentType,
                        options = PAYMENT_TYPES,
                        expanded = paymentExpanded,
                        onExpandedChange = { paymentExpanded = it },
                        onSelect = { selectedPaymentType = it; paymentExpanded = false },
                        modifier = Modifier.weight(1f)
                    )
                    EmpDropdown(
                        label = "Banco",
                        value = selectedBank,
                        options = BANKS,
                        expanded = bankExpanded,
                        onExpandedChange = { bankExpanded = it },
                        onSelect = { selectedBank = it; bankExpanded = false },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = bankAccount,
                    onValueChange = { bankAccount = it },
                    label = { Text("Número de cuenta") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = baseSalaryStr,
                    onValueChange = { baseSalaryStr = it },
                    label = { Text("Salario base (Q)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("Q") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                // ── 7. Estado ─────────────────────────────────────────────────
                EmpFormSection("Estado Laboral")

                EmpDropdown(
                    label = "Estado",
                    value = selectedStatus.label(),
                    options = EmployeeStatus.entries.map { it.label() },
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = it },
                    onSelect = { label ->
                        selectedStatus = EmployeeStatus.entries.first { it.label() == label }
                        statusExpanded = false
                    }
                )

                OutlinedTextField(
                    value = hireDateMillis?.let { formatEmployeeDate(it) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fecha de ingreso") },
                    placeholder = { Text("Seleccionar fecha") },
                    trailingIcon = {
                        IconButton(onClick = { showHirePicker = true }) {
                            Icon(Icons.Default.CalendarToday, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { showHirePicker = true }
                )

                if (selectedStatus != EmployeeStatus.ACTIVE && selectedStatus != EmployeeStatus.ON_VACATION) {
                    OutlinedTextField(
                        value = endDateMillis?.let { formatEmployeeDate(it) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha de baja") },
                        placeholder = { Text("Seleccionar fecha") },
                        trailingIcon = {
                            IconButton(onClick = { showEndPicker = true }) {
                                Icon(Icons.Default.CalendarToday, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { showEndPicker = true }
                    )
                }

                // ── 8. Vinculación de cuenta ──────────────────────────────────
                if (users.isNotEmpty()) {
                    EmpFormSection("Cuenta del Sistema")

                    EmpDropdownCustom(
                        label = "Vincular con usuario",
                        displayValue = selectedLinkedUser?.displayName ?: "",
                        expanded = linkedUserExpanded,
                        onExpandedChange = { linkedUserExpanded = it }
                    ) {
                        DropdownMenuItem(text = { Text("— Sin vincular —") }, onClick = {
                            selectedLinkedUser = null
                            linkedUserExpanded = false
                        })
                        users.forEach { user ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(user.displayName, style = MaterialTheme.typography.bodyMedium)
                                        Text(user.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = { selectedLinkedUser = user; linkedUserExpanded = false }
                            )
                        }
                    }

                    Text(
                        "Vincula este colaborador con una cuenta de acceso al sistema para evitar duplicados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ── 9. Notas ──────────────────────────────────────────────────
                EmpFormSection("Notas")

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observaciones") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(8.dp))

                val branch = if (branches.size == 1) branches.first() else selectedBranch

                Button(
                    onClick = {
                        if (name.isBlank()) return@Button
                        val b = branch ?: return@Button
                        val pos = selectedPosition
                        onSave(
                            Employee(
                                id = existing?.id ?: "",
                                name = name.trim(),
                                employeeCode = employeeCode.trim(),
                                photoUrl = existing?.photoUrl ?: "",
                                dpi = dpi.trim(),
                                nit = nit.trim(),
                                birthDate = birthDateMillis ?: 0L,
                                gender = selectedGender,
                                maritalStatus = selectedMarital,
                                phone = phone.trim(),
                                phoneAux = phoneAux.trim(),
                                email = email.trim(),
                                address = address.trim(),
                                emergencyContact = emergencyContact.trim(),
                                emergencyPhone = emergencyPhone.trim(),
                                branchId = b.id,
                                branchName = b.name,
                                department = selectedDepartment,
                                positionId = pos?.id ?: "",
                                positionTitle = pos?.title ?: "",
                                supervisorId = selectedSupervisorUser?.id ?: "",
                                supervisorName = selectedSupervisorUser?.displayName ?: "",
                                contractType = selectedContractType,
                                workDays = workDays.sorted(),
                                startTime = startTime.trim(),
                                endTime = endTime.trim(),
                                paymentType = selectedPaymentType,
                                bank = selectedBank,
                                bankAccount = bankAccount.trim(),
                                baseSalary = baseSalaryStr.toDoubleOrNull() ?: 0.0,
                                status = selectedStatus,
                                hireDate = hireDateMillis ?: 0L,
                                endDate = endDateMillis ?: 0L,
                                notes = notes.trim(),
                                active = selectedStatus == EmployeeStatus.ACTIVE,
                                createdAt = existing?.createdAt ?: 0L,
                                userId = selectedLinkedUser?.id ?: ""
                            ),
                            photoBytes
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
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

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun EmployeeStatus.label(): String = when (this) {
    EmployeeStatus.ACTIVE -> "Activo"
    EmployeeStatus.SUSPENDED -> "Suspendido"
    EmployeeStatus.ON_VACATION -> "Vacaciones"
    EmployeeStatus.FIRED -> "Despedido"
    EmployeeStatus.RESIGNED -> "Renunció"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar hora") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun EmpFormSection(title: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = PrimaryBlue,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = PrimaryBlue.copy(alpha = 0.15f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmpDropdown(
    label: String,
    value: String,
    options: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmpDropdownCustom(
    label: String,
    displayValue: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isError: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable () -> Unit
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange, modifier = modifier) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            isError = isError,
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            content()
        }
    }
}
