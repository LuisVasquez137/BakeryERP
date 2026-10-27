package com.bakery.erp.model

import com.bakery.erp.util.fmtTrim
import kotlin.math.ceil

const val GRAMS_PER_POUND = 453.592

/**
 * Resultado de evaluar si lo pedido de una receta alcanza el mínimo que la
 * amasadora puede trabajar.
 *
 * El chequeo es **por receta y no por producto**: de una misma masa salen varios
 * panes, y a la máquina le da igual en qué se corten — lo que cuenta es la suma.
 * Cinco bolitas de queso solas no amasan; cinco bolitas más treinta pirujos de
 * la misma masa, sí.
 *
 * Por eso tampoco se puede validar al momento de pedir: cuando la sucursal envía
 * su pedido todavía no se sabe qué van a pedir las demás. La decisión vive en la
 * consolidación del lote, que es cuando ya está toda la demanda del día.
 */
data class RecipeBatchCheck(
    val recipeId: String,
    val recipeName: String,
    /** Mínimo configurado en la receta, en gramos de harina base. */
    val minFlourGrams: Double,
    /** Harina base que se mojaría con lo pedido hoy. */
    val currentFlourGrams: Double,
    /** Líneas del lote que salen de esta receta. */
    val lines: List<ProductionLotLine>,
    /**
     * Receta por tanda (torta de leche, `model/BatchRecipe.kt`): la bandeja se hace siempre
     * entera, así que el "mínimo" es completar bandejas. En ese caso [currentFlourGrams] y
     * [minFlourGrams] son **piezas**, no gramos: lo pedido (50) y lo que dan las bandejas
     * enteras (70). El reparto del sobrante es el mismo diálogo que el de la amasadora.
     */
    val batch: BatchSpec? = null
) {
    val isUnderMinimum: Boolean
        get() = minFlourGrams > 0.0 && currentFlourGrams > 0.0 && currentFlourGrams < minFlourGrams

    /** Cuánto hay que multiplicar lo pedido para llegar al mínimo. 1.0 si ya alcanza. */
    val scaleFactor: Double
        get() = if (!isUnderMinimum) 1.0 else minFlourGrams / currentFlourGrams

    /**
     * Cantidades mínimas por producto para que la amasada sea viable: escala lo
     * pedido proporcionalmente y redondea hacia arriba. Se reparte proporcional
     * porque es lo que menos distorsiona la mezcla que la planta ya iba a hacer.
     */
    fun minimumQuantitiesByProduct(): Map<String, Double> {
        if (!isUnderMinimum) return lines.associate { it.productId to it.plannedQuantity }
        val factor = scaleFactor
        return lines.associate { it.productId to ceil(it.plannedQuantity * factor) }
    }

    /** Unidades extra por producto respecto de lo que las sucursales pidieron. */
    fun extraUnitsByProduct(): Map<String, Double> {
        val minimums = minimumQuantitiesByProduct()
        return lines.associate { line ->
            line.productId to
                ((minimums[line.productId] ?: 0.0) - line.plannedQuantity).coerceAtLeast(0.0)
        }
    }
}

/** Una línea para la tarjeta del aviso: "2.1 lb de 3 lb" o "piden 50 · 2 bandejas dan 70". */
fun RecipeBatchCheck.progressText(): String = batch?.let { spec ->
    "piden ${currentFlourGrams.fmtTrim(0)} · ${spec.label(spec.batchesFor(minFlourGrams))} dan ${minFlourGrams.fmtTrim(0)}"
} ?: "${currentFlourGrams.flourGramsAsPounds()} de ${minFlourGrams.flourGramsAsPounds()}"

/** Explicación del aviso, para el diálogo y el reparto por turnos. */
fun RecipeBatchCheck.shortfallText(): String = batch?.let { spec ->
    "Piden ${currentFlourGrams.fmtTrim(0)} y la ${spec.label(1).substringAfter(' ')} se hace entera: " +
        "${spec.label(spec.batchesFor(minFlourGrams))} dan ${minFlourGrams.fmtTrim(0)}, sobran " +
        "${(minFlourGrams - currentFlourGrams).fmtTrim(0)}."
} ?: ("Con lo que pidieron se mojarían solo ${currentFlourGrams.flourGramsAsPounds()} de harina, " +
    "y la amasadora necesita al menos ${minFlourGrams.flourGramsAsPounds()}.")

/**
 * Harina base necesaria para [doughGrams] de masa según los porcentajes de la
 * receta. Es la misma fórmula que usa la receta escalada del panadero:
 * `harina = masa × 100 ÷ Σ porcentajes`.
 *
 * Devuelve null si la receta no permite el cálculo (sin ingredientes, sin base,
 * o porcentajes en cero).
 */
fun Recipe.flourGramsForDough(doughGrams: Double): Double? {
    val components = allComponents
    if (components.isEmpty() || components.none { it.isBase }) return null
    val sumPercentages = components.sumOf { it.percentage }
    if (sumPercentages <= 0.0) return null
    return doughGrams * 100.0 / sumPercentages
}

/**
 * Agrupa [lines] por receta y evalúa el mínimo de cada una.
 *
 * [rawWeightResolver] resuelve el peso en crudo de cada línea (con el fallback al
 * producto para lotes viejos donde la línea no lo guardó). Las líneas sin receta,
 * sin peso en crudo o cuya receta no declara mínimo quedan fuera del resultado:
 * no hay nada que validar.
 */
fun checkRecipeBatchMinimums(
    lines: List<ProductionLotLine>,
    recipesById: Map<String, Recipe>,
    rawWeightResolver: (ProductionLotLine) -> Double?
): List<RecipeBatchCheck> {
    // Masa extra que otra receta necesita como relleno de esta (Pan de Capa →
    // bolita de Suave, ver DoughFilling) — sin esto, el mínimo de amasada de
    // Suave se evaluaría sin contar la masa que también se va a mojar para el
    // relleno, y podría marcarse "bajo el mínimo" cuando en realidad no lo está.
    val extraDoughByRecipe: Map<String, Double> = lines
        .flatMap { line -> line.doughFillingsSnapshot.map { it.recipeId to line.plannedQuantity * it.rawWeightGrams } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, grams) -> grams.sum() }

    val byRecipe = lines.filter { it.recipeId.isNotEmpty() }.groupBy { it.recipeId }

    val withOwnLines = byRecipe.mapNotNull { (recipeId, recipeLines) ->
        val recipe = recipesById[recipeId] ?: return@mapNotNull null
        // Por tanda: lo pedido se completa a bandejas enteras (50 → 70).
        recipe.batchSpec?.let { spec ->
            val pieces = recipeLines.sumOf { it.plannedQuantity }
            val target = spec.roundUp(pieces)
            return@mapNotNull if (pieces <= 0.0 || target <= pieces + 1e-9) null
            else RecipeBatchCheck(
                recipeId = recipeId,
                recipeName = recipe.name.ifEmpty { recipeLines.first().recipeName },
                minFlourGrams = target,
                currentFlourGrams = pieces,
                lines = recipeLines.sortedBy { it.productName },
                batch = spec
            )
        }
        if (recipe.minBatchFlourGrams <= 0.0) return@mapNotNull null
        val doughGrams = recipeLines.sumOf { line ->
            val raw = rawWeightResolver(line) ?: 0.0
            line.plannedQuantity * raw
        } + (extraDoughByRecipe[recipeId] ?: 0.0)
        if (doughGrams <= 0.0) return@mapNotNull null
        val flour = recipe.flourGramsForDough(doughGrams) ?: return@mapNotNull null
        RecipeBatchCheck(
            recipeId = recipeId,
            recipeName = recipe.name.ifEmpty { recipeLines.first().recipeName },
            minFlourGrams = recipe.minBatchFlourGrams,
            currentFlourGrams = flour,
            lines = recipeLines.sortedBy { it.productName }
        )
    }

    // Recetas que SOLO aparecen como relleno de otra masa (nadie pidió Pan Suave,
    // pero sí Pan de Capa): sin esto, esa masa nunca se evaluaba contra el
    // mínimo — se amasa igual, así que también tiene que poder chequearse.
    // `lines = emptyList()`: no hay producto propio al que subirle la cantidad,
    // así que `MinimumBatchDecisionDialog` no aplica acá — el caller debe
    // mostrar un aviso simple en vez del diálogo de reparto de excedente.
    val fillingOnly = extraDoughByRecipe.keys.minus(byRecipe.keys).mapNotNull { recipeId ->
        val recipe = recipesById[recipeId] ?: return@mapNotNull null
        if (recipe.minBatchFlourGrams <= 0.0) return@mapNotNull null
        val doughGrams = extraDoughByRecipe[recipeId] ?: 0.0
        if (doughGrams <= 0.0) return@mapNotNull null
        val flour = recipe.flourGramsForDough(doughGrams) ?: return@mapNotNull null
        RecipeBatchCheck(
            recipeId = recipeId,
            recipeName = recipe.name,
            minFlourGrams = recipe.minBatchFlourGrams,
            currentFlourGrams = flour,
            lines = emptyList()
        )
    }

    return (withOwnLines + fillingOnly).sortedBy { it.recipeName }
}

/**
 * Harina base que se mojaría produciendo [lines] de esta receta con las cantidades
 * que resuelva [quantityOf] — no siempre `plannedQuantity`: en el reparto de
 * turnos cada turno tiene su propia cantidad para el mismo producto, y ahí se le
 * pasa el mapa de ese turno en vez de leer la línea directamente.
 *
 * A diferencia de [checkRecipeBatchMinimums], no exige que la receta tenga un
 * mínimo configurado — sirve para mostrar el subtotal de harina siempre, no solo
 * como advertencia de amasada insuficiente.
 */
fun Recipe.flourGramsForLines(
    lines: List<ProductionLotLine>,
    quantityOf: (ProductionLotLine) -> Double,
    rawWeightResolver: (ProductionLotLine) -> Double?
): Double? {
    val doughGrams = lines.sumOf { line -> (rawWeightResolver(line) ?: 0.0) * quantityOf(line) }
    if (doughGrams <= 0.0) return null
    return flourGramsForDough(doughGrams)
}

/** Formatea gramos de harina como libras para la UI ("2.5 lb"). */
fun Double.flourGramsAsPounds(): String {
    val lb = this / GRAMS_PER_POUND
    val rounded = (lb * 100).toLong() / 100.0
    return if (rounded == rounded.toLong().toDouble()) "${rounded.toLong()} lb"
    else "$rounded lb"
}
