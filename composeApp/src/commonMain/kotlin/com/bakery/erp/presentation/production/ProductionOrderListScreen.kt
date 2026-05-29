package com.bakery.erp.presentation.production

import com.bakery.erp.util.fmt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
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
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.ProductionOrder
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.ProductionOrderStatus
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import kotlin.time.Clock
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
    onNavigateToEditOrder: (orderId: String) -> Unit = {},
    onNavigateToLotForm: (branchId: String?, shiftId: String?, date: Long?) -> Unit,
    onNavigateToLotDetail: (lotId: String) -> Unit = {},
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
                    containerColor = Color(0xFF005B8D),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (canSend && selectedTab == 0) {
                FloatingActionButton(
                    onClick = onNavigateToForm,
                    containerColor = Color(0xFF005B8D)
                ) { Icon(Icons.Default.Add, "Nuevo pedido", tint = Color.White) }
            }
        },
        containerColor = Color(0xFFFDFBF7)
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
                            onLoadShifts = { branchId ->
                                viewModel.loadShiftsForProducingBranch(companyId, branchId)
                            },
                            onLoadConsolidated = { branchId, date, shiftId ->
                                viewModel.loadConsolidatedView(companyId, branchId, date, shiftId)
                            },
                            onNavigateToLotForm = onNavigateToLotForm,
                            onNavigateToLotDetail = onNavigateToLotDetail
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
                        selectedContainerColor = Color(0xFF005B8D),
                        selectedLabelColor = Color.White
                    )
                )
                shiftFilterOptions.forEach { (id, name) ->
                    FilterChip(
                        selected = selectedShiftFilter == id,
                        onClick = { onShiftFilterChange(id) },
                        label = { Text(name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF005B8D),
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
                Icon(Icons.Default.ChevronLeft, "Mes anterior", tint = Color(0xFF005B8D))
            }
            Row(
                modifier = Modifier.weight(1f).clickable(onClick = onTap),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = Color(0xFF005B8D),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${monthName(month)} $year",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF005B8D),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.ChevronRight, "Mes siguiente", tint = Color(0xFF005B8D))
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
                        Icon(Icons.Default.ChevronLeft, "Año anterior", tint = Color(0xFF005B8D))
                    }
                    Text(
                        "$year",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF005B8D),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    IconButton(onClick = { year += 1 }) {
                        Icon(Icons.Default.ChevronRight, "Año siguiente", tint = Color(0xFF005B8D))
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
                                    color = if (selected) Color(0xFF005B8D) else Color.Transparent,
                                    border = if (selected) null
                                             else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        monthShort(m),
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        textAlign = TextAlign.Center,
                                        color = if (selected) Color.White else Color(0xFF005B8D),
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
                        // Sucursal solicitante resaltada con chip azul del tema (letras
                        // blancas) — la encargada de planta identifica rápido el origen.
                        Box(modifier = Modifier.weight(1.2f).padding(horizontal = 4.dp)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF005B8D)
                            ) {
                                Text(
                                    order.sellingBranchName.ifBlank { "—" },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                        TableCell(order.producingBranchName, Modifier.weight(1.2f))
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
        color = Color(0xFF005B8D),
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
): Triple<String, Color, Color> {
    if (orderStatus == ProductionOrderStatus.CANCELLED) {
        return Triple("Cancelado", Color.Gray, Color(0xFFF5F5F5))
    }
    if (orderStatus == ProductionOrderStatus.CONFIRMED) {
        return Triple("Confirmado", Color(0xFF2E7D32), Color(0xFFE8F5E9))
    }
    // PENDING. Si es tardío y aún no fue incorporado a un lote, marcar explícitamente.
    if (isLateOrder && lotStatus == null) {
        return Triple("Pedido tardío · pend. aprobar", Color(0xFFC62828), Color(0xFFFFEBEE))
    }
    return when (lotStatus) {
        ProductionLotStatus.OPEN ->
            Triple("Lote abierto", Color(0xFF1565C0), Color(0xFFE3F2FD))
        ProductionLotStatus.IN_PROGRESS ->
            Triple("En producción", Color(0xFF6A1B9A), Color(0xFFF3E5F5))
        ProductionLotStatus.COMPLETED ->
            Triple("Producción terminada", Color(0xFF2E7D32), Color(0xFFE8F5E9))
        else ->
            Triple("Pendiente", Color(0xFFF57C00), Color(0xFFFFF3E0))
    }
}

@Composable
private fun StatusChip(status: String, lotStatus: String? = null, isLateOrder: Boolean = false) {
    val (label, color, bg) = effectiveOrderDisplay(status, lotStatus, isLateOrder)
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConsolidatedTab(
    state: ProductionUiState,
    user: com.bakery.erp.model.User?,
    canManageLots: Boolean,
    onLoadShifts: (branchId: String) -> Unit,
    onLoadConsolidated: (branchId: String, date: Long, shiftId: String) -> Unit,
    onNavigateToLotForm: (branchId: String?, shiftId: String?, date: Long?) -> Unit,
    onNavigateToLotDetail: (lotId: String) -> Unit
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
    var selectedShiftId by rememberSaveable { mutableStateOf("") }

    // Al cambiar de planta: cargar sus turnos.
    LaunchedEffect(selectedBranch?.id) {
        val branchId = selectedBranch?.id ?: return@LaunchedEffect
        onLoadShifts(branchId)
    }

    // Resolver el turno por defecto al cargar los turnos: el primero activo. Si la planta
    // no tiene turnos configurados, dejamos shiftId = "" (pedidos sin turno).
    LaunchedEffect(state.shiftsForSelectedProducingBranch) {
        val shifts = state.shiftsForSelectedProducingBranch
        if (shifts.isEmpty()) {
            selectedShiftId = ""
        } else if (shifts.none { it.id == selectedShiftId }) {
            selectedShiftId = shifts.first().id
        }
    }

    LaunchedEffect(selectedBranch, selectedDate, selectedShiftId) {
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

        // ── Selector de turno ─────────────────────────────────────────────
        val shifts = state.shiftsForSelectedProducingBranch
        if (shifts.size > 1) {
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
                                selectedContainerColor = Color(0xFF005B8D),
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
                        color = Color(0xFF005B8D)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005B8D))
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
                            color = Color(0xFF005B8D)
                        )
                        branches.forEach { (_, branchName) ->
                            Text(
                                branchName,
                                modifier = Modifier.width(qtyColWidth).padding(horizontal = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF005B8D),
                                textAlign = TextAlign.End
                            )
                        }
                        Text(
                            "Total",
                            modifier = Modifier.width(qtyColWidth).padding(horizontal = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF005B8D),
                            textAlign = TextAlign.End
                        )
                    }
                    HorizontalDivider(color = lineColor, thickness = 1.dp)

                    // Data rows (alternating bg + dividers entre filas).
                    rows.forEachIndexed { index, row ->
                        Row(
                            modifier = Modifier
                                .background(if (index % 2 == 1) altRowBg else Color.Transparent)
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                row.productName,
                                modifier = Modifier.width(productColWidth).padding(horizontal = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
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
                            color = Color(0xFF005B8D)
                        )
                        branches.forEach { (branchId, _) ->
                            val branchTotal = rows.sumOf { it.quantitiesByBranch[branchId] ?: 0.0 }
                            Text(
                                formatQty(branchTotal),
                                modifier = Modifier.width(qtyColWidth).padding(horizontal = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF005B8D),
                                textAlign = TextAlign.End
                            )
                        }
                        Text(
                            formatQty(rows.sumOf { it.total }),
                            modifier = Modifier.width(qtyColWidth).padding(horizontal = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF005B8D),
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
        border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF005B8D)) else null
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
                        color = Color(0xFF005B8D)
                    )
                }
            }

            CardInfoRow("Sucursal solicitante", order.sellingBranchName.ifBlank { "—" })
            CardInfoRow("Sucursal productora", order.producingBranchName.ifBlank { "—" })
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
            color = Color(0xFF005B8D),
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
