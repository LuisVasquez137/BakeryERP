package com.bakery.erp.data

import com.bakery.erp.domain.PackagingItemRepository
import com.bakery.erp.model.PackagingItem
import com.bakery.erp.model.packagingItemProblem
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebasePackagingItemRepository(
    private val firestore: FirebaseFirestore
) : PackagingItemRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/packagingItems")

    override suspend fun getItems(companyId: String): List<PackagingItem> =
        col(companyId).get().documents.map { it.toItem() }.sortedForPos()

    override fun observeActive(companyId: String): Flow<List<PackagingItem>> =
        col(companyId).snapshots.map { snapshot ->
            snapshot.documents.map { it.toItem() }.filter { it.active }.sortedForPos()
        }

    override suspend fun upsertItem(companyId: String, item: PackagingItem) {
        packagingItemProblem(item)?.let { throw IllegalArgumentException(it) }
        val docRef = if (item.id.isEmpty()) col(companyId).document
                     else col(companyId).document(item.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val toSave = item.copy(
            id = docRef.id,
            name = item.name.trim(),
            createdAt = if (item.createdAt == 0L) now else item.createdAt,
            updatedAt = now
        )
        docRef.set(toSave.toMap())
    }

    override suspend fun deleteItem(companyId: String, itemId: String) {
        col(companyId).document(itemId).delete()
    }

    private fun List<PackagingItem>.sortedForPos() =
        sortedWith(compareBy<PackagingItem> { it.sortOrder }.thenBy { it.name.lowercase() })

    private fun PackagingItem.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "name" to name,
        "salePrice" to salePrice,
        "active" to active,
        "sortOrder" to sortOrder,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    private fun DocumentSnapshot.toItem() = PackagingItem(
        id = id,
        name = get<String?>("name") ?: "",
        salePrice = runCatching { get<Double?>("salePrice") }.getOrNull()
            ?: runCatching { get<Long?>("salePrice")?.toDouble() }.getOrNull() ?: 0.0,
        active = get<Boolean?>("active") ?: true,
        sortOrder = (runCatching { get<Double?>("sortOrder") }.getOrNull() ?: 0.0).toInt(),
        createdAt = readMillis("createdAt"),
        updatedAt = readMillis("updatedAt")
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
