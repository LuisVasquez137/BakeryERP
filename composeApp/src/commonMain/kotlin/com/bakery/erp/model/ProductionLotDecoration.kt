package com.bakery.erp.model

/**
 * Snapshot del adorno de un producto al crear la línea del lote.
 *
 * Si es un adorno de [PreparationBatch] (algo que preparamos), captura la regla
 * del producto ([gramsPerUnit]) y el batch elegido ([batchId]/[batchName]) para no
 * releerlos al completar el lote. Si es un insumo comprado ya hecho ([ingredientId]
 * no vacío), no hay batch que elegir: se descuenta directo del `BranchStock` junto
 * con el resto de la receta, y [batchId]/[batchName] quedan vacíos.
 */
data class ProductionLotDecoration(
    val preparationId: String = "",
    val preparationName: String = "",
    val ingredientId: String = "",
    val ingredientName: String = "",
    val gramsPerUnit: Double = 0.0,
    val batchId: String = "",
    val batchName: String = ""
) {
    val isIngredientSourced: Boolean get() = ingredientId.isNotEmpty()
}
