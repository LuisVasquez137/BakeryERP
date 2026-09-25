package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupSaleAllocationTest {

    private fun line(pid: String, expected: Double, counted: Double) =
        BreadCountLine(id = pid, productId = pid, productName = pid,
            expectedStock = expected, countedStock = counted,
            difference = counted - expected)

    private val group = mapOf("g" to setOf("frances", "sheca", "pirujo"))

    @Test
    fun ventaMenorAlFaltante_elRestoEsMerma() {
        val lines = listOf(line("frances", 200.0, 180.0), line("sheca", 100.0, 88.0))
        val a = allocateGroupSales(mapOf("g" to 30.0), group, lines)
        assertEquals(30.0, a.totalAllocated)
        assertEquals(0.0, a.totalUnexplained)
        // 20:12 → 18.75 / 11.25 → mayor residuo: 19 y 11
        assertEquals(19.0, a.soldOf("frances"))
        assertEquals(11.0, a.soldOf("sheca"))
        assertEquals(32.0, a.shortageByGroup["g"])
    }

    @Test
    fun ventaIgualAlFaltante_sinMerma() {
        val lines = listOf(line("frances", 50.0, 30.0), line("sheca", 50.0, 40.0))
        val a = allocateGroupSales(mapOf("g" to 30.0), group, lines)
        assertEquals(20.0, a.soldOf("frances"))
        assertEquals(10.0, a.soldOf("sheca"))
        assertEquals(0.0, a.totalUnexplained)
    }

    @Test
    fun ventaMayorAlFaltante_seAvisaYNoSeInventaMovimiento() {
        val lines = listOf(line("frances", 50.0, 40.0))
        val a = allocateGroupSales(mapOf("g" to 25.0), group, lines)
        assertEquals(10.0, a.soldOf("frances"))
        assertEquals(15.0, a.unexplainedByGroup["g"])
    }

    @Test
    fun redondeoConTresProductos_sumaExactoYNoPasaTopes() {
        val lines = listOf(
            line("frances", 10.0, 9.0),
            line("sheca", 10.0, 9.0),
            line("pirujo", 10.0, 9.0)
        )
        val a = allocateGroupSales(mapOf("g" to 2.0), group, lines)
        assertEquals(2.0, a.totalAllocated)
        lines.forEach { assertTrue(a.soldOf(it.productId) <= 1.0) }
        assertTrue(lines.all { a.soldOf(it.productId) % 1.0 == 0.0 })
    }

    @Test
    fun sobranteNoRecibeVenta() {
        val lines = listOf(line("frances", 50.0, 55.0), line("sheca", 50.0, 40.0))
        val a = allocateGroupSales(mapOf("g" to 8.0), group, lines)
        assertEquals(0.0, a.soldOf("frances"))
        assertEquals(8.0, a.soldOf("sheca"))
    }

    @Test
    fun grupoSinVentas_todoEsMerma() {
        val lines = listOf(line("frances", 50.0, 45.0))
        val a = allocateGroupSales(emptyMap(), group, lines)
        assertEquals(0.0, a.soldOf("frances"))
        assertEquals(0.0, a.totalUnexplained)
    }

    @Test
    fun productoFueraDelGrupoNoSeToca() {
        val lines = listOf(line("pastel", 5.0, 3.0), line("frances", 50.0, 45.0))
        val a = allocateGroupSales(mapOf("g" to 10.0), group, lines)
        assertEquals(0.0, a.soldOf("pastel"))
        assertEquals(5.0, a.soldOf("frances"))
        assertEquals(5.0, a.unexplainedByGroup["g"])
    }

    @Test
    fun exclusividadSoloEntreCategoriasQueCompartenSucursal() {
        val a = POSCategory(id = "a", productIds = listOf("p1"), branchIds = listOf("b1"))
        val b = POSCategory(id = "b", productIds = listOf("p2"), branchIds = listOf("b2"))
        val todas = POSCategory(id = "c", productIds = listOf("p3"))
        val nueva = POSCategory(id = "", branchIds = listOf("b1"))
        val conflicts = productCategoryConflicts(nueva, listOf(a, b, todas))
        assertEquals(setOf("p1", "p3"), conflicts.keys)
    }
}
