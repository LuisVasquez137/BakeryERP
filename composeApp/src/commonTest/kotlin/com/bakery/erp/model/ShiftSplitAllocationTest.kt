package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El desglose por sucursal es lo que arma la nota de despacho de cada turno.
 * Lo que se prueba acá es la propiedad que hacía falla el conteo en despacho:
 * **turno A + turno B tiene que dar exactamente lo que pidió cada sucursal**,
 * pase lo que pase con las ediciones de pedidos.
 */
class ShiftSplitAllocationTest {

    private fun assertConserves(
        requested: Map<String, Double>,
        result: Pair<Map<String, Double>, Map<String, Double>>
    ) {
        val (a, b) = result
        requested.forEach { (orderId, qty) ->
            assertEquals(
                qty, (a[orderId] ?: 0.0) + (b[orderId] ?: 0.0),
                "El pedido $orderId no se conserva entre los dos turnos"
            )
        }
    }

    @Test
    fun repartoInicial_sinHistoria_esProporcionalYConserva() {
        val requested = mapOf("suc-a" to 200.0, "suc-b" to 100.0)
        val result = splitOrderAllocations(requested, emptyMap(), emptyMap(), shiftTotalA = 150.0)

        assertConserves(requested, result)
        assertEquals(150.0, result.first.values.sum())
        assertEquals(100.0, result.first["suc-a"])
        assertEquals(50.0, result.first["suc-b"])
    }

    @Test
    fun sucursalConEncargo_noSeLeMueveCuandoOtraEditaSuPedido() {
        // Sucursal A tiene un encargo: de sus 200 le dieron 125 en la mañana.
        // Sucursal B edita su propio pedido (100 → 160). El encargo de A no se toca.
        val previousA = mapOf("suc-a" to 125.0, "suc-b" to 50.0)
        val previousB = mapOf("suc-a" to 75.0, "suc-b" to 50.0)
        val requested = mapOf("suc-a" to 200.0, "suc-b" to 160.0)

        val result = splitOrderAllocations(requested, previousA, previousB, shiftTotalA = 205.0)

        assertConserves(requested, result)
        assertEquals(125.0, result.first["suc-a"], "El encargo de la mañana se movió")
        assertEquals(75.0, result.second["suc-a"])
        assertEquals(80.0, result.first["suc-b"])
        assertEquals(205.0, result.first.values.sum())
    }

    @Test
    fun pedidoNuevoQueSeSuma_recibeSuParteSinTocarLosDemas() {
        val previousA = mapOf("suc-a" to 100.0)
        val previousB = mapOf("suc-a" to 100.0)
        val requested = mapOf("suc-a" to 200.0, "suc-nueva" to 60.0)

        val result = splitOrderAllocations(requested, previousA, previousB, shiftTotalA = 130.0)

        assertConserves(requested, result)
        assertEquals(100.0, result.first["suc-a"])
        assertEquals(30.0, result.first["suc-nueva"])
    }

    @Test
    fun pedidoCancelado_desapareceDeAmbosTurnos() {
        val previousA = mapOf("suc-a" to 100.0, "suc-b" to 50.0)
        val previousB = mapOf("suc-a" to 100.0, "suc-b" to 50.0)
        val requested = mapOf("suc-a" to 200.0) // suc-b canceló

        val result = splitOrderAllocations(requested, previousA, previousB, shiftTotalA = 100.0)

        assertConserves(requested, result)
        assertTrue("suc-b" !in result.first)
        assertTrue("suc-b" !in result.second)
    }

    @Test
    fun turnoAAbsorbeTodo_dejaElOtroTurnoVacio() {
        val requested = mapOf("suc-a" to 200.0, "suc-b" to 100.0)
        val result = splitOrderAllocations(requested, emptyMap(), emptyMap(), shiftTotalA = 300.0)

        assertConserves(requested, result)
        assertEquals(300.0, result.first.values.sum())
        assertTrue(result.second.isEmpty())
    }

    @Test
    fun turnoAEnCero_mandaTodoAlOtroTurno() {
        val requested = mapOf("suc-a" to 200.0, "suc-b" to 100.0)
        val result = splitOrderAllocations(requested, emptyMap(), emptyMap(), shiftTotalA = 0.0)

        assertConserves(requested, result)
        assertTrue(result.first.isEmpty())
        assertEquals(300.0, result.second.values.sum())
    }

    @Test
    fun fijadoQueYaNoCabe_cedeAnteLoQueRealmenteSeHornea() {
        // Le habían dado 180 de 200 a suc-a en la mañana, pero ahora el turno A
        // solo hornea 100 en total: no se puede respetar el fijado.
        val previousA = mapOf("suc-a" to 180.0)
        val previousB = mapOf("suc-a" to 20.0)
        val requested = mapOf("suc-a" to 200.0, "suc-b" to 100.0)

        val result = splitOrderAllocations(requested, previousA, previousB, shiftTotalA = 100.0)

        assertConserves(requested, result)
        assertEquals(100.0, result.first.values.sum(), "El turno A despacha más de lo que hornea")
    }

    @Test
    fun cantidadesImpares_nuncaPierdenNiInventanUnidades() {
        val requested = mapOf("suc-a" to 33.0, "suc-b" to 33.0, "suc-c" to 34.0)
        val result = splitOrderAllocations(requested, emptyMap(), emptyMap(), shiftTotalA = 50.0)

        assertConserves(requested, result)
        assertEquals(50.0, result.first.values.sum())
        assertEquals(50.0, result.second.values.sum())
    }

    @Test
    fun sinPedidos_noProduceDesglose() {
        val result = splitOrderAllocations(emptyMap(), emptyMap(), emptyMap(), shiftTotalA = 100.0)
        assertTrue(result.first.isEmpty() && result.second.isEmpty())
    }

    // ── Fijar una sucursal no le mueve el número a las demás ──────────────────

    @Test
    fun fijarUnaSucursal_noTocaALasDemas() {
        // Caso real: Democracia pidió 200 franceses y Almolonga 100. El reparto por
        // peso dio 150 al primer viaje (100 y 50). La encargada le sube a Democracia
        // a 150 en la mañana: Almolonga tiene que quedarse en 50.
        val requested = mapOf("democracia" to 200.0, "almolonga" to 100.0)
        val current = mapOf("democracia" to 100.0, "almolonga" to 50.0)

        val result = pinOrderAllocation(requested, current, "democracia", 150.0)

        assertEquals(150.0, result["democracia"])
        assertEquals(50.0, result["almolonga"], "Le cambió el número a la otra sucursal")
    }

    @Test
    fun fijarUnaSucursal_subeElTotalDelTurno() {
        val requested = mapOf("democracia" to 200.0, "almolonga" to 100.0)
        val current = mapOf("democracia" to 100.0, "almolonga" to 50.0)

        val result = pinOrderAllocation(requested, current, "democracia", 150.0)

        // El turno de la mañana hornea más, en vez de recortarle a Almolonga.
        assertEquals(200.0, result.values.sum())
    }

    @Test
    fun fijarMasDeLoPedido_seAcotaALoPedido() {
        val requested = mapOf("democracia" to 200.0)
        val result = pinOrderAllocation(requested, mapOf("democracia" to 100.0), "democracia", 999.0)
        assertEquals(200.0, result["democracia"])
    }

    @Test
    fun fijarSucursalQueNoPidio_noInventaLinea() {
        val requested = mapOf("democracia" to 200.0)
        val current = mapOf("democracia" to 100.0)
        val result = pinOrderAllocation(requested, current, "salcaja", 50.0)
        assertEquals(current, result)
    }

    // ── Reparto por defecto: mitad y mitad por sucursal ───────────────────────

    @Test
    fun sinEncargo_cadaSucursalVaMitadYMitad() {
        val requested = mapOf("democracia" to 200.0, "almolonga" to 100.0)
        val result = defaultOrderAllocationsForShiftA(requested, emptyMap(), true, true)

        assertEquals(100.0, result["democracia"])
        assertEquals(50.0, result["almolonga"])
    }

    @Test
    fun encargoDeUnaSucursal_noMueveALasQueNoEspecificaron() {
        // El caso que se probó en Bombero: Calvario pide 100 y declara 60 temprano;
        // Democracia pide 200 sin declarar nada y tiene que quedar 100/100 — no 90/110,
        // que era lo que salía cuando el peso de masa mandaba sobre el reparto.
        val requested = mapOf("calvario" to 100.0, "democracia" to 200.0)
        val pinned = mapOf("calvario" to 60.0)

        val result = defaultOrderAllocationsForShiftA(requested, pinned, true, true)

        assertEquals(60.0, result["calvario"])
        assertEquals(100.0, result["democracia"], "Se le movió el número a quien no pidió nada")
        // El turno de la mañana hornea 160 y el de la tarde 140: el total se deriva.
        assertEquals(160.0, result.values.sum())
    }

    @Test
    fun productoExcluidoDeUnTurno_vaEnteroAlOtro() {
        val requested = mapOf("democracia" to 200.0, "almolonga" to 100.0)

        val soloB = defaultOrderAllocationsForShiftA(requested, emptyMap(), allowedInA = false, allowedInB = true)
        assertEquals(0.0, soloB.values.sum())

        val soloA = defaultOrderAllocationsForShiftA(requested, emptyMap(), allowedInA = true, allowedInB = false)
        assertEquals(300.0, soloA.values.sum())
    }

    @Test
    fun exclusionMandaSobreElEncargo() {
        // La sucursal pidió 60 temprano pero ese turno no hace el producto: no hay
        // forma de cumplirlo, va entero al otro turno.
        val requested = mapOf("calvario" to 100.0)
        val pinned = mapOf("calvario" to 60.0)

        val result = defaultOrderAllocationsForShiftA(requested, pinned, allowedInA = false, allowedInB = true)

        assertEquals(0.0, result["calvario"])
    }

    @Test
    fun cantidadImpar_noPierdeUnidades() {
        val requested = mapOf("democracia" to 33.0)
        val result = defaultOrderAllocationsForShiftA(requested, emptyMap(), true, true)
        val a = result.getValue("democracia")
        assertEquals(33.0, a + (33.0 - a), "Los dos viajes no suman lo pedido")
        assertTrue(a == 16.0 || a == 17.0, "Reparto impar inesperado: $a")
    }

    @Test
    fun encargoMayorALoPedido_seAcota() {
        val requested = mapOf("calvario" to 100.0)
        val pinned = mapOf("calvario" to 500.0)
        val result = defaultOrderAllocationsForShiftA(requested, pinned, true, true)
        assertEquals(100.0, result["calvario"])
    }

    @Test
    fun encargoDeclarado_seRespetaAunqueElRestoCaigaAProporcional() {
        // El caso real de Bombero: Calvario pidió 100 y declaró 60 temprano. Al pasar
        // por el mínimo de amasada el reparto se recalculaba proporcional y a Calvario
        // le quedaban 53. El encargo es un compromiso con un cliente: no se diluye.
        val requested = mapOf("calvario" to 100.0, "democracia" to 200.0)
        val declared = mapOf("calvario" to 60.0)

        val (a, b) = splitOrderAllocations(
            requestedNow = requested,
            previousA = emptyMap(),
            previousB = emptyMap(),
            shiftTotalA = 160.0,
            declaredEarly = declared
        )

        assertConserves(requested, a to b)
        assertEquals(60.0, a["calvario"], "El encargo declarado se diluyó")
        assertEquals(100.0, a["democracia"])
    }

    @Test
    fun encargosQueNoCabenEnElTurno_subenElTotal() {
        // Los encargos comprometen 180, aunque el turno tenía asignados 100: se hornea
        // más, nunca se incumple lo declarado.
        val requested = mapOf("calvario" to 100.0, "democracia" to 200.0)
        val declared = mapOf("calvario" to 80.0, "democracia" to 100.0)

        val (a, b) = splitOrderAllocations(
            requestedNow = requested,
            previousA = emptyMap(),
            previousB = emptyMap(),
            shiftTotalA = 100.0,
            declaredEarly = declared
        )

        assertConserves(requested, a to b)
        assertEquals(80.0, a["calvario"])
        assertEquals(100.0, a["democracia"])
    }

    @Test
    fun encargoDeclarado_sobreviveAMoverElTotalDelTurno() {
        // Democracia declaró 150 para el primer viaje. Después la encargada mueve el
        // total del turno a 180: el encargo se respeta y solo se acomoda Almolonga.
        val requested = mapOf("democracia" to 200.0, "almolonga" to 100.0)
        val pinned = mapOf("democracia" to 150.0)

        val result = rebalanceOrderAllocation(requested, shiftTotalA = 180.0, pinnedValues = pinned)

        assertEquals(150.0, result["democracia"])
        assertEquals(30.0, result["almolonga"])
    }

    // ── Sucursales que solo reciben en un viaje ───────────────────────────────

    @Test
    fun sucursalDeSegundoViaje_noRecibeNadaEnElPrimero() {
        // Almolonga abre tarde: no hay quién reciba el pan del primer viaje.
        val requested = mapOf("democracia" to 200.0, "almolonga" to 100.0)

        val result = defaultOrderAllocationsForShiftA(
            orderRequested = requested,
            pinned = emptyMap(),
            allowedInA = true,
            allowedInB = true,
            restrictedTrip = mapOf("almolonga" to DISPATCH_TRIP_SECOND)
        )

        assertEquals(0.0, result["almolonga"])
        // Las demás se siguen repartiendo mitad y mitad de lo suyo.
        assertEquals(100.0, result["democracia"])
    }

    @Test
    fun sucursalDePrimerViaje_recibeTodoTemprano() {
        val requested = mapOf("democracia" to 200.0, "calvario" to 60.0)

        val result = defaultOrderAllocationsForShiftA(
            orderRequested = requested,
            pinned = emptyMap(),
            allowedInA = true,
            allowedInB = true,
            restrictedTrip = mapOf("calvario" to DISPATCH_TRIP_FIRST)
        )

        assertEquals(60.0, result["calvario"])
        assertEquals(100.0, result["democracia"])
    }

    @Test
    fun restriccionDeViaje_leGanaAlEncargoDeclarado() {
        // Un encargo para el primer viaje de una sucursal que solo recibe en el
        // segundo no es un encargo: es un error de captura.
        val requested = mapOf("almolonga" to 100.0)

        val result = defaultOrderAllocationsForShiftA(
            orderRequested = requested,
            pinned = mapOf("almolonga" to 40.0),
            allowedInA = true,
            allowedInB = true,
            restrictedTrip = mapOf("almolonga" to DISPATCH_TRIP_SECOND)
        )

        assertEquals(0.0, result["almolonga"])
    }

    @Test
    fun exclusionDelProducto_leGanaALaRestriccionDeViaje() {
        // El pan solo se hornea en el turno A: aunque la sucursal solo reciba en el
        // segundo viaje, no hay forma de producirlo ahí. Se avisa en vez de perderlo.
        val requested = mapOf("almolonga" to 100.0)
        val restricted = mapOf("almolonga" to DISPATCH_TRIP_SECOND)

        val result = defaultOrderAllocationsForShiftA(
            orderRequested = requested,
            pinned = emptyMap(),
            allowedInA = true,
            allowedInB = false,
            restrictedTrip = restricted
        )

        assertEquals(100.0, result["almolonga"])
        assertEquals(
            listOf("almolonga"),
            tripRestrictionConflicts(requested, restricted, allowedInA = true, allowedInB = false)
        )
    }

    @Test
    fun sinConflicto_noHayAviso() {
        val requested = mapOf("almolonga" to 100.0, "democracia" to 50.0)
        assertTrue(
            tripRestrictionConflicts(
                requested,
                mapOf("almolonga" to DISPATCH_TRIP_SECOND),
                allowedInA = true,
                allowedInB = true
            ).isEmpty()
        )
    }

    @Test
    fun editarOtroPedido_noLeMandaPanDelPrimerViajeALaSucursalRestringida() {
        // Democracia edita su pedido; Almolonga (solo segundo viaje) no se mueve, y
        // los dos turnos siguen sumando exactamente lo pedido por cada una.
        val requested = mapOf("democracia" to 260.0, "almolonga" to 100.0)
        val previousA = mapOf("democracia" to 100.0)
        val previousB = mapOf("democracia" to 100.0, "almolonga" to 100.0)

        val result = splitOrderAllocations(
            requestedNow = requested,
            previousA = previousA,
            previousB = previousB,
            shiftTotalA = 130.0,
            restrictedTrip = mapOf("almolonga" to DISPATCH_TRIP_SECOND)
        )

        assertConserves(requested, result)
        val (a, b) = result
        assertEquals(null, a["almolonga"])
        assertEquals(100.0, b["almolonga"])
    }

    @Test
    fun sucursalDePrimerViaje_subeElTotalDelTurnoEnLugarDeQuitarleALasDemas() {
        // Calvario cierra temprano: sus 60 van completas en el primer viaje aunque
        // el turno tenía asignadas menos unidades. Se hornea más, no se le recorta
        // a Democracia.
        val requested = mapOf("democracia" to 200.0, "calvario" to 60.0)

        val result = splitOrderAllocations(
            requestedNow = requested,
            previousA = emptyMap(),
            previousB = emptyMap(),
            shiftTotalA = 30.0,
            restrictedTrip = mapOf("calvario" to DISPATCH_TRIP_FIRST)
        )

        assertConserves(requested, result)
        assertEquals(60.0, result.first["calvario"])
    }
}
