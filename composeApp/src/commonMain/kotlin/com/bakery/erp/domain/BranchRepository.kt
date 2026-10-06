package com.bakery.erp.domain

import com.bakery.erp.model.Branch

interface BranchRepository {
    suspend fun getBranches(companyId: String): List<Branch>
    suspend fun getBranch(companyId: String, branchId: String): Branch?
    suspend fun addBranch(companyId: String, branch: Branch)
    suspend fun updateBranch(companyId: String, branch: Branch)
    suspend fun deleteBranch(companyId: String, branchId: String)

    /**
     * Actualiza solo [Branch.productSortOrder]. Escritura parcial a propósito:
     * el orden se configura desde el consolidado de pedidos, donde no se tiene
     * el documento completo de la sucursal y un `set()` la sobreescribiría.
     */
    suspend fun updateProductSortOrder(
        companyId: String,
        branchId: String,
        productIds: List<String>
    )

    /**
     * Actualiza solo [Branch.productionSortOrder] — el orden de la orden de
     * producción del panadero. Mismo criterio de escritura parcial.
     */
    suspend fun updateProductionSortOrder(
        companyId: String,
        branchId: String,
        productIds: List<String>
    )

    /**
     * Actualiza solo [Branch.dispatchBranchOrder] — el orden en que la planta ve
     * las sucursales destino al empacar. Mismo criterio de escritura parcial: se
     * configura desde la matriz de empaque, que no tiene el documento completo.
     */
    suspend fun updateDispatchBranchOrder(
        companyId: String,
        branchId: String,
        destinationBranchIds: List<String>
    )

    /**
     * Actualiza solo [Branch.deliveryRouteOrder] — la ruta fija del camión de esa
     * planta. Escritura parcial por lo mismo que los demás órdenes: se configura desde
     * Reparto, que no tiene el documento completo de la sucursal.
     */
    suspend fun updateDeliveryRouteOrder(
        companyId: String,
        branchId: String,
        destinationBranchIds: List<String>
    )

    /**
     * Actualiza solo [Branch.latitude]/[Branch.longitude]. Escritura parcial:
     * se llama desde el formulario de pedido, donde no se tiene el documento
     * completo, y un `.set()` lo sobreescribiría. Una sola vez por sucursal
     * (ver [Branch.latitude]).
     */
    suspend fun updateLocation(
        companyId: String,
        branchId: String,
        latitude: Double,
        longitude: Double
    )
}
