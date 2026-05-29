package com.bakery.erp.model

data class ProductionLot(
    val id: String = "",
    val producingBranchId: String = "",
    val producingBranchName: String = "",
    val shiftId: String = "",
    val shiftName: String = "",
    val productionDate: Long = 0L,
    val status: String = ProductionLotStatus.OPEN,
    val notes: String? = null,
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val stockDeducted: Boolean = false,
    val lineCount: Int = 0,
    val sourceOrderIds: List<String> = emptyList(),
    /**
     * IDs de [CustomerOrder] consolidados en este lote (revendedor / encargo retail / pastel).
     * Lista separada de [sourceOrderIds] para mantener retrocompatibilidad y permitir
     * queries `where-in` específicas por canal.
     */
    val customerOrderIds: List<String> = emptyList(),
    val lines: List<ProductionLotLine> = emptyList()
)

object ProductionLotStatus {
    const val OPEN        = "OPEN"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETED   = "COMPLETED"
    const val CANCELLED   = "CANCELLED"

    fun label(status: String) = when (status) {
        OPEN        -> "Abierto"
        IN_PROGRESS -> "En producción"
        COMPLETED   -> "Completado"
        CANCELLED   -> "Cancelado"
        else        -> "Desconocido"
    }
}
