package com.bakery.erp.domain

import com.bakery.erp.model.POSCategory
import kotlinx.coroutines.flow.Flow

interface POSCategoryRepository {
    suspend fun getCategories(companyId: String): List<POSCategory>
    suspend fun getCategory(companyId: String, categoryId: String): POSCategory?

    /**
     * Activos, ordenados por `sortOrder`. Filtra `branchIds` para incluir solo los que aplican a
     * [branchId] (`branchIds` vacío = aplica a todas las sucursales).
     */
    fun observeActiveForBranch(companyId: String, branchId: String): Flow<List<POSCategory>>

    suspend fun upsertCategory(companyId: String, category: POSCategory)
    suspend fun deleteCategory(companyId: String, categoryId: String)
}
