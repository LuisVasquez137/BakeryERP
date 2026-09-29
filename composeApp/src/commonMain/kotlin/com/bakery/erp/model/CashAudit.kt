package com.bakery.erp.model

/**
 * Bitácora de cajas: qué pasó con cada gaveta, cuándo y quién. Existe para control: un cierre
 * (o solo entrar a la pantalla de cierre, que muestra cuánto debería haber) fuera del horario de
 * cuadre de esa caja significa que alguien está contando dinero sin autorización.
 *
 * `companies/{c}/cashAuditEvents/{id}`, plana, filtrada por sucursal. Se escribe y no se edita:
 * es un registro, no un estado.
 */
data class CashAuditEvent(
    val id: String = "",
    val type: String = CashAuditEventType.CLOSE_SCREEN,
    val at: Long = 0L,
    val branchId: String = "",
    val branchName: String = "",
    val registerId: String = "",
    val registerName: String = "",
    val sessionId: String = "",
    /** Usuario de la app. En la tablet de la caja suele ser el mismo para todas. */
    val userId: String = "",
    val userName: String = "",
    /** Colaborador que opera: el que abrió o, al cerrar, el que recibe la gaveta. */
    val employeeName: String = "",
    /** Fuera del horario de cuadre de esa caja. */
    val outOfSchedule: Boolean = false,
    /** "2:00 PM, 9:00 PM (±30 min)": contra qué se comparó, para leerlo sin abrir la caja. */
    val scheduleLabel: String = "",
    val expectedCash: Double = 0.0,
    val countedCash: Double = 0.0,
    val difference: Double = 0.0,
    val notes: String = ""
)

object CashAuditEventType {
    const val OPEN = "OPEN"
    /** Entró a la pantalla de cierre: ya vio cuánto debería haber, cierre o no. */
    const val CLOSE_SCREEN = "CLOSE_SCREEN"
    const val CLOSE = "CLOSE"

    fun label(type: String) = when (type) {
        OPEN -> "Abrió caja"
        CLOSE_SCREEN -> "Entró al cierre"
        CLOSE -> "Cerró caja"
        else -> type
    }
}

/** Tolerancia por omisión alrededor de cada horario de cuadre. */
const val DEFAULT_CLOSE_TOLERANCE_MINUTES = 30

/**
 * ¿[minuteOfDay] cae dentro de algún horario de cuadre (±[toleranceMinutes])? Sin horarios
 * configurados la caja no tiene restricción: todo cuenta como dentro, en vez de marcar en rojo
 * cada cierre de una caja que nadie configuró.
 *
 * [schedules] en "HH:mm". La ventana puede cruzar la medianoche (un cuadre a las 23:50 con 30
 * minutos de tolerancia alcanza hasta las 00:20).
 */
fun isWithinCloseSchedule(minuteOfDay: Int, schedules: List<String>, toleranceMinutes: Int): Boolean {
    val times = schedules.mapNotNull { parseScheduleMinutes(it) }
    if (times.isEmpty()) return true
    return times.any { t ->
        val diff = kotlin.math.abs(minuteOfDay - t)
        minOf(diff, 24 * 60 - diff) <= toleranceMinutes
    }
}

/** "14:00" → 840. Null si no es una hora válida. */
fun parseScheduleMinutes(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val m = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return h * 60 + m
}

/** "2:00 PM, 9:00 PM (±30 min)". Vacío si la caja no tiene horarios. */
fun closeScheduleLabel(schedules: List<String>, toleranceMinutes: Int): String {
    val times = schedules.mapNotNull { parseScheduleMinutes(it) }.sorted()
    if (times.isEmpty()) return ""
    return times.joinToString(", ") { t ->
        val h = t / 60; val m = t % 60
        val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        "$h12:${m.toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"
    } + " (±$toleranceMinutes min)"
}
