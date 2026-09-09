package com.bakery.erp.util

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun Double.fmt(decimals: Int = 2): String {
    if (decimals <= 0) return roundToLong().toString()
    var factor = 1L
    repeat(decimals) { factor *= 10L }
    val negative = this < 0
    val r = (abs(this) * factor).roundToLong()
    val intPart = r / factor
    val decPart = r % factor
    return "${if (negative) "-" else ""}$intPart.${decPart.toString().padStart(decimals, '0')}"
}

/** Como [fmt] pero sin ceros de más al final: "28.00" → "28", "10.60" → "10.6". */
fun Double.fmtTrim(decimals: Int = 2): String = fmt(decimals).trimEnd('0').trimEnd('.')

/**
 * Porcentaje del panadero con decimales según la magnitud. Los insumos de traza
 * (sal, mejorador, 0.625%) pierden demasiado con un solo decimal: redondeados a
 * 0.6% las cuentas que uno hace a mano dejan de cuadrar con lo que pesa la
 * balanza, aunque el valor guardado sea exacto.
 *
 *  0.625 -> "0.625"   ·   2.0 -> "2"   ·   62.5 -> "62.5"   ·   100.0 -> "100"
 */
fun Double.fmtPercentage(): String = when {
    this == 0.0 -> "0"
    abs(this) < 1.0 -> fmt(3).trimEnd('0').trimEnd('.')
    abs(this) < 10.0 -> fmt(2).trimEnd('0').trimEnd('.')
    else -> fmt(1).trimEnd('0').trimEnd('.')
}

/**
 * Formato de moneda con separador de miles. Ej:
 *  - 135000.0.fmtMoney()    -> "135,000.00"
 *  - 1234567.89.fmtMoney(2) -> "1,234,567.89"
 *  - 50.0.fmtMoney(0)       -> "50"
 *
 * Multiplataforma — implementación manual sin depender de java.text.
 */
fun Double.fmtMoney(decimals: Int = 2): String {
    val negative = this < 0
    var factor = 1L
    repeat(decimals) { factor *= 10L }
    val rounded = (abs(this) * factor).roundToLong()
    val intPart = rounded / factor
    val decPart = rounded % factor

    val intStr = intPart.toString()
    val withSeparators = buildString {
        intStr.reversed().forEachIndexed { idx, c ->
            if (idx > 0 && idx % 3 == 0) append(',')
            append(c)
        }
    }.reversed()

    val sign = if (negative) "-" else ""
    return if (decimals <= 0) "$sign$withSeparators"
    else "$sign$withSeparators.${decPart.toString().padStart(decimals, '0')}"
}

/**
 * Hora de reloj de un epoch-millis, en 12 horas ("2:35 p.m.") — que es como se lee una hora
 * acá y como la muestra ComandaYa. Devuelve "" si no hay marca.
 */
fun Long.fmtClockTime(): String {
    if (this <= 0L) return ""
    val t = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val h24 = t.hour
    val h12 = when {
        h24 == 0 -> 12
        h24 > 12 -> h24 - 12
        else -> h24
    }
    val suffix = if (h24 < 12) "a.m." else "p.m."
    return "$h12:${t.minute.toString().padStart(2, '0')} $suffix"
}
