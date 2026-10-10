package com.bakery.erp.presentation.expenses

import com.bakery.erp.util.fmt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Expense
import com.bakery.erp.model.ExpenseCategory
import com.bakery.erp.model.ExpensePaidFrom
import com.bakery.erp.model.Permission
import com.bakery.erp.model.formatPeriodLabel
import com.bakery.erp.model.costCenterReportName
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.branches.BranchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (expenseId: String) -> Unit,
    /**
     * Anotar un gasto abre `CashExpenseFormScreen`, no el formulario largo.
     *
     * Aquél pedía nombre, categoría, sucursal, período y monto para guardar un número, y no
     * sabía lo único que de verdad importa a diario: **de qué caja salió la plata**. Sin eso
     * el gasto no se descuenta al cuadrar y la cajera aparece con un faltante que no es suyo.
     */
    onNavigateToQuickPurchase: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ExpenseViewModel,
    branchViewModel: BranchViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val branchState by branchViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    // El FAB abre la captura SIN caja: lo que se anota desde acá no sale de ninguna
    // gaveta. Ver `Permission.ADD_GENERAL_EXPENSE`.
    val canAddVariable = authState.user?.hasPermission(Permission.ADD_GENERAL_EXPENSE) == true

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) {
            viewModel.loadExpenses(companyId)
            branchViewModel.loadBranches(companyId)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // Derived data
    val filteredExpenses = viewModel.filteredExpenses
    val variableTotal = viewModel.totalAmount

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gastos", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            if (canAddVariable) {
                FloatingActionButton(
                    onClick = onNavigateToQuickPurchase,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Anotar un gasto", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Período ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.goPreviousPeriod() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
                }
                Text(
                    text = formatPeriodLabel(state.selectedPeriod),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { viewModel.goNextPeriod() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
                }
            }

            // ── Filtro de sucursales ─────────────────────────────────────────
            val activeBranches = branchState.branchList.filter { it.active }
            if (activeBranches.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedBranchId == null,
                            onClick = { viewModel.setFilterBranch(null) },
                            label = { Text("Todas") }
                        )
                    }
                    items(activeBranches) { branch ->
                        FilterChip(
                            selected = state.selectedBranchId == branch.id,
                            onClick = { viewModel.setFilterBranch(branch.id) },
                            label = { Text(branch.name, maxLines = 1) }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // ── Content ───────────────────────────────────────────────────────
            if (state.isLoading && state.expenseList.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Los costos fijos vivían acá arriba como una sección más, y eran otra
                    // cosa: un PLAN mensual —lo que se espera pagar de renta y luz— y no
                    // dinero que ya salió. Sumados en el mismo total, el mes se veía gastado
                    // desde el día 1. Ahora tienen módulo propio y esta lista es solo lo que
                    // de verdad se gastó.
                    item(key = "variable_header") {
                        ExpenseSectionHeader(
                            title = formatPeriodLabel(state.selectedPeriod),
                            total = variableTotal,
                            onManage = null
                        )
                    }

                    if (filteredExpenses.isEmpty()) {
                        item(key = "variable_empty") {
                            VariableEmptyRow()
                        }
                    } else {
                        items(filteredExpenses, key = { "v_${it.id}" }) { expense ->
                            ExpenseListCard(
                                expense = expense,
                                onClick = { onNavigateToDetail(expense.id) }
                            )
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }

            // ── Total ──────────────────────────────────────────────────────────
            // Un solo número: lo que se gastó en el período mostrado. El desglose
            // "fijos / variables / total" desapareció con la sección de costos fijos —
            // sumaba un plan mensual con desembolsos reales.
            if (filteredExpenses.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Q ${variableTotal.fmt(2)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// ─── Section header with optional manage button ───────────────────────────────

@Composable
private fun ExpenseSectionHeader(title: String, total: Double, onManage: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (total > 0) {
                Text(
                    text = "Q ${total.fmt(2)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onManage != null) {
                OutlinedButton(
                    onClick = onManage,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Gestionar", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun VariableEmptyRow() {
    Text(
        text = "Sin gastos este mes. Toca + para anotar uno.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

// ─── Expense card (variable) ──────────────────────────────────────────────────

@Composable
private fun ExpenseListCard(expense: Expense, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = expense.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                CategoryBadge(expense.category)
                // De dónde salió la plata va en la fila: es la diferencia entre un gasto
                // que ya explica el faltante de una caja y uno que no toca ninguna gaveta,
                // y sin verla hay que abrir el detalle de cada uno para saberlo.
                val origen = when {
                    expense.cashRegisterName.isNotEmpty() -> "De ${expense.cashRegisterName}"
                    expense.paidFrom == ExpensePaidFrom.GENERAL -> "Caja general / dueño"
                    else -> ""
                }
                Text(
                    // Generales y Reparto se nombran en vez de la sucursal donde se anotaron:
                    // es lo que decide en qué renglón del resultado cae.
                    text = listOf(costCenterReportName(expense.costCenter, expense.branchName), origen)
                        .filter { it.isNotEmpty() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.width(8.dp))
            Text(
                text = "Q ${expense.amount.fmt(2)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// ─── Shared badges ────────────────────────────────────────────────────────────

@Composable
internal fun CategoryBadge(category: ExpenseCategory) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = category.label.take(20),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
