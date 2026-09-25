package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Los totales que ve quien revisa una caja.
 *
 * El modo de fallo real no es un decimal mal redondeado: es que un gasto eliminado siga
 * contando, o que el movimiento que lo devolvió aparezca como "ingreso". Las dos cosas hacen
 * que el total de la pantalla no cuadre con el efectivo del cajón, y ahí la pantalla deja de
 * servir para lo único que existe.
 */
class CashMovementTotalsTest {

    private fun mov(
        id: String,
        type: String,
        amount: Double,
        reason: String = "algo",
        purchaseId: String = "",
        movementId: String = ""
    ) = CashMovement(
        id = id,
        sessionId = "s1",
        type = type,
        amount = amount,
        reason = reason,
        sourcePurchaseId = purchaseId,
        sourceMovementId = movementId
    )

    @Test
    fun `gastos e ingresos se separan por el signo del movimiento`() {
        val totals = cashMovementTotals(
            listOf(
                mov("1", CashMovementType.EXPENSE, 100.0, purchaseId = "p1"),
                mov("2", CashMovementType.FUND_ADDITION, 250.0),
                mov("3", CashMovementType.DEPOSIT, 50.0)
            )
        )
        assertEquals(100.0, totals.gastos)
        assertEquals(300.0, totals.ingresos)
    }

    @Test
    fun `un gasto eliminado deja de contar y su devolucion no es un ingreso`() {
        val all = listOf(
            mov("1", CashMovementType.EXPENSE, 100.0, purchaseId = "p1"),
            mov("2", CashMovementType.DEPOSIT, 100.0, reason = "Eliminado: Cloro", purchaseId = "p1")
        )
        val totals = cashMovementTotals(all)
        // Sin agrupar el ajuste con su gasto daría gastos=100 e ingresos=100, y la pantalla
        // diría que se movieron Q200 el día que no se movió nada.
        assertEquals(0.0, totals.gastos)
        assertEquals(0.0, totals.ingresos)
    }

    @Test
    fun `un gasto corregido cuenta por su monto nuevo`() {
        val all = listOf(
            mov("1", CashMovementType.EXPENSE, 50.0, purchaseId = "p1"),
            mov("2", CashMovementType.EXPENSE, 10.0, reason = "Corrección: Cloro", purchaseId = "p1")
        )
        assertEquals(60.0, cashMovementTotals(all).gastos)
    }

    @Test
    fun `corregir hacia abajo devuelve efectivo sin volverse ingreso`() {
        val all = listOf(
            mov("1", CashMovementType.EXPENSE, 325.0, purchaseId = "p1"),
            mov("2", CashMovementType.DEPOSIT, 292.5, reason = "Corrección: Taxi", purchaseId = "p1")
        )
        val totals = cashMovementTotals(all)
        assertEquals(32.5, totals.gastos)
        assertEquals(0.0, totals.ingresos)
    }

    @Test
    fun `un ingreso eliminado se anula por su vinculo de movimiento`() {
        val all = listOf(
            mov("1", CashMovementType.FUND_ADDITION, 200.0),
            mov("2", CashMovementType.WITHDRAWAL, 200.0,
                reason = "Eliminado: cambio que mandaron", movementId = "1")
        )
        val totals = cashMovementTotals(all)
        assertEquals(0.0, totals.ingresos)
        assertEquals(0.0, totals.gastos)
    }

    @Test
    fun `un ingreso corregido cuenta por su monto nuevo`() {
        val all = listOf(
            mov("1", CashMovementType.DEPOSIT, 100.0),
            mov("2", CashMovementType.WITHDRAWAL, 40.0,
                reason = "Corrección: propina", movementId = "1")
        )
        assertEquals(60.0, cashMovementTotals(all).ingresos)
    }

    @Test
    fun `lo revertido se marca y lo corregido no`() {
        val eliminado = listOf(
            mov("1", CashMovementType.FUND_ADDITION, 200.0),
            mov("2", CashMovementType.WITHDRAWAL, 200.0, reason = "Eliminado: x", movementId = "1")
        )
        assertTrue(eliminado[0].isReverted(eliminado))

        // Corregir no bloquea: se puede volver a corregir. Solo eliminar cierra la puerta.
        val corregido = listOf(
            mov("1", CashMovementType.FUND_ADDITION, 200.0),
            mov("2", CashMovementType.WITHDRAWAL, 50.0, reason = "Corrección: x", movementId = "1")
        )
        assertFalse(corregido[0].isReverted(corregido))
    }

    @Test
    fun `un traslado entre cajas cuenta como movimiento normal`() {
        // No es un ajuste: el dinero de verdad se fue a otra gaveta, y el espejo vive en la
        // otra sesión — no en esta lista, así que no puede compensarse acá.
        val all = listOf(mov("1", CashMovementType.TRANSFER_OUT, 500.0, reason = "A caja 2"))
        assertEquals(500.0, cashMovementTotals(all).gastos)
    }

    @Test
    fun `un ajuste huerfano no se pierde del total`() {
        // Si el movimiento original ya no está —dato viejo, borrado a mano— el ajuste sigue
        // siendo dinero que se movió. Contarlo como cero escondería efectivo real.
        val all = listOf(
            mov("2", CashMovementType.DEPOSIT, 80.0, reason = "Eliminado: algo", movementId = "no-existe")
        )
        assertEquals(80.0, cashMovementTotals(all).ingresos)
    }

    @Test
    fun `el detalle del cierre suma lo mismo que el total y esconde lo eliminado`() {
        val all = listOf(
            mov("1", CashMovementType.EXPENSE, 50.0, reason = "Taxi", purchaseId = "p1"),
            mov("2", CashMovementType.EXPENSE, 10.0, reason = "Corrección: Taxi", purchaseId = "p1"),
            mov("3", CashMovementType.EXPENSE, 100.0, reason = "Cloro", purchaseId = "p2"),
            mov("4", CashMovementType.DEPOSIT, 100.0, reason = "Eliminado: Cloro", purchaseId = "p2"),
            mov("5", CashMovementType.FUND_ADDITION, 200.0, reason = "Cambio")
        )
        val items = cashSessionDetailItems(all)
        assertEquals(listOf("1", "5"), items.map { it.movement.id })
        val taxi = items.first()
        assertEquals(60.0, taxi.amount)
        assertTrue(taxi.isOutflow)
        assertTrue(taxi.wasCorrected)
        val totals = cashMovementTotals(all)
        assertEquals(totals.gastos, items.filter { it.isOutflow }.sumOf { it.amount })
        assertEquals(totals.ingresos, items.filterNot { it.isOutflow }.sumOf { it.amount })
        assertFalse(items.last().wasCorrected)
    }
}
