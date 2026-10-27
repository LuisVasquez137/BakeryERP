package com.bakery.erp.presentation.pos

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.FelInvoiceStatus
import com.bakery.erp.model.Permission
import com.bakery.erp.model.SaleOrder
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.SaleStatus
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.isRestaurantSale
import com.bakery.erp.model.ordersFromSales
import com.bakery.erp.model.paymentPartsLabel
import com.bakery.erp.model.restaurantSaleLabel
import com.bakery.erp.model.saleVoidBlocker
import com.bakery.erp.model.paymentChangeBlocker
import com.bakery.erp.model.paymentChangePermission
import com.bakery.erp.model.withPaymentMethod
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtMoney

/**
 * Detalle de UN cobro: el pan y la cuenta del restaurante que se pagaron juntos se ven como una
 * sola cuenta, se facturan en una sola factura y se reimprimen en un solo ticket, igual que en
 * el cobro. El cobro sale de la lista en vivo del historial: al facturar, el estado se actualiza
 * solo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleOrderDetailScreen(
    orderKey: String,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: SaleHistoryViewModel,
    bluetoothPrinterManager: com.bakery.erp.domain.BluetoothPrinterManager? = null
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId.orEmpty()
    // Desde la caja, quien cobra ve los montos: los necesita para reconocer la venta.
    val canSeeValue = user?.hasPermission(Permission.VIEW_SALES_VALUE) == true ||
        (state.posMode && user?.hasPermission(Permission.OPERATE_POS) == true)
    val snackbar = remember { SnackbarHostState() }
    val order: SaleOrder? = remember(state.sales, orderKey) { ordersFromSales(state.sales).firstOrNull { it.key == orderKey } }
    val saleIds = order?.sales?.map { it.id }.orEmpty()

    LaunchedEffect(companyId, saleIds) {
        if (companyId.isNotEmpty() && saleIds.isNotEmpty()) viewModel.loadOrderLines(companyId, saleIds)
    }
    val orderSessionId = order?.primary?.sessionId.orEmpty()
    LaunchedEffect(companyId, orderSessionId) {
        if (companyId.isNotEmpty()) viewModel.loadOrderSession(companyId, orderSessionId)
    }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() } }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    val branchHasNit = state.branches.firstOrNull { it.id == order?.primary?.branchId }?.felProfileId?.isNotBlank() == true
    // Facturar después es el mismo acto que facturar al cobrar: pide el permiso de cobrar.
    val toInvoice = order?.uninvoicedSales.orEmpty()
    val canInvoiceLater = order != null && !order.voided && branchHasNit && toInvoice.isNotEmpty() &&
        user?.hasPermission(Permission.OPERATE_POS) == true
    // Anular es corregir un documento fiscal ya emitido: mismo permiso que pide el servidor.
    val canFixInvoice = user?.hasPermission(Permission.VOID_SALE) == true
    val invoicedSale = order?.invoicedSale
    var invoiceOpen by remember { mutableStateOf(false) }
    var voidOpen by remember { mutableStateOf(false) }
    var reissueOpen by remember { mutableStateOf(false) }
    var btPickerOpen by remember { mutableStateOf(false) }
    var saleVoidOpen by remember { mutableStateOf(false) }
    // Anular la venta: mismo permiso que anular la factura (el servidor lo pide para la SAT).
    val canVoidSale = user?.hasPermission(Permission.VOID_SALE) == true
    // La caja se lee al abrir el detalle; mientras llega, el bloqueo dice que no se encontró.
    val voidBlocker = order?.let { saleVoidBlocker(it, state.orderSession) }
    // Cambiar la forma de pago: en la caja abierta quien anula ventas; cerrada, quien ve la bitácora.
    var paymentChangeOpen by remember { mutableStateOf(false) }
    val canChangePayment = order != null && !order.voided && state.orderSession != null &&
        user?.hasPermission(paymentChangePermission(state.orderSession)) == true
    val paymentChangeBlocker = order?.let { paymentChangeBlocker(it, state.orderSession) }

    fun print() {
        val o = order ?: return
        val manager = bluetoothPrinterManager
        if (manager != null && manager.isPlatformSupported && manager.getSavedConfig() == null) btPickerOpen = true
        else viewModel.printOrder(companyId, o)
    }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Detalle del cobro", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    if (viewModel.canPrint && order != null) {
                        IconButton(onClick = { print() }, enabled = !state.isPrinting && !state.isOrderLoading) {
                            if (state.isPrinting) CircularProgressIndicator(
                                Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary
                            )
                            else Icon(Icons.Default.Print, if (order.hasInvoice) "Reimprimir con factura" else "Reimprimir ticket")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (order == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator()
                else Text("No se encontró el cobro.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OrderHeaderCard(order, canSeeValue) }
                if (order.voided) item {
                    VoidedOrderCard(
                        order = order,
                        canMarkRefund = canVoidSale,
                        busy = state.isVoidBusy,
                        onMarkRefunded = { user?.let { viewModel.markBankRefunded(companyId, order, it) } }
                    )
                }
                if (canInvoiceLater) item {
                    Button(
                        onClick = { invoiceOpen = true },
                        enabled = !state.isInvoiceBusy,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(if (order.hasInvoice) "Facturar lo que falta" else "Facturar este cobro")
                    }
                }
                if (canFixInvoice && invoicedSale != null && !order.voided) {
                    when (invoicedSale.invoiceStatus) {
                        FelInvoiceStatus.CERTIFICADO -> item {
                            OutlinedButton(
                                onClick = { voidOpen = true },
                                enabled = !state.isInvoiceBusy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Anular factura") }
                        }
                        FelInvoiceStatus.ANULADO, FelInvoiceStatus.RECHAZADO -> item {
                            OutlinedButton(
                                onClick = { reissueOpen = true },
                                enabled = !state.isInvoiceBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Facturar de nuevo") }
                        }
                        else -> Unit
                    }
                }
                if (canChangePayment) item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { paymentChangeOpen = true },
                            enabled = paymentChangeBlocker == null && !state.isPaymentChangeBusy && !state.isVoidBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cambiar forma de pago") }
                        if (paymentChangeBlocker != null) {
                            Text(paymentChangeBlocker, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (canVoidSale && !order.voided) item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { saleVoidOpen = true },
                            enabled = voidBlocker == null && !state.isVoidBusy && !state.isInvoiceBusy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Anular venta (devolución)") }
                        if (voidBlocker != null) {
                            Text(voidBlocker, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                order.sales.forEach { sale ->
                    item(key = "h_" + sale.id) {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (sale.isRestaurantSale) restaurantSaleLabel(sale) else "Panadería",
                                fontWeight = FontWeight.SemiBold,
                                color = BrandColors.Primary,
                                modifier = Modifier.weight(1f)
                            )
                            if (sale.status == SaleStatus.VOIDED) {
                                Text("Anulada", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(8.dp))
                            }
                            if (canSeeValue && order.sales.size > 1) {
                                Text("Q ${sale.total.fmtMoney(2)}", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    val lines = state.orderLines[sale.id]
                    if (lines == null) {
                        item(key = "l_" + sale.id) {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                    } else {
                        items(lines, key = { sale.id + "_" + it.id }) { line -> SaleLineRow(line, canSeeValue) }
                    }
                }
            }
        }
        com.bakery.erp.ui.LoadingOverlay(visible = state.isInvoiceBusy, message = "Hablando con la SAT…")
        com.bakery.erp.ui.LoadingOverlay(visible = state.isVoidBusy, message = "Anulando la venta…")
        com.bakery.erp.ui.LoadingOverlay(visible = state.isPaymentChangeBusy, message = "Cambiando la forma de pago…")
    }

    val lookup: suspend (String) -> Result<String?> = { nit -> viewModel.lookupNit(companyId, order?.primary?.branchId.orEmpty(), nit) }
    if (invoiceOpen && order != null) {
        // La factura cubre lo que todavía no tiene factura: el pan y el restaurante juntos.
        val invoiceTotal = toInvoice.sumOf { it.total }
        ReissueInvoiceDialog(
            sale = toInvoice.first().copy(total = invoiceTotal),
            lookupNit = lookup,
            onDismiss = { invoiceOpen = false },
            onConfirm = { to ->
                invoiceOpen = false
                viewModel.invoiceOrder(companyId, order, to)
            },
            title = "Facturar este cobro",
            message = if (toInvoice.size > 1)
                "Una sola factura por Q ${invoiceTotal.fmtMoney(2)}: el pan y la cuenta del restaurante juntos, con la fecha de hoy. El cobro no cambia."
            else "Se emite la factura por Q ${invoiceTotal.fmtMoney(2)} con la fecha de hoy. El cobro no cambia."
        )
    }
    if (voidOpen && order != null && invoicedSale != null) {
        val sameInvoice = order.sales.filter { it.dteId == invoicedSale.dteId }
        VoidInvoiceDialog(
            sale = invoicedSale.copy(total = sameInvoice.sumOf { it.total }),
            lookupNit = lookup,
            onDismiss = { voidOpen = false },
            onConfirm = { reason, reissueTo ->
                voidOpen = false
                viewModel.voidInvoice(companyId, invoicedSale, reason, reissueTo)
            }
        )
    }
    if (reissueOpen && order != null && invoicedSale != null) {
        val sameInvoice = order.sales.filter { it.dteId == invoicedSale.dteId }
        ReissueInvoiceDialog(
            sale = invoicedSale.copy(total = sameInvoice.sumOf { it.total }),
            lookupNit = lookup,
            onDismiss = { reissueOpen = false },
            onConfirm = { to ->
                reissueOpen = false
                // El servidor vuelve a juntar todas las ventas que cubría la factura anterior.
                viewModel.reissueInvoice(companyId, invoicedSale, to)
            }
        )
    }
    if (saleVoidOpen && order != null) {
        VoidSaleDialog(
            order = order,
            onDismiss = { saleVoidOpen = false },
            onConfirm = { reason ->
                saleVoidOpen = false
                user?.let { viewModel.voidOrder(companyId, order, reason, it) }
            }
        )
    }
    val changeSession = state.orderSession
    if (paymentChangeOpen && order != null && changeSession != null) {
        PaymentChangeDialog(
            order = order,
            session = changeSession,
            onDismiss = { paymentChangeOpen = false },
            onConfirm = { method ->
                paymentChangeOpen = false
                user?.let { viewModel.changePaymentMethod(companyId, order, method, it) }
            }
        )
    }
    if (btPickerOpen && bluetoothPrinterManager != null) {
        BluetoothPrinterPickerDialog(
            manager = bluetoothPrinterManager,
            onConfigured = {
                btPickerOpen = false
                order?.let { viewModel.printOrder(companyId, it) }
            },
            onDismiss = { btPickerOpen = false }
        )
    }
}

@Composable
private fun OrderHeaderCard(order: SaleOrder, canSeeValue: Boolean) {
    val p = order.primary
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${formatReceiptTimestamp(order.at)} · ${p.branchName} · ${p.registerName}", fontWeight = FontWeight.SemiBold)
            Text("Cajero: ${order.cashierName}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            PaymentMethodChips(order, showAmounts = canSeeValue)
            order.sales.firstOrNull { it.paymentChangedAt > 0L }?.let { s ->
                Text(
                    "Se cobró como ${SalePaymentMethod.label(s.originalPaymentMethod)}; lo corrigió " +
                        "${s.paymentChangedByName.ifBlank { "alguien" }} el ${formatReceiptTimestamp(s.paymentChangedAt)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (p.resellerName.isNotBlank()) {
                Text("Revendedor: ${p.resellerName}", style = MaterialTheme.typography.bodySmall, color = BrandColors.Primary)
            }
            // Cada factura distinta del cobro (normalmente una; la de ComandaYa va aparte).
            val invoices = order.sales.filter { it.invoiceStatus.isNotBlank() }
                .distinctBy { it.dteId.ifBlank { it.invoiceStatus + it.id } }
            if (invoices.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                invoices.forEach { s ->
                    SaleInvoiceChip(s)
                    if (s.invoiceReceptorName.isNotBlank()) {
                        Text(
                            "A nombre de ${s.invoiceReceptorName}" +
                                if (s.invoiceReceptorId.isNotBlank() && s.invoiceReceptorId != "CF") " · NIT ${s.invoiceReceptorId}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (canSeeValue) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                val discount = order.sales.sumOf { it.discount }
                if (discount > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Descuento"); Text("- Q ${discount.fmtMoney(2)}")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.SemiBold)
                    Text("Q ${order.total.fmtMoney(2)}", fontWeight = FontWeight.Bold)
                }
                // Lo recibido y el vuelto son del cobro entero (se copian iguales a cada venta).
                if (p.tenderedAmount > 0.004) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (order.paymentParts.size > 1) "Efectivo recibido" else "Recibido")
                        Text("Q ${p.tenderedAmount.fmtMoney(2)}")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Vuelto"); Text("Q ${p.changeAmount.fmtMoney(2)}")
                    }
                }
            }
        }
    }
}

/**
 * Elegir la forma de pago correcta del cobro y ver, antes de confirmar, qué le hace a la caja:
 * cuánto cambia el efectivo esperado y los vouchers y, si la caja ya cerró, cómo queda la
 * diferencia de ese cierre.
 */
@Composable
private fun PaymentChangeDialog(
    order: SaleOrder,
    session: com.bakery.erp.model.CashSession,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var method by remember { mutableStateOf<String?>(null) }
    val current = order.paymentMethod
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar forma de pago") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cobro de Q ${order.total.fmtMoney(2)}, registrado como ${SalePaymentMethod.label(current)}. " +
                    "¿Cómo se pagó de verdad?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.bakery.erp.model.PAYMENT_CHANGE_METHODS.filter { it != current }.forEach { m ->
                        val (bg, fg) = com.bakery.erp.ui.theme.chipColors(
                            com.bakery.erp.ui.theme.paymentMethodTone(m), com.bakery.erp.ui.theme.ChipEmphasis.SOFT
                        )
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(SalePaymentMethod.label(m)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = bg, selectedLabelColor = fg)
                        )
                    }
                }
                val chosen = method
                if (chosen != null) {
                    val deltas = order.sales.fold(emptyMap<String, Double>()) { acc, s ->
                        val d = com.bakery.erp.model.paymentChangeSessionDeltas(
                            session, s, s.withPaymentMethod(chosen)
                        )
                        (acc.keys + d.keys).associateWith { (acc[it] ?: 0.0) + (d[it] ?: 0.0) }
                    }
                    fun line(label: String, field: String) = deltas[field]?.takeIf { kotlin.math.abs(it) > 0.004 }?.let {
                        "$label ${if (it > 0) "sube" else "baja"} Q ${kotlin.math.abs(it).fmtMoney(2)}"
                    }
                    listOfNotNull(
                        line("El efectivo esperado de la caja", "totalCashSales"),
                        line("La tarjeta (vouchers)", "totalCardSales"),
                        line("Las transferencias", "totalTransferSales"),
                        line("PedidosYa", "totalPedidosYaSales")
                    ).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (session.status == com.bakery.erp.model.CashSessionStatus.CLOSED) {
                        val newDiff = session.difference + (deltas["difference"] ?: 0.0)
                        Text(
                            "Esta caja ya se cerró: la diferencia de efectivo de ese cierre pasa de " +
                                "Q ${session.difference.fmtMoney(2)} a Q ${newDiff.fmtMoney(2)}.",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text("Queda en la bitácora y se avisa al dueño. La factura no cambia.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = method != null, onClick = { method?.let(onConfirm) }) { Text("Cambiar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
