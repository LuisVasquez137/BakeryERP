package com.bakery.erp.presentation.reports.production

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.DispatchRepository
import com.bakery.erp.domain.PlantIssueRepository
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.domain.report.DispatchSummary
import com.bakery.erp.domain.report.ProductionSummary
import com.bakery.erp.model.Branch
import com.bakery.erp.model.PlantIssue
import com.bakery.erp.model.PlantIssueIngredientSummary
import com.bakery.erp.model.summarizePlantIssues
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.presentation.reports.shared.ReportPeriod
import kotlin.time.Clock
import kotlinx.coroutines.async
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

data class ProductionReportUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val period: ReportPeriod = ReportPeriod.MONTH,
    val anchorDate: LocalDate = LocalDate(2026, 1, 1),
    val branches: List<Branch> = emptyList(),
    val selectedBranchId: String? = null,
    val production: ProductionSummary = ProductionSummary(),
    val dispatch: DispatchSummary = DispatchSummary(),
    /**
     * Material que salió de bodega sin pasar por una receta (ver `PlantIssue`). Va en
     * este reporte y no solo en el suyo porque es aquí donde se compara un turno con
     * otro: dos turnos que hornean lo mismo y gastan el doble de harina de mesa es
     * información de producción, no de inventario.
     */
    val plantIssues: List<PlantIssue> = emptyList(),
    val plantIssueSummaries: List<PlantIssueIngredientSummary> = emptyList()
)

class ProductionReportViewModel(
    private val productionRepository: ProductionRepository,
    private val dispatchRepository: DispatchRepository,
    private val branchRepository: BranchRepository,
    private val plantIssueRepository: PlantIssueRepository
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private val _uiState = MutableStateFlow(
        ProductionReportUiState(anchorDate = Clock.System.now().toLocalDateTime(tz).date)
    )
    val uiState: StateFlow<ProductionReportUiState> = _uiState.asStateFlow()

    private var loadedForUser: String? = null

    fun ensureLoadedFor(user: User) {
        if (loadedForUser == user.id) return
        loadedForUser = user.id
        viewModelScope.launch {
            val all = branchRepository.getBranches(user.orgId)
                .filter { user.canAccessBranch(it.id) }
            _uiState.update { it.copy(branches = all) }
            reload(user)
        }
    }

    fun setPeriod(user: User, period: ReportPeriod) {
        _uiState.update { it.copy(period = period) }; reload(user)
    }

    fun setBranch(user: User, branchId: String?) {
        _uiState.update { it.copy(selectedBranchId = branchId) }; reload(user)
    }

    fun next(user: User) {
        _uiState.update { it.copy(anchorDate = shiftAnchor(it.anchorDate, it.period, +1)) }; reload(user)
    }

    fun prev(user: User) {
        _uiState.update { it.copy(anchorDate = shiftAnchor(it.anchorDate, it.period, -1)) }; reload(user)
    }

    fun reload(user: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                coroutineScope {
                    val s = _uiState.value
                    val range = rangeFor(s.anchorDate, s.period)
                    val branchIds = s.selectedBranchId?.let { listOf(it) }
                        ?: s.branches.map { it.id }
                    val prodAsync = async {
                        productionRepository.aggregateProductionInRange(
                            companyId = user.orgId,
                            producingBranchIds = branchIds,
                            fromMillis = range.first,
                            untilMillis = range.second
                        )
                    }
                    val dispAsync = async {
                        dispatchRepository.aggregateDispatchInRange(
                            companyId = user.orgId,
                            fromMillis = range.first,
                            untilMillis = range.second
                        )
                    }
                    // Nunca tumba el reporte: el consumo de planta es una sección más,
                    // y que falte su lectura no puede dejar sin "planeado vs producido"
                    // a quien vino a ver eso.
                    val issuesAsync = async {
                        runCatching {
                            plantIssueRepository.getIssuesInRange(
                                companyId = user.orgId,
                                branchIds = branchIds,
                                fromMillis = range.first,
                                untilMillis = range.second
                            )
                        }.getOrElse { emptyList() }
                    }
                    Triple(prodAsync.await(), dispAsync.await(), issuesAsync.await())
                }
            }.onSuccess { (prod, disp, issues) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        production = prod,
                        dispatch = disp,
                        plantIssues = issues,
                        plantIssueSummaries = summarizePlantIssues(issues)
                    )
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
