package com.bakery.erp.data

import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.domain.report.DailyProductionPoint
import com.bakery.erp.domain.report.ProductProductionStat
import com.bakery.erp.domain.report.ProductionSummary
import com.bakery.erp.domain.report.ShiftProductionStat
import com.bakery.erp.model.CustomerLineSpec
import com.bakery.erp.model.DoughFilling
import com.bakery.erp.model.OrderPattern
import com.bakery.erp.model.User
import com.bakery.erp.model.SourceOrderAllocation
import com.bakery.erp.model.isoWeekdayOf
import com.bakery.erp.model.orderPatternId
import com.bakery.erp.model.withOrder
import com.bakery.erp.model.withoutOrder
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotDecoration
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.summarizeProductionWaste
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
import dev.gitlive.firebase.firestore.where
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

    private fun orderPatternsCol(companyId: String) =
        firestore.collection("companies/$companyId/orderPatterns")

    // ── Turnos ────────────────────────────────────────────────────────────────

    /**
     * Orden estable por viaje de despacho (ver [ProductionShift.dispatchOrder]): los
     * turnos sin configurar (`0`) van al final, alfabéticos. Antes salían en el orden
     * que devolviera Firestore, así que cuál turno era la columna izquierda del
     * reparto podía cambiar entre cargas.
     */
    override suspend fun getShiftsForBranch(companyId: String, branchId: String): List<ProductionShift> =
        shiftsCol(companyId).get().documents
            .map { it.toShift() }
            .filter { it.branchId == branchId && it.active }
            .sortedWith(
                compareBy<ProductionShift> { if (it.dispatchOrder <= 0) Int.MAX_VALUE else it.dispatchOrder }
                    .thenBy { it.name }
            )

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
        recordOrderInPattern(companyId, final, lines)
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
        recordOrderInPattern(companyId, final, lines)
    }

    private fun orderAdjustmentsCol(companyId: String) =
        firestore.collection("companies/$companyId/orderAdjustments")

    override suspend fun saveOrderAdjustment(
        companyId: String,
        adjustment: com.bakery.erp.model.ProductionOrderAdjustment
    ) {
        val ref = orderAdjustmentsCol(companyId).document
        ref.set(
            firestoreSafeOf(
                "id" to ref.id,
                "orderId" to adjustment.orderId,
                "lotId" to adjustment.lotId,
                "shiftName" to adjustment.shiftName,
                "sellingBranchName" to adjustment.sellingBranchName,
                "productId" to adjustment.productId,
                "productName" to adjustment.productName,
                "delta" to adjustment.delta,
                "lotStatus" to adjustment.lotStatus,
                "verifiedNotMade" to adjustment.verifiedNotMade,
                "recipeName" to adjustment.recipeName,
                "createdByUserId" to adjustment.createdByUserId,
                "createdByUserName" to adjustment.createdByUserName,
                "createdAt" to adjustment.createdAt
            )
        )
    }

    override suspend fun getOrderAdjustments(
        companyId: String,
        orderId: String
    ): List<com.bakery.erp.model.ProductionOrderAdjustment> =
        orderAdjustmentsCol(companyId).where { "orderId" equalTo orderId }.get().documents.map { d ->
            com.bakery.erp.model.ProductionOrderAdjustment(
                id = d.id,
                orderId = d.get<String?>("orderId") ?: "",
                lotId = d.get<String?>("lotId") ?: "",
                shiftName = d.get<String?>("shiftName") ?: "",
                sellingBranchName = d.get<String?>("sellingBranchName") ?: "",
                productId = d.get<String?>("productId") ?: "",
                productName = d.get<String?>("productName") ?: "",
                delta = runCatching { d.get<Double?>("delta") }.getOrNull() ?: 0.0,
                lotStatus = d.get<String?>("lotStatus") ?: "",
                verifiedNotMade = runCatching { d.get<Boolean?>("verifiedNotMade") }.getOrNull() ?: false,
                recipeName = d.get<String?>("recipeName") ?: "",
                createdByUserId = d.get<String?>("createdByUserId") ?: "",
                createdByUserName = d.get<String?>("createdByUserName") ?: "",
                createdAt = d.readMillis("createdAt")
            )
        }.sortedBy { it.createdAt }

    override suspend fun updateOrderStatus(companyId: String, orderId: String, newStatus: String) {
        val order = getOrder(companyId, orderId) ?: return
        ordersCol(companyId).document(orderId).set(
            order.copy(status = newStatus, updatedAt = Clock.System.now().toEpochMilliseconds()).toOrderMap()
        )
        // Un pedido cancelado no debe seguir alimentando las cantidades sugeridas.
        if (newStatus == ProductionOrderStatus.CANCELLED) {
            runCatching {
                val patternId = orderPatternId(
                    order.sellingBranchId,
                    order.producingBranchId,
                    order.shiftId,
                    isoWeekdayOf(order.requestedDate)
                )
                val current = getOrderPattern(companyId, patternId) ?: return@runCatching
                saveOrderPattern(companyId, current.withoutOrder(orderId))
            }
        }
    }

    override suspend fun deleteOrder(companyId: String, orderId: String) {
        // Se lee ANTES de borrar: el patrón se localiza con los datos del pedido
        // (sucursal + planta + turno + día de la semana), y después ya no están.
        val order = getOrder(companyId, orderId)
        orderLinesCol(companyId, orderId).get().documents.forEach { line ->
            runCatching { orderLinesCol(companyId, orderId).document(line.id).delete() }
        }
        ordersCol(companyId).document(orderId).delete()
        // Un pedido borrado tampoco debe seguir alimentando las cantidades
        // sugeridas — mismo criterio que updateOrderStatus(CANCELLED).
        if (order != null) {
            runCatching {
                val patternId = orderPatternId(
                    order.sellingBranchId,
                    order.producingBranchId,
                    order.shiftId,
                    isoWeekdayOf(order.requestedDate)
                )
                val current = getOrderPattern(companyId, patternId) ?: return@runCatching
                saveOrderPattern(companyId, current.withoutOrder(orderId))
            }
        }
    }

    override suspend fun deleteLot(companyId: String, lotId: String) {
        lotLinesCol(companyId, lotId).get().documents.forEach { line ->
            runCatching { lotLinesCol(companyId, lotId).document(line.id).delete() }
        }
        lotsCol(companyId).document(lotId).delete()
    }

    override suspend fun excludeOrdersFromLotPlanning(companyId: String, orderIds: List<String>) {
        if (orderIds.isEmpty()) return
        // Escritura parcial: no se toca el resto del pedido ni su estado.
        coroutineScope {
            orderIds.map { orderId ->
                async {
                    runCatching {
                        ordersCol(companyId).document(orderId).update(
                            firestoreSafeOf(
                                "excludedFromLotPlanning" to true,
                                "updatedAt" to Clock.System.now().toEpochMilliseconds()
                            )
                        )
                    }
                }
            }.awaitAll()
        }
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
                // Suma cantidades a la línea existente y combina sourceOrderAllocations
                // (sin duplicar el mismo orderId) — si la línea ya traía desglose por
                // sucursal (lote de reparto por turnos), el pedido tardío necesita su
                // propia entrada o createDispatchNotesForLot lo trataría como 0 en este turno.
                val mergedAllocations = match.sourceOrderAllocations +
                    incoming.sourceOrderAllocations.filter { alloc ->
                        match.sourceOrderAllocations.none { it.orderId == alloc.orderId }
                    }
                val merged = match.copy(
                    plannedQuantity = match.plannedQuantity + incoming.plannedQuantity,
                    producedQuantity = match.producedQuantity + incoming.producedQuantity,
                    sourceOrderAllocations = mergedAllocations
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
            // En un lote de reparto por turnos la línea solo tiene la PORCIÓN de este
            // pedido que produce este turno, no lo pedido completo. Restar la cantidad
            // completa vaciaba la línea y borraba de un turno el pan de todas las demás
            // sucursales — se resta el desglose de este pedido y se lo saca de la lista.
            val allocated = match.sourceOrderAllocations.find { it.orderId == orderId }
            val delta = allocated?.quantity ?: toRemove.requestedQuantity
            val remainingAllocations = match.sourceOrderAllocations.filter { it.orderId != orderId }
            val newPlanned = (match.plannedQuantity - delta).coerceAtLeast(0.0)
            val newProduced = (match.producedQuantity - delta).coerceAtLeast(0.0)
            if (newPlanned <= 0.0) {
                // Sin demanda restante: eliminar la línea.
                runCatching { lotLinesCol(companyId, lotId).document(match.id).delete() }
                byProduct.remove(toRemove.productId)
            } else {
                val updated = match.copy(
                    plannedQuantity = newPlanned,
                    producedQuantity = newProduced,
                    sourceOrderAllocations = remainingAllocations
                )
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

    // ── Patrones de pedido (cantidades sugeridas) ─────────────────────────────

    override suspend fun getOrderPattern(companyId: String, patternId: String): OrderPattern? =
        runCatching {
            val doc = orderPatternsCol(companyId).document(patternId).get()
            if (!doc.exists) null else doc.toOrderPattern()
        }.getOrNull()

    override suspend fun rebuildOrderPatternFromHistory(
        companyId: String,
        sellingBranchId: String,
        producingBranchId: String,
        shiftId: String,
        weekday: Int
    ): OrderPattern {
        val patternId = orderPatternId(sellingBranchId, producingBranchId, shiftId, weekday)
        val empty = OrderPattern(
            id = patternId,
            sellingBranchId = sellingBranchId,
            producingBranchId = producingBranchId,
            shiftId = shiftId,
            weekday = weekday
        )
        if (sellingBranchId.isEmpty() || producingBranchId.isEmpty()) return empty

        // Filtro por igualdad simple (no requiere índice compuesto); el resto se
        // acota en cliente y solo leemos las líneas de los MAX_SAMPLES pedidos
        // que realmente alimentan la sugerencia.
        val candidates = runCatching {
            ordersCol(companyId)
                .where { "sellingBranchId" equalTo sellingBranchId }
                .get().documents.map { it.toOrder() }
        }.getOrElse { return empty }
            .filter {
                it.producingBranchId == producingBranchId &&
                    // Un shiftId objetivo vacío significa "planta sin selector de turno"
                    // (0 turnos, o 2 turnos con reparto automático). En ese caso el
                    // historial puede tener shiftId real de ANTES de que existiera el
                    // reparto automático — se toma como muestra igual, si no el patrón
                    // queda huérfano y la sugerencia nunca se rellena.
                    (shiftId.isEmpty() || it.shiftId == shiftId) &&
                    it.status != ProductionOrderStatus.CANCELLED &&
                    isoWeekdayOf(it.requestedDate) == weekday
            }
            .sortedByDescending { it.requestedDate }
            .take(OrderPattern.MAX_SAMPLES)

        var pattern = empty
        // Del más viejo al más reciente para que `recentOrderIds` quede ordenado.
        candidates.reversed().forEach { order ->
            val lines = runCatching { getOrderLines(companyId, order.id) }.getOrElse { emptyList() }
            if (lines.isEmpty()) return@forEach
            pattern = pattern.withOrder(
                order.id,
                // Los pedidos especiales no entran al patrón: son piezas de un
                // solo día con un productId sintético e irrepetible. Sumarlos
                // llenaría la sugerencia de productos fantasma que ya no existen.
                lines.filter { !it.isSpecial }
                    .associate { it.productId to it.requestedQuantity }
            )
        }
        runCatching { saveOrderPattern(companyId, pattern) }
        return pattern
    }

    /**
     * Persiste el patrón (con `updatedAt` fresco). Documento plano; las
     * cantidades van como mapa anidado `orderId → productId → cantidad`.
     */
    private suspend fun saveOrderPattern(companyId: String, pattern: OrderPattern) {
        orderPatternsCol(companyId).document(pattern.id).set(pattern.toPatternMap())
    }

    /**
     * Mantiene al día el patrón de la combinación del pedido. Se llama desde
     * [saveOrder] y [updateOrder]; nunca hace fallar la operación principal —
     * una sugerencia desactualizada no debe impedir guardar un pedido.
     */
    private suspend fun recordOrderInPattern(
        companyId: String,
        order: ProductionOrder,
        lines: List<ProductionOrderLine>
    ) {
        if (order.id.isEmpty() || order.sellingBranchId.isEmpty()) return
        if (order.status == ProductionOrderStatus.CANCELLED) return
        runCatching {
            val weekday = isoWeekdayOf(order.requestedDate)
            val patternId = orderPatternId(
                order.sellingBranchId, order.producingBranchId, order.shiftId, weekday
            )
            val current = getOrderPattern(companyId, patternId) ?: OrderPattern(
                id = patternId,
                sellingBranchId = order.sellingBranchId,
                producingBranchId = order.producingBranchId,
                shiftId = order.shiftId,
                weekday = weekday
            )
            saveOrderPattern(
                companyId,
                current.withOrder(
                    order.id,
                    // Se resta rainBumpUnits para que la muestra histórica quede
                    // "limpia" de lluvia — si no, el ajuste automático se vuelve
                    // la nueva normalidad y se acumula semana tras semana (ver
                    // com.bakery.erp.model.withRainBump).
                    // Sin los especiales, por la misma razón que en
                    // `rebuildOrderPatternFromHistory`: su productId es sintético
                    // y no se repite nunca.
                    lines.filter { !it.isSpecial }.associate {
                        it.productId to (it.requestedQuantity - it.rainBumpUnits).coerceAtLeast(0.0)
                    }
                )
            )
        }
    }

    private fun OrderPattern.toPatternMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "sellingBranchId" to sellingBranchId,
        "producingBranchId" to producingBranchId,
        "shiftId" to shiftId,
        "weekday" to weekday,
        "recentOrderIds" to recentOrderIds,
        "quantitiesByOrder" to quantitiesByOrder,
        "updatedAt" to Clock.System.now().toEpochMilliseconds()
    )

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toOrderPattern(): OrderPattern = OrderPattern(
        id = id,
        sellingBranchId = get<String?>("sellingBranchId") ?: "",
        producingBranchId = get<String?>("producingBranchId") ?: "",
        shiftId = get<String?>("shiftId") ?: "",
        weekday = runCatching { get<Long?>("weekday")?.toInt() }.getOrNull() ?: 1,
        recentOrderIds = runCatching { get<List<String>?>("recentOrderIds") }.getOrNull()
            ?: emptyList(),
        // `get<Map<String, Any?>>` no funciona: kotlinx.serialization no tiene
        // serializer para `Any` (falla con "Serializer for class 'Any' is not
        // found", capturado en silencio por el runCatching y el patrón siempre
        // volvía con quantitiesByOrder vacío aunque recentOrderIds sí llegaba
        // bien). Como todos los valores guardados son String→Double concretos
        // (firestoreSafeOf convierte Long a Double al escribir), pedirle el tipo
        // anidado exacto sí tiene serializer y decodifica bien.
        quantitiesByOrder = runCatching {
            get<Map<String, Map<String, Double>>?>("quantitiesByOrder") ?: emptyMap()
        }.getOrElse { emptyMap() },
        updatedAt = readMillis("updatedAt")
    )

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

    override suspend fun getActiveLotsForBranchDate(
        companyId: String,
        producingBranchId: String,
        productionDate: Long
    ): List<ProductionLot> {
        val tz = TimeZone.currentSystemDefault()
        val targetDay = Instant.fromEpochMilliseconds(productionDate).toLocalDateTime(tz).date
        return lotsCol(companyId).get().documents
            .map { it.toLot() }
            .filter {
                it.status != ProductionLotStatus.CANCELLED &&
                    it.producingBranchId == producingBranchId &&
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

    override suspend fun updateLotStatus(companyId: String, lotId: String, newStatus: String, by: User?) {
        val lot = getLot(companyId, lotId) ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        // Cuando un lote pasa a IN_PROGRESS por primera vez, registramos la hora real
        // de inicio. Esto alimenta el cronómetro vivo del detalle y permite que el
        // tiempo trabajado quede como dato auditable para reportes de producción.
        val startedAt = if (newStatus == ProductionLotStatus.IN_PROGRESS && lot.startedAt == null) now
                        else lot.startedAt
        val firstStart = newStatus == ProductionLotStatus.IN_PROGRESS && lot.startedAt == null && by != null
        lotsCol(companyId).document(lotId).set(
            lot.copy(
                status = newStatus, updatedAt = now, startedAt = startedAt,
                startedByUserId = if (firstStart) by!!.id else lot.startedByUserId,
                startedByUserName = if (firstStart) by!!.displayName.ifBlank { by.email } else lot.startedByUserName,
                startedByEmployeeId = if (firstStart) by!!.operatorEmployeeId else lot.startedByEmployeeId
            ).toLotMap()
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
        producedLines: List<ProductionLotLine>,
        by: User?
    ) {
        val lot = getLot(companyId, lotId) ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        lotsCol(companyId).document(lotId).set(
            lot.copy(
                status = ProductionLotStatus.COMPLETED,
                completedAt = now,
                updatedAt = now,
                completedByUserId = by?.id ?: lot.completedByUserId,
                completedByUserName = by?.let { it.displayName.ifBlank { it.email } } ?: lot.completedByUserName,
                completedByEmployeeId = by?.operatorEmployeeId ?: lot.completedByEmployeeId
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
        val allLines = linesByLot.values.flatten()
        val wasteTotal = allLines.sumOf { it.wasteQuantity }
        val extraTotal = allLines.sumOf { it.extraQuantity }
        val wasteByProduct = summarizeProductionWaste(allLines)

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
            wasteTotal = wasteTotal,
            extraTotal = extraTotal,
            wasteByProduct = wasteByProduct,
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
        "excludedProductIds" to excludedProductIds,
        "dispatchOrder" to dispatchOrder,
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
        excludedProductIds = runCatching { get<List<String>?>("excludedProductIds") }.getOrNull() ?: emptyList(),
        dispatchOrder = runCatching { get<Long?>("dispatchOrder")?.toInt() }.getOrNull() ?: 0,
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
        "excludedFromLotPlanning" to excludedFromLotPlanning,
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
        "rainBumpUnits" to rainBumpUnits,
        "earlyShiftQuantity" to earlyShiftQuantity,
        "earlyShiftNote" to earlyShiftNote,
        "isSpecial" to isSpecial,
        "specialRecipeId" to specialRecipeId,
        "specialRecipeName" to specialRecipeName,
        "specialRawWeightGrams" to specialRawWeightGrams,
        "specialPhotoUrl" to specialPhotoUrl,
        "specialUnitPrice" to specialUnitPrice,
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
        excludedFromLotPlanning = runCatching {
            get<Boolean?>("excludedFromLotPlanning")
        }.getOrNull() ?: false,
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
        rainBumpUnits = runCatching { get<Long?>("rainBumpUnits")?.toInt() }.getOrNull() ?: 0,
        earlyShiftQuantity = runCatching { get<Double?>("earlyShiftQuantity") }.getOrNull(),
        earlyShiftNote = get<String?>("earlyShiftNote") ?: "",
        isSpecial = runCatching { get<Boolean?>("isSpecial") }.getOrNull() ?: false,
        specialRecipeId = get<String?>("specialRecipeId") ?: "",
        specialRecipeName = get<String?>("specialRecipeName") ?: "",
        specialRawWeightGrams = runCatching {
            get<Double?>("specialRawWeightGrams")
        }.getOrNull() ?: 0.0,
        specialPhotoUrl = get<String?>("specialPhotoUrl") ?: "",
        specialUnitPrice = runCatching { get<Double?>("specialUnitPrice") }.getOrNull() ?: 0.0,
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
        "startedByUserId" to startedByUserId,
        "startedByUserName" to startedByUserName,
        "startedByEmployeeId" to startedByEmployeeId,
        "completedByUserId" to completedByUserId,
        "completedByUserName" to completedByUserName,
        "completedByEmployeeId" to completedByEmployeeId,
        "stockDeducted" to stockDeducted,
        "lineCount" to lineCount,
        "sourceOrderIds" to sourceOrderIds,
        "isShiftSplit" to isShiftSplit,
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
        "producedCountConfirmed" to producedCountConfirmed,
        "wasteQuantity" to wasteQuantity,
        "wasteReason" to wasteReason,
        "wasteNotes" to wasteNotes,
        "extraQuantity" to extraQuantity,
        "extraReason" to extraReason,
        "extraNotes" to extraNotes,
        "recipeId" to recipeId,
        "recipeName" to recipeName,
        "plantRetainedQuantity" to plantRetainedQuantity,
        "spongeBatchId" to spongeBatchId,
        "spongeBatchName" to spongeBatchName,
        "spongeGramsUsed" to spongeGramsUsed,
        "spongeConsumed" to spongeConsumed,
        // Arreglos paralelos en vez de List<Map<String, Any?>>: GitLive no tiene
        // serializer para `Any` (ver feedback_gitlive_any_serializer) — con el
        // formato anterior decorationsSnapshot/customerSpecs siempre volvían
        // vacíos al releer el lote (recargar la pantalla, abrir desde otro
        // dispositivo), así que los adornos comprados nunca se descontaban en
        // `completeLot` y las notas de cliente desaparecían del detalle.
        "decoPreparationIds" to decorationsSnapshot.map { it.preparationId },
        "decoPreparationNames" to decorationsSnapshot.map { it.preparationName },
        "decoIngredientIds" to decorationsSnapshot.map { it.ingredientId },
        "decoIngredientNames" to decorationsSnapshot.map { it.ingredientName },
        "decoGramsPerUnit" to decorationsSnapshot.map { it.gramsPerUnit },
        "decoBatchIds" to decorationsSnapshot.map { it.batchId },
        "decoBatchNames" to decorationsSnapshot.map { it.batchName },
        "doughFillingRecipeIds" to doughFillingsSnapshot.map { it.recipeId },
        "doughFillingRecipeNames" to doughFillingsSnapshot.map { it.recipeName },
        "doughFillingRawWeightGrams" to doughFillingsSnapshot.map { it.rawWeightGrams },
        "csOrderIds" to customerSpecs.map { it.customerOrderId },
        "csCustomerNames" to customerSpecs.map { it.customerName },
        "csQuantities" to customerSpecs.map { it.quantity },
        "csCustomSpecs" to customerSpecs.map { it.customSpec },
        "csCustomerTypes" to customerSpecs.map { it.customerType },
        "soaOrderIds" to sourceOrderAllocations.map { it.orderId },
        "soaBranchNames" to sourceOrderAllocations.map { it.branchName },
        "soaQuantities" to sourceOrderAllocations.map { it.quantity },
        "isSpecial" to isSpecial,
        "specialPhotoUrl" to specialPhotoUrl,
        "adjustmentNote" to adjustmentNote
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
        startedByUserId = runCatching { get<String?>("startedByUserId") }.getOrNull() ?: "",
        startedByUserName = runCatching { get<String?>("startedByUserName") }.getOrNull() ?: "",
        startedByEmployeeId = runCatching { get<String?>("startedByEmployeeId") }.getOrNull() ?: "",
        completedByUserId = runCatching { get<String?>("completedByUserId") }.getOrNull() ?: "",
        completedByUserName = runCatching { get<String?>("completedByUserName") }.getOrNull() ?: "",
        completedByEmployeeId = runCatching { get<String?>("completedByEmployeeId") }.getOrNull() ?: "",
        stockDeducted = runCatching { get<Boolean?>("stockDeducted") }.getOrNull() ?: false,
        lineCount = runCatching { get<Long?>("lineCount")?.toInt() }.getOrNull() ?: 0,
        sourceOrderIds = runCatching { get<List<String>?>("sourceOrderIds") }.getOrNull() ?: emptyList(),
        isShiftSplit = runCatching { get<Boolean?>("isShiftSplit") }.getOrNull() ?: false,
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
        producedCountConfirmed = runCatching { get<Boolean?>("producedCountConfirmed") }.getOrNull() ?: false,
        adjustmentNote = runCatching { get<String?>("adjustmentNote") }.getOrNull() ?: "",
        wasteQuantity = runCatching { get<Double?>("wasteQuantity") }.getOrNull() ?: 0.0,
        wasteReason = get<String?>("wasteReason") ?: "",
        wasteNotes = get<String?>("wasteNotes") ?: "",
        extraQuantity = runCatching { get<Double?>("extraQuantity") }.getOrNull() ?: 0.0,
        extraReason = get<String?>("extraReason") ?: "",
        extraNotes = get<String?>("extraNotes") ?: "",
        recipeId = get<String?>("recipeId") ?: "",
        recipeName = get<String?>("recipeName") ?: "",
        plantRetainedQuantity = runCatching {
            get<Double?>("plantRetainedQuantity")
        }.getOrNull() ?: 0.0,
        spongeBatchId = get<String?>("spongeBatchId") ?: "",
        spongeBatchName = get<String?>("spongeBatchName") ?: "",
        spongeGramsUsed = runCatching { get<Double?>("spongeGramsUsed") }.getOrNull() ?: 0.0,
        spongeConsumed = runCatching { get<Boolean?>("spongeConsumed") }.getOrNull() ?: false,
        decorationsSnapshot = runCatching {
            val preparationIds = get<List<String>?>("decoPreparationIds") ?: emptyList()
            val preparationNames = get<List<String>?>("decoPreparationNames") ?: emptyList()
            val ingredientIds = get<List<String>?>("decoIngredientIds") ?: emptyList()
            val ingredientNames = get<List<String>?>("decoIngredientNames") ?: emptyList()
            val gramsPerUnit = get<List<Double>?>("decoGramsPerUnit") ?: emptyList()
            val batchIds = get<List<String>?>("decoBatchIds") ?: emptyList()
            val batchNames = get<List<String>?>("decoBatchNames") ?: emptyList()
            preparationIds.indices.map { i ->
                ProductionLotDecoration(
                    preparationId = preparationIds[i],
                    preparationName = preparationNames.getOrElse(i) { "" },
                    ingredientId = ingredientIds.getOrElse(i) { "" },
                    ingredientName = ingredientNames.getOrElse(i) { "" },
                    gramsPerUnit = gramsPerUnit.getOrElse(i) { 0.0 },
                    batchId = batchIds.getOrElse(i) { "" },
                    batchName = batchNames.getOrElse(i) { "" }
                )
            }
        }.getOrElse { emptyList() },
        doughFillingsSnapshot = runCatching {
            val recipeIds = get<List<String>?>("doughFillingRecipeIds") ?: emptyList()
            val recipeNames = get<List<String>?>("doughFillingRecipeNames") ?: emptyList()
            val rawWeightGrams = get<List<Double>?>("doughFillingRawWeightGrams") ?: emptyList()
            recipeIds.indices.map { i ->
                DoughFilling(
                    recipeId = recipeIds[i],
                    recipeName = recipeNames.getOrElse(i) { "" },
                    rawWeightGrams = rawWeightGrams.getOrElse(i) { 0.0 }
                )
            }
        }.getOrElse { emptyList() },
        customerSpecs = runCatching {
            val orderIds = get<List<String>?>("csOrderIds") ?: emptyList()
            val customerNames = get<List<String>?>("csCustomerNames") ?: emptyList()
            val quantities = get<List<Double>?>("csQuantities") ?: emptyList()
            val customSpecs = get<List<String>?>("csCustomSpecs") ?: emptyList()
            val customerTypes = get<List<String>?>("csCustomerTypes") ?: emptyList()
            orderIds.indices.map { i ->
                CustomerLineSpec(
                    customerOrderId = orderIds[i],
                    customerName = customerNames.getOrElse(i) { "" },
                    quantity = quantities.getOrElse(i) { 0.0 },
                    customSpec = customSpecs.getOrElse(i) { "" },
                    customerType = customerTypes.getOrElse(i) { "" }
                )
            }
        }.getOrElse { emptyList() },
        sourceOrderAllocations = runCatching {
            val orderIds = get<List<String>?>("soaOrderIds") ?: emptyList()
            val branchNames = get<List<String>?>("soaBranchNames") ?: emptyList()
            val quantities = get<List<Double>?>("soaQuantities") ?: emptyList()
            orderIds.indices.map { i ->
                SourceOrderAllocation(
                    orderId = orderIds[i],
                    branchName = branchNames.getOrElse(i) { "" },
                    quantity = quantities.getOrElse(i) { 0.0 }
                )
            }
        }.getOrElse { emptyList() },
        isSpecial = runCatching { get<Boolean?>("isSpecial") }.getOrNull() ?: false,
        specialPhotoUrl = get<String?>("specialPhotoUrl") ?: ""
    )

    // ── Utilidad ──────────────────────────────────────────────────────────────

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
