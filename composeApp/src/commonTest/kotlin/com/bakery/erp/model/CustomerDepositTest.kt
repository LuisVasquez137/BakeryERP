package com.bakery.erp.model

import kotlin.test.Test
import kotlinx.datetime.toInstant
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
    fun `el encargo del catalogo se describe y se valora`() {
        val items = listOf(
            CustomerDepositItem("p1", "Pastel tres leches", 1.0, 250.0),
            CustomerDepositItem("p2", "Pirujo", 24.0, 1.0)
        )
        assertEquals("1 Pastel tres leches, 24 Pirujo", depositItemsDescription(items))
        assertEquals(274.0, depositItemsTotal(items))
    }

    @Test
    fun `la entrega se lee como hoy, manana o la fecha`() {
        val tz = kotlinx.datetime.TimeZone.of("America/Guatemala")
        // 2026-09-28 lunes 09:00 en Guatemala = 15:00 UTC.
        fun at(month: Int, d: Int, h: Int, m: Int) =
            kotlinx.datetime.LocalDateTime(2026, month, d, h, m).toInstant(tz).toEpochMilliseconds()
        val now = at(9, 28, 9, 0)
        assertEquals("Hoy 4:30 PM", deliveryDayLabel(at(9, 28, 16, 30), now, tz))
        assertEquals("Mañana 10:00 AM", deliveryDayLabel(at(9, 29, 10, 0), now, tz))
        assertEquals("sáb 03/10 12:15 PM", deliveryDayLabel(at(10, 3, 12, 15), now, tz))
        assertEquals("", deliveryDayLabel(0L, now, tz))
    }

    @Test
    fun `la fecha larga lleva el dia de la semana`() {
        assertEquals("Miércoles 30/09/2026", longDateLabel(kotlinx.datetime.LocalDate(2026, 9, 30)))
        assertEquals("Sábado 03/10/2026", longDateLabel(kotlinx.datetime.LocalDate(2026, 10, 3)))
    }

    @Test
    fun `no se acepta una entrega que ya paso`() {
        val now = 1_000_000L
        assertEquals(null, depositDeliveryProblem(now + 60_000, now))
        assertEquals("Esa fecha y hora de entrega ya pasaron.", depositDeliveryProblem(now - 1, now))
        assertEquals("Esa fecha y hora de entrega ya pasaron.", depositDeliveryProblem(now, now))
        assertEquals("Falta la fecha y hora de entrega.", depositDeliveryProblem(0L, now))
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
