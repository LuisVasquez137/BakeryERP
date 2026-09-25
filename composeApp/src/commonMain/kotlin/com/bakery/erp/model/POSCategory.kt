package com.bakery.erp.model

/**
 * Categoría de POS — agrupa productos para acceso rápido en la pantalla de venta.
 *
 * Eje exclusivo de UX (distinto de [Product.category] que clasifica para producción).
 * Normalmente cada producto se vende a su propio [Product.salePrice]. Con [sellAsGroup] la
 * categoría se cobra como UN solo botón ("10 panes de Q0.50") sin elegir el tipo de pan.
 *
 * Un producto pertenece a lo sumo a una categoría entre las que comparten sucursal
 * ([productCategoryConflicts]): si estuviera en dos, no se sabría a qué grupo repartirle la
 * venta en el conteo, y la lista para categorizar dejaría de decir qué falta.
 *
 * [name] limitado a [POS_CATEGORY_NAME_MAX_CHARS] caracteres: la fila de chips del POS se
 * desliza de lado, pero un nombre más largo que eso ya obliga a desplazarla para ver el resto.
 * [branchIds] vacío = aplica en todas las sucursales; con valores = solo en esas sucursales.
 */
data class POSCategory(
    val id: String = "",
    val name: String = "",
    val productIds: List<String> = emptyList(),
    /** Sucursales donde aplica esta categoría. Vacío = aplica en todas. */
    val branchIds: List<String> = emptyList(),
    /** Color del chip como hex (ej. "#0D7A8C"). */
    val color: String = "#0D7A8C",
    val sortOrder: Int = 0,
    val active: Boolean = true,
    /**
     * Se cobra como un solo botón a [groupUnitPrice]: la cajera teclea cuántos panes y no
     * cuáles. La venta no descuenta ningún producto; el conteo de cierre reparte esas
     * unidades entre [productIds] según lo que falta (`allocateGroupSales`).
     */
    val sellAsGroup: Boolean = false,
    /**
     * Precio por unidad del grupo. Solo entran productos con este mismo `salePrice`, así el
     * precio del grupo nunca contradice el catálogo.
     */
    val groupUnitPrice: Double = 0.0,
    /**
     * Cómo se llama el grupo en el ticket, el carrito y los reportes ("Pan de Q0.50"). El
     * [name] es el chip del POS; vacío = se arma del precio.
     */
    val groupLabel: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** Máximo de caracteres permitido para el nombre de una categoría POS. */
const val POS_CATEGORY_NAME_MAX_CHARS = 15

/** Colores predefinidos sugeridos para categorías POS. */
val POS_CATEGORY_COLORS = listOf(
    "#0D7A8C", // teal
    "#1A56A0", // blue
    "#FF9800", // naranja de marca
    "#2E4A6E", // coffee
    "#6B8E23", // olive
    "#A0522D", // sienna
    "#8B4513", // saddle brown
    "#C71585"  // magenta
)

/** `true` si las dos categorías se muestran en al menos una sucursal en común (vacío = todas). */
fun POSCategory.sharesBranchWith(other: POSCategory): Boolean =
    branchIds.isEmpty() || other.branchIds.isEmpty() || branchIds.any { it in other.branchIds }

/**
 * productId → categoría que ya lo tiene, entre las OTRAS categorías que comparten sucursal
 * con [candidate]. Es lo que el formulario esconde y lo que el guardado revalida.
 */
fun productCategoryConflicts(
    candidate: POSCategory,
    all: List<POSCategory>
): Map<String, POSCategory> {
    val result = mutableMapOf<String, POSCategory>()
    all.filter { it.id != candidate.id && it.id.isNotEmpty() && it.sharesBranchWith(candidate) }
        .forEach { other -> other.productIds.forEach { pid -> if (pid !in result) result[pid] = other } }
    return result
}

/** Nombre del grupo para ticket, carrito y reportes. */
val POSCategory.groupDisplayName: String
    get() = groupLabel.trim().ifEmpty {
        "Pan de Q" + ((groupUnitPrice * 100).toLong().let { c -> "${c / 100}.${(c % 100).toString().padStart(2, '0')}" })
    }

/** El precio de venta coincide con el del grupo (tolerancia de centavo por redondeo). */
fun matchesGroupPrice(salePrice: Double, groupUnitPrice: Double): Boolean =
    kotlin.math.abs(salePrice - groupUnitPrice) < 0.005
