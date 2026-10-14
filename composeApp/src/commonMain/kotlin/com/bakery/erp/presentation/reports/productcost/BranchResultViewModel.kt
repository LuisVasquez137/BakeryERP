package com.bakery.erp.presentation.reports.productcost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.BranchResultsMonth
import com.bakery.erp.domain.DispatchRepository
import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.domain.ExpenseRepository
import com.bakery.erp.domain.PayrollRepository
import com.bakery.erp.domain.PurchaseRepository
import com.bakery.erp.domain.SaleRepository
import com.bakery.erp.domain.StandardCostRepository
import com.bakery.erp.domain.branchResults
import com.bakery.erp.domain.deliveryExpenses
import com.bakery.erp.domain.deliveryMonthCost
import com.bakery.erp.domain.deliveryPayroll
import com.bakery.erp.domain.storeDeliveries
import com.bakery.erp.model.PurchaseStatus
import com.bakery.erp.model.nextPeriod
import com.bakery.erp.model.previousPeriod
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class BranchResultUiState(
    val period: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val result: BranchResultsMonth? = null,
    /** El pan del mes se valoró a costo estándar congelado (si no, con la lista "Sucursales"). */
    val standardCostFrozen: Boolean = false
)

/** "Resultado por sucursal": cuánto aporta cada tienda, cómo salió cada planta y los generales. */
class BranchResultViewModel(
    private val branchRepository: BranchRepository,
    private val saleRepository: SaleRepository,
    private val dispatchRepository: DispatchRepository,
    private val purchaseRepository: PurchaseRepository,
    private val expenseRepository: ExpenseRepository,
    private val employeeRepository: EmployeeRepository,
    private val payrollRepository: PayrollRepository,
    private val standardCostRepository: StandardCostRepository,
    private val stockTransferRepository: com.bakery.erp.domain.StockTransferRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BranchResultUiState(period = currentMonth()))
    val uiState: StateFlow<BranchResultUiState> = _uiState.asStateFlow()

    /** El mes en curso y los dos anteriores. */
    val periodOptions: List<String>
        get() = currentMonth().let { m -> listOf(previousPeriod(previousPeriod(m)), previousPeriod(m), m) }

    fun load(companyId: String, period: String = _uiState.value.period) {
        viewModelScope.launch {
            _uiState.update { it.copy(period = period, isLoading = true, error = null) }
            runCatching { compute(companyId, period) }
                .onSuccess { s -> _uiState.value = s }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "No se pudo calcular") } }
        }
    }

    private suspend fun compute(companyId: String, period: String): BranchResultUiState = coroutineScope {
        val tz = TimeZone.currentSystemDefault()
        val from = startOf(period, tz)
        val until = startOf(nextPeriod(period), tz)

        val branchesD = async { branchRepository.getBranches(companyId) }
        val salesD = async { saleRepository.aggregateSalesInRange(companyId, null, from, until) }
        // getNotesInRange incluye el extremo final.
        val notesD = async { dispatchRepository.getNotesInRange(companyId, from, until - 1) }
        val expensesD = async { expenseRepository.getExpensesForPeriod(companyId, period) }
        val employeesD = async { employeeRepository.getEmployees(companyId) }
        val payrollD = async { runCatching { payrollRepository.getPayrollForPeriod(companyId, from, until) }.getOrElse { emptyList() } }
        // Traslados del mes en que se RECIBIERON: es cuando el costo cambió de sucursal.
        val transfersD = async { runCatching { stockTransferRepository.getReceivedInRange(companyId, from, until) }.getOrElse { emptyList() } }
        val frozenD = async { runCatching { standardCostRepository.getPeriod(companyId, period) }.getOrNull() }
        // Solo las compras del mes ya recibidas; sus líneas dicen qué es reventa y qué insumo.
        val purchases = purchaseRepository.getPurchases(companyId)
            .filter { it.date in from until until && it.status != PurchaseStatus.PENDING_RECEIPT }
            .map { p -> async { p to purchaseRepository.getLines(companyId, p.id) } }
            .awaitAll()

        val notes = notesD.await()
        val expenses = expensesD.await()
        val employees = employeesD.await()
        val payroll = payrollD.await()
        val (deliveryPay, deliveryPeople) = deliveryPayroll(employees, payroll)
        val delivery = deliveryMonthCost(deliveryExpenses(expenses), deliveryPay, deliveryPeople, storeDeliveries(notes, tz))

        BranchResultUiState(
            period = period,
            result = branchResults(
                branches = branchesD.await(),
                salesByBranch = salesD.await().byBranch.associate { it.branchId to it.total },
                notes = notes,
                purchases = purchases,
                expenses = expenses,
                employees = employees,
                payrollRecords = payroll,
                delivery = delivery,
                transfers = transfersD.await()
            ),
            standardCostFrozen = frozenD.await() != null
        )
    }
}

private fun currentMonth(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return "${now.year}-${now.monthNumber.toString().padStart(2, '0')}"
}

private fun startOf(period: String, tz: TimeZone): Long =
    LocalDate(period.substring(0, 4).toInt(), period.substring(5, 7).toInt(), 1)
        .atStartOfDayIn(tz).toEpochMilliseconds()
