package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El conteo final reparte lo contado (ver `reconcileCount`). Los casos salen de los
 * lotes reales: los panaderos no anotaban nada en "¿Qué pasó?", corregían el número
 * contando, y el pan de más desaparecía de los libros.
 */
class CountReconciliationTest {

    private fun line(planned: Double, extra: Double = 0.0, waste: Double = 0.0, produced: Double = planned) =
        ProductionLotLine(
            productId = "capa",
            plannedQuantity = planned,
            producedQuantity = produced,
            extraQuantity = extra,
            wasteQuantity = waste
        )

    @Test
    fun contoDeMas_loQuePasaQuedaEnLaPlanta() {
        // 5/10: 388 Pan de Capa pedidos, se contaron 400.
        val r = line(388.0).reconcileCount(400.0)
        assertEquals(388.0, r.produced)
        assertEquals(12.0, r.extra)
        assertEquals(12.0, r.surplus)
        assertEquals(0.0, r.shortfall)
    }

    @Test
    fun contoDeMenos_hayQuePreguntar() {
        // 17/9: 460 pedidos, se contaron 440.
        val r = line(460.0).reconcileCount(440.0)
        assertEquals(440.0, r.produced)
        assertEquals(0.0, r.extra)
        assertEquals(20.0, r.shortfall)
    }

    @Test
    fun loAnotadoDeMas_noEsSobrante() {
        // Se anotaron 50 de más: contar 438 sobre 388 es lo esperado, no un sobrante.
        val r = line(388.0, extra = 50.0).reconcileCount(438.0)
        assertEquals(388.0, r.produced)
        assertEquals(50.0, r.extra)
        assertEquals(0.0, r.surplus)
        assertEquals(0.0, r.shortfall)
    }

    @Test
    fun mermaYaAnotada_noSeVuelveAPreguntar() {
        // 40 quemadas anotadas en "¿Qué pasó?" y no se repusieron: 60 buenas es lo esperable.
        assertEquals(0.0, line(100.0, waste = 40.0).reconcileCount(60.0).shortfall)
        // Si además faltan 10, eso sí se pregunta.
        assertEquals(10.0, line(100.0, waste = 40.0).reconcileCount(50.0).shortfall)
    }

    @Test
    fun loAmasadoSigueCuadrando() {
        // Contó 440 de 460 y dijo que se quemaron 20: se descuenta la masa de 460.
        val l = line(460.0)
        val r = l.reconcileCount(440.0)
        val saved = l.copy(producedQuantity = r.produced, extraQuantity = r.extra, wasteQuantity = r.shortfall)
        assertEquals(460.0, saved.kneadedQuantity(fallbackToPlanned = false))
        // Contó 400 de 388: se descuenta la masa de 400 y 12 van a la planta.
        val l2 = line(388.0)
        val r2 = l2.reconcileCount(400.0)
        val saved2 = l2.copy(producedQuantity = r2.produced, extraQuantity = r2.extra)
        assertEquals(400.0, saved2.kneadedQuantity(fallbackToPlanned = false))
        assertEquals(12.0, saved2.plantIngestQuantity())
    }

    @Test
    fun prellenadoMuestraElPanFisico() {
        assertEquals(438.0, line(388.0, extra = 50.0).physicalGoodCount())
        assertEquals(0.0, line(388.0, produced = 0.0).physicalGoodCount())
    }

    @Test
    fun cierre_pasaElExcedenteAPlanta() {
        val fixed = line(388.0, produced = 400.0).withSurplusAsExtra()
        assertEquals(388.0, fixed.producedQuantity)
        assertEquals(12.0, fixed.extraQuantity)
        assertEquals(ProductionExtraReason.COUNTED_MORE, fixed.extraReason)
        // Sin excedente no toca nada.
        val same = line(388.0, produced = 380.0)
        assertEquals(same, same.withSurplusAsExtra())
    }
}
