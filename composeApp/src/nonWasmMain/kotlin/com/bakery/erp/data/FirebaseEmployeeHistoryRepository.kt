package com.bakery.erp.data

import com.bakery.erp.domain.EmployeeHistoryRepository
import com.bakery.erp.model.EmployeeHistoryEntry
import com.bakery.erp.model.HistoryEventType
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock

class FirebaseEmployeeHistoryRepository(
    private val firestore: FirebaseFirestore
) : EmployeeHistoryRepository {

    private fun col(companyId: String, employeeId: String) =
        firestore.collection("companies/$companyId/employees/$employeeId/history")

    override suspend fun getHistory(companyId: String, employeeId: String): List<EmployeeHistoryEntry> =
        col(companyId, employeeId).get().documents
            .map { it.toEntry() }
            .sortedByDescending { it.date }

    override suspend fun addEntry(
        companyId: String,
        employeeId: String,
        entry: EmployeeHistoryEntry
    ): EmployeeHistoryEntry {
        val docRef = if (entry.id.isEmpty()) col(companyId, employeeId).document
                     else col(companyId, employeeId).document(entry.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val final = entry.copy(id = docRef.id, employeeId = employeeId, createdAt = now)
        docRef.set(final.toMap())
        return final
    }

    override suspend fun deleteEntry(companyId: String, employeeId: String, entryId: String) {
        col(companyId, employeeId).document(entryId).delete()
    }

    private fun EmployeeHistoryEntry.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "employeeId" to employeeId,
        "type" to type.name,
        "description" to description,
        "amount" to amount,
        "date" to date,
        "createdBy" to createdBy,
        "createdAt" to createdAt
    )

    private fun DocumentSnapshot.toEntry(): EmployeeHistoryEntry {
        val typeStr = runCatching { get<String?>("type") }.getOrNull()
        val type = typeStr?.let { runCatching { HistoryEventType.valueOf(it) }.getOrNull() }
            ?: HistoryEventType.NOTE
        return EmployeeHistoryEntry(
            id = id,
            employeeId = get<String?>("employeeId") ?: "",
            type = type,
            description = get<String?>("description") ?: "",
            amount = runCatching { get<Double?>("amount") }.getOrNull(),
            date = runCatching { get<Long?>("date") }.getOrNull() ?: 0L,
            createdBy = get<String?>("createdBy") ?: "",
            createdAt = runCatching { get<Long?>("createdAt") }.getOrNull() ?: 0L
        )
    }
}
