package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals

class HeldCartTest {

    private fun cart(label: String, vararg lines: HeldCartLine) = HeldCart(label = label, lines = lines.toList())

    @Test
    fun labelUsesTheLowestFreeNumber() {
        assertEquals("Cuenta 1", nextHeldCartLabel(emptyList()))
        assertEquals("Cuenta 2", nextHeldCartLabel(listOf(cart("Cuenta 1"), cart("Señora de rojo"))))
        // Se cobró la 1 y quedó la 2: la siguiente vuelve a ser la 1.
        assertEquals("Cuenta 1", nextHeldCartLabel(listOf(cart("Cuenta 2"))))
    }

    @Test
    fun totalAndSummaryForTheAuditLog() {
        val c = cart(
            "Don Luis",
            HeldCartLine(productName = "Champurrada", quantity = 3.0, unitPrice = 1.5),
            HeldCartLine(productName = "Bolsa", quantity = 1.0, unitPrice = 0.25)
        )
        assertEquals(4.75, c.total)
        assertEquals("3 × Champurrada, 1 × Bolsa", c.itemsSummary())
    }

    @Test
    fun minutesWaiting() {
        val c = HeldCart(createdAt = 1_000_000L)
        assertEquals(3L, c.minutesWaiting(1_000_000L + 3 * 60_000L + 59_000L))
        assertEquals(0L, c.minutesWaiting(900_000L)) // reloj atrasado: nunca negativo
    }
}
