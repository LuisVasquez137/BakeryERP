package com.bakery.erp.data

import com.bakery.erp.domain.PriceListRepository
import com.bakery.erp.domain.PriceSource
import com.bakery.erp.domain.ResolvedPrice
import com.bakery.erp.model.PriceList
import com.bakery.erp.model.PriceListType
import com.bakery.erp.model.ProductPrice
import com.bakery.erp.model.priceDocId
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlin.time.Clock

class FirebasePriceListRepository(
    private val firestore: FirebaseFirestore
) : PriceListRepository {

    private fun listsCol(companyId: String) =
        firestore.collection("companies/$companyId/priceLists")

    private fun pricesCol(companyId: String) =
        firestore.collection("companies/$companyId/productPrices")

    private fun branchDoc(companyId: String, branchId: String) =
        firestore.collection("companies/$companyId/branches").document(branchId)

    private fun productDoc(companyId: String, productId: String) =
        firestore.collection("companies/$companyId/products").document(productId)

    // ── Listas ────────────────────────────────────────────────────────────────

    override suspend fun getLists(companyId: String): List<PriceList> =
        listsCol(companyId).get().documents.map { it.toPriceList() }

    override suspend fun getList(companyId: String, listId: String): PriceList? =
        listsCol(companyId).document(listId).get().takeIf { it.exists }?.toPriceList()

    override suspend fun addList(companyId: String, list: PriceList) {
        val docRef = if (list.id.isEmpty()) listsCol(companyId).document
        else listsCol(companyId).document(list.id)
        val toSave = list.copy(id = docRef.id)
        if (toSave.isDefault) clearDefaultFlag(companyId, toSave.type, exceptId = toSave.id)
        docRef.set(toSave.toMap())
    }

    override suspend fun updateList(companyId: String, list: PriceList) {
        if (list.isDefault) clearDefaultFlag(companyId, list.type, exceptId = list.id)
        listsCol(companyId).document(list.id).set(list.toMap())
    }

    override suspend fun deleteList(companyId: String, listId: String) {
        listsCol(companyId).document(listId).delete()
    }

    private suspend fun clearDefaultFlag(companyId: String, type: String, exceptId: String) {
        val others = listsCol(companyId).where { "type" equalTo type }.get()
            .documents.map { it.toPriceList() }
            .filter { it.id != exceptId && it.isDefault }
        others.forEach { listsCol(companyId).document(it.id).set(it.copy(isDefault = false).toMap()) }
    }

    override suspend fun ensureSeedLists(companyId: String) {
        val existing = getLists(companyId)
        if (existing.isNotEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()
        addList(companyId, PriceList(
            name = "Cliente final",
            type = PriceListType.RETAIL,
            isDefault = true,
            createdAt = now, updatedAt = now
        ))
        addList(companyId, PriceList(
            name = "Sucursales",
            type = PriceListType.WHOLESALE,
            isDefault = true,
            createdAt = now, updatedAt = now
        ))
    }

    override suspend fun getDefaultList(companyId: String, type: String): PriceList? =
        listsCol(companyId).where { "type" equalTo type }.get().documents
            .map { it.toPriceList() }
            .firstOrNull { it.isDefault && it.active }

    // ── Precios ───────────────────────────────────────────────────────────────

    override suspend fun getPricesForProduct(companyId: String, productId: String): List<ProductPrice> =
        pricesCol(companyId).where { "productId" equalTo productId }.get()
            .documents.map { it.toProductPrice() }

    override suspend fun getPricesForList(companyId: String, listId: String): List<ProductPrice> =
        pricesCol(companyId).where { "priceListId" equalTo listId }.get()
            .documents.map { it.toProductPrice() }

    override suspend fun upsertPrice(companyId: String, price: ProductPrice) {
        val id = price.id.ifEmpty { priceDocId(price.productId, price.priceListId) }
        val now = Clock.System.now().toEpochMilliseconds()
        pricesCol(companyId).document(id).set(price.copy(id = id, updatedAt = now).toMap())
    }

    override suspend fun deletePrice(companyId: String, productId: String, listId: String) {
        pricesCol(companyId).document(priceDocId(productId, listId)).delete()
    }

    override suspend fun resolveWholesalePrice(
        companyId: String,
        destinationBranchId: String,
        productId: String
    ): ResolvedPrice {
        val branchSnap = runCatching { branchDoc(companyId, destinationBranchId).get() }.getOrNull()
        val branchListId = branchSnap?.let { runCatching { it.get<String?>("defaultPriceListId") }.getOrNull() }
            .orEmpty()

        val chosenList = (branchListId.takeIf { it.isNotEmpty() }
            ?.let { getList(companyId, it) }
            ?.takeIf { it.active }
            ) ?: getDefaultList(companyId, PriceListType.WHOLESALE)
            ?: return ResolvedPrice(0.0, "", "", false, PriceSource.NONE)

        // Cascada:
        // 1) Override explícito por producto en esta lista.
        val overrideDoc = pricesCol(companyId).document(priceDocId(productId, chosenList.id)).get()
        val override = if (overrideDoc.exists) overrideDoc.toProductPrice() else null
        if (override != null && override.unitPrice > 0) {
            return ResolvedPrice(
                unitPrice = override.unitPrice,
                priceListId = chosenList.id,
                priceListName = chosenList.name,
                found = true,
                source = PriceSource.PRODUCT_OVERRIDE
            )
        }

        // 2) Factor sobre el precio de venta al cliente final (solo WHOLESALE/CUSTOM).
        val factor = chosenList.priceFactor
        if (factor != null && factor > 0 &&
            (chosenList.type == PriceListType.WHOLESALE || chosenList.type == PriceListType.CUSTOM)
        ) {
            val productSnap = runCatching { productDoc(companyId, productId).get() }.getOrNull()
            val retailPrice = productSnap?.let {
                runCatching { it.get<Double?>("salePrice") }.getOrNull()
            } ?: 0.0
            if (retailPrice > 0) {
                return ResolvedPrice(
                    unitPrice = retailPrice * factor,
                    priceListId = chosenList.id,
                    priceListName = chosenList.name,
                    found = true,
                    source = PriceSource.LIST_FACTOR
                )
            }
        }

        // 3) Sin precio resoluble.
        return ResolvedPrice(
            unitPrice = 0.0,
            priceListId = chosenList.id,
            priceListName = chosenList.name,
            found = false,
            source = PriceSource.NONE
        )
    }

    // ── Serialización ─────────────────────────────────────────────────────────

    private fun PriceList.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "name" to name,
        "type" to type,
        "isDefault" to isDefault,
        "active" to active,
        "priceFactor" to priceFactor,
        "notes" to notes,
        "createdAt" to if (createdAt == 0L) Clock.System.now().toEpochMilliseconds() else createdAt,
        "updatedAt" to Clock.System.now().toEpochMilliseconds()
    )

    private fun DocumentSnapshot.toPriceList() = PriceList(
        id = id,
        name = get<String?>("name") ?: "",
        type = get<String?>("type") ?: PriceListType.WHOLESALE,
        isDefault = get<Boolean?>("isDefault") ?: false,
        active = get<Boolean?>("active") ?: true,
        priceFactor = runCatching { get<Double?>("priceFactor") }.getOrNull(),
        notes = get<String?>("notes"),
        createdAt = readTimestampAsMillis("createdAt"),
        updatedAt = readTimestampAsMillis("updatedAt")
    )

    private fun ProductPrice.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "productId" to productId,
        "priceListId" to priceListId,
        "priceListName" to priceListName,
        "unitPrice" to unitPrice,
        "updatedAt" to Clock.System.now().toEpochMilliseconds()
    )

    private fun DocumentSnapshot.toProductPrice() = ProductPrice(
        id = id,
        productId = get<String?>("productId") ?: "",
        priceListId = get<String?>("priceListId") ?: "",
        priceListName = get<String?>("priceListName") ?: "",
        unitPrice = (get<Double?>("unitPrice") ?: 0.0),
        updatedAt = readTimestampAsMillis("updatedAt")
    )

    private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
