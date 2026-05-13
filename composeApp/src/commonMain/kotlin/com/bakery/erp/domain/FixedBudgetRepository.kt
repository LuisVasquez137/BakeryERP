package com.bakery.erp.domain

import com.bakery.erp.model.FixedBudgetItem

interface FixedBudgetRepository {
    suspend fun getFixedBudgetItems(companyId: String): List<FixedBudgetItem>
    suspend fun addFixedBudgetItem(companyId: String, item: FixedBudgetItem)
    suspend fun updateFixedBudgetItem(companyId: String, item: FixedBudgetItem)
    suspend fun deleteFixedBudgetItem(companyId: String, itemId: String)
}
