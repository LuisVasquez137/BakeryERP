package com.bakery.erp.presentation.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.PackagingFilter
import com.bakery.erp.model.OrderInvoiceFilter
import com.bakery.erp.model.SaleOrder
import com.bakery.erp.model.bankRefundPending
import com.bakery.erp.model.matches
import com.bakery.erp.model.ordersFromSales
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import com.bakery.erp.model.Permission
import com.bakery.erp.model.chargedPackaging
import com.bakery.erp.model.filterByPackaging
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.ChipTone
import androidx.compose.runtime.saveable.rememberSaveable
import com.bakery.erp.model.Sale
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.util.fmtMoney
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleHistoryListScreen(
    onNavigateBack: () -> Unit,
    /** Abre el cobro (pan + restaurante juntos), por su [SaleOrder.key]. */
    onNavigateToDetail: (orderKey: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: SaleHistoryViewModel,
    /** "Ventas de esta caja": abierta desde el punto de venta con su sucursal y caja. */
    posBranchId: String? = null,
    posRegisterId: String? = null
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val user = authState.user
    val fromPos = posRegisterId != null
    // Los totales del día solo para quien ve el valor de las ventas; el monto de cada cobro
    // también para quien está cobrando en la caja, que lo necesita para encontrar la venta.
    val canSeeTotals = user?.hasPermission(Permission.VIEW_SALES_VALUE) == true
    val canSeeValue = canSeeTotals || (fromPos && user?.hasPermission(Permission.OPERATE_POS) == true)
    val snackbar = remember { SnackbarHostState() }
    var packagingFilter by rememberSaveable { mutableStateOf(PackagingFilter.ALL) }
    // Al volver del detalle se conservan la caja, el filtro y la búsqueda.
    var initialized by rememberSaveable { mutableStateOf(false) }

    // Caja elegida: sus ventas (el restaurante cobrado en esta caja lleva la caja que lo cobró).
    val registerSales = if (state.registerFilter.isEmpty()) state.sales
        else state.sales.filter { it.registerId == state.registerFilter }
    // El filtro solo aparece si ese día se cobró algún empaque: en la panadería que no cobra
    // la bolsa no tiene nada que decir.
    val anyPackaging = registerSales.any { it.chargedPackaging }
    val packagingIds = if (anyPackaging) registerSales.filterByPackaging(packagingFilter).map { it.id }.toSet() else null
    val shownOrders = ordersFromSales(registerSales)
        .filter { o -> packagingIds == null || o.sales.any { it.id in packagingIds } }
        .filter { state.invoiceFilter.accepts(it) }
        .filter { it.matches(state.query) }
    val registers = state.sales.filter { it.registerId.isNotBlank() }
        .distinctBy { it.registerId }
        .map { it.registerId to it.registerName.ifBlank { "Caja" } }
        .sortedBy { it.second }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && user != null && !initialized) {
            initialized = true
            if (posBranchId != null && posRegisterId != null) viewModel.openForRegister(companyId, user, posBranchId, posRegisterId)
            else viewModel.load(companyId, user)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (fromPos) "Ventas de la caja" else "Historial de ventas", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                // Filtros compactos (en horizontal la lista necesita el alto): búsqueda y día en
                // una fila; sucursal, caja, factura y empaque en una sola fila de chips que se
                // desliza de lado; el resumen del día en una línea de texto.
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { viewModel.setQuery(it) },
                        placeholder = { Text("Monto, # orden, mesa o nombre", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = if (state.query.isNotEmpty()) {
                            { IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Close, "Borrar búsqueda") } }
                        } else null,
                        singleLine = true,
                        // Casi siempre se busca por monto ("pagué 35"): teclado de números.
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    DateChevronBar(
                        date = state.selectedDate,
                        onPrev = { state.selectedDate?.let { d -> viewModel.selectDate(companyId, d.minusDays(1)) } },
                        onNext = { state.selectedDate?.let { d -> viewModel.selectDate(companyId, d.plusDays(1)) } },
                        onResetToday = {
                            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                            viewModel.selectDate(companyId, today)
                        }
                    )
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sucursal (desde la caja, la sucursal es la de la caja)
                    if (!fromPos && state.branches.size > 1) {
                        state.branches.forEach { b ->
                            FilterChip(
                                selected = b.id == state.selectedBranchId,
                                onClick = { viewModel.selectBranch(companyId, b.id) },
                                label = { Text(b.name) }
                            )
                        }
                        ChipSeparator()
                    }
                    if (RegisterChips(
                            registers = registers,
                            posRegisterId = posRegisterId,
                            selected = state.registerFilter,
                            onSelect = { viewModel.setRegisterFilter(it) }
                        )
                    ) ChipSeparator()
                    OrderInvoiceFilter.entries.forEach { f ->
                        FilterChip(
                            selected = state.invoiceFilter == f,
                            onClick = { viewModel.setInvoiceFilter(f) },
                            label = { Text(f.label) }
                        )
                    }
                    if (anyPackaging) {
                        ChipSeparator()
                        val counts = PackagingFilter.entries.associateWith { registerSales.filterByPackaging(it).size }
                        PackagingFilter.entries.forEach { f ->
                            FilterChip(
                                selected = f == packagingFilter,
                                onClick = { packagingFilter = f },
                                label = { Text("${f.label} (${counts[f] ?: 0})") }
                            )
                        }
                    }
                }
                // En la caja nunca se muestra cuánto se lleva vendido, ni al dueño: con ese dato
                // se puede contar la gaveta antes del cuadre del cierre.
                DaySummaryLine(sales = registerSales, orders = shownOrders.size, canSeeValue = canSeeTotals && !fromPos)
                HorizontalDivider()
                when {
                    state.isLoading && state.sales.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    shownOrders.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Receipt, null,
                                     tint = MaterialTheme.colorScheme.outline,
                                     modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(if (registerSales.isEmpty()) "Sin ventas en esta fecha" else "Ningún cobro con este filtro",
                                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(shownOrders, key = { it.key }) { order ->
                            OrderCard(
                                order = order,
                                canSeeValue = canSeeValue,
                                showRegister = state.registerFilter.isEmpty(),
                                onClick = { onNavigateToDetail(order.key) }
                            )
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
private fun DateChevronBar(
    date: LocalDate?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onResetToday: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "Día anterior") }
        TextButton(onClick = onResetToday) {
            Text(date?.toString() ?: "—", fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "Día siguiente") }
    }
}

/** "Esta caja · Caja 2 · Todas": el cliente casi siempre pagó en la caja donde vuelve a reclamar. */
@Composable
private fun RegisterChips(
    registers: List<Pair<String, String>>,
    posRegisterId: String?,
    selected: String,
    onSelect: (String) -> Unit
): Boolean {
    val options = buildList {
        if (posRegisterId != null) add(posRegisterId to "Esta caja")
        registers.filter { it.first != posRegisterId }.forEach { add(it) }
    }
    if (options.size < 2 && posRegisterId == null) return false
    options.forEach { (id, name) ->
        FilterChip(selected = selected == id, onClick = { onSelect(id) }, label = { Text(name) })
    }
    FilterChip(
        selected = selected.isEmpty(),
        onClick = { onSelect("") },
        label = { Text(if (posRegisterId != null) "Toda la sucursal" else "Todas las cajas") }
    )
    return true
}

@Composable
private fun ChipSeparator() {
    Box(Modifier.height(24.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** "12 cobros · 15 ventas · Q 845.00 · promedio Q 56.33" en una línea, en vez de tarjetas. */
@Composable
private fun DaySummaryLine(sales: List<Sale>, orders: Int, canSeeValue: Boolean) {
    val count = sales.size
    val total = sales.sumOf { it.total }
    val text = buildList {
        add("$orders cobro(s)")
        add("$count venta(s)")
        if (canSeeValue) {
            add("Q ${total.fmtMoney(2)}")
            if (count > 0) add("promedio Q ${(total / count).fmtMoney(2)}")
        }
    }.joinToString(" · ")
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun OrderCard(order: SaleOrder, canSeeValue: Boolean, showRegister: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    order.description.ifBlank { "Venta" },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                )
                Text(
                    listOfNotNull(
                        hourLabel(order.at),
                        order.registerName.takeIf { showRegister && it.isNotBlank() },
                        order.cashierName.ifBlank { null }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    SalePaymentMethod.label(order.paymentMethod) +
                        if (order.paymentParts.size > 1 && canSeeValue) " · " + com.bakery.erp.model.paymentPartsLabel(order.paymentParts) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                val invoiceSale = order.invoicedSale
                val packaging = order.sales.filter { it.chargedPackaging }
                if (invoiceSale != null || packaging.isNotEmpty() || order.voided) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (order.voided) BrandChip(text = "Anulada", tone = ChipTone.DANGER)
                        if (order.sales.any { it.bankRefundPending }) BrandChip(text = "Devolver por banco", tone = ChipTone.DANGER)
                        if (invoiceSale != null) SaleInvoiceChip(invoiceSale)
                        if (packaging.isNotEmpty()) {
                            BrandChip(
                                text = "Empaque ×${packaging.sumOf { it.packagingUnits }.fmtMoney(0)}",
                                tone = ChipTone.INFO
                            )
                        }
                    }
                }
            }
            if (canSeeValue) {
                Text("Q ${order.total.fmtMoney(2)}",
                     fontWeight = FontWeight.Bold,
                     style = MaterialTheme.typography.titleSmall,
                     color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** "14:05" en la hora del aparato. */
private fun hourLabel(millis: Long): String {
    if (millis <= 0L) return "—"
    val t = kotlin.time.Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}

// Helpers de fecha — kotlinx.datetime
private fun LocalDate.minusDays(days: Int): LocalDate {
    var y = year; var m = monthNumber; var d = dayOfMonth - days
    while (d < 1) {
        m -= 1
        if (m < 1) { m = 12; y -= 1 }
        d += daysInMonth(y, m)
    }
    return LocalDate(y, m, d)
}
private fun LocalDate.plusDays(days: Int): LocalDate {
    var y = year; var m = monthNumber; var d = dayOfMonth + days
    while (d > daysInMonth(y, m)) {
        d -= daysInMonth(y, m); m += 1
        if (m > 12) { m = 1; y += 1 }
    }
    return LocalDate(y, m, d)
}
private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}
