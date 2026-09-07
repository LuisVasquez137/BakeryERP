package com.bakery.erp.model

/** Cabecera de venta — una transacción del POS. */
data class Sale(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val registerId: String = "",
    val registerName: String = "",
    val sessionId: String = "",
    val cashierId: String = "",
    val cashierName: String = "",
    val subtotal: Double = 0.0,
    /** Descuento total aplicado a la venta. 0 en MVP — placeholder Fase 2. */
    val discount: Double = 0.0,
    val total: Double = 0.0,
    val paymentMethod: String = SalePaymentMethod.CASH,
    /** Monto entregado por el cliente (solo efectivo; en tarjeta/transferencia = total). */
    val tenderedAmount: Double = 0.0,
    /** Vuelto en efectivo entregado al cliente (0 en tarjeta/transferencia). */
    val changeAmount: Double = 0.0,
    val status: String = SaleStatus.COMPLETED,
    /** Denormalizado para listas; igual a lines.size. */
    val lineCount: Int = 0,
    /**
     * Si la venta es un cobro de anticipo o saldo de un [CustomerOrder], ID del pedido.
     * Permite que el POS cobre el saldo de un pastel vinculándolo al encargo.
     */
    val linkedCustomerOrderId: String = "",
    val createdAt: Long = 0L
)

object SalePaymentMethod {
    const val CASH     = "CASH"
    const val CARD     = "CARD"
    const val TRANSFER = "TRANSFER"

    fun label(method: String) = when (method) {
        CASH     -> "Efectivo"
        CARD     -> "Tarjeta"
        TRANSFER -> "Transferencia"
        else     -> "Desconocido"
    }

    val all = listOf(CASH, CARD, TRANSFER)
}

object SaleStatus {
    const val COMPLETED = "COMPLETED"
    /** Reservado para Fase 2 (anulación post-cobro). */
    const val VOIDED    = "VOIDED"

    fun label(status: String) = when (status) {
        COMPLETED -> "Completada"
        VOIDED    -> "Anulada"
        else      -> "Desconocido"
    }
}
