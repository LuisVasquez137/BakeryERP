package com.bakery.erp.presentation.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.RecipeRepository
import com.bakery.erp.model.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class RecipeUiState(
    val recipeList: List<Recipe> = emptyList(),
    val selectedRecipe: Recipe? = null,
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false
)

class RecipeViewModel(
    private val repository: RecipeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeUiState())
    val uiState: StateFlow<RecipeUiState> = _uiState.asStateFlow()

    fun loadRecipes(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.getRecipes(companyId) }
                .onSuccess { _uiState.value = _uiState.value.copy(recipeList = it, isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun loadRecipe(companyId: String, recipeId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDetailLoading = true, error = null)
            runCatching { repository.getRecipe(companyId, recipeId) }
                .onSuccess { _uiState.value = _uiState.value.copy(selectedRecipe = it, isDetailLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isDetailLoading = false, error = it.message) }
        }
    }

    fun saveRecipe(companyId: String, recipe: Recipe) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val isNew = recipe.id.isEmpty()
                val finalRecipe = if (isNew) recipe.copy(id = generateId()) else recipe
                if (isNew) repository.addRecipe(companyId, finalRecipe)
                else repository.updateRecipe(companyId, finalRecipe)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
                loadRecipes(companyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun deleteRecipe(companyId: String, recipeId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.deleteRecipe(companyId, recipeId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isDeleted = true, selectedRecipe = null)
                    loadRecipes(companyId)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun selectRecipe(recipe: Recipe?) {
        _uiState.value = _uiState.value.copy(selectedRecipe = recipe)
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
