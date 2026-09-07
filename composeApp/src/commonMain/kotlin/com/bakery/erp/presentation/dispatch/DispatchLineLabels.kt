package com.bakery.erp.presentation.dispatch

import com.bakery.erp.model.DispatchNoteLine
import com.bakery.erp.presentation.production.RecipeRef
import com.bakery.erp.util.fmtTrim
import com.bakery.erp.util.gramsToOz

/**
 * Nombre de una línea de despacho tal como se lee en empaque, matriz, recepción
 * y detalle.
 *
 * Un pedido especial lleva ★ y **su peso en onzas**: "el pirujo del doble" y un
 * pirujo normal se llaman casi igual, y quien empaca o cuenta en la sucursal no
 * tiene a mano el pedido original para distinguirlos. El peso es justamente lo
 * que se le pidió a la encargada al anotarlo, así que mostrarlo no cuesta nada
 * y cierra el circuito.
 *
 * En onzas y no en gramos porque es la unidad en que se pide y se piensa el pan
 * acá; internamente sigue guardado en gramos (ver
 * [com.bakery.erp.model.DispatchNoteLine.specialRawWeightGrams]).
 */
internal fun dispatchLineLabel(
    productName: String,
    isSpecial: Boolean,
    specialRawWeightGrams: Double
): String {
    if (!isSpecial) return productName
    val weight = specialRawWeightGrams
        .takeIf { it > 0.0 }
        ?.let { " (${it.gramsToOz().fmtTrim(1)} oz)" }
        .orEmpty()
    return "★ $productName$weight"
}

/**
 * Masa a la que pertenece una línea de despacho, para el agrupado de las
 * pantallas.
 *
 * El especial manda: su masa viene en la propia línea porque su `productId` es
 * sintético y nunca va a estar en [refs], que se arma con los productos del
 * catálogo. Lo demás se resuelve como siempre.
 */
internal fun dispatchLineRecipeId(line: DispatchNoteLine, refs: Map<String, RecipeRef>): String? =
    line.specialRecipeId.takeIf { it.isNotEmpty() } ?: refs[line.productId]?.id

internal fun dispatchLineRecipeName(line: DispatchNoteLine, refs: Map<String, RecipeRef>): String? =
    line.specialRecipeName.takeIf { it.isNotEmpty() } ?: refs[line.productId]?.name
