package com.bakery.erp.presentation.reports.productcost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.CostProblem
import com.bakery.erp.domain.IngredientGramPrice
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.PreparationRepository
import com.bakery.erp.domain.ProductMaterialCost
import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.domain.PurchaseRepository
import com.bakery.erp.domain.RecipeRepository
import com.bakery.erp.domain.ingredientGramPrices
import com.bakery.erp.domain.productMaterialCost
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.IngredientUsage
import com.bakery.erp.model.isIngredientBacked
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Insumo de materia prima y su precio por gramo; [price] null = sin precio. */
data class IngredientPriceRow(
    val ingredient: Ingredient,
    val price: IngredientGramPrice?,
    /** Productos cuyo costo queda corto por este insumo. */
    val affectedProducts: Int
)

data class ProductCostUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val costs: List<ProductMaterialCost> = emptyList(),
    val ingredientPrices: List<IngredientPriceRow> = emptyList(),
    val loadedForCompany: String? = null
) {
    val calculated: List<ProductMaterialCost> get() = costs.filter { it.total != null }
    val notCalculated: List<ProductMaterialCost> get() = costs.filter { it.total == null }
    val incomplete: List<ProductMaterialCost> get() = costs.filter { it.isIncomplete }
    val missingPriceIngredients: List<IngredientPriceRow>
        get() = ingredientPrices.filter { it.price == null && it.affectedProducts > 0 }
}

/**
 * Costo de materia prima de cada producto propio. Precio de insumos: promedio de lo comprado en
 * [PRICE_WINDOW_DAYS] días o, si no se compró, el de su presentación (ver `ingredientGramPrices`).
 */
class ProductCostViewModel(
    private val productRepository: ProductRepository,
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val preparationRepository: PreparationRepository,
    private val purchaseRepository: PurchaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductCostUiState())
    val uiState: StateFlow<ProductCostUiState> = _uiState.asStateFlow()

    fun ensureLoaded(companyId: String) {
        if (_uiState.value.loadedForCompany == companyId || _uiState.value.isLoading) return
        reload(companyId)
    }

    fun reload(companyId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { load(companyId) }
                .onSuccess { s -> _uiState.value = s.copy(loadedForCompany = companyId) }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "No se pudo calcular") } }
        }
    }

    private suspend fun load(companyId: String): ProductCostUiState = coroutineScope {
        val productsD = async { productRepository.getProducts(companyId) }
        val recipesD = async { recipeRepository.getRecipesWithYields(companyId) }
        val ingredientsD = async { ingredientRepository.getIngredients(companyId) }
        val preparationsD = async { runCatching { preparationRepository.getPreparations(companyId) }.getOrElse { emptyList() } }
        val purchasesD = async { purchaseRepository.getPurchases(companyId) }

        // Solo lo que hace la planta: la reventa ya tiene su costo de compra, y lo que vende
        // un insumo (el huevo por docena) cuesta lo que cuesta el insumo.
        val products = productsD.await()
            .filter { it.active && it.isInHouseProduced && !it.isIngredientBacked }
        val ingredients = ingredientsD.await().filter { it.usage == IngredientUsage.PRODUCTION }
        val ingredientIds = ingredients.map { it.id }.toSet()

        // `getRecipesWithYields` no trae componentes: se lee completa cada receta que produce
        // algo (o que es masa de relleno de alguien). Son pocas, una lectura por receta.
        val headers = recipesD.await()
        val productIds = products.map { it.id }.toSet()
        val fillingRecipeIds = products.flatMap { p -> p.doughFillings.map { it.recipeId } }.toSet()
        val neededIds = headers.filter { r -> r.yields.any { it.productId in productIds } || r.id in fillingRecipeIds }
            .map { it.id }
        val recipes = neededIds.map { id -> async { runCatching { recipeRepository.getRecipe(companyId, id) }.getOrNull() } }
            .awaitAll().filterNotNull()
        val recipesById = recipes.associateBy { it.id }
        val recipeByProduct = buildMap {
            recipes.forEach { r -> r.yields.forEach { y -> if (y.productId !in this) put(y.productId, r) } }
        }

        // Compras del periodo que traen materia prima: solo esas abren sus líneas.
        val since = Clock.System.now().toEpochMilliseconds() - PRICE_WINDOW_DAYS * DAY_MS
        val lines = purchasesD.await()
            .filter { it.date >= since && it.itemIds.any { id -> id in ingredientIds } }
            .map { p -> async { runCatching { purchaseRepository.getLines(companyId, p.id) }.getOrElse { emptyList() } } }
            .awaitAll().flatten()

        val prices = ingredientGramPrices(ingredients, lines)
        val ingredientsById = ingredients.associateBy { it.id }
        val preparationsById = preparationsD.await().associateBy { it.id }
        val costs = products.map {
            productMaterialCost(it, recipeByProduct, recipesById, preparationsById, ingredientsById, prices)
        }.sortedWith(compareBy({ it.product.category }, { it.product.name.lowercase() }))

        val missingCount = costs.flatMap { c ->
            c.problems.filterIsInstance<CostProblem.MissingPrices>().flatMap { it.names }.distinct()
        }.groupingBy { it }.eachCount()
        val rows = ingredients.map { ing -> IngredientPriceRow(ing, prices[ing.id], missingCount[ing.name] ?: 0) }
            .sortedWith(compareBy({ it.price != null }, { -it.affectedProducts }, { it.ingredient.name.lowercase() }))

        ProductCostUiState(isLoading = false, costs = costs, ingredientPrices = rows)
    }

    companion object {
        const val PRICE_WINDOW_DAYS = 90L
        private const val DAY_MS = 86_400_000L
    }
}
