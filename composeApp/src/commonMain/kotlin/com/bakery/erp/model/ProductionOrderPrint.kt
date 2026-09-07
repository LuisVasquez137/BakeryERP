package com.bakery.erp.model

/**
 * Snapshot imprimible de la orden de producción de un lote — lo que el panadero
 * necesita ver en papel mientras hornea, agrupado por masa igual que en pantalla.
 *
 * Es un snapshot y no una referencia al [ProductionLot] a propósito: el formato del
 * papel no debe acoplarse al modelo de lote (mismo criterio que [ReceiptData] con
 * `Sale`), y así el builder de ESC/POS y el de HTML comparten exactamente los mismos
 * números que la pantalla ya calculó.
 */
data class ProductionOrderPrintData(
    /** Planta productora — encabezado grande del papel. */
    val branchName: String,
    val shiftName: String,
    /** Fecha de producción ya formateada ("02/09/2026"). */
    val dateLabel: String,
    val statusLabel: String,
    /** Momento de la impresión ("02/09/2026 05:12") — para no confundir dos copias. */
    val printedAtLabel: String,
    val groups: List<PrintMasaGroup>
) {
    val totalUnits: Int get() = groups.sumOf { it.totalUnits }
    val totalDoughLb: Double get() = groups.sumOf { it.doughLb }
}

/** Una masa (receta) con los productos que salen de ella. */
data class PrintMasaGroup(
    val title: String,
    val totalUnits: Int,
    /** Masa total a amasar en libras — incluye [extraDoughLb]. */
    val doughLb: Double,
    val hasSponge: Boolean,
    /** Masa extra que otros productos llevan como relleno de ESTA receta. */
    val extraDoughLb: Double = 0.0,
    /** Productos que aportan esa masa extra ("Pan de Capa, Gusanito"). */
    val extraDoughFor: String = "",
    /**
     * Masa que nadie pidió pero que hay que amasar igual porque otro producto la
     * lleva de relleno: no tiene productos propios que contar.
     */
    val isFillingOnly: Boolean = false,
    val items: List<PrintProductLine> = emptyList()
)

data class PrintProductLine(
    val name: String,
    val quantity: Int,
    /** "4 band. + 8 sueltos" — vacío si el producto no tiene bandeja configurada. */
    val trayBreakdown: String = "",
    /** Pedido especial: figura fuera de catálogo, se marca con ★ / * en el papel. */
    val isSpecial: Boolean = false,
    /** Sin `rawWeightGrams`: no entró al cálculo de masa y hay que avisarlo. */
    val missingWeight: Boolean = false,
    /** "Doña María: 5" — encargos de cliente que viven dentro de esta línea. */
    val customerNotes: List<String> = emptyList()
)
