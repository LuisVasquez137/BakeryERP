package com.bakery.erp.presentation.suppliers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.SupplierRepository
import com.bakery.erp.model.Supplier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class SupplierUiState(
    val supplierList: List<Supplier> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedSupplier: Supplier? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false
)

class SupplierViewModel(
    private val repository: SupplierRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupplierUiState())
    val uiState: StateFlow<SupplierUiState> = _uiState.asStateFlow()

    fun loadSuppliers(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.getSuppliers(companyId) }
                .onSuccess { _uiState.value = _uiState.value.copy(supplierList = it, isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun saveSupplier(companyId: String, supplier: Supplier) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val isNew = supplier.id.isEmpty()
                val final = if (isNew) supplier.copy(id = generateId()) else supplier
                if (isNew) repository.addSupplier(companyId, final)
                else repository.updateSupplier(companyId, final)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
                loadSuppliers(companyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun deleteSupplier(companyId: String, supplierId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.deleteSupplier(companyId, supplierId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isDeleted = true)
                    loadSuppliers(companyId)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun selectSupplier(supplier: Supplier?) {
        _uiState.value = _uiState.value.copy(selectedSupplier = supplier)
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
