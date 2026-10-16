package com.bakery.erp.presentation.customerOrders

import com.bakery.erp.ui.BrandTopAppBar

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TabRow
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CustomerOrder
import com.bakery.erp.model.CustomerOrderStatus
import com.bakery.erp.model.CustomerOrderType
import com.bakery.erp.model.Permission
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone

private val MutedText = Color(0xFF6E6E6E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerOrderListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToForm: (orderId: String?) -> Unit,
    onNavigateToDetail: (orderId: String) -> Unit,
    onNavigateToCustomers: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CustomerOrderViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val user = authState.user
    val canCreateAny = user != null && (
        user.hasPermission(Permission.CREATE_RESELLER_ORDER) ||
            user.hasPermission(Permission.CREATE_RETAIL_SPECIAL_ORDER) ||
            user.hasPermission(Permission.CREATE_CAKE_ORDER)
        )
    val canManageCustomers = user?.hasPermission(Permission.MANAGE_EXTERNAL_CUSTOMERS) == true
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) {
            viewModel.observeOrders(companyId)
            viewModel.loadOrdersOnce(companyId)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var typeFilter by rememberSaveable { mutableStateOf("ALL") }

    val tabs = listOf(
        "Activos" to listOf(
            CustomerOrderStatus.PENDING,
            CustomerOrderStatus.PARTIALLY_CONFIRMED,
            CustomerOrderStatus.CONFIRMED,
            CustomerOrderStatus.IN_PRODUCTION,
            CustomerOrderStatus.READY
        ),
        "Entregados" to listOf(CustomerOrderStatus.DELIVERED),
        "Cancelados" to listOf(CustomerOrderStatus.CANCELLED)
    )
    val tabStatuses = tabs[tabIndex].second
    val filtered = state.orders.filter {
        it.status in tabStatuses && (typeFilter == "ALL" || it.type == typeFilter)
    }

    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            BrandTopAppBar(
                title = { Text("Pedidos de clientes", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (canManageCustomers) {
                        IconButton(onClick = onNavigateToCustomers) {
                            Icon(Icons.Default.People, contentDescription = "Clientes externos")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            // El FAB se oculta cuando el filtro es RESELLER porque los pedidos
            // de revendedor solo pueden ser creados por el propio revendedor desde su app.
            if (canCreateAny && typeFilter != CustomerOrderType.RESELLER) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToForm(null) },
                    containerColor = BrandColors.Primary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nuevo pedido") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = tabIndex,
                    containerColor = WarmIvory,
                    contentColor = PrimaryBlue
                ) {
                    tabs.forEachIndexed { idx, (label, _) ->
                        Tab(
                            selected = tabIndex == idx,
                            onClick = { tabIndex = idx },
                            text = { Text(label, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
                TypeFilterChips(typeFilter) { typeFilter = it }
                when {
                    state.isLoading && state.orders.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = PrimaryBlue)
                    }
                    filtered.isEmpty() -> EmptyOrderState()
                    else -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.id }) { order ->
                            OrderCard(order) { onNavigateToDetail(order.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeFilterChips(current: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val opts = listOf(
            "ALL" to "Todos",
            CustomerOrderType.RESELLER to "Revendedores",
            CustomerOrderType.RETAIL_SPECIAL to "Encargos",
            CustomerOrderType.CAKE to "Pasteles"
        )
        opts.forEach { (value, label) ->
            FilterChip(
                selected = current == value,
                onClick = { onChange(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryBlue,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun EmptyOrderState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Inbox, contentDescription = null,
            tint = PrimaryBlue.copy(alpha = 0.4f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.size(12.dp))
        Text(
            "Sin pedidos en este estado",
            style = MaterialTheme.typography.titleMedium,
            color = PrimaryBlue,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OrderCard(order: CustomerOrder, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TypeBadge(order.type)
                Spacer(Modifier.width(8.dp))
                Text(
                    order.customerName.ifEmpty { "Sin nombre" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                StatusBadge(order.status)
            }
            Spacer(Modifier.size(6.dp))
            Text(
                "Entrega: ${formatDate(order.deliveryDate)}${order.deliveryTime.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
            if (order.producingBranchName.isNotEmpty()) {
                Text(
                    "Planta: ${order.producingBranchName}${order.shiftName.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
            if (order.createdByUserName.isNotBlank()) {
                Text(
                    "Tomó: ${order.createdByUserName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
            Spacer(Modifier.size(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${order.lineCount} producto(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText
                )
                if (order.totalAmount > 0.0) {
                    val balanceTxt = if (order.balanceAmount > 0.0)
                        " · saldo Q ${formatMoney(order.balanceAmount)}" else ""
                    Text(
                        "Total Q ${formatMoney(order.totalAmount)}$balanceTxt",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.AccentDeep,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(type: String) {
    val color = when (type) {
        CustomerOrderType.RESELLER -> BrandColors.AccentDeep
        CustomerOrderType.CAKE -> Color(0xFFAD1457)
        else -> PrimaryBlue
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            CustomerOrderType.label(type),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusBadge(status: String) {
    // "Pendiente" iba naranja #E65100 sobre #FFE0B2: 3.0:1, por debajo de lo legible.
    val tone = when (status) {
        CustomerOrderStatus.PENDING,
        CustomerOrderStatus.PARTIALLY_CONFIRMED -> ChipTone.ATTENTION
        CustomerOrderStatus.CONFIRMED,
        CustomerOrderStatus.READY -> ChipTone.INFO
        CustomerOrderStatus.IN_PRODUCTION -> ChipTone.PROGRESS
        CustomerOrderStatus.DELIVERED -> ChipTone.SUCCESS
        else -> ChipTone.NEUTRAL
    }
    BrandChip(CustomerOrderStatus.label(status), tone)
}

internal fun formatDate(millis: Long): String {
    if (millis <= 0L) return "—"
    val dt: LocalDateTime = Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val m = dt.monthNumber.toString().padStart(2, '0')
    val d = dt.dayOfMonth.toString().padStart(2, '0')
    return "$d/$m/${dt.year}"
}

internal fun formatMoney(value: Double): String {
    val rounded = (value * 100).toLong()
    val whole = rounded / 100
    val cents = (rounded % 100).let { if (it < 10) "0$it" else "$it" }
    return "$whole.$cents"
}
