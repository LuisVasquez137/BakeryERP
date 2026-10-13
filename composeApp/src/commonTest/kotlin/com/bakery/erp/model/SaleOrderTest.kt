package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaleOrderTest {

    private val bread = Sale(id = "b1", checkoutId = "c1", total = 20.0, createdAt = 100, lineCount = 3, registerId = "r1")
    private val food = Sale(
        id = "f1", checkoutId = "c1", total = 55.5, createdAt = 50, collectedAt = 110,
        salesChannel = SalesChannel.RESTAURANT, source = SaleSource.COMANDAYA,
        externalOrderNumber = "47", externalTableLabel = "Mesa 4", registerId = "r1"
    )

    @Test
    fun breadAndRestaurantPaidTogetherAreOneOrder() {
        val other = Sale(id = "x", total = 5.0, createdAt = 200)
        val pending = Sale(id = "p", total = 9.0, createdAt = 300, status = SaleStatus.PENDING_COLLECTION)
        val orders = ordersFromSales(listOf(food, other, bread, pending))
        assertEquals(listOf("sale_x", "c1"), orders.map { it.key })
        val o = orders[1]
        assertEquals(listOf("b1", "f1"), o.sales.map { it.id }) // pan primero
        assertEquals(75.5, o.total)
        assertEquals(110, o.at)
        assertEquals("Pan · 3 ítem(s) · Restaurante · #47 · Mesa 4", o.description)
    }

    @Test
    fun invoiceFilterCountsOnlyWhatCanStillBeInvoiced() {
        val o = SaleOrder("c1", listOf(bread, food))
        assertTrue(OrderInvoiceFilter.NOT_INVOICED.accepts(o))
        // ComandaYa ya facturó el restaurante: el pan todavía se puede facturar.
        val partly = SaleOrder("c1", listOf(bread, food.copy(invoiceStatus = FelInvoiceStatus.EXTERNA)))
        assertEquals(listOf("b1"), partly.uninvoicedSales.map { it.id })
        assertTrue(OrderInvoiceFilter.INVOICED.accepts(partly))
        val done = SaleOrder("c1", listOf(bread.copy(invoiceStatus = "CERTIFICADO", dteId = "d"), food.copy(invoiceStatus = "CERTIFICADO", dteId = "d")))
        assertFalse(OrderInvoiceFilter.NOT_INVOICED.accepts(done))
        assertEquals("b1", done.invoicedSale?.id)
    }

    @Test
    fun searchByAmountOrderNumberOrName() {
        val o = SaleOrder("c1", listOf(bread, food.copy(invoiceReceptorName = "JUAN PEREZ")))
        assertTrue(o.matches("75"))      // total del cobro
        assertTrue(o.matches("Q 75.5"))
        assertTrue(o.matches("20"))      // solo el pan
        assertTrue(o.matches("47"))      // número de orden
        assertTrue(o.matches("mesa 4"))
        assertTrue(o.matches("juan"))
        assertFalse(o.matches("30"))
        assertTrue(o.matches(""))
    }

    @Test
    fun splitPaymentAcrossSalesAddsUp() {
        val o = SaleOrder("c1", listOf(
            bread.copy(paymentMethod = SalePaymentMethod.CARD),
            food.copy(paymentMethod = SalePaymentMethod.CASH)
        ))
        assertEquals(SalePaymentMethod.MIXED, o.paymentMethod)
        assertEquals(mapOf(SalePaymentMethod.CARD to 20.0, SalePaymentMethod.CASH to 55.5), o.paymentParts)
    }
}
