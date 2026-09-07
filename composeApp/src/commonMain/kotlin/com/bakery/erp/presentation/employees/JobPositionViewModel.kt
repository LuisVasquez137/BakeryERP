package com.bakery.erp.presentation.employees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.JobPositionRepository
import com.bakery.erp.model.JobPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JobPositionUiState(
    val positions: List<JobPosition> = emptyList(),
    val selectedPosition: JobPosition? = null,
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null
)

class JobPositionViewModel(
    private val repository: JobPositionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(JobPositionUiState())
    val uiState: StateFlow<JobPositionUiState> = _uiState.asStateFlow()

    fun loadPositions(companyId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val positions = runCatching { repository.getJobPositions(companyId) }.getOrElse { emptyList() }
            _uiState.update { it.copy(positions = positions, isLoading = false) }
        }
    }

    fun loadDetail(companyId: String, positionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true) }
            val position = runCatching { repository.getJobPosition(companyId, positionId) }.getOrNull()
            _uiState.update { it.copy(selectedPosition = position, isDetailLoading = false) }
        }
    }

    fun selectPosition(position: JobPosition?) {
        _uiState.update { it.copy(selectedPosition = position) }
    }

    fun savePosition(companyId: String, position: JobPosition) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.saveJobPosition(companyId, position) }
                .onSuccess { _uiState.update { it.copy(isLoading = false, isSaved = true) } }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, error = err.message) } }
        }
    }

    fun deletePosition(companyId: String, positionId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteJobPosition(companyId, positionId) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            positions = state.positions.filter { it.id != positionId },
                            selectedPosition = if (state.selectedPosition?.id == positionId) null
                                               else state.selectedPosition,
                            isDeleted = true
                        )
                    }
                }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun clearSaved() { _uiState.update { it.copy(isSaved = false) } }
    fun clearDeleted() { _uiState.update { it.copy(isDeleted = false) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
