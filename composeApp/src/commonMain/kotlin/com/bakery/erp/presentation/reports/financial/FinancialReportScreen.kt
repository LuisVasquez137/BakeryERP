package com.bakery.erp.presentation.reports.financial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.BreadCountReason
import com.bakery.erp.model.User
import com.bakery.erp.presentation.reports.shared.HorizontalBars
import com.bakery.erp.presentation.reports.shared.KpiTile
import com.bakery.erp.presentation.reports.shared.PeriodNavigator
import com.bakery.erp.presentation.reports.shared.ReportPrimary
import com.bakery.erp.presentation.reports.shared.ReportSection
import com.bakery.erp.presentation.reports.shared.ReportSlate
import com.bakery.erp.presentation.reports.shared.ReportSurface
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney
import kotlinx.datetime.LocalDate
import com.bakery.erp.ui.theme.BrandColors

private val MONTHS = listOf(
    "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialReportScreen(
    user: User?,
    onNavigateBack: () -> Unit,
    viewModel: FinancialReportViewModel
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(user?.id) { user?.let { viewModel.ensureLoadedFor(it) } }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = ReportSurface,
        topBar = {
            TopAppBar(
                title = { Text("Estado financiero", color = Color.White, fontWeight = FontWeight.Bold) },
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
                    PeriodNavigator(
                        label = monthLabel(state.anchorMonth),
                        onPrev = { user?.let(viewModel::prev) },
                        onNext = { user?.let(viewModel::next) }
                    )
                }
                if (state.isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ReportPrimary)
                        }
                    }
                } else {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiTile(
                                label = "Ingresos",
                                value = "Q ${state.sales.totalRevenue.fmtMoney()}",
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                color = Color(0xFF1A8F4A),
                                modifier = Modifier.weight(1f)
                            )
                            KpiTile(
                                label = "Egresos",
                                value = "Q ${(state.expenses.total + state.payroll.totalEmployerCost).fmtMoney()}",
                                icon = Icons.AutoMirrored.Filled.TrendingDown,
                                color = BrandColors.AccentDeep,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiTile(
                                label = "Utilidad operativa",
                                value = "Q ${state.operatingProfit.fmtMoney()}",
                                icon = Icons.Default.AttachMoney,
                                color = if (state.operatingProfit >= 0) Color(0xFF1A8F4A) else BrandColors.AccentDeep,
                                modifier = Modifier.weight(1f)
                            )
                            KpiTile(
                                label = "Margen",
                                value = "${state.marginPct.fmt(1)}%",
                                icon = Icons.Default.Percent,
                                color = if (state.marginPct >= 0) Color(0xFF1A8F4A) else BrandColors.AccentDeep,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiTile(
                                label = "Comprado",
                                value = "Q ${state.purchases.total.fmtMoney()}",
                                icon = Icons.Default.ShoppingCart,
                                color = ReportPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            KpiTile(
                                label = "Salió de caja",
                                value = "Q ${state.cashOut.fmtMoney()}",
                                icon = Icons.AutoMirrored.Filled.TrendingDown,
                                color = BrandColors.AccentDeep,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        ReportSection(title = "P&L del mes") {
                            PnLLine("Ingresos por ventas", state.sales.totalRevenue, isPositive = true)
                            PnLLine("Mermas valoradas",   -state.merma.totalValue)
                            if (state.stock.counterDamagedValue > 0.004)
                                PnLLine("Venía mal (sacado en caja)", -state.stock.counterDamagedValue)
                            if (state.stock.courtesyValue > 0.004)
                                PnLLine("Regalos desde la caja (a costo)", -state.stock.courtesyValue)
                            PnLLine("Gastos operativos",  -state.expenses.total)
                            PnLLine("Planilla (costo patronal)", -state.payroll.totalEmployerCost)
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            PnLLine(
                                label = "Utilidad operativa",
                                amount = state.operatingProfit,
                                isPositive = state.operatingProfit >= 0,
                                bold = true
                            )
                            // Sin esta nota, el dueño que compró Q8,000 de harina no
                            // entiende por qué el P&L no la muestra y desconfía del número.
                            if (state.purchases.inventoryTotal > 0.0) {
                                Spacer(Modifier.height(8.dp))
                                ReportNote(
                                    "No incluye Q ${state.purchases.inventoryTotal.fmtMoney()} de " +
                                        "compras de materia prima y reventa: son inventario hasta " +
                                        "que el producto se vende. Están abajo, en Compras del mes."
                                )
                            }
                        }
                    }
                    item {
                        ReportSection(title = "Compras del mes") {
                            if (state.purchases.purchasesCount == 0) {
                                Text(
                                    "Sin compras registradas este mes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF607585)
                                )
                            } else {
                                PnLLine("Total comprado", state.purchases.total, isPositive = null)
                                Spacer(Modifier.height(6.dp))
                                BreakdownRow(
                                    "A inventario",
                                    state.purchases.inventoryTotal,
                                    "Materia prima y reventa. Pega al costo cuando se vende."
                                )
                                BreakdownRow(
                                    "Gasto directo",
                                    state.purchases.expensedTotal,
                                    "Suministros y servicios. Ya contados arriba en el P&L."
                                )
                                // Solo se dibuja cuando de verdad hay algo sin repartir. Es
                                // preferible mostrarlo a repartirlo a ojo: adivinar acá sería
                                // mentir justo en el número que la sección existe para dar.
                                if (state.purchases.unclassifiedTotal > 0.0) {
                                    BreakdownRow(
                                        "Sin desglosar",
                                        state.purchases.unclassifiedTotal,
                                        "Compras mixtas registradas antes de esta versión."
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                                PnLLine("Ya pagado", state.purchases.paidTotal, isPositive = null)
                                PnLLine(
                                    "Queda debiendo",
                                    state.purchases.pendingTotal,
                                    isPositive = state.purchases.pendingTotal <= 0.0
                                )
                                Spacer(Modifier.height(8.dp))
                                ReportNote(
                                    "${state.purchases.invoiceCount} " +
                                        (if (state.purchases.invoiceCount == 1) "factura" else "facturas") +
                                        " · ${state.purchases.quickCount} " +
                                        (if (state.purchases.quickCount == 1) "compra rápida" else "compras rápidas")
                                )
                            }
                        }
                    }
                    item {
                        ReportSection(title = "Salida de dinero del mes") {
                            PnLLine("Ventas cobradas", state.sales.totalRevenue, isPositive = true)
                            PnLLine("Compras pagadas", -state.purchases.paidTotal)
                            PnLLine("Gastos fuera de compras", -state.expenses.standalone)
                            PnLLine("Planilla (costo patronal)", -state.payroll.totalEmployerCost)
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            PnLLine(
                                label = "Efectivo neto",
                                amount = state.netCash,
                                isPositive = state.netCash >= 0,
                                bold = true
                            )
                            Spacer(Modifier.height(8.dp))
                            ReportNote(
                                "Los suministros y servicios no se restan dos veces: van dentro " +
                                    "de \"Compras pagadas\", no en la línea de gastos. Los abonos a " +
                                    "facturas de meses anteriores cuentan en su propio mes."
                            )
                        }
                    }
                    item {
                        ReportSection(title = "Gastos por categoría") {
                            if (state.expenses.byCategory.isEmpty()) {
                                Text("Sin gastos registrados para este mes.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF607585))
                            } else {
                                HorizontalBars(
                                    items = state.expenses.byCategory.entries
                                        .sortedByDescending { it.value }
                                        .map { it.key.label to it.value },
                                    valueFormatter = { "Q ${it.fmtMoney()}" }
                                )
                            }
                        }
                    }
                    item {
                        ReportSection(title = "Por sucursal") {
                            if (state.expenses.byBranch.isEmpty() && state.sales.byBranch.isEmpty()) {
                                Text("Sin datos por sucursal.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF607585))
                            } else {
                                val ingresosPorBranch = state.sales.byBranch.associate { it.branchId to (it.branchName to it.total) }
                                val gastosPorBranch = state.expenses.byBranch.associate { it.branchId to (it.branchName to it.total) }
                                val allKeys = (ingresosPorBranch.keys + gastosPorBranch.keys).distinct()
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    allKeys.forEach { id ->
                                        val name = ingresosPorBranch[id]?.first
                                            ?: gastosPorBranch[id]?.first
                                            ?: "Sucursal"
                                        val ingreso = ingresosPorBranch[id]?.second ?: 0.0
                                        val gasto = gastosPorBranch[id]?.second ?: 0.0
                                        val utilidad = ingreso - gasto
                                        BranchPnLRow(name, ingreso, gasto, utilidad)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        ReportSection(title = "Mermas del mes") {
                            if (state.merma.byReason.isEmpty()) {
                                Text("Sin mermas registradas.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF607585))
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    state.merma.byReason.entries
                                        .sortedByDescending { it.value.valueImpact }
                                        .forEach { (reason, stat) ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(BreadCountReason.label(reason), color = ReportSlate)
                                                Text(
                                                    "${stat.units.toInt()} u  ·  Q ${stat.valueImpact.fmtMoney()}",
                                                    color = BrandColors.AccentDeep,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                }
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
private fun PnLLine(label: String, amount: Double, isPositive: Boolean? = null, bold: Boolean = false) {
    val color = when {
        isPositive == true  -> Color(0xFF1A8F4A)
        isPositive == false -> BrandColors.AccentDeep
        amount < 0          -> BrandColors.AccentDeep
        else                -> ReportSlate
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = ReportSlate,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = (if (amount < 0) "− " else "") + "Q ${kotlin.math.abs(amount).fmtMoney()}",
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Renglón de desglose: monto a la derecha y, debajo, por qué está donde está. */
@Composable
private fun BreakdownRow(label: String, amount: Double, hint: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = ReportSlate, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Q ${amount.fmtMoney()}",
                color = ReportSlate,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(hint, style = MaterialTheme.typography.labelSmall, color = Color(0xFF607585))
    }
}

@Composable
private fun ReportNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Color(0xFF607585))
}

@Composable
private fun BranchPnLRow(name: String, ingreso: Double, gasto: Double, utilidad: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF7F4EF))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, fontWeight = FontWeight.SemiBold, color = ReportSlate, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Q ${utilidad.fmtMoney()}",
                color = if (utilidad >= 0) Color(0xFF1A8F4A) else BrandColors.AccentDeep,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "Ingresos Q ${ingreso.fmtMoney()}  ·  Gastos Q ${gasto.fmtMoney()}",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF607585)
        )
    }
}

private fun monthLabel(date: LocalDate): String =
    "${MONTHS.getOrElse(date.monthNumber) { "?" }} ${date.year}"
