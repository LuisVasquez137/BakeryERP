package com.bakery.erp.model

import com.bakery.erp.domain.transformExtrasCost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Transformar: pan molde → pan tostado, pirujo de mantequilla → pirujo con ajo. Sale el
 * original, entra el nuevo con el costo del original más los insumos agregados.
 */
class TransformationTest {

    private val tostado = Product(
        id = "tos", name = "Pan tostado",
        transformFromProductId = "mol", transformFromProductName = "Pan molde", transformYield = 1.5,
        transformExtras = listOf(TransformExtra("bolsa", "Bolsa tostado", 1.0, "unidad"))
    )
    private val ajo = Product(
        id = "aj", name = "Pirujo con ajo",
        transformFromProductId = "pir", transformFromProductName = "Pirujo mantequilla", transformYield = 1.0,
        transformExtras = listOf(
            TransformExtra("mant", "Mantequilla", 10.0, "gr"),
            TransformExtra("ajo", "Ajo", 2.0, "gr")
        )
    )
    private val moldeStock = FinishedProductStock(productId = "mol", currentStock = 10.0, avgUnitCost = 12.0)

    private fun request(p: Product = tostado, used: Double = 4.0, out: Double = 6.0) =
        TransformationRequest("cal", "Calvario", p, used, out, "u1", "Ana")

    @Test
    fun suggestsTheUsualYieldInWholePieces() {
        assertEquals(6.0, tostado.suggestedTransformOutput(4.0))
        assertEquals(4.0, tostado.suggestedTransformOutput(3.0)) // 4.5 → 4 empaques
        assertEquals(0.0, Product(id = "x").suggestedTransformOutput(3.0))
    }

    @Test
    fun cannotUseMoreThanTheInventoryHas() {
        assertNull(transformationError(request(used = 10.0), moldeStock))
        assertNotNull(transformationError(request(used = 11.0), moldeStock))
        assertNotNull(transformationError(request(out = 0.0), moldeStock))
        assertNotNull(transformationError(request(p = Product(id = "x", name = "X")), moldeStock))
        assertNotNull(transformationError(request(), null))
    }

    @Test
    fun costPassesFromTheOriginalPlusExtras() {
        // 4 moldes a Q12 + Q3 de extras = Q51, entre 5 empaques que salieron (no 6).
        val moves = transformationMovements(request(out = 5.0), moldeStock, extrasCost = 3.0, referenceId = "r1")
        assertEquals(2, moves.size)
        val (out, inn) = moves
        assertEquals("mol", out.productId)
        assertEquals(-4.0, out.quantity)
        assertEquals(12.0, out.unitPriceAtMovement)
        assertEquals("tos", inn.productId)
        assertEquals(5.0, inn.quantity)
        assertEquals(51.0 / 5, inn.unitPriceAtMovement, 1e-9)
        assertTrue(moves.all {
            it.type == FinishedMovementType.TRANSFORMACION && it.referenceId == "r1" &&
                it.referenceType == FinishedMovementReference.TRANSFORMATION
        })
    }

    @Test
    fun extrasScaleWithWhatCameOut() {
        val extras = ajo.transformExtrasFor(20.0)
        assertEquals(listOf(200.0, 40.0), extras.map { it.second })
    }

    @Test
    fun suppliesDoNotAddToCostButIngredientsDo() {
        val mant = Ingredient(
            id = "mant", name = "Mantequilla", usage = IngredientUsage.PRODUCTION,
            purchaseFormats = listOf(PurchaseFormat(id = "f", isDefault = true, unitsPerPackage = 1000.0, unit = "gr", packageCost = 50.0))
        )
        val bolsa = Ingredient(
            id = "bolsa", name = "Bolsa tostado", usage = IngredientUsage.SUPPLY, tracksStock = false,
            purchaseFormats = listOf(PurchaseFormat(id = "g", isDefault = true, unitsPerPackage = 100.0, unit = "unidad", packageCost = 30.0))
        )
        val cost = transformExtrasCost(
            listOf(TransformExtra("mant", "Mantequilla", 10.0, "gr") to 200.0, TransformExtra("bolsa", "Bolsa", 1.0, "unidad") to 6.0),
            mapOf("mant" to mant, "bolsa" to bolsa)
        )
        assertEquals(10.0, cost.cost, 1e-9) // 200 g a Q0.05
        assertTrue(cost.missingPrices.isEmpty())
    }

    @Test
    fun transformedProductIsNotOrderedFromPlantOnceSaved() {
        assertTrue(tostado.isTransformed)
        assertFalse(tostado.copy(isMadeForStock = true).isOrderedFromPlant)
        assertFalse(tostado.copy(transformYield = 0.0).isTransformed)
    }
}
