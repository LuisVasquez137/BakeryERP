package com.bakery.erp.model

/**
 * Formato en que se compra un ingrediente.
 * Permite comprar en diferentes presentaciones (saco 50 lb, libra suelta, etc.)
 * con distintos proveedores y precios, midiendo siempre en la misma unidad base.
 */
data class PurchaseFormat(
    val id: String = "",
    val description: String = "",        // "Saco de 50 lbs", "Libra suelta"
    val unit: String = "",               // Unidad de medida de esta presentación (ej: lb, gr)
    val supplierId: String = "",
    val supplierName: String = "",       // Desnormalizado para display rápido
    val unitsPerPackage: Double = 1.0,   // Cuántas unidades trae el paquete (en `unit`)
    val packageCost: Double = 0.0,       // Costo del paquete en Q
    val isDefault: Boolean = false       // Formato preferido para calcular costos
) {
    /** Costo por unidad calculado automáticamente */
    val costPerUnit: Double
        get() = if (unitsPerPackage > 0) packageCost / unitsPerPackage else 0.0
}

data class Ingredient(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val allowedUnits: List<String> = emptyList(),  // Unidades en que se puede medir este ingrediente
    val purchaseFormats: List<PurchaseFormat> = emptyList(),
    val stockAlert: Double? = null,
    val notes: String? = null,
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    /** Primera unidad — usada como referencia en stock y alertas */
    val effectiveBaseUnit: String
        get() = allowedUnits.firstOrNull() ?: ""

    /** Costo vigente por unidad, según el formato predeterminado */
    val defaultUnitCost: Double
        get() {
            val fmt = purchaseFormats.firstOrNull { it.isDefault } ?: purchaseFormats.firstOrNull()
            return fmt?.costPerUnit ?: 0.0
        }
}

/** Categorías predefinidas de materias primas para panadería */
val INGREDIENT_CATEGORIES = listOf(
    "Harinas",
    "Grasas",
    "Azúcares",
    "Lácteos",
    "Levaduras",
    "Huevos",
    "Frutas y Rellenos",
    "Coberturas",
    "Especias y Saborizantes",
    "Empaques",
    "Otros"
)

/** Unidades de medida predefinidas */
val INGREDIENT_UNITS = listOf(
    "lb",
    "kg",
    "oz",
    "gr",
    "lt",
    "ml",
    "unidad",
    "docena",
    "saco"
)
