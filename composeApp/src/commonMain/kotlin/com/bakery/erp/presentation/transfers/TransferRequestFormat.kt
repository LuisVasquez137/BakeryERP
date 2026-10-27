package com.bakery.erp.presentation.transfers

import com.bakery.erp.model.StockTransfer
import com.bakery.erp.model.tripTitle
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Inicio del día de hoy + [plusDays], en millis (zona del equipo). */
internal fun startOfDayMillis(plusDays: Int = 0): Long {
    val tz = TimeZone.currentSystemDefault()
    val day = Clock.System.now().toLocalDateTime(tz).date.plus(plusDays, DateTimeUnit.DAY)
    return LocalDateTime(day, LocalTime(0, 0, 0)).toInstant(tz).toEpochMilliseconds()
}

/** "hoy", "mañana", "ayer" o "18/10". */
internal fun forDateLabel(millis: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val day = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date
    return when (day) {
        today -> "hoy"
        today.plus(1, DateTimeUnit.DAY) -> "mañana"
        today.plus(-1, DateTimeUnit.DAY) -> "ayer"
        else -> "${day.dayOfMonth.toString().padStart(2, '0')}/${day.monthNumber.toString().padStart(2, '0')}"
    }
}

/** "Para mañana · 1er viaje". Vacío si el traslado no va en el reparto. */
internal fun StockTransfer.deliveryLabel(): String =
    if (!ridesDelivery) "" else "Para ${forDateLabel(forDate)} · ${tripTitle(trip)}"
