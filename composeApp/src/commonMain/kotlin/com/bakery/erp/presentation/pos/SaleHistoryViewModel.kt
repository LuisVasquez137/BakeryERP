package com.bakery.erp.presentation.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    /**
     * Abierta desde una caja ("Ventas de esta caja"): sucursal fija, la caja propia marcada y
     * los montos visibles para quien cobra, que los necesita para encontrar la venta.
     */
    val posRegisterId: String = "",
    /** Líneas por venta del cobro abierto en el detalle. */
    val orderLines: Map<String, List<SaleLine>> = emptyMap(),
    val isOrderLoading: Boolean = false
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
    private val authRepository: com.bakery.erp.domain.AuthRepository
) : ViewModel() {

    val canPrint: Boolean get() = receiptPrinter.isAvailable
    private var organizationName: String? = null

    private val _uiState = MutableStateFlow(SaleHistoryUiState())
    val uiState: StateFlow<SaleHistoryUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    fun load(companyId: String, currentUser: User) {
        // Desde el Home: todas las cajas, sin la caja propia de una sesión anterior.
        _uiState.update {
            it.copy(posRegisterId = "", registerFilter = "", invoiceFilter = com.bakery.erp.model.OrderInvoiceFilter.ALL, query = "")
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
                invoiceFilter = com.bakery.erp.model.OrderInvoiceFilter.ALL, query = "",
                selectedBranchId = branchId, selectedDate = today
            )
        }
        loadBranches(companyId, currentUser, forcedBranchId = branchId)
    }

    fun setRegisterFilter(registerId: String) { _uiState.update { it.copy(registerFilter = registerId) } }
    fun setInvoiceFilter(f: com.bakery.erp.model.OrderInvoiceFilter) { _uiState.update { it.copy(invoiceFilter = f) } }
    fun setQuery(q: String) { _uiState.update { it.copy(query = q) } }

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

    /**
     * Factura el cobro completo en UNA factura —el pan y la cuenta del restaurante juntos—, igual
     * que al cobrar. Solo entran las ventas sin factura: lo que ComandaYa ya facturó queda fuera.
     */
    fun invoiceOrder(companyId: String, order: com.bakery.erp.model.SaleOrder, to: com.bakery.erp.model.FelInvoiceRequest) {
        if (_uiState.value.isInvoiceBusy) return
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
