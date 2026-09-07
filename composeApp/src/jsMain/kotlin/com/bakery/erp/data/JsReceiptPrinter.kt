package com.bakery.erp.data

import com.bakery.erp.domain.PrintMediaSize
import com.bakery.erp.domain.PrintableDocument
import com.bakery.erp.domain.ReceiptPrinter
import com.bakery.erp.model.ReceiptData
import com.bakery.erp.util.buildReceiptHtml

/**
 * Ticket de venta en navegador. El transporte (iframe oculto + `window.print()`) vive
 * en [JsDocumentPrinter]; acá solo se decide el formato del ticket.
 */
class JsReceiptPrinter : ReceiptPrinter {

    private val documentPrinter = JsDocumentPrinter()

    override val isAvailable: Boolean = true

    override suspend fun print(data: ReceiptData): Result<Unit> =
        documentPrinter.print(
            PrintableDocument(
                jobName = "Ticket-${data.saleId.ifEmpty { "venta" }}",
                html = buildReceiptHtml(data),
                mediaSize = PrintMediaSize.RECEIPT
            )
        )
}
