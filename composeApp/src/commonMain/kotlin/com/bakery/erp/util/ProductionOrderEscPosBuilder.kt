package com.bakery.erp.util

import com.bakery.erp.model.PrintMasaGroup
import com.bakery.erp.model.ProductionOrderPrintData

/**
 * Orden de producción en ESC/POS para térmica de 58 mm (32 col) u 80 mm (48 col).
 *
 * El papel lo lee un panadero de pie, con las manos en la masa y a un brazo de
 * distancia, así que todo lo que él necesita ver de un vistazo va a doble altura:
 * el nombre de la masa, y el nombre + cantidad de cada producto. El detalle de
 * apoyo (libras de masa, bandejas, encargos) va a tamaño normal — leerlo requiere
 * acercarse, y está bien, porque no es lo que decide qué hornear.
 *
 * Mezclar tamaños dentro de una misma línea descuadra el relleno de espacios (la
 * impresora cuenta caracteres, no ancho), así que cada línea se imprime a un solo
 * tamaño y las columnas se calculan con [padBetween] sobre el ancho efectivo:
 * a doble ancho caben la mitad de columnas.
 */
fun buildProductionOrderEscPos(
    data: ProductionOrderPrintData,
    paperWidthMm: Int
): ByteArray {
    val cols = escPosColumns(paperWidthMm)
    val w = EscPosWriter(cols)

    w.init()

    // ── Encabezado ────────────────────────────────────────────────────────────
    w.center()
    w.bold(true); w.size(2, 2)
    w.line("ORDEN DE")
    w.line("PRODUCCION")
    w.normalSize()
    w.size(1, 2)
    if (data.branchName.isNotBlank()) w.line(data.branchName.take(cols))
    w.normalSize(); w.bold(false)
    w.left()
    w.separator('=')

    if (data.shiftName.isNotBlank()) w.line("Turno:   ${data.shiftName}")
    if (data.dateLabel.isNotBlank()) w.line("Fecha:   ${data.dateLabel}")
    if (data.statusLabel.isNotBlank()) w.line("Estado:  ${data.statusLabel}")
    if (data.printedAtLabel.isNotBlank()) w.line("Impreso: ${data.printedAtLabel}")
    w.separator('=')

    // Total del turno a doble altura: es la cifra que el panadero contrasta al final.
    w.bold(true); w.size(1, 2)
    w.line(padBetween(cols, "TOTAL", "${data.totalUnits} u"))
    if (data.totalDoughLb > 0.0) {
        w.line(padBetween(cols, "MASA", "${data.totalDoughLb.fmt(1)} lb"))
    }
    w.normalSize(); w.bold(false)
    w.separator('=')
    w.lf()

    // ── Una sección por masa ──────────────────────────────────────────────────
    data.groups.forEach { group ->
        printGroup(w, group, cols)
        w.lf()
    }

    // ── Pie ───────────────────────────────────────────────────────────────────
    w.separator('=')
    w.center()
    w.line("Fin de la orden")
    if (data.groups.any { line -> line.items.any { it.isSpecial } }) {
        w.line("* = pedido especial")
    }
    w.left()

    w.feedAndCut()
    return w.toByteArray()
}

private fun printGroup(w: EscPosWriter, group: PrintMasaGroup, cols: Int) {
    // Título de la masa: doble ancho y alto. A doble ancho caben cols/2 caracteres.
    w.bold(true); w.size(2, 2)
    w.line(group.title.uppercase().take(cols / 2))
    w.normalSize(); w.bold(false)

    if (group.isFillingOnly) {
        // Masa que nadie pidió: solo se amasa porque otro pan la lleva de relleno.
        // Sin este aviso el panadero no tiene forma de saber que le toca hacerla.
        w.line("NO PEDIDA - solo para relleno")
        if (group.extraDoughFor.isNotBlank()) {
            wrapToWidth("de: ${group.extraDoughFor}", cols, "  ").forEach { w.line(it) }
        }
        if (group.doughLb > 0.0) w.line("${group.doughLb.fmt(1)} lb de masa a amasar")
        w.separator()
        return
    }

    val subtitle = buildString {
        append("${group.totalUnits} u")
        if (group.doughLb > 0.0) append(" - ${group.doughLb.fmt(1)} lb masa")
        if (group.hasSponge) append(" - con esponja")
    }
    wrapToWidth(subtitle, cols, "  ").forEach { w.line(it) }
    if (group.extraDoughLb > 0.0 && group.extraDoughFor.isNotBlank()) {
        wrapToWidth(
            "+ ${group.extraDoughLb.fmt(1)} lb para relleno de ${group.extraDoughFor}",
            cols, "  "
        ).forEach { w.line(it) }
    }
    w.separator()

    group.items.forEach { item ->
        val qty = item.quantity.toString()
        val name = (if (item.isSpecial) "* " else "") + item.name
        // Producto + cantidad a doble altura: el par que el panadero busca.
        w.bold(true); w.size(1, 2)
        w.line(padBetween(cols, name, qty))
        w.normalSize(); w.bold(false)

        // El detalle cuelga del producto con sangría fija; el wrap trabaja sobre el
        // ancho restante para que la sangría no se coma columnas de texto.
        if (item.trayBreakdown.isNotBlank()) {
            wrapToWidth(item.trayBreakdown, cols - 3, "  ").forEach { w.line("   $it") }
        }
        if (item.missingWeight) w.line("   !! SIN PESO CONFIGURADO")
        item.customerNotes.forEach { note ->
            wrapToWidth("> $note", cols - 3, "  ").forEach { w.line("   $it") }
        }
    }
    w.separator()
}
