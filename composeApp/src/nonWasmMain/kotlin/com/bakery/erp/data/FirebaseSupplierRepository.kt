package com.bakery.erp.data

import com.bakery.erp.domain.SupplierRepository
import com.bakery.erp.model.Supplier
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock

class FirebaseSupplierRepository(
    private val firestore: FirebaseFirestore
) : SupplierRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/suppliers")

    override suspend fun getSuppliers(companyId: String): List<Supplier> =
        col(companyId).get().documents.map { it.toSupplier() }

    override suspend fun addSupplier(companyId: String, supplier: Supplier) {
        val docRef = if (supplier.id.isEmpty()) col(companyId).document
        else col(companyId).document(supplier.id)
        docRef.set(supplier.copy(id = docRef.id).toMap())
    }

    override suspend fun updateSupplier(companyId: String, supplier: Supplier) {
        col(companyId).document(supplier.id).set(supplier.toMap())
    }

    override suspend fun deleteSupplier(companyId: String, supplierId: String) {
        col(companyId).document(supplierId).delete()
    }

    // ── Serialization ────────────────────────────────────────────────────────

    private fun Supplier.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "company" to company,
        "salesperson" to salesperson,
        "phone" to phone,
        "address" to address,
        "notes" to notes,
        "active" to active,
        "createdAt" to if (createdAt == 0L) Clock.System.now().toEpochMilliseconds() else createdAt,
        "updatedAt" to Clock.System.now().toEpochMilliseconds()
    )

    private fun DocumentSnapshot.toSupplier() = Supplier(
        id = id,
        company = get<String?>("company") ?: "",
        salesperson = get<String?>("salesperson") ?: "",
        phone = get<String?>("phone") ?: "",
        address = get<String?>("address"),
        notes = get<String?>("notes"),
        active = get<Boolean?>("active") ?: true,
        createdAt = readTimestampAsMillis("createdAt"),
        updatedAt = readTimestampAsMillis("updatedAt")
    )

    private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
