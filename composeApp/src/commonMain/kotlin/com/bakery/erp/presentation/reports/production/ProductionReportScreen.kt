package com.bakery.erp.presentation.reports.production

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.DiscrepancyType
import com.bakery.erp.model.ProductionWasteReason
import com.bakery.erp.model.User
import com.bakery.erp.model.rhythmLabel
import com.bakery.erp.presentation.reports.shared.BranchFilterRow
import com.bakery.erp.presentation.reports.shared.EmptyDataNote
import com.bakery.erp.presentation.reports.shared.KpiTile
import com.bakery.erp.presentation.reports.shared.PairedBars
import com.bakery.erp.presentation.reports.shared.PeriodNavigator
import com.bakery.erp.presentation.reports.shared.ReportPeriod
import com.bakery.erp.presentation.reports.shared.ReportPeriodSelector
import com.bakery.erp.presentation.reports.shared.ReportPrimary
import com.bakery.erp.presentation.reports.shared.ReportSection
import com.bakery.erp.presentation.reports.shared.ReportSlate
import com.bakery.erp.presentation.reports.shared.ReportSurface
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.util.fmt
import kotlinx.datetime.LocalDate
import com.bakery.erp.ui.theme.BrandColors

private val MONTHS = listOf(
    "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionReportScreen(
    user: User?,
    onNavigateBack: () -> Unit,
    viewModel: ProductionReportViewModel
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(user?.id) { user?.let { viewModel.ensureLoadedFor(it) } }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = ReportSurface,
        topBar = {
            BrandTopAppBar(
                title = { Text("Reporte de producción", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ReportPrimary)
            )
        }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ReportPeriodSelector(
                        selected = state.period,
                        onChange = { user?.let { u -> viewModel.setPeriod(u, it) } }
                    )
                }
                item {
                    PeriodNavigator(
                        label = formatPeriodLabel(state.anchorDate, state.period),
                        onPrev = { user?.let(viewModel::prev) },
                        onNext = { user?.let(viewModel::next) }
                    )
                }
                if (state.branches.size > 1) {
                    item {
                        BranchFilterRow(
                            branches = state.branches.map { it.id to it.name },
                            selectedBranchId = state.selectedBranchId,
                            onSelect = { id -> user?.let { u -> viewModel.setBranch(u, id) } }
                        )
                    }
                }

                if (state.isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ReportPrimary)
                        }
                    }
                } else {
                    val p = state.production
                    val d = state.dispatch
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiTile(
                                label = "Lotes completados",
                                value = p.completedLotsCount.toString(),
                                icon = Icons.Default.Factory,
                                modifier = Modifier.weight(1f)
                            )
                            KpiTile(
                                label = "Unidades producidas",
                                value = p.producedTotal.toInt().toString(),
                                icon = Icons.Default.LocalShipping,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiTile(
                                label = "% Eficiencia",
                                value = "${p.efficiencyPct.fmt(1)}%",
                                icon = Icons.Default.Percent,
                                modifier = Modifier.weight(1f)
                            )
                            KpiTile(
                                label = "Merma en tránsito",
                                value = "${d.inTransitLossUnits.toInt()} u",
                                icon = Icons.Default.Warning,
                                color = BrandColors.AccentDeep,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        ReportSection(title = "Planeado vs producido por día") {
                            PairedBars(
                                items = p.byDay.map { d2 ->
                                    Triple(d2.dateLabel.takeLast(2), d2.planned, d2.produced)
                                }
                            )
                        }
                    }
                    item {
                        ReportSection(title = "Por turno") {
                            if (p.byShift.isEmpty()) {
                                EmptyDataNote()
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    p.byShift.forEach { sh ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(sh.shiftName, fontWeight = FontWeight.SemiBold, color = ReportSlate)
                                                Text(
                                                    "${sh.lotsCount} lotes",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF607585)
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    "P: ${sh.planned.toInt()}  ·  R: ${sh.produced.toInt()}",
                                                    color = ReportPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        ReportSection(title = "Por producto (top 15)") {
                            if (p.byProduct.isEmpty()) {
                                EmptyDataNote()
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    p.byProduct.take(15).forEach { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                row.productName,
                                                modifier = Modifier.weight(1f),
                                                color = ReportSlate,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "${row.produced.toInt()} / ${row.planned.toInt()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF607585),
                                                modifier = Modifier.padding(end = 10.dp)
                                            )
                                            DiffBadge(row.diffPct)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        // Merma de horno: pan que se amasó y se perdió. Va junto a
                        // "planeado vs producido" porque es lo que explica la diferencia
                        // entre la masa que se gastó y el pan que salió — sin esta
                        // sección, el reporte muestra eficiencia sin decir a dónde se fue.
                        ReportSection(
                            title = "Pan perdido en el horno",
                            subtitle = "Se quemó, se cayó o salió mal. Esta masa se descontó " +
                                "del inventario aunque el pan no se haya entregado."
                        ) {
                            if (p.wasteByProduct.isEmpty()) {
                                EmptyDataNote()
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    p.wasteByProduct.take(12).forEach { w ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    w.productName,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = ReportSlate
                                                )
                                                Text(
                                                    buildString {
                                                        append(w.recipeName.ifBlank { "Sin masa" })
                                                        val top = w.byReason.maxByOrNull { it.value }?.key
                                                        if (top != null) {
                                                            append(" · ")
                                                            append(ProductionWasteReason.label(top))
                                                        }
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF607585)
                                                )
                                            }
                                            Text(
                                                // El porcentaje va sobre lo amasado, no sobre lo
                                                // bueno: la pregunta es "de cada 100 que metí al
                                                // horno, cuántas perdí".
                                                "${w.wasteUnits.toInt()} u · ${w.wastePct.fmt(1)}%",
                                                color = BrandColors.AccentDeep,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        // Material de planta: lo que se gastó fuera de la receta. Va antes
                        // de las discrepancias porque explica parte del faltante que después
                        // aparece al contar — la harina de la mesa no la descuenta el lote.
                        ReportSection(
                            title = "Material de planta",
                            subtitle = "Salidas de bodega que la receta no explica: harina " +
                                "para la mesa, manteca para las bandejas, limpieza."
                        ) {
                            if (state.plantIssueSummaries.isEmpty()) {
                                EmptyDataNote()
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    state.plantIssueSummaries.take(10).forEach { sm ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    sm.ingredientName,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = ReportSlate
                                                )
                                                Text(
                                                    "${sm.rhythmLabel()} · ${sm.issueCount} registros",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF607585)
                                                )
                                            }
                                            Text(
                                                if (sm.packages > 0.0) "${sm.packages.toInt()} empaques"
                                                else "${sm.quantityInBaseUnit.fmt(1)} ${sm.baseUnit}",
                                                color = BrandColors.AccentDeep,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        ReportSection(title = "Discrepancias en despacho") {
                            if (d.byDestinationBranch.isEmpty()) {
                                EmptyDataNote()
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    d.byDestinationBranch.forEach { b ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    b.branchName,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = ReportSlate
                                                )
                                                Text(
                                                    "${b.notesCount} notas · ${b.discrepancyCount} con discrepancia",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF607585)
                                                )
                                            }
                                            Text(
                                                "−${b.lossUnits.toInt()} u",
                                                color = BrandColors.AccentDeep,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    HorizontalDivider()
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        d.byDiscrepancyType.entries.forEach { (type, stat) ->
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    DiscrepancyType.label(type).ifEmpty { type },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF607585)
                                                )
                                                Text(
                                                    "${stat.occurrences} · ${stat.units.toInt()} u",
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = ReportSlate
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (p.lateOrderCount > 0) {
                        item {
                            ReportSection(title = "Pedidos tardíos del período") {
                                Text(
                                    "${p.lateOrderCount} pedidos llegaron tarde y requirieron consolidación adicional.",
                                    color = ReportSlate,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }
}

@Composable
private fun DiffBadge(diffPct: Double) {
    val (color, text) = when {
        diffPct >= 0  -> Color(0xFF1A8F4A) to "+${diffPct.fmt(0)}%"
        diffPct < -5  -> BrandColors.AccentDeep to "${diffPct.fmt(0)}%"
        else          -> Color(0xFFE08F00) to "${diffPct.fmt(0)}%"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatPeriodLabel(date: LocalDate, period: ReportPeriod): String = when (period) {
    ReportPeriod.DAY   -> "${date.dayOfMonth} ${MONTHS.getOrElse(date.monthNumber) { "?" }} ${date.year}"
    ReportPeriod.WEEK  -> "Semana al ${date.dayOfMonth} ${MONTHS.getOrElse(date.monthNumber) { "?" }}"
    ReportPeriod.MONTH -> "${MONTHS.getOrElse(date.monthNumber) { "?" }} ${date.year}"
}
