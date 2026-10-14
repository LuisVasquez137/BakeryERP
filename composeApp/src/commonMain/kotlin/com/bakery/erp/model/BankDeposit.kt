package com.bakery.erp.model

import com.bakery.erp.util.fmt

/**
 * Efectivo de una gaveta que se lleva a depositar al banco, con la foto de la boleta como
 * comprobante.
 *
 * Existe para que el dinero termine en el banco y no en el bolsillo de nadie: un "retiro" no dice
 * a dónde fue el dinero; un depósito queda abierto hasta que alguien adjunta la boleta, y mientras
 * esté abierto esa caja no puede cerrar turno.
 *
 * **Va en dos pasos** porque así pasa en la realidad: el dinero sale de la gaveta cuando la cajera
 * se va al banco (y en ese momento se descuenta, o la caja descuadra mientras ella no está) y la
 * boleta llega cuando vuelve.
 *
 * - **A medio turno**: el dinero sale por un [CashMovement] tipo [CashMovementType.BANK_DEPOSIT]
 *   ([movementId]); el esperado de la gaveta baja en ese momento.
 * - **Al cerrar** ([atClose]): sale como el retiro del cierre (`CashSession.closingWithdrawal`), que
 *   ya descuenta de lo que se entrega; no hay movimiento.
 *
 * `companies/{c}/bankDeposits/{id}`, plana: los pendientes se buscan por caja ([registerId]) sin
 * importar el turno, porque un depósito del cierre de anoche se completa en el turno de hoy.
 * Historial: no se borra (reglas). Ver `arquitectura/pos-ventas.md`.
 */
data class BankDeposit(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val registerId: String = "",
    val registerName: String = "",
    /** Turno del que salió el dinero. */
    val sessionId: String = "",
    /** Movimiento de caja que lo sacó de la gaveta (a medio turno). Vacío si fue al cerrar. */
    val movementId: String = "",
    /** Salió con el cierre del turno (retiro del cierre), no a medio turno. */
    val atClose: Boolean = false,
    val amount: Double = 0.0,
    val bankName: String = "",
    /** Colaborador que lleva el dinero al banco. */
    val carriedByEmployeeId: String = "",
    val carriedByEmployeeName: String = "",
    /**
     * Quien lo lleva escribió su PIN al salir (equipo de sucursal con PIN). Así consta que esa
     * persona aceptó llevarlo, no solo que alguien la escogió de la lista.
     */
    val carrierConfirmedWithPin: Boolean = false,
    val notes: String = "",
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val createdByEmployeeId: String = "",
    val createdAt: Long = 0L,
    val status: String = BankDepositStatus.PENDING,

    // ── Boleta ─────────────────────────────────────────────────────────────
    val slipPhotoUrl: String = "",
    val slipNumber: String = "",
    /** Lo que dice la boleta. Si no es igual a [amount], la diferencia se ve en rojo. */
    val slipAmount: Double = 0.0,
    val slipAttachedAt: Long = 0L,
    val slipAttachedByUserName: String = "",
    val slipAttachedByEmployeeId: String = "",

    // ── Sin boleta (se cerró igual, con motivo; al dueño le llega el aviso) ──
    val missingSlipReason: String = "",
    val missingSlipAt: Long = 0L,
    val missingSlipByUserName: String = ""
)

object BankDepositStatus {
    /** Salió de la gaveta y todavía no hay boleta. Bloquea el cierre de turno de esa caja. */
    const val PENDING = "PENDING"
    /** Boleta adjunta. */
    const val SLIP_ATTACHED = "SLIP_ATTACHED"
    /** Se dejó sin boleta con un motivo (se perdió, el banco no la dio…). Ya no bloquea. */
    const val NO_SLIP = "NO_SLIP"
    /** No llegó a salir de la gaveta: falló el movimiento de caja al crearlo. */
    const val CANCELLED = "CANCELLED"

    fun label(status: String) = when (status) {
        PENDING -> "Sin boleta"
        SLIP_ATTACHED -> "Con boleta"
        NO_SLIP -> "Cerrado sin boleta"
        CANCELLED -> "Cancelado"
        else -> status
    }
}

val BankDeposit.isPending: Boolean get() = status == BankDepositStatus.PENDING

/** Boleta menos lo que salió de la gaveta: negativo = se depositó menos. 0 sin boleta. */
val BankDeposit.slipDifference: Double
    get() = if (status == BankDepositStatus.SLIP_ATTACHED) slipAmount - amount else 0.0

val BankDeposit.slipMismatch: Boolean get() = kotlin.math.abs(slipDifference) > 0.004

/** Bancos que se ofrecen como botones; se puede escribir otro. */
val COMMON_BANKS = listOf("Banrural", "Banco Industrial", "BAM", "G&T Continental", "Banco de los Trabajadores")

/** Mínimo de letras del motivo para dejar un depósito sin boleta. */
const val MIN_MISSING_SLIP_REASON = 5

/** Qué impide sacar [amount] de la gaveta para depositar, o null si está bien. */
fun bankDepositProblem(amount: Double, cashInDrawer: Double, bankName: String, carriedBy: String): String? = when {
    amount <= 0.004 -> "Escribe cuánto se lleva al banco."
    amount > cashInDrawer + 0.004 -> "No hay tanto en la gaveta (se esperan Q ${cashInDrawer.fmt(2)})."
    bankName.isBlank() -> "¿A qué banco?"
    carriedBy.isBlank() -> "¿Quién lo lleva?"
    else -> null
}

/** Qué impide adjuntar la boleta, o null si está bien. */
fun bankSlipProblem(hasPhoto: Boolean, slipNumber: String, slipAmount: Double?): String? = when {
    !hasPhoto -> "Toma la foto de la boleta."
    slipNumber.isBlank() -> "Escribe el número de la boleta."
    slipAmount == null || slipAmount <= 0.004 -> "Escribe el monto que dice la boleta."
    else -> null
}
