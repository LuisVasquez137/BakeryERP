package com.bakery.erp.model

data class ProductCharacteristics(
    val crustColor: String? = null,
    val crumbColor: String? = null,
    val texture: String? = null,
    val shape: String? = null,
    val aroma: String? = null,
    val flavorProfile: String? = null
)

/** Ficha técnica de producción — parámetros para mantener estándar de calidad. */
data class BakingTechnicalSheet(
    val bakingTemperatureCelsius: Int? = null,
    val bakingTimeMinutes: Int? = null,
    val fermentationTimeHours: Double? = null,
    val fermentationTemperatureCelsius: Int? = null,
    val hydrationPercentage: Double? = null, // % de agua sobre harina
    val shapingNotes: String? = null,
    val bakingNotes: String? = null
)

data class Product(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val imageUrl: String? = null,
    val active: Boolean = true,
    // Datos comerciales
    val salePrice: Double = 0.0,        // Precio por presentación, en Q
    val unit: String = "",              // Presentación de venta (ej: "unidad", "tira de 6")
    // Datos físicos del ítem terminado
    val weightPerUnit: Double? = null,  // Peso por unidad en onzas
    val rawWeightGrams: Double? = null, // Peso en crudo (siempre en gramos)
    val rawWeightUnit: String? = null,  // Preferencia de visualización: "g" o "oz"
    val shelfLife: Int? = null,         // Vida útil en días
    val storageType: String? = null,    // Tipo de almacenamiento
    // Organoléptico
    val characteristics: ProductCharacteristics = ProductCharacteristics(),
    // Producción
    val technicalSheet: BakingTechnicalSheet = BakingTechnicalSheet(),
    // Adornos (preparaciones tipo DECORATION que se consumen al producir N unidades)
    val decorations: List<ProductDecoration> = emptyList(),
    /**
     * `true` cuando producimos el producto en planta (tiene receta y se hornea/elabora).
     * `false` para productos de reventa pura (tostadas, chocolate en barra) que compramos
     * a un proveedor y vendemos sin transformación. Los pedidos con líneas
     * `isInHouseProduced=false` saltan el lote de producción y se sirven directo del stock
     * terminado.
     */
    val isInHouseProduced: Boolean = true,
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
