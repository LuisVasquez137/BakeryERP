package com.bakery.erp.data

import com.bakery.erp.domain.CustomerOrderRepository
import com.bakery.erp.domain.report.CustomerOrderTypeStat
import com.bakery.erp.domain.report.CustomerOrdersSummary
import com.bakery.erp.domain.report.CustomerStat
import com.bakery.erp.model.CakeOrderDetails
import com.bakery.erp.model.CakeTemperature
import com.bakery.erp.model.CakeType
import com.bakery.erp.model.CustomerOrder
import com.bakery.erp.model.CustomerOrderDeliveryType
import com.bakery.erp.model.CustomerOrderLine
import com.bakery.erp.model.CustomerOrderPayment
import com.bakery.erp.model.CustomerOrderPaymentMethod
import com.bakery.erp.model.CustomerOrderPaymentType
import com.bakery.erp.model.CustomerOrderStatus
import com.bakery.erp.model.CustomerOrderType
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class FirebaseCustomerOrderRepository(
    private val firestore: FirebaseFirestore
) : CustomerOrderRepository {

    private fun ordersCol(companyId: String) =
        firestore.collection("companies/$companyId/customerOrders")

    private fun linesCol(companyId: String, orderId: String) =
        firestore.collection("companies/$companyId/customerOrders/$orderId/lines")

    private fun paymentsCol(companyId: String, orderId: String) =
        firestore.collection("companies/$companyId/customerOrders/$orderId/payments")

    // ── Lectura ───────────────────────────────────────────────────────────────

    override fun observeOrders(companyId: String): Flow<List<CustomerOrder>> =
        ordersCol(companyId).snapshots.map { snapshot ->
            snapshot.documents.map { it.toOrder() }
        }

    override suspend fun getOrdersForCustomer(
        companyId: String,
        customerId: String
    ): List<CustomerOrder> =
        ordersCol(companyId).get().documents
            .map { it.toOrder() }
            .filter { it.customerId == customerId }
            .sortedByDescending { it.deliveryDate }

    override suspend fun getOrdersInRange(
        companyId: String,
        fromMillis: Long,
        untilMillis: Long
    ): List<CustomerOrder> =
        ordersCol(companyId).get().documents
            .map { it.toOrder() }
            .filter { it.deliveryDate in fromMillis until untilMillis }
            .sortedByDescending { it.deliveryDate }

    override suspend fun getPendingOrdersForProducingBranch(
        companyId: String,
        producingBranchId: String,
        deliveryDate: Long,
        shiftId: String
    ): List<CustomerOrder> {
        val tz = TimeZone.currentSystemDefault()
        val targetDay = Instant.fromEpochMilliseconds(deliveryDate).toLocalDateTime(tz).date
        return ordersCol(companyId).get().documents
            .map { it.toOrder() }
            .filter {
                it.status == CustomerOrderStatus.PENDING &&
                    it.producingBranchId == producingBranchId &&
                    it.shiftId == shiftId &&
                    Instant.fromEpochMilliseconds(it.deliveryDate)
                        .toLocalDateTime(tz).date == targetDay
            }
            .sortedBy { it.customerName }
    }

    override suspend fun getPendingOrdersForSellingBranch(
        companyId: String,
        sellingBranchId: String,
        includingLinesLinkedToPO: String
    ): List<CustomerOrder> {
        // Pre-filtra los CO de esa sucursal que NO están en estados terminales,
        // luego para cada uno verifica si tiene al menos una línea sin asignar
        // O al menos una línea ya asignada al PO que se está editando.
        val candidates = ordersCol(companyId).get().documents
            .map { it.toOrder() }
            .filter {
                it.deliveryBranchId == sellingBranchId &&
                    it.status !in listOf(
                        CustomerOrderStatus.CANCELLED,
                        CustomerOrderStatus.DELIVERED,
                        CustomerOrderStatus.IN_PRODUCTION,
                        CustomerOrderStatus.READY
                    )
            }
        val result = mutableListOf<CustomerOrder>()
        for (co in candidates) {
            val lines = runCatching { getOrderLines(companyId, co.id) }.getOrElse { emptyList() }
            val matches = lines.any { line ->
                line.requiresProduction && (
                    line.linkedProductionOrderId.isEmpty() ||
                        (includingLinesLinkedToPO.isNotEmpty() &&
                            line.linkedProductionOrderId == includingLinesLinkedToPO)
                    )
            }
            if (matches) result += co
        }
        return result.sortedBy { it.deliveryDate }
    }

    override suspend fun setLinesLinkedPO(
        companyId: String,
        orderId: String,
        lineIds: List<String>,
        productionOrderId: String
    ) {
        if (lineIds.isEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()
        lineIds.forEach { lineId ->
            val lineDoc = linesCol(companyId, orderId).document(lineId).get()
            if (!lineDoc.exists) return@forEach
            val current = lineDoc.toLine()
            linesCol(companyId, orderId).document(lineId).set(
                current.copy(linkedProductionOrderId = productionOrderId).toLineMap()
            )
        }
        recomputeStatus(companyId, orderId, now)
    }

    override suspend fun unsetLinesLinkedPO(
        companyId: String,
        orderId: String,
        lineIds: List<String>
    ) {
        if (lineIds.isEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()
        lineIds.forEach { lineId ->
            val lineDoc = linesCol(companyId, orderId).document(lineId).get()
            if (!lineDoc.exists) return@forEach
            val current = lineDoc.toLine()
            linesCol(companyId, orderId).document(lineId).set(
                current.copy(linkedProductionOrderId = "").toLineMap()
            )
        }
        recomputeStatus(companyId, orderId, now)
    }

    /**
     * Recalcula el status del header del CO según el estado de asignación de sus
     * líneas. Solo aplica a la matriz PENDING / PARTIALLY_CONFIRMED / CONFIRMED.
     * Si el CO ya está en estados posteriores (IN_PRODUCTION, READY, DELIVERED,
     * CANCELLED) NO los toca — esos los maneja el flujo de producción/despacho.
     */
    private suspend fun recomputeStatus(companyId: String, orderId: String, nowMillis: Long) {
        val order = getOrder(companyId, orderId) ?: return
        if (order.status in listOf(
                CustomerOrderStatus.IN_PRODUCTION,
                CustomerOrderStatus.READY,
                CustomerOrderStatus.DELIVERED,
                CustomerOrderStatus.CANCELLED
            )
        ) return
        val lines = getOrderLines(companyId, orderId).filter { it.requiresProduction }
        if (lines.isEmpty()) return
        val newStatus = when {
            lines.all { it.linkedProductionOrderId.isNotEmpty() } -> CustomerOrderStatus.CONFIRMED
            lines.any { it.linkedProductionOrderId.isNotEmpty() } -> CustomerOrderStatus.PARTIALLY_CONFIRMED
            else -> CustomerOrderStatus.PENDING
        }
        if (newStatus != order.status) {
            ordersCol(companyId).document(orderId).set(
                order.copy(status = newStatus, updatedAt = nowMillis).toOrderMap()
            )
        }
    }

    override suspend fun getOrder(companyId: String, orderId: String): CustomerOrder? =
        runCatching { ordersCol(companyId).document(orderId).get().toOrder() }.getOrNull()

    override suspend fun getOrderLines(companyId: String, orderId: String): List<CustomerOrderLine> =
        linesCol(companyId, orderId).get().documents.map { it.toLine() }

    override suspend fun getOrderPayments(
        companyId: String,
        orderId: String
    ): List<CustomerOrderPayment> =
        paymentsCol(companyId, orderId).get().documents
            .map { it.toPayment() }
            .sortedBy { it.receivedAt }

    // ── Escritura ─────────────────────────────────────────────────────────────

    override suspend fun saveOrder(
        companyId: String,
        order: CustomerOrder,
        lines: List<CustomerOrderLine>
    ) {
        val docRef = if (order.id.isEmpty()) ordersCol(companyId).document
                     else ordersCol(companyId).document(order.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val final = order.copy(
            id = docRef.id,
            lineCount = lines.size,
            balanceAmount = (order.totalAmount - order.advancePaidAmount).coerceAtLeast(0.0),
            updatedAt = now,
            createdAt = if (order.createdAt == 0L) now else order.createdAt
        )
        docRef.set(final.toOrderMap())
        lines.forEach { line ->
            val lineDoc = if (line.id.isEmpty()) linesCol(companyId, docRef.id).document
                          else linesCol(companyId, docRef.id).document(line.id)
            lineDoc.set(line.copy(id = lineDoc.id).toLineMap())
        }
    }

    override suspend fun updateOrder(
        companyId: String,
        order: CustomerOrder,
        lines: List<CustomerOrderLine>
    ) {
        require(order.id.isNotEmpty()) { "updateOrder requiere un id de pedido" }
        val now = Clock.System.now().toEpochMilliseconds()
        val final = order.copy(
            lineCount = lines.size,
            balanceAmount = (order.totalAmount - order.advancePaidAmount).coerceAtLeast(0.0),
            updatedAt = now,
            createdAt = if (order.createdAt == 0L) now else order.createdAt
        )
        ordersCol(companyId).document(order.id).set(final.toOrderMap())

        val existingIds = linesCol(companyId, order.id).get().documents.map { it.id }.toSet()
        val keptIds = mutableSetOf<String>()
        lines.forEach { line ->
            val lineDoc = if (line.id.isEmpty()) linesCol(companyId, order.id).document
                          else linesCol(companyId, order.id).document(line.id)
            lineDoc.set(line.copy(id = lineDoc.id).toLineMap())
            keptIds += lineDoc.id
        }
        (existingIds - keptIds).forEach { obsoleteId ->
            runCatching { linesCol(companyId, order.id).document(obsoleteId).delete() }
        }
    }

    override suspend fun updateOrderStatus(
        companyId: String,
        orderId: String,
        newStatus: String
    ) {
        val order = getOrder(companyId, orderId) ?: return
        ordersCol(companyId).document(orderId).set(
            order.copy(
                status = newStatus,
                updatedAt = Clock.System.now().toEpochMilliseconds()
            ).toOrderMap()
        )
    }

    override suspend fun cancelOrder(companyId: String, orderId: String) {
        updateOrderStatus(companyId, orderId, CustomerOrderStatus.CANCELLED)
    }

    // ── Pagos ─────────────────────────────────────────────────────────────────

    override suspend fun recordPayment(
        companyId: String,
        orderId: String,
        payment: CustomerOrderPayment
    ) {
        val order = getOrder(companyId, orderId) ?: error("Pedido no encontrado: $orderId")
        val docRef = if (payment.id.isEmpty()) paymentsCol(companyId, orderId).document
                     else paymentsCol(companyId, orderId).document(payment.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val final = payment.copy(
            id = docRef.id,
            orderId = orderId,
            receivedAt = if (payment.receivedAt == 0L) now else payment.receivedAt
        )
        docRef.set(final.toMap())

        // Recalcula denormalizados desde el conjunto completo de pagos.
        val payments = getOrderPayments(companyId, orderId)
        val advance = payments
            .filter { it.type != CustomerOrderPaymentType.BALANCE }
            .sumOf { it.amount }
        val balanceFromBalance = payments
            .filter { it.type == CustomerOrderPaymentType.BALANCE }
            .sumOf { it.amount }
        val totalPaid = advance + balanceFromBalance
        val newBalance = (order.totalAmount - totalPaid).coerceAtLeast(0.0)

        // Auto-DELIVERED si era READY y el saldo se completa con un pago BALANCE.
        val newStatus = if (
            payment.type == CustomerOrderPaymentType.BALANCE &&
            order.status == CustomerOrderStatus.READY &&
            newBalance <= 0.0
        ) CustomerOrderStatus.DELIVERED else order.status

        ordersCol(companyId).document(orderId).set(
            order.copy(
                advancePaidAmount = advance,
                balanceAmount = newBalance,
                status = newStatus,
                updatedAt = now
            ).toOrderMap()
        )
    }

    // ── Reportes ──────────────────────────────────────────────────────────────

    override suspend fun aggregateCustomerOrdersInRange(
        companyId: String,
        fromMillis: Long,
        untilMillis: Long
    ): CustomerOrdersSummary {
        val orders = ordersCol(companyId).get().documents
            .map { it.toOrder() }
            .filter {
                it.deliveryDate in fromMillis until untilMillis &&
                    it.status != CustomerOrderStatus.CANCELLED
            }
        if (orders.isEmpty()) return CustomerOrdersSummary()

        val byTypeAcc = HashMap<String, Quad>()
        orders.forEach { o ->
            val cur = byTypeAcc[o.type] ?: Quad()
            byTypeAcc[o.type] = cur.copy(
                count = cur.count + 1,
                totalAmount = cur.totalAmount + o.totalAmount,
                advancesCollected = cur.advancesCollected + o.advancePaidAmount,
                pendingBalance = cur.pendingBalance + o.balanceAmount
            )
        }

        val byCustomerAcc = HashMap<String, Triple<String, Int, Double>>()
        orders.forEach { o ->
            if (o.customerId.isEmpty()) return@forEach
            val (name, count, total) = byCustomerAcc[o.customerId]
                ?: Triple(o.customerName, 0, 0.0)
            byCustomerAcc[o.customerId] = Triple(name, count + 1, total + o.totalAmount)
        }
        val topCustomers = byCustomerAcc.entries
            .map { (id, t) -> CustomerStat(id, t.first, t.second, t.third) }
            .sortedByDescending { it.totalAmount }
            .take(10)

        return CustomerOrdersSummary(
            ordersCount = orders.size,
            byType = byTypeAcc.mapValues {
                CustomerOrderTypeStat(
                    count = it.value.count,
                    totalAmount = it.value.totalAmount,
                    advancesCollected = it.value.advancesCollected,
                    pendingBalance = it.value.pendingBalance
                )
            },
            totalExpectedRevenue = orders.sumOf { it.totalAmount },
            totalAdvancesCollected = orders.sumOf { it.advancePaidAmount },
            totalPendingBalance = orders
                .filter {
                    it.status in listOf(
                        CustomerOrderStatus.CONFIRMED,
                        CustomerOrderStatus.IN_PRODUCTION,
                        CustomerOrderStatus.READY
                    )
                }
                .sumOf { it.balanceAmount },
            topCustomers = topCustomers
        )
    }

    private data class Quad(
        val count: Int = 0,
        val totalAmount: Double = 0.0,
        val advancesCollected: Double = 0.0,
        val pendingBalance: Double = 0.0,
    )

    // ── Serialización ─────────────────────────────────────────────────────────

    private fun CustomerOrder.toOrderMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "type" to type,
        "status" to status,
        "customerId" to customerId,
        "customerName" to customerName,
        "customerPhone" to customerPhone,
        "customerPhoneAlt" to customerPhoneAlt,
        "recipientName" to recipientName,
        "deliveryType" to deliveryType,
        "deliveryBranchId" to deliveryBranchId,
        "deliveryBranchName" to deliveryBranchName,
        "deliveryAddress" to deliveryAddress,
        "deliveryAddressReference" to deliveryAddressReference,
        "deliveryDate" to deliveryDate,
        "deliveryTime" to deliveryTime,
        "producingBranchId" to producingBranchId,
        "producingBranchName" to producingBranchName,
        "shiftId" to shiftId,
        "shiftName" to shiftName,
        "isLateOrder" to isLateOrder,
        "cakeDetails" to cakeDetails?.let {
            mapOf(
                "cakeType" to it.cakeType,
                "temperature" to it.temperature,
                "totalPortions" to it.totalPortions,
                "biscocho" to it.biscocho,
                "filling" to it.filling,
                "extras" to it.extras,
                "clientSignatureUrl" to it.clientSignatureUrl,
            )
        },
        "priceListId" to priceListId,
        "priceListName" to priceListName,
        "subtotalAmount" to subtotalAmount,
        "riceSheetAmount" to riceSheetAmount,
        "fountainRentAmount" to fountainRentAmount,
        "extrasAmount" to extrasAmount,
        "totalAmount" to totalAmount,
        "depositBaseAmount" to depositBaseAmount,
        "advancePaidAmount" to advancePaidAmount,
        "balanceAmount" to balanceAmount,
        "notes" to notes,
        "createdByUserId" to createdByUserId,
        "createdByUserName" to createdByUserName,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "lineCount" to lineCount
    )

    private fun CustomerOrderLine.toLineMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "productId" to productId,
        "productName" to productName,
        "productUnit" to productUnit,
        "requestedQuantity" to requestedQuantity,
        "unitPrice" to unitPrice,
        "priceSource" to priceSource,
        "customSpec" to customSpec,
        "requiresProduction" to requiresProduction,
        "linkedProductionOrderId" to linkedProductionOrderId
    )

    private fun CustomerOrderPayment.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "orderId" to orderId,
        "type" to type,
        "amount" to amount,
        "method" to method,
        "saleId" to saleId,
        "cashSessionId" to cashSessionId,
        "branchId" to branchId,
        "receivedByUserId" to receivedByUserId,
        "receivedByUserName" to receivedByUserName,
        "receivedAt" to receivedAt,
        "notes" to notes
    )

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toOrder(): CustomerOrder {
        // `get<Map<String, Any?>?>(...)` no funciona: kotlinx.serialization no tiene
        // serializer para `Any` (ver quantitiesByOrder en FirebaseProductionRepository,
        // mismo bug) — siempre lanzaba y el runCatching lo devolvía como null en
        // silencio, así que un pastel guardado nunca volvía a cargar sus datos al
        // reabrir el pedido. Se leen los campos concretos por path punteado en vez de
        // pedir el mapa completo como `Any?`.
        val cake = if (get<String?>("type") == CustomerOrderType.CAKE) {
            CakeOrderDetails(
                cakeType = get<String?>("cakeDetails.cakeType") ?: CakeType.FONDANT,
                temperature = get<String?>("cakeDetails.temperature") ?: CakeTemperature.AMBIENT,
                totalPortions = runCatching { get<Long?>("cakeDetails.totalPortions")?.toInt() }
                    .getOrNull() ?: 0,
                biscocho = get<String?>("cakeDetails.biscocho") ?: "",
                filling = get<String?>("cakeDetails.filling") ?: "",
                extras = get<String?>("cakeDetails.extras") ?: "",
                clientSignatureUrl = get<String?>("cakeDetails.clientSignatureUrl") ?: "",
            )
        } else null
        println("BAKEOS_DEBUG4 orderId=$id type=${get<String?>("type")} cake=$cake")
        return CustomerOrder(
            id = id,
            type = get<String?>("type") ?: CustomerOrderType.RETAIL_SPECIAL,
            status = get<String?>("status") ?: CustomerOrderStatus.PENDING,
            customerId = get<String?>("customerId") ?: "",
            customerName = get<String?>("customerName") ?: "",
            customerPhone = get<String?>("customerPhone") ?: "",
            customerPhoneAlt = get<String?>("customerPhoneAlt") ?: "",
            recipientName = get<String?>("recipientName") ?: "",
            deliveryType = get<String?>("deliveryType") ?: CustomerOrderDeliveryType.PICKUP,
            deliveryBranchId = get<String?>("deliveryBranchId") ?: "",
            deliveryBranchName = get<String?>("deliveryBranchName") ?: "",
            deliveryAddress = get<String?>("deliveryAddress") ?: "",
            deliveryAddressReference = get<String?>("deliveryAddressReference") ?: "",
            deliveryDate = readMillis("deliveryDate"),
            deliveryTime = get<String?>("deliveryTime") ?: "",
            producingBranchId = get<String?>("producingBranchId") ?: "",
            producingBranchName = get<String?>("producingBranchName") ?: "",
            shiftId = get<String?>("shiftId") ?: "",
            shiftName = get<String?>("shiftName") ?: "",
            isLateOrder = runCatching { get<Boolean?>("isLateOrder") }.getOrNull() ?: false,
            cakeDetails = cake,
            priceListId = get<String?>("priceListId") ?: "",
            priceListName = get<String?>("priceListName") ?: "",
            subtotalAmount = readDouble("subtotalAmount"),
            riceSheetAmount = readDouble("riceSheetAmount"),
            fountainRentAmount = readDouble("fountainRentAmount"),
            extrasAmount = readDouble("extrasAmount"),
            totalAmount = readDouble("totalAmount"),
            depositBaseAmount = readDouble("depositBaseAmount"),
            advancePaidAmount = readDouble("advancePaidAmount"),
            balanceAmount = readDouble("balanceAmount"),
            notes = get<String?>("notes") ?: "",
            createdByUserId = get<String?>("createdByUserId") ?: "",
            createdByUserName = get<String?>("createdByUserName") ?: "",
            createdAt = readMillis("createdAt"),
            updatedAt = readMillis("updatedAt"),
            lineCount = runCatching { get<Long?>("lineCount")?.toInt() }.getOrNull() ?: 0
        )
    }

    private fun DocumentSnapshot.toLine() = CustomerOrderLine(
        id = id,
        productId = get<String?>("productId") ?: "",
        productName = get<String?>("productName") ?: "",
        productUnit = get<String?>("productUnit") ?: "",
        requestedQuantity = readDouble("requestedQuantity"),
        unitPrice = readDouble("unitPrice"),
        priceSource = get<String?>("priceSource") ?: "",
        customSpec = get<String?>("customSpec") ?: "",
        requiresProduction = runCatching {
            get<Boolean?>("requiresProduction")
        }.getOrNull() ?: true,
        linkedProductionOrderId = get<String?>("linkedProductionOrderId") ?: ""
    )

    private fun DocumentSnapshot.toPayment() = CustomerOrderPayment(
        id = id,
        orderId = get<String?>("orderId") ?: "",
        type = get<String?>("type") ?: CustomerOrderPaymentType.ADVANCE,
        amount = readDouble("amount"),
        method = get<String?>("method") ?: CustomerOrderPaymentMethod.CASH,
        saleId = get<String?>("saleId") ?: "",
        cashSessionId = get<String?>("cashSessionId") ?: "",
        branchId = get<String?>("branchId") ?: "",
        receivedByUserId = get<String?>("receivedByUserId") ?: "",
        receivedByUserName = get<String?>("receivedByUserName") ?: "",
        receivedAt = readMillis("receivedAt"),
        notes = get<String?>("notes") ?: ""
    )

    private fun DocumentSnapshot.readDouble(field: String): Double =
        runCatching { get<Double?>(field) }.getOrNull() ?: 0.0

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
