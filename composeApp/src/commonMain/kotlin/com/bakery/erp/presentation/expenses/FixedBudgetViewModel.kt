package com.bakery.erp.presentation.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.FixedBudgetRepository
import com.bakery.erp.model.FixedBudgetItem
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
    val isDeleted: Boolean = false
)

class FixedBudgetViewModel(
    private val repository: FixedBudgetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FixedBudgetUiState())
    val uiState: StateFlow<FixedBudgetUiState> = _uiState.asStateFlow()

    fun loadItems(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.getFixedBudgetItems(companyId) }
                .onSuccess { _uiState.value = _uiState.value.copy(itemList = it, isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

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
