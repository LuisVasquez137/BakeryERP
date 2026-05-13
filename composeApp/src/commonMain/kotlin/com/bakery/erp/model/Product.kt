package com.bakery.erp.model

data class ProductCharacteristics(
    val crustColor: String? = null,
    val crumbColor: String? = null,
    val texture: String? = null,
    val shape: String? = null,
    val aroma: String? = null,
    val flavorProfile: String? = null
)

data class Product(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val imageUrl: String? = null,
    val active: Boolean = true,
    // Datos comerciales
    val salePrice: Double = 0.0,        // Precio por unidad mínima, en Q
    val unit: String = "",              // Descripción de unidad mínima (ej: "unidad", "tira de 6")
    // Datos físicos del ítem terminado
    val weightPerUnit: Double? = null,  // Peso por unidad en onzas
    val rawWeightGrams: Double? = null, // Peso en crudo sin relleno (gramos)
    val shelfLife: Int? = null,         // Vida útil en días
    val storageType: String? = null,    // Tipo de almacenamiento
    // Organoléptico
    val characteristics: ProductCharacteristics = ProductCharacteristics(),
    val notes: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** Categorías predefinidas de productos de panadería */
val PRODUCT_CATEGORIES = listOf(
    "Panadería",
    "Repostería",
    "Pastelería"
)
