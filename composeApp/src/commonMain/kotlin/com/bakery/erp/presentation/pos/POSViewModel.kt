package com.bakery.erp.presentation.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.CashSessionRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.POSCategoryRepository
import com.bakery.erp.domain.PackagingItemRepository
import com.bakery.erp.domain.PriceListRepository
import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.domain.ReceiptPrinter
import com.bakery.erp.domain.SaleRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.isOperatedBy
import com.bakery.erp.model.expectedCashNow
import com.bakery.erp.model.CheckoutPayment
import com.bakery.erp.model.CheckoutRecord
import com.bakery.erp.model.checkoutsFromSales
import com.bakery.erp.model.FinishedProductStock
import com.bakery.erp.model.isIngredientBacked
import com.bakery.erp.model.POSCategory
import com.bakery.erp.model.PackagingItem
import com.bakery.erp.model.groupDisplayName
import com.bakery.erp.model.PriceList
import com.bakery.erp.model.PriceListType
import com.bakery.erp.model.Product
import com.bakery.erp.model.ReceiptData
import com.bakery.erp.model.ReceiptLine
import com.bakery.erp.model.Sale
import com.bakery.erp.model.SaleLine
import com.bakery.erp.model.SaleLinePriceSource
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.User
import com.bakery.erp.util.fmtMoney
import com.bakery.erp.util.fmtTrim
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
import kotlinx.coroutines.withTimeoutOrNull
import com.bakery.erp.model.FelCertifyResult
import com.bakery.erp.model.ExternalCustomer
import com.bakery.erp.model.ExternalCustomerType
import com.bakery.erp.model.ResellerRegistrationStatus
import com.bakery.erp.domain.ExternalCustomerRepository
import com.bakery.erp.domain.PriceSource
import com.bakery.erp.domain.ResolvedPrice
import com.bakery.erp.domain.groupListPrice
import com.bakery.erp.domain.resolveListPrices
import com.bakery.erp.model.FelInvoiceRequest
import com.bakery.erp.model.FelInvoiceStatus
import com.bakery.erp.model.felReceptorProblem
import com.bakery.erp.model.acceptsDeposit

/**
 * Línea del carrito antes de confirmar la venta. Vive solo en memoria del ViewModel.
 * Cuando se confirma se convierte en [SaleLine]. Vende un producto concreto ([productId]) o
 * un grupo de precio ([quickGroupId], "Pan Q0.50"): en ése [productId] va vacío y el
 * inventario lo reparte el conteo de cierre.
 */
data class CartLine(
    val key: String,                    // único en el carrito
    val productId: String,
    val productName: String,
    val quantity: Double = 0.0,
    val unitPrice: Double = 0.0,
    val priceListId: String = "",
    val priceSource: String = SaleLinePriceSource.PRODUCT_SALE_PRICE,
    val quickGroupId: String = "",
    /**
     * Cuenta del restaurante (ComandaYa) que el cliente paga junto con su pan. No es una
     * línea de venta nueva: al cobrar, esa venta ya existente se da por recibida en esta caja.
     */
    val pendingSaleId: String = "",
    /** Empaque cobrado (la bolsa): sin producto ni inventario. Ver [PackagingItem]. */
    val packagingItemId: String = ""
) {
    val subtotal: Double get() = quantity * unitPrice
    val isGroup: Boolean get() = quickGroupId.isNotEmpty()
    val isRestaurant: Boolean get() = pendingSaleId.isNotEmpty()
    val isPackaging: Boolean get() = packagingItemId.isNotEmpty()
    /** Tiene un producto con existencia contra la cual avisar sobreventa. */
    val hasProductStock: Boolean get() = !isGroup && !isRestaurant && !isPackaging
    /** Descuenta o mueve algo del mostrador (no es una cuenta del restaurante). */
    val isBakery: Boolean get() = !isRestaurant
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
    /**
     * Empaques que se cobran (bolsa, caja), activos. Vacío en la panadería que no cobra la
     * bolsa: entonces el POS no muestra los botones.
     */
    val packagingItems: List<PackagingItem> = emptyList(),
    /** Total de categorías que existen en la empresa (sin filtrar por sucursal) — para diagnóstico. */
    val totalCategoriesCount: Int = 0,
    val cart: List<CartLine> = emptyList(),
    /**
     * Unidades vendidas en la sucursal los últimos [POPULARITY_DAYS] días, por productId y por
     * `groupSaleKey` del grupo de precio. Ordena "Todos": lo que más se pide va primero, que es
     * lo que la cajera busca con la fila esperando.
     */
    val popularityByKey: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = false,
    /**
     * Solo durante el COBRO, no durante la carga de la pantalla.
     *
     * `isLoading` sube también en `load()`, que corre cada vez que se vuelve al POS —por
     * ejemplo al terminar de anotar un gasto—, y el diálogo colgado de él anunciaba
     * "Procesando venta… descontando inventario" cuando no se estaba vendiendo nada.
     */
    val isCheckingOut: Boolean = false,
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
    /**
     * Últimos cobros de esta caja, el más reciente primero. Para "Último cobro": cuánto se
     * cobró, con qué billetes pagaron y cuánto vuelto se dio.
     */
    val recentCheckouts: List<CheckoutRecord> = emptyList(),
    val isLoadingCheckouts: Boolean = false,
    /** Aviso de algo que salió bien (abono cobrado, adelanto entregado) para el snackbar. */
    val notice: String? = null,
    val isCreditBusy: Boolean = false,
    /** Cuenta recién recibida — para el acuse; se limpia al cerrarlo. */
    val justCollected: Sale? = null,
    /** Factura de la venta recién cobrada, para el diálogo de éxito. Null = no se facturó. */
    val invoiceProgress: InvoiceProgress? = null,
    /** Anticipos de clientes con saldo, de todas las sucursales (se cargan al abrir el cobro). */
    val openDeposits: List<com.bakery.erp.model.CustomerDeposit> = emptyList(),
    val isDepositBusy: Boolean = false,
    /** Comprobante por imprimir (recién recibido o reimpresión). La pantalla lo manda y lo limpia. */
    val depositToPrint: com.bakery.erp.model.CustomerDeposit? = null,
    /** Pedidos de clientes con saldo, para ligarles el anticipo al recibirlo. */
    val depositOrders: List<com.bakery.erp.model.CustomerOrder> = emptyList(),
    /** Cambia cada vez que se guarda un anticipo: la pantalla cierra el diálogo al verlo. */
    val depositSavedAt: Long = 0L,
    /** Sucursales que venden: dónde puede recoger el cliente su encargo. */
    val pickupBranches: List<Branch> = emptyList(),
    /** Cuándo terminó de imprimirse el último ticket: el acuse muestra "Ticket impreso". */
    val printedAt: Long = 0L,
    /**
     * Venta a revendedor en curso: el carrito se cobra con SU lista de precios en vez del
     * precio público. Null = venta de mostrador. Ver [ResellerSale].
     */
    val resellerSale: ResellerSale? = null,
    /** Revendedores activos, para elegir a quién se le vende. Se cargan al abrir el selector. */
    val resellers: List<ExternalCustomer> = emptyList(),
    val isLoadingResellers: Boolean = false
)

/**
 * El revendedor al que se le está vendiendo y sus precios ya resueltos para todo el catálogo.
 *
 * Se resuelven al elegirlo —una lectura de la lista y sus precios fijos— y no producto por
 * producto al tocar cada ficha: la cajera tiene la fila esperando, y así cada ficha puede
 * mostrar el precio de revendedor antes de tocarla.
 */
data class ResellerSale(
    val customer: ExternalCustomer,
    val priceListId: String,
    val priceListName: String,
    /** La lista elegida: el grupo de precio sin panes resuelve su precio con su regla. */
    val list: PriceList,
    val prices: Map<String, ResolvedPrice>
)

/**
 * Cómo va la factura de la venta recién cobrada. [saleId] ata el resultado a SU venta: si la
 * cajera ya cobró la siguiente cuando Digifact contesta, la respuesta vieja no puede aparecer
 * en el acuse de la nueva.
 */
data class InvoiceProgress(
    val saleId: String,
    val status: String,
    val message: String = "",
    val result: FelCertifyResult? = null
)

/** A qué cuenta se carga una venta a crédito y quién se lleva la mercadería. */
data class CreditCharge(
    val account: com.bakery.erp.model.CreditAccount,
    val personId: String = "",
    val personName: String = ""
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
    private val authRepository: AuthRepository,
    /** Ventas a crédito, abonos cobrados en caja y adelantos a colaboradores. */
    private val creditRepository: com.bakery.erp.domain.CreditRepository,
    /** Facturación electrónica: certificar al cobrar y consultar NIT. */
    private val felRepository: com.bakery.erp.domain.FelRepository,
    /** Anticipos de clientes: recibir, aplicar al cobrar y devolver. */
    private val depositRepository: com.bakery.erp.domain.CustomerDepositRepository,
    /** Pedidos de clientes (pasteles): el anticipo ligado se registra como pago del pedido. */
    private val customerOrderRepository: com.bakery.erp.domain.CustomerOrderRepository,
    /** Revendedores: a quién se le vende con su lista de precios. */
    private val externalCustomerRepository: ExternalCustomerRepository,
    /** Empaques que se cobran en caja (la bolsa). */
    private val packagingItemRepository: PackagingItemRepository
) : ViewModel() {

    private var organizationName: String = ""

    private val _uiState = MutableStateFlow(POSUiState(isPrintingAvailable = receiptPrinter.isAvailable))
    val uiState: StateFlow<POSUiState> = _uiState.asStateFlow()

    private var stockObserveJob: Job? = null
    private var categoriesObserveJob: Job? = null
    private var sessionObserveJob: Job? = null
    private var pendingObserveJob: Job? = null
    private var packagingObserveJob: Job? = null
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
                observePackagingItems(companyId)
                observeSession(companyId, result.session.id)
                observePendingCollections(companyId, result.session.branchId)
                loadPopularity(companyId, result.session.branchId)
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    /**
     * Sale de los resúmenes diarios (~30 documentos), no de las ventas. Si falla, "Todos"
     * queda alfabético: el orden es una comodidad y no puede impedir cobrar.
     */
    private fun loadPopularity(companyId: String, branchId: String) {
        // Una lectura por sucursal por día: `load()` corre cada vez que se vuelve al POS (al
        // terminar de anotar un gasto, por ejemplo) y el orden de ayer sirve igual para hoy.
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        val cacheKey = "$companyId|$branchId|$today"
        popularityCache[cacheKey]?.let { cached ->
            _uiState.update { it.copy(popularityByKey = cached) }
            return
        }
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val from = now - POPULARITY_DAYS * 24L * 60 * 60 * 1000
            runCatching {
                saleRepository.aggregateSalesInRange(companyId, listOf(branchId), from, now)
                    .unitsByProduct
            }.onSuccess { units ->
                popularityCache[cacheKey] = units
                _uiState.update { it.copy(popularityByKey = units) }
            }
        }
    }

    /** Orden de "Todos" ya leído hoy, por empresa|sucursal|fecha. */
    private val popularityCache = mutableMapOf<String, Map<String, Double>>()

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

    /**
     * En vivo: si el dueño cambia el precio de la bolsa, las cajas abiertas cobran el nuevo sin
     * recargar. Lo que ya está en un carrito conserva el precio con que entró. Si falla, no hay
     * botones de empaque y se sigue cobrando: nunca puede impedir una venta.
     */
    private fun observePackagingItems(companyId: String) {
        packagingObserveJob?.cancel()
        packagingObserveJob = viewModelScope.launch {
            packagingItemRepository.observeActive(companyId)
                .catch { emit(emptyList()) }
                .collect { items -> _uiState.update { it.copy(packagingItems = items) } }
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
                .collect { list ->
                    val stillPending = list.map { it.id }.toSet()
                    _uiState.update { st ->
                        // Si otra caja recibió una cuenta que estaba en este carrito, se saca:
                        // cobrarla acá la metería en dos gavetas.
                        val gone = st.cart.filter { it.isRestaurant && it.pendingSaleId !in stillPending }
                        st.copy(
                            pendingCollections = list,
                            cart = st.cart - gone.toSet(),
                            error = if (gone.isNotEmpty())
                                "Otra caja ya recibió ${gone.joinToString { it.productName }}; se quitó del carrito."
                            else st.error
                        )
                    }
                }
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
                    cashierName = currentUser.displayName,
                    cashierEmployeeId = currentUser.operatorEmployeeId
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

    /**
     * Pone la cuenta del restaurante en el carrito para cobrarla junto con lo que el cliente
     * lleve de panadería: un solo pago, un solo vuelto. Se cobra con el resto al confirmar.
     */
    fun addPendingToCart(sale: Sale) {
        if (_uiState.value.cart.any { it.pendingSaleId == sale.id }) return
        val line = CartLine(
            key = "rest_${sale.id}",
            productId = "",
            productName = restaurantLineName(sale),
            quantity = 1.0,
            unitPrice = sale.total,
            pendingSaleId = sale.id
        )
        _uiState.update { it.copy(cart = it.cart + line) }
    }

    private fun restaurantLineName(sale: Sale): String =
        listOfNotNull(
            "Restaurante",
            sale.externalOrderNumber.takeIf { it.isNotEmpty() }?.let { "#$it" },
            sale.externalTableLabel.ifEmpty { null }
        ).joinToString(" · ")

    // ── Carrito ───────────────────────────────────────────────────────────

    fun addProductToCart(product: Product, quantity: Double) {
        val resale = _uiState.value.resellerSale
        // A un revendedor no se le cobra un pan que no tiene precio en su lista: caer al precio
        // público le cobraría de más, y adivinar uno le cobraría lo que nadie decidió.
        if (resale != null && resellerPriceFor(product) == null) {
            _uiState.update {
                it.copy(error = "${product.name} no tiene precio en la lista «${resale.priceListName}». " +
                    "Agregáselo en Listas de precios.")
            }
            return
        }
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
                priceListId = resale?.priceListId ?: retailPriceListId,
                priceSource = unitPrice.second
            )
        }
        _uiState.update { it.copy(cart = newCart) }
    }

    /**
     * Agrega N panes de un grupo de precio ("Pan Q0.50"). La cajera no elige el tipo: con cola
     * en el mostrador eso es lento y además da un dato malo. Acumula sobre la misma línea.
     */
    fun addGroupToCart(category: POSCategory, quantity: Double) {
        if (!category.sellAsGroup || quantity <= 0.0) return
        val resale = _uiState.value.resellerSale
        val unitPrice = groupPriceFor(category) ?: run {
            _uiState.update {
                it.copy(error = "Los panes de ${category.groupDisplayName} no tienen todos el mismo precio " +
                    "en la lista «${resale?.priceListName.orEmpty()}». Cobralos uno por uno con la lupa.")
            }
            return
        }
        val key = "grp_${category.id}"
        val cart = _uiState.value.cart
        val newCart = if (cart.any { it.key == key }) {
            cart.map { if (it.key == key) it.copy(quantity = it.quantity + quantity) else it }
        } else {
            cart + CartLine(
                key = key,
                productId = "",
                productName = category.groupDisplayName,
                quantity = quantity,
                unitPrice = unitPrice,
                priceListId = resale?.priceListId ?: retailPriceListId,
                priceSource = SaleLinePriceSource.QUICK_GROUP,
                quickGroupId = category.id
            )
        }
        _uiState.update { it.copy(cart = newCart) }
    }

    /**
     * Suma un empaque (la bolsa) al carrito: cada toque, uno más sobre la misma línea. Mismo
     * precio para todos — al revendedor también: la bolsa no está en su lista de precios.
     */
    fun addPackagingToCart(item: PackagingItem) {
        if (item.salePrice <= 0.0) return
        val key = "pkg_${item.id}"
        val cart = _uiState.value.cart
        val newCart = if (cart.any { it.key == key }) {
            cart.map { if (it.key == key) it.copy(quantity = it.quantity + 1) else it }
        } else {
            cart + CartLine(
                key = key,
                productId = "",
                productName = item.name,
                quantity = 1.0,
                unitPrice = item.salePrice,
                priceSource = SaleLinePriceSource.PACKAGING,
                packagingItemId = item.id
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

    /**
     * Cobra el carrito. [bills] son los billetes que la cajera fue tocando (valor → cantidad);
     * vacío si tecleó el monto. Se guardan para "Último cobro".
     *
     * Si el carrito trae cuentas del restaurante, primero se reciben ésas —si otra caja ya
     * recibió alguna, el cobro se corta antes de escribir nada del mostrador— y después se
     * registra la venta de panadería. Todas quedan con el mismo `checkoutId` y los mismos
     * datos del pago.
     */
    fun confirmSale(
        companyId: String,
        currentUser: User,
        paymentMethod: String,
        tenderedAmount: Double,
        bills: Map<Int, Int> = emptyMap(),
        credit: CreditCharge? = null,
        /** Colaborador que compra pagando en efectivo y tiene descuento configurado. */
        discountAccount: com.bakery.erp.model.CreditAccount? = null,
        /** A quién facturar, o null si el cliente no pidió factura. */
        invoice: FelInvoiceRequest? = null,
        /** Anticipo del cliente que paga parte (o todo) de este cobro. */
        deposit: com.bakery.erp.model.CustomerDeposit? = null,
        /**
         * Pago dividido: lo que va con tarjeta / transferencia (método → monto). El efectivo es
         * el resto y [tenderedAmount] es lo que dieron de ESA parte. Vacío = un solo método.
         */
        split: Map<String, Double> = emptyMap()
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
        val isCredit = paymentMethod == SalePaymentMethod.CREDIT
        // Sin fiado combinado: el cargo a la cuenta y la venta tendrían que partirse.
        val splitTyped = if (isCredit) emptyMap() else split
            .filterKeys { it in com.bakery.erp.model.SPLIT_NON_CASH_METHODS }
            .filterValues { it > 0.004 }
        val resale = state.resellerSale
        // Al revendedor se le vende de contado (decisión del dueño: fiarle es raro y no se
        // ofrece). Su precio ya es el rebajado: no se le suma el descuento de colaborador.
        if (resale != null && isCredit) {
            _uiState.update { it.copy(error = "La venta a revendedor es de contado.") }; return
        }
        // El descuento se recalcula acá desde la cuenta, no se confía en lo que mostró el
        // diálogo, y solo con efectivo: premia el pago al contado, no el fiado ni la tarjeta.
        // Un pago dividido no lo lleva: premia pagar TODO de contado.
        val discountAmount = if (resale == null && paymentMethod == SalePaymentMethod.CASH && splitTyped.isEmpty() &&
            discountAccount?.isEmployee == true && discountAccount.cashDiscountEligible
        ) employeeDiscountAmount() else 0.0
        val total = cartTotal() - discountAmount
        if (isCredit) {
            if (credit == null) {
                _uiState.update { it.copy(error = "Elegí a qué cuenta se carga") }; return
            }
            if (credit.account.requiresPerson && credit.personName.isBlank()) {
                _uiState.update { it.copy(error = "Elegí quién se lleva la mercadería") }; return
            }
            // Una cuenta del restaurante ya es una venta cobrada en ComandaYa: fiarla acá la
            // dejaría como recibida en caja sin que entre un centavo.
            if (state.cart.any { it.isRestaurant }) {
                _uiState.update { it.copy(error = "Las cuentas del restaurante no se pueden cargar a crédito") }; return
            }
        }
        // El anticipo paga primero; hoy solo se cobra el saldo. No se combina con fiado.
        val depositToApply = deposit?.takeIf { !isCredit && it.isOpen }
        val depositApplied = depositToApply?.let { money(minOf(it.remaining, total)) } ?: 0.0
        val due = money(total - depositApplied)
        val coveredByDeposit = depositApplied > 0.004 && due <= 0.004
        // Pago dividido: se revalida contra el total recién calculado, no contra lo que vio el
        // diálogo. Si una sola parte cubre todo (tarjeta por el total), es un cobro normal.
        val splitParts: Map<String, Double> = if (splitTyped.isNotEmpty() && !coveredByDeposit) {
            com.bakery.erp.model.splitPaymentProblem(due, splitTyped)?.let { problem ->
                _uiState.update { it.copy(error = problem) }; return
            }
            com.bakery.erp.model.splitPaymentParts(due, splitTyped)
        } else emptyMap()
        val isMixed = splitParts.size > 1
        val chargeMethod = when {
            isMixed -> SalePaymentMethod.MIXED
            splitParts.size == 1 -> splitParts.keys.first()
            else -> paymentMethod
        }
        // Todo pagado con anticipo: la venta no tiene otro método que mostrar.
        val saleMethod = if (coveredByDeposit) SalePaymentMethod.CUSTOMER_DEPOSIT else chargeMethod
        // Lo que se paga en billetes: todo si es efectivo, el resto si es dividido.
        val cashDue = when {
            coveredByDeposit -> 0.0
            isMixed -> splitParts[SalePaymentMethod.CASH] ?: 0.0
            chargeMethod == SalePaymentMethod.CASH -> due
            else -> 0.0
        }
        val takesCash = cashDue > 0.004
        // En un pago dividido, "le dieron" y el vuelto son de la parte en efectivo.
        val effectiveTendered = if (takesCash) tenderedAmount else if (isMixed) 0.0 else due
        if (takesCash && tenderedAmount < cashDue - 0.004) {
            val msg = if (isMixed) "El efectivo recibido no alcanza para Q ${cashDue.fmtMoney(2)}"
                      else "Monto recibido menor al total"
            _uiState.update { it.copy(error = msg) }; return
        }
        val change = if (takesCash) (tenderedAmount - cashDue).coerceAtLeast(0.0) else 0.0
        val cashBills = if (takesCash) bills.filterValues { it > 0 } else emptyMap()

        val snapshotCart = state.cart
        val bakeryCart = snapshotCart.filter { it.isBakery }
        val restaurantCart = snapshotCart.filter { it.isRestaurant }
        val bakeryTotal = bakeryCart.sumOf { it.subtotal }
        // La factura cubre el pan y las cuentas del restaurante que ComandaYa no facturó. Las
        // que sí facturó (en la mesa) quedan fuera: se verían dos veces ante la SAT.
        val invoiceableRestaurant = invoiceableRestaurantIds(restaurantCart)
        val invoiceRequest = invoice?.takeIf {
            canInvoice() && (bakeryCart.isNotEmpty() || invoiceableRestaurant.isNotEmpty())
        }
        if (invoiceRequest != null) {
            val invoicedTotal = (if (bakeryCart.isNotEmpty()) bakeryTotal - discountAmount else 0.0) +
                restaurantCart.filter { it.pendingSaleId in invoiceableRestaurant }.sumOf { it.subtotal }
            felReceptorProblem(invoiceRequest, invoicedTotal)?.let { problem ->
                _uiState.update { it.copy(error = problem) }; return
            }
        }
        // Cuánto del anticipo va a cada venta: primero las cuentas del restaurante, después el pan.
        val depositPlan = com.bakery.erp.model.planDepositApplication(
            depositApplied,
            restaurantCart.map { it.pendingSaleId to it.subtotal } +
                listOfNotNull(if (bakeryCart.isNotEmpty()) BAKERY_SALE_KEY to money(bakeryTotal - discountAmount) else null)
        )
        // Pago dividido: la parte de cada venta por método. Primero las cuentas del restaurante
        // (en el mismo orden que el anticipo), después el pan; cada una por lo que cobra hoy.
        val partsPlan: Map<String, Map<String, Double>> = if (isMixed) com.bakery.erp.model.allocatePaymentParts(
            splitParts,
            restaurantCart.map { it.pendingSaleId to money(it.subtotal - (depositPlan[it.pendingSaleId] ?: 0.0)) } +
                listOfNotNull(if (bakeryCart.isNotEmpty())
                    BAKERY_SALE_KEY to money(bakeryTotal - discountAmount - (depositPlan[BAKERY_SALE_KEY] ?: 0.0)) else null)
        ) else emptyMap()
        val payment = CheckoutPayment(
            checkoutId = newCheckoutId(),
            method = saleMethod,
            checkoutTotal = total,
            tenderedAmount = effectiveTendered,
            changeAmount = change,
            billValues = cashBills.keys.map { it.toDouble() },
            billCounts = cashBills.values.map { it.toDouble() }
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isCheckingOut = true, error = null) }
            // Lo del anticipo que ya quedó escrito en alguna venta. Si el cobro se corta a medias,
            // se libera solo el resto: lo que pagó una cuenta ya recibida es real.
            var depositUsed = 0.0
            var depositReserved = false
            runCatching {
                // Revalidar la sesión contra Firestore antes de escribir: debe seguir OPEN y ser
                // del cajero actual. Evita registrar ventas contra una sesión ya cerrada por un
                // cuadre / cierre concurrente o abierta por otro cajero.
                val freshSession = cashSessionRepository.getSession(companyId, session.id)
                    ?: throw IllegalStateException("La sesión de caja ya no existe.")
                if (freshSession.status != com.bakery.erp.model.CashSessionStatus.OPEN) {
                    throw IllegalStateException("La caja fue cerrada. No se puede registrar la venta.")
                }
                if (!freshSession.isOperatedBy(currentUser)) {
                    throw IllegalStateException("Esta sesión de caja pertenece a ${freshSession.cashierName}.")
                }

                // El anticipo se reserva ANTES de escribir ventas: si otra caja lo aplicó primero,
                // el cobro se corta acá sin haber movido nada.
                if (depositToApply != null && depositApplied > 0.004) {
                    depositRepository.apply(companyId, depositToApply.id, depositApplied, branch.name)
                    depositReserved = true
                }

                // Primero lo del restaurante: su guarda de "ya la recibió otra caja" es la que
                // puede fallar, y tiene que hacerlo antes de mover inventario del mostrador.
                val collected = restaurantCart.map { line ->
                    val invoiceThis = invoiceRequest != null && line.pendingSaleId in invoiceableRestaurant
                    val depositHere = depositPlan[line.pendingSaleId] ?: 0.0
                    saleRepository.collectPendingSale(
                        companyId = companyId,
                        saleId = line.pendingSaleId,
                        sessionId = session.id,
                        registerId = session.registerId,
                        registerName = session.registerName,
                        cashierId = currentUser.id,
                        cashierName = currentUser.displayName,
                        cashierEmployeeId = currentUser.operatorEmployeeId,
                        checkout = (if (!invoiceThis) payment else payment.copy(
                            invoiceReceptorId = invoiceRequest!!.receptorId,
                            invoiceReceptorName = invoiceRequest.receptorName,
                            invoiceWithoutDetail = invoiceRequest.withoutDetail
                        )).copy(
                            depositId = depositToApply?.id.orEmpty(),
                            depositFolio = depositToApply?.folio.orEmpty(),
                            depositAmount = depositHere
                        ).let { p ->
                            val mine = partsPlan[line.pendingSaleId].orEmpty()
                            if (!isMixed) p
                            else p.copy(method = com.bakery.erp.model.headerMethodFor(mine, saleMethod), parts = mine)
                        }
                    ).also { depositUsed += depositHere }
                }
                // Las ventas que van en la factura de este cobro: las que quedaron SOLICITADA.
                val collectedToInvoice = collected
                    .filter { it.invoiceStatus == FelInvoiceStatus.SOLICITADA }.map { it.id }

                if (bakeryCart.isEmpty()) return@runCatching collected.first() to collectedToInvoice

                val bakeryDeposit = depositPlan[BAKERY_SALE_KEY] ?: 0.0
                val bakeryParts = partsPlan[BAKERY_SALE_KEY].orEmpty()
                val bakeryMethod = if (isMixed) com.bakery.erp.model.headerMethodFor(bakeryParts, saleMethod) else saleMethod
                val sale = Sale(
                    branchId = branch.id,
                    branchName = branch.name,
                    registerId = session.registerId,
                    registerName = session.registerName,
                    sessionId = session.id,
                    cashierId = currentUser.id,
                    cashierName = currentUser.displayName,
                    cashierEmployeeId = currentUser.operatorEmployeeId,
                    subtotal = bakeryTotal,
                    discount = discountAmount,
                    total = bakeryTotal - discountAmount,
                    discountAccountId = if (discountAmount > 0) discountAccount?.id.orEmpty() else "",
                    discountLabel = if (discountAmount > 0) "Descuento colaborador: ${discountAccount?.name.orEmpty()}" else "",
                    paymentMethod = bakeryMethod,
                    tenderedAmount = effectiveTendered,
                    changeAmount = change,
                    // Unidades por grupo en el encabezado: el conteo de cierre las reparte
                    // leyendo solo encabezados, sin abrir las líneas de cada ticket.
                    groupIds = bakeryCart.filter { it.isGroup }.map { it.quickGroupId },
                    groupUnits = bakeryCart.filter { it.isGroup }.map { it.quantity },
                    packagingUnits = bakeryCart.filter { it.isPackaging }.sumOf { it.quantity },
                    packagingAmount = money(bakeryCart.filter { it.isPackaging }.sumOf { it.subtotal }),
                    checkoutId = payment.checkoutId,
                    checkoutTotal = total,
                    billValues = payment.billValues,
                    billCounts = payment.billCounts,
                    creditAccountId = credit?.account?.id.orEmpty(),
                    creditAccountName = credit?.account?.name.orEmpty(),
                    creditPersonName = credit?.personName.orEmpty(),
                    resellerId = resale?.customer?.id.orEmpty(),
                    resellerName = resale?.customer?.name.orEmpty(),
                    resellerPriceListName = resale?.priceListName.orEmpty(),
                    // SOLICITADA en la misma escritura que crea la venta: si la llamada que
                    // sigue se pierde, el reintento del servidor la encuentra igual.
                    invoiceStatus = if (invoiceRequest != null) FelInvoiceStatus.SOLICITADA else "",
                    invoiceReceptorId = invoiceRequest?.receptorId.orEmpty(),
                    invoiceReceptorName = invoiceRequest?.receptorName.orEmpty(),
                    invoiceWithoutDetail = invoiceRequest?.withoutDetail == true,
                    depositId = if (bakeryDeposit > 0.004) depositToApply?.id.orEmpty() else "",
                    depositFolio = if (bakeryDeposit > 0.004) depositToApply?.folio.orEmpty() else "",
                    depositAmount = bakeryDeposit,
                    linkedCustomerOrderId = depositToApply?.customerOrderId?.takeIf { bakeryDeposit > 0.004 }.orEmpty(),
                    // Con anticipo, la caja solo suma el saldo (ver createSale).
                    paymentBreakdown = if (isMixed) buildMap {
                        if (bakeryDeposit > 0.004) put(SalePaymentMethod.CUSTOMER_DEPOSIT, bakeryDeposit)
                        putAll(bakeryParts)
                    } else com.bakery.erp.model.paymentBreakdownWithDeposit(
                        money(bakeryTotal - discountAmount), bakeryDeposit, saleMethod
                    )
                )
                val lines = bakeryCart.map { line ->
                    if (line.isPackaging) {
                        return@map SaleLine(
                            productId = null,
                            productName = line.productName,
                            quantity = line.quantity,
                            unitPrice = line.unitPrice,
                            subtotal = line.subtotal,
                            priceSource = line.priceSource,
                            stockDeducted = false,
                            packagingItemId = line.packagingItemId
                        )
                    }
                    if (line.isGroup) {
                        return@map SaleLine(
                            productId = null,
                            productName = line.productName,
                            quickGroupId = line.quickGroupId,
                            quickGroupName = line.productName,
                            quantity = line.quantity,
                            unitPrice = line.unitPrice,
                            subtotal = line.subtotal,
                            priceListId = line.priceListId,
                            priceSource = line.priceSource,
                            stockDeducted = false
                        )
                    }
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
                val created = saleRepository.createSale(companyId, sale, lines)
                depositUsed += bakeryDeposit
                if (isCredit && credit != null) {
                    // El cargo va DESPUÉS de la venta: sin venta no hay nada que deber. Si
                    // fallara acá, la venta queda y el error lo dice, para cargarlo a mano.
                    runCatching {
                        creditRepository.addMovement(companyId, com.bakery.erp.model.CreditMovement(
                            accountId = credit.account.id,
                            accountName = credit.account.name,
                            type = com.bakery.erp.model.CreditMovementType.SALE,
                            amount = created.total,
                            description = bakeryCart.joinToString { l -> "${l.quantity.fmtMoney(0)} ${l.productName}" }
                                .take(300),
                            personId = credit.personId,
                            personName = credit.personName.ifBlank { credit.account.name },
                            saleId = created.id,
                            sessionId = session.id,
                            registerName = session.registerName,
                            branchId = branch.id,
                            branchName = branch.name,
                            createdByUserId = currentUser.id,
                            createdByUserName = currentUser.displayName,
                            createdByEmployeeId = currentUser.operatorEmployeeId
                        ))
                    }.onFailure { e ->
                        throw IllegalStateException(
                            "La venta se registró pero no se pudo cargar a ${credit.account.name}: ${e.message}. " +
                                "Cargala desde Créditos → Ajuste."
                        )
                    }
                }
                created to (listOfNotNull(created.id.takeIf { invoiceRequest != null }) + collectedToInvoice)
            }.onSuccess { (sale, invoiceSaleIds) ->
                if (depositToApply != null && depositApplied > 0.004 && depositToApply.customerOrderId.isNotBlank()) {
                    completeOrderOnDelivery(companyId, currentUser, depositToApply, due, saleMethod, sale.id)
                }
                if (depositToApply != null && depositApplied > 0.004) {
                    // Constancia de qué ventas pagó. No crítico: cada venta ya lleva el folio.
                    viewModelScope.launch {
                        runCatching { depositRepository.linkSales(companyId, depositToApply.id, listOf(sale.id) + invoiceSaleIds) }
                    }
                }
                // El ticket y el acuse muestran el COBRO completo, no solo la venta de pan.
                val checkoutSale = sale.copy(subtotal = total + discountAmount, discount = discountAmount,
                    total = total, tenderedAmount = effectiveTendered, changeAmount = change,
                    paymentMethod = saleMethod,
                    // El ticket dice cómo se pagó TODO el cobro, no solo la parte del pan.
                    paymentBreakdown = if (isMixed) splitParts else sale.paymentBreakdown,
                    depositFolio = depositToApply?.folio.orEmpty().takeIf { depositApplied > 0.004 }.orEmpty(),
                    depositAmount = depositApplied)
                val receipt = buildReceiptData(checkoutSale, snapshotCart, branch)
                val record = CheckoutRecord(
                    checkoutId = payment.checkoutId,
                    at = Clock.System.now().toEpochMilliseconds(),
                    method = chargeMethod,
                    total = total,
                    tendered = effectiveTendered,
                    change = change,
                    parts = if (isMixed) splitParts else emptyMap(),
                    bills = cashBills.entries.sortedByDescending { it.key }
                        .map { it.key.toDouble() to it.value },
                    items = snapshotCart.map { l ->
                        if (l.isRestaurant) l.productName
                        else "${l.quantity.fmtMoney(0)} × ${l.productName}"
                    } + listOfNotNull(
                        if (discountAmount > 0) "Descuento ${discountAccount?.name.orEmpty()}: −Q ${discountAmount.fmtMoney(2)}" else null,
                        if (depositApplied > 0.004) "Anticipo ${depositToApply?.folio.orEmpty()}: −Q ${depositApplied.fmtMoney(2)}" else null
                    ),
                    cashierName = currentUser.displayName
                )
                val invoiced = invoiceRequest != null && invoiceSaleIds.isNotEmpty()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isCheckingOut = false,
                        cart = emptyList(),
                        // La siguiente venta es de mostrador: quedarse en modo revendedor le
                        // cobraría barato al cliente que sigue en la fila.
                        resellerSale = null,
                        lastSaleSummary = LastSaleSummary(total, change, chargeMethod),
                        lastReceiptData = receipt,
                        recentCheckouts = (listOf(record) + it.recentCheckouts).take(MAX_RECENT_CHECKOUTS),
                        invoiceProgress = if (invoiced) InvoiceProgress(invoiceSaleIds.first(), FelInvoiceStatus.SOLICITADA) else null
                    )
                }
                if (invoiced && invoiceRequest != null) certifyInvoice(companyId, invoiceSaleIds, invoiceRequest)
            }.onFailure { err ->
                // Lo reservado del anticipo que no llegó a ninguna venta vuelve a estar disponible.
                val unused = money(depositApplied - depositUsed)
                if (depositReserved && depositToApply != null && unused > 0.004) {
                    runCatching { depositRepository.release(companyId, depositToApply.id, unused) }
                }
                _uiState.update { it.copy(isLoading = false, isCheckingOut = false, error = err.message) }
            }
        }
    }

    // ── Anticipos de clientes ─────────────────────────────────────────────────

    /** Anticipos con saldo de todas las sucursales: el cliente recoge donde quiera. */
    fun loadOpenDeposits(companyId: String) {
        viewModelScope.launch {
            runCatching { depositRepository.getOpen(companyId) }
                .onSuccess { list -> _uiState.update { it.copy(openDeposits = list) } }
                .onFailure { e -> _uiState.update { it.copy(error = "No se pudieron cargar los anticipos: ${e.message}") } }
        }
    }

    /** Sucursales que venden (activas): el cliente puede recoger en cualquiera. */
    fun loadPickupBranches(companyId: String) {
        viewModelScope.launch {
            runCatching { branchRepository.getBranches(companyId) }
                .onSuccess { list ->
                    _uiState.update { s ->
                        s.copy(pickupBranches = list
                            .filter { it.active && com.bakery.erp.model.BranchCapability.CAN_SELL in it.capabilities }
                            .sortedBy { it.name.lowercase() })
                    }
                }
        }
    }

    /** Pedidos de clientes que todavía aceptan anticipo (con saldo, no entregados ni cancelados). */
    fun loadDepositOrders(companyId: String) {
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val day = 24L * 3600 * 1000
            runCatching { customerOrderRepository.getOrdersInRange(companyId, now - 30 * day, now + 365 * day) }
                .onSuccess { list ->
                    _uiState.update { s ->
                        s.copy(depositOrders = list.filter { it.acceptsDeposit() }.sortedBy { it.deliveryDate })
                    }
                }
        }
    }

    /**
     * Entregado con su anticipo aplicado: el pedido registra lo que se pagó hoy (el saldo) y,
     * si ya no debe nada, queda entregado. No crítico para el cobro, que ya está escrito: si
     * falla, se avisa para marcarlo a mano desde el pedido.
     */
    private fun completeOrderOnDelivery(
        companyId: String,
        currentUser: User,
        deposit: com.bakery.erp.model.CustomerDeposit,
        paidToday: Double,
        method: String,
        saleId: String
    ) {
        val session = _uiState.value.session
        val branch = _uiState.value.branch
        viewModelScope.launch {
            runCatching {
                val orderId = deposit.customerOrderId
                if (paidToday > 0.004) {
                    customerOrderRepository.recordPayment(companyId, orderId, com.bakery.erp.model.CustomerOrderPayment(
                        type = com.bakery.erp.model.CustomerOrderPaymentType.BALANCE,
                        amount = paidToday,
                        method = method,
                        saleId = saleId,
                        cashSessionId = session?.id.orEmpty(),
                        branchId = branch?.id.orEmpty(),
                        receivedByUserId = currentUser.id,
                        receivedByUserName = currentUser.displayName,
                        notes = "Saldo al entregar · anticipo ${deposit.folio}",
                        depositId = deposit.id,
                        depositFolio = deposit.folio
                    ))
                }
                val order = customerOrderRepository.getOrder(companyId, orderId)
                if (order != null && order.balanceAmount <= 0.004 &&
                    order.status != com.bakery.erp.model.CustomerOrderStatus.DELIVERED &&
                    order.status != com.bakery.erp.model.CustomerOrderStatus.CANCELLED
                ) {
                    customerOrderRepository.updateOrderStatus(companyId, orderId, com.bakery.erp.model.CustomerOrderStatus.DELIVERED)
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = "La venta quedó, pero no se pudo cerrar el pedido: ${e.message}") }
            }
        }
    }

    /**
     * Recibe un anticipo en la caja abierta. El efectivo entra a la gaveta como movimiento (el
     * cuadre de ESTA caja lo explica) y NO como venta: la venta ocurre al entregar el encargo.
     * Si es de un pedido, queda registrado como pago del pedido.
     */
    fun receiveDeposit(companyId: String, currentUser: User, input: DepositInput, print: Boolean = true) {
        val session = _uiState.value.session ?: return
        val branch = _uiState.value.branch ?: return
        if (_uiState.value.isDepositBusy || input.amount <= 0.0) return
        // Otra vez acá y no solo en el diálogo: pudo quedar abierto mientras la hora pasaba.
        com.bakery.erp.model.depositDeliveryProblem(input.deliveryAt, Clock.System.now().toEpochMilliseconds())?.let { problem ->
            _uiState.update { it.copy(error = problem) }; return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isDepositBusy = true, error = null) }
            runCatching {
                val saved = depositRepository.create(companyId, com.bakery.erp.model.CustomerDeposit(
                    customerName = input.customerName,
                    customerPhone = input.customerPhone,
                    description = input.description,
                    deliveryLabel = input.deliveryLabel,
                    deliveryAt = input.deliveryAt,
                    items = input.items,
                    isRestaurant = input.isRestaurant,
                    pickupBranchId = input.pickupBranchId.ifBlank { branch.id },
                    pickupBranchName = input.pickupBranchName.ifBlank { branch.name },
                    amount = money(input.amount),
                    method = input.method,
                    branchId = branch.id,
                    branchName = branch.name,
                    sessionId = session.id,
                    registerName = session.registerName,
                    receivedByUserId = currentUser.id,
                    receivedByUserName = currentUser.displayName,
                    customerOrderId = input.orderId,
                    customerOrderLabel = input.orderLabel
                ))
                // Tarjeta o transferencia no entran a la gaveta: quedan solo en el anticipo.
                if (input.method == SalePaymentMethod.CASH) {
                    cashSessionRepository.addMovement(companyId, com.bakery.erp.model.CashMovement(
                        sessionId = session.id,
                        type = com.bakery.erp.model.CashMovementType.DEPOSIT,
                        amount = saved.amount,
                        reason = "Anticipo ${saved.folio} · ${saved.customerName}",
                        createdByUserId = currentUser.id,
                        createdByUserName = currentUser.displayName,
                        createdByEmployeeId = currentUser.operatorEmployeeId,
                        sourceDepositId = saved.id
                    ))
                }
                if (saved.customerOrderId.isNotBlank()) {
                    // El pedido ve el anticipo como pago suyo (abonado y saldo se recalculan). Si
                    // esto falla, el dinero ya está bien registrado: se avisa y el anticipo sigue.
                    runCatching {
                        customerOrderRepository.recordPayment(companyId, saved.customerOrderId, com.bakery.erp.model.CustomerOrderPayment(
                            type = com.bakery.erp.model.CustomerOrderPaymentType.ADVANCE,
                            amount = saved.amount,
                            method = saved.method,
                            cashSessionId = session.id,
                            branchId = branch.id,
                            receivedByUserId = currentUser.id,
                            receivedByUserName = currentUser.displayName,
                            notes = "Anticipo ${saved.folio}",
                            depositId = saved.id,
                            depositFolio = saved.folio
                        ))
                    }.onFailure { e ->
                        _uiState.update { it.copy(error = "Anticipo ${saved.folio} recibido, pero no se registró en el pedido: ${e.message}") }
                    }
                }
                saved
            }.onSuccess { saved ->
                _uiState.update {
                    it.copy(
                        isDepositBusy = false,
                        // "Solo guardar" (sin impresora, o el cliente no lo quiere): se reimprime
                        // después desde "Anticipos pendientes".
                        depositToPrint = if (print) saved else null,
                        depositSavedAt = Clock.System.now().toEpochMilliseconds(),
                        openDeposits = listOf(saved) + it.openDeposits,
                        notice = "Anticipo ${saved.folio} recibido: Q ${saved.amount.fmtMoney(2)}"
                    )
                }
            }.onFailure { e -> _uiState.update { it.copy(isDepositBusy = false, error = e.message) } }
        }
    }

    /**
     * Devuelve lo que queda de un anticipo, en efectivo de ESTA gaveta. Se valida contra el
     * efectivo de la sesión recién leída: no se entrega dinero que no está.
     */
    fun refundDeposit(companyId: String, currentUser: User, deposit: com.bakery.erp.model.CustomerDeposit, reason: String) {
        val session = _uiState.value.session ?: return
        if (_uiState.value.isDepositBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDepositBusy = true, error = null) }
            runCatching {
                val fresh = cashSessionRepository.getSession(companyId, session.id)
                    ?: throw IllegalStateException("La sesión de caja ya no existe.")
                if (fresh.expectedCashNow + 0.004 < deposit.remaining) {
                    throw IllegalStateException(
                        "En esta caja hay Q ${fresh.expectedCashNow.fmtMoney(2)}: no alcanza para devolver Q ${deposit.remaining.fmtMoney(2)}."
                    )
                }
                val amount = depositRepository.refund(companyId, deposit.id, currentUser.displayName, reason)
                cashSessionRepository.addMovement(companyId, com.bakery.erp.model.CashMovement(
                    sessionId = session.id,
                    type = com.bakery.erp.model.CashMovementType.WITHDRAWAL,
                    amount = amount,
                    reason = "Devolución anticipo ${deposit.folio} · ${deposit.customerName}",
                    createdByUserId = currentUser.id,
                    createdByUserName = currentUser.displayName,
                    createdByEmployeeId = currentUser.operatorEmployeeId,
                    sourceDepositId = deposit.id
                ))
                if (deposit.customerOrderId.isNotBlank()) {
                    // Pago negativo: el pedido vuelve a deber lo devuelto.
                    runCatching {
                        customerOrderRepository.recordPayment(companyId, deposit.customerOrderId, com.bakery.erp.model.CustomerOrderPayment(
                            type = com.bakery.erp.model.CustomerOrderPaymentType.ADVANCE,
                            amount = -amount,
                            method = com.bakery.erp.model.CustomerOrderPaymentMethod.CASH,
                            cashSessionId = session.id,
                            receivedByUserId = currentUser.id,
                            receivedByUserName = currentUser.displayName,
                            notes = "Devolución anticipo ${deposit.folio}: $reason",
                            depositId = deposit.id,
                            depositFolio = deposit.folio
                        ))
                    }
                }
                amount
            }.onSuccess { amount ->
                _uiState.update {
                    it.copy(
                        isDepositBusy = false,
                        openDeposits = it.openDeposits.filterNot { d -> d.id == deposit.id },
                        notice = "Anticipo ${deposit.folio} devuelto: Q ${amount.fmtMoney(2)}"
                    )
                }
            }.onFailure { e -> _uiState.update { it.copy(isDepositBusy = false, error = e.message) } }
        }
    }

    fun reprintDeposit(deposit: com.bakery.erp.model.CustomerDeposit) = _uiState.update { it.copy(depositToPrint = deposit) }
    fun consumeDepositToPrint() = _uiState.update { it.copy(depositToPrint = null) }

    /** Nombre comercial para el encabezado de los comprobantes, igual que el ticket. */
    fun receiptCompanyName(): String = organizationName.ifEmpty { _uiState.value.branch?.name ?: "" }

    /**
     * Carga los cobros de la sesión para "Último cobro". Los de este aparato ya están en
     * memoria con su detalle; esto cubre el caso de que la app se haya reiniciado a media
     * jornada, leyendo las ventas de la sesión (sin sus líneas).
     */
    fun loadRecentCheckouts(companyId: String) {
        val session = _uiState.value.session ?: return
        if (_uiState.value.recentCheckouts.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCheckouts = true) }
            runCatching { saleRepository.getSalesForSession(companyId, session.id) }
                .onSuccess { sales ->
                    _uiState.update {
                        it.copy(
                            isLoadingCheckouts = false,
                            recentCheckouts = checkoutsFromSales(sales).take(MAX_RECENT_CHECKOUTS)
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoadingCheckouts = false, error = e.message) } }
        }
    }

    /**
     * Cobra en ESTA caja un abono a una cuenta de crédito. El efectivo entra a la gaveta como
     * ingreso (cuenta para el cuadre); tarjeta y transferencia solo bajan la deuda.
     *
     * El abono se escribe antes que el movimiento de caja: si la caja fallara, la deuda ya
     * bajó y el error lo dice — al revés, la gaveta tendría dinero que no le baja a nadie.
     */
    fun collectCreditPayment(
        companyId: String,
        currentUser: User,
        account: com.bakery.erp.model.CreditAccount,
        amount: Double,
        method: String,
        tendered: Double,
        bills: Map<Int, Int>
    ) {
        val session = _uiState.value.session ?: return
        val branch = _uiState.value.branch ?: return
        if (amount <= 0.0) return
        val isCash = method == SalePaymentMethod.CASH
        val effectiveTendered = if (isCash) tendered.coerceAtLeast(amount) else amount
        val change = if (isCash) effectiveTendered - amount else 0.0
        viewModelScope.launch {
            _uiState.update { it.copy(isCreditBusy = true, error = null) }
            runCatching {
                val movement = creditRepository.addMovement(companyId, com.bakery.erp.model.CreditMovement(
                    accountId = account.id,
                    accountName = account.name,
                    type = com.bakery.erp.model.CreditMovementType.PAYMENT,
                    amount = -amount,
                    method = method,
                    description = "Pago en caja",
                    sessionId = session.id,
                    registerName = session.registerName,
                    branchId = branch.id,
                    branchName = branch.name,
                    createdByUserId = currentUser.id,
                    createdByUserName = currentUser.displayName,
                    createdByEmployeeId = currentUser.operatorEmployeeId
                ))
                if (isCash) {
                    cashSessionRepository.addMovement(companyId, com.bakery.erp.model.CashMovement(
                        sessionId = session.id,
                        type = com.bakery.erp.model.CashMovementType.DEPOSIT,
                        amount = amount,
                        reason = "Abono a crédito: ${account.name}",
                        createdByUserId = currentUser.id,
                        createdByUserName = currentUser.displayName,
                        createdByEmployeeId = currentUser.operatorEmployeeId,
                        sourceCreditMovementId = movement.id
                    ))
                }
            }.onSuccess {
                val record = CheckoutRecord(
                    checkoutId = newCheckoutId(),
                    at = Clock.System.now().toEpochMilliseconds(),
                    method = method,
                    total = amount,
                    tendered = effectiveTendered,
                    change = change,
                    bills = bills.filterValues { it > 0 }.entries.sortedByDescending { it.key }
                        .map { it.key.toDouble() to it.value },
                    items = listOf("Abono a crédito · ${account.name}"),
                    cashierName = currentUser.displayName
                )
                _uiState.update {
                    it.copy(
                        isCreditBusy = false,
                        notice = "Abono de Q ${amount.fmtMoney(2)} a ${account.name}" +
                            if (change > 0.004) " · Vuelto Q ${change.fmtMoney(2)}" else "",
                        recentCheckouts = (listOf(record) + it.recentCheckouts).take(MAX_RECENT_CHECKOUTS)
                    )
                }
            }.onFailure { e -> _uiState.update { it.copy(isCreditBusy = false, error = e.message) } }
        }
    }

    /**
     * Adelanto de salario que sale de ESTA gaveta y se carga a la cuenta del colaborador.
     * Se valida contra el efectivo de la sesión recién leída: no se entrega dinero que no está.
     */
    fun giveEmployeeAdvance(
        companyId: String,
        currentUser: User,
        account: com.bakery.erp.model.CreditAccount,
        amount: Double,
        note: String
    ) {
        val session = _uiState.value.session ?: return
        val branch = _uiState.value.branch ?: return
        if (amount <= 0.0 || !account.isEmployee) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCreditBusy = true, error = null) }
            runCatching {
                val fresh = cashSessionRepository.getSession(companyId, session.id)
                    ?: throw IllegalStateException("La sesión de caja ya no existe.")
                if (fresh.status != com.bakery.erp.model.CashSessionStatus.OPEN) {
                    throw IllegalStateException("La caja fue cerrada.")
                }
                if (amount > fresh.expectedCashNow + 0.004) {
                    throw IllegalStateException(
                        "En la caja hay Q ${fresh.expectedCashNow.fmtMoney(2)}: no alcanza para un adelanto de Q ${amount.fmtMoney(2)}."
                    )
                }
                val movement = creditRepository.addMovement(companyId, com.bakery.erp.model.CreditMovement(
                    accountId = account.id,
                    accountName = account.name,
                    type = com.bakery.erp.model.CreditMovementType.ADVANCE,
                    amount = amount,
                    description = note.ifBlank { "Adelanto de caja" },
                    personName = account.name,
                    sessionId = session.id,
                    registerName = session.registerName,
                    branchId = branch.id,
                    branchName = branch.name,
                    method = SalePaymentMethod.CASH,
                    createdByUserId = currentUser.id,
                    createdByUserName = currentUser.displayName,
                    createdByEmployeeId = currentUser.operatorEmployeeId
                ))
                cashSessionRepository.addMovement(companyId, com.bakery.erp.model.CashMovement(
                    sessionId = session.id,
                    type = com.bakery.erp.model.CashMovementType.WITHDRAWAL,
                    amount = amount,
                    reason = "Adelanto: ${account.name}" + if (note.isNotBlank()) " · $note" else "",
                    createdByUserId = currentUser.id,
                    createdByUserName = currentUser.displayName,
                    createdByEmployeeId = currentUser.operatorEmployeeId,
                    sourceCreditMovementId = movement.id
                ))
            }.onSuccess {
                _uiState.update {
                    it.copy(isCreditBusy = false,
                        notice = "Adelanto de Q ${amount.fmtMoney(2)} a ${account.name}. Se descuenta en su planilla.")
                }
            }.onFailure { e -> _uiState.update { it.copy(isCreditBusy = false, error = e.message) } }
        }
    }

    fun clearNotice() { _uiState.update { it.copy(notice = null) } }

    /**
     * Cuánto se le descontaría al carrito si lo compra un colaborador con derecho y paga en
     * efectivo: el % de CADA producto sobre su línea. El pan de grupo y las cuentas del
     * restaurante no tienen producto, así que no descuentan.
     */
    fun employeeDiscountAmount(): Double {
        val st = _uiState.value
        val lines = st.cart.filter { it.isBakery && !it.isGroup && it.productId.isNotEmpty() }
            .map { it.productId to it.subtotal }
        val percents = st.productsById.mapValues { it.value.employeeDiscountPercent }
        return com.bakery.erp.model.employeeCashDiscount(lines, percents)
    }

    private fun newCheckoutId(): String =
        "co_" + Clock.System.now().toEpochMilliseconds().toString(36) +
            kotlin.random.Random.nextInt(0, 36 * 36 * 36).toString(36)

    /** Dispara la impresión del último ticket. Reporta errores vía `error` en el state. */
    fun printLastReceipt() {
        val data = _uiState.value.lastReceiptData ?: return
        if (_uiState.value.isPrinting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPrinting = true) }
            val started = Clock.System.now().toEpochMilliseconds()
            val result = receiptPrinter.print(data)
            // El aviso de "Imprimiendo" dura al menos un momento: la térmica responde en medio
            // segundo y un parpadeo no se alcanza a leer — parecía que el botón no hacía nada.
            val elapsed = Clock.System.now().toEpochMilliseconds() - started
            if (elapsed < MIN_PRINTING_FEEDBACK_MS) kotlinx.coroutines.delay(MIN_PRINTING_FEEDBACK_MS - elapsed)
            result
                .onFailure { e ->
                    _uiState.update { it.copy(isPrinting = false, error = e.message ?: "Error al imprimir") }
                }
                .onSuccess {
                    _uiState.update { it.copy(isPrinting = false, printedAt = Clock.System.now().toEpochMilliseconds()) }
                }
        }
    }

    /** Cierra el diálogo de éxito y descarta el último ticket. */
    fun dismissLastSale() {
        _uiState.update { it.copy(lastReceiptData = null, lastSaleSummary = null, invoiceProgress = null) }
    }

    /** La sucursal abierta tiene un NIT asignado. Que el NIT esté activo lo decide el servidor. */
    fun canInvoice(): Boolean = _uiState.value.branch?.felProfileId?.isNotBlank() == true

    /**
     * Cuentas del restaurante del carrito que se pueden facturar acá: las que ComandaYa no
     * facturó. Se mira la lista viva de pendientes y no el carrito, porque el mesero puede
     * facturarla en ComandaYa mientras la cuenta ya está en el carrito de la caja.
     */
    fun invoiceableRestaurantIds(restaurantCart: List<CartLine> = _uiState.value.cart.filter { it.isRestaurant }): Set<String> {
        val pending = _uiState.value.pendingCollections.associateBy { it.id }
        return restaurantCart.map { it.pendingSaleId }
            .filter { id -> pending[id]?.invoiceStatus.isNullOrBlank() }
            .toSet()
    }

    /** El cobro sale con "Facturar" marcado en esta sucursal. */
    fun invoiceByDefault(): Boolean = canInvoice() && _uiState.value.branch?.invoiceByDefault == true

    /** Nombre del NIT en la SAT, para que la cajera confirme a quién le factura. */
    suspend fun lookupNit(companyId: String, nit: String): Result<String?> {
        val branchId = _uiState.value.branch?.id ?: return Result.success(null)
        return runCatching { felRepository.lookupNit(companyId, branchId, nit) }
    }

    /**
     * Pide la certificación y espera hasta [INVOICE_WAIT_MS]. Digifact tarda de 2 a 45 s; esperar
     * siempre lo peor trabaría la fila en sus días malos. Si no contesta a tiempo, la venta ya
     * está guardada con su factura pedida y el servidor la termina solo — el acuse lo dice y la
     * factura aparece después en el historial.
     */
    private fun certifyInvoice(companyId: String, saleIds: List<String>, request: FelInvoiceRequest) {
        // El progreso se ata a la primera venta del cobro: es la que el acuse conoce.
        val saleId = saleIds.first()
        viewModelScope.launch {
            val progress = runCatching {
                withTimeoutOrNull(INVOICE_WAIT_MS) { felRepository.certifySales(companyId, saleIds, request) }
            }.fold(
                onSuccess = { r ->
                    if (r == null) InvoiceProgress(saleId, FelInvoiceStatus.PENDIENTE,
                        "Digifact está tardando. La factura se completa sola y queda en el historial de ventas.")
                    else InvoiceProgress(saleId, r.status, r.message, r)
                },
                onFailure = { e ->
                    val msg = e.message.orEmpty()
                    // Configuración (sucursal sin NIT, NIT desactivado): el servidor ya la marcó
                    // RECHAZADA y reintentar no la arregla.
                    if (msg.contains("failed", ignoreCase = true) && msg.contains("precondition", ignoreCase = true))
                        InvoiceProgress(saleId, FelInvoiceStatus.RECHAZADO, msg.replaceFirst(Regex("^[A-Z_]+:\\s*"), ""))
                    else InvoiceProgress(saleId, FelInvoiceStatus.PENDIENTE,
                        "No se pudo pedir la factura ahora. La venta quedó guardada y la factura se reintenta sola.")
                }
            )
            _uiState.update { s ->
                if (s.invoiceProgress?.saleId != saleId) return@update s
                // Certificada: el ticket del cobro pasa a llevar la factura y el QR, así el
                // "Imprimir ticket" del acuse ya imprime la representación gráfica.
                val certified = progress.result?.takeIf { progress.status == FelInvoiceStatus.CERTIFICADO }
                s.copy(
                    invoiceProgress = progress,
                    lastReceiptData = if (certified != null) s.lastReceiptData?.copy(
                        invoice = com.bakery.erp.model.receiptInvoiceFrom(certified, formatReceiptTimestamp(certified.certifiedAt))
                    ) else s.lastReceiptData
                )
            }
        }
    }

    fun clearLastSaleSummary() { _uiState.update { it.copy(lastSaleSummary = null) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }

    private fun buildReceiptData(sale: Sale, lines: List<CartLine>, branch: Branch): ReceiptData =
        saleReceiptData(
            sale = sale,
            lines = lines.map { l -> ReceiptLine(l.productName, l.quantity, l.unitPrice, l.subtotal) },
            branchName = branch.name,
            branchLocation = branch.location,
            organizationName = organizationName
        )


    // ── Helpers ───────────────────────────────────────────────────────────

    /** Devuelve (precio, source). `salePrice` es la fuente de verdad para retail. */
    private fun resolveProductPrice(product: Product): Pair<Double, String> {
        resellerPriceFor(product)?.let { r ->
            val source = if (r.source == PriceSource.PRODUCT_OVERRIDE) SaleLinePriceSource.PRODUCT_OVERRIDE
                         else SaleLinePriceSource.LIST_FACTOR
            return r.unitPrice to source
        }
        return product.salePrice to SaleLinePriceSource.PRODUCT_SALE_PRICE
    }

    // ── Venta a revendedor ─────────────────────────────────────────────────

    /** Precio del revendedor en curso, o null si no hay venta a revendedor o no tiene precio. */
    private fun resellerPriceFor(product: Product): ResolvedPrice? =
        _uiState.value.resellerSale?.prices?.get(product.id)?.takeIf { it.found && it.unitPrice > 0 }

    /** Lo que la ficha muestra y lo que se cobra. Null = sin precio para este revendedor. */
    fun unitPriceFor(product: Product): Double? =
        if (_uiState.value.resellerSale == null) product.salePrice else resellerPriceFor(product)?.unitPrice

    /** Precio por pan del grupo. Null = el revendedor no tiene un precio único para el grupo. */
    fun groupPriceFor(category: POSCategory): Double? {
        val resale = _uiState.value.resellerSale ?: return category.groupUnitPrice
        return groupListPrice(category, _uiState.value.productsById, resale.prices, resale.list)
    }

    fun loadResellers(companyId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingResellers = true) }
            val list = runCatching { externalCustomerRepository.getCustomers(companyId) }
                .getOrElse { emptyList() }
                .filter {
                    it.type == ExternalCustomerType.RESELLER && it.active &&
                        it.registrationStatus == ResellerRegistrationStatus.ACTIVE
                }
                .sortedBy { it.name.lowercase() }
            _uiState.update { it.copy(resellers = list, isLoadingResellers = false) }
        }
    }

    /**
     * Pasa la caja a venta a revendedor. Lo que ya estaba en el carrito se vuelve a cobrar con
     * la lista del revendedor: la cajera muchas veces empieza a marcar y después se entera de
     * que es revendedor, y obligarla a vaciar el carrito sería volver a marcar todo.
     */
    fun startResellerSale(companyId: String, customer: ExternalCustomer) {
        if (customer.priceListId.isBlank()) {
            _uiState.update {
                it.copy(error = "${customer.name} no tiene lista de precios. Asignale una en Clientes → Revendedores.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                val list = priceListRepository.getList(companyId, customer.priceListId)
                    ?.takeIf { it.active }
                    ?: throw IllegalStateException(
                        "La lista de precios de ${customer.name} no existe o está desactivada."
                    )
                val overrides = priceListRepository.getPricesForList(companyId, list.id)
                ResellerSale(
                    customer = customer,
                    priceListId = list.id,
                    priceListName = list.name,
                    list = list,
                    prices = resolveListPrices(list, overrides, _uiState.value.products)
                )
            }.onSuccess { resale ->
                _uiState.update { it.copy(isLoading = false, resellerSale = resale) }
                repriceCart()
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /** Vuelve a venta de mostrador; lo del carrito regresa al precio público. */
    fun endResellerSale() {
        if (_uiState.value.resellerSale == null) return
        _uiState.update { it.copy(resellerSale = null) }
        repriceCart()
    }

    /**
     * Recalcula el precio de cada renglón del carrito con el modo actual. Lo que no tiene precio
     * para el revendedor se saca y se avisa: dejarlo al precio público le cobraría de más sin
     * que la cajera lo note.
     */
    private fun repriceCart() {
        val state = _uiState.value
        val resale = state.resellerSale
        val categoriesById = state.posCategories.associateBy { it.id }
        val dropped = mutableListOf<String>()
        val repriced = state.cart.mapNotNull { line ->
            when {
                // La bolsa vale lo mismo para todos: no está en ninguna lista de precios.
                line.isRestaurant || line.isPackaging -> line
                line.isGroup -> {
                    val price = categoriesById[line.quickGroupId]?.let { groupPriceFor(it) }
                    if (price == null) { dropped += line.productName; null }
                    else line.copy(unitPrice = price, priceListId = resale?.priceListId ?: retailPriceListId)
                }
                else -> {
                    val product = state.productsById[line.productId]
                    if (product == null || unitPriceFor(product) == null) { dropped += line.productName; null }
                    else {
                        val (price, source) = resolveProductPrice(product)
                        line.copy(unitPrice = price, priceSource = source,
                            priceListId = resale?.priceListId ?: retailPriceListId)
                    }
                }
            }
        }
        _uiState.update {
            it.copy(
                cart = repriced,
                error = if (dropped.isEmpty()) it.error
                        else "Se sacaron del carrito por no tener precio de revendedor: ${dropped.joinToString()}"
            )
        }
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
        // El grupo de precio no tiene un producto contra el cual comparar: su inventario lo
        // reparte el conteo de cierre.
        _uiState.value.cart.filter { it.hasProductStock && it.quantity > stockOf(it.productId) }
}

/** Ventana de ventas que ordena la pestaña "Todos" del POS. */
private const val POPULARITY_DAYS = 30

/** Cuántos cobros guarda "Último cobro" para ir hacia atrás. */
private const val MAX_RECENT_CHECKOUTS = 10

/** Cuánto se muestra, como mínimo, el aviso de "Imprimiendo ticket". */
private const val MIN_PRINTING_FEEDBACK_MS = 1_600L

/** Clave de la venta de pan en el reparto del anticipo (las del restaurante van por su id). */
private const val BAKERY_SALE_KEY = "__bakery__"

private fun money(v: Double): Double = kotlin.math.round(v * 100) / 100

/** Cuánto espera el acuse a que Digifact certifique antes de decir "se completa sola". */
private const val INVOICE_WAIT_MS = 15_000L
