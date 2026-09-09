package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripMismatchTest {

    @Test
    fun `sucursal sin restriccion nunca genera aviso`() {
        assertNull(tripMismatchFor("Democracia", DISPATCH_TRIP_BOTH, 1))
        assertNull(tripMismatchFor("Democracia", DISPATCH_TRIP_BOTH, 2))
    }

    @Test
    fun `nota del viaje que la sucursal si recibe no genera aviso`() {
        assertNull(tripMismatchFor("Pacaja", DISPATCH_TRIP_SECOND, 2))
        assertNull(tripMismatchFor("Otra", DISPATCH_TRIP_FIRST, 1))
    }

    @Test
    fun `el caso de Pacaja - pan del turno noche para quien solo recibe al mediodia`() {
        val m = tripMismatchFor("Pacajá", DISPATCH_TRIP_SECOND, 1)
        assertEquals(TripMismatch("Pacajá", DISPATCH_TRIP_SECOND, 1), m)
        assertEquals(
            "Pacajá solo recibe en el segundo viaje — va en ese, no en éste",
            m!!.runFormLabel()
        )
        assertEquals(
            "Guardar para el segundo viaje — Pacajá no recibe en éste",
            m.packingLabel()
        )
    }

    @Test
    fun `tambien al reves - sucursal que solo recibe temprano con nota del segundo viaje`() {
        val m = tripMismatchFor("Madrugadora", DISPATCH_TRIP_FIRST, 2)
        assertEquals(DISPATCH_TRIP_FIRST, m?.restrictedTrip)
        assertEquals(
            "Madrugadora solo recibe en el primer viaje — va en ese, no en éste",
            m!!.runFormLabel()
        )
    }

    @Test
    fun `turno sin dispatchOrder configurado no afirma nada`() {
        // dispatchOrder = 0 significa "sin configurar", no "viaje cero": sin ese dato
        // no se sabe a qué viaje pertenece la nota y un aviso sería adivinanza.
        assertNull(tripMismatchFor("Pacajá", DISPATCH_TRIP_SECOND, 0))
    }
}
