package com.bakery.erp.model

import kotlin.math.min
import kotlin.math.round

/**
 * Anticipo de un cliente por un encargo (pan, un pastel, comida del restaurante).
 *
 * Es dinero que **entra a una gaveta hoy pero todavía no es venta**: si el cliente cancela, se
 * le devuelve. Por eso al recibirlo solo deja un movimiento de entrada en la caja donde se
 * recibió (el cuadre de ESA caja lo explica) y no suma al reporte de ventas. La venta ocurre el
 * día de la entrega, en cualquier caja o sucursal: ahí el anticipo se aplica como forma de pago
 * ([SalePaymentMethod.CUSTOMER_DEPOSIT]) que no mueve efectivo, y solo entra el saldo.
 *
 * Reemplaza el papel donde se anotaba "ya pagó". El [folio] va impreso en el comprobante que se
 * queda el cliente y es por lo que se busca al entregar.
 *
 * `companies/{c}/customerDeposits/{id}`. No es el adelanto a colaboradores (Créditos): aquél sale
 * de la gaveta, éste entra.
 */
data class CustomerDeposit(
    val id: String = "",
    /** "ANT-0042": correlativo por empresa, asignado en transacción al crearlo. */
    val folio: String = "",
    val status: String = CustomerDepositStatus.OPEN,
    val customerName: String = "",
    val customerPhone: String = "",
    /** Qué encargó ("3 docenas de pirujo", "pastel de chocolate", "almuerzo para 10"). */
    val description: String = "",
    /** Para cuándo, como lo dijo el cliente. Texto: "sábado 10 am" es un dato válido. */
    val deliveryLabel: String = "",
    val amount: Double = 0.0,
    /** CASH | CARD | TRANSFER. Solo el efectivo entra a la gaveta como movimiento. */
    val method: String = SalePaymentMethod.CASH,
    val branchId: String = "",
    val branchName: String = "",
    val sessionId: String = "",
    val registerName: String = "",
    val receivedByUserId: String = "",
    val receivedByUserName: String = "",
    val receivedAt: Long = 0L,
    /** Cuánto ya se aplicó a ventas. Puede ser parcial: el encargo se recoge en dos veces. */
    val appliedAmount: Double = 0.0,
    val appliedSaleIds: List<String> = emptyList(),
    val lastAppliedAt: Long = 0L,
    val lastAppliedBranchName: String = "",
    val refundedAmount: Double = 0.0,
    val refundedAt: Long = 0L,
    val refundedByUserName: String = "",
    val refundReason: String = "",
    val notes: String = "",
    /**
     * Pedido de cliente (pastel, encargo especial) al que pertenece. Vacío = anticipo suelto
     * (pan, comida del restaurante). Ligado, recibirlo registra el pago en el pedido y aplicarlo
     * al entregar cierra el pedido.
     */
    val customerOrderId: String = "",
    /** "Pastel · 12/10 · Juan Pérez": lo que se muestra sin abrir el pedido. */
    val customerOrderLabel: String = ""
) {
    /** Lo que todavía se puede aplicar o devolver. */
    val remaining: Double get() = roundMoney(amount - appliedAmount - refundedAmount).coerceAtLeast(0.0)
    val isOpen: Boolean get() = status == CustomerDepositStatus.OPEN && remaining > 0.004
}

object CustomerDepositStatus {
    const val OPEN = "OPEN"
    /** Aplicado completo a ventas. */
    const val APPLIED = "APPLIED"
    /** Se devolvió lo que quedaba (el cliente canceló). */
    const val REFUNDED = "REFUNDED"

    fun label(status: String) = when (status) {
        OPEN -> "Pendiente de aplicar"
        APPLIED -> "Aplicado"
        REFUNDED -> "Devuelto"
        else -> status
    }
}

/** "ANT-0042". Cuatro cifras alcanzan para años; con más, simplemente crece. */
fun customerDepositFolio(number: Long): String = "ANT-" + number.toString().padStart(4, '0')

/**
 * ¿Este anticipo es el que busca la cajera? Por folio (con o sin "ANT-", con o sin ceros),
 * nombre (sin tildes, por palabras en cualquier orden) o teléfono (solo dígitos).
 */
fun CustomerDeposit.matches(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val digits = q.filter { it.isDigit() }
    if (digits.isNotEmpty() && digits.length == q.filter { !it.isWhitespace() && it != '-' }.length) {
        val folioNumber = folio.filter { it.isDigit() }.trimStart('0')
        if (folioNumber.isNotEmpty() && folioNumber == digits.trimStart('0')) return true
        if (digits.length >= 4 && customerPhone.filter { it.isDigit() }.contains(digits)) return true
    }
    if (folio.equals(q, ignoreCase = true)) return true
    // Misma normalización que la búsqueda de compras (PurchaseSearch.kt).
    val haystack = normalizeForSearch("$customerName $description")
    return normalizeForSearch(q).split(' ').filter { it.isNotBlank() }.all { it in haystack }
}

/**
 * Cuánto del anticipo va a cada venta de un cobro. Primero las cuentas del restaurante y después
 * el pan, en el orden en que vienen: el encargo típico del restaurante es la comida, y así el
 * anticipo cae donde el cliente lo dejó. Nunca más que el total de cada venta ni que [available].
 * Las ventas a las que no les toca nada no aparecen.
 */
fun planDepositApplication(available: Double, saleTotals: List<Pair<String, Double>>): Map<String, Double> {
    var left = roundMoney(available)
    val out = linkedMapOf<String, Double>()
    for ((id, total) in saleTotals) {
        if (left <= 0.004) break
        val take = roundMoney(min(left, total))
        if (take > 0.004) {
            out[id] = take
            left = roundMoney(left - take)
        }
    }
    return out
}

/**
 * Formas de pago de una venta a la que se le aplicó [depositAmount] del anticipo: el anticipo
 * aparte (no suma a la gaveta) y el resto con el método del cobro. Sin anticipo queda vacío,
 * que es "todo por el método de la venta", como siempre.
 */
fun paymentBreakdownWithDeposit(total: Double, depositAmount: Double, method: String): Map<String, Double> {
    if (depositAmount <= 0.004) return emptyMap()
    val rest = roundMoney(total - depositAmount)
    return buildMap {
        put(SalePaymentMethod.CUSTOMER_DEPOSIT, roundMoney(depositAmount))
        if (rest > 0.004) put(method, rest)
    }
}

private fun roundMoney(v: Double): Double = round(v * 100) / 100
