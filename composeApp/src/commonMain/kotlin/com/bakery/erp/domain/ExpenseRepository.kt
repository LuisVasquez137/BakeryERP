package com.bakery.erp.domain

import com.bakery.erp.domain.report.ExpenseSummary
import com.bakery.erp.model.Expense

interface ExpenseRepository {
    suspend fun getExpenses(companyId: String): List<Expense>
    suspend fun addExpense(companyId: String, expense: Expense)
    suspend fun updateExpense(companyId: String, expense: Expense)
    suspend fun deleteExpense(companyId: String, expenseId: String)

    /**
     * Deja los gastos generados por [purchaseId] exactamente igual a [expenses]: borra los
     * que ya no corresponden y escribe los actuales.
     *
     * Se reconcilia por FACTURA y no por línea porque `updatePurchase` borra y reescribe la
     * subcolección con ids nuevos: emparejar por `sourcePurchaseLineId` fallaría en cada
     * edición y dejaría el mes duplicado. Una lista vacía revierte por completo — que es lo
     * que necesita el borrado de la compra.
     */
    suspend fun replaceExpensesForPurchase(
        companyId: String,
        purchaseId: String,
        expenses: List<Expense>
    )

    /** Gastos de un período "YYYY-MM". */
    suspend fun getExpensesForPeriod(companyId: String, period: String): List<Expense>

    /** Agrega gastos del período por categoría y sucursal. */
    suspend fun aggregateExpensesForPeriod(companyId: String, period: String): ExpenseSummary
}
