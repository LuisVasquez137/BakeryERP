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
