package com.bakery.erp.presentation.employees

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Login
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
import androidx.compose.material3.OutlinedButton
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
import com.bakery.erp.model.Employee
import com.bakery.erp.model.EmployeeStatus
import com.bakery.erp.model.EntryAvailability
import com.bakery.erp.model.SelfieCheck
import com.bakery.erp.model.hasSplitShift
import com.bakery.erp.model.scheduleLabel
import com.bakery.erp.ui.SelfieCamera
import com.bakery.erp.ui.SelfieCameraStatus
import com.bakery.erp.ui.isSelfieCameraAvailable
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.presentation.auth.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.bakery.erp.ui.theme.BrandColors.Primary as KioskBlue
import com.bakery.erp.ui.theme.BrandColors

private val KioskGreen = Color(0xFF1B5E20)
private val KioskRed = Color(0xFFB00020)

private enum class KioskStep { LIST, CONFIRM, SELFIE, SUCCESS }

private data class SuccessInfo(
    val employeeName: String,
    val time: String,
    val lateMinutes: Int,
    val withPhoto: Boolean,
    /** "1er turno"/"2do turno" en horario mixto; vacío en turno simple. */
    val shiftLabel: String
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
    var pendingCheck by remember { mutableStateOf(SelfieCheck.NONE) }

    // El kiosko solo marca la ENTRADA: lo que se controla es la hora de llegada.
    fun submit(selfie: ByteArray?, check: SelfieCheck) {
        pendingCheck = check
        viewModel.clockInShiftEmployee(companyId, selfie, check)
    }

    // Sin cámara en este equipo (web) se marca directo, y la marca queda "sin foto".
    fun startMark() {
        if (isSelfieCameraAvailable) kioskStep = KioskStep.SELFIE
        else submit(null, SelfieCheck.SKIPPED)
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && state.employees.isEmpty()) {
            viewModel.loadEmployees(companyId)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            // Falló el registro después de la foto: vuelve a la ficha para reintentar.
            if (kioskStep == KioskStep.SELFIE) kioskStep = KioskStep.CONFIRM
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.shiftClockInSuccess) {
        if (state.shiftClockInSuccess) {
            val record = state.shiftTodayRecord
            val employee = state.shiftClockInEmployee
            if (record != null && employee != null) {
                successInfo = SuccessInfo(
                    employeeName = employee.name,
                    time = kioskFormatTime(record.clockIn),
                    lateMinutes = record.lateMinutes,
                    withPhoto = pendingCheck != SelfieCheck.SKIPPED,
                    shiftLabel = if (employee.hasSplitShift) shiftName(record.shiftNumber) else ""
                )
                kioskStep = KioskStep.SUCCESS
            }
            viewModel.clearShiftSuccess()
        }
    }

    LaunchedEffect(kioskStep) {
        if (kioskStep == KioskStep.SUCCESS) {
            // Con tardanza se queda más tiempo: el aviso es para leerlo.
            delay(successScreenMillis(successInfo?.lateMinutes ?: 0))
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
                BrandTopAppBar(
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
                entry = state.shiftEntry,
                isLoading = state.isShiftClockInLoading,
                onClockIn = { startMark() },
                onCancel = {
                    kioskStep = KioskStep.LIST
                    viewModel.clearShiftEmployee()
                }
            )

            KioskStep.SELFIE -> KioskSelfieContent(
                modifier = Modifier.fillMaxSize(),
                employeeName = state.shiftClockInEmployee?.name ?: "",
                isSaving = state.isShiftClockInLoading,
                onCaptured = { jpeg, check -> submit(jpeg, check) },
                onSkip = { submit(null, SelfieCheck.SKIPPED) },
                onCancel = { kioskStep = KioskStep.CONFIRM }
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
                val schedule = employee.scheduleLabel()
                if (schedule.isNotBlank()) {
                    Text(schedule, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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
    entry: EntryAvailability?,
    isLoading: Boolean,
    onClockIn: () -> Unit,
    onCancel: () -> Unit
) {
    if (employee == null) return

    val available = entry as? EntryAvailability.Available
    val alreadyIn = entry != null && available == null
    val split = employee.hasSplitShift

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

            val schedule = employee.scheduleLabel()
            if (schedule.isNotBlank()) {
                Surface(shape = RoundedCornerShape(50), color = KioskBlue.copy(alpha = 0.08f)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, null, tint = KioskBlue, modifier = Modifier.size(14.dp))
                        Text(
                            (if (split) "Turno mixto: " else "Turno: ") + schedule,
                            style = MaterialTheme.typography.labelMedium,
                            color = KioskBlue
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            val doneMessage = when (entry) {
                is EntryAvailability.Done -> if (split && entry.entries.size > 1)
                    "Ya marcaste tus dos entradas de hoy: " +
                        entry.entries.joinToString(" y ") { kioskFormatTime(it.clockIn) }
                else "Ya marcaste tu entrada hoy a las ${kioskFormatTime(entry.entries.last().clockIn)}"
                is EntryAvailability.SecondOpensLater ->
                    "Ya marcaste la entrada del 1er turno a las ${kioskFormatTime(entry.first.clockIn)}. " +
                        "La del 2do turno se marca a partir de las ${entry.opensAt}."
                else -> null
            }
            if (doneMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BrandColors.PrimaryContainer),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = KioskBlue, modifier = Modifier.size(18.dp))
                        Text(
                            doneMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = KioskBlue
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            if (isLoading || entry == null) {
                CircularProgressIndicator(color = KioskBlue, modifier = Modifier.size(36.dp))
            } else {
                if (available != null) {
                    Button(
                        onClick = onClockIn,
                        colors = ButtonDefaults.buttonColors(containerColor = KioskGreen),
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Login, null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (split) "MARCAR ENTRADA · ${shiftName(available.shiftNumber).uppercase()}" else "MARCAR ENTRADA",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            TextButton(onClick = onCancel, enabled = !isLoading) {
                Text(if (alreadyIn) "Volver" else "Cancelar", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ── Selfie ────────────────────────────────────────────────────────────────────

/** Tras este tiempo sin foto aparece "Marcar sin foto": la cámara nunca deja a nadie sin marcar. */
private const val SELFIE_SKIP_AFTER_MS = 15_000L

/**
 * Pensada para la tablet en horizontal: todo apilado al centro y el círculo chico a
 * propósito (a lo sumo 200 dp o un tercio del alto), para que nadie sienta que se está
 * tomando una foto de cuerpo entero. La foto se dispara sola.
 */
@Composable
private fun KioskSelfieContent(
    modifier: Modifier,
    employeeName: String,
    isSaving: Boolean,
    onCaptured: (ByteArray, SelfieCheck) -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit
) {
    var status by remember { mutableStateOf(SelfieCameraStatus.STARTING) }
    var showSkip by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SELFIE_SKIP_AFTER_MS)
        showSkip = true
    }
    val cameraBroken = status == SelfieCameraStatus.NO_PERMISSION || status == SelfieCameraStatus.NO_CAMERA
    val captured = status == SelfieCameraStatus.CAPTURED

    val ringColor = when (status) {
        SelfieCameraStatus.BLINK -> KioskBlue
        SelfieCameraStatus.CAPTURED -> KioskGreen
        SelfieCameraStatus.MULTIPLE_FACES -> BrandColors.Accent
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val message = when (status) {
        SelfieCameraStatus.STARTING -> "Abriendo cámara…"
        SelfieCameraStatus.NO_PERMISSION -> "La app no tiene permiso para usar la cámara"
        SelfieCameraStatus.NO_CAMERA -> "Este equipo no tiene cámara frontal disponible"
        SelfieCameraStatus.LOOK_AT_CAMERA -> "Acércate y mira a la cámara"
        SelfieCameraStatus.MULTIPLE_FACES -> "Solo una persona frente a la cámara"
        SelfieCameraStatus.BLINK -> "Parpadea"
        SelfieCameraStatus.CAPTURED -> "¡Listo!"
    }

    BoxWithConstraints(
        modifier = modifier.background(BrandColors.Ivory).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        val circle = minOf(200.dp, maxHeight / 3, maxWidth * 0.6f)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                employeeName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Marcando entrada",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .size(circle)
                    .border(4.dp, ringColor, CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (!cameraBroken) {
                    SelfieCamera(
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        onStatus = { status = it },
                        onCaptured = onCaptured
                    )
                }
                if (captured || isSaving) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }

            Text(
                message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (cameraBroken) KioskRed else KioskBlue,
                textAlign = TextAlign.Center
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel, enabled = !captured && !isSaving) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.outline)
                }
                if ((showSkip || cameraBroken) && !captured && !isSaving) {
                    OutlinedButton(onClick = onSkip) {
                        Text("Marcar sin foto")
                    }
                }
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

    val totalSeconds = (successScreenMillis(info.lateMinutes) / 1_000).toInt()
    var countdown by remember { mutableIntStateOf(totalSeconds) }
    LaunchedEffect(Unit) {
        repeat(totalSeconds) {
            delay(1_000)
            countdown--
        }
    }
    val isLate = info.lateMinutes > 0

    Box(
        modifier = modifier.background(BrandColors.Ivory),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp).widthIn(max = 560.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(KioskGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Text(
                if (info.shiftLabel.isBlank()) "Entrada registrada" else "Entrada registrada · ${info.shiftLabel}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = KioskGreen,
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
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (info.withPhoto) {
                BrandChip("Foto registrada", ChipTone.INFO)
            } else {
                BrandChip("Marcada sin foto", ChipTone.ATTENTION)
            }

            if (isLate) {
                // Tono de aviso, no de regaño: naranja de relleno y texto AccentDeep (paleta).
                Card(
                    colors = CardDefaults.cardColors(containerColor = BrandColors.Accent.copy(alpha = 0.14f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Has llegado ${kioskFormatLateTime(info.lateMinutes)} tarde",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.AccentDeep,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Esfuérzate por llegar un poco más temprano para evitar penalizaciones innecesarias.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                BrandChip("A tiempo, ¡gracias por tu puntualidad!", ChipTone.SUCCESS)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Volviendo en $countdown...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun shiftName(shiftNumber: Int) = if (shiftNumber == 2) "2do turno" else "1er turno"

private fun successScreenMillis(lateMinutes: Int): Long = if (lateMinutes > 0) 7_000L else 4_000L

// ── Utilidades ────────────────────────────────────────────────────────────────

private fun kioskFormatTime(millis: Long): String {
    if (millis == 0L) return "--:--"
    val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${ldt.hour.toString().padStart(2, '0')}:${ldt.minute.toString().padStart(2, '0')}"
}

/** "1 minuto", "25 minutos", "1 hora", "1 hora y 10 minutos". */
private fun kioskFormatLateTime(minutes: Int): String {
    fun mins(n: Int) = "$n minuto${if (n == 1) "" else "s"}"
    if (minutes < 60) return mins(minutes)
    val h = minutes / 60
    val m = minutes % 60
    val hours = "$h hora${if (h == 1) "" else "s"}"
    return if (m == 0) hours else "$hours y ${mins(m)}"
}
