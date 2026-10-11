package com.bakery.erp.domain

import com.bakery.erp.model.CostCenter
import com.bakery.erp.model.DispatchDestinationType
import com.bakery.erp.model.DispatchNote
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.model.Employee
import com.bakery.erp.model.Expense
import com.bakery.erp.model.PayrollRecord
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Costo del reparto (paso 5 de `arquitectura/propuesta-rentabilidad-por-sucursal.md`).
 *
 * El piloto y el camión no son costo del pan (no van a la planta) ni de nadie en general: los
 * paga cada tienda según las veces que el camión llegó a dejarle pan. Una entrega cuesta
 * parecido lleve mucho o poco, por eso se reparte por entregas y no por valor despachado.
 */

/** Una entrega = el camión llegó a una tienda en un día y turno, lleve una nota o varias. */
data class StoreDeliveries(val branchId: String, val branchName: String, val deliveries: Int)

/** Lo que le toca a cada tienda del reparto del mes. */
data class StoreDeliveryCharge(
    val branchId: String,
    val branchName: String,
    val deliveries: Int,
    val charge: Double
)

data class DeliveryMonthCost(
    /** Gastos anotados a Reparto (gasolina, mantenimiento del camión...). */
    val expenses: Double,
    /** Sueldo + prestaciones de los colaboradores marcados como Reparto. */
    val payroll: Double,
    val payrollPeople: Int,
    val stores: List<StoreDeliveryCharge>
) {
    val total: Double get() = expenses + payroll
    val deliveries: Int get() = stores.sumOf { it.deliveries }
    /** Q por entrega; null si en el mes no hubo entregas. */
    val costPerDelivery: Double? get() = if (deliveries > 0) total / deliveries else null
}

/** Estados en los que el pan ya salió en el camión. Lo que no salió no es una entrega. */
private val SHIPPED = setOf(
    DispatchStatus.IN_TRANSIT,
    DispatchStatus.ARRIVED,
    DispatchStatus.DELIVERED,
    DispatchStatus.DELIVERED_WITH_DISCREPANCY
)

/**
 * Entregas del mes por tienda, contadas desde las notas de despacho y no desde los repartos:
 * los repartos casi nunca se cierran y muchas notas salen sin reparto asignado. La nota de la
 * planta a su propia tienda no viaja (`origin == destination`), no cuenta.
 *
 * Pan y repostería del mismo día y turno a la misma tienda son una sola entrega.
 */
fun storeDeliveries(notes: List<DispatchNote>, tz: TimeZone): List<StoreDeliveries> =
    notes.asSequence()
        .filter { it.status in SHIPPED }
        .filter { it.destinationType == DispatchDestinationType.BRANCH && it.destinationBranchId.isNotEmpty() }
        .filter { it.destinationBranchId != it.originBranchId }
        .groupBy { it.destinationBranchId }
        .map { (branchId, list) ->
            val visits = list.map { n ->
                Instant.fromEpochMilliseconds(n.productionDate).toLocalDateTime(tz).date to n.shiftId
            }.toSet()
            StoreDeliveries(branchId, list.first().destinationBranchName, visits.size)
        }
        .sortedBy { it.branchName.lowercase() }

/** Sueldo + prestaciones de quienes están marcados como Reparto. */
fun deliveryPayroll(employees: List<Employee>, payrollRecords: List<PayrollRecord>): Pair<Double, Int> {
    val recordsByEmployee = payrollRecords.groupBy { it.employeeId }
    val costs = employees.filter { it.costCenter == CostCenter.DELIVERY }
        .map { it.monthlyCost(recordsByEmployee[it.id].orEmpty()) }
        .filter { it > 0.0 }
    return costs.sum() to costs.size
}

fun deliveryExpenses(expenses: List<Expense>): Double =
    expenses.filter { it.costCenter == CostCenter.DELIVERY }.sumOf { it.amount }

/** Reparte el costo del mes entre las tiendas por número de entregas. */
fun deliveryMonthCost(
    expenses: Double,
    payroll: Double,
    payrollPeople: Int,
    stores: List<StoreDeliveries>
): DeliveryMonthCost {
    val total = expenses + payroll
    val count = stores.sumOf { it.deliveries }
    return DeliveryMonthCost(
        expenses = expenses,
        payroll = payroll,
        payrollPeople = payrollPeople,
        stores = stores.map {
            StoreDeliveryCharge(
                it.branchId, it.branchName, it.deliveries,
                charge = if (count > 0) total * it.deliveries / count else 0.0
            )
        }
    )
}
