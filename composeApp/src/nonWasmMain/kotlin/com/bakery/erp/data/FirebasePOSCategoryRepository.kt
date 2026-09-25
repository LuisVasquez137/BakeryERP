package com.bakery.erp.data

import com.bakery.erp.domain.POSCategoryRepository
import com.bakery.erp.model.POSCategory
import com.bakery.erp.model.POS_CATEGORY_NAME_MAX_CHARS
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Conserva la colección Firestore original `saleQuickGroups` para evitar migración de datos.
 * Documentos legacy con campos `mode` y `displayPrice` se leen ignorando esos campos.
 */
class FirebasePOSCategoryRepository(
    private val firestore: FirebaseFirestore
) : POSCategoryRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/saleQuickGroups")

    override suspend fun getCategories(companyId: String): List<POSCategory> =
        col(companyId).get().documents.map { it.toCategory() }
            .sortedBy { it.sortOrder }

    override suspend fun getCategory(companyId: String, categoryId: String): POSCategory? =
        col(companyId).document(categoryId).get().takeIf { it.exists }?.toCategory()

    override fun observeActiveForBranch(
        companyId: String, branchId: String
    ): Flow<List<POSCategory>> =
        col(companyId).snapshots.map { snapshot ->
            snapshot.documents
                .map { it.toCategory() }
                .filter { it.active }
                .filter { it.branchIds.isEmpty() || branchId in it.branchIds }
                .sortedBy { it.sortOrder }
        }

    override suspend fun upsertCategory(companyId: String, category: POSCategory) {
        require(category.name.isNotBlank()) { "El nombre no puede estar vacío" }
        require(category.name.length <= POS_CATEGORY_NAME_MAX_CHARS) {
            "El nombre no puede tener más de $POS_CATEGORY_NAME_MAX_CHARS caracteres"
        }
        val docRef = if (category.id.isEmpty()) col(companyId).document
                     else col(companyId).document(category.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val toSave = category.copy(
            id = docRef.id,
            createdAt = if (category.createdAt == 0L) now else category.createdAt,
            updatedAt = now
        )
        docRef.set(toSave.toMap())
    }

    override suspend fun deleteCategory(companyId: String, categoryId: String) {
        col(companyId).document(categoryId).delete()
    }

    private fun POSCategory.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "name" to name,
        "productIds" to productIds,
        "branchIds" to branchIds,
        "color" to color,
        "sortOrder" to sortOrder,
        "active" to active,
        "sellAsGroup" to sellAsGroup,
        "groupUnitPrice" to groupUnitPrice,
        "groupLabel" to groupLabel,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    private fun DocumentSnapshot.toCategory() = POSCategory(
        id = id,
        name = get<String?>("name") ?: "",
        productIds = runCatching { get<List<String>?>("productIds") }.getOrNull() ?: emptyList(),
        branchIds = runCatching { get<List<String>?>("branchIds") }.getOrNull() ?: emptyList(),
        color = get<String?>("color") ?: "#0D7A8C",
        sortOrder = (get<Double?>("sortOrder") ?: 0.0).toInt(),
        active = get<Boolean?>("active") ?: true,
        sellAsGroup = get<Boolean?>("sellAsGroup") ?: false,
        groupUnitPrice = runCatching { get<Double?>("groupUnitPrice") }.getOrNull() ?: 0.0,
        groupLabel = get<String?>("groupLabel") ?: "",
        createdAt = readMillis("createdAt"),
        updatedAt = readMillis("updatedAt")
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
