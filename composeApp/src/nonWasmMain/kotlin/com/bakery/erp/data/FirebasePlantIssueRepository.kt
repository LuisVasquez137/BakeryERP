package com.bakery.erp.data

import com.bakery.erp.domain.PlantIssueRepository
import com.bakery.erp.model.PlantIssue
import com.bakery.erp.model.PlantIssueReason
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlin.time.Clock

class FirebasePlantIssueRepository(
    private val firestore: FirebaseFirestore
) : PlantIssueRepository {

    private fun issuesCol(companyId: String) =
        firestore.collection("companies/$companyId/plantIssues")

    override suspend fun createIssue(companyId: String, issue: PlantIssue): PlantIssue {
        val docRef = issuesCol(companyId).document
        val finalIssue = issue.copy(
            id = docRef.id,
            issuedAt = if (issue.issuedAt == 0L) Clock.System.now().toEpochMilliseconds()
                       else issue.issuedAt
        )
        docRef.set(finalIssue.toMap())
        return finalIssue
    }

    override suspend fun getIssuesInRange(
        companyId: String,
        branchIds: List<String>,
        fromMillis: Long,
        untilMillis: Long
    ): List<PlantIssue> = issuesCol(companyId).get().documents
        .map { it.toIssue() }
        .filter { (branchIds.isEmpty() || it.branchId in branchIds) && it.issuedAt in fromMillis..untilMillis }
        .sortedByDescending { it.issuedAt }

    override suspend fun getRecentIssues(
        companyId: String,
        branchId: String,
        limit: Int
    ): List<PlantIssue> = issuesCol(companyId).where { "branchId" equalTo branchId }.get().documents
        .map { it.toIssue() }
        .sortedByDescending { it.issuedAt }
        .take(limit)

    override suspend fun getIssuesForLot(companyId: String, lotId: String): List<PlantIssue> =
        issuesCol(companyId).where { "lotId" equalTo lotId }.get().documents
            .map { it.toIssue() }
            .sortedByDescending { it.issuedAt }

    override suspend fun deleteIssue(companyId: String, issueId: String) {
        issuesCol(companyId).document(issueId).delete()
    }

    private fun PlantIssue.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "branchId" to branchId,
        "branchName" to branchName,
        "ingredientId" to ingredientId,
        "ingredientName" to ingredientName,
        "formatId" to formatId,
        "formatDescription" to formatDescription,
        "unitsPerPackage" to unitsPerPackage,
        "formatUnit" to formatUnit,
        "packageQuantity" to packageQuantity,
        "looseQuantity" to looseQuantity,
        "looseUnit" to looseUnit,
        "quantityInBaseUnit" to quantityInBaseUnit,
        "baseUnit" to baseUnit,
        "reason" to reason,
        "notes" to notes,
        "lotId" to lotId,
        "shiftId" to shiftId,
        "shiftName" to shiftName,
        "issuedByUserId" to issuedByUserId,
        "issuedByUserName" to issuedByUserName,
        "issuedAt" to issuedAt
    )

    private fun DocumentSnapshot.toIssue() = PlantIssue(
        id = id,
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        ingredientId = get<String?>("ingredientId") ?: "",
        ingredientName = get<String?>("ingredientName") ?: "",
        formatId = get<String?>("formatId") ?: "",
        formatDescription = get<String?>("formatDescription") ?: "",
        unitsPerPackage = get<Double?>("unitsPerPackage") ?: 0.0,
        formatUnit = get<String?>("formatUnit") ?: "",
        packageQuantity = get<Double?>("packageQuantity") ?: 0.0,
        looseQuantity = get<Double?>("looseQuantity") ?: 0.0,
        looseUnit = get<String?>("looseUnit") ?: "",
        quantityInBaseUnit = get<Double?>("quantityInBaseUnit") ?: 0.0,
        baseUnit = get<String?>("baseUnit") ?: "",
        reason = get<String?>("reason") ?: PlantIssueReason.OTHER,
        notes = get<String?>("notes") ?: "",
        lotId = get<String?>("lotId") ?: "",
        shiftId = get<String?>("shiftId") ?: "",
        shiftName = get<String?>("shiftName") ?: "",
        issuedByUserId = get<String?>("issuedByUserId") ?: "",
        issuedByUserName = get<String?>("issuedByUserName") ?: "",
        issuedAt = readMillis("issuedAt")
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
