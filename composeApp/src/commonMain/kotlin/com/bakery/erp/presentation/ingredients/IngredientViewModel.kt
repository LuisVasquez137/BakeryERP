package com.bakery.erp.presentation.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.IngredientUsage
import com.bakery.erp.domain.RecipeRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchStock
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.StockMovement
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class IngredientUiState(
    val ingredientList: List<Ingredient> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedIngredient: Ingredient? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val relatedRecipes: List<IngredientUsage>? = null,
    val isRelatedRecipesLoading: Boolean = false,
    val relatedRecipesIngredientId: String? = null,
    // Branch filter for ingredient list
    val accessibleBranches: List<Branch> = emptyList(),
    val selectedBranch: Branch? = null,
    val selectedBranchStockMap: Map<String, Double> = emptyMap(),
    val isBranchStocksLoading: Boolean = false,
    // Detail tabs
    val selectedIngredientStocks: List<BranchStock>? = null,
    val selectedIngredientStocksForId: String? = null,
    val stockMovements: List<StockMovement>? = null,
    val stockMovementsForId: String? = null,
    val isMovementsLoading: Boolean = false
)

class IngredientViewModel(
    private val repository: IngredientRepository,
    private val recipeRepository: RecipeRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IngredientUiState())
    val uiState: StateFlow<IngredientUiState> = _uiState.asStateFlow()

    fun loadIngredients(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.getIngredients(companyId) }
                .onSuccess { _uiState.value = _uiState.value.copy(ingredientList = it, isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun saveIngredient(companyId: String, ingredient: Ingredient) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val isNew = ingredient.id.isEmpty()
                val finalIngredient = if (isNew) ingredient.copy(id = generateId()) else ingredient
                if (isNew) repository.addIngredient(companyId, finalIngredient)
                else repository.updateIngredient(companyId, finalIngredient)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
                loadIngredients(companyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun deleteIngredient(companyId: String, ingredientId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.deleteIngredient(companyId, ingredientId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isDeleted = true)
                    loadIngredients(companyId)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun loadRelatedRecipes(companyId: String, ingredientId: String) {
        if (_uiState.value.relatedRecipesIngredientId == ingredientId &&
            _uiState.value.relatedRecipes != null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRelatedRecipesLoading = true,
                    relatedRecipesIngredientId = ingredientId,
                    relatedRecipes = null
                )
            }
            val usages = runCatching {
                recipeRepository.getRecipesUsingIngredient(companyId, ingredientId)
            }.getOrElse { emptyList() }
            _uiState.update { it.copy(relatedRecipes = usages, isRelatedRecipesLoading = false) }
        }
    }

    // ── Branch filter for ingredient list ────────────────────────────────────

    fun loadAccessibleBranches(companyId: String, currentUser: User) {
        viewModelScope.launch {
            val branches = runCatching { branchRepository.getBranches(companyId) }.getOrElse { emptyList() }
            val accessible = branches.filter { b -> b.active && currentUser.canAccessBranch(b.id) }
            val currentSelected = _uiState.value.selectedBranch
            val branchToSelect = currentSelected ?: accessible.firstOrNull()
            _uiState.update {
                it.copy(
                    accessibleBranches = accessible,
                    selectedBranch = branchToSelect
                )
            }
            branchToSelect?.let { loadStocksForBranch(companyId, it.id) }
        }
    }

    fun selectBranch(companyId: String, branch: Branch) {
        _uiState.update { it.copy(selectedBranch = branch, selectedBranchStockMap = emptyMap()) }
        loadStocksForBranch(companyId, branch.id)
    }

    private fun loadStocksForBranch(companyId: String, branchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBranchStocksLoading = true) }
            val map = runCatching { repository.getBranchStocksForBranch(companyId, branchId) }.getOrElse { emptyMap() }
            _uiState.update { it.copy(selectedBranchStockMap = map, isBranchStocksLoading = false) }
        }
    }

    // ── Detail tabs ──────────────────────────────────────────────────────────

    fun loadIngredientBranchStocks(companyId: String, ingredientId: String, currentUser: User) {
        if (_uiState.value.selectedIngredientStocksForId == ingredientId &&
            _uiState.value.selectedIngredientStocks != null) return
        viewModelScope.launch {
            val stocks = runCatching {
                repository.getBranchStocksForIngredient(companyId, ingredientId, currentUser.branchIds)
            }.getOrElse { emptyList() }
            _uiState.update { it.copy(
                selectedIngredientStocks = stocks,
                selectedIngredientStocksForId = ingredientId
            ) }
        }
    }

    fun loadStockMovements(companyId: String, ingredientId: String) {
        if (_uiState.value.stockMovementsForId == ingredientId &&
            _uiState.value.stockMovements != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMovementsLoading = true) }
            val movements = runCatching { repository.getStockMovements(companyId, ingredientId) }.getOrElse { emptyList() }
            _uiState.update { it.copy(
                stockMovements = movements,
                stockMovementsForId = ingredientId,
                isMovementsLoading = false
            ) }
        }
    }

    fun clearIngredientDetailData() {
        _uiState.update { it.copy(
            selectedIngredientStocks = null,
            selectedIngredientStocksForId = null,
            stockMovements = null,
            stockMovementsForId = null
        ) }
    }

    fun refreshSelectedBranchStocks(companyId: String) {
        val branch = _uiState.value.selectedBranch ?: return
        loadStocksForBranch(companyId, branch.id)
    }

    fun selectIngredient(ingredient: Ingredient?) {
        _uiState.value = _uiState.value.copy(selectedIngredient = ingredient)
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
