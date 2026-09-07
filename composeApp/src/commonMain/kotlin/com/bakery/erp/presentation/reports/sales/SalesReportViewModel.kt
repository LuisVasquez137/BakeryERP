package com.bakery.erp.presentation.reports.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.SaleRepository
import com.bakery.erp.domain.report.SalesSummary
import com.bakery.erp.model.Branch
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.presentation.reports.shared.ReportPeriod
import kotlin.time.Clock
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

data class SalesReportUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val period: ReportPeriod = ReportPeriod.MONTH,
    val anchorDate: LocalDate = LocalDate(2026, 1, 1),
    val branches: List<Branch> = emptyList(),
    val selectedBranchId: String? = null,
    val summary: SalesSummary = SalesSummary()
)

class SalesReportViewModel(
    private val saleRepository: SaleRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private val _uiState = MutableStateFlow(
        SalesReportUiState(anchorDate = Clock.System.now().toLocalDateTime(tz).date)
    )
    val uiState: StateFlow<SalesReportUiState> = _uiState.asStateFlow()

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
        _uiState.update { it.copy(period = period) }
        reload(user)
    }

    fun setBranch(user: User, branchId: String?) {
        _uiState.update { it.copy(selectedBranchId = branchId) }
        reload(user)
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val s = _uiState.value
                val range = rangeFor(s.anchorDate, s.period)
                val branchIds = s.selectedBranchId?.let { listOf(it) }
                    ?: s.branches.map { it.id }
                saleRepository.aggregateSalesInRange(
                    companyId = user.orgId,
                    branchIds = branchIds,
                    fromMillis = range.first,
                    untilMillis = range.second
                )
            }.onSuccess { summary ->
                _uiState.update { it.copy(isLoading = false, summary = summary) }
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
                // semana: 7 días terminando en anchorDate inclusive
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
