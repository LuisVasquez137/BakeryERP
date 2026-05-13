package com.bakery.erp.model

enum class ExpenseCategory {
    ALQUILER, PLANILLA, SERVICIOS, SEGURIDAD,
    DEPRECIACION, MANTENIMIENTO, TRANSPORTE, OTROS;

    val label: String get() = when (this) {
        ALQUILER -> "Alquiler / Local"
        PLANILLA -> "Planilla / Salarios"
        SERVICIOS -> "Servicios (luz, agua, internet)"
        SEGURIDAD -> "Seguridad"
        DEPRECIACION -> "Depreciación de equipo"
        MANTENIMIENTO -> "Mantenimiento y reparaciones"
        TRANSPORTE -> "Transporte / Distribución"
        OTROS -> "Otros"
    }
}

data class Expense(
    val id: String = "",
    val orgId: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val name: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTROS,
    val amount: Double = 0.0,
    val period: String = "",         // "YYYY-MM"
    val notes: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

private val MONTH_NAMES = listOf(
    "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

fun formatPeriodLabel(period: String): String {
    if (period.length != 7) return period
    val year = period.substring(0, 4).toIntOrNull() ?: return period
    val month = period.substring(5, 7).toIntOrNull() ?: return period
    return "${MONTH_NAMES.getOrElse(month) { "?" }} $year"
}

fun nextPeriod(current: String): String {
    if (current.length != 7) return current
    val year = current.substring(0, 4).toIntOrNull() ?: return current
    val month = current.substring(5, 7).toIntOrNull() ?: return current
    return if (month == 12) "${year + 1}-01"
    else "$year-${(month + 1).toString().padStart(2, '0')}"
}

fun previousPeriod(current: String): String {
    if (current.length != 7) return current
    val year = current.substring(0, 4).toIntOrNull() ?: return current
    val month = current.substring(5, 7).toIntOrNull() ?: return current
    return if (month == 1) "${year - 1}-12"
    else "$year-${(month - 1).toString().padStart(2, '0')}"
}
