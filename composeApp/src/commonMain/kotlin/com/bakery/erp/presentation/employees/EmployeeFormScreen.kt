package com.bakery.erp.presentation.employees

import com.bakery.erp.model.splitsStore
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import com.bakery.erp.model.DocumentType
import com.bakery.erp.model.EmployeeDocument
import com.bakery.erp.model.Permission
import com.bakery.erp.model.UserRole
import com.bakery.erp.domain.pinProblem
import com.bakery.erp.presentation.users.PermissionChecklist
import com.bakery.erp.ui.DocumentPickerButton
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.hasPermission
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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material3.Switch
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
import com.bakery.erp.model.CostCenter
import com.bakery.erp.presentation.expenses.CostCenterPicker
import com.bakery.erp.model.Employee
import com.bakery.erp.model.EmployeeStatus
import com.bakery.erp.model.JobPosition
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.ImagePickerButton
import com.bakery.erp.ui.LoadingOverlay
import kotlin.time.Clock
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors


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
    /** Tras un alta: abrir la ficha con sus pestañas (documentos, etc.) en lugar de volver a la lista. */
    onCreated: (String) -> Unit = { onNavigateBack() },
    authViewModel: AuthViewModel,
    viewModel: EmployeeViewModel,
    /** Para aplicar el PIN y los permisos del alta en cuanto la ficha existe. */
    accessViewModel: EmployeeAccessViewModel? = null
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
    // PIN y permisos del alta: van por la Cloud Function / el VM de acceso, y necesitan el id.
    var pendingAccess by remember { mutableStateOf<NewEmployeeExtras?>(null) }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            val newId = state.savedEmployeeId
            viewModel.clearSaved()
            if (isNew && newId != null) {
                pendingAccess?.takeIf { it.permissions.isNotEmpty() || it.pin != null }?.let { a ->
                    accessViewModel?.setupNewEmployee(companyId, newId, a.permissions, a.pin, a.pinTypedByEmployee)
                }
                onCreated(newId)
            } else onNavigateBack()
        }
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
            containerColor = BrandColors.Ivory
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
        // Solo las sucursales a las que el usuario tiene acceso: si no, la encargada
        // de Democracia podría dar de alta (o mover) a alguien en otra sucursal.
        branches = state.branches.filter { b ->
            authState.user?.canAccessBranch(b.id) == true || (!isNew && b.id == state.selectedEmployee?.branchId)
        },
        jobPositions = state.jobPositions,
        employees = state.employees,
        canSeeSalary = authState.user?.hasPermission(com.bakery.erp.model.Permission.VIEW_EMPLOYEE_SALARY) == true,
        isLoading = state.isLoading,
        error = state.error,
        onNavigateBack = onNavigateBack,
        // Las demás pestañas solo en el alta: al editar, eso vive en la ficha (detalle).
        canAttachDocuments = isNew && authState.user?.hasPermission(Permission.VIEW_EMPLOYEE_DOCUMENTS) == true,
        canSetupAccess = isNew && accessViewModel != null &&
            (authState.user?.role == UserRole.OWNER || authState.user?.role == UserRole.ADMIN) &&
            authState.user?.hasPermission(Permission.VIEW_EMPLOYEE_ACCESS) == true,
        onSave = { employee, photoBytes, extras ->
            pendingAccess = extras
            viewModel.saveEmployee(companyId, employee, photoBytes, extras.documents)
        },
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
    /** Para "Jefe inmediato": el jefe es una persona (colaborador), no una cuenta. */
    employees: List<Employee>,
    /** Sin VIEW_EMPLOYEE_SALARY el salario ni se muestra ni se cambia (se guarda el que había). */
    canSeeSalary: Boolean,
    isLoading: Boolean,
    error: String?,
    onNavigateBack: () -> Unit,
    /** Alta: pestaña Documentos (se suben al guardar). */
    canAttachDocuments: Boolean,
    /** Alta: pestaña Acceso (PIN y permisos), solo dueño/administrador. */
    canSetupAccess: Boolean,
    onSave: (Employee, ByteArray?, NewEmployeeExtras) -> Unit,
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
    // A quién se le carga el sueldo: el piloto marca en la planta pero es de Reparto. Ver
    // `CostCenter`.
    var costCenter by rememberSaveable { mutableStateOf(existing?.costCenter ?: CostCenter.BRANCH) }
    var selectedPosition by remember { mutableStateOf<JobPosition?>(null) }
    var selectedDepartment by rememberSaveable { mutableStateOf(existing?.department ?: "") }
    // Jefe inmediato: id + nombre guardados tal cual. Las fichas viejas pueden traer el id de
    // una CUENTA (antes la lista era de usuarios): se respeta mientras nadie lo cambie.
    var supervisorId by rememberSaveable { mutableStateOf(existing?.supervisorId ?: "") }
    var supervisorName by rememberSaveable { mutableStateOf(existing?.supervisorName ?: "") }
    var selectedContractType by rememberSaveable { mutableStateOf(existing?.contractType ?: "") }

    // ── Sección 4: Horario ────────────────────────────────────────────────────
    var workDays by remember { mutableStateOf(existing?.workDays ?: emptyList<Int>()) }
    var startTime by rememberSaveable { mutableStateOf(existing?.startTime ?: "") }
    var endTime by rememberSaveable { mutableStateOf(existing?.endTime ?: "") }
    var splitShift by rememberSaveable { mutableStateOf(existing?.splitShift ?: false) }
    var secondStartTime by rememberSaveable { mutableStateOf(existing?.secondStartTime ?: "") }
    var secondEndTime by rememberSaveable { mutableStateOf(existing?.secondEndTime ?: "") }

    // ── Sección 5: Financiero ─────────────────────────────────────────────────
    var selectedPaymentType by rememberSaveable { mutableStateOf(existing?.paymentType ?: "") }
    var selectedBank by rememberSaveable { mutableStateOf(existing?.bank ?: "") }
    var bankAccount by rememberSaveable { mutableStateOf(existing?.bankAccount ?: "") }
    var baseSalaryStr by rememberSaveable { mutableStateOf(if ((existing?.baseSalary ?: 0.0) > 0) existing!!.baseSalary.toString() else "") }
    var monthlyBenefitsStr by rememberSaveable { mutableStateOf(if ((existing?.monthlyBenefits ?: 0.0) > 0) existing!!.monthlyBenefits.toString() else "") }

    // ── Sección 6: Estado ─────────────────────────────────────────────────────
    var selectedStatus by rememberSaveable { mutableStateOf(existing?.status ?: EmployeeStatus.ACTIVE) }
    var hireDateMillis by rememberSaveable { mutableStateOf<Long?>(existing?.hireDate?.takeIf { it > 0L }) }
    var endDateMillis by rememberSaveable { mutableStateOf<Long?>(existing?.endDate?.takeIf { it > 0L }) }

    // ── Sección 7: Notas ──────────────────────────────────────────────────────
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }

    // ── Otras pestañas del alta (remember: bytes y PIN no van al estado guardado) ──
    var pendingDocs by remember { mutableStateOf(listOf<Pair<EmployeeDocument, ByteArray>>()) }
    var pendingPermissions by remember { mutableStateOf(setOf<String>()) }
    var pendingPin by remember { mutableStateOf<String?>(null) }
    var pendingPinTypedByEmployee by remember { mutableStateOf(false) }
    val formTabs = listOfNotNull("Datos", "Documentos".takeIf { canAttachDocuments }, "Acceso".takeIf { canSetupAccess })
    var selectedFormTab by rememberSaveable { mutableStateOf("Datos") }
    val currentFormTab = selectedFormTab.takeIf { it in formTabs } ?: "Datos"

    // ── Dropdown expand state ─────────────────────────────────────────────────
    var branchExpanded by remember { mutableStateOf(false) }
    var positionExpanded by remember { mutableStateOf(false) }
    var departmentExpanded by remember { mutableStateOf(false) }
    var supervisorExpanded by remember { mutableStateOf(false) }
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
    var showSecondStartPicker by remember { mutableStateOf(false) }
    var showSecondEndPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Initialize selects from existing data
    LaunchedEffect(branches, jobPositions, existing) {
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

    if (showSecondStartPicker) {
        val parts = secondStartTime.split(":").mapNotNull { it.toIntOrNull() }
        TimePickerDialog(
            initialHour = parts.getOrElse(0) { 15 },
            initialMinute = parts.getOrElse(1) { 0 },
            onDismiss = { showSecondStartPicker = false },
            onConfirm = { h, m ->
                secondStartTime = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
                showSecondStartPicker = false
            }
        )
    }
    if (showSecondEndPicker) {
        val parts = secondEndTime.split(":").mapNotNull { it.toIntOrNull() }
        TimePickerDialog(
            initialHour = parts.getOrElse(0) { 19 },
            initialMinute = parts.getOrElse(1) { 0 },
            onDismiss = { showSecondEndPicker = false },
            onConfirm = { h, m ->
                secondEndTime = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
                showSecondEndPicker = false
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

    val branch = if (branches.size == 1) branches.first() else selectedBranch
    val canSubmit = name.isNotBlank() && branch != null && !isLoading
    fun submit() {
        if (name.isBlank()) return
        val b = branch ?: return
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
                costCenter = costCenter,
                department = selectedDepartment,
                positionId = pos?.id ?: "",
                positionTitle = pos?.title ?: "",
                supervisorId = supervisorId,
                supervisorName = supervisorName,
                contractType = selectedContractType,
                workDays = workDays.sorted(),
                startTime = startTime.trim(),
                endTime = endTime.trim(),
                // Sin hora de entrada del 2º turno no hay turno mixto: se guarda apagado.
                splitShift = splitShift && secondStartTime.isNotBlank(),
                secondStartTime = if (splitShift) secondStartTime.trim() else "",
                secondEndTime = if (splitShift) secondEndTime.trim() else "",
                paymentType = selectedPaymentType,
                bank = selectedBank,
                bankAccount = bankAccount.trim(),
                baseSalary = if (canSeeSalary) baseSalaryStr.toDoubleOrNull() ?: 0.0
                             else existing?.baseSalary ?: 0.0,
                monthlyBenefits = if (canSeeSalary) monthlyBenefitsStr.toDoubleOrNull() ?: 0.0
                                  else existing?.monthlyBenefits ?: 0.0,
                status = selectedStatus,
                hireDate = hireDateMillis ?: 0L,
                endDate = endDateMillis ?: 0L,
                notes = notes.trim(),
                active = selectedStatus == EmployeeStatus.ACTIVE,
                createdAt = existing?.createdAt ?: 0L,
                // Ya no se edita (la identidad sale del PIN); se conserva el que tuviera.
                userId = existing?.userId ?: ""
            ),
            photoBytes,
            NewEmployeeExtras(
                documents = pendingDocs,
                permissions = pendingPermissions,
                pin = pendingPin,
                pinTypedByEmployee = pendingPinTypedByEmployee
            )
        )
    }

    Box(Modifier.fillMaxSize()) {
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
        containerColor = BrandColors.Ivory
    ) { padding ->
      Column(Modifier.fillMaxSize().padding(padding)) {
        if (formTabs.size > 1) {
            ScrollableTabRow(selectedTabIndex = formTabs.indexOf(currentFormTab), edgePadding = 8.dp) {
                formTabs.forEach { title ->
                    val count = if (title == "Documentos" && pendingDocs.isNotEmpty()) " (${pendingDocs.size})" else ""
                    Tab(
                        selected = currentFormTab == title,
                        onClick = { selectedFormTab = title },
                        text = { Text(title + count, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }
        when (currentFormTab) {
        "Documentos" -> NewEmployeeDocumentsTab(
            pending = pendingDocs,
            onAdd = { pendingDocs = pendingDocs + it },
            onRemove = { i -> pendingDocs = pendingDocs.filterIndexed { idx, _ -> idx != i } },
            canSubmit = canSubmit,
            isLoading = isLoading,
            onSubmit = { submit() }
        )
        "Acceso" -> NewEmployeeAccessTab(
            employeeName = name.ifBlank { "el colaborador" },
            permissions = pendingPermissions,
            onPermissionsChange = { pendingPermissions = it },
            hasPin = pendingPin != null,
            pinTypedByEmployee = pendingPinTypedByEmployee,
            onPinSet = { pin, typed -> pendingPin = pin; pendingPinTypedByEmployee = typed },
            onPinClear = { pendingPin = null },
            canSubmit = canSubmit,
            isLoading = isLoading,
            onSubmit = { submit() }
        )
        else -> AdaptiveContainer(modifier = Modifier.weight(1f)) {
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

                CostCenterPicker(
                    selected = costCenter,
                    branchLabel = branch?.name.orEmpty(),
                    splitsStore = branch?.splitsStore == true,
                    onSelect = { costCenter = it },
                    label = "¿A quién se le carga su sueldo?"
                )

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
                                    if (canSeeSalary && baseSalaryStr.isBlank() && pos.minSalary > 0) baseSalaryStr = pos.minSalary.toString()
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

                // Posibles jefes: colaboradores activos, menos la persona misma.
                val supervisorOptions = employees
                    .filter { it.id != existing?.id && it.status == EmployeeStatus.ACTIVE }
                    .sortedBy { it.name.lowercase() }
                EmpDropdownCustom(
                    label = "Jefe inmediato",
                    displayValue = supervisorName,
                    expanded = supervisorExpanded,
                    onExpandedChange = { supervisorExpanded = it }
                ) {
                    DropdownMenuItem(text = { Text("— Sin jefe —") }, onClick = {
                        supervisorId = ""
                        supervisorName = ""
                        supervisorExpanded = false
                    })
                    if (supervisorOptions.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Todavía no hay otros colaboradores", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { supervisorExpanded = false }
                        )
                    }
                    supervisorOptions.forEach { e ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(e.name, style = MaterialTheme.typography.bodyMedium)
                                    val detail = listOf(e.positionTitle, e.branchName).filter { it.isNotBlank() }.joinToString(" · ")
                                    if (detail.isNotBlank()) {
                                        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            onClick = { supervisorId = e.id; supervisorName = e.name; supervisorExpanded = false }
                        )
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
                        label = { Text(if (splitShift) "Entrada 1er turno" else "Hora entrada") },
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
                        label = { Text(if (splitShift) "Salida 1er turno" else "Hora salida") },
                        placeholder = { Text("17:00") },
                        trailingIcon = {
                            IconButton(onClick = { showEndTimePicker = true }) {
                                Icon(Icons.Default.AccessTime, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.weight(1f).clickable { showEndTimePicker = true }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Turno mixto", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "Dos entradas al día; cada una se marca en el kiosko y cuenta su propia tardanza",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = splitShift, onCheckedChange = { splitShift = it })
                }

                if (splitShift) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = secondStartTime,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Entrada 2do turno") },
                            placeholder = { Text("15:00") },
                            trailingIcon = {
                                IconButton(onClick = { showSecondStartPicker = true }) {
                                    Icon(Icons.Default.AccessTime, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                }
                            },
                            isError = secondStartTime.isBlank(),
                            supportingText = if (secondStartTime.isBlank()) {
                                { Text("Obligatoria para el turno mixto") }
                            } else null,
                            modifier = Modifier.weight(1f).clickable { showSecondStartPicker = true }
                        )
                        OutlinedTextField(
                            value = secondEndTime,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Salida 2do turno") },
                            placeholder = { Text("19:00") },
                            trailingIcon = {
                                IconButton(onClick = { showSecondEndPicker = true }) {
                                    Icon(Icons.Default.AccessTime, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                }
                            },
                            modifier = Modifier.weight(1f).clickable { showSecondEndPicker = true }
                        )
                    }
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

                if (canSeeSalary) {
                    OutlinedTextField(
                        value = baseSalaryStr,
                        onValueChange = { baseSalaryStr = it },
                        label = { Text("Salario base (Q)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        prefix = { Text("Q") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = monthlyBenefitsStr,
                        onValueChange = { monthlyBenefitsStr = it },
                        label = { Text("Prestaciones al mes (Q)") },
                        supportingText = { Text("Lo que le das aparte del salario: bonificación, aguinaldo y bono 14 repartidos al mes, alimentación. Se suma a su costo.") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        prefix = { Text("Q") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

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

                Button(
                    onClick = { submit() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    enabled = canSubmit
                ) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                    else Text("Guardar colaborador", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))
            }
        }
        }
      }
    }
    LoadingOverlay(visible = isLoading)
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

// ── Pestañas extra del alta ───────────────────────────────────────────────────

/** Lo que se capturó en las otras pestañas del alta y se aplica cuando la ficha ya tiene id. */
data class NewEmployeeExtras(
    val documents: List<Pair<EmployeeDocument, ByteArray>> = emptyList(),
    val permissions: Set<String> = emptySet(),
    val pin: String? = null,
    val pinTypedByEmployee: Boolean = false
)

/** Botón de guardar repetido en cada pestaña: si falta algo de Datos, lo dice. */
@Composable
private fun NewEmployeeSaveButton(canSubmit: Boolean, isLoading: Boolean, onSubmit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!canSubmit && !isLoading) {
            Text(
                "Para guardar, llena el nombre y la sucursal en la pestaña Datos.",
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.MutedSlate
            )
        }
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            enabled = canSubmit
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
            else Text("Guardar colaborador", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewEmployeeDocumentsTab(
    pending: List<Pair<EmployeeDocument, ByteArray>>,
    onAdd: (Pair<EmployeeDocument, ByteArray>) -> Unit,
    onRemove: (Int) -> Unit,
    canSubmit: Boolean,
    isLoading: Boolean,
    onSubmit: () -> Unit
) {
    var selectedDocType by remember { mutableStateOf(DocumentType.DPI) }
    var docTypeExpanded by remember { mutableStateOf(false) }
    val typeName = employeeDocumentTypes.find { it.first == selectedDocType }?.second ?: "documento"

    AdaptiveContainer {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Adjuntar documento", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                        HorizontalDivider(color = PrimaryBlue.copy(alpha = 0.12f))
                        Text(
                            "Se suben al guardar el colaborador.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.MutedSlate
                        )
                        ExposedDropdownMenuBox(expanded = docTypeExpanded, onExpandedChange = { docTypeExpanded = it }) {
                            OutlinedTextField(
                                value = typeName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Tipo de documento") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(docTypeExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = docTypeExpanded, onDismissRequest = { docTypeExpanded = false }) {
                                employeeDocumentTypes.forEach { (type, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { selectedDocType = type; docTypeExpanded = false })
                                }
                            }
                        }
                        DocumentPickerButton(
                            onDocumentSelected = { bytes, filename ->
                                onAdd(
                                    EmployeeDocument(
                                        type = selectedDocType,
                                        name = filename,
                                        fileType = if (filename.endsWith(".pdf", true)) "pdf" else "image",
                                        uploadedAt = Clock.System.now().toEpochMilliseconds()
                                    ) to bytes
                                )
                            }
                        ) { pickDocument ->
                            Button(
                                onClick = pickDocument,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.UploadFile, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Seleccionar $typeName")
                            }
                        }
                    }
                }
            }
            if (pending.isNotEmpty()) {
                item { Text("Por subir", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                itemsIndexed(pending) { index, (doc, _) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AttachFile, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(doc.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    employeeDocumentTypes.find { it.first == doc.type }?.second ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(onClick = { onRemove(index) }) {
                                Icon(Icons.Default.Close, "Quitar", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                NewEmployeeSaveButton(canSubmit, isLoading, onSubmit)
            }
        }
    }
}

@Composable
private fun NewEmployeeAccessTab(
    employeeName: String,
    permissions: Set<String>,
    onPermissionsChange: (Set<String>) -> Unit,
    hasPin: Boolean,
    pinTypedByEmployee: Boolean,
    onPinSet: (pin: String, typedByEmployee: Boolean) -> Unit,
    onPinClear: () -> Unit,
    canSubmit: Boolean,
    isLoading: Boolean,
    onSubmit: () -> Unit
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

    if (showPinDialog) {
        SetPinDialog(
            employeeName = employeeName,
            isSaving = false,
            error = pinError,
            onDismiss = { showPinDialog = false; pinError = null },
            onSave = { pin, confirm, typed ->
                // Misma validación que la pestaña Acceso; aquí solo se guarda hasta tener la ficha.
                pinError = pinProblem(pin) ?: if (pin != confirm) "Los dos PIN no coinciden." else null
                if (pinError == null) { onPinSet(pin, typed); showPinDialog = false }
            }
        )
    }

    AdaptiveContainer {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NewEmployeeCard("PIN") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        !hasPin -> BrandChip("Sin PIN", ChipTone.NEUTRAL)
                        pinTypedByEmployee -> BrandChip("PIN listo", ChipTone.SUCCESS)
                        else -> BrandChip("PIN temporal", ChipTone.ATTENTION)
                    }
                }
                Text(
                    "Con el PIN se identifica en los equipos de las sucursales. Se guarda junto con el colaborador.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.MutedSlate
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pinError = null; showPinDialog = true }) {
                        Icon(Icons.Default.Key, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (hasPin) "Cambiar PIN" else "Asignar PIN")
                    }
                    if (hasPin) TextButton(onClick = onPinClear) { Text("Quitar") }
                }
            }
            NewEmployeeCard("Qué puede hacer con su PIN") {
                Text(
                    "En cada equipo podrá hacer lo que tenga marcado aquí Y permita la cuenta de ese equipo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.MutedSlate
                )
                PermissionChecklist(selected = permissions, onChange = onPermissionsChange)
            }
            NewEmployeeSaveButton(canSubmit, isLoading, onSubmit)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NewEmployeeCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            HorizontalDivider(color = PrimaryBlue.copy(alpha = 0.12f))
            content()
        }
    }
}
