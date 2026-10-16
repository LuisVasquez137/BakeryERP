package com.bakery.erp.presentation.delivery

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.DeliveryRun
import com.bakery.erp.model.DeliveryRunStatus
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


/** Umbral a partir del cual una hoja se considera "tardada". Una jornada de reparto normal
 *  en una panadería ronda 1–2h; >2h ya merece revisión. Ajustable acá. */
private const val SLOW_RUN_MIN = 120L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryHistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRun: (runId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: DeliveryViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    // Mes navegable. Default = mes en curso.
    var anchorMonth by remember { mutableStateOf(currentMonthStart()) }

    LaunchedEffect(companyId, anchorMonth) {
        if (companyId.isNotEmpty()) {
            val (from, until) = monthBounds(anchorMonth)
            viewModel.loadHistoryRange(companyId, from, until)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val runs = state.historyRuns

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Historial de entregas", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSeaBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = WarmIvory
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                MonthHeader(
                    anchor = anchorMonth,
                    onPrev = { anchorMonth = shiftMonth(anchorMonth, -1) },
                    onNext = { anchorMonth = shiftMonth(anchorMonth, +1) },
                    onResetCurrent = { anchorMonth = currentMonthStart() }
                )

                when {
                    state.isHistoryLoading && runs.isEmpty() ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    runs.isEmpty() ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.History, null,
                                    modifier = Modifier.size(36.dp),
                                    tint = DeepSeaBlue.copy(alpha = 0.5f))
                                Spacer(Modifier.height(8.dp))
                                Text("Sin hojas de reparto este mes",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DeepSeaBlue.copy(alpha = 0.7f))
                            }
                        }
                    else -> AdaptiveContainer {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item { MonthSummaryCard(runs) }
                            items(runs, key = { it.id }) { run ->
                                HistoryRow(run = run, onClick = { onNavigateToRun(run.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    anchor: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onResetCurrent: () -> Unit
) {
    val label = remember(anchor) { formatMonth(anchor) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "Mes anterior", tint = DeepSeaBlue) }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = DeepSeaBlue,
            modifier = Modifier.clickable { onResetCurrent() }
        )
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "Mes siguiente", tint = DeepSeaBlue) }
    }
}

@Composable
private fun MonthSummaryCard(runs: List<DeliveryRun>) {
    val total = runs.size
    val completed = runs.count { it.status == DeliveryRunStatus.COMPLETED }
    val cancelled = runs.count { it.status == DeliveryRunStatus.CANCELLED }
    val durations = runs.mapNotNull { runDurationMinutes(it) }
    val avgMin = if (durations.isNotEmpty()) durations.average().toLong() else 0L
    val slow = durations.count { it > SLOW_RUN_MIN }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSeaBlue),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Resumen del mes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryStat("Total", total.toString())
                SummaryStat("Completadas", completed.toString())
                SummaryStat("Canceladas", cancelled.toString())
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryStat("Duración promedio", if (avgMin > 0) formatDurationMin(avgMin) else "—")
                SummaryStat(
                    "Tardadas (>${formatDurationMin(SLOW_RUN_MIN)})",
                    slow.toString(),
                    highlight = slow > 0
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, highlight: Boolean = false) {
    Column {
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) Color(0xFFFFAB91) else Color.White
        )
    }
}

@Composable
private fun HistoryRow(run: DeliveryRun, onClick: () -> Unit) {
    val durMin = runDurationMinutes(run)
    val isSlow = durMin != null && durMin > SLOW_RUN_MIN

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${formatDay(run.plannedDate)} · ${run.originBranchName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepSeaBlue
                    )
                    Text(
                        "Repartidor: ${run.driverName.ifEmpty { "—" }} · ${run.stopCount} parada(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChipSmall(run.status)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isSlow) BrandColors.AccentDeep else DeepSeaBlue.copy(alpha = 0.6f))
                Spacer(Modifier.width(4.dp))
                Text(
                    durMin?.let { formatDurationMin(it) } ?: "Sin duración",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSlow) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSlow) BrandColors.AccentDeep else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isSlow) {
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null,
                                    modifier = Modifier.size(12.dp), tint = BrandColors.AccentDeep)
                                Spacer(Modifier.width(4.dp))
                                Text("Tardada",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandColors.AccentDeep)
                            }
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFFFFF3E0)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChipSmall(status: String) {
    val (text, fg, bg) = when (status) {
        DeliveryRunStatus.PENDING     -> Triple("Pendiente", Color(0xFF6A1B9A), Color(0xFFF3E5F5))
        DeliveryRunStatus.IN_PROGRESS -> Triple("En curso",   Color(0xFF1565C0), Color(0xFFE3F2FD))
        DeliveryRunStatus.COMPLETED   -> Triple("Completada", Color(0xFF2E7D32), Color(0xFFE8F5E9))
        DeliveryRunStatus.CANCELLED   -> Triple("Cancelada",  Color(0xFF616161), Color(0xFFEEEEEE))
        else                          -> Triple(status, Color.Gray, Color.LightGray)
    }
    AssistChip(
        onClick = {},
        label = {
            Text(text, style = MaterialTheme.typography.labelSmall,
                color = fg, fontWeight = FontWeight.Medium)
        },
        colors = AssistChipDefaults.assistChipColors(containerColor = bg)
    )
}

// ── Helpers de fechas ────────────────────────────────────────────────────────

private fun runDurationMinutes(run: DeliveryRun): Long? {
    val s = run.startedAt ?: return null
    val e = run.completedAt ?: return null
    if (s <= 0L || e <= 0L || e < s) return null
    return (e - s) / 60_000L
}

private fun formatDurationMin(min: Long): String =
    when {
        min < 60 -> "${min} min"
        else -> {
            val h = min / 60
            val m = min % 60
            if (m == 0L) "${h} h" else "${h} h ${m} min"
        }
    }

private fun currentMonthStart(): Long {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val firstOfMonth = LocalDate(today.year, today.monthNumber, 1)
    return LocalDateTime(firstOfMonth, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds()
}

private fun shiftMonth(anchor: Long, deltaMonths: Int): Long {
    val tz = TimeZone.currentSystemDefault()
    val current = Instant.fromEpochMilliseconds(anchor).toLocalDateTime(tz).date
    var y = current.year
    var m = current.monthNumber + deltaMonths
    while (m < 1) { m += 12; y -= 1 }
    while (m > 12) { m -= 12; y += 1 }
    val first = LocalDate(y, m, 1)
    return LocalDateTime(first, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds()
}

private fun monthBounds(anchor: Long): Pair<Long, Long> {
    val tz = TimeZone.currentSystemDefault()
    val start = Instant.fromEpochMilliseconds(anchor).toLocalDateTime(tz).date
    val nextMonthFirst = start.plus(1, DateTimeUnit.MONTH)
    val from = LocalDateTime(start, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds()
    val until = LocalDateTime(nextMonthFirst, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds() - 1
    return from to until
}

private val MONTH_NAMES = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

private fun formatMonth(anchor: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val d = Instant.fromEpochMilliseconds(anchor).toLocalDateTime(tz).date
    return "${MONTH_NAMES[d.monthNumber - 1]} ${d.year}"
}
