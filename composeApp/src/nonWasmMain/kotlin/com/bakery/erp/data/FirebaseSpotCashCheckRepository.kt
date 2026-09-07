package com.bakery.erp.data

import com.bakery.erp.domain.SpotCashCheckRepository
import com.bakery.erp.model.CashDenominations
import com.bakery.erp.model.SpotCashCheck
import com.bakery.erp.model.SpotCashCheckResolution
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebaseSpotCashCheckRepository(
    private val firestore: FirebaseFirestore
) : SpotCashCheckRepository {

    private fun checksCol(companyId: String) =
        firestore.collection("companies/$companyId/spotCashChecks")

    private fun denomsCol(companyId: String, checkId: String) =
        firestore.collection("companies/$companyId/spotCashChecks/$checkId/denominations")

    override suspend fun create(
        companyId: String,
        check: SpotCashCheck,
        denominations: Map<String, Int>
    ): SpotCashCheck {
        val docRef = checksCol(companyId).document
        val now = Clock.System.now().toEpochMilliseconds()
        val finalCheck = check.copy(
            id = docRef.id,
            checkedAt = if (check.checkedAt == 0L) now else check.checkedAt
        )
        docRef.set(finalCheck.toMap())
        denominations.filter { it.value > 0 }.forEach { (code, qty) ->
            val denom = CashDenominations.byCode(code) ?: return@forEach
            denomsCol(companyId, finalCheck.id).document(code).set(firestoreSafeOf(
                "denomCode" to code,
                "quantity" to qty,
                "subtotal" to qty * denom.value
            ))
        }
        return finalCheck
    }

    override suspend fun attachAdjustmentMovement(
        companyId: String, checkId: String, movementId: String
    ) {
        val current = get(companyId, checkId) ?: return
        checksCol(companyId).document(checkId).set(
            current.copy(adjustmentMovementId = movementId).toMap()
        )
    }

    override suspend fun get(companyId: String, checkId: String): SpotCashCheck? =
        checksCol(companyId).document(checkId).get().takeIf { it.exists }?.toCheck()

    override suspend fun getForSession(companyId: String, sessionId: String): List<SpotCashCheck> =
        checksCol(companyId).where { "sessionId" equalTo sessionId }.get().documents
            .map { it.toCheck() }
            .sortedByDescending { it.checkedAt }

    override suspend fun getForBranchInRange(
        companyId: String,
        branchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): List<SpotCashCheck> = checksCol(companyId).where { "branchId" equalTo branchId }.get().documents
        .map { it.toCheck() }
        .filter { it.checkedAt in fromMillis..untilMillis }
        .sortedByDescending { it.checkedAt }

    override fun observeForBranchInRange(
        companyId: String,
        branchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): Flow<List<SpotCashCheck>> = checksCol(companyId).snapshots.map { snap ->
        snap.documents
            .map { it.toCheck() }
            .filter { it.branchId == branchId && it.checkedAt in fromMillis..untilMillis }
            .sortedByDescending { it.checkedAt }
    }

    override suspend fun getDenominations(companyId: String, checkId: String): Map<String, Int> =
        denomsCol(companyId, checkId).get().documents.associate { doc ->
            val code = doc.get<String?>("denomCode") ?: doc.id
            val qty  = (doc.get<Double?>("quantity") ?: 0.0).toInt()
            code to qty
        }

    private fun SpotCashCheck.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "branchId" to branchId,
        "branchName" to branchName,
        "registerId" to registerId,
        "registerName" to registerName,
        "sessionId" to sessionId,
        "cashierId" to cashierId,
        "cashierName" to cashierName,
        "checkedByUserId" to checkedByUserId,
        "checkedByUserName" to checkedByUserName,
        "checkedAt" to checkedAt,
        "expectedCashAtCheck" to expectedCashAtCheck,
        "countedCash" to countedCash,
        "difference" to difference,
        "resolution" to resolution,
        "adjustmentMovementId" to adjustmentMovementId,
        "notes" to notes
    )

    private fun DocumentSnapshot.toCheck() = SpotCashCheck(
        id = id,
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        registerId = get<String?>("registerId") ?: "",
        registerName = get<String?>("registerName") ?: "",
        sessionId = get<String?>("sessionId") ?: "",
        cashierId = get<String?>("cashierId") ?: "",
        cashierName = get<String?>("cashierName") ?: "",
        checkedByUserId = get<String?>("checkedByUserId") ?: "",
        checkedByUserName = get<String?>("checkedByUserName") ?: "",
        checkedAt = readMillis("checkedAt"),
        expectedCashAtCheck = get<Double?>("expectedCashAtCheck") ?: 0.0,
        countedCash = get<Double?>("countedCash") ?: 0.0,
        difference = get<Double?>("difference") ?: 0.0,
        resolution = get<String?>("resolution") ?: SpotCashCheckResolution.NONE,
        adjustmentMovementId = get<String?>("adjustmentMovementId"),
        notes = get<String?>("notes") ?: ""
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
