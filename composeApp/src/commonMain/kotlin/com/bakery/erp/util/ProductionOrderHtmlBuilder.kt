package com.bakery.erp.util

import com.bakery.erp.model.PrintMasaGroup
import com.bakery.erp.model.ProductionOrderPrintData

/**
 * Orden de producción en HTML, para imprimir en hoja normal (web, o el diálogo del
 * sistema en Android/iOS cuando no hay térmica configurada).
 *
 * Es el mismo contenido que la versión térmica, pero aprovechando el ancho de la
 * hoja: la cantidad de cada producto sale en 34pt porque es el único número que el
 * panadero busca desde lejos, y cada masa lleva su casilla de "Hecho" porque en
 * papel el avance se marca con lápiz.
 *
 * `print-color-adjust: exact` es necesario para que las bandas de color de cada masa
 * sobrevivan al diálogo de impresión — por defecto los navegadores descartan fondos, y
 * sin ellas las masas se funden en una sola lista al bajar la hoja.
 */
fun buildProductionOrderHtml(data: ProductionOrderPrintData): String {
    val meta = listOf(
        "Turno" to data.shiftName,
        "Fecha" to data.dateLabel,
        "Estado" to data.statusLabel,
        "Impreso" to data.printedAtLabel
    ).filter { it.second.isNotBlank() }
        .joinToString("") { (k, v) ->
            """<div class="meta-item"><span class="meta-k">${esc(k)}</span>${esc(v)}</div>"""
        }

    val doughTotal = if (data.totalDoughLb > 0.0)
        """<div class="kpi"><span class="kpi-n">${data.totalDoughLb.fmt(1)}</span><span class="kpi-l">lb de masa</span></div>"""
    else ""

    val groups = data.groups.joinToString("") { groupHtml(it) }

    val specialLegend = if (data.groups.any { g -> g.items.any { it.isSpecial } })
        """<div class="legend">★ = pedido especial (figura fuera de catálogo)</div>"""
    else ""

    return """
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8" />
<title>Orden de producción — ${esc(data.branchName)}</title>
<style>
  @page { size: A4 portrait; margin: 12mm 10mm; }
  * { box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  body {
    font-family: -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    color: #1a1a1a; margin: 0; font-size: 15pt; line-height: 1.35;
  }
  header { border-bottom: 4px solid #005B8D; padding-bottom: 10px; margin-bottom: 14px; }
  h1 { font-size: 30pt; margin: 0; color: #005B8D; letter-spacing: -0.5px; }
  .branch { font-size: 21pt; font-weight: 700; margin-top: 2px; }
  .meta { display: flex; flex-wrap: wrap; gap: 6px 22px; margin-top: 8px; font-size: 13pt; }
  .meta-k { color: #666; margin-right: 6px; }
  .kpis { display: flex; gap: 26px; margin: 14px 0 20px; }
  .kpi {
    border: 3px solid #005B8D; border-radius: 10px; padding: 8px 20px;
    display: flex; align-items: baseline; gap: 10px;
  }
  .kpi-n { font-size: 30pt; font-weight: 800; color: #005B8D; }
  .kpi-l { font-size: 14pt; color: #444; }

  .masa { margin-bottom: 18px; page-break-inside: avoid; break-inside: avoid;
          border: 2px solid #005B8D; border-radius: 10px; overflow: hidden; }
  .masa-head { background: #005B8D; color: #fff; padding: 9px 14px; }
  .masa-title { font-size: 23pt; font-weight: 800; text-transform: uppercase; }
  .masa-sub { font-size: 13pt; opacity: .92; }
  .masa-extra { font-size: 13pt; color: #FFE0A3; font-weight: 600; }

  table { width: 100%; border-collapse: collapse; }
  td { padding: 9px 14px; border-top: 1px solid #DCE6ED; vertical-align: middle; }
  tr:nth-child(even) td { background: #F5F9FC; }
  td.name { font-size: 20pt; font-weight: 600; }
  td.qty { font-size: 34pt; font-weight: 800; text-align: right; width: 130px;
           white-space: nowrap; color: #005B8D; }
  td.done { width: 58px; text-align: center; }
  .box { display: inline-block; width: 30px; height: 30px; border: 3px solid #999; border-radius: 5px; }
  .tray { display: block; font-size: 12pt; font-weight: 400; color: #555; }
  .note { display: block; font-size: 12pt; color: #7A5B00; font-weight: 600; }
  .warn { display: block; font-size: 12pt; color: #C62828; font-weight: 700; }
  .star { color: #A85200; }

  .filling-only { padding: 12px 14px; font-size: 14pt; background: #FFF8E1; }
  .filling-only b { color: #8A6400; }

  .legend { font-size: 12pt; color: #666; margin-top: 6px; }
  footer { margin-top: 20px; border-top: 2px solid #DCE6ED; padding-top: 8px;
           font-size: 12pt; color: #777; }
</style>
</head>
<body>
  <header>
    <h1>Orden de producción</h1>
    <div class="branch">${esc(data.branchName)}</div>
    <div class="meta">$meta</div>
  </header>

  <div class="kpis">
    <div class="kpi"><span class="kpi-n">${data.totalUnits}</span><span class="kpi-l">unidades</span></div>
    $doughTotal
  </div>

  $groups
  $specialLegend

  <footer>BakeOS · Orden de producción impresa el ${esc(data.printedAtLabel)}</footer>
</body>
</html>
""".trimIndent()
}

private fun groupHtml(group: PrintMasaGroup): String {
    if (group.isFillingOnly) {
        return """
        <div class="masa">
          <div class="masa-head">
            <div class="masa-title">${esc(group.title)}</div>
            <div class="masa-sub">${group.doughLb.fmt(1)} lb de masa a amasar</div>
          </div>
          <div class="filling-only">
            <b>No la pidió ninguna sucursal</b> — se amasa solo porque la lleva de relleno
            ${esc(group.extraDoughFor)}.
          </div>
        </div>
        """.trimIndent()
    }

    val sub = buildString {
        append("${group.totalUnits} unidades")
        if (group.doughLb > 0.0) append(" · ${group.doughLb.fmt(1)} lb de masa")
        if (group.hasSponge) append(" · con esponja")
    }
    val extra = if (group.extraDoughLb > 0.0 && group.extraDoughFor.isNotBlank())
        """<div class="masa-extra">+ ${group.extraDoughLb.fmt(1)} lb para relleno de ${esc(group.extraDoughFor)}</div>"""
    else ""

    val rows = group.items.joinToString("") { item ->
        val star = if (item.isSpecial) """<span class="star">★</span> """ else ""
        val tray = if (item.trayBreakdown.isNotBlank())
            """<span class="tray">${esc(item.trayBreakdown)}</span>""" else ""
        val warn = if (item.missingWeight)
            """<span class="warn">Sin peso configurado — no entró al cálculo de masa</span>""" else ""
        val notes = item.customerNotes.joinToString("") {
            """<span class="note">▸ ${esc(it)}</span>"""
        }
        """
        <tr>
          <td class="name">$star${esc(item.name)}$tray$warn$notes</td>
          <td class="qty">${item.quantity}</td>
          <td class="done"><span class="box"></span></td>
        </tr>
        """.trimIndent()
    }

    return """
    <div class="masa">
      <div class="masa-head">
        <div class="masa-title">${esc(group.title)}</div>
        <div class="masa-sub">${esc(sub)}</div>
        $extra
      </div>
      <table>$rows</table>
    </div>
    """.trimIndent()
}

private fun esc(s: String): String =
    s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
