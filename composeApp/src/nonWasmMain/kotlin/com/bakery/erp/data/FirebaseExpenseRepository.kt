package com.bakery.erp.data

import com.bakery.erp.domain.ExpenseRepository
import com.bakery.erp.model.Expense
import com.bakery.erp.model.ExpenseCategory
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds

class FirebaseExpenseRepository(
    private val firestore: FirebaseFirestore
) : ExpenseRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/expenses")

    override suspend fun getExpenses(companyId: String): List<Expense> =
        col(companyId).get().documents.map { it.toExpense() }

    override suspend fun addExpense(companyId: String, expense: Expense) {
        val docRef = if (expense.id.isEmpty()) col(companyId).document
        else col(companyId).document(expense.id)
        docRef.set(expense.copy(id = docRef.id).toMap())
    }

    override suspend fun updateExpense(companyId: String, expense: Expense) {
        col(companyId).document(expense.id).set(expense.toMap())
    }

    override suspend fun deleteExpense(companyId: String, expenseId: String) {
        col(companyId).document(expenseId).delete()
    }

    // ── Serialization ────────────────────────────────────────────────────────

    private fun Expense.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "orgId" to orgId,
        "branchId" to branchId,
        "branchName" to branchName,
        "name" to name,
        "category" to category.name,
        "amount" to amount,
        "period" to period,
        "notes" to notes,
        "createdAt" to if (createdAt == 0L) System.currentTimeMillis() else createdAt,
        "updatedAt" to System.currentTimeMillis()
    )

    private fun DocumentSnapshot.toExpense() = Expense(
        id = id,
        orgId = get<String?>("orgId") ?: "",
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        name = get<String?>("name") ?: "",
        category = runCatching {
            ExpenseCategory.valueOf(get<String?>("category") ?: "")
        }.getOrElse { ExpenseCategory.OTROS },
        amount = get<Double?>("amount") ?: get<Long?>("amount")?.toDouble() ?: 0.0,
        period = get<String?>("period") ?: "",
        notes = get<String?>("notes"),
        createdAt = readTimestampAsMillis("createdAt"),
        updatedAt = readTimestampAsMillis("updatedAt")
    )

    private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
