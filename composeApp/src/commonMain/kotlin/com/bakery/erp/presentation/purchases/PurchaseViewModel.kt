package com.bakery.erp.presentation.purchases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.AssetHistoryRepository
import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.ExpenseRepository
import com.bakery.erp.domain.FixedAssetRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.NewStockLot
import com.bakery.erp.domain.PurchaseLotEntry
import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.domain.PurchaseInvoiceImageUploader
import com.bakery.erp.domain.PurchaseRepository
import com.bakery.erp.domain.SupplierRepository
import com.bakery.erp.model.AssetHistoryEntry
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.FinishedMovementReference
import com.bakery.erp.model.FinishedMovementType
import com.bakery.erp.model.FinishedStockMovement
import com.bakery.erp.model.FixedAsset
import com.bakery.erp.model.IngredientUsage
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Product
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PURCHASE_BALANCE_EPSILON
import com.bakery.erp.model.PurchaseDisplayStatus
import com.bakery.erp.model.PurchaseFilters
import com.bakery.erp.model.PurchaseFormat
import com.bakery.erp.model.effectiveBranchId
import com.bakery.erp.model.effectiveBranchName
import com.bakery.erp.model.PurchaseKind
import com.bakery.erp.model.branchesTouched
import com.bakery.erp.model.PurchaseLine
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
import com.bakery.erp.model.itemName
import com.bakery.erp.model.nearestExpiry
import com.bakery.erp.model.nearestExpiryLine
import com.bakery.erp.model.purchaseTotal
import com.bakery.erp.model.taxBreakdownFromGross
import com.bakery.erp.util.fmtMoney
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
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
    /** Catálogo completo, solo para el picker de "agregar insumo fuera del proveedor". */
    val allIngredients: List<Ingredient> = emptyList(),
    val draftLines: List<PurchaseLine> = emptyList(),
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
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseUiState())
    val uiState: StateFlow<PurchaseUiState> = _uiState.asStateFlow()

    fun loadPurchases(companyId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { purchaseRepository.getPurchases(companyId) }
                .onSuccess { all ->
                    val filtered = all.filter { currentUser.canAccessBranch(it.branchId) }
                    _uiState.update { it.copy(purchases = filtered, isLoading = false) }
                }
                .onFailure { _uiState.update { s -> s.copy(isLoading = false, error = it.message) } }
        }
        // Los proveedores alimentan el filtro por proveedor de la lista, no solo el formulario.
        viewModelScope.launch {
            val suppliers = runCatching { supplierRepository.getSuppliers(companyId) }.getOrElse { emptyList() }
            _uiState.update { if (it.suppliers.isEmpty()) it.copy(suppliers = suppliers) else it }
        }
    }

    fun loadPurchaseDetail(companyId: String, purchaseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true) }
            val purchase = runCatching { purchaseRepository.getPurchase(companyId, purchaseId) }.getOrNull()
            val lines = if (purchase != null)
                runCatching { purchaseRepository.getLines(companyId, purchaseId) }.getOrElse { emptyList() }
            else emptyList()
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
            loadPurchaseFlows(companyId)
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
        val org = authRepository.getOrganization(companyId).getOrNull() ?: return
        _uiState.update {
            it.copy(
                usesResale = org.usesResalePurchases,
                usesSupplies = org.usesSupplyPurchases,
                usesServices = org.usesServicePurchases
            )
        }
        if (org.usesServicePurchases && _uiState.value.assets.isEmpty()) {
            val assets = runCatching { assetRepository.getAssets(companyId) }.getOrElse { emptyList() }
            _uiState.update { st -> st.copy(assets = assets.filter { it.active }) }
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
                (p.active && !p.isInHouseProduced && p.supplierId == purchase.supplierId) || p.id in lineProductIds
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
        invoicePhotoBytes: ByteArray? = null
    ) {
        viewModelScope.launch {
            val original = _uiState.value.editingPurchase ?: return@launch
            val branch = _uiState.value.selectedBranch ?: return@launch
            val supplier = _uiState.value.selectedSupplier
            // Igual que al registrar: el proveedor puede ser ocasional. Al reabrir una compra
            // que ya lo era, el nombre guardado es el que sigue valiendo si no se tocó nada.
            val supplierLabel = supplier?.company
                ?: _uiState.value.occasionalSupplierName.trim().ifEmpty { original.supplierName }
            val newLines = _uiState.value.draftLines
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
        // La clave incluye la SUCURSAL, no solo el insumo: en una vuelta de compras la misma
        // papa puede ir a dos sucursales, y agrupando solo por insumo el ajuste caería
        // entero sobre una de las dos. En una factura la sucursal es una sola y esto se
        // comporta igual que antes.
        fun index(lines: List<PurchaseLine>) = lines
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
                        (purchase.invoiceNumber?.let { "Nº $it" } ?: "a ${purchase.supplierName}")
                )
            )
        }

        // Reventa: mismo criterio, contra el inventario de producto terminado.
        fun indexResale(lines: List<PurchaseLine>) = lines
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
                purchaseRepository.deletePurchase(companyId, purchaseId)
            }.onSuccess {
                _uiState.update { st ->
                    st.copy(
                        isProcessing = false,
                        isDeleted = true,
                        purchases = st.purchases.filterNot { it.id == purchaseId },
                        selectedPurchase = st.selectedPurchase?.takeIf { it.id != purchaseId },
                        selectedPurchaseLines = emptyList(),
                        selectedPurchasePayments = emptyList(),
                        message = "Compra eliminada"
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

    private fun loadResaleProductsBySupplier(companyId: String, supplierId: String) {
        viewModelScope.launch {
            val all = runCatching { productRepository.getProducts(companyId) }.getOrElse { emptyList() }
            val filtered = all.filter { p ->
                p.active && !p.isInHouseProduced && p.supplierId == supplierId
            }.sortedBy { it.name.lowercase() }
            _uiState.update { it.copy(supplierResaleProducts = filtered) }
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
    private fun PurchaseLine.matchKey(): String = when (lineKind) {
        PurchaseLineKind.RESALE_PRODUCT -> "P:$productId"
        PurchaseLineKind.SERVICE -> "S:$id"
        else -> "I:$ingredientId"
    }

    /**
     * Agrega o reemplaza una línea de servicio del borrador. Se le genera id si no lo trae
     * —es la clave con la que se distingue de los demás conceptos de la misma factura.
     */
    fun upsertServiceLine(line: PurchaseLine) {
        val withId = if (line.id.isEmpty()) line.copy(id = generateId()) else line
        _uiState.update { state ->
            val others = state.draftLines.filter { it.matchKey() != withId.matchKey() }
            state.copy(draftLines = others + withId)
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

    // ── Vuelta de compras ────────────────────────────────────────────────────

    /**
     * Guarda una vuelta al mercado: un solo desembolso, muchas cosas, varias sucursales.
     *
     * Reutiliza `savePurchase` por dentro en vez de tener su propio camino de escritura: los
     * efectos son los mismos (inventario por línea, gasto del mes, Kardex) y duplicarlos
     * sería duplicar el riesgo de doble ingreso, que es lo que este módulo lleva evitando.
     * Lo único propio de la vuelta es cómo se arman las líneas y que nace pagada — es
     * efectivo, no genera cuenta por pagar.
     */
    internal fun saveMarketRun(
        companyId: String,
        currentUser: User,
        defaultBranch: Branch,
        lines: List<MarketRunDraftLine>,
        date: Long,
        notes: String?,
        expenseCategory: String,
        photoBytes: ByteArray? = null
    ) {
        if (lines.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val purchaseId = generateId()
                val photoUrl = photoBytes?.let { bytes ->
                    runCatching {
                        invoiceImageUploader.uploadInvoicePhoto(bytes, companyId, purchaseId)
                    }.getOrDefault("")
                }.orEmpty()
                val purchaseLines = lines.map { it.toPurchaseLine(expenseCategory) }
                val total = purchaseLines.purchaseTotal()
                val purchase = Purchase(
                    id = purchaseId,
                    kind = PurchaseKind.MARKET_RUN,
                    // Default de las líneas que no declararon sucursal propia.
                    branchId = defaultBranch.id,
                    branchName = defaultBranch.name,
                    // Sin proveedor: el lugar es de cada línea. El nombre visible en la
                    // lista lo arma la pantalla a partir de los lugares que trae.
                    supplierId = "",
                    supplierName = "",
                    date = date,
                    notes = notes?.trim()?.ifEmpty { null },
                    receivedById = currentUser.id,
                    receivedByName = currentUser.displayName.ifEmpty { currentUser.email },
                    // Nace saldada: es efectivo del momento, no hay cuenta por pagar que
                    // vigilar ni vencimiento que calcular.
                    status = PurchaseStatus.PAID,
                    totalAmount = total,
                    paidAmount = total,
                    paymentTermsDays = 0,
                    dueDate = 0L,
                    receivedAt = date,
                    nearestExpiryDate = purchaseLines.nearestExpiry(),
                    nearestExpiryItemName = purchaseLines.nearestExpiryLine()?.itemName.orEmpty(),
                    invoicePhotoUrl = photoUrl
                )
                purchaseRepository.savePurchase(companyId, purchase, purchaseLines)
                applyStockForPurchase(
                    companyId, purchase, purchaseLines, currentUser,
                    perishableIngredientIds(companyId)
                )
                syncPurchaseSideEffects(companyId, purchase, purchaseLines, emptyList(), currentUser)
            }.onSuccess {
                _uiState.update {
                    it.copy(isLoading = false, isSaved = true, message = "Vuelta registrada")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, error = err.message ?: "No se pudo guardar la vuelta")
                }
            }
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
        st.supplierResaleProducts
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

    fun updateDraftLine(line: PurchaseLine) {
        _uiState.update { state ->
            val others = state.draftLines.filter { it.matchKey() != line.matchKey() }
            val updated = if (line.quantityReceived > 0.0) others + line else others
            state.copy(draftLines = updated)
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
        invoicePhotoBytes: ByteArray? = null
    ) {
        viewModelScope.launch {
            val branch = _uiState.value.selectedBranch ?: return@launch
            val supplier = _uiState.value.selectedSupplier
            // El proveedor puede ser ocasional: nombre a mano, sin ficha en el catálogo.
            // Lo que no puede es no haber ninguno — la factura tiene que decir de quién es.
            val supplierLabel = supplier?.company
                ?: _uiState.value.occasionalSupplierName.trim().ifEmpty { return@launch }
            val lines = _uiState.value.draftLines
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
                val terms = (paymentTermsDaysOverride ?: supplier?.paymentTermsDays ?: 0).coerceAtLeast(0)
                val total = lines.purchaseTotal()
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
                    invoicePhotoUrl = photoUrl
                )
                purchaseRepository.savePurchase(companyId, purchase, lines)

                // El precio del formato se actualiza al REGISTRAR, no al recibir: es el precio
                // que el proveedor cotizó y que el usuario acaba de teclear.
                lines.forEach { line ->
                    if (line.lineKind == PurchaseLineKind.RESALE_PRODUCT) return@forEach
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
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSaved = true, draftLines = emptyList()) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message ?: "Error al guardar la compra") }
            }
        }
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
     * `{branchId}_{ingredientId}`. En una vuelta que lleva fruta a dos sucursales, llamarlo
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
            // ── Servicio: no mueve inventario. Su efecto es el gasto, que sincroniza
            //    `syncPurchaseSideEffects` aparte. Sin esta guarda caería al camino de
            //    insumo con `ingredientId` vacío y escribiría stock de la nada.
            if (line.lineKind == PurchaseLineKind.SERVICE) return@forEach
            // La sucursal es la de LA LÍNEA, no la del encabezado: una vuelta al mercado
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
                    date = purchase.date
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
