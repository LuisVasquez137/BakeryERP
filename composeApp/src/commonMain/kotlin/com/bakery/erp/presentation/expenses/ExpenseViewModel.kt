package com.bakery.erp.presentation.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.ExpenseRepository
import com.bakery.erp.model.Expense
import com.bakery.erp.model.nextPeriod
import com.bakery.erp.model.previousPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

data class ExpenseUiState(
    val expenseList: List<Expense> = emptyList(),
    val selectedBranchId: String? = null,
    val selectedPeriod: String = currentPeriod(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false
)

private fun currentPeriod(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return "${now.year}-${now.monthNumber.toString().padStart(2, '0')}"
}

class ExpenseViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpenseUiState())
    val uiState: StateFlow<ExpenseUiState> = _uiState.asStateFlow()

    val filteredExpenses: List<Expense>
        get() = with(_uiState.value) {
            expenseList.filter {
                (selectedBranchId == null || it.branchId == selectedBranchId) &&
                it.period == selectedPeriod
            }
        }

    val totalAmount: Double get() = filteredExpenses.sumOf { it.amount }

    fun loadExpenses(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.getExpenses(companyId) }
                .onSuccess { _uiState.value = _uiState.value.copy(expenseList = it, isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun saveExpense(companyId: String, expense: Expense) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val isNew = expense.id.isEmpty()
                val final = if (isNew) expense.copy(id = generateId(), orgId = companyId) else expense
                if (isNew) repository.addExpense(companyId, final)
                else repository.updateExpense(companyId, final)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
                loadExpenses(companyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun deleteExpense(companyId: String, expenseId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.deleteExpense(companyId, expenseId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isDeleted = true)
                    loadExpenses(companyId)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun setFilterBranch(branchId: String?) {
        _uiState.value = _uiState.value.copy(selectedBranchId = branchId)
    }

    fun setPeriod(period: String) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period)
    }

    fun goNextPeriod() {
        _uiState.value = _uiState.value.copy(selectedPeriod = nextPeriod(_uiState.value.selectedPeriod))
    }

    fun goPreviousPeriod() {
        _uiState.value = _uiState.value.copy(selectedPeriod = previousPeriod(_uiState.value.selectedPeriod))
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
