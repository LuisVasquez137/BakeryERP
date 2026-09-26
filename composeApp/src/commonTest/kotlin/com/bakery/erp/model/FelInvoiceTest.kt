package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FelInvoiceTest {

    @Test
    fun `consumidor final pasa debajo del limite`() {
        assertNull(felReceptorProblem(FelInvoiceRequest(), 2499.99))
    }

    @Test
    fun `consumidor final desde Q2500 exige NIT`() {
        assertNotNull(felReceptorProblem(FelInvoiceRequest(), 2500.0))
        assertTrue(felRequiresIdentifiedBuyer(2500.0))
        assertFalse(felRequiresIdentifiedBuyer(2499.99))
    }

    @Test
    fun `NIT valido con nombre pasa aunque el monto sea grande`() {
        assertNull(felReceptorProblem(FelInvoiceRequest("59325216", "LUIS VASQUEZ"), 9000.0))
    }

    @Test
    fun `NIT con digito equivocado o sin nombre no pasa`() {
        assertEquals("El NIT no es válido.", felReceptorProblem(FelInvoiceRequest("59325217", "X"), 10.0))
        assertEquals("Falta el nombre del cliente.", felReceptorProblem(FelInvoiceRequest("59325216", " "), 10.0))
    }

    @Test
    fun `estados que todavia terminan en factura solos`() {
        assertTrue(FelInvoiceStatus.isInFlight(FelInvoiceStatus.SOLICITADA))
        assertTrue(FelInvoiceStatus.isInFlight(FelInvoiceStatus.PENDIENTE))
        assertFalse(FelInvoiceStatus.isInFlight(FelInvoiceStatus.RECHAZADO))
        assertFalse(FelInvoiceStatus.isInFlight(""))
    }
}
