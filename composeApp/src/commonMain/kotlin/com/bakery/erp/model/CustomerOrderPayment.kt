package com.bakery.erp.model

/**
 * Pago parcial registrado contra un [CustomerOrder].
 *
 * Tipos:
 *  - ADVANCE: anticipo inicial al hacer el encargo.
 *  - DEPOSIT_BASE: depósito en garantía por la base/fuente (reembolsable, pasteles).
 *  - EXTRA: pago intermedio adicional.
 *  - BALANCE: saldo final al momento de entrega (típicamente cobrado en POS).
 *
 * Si el pago se hizo desde el POS, [saleId] vincula la transacción para trazabilidad.
 */
data class CustomerOrderPayment(
    val id: String = "",
    val orderId: String = "",
    val type: String = CustomerOrderPaymentType.ADVANCE,
    val amount: Double = 0.0,
    /** CASH | CARD | TRANSFER. */
    val method: String = CustomerOrderPaymentMethod.CASH,
    /** Vacío si el pago no se procesó por POS. */
    val saleId: String = "",
    val cashSessionId: String = "",
    val branchId: String = "",
    val receivedByUserId: String = "",
    val receivedByUserName: String = "",
    val receivedAt: Long = 0L,
    val notes: String = "",
    /** Anticipo de caja que lo originó ([CustomerDeposit]): el dinero entró por ahí. */
    val depositId: String = "",
    val depositFolio: String = "",
)

object CustomerOrderPaymentType {
    const val ADVANCE = "ADVANCE"
    const val DEPOSIT_BASE = "DEPOSIT_BASE"
    const val EXTRA = "EXTRA"
    const val BALANCE = "BALANCE"

    fun label(type: String) = when (type) {
        ADVANCE -> "Anticipo"
        DEPOSIT_BASE -> "Depósito de base"
        EXTRA -> "Pago intermedio"
        BALANCE -> "Saldo"
        else -> "Pago"
    }
}

object CustomerOrderPaymentMethod {
    const val CASH = "CASH"
    const val CARD = "CARD"
    const val TRANSFER = "TRANSFER"

    fun label(method: String) = when (method) {
        CASH -> "Efectivo"
        CARD -> "Tarjeta"
        TRANSFER -> "Transferencia"
        else -> "Otro"
    }
}
