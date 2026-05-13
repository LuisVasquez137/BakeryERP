package com.bakery.erp.model

data class RecipeComponent(
    val id: String = "",
    val stageId: String = "",
    val ingredientId: String = "",
    val ingredientName: String = "",
    val ingredientUnit: String = "",
    val percentage: Double = 0.0,
    val isBase: Boolean = false,
    val order: Int = 0
)
