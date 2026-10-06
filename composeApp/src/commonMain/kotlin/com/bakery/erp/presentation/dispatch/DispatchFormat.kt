package com.bakery.erp.presentation.dispatch

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** "06/10/2026" en la zona del aparato. */
internal fun formatDay(millis: Long): String {
    if (millis == 0L) return "—"
    val tz = TimeZone.currentSystemDefault()
    val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date
    val mm = d.monthNumber.toString().padStart(2, '0')
    val dd = d.dayOfMonth.toString().padStart(2, '0')
    return "$dd/$mm/${d.year}"
}
