package com.bakery.erp.presentation.purchases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.AssetHistoryRepository
import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.CashSessionRepository
import com.bakery.erp.domain.ExpenseRepository
import com.bakery.erp.domain.FixedAssetRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.NewStockLot
import com.bakery.erp.domain.PurchaseLotEntry
import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.domain.PurchaseInvoiceImageUploader
import com.bakery.erp.domain.PurchaseConceptRepository
import com.bakery.erp.domain.PriceListRepository
import com.bakery.erp.domain.PurchaseRepository
import com.bakery.erp.domain.SupplierRepository
import com.bakery.erp.model.AssetHistoryEntry
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.CashMovement
import com.bakery.erp.model.CashMovementType
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.CashSessionStatus
import com.bakery.erp.model.expectedCashNow
import com.bakery.erp.model.ExpensePaidFrom
import com.bakery.erp.model.FinishedMovementReference
import com.bakery.erp.model.FinishedMovementType
import com.bakery.erp.model.FinishedStockMovement
import com.bakery.erp.model.FixedAsset
import com.bakery.erp.model.IngredientUsage
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Product
import com.bakery.erp.model.PriceListType
import com.bakery.erp.model.ProductPrice
import com.bakery.erp.model.Permission
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.isQuick
import com.bakery.erp.model.listTitle
import com.bakery.erp.model.PURCHASE_BALANCE_EPSILON
import com.bakery.erp.model.PurchaseConcept
import com.bakery.erp.model.afterPurchase
import com.bakery.erp.model.findByName
import com.bakery.erp.model.PurchaseDisplayStatus
import com.bakery.erp.model.PurchaseFilters
import com.bakery.erp.model.PurchaseFormat
import com.bakery.erp.model.RESALE_CATEGORY
import com.bakery.erp.model.RepriceSuggestion
import com.bakery.erp.model.repriceSuggestionFor
import com.bakery.erp.model.effectiveBranchId
import com.bakery.erp.model.effectiveBranchName
import com.bakery.erp.model.PurchaseKind
import com.bakery.erp.model.branchesTouched
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.CostCenter
import com.bakery.erp.model.withExpenseCostCenter
import com.bakery.erp.model.PurchaseLineKind
import com.bakery.erp.model.PurchasePayment
import com.bakery.erp.model.PurchasePeriod
import com.bakery.erp.model.PurchaseSort
import com.bakery.erp.model.PurchaseStatus
import com.bakery.erp.model.StockMovement
import com.bakery.erp.model.Supplier
import com.bakery.erp.model.User
import com.bakery.erp.model.balanceDue
import com.bakery.erp.presentation.assets.applyAssetEntrySideEffects
import com.bakery.erp.presentation.assets.recalcAndPersistAssetCounters
import com.bakery.erp.model.kindsPresent
import com.bakery.erp.model.formatsWithPaidPrices
import com.bakery.erp.model.purchaseFormatsForEditing
import com.bakery.erp.model.suppliedBy
import com.bakery.erp.model.withDerivedPurchaseReference
import com.bakery.erp.model.itemName
import com.bakery.erp.model.nearestExpiry
import com.bakery.erp.model.nearestExpiryLine
import com.bakery.erp.model.purchaseLineTouchesStock
import com.bakery.erp.model.purchaseTotal
import com.bakery.erp.model.taxBreakdownFromGross
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PurchaseUiState(
    // Lista
    val purchases: List<Purchase> = emptyList(),
    val filters: PurchaseFilters = PurchaseFilters(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val isSaved: Boolean = false,
    // Detalle
    val selectedPurchase: Purchase? = null,
    val selectedPurchaseLines: List<PurchaseLine> = emptyList(),
    val selectedPurchasePayments: List<PurchasePayment> = emptyList(),
    val isDetailLoading: Boolean = false,
    val isProcessing: Boolean = false,
    // Formulario — paso 1: sucursal
    val accessibleBranches: List<Branch> = emptyList(),
    val selectedBranch: Branch? = null,
    val isLoadingBranches: Boolean = false,
    // Formulario — paso 2: proveedor
    val suppliers: List<Supplier> = emptyList(),
    val selectedSupplier: Supplier? = null,
    /**
     * Proveedor ocasional escrito a mano: la tienda de la esquina, el puesto del mercado.
     * Solo se usa cuando [selectedSupplier] es null. No se da de alta como `Supplier` a
     * propósito — un proveedor al que le comprás una vez no es un proveedor, y el catálogo
     * termina lleno de nombres que nadie vuelve a tocar.
     */
    val occasionalSupplierName: String = "",
    // Formulario — paso 3: líneas de insumos
    /**
     * Productos de reventa a los que la factura recién registrada les subió el costo, con el
     * precio que conserva su margen. Se llena DESPUÉS de guardar, a propósito: la compra es un
     * hecho y el precio de venta es otra decisión —a veces de otra persona—, y bloquear el
     * guardado con una pregunta de precios hace que se cierre sin leer para poder seguir.
     */
    val repriceSuggestions: List<RepriceSuggestion> = emptyList(),
    val supplierIngredients: List<Ingredient> = emptyList(),
    val isLoadingIngredients: Boolean = false,
    // Formulario — paso 3b: productos de reventa del proveedor
    val supplierResaleProducts: List<Product> = emptyList(),
    /**
     * Suministros de operación del proveedor (cloro, papel). Van en su propia lista y no
     * mezclados con la materia prima: quien captura la factura de la abarrotería lee un
     * bloque, y quien captura la del molino lee el otro.
     */
    val supplierSupplies: List<Ingredient> = emptyList(),
    /** Activos a los que se le puede imputar una línea de servicio (horno, camioneta). */
    val assets: List<FixedAsset> = emptyList(),
    /** Flujos que la empresa tiene encendidos. Ver `Organization.usesResalePurchases`. */
    val usesResale: Boolean = false,
    val usesSupplies: Boolean = false,
    val usesServices: Boolean = false,
    /**
     * `true` cuando ya se leyó la configuración de la empresa. Mientras es `false` los tres
     * flags de arriba valen su default, que es indistinguible de "apagado" — y por eso
     * `CaptureKindStep` no dibuja sus opciones hasta acá: sin esto la de reventa aparecía
     * sola un par de segundos después de las otras dos, como si el toque anterior la
     * hubiera invocado.
     */
    val purchaseFlowsLoaded: Boolean = false,
    /** Conceptos sin inventario que se ofrecen al capturar. Vacío si la empresa no usa servicios. */
    val purchaseConcepts: List<PurchaseConcept> = emptyList(),
    /** Catálogo completo, solo para el picker de "agregar insumo fuera del proveedor". */
    val allIngredients: List<Ingredient> = emptyList(),
    /**
     * Productos de reventa de TODA la empresa, sin filtrar por proveedor.
     *
     * [supplierResaleProducts] no sirve para la compra rápida: solo se llena al elegir un
     * proveedor del catálogo, y una compra rápida no tiene proveedor. Mientras esta lista no
     * existió, la sección de reventa del diálogo rápido recibía siempre una lista vacía y
     * las tostadas compradas en el mercado caían como gasto suelto, sin entrar nunca al
     * inventario de tienda.
     */
    val allResaleProducts: List<Product> = emptyList(),
    val draftLines: List<PurchaseLine> = emptyList(),
    /**
     * Caja a la que se carga lo que se está anotando. La fija quien abre la pantalla —el POS
     * pasa la sesión que se está operando— y **nunca se elige de una lista**: quien anota
     * suele ser el mismo que está cobrando, con gente esperando, y un desplegable de cajas
     * es una forma de marcar la equivocada sin darse cuenta. El precio de ese error no lo
     * paga quien lo comete sino la cajera de la otra gaveta, que aparece con un faltante que
     * no es suyo. `null` = caja general, que es el caso del acceso de Operaciones.
     */
    val pinnedSession: CashSession? = null,
    /** Compra que se está editando; null = alta. */
    val editingPurchase: Purchase? = null,
    /** Líneas tal como estaban al abrir la edición — la base para corregir el stock. */
    val editingOriginalLines: List<PurchaseLine> = emptyList(),
    val isLoadingEdit: Boolean = false,
    val isDeleted: Boolean = false
) {
    /** Búsqueda de la lista — vive dentro de [filters], se expone aparte por comodidad. */
    val searchQuery: String get() = filters.query
}

/** Resultado de escanear un código en el formulario de compra. */
sealed interface BarcodeMatch {
    data class OfIngredient(val ingredient: Ingredient) : BarcodeMatch
    data class OfProduct(val product: Product) : BarcodeMatch
}

class PurchaseViewModel(
    private val purchaseRepository: PurchaseRepository,
    private val ingredientRepository: IngredientRepository,
    private val supplierRepository: SupplierRepository,
    private val branchRepository: BranchRepository,
    private val finishedStockRepository: FinishedStockRepository,
    private val productRepository: ProductRepository,
    private val invoiceImageUploader: PurchaseInvoiceImageUploader,
    // Flujo de administración: una línea de suministro o de servicio deja un gasto en el
    // mes, y una de servicio con activo declarado deja además su evento en el historial.
    private val expenseRepository: ExpenseRepository,
    private val assetRepository: FixedAssetRepository,
    private val assetHistoryRepository: AssetHistoryRepository,
    // Solo para leer qué flujos de compra tiene encendidos la empresa.
    private val authRepository: AuthRepository,
    // Una compra rápida pagada desde una gaveta escribe además el `CashMovement` que la
    // descuenta al cuadrar. Ese movimiento es lo único que hace que el faltante de la caja
    // esté explicado; sin él la cajera aparece corta y nadie sabe por qué.
    private val cashSessionRepository: CashSessionRepository,
    // Catálogo de cosas que se compran seguido y no llevan inventario (bolsas, fletes). Es lo
    // que permite que el historial del proveedor se pueda sumar. Ver `PurchaseConcept`.
    private val conceptRepository: PurchaseConceptRepository,
    // Subir el precio de venta de un producto de reventa NO es escribir `salePrice` y ya:
    // ese campo es la fuente de verdad de la lista RETAIL predeterminada y el formulario del
    // producto escribe los dos. Escribiendo solo uno, el POS podría seguir cobrando el viejo.
    private val priceListRepository: PriceListRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseUiState())
    val uiState: StateFlow<PurchaseUiState> = _uiState.asStateFlow()

    private var purchasesJob: Job? = null

    /**
     * Escucha la lista mientras la pantalla está abierta: una compra registrada (o abonada,
     * o borrada) en otro equipo aparece sola. Antes era una lectura única y el otro equipo
     * no la veía hasta reiniciar la app. Lo suelta [stopObservingPurchases].
     */
    fun loadPurchases(companyId: String, currentUser: User) {
        purchasesJob?.cancel()
        // Solo el primer llenado muestra el spinner; si ya hay lista, se refresca encima.
        _uiState.update { it.copy(isLoading = it.purchases.isEmpty(), error = null) }
        purchasesJob = viewModelScope.launch {
            purchaseRepository.observePurchases(companyId)
                .catch { e -> _uiState.update { s -> s.copy(isLoading = false, error = e.message) } }
                .collect { all ->
                    val filtered = all.filter { currentUser.canAccessBranch(it.branchId) }
                    _uiState.update { it.copy(purchases = filtered, isLoading = false) }
                }
        }
        // Los proveedores alimentan el filtro por proveedor de la lista, no solo el formulario.
        viewModelScope.launch {
            val suppliers = runCatching { supplierRepository.getSuppliers(companyId) }.getOrElse { emptyList() }
            _uiState.update { if (it.suppliers.isEmpty()) it.copy(suppliers = suppliers) else it }
        }
    }

    fun stopObservingPurchases() {
        purchasesJob?.cancel()
        purchasesJob = null
    }

    fun loadPurchaseDetail(companyId: String, purchaseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true) }
            val purchase = runCatching { purchaseRepository.getPurchase(companyId, purchaseId) }.getOrNull()
            val lines = if (purchase != null)
                runCatching { purchaseRepository.getLines(companyId, purchaseId) }.getOrElse { emptyList() }
            else emptyList()
            // Quien recibe la mercadería compara el dibujo de la caja contra el renglón, y la
            // foto vive en el producto, no en la línea. Se resuelve contra el catálogo en vez
            // de guardarla como snapshot para que las compras VIEJAS también la muestren.
            if (lines.any { it.productId.isNotEmpty() }) ensureResaleCatalog(companyId)
            val payments = if (purchase != null)
                runCatching { purchaseRepository.getPayments(companyId, purchaseId) }.getOrElse { emptyList() }
            else emptyList()
            _uiState.update {
                it.copy(
                    selectedPurchase = purchase?.withFallbackTotal(lines),
                    selectedPurchaseLines = lines,
                    selectedPurchasePayments = payments,
                    isDetailLoading = false
                )
            }
        }
    }

    /**
     * Carga el catálogo de reventa una sola vez. Lo llena normalmente `selectSupplier`, pero al
     * detalle de una compra se puede llegar directo —desde la lista, o de un push— sin haber
     * pasado nunca por la captura.
     */
    private suspend fun ensureResaleCatalog(companyId: String) {
        if (_uiState.value.allResaleProducts.isNotEmpty()) return
        val all = runCatching { productRepository.getProducts(companyId) }.getOrElse { return }
        val resale = all.filter { it.active && !it.isInHouseProduced }.sortedBy { it.name.lowercase() }
        _uiState.update { it.copy(allResaleProducts = resale) }
    }

    fun selectPurchase(purchase: Purchase, companyId: String) {
        _uiState.update {
            it.copy(
                selectedPurchase = purchase,
                selectedPurchaseLines = emptyList(),
                selectedPurchasePayments = emptyList(),
                isDetailLoading = true
            )
        }
        viewModelScope.launch {
            val lines = runCatching { purchaseRepository.getLines(companyId, purchase.id) }.getOrElse { emptyList() }
            val payments = runCatching { purchaseRepository.getPayments(companyId, purchase.id) }.getOrElse { emptyList() }
            _uiState.update {
                it.copy(
                    selectedPurchase = it.selectedPurchase?.takeIf { p -> p.id == purchase.id }?.withFallbackTotal(lines)
                        ?: it.selectedPurchase,
                    selectedPurchaseLines = lines,
                    selectedPurchasePayments = payments,
                    isDetailLoading = false
                )
            }
        }
    }

    /**
     * Las compras registradas antes de que el encabezado guardara `totalAmount` traen 0.
     * En el detalle, donde las líneas ya están cargadas, se calcula al vuelo para no mostrar
     * "Q0.00" sobre una factura que sí tiene monto. En la LISTA no se puede hacer esto —
     * ahí el arreglo es el script de backfill (`functions/tools/backfill-purchases.js`).
     */
    private fun Purchase.withFallbackTotal(lines: List<PurchaseLine>): Purchase =
        if (totalAmount <= 0.0 && lines.isNotEmpty()) copy(totalAmount = lines.purchaseTotal()) else this

    fun clearSelectedPurchase() {
        _uiState.update {
            it.copy(
                selectedPurchase = null,
                selectedPurchaseLines = emptyList(),
                selectedPurchasePayments = emptyList()
            )
        }
    }

    // ── Filtros de la lista ──────────────────────────────────────────────────

    private fun updateFilters(transform: (PurchaseFilters) -> PurchaseFilters) {
        _uiState.update { it.copy(filters = transform(it.filters)) }
    }

    fun updateSearchQuery(query: String) = updateFilters { it.copy(query = query) }

    fun setPeriod(period: PurchasePeriod, from: Long? = null, until: Long? = null) =
        updateFilters { it.copy(period = period, customFrom = from ?: it.customFrom, customUntil = until ?: it.customUntil) }

    fun toggleStatusFilter(status: PurchaseDisplayStatus) = updateFilters {
        it.copy(statuses = if (status in it.statuses) it.statuses - status else it.statuses + status)
    }

    fun toggleBranchFilter(branchId: String) = updateFilters {
        it.copy(branchIds = if (branchId in it.branchIds) it.branchIds - branchId else it.branchIds + branchId)
    }

    fun toggleSupplierFilter(supplierId: String) = updateFilters {
        it.copy(supplierIds = if (supplierId in it.supplierIds) it.supplierIds - supplierId else it.supplierIds + supplierId)
    }

    fun setSort(sort: PurchaseSort) = updateFilters { it.copy(sort = sort) }

    /**
     * "Mostrame lo vencido" abre SIEMPRE todo el histórico, no el período en pantalla.
     *
     * Una deuda no pertenece a un mes: la factura que hay que ir a pagar hoy suele ser de
     * hace tres meses, y dejar ese filtro acotado al mes en curso devolvía una lista vacía
     * justo cuando había dinero vencido.
     */
    fun showOverdue() = updateFilters {
        it.copy(period = PurchasePeriod.ALL, statuses = setOf(PurchaseDisplayStatus.OVERDUE))
    }

    /** Limpia todo menos el período: cambiar de mes es navegación, no un filtro que estorbe. */
    fun clearFilters() = updateFilters {
        PurchaseFilters(period = it.period, customFrom = it.customFrom, customUntil = it.customUntil)
    }

    /** Compatibilidad con el filtro de sucursal único anterior. */
    fun setBranchFilter(branchId: String?) = updateFilters {
        it.copy(branchIds = if (branchId == null) emptySet() else setOf(branchId))
    }

    // ── Formulario ───────────────────────────────────────────────────────────

    fun loadFormData(companyId: String, currentUser: User) {
        // La configuración de la empresa va en su PROPIA corrutina, no al final de la
        // cadena. Es una sola lectura, pero encolada detrás de sucursales + proveedores +
        // insumos llegaba segundos tarde, y de ella cuelga qué opciones ofrece la pantalla
        // de "¿qué vas a anotar?": las dos primeras salían de inmediato y la de reventa se
        // agregaba sola después.
        viewModelScope.launch { loadPurchaseFlows(companyId) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBranches = true) }
            val allBranches = runCatching { branchRepository.getBranches(companyId) }.getOrElse { emptyList() }
            val accessible = allBranches.filter { branch ->
                branch.active &&
                currentUser.canAccessBranch(branch.id) &&
                // Insumos → CAN_PRODUCE/IS_WAREHOUSE; reventa → CAN_SELL (el stock de reventa
                // vive donde se vende). Se incluyen todas para no bloquear ninguno de los dos flujos.
                (BranchCapability.CAN_PRODUCE in branch.capabilities ||
                 BranchCapability.IS_WAREHOUSE in branch.capabilities ||
                 BranchCapability.CAN_SELL in branch.capabilities)
            }
            val suppliers = runCatching { supplierRepository.getSuppliers(companyId) }.getOrElse { emptyList() }
                .filter { it.active }
            val allIngredients = runCatching { ingredientRepository.getIngredients(companyId) }
                .getOrElse { emptyList() }.filter { it.active }
            _uiState.update {
                it.copy(
                    allIngredients = allIngredients,
                    accessibleBranches = accessible,
                    selectedBranch = if (accessible.size == 1) accessible.first() else it.selectedBranch,
                    suppliers = suppliers,
                    isLoadingBranches = false
                )
            }
        }
    }

    /**
     * Lee qué flujos de compra tiene encendidos la empresa y, si usa servicios, trae los
     * activos a los que se les puede imputar un gasto.
     *
     * Es una lectura por apertura del formulario y no un dato en `User` a propósito: la
     * configuración cambia poco pero el usuario ya está cacheado en sesión, y arrastrarla ahí
     * significaría que encender un flujo no se ve hasta volver a entrar a la app.
     */
    private suspend fun loadPurchaseFlows(companyId: String) {
        val org = authRepository.getOrganization(companyId).getOrNull() ?: run {
            // Sin organización legible no hay nada que esperar: se marca resuelto igual para
            // no dejar la pantalla de elección en un spinner eterno.
            _uiState.update { it.copy(purchaseFlowsLoaded = true) }
            return
        }
        _uiState.update {
            it.copy(
                usesResale = org.usesResalePurchases,
                usesSupplies = org.usesSupplyPurchases,
                usesServices = org.usesServicePurchases,
                purchaseFlowsLoaded = true
            )
        }
        if (org.usesServicePurchases && _uiState.value.assets.isEmpty()) {
            val assets = runCatching { assetRepository.getAssets(companyId) }.getOrElse { emptyList() }
            _uiState.update { st -> st.copy(assets = assets.filter { it.active }) }
        }
        // El catálogo de conceptos se lee entero: son decenas de nombres, no miles, y sirve
        // tanto para sugerir como para evitar que el mismo se dé de alta dos veces escrito
        // distinto. Que falle no puede impedir capturar: se sigue con texto libre.
        //
        // NO se gatea con `usesServicePurchases`: esa bandera decide si la FACTURA muestra su
        // sección de servicios, y el gasto anotado desde la caja no pasa por ahí. Gatearlo
        // dejaría al caso más común —el gasto del día— sin catálogo y sin historial sumable.
        if (_uiState.value.purchaseConcepts.isEmpty()) {
            val concepts = runCatching { conceptRepository.getConcepts(companyId) }
                .getOrElse { emptyList() }
            _uiState.update { st -> st.copy(purchaseConcepts = concepts.filter { it.active }) }
        }
        // El catálogo de reventa se trae acá y no en `selectSupplier` porque la compra
        // rápida lo necesita sin proveedor de por medio. Ver `allResaleProducts`.
        if (org.usesResalePurchases && _uiState.value.allResaleProducts.isEmpty()) {
            val products = runCatching { productRepository.getProducts(companyId) }.getOrElse { emptyList() }
            _uiState.update { st ->
                st.copy(
                    allResaleProducts = products
                        .filter { it.active && !it.isInHouseProduced }
                        .sortedBy { it.name.lowercase() }
                )
            }
        }
    }

    /**
     * Prepara el formulario para editar una compra ya registrada.
     *
     * Al catálogo del proveedor se le UNEN los insumos que la compra ya trae: si a un insumo
     * le quitaron la presentación de ese proveedor después de registrar la factura, dejaría
     * de tener fila en pantalla y su línea quedaría sumando en el total sin poder tocarla.
     */
    fun loadPurchaseForEdit(companyId: String, purchaseId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEdit = true, error = null) }
            val purchase = runCatching { purchaseRepository.getPurchase(companyId, purchaseId) }.getOrNull()
            if (purchase == null) {
                _uiState.update { it.copy(isLoadingEdit = false, error = "No se encontró la compra") }
                return@launch
            }
            val lines = runCatching { purchaseRepository.getLines(companyId, purchaseId) }.getOrElse { emptyList() }
            val branches = runCatching { branchRepository.getBranches(companyId) }.getOrElse { emptyList() }
            val suppliers = runCatching { supplierRepository.getSuppliers(companyId) }.getOrElse { emptyList() }
            val catalog = runCatching { ingredientRepository.getIngredients(companyId) }
                .getOrElse { emptyList() }.filter { it.active }
            val products = runCatching { productRepository.getProducts(companyId) }.getOrElse { emptyList() }

            val lineIngredientIds = lines.map { it.ingredientId }.filter { it.isNotEmpty() }.toSet()
            val (editSupplies, editProduction) = catalog.filter { ing ->
                ing.purchaseFormats.any { it.supplierId == purchase.supplierId } || ing.id in lineIngredientIds
            }.sortedBy { it.name.lowercase() }
                .partition { it.usage == IngredientUsage.SUPPLY }
            val lineProductIds = lines.map { it.productId }.filter { it.isNotEmpty() }.toSet()
            val resale = products.filter { p ->
                (p.active && !p.isInHouseProduced && p.suppliedBy(purchase.supplierId)) || p.id in lineProductIds
            }.sortedBy { it.name.lowercase() }

            _uiState.update {
                it.copy(
                    isLoadingEdit = false,
                    editingPurchase = purchase,
                    editingOriginalLines = lines,
                    draftLines = lines,
                    accessibleBranches = branches.filter { b -> currentUser.canAccessBranch(b.id) },
                    selectedBranch = branches.find { b -> b.id == purchase.branchId },
                    suppliers = suppliers,
                    selectedSupplier = suppliers.find { sup -> sup.id == purchase.supplierId },
                    // Compra a proveedor ocasional: no hay ficha que seleccionar, el nombre
                    // guardado es todo lo que hay. Sin esto reabrirla lo borraría.
                    occasionalSupplierName =
                        if (purchase.supplierId.isEmpty()) purchase.supplierName else "",
                    allIngredients = catalog,
                    supplierIngredients = editProduction,
                    supplierSupplies = editSupplies,
                    supplierResaleProducts = resale
                )
            }
            // También en edición: sin las banderas, las secciones de suministros y servicios
            // no se dibujarían y la factura se guardaría perdiendo esas líneas.
            loadPurchaseFlows(companyId)
        }
    }

    /**
     * Guarda la edición y **corrige el inventario por diferencia**, no reescribiendo.
     *
     * Se compara línea por línea contra lo que había: solo se mueve el delta, y queda un
     * movimiento de Kardex tipo AJUSTE que documenta la corrección. Reaplicar la compra
     * entera duplicaría el ingreso, y borrar y volver a sumar dejaría el histórico mintiendo
     * sobre lo que realmente pasó ese día.
     *
     * Una compra en `PENDING_RECEIPT` no tocó inventario, así que editarla tampoco lo toca.
     */
    fun updatePurchase(
        companyId: String,
        currentUser: User,
        invoiceNumber: String?,
        notes: String?,
        date: Long,
        status: String,
        paymentTermsDays: Int,
        dueDate: Long,
        taxRatePercent: Double,
        declaredTotal: Double,
        invoicePhotoBytes: ByteArray? = null,
        /** A quién le corresponde lo que la factura gasta. Ver [CostCenter]. */
        expenseCostCenter: String = CostCenter.BRANCH
    ) {
        viewModelScope.launch {
            val original = _uiState.value.editingPurchase ?: return@launch
            val branch = _uiState.value.selectedBranch ?: return@launch
            val supplier = _uiState.value.selectedSupplier
            // Igual que al registrar: el proveedor puede ser ocasional. Al reabrir una compra
            // que ya lo era, el nombre guardado es el que sigue valiendo si no se tocó nada.
            val supplierLabel = supplier?.company
                ?: _uiState.value.occasionalSupplierName.trim().ifEmpty { original.supplierName }
            val newLines = _uiState.value.draftLines.withExpenseCostCenter(expenseCostCenter)
            if (newLines.isEmpty()) {
                _uiState.update { it.copy(error = "La compra debe llevar al menos una línea") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val oldLines = _uiState.value.editingOriginalLines
                val total = newLines.purchaseTotal()
                val photoUrl = invoicePhotoBytes?.let { bytes ->
                    runCatching {
                        invoiceImageUploader.uploadInvoicePhoto(bytes, companyId, original.id)
                    }.getOrNull()
                } ?: original.invoicePhotoUrl
                val updated = original.copy(
                    branchId = branch.id,
                    branchName = branch.name,
                    supplierId = supplier?.id.orEmpty(),
                    supplierName = supplierLabel,
                    date = date,
                    notes = notes?.trim()?.ifEmpty { null },
                    invoiceNumber = invoiceNumber?.trim()?.ifEmpty { null },
                    status = status,
                    totalAmount = total,
                    taxAmount = taxBreakdownFromGross(total, taxRatePercent).tax,
                    taxRatePercent = taxRatePercent,
                    declaredTotal = declaredTotal,
                    // El repo también lo denormaliza al escribir; acá se replica para que la
                    // lista filtre bien sin tener que releer la compra.
                    lineKinds = newLines.kindsPresent(),
                    paymentTermsDays = paymentTermsDays,
                    dueDate = dueDate,
                    receivedAt = if (status == PurchaseStatus.PENDING_RECEIPT) 0L
                                 else original.receivedAt.takeIf { it > 0L } ?: date,
                    invoicePhotoUrl = photoUrl
                )
                purchaseRepository.updatePurchase(companyId, updated, newLines)

                val wasReceived = original.status != PurchaseStatus.PENDING_RECEIPT
                val isReceived = status != PurchaseStatus.PENDING_RECEIPT
                val perishableIds = perishableIngredientIds(companyId)
                when {
                    // Estaba y sigue recibida → solo la diferencia.
                    wasReceived && isReceived ->
                        applyStockDelta(companyId, updated, oldLines, newLines, currentUser, perishableIds)
                    // Nunca entró y ahora sí (se marcó recibida al editar) → ingresa completa.
                    !wasReceived && isReceived ->
                        applyStockForPurchase(companyId, updated, newLines, currentUser, perishableIds)
                    // Estaba recibida y se devuelve a pendiente → se revierte todo lo que entró.
                    wasReceived && !isReceived ->
                        applyStockDelta(companyId, updated, oldLines, emptyList(), currentUser, perishableIds)
                }
                if (isReceived) {
                    reconcileLotsPerBranch(companyId, updated, newLines, perishableIds)
                }
                // Los gastos siguen la misma regla que el stock: una factura pendiente de
                // recibir todavía no gastó nada, y volverla a pendiente lo revierte.
                syncPurchaseSideEffects(
                    companyId, updated, if (isReceived) newLines else emptyList(), oldLines, currentUser
                )
                syncPurchaseConcepts(companyId, updated.supplierId, newLines, date)
                updated
            }.onSuccess { updated ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSaved = true,
                        draftLines = emptyList(),
                        editingPurchase = null,
                        editingOriginalLines = emptyList(),
                        purchases = it.purchases.map { p -> if (p.id == updated.id) updated else p },
                        selectedPurchase = if (it.selectedPurchase?.id == updated.id) updated else it.selectedPurchase,
                        message = "Compra actualizada"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message ?: "No se pudo actualizar la compra") }
            }
        }
    }

    /**
     * Existencia que una línea toca: sucursal + insumo (o producto). El inventario está
     * llaveado por las dos cosas, así que la corrección por diferencia también tiene que
     * estarlo — si no, mover una papa del restaurante a la panadería se vería como "cero
     * cambio" y ninguna de las dos sucursales se enteraría.
     */
    private data class StockKey(val branchId: String, val itemId: String)

    /**
     * Mueve solo la diferencia entre dos versiones de las líneas. El lote lo maneja aparte
     * `reconcilePurchaseLots`, por eso acá se pasa `skipLots = true`.
     */
    private suspend fun applyStockDelta(
        companyId: String,
        purchase: Purchase,
        oldLines: List<PurchaseLine>,
        newLines: List<PurchaseLine>,
        currentUser: User,
        perishableIds: Set<String>
    ) {
        // La clave incluye la SUCURSAL, no solo el insumo: en una compra rápida la misma
        // papa puede ir a dos sucursales, y agrupando solo por insumo el ajuste caería
        // entero sobre una de las dos. En una factura la sucursal es una sola y esto se
        // comporta igual que antes.
        fun index(lines: List<PurchaseLine>) = lines
            .filter { purchaseLineTouchesStock(it.lineKind) }
            .filter { it.lineKind != PurchaseLineKind.RESALE_PRODUCT && it.ingredientId.isNotEmpty() }
            .groupBy { StockKey(it.effectiveBranchId(purchase), it.ingredientId) }
            .mapValues { (_, v) ->
                Triple(
                    v.sumOf { it.quantityReceived },
                    v.first().ingredientName,
                    v.first().effectiveBranchName(purchase)
                )
            }

        val before = index(oldLines)
        val after = index(newLines)
        (before.keys + after.keys).forEach { key ->
            val oldQty = before[key]?.first ?: 0.0
            val newQty = after[key]?.first ?: 0.0
            val delta = newQty - oldQty
            if (kotlin.math.abs(delta) < 0.0001) return@forEach
            val entry = after[key] ?: before[key] ?: return@forEach
            val name = entry.second
            val branchName = entry.third
            if (key.branchId.isEmpty()) return@forEach
            val stocks = ingredientRepository.getBranchStocksForIngredient(
                companyId, key.itemId, listOf(key.branchId)
            )
            val prev = stocks.find { it.branchId == key.branchId }?.currentStock ?: 0.0
            ingredientRepository.updateBranchStock(
                companyId, key.itemId, name,
                key.branchId, branchName, delta,
                skipLots = true
            )
            ingredientRepository.addStockMovement(
                companyId, key.itemId, StockMovement(
                    type = "AJUSTE",
                    quantity = delta,
                    balanceBefore = prev,
                    balanceAfter = (prev + delta).coerceAtLeast(0.0),
                    branchId = key.branchId,
                    branchName = branchName,
                    reference = purchase.id,
                    date = Clock.System.now().toEpochMilliseconds(),
                    notes = "Corrección de la compra " +
                        (purchase.invoiceNumber?.let { "Nº $it" } ?: "a ${purchase.supplierName}"),
                        createdByUserId = currentUser.id,
                        createdByUserName = currentUser.displayName.ifBlank { currentUser.email },
                        createdByEmployeeId = currentUser.operatorEmployeeId
                )
            )
        }

        // Reventa: mismo criterio, contra el inventario de producto terminado.
        fun indexResale(lines: List<PurchaseLine>) = lines
            .filter { purchaseLineTouchesStock(it.lineKind) }
            .filter { it.lineKind == PurchaseLineKind.RESALE_PRODUCT && it.productId.isNotEmpty() }
            .groupBy { StockKey(it.effectiveBranchId(purchase), it.productId) }
            .mapValues { (_, v) -> v.sumOf { it.quantityReceived } to v.first() }

        val beforeR = indexResale(oldLines)
        val afterR = indexResale(newLines)
        (beforeR.keys + afterR.keys).forEach { key ->
            val delta = (afterR[key]?.first ?: 0.0) - (beforeR[key]?.first ?: 0.0)
            if (kotlin.math.abs(delta) < 0.0001) return@forEach
            val ref = afterR[key]?.second ?: beforeR[key]?.second ?: return@forEach
            if (key.branchId.isEmpty()) return@forEach
            finishedStockRepository.applyMovement(
                companyId,
                FinishedStockMovement(
                    productId = key.itemId,
                    productName = ref.productName,
                    branchId = key.branchId,
                    branchName = ref.effectiveBranchName(purchase),
                    type = FinishedMovementType.AJUSTE,
                    quantity = delta,
                    unitPriceAtMovement = ref.unitCost,
                    referenceType = FinishedMovementReference.PURCHASE,
                    referenceId = purchase.id,
                    reason = "Corrección de compra a ${purchase.supplierName}",
                    createdByUserId = currentUser.id,
                    createdByUserName = currentUser.displayName.ifEmpty { currentUser.email }
                )
            )
        }
    }

    /**
     * Elimina una compra y **revierte lo que hizo**: saca del inventario lo que había
     * ingresado, deja sus lotes en saldo cero y borra líneas, abonos y encabezado.
     *
     * Se reutiliza [applyStockDelta] con la lista nueva vacía, que es exactamente "quitá todo
     * lo que esta compra metió": así el reverso usa el mismo camino ya probado del ajuste por
     * diferencia, y queda igualmente un movimiento de Kardex documentándolo. Una compra en
     * `PENDING_RECEIPT` nunca tocó inventario, así que solo se borra el documento.
     */
    fun deletePurchase(companyId: String, purchaseId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            runCatching {
                val purchase = purchaseRepository.getPurchase(companyId, purchaseId)
                    ?: error("No se encontró la compra")
                // Borrar mueve inventario y dinero hacia atrás, así que pide permiso. La
                // excepción es lo que se pagó de la propia gaveta mientras sigue abierta:
                // ver `canManageOwnDrawerPurchase`.
                if (!currentUser.hasPermission(Permission.DELETE_PURCHASES) &&
                    !canManageOwnDrawerPurchase(companyId, purchase, currentUser)
                ) {
                    error("No tenés permiso para eliminar esta compra")
                }
                val lines = purchaseRepository.getLines(companyId, purchaseId)
                if (purchase.status != PurchaseStatus.PENDING_RECEIPT) {
                    applyStockDelta(
                        companyId, purchase, lines, emptyList(), currentUser,
                        perishableIngredientIds(companyId)
                    )
                    // Sus lotes quedan en cero: borrarlos dejaría huérfano el consumo que ya
                    // se hizo contra ellos.
                    // Lista vacía = "agotá todo lo que esta compra metió", en cada sucursal
                    // que haya tocado.
                    runCatching {
                        reconcileLotsPerBranch(
                            companyId, purchase, emptyList(), emptySet(),
                            extraBranches = lines.branchesTouched(purchase)
                        )
                    }
                }
                // Los gastos y los eventos de activo se van con la factura. La lista nueva va
                // vacía —"quitá todo lo que esta compra dejó"— y las líneas viejas dicen a
                // qué activos hay que ir a limpiar. Se llama también en `PENDING_RECEIPT`:
                // no debería haber dejado nada, pero si lo hizo, acá se recoge.
                syncPurchaseSideEffects(companyId, purchase, emptyList(), lines, currentUser)

                // Si salió de una gaveta, ese efectivo vuelve. Se hace con el MISMO helper que
                // lo descontó, con el total nuevo en cero: un reverso escrito aparte termina
                // usando otra cantidad y deja la caja cuadrando contra un gasto que ya no
                // existe. Si la caja ya cerró no se toca —su cierre está firmado— y se avisa.
                val drawer = purchase.cashSessionId.takeIf { it.isNotEmpty() }?.let { sid ->
                    runCatching { cashSessionRepository.getSession(companyId, sid) }
                        .getOrNull()
                        ?.takeIf { it.status == CashSessionStatus.OPEN }
                }
                syncPurchaseCashMovement(
                    companyId = companyId,
                    session = drawer,
                    previousTotal = purchase.totalAmount,
                    newTotal = 0.0,
                    concept = purchase.listTitle(),
                    currentUser = currentUser,
                    purchaseId = purchaseId,
                    isDeletion = true
                )
                purchaseRepository.deletePurchase(companyId, purchaseId)
                purchase.cashSessionId.isNotEmpty() && drawer == null
            }.onSuccess { drawerLocked ->
                _uiState.update { st ->
                    st.copy(
                        isProcessing = false,
                        isDeleted = true,
                        purchases = st.purchases.filterNot { it.id == purchaseId },
                        selectedPurchase = st.selectedPurchase?.takeIf { it.id != purchaseId },
                        selectedPurchaseLines = emptyList(),
                        selectedPurchasePayments = emptyList(),
                        message = if (drawerLocked) {
                            "Compra eliminada. La caja de la que salió ya está cerrada, así que " +
                                "su efectivo no se devolvió."
                        } else "Compra eliminada"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isProcessing = false, error = err.message ?: "No se pudo eliminar la compra")
                }
            }
        }
    }

    fun clearDeleted() { _uiState.update { it.copy(isDeleted = false) } }

    fun cancelEdit() {
        _uiState.update {
            it.copy(
                editingPurchase = null,
                editingOriginalLines = emptyList(),
                draftLines = emptyList(),
                isSaved = false,
                error = null
            )
        }
    }

    /** Proveedor ocasional. Escribirlo descarta el del catálogo, y viceversa. */
    fun setOccasionalSupplier(companyId: String, name: String) {
        _uiState.update {
            it.copy(
                occasionalSupplierName = name,
                selectedSupplier = if (name.isBlank()) it.selectedSupplier else null
            )
        }
    }

    fun selectBranch(branch: Branch?) {
        _uiState.update { it.copy(selectedBranch = branch) }
    }

    fun selectSupplier(companyId: String, supplier: Supplier?) {
        _uiState.update {
            it.copy(
                selectedSupplier = supplier,
                occasionalSupplierName = if (supplier != null) "" else it.occasionalSupplierName,
                draftLines = emptyList(),
                supplierIngredients = emptyList(),
                supplierSupplies = emptyList(),
                supplierResaleProducts = emptyList()
            )
        }
        if (supplier != null) {
            loadIngredientsBySupplier(companyId, supplier.id)
            loadResaleProductsBySupplier(companyId, supplier.id)
        }
    }

    private fun loadIngredientsBySupplier(companyId: String, supplierId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingIngredients = true) }
            val cached = _uiState.value.allIngredients
            val all = cached.ifEmpty {
                runCatching { ingredientRepository.getIngredients(companyId) }.getOrElse { emptyList() }
            }
            // Alfabético: quien captura va leyendo la factura de arriba abajo y busca cada
            // renglón por nombre. El orden de llegada de Firestore es arbitrario y obligaba a
            // recorrer la lista entera por cada insumo.
            val filtered = all.filter { ing ->
                ing.active && ing.purchaseFormats.any { fmt -> fmt.supplierId == supplierId }
            }.sortedBy { it.name.lowercase() }
            // Materia prima y suministros se separan acá y no en la pantalla: son dos
            // bloques distintos de la factura y quien captura lee uno u otro, no los dos
            // revueltos por orden alfabético.
            val (supplies, production) = filtered.partition { it.usage == IngredientUsage.SUPPLY }
            _uiState.update {
                it.copy(
                    allIngredients = it.allIngredients.ifEmpty { all },
                    supplierIngredients = production,
                    supplierSupplies = supplies,
                    isLoadingIngredients = false
                )
            }
        }
    }

    /**
     * Catálogo de reventa del proveedor: los productos que tienen una presentación suya
     * (`suppliedBy`). Un mismo producto aparece en el catálogo de cada proveedor que lo vende —
     * la lata de Coca-Cola la trae la distribuidora y también el mayorista—, y el inventario
     * sigue siendo uno solo porque es la misma lata.
     */
    private fun loadResaleProductsBySupplier(companyId: String, supplierId: String) {
        viewModelScope.launch {
            val all = runCatching { productRepository.getProducts(companyId) }.getOrElse { emptyList() }
            val resale = all.filter { it.active && !it.isInHouseProduced }.sortedBy { it.name.lowercase() }
            _uiState.update {
                it.copy(
                    supplierResaleProducts = resale.filter { p -> p.suppliedBy(supplierId) },
                    // Se refresca también el catálogo completo: es de donde sale el picker de
                    // "agregar producto de este proveedor", y una presentación recién creada
                    // desde el formulario de producto tiene que verse sin reabrir la app.
                    allResaleProducts = resale
                )
            }
        }
    }

    /**
     * Da de alta un insumo que NO existía en ningún catálogo y se lo asocia al proveedor, sin
     * salir de la captura.
     *
     * [addSupplierFormat] resuelve el caso de un insumo que ya existe y al que le falta la
     * presentación de este proveedor. Faltaba el otro: la factura trae algo que nunca se compró,
     * y el único camino era mandarlo a "Servicios y gastos". Eso lo guarda como TEXTO
     * (`conceptName`) — media factura quedaba en el catálogo y la otra media en texto suelto, sin
     * inventario, sin historial sumable por ítem y sin forma de encontrarlo la próxima vez, que
     * es justo lo que el catálogo existe para evitar.
     *
     * Se pide el mínimo que no se puede arreglar después sin re-teclear la factura: nombre, para
     * qué es, y la unidad —de la unidad cuelgan el stock, el Kardex y la conversión de la
     * presentación—. La categoría nace en "Otros" si no la eligen: solo afecta filtros y se
     * corrige en un segundo desde el catálogo.
     */
    fun createSupplierIngredient(
        companyId: String,
        name: String,
        usage: IngredientUsage,
        baseUnit: String,
        category: String,
        tracksStock: Boolean,
        description: String,
        unitsPerPackage: Double,
        packageCost: Double,
        supplierItemName: String = ""
    ) {
        val supplier = _uiState.value.selectedSupplier ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                val ingredient = Ingredient(
                    id = generateId(),
                    name = name.trim(),
                    category = category,
                    usage = usage,
                    allowedUnits = listOf(baseUnit),
                    // La materia prima siempre cuenta: de ese stock sale el descuento de
                    // producción. Solo el suministro puede no hacerlo. Misma regla que
                    // `ingredientPurchaseLineKind`, que tampoco confía en lo que venga marcado.
                    tracksStock = usage == IngredientUsage.PRODUCTION || tracksStock,
                    active = true,
                    createdAt = now,
                    updatedAt = now
                )
                ingredientRepository.addIngredient(companyId, ingredient)
                val saved = ingredientRepository.addPurchaseFormat(
                    companyId, ingredient.id,
                    PurchaseFormat(
                        description = description.trim(),
                        unit = baseUnit,
                        supplierId = supplier.id,
                        supplierName = supplier.company,
                        unitsPerPackage = unitsPerPackage.coerceAtLeast(0.0001),
                        packageCost = packageCost,
                        // Es la primera que tiene, así que es la de costeo por definición.
                        isDefault = true,
                        supplierItemName = supplierItemName.trim()
                    )
                )
                ingredient.copy(purchaseFormats = listOf(saved))
            }.onSuccess { created ->
                _uiState.update { st ->
                    val isSupply = created.usage == IngredientUsage.SUPPLY
                    st.copy(
                        isProcessing = false,
                        supplierIngredients = if (isSupply) st.supplierIngredients
                            else (st.supplierIngredients + created).sortedBy { it.name.lowercase() },
                        supplierSupplies = if (isSupply)
                            (st.supplierSupplies + created).sortedBy { it.name.lowercase() }
                            else st.supplierSupplies,
                        allIngredients = st.allIngredients + created,
                        message = created.name + " creado y agregado a " + supplier.company
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isProcessing = false, error = err.message ?: "No se pudo crear el insumo")
                }
            }
        }
    }

    /**
     * Lo mismo para un producto de reventa que todavía no existe.
     *
     * Acá había una decisión explícita en contra —"dar de alta uno nuevo pide precio de venta,
     * listas y código de barras, y eso no se hace con el repartidor esperando"—. Lo que se
     * corrigió no es esa premisa sino la conclusión: el problema era PEDIR todo eso, no crear el
     * producto. Nace con nombre, unidad de venta y, si lo saben, el precio; lo demás se completa
     * en su ficha cuando haya tiempo. Sin precio todavía no se puede vender en el POS, y el
     * diálogo lo dice — pero la factura entra al inventario con su costo, que es lo único que no
     * se puede recuperar después.
     */
    fun createSupplierResaleProduct(
        companyId: String,
        name: String,
        saleUnit: String,
        salePrice: Double,
        description: String,
        unitsPerPackage: Double,
        packageCost: Double,
        supplierItemName: String = ""
    ) {
        val supplier = _uiState.value.selectedSupplier ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            runCatching {
                val unit = saleUnit.trim().ifBlank { "unidad" }
                val format = PurchaseFormat(
                    id = generateId(),
                    description = description.trim(),
                    unit = unit,
                    supplierId = supplier.id,
                    supplierName = supplier.company,
                    unitsPerPackage = unitsPerPackage.coerceAtLeast(0.0001),
                    packageCost = packageCost,
                    isDefault = true,
                    supplierItemName = supplierItemName.trim()
                )
                val product = Product(
                    id = generateId(),
                    name = name.trim(),
                    category = RESALE_CATEGORY,
                    unit = unit,
                    salePrice = salePrice,
                    isInHouseProduced = false,
                    active = true,
                    purchaseFormats = listOf(format)
                ).withDerivedPurchaseReference()
                productRepository.addProduct(companyId, product)
                product
            }.onSuccess { created ->
                _uiState.update { st ->
                    st.copy(
                        isProcessing = false,
                        supplierResaleProducts = (st.supplierResaleProducts + created)
                            .sortedBy { it.name.lowercase() },
                        allResaleProducts = st.allResaleProducts + created,
                        message = created.name + " creado y agregado a " + supplier.company
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isProcessing = false, error = err.message ?: "No se pudo crear el producto")
                }
            }
        }
    }

    /**
     * Da de alta una presentación de compra de [product] para el proveedor seleccionado y lo
     * suma a su catálogo, sin salir de la captura. Mismo criterio que [addSupplierFormat]: si se
     * lo compraste a este proveedor, eso es una presentación suya, y la próxima factura ya no
     * vuelve a preguntar cuántas latas trae la caja.
     */
    fun addResaleSupplierFormat(
        companyId: String,
        product: Product,
        description: String,
        unitsPerPackage: Double,
        packageCost: Double,
        supplierItemName: String = ""
    ) {
        val supplier = _uiState.value.selectedSupplier ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            runCatching {
                // Un producto viejo guarda su proveedor único fuera de las presentaciones:
                // convertirlo primero evita que agregarle la caja lo borre del catálogo de su
                // proveedor de siempre.
                val existing = product.purchaseFormatsForEditing { generateId() }
                val format = PurchaseFormat(
                    id = generateId(),
                    description = description.trim(),
                    unit = product.unit.ifBlank { "unidad" },
                    supplierId = supplier.id,
                    supplierName = supplier.company,
                    unitsPerPackage = unitsPerPackage.coerceAtLeast(0.0001),
                    packageCost = packageCost,
                    isDefault = existing.isEmpty(),
                    supplierItemName = supplierItemName.trim()
                )
                val updated = product.copy(purchaseFormats = existing + format).withDerivedPurchaseReference()
                productRepository.updatePurchaseFormats(companyId, updated)
                updated
            }.onSuccess { updated ->
                _uiState.update { st ->
                    st.copy(
                        isProcessing = false,
                        supplierResaleProducts = (st.supplierResaleProducts.filterNot { it.id == updated.id } + updated)
                            .sortedBy { it.name.lowercase() },
                        allResaleProducts = st.allResaleProducts.map { if (it.id == updated.id) updated else it },
                        message = "${updated.name} agregado a ${supplier.company}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isProcessing = false, error = err.message ?: "No se pudo agregar la presentación")
                }
            }
        }
    }

    /**
     * Clave estable por línea: insumo (o suministro) por ingredientId, reventa por
     * productId, servicio por su propio id.
     *
     * El servicio necesita id propio porque NO apunta a ningún catálogo: sin esto, dos
     * conceptos distintos de la misma factura ("mantenimiento del horno" y "flete")
     * compartirían clave y el segundo pisaría al primero.
     */
    private fun PurchaseLine.matchKey(): String = when {
        lineKind == PurchaseLineKind.SERVICE -> "S:$id"
        // Por producto SIEMPRE que la línea salga de una fila de producto — incluida la
        // redirigida al insumo (la leche), que se guarda como línea de insumo. Si esa
        // compartiera clave con la fila del insumo, capturar las dos reemplazaría una por
        // otra en silencio en vez de sumarlas, que es lo que el inventario espera.
        productId.isNotEmpty() -> "P:$productId"
        else -> "I:$ingredientId"
    }

    /**
     * Agrega o reemplaza una línea de servicio del borrador. Se le genera id si no lo trae
     * —es la clave con la que se distingue de los demás conceptos de la misma factura.
     */
    fun upsertServiceLine(
        companyId: String,
        line: PurchaseLine,
        newConcept: PurchaseConcept? = null
    ) {
        val withId = if (line.id.isEmpty()) line.copy(id = generateId()) else line
        _uiState.update { state ->
            val others = state.draftLines.filter { it.matchKey() != withId.matchKey() }
            state.copy(draftLines = others + withId)
        }
        // El concepto se da de alta YA, no al guardar la factura: su id tiene que estar en la
        // línea para que el historial la pueda agrupar, y esperarse al final significaría
        // arrastrar el objeto a través de todo el guardado para ponérselo después. Si falla,
        // la línea queda como texto libre — se pierde la agrupación, no el renglón.
        val concept = newConcept ?: return
        viewModelScope.launch {
            val saved = runCatching { conceptRepository.saveConcept(companyId, concept) }
                .getOrNull() ?: return@launch
            _uiState.update { state ->
                state.copy(
                    purchaseConcepts = state.purchaseConcepts + saved,
                    draftLines = state.draftLines.map {
                        if (it.id == withId.id) it.copy(
                            conceptId = saved.id,
                            conceptUnit = it.conceptUnit.ifEmpty { saved.unit }
                        ) else it
                    }
                )
            }
        }
    }

    /**
     * Deja en cada concepto lo que la compra recién guardada le enseñó: a qué proveedor se le
     * compró, a cuánto y cuándo. Nunca lanza — una factura correcta no se puede perder porque
     * falle la actualización de un catálogo de nombres.
     */
    private suspend fun syncPurchaseConcepts(
        companyId: String,
        supplierId: String,
        lines: List<PurchaseLine>,
        date: Long
    ) {
        val byId = _uiState.value.purchaseConcepts.associateBy { it.id }
        lines.filter { it.conceptId.isNotEmpty() }
            .groupBy { it.conceptId }
            .forEach { (conceptId, group) ->
                val current = byId[conceptId] ?: return@forEach
                val line = group.first()
                val updated = current.afterPurchase(supplierId, line.unitCost, date) ?: return@forEach
                runCatching {
                    conceptRepository.recordPurchase(
                        companyId, conceptId, updated.supplierIds,
                        updated.lastUnitCost, updated.lastPurchaseDate
                    )
                }
                _uiState.update { st ->
                    st.copy(purchaseConcepts = st.purchaseConcepts.map { if (it.id == conceptId) updated else it })
                }
            }
    }

    /** Quita una línea del borrador por su clave de catálogo (insumo o producto). */
    fun removeDraftLine(line: PurchaseLine) {
        _uiState.update { state ->
            state.copy(draftLines = state.draftLines.filterNot { it.matchKey() == line.matchKey() })
        }
    }

    fun removeServiceLine(lineId: String) {
        _uiState.update { state ->
            state.copy(
                draftLines = state.draftLines.filterNot {
                    it.lineKind == PurchaseLineKind.SERVICE && it.id == lineId
                }
            )
        }
    }

    // ── Compra rápida ────────────────────────────────────────────────────

    /**
     * Guarda una compra rápida: un solo desembolso, muchas cosas, varias sucursales.
     *
     * Reutiliza `savePurchase` por dentro en vez de tener su propio camino de escritura: los
     * efectos son los mismos (inventario por línea, gasto del mes, Kardex) y duplicarlos
     * sería duplicar el riesgo de doble ingreso, que es lo que este módulo lleva evitando.
     * Lo único propio de la compra rápida es cómo se arman las líneas y que nace pagada — es
     * efectivo, no genera cuenta por pagar.
     */
    internal fun saveQuickPurchase(
        companyId: String,
        currentUser: User,
        defaultBranch: Branch,
        lines: List<QuickPurchaseDraftLine>,
        date: Long,
        notes: String?,
        photoBytes: ByteArray? = null,
        /**
         * Compra que se está corrigiendo, o `null` para dar de alta una nueva.
         *
         * Lo manda la pantalla y no se deduce de `editingPurchase` a propósito: el
         * ViewModel es singleton, así que abrir una corrección y salirse sin guardar deja
         * ese campo puesto. Deducirlo de ahí haría que la SIGUIENTE compra nueva
         * sobreescribiera en silencio la que se había abierto antes.
         */
        purchaseId: String? = null,
        /**
         * Caja de la que salió el efectivo, o `null` si no salió de ninguna gaveta.
         *
         * Se revalida contra Firestore antes de escribir: entre que se abrió la pantalla y
         * se tocó guardar, el cajero pudo haber cerrado su turno, y meterle un movimiento a
         * una caja ya cuadrada le cambiaría un cierre que alguien ya firmó. Si eso pasa la
         * compra se guarda igual como caja general y se avisa — perder el registro del
         * desembolso sería peor que perder la atribución a la gaveta.
         */
        session: CashSession? = null,
        /** Número de la factura o del recibo, cuando el papel lo trae. */
        invoiceNumber: String? = null,
        /**
         * A quién se le compró. Puede ir solo [supplierName] con [supplierId] vacío —el
         * proveedor ocasional, la ferretería de una sola vez— igual que en la factura: al que
         * le comprás una vez no es un proveedor, y darlo de alta llena el catálogo de nombres
         * que nadie vuelve a tocar.
         */
        supplierId: String = "",
        supplierName: String = ""
    ) {
        if (lines.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                // Al corregir se conserva el documento: borrar y recrear le cambiaría el id,
                // y con él se irían los gastos y los lotes que ya cuelgan de esta compra.
                val editing = purchaseId?.let { id ->
                    _uiState.value.editingPurchase?.takeIf { it.id == id && it.isQuick }
                }
                if (editing != null &&
                    !currentUser.hasPermission(Permission.EDIT_PURCHASES) &&
                    !canManageOwnDrawerPurchase(companyId, editing, currentUser)
                ) {
                    error("No tenés permiso para corregir esta compra")
                }
                val docId = purchaseId ?: generateId()
                val photoUrl = photoBytes?.let { bytes ->
                    runCatching {
                        invoiceImageUploader.uploadInvoicePhoto(bytes, companyId, docId)
                    }.getOrNull()
                } ?: editing?.invoicePhotoUrl.orEmpty()
                val purchaseLines = lines.map { it.toPurchaseLine() }
                val total = purchaseLines.purchaseTotal()
                // De qué gaveta salió. Manda la caja que pasa la pantalla; al corregir desde
                // Compras no viene ninguna y entonces manda la que quedó guardada en la
                // compra, que es la que tiene el movimiento a ajustar.
                val intendedSessionId = session?.id?.ifEmpty { null } ?: editing?.cashSessionId.orEmpty()
                val liveSession = intendedSessionId.takeIf { it.isNotEmpty() }?.let { sid ->
                    runCatching { cashSessionRepository.getSession(companyId, sid) }
                        .getOrNull()
                        ?.takeIf { it.status == CashSessionStatus.OPEN }
                }
                // La caja de la que salió ya cerró. La atribución NO se reescribe: el
                // movimiento que la descontó vive dentro de un cierre que alguien firmó, y
                // volverla "caja general" dejaría ese faltante sin nada que lo explique.
                val drawerLocked = liveSession == null && !editing?.cashSessionId.isNullOrEmpty()
                // Antes de escribir NADA: si no alcanza el efectivo, esto revienta y no queda
                // ni compra a medio guardar ni inventario movido.
                assertCashAvailable(
                    session = liveSession,
                    amount = total,
                    alreadyCharged = if (editing != null && editing.cashSessionId == liveSession?.id) {
                        editing.totalAmount
                    } else 0.0
                )
                val base = editing ?: Purchase(
                    id = docId,
                    kind = PurchaseKind.QUICK,
                    receivedById = currentUser.id,
                    receivedByName = currentUser.displayName.ifEmpty { currentUser.email },
                    // Nace saldada: es efectivo del momento, no hay cuenta por pagar que
                    // vigilar ni vencimiento que calcular.
                    status = PurchaseStatus.PAID,
                    paymentTermsDays = 0,
                    dueDate = 0L
                )
                val purchase = base.copy(
                    // Default de las líneas que no declararon sucursal propia.
                    branchId = defaultBranch.id,
                    branchName = defaultBranch.name,
                    // Puede ir vacío: en una vuelta de compras el lugar es de cada línea y el
                    // nombre visible en la lista sale de lo que trae (ver `Purchase.listTitle`).
                    // Se llena cuando el desembolso SÍ tiene un solo dueño —el de gaseosas que
                    // llegó a la puerta—, que es el caso en que uno quiere buscarlo por ahí.
                    supplierId = supplierId,
                    supplierName = supplierName,
                    date = date,
                    notes = notes?.trim()?.ifEmpty { null },
                    totalAmount = total,
                    // Se pagó completa en el momento, así que el abono sigue al total: si al
                    // corregir el monto quedara el viejo, la compra arrastraría un saldo (o un
                    // sobrepago) que nadie debe.
                    paidAmount = total,
                    receivedAt = date,
                    nearestExpiryDate = purchaseLines.nearestExpiry(),
                    nearestExpiryItemName = purchaseLines.nearestExpiryLine()?.itemName.orEmpty(),
                    invoicePhotoUrl = photoUrl,
                    invoiceNumber = invoiceNumber?.trim()?.ifEmpty { null } ?: editing?.invoiceNumber,
                    // La atribución se guarda en el encabezado y de ahí la copian los gastos
                    // que la compra genere. Es el encabezado y no la línea porque es UN
                    // desembolso: todo salió de la misma bolsa.
                    paidFrom = when {
                        liveSession != null -> ExpensePaidFrom.CASH_DRAWER
                        drawerLocked -> editing?.paidFrom.orEmpty()
                        else -> ExpensePaidFrom.GENERAL
                    },
                    cashSessionId = liveSession?.id
                        ?: editing?.cashSessionId.takeIf { drawerLocked }.orEmpty(),
                    cashRegisterName = liveSession?.registerName
                        ?: editing?.cashRegisterName.takeIf { drawerLocked }.orEmpty()
                )
                val perishableIds = perishableIngredientIds(companyId)
                if (editing == null) {
                    purchaseRepository.savePurchase(companyId, purchase, purchaseLines)
                    applyStockForPurchase(companyId, purchase, purchaseLines, currentUser, perishableIds)
                } else {
                    // El inventario se corrige POR DIFERENCIA con el mismo `applyStockDelta`
                    // que usa la factura: reaplicar la compra entera duplicaría el ingreso, y
                    // escribir un reverso aparte perdería las guardas ya probadas.
                    val oldLines = _uiState.value.editingOriginalLines
                    purchaseRepository.updatePurchase(companyId, purchase, purchaseLines)
                    applyStockDelta(companyId, purchase, oldLines, purchaseLines, currentUser, perishableIds)
                    reconcileLotsPerBranch(companyId, purchase, purchaseLines, perishableIds)
                }
                syncPurchaseSideEffects(
                    companyId, purchase, purchaseLines,
                    _uiState.value.editingOriginalLines, currentUser
                )
                // Lo mismo que en la factura: los conceptos del catálogo aprenden a quién se
                // le compró y a cuánto. Con proveedor ocasional (sin id) solo se guarda el
                // precio — no hay ficha a la que agregarlo.
                syncPurchaseConcepts(companyId, supplierId, purchaseLines, date)
                syncPurchaseCashMovement(
                    companyId = companyId,
                    session = liveSession,
                    // Lo que esta compra YA le había descontado a ESTA gaveta. Si la compra
                    // es nueva, o si el desembolso se atribuye ahora a otra caja, no hay
                    // nada previo que compensar y entra el total completo.
                    previousTotal = if (editing != null && editing.cashSessionId == liveSession?.id) {
                        editing.totalAmount
                    } else 0.0,
                    newTotal = total,
                    // De las LÍNEAS y no de `purchase.listTitle()`: los nombres se
                    // denormalizan en el encabezado al escribirlo, así que el objeto que
                    // está acá en memoria todavía los trae vacíos y el movimiento de la
                    // caja habría quedado como "Compra rápida" — que es justo lo que no
                    // sirve cuando alguien busca qué explica el faltante.
                    concept = purchaseLines.map { it.itemName }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .take(3)
                        .joinToString(", ")
                        .ifEmpty { "Compra rápida" },
                    currentUser = currentUser,
                    purchaseId = docId
                )
                // Se avisa solo cuando lo guardado no es lo que el usuario pidió.
                when {
                    drawerLocked -> "Se corrigió la compra, pero la caja \"${'$'}{editing?.cashRegisterName.orEmpty()}\" " +
                        "ya está cerrada: su efectivo no se movió."
                    session != null && liveSession == null ->
                        "La caja se cerró mientras anotabas. Quedó como caja general."
                    else -> null
                }
            }.onSuccess { drawerWarning ->
                val wasEdit = purchaseId != null
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSaved = true,
                        editingPurchase = null,
                        editingOriginalLines = emptyList(),
                        draftLines = emptyList(),
                        pinnedSession = null,
                        // El aviso viaja en `message` y no en `error` porque la pantalla
                        // navega hacia atrás apenas ve `isSaved`: un snackbar lanzado desde
                        // el formulario se va con el formulario. `resetForm` limpia `error`
                        // y no `message`, así que este sí llega a la lista.
                        message = drawerWarning
                            ?: if (wasEdit) "Compra rápida actualizada" else "Compra rápida registrada"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, error = err.message ?: "No se pudo guardar la compra")
                }
            }
        }
    }

    /**
     * Lee la caja a la que se va a cargar lo que se anote. `null` la limpia — el caso del
     * acceso de Operaciones, donde el desembolso es de caja general y no toca ninguna gaveta.
     *
     * Solo se acepta si sigue ABIERTA: cargarle un gasto a una caja ya cuadrada cambiaría un
     * cierre que alguien firmó.
     */
    fun loadPinnedSession(companyId: String, sessionId: String?) {
        if (sessionId.isNullOrEmpty()) {
            _uiState.update { it.copy(pinnedSession = null) }
            return
        }
        viewModelScope.launch {
            val session = runCatching { cashSessionRepository.getSession(companyId, sessionId) }
                .getOrNull()
                ?.takeIf { it.status == CashSessionStatus.OPEN }
            _uiState.update { it.copy(pinnedSession = session) }
        }
    }

    /**
     * Deja la gaveta descontada por lo que esta compra se llevó.
     *
     * Al corregir **no reescribe el movimiento original: escribe la diferencia**, igual que
     * el inventario se corrige por diferencia contra `editingOriginalLines` en vez de
     * reaplicarse. Un movimiento reescrito borraría del historial de la caja el hecho de que
     * el monto cambió, que es exactamente lo que alguien va a querer ver cuando el cuadre no
     * dé; y el repositorio de cajas no expone borrar ni editar movimientos a propósito.
     *
     * Si el total bajó, la diferencia entra como DEPOSIT: ese efectivo nunca salió.
     */
    private suspend fun syncPurchaseCashMovement(
        companyId: String,
        session: CashSession?,
        previousTotal: Double,
        newTotal: Double,
        concept: String,
        currentUser: User,
        /** Compra que provoca el movimiento, para poder volver a ella desde la gaveta. */
        purchaseId: String = "",
        /** `true` cuando el movimiento repone lo de una compra que se está borrando. */
        isDeletion: Boolean = false
    ) {
        if (session == null) return
        val delta = newTotal - previousTotal
        // Centavos de redondeo no son una corrección. Mismo umbral que el saldo de la factura.
        if (kotlin.math.abs(delta) < PURCHASE_BALANCE_EPSILON) return
        val isCorrection = previousTotal > 0.0
        cashSessionRepository.addMovement(
            companyId,
            CashMovement(
                sessionId = session.id,
                type = if (delta > 0) CashMovementType.EXPENSE else CashMovementType.DEPOSIT,
                amount = kotlin.math.abs(delta),
                reason = when {
                    isDeletion -> "Eliminado: $concept"
                    isCorrection -> "Corrección: $concept"
                    else -> concept
                },
                createdByUserId = currentUser.id,
                createdByUserName = currentUser.displayName.ifEmpty { currentUser.email },
                sourcePurchaseId = purchaseId
            )
        )
    }

    /**
     * Deja que quien opera una caja corrija o elimine **lo que se pagó de su propia gaveta**,
     * aunque no tenga los permisos de Compras.
     *
     * Es el error del día: se tecleó 325 en vez de 32.50, o el dinero no salió de esa caja.
     * Exigir `EDIT_PURCHASES` para eso obliga a la cajera a llamar a alguien antes de cerrar,
     * y el desvío termina cuadrándose a mano — que es exactamente lo que este módulo evita.
     *
     * El permiso amplio sigue mandando; esto solo lo suple cuando se cumplen las tres cosas:
     * es una compra rápida, está atribuida a una gaveta, y esa gaveta **sigue abierta**. Con
     * la caja cerrada nadie la toca: ese cierre ya lo firmó alguien. Y nada se pierde, porque
     * tanto corregir como borrar dejan su movimiento en el historial de la caja.
     */
    private suspend fun canManageOwnDrawerPurchase(
        companyId: String,
        purchase: Purchase?,
        currentUser: User
    ): Boolean {
        if (purchase == null || !purchase.isQuick) return false
        val sid = purchase.cashSessionId.takeIf { it.isNotEmpty() } ?: return false
        val session = runCatching { cashSessionRepository.getSession(companyId, sid) }.getOrNull()
        return session?.status == CashSessionStatus.OPEN &&
            currentUser.hasPermission(Permission.ADD_VARIABLE_EXPENSE)
    }

    /**
     * Se niega a pagar de una gaveta más efectivo del que hay en ella.
     *
     * La cajera lleva vendido Q300 y quiere pagar un gasto de Q325: ese billete no existe.
     * Dejarlo pasar no "documenta un faltante", **inventa uno**: el cuadre del cierre pediría
     * devolver dinero que nunca entró, y el reclamo cae sobre quien anotó de buena fe.
     *
     * El tope es `expectedCashNow` —fondo de cambio + ventas en efectivo + movimientos—, y se
     * calcula sobre la sesión **recién leída**, no sobre la que traía la pantalla: entre abrir
     * la captura y guardar pudo entrar una venta o salir un retiro. Mismo criterio que
     * `addPayment`, que revalida el saldo de la factura contra lo leído en ese momento.
     *
     * [alreadyCharged] es lo que esta misma compra ya le había descontado a esta gaveta, y
     * vuelve a estar disponible al corregirla: sin restarlo, subir de Q50 a Q60 se compararía
     * contra una caja a la que ya se le quitaron los Q50.
     */
    private fun assertCashAvailable(session: CashSession?, amount: Double, alreadyCharged: Double) {
        if (session == null) return
        val available = session.expectedCashNow + alreadyCharged
        if (amount > available + PURCHASE_BALANCE_EPSILON) {
            throw IllegalStateException(
                "En ${session.registerName.ifEmpty { "esta caja" }} hay Q ${available.fmt(2)} " +
                    "y estás anotando Q ${amount.fmt(2)}. Si el dinero salió de otro lado, " +
                    "anotalo desde Operaciones en vez de desde la caja."
            )
        }
    }

    /**
     * Insumo o producto que corresponde a un código de barras escaneado. Busca en el
     * catálogo del proveedor primero y en el general después: si el código no está asociado
     * a este proveedor igual sirve para encontrar el ítem, y quien captura decide.
     */
    fun findByBarcode(code: String): BarcodeMatch? {
        if (code.isBlank()) return null
        val trimmed = code.trim()
        val st = _uiState.value
        (st.supplierIngredients + st.supplierSupplies + st.allIngredients)
            .firstOrNull { it.barcode.isNotEmpty() && it.barcode == trimmed }
            ?.let { return BarcodeMatch.OfIngredient(it) }
        (st.supplierResaleProducts + st.allResaleProducts)
            .firstOrNull { it.barcode.isNotEmpty() && it.barcode == trimmed }
            ?.let { return BarcodeMatch.OfProduct(it) }
        return null
    }

    /**
     * Da de alta una presentación de compra de [ingredient] para el proveedor seleccionado y
     * lo suma a la lista del formulario, sin salir de la captura.
     *
     * La presentación SIEMPRE se guarda, sin preguntar: si le compraste esto a este proveedor,
     * eso es exactamente lo que el modelo llama una presentación suya. Guardarla de forma
     * transitoria obligaría a arrastrar un formato sin id por todo el flujo, y la próxima
     * factura volvería a pedir los mismos datos.
     */
    fun addSupplierFormat(
        companyId: String,
        ingredient: Ingredient,
        description: String,
        unit: String,
        unitsPerPackage: Double,
        packageCost: Double
    ) {
        val supplier = _uiState.value.selectedSupplier ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            runCatching {
                ingredientRepository.addPurchaseFormat(
                    companyId, ingredient.id,
                    PurchaseFormat(
                        description = description.trim(),
                        unit = unit,
                        supplierId = supplier.id,
                        supplierName = supplier.company,
                        unitsPerPackage = unitsPerPackage.coerceAtLeast(0.0001),
                        packageCost = packageCost,
                        // Solo es el predeterminado si el insumo no tenía ninguno: no le
                        // cambiamos el formato de costeo a un insumo ya configurado.
                        isDefault = ingredient.purchaseFormats.isEmpty()
                    )
                )
            }.onSuccess { saved ->
                val updated = ingredient.copy(purchaseFormats = ingredient.purchaseFormats + saved)
                _uiState.update { st ->
                    st.copy(
                        isProcessing = false,
                        supplierIngredients = (st.supplierIngredients.filterNot { it.id == updated.id } + updated)
                            .sortedBy { it.name.lowercase() },
                        allIngredients = st.allIngredients.map { if (it.id == updated.id) updated else it },
                        message = "${updated.name} agregado a ${supplier.company}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isProcessing = false, error = err.message ?: "No se pudo agregar el insumo")
                }
            }
        }
    }

    /**
     * Las filas del catálogo rearman la línea completa en cada tecla, sin saber del lote ni del
     * vencimiento (se capturan aparte, en la sección "Vencimientos"). Por eso se CONSERVAN los de
     * la línea anterior: sin esto, anotar la fecha y después corregir la cantidad la borraba en
     * silencio. Para cambiarlos está [updateLineExpiry].
     */
    fun updateDraftLine(line: PurchaseLine) {
        _uiState.update { state ->
            val previous = state.draftLines.firstOrNull { it.matchKey() == line.matchKey() }
            val merged = if (previous != null && line.expiryDate == 0L && line.lotCode.isEmpty()) {
                line.copy(expiryDate = previous.expiryDate, lotCode = previous.lotCode)
            } else line
            val others = state.draftLines.filter { it.matchKey() != line.matchKey() }
            val updated = if (merged.quantityReceived > 0.0) others + merged else others
            state.copy(draftLines = updated)
        }
    }

    /** Lote y vencimiento de una línea ya anotada. Único camino que puede dejarlos vacíos. */
    fun updateLineExpiry(line: PurchaseLine, expiryDate: Long, lotCode: String) {
        _uiState.update { state ->
            state.copy(
                draftLines = state.draftLines.map {
                    if (it.matchKey() == line.matchKey()) it.copy(expiryDate = expiryDate, lotCode = lotCode) else it
                }
            )
        }
    }

    fun savePurchase(
        companyId: String,
        currentUser: User,
        invoiceNumber: String?,
        notes: String?,
        date: Long,
        status: String = PurchaseStatus.RECEIVED_PENDING_PAYMENT,
        paymentTermsDaysOverride: Int? = null,
        dueDateOverride: Long? = null,
        taxRatePercent: Double = 0.0,
        declaredTotal: Double = 0.0,
        invoicePhotoBytes: ByteArray? = null,
        /**
         * Caja de la que salió el efectivo, o `null` si la factura queda por pagar / la paga
         * el dueño. Es el caso del proveedor que llega a la puerta y le pagan del cajón: la
         * factura es idéntica a cualquier otra, lo único distinto es de qué bolsa salió.
         */
        session: CashSession? = null,
        /** A quién le corresponde lo que la factura gasta. Ver [CostCenter]. */
        expenseCostCenter: String = CostCenter.BRANCH
    ) {
        viewModelScope.launch {
            val branch = _uiState.value.selectedBranch ?: return@launch
            val supplier = _uiState.value.selectedSupplier
            // El proveedor puede ser ocasional: nombre a mano, sin ficha en el catálogo.
            // Lo que no puede es no haber ninguno — la factura tiene que decir de quién es.
            val supplierLabel = supplier?.company
                ?: _uiState.value.occasionalSupplierName.trim().ifEmpty { return@launch }
            val lines = _uiState.value.draftLines.withExpenseCostCenter(expenseCostCenter)
            if (lines.isEmpty()) return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val purchaseId = generateId()
                // La foto se sube antes de escribir el documento porque su URL es un campo
                // del encabezado. Si falla, la compra se guarda igual: el respaldo es
                // opcional y perder la captura entera por una foto sería peor.
                val photoUrl = invoicePhotoBytes?.let { bytes ->
                    runCatching {
                        invoiceImageUploader.uploadInvoicePhoto(bytes, companyId, purchaseId)
                    }.getOrDefault("")
                }.orEmpty()
                // Se relee la sesión en vez de confiar en la que trae la pantalla: entre
                // abrirla y guardar, el cajero pudo haber cerrado su turno.
                val liveSession = session?.let { chosen ->
                    runCatching { cashSessionRepository.getSession(companyId, chosen.id) }
                        .getOrNull()
                        ?.takeIf { it.status == CashSessionStatus.OPEN }
                }
                val terms = (paymentTermsDaysOverride ?: supplier?.paymentTermsDays ?: 0).coerceAtLeast(0)
                val total = lines.purchaseTotal()
                assertCashAvailable(liveSession, total, alreadyCharged = 0.0)
                val tax = taxBreakdownFromGross(total, taxRatePercent).tax
                val purchase = Purchase(
                    id = purchaseId,
                    branchId = branch.id,
                    branchName = branch.name,
                    supplierId = supplier?.id.orEmpty(),
                    supplierName = supplierLabel,
                    date = date,
                    notes = notes?.trim()?.ifEmpty { null },
                    invoiceNumber = invoiceNumber?.trim()?.ifEmpty { null },
                    receivedById = currentUser.id,
                    receivedByName = currentUser.displayName.ifEmpty { currentUser.email },
                    status = status,
                    totalAmount = total,
                    // Registrar una compra ya "Pagada" la deja saldada: si no, el saldo quedaría
                    // igual al total y la factura aparecería en "por pagar" pese al estado.
                    paidAmount = if (status == PurchaseStatus.PAID) total else 0.0,
                    taxAmount = tax,
                    taxRatePercent = taxRatePercent,
                    declaredTotal = declaredTotal,
                    nearestExpiryDate = lines.nearestExpiry(),
                    nearestExpiryItemName = lines.nearestExpiryLine()?.itemName.orEmpty(),
                    paymentTermsDays = terms,
                    // Contado (0 días) no genera vencimiento que vigilar.
                    dueDate = dueDateOverride ?: if (terms > 0) date + terms * 86_400_000L else 0L,
                    receivedAt = if (status == PurchaseStatus.PENDING_RECEIPT) 0L else date,
                    invoicePhotoUrl = photoUrl,
                    paidFrom = if (liveSession != null) ExpensePaidFrom.CASH_DRAWER
                               else ExpensePaidFrom.GENERAL,
                    cashSessionId = liveSession?.id.orEmpty(),
                    cashRegisterName = liveSession?.registerName.orEmpty()
                )
                purchaseRepository.savePurchase(companyId, purchase, lines)

                // El precio del formato se actualiza al REGISTRAR, no al recibir: es el precio
                // que el proveedor cotizó y que el usuario acaba de teclear.
                lines.forEach { line ->
                    if (line.lineKind == PurchaseLineKind.RESALE_PRODUCT) return@forEach
                    // Una línea con producto detrás trae el formato del PRODUCTO, no el del
                    // insumo: su precio lo actualiza `syncResaleFormatPrices`.
                    if (line.productId.isNotEmpty()) return@forEach
                    if (line.formatId.isEmpty() || line.packageCost <= 0) return@forEach
                    val ingredient = _uiState.value.supplierIngredients.find { it.id == line.ingredientId }
                    val originalFormat = ingredient?.purchaseFormats?.find { it.id == line.formatId }
                    if (originalFormat != null && originalFormat.packageCost != line.packageCost) {
                        runCatching {
                            ingredientRepository.updatePurchaseFormatCost(
                                companyId, line.ingredientId, line.formatId, line.packageCost
                            )
                        }
                    }
                }

                // Mismo criterio para la reventa: la caja de gaseosas que hoy costó Q126 es la
                // que la próxima factura debe proponer.
                syncResaleFormatPrices(companyId, lines)
                // Y lo mismo para los conceptos sin inventario: el proveedor al que se le
                // compró y el precio que cobró hoy.
                syncPurchaseConcepts(companyId, supplier?.id.orEmpty(), lines, date)

                // Una compra "pendiente de recibir" NO toca el inventario: el pan todavía está
                // en el camión del proveedor. El stock entra con `receivePurchase`.
                if (status != PurchaseStatus.PENDING_RECEIPT) {
                    applyStockForPurchase(
                        companyId, purchase, lines, currentUser, perishableIngredientIds(companyId)
                    )
                    // Suministros y servicios dejan además su gasto en el mes, y el
                    // mantenimiento su evento en el historial del activo.
                    syncPurchaseSideEffects(companyId, purchase, lines, emptyList(), currentUser)
                }
                // El efectivo sale de la gaveta aunque lo comprado sea inventario y no gasto:
                // lo que se cuadra al cierre es el dinero, no el destino contable.
                syncPurchaseCashMovement(
                    companyId = companyId,
                    session = liveSession,
                    previousTotal = 0.0,
                    newTotal = total,
                    concept = supplierLabel,
                    currentUser = currentUser,
                    purchaseId = purchaseId
                )
                session != null && liveSession == null
            }.onSuccess { drawerClosedMidway ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSaved = true,
                        draftLines = emptyList(),
                        pinnedSession = null,
                        message = if (drawerClosedMidway) {
                            "La caja se cerró mientras capturabas. La compra quedó registrada, " +
                                "pero su efectivo no se descontó de ninguna gaveta."
                        } else null
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message ?: "Error al guardar la compra") }
            }
        }
    }

    /** Escribe el precio pagado en las presentaciones de reventa que cambiaron. Nunca lanza. */
    private suspend fun syncResaleFormatPrices(companyId: String, lines: List<PurchaseLine>) {
        // Por producto y no por tipo de línea: la compra de un producto respaldado por insumo
        // se guarda como línea de insumo y su presentación se actualiza igual.
        val productIds = lines.filter { it.productId.isNotEmpty() && it.formatId.isNotEmpty() }
            .mapTo(HashSet()) { it.productId }
        if (productIds.isEmpty()) return
        val catalog = _uiState.value.let { it.supplierResaleProducts + it.allResaleProducts }
        val suggestions = mutableListOf<RepriceSuggestion>()
        productIds.forEach { id ->
            val product = catalog.firstOrNull { it.id == id } ?: return@forEach
            val formats = product.formatsWithPaidPrices(lines) ?: return@forEach
            val updated = product.copy(purchaseFormats = formats).withDerivedPurchaseReference()
            runCatching { productRepository.updatePurchaseFormats(companyId, updated) }
                .onSuccess {
                    // Se compara `purchaseCost` —el costo derivado de la presentación
                    // PREDETERMINADA— y no el precio de cualquier formato: si subió el de un
                    // proveedor al que casi no le comprás, tu costo de venta no cambió y
                    // proponer un aumento sería mentir sobre el margen.
                    repriceSuggestionFor(
                        product = updated,
                        oldCost = product.purchaseCost ?: 0.0,
                        newCost = updated.purchaseCost ?: 0.0
                    )?.let(suggestions::add)
                }
        }
        if (suggestions.isNotEmpty()) {
            _uiState.update { it.copy(repriceSuggestions = suggestions.sortedBy { s -> s.productName }) }
        }
    }

    /**
     * Aplica los precios aceptados en el diálogo posterior a la factura.
     *
     * Escribe `salePrice` **y** el `ProductPrice` de la lista RETAIL predeterminada, que es
     * exactamente lo que hace `ProductViewModel.saveProduct`: `salePrice` es la fuente de
     * verdad de esa lista y las dos puntas tienen que quedar de acuerdo, o el POS cobra una y
     * el resto del sistema muestra la otra.
     *
     * Usa `updateProduct` (escritura completa) sobre el producto del catálogo en memoria, que
     * es el que se acaba de actualizar con el costo nuevo — releerlo sería una consulta de más
     * para volver a tener lo mismo.
     */
    fun applyRepricing(companyId: String, pricesByProductId: Map<String, Double>) {
        val accepted = pricesByProductId.filterValues { it > 0.0 }
        if (accepted.isEmpty()) { dismissRepricing(); return }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val retail = runCatching {
                priceListRepository.getDefaultList(companyId, PriceListType.RETAIL)
            }.getOrNull()
            val catalog = _uiState.value.let { it.supplierResaleProducts + it.allResaleProducts }
            var applied = 0
            accepted.forEach { (productId, price) ->
                val product = catalog.firstOrNull { it.id == productId } ?: return@forEach
                runCatching {
                    productRepository.updateProduct(companyId, product.copy(salePrice = price))
                    if (retail != null) {
                        priceListRepository.upsertPrice(
                            companyId,
                            ProductPrice(
                                productId = productId,
                                priceListId = retail.id,
                                priceListName = retail.name,
                                unitPrice = price
                            )
                        )
                    }
                }.onSuccess { applied++ }
            }
            _uiState.update { st ->
                st.copy(
                    isProcessing = false,
                    repriceSuggestions = emptyList(),
                    // El catálogo en memoria tiene que reflejarlo: si no, reabrir la misma
                    // factura volvería a proponer el aumento que ya se aplicó.
                    supplierResaleProducts = st.supplierResaleProducts.map { p ->
                        accepted[p.id]?.let { p.copy(salePrice = it) } ?: p
                    },
                    allResaleProducts = st.allResaleProducts.map { p ->
                        accepted[p.id]?.let { p.copy(salePrice = it) } ?: p
                    },
                    message = if (applied == 1) "Precio actualizado"
                              else "Precios actualizados ($applied)"
                )
            }
        }
    }

    /** Cierra el diálogo sin tocar precios. El costo nuevo ya quedó guardado igual. */
    fun dismissRepricing() {
        _uiState.update { it.copy(repriceSuggestions = emptyList()) }
    }

    /**
     * Ingresa las líneas al inventario y escribe el Kardex. Es el único lugar que lo hace,
     * y lo llaman los dos caminos: registrar una compra ya recibida, y recibir después una
     * que se había dejado pendiente. Duplicarlo sería duplicar el riesgo de doble ingreso.
     */
    /**
     * Reconcilia los lotes de la compra **una vez por sucursal**.
     *
     * `reconcilePurchaseLots` recibe UNA sucursal porque el libro de lotes está llaveado por
     * `{branchId}_{ingredientId}`. En una compra que lleva fruta a dos sucursales, llamarlo
     * con la del encabezado dejaría los lotes de la otra sin registrar y el aviso de
     * caducidad apuntando a fruta que está en otro lado. Se le pasa a cada sucursal SOLO sus
     * líneas: una lista vacía para una sucursal que ya no recibe nada es justamente lo que
     * agota sus lotes.
     */
    private suspend fun reconcileLotsPerBranch(
        companyId: String,
        purchase: Purchase,
        lines: List<PurchaseLine>,
        perishableIds: Set<String>,
        extraBranches: List<Pair<String, String>> = emptyList()
    ) {
        val perishable = lines.filter {
            // Sin conteo no hay libro de lotes que llevar: el lote existe para consumirse
            // FEFO contra un stock que acá no se mueve.
            purchaseLineTouchesStock(it.lineKind) &&
                it.ingredientId.isNotEmpty() &&
                (it.ingredientId in perishableIds || it.expiryDate > 0L)
        }
        val branches = (lines.branchesTouched(purchase) + extraBranches).distinct()
        branches.forEach { (branchId, branchName) ->
            if (branchId.isEmpty()) return@forEach
            ingredientRepository.reconcilePurchaseLots(
                companyId, purchase.id, branchId, branchName,
                perishable.filter { it.effectiveBranchId(purchase) == branchId }.map {
                    PurchaseLotEntry(
                        ingredientId = it.ingredientId,
                        ingredientName = it.ingredientName,
                        quantity = it.quantityReceived,
                        expiryDate = it.expiryDate,
                        lotCode = it.lotCode,
                        unitCost = it.unitCost,
                        receivedAt = purchase.date
                    )
                }
            )
        }
    }

    /**
     * Deja los gastos y los eventos de activo de la factura iguales a sus líneas actuales.
     *
     * Es el ÚNICO lugar que los escribe, y lo llaman los tres caminos: registrar, editar y
     * borrar (borrar pasa una lista vacía, que es exactamente "quitá todo lo que esta
     * factura dejó"). Reusar la misma reconciliación en vez de escribir un reverso aparte
     * es lo que hace que el borrado herede las mismas guardas ya probadas — mismo criterio
     * que `applyStockDelta` con el inventario.
     *
     * Se reconstruye entero en vez de aplicar diferencias porque `updatePurchase` reescribe
     * la subcolección con ids nuevos: no hay línea vieja con la que emparejar.
     *
     * Un fallo acá no tumba la compra. El inventario ya se movió y es el dato caro; un
     * gasto que no se escribió se corrige reabriendo la factura, un ingreso duplicado no.
     */
    private suspend fun syncPurchaseSideEffects(
        companyId: String,
        purchase: Purchase,
        lines: List<PurchaseLine>,
        previousLines: List<PurchaseLine>,
        currentUser: User
    ) {
        runCatching {
            expenseRepository.replaceExpensesForPurchase(
                companyId, purchase.id, expensesForPurchase(companyId, purchase, lines)
            )
        }

        // Historial de activos: se limpia lo que esta factura había dejado antes y se
        // vuelve a escribir. Los activos a limpiar son los de ANTES más los de ahora — si
        // se le cambió el horno por la batidora, el evento del horno tiene que irse.
        val previousAssetIds = previousLines.filter { it.assetId.isNotEmpty() }.map { it.assetId }
        val entries = assetEntriesForPurchase(purchase, lines, currentUser)
        val touchedAssetIds = (previousAssetIds + entries.map { it.assetId }).distinct()
        touchedAssetIds.forEach { assetId ->
            runCatching {
                assetHistoryRepository.getHistory(companyId, assetId)
                    .filter { it.sourcePurchaseId == purchase.id }
                    .forEach { assetHistoryRepository.deleteEntry(companyId, assetId, it.id) }
            }
        }
        entries.forEach { (assetId, entry) ->
            runCatching {
                assetHistoryRepository.addEntry(companyId, assetId, entry)
                applyAssetEntrySideEffects(assetRepository, companyId, assetId, entry)
            }
        }
        // Los contadores se recalculan al final y una sola vez por activo: dos líneas de la
        // misma factura pueden tocar el mismo horno.
        touchedAssetIds.forEach { assetId ->
            runCatching {
                recalcAndPersistAssetCounters(assetRepository, assetHistoryRepository, companyId, assetId)
            }
        }
    }

    /**
     * Insumos que llevan libro de lotes. Solo los perecederos: al resto (harina, azúcar, sal)
     * les rota el stock en días y llevarles lotes sería papeleo sin lector.
     */
    private suspend fun perishableIngredientIds(companyId: String): Set<String> {
        val catalog = _uiState.value.allIngredients.ifEmpty {
            runCatching { ingredientRepository.getIngredients(companyId) }.getOrElse { emptyList() }
        }
        return catalog.filter { it.isPerishable }.mapTo(HashSet()) { it.id }
    }

    private suspend fun applyStockForPurchase(
        companyId: String,
        purchase: Purchase,
        lines: List<PurchaseLine>,
        currentUser: User,
        perishableIds: Set<String>
    ) {
        lines.forEach { line ->
            // ── Lo que no mueve inventario se salta acá: el servicio (sin catálogo) y el
            //    suministro sin conteo (la bolsa, que SÍ tiene catálogo y por eso llega con
            //    `ingredientId` lleno — sin esta guarda le escribiría stock igual). Su
            //    efecto es el gasto, que sincroniza `syncPurchaseSideEffects` aparte.
            if (!purchaseLineTouchesStock(line.lineKind)) return@forEach
            // La sucursal es la de LA LÍNEA, no la del encabezado: una compra rápida
            // surte a varias de una sola salida. En una factura las dos coinciden.
            val branchId = line.effectiveBranchId(purchase)
            val branchName = line.effectiveBranchName(purchase)
            if (branchId.isEmpty()) return@forEach
            // ── Reventa: ingresa a inventario terminado (FinishedProductStock) ──
            if (line.lineKind == PurchaseLineKind.RESALE_PRODUCT) {
                finishedStockRepository.applyMovement(
                    companyId,
                    FinishedStockMovement(
                        productId = line.productId,
                        productName = line.productName,
                        branchId = branchId,
                        branchName = branchName,
                        type = FinishedMovementType.RECEPCION,
                        quantity = line.quantityReceived,
                        unitPriceAtMovement = line.unitCost,
                        referenceType = FinishedMovementReference.PURCHASE,
                        referenceId = purchase.id,
                        reason = "Compra a ${purchase.supplierName}",
                        createdByUserId = currentUser.id,
                        createdByUserName = currentUser.displayName.ifEmpty { currentUser.email }
                    )
                )
                return@forEach
            }
            // ── Insumo o suministro: actualizan BranchStock + Kardex igual. Un suministro
            //    se almacena exactamente como una harina; lo único que difiere es que
            //    además se gasta, y de eso se encarga la sincronización de gastos.
            val stocks = ingredientRepository.getBranchStocksForIngredient(
                companyId, line.ingredientId, listOf(branchId)
            )
            val prevStock = stocks.find { it.branchId == branchId }?.currentStock ?: 0.0
            // Un insumo perecedero entra como lote propio aunque no se le haya anotado la
            // fecha: si el libro queda incompleto, el consumo FEFO agota de más los lotes que
            // sí la tienen y el aviso de caducidad empieza a mentir.
            val tracksLots = line.ingredientId in perishableIds || line.expiryDate > 0L
            ingredientRepository.updateBranchStock(
                companyId, line.ingredientId, line.ingredientName,
                branchId, branchName, line.quantityReceived,
                newLot = if (tracksLots) NewStockLot(
                    expiryDate = line.expiryDate,
                    lotCode = line.lotCode,
                    unitCost = line.unitCost,
                    purchaseId = purchase.id,
                    receivedAt = purchase.date
                ) else null
            )
            ingredientRepository.addStockMovement(
                companyId, line.ingredientId, StockMovement(
                    type = "COMPRA",
                    quantity = line.quantityReceived,
                    balanceBefore = prevStock,
                    balanceAfter = prevStock + line.quantityReceived,
                    branchId = branchId,
                    branchName = branchName,
                    reference = purchase.id,
                    date = purchase.date,
                        createdByUserId = currentUser.id,
                        createdByUserName = currentUser.displayName.ifBlank { currentUser.email },
                        createdByEmployeeId = currentUser.operatorEmployeeId
                )
            )
        }
    }

    /**
     * Recibe una compra que estaba en `PENDING_RECEIPT`: ahora sí entra al inventario.
     *
     * La guarda por `status` no es cosmética — es lo único que impide que dos toques al
     * botón, o dos encargados en dos tablets, ingresen el mismo saco de harina dos veces.
     */
    fun receivePurchase(companyId: String, purchaseId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            runCatching {
                val purchase = purchaseRepository.getPurchase(companyId, purchaseId)
                    ?: error("No se encontró la compra")
                if (purchase.status != PurchaseStatus.PENDING_RECEIPT) {
                    error("Esta compra ya fue recibida")
                }
                val lines = purchaseRepository.getLines(companyId, purchaseId)
                val receivedAt = Clock.System.now().toEpochMilliseconds()
                // Se marca ANTES de mover stock: si algo falla a media aplicación, un reintento
                // encuentra la compra ya recibida y no vuelve a sumar lo que sí entró.
                purchaseRepository.markReceived(companyId, purchaseId, receivedAt)
                applyStockForPurchase(
                    companyId, purchase, lines, currentUser, perishableIngredientIds(companyId)
                )
                syncPurchaseSideEffects(companyId, purchase, lines, emptyList(), currentUser)
                purchase.copy(status = PurchaseStatus.RECEIVED_PENDING_PAYMENT, receivedAt = receivedAt)
            }.onSuccess { updated ->
                replacePurchaseInState(updated)
                _uiState.update { it.copy(isProcessing = false, message = "Compra recibida — el stock ya entró") }
            }.onFailure { err ->
                _uiState.update { it.copy(isProcessing = false, error = err.message ?: "No se pudo recibir la compra") }
            }
        }
    }

    // ── Cuentas por pagar ────────────────────────────────────────────────────

    fun addPayment(
        companyId: String,
        purchaseId: String,
        amount: Double,
        date: Long,
        method: String,
        reference: String,
        notes: String?,
        currentUser: User
    ) {
        viewModelScope.launch {
            val purchase = _uiState.value.selectedPurchase?.takeIf { it.id == purchaseId }
            if (amount <= 0.0) {
                _uiState.update { it.copy(error = "El monto del abono debe ser mayor a cero") }
                return@launch
            }
            // Primera barrera; la definitiva está en el repositorio, que revalida contra el
            // saldo recién leído por si alguien abonó desde otro dispositivo mientras tanto.
            if (purchase != null && amount > purchase.balanceDue + PURCHASE_BALANCE_EPSILON) {
                _uiState.update {
                    it.copy(error = "El abono no puede pasar del saldo: Q${purchase.balanceDue.fmtMoney()}")
                }
                return@launch
            }
            _uiState.update { it.copy(isProcessing = true, error = null) }
            runCatching {
                purchaseRepository.addPayment(
                    companyId, purchaseId,
                    PurchasePayment(
                        amount = amount,
                        date = date,
                        method = method,
                        reference = reference.trim(),
                        notes = notes?.trim()?.ifEmpty { null },
                        createdById = currentUser.id,
                        createdByName = currentUser.displayName.ifEmpty { currentUser.email }
                    )
                )
                purchaseRepository.getPayments(companyId, purchaseId)
            }.onSuccess { payments ->
                refreshHeaderAfterPayments(companyId, purchaseId, payments)
                _uiState.update { it.copy(isProcessing = false, message = "Abono registrado") }
            }.onFailure { err ->
                _uiState.update { it.copy(isProcessing = false, error = err.message ?: "No se pudo registrar el abono") }
            }
        }
    }

    /**
     * Salda la factura completa en un toque.
     *
     * Registra un abono por el saldo entero en vez de solo cambiar el estado a PAGADA: el
     * `paidAmount` del encabezado es una caché de la suma de los abonos y `recalculateBalance`
     * la recomputa desde la subcolección, así que un estado sin abono detrás volvería a
     * "pendiente de pago" en cuanto algo tocara la factura. Además deja el rastro de cuándo y
     * cómo se pagó, que es la mitad de para qué sirve el módulo.
     */
    fun settleInFull(
        companyId: String,
        purchaseId: String,
        date: Long,
        method: String,
        reference: String,
        currentUser: User
    ) {
        val purchase = _uiState.value.selectedPurchase?.takeIf { it.id == purchaseId }
        val balance = purchase?.balanceDue ?: 0.0
        if (balance <= PURCHASE_BALANCE_EPSILON) {
            _uiState.update { it.copy(message = "La factura ya está saldada") }
            return
        }
        addPayment(
            companyId = companyId,
            purchaseId = purchaseId,
            amount = balance,
            date = date,
            method = method,
            reference = reference,
            notes = null,
            currentUser = currentUser
        )
    }

    fun deletePayment(companyId: String, purchaseId: String, paymentId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            runCatching {
                purchaseRepository.deletePayment(companyId, purchaseId, paymentId)
                purchaseRepository.getPayments(companyId, purchaseId)
            }.onSuccess { payments ->
                refreshHeaderAfterPayments(companyId, purchaseId, payments)
                _uiState.update { it.copy(isProcessing = false, message = "Abono eliminado") }
            }.onFailure { err ->
                _uiState.update { it.copy(isProcessing = false, error = err.message ?: "No se pudo eliminar el abono") }
            }
        }
    }

    private suspend fun refreshHeaderAfterPayments(
        companyId: String,
        purchaseId: String,
        payments: List<PurchasePayment>
    ) {
        val header = runCatching { purchaseRepository.getPurchase(companyId, purchaseId) }.getOrNull()
        _uiState.update { state ->
            val merged = header?.withFallbackTotal(state.selectedPurchaseLines)
            state.copy(
                selectedPurchasePayments = payments,
                selectedPurchase = merged ?: state.selectedPurchase,
                purchases = if (merged != null) state.purchases.map { if (it.id == purchaseId) merged else it }
                            else state.purchases
            )
        }
    }

    fun updateDueDate(companyId: String, purchaseId: String, dueDate: Long) {
        viewModelScope.launch {
            runCatching { purchaseRepository.updateDueDate(companyId, purchaseId, dueDate) }
                .onSuccess {
                    val current = _uiState.value.purchases.find { it.id == purchaseId }
                        ?: _uiState.value.selectedPurchase
                    current?.let { replacePurchaseInState(it.copy(dueDate = dueDate)) }
                }
                .onFailure { err -> _uiState.update { s -> s.copy(error = err.message) } }
        }
    }

    private fun replacePurchaseInState(updated: Purchase) {
        _uiState.update { state ->
            state.copy(
                purchases = state.purchases.map { if (it.id == updated.id) updated else it },
                selectedPurchase = if (state.selectedPurchase?.id == updated.id)
                    updated.withFallbackTotal(state.selectedPurchaseLines) else state.selectedPurchase
            )
        }
    }

    private fun generateId(): String {
        val hex = "0123456789abcdef"
        return Random.nextBytes(16).joinToString("") { b ->
            hex[(b.toInt() shr 4) and 0xF].toString() + hex[b.toInt() and 0xF]
        }
    }

    fun resetForm() {
        _uiState.update {
            it.copy(
                selectedBranch = if (it.accessibleBranches.size == 1) it.accessibleBranches.first() else null,
                selectedSupplier = null,
                supplierIngredients = emptyList(),
                draftLines = emptyList(),
                isSaved = false,
                error = null
            )
        }
    }

    fun updatePurchaseStatus(companyId: String, purchaseId: String, newStatus: String) {
        viewModelScope.launch {
            runCatching { purchaseRepository.updatePurchaseStatus(companyId, purchaseId, newStatus) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            selectedPurchase = state.selectedPurchase?.takeIf { it.id == purchaseId }
                                ?.copy(status = newStatus) ?: state.selectedPurchase,
                            purchases = state.purchases.map { if (it.id == purchaseId) it.copy(status = newStatus) else it }
                        )
                    }
                }
                .onFailure { err -> _uiState.update { s -> s.copy(error = err.message ?: "Error al actualizar el estado") } }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    fun clearMessage() { _uiState.update { it.copy(message = null) } }
}
