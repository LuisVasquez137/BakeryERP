package com.bakery.erp.data

import com.bakery.erp.domain.PurchaseRepository
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseStatus
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds

class FirebasePurchaseRepository(
    private val firestore: FirebaseFirestore
) : PurchaseRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/purchases")

    private fun linesCol(companyId: String, purchaseId: String) =
        firestore.collection("companies/$companyId/purchases/$purchaseId/lines")

    override suspend fun getPurchases(companyId: String): List<Purchase> =
        col(companyId).get().documents.map { it.toPurchase() }.sortedByDescending { it.date }

    override suspend fun getPurchase(companyId: String, purchaseId: String): Purchase? =
        runCatching { col(companyId).document(purchaseId).get().toPurchase() }.getOrNull()

    override suspend fun savePurchase(companyId: String, purchase: Purchase, lines: List<PurchaseLine>) {
        val docRef = if (purchase.id.isEmpty()) col(companyId).document
                     else col(companyId).document(purchase.id)
        val finalPurchase = purchase.copy(
            id = docRef.id,
            lineCount = lines.size,
            createdAt = System.currentTimeMillis()
        )
        docRef.set(finalPurchase.toPurchaseMap())
        lines.forEach { line ->
            val lineDoc = linesCol(companyId, docRef.id).document
            lineDoc.set(line.copy(id = lineDoc.id).toLineMap())
        }
    }

    override suspend fun getLines(companyId: String, purchaseId: String): List<PurchaseLine> =
        linesCol(companyId, purchaseId).get().documents.map { it.toPurchaseLine() }

    override suspend fun updatePurchaseStatus(companyId: String, purchaseId: String, newStatus: String) {
        val purchase = getPurchase(companyId, purchaseId) ?: return
        col(companyId).document(purchaseId).set(purchase.copy(status = newStatus).toPurchaseMap())
    }

    // ── Serialización ────────────────────────────────────────────────────────

    private fun Purchase.toPurchaseMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "branchId" to branchId,
        "branchName" to branchName,
        "supplierId" to supplierId,
        "supplierName" to supplierName,
        "date" to date,
        "notes" to notes,
        "lineCount" to lineCount,
        "createdAt" to createdAt,
        "invoiceNumber" to invoiceNumber,
        "receivedById" to receivedById,
        "receivedByName" to receivedByName,
        "status" to status
    )

    private fun PurchaseLine.toLineMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "ingredientId" to ingredientId,
        "ingredientName" to ingredientName,
        "ingredientUnit" to ingredientUnit,
        "quantityReceived" to quantityReceived,
        "packagesReceived" to packagesReceived,
        "packageDescription" to packageDescription,
        "unitCost" to unitCost,
        "packageCost" to packageCost,
        "formatId" to formatId
    )

    private fun DocumentSnapshot.toPurchase() = Purchase(
        id = id,
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        supplierId = get<String?>("supplierId") ?: "",
        supplierName = get<String?>("supplierName") ?: "",
        date = readMillis("date"),
        notes = get<String?>("notes"),
        lineCount = runCatching { get<Long?>("lineCount")?.toInt() }.getOrNull() ?: 0,
        createdAt = readMillis("createdAt"),
        invoiceNumber = get<String?>("invoiceNumber"),
        receivedById = get<String?>("receivedById") ?: "",
        receivedByName = get<String?>("receivedByName") ?: "",
        status = get<String?>("status") ?: PurchaseStatus.RECEIVED_PENDING_PAYMENT
    )

    private fun DocumentSnapshot.toPurchaseLine() = PurchaseLine(
        id = id,
        ingredientId = get<String?>("ingredientId") ?: "",
        ingredientName = get<String?>("ingredientName") ?: "",
        ingredientUnit = get<String?>("ingredientUnit") ?: "",
        quantityReceived = runCatching { get<Double?>("quantityReceived") }.getOrNull() ?: 0.0,
        packagesReceived = runCatching { get<Double?>("packagesReceived") }.getOrNull() ?: 0.0,
        packageDescription = get<String?>("packageDescription") ?: "",
        unitCost = runCatching { get<Double?>("unitCost") }.getOrNull() ?: 0.0,
        packageCost = runCatching { get<Double?>("packageCost") }.getOrNull() ?: 0.0,
        formatId = get<String?>("formatId") ?: ""
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
