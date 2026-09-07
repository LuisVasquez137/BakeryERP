package com.bakery.erp.data

import com.bakery.erp.domain.FixedAssetRepository
import com.bakery.erp.model.AssetStatus
import com.bakery.erp.model.FixedAsset
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock

class FirebaseFixedAssetRepository(
    private val firestore: FirebaseFirestore
) : FixedAssetRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/assets")

    override suspend fun getAssets(companyId: String): List<FixedAsset> =
        col(companyId).get().documents
            .map { it.toAsset() }
            .filter { it.active }
            .sortedByDescending { it.updatedAt }

    override suspend fun getAsset(companyId: String, assetId: String): FixedAsset? =
        runCatching { col(companyId).document(assetId).get().toAsset() }.getOrNull()

    override suspend fun saveAsset(companyId: String, asset: FixedAsset): FixedAsset {
        val docRef = if (asset.id.isEmpty()) col(companyId).document
                     else col(companyId).document(asset.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val final = asset.copy(
            id = docRef.id,
            createdAt = if (asset.createdAt == 0L) now else asset.createdAt,
            updatedAt = now
        )
        docRef.set(final.toMap())
        return final
    }

    override suspend fun deleteAsset(companyId: String, assetId: String) {
        // Borrado lógico — conserva el historial para auditoría.
        val now = Clock.System.now().toEpochMilliseconds()
        col(companyId).document(assetId).update(
            mapOf(
                "active" to false,
                "updatedAt" to now.toDouble()
            )
        )
    }

    // ── Serialization ────────────────────────────────────────────────────────

    private fun FixedAsset.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "assetCode" to assetCode,
        "name" to name,
        "description" to description,
        "category" to category,
        "imageUrl" to imageUrl,
        "branchId" to branchId,
        "branchName" to branchName,
        "location" to location,
        "responsible" to responsible,
        "purchaseDate" to purchaseDate,
        "purchaseCost" to purchaseCost,
        "currency" to currency,
        "supplierName" to supplierName,
        "invoiceNumber" to invoiceNumber,
        "brand" to brand,
        "model" to model,
        "serialNumber" to serialNumber,
        "warrantyUntil" to warrantyUntil,
        "status" to status.name,
        "usefulLifeMonths" to usefulLifeMonths,
        "residualValue" to residualValue,
        "nextMaintenanceDate" to nextMaintenanceDate,
        "maintenanceIntervalDays" to maintenanceIntervalDays,
        "totalMaintenanceCost" to totalMaintenanceCost,
        "totalRepairCost" to totalRepairCost,
        "totalUpgradeCost" to totalUpgradeCost,
        "historyCount" to historyCount,
        "notes" to notes,
        "active" to active,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    private fun DocumentSnapshot.toAsset(): FixedAsset {
        val statusStr = runCatching { get<String?>("status") }.getOrNull()
        val status = statusStr?.let { runCatching { AssetStatus.valueOf(it) }.getOrNull() }
            ?: AssetStatus.ACTIVE
        return FixedAsset(
            id = id,
            assetCode = get<String?>("assetCode") ?: "",
            name = get<String?>("name") ?: "",
            description = get<String?>("description") ?: "",
            category = get<String?>("category") ?: "",
            imageUrl = get<String?>("imageUrl"),
            branchId = get<String?>("branchId") ?: "",
            branchName = get<String?>("branchName") ?: "",
            location = get<String?>("location") ?: "",
            responsible = get<String?>("responsible"),
            purchaseDate = readTimestampAsMillis("purchaseDate"),
            purchaseCost = get<Double?>("purchaseCost") ?: 0.0,
            currency = get<String?>("currency") ?: "GTQ",
            supplierName = get<String?>("supplierName"),
            invoiceNumber = get<String?>("invoiceNumber"),
            brand = get<String?>("brand"),
            model = get<String?>("model"),
            serialNumber = get<String?>("serialNumber"),
            warrantyUntil = runCatching { get<Long?>("warrantyUntil") }.getOrNull(),
            status = status,
            usefulLifeMonths = runCatching { get<Long?>("usefulLifeMonths") }.getOrNull()?.toInt(),
            residualValue = get<Double?>("residualValue") ?: 0.0,
            nextMaintenanceDate = runCatching { get<Long?>("nextMaintenanceDate") }.getOrNull(),
            maintenanceIntervalDays = runCatching { get<Long?>("maintenanceIntervalDays") }.getOrNull()?.toInt(),
            totalMaintenanceCost = get<Double?>("totalMaintenanceCost") ?: 0.0,
            totalRepairCost = get<Double?>("totalRepairCost") ?: 0.0,
            totalUpgradeCost = get<Double?>("totalUpgradeCost") ?: 0.0,
            historyCount = runCatching { get<Long?>("historyCount") }.getOrNull()?.toInt() ?: 0,
            notes = get<String?>("notes"),
            active = get<Boolean?>("active") ?: true,
            createdAt = readTimestampAsMillis("createdAt"),
            updatedAt = readTimestampAsMillis("updatedAt")
        )
    }

    private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
