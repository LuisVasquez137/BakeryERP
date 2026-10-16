package com.bakery.erp.presentation.production

import com.bakery.erp.ui.BrandTopAppBar

import com.bakery.erp.util.fmt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapVert
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
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.ProductionOrder
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.ProductionOrderStatus
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.ui.LoadingOverlay
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone

private data class ConsolidatedRow(
    val productId: String,
    val productName: String,
    val quantitiesByBranch: Map<String, Double>,
    val total: Double,
    /**
     * Masa declarada en la línea, solo para los pedidos especiales: su
     * `productId` es sintético y no aparece en el mapa producto→masa, así que
     * sin esto caerían en "Otros productos" — justo lejos de la masa con la que
     * se van a hornear, que es lo que la encargada necesita ver junto.
     */
    val specialRecipeId: String = "",
    val specialRecipeName: String = "",
    val isSpecial: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionOrderListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (orderId: String) -> Unit,
    onNavigateToForm: () -> Unit,
    onNavigateToEditOrder: (orderId: String) -> Unit = {},
    onNavigateToLotForm: (branchId: String?, shiftId: String?, date: Long?) -> Unit,
    onNavigateToLotDetail: (lotId: String) -> Unit = {},
    onNavigateToShiftSplit: (branchId: String, date: Long) -> Unit = { _, _ -> },
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel,
    productViewModel: ProductViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val productState by productViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canSend = user?.hasPermission(Permission.SEND_PRODUCTION_ORDER) == true
    val canManageLots = user?.hasPermission(Permission.MANAGE_PRODUCTION_LOTS) == true
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Mes seleccionado para los tabs "Mis pedidos" y "Recibidos". Default = mes actual.
    val now = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
    var selectedYear by rememberSaveable { mutableIntStateOf(now.year) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(now.monthNumber) }
    var showMonthPicker by remember { mutableStateOf(false) }

    LaunchedEffect(companyId, selectedYear, selectedMonth) {
        if (companyId.isNotEmpty() && user != null) {
            viewModel.observeOrdersForMonth(companyId, user, selectedYear, selectedMonth)
        }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopObservingOrders() }
    }
    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && user != null) {
            viewModel.loadOrderFormData(companyId, user)
        }
    }
    // El catálogo solo se necesita en el tab Consolidado (para configurar el
    // orden de horneado); se carga al entrar ahí, no al abrir la pantalla.
    LaunchedEffect(companyId, selectedTab) {
        if (companyId.isNotEmpty() && selectedTab == 2 && productState.productList.isEmpty()) {
            productViewModel.loadProducts(companyId)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            currentYear = selectedYear,
            currentMonth = selectedMonth,
            onDismiss = { showMonthPicker = false },
            onSelect = { y, m ->
                selectedYear = y
                selectedMonth = m
                showMonthPicker = false
            }
        )
    }

    val myOrders = remember(state.orders, user) {
        state.orders.filter { it.sellingBranchId.let { id -> user?.canAccessBranch(id) ?: false } }
    }
    val receivedOrdersAll = remember(state.orders, user) {
        state.orders.filter { it.producingBranchId.let { id -> user?.canAccessBranch(id) ?: false } }
    }

    val canSeeProducingTabs = remember(state.producingBranches, user) {
        state.producingBranches.any { user?.canAccessBranch(it.id) == true }
    }

    // Conjunto de turnos disponibles en los pedidos recibidos (para los chips de filtro).
    val receivedShiftOptions = remember(receivedOrdersAll) {
        receivedOrdersAll
            .filter { it.shiftId.isNotBlank() }
            .map { it.shiftId to it.shiftName }
            .distinct()
            .sortedBy { it.second }
    }
    // "" = todos los turnos. Si solo hay un turno, no se muestra el filtro pero el valor es ese.
    var receivedShiftFilter by rememberSaveable { mutableStateOf("") }
    val receivedOrders = remember(receivedOrdersAll, receivedShiftFilter) {
        if (receivedShiftFilter.isEmpty()) receivedOrdersAll
        else receivedOrdersAll.filter { it.shiftId == receivedShiftFilter }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            BrandTopAppBar(
                title = { Text("Pedidos de Producción", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (canSend && selectedTab == 0) {
                FloatingActionButton(
                    onClick = onNavigateToForm,
                    containerColor = BrandColors.Primary
                ) { Icon(Icons.Default.Add, "Nuevo pedido", tint = Color.White) }
            }
        },
        containerColor = BrandColors.Ivory
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val isWide = maxWidth >= 1000.dp
            // En pantallas anchas el detalle aparece en panel derecho; en pequeñas se navega
            // a la screen completa.
            var sidePanelOrderId by rememberSaveable { mutableStateOf<String?>(null) }

            val onOrderClick: (String) -> Unit = { id ->
                if (isWide) sidePanelOrderId = id else onNavigateToDetail(id)
            }

            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                            lotStatusByOrderId = state.lotStatusByOrderId,
                            isLoading = state.isLoading,
                            emptyMessage = "Aún no has enviado pedidos en este mes",
                            infoText = "Pedidos que has enviado a las plantas productoras. Aquí puedes hacer seguimiento del estado de cada solicitud.",
                            onOrderClick = onOrderClick,
                            selectedOrderId = sidePanelOrderId,
                            selectedYear = selectedYear,
                            selectedMonth = selectedMonth,
                            onPrevMonth = {
                                if (selectedMonth == 1) { selectedYear -= 1; selectedMonth = 12 }
                                else selectedMonth -= 1
                            },
                            onNextMonth = {
                                if (selectedMonth == 12) { selectedYear += 1; selectedMonth = 1 }
                                else selectedMonth += 1
                            },
                            onPickMonth = { showMonthPicker = true }
                        )
                        1 -> OrdersTab(
                            orders = receivedOrders,
                            lotStatusByOrderId = state.lotStatusByOrderId,
                            isLoading = state.isLoading,
                            emptyMessage = "No hay pedidos recibidos en este mes",
                            infoText = "Pedidos que han llegado a tu planta productora desde otras sucursales. Usa la pestaña Consolidado para ver la demanda agrupada por fecha.",
                            onOrderClick = onOrderClick,
                            selectedOrderId = sidePanelOrderId,
                            selectedYear = selectedYear,
                            selectedMonth = selectedMonth,
                            onPrevMonth = {
                                if (selectedMonth == 1) { selectedYear -= 1; selectedMonth = 12 }
                                else selectedMonth -= 1
                            },
                            onNextMonth = {
                                if (selectedMonth == 12) { selectedYear += 1; selectedMonth = 1 }
                                else selectedMonth += 1
                            },
                            onPickMonth = { showMonthPicker = true },
                            shiftFilterOptions = receivedShiftOptions,
                            selectedShiftFilter = receivedShiftFilter,
                            onShiftFilterChange = { receivedShiftFilter = it }
                        )
                        2 -> ConsolidatedTab(
                            state = state,
                            user = user,
                            canManageLots = canManageLots,
                            products = productState.productList,
                            onSaveSortOrder = { branchId, ids ->
                                viewModel.saveProductSortOrder(companyId, branchId, ids)
                            },
                            onLoadShifts = { branchId ->
                                viewModel.loadShiftsForProducingBranch(companyId, branchId)
                            },
                            onLoadConsolidated = { branchId, date, shiftId ->
                                viewModel.loadConsolidatedView(companyId, branchId, date, shiftId)
                            },
                            onNavigateToLotForm = onNavigateToLotForm,
                            onNavigateToLotDetail = onNavigateToLotDetail,
                            onNavigateToShiftSplit = onNavigateToShiftSplit
                        )
                    }
                }
                if (isWide && sidePanelOrderId != null && selectedTab != 2) {
                    VerticalDivider(color = Color(0xFFE0E0E0))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        ProductionOrderDetailContent(
                            orderId = sidePanelOrderId!!,
                            authViewModel = authViewModel,
                            viewModel = viewModel,
                            onNavigateToEdit = { id ->
                                sidePanelOrderId = null
                                onNavigateToEditOrder(id)
                            },
                            onAfterCancel = { sidePanelOrderId = null },
                            snackbarHostState = snackbarHostState,
                            modifier = Modifier.fillMaxSize(),
                            onClosePanel = { sidePanelOrderId = null }
                        )
                    }
                }
            }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }
}

@Composable
private fun OrdersTab(
    orders: List<ProductionOrder>,
    lotStatusByOrderId: Map<String, String>,
    isLoading: Boolean,
    emptyMessage: String,
    infoText: String,
    onOrderClick: (String) -> Unit,
    selectedOrderId: String? = null,
    selectedYear: Int,
    selectedMonth: Int,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPickMonth: () -> Unit,
    /** Pares (shiftId, shiftName) presentes en el listado actual. */
    shiftFilterOptions: List<Pair<String, String>> = emptyList(),
    /** "" = todos los turnos. */
    selectedShiftFilter: String = "",
    onShiftFilterChange: (String) -> Unit = {}
) {
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

        // Header de mes
        MonthHeader(
            year = selectedYear,
            month = selectedMonth,
            onPrev = onPrevMonth,
            onNext = onNextMonth,
            onTap = onPickMonth
        )

        // Filtros de turno (Recibidos): solo si hay más de un turno en los pedidos.
        if (shiftFilterOptions.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedShiftFilter.isEmpty(),
                    onClick = { onShiftFilterChange("") },
                    label = { Text("Todos los turnos") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandColors.Primary,
                        selectedLabelColor = Color.White
                    )
                )
                shiftFilterOptions.forEach { (id, name) ->
                    FilterChip(
                        selected = selectedShiftFilter == id,
                        onClick = { onShiftFilterChange(id) },
                        label = { Text(name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandColors.Primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (orders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, Modifier.size(48.dp), tint = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Text(emptyMessage, color = Color.Gray)
                }
            }
            return
        }

        // Layout responsive: tabla en ≥720dp, cards en pantallas pequeñas.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth >= 720.dp) {
                ProductionOrderTable(
                    orders = orders,
                    lotStatusByOrderId = lotStatusByOrderId,
                    selectedOrderId = selectedOrderId,
                    onOrderClick = onOrderClick
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(orders) { order ->
                        ProductionOrderCard(
                            order = order,
                            lotStatus = lotStatusByOrderId[order.id],
                            isSelected = order.id == selectedOrderId,
                            onClick = { onOrderClick(order.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    year: Int,
    month: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTap: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Default.ChevronLeft, "Mes anterior", tint = BrandColors.Primary)
            }
            Row(
                modifier = Modifier.weight(1f).clickable(onClick = onTap),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = BrandColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${monthName(month)} $year",
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.ChevronRight, "Mes siguiente", tint = BrandColors.Primary)
            }
        }
    }
}

@Composable
private fun MonthPickerDialog(
    currentYear: Int,
    currentMonth: Int,
    onDismiss: () -> Unit,
    onSelect: (year: Int, month: Int) -> Unit
) {
    var year by remember { mutableIntStateOf(currentYear) }
    var month by remember { mutableIntStateOf(currentMonth) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar mes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Stepper de año.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { year -= 1 }) {
                        Icon(Icons.Default.ChevronLeft, "Año anterior", tint = BrandColors.Primary)
                    }
                    Text(
                        "$year",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = BrandColors.Primary,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    IconButton(onClick = { year += 1 }) {
                        Icon(Icons.Default.ChevronRight, "Año siguiente", tint = BrandColors.Primary)
                    }
                }
                // Grid 4x3 de meses.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (rowStart in listOf(1, 5, 9)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (m in rowStart..(rowStart + 3)) {
                                val selected = m == month
                                Surface(
                                    modifier = Modifier.weight(1f).clickable { month = m },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) BrandColors.Primary else Color.Transparent,
                                    border = if (selected) null
                                             else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        monthShort(m),
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        textAlign = TextAlign.Center,
                                        color = if (selected) Color.White else BrandColors.Primary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(year, month) }) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ProductionOrderTable(
    orders: List<ProductionOrder>,
    lotStatusByOrderId: Map<String, String>,
    selectedOrderId: String? = null,
    onOrderClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableHeaderCell("Estado", Modifier.weight(0.9f))
                TableHeaderCell("Sucursal solicitante", Modifier.weight(1.2f))
                TableHeaderCell("Planta productora", Modifier.weight(1.2f))
                TableHeaderCell("Turno", Modifier.weight(0.9f))
                TableHeaderCell("Para el día", Modifier.weight(1f))
                TableHeaderCell("Productos", Modifier.weight(0.7f), TextAlign.End)
                TableHeaderCell("Creado por", Modifier.weight(1.2f))
                TableHeaderCell("Hora envío", Modifier.weight(0.9f), TextAlign.End)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(orders) { order ->
                    val isSelected = order.id == selectedOrderId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) Color(0xFFE3F2FD) else Color.Transparent
                            )
                            .clickable { onOrderClick(order.id) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(0.9f)) {
                            StatusChip(order.status, lotStatusByOrderId[order.id], order.isLateOrder)
                        }
                        // Origen y destino con chips de colores distintos: de un vistazo
                        // se lee "quién pide" (azul) y "quién produce" (dorado).
                        Box(modifier = Modifier.weight(1.2f).padding(horizontal = 4.dp)) {
                            BranchChip(order.sellingBranchName, BranchChipRole.REQUESTING)
                        }
                        Box(modifier = Modifier.weight(1.2f).padding(horizontal = 4.dp)) {
                            BranchChip(order.producingBranchName, BranchChipRole.PRODUCING)
                        }
                        TableCell(
                            order.shiftName.ifBlank { "—" },
                            Modifier.weight(0.9f),
                            color = if (order.shiftName.isBlank())
                                MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.onSurface
                        )
                        TableCell(formatProductionDate(order.requestedDate), Modifier.weight(1f))
                        TableCell(
                            "${order.lineCount}",
                            Modifier.weight(0.7f),
                            textAlign = TextAlign.End,
                            fontWeight = FontWeight.SemiBold
                        )
                        TableCell(order.createdByUserName.ifBlank { "—" }, Modifier.weight(1.2f))
                        TableCell(
                            formatHourMinute(order.createdAt),
                            Modifier.weight(0.9f),
                            textAlign = TextAlign.End
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun TableHeaderCell(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = BrandColors.Primary,
        textAlign = textAlign
    )
}

@Composable
private fun TableCell(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = fontWeight,
        color = color,
        textAlign = textAlign
    )
}

/**
 * Etiqueta y colores del estado efectivo de un pedido: combina su propio status con el
 * status del lote que lo contiene (si existe). Permite mostrar "Lote abierto" o
 * "En producción" en vez de "Pendiente" cuando el flujo avanzó del lado de la planta.
 *
 * Cuando un pedido es "tardío" ([isLateOrder] = true) y aún no está en un lote, el chip
 * cambia a "Pedido tardío · pend. aprobar" para que la encargada de la planta sepa que
 * tiene que decidir si lo incorpora al lote en curso.
 */
internal fun effectiveOrderDisplay(
    orderStatus: String,
    lotStatus: String?,
    isLateOrder: Boolean = false
): Pair<String, ChipTone> {
    if (orderStatus == ProductionOrderStatus.CANCELLED) {
        return "Cancelado" to ChipTone.NEUTRAL
    }
    if (orderStatus == ProductionOrderStatus.CONFIRMED) {
        return "Confirmado" to ChipTone.SUCCESS
    }
    // PENDING. Si es tardío y aún no fue incorporado a un lote, marcar explícitamente.
    if (isLateOrder && lotStatus == null) {
        return "Pedido tardío · pend. aprobar" to ChipTone.DANGER
    }
    return when (lotStatus) {
        ProductionLotStatus.OPEN -> "Lote abierto" to ChipTone.INFO
        ProductionLotStatus.IN_PROGRESS -> "En producción" to ChipTone.PROGRESS
        ProductionLotStatus.COMPLETED -> "Producción terminada" to ChipTone.SUCCESS
        else -> "Pendiente" to ChipTone.ATTENTION
    }
}

/**
 * Rol de la sucursal dentro del pedido. Cada rol tiene su propio color de chip
 * para que en una tabla con muchas filas el ojo distinga origen de destino sin
 * leer el encabezado de la columna.
 */
private enum class BranchChipRole { REQUESTING, PRODUCING }

@Composable
private fun BranchChip(
    name: String,
    role: BranchChipRole,
    modifier: Modifier = Modifier
) {
    // Azul del tema para quien pide; naranja de marca para quien produce. El naranja
    // lleva tinta azul marino (OnAccent) y no blanco: sobre #FF9800 el blanco da 2.2:1.
    val (bg, fg) = when (role) {
        BranchChipRole.REQUESTING -> BrandColors.Primary to Color.White
        BranchChipRole.PRODUCING -> BrandColors.Accent to BrandColors.OnAccent
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(6.dp), color = bg) {
        Text(
            name.ifBlank { "—" },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}

@Composable
private fun StatusChip(status: String, lotStatus: String? = null, isLateOrder: Boolean = false) {
    val (label, tone) = effectiveOrderDisplay(status, lotStatus, isLateOrder)
    BrandChip(label, tone)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConsolidatedTab(
    state: ProductionUiState,
    user: com.bakery.erp.model.User?,
    canManageLots: Boolean,
    /** Catálogo completo, para poder ordenar productos que hoy no se pidieron. */
    products: List<Product>,
    onSaveSortOrder: (branchId: String, productIds: List<String>) -> Unit,
    onLoadShifts: (branchId: String) -> Unit,
    onLoadConsolidated: (branchId: String, date: Long, shiftId: String) -> Unit,
    onNavigateToLotForm: (branchId: String?, shiftId: String?, date: Long?) -> Unit,
    onNavigateToLotDetail: (lotId: String) -> Unit,
    onNavigateToShiftSplit: (branchId: String, date: Long) -> Unit
) {
    val producingBranches = remember(state.producingBranches, user) {
        state.producingBranches.filter {
            it.active && BranchCapability.CAN_PRODUCE in it.capabilities &&
                (user?.canAccessBranch(it.id) ?: false)
        }
    }

    // Se persiste el id (no la instancia): al guardar el orden de productos la
    // lista de sucursales se recrea y con la instancia se perdía la selección.
    var selectedBranchId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedBranch = remember(producingBranches, selectedBranchId) {
        producingBranches.find { it.id == selectedBranchId } ?: producingBranches.firstOrNull()
    }
    var selectedDate by remember { mutableStateOf(startOfNextDay()) }
    var branchDropdownExpanded by remember { mutableStateOf(false) }
    var selectedShiftId by rememberSaveable { mutableStateOf("") }
    var showSortDialog by remember { mutableStateOf(false) }

    // Orden configurado por la planta (secuencia de horneado). Los productos que
    // no estén en la lista caen al final, alfabéticamente.
    val sortIndex = remember(selectedBranch?.productSortOrder) {
        selectedBranch?.productSortOrder.orEmpty()
            .withIndex().associate { (i, id) -> id to i }
    }
    // El orden se guarda por producto; acá se traduce a orden de masas.
    val recipeOrderIndex = remember(selectedBranch?.productSortOrder, state.recipeRefByProductId) {
        recipeOrderIndexFrom(selectedBranch?.productSortOrder.orEmpty()) { productId ->
            state.recipeRefByProductId[productId]?.id
        }
    }

    // Al cambiar de planta: cargar sus turnos.
    LaunchedEffect(selectedBranch?.id) {
        val branchId = selectedBranch?.id ?: return@LaunchedEffect
        onLoadShifts(branchId)
    }

    // Resolver el turno por defecto al cargar los turnos: el primero activo. Si la
    // planta no tiene turnos, o tiene exactamente 2 (reparto automático — ver
    // ProductionShiftSplitScreen), dejamos shiftId = "" (pedidos sin turno).
    LaunchedEffect(state.shiftsForSelectedProducingBranch) {
        val shifts = state.shiftsForSelectedProducingBranch
        if (shifts.isEmpty() || shifts.size == 2) {
            selectedShiftId = ""
        } else if (shifts.none { it.id == selectedShiftId }) {
            selectedShiftId = shifts.first().id
        }
    }

    // Se observa el id (no la instancia): guardar el orden de productos crea un
    // Branch nuevo y recargaría el consolidado sin necesidad.
    LaunchedEffect(selectedBranch?.id, selectedDate, selectedShiftId) {
        val branchId = selectedBranch?.id ?: return@LaunchedEffect
        onLoadConsolidated(branchId, selectedDate, selectedShiftId)
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
                            onClick = { selectedBranchId = branch.id; branchDropdownExpanded = false }
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

        // ── Selector de turno ─────────────────────────────────────────────
        val shifts = state.shiftsForSelectedProducingBranch
        if (shifts.size == 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEAF3F8), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${selectedBranch?.name.orEmpty()} reparte automático entre " +
                        shifts.joinToString(" y ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.Primary
                )
            }
        } else if (shifts.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Turno de producción",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shifts.forEach { shift ->
                        val selected = shift.id == selectedShiftId
                        FilterChip(
                            selected = selected,
                            onClick = { selectedShiftId = shift.id },
                            label = { Text(shift.name, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandColors.Primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        } else if (shifts.size == 1) {
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
                        "Turno",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        shifts.first().name,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.Primary
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

        // ── Orden de horneado ─────────────────────────────────────────────
        // El orden en que la planta quiere ver los productos (no alfabético,
        // sino la secuencia real del horno). Se guarda en la sucursal.
        if (canManageLots && selectedBranch != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedBranch!!.productSortOrder.isEmpty()) {
                    Text(
                        "Orden alfabético",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Orden personalizado de ${selectedBranch!!.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.AccentDeep,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(onClick = { showSortDialog = true }) {
                    Icon(
                        Icons.Default.SwapVert,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = BrandColors.Primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Ordenar productos", color = BrandColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (showSortDialog && selectedBranch != null) {
            ProductSortOrderDialog(
                title = "Orden del consolidado · ${selectedBranch!!.name}",
                description = "El consolidado se muestra agrupado por masa. Al subir un " +
                    "producto sube toda su masa, y dentro de ella los productos quedan en " +
                    "el orden que definas acá. Se guarda en la sucursal, así que lo verán " +
                    "igual todos sus dispositivos. La orden de producción del panadero " +
                    "tiene su propio orden, aparte de este.",
                products = products,
                assignedProductIds = selectedBranch!!.assignedProductIds,
                currentOrder = selectedBranch!!.productSortOrder,
                onDismiss = { showSortDialog = false },
                onSave = { ids ->
                    onSaveSortOrder(selectedBranch!!.id, ids)
                    showSortDialog = false
                }
            )
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

            val rows = remember(ordersWithLines, sortIndex) {
                ordersWithLines
                    .flatMap { (order, lines) ->
                        lines.map { line -> line to order.sellingBranchId }
                    }
                    .groupBy { it.first.productId }
                    .map { (productId, entries) ->
                        val sample = entries.first().first
                        val byBranch = entries.groupBy { it.second }
                            .mapValues { (_, v) -> v.sumOf { it.first.requestedQuantity } }
                        ConsolidatedRow(
                            productId = productId,
                            productName = sample.productName,
                            quantitiesByBranch = byBranch,
                            total = byBranch.values.sum(),
                            specialRecipeId = sample.specialRecipeId,
                            specialRecipeName = sample.specialRecipeName,
                            isSpecial = sample.isSpecial
                        )
                    }
            }

            // Agrupado por masa, igual que el formulario de pedido. El orden
            // configurado sigue mandando, pero ahora manda sobre las masas: subir
            // un producto sube toda su masa (ver recipeOrderIndexFrom).
            val groups = remember(rows, sortIndex, recipeOrderIndex, state.recipeRefByProductId) {
                groupByMasa(
                    items = rows,
                    recipeIdOf = { row ->
                        if (row.isSpecial) row.specialRecipeId.takeIf { it.isNotEmpty() }
                        else state.recipeRefByProductId[row.productId]?.id
                    },
                    recipeNameOf = { row ->
                        if (row.isSpecial) row.specialRecipeName.takeIf { it.isNotBlank() }
                        else state.recipeRefByProductId[row.productId]?.name
                    },
                    nameOf = { it.productName },
                    groupOrderIndex = { recipeOrderIndex[it] ?: Int.MAX_VALUE },
                    itemOrderIndex = { sortIndex[it.productId] ?: Int.MAX_VALUE }
                )
            }

            // Card principal: vertical en móvil, tabla en tablet/PC
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 600.dp) {
                    ConsolidatedTable(groups = groups, branches = branches)
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

                            groups.forEach { group ->
                                MasaSectionLabel(
                                    title = group.title,
                                    units = group.items.sumOf { it.total }
                                )
                                group.items.forEachIndexed { index, row ->
                                    ConsolidatedProductRow(row = row, branches = branches)
                                    if (index < group.items.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
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
                        formatQty(grandTotal) + " unidades",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Botón crear lote, o banner verde si ya existe lote para esta combinación.
            val existingLot = state.lotForConsolidatedView
            if (existingLot != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Ya se creó un lote de producción para esta fecha y turno",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Estado: ${ProductionLotStatus.label(existingLot.status)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1B5E20)
                            )
                        }
                        TextButton(onClick = { onNavigateToLotDetail(existingLot.id) }) {
                            Text(
                                "Ver lote",
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else if (canManageLots && shifts.size == 2) {
                Button(
                    onClick = {
                        val branchId = selectedBranch?.id ?: return@Button
                        onNavigateToShiftSplit(branchId, selectedDate)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
                ) {
                    Text("Repartir turnos y crear lotes →", fontWeight = FontWeight.Bold)
                }
            } else if (canManageLots) {
                Button(
                    onClick = {
                        onNavigateToLotForm(
                            selectedBranch?.id,
                            selectedShiftId.takeIf { it.isNotBlank() },
                            selectedDate
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
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
                if (row.isSpecial) "★ ${row.productName}" else row.productName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (row.isSpecial) BrandColors.AccentDeep else Color.Unspecified,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    "${formatQty(row.total)} unidades",
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
                        formatQty(qty) + " unidades",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/** Separador de masa en el listado vertical (móvil). */
@Composable
private fun MasaSectionLabel(title: String, units: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF7A8B96)
        )
        Text(
            "${formatQty(units)} u.",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF7A8B96)
        )
    }
}

@Composable
private fun ConsolidatedTable(
    groups: List<MasaGroup<ConsolidatedRow>>,
    branches: List<Pair<String, String>>
) {
    val productColWidth = 160.dp
    val qtyColWidth = 80.dp
    val rows = remember(groups) { groups.flatMap { it.items } }

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

            val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
            val headerBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            val altRowBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    // Header con fondo y línea superior/inferior.
                    HorizontalDivider(color = lineColor, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .background(headerBg)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Producto",
                            modifier = Modifier.width(productColWidth).padding(horizontal = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.Primary
                        )
                        branches.forEach { (_, branchName) ->
                            Text(
                                branchName,
                                modifier = Modifier.width(qtyColWidth).padding(horizontal = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = BrandColors.Primary,
                                textAlign = TextAlign.End
                            )
                        }
                        Text(
                            "Total",
                            modifier = Modifier.width(qtyColWidth).padding(horizontal = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.Primary,
                            textAlign = TextAlign.End
                        )
                    }
                    HorizontalDivider(color = lineColor, thickness = 1.dp)

                    // Filas agrupadas por masa. El índice para el fondo alternado
                    // corre a través de todos los grupos para que la lectura en
                    // zigzag no se reinicie en cada encabezado.
                    var rowIndex = 0
                    groups.forEach { group ->
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                group.title.uppercase(),
                                modifier = Modifier.width(productColWidth).padding(horizontal = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7A8B96)
                            )
                            branches.forEach { (branchId, _) ->
                                Text(
                                    formatQty(group.items.sumOf { it.quantitiesByBranch[branchId] ?: 0.0 }),
                                    modifier = Modifier.width(qtyColWidth).padding(horizontal = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7A8B96),
                                    textAlign = TextAlign.End
                                )
                            }
                            Text(
                                formatQty(group.items.sumOf { it.total }),
                                modifier = Modifier.width(qtyColWidth).padding(horizontal = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7A8B96),
                                textAlign = TextAlign.End
                            )
                        }
                        HorizontalDivider(color = lineColor, thickness = 0.6.dp)
                        group.items.forEach { row ->
                        val index = rowIndex++
                        Row(
                            modifier = Modifier
                                .background(if (index % 2 == 1) altRowBg else Color.Transparent)
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // El "★" delante marca el pedido especial en la tabla sin
                            // gastar una columna: es un pan de encargo, no el de todos
                            // los días, y la planta lo tiene que ver al consolidar.
                            Text(
                                if (row.isSpecial) "★ ${row.productName}" else row.productName,
                                modifier = Modifier.width(productColWidth).padding(horizontal = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (row.isSpecial) BrandColors.AccentDeep
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            branches.forEach { (branchId, _) ->
                                val qty = row.quantitiesByBranch[branchId] ?: 0.0
                                Text(
                                    if (qty > 0.0) formatQty(qty) else "—",
                                    modifier = Modifier.width(qtyColWidth).padding(horizontal = 6.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.End,
                                    color = if (qty > 0.0) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(
                                formatQty(row.total),
                                modifier = Modifier.width(qtyColWidth).padding(horizontal = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End
                            )
                        }
                        HorizontalDivider(color = lineColor, thickness = 0.6.dp)
                        }
                    }

                    // Totals row.
                    Row(
                        modifier = Modifier
                            .background(headerBg)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "TOTAL",
                            modifier = Modifier.width(productColWidth).padding(horizontal = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.Primary
                        )
                        branches.forEach { (branchId, _) ->
                            val branchTotal = rows.sumOf { it.quantitiesByBranch[branchId] ?: 0.0 }
                            Text(
                                formatQty(branchTotal),
                                modifier = Modifier.width(qtyColWidth).padding(horizontal = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = BrandColors.Primary,
                                textAlign = TextAlign.End
                            )
                        }
                        Text(
                            formatQty(rows.sumOf { it.total }),
                            modifier = Modifier.width(qtyColWidth).padding(horizontal = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.Primary,
                            textAlign = TextAlign.End
                        )
                    }
                    HorizontalDivider(color = lineColor, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun ProductionOrderCard(
    order: ProductionOrder,
    lotStatus: String?,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (isSelected) BorderStroke(1.5.dp, BrandColors.Primary) else null
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Fila superior: estado + #productos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(order.status, lotStatus, order.isLateOrder)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        "${order.lineCount} productos",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.Primary
                    )
                }
            }

            // Origen → destino con los mismos chips de color que la tabla, para
            // que la lectura sea idéntica en teléfono y en tablet.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Solicita",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(3.dp))
                    BranchChip(order.sellingBranchName, BranchChipRole.REQUESTING)
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "hacia",
                    modifier = Modifier.size(16.dp).padding(horizontal = 2.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "Produce",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(3.dp))
                    BranchChip(order.producingBranchName, BranchChipRole.PRODUCING)
                }
            }
            CardInfoRow("Turno", order.shiftName.ifBlank { "—" })
            CardInfoRow("Para el día", formatProductionDate(order.requestedDate))
            CardInfoRow(
                "Creado por",
                "${order.createdByUserName.ifBlank { "—" }} · ${formatHourMinute(order.createdAt)}"
            )
        }
    }
}

@Composable
private fun CardInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = BrandColors.Primary,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

private fun monthName(month: Int): String = when (month) {
    1 -> "Enero"; 2 -> "Febrero"; 3 -> "Marzo"; 4 -> "Abril"
    5 -> "Mayo"; 6 -> "Junio"; 7 -> "Julio"; 8 -> "Agosto"
    9 -> "Septiembre"; 10 -> "Octubre"; 11 -> "Noviembre"; 12 -> "Diciembre"
    else -> "—"
}

private fun monthShort(month: Int): String = when (month) {
    1 -> "Ene"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Abr"
    5 -> "May"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Ago"
    9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; 12 -> "Dic"
    else -> "—"
}

internal fun formatHourMinute(millis: Long): String {
    if (millis == 0L) return "—"
    return runCatching {
        val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        "${ldt.hour.toString().padStart(2,'0')}:${ldt.minute.toString().padStart(2,'0')}"
    }.getOrElse { "—" }
}

private fun formatQty(qty: Double): String =
    if (qty == qty.toLong().toDouble()) qty.toLong().toString() else qty.fmt(1)

internal fun formatProductionDate(millis: Long): String {
    if (millis == 0L) return "—"
    return runCatching {
        val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        "${ldt.dayOfMonth.toString().padStart(2,'0')}/${ldt.monthNumber.toString().padStart(2,'0')}/${ldt.year}"
    }.getOrElse { "—" }
}
