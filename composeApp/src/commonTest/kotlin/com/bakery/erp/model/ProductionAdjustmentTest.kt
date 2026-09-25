package com.bakery.erp.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ajustes de un lote ya andando: pan perdido y pan que hay que hacer de más
 * (ver `ProductionAdjustment.kt`).
 *
 * Lo que se verifica son las tres cantidades que el modelo tenía colapsadas en una —
 * lo amasado, lo bueno y lo que entra a la planta— porque cada confusión entre ellas
 * falla en silencio: o falta harina en el inventario, o el pan se hornea y no entra a
 * ningún libro.
 */
class ProductionAdjustmentTest {

    private fun line(
        planned: Double = 100.0,
        produced: Double = 0.0,
        waste: Double = 0.0,
        extra: Double = 0.0,
        retained: Double = 0.0,
        wasteReason: String = "",
        extraReason: String = ""
    ) = ProductionLotLine(
        id = "l1",
        productId = "p1",
        productName = "Pan de capa",
        recipeName = "Hojaldre",
        plannedQuantity = planned,
        producedQuantity = produced,
        wasteQuantity = waste,
        wasteReason = wasteReason,
        extraQuantity = extra,
        extraReason = extraReason,
        plantRetainedQuantity = retained
    )

    private fun assertClose(expected: Double, actual: Double) =
        assertTrue(abs(expected - actual) < 0.001, "esperaba $expected, obtuve $actual")

    // ── Lo amasado: la base del consumo de insumos ───────────────────────────

    @Test
    fun `sin ajustes lo amasado es lo bueno, como siempre`() {
        assertClose(100.0, line(produced = 100.0).kneadedQuantity())
    }

    @Test
    fun `se quemaron 40 de 100 y se repusieron - se amaso para 140`() {
        // El caso que originó todo: el panadero cuenta 100 buenos al final, pero la
        // harina que se gastó fue para 140. Sin esto faltaban 40 en el inventario y
        // aparecían después en el conteo físico sin nada que lo explicara.
        val l = line(produced = 100.0, waste = 40.0)
        assertClose(140.0, l.kneadedQuantity())
        assertClose(100.0, l.effectiveProducedQuantity())
    }

    @Test
    fun `se quemaron 40 y NO se repusieron - se amaso 100 y salieron 60`() {
        // La diferencia entre reponer y no reponer la lleva el conteo final, no un
        // campo aparte: el panadero cuenta lo bueno y la merma ya está contada.
        val l = line(produced = 60.0, waste = 40.0)
        assertClose(100.0, l.kneadedQuantity())
        assertClose(60.0, l.effectiveProducedQuantity())
    }

    @Test
    fun `encargo de 100 a media manana - se amaso 300`() {
        val l = line(planned = 200.0, produced = 200.0, extra = 100.0)
        assertClose(300.0, l.kneadedQuantity())
    }

    @Test
    fun `antes del conteo se usa lo planeado, para que la receta escalada no de cero`() {
        // La lista del panadero se mira ANTES de que exista un conteo. Sin el fallback
        // mostraría cero masa hasta el final del turno, que es cuando ya no sirve.
        assertClose(100.0, line(planned = 100.0, produced = 0.0).kneadedQuantity())
        assertClose(130.0, line(planned = 100.0, produced = 0.0, extra = 30.0).kneadedQuantity())
    }

    @Test
    fun `al cerrar el lote no hay fallback a lo planeado`() {
        // `completeLot` exige conteo confirmado, así que un produced en cero ahí
        // significa "no se produjo", no "todavía no se contó". Descontar lo planeado
        // sacaría insumos de pan que nunca se hizo.
        assertClose(0.0, line(planned = 100.0, produced = 0.0).kneadedQuantity(fallbackToPlanned = false))
        assertClose(40.0, line(planned = 100.0, produced = 0.0, waste = 40.0)
            .kneadedQuantity(fallbackToPlanned = false))
    }

    // ── Lo que hay que amasar: el extra sí, la merma no ──────────────────────

    @Test
    fun `lo que hay que amasar incluye el extra`() {
        // Todavía no se hizo: tiene que aparecer en la lista del panadero.
        assertClose(300.0, line(planned = 200.0, produced = 200.0, extra = 100.0).quantityToKnead())
    }

    @Test
    fun `lo que hay que amasar EXCLUYE lo perdido`() {
        // Lo quemado ya se amasó una vez. Sumarlo mostraría "140" — una amasada que
        // nunca se hace de un solo golpe, y el panadero pesaría de más.
        val l = line(produced = 100.0, waste = 40.0)
        assertClose(100.0, l.quantityToKnead())
        // …pero el consumo sí la cuenta: son dos preguntas distintas.
        assertClose(140.0, l.kneadedQuantity())
    }

    // ── Lo que entra al stock de la planta ───────────────────────────────────

    @Test
    fun `el pan de mas entra al inventario de la planta`() {
        // Sin esto se horneaba, consumía insumos y desaparecía de los libros: las
        // notas de despacho salen de los pedidos, y ese pan no está en ninguno.
        assertClose(100.0, line(produced = 200.0, extra = 100.0).plantIngestQuantity())
    }

    @Test
    fun `el excedente del minimo de amasada y el pan de mas se suman en un solo ingreso`() {
        // Dos razones, un solo movimiento de Kardex: dos llamadas seguidas a
        // applyMovement sobre el mismo doc se pisan (lee-modifica-escribe).
        val l = line(produced = 200.0, extra = 100.0, retained = 30.0)
        assertClose(130.0, l.plantIngestQuantity())
        assertTrue("mínimo de amasada" in l.plantIngestReason(), l.plantIngestReason())
        assertTrue("extra" in l.plantIngestReason(), l.plantIngestReason())
    }

    @Test
    fun `la merma NO entra al inventario de la planta`() {
        // Ese pan no existe: se quemó. Ingresarlo inventaría stock.
        assertClose(0.0, line(produced = 60.0, waste = 40.0).plantIngestQuantity())
    }

    @Test
    fun `la razon del ingreso dice cual de las dos fue`() {
        val onlyExtra = line(produced = 200.0, extra = 100.0, extraReason = ProductionExtraReason.CUSTOMER_ORDER)
        assertTrue("Encargo de un cliente" in onlyExtra.plantIngestReason(), onlyExtra.plantIngestReason())
        val onlyRetained = line(produced = 200.0, retained = 30.0)
        assertTrue("mínimo de amasada" in onlyRetained.plantIngestReason(), onlyRetained.plantIngestReason())
    }

    // ── Productos de DOS masas (Pan de Capa) ────────────────────────────────

    @Test
    fun `un pan de dos masas consume las DOS cuando se quema`() {
        // El Pan de Capa es masa de Capa por fuera con una bolita de Suave adentro.
        // El que se quemó ya estaba formado y relleno: las dos masas se gastaron.
        // Éste es el caso más fácil de dejar mal, porque solo una de las dos sale
        // de `calculateLineConsumption` — la otra se calcula en su propio recorrido.
        val l = line(produced = 100.0, waste = 40.0).copy(
            doughFillingsSnapshot = listOf(
                DoughFilling(recipeId = "suave", recipeName = "Suave", rawWeightGrams = 25.0)
            )
        )
        val kneaded = l.kneadedQuantity(fallbackToPlanned = false)
        assertClose(140.0, kneaded)
        // La bolita de suave sale de esas mismas 140 piezas, no de las 100 buenas.
        assertClose(3500.0, kneaded * l.doughFillingsSnapshot.first().rawWeightGrams)
    }

    @Test
    fun `el pan de mas tambien arrastra su relleno`() {
        val l = line(planned = 200.0, produced = 200.0, extra = 100.0).copy(
            doughFillingsSnapshot = listOf(
                DoughFilling(recipeId = "suave", recipeName = "Suave", rawWeightGrams = 25.0)
            )
        )
        assertClose(300.0, l.kneadedQuantity(fallbackToPlanned = false))
        // Y en la lista del panadero también: la masa de Suave tiene que crecer, si no
        // se hornean 300 capas con bolitas para 200.
        assertClose(300.0, l.quantityToKnead())
    }

    // ── Resumen de merma para el reporte ─────────────────────────────────────

    @Test
    fun `el porcentaje de merma se mide sobre lo amasado, no sobre lo bueno`() {
        // De cada 100 que metí al horno, cuántas perdí. Dividir entre lo bueno da más
        // de 100% cuando se pierde más de la mitad, que es cuando más importa leerlo.
        val summary = summarizeProductionWaste(listOf(line(produced = 60.0, waste = 40.0))).single()
        assertClose(40.0, summary.wastePct)
        assertClose(40.0, summary.wasteUnits)
        assertClose(60.0, summary.goodUnits)
    }

    @Test
    fun `las lineas sin merma no aparecen en el reporte`() {
        val lines = listOf(
            line(produced = 100.0),
            line(produced = 60.0, waste = 40.0, wasteReason = ProductionWasteReason.BURNED)
        )
        assertEquals(1, summarizeProductionWaste(lines).size)
    }

    @Test
    fun `dos tandas quemadas del mismo producto se suman con sus motivos`() {
        val lines = listOf(
            line(produced = 60.0, waste = 40.0, wasteReason = ProductionWasteReason.BURNED),
            line(produced = 0.0, waste = 10.0, wasteReason = ProductionWasteReason.DROPPED)
        )
        val summary = summarizeProductionWaste(lines).single()
        assertClose(50.0, summary.wasteUnits)
        assertClose(40.0, summary.byReason.getValue(ProductionWasteReason.BURNED))
        assertClose(10.0, summary.byReason.getValue(ProductionWasteReason.DROPPED))
    }

    @Test
    fun `un motivo guardado que ya no esta en la lista se muestra tal cual`() {
        // El reporte viejo debe seguir diciendo qué pasó. Mismo criterio que
        // PlantIssueReason.
        assertEquals("MOTIVO_VIEJO", ProductionWasteReason.label("MOTIVO_VIEJO"))
        assertEquals("Otro", ProductionWasteReason.label(""))
        assertEquals("Otro", ProductionExtraReason.label(""))
    }

    @Test
    fun `sin nada amasado el porcentaje es cero y no divide entre cero`() {
        val summary = ProductionWasteSummary("p", "Pan", "Masa", 0.0, 0.0, emptyMap())
        assertClose(0.0, summary.wastePct)
    }
}
