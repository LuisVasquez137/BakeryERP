package com.bakery.erp.presentation.plantissues

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.PlantIssue
import com.bakery.erp.model.PlantIssueIngredientSummary
import com.bakery.erp.model.PlantIssueReason
import com.bakery.erp.model.rhythmLabel
import com.bakery.erp.model.Permission
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.reports.shared.BranchFilterRow
import com.bakery.erp.presentation.reports.shared.EmptyDataNote
import com.bakery.erp.presentation.reports.shared.KpiTile
import com.bakery.erp.presentation.reports.shared.PeriodNavigator
import com.bakery.erp.presentation.reports.shared.ReportSection
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.util.fmt
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private val MONTHS = listOf(
    "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

/**
 * "Consumo de planta": qué material salió de bodega sin pasar por una receta, y —el
 * dato que originó el módulo— **cada cuánto se abre otro empaque**.
 *
 * Es a la vez el reporte y el historial, no dos pantallas: quien mira "sacan un saco
 * cada 2.5 días" es la misma persona que quiere ver los vales que dan ese número, y
 * el único lugar desde donde se puede corregir uno mal anotado.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlantIssueReportScreen(
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: PlantIssueViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val tz = remember { TimeZone.currentSystemDefault() }
    val snackbarHostState = remember { SnackbarHostState() }

    var anchorMonth by remember {
        mutableStateOf(Clock.System.now().toLocalDateTime(tz).date)
    }
    var branchFilter by remember { mutableStateOf<String?>(null) }
    var showIssueDialog by remember { mutableStateOf(false) }
    var pendingUndo by remember { mutableStateOf<PlantIssue?>(null) }

    val canIssue = user?.hasPermission(Permission.ISSUE_PLANT_MATERIAL) == true

    LaunchedEffect(companyId, user?.id) {
        if (companyId.isNotEmpty() && user != null) viewModel.load(companyId, user)
    }
    LaunchedEffect(companyId, user?.id, anchorMonth, branchFilter, state.accessibleBranches.size) {
        if (companyId.isEmpty() || user == null) return@LaunchedEffect
        val start = LocalDate(anchorMonth.year, anchorMonth.monthNumber, 1)
        val end = start.plus(DatePeriod(months = 1))
        viewModel.loadPeriod(
            companyId = companyId,
            user = user,
            fromMillis = start.atStartOfDayIn(tz).toEpochMilliseconds(),
            untilMillis = end.atStartOfDayIn(tz).toEpochMilliseconds(),
            branchId = branchFilter.orEmpty()
        )
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
            // El vale que se acaba de deshacer tiene que salir de la lista del mes,
            // que se leyó antes del cambio.
            if (companyId.isNotEmpty() && user != null) {
                val start = LocalDate(anchorMonth.year, anchorMonth.monthNumber, 1)
                val end = start.plus(DatePeriod(months = 1))
                viewModel.loadPeriod(
                    companyId, user,
                    start.atStartOfDayIn(tz).toEpochMilliseconds(),
                    end.atStartOfDayIn(tz).toEpochMilliseconds(),
                    branchFilter.orEmpty()
                )
            }
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    if (showIssueDialog && user != null) {
        PlantIssueDialog(
            companyId = companyId,
            user = user,
            viewModel = viewModel,
            onDismiss = { showIssueDialog = false },
            presetBranchId = branchFilter.orEmpty()
        )
    }

    pendingUndo?.let { issue ->
        UndoIssueDialog(
            issue = issue,
            onConfirm = {
                user?.let { viewModel.undoIssue(companyId, issue, it) }
                pendingUndo = null
            },
            onDismiss = { pendingUndo = null }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Consumo de planta", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (canIssue) {
                FloatingActionButton(
                    onClick = { showIssueDialog = true },
                    containerColor = BrandColors.Primary,
                    contentColor = Color.White
                ) { Icon(Icons.Default.Inventory2, contentDescription = "Anotar material") }
            }
        }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(BrandColors.Ivory),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    PeriodNavigator(
                        label = "${MONTHS.getOrElse(anchorMonth.monthNumber) { "?" }} ${anchorMonth.year}",
                        onPrev = { anchorMonth = anchorMonth.minus(DatePeriod(months = 1)) },
                        onNext = { anchorMonth = anchorMonth.plus(DatePeriod(months = 1)) },
                        onCenterClick = { anchorMonth = Clock.System.now().toLocalDateTime(tz).date }
                    )
                }
                if (state.accessibleBranches.size > 1) {
                    item {
                        BranchFilterRow(
                            branches = state.accessibleBranches.map { it.id to it.name },
                            selectedBranchId = branchFilter,
                            onSelect = { branchFilter = it }
                        )
                    }
                }

                if (state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    item { KpiRow(state.periodIssues, state.summaries) }

                    item {
                        ReportSection(
                            title = "Cada cuánto se abre otro empaque",
                            // La explicación va en la pantalla y no solo en el código:
                            // sin ella, "cada 2.5 días" se lee como una medición exacta
                            // del consumo, y no lo es — es el ritmo con que van a bodega.
                            subtitle = "Días entre un empaque y el siguiente del mismo insumo. " +
                                "Las cantidades sueltas no cuentan acá — salen del empaque que " +
                                "ya estaba abierto — pero sí suman al total."
                        ) {
                            if (state.summaries.isEmpty()) EmptyDataNote()
                            else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                state.summaries.forEach { IngredientSummaryRow(it) }
                            }
                        }
                    }

                    item {
                        ReportSection(title = "En qué se va") {
                            val byReason = state.periodIssues
                                .groupBy { it.reason }
                                .map { (reason, list) -> reason to list.size }
                                .sortedByDescending { it.second }
                            if (byReason.isEmpty()) EmptyDataNote()
                            else FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                byReason.forEach { (reason, count) ->
                                    BrandChip(
                                        "${PlantIssueReason.shortLabel(reason)} · $count",
                                        ChipTone.INFO
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            "Movimientos del mes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.TextSlate
                        )
                    }
                    if (state.periodIssues.isEmpty()) {
                        item { EmptyDataNote() }
                    } else {
                        items(state.periodIssues, key = { it.id }) { issue ->
                            IssueRow(
                                issue = issue,
                                // Deshacer mueve inventario hacia atrás, así que pide el
                                // mismo permiso que sacarlo. No se limita a "el que lo
                                // sacó": el vale mal anotado a las 4am lo suele descubrir
                                // el del siguiente turno.
                                canUndo = canIssue && !state.isSaving,
                                onUndo = { pendingUndo = issue }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiRow(issues: List<PlantIssue>, summaries: List<PlantIssueIngredientSummary>) {
    val totalPackages = issues.sumOf { it.packageQuantity }
    // Promedio de los promedios por insumo: la media global entre vales de insumos
    // distintos no significa nada (la harina sale a diario y la manteca cada dos
    // semanas; su promedio conjunto no describe a ninguna de las dos).
    val avgGaps = summaries.mapNotNull { it.avgDaysBetween }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        KpiTile(
            label = "Empaques",
            value = totalPackages.fmt(0),
            icon = Icons.Default.Inventory2,
            modifier = Modifier.weight(1f)
        )
        KpiTile(
            label = "Salidas",
            value = issues.size.toString(),
            icon = Icons.Default.LocalShipping,
            modifier = Modifier.weight(1f)
        )
        KpiTile(
            label = "Días entre salidas",
            value = if (avgGaps.isEmpty()) "—" else (avgGaps.sum() / avgGaps.size).fmt(1),
            icon = Icons.Default.Schedule,
            color = BrandColors.AccentDeep,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun IngredientSummaryRow(summary: PlantIssueIngredientSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                summary.ingredientName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            // El texto sale de `rhythmLabel` y no se arma acá porque los tres casos
            // (ritmo medible / un solo empaque / solo sueltas) los comparte con el
            // reporte de Producción, y el tercero es fácil de olvidar en uno de los dos.
            BrandChip(
                text = summary.rhythmLabel(),
                tone = if (summary.avgDaysBetween != null) ChipTone.ATTENTION else ChipTone.NEUTRAL
            )
        }
        Text(
            buildString {
                if (summary.packages > 0.0) {
                    append(summary.packages.fmt(0)).append(" empaques")
                    append(" · ")
                }
                append(summary.quantityInBaseUnit.fmt(1)).append(" ").append(summary.baseUnit)
                append(" · ").append(summary.issueCount).append(" registros")
                // Se dice cuántos fueron sueltos: es lo que explica por qué el total de
                // cantidad sube sin que suba el número de empaques.
                val loose = summary.issueCount - summary.packageIssueCount
                if (loose > 0) append(" (").append(loose).append(" sueltos)")
            },
            style = MaterialTheme.typography.bodySmall,
            color = BrandColors.MutedSlate
        )
        val topReason = summary.packagesByReason.maxByOrNull { it.value }?.key
        if (topReason != null) {
            Text(
                "Sobre todo: ${PlantIssueReason.label(topReason)}",
                style = MaterialTheme.typography.labelSmall,
                color = BrandColors.MutedSlate
            )
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun IssueRow(issue: PlantIssue, canUndo: Boolean, onUndo: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    issue.ingredientName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${issue.displayQuantity} · ${issue.reasonLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.MutedSlate
                )
                if (issue.notes.isNotBlank()) {
                    Text(
                        issue.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.MutedSlate
                    )
                }
                Text(
                    buildString {
                        append(formatIssueDate(issue.issuedAt))
                        if (issue.branchName.isNotBlank()) append(" · ").append(issue.branchName)
                        if (issue.issuedByUserName.isNotBlank()) append(" · ").append(issue.issuedByUserName)
                        if (issue.shiftName.isNotBlank()) append(" · ").append(issue.shiftName)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.MutedSlate
                )
            }
            if (canUndo) {
                IconButton(onClick = onUndo) {
                    Icon(
                        Icons.Default.Undo,
                        contentDescription = "Deshacer",
                        tint = BrandColors.AccentIcon,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UndoIssueDialog(issue: PlantIssue, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Deshacer este registro?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Vuelven ${issue.displayQuantity} de ${issue.ingredientName} " +
                        "al inventario de ${issue.branchName}."
                )
                // Se dice explícitamente que el Kardex conserva las dos líneas: el
                // usuario espera que "deshacer" borre, y la sorpresa al ver dos
                // movimientos es peor que avisarlo antes.
                Text(
                    "El Kardex conserva el registro y le agrega la devolución — nada se borra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.MutedSlate
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Deshacer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun formatIssueDate(millis: Long): String {
    if (millis <= 0L) return ""
    val dt = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val hh = dt.hour.toString().padStart(2, '0')
    val mm = dt.minute.toString().padStart(2, '0')
    return "${dt.dayOfMonth} ${MONTHS.getOrElse(dt.monthNumber) { "" }.take(3)} · $hh:$mm"
}
