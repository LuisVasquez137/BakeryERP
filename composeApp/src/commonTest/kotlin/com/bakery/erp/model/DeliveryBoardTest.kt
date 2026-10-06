package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeliveryBoardTest {

    private fun note(
        dest: String,
        status: String,
        origin: String = "bombero"
    ) = DispatchNote(
        id = "n-$dest-$status",
        originBranchId = origin,
        destinationBranchId = dest,
        destinationBranchName = dest.replaceFirstChar { it.uppercase() },
        status = status
    )

    private val plant = BoardViewer(supervision = false, canManageRuns = true, canReceiveAt = { false })
    private val democracia = BoardViewer(supervision = false, canManageRuns = false, canReceiveAt = { it == "democracia" })
    private val owner = BoardViewer(supervision = true, canManageRuns = true, canReceiveAt = { true })

    @Test
    fun aLaPlantaLeTocaLoListoSinCamion() {
        assertTrue(plant.isMyTurn(note("democracia", DispatchStatus.READY)))
        // La nota de la propia planta no viaja.
        assertFalse(plant.isMyTurn(note("bombero", DispatchStatus.READY)))
        assertFalse(plant.isMyTurn(note("democracia", DispatchStatus.ARRIVED)))
    }

    @Test
    fun aLaSucursalLeTocaContarLoSuyo() {
        assertTrue(democracia.isMyTurn(note("democracia", DispatchStatus.ARRIVED)))
        assertFalse(democracia.isMyTurn(note("calvario", DispatchStatus.ARRIVED)))
        assertFalse(democracia.isMyTurn(note("democracia", DispatchStatus.IN_TRANSIT)))
    }

    @Test
    fun alDuenoNoLeLateNada() {
        assertFalse(owner.isMyTurn(note("democracia", DispatchStatus.READY)))
        assertFalse(owner.isMyTurn(note("democracia", DispatchStatus.ARRIVED)))
    }

    @Test
    fun ordenLoQueTeTocaPrimero() {
        val sorted = sortForBoard(
            listOf(
                note("calvario", DispatchStatus.DELIVERED),
                note("calvario", DispatchStatus.IN_TRANSIT),
                note("democracia", DispatchStatus.ARRIVED),
                note("almolonga", DispatchStatus.DRAFT)
            ),
            democracia
        )
        assertEquals(
            listOf(DispatchStatus.ARRIVED, DispatchStatus.IN_TRANSIT, DispatchStatus.DRAFT, DispatchStatus.DELIVERED),
            sorted.map { it.status }
        )
    }

    @Test
    fun panDeNocheDePacajaSaleEnElSegundoViaje() {
        assertEquals(DISPATCH_TRIP_SECOND, tripOfNote(shiftTrip = 1, destinationOnlyTrip = DISPATCH_TRIP_SECOND))
        assertEquals(1, tripOfNote(shiftTrip = 1, destinationOnlyTrip = DISPATCH_TRIP_BOTH))
    }

    @Test
    fun avanceDeEmpaquePorPanes() {
        assertEquals(0.5, DispatchNote(plannedUnits = 640.0, packedUnits = 320.0).packedFraction())
        assertNull(DispatchNote().packedFraction())
    }

    @Test
    fun paradasEnElOrdenDeLaRuta() {
        val ordered = orderStopsByRoute(
            listOf(note("democracia", "READY"), note("nueva", "READY"), note("calvario", "READY")),
            routeOrder = listOf("calvario", "democracia")
        )
        assertEquals(listOf("calvario", "democracia", "nueva"), ordered.map { it.destinationBranchId })
    }

    @Test
    fun promedioDelTramoPesaLoReciente() {
        assertEquals(40.0 to 1, updatedLegMinutes(null, 40.0))
        val stat = DeliveryLegStat("bombero", "democracia", avgMinutes = 40.0, samples = 4)
        assertEquals(45.0 to 5, updatedLegMinutes(stat, 60.0))
        // Una firma olvidada de 6 horas no entra.
        assertNull(updatedLegMinutes(stat, 360.0))
    }

    @Test
    fun estimadoSoloConHistorialSuficiente() {
        val start = 0L
        val now = 20 * 60_000L
        val reliable = DeliveryLegStat("bombero", "democracia", 40.0, samples = 5)
        val p = stopProgress(2, 3, start, now, reliable)
        assertEquals(0.5, p.fraction)
        assertEquals(20, p.minutesLeft)
        assertEquals("Llegaría en unos 20 min · parada 3 de 3", p.etaLabel())

        // Sin historial: el camión se mueve igual, pero no se promete un minuto.
        val guess = stopProgress(0, 3, start, now, null)
        assertNull(guess.minutesLeft)
        assertEquals("Salió hace 20 min · parada 1 de 3", guess.etaLabel())
        // Nunca llega solo: llega cuando la sucursal firma.
        assertEquals(0.97, guess.fraction)
    }
}
