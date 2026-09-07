package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.DailyCashClose
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.util.fmtMoney
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyCashCloseListScreen(
    onNavigateBack: () -> Unit,
    onOpenClose: (branchId: String, date: String) -> Unit,
    onOpenToday: (branchId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: DailyCashCloseViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val user = authState.user
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && user != null) viewModel.loadBranches(companyId, user)
    }
    LaunchedEffect(state.selectedBranchId, state.yearMonth, companyId) {
        if (companyId.isNotEmpty() && state.selectedBranchId.isNotEmpty()) {
            viewModel.loadMonth(companyId)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    val totalDepositedMonth = state.monthlyCloses.sumOf { it.depositedAmount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cierres del día", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSeaBlue,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (state.selectedBranchId.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onOpenToday(state.selectedBranchId) },
                    containerColor = BrandColors.Primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cerrar día actual")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = WarmIvory
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                if (state.branches.isEmpty() && state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@Column
                }
                if (state.branches.isEmpty()) {
                    Text("No tienes acceso a sucursales con capacidad de venta.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    return@Column
                }

                if (state.branches.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.branches.forEach { b ->
                            FilterChip(
                                selected = b.id == state.selectedBranchId,
                                onClick = { viewModel.selectBranch(b.id) },
                                label = { Text(b.name) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Row(Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { viewModel.setYearMonth(prevYM(state.yearMonth)) }) {
                        Icon(Icons.Default.ChevronLeft, "Mes anterior")
                    }
                    Text(monthLabel(state.yearMonth), fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
                    IconButton(onClick = { viewModel.setYearMonth(nextYM(state.yearMonth)) }) {
                        Icon(Icons.Default.ChevronRight, "Mes siguiente")
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepSeaBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Total depositado · ${monthLabel(state.yearMonth)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.85f))
                            Text("Q ${totalDepositedMonth.fmtMoney(2)}",
                                fontWeight = FontWeight.Bold, color = Color.White,
                                style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }

                if (state.monthlyCloses.isEmpty()) {
                    Text("Sin cierres en este mes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.monthlyCloses, key = { it.id }) { c ->
                            CloseRow(c) { onOpenClose(c.branchId, c.closeDate) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloseRow(c: DailyCashClose, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(c.closeDate, fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
                Text("Q ${c.depositedAmount.fmtMoney(2)}", fontWeight = FontWeight.Bold,
                    color = BrandColors.AccentDeep)
            }
            Text("${c.sessionIds.size} sesiones · Cerrado por ${c.closedByUserName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Queda mañana", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
                Text("Q ${c.leftForNextDay.fmtMoney(2)}",
                    style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ventas del día", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
                Text("Q ${c.totalSales.fmtMoney(2)}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun monthLabel(yearMonth: String): String {
    val (y, m) = yearMonth.split("-").let { it[0] to it[1].toInt() }
    val names = listOf("Enero","Febrero","Marzo","Abril","Mayo","Junio",
        "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre")
    return "${names[m - 1]} $y"
}

private fun prevYM(ym: String): String {
    val (y, m) = ym.split("-").let { it[0].toInt() to it[1].toInt() }
    val (ny, nm) = if (m == 1) (y - 1) to 12 else y to (m - 1)
    return "${ny.toString().padStart(4, '0')}-${nm.toString().padStart(2, '0')}"
}

private fun nextYM(ym: String): String {
    val (y, m) = ym.split("-").let { it[0].toInt() to it[1].toInt() }
    val (ny, nm) = if (m == 12) (y + 1) to 1 else y to (m + 1)
    return "${ny.toString().padStart(4, '0')}-${nm.toString().padStart(2, '0')}"
}
