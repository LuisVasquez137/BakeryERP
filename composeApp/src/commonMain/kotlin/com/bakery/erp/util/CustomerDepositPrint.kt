package com.bakery.erp.util

import com.bakery.erp.domain.PrintMediaSize
import com.bakery.erp.domain.PrintableDocument
import com.bakery.erp.model.CustomerDeposit
import com.bakery.erp.model.SalePaymentMethod

/**
 * Comprobante del anticipo: reemplaza al papel donde se anotaba "ya pagó". Lo que importa es
 * el FOLIO, grande —es por lo que se busca al entregar— y que diga que no es factura: la
 * factura sale el día de la entrega, por el total.
 */
fun buildCustomerDepositDocument(
    deposit: CustomerDeposit,
    companyName: String,
    receivedAtLabel: String
): PrintableDocument = PrintableDocument(
    jobName = "Anticipo ${deposit.folio}",
    html = depositHtml(deposit, companyName, receivedAtLabel),
    mediaSize = PrintMediaSize.RECEIPT,
    escPosByWidthMm = listOf(58, 80).associateWith { depositEscPos(deposit, companyName, receivedAtLabel, it) }
)

internal fun depositEscPos(deposit: CustomerDeposit, companyName: String, receivedAtLabel: String, paperWidthMm: Int): ByteArray {
    val cols = if (paperWidthMm >= 76) 48 else 32
    val out = mutableListOf<Byte>()
    fun raw(vararg b: Int) { b.forEach { out.add(it.toByte()) } }
    fun lf() = raw(0x0A)
    fun line(s: String) { out.addAll(escPosTransliterate(s).encodeToByteArray().toList()); lf() }
    fun wrapped(s: String) = wrapToWidth(s, cols).forEach { line(it) }
    fun center() = raw(0x1B, 0x61, 0x01)
    fun left() = raw(0x1B, 0x61, 0x00)
    fun bold(on: Boolean) = raw(0x1B, 0x45, if (on) 1 else 0)
    fun big(on: Boolean) = raw(0x1D, 0x21, if (on) 0x11 else 0x00)
    fun sep() = line("-".repeat(cols))

    raw(0x1B, 0x40)
    center(); bold(true)
    wrapped(companyName)
    line("ANTICIPO DE ENCARGO")
    big(true); line(deposit.folio); big(false)
    bold(false)
    line("Conserve este comprobante")
    left(); sep()
    wrapped("Cliente: ${deposit.customerName}")
    if (deposit.customerPhone.isNotBlank()) line("Tel: ${deposit.customerPhone}")
    wrapped("Encargo: ${deposit.description}")
    if (deposit.customerOrderLabel.isNotBlank()) wrapped("Pedido: ${deposit.customerOrderLabel}")
    if (deposit.deliveryLabel.isNotBlank()) wrapped("Entrega: ${deposit.deliveryLabel}")
    sep()
    // A doble ancho cada carácter ocupa dos columnas: el monto va solo y centrado.
    center(); bold(true)
    line("ANTICIPO")
    big(true); line("Q${deposit.amount.fmt(2)}"); big(false)
    bold(false); left()
    line("Pago: ${SalePaymentMethod.label(deposit.method)}")
    sep()
    wrapped("Recibido: $receivedAtLabel")
    wrapped("${deposit.branchName} · ${deposit.registerName} · ${deposit.receivedByUserName}")
    sep()
    center()
    wrapped("Este comprobante no es factura. La factura se emite al entregar el encargo, por el total.")
    left()
    raw(0x1B, 0x64, 0x04)
    raw(0x1D, 0x56, 0x00)
    return out.toByteArray()
}

private fun depositHtml(d: CustomerDeposit, companyName: String, receivedAtLabel: String): String {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return """
    <!DOCTYPE html>
    <html lang="es"><head><meta charset="UTF-8" /><title>Anticipo ${esc(d.folio)}</title>
    <style>
      @page { margin: 4mm; size: 80mm auto; }
      body { font-family: 'Courier New', monospace; font-size: 12px; width: 76mm; margin: 0; padding: 6px; color: #000; }
      .c { text-align: center; } .b { font-weight: bold; }
      .folio { font-size: 26px; font-weight: bold; letter-spacing: 1px; margin: 4px 0; }
      .sep { border-top: 1px dashed #000; margin: 6px 0; }
      .row { display: flex; justify-content: space-between; }
      .amount { font-size: 16px; font-weight: bold; }
      .small { font-size: 10px; }
    </style></head><body>
      <div class="c b">${esc(companyName)}</div>
      <div class="c b">ANTICIPO DE ENCARGO</div>
      <div class="c folio">${esc(d.folio)}</div>
      <div class="c small">Conserve este comprobante</div>
      <div class="sep"></div>
      <div>Cliente: ${esc(d.customerName)}</div>
      ${if (d.customerPhone.isNotBlank()) "<div>Tel: ${esc(d.customerPhone)}</div>" else ""}
      <div>Encargo: ${esc(d.description)}</div>
      ${if (d.customerOrderLabel.isNotBlank()) "<div>Pedido: ${esc(d.customerOrderLabel)}</div>" else ""}
      ${if (d.deliveryLabel.isNotBlank()) "<div>Entrega: ${esc(d.deliveryLabel)}</div>" else ""}
      <div class="sep"></div>
      <div class="row amount"><span>ANTICIPO</span><span>Q${d.amount.fmt(2)}</span></div>
      <div>Pago: ${esc(SalePaymentMethod.label(d.method))}</div>
      <div class="sep"></div>
      <div class="small">Recibido: ${esc(receivedAtLabel)}<br>${esc(d.branchName)} · ${esc(d.registerName)} · ${esc(d.receivedByUserName)}</div>
      <div class="sep"></div>
      <div class="c small">Este comprobante no es factura. La factura se emite al entregar el encargo, por el total.</div>
    </body></html>
    """.trimIndent()
}
