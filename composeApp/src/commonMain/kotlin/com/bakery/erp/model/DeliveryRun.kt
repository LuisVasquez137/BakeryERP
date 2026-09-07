package com.bakery.erp.model

data class DeliveryRun(
    val id: String = "",
    val originBranchId: String = "",
    val originBranchName: String = "",
    val driverUserId: String = "",
    val driverName: String = "",
    /** Día planeado para el reparto (millis al inicio del día, TZ local). */
    val plannedDate: Long = 0L,
    val status: String = DeliveryRunStatus.PENDING,
    /** ID del activo fijo (categoría Vehículos) usado en este viaje. Vacío si aún no se asigna. */
    val vehicleAssetId: String = "",
    /** Descripción denormalizada del vehículo (nombre del activo + placa/modelo). Se llena al
     *  seleccionar el activo en el form; mostrar este campo en UI evita ir al activo cada vez. */
    val vehicleInfo: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    /** Notas de despacho que carga este viaje. */
    val dispatchNoteIds: List<String> = emptyList(),
    /** Contador denormalizado (= dispatchNoteIds.size) para listar rápido. */
    val stopCount: Int = 0,
    val notes: String? = null,
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

object DeliveryRunStatus {
    const val PENDING     = "PENDING"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETED   = "COMPLETED"
    const val CANCELLED   = "CANCELLED"

    fun label(status: String) = when (status) {
        PENDING     -> "Por salir"
        IN_PROGRESS -> "En ruta"
        COMPLETED   -> "Completado"
        CANCELLED   -> "Cancelado"
        else        -> "Desconocido"
    }

    fun isTerminal(status: String) = status in listOf(COMPLETED, CANCELLED)
}
