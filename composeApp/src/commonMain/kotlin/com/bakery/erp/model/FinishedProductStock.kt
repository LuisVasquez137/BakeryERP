package com.bakery.erp.model

data class FinishedProductStock(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val productId: String = "",
    val productName: String = "",
    val currentStock: Double = 0.0,
    /** Costo promedio ponderado del inventario actual (precio mayorista pagado). */
    val avgUnitCost: Double = 0.0,
    val updatedAt: Long = 0L
)

fun finishedStockDocId(branchId: String, productId: String) = "${branchId}_$productId"
