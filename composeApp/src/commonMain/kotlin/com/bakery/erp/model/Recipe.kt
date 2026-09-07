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
    /**
     * Harina base mínima que la amasadora puede trabajar, en gramos. `0` = sin
     * mínimo.
     *
     * El límite es de la máquina, no del producto: por debajo de cierta cantidad
     * el gancho no alcanza la masa y no amasa. Por eso vive en la **receta** y no
     * en el producto — de una misma masa pueden salir varios panes, y lo que
     * cuenta para la amasadora es la suma de todos ellos.
     *
     * Se compara contra la harina base (`masa total × 100 ÷ Σ porcentajes`), que
     * es la misma cifra que muestra la receta escalada al panadero.
     */
    val minBatchFlourGrams: Double = 0.0,
    /**
     * Tiempo de amasado en segundos. `0` = sin tiempo configurado (no muestra
     * timer en la Lista de Producción). Vive en la receta —no en el
     * producto— por la misma razón que [minBatchFlourGrams]: la amasada es
     * una sola para todos los panes que salen de esa masa.
     */
    val kneadingTimeSeconds: Int = 0,
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val components: List<RecipeComponent> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val stages: List<RecipeStage> = emptyList(),
    val yields: List<RecipeYield> = emptyList()
) {
    val allComponents: List<RecipeComponent>
        get() = if (components.isNotEmpty()) components
                else stages.flatMap { it.components }
    val allSteps: List<RecipeStep>
        get() = if (steps.isNotEmpty()) steps
                else stages.flatMap { it.steps }
}

val RECIPE_CATEGORIES = listOf("Pan Principal", "Cubierta", "Relleno")
