package com.bakery.erp.model

import com.bakery.erp.domain.deliveryExpenses
import com.bakery.erp.domain.deliveryMonthCost
import com.bakery.erp.domain.deliveryPayroll
import com.bakery.erp.domain.storeDeliveries
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeliveryCostTest {

    private val tz = TimeZone.UTC
    private fun day(d: Int) = LocalDate(2026, 10, d).atStartOfDayIn(tz).toEpochMilliseconds()
    private fun near(expected: Double, actual: Double?) =
        assertTrue(actual != null && abs(expected - actual) < 1e-6, "esperado $expected, salió $actual")

    private fun note(dest: String, d: Int, shift: String = "dia", origin: String = "pan", status: String = DispatchStatus.DELIVERED) =
        DispatchNote(originBranchId = origin, destinationBranchId = dest, destinationBranchName = dest.uppercase(),
            productionDate = day(d), shiftId = shift, status = status)

    @Test
    fun panYReposteriaDelMismoTurnoSonUnaEntrega() {
        val stores = storeDeliveries(listOf(
            note("t1", 1), note("t1", 1, origin = "rep"),   // mismo viaje
            note("t1", 1, shift = "noche"),                  // segundo viaje
            note("t1", 2),
            note("t2", 1)
        ), tz).associateBy { it.branchId }
        assertEquals(3, stores.getValue("t1").deliveries)
        assertEquals(1, stores.getValue("t2").deliveries)
    }

    @Test
    fun loQueNoViajaNoCuenta() {
        val stores = storeDeliveries(listOf(
            note("pan", 1),                                         // planta a su propia tienda
            note("t1", 1, status = DispatchStatus.READY),          // no salió
            note("t1", 2, status = DispatchStatus.CANCELLED),
            note("t1", 3, status = DispatchStatus.ARRIVED)          // llegó, falta conteo: sí cuenta
        ), tz)
        assertEquals(1, stores.single().deliveries)
    }

    @Test
    fun seRepartePorEntregas() {
        val employees = listOf(
            Employee(id = "p", baseSalary = 4000.0, monthlyBenefits = 500.0, costCenter = CostCenter.DELIVERY),
            Employee(id = "x", baseSalary = 3000.0)
        )
        val (payroll, people) = deliveryPayroll(employees, emptyList())
        near(4500.0, payroll)
        assertEquals(1, people)
        val expenses = deliveryExpenses(listOf(
            Expense(amount = 1500.0, costCenter = CostCenter.DELIVERY),
            Expense(amount = 900.0, costCenter = CostCenter.GENERAL)
        ))
        near(1500.0, expenses)

        val stores = storeDeliveries((1..30).map { note("t1", it % 28 + 1) } + (1..10).map { note("t2", it) }, tz)
        val cost = deliveryMonthCost(expenses, payroll, people, stores)
        near(6000.0, cost.total)
        assertEquals(38, cost.deliveries)
        near(6000.0, cost.stores.sumOf { it.charge })
        near(6000.0 * 10 / 38, cost.stores.first { it.branchId == "t2" }.charge)
    }

    @Test
    fun sinEntregasNoHayCostoPorEntrega() {
        assertNull(deliveryMonthCost(100.0, 0.0, 0, emptyList()).costPerDelivery)
    }
}
