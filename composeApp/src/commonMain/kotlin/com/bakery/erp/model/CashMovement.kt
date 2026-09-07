package com.bakery.erp.model

/**
 * Movimiento de caja no asociado a una venta: retiros, depósitos, pagos pequeños, fondo extra.
 * [amount] es siempre positivo; el [type] define la dirección del flujo.
 */
data class CashMovement(
    val id: String = "",
    val sessionId: String = "",
    val type: String = CashMovementType.WITHDRAWAL,
    val amount: Double = 0.0,
    val reason: String = "",
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val createdAt: Long = 0L,
    /** Si forma parte de un [CashTransfer] entre cajas, el id del transfer (mismo en ambos espejos). */
    val transferId: String? = null,
    /** Si forma parte de un transfer, la sesión contraparte (origen si este es IN, destino si es OUT). */
    val linkedSessionId: String? = null,
    /** Si fue creado por un cuadre sorpresa con FORCE_ADJUST, el id del [SpotCashCheck]. */
    val spotCheckId: String? = null
)

object CashMovementType {
    /** Sale efectivo de la caja (entrega al dueño / banco). */
    const val WITHDRAWAL    = "WITHDRAWAL"
    /** Entra efectivo a la caja (no por venta — ej. pago anticipado de cliente). */
    const val DEPOSIT       = "DEPOSIT"
    /** Pago de gasto pequeño desde la caja (caja chica). */
    const val EXPENSE       = "EXPENSE"
    /** Se agregó más fondo de cambio durante el turno. */
    const val FUND_ADDITION = "FUND_ADDITION"
    /** Salida de efectivo hacia otra caja (espejo de TRANSFER_IN en la sesión destino). */
    const val TRANSFER_OUT  = "TRANSFER_OUT"
    /** Entrada de efectivo desde otra caja (espejo de TRANSFER_OUT en la sesión origen). */
    const val TRANSFER_IN   = "TRANSFER_IN"

    fun label(type: String) = when (type) {
        WITHDRAWAL    -> "Retiro"
        DEPOSIT       -> "Depósito"
        EXPENSE       -> "Gasto"
        FUND_ADDITION -> "Más fondo de cambio"
        TRANSFER_OUT  -> "Traslado a otra caja"
        TRANSFER_IN   -> "Recibido de otra caja"
        else          -> "Desconocido"
    }

    /** Signo del movimiento sobre el efectivo esperado. */
    fun sign(type: String): Int = when (type) {
        DEPOSIT, FUND_ADDITION, TRANSFER_IN -> +1
        WITHDRAWAL, EXPENSE, TRANSFER_OUT   -> -1
        else                                -> 0
    }
}
