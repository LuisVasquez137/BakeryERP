package com.bakery.erp.data

import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.ProductionOrder
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.ProductionOrderStatus
import com.bakery.erp.model.ProductionShift
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds

class FirebaseProductionRepository(
    private val firestore: FirebaseFirestore
) : ProductionRepository {

    private fun shiftsCol(companyId: String) =
        firestore.collection("companies/$companyId/productionShifts")

    private fun ordersCol(companyId: String) =
        firestore.collection("companies/$companyId/productionOrders")

    private fun orderLinesCol(companyId: String, orderId: String) =
        firestore.collection("companies/$companyId/productionOrders/$orderId/lines")

    private fun lotsCol(companyId: String) =
        firestore.collection("companies/$companyId/productionLots")

    private fun lotLinesCol(companyId: String, lotId: String) =
        firestore.collection("companies/$companyId/productionLots/$lotId/lines")

    // ── Turnos ────────────────────────────────────────────────────────────────

    override suspend fun getShiftsForBranch(companyId: String, branchId: String): List<ProductionShift> =
        shiftsCol(companyId).get().documents
            .map { it.toShift() }
            .filter { it.branchId == branchId && it.active }

    override suspend fun saveShift(companyId: String, shift: ProductionShift) {
        val docRef = if (shift.id.isEmpty()) shiftsCol(companyId).document
                     else shiftsCol(companyId).document(shift.id)
        val now = System.currentTimeMillis()
        val final = shift.copy(
            id = docRef.id,
            updatedAt = now,
            createdAt = if (shift.createdAt == 0L) now else shift.createdAt
        )
        docRef.set(final.toShiftMap())
    }

    override suspend fun deleteShift(companyId: String, shiftId: String) {
        shiftsCol(companyId).document(shiftId).delete()
    }

    // ── Pedidos ───────────────────────────────────────────────────────────────

    override suspend fun getOrdersForBranch(companyId: String, branchId: String): List<ProductionOrder> =
        ordersCol(companyId).get().documents
            .map { it.toOrder() }
            .filter { it.sellingBranchId == branchId }
            .sortedByDescending { it.requestedDate }

    override suspend fun getOrdersForProducingBranch(
        companyId: String,
        producingBranchId: String,
        requestedDate: Long
    ): List<ProductionOrder> =
        ordersCol(companyId).get().documents
            .map { it.toOrder() }
            .filter { it.producingBranchId == producingBranchId && it.requestedDate == requestedDate }
            .sortedBy { it.sellingBranchName }

    override suspend fun getOrder(companyId: String, orderId: String): ProductionOrder? =
        runCatching { ordersCol(companyId).document(orderId).get().toOrder() }.getOrNull()

    override suspend fun getOrderLines(companyId: String, orderId: String): List<ProductionOrderLine> =
        orderLinesCol(companyId, orderId).get().documents.map { it.toOrderLine() }

    override suspend fun saveOrder(
        companyId: String,
        order: ProductionOrder,
        lines: List<ProductionOrderLine>
    ) {
        val docRef = if (order.id.isEmpty()) ordersCol(companyId).document
                     else ordersCol(companyId).document(order.id)
        val now = System.currentTimeMillis()
        val final = order.copy(
            id = docRef.id,
            lineCount = lines.size,
            updatedAt = now,
            createdAt = if (order.createdAt == 0L) now else order.createdAt
        )
        docRef.set(final.toOrderMap())
        lines.forEach { line ->
            val lineDoc = if (line.id.isEmpty()) orderLinesCol(companyId, docRef.id).document
                          else orderLinesCol(companyId, docRef.id).document(line.id)
            lineDoc.set(line.copy(id = lineDoc.id).toOrderLineMap())
        }
    }

    override suspend fun updateOrderStatus(companyId: String, orderId: String, newStatus: String) {
        val order = getOrder(companyId, orderId) ?: return
        ordersCol(companyId).document(orderId).set(
            order.copy(status = newStatus, updatedAt = System.currentTimeMillis()).toOrderMap()
        )
    }

    // ── Lotes ─────────────────────────────────────────────────────────────────

    override suspend fun getLotsForBranch(companyId: String, branchId: String): List<ProductionLot> =
        lotsCol(companyId).get().documents
            .map { it.toLot() }
            .filter { it.producingBranchId == branchId }
            .sortedByDescending { it.productionDate }

    override suspend fun getLot(companyId: String, lotId: String): ProductionLot? =
        runCatching { lotsCol(companyId).document(lotId).get().toLot() }.getOrNull()

    override suspend fun getLotLines(companyId: String, lotId: String): List<ProductionLotLine> =
        lotLinesCol(companyId, lotId).get().documents.map { it.toLotLine() }

    override suspend fun saveLot(
        companyId: String,
        lot: ProductionLot,
        lines: List<ProductionLotLine>
    ) {
        val docRef = if (lot.id.isEmpty()) lotsCol(companyId).document
                     else lotsCol(companyId).document(lot.id)
        val now = System.currentTimeMillis()
        val final = lot.copy(
            id = docRef.id,
            lineCount = lines.size,
            updatedAt = now,
            createdAt = if (lot.createdAt == 0L) now else lot.createdAt
        )
        docRef.set(final.toLotMap())
        lines.forEach { line ->
            val lineDoc = if (line.id.isEmpty()) lotLinesCol(companyId, docRef.id).document
                          else lotLinesCol(companyId, docRef.id).document(line.id)
            lineDoc.set(line.copy(id = lineDoc.id).toLotLineMap())
        }
    }

    override suspend fun updateLotStatus(companyId: String, lotId: String, newStatus: String) {
        val lot = getLot(companyId, lotId) ?: return
        lotsCol(companyId).document(lotId).set(
            lot.copy(status = newStatus, updatedAt = System.currentTimeMillis()).toLotMap()
        )
    }

    override suspend fun completeLot(
        companyId: String,
        lotId: String,
        producedLines: List<ProductionLotLine>
    ) {
        val lot = getLot(companyId, lotId) ?: return
        val now = System.currentTimeMillis()
        lotsCol(companyId).document(lotId).set(
            lot.copy(
                status = ProductionLotStatus.COMPLETED,
                completedAt = now,
                updatedAt = now
            ).toLotMap()
        )
        producedLines.forEach { line ->
            val existing = getLotLines(companyId, lotId).find { it.id == line.id }
            if (existing != null) {
                lotLinesCol(companyId, lotId).document(line.id).set(
                    existing.copy(producedQuantity = line.producedQuantity).toLotLineMap()
                )
            }
        }
    }

    override suspend fun markStockDeducted(companyId: String, lotId: String) {
        val lot = getLot(companyId, lotId) ?: return
        lotsCol(companyId).document(lotId).set(
            lot.copy(stockDeducted = true, updatedAt = System.currentTimeMillis()).toLotMap()
        )
    }

    // ── Serialización — Turnos ────────────────────────────────────────────────

    private fun ProductionShift.toShiftMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "branchId" to branchId,
        "branchName" to branchName,
        "name" to name,
        "startTime" to startTime,
        "endTime" to endTime,
        "active" to active,
        "createdByUserId" to createdByUserId,
        "createdByUserName" to createdByUserName,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    private fun DocumentSnapshot.toShift() = ProductionShift(
        id = id,
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        name = get<String?>("name") ?: "",
        startTime = get<String?>("startTime") ?: "",
        endTime = get<String?>("endTime") ?: "",
        active = runCatching { get<Boolean?>("active") }.getOrNull() ?: true,
        createdByUserId = get<String?>("createdByUserId") ?: "",
        createdByUserName = get<String?>("createdByUserName") ?: "",
        createdAt = readMillis("createdAt"),
        updatedAt = readMillis("updatedAt")
    )

    // ── Serialización — Pedidos ───────────────────────────────────────────────

    private fun ProductionOrder.toOrderMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "sellingBranchId" to sellingBranchId,
        "sellingBranchName" to sellingBranchName,
        "producingBranchId" to producingBranchId,
        "producingBranchName" to producingBranchName,
        "requestedDate" to requestedDate,
        "status" to status,
        "notes" to notes,
        "createdByUserId" to createdByUserId,
        "createdByUserName" to createdByUserName,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "lineCount" to lineCount
    )

    private fun ProductionOrderLine.toOrderLineMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "productId" to productId,
        "productName" to productName,
        "requestedQuantity" to requestedQuantity,
        "productUnit" to productUnit
    )

    private fun DocumentSnapshot.toOrder() = ProductionOrder(
        id = id,
        sellingBranchId = get<String?>("sellingBranchId") ?: "",
        sellingBranchName = get<String?>("sellingBranchName") ?: "",
        producingBranchId = get<String?>("producingBranchId") ?: "",
        producingBranchName = get<String?>("producingBranchName") ?: "",
        requestedDate = readMillis("requestedDate"),
        status = get<String?>("status") ?: ProductionOrderStatus.PENDING,
        notes = get<String?>("notes"),
        createdByUserId = get<String?>("createdByUserId") ?: "",
        createdByUserName = get<String?>("createdByUserName") ?: "",
        createdAt = readMillis("createdAt"),
        updatedAt = readMillis("updatedAt"),
        lineCount = runCatching { get<Long?>("lineCount")?.toInt() }.getOrNull() ?: 0
    )

    private fun DocumentSnapshot.toOrderLine() = ProductionOrderLine(
        id = id,
        productId = get<String?>("productId") ?: "",
        productName = get<String?>("productName") ?: "",
        requestedQuantity = runCatching { get<Double?>("requestedQuantity") }.getOrNull() ?: 0.0,
        productUnit = get<String?>("productUnit") ?: ""
    )

    // ── Serialización — Lotes ─────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun ProductionLot.toLotMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "producingBranchId" to producingBranchId,
        "producingBranchName" to producingBranchName,
        "shiftId" to shiftId,
        "shiftName" to shiftName,
        "productionDate" to productionDate,
        "status" to status,
        "notes" to notes,
        "createdByUserId" to createdByUserId,
        "createdByUserName" to createdByUserName,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "completedAt" to completedAt,
        "stockDeducted" to stockDeducted,
        "lineCount" to lineCount,
        "sourceOrderIds" to sourceOrderIds
    )

    private fun ProductionLotLine.toLotLineMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "productId" to productId,
        "productName" to productName,
        "productUnit" to productUnit,
        "rawWeightGrams" to rawWeightGrams,
        "plannedQuantity" to plannedQuantity,
        "producedQuantity" to producedQuantity,
        "recipeId" to recipeId,
        "recipeName" to recipeName
    )

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toLot() = ProductionLot(
        id = id,
        producingBranchId = get<String?>("producingBranchId") ?: "",
        producingBranchName = get<String?>("producingBranchName") ?: "",
        shiftId = get<String?>("shiftId") ?: "",
        shiftName = get<String?>("shiftName") ?: "",
        productionDate = readMillis("productionDate"),
        status = get<String?>("status") ?: ProductionLotStatus.OPEN,
        notes = get<String?>("notes"),
        createdByUserId = get<String?>("createdByUserId") ?: "",
        createdByUserName = get<String?>("createdByUserName") ?: "",
        createdAt = readMillis("createdAt"),
        updatedAt = readMillis("updatedAt"),
        completedAt = runCatching { get<Long?>("completedAt") }.getOrNull(),
        stockDeducted = runCatching { get<Boolean?>("stockDeducted") }.getOrNull() ?: false,
        lineCount = runCatching { get<Long?>("lineCount")?.toInt() }.getOrNull() ?: 0,
        sourceOrderIds = runCatching { get<List<String>?>("sourceOrderIds") }.getOrNull() ?: emptyList()
    )

    private fun DocumentSnapshot.toLotLine() = ProductionLotLine(
        id = id,
        productId = get<String?>("productId") ?: "",
        productName = get<String?>("productName") ?: "",
        productUnit = get<String?>("productUnit") ?: "",
        rawWeightGrams = runCatching { get<Double?>("rawWeightGrams") }.getOrNull(),
        plannedQuantity = runCatching { get<Double?>("plannedQuantity") }.getOrNull() ?: 0.0,
        producedQuantity = runCatching { get<Double?>("producedQuantity") }.getOrNull() ?: 0.0,
        recipeId = get<String?>("recipeId") ?: "",
        recipeName = get<String?>("recipeName") ?: ""
    )

    // ── Utilidad ──────────────────────────────────────────────────────────────

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
