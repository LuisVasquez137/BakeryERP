package com.bakery.erp.presentation.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.CashSessionRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.POSCategoryRepository
import com.bakery.erp.domain.PriceListRepository
import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.domain.ReceiptPrinter
import com.bakery.erp.domain.SaleRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.FinishedProductStock
import com.bakery.erp.model.isIngredientBacked
import com.bakery.erp.model.POSCategory
import com.bakery.erp.model.PriceListType
import com.bakery.erp.model.Product
import com.bakery.erp.model.ReceiptData
import com.bakery.erp.model.ReceiptLine
import com.bakery.erp.model.Sale
import com.bakery.erp.model.SaleLine
import com.bakery.erp.model.SaleLinePriceSource
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.User
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Línea del carrito antes de confirmar la venta. Vive solo en memoria del ViewModel.
 * Cuando se confirma se convierte en [SaleLine]. Siempre hay un [productId] concreto —
 * la dualidad EXPRESS/DETAILED del diseño anterior fue eliminada.
 */
data class CartLine(
    val key: String,                    // único en el carrito
    val productId: String,
    val productName: String,
    val quantity: Double = 0.0,
    val unitPrice: Double = 0.0,
    val priceListId: String = "",
    val priceSource: String = SaleLinePriceSource.PRODUCT_SALE_PRICE
) {
    val subtotal: Double get() = quantity * unitPrice
}

data class POSUiState(
    val branch: Branch? = null,
    val session: CashSession? = null,
    val products: List<Product> = emptyList(),
    val productsById: Map<String, Product> = emptyMap(),
    val stocksByProductId: Map<String, FinishedProductStock> = emptyMap(),
    /**
     * Existencias de insumos de la sucursal, para los productos respaldados por uno.
     * Se carga una vez al abrir la caja —una sola consulta por sucursal— en vez de
     * observarse: el aviso de sobreventa es orientativo y no justifica un listener más.
     */
    val ingredientStockByIngredientId: Map<String, Double> = emptyMap(),
    val posCategories: List<POSCategory> = emptyList(),
    /** Total de categorías que existen en la empresa (sin filtrar por sucursal) — para diagnóstico. */
    val totalCategoriesCount: Int = 0,
    val cart: List<CartLine> = emptyList(),
    val isLoading: Boolean = false,
    val isPrinting: Boolean = false,
    val error: String? = null,
    val lastSaleSummary: LastSaleSummary? = null,
    /** Datos del ticket de la venta recién confirmada — para el diálogo de éxito + impresión. */
    val lastReceiptData: ReceiptData? = null,
    val isPrintingAvailable: Boolean = false,
    /**
     * Cuentas del restaurante ya cobradas al cliente cuyo efectivo todavía no entró a
     * ninguna gaveta. Todas las cajas de la sucursal ven la misma lista.
     */
    val pendingCollections: List<Sale> = emptyList(),
    val isCollecting: Boolean = false,
    /** Cuenta recién recibida — para el acuse; se limpia al cerrarlo. */
    val justCollected: Sale? = null
)

data class LastSaleSummary(
    val total: Double,
    val change: Double,
    val method: String
)

class POSViewModel(
    private val saleRepository: SaleRepository,
    private val posCategoryRepository: POSCategoryRepository,
    private val cashSessionRepository: CashSessionRepository,
    private val productRepository: ProductRepository,
    private val finishedStockRepository: FinishedStockRepository,
    // Para los productos que venden del inventario de insumos (el huevo por docena).
    private val ingredientRepository: IngredientRepository,
    private val priceListRepository: PriceListRepository,
    private val branchRepository: BranchRepository,
    private val receiptPrinter: ReceiptPrinter,
    private val authRepository: AuthRepository
) : ViewModel() {

    private var organizationName: String = ""

    private val _uiState = MutableStateFlow(POSUiState(isPrintingAvailable = receiptPrinter.isAvailable))
    val uiState: StateFlow<POSUiState> = _uiState.asStateFlow()

    private var stockObserveJob: Job? = null
    private var categoriesObserveJob: Job? = null
    private var sessionObserveJob: Job? = null
    private var pendingObserveJob: Job? = null
    private var retailPriceListId: String = ""
    private var retailPriceFactor: Double? = null

    /** Punto de entrada: carga branch, productos, listas y arranca observers de la sesión. */
    fun load(companyId: String, sessionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val session = cashSessionRepository.getSession(companyId, sessionId)
                    ?: throw IllegalStateException("Sesión no encontrada: $sessionId")
                val branch = branchRepository.getBranch(companyId, session.branchId)
                    ?: throw IllegalStateException("Sucursal no encontrada: ${session.branchId}")
                val products = productRepository.getProducts(companyId)
                    .filter { it.active }
                    .sortedBy { it.name }

                // Lista de precios efectiva para esta sucursal (RETAIL).
                val branchListId = branch.defaultPriceListId
                val list = (branchListId.takeIf { it.isNotEmpty() }
                    ?.let { priceListRepository.getList(companyId, it) }
                    ?.takeIf { it.active }
                    ) ?: priceListRepository.getDefaultList(companyId, PriceListType.RETAIL)
                retailPriceListId = list?.id.orEmpty()
                retailPriceFactor = list?.priceFactor

                // Total de categorías existentes — para empty state diagnóstico.
                val totalCategoriesCount = posCategoryRepository.getCategories(companyId).size

                // Nombre comercial — usado como cabecera del ticket impreso.
                organizationName = authRepository.getOrganization(companyId)
                    .map { it.name }
                    .getOrDefault("")

                LoadResult(session, branch, products, totalCategoriesCount)
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        session = result.session,
                        branch = result.branch,
                        products = result.products,
                        productsById = result.products.associateBy { p -> p.id },
                        totalCategoriesCount = result.totalCategoriesCount
                    )
                }
                observeStocks(companyId, result.session.branchId)
                loadIngredientStock(companyId, result.session.branchId, result.products)
                observePOSCategories(companyId, result.session.branchId)
                observeSession(companyId, result.session.id)
                observePendingCollections(companyId, result.session.branchId)
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    private data class LoadResult(
        val session: CashSession,
        val branch: Branch,
        val products: List<Product>,
        val totalCategoriesCount: Int
    )

    private fun observeStocks(companyId: String, branchId: String) {
        stockObserveJob?.cancel()
        stockObserveJob = viewModelScope.launch {
            finishedStockRepository.observeStocksForBranch(companyId, branchId)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { list ->
                    _uiState.update { it.copy(stocksByProductId = list.associateBy { s -> s.productId }) }
                }
        }
    }

    /**
     * Existencias de insumos de la sucursal, solo si algún producto vendible se respalda en
     * uno. Sin productos así no se gasta la consulta — que es el caso de casi toda panadería.
     */
    private fun loadIngredientStock(companyId: String, branchId: String, products: List<Product>) {
        if (products.none { it.isIngredientBacked }) return
        viewModelScope.launch {
            // Una sola consulta por sucursal devuelve todo el mapa insumo → existencia.
            runCatching { ingredientRepository.getBranchStocksForBranch(companyId, branchId) }
                .onSuccess { stocks ->
                    _uiState.update { it.copy(ingredientStockByIngredientId = stocks) }
                }
        }
    }

    private fun observePOSCategories(companyId: String, branchId: String) {
        categoriesObserveJob?.cancel()
        categoriesObserveJob = viewModelScope.launch {
            posCategoryRepository.observeActiveForBranch(companyId, branchId)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { categories -> _uiState.update { it.copy(posCategories = categories) } }
        }
    }

    private fun observeSession(companyId: String, sessionId: String) {
        sessionObserveJob?.cancel()
        sessionObserveJob = viewModelScope.launch {
            cashSessionRepository.observeSession(companyId, sessionId)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { session -> _uiState.update { it.copy(session = session) } }
        }
    }

    /**
     * Cuentas del restaurante pendientes de entregar a caja, en vivo. En una sucursal sin
     * restaurante la lista simplemente nunca trae nada y no se muestra el botón.
     */
    private fun observePendingCollections(companyId: String, branchId: String) {
        pendingObserveJob?.cancel()
        // Solo las de HOY. Una cuenta que nadie entregó ayer no puede seguir apareciendo:
        // el día siguiente arranca de cero. El rango se fija al abrir la caja — una sesión
        // que cruce la medianoche seguirá mostrando el día en que se abrió, que es lo que
        // espera quien la está atendiendo.
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val from = today.atStartOfDayIn(tz).toEpochMilliseconds()
        val until = today.plus(DatePeriod(days = 1)).atStartOfDayIn(tz).toEpochMilliseconds()
        pendingObserveJob = viewModelScope.launch {
            saleRepository.observePendingCollections(companyId, branchId, from, until)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { list -> _uiState.update { it.copy(pendingCollections = list) } }
        }
    }

    /**
     * El mesero entrega el efectivo y esta caja lo recibe.
     *
     * No es un segundo cobro: el cliente ya pagó en ComandaYa. Lo que se registra acá es que
     * el dinero cambió de manos y ya está en ESTA gaveta — el único dato que ningún
     * algoritmo puede deducir cuando la sucursal tiene dos cajas y cualquiera puede recibir.
     */
    fun collectPendingSale(companyId: String, sale: Sale, currentUser: User) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isCollecting = true, error = null) }
            runCatching {
                saleRepository.collectPendingSale(
                    companyId = companyId,
                    saleId = sale.id,
                    sessionId = session.id,
                    registerId = session.registerId,
                    registerName = session.registerName,
                    cashierId = currentUser.id,
                    cashierName = currentUser.displayName
                )
            }.onSuccess { collected ->
                // La fila desaparece sola: el listener ya no la trae porque dejó de estar
                // pendiente. No hace falta quitarla a mano.
                _uiState.update { it.copy(isCollecting = false, justCollected = collected) }
            }.onFailure { e ->
                _uiState.update { it.copy(isCollecting = false, error = e.message) }
            }
        }
    }

    fun clearJustCollected() { _uiState.update { it.copy(justCollected = null) } }

    // ── Carrito ───────────────────────────────────────────────────────────

    fun addProductToCart(product: Product, quantity: Double) {
        val unitPrice = resolveProductPrice(product)
        val existing = _uiState.value.cart.find { it.productId == product.id }
        val newCart = if (existing != null) {
            _uiState.value.cart.map {
                if (it.key == existing.key) it.copy(quantity = it.quantity + quantity) else it
            }
        } else {
            _uiState.value.cart + CartLine(
                key = "prod_${product.id}_${Clock.System.now().toEpochMilliseconds()}",
                productId = product.id,
                productName = product.name,
                quantity = quantity,
                unitPrice = unitPrice.first,
                priceListId = retailPriceListId,
                priceSource = unitPrice.second
            )
        }
        _uiState.update { it.copy(cart = newCart) }
    }

    fun updateLineQuantity(key: String, newQuantity: Double) {
        if (newQuantity <= 0) {
            removeLine(key); return
        }
        val newCart = _uiState.value.cart.map {
            if (it.key == key) it.copy(quantity = newQuantity) else it
        }
        _uiState.update { it.copy(cart = newCart) }
    }

    fun removeLine(key: String) {
        _uiState.update { it.copy(cart = it.cart.filter { l -> l.key != key }) }
    }

    fun clearCart() { _uiState.update { it.copy(cart = emptyList()) } }

    /** Subtotal y total — sin descuento ni impuestos en MVP. */
    fun cartTotal(): Double = _uiState.value.cart.sumOf { it.subtotal }

    // ── Confirmar venta ───────────────────────────────────────────────────

    fun confirmSale(
        companyId: String,
        currentUser: User,
        paymentMethod: String,
        tenderedAmount: Double
    ) {
        val state = _uiState.value
        val session = state.session ?: run {
            _uiState.update { it.copy(error = "Sesión inválida") }; return
        }
        val branch = state.branch ?: run {
            _uiState.update { it.copy(error = "Sucursal inválida") }; return
        }
        if (state.cart.isEmpty()) {
            _uiState.update { it.copy(error = "El carrito está vacío") }; return
        }
        val total = cartTotal()
        val effectiveTendered = if (paymentMethod == SalePaymentMethod.CASH) tenderedAmount else total
        if (effectiveTendered < total) {
            _uiState.update { it.copy(error = "Monto recibido menor al total") }; return
        }
        val change = if (paymentMethod == SalePaymentMethod.CASH) effectiveTendered - total else 0.0

        val snapshotCart = state.cart
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                // Revalidar la sesión contra Firestore antes de escribir: debe seguir OPEN y ser
                // del cajero actual. Evita registrar ventas contra una sesión ya cerrada por un
                // cuadre / cierre concurrente o abierta por otro cajero.
                val freshSession = cashSessionRepository.getSession(companyId, session.id)
                    ?: throw IllegalStateException("La sesión de caja ya no existe.")
                if (freshSession.status != com.bakery.erp.model.CashSessionStatus.OPEN) {
                    throw IllegalStateException("La caja fue cerrada. No se puede registrar la venta.")
                }
                if (freshSession.cashierId != currentUser.id) {
                    throw IllegalStateException("Esta sesión de caja pertenece a ${freshSession.cashierName}.")
                }
                val sale = Sale(
                    branchId = branch.id,
                    branchName = branch.name,
                    registerId = session.registerId,
                    registerName = session.registerName,
                    sessionId = session.id,
                    cashierId = currentUser.id,
                    cashierName = currentUser.displayName,
                    subtotal = total,
                    discount = 0.0,
                    total = total,
                    paymentMethod = paymentMethod,
                    tenderedAmount = effectiveTendered,
                    changeAmount = change
                )
                val lines = snapshotCart.map { line ->
                    // El vínculo con el insumo se snapshotea acá, donde el catálogo está a
                    // mano: así el repositorio de ventas decide a qué inventario pegarle sin
                    // conocer productos, y la venta sigue diciendo de dónde salió aunque
                    // mañana se desvincule el producto.
                    val product = _uiState.value.productsById[line.productId]
                    SaleLine(
                        productId = line.productId,
                        productName = line.productName,
                        quantity = line.quantity,
                        unitPrice = line.unitPrice,
                        subtotal = line.subtotal,
                        priceListId = line.priceListId,
                        priceSource = line.priceSource,
                        // stockDeducted lo decide el repo al persistir
                        stockDeducted = false,
                        linkedIngredientId = product?.linkedIngredientId.orEmpty(),
                        linkedIngredientName = product?.linkedIngredientName.orEmpty(),
                        ingredientUnitsPerSale = product?.ingredientUnitsPerSale ?: 0.0,
                        // El respaldado por insumo no tiene `avgUnitCost` que consultar: su
                        // costo de referencia es el de compra del producto.
                        costAtSale = if (product?.isIngredientBacked == true) {
                            product.purchaseCost ?: 0.0
                        } else 0.0
                    )
                }
                saleRepository.createSale(companyId, sale, lines)
            }.onSuccess { sale ->
                val receipt = buildReceiptData(sale, snapshotCart, branch)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        cart = emptyList(),
                        lastSaleSummary = LastSaleSummary(sale.total, sale.changeAmount, sale.paymentMethod),
                        lastReceiptData = receipt
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    /** Dispara la impresión del último ticket. Reporta errores vía `error` en el state. */
    fun printLastReceipt() {
        val data = _uiState.value.lastReceiptData ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isPrinting = true) }
            receiptPrinter.print(data)
                .onFailure { e ->
                    _uiState.update { it.copy(isPrinting = false, error = e.message ?: "Error al imprimir") }
                }
                .onSuccess {
                    _uiState.update { it.copy(isPrinting = false) }
                }
        }
    }

    /** Cierra el diálogo de éxito y descarta el último ticket. */
    fun dismissLastSale() {
        _uiState.update { it.copy(lastReceiptData = null, lastSaleSummary = null) }
    }

    fun clearLastSaleSummary() { _uiState.update { it.copy(lastSaleSummary = null) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }

    private fun buildReceiptData(sale: Sale, lines: List<CartLine>, branch: Branch): ReceiptData {
        val createdMillis = if (sale.createdAt > 0L) sale.createdAt
                            else Clock.System.now().toEpochMilliseconds()
        // Cabecera: nombre comercial (org) en grande, abajo "Sucursal: X" + dirección si hay.
        val header = organizationName.ifEmpty { branch.name.ifEmpty { "Punto de Venta" } }
        val subHeader = buildString {
            if (organizationName.isNotEmpty() && branch.name.isNotEmpty()) {
                append("Sucursal: ").append(branch.name)
            }
            if (branch.location.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append(branch.location)
            }
        }
        return ReceiptData(
            companyName = header,
            branchName = subHeader,
            saleId = sale.id.takeLast(8).ifEmpty { "—" },
            dateLabel = formatTimestamp(createdMillis),
            cashierName = sale.cashierName,
            registerName = sale.registerName,
            lines = lines.map { l ->
                ReceiptLine(
                    name = l.productName,
                    quantity = l.quantity,
                    unitPrice = l.unitPrice,
                    subtotal = l.subtotal
                )
            },
            subtotal = sale.subtotal,
            discount = sale.discount,
            total = sale.total,
            paymentMethodLabel = SalePaymentMethod.label(sale.paymentMethod),
            tenderedAmount = sale.tenderedAmount,
            changeAmount = sale.changeAmount
        )
    }

    @Suppress("DEPRECATION")
    private fun formatTimestamp(millis: Long): String {
        val instant = Instant.fromEpochMilliseconds(millis)
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val day = local.dayOfMonth.toString().padStart(2, '0')
        val month = local.monthNumber.toString().padStart(2, '0')
        val hour = local.hour.toString().padStart(2, '0')
        val minute = local.minute.toString().padStart(2, '0')
        return "$day/$month/${local.year} $hour:$minute"
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Devuelve (precio, source). `salePrice` es la fuente de verdad para retail. */
    private fun resolveProductPrice(product: Product): Pair<Double, String> {
        return product.salePrice to SaleLinePriceSource.PRODUCT_SALE_PRICE
    }

    /**
     * Stock disponible para un producto en la sucursal actual. 0 si no hay registro.
     *
     * Un producto respaldado por un insumo (el huevo) no tiene inventario de terminado: su
     * existencia es la del insumo, convertida a piezas vendibles. Sin esta rama el aviso de
     * sobreventa marcaría en rojo TODA venta de huevo, y un aviso que siempre suena deja
     * de avisar.
     */
    fun stockOf(productId: String): Double {
        val product = _uiState.value.productsById[productId]
        if (product != null && product.isIngredientBacked) {
            val available = _uiState.value.ingredientStockByIngredientId[product.linkedIngredientId] ?: 0.0
            val perSale = product.ingredientUnitsPerSale.takeIf { it > 0.0 } ?: 1.0
            return available / perSale
        }
        return _uiState.value.stocksByProductId[productId]?.currentStock ?: 0.0
    }

    /**
     * Líneas del carrito cuya cantidad supera el stock disponible en la sucursal.
     * Se usa para advertir (no bloquea la venta): en panadería el conteo físico puede
     * diferir del sistema, así que se permite vender aunque el stock quede negativo.
     */
    fun oversoldLines(): List<CartLine> =
        _uiState.value.cart.filter { it.quantity > stockOf(it.productId) }
}
