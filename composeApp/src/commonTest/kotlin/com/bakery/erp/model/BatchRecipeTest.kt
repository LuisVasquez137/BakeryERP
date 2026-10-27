package com.bakery.erp.model

import com.bakery.erp.domain.IngredientGramPrice
import com.bakery.erp.domain.IngredientPriceOrigin
import com.bakery.erp.domain.productMaterialCost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BatchRecipeTest {

    // Torta de leche: la bandeja se hace siempre entera y da 35 pedazos.
    private val bandeja = BatchSpec(fullYield = 35.0, unitName = "bandeja")

    private val torta = Recipe(
        id = "torta",
        name = "Torta de leche",
        inputMode = RECIPE_MODE_BATCH,
        batchYield = 35.0,
        batchUnitName = "bandeja",
        components = listOf(
            RecipeComponent(id = "1", productId = "molde", productName = "Pan molde",
                ingredientName = "Pan molde", ingredientUnit = "unidad", inputQuantity = 6.0),
            RecipeComponent(id = "2", ingredientId = "aceite", ingredientName = "Aceite",
                ingredientUnit = "oz", inputQuantity = 4.0),
            RecipeComponent(id = "3", ingredientId = "leche", ingredientName = "Leche",
                ingredientUnit = "lt", inputQuantity = 1.0),
            RecipeComponent(id = "4", ingredientId = "huevo", ingredientName = "Huevo",
                ingredientUnit = "unidad", inputQuantity = 4.0)
        )
    )

    private fun line(productId: String, qty: Double) = ProductionLotLine(
        id = productId, productId = productId, productName = "Torta de leche",
        recipeId = "torta", recipeName = "Torta de leche", plannedQuantity = qty
    )

    @Test
    fun loPedidoSeCompletaABandejasEnteras() {
        assertEquals(35.0, bandeja.roundUp(10.0))
        assertEquals(35.0, bandeja.roundUp(35.0))
        assertEquals(70.0, bandeja.roundUp(50.0))
        assertEquals(0.0, bandeja.roundUp(0.0))
    }

    @Test
    fun loContadoCaeEnLaBandejaMasCercana() {
        assertEquals(1, bandeja.batchesFor(34.0))
        assertEquals(1, bandeja.batchesFor(36.0))
        assertEquals(2, bandeja.batchesFor(70.0))
        assertEquals(2, bandeja.batchesFor(68.0))
        // Aunque se haya pedido poco, si se hizo algo fue al menos una bandeja.
        assertEquals(1, bandeja.batchesFor(10.0))
        assertEquals(0, bandeja.batchesFor(0.0))
    }

    @Test
    fun etiquetas() {
        assertEquals("1 bandeja", bandeja.label(1))
        assertEquals("2 bandejas", bandeja.label(2))
        assertEquals("1 tanda", BatchSpec(35.0).label(1))
    }

    @Test
    fun consumoDeDosBandejas() {
        val usage = torta.batchUsage(batches = 2, unitWeightById = mapOf("huevo" to 50.0))
        // 2 bandejas = 12 panes molde, que salen del terminado (no de insumos).
        assertEquals(12.0, usage.productUnits["molde"])
        assertEquals(8 * 28.3495, usage.ingredientGrams["aceite"]!!, 0.01)
        assertEquals(2000.0, usage.ingredientGrams["leche"]!!, 0.01)
        assertEquals(400.0, usage.ingredientGrams["huevo"]!!, 0.01)
        assertTrue(usage.unconvertible.isEmpty())
    }

    @Test
    fun huevoSinPesoNoSeInventa() {
        val usage = torta.batchUsage(batches = 1, unitWeightById = emptyMap())
        assertEquals(listOf("Huevo"), usage.unconvertible)
        assertTrue("huevo" !in usage.ingredientGrams)
    }

    @Test
    fun avisoAlArmarElLoteCuandoNoCompletaLaBandeja() {
        // Tres sucursales piden 20 + 15 + 15 = 50: 2 bandejas dan 70, sobran 20.
        val checks = checkRecipeBatchMinimums(
            lines = listOf(line("torta-p", 50.0)),
            recipesById = mapOf("torta" to torta),
            rawWeightResolver = { null }
        )
        val check = assertNotNull(checks.singleOrNull())
        assertTrue(check.isUnderMinimum)
        assertEquals(mapOf("torta-p" to 70.0), check.minimumQuantitiesByProduct())
        assertEquals(mapOf("torta-p" to 20.0), check.extraUnitsByProduct())
        assertEquals("piden 50 · 2 bandejas dan 70", check.progressText())
    }

    @Test
    fun bandejaCompletaNoAvisa() {
        val checks = checkRecipeBatchMinimums(
            lines = listOf(line("torta-p", 70.0)),
            recipesById = mapOf("torta" to torta),
            rawWeightResolver = { null }
        )
        assertTrue(checks.isEmpty())
    }

    @Test
    fun costoPorPedazoIncluyeElPanMolde() {
        val molde = Product(id = "molde", name = "Pan molde", rawWeightGrams = 500.0)
        val masaMolde = Recipe(
            id = "rm", name = "Masa molde",
            components = listOf(RecipeComponent(ingredientId = "harina", ingredientName = "Harina", percentage = 100.0, isBase = true))
        )
        val pedazo = Product(id = "torta-p", name = "Torta de leche")
        val prices = listOf("harina" to 0.01, "aceite" to 0.02, "leche" to 0.005, "huevo" to 0.03)
            .associate { (id, p) -> id to IngredientGramPrice(id, p, IngredientPriceOrigin.FORMAT) }
        val ingredientsById = mapOf("huevo" to Ingredient(id = "huevo", name = "Huevo", unitWeightGrams = 50.0))
        val cost = productMaterialCost(
            pedazo,
            recipeByProduct = mapOf("torta-p" to torta, "molde" to masaMolde),
            recipesById = mapOf("torta" to torta, "rm" to masaMolde),
            preparationsById = emptyMap(),
            ingredientsById = ingredientsById,
            prices = prices,
            productsById = mapOf("molde" to molde, "torta-p" to pedazo)
        )
        // Bandeja: 6 moldes × Q5 + aceite 4 oz + 1 L leche + 4 huevos, ÷ 35.
        val batch = 6 * 500.0 * 0.01 + 4 * 28.3495 * 0.02 + 1000.0 * 0.005 + 200.0 * 0.03
        assertEquals(batch / 35.0, cost.total!!, 0.0001)
        assertTrue(cost.problems.isEmpty())
    }
}
