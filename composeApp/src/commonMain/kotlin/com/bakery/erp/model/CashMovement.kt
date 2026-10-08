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
    /** Colaborador que lo anotó, identificado con PIN en un equipo de sucursal. */
    val createdByEmployeeId: String = "",
    val createdAt: Long = 0L,
    /** Si forma parte de un [CashTransfer] entre cajas, el id del transfer (mismo en ambos espejos). */
    val transferId: String? = null,
    /** Si forma parte de un transfer, la sesión contraparte (origen si este es IN, destino si es OUT). */
    val linkedSessionId: String? = null,
    /** Si fue creado por un cuadre sorpresa con FORCE_ADJUST, el id del [SpotCashCheck]. */
    val spotCheckId: String? = null,
    /**
     * Compra que provocó este movimiento, cuando el gasto se anotó desde la caja.
     *
     * Es lo que permite ir del renglón de la gaveta —"¿por qué me faltan Q325?"— al documento
     * que lo explica, para corregirle el monto o borrarlo si no salió de esa caja. Sin el
     * vínculo, el movimiento es un texto suelto y lo único que quedaba era cuadrar a mano.
     * Vacío = movimiento capturado a mano (retiro, depósito, traslado).
     */
    val sourcePurchaseId: String = "",
    /**
     * Movimiento al que este corrige o revierte.
     *
     * Los gastos se ligan por [sourcePurchaseId] porque detrás hay un documento; un ingreso no
     * tiene más que a sí mismo, y sin este vínculo la corrección quedaría como un movimiento
     * suelto que nadie puede emparejar con lo que corrigió. Vacío = movimiento original.
     */
    val sourceMovementId: String = "",
    /**
     * Movimiento de crédito que lo causó: el abono de una cuenta que entró en efectivo, o el
     * adelanto a un colaborador que salió de la gaveta. Estos no se corrigen desde la lista de
     * la caja: se corrigen en la cuenta, que es donde el saldo tiene que cuadrar.
     */
    val sourceCreditMovementId: String = "",
    /**
     * Anticipo de cliente que lo causó: la entrada al recibirlo o la salida al devolverlo. No se
     * corrige desde la lista de la caja: se corrige en el anticipo, que es lo que el cliente
     * tiene en su comprobante.
     */
    val sourceDepositId: String = ""
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

    /**
     * Cómo se llama el movimiento **en la lista de la caja**, que es donde alguien busca por
     * qué no le cuadra el efectivo.
     *
     * "Depósito" no dice nada cuando el dinero volvió porque se eliminó o se corrigió un gasto:
     * quien lee la lista se pregunta quién depositó y por qué. El texto sale del motivo, que ya
     * distingue los dos casos ("Eliminado: …" / "Corrección: …").
     */
    fun listLabel(movement: CashMovement): String = when {
        movement.sourcePurchaseId.isNotEmpty() && movement.reason.startsWith("Eliminado:") ->
            "Devuelto — gasto eliminado"
        movement.sourcePurchaseId.isNotEmpty() && movement.reason.startsWith("Corrección:") ->
            "Ajuste — gasto corregido"
        movement.type == EXPENSE && movement.sourcePurchaseId.isNotEmpty() ->
            "Gasto pagado de la caja"
        movement.spotCheckId != null -> "Ajuste por cuadre sorpresa"
        movement.sourceCreditMovementId.isNotEmpty() && movement.type == DEPOSIT -> "Abono a crédito"
        movement.sourceCreditMovementId.isNotEmpty() && movement.type == WITHDRAWAL -> "Adelanto a colaborador"
        movement.type == FUND_ADDITION -> "Ingreso desde caja general"
        movement.type == DEPOSIT -> "Ingreso — otro origen"
        else -> label(movement.type)
    }

    /** Signo del movimiento sobre el efectivo esperado. */
    fun sign(type: String): Int = when (type) {
        DEPOSIT, FUND_ADDITION, TRANSFER_IN -> +1
        WITHDRAWAL, EXPENSE, TRANSFER_OUT   -> -1
        else                                -> 0
    }
}
