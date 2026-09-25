package com.bakery.erp.model

/**
 * Presentaciones de compra de un producto de reventa. Ver [Product.purchaseFormats].
 *
 * Lógica pura: la usan el formulario de producto, la captura de la factura y el ViewModel de
 * compras, y la conversión caja → unidades es exactamente el número que no puede salir mal.
 */

/**
 * `true` si a [supplierId] se le compra este producto.
 *
 * Con presentaciones manda la presentación. Un producto viejo sin ninguna cae al
 * `supplierId` único que tenía antes: sin ese fallback, todos los productos de reventa ya
 * cargados desaparecerían del catálogo de su proveedor el día del cambio.
 */
fun Product.suppliedBy(supplierId: String): Boolean {
    if (supplierId.isEmpty()) return false
    return if (purchaseFormats.isNotEmpty()) purchaseFormats.any { it.supplierId == supplierId }
    else this.supplierId == supplierId
}

/**
 * Presentaciones que se le pueden elegir a [supplierId] al capturar su factura: las suyas,
 * con la predeterminada primero. Las que no tienen proveedor declarado también cuentan —
 * "Caja de 24" es una caja de 24 venga de quien venga.
 */
fun Product.formatsForSupplier(supplierId: String): List<PurchaseFormat> =
    purchaseFormats
        .filter { it.supplierId == supplierId || it.supplierId.isEmpty() }
        .sortedWith(compareByDescending<PurchaseFormat> { it.supplierId == supplierId }.thenByDescending { it.isDefault })

/** Presentación que se ofrece primero al capturar. `null` = se compra por unidad. */
fun Product.defaultFormatForSupplier(supplierId: String): PurchaseFormat? =
    formatsForSupplier(supplierId).firstOrNull()

/** Presentación predeterminada del producto, sin importar el proveedor. */
val Product.defaultPurchaseFormat: PurchaseFormat?
    get() = purchaseFormats.firstOrNull { it.isDefault } ?: purchaseFormats.firstOrNull()

/**
 * Deja `purchaseCost`, `supplierId` y `supplierName` iguales a la presentación predeterminada.
 *
 * No son datos independientes: son la caché de "a cuánto me cuesta UNA unidad" que lee el POS
 * para el costo de venta, y dejarlos tecleables aparte haría que el margen y el costo de venta
 * digan cosas distintas en cuanto alguien actualice el precio de la caja. Sin presentaciones
 * no se toca nada: es un producto viejo y esos campos son todo lo que tiene.
 */
fun Product.withDerivedPurchaseReference(): Product {
    val fmt = defaultPurchaseFormat ?: return this
    return copy(
        purchaseFormats = purchaseFormats.normalizedDefault(),
        purchaseCost = fmt.costPerUnit.takeIf { it > 0.0 } ?: purchaseCost,
        supplierId = fmt.supplierId.ifEmpty { null },
        supplierName = fmt.supplierName.ifEmpty { null }
    )
}

/** Exactamente una predeterminada: la marcada, o la primera si ninguna lo está. */
fun List<PurchaseFormat>.normalizedDefault(): List<PurchaseFormat> {
    if (isEmpty()) return this
    val defaultId = firstOrNull { it.isDefault }?.id ?: first().id
    return map { it.copy(isDefault = it.id == defaultId) }
}

/**
 * Convierte el producto viejo —un proveedor y un costo por unidad— en su primera presentación,
 * para que el formulario lo muestre como lo que es y guardarlo migre el dato sin script.
 * Si ya tiene presentaciones, o no tiene nada que convertir, devuelve la lista tal cual.
 */
fun Product.purchaseFormatsForEditing(newId: () -> String): List<PurchaseFormat> {
    if (purchaseFormats.isNotEmpty()) return purchaseFormats
    val cost = purchaseCost ?: 0.0
    if (supplierId.isNullOrEmpty() && cost <= 0.0) return emptyList()
    return listOf(
        PurchaseFormat(
            id = newId(),
            description = unit.ifBlank { "Unidad" }.replaceFirstChar { it.uppercase() },
            unit = unit.ifBlank { "unidad" },
            supplierId = supplierId.orEmpty(),
            supplierName = supplierName.orEmpty(),
            unitsPerPackage = 1.0,
            packageCost = cost,
            isDefault = true
        )
    )
}

/**
 * Línea de compra de reventa.
 *
 * Con [format], lo tecleado son EMPAQUES: 2 cajas de 24 a Q120 entran como 48 unidades a
 * Q5.00 cada una. `quantityReceived` va en unidades de venta porque eso es lo que se cuenta
 * en la tienda y lo que descuenta el POS; `packageCost × packagesReceived` sigue dando el
 * importe exacto de la factura (`lineTotal`), sin redondeos por dividir primero.
 *
 * Sin [format] se compra por unidad suelta y [price] es el costo de UNA: el comportamiento
 * de siempre, que se conserva para el producto sin presentaciones y para cuando el
 * repartidor deja seis latas sueltas.
 */
fun resalePurchaseLine(
    product: Product,
    format: PurchaseFormat?,
    quantity: Double,
    price: Double,
    linkedIngredient: Ingredient? = null
): PurchaseLine {
    val qty = quantity.coerceAtLeast(0.0)
    val cost = price.coerceAtLeast(0.0)
    val line = if (format == null || format.unitsPerPackage <= 0.0) {
        PurchaseLine(
            lineKind = PurchaseLineKind.RESALE_PRODUCT,
            productId = product.id,
            productName = product.name,
            ingredientUnit = product.unit,
            quantityReceived = qty,
            packagesReceived = qty,
            packageDescription = product.unit,
            unitCost = cost,
            packageCost = cost
        )
    } else {
        PurchaseLine(
            lineKind = PurchaseLineKind.RESALE_PRODUCT,
            productId = product.id,
            productName = product.name,
            ingredientUnit = product.unit,
            quantityReceived = qty * format.unitsPerPackage,
            packagesReceived = qty,
            packageDescription = format.description,
            unitCost = cost / format.unitsPerPackage,
            packageCost = cost,
            formatId = format.id
        )
    }
    return line.towardsLinkedIngredient(product, linkedIngredient)
}

/**
 * Manda la línea al inventario del insumo vinculado, cuando el producto vende de ese pozo.
 *
 * Es el caso de la leche: entra a las recetas y además se vende en el mostrador. El POS le
 * descuenta `BranchStock` al insumo (ver `Product.linkedIngredientId`) y el inventario de
 * tienda ni siquiera dibuja su `FinishedProductStock` — así que una compra que entrara como
 * reventa se guardaba en un pozo que nadie mira y que ninguna venta descuenta: la leche
 * desaparecía del sistema en silencio, y si además se capturó el insumo quedaba contada dos
 * veces. Por eso la redirección vive acá, en el ÚNICO lugar que arma una línea de reventa,
 * y no en cada pantalla que la ofrece.
 *
 * Lo que cambia es el destino, no el papel: `packagesReceived`, `packageCost` y `formatId`
 * se conservan, así `lineTotal` sigue dando el importe exacto de la factura y la
 * presentación sigue actualizando su precio (`formatsWithPaidPrices`, que por eso empareja
 * por `productId` y no por tipo de línea). `productId` se conserva por lo mismo: es lo que
 * dice de qué renglón del catálogo salió.
 *
 * Sin el insumo a mano —catálogo todavía cargando— se usa el nombre que el producto tiene
 * snapshotteado: el destino es el id, que siempre está, y el nombre es display.
 */
private fun PurchaseLine.towardsLinkedIngredient(
    product: Product,
    ingredient: Ingredient?
): PurchaseLine {
    if (!product.isIngredientBacked) return this
    // La cantidad pasa de unidades de venta a unidades base del insumo: una caja de 12
    // cartones de leche de litro son 12 litros, no 12 "leches".
    val stockQty = product.ingredientUnitsFor(quantityReceived)
    return copy(
        // Un insumo de operación se gasta el día que se compra; uno de producción no. La
        // línea tiene que decir cuál es o el gasto del mes saldría mal.
        lineKind = ingredient?.let { ingredientPurchaseLineKind(it) }
            ?: PurchaseLineKind.INGREDIENT,
        ingredientId = product.linkedIngredientId,
        ingredientName = ingredient?.name ?: product.linkedIngredientName.ifEmpty { product.name },
        ingredientUnit = ingredient?.effectiveBaseUnit ?: "",
        quantityReceived = stockQty,
        // El importe no se toca: lo que se recalcula es a cuánto sale UNA unidad del insumo.
        unitCost = if (stockQty > 0.0) lineTotal / stockQty else 0.0
    )
}

/** `true` si la línea es la compra de un producto que vende del inventario de un insumo. */
val PurchaseLine.isRedirectedToIngredient: Boolean
    get() = lineKind != PurchaseLineKind.RESALE_PRODUCT &&
        productId.isNotEmpty() && ingredientId.isNotEmpty()

/**
 * Presentaciones con el precio que se acaba de pagar, o `null` si ninguna cambió.
 *
 * El precio del formato se actualiza al registrar la factura —igual que con los insumos—: es
 * lo que el proveedor cobró hoy, y la próxima factura debe proponerlo. Solo se tocan las
 * presentaciones que vinieron en [lines]; una línea por unidad suelta no tiene formato que
 * actualizar.
 */
fun Product.formatsWithPaidPrices(lines: List<PurchaseLine>): List<PurchaseFormat>? {
    // Se empareja por PRODUCTO y no por tipo de línea: la compra de un producto respaldado
    // por insumo entra como línea de insumo (ver `towardsLinkedIngredient`) y su presentación
    // tiene que actualizarse igual — de ella sale el `purchaseCost` que el POS usa de costo.
    val paid = lines
        .filter { it.productId == id }
        .filter { it.formatId.isNotEmpty() && it.packageCost > 0.0 }
        .associate { it.formatId to it.packageCost }
    if (paid.isEmpty()) return null
    var changed = false
    val updated = purchaseFormats.map { fmt ->
        val price = paid[fmt.id]
        if (price != null && kotlin.math.abs(price - fmt.packageCost) > 0.001) {
            changed = true
            fmt.copy(packageCost = price)
        } else fmt
    }
    return if (changed) updated else null
}
