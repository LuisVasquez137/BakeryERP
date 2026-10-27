package com.bakery.erp.domain

import com.bakery.erp.domain.report.StockMovementSummary
import com.bakery.erp.model.FinishedProductStock
import com.bakery.erp.model.FinishedStockMovement
import com.bakery.erp.model.PortioningRequest
import com.bakery.erp.model.TransformationRequest
import kotlinx.coroutines.flow.Flow

interface FinishedStockRepository {

    suspend fun getStock(companyId: String, branchId: String, productId: String): FinishedProductStock?

    suspend fun getStocksForBranch(companyId: String, branchId: String): List<FinishedProductStock>

    fun observeStocksForBranch(companyId: String, branchId: String): Flow<List<FinishedProductStock>>

    suspend fun getMovementsForProduct(
        companyId: String,
        branchId: String,
        productId: String,
        fromMillis: Long? = null,
        untilMillis: Long? = null
    ): List<FinishedStockMovement>

    /**
     * Aplica un movimiento al stock terminado:
     *  - signed `quantity > 0` → ingreso (RECEPCION). Recalcula `avgUnitCost` ponderado.
     *  - signed `quantity < 0` → salida (MERMA/VENTA/AJUSTE/TRANSFERENCIA). Mantiene avgUnitCost.
     *  - Crea el doc de stock si no existía.
     *  - Escribe el movimiento en el Kardex.
     */
    suspend fun applyMovement(companyId: String, movement: FinishedStockMovement)

    /**
     * Corta enteros en porciones (ver `model/Portioning.kt`): lee el stock ACTUAL del entero
     * (no el de la pantalla, que puede venir atrasado), valida con `portioningError` —lanza
     * `IllegalStateException` con el mensaje— y aplica la salida del entero y la entrada de
     * porciones, en serie. Devuelve las porciones que entraron.
     */
    suspend fun applyPortioning(companyId: String, request: PortioningRequest): Int

    /**
     * Hace un producto de otro (ver `model/Transformation.kt`): lee el stock ACTUAL del
     * original, valida con `transformationError` (lanza con el mensaje) y aplica las dos puntas
     * en serie. [extrasCost] = lo que cuestan los insumos agregados (entra al costo del nuevo).
     * Devuelve el `referenceId` que une los dos movimientos (los insumos lo usan en su Kardex).
     */
    suspend fun applyTransformation(companyId: String, request: TransformationRequest, extrasCost: Double): String

    /**
     * Agrega los movimientos del Kardex de terminado en rango. Si [branchId] está vacío,
     * agrega para toda la organización. Calcula receivedUnits / soldUnits / mermaUnits +
     * mermaValue (suma de |quantity| × unitPriceAtMovement de movimientos MERMA).
     */
    suspend fun aggregateMovementsInRange(
        companyId: String,
        branchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): StockMovementSummary
}
