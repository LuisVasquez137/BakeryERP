package com.bakery.erp.model

/**
 * Cuenta de crédito: QUIÉN debe. Una persona, una institución (el colegio) o un colaborador.
 *
 * Reemplaza el cuaderno. La deuda es de la cuenta, no de quien vino a llevar la mercadería:
 * al colegio no siempre llega la misma persona, así que la cuenta tiene [persons] autorizadas
 * y cada cargo anota cuál de ellas se llevó las cosas.
 *
 * [balance] es una CACHÉ: la fuente de verdad son los [CreditMovement] de la cuenta, y cada
 * vez que se escribe uno el saldo se recalcula sumando todos (mismo criterio que `paidAmount`
 * de las compras). Así un doble toque o un movimiento borrado no dejan el saldo corrido.
 */
data class CreditAccount(
    val id: String = "",
    val type: String = CreditAccountType.PERSON,
    val name: String = "",
    /** NIT, para el estado de cuenta de una institución. Opcional. */
    val taxId: String = "",
    val phone: String = "",
    val notes: String = "",
    /** Para [CreditAccountType.EMPLOYEE]: el colaborador. La cuenta sale de Colaboradores. */
    val employeeId: String = "",
    val active: Boolean = true,
    val persons: List<AuthorizedPerson> = emptyList(),
    /** Lo que debe hoy. Positivo = debe. Caché de la suma de movimientos. */
    val balance: Double = 0.0,
    val lastMovementAt: Long = 0L,
    /**
     * Cargos que siguen sin pagar, en arreglos paralelos (fecha, lo que queda de ese cargo).
     * Se recalculan con cada movimiento aplicando los pagos a lo MÁS VIEJO primero
     * ([openCharges]). Viven en la cuenta para que el reporte de antigüedad y el aviso del
     * Inicio no tengan que leer el estado de cuenta de cada cliente.
     */
    val openChargeDates: List<Long> = emptyList(),
    val openChargeAmounts: List<Double> = emptyList(),
    /**
     * Solo colaboradores: si tiene derecho al descuento de colaborador cuando compra y paga
     * en EFECTIVO en ese momento. El % no es de la persona sino de cada producto
     * ([Product.employeeDiscountPercent]); lo fiado nunca lleva descuento.
     */
    val cashDiscountEligible: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val isEmployee: Boolean get() = type == CreditAccountType.EMPLOYEE
    /** Una institución exige elegir quién se lleva la mercadería; una persona es ella misma. */
    val requiresPerson: Boolean get() = type == CreditAccountType.ORGANIZATION
    val activePersons: List<AuthorizedPerson> get() = persons.filter { it.active }
}

/** Alguien autorizado a cargar a la cuenta. Solo el administrador los agrega. */
data class AuthorizedPerson(
    val id: String = "",
    val name: String = "",
    /** DPI u otro documento, para confirmar que es quien dice ser. */
    val idDocument: String = "",
    val phone: String = "",
    val active: Boolean = true
)

object CreditAccountType {
    const val PERSON       = "PERSON"
    const val ORGANIZATION = "ORGANIZATION"
    const val EMPLOYEE     = "EMPLOYEE"

    fun label(type: String) = when (type) {
        PERSON       -> "Persona"
        ORGANIZATION -> "Institución"
        EMPLOYEE     -> "Colaborador"
        else         -> type
    }
}

/**
 * La cuenta de un colaborador tiene id fijo: así existe una sola por colaborador aunque dos
 * pantallas la creen a la vez, y se encuentra sin consulta.
 */
fun employeeCreditAccountId(employeeId: String): String = "emp_$employeeId"

/**
 * Un renglón del estado de cuenta. [amount] lleva signo: positivo aumenta la deuda (una
 * compra, un adelanto), negativo la baja (un pago, el descuento en planilla).
 *
 * Nada se edita ni se borra desde la pantalla: un error se corrige con un [CreditMovementType.ADJUSTMENT],
 * así el estado de cuenta que se le entregó al colegio sigue diciendo lo mismo. La única
 * excepción es el descuento de planilla, que se reescribe con la planilla misma (id fijo).
 */
data class CreditMovement(
    val id: String = "",
    val accountId: String = "",
    val accountName: String = "",
    val type: String = CreditMovementType.SALE,
    val amount: Double = 0.0,
    val description: String = "",
    /** Quién se llevó la mercadería (institución) o a quién se le entregó el adelanto. */
    val personId: String = "",
    val personName: String = "",
    val saleId: String = "",
    val payrollId: String = "",
    /** Sesión de caja donde pasó, si pasó en una caja. Vacío = caja general / administración. */
    val sessionId: String = "",
    val registerName: String = "",
    val branchId: String = "",
    val branchName: String = "",
    /** Forma de pago de un abono (efectivo, tarjeta, transferencia). */
    val method: String = "",
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    /** Colaborador que lo registró, identificado con PIN en un equipo de sucursal. */
    val createdByEmployeeId: String = "",
    val createdAt: Long = 0L
) {
    val isCharge: Boolean get() = amount > 0
}

object CreditMovementType {
    /** Compra a crédito en el POS. */
    const val SALE              = "SALE"
    /** Adelanto de salario: dinero que salió de una caja y se carga al colaborador. */
    const val ADVANCE           = "ADVANCE"
    /** Pago: en una caja, o registrado por administración (transferencia, depósito). */
    const val PAYMENT           = "PAYMENT"
    /** Descontado en la planilla del colaborador. */
    const val PAYROLL_DEDUCTION = "PAYROLL_DEDUCTION"
    /** Corrección o saldo anterior (lo que ya debía en el cuaderno). */
    const val ADJUSTMENT        = "ADJUSTMENT"
    /** Producto que se le cayó o arruinó en el mostrador, a costo. Perdonarlo = ADJUSTMENT negativo. */
    const val ACCIDENT          = "ACCIDENT"

    fun label(type: String) = when (type) {
        SALE              -> "Compra a crédito"
        ADVANCE           -> "Adelanto"
        PAYMENT           -> "Pago"
        PAYROLL_DEDUCTION -> "Descuento en planilla"
        ADJUSTMENT        -> "Ajuste"
        ACCIDENT          -> "Accidente en mostrador"
        else              -> type
    }
}

/** Id fijo del descuento de una planilla: reescribirla actualiza el mismo renglón. */
fun payrollCreditMovementId(payrollId: String): String = "payroll_$payrollId"

/** Saldo a partir de los movimientos: la única forma correcta de calcularlo. */
fun creditBalanceOf(movements: List<CreditMovement>): Double =
    kotlin.math.round(movements.sumOf { it.amount } * 100) / 100.0

/**
 * Estado de cuenta: movimientos del más viejo al más nuevo con el saldo acumulado después
 * de cada uno, que es lo que la institución compara contra sus propios vales.
 */
fun creditStatement(movements: List<CreditMovement>): List<Pair<CreditMovement, Double>> {
    var running = 0.0
    return movements.sortedBy { it.createdAt }.map { m ->
        running += m.amount
        m to kotlin.math.round(running * 100) / 100.0
    }
}

/** Un cargo todavía abierto: cuándo se hizo y cuánto falta pagar de él. */
data class OpenCharge(val at: Long, val remaining: Double)

/**
 * Cargos sin pagar, aplicando cada pago (y cada ajuste que baja la deuda) a los cargos MÁS
 * VIEJOS primero — FIFO, como concilia Odoo. Así "debe desde hace 60 días" significa que la
 * compra de hace 60 días de verdad sigue sin pagarse, no que la cuenta tuvo saldo todo ese
 * tiempo. Un pago de más queda como saldo a favor y se come los cargos que vengan después.
 */
fun openCharges(movements: List<CreditMovement>): List<OpenCharge> {
    val open = ArrayDeque<OpenCharge>()
    var credit = 0.0 // saldo a favor pendiente de aplicar
    movements.sortedBy { it.createdAt }.forEach { m ->
        if (m.amount > 0) {
            var remaining = m.amount
            if (credit > 0) {
                val used = minOf(credit, remaining)
                credit -= used; remaining -= used
            }
            if (remaining > 0.004) open.addLast(OpenCharge(m.createdAt, remaining))
        } else if (m.amount < 0) {
            var pay = -m.amount
            while (pay > 0.004 && open.isNotEmpty()) {
                val first = open.removeFirst()
                if (first.remaining > pay + 0.004) {
                    open.addFirst(first.copy(remaining = first.remaining - pay)); pay = 0.0
                } else pay -= first.remaining
            }
            credit += pay.coerceAtLeast(0.0)
        }
    }
    return open.map { it.copy(remaining = kotlin.math.round(it.remaining * 100) / 100.0) }
}

val CreditAccount.openChargeList: List<OpenCharge>
    get() = openChargeDates.zip(openChargeAmounts).map { OpenCharge(it.first, it.second) }

/** Fecha del cargo más viejo sin pagar. null = no debe nada. */
val CreditAccount.oldestUnpaidAt: Long?
    get() = openChargeDates.minOrNull()

/** Antigüedad de saldos de una cuenta, en los tramos clásicos. */
data class CreditAging(
    val upTo30: Double = 0.0,
    val days31to60: Double = 0.0,
    val days61to90: Double = 0.0,
    val over90: Double = 0.0
) {
    val total: Double get() = upTo30 + days31to60 + days61to90 + over90
    val overdue30: Double get() = days31to60 + days61to90 + over90
    operator fun plus(o: CreditAging) = CreditAging(
        upTo30 + o.upTo30, days31to60 + o.days31to60, days61to90 + o.days61to90, over90 + o.over90
    )
}

private const val DAY_MS = 24L * 60 * 60 * 1000

fun creditAgingOf(charges: List<OpenCharge>, now: Long): CreditAging =
    charges.fold(CreditAging()) { acc, c ->
        val days = (now - c.at) / DAY_MS
        when {
            days <= 30 -> acc.copy(upTo30 = acc.upTo30 + c.remaining)
            days <= 60 -> acc.copy(days31to60 = acc.days31to60 + c.remaining)
            days <= 90 -> acc.copy(days61to90 = acc.days61to90 + c.remaining)
            else -> acc.copy(over90 = acc.over90 + c.remaining)
        }
    }

/**
 * Descuento de colaborador de una compra: suma, línea por línea, el % que lleva cada producto.
 * Lo que no tiene % (la mayoría del pan) no descuenta nada.
 *
 * @param lines (productId, subtotal de la línea)
 */
fun employeeCashDiscount(lines: List<Pair<String, Double>>, percentByProduct: Map<String, Double>): Double =
    kotlin.math.round(lines.sumOf { (pid, subtotal) -> cashDiscountFor(subtotal, percentByProduct[pid] ?: 0.0) } * 100) / 100.0

/** Descuento de [percent] % sobre [amount], redondeado a centavos. */
fun cashDiscountFor(amount: Double, percent: Double): Double =
    // Medio centavo sube (Q1.125 → Q1.13), como se redondea en caja. `round` redondearía al par.
    if (percent <= 0.0) 0.0 else ((amount * percent) + 1e-9).let { kotlin.math.floor(it + 0.5) } / 100.0
