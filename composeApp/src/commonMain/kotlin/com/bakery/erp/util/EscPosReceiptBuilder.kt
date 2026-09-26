package com.bakery.erp.util

import com.bakery.erp.model.FEL_CERTIFIER_NAME
import com.bakery.erp.model.FEL_CERTIFIER_NIT
import com.bakery.erp.model.ReceiptData

/**
 * Construye el ticket como bytes ESC/POS para impresoras térmicas de 58mm (32 chars) o 80mm
 * (48 chars). Se envía por socket Bluetooth/RFCOMM (SPP) directo a la impresora.
 *
 * Comandos cubiertos:
 *  - ESC @         init
 *  - ESC a n       alineación (0=izq, 1=centro, 2=der)
 *  - ESC E n       bold on/off
 *  - GS ! n        doble ancho/alto (0x11 = ambos)
 *  - ESC d n       feed n líneas
 *  - GS V 0        corte total de papel
 *
 * Texto se envía como ASCII con acentos transliterados (á→a, ñ→n) para evitar problemas
 * con codepages de impresoras chinas baratas.
 */
fun buildEscPosReceipt(data: ReceiptData, paperWidthMm: Int): ByteArray {
    val cols = if (paperWidthMm >= 76) 48 else 32
    val out = mutableListOf<Byte>()

    fun raw(vararg bytes: Int) { bytes.forEach { out.add(it.toByte()) } }
    fun text(s: String) { out.addAll(escPosTransliterate(s).encodeToByteArray().toList()) }
    fun lf() { raw(0x0A) }
    fun line(s: String) { text(s); lf() }
    fun center() { raw(0x1B, 0x61, 0x01) }
    fun leftAlign() { raw(0x1B, 0x61, 0x00) }
    fun bold(on: Boolean) { raw(0x1B, 0x45, if (on) 0x01 else 0x00) }
    fun bigSize(on: Boolean) { raw(0x1D, 0x21, if (on) 0x11 else 0x00) }
    fun separator() { line("-".repeat(cols)) }
    fun wrapped(s: String) { wrapToWidth(s, cols).forEach { line(it) } }
    val invoice = data.invoice

    // Init
    raw(0x1B, 0x40)

    // Encabezado: nombre comercial grande y centrado. Con factura manda el EMISOR: la SAT pide
    // su nombre, NIT y dirección, y pueden no ser los de la empresa en BakeOS.
    center()
    bold(true); bigSize(true)
    wrapToWidth(invoice?.emisorTradeName ?: data.companyName, cols / 2).forEach { line(it) }
    bigSize(false)
    if (invoice != null) {
        bold(false)
        wrapped(invoice.emisorName)
        line("NIT: ${invoice.emisorNit}")
        wrapped(invoice.emisorAddress)
    } else {
        if (data.branchName.isNotEmpty()) line(data.branchName)
        bold(false)
    }
    leftAlign()
    separator()

    if (invoice != null) {
        center(); bold(true)
        wrapped(invoice.title)
        line("DOCUMENTO TRIBUTARIO ELECTRONICO")
        bold(false); leftAlign()
        line("Serie: ${invoice.serie}")
        line("Numero: ${invoice.numero}")
        line("Autorizacion:")
        wrapped(invoice.authorization)
        line("Certificada: ${invoice.certifiedAtLabel}")
        separator()
        line("NIT: ${invoice.receptorId}")
        wrapped("Nombre: ${invoice.receptorName}")
        separator()
    }

    // Metadatos
    line("Caja: ${data.registerName}")
    line("Cajero: ${data.cashierName}")
    line("Fecha: ${data.dateLabel}")
    line("Venta: ${data.saleId}")
    separator()

    // Líneas — nombre a la izquierda, subtotal a la derecha
    data.lines.forEach { l ->
        val priceStr = "Q${l.subtotal.fmt(2)}"
        val nameMax = cols - priceStr.length - 1
        val displayName = l.name.take(nameMax)
        val pad = cols - displayName.length - priceStr.length
        line(displayName + " ".repeat(pad.coerceAtLeast(1)) + priceStr)
        // meta línea: cantidad x precio unitario
        val meta = "  x${l.quantity.fmt(0)} @ Q${l.unitPrice.fmt(2)}"
        line(meta.take(cols))
    }
    separator()

    // Totales
    line(rightLabel(cols, "Subtotal", "Q${data.subtotal.fmt(2)}"))
    if (data.discount > 0.0) {
        line(rightLabel(cols, "Descuento", "-Q${data.discount.fmt(2)}"))
    }
    bold(true); bigSize(true)
    line(rightLabel(cols / 2, "TOTAL", "Q${data.total.fmt(2)}"))
    bigSize(false); bold(false)
    // La cuenta del restaurante del mismo cobro no entra en esta factura: el ticket tiene que
    // decir cuánto se facturó, o el total impreso y el de la SAT no coinciden.
    if (invoice != null && kotlin.math.abs(invoice.invoicedTotal - data.total) > 0.004) {
        line(rightLabel(cols, "Monto facturado", "Q${invoice.invoicedTotal.fmt(2)}"))
    }
    separator()

    line("Pago: ${data.paymentMethodLabel}")
    if (data.tenderedAmount > 0.0 && data.tenderedAmount != data.total) {
        line(rightLabel(cols, "Recibido", "Q${data.tenderedAmount.fmt(2)}"))
    }
    if (data.changeAmount > 0.0) {
        line(rightLabel(cols, "Vuelto", "Q${data.changeAmount.fmt(2)}"))
    }
    separator()

    if (invoice != null) {
        center()
        invoice.phrases.forEach { wrapped(it) }
        wrapped("Certificador: $FEL_CERTIFIER_NAME NIT $FEL_CERTIFIER_NIT")
        invoice.verificationUrl?.let { url ->
            lf()
            out.addAll(escPosQr(url, if (cols >= 48) 6 else 5).toList())
            lf()
            line("Verifique su factura en la SAT")
        }
        if (invoice.isTest) { bold(true); wrapped("DOCUMENTO DE PRUEBA - SIN VALIDEZ FISCAL"); bold(false) }
        leftAlign()
        separator()
    }

    // Pie de página
    center()
    line(data.footerMessage)
    leftAlign()

    // Feed + corte
    raw(0x1B, 0x64, 0x04)        // feed 4 líneas
    raw(0x1D, 0x56, 0x00)        // corte total

    return out.toByteArray()
}

private fun rightLabel(cols: Int, label: String, value: String): String {
    val pad = cols - label.length - value.length
    return label + " ".repeat(pad.coerceAtLeast(1)) + value
}
