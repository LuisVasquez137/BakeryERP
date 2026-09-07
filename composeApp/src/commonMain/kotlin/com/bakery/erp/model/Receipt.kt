package com.bakery.erp.model

/**
 * Datos para construir un ticket de venta imprimible. Snapshot — independiente de Sale para
 * que el formato del recibo no se acople a cambios futuros en el modelo de venta.
 */
data class ReceiptData(
    val companyName: String,
    val branchName: String,
    val saleId: String,
    val dateLabel: String,       // ya formateado "22/05/2026 14:35"
    val cashierName: String,
    val registerName: String,
    val lines: List<ReceiptLine>,
    val subtotal: Double,
    val discount: Double,
    val total: Double,
    val paymentMethodLabel: String,
    val tenderedAmount: Double,
    val changeAmount: Double,
    val footerMessage: String = "¡Gracias por su compra!"
)

data class ReceiptLine(
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val subtotal: Double
)
