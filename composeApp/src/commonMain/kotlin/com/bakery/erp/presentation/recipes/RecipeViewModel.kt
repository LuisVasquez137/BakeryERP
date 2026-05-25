package com.bakery.erp.presentation.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.RecipeImageUploader
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
    private val repository: RecipeRepository,
    private val imageUploader: RecipeImageUploader
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

    /**
     * @param stepPhotos bytes nuevos por id de paso (id estable generado en el form).
     *   Se suben a Storage antes de persistir y se fija [RecipeStep.photoUrl].
     */
    fun saveRecipe(
        companyId: String,
        recipe: Recipe,
        stepPhotos: Map<String, ByteArray> = emptyMap()
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val isNew = recipe.id.isEmpty()
                val recipeId = if (isNew) generateId() else recipe.id
                val withId = recipe.copy(id = recipeId)
                val finalRecipe = if (stepPhotos.isEmpty()) withId else withId.copy(
                    steps = withId.steps.map { step ->
                        val bytes = stepPhotos[step.id]
                        if (bytes == null) step
                        else step.copy(
                            photoUrl = imageUploader.uploadStepImage(
                                bytes, recipeId, step.stageId, step.id
                            )
                        )
                    }
                )
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
