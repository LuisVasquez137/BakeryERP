package com.bakery.erp.presentation.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.model.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ProductUiState(
    val productList: List<Product> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedProduct: Product? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false
)

class ProductViewModel(
    private val repository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductUiState())
    val uiState: StateFlow<ProductUiState> = _uiState.asStateFlow()

    fun loadProducts(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.getProducts(companyId) }
                .onSuccess { _uiState.value = _uiState.value.copy(productList = it, isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun saveProduct(companyId: String, product: Product, imageBytes: ByteArray?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val isNew = product.id.isEmpty()
                val productId = if (isNew) generateId() else product.id
                var finalProduct = product.copy(id = productId)

                if (imageBytes != null) {
                    val imageUrl = repository.uploadImage(imageBytes, productId)
                    finalProduct = finalProduct.copy(imageUrl = imageUrl)
                }

                if (isNew) repository.addProduct(companyId, finalProduct)
                else repository.updateProduct(companyId, finalProduct)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
                loadProducts(companyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun deleteProduct(companyId: String, productId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.deleteProduct(companyId, productId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isDeleted = true)
                    loadProducts(companyId)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun selectProduct(product: Product?) {
        _uiState.value = _uiState.value.copy(selectedProduct = product)
    }

    fun clearSaved() {
        _uiState.value = _uiState.value.copy(isSaved = false)
    }

    fun clearDeleted() {
        _uiState.value = _uiState.value.copy(isDeleted = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun generateId(): String {
        val hex = "0123456789abcdef"
        return Random.nextBytes(16).joinToString("") { b ->
            hex[(b.toInt() shr 4) and 0xF].toString() + hex[b.toInt() and 0xF]
        }
    }
}
