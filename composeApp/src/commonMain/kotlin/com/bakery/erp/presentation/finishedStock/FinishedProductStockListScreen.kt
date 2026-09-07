package com.bakery.erp.presentation.finishedStock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.FinishedProductStock
import com.bakery.erp.model.Permission
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.util.fmt
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishedProductStockListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMovements: (branchId: String, productId: String) -> Unit,
    onNavigateToAdjust: (branchId: String, productId: String?) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: FinishedStockViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canSeeValue = user?.hasPermission(Permission.VIEW_DISPATCH_VALUE) == true
    val canAdjust = user?.hasPermission(Permission.MANAGE_FINISHED_STOCK) == true
    val snackbarHostState = remember { SnackbarHostState() }
    var search by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(companyId, user?.id) {
        if (companyId.isNotEmpty() && user != null) viewModel.loadBranches(companyId, user)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val filtered = remember(state.stocks, search) {
        if (search.isBlank()) state.stocks
        else state.stocks.filter { it.productName.contains(search, ignoreCase = true) }
    }
    val totalValue = remember(state.stocks) {
        state.stocks.sumOf { it.currentStock * it.avgUnitCost }
    }
    val totalUnits = remember(state.stocks) {
        state.stocks.sumOf { it.currentStock }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventario de tienda", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSeaBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = WarmIvory
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Selector de sucursal (chips)
            if (state.branches.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.branches.forEach { branch ->
                        FilterChip(
                            selected = branch.id == state.selectedBranchId,
                            onClick = { viewModel.selectBranch(companyId, branch.id) },
                            label = { Text(branch.name, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = if (branch.id == state.selectedBranchId)
                                ({ Icon(Icons.Default.Store, null, modifier = Modifier.size(16.dp)) })
                            else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DeepSeaBlue,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }
                }
            }

            // KPIs
            if (state.stocks.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiCard(
                        label = "Productos",
                        value = state.stocks.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        label = "Unidades",
                        value = totalUnits.fmt(0),
                        modifier = Modifier.weight(1f)
                    )
                    if (canSeeValue) {
                        KpiCard(
                            label = "Valor inv.",
                            value = "Q${totalValue.fmt(2)}",
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }
            }

            // Buscador
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Buscar producto") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.stocks.isEmpty() ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.branches.isEmpty() ->
                        EmptyState(
                            title = "Sin sucursales accesibles",
                            body = "Necesitas tener al menos una sucursal asignada con capacidad de venta o almacén."
                        )
                    state.stocks.isEmpty() ->
                        EmptyState(
                            title = "Aún no hay producto terminado",
                            body = "El inventario se llena automáticamente cuando confirmas la recepción de un despacho."
                        )
                    filtered.isEmpty() ->
                        EmptyState(title = "Sin resultados", body = "Intenta otro término de búsqueda.")
                    else -> AdaptiveContainer {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.id }) { stock ->
                                StockCard(
                                    stock = stock,
                                    canSeeValue = canSeeValue,
                                    canAdjust = canAdjust,
                                    onClick = { onNavigateToMovements(stock.branchId, stock.productId) },
                                    onAdjust = { onNavigateToAdjust(stock.branchId, stock.productId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }
}

@Composable
private fun KpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
        }
    }
}

@Composable
private fun StockCard(
    stock: FinishedProductStock,
    canSeeValue: Boolean,
    canAdjust: Boolean,
    onClick: () -> Unit,
    onAdjust: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BakeryDining, null, tint = DeepSeaBlue, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stock.productName, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
                val sub = buildString {
                    append("${stock.currentStock.fmt(0)} unidades")
                    if (canSeeValue && stock.avgUnitCost > 0) {
                        append(" · costo Q${stock.avgUnitCost.fmt(2)} c/u")
                    }
                }
                Text(sub, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canSeeValue && stock.currentStock > 0 && stock.avgUnitCost > 0) {
                Text(
                    "Q${(stock.currentStock * stock.avgUnitCost).fmt(2)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = DeepSeaBlue
                )
            }
            if (canAdjust) {
                Spacer(Modifier.width(8.dp))
                AssistChip(
                    onClick = onAdjust,
                    label = { Text("Ajustar", style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFFFF3E0))
                )
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Inventory, null, modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
