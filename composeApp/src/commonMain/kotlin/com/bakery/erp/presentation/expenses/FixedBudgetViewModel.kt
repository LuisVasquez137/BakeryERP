package com.bakery.erp.presentation.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.ExpenseRepository
import com.bakery.erp.domain.FixedBudgetRepository
import com.bakery.erp.model.FixedBudgetItem
import com.bakery.erp.model.fixedExpenseId
import com.bakery.erp.model.toMonthlyExpense
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class FixedBudgetUiState(
    val itemList: List<FixedBudgetItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    /** Mes que se ofrece anotar ("YYYY-MM"): el actual. */
    val period: String = currentMonth(),
    /** Ids de los gastos de [period] que ya salieron de un costo fijo. */
    val postedExpenseIds: Set<String> = emptySet(),
    val isPosting: Boolean = false,
    /** Resultado de la última vez que se anotó el mes, para el snackbar. */
    val postMessage: String? = null
) {
    /** Costos fijos que todavía no tienen su gasto de [period]. */
    val pendingForPeriod: List<FixedBudgetItem>
        get() = itemList.filter { it.active && it.amount > 0.0 && fixedExpenseId(it.id, period) !in postedExpenseIds }
}

private fun currentMonth(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return "${now.year}-${now.monthNumber.toString().padStart(2, '0')}"
}

class FixedBudgetViewModel(
    private val repository: FixedBudgetRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FixedBudgetUiState())
    val uiState: StateFlow<FixedBudgetUiState> = _uiState.asStateFlow()

    fun loadItems(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val period = currentMonth()
            runCatching {
                val items = repository.getFixedBudgetItems(companyId)
                val posted = runCatching { expenseRepository.getExpensesForPeriod(companyId, period) }
                    .getOrElse { emptyList() }
                    .map { it.id }.filter { it.startsWith("fijo_") }.toSet()
                items to posted
            }
                .onSuccess { (items, posted) ->
                    _uiState.value = _uiState.value.copy(
                        itemList = items, postedExpenseIds = posted, period = period, isLoading = false
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    /**
     * Crea el gasto del mes de cada costo fijo que todavía no lo tiene. Relee los gastos del mes
     * justo antes (otro teléfono pudo haberlos anotado) y el id fijo garantiza que un reintento
     * reescriba el mismo documento en vez de duplicarlo.
     */
    fun postMonth(companyId: String) {
        if (_uiState.value.isPosting) return
        viewModelScope.launch {
            val period = _uiState.value.period
            _uiState.value = _uiState.value.copy(isPosting = true, error = null)
            runCatching {
                val posted = expenseRepository.getExpensesForPeriod(companyId, period).map { it.id }.toSet()
                val now = Clock.System.now().toEpochMilliseconds()
                val toPost = _uiState.value.itemList
                    .filter { it.active && it.amount > 0.0 && fixedExpenseId(it.id, period) !in posted }
                toPost.forEach { item ->
                    expenseRepository.addExpense(companyId, item.toMonthlyExpense(period, now).copy(orgId = companyId))
                }
                toPost.size
            }.onSuccess { count ->
                _uiState.value = _uiState.value.copy(
                    isPosting = false,
                    postMessage = if (count == 0) "Los costos fijos de este mes ya estaban anotados"
                    else "Se anotaron $count gastos. Corregí en Gastos los que no salieron igual (la luz, el agua)."
                )
                loadItems(companyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isPosting = false, error = it.message)
                loadItems(companyId)
            }
        }
    }

    fun clearPostMessage() { _uiState.value = _uiState.value.copy(postMessage = null) }

    fun saveItem(companyId: String, item: FixedBudgetItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val isNew = item.id.isEmpty()
                val final = if (isNew) item.copy(id = generateId(), orgId = companyId) else item.copy(orgId = companyId)
                if (isNew) repository.addFixedBudgetItem(companyId, final)
                else repository.updateFixedBudgetItem(companyId, final)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
                loadItems(companyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun deleteItem(companyId: String, itemId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.deleteFixedBudgetItem(companyId, itemId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isDeleted = true)
                    loadItems(companyId)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun clearSaved() { _uiState.value = _uiState.value.copy(isSaved = false) }
    fun clearDeleted() { _uiState.value = _uiState.value.copy(isDeleted = false) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    private fun generateId(): String {
        val hex = "0123456789abcdef"
        return Random.nextBytes(16).joinToString("") { b ->
            hex[(b.toInt() shr 4) and 0xF].toString() + hex[b.toInt() and 0xF]
        }
    }
}
