package com.bakery.erp.model

import com.bakery.erp.util.fmtTrim
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Receta por tanda: la torta de leche. No es masa de pan — no lleva harina base ni % del
 * panadero — sino una bandeja que se hace siempre entera: "6 panes molde, 4 oz de aceite,
 * 1 L de leche… salen 35 pedazos". Puede llevar productos del inventario (el pan molde), que al
 * completar el lote salen del terminado de la planta. Tests en `BatchRecipeTest`.
 *
 * Las sucursales piden los pedazos que quieren; al armar el lote lo pedido se completa a
 * bandejas enteras y la encargada reparte lo de más (mismo flujo que el mínimo de amasada,
 * `RecipeBatchMinimum.kt`).
 */

const val RECIPE_MODE_BATCH = "BATCH"

val Recipe.isBatch: Boolean get() = inputMode == RECIPE_MODE_BATCH

/** Lo que define una tanda; null si la receta no es por tanda o no dice cuánto rinde. */
val Recipe.batchSpec: BatchSpec?
    get() = if (isBatch && batchYield > 0.0) BatchSpec(batchYield, batchUnitName) else null

/** Componente que es un producto del inventario (pan molde), no un insumo. */
val RecipeComponent.isProduct: Boolean get() = productId.isNotEmpty()

data class BatchSpec(
    /** Piezas de una tanda (35 pedazos). */
    val fullYield: Double,
    /** "bandeja". Vacío = "tanda". */
    val unitName: String = ""
) {
    private val unit: String get() = unitName.trim().ifEmpty { "tanda" }

    fun piecesFor(batches: Int): Double = batches.coerceAtLeast(0) * fullYield

    /**
     * Tandas que corresponden a [quantity] piezas: la más cercana (empate hacia arriba), y al
     * menos una si se pidió algo. Sirve igual para lo planeado (ya completado a tandas) que para
     * lo contado: si de una bandeja salieron 34 o 36, sigue siendo una bandeja.
     */
    fun batchesFor(quantity: Double): Int {
        if (quantity <= 0.0 || fullYield <= 0.0) return 0
        val exact = quantity / fullYield
        val lower = floor(exact + 1e-9).toInt()
        val nearest = if (exact - lower >= 0.5 - 1e-9) lower + 1 else lower
        return nearest.coerceAtLeast(1)
    }

    /** Piezas de las tandas enteras que hacen falta para cubrir [quantity] (50 → 70). */
    fun roundUp(quantity: Double): Double =
        if (quantity <= 0.0 || fullYield <= 0.0) 0.0 else ceil(quantity / fullYield - 1e-9) * fullYield

    /** "1 bandeja", "2 bandejas". */
    fun label(batches: Int): String = "$batches ${if (batches == 1) unit else plural(unit)}"

    private fun plural(word: String): String =
        if (word.last().lowercaseChar() in "aeiou") "${word}s" else "${word}es"
}

/** Lo que gasta una cantidad de tandas: insumos en gramos y productos en unidades. */
data class BatchUsage(
    val ingredientGrams: Map<String, Double>,
    /** productId → unidades (pan molde). */
    val productUnits: Map<String, Double>,
    /** Insumos que no se pudieron pasar a gramos (contados sin peso unitario). */
    val unconvertible: List<String>
)

/**
 * Consumo de [batches] tandas: cada componente × tandas. [unitWeightById] es el peso de una
 * unidad de cada insumo (el huevo).
 */
fun Recipe.batchUsage(batches: Int, unitWeightById: Map<String, Double>): BatchUsage {
    val grams = HashMap<String, Double>()
    val units = HashMap<String, Double>()
    val unconvertible = mutableListOf<String>()
    if (batches <= 0) return BatchUsage(grams, units, unconvertible)
    for (c in allComponents) {
        val qty = c.inputQuantity * batches
        if (qty <= 0.0) continue
        if (c.isProduct) {
            units[c.productId] = (units[c.productId] ?: 0.0) + qty
            continue
        }
        if (c.ingredientId.isEmpty()) continue
        val g = toGramsOrNull(qty, c.ingredientUnit, unitWeightById[c.ingredientId] ?: 0.0)
        if (g == null) { unconvertible += c.ingredientName; continue }
        grams[c.ingredientId] = (grams[c.ingredientId] ?: 0.0) + g
    }
    return BatchUsage(grams, units, unconvertible.distinct())
}

/** "12 unidad", "2 lt" — la cantidad escalada de un componente, en la unidad en que se escribió. */
fun RecipeComponent.scaledBatchText(batches: Int): String =
    "${(inputQuantity * batches).fmtTrim(2)} ${ingredientUnit.ifEmpty { "u." }}"
