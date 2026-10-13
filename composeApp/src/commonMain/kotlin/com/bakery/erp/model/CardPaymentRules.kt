package com.bakery.erp.model

import kotlin.math.roundToLong

/**
 * Reglas de pago con tarjeta de la empresa (todas las sucursales). Documento
 * `companies/{c}/paymentRules/card`; lo edita quien tiene `MANAGE_POS_CONFIG`.
 *
 * Totalmente opcional: sin documento (o con los valores por defecto) la tarjeta se acepta por
 * cualquier monto y sin recargo, que es como funcionaba antes. Combinaciones posibles: nunca
 * recargo, siempre recargo, recargo solo por debajo de un monto, y además un mínimo con tarjeta.
 *
 * Todo se compara contra lo que pasa POR LA TERMINAL (en un pago dividido, solo la parte con
 * tarjeta): la comisión del banco es sobre eso, y así dividir la cuenta no esquiva el recargo.
 * La cajera no puede quitarlo (decisión del dueño).
 */
data class CardPaymentRules(
    /** Debajo de esto no se acepta tarjeta. 0 = sin mínimo. */
    val minimumAmount: Double = 0.0,
    val surchargeMode: String = CardSurchargeMode.NEVER,
    /** Con [CardSurchargeMode.BELOW]: se recarga si lo de tarjeta es MENOR a esto. */
    val surchargeThreshold: Double = 0.0,
    val surchargeType: String = CardSurchargeType.PERCENT,
    /** Porcentaje (5 = 5%) o quetzales fijos, según [surchargeType]. */
    val surchargeValue: Double = 0.0,
    val updatedAt: Long = 0L,
    val updatedByName: String = ""
)

object CardSurchargeMode {
    const val NEVER = "NEVER"
    const val ALWAYS = "ALWAYS"
    const val BELOW = "BELOW"
}

object CardSurchargeType {
    const val PERCENT = "PERCENT"
    const val FIXED = "FIXED"
}

/** Qué está mal en la configuración; null si se puede guardar. */
fun cardRulesProblem(r: CardPaymentRules): String? = when {
    r.minimumAmount < 0 -> "El mínimo no puede ser negativo"
    r.surchargeMode == CardSurchargeMode.NEVER -> null
    r.surchargeValue <= 0 -> "Escribí cuánto es el recargo"
    r.surchargeType == CardSurchargeType.PERCENT && r.surchargeValue > 100 -> "El porcentaje no puede pasar de 100"
    r.surchargeMode == CardSurchargeMode.BELOW && r.surchargeThreshold <= 0 -> "Escribí desde qué monto ya no se cobra recargo"
    r.surchargeMode == CardSurchargeMode.BELOW && r.minimumAmount > 0 && r.surchargeThreshold <= r.minimumAmount ->
        "El monto del recargo tiene que ser mayor que el mínimo con tarjeta"
    else -> null
}

/** Recargo que lleva [cardAmount] pasado por la terminal, al centavo. 0 = sin recargo. */
fun CardPaymentRules.surchargeFor(cardAmount: Double): Double {
    if (cardAmount <= 0.004 || surchargeValue <= 0.0) return 0.0
    val applies = when (surchargeMode) {
        CardSurchargeMode.ALWAYS -> true
        CardSurchargeMode.BELOW -> cardAmount < surchargeThreshold - 0.004
        else -> false
    }
    if (!applies) return 0.0
    val raw = if (surchargeType == CardSurchargeType.FIXED) surchargeValue else cardAmount * surchargeValue / 100.0
    return (raw * 100).roundToLong() / 100.0
}

/** Null si [cardAmount] se puede pasar con tarjeta; si no, por qué. */
fun CardPaymentRules.minimumProblem(cardAmount: Double): String? =
    if (minimumAmount > 0.004 && cardAmount > 0.004 && cardAmount < minimumAmount - 0.004)
        "Tarjeta desde Q ${minimumAmount.money()}"
    else null

/** "5%" o "Q 2.00", y la condición: lo que ve la cajera y lo que se guarda en la venta. */
fun CardPaymentRules.surchargeDescription(): String {
    if (surchargeMode == CardSurchargeMode.NEVER || surchargeValue <= 0) return "Sin recargo"
    val amount = if (surchargeType == CardSurchargeType.FIXED) "Q ${surchargeValue.money()}"
                 else "${surchargeValue.trimmed()}%"
    return when (surchargeMode) {
        CardSurchargeMode.ALWAYS -> "$amount en todo pago con tarjeta"
        else -> "$amount si es menos de Q ${surchargeThreshold.money()}"
    }
}

/** Nombre del renglón del recargo en el ticket. En la factura no aparece (ver abajo). */
const val CARD_SURCHARGE_LINE_NAME = "Recargo por pago con tarjeta"

/** Clave del recargo en el desglose por producto de los reportes: no es pan vendido. */
const val CARD_SURCHARGE_SALE_KEY = "srg_card"

/**
 * Reparte un recargo entre los renglones de una factura para que NO aparezca descrito (decisión
 * del dueño) y aun así la factura sume exacto lo cobrado — la SAT no admite que falte.
 *
 * Cada renglón sube un número entero de centavos POR UNIDAD (el servidor redondea el precio
 * unitario al centavo, así que un precio de Q1.0333 no se puede). Lo que no se puede repartir
 * así se le carga a un renglón de una unidad; si no hay ninguno, al renglón más grande se le
 * separa una unidad en su propio renglón. Si nada se puede ajustar (sin cantidades enteras),
 * el recargo queda como "Cargo adicional" — mejor que una factura que no cuadra.
 *
 * La MISMA regla está en `functions/fel/nucXmlBuilder.js::absorberRecargo`: el ticket de una
 * venta facturada tiene que decir lo mismo que la factura.
 */
fun absorbSurcharge(lines: List<ReceiptLine>, surcharge: Double): List<ReceiptLine> {
    val s = (surcharge * 100).roundToLong()
    if (s <= 0) return lines
    val qty = lines.map { l -> l.quantity.takeIf { it >= 1.0 && it == kotlin.math.floor(it) }?.toLong() ?: 0L }
    val price = lines.map { (it.unitPrice * 100).roundToLong() }
    val gross = lines.indices.map { i -> if (qty[i] > 0 && price[i] > 0) qty[i] * price[i] else 0L }
    val sum = gross.sum()
    if (sum <= 0) return lines + ReceiptLine("Cargo adicional", 1.0, s / 100.0, s / 100.0)
    val perUnit = lines.indices.map { i -> if (gross[i] > 0) (s * gross[i] / sum) / qty[i] else 0L }.toMutableList()
    var rest = s - lines.indices.sumOf { qty[it] * perUnit[it] }
    val adjustable = lines.indices.filter { gross[it] > 0 }.sortedByDescending { gross[it] }
    var split: Pair<Int, Long>? = null
    if (rest > 0) {
        val single = adjustable.firstOrNull { qty[it] == 1L }
        if (single != null) { perUnit[single] += rest; rest = 0 }
        else split = adjustable.first() to rest
    }
    return lines.indices.flatMap { i ->
        val p = (price[i] + perUnit[i]) / 100.0
        if (gross[i] <= 0) listOf(lines[i])
        else if (split?.first == i) {
            val extra = split.second
            listOf(
                lines[i].copy(quantity = (qty[i] - 1).toDouble(), unitPrice = p, subtotal = (qty[i] - 1) * p),
                lines[i].copy(quantity = 1.0, unitPrice = p + extra / 100.0, subtotal = p + extra / 100.0)
            )
        } else listOf(lines[i].copy(unitPrice = p, subtotal = qty[i] * (price[i] + perUnit[i]) / 100.0))
    }
}

private fun Double.money(): String {
    val c = (this * 100).roundToLong()
    return "${c / 100}.${(kotlin.math.abs(c) % 100).toString().padStart(2, '0')}"
}

private fun Double.trimmed(): String =
    if (this == kotlin.math.floor(this)) this.toLong().toString() else money().trimEnd('0').trimEnd('.')
