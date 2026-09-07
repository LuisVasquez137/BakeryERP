package com.bakery.erp.data

import com.bakery.erp.domain.AssetHistoryRepository
import com.bakery.erp.model.AssetEventType
import com.bakery.erp.model.AssetHistoryEntry
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock

class FirebaseAssetHistoryRepository(
    private val firestore: FirebaseFirestore
) : AssetHistoryRepository {

    private fun col(companyId: String, assetId: String) =
        firestore.collection("companies/$companyId/assets/$assetId/history")

    override suspend fun getHistory(companyId: String, assetId: String): List<AssetHistoryEntry> =
        col(companyId, assetId).get().documents
            .map { it.toEntry() }
            .sortedByDescending { it.date }

    override suspend fun addEntry(
        companyId: String,
        assetId: String,
        entry: AssetHistoryEntry
    ): AssetHistoryEntry {
        val docRef = if (entry.id.isEmpty()) col(companyId, assetId).document
                     else col(companyId, assetId).document(entry.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val final = entry.copy(
            id = docRef.id,
            assetId = assetId,
            createdAt = if (entry.createdAt == 0L) now else entry.createdAt
        )
        docRef.set(final.toMap())
        return final
    }

    override suspend fun updateEntry(companyId: String, assetId: String, entry: AssetHistoryEntry) {
        col(companyId, assetId).document(entry.id).set(entry.copy(assetId = assetId).toMap())
    }

    override suspend fun deleteEntry(companyId: String, assetId: String, entryId: String) {
        col(companyId, assetId).document(entryId).delete()
    }

    private fun AssetHistoryEntry.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "assetId" to assetId,
        "type" to type.name,
        "date" to date,
        "description" to description,
        "cost" to cost,
        "providerName" to providerName,
        "invoiceNumber" to invoiceNumber,
        "sourcePurchaseId" to sourcePurchaseId,
        "nextMaintenanceDate" to nextMaintenanceDate,
        "fromBranchId" to fromBranchId,
        "fromBranchName" to fromBranchName,
        "toBranchId" to toBranchId,
        "toBranchName" to toBranchName,
        "createdBy" to createdBy,
        "createdByName" to createdByName,
        "createdAt" to createdAt
    )

    private fun DocumentSnapshot.toEntry(): AssetHistoryEntry {
        val typeStr = runCatching { get<String?>("type") }.getOrNull()
        val type = typeStr?.let { runCatching { AssetEventType.valueOf(it) }.getOrNull() }
            ?: AssetEventType.NOTE
        return AssetHistoryEntry(
            id = id,
            assetId = get<String?>("assetId") ?: "",
            type = type,
            date = readTimestampAsMillis("date"),
            description = get<String?>("description") ?: "",
            cost = get<Double?>("cost") ?: 0.0,
            providerName = get<String?>("providerName"),
            invoiceNumber = get<String?>("invoiceNumber"),
            sourcePurchaseId = runCatching { get<String?>("sourcePurchaseId") }.getOrNull() ?: "",
            nextMaintenanceDate = runCatching { get<Long?>("nextMaintenanceDate") }.getOrNull(),
            fromBranchId = get<String?>("fromBranchId"),
            fromBranchName = get<String?>("fromBranchName"),
            toBranchId = get<String?>("toBranchId"),
            toBranchName = get<String?>("toBranchName"),
            createdBy = get<String?>("createdBy") ?: "",
            createdByName = get<String?>("createdByName") ?: "",
            createdAt = readTimestampAsMillis("createdAt")
        )
    }

    private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
