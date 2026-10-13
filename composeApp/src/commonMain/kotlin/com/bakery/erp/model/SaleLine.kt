package com.bakery.erp.model

/**
 * Línea de venta. Subcolección de [Sale].
 *
 * Una línea vende un producto concreto ([productId]), un grupo de precio ([quickGroupId],
 * ej. "Pan Q0.50", ver [POSCategory.sellAsGroup]) o un empaque cobrado ([packagingItemId], la
 * bolsa). La de grupo no trae producto y no descuenta inventario al cobrarse: el conteo de
 * cierre reparte esas unidades entre los panes del grupo según lo que de verdad falta
 * (`allocateGroupSales`). La de empaque tampoco mueve inventario (ver [PackagingItem]).
 *
 * [costAtSale] es el snapshot del `avgUnitCost` de [FinishedProductStock] al momento de la venta,
 * para que reportes históricos de margen no se vean afectados por compras posteriores.
 */
data class SaleLine(
    val id: String = "",
    val saleId: String = "",
    val productId: String? = null,
    val productName: String = "",
    val quickGroupId: String? = null,
    val quickGroupName: String? = null,
    val quantity: Double = 0.0,
    val unitPrice: Double = 0.0,
    val subtotal: Double = 0.0,
    val costAtSale: Double = 0.0,
    val priceListId: String = "",
    val priceSource: String = SaleLinePriceSource.PRODUCT_SALE_PRICE,
    /** true si se aplicó descuento al inventario al confirmar la venta. */
    val stockDeducted: Boolean = false,
    // ── Producto respaldado por un insumo (el huevo) ─────────────────────────
    /**
     * Insumo del que salió el stock, cuando el producto vende del inventario de insumos
     * en vez del de terminado (ver `Product.linkedIngredientId`). Vacío = venta normal.
     *
     * Es un SNAPSHOT y no una lectura del catálogo a propósito, igual que `priceSource`:
     * la venta tiene que seguir diciendo de dónde salió el pan aunque mañana se desvincule
     * el producto. Y de paso evita que el repositorio de ventas tenga que conocer el
     * catálogo de productos para decidir a qué inventario pegarle.
     */
    val linkedIngredientId: String = "",
    val linkedIngredientName: String = "",
    /** Unidades base del insumo que consumió UNA pieza vendida. 0 = uno a uno. */
    val ingredientUnitsPerSale: Double = 0.0,
    /** Empaque cobrado (bolsa, caja). Sin producto y sin inventario. Ver [PackagingItem]. */
    val packagingItemId: String? = null,
    /**
     * Recargo por pagar con tarjeta ([CardPaymentRules]). Va en el ticket, pero NO descrito en
     * la factura: el servidor lo reparte entre los productos (`absorberRecargo`).
     */
    val cardSurcharge: Boolean = false
)

/** `true` si la línea cobra un empaque (la bolsa) y no pan. */
val SaleLine.isPackaging: Boolean
    get() = !packagingItemId.isNullOrEmpty()

/** `true` si la línea descuenta inventario de insumos en vez de terminado. */
val SaleLine.isIngredientBacked: Boolean
    get() = linkedIngredientId.isNotEmpty()

/** Unidades del insumo que consume esta línea. Nunca cero si hay insumo detrás. */
val SaleLine.ingredientUnitsConsumed: Double
    get() = quantity * (ingredientUnitsPerSale.takeIf { it > 0.0 } ?: 1.0)

object SaleLinePriceSource {
    /** Override explícito en `ProductPrice` para la lista RETAIL. */
    const val PRODUCT_OVERRIDE   = "PRODUCT_OVERRIDE"
    /** Calculado aplicando `priceFactor` al `Product.salePrice`. */
    const val LIST_FACTOR        = "LIST_FACTOR"
    /** Usó `Product.salePrice` directamente (sin override ni factor). */
    const val PRODUCT_SALE_PRICE = "PRODUCT_SALE_PRICE"
    /** Línea de grupo de precio ([POSCategory.sellAsGroup]) — cobra `groupUnitPrice`. */
    const val QUICK_GROUP        = "QUICK_GROUP"
    /** Empaque cobrado ([PackagingItem.salePrice]): mismo precio en toda sucursal y cliente. */
    const val PACKAGING          = "PACKAGING"
    /** Recargo por pago con tarjeta, calculado con [CardPaymentRules]. */
    const val CARD_SURCHARGE     = "CARD_SURCHARGE"
}

/** Clave con la que una línea de grupo entra al desglose por producto de los reportes. */
fun groupSaleKey(groupId: String): String = "grp_$groupId"

/**
 * Clave con la que un empaque entra al desglose por producto de los reportes. Va aparte del
 * pan a propósito: una bolsa no es una pieza vendida.
 */
fun packagingSaleKey(itemId: String): String = "pkg_$itemId"

/** `true` si la clave del desglose es de un empaque y no de pan. */
fun isPackagingSaleKey(key: String): Boolean = key.startsWith("pkg_")
