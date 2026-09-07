package com.bakery.erp.domain

import com.bakery.erp.model.EmployeeHistoryEntry

interface EmployeeHistoryRepository {
    suspend fun getHistory(companyId: String, employeeId: String): List<EmployeeHistoryEntry>
    suspend fun addEntry(companyId: String, employeeId: String, entry: EmployeeHistoryEntry): EmployeeHistoryEntry
    suspend fun deleteEntry(companyId: String, employeeId: String, entryId: String)
}
