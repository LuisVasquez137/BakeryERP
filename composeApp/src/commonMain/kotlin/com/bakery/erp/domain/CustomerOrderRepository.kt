package com.bakery.erp.domain

import com.bakery.erp.domain.report.CustomerOrdersSummary
import com.bakery.erp.model.CustomerOrder
import com.bakery.erp.model.CustomerOrderLine
import com.bakery.erp.model.CustomerOrderPayment
import kotlinx.coroutines.flow.Flow

interface CustomerOrderRepository {

    // ── Lectura ───────────────────────────────────────────────────────────────

    fun observeOrders(companyId: String): Flow<List<CustomerOrder>>

    /** Pedidos del cliente externo dado (sus propios encargos — vista del revendedor). */
    suspend fun getOrdersForCustomer(companyId: String, customerId: String): List<CustomerOrder>

    suspend fun getOrdersInRange(
        companyId: String,
        fromMillis: Long,
        untilMillis: Long
    ): List<CustomerOrder>

    /** Pedidos que se entregan en [branchId] con `deliveryDate` en `[fromMillis, untilMillis)`. */
    suspend fun getOrdersForDeliveryBranch(
        companyId: String,
        branchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): List<CustomerOrder>

    /**
     * Pedidos PENDING para una planta+día+turno; análogo a
     * `ProductionRepository.getPendingOrdersForProducingBranch` pero filtrando por
     * la cuádrupla completa para que el supervisor los consolide al armar el lote.
     */
    suspend fun getPendingOrdersForProducingBranch(
        companyId: String,
        producingBranchId: String,
        deliveryDate: Long,
        shiftId: String
    ): List<CustomerOrder>

    /**
     * Pedidos con `deliveryBranchId` = [sellingBranchId] que tienen al menos una
     * línea con `linkedProductionOrderId == ""` (todavía pendiente de asignar a
     * algún PO) — o al menos una línea ya asignada al [includingLinesLinkedToPO]
     * indicado (para que aparezcan al editar ese PO). NO filtra por fecha — el
     * caller filtrará.
     */
    suspend fun getPendingOrdersForSellingBranch(
        companyId: String,
        sellingBranchId: String,
        includingLinesLinkedToPO: String = ""
    ): List<CustomerOrder>

    /**
     * Marca las líneas indicadas con `linkedProductionOrderId = [productionOrderId]`
     * y recalcula el status del header del [CustomerOrder]:
     *  - todas las líneas asignadas → CONFIRMED
     *  - algunas → PARTIALLY_CONFIRMED
     *  - ninguna → PENDING
     */
    suspend fun setLinesLinkedPO(
        companyId: String,
        orderId: String,
        lineIds: List<String>,
        productionOrderId: String
    )

    /**
     * Pone `linkedProductionOrderId = ""` en las líneas indicadas y recalcula el
     * status del header. Usado al editar/cancelar un PO para liberar líneas.
     */
    suspend fun unsetLinesLinkedPO(
        companyId: String,
        orderId: String,
        lineIds: List<String>
    )

    suspend fun getOrder(companyId: String, orderId: String): CustomerOrder?

    suspend fun getOrderLines(companyId: String, orderId: String): List<CustomerOrderLine>

    suspend fun getOrderPayments(companyId: String, orderId: String): List<CustomerOrderPayment>

    // ── Escritura ─────────────────────────────────────────────────────────────

    suspend fun saveOrder(
        companyId: String,
        order: CustomerOrder,
        lines: List<CustomerOrderLine>
    )

    /**
     * Actualiza un pedido existente: header + reemplaza completamente la subcolección
     * de líneas. Falla si [order.id] está vacío.
     */
    suspend fun updateOrder(
        companyId: String,
        order: CustomerOrder,
        lines: List<CustomerOrderLine>
    )

    suspend fun updateOrderStatus(companyId: String, orderId: String, newStatus: String)

    /**
     * Cancela el pedido con bloqueos análogos a ProductionRepository: no se puede
     * cancelar si el lote vinculado ya está IN_PROGRESS o COMPLETED.
     */
    suspend fun cancelOrder(companyId: String, orderId: String)

    // ── Pagos ─────────────────────────────────────────────────────────────────

    /**
     * Registra un pago contra el pedido y recalcula `advancePaidAmount`/`balanceAmount`
     * denormalizados en el header. Si [payment.type] == BALANCE y el saldo queda en 0
     * y el pedido está en READY, lo pasa automáticamente a DELIVERED.
     */
    suspend fun recordPayment(
        companyId: String,
        orderId: String,
        payment: CustomerOrderPayment
    )

    // ── Reportes ──────────────────────────────────────────────────────────────

    suspend fun aggregateCustomerOrdersInRange(
        companyId: String,
        fromMillis: Long,
        untilMillis: Long
    ): CustomerOrdersSummary
}
