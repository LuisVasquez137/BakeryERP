package com.bakery.erp.domain

import com.bakery.erp.model.BranchStock
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.StockMovement

interface IngredientRepository {
    suspend fun getIngredients(companyId: String): List<Ingredient>
    suspend fun addIngredient(companyId: String, ingredient: Ingredient)
    suspend fun updateIngredient(companyId: String, ingredient: Ingredient)
    suspend fun deleteIngredient(companyId: String, ingredientId: String)

    // Stock por sucursal
    suspend fun getBranchStocksForBranch(companyId: String, branchId: String): Map<String, Double>
    suspend fun getBranchStocksForIngredient(companyId: String, ingredientId: String, branchIds: List<String>): List<BranchStock>
    suspend fun updateBranchStock(companyId: String, ingredientId: String, ingredientName: String, branchId: String, branchName: String, delta: Double)

    // Kardex
    suspend fun addStockMovement(companyId: String, ingredientId: String, movement: StockMovement)
    suspend fun getStockMovements(companyId: String, ingredientId: String): List<StockMovement>

    // Precio de presentación de compra
    suspend fun updatePurchaseFormatCost(companyId: String, ingredientId: String, formatId: String, newPackageCost: Double)
}
