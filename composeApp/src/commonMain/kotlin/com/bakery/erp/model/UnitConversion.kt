package com.bakery.erp.model

import kotlin.math.roundToLong

enum class UnitFamily { WEIGHT, VOLUME, COUNT }

fun unitFamily(unit: String): UnitFamily? = when (unit) {
    "gr", "oz", "lb", "kg"        -> UnitFamily.WEIGHT
    "ml", "lt"                     -> UnitFamily.VOLUME
    "unidad", "docena", "saco"     -> UnitFamily.COUNT
    else                           -> null
}

/** Unidades compatibles para conversión (misma familia). COUNT devuelve lista de 1 ítem. */
fun compatibleUnits(unit: String): List<String> = when (unitFamily(unit)) {
    UnitFamily.WEIGHT -> listOf("gr", "oz", "lb", "kg")
    UnitFamily.VOLUME -> listOf("ml", "lt")
    UnitFamily.COUNT  -> listOf(unit)
    null              -> listOf(unit)
}

/** Convierte a gramos (peso) o mililitros (volumen). COUNT/desconocido: pasa sin cambio. */
fun toBaseUnit(value: Double, fromUnit: String): Double = when (fromUnit) {
    "gr"  -> value
    "oz"  -> value * 28.3495
    "lb"  -> value * 453.592
    "kg"  -> value * 1000.0
    "ml"  -> value
    "lt"  -> value * 1000.0
    else  -> value
}

// ── Conteo ↔ peso ────────────────────────────────────────────────────────────
//
// El huevo se compra y se cuenta por unidad, pero la receta lo necesita en peso:
// el % del panadero es una razón entre pesos, y "6" no es un peso. El puente es
// `Ingredient.unitWeightGrams` (cuánto pesa UNA unidad). Sin ese dato la
// conversión no existe y estas funciones devuelven null en vez de inventar un
// número — antes `toBaseUnit` devolvía el valor tal cual y 6 huevos entraban a
// la receta como 6 gramos.

/** Cuántas unidades sueltas trae una unidad de conteo. null = sin equivalencia fija. */
fun countUnitsPer(unit: String): Double? = when (unit) {
    "unidad" -> 1.0
    "docena" -> 12.0
    // "saco" depende del insumo (saco de harina ≠ saco de azúcar): lo resuelve
    // el formato de compra, no una constante.
    else     -> null
}

/**
 * Convierte [value] [fromUnit] a gramos, incluyendo unidades de conteo vía
 * [unitWeightGrams] (peso de UNA unidad del insumo).
 *
 * Devuelve null cuando no hay conversión posible: unidad de conteo sin peso
 * unitario configurado, "saco", o unidad desconocida. El volumen se aproxima
 * 1 ml ≈ 1 g, como siempre lo ha hecho el resto del sistema.
 */
fun toGramsOrNull(value: Double, fromUnit: String, unitWeightGrams: Double): Double? =
    when (unitFamily(fromUnit)) {
        UnitFamily.WEIGHT, UnitFamily.VOLUME -> toBaseUnit(value, fromUnit)
        UnitFamily.COUNT -> {
            val perUnit = countUnitsPer(fromUnit)
            if (perUnit == null || unitWeightGrams <= 0.0) null
            else value * perUnit * unitWeightGrams
        }
        null -> null
    }

/**
 * Inverso de [toGramsOrNull]: pasa [grams] a [toUnit] para poder descontar del
 * stock en la unidad en que realmente se guarda el insumo (el huevo se descuenta
 * en unidades, no en gramos). Devuelve null si la conversión no es posible.
 */
fun gramsToUnitOrNull(grams: Double, toUnit: String, unitWeightGrams: Double): Double? =
    when (unitFamily(toUnit)) {
        UnitFamily.WEIGHT -> convertUnits(grams, "gr", toUnit)
        UnitFamily.VOLUME -> convertUnits(grams, "ml", toUnit)
        UnitFamily.COUNT -> {
            val perUnit = countUnitsPer(toUnit)
            val gramsPer = if (perUnit == null) 0.0 else perUnit * unitWeightGrams
            if (gramsPer <= 0.0) null else grams / gramsPer
        }
        null -> null
    }

/** Devuelve la unidad "mayor" de la lista para uso como fallback sin formato de compra. */
fun largestUnit(units: List<String>): String {
    val weightOrder = listOf("kg", "lb", "oz", "gr")
    val volumeOrder = listOf("lt", "ml")
    val countOrder  = listOf("saco", "docena", "unidad")
    return weightOrder.firstOrNull { it in units }
        ?: volumeOrder.firstOrNull { it in units }
        ?: countOrder.firstOrNull { it in units }
        ?: units.firstOrNull() ?: ""
}

/**
 * Convierte [value] de [fromUnit] a [toUnit].
 * Si son de familias distintas (ej: lb → unidad), devuelve el valor sin cambios.
 */
fun convertUnits(value: Double, fromUnit: String, toUnit: String): Double {
    if (fromUnit == toUnit) return value
    if (unitFamily(fromUnit) != unitFamily(toUnit)) return value
    val fromBase = toBaseUnit(value, fromUnit)
    val toBase   = toBaseUnit(1.0, toUnit)
    return if (toBase > 0) fromBase / toBase else value
}

private fun formatOneDecimal(value: Double): String {
    val r = (value * 10).roundToLong()
    return "${r / 10}.${r % 10}"
}

/** Muestra el stock en presentaciones (ej: "5 × Saco 100 lb") o en unidad mayor (ej: "2.0 lb"). */
fun formatStockDisplay(currentStock: Double, ingredient: Ingredient): String {
    val defaultFormat = ingredient.purchaseFormats.firstOrNull { it.isDefault }
        ?: ingredient.purchaseFormats.firstOrNull()

    return if (defaultFormat != null && defaultFormat.unitsPerPackage > 0) {
        val factor = convertUnits(defaultFormat.unitsPerPackage, defaultFormat.unit, ingredient.effectiveBaseUnit)
        val packages = if (factor > 0) currentStock / factor else 0.0
        val pkgStr = if (packages == packages.toLong().toDouble()) packages.toLong().toString()
                     else formatOneDecimal(packages)
        "$pkgStr × ${defaultFormat.description}"
    } else {
        val largest = largestUnit(ingredient.allowedUnits)
        val converted = convertUnits(currentStock, ingredient.effectiveBaseUnit, largest)
        "${formatOneDecimal(converted)} $largest"
    }
}
