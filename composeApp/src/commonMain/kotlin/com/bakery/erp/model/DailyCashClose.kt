package com.bakery.erp.model

/**
 * Cierre consolidado del día por sucursal. Agrupa todas las [CashSession]
 * CLOSED del día calendario en una sucursal y materializa:
 *   - cuánto efectivo se lleva el owner ([depositedAmount] + subcolección `depositDenoms/`)
 *   - cuánto sencillo queda como fondo para mañana ([leftForNextDay] + subcolección `leftDenoms/`)
 *
 * Una vez CLOSED es inmutable; cualquier corrección posterior va por
 * movimientos de ajuste, nunca reabriendo el cierre.
 */
data class DailyCashClose(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    /** yyyy-MM-dd en TZ local (no millis), evita drifts. */
    val closeDate: String = "",
    val sessionIds: List<String> = emptyList(),
    val totalCashSales: Double = 0.0,
    val totalCardSales: Double = 0.0,
    val totalTransferSales: Double = 0.0,
    val totalPedidosYaSales: Double = 0.0,
    val totalSales: Double = 0.0,
    val totalMovementsIn: Double = 0.0,
    val totalMovementsOut: Double = 0.0,
    val totalExpectedCash: Double = 0.0,
    val totalCountedCash: Double = 0.0,
    /** countedCash − expectedCash sumado (informativo). */
    val totalDifference: Double = 0.0,
    val leftForNextDay: Double = 0.0,
    val depositedAmount: Double = 0.0,
    val status: String = DailyCashCloseStatus.OPEN,
    val closedByUserId: String = "",
    val closedByUserName: String = "",
    val closedAt: Long? = null,
    val notes: String? = null
)

object DailyCashCloseStatus {
    const val OPEN   = "OPEN"
    const val CLOSED = "CLOSED"

    fun label(status: String) = when (status) {
        OPEN   -> "Borrador"
        CLOSED -> "Cerrado"
        else   -> "Desconocido"
    }
}
