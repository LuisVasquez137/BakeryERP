package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CashHandoverTest {

    private val turno = CashSession(
        openingBalance = 500.0, totalCashSales = 1200.0,
        totalCardSales = 300.0, totalTransferSales = 150.0,
        carriedInCard = 200.0, carriedInTransfer = 50.0
    )

    @Test
    fun `entrega el efectivo contado menos lo retirado y acumula vouchers`() {
        val h = shiftHandover(turno, countedCash = 1690.0, withdrawal = 1000.0)
        assertEquals(690.0, h.cash)
        assertEquals(500.0, h.card)      // 200 recibidos + 300 del turno
        assertEquals(200.0, h.transfer)  // 50 recibidos + 150 del turno
    }

    @Test
    fun `no se retira mas de lo que hay ni un negativo`() {
        assertNull(closingWithdrawalProblem(1690.0, 1690.0))
        assertNotNull(closingWithdrawalProblem(1690.0, 1690.5))
        assertNotNull(closingWithdrawalProblem(1690.0, -1.0))
    }

    @Test
    fun `lo que se esperaba al abrir sale del ultimo cierre con entrega`() {
        val cerrado = turno.copy(status = CashSessionStatus.CLOSED, handoverRecorded = true, carriedOutCash = 690.0)
        assertEquals(690.0, expectedOpeningCash(cerrado))
        assertNull(expectedOpeningCash(cerrado.copy(handoverRecorded = false)))
        assertNull(expectedOpeningCash(null))
    }

    @Test
    fun `diferencia al abrir solo si habia que esperar`() {
        assertEquals(-10.0, CashSession(openingExpected = 500.0, openingBalance = 490.0).openingDifference)
        assertEquals(0.0, CashSession(openingExpected = 0.0, openingBalance = 490.0).openingDifference)
    }
}
