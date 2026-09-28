package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomerOrderDepositTest {

    private val cake = CustomerOrder(
        id = "o1", type = CustomerOrderType.CAKE, status = CustomerOrderStatus.CONFIRMED,
        customerName = "Ana López", totalAmount = 300.0, balanceAmount = 300.0
    )

    @Test
    fun `un pastel confirmado con saldo acepta anticipo`() {
        assertTrue(cake.acceptsDeposit())
        assertTrue(cake.copy(status = CustomerOrderStatus.READY).acceptsDeposit())
    }

    @Test
    fun `entregado, cancelado, pagado o de revendedor no`() {
        assertFalse(cake.copy(status = CustomerOrderStatus.DELIVERED).acceptsDeposit())
        assertFalse(cake.copy(status = CustomerOrderStatus.CANCELLED).acceptsDeposit())
        assertFalse(cake.copy(balanceAmount = 0.0).acceptsDeposit())
        assertFalse(cake.copy(type = CustomerOrderType.RESELLER).acceptsDeposit())
    }

    @Test
    fun `etiqueta y descripcion para el anticipo`() {
        assertEquals("Pastel · 12/10 · Ana López", cake.depositLabel("12/10"))
        val lines = listOf(CustomerOrderLine(productName = "Pastel tres leches", requestedQuantity = 1.0))
        assertEquals("1 Pastel tres leches", cake.depositDescription(lines))
        assertEquals("Pastel", cake.depositDescription(emptyList()))
    }

    @Test
    fun `el anticipo escrito a mano se migra solo si no hay pagos`() {
        val legacy = cake.copy(advancePaidAmount = 100.0)
        assertEquals(100.0, legacyAdvanceToMigrate(legacy, emptyList()))
        assertEquals(0.0, legacyAdvanceToMigrate(legacy, listOf(CustomerOrderPayment(amount = 100.0))))
        assertEquals(0.0, legacyAdvanceToMigrate(cake, emptyList()))
    }
}
