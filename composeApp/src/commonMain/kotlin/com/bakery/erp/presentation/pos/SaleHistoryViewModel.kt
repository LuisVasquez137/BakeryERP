package com.bakery.erp.presentation.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.ConnectionStatus
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.CashSessionRepository
import com.bakery.erp.domain.SaleRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.Sale
import com.bakery.erp.model.SaleLine
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.isRestaurantSale
import com.bakery.erp.model.bankRefundPending
import com.bakery.erp.model.voidCashAmount
import com.bakery.erp.model.effectivePaymentBreakdown
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.withPaymentMethod
import com.bakery.erp.util.fmtMoney
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

data class SaleHistoryUiState(
    val branches: List<Branch> = emptyList(),
    val selectedBranchId: String = "",
    val selectedDate: LocalDate? = null,
    val sales: List<Sale> = emptyList(),
    val selectedSale: Sale? = null,
    val selectedSaleLines: List<SaleLine> = emptyList(),
    val sessions: List<CashSession> = emptyList(),
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val error: String? = null,
    /** Anulando o volviendo a facturar: tapa la pantalla, Digifact puede tardar. */
    val isInvoiceBusy: Boolean = false,
    /** Resultado para el snackbar ("Factura anulada", "Nueva factura serie…"). */
    val notice: String? = null,
    val isPrinting: Boolean = false,
    /** Caja de la lista de cobros. Vacío = todas las de la sucursal. */
    val registerFilter: String = "",
    val invoiceFilter: com.bakery.erp.model.OrderInvoiceFilter = com.bakery.erp.model.OrderInvoiceFilter.ALL,
    /** Monto, número de orden, mesa o nombre (ver [com.bakery.erp.model.matches]). */
    val query: String = "",
    /** Forma de pago de la lista de cobros. Vacío = todas. Un pago dividido sale en cada una. */
    val paymentFilter: String = "",
    /**
     * Abierta desde una caja ("Ventas de esta caja"): sucursal fija, la caja propia marcada y
     * los montos visibles para quien cobra, que los necesita para encontrar la venta.
     */
    val posRegisterId: String = "",
    /** Líneas por venta del cobro abierto en el detalle. */
    val orderLines: Map<String, List<SaleLine>> = emptyMap(),
    val isOrderLoading: Boolean = false,
    /** Caja del cobro abierto en el detalle: decide si todavía se puede anular. */
    val orderSession: CashSession? = null,
    /** Anulando un cobro: tapa la pantalla (factura, inventario y caja). */
    val isVoidBusy: Boolean = false,
    /** Corrigiendo la forma de pago de un cobro. */
    val isPaymentChangeBusy: Boolean = false
) {
    val posMode: Boolean get() = posRegisterId.isNotEmpty()
}

class SaleHistoryViewModel(
    private val saleRepository: SaleRepository,
    private val cashSessionRepository: CashSessionRepository,
    private val branchRepository: BranchRepository,
    /** Anular y volver a facturar desde el detalle de la venta. */
    private val felRepository: com.bakery.erp.domain.FelRepository,
    /** Reimpresión del ticket desde el detalle. */
    private val receiptPrinter: com.bakery.erp.domain.ReceiptPrinter,
    /** Solo para el nombre comercial del encabezado del ticket, igual que en el POS. */
    private val authRepository: com.bakery.erp.domain.AuthRepository,
    /** Aviso al dueño de cada venta anulada. */
    private val notificationRepository: com.bakery.erp.domain.NotificationRepository,
    /** Cada anulación queda en la bitácora de cajas. */
    private val cashAuditRepository: com.bakery.erp.domain.CashAuditRepository
) : ViewModel() {

    val canPrint: Boolean get() = receiptPrinter.isAvailable
    private var organizationName: String? = null

    private val _uiState = MutableStateFlow(SaleHistoryUiState())
    val uiState: StateFlow<SaleHistoryUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    fun load(companyId: String, currentUser: User) {
        // Desde el Home: todas las cajas, sin la caja propia de una sesión anterior.
        _uiState.update {
            it.copy(posRegisterId = "", registerFilter = "", invoiceFilter = com.bakery.erp.model.OrderInvoiceFilter.ALL, query = "",
                paymentFilter = "")
        }
        loadBranches(companyId, currentUser, forcedBranchId = null)
    }

    /**
     * "Ventas de esta caja": la sucursal de la caja, hoy, con la caja marcada. La cajera puede
     * ampliar a toda la sucursal si el cliente pagó en otra caja.
     */
    fun openForRegister(companyId: String, currentUser: User, branchId: String, registerId: String) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        _uiState.update {
            it.copy(
                posRegisterId = registerId, registerFilter = registerId,
                invoiceFilter = com.bakery.erp.model.OrderInvoiceFilter.ALL, query = "", paymentFilter = "",
                selectedBranchId = branchId, selectedDate = today
            )
        }
        loadBranches(companyId, currentUser, forcedBranchId = branchId)
    }

    fun setRegisterFilter(registerId: String) { _uiState.update { it.copy(registerFilter = registerId) } }
    fun setInvoiceFilter(f: com.bakery.erp.model.OrderInvoiceFilter) { _uiState.update { it.copy(invoiceFilter = f) } }
    fun setQuery(q: String) { _uiState.update { it.copy(query = q) } }
    fun setPaymentFilter(method: String) { _uiState.update { it.copy(paymentFilter = method) } }

    private fun loadBranches(companyId: String, currentUser: User, forcedBranchId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                branchRepository.getBranches(companyId)
                    .filter { it.active }
                    .filter { it.id == forcedBranchId || currentUser.canAccessBranch(it.id) }
                    .filter { BranchCapability.CAN_SELL in it.capabilities }
                    .sortedBy { it.name }
            }.onSuccess { branches ->
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                val current = _uiState.value.selectedBranchId
                // La caja manda: su sucursal aunque el usuario no la tenga en su lista general.
                val nextSelected = forcedBranchId
                    ?: if (current.isNotEmpty() && branches.any { it.id == current }) current
                       else branches.firstOrNull()?.id.orEmpty()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        branches = branches,
                        selectedBranchId = nextSelected,
                        selectedDate = it.selectedDate ?: now
                    )
                }
                if (nextSelected.isNotEmpty()) observeForDay(companyId, nextSelected, _uiState.value.selectedDate ?: now)
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun selectBranch(companyId: String, branchId: String) {
        if (branchId == _uiState.value.selectedBranchId) return
        _uiState.update { it.copy(selectedBranchId = branchId, sales = emptyList()) }
        val day = _uiState.value.selectedDate ?: return
        observeForDay(companyId, branchId, day)
    }

    fun selectDate(companyId: String, date: LocalDate) {
        if (date == _uiState.value.selectedDate) return
        _uiState.update { it.copy(selectedDate = date, sales = emptyList()) }
        val branch = _uiState.value.selectedBranchId.takeIf { it.isNotEmpty() } ?: return
        observeForDay(companyId, branch, date)
    }

    private fun observeForDay(companyId: String, branchId: String, date: LocalDate) {
        observeJob?.cancel()
        val tz = TimeZone.currentSystemDefault()
        val from = date.atStartOfDayIn(tz).toEpochMilliseconds()
        val until = from + 24L * 60 * 60 * 1000 - 1
        observeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            saleRepository.observeSalesForBranchInRange(companyId, branchId, from, until)
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { sales ->
                    _uiState.update { it.copy(isLoading = false, sales = sales) }
                }
        }
        // Sesiones del día (no en vivo, una sola lectura — el cierre cambia poco).
        viewModelScope.launch {
            runCatching {
                cashSessionRepository.getSessionsForBranch(companyId, branchId, from, until)
            }.onSuccess { sess -> _uiState.update { it.copy(sessions = sess) } }
        }
    }

    fun loadSaleDetail(companyId: String, saleId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, error = null) }
            runCatching {
                val sale = saleRepository.getSale(companyId, saleId)
                val lines = if (sale != null) saleRepository.getSaleLines(companyId, saleId) else emptyList()
                sale to lines
            }.onSuccess { (sale, lines) ->
                _uiState.update {
                    it.copy(isDetailLoading = false, selectedSale = sale, selectedSaleLines = lines)
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isDetailLoading = false, error = err.message) }
            }
        }
    }

    /** Líneas de cada venta del cobro, para el detalle (el cobro en sí sale de la lista en vivo). */
    fun loadOrderLines(companyId: String, saleIds: List<String>) {
        if (saleIds.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOrderLoading = true, error = null) }
            runCatching { saleIds.associateWith { id -> saleRepository.getSaleLines(companyId, id) } }
                .onSuccess { map -> _uiState.update { it.copy(isOrderLoading = false, orderLines = map) } }
                .onFailure { e -> _uiState.update { it.copy(isOrderLoading = false, error = e.message) } }
        }
    }

    /** Caja del cobro abierto: si ya cerró, el cobro no se puede anular. */
    fun loadOrderSession(companyId: String, sessionId: String) {
        if (sessionId.isBlank()) { _uiState.update { it.copy(orderSession = null) }; return }
        viewModelScope.launch {
            runCatching { cashSessionRepository.getSession(companyId, sessionId) }
                .onSuccess { s -> _uiState.update { it.copy(orderSession = s) } }
        }
    }

    /**
     * Anula el cobro entero (ver `SaleVoid.kt`).
     *
     * La factura va PRIMERO: si la SAT no la anula, no se toca nada y se puede reintentar. Si la
     * factura se anuló y algo de después falla, el reintento la encuentra ANULADA y sigue. Después
     * cada venta (guarda de estado, inventario, caja, resumen) y al final la bitácora y el aviso
     * al dueño, que no pueden tumbar una anulación que ya ocurrió.
     */
    fun voidOrder(companyId: String, order: com.bakery.erp.model.SaleOrder, reason: String, currentUser: User) {
        if (_uiState.value.isVoidBusy) return
        if (!ConnectionStatus.isOnline.value) {
            _uiState.update { it.copy(error = "Sin internet no se puede anular ventas. Esperá a que vuelva la conexión.") }
            return
        }
        val cleanReason = reason.trim()
        if (cleanReason.length < com.bakery.erp.model.MIN_SALE_VOID_REASON) {
            _uiState.update { it.copy(error = "Escribe el motivo de la anulación.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isVoidBusy = true, error = null) }
            val result = runCatching {
                val session = cashSessionRepository.getSession(companyId, order.primary.sessionId)
                com.bakery.erp.model.saleVoidBlocker(order, session)?.let { error(it) }
                val invoiced = order.sales.firstOrNull {
                    it.invoiceStatus == com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO && it.dteId.isNotBlank()
                }
                if (invoiced != null) {
                    runCatching { felRepository.voidInvoice(companyId, invoiced.dteId, "Venta anulada: $cleanReason") }
                        .onFailure { e -> error("No se pudo anular la factura, la venta no se tocó: ${readable(e, "")}") }
                }
                val request = com.bakery.erp.model.SaleVoidRequest(
                    reason = cleanReason,
                    userId = currentUser.id,
                    userName = currentUser.displayName,
                    employeeId = currentUser.operatorEmployeeId
                )
                val voided = order.sales.map { saleRepository.voidSale(companyId, it.id, request) }
                Triple(session!!, voided, invoiced != null)
            }
            result.onFailure { e ->
                _uiState.update { it.copy(isVoidBusy = false, error = readable(e, "No se pudo anular la venta.")) }
                return@launch
            }
            val (session, voided, hadInvoice) = result.getOrThrow()
            val total = voided.sumOf { it.total }
            val cash = voided.sumOf { it.voidCashAmount }
            val bank = voided.sumOf { it.bankRefundAmount }
            runCatching {
                cashAuditRepository.log(companyId, com.bakery.erp.model.CashAuditEvent(
                    type = com.bakery.erp.model.CashAuditEventType.SALE_VOID,
                    branchId = session.branchId,
                    branchName = session.branchName,
                    registerId = session.registerId,
                    registerName = session.registerName,
                    sessionId = session.id,
                    userId = currentUser.id,
                    userName = currentUser.displayName,
                    employeeName = session.openedByEmployeeName,
                    expectedCash = total,
                    countedCash = cash,
                    notes = cleanReason.take(500)
                ))
            }
            runCatching {
                val time = formatReceiptTimestamp(order.at)
                notificationRepository.push(companyId, com.bakery.erp.model.AppNotification(
                    type = com.bakery.erp.model.NotificationType.SALE_VOIDED,
                    title = "Venta anulada: Q ${total.fmtMoney(2)}",
                    body = "${session.branchName} · ${session.registerName} · ${currentUser.displayName} anuló el cobro de $time. " +
                        "Motivo: $cleanReason" +
                        (if (bank > 0.004) ". Devolver por banco Q ${bank.fmtMoney(2)}" else "") +
                        (if (hadInvoice) ". La factura se anuló" else ""),
                    targetBranchIds = listOf(session.branchId),
                    requiredPermission = com.bakery.erp.model.Permission.VIEW_CASH_AUDIT.name,
                    targetScreen = com.bakery.erp.model.NotificationTarget.CASH_AUDIT,
                    targetId = session.branchId,
                    createdByUserId = currentUser.id,
                    createdByUserName = currentUser.displayName
                ))
            }
            val notice = buildString {
                append("Venta anulada.")
                if (cash > 0.004) append(" Devuelve Q ${cash.fmtMoney(2)} en efectivo.")
                if (bank > 0.004) append(" Q ${bank.fmtMoney(2)} queda pendiente de devolver por banco.")
            }
            _uiState.update { it.copy(isVoidBusy = false, notice = notice) }
            loadOrderSession(companyId, session.id)
        }
    }

    /**
     * Corrige la forma de pago del cobro entero a [method] (ver `SalePaymentChange.kt`). Cada venta
     * del cobro por separado (pan y restaurante): si una falla, las ya corregidas quedan bien y el
     * reintento salta las que ya están en ese método. Bitácora y aviso al dueño al final.
     */
    fun changePaymentMethod(companyId: String, order: com.bakery.erp.model.SaleOrder, method: String, currentUser: User) {
        if (_uiState.value.isPaymentChangeBusy) return
        if (!ConnectionStatus.isOnline.value) {
            _uiState.update { it.copy(error = "Sin internet no se puede cambiar la forma de pago. Esperá a que vuelva la conexión.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isPaymentChangeBusy = true, error = null) }
            val result = runCatching {
                val session = cashSessionRepository.getSession(companyId, order.primary.sessionId)
                com.bakery.erp.model.paymentChangeBlocker(order, session)?.let { error(it) }
                if (!currentUser.hasPermission(com.bakery.erp.model.paymentChangePermission(session)))
                    error("No tienes permiso para corregir la forma de pago de esta caja.")
                order.sales
                    .filter { it.effectivePaymentBreakdown != it.withPaymentMethod(method).effectivePaymentBreakdown }
                    .forEach { saleRepository.changePaymentMethod(companyId, it.id, method, currentUser.displayName) }
                session!!
            }
            result.onFailure { e ->
                _uiState.update { it.copy(isPaymentChangeBusy = false, error = readable(e, "No se pudo cambiar la forma de pago.")) }
                return@launch
            }
            val session = result.getOrThrow()
            val from = com.bakery.erp.model.SalePaymentMethod.label(order.paymentMethod)
            val to = com.bakery.erp.model.SalePaymentMethod.label(method)
            val time = formatReceiptTimestamp(order.at)
            runCatching {
                cashAuditRepository.log(companyId, com.bakery.erp.model.CashAuditEvent(
                    type = com.bakery.erp.model.CashAuditEventType.PAYMENT_CHANGE,
                    branchId = session.branchId,
                    branchName = session.branchName,
                    registerId = session.registerId,
                    registerName = session.registerName,
                    sessionId = session.id,
                    userId = currentUser.id,
                    userName = currentUser.displayName,
                    employeeName = session.openedByEmployeeName,
                    expectedCash = order.total,
                    notes = "$from → $to · cobro de $time" +
                        if (session.status == com.bakery.erp.model.CashSessionStatus.CLOSED) " · caja ya cerrada" else ""
                ))
            }
            runCatching {
                notificationRepository.push(companyId, com.bakery.erp.model.AppNotification(
                    type = com.bakery.erp.model.NotificationType.SALE_PAYMENT_CHANGED,
                    title = "Forma de pago corregida: Q ${order.total.fmtMoney(2)}",
                    body = "${session.branchName} · ${session.registerName} · ${currentUser.displayName} cambió el cobro de " +
                        "$time de $from a $to.",
                    targetBranchIds = listOf(session.branchId),
                    requiredPermission = com.bakery.erp.model.Permission.VIEW_CASH_AUDIT.name,
                    targetScreen = com.bakery.erp.model.NotificationTarget.CASH_AUDIT,
                    targetId = session.branchId,
                    createdByUserId = currentUser.id,
                    createdByUserName = currentUser.displayName
                ))
            }
            _uiState.update { it.copy(isPaymentChangeBusy = false, notice = "Cobro cambiado a $to.") }
            loadOrderSession(companyId, session.id)
        }
    }

    /** La parte de tarjeta/transferencia de un cobro anulado ya se le devolvió al cliente. */
    fun markBankRefunded(companyId: String, order: com.bakery.erp.model.SaleOrder, currentUser: User) {
        if (_uiState.value.isVoidBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isVoidBusy = true, error = null) }
            runCatching {
                order.sales.filter { it.bankRefundPending }.forEach {
                    saleRepository.markBankRefunded(companyId, it.id, currentUser.displayName)
                }
            }.onSuccess { _uiState.update { it.copy(isVoidBusy = false, notice = "Marcado como devuelto por banco.") } }
                .onFailure { e -> _uiState.update { it.copy(isVoidBusy = false, error = readable(e, "No se pudo guardar.")) } }
        }
    }

    /**
     * Factura el cobro completo en UNA factura —el pan y la cuenta del restaurante juntos—, igual
     * que al cobrar. Solo entran las ventas sin factura: lo que ComandaYa ya facturó queda fuera.
     */
    fun invoiceOrder(companyId: String, order: com.bakery.erp.model.SaleOrder, to: com.bakery.erp.model.FelInvoiceRequest) {
        if (_uiState.value.isInvoiceBusy) return
        if (!ConnectionStatus.isOnline.value) {
            _uiState.update { it.copy(error = "Sin internet no se puede facturar desde el historial. Esperá a que vuelva la conexión.") }
            return
        }
        val ids = order.uninvoicedSales.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isInvoiceBusy = true, error = null) }
            runCatching { felRepository.certifySales(companyId, ids, to, reissue = false) }
                .onSuccess { r -> _uiState.update { it.copy(isInvoiceBusy = false, notice = reissueNotice(r)) } }
                .onFailure { e -> _uiState.update { it.copy(isInvoiceBusy = false, error = readable(e, "No se pudo facturar.")) } }
        }
    }

    /** Reimprime el cobro completo en un solo ticket, con la factura y el QR si la tiene. */
    fun printOrder(companyId: String, order: com.bakery.erp.model.SaleOrder) {
        if (_uiState.value.isPrinting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPrinting = true, error = null) }
            runCatching {
                val lines = _uiState.value.orderLines
                val primary = order.primary
                val branch = _uiState.value.branches.firstOrNull { it.id == primary.branchId }
                val org = organizationName ?: authRepository.getOrganization(companyId)
                    .map { it.name }.getOrDefault("").also { organizationName = it }
                // La cuenta del restaurante va en un renglón, como en el ticket del cobro.
                val receiptLines = order.sales.flatMap { s ->
                    if (s.isRestaurantSale)
                        listOf(com.bakery.erp.model.ReceiptLine(com.bakery.erp.model.restaurantSaleLabel(s), 1.0, s.total, s.total))
                    else receiptLinesOf(lines[s.id].orEmpty(), invoiced = s.invoiceStatus.isNotBlank())
                }
                val combined = primary.copy(
                    subtotal = order.sales.sumOf { it.subtotal },
                    discount = order.sales.sumOf { it.discount },
                    total = order.total,
                    paymentMethod = order.paymentMethod,
                    paymentBreakdown = if (order.paymentParts.size > 1) order.paymentParts else primary.paymentBreakdown
                )
                val base = saleReceiptData(
                    sale = combined,
                    lines = receiptLines,
                    branchName = branch?.name ?: primary.branchName,
                    branchLocation = branch?.location.orEmpty(),
                    organizationName = org
                )
                val invoicedSale = order.invoicedSale
                val invoice = if (invoicedSale?.invoiceStatus == com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO)
                    felRepository.getInvoice(companyId, invoicedSale.dteId)
                        ?.takeIf { it.status == com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO }
                else null
                val data = if (invoice == null) base
                else base.copy(invoice = com.bakery.erp.model.receiptInvoiceFrom(invoice, formatReceiptTimestamp(invoice.certifiedAt)))
                receiptPrinter.print(data).getOrThrow()
            }.onSuccess {
                _uiState.update { it.copy(isPrinting = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isPrinting = false, error = e.message ?: "No se pudo imprimir.") }
            }
        }
    }

    fun clearSelected() {
        _uiState.update { it.copy(selectedSale = null, selectedSaleLines = emptyList()) }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearNotice() { _uiState.update { it.copy(notice = null) } }

    /**
     * Anula la factura de [sale] y, si viene [reissueTo], emite otra con esos datos. Las dos cosas
     * en ese orden y por separado: si la anulación sale y la nueva falla, lo anulado ya es un
     * hecho ante la SAT y la venta queda para "Facturar de nuevo", en vez de revertir algo que
     * no se puede revertir.
     */
    fun voidInvoice(companyId: String, sale: Sale, reason: String, reissueTo: com.bakery.erp.model.FelInvoiceRequest?) {
        if (_uiState.value.isInvoiceBusy) return
        if (!ConnectionStatus.isOnline.value) {
            _uiState.update { it.copy(error = "Sin internet no se puede anular facturas. Esperá a que vuelva la conexión.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isInvoiceBusy = true, error = null) }
            val voided = runCatching { felRepository.voidInvoice(companyId, sale.dteId, reason) }
            voided.onFailure { e ->
                _uiState.update { it.copy(isInvoiceBusy = false, error = readable(e, "No se pudo anular la factura.")) }
                return@launch
            }
            val notice = if (reissueTo == null) "Factura anulada."
            else runCatching { felRepository.certifySales(companyId, listOf(sale.id), reissueTo, reissue = true) }
                .fold(
                    onSuccess = { r -> reissueNotice(r) },
                    onFailure = { e -> "Factura anulada, pero la nueva no salió: ${readable(e, "")}. Usá \"Facturar de nuevo\"." }
                )
            _uiState.update { it.copy(isInvoiceBusy = false, notice = notice) }
            loadSaleDetail(companyId, sale.id)
        }
    }

    /**
     * Factura una venta desde el historial. Sin factura previa es la primera emisión (el cliente
     * la pidió después de cobrar); con una anulada o rechazada es volver a facturar.
     */
    fun reissueInvoice(companyId: String, sale: Sale, to: com.bakery.erp.model.FelInvoiceRequest) {
        if (_uiState.value.isInvoiceBusy) return
        if (!ConnectionStatus.isOnline.value) {
            _uiState.update { it.copy(error = "Sin internet no se puede facturar desde el historial. Esperá a que vuelva la conexión.") }
            return
        }
        val reissue = sale.invoiceStatus.isNotBlank()
        viewModelScope.launch {
            _uiState.update { it.copy(isInvoiceBusy = true, error = null) }
            // Solo esta venta: al reemitir, el servidor suma las demás que cubría la anterior.
            runCatching { felRepository.certifySales(companyId, listOf(sale.id), to, reissue = reissue) }
                .onSuccess { r -> _uiState.update { it.copy(isInvoiceBusy = false, notice = reissueNotice(r)) } }
                .onFailure { e -> _uiState.update { it.copy(isInvoiceBusy = false, error = readable(e, "No se pudo facturar.")) } }
            loadSaleDetail(companyId, sale.id)
        }
    }

    /**
     * Reimprime el ticket de la venta. Si está facturada, con la factura y el QR: se leen del
     * documento guardado, sin volver a llamar a Digifact.
     */
    fun printSale(companyId: String, sale: Sale) {
        if (_uiState.value.isPrinting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPrinting = true, error = null) }
            runCatching {
                val branch = _uiState.value.branches.firstOrNull { it.id == sale.branchId }
                val org = organizationName ?: authRepository.getOrganization(companyId)
                    .map { it.name }.getOrDefault("").also { organizationName = it }
                val base = saleReceiptData(
                    sale = sale,
                    lines = receiptLinesOf(_uiState.value.selectedSaleLines, invoiced = sale.invoiceStatus.isNotBlank()),
                    branchName = branch?.name ?: sale.branchName,
                    branchLocation = branch?.location.orEmpty(),
                    organizationName = org
                )
                val invoice = if (sale.invoiceStatus == com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO)
                    felRepository.getInvoice(companyId, sale.dteId)
                        ?.takeIf { it.status == com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO }
                else null
                val data = if (invoice == null) base
                else base.copy(invoice = com.bakery.erp.model.receiptInvoiceFrom(invoice, formatReceiptTimestamp(invoice.certifiedAt)))
                receiptPrinter.print(data).getOrThrow()
            }.onSuccess {
                _uiState.update { it.copy(isPrinting = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isPrinting = false, error = e.message ?: "No se pudo imprimir.") }
            }
        }
    }

    /** NIT en la SAT, para confirmar a quién se le factura. */
    suspend fun lookupNit(companyId: String, branchId: String, nit: String): Result<String?> =
        runCatching { felRepository.lookupNit(companyId, branchId, nit) }

    private fun reissueNotice(r: com.bakery.erp.model.FelCertifyResult): String = when (r.status) {
        com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO -> "Nueva factura serie ${r.serie} · núm. ${r.numero}."
        com.bakery.erp.model.FelInvoiceStatus.PENDIENTE -> "La nueva factura quedó pendiente: se completa sola."
        else -> "La nueva factura no se emitió: ${r.message}"
    }

    private fun readable(e: Throwable, fallback: String): String =
        (e.message ?: fallback).replaceFirst(Regex("^[A-Z_]+:\\s*"), "").ifBlank { fallback }
}
