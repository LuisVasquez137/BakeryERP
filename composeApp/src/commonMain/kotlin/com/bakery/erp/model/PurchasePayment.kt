package com.bakery.erp.model

/**
 * Abono a una factura de compra. Vive en `purchases/{id}/payments/{id}`.
 *
 * Se guardan los pagos y NO solo el saldo porque "¿ya le pagué a Molinos?" casi nunca se
 * contesta con un número: se contesta con "sí, el cheque 1042 del 12 de agosto". El
 * `Purchase.paidAmount` es la suma denormalizada, para que la lista no tenga que abrir
 * esta subcolección en cada factura.
 */
data class PurchasePayment(
    val id: String = "",
    val amount: Double = 0.0,
    val date: Long = 0L,
    val method: String = PurchasePaymentMethod.CASH,
    val reference: String = "",      // no. de cheque, boleta de depósito, transferencia
    val notes: String? = null,
    val createdById: String = "",
    val createdByName: String = "",
    val createdAt: Long = 0L
)

object PurchasePaymentMethod {
    const val CASH = "CASH"
    const val TRANSFER = "TRANSFER"
    const val CHECK = "CHECK"
    const val CARD = "CARD"
    const val OTHER = "OTHER"

    val all = listOf(CASH, TRANSFER, CHECK, CARD, OTHER)

    fun label(method: String) = when (method) {
        CASH -> "Efectivo"
        TRANSFER -> "Transferencia"
        CHECK -> "Cheque"
        CARD -> "Tarjeta"
        OTHER -> "Otro"
        else -> "Otro"
    }

    /** El número de cheque o de boleta es el dato que después se busca; en efectivo estorba. */
    fun requiresReference(method: String) = method == CHECK || method == TRANSFER
}
