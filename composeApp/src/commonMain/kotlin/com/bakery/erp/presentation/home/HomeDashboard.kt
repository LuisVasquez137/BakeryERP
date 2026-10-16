package com.bakery.erp.presentation.home

import com.bakery.erp.domain.report.SalesSummary
import com.bakery.erp.model.CustomerOrder
import com.bakery.erp.model.CustomerOrderStatus
import com.bakery.erp.model.CustomerOrderType
import com.bakery.erp.model.DispatchNote
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.SalesChannel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Gráficas del inicio. Hay dos tableros y no uno configurable: lo que se ve depende de
 * cuántas sucursales puede ver la persona.
 *
 * - **Owner**: quien ve varias sucursales (o todas). Panorama del negocio, con montos solo
 *   si tiene `VIEW_SALES_VALUE`.
 * - **Branch**: quien ve una sola sucursal. Solo lo suyo, en porcentajes y unidades: el
 *   personal se compara contra sí mismo sin ver cuánto dinero entra.
 *
 * Así una sucursal nueva tiene sus gráficas sin que nadie las configure.
 */
sealed interface HomeDashboard {
    data class Owner(
        val showMoney: Boolean,
        val branchCount: Int,
        val selected: DashboardPeriod,
        val periods: Map<DashboardPeriod, OwnerPeriodData>
    ) : HomeDashboard

    data class Branch(val data: BranchToday) : HomeDashboard
}

enum class DashboardPeriod(val label: String) { TODAY("Hoy"), WEEK("Semana"), MONTH("Mes") }

data class ChartPoint(val label: String, val value: Double)

/** Una rebanada o una barra. [deltaPct] = contra el período anterior, si se puede comparar. */
data class Slice(val label: String, val value: Double, val deltaPct: Double? = null)

data class OwnerPeriodData(
    val period: DashboardPeriod,
    val total: Double,
    /**
     * Lo del período anterior **hasta la misma hora**: hoy a las 10 de la mañana no se
     * compara contra el día entero de la semana pasada, o el inicio diría todas las
     * mañanas que se vende menos.
     */
    val previousComparable: Double,
    val current: List<ChartPoint>,
    val previous: List<ChartPoint>,
    val branches: List<Slice>,
    val payments: List<Slice>,
    val channels: List<Slice>
) {
    val deltaPct: Double?
        get() = if (previousComparable > 0.0) (total - previousComparable) / previousComparable * 100.0 else null
}

data class BranchToday(
    val branchName: String,
    /** "jueves": el día con el que se compara. */
    val weekdayName: String,
    /** Hoy hasta ahora ÷ el mismo día de la semana pasada hasta la misma hora. */
    val paceRatio: Double?,
    /** Parte de un día normal que todavía falta vender a partir de ahora (0..1). */
    val remainingNormalShare: Double?,
    val hours: List<Int>,
    /** Por hora, fracción de un día normal (promedio de los últimos 4 días iguales). */
    val normalShare: List<Double>,
    /** Por hora, lo de hoy en la misma escala; solo hasta la hora actual. */
    val todayShare: List<Double>,
    val peakLabel: String?,
    val topProducts: List<Slice>,
    val plantOrder: PlantOrderToday?,
    val customerOrders: CustomerOrdersSoon
)

enum class PlantOrderStage { PACKING, ON_THE_WAY, RECEIVED }

data class PlantOrderToday(
    val requested: Double,
    /** Lo recibido si ya se contó; si no, lo empacado. */
    val arrived: Double,
    val stage: PlantOrderStage,
    val shortages: List<Shortage>
)

data class Shortage(val productName: String, val expected: Double, val got: Double)

data class CustomerOrdersSoon(val today: Int, val tomorrow: Int, val next: NextCustomerOrder?)

data class NextCustomerOrder(val time: String, val title: String, val customer: String)

// ── Cálculos (puros) ─────────────────────────────────────────────────────────

/** Días del período: hoy cuenta como uno. */
fun DashboardPeriod.days(): Int = when (this) {
    DashboardPeriod.TODAY -> 1
    DashboardPeriod.WEEK -> 7
    DashboardPeriod.MONTH -> 30
}

/**
 * Cuántos días atrás está el período con el que se compara. Hoy se compara contra el
 * mismo día de la semana pasada (un sábado contra un viernes no dice nada).
 */
fun DashboardPeriod.compareShiftDays(): Int = if (this == DashboardPeriod.TODAY) 7 else days()

/**
 * Lo vendido en un día hasta la hora [hour] y [minute]: las horas completas más la parte
 * transcurrida de la hora en curso, suponiendo que esa hora vende parejo.
 */
fun salesUpTo(hourHistogram: Map<Int, Double>, hour: Int, minute: Int): Double =
    hourHistogram.entries.sumOf { (h, v) ->
        when {
            h < hour -> v
            h == hour -> v * minute / 60.0
            else -> 0.0
        }
    }

/** Rango de horas a dibujar: al menos de 6 a 20, más las horas que tengan ventas. */
fun hourAxis(vararg histograms: Map<Int, Double>): List<Int> {
    val withSales = histograms.flatMap { h -> h.filterValues { it > 0.0 }.keys }
    val from = minOf(6, withSales.minOrNull() ?: 6)
    val to = maxOf(20, withSales.maxOrNull() ?: 20)
    return (from..to).toList()
}

fun hourLabel(h: Int): String = when {
    h == 0 -> "12 a. m."
    h < 12 -> "$h a. m."
    h == 12 -> "12 m."
    else -> "${h - 12} p. m."
}

private fun shortHour(h: Int): String = when {
    h == 0 -> "12"
    h <= 12 -> "$h"
    else -> "${h - 12}"
}

fun weekdayShort(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "Lun"
    DayOfWeek.TUESDAY -> "Mar"
    DayOfWeek.WEDNESDAY -> "Mié"
    DayOfWeek.THURSDAY -> "Jue"
    DayOfWeek.FRIDAY -> "Vie"
    DayOfWeek.SATURDAY -> "Sáb"
    else -> "Dom"
}

fun weekdayName(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "lunes"
    DayOfWeek.TUESDAY -> "martes"
    DayOfWeek.WEDNESDAY -> "miércoles"
    DayOfWeek.THURSDAY -> "jueves"
    DayOfWeek.FRIDAY -> "viernes"
    DayOfWeek.SATURDAY -> "sábado"
    else -> "domingo"
}

private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/**
 * Puntos por día del rango `[from, from + n)`. Los días sin ventas valen 0: si se saltaran,
 * la línea uniría el viernes con el domingo como si el sábado no existiera.
 */
private fun dailyPoints(summary: SalesSummary, from: LocalDate, n: Int, today: LocalDate): List<ChartPoint> {
    val byDay = summary.byDay.associateBy { it.dateLabel }
    return (0 until n).map { i ->
        val date = from.plus(DatePeriod(days = i))
        val label = when {
            date == today -> "Hoy"
            n <= 7 -> weekdayShort(date.dayOfWeek)
            else -> "${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]}"
        }
        ChartPoint(label, byDay[date.toString()]?.revenue ?: 0.0)
    }
}

/** Las [max] más grandes y el resto junto en "Otras", para que la dona se pueda leer. */
private fun topWithOthers(slices: List<Slice>, max: Int, othersLabel: String): List<Slice> {
    val sorted = slices.filter { it.value > 0.0 }.sortedByDescending { it.value }
    if (sorted.size <= max) return sorted
    val rest = sorted.drop(max - 1)
    return sorted.take(max - 1) + Slice(othersLabel, rest.sumOf { it.value })
}

fun paymentLabel(method: String): String = when (method) {
    SalePaymentMethod.CASH -> "Efectivo"
    SalePaymentMethod.CARD -> "Tarjeta"
    SalePaymentMethod.TRANSFER -> "Transferencia"
    SalePaymentMethod.PEDIDOS_YA -> "PedidosYa"
    SalePaymentMethod.CREDIT -> "Crédito"
    SalePaymentMethod.CUSTOMER_DEPOSIT -> "Anticipo"
    SalePaymentMethod.MIXED -> "Mixto"
    else -> "Otro"
}

/**
 * Arma un período del tablero del dueño.
 *
 * @param previousLastDay resumen del último día del período anterior (el que corresponde a
 *   "hoy"), para recortarlo a la hora actual. Para [DashboardPeriod.TODAY] es el mismo
 *   [previous].
 */
fun buildOwnerPeriod(
    period: DashboardPeriod,
    today: LocalDate,
    hour: Int,
    minute: Int,
    current: SalesSummary,
    previous: SalesSummary,
    previousLastDay: SalesSummary
): OwnerPeriodData {
    val n = period.days()
    val shift = period.compareShiftDays()
    val from = today.minus(DatePeriod(days = n - 1))

    // El último día del período anterior va recortado a esta misma hora.
    val previousComparable = previous.totalRevenue - previousLastDay.totalRevenue +
        salesUpTo(previousLastDay.hourHistogram, hour, minute)

    val (cur, prev) = if (period == DashboardPeriod.TODAY) {
        val hours = hourAxis(current.hourHistogram, previous.hourHistogram)
        hours.filter { it <= hour }.map { ChartPoint(shortHour(it), current.hourHistogram[it] ?: 0.0) } to
            hours.map { ChartPoint(shortHour(it), previous.hourHistogram[it] ?: 0.0) }
    } else {
        dailyPoints(current, from, n, today) to
            dailyPoints(previous, from.minus(DatePeriod(days = shift)), n, today.minus(DatePeriod(days = shift)))
    }

    // El cambio por sucursal usa el mismo recorte que el total, repartido en proporción:
    // los resúmenes no traen horas por sucursal, y sin recorte cada mañana todas saldrían
    // a la baja.
    val cutFactor = if (previous.totalRevenue > 0.0) previousComparable / previous.totalRevenue else 1.0
    val prevByBranch = previous.byBranch.associateBy { it.branchId }
    val branches = current.byBranch.map { b ->
        val before = (prevByBranch[b.branchId]?.total ?: 0.0) * cutFactor
        Slice(
            label = b.branchName.ifBlank { "Sin nombre" },
            value = b.total,
            deltaPct = if (before > 0.0) (b.total - before) / before * 100.0 else null
        )
    }

    val payments = current.byPaymentMethod.map { (m, v) -> Slice(paymentLabel(m), v.total) }
        .groupBy { it.label }.map { (label, s) -> Slice(label, s.sumOf { it.value }) }
    val channels = current.byChannel.map { (c, v) -> Slice(SalesChannel.label(c), v.total) }
        .filter { it.value > 0.0 }.sortedByDescending { it.value }

    return OwnerPeriodData(
        period = period,
        total = current.totalRevenue,
        previousComparable = previousComparable,
        current = cur,
        previous = prev,
        branches = topWithOthers(branches, 5, "Otras"),
        payments = topWithOthers(payments, 4, "Otros"),
        channels = channels
    )
}

/**
 * Tablero de una sucursal.
 *
 * @param sameWeekdays los últimos días iguales a hoy (hace 7, 14, 21, 28 días), el primero
 *   el de la semana pasada. Con ellos se arma "un jueves normal".
 */
fun buildBranchToday(
    branchName: String,
    today: LocalDate,
    hour: Int,
    minute: Int,
    todaySales: SalesSummary,
    sameWeekdays: List<SalesSummary>,
    notes: List<DispatchNote>,
    customerOrders: List<CustomerOrder>,
    todayStartMillis: Long,
    tomorrowStartMillis: Long
): BranchToday {
    val lastWeek = sameWeekdays.firstOrNull()
    val lastWeekSoFar = lastWeek?.let { salesUpTo(it.hourHistogram, hour, minute) } ?: 0.0
    val todaySoFar = todaySales.totalRevenue

    // Un día normal = promedio de los días iguales que sí vendieron (un feriado cerrado
    // no debe bajar el promedio).
    val usable = sameWeekdays.filter { it.totalRevenue > 0.0 }
    val hours = hourAxis(todaySales.hourHistogram, *usable.map { it.hourHistogram }.toTypedArray())
    val normalTotal = if (usable.isEmpty()) 0.0 else usable.sumOf { it.totalRevenue } / usable.size
    val normalByHour = hours.map { h ->
        if (usable.isEmpty()) 0.0 else usable.sumOf { it.hourHistogram[h] ?: 0.0 } / usable.size
    }
    val normalShare = normalByHour.map { if (normalTotal > 0.0) it / normalTotal else 0.0 }
    val todayShare = hours.filter { it <= hour }.map { h ->
        if (normalTotal > 0.0) (todaySales.hourHistogram[h] ?: 0.0) / normalTotal else 0.0
    }
    val remaining = if (normalTotal > 0.0) {
        hours.indices.sumOf { i ->
            val h = hours[i]
            when {
                h > hour -> normalShare[i]
                h == hour -> normalShare[i] * (60 - minute) / 60.0
                else -> 0.0
            }
        }
    } else null

    // Pico: las dos horas seguidas que más venden en un día normal.
    val peakLabel = if (normalTotal > 0.0 && normalShare.size >= 2) {
        val best = (0 until normalShare.size - 1).maxByOrNull { normalShare[it] + normalShare[it + 1] }!!
        val from = hours[best]
        val to = hours[best] + 2
        "${shortHour(from)}–${hourLabel(to % 24)}"
    } else null

    val top = todaySales.topProducts
        .filter { it.quantity > 0.0 }
        .sortedByDescending { it.quantity }
        .take(5)
        .map { Slice(it.productName.ifBlank { "Producto" }, it.quantity) }

    return BranchToday(
        branchName = branchName,
        weekdayName = weekdayName(today.dayOfWeek),
        paceRatio = if (lastWeekSoFar > 0.0) todaySoFar / lastWeekSoFar else null,
        remainingNormalShare = remaining,
        hours = hours,
        normalShare = normalShare,
        todayShare = todayShare,
        peakLabel = peakLabel,
        topProducts = top,
        plantOrder = plantOrderOf(notes),
        customerOrders = customerOrdersSoon(customerOrders, todayStartMillis, tomorrowStartMillis)
    )
}

/** El pedido a planta de hoy, sumando todas las notas (una por turno). */
fun plantOrderOf(notes: List<DispatchNote>): PlantOrderToday? {
    val live = notes.filter { it.status != DispatchStatus.CANCELLED }
    if (live.isEmpty()) return null
    val received = live.all { it.status == DispatchStatus.DELIVERED || it.status == DispatchStatus.DELIVERED_WITH_DISCREPANCY }
    val onTheWay = live.any {
        it.status == DispatchStatus.IN_TRANSIT || it.status == DispatchStatus.ARRIVED ||
            it.status == DispatchStatus.DELIVERED || it.status == DispatchStatus.DELIVERED_WITH_DISCREPANCY
    }
    val stage = when {
        received -> PlantOrderStage.RECEIVED
        onTheWay -> PlantOrderStage.ON_THE_WAY
        else -> PlantOrderStage.PACKING
    }
    val lines = live.flatMap { n ->
        val counted = n.status == DispatchStatus.DELIVERED || n.status == DispatchStatus.DELIVERED_WITH_DISCREPANCY
        n.lines.map { l -> Triple(l.productName, l.requestedQuantity, if (counted) l.receivedQuantity else l.packedQuantity) }
    }
    val requested = lines.sumOf { it.second }.takeIf { it > 0.0 } ?: live.sumOf { it.plannedUnits }
    val arrived = if (lines.isNotEmpty()) lines.sumOf { it.third } else live.sumOf { it.packedUnits }
    // Faltantes solo cuando ya hay algo que comparar: mientras se empaca, todo "falta".
    val shortages = if (stage == PlantOrderStage.PACKING) emptyList() else lines
        .groupBy { it.first }
        .map { (name, ls) -> Shortage(name, ls.sumOf { it.second }, ls.sumOf { it.third }) }
        .filter { it.got + 0.001 < it.expected }
        .sortedByDescending { it.expected - it.got }
    return PlantOrderToday(requested, arrived, stage, shortages)
}

/** Pedidos de clientes que esta sucursal entrega hoy y mañana. */
fun customerOrdersSoon(orders: List<CustomerOrder>, todayStart: Long, tomorrowStart: Long): CustomerOrdersSoon {
    val open = orders.filter {
        it.status != CustomerOrderStatus.DELIVERED &&
            it.status != CustomerOrderStatus.CANCELLED &&
            it.status != CustomerOrderStatus.DRAFT
    }
    val today = open.filter { it.deliveryDate in todayStart until tomorrowStart }
    val tomorrow = open.filter { it.deliveryDate >= tomorrowStart }
    val next = today.sortedBy { it.deliveryTime.ifBlank { "99" } }.firstOrNull()
        ?: tomorrow.sortedBy { it.deliveryTime.ifBlank { "99" } }.firstOrNull()
    return CustomerOrdersSoon(
        today = today.size,
        tomorrow = tomorrow.size,
        next = next?.let { o ->
            NextCustomerOrder(
                time = when {
                    o.deliveryDate >= tomorrowStart -> "Mañana"
                    o.deliveryTime.isNotBlank() -> o.deliveryTime
                    else -> "Hoy"
                },
                title = when (o.type) {
                    CustomerOrderType.CAKE -> "Pastel"
                    CustomerOrderType.RESELLER -> "Pedido de revendedor"
                    else -> "Pedido especial"
                },
                customer = o.recipientName.ifBlank { o.customerName }
            )
        }
    )
}
