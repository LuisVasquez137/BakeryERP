package com.bakery.erp.data

import com.bakery.erp.domain.ExpenseRepository
import com.bakery.erp.domain.report.BranchExpenseStat
import com.bakery.erp.domain.report.ExpenseSummary
import com.bakery.erp.model.CostCenter
import com.bakery.erp.model.Expense
import com.bakery.erp.model.costCenterReportKey
import com.bakery.erp.model.costCenterReportName
import com.bakery.erp.model.isFromPurchase
import com.bakery.erp.model.ExpenseCategory
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock

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

    override suspend fun replaceExpensesForPurchase(
        companyId: String,
        purchaseId: String,
        expenses: List<Expense>
    ) {
        // Igualdad simple sobre un campo: no necesita índice compuesto.
        val existing = runCatching {
            col(companyId).where { "sourcePurchaseId" equalTo purchaseId }.get().documents
        }.getOrElse { emptyList() }
        existing.forEach { col(companyId).document(it.id).delete() }
        expenses.forEach { expense ->
            val docRef = col(companyId).document
            docRef.set(expense.copy(id = docRef.id, sourcePurchaseId = purchaseId).toMap())
        }
    }

    override suspend fun getExpensesForPeriod(companyId: String, period: String): List<Expense> =
        col(companyId).get().documents.map { it.toExpense() }.filter { it.period == period }

    override suspend fun aggregateExpensesForPeriod(
        companyId: String, period: String
    ): ExpenseSummary {
        val expenses = getExpensesForPeriod(companyId, period)
        if (expenses.isEmpty()) return ExpenseSummary()
        val total = expenses.sumOf { it.amount }
        val byCategory = expenses.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        // Generales y Reparto salen en su propio renglón aunque se hayan anotado en una
        // sucursal: si no, la planta donde se paga todo cargaría con el contador y el piloto.
        val byBranch = expenses
            .groupBy {
                costCenterReportKey(it.costCenter, it.branchId) to
                    costCenterReportName(it.costCenter, it.branchName)
            }
            .map { (key, list) ->
                BranchExpenseStat(
                    branchId = key.first,
                    branchName = key.second.ifEmpty { "Sin sucursal" },
                    total = list.sumOf { it.amount }
                )
            }
            .sortedByDescending { it.total }
        return ExpenseSummary(
            total = total,
            recordsCount = expenses.size,
            byCategory = byCategory,
            byBranch = byBranch,
            // Sin este corte, el flujo de caja restaría dos veces los suministros y los
            // servicios: una dentro de la compra que los generó y otra como gasto del mes.
            fromPurchases = expenses.filter { it.isFromPurchase }.sumOf { it.amount }
        )
    }

    // ── Serialization ────────────────────────────────────────────────────────

    private fun Expense.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "orgId" to orgId,
        "branchId" to branchId,
        "branchName" to branchName,
        "name" to name,
        "category" to category.name,
        "amount" to amount,
        "period" to period,
        "notes" to notes,
        "sourcePurchaseId" to sourcePurchaseId,
        "sourcePurchaseLineId" to sourcePurchaseLineId,
        "paidFrom" to paidFrom,
        "cashSessionId" to cashSessionId,
        "cashRegisterName" to cashRegisterName,
        "photoUrl" to photoUrl,
        "costCenter" to costCenter,
        "createdByUserId" to createdByUserId,
        "createdByUserName" to createdByUserName,
        "createdByEmployeeId" to createdByEmployeeId,
        "createdAt" to if (createdAt == 0L) Clock.System.now().toEpochMilliseconds() else createdAt,
        "updatedAt" to Clock.System.now().toEpochMilliseconds()
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
        paidFrom = get<String?>("paidFrom") ?: "",
        cashSessionId = get<String?>("cashSessionId") ?: "",
        cashRegisterName = get<String?>("cashRegisterName") ?: "",
        photoUrl = get<String?>("photoUrl") ?: "",
        costCenter = CostCenter.normalize(runCatching { get<String?>("costCenter") }.getOrNull()),
        sourcePurchaseId = runCatching { get<String?>("sourcePurchaseId") }.getOrNull() ?: "",
        sourcePurchaseLineId = runCatching { get<String?>("sourcePurchaseLineId") }.getOrNull() ?: "",
        createdByUserId = runCatching { get<String?>("createdByUserId") }.getOrNull() ?: "",
        createdByUserName = runCatching { get<String?>("createdByUserName") }.getOrNull() ?: "",
        createdByEmployeeId = runCatching { get<String?>("createdByEmployeeId") }.getOrNull() ?: "",
        createdAt = readTimestampAsMillis("createdAt"),
        updatedAt = readTimestampAsMillis("updatedAt")
    )

    private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
