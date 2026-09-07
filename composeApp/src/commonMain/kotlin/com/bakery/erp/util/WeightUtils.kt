package com.bakery.erp.util

const val GRAMS_PER_OUNCE = 28.3495
const val GRAMS_PER_POUND_W = 453.592

/**
 * Umbral de VISTA para pasar de gramos a onzas — no es lo mismo que una onza
 * exacta ([GRAMS_PER_OUNCE], que sigue siendo el factor de conversión real).
 * Por debajo de esto (sal, levadura en amasadas chicas) los gramos se leen
 * mejor que una fracción de onza.
 */
const val GRAMS_TO_OZ_DISPLAY_THRESHOLD = 16.0

fun Double.ozToGrams(): Double = this * GRAMS_PER_OUNCE
fun Double.gramsToOz(): Double = this / GRAMS_PER_OUNCE

/**
 * Peso como lo lee un panadero de acá: onzas y libras, con gramos solo para lo
 * que no llega a [GRAMS_TO_OZ_DISPLAY_THRESHOLD] (sal, levadura). Internamente
 * todo se guarda en gramos por exactitud; esto es solo presentación.
 */
fun formatBakerWeight(grams: Double): String = when {
    grams <= 0.0 -> "—"
    grams < GRAMS_TO_OZ_DISPLAY_THRESHOLD -> "${grams.fmtTrim(1)} g"
    grams < GRAMS_PER_POUND_W -> "${(grams / GRAMS_PER_OUNCE).fmtTrim(2)} oz"
    else -> "${(grams / GRAMS_PER_POUND_W).fmtTrim(2)} lb"
}
