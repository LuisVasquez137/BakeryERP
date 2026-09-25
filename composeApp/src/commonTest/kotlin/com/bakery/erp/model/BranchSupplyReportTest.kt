package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reporte "Pedido vs. recibido" (ver [buildBranchSupplyReport]).
 *
 * El modo de fallo real es contar dos veces o mezclar etapas: sumar lo empacado de una
 * nota que todavía no salió, comparar lo recibido contra una nota sin contar, o leer
 * como "reparto roto" el turno cuyo lote todavía no arranca.
 */
class BranchSupplyReportTest {

    private fun orderLine(productId: String, qty: Double, name: String = productId) =
        ProductionOrderLine(id = "l_$productId", productId = productId, productName = name, requestedQuantity = qty)

    private fun note(
        orderId: String,
        shiftId: String,
        status: String,
        vararg lines: DispatchNoteLine
    ) = DispatchNote(
        id = "n_${orderId}_$shiftId",
        sourceOrderId = orderId,
        shiftId = shiftId,
        shiftName = if (shiftId == "night") "Noche" else "Día",
        status = status,
        lines = lines.toList()
    )

    private fun noteLine(
        productId: String,
        assigned: Double,
        packed: Double = assigned,
        received: Double = 0.0,
        discrepancy: String? = null
    ) = DispatchNoteLine(
        productId = productId,
        productName = productId,
        requestedQuantity = assigned,
        packedQuantity = packed,
        receivedQuantity = received,
        discrepancyType = discrepancy
    )

    @Test
    fun twoShiftsThatAddUpToTheOrder() {
        val report = buildBranchSupplyReport(
            orderLines = mapOf("o1" to listOf(orderLine("pirujo", 200.0))),
            notes = listOf(
                note("o1", "night", DispatchStatus.DELIVERED, noteLine("pirujo", 125.0, received = 125.0)),
                note("o1", "day", DispatchStatus.DELIVERED, noteLine("pirujo", 75.0, received = 75.0))
            ),
            shiftOrder = mapOf("night" to 1, "day" to 2),
            expectedShiftsForOrder = { 2 }
        )
        val row = report.rows.single()
        assertEquals(listOf("night", "day"), report.columns.map { it.shiftId })
        assertEquals(125.0, row.byShift["night"]?.assigned)
        assertEquals(75.0, row.byShift["day"]?.assigned)
        assertEquals(200.0, row.sent)
        assertEquals(200.0, row.received)
        assertEquals(0.0, row.receptionDiff)
        assertFalse(row.splitMismatch)
        assertEquals(100.0, report.fulfillmentPct)
    }

    @Test
    fun shortfallAtReceptionIsTheDifferenceAgainstWhatWasCounted() {
        val report = buildBranchSupplyReport(
            orderLines = mapOf("o1" to listOf(orderLine("concha", 100.0))),
            notes = listOf(
                note(
                    "o1", "night", DispatchStatus.DELIVERED_WITH_DISCREPANCY,
                    noteLine("concha", 50.0, received = 47.0, discrepancy = DiscrepancyType.SHORT)
                ),
                // Segundo turno en camino: salió pero nadie lo contó todavía.
                note("o1", "day", DispatchStatus.IN_TRANSIT, noteLine("concha", 50.0))
            ),
            expectedShiftsForOrder = { 2 }
        )
        val row = report.rows.single()
        assertEquals(100.0, row.sent)
        assertEquals(50.0, row.sentCounted)
        assertEquals(47.0, row.received)
        assertEquals(-3.0, row.receptionDiff)
        assertEquals(50.0, row.awaitingCount)
        assertEquals(3.0, report.shortTotal)
        assertTrue(DiscrepancyType.SHORT in row.discrepancyTypes)
    }

    @Test
    fun draftNotesAreAssignedButNotSent() {
        val report = buildBranchSupplyReport(
            orderLines = mapOf("o1" to listOf(orderLine("pirujo", 40.0))),
            notes = listOf(note("o1", "", DispatchStatus.DRAFT, noteLine("pirujo", 40.0)))
        )
        val row = report.rows.single()
        assertEquals(40.0, row.assignedTotal)
        assertEquals(0.0, row.sent)
        assertTrue(row.byShift[""]!!.packingPending)
        assertFalse(row.splitMismatch)
    }

    @Test
    fun secondShiftNotStartedYetIsNotABrokenSplit() {
        val report = buildBranchSupplyReport(
            orderLines = mapOf("o1" to listOf(orderLine("pirujo", 200.0))),
            notes = listOf(note("o1", "night", DispatchStatus.READY, noteLine("pirujo", 100.0))),
            expectedShiftsForOrder = { 2 }
        )
        val row = report.rows.single()
        assertFalse(row.splitMismatch)
        assertEquals(100.0, row.notYetPlanned)
    }

    @Test
    fun shiftsThatDoNotAddUpAreFlagged() {
        // Los dos turnos ya despacharon y suman 180 de 200: el pedido se editó después
        // de repartir, o se anuló parte por mínimo de amasada.
        val report = buildBranchSupplyReport(
            orderLines = mapOf("o1" to listOf(orderLine("pirujo", 200.0))),
            notes = listOf(
                note("o1", "night", DispatchStatus.READY, noteLine("pirujo", 100.0)),
                note("o1", "day", DispatchStatus.READY, noteLine("pirujo", 80.0))
            ),
            expectedShiftsForOrder = { 2 }
        )
        assertTrue(report.rows.single().splitMismatch)
    }

    @Test
    fun orderWithoutAnyLotYetIsNotYetPlanned() {
        val report = buildBranchSupplyReport(
            orderLines = mapOf("o1" to listOf(orderLine("pirujo", 60.0))),
            notes = emptyList()
        )
        val row = report.rows.single()
        assertEquals(60.0, row.notYetPlanned)
        assertFalse(row.splitMismatch)
        assertTrue(report.columns.isEmpty())
    }

    @Test
    fun cancelledNotesStayOutOfSent() {
        val report = buildBranchSupplyReport(
            orderLines = mapOf("o1" to listOf(orderLine("pirujo", 30.0))),
            notes = listOf(note("o1", "", DispatchStatus.CANCELLED, noteLine("pirujo", 30.0)))
        )
        val row = report.rows.single()
        assertEquals(0.0, row.sent)
        assertEquals(30.0, row.cancelled)
        assertEquals(0.0, row.received)
    }

    @Test
    fun notesFromOtherOrdersAreIgnored() {
        val report = buildBranchSupplyReport(
            orderLines = mapOf("o1" to listOf(orderLine("pirujo", 10.0))),
            notes = listOf(note("otro", "", DispatchStatus.DELIVERED, noteLine("pirujo", 99.0, received = 99.0)))
        )
        assertEquals(0.0, report.rows.single().received)
    }

    @Test
    fun specialsHaveNoSalesAndCatalogProductsDefaultToZero() {
        val special = ProductionOrderLine(
            productId = "esp_abc", productName = "Pirujo doble", requestedQuantity = 5.0,
            isSpecial = true, specialRecipeId = "r1", specialRecipeName = "Francés"
        )
        val report = buildBranchSupplyReport(
            orderLines = mapOf("o1" to listOf(special, orderLine("concha", 10.0))),
            notes = emptyList(),
            soldByProduct = mapOf("esp_abc" to 3.0)
        )
        val bySku = report.rows.associateBy { it.productId }
        assertNull(bySku["esp_abc"]!!.sold)
        assertEquals("r1", bySku["esp_abc"]!!.specialRecipeId)
        assertEquals(0.0, bySku["concha"]!!.sold)
    }

    @Test
    fun ordersForTheSameProductAddUp() {
        val report = buildBranchSupplyReport(
            orderLines = mapOf(
                "o1" to listOf(orderLine("pirujo", 100.0)),
                "o2" to listOf(orderLine("pirujo", 120.0))
            ),
            notes = listOf(
                note("o1", "", DispatchStatus.DELIVERED, noteLine("pirujo", 100.0, received = 100.0)),
                note("o2", "", DispatchStatus.DELIVERED, noteLine("pirujo", 120.0, received = 118.0))
            )
        )
        val row = report.rows.single()
        assertEquals(220.0, row.ordered)
        assertEquals(218.0, row.received)
        assertEquals(-2.0, row.receptionDiff)
    }
}
