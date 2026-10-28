package com.bakery.erp.model

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StaffBreadTest {

    private fun line(pid: String, expected: Double, counted: Double) =
        BreadCountLine(id = pid, productId = pid, productName = pid,
            expectedStock = expected, countedStock = counted,
            difference = counted - expected)

    private val group = mapOf("g" to setOf("frances", "sheca"))

    @Test
    fun colaboradorSeReparteDespuesDeLaVenta() {
        // Faltan 20 + 10; se vendieron 24 y 4 fueron de colaboradoras → 2 de merma.
        val lines = listOf(line("frances", 100.0, 80.0), line("sheca", 50.0, 40.0))
        val a = allocateGroupSales(mapOf("g" to 24.0), group, lines, mapOf("g" to 4.0))
        assertEquals(24.0, a.totalAllocated)
        assertEquals(4.0, a.totalStaff)
        assertEquals(0.0, a.totalUnexplained)
        lines.forEach { assertEquals(true, a.soldOf(it.productId) + a.staffOf(it.productId) <= it.shortage) }
    }

    @Test
    fun colaboradorSinFaltanteQueAlcance_seAvisa() {
        val lines = listOf(line("frances", 50.0, 40.0))
        val a = allocateGroupSales(mapOf("g" to 8.0), group, lines, mapOf("g" to 4.0))
        assertEquals(8.0, a.soldOf("frances"))
        assertEquals(2.0, a.staffOf("frances"))
        assertEquals(2.0, a.totalUnexplained)
    }

    @Test
    fun soloColaborador_sinVentas() {
        val lines = listOf(line("frances", 50.0, 47.0))
        val a = allocateGroupSales(emptyMap(), group, lines, mapOf("g" to 3.0))
        assertEquals(3.0, a.staffOf("frances"))
        assertEquals(0.0, a.totalAllocated)
    }

    private fun staff(employee: String, units: Double, at: Long = 1_000L) = CounterRemoval(
        kind = CounterRemovalKind.STAFF_BREAD,
        responsibleEmployeeId = employee,
        responsibleEmployeeName = employee,
        groupId = "g",
        groupUnits = units,
        groupUnitPrice = 0.5,
        createdAt = at
    )

    @Test
    fun topeDiarioPorPersona_bloquea() {
        val tz = TimeZone.UTC
        val today = listOf(staff("ana", 3.0), staff("luis", 4.0))
        val used = staffBreadUsedOn(today, "ana", 2_000L, tz)
        assertEquals(3.0, used)
        assertNull(counterRemovalError(staff("ana", 1.0), false, 0.0, 0.0, 4.0, used))
        assertNotNull(counterRemovalError(staff("ana", 2.0), false, 0.0, 0.0, 4.0, used))
        // Sin tope configurado no se da.
        assertNotNull(counterRemovalError(staff("ana", 1.0), false, 0.0, 0.0, 0.0, 0.0))
    }

    @Test
    fun loDeAyerNoCuenta() {
        val day = 24L * 60 * 60 * 1000
        val used = staffBreadUsedOn(listOf(staff("ana", 4.0, at = 1_000L)), "ana", day + 1_000L, TimeZone.UTC)
        assertEquals(0.0, used)
    }

    @Test
    fun valorAPrecioDeVenta() {
        assertEquals(2.0, staff("ana", 4.0).totalPrice)
        assertEquals(4.0, staff("ana", 4.0).totalUnits)
    }
}
