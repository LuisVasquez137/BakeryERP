package com.bakery.erp.model

data class ProductionLotLine(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val productUnit: String = "",
    val rawWeightGrams: Double? = null,
    val plannedQuantity: Double = 0.0,
    val producedQuantity: Double = 0.0,
    val recipeId: String = "",
    val recipeName: String = ""
)
