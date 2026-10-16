package com.bakery.erp.presentation.reports.productcost

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.bakery.erp.domain.PlantMonthCost
import com.bakery.erp.domain.ProductStandardCostDraft
import com.bakery.erp.model.Permission
import com.bakery.erp.model.User
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.formatPeriodLabel
import com.bakery.erp.presentation.reports.shared.ReportPrimary
import com.bakery.erp.presentation.reports.shared.ReportSurface
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * "Costo estándar": a cuánto le entrega cada planta el pan a las tiendas este mes. Muestra de
 * dónde sale cada número (gastos, planilla, kilos de masa, tasa) para que el dueño lo revise
 * antes de congelarlo; al congelarlo, los despachos del mes salen a ese costo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardCostScreen(
    user: User?,
    onNavigateBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    viewModel: StandardCostViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val companyId = user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf(false) }
    val canFreeze = user?.hasPermission(Permission.MANAGE_PRICE_LISTS) == true

    LaunchedEffect(companyId) { if (companyId.isNotEmpty()) viewModel.load(companyId) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    LaunchedEffect(state.error) {
        if (state.plants.isNotEmpty()) state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    val target = formatPeriodLabel(state.targetPeriod)
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Congelar costo de $target") },
            text = {
                Text(
                    "${state.withCost.size} productos quedan con este costo todo $target, y los despachos " +
                        "que se hagan desde ahora salen a ese valor. Los ya hechos no cambian." +
                        if (state.withoutCost.isNotEmpty())
                            "\n\n${state.withoutCost.size} sin costo se siguen despachando con la lista \"Sucursales\"."
                        else ""
                )
            },
            confirmButton = {
                Button(onClick = { confirm = false; user?.let { viewModel.freeze(companyId, it) } }) { Text("Congelar") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        containerColor = ReportSurface,
        topBar = {
            BrandTopAppBar(
                title = { Text("Costo estándar", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { if (companyId.isNotEmpty()) viewModel.load(companyId) }) {
                        Icon(Icons.Default.Refresh, "Actualizar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ReportPrimary)
            )
        },
        bottomBar = {
            if (canFreeze && state.withCost.isNotEmpty() && !state.isLoading) {
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Button(
                        onClick = { confirm = true },
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
                    ) {
                        Text(if (state.frozen != null) "Volver a congelar $target" else "Congelar costo de $target")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            when {
                state.isLoading && state.plants.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.error != null && state.plants.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                else -> Content(state, viewModel.periodOptions, { viewModel.load(companyId, it) }, onOpenProduct)
            }
            LoadingOverlay(visible = state.isFreezing, message = "Congelando costo...")
        }
    }
}

@Composable
private fun Content(
    state: StandardCostUiState,
    periods: List<String>,
    onPeriod: (String) -> Unit,
    onOpenProduct: (String) -> Unit
) {
    val frozenByProduct = state.frozenCosts.associateBy { it.productId }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                periods.forEach { p ->
                    FilterChip(selected = p == state.targetPeriod, onClick = { onPeriod(p) },
                        label = { Text(formatPeriodLabel(p)) })
                }
            }
        }
        item { StatusCard(state) }
        if (state.splitBranchesWithoutShare.isNotEmpty()) item {
            BrandChip(
                "${state.splitBranchesWithoutShare.joinToString()}: tiene gastos de local compartido pero 0% de tienda. " +
                    "Ponelo en la ficha de la sucursal.",
                ChipTone.ATTENTION
            )
        }
        item { SectionTitle("Plantas — datos de ${formatPeriodLabel(state.sourcePeriod)}") }
        items(state.plants, key = { "plant_${it.branchId}" }) { PlantCard(it) }

        val byPlant = state.withCost.groupBy { it.plantName }
        byPlant.forEach { (plant, list) ->
            item(key = "hdr_$plant") { SectionTitle("$plant (${list.size})") }
            items(list, key = { "c_${it.productId}" }) { d ->
                ProductRow(d, frozenByProduct[d.productId]?.unitCost, onOpenProduct)
            }
        }
        if (state.withoutCost.isNotEmpty()) {
            item(key = "hdr_missing") {
                Column {
                    SectionTitle("Sin costo (${state.withoutCost.size})")
                    Text("Se siguen despachando con la lista \"Sucursales\".",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.withoutCost, key = { "m_${it.productId}" }) { d ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenProduct(d.productId) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(d.productName, Modifier.weight(1f))
                    Text(d.gap?.label.orEmpty(), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StatusCard(state: StandardCostUiState) {
    val frozen = state.frozen
    val target = formatPeriodLabel(state.targetPeriod)
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (frozen != null) BrandColors.SuccessSoft else BrandColors.AccentSoft)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (frozen != null) {
                Text("Congelado para $target", fontWeight = FontWeight.SemiBold, color = BrandColors.Success)
                Text(
                    "${frozen.productCount} productos, el ${dateLabel(frozen.frozenAt)} por ${frozen.frozenByName}. " +
                        "Los despachos de $target salen a este costo. Abajo está el cálculo con los datos de hoy: " +
                        "si cambió algo (un gasto que faltaba), se puede volver a congelar.",
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.TextSlate
                )
            } else {
                Text("$target sin congelar", fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
                Text(
                    "Los despachos de $target se valoran con la lista \"Sucursales\". Revisá los gastos y kilos " +
                        "de cada planta y congelá cuando estén completos: los costos fijos anotados, la planilla " +
                        "con su planta y los lotes cerrados.",
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.TextSlate
                )
            }
        }
    }
}

@Composable
private fun PlantCard(p: PlantMonthCost) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(p.branchName, fontWeight = FontWeight.Bold, color = BrandColors.Primary)
            Line("Gastos de la planta", "Q ${p.expenses.fmtMoney(2)}")
            Line(
                "Planilla (${p.payrollPeople} personas" +
                    (if (p.payrollFromRecords > 0) ", ${p.payrollFromRecords} con nómina" else ", salario base") + " + prestaciones)",
                "Q ${p.payroll.fmtMoney(2)}"
            )
            Line("Total", "Q ${p.total.fmtMoney(2)}", bold = true)
            Line("Masa producida", "${(p.doughGrams / 1000).fmtMoney(0)} kg")
            val rate = p.ratePerGram
            if (rate != null) Line("Gasto de planta por kilo", "Q ${(rate * 1000).fmtMoney(2)}", bold = true)
            else BrandChip("Masa insuficiente para repartir: sus productos quedan sin costo", ChipTone.ATTENTION)
            if (p.linesWithoutWeight > 0) {
                Text(
                    "${p.linesWithoutWeight} líneas de lote sin peso en crudo: su masa no se contó y la tasa sale más alta.",
                    style = MaterialTheme.typography.labelSmall, color = BrandColors.AccentDeep
                )
            }
        }
    }
}

@Composable
private fun ProductRow(d: ProductStandardCostDraft, frozenCost: Double?, onOpenProduct: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpenProduct(d.productId) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(d.productName, fontWeight = FontWeight.SemiBold)
            Text(
                "Materia prima Q ${d.materialCost.fmtMoney(3)} + planta Q ${d.overheadCost.fmtMoney(3)} · ${d.doughGrams.fmt(0)} g" +
                    if (d.materialIncomplete) " · falta precio de un insumo" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (d.materialIncomplete) BrandColors.AccentDeep else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Q ${d.unitCost.fmtMoney(3)}", fontWeight = FontWeight.Bold, color = BrandColors.Primary)
            val share = if (d.salePrice > 0) " · ${(d.unitCost / d.salePrice * 100).fmt(0)}% de Q ${d.salePrice.fmtMoney(2)}" else ""
            Text(
                (frozenCost?.let { "congelado Q ${it.fmtMoney(3)}" } ?: "") + share,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        color = BrandColors.Primary, modifier = Modifier.padding(top = 6.dp))
}

private fun dateLabel(millis: Long): String {
    val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${d.dayOfMonth}/${d.monthNumber}/${d.year}"
}
