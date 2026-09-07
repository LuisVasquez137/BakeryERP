package com.bakery.erp.domain

import com.bakery.erp.model.AssetHistoryEntry

interface AssetHistoryRepository {
    suspend fun getHistory(companyId: String, assetId: String): List<AssetHistoryEntry>
    suspend fun addEntry(companyId: String, assetId: String, entry: AssetHistoryEntry): AssetHistoryEntry
    suspend fun updateEntry(companyId: String, assetId: String, entry: AssetHistoryEntry)
    suspend fun deleteEntry(companyId: String, assetId: String, entryId: String)
}
