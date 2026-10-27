package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DayCashTotalsTest {

    // Caja 1, turno de la mañana: abre con 500, vende 1000, cuenta 1490 (faltan 10), manda 400 al
    // banco y entrega 1090 al turno de la tarde.
    private val manana = CashSession(
        id = "m", status = CashSessionStatus.CLOSED, openingBalance = 500.0, totalCashSales = 1000.0,
        expectedCash = 1500.0, countedCash = 1490.0, difference = -10.0,
        closingWithdrawal = 400.0, carriedOutCash = 1090.0, handoverRecorded = true
    )
    // Tarde: recibe 1090, vende 600, cuenta 1695 (sobran 5), cierre del día sin retiro.
    private val tarde = CashSession(
        id = "t", status = CashSessionStatus.CLOSED, previousSessionId = "m", openingBalance = 1090.0,
        totalCashSales = 600.0, expectedCash = 1690.0, countedCash = 1695.0, difference = 5.0,
        carriedOutCash = 1695.0, handoverRecorded = true
    )
    // Caja 2, un solo turno.
    private val caja2 = CashSession(
        id = "c2", status = CashSessionStatus.CLOSED, openingBalance = 0.0, totalCashSales = 300.0,
        expectedCash = 300.0, countedCash = 300.0, difference = 0.0,
        carriedOutCash = 300.0, handoverRecorded = true
    )

    @Test
    fun `lo entregado de un turno al siguiente no se cuenta dos veces`() {
        val t = dayCashTotals(listOf(manana, tarde, caja2))
        assertEquals(1995.0, t.counted)       // 1695 de la caja 1 + 300 de la caja 2, no 3485
        assertEquals(-5.0, t.difference)      // -10 + 5 + 0
        assertEquals(2000.0, t.expected)      // lo que habría sin diferencias
        assertEquals(400.0, t.takenAtShiftClose)
    }

    @Test
    fun `turno que se entrego a uno todavia abierto no cuenta como final`() {
        val abierto = tarde.copy(status = CashSessionStatus.OPEN)
        val t = dayCashTotals(closed = listOf(manana), all = listOf(manana, abierto))
        assertEquals(0.0, t.counted)
    }

    @Test
    fun `cierre viejo sin entrega registrada usa lo contado`() {
        val viejo = caja2.copy(handoverRecorded = false, carriedOutCash = 0.0)
        assertEquals(300.0, dayCashTotals(listOf(viejo)).counted)
    }
}
