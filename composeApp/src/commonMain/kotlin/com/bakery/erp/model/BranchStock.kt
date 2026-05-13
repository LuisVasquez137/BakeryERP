package com.bakery.erp.model

data class BranchStock(
    val id: String = "",            // "{branchId}_{ingredientId}"
    val branchId: String = "",
    val branchName: String = "",
    val ingredientId: String = "",
    val ingredientName: String = "",
    val currentStock: Double = 0.0,
    val updatedAt: Long = 0L
)
