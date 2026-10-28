package com.bakery.erp.model

import com.bakery.erp.util.fmtTrim

/**
 * Un platillo del restaurante (ComandaYa) y el pan del mostrador que lleva (oct 2026).
 *
 * La cocina toma el pirujo del chicken sandwich del mostrador; sin esto el conteo de cierre lo
 * ve como faltante. Al llegar la cuenta, el servidor (`functions/externalSales.js` +
 * `restaurantBread.js`) descuenta [breads] × cantidad del inventario de tienda de la sucursal
 * como [FinishedMovementType.CONSUMO_RESTAURANTE].
 *
 * Firestore `companies/{c}/restaurantDishes/{externalItemId}`. El id es el del platillo en
 * ComandaYa, no el nombre: renombrarlo allá no rompe el vínculo. El servidor crea el documento
 * la primera vez que llega el platillo ([dishName], [lastSeenAt]) y BakeOS solo escribe el pan,
 * en arreglos paralelos (GitLive no guarda listas de objetos).
 */
data class RestaurantDish(
    val externalItemId: String = "",
    val dishName: String = "",
    val breads: List<RestaurantDishBread> = emptyList(),
    /** Última vez que llegó en una cuenta: los que ya no se venden se van al fondo. */
    val lastSeenAt: Long = 0L
) {
    val hasBread: Boolean get() = breads.any { it.quantity > 0.0 }
}

data class RestaurantDishBread(
    val productId: String = "",
    val productName: String = "",
    /** Panes por platillo. */
    val quantity: Double = 0.0
)

/** "1 Pirujo con punta · 2 Pan molde". */
fun RestaurantDish.breadSummary(): String =
    breads.filter { it.quantity > 0.0 }.joinToString(" · ") {
        "${it.quantity.fmtTrim()} ${it.productName}"
    }
