package com.bakery.erp.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class BookValueResult(
    val bookValue: Double,
    val accumulatedDepreciation: Double,
    val monthsElapsed: Int,
    val monthsRemaining: Int
)

/**
 * Calcula el valor en libros de un activo bajo depreciación lineal.
 *
 * - Si `usefulLifeMonths <= 0` o `purchaseDate == 0L`: no se deprecia, bookValue = purchaseCost.
 * - Si ya pasó toda la vida útil: bookValue = residualValue.
 */
fun calculateBookValue(
    purchaseCost: Double,
    residualValue: Double,
    usefulLifeMonths: Int,
    purchaseDate: Long,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds()
): BookValueResult {
    if (usefulLifeMonths <= 0 || purchaseDate <= 0L || nowMillis < purchaseDate) {
        return BookValueResult(
            bookValue = purchaseCost,
            accumulatedDepreciation = 0.0,
            monthsElapsed = 0,
            monthsRemaining = usefulLifeMonths.coerceAtLeast(0)
        )
    }

    val monthsElapsed = monthsBetween(purchaseDate, nowMillis).coerceAtLeast(0)
    val cappedMonths = monthsElapsed.coerceAtMost(usefulLifeMonths)
    val depreciableAmount = (purchaseCost - residualValue).coerceAtLeast(0.0)
    val monthlyDepreciation = depreciableAmount / usefulLifeMonths
    val accumulated = (monthlyDepreciation * cappedMonths).coerceAtMost(depreciableAmount)
    val bookValue = (purchaseCost - accumulated).coerceAtLeast(residualValue)
    return BookValueResult(
        bookValue = bookValue,
        accumulatedDepreciation = accumulated,
        monthsElapsed = monthsElapsed,
        monthsRemaining = (usefulLifeMonths - cappedMonths).coerceAtLeast(0)
    )
}

private fun monthsBetween(fromMillis: Long, toMillis: Long): Int {
    val tz = TimeZone.currentSystemDefault()
    val from = Instant.fromEpochMilliseconds(fromMillis).toLocalDateTime(tz)
    val to = Instant.fromEpochMilliseconds(toMillis).toLocalDateTime(tz)
    var months = (to.year - from.year) * 12 + (to.monthNumber - from.monthNumber)
    if (to.dayOfMonth < from.dayOfMonth) months -= 1
    return months
}
