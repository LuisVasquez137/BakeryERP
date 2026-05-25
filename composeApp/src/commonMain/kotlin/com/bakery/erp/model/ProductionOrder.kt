package com.bakery.erp.model

data class ProductionOrder(
    val id: String = "",
    val sellingBranchId: String = "",
    val sellingBranchName: String = "",
    val producingBranchId: String = "",
    val producingBranchName: String = "",
    /** Turno de producción al que va dirigido el pedido. Una planta con dos turnos
     *  habilitados puede recibir un pedido por turno y por sucursal. Pedidos viejos
     *  (pre-feature de turnos) pueden tener esta cadena vacía. */
    val shiftId: String = "",
    val shiftName: String = "",
    val requestedDate: Long = 0L,
    val status: String = ProductionOrderStatus.PENDING,
    /**
     * `true` cuando el pedido se envió aún sabiendo que ya existía otro pedido para la
     * misma combinación pero ya está en un lote. Requiere aprobación explícita de la
     * planta productora antes de incorporarse al lote en curso.
     */
    val isLateOrder: Boolean = false,
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
