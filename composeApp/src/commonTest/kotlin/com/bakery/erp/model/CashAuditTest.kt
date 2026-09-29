package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CashAuditTest {

    private val caja1 = listOf("14:00", "21:00")

    @Test
    fun `dentro de la tolerancia de algun horario`() {
        assertTrue(isWithinCloseSchedule(14 * 60, caja1, 30))
        assertTrue(isWithinCloseSchedule(13 * 60 + 30, caja1, 30))
        assertTrue(isWithinCloseSchedule(21 * 60 + 30, caja1, 30))
    }

    @Test
    fun `fuera de horario`() {
        assertFalse(isWithinCloseSchedule(11 * 60, caja1, 30))
        assertFalse(isWithinCloseSchedule(13 * 60 + 29, caja1, 30))
        assertFalse(isWithinCloseSchedule(17 * 60, caja1, 30))
    }

    @Test
    fun `la ventana cruza la medianoche`() {
        assertTrue(isWithinCloseSchedule(10, listOf("23:50"), 30))
        assertFalse(isWithinCloseSchedule(30, listOf("23:50"), 30))
    }

    @Test
    fun `sin horarios configurados no se marca nada`() {
        assertTrue(isWithinCloseSchedule(3 * 60, emptyList(), 30))
        assertTrue(isWithinCloseSchedule(3 * 60, listOf("basura"), 30))
    }

    @Test
    fun `horas y etiqueta`() {
        assertEquals(840, parseScheduleMinutes("14:00"))
        assertNull(parseScheduleMinutes("25:00"))
        assertEquals("2:00 PM, 9:00 PM (±30 min)", closeScheduleLabel(listOf("21:00", "14:00"), 30))
        assertEquals("12:15 AM (±15 min)", closeScheduleLabel(listOf("00:15"), 15))
        assertEquals("", closeScheduleLabel(emptyList(), 30))
    }
}
