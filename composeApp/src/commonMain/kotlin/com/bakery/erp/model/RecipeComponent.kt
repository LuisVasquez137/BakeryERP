package com.bakery.erp.model

data class RecipeComponent(
    val id: String = "",
    val stageId: String = "",
    val ingredientId: String = "",
    val ingredientName: String = "",
    val ingredientUnit: String = "",
    val percentage: Double = 0.0,
    /**
     * Cantidad tal como la escribió el usuario, en [ingredientUnit], cuando la
     * receta se ingresó por peso. `percentage` sigue siendo el dato canónico —
     * esto solo permite reabrir la receta y ver los mismos números que se
     * tecleron. Sin él, editar una receta por peso releía los porcentajes como
     * si fueran pesos y los degradaba en cada guardado. 0 = recetas por %.
     */
    val inputQuantity: Double = 0.0,
    val isBase: Boolean = false,
    val order: Int = 0
)
