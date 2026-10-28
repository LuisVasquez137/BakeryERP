package com.bakery.erp.data

import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock

class FirebaseBranchRepository(
    private val firestore: FirebaseFirestore
) : BranchRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/branches")

    override suspend fun getBranches(companyId: String): List<Branch> =
        col(companyId).get().documents.map { it.toBranch() }

    override suspend fun getBranch(companyId: String, branchId: String): Branch? =
        runCatching { col(companyId).document(branchId).get().toBranch() }.getOrNull()

    override suspend fun addBranch(companyId: String, branch: Branch) {
        val docRef = if (branch.id.isEmpty()) col(companyId).document
        else col(companyId).document(branch.id)
        docRef.set(branch.copy(id = docRef.id).toMap())
    }

    override suspend fun updateBranch(companyId: String, branch: Branch) {
        col(companyId).document(branch.id).set(branch.toMap())
    }

    override suspend fun deleteBranch(companyId: String, branchId: String) {
        col(companyId).document(branchId).delete()
    }

    override suspend fun updateProductSortOrder(
        companyId: String,
        branchId: String,
        productIds: List<String>
    ) {
        col(companyId).document(branchId).update(
            firestoreSafeOf(
                "productSortOrder" to productIds,
                "updatedAt" to Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    override suspend fun updateProductionSortOrder(
        companyId: String,
        branchId: String,
        productIds: List<String>
    ) {
        col(companyId).document(branchId).update(
            firestoreSafeOf(
                "productionSortOrder" to productIds,
                "updatedAt" to Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    override suspend fun updateDispatchBranchOrder(
        companyId: String,
        branchId: String,
        destinationBranchIds: List<String>
    ) {
        col(companyId).document(branchId).update(
            firestoreSafeOf(
                "dispatchBranchOrder" to destinationBranchIds,
                "updatedAt" to Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    override suspend fun updateDeliveryRouteOrder(
        companyId: String,
        branchId: String,
        destinationBranchIds: List<String>
    ) {
        col(companyId).document(branchId).update(
            firestoreSafeOf(
                "deliveryRouteOrder" to destinationBranchIds,
                "updatedAt" to Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    override suspend fun updateLocation(
        companyId: String,
        branchId: String,
        latitude: Double,
        longitude: Double
    ) {
        col(companyId).document(branchId).update(
            firestoreSafeOf(
                "latitude" to latitude,
                "longitude" to longitude,
                "updatedAt" to Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    // ── Serialization ────────────────────────────────────────────────────────

    private fun Branch.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "orgId" to orgId,
        "name" to name,
        "location" to location,
        "phone" to phone,
        "managerName" to managerName,
        "notes" to notes,
        "capabilities" to capabilities.map { it.name },
        "assignedProductIds" to assignedProductIds,
        "defaultPriceListId" to defaultPriceListId,
        "productSortOrder" to productSortOrder,
        "productionSortOrder" to productionSortOrder,
        "dispatchBranchOrder" to dispatchBranchOrder,
        "deliveryRouteOrder" to deliveryRouteOrder,
        "closedWeekdays" to closedWeekdays,
        "receivesOnlyInTrip" to receivesOnlyInTrip,
        "latitude" to latitude,
        "longitude" to longitude,
        // Nombres en español: los lee la Cloud Function de FEL (felEmisor.js) tal cual.
        "felProfileId" to felProfileId,
        "felCodigoEstablecimiento" to felEstablishmentCode,
        "invoiceByDefault" to invoiceByDefault,
        "storeSharePercent" to storeSharePercent,
        "dailyCourtesyQuota" to dailyCourtesyQuota,
        "dailyStaffBreadUnits" to dailyStaffBreadUnits,
        "active" to active,
        "createdAt" to if (createdAt == 0L) Clock.System.now().toEpochMilliseconds() else createdAt,
        "updatedAt" to Clock.System.now().toEpochMilliseconds()
    )

    private fun DocumentSnapshot.toBranch() = Branch(
        id = id,
        orgId = get<String?>("orgId") ?: "",
        name = get<String?>("name") ?: "",
        location = get<String?>("location") ?: "",
        phone = get<String?>("phone") ?: "",
        managerName = get<String?>("managerName") ?: "",
        notes = get<String?>("notes"),
        capabilities = runCatching {
            get<List<String>?>("capabilities")
                ?.mapNotNull { runCatching { BranchCapability.valueOf(it) }.getOrNull() }
                ?: emptyList()
        }.getOrElse { emptyList() },
        assignedProductIds = runCatching {
            get<List<String>?>("assignedProductIds") ?: emptyList()
        }.getOrElse { emptyList() },
        defaultPriceListId = get<String?>("defaultPriceListId") ?: "",
        productSortOrder = runCatching {
            get<List<String>?>("productSortOrder") ?: emptyList()
        }.getOrElse { emptyList() },
        productionSortOrder = runCatching {
            get<List<String>?>("productionSortOrder") ?: emptyList()
        }.getOrElse { emptyList() },
        dispatchBranchOrder = runCatching {
            get<List<String>?>("dispatchBranchOrder") ?: emptyList()
        }.getOrElse { emptyList() },
        deliveryRouteOrder = runCatching {
            get<List<String>?>("deliveryRouteOrder") ?: emptyList()
        }.getOrElse { emptyList() },
        closedWeekdays = runCatching {
            get<List<Long>?>("closedWeekdays")?.map { it.toInt() } ?: emptyList()
        }.getOrElse { emptyList() },
        receivesOnlyInTrip = runCatching {
            get<Long?>("receivesOnlyInTrip")?.toInt()
        }.getOrNull() ?: 0,
        latitude = runCatching { get<Double?>("latitude") }.getOrNull(),
        longitude = runCatching { get<Double?>("longitude") }.getOrNull(),
        felProfileId = runCatching { get<String?>("felProfileId") }.getOrNull() ?: "",
        felEstablishmentCode = runCatching { get<String?>("felCodigoEstablecimiento") }.getOrNull() ?: "",
        invoiceByDefault = runCatching { get<Boolean?>("invoiceByDefault") }.getOrNull() ?: false,
        // Un 15 guardado desde la web llega como entero.
        storeSharePercent = runCatching { get<Double?>("storeSharePercent") }.getOrNull()
            ?: runCatching { get<Long?>("storeSharePercent")?.toDouble() }.getOrNull() ?: 0.0,
        dailyCourtesyQuota = runCatching { get<Double?>("dailyCourtesyQuota") }.getOrNull()
            ?: runCatching { get<Long?>("dailyCourtesyQuota")?.toDouble() }.getOrNull() ?: 0.0,
        dailyStaffBreadUnits = runCatching { get<Double?>("dailyStaffBreadUnits") }.getOrNull()
            ?: runCatching { get<Long?>("dailyStaffBreadUnits")?.toDouble() }.getOrNull() ?: 0.0,
        active = get<Boolean?>("active") ?: true,
        createdAt = readTimestampAsMillis("createdAt"),
        updatedAt = readTimestampAsMillis("updatedAt")
    )

    private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
