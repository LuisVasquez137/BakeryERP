package com.bakery.erp.model

/**
 * Anular una venta ya cobrada: el cliente devolvió el pan, o la cajera se equivocó.
 *
 * El robo que esto puede esconder es el clásico: el cliente paga en efectivo y se va, la cajera
 * anula y se queda el dinero — la caja cuadra perfecto. Decisiones tomadas con el dueño (oct
 * 2026): sin PIN de autorizador, pero con **motivo obligatorio**, **aviso push al dueño** en
 * cada anulación, registro en la bitácora de cajas y la lista de anuladas **en el cuadre**. Lo
 * que disuade es que se ve.
 *
 * Se anula el cobro entero, nunca una parte: una devolución parcial es anular y volver a cobrar
 * lo que sí se lleva. Es un solo camino y encaja con la factura, que con CF no admite nota de
 * crédito (se anula y se emite otra).
 */
data class SaleVoidRequest(
    val reason: String,
    val userId: String,
    val userName: String,
    val employeeId: String = ""
)

/** Motivos frecuentes: tocarlos llena el campo. */
val SALE_VOID_REASONS = listOf(
    "Cliente devolvió el pan",
    "Me equivoqué de producto",
    "Me equivoqué de cantidad",
    "Cobro duplicado"
)

/** Mínimo de letras del motivo: "x" no explica nada al dueño que lee el aviso. */
const val MIN_SALE_VOID_REASON = 5

/**
 * Por qué este cobro NO se puede anular desde la caja, o null si sí se puede.
 *
 * [session] es la sesión de caja de la venta (null si no se encontró). Solo se anula dentro del
 * turno abierto: después del cierre, la gaveta ya se contó y entregó, y devolver dinero de un
 * turno cerrado lo sacaría de una caja que hoy tiene otra persona.
 */
fun saleVoidBlocker(order: SaleOrder, session: CashSession?): String? {
    val sales = order.sales
    if (order.voided) return "Este cobro ya está anulado."
    if (sales.any { it.status != SaleStatus.COMPLETED }) return "Este cobro tiene ventas que no están completadas."
    if (sales.any { it.isRestaurantSale || it.source != SaleSource.POS })
        return "Este cobro incluye una cuenta del restaurante: todavía no se puede anular desde aquí."
    val methods = sales.flatMap { it.effectivePaymentBreakdown.filterValues { v -> v > 0.004 }.keys }.toSet()
    if (SalePaymentMethod.CREDIT in methods)
        return "Es una venta a crédito: todavía no se puede anular desde aquí."
    if (SalePaymentMethod.CUSTOMER_DEPOSIT in methods || sales.any { it.depositAmount > 0.004 })
        return "Se pagó con un anticipo: todavía no se puede anular desde aquí."
    if (sales.any { it.linkedCustomerOrderId.isNotBlank() })
        return "Es el cobro de un encargo: todavía no se puede anular desde aquí."
    if (sales.any { it.invoiceStatus in listOf(FelInvoiceStatus.SOLICITADA, FelInvoiceStatus.CERTIFICANDO, FelInvoiceStatus.PENDIENTE) })
        return "La factura todavía se está emitiendo. Espera a que salga y vuelve a intentar."
    if (sales.any { it.invoiceConflict }) return "Este cobro tiene dos facturas: primero hay que resolver eso."
    val sessionIds = sales.map { it.sessionId }.distinct()
    if (sessionIds.size != 1 || sessionIds.first().isBlank()) return "No se sabe de qué caja es este cobro."
    if (session == null || session.id != sessionIds.first()) return "No se encontró la caja de este cobro."
    if (session.status != CashSessionStatus.OPEN)
        return "La caja de este cobro ya se cerró. Solo se anulan ventas del turno abierto."
    return null
}

/** Lo que sale de la gaveta al anular: la parte del cobro que se pagó en efectivo. */
val Sale.voidCashAmount: Double
    get() = effectivePaymentBreakdown[SalePaymentMethod.CASH] ?: 0.0

/**
 * Lo que se pagó con tarjeta, transferencia o PedidosYa: se devuelve por el banco (o por
 * PedidosYa), no de la gaveta.
 */
val Sale.voidBankAmount: Double
    get() = effectivePaymentBreakdown.filterKeys { it in SalePaymentMethod.nonCashSettled }.values.sum()

/**
 * Lo que la anulación le hace a la sesión de caja. Función pura para poder probarla.
 *
 * - El **efectivo** sale de las ventas en efectivo: la cajera se lo devuelve al cliente de la
 *   gaveta, así que el esperado baja lo mismo y la caja sigue cuadrando.
 * - La **tarjeta/transferencia** NO baja de su total: el voucher sigue en la gaveta y pasa de
 *   turno en turno como siempre (`shiftHandover`). Se devuelve por el banco y queda anotado
 *   aparte ([CashSession.totalVoidedNonCash]).
 * - El total vendido y el número de ventas bajan: la venta no ocurrió.
 */
fun CashSession.withSaleVoided(sale: Sale): CashSession {
    val parts = sale.effectivePaymentBreakdown
    // incrementSaleTotals suma una venta por cada método, salvo el anticipo.
    val counted = parts.keys.count { it != SalePaymentMethod.CUSTOMER_DEPOSIT }
    return copy(
        totalCashSales = totalCashSales - sale.voidCashAmount,
        totalSales = totalSales - sale.total,
        saleCount = (saleCount - counted).coerceAtLeast(0),
        voidedSaleCount = voidedSaleCount + 1,
        totalVoidedSales = totalVoidedSales + sale.total,
        totalVoidedNonCash = totalVoidedNonCash + sale.voidBankAmount
    )
}
