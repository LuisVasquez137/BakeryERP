package com.bakery.erp.model

enum class RecipeStageType(val displayName: String) {
    PREFERMENTO("Pre-fermento"),
    MASA_PRINCIPAL("Masa Principal"),
    RELLENO("Relleno"),
    CUBIERTA("Cubierta"),
    OTRO("Otro")
}

data class RecipeStage(
    val id: String = "",
    val name: String = "",
    val order: Int = 0,
    val type: String = "MASA_PRINCIPAL",
    val instructions: String? = null,
    val restTimeMinutes: Int? = null,
    val notes: String? = null,
    val components: List<RecipeComponent> = emptyList(),
    val steps: List<RecipeStep> = emptyList()
)
