package com.bakery.erp.presentation.reports.branchsupply

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.User
import com.bakery.erp.presentation.production.ProductionViewModel
import com.bakery.erp.presentation.reports.shared.BranchFilterRow
import com.bakery.erp.presentation.reports.shared.EmptyDataNote
import com.bakery.erp.presentation.reports.shared.KpiTile
import com.bakery.erp.presentation.reports.shared.PeriodNavigator
import com.bakery.erp.presentation.reports.shared.ReportPeriod
import com.bakery.erp.presentation.reports.shared.ReportPeriodSelector
import com.bakery.erp.presentation.reports.shared.ReportPrimary
import com.bakery.erp.presentation.reports.shared.ReportSection
import com.bakery.erp.presentation.reports.shared.ReportSurface
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtTrim
import kotlinx.datetime.LocalDate

private val MONTHS = listOf(
    "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

/**
 * "Pedido vs. recibido": qué pidió la sucursal, qué le asignó la planta en cada
 * turno, qué le empacaron, qué contó al recibir y qué vendió.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchSupplyReportScreen(
    user: User?,
    onNavigateBack: () -> Unit,
    viewModel: BranchSupplyReportViewModel,
    productionViewModel: ProductionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val productionState by productionViewModel.uiState.collectAsState()

    LaunchedEffect(user?.id) {
        user?.let {
            viewModel.ensureLoadedFor(it)
            // Para agrupar por masa. Ya cacheado en el VM si se abrió producción antes.
            productionViewModel.loadRecipeRefByProductIdIfNeeded(it.orgId)
        }
    }

    Scaffold(
        containerColor = ReportSurface,
        topBar = {
            BrandTopAppBar(
                title = { Text("Pedido vs. recibido", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { user?.let(viewModel::reload) }) {
                        Icon(Icons.Default.Refresh, "Actualizar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ReportPrimary)
            )
        }
    ) { padding ->
        // Tope más ancho que el de los formularios: con dos turnos la tabla lleva 8 columnas.
        AdaptiveContainer(modifier = Modifier.padding(padding), maxContentWidth = 1100.dp) {
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
                            onSelect = { id -> user?.let { u -> viewModel.setBranch(u, id) } },
                            allowAll = false
                        )
                    }
                }

                when {
                    state.isLoading -> item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ReportPrimary)
                        }
                    }
                    state.error != null -> item {
                        Text(
                            "No se pudo cargar el reporte: ${state.error}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    state.branches.isEmpty() -> item {
                        Text(
                            "No tenés sucursales de venta asignadas.",
                            color = BrandColors.MutedSlate
                        )
                    }
                    state.selectedPlant == null -> item {
                        ReportSection(title = "Sin pedidos en este período") { EmptyDataNote() }
                    }
                    else -> {
                        val plant = state.selectedPlant!!
                        val r = plant.report
                        // Una pestaña por planta: cada una despacha con sus propios turnos,
                        // y juntas la lista se vuelve interminable.
                        if (state.plants.size > 1) {
                            item {
                                PlantTabs(
                                    plants = state.plants,
                                    selectedPlantId = plant.plantId,
                                    onSelect = viewModel::selectPlant
                                )
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                KpiTile("Pedido", r.orderedTotal.fmtTrim(1), Icons.Default.ReceiptLong, modifier = Modifier.weight(1f))
                                KpiTile("Enviado", r.sentTotal.fmtTrim(1), Icons.Default.LocalShipping, modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                KpiTile("Recibido", r.receivedTotal.fmtTrim(1), Icons.Default.Inventory2, modifier = Modifier.weight(1f))
                                KpiTile(
                                    "Cumplimiento",
                                    r.fulfillmentPct?.let { "${it.fmtTrim(1)}%" } ?: "—",
                                    Icons.Default.Percent,
                                    modifier = Modifier.weight(1f)
                                )
                                KpiTile(
                                    "Faltó al contar",
                                    r.shortTotal.fmtTrim(1),
                                    Icons.Default.RemoveShoppingCart,
                                    color = if (r.shortTotal > 0) MaterialTheme.colorScheme.error else ReportPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        item {
                            ReportSection(
                                title = "Pedido a ${plant.plantName}",
                                subtitle = buildString {
                                    append("${plant.orderCount} pedido")
                                    if (plant.orderCount != 1) append("s")
                                    if (r.awaitingCountTotal > 0) {
                                        append(" · ${r.awaitingCountTotal.fmtTrim(1)} unidades en camino o sin contar")
                                    }
                                }
                            ) {
                                BranchSupplyTable(
                                    report = r,
                                    recipeRefByProductId = productionState.recipeRefByProductId,
                                    showSold = true
                                )
                            }
                        }
                        item {
                            Text(
                                "Recibido es lo que la sucursal contó al cerrar la recepción; la diferencia " +
                                    "se calcula solo sobre lo ya contado. Vendido incluye pan que ya estaba " +
                                    "en la tienda antes del período, así que no tiene por qué coincidir con lo recibido.",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandColors.MutedSlate
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatPeriodLabel(date: LocalDate, period: ReportPeriod): String = when (period) {
    ReportPeriod.DAY   -> "${date.dayOfMonth} ${MONTHS.getOrElse(date.monthNumber) { "?" }} ${date.year}"
    ReportPeriod.WEEK  -> "Semana al ${date.dayOfMonth} ${MONTHS.getOrElse(date.monthNumber) { "?" }}"
    ReportPeriod.MONTH -> "${MONTHS.getOrElse(date.monthNumber) { "?" }} ${date.year}"
}

@Composable
private fun PlantTabs(
    plants: List<PlantSupply>,
    selectedPlantId: String,
    onSelect: (String) -> Unit
) {
    val selectedIndex = plants.indexOfFirst { it.plantId == selectedPlantId }.coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.White,
        contentColor = ReportPrimary,
        edgePadding = 0.dp
    ) {
        plants.forEachIndexed { index, p ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(p.plantId) },
                text = {
                    Text(
                        "${p.plantName} (${p.report.orderedTotal.fmtTrim(0)})",
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == selectedIndex) ReportPrimary else BrandColors.MutedSlate
                    )
                }
            )
        }
    }
}
