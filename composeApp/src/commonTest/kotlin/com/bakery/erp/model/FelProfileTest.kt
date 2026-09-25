package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FelProfileTest {

    @Test
    fun `NITs reales validan con y sin guion`() {
        assertTrue(isValidGuatemalaNit("59325216"))
        assertTrue(isValidGuatemalaNit("5932521-6"))
        assertTrue(isValidGuatemalaNit("4630087-2"))
        assertTrue(isValidGuatemalaNit(" 32000278 "))
    }

    @Test
    fun `un digito cambiado no valida`() {
        assertFalse(isValidGuatemalaNit("59325217"))
        assertFalse(isValidGuatemalaNit("59352216"))
        assertFalse(isValidGuatemalaNit("CF"))
        assertFalse(isValidGuatemalaNit(""))
    }

    @Test
    fun `digito verificador K`() {
        // Cuerpo "6": 6×2 = 12; 12 % 11 = 1; 11 − 1 = 10 → K.
        assertTrue(isValidGuatemalaNit("6-K"))
        assertTrue(isValidGuatemalaNit("6k"))
        assertFalse(isValidGuatemalaNit("6-1"))
        // Cuerpo "5": 5×2 = 10; 11 − 10 = 1 → el verificador es 1, no K.
        assertTrue(isValidGuatemalaNit("5-1"))
    }

    @Test
    fun `formato de lectura`() {
        assertEquals("5932521-6", formatNit("59325216"))
        assertEquals("6-K", formatNit("6k"))
    }

    @Test
    fun `afiliacion guardada decide el documento`() {
        assertEquals("FPEQ", FelVatAffiliation.fromStored("PEQ").docType)
        assertEquals("FPEQ", FelVatAffiliation.fromStored("pec").docType)
        assertEquals("FACT", FelVatAffiliation.fromStored("GEN").docType)
        assertEquals("FACT", FelVatAffiliation.fromStored(null).docType)
    }
}

class FelAssignmentProblemsTest {
    private val names = mapOf("b" to "Bombero", "p" to "Pacaja", "d" to "Democracia")
    private fun check(vararg a: FelBranchAssignment) = felAssignmentProblems(a.toList()) { names[it] ?: it }

    @Test
    fun `codigos distintos pasan`() {
        assertTrue(check(
            FelBranchAssignment("b", "1", true),
            FelBranchAssignment("p", "2", false),
            FelBranchAssignment("d", " 3 ", true)
        ).isEmpty())
    }

    @Test
    fun `codigo vacio o no numerico`() {
        val p = check(FelBranchAssignment("b", "", true), FelBranchAssignment("p", "A1", false))
        assertEquals(2, p.size)
        assertTrue(p[0].startsWith("Bombero"))
        assertTrue(p[1].startsWith("Pacaja"))
    }

    @Test
    fun `codigo repetido nombra las dos sucursales, aunque uno lleve cero a la izquierda`() {
        val p = check(FelBranchAssignment("b", "1", true), FelBranchAssignment("p", "01", false))
        assertEquals(1, p.size)
        assertTrue("Bombero" in p[0] && "Pacaja" in p[0])
    }
}
