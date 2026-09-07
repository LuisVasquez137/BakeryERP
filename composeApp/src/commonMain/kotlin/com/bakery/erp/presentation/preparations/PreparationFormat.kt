package com.bakery.erp.presentation.preparations

import com.bakery.erp.model.convertUnits
import com.bakery.erp.util.fmt

/** Muestra un peso (en gramos) en una unidad legible para panadería (lb). */
fun formatBatchWeight(grams: Double): String {
    if (grams <= 0.0) return "—"
    val lb = convertUnits(grams, "gr", "lb")
    return "${lb.fmt(2)} lb"
}
