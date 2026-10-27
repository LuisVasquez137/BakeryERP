package com.bakery.erp.model

import kotlin.math.ceil

/*
 * Hoja de horneado de bizcochos (ver "Bizcochos" en `arquitectura/catalogo.md`).
 *
 * El repostero ve cuántos bizcochos piden los pedidos del día, cuántos tiene fríos y decide
 * cuántos hacer; de eso sale la masa a batir. Masa + molde, igual que el pan agrupado por masa:
 * la plancha y el bizcocho de frutas son de la misma masa de vainilla y se baten juntos.
 */

/** Lo que piden los pedidos de un molde: bizcochos (fracción para la plancha) y de qué pasteles. */
data class BaseDemand(
    val units: Double = 0.0,
    /** Nombre del pastel → cuántos piden. Para que el repostero vea de dónde sale el número. */
    val byProduct: Map<String, Double> = emptyMap()
)

/**
 * Bizcochos que piden [lines] (producto → cantidad pedida), por molde. Solo cuenta lo que el
 * producto lleva como bizcocho ([ProductDecoration.isCounted]): 10 pasteles de frutas = 10;
 * 25 pastelitos a 1/24 de plancha = 1.04 planchas.
 */
fun baseDemand(
    lines: List<Pair<String, Double>>,
    productsById: Map<String, Product>
): Map<String, BaseDemand> {
    val units = mutableMapOf<String, Double>()
    val byProduct = mutableMapOf<String, MutableMap<String, Double>>()
    for ((productId, qty) in lines) {
        if (qty <= 0.0) continue
        val product = productsById[productId] ?: continue
        for (deco in product.decorations) {
            if (!deco.isCounted || deco.preparationId.isEmpty()) continue
            units[deco.preparationId] = (units[deco.preparationId] ?: 0.0) + qty * deco.unitsPerProduct
            val names = byProduct.getOrPut(deco.preparationId) { mutableMapOf() }
            names[product.name] = (names[product.name] ?: 0.0) + qty
        }
    }
    return units.mapValues { (id, u) -> BaseDemand(u, byProduct[id].orEmpty()) }
}

/**
 * Bizcochos que pide un lote de producción, por molde. Sale del snapshot de adornos de cada
 * línea —el mismo que descuenta `completeLot`— y no del producto de hoy: si alguien cambia el
 * pastel a medio día, lo que se sugiere hornear y lo que se descuenta siguen coincidiendo.
 * Cuenta lo mismo que descuenta el cierre: planeado o contado, más lo quemado (ese pastel ya
 * gastó su bizcocho) y lo de más.
 */
fun lotBaseDemand(lines: List<ProductionLotLine>): Map<String, BaseDemand> {
    val units = mutableMapOf<String, Double>()
    val byProduct = mutableMapOf<String, MutableMap<String, Double>>()
    for (line in lines) {
        val qty = line.kneadedQuantity()
        if (qty <= 0.0) continue
        for (deco in line.decorationsSnapshot) {
            if (!deco.isCounted || deco.preparationId.isEmpty()) continue
            units[deco.preparationId] = (units[deco.preparationId] ?: 0.0) + qty * deco.unitsPerProduct
            val names = byProduct.getOrPut(deco.preparationId) { mutableMapOf() }
            names[line.productName] = (names[line.productName] ?: 0.0) + qty
        }
    }
    return units.mapValues { (id, u) -> BaseDemand(u, byProduct[id].orEmpty()) }
}

/** Una fila de la hoja: un molde con lo pedido y lo que hay. */
data class BakeRow(
    val base: Preparation,
    val demand: BaseDemand,
    val onHand: Double,
    /** Cuándo se hizo el más viejo que queda (0 = no queda ninguno). */
    val oldestProducedAt: Long = 0L
) {
    /** Lo que no alcanza para los pedidos. */
    val short: Double get() = (demand.units - onHand).coerceAtLeast(0.0)

    /**
     * Sugerencia: cubrir lo pedido y dejar el mínimo frío para los días siguientes, en bizcochos
     * enteros (no se hornea media plancha). El repostero la cambia: él decide, como la esponja.
     */
    val suggested: Int
        get() = ceil(demand.units + base.stockMinimumUnits - onHand - 1e-9).toInt().coerceAtLeast(0)
}

/** Saldo de bizcochos por molde y el más viejo de cada uno. Solo lotes activos con saldo. */
fun baseStock(batches: List<PreparationBatch>): Map<String, Pair<Double, Long>> =
    batches.filter { it.status == PreparationBatchStatus.ACTIVE && it.remainingUnits > 0.0 }
        .groupBy { it.preparationId }
        .mapValues { (_, list) -> list.sumOf { it.remainingUnits } to list.minOf { it.producedAt } }

/** Un molde a hornear. */
data class BakeMold(val base: Preparation, val units: Double) {
    val batterGrams: Double get() = units * base.batterGramsPerUnit
}

/** Lo que se bate de una masa: todos sus moldes juntos. */
data class BatterMix(
    val batterId: String,
    val batterName: String,
    /** Null si la masa ya no existe: no hay receta que escalar. */
    val batter: Preparation?,
    val molds: List<BakeMold>
) {
    val totalGrams: Double get() = molds.sumOf { it.batterGrams }

    /** Cuántas veces la receta de la masa. Null si la masa no tiene insumos (no pesa nada). */
    val scale: Double?
        get() = batter?.batchWeightGrams?.takeIf { it > 0.0 }?.let { totalGrams / it }

    /** Insumos escalados, en la unidad en que está escrita la masa (huevos en unidades). */
    val scaledIngredients: List<Pair<PreparationIngredient, Double>>
        get() {
            val s = scale ?: return emptyList()
            return batter?.ingredients.orEmpty().map { it to it.quantityPerBatch * s }
        }
}

/**
 * Agrupa lo que se va a hornear por masa. Los moldes sin masa o sin masa por molde quedan
 * fuera: no hay cómo saber cuánta mezcla llevan (el form del bizcocho no deja guardarlos así).
 */
fun batterMixes(molds: List<BakeMold>, battersById: Map<String, Preparation>): List<BatterMix> =
    molds.filter { it.units > 0.0 && it.base.batterPreparationId.isNotEmpty() && it.base.batterGramsPerUnit > 0.0 }
        .groupBy { it.base.batterPreparationId }
        .map { (batterId, list) ->
            val batter = battersById[batterId]
            BatterMix(
                batterId = batterId,
                batterName = batter?.name ?: list.first().base.batterPreparationName,
                batter = batter,
                molds = list
            )
        }
        .sortedBy { it.batterName.lowercase() }

/** Cuánto se saca de un lote de bizcochos. */
data class UnitTake(val batchId: String, val units: Double)

/**
 * Bizcochos a sacar de [batches], el más viejo primero (es el que se pone feo). Devuelve lo
 * que se saca de cada lote y lo que no alcanzó.
 */
fun fifoUnitTakes(batches: List<PreparationBatch>, units: Double): Pair<List<UnitTake>, Double> {
    var left = units
    val takes = mutableListOf<UnitTake>()
    for (b in batches.filter { it.status == PreparationBatchStatus.ACTIVE && it.remainingUnits > 0.0 }
        .sortedBy { it.producedAt }) {
        if (left <= UNIT_EPSILON) break
        val take = minOf(left, b.remainingUnits)
        takes += UnitTake(b.id, take)
        left -= take
    }
    return takes to (if (left > UNIT_EPSILON) left else 0.0)
}

/** Debajo de esto un saldo de bizcochos es cero (1/24 de plancha da decimales que no cierran). */
const val UNIT_EPSILON = 0.001
