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

/**
 * Efectivo del día de una sucursal a partir de sus turnos.
 *
 * Con la cadena de turnos ([ShiftHandover]) la gaveta pasa de un turno al siguiente: sumar lo
 * contado en cada turno contaba dos veces lo que se entregó, y el cierre del día pedía repartir
 * dinero que no existía. Lo que hay al final es lo que dejó el ÚLTIMO turno de cada caja (el que
 * nadie recibió), ya sin lo que salió al banco o se retiró al cerrar. La diferencia sí se suma:
 * cada turno responde por la suya.
 */
data class DayCashTotals(
    /** Lo que debería haber en las gavetas al final: [counted] menos las diferencias. */
    val expected: Double,
    /** Efectivo que quedó en las gavetas al cerrar el último turno de cada caja. */
    val counted: Double,
    /** Suma de las diferencias (faltante/sobrante) de cada turno. */
    val difference: Double,
    /** Lo que salió al cerrar turnos (al banco o retiro): ya no está en las gavetas. */
    val takenAtShiftClose: Double
)

/** [closed]: turnos cerrados del día; [all]: todos los del día, para saber cuáles se entregaron. */
fun dayCashTotals(closed: List<CashSession>, all: List<CashSession> = closed): DayCashTotals {
    val handedOver = all.mapNotNull { it.previousSessionId.takeIf { id -> id.isNotEmpty() } }.toSet()
    val counted = dayMoney(closed.filter { it.id !in handedOver }
        .sumOf { if (it.handoverRecorded) it.carriedOutCash else it.countedCash })
    val difference = dayMoney(closed.sumOf { it.difference })
    return DayCashTotals(
        expected = dayMoney(counted - difference),
        counted = counted,
        difference = difference,
        takenAtShiftClose = dayMoney(closed.sumOf { it.closingWithdrawal })
    )
}

private fun dayMoney(v: Double) = kotlin.math.round(v * 100) / 100
