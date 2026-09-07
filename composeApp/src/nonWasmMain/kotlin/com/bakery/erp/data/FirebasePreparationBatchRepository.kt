package com.bakery.erp.data

import com.bakery.erp.domain.PreparationBatchRepository
import com.bakery.erp.model.PreparationBatch
import com.bakery.erp.model.PreparationBatchStatus
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock

/**
 * Estructura Firestore:
 *   companies/{orgId}/preparationBatches/{batchId}    ← documento plano
 */
class FirebasePreparationBatchRepository(
    private val firestore: FirebaseFirestore
) : PreparationBatchRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/preparationBatches")

    override suspend fun getBatches(companyId: String): List<PreparationBatch> =
        col(companyId).get().documents.map { it.toBatch() }

    override suspend fun getBatch(companyId: String, batchId: String): PreparationBatch? =
        runCatching { col(companyId).document(batchId).get().toBatch() }.getOrNull()

    override suspend fun addBatch(companyId: String, batch: PreparationBatch) {
        val docRef = if (batch.id.isEmpty()) col(companyId).document
        else col(companyId).document(batch.id)
        docRef.set(batch.copy(id = docRef.id).toMap())
    }

    override suspend fun updateBatch(companyId: String, batch: PreparationBatch) {
        col(companyId).document(batch.id).set(batch.toMap())
    }

    override suspend fun deleteBatch(companyId: String, batchId: String) {
        col(companyId).document(batchId).delete()
    }

    override suspend fun listActiveBatches(
        companyId: String,
        branchId: String,
        preparationId: String?
    ): List<PreparationBatch> =
        col(companyId).get().documents
            .map { it.toBatch() }
            .filter {
                it.branchId == branchId &&
                    it.status == PreparationBatchStatus.ACTIVE &&
                    it.remainingWeightGrams > 0.0 &&
                    (preparationId == null || it.preparationId == preparationId)
            }
            .sortedBy { it.producedAt }

    override suspend fun consumeFromBatch(companyId: String, batchId: String, grams: Double) {
        if (grams <= 0.0) return
        val batch = getBatch(companyId, batchId)
            ?: throw IllegalStateException("Batch $batchId no encontrado")
        val newRemaining = batch.remainingWeightGrams - grams
        if (newRemaining < -5.0) {
            throw IllegalStateException(
                "Saldo insuficiente en batch ${batch.preparationName}: " +
                    "${batch.remainingWeightGrams}g disponibles, se piden ${grams}g"
            )
        }
        val coerced = if (newRemaining < 0.0) 0.0 else newRemaining
        val newStatus = if (coerced <= 5.0) PreparationBatchStatus.DEPLETED else batch.status
        updateBatch(
            companyId,
            batch.copy(
                remainingWeightGrams = coerced,
                status = newStatus,
                updatedAt = Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    override suspend fun returnToBatch(companyId: String, batchId: String, grams: Double) {
        if (grams <= 0.0) return
        val batch = getBatch(companyId, batchId)
            ?: throw IllegalStateException("Batch $batchId no encontrado")
        val newRemaining = (batch.remainingWeightGrams + grams).coerceAtMost(batch.producedWeightGrams)
        val newStatus = if (newRemaining > 5.0 && batch.status == PreparationBatchStatus.DEPLETED)
            PreparationBatchStatus.ACTIVE else batch.status
        updateBatch(
            companyId,
            batch.copy(
                remainingWeightGrams = newRemaining,
                status = newStatus,
                updatedAt = Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    // ── Serialización ────────────────────────────────────────────────────────

    private fun PreparationBatch.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "preparationId" to preparationId,
        "preparationName" to preparationName,
        "branchId" to branchId,
        "branchName" to branchName,
        "batchCount" to batchCount,
        "producedWeightGrams" to producedWeightGrams,
        "remainingWeightGrams" to remainingWeightGrams,
        "status" to status,
        "producedByUserId" to producedByUserId,
        "producedByUserName" to producedByUserName,
        "producedAt" to producedAt,
        "createdAt" to if (createdAt == 0L) Clock.System.now().toEpochMilliseconds() else createdAt,
        "updatedAt" to Clock.System.now().toEpochMilliseconds()
    )

    private fun DocumentSnapshot.toBatch() = PreparationBatch(
        id = id,
        preparationId = get<String?>("preparationId") ?: "",
        preparationName = get<String?>("preparationName") ?: "",
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        batchCount = get<Long?>("batchCount")?.toInt() ?: 1,
        producedWeightGrams = get<Double?>("producedWeightGrams") ?: 0.0,
        remainingWeightGrams = get<Double?>("remainingWeightGrams") ?: 0.0,
        status = get<String?>("status") ?: PreparationBatchStatus.ACTIVE,
        producedByUserId = get<String?>("producedByUserId") ?: "",
        producedByUserName = get<String?>("producedByUserName") ?: "",
        producedAt = readTimestampAsMillis("producedAt"),
        createdAt = readTimestampAsMillis("createdAt"),
        updatedAt = readTimestampAsMillis("updatedAt")
    )

    private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
