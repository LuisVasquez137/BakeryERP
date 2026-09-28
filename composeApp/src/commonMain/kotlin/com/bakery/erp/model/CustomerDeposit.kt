package com.bakery.erp.model

import kotlin.math.min
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
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
    /**
     * Cuándo se entrega, fecha y hora exactas (epoch millis). Es un dato y no un texto porque de
     * él cuelgan los recordatorios: "sábado 10 am" no se puede ordenar ni avisar. 0 = sin fecha
     * (anticipos anteriores, que solo traían [deliveryLabel]).
     */
    val deliveryAt: Long = 0L,
    /** Texto de la entrega. Se arma de [deliveryAt]; en anticipos viejos es lo que se tecleó. */
    val deliveryLabel: String = "",
    /**
     * Qué encargó, del catálogo. Vacío en un encargo del restaurante: los platillos viven en
     * ComandaYa y BakeOS no los conoce, así que ahí el encargo es [description] en texto.
     */
    val items: List<CustomerDepositItem> = emptyList(),
    /** Encargo del restaurante (comida): se describe en texto, no con el catálogo. */
    val isRestaurant: Boolean = false,
    /**
     * Sucursal donde se recoge. Puede no ser la que recibió el anticipo ([branchId]): el cliente
     * deja el anticipo en Bombero y recoge en Pacajá. Vacío = la misma donde se recibió
     * (anticipos anteriores a este campo).
     */
    val pickupBranchId: String = "",
    val pickupBranchName: String = "",
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
    /** Dónde se recoge: la elegida, o la misma donde se dejó el anticipo. */
    val effectivePickupBranchName: String get() = pickupBranchName.ifBlank { branchName }
    val picksUpElsewhere: Boolean get() = pickupBranchId.isNotBlank() && pickupBranchId != branchId
}

/** Un producto del catálogo dentro de un encargo. El precio es el de hoy: sirve de referencia. */
data class CustomerDepositItem(
    val productId: String = "",
    val productName: String = "",
    val quantity: Double = 0.0,
    val unitPrice: Double = 0.0
) {
    val subtotal: Double get() = roundMoney(quantity * unitPrice)
}

/** "2 Pastel tres leches, 3 Docena de pirujo": el encargo en una línea. */
fun depositItemsDescription(items: List<CustomerDepositItem>): String =
    items.joinToString(", ") { i ->
        val q = if (i.quantity % 1.0 == 0.0) i.quantity.toLong().toString() else i.quantity.toString()
        "$q ${i.productName}"
    }

/** Lo que costaría el encargo a precio de hoy: referencia para el anticipo, no un cobro. */
fun depositItemsTotal(items: List<CustomerDepositItem>): Double = roundMoney(items.sumOf { it.subtotal })

private val WEEKDAYS = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")

/**
 * "Hoy 10:30 AM", "Mañana 4:00 PM", "sáb 12/10 10:30 AM". Lo relativo va primero porque es
 * lo que la cajera necesita leer rápido: qué se entrega hoy.
 */
fun deliveryDayLabel(deliveryAt: Long, nowMillis: Long, timeZone: kotlinx.datetime.TimeZone): String {
    if (deliveryAt <= 0L) return ""
    val at = kotlin.time.Instant.fromEpochMilliseconds(deliveryAt).toLocalDateTime(timeZone)
    val today = kotlin.time.Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date
    val days = today.daysUntil(at.date)
    val day = when (days) {
        0 -> "Hoy"
        1 -> "Mañana"
        -1 -> "Ayer"
        else -> "${WEEKDAYS[at.date.dayOfWeek.ordinal]} ${at.date.dayOfMonth.toString().padStart(2, '0')}/" +
            at.date.monthNumber.toString().padStart(2, '0')
    }
    val h12 = when { at.hour == 0 -> 12; at.hour > 12 -> at.hour - 12; else -> at.hour }
    val ampm = if (at.hour < 12) "AM" else "PM"
    return "$day $h12:${at.minute.toString().padStart(2, '0')} $ampm"
}

private val WEEKDAYS_LONG = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

/** "Miércoles 10/12/2026": la fecha elegida con su día, para no confundir la semana. */
fun longDateLabel(date: kotlinx.datetime.LocalDate): String =
    "${WEEKDAYS_LONG[date.dayOfWeek.ordinal]} ${date.dayOfMonth.toString().padStart(2, '0')}/" +
        "${date.monthNumber.toString().padStart(2, '0')}/${date.year}"

/**
 * Qué impide aceptar esta entrega, o null si está bien. No se da anticipo para algo que ya
 * tenía que entregarse: ni un día pasado, ni hoy a una hora que ya pasó.
 */
fun depositDeliveryProblem(deliveryAt: Long, nowMillis: Long): String? = when {
    deliveryAt <= 0L -> "Falta la fecha y hora de entrega."
    deliveryAt <= nowMillis -> "Esa fecha y hora de entrega ya pasaron."
    else -> null
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
