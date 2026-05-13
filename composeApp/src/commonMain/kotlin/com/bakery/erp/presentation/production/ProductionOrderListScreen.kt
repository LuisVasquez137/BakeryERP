package com.bakery.erp.presentation.production

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.Permission
import com.bakery.erp.model.ProductionOrder
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.ProductionOrderStatus
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private data class ConsolidatedRow(
    val productId: String,
    val productName: String,
    val quantitiesByBranch: Map<String, Double>,
    val total: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionOrderListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (orderId: String) -> Unit,
    onNavigateToForm: () -> Unit,
    onNavigateToLotForm: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canSend = user?.hasPermission(Permission.SEND_PRODUCTION_ORDER) == true
    val canManageLots = user?.hasPermission(Permission.MANAGE_PRODUCTION_LOTS) == true
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && user != null) {
            viewModel.loadOrders(companyId, user)
            viewModel.loadOrderFormData(companyId, user)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val myOrders = remember(state.orders, user) {
        state.orders.filter { it.sellingBranchId.let { id -> user?.canAccessBranch(id) ?: false } }
    }
    val receivedOrders = remember(state.orders, user) {
        state.orders.filter { it.producingBranchId.let { id -> user?.canAccessBranch(id) ?: false } }
    }

    val canSeeProducingTabs = remember(state.producingBranches, user) {
        state.producingBranches.any { user?.canAccessBranch(it.id) == true }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Pedidos de Producción", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF123456),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (canSend && selectedTab == 0) {
                FloatingActionButton(
                    onClick = onNavigateToForm,
                    containerColor = Color(0xFF123456)
                ) { Icon(Icons.Default.Add, "Nuevo pedido", tint = Color.White) }
            }
        },
        containerColor = Color(0xFFFDFBF7)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            if (canSeeProducingTabs) {
                TabRow(selectedTabIndex = selectedTab, containerColor = Color.White) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("Mis pedidos", modifier = Modifier.padding(vertical = 12.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("Recibidos", modifier = Modifier.padding(vertical = 12.dp))
                    }
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                        Text("Consolidado", modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            } else {
                if (selectedTab != 0) selectedTab = 0
            }

            when (selectedTab) {
                0 -> OrdersTab(
                    orders = myOrders,
                    isLoading = state.isLoading,
                    emptyMessage = "Aún no has enviado pedidos",
                    infoText = "Pedidos que has enviado a las plantas productoras. Aquí puedes hacer seguimiento del estado de cada solicitud.",
                    showSellingBranch = false,
                    onOrderClick = onNavigateToDetail
                )
                1 -> OrdersTab(
                    orders = receivedOrders,
                    isLoading = state.isLoading,
                    emptyMessage = "No hay pedidos recibidos",
                    infoText = "Pedidos que han llegado a tu planta productora desde otras sucursales. Usa la pestaña Consolidado para ver la demanda agrupada por fecha.",
                    showSellingBranch = true,
                    onOrderClick = onNavigateToDetail
                )
                2 -> ConsolidatedTab(
                    state = state,
                    user = user,
                    canManageLots = canManageLots,
                    onLoadConsolidated = { branchId, date ->
                        viewModel.loadConsolidatedView(companyId, branchId, date)
                    },
                    onNavigateToLotForm = onNavigateToLotForm
                )
            }
        }
    }
}

@Composable
private fun OrdersTab(
    orders: List<ProductionOrder>,
    isLoading: Boolean,
    emptyMessage: String,
    infoText: String,
    showSellingBranch: Boolean,
    onOrderClick: (String) -> Unit
) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        // Banner informativo
        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    infoText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        if (orders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, Modifier.size(48.dp), tint = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Text(emptyMessage, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(orders) { order ->
                    ProductionOrderCard(
                        order = order,
                        showBranch = showSellingBranch,
                        onClick = { onOrderClick(order.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConsolidatedTab(
    state: ProductionUiState,
    user: com.bakery.erp.model.User?,
    canManageLots: Boolean,
    onLoadConsolidated: (branchId: String, date: Long) -> Unit,
    onNavigateToLotForm: () -> Unit
) {
    val producingBranches = remember(state.producingBranches, user) {
        state.producingBranches.filter {
            it.active && BranchCapability.CAN_PRODUCE in it.capabilities &&
                (user?.canAccessBranch(it.id) ?: false)
        }
    }

    var selectedBranch by remember(producingBranches) {
        mutableStateOf(producingBranches.firstOrNull())
    }
    var selectedDate by remember { mutableStateOf(startOfNextDay()) }
    var branchDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedBranch, selectedDate) {
        val branchId = selectedBranch?.id ?: return@LaunchedEffect
        onLoadConsolidated(branchId, selectedDate)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Selector de planta ────────────────────────────────────────────
        if (producingBranches.size > 1) {
            ExposedDropdownMenuBox(
                expanded = branchDropdownExpanded,
                onExpandedChange = { branchDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedBranch?.name ?: "Seleccionar planta",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Planta productora") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(branchDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = branchDropdownExpanded,
                    onDismissRequest = { branchDropdownExpanded = false }
                ) {
                    producingBranches.forEach { branch ->
                        DropdownMenuItem(
                            text = { Text(branch.name) },
                            onClick = { selectedBranch = branch; branchDropdownExpanded = false }
                        )
                    }
                }
            }
        } else if (producingBranches.size == 1) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Planta productora",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        producingBranches.first().name,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ── Selector de fecha ─────────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedDate -= 86_400_000L }) {
                    Icon(Icons.Default.ChevronLeft, "Día anterior", tint = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Fecha de entrega",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatProductionDate(selectedDate),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                IconButton(onClick = { selectedDate += 86_400_000L }) {
                    Icon(Icons.Default.ChevronRight, "Día siguiente", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ── Tabla consolidada ─────────────────────────────────────────────
        if (state.isConsolidatedLoading) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.consolidatedOrders.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Sin pedidos para esta fecha",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val ordersWithLines = state.consolidatedOrders.map { order ->
                order to (state.consolidatedLinesMap[order.id] ?: emptyList())
            }
            val branches = ordersWithLines.map { (order, _) ->
                order.sellingBranchId to order.sellingBranchName
            }.distinct().sortedBy { it.second }

            val rows = remember(ordersWithLines) {
                ordersWithLines
                    .flatMap { (order, lines) ->
                        lines.map { line ->
                            Triple(
                                line.productId,
                                line.productName,
                                order.sellingBranchId to line.requestedQuantity
                            )
                        }
                    }
                    .groupBy { it.first }
                    .map { (productId, entries) ->
                        val productName = entries.first().second
                        val byBranch = entries.groupBy { it.third.first }
                            .mapValues { (_, v) -> v.sumOf { it.third.second } }
                        ConsolidatedRow(productId, productName, byBranch, byBranch.values.sum())
                    }
                    .sortedBy { it.productName }
            }

            // Card principal: vertical en móvil, tabla en tablet/PC
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 600.dp) {
                    ConsolidatedTable(rows = rows, branches = branches)
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "PEDIDOS CONSOLIDADOS",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(
                                        "${rows.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            rows.forEachIndexed { index, row ->
                                ConsolidatedProductRow(row = row, branches = branches)
                                if (index < rows.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Total general
            val grandTotal = rows.sumOf { it.total }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "TOTAL GENERAL",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "${state.consolidatedOrders.size} pedido(s) · ${branches.size} sucursal(es)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        formatQty(grandTotal) + " uds",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Botón crear lote
            if (canManageLots) {
                Button(
                    onClick = onNavigateToLotForm,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123456))
                ) {
                    Text("Crear lote de producción →", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ConsolidatedProductRow(
    row: ConsolidatedRow,
    branches: List<Pair<String, String>>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Nombre del producto + total en la misma línea
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                row.productName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    "${formatQty(row.total)} uds total",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Una fila por sucursal que pidió este producto
        branches.forEach { (branchId, branchName) ->
            val qty = row.quantitiesByBranch[branchId] ?: 0.0
            if (qty > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        branchName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatQty(qty) + " uds",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsolidatedTable(
    rows: List<ConsolidatedRow>,
    branches: List<Pair<String, String>>
) {
    val productColWidth = 160.dp
    val qtyColWidth = 80.dp

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PEDIDOS CONSOLIDADOS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "${rows.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    // Header
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Producto",
                            modifier = Modifier.width(productColWidth),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        branches.forEach { (_, branchName) ->
                            Text(
                                branchName,
                                modifier = Modifier.width(qtyColWidth),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End
                            )
                        }
                        Text(
                            "Total",
                            modifier = Modifier.width(qtyColWidth),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Data rows
                    rows.forEachIndexed { index, row ->
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                row.productName,
                                modifier = Modifier.width(productColWidth),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            branches.forEach { (branchId, _) ->
                                val qty = row.quantitiesByBranch[branchId] ?: 0.0
                                Text(
                                    if (qty > 0.0) formatQty(qty) else "—",
                                    modifier = Modifier.width(qtyColWidth),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.End,
                                    color = if (qty > 0.0) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(
                                formatQty(row.total),
                                modifier = Modifier.width(qtyColWidth),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End
                            )
                        }
                        if (index < rows.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }

                    // Totals row
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.5.dp)
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "TOTAL",
                            modifier = Modifier.width(productColWidth),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        branches.forEach { (branchId, _) ->
                            val branchTotal = rows.sumOf { it.quantitiesByBranch[branchId] ?: 0.0 }
                            Text(
                                formatQty(branchTotal),
                                modifier = Modifier.width(qtyColWidth),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End
                            )
                        }
                        Text(
                            formatQty(rows.sumOf { it.total }),
                            modifier = Modifier.width(qtyColWidth),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductionOrderCard(
    order: ProductionOrder,
    showBranch: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when (order.status) {
        ProductionOrderStatus.PENDING   -> Color(0xFFF57C00)
        ProductionOrderStatus.CONFIRMED -> Color(0xFF2E7D32)
        ProductionOrderStatus.CANCELLED -> Color.Gray
        else                            -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (showBranch) order.sellingBranchName else order.producingBranchName,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Para: ${formatProductionDate(order.requestedDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${order.lineCount} producto(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Text(
                    ProductionOrderStatus.label(order.status),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatQty(qty: Double): String =
    if (qty == qty.toLong().toDouble()) qty.toLong().toString() else "%.1f".format(qty)

internal fun formatProductionDate(millis: Long): String {
    if (millis == 0L) return "—"
    return runCatching {
        val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        "${ldt.dayOfMonth.toString().padStart(2,'0')}/${ldt.monthNumber.toString().padStart(2,'0')}/${ldt.year}"
    }.getOrElse { "—" }
}
