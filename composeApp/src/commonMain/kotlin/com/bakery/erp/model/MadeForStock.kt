package com.bakery.erp.model

import kotlin.math.ceil

/**
 * Productos que se hacen por adelantado y se guardan en la planta (pan molde, rosca de
 * panela): ver [Product.isMadeForStock]. Lógica pura, con tests en `MadeForStockTest`.
 *
 * La planta que guarda cada producto es la que lo tiene asignado
 * ([Branch.assignedProductIds] con [BranchCapability.CAN_PRODUCE]) — la misma fuente de
 * verdad que dice quién lo produce, en vez de un segundo campo que pueda contradecirla.
 */

/** Un producto para existencia en una planta, con lo que hay y lo que debería haber. */
data class StockReplenishment(
    val productId: String,
    val productName: String,
    val branchId: String,
    val branchName: String,
    val onHand: Double,
    val minimum: Double
) {
    /** Lo que falta para llegar al mínimo, nunca negativo. */
    val missing: Double get() = (minimum - onHand).coerceAtLeast(0.0)
    val isBelowMinimum: Boolean get() = minimum > 0.0 && onHand < minimum
}

/** Plantas que guardan [product]: las que producen y lo tienen asignado. */
fun keeperPlantsOf(product: Product, branches: List<Branch>): List<Branch> =
    branches.filter {
        BranchCapability.CAN_PRODUCE in it.capabilities && product.id in it.assignedProductIds
    }

/**
 * Productos para existencia de [plant], con su saldo. Es la lista del form de lote para
 * existencia: todos los que esa planta guarda, estén o no bajo el mínimo (la encargada
 * puede adelantarse).
 */
fun madeForStockProductsOf(
    plant: Branch,
    products: List<Product>,
    stockOf: (productId: String) -> Double
): List<StockReplenishment> =
    products
        .filter { it.active && it.isMadeForStock && it.id in plant.assignedProductIds }
        .map {
            StockReplenishment(
                productId = it.id,
                productName = it.name,
                branchId = plant.id,
                branchName = plant.name,
                onHand = stockOf(it.id),
                minimum = it.plantStockMinimum
            )
        }
        .sortedBy { it.productName.lowercase() }

/** Los que bajaron del mínimo en cualquier planta, para el aviso del Home. */
fun stockReplenishmentsNeeded(
    products: List<Product>,
    plants: List<Branch>,
    stockOf: (branchId: String, productId: String) -> Double
): List<StockReplenishment> =
    plants
        .filter { BranchCapability.CAN_PRODUCE in it.capabilities }
        .flatMap { plant -> madeForStockProductsOf(plant, products) { stockOf(plant.id, it) } }
        .filter { it.isBelowMinimum }

/**
 * Cantidad que se sugiere hacer: lo que falta para el mínimo, en piezas enteras.
 * Sin mínimo no sugiere nada — la encargada escribe lo que va a hacer.
 */
fun StockReplenishment.suggestedQuantity(): Double = ceil(missing)

/**
 * Lo que se hace para existencia lo pide la propia planta en su pedido diario (Bombero →
 * Bombero): así entra al lote del día con su receta escalada y al recibir la nota queda en
 * su inventario. Las demás sucursales no lo ven; lo piden por traslado. Lo transformado
 * (pan tostado) tampoco: se hace con "Transformar", no se hornea.
 */
fun Product.isOrderableIn(sellingBranchId: String, producingBranchId: String): Boolean = when {
    isOrderedFromPlant -> true
    !isMadeForStock || isTransformed -> false
    else -> sellingBranchId.isNotEmpty() && sellingBranchId == producingBranchId
}

/** Lo que la encargada ve en la fila del pedido de un producto para existencia. */
data class StockOrderInfo(
    val onHand: Double,
    val minimum: Double
)
