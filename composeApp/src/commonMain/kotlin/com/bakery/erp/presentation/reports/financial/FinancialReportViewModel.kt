package com.bakery.erp.presentation.reports.financial

import com.bakery.erp.model.hasPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.BreadCountRepository
import com.bakery.erp.domain.ExpenseRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.PayrollRepository
import com.bakery.erp.domain.PurchaseRepository
import com.bakery.erp.domain.SaleRepository
import com.bakery.erp.domain.report.ExpenseSummary
import com.bakery.erp.domain.report.MermaSummary
import com.bakery.erp.domain.report.PayrollSummary
import com.bakery.erp.domain.report.PurchaseSummary
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
    val stock: StockMovementSummary = StockMovementSummary(),
    val purchases: PurchaseSummary = PurchaseSummary()
) {
    /**
     * Utilidad contable del mes. **Las compras de inventario no van acá a propósito**: la
     * harina es un activo hasta que se vende, y restarla al comprarla haría que un mes en el
     * que se llenó la bodega saliera en pérdida y el siguiente en ganancia inventada.
     */
    val operatingProfit: Double
        get() = sales.totalRevenue - expenses.total - payroll.totalEmployerCost - merma.totalValue -
            // Lo sacado del mostrador con la caja abierta ya no llega al conteo como faltante.
            stock.counterDamagedValue - stock.courtesyValue
    val marginPct: Double
        get() = if (sales.totalRevenue > 0) operatingProfit / sales.totalRevenue * 100.0 else 0.0

    /**
     * Lo que de verdad salió de la caja, que es la otra pregunta —"¿en qué se me fue el
     * mes?"— y la que el reporte no contestaba.
     *
     * Se restan las compras **pagadas** (no las que quedaron a crédito) y, de los gastos,
     * solo los que NO vienen de una compra: los suministros y los servicios ya están adentro
     * del monto de su factura, y contarlos otra vez acá los duplicaría.
     */
    val cashOut: Double
        get() = purchases.paidTotal + expenses.standalone + payroll.totalEmployerCost

    val netCash: Double get() = sales.totalRevenue - cashOut
}

class FinancialReportViewModel(
    private val saleRepository: SaleRepository,
    private val expenseRepository: ExpenseRepository,
    private val payrollRepository: PayrollRepository,
    private val purchaseRepository: PurchaseRepository,
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
                    // La nómina es de quien ve salarios (reglas): sin el permiso la lectura se
                    // negaría y tumbaría el reporte entero. Sale en cero.
                    val payAsync = async {
                        if (user.hasPermission(com.bakery.erp.model.Permission.VIEW_EMPLOYEE_SALARY))
                            payrollRepository.aggregatePayrollForPeriod(user.orgId, start, endExclusive)
                        else com.bakery.erp.domain.report.PayrollSummary()
                    }
                    val stockAsync = async {
                        finishedStockRepository.aggregateMovementsInRange(user.orgId, "", start, endExclusive)
                    }
                    val mermaAsync = async { breadCountRepository.aggregateMermasForMonth(user.orgId, "", period) }
                    val purchAsync = async {
                        purchaseRepository.aggregatePurchasesInRange(user.orgId, start, endExclusive)
                    }

                    Sextuple(
                        salesAsync.await(),
                        expAsync.await(),
                        payAsync.await(),
                        stockAsync.await(),
                        mermaAsync.await(),
                        purchAsync.await()
                    )
                }
            }.onSuccess { (sales, exp, pay, stock, merma, purch) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sales = sales,
                        expenses = exp,
                        payroll = pay,
                        stock = stock,
                        merma = merma,
                        purchases = purch
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}

private data class Sextuple<A, B, C, D, E, F>(
    val a: A, val b: B, val c: C, val d: D, val e: E, val f: F
)
