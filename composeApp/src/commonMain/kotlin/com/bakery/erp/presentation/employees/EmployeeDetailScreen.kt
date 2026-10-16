package com.bakery.erp.presentation.employees

import com.bakery.erp.ui.BrandTopAppBar

import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.Permission
import androidx.compose.material3.ScrollableTabRow
import com.bakery.erp.util.fmt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.AttendanceRecord
import com.bakery.erp.model.SelfieCheck
import com.bakery.erp.model.hasSplitShift
import com.bakery.erp.model.scheduleLabel
import com.bakery.erp.model.AttendanceType
import com.bakery.erp.model.DocumentType
import com.bakery.erp.model.Employee
import com.bakery.erp.model.EmployeeDocument
import com.bakery.erp.model.EmployeeHistoryEntry
import com.bakery.erp.model.EmployeeStatus
import com.bakery.erp.model.HistoryEventType
import com.bakery.erp.model.PayrollRecord
import com.bakery.erp.model.PayrollStatus
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.DocumentPickerButton
import com.bakery.erp.ui.ProductImage
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors

private val TealAccent = Color(0xFF0D7A8C)
private val LabelColor = Color(0xFF455A64)
private val ErrorRed = Color(0xFFB00020)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeDetailScreen(
    employeeId: String,
    onNavigateBack: () -> Unit,
    onNavigateToForm: (String) -> Unit,
    onNavigateToPayrollForm: (String, String?) -> Unit = { _, _ -> },
    authViewModel: AuthViewModel,
    viewModel: EmployeeViewModel,
    /** Pestaña "Créditos": lo que debe (compras fiadas + adelantos) y su estado de cuenta. */
    creditViewModel: com.bakery.erp.presentation.credit.CreditViewModel? = null,
    /** Pestaña "Acceso": PIN y permisos con los que se identifica en los equipos. */
    accessViewModel: EmployeeAccessViewModel? = null
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val creditState = creditViewModel?.uiState?.collectAsState()?.value
    LaunchedEffect(creditState?.message) {
        creditState?.message?.let { snackbarHostState.showSnackbar(it); creditViewModel?.clearMessage() }
    }

    LaunchedEffect(employeeId, companyId) {
        if (companyId.isNotEmpty()) viewModel.loadEmployeeDetail(companyId, employeeId)
    }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) { viewModel.clearDeleted(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // Por nombre y no por posición: "Créditos" y "Acceso" aparecen según lo que llegue.
    // Cada pestaña (menos Perfil) pide su propio permiso; el dueño las ve todas.
    // Nómina = cuánto gana: VIEW_EMPLOYEE_SALARY, que también esconde el salario del Perfil.
    fun can(p: Permission) = authState.user?.hasPermission(p) == true
    val canSeeSalary = can(Permission.VIEW_EMPLOYEE_SALARY)
    val tabs = listOfNotNull(
        "Perfil",
        "Asistencia".takeIf { can(Permission.VIEW_EMPLOYEE_ATTENDANCE) },
        "Nómina".takeIf { canSeeSalary },
        "Documentos".takeIf { can(Permission.VIEW_EMPLOYEE_DOCUMENTS) },
        "Historial".takeIf { can(Permission.VIEW_EMPLOYEE_HISTORY) },
        "Créditos".takeIf { creditViewModel != null && can(Permission.VIEW_EMPLOYEE_CREDIT) },
        "Acceso".takeIf { accessViewModel != null && can(Permission.VIEW_EMPLOYEE_ACCESS) }
    )
    val currentTab = tabs.getOrElse(selectedTab) { tabs.first() }

    LaunchedEffect(currentTab, companyId) {
        if (companyId.isEmpty()) return@LaunchedEffect
        when (currentTab) {
            // Asistencia carga su propio mes desde AttendanceTab; Acceso, desde EmployeeAccessTab.
            "Nómina" -> viewModel.loadPayrollHistory(companyId, employeeId)
            "Documentos" -> viewModel.loadDocuments(companyId, employeeId)
            "Historial" -> viewModel.loadHistory(companyId, employeeId)
            "Créditos" -> creditViewModel?.loadEmployeeAccount(companyId, employeeId)
        }
    }

    val employee = state.selectedEmployee

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = {
                    Text(
                        employee?.name ?: "Colaborador",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    if (employee != null) {
                        IconButton(onClick = { onNavigateToForm(employeeId) }) {
                            Icon(Icons.Default.Edit, "Editar", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BrandColors.Ivory
    ) { padding ->
        when {
            state.isDetailLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            employee == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("No se encontró el colaborador", color = MaterialTheme.colorScheme.outline) }

            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Deslizable: con seis pestañas repartidas a lo fijo los títulos no caben en teléfono.
                ScrollableTabRow(selectedTabIndex = tabs.indexOf(currentTab), edgePadding = 8.dp) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = currentTab == title,
                            onClick = { selectedTab = index },
                            text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                when (currentTab) {
                    "Perfil" -> ProfileTab(employee = employee, showSalary = canSeeSalary)
                    "Asistencia" -> AttendanceTab(
                        employee = employee,
                        companyId = companyId,
                        state = state,
                        viewModel = viewModel
                    )
                    "Nómina" -> PayrollTab(
                        employee = employee,
                        companyId = companyId,
                        state = state,
                        viewModel = viewModel,
                        onNavigateToPayrollForm = { onNavigateToPayrollForm(employeeId, it) }
                    )
                    "Documentos" -> DocumentsTab(
                        employee = employee,
                        companyId = companyId,
                        state = state,
                        viewModel = viewModel
                    )
                    "Historial" -> HistoryTab(
                        employee = employee,
                        companyId = companyId,
                        state = state,
                        viewModel = viewModel
                    )
                    "Acceso" -> if (accessViewModel != null) {
                        EmployeeAccessTab(
                            employeeId = employeeId,
                            companyId = companyId,
                            currentUser = authState.user,
                            viewModel = accessViewModel,
                            onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                        )
                    }
                    "Créditos" -> if (creditViewModel != null && creditState != null) {
                        val account = creditState.selected?.takeIf { it.employeeId == employee.id }
                        if (account == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        } else {
                            val u = authState.user
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                com.bakery.erp.presentation.credit.CreditAccountPanel(
                                    account = account,
                                    movements = creditState.movements,
                                    canManage = u?.hasPermission(Permission.MANAGE_CREDIT_ACCOUNTS) == true ||
                                        u?.hasPermission(Permission.MANAGE_EMPLOYEES) == true,
                                    isSaving = creditState.isSaving,
                                    companyId = companyId,
                                    user = u,
                                    viewModel = creditViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Tab Perfil ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileTab(employee: Employee, showSalary: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        modifier = Modifier
            .widthIn(max = 700.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (employee.photoUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                ) {
                    ProductImage(
                        imageUrl = employee.photoUrl,
                        contentDescription = employee.name,
                        modifier = Modifier.size(72.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(48.dp), tint = PrimaryBlue)
                }
            }
            Column {
                Text(employee.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (employee.positionTitle.isNotBlank()) {
                    Text(employee.positionTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (employee.department.isNotBlank()) {
                    Text(employee.department, style = MaterialTheme.typography.labelSmall, color = LabelColor)
                }
                Spacer(Modifier.height(4.dp))
                StatusChip(employee.status)
            }
        }

        HorizontalDivider()

        ProfileSection("Datos Personales") {
            if (employee.employeeCode.isNotBlank()) ProfileRow("Código", employee.employeeCode)
            if (employee.dpi.isNotBlank()) ProfileRow("DPI", employee.dpi)
            if (employee.nit.isNotBlank()) ProfileRow("NIT", employee.nit)
            if (employee.birthDate > 0) ProfileRow("Nacimiento", formatEmployeeDate(employee.birthDate))
            if (employee.gender.isNotBlank()) ProfileRow("Género", employee.gender)
            if (employee.maritalStatus.isNotBlank()) ProfileRow("Estado civil", employee.maritalStatus)
        }

        ProfileSection("Contacto") {
            if (employee.address.isNotBlank()) ProfileRow("Dirección", employee.address)
            if (employee.phone.isNotBlank()) ProfileRow("Teléfono", employee.phone)
            if (employee.phoneAux.isNotBlank()) ProfileRow("Tel. auxiliar", employee.phoneAux)
            if (employee.email.isNotBlank()) ProfileRow("Correo", employee.email)
            if (employee.emergencyContact.isNotBlank()) ProfileRow("Emergencia", "${employee.emergencyContact} — ${employee.emergencyPhone}")
        }

        ProfileSection("Empleo") {
            if (employee.branchName.isNotBlank()) ProfileRow("Sucursal", employee.branchName)
            if (employee.supervisorName.isNotBlank()) ProfileRow("Jefe inmediato", employee.supervisorName)
            if (employee.contractType.isNotBlank()) ProfileRow("Tipo contrato", employee.contractType)
            if (employee.hireDate > 0) ProfileRow("Fecha ingreso", formatEmployeeDate(employee.hireDate))
            if (employee.endDate > 0 && employee.status != EmployeeStatus.ACTIVE) ProfileRow("Fecha baja", formatEmployeeDate(employee.endDate))
        }

        if (employee.workDays.isNotEmpty() || employee.startTime.isNotBlank()) {
            ProfileSection("Horario") {
                if (employee.workDays.isNotEmpty()) {
                    val dayNames = mapOf(1 to "Lun", 2 to "Mar", 3 to "Mié", 4 to "Jue", 5 to "Vie", 6 to "Sáb", 7 to "Dom")
                    ProfileRow("Días", employee.workDays.mapNotNull { dayNames[it] }.joinToString(", "))
                }
                val schedule = employee.scheduleLabel()
                if (schedule.isNotBlank()) {
                    ProfileRow(if (employee.hasSplitShift) "Turno mixto" else "Horario", schedule)
                }
            }
        }

        ProfileSection("Financiero") {
            if (employee.paymentType.isNotBlank()) ProfileRow("Tipo de pago", employee.paymentType)
            if (employee.bank.isNotBlank()) ProfileRow("Banco", employee.bank)
            if (employee.bankAccount.isNotBlank()) ProfileRow("Cuenta", employee.bankAccount)
            if (showSalary && employee.baseSalary > 0) ProfileRow("Salario base", "Q${employee.baseSalary.fmt(2)}")
            if (showSalary && employee.monthlyBenefits > 0) ProfileRow("Prestaciones al mes", "Q${employee.monthlyBenefits.fmt(2)}")
        }

        if (employee.notes.isNotBlank()) {
            ProfileSection("Notas") {
                Text(employee.notes, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable () -> Unit) {
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

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = LabelColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun StatusChip(status: EmployeeStatus) {
    val (bg, fg) = when (status) {
        EmployeeStatus.ACTIVE -> Color(0xFFDCF1DC) to Color(0xFF1B5E20)
        EmployeeStatus.SUSPENDED -> Color(0xFFFFF9C4) to Color(0xFFF57F17)
        EmployeeStatus.ON_VACATION -> BrandColors.PrimaryContainer to PrimaryBlue
        EmployeeStatus.FIRED -> Color(0xFFFFEBEE) to Color(0xFFB00020)
        EmployeeStatus.RESIGNED -> Color(0xFFF3E5F5) to Color(0xFF6A1B9A)
    }
    val label = when (status) {
        EmployeeStatus.ACTIVE -> "Activo"
        EmployeeStatus.SUSPENDED -> "Suspendido"
        EmployeeStatus.ON_VACATION -> "Vacaciones"
        EmployeeStatus.FIRED -> "Despedido"
        EmployeeStatus.RESIGNED -> "Renunció"
    }
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

// ── Tab Asistencia ────────────────────────────────────────────────────────────

@Composable
private fun AttendanceTab(
    employee: Employee,
    companyId: String,
    state: EmployeeUiState,
    viewModel: EmployeeViewModel
) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val isTablet = with(density) { windowInfo.containerSize.width.toDp() >= 600.dp }

    val nowDT = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
    var displayMonth by rememberSaveable { mutableIntStateOf(nowDT.monthNumber) }
    var displayYear by rememberSaveable { mutableIntStateOf(nowDT.year) }

    LaunchedEffect(displayMonth, displayYear, companyId) {
        if (companyId.isNotEmpty()) {
            viewModel.loadAttendance(companyId, employee.id, displayMonth, displayYear)
        }
    }

    val isCurrentMonth = run {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        displayMonth == now.monthNumber && displayYear == now.year
    }

    fun prevMonth() {
        if (displayMonth == 1) { displayMonth = 12; displayYear-- } else displayMonth--
    }
    fun nextMonth() {
        if (!isCurrentMonth) {
            if (displayMonth == 12) { displayMonth = 1; displayYear++ } else displayMonth++
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = ::prevMonth) {
                Icon(Icons.Default.KeyboardArrowLeft, "Mes anterior", tint = PrimaryBlue)
            }
            Text(
                "${attendanceMonthName(displayMonth)} $displayYear",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryBlue
            )
            IconButton(onClick = ::nextMonth, enabled = !isCurrentMonth) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    "Mes siguiente",
                    tint = if (isCurrentMonth) MaterialTheme.colorScheme.outline else PrimaryBlue
                )
            }
        }

        HorizontalDivider()

        when {
            state.isAttendanceLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { AttendanceKpiRow(records = state.attendanceMonth) }

                if (state.attendanceToday != null && isCurrentMonth) {
                    item { TodayAttendanceCard(state.attendanceToday!!) }
                }

                if (state.attendanceMonth.isNotEmpty()) {
                    item {
                        Text(
                            "Registros del mes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (isTablet) {
                        item { AttendanceTableHeader() }
                        items(state.attendanceMonth, key = { it.id }) { record ->
                            AttendanceTableRow(record)
                        }
                    } else {
                        items(state.attendanceMonth, key = { it.id }) { record ->
                            AttendanceRecordCard(record)
                        }
                    }
                } else {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(8.dp))
                                Text("Sin registros este mes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Usa «Ingreso a Turno» para marcar asistencia",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun AttendanceTableHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Fecha", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryBlue, modifier = Modifier.weight(2f))
            Text("Entrada", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryBlue, modifier = Modifier.weight(1.5f))
            Text("Salida", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryBlue, modifier = Modifier.weight(1.5f))
            Text("Horas", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryBlue, modifier = Modifier.weight(1f))
            Text("Estado", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryBlue, modifier = Modifier.weight(2f))
        }
    }
}

@Composable
private fun AttendanceTableRow(record: AttendanceRecord) {
    val isLate = record.lateMinutes > 0
    val typeColor = attendanceTypeColor(record.type)
    val typeLabel = attendanceTypeLabel(record.type)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLate) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(2f)) {
                Text(formatEmployeeDate(record.date), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                if (record.shiftNumber == 2) {
                    Text("2do turno", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                }
                if (isLate) {
                    Text(formatLateTime(record.lateMinutes), style = MaterialTheme.typography.labelSmall, color = ErrorRed, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(
                modifier = Modifier.weight(1.5f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (record.clockIn > 0) formatTime(record.clockIn) else "—", style = MaterialTheme.typography.bodySmall)
                AttendanceSelfieThumb("entrada", record.clockIn, record.clockInSelfieCheck, record.clockInPhotoUrl)
            }
            Row(
                modifier = Modifier.weight(1.5f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (record.clockOut > 0) formatTime(record.clockOut) else "—", style = MaterialTheme.typography.bodySmall)
                AttendanceSelfieThumb("salida", record.clockOut, record.clockOutSelfieCheck, record.clockOutPhotoUrl)
            }
            Text(
                if (record.hoursWorked > 0) "${record.hoursWorked.fmt(1)}h" else "—",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Surface(shape = RoundedCornerShape(50), color = typeColor.copy(alpha = 0.12f), modifier = Modifier.weight(2f)) {
                Text(
                    typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = typeColor,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun AttendanceKpiRow(records: List<AttendanceRecord>) {
    // Días, no marcas: un turno mixto deja dos marcas el mismo día.
    val present = records.filter { it.type == AttendanceType.PRESENT }.map { it.date }.distinct().size
    val absent = records.count { it.type == AttendanceType.ABSENT }
    val lateCount = records.count { it.lateMinutes > 0 }
    val totalHours = records.sumOf { it.hoursWorked }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AttendanceKpi("Presentes", present.toString(), Color(0xFF1B5E20), Color(0xFFDCF1DC), Modifier.weight(1f))
        AttendanceKpi("Ausencias", absent.toString(), Color(0xFFB00020), Color(0xFFFFEBEE), Modifier.weight(1f))
        AttendanceKpi("Tardanzas", lateCount.toString(), Color(0xFFF57F17), Color(0xFFFFF9C4), Modifier.weight(1f))
        AttendanceKpi("Horas", totalHours.fmt(0), TealAccent, Color(0xFFD4EFF3), Modifier.weight(1f))
    }
}

@Composable
private fun AttendanceKpi(label: String, value: String, fg: Color, bg: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg)
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun TodayAttendanceCard(record: AttendanceRecord) {
    val isClosed = record.clockOut > 0
    val cardColor = if (isClosed) Color(0xFFDCF1DC) else BrandColors.PrimaryContainer
    val accentColor = if (isClosed) Color(0xFF1B5E20) else PrimaryBlue

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isClosed) Icons.Default.CheckCircle else Icons.Default.Schedule,
                null,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (isClosed) "Hoy — Turno completado" else "Hoy — Turno en curso",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold
                )
                if (record.clockIn > 0) {
                    val entryText = "Entrada: ${formatTime(record.clockIn)}"
                    val exitText = if (isClosed) "   •   Salida: ${formatTime(record.clockOut)}" else ""
                    Text(entryText + exitText, style = MaterialTheme.typography.bodySmall)
                }
                if (isClosed && record.hoursWorked > 0) {
                    Text("${record.hoursWorked.fmt(1)} horas trabajadas", style = MaterialTheme.typography.labelSmall, color = accentColor)
                }
                if (record.lateMinutes > 0) {
                    Text(formatLateTime(record.lateMinutes), style = MaterialTheme.typography.labelSmall, color = ErrorRed, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AttendanceRecordCard(record: AttendanceRecord) {
    val isLate = record.lateMinutes > 0
    val typeColor = attendanceTypeColor(record.type)
    val typeLabel = attendanceTypeLabel(record.type)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLate) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    formatEmployeeDate(record.date) + if (record.shiftNumber == 2) " · 2do turno" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                if (record.clockIn > 0) {
                    Text(
                        "${formatTime(record.clockIn)}${if (record.clockOut > 0) " – ${formatTime(record.clockOut)}" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isLate) {
                    Text(formatLateTime(record.lateMinutes), style = MaterialTheme.typography.labelSmall, color = ErrorRed, fontWeight = FontWeight.SemiBold)
                }
                if (record.clockInSelfieCheck != SelfieCheck.NONE || record.clockOutSelfieCheck != SelfieCheck.NONE) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AttendanceSelfieThumb("entrada", record.clockIn, record.clockInSelfieCheck, record.clockInPhotoUrl)
                        AttendanceSelfieThumb("salida", record.clockOut, record.clockOutSelfieCheck, record.clockOutPhotoUrl)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(shape = RoundedCornerShape(50), color = typeColor.copy(alpha = 0.12f)) {
                    Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = typeColor, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                if (record.hoursWorked > 0) {
                    Text("${record.hoursWorked.fmt(1)}h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

private fun attendanceTypeColor(type: AttendanceType) = when (type) {
    AttendanceType.PRESENT -> Color(0xFF1B5E20)
    AttendanceType.ABSENT -> Color(0xFFB00020)
    AttendanceType.HALF_DAY -> Color(0xFFF57F17)
    AttendanceType.VACATION -> PrimaryBlue
    AttendanceType.SICK_LEAVE -> Color(0xFF6A1B9A)
    AttendanceType.HOLIDAY -> TealAccent
}

private fun attendanceTypeLabel(type: AttendanceType) = when (type) {
    AttendanceType.PRESENT -> "Presente"
    AttendanceType.ABSENT -> "Ausente"
    AttendanceType.HALF_DAY -> "Medio día"
    AttendanceType.VACATION -> "Vacación"
    AttendanceType.SICK_LEAVE -> "Enfermedad"
    AttendanceType.HOLIDAY -> "Feriado"
}

// ── Tab Nómina ────────────────────────────────────────────────────────────────

@Composable
private fun PayrollTab(
    employee: Employee,
    companyId: String,
    state: EmployeeUiState,
    viewModel: EmployeeViewModel,
    onNavigateToPayrollForm: (String?) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        when {
            state.isPayrollLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (employee.hireDate > 0) {
                    val oneYearMs = 365L * 24 * 3600 * 1000
                    val eligible = Clock.System.now().toEpochMilliseconds() - employee.hireDate >= oneYearMs
                    if (eligible) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCF1DC)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF1B5E20), modifier = Modifier.size(20.dp))
                                    Text(
                                        "Vacaciones disponibles desde ${formatEmployeeDate(employee.hireDate + oneYearMs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF1B5E20)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { onNavigateToPayrollForm(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Nuevo período de nómina")
                    }
                }

                if (state.payrollHistory.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.MonetizationOn, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(8.dp))
                                Text("Sin registros de nómina", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                } else {
                    items(state.payrollHistory, key = { it.id }) { record ->
                        PayrollRecordCard(
                            record = record,
                            onClick = { onNavigateToPayrollForm(record.id) },
                            onDelete = { viewModel.deletePayroll(companyId, employee.id, record.id) }
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun PayrollRecordCard(
    record: PayrollRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar período") },
            text = { Text("¿Eliminar este registro de nómina?") },
            confirmButton = { TextButton(onClick = { onDelete(); showDeleteDialog = false }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    val statusColor = when (record.status) {
        PayrollStatus.DRAFT -> Color(0xFFF57F17)
        PayrollStatus.APPROVED -> PrimaryBlue
        PayrollStatus.PAID -> Color(0xFF1B5E20)
    }
    val statusLabel = when (record.status) {
        PayrollStatus.DRAFT -> "Borrador"
        PayrollStatus.APPROVED -> "Aprobado"
        PayrollStatus.PAID -> "Pagado"
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${formatEmployeeDate(record.periodStart)} – ${formatEmployeeDate(record.periodEnd)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Neto: Q${record.totalNet.fmt(2)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (record.paymentDate > 0) {
                    Text("Pagado: ${formatEmployeeDate(record.paymentDate)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = RoundedCornerShape(50), color = statusColor.copy(alpha = 0.12f)) {
                    Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ── Tab Documentos ────────────────────────────────────────────────────────────

/** Tipos que se pueden subir, con su nombre en pantalla. También los usa el alta (EmployeeFormScreen). */
internal val employeeDocumentTypes = listOf(
    DocumentType.CONTRACT to "Contrato",
    DocumentType.DPI to "DPI",
    DocumentType.CV to "Currículum (CV)",
    DocumentType.HEALTH_CARD to "Tarjeta de salud",
    DocumentType.CERTIFICATION to "Tarjeta manip. alimentos",
    DocumentType.BACKGROUND_CHECK to "Antecedentes penales",
    DocumentType.POLICE_RECORD to "Antecedentes policiacos",
    DocumentType.SIGNATURE to "Firma",
    DocumentType.OTHER to "Otro documento"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentsTab(
    employee: Employee,
    companyId: String,
    state: EmployeeUiState,
    viewModel: EmployeeViewModel
) {
    var selectedDocType by remember { mutableStateOf(DocumentType.OTHER) }
    var docTypeExpanded by remember { mutableStateOf(false) }

    val documentTypes = employeeDocumentTypes

    Column(modifier = Modifier.fillMaxSize()) {
        when {
            state.isDocumentsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Upload section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Subir documento",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlue
                            )
                            HorizontalDivider(color = PrimaryBlue.copy(alpha = 0.12f))

                            ExposedDropdownMenuBox(
                                expanded = docTypeExpanded,
                                onExpandedChange = { docTypeExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = documentTypes.find { it.first == selectedDocType }?.second ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Tipo de documento") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(docTypeExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = docTypeExpanded,
                                    onDismissRequest = { docTypeExpanded = false }
                                ) {
                                    documentTypes.forEach { (type, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = { selectedDocType = type; docTypeExpanded = false }
                                        )
                                    }
                                }
                            }

                            val typeName = documentTypes.find { it.first == selectedDocType }?.second ?: "documento"
                            DocumentPickerButton(
                                onDocumentSelected = { bytes, filename ->
                                    val doc = EmployeeDocument(
                                        employeeId = employee.id,
                                        type = selectedDocType,
                                        name = filename,
                                        fileType = if (filename.endsWith(".pdf", true)) "pdf" else "image",
                                        uploadedAt = Clock.System.now().toEpochMilliseconds()
                                    )
                                    viewModel.uploadDocument(companyId, employee.id, doc, bytes)
                                }
                            ) { pickDocument ->
                                Button(
                                    onClick = pickDocument,
                                    enabled = !state.isUploadingDocument,
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (state.isUploadingDocument) {
                                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Subiendo...")
                                    } else {
                                        Icon(Icons.Default.UploadFile, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Seleccionar $typeName")
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.documents.isNotEmpty()) {
                    item {
                        Text(
                            "Documentos guardados",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(state.documents, key = { it.id }) { doc ->
                        DocumentCard(
                            doc = doc,
                            onDelete = { viewModel.deleteDocument(companyId, employee.id, doc.id) }
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun DocumentCard(doc: EmployeeDocument, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar documento") },
            text = { Text("¿Eliminar \"${doc.name}\"?") },
            confirmButton = { TextButton(onClick = { onDelete(); showDeleteDialog = false }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    val typeLabel = when (doc.type) {
        DocumentType.CONTRACT -> "Contrato"
        DocumentType.DPI -> "DPI"
        DocumentType.BACKGROUND_CHECK -> "Ant. penales"
        DocumentType.POLICE_RECORD -> "Ant. policiacos"
        DocumentType.CV -> "CV"
        DocumentType.CERTIFICATION -> "Certif. manip. alimentos"
        DocumentType.HEALTH_CARD -> "Carnet salud"
        DocumentType.SIGNATURE -> "Firma"
        DocumentType.OTHER -> "Otro"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(BrandColors.PrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AttachFile, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(doc.name.ifBlank { typeLabel }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    if (doc.uploadedAt > 0) {
                        Text(formatEmployeeDate(doc.uploadedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            Row {
                if (doc.fileUrl.isNotBlank()) {
                    IconButton(onClick = { uriHandler.openUri(doc.fileUrl) }) {
                        Icon(Icons.Default.OpenInNew, "Ver documento", tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ── Tab Historial ─────────────────────────────────────────────────────────────

private val historyEventLabels = mapOf(
    HistoryEventType.PAYMENT to "Pago de salario",
    HistoryEventType.ADVANCE to "Anticipo",
    HistoryEventType.INFRACTION to "Falta",
    HistoryEventType.WARNING to "Llamada de atención",
    HistoryEventType.SUSPENSION to "Suspensión",
    HistoryEventType.REINSTATEMENT to "Reincorporación",
    HistoryEventType.LEAVE_PERMIT to "Permiso",
    HistoryEventType.POSITION_CHANGE to "Cambio de puesto",
    HistoryEventType.NOTE to "Nota general"
)

private fun historyEventColor(type: HistoryEventType) = when (type) {
    HistoryEventType.PAYMENT -> Color(0xFF1B5E20)
    HistoryEventType.ADVANCE -> Color(0xFF2E7D32)
    HistoryEventType.INFRACTION -> Color(0xFFB00020)
    HistoryEventType.WARNING -> Color(0xFFF57F17)
    HistoryEventType.SUSPENSION -> Color(0xFF6A1B9A)
    HistoryEventType.REINSTATEMENT -> Color(0xFF0D47A1)
    HistoryEventType.LEAVE_PERMIT -> Color(0xFF00695C)
    HistoryEventType.POSITION_CHANGE -> BrandColors.Primary
    HistoryEventType.NOTE -> Color(0xFF455A64)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTab(
    employee: Employee,
    companyId: String,
    state: EmployeeUiState,
    viewModel: EmployeeViewModel
) {
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddHistoryEntryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { entry ->
                viewModel.addHistoryEntry(companyId, employee.id, entry)
                showAddDialog = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when {
            state.isHistoryLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar evento")
                    }
                }

                if (state.historyEntries.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(8.dp))
                                Text("Sin eventos registrados", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Registra pagos, faltas, permisos y otros sucesos del colaborador",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(state.historyEntries, key = { it.id }) { entry ->
                        HistoryEntryCard(
                            entry = entry,
                            onDelete = { viewModel.deleteHistoryEntry(companyId, employee.id, entry.id) }
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(entry: EmployeeHistoryEntry, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar evento") },
            text = { Text("¿Eliminar este registro del historial?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    val color = historyEventColor(entry.type)
    val label = historyEventLabels[entry.type] ?: "Evento"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (entry.type) {
                    HistoryEventType.PAYMENT, HistoryEventType.ADVANCE -> Icons.Default.MonetizationOn
                    HistoryEventType.INFRACTION, HistoryEventType.WARNING -> Icons.Default.Warning
                    HistoryEventType.SUSPENSION -> Icons.Default.Logout
                    HistoryEventType.REINSTATEMENT -> Icons.Default.Login
                    HistoryEventType.LEAVE_PERMIT -> Icons.Default.Schedule
                    else -> Icons.Default.CalendarToday
                }
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    if (entry.date > 0) {
                        Text(
                            formatEmployeeDate(entry.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                if (entry.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(entry.description, style = MaterialTheme.typography.bodySmall)
                }
                if (entry.amount != null && entry.amount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Q${entry.amount.fmt(2)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                }
            }

            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddHistoryEntryDialog(
    onDismiss: () -> Unit,
    onConfirm: (EmployeeHistoryEntry) -> Unit
) {
    var selectedType by remember { mutableStateOf(HistoryEventType.NOTE) }
    var typeExpanded by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableStateOf<Long?>(Clock.System.now().toEpochMilliseconds()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val showAmount = selectedType == HistoryEventType.PAYMENT || selectedType == HistoryEventType.ADVANCE

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { selectedDateMillis = dpState.selectedDateMillis; showDatePicker = false }) {
                    Text("Aceptar")
                }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(dpState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar evento al historial", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = historyEventLabels[selectedType] ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de evento") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        // Los adelantos ya no se anotan acá: van en la pestaña Créditos, que lleva
                        // el saldo y se descuenta sola en la planilla. Los viejos se siguen viendo.
                        HistoryEventType.entries.filter { it != HistoryEventType.ADVANCE }.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(historyEventLabels[type] ?: type.name) },
                                onClick = { selectedType = type; typeExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = selectedDateMillis?.let { formatEmployeeDate(it) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fecha") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )

                if (showAmount) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Monto (Q)") },
                        prefix = { Text("Q") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        EmployeeHistoryEntry(
                            type = selectedType,
                            description = description.trim(),
                            amount = if (showAmount) amountStr.toDoubleOrNull() else null,
                            date = selectedDateMillis ?: Clock.System.now().toEpochMilliseconds()
                        )
                    )
                },
                enabled = description.isNotBlank() || showAmount
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ── Utilidades ────────────────────────────────────────────────────────────────

private fun formatTime(millis: Long): String {
    if (millis == 0L) return ""
    val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${ldt.hour.toString().padStart(2, '0')}:${ldt.minute.toString().padStart(2, '0')}"
}

private fun formatLateTime(minutes: Int): String = when {
    minutes <= 0 -> ""
    minutes < 60 -> "$minutes min tarde"
    else -> {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0) "${h}h tarde" else "${h}h ${m}min tarde"
    }
}

private fun attendanceMonthName(month: Int): String = when (month) {
    1 -> "Enero"; 2 -> "Febrero"; 3 -> "Marzo"; 4 -> "Abril"
    5 -> "Mayo"; 6 -> "Junio"; 7 -> "Julio"; 8 -> "Agosto"
    9 -> "Septiembre"; 10 -> "Octubre"; 11 -> "Noviembre"; 12 -> "Diciembre"
    else -> month.toString()
}
