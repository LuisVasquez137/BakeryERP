package com.bakery.erp.presentation.reports.sales

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Permission
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.User
import com.bakery.erp.model.UserRole
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.reports.shared.BranchFilterRow
import com.bakery.erp.presentation.reports.shared.EmptyDataNote
import com.bakery.erp.presentation.reports.shared.HorizontalBars
import com.bakery.erp.presentation.reports.shared.KpiTile
import com.bakery.erp.presentation.reports.shared.PeriodNavigator
import com.bakery.erp.presentation.reports.shared.ReportPeriod
import com.bakery.erp.presentation.reports.shared.ReportPrimary
import com.bakery.erp.presentation.reports.shared.ReportPeriodSelector
import com.bakery.erp.presentation.reports.shared.ReportSection
import com.bakery.erp.presentation.reports.shared.ReportSurface
import com.bakery.erp.presentation.reports.shared.ReportSlate
import com.bakery.erp.presentation.reports.shared.VerticalBars
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.util.fmtMoney
import kotlinx.datetime.LocalDate

private val MONTHS = listOf(
    "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesReportScreen(
    user: User?,
    onNavigateBack: () -> Unit,
    viewModel: SalesReportViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val showValue = user?.hasPermission(Permission.VIEW_SALES_VALUE) == true || user?.role == UserRole.OWNER

    LaunchedEffect(user?.id) { user?.let { viewModel.ensureLoadedFor(it) } }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = ReportSurface,
        topBar = {
            BrandTopAppBar(
                title = { Text("Reporte de ventas", color = Color.White, fontWeight = FontWeight.Bold) },
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
                    item { CenteredProgress() }
                } else {
                    val s = state.summary
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiTile(
                                label = "Ingresos",
                                value = if (showValue) "Q ${s.totalRevenue.fmtMoney()}" else "—",
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                modifier = Modifier.weight(1f)
                            )
                            KpiTile(
                                label = "Tickets",
                                value = s.transactionCount.toString(),
                                icon = Icons.Default.Receipt,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiTile(
                                label = "Ticket promedio",
                                value = if (showValue) "Q ${s.avgTicket.fmtMoney()}" else "—",
                                icon = Icons.Default.AttachMoney,
                                modifier = Modifier.weight(1f)
                            )
                            KpiTile(
                                label = "Unidades",
                                value = s.unitsSold.toInt().toString(),
                                icon = Icons.Default.ShoppingBag,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        ReportSection(title = "Ventas por día") {
                            VerticalBars(
                                items = s.byDay.map { p ->
                                    val day = p.dateLabel.takeLast(2)
                                    day to p.revenue
                                }
                            )
                        }
                    }
                    item {
                        ReportSection(title = "Por método de pago") {
                            if (s.byPaymentMethod.isEmpty()) {
                                EmptyDataNote()
                            } else {
                                HorizontalBars(
                                    items = s.byPaymentMethod.entries
                                        .sortedByDescending { it.value.total }
                                        .map { SalePaymentMethod.label(it.key) to it.value.total },
                                    valueFormatter = { v -> if (showValue) "Q ${v.fmtMoney()}" else "${(v / s.totalRevenue.coerceAtLeast(1.0) * 100).toInt()}%" }
                                )
                            }
                        }
                    }
                    item {
                        ReportSection(title = "Top productos") {
                            if (s.topProducts.isEmpty()) {
                                EmptyDataNote()
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    s.topProducts.take(10).forEach { p ->
                                        TopProductRow(
                                            name = p.productName,
                                            quantity = p.quantity,
                                            revenue = p.revenue,
                                            showValue = showValue
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Solo si se cobró empaque en el período: la panadería que no cobra la
                    // bolsa no ve una sección vacía.
                    if (s.cardSurchargeTotal > 0.004 && showValue) item {
                        ReportSection(title = "Recargo por tarjeta") {
                            Text("Q ${s.cardSurchargeTotal.fmtMoney(2)} cobrados de recargo en el período",
                                style = MaterialTheme.typography.bodyMedium, color = ReportSlate)
                        }
                    }
                    if (s.packagingSales.isNotEmpty()) item {
                        ReportSection(title = "Empaques cobrados") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                s.packagingSales.forEach { p ->
                                    TopProductRow(
                                        name = p.productName,
                                        quantity = p.quantity,
                                        revenue = p.revenue,
                                        showValue = showValue
                                    )
                                }
                            }
                        }
                    }
                    item {
                        ReportSection(title = "Cajeros") {
                            if (s.byCashier.isEmpty()) {
                                EmptyDataNote()
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    s.byCashier.take(10).forEach { c ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = c.cashierName.ifEmpty { c.cashierId.take(6) },
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = ReportSlate,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    "${c.transactionCount} tickets · ticket Q ${c.avgTicket.fmtMoney()}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF607585)
                                                )
                                            }
                                            if (showValue) {
                                                Text(
                                                    "Q ${c.total.fmtMoney()}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = ReportPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        ReportSection(title = "Ventas por hora") {
                            val hourly = (0..23).map { h ->
                                val v = s.hourHistogram[h] ?: 0.0
                                h.toString() to v
                            }
                            VerticalBars(items = hourly, barWidth = 10.dp)
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
private fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ReportPrimary)
    }
}

@Composable
private fun TopProductRow(name: String, quantity: Double, revenue: Double, showValue: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(ReportPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ShoppingBag, null, tint = ReportPrimary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            color = ReportSlate,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${quantity.toInt()} u",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF607585),
            modifier = Modifier.padding(end = 12.dp)
        )
        if (showValue) {
            Text(
                "Q ${revenue.fmtMoney()}",
                fontWeight = FontWeight.SemiBold,
                color = ReportPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatPeriodLabel(date: LocalDate, period: ReportPeriod): String = when (period) {
    ReportPeriod.DAY   -> "${date.dayOfMonth} ${MONTHS.getOrElse(date.monthNumber) { "?" }} ${date.year}"
    ReportPeriod.WEEK  -> "Semana al ${date.dayOfMonth} ${MONTHS.getOrElse(date.monthNumber) { "?" }}"
    ReportPeriod.MONTH -> "${MONTHS.getOrElse(date.monthNumber) { "?" }} ${date.year}"
}
