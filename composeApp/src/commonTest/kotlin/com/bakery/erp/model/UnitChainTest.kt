package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnitChainTest {

    private fun line(
        requested: Double = 400.0,
        packed: Double = 400.0,
        received: Double? = 400.0,
        packedReason: String? = null,
        planned: Double? = null,
        produced: Double? = null
    ) = ChainLine("frances", "Francés", requested, packed, packedReason, received, null, null, planned, produced)

    @Test
    fun todoCuadra_noHayQuiebre() {
        assertNull(line().firstBreak())
        assertFalse(line().hasDifference)
    }

    @Test
    fun casoRealDeDemocracia_faltoEntreLaSalidaYElConteo() {
        // 400 empacados, contaron 306: no fue la planta.
        val l = line(packed = 400.0, received = 306.0)
        assertEquals(ChainStage.TRANSIT_OR_BRANCH, l.firstBreak())
        assertEquals(-94.0, summarizeChain(listOf(l)).transitOrBranch)
    }

    @Test
    fun empaqueCortoPorqueNoAlcanzo_esDeProduccion() {
        assertEquals(ChainStage.PRODUCTION, line(packed = 360.0, received = 360.0, packedReason = PackingReason.NOT_PRODUCED).firstBreak())
        // Aunque no lo digan: si el turno horneó menos de lo planeado, el empaque no pudo mandar más.
        assertEquals(ChainStage.PRODUCTION, line(packed = 360.0, received = 360.0, packedReason = otherReason("x"), planned = 1200.0, produced = 1160.0).firstBreak())
    }

    @Test
    fun empaqueCortoConHornoCompleto_esDelEmpaque() {
        assertEquals(ChainStage.PACKING, line(packed = 390.0, received = 390.0, packedReason = PackingReason.DAMAGED, planned = 1200.0, produced = 1200.0).firstBreak())
    }

    @Test
    fun traspasoEntreSucursales_noEsPerdida() {
        val dado = line(packed = 380.0, received = 380.0, packedReason = PackingReason.givenTo("Calvario"))
        val recibido = line(packed = 420.0, received = 420.0, packedReason = PackingReason.takenFrom("Democracia"))
        val s = summarizeChain(listOf(dado, recibido))
        assertEquals(0.0, s.total)
        assertEquals(0.0, s.transferred)
        assertNull(dado.firstBreak())
        // Lo viejo, escrito por el traspaso de antes, también se reconoce.
        assertTrue(line(packed = 380.0, packedReason = "20 reasignadas a otra sucursal").isTransfer())
    }

    @Test
    fun sinContarTodavia_noSeInventaPerdidaDeCamino() {
        val l = line(packed = 400.0, received = null)
        assertNull(l.receptionDiff)
        assertEquals(0.0, summarizeChain(listOf(l)).transitOrBranch)
    }

    @Test
    fun motivosCompletos() {
        assertTrue(isCompleteReason(PackingReason.NOT_PRODUCED, PackingReason.WHEN_LESS))
        assertTrue(isCompleteReason(otherReason("se cayó la bandeja"), PackingReason.WHEN_LESS))
        assertTrue(isCompleteReason(PackingReason.takenFrom("Calvario"), emptyList()))
        assertFalse(isCompleteReason("Otro", PackingReason.WHEN_LESS))
        assertFalse(isCompleteReason("Otro:  ", PackingReason.WHEN_LESS))
        assertFalse(isCompleteReason("no se iso", PackingReason.WHEN_LESS))
    }

    @Test
    fun motivosDeRecepcionSegunElTipo() {
        assertEquals(listOf(ReceptionReason.SHORT_IN_BASKET, ReceptionReason.BASKET_MISSING), ReceptionReason.optionsFor(DiscrepancyType.SHORT))
        assertTrue(ReceptionReason.optionsFor(null).isEmpty())
    }

    @Test
    fun lasCanastasSeparanCamionDeSucursal() {
        assertEquals(BasketCheck.MISSING_BASKETS, basketCheck(sent = 6, received = 5))
        assertEquals("Camión", transitStageLabel(basketCheck(6, 5)))
        assertEquals(BasketCheck.ALL_ARRIVED, basketCheck(6, 6))
        assertEquals("Dentro de la canasta", transitStageLabel(basketCheck(6, 6)))
        // Nota vieja sin canastas, o todavía sin firmar: no se afirma nada.
        assertEquals(BasketCheck.UNKNOWN, basketCheck(0, null))
        assertEquals(BasketCheck.UNKNOWN, basketCheck(6, null))
    }
}
