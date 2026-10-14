package com.bakery.erp.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Los tres casos de "Sacar del mostrador" y sus controles: la merma pide foto, el accidente
 * pide responsable y costo, el regalo pide nota y no pasa la cuota. Nadie se perdona a sí misma.
 */
class CounterRemovalTest {

    private val tz = TimeZone.of("America/Guatemala")
    private fun at(h: Int, day: Int = 14) =
        LocalDateTime(2026, 10, day, h, 0).toInstant(tz).toEpochMilliseconds()

    private val pirujo = CounterRemovalLine("p1", "Pirujo", 4.0, unitCost = 0.30, unitPrice = 0.50)
    private val pastel = CounterRemovalLine("p2", "Selva negra", 1.0, unitCost = 60.0, unitPrice = 125.0)

    private fun removal(kind: String, vararg lines: CounterRemovalLine, note: String = "", responsible: String = "") =
        CounterRemoval(kind = kind, lines = lines.toList(), note = note, responsibleEmployeeId = responsible)

    @Test
    fun sinProductosNoSeRegistraNada() {
        assertNotNull(counterRemovalError(removal(CounterRemovalKind.DAMAGED), hasPhoto = true, 0.0, 0.0))
    }

    @Test
    fun laMermaExigeFoto() {
        val r = removal(CounterRemovalKind.DAMAGED, pirujo)
        assertNotNull(counterRemovalError(r, hasPhoto = false, 0.0, 0.0))
        assertNull(counterRemovalError(r, hasPhoto = true, 0.0, 0.0))
    }

    @Test
    fun elAccidenteExigeResponsableYCostoConocido() {
        assertNotNull(counterRemovalError(removal(CounterRemovalKind.ACCIDENT, pirujo), false, 0.0, 0.0))
        assertNull(counterRemovalError(removal(CounterRemovalKind.ACCIDENT, pirujo, responsible = "e1"), false, 0.0, 0.0))
        // Cargar Q0 sería perdonarlo sin que nadie lo decida.
        val sinCosto = pirujo.copy(unitCost = 0.0)
        assertNotNull(counterRemovalError(removal(CounterRemovalKind.ACCIDENT, sinCosto, responsible = "e1"), false, 0.0, 0.0))
    }

    @Test
    fun elAccidenteSeCargaACosto() {
        val r = removal(CounterRemovalKind.ACCIDENT, pirujo, pastel, responsible = "e1")
        assertEquals(61.2, r.totalCost)
        assertEquals(127.0, r.totalPrice)
    }

    @Test
    fun elRegaloPideNotaYNoPasaLaCuota() {
        val dos = pirujo.copy(quantity = 2.0) // Q1.00 a precio de venta
        assertNotNull(counterRemovalError(removal(CounterRemovalKind.COURTESY, dos), false, 15.0, 0.0))
        assertNull(counterRemovalError(removal(CounterRemovalKind.COURTESY, dos, note = "señor de la esquina"), false, 15.0, 0.0))
        // Justo en el límite se puede; un centavo más, no.
        assertNull(counterRemovalError(removal(CounterRemovalKind.COURTESY, dos, note = "señor"), false, 15.0, 14.0))
        assertNotNull(counterRemovalError(removal(CounterRemovalKind.COURTESY, dos, note = "señor"), false, 15.0, 14.01))
        // Sin cuota configurada no se regala.
        assertNotNull(counterRemovalError(removal(CounterRemovalKind.COURTESY, dos, note = "señor"), false, 0.0, 0.0))
    }

    @Test
    fun laCuotaSeCuentaPorSucursalYPorDia() {
        val regaloHoy = removal(CounterRemovalKind.COURTESY, pirujo, note = "x").copy(branchId = "b1", createdAt = at(9))
        val regaloAyer = regaloHoy.copy(id = "ayer", createdAt = at(9, day = 13))
        val otraSucursal = regaloHoy.copy(id = "otra", branchId = "b2")
        val merma = removal(CounterRemovalKind.DAMAGED, pastel).copy(branchId = "b1", createdAt = at(10))
        assertEquals(2.0, courtesyUsedOn(listOf(regaloHoy, regaloAyer, otraSucursal, merma), "b1", at(18), tz))
    }

    @Test
    fun nadieSePerdonaASiMisma() {
        val acc = removal(CounterRemovalKind.ACCIDENT, pirujo, responsible = "ana")
        assertNotNull(accidentForgiveBlocker(acc, "ana"))
        assertNull(accidentForgiveBlocker(acc, "lucia"))
        // El dueño con cuenta propia (sin ficha) sí puede.
        assertNull(accidentForgiveBlocker(acc, ""))
        assertNotNull(accidentForgiveBlocker(acc.copy(forgiven = true), "lucia"))
        assertNotNull(accidentForgiveBlocker(removal(CounterRemovalKind.DAMAGED, pirujo), "lucia"))
    }

    @Test
    fun cadaCasoVaASuMovimientoDeInventario() {
        assertEquals(FinishedMovementType.MERMA, CounterRemovalKind.movementType(CounterRemovalKind.DAMAGED))
        assertEquals(FinishedMovementType.ACCIDENTE, CounterRemovalKind.movementType(CounterRemovalKind.ACCIDENT))
        assertEquals(FinishedMovementType.CORTESIA, CounterRemovalKind.movementType(CounterRemovalKind.COURTESY))
    }

    @Test
    fun resumenPorPersona() {
        val mermaAna = removal(CounterRemovalKind.DAMAGED, pirujo).copy(createdByEmployeeId = "ana", createdByUserName = "Ana")
        val accidenteAna = removal(CounterRemovalKind.ACCIDENT, pastel, responsible = "ana")
            .copy(responsibleEmployeeName = "Ana", chargeAmount = 60.0, forgiven = true, createdByEmployeeId = "lucia")
        val regaloLucia = removal(CounterRemovalKind.COURTESY, pirujo, note = "x")
            .copy(createdByEmployeeId = "lucia", createdByUserName = "Lucía")
        val stats = counterRemovalByPerson(listOf(mermaAna, accidenteAna, regaloLucia))
        val ana = stats.first { it.name == "Ana" }
        assertEquals(4.0, ana.damagedUnits)
        assertEquals(1, ana.accidents)
        assertEquals(60.0, ana.accidentForgiven)
        assertEquals(2.0, stats.first { it.name == "Lucía" }.courtesyValue)
        assertEquals(2, stats.size)
    }

    @Test
    fun resumenDelTurno() {
        val lines = counterRemovalSummary(listOf(
            removal(CounterRemovalKind.DAMAGED, pirujo),
            removal(CounterRemovalKind.ACCIDENT, pastel, responsible = "ana").copy(responsibleEmployeeName = "Ana")
        ))
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("Ana"))
    }
}
