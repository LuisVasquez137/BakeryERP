package com.bakery.erp.data

import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock

class FirebaseBranchRepository(
    private val firestore: FirebaseFirestore
) : BranchRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/branches")

    override suspend fun getBranches(companyId: String): List<Branch> =
        col(companyId).get().documents.map { it.toBranch() }

    override suspend fun getBranch(companyId: String, branchId: String): Branch? =
        runCatching { col(companyId).document(branchId).get().toBranch() }.getOrNull()

    override suspend fun addBranch(companyId: String, branch: Branch) {
        val docRef = if (branch.id.isEmpty()) col(companyId).document
        else col(companyId).document(branch.id)
        docRef.set(branch.copy(id = docRef.id).toMap())
    }

    override suspend fun updateBranch(companyId: String, branch: Branch) {
        col(companyId).document(branch.id).set(branch.toMap())
    }

    override suspend fun deleteBranch(companyId: String, branchId: String) {
        col(companyId).document(branchId).delete()
    }

    // ── Serialization ────────────────────────────────────────────────────────

    private fun Branch.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "orgId" to orgId,
        "name" to name,
        "location" to location,
        "phone" to phone,
        "managerName" to managerName,
        "notes" to notes,
        "capabilities" to capabilities.map { it.name },
        "assignedProductIds" to assignedProductIds,
        "defaultPriceListId" to defaultPriceListId,
        "active" to active,
        "createdAt" to if (createdAt == 0L) Clock.System.now().toEpochMilliseconds() else createdAt,
        "updatedAt" to Clock.System.now().toEpochMilliseconds()
    )

    private fun DocumentSnapshot.toBranch() = Branch(
        id = id,
        orgId = get<String?>("orgId") ?: "",
        name = get<String?>("name") ?: "",
        location = get<String?>("location") ?: "",
        phone = get<String?>("phone") ?: "",
        managerName = get<String?>("managerName") ?: "",
        notes = get<String?>("notes"),
        capabilities = runCatching {
            get<List<String>?>("capabilities")
                ?.mapNotNull { runCatching { BranchCapability.valueOf(it) }.getOrNull() }
                ?: emptyList()
        }.getOrElse { emptyList() },
        assignedProductIds = runCatching {
            get<List<String>?>("assignedProductIds") ?: emptyList()
        }.getOrElse { emptyList() },
        defaultPriceListId = get<String?>("defaultPriceListId") ?: "",
        active = get<Boolean?>("active") ?: true,
        createdAt = readTimestampAsMillis("createdAt"),
        updatedAt = readTimestampAsMillis("updatedAt")
    )

    private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
