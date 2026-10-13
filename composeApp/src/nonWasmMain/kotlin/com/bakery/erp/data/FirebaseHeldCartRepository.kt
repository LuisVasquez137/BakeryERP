package com.bakery.erp.data

import com.bakery.erp.domain.HeldCartRepository
import com.bakery.erp.model.HeldCart
import com.bakery.erp.model.HeldCartLine
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Los renglones van en arreglos paralelos dentro del documento: GitLive no serializa listas de
 * objetos (ver CLAUDE.md). Consulta por `sessionId` (índice de un solo campo).
 */
class FirebaseHeldCartRepository(
    private val firestore: FirebaseFirestore
) : HeldCartRepository {

    private fun col(companyId: String) = firestore.collection("companies/$companyId/heldCarts")

    override fun observeForSession(companyId: String, sessionId: String): Flow<List<HeldCart>> =
        col(companyId).where { "sessionId" equalTo sessionId }.snapshots.map { snap ->
            snap.documents.map { it.toHeldCart() }.sortedBy { it.createdAt }
        }

    override suspend fun getForSession(companyId: String, sessionId: String): List<HeldCart> =
        col(companyId).where { "sessionId" equalTo sessionId }.get().documents
            .map { it.toHeldCart() }.sortedBy { it.createdAt }

    override suspend fun save(companyId: String, cart: HeldCart): String {
        val ref = if (cart.id.isEmpty()) col(companyId).document else col(companyId).document(cart.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val c = cart.copy(id = ref.id, createdAt = if (cart.createdAt == 0L) now else cart.createdAt)
        ref.set(firestoreSafeOf(
            "id" to c.id,
            "branchId" to c.branchId,
            "registerId" to c.registerId,
            "sessionId" to c.sessionId,
            "label" to c.label,
            "lineKeys" to c.lines.map { it.key },
            "lineProductIds" to c.lines.map { it.productId },
            "lineNames" to c.lines.map { it.productName },
            "lineQuantities" to c.lines.map { it.quantity },
            "lineUnitPrices" to c.lines.map { it.unitPrice },
            "linePriceListIds" to c.lines.map { it.priceListId },
            "linePriceSources" to c.lines.map { it.priceSource },
            "lineQuickGroupIds" to c.lines.map { it.quickGroupId },
            "linePendingSaleIds" to c.lines.map { it.pendingSaleId },
            "linePackagingItemIds" to c.lines.map { it.packagingItemId },
            "resellerId" to c.resellerId,
            "resellerName" to c.resellerName,
            "createdAt" to c.createdAt,
            "createdByName" to c.createdByName
        ))
        return c.id
    }

    override suspend fun delete(companyId: String, cartId: String) {
        col(companyId).document(cartId).delete()
    }

    private fun DocumentSnapshot.strings(field: String): List<String> =
        runCatching { get<List<String>?>(field) }.getOrNull() ?: emptyList()

    private fun DocumentSnapshot.doubles(field: String): List<Double> =
        runCatching { get<List<Double>?>(field) }.getOrNull() ?: emptyList()

    private fun DocumentSnapshot.toHeldCart(): HeldCart {
        val keys = strings("lineKeys")
        val productIds = strings("lineProductIds")
        val names = strings("lineNames")
        val quantities = doubles("lineQuantities")
        val prices = doubles("lineUnitPrices")
        val lists = strings("linePriceListIds")
        val sources = strings("linePriceSources")
        val groups = strings("lineQuickGroupIds")
        val pendings = strings("linePendingSaleIds")
        val packagings = strings("linePackagingItemIds")
        return HeldCart(
            id = id,
            branchId = get<String?>("branchId") ?: "",
            registerId = get<String?>("registerId") ?: "",
            sessionId = get<String?>("sessionId") ?: "",
            label = get<String?>("label") ?: "",
            lines = keys.indices.map { i ->
                HeldCartLine(
                    key = keys[i],
                    productId = productIds.getOrElse(i) { "" },
                    productName = names.getOrElse(i) { "" },
                    quantity = quantities.getOrElse(i) { 0.0 },
                    unitPrice = prices.getOrElse(i) { 0.0 },
                    priceListId = lists.getOrElse(i) { "" },
                    priceSource = sources.getOrElse(i) { "" },
                    quickGroupId = groups.getOrElse(i) { "" },
                    pendingSaleId = pendings.getOrElse(i) { "" },
                    packagingItemId = packagings.getOrElse(i) { "" }
                )
            },
            resellerId = get<String?>("resellerId") ?: "",
            resellerName = get<String?>("resellerName") ?: "",
            createdAt = runCatching { get<Double?>("createdAt")?.toLong() }.getOrNull()
                ?: runCatching { get<Long?>("createdAt") }.getOrNull() ?: 0L,
            createdByName = get<String?>("createdByName") ?: ""
        )
    }
}
