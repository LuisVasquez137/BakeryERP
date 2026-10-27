package com.bakery.erp.presentation.finishedStock

import com.bakery.erp.ui.BrandTopAppBar

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
import com.bakery.erp.model.StoreInventoryRow
import androidx.compose.material.icons.filled.ShoppingBag
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Transform


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
    // Cortar un pastel lo hace cualquiera del mostrador, no solo la encargada.
    val canPortion = user?.let {
        it.hasPermission(Permission.MANAGE_FINISHED_STOCK) || it.hasPermission(Permission.OPERATE_POS) ||
            it.hasPermission(Permission.COUNT_FINISHED_STOCK)
    } == true
    var portioningOpen by rememberSaveable { mutableStateOf(false) }
    // Transformar (molde → tostado) es trabajo de planta: la encargada o quien maneja lotes.
    val canTransform = authState.usesMadeForStock && user?.let {
        it.hasPermission(Permission.MANAGE_FINISHED_STOCK) || it.hasPermission(Permission.MANAGE_PRODUCTION_LOTS)
    } == true
    var transformOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var search by rememberSaveable { mutableStateOf("") }
    // "" = todos, "PAN" = producido en planta, "REVENTA". Solo se ofrece si hay reventa: un
    // filtro que siempre muestra lo mismo es ruido.
    var kindFilter by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(companyId, user?.id) {
        if (companyId.isNotEmpty() && user != null) viewModel.loadBranches(companyId, user)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            portioningOpen = false
            transformOpen = false
            viewModel.clearNotice()
            snackbarHostState.showSnackbar(it)
        }
    }

    val hasResale = state.rows.any { it.isResale }
    val byKind = remember(state.rows, kindFilter) {
        when (kindFilter) {
            "PAN" -> state.rows.filter { !it.isResale }
            "REVENTA" -> state.rows.filter { it.isResale }
            else -> state.rows
        }
    }
    val filtered = remember(byKind, search) {
        if (search.isBlank()) byKind
        else byKind.filter { it.productName.contains(search, ignoreCase = true) }
    }
    val totalValue = remember(byKind) {
        byKind.sumOf { it.currentStock.coerceAtLeast(0.0) * it.avgUnitCost }
    }
    val totalUnits = remember(byKind) {
        byKind.sumOf { it.currentStock }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Inventario de tienda", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // Conteo inicial de lo que no aparece en la lista: el pan de planta solo sale
                    // aquí después de su primer despacho a la sucursal.
                    if (canAdjust && state.selectedBranchId.isNotEmpty()) {
                        IconButton(onClick = { onNavigateToAdjust(state.selectedBranchId, null) }) {
                            Icon(Icons.Default.Add, contentDescription = "Agregar producto")
                        }
                    }
                    if (canTransform && state.transformProducts.isNotEmpty()) {
                        IconButton(onClick = { transformOpen = true }) {
                            Icon(Icons.Default.Transform, contentDescription = "Transformar")
                        }
                    }
                    if (canPortion && state.portionProducts.isNotEmpty()) {
                        IconButton(onClick = { portioningOpen = true }) {
                            Icon(Icons.Default.ContentCut, contentDescription = "Porcionar pastel")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSeaBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
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

            if (hasResale) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("" to "Todos", "PAN" to "Pan propio", "REVENTA" to "Reventa").forEach { (key, label) ->
                        FilterChip(
                            selected = kindFilter == key,
                            onClick = { kindFilter = key },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }

            // KPIs
            if (byKind.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiCard(
                        label = "Productos",
                        value = byKind.size.toString(),
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
                    state.isLoading && state.rows.isEmpty() ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.branches.isEmpty() ->
                        EmptyState(
                            title = "Sin sucursales accesibles",
                            body = "Necesitas tener al menos una sucursal asignada con capacidad de venta o almacén."
                        )
                    byKind.isEmpty() ->
                        EmptyState(
                            title = "Aún no hay producto terminado",
                            body = "El pan entra al confirmar la recepción de un despacho; la reventa, al " +
                                "registrar la compra al proveedor."
                        )
                    filtered.isEmpty() ->
                        EmptyState(title = "Sin resultados", body = "Intenta otro término de búsqueda.")
                    else -> AdaptiveContainer {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.productId }) { row ->
                                StockCard(
                                    row = row,
                                    canSeeValue = canSeeValue,
                                    // El de insumo se ajusta en el insumo: acá escribiría un
                                    // inventario de terminado paralelo que la venta no descuenta.
                                    canAdjust = canAdjust && !row.isIngredientBacked,
                                    onClick = {
                                        if (!row.isIngredientBacked) {
                                            onNavigateToMovements(state.selectedBranchId, row.productId)
                                        }
                                    },
                                    onAdjust = { onNavigateToAdjust(state.selectedBranchId, row.productId) }
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

    if (transformOpen && user != null) {
        val stockById = remember(state.rows) { state.rows.associate { it.productId to it.currentStock } }
        TransformDialog(
            products = state.transformProducts,
            stockOf = { stockById[it] ?: 0.0 },
            isBusy = state.isTransforming,
            onDismiss = { transformOpen = false },
            onConfirm = { product, used, produced, note ->
                viewModel.transform(companyId, product, used, produced, note, user)
            }
        )
    }

    if (portioningOpen && user != null) {
        val stockById = remember(state.rows) { state.rows.associate { it.productId to it.currentStock } }
        PortioningDialog(
            products = state.portionProducts,
            stockOf = { stockById[it] ?: 0.0 },
            isBusy = state.isPortioning,
            onDismiss = { portioningOpen = false },
            onConfirm = { portion, wholes, perWhole ->
                viewModel.cutIntoPortions(companyId, portion, wholes, perWhole, user)
            }
        )
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
    row: StoreInventoryRow,
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
            Icon(
                if (row.isResale) Icons.Default.ShoppingBag else Icons.Default.BakeryDining,
                null, tint = DeepSeaBlue, modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(row.productName, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
                val sub = buildString {
                    append("${row.currentStock.fmt(0)} unidades")
                    if (canSeeValue && row.avgUnitCost > 0) {
                        append(" · costo Q${row.avgUnitCost.fmt(2)} c/u")
                    }
                }
                // Sin existencias en naranja oscuro: es lo que hay que reponer, y es también
                // la señal de que una factura todavía no entró.
                Text(sub, style = MaterialTheme.typography.bodySmall,
                    color = if (row.currentStock <= 0.0) BrandColors.AccentDeep
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    row.isIngredientBacked -> Text(
                        "Del inventario del insumo ${row.linkedIngredientName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    !row.hasStockRecord -> Text(
                        "Sin movimientos todavía en esta sucursal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (canSeeValue && row.currentStock > 0 && row.avgUnitCost > 0) {
                Text(
                    "Q${(row.currentStock * row.avgUnitCost).fmt(2)}",
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
                    colors = AssistChipDefaults.assistChipColors(containerColor = BrandColors.AccentSoft)
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
