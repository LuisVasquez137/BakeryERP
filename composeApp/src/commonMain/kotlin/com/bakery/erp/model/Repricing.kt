package com.bakery.erp.model

import kotlin.math.ceil

/**
 * Qué precio de venta proponer cuando al proveedor le subió el costo.
 *
 * El costo de una presentación se actualiza sola al registrar la factura
 * (`updatePurchaseFormatCost` / `syncResaleFormatPrices`), pero el precio de venta **no lo
 * tocaba nadie**: la caja de gaseosas pasaba de Q120 a Q126 y el margen se achicaba en
 * silencio, factura tras factura, hasta que alguien lo notaba meses después mirando un
 * reporte. Esto no sube nada solo — propone un número en el único momento en que la
 * información está sobre la mesa, que es cuando se acaba de teclear la factura.
 */

/** El escalón de precio que la caja puede dar de vuelto sin pensar. */
const val PRICE_STEP = 0.50

/**
 * Cuánto tiene que superar el precio ideal al actual para molestar al usuario.
 *
 * Es la mitad del escalón, y existe porque las dos reglas del redondeo —siempre para arriba,
 * de Q0.50 en Q0.50— se llevan mal con los aumentos chicos: un costo que sube Q0.05 mueve el
 * precio ideal de Q10.00 a Q10.07, y redondeando hacia arriba eso propondría Q10.50. Sería
 * subir el precio 5% porque el costo subió 0.7%, y un aviso que exagera se empieza a cerrar
 * sin leer — que es la forma en que mueren las alertas.
 *
 * El amortiguador decide **si** se avisa, nunca **cuánto** se sube: cuando el aviso sale, el
 * redondeo sigue siendo hacia arriba, sin excepción.
 */
const val REPRICE_BUFFER = 0.25

/** Redondea hacia arriba al siguiente múltiplo de [PRICE_STEP]. Nunca hacia abajo: para abajo
 *  se come el margen que este cálculo existe para defender. */
fun roundUpToPriceStep(value: Double): Double {
    if (value <= 0.0) return 0.0
    // El épsilon evita que un valor ya exacto (10.50) salte al escalón siguiente por el error
    // de coma flotante que arrastra haber dividido y multiplicado antes.
    return ceil(value / PRICE_STEP - 1e-9) * PRICE_STEP
}

/**
 * Precio sugerido para conservar el mismo margen, o `null` si no hay nada que proponer.
 *
 * El margen que se preserva es el que la app YA muestra en la ficha del producto,
 * `(precio − costo) ÷ precio` —margen sobre venta, no recargo sobre costo—. Preservarlo se
 * reduce a una regla de tres: `precio × (costoNuevo ÷ costoViejo)`. Usar la otra definición
 * daría un número distinto del porcentaje que el usuario está viendo en pantalla.
 *
 * Devuelve null cuando el costo no subió, cuando falta algún dato para dividir, cuando el
 * aumento no llega al [REPRICE_BUFFER], o cuando el precio redondeado es el que ya tenía.
 */
fun suggestedSalePrice(
    currentPrice: Double,
    oldCost: Double,
    newCost: Double
): Double? {
    if (currentPrice <= 0.0 || oldCost <= 0.0 || newCost <= 0.0) return null
    if (newCost <= oldCost) return null
    val ideal = currentPrice * (newCost / oldCost)
    if (ideal - currentPrice <= REPRICE_BUFFER) return null
    val rounded = roundUpToPriceStep(ideal)
    return rounded.takeIf { it > currentPrice }
}

/**
 * Un producto al que le subió el costo, con el precio que se propone.
 *
 * [suggestedPrice] ya viene redondeado; [currentPrice] y los márgenes van para que el
 * diálogo pueda mostrar de dónde sale el número — una sugerencia de precio que no explica
 * su origen no se aplica, se cierra.
 */
data class RepriceSuggestion(
    val productId: String,
    val productName: String,
    val oldCost: Double,
    val newCost: Double,
    val currentPrice: Double,
    val suggestedPrice: Double
) {
    /** Margen sobre venta con el costo nuevo y el precio de hoy: lo que se está perdiendo. */
    val currentMarginPct: Double
        get() = if (currentPrice > 0.0) (currentPrice - newCost) / currentPrice * 100.0 else 0.0

    /** Margen sobre venta si se aplica la sugerencia. */
    val suggestedMarginPct: Double
        get() = if (suggestedPrice > 0.0) (suggestedPrice - newCost) / suggestedPrice * 100.0 else 0.0

    /** Cuánto subió el costo, en por ciento. */
    val costIncreasePct: Double
        get() = if (oldCost > 0.0) (newCost - oldCost) / oldCost * 100.0 else 0.0
}

/**
 * Arma la sugerencia de un producto cuyo costo cambió, o null si no aplica.
 *
 * **Solo reventa.** La materia prima no tiene precio de venta propio —el precio del pan sale
 * de la receta y del costeo, no de lo que costó el saco de harina— y el suministro no se vende.
 * Ojo con el producto respaldado por insumo (la leche, el huevo): ÉSE sí entra, aunque su línea
 * de compra sea de tipo insumo, porque es un producto de reventa con su precio en el mostrador.
 * Por eso esto recibe el producto y no la línea.
 */
fun repriceSuggestionFor(
    product: Product,
    oldCost: Double,
    newCost: Double
): RepriceSuggestion? {
    if (product.isInHouseProduced) return null
    val suggested = suggestedSalePrice(product.salePrice, oldCost, newCost) ?: return null
    return RepriceSuggestion(
        productId = product.id,
        productName = product.name,
        oldCost = oldCost,
        newCost = newCost,
        currentPrice = product.salePrice,
        suggestedPrice = suggested
    )
}
