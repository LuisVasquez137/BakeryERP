package com.bakery.erp.model

import kotlin.math.abs
import kotlin.math.round

/**
 * Corregir la forma de pago de un cobro ya hecho: se pagó con tarjeta y la cajera marcó
 * efectivo (o al revés). Primer día real en Democracia (26 oct 2026): un cobro de tarjeta quedó
 * como efectivo y la caja salió con faltante de efectivo y sobrante de vouchers por lo mismo.
 *
 * El total del cobro no cambia, solo a qué método se suma. Lo que mueve en la caja se escribe
 * como diferencia (sumas en el servidor) y queda en la bitácora con aviso al dueño: pasar un
 * efectivo a tarjeta es otra forma de quedarse con el dinero, y lo que lo frena es que se vea
 * (y que los vouchers ya se cuentan al cerrar).
 *
 * Se corrige el cobro entero a UN método. Un pago dividido mal registrado se pasa entero a un
 * método; partirlo de nuevo no se puede desde aquí.
 */

/** Métodos a los que se puede corregir: los del cobro en el POS. */
val PAYMENT_CHANGE_METHODS: List<String> = SalePaymentMethod.checkout

/** Total de la sesión al que suma cada método, o null si no suma a ninguno (anticipo). */
fun sessionTotalField(method: String): String? = when (method) {
    SalePaymentMethod.CASH -> "totalCashSales"
    SalePaymentMethod.CARD -> "totalCardSales"
    SalePaymentMethod.TRANSFER -> "totalTransferSales"
    SalePaymentMethod.PEDIDOS_YA -> "totalPedidosYaSales"
    SalePaymentMethod.CREDIT -> "totalCreditSales"
    else -> null
}

/**
 * Por qué este cobro no se puede corregir, o null si sí. [session] es la caja del cobro.
 *
 * Una caja cerrada sí se corrige (es justo cuando aparece el error: al cuadrar), salvo que se
 * haya entregado a otro turno: sus vouchers ya pasaron al siguiente y habría que corregir la
 * cadena entera.
 */
fun paymentChangeBlocker(order: SaleOrder, session: CashSession?): String? {
    val sales = order.sales
    if (order.voided) return "Este cobro está anulado."
    if (sales.any { it.status != SaleStatus.COMPLETED }) return "Este cobro tiene ventas que no están completadas."
    val methods = sales.flatMap { it.effectivePaymentBreakdown.filterValues { v -> v > 0.004 }.keys }.toSet()
    if (SalePaymentMethod.CREDIT in methods) return "Es una venta a crédito: no se cambia la forma de pago."
    if (SalePaymentMethod.CUSTOMER_DEPOSIT in methods || sales.any { it.depositAmount > 0.004 })
        return "Se pagó con un anticipo: no se cambia la forma de pago."
    val sessionIds = sales.map { it.sessionId }.distinct()
    if (sessionIds.size != 1 || sessionIds.first().isBlank()) return "No se sabe de qué caja es este cobro."
    if (session == null || session.id != sessionIds.first()) return "No se encontró la caja de este cobro."
    if (session.status == CashSessionStatus.CLOSED && session.receivedByEmployeeName.isNotBlank())
        return "La caja de este cobro se entregó a ${session.receivedByEmployeeName} en el turno siguiente: " +
            "no se puede corregir desde aquí."
    return null
}

/** Quién puede corregir: en la caja abierta, quien anula ventas; en una cerrada, quien ve la bitácora. */
fun paymentChangePermission(session: CashSession?): Permission =
    if (session?.status == CashSessionStatus.CLOSED) Permission.VIEW_CASH_AUDIT else Permission.VOID_SALE

/** La venta con todo su total en [method]. */
fun Sale.withPaymentMethod(method: String): Sale =
    copy(paymentMethod = method, paymentBreakdown = mapOf(method to total))

/**
 * Lo que la corrección le cambia a la sesión: campo → diferencia (sumas en el servidor).
 *
 * Abierta, solo los totales por método (el esperado se calcula de ellos). Cerrada, además lo que
 * el cierre ya fijó: el efectivo esperado y la diferencia (lo contado no cambia), los vouchers
 * esperados y lo que quedó para recoger.
 */
fun paymentChangeSessionDeltas(session: CashSession, before: Sale, after: Sale): Map<String, Double> {
    val deltas = linkedMapOf<String, Double>()
    fun add(field: String, v: Double) {
        val next = round(((deltas[field] ?: 0.0) + v) * 100) / 100
        if (abs(next) > 0.004) deltas[field] = next else deltas.remove(field)
    }
    before.effectivePaymentBreakdown.forEach { (m, a) -> sessionTotalField(m)?.let { add(it, -a) } }
    after.effectivePaymentBreakdown.forEach { (m, a) -> sessionTotalField(m)?.let { add(it, a) } }
    // La caja cuenta una venta por cada método del desglose (incrementSaleTotals), salvo anticipo.
    fun Sale.parts() = effectivePaymentBreakdown.keys.count { it != SalePaymentMethod.CUSTOMER_DEPOSIT }
    (after.parts() - before.parts()).takeIf { it != 0 }?.let { deltas["saleCount"] = it.toDouble() }
    if (session.status == CashSessionStatus.CLOSED) {
        val cash = deltas["totalCashSales"] ?: 0.0
        val card = deltas["totalCardSales"] ?: 0.0
        val transfer = deltas["totalTransferSales"] ?: 0.0
        add("expectedCash", cash)
        add("difference", -cash)
        if (session.countedCardVouchers != null) add("expectedCardVouchers", card)
        if (session.handoverRecorded) {
            add("carriedOutCard", card)
            add("carriedOutTransfer", transfer)
        }
    }
    return deltas
}
