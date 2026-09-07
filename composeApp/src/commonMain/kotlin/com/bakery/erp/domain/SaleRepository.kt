package com.bakery.erp.domain

import com.bakery.erp.domain.report.SalesSummary
import com.bakery.erp.model.Sale
import com.bakery.erp.model.SaleLine
import kotlinx.coroutines.flow.Flow

interface SaleRepository {

    /**
     * Persiste una venta con sus líneas:
     *  1. Para cada línea con productId no-EXPRESS:
     *     - Lee `FinishedProductStock` para snapshot del `avgUnitCost` en `costAtSale`.
     *     - Llama `FinishedStockRepository.applyMovement` (tipo VENTA, referencia SALE).
     *     - Marca `stockDeducted = true`.
     *  2. Para líneas EXPRESS sin productId: `stockDeducted = false`, `costAtSale = 0`.
     *  3. Escribe el doc `Sale` y la subcolección `lines`.
     *  4. Incrementa los totales denormalizados en `CashSession` vía
     *     `CashSessionRepository.incrementSaleTotals`.
     */
    suspend fun createSale(companyId: String, sale: Sale, lines: List<SaleLine>): Sale

    suspend fun getSale(companyId: String, saleId: String): Sale?
    suspend fun getSaleLines(companyId: String, saleId: String): List<SaleLine>

    suspend fun getSalesForSession(companyId: String, sessionId: String): List<Sale>
    fun observeSalesForSession(companyId: String, sessionId: String): Flow<List<Sale>>

    /** Histórico por sucursal en rango de fechas (ms epoch). */
    suspend fun getSalesForBranchInRange(
        companyId: String,
        branchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): List<Sale>

    fun observeSalesForBranchInRange(
        companyId: String,
        branchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): Flow<List<Sale>>

    /**
     * Agrega ventas (header + líneas) en rango de fechas para un set de sucursales.
     * Si [branchIds] es null o vacío, no filtra por sucursal.
     * Devuelve totales y desgloses (por método de pago, cajero, sucursal, producto,
     * día y hora). Las líneas se cargan en bloque para calcular `topProducts`.
     */
    suspend fun aggregateSalesInRange(
        companyId: String,
        branchIds: List<String>?,
        fromMillis: Long,
        untilMillis: Long
    ): SalesSummary
}
