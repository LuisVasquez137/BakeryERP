package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomerDepositTest {

    private val deposit = CustomerDeposit(
        folio = "ANT-0042", customerName = "María José Pérez", customerPhone = "5512-3456",
        description = "Pastel de chocolate", amount = 150.0
    )

    @Test
    fun `lo que queda descuenta lo aplicado y lo devuelto`() {
        assertEquals(150.0, deposit.remaining)
        assertEquals(50.0, deposit.copy(appliedAmount = 100.0).remaining)
        assertEquals(0.0, deposit.copy(appliedAmount = 100.0, refundedAmount = 50.0).remaining)
        assertFalse(deposit.copy(appliedAmount = 150.0).isOpen)
    }

    @Test
    fun `folio correlativo`() {
        assertEquals("ANT-0042", customerDepositFolio(42))
        assertEquals("ANT-12345", customerDepositFolio(12345))
    }

    @Test
    fun `se encuentra por folio, nombre sin tildes o telefono`() {
        assertTrue(deposit.matches("ANT-0042"))
        assertTrue(deposit.matches("42"))
        assertTrue(deposit.matches("0042"))
        assertTrue(deposit.matches("perez maria"))
        assertTrue(deposit.matches("chocolate"))
        assertTrue(deposit.matches("3456"))
        assertFalse(deposit.matches("43"))
        assertFalse(deposit.matches("juan"))
    }

    @Test
    fun `el anticipo se reparte en orden sin pasar el total de cada venta`() {
        val plan = planDepositApplication(150.0, listOf("rest" to 120.0, "pan" to 60.0))
        assertEquals(mapOf("rest" to 120.0, "pan" to 30.0), plan)
    }

    @Test
    fun `si el anticipo sobra, solo se usa lo que cuestan las ventas`() {
        assertEquals(mapOf("pan" to 40.0), planDepositApplication(150.0, listOf("pan" to 40.0)))
        assertEquals(emptyMap(), planDepositApplication(0.0, listOf("pan" to 40.0)))
    }

    @Test
    fun `las formas de pago separan el anticipo del saldo`() {
        assertEquals(
            mapOf(SalePaymentMethod.CUSTOMER_DEPOSIT to 100.0, SalePaymentMethod.CASH to 50.0),
            paymentBreakdownWithDeposit(150.0, 100.0, SalePaymentMethod.CASH)
        )
        // Cubierto completo: no queda saldo que cobrar.
        assertEquals(
            mapOf(SalePaymentMethod.CUSTOMER_DEPOSIT to 150.0),
            paymentBreakdownWithDeposit(150.0, 150.0, SalePaymentMethod.CASH)
        )
        assertTrue(paymentBreakdownWithDeposit(150.0, 0.0, SalePaymentMethod.CASH).isEmpty())
    }
}
