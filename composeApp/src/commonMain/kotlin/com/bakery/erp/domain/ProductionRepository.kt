package com.bakery.erp.domain

import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.ProductionOrder
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.ProductionShift

interface ProductionRepository {

    // ── Turnos ────────────────────────────────────────────────────────────────

    suspend fun getShiftsForBranch(companyId: String, branchId: String): List<ProductionShift>
    suspend fun saveShift(companyId: String, shift: ProductionShift)
    suspend fun deleteShift(companyId: String, shiftId: String)

    // ── Pedidos de producción ─────────────────────────────────────────────────

    suspend fun getOrdersForBranch(companyId: String, branchId: String): List<ProductionOrder>
    suspend fun getOrdersForProducingBranch(
        companyId: String,
        producingBranchId: String,
        requestedDate: Long
    ): List<ProductionOrder>
    suspend fun getOrder(companyId: String, orderId: String): ProductionOrder?
    suspend fun getOrderLines(companyId: String, orderId: String): List<ProductionOrderLine>
    suspend fun saveOrder(
        companyId: String,
        order: ProductionOrder,
        lines: List<ProductionOrderLine>
    )
    suspend fun updateOrderStatus(companyId: String, orderId: String, newStatus: String)

    // ── Lotes de producción ───────────────────────────────────────────────────

    suspend fun getLotsForBranch(companyId: String, branchId: String): List<ProductionLot>
    suspend fun getLot(companyId: String, lotId: String): ProductionLot?
    suspend fun getLotLines(companyId: String, lotId: String): List<ProductionLotLine>
    suspend fun saveLot(
        companyId: String,
        lot: ProductionLot,
        lines: List<ProductionLotLine>
    )
    suspend fun updateLotStatus(companyId: String, lotId: String, newStatus: String)
    suspend fun completeLot(
        companyId: String,
        lotId: String,
        producedLines: List<ProductionLotLine>
    )
    suspend fun markStockDeducted(companyId: String, lotId: String)
}
