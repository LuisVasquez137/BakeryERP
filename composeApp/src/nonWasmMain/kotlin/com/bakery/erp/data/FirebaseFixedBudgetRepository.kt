package com.bakery.erp.data

import com.bakery.erp.domain.FixedBudgetRepository
import com.bakery.erp.model.ExpenseCategory
import com.bakery.erp.model.FixedBudgetItem
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock

class FirebaseFixedBudgetRepository(
    private val firestore: FirebaseFirestore
) : FixedBudgetRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/fixedBudget")

    override suspend fun getFixedBudgetItems(companyId: String): List<FixedBudgetItem> =
        col(companyId).get().documents.map { it.toItem() }

    override suspend fun addFixedBudgetItem(companyId: String, item: FixedBudgetItem) {
        val docRef = if (item.id.isEmpty()) col(companyId).document else col(companyId).document(item.id)
        docRef.set(item.copy(id = docRef.id, orgId = companyId).toMap())
    }

    override suspend fun updateFixedBudgetItem(companyId: String, item: FixedBudgetItem) {
        col(companyId).document(item.id).set(item.copy(orgId = companyId).toMap())
    }

    override suspend fun deleteFixedBudgetItem(companyId: String, itemId: String) {
        col(companyId).document(itemId).delete()
    }

    private fun FixedBudgetItem.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "orgId" to orgId,
        "branchId" to branchId,
        "branchName" to branchName,
        "name" to name,
        "category" to category.name,
        "amount" to amount,
        "notes" to notes,
        "active" to active,
        "updatedAt" to Clock.System.now().toEpochMilliseconds()
    )

    private fun DocumentSnapshot.toItem(): FixedBudgetItem = FixedBudgetItem(
        id = id,
        orgId = get<String?>("orgId") ?: "",
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        name = get<String?>("name") ?: "",
        category = runCatching {
            ExpenseCategory.valueOf(get<String?>("category") ?: "")
        }.getOrElse { ExpenseCategory.OTROS },
        amount = get<Double?>("amount") ?: get<Long?>("amount")?.toDouble() ?: 0.0,
        notes = get<String?>("notes"),
        active = get<Boolean?>("active") ?: true,
        updatedAt = runCatching { get<Long?>("updatedAt") }.getOrNull() ?: 0L
    )
}
