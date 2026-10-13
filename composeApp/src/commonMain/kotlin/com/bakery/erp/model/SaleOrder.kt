package com.bakery.erp.model

/**
 * Un cobro tal como lo vivió el cliente: el pan del mostrador y la cuenta del restaurante que
 * pagó en la misma pasada son UNA cuenta, aunque se guarden como dos ventas (cada una con su
 * canal, para los reportes). El historial y "Ventas de esta caja" listan cobros, y desde el
 * detalle se factura el cobro completo en una sola factura, igual que al cobrar.
 *
 * [key] es el [Sale.checkoutId]; las ventas viejas sin él (o las cuentas del restaurante que
 * se recibieron sin pasar por el carrito) son su propio cobro con la clave `sale_<id>`.
 */
data class SaleOrder(
    val key: String,
    /** Pan primero y después las cuentas del restaurante, como salen en el ticket. */
    val sales: List<Sale>
) {
    val primary: Sale get() = sales.first()
    val at: Long get() = sales.maxOf { maxOf(it.createdAt, it.collectedAt) }
    val registerId: String get() = primary.registerId
    val registerName: String get() = primary.registerName
    val cashierName: String get() = primary.cashierName
    val total: Double get() = kotlin.math.round(sales.sumOf { it.total } * 100) / 100
    val hasRestaurant: Boolean get() = sales.any { it.isRestaurantSale }
    val hasBakery: Boolean get() = sales.any { !it.isRestaurantSale }
    val voided: Boolean get() = sales.all { it.status == SaleStatus.VOIDED }

    /** Lo cobrado por método sumando las ventas: en un pago dividido cada una lleva su parte. */
    val paymentParts: Map<String, Double>
        get() = sales.filter { it.status == SaleStatus.COMPLETED }
            .flatMap { it.effectivePaymentBreakdown.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, v) -> kotlin.math.round(v.sum() * 100) / 100 }
            .filterValues { it > 0.004 }

    val paymentMethod: String
        get() = paymentParts.keys.singleOrNull() ?: if (paymentParts.size > 1) SalePaymentMethod.MIXED else primary.paymentMethod

    /** Alguna venta del cobro tiene factura (emitida, pedida, anulada o de ComandaYa). */
    val hasInvoice: Boolean get() = sales.any { it.invoiceStatus.isNotBlank() }

    /** Lo que todavía se puede facturar: las ventas cobradas que no tienen ninguna factura. */
    val uninvoicedSales: List<Sale>
        get() = sales.filter { it.invoiceStatus.isBlank() && it.status == SaleStatus.COMPLETED && it.total > 0.0 }

    /** La venta que representa la factura del cobro (todas las de un cobro comparten documento). */
    val invoicedSale: Sale? get() = sales.firstOrNull { it.invoiceStatus.isNotBlank() && it.dteId.isNotBlank() }
        ?: sales.firstOrNull { it.invoiceStatus.isNotBlank() }

    /** "Pan · Restaurante #47 · Mesa 4": de qué fue el cobro, sin tener que abrirlo. */
    val description: String
        get() = buildList {
            if (hasBakery) add(if (sales.count { !it.isRestaurantSale } == 1) "Pan · ${sales.first { !it.isRestaurantSale }.lineCount} ítem(s)" else "Pan")
            sales.filter { it.isRestaurantSale }.forEach { add(restaurantSaleLabel(it)) }
        }.joinToString(" · ")
}

val Sale.isRestaurantSale: Boolean
    get() = salesChannel == SalesChannel.RESTAURANT || source == SaleSource.COMANDAYA

/** "Restaurante · #47 · Mesa 4", igual que el renglón del carrito y del ticket. */
fun restaurantSaleLabel(sale: Sale): String =
    listOfNotNull(
        "Restaurante",
        sale.externalOrderNumber.takeIf { it.isNotEmpty() }?.let { "#$it" },
        sale.externalTableLabel.ifEmpty { null }
    ).joinToString(" · ")

/**
 * Agrupa las ventas en cobros, del más reciente al más viejo. Las cuentas del restaurante que
 * todavía esperan a la caja no son un cobro: se dejan fuera.
 */
fun ordersFromSales(sales: List<Sale>): List<SaleOrder> =
    sales.filter { it.status != SaleStatus.PENDING_COLLECTION }
        .groupBy { it.checkoutId.ifEmpty { "sale_" + it.id } }
        .map { (key, group) ->
            SaleOrder(key, group.sortedWith(compareBy<Sale> { it.isRestaurantSale }.thenBy { it.createdAt }))
        }
        .sortedByDescending { it.at }

/** Filtro de factura de la lista de cobros. */
enum class OrderInvoiceFilter(val label: String) {
    ALL("Todas"),
    NOT_INVOICED("Sin factura"),
    INVOICED("Con factura");

    fun accepts(order: SaleOrder): Boolean = when (this) {
        ALL -> true
        NOT_INVOICED -> !order.voided && order.uninvoicedSales.isNotEmpty()
        INVOICED -> order.hasInvoice
    }
}

/**
 * Búsqueda del cobro que el cliente viene a reclamar. Con números busca por monto ("35" encuentra
 * Q35.00 y Q35.50; también el total de solo el pan o solo el restaurante) y por número de orden
 * del restaurante; con letras, por mesa, cajero, revendedor o a nombre de quién se facturó.
 */
fun SaleOrder.matches(query: String): Boolean {
    val q = query.trim().removePrefix("Q").removePrefix("q").trim()
    if (q.isEmpty()) return true
    val numeric = q.all { it.isDigit() || it == '.' || it == ',' }
    if (numeric) {
        val n = q.replace(',', '.')
        val amounts = listOf(total) + sales.map { it.total }
        return amounts.any { a -> money2(a).startsWith(n) } ||
            sales.any { it.externalOrderNumber == q }
    }
    val text = q.lowercase()
    return sales.any { s ->
        listOf(s.externalTableLabel, s.cashierName, s.resellerName, s.invoiceReceptorName, s.creditAccountName, s.externalUserName)
            .any { it.lowercase().contains(text) }
    }
}

/** 35 → "35.00", 35.5 → "35.50": la forma en que se busca un monto. */
private fun money2(v: Double): String {
    val cents = kotlin.math.round(v * 100).toLong()
    val abs = kotlin.math.abs(cents)
    return (if (cents < 0) "-" else "") + "${abs / 100}." + (abs % 100).toString().padStart(2, '0')
}
