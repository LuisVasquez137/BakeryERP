package com.bakery.erp.model

/**
 * Un paso individual de preparación dentro de una etapa de receta.
 * Reemplaza al string newline-delimited de [RecipeStage.instructions], permitiendo
 * una foto opcional por paso. [RecipeStage.instructions] se conserva (texto unido)
 * solo por compatibilidad de lectura con recetas viejas.
 */
data class RecipeStep(
    val id: String = "",
    val stageId: String = "",
    val order: Int = 0,
    val text: String = "",
    val photoUrl: String? = null
)
