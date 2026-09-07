package com.bakery.erp.presentation.pricing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.PriceListRepository
import com.bakery.erp.model.PriceList
import com.bakery.erp.model.ProductPrice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class PriceListUiState(
    val lists: List<PriceList> = emptyList(),
    val pricesForProduct: List<ProductPrice> = emptyList(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null
)

class PriceListViewModel(
    private val repository: PriceListRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PriceListUiState())
    val uiState: StateFlow<PriceListUiState> = _uiState.asStateFlow()

    fun loadLists(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                repository.ensureSeedLists(companyId)
                repository.getLists(companyId)
            }.onSuccess { lists ->
                _uiState.value = _uiState.value.copy(
                    lists = lists.sortedWith(compareByDescending<PriceList> { it.isDefault }.thenBy { it.type }.thenBy { it.name }),
                    isLoading = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun saveList(companyId: String, list: PriceList) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val isNew = list.id.isEmpty()
                val final = if (isNew) list.copy(id = generateId()) else list
                if (isNew) repository.addList(companyId, final)
                else repository.updateList(companyId, final)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
                loadLists(companyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun deleteList(companyId: String, listId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.deleteList(companyId, listId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isDeleted = true)
                    loadLists(companyId)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                }
        }
    }

    /** Carga los precios de un producto en todas las listas. Si no existe entrada se devuelve
     *  ausente: el caller hace el merge con `lists` para mostrar un input vacío. */
    fun loadPricesForProduct(companyId: String, productId: String) {
        viewModelScope.launch {
            runCatching { repository.getPricesForProduct(companyId, productId) }
                .onSuccess { _uiState.value = _uiState.value.copy(pricesForProduct = it) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun upsertPrice(companyId: String, price: ProductPrice) {
        viewModelScope.launch {
            runCatching { repository.upsertPrice(companyId, price) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
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
