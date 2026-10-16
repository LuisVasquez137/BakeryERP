package com.bakery.erp.presentation.reports.productcost

import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.Permission
import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.domain.DeliveryMonthCost
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
 * "Costo de reparto": sueldo del piloto + gastos del camión del mes, repartidos entre las
 * tiendas por las veces que el camión llegó a cada una.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryCostScreen(
    user: User?,
    onNavigateBack: () -> Unit,
    viewModel: DeliveryCostViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val companyId = user?.orgId.orEmpty()
    val canSeePay = user?.hasPermission(Permission.VIEW_EMPLOYEE_SALARY) == true
    viewModel.includePay = canSeePay
    LaunchedEffect(companyId, canSeePay) { if (companyId.isNotEmpty()) viewModel.load(companyId) }

    Scaffold(
        containerColor = ReportSurface,
        topBar = {
            BrandTopAppBar(
                title = { Text("Costo de reparto", color = Color.White, fontWeight = FontWeight.Bold) },
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
                if (!canSeePay) item { NoSalaryNotice() }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(viewModel.periodOptions) { p ->
                            FilterChip(selected = p == state.period, onClick = { viewModel.load(companyId, p) },
                                label = { Text(formatPeriodLabel(p)) })
                        }
                    }
                }
                val cost = state.cost
                when {
                    state.isLoading -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
                    }
                    state.error != null -> item {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                    cost != null -> {
                        item { TotalCard(cost) }
                        if (cost.payrollPeople == 0) item {
                            BrandChip(
                                "Nadie está marcado como Reparto. En Colaboradores, abrí la ficha del piloto y en " +
                                    "\"¿A quién se le carga su sueldo?\" elegí Reparto.",
                                ChipTone.ATTENTION
                            )
                        }
                        if (cost.expenses == 0.0) item {
                            BrandChip(
                                "No hay gastos anotados a Reparto este mes (gasolina, mantenimiento del camión). " +
                                    "Al anotarlos, elegí Reparto como destino.",
                                ChipTone.ATTENTION
                            )
                        }
                        item {
                            Text("Por tienda", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                color = BrandColors.Primary, modifier = Modifier.padding(top = 6.dp))
                        }
                        if (cost.stores.isEmpty()) item {
                            Text("No salió pan en camión este mes.", style = MaterialTheme.typography.bodySmall)
                        }
                        items(cost.stores, key = { it.branchId }) { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.branchName, fontWeight = FontWeight.SemiBold)
                                    Text("${s.deliveries} entregas", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Q ${s.charge.fmtMoney(2)}", fontWeight = FontWeight.Bold, color = BrandColors.Primary)
                            }
                            HorizontalDivider()
                        }
                        item {
                            Text(
                                "Una entrega es cada vez que el camión llega a una tienda (mismo día y turno, aunque " +
                                    "lleve pan y repostería). Se cuentan las notas de despacho que salieron, tengan o " +
                                    "no reparto asignado. El pan de la planta a su propia tienda no viaja y no cuenta.",
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
private fun TotalCard(c: DeliveryMonthCost) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Line("Sueldo y prestaciones (${c.payrollPeople} personas)", "Q ${c.payroll.fmtMoney(2)}")
            Line("Gastos del camión", "Q ${c.expenses.fmtMoney(2)}")
            Line("Total del mes", "Q ${c.total.fmtMoney(2)}", bold = true)
            Line("Entregas", "${c.deliveries}")
            c.costPerDelivery?.let { Line("Costo por entrega", "Q ${it.fmtMoney(2)}", bold = true) }
        }
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}
