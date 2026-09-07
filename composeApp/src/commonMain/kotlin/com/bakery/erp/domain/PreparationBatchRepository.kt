package com.bakery.erp.domain

import com.bakery.erp.model.PreparationBatch

interface PreparationBatchRepository {
    suspend fun getBatches(companyId: String): List<PreparationBatch>
    suspend fun getBatch(companyId: String, batchId: String): PreparationBatch?
    suspend fun addBatch(companyId: String, batch: PreparationBatch)
    suspend fun updateBatch(companyId: String, batch: PreparationBatch)
    suspend fun deleteBatch(companyId: String, batchId: String)

    /**
     * Lista batches ACTIVE para una sucursal, opcionalmente filtrados por
     * preparación específica. Devuelve solo los que tienen [PreparationBatch.remainingWeightGrams] > 0.
     */
    suspend fun listActiveBatches(
        companyId: String,
        branchId: String,
        preparationId: String? = null
    ): List<PreparationBatch>

    /**
     * Resta [grams] del saldo del batch. Si el saldo cae a 0 (con tolerancia ±5g),
     * marca el status como DEPLETED. Falla si no existe el batch o no hay saldo suficiente.
     */
    suspend fun consumeFromBatch(companyId: String, batchId: String, grams: Double)

    /**
     * Inverso de [consumeFromBatch]: devuelve [grams] al saldo (tope =
     * `producedWeightGrams`, no puede quedar por encima de lo producido) y
     * reactiva el batch si estaba DEPLETED. Para cuando el panadero se
     * equivoca de lote/cantidad de esponja y aún no completó la producción.
     */
    suspend fun returnToBatch(companyId: String, batchId: String, grams: Double)
}
