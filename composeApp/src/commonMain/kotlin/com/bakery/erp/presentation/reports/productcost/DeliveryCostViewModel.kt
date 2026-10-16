package com.bakery.erp.presentation.reports.productcost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.DeliveryMonthCost
import com.bakery.erp.domain.DispatchRepository
import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.domain.ExpenseRepository
import com.bakery.erp.domain.PayrollRepository
import com.bakery.erp.domain.deliveryExpenses
import com.bakery.erp.domain.deliveryMonthCost
import com.bakery.erp.domain.deliveryPayroll
import com.bakery.erp.domain.storeDeliveries
import com.bakery.erp.model.nextPeriod
import com.bakery.erp.model.previousPeriod
import kotlinx.coroutines.async
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

data class DeliveryCostUiState(
    val period: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val cost: DeliveryMonthCost? = null
)

/** "Costo de reparto": cuánto costó el camión en el mes y cuánto le toca a cada tienda. */
class DeliveryCostViewModel(
    private val expenseRepository: ExpenseRepository,
    private val employeeRepository: EmployeeRepository,
    private val payrollRepository: PayrollRepository,
    private val dispatchRepository: DispatchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeliveryCostUiState(period = thisMonth()))
    val uiState: StateFlow<DeliveryCostUiState> = _uiState.asStateFlow()

    /** El mes en curso y los dos anteriores. */
    val periodOptions: List<String>
        get() = thisMonth().let { m -> listOf(previousPeriod(previousPeriod(m)), previousPeriod(m), m) }

    /**
     * Si quien mira puede leer salarios (`VIEW_EMPLOYEE_SALARY`). Lo fija la pantalla; sin él,
     * las fichas se leen sin la parte de pago y el costo sale sin mano de obra.
     */
    var includePay: Boolean = false

    fun load(companyId: String, period: String = _uiState.value.period) {
        viewModelScope.launch {
            _uiState.update { it.copy(period = period, isLoading = true, error = null) }
            runCatching { compute(companyId, period) }
                .onSuccess { c -> _uiState.update { it.copy(isLoading = false, cost = c) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "No se pudo calcular") } }
        }
    }

    private suspend fun compute(companyId: String, period: String): DeliveryMonthCost = coroutineScope {
        val tz = TimeZone.currentSystemDefault()
        val from = monthStart(period, tz)
        val until = monthStart(nextPeriod(period), tz)
        val expensesD = async { expenseRepository.getExpensesForPeriod(companyId, period) }
        val employeesD = async { employeeRepository.getEmployees(
            companyId, if (includePay) setOf(EmployeeRepository.PrivatePart.PAY) else emptySet()
        ) }
        val payrollD = async { runCatching { payrollRepository.getPayrollForPeriod(companyId, from, until) }.getOrElse { emptyList() } }
        // getNotesInRange incluye el extremo final: hasta el último milisegundo del mes.
        val notesD = async { dispatchRepository.getNotesInRange(companyId, from, until - 1) }
        val (payroll, people) = deliveryPayroll(employeesD.await(), payrollD.await())
        deliveryMonthCost(
            expenses = deliveryExpenses(expensesD.await()),
            payroll = payroll,
            payrollPeople = people,
            stores = storeDeliveries(notesD.await(), tz)
        )
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

private fun thisMonth(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return "${now.year}-${now.monthNumber.toString().padStart(2, '0')}"
}

private fun monthStart(period: String, tz: TimeZone): Long =
    LocalDate(period.substring(0, 4).toInt(), period.substring(5, 7).toInt(), 1)
        .atStartOfDayIn(tz).toEpochMilliseconds()
