package com.bakery.erp.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Rango de fechas de la lista de compras.
 *
 * La lista NO carga todo el histórico: hoy son pocas facturas, pero la consulta crece para
 * siempre y nadie abre "Compras" para ver lo de hace dos años. El default es el mes en curso
 * —igual que las listas de pedidos y despacho— y [ALL] queda disponible para la pregunta que
 * sí necesita el histórico completo ("¿cuándo fue la última vez que compré aceite?").
 */
enum class PurchasePeriod(val label: String) {
    THIS_MONTH("Este mes"),
    LAST_MONTH("Mes pasado"),
    LAST_3_MONTHS("Últimos 3 meses"),
    THIS_YEAR("Este año"),
    CUSTOM("Personalizado"),
    ALL("Todo")
}

/** Rango semiabierto en epoch-millis: `from <= date < until`. */
data class DateRangeMillis(val from: Long, val until: Long) {
    operator fun contains(millis: Long) = millis >= from && millis < until

    companion object {
        val UNBOUNDED = DateRangeMillis(0L, Long.MAX_VALUE)
    }
}

private fun LocalDate.startMillis(tz: TimeZone) = atStartOfDayIn(tz).toEpochMilliseconds()

/** Primer día del mes que contiene a [date], desplazado [monthsBack] meses hacia atrás. */
private fun monthStart(date: LocalDate, monthsBack: Int): LocalDate {
    var year = date.year
    var month = date.monthNumber - monthsBack
    while (month <= 0) {
        month += 12
        year -= 1
    }
    return LocalDate(year, month, 1)
}

/** Día siguiente a [date], sin depender de `DatePeriod`. */
private fun nextDay(date: LocalDate): LocalDate {
    val daysInMonth = daysInMonth(date.year, date.monthNumber)
    return when {
        date.dayOfMonth < daysInMonth -> LocalDate(date.year, date.monthNumber, date.dayOfMonth + 1)
        date.monthNumber < 12 -> LocalDate(date.year, date.monthNumber + 1, 1)
        else -> LocalDate(date.year + 1, 1, 1)
    }
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    else -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
}

/**
 * Resuelve el preset a un rango concreto. [customFrom]/[customUntil] solo se usan con
 * [PurchasePeriod.CUSTOM] y son inclusivos por día: el `until` se lleva al inicio del día
 * siguiente para que una compra registrada a las 6 pm del último día sí entre.
 */
fun purchaseDateRange(
    period: PurchasePeriod,
    nowMillis: Long,
    tz: TimeZone = TimeZone.currentSystemDefault(),
    customFrom: Long? = null,
    customUntil: Long? = null
): DateRangeMillis {
    val today: LocalDate = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz).date
    val tomorrowStart = nextDay(today).startMillis(tz)
    return when (period) {
        PurchasePeriod.THIS_MONTH ->
            DateRangeMillis(monthStart(today, 0).startMillis(tz), tomorrowStart)
        PurchasePeriod.LAST_MONTH ->
            DateRangeMillis(monthStart(today, 1).startMillis(tz), monthStart(today, 0).startMillis(tz))
        PurchasePeriod.LAST_3_MONTHS ->
            DateRangeMillis(monthStart(today, 2).startMillis(tz), tomorrowStart)
        PurchasePeriod.THIS_YEAR ->
            DateRangeMillis(LocalDate(today.year, 1, 1).startMillis(tz), tomorrowStart)
        PurchasePeriod.ALL -> DateRangeMillis.UNBOUNDED
        PurchasePeriod.CUSTOM -> {
            val fromDate = customFrom?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(tz).date }
                ?: monthStart(today, 0)
            val untilDate = customUntil?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(tz).date }
                ?: today
            DateRangeMillis(fromDate.startMillis(tz), nextDay(untilDate).startMillis(tz))
        }
    }
}

/**
 * Filtros de la lista de compras. Todo se aplica en memoria sobre el rango ya cargado:
 * Firestore no combina una desigualdad de fecha con varios `where` de igualdad sin un índice
 * compuesto por cada combinación, y un mes de facturas cabe de sobra en memoria.
 */
data class PurchaseFilters(
    val query: String = "",
    val period: PurchasePeriod = PurchasePeriod.THIS_MONTH,
    val customFrom: Long? = null,
    val customUntil: Long? = null,
    /** Vacío = todos los estados. */
    val statuses: Set<PurchaseDisplayStatus> = emptySet(),
    val branchIds: Set<String> = emptySet(),
    val supplierIds: Set<String> = emptySet(),
    val sort: PurchaseSort = PurchaseSort.DATE_DESC
) {
    /** Cuántos criterios distintos del de fecha están puestos — para el badge del botón. */
    val activeCount: Int
        get() = (if (statuses.isNotEmpty()) 1 else 0) +
            (if (branchIds.isNotEmpty()) 1 else 0) +
            (if (supplierIds.isNotEmpty()) 1 else 0) +
            (if (sort != PurchaseSort.DATE_DESC) 1 else 0)

    val hasAny: Boolean
        get() = query.isNotBlank() || activeCount > 0 || period != PurchasePeriod.THIS_MONTH
}

enum class PurchaseSort(val label: String) {
    DATE_DESC("Más reciente"),
    DATE_ASC("Más antigua"),
    AMOUNT_DESC("Mayor monto"),
    AMOUNT_ASC("Menor monto"),
    SUPPLIER("Proveedor")
}

/**
 * Aplica búsqueda + filtros + orden. La búsqueda alcanza el CONTENIDO de la factura
 * ([Purchase.itemNames], denormalizado al guardar): escribir "Aceite" trae todas las
 * facturas que lo llevan dentro aunque la lista nunca muestre el detalle.
 */
fun List<Purchase>.applyPurchaseFilters(
    filters: PurchaseFilters,
    nowMillis: Long,
    tz: TimeZone = TimeZone.currentSystemDefault()
): List<Purchase> {
    val q = filters.query.trim()
    val range = purchaseDateRange(filters.period, nowMillis, tz, filters.customFrom, filters.customUntil)
    val filtered = filter { p ->
        p.date in range &&
            (filters.branchIds.isEmpty() || p.branchId in filters.branchIds) &&
            (filters.supplierIds.isEmpty() || p.supplierId in filters.supplierIds) &&
            (filters.statuses.isEmpty() || p.displayStatus(nowMillis) in filters.statuses) &&
            (q.isEmpty() || p.matchesQuery(q))
    }
    return when (filters.sort) {
        PurchaseSort.DATE_DESC -> filtered.sortedByDescending { it.date }
        PurchaseSort.DATE_ASC -> filtered.sortedBy { it.date }
        PurchaseSort.AMOUNT_DESC -> filtered.sortedByDescending { it.totalAmount }
        PurchaseSort.AMOUNT_ASC -> filtered.sortedBy { it.totalAmount }
        PurchaseSort.SUPPLIER -> filtered.sortedWith(
            compareBy<Purchase> { it.supplierName.lowercase() }.thenByDescending { it.date }
        )
    }
}

private fun Purchase.matchesQuery(q: String): Boolean =
    supplierName.contains(q, ignoreCase = true) ||
        branchName.contains(q, ignoreCase = true) ||
        (invoiceNumber?.contains(q, ignoreCase = true) == true) ||
        (notes?.contains(q, ignoreCase = true) == true) ||
        itemNames.any { it.contains(q, ignoreCase = true) }

/** Totales del período mostrado, para la fila de KPIs. */
data class PurchaseKpis(
    val invoiceCount: Int = 0,
    val total: Double = 0.0,
    val paid: Double = 0.0,
    val outstanding: Double = 0.0,
    val overdue: Double = 0.0,
    val overdueCount: Int = 0,
    val pendingReceiptCount: Int = 0
)

fun List<Purchase>.purchaseKpis(nowMillis: Long): PurchaseKpis {
    var total = 0.0
    var paid = 0.0
    var outstanding = 0.0
    var overdue = 0.0
    var overdueCount = 0
    var pendingReceipt = 0
    forEach { p ->
        total += p.totalAmount
        paid += p.paidAmount
        when (p.displayStatus(nowMillis)) {
            PurchaseDisplayStatus.PENDING_RECEIPT -> pendingReceipt++
            PurchaseDisplayStatus.OVERDUE -> {
                overdue += p.balanceDue
                overdueCount++
                outstanding += p.balanceDue
            }
            PurchaseDisplayStatus.PARTIALLY_PAID,
            PurchaseDisplayStatus.PENDING_PAYMENT -> outstanding += p.balanceDue
            PurchaseDisplayStatus.PAID -> Unit
        }
    }
    return PurchaseKpis(size, total, paid, outstanding, overdue, overdueCount, pendingReceipt)
}
