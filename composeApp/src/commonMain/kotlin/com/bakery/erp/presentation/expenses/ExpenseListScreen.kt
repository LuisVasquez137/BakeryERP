package com.bakery.erp.presentation.expenses

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
import com.bakery.erp.model.FixedBudgetItem
import com.bakery.erp.model.Permission
import com.bakery.erp.model.formatPeriodLabel
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.branches.BranchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (expenseId: String) -> Unit,
    onNavigateToForm: () -> Unit,
    onNavigateToFixedBudget: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ExpenseViewModel,
    branchViewModel: BranchViewModel,
    fixedBudgetViewModel: FixedBudgetViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val branchState by branchViewModel.uiState.collectAsState()
    val fixedState by fixedBudgetViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    val canManageFixed = authState.user?.hasPermission(Permission.MANAGE_FIXED_BUDGET) == true
    val canAddVariable = authState.user?.hasPermission(Permission.ADD_VARIABLE_EXPENSE) == true

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) {
            viewModel.loadExpenses(companyId)
            branchViewModel.loadBranches(companyId)
            fixedBudgetViewModel.loadItems(companyId)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // Derived data
    val filteredExpenses = viewModel.filteredExpenses
    val filteredFixed = fixedState.itemList.filter { item ->
        item.active && (state.selectedBranchId == null || item.branchId == state.selectedBranchId)
    }
    val fixedTotal = filteredFixed.sumOf { it.amount }
    val variableTotal = viewModel.totalAmount
    val grandTotal = fixedTotal + variableTotal

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
                    onClick = onNavigateToForm,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo gasto variable", tint = MaterialTheme.colorScheme.onPrimary)
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
            if (state.isLoading && state.expenseList.isEmpty() && fixedState.itemList.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // ── Sección Costos Fijos ──────────────────────────────────
                    item(key = "fixed_header") {
                        ExpenseSectionHeader(
                            title = "Costos Fijos",
                            total = fixedTotal,
                            onManage = if (canManageFixed) onNavigateToFixedBudget else null
                        )
                    }

                    if (filteredFixed.isEmpty()) {
                        item(key = "fixed_empty") {
                            FixedEmptyRow(canManage = canManageFixed, onManage = onNavigateToFixedBudget)
                        }
                    } else {
                        items(filteredFixed, key = { "f_${it.id}" }) { item ->
                            FixedBudgetRow(item)
                        }
                    }

                    // ── Sección Gastos Variables ──────────────────────────────
                    item(key = "variable_header") {
                        Spacer(Modifier.height(8.dp))
                        ExpenseSectionHeader(
                            title = "Gastos Variables — ${formatPeriodLabel(state.selectedPeriod)}",
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

            // ── Totales ────────────────────────────────────────────────────────
            if (filteredFixed.isNotEmpty() || filteredExpenses.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fijos:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Q ${"%.2f".format(fixedTotal)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Variables:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Q ${"%.2f".format(variableTotal)}", style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Q ${"%.2f".format(grandTotal)}",
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
                    text = "Q ${"%.2f".format(total)}",
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

// ─── Fixed budget compact row ─────────────────────────────────────────────────

@Composable
private fun FixedBudgetRow(item: FixedBudgetItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(item.category.label.take(20), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Text(
            text = "Q ${"%.2f".format(item.amount)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun FixedEmptyRow(canManage: Boolean, onManage: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Sin costos fijos definidos",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        if (canManage) {
            OutlinedButton(
                onClick = onManage,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Definir", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun VariableEmptyRow() {
    Text(
        text = "Sin gastos variables este mes. Toca + para agregar.",
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
                if (expense.branchName.isNotEmpty()) {
                    Text(
                        text = expense.branchName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            Text(
                text = "Q ${"%.2f".format(expense.amount)}",
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
