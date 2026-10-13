package com.bakery.erp.model

/**
 * Empaque que se cobra en caja: la bolsa plástica, la caja de pastel. Colección
 * `companies/{c}/packagingItems`.
 *
 * NO es un [Product] a propósito. Un producto entra a producción, despacho, inventario de
 * terminado, listas de precios y al conteo de cierre; la bolsa no pasa por nada de eso, y como
 * producto quedaría para siempre como faltante en el conteo e inflaría "ventas de pan".
 * Tampoco descuenta inventario: la compra de bolsas entra como compra rápida (gasto) y su
 * existencia no se lleva — decisión del dueño, por ahora no vale la pena.
 *
 * Es opcional por empresa: la panadería que no cobra la bolsa no da de alta ninguno y el POS
 * no muestra nada. El precio es uno solo para todas las sucursales y también al revendedor.
 */
data class PackagingItem(
    val id: String = "",
    val name: String = "",
    val salePrice: Double = 0.0,
    val active: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** El nombre va en un botón del POS junto con el precio: largo no cabe. */
const val PACKAGING_NAME_MAX_CHARS = 24

/** Qué le falta a un empaque para poder guardarse; null si está bien. */
fun packagingItemProblem(item: PackagingItem): String? = when {
    item.name.isBlank() -> "Escribí el nombre del empaque"
    item.name.trim().length > PACKAGING_NAME_MAX_CHARS ->
        "El nombre no puede pasar de $PACKAGING_NAME_MAX_CHARS letras"
    item.salePrice <= 0.0 -> "El precio de venta tiene que ser mayor que cero"
    else -> null
}

/** La venta cobró al menos un empaque (la bolsa). */
val Sale.chargedPackaging: Boolean
    get() = packagingUnits > 0.0

/** Filtro del historial de ventas: "¿se cobró la bolsa?". */
enum class PackagingFilter(val label: String) {
    ALL("Todas"),
    WITH("Con empaque"),
    WITHOUT("Sin empaque")
}

/**
 * Aplica el filtro de empaque. "Sin empaque" solo mira las ventas de panadería: una cuenta del
 * restaurante nunca lleva bolsa cobrada en caja y contarla ahí sería ruido.
 */
fun List<Sale>.filterByPackaging(filter: PackagingFilter): List<Sale> = when (filter) {
    PackagingFilter.ALL -> this
    PackagingFilter.WITH -> filter { it.chargedPackaging }
    PackagingFilter.WITHOUT -> filter { !it.chargedPackaging && it.salesChannel == SalesChannel.BAKERY }
}
