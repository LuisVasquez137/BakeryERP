package com.bakery.erp.presentation.reports.branchsupply

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.DispatchRepository
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.domain.SaleRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.BranchSupplyReport
import com.bakery.erp.model.DispatchNote
import com.bakery.erp.model.ProductionOrder
import com.bakery.erp.model.ProductionOrderStatus
import com.bakery.erp.model.User
import com.bakery.erp.model.buildBranchSupplyReport
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.presentation.reports.shared.ReportPeriod
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class BranchSupplyReportUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Default: el día. La pregunta típica es "¿me llegó lo de hoy?". */
    val period: ReportPeriod = ReportPeriod.DAY,
    val anchorDate: LocalDate = LocalDate(2026, 1, 1),
    val branches: List<Branch> = emptyList(),
    val selectedBranchId: String? = null,
    /** Un reporte por planta productora: cada una despacha por su lado, con sus turnos. */
    val plants: List<PlantSupply> = emptyList(),
    val selectedPlantId: String? = null
) {
    val selectedPlant: PlantSupply?
        get() = plants.firstOrNull { it.plantId == selectedPlantId } ?: plants.firstOrNull()
}

/**
 * Lo que una planta le mandó a la sucursal. Se separa por planta porque cada una tiene
 * sus turnos y sus viajes: mezclarlas en una tabla juntaba columnas de turnos que no
 * tienen nada que ver entre sí y hacía la lista interminable.
 */
data class PlantSupply(
    val plantId: String,
    val plantName: String,
    val report: BranchSupplyReport,
    /** Pedidos del período a esta planta (no cancelados). */
    val orderCount: Int
)

/** Lo que el reporte necesita de un conjunto de pedidos, ya leído de Firestore. */
internal data class BranchSupplyData(
    val plants: List<PlantSupply>
)

/**
 * Lee pedidos, notas y turnos de las plantas y arma el reporte. Lo usan el reporte
 * por período y la sección "Seguimiento" del detalle de un pedido: la misma lectura,
 * para que las dos pantallas no puedan decir cosas distintas del mismo pedido.
 */
internal suspend fun loadBranchSupplyData(
    companyId: String,
    orders: List<ProductionOrder>,
    productionRepository: ProductionRepository,
    dispatchRepository: DispatchRepository,
    soldByProduct: Map<String, Double>?
): BranchSupplyData = coroutineScope {
    val live = orders.filter { it.status != ProductionOrderStatus.CANCELLED }
    if (live.isEmpty()) return@coroutineScope BranchSupplyData(emptyList())

    val linesDeferred = live.map { o -> async { o.id to productionRepository.getOrderLines(companyId, o.id) } }
    // Una consulta por pedido (por `sourceOrderId`, con índice de igualdad) en vez de
    // traer la colección entera de notas: un día son 1–2 pedidos, un mes ~30.
    val notesDeferred = live.map { o ->
        async {
            dispatchRepository.getNotesForOrder(companyId, o.id).map { header ->
                async { dispatchRepository.getNote(companyId, header.id) ?: header }
            }.awaitAll()
        }
    }
    val plantIds = live.map { it.producingBranchId }.distinct()
    val shiftsDeferred = plantIds.map { id ->
        async { id to runCatching { productionRepository.getShiftsForBranch(companyId, id) }.getOrDefault(emptyList()) }
    }

    val orderLines = linesDeferred.awaitAll().toMap()
    val notes: List<DispatchNote> = notesDeferred.awaitAll().flatten()
    val shiftsByPlant = shiftsDeferred.awaitAll().toMap()

    val activeShiftCount = shiftsByPlant.mapValues { (_, shifts) -> shifts.count { it.active } }
    val shiftOrder = shiftsByPlant.values.flatten().associate { it.id to it.dispatchOrder }
    val ordersById = live.associateBy { it.id }

    val plants = live.groupBy { it.producingBranchId }.map { (plantId, plantOrders) ->
        val ids = plantOrders.map { it.id }.toSet()
        PlantSupply(
            plantId = plantId,
            plantName = plantOrders.first().producingBranchName.ifBlank { "Planta" },
            report = buildBranchSupplyReport(
                // Solo los pedidos de esta planta; las notas se filtran solas por `sourceOrderId`.
                orderLines = orderLines.filterKeys { it in ids },
                notes = notes,
                soldByProduct = soldByProduct,
                shiftOrder = shiftOrder,
                expectedShiftsForOrder = { orderId ->
                    val order = ordersById[orderId]
                    // Pedido sin turno a una planta de dos turnos = se reparte en dos notas.
                    if (order != null && order.shiftId.isEmpty() && (activeShiftCount[order.producingBranchId] ?: 0) == 2) 2 else 1
                }
            ),
            orderCount = plantOrders.size
        )
    }.sortedBy { it.plantName }
    BranchSupplyData(plants)
}

class BranchSupplyReportViewModel(
    private val productionRepository: ProductionRepository,
    private val dispatchRepository: DispatchRepository,
    private val saleRepository: SaleRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private val _uiState = MutableStateFlow(
        BranchSupplyReportUiState(anchorDate = Clock.System.now().toLocalDateTime(tz).date)
    )
    val uiState: StateFlow<BranchSupplyReportUiState> = _uiState.asStateFlow()

    private var loadedForUser: String? = null

    fun ensureLoadedFor(user: User) {
        if (loadedForUser == user.id) return
        loadedForUser = user.id
        viewModelScope.launch {
            // Solo sucursales que venden: una planta pura no le pide pan a nadie.
            val all = runCatching { branchRepository.getBranches(user.orgId) }.getOrDefault(emptyList())
                .filter { it.active && user.canAccessBranch(it.id) }
                .filter { it.capabilities.isEmpty() || BranchCapability.CAN_SELL in it.capabilities }
                .sortedBy { it.name }
            // Una sucursal a la vez: el reporte contesta "¿qué le llegó a ESTA sucursal?",
            // y sumar varias mezcla faltantes de una con sobrantes de otra.
            _uiState.update { it.copy(branches = all, selectedBranchId = it.selectedBranchId ?: all.firstOrNull()?.id) }
            reload(user)
        }
    }

    fun setPeriod(user: User, period: ReportPeriod) {
        _uiState.update { it.copy(period = period) }
        reload(user)
    }

    fun setBranch(user: User, branchId: String?) {
        if (branchId == null) return
        _uiState.update { it.copy(selectedBranchId = branchId) }
        reload(user)
    }

    fun selectPlant(plantId: String) {
        _uiState.update { it.copy(selectedPlantId = plantId) }
    }

    fun next(user: User) {
        _uiState.update { it.copy(anchorDate = shiftAnchor(it.anchorDate, it.period, +1)) }
        reload(user)
    }

    fun prev(user: User) {
        _uiState.update { it.copy(anchorDate = shiftAnchor(it.anchorDate, it.period, -1)) }
        reload(user)
    }

    fun reload(user: User) {
        val branchId = _uiState.value.selectedBranchId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val s = _uiState.value
                val (from, until) = rangeFor(s.anchorDate, s.period)
                coroutineScope {
                    val sales = async {
                        runCatching {
                            saleRepository.aggregateSalesInRange(user.orgId, listOf(branchId), from, until)
                                .unitsByProduct
                        }.getOrNull()
                    }
                    val orders = productionRepository.getOrdersForSellingBranchInRange(
                        user.orgId, branchId, from, until
                    )
                    loadBranchSupplyData(
                        companyId = user.orgId,
                        orders = orders,
                        productionRepository = productionRepository,
                        dispatchRepository = dispatchRepository,
                        soldByProduct = sales.await()
                    )
                }
            }.onSuccess { data ->
                _uiState.update {
                    // Se conserva la planta elegida al cambiar de día, si ese día también le pidió.
                    val keep = it.selectedPlantId?.takeIf { id -> data.plants.any { p -> p.plantId == id } }
                    it.copy(isLoading = false, plants = data.plants, selectedPlantId = keep ?: data.plants.firstOrNull()?.plantId)
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun shiftAnchor(date: LocalDate, period: ReportPeriod, delta: Int): LocalDate =
        when (period) {
            ReportPeriod.DAY   -> if (delta > 0) date.plus(DatePeriod(days = 1)) else date.minus(DatePeriod(days = 1))
            ReportPeriod.WEEK  -> if (delta > 0) date.plus(DatePeriod(days = 7)) else date.minus(DatePeriod(days = 7))
            ReportPeriod.MONTH -> if (delta > 0) date.plus(DatePeriod(months = 1)) else date.minus(DatePeriod(months = 1))
        }

    private fun rangeFor(date: LocalDate, period: ReportPeriod): Pair<Long, Long> {
        val start: LocalDate
        val endExclusive: LocalDate
        when (period) {
            ReportPeriod.DAY   -> { start = date; endExclusive = date.plus(DatePeriod(days = 1)) }
            ReportPeriod.WEEK  -> {
                start = date.minus(DatePeriod(days = 6))
                endExclusive = date.plus(DatePeriod(days = 1))
            }
            ReportPeriod.MONTH -> {
                start = LocalDate(date.year, date.monthNumber, 1)
                endExclusive = start.plus(DatePeriod(months = 1))
            }
        }
        return start.atStartOfDayIn(tz).toEpochMilliseconds() to
            endExclusive.atStartOfDayIn(tz).toEpochMilliseconds()
    }
}
