package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OrderAdjustmentTest {

    private fun target(
        lotStatus: String = ProductionLotStatus.IN_PROGRESS,
        produces: Boolean = true,
        counted: Boolean = false,
        current: Double = 20.0,
        noteStatus: String? = DispatchStatus.DRAFT,
        checked: Boolean = false
    ) = OrderAdjustmentTarget(lotStatus, produces, counted, current, noteStatus, checked)

    @Test
    fun ajusteNormal_pasa() {
        assertNull(orderAdjustmentProblem(target(), 10.0))
        assertNull(orderAdjustmentProblem(target(), -20.0))
    }

    @Test
    fun turnoQueNoHaceElPan_noRecibeMas() {
        // El error del reparto (Cubo pequeño en la mañana) no puede volver por acá.
        assertNotNull(orderAdjustmentProblem(target(produces = false), 5.0))
    }

    @Test
    fun masaYaContada_bloquea() {
        assertNotNull(orderAdjustmentProblem(target(counted = true), 5.0))
        assertNotNull(orderAdjustmentProblem(target(counted = true), -5.0))
    }

    @Test
    fun noSeQuitaMasDeLoQueTiene() {
        assertNotNull(orderAdjustmentProblem(target(current = 20.0), -21.0))
    }

    @Test
    fun notaYaEmpacadaOCerrada_bloquea() {
        assertNotNull(orderAdjustmentProblem(target(noteStatus = DispatchStatus.READY), 5.0))
        assertNotNull(orderAdjustmentProblem(target(checked = true), 5.0))
        // Turno abierto: todavía no hay nota, y eso no es un problema.
        assertNull(orderAdjustmentProblem(target(lotStatus = ProductionLotStatus.OPEN, noteStatus = null), 5.0))
    }

    @Test
    fun loteTerminado_bloquea() {
        assertNotNull(orderAdjustmentProblem(target(lotStatus = ProductionLotStatus.COMPLETED), 5.0))
    }

    @Test
    fun soloElTurnoEnProduccionPideVerificacion() {
        assertEquals(true, orderAdjustmentNeedsVerification(ProductionLotStatus.IN_PROGRESS))
        assertEquals(false, orderAdjustmentNeedsVerification(ProductionLotStatus.OPEN))
    }

    @Test
    fun desglose_soloCambiaLaSucursalAjustada() {
        val allocs = listOf(
            SourceOrderAllocation("dem", "Democracia", 50.0),
            SourceOrderAllocation("cal", "Calvario", 30.0)
        )
        assertEquals(
            listOf(SourceOrderAllocation("dem", "Democracia", 30.0), SourceOrderAllocation("cal", "Calvario", 30.0)),
            allocs.adjustedFor("dem", "Democracia", -20.0)
        )
        // Queda en cero → sale del desglose.
        assertEquals(listOf(SourceOrderAllocation("cal", "Calvario", 30.0)), allocs.adjustedFor("dem", "Democracia", -50.0))
        // No estaba en este turno → entra.
        assertEquals(
            allocs + SourceOrderAllocation("pac", "Pacajá", 10.0),
            allocs.adjustedFor("pac", "Pacajá", 10.0)
        )
    }

    @Test
    fun encargoDelPrimerViaje_acompanaAlAjusteDeEseTurno() {
        val line = ProductionOrderLine(productId = "p", requestedQuantity = 100.0, earlyShiftQuantity = 60.0)
        val first = line.adjustedBy(-20.0, isFirstTripShift = true)
        assertEquals(80.0, first.requestedQuantity)
        assertEquals(40.0, first.earlyShiftQuantity)
        val second = line.adjustedBy(-20.0, isFirstTripShift = false)
        assertEquals(80.0, second.requestedQuantity)
        assertEquals(60.0, second.earlyShiftQuantity)
        // Sin encargo declarado no se inventa uno.
        assertNull(ProductionOrderLine(productId = "p", requestedQuantity = 10.0).adjustedBy(5.0, true).earlyShiftQuantity)
    }

    @Test
    fun ajuste_conservaElRepartoParaElRecalculo() {
        // Después del ajuste, turno A + turno B tiene que seguir dando lo pedido: si no,
        // la próxima edición de cualquier pedido recalcularía y movería el pan.
        val previousA = mapOf("dem" to 50.0)
        val previousB = mapOf("dem" to 50.0)
        val adjustedA = listOf(SourceOrderAllocation("dem", "Democracia", 50.0))
            .adjustedFor("dem", "Democracia", -20.0).associate { it.orderId to it.quantity }
        val requestedNow = mapOf("dem" to 80.0)
        val (a, b) = splitOrderAllocations(requestedNow, adjustedA, previousB, shiftTotalA = 30.0)
        assertEquals(30.0, a["dem"])
        assertEquals(50.0, b["dem"])
        assertEquals(100.0, previousA.values.sum() + previousB.values.sum())
    }

    @Test
    fun etiquetaParaElPanadero() {
        assertEquals("+20 Democracia (1:20 AM)", orderAdjustmentLabel(20.0, "Democracia", "1:20 AM"))
        assertEquals("−5 Pacajá (11:05 PM)", orderAdjustmentLabel(-5.0, "Pacajá", "11:05 PM"))
    }
}
