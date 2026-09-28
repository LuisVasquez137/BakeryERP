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
    val footerMessage: String = "¡Gracias por su compra!",
    /** Anticipo aplicado: el ticket lo resta del total y dice cuánto se pagó hoy. */
    val depositFolio: String = "",
    val depositAmount: Double = 0.0,
    /** Datos de la factura electrónica. Null = ticket sin factura (no se pidió o no salió aún). */
    val invoice: ReceiptInvoice? = null
)

/**
 * Lo que la SAT exige en la representación gráfica de una factura: emisor, documento,
 * autorización, receptor, frases, certificador y el QR de verificación.
 */
data class ReceiptInvoice(
    val title: String,
    val emisorName: String,
    val emisorTradeName: String,
    val emisorNit: String,
    val emisorAddress: String,
    val serie: String,
    val numero: String,
    val authorization: String,
    val certifiedAtLabel: String,
    val receptorId: String,
    val receptorName: String,
    /** Lo que cubre la factura. Puede ser menos que el cobro: la cuenta del restaurante va aparte. */
    val invoicedTotal: Double,
    val phrases: List<String>,
    /** Null si faltan datos para armarla: no se imprime un QR que no verifica nada. */
    val verificationUrl: String?,
    /** Certificado en el ambiente de pruebas: sin validez fiscal, y el ticket lo dice. */
    val isTest: Boolean = false,
    /**
     * Factura sin detalle: el ticket imprime esta sola línea por [invoicedTotal] en vez de los
     * productos. Es lo que certificó la SAT, y la representación gráfica tiene que decir lo mismo.
     */
    val singleLineDescription: String? = null
)

/** Arma el bloque fiscal del ticket desde la respuesta de la Cloud Function. */
fun receiptInvoiceFrom(result: FelCertifyResult, certifiedAtLabel: String) = ReceiptInvoice(
    title = felDocTypeTitle(result.docType),
    emisorName = result.emisorName,
    emisorTradeName = result.emisorTradeName.ifBlank { result.emisorName },
    emisorNit = formatNit(result.emisorNit),
    emisorAddress = result.emisorAddress,
    serie = result.serie,
    numero = result.numero,
    authorization = result.uuid,
    certifiedAtLabel = certifiedAtLabel,
    receptorId = if (result.receptorId.isBlank() || result.receptorId == FEL_CONSUMIDOR_FINAL) FEL_CONSUMIDOR_FINAL
        else formatNit(result.receptorId),
    receptorName = result.receptorName.ifBlank { "CONSUMIDOR FINAL" },
    invoicedTotal = result.total,
    phrases = felPhrasesFor(result),
    verificationUrl = felVerificationUrl(result.uuid, result.emisorNit, result.receptorId, result.total),
    isTest = result.environment.equals("TEST", ignoreCase = true),
    singleLineDescription = result.withoutDetailDescription.ifBlank { null }
)

data class ReceiptLine(
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val subtotal: Double
)
