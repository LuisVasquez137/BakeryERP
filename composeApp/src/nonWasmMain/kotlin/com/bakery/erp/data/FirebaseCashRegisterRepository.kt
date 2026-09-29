package com.bakery.erp.data

import com.bakery.erp.domain.CashRegisterRepository
import com.bakery.erp.model.CashRegister
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebaseCashRegisterRepository(
    private val firestore: FirebaseFirestore
) : CashRegisterRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/cashRegisters")

    override suspend fun getAll(companyId: String): List<CashRegister> =
        col(companyId).get().documents.map { it.toRegister() }
            .filter { !it.deleted }
            .sortedBy { it.name }

    override suspend fun get(companyId: String, registerId: String): CashRegister? =
        col(companyId).document(registerId).get().takeIf { it.exists }?.toRegister()

    override suspend fun getForBranch(companyId: String, branchId: String): List<CashRegister> =
        col(companyId).where { "branchId" equalTo branchId }.get().documents
            .map { it.toRegister() }
            .filter { !it.deleted }
            .sortedBy { it.name }

    override fun observeForBranch(companyId: String, branchId: String): Flow<List<CashRegister>> =
        col(companyId).snapshots.map { snapshot ->
            snapshot.documents
                .map { it.toRegister() }
                .filter { it.branchId == branchId && !it.deleted }
                .sortedBy { it.name }
        }

    override suspend fun upsert(companyId: String, register: CashRegister) {
        val docRef = if (register.id.isEmpty()) col(companyId).document
                     else col(companyId).document(register.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val toSave = register.copy(
            id = docRef.id,
            createdAt = if (register.createdAt == 0L) now else register.createdAt,
            updatedAt = now
        )
        docRef.set(toSave.toMap())
    }

    override suspend fun delete(companyId: String, registerId: String) {
        col(companyId).document(registerId).delete()
    }

    override suspend fun archive(companyId: String, registerId: String) {
        val existing = get(companyId, registerId) ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        col(companyId).document(registerId)
            .set(existing.copy(deleted = true, active = false, updatedAt = now).toMap())
    }

    private fun CashRegister.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "branchId" to branchId,
        "branchName" to branchName,
        "name" to name,
        "active" to active,
        "deleted" to deleted,
        "closeSchedules" to closeSchedules,
        "closeToleranceMinutes" to closeToleranceMinutes,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    private fun DocumentSnapshot.toRegister() = CashRegister(
        id = id,
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        name = get<String?>("name") ?: "",
        active = get<Boolean?>("active") ?: true,
        deleted = get<Boolean?>("deleted") ?: false,
        closeSchedules = runCatching { get<List<String>?>("closeSchedules") }.getOrNull().orEmpty(),
        closeToleranceMinutes = runCatching { get<Long?>("closeToleranceMinutes")?.toInt() }.getOrNull()
            ?: runCatching { get<Double?>("closeToleranceMinutes")?.toInt() }.getOrNull()
            ?: com.bakery.erp.model.DEFAULT_CLOSE_TOLERANCE_MINUTES,
        createdAt = readMillis("createdAt"),
        updatedAt = readMillis("updatedAt")
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
