package com.bakery.erp.model

/**
 * Snapshot del adorno de un producto al crear la línea del lote.
 *
 * Si es un adorno de [PreparationBatch] (algo que preparamos), captura la regla
 * del producto ([gramsPerUnit]) y el batch elegido ([batchId]/[batchName]) para no
 * releerlos al completar el lote. Si es un insumo comprado ya hecho ([ingredientId]
 * no vacío), no hay batch que elegir: se descuenta directo del `BranchStock` junto
 * con el resto de la receta, y [batchId]/[batchName] quedan vacíos.
 *
 * Un bizcocho ([unitsPerProduct] > 0) tampoco elige batch: al completar se toman los más
 * viejos de la planta (`PreparationBatchRepository.consumeUnits`).
 */
data class ProductionLotDecoration(
    val preparationId: String = "",
    val preparationName: String = "",
    val ingredientId: String = "",
    val ingredientName: String = "",
    val gramsPerUnit: Double = 0.0,
    val batchId: String = "",
    val batchName: String = "",
    val unitsPerProduct: Double = 0.0
) {
    val isIngredientSourced: Boolean get() = ingredientId.isNotEmpty()
    val isCounted: Boolean get() = unitsPerProduct > 0.0
}
