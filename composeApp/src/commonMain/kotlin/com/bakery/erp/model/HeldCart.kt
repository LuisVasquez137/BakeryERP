package com.bakery.erp.model

/**
 * Cuenta en espera: el carrito de un cliente que fue por más pan mientras se atiende al
 * siguiente. Colección `companies/{c}/heldCarts`, una por carrito, ligada al turno de caja.
 *
 * Decisiones del dueño (oct 2026):
 * - Son de CADA caja (del turno, [sessionId]): el cliente vuelve a la misma cajera y cada gaveta
 *   responde por lo suyo.
 * - Van a Firestore y no solo a memoria: si la app se cierra en medio de la fila, no se pierden.
 * - No mueven inventario: el stock se mueve al cobrar, como siempre.
 * - Descartar no pide permiso pero queda en la bitácora de cajas: cobrar en efectivo, dejar la
 *   cuenta en espera y descartarla sería meter dinero a la gaveta sin venta.
 * - No se puede cerrar el turno con cuentas en espera: se cobran o se descartan antes.
 */
data class HeldCart(
    val id: String = "",
    val branchId: String = "",
    val registerId: String = "",
    val sessionId: String = "",
    /** "Señora de rojo", o "Cuenta 2" si no se escribió nada. */
    val label: String = "",
    val lines: List<HeldCartLine> = emptyList(),
    /** Venta a revendedor en curso al ponerla en espera: al retomarla se vuelve a ese modo. */
    val resellerId: String = "",
    val resellerName: String = "",
    val createdAt: Long = 0L,
    val createdByName: String = ""
)

/** Un renglón del carrito tal como estaba. Ver `CartLine` del POS. */
data class HeldCartLine(
    val key: String = "",
    val productId: String = "",
    val productName: String = "",
    val quantity: Double = 0.0,
    val unitPrice: Double = 0.0,
    val priceListId: String = "",
    val priceSource: String = "",
    val quickGroupId: String = "",
    val pendingSaleId: String = "",
    val packagingItemId: String = ""
)

val HeldCart.total: Double get() = lines.sumOf { it.quantity * it.unitPrice }

/** Para que no se vuelva un estacionamiento de carritos. */
const val MAX_HELD_CARTS = 5

/** "Cuenta N" con el menor N que no esté en uso en esta caja. */
fun nextHeldCartLabel(existing: List<HeldCart>): String {
    val used = existing.map { it.label }.toSet()
    return generateSequence(1) { it + 1 }.map { "Cuenta $it" }.first { it !in used }
}

/** "3 × Champurrada, 1 × Bolsa": lo que queda en la bitácora al descartar. */
fun HeldCart.itemsSummary(): String =
    lines.joinToString(", ") { l ->
        val q = if (l.quantity == kotlin.math.floor(l.quantity)) l.quantity.toLong().toString() else l.quantity.toString()
        "$q × ${l.productName}"
    }

/** Minutos que lleva en espera, para la ficha ("3 min"). */
fun HeldCart.minutesWaiting(nowMillis: Long): Long = ((nowMillis - createdAt).coerceAtLeast(0L)) / 60_000L
