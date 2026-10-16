package com.bakery.erp.presentation.reports.productcost

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.domain.BranchResultsMonth
import com.bakery.erp.domain.PlantResult
import com.bakery.erp.domain.StoreResult
import com.bakery.erp.model.User
import com.bakery.erp.model.formatPeriodLabel
import com.bakery.erp.presentation.reports.shared.ReportPrimary
import com.bakery.erp.presentation.reports.shared.ReportSurface
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.util.fmtMoney

/**
 * "Resultado por sucursal" (sección 2.4 de `propuesta-rentabilidad-por-sucursal.md`): el aporte de
 * cada tienda, el resultado de cada planta y los generales, que juntos dan la ganancia del mes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchResultScreen(
    user: User?,
    onNavigateBack: () -> Unit,
    viewModel: BranchResultViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val companyId = user?.orgId.orEmpty()
    LaunchedEffect(companyId) { if (companyId.isNotEmpty()) viewModel.load(companyId) }

    Scaffold(
        containerColor = ReportSurface,
        topBar = {
            BrandTopAppBar(
                title = { Text("Resultado por sucursal", color = Color.White, fontWeight = FontWeight.Bold) },
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
        }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(viewModel.periodOptions) { p ->
                            FilterChip(selected = p == state.period, onClick = { viewModel.load(companyId, p) },
                                label = { Text(formatPeriodLabel(p)) })
                        }
                    }
                }
                val r = state.result
                when {
                    state.isLoading -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
                    }
                    state.error != null -> item { Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error) }
                    r != null -> {
                        item { SummaryCard(r) }
                        if (!state.standardCostFrozen) item {
                            BrandChip(
                                "Este mes no hay costo estándar congelado: el pan se valoró con la lista \"Sucursales\". " +
                                    "Lo que gana cada tienda y cada planta todavía no es real; el total de la empresa sí.",
                                ChipTone.ATTENTION
                            )
                        }
                        val noSales = r.stores.filter { it.sales == 0.0 && it.bread > 0.0 }
                        if (noSales.isNotEmpty()) item {
                            BrandChip(
                                "${noSales.joinToString { it.name }}: recibió pan pero no tiene ventas en caja. " +
                                    "Sin ventas registradas su resultado sale en rojo.",
                                ChipTone.ATTENTION
                            )
                        }
                        if (!r.balances) item {
                            BrandChip(
                                "La suma no cuadra con la empresa (Q ${r.total.fmtMoney(2)} contra " +
                                    "Q ${r.companyCheck.fmtMoney(2)}). Hay algo mal anotado.",
                                ChipTone.DANGER
                            )
                        }
                        item { SectionTitle("Tiendas") }
                        items(r.stores, key = { "s_${it.branchId}" }) { StoreCard(it) }
                        item { SectionTitle("Plantas") }
                        items(r.plants, key = { "p_${it.branchId}" }) { PlantCard(it) }
                        item { GeneralsCard(r) }
                        item {
                            Text(
                                "Las compras de insumos y reventa cuentan el mes en que se recibieron, no cuando se " +
                                    "usaron: un mes que se llenó la bodega la planta sale más cara. Los generales no se " +
                                    "reparten: lo que aporta cada tienda es lo que deja para pagarlos.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(r: BranchResultsMonth) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            r.stores.forEach { Line(storeLabel(it), it.contribution, signed = true) }
            r.plants.forEach { Line("Planta ${it.name}", it.result, signed = true) }
            Line("Generales", -r.generals.total, signed = true)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Line("Ganancia de la empresa", r.total, signed = true, bold = true)
        }
    }
}

@Composable
private fun StoreCard(s: StoreResult) {
    DetailCard(storeLabel(s), s.contribution, "Aporte") {
        Line("Ventas de caja", s.sales)
        Line("Pan recibido", -s.bread)
        if (s.resale != 0.0) Line("Reventa comprada", -s.resale)
        if (s.ingredients != 0.0) Line("Insumos comprados", -s.ingredients)
        if (s.transfers != 0.0) Line("Traslados (recibido − enviado)", -s.transfers)
        Line("Margen", s.grossMargin, bold = true)
        Line("Gastos", -s.expenses)
        Line("Planilla", -s.payroll)
        if (s.deliveries > 0 || s.delivery != 0.0) Line("Reparto (${s.deliveries} entregas)", -s.delivery)
    }
}

@Composable
private fun PlantCard(p: PlantResult) {
    DetailCard("Planta ${p.name}", p.result, "Resultado") {
        Line("Despachado a las tiendas", p.shipped)
        Line("Insumos comprados", -p.material)
        if (p.transfers != 0.0) Line("Traslados (recibido − enviado)", -p.transfers)
        Line("Gastos", -p.expenses)
        Line("Planilla", -p.payroll)
    }
}

@Composable
private fun GeneralsCard(r: BranchResultsMonth) {
    val g = r.generals
    DetailCard("Generales", -g.total, "Total") {
        Line("Gastos generales", -g.expenses)
        Line("Planilla general", -g.payroll)
        if (g.undistributedDelivery != 0.0) Line("Reparto sin entregas", -g.undistributedDelivery)
        if (g.orphan != 0.0) Line("De sucursales que ya no existen", -g.orphan)
    }
}

@Composable
private fun DetailCard(title: String, total: Double, totalLabel: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = BrandColors.Primary)
                Text("$totalLabel ${money(total, signed = true)}", fontWeight = FontWeight.Bold, color = tone(total))
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (open) content()
        }
    }
}

@Composable
private fun Line(label: String, value: Double, signed: Boolean = false, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(money(value, signed), style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (signed) tone(value) else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        color = BrandColors.Primary, modifier = Modifier.padding(top = 6.dp))
}

private fun storeLabel(s: StoreResult) = if (s.isPlantStore) "Tienda ${s.name}" else s.name

private fun money(v: Double, signed: Boolean): String {
    val sign = if (v < 0) "−" else if (signed && v > 0) "+" else ""
    return "${sign}Q ${kotlin.math.abs(v).fmtMoney(2)}"
}

@Composable
private fun tone(v: Double): Color = when {
    v < 0 -> BrandColors.Danger
    v > 0 -> BrandColors.Success
    else -> MaterialTheme.colorScheme.onSurface
}
