package com.bakery.erp.data

import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.domain.report.DailyProductionPoint
import com.bakery.erp.domain.report.ProductProductionStat
import com.bakery.erp.domain.report.ProductionSummary
import com.bakery.erp.domain.report.ShiftProductionStat
import com.bakery.erp.model.CustomerLineSpec
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotDecoration
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.CustomerLineRef
import com.bakery.erp.model.ProductionOrder
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.ProductionOrderStatus
import com.bakery.erp.model.ProductionShift
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

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
        val now = Clock.System.now().toEpochMilliseconds()
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

    override fun observeOrders(companyId: String): Flow<List<ProductionOrder>> =
        ordersCol(companyId).snapshots.map { snapshot ->
            snapshot.documents.map { it.toOrder() }
        }

    override fun observeLots(companyId: String): Flow<List<ProductionLot>> =
        lotsCol(companyId).snapshots.map { snapshot ->
            snapshot.documents.map { it.toLot() }
        }

    override fun observeLotLines(companyId: String, lotId: String): Flow<List<ProductionLotLine>> =
        lotLinesCol(companyId, lotId).snapshots.map { snapshot ->
            snapshot.documents.map { it.toLotLine() }
        }

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

    override suspend fun getOrdersForSellingBranchInRange(
        companyId: String,
        sellingBranchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): List<ProductionOrder> =
        ordersCol(companyId).get().documents
            .map { it.toOrder() }
            .filter {
                it.sellingBranchId == sellingBranchId &&
                    it.requestedDate in fromMillis until untilMillis
            }
            .sortedByDescending { it.requestedDate }

    override suspend fun getOrdersForProducingBranchInRange(
        companyId: String,
        producingBranchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): List<ProductionOrder> =
        ordersCol(companyId).get().documents
            .map { it.toOrder() }
            .filter {
                it.producingBranchId == producingBranchId &&
                    it.requestedDate in fromMillis until untilMillis
            }
            .sortedByDescending { it.requestedDate }

    override suspend fun getPendingOrdersForProducingBranch(
        companyId: String,
        producingBranchId: String
    ): List<ProductionOrder> =
        ordersCol(companyId).get().documents
            .map { it.toOrder() }
            .filter { it.producingBranchId == producingBranchId && it.status == ProductionOrderStatus.PENDING }
            .sortedBy { it.requestedDate }

    override suspend fun findPendingOrderForCombination(
        companyId: String,
        sellingBranchId: String,
        producingBranchId: String,
        requestedDate: Long,
        shiftId: String
    ): ProductionOrder? {
        val tz = TimeZone.currentSystemDefault()
        val targetDay = Instant.fromEpochMilliseconds(requestedDate).toLocalDateTime(tz).date
        return ordersCol(companyId).get().documents
            .map { it.toOrder() }
            .firstOrNull {
                it.status == ProductionOrderStatus.PENDING &&
                    it.sellingBranchId == sellingBranchId &&
                    it.producingBranchId == producingBranchId &&
                    it.shiftId == shiftId &&
                    Instant.fromEpochMilliseconds(it.requestedDate).toLocalDateTime(tz).date == targetDay
            }
    }

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
        val now = Clock.System.now().toEpochMilliseconds()
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

    override suspend fun updateOrder(
        companyId: String,
        order: ProductionOrder,
        lines: List<ProductionOrderLine>
    ) {
        require(order.id.isNotEmpty()) { "updateOrder requiere un id de pedido" }
        val now = Clock.System.now().toEpochMilliseconds()
        val final = order.copy(
            lineCount = lines.size,
            updatedAt = now,
            createdAt = if (order.createdAt == 0L) now else order.createdAt
        )
        ordersCol(companyId).document(order.id).set(final.toOrderMap())

        // Reemplaza completamente la subcolección de líneas: borra las que ya no
        // están y escribe/actualiza el resto. Esto soporta el caso "encargada
        // eliminó un producto del pedido durante la edición".
        val existingIds = orderLinesCol(companyId, order.id).get().documents.map { it.id }.toSet()
        val keptIds = mutableSetOf<String>()
        lines.forEach { line ->
            val lineDoc = if (line.id.isEmpty()) orderLinesCol(companyId, order.id).document
                          else orderLinesCol(companyId, order.id).document(line.id)
            lineDoc.set(line.copy(id = lineDoc.id).toOrderLineMap())
            keptIds += lineDoc.id
        }
        (existingIds - keptIds).forEach { obsoleteId ->
            runCatching { orderLinesCol(companyId, order.id).document(obsoleteId).delete() }
        }
    }

    override suspend fun updateOrderStatus(companyId: String, orderId: String, newStatus: String) {
        val order = getOrder(companyId, orderId) ?: return
        ordersCol(companyId).document(orderId).set(
            order.copy(status = newStatus, updatedAt = Clock.System.now().toEpochMilliseconds()).toOrderMap()
        )
    }

    override suspend fun addOrderToLot(
        companyId: String,
        lotId: String,
        orderId: String,
        newLines: List<ProductionLotLine>
    ) {
        val lot = getLot(companyId, lotId) ?: error("Lote no encontrado: $lotId")
        if (lot.status == ProductionLotStatus.COMPLETED || lot.status == ProductionLotStatus.CANCELLED) {
            error("No se puede agregar a un lote ${lot.status}")
        }
        val existing = getLotLines(companyId, lotId)
        val byProduct = existing.associateBy { it.productId }.toMutableMap()

        for (incoming in newLines) {
            val match = byProduct[incoming.productId]
            if (match != null) {
                // Suma cantidades a la línea existente y persiste.
                val merged = match.copy(
                    plannedQuantity = match.plannedQuantity + incoming.plannedQuantity,
                    producedQuantity = match.producedQuantity + incoming.producedQuantity
                )
                lotLinesCol(companyId, lotId).document(match.id).set(merged.toLotLineMap())
                byProduct[incoming.productId] = merged
            } else {
                // Línea nueva: crea doc con id generado.
                val lineDoc = lotLinesCol(companyId, lotId).document
                val created = incoming.copy(id = lineDoc.id)
                lineDoc.set(created.toLotLineMap())
                byProduct[incoming.productId] = created
            }
        }

        // Actualiza cabecera: incrementa lineCount, agrega orderId a sourceOrderIds y refresca updatedAt.
        val now = Clock.System.now().toEpochMilliseconds()
        val updatedLot = lot.copy(
            lineCount = byProduct.size,
            sourceOrderIds = (lot.sourceOrderIds + orderId).distinct(),
            updatedAt = now
        )
        lotsCol(companyId).document(lotId).set(updatedLot.toLotMap())

        // El pedido se queda en PENDING; sólo cambia a CONFIRMED cuando el lote complete.
        // De esta forma el chip muestra "Lote abierto" / "En producción" hasta cerrar.
    }

    override suspend fun removeOrderFromLot(
        companyId: String,
        lotId: String,
        orderId: String,
        orderLines: List<ProductionOrderLine>
    ) {
        val lot = getLot(companyId, lotId) ?: error("Lote no encontrado: $lotId")
        if (lot.status == ProductionLotStatus.COMPLETED || lot.status == ProductionLotStatus.CANCELLED) {
            error("No se puede quitar el pedido de un lote ${lot.status}")
        }
        val existing = getLotLines(companyId, lotId)
        val byProduct = existing.associateBy { it.productId }.toMutableMap()

        for (toRemove in orderLines) {
            val match = byProduct[toRemove.productId] ?: continue
            val newPlanned = (match.plannedQuantity - toRemove.requestedQuantity).coerceAtLeast(0.0)
            val newProduced = (match.producedQuantity - toRemove.requestedQuantity).coerceAtLeast(0.0)
            if (newPlanned <= 0.0) {
                // Sin demanda restante: eliminar la línea.
                runCatching { lotLinesCol(companyId, lotId).document(match.id).delete() }
                byProduct.remove(toRemove.productId)
            } else {
                val updated = match.copy(plannedQuantity = newPlanned, producedQuantity = newProduced)
                lotLinesCol(companyId, lotId).document(match.id).set(updated.toLotLineMap())
                byProduct[toRemove.productId] = updated
            }
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val updatedLot = lot.copy(
            lineCount = byProduct.size,
            sourceOrderIds = lot.sourceOrderIds.filter { it != orderId },
            updatedAt = now
        )
        lotsCol(companyId).document(lotId).set(updatedLot.toLotMap())
    }

    override suspend fun addCustomerOrderToLot(
        companyId: String,
        lotId: String,
        customerOrderId: String,
        newLines: List<ProductionLotLine>
    ) {
        val lot = getLot(companyId, lotId) ?: error("Lote no encontrado: $lotId")
        if (lot.status == ProductionLotStatus.COMPLETED || lot.status == ProductionLotStatus.CANCELLED) {
            error("No se puede agregar a un lote ${lot.status}")
        }
        val existing = getLotLines(companyId, lotId)
        val byProduct = existing.associateBy { it.productId }.toMutableMap()

        for (incoming in newLines) {
            val match = byProduct[incoming.productId]
            if (match != null) {
                // Suma cantidades y combina customerSpecs (sin duplicar el mismo orderId).
                val mergedSpecs = (match.customerSpecs +
                    incoming.customerSpecs.filter { spec ->
                        match.customerSpecs.none { it.customerOrderId == spec.customerOrderId }
                    })
                val merged = match.copy(
                    plannedQuantity = match.plannedQuantity + incoming.plannedQuantity,
                    producedQuantity = match.producedQuantity + incoming.producedQuantity,
                    customerSpecs = mergedSpecs
                )
                lotLinesCol(companyId, lotId).document(match.id).set(merged.toLotLineMap())
                byProduct[incoming.productId] = merged
            } else {
                val lineDoc = lotLinesCol(companyId, lotId).document
                val created = incoming.copy(id = lineDoc.id)
                lineDoc.set(created.toLotLineMap())
                byProduct[incoming.productId] = created
            }
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val updatedLot = lot.copy(
            lineCount = byProduct.size,
            customerOrderIds = (lot.customerOrderIds + customerOrderId).distinct(),
            updatedAt = now
        )
        lotsCol(companyId).document(lotId).set(updatedLot.toLotMap())
    }

    override suspend fun removeCustomerOrderFromLot(
        companyId: String,
        lotId: String,
        customerOrderId: String
    ) {
        val lot = getLot(companyId, lotId) ?: error("Lote no encontrado: $lotId")
        if (lot.status == ProductionLotStatus.COMPLETED || lot.status == ProductionLotStatus.CANCELLED) {
            error("No se puede quitar el pedido de un lote ${lot.status}")
        }
        val existing = getLotLines(companyId, lotId)

        for (line in existing) {
            val specsToRemove = line.customerSpecs.filter { it.customerOrderId == customerOrderId }
            if (specsToRemove.isEmpty()) continue

            val qtyToRemove = specsToRemove.sumOf { it.quantity }
            val newPlanned = (line.plannedQuantity - qtyToRemove).coerceAtLeast(0.0)
            val newProduced = (line.producedQuantity - qtyToRemove).coerceAtLeast(0.0)
            val remainingSpecs = line.customerSpecs - specsToRemove.toSet()

            if (newPlanned <= 0.0 && remainingSpecs.isEmpty()) {
                runCatching { lotLinesCol(companyId, lotId).document(line.id).delete() }
            } else {
                val updated = line.copy(
                    plannedQuantity = newPlanned,
                    producedQuantity = newProduced,
                    customerSpecs = remainingSpecs
                )
                lotLinesCol(companyId, lotId).document(line.id).set(updated.toLotLineMap())
            }
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val remainingLines = getLotLines(companyId, lotId)
        val updatedLot = lot.copy(
            lineCount = remainingLines.size,
            customerOrderIds = lot.customerOrderIds.filter { it != customerOrderId },
            updatedAt = now
        )
        lotsCol(companyId).document(lotId).set(updatedLot.toLotMap())
    }

    // ── Lotes ─────────────────────────────────────────────────────────────────

    override suspend fun getLotsForBranch(companyId: String, branchId: String): List<ProductionLot> =
        lotsCol(companyId).get().documents
            .map { it.toLot() }
            .filter { it.producingBranchId == branchId }
            .sortedByDescending { it.productionDate }

    override suspend fun getLotsForBranchInRange(
        companyId: String,
        producingBranchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): List<ProductionLot> =
        lotsCol(companyId).get().documents
            .map { it.toLot() }
            .filter {
                it.productionDate in fromMillis until untilMillis &&
                    (producingBranchId.isEmpty() || it.producingBranchId == producingBranchId)
            }
            .sortedByDescending { it.productionDate }

    override suspend fun getActiveLotForBranchDateShift(
        companyId: String,
        producingBranchId: String,
        productionDate: Long,
        shiftId: String
    ): ProductionLot? {
        val tz = TimeZone.currentSystemDefault()
        val targetDay = Instant.fromEpochMilliseconds(productionDate).toLocalDateTime(tz).date
        return lotsCol(companyId).get().documents
            .map { it.toLot() }
            .firstOrNull {
                it.status != ProductionLotStatus.CANCELLED &&
                    it.producingBranchId == producingBranchId &&
                    it.shiftId == shiftId &&
                    Instant.fromEpochMilliseconds(it.productionDate).toLocalDateTime(tz).date == targetDay
            }
    }

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
        val now = Clock.System.now().toEpochMilliseconds()
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
        val now = Clock.System.now().toEpochMilliseconds()
        // Cuando un lote pasa a IN_PROGRESS por primera vez, registramos la hora real
        // de inicio. Esto alimenta el cronómetro vivo del detalle y permite que el
        // tiempo trabajado quede como dato auditable para reportes de producción.
        val startedAt = if (newStatus == ProductionLotStatus.IN_PROGRESS && lot.startedAt == null) now
                        else lot.startedAt
        lotsCol(companyId).document(lotId).set(
            lot.copy(status = newStatus, updatedAt = now, startedAt = startedAt).toLotMap()
        )
    }

    override suspend fun saveLotLine(companyId: String, lotId: String, line: ProductionLotLine) {
        val docRef = if (line.id.isEmpty()) lotLinesCol(companyId, lotId).document
                     else lotLinesCol(companyId, lotId).document(line.id)
        docRef.set(line.copy(id = docRef.id).toLotLineMap())
    }

    override suspend fun deleteLotLine(companyId: String, lotId: String, lineId: String) {
        val lot = getLot(companyId, lotId) ?: return
        if (lot.status != ProductionLotStatus.OPEN) {
            error("Solo se pueden eliminar líneas de un lote ABIERTO")
        }
        lotLinesCol(companyId, lotId).document(lineId).delete()
        val now = Clock.System.now().toEpochMilliseconds()
        val remaining = getLotLines(companyId, lotId).size
        lotsCol(companyId).document(lotId).set(
            lot.copy(lineCount = remaining, updatedAt = now).toLotMap()
        )
    }

    override suspend fun completeLot(
        companyId: String,
        lotId: String,
        producedLines: List<ProductionLotLine>
    ) {
        val lot = getLot(companyId, lotId) ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        lotsCol(companyId).document(lotId).set(
            lot.copy(
                status = ProductionLotStatus.COMPLETED,
                completedAt = now,
                updatedAt = now
            ).toLotMap()
        )
        // Guardar la línea completa (no solo producedQuantity) para persistir cualquier cambio
        // de esponja/adornos hecho en la pantalla de detalle.
        val existingIds = getLotLines(companyId, lotId).map { it.id }.toSet()
        producedLines.forEach { line ->
            if (line.id in existingIds) {
                lotLinesCol(companyId, lotId).document(line.id).set(line.toLotLineMap())
            }
        }
    }

    override suspend fun markStockDeducted(companyId: String, lotId: String) {
        val lot = getLot(companyId, lotId) ?: return
        lotsCol(companyId).document(lotId).set(
            lot.copy(stockDeducted = true, updatedAt = Clock.System.now().toEpochMilliseconds()).toLotMap()
        )
    }

    override suspend fun aggregateProductionInRange(
        companyId: String,
        producingBranchIds: List<String>?,
        fromMillis: Long,
        untilMillis: Long
    ): ProductionSummary = coroutineScope {
        val lots = lotsCol(companyId).get().documents
            .map { it.toLot() }
            .filter {
                it.productionDate in fromMillis until untilMillis &&
                    it.status != ProductionLotStatus.CANCELLED &&
                    (producingBranchIds.isNullOrEmpty() || it.producingBranchId in producingBranchIds)
            }
        if (lots.isEmpty()) return@coroutineScope ProductionSummary()

        // Líneas por lote en paralelo
        val linesByLot: Map<String, List<ProductionLotLine>> = lots.map { lot ->
            async { lot.id to getLotLines(companyId, lot.id) }
        }.awaitAll().toMap()

        val tz = TimeZone.currentSystemDefault()
        val plannedTotal = linesByLot.values.flatten().sumOf { it.plannedQuantity }
        val producedTotal = linesByLot.values.flatten().sumOf { it.producedQuantity }
        val completedCount = lots.count { it.status == ProductionLotStatus.COMPLETED }

        val byShift = lots.groupBy { it.shiftId to it.shiftName }
            .map { (key, ls) ->
                val ll = ls.flatMap { linesByLot[it.id].orEmpty() }
                ShiftProductionStat(
                    shiftId = key.first,
                    shiftName = key.second.ifEmpty { "Sin turno" },
                    lotsCount = ls.size,
                    planned = ll.sumOf { it.plannedQuantity },
                    produced = ll.sumOf { it.producedQuantity }
                )
            }
            .sortedBy { it.shiftName }

        val byProductAccum = HashMap<String, Triple<String, Double, Double>>()
        linesByLot.values.flatten().forEach { l ->
            if (l.productId.isEmpty()) return@forEach
            val (name, planned, produced) = byProductAccum[l.productId]
                ?: Triple(l.productName, 0.0, 0.0)
            byProductAccum[l.productId] =
                Triple(name, planned + l.plannedQuantity, produced + l.producedQuantity)
        }
        val byProduct = byProductAccum.entries
            .map { (pid, t) ->
                val diff = if (t.second > 0) (t.third - t.second) / t.second * 100.0 else 0.0
                ProductProductionStat(pid, t.first, t.second, t.third, diff)
            }
            .sortedByDescending { it.planned }
            .take(50)

        val dayAccum = HashMap<String, Pair<Long, Pair<Double, Double>>>()
        lots.forEach { lot ->
            val ldt = Instant.fromEpochMilliseconds(lot.productionDate).toLocalDateTime(tz)
            val dateKey = ldt.date.toString()
            val startMillis = ldt.date.atStartOfDayIn(tz).toEpochMilliseconds()
            val ll = linesByLot[lot.id].orEmpty()
            val (_, pair) = dayAccum[dateKey] ?: (startMillis to (0.0 to 0.0))
            dayAccum[dateKey] = startMillis to (
                pair.first + ll.sumOf { it.plannedQuantity } to
                    pair.second + ll.sumOf { it.producedQuantity }
                )
        }
        val byDay = dayAccum.entries
            .sortedBy { it.value.first }
            .map { (label, value) ->
                DailyProductionPoint(value.first, label, value.second.first, value.second.second)
            }

        // Pedidos tardíos PENDING en el rango
        val lateOrderCount = ordersCol(companyId).get().documents
            .map { it.toOrder() }
            .count {
                it.isLateOrder && it.status == ProductionOrderStatus.PENDING &&
                    it.requestedDate in fromMillis until untilMillis &&
                    (producingBranchIds.isNullOrEmpty() || it.producingBranchId in producingBranchIds)
            }

        ProductionSummary(
            lotsCount = lots.size,
            completedLotsCount = completedCount,
            plannedTotal = plannedTotal,
            producedTotal = producedTotal,
            efficiencyPct = if (plannedTotal > 0) producedTotal / plannedTotal * 100.0 else 0.0,
            lateOrderCount = lateOrderCount,
            byShift = byShift,
            byProduct = byProduct,
            byDay = byDay
        )
    }

    // ── Serialización — Turnos ────────────────────────────────────────────────

    private fun ProductionShift.toShiftMap(): Map<String, Any?> = firestoreSafeOf(
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

    private fun ProductionOrder.toOrderMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "sellingBranchId" to sellingBranchId,
        "sellingBranchName" to sellingBranchName,
        "producingBranchId" to producingBranchId,
        "producingBranchName" to producingBranchName,
        "shiftId" to shiftId,
        "shiftName" to shiftName,
        "requestedDate" to requestedDate,
        "status" to status,
        "isLateOrder" to isLateOrder,
        "notes" to notes,
        "createdByUserId" to createdByUserId,
        "createdByUserName" to createdByUserName,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "lineCount" to lineCount,
        "includedCustomerLineIds" to includedCustomerLineIds
    )

    private fun ProductionOrderLine.toOrderLineMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "productId" to productId,
        "productName" to productName,
        "requestedQuantity" to requestedQuantity,
        "productUnit" to productUnit,
        "customerLineRefs" to customerLineRefs.map {
            mapOf(
                "customerOrderId" to it.customerOrderId,
                "customerName" to it.customerName,
                "customerOrderLineId" to it.customerOrderLineId,
                "quantity" to it.quantity,
                "customSpec" to it.customSpec
            )
        }
    )

    private fun DocumentSnapshot.toOrder() = ProductionOrder(
        id = id,
        sellingBranchId = get<String?>("sellingBranchId") ?: "",
        sellingBranchName = get<String?>("sellingBranchName") ?: "",
        producingBranchId = get<String?>("producingBranchId") ?: "",
        producingBranchName = get<String?>("producingBranchName") ?: "",
        shiftId = get<String?>("shiftId") ?: "",
        shiftName = get<String?>("shiftName") ?: "",
        requestedDate = readMillis("requestedDate"),
        status = get<String?>("status") ?: ProductionOrderStatus.PENDING,
        isLateOrder = runCatching { get<Boolean?>("isLateOrder") }.getOrNull() ?: false,
        notes = get<String?>("notes"),
        createdByUserId = get<String?>("createdByUserId") ?: "",
        createdByUserName = get<String?>("createdByUserName") ?: "",
        createdAt = readMillis("createdAt"),
        updatedAt = readMillis("updatedAt"),
        lineCount = runCatching { get<Long?>("lineCount")?.toInt() }.getOrNull() ?: 0,
        includedCustomerLineIds = runCatching {
            get<List<String>?>("includedCustomerLineIds")
        }.getOrNull() ?: emptyList()
    )

    private fun DocumentSnapshot.toOrderLine() = ProductionOrderLine(
        id = id,
        productId = get<String?>("productId") ?: "",
        productName = get<String?>("productName") ?: "",
        requestedQuantity = runCatching { get<Double?>("requestedQuantity") }.getOrNull() ?: 0.0,
        productUnit = get<String?>("productUnit") ?: "",
        customerLineRefs = runCatching {
            @Suppress("UNCHECKED_CAST")
            (get<List<Map<String, Any?>>?>("customerLineRefs") ?: emptyList()).map {
                CustomerLineRef(
                    customerOrderId = it["customerOrderId"] as? String ?: "",
                    customerName = it["customerName"] as? String ?: "",
                    customerOrderLineId = it["customerOrderLineId"] as? String ?: "",
                    quantity = (it["quantity"] as? Number)?.toDouble() ?: 0.0,
                    customSpec = it["customSpec"] as? String ?: ""
                )
            }
        }.getOrElse { emptyList() }
    )

    // ── Serialización — Lotes ─────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun ProductionLot.toLotMap(): Map<String, Any?> = firestoreSafeOf(
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
        "startedAt" to startedAt,
        "completedAt" to completedAt,
        "stockDeducted" to stockDeducted,
        "lineCount" to lineCount,
        "sourceOrderIds" to sourceOrderIds,
        "customerOrderIds" to customerOrderIds
    )

    private fun ProductionLotLine.toLotLineMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "productId" to productId,
        "productName" to productName,
        "productUnit" to productUnit,
        "rawWeightGrams" to rawWeightGrams,
        "plannedQuantity" to plannedQuantity,
        "producedQuantity" to producedQuantity,
        "recipeId" to recipeId,
        "recipeName" to recipeName,
        "spongeBatchId" to spongeBatchId,
        "spongeBatchName" to spongeBatchName,
        "spongeGramsUsed" to spongeGramsUsed,
        "decorationsSnapshot" to decorationsSnapshot.map {
            mapOf(
                "preparationId" to it.preparationId,
                "preparationName" to it.preparationName,
                "gramsPerUnit" to it.gramsPerUnit,
                "batchId" to it.batchId,
                "batchName" to it.batchName
            )
        },
        "customerSpecs" to customerSpecs.map {
            mapOf(
                "customerOrderId" to it.customerOrderId,
                "customerName" to it.customerName,
                "quantity" to it.quantity,
                "customSpec" to it.customSpec,
                "customerType" to it.customerType
            )
        }
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
        startedAt = runCatching { get<Long?>("startedAt") }.getOrNull(),
        completedAt = runCatching { get<Long?>("completedAt") }.getOrNull(),
        stockDeducted = runCatching { get<Boolean?>("stockDeducted") }.getOrNull() ?: false,
        lineCount = runCatching { get<Long?>("lineCount")?.toInt() }.getOrNull() ?: 0,
        sourceOrderIds = runCatching { get<List<String>?>("sourceOrderIds") }.getOrNull() ?: emptyList(),
        customerOrderIds = runCatching { get<List<String>?>("customerOrderIds") }.getOrNull() ?: emptyList()
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
        recipeName = get<String?>("recipeName") ?: "",
        spongeBatchId = get<String?>("spongeBatchId") ?: "",
        spongeBatchName = get<String?>("spongeBatchName") ?: "",
        spongeGramsUsed = runCatching { get<Double?>("spongeGramsUsed") }.getOrNull() ?: 0.0,
        decorationsSnapshot = runCatching {
            @Suppress("UNCHECKED_CAST")
            (get<List<Map<String, Any?>>?>("decorationsSnapshot") ?: emptyList()).map {
                ProductionLotDecoration(
                    preparationId = it["preparationId"] as? String ?: "",
                    preparationName = it["preparationName"] as? String ?: "",
                    gramsPerUnit = (it["gramsPerUnit"] as? Number)?.toDouble() ?: 0.0,
                    batchId = it["batchId"] as? String ?: "",
                    batchName = it["batchName"] as? String ?: ""
                )
            }
        }.getOrElse { emptyList() },
        customerSpecs = runCatching {
            @Suppress("UNCHECKED_CAST")
            (get<List<Map<String, Any?>>?>("customerSpecs") ?: emptyList()).map {
                CustomerLineSpec(
                    customerOrderId = it["customerOrderId"] as? String ?: "",
                    customerName = it["customerName"] as? String ?: "",
                    quantity = (it["quantity"] as? Number)?.toDouble() ?: 0.0,
                    customSpec = it["customSpec"] as? String ?: "",
                    customerType = it["customerType"] as? String ?: ""
                )
            }
        }.getOrElse { emptyList() }
    )

    // ── Utilidad ──────────────────────────────────────────────────────────────

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
