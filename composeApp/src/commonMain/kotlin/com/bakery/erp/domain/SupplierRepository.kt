package com.bakery.erp.domain

import com.bakery.erp.model.Supplier

interface SupplierRepository {
    suspend fun getSuppliers(companyId: String): List<Supplier>
    suspend fun addSupplier(companyId: String, supplier: Supplier)
    suspend fun updateSupplier(companyId: String, supplier: Supplier)
    suspend fun deleteSupplier(companyId: String, supplierId: String)
}
