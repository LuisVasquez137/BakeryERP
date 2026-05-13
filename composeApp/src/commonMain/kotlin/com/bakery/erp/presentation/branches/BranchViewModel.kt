package com.bakery.erp.presentation.branches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.model.Branch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class BranchUiState(
    val branchList: List<Branch> = emptyList(),
    val selectedBranch: Branch? = null,
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false
)

class BranchViewModel(
    private val repository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BranchUiState())
    val uiState: StateFlow<BranchUiState> = _uiState.asStateFlow()

    fun loadBranches(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.getBranches(companyId) }
                .onSuccess { _uiState.value = _uiState.value.copy(branchList = it, isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun saveBranch(companyId: String, branch: Branch) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val isNew = branch.id.isEmpty()
                val final = if (isNew) branch.copy(id = generateId(), orgId = companyId) else branch
                if (isNew) repository.addBranch(companyId, final)
                else repository.updateBranch(companyId, final)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
                loadBranches(companyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun deleteBranch(companyId: String, branchId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.deleteBranch(companyId, branchId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isDeleted = true)
                    loadBranches(companyId)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun selectBranch(branch: Branch?) {
        _uiState.value = _uiState.value.copy(selectedBranch = branch)
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
