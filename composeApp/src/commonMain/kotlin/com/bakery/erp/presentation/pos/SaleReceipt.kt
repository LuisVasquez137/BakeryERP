package com.bakery.erp.presentation.pos

import com.bakery.erp.model.ReceiptData
import com.bakery.erp.model.ReceiptLine
import com.bakery.erp.model.Sale
import com.bakery.erp.model.SaleLine
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.effectivePaymentBreakdown
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * El ticket de una venta. Lo usan el cobro del POS y la reimpresión desde el historial: un
 * solo lugar, para que el ticket reimpreso diga lo mismo que el original — encabezado, método
 * de pago, cómo se nombra una venta fiada.
 */
internal fun saleReceiptData(
    sale: Sale,
    lines: List<ReceiptLine>,
    branchName: String,
    branchLocation: String,
    organizationName: String
): ReceiptData {
    val createdMillis = if (sale.createdAt > 0L) sale.createdAt else Clock.System.now().toEpochMilliseconds()
    // Cabecera: nombre comercial (org) en grande, abajo "Sucursal: X" + dirección si hay.
    val header = organizationName.ifEmpty { branchName.ifEmpty { "Punto de Venta" } }
    val subHeader = buildString {
        if (organizationName.isNotEmpty() && branchName.isNotEmpty()) append("Sucursal: ").append(branchName)
        if (branchLocation.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append(branchLocation)
        }
    }
    return ReceiptData(
        companyName = header,
        branchName = subHeader,
        saleId = sale.id.takeLast(8).ifEmpty { "—" },
        dateLabel = formatReceiptTimestamp(createdMillis),
        cashierName = sale.cashierName,
        registerName = sale.registerName,
        lines = lines,
        subtotal = sale.subtotal,
        discount = sale.discount,
        total = sale.total,
        paymentMethodLabel = if (sale.paymentMethod == SalePaymentMethod.CREDIT)
            "Crédito · ${sale.creditAccountName}" +
                (if (sale.creditPersonName.isNotBlank() && sale.creditPersonName != sale.creditAccountName)
                    " (llevó ${sale.creditPersonName})" else "")
        // El revendedor va junto al método de pago y no en un renglón propio del ticket: así
        // sale igual en la impresora, en el acuse y en la reimpresión sin tocar el formato.
        else SalePaymentMethod.label(sale.paymentMethod) +
            // Pago dividido: cuánto por cada método. "Recibido" y "vuelto" son del efectivo.
            (if (sale.paymentMethod == SalePaymentMethod.MIXED)
                " · " + com.bakery.erp.model.paymentPartsLabel(sale.effectivePaymentBreakdown) else "") +
            (if (sale.resellerName.isNotBlank()) " · Revendedor: ${sale.resellerName}" else ""),
        tenderedAmount = sale.tenderedAmount,
        changeAmount = sale.changeAmount,
        depositFolio = sale.depositFolio,
        depositAmount = sale.depositAmount
    )
}

/** Líneas guardadas de una venta → líneas del ticket. */
internal fun receiptLinesOf(lines: List<SaleLine>): List<ReceiptLine> = lines.map { l ->
    ReceiptLine(
        name = l.productName.ifBlank { l.quickGroupName.orEmpty() },
        quantity = l.quantity,
        unitPrice = l.unitPrice,
        subtotal = l.subtotal
    )
}

/** "25/09/2026 14:35" en la hora del dispositivo. */
internal fun formatReceiptTimestamp(millis: Long): String {
    val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    val day = local.dayOfMonth.toString().padStart(2, '0')
    val month = local.monthNumber.toString().padStart(2, '0')
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "$day/$month/${local.year} $hour:$minute"
}
