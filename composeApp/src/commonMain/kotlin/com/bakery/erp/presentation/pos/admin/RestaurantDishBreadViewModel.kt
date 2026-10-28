package com.bakery.erp.presentation.pos.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.domain.RestaurantDishRepository
import com.bakery.erp.model.Product
import com.bakery.erp.model.RestaurantDish
import com.bakery.erp.model.RestaurantDishBread
import com.bakery.erp.model.isIngredientBacked
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RestaurantDishBreadUiState(
    /** Con pan primero; dentro, los que llegaron más recientemente. */
    val dishes: List<RestaurantDish> = emptyList(),
    /** Lo que se puede asignar: productos del mostrador con inventario propio. */
    val products: List<Product> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    /** Cambia cada vez que se guarda: la pantalla cierra el diálogo al verlo. */
    val savedAt: Long = 0L
)

/** Qué pan del mostrador lleva cada platillo del restaurante. Ver [RestaurantDish]. */
class RestaurantDishBreadViewModel(
    private val repository: RestaurantDishRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RestaurantDishBreadUiState())
    val uiState: StateFlow<RestaurantDishBreadUiState> = _uiState.asStateFlow()

    fun load(companyId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val products = async {
                    productRepository.getProducts(companyId)
                        .filter { it.active && !it.isIngredientBacked }
                        .sortedBy { it.name.lowercase() }
                }
                sorted(repository.getDishes(companyId)) to products.await()
            }.onSuccess { (dishes, products) ->
                _uiState.update { it.copy(isLoading = false, dishes = dishes, products = products) }
            }.onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun save(companyId: String, dish: RestaurantDish, breads: List<RestaurantDishBread>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                repository.setBreads(companyId, dish.externalItemId, breads)
                sorted(repository.getDishes(companyId))
            }.onSuccess { dishes ->
                _uiState.update {
                    it.copy(isSaving = false, dishes = dishes,
                        savedAt = kotlin.time.Clock.System.now().toEpochMilliseconds())
                }
            }.onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message ?: "No se pudo guardar") } }
        }
    }

    private fun sorted(dishes: List<RestaurantDish>) =
        dishes.sortedWith(compareByDescending<RestaurantDish> { it.hasBread }.thenByDescending { it.lastSeenAt })

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
