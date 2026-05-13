package com.bakery.erp.domain

import com.bakery.erp.model.Expense

interface ExpenseRepository {
    suspend fun getExpenses(companyId: String): List<Expense>
    suspend fun addExpense(companyId: String, expense: Expense)
    suspend fun updateExpense(companyId: String, expense: Expense)
    suspend fun deleteExpense(companyId: String, expenseId: String)
}
