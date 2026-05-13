package com.bakery.erp.presentation.production

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.domain.RecipeRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.ProductionOrder
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.ProductionOrderStatus
import com.bakery.erp.model.ProductionShift
import com.bakery.erp.model.Recipe
import com.bakery.erp.model.StockMovement
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.convertUnits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductionUiState(
    // ── Lista de pedidos ──────────────────────────────────────────────────────
    val orders: List<ProductionOrder> = emptyList(),
    val orderSearchQuery: String = "",
    val orderDateFilter: Long? = null,

    // ── Lista de lotes ────────────────────────────────────────────────────────
    val lots: List<ProductionLot> = emptyList(),
    val lotDateFilter: Long? = null,

    // ── Detalle ───────────────────────────────────────────────────────────────
    val selectedOrder: ProductionOrder? = null,
    val selectedOrderLines: List<ProductionOrderLine> = emptyList(),
    val selectedLot: ProductionLot? = null,
    val selectedLotLines: List<ProductionLotLine> = emptyList(),
    val isDetailLoading: Boolean = false,

    // ── Formulario de pedido ──────────────────────────────────────────────────
    val producingBranches: List<Branch> = emptyList(),
    val sellingBranches: List<Branch> = emptyList(),
    val draftOrderLines: List<ProductionOrderLine> = emptyList(),

    // ── Formulario de lote ────────────────────────────────────────────────────
    val shifts: List<ProductionShift> = emptyList(),
    val pendingOrdersForDate: List<ProductionOrder> = emptyList(),
    val pendingOrderLinesMap: Map<String, List<ProductionOrderLine>> = emptyMap(),
    val draftLotLines: List<ProductionLotLine> = emptyList(),

    // ── Completar lote (producedQuantity editables) ───────────────────────────
    val editableLotLines: List<ProductionLotLine> = emptyList(),

    // ── Vista consolidada (tab "Por fecha") ───────────────────────────────────
    val consolidatedOrders: List<ProductionOrder> = emptyList(),
    val consolidatedLinesMap: Map<String, List<ProductionOrderLine>> = emptyMap(),
    val isConsolidatedLoading: Boolean = false,

    // ── Estado general ────────────────────────────────────────────────────────
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val isCompleted: Boolean = false,
    val error: String? = null,
    val stockWarnings: List<String> = emptyList()
)

class ProductionViewModel(
    private val productionRepository: ProductionRepository,
    private val ingredientRepository: IngredientRepository,
    private val recipeRepository: RecipeRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductionUiState())
    val uiState: StateFlow<ProductionUiState> = _uiState.asStateFlow()

    // ── Carga de listas ───────────────────────────────────────────────────────

    fun loadOrders(companyId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val allBranches = runCatching { branchRepository.getBranches(companyId) }.getOrElse { emptyList() }
            val userBranches = allBranches.filter { currentUser.canAccessBranch(it.id) }
            val branchIds = userBranches.map { it.id }
            val orders = mutableListOf<ProductionOrder>()
            branchIds.forEach { branchId ->
                orders += runCatching {
                    productionRepository.getOrdersForBranch(companyId, branchId)
                }.getOrElse { emptyList() }
            }
            _uiState.update {
                it.copy(
                    orders = orders.distinctBy { o -> o.id }.sortedByDescending { o -> o.requestedDate },
                    isLoading = false
                )
            }
        }
    }

    fun loadReceivedOrders(companyId: String, currentUser: User, producingBranchId: String, date: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val orders = runCatching {
                productionRepository.getOrdersForProducingBranch(companyId, producingBranchId, date)
            }.getOrElse { emptyList() }
            _uiState.update { it.copy(orders = orders, isLoading = false) }
        }
    }

    fun loadLots(companyId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val allBranches = runCatching { branchRepository.getBranches(companyId) }.getOrElse { emptyList() }
            val producingBranches = allBranches.filter {
                it.active && currentUser.canAccessBranch(it.id) &&
                BranchCapability.CAN_PRODUCE in it.capabilities
            }
            val lots = mutableListOf<ProductionLot>()
            producingBranches.forEach { branch ->
                lots += runCatching {
                    productionRepository.getLotsForBranch(companyId, branch.id)
                }.getOrElse { emptyList() }
            }
            _uiState.update {
                it.copy(
                    lots = lots.distinctBy { l -> l.id }.sortedByDescending { l -> l.productionDate },
                    isLoading = false
                )
            }
        }
    }

    // ── Filtros ───────────────────────────────────────────────────────────────

    fun setOrderSearchQuery(query: String) { _uiState.update { it.copy(orderSearchQuery = query) } }
    fun setOrderDateFilter(date: Long?) { _uiState.update { it.copy(orderDateFilter = date) } }
    fun setLotDateFilter(date: Long?) { _uiState.update { it.copy(lotDateFilter = date) } }

    // ── Detalle de pedido ─────────────────────────────────────────────────────

    fun loadOrderDetail(companyId: String, orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true) }
            val order = runCatching { productionRepository.getOrder(companyId, orderId) }.getOrNull()
            val lines = if (order != null)
                runCatching { productionRepository.getOrderLines(companyId, orderId) }.getOrElse { emptyList() }
            else emptyList()
            _uiState.update { it.copy(selectedOrder = order, selectedOrderLines = lines, isDetailLoading = false) }
        }
    }

    fun cancelOrder(companyId: String, orderId: String) {
        viewModelScope.launch {
            runCatching {
                productionRepository.updateOrderStatus(companyId, orderId, ProductionOrderStatus.CANCELLED)
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        orders = state.orders.map { if (it.id == orderId) it.copy(status = ProductionOrderStatus.CANCELLED) else it },
                        selectedOrder = state.selectedOrder?.takeIf { it.id == orderId }
                            ?.copy(status = ProductionOrderStatus.CANCELLED) ?: state.selectedOrder
                    )
                }
            }.onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    // ── Formulario de pedido ──────────────────────────────────────────────────

    fun loadOrderFormData(companyId: String, currentUser: User? = null) {
        viewModelScope.launch {
            val allBranches = runCatching { branchRepository.getBranches(companyId) }.getOrElse { emptyList() }
            val producing = allBranches.filter { it.active && BranchCapability.CAN_PRODUCE in it.capabilities }
            val selling = if (currentUser != null) {
                // Una sucursal puede ser solicitante si tiene CAN_SELL,
                // incluyendo plantas productoras que también son tiendas (CAN_PRODUCE + CAN_SELL)
                allBranches.filter {
                    it.active && currentUser.canAccessBranch(it.id) && BranchCapability.CAN_SELL in it.capabilities
                }.ifEmpty {
                    // Fallback: si ninguna tiene CAN_SELL configurado, mostrar todas las accesibles
                    allBranches.filter { it.active && currentUser.canAccessBranch(it.id) }
                }
            } else emptyList()
            _uiState.update { it.copy(producingBranches = producing, sellingBranches = selling) }
        }
    }

    fun updateDraftOrderLine(line: ProductionOrderLine) {
        _uiState.update { state ->
            val existing = state.draftOrderLines.find { it.productId == line.productId }
            val updated = if (existing != null) {
                state.draftOrderLines.map { if (it.productId == line.productId) line else it }
            } else {
                state.draftOrderLines + line
            }
            state.copy(draftOrderLines = updated.filter { it.requestedQuantity > 0.0 })
        }
    }

    fun saveOrder(
        companyId: String,
        currentUser: User,
        sellingBranch: Branch,
        producingBranch: Branch,
        requestedDate: Long,
        notes: String?
    ) {
        viewModelScope.launch {
            val lines = _uiState.value.draftOrderLines
            if (lines.isEmpty()) {
                _uiState.update { it.copy(error = "Agrega al menos un producto al pedido") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val order = ProductionOrder(
                    sellingBranchId = sellingBranch.id,
                    sellingBranchName = sellingBranch.name,
                    producingBranchId = producingBranch.id,
                    producingBranchName = producingBranch.name,
                    requestedDate = requestedDate,
                    status = ProductionOrderStatus.PENDING,
                    notes = notes?.trim()?.ifEmpty { null },
                    createdByUserId = currentUser.id,
                    createdByUserName = currentUser.displayName.ifEmpty { currentUser.email }
                )
                productionRepository.saveOrder(companyId, order, lines)
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSaved = true, draftOrderLines = emptyList()) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message ?: "Error al guardar el pedido") }
            }
        }
    }

    // ── Formulario de lote ────────────────────────────────────────────────────

    fun loadLotFormData(companyId: String, producingBranchId: String, date: Long, products: List<Product>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val shifts = runCatching {
                productionRepository.getShiftsForBranch(companyId, producingBranchId)
            }.getOrElse { emptyList() }

            val pendingOrders = runCatching {
                productionRepository.getOrdersForProducingBranch(companyId, producingBranchId, date)
            }.getOrElse { emptyList() }.filter { it.status == ProductionOrderStatus.PENDING }

            val linesMap = mutableMapOf<String, List<ProductionOrderLine>>()
            pendingOrders.forEach { order ->
                linesMap[order.id] = runCatching {
                    productionRepository.getOrderLines(companyId, order.id)
                }.getOrElse { emptyList() }
            }

            // Cargar recetas con yields para poder asociar producto → receta
            val recipesWithYields = runCatching {
                recipeRepository.getRecipesWithYields(companyId)
            }.getOrElse { emptyList() }

            val consolidated = consolidateOrderLines(linesMap.values.flatten(), recipesWithYields, products)

            _uiState.update {
                it.copy(
                    shifts = shifts,
                    pendingOrdersForDate = pendingOrders,
                    pendingOrderLinesMap = linesMap,
                    draftLotLines = consolidated,
                    isLoading = false
                )
            }
        }
    }

    private fun consolidateOrderLines(
        allLines: List<ProductionOrderLine>,
        recipes: List<Recipe>,
        products: List<Product>
    ): List<ProductionLotLine> {
        val grouped = allLines.groupBy { it.productId }
        return grouped.map { (productId, lines) ->
            val product = products.find { it.id == productId }
            val recipe = recipes.find { r -> r.yields.any { it.productId == productId } }
            ProductionLotLine(
                productId = productId,
                productName = lines.first().productName,
                productUnit = lines.first().productUnit,
                rawWeightGrams = product?.rawWeightGrams,
                plannedQuantity = lines.sumOf { it.requestedQuantity },
                producedQuantity = lines.sumOf { it.requestedQuantity },
                recipeId = recipe?.id ?: "",
                recipeName = recipe?.name ?: ""
            )
        }.sortedBy { it.productName }
    }

    fun updateDraftLotLine(line: ProductionLotLine) {
        _uiState.update { state ->
            state.copy(draftLotLines = state.draftLotLines.map { if (it.productId == line.productId) line else it })
        }
    }

    fun saveLot(
        companyId: String,
        currentUser: User,
        producingBranch: Branch,
        shift: ProductionShift,
        productionDate: Long,
        notes: String?
    ) {
        viewModelScope.launch {
            val lines = _uiState.value.draftLotLines
            if (lines.isEmpty()) {
                _uiState.update { it.copy(error = "No hay líneas para el lote") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val sourceOrderIds = _uiState.value.pendingOrdersForDate.map { it.id }
                val lot = ProductionLot(
                    producingBranchId = producingBranch.id,
                    producingBranchName = producingBranch.name,
                    shiftId = shift.id,
                    shiftName = shift.name,
                    productionDate = productionDate,
                    status = ProductionLotStatus.OPEN,
                    notes = notes?.trim()?.ifEmpty { null },
                    createdByUserId = currentUser.id,
                    createdByUserName = currentUser.displayName.ifEmpty { currentUser.email },
                    sourceOrderIds = sourceOrderIds
                )
                productionRepository.saveLot(companyId, lot, lines)
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSaved = true, draftLotLines = emptyList()) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message ?: "Error al crear el lote") }
            }
        }
    }

    // ── Detalle de lote ───────────────────────────────────────────────────────

    fun loadLotDetail(companyId: String, lotId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true) }
            val lot = runCatching { productionRepository.getLot(companyId, lotId) }.getOrNull()
            val lines = if (lot != null)
                runCatching { productionRepository.getLotLines(companyId, lotId) }.getOrElse { emptyList() }
            else emptyList()
            _uiState.update {
                it.copy(
                    selectedLot = lot,
                    selectedLotLines = lines,
                    editableLotLines = lines,
                    isDetailLoading = false
                )
            }
        }
    }

    fun startLot(companyId: String, lotId: String) {
        viewModelScope.launch {
            runCatching {
                productionRepository.updateLotStatus(companyId, lotId, ProductionLotStatus.IN_PROGRESS)
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        selectedLot = state.selectedLot?.copy(status = ProductionLotStatus.IN_PROGRESS),
                        lots = state.lots.map { if (it.id == lotId) it.copy(status = ProductionLotStatus.IN_PROGRESS) else it }
                    )
                }
            }.onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun updateEditableLotLine(line: ProductionLotLine) {
        _uiState.update { state ->
            state.copy(editableLotLines = state.editableLotLines.map { if (it.id == line.id) line else it })
        }
    }

    // ── Completar lote + descuento de stock ───────────────────────────────────

    fun completeLot(companyId: String, lot: ProductionLot, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, stockWarnings = emptyList()) }
            runCatching {
                val lines = _uiState.value.editableLotLines

                // 1. Marcar lote como COMPLETADO
                productionRepository.completeLot(companyId, lot.id, lines)

                // 2. Marcar pedidos fuente como CONFIRMADOS
                lot.sourceOrderIds.forEach { orderId ->
                    runCatching {
                        productionRepository.updateOrderStatus(companyId, orderId, ProductionOrderStatus.CONFIRMED)
                    }
                }

                // 3. Descontar insumos del stock
                val warnings = mutableListOf<String>()

                // Cargar recetas completas (con etapas y componentes) para la fórmula del panadero
                val activeLines = lines.filter { it.producedQuantity > 0 }
                val recipeIds = activeLines.filter { it.recipeId.isNotEmpty() }.map { it.recipeId }.distinct()
                val fullRecipeMap = mutableMapOf<String, Recipe>()
                recipeIds.forEach { id ->
                    runCatching { recipeRepository.getRecipe(companyId, id) }.getOrNull()
                        ?.let { fullRecipeMap[id] = it }
                }

                val ingredients = runCatching { ingredientRepository.getIngredients(companyId) }.getOrElse { emptyList() }
                val ingredientMap = ingredients.associateBy { it.id }
                val now = System.currentTimeMillis()

                for (line in activeLines) {
                    val recipe = fullRecipeMap[line.recipeId]

                    if (recipe == null) {
                        warnings.add("${line.productName}: sin receta asignada — stock no descontado")
                        continue
                    }
                    if (line.rawWeightGrams == null || line.rawWeightGrams <= 0.0) {
                        warnings.add("${line.productName}: peso crudo no definido — stock no descontado")
                        continue
                    }

                    val usage = calculateIngredientUsage(line.producedQuantity, line.rawWeightGrams, recipe)
                    if (usage.isEmpty()) {
                        warnings.add("${line.productName}: no se pudo calcular el consumo de insumos")
                        continue
                    }

                    for ((ingredientId, gramsToDeduct) in usage) {
                        val ingredient = ingredientMap[ingredientId] ?: continue
                        val baseUnit = ingredient.allowedUnits.firstOrNull() ?: "gr"
                        val delta = -convertUnits(gramsToDeduct, "gr", baseUnit)

                        val stocks = runCatching {
                            ingredientRepository.getBranchStocksForIngredient(
                                companyId, ingredientId, listOf(lot.producingBranchId)
                            )
                        }.getOrElse { emptyList() }
                        val prevStock = stocks.find { it.branchId == lot.producingBranchId }?.currentStock ?: 0.0

                        runCatching {
                            ingredientRepository.updateBranchStock(
                                companyId, ingredientId, ingredient.name,
                                lot.producingBranchId, lot.producingBranchName, delta
                            )
                        }
                        runCatching {
                            ingredientRepository.addStockMovement(
                                companyId, ingredientId, StockMovement(
                                    type = "PRODUCCION",
                                    quantity = delta,
                                    balanceBefore = prevStock,
                                    balanceAfter = (prevStock + delta).coerceAtLeast(0.0),
                                    branchId = lot.producingBranchId,
                                    branchName = lot.producingBranchName,
                                    reference = lot.id,
                                    date = now
                                )
                            )
                        }
                    }
                }

                // 4. Marcar stock como descontado (para detectar fallos parciales)
                runCatching { productionRepository.markStockDeducted(companyId, lot.id) }

                warnings
            }.onSuccess { warnings ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isCompleted = true,
                        stockWarnings = warnings,
                        selectedLot = state.selectedLot?.copy(
                            status = ProductionLotStatus.COMPLETED,
                            stockDeducted = true
                        ),
                        lots = state.lots.map {
                            if (it.id == lot.id) it.copy(status = ProductionLotStatus.COMPLETED) else it
                        }
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message ?: "Error al completar el lote") }
            }
        }
    }

    // ── Turnos (configuración) ────────────────────────────────────────────────

    fun loadShifts(companyId: String, branchId: String) {
        viewModelScope.launch {
            val shifts = runCatching {
                productionRepository.getShiftsForBranch(companyId, branchId)
            }.getOrElse { emptyList() }
            _uiState.update { it.copy(shifts = shifts) }
        }
    }

    fun saveShift(companyId: String, shift: ProductionShift) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { productionRepository.saveShift(companyId, shift) }
                .onSuccess { _uiState.update { it.copy(isLoading = false, isSaved = true) } }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, error = err.message) } }
        }
    }

    fun deleteShift(companyId: String, shiftId: String) {
        viewModelScope.launch {
            runCatching { productionRepository.deleteShift(companyId, shiftId) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(shifts = state.shifts.filter { it.id != shiftId })
                    }
                }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    fun clearSaved() { _uiState.update { it.copy(isSaved = false) } }
    fun clearCompleted() { _uiState.update { it.copy(isCompleted = false, stockWarnings = emptyList()) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }

    fun resetOrderForm() {
        _uiState.update { it.copy(draftOrderLines = emptyList(), isSaved = false, error = null) }
    }

    fun clearDraftOrderLines() {
        _uiState.update { it.copy(draftOrderLines = emptyList()) }
    }

    // ── Vista consolidada ─────────────────────────────────────────────────────

    fun loadConsolidatedView(companyId: String, producingBranchId: String, date: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConsolidatedLoading = true, error = null) }
            val orders = runCatching {
                productionRepository.getOrdersForProducingBranch(companyId, producingBranchId, date)
            }.getOrElse { emptyList() }
                .filter { it.status != ProductionOrderStatus.CANCELLED }
            val linesMap = mutableMapOf<String, List<ProductionOrderLine>>()
            orders.forEach { order ->
                linesMap[order.id] = runCatching {
                    productionRepository.getOrderLines(companyId, order.id)
                }.getOrElse { emptyList() }
            }
            _uiState.update {
                it.copy(
                    consolidatedOrders = orders,
                    consolidatedLinesMap = linesMap,
                    isConsolidatedLoading = false
                )
            }
        }
    }

    fun resetLotForm() {
        _uiState.update {
            it.copy(
                draftLotLines = emptyList(),
                pendingOrdersForDate = emptyList(),
                pendingOrderLinesMap = emptyMap(),
                isSaved = false,
                error = null
            )
        }
    }

    // ── Fórmula de panadero ───────────────────────────────────────────────────

    private fun calculateIngredientUsage(
        producedQuantity: Double,
        rawWeightGrams: Double,
        recipe: Recipe
    ): Map<String, Double> {
        if (producedQuantity <= 0.0 || rawWeightGrams <= 0.0) return emptyMap()
        if (recipe.stages.isEmpty()) return emptyMap()

        val totalRawGrams = producedQuantity * rawWeightGrams
        val result = mutableMapOf<String, Double>()

        for (stage in recipe.stages) {
            val components = stage.components
            if (components.none { it.isBase }) continue
            val sumPercentages = components.sumOf { it.percentage }
            if (sumPercentages <= 0.0) continue

            val baseWeight = totalRawGrams * 100.0 / sumPercentages
            for (component in components) {
                val grams = (component.percentage / 100.0) * baseWeight
                result[component.ingredientId] = (result[component.ingredientId] ?: 0.0) + grams
            }
        }
        return result
    }
}
