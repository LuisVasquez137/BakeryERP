package com.bakery.erp.model

import com.bakery.erp.util.fmtTrim

/**
 * Cortar en la tienda pasteles enteros que no se vendieron ("Selva negra" → 8 × "Porción de
 * selva negra"). Es una transformación del inventario de tienda, no una venta ni una merma:
 * sale el entero y entran las porciones con el costo del pastel repartido, así el margen de
 * la porción sale bien y los reportes de merma/ajuste no se ensucian con cada corte.
 *
 * No se deshace (un pastel cortado no se vuelve a pegar); si se equivocan se corrige con un
 * ajuste, como todo el Kardex.
 */
data class PortioningRequest(
    val branchId: String,
    val branchName: String,
    /** El producto porción; su [Product.portionOfProductId] dice de qué entero sale. */
    val portion: Product,
    /** Enteros que se cortan. */
    val wholes: Int,
    /** Porciones que salieron de CADA entero (precargado con [Product.portionsPerWhole]). */
    val portionsPerWhole: Int,
    val userId: String,
    val userName: String,
    val note: String? = null
) {
    val totalPortions: Int get() = wholes * portionsPerWhole
}

/**
 * Por qué no se puede cortar, o `null` si se puede. [wholeStock] = existencias actuales del
 * entero en la sucursal (null = nunca tuvo movimiento).
 *
 * No deja cortar más de lo que el sistema dice que hay: el costo de las porciones sale del
 * entero, y un entero en negativo daría porciones sin costo y un inventario que no cuadra.
 * Si el pastel está en el estante pero no en el sistema, primero se ajusta el entero.
 */
fun portioningError(request: PortioningRequest, wholeStock: FinishedProductStock?): String? {
    val p = request.portion
    return when {
        !p.isPortion -> "${p.name} no está configurado como porción de otro producto"
        request.wholes < 1 -> "Indica cuántos enteros vas a cortar"
        request.portionsPerWhole < 1 -> "Indica cuántas porciones salen de cada uno"
        (wholeStock?.currentStock ?: 0.0) < request.wholes ->
            "Según el inventario hay ${(wholeStock?.currentStock ?: 0.0).fmtTrim()} de " +
                "${p.portionOfProductName} en esta tienda. Si hay más en el estante, primero " +
                "ajusta el inventario del entero."
        else -> null
    }
}

/**
 * Los dos movimientos del corte, en el orden en que se aplican: primero sale el entero y
 * después entran las porciones (si el segundo falla, lo que queda es un entero de menos —
 * visible en el Kardex con su referencia— y no porciones fantasma con costo cero).
 *
 * El entero sale a su costo promedio y cada porción entra a ese costo ÷ porciones, así el
 * valor del inventario no cambia al cortar.
 */
fun portioningMovements(
    request: PortioningRequest,
    wholeStock: FinishedProductStock,
    referenceId: String
): List<FinishedStockMovement> {
    val p = request.portion
    val wholeCost = wholeStock.avgUnitCost
    val reason = buildString {
        append("${request.wholes} × ${request.portionsPerWhole} porciones")
        request.note?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" — $it") }
    }
    val base = FinishedStockMovement(
        branchId = request.branchId,
        branchName = request.branchName,
        type = FinishedMovementType.PORCIONADO,
        referenceType = FinishedMovementReference.PORTIONING,
        referenceId = referenceId,
        reason = reason,
        createdByUserId = request.userId,
        createdByUserName = request.userName
    )
    return listOf(
        base.copy(
            productId = p.portionOfProductId,
            productName = p.portionOfProductName,
            quantity = -request.wholes.toDouble(),
            unitPriceAtMovement = wholeCost
        ),
        base.copy(
            productId = p.id,
            productName = p.name,
            quantity = request.totalPortions.toDouble(),
            unitPriceAtMovement = wholeCost / request.portionsPerWhole
        )
    )
}


/**
 * ¿Ofrecer cortar un entero al cobrar? Sí cuando la cajera mete una porción al carrito y ya
 * no alcanzan las porciones del inventario pero hay enteros: es el momento en que de verdad
 * está cortando el pastel, y si no se registra ahí la porción se vende en negativo.
 *
 * [quantityInCart] = lo que lleva la línea DESPUÉS de agregar.
 */
fun shouldOfferPortioning(
    product: Product,
    quantityInCart: Double,
    portionStock: Double,
    wholeStock: Double
): Boolean = product.isPortion && quantityInCart > portionStock && wholeStock >= 1.0
