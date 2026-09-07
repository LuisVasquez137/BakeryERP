package com.bakery.erp.presentation.production

/**
 * Vista escalada de una receta para una línea de lote: muestra al panadero
 * exactamente cuánto pesar de cada ingrediente para la cantidad solicitada,
 * descontando lo que aporta la esponja si decidió usarla.
 */
data class ScaledRecipeView(
    val productName: String,
    val producedQuantity: Double,
    val productUnit: String,
    val rawWeightGrams: Double,
    val totalDoughGrams: Double,
    val rows: List<ScaledRecipeRow>,
    val sponge: ScaledSpongeContribution? = null,
    val decorations: List<ScaledDecoration> = emptyList()
)

data class ScaledRecipeRow(
    val ingredientId: String,
    val ingredientName: String,
    val percentage: Double,
    /** Gramos totales necesarios para el lote (antes de descontar esponja). */
    val totalGrams: Double,
    /** Gramos de este ingrediente que ya vienen en la esponja. */
    val gramsFromSponge: Double,
    /** Gramos que el panadero debe pesar aparte (totalGrams − gramsFromSponge). */
    val finalGrams: Double,
    val isBase: Boolean,
    /**
     * Cuántas piezas equivalen a [finalGrams] cuando el insumo se cuenta en vez
     * de pesarse. El panadero no pesa el huevo: lo cuenta. El peso manda para el
     * cálculo, pero en la mesa sirve leer "≈ 6 u". null si el insumo se pesa.
     */
    val countEquivalent: Double? = null,
    /** Unidad de conteo del insumo ("unidad" / "docena"). Vacío si se pesa. */
    val countUnit: String = ""
)

data class ScaledSpongeContribution(
    val preparationName: String,
    val gramsUsed: Double
)

data class ScaledDecoration(
    val preparationName: String,
    val gramsPerUnit: Double,
    val totalGrams: Double
)
