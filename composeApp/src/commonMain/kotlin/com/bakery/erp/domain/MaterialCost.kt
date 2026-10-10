package com.bakery.erp.domain

import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Preparation
import com.bakery.erp.model.Product
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseLineKind
import com.bakery.erp.model.Recipe
import com.bakery.erp.model.lineTotal
import com.bakery.erp.model.toGramsOrNull

/**
 * Costo de materia prima por producto (paso 3 de `arquitectura/propuesta-rentabilidad-por-sucursal.md`).
 *
 * Todo se lleva a **quetzales por gramo**: la receta está en % del panadero (una razón entre
 * pesos) y el producto declara su peso en crudo, así que el gramo es la única unidad en que
 * los tres se pueden multiplicar sin adivinar.
 */

/** De dónde salió el precio de un insumo. */
enum class IngredientPriceOrigin {
    /** Promedio ponderado de lo comprado en el periodo: lo que de verdad se pagó. */
    PURCHASES,
    /** Precio de la presentación predeterminada: el último que se tecleó o cotizó. */
    FORMAT
}

data class IngredientGramPrice(
    val ingredientId: String,
    val costPerGram: Double,
    val origin: IngredientPriceOrigin,
    /** Cuántas compras entraron al promedio (0 con [IngredientPriceOrigin.FORMAT]). */
    val purchaseCount: Int = 0
)

/**
 * Precio por gramo de cada insumo. Primero el promedio de [purchaseLines] (lo pagado de verdad,
 * ponderado por cantidad: un saco comprado caro una vez no pesa igual que diez a buen precio);
 * si no se compró en el periodo, el de la presentación predeterminada.
 *
 * Un insumo queda FUERA del mapa si no tiene ninguno de los dos o si su unidad no se puede
 * pasar a gramos (un "Bote" sin peso): inventar un precio escondería el hueco, y la pantalla
 * de costos existe justo para mostrar esos huecos.
 */
fun ingredientGramPrices(
    ingredients: List<Ingredient>,
    purchaseLines: List<PurchaseLine>
): Map<String, IngredientGramPrice> {
    val linesByIngredient = purchaseLines
        .filter { it.lineKind == PurchaseLineKind.INGREDIENT && it.productId.isEmpty() }
        .filter { it.ingredientId.isNotEmpty() && it.quantityReceived > 0.0 && it.lineTotal > 0.0 }
        .groupBy { it.ingredientId }
    return ingredients.mapNotNull { ing ->
        fromPurchases(ing, linesByIngredient[ing.id].orEmpty()) ?: fromDefaultFormat(ing)
    }.associateBy { it.ingredientId }
}

private fun fromPurchases(ing: Ingredient, lines: List<PurchaseLine>): IngredientGramPrice? {
    var cost = 0.0
    var grams = 0.0
    var count = 0
    for (line in lines) {
        val unit = line.ingredientUnit.ifBlank { ing.effectiveBaseUnit }
        val g = toGramsOrNull(line.quantityReceived, unit, ing.unitWeightGrams) ?: continue
        if (g <= 0.0) continue
        cost += line.lineTotal
        grams += g
        count++
    }
    return if (grams > 0.0) IngredientGramPrice(ing.id, cost / grams, IngredientPriceOrigin.PURCHASES, count) else null
}

private fun fromDefaultFormat(ing: Ingredient): IngredientGramPrice? {
    val fmt = ing.purchaseFormats.firstOrNull { it.isDefault } ?: ing.purchaseFormats.firstOrNull() ?: return null
    if (fmt.packageCost <= 0.0 || fmt.unitsPerPackage <= 0.0) return null
    val grams = toGramsOrNull(fmt.unitsPerPackage, fmt.unit, ing.unitWeightGrams) ?: return null
    return if (grams > 0.0) IngredientGramPrice(ing.id, fmt.packageCost / grams, IngredientPriceOrigin.FORMAT) else null
}

/** Costo por gramo de una masa, y los insumos de su receta que no tienen precio. */
data class DoughGramCost(
    val costPerGram: Double,
    /** Nombres de los insumos sin precio. Su parte cuenta como Q0: el costo queda corto. */
    val missingPrices: List<String>
) {
    val isComplete: Boolean get() = missingPrices.isEmpty()
}

/**
 * Costo de un gramo de masa: `Σ (% × Q/g) ÷ Σ %`. Es la misma proporción que usa la receta
 * escalada del panadero (`flourGramsForDough`), así que el costo sale de la masa que de verdad
 * se amasa. Null si la receta no tiene componentes o sus porcentajes suman cero.
 */
fun Recipe.doughGramCost(prices: Map<String, IngredientGramPrice>): DoughGramCost? {
    val comps = allComponents.filter { it.percentage > 0.0 }
    val sumPct = comps.sumOf { it.percentage }
    if (comps.isEmpty() || sumPct <= 0.0) return null
    val missing = comps.filter { it.ingredientId !in prices }.map { it.ingredientName }.distinct()
    val cost = comps.sumOf { c -> c.percentage * (prices[c.ingredientId]?.costPerGram ?: 0.0) }
    return DoughGramCost(cost / sumPct, missing)
}

/**
 * Costo por gramo de una preparación (la concha, un relleno): lo que cuesta un lote ÷ lo que
 * pesa. Null si el lote no pesa nada.
 */
fun Preparation.gramCost(
    prices: Map<String, IngredientGramPrice>,
    ingredientsById: Map<String, Ingredient>
): DoughGramCost? {
    val weight = batchWeightGrams
    if (weight <= 0.0) return null
    val missing = mutableListOf<String>()
    var cost = 0.0
    for (pi in ingredients) {
        val grams = toGramsOrNull(pi.quantityPerBatch, pi.unit,
            pi.unitWeightGrams.takeIf { it > 0.0 } ?: ingredientsById[pi.ingredientId]?.unitWeightGrams ?: 0.0)
        val price = prices[pi.ingredientId]
        if (grams == null || price == null) { missing += pi.ingredientName; continue }
        cost += grams * price.costPerGram
    }
    return DoughGramCost(cost / weight, missing.distinct())
}

/** Por qué no se pudo calcular (o se calculó corto) el costo de un producto. */
sealed class CostProblem {
    /** Ninguna receta dice que lo produce. */
    data object NoRecipe : CostProblem()
    /** No tiene peso en crudo: no se sabe cuánta masa lleva. */
    data object NoRawWeight : CostProblem()
    /** La receta no tiene ingredientes con porcentaje. */
    data object EmptyRecipe : CostProblem()
    /** Insumos (de la masa, rellenos o adornos) sin precio: su parte cuenta Q0. */
    data class MissingPrices(val names: List<String>) : CostProblem()
}

data class ProductMaterialCost(
    val product: Product,
    val recipeName: String,
    val doughGrams: Double,
    /** Masa propia + rellenos de otra masa + adornos. */
    val doughCost: Double,
    val fillingsCost: Double,
    val decorationsCost: Double,
    /** Null = no se pudo calcular ([CostProblem.NoRecipe], [CostProblem.NoRawWeight]...). */
    val total: Double?,
    val problems: List<CostProblem>
) {
    /** Qué parte del precio al público se va en materia prima. Null sin precio o sin costo. */
    val shareOfPrice: Double?
        get() = total?.takeIf { product.salePrice > 0.0 }?.let { it / product.salePrice }
    /** Costo calculado, pero corto porque algún insumo no tiene precio. */
    val isIncomplete: Boolean get() = total != null && problems.isNotEmpty()
}

/**
 * Costo de materia prima de UNA unidad de [product] (su presentación de venta, la misma
 * cantidad a la que se refiere su peso en crudo):
 *  - masa propia = peso en crudo × Q/g de su receta,
 *  - rellenos de otra masa ([Product.doughFillings]) = gramos × Q/g de ESA receta,
 *  - adornos ([Product.decorations]) = gramos × Q/g de la preparación o del insumo comprado.
 *
 * [recipeByProduct] es la receta que produce cada producto (por sus `yields`); [recipesById]
 * resuelve las masas de los rellenos.
 */
fun productMaterialCost(
    product: Product,
    recipeByProduct: Map<String, Recipe>,
    recipesById: Map<String, Recipe>,
    preparationsById: Map<String, Preparation>,
    ingredientsById: Map<String, Ingredient>,
    prices: Map<String, IngredientGramPrice>
): ProductMaterialCost {
    val recipe = recipeByProduct[product.id]
    val raw = product.rawWeightGrams ?: 0.0
    fun fail(p: CostProblem) = ProductMaterialCost(product, recipe?.name.orEmpty(), raw, 0.0, 0.0, 0.0, null, listOf(p))
    if (recipe == null) return fail(CostProblem.NoRecipe)
    if (raw <= 0.0) return fail(CostProblem.NoRawWeight)
    val dough = recipe.doughGramCost(prices) ?: return fail(CostProblem.EmptyRecipe)
    val missing = dough.missingPrices.toMutableList()

    val doughCost = raw * dough.costPerGram
    val fillingsCost = product.doughFillings.sumOf { f ->
        val fillingDough = recipesById[f.recipeId]?.doughGramCost(prices)
        if (fillingDough == null) { missing += "masa ${f.recipeName}"; 0.0 }
        else { missing += fillingDough.missingPrices; f.rawWeightGrams * fillingDough.costPerGram }
    }
    val decorationsCost = product.decorations.sumOf { d ->
        if (d.isIngredientSourced) {
            val price = prices[d.ingredientId]
            if (price == null) { missing += d.ingredientName; 0.0 } else d.gramsPerUnit * price.costPerGram
        } else {
            val prep = preparationsById[d.preparationId]?.gramCost(prices, ingredientsById)
            if (prep == null) { missing += d.preparationName; 0.0 }
            else { missing += prep.missingPrices; d.gramsPerUnit * prep.costPerGram }
        }
    }
    val problems = missing.distinct().takeIf { it.isNotEmpty() }
        ?.let { listOf(CostProblem.MissingPrices(it)) }.orEmpty()
    return ProductMaterialCost(
        product = product,
        recipeName = recipe.name,
        doughGrams = raw,
        doughCost = doughCost,
        fillingsCost = fillingsCost,
        decorationsCost = decorationsCost,
        total = doughCost + fillingsCost + decorationsCost,
        problems = problems
    )
}
