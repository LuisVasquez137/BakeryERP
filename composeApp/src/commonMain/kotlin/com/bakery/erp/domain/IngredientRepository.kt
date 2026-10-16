package com.bakery.erp.domain

import com.bakery.erp.model.BranchStock
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.PurchaseFormat
import com.bakery.erp.model.StockLot
import com.bakery.erp.model.StockMovement

/** Estado deseado del lote de un insumo dentro de una compra editada. */
data class PurchaseLotEntry(
    val ingredientId: String,
    val ingredientName: String,
    val quantity: Double,
    val expiryDate: Long = 0L,
    val lotCode: String = "",
    val unitCost: Double = 0.0,
    val receivedAt: Long = 0L
)

/** Datos del lote que entra con una compra. Solo se manda para insumos perecederos. */
data class NewStockLot(
    val expiryDate: Long = 0L,
    val lotCode: String = "",
    val unitCost: Double = 0.0,
    val purchaseId: String = "",
    val receivedAt: Long = 0L
)

interface IngredientRepository {
    suspend fun getIngredients(companyId: String): List<Ingredient>
    suspend fun addIngredient(companyId: String, ingredient: Ingredient)
    suspend fun updateIngredient(companyId: String, ingredient: Ingredient)
    suspend fun deleteIngredient(companyId: String, ingredientId: String)

    // Stock por sucursal
    suspend fun getBranchStocksForBranch(companyId: String, branchId: String): Map<String, Double>
    suspend fun getBranchStocksForIngredient(companyId: String, ingredientId: String, branchIds: List<String>): List<BranchStock>
    /**
     * Mueve el stock de un insumo en una sucursal y, de paso, mantiene el libro de lotes.
     *
     * El manejo de lotes vive ACÁ y no en cada ViewModel a propósito: hay cuatro caminos que
     * bajan stock (producción, lotes de preparación, ajuste manual y compras) y al que se le
     * olvidara descontar el lote dejaría el aviso de caducidad apuntando a un saco que ya no
     * existe. Este es el único cuello de botella por el que pasan los cuatro.
     *
     * - `delta < 0` → descuenta de los lotes por FEFO (no-op si el insumo no lleva lotes).
     * - [newLot] presente → registra el ingreso como lote nuevo. Solo lo manda la compra de
     *   un insumo perecedero; así los lotes existen únicamente donde sirven.
     * - `delta > 0` sin [newLot] → si el insumo ya lleva lotes, entra como lote sin fecha,
     *   para que un ajuste manual no deje el libro corto.
     *
     * El saldo se lee y se escribe en una transacción (dos movimientos a la vez sobre el
     * mismo insumo ya no se pisan). Devuelve el saldo que había justo antes del cambio,
     * leído dentro de esa transacción: es el `balanceBefore` correcto para el Kardex.
     */
    suspend fun updateBranchStock(
        companyId: String,
        ingredientId: String,
        ingredientName: String,
        branchId: String,
        branchName: String,
        delta: Double,
        newLot: NewStockLot? = null,
        /**
         * `true` deja el libro de lotes intacto. Lo usa la EDICIÓN de una compra, que corrige
         * el stock por diferencia pero reconcilia sus propios lotes aparte con
         * [reconcilePurchaseLots]: si además se dispararan las reglas automáticas, la
         * corrección se contaría dos veces.
         */
        skipLots: Boolean = false
    ): Double

    // ── Lotes de insumos perecederos (FEFO) ──────────────────────────────────

    suspend fun getStockLots(companyId: String, branchId: String, ingredientId: String): List<StockLot>

    /** Lotes con saldo de las sucursales dadas. Alimenta el aviso de caducidad del Home. */
    suspend fun getActiveStockLots(companyId: String, branchIds: List<String>): List<StockLot>

    /**
     * Deja los lotes de [purchaseId] igual a [lines] tras editar la compra.
     *
     * Ajusta el lote existente en vez de borrarlo y recrearlo: un lote puede estar
     * parcialmente consumido, y recrearlo lo devolvería a su cantidad original inflando el
     * libro. Una línea que desapareció de la compra deja su lote en saldo cero (no se borra,
     * para no dejar huérfano el consumo que ya se hizo contra él).
     */
    suspend fun reconcilePurchaseLots(
        companyId: String,
        purchaseId: String,
        branchId: String,
        branchName: String,
        lines: List<PurchaseLotEntry>
    )

    // Kardex
    suspend fun addStockMovement(companyId: String, ingredientId: String, movement: StockMovement)
    suspend fun getStockMovements(companyId: String, ingredientId: String): List<StockMovement>

    // Precio de presentación de compra
    suspend fun updatePurchaseFormatCost(companyId: String, ingredientId: String, formatId: String, newPackageCost: Double)

    /**
     * Agrega UNA presentación sin reescribir el insumo. `updateIngredient` hace `set()` del
     * documento y borra y reescribe toda la subcolección de formatos: llamarlo desde el
     * formulario de compra —que solo quiere agregar una presentación— arriesga pisar una
     * edición del insumo hecha en paralelo. Devuelve el formato ya con su id.
     */
    suspend fun addPurchaseFormat(companyId: String, ingredientId: String, format: PurchaseFormat): PurchaseFormat
}
