package com.bakery.erp.presentation.production

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.DispatchRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.PreparationBatchRepository
import com.bakery.erp.domain.PreparationRepository
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.domain.RecipeRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Preparation
import com.bakery.erp.model.PreparationBatch
import com.bakery.erp.model.PreparationUsage
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotDecoration
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
import com.bakery.erp.model.toBaseUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

data class ProductionUiState(
    // ── Lista de pedidos ──────────────────────────────────────────────────────
    val orders: List<ProductionOrder> = emptyList(),
    /**
     * Estado del lote (no CANCELLED) que incluye al pedido, indexado por `orderId`.
     * Se usa para mostrar "Lote abierto" / "En producción" en vez de "Pendiente" en
     * la lista y el detalle cuando aplica.
     */
    val lotStatusByOrderId: Map<String, String> = emptyMap(),
    val orderSearchQuery: String = "",
    val orderDateFilter: Long? = null,

    // ── Lista de lotes ────────────────────────────────────────────────────────
    val lots: List<ProductionLot> = emptyList(),
    val lotDateFilter: Long? = null,

    // ── Detalle ───────────────────────────────────────────────────────────────
    val selectedOrder: ProductionOrder? = null,
    val selectedOrderLines: List<ProductionOrderLine> = emptyList(),
    /**
     * Si el pedido seleccionado ya fue incorporado a un lote (no CANCELLED), aquí
     * está ese lote. Usado por la pantalla de detalle para deshabilitar el botón
     * "Editar" y mostrar un aviso al usuario.
     */
    val lotForSelectedOrder: ProductionLot? = null,
    val selectedLot: ProductionLot? = null,
    val selectedLotLines: List<ProductionLotLine> = emptyList(),
    val isDetailLoading: Boolean = false,

    // ── Formulario de pedido ──────────────────────────────────────────────────
    val producingBranches: List<Branch> = emptyList(),
    val sellingBranches: List<Branch> = emptyList(),
    val draftOrderLines: List<ProductionOrderLine> = emptyList(),
    /** Turnos activos de la planta productora seleccionada en el form. */
    val shiftsForSelectedProducingBranch: List<ProductionShift> = emptyList(),
    /**
     * Pedido PENDING ya existente que choca con la combinación que el usuario
     * intentó guardar (misma sucursal origen + planta + fecha + turno). Si no es
     * null, la pantalla debe ofrecer "Editar existente" o "Crear de todos modos".
     */
    val pendingOrderConflict: ProductionOrder? = null,

    // ── Formulario de lote ────────────────────────────────────────────────────
    val shifts: List<ProductionShift> = emptyList(),
    val pendingOrdersForDate: List<ProductionOrder> = emptyList(),
    /** Pedidos PENDING para la planta seleccionada en otras fechas — para avisar a la encargada. */
    val pendingOrdersInOtherDates: Map<Long, Int> = emptyMap(),
    val pendingOrderLinesMap: Map<String, List<ProductionOrderLine>> = emptyMap(),
    val draftLotLines: List<ProductionLotLine> = emptyList(),
    /** Preparaciones tipo DOUGH (esponja/masa madre) disponibles para usar en el lote. */
    val availableSpongeBatches: List<PreparationBatch> = emptyList(),
    /** Preparaciones tipo DECORATION agrupadas por preparationId. */
    val availableDecorationBatchesByPreparation: Map<String, List<PreparationBatch>> = emptyMap(),
    /** Catálogo completo de preparaciones del companyId para resolver fracciones de ingrediente. */
    val preparationsCatalog: List<Preparation> = emptyList(),
    /** Recetas completas (con `components`) de los productos del lote, indexadas por id. */
    val recipesById: Map<String, Recipe> = emptyMap(),
    /** Productos cacheados para fallback de campos no persistidos en la línea (ej. rawWeightGrams). */
    val productsById: Map<String, Product> = emptyMap(),

    // ── Completar lote (producedQuantity editables) ───────────────────────────
    val editableLotLines: List<ProductionLotLine> = emptyList(),

    // ── Pedidos tardíos (panel "después de cierre") ───────────────────────────
    /**
     * Pedidos PENDING para la misma `(producingBranch, día, shiftId)` del lote
     * seleccionado, pero que aún no fueron incorporados al lote. Aparecen como
     * candidatos para "Agregar al lote" — caso de cliente walk-in o sucursal que
     * olvidó enviar a tiempo.
     */
    val lateOrdersForLot: List<ProductionOrder> = emptyList(),
    val lateOrderLinesMap: Map<String, List<ProductionOrderLine>> = emptyMap(),
    val isAddingLateOrder: Boolean = false,

    // ── Vista consolidada (tab "Por fecha") ───────────────────────────────────
    val consolidatedOrders: List<ProductionOrder> = emptyList(),
    val consolidatedLinesMap: Map<String, List<ProductionOrderLine>> = emptyMap(),
    /**
     * Lote no-CANCELLED ya creado para la combinación (planta, fecha, turno) seleccionada
     * en el tab Consolidado. Si != null, se reemplaza el botón "Crear lote" por un banner
     * de "ya existe lote" para evitar duplicidad.
     */
    val lotForConsolidatedView: ProductionLot? = null,
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
    private val branchRepository: BranchRepository,
    private val preparationRepository: PreparationRepository,
    private val preparationBatchRepository: PreparationBatchRepository,
    private val dispatchRepository: DispatchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductionUiState())
    val uiState: StateFlow<ProductionUiState> = _uiState.asStateFlow()

    /** Job activo del listener en vivo del listado de pedidos. */
    private var ordersObservationJob: Job? = null

    /** Job activo del listener en vivo del detalle del lote. */
    private var lotDetailObservationJob: Job? = null

    // ── Carga de listas ───────────────────────────────────────────────────────

    /**
     * Observa pedidos del mes [year]/[month] en tiempo real. Cancela cualquier
     * observación previa y se re-suscribe a Firestore vía snapshot listener.
     *
     * Un solo listener por organización en `productionOrders` y otro en
     * `productionLots`: el filtrado por sucursal/fecha/turno ocurre en cliente.
     * Esto evita el polling N-queries-por-branch y mantiene la lista actualizada
     * en vivo (nuevos pedidos aparecen sin recargar).
     */
    fun observeOrdersForMonth(companyId: String, currentUser: User, year: Int, month: Int) {
        ordersObservationJob?.cancel()
        ordersObservationJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val tz = TimeZone.currentSystemDefault()
            val firstDay = LocalDate(year, month, 1)
            val nextMonthDay = if (month == 12) LocalDate(year + 1, 1, 1)
                               else LocalDate(year, month + 1, 1)
            val fromMillis = LocalDateTime(firstDay, LocalTime(0, 0, 0))
                .toInstant(tz).toEpochMilliseconds()
            val untilMillis = LocalDateTime(nextMonthDay, LocalTime(0, 0, 0))
                .toInstant(tz).toEpochMilliseconds()

            val allBranches = runCatching { branchRepository.getBranches(companyId) }.getOrElse { emptyList() }
            val userBranchIds = allBranches
                .filter { currentUser.canAccessBranch(it.id) }
                .map { it.id }
                .toSet()

            combine(
                productionRepository.observeOrders(companyId)
                    .catch { _uiState.update { s -> s.copy(error = it.message) } },
                productionRepository.observeLots(companyId)
                    .catch { _uiState.update { s -> s.copy(error = it.message) } }
            ) { orders, lots ->
                val filtered = orders
                    .filter { it.requestedDate in fromMillis until untilMillis }
                    .filter {
                        it.sellingBranchId in userBranchIds ||
                            it.producingBranchId in userBranchIds
                    }
                    .sortedByDescending { it.requestedDate }
                val lotStatusByOrderId = mutableMapOf<String, String>()
                for (lot in lots) {
                    if (lot.status == ProductionLotStatus.CANCELLED) continue
                    lot.sourceOrderIds.forEach { id -> lotStatusByOrderId[id] = lot.status }
                }
                filtered to lotStatusByOrderId
            }.collect { (orders, lotStatusByOrderId) ->
                _uiState.update {
                    it.copy(
                        orders = orders,
                        lotStatusByOrderId = lotStatusByOrderId,
                        isLoading = false
                    )
                }
            }
        }
    }

    /** Cancela el listener si la pantalla se cierra o cambia de contexto. */
    fun stopObservingOrders() {
        ordersObservationJob?.cancel()
        ordersObservationJob = null
    }

    /**
     * Observa en vivo el detalle de un lote: documento, líneas y pedidos tardíos
     * pendientes para la misma (planta, día, turno). Sustituye [loadLotDetail] en la
     * pantalla "Lista de Producción" para que los panaderos vean los cambios sin
     * recargar (p. ej., cuando la encargada aprueba un pedido tardío).
     */
    fun observeLotDetail(companyId: String, lotId: String) {
        lotDetailObservationJob?.cancel()
        lotDetailObservationJob = viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true) }
            combine(
                productionRepository.observeLots(companyId)
                    .catch { _uiState.update { s -> s.copy(error = it.message) } },
                productionRepository.observeLotLines(companyId, lotId)
                    .catch { _uiState.update { s -> s.copy(error = it.message) } },
                productionRepository.observeOrders(companyId)
                    .catch { _uiState.update { s -> s.copy(error = it.message) } }
            ) { lots, lines, orders ->
                Triple(lots.firstOrNull { it.id == lotId }, lines, orders)
            }.collect { (lot, lines, orders) ->
                if (lot == null) {
                    _uiState.update {
                        it.copy(
                            selectedLot = null,
                            selectedLotLines = emptyList(),
                            editableLotLines = emptyList(),
                            lateOrdersForLot = emptyList(),
                            lateOrderLinesMap = emptyMap(),
                            isDetailLoading = false
                        )
                    }
                    return@collect
                }

                val tz = TimeZone.currentSystemDefault()
                val lotDay = Instant.fromEpochMilliseconds(lot.productionDate)
                    .toLocalDateTime(tz).date
                val lateOrders = if (lot.status != ProductionLotStatus.COMPLETED &&
                    lot.status != ProductionLotStatus.CANCELLED) {
                    orders.filter {
                        it.status == ProductionOrderStatus.PENDING &&
                            it.producingBranchId == lot.producingBranchId &&
                            it.shiftId == lot.shiftId &&
                            it.id !in lot.sourceOrderIds &&
                            Instant.fromEpochMilliseconds(it.requestedDate)
                                .toLocalDateTime(tz).date == lotDay
                    }.sortedBy { it.createdAt }
                } else emptyList()

                // Las líneas de los pedidos tardíos se cargan bajo demanda (lectura por
                // pedido). Reusamos el cache previo si las líneas todavía aplican y
                // pedimos las nuevas que aparezcan.
                val previousMap = _uiState.value.lateOrderLinesMap
                val lateLinesMap = mutableMapOf<String, List<ProductionOrderLine>>()
                for (order in lateOrders) {
                    val cached = previousMap[order.id]
                    if (cached != null) {
                        lateLinesMap[order.id] = cached
                    } else {
                        runCatching {
                            productionRepository.getOrderLines(companyId, order.id)
                        }.getOrNull()?.let { lateLinesMap[order.id] = it }
                    }
                }

                // Preservar las modificaciones en memoria del panadero (sponge config),
                // mergeando con los datos frescos del listener: para líneas que ya
                // existían en `editableLotLines`, conservamos los campos de esponja.
                val previousEditable = _uiState.value.editableLotLines
                    .associateBy { it.id }
                val mergedLines = lines.map { fresh ->
                    val prev = previousEditable[fresh.id]
                    if (prev != null) {
                        fresh.copy(
                            spongeBatchId = prev.spongeBatchId,
                            spongeBatchName = prev.spongeBatchName,
                            spongeGramsUsed = prev.spongeGramsUsed
                        )
                    } else fresh
                }

                _uiState.update {
                    it.copy(
                        selectedLot = lot,
                        selectedLotLines = lines,
                        editableLotLines = mergedLines,
                        lateOrdersForLot = lateOrders,
                        lateOrderLinesMap = lateLinesMap,
                        isDetailLoading = false
                    )
                }
            }
        }
        // Cargas auxiliares one-shot que no necesitan listener (recetas / preparaciones
        // / batches). Si el panadero edita esponja, la siguiente vuelta del combine
        // preserva su selección via el merge con editableLotLines.
        viewModelScope.launch {
            val lot = runCatching { productionRepository.getLot(companyId, lotId) }.getOrNull()
                ?: return@launch
            val lines = runCatching {
                productionRepository.getLotLines(companyId, lotId)
            }.getOrElse { emptyList() }
            val recipeIds = lines.map { it.recipeId }.filter { it.isNotEmpty() }.distinct()
            val recipesById = recipeIds.mapNotNull { id ->
                runCatching { recipeRepository.getRecipe(companyId, id) }.getOrNull()?.let { id to it }
            }.toMap()
            val preparationsLight = runCatching {
                preparationRepository.getPreparations(companyId)
            }.getOrElse { emptyList() }
            val prepUsageById = preparationsLight.associate { it.id to it.usage }

            val referencedBatchIds = (
                lines.map { it.spongeBatchId } +
                    lines.flatMap { it.decorationsSnapshot.map { d -> d.batchId } }
                ).filter { it.isNotEmpty() }.distinct()
            val referencedBatches = referencedBatchIds.mapNotNull { batchId ->
                runCatching { preparationBatchRepository.getBatch(companyId, batchId) }.getOrNull()
            }
            val activeBatchesInBranch = runCatching {
                preparationBatchRepository.listActiveBatches(companyId, lot.producingBranchId)
            }.getOrElse { emptyList() }
            val activeSponges = activeBatchesInBranch.filter {
                prepUsageById[it.preparationId] == PreparationUsage.DOUGH
            }
            val mergedSpongeBatches = (
                activeSponges + referencedBatches.filter { it.id !in activeSponges.map { a -> a.id } }
                )

            val spongePrepIds = mergedSpongeBatches.map { it.preparationId }.distinct()
            val spongePreparationsFull = spongePrepIds.mapNotNull { id ->
                runCatching { preparationRepository.getPreparation(companyId, id) }.getOrNull()
            }
            val preparationsCatalog = preparationsLight.map { light ->
                spongePreparationsFull.find { it.id == light.id } ?: light
            } + spongePreparationsFull.filter { full -> preparationsLight.none { it.id == full.id } }

            _uiState.update {
                it.copy(
                    recipesById = recipesById,
                    preparationsCatalog = preparationsCatalog,
                    availableSpongeBatches = mergedSpongeBatches
                )
            }
        }
    }

    /** Cancela el observador del detalle del lote. */
    fun stopObservingLotDetail() {
        lotDetailObservationJob?.cancel()
        lotDetailObservationJob = null
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
            _uiState.update { it.copy(isDetailLoading = true, lotForSelectedOrder = null) }
            val order = runCatching { productionRepository.getOrder(companyId, orderId) }.getOrNull()
            val lines = if (order != null)
                runCatching { productionRepository.getOrderLines(companyId, orderId) }.getOrElse { emptyList() }
            else emptyList()
            // Buscar si este pedido ya fue incorporado a algún lote (no CANCELLED) de la
            // planta productora. Si está → no se puede editar desde el lado origen.
            val lot = if (order != null && order.producingBranchId.isNotEmpty()) {
                runCatching {
                    productionRepository.getLotsForBranch(companyId, order.producingBranchId)
                        .firstOrNull {
                            it.status != ProductionLotStatus.CANCELLED && orderId in it.sourceOrderIds
                        }
                }.getOrNull()
            } else null
            _uiState.update {
                it.copy(
                    selectedOrder = order,
                    selectedOrderLines = lines,
                    lotForSelectedOrder = lot,
                    isDetailLoading = false
                )
            }
        }
    }

    fun cancelOrder(companyId: String, orderId: String) {
        viewModelScope.launch {
            runCatching {
                // Si el pedido está en un lote OPEN, primero restamos sus líneas del lote.
                // Si está en IN_PROGRESS o COMPLETED, no se permite cancelar.
                val order = productionRepository.getOrder(companyId, orderId)
                    ?: error("Pedido no encontrado")
                if (order.status == ProductionOrderStatus.CANCELLED) {
                    return@runCatching
                }
                val lot = if (order.producingBranchId.isNotEmpty()) {
                    productionRepository.getLotsForBranch(companyId, order.producingBranchId)
                        .firstOrNull {
                            it.status != ProductionLotStatus.CANCELLED && orderId in it.sourceOrderIds
                        }
                } else null
                if (lot != null) {
                    when (lot.status) {
                        ProductionLotStatus.IN_PROGRESS,
                        ProductionLotStatus.COMPLETED ->
                            error("El lote ya está en producción o cerrado, no se puede cancelar")
                        ProductionLotStatus.OPEN -> {
                            val orderLines = productionRepository.getOrderLines(companyId, orderId)
                            productionRepository.removeOrderFromLot(companyId, lot.id, orderId, orderLines)
                        }
                    }
                }
                productionRepository.updateOrderStatus(companyId, orderId, ProductionOrderStatus.CANCELLED)
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        orders = state.orders.map {
                            if (it.id == orderId) it.copy(status = ProductionOrderStatus.CANCELLED) else it
                        },
                        selectedOrder = state.selectedOrder?.takeIf { it.id == orderId }
                            ?.copy(status = ProductionOrderStatus.CANCELLED) ?: state.selectedOrder,
                        lotForSelectedOrder = if (state.selectedOrder?.id == orderId) null
                                              else state.lotForSelectedOrder
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

    fun loadShiftsForProducingBranch(companyId: String, branchId: String) {
        viewModelScope.launch {
            if (branchId.isEmpty()) {
                _uiState.update { it.copy(shiftsForSelectedProducingBranch = emptyList()) }
                return@launch
            }
            val shifts = runCatching {
                productionRepository.getShiftsForBranch(companyId, branchId)
            }.getOrElse { emptyList() }.filter { it.active }
            _uiState.update { it.copy(shiftsForSelectedProducingBranch = shifts) }
        }
    }

    /** Carga un pedido para editarlo: pre-puebla [draftOrderLines] con sus líneas. */
    fun loadOrderForEdit(companyId: String, orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, draftOrderLines = emptyList()) }
            val order = runCatching { productionRepository.getOrder(companyId, orderId) }.getOrNull()
            val lines = if (order != null)
                runCatching { productionRepository.getOrderLines(companyId, orderId) }.getOrElse { emptyList() }
            else emptyList()
            _uiState.update {
                it.copy(
                    selectedOrder = order,
                    selectedOrderLines = lines,
                    draftOrderLines = lines,
                    isDetailLoading = false
                )
            }
            if (order != null && order.producingBranchId.isNotEmpty()) {
                loadShiftsForProducingBranch(companyId, order.producingBranchId)
            }
        }
    }

    fun clearPendingOrderConflict() {
        _uiState.update { it.copy(pendingOrderConflict = null) }
    }

    fun saveOrder(
        companyId: String,
        currentUser: User,
        sellingBranch: Branch,
        producingBranch: Branch,
        shift: ProductionShift?,
        requestedDate: Long,
        notes: String?,
        skipDuplicateCheck: Boolean = false
    ) {
        viewModelScope.launch {
            val lines = _uiState.value.draftOrderLines
            if (lines.isEmpty()) {
                _uiState.update { it.copy(error = "Agrega al menos un producto al pedido") }
                return@launch
            }
            // Si la combinación choca con un pedido existente, decidir:
            //  - si el pedido existente NO está en ningún lote → mostrar dialog "editar existente"
            //    (no se permite duplicar pedidos standalone).
            //  - si el pedido existente YA está en un lote OPEN o IN_PROGRESS → permitir crear
            //    el nuevo como "pedido tardío" que requiere aprobación de la planta.
            var markAsLate = false
            if (!skipDuplicateCheck) {
                val conflict = runCatching {
                    productionRepository.findPendingOrderForCombination(
                        companyId,
                        sellingBranch.id,
                        producingBranch.id,
                        requestedDate,
                        shift?.id ?: ""
                    )
                }.getOrNull()
                if (conflict != null) {
                    val conflictLot = runCatching {
                        productionRepository.getLotsForBranch(companyId, producingBranch.id)
                            .firstOrNull {
                                it.status != ProductionLotStatus.CANCELLED &&
                                    conflict.id in it.sourceOrderIds
                            }
                    }.getOrNull()
                    if (conflictLot == null) {
                        _uiState.update { it.copy(pendingOrderConflict = conflict) }
                        return@launch
                    }
                    // Pedido existente ya está en un lote: permitir como tardío.
                    markAsLate = true
                }
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val order = ProductionOrder(
                    sellingBranchId = sellingBranch.id,
                    sellingBranchName = sellingBranch.name,
                    producingBranchId = producingBranch.id,
                    producingBranchName = producingBranch.name,
                    shiftId = shift?.id ?: "",
                    shiftName = shift?.name ?: "",
                    requestedDate = requestedDate,
                    status = ProductionOrderStatus.PENDING,
                    isLateOrder = markAsLate,
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

    /**
     * Actualiza un pedido existente (cantidades + notas + turno). Las sucursales y
     * la fecha no se editan — si la encargada se equivocó en esos campos debe
     * cancelar y crear uno nuevo.
     */
    fun updateExistingOrder(
        companyId: String,
        orderId: String,
        shift: ProductionShift?,
        notes: String?
    ) {
        viewModelScope.launch {
            val current = _uiState.value.selectedOrder
            if (current == null || current.id != orderId) {
                _uiState.update { it.copy(error = "No se cargó el pedido a editar") }
                return@launch
            }
            val lines = _uiState.value.draftOrderLines
            if (lines.isEmpty()) {
                _uiState.update { it.copy(error = "Agrega al menos un producto al pedido") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val updated = current.copy(
                    shiftId = shift?.id ?: current.shiftId,
                    shiftName = shift?.name ?: current.shiftName,
                    notes = notes?.trim()?.ifEmpty { null }
                )
                productionRepository.updateOrder(companyId, updated, lines)

                // Si el pedido está incluido en un lote OPEN, recalcular las líneas del
                // lote para mantenerlo consistente con los pedidos fuente actualizados.
                if (current.producingBranchId.isNotEmpty()) {
                    val lotsForBranch = runCatching {
                        productionRepository.getLotsForBranch(companyId, current.producingBranchId)
                    }.getOrElse { emptyList() }
                    val containingLot = lotsForBranch.firstOrNull {
                        it.status == ProductionLotStatus.OPEN && current.id in it.sourceOrderIds
                    }
                    if (containingLot != null) {
                        recomputeLotLinesFromSources(companyId, containingLot.id)
                    }
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSaved = true,
                        draftOrderLines = emptyList(),
                        selectedOrder = null,
                        selectedOrderLines = emptyList()
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, error = err.message ?: "Error al actualizar el pedido")
                }
            }
        }
    }

    // ── Formulario de lote ────────────────────────────────────────────────────

    fun loadLotFormData(
        companyId: String,
        producingBranchId: String,
        date: Long,
        shiftId: String,
        products: List<Product>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val shifts = runCatching {
                productionRepository.getShiftsForBranch(companyId, producingBranchId)
            }.getOrElse { emptyList() }

            // Si aún no se eligió un turno (en el flujo sin presets), solo cargamos la
            // lista de turnos disponibles para que el usuario pueda elegir. La
            // consolidación necesita un turno fijo para no mezclar mañana con noche.
            if (shiftId.isEmpty()) {
                _uiState.update {
                    it.copy(
                        shifts = shifts,
                        pendingOrdersForDate = emptyList(),
                        pendingOrdersInOtherDates = emptyMap(),
                        pendingOrderLinesMap = emptyMap(),
                        draftLotLines = emptyList(),
                        isLoading = false
                    )
                }
                return@launch
            }

            // Cargamos TODOS los pendientes para esta planta para detectar mismatches de fecha
            // y turno. Filtramos en cliente por turno + día calendario.
            val allPendingForBranch = runCatching {
                productionRepository.getPendingOrdersForProducingBranch(companyId, producingBranchId)
            }.getOrElse { emptyList() }
                .filter { it.shiftId == shiftId }
            // Comparamos por día calendario (no por millis exactos) para tolerar pequeños drifts
            // entre dispositivos / cálculos del default de fecha.
            val tz = TimeZone.currentSystemDefault()
            val targetDay = Instant.fromEpochMilliseconds(date).toLocalDateTime(tz).date
            val pendingOrders = allPendingForBranch.filter {
                Instant.fromEpochMilliseconds(it.requestedDate).toLocalDateTime(tz).date == targetDay
            }
            val pendingInOtherDates = allPendingForBranch
                .filter {
                    Instant.fromEpochMilliseconds(it.requestedDate).toLocalDateTime(tz).date != targetDay
                }
                .groupBy { it.requestedDate }
                .mapValues { it.value.size }

            val linesMap = mutableMapOf<String, List<ProductionOrderLine>>()
            pendingOrders.forEach { order ->
                linesMap[order.id] = runCatching {
                    productionRepository.getOrderLines(companyId, order.id)
                }.getOrElse { emptyList() }
            }

            val recipesWithYields = runCatching {
                recipeRepository.getRecipesWithYields(companyId)
            }.getOrElse { emptyList() }

            val activeBatches = runCatching {
                preparationBatchRepository.listActiveBatches(companyId, producingBranchId)
            }.getOrElse { emptyList() }

            val preparations = runCatching {
                preparationRepository.getPreparations(companyId)
            }.getOrElse { emptyList() }
            val prepUsageById = preparations.associate { it.id to it.usage }

            val spongeBatches = activeBatches.filter {
                prepUsageById[it.preparationId] == PreparationUsage.DOUGH
            }
            val decorationBatches = activeBatches.filter {
                prepUsageById[it.preparationId] == PreparationUsage.DECORATION
            }.groupBy { it.preparationId }

            val consolidated = consolidateOrderLines(
                linesMap.values.flatten(), recipesWithYields, products, decorationBatches
            )

            // Cargar la receta completa de cada línea (con components planos) para mostrar
            // el escalado al panadero.
            val recipeIds = consolidated.map { it.recipeId }.filter { it.isNotEmpty() }.distinct()
            val recipesById = recipeIds.mapNotNull { id ->
                runCatching { recipeRepository.getRecipe(companyId, id) }.getOrNull()?.let { id to it }
            }.toMap()

            _uiState.update {
                it.copy(
                    shifts = shifts,
                    pendingOrdersForDate = pendingOrders,
                    pendingOrdersInOtherDates = pendingInOtherDates,
                    pendingOrderLinesMap = linesMap,
                    draftLotLines = consolidated,
                    availableSpongeBatches = spongeBatches,
                    availableDecorationBatchesByPreparation = decorationBatches,
                    preparationsCatalog = preparations,
                    recipesById = recipesById,
                    isLoading = false
                )
            }
        }
    }

    /**
     * Calcula la "vista escalada" de la receta para una línea del lote: cuánto pesar de cada
     * ingrediente para producir [ProductionLotLine.plannedQuantity] unidades del producto,
     * descontando lo que aporta la esponja si el panadero la activó.
     */
    /**
     * Devuelve un mensaje explicando por qué [buildScaledRecipeView] no puede construir
     * la vista para esta línea, o null si todo está OK.
     */
    fun diagnoseScaledRecipe(line: ProductionLotLine): String? {
        if (line.recipeId.isEmpty()) {
            return "El producto \"${line.productName}\" no tiene receta asociada. " +
                "Edita la receta y agrégalo en \"Productos que genera\"."
        }
        val recipe = _uiState.value.recipesById[line.recipeId]
        if (recipe == null) {
            return "No se pudo cargar la receta \"${line.recipeName}\". Recarga la pantalla."
        }
        val rawWeightGrams = effectiveRawWeightGrams(line)
        if (rawWeightGrams == null || rawWeightGrams <= 0.0) {
            return "El producto \"${line.productName}\" no tiene peso en crudo configurado. " +
                "Edítalo en el módulo Productos (campo \"Peso en crudo\") para poder calcular la receta."
        }
        val quantity = if (line.producedQuantity > 0.0) line.producedQuantity else line.plannedQuantity
        if (quantity <= 0.0) {
            return "La cantidad a producir es 0."
        }
        val components = recipe.allComponents
        if (components.isEmpty()) {
            return "La receta \"${recipe.name}\" no tiene ingredientes."
        }
        if (components.none { it.isBase }) {
            return "La receta \"${recipe.name}\" no tiene ningún ingrediente marcado como base (harina)."
        }
        if (components.sumOf { it.percentage } <= 0.0) {
            return "La receta \"${recipe.name}\" tiene porcentajes inválidos."
        }
        return null
    }

    /**
     * Peso en crudo efectivo para una línea: si la línea no lo tiene (lotes creados antes de
     * que se cargaran los productos), cae al producto cacheado.
     */
    private fun effectiveRawWeightGrams(line: ProductionLotLine): Double? {
        val fromLine = line.rawWeightGrams
        if (fromLine != null && fromLine > 0.0) return fromLine
        return _uiState.value.productsById[line.productId]?.rawWeightGrams
    }

    /** Refresca el caché de productos en el state (llamado desde las screens). */
    fun updateProductsCache(products: List<Product>) {
        if (products.isEmpty()) return
        _uiState.update { it.copy(productsById = products.associateBy { p -> p.id }) }
    }

    fun buildScaledRecipeView(line: ProductionLotLine): ScaledRecipeView? {
        val recipe = _uiState.value.recipesById[line.recipeId] ?: return null
        val rawWeightGrams = effectiveRawWeightGrams(line) ?: return null
        // Si el panadero ya ajustó la cantidad real producida, se usa esa; si no, la planeada.
        val quantity = if (line.producedQuantity > 0.0) line.producedQuantity else line.plannedQuantity
        if (quantity <= 0.0 || rawWeightGrams <= 0.0) return null
        val components = recipe.allComponents.sortedBy { it.order }
        val sumPercentages = components.sumOf { it.percentage }
        if (sumPercentages <= 0.0 || components.none { it.isBase }) return null

        val totalDoughGrams = quantity * rawWeightGrams
        val baseWeight = totalDoughGrams * 100.0 / sumPercentages

        // Aporte de la esponja a cada ingredientId (si está activa)
        val sponge: Preparation? = _uiState.value.availableSpongeBatches
            .find { it.id == line.spongeBatchId }
            ?.let { batch ->
                _uiState.value.preparationsCatalog.find { it.id == batch.preparationId }
            }
        val spongeContributionByIngredient = mutableMapOf<String, Double>()
        if (sponge != null && line.spongeGramsUsed > 0.0) {
            val batchWeight = sponge.batchWeightGrams
            if (batchWeight > 0.0) {
                for (ing in sponge.ingredients) {
                    val gramsInBatch = toBaseUnit(ing.quantityPerBatch, ing.unit)
                    val fraction = gramsInBatch / batchWeight
                    spongeContributionByIngredient[ing.ingredientId] = line.spongeGramsUsed * fraction
                }
            }
        }

        val rows = components.map { c ->
            val totalGrams = (c.percentage / 100.0) * baseWeight
            val gramsFromSponge = spongeContributionByIngredient[c.ingredientId] ?: 0.0
            val finalGrams = (totalGrams - gramsFromSponge).coerceAtLeast(0.0)
            ScaledRecipeRow(
                ingredientId = c.ingredientId,
                ingredientName = c.ingredientName,
                percentage = c.percentage,
                totalGrams = totalGrams,
                gramsFromSponge = gramsFromSponge,
                finalGrams = finalGrams,
                isBase = c.isBase
            )
        }

        val spongeContribution = sponge?.takeIf { line.spongeGramsUsed > 0.0 }?.let {
            ScaledSpongeContribution(
                preparationName = it.name,
                gramsUsed = line.spongeGramsUsed
            )
        }

        return ScaledRecipeView(
            productName = line.productName,
            producedQuantity = quantity,
            productUnit = line.productUnit,
            rawWeightGrams = rawWeightGrams,
            totalDoughGrams = totalDoughGrams,
            rows = rows,
            sponge = spongeContribution,
            decorations = line.decorationsSnapshot.map { d ->
                ScaledDecoration(
                    preparationName = d.preparationName,
                    gramsPerUnit = d.gramsPerUnit,
                    totalGrams = d.gramsPerUnit * quantity
                )
            }
        )
    }

    private fun consolidateOrderLines(
        allLines: List<ProductionOrderLine>,
        recipes: List<Recipe>,
        products: List<Product>,
        decorationBatchesByPrep: Map<String, List<PreparationBatch>>
    ): List<ProductionLotLine> {
        val grouped = allLines.groupBy { it.productId }
        return grouped.map { (productId, lines) ->
            val product = products.find { it.id == productId }
            val recipe = recipes.find { r -> r.yields.any { it.productId == productId } }
            // Pre-seleccionar el batch con saldo más antiguo (FIFO) para cada adorno declarado.
            val decorationsSnapshot = product?.decorations.orEmpty().map { deco ->
                val candidate = decorationBatchesByPrep[deco.preparationId]?.firstOrNull()
                ProductionLotDecoration(
                    preparationId = deco.preparationId,
                    preparationName = deco.preparationName,
                    gramsPerUnit = deco.gramsPerUnit,
                    batchId = candidate?.id ?: "",
                    batchName = candidate?.preparationName ?: ""
                )
            }
            ProductionLotLine(
                productId = productId,
                productName = lines.first().productName,
                productUnit = lines.first().productUnit,
                rawWeightGrams = product?.rawWeightGrams,
                plannedQuantity = lines.sumOf { it.requestedQuantity },
                producedQuantity = lines.sumOf { it.requestedQuantity },
                recipeId = recipe?.id ?: "",
                recipeName = recipe?.name ?: "",
                decorationsSnapshot = decorationsSnapshot
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

            // Cargar datos auxiliares para mostrar la receta escalada en el detalle.
            val recipeIds = lines.map { it.recipeId }.filter { it.isNotEmpty() }.distinct()
            val recipesById = recipeIds.mapNotNull { id ->
                runCatching { recipeRepository.getRecipe(companyId, id) }.getOrNull()?.let { id to it }
            }.toMap()
            val preparationsLight = runCatching {
                preparationRepository.getPreparations(companyId)
            }.getOrElse { emptyList() }
            val prepUsageById = preparationsLight.associate { it.id to it.usage }

            // Combinamos:
            //  - los batches referenciados por las líneas (pueden estar DEPLETED si el lote
            //    se completó) para que la columna muestre el nombre correcto, y
            //  - los batches ACTIVE de tipo DOUGH en la sucursal productora para que el
            //    panadero pueda elegir/cambiar la esponja desde la pantalla de detalle.
            val referencedBatchIds = (
                lines.map { it.spongeBatchId } +
                lines.flatMap { it.decorationsSnapshot.map { d -> d.batchId } }
            ).filter { it.isNotEmpty() }.distinct()
            val referencedBatches = referencedBatchIds.mapNotNull { batchId ->
                runCatching { preparationBatchRepository.getBatch(companyId, batchId) }.getOrNull()
            }
            val activeBatchesInBranch = if (lot != null) {
                runCatching {
                    preparationBatchRepository.listActiveBatches(companyId, lot.producingBranchId)
                }.getOrElse { emptyList() }
            } else emptyList()
            val activeSponges = activeBatchesInBranch.filter {
                prepUsageById[it.preparationId] == PreparationUsage.DOUGH
            }
            // Unión sin duplicados por id (priorizamos las activas para tener saldo actualizado).
            val mergedSpongeBatches = (
                activeSponges + referencedBatches.filter { it.id !in activeSponges.map { a -> a.id } }
            )

            // ⚠️ getPreparations() trae solo los docs raíz (sin ingredientes). Para calcular el
            // aporte de la esponja por insumo necesitamos los ingredientes (subcolección).
            // Cargamos completas solo las preparaciones referenciadas por la esponja del lote.
            val spongePrepIds = mergedSpongeBatches.map { it.preparationId }.distinct()
            val spongePreparationsFull = spongePrepIds.mapNotNull { id ->
                runCatching { preparationRepository.getPreparation(companyId, id) }.getOrNull()
            }
            val preparationsCatalog = preparationsLight.map { light ->
                spongePreparationsFull.find { it.id == light.id } ?: light
            } + spongePreparationsFull.filter { full -> preparationsLight.none { it.id == full.id } }

            // Pedidos tardíos: PENDING para la misma combinación que aún no están
            // en el lote (no aparecen en sourceOrderIds). Solo si el lote es editable.
            val lateOrders = if (lot != null && lot.status != ProductionLotStatus.COMPLETED &&
                                lot.status != ProductionLotStatus.CANCELLED) {
                val tz = TimeZone.currentSystemDefault()
                val lotDay = Instant.fromEpochMilliseconds(lot.productionDate)
                    .toLocalDateTime(tz).date
                runCatching {
                    productionRepository.getPendingOrdersForProducingBranch(companyId, lot.producingBranchId)
                }.getOrElse { emptyList() }
                    .filter {
                        Instant.fromEpochMilliseconds(it.requestedDate).toLocalDateTime(tz).date == lotDay &&
                            it.shiftId == lot.shiftId &&
                            it.id !in lot.sourceOrderIds
                    }
                    .sortedBy { it.createdAt }
            } else emptyList()
            val lateLinesMap = mutableMapOf<String, List<ProductionOrderLine>>()
            lateOrders.forEach { order ->
                lateLinesMap[order.id] = runCatching {
                    productionRepository.getOrderLines(companyId, order.id)
                }.getOrElse { emptyList() }
            }

            _uiState.update {
                it.copy(
                    selectedLot = lot,
                    selectedLotLines = lines,
                    editableLotLines = lines,
                    recipesById = recipesById,
                    preparationsCatalog = preparationsCatalog,
                    availableSpongeBatches = mergedSpongeBatches,
                    lateOrdersForLot = lateOrders,
                    lateOrderLinesMap = lateLinesMap,
                    isDetailLoading = false
                )
            }
        }
    }

    /**
     * Anexa un pedido tardío al lote actual: construye [ProductionLotLine] para sus
     * líneas (reusa cache de productos/recetas en state) y llama al repositorio.
     * Recarga el detalle al final para reflejar los cambios.
     */
    fun addLateOrderToCurrentLot(companyId: String, lot: ProductionLot, order: ProductionOrder) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingLateOrder = true, error = null) }
            runCatching {
                val orderLines = _uiState.value.lateOrderLinesMap[order.id]
                    ?: productionRepository.getOrderLines(companyId, order.id)

                val productsById = _uiState.value.productsById
                // Recetas: cache existente + buscar las faltantes para productos nuevos.
                val recipesById = _uiState.value.recipesById.toMutableMap()
                val missingProductIds = orderLines.map { it.productId }.filter { pid ->
                    recipesById.values.none { r -> r.yields.any { y -> y.productId == pid } }
                }
                if (missingProductIds.isNotEmpty()) {
                    val all = runCatching {
                        recipeRepository.getRecipesWithYields(companyId)
                    }.getOrElse { emptyList() }
                    all.filter { r -> r.yields.any { it.productId in missingProductIds } }
                        .forEach { lite ->
                            runCatching { recipeRepository.getRecipe(companyId, lite.id) }
                                .getOrNull()?.let { full -> recipesById[full.id] = full }
                        }
                }

                val decorationBatchesByPrep = _uiState.value.availableDecorationBatchesByPreparation
                val newLines = orderLines.map { ol ->
                    val product = productsById[ol.productId]
                    val recipe = recipesById.values.find { r ->
                        r.yields.any { it.productId == ol.productId }
                    }
                    val decorationsSnapshot = product?.decorations.orEmpty().map { deco ->
                        val candidate = decorationBatchesByPrep[deco.preparationId]?.firstOrNull()
                        ProductionLotDecoration(
                            preparationId = deco.preparationId,
                            preparationName = deco.preparationName,
                            gramsPerUnit = deco.gramsPerUnit,
                            batchId = candidate?.id ?: "",
                            batchName = candidate?.preparationName ?: ""
                        )
                    }
                    ProductionLotLine(
                        productId = ol.productId,
                        productName = ol.productName,
                        productUnit = ol.productUnit,
                        rawWeightGrams = product?.rawWeightGrams,
                        plannedQuantity = ol.requestedQuantity,
                        producedQuantity = ol.requestedQuantity,
                        recipeId = recipe?.id ?: "",
                        recipeName = recipe?.name ?: "",
                        decorationsSnapshot = decorationsSnapshot
                    )
                }

                productionRepository.addOrderToLot(companyId, lot.id, order.id, newLines)
            }.onSuccess {
                _uiState.update { it.copy(isAddingLateOrder = false) }
                // Recarga para mostrar las nuevas líneas y limpiar el pedido del panel.
                loadLotDetail(companyId, lot.id)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isAddingLateOrder = false, error = err.message ?: "Error al agregar pedido")
                }
            }
        }
    }

    fun startLot(companyId: String, lotId: String) {
        viewModelScope.launch {
            runCatching {
                // Persistir los cambios de esponja hechos en la pantalla de detalle antes
                // de marcar el lote como IN_PROGRESS.
                _uiState.value.editableLotLines.forEach { line ->
                    runCatching { productionRepository.saveLotLine(companyId, lotId, line) }
                }
                productionRepository.updateLotStatus(companyId, lotId, ProductionLotStatus.IN_PROGRESS)
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        selectedLot = state.selectedLot?.copy(status = ProductionLotStatus.IN_PROGRESS),
                        selectedLotLines = state.editableLotLines,
                        lots = state.lots.map { if (it.id == lotId) it.copy(status = ProductionLotStatus.IN_PROGRESS) else it }
                    )
                }
            }.onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    /**
     * Guarda la configuración de esponja de un lote sin cambiar su estado.
     * Útil cuando el panadero ajusta la esponja en el detalle pero el lote sigue OPEN.
     */
    fun saveLotLineSpongeConfig(companyId: String, lotId: String) {
        viewModelScope.launch {
            runCatching {
                _uiState.value.editableLotLines.forEach { line ->
                    productionRepository.saveLotLine(companyId, lotId, line)
                }
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(selectedLotLines = state.editableLotLines)
                }
            }.onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun updateEditableLotLine(line: ProductionLotLine) {
        _uiState.update { state ->
            state.copy(editableLotLines = state.editableLotLines.map { if (it.id == line.id) line else it })
        }
    }

    /**
     * Recalcula las líneas del lote a partir de los pedidos fuente. Para cada productId
     * agregado en `lot.sourceOrderIds`:
     *   - si la línea ya existe, actualiza `plannedQuantity` (preserva sponge/decoraciones);
     *   - si la cantidad agregada es 0, elimina la línea;
     *   - si el producto es nuevo en el lote, crea la línea con receta+decoraciones.
     *
     * Se llama tras editar un pedido que está incluido en un lote OPEN para mantener
     * consistencia automática y conservar la trazabilidad a las sucursales solicitantes.
     */
    private suspend fun recomputeLotLinesFromSources(
        companyId: String,
        lotId: String
    ): Result<Unit> = runCatching {
        val lot = productionRepository.getLot(companyId, lotId) ?: return@runCatching
        if (lot.status != ProductionLotStatus.OPEN) return@runCatching
        // Sumar requestedQuantity por productId en TODOS los pedidos fuente del lote.
        val byProduct = mutableMapOf<String, Double>()
        val nameUnitByProduct = mutableMapOf<String, Pair<String, String>>()
        for (orderId in lot.sourceOrderIds) {
            val orderLines = runCatching {
                productionRepository.getOrderLines(companyId, orderId)
            }.getOrElse { emptyList() }
            for (ol in orderLines) {
                byProduct[ol.productId] = (byProduct[ol.productId] ?: 0.0) + ol.requestedQuantity
                nameUnitByProduct.getOrPut(ol.productId) { ol.productName to ol.productUnit }
            }
        }

        val existing = productionRepository.getLotLines(companyId, lotId).associateBy { it.productId }

        // Actualizar o eliminar líneas existentes.
        for ((productId, line) in existing) {
            val newQty = byProduct[productId]
            if (newQty == null || newQty <= 0.0) {
                runCatching { productionRepository.deleteLotLine(companyId, lotId, line.id) }
            } else if (newQty != line.plannedQuantity) {
                productionRepository.saveLotLine(
                    companyId, lotId,
                    line.copy(plannedQuantity = newQty, producedQuantity = newQty)
                )
            }
        }

        // Crear líneas nuevas para productos que no estaban antes en el lote.
        val newProductIds = byProduct.keys - existing.keys
        if (newProductIds.isNotEmpty()) {
            val productsById = _uiState.value.productsById
            val recipesById = _uiState.value.recipesById.toMutableMap()
            val missing = newProductIds.filter { pid ->
                recipesById.values.none { r -> r.yields.any { y -> y.productId == pid } }
            }
            if (missing.isNotEmpty()) {
                val all = runCatching {
                    recipeRepository.getRecipesWithYields(companyId)
                }.getOrElse { emptyList() }
                all.filter { r -> r.yields.any { it.productId in missing } }
                    .forEach { lite ->
                        runCatching { recipeRepository.getRecipe(companyId, lite.id) }
                            .getOrNull()?.let { full -> recipesById[full.id] = full }
                    }
            }
            val decorationBatchesByPrep = _uiState.value.availableDecorationBatchesByPreparation
            for (productId in newProductIds) {
                val qty = byProduct[productId] ?: continue
                if (qty <= 0.0) continue
                val product = productsById[productId]
                val recipe = recipesById.values.find { r ->
                    r.yields.any { it.productId == productId }
                }
                val (productName, productUnit) = nameUnitByProduct[productId] ?: ("" to "")
                val decorations = product?.decorations.orEmpty().map { deco ->
                    val candidate = decorationBatchesByPrep[deco.preparationId]?.firstOrNull()
                    ProductionLotDecoration(
                        preparationId = deco.preparationId,
                        preparationName = deco.preparationName,
                        gramsPerUnit = deco.gramsPerUnit,
                        batchId = candidate?.id ?: "",
                        batchName = candidate?.preparationName ?: ""
                    )
                }
                productionRepository.saveLotLine(
                    companyId, lotId,
                    ProductionLotLine(
                        id = "",
                        productId = productId,
                        productName = productName,
                        productUnit = productUnit,
                        rawWeightGrams = product?.rawWeightGrams,
                        plannedQuantity = qty,
                        producedQuantity = qty,
                        recipeId = recipe?.id ?: "",
                        recipeName = recipe?.name ?: "",
                        decorationsSnapshot = decorations
                    )
                )
            }
        }
    }

    /**
     * Elimina una línea del lote. Solo funciona si el lote está OPEN. La planta puede
     * usar esto para quitar un producto entero del lote (p. ej. cuando recibió un
     * pedido tardío y luego lo retiró). Recarga el detalle al terminar.
     */
    fun deleteLotLine(companyId: String, lotId: String, lineId: String) {
        viewModelScope.launch {
            runCatching {
                productionRepository.deleteLotLine(companyId, lotId, lineId)
            }.onSuccess {
                loadLotDetail(companyId, lotId)
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message ?: "Error al eliminar la línea") }
            }
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

                // 3. Descontar insumos del stock + consumir batches de esponja/adorno
                val warnings = mutableListOf<String>()
                val activeLines = lines.filter { it.producedQuantity > 0 }
                val recipeIds = activeLines.filter { it.recipeId.isNotEmpty() }.map { it.recipeId }.distinct()
                val fullRecipeMap = mutableMapOf<String, Recipe>()
                recipeIds.forEach { id ->
                    runCatching { recipeRepository.getRecipe(companyId, id) }.getOrNull()
                        ?.let { fullRecipeMap[id] = it }
                }

                // Cargar preparaciones únicas referenciadas por las líneas (esponjas usadas)
                val spongeBatchIds = activeLines.filter { it.spongeGramsUsed > 0.0 && it.spongeBatchId.isNotEmpty() }
                    .map { it.spongeBatchId }.distinct()
                val spongePreparationMap = mutableMapOf<String, Preparation>()
                spongeBatchIds.forEach { batchId ->
                    val batch = runCatching { preparationBatchRepository.getBatch(companyId, batchId) }.getOrNull()
                    if (batch != null) {
                        val prep = runCatching {
                            preparationRepository.getPreparation(companyId, batch.preparationId)
                        }.getOrNull()
                        if (prep != null) spongePreparationMap[batchId] = prep
                    }
                }

                val ingredients = runCatching { ingredientRepository.getIngredients(companyId) }.getOrElse { emptyList() }
                val ingredientMap = ingredients.associateBy { it.id }
                val now = Clock.System.now().toEpochMilliseconds()

                for (line in activeLines) {
                    val recipe = fullRecipeMap[line.recipeId]

                    if (recipe == null) {
                        warnings.add("${line.productName}: sin receta asignada — stock no descontado")
                        continue
                    }
                    val effectiveRaw = effectiveRawWeightGrams(line)
                    if (effectiveRaw == null || effectiveRaw <= 0.0) {
                        warnings.add("${line.productName}: peso crudo no definido — stock no descontado")
                        continue
                    }

                    val sponge = spongePreparationMap[line.spongeBatchId]
                    val usage = calculateLineConsumption(line, recipe, sponge)
                    if (usage.isEmpty()) {
                        warnings.add("${line.productName}: no se pudo calcular el consumo de insumos")
                        continue
                    }

                    for ((ingredientId, gramsToDeduct) in usage) {
                        if (gramsToDeduct <= 0.0) continue
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

                    // Consumir el batch de esponja según los gramos declarados.
                    if (line.spongeGramsUsed > 0.0 && line.spongeBatchId.isNotEmpty()) {
                        runCatching {
                            preparationBatchRepository.consumeFromBatch(
                                companyId, line.spongeBatchId, line.spongeGramsUsed
                            )
                        }.onFailure { warnings.add("${line.productName}: ${it.message}") }
                    }

                    // Consumir los batches de adornos: gramsPerUnit × producedQuantity
                    for (deco in line.decorationsSnapshot) {
                        if (deco.batchId.isEmpty() || deco.gramsPerUnit <= 0.0) continue
                        val totalGrams = deco.gramsPerUnit * line.producedQuantity
                        if (totalGrams <= 0.0) continue
                        runCatching {
                            preparationBatchRepository.consumeFromBatch(companyId, deco.batchId, totalGrams)
                        }.onFailure {
                            warnings.add("${line.productName} · ${deco.preparationName}: ${it.message}")
                        }
                    }
                }

                // 4. Marcar stock como descontado (para detectar fallos parciales)
                runCatching { productionRepository.markStockDeducted(companyId, lot.id) }

                // 5. Crear notas de despacho DRAFT (una por sucursal solicitante).
                //    Idempotente: si el lote se completa dos veces (raro), no duplica.
                runCatching {
                    dispatchRepository.createDispatchNotesForLot(
                        companyId,
                        lot.copy(status = ProductionLotStatus.COMPLETED),
                        currentUser
                    )
                }.onFailure {
                    warnings.add("Aviso: no se pudieron crear las notas de despacho — ${it.message}")
                }

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
        _uiState.update {
            it.copy(
                draftOrderLines = emptyList(),
                shiftsForSelectedProducingBranch = emptyList(),
                pendingOrderConflict = null,
                isSaved = false,
                error = null
            )
        }
    }

    fun clearDraftOrderLines() {
        _uiState.update { it.copy(draftOrderLines = emptyList()) }
    }

    // ── Vista consolidada ─────────────────────────────────────────────────────

    fun loadConsolidatedView(
        companyId: String,
        producingBranchId: String,
        date: Long,
        shiftId: String
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isConsolidatedLoading = true,
                    error = null,
                    lotForConsolidatedView = null
                )
            }
            // Comparamos por día calendario (no por millis exactos) para tolerar drifts
            // entre default-tomorrow del form de pedido y del chevron del tab consolidado.
            val tz = TimeZone.currentSystemDefault()
            val targetDay = Instant.fromEpochMilliseconds(date).toLocalDateTime(tz).date
            val orders = runCatching {
                productionRepository.getPendingOrdersForProducingBranch(companyId, producingBranchId)
                    // El método anterior excluye CANCELLED por el status PENDING, pero también
                    // queremos contar CONFIRMED del día (pedidos ya tomados por un lote previo
                    // pueden seguir visibles para auditoría). Por ahora solo PENDING.
            }.getOrElse { emptyList() }
                .filter {
                    Instant.fromEpochMilliseconds(it.requestedDate).toLocalDateTime(tz).date == targetDay
                }
                .filter { it.shiftId == shiftId }

            val linesMap = mutableMapOf<String, List<ProductionOrderLine>>()
            orders.forEach { order ->
                linesMap[order.id] = runCatching {
                    productionRepository.getOrderLines(companyId, order.id)
                }.getOrElse { emptyList() }
            }

            val lot = runCatching {
                productionRepository.getActiveLotForBranchDateShift(
                    companyId, producingBranchId, date, shiftId
                )
            }.getOrNull()

            _uiState.update {
                it.copy(
                    consolidatedOrders = orders,
                    consolidatedLinesMap = linesMap,
                    lotForConsolidatedView = lot,
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

    /**
     * Calcula los ingredientes (en gramos por ingredientId) que consume una línea de lote.
     * Si la línea declara esponja ([ProductionLotLine.spongeGramsUsed] > 0), resta los gramos
     * que aporta la esponja por ingredientId. La esponja se modela como [Preparation.ingredients].
     */
    private fun calculateLineConsumption(
        line: ProductionLotLine,
        recipe: Recipe,
        sponge: Preparation?
    ): Map<String, Double> {
        // Fallback al producto si la línea no tiene peso (lotes legacy con timing bug).
        val rawWeightGrams = effectiveRawWeightGrams(line) ?: return emptyMap()
        if (line.producedQuantity <= 0.0 || rawWeightGrams <= 0.0) return emptyMap()

        // Receta plana: usar allComponents (incluye fallback de stages legacy).
        val components = recipe.allComponents
        if (components.none { it.isBase }) return emptyMap()
        val sumPercentages = components.sumOf { it.percentage }
        if (sumPercentages <= 0.0) return emptyMap()

        val totalRawGrams = line.producedQuantity * rawWeightGrams
        val baseWeight = totalRawGrams * 100.0 / sumPercentages
        val result = mutableMapOf<String, Double>()
        for (component in components) {
            val grams = (component.percentage / 100.0) * baseWeight
            result[component.ingredientId] = (result[component.ingredientId] ?: 0.0) + grams
        }

        // Restar el aporte de la esponja a la masa: por cada gramo de esponja usada, qué fracción
        // de cada ingrediente aporta = quantityPerBatch_i / batchWeightGrams.
        if (sponge != null && line.spongeGramsUsed > 0.0) {
            val totalBatchWeight = sponge.batchWeightGrams
            if (totalBatchWeight > 0.0) {
                for (ing in sponge.ingredients) {
                    val ingredientGramsInBatch = toBaseUnit(ing.quantityPerBatch, ing.unit)
                    val fraction = ingredientGramsInBatch / totalBatchWeight
                    val gramsAportados = line.spongeGramsUsed * fraction
                    val current = result[ing.ingredientId] ?: 0.0
                    // Si la esponja aporta más de lo necesario, dejamos 0 (exceso aceptado).
                    result[ing.ingredientId] = (current - gramsAportados).coerceAtLeast(0.0)
                }
            }
        }
        return result
    }
}
