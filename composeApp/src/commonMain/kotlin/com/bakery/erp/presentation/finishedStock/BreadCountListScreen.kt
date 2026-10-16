package com.bakery.erp.presentation.finishedStock

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.BreadCount
import com.bakery.erp.model.BreadCountStatus
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.util.fmtMoney
import kotlinx.datetime.LocalDate
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreadCountListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCount: (countId: String) -> Unit,
    onOpenNewForBranch: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: BreadCountViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val user = authState.user
    // El valor de la merma sale del costo del pan.
    val canSeeValue = user?.hasPermission(com.bakery.erp.model.Permission.VIEW_DISPATCH_VALUE) == true
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && user != null) viewModel.loadBranches(companyId, user)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Conteo de pan", fontWeight = FontWeight.SemiBold) },
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
                    onClick = onOpenNewForBranch,
                    containerColor = BrandColors.Primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Contar pan de hoy")
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
                    Text("No tienes acceso a ninguna sucursal con capacidad de venta.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    return@Column
                }

                if (state.branches.size > 1) {
                    Text("Sucursal", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.branches.forEach { b ->
                            FilterChip(
                                selected = b.id == state.selectedBranchId,
                                onClick = { viewModel.selectBranch(companyId, b.id) },
                                label = { Text(b.name) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                MonthSelector(
                    yearMonth = state.yearMonth,
                    onPrev = { viewModel.setYearMonth(companyId, prevYearMonth(state.yearMonth)) },
                    onNext = { viewModel.setYearMonth(companyId, nextYearMonth(state.yearMonth)) }
                )
                Spacer(Modifier.height(12.dp))

                if (state.monthlyCounts.isEmpty()) {
                    Text("Sin conteos en este mes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.monthlyCounts, key = { it.id }) { count ->
                            CountCard(count, canSeeValue) { onNavigateToCount(count.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(yearMonth: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "Mes anterior") }
        Text(monthLabel(yearMonth), fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "Mes siguiente") }
    }
}

@Composable
private fun CountCard(count: BreadCount, canSeeValue: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        onClick = onClick
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(count.countDate, fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
                AssistChip(
                    onClick = onClick,
                    label = { Text(BreadCountStatus.label(count.status)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (count.status == BreadCountStatus.CLOSED)
                            Color(0xFF1B5E20).copy(alpha = 0.12f)
                        else BurntOrange.copy(alpha = 0.18f),
                        labelColor = if (count.status == BreadCountStatus.CLOSED)
                            Color(0xFF1B5E20) else BrandColors.AccentDeep
                    )
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                count.branchName + count.countedByUserName.takeIf { it.isNotBlank() }?.let { " · Contó $it" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Productos contados", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    Text("${count.lineCount}", fontWeight = FontWeight.SemiBold)
                }
                if (canSeeValue && count.totalShortageValue > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Mermas (valor)", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                        Text("Q ${count.totalShortageValue.fmtMoney(2)}",
                            color = Color(0xFFB00020), fontWeight = FontWeight.SemiBold)
                    }
                }
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

private fun prevYearMonth(ym: String): String {
    val (y, m) = ym.split("-").let { it[0].toInt() to it[1].toInt() }
    val (ny, nm) = if (m == 1) (y - 1) to 12 else y to (m - 1)
    return "${ny.toString().padStart(4, '0')}-${nm.toString().padStart(2, '0')}"
}

private fun nextYearMonth(ym: String): String {
    val (y, m) = ym.split("-").let { it[0].toInt() to it[1].toInt() }
    val (ny, nm) = if (m == 12) (y + 1) to 1 else y to (m + 1)
    return "${ny.toString().padStart(4, '0')}-${nm.toString().padStart(2, '0')}"
}
