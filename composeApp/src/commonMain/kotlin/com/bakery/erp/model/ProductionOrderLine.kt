package com.bakery.erp.model

data class ProductionOrderLine(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val requestedQuantity: Double = 0.0,
    val productUnit: String = ""
)
