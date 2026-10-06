package com.bakery.erp.presentation.delivery

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** "06/10/2026" en la zona del aparato. */
internal fun formatDay(millis: Long): String {
    if (millis == 0L) return "—"
    val tz = TimeZone.currentSystemDefault()
    val ld: LocalDateTime = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)
    val dd = ld.date.dayOfMonth.toString().padStart(2, '0')
    val mm = ld.date.monthNumber.toString().padStart(2, '0')
    return "$dd/$mm/${ld.date.year}"
}
