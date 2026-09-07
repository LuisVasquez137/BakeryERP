package com.bakery.erp.presentation.production

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.CustomerOrderRepository
import com.bakery.erp.domain.DispatchRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.NotificationRepository
import com.bakery.erp.domain.PreparationBatchRepository
import com.bakery.erp.domain.PreparationRepository
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.domain.RecipeRepository
import com.bakery.erp.domain.ForecastPeriod
import com.bakery.erp.domain.SpecialOrderImageUploader
import com.bakery.erp.domain.WeatherRepository
import com.bakery.erp.model.AppNotification
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.DoughFilling
import com.bakery.erp.model.NotificationTarget
import com.bakery.erp.model.NotificationType
import com.bakery.erp.model.Permission
import com.bakery.erp.model.FinishedMovementReference
import com.bakery.erp.model.FinishedMovementType
import com.bakery.erp.model.FinishedStockMovement
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.OrderPattern
import com.bakery.erp.model.isoWeekdayName
import com.bakery.erp.model.isoWeekdayOf
import com.bakery.erp.model.orderPatternId
import com.bakery.erp.model.sampleCount
import com.bakery.erp.model.suggestedQuantities
import com.bakery.erp.model.withRainBump
import com.bakery.erp.model.Preparation
import com.bakery.erp.model.PreparationBatch
import com.bakery.erp.model.PreparationUsage
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotDecoration
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.SourceOrderAllocation
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.ProductionOrder
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.ProductionOrderStatus
import com.bakery.erp.model.ProductionShift
import com.bakery.erp.model.Recipe
import com.bakery.erp.model.RecipeBatchCheck
import com.bakery.erp.model.checkRecipeBatchMinimums
import com.bakery.erp.model.allocateOrdersForShiftA
import com.bakery.erp.model.computeShiftSplit
import com.bakery.erp.model.DISPATCH_TRIP_BOTH
import com.bakery.erp.model.DISPATCH_TRIP_FIRST
import com.bakery.erp.model.DISPATCH_TRIP_SECOND
import com.bakery.erp.model.defaultOrderAllocationsForShiftA
import com.bakery.erp.model.pinOrderAllocation
import com.bakery.erp.model.rebalanceOrderAllocation
import com.bakery.erp.model.rebalanceShiftSplit
import com.bakery.erp.model.splitOrderAllocations
import com.bakery.erp.model.tripRestrictionConflicts
import com.bakery.erp.model.tripRestrictionPins
import com.bakery.erp.model.StockMovement
import com.bakery.erp.model.User
import com.bakery.erp.model.UserRole
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.convertUnits
import com.bakery.erp.model.countUnitsPer
import com.bakery.erp.model.gramsToUnitOrNull
import com.bakery.erp.model.toBaseUnit
import com.bakery.erp.model.toGramsOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import kotlin.math.roundToInt
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Referencia liviana a la masa de la que sale un producto. */
data class RecipeRef(val id: String, val name: String)

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
    /**
     * Sucursales con CAN_PRODUCE accesibles para el usuario actual. Alimenta el
     * header dinámico de "Lotes de Producción" — si es exactamente una, el título
     * incluye el nombre de la sucursal.
     */
    val accessibleProducingBranches: List<Branch> = emptyList(),

    // ── Detalle ───────────────────────────────────────────────────────────────
    val selectedOrder: ProductionOrder? = null,
    val selectedOrderLines: List<ProductionOrderLine> = emptyList(),
    /**
     * Si el pedido seleccionado ya fue incorporado a un lote (no CANCELLED), aquí
     * está ese lote. Usado por la pantalla de detalle para deshabilitar el botón
     * "Editar" y mostrar un aviso al usuario.
     */
    val lotForSelectedOrder: ProductionLot? = null,
    /**
     * TODOS los lotes que consolidaron el pedido seleccionado. En una planta de 2
     * turnos son dos, y pueden estar en estados distintos — [lotForSelectedOrder]
     * es solo el representativo (el abierto, si queda alguno).
     */
    val lotsForSelectedOrder: List<ProductionLot> = emptyList(),
    val selectedLot: ProductionLot? = null,
    val selectedLotLines: List<ProductionLotLine> = emptyList(),
    /**
     * Sucursal productora del lote seleccionado. Se carga para poder aplicar
     * [Branch.productionSortOrder] (el orden del panadero) a la orden de
     * producción y ofrecer su configuración desde esa pantalla.
     */
    val lotBranch: Branch? = null,
    val isDetailLoading: Boolean = false,

    // ── Formulario de pedido ──────────────────────────────────────────────────
    val producingBranches: List<Branch> = emptyList(),
    val sellingBranches: List<Branch> = emptyList(),
    /**
     * `productId` → masa de la que sale. Agrupa la lista del formulario de pedido
     * por receta: barcas, cuadrados y milhojas salen todos de la hojaldre y en
     * orden alfabético quedaban desperdigados por toda la lista.
     */
    val recipeRefByProductId: Map<String, RecipeRef> = emptyMap(),
    val draftOrderLines: List<ProductionOrderLine> = emptyList(),
    /** Turnos activos de la planta productora seleccionada en el form. */
    val shiftsForSelectedProducingBranch: List<ProductionShift> = emptyList(),
    /**
     * Pedido PENDING ya existente que choca con la combinación que el usuario
     * intentó guardar (misma sucursal origen + planta + fecha + turno). Si no es
     * null, la pantalla debe ofrecer "Editar existente" o "Crear de todos modos".
     */
    val pendingOrderConflict: ProductionOrder? = null,

    /** Pedidos de revendedores con AL MENOS UNA línea pendiente (sin asignar a
     *  ningún PO) cuyo deliveryBranchId = sucursal vendedora seleccionada en el
     *  form + misma fecha. La encargada los puede consolidar en su PO marcando
     *  líneas individuales en el modal. */
    val pendingCustomerOrdersForSelling: List<com.bakery.erp.model.CustomerOrder> = emptyList(),
    /** Líneas pendientes (linkedProductionOrderId vacío) por order. Las ya asignadas
     *  a otros POs NO aparecen aquí. */
    val pendingCustomerOrderLinesByOrder: Map<String, List<com.bakery.erp.model.CustomerOrderLine>> = emptyMap(),
    /** IDs de las CustomerOrderLines que la encargada marcó para incluir en este PO. */
    val selectedCustomerLineIds: Set<String> = emptySet(),
    /** En edición de un PO existente: snapshot de las líneas que YA estaban
     *  incluidas al momento de cargar. Usado para diff al guardar. */
    val editingPOIncludedCustomerLineIds: Set<String> = emptySet(),

    // ── Cantidades sugeridas por día de la semana ─────────────────────────────
    /** [orderPatternId] de la combinación ya resuelta, para no re-sugerir en cada
     *  recomposición ni pisar lo que la encargada escribió después. */
    val suggestionPatternId: String = "",
    val isSuggestionLoading: Boolean = false,
    /** true cuando se prellenaron cantidades desde el historial de ese día. */
    val suggestionApplied: Boolean = false,
    /** Cuántos pedidos pasados respaldan la sugerencia (0 = sin historial). */
    val suggestionSampleCount: Int = 0,
    /** ISO 1 = lunes … 7 = domingo, del día para el que se sugirió. */
    val suggestionWeekday: Int = 1,
    /**
     * `productId` → cantidad típica (mediana histórica) de ese producto para
     * ese día de la semana. Se calcula junto con la sugerencia pero se
     * mantiene aparte de [draftOrderLines] porque debe sobrevivir a que la
     * encargada edite las cantidades — es la vara con la que se compara lo
     * que está escribiendo ahora, no un valor a prellenar. Usado en
     * `ProductOrderRow` para el aviso de "estás pidiendo muy distinto a lo
     * habitual".
     */
    val expectedQuantityByProduct: Map<String, Double> = emptyMap(),
    /** true si el pronóstico de la sucursal para el día pedido subió las
     *  cantidades sugeridas por lluvia probable — ver `withRainBump`. */
    /**
     * Hubo unidades de más recomendadas por lluvia en ESTA combinación. Lo
     * consume `rainExtraUnitsByProduct` fila por fila; el diálogo de clima se
     * decide con [rainProbabilityPercent] / [rainPeriods], que existen aunque
     * ningún producto tenga historial que subir.
     */
    val rainForecastApplied: Boolean = false,
    /** Probabilidad de lluvia (%) usada para [rainForecastApplied]. */
    val rainProbabilityPercent: Int = 0,
    /** Unidades sugeridas de más (sumadas entre todos los productos) por el
     *  ajuste de lluvia — para mostrar el bulto real, no solo el %. */
    val rainExtraUnits: Int = 0,
    /** `productId` → unidades de más por lluvia, para mostrarlo en la fila de
     *  cada producto (no solo el total). */
    val rainExtraUnitsByProduct: Map<String, Int> = emptyMap(),
    /** `"$sellingBranchId|$requestedDate"` ya resuelto por [refreshRainForecast] —
     *  evita re-consultar Open-Meteo si nada cambió. */
    val rainForecastKey: String = "",
    /** Clima por tramos del día (mañana/mediodía/tarde/noche) — reemplaza la
     *  idea de "una sola hora de inicio". Ver [DailyForecast.periods]. */
    val rainPeriods: List<ForecastPeriod> = emptyList(),

    // ── Formulario de lote ────────────────────────────────────────────────────
    val shifts: List<ProductionShift> = emptyList(),
    val pendingOrdersForDate: List<ProductionOrder> = emptyList(),
    /** Pedidos PENDING para la planta seleccionada en otras fechas — para avisar a la encargada. */
    val pendingOrdersInOtherDates: Map<Long, Int> = emptyMap(),
    /** IDs de esos pedidos, para poder omitirlos todos de un botonazo. */
    val otherDatePendingOrderIds: List<String> = emptyList(),
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
    /**
     * Insumos del companyId, para traducir entre el peso que manda la receta y la
     * unidad en que realmente vive el stock (el huevo se pesa en la receta pero se
     * descuenta en unidades). Se carga una vez — el VM es singleton.
     */
    val ingredientsById: Map<String, Ingredient> = emptyMap(),

    // ── Reparto de turnos (plantas con exactamente 2 turnos activos) ──────────
    /** Líneas del día completo, ya consolidadas (cantidad total pedida por producto, sin repartir aún). */
    val shiftSplitLines: List<ProductionLotLine> = emptyList(),
    val shiftSplitShiftA: ProductionShift? = null,
    val shiftSplitShiftB: ProductionShift? = null,
    /** `productId → cantidad` para cada turno. Siempre suman la cantidad total de [shiftSplitLines]. */
    val shiftSplitQuantityA: Map<String, Double> = emptyMap(),
    val shiftSplitQuantityB: Map<String, Double> = emptyMap(),
    val shiftSplitProductsWithoutWeight: List<String> = emptyList(),
    val shiftSplitConflictingExclusions: List<String> = emptyList(),
    /** Pedidos PENDING del día que alimentan el reparto — se guardan como sourceOrderIds de ambos lotes. */
    val shiftSplitSourceOrderIds: List<String> = emptyList(),
    /** Mismos pedidos que [shiftSplitSourceOrderIds] pero con el objeto completo — los usa el diálogo de mínimo. */
    val shiftSplitPendingOrders: List<ProductionOrder> = emptyList(),
    val shiftSplitOrderLinesMap: Map<String, List<ProductionOrderLine>> = emptyMap(),
    /**
     * Unidades horneadas de más (por mínimo de amasada) que no se le asignaron a
     * ninguna sucursal y por lo tanto se quedan en la planta — una entrada por
     * turno, igual que [ProductionLotLine.plantRetainedQuantity] pero antes de
     * que exista el lote.
     */
    val shiftSplitRetainedA: Map<String, Double> = emptyMap(),
    val shiftSplitRetainedB: Map<String, Double> = emptyMap(),
    /** Si ya existe lote (de cualquier turno) para esta combinación, el reparto se reemplaza por un banner. */
    val shiftSplitExistingLots: List<ProductionLot> = emptyList(),
    /**
     * `productId → (orderId → cantidad en turno A)`. De quién son las unidades del
     * turno A, no cuántas hay — eso lo decide [shiftSplitQuantityA]. Por defecto se
     * calcula proporcional a lo que pidió cada sucursal ([allocateOrdersForShiftA]);
     * `reassignOrderAllocation` permite fijar una sucursal específica (ej. tiene un
     * encargo especial) sin afectar a las demás. El turno B de cada orden es
     * `requestedQuantity - quantityA` (conservación).
     */
    val shiftSplitOrderAllocationA: Map<String, Map<String, Double>> = emptyMap(),
    /**
     * `productId → (orderId → cantidad fijada en el primer viaje)`. Solo las
     * cantidades **declaradas**: las que la sucursal pidió en su pedido
     * ([ProductionOrderLine.earlyShiftQuantity]) o las que la encargada fijó a mano
     * en el diálogo "Por sucursal".
     *
     * Se guardan aparte de [shiftSplitOrderAllocationA] (que tiene el desglose
     * completo, declarado y derivado) para que mover la cantidad total de un turno
     * reacomode solo a las sucursales que no pidieron nada en particular, y nunca
     * pise un encargo.
     */
    val shiftSplitPinnedA: Map<String, Map<String, Double>> = emptyMap(),
    /** `productId → (orderId → motivo)` del encargo declarado, para mostrárselo a la encargada. */
    val shiftSplitEarlyNotes: Map<String, Map<String, String>> = emptyMap(),
    /**
     * `orderId → viaje` de las sucursales que solo reciben en uno de los dos
     * viajes (ver [com.bakery.erp.model.Branch.receivesOnlyInTrip]). Solo trae
     * las restringidas; la ausencia de una orden significa "se reparte normal".
     *
     * Va aparte de [shiftSplitPinnedA] aunque en el cálculo se comporte como un
     * pin, porque en pantalla no es lo mismo: un encargo es una decisión de ese
     * día que la encargada puede mover, y esto es el horario de la sucursal, que
     * no se mueve desde acá.
     */
    val shiftSplitRestrictedTrip: Map<String, Int> = emptyMap(),
    /** Avisos de "esta sucursal solo recibe en un viaje que no hornea ese pan" — ver `tripRestrictionConflicts`. */
    val shiftSplitTripRestrictionWarnings: List<String> = emptyList(),
    val isShiftSplitLoading: Boolean = false,

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
    /** true mientras se sube la foto / se guarda un pedido especial en el
     *  borrador, para bloquear el botón del diálogo y no duplicar la línea. */
    val isSavingSpecialLine: Boolean = false,
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
    private val dispatchRepository: DispatchRepository,
    private val customerOrderRepository: CustomerOrderRepository,
    private val finishedStockRepository: FinishedStockRepository,
    private val notificationRepository: NotificationRepository,
    private val weatherRepository: WeatherRepository,
    private val specialOrderImageUploader: SpecialOrderImageUploader
) : ViewModel() {

    /**
     * Escribe un aviso sin dejar que su fallo tumbe la operación que lo provocó:
     * si Firestore rechaza la notificación, el pedido igual se guardó. Nunca
     * propaga la excepción hacia arriba.
     */
    private suspend fun notifySafely(companyId: String, notification: AppNotification) {
        runCatching { notificationRepository.push(companyId, notification) }
    }

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
                // Un pedido de planta de 2 turnos puede vivir en dos lotes a la vez —
                // se muestra el estado MENOS avanzado de los dos (OPEN < IN_PROGRESS <
                // COMPLETED), que es lo que le importa a la sucursal solicitante: "qué
                // falta" para que su pedido esté completamente listo.
                val statusRank = mapOf(
                    ProductionLotStatus.OPEN to 0,
                    ProductionLotStatus.IN_PROGRESS to 1,
                    ProductionLotStatus.COMPLETED to 2
                )
                val statusesByOrderId = mutableMapOf<String, MutableList<String>>()
                for (lot in lots) {
                    if (lot.status == ProductionLotStatus.CANCELLED) continue
                    lot.sourceOrderIds.forEach { id ->
                        statusesByOrderId.getOrPut(id) { mutableListOf() }.add(lot.status)
                    }
                }
                val lotStatusByOrderId = statusesByOrderId.mapValues { (_, statuses) ->
                    statuses.minByOrNull { statusRank[it] ?: Int.MAX_VALUE } ?: statuses.first()
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
        ensureIngredientCache(companyId)
        lotDetailObservationJob?.cancel()
        lotDetailObservationJob = viewModelScope.launch {
            // lotBranch se limpia: si venimos de un lote de otra planta, su orden
            // de productos no aplica acá.
            _uiState.update { it.copy(isDetailLoading = true, lotBranch = null) }
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
                            // Un pedido sin turno ("") es de una planta de 2 turnos que
                            // reparte automático — coincide con CUALQUIER lote de esa
                            // planta/día, para que el pedido tardío aparezca en ambos
                            // paneles y la encargada elija a cuál turno lo aprueba.
                            (it.shiftId == lot.shiftId || (lot.shiftId.isNotEmpty() && it.shiftId.isEmpty())) &&
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
                            spongeGramsUsed = prev.spongeGramsUsed,
                            spongeConsumed = prev.spongeConsumed
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
            // Sucursal productora: necesaria para aplicar el orden de la lista
            // del panadero y para poder configurarlo desde esa pantalla.
            runCatching { branchRepository.getBranch(companyId, lot.producingBranchId) }
                .getOrNull()
                ?.let { branch -> _uiState.update { it.copy(lotBranch = branch) } }

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
                    accessibleProducingBranches = producingBranches,
                    isLoading = false
                )
            }
        }
    }

    /**
     * Guarda cantidades reales producidas registradas durante el turno (no al final).
     * Persiste cada línea modificada vía [saveLotLine] y actualiza [editableLotLines]
     * para que la UI refleje el cambio sin esperar al listener.
     *
     * [confirmedLineIds] son TODAS las líneas de la(s) masa(s) que se mostraron en
     * el sheet al presionar "Guardar todo" — no solo las que trae [quantities]
     * (donde se escribió algo). Marca `producedCountConfirmed = true` en todas
     * ellas: dejar un campo en blanco porque el número prellenado ya era
     * correcto sigue contando como conteo confirmado, no como "no lo conté".
     * Ver `producedCountConfirmed` en el modelo — bloquea "Terminar lote".
     */
    fun saveProducedQuantities(
        companyId: String,
        lotId: String,
        quantities: Map<String, Double>,
        confirmedLineIds: Set<String> = emptySet()
    ) {
        val touchedIds = quantities.keys + confirmedLineIds
        if (touchedIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val current = _uiState.value.editableLotLines
                val updated = current.map { line ->
                    when {
                        line.id in quantities -> line.copy(
                            producedQuantity = quantities.getValue(line.id),
                            producedCountConfirmed = true
                        )
                        line.id in confirmedLineIds -> line.copy(
                            // Nada escrito para esta línea = el prellenado (lo ya
                            // registrado, o si no, lo pedido) ya era correcto.
                            producedQuantity = line.producedQuantity.takeIf { it > 0.0 }
                                ?: line.plannedQuantity,
                            producedCountConfirmed = true
                        )
                        else -> line
                    }
                }
                updated.filter { it.id in touchedIds }.forEach { line ->
                    productionRepository.saveLotLine(companyId, lotId, line)
                }
                _uiState.update { it.copy(editableLotLines = updated) }
            }.onFailure { err -> _uiState.update { it.copy(error = err.message) } }
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
            // En una planta de 2 turnos un mismo pedido alimenta DOS lotes, y pueden
            // estar en estados distintos (la noche ya amasando, la mañana todavía
            // abierta). Antes se tomaba el primero que devolviera Firestore —
            // arbitrario — así que un turno en producción bloqueaba la edición aunque
            // el otro siguiera abierto, y el resultado cambiaba entre cargas.
            val lots = if (order != null && order.producingBranchId.isNotEmpty()) {
                runCatching {
                    productionRepository.getLotsForBranch(companyId, order.producingBranchId)
                        .filter {
                            it.status != ProductionLotStatus.CANCELLED && orderId in it.sourceOrderIds
                        }
                }.getOrElse { emptyList() }
            } else emptyList()
            // El lote "representativo" es el abierto si hay alguno: lo que decide si
            // todavía se puede tocar el pedido es que quede al menos un turno editable.
            val lot = lots.firstOrNull { it.status == ProductionLotStatus.OPEN }
                ?: lots.firstOrNull()
            _uiState.update {
                it.copy(
                    selectedOrder = order,
                    selectedOrderLines = lines,
                    lotForSelectedOrder = lot,
                    lotsForSelectedOrder = lots,
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
                // Un pedido de una planta de 2 turnos puede vivir en DOS lotes (uno por
                // turno) — hay que actuar sobre todos los que lo contengan, no solo el
                // primero que aparezca.
                val lots = if (order.producingBranchId.isNotEmpty()) {
                    productionRepository.getLotsForBranch(companyId, order.producingBranchId)
                        .filter { it.status != ProductionLotStatus.CANCELLED && orderId in it.sourceOrderIds }
                } else emptyList()
                if (lots.any { it.status == ProductionLotStatus.IN_PROGRESS || it.status == ProductionLotStatus.COMPLETED }) {
                    error("Al menos uno de los turnos ya está en producción o cerrado, no se puede cancelar")
                }
                lots.filter { it.status == ProductionLotStatus.OPEN }.forEach { lot ->
                    val orderLines = productionRepository.getOrderLines(companyId, orderId)
                    productionRepository.removeOrderFromLot(companyId, lot.id, orderId, orderLines)
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

    /**
     * Borra el pedido **de raíz** con toda su cascada: notas de despacho, lotes que
     * quedaron sin ninguna otra fuente, la muestra del patrón de sugerencias y los
     * avisos que apuntaban a lo borrado.
     *
     * ## Por qué existe, si ya está cancelar
     *
     * Cancelar es lo correcto para un pedido de verdad: deja el rastro de que
     * existió y de que alguien lo dio de baja. Pero un pedido de **prueba** no
     * debería dejar rastro — un CANCELLED se queda a la vista para siempre en las
     * listas, y hasta que se agregó esto no había forma de sacarlo desde la app
     * ([cancelOrder] además se bloquea en cuanto el lote arranca, que es
     * justamente donde queda atrapada una prueba que se llevó hasta producción).
     * Por eso la UI lo limita al OWNER y este método lo revalida: es una salida de
     * emergencia, no parte de la operación diaria.
     *
     * ## Lo que NO borra
     *
     * Se detiene, sin tocar nada, si el pedido ya movió inventario o pan real:
     * - un lote **COMPLETED** ya descontó insumos y escribió Kardex;
     * - una nota **IN_TRANSIT o posterior** documenta pan que ya viajó o se recibió.
     *
     * En ambos casos el borrado dejaría movimientos apuntando a documentos
     * inexistentes, que es peor que el pedido de prueba que se quería limpiar. La
     * esponja sí se devuelve a su lote de preparación cuando el lote se borra
     * entero, porque ese consumo se registra al confirmarla y no al cerrar el lote.
     */
    fun deleteOrderPermanently(
        companyId: String,
        orderId: String,
        currentUser: User,
        onDeleted: () -> Unit = {}
    ) {
        if (currentUser.role != UserRole.OWNER) {
            _uiState.update { it.copy(error = "Solo el propietario puede borrar un pedido") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val order = productionRepository.getOrder(companyId, orderId)
                    ?: error("Pedido no encontrado")
                val orderLines = productionRepository.getOrderLines(companyId, orderId)

                // Un pedido de planta de 2 turnos vive en DOS lotes: hay que mirarlos todos.
                val lots = if (order.producingBranchId.isNotEmpty()) {
                    productionRepository.getLotsForBranch(companyId, order.producingBranchId)
                        .filter { orderId in it.sourceOrderIds }
                } else emptyList()

                lots.firstOrNull { it.status == ProductionLotStatus.COMPLETED }?.let { lot ->
                    error(
                        "El lote ${lot.shiftName.ifEmpty { "de la planta" }} ya se cerró y descontó " +
                            "insumos. Borrar el pedido dejaría el Kardex descuadrado — cancelalo y " +
                            "ajustá el stock a mano si hace falta."
                    )
                }

                // A propósito SIN runCatching: si esta lectura falla y la damos por lista
                // vacía, la guarda de abajo no revisa nada y se borra un pedido cuyo pan
                // ya iba en el camión. Que reviente y no borre nada es el resultado correcto.
                val notes = dispatchRepository.getNotesForOrder(companyId, orderId)
                notes.firstOrNull {
                    it.status == DispatchStatus.IN_TRANSIT ||
                        it.status == DispatchStatus.ARRIVED ||
                        it.status == DispatchStatus.DELIVERED ||
                        it.status == DispatchStatus.DELIVERED_WITH_DISCREPANCY
                }?.let { note ->
                    error(
                        "La nota de despacho hacia ${note.destinationBranchName} ya salió a reparto " +
                            "o se recibió. Ese pan existe: el pedido se cancela, no se borra."
                    )
                }

                // 1. Notas de despacho (DRAFT / READY / CANCELLED) + sus líneas.
                notes.forEach { note ->
                    runCatching { dispatchRepository.deleteNote(companyId, note.id) }
                }

                // 2. Lotes. El que quede sin ninguna otra fuente se borra entero; el que
                //    todavía sirva a otras sucursales solo pierde la porción de este pedido.
                val deletedLotIds = mutableListOf<String>()
                lots.forEach { lot ->
                    val otherSources = lot.sourceOrderIds.filter { it != orderId }
                    if (otherSources.isEmpty() && lot.customerOrderIds.isEmpty()) {
                        // La esponja se descuenta al confirmarla, no al cerrar el lote: si no se
                        // devuelve acá, esos gramos quedan consumidos por un lote que ya no existe.
                        productionRepository.getLotLines(companyId, lot.id)
                            .filter {
                                it.spongeConsumed && it.spongeGramsUsed > 0.0 && it.spongeBatchId.isNotEmpty()
                            }
                            .forEach { line ->
                                runCatching {
                                    preparationBatchRepository.returnToBatch(
                                        companyId, line.spongeBatchId, line.spongeGramsUsed
                                    )
                                }
                            }
                        productionRepository.deleteLot(companyId, lot.id)
                        deletedLotIds += lot.id
                    } else if (lot.status != ProductionLotStatus.CANCELLED) {
                        productionRepository.removeOrderFromLot(companyId, lot.id, orderId, orderLines)
                    }
                    // Un lote CANCELLED que todavía alimenta a otros pedidos se deja intacto:
                    // es historia inerte y `removeOrderFromLot` lo rechaza — sin este caso la
                    // excepción cortaba el borrado a la mitad, con las notas ya eliminadas.
                }

                // 3. El pedido y su muestra en las cantidades sugeridas.
                productionRepository.deleteOrder(companyId, orderId)

                // 4. Avisos que apuntaban al pedido o a un lote borrado: si quedan, la
                //    campana lleva a una pantalla vacía.
                runCatching {
                    notificationRepository.deleteForTargets(companyId, listOf(orderId) + deletedLotIds)
                }
            }.onSuccess {
                _uiState.update { state ->
                    val wasSelected = state.selectedOrder?.id == orderId
                    state.copy(
                        isLoading = false,
                        orders = state.orders.filterNot { it.id == orderId },
                        selectedOrder = state.selectedOrder?.takeIf { it.id != orderId },
                        selectedOrderLines = if (wasSelected) emptyList() else state.selectedOrderLines,
                        lotForSelectedOrder = if (wasSelected) null else state.lotForSelectedOrder,
                        lotsForSelectedOrder = if (wasSelected) emptyList() else state.lotsForSelectedOrder
                    )
                }
                onDeleted()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
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

            ensureRecipeRefByProductIdLoaded(companyId)
        }
    }

    /**
     * Mapa producto → masa (receta), usado para agrupar listas por receta en
     * vez de producto por producto (form de pedido, diálogo de orden del
     * panadero). Se cachea en el state: el VM es singleton, así que solo se lee
     * la primera vez (getRecipesWithYields es N+1 consultas y no vale la pena
     * repetirlo en cada pantalla que lo necesita).
     */
    private suspend fun ensureRecipeRefByProductIdLoaded(companyId: String) {
        if (_uiState.value.recipeRefByProductId.isNotEmpty()) return
        val recipes = runCatching {
            recipeRepository.getRecipesWithYields(companyId)
        }.getOrElse { emptyList() }
        val refs = buildMap {
            recipes.forEach { recipe ->
                val ref = RecipeRef(recipe.id, recipe.name)
                recipe.yields.forEach { y -> put(y.productId, ref) }
            }
        }
        if (refs.isNotEmpty()) {
            _uiState.update { it.copy(recipeRefByProductId = refs) }
        }
    }

    /** Punto de entrada para pantallas que solo necesitan el mapa producto→masa
     *  (p. ej. el diálogo de orden de producción del panadero) sin cargar todo
     *  lo demás que carga `loadOrderFormData`. */
    fun loadRecipeRefByProductIdIfNeeded(companyId: String) {
        viewModelScope.launch { ensureRecipeRefByProductIdLoaded(companyId) }
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

    /** Quita una línea del borrador del pedido. Se usa para los pedidos
     *  especiales, que no tienen un campo de cantidad al que ponerle 0. */
    fun removeDraftOrderLine(productId: String) {
        _uiState.update { state ->
            state.copy(draftOrderLines = state.draftOrderLines.filter { it.productId != productId })
        }
    }

    /**
     * Agrega (o reemplaza) un pedido especial en el borrador, subiendo antes su
     * foto de referencia si la encargada eligió una.
     *
     * La foto se sube acá y no al enviar el pedido para que el diálogo pueda
     * cerrarse con la URL ya resuelta: si fallara la subida, el especial se
     * guarda igual sin foto — es un dato opcional y perderlo no puede costar el
     * encargo entero. La `productId` sintética de la línea es la clave del
     * archivo en Storage, así que reeditar el mismo especial sobreescribe su
     * foto en vez de dejar huérfana la anterior.
     */
    fun saveSpecialDraftLine(
        companyId: String,
        line: ProductionOrderLine,
        photoBytes: ByteArray?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingSpecialLine = true) }
            val photoUrl = if (photoBytes != null && photoBytes.isNotEmpty()) {
                runCatching {
                    specialOrderImageUploader.uploadSpecialItemImage(
                        photoBytes, companyId, line.productId
                    )
                }.getOrNull().orEmpty().ifEmpty { line.specialPhotoUrl }
            } else line.specialPhotoUrl
            updateDraftOrderLine(line.copy(specialPhotoUrl = photoUrl))
            _uiState.update { it.copy(isSavingSpecialLine = false) }
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

    /**
     * Carga un pedido para editarlo. Separa las cantidades manuales de la encargada
     * de los aportes de revendedores: las líneas guardadas combinan ambos, por eso
     * restamos la suma de `customerLineRefs.quantity` para dejar solo la parte
     * propia en `draftOrderLines`. Las líneas de revendedor se reflejan en
     * `selectedCustomerLineIds` para que aparezcan pre-marcadas en el modal.
     */
    fun loadOrderForEdit(companyId: String, orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, draftOrderLines = emptyList()) }
            val order = runCatching { productionRepository.getOrder(companyId, orderId) }.getOrNull()
            val lines = if (order != null)
                runCatching { productionRepository.getOrderLines(companyId, orderId) }.getOrElse { emptyList() }
            else emptyList()

            // draftOrderLines = cantidad total - aportes de revendedor.
            // Si la cantidad propia es 0, no la mostramos como línea del draft
            // (es una línea que solo existe por el aporte del revendedor).
            val manualOnlyLines = lines.mapNotNull { line ->
                val resellerQty = line.customerLineRefs.sumOf { it.quantity }
                val ownQty = (line.requestedQuantity - resellerQty).coerceAtLeast(0.0)
                if (ownQty > 0.0) line.copy(requestedQuantity = ownQty, customerLineRefs = emptyList())
                else null
            }

            val includedSet = (order?.includedCustomerLineIds ?: emptyList()).toSet()
            _uiState.update {
                it.copy(
                    selectedOrder = order,
                    selectedOrderLines = lines,
                    draftOrderLines = manualOnlyLines,
                    selectedCustomerLineIds = includedSet,
                    editingPOIncludedCustomerLineIds = includedSet,
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

    // ── Cantidades sugeridas ──────────────────────────────────────────────────

    /**
     * Prellena [draftOrderLines] con lo que esta sucursal suele pedir a esta
     * planta **ese día de la semana** (lunes, martes, …).
     *
     * Costo: 1 lectura del documento `orderPatterns/{...}` — el patrón se
     * mantiene al día desde el propio `saveOrder`. Solo la primera vez que no
     * existe el patrón se reconstruye desde el historial (escaneo acotado a los
     * últimos [OrderPattern.MAX_SAMPLES] pedidos que coinciden) y se persiste.
     *
     * Nunca pisa cantidades ya escritas: si la encargada tocó algo, se cancela.
     *
     * El clima **no** sube estas cantidades — solo calcula `rainExtraUnitsByProduct`
     * para mostrarlo como recomendación aparte junto a cada producto. Es la
     * encargada quien decide escribir la cantidad más alta o no.
     */
    fun applySuggestedQuantities(
        companyId: String,
        sellingBranchId: String,
        producingBranchId: String,
        shiftId: String,
        requestedDate: Long,
        products: List<Product>
    ) {
        if (companyId.isEmpty() || sellingBranchId.isEmpty() || producingBranchId.isEmpty()) return
        if (products.isEmpty()) return
        val weekday = isoWeekdayOf(requestedDate)
        val patternId = orderPatternId(sellingBranchId, producingBranchId, shiftId, weekday)
        // Ya resolvimos esta combinación en esta sesión del form.
        if (_uiState.value.suggestionPatternId == patternId) return
        // Un pedido especial ya anotado no cuenta como "la encargada ya escribió
        // cantidades": son dos cosas independientes, y si contara, anotar el
        // especial primero dejaría el pedido de todos los días sin prellenar.
        if (_uiState.value.draftOrderLines.any { !it.isSpecial }) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSuggestionLoading = true, suggestionPatternId = patternId) }
            val pattern = runCatching {
                productionRepository.getOrderPattern(companyId, patternId)
            }.getOrNull() ?: runCatching {
                productionRepository.rebuildOrderPatternFromHistory(
                    companyId, sellingBranchId, producingBranchId, shiftId, weekday
                )
            }.getOrNull()

            val baseExpected = pattern?.suggestedQuantities().orEmpty()
            // El clima YA NO se suma solo — solo se calcula para mostrarlo como
            // recomendación aparte (ver rainExtraUnitsByProduct en ProductOrderRow).
            // La encargada decide si lo aplica escribiéndolo ella misma; nunca
            // inventa una cantidad para un producto sin pedidos previos. Usa el
            // pronóstico que [refreshRainForecast] ya haya resuelto para esta
            // combinación (branch, fecha) — no vuelve a consultar Open-Meteo
            // acá, para no duplicar la llamada.
            val rainProbability = _uiState.value.rainProbabilityPercent.takeIf { it > 0 }
            val bumped = baseExpected.withRainBump(rainProbability)
            val rainDeltaByProduct = baseExpected.keys.associateWith { productId ->
                ((bumped[productId] ?: 0.0) - (baseExpected[productId] ?: 0.0)).roundToInt()
            }.filterValues { it > 0 }

            val productsById = products.associateBy { it.id }
            val lines = baseExpected
                .mapNotNull { (productId, qty) ->
                    val product = productsById[productId]?.takeIf { it.active }
                        ?: return@mapNotNull null
                    ProductionOrderLine(
                        id = "",
                        productId = productId,
                        productName = product.name,
                        requestedQuantity = qty,
                        productUnit = product.unit
                    )
                }
                .sortedBy { it.productName }

            _uiState.update { state ->
                // Si mientras leíamos la encargada ya escribió cantidades, no tocamos nada.
                if (state.draftOrderLines.any { !it.isSpecial }) state.copy(isSuggestionLoading = false)
                else state.copy(
                    // Los especiales ya anotados se conservan: la sugerencia solo
                    // llena productos de catálogo.
                    draftOrderLines = state.draftOrderLines.filter { it.isSpecial } + lines,
                    suggestionApplied = lines.isNotEmpty(),
                    suggestionSampleCount = pattern?.sampleCount ?: 0,
                    suggestionWeekday = weekday,
                    expectedQuantityByProduct = baseExpected,
                    rainForecastApplied = rainDeltaByProduct.isNotEmpty(),
                    rainExtraUnits = rainDeltaByProduct.values.sum(),
                    rainExtraUnitsByProduct = rainDeltaByProduct,
                    isSuggestionLoading = false
                )
            }
        }
    }

    /**
     * Refresca el % de probabilidad de lluvia para (sucursal, fecha) —
     * independiente de si ya se prellenaron cantidades ([applySuggestedQuantities]
     * no vuelve a correr una vez que hay líneas, a propósito, para no pisar lo
     * que la encargada ya escribió). Sin esto, cambiar de fecha después de la
     * primera sugerencia dejaba pegado el pronóstico del primer día probado.
     */
    fun refreshRainForecast(
        sellingBranchId: String,
        requestedDate: Long,
        sellingBranchLatitude: Double?,
        sellingBranchLongitude: Double?
    ) {
        if (sellingBranchId.isEmpty() || sellingBranchLatitude == null || sellingBranchLongitude == null) {
            _uiState.update { it.copy(rainForecastKey = "", rainProbabilityPercent = 0) }
            return
        }
        val key = "$sellingBranchId|$requestedDate"
        if (_uiState.value.rainForecastKey == key) return
        _uiState.update { it.copy(rainForecastKey = key) }
        viewModelScope.launch {
            val forecast = runCatching {
                weatherRepository.getForecast(sellingBranchLatitude, sellingBranchLongitude, requestedDate)
            }.getOrNull()
            // Si mientras tanto cambiaron de fecha otra vez, esta respuesta ya
            // quedó vieja — no la pisamos encima de la más reciente.
            if (_uiState.value.rainForecastKey != key) return@launch
            _uiState.update {
                it.copy(
                    rainProbabilityPercent = forecast?.rainProbabilityPercent ?: 0,
                    rainPeriods = forecast?.periods ?: emptyList()
                )
            }
        }
    }

    /**
     * Guarda las coordenadas capturadas (una sola vez) para una sucursal —
     * ver [com.bakery.erp.model.Branch.latitude] / `LocationRequester`.
     * Escritura parcial (nunca reescribe el resto de la sucursal) y refresca
     * `sellingBranches` en memoria para que el form la vea de inmediato, sin
     * esperar una recarga completa.
     */
    fun saveBranchLocation(companyId: String, branchId: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            runCatching { branchRepository.updateLocation(companyId, branchId, latitude, longitude) }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
            _uiState.update { state ->
                state.copy(
                    sellingBranches = state.sellingBranches.map {
                        if (it.id == branchId) it.copy(latitude = latitude, longitude = longitude) else it
                    }
                )
            }
        }
    }

    /** Borra las cantidades prellenadas para empezar de cero. */
    fun clearSuggestedQuantities() {
        _uiState.update { state ->
            // "Empezar de cero" borra lo sugerido, no los especiales que la
            // encargada escribió a mano.
            state.copy(
                draftOrderLines = state.draftOrderLines.filter { it.isSpecial },
                suggestionApplied = false
            )
        }
    }

    /**
     * Carga los pedidos de revendedor PENDING para la sucursal vendedora seleccionada,
     * filtrados por fecha de entrega = [requestedDate] (día calendario). Estos pedidos
     * son candidatos para incluir en el PO que está armando la encargada.
     */
    /**
     * Carga los pedidos de revendedor con al menos una línea pendiente para la
     * sucursal vendedora seleccionada, filtrados por fecha = [requestedDate].
     * Solo se muestran las líneas todavía no asignadas a ningún PO en
     * `pendingCustomerOrderLinesByOrder`.
     */
    fun loadPendingCustomerOrdersForSelling(
        companyId: String,
        sellingBranchId: String,
        requestedDate: Long
    ) {
        if (sellingBranchId.isEmpty() || requestedDate <= 0L) {
            _uiState.update {
                it.copy(
                    pendingCustomerOrdersForSelling = emptyList(),
                    pendingCustomerOrderLinesByOrder = emptyMap(),
                    selectedCustomerLineIds = emptySet()
                )
            }
            return
        }
        viewModelScope.launch {
            // En modo edición, también queremos ver los CO con líneas ya asignadas
            // al PO que se está editando (para que la encargada pueda desmarcarlas).
            val editingPOId = _uiState.value.selectedOrder?.id ?: ""
            val all = runCatching {
                customerOrderRepository.getPendingOrdersForSellingBranch(
                    companyId, sellingBranchId, editingPOId
                )
            }.getOrElse { emptyList() }
            val tz = TimeZone.currentSystemDefault()
            val targetDay = Instant.fromEpochMilliseconds(requestedDate).toLocalDateTime(tz).date
            val filtered = all.filter {
                Instant.fromEpochMilliseconds(it.deliveryDate).toLocalDateTime(tz).date == targetDay
            }
            // Para cada CO, cargar TODAS sus líneas; el panel filtrará las pendientes.
            // Pero ya tenemos pre-seleccionadas (caso edición) — mantenemos las líneas
            // marcadas como seleccionadas aunque ya tengan linkedProductionOrderId.
            val editingIncluded = _uiState.value.editingPOIncludedCustomerLineIds
            val linesMap = mutableMapOf<String, List<com.bakery.erp.model.CustomerOrderLine>>()
            filtered.forEach { co ->
                val all = runCatching {
                    customerOrderRepository.getOrderLines(companyId, co.id)
                }.getOrElse { emptyList() }
                // Mostrar las pendientes (sin asignar) + las pre-seleccionadas (caso edit).
                linesMap[co.id] = all.filter {
                    it.linkedProductionOrderId.isEmpty() || it.id in editingIncluded
                }
            }
            _uiState.update {
                it.copy(
                    pendingCustomerOrdersForSelling = filtered,
                    pendingCustomerOrderLinesByOrder = linesMap
                )
            }
        }
    }

    /**
     * Reemplaza la selección de líneas pendientes para un CO específico — usado
     * cuando la encargada cierra el modal con "Aceptar". NO modifica [draftOrderLines]
     * (la encargada ingresa sus cantidades manualmente). La consolidación se hace
     * solo al momento de guardar el PO.
     */
    fun setSelectedCustomerLinesForOrder(
        customerOrderId: String,
        newSelectedLineIds: Set<String>
    ) {
        val state = _uiState.value
        val orderLines = state.pendingCustomerOrderLinesByOrder[customerOrderId].orEmpty()
        val allOrderLineIds = orderLines.map { it.id }.toSet()
        // Quitar las líneas de este CO de la selección y agregar las nuevas.
        val withoutThisOrder = state.selectedCustomerLineIds - allOrderLineIds
        val newSelection = withoutThisOrder + newSelectedLineIds
        _uiState.update { it.copy(selectedCustomerLineIds = newSelection) }
    }

    fun clearCustomerOrderInclusions() {
        _uiState.update {
            it.copy(
                selectedCustomerLineIds = emptySet(),
                pendingCustomerOrdersForSelling = emptyList(),
                pendingCustomerOrderLinesByOrder = emptyMap(),
                editingPOIncludedCustomerLineIds = emptySet()
            )
        }
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
                // 1. Construir las líneas finales del PO sumando las cantidades manuales
                //    de la encargada + las líneas de revendedor seleccionadas, agrupando
                //    por productId y guardando customerLineRefs por desglose.
                val st = _uiState.value
                val coLinesById = st.pendingCustomerOrderLinesByOrder.values.flatten()
                    .associateBy { it.id }
                val coNamesById = st.pendingCustomerOrdersForSelling
                    .associate { it.id to it.customerName }
                // CO line ID → CO ID (para saber a qué pedido pertenece cada línea selected)
                val coIdByLineId: Map<String, String> = buildMap {
                    st.pendingCustomerOrderLinesByOrder.forEach { (coId, ls) ->
                        ls.forEach { l -> put(l.id, coId) }
                    }
                }

                val mergedLines = lines.toMutableList()
                st.selectedCustomerLineIds.forEach { coLineId ->
                    val coLine = coLinesById[coLineId] ?: return@forEach
                    if (!coLine.requiresProduction) return@forEach
                    val coId = coIdByLineId[coLineId] ?: return@forEach
                    val ref = com.bakery.erp.model.CustomerLineRef(
                        customerOrderId = coId,
                        customerName = coNamesById[coId] ?: "",
                        customerOrderLineId = coLineId,
                        quantity = coLine.requestedQuantity,
                        customSpec = coLine.customSpec
                    )
                    val idx = mergedLines.indexOfFirst { it.productId == coLine.productId }
                    if (idx >= 0) {
                        val existing = mergedLines[idx]
                        mergedLines[idx] = existing.copy(
                            requestedQuantity = existing.requestedQuantity + coLine.requestedQuantity,
                            customerLineRefs = existing.customerLineRefs + ref
                        )
                    } else {
                        mergedLines += ProductionOrderLine(
                            id = "",
                            productId = coLine.productId,
                            productName = coLine.productName,
                            requestedQuantity = coLine.requestedQuantity,
                            productUnit = coLine.productUnit,
                            customerLineRefs = listOf(ref)
                        )
                    }
                }

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
                    createdByUserName = currentUser.displayName.ifEmpty { currentUser.email },
                    includedCustomerLineIds = st.selectedCustomerLineIds.toList()
                )
                productionRepository.saveOrder(companyId, order, mergedLines)

                // 2. Localizar el PO recién creado para obtener su ID definitivo.
                val savedOrders = runCatching {
                    productionRepository.getOrdersForSellingBranchInRange(
                        companyId, sellingBranch.id,
                        requestedDate, requestedDate + 24L * 60 * 60 * 1000
                    )
                }.getOrElse { emptyList() }
                val ourOrder = savedOrders
                    .filter { it.producingBranchId == producingBranch.id }
                    .filter { it.shiftId == (shift?.id ?: "") }
                    .maxByOrNull { it.createdAt }
                val poId = ourOrder?.id

                // 2b. Avisar a la planta que le entró un pedido. Se dispara acá y
                //     no en el repositorio porque solo la creación desde el form
                //     es "un pedido nuevo": las recomputaciones internas del lote
                //     también escriben pedidos y no deben sonar.
                notifySafely(
                    companyId,
                    AppNotification(
                        type = NotificationType.ORDER_RECEIVED,
                        title = "${sellingBranch.name} te envió un pedido",
                        body = buildString {
                            append("${mergedLines.size} producto(s) para el ")
                            append(formatDayForNotification(requestedDate))
                            if (!shift?.name.isNullOrBlank()) append(" · turno ${shift.name}")
                            if (markAsLate) append(" · PEDIDO TARDÍO, requiere tu aprobación")
                        },
                        targetBranchIds = listOf(producingBranch.id),
                        requiredPermission = Permission.MANAGE_PRODUCTION_LOTS.name,
                        targetScreen = if (poId.isNullOrEmpty())
                            NotificationTarget.PRODUCTION_ORDER_LIST
                        else NotificationTarget.PRODUCTION_ORDER_DETAIL,
                        targetId = poId.orEmpty(),
                        createdByUserId = currentUser.id,
                        createdByUserName = currentUser.displayName.ifEmpty { currentUser.email }
                    )
                )

                // 3. Marcar cada línea de revendedor seleccionada con su linkedProductionOrderId.
                //    Agrupar por CO para llamar setLinesLinkedPO batch.
                if (!poId.isNullOrEmpty()) {
                    val linesByCo = st.selectedCustomerLineIds
                        .mapNotNull { lineId -> coIdByLineId[lineId]?.let { it to lineId } }
                        .groupBy({ it.first }, { it.second })
                    linesByCo.forEach { (coId, lineIds) ->
                        runCatching {
                            customerOrderRepository.setLinesLinkedPO(
                                companyId, coId, lineIds, poId
                            )
                        }
                    }
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSaved = true,
                        draftOrderLines = emptyList(),
                        selectedCustomerLineIds = emptySet(),
                        pendingCustomerOrdersForSelling = emptyList(),
                        pendingCustomerOrderLinesByOrder = emptyMap(),
                        editingPOIncludedCustomerLineIds = emptySet()
                    )
                }
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
                // 1. Construir las líneas combinadas (mismo merge que saveOrder).
                val st = _uiState.value
                val coLinesById = st.pendingCustomerOrderLinesByOrder.values.flatten()
                    .associateBy { it.id }
                val coNamesById = st.pendingCustomerOrdersForSelling
                    .associate { it.id to it.customerName }
                val coIdByLineId: Map<String, String> = buildMap {
                    st.pendingCustomerOrderLinesByOrder.forEach { (coId, ls) ->
                        ls.forEach { l -> put(l.id, coId) }
                    }
                }
                val mergedLines = lines.toMutableList()
                st.selectedCustomerLineIds.forEach { coLineId ->
                    val coLine = coLinesById[coLineId] ?: return@forEach
                    if (!coLine.requiresProduction) return@forEach
                    val coId = coIdByLineId[coLineId] ?: return@forEach
                    val ref = com.bakery.erp.model.CustomerLineRef(
                        customerOrderId = coId,
                        customerName = coNamesById[coId] ?: "",
                        customerOrderLineId = coLineId,
                        quantity = coLine.requestedQuantity,
                        customSpec = coLine.customSpec
                    )
                    val idx = mergedLines.indexOfFirst { it.productId == coLine.productId }
                    if (idx >= 0) {
                        val existing = mergedLines[idx]
                        mergedLines[idx] = existing.copy(
                            requestedQuantity = existing.requestedQuantity + coLine.requestedQuantity,
                            customerLineRefs = existing.customerLineRefs + ref
                        )
                    } else {
                        mergedLines += ProductionOrderLine(
                            id = "",
                            productId = coLine.productId,
                            productName = coLine.productName,
                            requestedQuantity = coLine.requestedQuantity,
                            productUnit = coLine.productUnit,
                            customerLineRefs = listOf(ref)
                        )
                    }
                }

                val updated = current.copy(
                    shiftId = shift?.id ?: current.shiftId,
                    shiftName = shift?.name ?: current.shiftName,
                    notes = notes?.trim()?.ifEmpty { null },
                    includedCustomerLineIds = st.selectedCustomerLineIds.toList()
                )
                productionRepository.updateOrder(companyId, updated, mergedLines)

                // 2. Diff: las líneas que ANTES estaban incluidas pero AHORA no →
                //    liberarlas (linkedProductionOrderId = ""). Las nuevas → asignarlas.
                val removed = st.editingPOIncludedCustomerLineIds - st.selectedCustomerLineIds
                val added = st.selectedCustomerLineIds - st.editingPOIncludedCustomerLineIds
                // Para hacer unset necesitamos saber a qué CO pertenece cada línea
                // removida — la consultamos directamente al repo si no la tenemos en cache.
                removed.groupBy { lineId ->
                    coIdByLineId[lineId] ?: ""
                }.filter { it.key.isNotEmpty() }.forEach { (coId, lineIds) ->
                    runCatching {
                        customerOrderRepository.unsetLinesLinkedPO(companyId, coId, lineIds)
                    }
                }
                added.groupBy { lineId -> coIdByLineId[lineId] ?: "" }
                    .filter { it.key.isNotEmpty() }
                    .forEach { (coId, lineIds) ->
                        runCatching {
                            customerOrderRepository.setLinesLinkedPO(
                                companyId, coId, lineIds, orderId
                            )
                        }
                    }

                // Si el pedido está incluido en uno o dos lotes OPEN (planta de 2
                // turnos: el mismo pedido puede alimentar ambos), recalcular sus líneas.
                if (current.producingBranchId.isNotEmpty()) {
                    val lotsForBranch = runCatching {
                        productionRepository.getLotsForBranch(companyId, current.producingBranchId)
                    }.getOrElse { emptyList() }
                    // Lo que decide el camino es si el pedido alimenta DOS lotes, no
                    // si los dos están abiertos: con el turno de noche ya en producción
                    // esto contaba un solo lote y usaba la recomputación de planta de un
                    // turno, que le metía al turno abierto el total del día entero (350
                    // en vez de 200) y dejaba el desglose por sucursal sin actualizar.
                    // `recomputeSplitLotLinesFromSources` ya congela el turno cerrado.
                    val containingLots = lotsForBranch.filter {
                        it.status != ProductionLotStatus.CANCELLED && current.id in it.sourceOrderIds
                    }
                    if (containingLots.size == 2) {
                        recomputeSplitLotLinesFromSources(companyId, containingLots[0], containingLots[1])
                    } else {
                        containingLots
                            .filter { it.status == ProductionLotStatus.OPEN }
                            .forEach { lot -> recomputeLotLinesFromSources(companyId, lot.id) }
                    }
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSaved = true,
                        draftOrderLines = emptyList(),
                        selectedOrder = null,
                        selectedOrderLines = emptyList(),
                        selectedCustomerLineIds = emptySet(),
                        editingPOIncludedCustomerLineIds = emptySet(),
                        pendingCustomerOrdersForSelling = emptyList(),
                        pendingCustomerOrderLinesByOrder = emptyMap()
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
        ensureIngredientCache(companyId)
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
            // Los que la planta ya decidió no producir no vuelven a aparecer acá.
            val otherDateOrders = allPendingForBranch
                .filter { !it.excludedFromLotPlanning }
                .filter {
                    Instant.fromEpochMilliseconds(it.requestedDate).toLocalDateTime(tz).date != targetDay
                }
            val pendingInOtherDates = otherDateOrders
                .groupBy { it.requestedDate }
                .mapValues { it.value.size }
            val otherDateOrderIds = otherDateOrders.map { it.id }

            // Las líneas de cada pedido en paralelo: eran N viajes secuenciales a
            // Firestore y en una planta con varias sucursales solicitantes eso es
            // lo que hacía lenta esta pantalla.
            val linesMap = coroutineScope {
                pendingOrders.map { order ->
                    async {
                        order.id to runCatching {
                            productionRepository.getOrderLines(companyId, order.id)
                        }.getOrElse { emptyList() }
                    }
                }.awaitAll().toMap()
            }

            // Nota: la consolidación de CustomerOrders ya NO ocurre aquí. Los pedidos
            // de revendedor se consolidan al ProductionOrder de la sucursal vendedora
            // (vía ProductionOrderFormScreen). El lote recibe solo los ProductionOrders.

            // Recetas, batches y preparaciones no dependen entre sí: en paralelo.
            val (recipesWithYields, activeBatches, preparations) = coroutineScope {
                val recipesDeferred = async {
                    runCatching { recipeRepository.getRecipesWithYields(companyId) }
                        .getOrElse { emptyList() }
                }
                val batchesDeferred = async {
                    runCatching {
                        preparationBatchRepository.listActiveBatches(companyId, producingBranchId)
                    }.getOrElse { emptyList() }
                }
                val prepsDeferred = async {
                    runCatching { preparationRepository.getPreparations(companyId) }
                        .getOrElse { emptyList() }
                }
                Triple(recipesDeferred.await(), batchesDeferred.await(), prepsDeferred.await())
            }
            val prepUsageById = preparations.associate { it.id to it.usage }

            val spongeBatches = activeBatches.filter {
                prepUsageById[it.preparationId] == PreparationUsage.DOUGH
            }
            val decorationBatches = activeBatches.filter {
                prepUsageById[it.preparationId] == PreparationUsage.DECORATION
            }.groupBy { it.preparationId }

            val consolidated = consolidateOrderLines(
                linesMap.values.flatten(),
                recipesWithYields, products, decorationBatches
            )

            // Cargar la receta completa de cada línea (con components planos) para mostrar
            // el escalado al panadero.
            val recipeIds = consolidated.map { it.recipeId }.filter { it.isNotEmpty() }.distinct()
            val recipesById = coroutineScope {
                recipeIds.map { id ->
                    async {
                        runCatching { recipeRepository.getRecipe(companyId, id) }
                            .getOrNull()?.let { id to it }
                    }
                }.awaitAll().filterNotNull().toMap()
            }

            _uiState.update {
                it.copy(
                    shifts = shifts,
                    pendingOrdersForDate = pendingOrders,
                    pendingOrdersInOtherDates = pendingInOtherDates,
                    otherDatePendingOrderIds = otherDateOrderIds,
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

    // ── Receta escalada por masa (vista del panadero) ─────────────────────────

    /**
     * Cantidad efectiva de una línea: lo realmente producido si ya se registró,
     * si no lo planeado.
     */
    private fun effectiveQuantity(line: ProductionLotLine): Double =
        if (line.producedQuantity > 0.0) line.producedQuantity else line.plannedQuantity

    /** Masa (en gramos) que aporta una línea. 0 si le falta el peso en crudo. */
    fun doughGramsOf(line: ProductionLotLine): Double {
        val raw = effectiveRawWeightGrams(line) ?: return 0.0
        return effectiveQuantity(line) * raw
    }

    /** Productos del grupo a los que les falta el peso en crudo configurado. */
    fun linesMissingRawWeight(lines: List<ProductionLotLine>): List<ProductionLotLine> =
        lines.filter { (effectiveRawWeightGrams(it) ?: 0.0) <= 0.0 && effectiveQuantity(it) > 0.0 }

    /**
     * Masa extra (en gramos) que la masa [recipeId] tiene que dar para rellenos
     * de OTROS productos — Pan de Capa pidiendo bolitas de Suave, por ejemplo
     * (ver [DoughFilling]). [allLines] debe ser TODAS las líneas del lote, no
     * solo las de esta masa: el producto que aporta el relleno vive en la masa
     * de Capa, no en la de Suave.
     */
    fun extraDoughGramsForRecipe(allLines: List<ProductionLotLine>, recipeId: String): Double =
        allLines.sumOf { line ->
            effectiveQuantity(line) * line.doughFillingsSnapshot
                .filter { it.recipeId == recipeId }
                .sumOf { it.rawWeightGrams }
        }

    /**
     * Detalle de quién aporta esa masa extra — para mostrarle al panadero
     * "+100 bolitas para Pan de Capa" en vez de solo un número más grande sin
     * explicación.
     */
    fun extraDoughContributionsForRecipe(
        allLines: List<ProductionLotLine>,
        recipeId: String
    ): List<Pair<String, Double>> = allLines
        .mapNotNull { line ->
            val grams = effectiveQuantity(line) * line.doughFillingsSnapshot
                .filter { it.recipeId == recipeId }
                .sumOf { it.rawWeightGrams }
            if (grams > 0.0) line.productName to grams else null
        }

    /** Gramos de esponja declarados para toda la masa. */
    fun spongeGramsOfGroup(lines: List<ProductionLotLine>): Double =
        lines.sumOf { it.spongeGramsUsed }

    fun spongeBatchIdOfGroup(lines: List<ProductionLotLine>): String =
        lines.firstOrNull { it.spongeBatchId.isNotEmpty() }?.spongeBatchId.orEmpty()

    fun spongeBatchNameOfGroup(lines: List<ProductionLotLine>): String =
        lines.firstOrNull { it.spongeBatchName.isNotEmpty() }?.spongeBatchName.orEmpty()

    /** true si la esponja de esta masa ya se confirmó (y por lo tanto ya se
     *  descontó del batch) — ver [confirmSpongeForGroup]. */
    fun spongeConsumedOfGroup(lines: List<ProductionLotLine>): Boolean =
        lines.any { it.spongeConsumed }

    /**
     * Reparte [totalGrams] de esponja entre [lines] en proporción a la masa
     * que aporta cada una — la amasada es una sola para todos los productos
     * de la receta, pero el descuento de insumos al cerrar el lote es por
     * línea, y cargarlo todo a una sola haría que el `coerceAtLeast(0)` se
     * comiera el exceso y quedara mal el Kardex.
     */
    private fun distributeSpongeGrams(lines: List<ProductionLotLine>, totalGrams: Double): Map<String, Double> {
        val doughByLine = lines.associate { it.id to doughGramsOf(it) }
        val totalDough = doughByLine.values.sum()
        return lines.associate { line ->
            line.id to when {
                totalGrams <= 0.0 -> 0.0
                totalDough > 0.0 -> totalGrams * (doughByLine[line.id] ?: 0.0) / totalDough
                // Sin pesos en crudo no hay proporción posible: se reparte parejo.
                else -> totalGrams / lines.size
            }
        }
    }

    /**
     * Confirma el uso de esponja para una masa completa: descuenta [totalGrams]
     * del batch **de inmediato** (no espera a "Completar lote") y persiste las
     * líneas afectadas, para que dejen de estar disponibles para otra masa y
     * quede registro aunque el panadero salga de la pantalla sin completar el
     * lote. Ver `spongeConsumed` en el modelo.
     */
    fun confirmSpongeForGroup(
        companyId: String,
        lotId: String,
        lines: List<ProductionLotLine>,
        batch: PreparationBatch,
        totalGrams: Double
    ) {
        if (lines.isEmpty() || totalGrams <= 0.0) return
        viewModelScope.launch {
            runCatching {
                val shareByLineId = distributeSpongeGrams(lines, totalGrams)
                val ids = lines.map { it.id }.toSet()
                val updatedLines = _uiState.value.editableLotLines.map { line ->
                    if (line.id !in ids) return@map line
                    line.copy(
                        spongeBatchId = batch.id,
                        spongeBatchName = batch.preparationName,
                        spongeGramsUsed = shareByLineId[line.id] ?: 0.0,
                        spongeConsumed = true
                    )
                }
                preparationBatchRepository.consumeFromBatch(companyId, batch.id, totalGrams)
                updatedLines.filter { it.id in ids }.forEach { line ->
                    productionRepository.saveLotLine(companyId, lotId, line)
                }
                _uiState.update { state ->
                    state.copy(
                        editableLotLines = updatedLines,
                        availableSpongeBatches = state.availableSpongeBatches.map { b ->
                            if (b.id == batch.id) b.copy(
                                remainingWeightGrams = (b.remainingWeightGrams - totalGrams).coerceAtLeast(0.0)
                            ) else b
                        }
                    )
                }
            }.onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    /**
     * Deshace una esponja ya confirmada: devuelve los gramos al batch
     * (`returnToBatch`) y limpia la declaración de las líneas. Solo tiene
     * sentido mientras el lote sigue OPEN/IN_PROGRESS.
     */
    fun removeSpongeFromGroup(companyId: String, lotId: String, lines: List<ProductionLotLine>) {
        val consumedLines = lines.filter { it.spongeConsumed && it.spongeBatchId.isNotEmpty() }
        if (consumedLines.isEmpty()) return
        val batchId = consumedLines.first().spongeBatchId
        val totalGrams = consumedLines.sumOf { it.spongeGramsUsed }
        viewModelScope.launch {
            runCatching {
                val ids = consumedLines.map { it.id }.toSet()
                val updatedLines = _uiState.value.editableLotLines.map { line ->
                    if (line.id !in ids) return@map line
                    line.copy(
                        spongeBatchId = "",
                        spongeBatchName = "",
                        spongeGramsUsed = 0.0,
                        spongeConsumed = false
                    )
                }
                preparationBatchRepository.returnToBatch(companyId, batchId, totalGrams)
                updatedLines.filter { it.id in ids }.forEach { line ->
                    productionRepository.saveLotLine(companyId, lotId, line)
                }
                _uiState.update { state ->
                    state.copy(
                        editableLotLines = updatedLines,
                        availableSpongeBatches = state.availableSpongeBatches.map { b ->
                            if (b.id == batchId) b.copy(
                                remainingWeightGrams = (b.remainingWeightGrams + totalGrams)
                                    .coerceAtMost(b.producedWeightGrams)
                            ) else b
                        }
                    )
                }
            }.onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    /**
     * Receta escalada para una **masa completa**: sale de la suma de masa de
     * todos sus productos, no de uno solo.
     *
     * Es el cambio de fondo respecto de la versión por producto: la amasada es
     * una sola. Si de la hojaldre salen 40 barcas y 30 cuadrados, hay que mojar
     * la harina de los 70, y cada producto pesa distinto en crudo.
     *
     * [extraDoughGrams] suma la masa que otros productos (de OTRA masa) necesitan
     * como relleno de esta receta (ver [extraDoughGramsForRecipe]) — el panadero
     * tiene que amasar de más aunque esas piezas no aparezcan en esta lista.
     *
     * [lines] puede venir vacío: una masa que nadie pidió directamente pero que
     * hay que amasar igual porque otro producto la lleva de relleno. En ese caso
     * [fallbackRecipeId]/[fallbackRecipeName] (el `key`/`title` del `MasaGroup`
     * sintético) son la única forma de saber de qué receta se trata.
     */
    fun buildScaledRecipeViewForGroup(
        lines: List<ProductionLotLine>,
        extraDoughGrams: Double = 0.0,
        fallbackRecipeId: String = "",
        fallbackRecipeName: String = ""
    ): ScaledRecipeView? {
        val first = lines.firstOrNull()
        val recipeId = first?.recipeId ?: fallbackRecipeId
        if (recipeId.isEmpty()) return null
        val recipe = _uiState.value.recipesById[recipeId] ?: return null
        val totalDoughGrams = lines.sumOf { doughGramsOf(it) } + extraDoughGrams
        if (totalDoughGrams <= 0.0) return null

        val components = recipe.allComponents.sortedBy { it.order }
        val sumPercentages = components.sumOf { it.percentage }
        if (sumPercentages <= 0.0 || components.none { it.isBase }) return null
        val baseWeight = totalDoughGrams * 100.0 / sumPercentages

        val spongeGrams = spongeGramsOfGroup(lines)
        val spongeBatchId = spongeBatchIdOfGroup(lines)
        val sponge: Preparation? = _uiState.value.availableSpongeBatches
            .find { it.id == spongeBatchId }
            ?.let { batch -> _uiState.value.preparationsCatalog.find { it.id == batch.preparationId } }

        val spongeContributionByIngredient = mutableMapOf<String, Double>()
        if (sponge != null && spongeGrams > 0.0 && sponge.batchWeightGrams > 0.0) {
            for (ing in sponge.ingredients) {
                val gramsInBatch = toGramsOrNull(ing.quantityPerBatch, ing.unit, ing.unitWeightGrams) ?: 0.0
                val fraction = gramsInBatch / sponge.batchWeightGrams
                spongeContributionByIngredient[ing.ingredientId] = spongeGrams * fraction
            }
        }

        val rows = components.map { c ->
            val totalGrams = (c.percentage / 100.0) * baseWeight
            val gramsFromSponge = spongeContributionByIngredient[c.ingredientId] ?: 0.0
            scaledRow(c, totalGrams, gramsFromSponge)
        }

        // Los adornos sí son por producto: se suman por preparación/insumo. Sin el
        // fallback a ingredientName, todos los adornos comprados ya hechos (nombre
        // de preparación vacío) se agrupaban bajo la misma clave "" y se mezclaban
        // en una sola fila.
        val decorations = lines
            .flatMap { line ->
                line.decorationsSnapshot.map { d -> d to (d.gramsPerUnit * effectiveQuantity(line)) }
            }
            .groupBy { it.first.preparationName.ifEmpty { it.first.ingredientName } }
            .map { (name, entries) ->
                ScaledDecoration(
                    preparationName = name,
                    gramsPerUnit = entries.first().first.gramsPerUnit,
                    totalGrams = entries.sumOf { it.second }
                )
            }

        return ScaledRecipeView(
            productName = recipe.name.ifEmpty { first?.recipeName ?: fallbackRecipeName },
            producedQuantity = lines.sumOf { effectiveQuantity(it) },
            productUnit = first?.productUnit ?: "",
            rawWeightGrams = 0.0,
            totalDoughGrams = totalDoughGrams,
            rows = rows,
            sponge = sponge?.takeIf { spongeGrams > 0.0 }
                ?.let { ScaledSpongeContribution(it.name, spongeGrams) },
            decorations = decorations
        )
    }

    /**
     * Por qué no se puede escalar esta masa, o null si todo está bien.
     *
     * Igual que [buildScaledRecipeViewForGroup], [lines] puede venir vacío para
     * una masa que solo existe como relleno de otro producto — en ese caso
     * [extraDoughGrams]/[fallbackRecipeId]/[fallbackRecipeName] son los que
     * traen la info, no las líneas.
     */
    fun diagnoseScaledRecipeForGroup(
        lines: List<ProductionLotLine>,
        extraDoughGrams: Double = 0.0,
        fallbackRecipeId: String = "",
        fallbackRecipeName: String = ""
    ): String? {
        val first = lines.firstOrNull()
        val recipeId = first?.recipeId ?: fallbackRecipeId
        val recipeName = first?.recipeName ?: fallbackRecipeName
        if (recipeId.isEmpty()) return "La masa no tiene productos."
        val recipe = _uiState.value.recipesById[recipeId]
            ?: return "No se pudo cargar la receta \"$recipeName\". Recarga la pantalla."
        if (lines.isEmpty()) {
            if (extraDoughGrams <= 0.0) return "La cantidad a producir es 0."
        } else {
            val missing = linesMissingRawWeight(lines)
            if (missing.size == lines.size) {
                return "Ninguno de estos productos tiene peso en crudo configurado, así que no se " +
                    "puede calcular cuánta masa hacen. Edítalos en Productos (campo \"Peso en " +
                    "crudo\"): " + missing.joinToString(", ") { it.productName }
            }
            if (lines.sumOf { effectiveQuantity(it) } + extraDoughGrams <= 0.0) {
                return "La cantidad a producir es 0."
            }
        }
        val components = recipe.allComponents
        if (components.isEmpty()) return "La receta \"${recipe.name}\" no tiene ingredientes."
        if (components.none { it.isBase }) {
            return "La receta \"${recipe.name}\" no tiene ningún ingrediente marcado como base (harina)."
        }
        if (components.sumOf { it.percentage } <= 0.0) {
            return "La receta \"${recipe.name}\" tiene porcentajes inválidos."
        }
        return null
    }

    /**
     * Fila de la receta escalada. Los gramos son el dato exacto; la equivalencia
     * en piezas es para el insumo que el panadero cuenta en vez de pesar (huevo).
     */
    private fun scaledRow(
        c: com.bakery.erp.model.RecipeComponent,
        totalGrams: Double,
        gramsFromSponge: Double
    ): ScaledRecipeRow {
        val finalGrams = (totalGrams - gramsFromSponge).coerceAtLeast(0.0)
        val ingredient = _uiState.value.ingredientsById[c.ingredientId]
        val countUnit = ingredient?.allowedUnits?.firstOrNull { countUnitsPer(it) != null }
        val countEquivalent = if (ingredient != null && countUnit != null)
            gramsToUnitOrNull(finalGrams, countUnit, ingredient.unitWeightGrams)
        else null
        return ScaledRecipeRow(
            ingredientId = c.ingredientId,
            ingredientName = c.ingredientName,
            percentage = c.percentage,
            totalGrams = totalGrams,
            gramsFromSponge = gramsFromSponge,
            finalGrams = finalGrams,
            isBase = c.isBase,
            countEquivalent = countEquivalent,
            countUnit = countUnit ?: ""
        )
    }

    /**
     * Catálogo de insumos en memoria. Se carga una sola vez: el VM es singleton y
     * solo se necesita para traducir peso ↔ unidades, que no cambia por lote.
     */
    private fun ensureIngredientCache(companyId: String) {
        if (companyId.isEmpty() || _uiState.value.ingredientsById.isNotEmpty()) return
        viewModelScope.launch {
            val list = runCatching { ingredientRepository.getIngredients(companyId) }
                .getOrElse { emptyList() }
            if (list.isNotEmpty()) {
                _uiState.update { st -> st.copy(ingredientsById = list.associateBy { it.id }) }
            }
        }
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
                    val gramsInBatch = toGramsOrNull(ing.quantityPerBatch, ing.unit, ing.unitWeightGrams) ?: 0.0
                    val fraction = gramsInBatch / batchWeight
                    spongeContributionByIngredient[ing.ingredientId] = line.spongeGramsUsed * fraction
                }
            }
        }

        val rows = components.map { c ->
            val totalGrams = (c.percentage / 100.0) * baseWeight
            val gramsFromSponge = spongeContributionByIngredient[c.ingredientId] ?: 0.0
            scaledRow(c, totalGrams, gramsFromSponge)
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
                    preparationName = d.preparationName.ifEmpty { d.ingredientName },
                    gramsPerUnit = d.gramsPerUnit,
                    totalGrams = d.gramsPerUnit * quantity
                )
            }
        )
    }

    /**
     * Snapshot de los adornos de un producto al armar una línea de lote.
     * Preselecciona FIFO el [PreparationBatch] para los que preparamos nosotros;
     * los insumos comprados ya hechos ([ProductDecoration.isIngredientSourced])
     * no necesitan batch — se descuentan directo del `BranchStock` al completar
     * el lote, igual que la harina.
     */
    private fun buildDecorationsSnapshot(
        product: Product?,
        decorationBatchesByPrep: Map<String, List<PreparationBatch>>
    ): List<ProductionLotDecoration> = product?.decorations.orEmpty().map { deco ->
        if (deco.isIngredientSourced) {
            ProductionLotDecoration(
                ingredientId = deco.ingredientId,
                ingredientName = deco.ingredientName,
                gramsPerUnit = deco.gramsPerUnit
            )
        } else {
            val candidate = decorationBatchesByPrep[deco.preparationId]?.firstOrNull()
            ProductionLotDecoration(
                preparationId = deco.preparationId,
                preparationName = deco.preparationName,
                gramsPerUnit = deco.gramsPerUnit,
                batchId = candidate?.id ?: "",
                batchName = candidate?.preparationName ?: ""
            )
        }
    }

    /**
     * Snapshot de [Product.doughFillings] al armar una línea de lote — mismo
     * criterio que [buildDecorationsSnapshot]: una vez creada la línea, el
     * relleno queda fijo aunque después se edite el producto.
     */
    private fun buildDoughFillingsSnapshot(product: Product?): List<DoughFilling> =
        product?.doughFillings.orEmpty()

    /**
     * Línea de lote a partir de una línea de pedido **especial** (ver
     * [ProductionOrderLine.isSpecial]).
     *
     * No hay producto de catálogo del que sacar receta ni peso: los dos vienen
     * copiados de la propia línea del pedido. De ahí para adelante la línea es
     * una más — entra a su grupo de masa, suma para el mínimo de amasada y se
     * descuenta en `completeLot` con la misma fórmula.
     *
     * Vive en un solo lugar porque son cuatro los caminos que crean líneas de
     * lote (consolidación, recomputación, reparto por turnos y pedido tardío) y
     * a uno que se le olvide el peso deja al panadero con una masa sin escalar.
     */
    private fun specialLotLine(
        source: ProductionOrderLine,
        quantity: Double,
        allocations: List<SourceOrderAllocation> = emptyList()
    ): ProductionLotLine = ProductionLotLine(
        id = "",
        productId = source.productId,
        productName = source.productName,
        productUnit = source.productUnit,
        rawWeightGrams = source.specialRawWeightGrams.takeIf { it > 0.0 },
        plannedQuantity = quantity,
        producedQuantity = quantity,
        recipeId = source.specialRecipeId,
        recipeName = source.specialRecipeName,
        isSpecial = true,
        specialPhotoUrl = source.specialPhotoUrl,
        sourceOrderAllocations = allocations
    )

    private fun consolidateOrderLines(
        allLines: List<ProductionOrderLine>,
        recipes: List<Recipe>,
        products: List<Product>,
        decorationBatchesByPrep: Map<String, List<PreparationBatch>>
    ): List<ProductionLotLine> {
        val grouped = allLines.groupBy { it.productId }
        return grouped.map { (productId, lines) ->
            // Pedido especial: la receta y el peso vienen en la propia línea, no
            // del catálogo. Su productId es único, así que este grupo tiene una
            // sola línea salvo que la misma sucursal lo haya pedido dos veces.
            val special = lines.firstOrNull { it.isSpecial }
            if (special != null) {
                return@map specialLotLine(special, lines.sumOf { it.requestedQuantity })
            }
            val product = products.find { it.id == productId }
            val recipe = recipes.find { r -> r.yields.any { it.productId == productId } }
            val decorationsSnapshot = buildDecorationsSnapshot(product, decorationBatchesByPrep)
            ProductionLotLine(
                productId = productId,
                productName = lines.first().productName,
                productUnit = lines.first().productUnit,
                rawWeightGrams = product?.rawWeightGrams,
                plannedQuantity = lines.sumOf { it.requestedQuantity },
                producedQuantity = lines.sumOf { it.requestedQuantity },
                recipeId = recipe?.id ?: "",
                recipeName = recipe?.name ?: "",
                decorationsSnapshot = decorationsSnapshot,
                doughFillingsSnapshot = buildDoughFillingsSnapshot(product)
            )
        }.sortedBy { it.productName }
    }

    // ── Mínimo de la amasadora ────────────────────────────────────────────────

    /**
     * Recetas del borrador del lote cuyo pedido no alcanza el mínimo que la
     * amasadora puede trabajar. Se evalúa sobre [draftLotLines] (antes de crear
     * el lote), que es cuando la encargada todavía puede decidir.
     */
    fun underMinimumRecipes(): List<RecipeBatchCheck> = checkRecipeBatchMinimums(
        lines = _uiState.value.draftLotLines,
        recipesById = _uiState.value.recipesById,
        rawWeightResolver = { effectiveRawWeightGrams(it) }
    ).filter { it.isUnderMinimum }

    /**
     * Igual que [underMinimumRecipes] pero para el borrador del reparto de turnos:
     * se evalúa cada turno por separado con las cantidades que le tocaron ahí — el
     * mínimo de amasada es una restricción de esa amasada puntual, no del día
     * completo repartido entre las dos.
     */
    fun underMinimumRecipesForShiftSplit(quantities: Map<String, Double>): List<RecipeBatchCheck> {
        val lines = _uiState.value.shiftSplitLines.mapNotNull { line ->
            val qty = quantities[line.productId] ?: 0.0
            if (qty <= 0.0) null else line.copy(plannedQuantity = qty, producedQuantity = qty)
        }
        return checkRecipeBatchMinimums(
            lines = lines,
            recipesById = _uiState.value.recipesById,
            rawWeightResolver = { it.rawWeightGrams }
        ).filter { it.isUnderMinimum }
    }

    /**
     * Aplica la decisión de la encargada sobre una receta bajo mínimo: sube cada
     * producto a la cantidad total que se va a hornear y anota cuánto de eso se
     * queda en la planta.
     *
     * [totalByProduct] = unidades a hornear (incluye lo pedido + el excedente).
     * [retainedByProduct] = de esas, cuántas NO se despachan.
     */
    fun applyMinimumBatchDecision(
        totalByProduct: Map<String, Double>,
        retainedByProduct: Map<String, Double>
    ) {
        _uiState.update { state ->
            state.copy(
                draftLotLines = state.draftLotLines.map { line ->
                    val total = totalByProduct[line.productId] ?: return@map line
                    line.copy(
                        plannedQuantity = total,
                        producedQuantity = total,
                        plantRetainedQuantity = retainedByProduct[line.productId] ?: 0.0
                    )
                }
            )
        }
    }

    /**
     * Resuelve una receta bajo mínimo horneando el mínimo.
     *
     * Además de subir las cantidades del borrador, sube las líneas de los pedidos
     * a los que la encargada les asignó excedente: las notas de despacho se
     * construyen desde los pedidos, así que sin esto la sucursal no recibiría el
     * pan de más que se le asignó.
     *
     * [extraByOrder] = `orderId → (productId → unidades extra)`.
     */
    fun resolveMinimumBatchByBaking(
        companyId: String,
        totalByProduct: Map<String, Double>,
        retainedByProduct: Map<String, Double>,
        extraByOrder: Map<String, Map<String, Double>>
    ) {
        applyMinimumBatchDecision(totalByProduct, retainedByProduct)
        if (extraByOrder.isEmpty()) return
        viewModelScope.launch {
            extraByOrder.forEach { (orderId, extrasByProduct) ->
                runCatching {
                    val order = productionRepository.getOrder(companyId, orderId)
                        ?: return@runCatching
                    val lines = productionRepository.getOrderLines(companyId, orderId)
                    val updated = lines.map { line ->
                        val extra = extrasByProduct[line.productId] ?: 0.0
                        if (extra > 0.0) line.copy(requestedQuantity = line.requestedQuantity + extra)
                        else line
                    }
                    productionRepository.updateOrder(companyId, order, updated)
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            error = "No se pudo actualizar el pedido con el excedente: ${err.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Anula los productos de una receta bajo mínimo: los saca del borrador del
     * lote **y** de los pedidos que los solicitaron, porque si quedaran en el
     * pedido la nota de despacho prometería un pan que nunca se horneó.
     *
     * Devuelve por callback los datos para avisar a las sucursales afectadas.
     */
    fun resolveMinimumBatchByCancelling(
        companyId: String,
        currentUser: User?,
        recipeName: String,
        productIds: Set<String>,
        affectedOrderIds: Set<String>
    ) {
        if (productIds.isEmpty()) return
        val cancelledNames = _uiState.value.draftLotLines
            .filter { it.productId in productIds }
            .map { it.productName }
        _uiState.update { state ->
            state.copy(draftLotLines = state.draftLotLines.filter { it.productId !in productIds })
        }
        viewModelScope.launch {
            val touchedOrders = mutableListOf<ProductionOrder>()
            affectedOrderIds.forEach { orderId ->
                runCatching {
                    val order = productionRepository.getOrder(companyId, orderId)
                        ?: return@runCatching
                    val lines = productionRepository.getOrderLines(companyId, orderId)
                    val remaining = lines.filter { it.productId !in productIds }
                    if (remaining.size != lines.size) {
                        productionRepository.updateOrder(companyId, order, remaining)
                        touchedOrders += order
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(error = "No se pudo quitar el producto del pedido: ${err.message}")
                    }
                }
            }

            // Avisar a cada sucursal que ese pan no le va a llegar. Es el aviso
            // más importante de todos: si nadie lo lee, la sucursal descubre el
            // faltante recibiendo el despacho.
            val productList = cancelledNames.joinToString(", ").ifEmpty { "un producto" }
            touchedOrders.groupBy { it.sellingBranchId }.forEach { (branchId, orders) ->
                if (branchId.isEmpty()) return@forEach
                notifySafely(
                    companyId,
                    AppNotification(
                        type = NotificationType.PRODUCT_CANCELLED_MIN_BATCH,
                        title = "No se va a hornear: $productList",
                        body = "La masa «$recipeName» no llegó al mínimo que la amasadora " +
                            "puede trabajar, así que ese producto se anuló de tu pedido del " +
                            formatDayForNotification(orders.first().requestedDate) +
                            ". El resto del pedido sigue igual.",
                        targetBranchIds = listOf(branchId),
                        requiredPermission = Permission.SEND_PRODUCTION_ORDER.name,
                        targetScreen = NotificationTarget.PRODUCTION_ORDER_DETAIL,
                        targetId = orders.first().id,
                        createdByUserId = currentUser?.id.orEmpty(),
                        createdByUserName = currentUser?.displayName?.ifEmpty { currentUser.email }
                            .orEmpty()
                    )
                )
            }
        }
    }

    fun dropProductsFromDraftLot(productIds: Set<String>) {
        if (productIds.isEmpty()) return
        _uiState.update { state ->
            state.copy(draftLotLines = state.draftLotLines.filter { it.productId !in productIds })
        }
    }

    /**
     * Saca de la planificación los pedidos pendientes de otras fechas.
     *
     * No los cancela ni los borra: siguen PENDING y visibles en su propia fecha.
     * Solo dejan de aparecer en este formulario, que es donde se acumulan los
     * pedidos viejos que nunca se produjeron.
     */
    fun dismissOtherDatePendingOrders(companyId: String) {
        val ids = _uiState.value.otherDatePendingOrderIds
        if (ids.isEmpty()) return
        // Se limpia de una en la UI: la escritura corre detrás y si falla se
        // recupera al recargar la pantalla.
        _uiState.update {
            it.copy(pendingOrdersInOtherDates = emptyMap(), otherDatePendingOrderIds = emptyList())
        }
        viewModelScope.launch {
            runCatching {
                productionRepository.excludeOrdersFromLotPlanning(companyId, ids)
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message ?: "No se pudieron omitir los pedidos") }
            }
        }
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

                // Avisar a cada sucursal solicitante que su pedido ya fue tomado.
                // Un aviso por sucursal (no uno por pedido) para no repetirle a la
                // misma gente si mandó varios pedidos al mismo turno.
                _uiState.value.pendingOrdersForDate
                    .groupBy { it.sellingBranchId }
                    .forEach { (branchId, branchOrders) ->
                        if (branchId.isEmpty()) return@forEach
                        notifySafely(
                            companyId,
                            AppNotification(
                                type = NotificationType.ORDER_IN_LOT,
                                title = "${producingBranch.name} tomó tu pedido",
                                body = "Tu pedido del ${formatDayForNotification(productionDate)}" +
                                    (if (shift.name.isNotBlank()) " (turno ${shift.name})" else "") +
                                    " entró al lote de producción.",
                                targetBranchIds = listOf(branchId),
                                requiredPermission = Permission.SEND_PRODUCTION_ORDER.name,
                                targetScreen = NotificationTarget.PRODUCTION_ORDER_DETAIL,
                                targetId = branchOrders.first().id,
                                createdByUserId = currentUser.id,
                                createdByUserName = currentUser.displayName.ifEmpty { currentUser.email }
                            )
                        )
                    }
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSaved = true, draftLotLines = emptyList()) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message ?: "Error al crear el lote") }
            }
        }
    }

    // ── Reparto de turnos (plantas con exactamente 2 turnos activos) ───────────

    /**
     * Carga la demanda del día completo para [producingBranchId]+[date] SIN filtrar
     * por turno (los pedidos de estas plantas llegan con `shiftId=""`) y calcula el
     * reparto inicial entre [shiftA]/[shiftB] con [computeShiftSplit]. Si ya existe
     * lote (de cualquier turno) para esta combinación, no calcula nada — la pantalla
     * debe mostrar el banner de "ya existe" en vez del editor.
     */
    fun loadShiftSplitData(
        companyId: String,
        producingBranchId: String,
        date: Long,
        shiftA: ProductionShift,
        shiftB: ProductionShift,
        products: List<Product>
    ) {
        ensureIngredientCache(companyId)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isShiftSplitLoading = true,
                    shiftSplitShiftA = shiftA,
                    shiftSplitShiftB = shiftB,
                    shiftSplitExistingLots = emptyList()
                )
            }

            val existingLots = runCatching {
                productionRepository.getActiveLotsForBranchDate(companyId, producingBranchId, date)
            }.getOrElse { emptyList() }
            if (existingLots.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        shiftSplitLines = emptyList(),
                        shiftSplitExistingLots = existingLots,
                        isShiftSplitLoading = false
                    )
                }
                return@launch
            }

            val tz = TimeZone.currentSystemDefault()
            val targetDay = Instant.fromEpochMilliseconds(date).toLocalDateTime(tz).date
            val pendingOrders = runCatching {
                productionRepository.getPendingOrdersForProducingBranch(companyId, producingBranchId)
            }.getOrElse { emptyList() }
                .filter { it.shiftId.isEmpty() }
                .filter { Instant.fromEpochMilliseconds(it.requestedDate).toLocalDateTime(tz).date == targetDay }

            val orderLinesPairs = coroutineScope {
                pendingOrders.map { order ->
                    async {
                        order.id to runCatching {
                            productionRepository.getOrderLines(companyId, order.id)
                        }.getOrElse { emptyList() }
                    }
                }.awaitAll()
            }
            val orderLinesMap = orderLinesPairs.toMap()
            val allOrderLines = orderLinesPairs.flatMap { it.second }

            val (recipesWithYields, activeBatches, preparations) = coroutineScope {
                val recipesDeferred = async {
                    runCatching { recipeRepository.getRecipesWithYields(companyId) }.getOrElse { emptyList() }
                }
                val batchesDeferred = async {
                    runCatching {
                        preparationBatchRepository.listActiveBatches(companyId, producingBranchId)
                    }.getOrElse { emptyList() }
                }
                val prepsDeferred = async {
                    runCatching { preparationRepository.getPreparations(companyId) }.getOrElse { emptyList() }
                }
                Triple(recipesDeferred.await(), batchesDeferred.await(), prepsDeferred.await())
            }
            val prepUsageById = preparations.associate { it.id to it.usage }
            val decorationBatches = activeBatches.filter {
                prepUsageById[it.preparationId] == PreparationUsage.DECORATION
            }.groupBy { it.preparationId }

            val consolidated = consolidateOrderLines(allOrderLines, recipesWithYields, products, decorationBatches)

            val recipeIds = consolidated.map { it.recipeId }.filter { it.isNotEmpty() }.distinct()
            val recipesById = coroutineScope {
                recipeIds.map { id ->
                    async {
                        runCatching { recipeRepository.getRecipe(companyId, id) }.getOrNull()?.let { id to it }
                    }
                }.awaitAll().filterNotNull().toMap()
            }

            val outcome = computeShiftSplit(consolidated, shiftA, shiftB) { it.rawWeightGrams }

            // Encargos declarados por la sucursal en su propio pedido: entran como
            // cantidades ya fijadas del primer viaje, así la encargada de planta no
            // tiene que enterarse por teléfono ni escribirlas a mano.
            val pinnedA = mutableMapOf<String, Map<String, Double>>()
            val earlyNotes = mutableMapOf<String, Map<String, String>>()
            consolidated.forEach { line ->
                val declared = orderLinesMap.mapNotNull { (orderId, lines) ->
                    val ol = lines.find { it.productId == line.productId } ?: return@mapNotNull null
                    val early = ol.earlyShiftQuantity ?: return@mapNotNull null
                    orderId to early.coerceIn(0.0, ol.requestedQuantity)
                }.toMap()
                if (declared.isNotEmpty()) pinnedA[line.productId] = declared
                val notes = orderLinesMap.mapNotNull { (orderId, lines) ->
                    val ol = lines.find { it.productId == line.productId } ?: return@mapNotNull null
                    if (ol.earlyShiftQuantity == null || ol.earlyShiftNote.isBlank()) null
                    else orderId to ol.earlyShiftNote
                }.toMap()
                if (notes.isNotEmpty()) earlyNotes[line.productId] = notes
            }

            // Cada sucursal va mitad y mitad de lo suyo, salvo que haya declarado un
            // encargo. El total de cada turno se deriva sumando — el balanceo por peso
            // de `computeShiftSplit` ya no manda sobre el número de cada sucursal
            // (daba 90/110 sobre 200 y no había forma de explicarlo); queda para
            // detectar exclusiones y para el aviso de harina por turno.
            val excludedA = shiftA.excludedProductIds.toSet()
            val excludedB = shiftB.excludedProductIds.toSet()

            // Sucursales que solo reciben en un viaje (abren tarde, cierran
            // temprano): su pedido no se reparte, va entero al viaje que pueden
            // recibir. La restricción vive en la sucursal, así que se traduce acá
            // a `orderId → viaje` una sola vez.
            val branchById = runCatching { branchRepository.getBranches(companyId) }
                .getOrElse { emptyList() }.associateBy { it.id }
            val restrictedTrip = pendingOrders.mapNotNull { order ->
                val trip = branchById[order.sellingBranchId]?.receivesOnlyInTrip ?: DISPATCH_TRIP_BOTH
                if (trip == DISPATCH_TRIP_FIRST || trip == DISPATCH_TRIP_SECOND) order.id to trip else null
            }.toMap()

            val orderAllocationA = consolidated.associate { line ->
                line.productId to defaultOrderAllocationsForShiftA(
                    orderRequested = orderRequestedFor(line.productId, orderLinesMap),
                    pinned = pinnedA[line.productId] ?: emptyMap(),
                    allowedInA = line.productId !in excludedA,
                    allowedInB = line.productId !in excludedB,
                    restrictedTrip = restrictedTrip
                )
            }

            // Sucursal restringida a un viaje que no hornea ese producto: no hay
            // reparto que cumpla las dos cosas, manda la producción y se avisa.
            val restrictionWarnings = consolidated.flatMap { line ->
                tripRestrictionConflicts(
                    orderRequested = orderRequestedFor(line.productId, orderLinesMap),
                    restrictedTrip = restrictedTrip,
                    allowedInA = line.productId !in excludedA,
                    allowedInB = line.productId !in excludedB
                ).map { orderId ->
                    val branchName = pendingOrders.find { it.id == orderId }?.sellingBranchName ?: "Una sucursal"
                    val shiftName = if (restrictedTrip[orderId] == DISPATCH_TRIP_FIRST) shiftA.name else shiftB.name
                    "$branchName solo recibe en $shiftName, pero ${line.productName} no se hornea en ese turno — " +
                        "queda en el otro viaje."
                }
            }
            val quantityA = consolidated.associate { line ->
                line.productId to (orderAllocationA[line.productId]?.values?.sum() ?: 0.0)
            }
            val quantityB = consolidated.associate { line ->
                val totalRequested = orderRequestedFor(line.productId, orderLinesMap).values.sum()
                line.productId to (totalRequested - (quantityA[line.productId] ?: 0.0)).coerceAtLeast(0.0)
            }

            _uiState.update {
                it.copy(
                    shiftSplitLines = consolidated,
                    shiftSplitQuantityA = quantityA,
                    shiftSplitQuantityB = quantityB,
                    shiftSplitProductsWithoutWeight = outcome.productsWithoutWeight,
                    shiftSplitConflictingExclusions = outcome.conflictingExclusions,
                    shiftSplitSourceOrderIds = pendingOrders.map { o -> o.id },
                    shiftSplitPendingOrders = pendingOrders,
                    shiftSplitOrderLinesMap = orderLinesMap,
                    shiftSplitRetainedA = emptyMap(),
                    shiftSplitRetainedB = emptyMap(),
                    shiftSplitOrderAllocationA = orderAllocationA,
                    shiftSplitPinnedA = pinnedA,
                    shiftSplitEarlyNotes = earlyNotes,
                    shiftSplitRestrictedTrip = restrictedTrip,
                    shiftSplitTripRestrictionWarnings = restrictionWarnings,
                    recipesById = recipesById,
                    availableDecorationBatchesByPreparation = decorationBatches,
                    preparationsCatalog = preparations,
                    isShiftSplitLoading = false
                )
            }
        }
    }

    /** `orderId → cantidad pedida` de [productId] entre todas las órdenes del reparto. */
    private fun orderRequestedFor(
        productId: String,
        orderLinesMap: Map<String, List<ProductionOrderLine>>
    ): Map<String, Double> =
        orderLinesMap.mapNotNull { (orderId, lines) ->
            lines.find { it.productId == productId }?.let { orderId to it.requestedQuantity }
        }.toMap()

    /**
     * Reajuste manual: mover la cantidad de un producto en un turno mueve la
     * diferencia al otro. El desglose por sucursal de ese producto se recalcula
     * proporcional al nuevo total — cualquier reasignación manual previa para
     * este producto ([reassignOrderAllocation]) se pierde, porque ya no aplica al
     * nuevo total.
     */
    fun rebalanceShiftSplitLine(productId: String, editedShiftIsA: Boolean, newValue: Double) {
        _uiState.update { state ->
            val (newA, newB) = rebalanceShiftSplit(
                state.shiftSplitQuantityA, state.shiftSplitQuantityB, productId, editedShiftIsA, newValue
            )
            // Lo retenido en planta por mínimo de amasada no va a ninguna sucursal,
            // así que no entra al reparto por sucursal.
            val orderedA = ((newA[productId] ?: 0.0) - (state.shiftSplitRetainedA[productId] ?: 0.0))
                .coerceAtLeast(0.0)
            // Los encargos declarados y las sucursales que solo reciben en un
            // viaje se respetan; solo se reacomodan las que no pidieron nada en
            // particular. Sin el pin de restricción, mover el total del producto
            // volvía a repartirle pan a la sucursal que ya está cerrada.
            val requested = orderRequestedFor(productId, state.shiftSplitOrderLinesMap)
            val newAllocation = rebalanceOrderAllocation(
                requested,
                orderedA,
                (state.shiftSplitPinnedA[productId] ?: emptyMap()) +
                    tripRestrictionPins(requested, state.shiftSplitRestrictedTrip)
            )
            state.copy(
                shiftSplitQuantityA = newA,
                shiftSplitQuantityB = newB,
                shiftSplitOrderAllocationA = state.shiftSplitOrderAllocationA + (productId to newAllocation)
            )
        }
    }

    /**
     * Fija cuánto del primer viaje le toca a UNA sucursal y **deriva el total del
     * turno** de la suma de todas — ver [pinOrderAllocation].
     *
     * Antes esto repartía un total fijo, así que subirle a una sucursal se lo
     * bajaba a las otras: si Democracia se llevaba 150 franceses de la mañana,
     * Almolonga veía cambiar su número sin que nadie hubiera tocado su pedido. Lo
     * correcto es que el turno de la mañana hornee más, no que Almolonga reciba
     * menos.
     */
    fun reassignOrderAllocation(productId: String, orderId: String, newValue: Double) {
        _uiState.update { state ->
            // Una sucursal que solo recibe en un viaje no se reparte a mano: el
            // límite es su horario de apertura, no una preferencia del día. Se
            // cambia en la ficha de la sucursal.
            if (orderId in state.shiftSplitRestrictedTrip) return@update state
            val requested = orderRequestedFor(productId, state.shiftSplitOrderLinesMap)
            val newAllocation = pinOrderAllocation(
                orderRequested = requested,
                currentAllocationA = state.shiftSplitOrderAllocationA[productId] ?: emptyMap(),
                orderId = orderId,
                newValue = newValue
            )
            val orderedA = newAllocation.values.sum()
            val totalRequested = requested.values.sum()
            state.copy(
                shiftSplitOrderAllocationA = state.shiftSplitOrderAllocationA + (productId to newAllocation),
                // El ajuste manual pesa igual que un encargo declarado: si después
                // se mueve el total del turno, esta sucursal no se toca.
                shiftSplitPinnedA = state.shiftSplitPinnedA +
                    (productId to ((state.shiftSplitPinnedA[productId] ?: emptyMap<String, Double>()) +
                        (orderId to (newAllocation[orderId] ?: 0.0)))),
                shiftSplitQuantityA = state.shiftSplitQuantityA +
                    (productId to orderedA + (state.shiftSplitRetainedA[productId] ?: 0.0)),
                shiftSplitQuantityB = state.shiftSplitQuantityB +
                    (productId to (totalRequested - orderedA).coerceAtLeast(0.0) +
                        (state.shiftSplitRetainedB[productId] ?: 0.0))
            )
        }
    }

    /**
     * Igual que [resolveMinimumBatchByBaking] pero para un turno del reparto de
     * dos turnos: el mínimo de amasada es una restricción de esa amasada
     * puntual, así que el excedente sube la cantidad de ESE turno únicamente —
     * el otro turno ya tiene la suya y no se toca. También sube las líneas de
     * los pedidos con excedente asignado, por la misma razón que en el flujo de
     * un turno: la nota de despacho se arma desde el pedido, no desde el lote.
     */
    fun resolveShiftSplitMinimumByBaking(
        companyId: String,
        editedShiftIsA: Boolean,
        totalByProduct: Map<String, Double>,
        retainedByProduct: Map<String, Double>,
        extraByOrder: Map<String, Map<String, Double>>
    ) {
        _uiState.update { state ->
            // El excedente ya viene atribuido a una orden puntual (la que se eligió
            // en el diálogo de mínimo) — se fija esa orden en el desglose por
            // sucursal en vez de recalcular proporcional, para no diluirlo entre
            // las demás sucursales que no pidieron el excedente.
            val updatedOrderLinesMap = state.shiftSplitOrderLinesMap.mapValues { (orderId, lines) ->
                val extras = extraByOrder[orderId] ?: return@mapValues lines
                lines.map { line ->
                    val extra = extras[line.productId] ?: 0.0
                    if (extra > 0.0) line.copy(requestedQuantity = line.requestedQuantity + extra) else line
                }
            }
            val newQuantityA = if (editedShiftIsA) state.shiftSplitQuantityA + totalByProduct else state.shiftSplitQuantityA
            // El excedente ya viene atribuido a una orden puntual, así que basta
            // SUMÁRSELO a lo que esa orden ya tenía: no hay nada que rebalancear.
            //
            // Antes esto llamaba a rebalanceOrderAllocation con solo los extras como
            // valores fijados, y eso repartía TODO el turno proporcionalmente —
            // pisando los encargos declarados por las sucursales. Con Calvario
            // pidiendo 60 de sus 100 en el primer viaje, pasar por el diálogo de
            // mínimo de amasada se los bajaba a 53 sin decir nada.
            val newAllocationA = if (!editedShiftIsA) state.shiftSplitOrderAllocationA
            else state.shiftSplitOrderAllocationA + totalByProduct.keys.associateWith { productId ->
                val current = state.shiftSplitOrderAllocationA[productId] ?: emptyMap()
                val extras = extraByOrder.mapNotNull { (orderId, byProduct) ->
                    byProduct[productId]?.let { orderId to it }
                }.toMap()
                (current.keys + extras.keys).associateWith { orderId ->
                    (current[orderId] ?: 0.0) + (extras[orderId] ?: 0.0)
                }
            }
            // Lo que el diálogo asignó queda fijado igual que un encargo: mover
            // después el total del turno no debe deshacer esta decisión.
            val newPinnedA = if (!editedShiftIsA) state.shiftSplitPinnedA
            else state.shiftSplitPinnedA + totalByProduct.keys.mapNotNull { productId ->
                val alloc = newAllocationA[productId] ?: return@mapNotNull null
                val touched = extraByOrder.filter { it.value.containsKey(productId) }.keys
                if (touched.isEmpty()) return@mapNotNull null
                productId to ((state.shiftSplitPinnedA[productId] ?: emptyMap()) +
                    touched.associateWith { alloc[it] ?: 0.0 })
            }.toMap()
            if (editedShiftIsA) {
                state.copy(
                    shiftSplitQuantityA = newQuantityA,
                    shiftSplitRetainedA = state.shiftSplitRetainedA + retainedByProduct,
                    shiftSplitOrderLinesMap = updatedOrderLinesMap,
                    shiftSplitOrderAllocationA = newAllocationA,
                    shiftSplitPinnedA = newPinnedA
                )
            } else {
                state.copy(
                    shiftSplitQuantityB = state.shiftSplitQuantityB + totalByProduct,
                    shiftSplitRetainedB = state.shiftSplitRetainedB + retainedByProduct,
                    shiftSplitOrderLinesMap = updatedOrderLinesMap,
                    shiftSplitOrderAllocationA = newAllocationA,
                    shiftSplitPinnedA = newPinnedA
                )
            }
        }
        if (extraByOrder.isEmpty()) return
        // Reparto ya actualizado por el bloque de arriba: es de donde sale a qué
        // viaje pertenece cada unidad del excedente.
        val allocationAfter = _uiState.value.shiftSplitOrderAllocationA
        viewModelScope.launch {
            extraByOrder.forEach { (orderId, extrasByProduct) ->
                runCatching {
                    val order = productionRepository.getOrder(companyId, orderId) ?: return@runCatching
                    val lines = productionRepository.getOrderLines(companyId, orderId)
                    val updated = lines.map { line ->
                        val extra = extrasByProduct[line.productId] ?: 0.0
                        if (extra <= 0.0) return@map line
                        val newQuantity = line.requestedQuantity + extra
                        // El excedente del mínimo de amasada pertenece al turno que no
                        // llegaba, así que hay que dejarlo escrito en el pedido: sin
                        // esto el pedido decía "110, de las cuales 60 temprano" y un
                        // recálculo posterior devolvía las 10 al otro turno, dejando esa
                        // amasada otra vez bajo el mínimo sin avisar.
                        if (!editedShiftIsA) line.copy(requestedQuantity = newQuantity)
                        else line.copy(
                            requestedQuantity = newQuantity,
                            earlyShiftQuantity = (
                                allocationAfter[line.productId]?.get(orderId)
                                    ?: ((line.earlyShiftQuantity ?: 0.0) + extra)
                                ).coerceIn(0.0, newQuantity)
                        )
                    }
                    productionRepository.updateOrder(companyId, order, updated)
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(error = "No se pudo actualizar el pedido con el excedente: ${err.message}")
                    }
                }
            }
        }
    }

    /**
     * Igual que [resolveMinimumBatchByCancelling] pero para el reparto de dos
     * turnos: la masa no se hornea hoy en ninguno de los dos turnos (no solo en
     * el que no llegaba al mínimo), así que sale de ambos mapas de cantidades y
     * de los pedidos que la solicitaron.
     */
    fun resolveShiftSplitMinimumByCancelling(
        companyId: String,
        productIds: Set<String>,
        affectedOrderIds: Set<String>
    ) {
        if (productIds.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                shiftSplitLines = state.shiftSplitLines.filter { it.productId !in productIds },
                shiftSplitQuantityA = state.shiftSplitQuantityA - productIds,
                shiftSplitQuantityB = state.shiftSplitQuantityB - productIds,
                shiftSplitRetainedA = state.shiftSplitRetainedA - productIds,
                shiftSplitRetainedB = state.shiftSplitRetainedB - productIds,
                shiftSplitOrderAllocationA = state.shiftSplitOrderAllocationA - productIds
            )
        }
        viewModelScope.launch {
            affectedOrderIds.forEach { orderId ->
                runCatching {
                    val order = productionRepository.getOrder(companyId, orderId) ?: return@runCatching
                    val lines = productionRepository.getOrderLines(companyId, orderId)
                    val remaining = lines.filter { it.productId !in productIds }
                    if (remaining.size != lines.size) {
                        productionRepository.updateOrder(companyId, order, remaining)
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(error = "No se pudo actualizar el pedido: ${err.message}")
                    }
                }
            }
        }
    }

    /**
     * Crea los dos lotes (uno por turno) a partir del reparto confirmado. Ambos
     * comparten `sourceOrderIds` — el mismo pedido alimenta a los dos turnos. Un
     * turno que quedó sin ningún producto asignado no genera lote (igual que
     * [saveLot] no crea un lote sin líneas).
     */
    fun saveShiftSplitLots(
        companyId: String,
        currentUser: User,
        producingBranch: Branch,
        shiftA: ProductionShift,
        shiftB: ProductionShift,
        productionDate: Long,
        notes: String?
    ) {
        viewModelScope.launch {
            val state = _uiState.value
            val sourceOrderIds = state.shiftSplitSourceOrderIds
            if (state.shiftSplitLines.isEmpty() || sourceOrderIds.isEmpty()) {
                _uiState.update { it.copy(error = "No hay pedidos para repartir") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val branchNameByOrderId = state.shiftSplitPendingOrders.associate { it.id to it.sellingBranchName }

                // De quién son las unidades de este producto en este turno — necesario
                // para que createDispatchNotesForLot arme la nota de despacho de cada
                // sucursal con SU porción, no con el pedido completo (ver
                // ProductionLotLine.sourceOrderAllocations).
                fun sourceAllocationsFor(productId: String, isShiftA: Boolean): List<SourceOrderAllocation> {
                    val requested = orderRequestedFor(productId, state.shiftSplitOrderLinesMap)
                    val allocationA = state.shiftSplitOrderAllocationA[productId] ?: emptyMap()
                    return requested.mapNotNull { (orderId, requestedQty) ->
                        val qtyA = allocationA[orderId] ?: 0.0
                        val qty = if (isShiftA) qtyA else (requestedQty - qtyA).coerceAtLeast(0.0)
                        if (qty <= 0.0) null else SourceOrderAllocation(
                            orderId = orderId,
                            branchName = branchNameByOrderId[orderId] ?: "",
                            quantity = qty
                        )
                    }
                }

                fun linesFor(
                    quantities: Map<String, Double>, retained: Map<String, Double>, isShiftA: Boolean
                ): List<ProductionLotLine> =
                    state.shiftSplitLines.mapNotNull { line ->
                        val qty = quantities[line.productId] ?: 0.0
                        if (qty <= 0.0) null else line.copy(
                            plannedQuantity = qty,
                            producedQuantity = qty,
                            plantRetainedQuantity = retained[line.productId] ?: 0.0,
                            sourceOrderAllocations = sourceAllocationsFor(line.productId, isShiftA)
                        )
                    }

                val linesA = linesFor(state.shiftSplitQuantityA, state.shiftSplitRetainedA, isShiftA = true)
                val linesB = linesFor(state.shiftSplitQuantityB, state.shiftSplitRetainedB, isShiftA = false)

                val baseLot = ProductionLot(
                    producingBranchId = producingBranch.id,
                    producingBranchName = producingBranch.name,
                    productionDate = productionDate,
                    status = ProductionLotStatus.OPEN,
                    notes = notes?.trim()?.ifEmpty { null },
                    createdByUserId = currentUser.id,
                    createdByUserName = currentUser.displayName.ifEmpty { currentUser.email },
                    sourceOrderIds = sourceOrderIds,
                    isShiftSplit = true
                )
                if (linesA.isNotEmpty()) {
                    productionRepository.saveLot(
                        companyId, baseLot.copy(shiftId = shiftA.id, shiftName = shiftA.name), linesA
                    )
                }
                if (linesB.isNotEmpty()) {
                    productionRepository.saveLot(
                        companyId, baseLot.copy(shiftId = shiftB.id, shiftName = shiftB.name), linesB
                    )
                }

                // Avisar una vez por sucursal solicitante — desde su punto de vista es
                // un solo pedido, aunque la planta lo haya repartido en dos turnos.
                val orders = sourceOrderIds.mapNotNull { id ->
                    runCatching { productionRepository.getOrder(companyId, id) }.getOrNull()
                }
                orders.groupBy { it.sellingBranchId }.forEach { (branchId, branchOrders) ->
                    if (branchId.isEmpty()) return@forEach
                    notifySafely(
                        companyId,
                        AppNotification(
                            type = NotificationType.ORDER_IN_LOT,
                            title = "${producingBranch.name} tomó tu pedido",
                            body = "Tu pedido del ${formatDayForNotification(productionDate)} entró al lote de producción.",
                            targetBranchIds = listOf(branchId),
                            requiredPermission = Permission.SEND_PRODUCTION_ORDER.name,
                            targetScreen = NotificationTarget.PRODUCTION_ORDER_DETAIL,
                            targetId = branchOrders.first().id,
                            createdByUserId = currentUser.id,
                            createdByUserName = currentUser.displayName.ifEmpty { currentUser.email }
                        )
                    )
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSaved = true,
                        shiftSplitLines = emptyList(),
                        shiftSplitQuantityA = emptyMap(),
                        shiftSplitQuantityB = emptyMap(),
                        shiftSplitRetainedA = emptyMap(),
                        shiftSplitRetainedB = emptyMap(),
                        shiftSplitPendingOrders = emptyList(),
                        shiftSplitOrderLinesMap = emptyMap()
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message ?: "Error al crear los lotes") }
            }
        }
    }

    // ── Detalle de lote ───────────────────────────────────────────────────────

    fun loadLotDetail(companyId: String, lotId: String) {
        ensureIngredientCache(companyId)
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
                            // Ver comentario equivalente en observeLotDetail.
                            (it.shiftId == lot.shiftId || (lot.shiftId.isNotEmpty() && it.shiftId.isEmpty())) &&
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
                val specialByProduct = orderLines.filter { it.isSpecial }.associateBy { it.productId }
                val missingProductIds = orderLines.map { it.productId }.filter { pid ->
                    pid !in specialByProduct &&
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
                // Si el lote actual viene de un reparto por turnos, el pedido tardío
                // también necesita su propia entrada — si no, createDispatchNotesForLot
                // no sabría que esta cantidad es de esta sucursal y la trataría como 0
                // en este turno. El `any { ... }` cubre los lotes repartidos anteriores
                // al flag `isShiftSplit`.
                val isSplitAwareLot = lot.isShiftSplit ||
                    _uiState.value.selectedLotLines.any { it.sourceOrderAllocations.isNotEmpty() }
                val newLines = orderLines.map { ol ->
                    val lateAllocations = if (isSplitAwareLot) listOf(
                        SourceOrderAllocation(
                            orderId = order.id,
                            branchName = order.sellingBranchName,
                            quantity = ol.requestedQuantity
                        )
                    ) else emptyList()
                    if (ol.isSpecial) {
                        return@map specialLotLine(ol, ol.requestedQuantity, lateAllocations)
                    }
                    val product = productsById[ol.productId]
                    val recipe = recipesById.values.find { r ->
                        r.yields.any { it.productId == ol.productId }
                    }
                    val decorationsSnapshot = buildDecorationsSnapshot(product, decorationBatchesByPrep)
                    ProductionLotLine(
                        productId = ol.productId,
                        productName = ol.productName,
                        productUnit = ol.productUnit,
                        rawWeightGrams = product?.rawWeightGrams,
                        plannedQuantity = ol.requestedQuantity,
                        producedQuantity = ol.requestedQuantity,
                        recipeId = recipe?.id ?: "",
                        recipeName = recipe?.name ?: "",
                        decorationsSnapshot = decorationsSnapshot,
                        doughFillingsSnapshot = buildDoughFillingsSnapshot(product),
                        sourceOrderAllocations = lateAllocations
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

    fun startLot(companyId: String, lotId: String, currentUser: User?) {
        viewModelScope.launch {
            runCatching {
                // Persistir los cambios de esponja hechos en la pantalla de detalle antes
                // de marcar el lote como IN_PROGRESS.
                _uiState.value.editableLotLines.forEach { line ->
                    runCatching { productionRepository.saveLotLine(companyId, lotId, line) }
                }
                productionRepository.updateLotStatus(companyId, lotId, ProductionLotStatus.IN_PROGRESS)

                // Crear las notas de despacho DRAFT desde ya, para que despacho pueda empezar
                // a contar mientras los panaderos siguen produciendo. Idempotente por
                // (lotId, sourceOrderId) — no duplica al completar más adelante.
                val lot = _uiState.value.selectedLot?.takeIf { it.id == lotId }
                    ?: productionRepository.getLot(companyId, lotId)
                if (lot != null && currentUser != null) {
                    runCatching {
                        val lotLines = productionRepository.getLotLines(companyId, lotId)
                        dispatchRepository.createDispatchNotesForLot(
                            companyId,
                            lot.copy(status = ProductionLotStatus.IN_PROGRESS),
                            lotLines,
                            currentUser
                        )
                    }

                    // Avisar a las sucursales que su pan ya está en el horno.
                    val sourceOrders = lot.sourceOrderIds.mapNotNull { orderId ->
                        runCatching { productionRepository.getOrder(companyId, orderId) }.getOrNull()
                    }
                    sourceOrders
                        .groupBy { it.sellingBranchId }
                        .forEach { (branchId, branchOrders) ->
                            if (branchId.isEmpty()) return@forEach
                            notifySafely(
                                companyId,
                                AppNotification(
                                    type = NotificationType.ORDER_IN_PRODUCTION,
                                    title = "${lot.producingBranchName} empezó a producir",
                                    body = "Tu pedido del ${formatDayForNotification(lot.productionDate)}" +
                                        (if (lot.shiftName.isNotBlank()) " (turno ${lot.shiftName})" else "") +
                                        " ya está en producción.",
                                    targetBranchIds = listOf(branchId),
                                    requiredPermission = Permission.SEND_PRODUCTION_ORDER.name,
                                    targetScreen = NotificationTarget.PRODUCTION_ORDER_DETAIL,
                                    targetId = branchOrders.first().id,
                                    createdByUserId = currentUser.id,
                                    createdByUserName = currentUser.displayName.ifEmpty { currentUser.email }
                                )
                            )
                        }
                }
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
        /** Línea especial original por `productId` — trae la masa y el peso que
         *  el catálogo no puede dar (ver [specialLotLine]). */
        val specialByProduct = mutableMapOf<String, ProductionOrderLine>()
        for (orderId in lot.sourceOrderIds) {
            val orderLines = runCatching {
                productionRepository.getOrderLines(companyId, orderId)
            }.getOrElse { emptyList() }
            for (ol in orderLines) {
                byProduct[ol.productId] = (byProduct[ol.productId] ?: 0.0) + ol.requestedQuantity
                nameUnitByProduct.getOrPut(ol.productId) { ol.productName to ol.productUnit }
                if (ol.isSpecial) specialByProduct.getOrPut(ol.productId) { ol }
            }
        }

        val existing = productionRepository.getLotLines(companyId, lotId).associateBy { it.productId }

        // Actualizar o eliminar líneas existentes. El excedente retenido por
        // mínimo de amasada NO sale de los pedidos, así que se suma aparte y
        // sobrevive a la recomputación (si no, al editar un pedido se perdería
        // la decisión que ya tomó la encargada).
        for ((productId, line) in existing) {
            val orderedQty = byProduct[productId] ?: 0.0
            val retained = line.plantRetainedQuantity
            val target = orderedQty + retained
            if (target <= 0.0) {
                runCatching { productionRepository.deleteLotLine(companyId, lotId, line.id) }
            } else if (target != line.plannedQuantity) {
                productionRepository.saveLotLine(
                    companyId, lotId,
                    line.copy(plannedQuantity = target, producedQuantity = target)
                )
            }
        }

        // Crear líneas nuevas para productos que no estaban antes en el lote.
        val newProductIds = byProduct.keys - existing.keys
        if (newProductIds.isNotEmpty()) {
            val productsById = _uiState.value.productsById
            val recipesById = _uiState.value.recipesById.toMutableMap()
            // Los especiales traen su masa en la línea: buscarles yields en el
            // catálogo sería una consulta N+1 garantizada a no encontrar nada.
            val missing = newProductIds.filter { pid ->
                pid !in specialByProduct &&
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
                val special = specialByProduct[productId]
                if (special != null) {
                    productionRepository.saveLotLine(companyId, lotId, specialLotLine(special, qty))
                    continue
                }
                val product = productsById[productId]
                val recipe = recipesById.values.find { r ->
                    r.yields.any { it.productId == productId }
                }
                val (productName, productUnit) = nameUnitByProduct[productId] ?: ("" to "")
                val decorations = buildDecorationsSnapshot(product, decorationBatchesByPrep)
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
                        decorationsSnapshot = decorations,
                        doughFillingsSnapshot = buildDoughFillingsSnapshot(product)
                    )
                )
            }
        }
    }

    /**
     * Variante de [recomputeLotLinesFromSources] para un pedido que alimenta DOS
     * lotes (planta de 2 turnos). No toca la función original — cero riesgo de
     * regresión para plantas de 1 turno — y en vez de resetear al reparto
     * automático inicial, atribuye el delta de cada producto de forma que
     * preserve el reparto manual que ya hizo la encargada:
     *
     *  - Sin cambio en el total pedido → no se toca la línea de ningún turno.
     *  - Aumento → va al turno con menos peso de masa en este momento (aprox.
     *    liviano/pesado, no una re-optimización global).
     *  - Disminución → se resta proporcional a como ya estaba repartido.
     *  - Producto nuevo (no estaba en ningún turno) → 50/50, salvo exclusión.
     *  - `plantRetainedQuantity` de cada turno se preserva intacta y aparte del
     *    reparto — igual que en la variante de un solo turno.
     */
    private suspend fun recomputeSplitLotLinesFromSources(
        companyId: String,
        lot1: ProductionLot,
        lot2: ProductionLot
    ): Result<Unit> = runCatching {
        if (lot1.status != ProductionLotStatus.OPEN && lot2.status != ProductionLotStatus.OPEN) {
            return@runCatching
        }

        val shiftsForBranch = runCatching {
            productionRepository.getShiftsForBranch(companyId, lot1.producingBranchId)
        }.getOrElse { emptyList() }
        // `earlyShiftQuantity` habla del PRIMER viaje, así que el lote "A" de este
        // cálculo tiene que ser el del primer viaje y no el que venga primero en la
        // lista. `getShiftsForBranch` ya los devuelve ordenados por `dispatchOrder`.
        fun tripOrder(lot: ProductionLot): Int =
            shiftsForBranch.indexOfFirst { it.id == lot.shiftId }
                .let { if (it < 0) Int.MAX_VALUE else it }
        val (lotA, lotB) = if (tripOrder(lot2) < tripOrder(lot1)) lot2 to lot1 else lot1 to lot2

        val openA = lotA.status == ProductionLotStatus.OPEN
        val openB = lotB.status == ProductionLotStatus.OPEN

        val sourceOrderIds = (lotA.sourceOrderIds + lotB.sourceOrderIds).distinct()
        val byProduct = mutableMapOf<String, Double>()
        val nameUnitByProduct = mutableMapOf<String, Pair<String, String>>()
        // productId → (orderId → cantidad pedida ahora). Es la base para reconstruir
        // el desglose por sucursal de cada turno: sin esto las notas de despacho
        // quedarían con las cantidades de antes de la edición.
        val requestedByProductAndOrder = mutableMapOf<String, MutableMap<String, Double>>()
        /** `productId → (orderId → unidades declaradas para el primer viaje)`. */
        val declaredEarlyByProductAndOrder = mutableMapOf<String, MutableMap<String, Double>>()
        val branchNameByOrderId = mutableMapOf<String, String>()
        /** Línea especial original por `productId` — ver [specialLotLine]. */
        val specialByProduct = mutableMapOf<String, ProductionOrderLine>()
        /** `orderId → viaje único` de las sucursales que solo reciben en uno. */
        val restrictedTrip = mutableMapOf<String, Int>()
        val branchById = runCatching { branchRepository.getBranches(companyId) }
            .getOrElse { emptyList() }.associateBy { it.id }
        for (orderId in sourceOrderIds) {
            val orderLines = runCatching {
                productionRepository.getOrderLines(companyId, orderId)
            }.getOrElse { emptyList() }
            runCatching { productionRepository.getOrder(companyId, orderId) }
                .getOrNull()?.let { order ->
                    branchNameByOrderId[orderId] = order.sellingBranchName
                    // El horario de la sucursal no depende del pedido: editar uno
                    // no puede empezar a mandarle pan al viaje en que está cerrada.
                    val trip = branchById[order.sellingBranchId]?.receivesOnlyInTrip ?: DISPATCH_TRIP_BOTH
                    if (trip == DISPATCH_TRIP_FIRST || trip == DISPATCH_TRIP_SECOND) {
                        restrictedTrip[orderId] = trip
                    }
                }
            for (ol in orderLines) {
                byProduct[ol.productId] = (byProduct[ol.productId] ?: 0.0) + ol.requestedQuantity
                nameUnitByProduct.getOrPut(ol.productId) { ol.productName to ol.productUnit }
                if (ol.isSpecial) specialByProduct.getOrPut(ol.productId) { ol }
                if (ol.requestedQuantity > 0.0) {
                    requestedByProductAndOrder.getOrPut(ol.productId) { mutableMapOf() }[orderId] =
                        ol.requestedQuantity
                }
                // El encargo que declaró la sucursal manda sobre cualquier reajuste
                // automático: editar un pedido no puede moverle a otra sucursal las
                // unidades que pidió para el primer viaje.
                ol.earlyShiftQuantity?.let { early ->
                    declaredEarlyByProductAndOrder.getOrPut(ol.productId) { mutableMapOf() }[orderId] =
                        early.coerceIn(0.0, ol.requestedQuantity)
                }
            }
        }

        val existingA = productionRepository.getLotLines(companyId, lotA.id).associateBy { it.productId }
        val existingB = productionRepository.getLotLines(companyId, lotB.id).associateBy { it.productId }

        // Recetas de productos nuevos que aún no están en cache — igual que la
        // variante de un solo turno.
        val newProductIds = byProduct.keys - existingA.keys - existingB.keys
        val recipesById = _uiState.value.recipesById.toMutableMap()
        if (newProductIds.isNotEmpty()) {
            val missing = newProductIds.filter { pid ->
                pid !in specialByProduct &&
                    recipesById.values.none { r -> r.yields.any { y -> y.productId == pid } }
            }
            if (missing.isNotEmpty()) {
                val all = runCatching { recipeRepository.getRecipesWithYields(companyId) }.getOrElse { emptyList() }
                all.filter { r -> r.yields.any { it.productId in missing } }
                    .forEach { lite ->
                        runCatching { recipeRepository.getRecipe(companyId, lite.id) }
                            .getOrNull()?.let { full -> recipesById[full.id] = full }
                    }
            }
        }

        val excludedA = shiftsForBranch.find { it.id == lotA.shiftId }?.excludedProductIds.orEmpty().toSet()
        val excludedB = shiftsForBranch.find { it.id == lotB.shiftId }?.excludedProductIds.orEmpty().toSet()

        val productsById = _uiState.value.productsById
        fun rawWeightOf(productId: String, snapshot: Double?): Double =
            snapshot ?: productsById[productId]?.rawWeightGrams ?: 0.0

        var currentWeightA = existingA.values.sumOf { it.plannedQuantity * rawWeightOf(it.productId, it.rawWeightGrams) }
        var currentWeightB = existingB.values.sumOf { it.plannedQuantity * rawWeightOf(it.productId, it.rawWeightGrams) }

        val decorationBatchesByPrep = _uiState.value.availableDecorationBatchesByPreparation

        for (productId in (byProduct.keys + existingA.keys + existingB.keys)) {
            val existingLineA = existingA[productId]
            val existingLineB = existingB[productId]
            val retainedA = existingLineA?.plantRetainedQuantity ?: 0.0
            val retainedB = existingLineB?.plantRetainedQuantity ?: 0.0
            val orderedTotal = byProduct[productId] ?: 0.0
            val oldOrderedA = ((existingLineA?.plannedQuantity ?: 0.0) - retainedA).coerceAtLeast(0.0)
            val oldOrderedB = ((existingLineB?.plannedQuantity ?: 0.0) - retainedB).coerceAtLeast(0.0)
            val oldOrderedTotal = oldOrderedA + oldOrderedB

            val (orderedA, orderedB) = when {
                orderedTotal <= 0.0 -> 0.0 to 0.0
                // Un turno que ya está en producción tiene su pan en el horno: su
                // parte queda congelada y el turno todavía abierto absorbe todo el
                // cambio. Sin esto, editar un pedido recalculaba los dos turnos pero
                // solo escribía en el abierto, y las cantidades dejaban de cuadrar.
                !openA -> oldOrderedA to (orderedTotal - oldOrderedA).coerceAtLeast(0.0)
                !openB -> (orderedTotal - oldOrderedB).coerceAtLeast(0.0) to oldOrderedB
                productId in excludedA && productId !in excludedB -> 0.0 to orderedTotal
                productId in excludedB && productId !in excludedA -> orderedTotal to 0.0
                // Producto nuevo: se reparte POR SUCURSAL (mitad y mitad de lo de
                // cada una, salvo encargo o restricción de viaje) y el total del
                // turno se deriva sumando — igual que el reparto inicial. Partir
                // el total a la mitad le habría dado pan del primer viaje a una
                // sucursal que solo recibe en el segundo.
                oldOrderedTotal <= 0.0 -> defaultOrderAllocationsForShiftA(
                    orderRequested = requestedByProductAndOrder[productId].orEmpty(),
                    pinned = declaredEarlyByProductAndOrder[productId].orEmpty(),
                    allowedInA = true,
                    allowedInB = true,
                    restrictedTrip = restrictedTrip
                ).values.sum().let { it to (orderedTotal - it).coerceAtLeast(0.0) }
                orderedTotal == oldOrderedTotal -> oldOrderedA to oldOrderedB
                orderedTotal > oldOrderedTotal -> {
                    val delta = orderedTotal - oldOrderedTotal
                    if (currentWeightA <= currentWeightB) (oldOrderedA + delta) to oldOrderedB
                    else oldOrderedA to (oldOrderedB + delta)
                }
                else -> {
                    val delta = oldOrderedTotal - orderedTotal
                    val newA = (oldOrderedA - delta * (oldOrderedA / oldOrderedTotal)).coerceAtLeast(0.0)
                    newA to (orderedTotal - newA).coerceAtLeast(0.0)
                }
            }
            // Desglose por sucursal de cada turno: decide DE QUIÉN son las unidades,
            // que es lo que arma la nota de despacho de cada sucursal.
            //
            // Con un turno ya en producción su desglose es un hecho consumado: el
            // otro turno se deriva restándolo, sucursal por sucursal. Ni un encargo
            // declarado puede mover pan que ya está en el horno — intentarlo dejaba
            // el turno abierto cuadrando contra un número que el cerrado nunca llegó
            // a guardar.
            val requestedNow = requestedByProductAndOrder[productId].orEmpty()
            val (allocationsA, allocationsB) = when {
                !openA -> {
                    val fixedA = existingLineA?.sourceOrderAllocations.orEmpty()
                    fixedA to derivedAllocations(requestedNow, fixedA, branchNameByOrderId)
                }
                !openB -> {
                    val fixedB = existingLineB?.sourceOrderAllocations.orEmpty()
                    derivedAllocations(requestedNow, fixedB, branchNameByOrderId) to fixedB
                }
                else -> splitAllocationsFor(
                    requestedNow = requestedNow,
                    previousAllocationsA = existingLineA?.sourceOrderAllocations.orEmpty(),
                    previousAllocationsB = existingLineB?.sourceOrderAllocations.orEmpty(),
                    shiftTotalA = orderedA,
                    declaredEarly = declaredEarlyByProductAndOrder[productId].orEmpty(),
                    restrictedTrip = restrictedTrip,
                    branchNameByOrderId = branchNameByOrderId
                )
            }

            // Con los dos turnos abiertos el desglose manda sobre el total: un
            // encargo declarado o una sucursal que solo recibe en un viaje pueden
            // mover unidades de un turno al otro, y la línea del lote tiene que
            // cuadrar con la suma de lo que se le despacha a cada sucursal — si no,
            // el turno hornea una cantidad y las notas prometen otra. El desglose
            // conserva por construcción (A + B = lo pedido), así que derivar de él
            // no puede perder ni inventar unidades.
            val finalOrderedA = if (openA && openB) allocationsA.sumOf { it.quantity } else orderedA
            val finalOrderedB = if (openA && openB) allocationsB.sumOf { it.quantity } else orderedB

            val targetA = finalOrderedA + retainedA
            val targetB = finalOrderedB + retainedB

            currentWeightA += (targetA - (existingLineA?.plannedQuantity ?: 0.0)) *
                rawWeightOf(productId, existingLineA?.rawWeightGrams)
            currentWeightB += (targetB - (existingLineB?.plannedQuantity ?: 0.0)) *
                rawWeightOf(productId, existingLineB?.rawWeightGrams)

            if (openA) {
                applySplitLotLine(
                    companyId, lotA.id, productId, targetA, existingLineA, allocationsA,
                    nameUnitByProduct, specialByProduct[productId],
                    recipesById, productsById, decorationBatchesByPrep
                )
            }
            if (openB) {
                applySplitLotLine(
                    companyId, lotB.id, productId, targetB, existingLineB, allocationsB,
                    nameUnitByProduct, specialByProduct[productId],
                    recipesById, productsById, decorationBatchesByPrep
                )
            }
        }
    }

    /**
     * Desglose del turno que sigue abierto cuando el otro ya está en producción:
     * a cada sucursal le toca lo que pidió menos lo que el turno cerrado ya le
     * está horneando. Conserva por construcción — los dos turnos siempre suman lo
     * pedido — y no puede contradecir lo que ya está en el horno.
     */
    private fun derivedAllocations(
        requestedNow: Map<String, Double>,
        fixedOther: List<SourceOrderAllocation>,
        branchNameByOrderId: Map<String, String>
    ): List<SourceOrderAllocation> {
        val fixedByOrder = fixedOther.associate { it.orderId to it.quantity }
        return requestedNow.mapNotNull { (orderId, requested) ->
            val qty = (requested - (fixedByOrder[orderId] ?: 0.0)).coerceAtLeast(0.0)
            if (qty <= 0.0) null
            else SourceOrderAllocation(
                orderId = orderId,
                branchName = branchNameByOrderId[orderId]
                    ?: fixedOther.find { it.orderId == orderId }?.branchName ?: "",
                quantity = qty
            )
        }
    }

    /**
     * Envoltorio de [splitOrderAllocations] que traduce entre las listas de
     * [SourceOrderAllocation] que guardan las líneas (con el nombre de sucursal
     * denormalizado para que el despacho no tenga que resolverlo) y los mapas
     * `orderId → cantidad` que usa la lógica pura del reparto.
     */
    private fun splitAllocationsFor(
        requestedNow: Map<String, Double>,
        previousAllocationsA: List<SourceOrderAllocation>,
        previousAllocationsB: List<SourceOrderAllocation>,
        shiftTotalA: Double,
        declaredEarly: Map<String, Double>,
        restrictedTrip: Map<String, Int>,
        branchNameByOrderId: Map<String, String>
    ): Pair<List<SourceOrderAllocation>, List<SourceOrderAllocation>> {
        val (mapA, mapB) = splitOrderAllocations(
            requestedNow = requestedNow,
            previousA = previousAllocationsA.associate { it.orderId to it.quantity },
            previousB = previousAllocationsB.associate { it.orderId to it.quantity },
            shiftTotalA = shiftTotalA,
            declaredEarly = declaredEarly,
            restrictedTrip = restrictedTrip
        )

        fun nameOf(orderId: String) = branchNameByOrderId[orderId]
            ?: previousAllocationsA.find { it.orderId == orderId }?.branchName
            ?: previousAllocationsB.find { it.orderId == orderId }?.branchName
            ?: ""

        return mapA.map { (orderId, qty) -> SourceOrderAllocation(orderId, nameOf(orderId), qty) } to
            mapB.map { (orderId, qty) -> SourceOrderAllocation(orderId, nameOf(orderId), qty) }
    }

    private suspend fun applySplitLotLine(
        companyId: String,
        lotId: String,
        productId: String,
        targetQty: Double,
        existing: ProductionLotLine?,
        allocations: List<SourceOrderAllocation>,
        nameUnitByProduct: Map<String, Pair<String, String>>,
        /** Línea de pedido especial de este producto, si lo es — trae la masa y
         *  el peso que el catálogo no tiene. */
        special: ProductionOrderLine?,
        recipesById: Map<String, Recipe>,
        productsById: Map<String, Product>,
        decorationBatchesByPrep: Map<String, List<PreparationBatch>>
    ) {
        if (targetQty <= 0.0) {
            if (existing != null) runCatching { productionRepository.deleteLotLine(companyId, lotId, existing.id) }
            return
        }
        if (existing != null) {
            // El desglose por sucursal también se persiste cuando cambió solo él
            // (misma cantidad total en el turno, distinto reparto entre sucursales):
            // es el dato del que sale la nota de despacho de cada una.
            if (targetQty != existing.plannedQuantity || allocations != existing.sourceOrderAllocations) {
                productionRepository.saveLotLine(
                    companyId, lotId,
                    existing.copy(
                        plannedQuantity = targetQty,
                        producedQuantity = targetQty,
                        sourceOrderAllocations = allocations
                    )
                )
            }
            return
        }
        if (special != null) {
            productionRepository.saveLotLine(
                companyId, lotId, specialLotLine(special, targetQty, allocations)
            )
            return
        }
        val (productName, productUnit) = nameUnitByProduct[productId] ?: ("" to "")
        val product = productsById[productId]
        val recipe = recipesById.values.find { r -> r.yields.any { it.productId == productId } }
        val decorations = buildDecorationsSnapshot(product, decorationBatchesByPrep)
        productionRepository.saveLotLine(
            companyId, lotId,
            ProductionLotLine(
                id = "",
                productId = productId,
                productName = productName,
                productUnit = productUnit,
                rawWeightGrams = product?.rawWeightGrams,
                plannedQuantity = targetQty,
                producedQuantity = targetQty,
                recipeId = recipe?.id ?: "",
                recipeName = recipe?.name ?: "",
                decorationsSnapshot = decorations,
                doughFillingsSnapshot = buildDoughFillingsSnapshot(product),
                // Sin esto la línea nueva no tiene desglose y `createDispatchNotesForLot`
                // la despacha como 0 a todas las sucursales: el pan sale del horno y no
                // aparece en ninguna nota.
                sourceOrderAllocations = allocations
            )
        )
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

                // 2. Marcar pedidos fuente como CONFIRMADOS — pero solo si ningún lote
                //    hermano (mismo producingBranch+día, otro turno) que también los
                //    referencie sigue OPEN/IN_PROGRESS. Un pedido de planta de 2 turnos
                //    no está realmente terminado hasta que ambos turnos completaron.
                val siblingLots = runCatching {
                    productionRepository.getActiveLotsForBranchDate(
                        companyId, lot.producingBranchId, lot.productionDate
                    )
                }.getOrElse { emptyList() }.filter { it.id != lot.id }
                lot.sourceOrderIds.forEach { orderId ->
                    val siblingStillOpen = siblingLots.any {
                        orderId in it.sourceOrderIds &&
                            (it.status == ProductionLotStatus.OPEN || it.status == ProductionLotStatus.IN_PROGRESS)
                    }
                    if (!siblingStillOpen) {
                        runCatching {
                            productionRepository.updateOrderStatus(companyId, orderId, ProductionOrderStatus.CONFIRMED)
                        }
                    }
                }

                // 3. Descontar insumos del stock + consumir batches de esponja/adorno
                val warnings = mutableListOf<String>()
                val activeLines = lines.filter { it.producedQuantity > 0 }
                // Incluye las recetas de doughFillingsSnapshot (Pan de Capa → Suave):
                // esa masa se descuenta como si fuera un Pan Suave más, así que su
                // receta también hay que cargarla aunque no tenga línea propia en el lote.
                val recipeIds = (
                    activeLines.filter { it.recipeId.isNotEmpty() }.map { it.recipeId } +
                        activeLines.flatMap { it.doughFillingsSnapshot }.map { it.recipeId }
                    ).distinct()
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

                // 3a. Agregar consumo total por insumo en TODAS las líneas. Antes
                //     hacíamos N×M round trips (N=líneas, M=insumos por receta). Ahora
                //     se compacta en 1 lectura de stocks + (≤ M únicos) escrituras +
                //     1 movimiento Kardex por insumo único. Reduce drásticamente el
                //     tiempo de cierre del lote.
                val aggregatedGramsByIngredient = HashMap<String, Double>()
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
                    for ((ingredientId, grams) in usage) {
                        if (grams <= 0.0) continue
                        aggregatedGramsByIngredient[ingredientId] =
                            (aggregatedGramsByIngredient[ingredientId] ?: 0.0) + grams
                    }
                }

                // Adornos/rellenos comprados ya hechos (relleno de higo, queso crema…):
                // se suman al MISMO agregado que la receta, en su propio recorrido — a
                // diferencia del consumo de receta, esto no depende de que la línea
                // tenga receta/peso crudo resueltos (un producto sin receta puede llevar
                // igual un relleno comprado).
                for (line in activeLines) {
                    for (deco in line.decorationsSnapshot) {
                        if (!deco.isIngredientSourced || deco.gramsPerUnit <= 0.0) continue
                        val grams = deco.gramsPerUnit * line.producedQuantity
                        if (grams <= 0.0) continue
                        aggregatedGramsByIngredient[deco.ingredientId] =
                            (aggregatedGramsByIngredient[deco.ingredientId] ?: 0.0) + grams
                    }
                }

                // Rellenos de masa cruda de OTRA receta (Pan de Capa → bolita de Suave,
                // ver DoughFilling): es literalmente masa de esa receta, así que se
                // escala y se suma al MISMO agregado — como si fuera un producto más
                // de esa receta, aunque no tenga línea propia en el lote. No depende de
                // que la línea "anfitriona" (Pan de Capa) tenga su propia receta resuelta:
                // son dos masas independientes.
                for (line in activeLines) {
                    for (filling in line.doughFillingsSnapshot) {
                        if (filling.recipeId.isEmpty() || filling.rawWeightGrams <= 0.0) continue
                        val fillingRecipe = fullRecipeMap[filling.recipeId]
                        if (fillingRecipe == null) {
                            warnings.add(
                                "${line.productName}: no se pudo cargar la receta de relleno " +
                                    "\"${filling.recipeName}\" — ese consumo no se descontó"
                            )
                            continue
                        }
                        val components = fillingRecipe.allComponents
                        val sumPercentages = components.sumOf { it.percentage }
                        if (components.none { it.isBase } || sumPercentages <= 0.0) continue
                        val totalRawGrams = line.producedQuantity * filling.rawWeightGrams
                        val baseWeight = totalRawGrams * 100.0 / sumPercentages
                        for (component in components) {
                            val grams = (component.percentage / 100.0) * baseWeight
                            if (grams <= 0.0) continue
                            aggregatedGramsByIngredient[component.ingredientId] =
                                (aggregatedGramsByIngredient[component.ingredientId] ?: 0.0) + grams
                        }
                    }
                }

                // 3b. Leer todos los stocks de la planta en UNA sola consulta.
                val stockMap = runCatching {
                    ingredientRepository.getBranchStocksForBranch(companyId, lot.producingBranchId)
                }.getOrElse { emptyMap() }

                // 3c. Aplicar descuento + Kardex en paralelo por insumo.
                coroutineScope {
                    aggregatedGramsByIngredient.forEach { (ingredientId, totalGrams) ->
                        val ingredient = ingredientMap[ingredientId] ?: return@forEach
                        val baseUnit = ingredient.allowedUnits.firstOrNull() ?: "gr"
                        // La receta calcula en gramos; el stock vive en la unidad del
                        // insumo. Para el huevo eso significa volver a unidades — sin
                        // esta conversión se restaban 300 huevos en vez de 6.
                        val inBaseUnit = gramsToUnitOrNull(totalGrams, baseUnit, ingredient.unitWeightGrams)
                        if (inBaseUnit == null) {
                            warnings.add(
                                "${ingredient.name}: no se puede pasar de peso a \"$baseUnit\" " +
                                    "— definí el peso de una unidad en Insumos. Stock no descontado."
                            )
                            return@forEach
                        }
                        val delta = -inBaseUnit
                        val prevStock = stockMap[ingredientId] ?: 0.0
                        launch {
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

                    // 3d. Consumo de batches de esponja y adornos en paralelo. Cada batch
                    //     es un documento distinto, no hay race condition entre líneas.
                    for (line in activeLines) {
                        // !spongeConsumed: si ya se descontó al confirmar en el detalle del
                        // lote (ver confirmSpongeForGroup), no se vuelve a descontar acá.
                        if (line.spongeGramsUsed > 0.0 && line.spongeBatchId.isNotEmpty() && !line.spongeConsumed) {
                            launch {
                                runCatching {
                                    preparationBatchRepository.consumeFromBatch(
                                        companyId, line.spongeBatchId, line.spongeGramsUsed
                                    )
                                }.onFailure { warnings.add("${line.productName}: ${it.message}") }
                            }
                        }
                        for (deco in line.decorationsSnapshot) {
                            // Los insumos comprados ya hechos se descontaron arriba junto
                            // con la receta (aggregatedGramsByIngredient) — acá solo van
                            // los que preparamos nosotros, que sí viven en un batch.
                            if (deco.isIngredientSourced || deco.gramsPerUnit <= 0.0) continue
                            val totalGrams = deco.gramsPerUnit * line.producedQuantity
                            if (totalGrams <= 0.0) continue
                            if (deco.batchId.isEmpty()) {
                                // Antes esto fallaba en silencio: se producía el pan y el
                                // relleno nunca se descontaba, sin ninguna pista de por qué
                                // el inventario no cuadraba. Hay que producir un lote de esa
                                // preparación antes de cerrar el lote de este producto.
                                warnings.add(
                                    "${line.productName} · ${deco.preparationName}: sin lote " +
                                        "disponible — no se descontó"
                                )
                                continue
                            }
                            launch {
                                runCatching {
                                    preparationBatchRepository.consumeFromBatch(companyId, deco.batchId, totalGrams)
                                }.onFailure {
                                    warnings.add("${line.productName} · ${deco.preparationName}: ${it.message}")
                                }
                            }
                        }
                    }
                }

                // 4. Marcar stock como descontado (para detectar fallos parciales)
                runCatching { productionRepository.markStockDeducted(companyId, lot.id) }

                // 4b. Excedente retenido: unidades que se hornearon para alcanzar
                //     el mínimo de la amasadora y que la encargada NO repartió a
                //     ninguna sucursal. No van en ninguna nota de despacho (esas
                //     salen de los pedidos fuente), así que sin este paso el pan
                //     desaparecería de los libros. Entra al terminado de la planta.
                activeLines.filter { it.plantRetainedQuantity > 0.0 }.forEach { line ->
                    runCatching {
                        finishedStockRepository.applyMovement(
                            companyId,
                            FinishedStockMovement(
                                productId = line.productId,
                                productName = line.productName,
                                branchId = lot.producingBranchId,
                                branchName = lot.producingBranchName,
                                type = FinishedMovementType.AJUSTE,
                                quantity = line.plantRetainedQuantity,
                                referenceType = FinishedMovementReference.ADJUSTMENT,
                                referenceId = lot.id,
                                reason = "Excedente por mínimo de amasada (${line.recipeName})",
                                createdByUserId = currentUser.id,
                                createdByUserName = currentUser.displayName.ifEmpty { currentUser.email },
                                createdAt = now
                            )
                        )
                    }.onFailure {
                        warnings.add(
                            "${line.productName}: no se pudo ingresar el excedente " +
                                "retenido al stock de la planta — ${it.message}"
                        )
                    }
                }

                // 5. Crear notas de despacho DRAFT (una por sucursal solicitante).
                //    Idempotente: si el lote se completa dos veces (raro), no duplica.
                runCatching {
                    dispatchRepository.createDispatchNotesForLot(
                        companyId,
                        lot.copy(status = ProductionLotStatus.COMPLETED),
                        activeLines,
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

    /** "lunes 12/08" — corto, para el cuerpo de las notificaciones. */
    private fun formatDayForNotification(millis: Long): String = runCatching {
        val tz = TimeZone.currentSystemDefault()
        val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date
        val day = isoWeekdayName(isoWeekdayOf(millis, tz))
        "$day ${date.dayOfMonth.toString().padStart(2, '0')}/" +
            date.monthNumber.toString().padStart(2, '0')
    }.getOrElse { "" }

    fun clearSaved() { _uiState.update { it.copy(isSaved = false) } }
    fun clearCompleted() { _uiState.update { it.copy(isCompleted = false, stockWarnings = emptyList()) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }

    fun resetOrderForm() {
        _uiState.update {
            it.copy(
                draftOrderLines = emptyList(),
                shiftsForSelectedProducingBranch = emptyList(),
                pendingOrderConflict = null,
                suggestionPatternId = "",
                suggestionApplied = false,
                suggestionSampleCount = 0,
                expectedQuantityByProduct = emptyMap(),
                rainForecastApplied = false,
                rainProbabilityPercent = 0,
                rainExtraUnits = 0,
                rainExtraUnitsByProduct = emptyMap(),
                rainForecastKey = "",
                rainPeriods = emptyList(),
                isSuggestionLoading = false,
                isSavingSpecialLine = false,
                isSaved = false,
                error = null
            )
        }
    }

    fun clearDraftOrderLines() {
        _uiState.update {
            it.copy(
                draftOrderLines = emptyList(),
                // Al cambiar de planta/combinación hay que volver a sugerir.
                suggestionPatternId = "",
                suggestionApplied = false,
                suggestionSampleCount = 0,
                expectedQuantityByProduct = emptyMap(),
                // El % de lluvia NO se resetea acá: depende de sucursal+fecha,
                // no de la planta/turno que se acaba de limpiar — refreshRainForecast
                // ya lo mantiene al día por su cuenta.
                rainForecastApplied = false,
                rainExtraUnits = 0,
                rainExtraUnitsByProduct = emptyMap()
            )
        }
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

            // Con shiftId="" (planta de 2 turnos, reparto automático) un lote real
            // nunca tiene shiftId vacío — getActiveLotForBranchDateShift jamás lo
            // encontraría aunque ya existan los dos lotes. getActiveLotsForBranchDate
            // no filtra por turno, así que sirve para ambos casos: para plantas de 0/1
            // turno, `shiftId` es el único valor posible y el resultado es el mismo de
            // siempre; para plantas de 2 turnos, detecta que YA se repartió aunque los
            // lotes tengan turnos reales.
            val lots = runCatching {
                productionRepository.getActiveLotsForBranchDate(companyId, producingBranchId, date)
            }.getOrElse { emptyList() }
            val relevantLots = if (shiftId.isEmpty()) lots else lots.filter { it.shiftId == shiftId }

            _uiState.update {
                it.copy(
                    consolidatedOrders = orders,
                    consolidatedLinesMap = linesMap,
                    lotForConsolidatedView = relevantLots.firstOrNull(),
                    isConsolidatedLoading = false
                )
            }
        }
    }

    /**
     * Persiste el orden en que la planta [producingBranchId] quiere ver los
     * productos en el consolidado. Se guarda en el documento de la sucursal para
     * que todos sus dispositivos vean la misma secuencia. Lista vacía = volver a
     * orden alfabético.
     */
    fun saveProductSortOrder(
        companyId: String,
        producingBranchId: String,
        productIds: List<String>
    ) {
        if (companyId.isEmpty() || producingBranchId.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                branchRepository.updateProductSortOrder(companyId, producingBranchId, productIds)
            }.onSuccess {
                // Reflejar el cambio de inmediato sin releer la colección.
                _uiState.update { state ->
                    state.copy(
                        producingBranches = state.producingBranches.map {
                            if (it.id == producingBranchId) it.copy(productSortOrder = productIds)
                            else it
                        }
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message ?: "No se pudo guardar el orden") }
            }
        }
    }

    /**
     * Persiste el orden en que el panadero de [producingBranchId] quiere ver la
     * orden de producción. Es una lista **aparte** de la del consolidado: el
     * horno tiene su propia secuencia y no tiene por qué coincidir con el orden
     * en que la encargada revisa los pedidos.
     */
    fun saveProductionSortOrder(
        companyId: String,
        producingBranchId: String,
        productIds: List<String>
    ) {
        if (companyId.isEmpty() || producingBranchId.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                branchRepository.updateProductionSortOrder(companyId, producingBranchId, productIds)
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        lotBranch = state.lotBranch
                            ?.takeIf { it.id == producingBranchId }
                            ?.copy(productionSortOrder = productIds)
                            ?: state.lotBranch,
                        producingBranches = state.producingBranches.map {
                            if (it.id == producingBranchId) it.copy(productionSortOrder = productIds)
                            else it
                        }
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message ?: "No se pudo guardar el orden") }
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
                    val ingredientGramsInBatch =
                        toGramsOrNull(ing.quantityPerBatch, ing.unit, ing.unitWeightGrams) ?: 0.0
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
