package com.bakery.erp.model

import com.bakery.erp.domain.productMaterialCost
import com.bakery.erp.domain.IngredientGramPrice
import com.bakery.erp.domain.IngredientPriceOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hoja de horneado de bizcochos: masa + molde. El repostero dice "1 plancha, 6 de frutas, 6 de
 * tres leches" y sale la masa a batir; el pastel consume bizcochos, el más viejo primero.
 */
class BakePlanTest {

    private val oz = 28.349523125

    // Masa de vainilla: 1000 g por receta.
    private val vainilla = Preparation(
        id = "vai", name = "Masa vainilla", usage = PreparationUsage.BATTER,
        ingredients = listOf(
            PreparationIngredient(ingredientId = "harina", ingredientName = "Harina", quantityPerBatch = 500.0, unit = "gr"),
            PreparationIngredient(ingredientId = "huevo", ingredientName = "Huevo", quantityPerBatch = 10.0, unit = "unidad", unitWeightGrams = 50.0)
        )
    )
    private val frutas = Preparation(
        id = "fru", name = "Bizcocho de frutas", usage = PreparationUsage.BASE,
        batterPreparationId = "vai", batterPreparationName = "Masa vainilla", batterGramsPerUnit = 22 * oz,
        stockMinimumUnits = 4.0
    )
    private val plancha = Preparation(
        id = "pla", name = "Plancha", usage = PreparationUsage.BASE,
        batterPreparationId = "vai", batterPreparationName = "Masa vainilla", batterGramsPerUnit = 2000.0
    )

    private val pastelFrutas = Product(
        id = "pf", name = "Pastel de frutas",
        decorations = listOf(ProductDecoration(preparationId = "fru", preparationName = "Bizcocho de frutas", unitsPerProduct = 1.0))
    )
    private val pastelito = Product(
        id = "pp", name = "Pastelito",
        decorations = listOf(ProductDecoration(preparationId = "pla", preparationName = "Plancha", unitsPerProduct = 1.0 / 24))
    )

    @Test
    fun demandCountsWholeCakesAndPortionsOfPlancha() {
        val demand = baseDemand(
            listOf("pf" to 10.0, "pp" to 25.0, "pf" to 2.0),
            mapOf("pf" to pastelFrutas, "pp" to pastelito)
        )
        assertEquals(12.0, demand.getValue("fru").units)
        assertEquals(mapOf("Pastel de frutas" to 12.0), demand.getValue("fru").byProduct)
        assertEquals(25.0 / 24, demand.getValue("pla").units, 1e-9)
    }

    @Test
    fun suggestionCoversOrdersPlusMinimumInWholeMolds() {
        // Piden 10, hay 6, quiere tener 4 → 8.
        assertEquals(8, BakeRow(frutas, BaseDemand(10.0), onHand = 6.0).suggested)
        assertEquals(4.0, BakeRow(frutas, BaseDemand(10.0), onHand = 6.0).short)
        // 1.04 planchas, hay 0.3 → 1 (no se hornea media plancha).
        assertEquals(1, BakeRow(plancha, BaseDemand(25.0 / 24), onHand = 0.3).suggested)
        // Alcanza y sobra: 0.
        assertEquals(0, BakeRow(plancha, BaseDemand(0.5), onHand = 2.0).suggested)
    }

    @Test
    fun moldsOfTheSameBatterAreMixedTogether() {
        val mixes = batterMixes(listOf(BakeMold(frutas, 6.0), BakeMold(plancha, 1.0)), mapOf("vai" to vainilla))
        assertEquals(1, mixes.size)
        val mix = mixes.single()
        assertEquals(6 * 22 * oz + 2000.0, mix.totalGrams, 1e-6)
        // Receta de 1000 g: la escala es la masa total en kilos.
        val scale = mix.totalGrams / 1000.0
        assertEquals(scale, mix.scale!!, 1e-9)
        val huevos = mix.scaledIngredients.first { it.first.ingredientId == "huevo" }.second
        assertEquals(10 * scale, huevos, 1e-9)
    }

    @Test
    fun moldWithoutBatterIsLeftOut() {
        val loose = frutas.copy(id = "x", batterPreparationId = "")
        assertTrue(batterMixes(listOf(BakeMold(loose, 3.0)), mapOf("vai" to vainilla)).isEmpty())
        // Masa que ya no existe: no hay receta que escalar.
        assertNull(batterMixes(listOf(BakeMold(frutas, 3.0)), emptyMap()).single().scale)
    }

    @Test
    fun cakesTakeTheOldestBizcochosFirst() {
        val old = PreparationBatch(id = "a", preparationId = "fru", producedAt = 1, producedUnits = 3.0, remainingUnits = 2.0)
        val new = PreparationBatch(id = "b", preparationId = "fru", producedAt = 2, producedUnits = 6.0, remainingUnits = 6.0)
        val (takes, uncovered) = fifoUnitTakes(listOf(new, old), 5.0)
        assertEquals(listOf(UnitTake("a", 2.0), UnitTake("b", 3.0)), takes)
        assertEquals(0.0, uncovered)

        val (_, short) = fifoUnitTakes(listOf(old), 3.0)
        assertEquals(1.0, short, 1e-9)
    }

    @Test
    fun stockSumsActiveBatchesAndKnowsTheOldest() {
        val stock = baseStock(listOf(
            PreparationBatch(id = "a", preparationId = "fru", producedAt = 5, producedUnits = 3.0, remainingUnits = 2.0),
            PreparationBatch(id = "b", preparationId = "fru", producedAt = 9, producedUnits = 6.0, remainingUnits = 6.0),
            PreparationBatch(id = "c", preparationId = "fru", producedAt = 1, producedUnits = 6.0, remainingUnits = 0.0,
                status = PreparationBatchStatus.DEPLETED)
        ))
        assertEquals(8.0 to 5L, stock["fru"])
    }

    @Test
    fun cakeCostIncludesItsShareOfTheBatter() {
        val recipe = Recipe(
            id = "r", name = "Pastel",
            components = listOf(RecipeComponent(ingredientId = "harina", ingredientName = "Harina", percentage = 100.0)),
            yields = emptyList()
        )
        val prices = mapOf(
            "harina" to IngredientGramPrice("harina", 0.01, IngredientPriceOrigin.FORMAT),
            "huevo" to IngredientGramPrice("huevo", 0.02, IngredientPriceOrigin.FORMAT)
        )
        // Masa: 500 g harina (Q5) + 500 g huevo (Q10) = Q15 / 1000 g.
        val cost = productMaterialCost(
            pastelito.copy(rawWeightGrams = 0.0001),
            mapOf("pp" to recipe), emptyMap(),
            mapOf("vai" to vainilla, "pla" to plancha), emptyMap(), prices
        )
        // 1/24 de plancha de 2000 g = 83.3 g de masa a Q0.015.
        assertEquals(2000.0 / 24 * 0.015, cost.decorationsCost, 1e-9)
    }
    @Test
    fun lotDemandUsesTheSnapshotAndCountsWasteAndExtra() {
        val frutasLine = ProductionLotLine(
            productId = "pf", productName = "Pastel de frutas", plannedQuantity = 5.0,
            wasteQuantity = 1.0, extraQuantity = 2.0,
            decorationsSnapshot = listOf(ProductionLotDecoration(preparationId = "fru", unitsPerProduct = 1.0))
        )
        // 30 porciones de tres leches a 1/24 de plancha, ya contadas (28 buenas).
        val porciones = ProductionLotLine(
            productId = "tl", productName = "Tres leches", plannedQuantity = 30.0, producedQuantity = 28.0,
            decorationsSnapshot = listOf(ProductionLotDecoration(preparationId = "pla", unitsPerProduct = 1.0 / 24))
        )
        val sinBizcocho = ProductionLotLine(productId = "x", productName = "Concha", plannedQuantity = 50.0)

        val demand = lotBaseDemand(listOf(frutasLine, porciones, sinBizcocho))

        assertEquals(8.0, demand.getValue("fru").units, 1e-9)
        assertEquals(28.0 / 24, demand.getValue("pla").units, 1e-9)
        assertEquals(setOf("fru", "pla"), demand.keys)
    }

    @Test
    fun preparationWithoutPlantsIsMadeEverywhere() {
        assertTrue(frutas.isMadeIn("calvario"))
        val soloCalvario = frutas.copy(branchIds = listOf("calvario"))
        assertTrue(soloCalvario.isMadeIn("calvario"))
        assertTrue(!soloCalvario.isMadeIn("bombero"))
    }
}
