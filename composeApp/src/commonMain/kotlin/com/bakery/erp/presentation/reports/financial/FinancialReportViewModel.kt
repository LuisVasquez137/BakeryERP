package com.bakery.erp.presentation.reports.financial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.BreadCountRepository
import com.bakery.erp.domain.ExpenseRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.PayrollRepository
import com.bakery.erp.domain.SaleRepository
import com.bakery.erp.domain.report.ExpenseSummary
import com.bakery.erp.domain.report.MermaSummary
import com.bakery.erp.domain.report.PayrollSummary
import com.bakery.erp.domain.report.SalesSummary
import com.bakery.erp.domain.report.StockMovementSummary
import com.bakery.erp.model.Branch
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
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
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class FinancialReportUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val anchorMonth: LocalDate = LocalDate(2026, 1, 1),
    val branches: List<Branch> = emptyList(),
    val sales: SalesSummary = SalesSummary(),
    val expenses: ExpenseSummary = ExpenseSummary(),
    val payroll: PayrollSummary = PayrollSummary(),
    val merma: MermaSummary = MermaSummary(),
    val stock: StockMovementSummary = StockMovementSummary()
) {
    val operatingProfit: Double
        get() = sales.totalRevenue - expenses.total - payroll.totalEmployerCost - merma.totalValue
    val marginPct: Double
        get() = if (sales.totalRevenue > 0) operatingProfit / sales.totalRevenue * 100.0 else 0.0
}

class FinancialReportViewModel(
    private val saleRepository: SaleRepository,
    private val expenseRepository: ExpenseRepository,
    private val payrollRepository: PayrollRepository,
    private val finishedStockRepository: FinishedStockRepository,
    private val breadCountRepository: BreadCountRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private val _uiState = MutableStateFlow(
        FinancialReportUiState(
            anchorMonth = Clock.System.now().toLocalDateTime(tz).date.let {
                LocalDate(it.year, it.monthNumber, 1)
            }
        )
    )
    val uiState: StateFlow<FinancialReportUiState> = _uiState.asStateFlow()

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

    fun next(user: User) {
        _uiState.update { it.copy(anchorMonth = it.anchorMonth.plus(DatePeriod(months = 1))) }
        reload(user)
    }

    fun prev(user: User) {
        _uiState.update {
            val ym = it.anchorMonth
            val prev = if (ym.monthNumber == 1) LocalDate(ym.year - 1, 12, 1)
                       else LocalDate(ym.year, ym.monthNumber - 1, 1)
            it.copy(anchorMonth = prev)
        }
        reload(user)
    }

    fun reload(user: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                coroutineScope {
                    val s = _uiState.value
                    val month = s.anchorMonth
                    val start = month.atStartOfDayIn(tz).toEpochMilliseconds()
                    val endExclusive = month.plus(DatePeriod(months = 1))
                        .atStartOfDayIn(tz).toEpochMilliseconds()
                    val period = "${month.year}-${month.monthNumber.toString().padStart(2, '0')}"
                    val branchIds = s.branches.map { it.id }

                    val salesAsync = async {
                        saleRepository.aggregateSalesInRange(user.orgId, branchIds, start, endExclusive)
                    }
                    val expAsync = async { expenseRepository.aggregateExpensesForPeriod(user.orgId, period) }
                    val payAsync = async { payrollRepository.aggregatePayrollForPeriod(user.orgId, start, endExclusive) }
                    val stockAsync = async {
                        finishedStockRepository.aggregateMovementsInRange(user.orgId, "", start, endExclusive)
                    }
                    val mermaAsync = async { breadCountRepository.aggregateMermasForMonth(user.orgId, "", period) }

                    Quintuple(
                        salesAsync.await(),
                        expAsync.await(),
                        payAsync.await(),
                        stockAsync.await(),
                        mermaAsync.await()
                    )
                }
            }.onSuccess { (sales, exp, pay, stock, merma) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sales = sales,
                        expenses = exp,
                        payroll = pay,
                        stock = stock,
                        merma = merma
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}

private data class Quintuple<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
