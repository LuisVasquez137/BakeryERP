package com.bakery.erp.model

/**
 * Una sesión de caja: apertura+cierre de una [CashRegister] por un cajero específico.
 * Mientras [status] sea OPEN solo ese cajero puede operar la caja.
 *
 * Los totales [totalCashSales] / [totalCardSales] / [totalTransferSales] / [totalSales] / [saleCount]
 * son denormalizados — se incrementan al confirmar cada venta para evitar query N+1 al cerrar.
 */
data class CashSession(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val registerId: String = "",
    val registerName: String = "",
    val cashierId: String = "",
    val cashierName: String = "",
    val openedAt: Long = 0L,
    val closedAt: Long? = null,
    /** Fondo de cambio inicial. */
    val openingBalance: Double = 0.0,
    val totalCashSales: Double = 0.0,
    val totalCardSales: Double = 0.0,
    val totalTransferSales: Double = 0.0,
    val totalSales: Double = 0.0,
    val totalMovementsIn: Double = 0.0,
    val totalMovementsOut: Double = 0.0,
    /** Calculado al cerrar: openingBalance + totalCashSales + movsIn - movsOut. */
    val expectedCash: Double = 0.0,
    val countedCash: Double = 0.0,
    /** countedCash - expectedCash. Negativo = faltante, positivo = sobrante. */
    val difference: Double = 0.0,
    val status: String = CashSessionStatus.OPEN,
    val saleCount: Int = 0,
    val notes: String? = null,
    /** Si la sesión fue incluida en un cierre del día (`DailyCashClose`), su id. Bloquea ediciones. */
    val dailyCloseId: String? = null
)

object CashSessionStatus {
    const val OPEN   = "OPEN"
    const val CLOSED = "CLOSED"

    fun label(status: String) = when (status) {
        OPEN   -> "Abierta"
        CLOSED -> "Cerrada"
        else   -> "Desconocido"
    }
}
