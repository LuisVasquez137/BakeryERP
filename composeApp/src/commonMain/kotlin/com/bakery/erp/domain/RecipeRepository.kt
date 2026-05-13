package com.bakery.erp.domain

import com.bakery.erp.model.Recipe

data class IngredientUsage(
    val recipeId: String,
    val recipeName: String,
    val recipeCategory: String,
    val percentage: Double,
    val isBase: Boolean,
    val ingredientUnit: String
)

interface RecipeRepository {
    suspend fun getRecipes(companyId: String): List<Recipe>
    suspend fun getRecipesWithYields(companyId: String): List<Recipe>
    suspend fun getRecipe(companyId: String, recipeId: String): Recipe
    suspend fun addRecipe(companyId: String, recipe: Recipe)
    suspend fun updateRecipe(companyId: String, recipe: Recipe)
    suspend fun deleteRecipe(companyId: String, recipeId: String)
    suspend fun getRecipesUsingIngredient(companyId: String, ingredientId: String): List<IngredientUsage>
}
