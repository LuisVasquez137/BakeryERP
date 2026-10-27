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
    card = session.expectedCardNow,
    transfer = session.expectedTransferNow
)

/**
 * Vouchers de tarjeta que debería haber en la gaveta ahora: los recibidos del turno anterior más
 * los cobrados en éste. Las ventas anuladas siguen adentro: su voucher se queda y se devuelve por
 * el banco ([CashSession.totalVoidedNonCash]).
 */
val CashSession.expectedCardNow: Double get() = money(carriedInCard + totalCardSales)

/** Comprobantes de transferencia que debería haber: los recibidos del turno anterior más los de éste. */
val CashSession.expectedTransferNow: Double get() = money(carriedInTransfer + totalTransferSales)

/**
 * Lo que se contó al cerrar de lo que no es efectivo. Null = no se ingresó (distinto de cero).
 *
 * Antes solo se contaba el efectivo y los vouchers pasaban de turno en turno sin que nadie los
 * sumara: un cobro con tarjeta registrado como efectivo (o al revés) no aparecía en ningún lado.
 * Las transferencias se revisan en el banco y PedidosYa en su app; PedidosYa no pasa de turno.
 */
data class NonCashCount(
    val cardVouchers: Double? = null,
    val transfers: Double? = null,
    val pedidosYa: Double? = null
)

/** Lo que se ingresó menos lo registrado en ventas. Negativo = falta, positivo = sobra. Null si no se ingresó. */
fun closeCountDifference(expected: Double, entered: Double?): Double? = entered?.let { money(it - expected) }

/**
 * Qué impide cerrar por un método que no es efectivo, o null. Es obligatorio si se cobró algo con
 * él; si no, se escribe solo si hay (un cobro registrado con otro método sale como sobrante).
 */
fun closeCountProblem(what: String, expected: Double, counted: Double?): String? = when {
    counted != null && counted < 0.0 -> "No puede ser negativo."
    expected > 0.004 && counted == null ->
        "Escribe $what (según las ventas son Q ${expected.toMoneyText()})."
    else -> null
}

/**
 * Cierre del día: qué impide dejar [left] de fondo para mañana, o null. Lo que no se deja se
 * retira o se va al banco.
 */
fun leftForTomorrowProblem(countedCash: Double, left: Double?): String? = when {
    left == null -> "Escribe cuánto efectivo se queda en la gaveta para mañana (0 si no se deja nada)."
    left < 0.0 -> "No puede ser negativo."
    left > countedCash + 0.004 -> "No se puede dejar más de lo que hay en la gaveta (Q ${countedCash.toMoneyText()})."
    else -> null
}

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
