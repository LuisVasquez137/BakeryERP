package com.bakery.erp.model

data class ProductionOrder(
    val id: String = "",
    val sellingBranchId: String = "",
    val sellingBranchName: String = "",
    val producingBranchId: String = "",
    val producingBranchName: String = "",
    val requestedDate: Long = 0L,
    val status: String = ProductionOrderStatus.PENDING,
    val notes: String? = null,
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lineCount: Int = 0,
    val lines: List<ProductionOrderLine> = emptyList()
)

object ProductionOrderStatus {
    const val PENDING   = "PENDING"
    const val CONFIRMED = "CONFIRMED"
    const val CANCELLED = "CANCELLED"

    fun label(status: String) = when (status) {
        PENDING   -> "Pendiente"
        CONFIRMED -> "Confirmado"
        CANCELLED -> "Cancelado"
        else      -> "Desconocido"
    }
}
