package com.bakery.erp.domain

import com.bakery.erp.model.PriceList
import com.bakery.erp.model.ProductPrice

interface PriceListRepository {
    // ── Listas ────────────────────────────────────────────────────────────────
    suspend fun getLists(companyId: String): List<PriceList>
    suspend fun getList(companyId: String, listId: String): PriceList?
    suspend fun addList(companyId: String, list: PriceList)
    suspend fun updateList(companyId: String, list: PriceList)
    suspend fun deleteList(companyId: String, listId: String)

    /** Crea las listas seed (`Cliente final` RETAIL default + `Sucursales` WHOLESALE default)
     *  si la compañía aún no tiene ninguna. Idempotente. */
    suspend fun ensureSeedLists(companyId: String)

    /** Lista default del tipo dado, o null si no existe. */
    suspend fun getDefaultList(companyId: String, type: String): PriceList?

    // ── Precios de productos ──────────────────────────────────────────────────
    suspend fun getPricesForProduct(companyId: String, productId: String): List<ProductPrice>
    suspend fun getPricesForList(companyId: String, listId: String): List<ProductPrice>
    suspend fun upsertPrice(companyId: String, price: ProductPrice)
    suspend fun deletePrice(companyId: String, productId: String, listId: String)

    /**
     * Resuelve el precio unitario para un producto al despachar hacia una sucursal.
     * Orden: `branch.defaultPriceListId` → lista default WHOLESALE → 0.0.
     * Devuelve `(unitPrice, priceListId, priceListName)` — el id de la lista usada
     * permite al llamador guardar trazabilidad de la decisión.
     */
    suspend fun resolveWholesalePrice(
        companyId: String,
        destinationBranchId: String,
        productId: String
    ): ResolvedPrice
}

data class ResolvedPrice(
    val unitPrice: Double,
    val priceListId: String,
    val priceListName: String,
    val found: Boolean,
    val source: String = PriceSource.NONE
)

object PriceSource {
    /** Override explícito en `ProductPrice` para esta lista. */
    const val PRODUCT_OVERRIDE = "PRODUCT_OVERRIDE"
    /** Calculado aplicando `priceFactor` al precio retail del producto. */
    const val LIST_FACTOR = "LIST_FACTOR"
    /** Sin precio resoluble; alerta al empacador. */
    const val NONE = "NONE"
}
