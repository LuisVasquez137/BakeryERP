package com.bakery.erp.model

import com.bakery.erp.util.fmt
import kotlin.math.round

/**
 * Lo que un turno le entrega al siguiente de la misma caja (o deja para mañana).
 *
 * La gaveta es una sola a lo largo del día: el efectivo, los vouchers de tarjeta y los
 * comprobantes de transferencia pasan de turno a turno, y el cuadre final del día es donde se ve
 * que todo cuadre. Antes cada turno arrancaba de cero tecleando su fondo, y lo que se dejaba de un
 * día para otro no se comprobaba en ningún lado.
 */
data class ShiftHandover(
    /** Efectivo que queda en la gaveta: lo contado menos lo retirado. */
    val cash: Double,
    /** Vouchers de tarjeta acumulados del día: los que se recibieron más los de este turno. */
    val card: Double,
    val transfer: Double
)

/**
 * Qué entrega el turno al cerrar. Los vouchers y transferencias se acumulan (lo que ya traía más
 * lo cobrado en este turno); el efectivo es el contado —no el esperado—: lo que de verdad hay es
 * lo que recibe el siguiente, y la diferencia ya quedó registrada en este cierre.
 */
fun shiftHandover(session: CashSession, countedCash: Double, withdrawal: Double): ShiftHandover = ShiftHandover(
    cash = money((countedCash - withdrawal).coerceAtLeast(0.0)),
    card = money(session.carriedInCard + session.totalCardSales),
    transfer = money(session.carriedInTransfer + session.totalTransferSales)
)

/** Qué impide retirar [withdrawal] de lo contado, o null si está bien. */
fun closingWithdrawalProblem(countedCash: Double, withdrawal: Double): String? = when {
    withdrawal < 0.0 -> "El retiro no puede ser negativo."
    withdrawal > countedCash + 0.004 -> "No se puede retirar más de lo que hay en la gaveta (Q ${countedCash.toMoneyText()})."
    else -> null
}

/**
 * Lo que la caja debería tener al abrir según lo que dejó el turno anterior, o null si no hay
 * de dónde saberlo (primera vez, o el último cierre fue anterior a esto).
 */
fun expectedOpeningCash(lastClosed: CashSession?): Double? =
    lastClosed?.takeIf { it.status == CashSessionStatus.CLOSED && it.handoverRecorded }?.carriedOutCash

private fun money(v: Double) = round(v * 100) / 100
private fun Double.toMoneyText(): String = fmt(2)
