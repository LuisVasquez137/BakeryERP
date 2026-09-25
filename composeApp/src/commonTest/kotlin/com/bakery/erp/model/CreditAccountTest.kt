package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CreditAccountTest {

    private fun m(amount: Double, at: Long) = CreditMovement(id = "m$at", accountId = "a", amount = amount, createdAt = at)

    @Test
    fun saldoEsLaSumaDeMovimientos() {
        val movements = listOf(m(25.5, 1), m(10.0, 2), m(-30.0, 3))
        assertEquals(5.5, creditBalanceOf(movements))
    }

    @Test
    fun estadoDeCuentaAcumulaEnOrdenCronologico() {
        val statement = creditStatement(listOf(m(-20.0, 3), m(50.0, 1), m(10.0, 2)))
        assertEquals(listOf(50.0, 60.0, 40.0), statement.map { it.second })
    }

    @Test
    fun institucionExigePersonaYColaboradorTieneIdFijo() {
        assertEquals(true, CreditAccount(type = CreditAccountType.ORGANIZATION).requiresPerson)
        assertEquals(false, CreditAccount(type = CreditAccountType.PERSON).requiresPerson)
        assertEquals("emp_123", employeeCreditAccountId("123"))
        assertEquals("payroll_p1", payrollCreditMovementId("p1"))
    }

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun pagoSeAplicaALoMasViejoPrimero() {
        // Compra de 100 hace 70 días, compra de 50 hace 10 días, pago de 80.
        val now = 100 * day
        val open = openCharges(listOf(m(100.0, 30 * day), m(50.0, 90 * day), m(-80.0, 95 * day)))
        assertEquals(listOf(OpenCharge(30 * day, 20.0), OpenCharge(90 * day, 50.0)), open)
        val aging = creditAgingOf(open, now)
        assertEquals(50.0, aging.upTo30)
        assertEquals(20.0, aging.days61to90)
        assertEquals(20.0, aging.overdue30)
    }

    @Test
    fun pagoDeMasQuedaASuFavorYSeComeElCargoSiguiente() {
        val open = openCharges(listOf(m(30.0, 1), m(-50.0, 2), m(15.0, 3), m(10.0, 4)))
        assertEquals(listOf(OpenCharge(4, 5.0)), open)
    }

    @Test
    fun descuentoDeColaboradorRedondeaACentavos() {
        assertEquals(0.5, cashDiscountFor(10.0, 5.0))
        assertEquals(1.13, cashDiscountFor(11.25, 10.0))
        assertEquals(0.0, cashDiscountFor(10.0, 0.0))
    }

    @Test
    fun descuentoDeColaboradorEsPorProducto() {
        // Galletas empacadas 10% sobre Q20, gaseosa 5% sobre Q15, pan sin descuento.
        val lines = listOf("galletas" to 20.0, "gaseosa" to 15.0, "pan" to 5.0)
        val percents = mapOf("galletas" to 10.0, "gaseosa" to 5.0)
        assertEquals(2.75, employeeCashDiscount(lines, percents))
        assertEquals(0.0, employeeCashDiscount(listOf("pan" to 5.0), percents))
    }
}
