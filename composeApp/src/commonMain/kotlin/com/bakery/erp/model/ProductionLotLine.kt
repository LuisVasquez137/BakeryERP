package com.bakery.erp.model

data class ProductionLotLine(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val productUnit: String = "",
    val rawWeightGrams: Double? = null,
    val plannedQuantity: Double = 0.0,
    val producedQuantity: Double = 0.0,
    val recipeId: String = "",
    val recipeName: String = "",
    // Esponja opcional: si el panadero declara usar esponja, se descuenta del batch elegido
    // y los ingredientes que aporta se restan de la masa al cerrar el lote.
    val spongeBatchId: String = "",
    val spongeBatchName: String = "",
    val spongeGramsUsed: Double = 0.0,
    // Snapshot de los adornos del producto al crear la línea, con el batch elegido.
    val decorationsSnapshot: List<ProductionLotDecoration> = emptyList()
)
