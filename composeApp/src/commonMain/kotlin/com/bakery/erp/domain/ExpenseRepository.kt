package com.bakery.erp.domain

import com.bakery.erp.domain.report.ExpenseSummary
import com.bakery.erp.model.Expense

interface ExpenseRepository {
    suspend fun getExpenses(companyId: String): List<Expense>
    suspend fun addExpense(companyId: String, expense: Expense)
    suspend fun updateExpense(companyId: String, expense: Expense)
    suspend fun deleteExpense(companyId: String, expenseId: String)

    /** Gastos de un período "YYYY-MM". */
    suspend fun getExpensesForPeriod(companyId: String, period: String): List<Expense>

    /** Agrega gastos del período por categoría y sucursal. */
    suspend fun aggregateExpensesForPeriod(companyId: String, period: String): ExpenseSummary
}
