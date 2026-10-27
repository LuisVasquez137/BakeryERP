package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SalePaymentChangeTest {
    private val open = CashSession(id = "s1", status = CashSessionStatus.OPEN)
    private val cashSale = Sale(id = "v1", sessionId = "s1", status = SaleStatus.COMPLETED,
        total = 350.0, paymentMethod = SalePaymentMethod.CASH)

    @Test
    fun `efectivo a tarjeta en caja abierta mueve solo los totales`() {
        val deltas = paymentChangeSessionDeltas(open, cashSale, cashSale.withPaymentMethod(SalePaymentMethod.CARD))
        assertEquals(mapOf("totalCashSales" to -350.0, "totalCardSales" to 350.0), deltas)
    }

    @Test
    fun `caja cerrada corrige esperado diferencia y vouchers`() {
        val closed = open.copy(status = CashSessionStatus.CLOSED, difference = -374.5,
            countedCardVouchers = 753.0, handoverRecorded = true)
        val deltas = paymentChangeSessionDeltas(closed, cashSale, cashSale.withPaymentMethod(SalePaymentMethod.CARD))
        assertEquals(-350.0, deltas["expectedCash"])
        assertEquals(350.0, deltas["difference"])
        assertEquals(350.0, deltas["expectedCardVouchers"])
        assertEquals(350.0, deltas["carriedOutCard"])
        assertNull(deltas["carriedOutTransfer"])
    }

    @Test
    fun `pago dividido a un metodo cuenta una venta menos`() {
        val mixed = cashSale.copy(paymentMethod = SalePaymentMethod.MIXED,
            paymentBreakdown = mapOf(SalePaymentMethod.CASH to 100.0, SalePaymentMethod.CARD to 250.0))
        val deltas = paymentChangeSessionDeltas(open, mixed, mixed.withPaymentMethod(SalePaymentMethod.CARD))
        assertEquals(mapOf("totalCashSales" to -100.0, "totalCardSales" to 100.0, "saleCount" to -1.0), deltas)
    }

    @Test
    fun `bloqueos`() {
        val order = SaleOrder(key = "k", sales = listOf(cashSale))
        assertNull(paymentChangeBlocker(order, open))
        assertNotNull(paymentChangeBlocker(order, null))
        assertNotNull(paymentChangeBlocker(order, open.copy(status = CashSessionStatus.CLOSED, receivedByEmployeeName = "Ana")))
        assertNull(paymentChangeBlocker(order, open.copy(status = CashSessionStatus.CLOSED)))
        val credit = SaleOrder(key = "k", sales = listOf(cashSale.copy(paymentMethod = SalePaymentMethod.CREDIT)))
        assertNotNull(paymentChangeBlocker(credit, open))
    }

    @Test
    fun `permiso segun la caja`() {
        assertEquals(Permission.VOID_SALE, paymentChangePermission(open))
        assertEquals(Permission.VIEW_CASH_AUDIT, paymentChangePermission(open.copy(status = CashSessionStatus.CLOSED)))
    }
}
