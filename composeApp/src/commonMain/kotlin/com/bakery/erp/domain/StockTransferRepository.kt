package com.bakery.erp.domain

import com.bakery.erp.model.StockTransfer

/**
 * Traslados de inventario entre sucursales. Ver `model/StockTransfer.kt`.
 *
 * Un solo camino por efecto: el inventario de tienda se mueve con
 * `FinishedStockRepository.applyMovement` (tipo TRANSFERENCIA) y el de insumos con
 * `IngredientRepository.updateBranchStock` (que mantiene los lotes) + su Kardex. El encabezado
 * siempre va antes que el stock: un reintento choca con la guarda de estado en vez de duplicar.
 */
interface StockTransferRepository {

    /**
     * Envía [transfer] (líneas con cantidad en la unidad del stock): calcula el costo y los lotes
     * que salen, escribe el encabezado EN CAMINO y descuenta el stock del origen. Devuelve lo
     * guardado, con costos y lotes.
     */
    suspend fun send(companyId: String, transfer: StockTransfer): StockTransfer

    /**
     * Recibe con [received] (una cantidad por línea, en el orden de `lines`). Vuelve a leer y
     * lanza con el mensaje si ya no está en camino.
     */
    suspend fun receive(
        companyId: String,
        transferId: String,
        received: List<Double>,
        byUserId: String,
        byName: String,
        byEmployeeId: String,
        note: String
    ): StockTransfer

    /** Cancela un traslado en camino: el stock (y sus lotes) vuelve al origen. */
    suspend fun cancel(
        companyId: String,
        transferId: String,
        byUserId: String,
        byName: String,
        reason: String
    ): StockTransfer

    suspend fun get(companyId: String, transferId: String): StockTransfer?

    /**
     * Los que salieron de o llegaron a [branchIds] (vacío = todas), el más reciente primero.
     * Los que siguen en camino vienen siempre; el resto, si se enviaron en el rango.
     */
    suspend fun getForBranches(
        companyId: String,
        branchIds: List<String>,
        fromMillis: Long,
        untilMillis: Long
    ): List<StockTransfer>

    /** Recibidos en el rango (por fecha de recepción), para el reporte por sucursal. */
    suspend fun getReceivedInRange(companyId: String, fromMillis: Long, untilMillis: Long): List<StockTransfer>
}
