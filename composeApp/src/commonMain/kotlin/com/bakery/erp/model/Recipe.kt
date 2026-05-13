package com.bakery.erp.model

data class Recipe(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val category: String = "",
    val inputMode: String = "PERCENTAGE",
    val stageCount: Int = 0,
    val yieldCount: Int = 0,
    val notes: String? = null,
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val stages: List<RecipeStage> = emptyList(),
    val yields: List<RecipeYield> = emptyList()
)

val RECIPE_CATEGORIES = listOf("Pan Principal", "Cubierta", "Relleno")
