package com.bakery.erp.data

import com.bakery.erp.domain.ExternalCustomerRepository
import com.bakery.erp.model.ExternalCustomer
import com.bakery.erp.model.ExternalCustomerType
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebaseExternalCustomerRepository(
    private val firestore: FirebaseFirestore
) : ExternalCustomerRepository {

    private fun customersCol(companyId: String) =
        firestore.collection("companies/$companyId/externalCustomers")

    override fun observeCustomers(companyId: String): Flow<List<ExternalCustomer>> =
        customersCol(companyId).snapshots.map { snapshot ->
            snapshot.documents.map { it.toCustomer() }.sortedBy { it.name.lowercase() }
        }

    override suspend fun getCustomers(companyId: String): List<ExternalCustomer> =
        customersCol(companyId).get().documents
            .map { it.toCustomer() }
            .sortedBy { it.name.lowercase() }

    override suspend fun getCustomer(companyId: String, customerId: String): ExternalCustomer? =
        runCatching { customersCol(companyId).document(customerId).get().toCustomer() }.getOrNull()

    override suspend fun getCustomerByLinkedUser(
        companyId: String,
        userId: String
    ): ExternalCustomer? =
        customersCol(companyId).get().documents
            .map { it.toCustomer() }
            .firstOrNull { it.linkedUserId == userId }

    override suspend fun saveCustomer(companyId: String, customer: ExternalCustomer) {
        val docRef = if (customer.id.isEmpty()) customersCol(companyId).document
                     else customersCol(companyId).document(customer.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val final = customer.copy(
            id = docRef.id,
            updatedAt = now,
            createdAt = if (customer.createdAt == 0L) now else customer.createdAt
        )
        docRef.set(final.toMap())
    }

    override suspend fun deleteCustomer(companyId: String, customerId: String) {
        customersCol(companyId).document(customerId).delete()
    }

    override suspend fun approveReseller(companyId: String, customer: ExternalCustomer) {
        val now = Clock.System.now().toEpochMilliseconds()
        val approved = customer.copy(
            registrationStatus = com.bakery.erp.model.ResellerRegistrationStatus.ACTIVE,
            active = true,
            updatedAt = now
        )
        customersCol(companyId).document(customer.id).set(approved.toMap())
        // Liberar al User vinculado para que pueda entrar a la app.
        if (customer.linkedUserId.isNotEmpty()) {
            firestore.collection("users").document(customer.linkedUserId)
                .update("isApproved" to true)
        }
    }

    override suspend fun rejectReseller(companyId: String, customer: ExternalCustomer) {
        val now = Clock.System.now().toEpochMilliseconds()
        val rejected = customer.copy(
            registrationStatus = com.bakery.erp.model.ResellerRegistrationStatus.REJECTED,
            active = false,
            updatedAt = now
        )
        customersCol(companyId).document(customer.id).set(rejected.toMap())
        // No reactivamos isApproved; el revendedor queda bloqueado en pending approval.
    }

    private fun ExternalCustomer.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "type" to type,
        "name" to name,
        "businessName" to businessName,
        "phone" to phone,
        "phoneAlt" to phoneAlt,
        "email" to email,
        "address" to address,
        "taxId" to taxId,
        "priceListId" to priceListId,
        "priceListName" to priceListName,
        "linkedUserId" to linkedUserId,
        "active" to active,
        "deliveryMode" to deliveryMode,
        "preferredBranchId" to preferredBranchId,
        "preferredBranchName" to preferredBranchName,
        "preferredPickupTime" to preferredPickupTime,
        "deliveryLatitude" to deliveryLatitude,
        "deliveryLongitude" to deliveryLongitude,
        "deliveryAddressReference" to deliveryAddressReference,
        "assignedProducingBranchId" to assignedProducingBranchId,
        "assignedProducingBranchName" to assignedProducingBranchName,
        "assignedShiftId" to assignedShiftId,
        "assignedShiftName" to assignedShiftName,
        "registrationStatus" to registrationStatus,
        "notes" to notes,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    private fun DocumentSnapshot.toCustomer() = ExternalCustomer(
        id = id,
        type = get<String?>("type") ?: ExternalCustomerType.RESELLER,
        name = get<String?>("name") ?: "",
        businessName = get<String?>("businessName") ?: "",
        phone = get<String?>("phone") ?: "",
        phoneAlt = get<String?>("phoneAlt") ?: "",
        email = get<String?>("email") ?: "",
        address = get<String?>("address") ?: "",
        taxId = get<String?>("taxId") ?: "",
        priceListId = get<String?>("priceListId") ?: "",
        priceListName = get<String?>("priceListName") ?: "",
        linkedUserId = get<String?>("linkedUserId") ?: "",
        active = runCatching { get<Boolean?>("active") }.getOrNull() ?: true,
        deliveryMode = get<String?>("deliveryMode")
            ?: com.bakery.erp.model.ResellerDeliveryMode.PICKUP,
        preferredBranchId = get<String?>("preferredBranchId") ?: "",
        preferredBranchName = get<String?>("preferredBranchName") ?: "",
        preferredPickupTime = get<String?>("preferredPickupTime") ?: "",
        deliveryLatitude = runCatching { get<Double?>("deliveryLatitude") }.getOrNull(),
        deliveryLongitude = runCatching { get<Double?>("deliveryLongitude") }.getOrNull(),
        deliveryAddressReference = get<String?>("deliveryAddressReference") ?: "",
        assignedProducingBranchId = get<String?>("assignedProducingBranchId") ?: "",
        assignedProducingBranchName = get<String?>("assignedProducingBranchName") ?: "",
        assignedShiftId = get<String?>("assignedShiftId") ?: "",
        assignedShiftName = get<String?>("assignedShiftName") ?: "",
        registrationStatus = get<String?>("registrationStatus")
            ?: com.bakery.erp.model.ResellerRegistrationStatus.ACTIVE,
        notes = get<String?>("notes") ?: "",
        createdAt = readMillis("createdAt"),
        updatedAt = readMillis("updatedAt")
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
