package com.bakery.erp.domain

import com.bakery.erp.model.PackagingItem
import kotlinx.coroutines.flow.Flow

/** Empaques que se cobran en caja (bolsa, caja de pastel). Ver [PackagingItem]. */
interface PackagingItemRepository {
    /** Todos, activos e inactivos, ordenados por `sortOrder` y nombre. */
    suspend fun getItems(companyId: String): List<PackagingItem>

    /** Solo los activos, en vivo: un precio cambiado se ve en las cajas abiertas sin recargar. */
    fun observeActive(companyId: String): Flow<List<PackagingItem>>

    suspend fun upsertItem(companyId: String, item: PackagingItem)
    suspend fun deleteItem(companyId: String, itemId: String)
}
