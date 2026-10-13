package com.bakery.erp.model

import kotlin.math.roundToLong

/**
 * Pago dividido: parte con tarjeta o transferencia y el resto en efectivo.
 *
 * La cajera teclea solo lo que NO es efectivo; el efectivo es lo que queda. Así nunca tiene que
 * cuadrar sumas a mano y no puede quedar un centavo sin cubrir. El vuelto sale solo de la parte
 * en efectivo: de una tarjeta no se da vuelto.
 *
 * Sin fiado (el cargo a la cuenta y la venta tendrían que partirse) y sin descuento de
 * colaborador (premia el pago de contado completo). Decisiones del dueño, oct 2026.
 */

/** Los métodos que se pueden teclear en un pago dividido; el efectivo es el resto. */
val SPLIT_NON_CASH_METHODS = listOf(SalePaymentMethod.CARD, SalePaymentMethod.TRANSFER)

/** Qué impide cobrar así; null si se puede. [nonCash] = método → monto tecleado. */
fun splitPaymentProblem(due: Double, nonCash: Map<String, Double>): String? {
    val amounts = nonCash.filterKeys { it in SPLIT_NON_CASH_METHODS }
    val sum = amounts.values.sum()
    return when {
        amounts.values.any { it < 0.0 } -> "Los montos no pueden ser negativos"
        sum <= 0.004 -> "Anotá cuánto va con tarjeta o transferencia"
        sum > due + 0.004 -> "Tarjeta y transferencia suman más que el total (Q ${due.fmtCents()})"
        else -> null
    }
}

/**
 * Las partes del cobro: lo tecleado más el efectivo que falta, sin las que quedan en cero.
 * Si una sola parte cubre todo, devuelve un mapa de un solo método — es un cobro normal.
 */
fun splitPaymentParts(due: Double, nonCash: Map<String, Double>): Map<String, Double> {
    val typed = SPLIT_NON_CASH_METHODS
        .associateWith { cents(nonCash[it] ?: 0.0) }
        .filterValues { it > 0 }
    val cash = cents(due) - typed.values.sum()
    return buildMap {
        typed.forEach { (m, c) -> put(m, c / 100.0) }
        if (cash > 0) put(SalePaymentMethod.CASH, cash / 100.0)
    }
}

/**
 * Reparte las partes de un cobro entre las ventas que paga (el pan y las cuentas del
 * restaurante del carrito). [sales] = (clave, lo que esa venta cobra hoy), en el orden en que
 * se llenan. Cada venta se llena consumiendo las partes en orden — tarjeta, transferencia,
 * efectivo — así cada venta suma exacto lo suyo y el total por método es el del cobro.
 *
 * En centavos enteros: con Double, Q0.10 + Q0.20 no da Q0.30 y una venta quedaría corta.
 */
fun allocatePaymentParts(
    parts: Map<String, Double>,
    sales: List<Pair<String, Double>>
): Map<String, Map<String, Double>> {
    val order = SPLIT_NON_CASH_METHODS + SalePaymentMethod.CASH
    val remaining = order.filter { (parts[it] ?: 0.0) > 0.0 }
        .associateWith { cents(parts[it] ?: 0.0) }
        .toMutableMap()
    return sales.associate { (key, amount) ->
        var need = cents(amount)
        val mine = mutableMapOf<String, Double>()
        for (method in order) {
            if (need <= 0) break
            val left = remaining[method] ?: continue
            val take = minOf(left, need)
            if (take <= 0) continue
            mine[method] = take / 100.0
            remaining[method] = left - take
            need -= take
        }
        key to mine.toMap()
    }
}

/** El método que va en el encabezado de una venta: el único que tiene, o "Mixto". */
fun headerMethodFor(parts: Map<String, Double>, fallback: String): String =
    when (parts.size) {
        0 -> fallback
        1 -> parts.keys.first()
        else -> SalePaymentMethod.MIXED
    }

/** "Efectivo Q 30.00 · Tarjeta Q 50.00", sin el anticipo (va en su propio renglón). */
fun paymentPartsLabel(parts: Map<String, Double>): String =
    (SPLIT_NON_CASH_METHODS + SalePaymentMethod.CASH + SalePaymentMethod.CREDIT)
        .filter { (parts[it] ?: 0.0) > 0.004 }
        .joinToString(" · ") { "${SalePaymentMethod.label(it)} Q ${parts.getValue(it).fmtCents()}" }

private fun cents(v: Double): Long = (v * 100).roundToLong()

private fun Double.fmtCents(): String {
    val c = cents(this)
    val abs = kotlin.math.abs(c)
    return (if (c < 0) "-" else "") + "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}
