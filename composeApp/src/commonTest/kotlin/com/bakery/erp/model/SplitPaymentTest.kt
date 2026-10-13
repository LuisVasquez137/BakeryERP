package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SplitPaymentTest {

    private val card = SalePaymentMethod.CARD
    private val transfer = SalePaymentMethod.TRANSFER
    private val cash = SalePaymentMethod.CASH

    @Test
    fun cashIsWhatIsLeft() {
        assertEquals(mapOf(card to 50.0, cash to 30.0), splitPaymentParts(80.0, mapOf(card to 50.0)))
        assertEquals(
            mapOf(card to 10.10, transfer to 20.20, cash to 0.30),
            splitPaymentParts(30.60, mapOf(card to 10.10, transfer to 20.20))
        )
    }

    @Test
    fun cardForTheWholeTotalIsASingleMethod() {
        assertEquals(mapOf(card to 80.0), splitPaymentParts(80.0, mapOf(card to 80.0)))
    }

    @Test
    fun problems() {
        assertNull(splitPaymentProblem(80.0, mapOf(card to 50.0)))
        assertNotNull(splitPaymentProblem(80.0, emptyMap()))
        assertNotNull(splitPaymentProblem(80.0, mapOf(card to 0.0)))
        assertNotNull(splitPaymentProblem(80.0, mapOf(card to 60.0, transfer to 30.0)))
        assertNotNull(splitPaymentProblem(80.0, mapOf(card to -5.0, transfer to 10.0)))
    }

    @Test
    fun partsAreSpreadAcrossTheSalesOfOneCheckoutExactly() {
        // Cuenta del restaurante Q60 + pan Q20; pagan Q50 tarjeta y Q30 efectivo.
        val plan = allocatePaymentParts(
            mapOf(card to 50.0, cash to 30.0),
            listOf("rest" to 60.0, "pan" to 20.0)
        )
        assertEquals(mapOf(card to 50.0, cash to 10.0), plan["rest"])
        assertEquals(mapOf(cash to 20.0), plan["pan"])
        // Por método, el total del cobro no cambia.
        assertEquals(50.0, plan.values.sumOf { it[card] ?: 0.0 })
        assertEquals(30.0, plan.values.sumOf { it[cash] ?: 0.0 })
    }

    @Test
    fun centsDoNotGetLost() {
        val plan = allocatePaymentParts(
            mapOf(card to 0.10, transfer to 0.20, cash to 0.05),
            listOf("a" to 0.15, "b" to 0.20)
        )
        assertEquals(mapOf(card to 0.10, transfer to 0.05), plan["a"])
        assertEquals(mapOf(transfer to 0.15, cash to 0.05), plan["b"])
    }

    @Test
    fun headerMethodAndLabel() {
        assertEquals(card, headerMethodFor(mapOf(card to 5.0), cash))
        assertEquals(SalePaymentMethod.MIXED, headerMethodFor(mapOf(card to 5.0, cash to 1.0), cash))
        assertEquals("Tarjeta Q 50.00 · Efectivo Q 30.00", paymentPartsLabel(mapOf(cash to 30.0, card to 50.0)))
    }

    @Test
    fun lastCheckoutAddsUpThePartsOfEverySale() {
        val sales = listOf(
            Sale(id = "r", checkoutId = "co1", paymentMethod = SalePaymentMethod.MIXED, total = 60.0,
                paymentBreakdown = mapOf(card to 50.0, cash to 10.0), createdAt = 1),
            Sale(id = "p", checkoutId = "co1", paymentMethod = cash, total = 20.0,
                paymentBreakdown = mapOf(cash to 20.0), createdAt = 2)
        )
        val record = checkoutsFromSales(sales).single()
        assertEquals(SalePaymentMethod.MIXED, record.method)
        assertEquals(mapOf(card to 50.0, cash to 30.0), record.parts)
    }

    @Test
    fun dailyRollupSplitsByMethod() {
        val c = SaleContribution(
            branchId = "s1", branchName = "S1", businessDate = "2026-10-12", dateMillis = 0, hour = 9,
            total = 80.0, paymentMethod = SalePaymentMethod.MIXED, cashierId = "u", cashierName = "U",
            channel = SalesChannel.BAKERY, units = 3.0, productName = emptyMap(), productQty = emptyMap(),
            productRevenue = emptyMap(), paymentParts = mapOf(card to 50.0, cash to 30.0)
        )
        val daily = SalesDaily() + c
        assertEquals(mapOf(card to 50.0, cash to 30.0), daily.paymentTotal)
        assertEquals(80.0, daily.revenue)
    }
}
