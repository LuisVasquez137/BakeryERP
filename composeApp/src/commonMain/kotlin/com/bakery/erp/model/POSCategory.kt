package com.bakery.erp.model

/**
 * Categoría de POS — agrupa productos para acceso rápido en la pantalla de venta.
 *
 * Eje exclusivo de UX (distinto de [Product.category] que clasifica para producción).
 * Cada producto del grupo se vende a su propio [Product.salePrice] (no hay precio único de categoría).
 *
 * [name] limitado a 4 caracteres para que el chip quepa cómodo en pantalla del POS.
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
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** Máximo de caracteres permitido para el nombre de una categoría POS. */
const val POS_CATEGORY_NAME_MAX_CHARS = 4

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
