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
 */
data class ProductDecoration(
    val preparationId: String = "",
    val preparationName: String = "",
    val ingredientId: String = "",
    val ingredientName: String = "",
    val gramsPerUnit: Double = 0.0
) {
    val isIngredientSourced: Boolean get() = ingredientId.isNotEmpty()
}
