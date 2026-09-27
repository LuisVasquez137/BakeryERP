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
import com.bakery.erp.domain.report.salesSummaryFromDaily
import com.bakery.erp.model.FinishedMovementReference
import com.bakery.erp.model.FinishedMovementType
import com.bakery.erp.model.FinishedStockMovement
import com.bakery.erp.model.CheckoutPayment
import com.bakery.erp.model.Sale
import com.bakery.erp.model.SaleContribution
import com.bakery.erp.model.SaleLine
import com.bakery.erp.model.SaleLinePriceSource
import com.bakery.erp.model.SaleSource
import com.bakery.erp.model.SalesChannel
import com.bakery.erp.model.effectivePaymentBreakdown
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.SaleStatus
import com.bakery.erp.model.SalesDaily
import com.bakery.erp.model.plus
import com.bakery.erp.model.salesDailyId
import com.bakery.erp.model.groupSaleKey
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

    private fun dailyCol(companyId: String) =
        firestore.collection("companies/$companyId/salesDaily")

    /**
     * Ventas de la empresa acotadas por fecha **en el servidor**. Todas las lecturas por
     * rango pasan por aquí.
     *
     * Antes cada una hacía `.get()` (o peor, `.snapshots`) sobre la colección entera y
     * filtraba la fecha en memoria: abrir el Home leía todas las ventas de la historia tres
     * veces, y el costo crecía con cada venta registrada. El rango se aplicaba igual, pero
     * después de haber pagado la lectura.
     *
     * `createdAt` se compara como `Double` a propósito: `firestoreSafeOf` convierte todo
     * `Long` a `Double` al escribir, así que el campo guardado es numérico — y un `Long` de
     * Kotlin no es un número para Firestore JS (ver `FirestoreSafe.kt`). Comparar contra un
     * `Long` dejaría la query sin devolver nada en web.
     *
     * El límite superior es **inclusivo**, igual que el `in fromMillis..untilMillis` que
     * reemplaza, para no cambiar ningún resultado en este paso.
     *
     * La sucursal se sigue filtrando en memoria: cruzarla con el rango exige un índice
     * compuesto `(branchId, createdAt)` que este proyecto no despliega hoy, y las sucursales
     * son pocas. Si el volumen lo pide, ése es el siguiente escalón.
     */
    private fun salesInRange(companyId: String, fromMillis: Long, untilMillis: Long) =
        salesCol(companyId).where {
            ("createdAt" greaterThanOrEqualTo fromMillis.toDouble()) and
                ("createdAt" lessThanOrEqualTo untilMillis.toDouble())
        }

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

        // 5. Aporte al resumen del día. Va al final y con la falla contenida a propósito:
        //    la venta ya está registrada y el stock ya se movió, así que un problema acá no
        //    puede tumbar un cobro que el cliente ya pagó. El resumen se repara corriendo
        //    `rebuildDailyRollups`, que recalcula desde las ventas — la fuente de verdad.
        runCatching { addToDailyRollup(companyId, contributionOf(finalSale, resolvedLines)) }

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

    // El listener va sobre la sesión, no sobre la colección completa: un `snapshots` sin
    // filtro descarga TODAS las ventas de la empresa al suscribirse y vuelve a cobrar por
    // cada documento que cambie, aunque sea de otro turno de hace meses.
    override fun observeSalesForSession(
        companyId: String, sessionId: String
    ): Flow<List<Sale>> =
        salesCol(companyId).where { "sessionId" equalTo sessionId }.snapshots.map { snapshot ->
            snapshot.documents
                .map { it.toSale() }
                .sortedByDescending { it.createdAt }
        }

    override suspend fun getSalesForBranchInRange(
        companyId: String, branchId: String, fromMillis: Long, untilMillis: Long
    ): List<Sale> = salesInRange(companyId, fromMillis, untilMillis).get().documents
        .map { it.toSale() }
        .filter { it.branchId == branchId }
        .sortedByDescending { it.createdAt }

    override fun observeSalesForBranchInRange(
        companyId: String, branchId: String, fromMillis: Long, untilMillis: Long
    ): Flow<List<Sale>> =
        salesInRange(companyId, fromMillis, untilMillis).snapshots.map { snapshot ->
            snapshot.documents
                .map { it.toSale() }
                .filter { it.branchId == branchId }
                .sortedByDescending { it.createdAt }
        }

    override fun observePendingCollections(
        companyId: String, branchId: String, fromMillis: Long, untilMillis: Long
    ): Flow<List<Sale>> =
        // Se consulta por rango de fecha (índice de un solo campo) y se filtran estado y
        // sucursal en memoria. Al revés —filtrar el estado en el servidor y la fecha acá—
        // haría que el listener creciera con cada cuenta que alguien nunca entregó.
        salesInRange(companyId, fromMillis, untilMillis).snapshots.map { snapshot ->
            snapshot.documents
                .map { it.toSale() }
                .filter { it.branchId == branchId && it.status == SaleStatus.PENDING_COLLECTION }
                .sortedBy { it.externalOrderAt.takeIf { t -> t > 0 } ?: it.createdAt }
        }

    override suspend fun collectPendingSale(
        companyId: String,
        saleId: String,
        sessionId: String,
        registerId: String,
        registerName: String,
        cashierId: String,
        cashierName: String,
        checkout: CheckoutPayment?
    ): Sale {
        val ref = salesCol(companyId).document(saleId)
        val collected = firestore.runTransaction {
            val snapshot = get(ref)
            if (!snapshot.exists) error("La cuenta ya no existe.")
            val sale = snapshot.toSale()
            // La guarda que importa: N cajas ven la misma lista. Si otra ya la recibió, ésta
            // tiene que fallar — si no, el mismo dinero entraría a dos gavetas.
            if (sale.status != SaleStatus.PENDING_COLLECTION) {
                error(
                    if (sale.cashierName.isNotEmpty()) "Esta cuenta ya la recibió ${sale.cashierName}."
                    else "Esta cuenta ya fue recibida."
                )
            }
            val received = sale.copy(
                status = SaleStatus.COMPLETED,
                sessionId = sessionId,
                registerId = registerId,
                registerName = registerName,
                cashierId = cashierId,
                cashierName = cashierName,
                collectedAt = Clock.System.now().toEpochMilliseconds()
            )
            // Cobrada desde el carrito: el cliente pagó en ESTA caja, así que el método real
            // es el del cobro y no el que traía ComandaYa. Sin esto, una cuenta pagada con
            // tarjeta en el mostrador sumaría como efectivo y el cuadre pediría billetes que
            // no están en la gaveta.
            val updated = if (checkout == null) received else received.copy(
                paymentMethod = checkout.method,
                paymentBreakdown = emptyMap(),
                checkoutId = checkout.checkoutId,
                checkoutTotal = checkout.checkoutTotal,
                tenderedAmount = checkout.tenderedAmount,
                changeAmount = checkout.changeAmount,
                billValues = checkout.billValues,
                billCounts = checkout.billCounts
            ).let { s ->
                // Se pidió factura del cobro y ComandaYa no la facturó (sin estado): queda
                // SOLICITADA en esta misma escritura, así el reintento del servidor la encuentra.
                if (checkout.invoiceReceptorId.isNotBlank() && s.invoiceStatus.isBlank()) s.copy(
                    invoiceStatus = com.bakery.erp.model.FelInvoiceStatus.SOLICITADA,
                    invoiceReceptorId = checkout.invoiceReceptorId,
                    invoiceReceptorName = checkout.invoiceReceptorName
                ) else s
            }
            set(ref, updated.toMap())
            updated
        }

        // Los totales de la gaveta van FUERA de la transacción, y por método: el efectivo se
        // cuadra contra el conteo físico y la tarjeta contra los vouchers guardados en esa
        // misma caja. Se hace después de fijar el estado, así un fallo acá no puede dejar la
        // cuenta reclamable dos veces.
        collected.effectivePaymentBreakdown.forEach { (method, amount) ->
            cashSessionRepository.incrementSaleTotals(companyId, sessionId, method, amount)
        }
        return collected
    }

    override suspend fun aggregateSalesInRange(
        companyId: String,
        branchIds: List<String>?,
        fromMillis: Long,
        untilMillis: Long
    ): SalesSummary {
        // El rango se compara contra el INICIO del día de cada resumen. Todos los llamadores
        // pasan `[inicio, finExclusivo)` en fronteras de día (ver `rangeFor` en los reportes),
        // así que el límite superior es estricto: con `<=`, un rango que termina en el inicio
        // de mañana se llevaría el día de mañana entero.
        val dailies = dailyCol(companyId).where {
            ("dateMillis" greaterThanOrEqualTo fromMillis.toDouble()) and
                ("dateMillis" lessThan untilMillis.toDouble())
        }.get().documents
            .map { it.toSalesDaily() }
            .filter { branchIds.isNullOrEmpty() || it.branchId in branchIds }

        return salesSummaryFromDaily(dailies)
    }

    override suspend fun rebuildDailyRollups(
        companyId: String,
        fromMillis: Long,
        untilMillis: Long
    ): Int = coroutineScope {
        val sales = salesInRange(companyId, fromMillis, untilMillis).get().documents
            .map { it.toSale() }
            // Las pendientes de entregar a caja YA son ingreso: el cliente pagó. Filtrar por
            // COMPLETED dejaría la reconstrucción por debajo del camino incremental.
            .filter { SaleStatus.countsAsRevenue(it.status) }

        if (sales.isEmpty()) return@coroutineScope 0

        // Una query de líneas por venta: el camino caro, y por eso vive solo acá. Es el
        // precio de reconstruir desde la fuente de verdad, que es justo lo que se pide.
        val contributions = sales.map { sale ->
            async { contributionOf(sale, getSaleLines(companyId, sale.id)) }
        }.awaitAll()

        val rebuilt = contributions
            .groupBy { salesDailyId(it.branchId, it.businessDate) }
            .map { (_, group) -> group.fold(SalesDaily()) { acc, c -> acc + c } }

        val now = Clock.System.now().toEpochMilliseconds()
        rebuilt.map { daily ->
            async {
                dailyCol(companyId).document(daily.id).set(daily.copy(updatedAt = now).toMap())
            }
        }.awaitAll()

        rebuilt.size
    }

    /**
     * Aporte de una venta a su resumen diario. **Único** lugar donde venta+líneas se traducen
     * a [SaleContribution], para que el aporte incremental del cobro y la reconstrucción
     * histórica no puedan calcular cosas distintas.
     */
    private fun contributionOf(sale: Sale, lines: List<SaleLine>): SaleContribution {
        val tz = TimeZone.currentSystemDefault()
        val ldt = Instant.fromEpochMilliseconds(sale.createdAt).toLocalDateTime(tz)
        // Las líneas express no tienen producto: suman a las unidades y al total, pero no
        // pueden entrar al desglose por producto. Las de grupo de precio ("Pan Q0.50") sí
        // entran, con clave `grp_<id>`: son la mayor parte del pan vendido y sin esto el
        // reporte las perdería de vista.
        val withProduct = lines.mapNotNull { line ->
            when {
                !line.productId.isNullOrEmpty() -> line
                !line.quickGroupId.isNullOrEmpty() ->
                    line.copy(productId = groupSaleKey(line.quickGroupId))
                else -> null
            }
        }
        return SaleContribution(
            branchId = sale.branchId,
            branchName = sale.branchName,
            businessDate = ldt.date.toString(),
            dateMillis = ldt.date.atStartOfDayIn(tz).toEpochMilliseconds(),
            hour = ldt.hour,
            total = sale.total,
            paymentMethod = sale.paymentMethod,
            // Una venta del restaurante todavía sin recibir en caja no tiene cajero: quien
            // la atendió es el mesero. Sin este respaldo, reconstruir los resúmenes le
            // borraría la atribución que sí escribe el camino incremental.
            cashierId = sale.cashierId.ifEmpty { sale.externalUserId },
            cashierName = sale.cashierName.ifEmpty { sale.externalUserName },
            channel = sale.salesChannel,
            units = lines.sumOf { it.quantity },
            productName = withProduct.associate { it.productId!! to it.productName },
            productQty = withProduct.groupBy { it.productId!! }
                .mapValues { (_, ls) -> ls.sumOf { it.quantity } },
            productRevenue = withProduct.groupBy { it.productId!! }
                .mapValues { (_, ls) -> ls.sumOf { it.subtotal } }
        )
    }

    /**
     * Suma la venta al resumen de su día.
     *
     * Va en transacción y no en un leer-modificar-escribir suelto porque dos cajas de la
     * misma sucursal cobran a la vez y la segunda pisaría a la primera. No se usa
     * `FieldValue.increment` porque GitLive 2.1.0 solo lo ofrece para enteros y esto es
     * dinero.
     */
    private suspend fun addToDailyRollup(companyId: String, c: SaleContribution) {
        val ref = dailyCol(companyId).document(salesDailyId(c.branchId, c.businessDate))
        firestore.runTransaction {
            val snapshot = get(ref)
            val current = if (snapshot.exists) snapshot.toSalesDaily() else SalesDaily()
            val updated = (current + c).copy(
                updatedAt = Clock.System.now().toEpochMilliseconds()
            )
            set(ref, updated.toMap())
        }
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
        "source" to source,
        "salesChannel" to salesChannel,
        "externalId" to externalId,
        "externalUserId" to externalUserId,
        "externalUserName" to externalUserName,
        "externalTableLabel" to externalTableLabel,
        "externalOrderNumber" to externalOrderNumber,
        "externalOrderAt" to externalOrderAt,
        "paymentBreakdown" to paymentBreakdown,
        "collectedAt" to collectedAt,
        "groupIds" to groupIds,
        "groupUnits" to groupUnits,
        "checkoutId" to checkoutId,
        "checkoutTotal" to checkoutTotal,
        "billValues" to billValues,
        "billCounts" to billCounts,
        "creditAccountId" to creditAccountId,
        "creditAccountName" to creditAccountName,
        "creditPersonName" to creditPersonName,
        "discountAccountId" to discountAccountId,
        "discountLabel" to discountLabel,
        // Van en el mapa completo porque collectPendingSale reescribe la venta con set():
        // sin ellos, recibir una cuenta ya facturada le borraría la factura.
        "invoiceStatus" to invoiceStatus,
        "dteId" to dteId,
        "invoiceSerie" to invoiceSerie,
        "invoiceNumero" to invoiceNumero,
        "invoiceUuid" to invoiceUuid,
        "invoiceReceptorId" to invoiceReceptorId,
        "invoiceReceptorName" to invoiceReceptorName,
        "externalDeliveryFee" to externalDeliveryFee,
        "externalDteId" to externalDteId,
        "invoiceConflict" to invoiceConflict,
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
        // Las ventas anteriores a estos campos son, por definición, pan cobrado en el POS.
        source = get<String?>("source") ?: SaleSource.POS,
        salesChannel = get<String?>("salesChannel") ?: SalesChannel.BAKERY,
        externalId = get<String?>("externalId") ?: "",
        externalUserId = get<String?>("externalUserId") ?: "",
        externalUserName = get<String?>("externalUserName") ?: "",
        externalTableLabel = get<String?>("externalTableLabel") ?: "",
        externalOrderNumber = get<String?>("externalOrderNumber") ?: "",
        externalOrderAt = readMillis("externalOrderAt"),
        paymentBreakdown = get<Map<String, Double>?>("paymentBreakdown") ?: emptyMap(),
        collectedAt = readMillis("collectedAt"),
        groupIds = runCatching { get<List<String>?>("groupIds") }.getOrNull() ?: emptyList(),
        groupUnits = runCatching { get<List<Double>?>("groupUnits") }.getOrNull() ?: emptyList(),
        checkoutId = get<String?>("checkoutId") ?: "",
        checkoutTotal = get<Double?>("checkoutTotal") ?: 0.0,
        billValues = runCatching { get<List<Double>?>("billValues") }.getOrNull() ?: emptyList(),
        billCounts = runCatching { get<List<Double>?>("billCounts") }.getOrNull() ?: emptyList(),
        creditAccountId = get<String?>("creditAccountId") ?: "",
        creditAccountName = get<String?>("creditAccountName") ?: "",
        creditPersonName = get<String?>("creditPersonName") ?: "",
        discountAccountId = get<String?>("discountAccountId") ?: "",
        discountLabel = get<String?>("discountLabel") ?: "",
        invoiceStatus = runCatching { get<String?>("invoiceStatus") }.getOrNull() ?: "",
        dteId = runCatching { get<String?>("dteId") }.getOrNull() ?: "",
        invoiceSerie = runCatching { get<String?>("invoiceSerie") }.getOrNull() ?: "",
        invoiceNumero = runCatching { get<String?>("invoiceNumero") }.getOrNull() ?: "",
        invoiceUuid = runCatching { get<String?>("invoiceUuid") }.getOrNull() ?: "",
        invoiceReceptorId = runCatching { get<String?>("invoiceReceptorId") }.getOrNull() ?: "",
        invoiceReceptorName = runCatching { get<String?>("invoiceReceptorName") }.getOrNull() ?: "",
        externalDeliveryFee = runCatching { get<Double?>("externalDeliveryFee") }.getOrNull()
            ?: runCatching { get<Long?>("externalDeliveryFee")?.toDouble() }.getOrNull() ?: 0.0,
        externalDteId = runCatching { get<String?>("externalDteId") }.getOrNull() ?: "",
        invoiceConflict = runCatching { get<Boolean?>("invoiceConflict") }.getOrNull() ?: false,
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

    private fun SalesDaily.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "branchId" to branchId,
        "branchName" to branchName,
        "businessDate" to businessDate,
        "dateMillis" to dateMillis,
        "revenue" to revenue,
        "transactionCount" to transactionCount,
        "unitsSold" to unitsSold,
        "paymentTotal" to paymentTotal,
        "paymentCount" to paymentCount,
        "cashierName" to cashierName,
        "cashierTotal" to cashierTotal,
        "cashierCount" to cashierCount,
        "hourTotal" to hourTotal,
        "productName" to productName,
        "productQty" to productQty,
        "productRevenue" to productRevenue,
        "channelRevenue" to channelRevenue,
        "channelCount" to channelCount,
        "channelUnits" to channelUnits,
        "updatedAt" to updatedAt
    )

    // Los mapas se leen con su tipo concreto y SIN runCatching: `get<Map<String, Any?>>()`
    // revienta con "Serializer for Any not found", y taparlo devolvería resúmenes en cero
    // sin que nadie se entere — que es exactamente cómo se perdieron las sugerencias de
    // pedidos en su momento. Si algo se rompe acá, tiene que romperse fuerte.
    private fun DocumentSnapshot.toSalesDaily() = SalesDaily(
        id = id,
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        businessDate = get<String?>("businessDate") ?: "",
        dateMillis = readMillis("dateMillis"),
        revenue = get<Double?>("revenue") ?: 0.0,
        transactionCount = (get<Double?>("transactionCount") ?: 0.0).toInt(),
        unitsSold = get<Double?>("unitsSold") ?: 0.0,
        paymentTotal = get<Map<String, Double>?>("paymentTotal") ?: emptyMap(),
        paymentCount = get<Map<String, Int>?>("paymentCount") ?: emptyMap(),
        cashierName = get<Map<String, String>?>("cashierName") ?: emptyMap(),
        cashierTotal = get<Map<String, Double>?>("cashierTotal") ?: emptyMap(),
        cashierCount = get<Map<String, Int>?>("cashierCount") ?: emptyMap(),
        hourTotal = get<Map<String, Double>?>("hourTotal") ?: emptyMap(),
        productName = get<Map<String, String>?>("productName") ?: emptyMap(),
        productQty = get<Map<String, Double>?>("productQty") ?: emptyMap(),
        productRevenue = get<Map<String, Double>?>("productRevenue") ?: emptyMap(),
        channelRevenue = get<Map<String, Double>?>("channelRevenue") ?: emptyMap(),
        channelCount = get<Map<String, Int>?>("channelCount") ?: emptyMap(),
        channelUnits = get<Map<String, Double>?>("channelUnits") ?: emptyMap(),
        updatedAt = readMillis("updatedAt")
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
