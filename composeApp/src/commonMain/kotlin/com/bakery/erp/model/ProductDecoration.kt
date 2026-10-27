package com.bakery.erp.model

/**
 * Adorno o relleno aplicado a un producto terminado (ej. concha sobre gusanito,
 * frijol en la sheca). Vive en [Product.decorations]. Exactamente UNA de las dos
 * fuentes está seteada:
 *
 *  - [preparationId]: algo que preparamos nosotros (concha, esponja de relleno).
 *    Se consume de un [PreparationBatch] — hay que producir el lote de relleno
 *    antes de poder producir el pan.
 *  - [ingredientId]: algo que compramos ya hecho (relleno de higo/queso crema en
 *    manga, mermeladas). Se consume directo del `BranchStock` del insumo, igual
 *    que la harina de la receta — sin paso de "producir" intermedio.
 *
 * Si la preparación es un bizcocho ([PreparationUsage.BASE]), en vez de gramos va
 * [unitsPerProduct]: el pastel de frutas lleva 1, el pastelito de porción 1/24 de plancha.
 */
data class ProductDecoration(
    val preparationId: String = "",
    val preparationName: String = "",
    val ingredientId: String = "",
    val ingredientName: String = "",
    val gramsPerUnit: Double = 0.0,
    /** Bizcochos por producto (1, o 1/24 de plancha). 0 = se mide en [gramsPerUnit]. */
    val unitsPerProduct: Double = 0.0
) {
    val isIngredientSourced: Boolean get() = ingredientId.isNotEmpty()
    val isCounted: Boolean get() = unitsPerProduct > 0.0
    /** Tiene su cantidad puesta (sin ella se descartaba en silencio al guardar). */
    val hasAmount: Boolean get() = gramsPerUnit > 0.0 || unitsPerProduct > 0.0
}
