package com.bakery.erp.presentation.employees

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakery.erp.model.AttendanceRecord
import com.bakery.erp.model.Employee
import com.bakery.erp.model.EmployeeStatus
import com.bakery.erp.presentation.auth.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.bakery.erp.ui.theme.BrandColors.Primary as KioskBlue
import com.bakery.erp.ui.theme.BrandColors

private val KioskGreen = Color(0xFF1B5E20)
private val KioskRed = Color(0xFFB00020)

private enum class KioskStep { LIST, CONFIRM, SUCCESS }

private data class SuccessInfo(
    val employeeName: String,
    val wasClockIn: Boolean,
    val time: String,
    val lateMinutes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftClockInScreen(
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: EmployeeViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    var kioskStep by remember { mutableStateOf(KioskStep.LIST) }
    var successInfo by remember { mutableStateOf<SuccessInfo?>(null) }
    var pendingAction by remember { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && state.employees.isEmpty()) {
            viewModel.loadEmployees(companyId)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    LaunchedEffect(state.shiftClockInSuccess) {
        if (state.shiftClockInSuccess) {
            val record = state.shiftTodayRecord
            val employee = state.shiftClockInEmployee
            if (record != null && employee != null) {
                val isClockIn = pendingAction == "clockIn"
                successInfo = SuccessInfo(
                    employeeName = employee.name,
                    wasClockIn = isClockIn,
                    time = kioskFormatTime(if (isClockIn) record.clockIn else record.clockOut),
                    lateMinutes = record.lateMinutes
                )
                kioskStep = KioskStep.SUCCESS
            }
            viewModel.clearShiftSuccess()
        }
    }

    LaunchedEffect(kioskStep) {
        if (kioskStep == KioskStep.SUCCESS) {
            delay(3_500)
            kioskStep = KioskStep.LIST
            viewModel.clearShiftEmployee()
            successInfo = null
        }
    }

    val filtered = state.employees
        .filter { it.status == EmployeeStatus.ACTIVE || it.status == EmployeeStatus.ON_VACATION }
        .filter {
            searchQuery.isBlank() ||
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.positionTitle.contains(searchQuery, ignoreCase = true)
        }

    Scaffold(
        topBar = {
            if (kioskStep == KioskStep.LIST) {
                TopAppBar(
                    title = { Text("Asistencia", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = KioskBlue,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BrandColors.Ivory
    ) { padding ->
        when (kioskStep) {
            KioskStep.LIST -> KioskListContent(
                modifier = Modifier.fillMaxSize().padding(padding),
                isLoading = state.isLoading,
                employees = filtered,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                onEmployeeClick = { employee ->
                    viewModel.selectShiftEmployee(companyId, employee)
                    kioskStep = KioskStep.CONFIRM
                }
            )

            KioskStep.CONFIRM -> KioskConfirmContent(
                modifier = Modifier.fillMaxSize(),
                employee = state.shiftClockInEmployee,
                todayRecord = state.shiftTodayRecord,
                isLoading = state.isShiftClockInLoading,
                onClockIn = {
                    pendingAction = "clockIn"
                    viewModel.clockInShiftEmployee(companyId)
                },
                onClockOut = {
                    pendingAction = "clockOut"
                    viewModel.clockOutShiftEmployee(companyId)
                },
                onCancel = {
                    kioskStep = KioskStep.LIST
                    viewModel.clearShiftEmployee()
                }
            )

            KioskStep.SUCCESS -> KioskSuccessContent(
                modifier = Modifier.fillMaxSize(),
                info = successInfo
            )
        }
    }
}

// ── Lista de colaboradores ────────────────────────────────────────────────────

@Composable
private fun KioskListContent(
    modifier: Modifier,
    isLoading: Boolean,
    employees: List<Employee>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onEmployeeClick: (Employee) -> Unit
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Buscar colaborador...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            employees.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Badge, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (searchQuery.isNotBlank()) "No se encontró \"$searchQuery\"" else "Sin colaboradores activos",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(employees, key = { it.id }) { employee ->
                    KioskEmployeeCard(employee = employee, onClick = { onEmployeeClick(employee) })
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun KioskEmployeeCard(employee: Employee, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(KioskBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    employee.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = KioskBlue
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(employee.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (employee.positionTitle.isNotBlank()) {
                    Text(employee.positionTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (employee.startTime.isNotBlank() && employee.endTime.isNotBlank()) {
                    Text("${employee.startTime} – ${employee.endTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Icon(Icons.Default.Login, null, tint = KioskBlue, modifier = Modifier.size(22.dp))
        }
    }
}

// ── Confirmación ──────────────────────────────────────────────────────────────

@Composable
private fun KioskConfirmContent(
    modifier: Modifier,
    employee: Employee?,
    todayRecord: AttendanceRecord?,
    isLoading: Boolean,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onCancel: () -> Unit
) {
    if (employee == null) return

    val isClockedIn = todayRecord != null && todayRecord.clockIn > 0 && todayRecord.clockOut == 0L
    val hasClockedOut = todayRecord != null && todayRecord.clockOut > 0

    Box(
        modifier = modifier.background(BrandColors.Ivory),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(KioskBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    employee.name.take(1).uppercase(),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = KioskBlue
                )
            }

            Text(
                employee.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (employee.positionTitle.isNotBlank()) {
                Text(
                    employee.positionTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            if (employee.startTime.isNotBlank() && employee.endTime.isNotBlank()) {
                Surface(shape = RoundedCornerShape(50), color = KioskBlue.copy(alpha = 0.08f)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, null, tint = KioskBlue, modifier = Modifier.size(14.dp))
                        Text("Turno: ${employee.startTime} – ${employee.endTime}", style = MaterialTheme.typography.labelMedium, color = KioskBlue)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            when {
                hasClockedOut -> Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDCF1DC)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = KioskGreen, modifier = Modifier.size(18.dp))
                        Text(
                            "Turno completado — Entrada: ${kioskFormatTime(todayRecord!!.clockIn)} · Salida: ${kioskFormatTime(todayRecord.clockOut)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = KioskGreen
                        )
                    }
                }

                isClockedIn -> Card(
                    colors = CardDefaults.cardColors(containerColor = BrandColors.PrimaryContainer),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Login, null, tint = KioskBlue, modifier = Modifier.size(18.dp))
                        Text(
                            "Entrada marcada: ${kioskFormatTime(todayRecord!!.clockIn)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = KioskBlue
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            if (isLoading) {
                CircularProgressIndicator(color = KioskBlue, modifier = Modifier.size(36.dp))
            } else {
                if (!isClockedIn && !hasClockedOut) {
                    Button(
                        onClick = onClockIn,
                        colors = ButtonDefaults.buttonColors(containerColor = KioskGreen),
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Login, null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("MARCAR ENTRADA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                if (isClockedIn) {
                    Button(
                        onClick = onClockOut,
                        colors = ButtonDefaults.buttonColors(containerColor = KioskRed),
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Logout, null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("MARCAR SALIDA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            TextButton(onClick = onCancel, enabled = !isLoading) {
                Text("Cancelar", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ── Pantalla de éxito ─────────────────────────────────────────────────────────

@Composable
private fun KioskSuccessContent(
    modifier: Modifier,
    info: SuccessInfo?
) {
    if (info == null) return

    var countdown by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        repeat(3) {
            delay(1_000)
            countdown--
        }
    }

    val bgColor = if (info.wasClockIn) Color(0xFFF0FFF4) else Color(0xFFFFF0F0)
    val accentColor = if (info.wasClockIn) KioskGreen else KioskRed

    Box(
        modifier = modifier.background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (info.wasClockIn) Icons.Default.CheckCircle else Icons.Default.Logout,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            Text(
                if (info.wasClockIn) "Entrada registrada" else "Salida registrada",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                textAlign = TextAlign.Center
            )

            Text(
                info.employeeName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Text(
                info.time,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (info.wasClockIn && info.lateMinutes > 0) {
                Surface(shape = RoundedCornerShape(50), color = KioskRed.copy(alpha = 0.12f)) {
                    Text(
                        kioskFormatLateTime(info.lateMinutes),
                        style = MaterialTheme.typography.titleSmall,
                        color = KioskRed,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            } else if (info.wasClockIn) {
                Surface(shape = RoundedCornerShape(50), color = KioskGreen.copy(alpha = 0.12f)) {
                    Text(
                        "A tiempo",
                        style = MaterialTheme.typography.titleSmall,
                        color = KioskGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Volviendo en $countdown...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

// ── Utilidades ────────────────────────────────────────────────────────────────

private fun kioskFormatTime(millis: Long): String {
    if (millis == 0L) return "--:--"
    val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${ldt.hour.toString().padStart(2, '0')}:${ldt.minute.toString().padStart(2, '0')}"
}

private fun kioskFormatLateTime(minutes: Int): String = when {
    minutes <= 0 -> ""
    minutes < 60 -> "$minutes minutos tarde"
    else -> {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0) "$h hora${if (h > 1) "s" else ""} tarde" else "$h h $m min tarde"
    }
}
