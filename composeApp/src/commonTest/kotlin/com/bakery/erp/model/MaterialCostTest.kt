package com.bakery.erp.model

import com.bakery.erp.domain.CostProblem
import com.bakery.erp.domain.IngredientPriceOrigin
import com.bakery.erp.domain.doughGramCost
import com.bakery.erp.domain.ingredientGramPrices
import com.bakery.erp.domain.productMaterialCost
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaterialCostTest {

    private fun near(expected: Double, actual: Double?, tol: Double = 1e-6) =
        assertTrue(actual != null && abs(expected - actual) < tol, "esperado $expected, salió $actual")

    // Saco de 50 lb a Q159 → Q159 / 22 679.6 g
    private val harina = Ingredient(
        id = "harina", name = "Harina", allowedUnits = listOf("lb"),
        purchaseFormats = listOf(PurchaseFormat(id = "s", unit = "lb", unitsPerPackage = 50.0, packageCost = 159.0, isDefault = true))
    )
    private val azucar = Ingredient(
        id = "azucar", name = "Azúcar", allowedUnits = listOf("lb"),
        purchaseFormats = listOf(PurchaseFormat(id = "q", unit = "lb", unitsPerPackage = 100.0, packageCost = 400.0, isDefault = true))
    )
    private val huevo = Ingredient(id = "huevo", name = "Huevos", allowedUnits = listOf("unidad"), unitWeightGrams = 50.0)

    @Test
    fun elPrecioSaleDeLaPresentacionPorGramo() {
        val prices = ingredientGramPrices(listOf(harina), emptyList())
        near(159.0 / (50 * 453.592), prices.getValue("harina").costPerGram)
        assertEquals(IngredientPriceOrigin.FORMAT, prices.getValue("harina").origin)
    }

    @Test
    fun lasComprasLeGananALaPresentacionYSePonderanPorCantidad() {
        val lines = listOf(
            // 30 huevos a Q1 y 90 a Q1.20 → 138 / 120 huevos = Q1.15 el huevo = Q0.023 el gramo
            PurchaseLine(ingredientId = "huevo", ingredientUnit = "unidad", quantityReceived = 30.0, unitCost = 1.0),
            PurchaseLine(ingredientId = "huevo", ingredientUnit = "unidad", quantityReceived = 90.0, unitCost = 1.20)
        )
        val p = ingredientGramPrices(listOf(huevo), lines).getValue("huevo")
        near(1.15 / 50.0, p.costPerGram)
        assertEquals(IngredientPriceOrigin.PURCHASES, p.origin)
        assertEquals(2, p.purchaseCount)
    }

    @Test
    fun sinCompraNiPresentacionNoHayPrecio() {
        assertTrue("huevo" !in ingredientGramPrices(listOf(huevo), emptyList()))
    }

    @Test
    fun elCostoDeLaMasaEsElPromedioPonderadoPorPorcentaje() {
        val recipe = Recipe(components = listOf(
            RecipeComponent(ingredientId = "harina", ingredientName = "Harina", percentage = 100.0, isBase = true),
            RecipeComponent(ingredientId = "azucar", ingredientName = "Azúcar", percentage = 25.0)
        ))
        val prices = ingredientGramPrices(listOf(harina, azucar), emptyList())
        val expected = (100 * prices.getValue("harina").costPerGram + 25 * prices.getValue("azucar").costPerGram) / 125
        near(expected, recipe.doughGramCost(prices)?.costPerGram)
    }

    @Test
    fun elProductoSumaMasaRellenoDeOtraMasaYAdorno() {
        val masa = Recipe(id = "r1", name = "Capa", components = listOf(
            RecipeComponent(ingredientId = "harina", ingredientName = "Harina", percentage = 100.0, isBase = true)),
            yields = listOf(RecipeYield(productId = "p")))
        val suave = Recipe(id = "r2", name = "Suave", components = listOf(
            RecipeComponent(ingredientId = "azucar", ingredientName = "Azúcar", percentage = 100.0, isBase = true)))
        val product = Product(
            id = "p", name = "Pan de capa", salePrice = 1.0, rawWeightGrams = 60.0,
            doughFillings = listOf(DoughFilling(recipeId = "r2", recipeName = "Suave", rawWeightGrams = 10.0)),
            decorations = listOf(ProductDecoration(ingredientId = "azucar", ingredientName = "Azúcar", gramsPerUnit = 5.0))
        )
        val prices = ingredientGramPrices(listOf(harina, azucar), emptyList())
        val h = prices.getValue("harina").costPerGram
        val a = prices.getValue("azucar").costPerGram
        val cost = productMaterialCost(product, mapOf("p" to masa), mapOf("r1" to masa, "r2" to suave),
            emptyMap(), emptyMap(), prices)
        near(60 * h + 10 * a + 5 * a, cost.total)
        near((60 * h + 15 * a) / 1.0, cost.shareOfPrice)
        assertTrue(cost.problems.isEmpty())
    }

    @Test
    fun sinRecetaOSinPesoNoSeCalcula() {
        val prices = ingredientGramPrices(listOf(harina), emptyList())
        val sinReceta = productMaterialCost(Product(id = "x", rawWeightGrams = 50.0), emptyMap(), emptyMap(), emptyMap(), emptyMap(), prices)
        assertNull(sinReceta.total)
        assertEquals(listOf<CostProblem>(CostProblem.NoRecipe), sinReceta.problems)

        val r = Recipe(id = "r", components = listOf(RecipeComponent(ingredientId = "harina", percentage = 100.0, isBase = true)))
        val sinPeso = productMaterialCost(Product(id = "x"), mapOf("x" to r), mapOf("r" to r), emptyMap(), emptyMap(), prices)
        assertNull(sinPeso.total)
        assertEquals(listOf<CostProblem>(CostProblem.NoRawWeight), sinPeso.problems)
    }

    @Test
    fun unInsumoSinPrecioDejaElCostoCortoYLoNombra() {
        val r = Recipe(id = "r", components = listOf(
            RecipeComponent(ingredientId = "harina", ingredientName = "Harina", percentage = 100.0, isBase = true),
            RecipeComponent(ingredientId = "huevo", ingredientName = "Huevos", percentage = 20.0)))
        val prices = ingredientGramPrices(listOf(harina, huevo), emptyList())
        val cost = productMaterialCost(Product(id = "x", rawWeightGrams = 120.0), mapOf("x" to r), mapOf("r" to r),
            emptyMap(), emptyMap(), prices)
        near(120.0 * (100 * prices.getValue("harina").costPerGram) / 120, cost.total)
        assertTrue(cost.isIncomplete)
        assertEquals(listOf<CostProblem>(CostProblem.MissingPrices(listOf("Huevos"))), cost.problems)
    }
}
