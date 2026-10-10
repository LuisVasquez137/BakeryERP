package com.bakery.erp.model

/** Qué puede marcar hoy una persona en el kiosko. */
sealed interface EntryAvailability {
    /** Le toca marcar la entrada de [shiftNumber], cuya hora de entrada es [startTime]. */
    data class Available(val shiftNumber: Int, val startTime: String) : EntryAvailability

    /** Ya marcó la entrada de hoy (o las dos, si es turno mixto). */
    data class Done(val entries: List<AttendanceRecord>) : EntryAvailability

    /**
     * Turno mixto con la 1ª entrada ya marcada: la 2ª se habilita desde [opensAt]. Evita que un
     * doble toque al llegar en la mañana registre de una vez la entrada de la tarde.
     */
    data class SecondOpensLater(val first: AttendanceRecord, val opensAt: String) : EntryAvailability
}

val Employee.hasSplitShift: Boolean get() = splitShift && secondStartTime.isNotBlank()

/** "07:00 – 11:00 · 15:00 – 19:00", o "" sin horario. */
fun Employee.scheduleLabel(): String {
    val first = if (startTime.isNotBlank() && endTime.isNotBlank()) "$startTime – $endTime" else ""
    if (!hasSplitShift) return first
    val second = if (secondEndTime.isNotBlank()) "$secondStartTime – $secondEndTime" else secondStartTime
    return listOf(first, second).filter { it.isNotBlank() }.joinToString(" · ")
}

/**
 * Desde qué hora cuenta como entrada del 2º turno: al terminar el 1º; sin hora de salida del
 * 1º, una hora antes de que empiece el 2º.
 */
private fun Employee.secondShiftOpensAtMinutes(): Int? =
    hhmmToMinutes(endTime) ?: hhmmToMinutes(secondStartTime)?.let { maxOf(0, it - 60) }

/**
 * Regla del kiosko. Las marcas viejas no traen turno y cuentan como del 1º.
 *
 * Turno mixto sin ninguna marca hoy: si ya pasó la apertura del 2º, la persona viene al 2º
 * (faltó o no marcó en la mañana) y su tardanza se mide contra el 2º, no contra el 1º.
 */
fun Employee.nextEntry(todayRecords: List<AttendanceRecord>, nowMinutes: Int): EntryAvailability {
    val entries = todayRecords.filter { it.clockIn > 0 }.sortedBy { it.clockIn }
    if (!hasSplitShift) {
        return if (entries.isEmpty()) EntryAvailability.Available(1, startTime)
        else EntryAvailability.Done(entries)
    }
    val marked = entries.map { it.shiftNumber }.toSet()
    val opensAt = secondShiftOpensAtMinutes()
    val secondOpen = opensAt == null || nowMinutes >= opensAt
    return when {
        2 in marked -> EntryAvailability.Done(entries)
        1 in marked && secondOpen -> EntryAvailability.Available(2, secondStartTime)
        1 in marked -> EntryAvailability.SecondOpensLater(
            first = entries.first { it.shiftNumber == 1 },
            opensAt = minutesToHHmm(opensAt!!)
        )
        secondOpen -> EntryAvailability.Available(2, secondStartTime)
        else -> EntryAvailability.Available(1, startTime)
    }
}

internal fun hhmmToMinutes(value: String): Int? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val m = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return h * 60 + m
}

private fun minutesToHHmm(total: Int): String =
    "${(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
