package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SaleVoidTest {

    private val session = CashSession(
        id = "s1", status = CashSessionStatus.OPEN, openingBalance = 100.0,
        totalCashSales = 50.0, totalCardSales = 30.0, totalSales = 80.0, saleCount = 3
    )
    private val cashSale = Sale(id = "a", sessionId = "s1", total = 20.0)

    private fun order(vararg sales: Sale) = SaleOrder("k", sales.toList())

    @Test
    fun cashSaleOfOpenShiftCanBeVoided() {
        assertNull(saleVoidBlocker(order(cashSale), session))
    }

    @Test
    fun closedShiftOrMissingSessionBlocks() {
        assertNotNull(saleVoidBlocker(order(cashSale), session.copy(status = CashSessionStatus.CLOSED)))
        assertNotNull(saleVoidBlocker(order(cashSale), null))
        assertNotNull(saleVoidBlocker(order(cashSale), session.copy(id = "otra")))
    }

    @Test
    fun unsupportedCasesBlock() {
        assertNotNull(saleVoidBlocker(order(cashSale.copy(status = SaleStatus.VOIDED)), session))
        assertNotNull(saleVoidBlocker(order(cashSale.copy(paymentMethod = SalePaymentMethod.CREDIT)), session))
        assertNotNull(saleVoidBlocker(order(cashSale.copy(depositAmount = 5.0)), session))
        assertNotNull(saleVoidBlocker(order(cashSale.copy(linkedCustomerOrderId = "p1")), session))
        assertNotNull(saleVoidBlocker(order(cashSale.copy(invoiceStatus = FelInvoiceStatus.SOLICITADA)), session))
        assertNotNull(saleVoidBlocker(order(cashSale, Sale(id = "r", sessionId = "s1", source = SaleSource.COMANDAYA)), session))
        // Facturada y certificada sí se puede: la factura se anula primero.
        assertNull(saleVoidBlocker(order(cashSale.copy(invoiceStatus = FelInvoiceStatus.CERTIFICADO)), session))
    }

    @Test
    fun cashVoidLowersExpectedCashAndSales() {
        val after = session.withSaleVoided(cashSale)
        assertEquals(30.0, after.totalCashSales)
        assertEquals(60.0, after.totalSales)
        assertEquals(2, after.saleCount)
        assertEquals(1, after.voidedSaleCount)
        assertEquals(20.0, after.totalVoidedSales)
        assertEquals(0.0, after.totalVoidedNonCash)
        assertEquals(session.expectedCashNow - 20.0, after.expectedCashNow)
    }

    @Test
    fun cardPartStaysWithTheVoucherAndGoesToBank() {
        val mixed = Sale(
            id = "m", sessionId = "s1", total = 30.0, paymentMethod = SalePaymentMethod.MIXED,
            paymentBreakdown = mapOf(SalePaymentMethod.CASH to 10.0, SalePaymentMethod.CARD to 20.0)
        )
        assertEquals(10.0, mixed.voidCashAmount)
        assertEquals(20.0, mixed.voidBankAmount)
        val after = session.withSaleVoided(mixed)
        assertEquals(40.0, after.totalCashSales)
        assertEquals(30.0, after.totalCardSales) // el voucher sigue en la gaveta
        assertEquals(50.0, after.totalSales)
        assertEquals(1, after.saleCount) // el pago dividido había sumado una por método
        assertEquals(20.0, after.totalVoidedNonCash)
    }

    @Test
    fun voidedSaleComesOutOfTheDailySummary() {
        val c = SaleContribution(
            branchId = "b", branchName = "B", businessDate = "2026-10-13", dateMillis = 0, hour = 9,
            total = 20.0, paymentMethod = SalePaymentMethod.CASH, cashierId = "u", cashierName = "U",
            channel = SalesChannel.BAKERY, units = 4.0, productName = mapOf("p" to "Pan"),
            productQty = mapOf("p" to 4.0), productRevenue = mapOf("p" to 20.0)
        )
        val base = SalesDaily() + c + c
        val after = base - c
        assertEquals(20.0, after.revenue)
        assertEquals(1, after.transactionCount)
        assertEquals(4.0, after.productQty["p"])
        assertEquals(20.0, after.paymentTotal[SalePaymentMethod.CASH])
        assertEquals(1, after.cashierCount["u"])
    }

    @Test
    fun pendingBankRefund() {
        val v = cashSale.copy(status = SaleStatus.VOIDED, bankRefundAmount = 15.0)
        assertEquals(true, v.bankRefundPending)
        assertEquals(false, v.copy(bankRefundDoneAt = 1).bankRefundPending)
        assertEquals(false, v.copy(bankRefundAmount = 0.0).bankRefundPending)
    }

    @Test
    fun pedidosYaVoidDoesNotLeaveTheDrawer() {
        val py = cashSale.copy(paymentMethod = SalePaymentMethod.PEDIDOS_YA)
        assertNull(saleVoidBlocker(order(py), session))
        assertEquals(0.0, py.voidCashAmount)
        assertEquals(20.0, py.voidBankAmount)
        val after = session.copy(totalPedidosYaSales = 20.0).withSaleVoided(py)
        assertEquals(50.0, after.totalCashSales)
        assertEquals(20.0, after.totalVoidedNonCash)
    }
}
