package com.bakery.erp.presentation.pos.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.PackagingItemRepository
import com.bakery.erp.model.PackagingItem
import com.bakery.erp.model.packagingItemProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PackagingAdminUiState(
    val items: List<PackagingItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    /** Cambia cada vez que se guarda o borra: la pantalla cierra el diálogo al verlo. */
    val savedAt: Long = 0L
)

/** Catálogo de empaques que se cobran en caja (bolsa, caja de pastel). */
class PackagingAdminViewModel(
    private val repository: PackagingItemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PackagingAdminUiState())
    val uiState: StateFlow<PackagingAdminUiState> = _uiState.asStateFlow()

    fun load(companyId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.getItems(companyId) }
                .onSuccess { items -> _uiState.update { it.copy(isLoading = false, items = items) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun save(companyId: String, item: PackagingItem) {
        packagingItemProblem(item)?.let { problem ->
            _uiState.update { it.copy(error = problem) }; return
        }
        val duplicate = _uiState.value.items.any {
            it.id != item.id && it.name.trim().equals(item.name.trim(), ignoreCase = true)
        }
        if (duplicate) {
            _uiState.update { it.copy(error = "Ya hay un empaque llamado «${item.name.trim()}»") }; return
        }
        // Uno nuevo va al final de la fila de botones del POS.
        val toSave = if (item.id.isEmpty())
            item.copy(sortOrder = (_uiState.value.items.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        else item
        mutate(companyId) { repository.upsertItem(companyId, toSave) }
    }

    fun delete(companyId: String, itemId: String) =
        mutate(companyId) { repository.deleteItem(companyId, itemId) }

    private fun mutate(companyId: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                block()
                repository.getItems(companyId)
            }.onSuccess { items ->
                _uiState.update {
                    it.copy(isSaving = false, items = items,
                        savedAt = kotlin.time.Clock.System.now().toEpochMilliseconds())
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
