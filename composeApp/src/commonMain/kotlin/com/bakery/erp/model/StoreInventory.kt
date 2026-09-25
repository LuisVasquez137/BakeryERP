package com.bakery.erp.model

/**
 * Renglón del inventario de tienda de UNA sucursal.
 *
 * `FinishedProductStock` solo existe desde el primer movimiento, así que listar documentos
 * dejaba fuera justo lo que más se busca al empezar: el producto de reventa que se acaba de
 * dar de alta —y que se va a comprar hoy— no aparecía en ninguna sucursal, ni siquiera en 0,
 * y no había forma de saber si el ingreso de la factura había entrado o no.
 */
data class StoreInventoryRow(
    val productId: String,
    val productName: String,
    val isResale: Boolean,
    val currentStock: Double,
    val avgUnitCost: Double,
    /**
     * Insumo del que el producto saca su stock (el huevo que se vende por docena). Con esto el
     * número NO vive en `finishedStocks` sino en el `BranchStock` del insumo: se muestra
     * convertido a piezas vendibles y no se ajusta desde acá — un ajuste escribiría un
     * inventario de terminado paralelo que nadie descuenta.
     */
    val linkedIngredientName: String = "",
    /** `false` = todavía no tuvo ningún movimiento en esta sucursal. */
    val hasStockRecord: Boolean = true
) {
    val isIngredientBacked: Boolean get() = linkedIngredientName.isNotEmpty()
}

/**
 * Une lo que hay en `finishedStocks` con el catálogo de reventa.
 *
 * - Todo documento de stock sale, como antes (el pan propio aparece cuando se recibe).
 * - Cada producto de reventa ACTIVO sin documento sale en 0: es el catálogo de lo que la
 *   sucursal vende, tenga o no existencias.
 * - Un producto respaldado por un insumo sale con el stock del insumo ÷ unidades por venta, y
 *   un documento de terminado suyo (si quedó de antes del vínculo) se ignora: mostrar los dos
 *   números es mostrar dos inventarios para una sola caja de huevos.
 *
 * [ingredientStock] = existencias por `ingredientId` en esta sucursal.
 */
fun buildStoreInventoryRows(
    stocks: List<FinishedProductStock>,
    products: List<Product>,
    ingredientStock: Map<String, Double>
): List<StoreInventoryRow> {
    val productsById = products.associateBy { it.id }
    val linked = products.filter { !it.isInHouseProduced && it.active && it.isIngredientBacked }
    val linkedIds = linked.mapTo(HashSet()) { it.id }

    val fromDocs = stocks
        .filter { it.productId !in linkedIds }
        .map { s ->
            val p = productsById[s.productId]
            StoreInventoryRow(
                productId = s.productId,
                productName = p?.name ?: s.productName,
                isResale = p?.isInHouseProduced == false,
                currentStock = s.currentStock,
                avgUnitCost = s.avgUnitCost
            )
        }
    val withDoc = stocks.mapTo(HashSet()) { it.productId }

    val emptyResale = products
        .filter { !it.isInHouseProduced && it.active && !it.isIngredientBacked && it.id !in withDoc }
        .map {
            StoreInventoryRow(
                productId = it.id,
                productName = it.name,
                isResale = true,
                currentStock = 0.0,
                avgUnitCost = it.purchaseCost ?: 0.0,
                hasStockRecord = false
            )
        }

    val backed = linked.map { p ->
        val perSale = p.ingredientUnitsPerSale.takeIf { it > 0.0 } ?: 1.0
        StoreInventoryRow(
            productId = p.id,
            productName = p.name,
            isResale = true,
            currentStock = (ingredientStock[p.linkedIngredientId] ?: 0.0) / perSale,
            avgUnitCost = p.purchaseCost ?: 0.0,
            linkedIngredientName = p.linkedIngredientName.ifEmpty { "insumo" }
        )
    }

    return (fromDocs + emptyResale + backed).sortedBy { it.productName.lowercase() }
}
