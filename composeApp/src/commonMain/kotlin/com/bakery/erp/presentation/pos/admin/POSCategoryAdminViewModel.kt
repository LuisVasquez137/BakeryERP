package com.bakery.erp.presentation.pos.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.POSCategoryRepository
import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.POSCategory
import com.bakery.erp.model.Product
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.productCategoryConflicts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class POSCategoryAdminUiState(
    val categories: List<POSCategory> = emptyList(),
    val products: List<Product> = emptyList(),
    val branches: List<Branch> = emptyList(),
    val selectedCategory: POSCategory? = null,
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false
)

class POSCategoryAdminViewModel(
    private val categoryRepository: POSCategoryRepository,
    private val productRepository: ProductRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(POSCategoryAdminUiState())
    val uiState: StateFlow<POSCategoryAdminUiState> = _uiState.asStateFlow()

    fun load(companyId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val categories = categoryRepository.getCategories(companyId)
                val products = productRepository.getProducts(companyId)
                    .filter { it.active }
                    .sortedBy { it.name }
                val branches = branchRepository.getBranches(companyId)
                    .filter { it.active }
                    .filter { currentUser.canAccessBranch(it.id) }
                    .filter { BranchCapability.CAN_SELL in it.capabilities }
                    .sortedBy { it.name }
                Triple(categories, products, branches)
            }.onSuccess { (categories, products, branches) ->
                _uiState.update {
                    it.copy(isLoading = false, categories = categories, products = products, branches = branches)
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun loadCategory(companyId: String, categoryId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, error = null) }
            runCatching { categoryRepository.getCategory(companyId, categoryId) }
                .onSuccess { c ->
                    _uiState.update { it.copy(isDetailLoading = false, selectedCategory = c) }
                }.onFailure { err ->
                    _uiState.update { it.copy(isDetailLoading = false, error = err.message) }
                }
        }
    }

    fun clearSelected() { _uiState.update { it.copy(selectedCategory = null) } }

    /**
     * Guarda la categoría. [movedProductIds] son los productos que el usuario eligió
     * expresamente pasar desde otra categoría: se quitan de aquélla. Cualquier OTRO choque
     * (alguien los puso en otra categoría desde otro dispositivo mientras se editaba) corta
     * el guardado — un producto en dos categorías dejaría sin saber a qué grupo repartirle
     * la venta en el conteo de cierre.
     */
    fun saveCategory(companyId: String, category: POSCategory, movedProductIds: Set<String> = emptySet()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val fresh = categoryRepository.getCategories(companyId)
                val conflicts = productCategoryConflicts(category, fresh)
                    .filterKeys { it in category.productIds }
                val unexpected = conflicts.filterKeys { it !in movedProductIds }
                if (unexpected.isNotEmpty()) {
                    val names = _uiState.value.products.associate { it.id to it.name }
                    val detail = unexpected.entries.take(3).joinToString {
                        "${names[it.key] ?: "producto"} (en ${it.value.name})"
                    }
                    throw IllegalStateException("Ya están en otra categoría: $detail")
                }
                conflicts.values.distinctBy { it.id }.forEach { other ->
                    categoryRepository.upsertCategory(
                        companyId,
                        other.copy(productIds = other.productIds.filterNot { it in conflicts.keys })
                    )
                }
                categoryRepository.upsertCategory(companyId, category)
                categoryRepository.getCategories(companyId)
            }
                .onSuccess { cats -> _uiState.update { it.copy(isLoading = false, isSaved = true, categories = cats) } }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, error = err.message) } }
        }
    }

    /** Relee las categorías: el formulario las necesita al día para esconder lo ya usado. */
    fun refreshCategories(companyId: String) {
        viewModelScope.launch {
            runCatching { categoryRepository.getCategories(companyId) }
                .onSuccess { cats -> _uiState.update { it.copy(categories = cats) } }
        }
    }

    fun deleteCategory(companyId: String, categoryId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                categoryRepository.deleteCategory(companyId, categoryId)
                categoryRepository.getCategories(companyId)
            }
                .onSuccess { cats -> _uiState.update { it.copy(isLoading = false, isDeleted = true, categories = cats) } }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, error = err.message) } }
        }
    }

    fun clearSaved() { _uiState.update { it.copy(isSaved = false) } }
    fun clearDeleted() { _uiState.update { it.copy(isDeleted = false) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
