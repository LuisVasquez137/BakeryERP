package com.bakery.erp.data

import android.content.Context
import com.bakery.erp.domain.BluetoothPrinterManager
import com.bakery.erp.domain.PrintMediaSize
import com.bakery.erp.domain.PrintableDocument
import com.bakery.erp.domain.ReceiptPrinter
import com.bakery.erp.model.ReceiptData
import com.bakery.erp.util.buildEscPosReceipt
import com.bakery.erp.util.buildReceiptHtml

/**
 * Ticket de venta en Android. El transporte (térmica Bluetooth con respaldo al
 * diálogo del sistema) vive en [AndroidDocumentPrinter]; acá solo se decide el
 * formato del ticket.
 */
class AndroidReceiptPrinter(
    appContext: Context,
    btManager: BluetoothPrinterManager
) : ReceiptPrinter {

    private val documentPrinter = AndroidDocumentPrinter(appContext, btManager)

    override val isAvailable: Boolean = true

    override suspend fun print(data: ReceiptData): Result<Unit> =
        documentPrinter.print(
            PrintableDocument(
                jobName = "Ticket-${data.saleId.ifEmpty { "venta" }}",
                html = buildReceiptHtml(data),
                mediaSize = PrintMediaSize.RECEIPT,
                escPosByWidthMm = mapOf(
                    58 to buildEscPosReceipt(data, 58),
                    80 to buildEscPosReceipt(data, 80)
                )
            )
        )
}
