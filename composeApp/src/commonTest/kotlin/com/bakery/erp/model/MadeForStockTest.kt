package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pan molde y rosca: se hacen por adelantado, se guardan en la planta y la sucursal los
 * pide por traslado. Lo que se prueba es quién los guarda, cuándo toca hacer y que el
 * lote para existencia ingrese lo contado.
 */
class MadeForStockTest {

    private val molde = Product(id = "molde", name = "Pan molde", isMadeForStock = true, plantStockMinimum = 20.0)
    private val rosca = Product(id = "rosca", name = "Rosca de panela", isMadeForStock = true)
    private val pirujo = Product(id = "pirujo", name = "Pirujo")
    private val porcion = Product(id = "p", name = "Porción", portionOfProductId = "x")

    private val bombero = Branch(
        id = "bom", name = "Bombero",
        capabilities = listOf(BranchCapability.CAN_PRODUCE),
        assignedProductIds = listOf("molde", "pirujo")
    )
    private val calvario = Branch(
        id = "cal", name = "Calvario",
        capabilities = listOf(BranchCapability.CAN_PRODUCE),
        assignedProductIds = listOf("rosca")
    )
    private val tienda = Branch(
        id = "t", name = "Democracia",
        capabilities = listOf(BranchCapability.CAN_SELL),
        assignedProductIds = listOf("molde")
    )

    @Test
    fun soloLasPlantasQueLoTienenAsignadoLoGuardan() {
        assertEquals(listOf("bom"), keeperPlantsOf(molde, listOf(bombero, calvario, tienda)).map { it.id })
    }

    @Test
    fun niLaPorcionNiLoDeExistenciaSePidenAPlanta() {
        assertTrue(pirujo.isOrderedFromPlant)
        assertFalse(molde.isOrderedFromPlant)
        assertFalse(porcion.isOrderedFromPlant)
    }

    @Test
    fun laListaDeLaPlantaTraeSoloSusProductosParaExistencia() {
        val list = madeForStockProductsOf(bombero, listOf(molde, rosca, pirujo)) { 7.0 }
        assertEquals(listOf("molde"), list.map { it.productId })
        assertEquals(7.0, list.single().onHand)
    }

    @Test
    fun avisaSoloBajoElMinimoYSinMinimoNuncaAvisa() {
        val stock = mapOf("bom" to mapOf("molde" to 12.0), "cal" to mapOf("rosca" to 0.0))
        val needed = stockReplenishmentsNeeded(listOf(molde, rosca), listOf(bombero, calvario, tienda)) { b, p ->
            stock[b]?.get(p) ?: 0.0
        }
        // La rosca está en 0 pero no tiene mínimo: no es aviso, es decisión de la encargada.
        assertEquals(listOf("molde"), needed.map { it.productId })
        assertEquals(8.0, needed.single().missing)
    }

    @Test
    fun enElMinimoExactoNoAvisa() {
        val needed = stockReplenishmentsNeeded(listOf(molde), listOf(bombero)) { _, _ -> 20.0 }
        assertTrue(needed.isEmpty())
    }

    @Test
    fun laSugerenciaRedondeaHaciaArriba() {
        val r = StockReplenishment("molde", "Pan molde", "bom", "Bombero", onHand = 12.5, minimum = 20.0)
        assertEquals(8.0, r.suggestedQuantity())
    }

    @Test
    fun soloLaPlantaQueSePideASiMismaVeLoDeExistencia() {
        assertTrue(molde.isOrderableIn("bom", "bom"))
        assertFalse(molde.isOrderableIn("t", "bom"))
        assertFalse(molde.isOrderableIn("", ""))
        assertTrue(pirujo.isOrderableIn("t", "bom"))
        assertFalse(porcion.isOrderableIn("bom", "bom"))
        // El pan tostado se hace con "Transformar", no se hornea.
        val tostado = Product(id = "tos", isMadeForStock = true, transformFromProductId = "molde", transformYield = 1.5)
        assertFalse(tostado.isOrderableIn("cal", "cal"))
    }
}
