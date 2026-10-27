package com.bakery.erp.domain

import kotlinx.coroutines.flow.Flow
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
     * Guarda un pedido de la sucursal (estado PEDIDO, sin mover stock). Lo envía después quien
     * lo tiene, con [send] sobre el mismo traslado: [send] vuelve a leerlo y choca si ya no está
     * pedido, así dos toques no lo mandan dos veces.
     */
    suspend fun request(companyId: String, transfer: StockTransfer): StockTransfer

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

    /**
     * Cancela un traslado en camino: el stock (y sus lotes) vuelve al origen. Un pedido todavía
     * sin enviar se cancela (o lo rechaza quien lo tiene) sin mover nada.
     */
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
     * Los pedidos y los que siguen en camino vienen siempre; el resto, si se enviaron en el rango.
     */
    suspend fun getForBranches(
        companyId: String,
        branchIds: List<String>,
        fromMillis: Long,
        untilMillis: Long
    ): List<StockTransfer>

    /**
     * Igual que [getForBranches] pero en vivo y sin tope de fecha final: la sucursal que
     * recibe ve aparecer el envío (y el origen ve que ya lo recibieron) sin salir de la lista.
     */
    fun observeForBranches(
        companyId: String,
        branchIds: List<String>,
        fromMillis: Long
    ): Flow<List<StockTransfer>>

    /** Recibidos en el rango (por fecha de recepción), para el reporte por sucursal. */
    suspend fun getReceivedInRange(companyId: String, fromMillis: Long, untilMillis: Long): List<StockTransfer>
}
