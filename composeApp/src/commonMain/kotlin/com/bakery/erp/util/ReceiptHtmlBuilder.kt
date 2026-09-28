package com.bakery.erp.util

import com.bakery.erp.model.FEL_CERTIFIER_NAME
import com.bakery.erp.model.FEL_CERTIFIER_NIT
import com.bakery.erp.model.ReceiptData

/**
 * Genera HTML imprimible del ticket. Pensado para impresoras térmicas de 58/80mm o impresión
 * por navegador (página estrecha con monospace, sin estilos pesados).
 *
 * El usuario lo abre en una ventana/print job — no se renderiza dentro de Compose.
 */
fun buildReceiptHtml(data: ReceiptData): String {
    val lineRows = data.lines.joinToString("") { line ->
        val name = escapeHtml(line.name).take(28)
        val qty = "x${line.quantity.fmt(0)}"
        val sub = "Q${line.subtotal.fmt(2)}"
        """
        <tr>
          <td class="name">$name<br><span class="meta">$qty · Q${line.unitPrice.fmt(2)} c/u</span></td>
          <td class="amount">$sub</td>
        </tr>
        """.trimIndent()
    }

    val discountRow = if (data.discount > 0.0) {
        """<div class="row"><span>Descuento</span><span>-Q${data.discount.fmt(2)}</span></div>"""
    } else ""

    // En la factura no va cuánto dio ni el vuelto: es el documento del cliente, no el del cobro.
    val changeRow = if (data.invoice == null && data.changeAmount > 0.0) {
        """<div class="row"><span>Vuelto</span><span>Q${data.changeAmount.fmt(2)}</span></div>"""
    } else ""

    val tenderedRow = if (data.invoice == null && data.tenderedAmount > 0.0 && data.tenderedAmount != data.total) {
        """<div class="row"><span>Recibido</span><span>Q${data.tenderedAmount.fmt(2)}</span></div>"""
    } else ""

    val inv = data.invoice
    // Con factura manda el EMISOR en el encabezado: la SAT pide su nombre, NIT y dirección.
    val header = if (inv != null) """
      <div class="center bold big">${escapeHtml(inv.emisorTradeName)}</div>
      <div class="center">${escapeHtml(inv.emisorName)}</div>
      <div class="center">NIT: ${escapeHtml(inv.emisorNit)}</div>
      <div class="center">${escapeHtml(inv.emisorAddress)}</div>
      <div class="sep"></div>
      <div class="center bold">${escapeHtml(inv.title)}</div>
      <div class="center">DOCUMENTO TRIBUTARIO ELECTRÓNICO</div>
      <div>Serie: ${escapeHtml(inv.serie)} · Número: ${escapeHtml(inv.numero)}</div>
      <div>Autorización:</div>
      <div class="mono-wrap">${escapeHtml(inv.authorization)}</div>
      <div>Certificada: ${escapeHtml(inv.certifiedAtLabel)}</div>
      <div class="sep"></div>
      <div>NIT: ${escapeHtml(inv.receptorId)}</div>
      <div>Nombre: ${escapeHtml(inv.receptorName)}</div>
    """ else """
      <div class="center bold big">${escapeHtml(data.companyName)}</div>
      <div class="center">${escapeHtml(data.branchName)}</div>
    """
    val invoicedRow = if (inv != null && kotlin.math.abs(inv.invoicedTotal - data.total) > 0.004) {
        """<div class="row"><span>Monto facturado</span><span>Q${inv.invoicedTotal.fmt(2)}</span></div>"""
    } else ""
    val fiscalFooter = if (inv != null) """
      <div class="center footer">${inv.phrases.joinToString("<br>") { escapeHtml(it) }}</div>
      <div class="center footer">Certificador: ${escapeHtml(FEL_CERTIFIER_NAME)} · NIT $FEL_CERTIFIER_NIT</div>
      ${inv.verificationUrl?.let { """<div class="center qr">${qrSvg(it, 34)}</div><div class="center footer">Verifique su factura en la SAT</div>""" } ?: ""}
      ${if (inv.isTest) """<div class="center bold">DOCUMENTO DE PRUEBA — SIN VALIDEZ FISCAL</div>""" else ""}
      <div class="sep"></div>
    """ else ""

    return """
    <!DOCTYPE html>
    <html lang="es">
    <head>
      <meta charset="UTF-8" />
      <title>Ticket ${escapeHtml(data.saleId)}</title>
      <style>
        @page { margin: 4mm; size: 80mm auto; }
        * { box-sizing: border-box; }
        body {
          font-family: 'Courier New', monospace;
          font-size: 12px;
          margin: 0;
          padding: 6px;
          width: 76mm;
          color: #000;
        }
        .center { text-align: center; }
        .right  { text-align: right; }
        .bold   { font-weight: bold; }
        .big    { font-size: 16px; }
        .sep    { border-top: 1px dashed #000; margin: 6px 0; }
        table   { width: 100%; border-collapse: collapse; }
        td      { vertical-align: top; padding: 2px 0; }
        td.name { width: 70%; }
        td.amount { width: 30%; text-align: right; }
        .meta   { font-size: 10px; color: #555; }
        .row    { display: flex; justify-content: space-between; padding: 1px 0; }
        .total  { font-size: 14px; font-weight: bold; }
        .footer { margin-top: 8px; font-size: 10px; }
        .mono-wrap { word-break: break-all; }
        .qr { margin-top: 6px; }
        @media print {
          html, body { margin: 0; padding: 0; }
        }
      </style>
    </head>
    <body>
      $header
      <div class="sep"></div>
      <div>Caja: ${escapeHtml(data.registerName)}</div>
      <div>Cajero: ${escapeHtml(data.cashierName)}</div>
      <div>Fecha: ${escapeHtml(data.dateLabel)}</div>
      <div>Venta: ${escapeHtml(data.saleId)}</div>
      <div class="sep"></div>
      <table>${data.invoice?.singleLineDescription?.let { d ->
        // Sin detalle: la única línea que certificó la SAT, por lo facturado.
        """<tr><td class="name">${escapeHtml(d)}</td><td class="amount">Q${data.invoice.invoicedTotal.fmt(2)}</td></tr>"""
      } ?: lineRows}</table>
      <div class="sep"></div>
      <div class="row"><span>Subtotal</span><span>Q${data.subtotal.fmt(2)}</span></div>
      $discountRow
      <div class="row total"><span>TOTAL</span><span>Q${data.total.fmt(2)}</span></div>
      ${if (data.depositAmount > 0.004) """<div class="row"><span>Anticipo ${escapeHtml(data.depositFolio)}</span><span>-Q${data.depositAmount.fmt(2)}</span></div><div class="row"><span>Pagado hoy</span><span>Q${(data.total - data.depositAmount).coerceAtLeast(0.0).fmt(2)}</span></div>""" else ""}
      $invoicedRow
      <div class="sep"></div>
      <div class="row"><span>Pago</span><span>${escapeHtml(data.paymentMethodLabel)}</span></div>
      $tenderedRow
      $changeRow
      <div class="sep"></div>
      $fiscalFooter
      <div class="center footer">${escapeHtml(data.footerMessage)}</div>
    </body>
    </html>
    """.trimIndent()
}

private fun escapeHtml(s: String): String =
    s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
