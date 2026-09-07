package com.bakery.erp.domain

import com.bakery.erp.model.SpotCashCheck
import kotlinx.coroutines.flow.Flow

interface SpotCashCheckRepository {

    /**
     * Persiste un cuadre sorpresa + el desglose por denominación (subcolección `denominations/`).
     * Si la resolución del check es `FORCE_ADJUST`, el caller debe haber creado ya el
     * `CashMovement` espejo y pasar su id en `check.adjustmentMovementId`.
     */
    suspend fun create(
        companyId: String,
        check: SpotCashCheck,
        denominations: Map<String, Int>
    ): SpotCashCheck

    /** Actualiza `adjustmentMovementId` después de haber creado el movimiento espejo. */
    suspend fun attachAdjustmentMovement(
        companyId: String, checkId: String, movementId: String
    )

    suspend fun get(companyId: String, checkId: String): SpotCashCheck?

    suspend fun getForSession(companyId: String, sessionId: String): List<SpotCashCheck>

    suspend fun getForBranchInRange(
        companyId: String,
        branchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): List<SpotCashCheck>

    fun observeForBranchInRange(
        companyId: String,
        branchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): Flow<List<SpotCashCheck>>

    suspend fun getDenominations(companyId: String, checkId: String): Map<String, Int>
}
