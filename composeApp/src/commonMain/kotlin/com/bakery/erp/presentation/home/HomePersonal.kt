package com.bakery.erp.presentation.home

import com.bakery.erp.model.AttendanceRecord
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.CustomerOrder
import com.bakery.erp.model.CustomerOrderStatus
import com.bakery.erp.model.DispatchNote
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.model.Employee
import com.bakery.erp.model.EmployeeStatus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Lo del Home que es de la PERSONA y no del negocio: sus pendientes y los cumpleaños.
 *
 * Los pendientes no repiten el banner de alertas: una alerta avisa ("hay 3 cajas sin
 * cerrar"), un pendiente le dice a quien está frente al equipo qué le toca hacer a ella y
 * la lleva ahí con un botón. Por eso cada uno mira lo suyo: su asistencia, su caja, el
 * pedido que llega a su sucursal.
 */
enum class PendingKind { ATTENDANCE, DISPATCH_ARRIVED, DISPATCH_ON_THE_WAY, OPEN_CASH, CUSTOMER_ORDERS }

data class PendingTask(
    val id: String,
    val kind: PendingKind,
    val title: String,
    val detail: String,
    val actionLabel: String? = null,
    /** Título del módulo del lanzador al que lleva el botón; HomeScreen revisa el permiso. */
    val module: String? = null
)

data class BirthdayPerson(
    val employeeId: String,
    val name: String,
    val photoUrl: String,
    val branchName: String,
    val date: LocalDate,
    /** Días desde hoy: negativo = ya pasó, 0 = hoy. */
    val daysFromToday: Int
)

data class Birthdays(
    /** Los de esta semana (lunes a domingo), en orden. */
    val thisWeek: List<BirthdayPerson>,
    /** Todos los del mes en curso, por día. */
    val thisMonth: List<BirthdayPerson>,
    val monthName: String,
    /** Todos los del año en curso (fechas de este año), por fecha: la pantalla de Cumpleaños. */
    val allYear: List<BirthdayPerson> = emptyList(),
    val year: Int = 0
)

private val MONTH_NAMES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
)

fun monthName(month: Int): String = MONTH_NAMES[month - 1]

/** "Hoy", "Mañana", "Ayer" o "jueves 17". */
fun birthdayWhen(p: BirthdayPerson): String = when (p.daysFromToday) {
    0 -> "Hoy"
    1 -> "Mañana"
    -1 -> "Ayer"
    else -> "${weekdayName(p.date.dayOfWeek)} ${p.date.dayOfMonth}"
}

/**
 * Cumpleaños de la semana y del mes, de las personas activas.
 *
 * La fecha de nacimiento se guarda como medianoche UTC (el selector de fecha), así que se
 * lee en UTC: leída en la hora de Guatemala caería un día antes.
 */
fun buildBirthdays(employees: List<Employee>, today: LocalDate): Birthdays {
    val monday = today.minus(DatePeriod(days = today.dayOfWeek.isoDayNumber - 1))
    val sunday = monday.plus(DatePeriod(days = 6))
    // Por persona: su cumpleaños de este año y, aparte, el que cae en esta semana (la semana
    // puede cruzar de año: 29 dic – 4 ene).
    val rows = employees
        .filter { it.active && it.status == EmployeeStatus.ACTIVE && it.birthDate != 0L }
        .map { e ->
            val born = Instant.fromEpochMilliseconds(e.birthDate).toLocalDateTime(TimeZone.UTC).date
            // 29 de febrero en un año que no es bisiesto: se celebra el 28.
            val day = if (born.monthNumber == 2 && born.dayOfMonth == 29) 28 else born.dayOfMonth
            fun inYear(y: Int) = runCatching { LocalDate(y, born.monthNumber, day) }.getOrNull()
            fun person(date: LocalDate) = BirthdayPerson(
                employeeId = e.id,
                name = e.name,
                photoUrl = e.photoUrl,
                branchName = e.branchName,
                date = date,
                daysFromToday = (date.toEpochDays() - today.toEpochDays()).toInt()
            )
            val thisYear = inYear(today.year)?.let(::person)
            val inWeek = listOfNotNull(inYear(today.year), inYear(today.year - 1), inYear(today.year + 1))
                .firstOrNull { it in monday..sunday }?.let(::person)
            thisYear to inWeek
        }
    val year = rows.mapNotNull { it.first }.sortedWith(compareBy({ it.date }, { it.name }))
    return Birthdays(
        thisWeek = rows.mapNotNull { it.second }.sortedBy { it.date },
        thisMonth = year.filter { it.date.month == today.month },
        monthName = monthName(today.monthNumber),
        allYear = year,
        year = today.year
    )
}

/**
 * Pendientes de la persona. Cada fuente llega ya filtrada a lo suyo; aquí solo se decide
 * qué se dice y a dónde lleva.
 *
 * @param employee su ficha (null = sin ficha: no hay asistencia que revisar).
 * @param todayAttendance sus marcas de hoy; null = no se pudo consultar (no se inventa el aviso).
 * @param staleCash sus cajas abiertas desde antes de hoy.
 * @param notes notas de planta hacia sus sucursales, de ayer y hoy.
 * @param ordersToday pedidos de clientes que entregan hoy sus sucursales.
 */
fun buildPendingTasks(
    employee: Employee?,
    today: LocalDate,
    hour: Int,
    minute: Int,
    todayAttendance: List<AttendanceRecord>?,
    canOpenAttendance: Boolean,
    staleCash: List<CashSession>,
    notes: List<DispatchNote>,
    ordersToday: List<CustomerOrder>
): List<PendingTask> {
    val tasks = mutableListOf<PendingTask>()

    // 1. Asistencia: solo si hoy le toca y ya pasó su hora de entrada.
    if (employee != null && todayAttendance != null && todayAttendance.isEmpty() &&
        today.dayOfWeek.isoDayNumber in employee.workDays
    ) {
        val start = parseHourMinute(employee.startTime)
        if (start != null && hour * 60 + minute >= start.first * 60 + start.second) {
            tasks += PendingTask(
                id = "attendance",
                kind = PendingKind.ATTENDANCE,
                title = "No has marcado tu entrada",
                detail = "Tu turno empezó a las ${employee.startTime}." +
                    if (canOpenAttendance) "" else " Márcala en el kiosko de la sucursal.",
                actionLabel = if (canOpenAttendance) "Marcar" else null,
                module = if (canOpenAttendance) "Asistencia" else null
            )
        }
    }

    // 2. Pedido de planta: lo que ya llegó (hay que contarlo) va antes que lo que viene.
    val arrived = notes.filter { it.status == DispatchStatus.ARRIVED }
    val onTheWay = notes.filter { it.status == DispatchStatus.IN_TRANSIT }
    if (arrived.isNotEmpty()) {
        tasks += PendingTask(
            id = "dispatch-arrived",
            kind = PendingKind.DISPATCH_ARRIVED,
            title = if (arrived.size == 1) "Llegó el pedido de planta" else "Llegaron ${arrived.size} pedidos de planta",
            detail = "Falta contarlo y confirmar lo que llegó.",
            actionLabel = "Recibir",
            module = "Reparto"
        )
    } else if (onTheWay.isNotEmpty()) {
        tasks += PendingTask(
            id = "dispatch-way",
            kind = PendingKind.DISPATCH_ON_THE_WAY,
            title = "El pedido de planta viene en camino",
            detail = onTheWay.first().shiftName.takeIf { it.isNotBlank() }?.let { "Turno $it." } ?: "Ten lista la mesa para contarlo.",
            actionLabel = "Ver",
            module = "Reparto"
        )
    }

    // 3. Su caja abierta desde otro día.
    staleCash.forEach { s ->
        tasks += PendingTask(
            id = "cash-${s.id}",
            kind = PendingKind.OPEN_CASH,
            title = "Tu caja ${s.registerName.ifBlank { "" }} sigue abierta".replace("  ", " "),
            detail = "Se abrió otro día. Ciérrala para que el cuadre de hoy empiece limpio.",
            actionLabel = "Cerrar",
            module = "Punto de venta"
        )
    }

    // 4. Pedidos de clientes que se entregan hoy y no se han entregado.
    val pending = ordersToday.filter {
        it.status != CustomerOrderStatus.DELIVERED && it.status != CustomerOrderStatus.CANCELLED &&
            it.status != CustomerOrderStatus.DRAFT
    }
    if (pending.isNotEmpty()) {
        val next = pending.sortedBy { it.deliveryTime.ifBlank { "99" } }.first()
        tasks += PendingTask(
            id = "orders",
            kind = PendingKind.CUSTOMER_ORDERS,
            title = if (pending.size == 1) "1 pedido de cliente para entregar hoy"
                    else "${pending.size} pedidos de clientes para entregar hoy",
            detail = "El próximo: ${next.recipientName.ifBlank { next.customerName }}" +
                if (next.deliveryTime.isNotBlank()) " a las ${next.deliveryTime}." else ".",
            actionLabel = "Ver",
            module = "Pedidos de clientes"
        )
    }
    return tasks
}

/** "7:00" o "07:00" → (7, 0). */
private fun parseHourMinute(text: String): Pair<Int, Int>? {
    val parts = text.trim().split(":")
    if (parts.size < 2) return null
    val h = parts[0].trim().toIntOrNull() ?: return null
    val m = parts[1].trim().take(2).toIntOrNull() ?: return null
    return h to m
}
