package com.bakery.erp.domain

import com.bakery.erp.model.CashRegister
import kotlinx.coroutines.flow.Flow

interface CashRegisterRepository {
    suspend fun getAll(companyId: String): List<CashRegister>
    suspend fun get(companyId: String, registerId: String): CashRegister?
    suspend fun getForBranch(companyId: String, branchId: String): List<CashRegister>
    fun observeForBranch(companyId: String, branchId: String): Flow<List<CashRegister>>
    suspend fun upsert(companyId: String, register: CashRegister)

    /** Borrado físico del doc. Usar solo cuando la caja nunca tuvo sesiones/ventas. */
    suspend fun delete(companyId: String, registerId: String)

    /** Soft-delete: marca `deleted=true`. La caja se oculta pero conserva su historial. */
    suspend fun archive(companyId: String, registerId: String)
}
