package com.bakery.erp.data

import com.bakery.erp.domain.CashSessionRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.SaleRepository
import com.bakery.erp.domain.report.BranchSalesStat
import com.bakery.erp.domain.report.CashierSalesStat
import com.bakery.erp.domain.report.DailySalesPoint
import com.bakery.erp.domain.report.MoneyAndCount
import com.bakery.erp.domain.report.ProductSalesStat
import com.bakery.erp.domain.report.SalesSummary
import com.bakery.erp.model.FinishedMovementReference
import com.bakery.erp.model.FinishedMovementType
import com.bakery.erp.model.FinishedStockMovement
import com.bakery.erp.model.Sale
import com.bakery.erp.model.SaleLine
import com.bakery.erp.model.SaleLinePriceSource
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.SaleStatus
import com.bakery.erp.model.StockMovement
import com.bakery.erp.model.ingredientUnitsConsumed
import com.bakery.erp.model.isIngredientBacked
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

class FirebaseSaleRepository(
    private val firestore: FirebaseFirestore,
    private val finishedStockRepository: FinishedStockRepository,
    private val cashSessionRepository: CashSessionRepository,
    // Para los productos respaldados por un insumo: su venta descuenta el inventario de
    // insumos, no el de terminado.
    private val ingredientRepository: IngredientRepository
) : SaleRepository {

    private fun salesCol(companyId: String) =
        firestore.collection("companies/$companyId/sales")

    private fun linesCol(companyId: String, saleId: String) =
        firestore.collection("companies/$companyId/sales/$saleId/lines")

    override suspend fun createSale(
        companyId: String, sale: Sale, lines: List<SaleLine>
    ): Sale = coroutineScope {
        val saleRef = salesCol(companyId).document
        val now = Clock.System.now().toEpochMilliseconds()
        val saleId = saleRef.id

        // 1. Para líneas con productId: snapshot del costo en paralelo (1 round-trip por
        //    producto único, no por línea — dedupe primero).
        // Los respaldados por un insumo no tienen `FinishedProductStock` que consultar: su
        // costo viene del snapshot que trae la línea (`Product.purchaseCost` al armar el
        // carrito). Pedirlo igual devolvería 0 y además gastaría una lectura por venta.
        val productIdsToCost = lines
            .filterNot { it.isIngredientBacked }
            .mapNotNull { it.productId?.takeIf { id -> id.isNotEmpty() } }
            .distinct()
        val costByProductId: Map<String, Double> = productIdsToCost.map { pid ->
            async {
                pid to (finishedStockRepository.getStock(companyId, sale.branchId, pid)?.avgUnitCost ?: 0.0)
            }
        }.awaitAll().toMap()

        val resolvedLines = lines.map { line ->
            val pid = line.productId
            if (pid != null && pid.isNotEmpty()) {
                line.copy(
                    saleId = saleId,
                    // El costo de un producto respaldado por insumo ya viene en la línea.
                    costAtSale = if (line.isIngredientBacked) line.costAtSale
                                 else costByProductId[pid] ?: 0.0,
                    stockDeducted = true
                )
            } else {
                line.copy(saleId = saleId, costAtSale = 0.0, stockDeducted = false)
            }
        }

        // 2. Aplicar movimientos de stock en paralelo (cada línea afecta un producto distinto
        //    porque el carrito ya agrega cantidades por productId).
        // Producto respaldado por un insumo (el huevo, que entra a las recetas y además se
        // vende por docena): descuenta el BranchStock del insumo y escribe el Kardex de
        // insumos. Es un solo pozo porque es una sola caja — el mostrador y la producción
        // comen de ahí, y esa competencia es la que de verdad ocurre en la bodega.
        val ingredientMovements = resolvedLines
            .filter { it.stockDeducted && it.isIngredientBacked }
            .map { line ->
                async {
                    val consumed = line.ingredientUnitsConsumed
                    val previous = ingredientRepository
                        .getBranchStocksForIngredient(companyId, line.linkedIngredientId, listOf(sale.branchId))
                        .find { it.branchId == sale.branchId }?.currentStock ?: 0.0
                    ingredientRepository.updateBranchStock(
                        companyId, line.linkedIngredientId, line.linkedIngredientName,
                        sale.branchId, sale.branchName, -consumed
                    )
                    ingredientRepository.addStockMovement(
                        companyId, line.linkedIngredientId,
                        StockMovement(
                            type = "VENTA",
                            quantity = -consumed,
                            balanceBefore = previous,
                            balanceAfter = previous - consumed,
                            branchId = sale.branchId,
                            branchName = sale.branchName,
                            reference = saleId,
                            date = now
                        )
                    )
                }
            }

        val movementsDone = resolvedLines
            .filter { it.stockDeducted && it.productId != null && !it.isIngredientBacked }
            .map { line ->
                async {
                    finishedStockRepository.applyMovement(
                        companyId,
                        FinishedStockMovement(
                            productId = line.productId!!,
                            productName = line.productName,
                            branchId = sale.branchId,
                            branchName = sale.branchName,
                            type = FinishedMovementType.VENTA,
                            quantity = -line.quantity,
                            unitPriceAtMovement = line.unitPrice,
                            referenceType = FinishedMovementReference.SALE,
                            referenceId = saleId,
                            reason = null,
                            createdByUserId = sale.cashierId,
                            createdByUserName = sale.cashierName,
                            createdAt = now
                        )
                    )
                }
            }

        // 3. En paralelo con los movimientos: escribir cabecera, líneas e incrementar sesión.
        val finalSale = sale.copy(
            id = saleId,
            createdAt = if (sale.createdAt == 0L) now else sale.createdAt,
            lineCount = resolvedLines.size,
            status = SaleStatus.COMPLETED
        )
        val saleWrite = async { saleRef.set(finalSale.toMap()) }
        val lineWrites = resolvedLines.map { line ->
            async {
                val lineRef = if (line.id.isEmpty()) linesCol(companyId, saleId).document
                              else linesCol(companyId, saleId).document(line.id)
                lineRef.set(line.copy(id = lineRef.id).toMap())
            }
        }
        val sessionInc = async {
            if (sale.sessionId.isNotEmpty()) {
                cashSessionRepository.incrementSaleTotals(
                    companyId, sale.sessionId, sale.paymentMethod, sale.total
                )
            }
        }

        movementsDone.awaitAll()
        ingredientMovements.awaitAll()
        saleWrite.await()
        lineWrites.awaitAll()
        sessionInc.await()

        finalSale
    }

    override suspend fun getSale(companyId: String, saleId: String): Sale? =
        salesCol(companyId).document(saleId).get().takeIf { it.exists }?.toSale()

    override suspend fun getSaleLines(companyId: String, saleId: String): List<SaleLine> =
        linesCol(companyId, saleId).get().documents.map { it.toLine() }

    override suspend fun getSalesForSession(companyId: String, sessionId: String): List<Sale> =
        salesCol(companyId).where { "sessionId" equalTo sessionId }.get().documents
            .map { it.toSale() }
            .sortedByDescending { it.createdAt }

    override fun observeSalesForSession(
        companyId: String, sessionId: String
    ): Flow<List<Sale>> =
        salesCol(companyId).snapshots.map { snapshot ->
            snapshot.documents
                .map { it.toSale() }
                .filter { it.sessionId == sessionId }
                .sortedByDescending { it.createdAt }
        }

    override suspend fun getSalesForBranchInRange(
        companyId: String, branchId: String, fromMillis: Long, untilMillis: Long
    ): List<Sale> = salesCol(companyId).where { "branchId" equalTo branchId }.get().documents
        .map { it.toSale() }
        .filter { it.createdAt in fromMillis..untilMillis }
        .sortedByDescending { it.createdAt }

    override fun observeSalesForBranchInRange(
        companyId: String, branchId: String, fromMillis: Long, untilMillis: Long
    ): Flow<List<Sale>> =
        salesCol(companyId).snapshots.map { snapshot ->
            snapshot.documents
                .map { it.toSale() }
                .filter { it.branchId == branchId && it.createdAt in fromMillis..untilMillis }
                .sortedByDescending { it.createdAt }
        }

    override suspend fun aggregateSalesInRange(
        companyId: String,
        branchIds: List<String>?,
        fromMillis: Long,
        untilMillis: Long
    ): SalesSummary = coroutineScope {
        // 1. Cargar todas las ventas COMPLETED del rango. Filtramos client-side por sucursal
        //    porque no hay índice por (branchId, createdAt) — leyendo branchId server-side
        //    requeriría una query por sucursal.
        val allSales = salesCol(companyId).get().documents
            .map { it.toSale() }
            .filter {
                it.status == SaleStatus.COMPLETED &&
                    it.createdAt in fromMillis..untilMillis &&
                    (branchIds.isNullOrEmpty() || it.branchId in branchIds)
            }

        if (allSales.isEmpty()) return@coroutineScope SalesSummary()

        // 2. Líneas de todas las ventas en paralelo (una query por venta).
        val linesPerSale = allSales.map { sale ->
            async { sale to getSaleLines(companyId, sale.id) }
        }.awaitAll()

        val tz = TimeZone.currentSystemDefault()
        val totalRevenue = allSales.sumOf { it.total }
        val transactionCount = allSales.size
        val unitsSold = linesPerSale.sumOf { (_, lines) -> lines.sumOf { it.quantity } }

        // Por método de pago
        val byPaymentMethod = allSales.groupBy { it.paymentMethod }
            .mapValues { (_, sales) -> MoneyAndCount(sales.sumOf { it.total }, sales.size) }

        // Por cajero
        val byCashier = allSales.groupBy { it.cashierId to it.cashierName }
            .map { (key, sales) ->
                val total = sales.sumOf { it.total }
                CashierSalesStat(
                    cashierId = key.first,
                    cashierName = key.second,
                    transactionCount = sales.size,
                    total = total,
                    avgTicket = total / sales.size
                )
            }
            .sortedByDescending { it.total }

        // Por sucursal
        val byBranch = allSales.groupBy { it.branchId to it.branchName }
            .map { (key, sales) ->
                BranchSalesStat(
                    branchId = key.first,
                    branchName = key.second,
                    transactionCount = sales.size,
                    total = sales.sumOf { it.total }
                )
            }
            .sortedByDescending { it.total }

        // Top productos (de las líneas)
        val productAccum = HashMap<String, Triple<String, Double, Double>>() // pid -> (name, qty, revenue)
        linesPerSale.forEach { (_, lines) ->
            lines.forEach { line ->
                val pid = line.productId ?: return@forEach
                val (name, qty, rev) = productAccum[pid] ?: Triple(line.productName, 0.0, 0.0)
                productAccum[pid] = Triple(name, qty + line.quantity, rev + line.subtotal)
            }
        }
        val topProducts = productAccum.entries
            .map { (pid, t) -> ProductSalesStat(pid, t.first, t.second, t.third) }
            .sortedByDescending { it.revenue }
            .take(20)

        // Histograma horario y por día
        val hourAccum = HashMap<Int, Double>()
        val dayAccum = HashMap<String, Pair<Long, Pair<Double, Int>>>() // key=yyyy-MM-dd -> (startMillis, (rev, count))
        allSales.forEach { sale ->
            val ldt = Instant.fromEpochMilliseconds(sale.createdAt).toLocalDateTime(tz)
            hourAccum[ldt.hour] = (hourAccum[ldt.hour] ?: 0.0) + sale.total
            val dateKey = ldt.date.toString()
            val startMillis = ldt.date.atStartOfDayIn(tz).toEpochMilliseconds()
            val (_, pair) = dayAccum[dateKey] ?: (startMillis to (0.0 to 0))
            dayAccum[dateKey] = startMillis to (pair.first + sale.total to pair.second + 1)
        }
        val byDay = dayAccum.entries
            .sortedBy { it.value.first }
            .map { (label, value) ->
                DailySalesPoint(
                    dateMillis = value.first,
                    dateLabel = label,
                    revenue = value.second.first,
                    transactionCount = value.second.second
                )
            }

        SalesSummary(
            totalRevenue = totalRevenue,
            transactionCount = transactionCount,
            unitsSold = unitsSold,
            avgTicket = totalRevenue / transactionCount,
            byPaymentMethod = byPaymentMethod,
            byCashier = byCashier,
            byBranch = byBranch,
            topProducts = topProducts,
            hourHistogram = hourAccum,
            byDay = byDay
        )
    }

    // ── Serialización ────────────────────────────────────────────────────────

    private fun Sale.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "branchId" to branchId,
        "branchName" to branchName,
        "registerId" to registerId,
        "registerName" to registerName,
        "sessionId" to sessionId,
        "cashierId" to cashierId,
        "cashierName" to cashierName,
        "subtotal" to subtotal,
        "discount" to discount,
        "total" to total,
        "paymentMethod" to paymentMethod,
        "tenderedAmount" to tenderedAmount,
        "changeAmount" to changeAmount,
        "status" to status,
        "lineCount" to lineCount,
        "linkedCustomerOrderId" to linkedCustomerOrderId,
        "createdAt" to createdAt
    )

    private fun DocumentSnapshot.toSale() = Sale(
        id = id,
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        registerId = get<String?>("registerId") ?: "",
        registerName = get<String?>("registerName") ?: "",
        sessionId = get<String?>("sessionId") ?: "",
        cashierId = get<String?>("cashierId") ?: "",
        cashierName = get<String?>("cashierName") ?: "",
        subtotal = get<Double?>("subtotal") ?: 0.0,
        discount = get<Double?>("discount") ?: 0.0,
        total = get<Double?>("total") ?: 0.0,
        paymentMethod = get<String?>("paymentMethod") ?: SalePaymentMethod.CASH,
        tenderedAmount = get<Double?>("tenderedAmount") ?: 0.0,
        changeAmount = get<Double?>("changeAmount") ?: 0.0,
        status = get<String?>("status") ?: SaleStatus.COMPLETED,
        lineCount = (get<Double?>("lineCount") ?: 0.0).toInt(),
        linkedCustomerOrderId = get<String?>("linkedCustomerOrderId") ?: "",
        createdAt = readMillis("createdAt")
    )

    private fun SaleLine.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "saleId" to saleId,
        "productId" to productId,
        "productName" to productName,
        "quickGroupId" to quickGroupId,
        "quickGroupName" to quickGroupName,
        "quantity" to quantity,
        "unitPrice" to unitPrice,
        "subtotal" to subtotal,
        "costAtSale" to costAtSale,
        "priceListId" to priceListId,
        "priceSource" to priceSource,
        "stockDeducted" to stockDeducted,
        "linkedIngredientId" to linkedIngredientId,
        "linkedIngredientName" to linkedIngredientName,
        "ingredientUnitsPerSale" to ingredientUnitsPerSale
    )

    private fun DocumentSnapshot.toLine() = SaleLine(
        id = id,
        saleId = get<String?>("saleId") ?: "",
        productId = get<String?>("productId"),
        productName = get<String?>("productName") ?: "",
        quickGroupId = get<String?>("quickGroupId"),
        quickGroupName = get<String?>("quickGroupName"),
        quantity = get<Double?>("quantity") ?: 0.0,
        unitPrice = get<Double?>("unitPrice") ?: 0.0,
        subtotal = get<Double?>("subtotal") ?: 0.0,
        costAtSale = get<Double?>("costAtSale") ?: 0.0,
        priceListId = get<String?>("priceListId") ?: "",
        priceSource = get<String?>("priceSource") ?: SaleLinePriceSource.PRODUCT_SALE_PRICE,
        stockDeducted = get<Boolean?>("stockDeducted") ?: false,
        linkedIngredientId = runCatching { get<String?>("linkedIngredientId") }.getOrNull() ?: "",
        linkedIngredientName = runCatching { get<String?>("linkedIngredientName") }.getOrNull() ?: "",
        ingredientUnitsPerSale = runCatching { get<Double?>("ingredientUnitsPerSale") }.getOrNull() ?: 0.0
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
