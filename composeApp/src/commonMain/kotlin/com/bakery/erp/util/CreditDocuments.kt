package com.bakery.erp.util

import com.bakery.erp.domain.PrintMediaSize
import com.bakery.erp.domain.PrintableDocument
import com.bakery.erp.model.CreditAccount
import com.bakery.erp.model.CreditAccountType
import com.bakery.erp.model.CreditAging
import com.bakery.erp.model.CreditMovement
import com.bakery.erp.model.CreditMovementType
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.creditStatement
import kotlin.math.abs

/**
 * Estado de cuenta imprimible — lo que se le entrega al colegio para que cuadre contra sus
 * propios vales. Hoja (HTML) o térmica (ESC/POS), igual que la orden de producción: la
 * plataforma elige, no quien arma el documento.
 *
 * Los renglones salen de [creditStatement], la misma función que la pantalla: el papel no
 * puede decir otra cosa que lo que se ve.
 */
fun creditStatementDocument(
    account: CreditAccount,
    movements: List<CreditMovement>,
    now: Long,
    organizationName: String = ""
): PrintableDocument {
    val rows = creditStatement(movements)
    return PrintableDocument(
        jobName = "Estado de cuenta - ${account.name}",
        html = statementHtml(account, rows, now, organizationName),
        mediaSize = PrintMediaSize.PAGE,
        escPosByWidthMm = mapOf(58 to statementEscPos(account, rows, now, 58), 80 to statementEscPos(account, rows, now, 80))
    )
}

private fun movementDetail(m: CreditMovement): String = listOfNotNull(
    m.description.ifBlank { null },
    m.personName.takeIf { it.isNotBlank() && m.type == CreditMovementType.SALE }?.let { "Llevó: $it" },
    m.method.takeIf { it.isNotBlank() && m.type == CreditMovementType.PAYMENT }?.let { SalePaymentMethod.label(it) }
).joinToString(" · ")

private fun statementHtml(
    account: CreditAccount,
    rows: List<Pair<CreditMovement, Double>>,
    now: Long,
    organizationName: String
): String {
    val body = rows.joinToString("") { (m, running) ->
        val charge = if (m.amount > 0) "Q ${m.amount.fmtMoney(2)}" else ""
        val payment = if (m.amount < 0) "Q ${abs(m.amount).fmtMoney(2)}" else ""
        """<tr><td>${esc(m.createdAt.fmtDate())}</td><td><b>${esc(CreditMovementType.label(m.type))}</b>""" +
            """<div class="d">${esc(movementDetail(m))}</div></td>""" +
            """<td class="n">$charge</td><td class="n">$payment</td><td class="n b">Q ${running.fmtMoney(2)}</td></tr>"""
    }
    val meta = listOfNotNull(
        CreditAccountType.label(account.type),
        account.taxId.ifBlank { null }?.let { "NIT $it" },
        account.phone.ifBlank { null }?.let { "Tel. $it" }
    ).joinToString(" · ")
    return """
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8" />
<title>Estado de cuenta — ${esc(account.name)}</title>
<style>
  @page { size: A4 portrait; margin: 14mm 12mm; }
  * { box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  body { font-family: -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; color: #1a1a1a; margin: 0; font-size: 11pt; }
  header { border-bottom: 3px solid #005B8D; padding-bottom: 8px; margin-bottom: 12px; }
  h1 { font-size: 20pt; margin: 0; color: #005B8D; }
  .org { color: #555; font-size: 10pt; }
  .acc { font-size: 15pt; font-weight: 700; margin-top: 4px; }
  .meta { color: #555; font-size: 10pt; }
  .bal { margin: 10px 0 14px; font-size: 14pt; }
  .bal b { color: #A85200; font-size: 18pt; }
  table { width: 100%; border-collapse: collapse; }
  th { text-align: left; font-size: 9.5pt; color: #555; border-bottom: 2px solid #005B8D; padding: 4px; }
  td { border-bottom: 1px solid #ddd; padding: 5px 4px; vertical-align: top; }
  .n { text-align: right; white-space: nowrap; }
  .b { font-weight: 700; }
  .d { color: #555; font-size: 9.5pt; }
  footer { margin-top: 18px; font-size: 9pt; color: #777; }
</style></head><body>
<header>
  ${if (organizationName.isNotBlank()) "<div class=\"org\">${esc(organizationName)}</div>" else ""}
  <h1>Estado de cuenta</h1>
  <div class="acc">${esc(account.name)}</div>
  <div class="meta">${esc(meta)} · Emitido ${esc(now.fmtDateTime())}</div>
</header>
<div class="bal">Saldo pendiente: <b>Q ${account.balance.fmtMoney(2)}</b></div>
<table>
  <thead><tr><th>Fecha</th><th>Concepto</th><th class="n">Cargo</th><th class="n">Abono</th><th class="n">Saldo</th></tr></thead>
  <tbody>$body</tbody>
</table>
<footer>Cargo = compra o adelanto · Abono = pago o descuento en planilla. Cualquier diferencia, favor de comunicarla.</footer>
</body></html>
""".trimIndent()
}

private fun statementEscPos(
    account: CreditAccount,
    rows: List<Pair<CreditMovement, Double>>,
    now: Long,
    widthMm: Int
): ByteArray {
    val cols = escPosColumns(widthMm)
    val w = EscPosWriter(cols)
    w.init()
    w.center(); w.bold(true); w.size(1, 2); w.line("ESTADO DE CUENTA"); w.normalSize(); w.bold(false)
    wrapToWidth(account.name, cols).forEach { w.line(it) }
    w.line(now.fmtDateTime())
    w.left(); w.separator()
    rows.forEach { (m, running) ->
        val amount = (if (m.amount > 0) "+" else "-") + "Q" + abs(m.amount).fmtMoney(2)
        w.line(padBetween(cols, "${m.createdAt.fmtDate()} ${CreditMovementType.label(m.type)}", amount))
        val detail = movementDetail(m)
        if (detail.isNotBlank()) wrapToWidth(detail, cols - 2).forEach { w.line("  $it") }
        w.line(padBetween(cols, "", "Saldo Q${running.fmtMoney(2)}"))
    }
    w.separator()
    w.bold(true); w.size(1, 2)
    w.line(padBetween(cols, "DEBE", "Q${account.balance.fmtMoney(2)}"))
    w.normalSize(); w.bold(false)
    w.feedAndCut()
    return w.toByteArray()
}

/** Una fila del reporte de antigüedad de saldos. */
data class CreditAgingRow(val account: CreditAccount, val aging: CreditAging)

/** Reporte de antigüedad imprimible: quién debe y desde hace cuánto. */
fun creditAgingDocument(rows: List<CreditAgingRow>, now: Long, title: String): PrintableDocument {
    val total = rows.fold(CreditAging()) { acc, r -> acc + r.aging }
    val body = rows.joinToString("") { r ->
        """<tr><td>${esc(r.account.name)}<div class="d">${esc(CreditAccountType.label(r.account.type))}</div></td>""" +
            listOf(r.aging.upTo30, r.aging.days31to60, r.aging.days61to90, r.aging.over90, r.aging.total)
                .joinToString("") { """<td class="n">${if (it > 0.004) "Q " + it.fmtMoney(2) else "—"}</td>""" } +
            "</tr>"
    }
    val totalRow = listOf(total.upTo30, total.days31to60, total.days61to90, total.over90, total.total)
        .joinToString("") { """<td class="n b">Q ${it.fmtMoney(2)}</td>""" }
    val html = """
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8" /><title>${esc(title)}</title>
<style>
  @page { size: A4 landscape; margin: 12mm; }
  body { font-family: -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; margin: 0; font-size: 10.5pt; color: #1a1a1a; }
  h1 { color: #005B8D; font-size: 18pt; margin: 0 0 4px; }
  .meta { color: #555; margin-bottom: 12px; }
  table { width: 100%; border-collapse: collapse; }
  th { text-align: right; font-size: 9.5pt; color: #555; border-bottom: 2px solid #005B8D; padding: 4px; }
  th:first-child { text-align: left; }
  td { border-bottom: 1px solid #ddd; padding: 5px 4px; }
  .n { text-align: right; white-space: nowrap; } .b { font-weight: 700; }
  .d { color: #777; font-size: 9pt; }
  tfoot td { border-top: 2px solid #005B8D; }
</style></head><body>
<h1>${esc(title)}</h1>
<div class="meta">Al ${esc(now.fmtDateTime())} · ${rows.size} cuenta(s) con saldo</div>
<table><thead><tr><th>Cuenta</th><th>0–30 días</th><th>31–60</th><th>61–90</th><th>Más de 90</th><th>Total</th></tr></thead>
<tbody>$body</tbody><tfoot><tr><td class="b">Total</td>$totalRow</tr></tfoot></table>
</body></html>
""".trimIndent()

    fun escPos(widthMm: Int): ByteArray {
        val cols = escPosColumns(widthMm)
        val w = EscPosWriter(cols)
        w.init(); w.center(); w.bold(true); w.line(title.uppercase()); w.bold(false)
        w.line(now.fmtDateTime()); w.left(); w.separator()
        rows.forEach { r ->
            w.bold(true); w.line(padBetween(cols, r.account.name.take(cols - 12), "Q${r.aging.total.fmtMoney(2)}")); w.bold(false)
            if (r.aging.overdue30 > 0.004) w.line("  +30 dias: Q${r.aging.overdue30.fmtMoney(2)}")
        }
        w.separator()
        w.bold(true); w.line(padBetween(cols, "TOTAL", "Q${total.total.fmtMoney(2)}")); w.bold(false)
        w.line(padBetween(cols, "Con mas de 30 dias", "Q${total.overdue30.fmtMoney(2)}"))
        w.feedAndCut()
        return w.toByteArray()
    }

    return PrintableDocument(
        jobName = title,
        html = html,
        mediaSize = PrintMediaSize.PAGE,
        escPosByWidthMm = mapOf(58 to escPos(58), 80 to escPos(80))
    )
}

private fun esc(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
