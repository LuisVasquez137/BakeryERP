package com.bakery.erp.domain

import com.bakery.erp.model.ExternalCustomer
import kotlinx.coroutines.flow.Flow

interface ExternalCustomerRepository {

    fun observeCustomers(companyId: String): Flow<List<ExternalCustomer>>

    suspend fun getCustomers(companyId: String): List<ExternalCustomer>

    suspend fun getCustomer(companyId: String, customerId: String): ExternalCustomer?

    /** Devuelve el ExternalCustomer cuyo `linkedUserId == userId`, o null si no está vinculado. */
    suspend fun getCustomerByLinkedUser(companyId: String, userId: String): ExternalCustomer?

    suspend fun saveCustomer(companyId: String, customer: ExternalCustomer)

    suspend fun deleteCustomer(companyId: String, customerId: String)

    /**
     * Aprueba un revendedor: guarda el [ExternalCustomer] con `registrationStatus=ACTIVE`
     * y `active=true`, Y actualiza el User vinculado a `isApproved=true` (para que
     * pueda dejar la pantalla de "pendiente de aprobación" y entrar a la app).
     * El [customer] que se pase debe traer ya asignados `assignedProducingBranchId/Name`,
     * `assignedShiftId/Name` y `priceListId/Name`.
     */
    suspend fun approveReseller(companyId: String, customer: ExternalCustomer)

    /** Rechaza un revendedor: `registrationStatus=REJECTED`, `active=false`. */
    suspend fun rejectReseller(companyId: String, customer: ExternalCustomer)
}
