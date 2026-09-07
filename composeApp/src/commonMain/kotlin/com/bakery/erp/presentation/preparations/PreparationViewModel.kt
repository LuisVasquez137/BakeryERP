package com.bakery.erp.presentation.preparations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.PreparationRepository
import com.bakery.erp.model.Preparation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class PreparationUiState(
    val preparationList: List<Preparation> = emptyList(),
    val selectedPreparation: Preparation? = null,
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false
)

class PreparationViewModel(
    private val repository: PreparationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreparationUiState())
    val uiState: StateFlow<PreparationUiState> = _uiState.asStateFlow()

    fun loadPreparations(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.getPreparations(companyId) }
                .onSuccess { _uiState.value = _uiState.value.copy(preparationList = it, isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun loadPreparation(companyId: String, preparationId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDetailLoading = true, error = null)
            runCatching { repository.getPreparation(companyId, preparationId) }
                .onSuccess { _uiState.value = _uiState.value.copy(selectedPreparation = it, isDetailLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isDetailLoading = false, error = it.message) }
        }
    }

    fun savePreparation(companyId: String, preparation: Preparation) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val isNew = preparation.id.isEmpty()
                val final = if (isNew) preparation.copy(id = generateId()) else preparation
                if (isNew) repository.addPreparation(companyId, final)
                else repository.updatePreparation(companyId, final)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
                loadPreparations(companyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun deletePreparation(companyId: String, preparationId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.deletePreparation(companyId, preparationId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isDeleted = true)
                    loadPreparations(companyId)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun selectPreparation(preparation: Preparation?) {
        _uiState.value = _uiState.value.copy(selectedPreparation = preparation)
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
