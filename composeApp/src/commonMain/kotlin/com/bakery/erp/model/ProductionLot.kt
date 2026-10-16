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
    /**
     * Quién lo inició y quién lo cerró. Son personas distintas a [createdByUserName] (quien
     * armó el lote) y en un equipo de planta, el colaborador que entró con su PIN.
     */
    val startedByUserId: String = "",
    val startedByUserName: String = "",
    val startedByEmployeeId: String = "",
    val completedByUserId: String = "",
    val completedByUserName: String = "",
    val completedByEmployeeId: String = "",
    val stockDeducted: Boolean = false,
    val lineCount: Int = 0,
    val sourceOrderIds: List<String> = emptyList(),
    /**
     * `true` cuando este lote nació de un reparto por turnos
     * (`ProductionViewModel.saveShiftSplitLots`): dos lotes hermanos comparten
     * [sourceOrderIds], y cada uno produce solo una porción de cada pedido.
     *
     * Es el dato que le dice a `createDispatchNotesForLot` que debe despachar el
     * desglose de [ProductionLotLine.sourceOrderAllocations] y NO la cantidad
     * pedida completa. Antes eso se deducía mirando si alguna línea traía
     * desglose, y esa heurística fallaba justo en el peor sentido: un lote
     * repartido cuyas líneas perdieron el desglose (recomputadas al editar un
     * pedido, o creadas después) se trataba como lote normal y **cada turno
     * despachaba el pedido entero** — el pan salía duplicado en las dos notas.
     */
    val isShiftSplit: Boolean = false,
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
