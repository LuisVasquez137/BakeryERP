package com.bakery.erp.domain

import com.bakery.erp.model.DeliveryRun
import com.bakery.erp.model.User
import kotlinx.coroutines.flow.Flow

interface DeliveryRepository {

    suspend fun getRun(companyId: String, runId: String): DeliveryRun?

    suspend fun getRunsInRange(companyId: String, fromMillis: Long, untilMillis: Long): List<DeliveryRun>

    fun observeRuns(companyId: String, fromMillis: Long, untilMillis: Long): Flow<List<DeliveryRun>>

    /** Crea o actualiza una hoja de reparto. Marca las notas seleccionadas como IN_TRANSIT
     *  (con `deliveryRunId` apuntando a esta hoja). Las notas que estaban en `existing.dispatchNoteIds`
     *  pero ya no están en `noteIds` se desasignan (vuelven a READY). */
    suspend fun saveRun(
        companyId: String,
        run: DeliveryRun,
        noteIds: List<String>,
        currentUser: User
    ): DeliveryRun

    suspend fun startRun(companyId: String, runId: String, currentUser: User)

    /** Cierra manualmente la hoja de reparto. */
    suspend fun completeRun(companyId: String, runId: String, currentUser: User)

    /** Cancela la hoja. Las notas asignadas se desasignan y vuelven a READY. */
    suspend fun cancelRun(companyId: String, runId: String, currentUser: User)

    /**
     * Verifica si todas las notas de la hoja están en estado terminal (DELIVERED,
     * DELIVERED_WITH_DISCREPANCY, CANCELLED). Si sí, marca el run como COMPLETED.
     * Idempotente — llamarlo varias veces es seguro.
     */
    suspend fun tryAutoCompleteRun(companyId: String, runId: String)
}
