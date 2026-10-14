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
    /**
     * % de descuento cuando un colaborador con derecho ([CreditAccount.cashDiscountEligible])
     * lo compra y paga en EFECTIVO en ese momento. 0 = sin descuento. Es por producto porque
     * no todo lleva: mayormente lo empacado y la reventa, y el % varía de uno a otro.
     */
    val employeeDiscountPercent: Double = 0.0,
    val unit: String = "",              // Presentación de venta (ej: "unidad", "tira de 6")
    // Datos físicos del ítem terminado
    val weightPerUnit: Double? = null,  // Peso por unidad en onzas
    val rawWeightGrams: Double? = null, // Peso en crudo (siempre en gramos)
    val rawWeightUnit: String? = null,  // Preferencia de visualización: "g" o "oz"
    val shelfLife: Int? = null,         // Vida útil en días
    val storageType: String? = null,    // Tipo de almacenamiento
    /**
     * Unidades que caben en una bandeja de horno de este producto. `0` = sin
     * configurar. Vive en el producto (no en la receta) porque el tamaño de
     * la pieza —y por tanto cuántas caben por bandeja— es del producto: de
     * una misma masa salen piezas de distinto tamaño en distintas bandejas.
     * Permite que "Registrar conteo" ofrezca contar en bandejas + sueltos
     * (como cuenta el panadero de verdad) en vez de solo el número neto.
     */
    val unitsPerTray: Int = 0,
    // Organoléptico
    val characteristics: ProductCharacteristics = ProductCharacteristics(),
    // Producción
    val technicalSheet: BakingTechnicalSheet = BakingTechnicalSheet(),
    // Adornos (preparaciones tipo DECORATION que se consumen al producir N unidades)
    val decorations: List<ProductDecoration> = emptyList(),
    /**
     * Rellenos de masa cruda de OTRA receta (ver [DoughFilling]) — no confundir
     * con [decorations]. Ej: Pan de Capa lleva una bolita de masa Suave adentro;
     * esa demanda se suma a la masa de Suave en vez de a la de Capa.
     */
    val doughFillings: List<DoughFilling> = emptyList(),
    /**
     * `true` cuando producimos el producto en planta (tiene receta y se hornea/elabora).
     * `false` para productos de reventa pura (tostadas, chocolate en barra) que compramos
     * a un proveedor y vendemos sin transformación. Los pedidos con líneas
     * `isInHouseProduced=false` saltan el lote de producción y se sirven directo del stock
     * terminado.
     */
    val isInHouseProduced: Boolean = true,
    /**
     * Presentaciones en que se le compra este producto de reventa a cada proveedor ("Caja de
     * 24 latas" a Q120 con la distribuidora, "Six pack" a Q33 con el mayorista). Misma clase y
     * mismo criterio que `Ingredient.purchaseFormats`: la relación producto↔proveedor es de
     * muchos a muchos y vive en la presentación, así que el catálogo de un proveedor se DEDUCE
     * de aquí (ver `suppliedBy`). `unit` es la presentación de venta del producto y
     * `unitsPerPackage` cuántas de ésas trae el empaque: es lo que convierte "1 caja" en las
     * 24 unidades que entran al inventario de tienda.
     *
     * Vive en el mismo documento (arreglos paralelos) y no en subcolección como la de insumos:
     * `getProducts` lo lee el POS y media app, y una subcolección sería una lectura extra por
     * cada producto — incluido todo el pan propio, que nunca lleva presentaciones.
     */
    val purchaseFormats: List<PurchaseFormat> = emptyList(),
    // Reventa: proveedor y costo de compra de referencia. Con presentaciones se DERIVAN de la
    // predeterminada al guardar (`withDerivedPurchaseReference`); se siguen escribiendo porque
    // el POS usa `purchaseCost` como costo de venta. Sin presentaciones son el dato viejo, de
    // cuando un producto solo podía tener un proveedor.
    val supplierId: String? = null,
    val supplierName: String? = null,
    val purchaseCost: Double? = null,
    /**
     * Código de barras del empaque (EAN-13 y similares). Es el MISMO código que trae
     * el fabricante, así que sirve para las dos puntas: encontrar el producto al
     * capturar la factura y encontrarlo al cobrarlo en el POS. Vacío = sin código.
     *
     * El pan propio no lleva: nadie le imprime un código a un pirujo.
     */
    val barcode: String = "",
    /**
     * Si caduca (lácteos, jugos, embutidos de reventa). Mismo criterio que
     * `Ingredient.isPerishable`: solo a éstos se les pide lote y vencimiento al capturar la
     * factura — pedírselo a una lata de gaseosa o a un chocolate en barra sería ruido en la
     * pantalla donde la velocidad de captura importa. Solo aplica a reventa: el pan propio no
     * pasa por compras.
     */
    val isPerishable: Boolean = false,
    /**
     * Insumo del que este producto saca su stock, para lo que se compra como insumo y
     * además se vende en el mostrador (el caso del huevo: entra a las recetas Y se
     * vende por docena).
     *
     * Cuando está presente, el producto NO lleva `FinishedProductStock` propio: la venta
     * descuenta el `BranchStock` del insumo y escribe el Kardex de insumos. Es un solo
     * pozo porque es una sola caja de huevos — comprarlo dos veces para tenerlo en dos
     * inventarios es justo lo que dejaba los conteos sin cuadrar.
     *
     * Vacío = producto normal, con su inventario de terminado.
     */
    val linkedIngredientId: String = "",
    val linkedIngredientName: String = "",
    /**
     * Cuántas unidades base del insumo se van con UNA venta de este producto. Una docena
     * vendida descuenta 12 del huevo, que se almacena en `unidad`. Solo aplica con
     * [linkedIngredientId]; 0 o 1 = uno a uno.
     */
    val ingredientUnitsPerSale: Double = 1.0,
    /**
     * Producto entero del que sale esta porción ("Porción de selva negra" ← "Selva negra").
     * El pastel que no se vendió entero se corta en la tienda: sale 1 del entero y entran
     * N porciones con el costo del pastel repartido (ver `portioningMovements`). La porción
     * lleva su propio precio y su propio inventario; nunca viene de planta.
     *
     * Vacío = producto normal.
     */
    val portionOfProductId: String = "",
    val portionOfProductName: String = "",
    /** Porciones que salen normalmente de un entero. Se puede cambiar al cortar. */
    val portionsPerWhole: Int = 0,
    val notes: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/**
 * `true` si el producto vende inventario de un insumo en vez del suyo propio.
 * Ver [Product.linkedIngredientId].
 */
val Product.isIngredientBacked: Boolean
    get() = linkedIngredientId.isNotEmpty()

/** `true` si el producto es la porción de otro (ver [Product.portionOfProductId]). */
val Product.isPortion: Boolean
    get() = portionOfProductId.isNotEmpty()

/** Unidades del insumo que consume una venta de N piezas. Nunca cero. */
fun Product.ingredientUnitsFor(quantity: Double): Double =
    quantity * (ingredientUnitsPerSale.takeIf { it > 0.0 } ?: 1.0)

/** Categorías predefinidas de productos producidos en planta */
val PRODUCT_CATEGORIES = listOf(
    "Panadería",
    "Repostería",
    "Pastelería",
    "Confitería"
)

/**
 * Categoría única para productos de reventa. Todo lo que se compra y se vende sin
 * transformar (huevos, café en bolsa, tostadas…) cae aquí — no tiene sentido
 * subdividirlo en Abarrotes/Bebidas/Otros porque, funcionalmente, todo es reventa.
 */
const val RESALE_CATEGORY = "Reventa"

/** Todas las categorías visibles en la lista de productos (in-house + reventa). */
val ALL_PRODUCT_CATEGORIES = PRODUCT_CATEGORIES + RESALE_CATEGORY
