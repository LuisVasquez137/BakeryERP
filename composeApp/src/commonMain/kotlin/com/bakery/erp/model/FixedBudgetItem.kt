package com.bakery.erp.model

data class FixedBudgetItem(
    val id: String = "",
    val orgId: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val name: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTROS,
    val amount: Double = 0.0,
    val notes: String? = null,
    val active: Boolean = true,
    val updatedAt: Long = 0L
)
