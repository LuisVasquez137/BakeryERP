package com.bakery.erp.data

import com.bakery.erp.domain.BreadCountRepository
import com.bakery.erp.domain.CounterRemovalRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.POSCategoryRepository
import com.bakery.erp.domain.SaleRepository
import com.bakery.erp.domain.report.MermaReasonStat
import com.bakery.erp.domain.report.MermaSummary
import com.bakery.erp.model.BreadCount
import com.bakery.erp.model.BreadCountLine
import com.bakery.erp.model.BreadCountReason
import com.bakery.erp.model.BreadCountStatus
import com.bakery.erp.model.FinishedMovementReference
import com.bakery.erp.model.FinishedMovementType
import com.bakery.erp.model.FinishedStockMovement
import com.bakery.erp.model.GroupSalesWindow
import com.bakery.erp.model.POSCategory
import com.bakery.erp.model.SaleStatus
import com.bakery.erp.model.allocateGroupSales
import com.bakery.erp.model.effectiveWaste
import com.bakery.erp.model.groupDisplayName
import com.bakery.erp.model.groupUnitsById
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebaseBreadCountRepository(
    private val firestore: FirebaseFirestore,
    private val finishedStockRepo: FinishedStockRepository,
    private val saleRepository: SaleRepository,
    private val posCategoryRepository: POSCategoryRepository,
    private val counterRemovalRepository: CounterRemovalRepository
) : BreadCountRepository {

    private fun countsCol(companyId: String) =
        firestore.collection("companies/$companyId/breadCounts")

    private fun linesCol(companyId: String, countId: String) =
        firestore.collection("companies/$companyId/breadCounts/$countId/lines")

    override suspend fun openOrResume(
        companyId: String,
        branchId: String,
        branchName: String,
        countDate: String,
        countedByUserId: String,
        countedByUserName: String
    ): BreadCount {
        val existing = getCountForDate(companyId, branchId, countDate)
        if (existing != null) {
            if (existing.status == BreadCountStatus.CLOSED) {
                throw IllegalStateException("Ya existe un conteo cerrado para esta fecha.")
            }
            return existing
        }
        val docRef = countsCol(companyId).document
        val now = Clock.System.now().toEpochMilliseconds()
        val stocks = finishedStockRepo.getStocksForBranch(companyId, branchId)
            .filter { it.currentStock > 0 }
        val header = BreadCount(
            id = docRef.id,
            branchId = branchId,
            branchName = branchName,
            countDate = countDate,
            countedByUserId = countedByUserId,
            countedByUserName = countedByUserName,
            status = BreadCountStatus.DRAFT,
            lineCount = stocks.size,
            createdAt = now
        )
        docRef.set(header.toMap())
        // Snapshot inicial: una línea por producto con stock > 0.
        stocks.forEach { stock ->
            val lineRef = linesCol(companyId, header.id).document
            val line = BreadCountLine(
                id = lineRef.id,
                productId = stock.productId,
                productName = stock.productName,
                expectedStock = stock.currentStock,
                countedStock = stock.currentStock,
                difference = 0.0,
                reason = "",
                reasonNote = "",
                unitCostSnapshot = stock.avgUnitCost,
                valueImpact = 0.0
            )
            lineRef.set(line.toMap())
        }
        return header
    }

    override suspend fun getCount(companyId: String, countId: String): BreadCount? =
        countsCol(companyId).document(countId).get().takeIf { it.exists }?.toCount()

    override suspend fun getLines(companyId: String, countId: String): List<BreadCountLine> =
        linesCol(companyId, countId).get().documents
            .map { it.toLine() }
            .sortedBy { it.productName }

    override fun observeCount(companyId: String, countId: String): Flow<BreadCount?> =
        countsCol(companyId).document(countId).snapshots.map { snap ->
            if (snap.exists) snap.toCount() else null
        }

    override fun observeLines(companyId: String, countId: String): Flow<List<BreadCountLine>> =
        linesCol(companyId, countId).snapshots.map { snap ->
            snap.documents.map { it.toLine() }.sortedBy { it.productName }
        }

    override suspend fun getCountForDate(
        companyId: String, branchId: String, countDate: String
    ): BreadCount? = countsCol(companyId).where { "branchId" equalTo branchId }.get().documents
        .map { it.toCount() }
        .firstOrNull { it.countDate == countDate }

    override suspend fun getCountsForMonth(
        companyId: String, branchId: String, yearMonth: String
    ): List<BreadCount> = countsCol(companyId).where { "branchId" equalTo branchId }.get().documents
        .map { it.toCount() }
        .filter { it.countDate.startsWith(yearMonth) }
        .sortedByDescending { it.countDate }

    override suspend fun updateLine(companyId: String, countId: String, line: BreadCountLine) {
        val diff = line.countedStock - line.expectedStock
        val valueImpact = diff * line.unitCostSnapshot
        linesCol(companyId, countId).document(line.id)
            .set(line.copy(difference = diff, valueImpact = valueImpact).toMap())
    }

    override suspend fun groupSalesWindow(companyId: String, countId: String): GroupSalesWindow {
        val header = getCount(companyId, countId)
            ?: throw IllegalStateException("Conteo no encontrado: $countId")
        val closed = header.status == BreadCountStatus.CLOSED
        // Un conteo ya cerrado conserva su ventana: recalcularla con "ahora" mostraría ventas
        // que le tocan al conteo siguiente.
        val until = if (closed && header.salesWindowUntil > 0) header.salesWindowUntil
                    else Clock.System.now().toEpochMilliseconds()
        val from = if (closed && header.salesWindowFrom > 0) {
            header.salesWindowFrom
        } else {
            // Desde el cierre del conteo anterior de la sucursal: un día sin contar se acumula
            // al siguiente en vez de perderse. El primer conteo arranca al inicio de su día.
            val previousClose = countsCol(companyId).where { "branchId" equalTo header.branchId }
                .get().documents.map { it.toCount() }
                .filter { it.id != countId && it.status == BreadCountStatus.CLOSED }
                .mapNotNull { it.closedAt }
                .filter { it < until }
                .maxOrNull()
            // +1: la consulta de ventas incluye los dos extremos, y una venta justo en el
            // cierre anterior ya la repartió aquel conteo.
            previousClose?.plus(1) ?: startOfDayMillis(header.countDate, header.createdAt)
        }

        // Aunque la categoría esté inactiva: si se apagó a media jornada, lo que ya se vendió
        // igual hay que repartirlo.
        val categories = posCategoryRepository.getCategories(companyId)
            .filter { it.sellAsGroup && it.appliesToBranch(header.branchId) }

        val unitsByGroup = mutableMapOf<String, Double>()
        saleRepository.getSalesForBranchInRange(companyId, header.branchId, from, until)
            .filter { SaleStatus.countsAsRevenue(it.status) }
            .forEach { sale ->
                sale.groupUnitsById.forEach { (gid, units) ->
                    unitsByGroup[gid] = (unitsByGroup[gid] ?: 0.0) + units
                }
            }

        // Pan de colaborador anotado en caja: tampoco descontó producto, lo reparte este conteo.
        // +1: getInRange excluye el extremo final y la ventana lo incluye.
        val staffUnitsByGroup = counterRemovalRepository.getInRange(companyId, header.branchId, from, until + 1)
            .filter { it.isStaffBread && it.groupId.isNotEmpty() && it.groupUnits > 0.0 }
            .groupBy { it.groupId }
            .mapValues { (_, list) -> list.sumOf { it.groupUnits } }

        return GroupSalesWindow(
            fromMillis = from,
            untilMillis = until,
            unitsByGroup = unitsByGroup,
            staffUnitsByGroup = staffUnitsByGroup,
            groupNames = categories.associate { it.id to it.groupDisplayName },
            groupUnitPrices = categories.associate { it.id to it.groupUnitPrice },
            groupProductIds = categories.associate { it.id to it.productIds.toSet() }
        )
    }

    private fun POSCategory.appliesToBranch(branchId: String) =
        branchIds.isEmpty() || branchId in branchIds

    private fun startOfDayMillis(countDate: String, fallback: Long): Long =
        runCatching {
            LocalDate.parse(countDate).atStartOfDayIn(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
        }.getOrDefault(fallback)

    override suspend fun closeCount(
        companyId: String,
        countId: String,
        notes: String?,
        currentUserId: String,
        currentUserName: String
    ): BreadCount {
        val header = getCount(companyId, countId)
            ?: throw IllegalStateException("Conteo no encontrado: $countId")
        if (header.status == BreadCountStatus.CLOSED) return header
        val lines = getLines(companyId, countId)
        val window = groupSalesWindow(companyId, countId)
        val allocation = allocateGroupSales(window.unitsByGroup, window.groupProductIds, lines, window.staffUnitsByGroup)
        val groupOfProduct = window.groupOfProduct
        val now = Clock.System.now().toEpochMilliseconds()

        var totalShortage = 0.0
        var totalSurplus = 0.0

        // En serie a propósito: `applyMovement` lee-modifica-escribe el stock, y la VENTA y la
        // MERMA de un mismo pan en paralelo se pisarían.
        lines.forEach { line ->
            val diff = line.countedStock - line.expectedStock
            if (diff == 0.0) return@forEach
            val absDiff = abs(diff)
            val unitCost = line.unitCostSnapshot
            if (diff < 0) {
                val sold = allocation.soldOf(line.productId).coerceAtMost(absDiff)
                val staff = allocation.staffOf(line.productId).coerceAtMost(absDiff - sold)
                val waste = absDiff - sold - staff
                if (sold > 0.0) {
                    val groupId = groupOfProduct[line.productId].orEmpty()
                    val groupName = window.groupNames[groupId].orEmpty().ifEmpty { "por grupo" }
                    finishedStockRepo.applyMovement(companyId, FinishedStockMovement(
                        productId = line.productId,
                        productName = line.productName,
                        branchId = header.branchId,
                        branchName = header.branchName,
                        type = FinishedMovementType.VENTA,
                        quantity = -sold,
                        unitPriceAtMovement = window.groupUnitPrices[groupId] ?: 0.0,
                        referenceType = FinishedMovementReference.SALE,
                        referenceId = countId,
                        reason = "Venta $groupName repartida en conteo $countId",
                        createdByUserId = currentUserId,
                        createdByUserName = currentUserName,
                        createdAt = now
                    ))
                }
                if (staff > 0.0) {
                    // A costo: es lo que resta el reporte financiero, como los regalos.
                    finishedStockRepo.applyMovement(companyId, FinishedStockMovement(
                        productId = line.productId,
                        productName = line.productName,
                        branchId = header.branchId,
                        branchName = header.branchName,
                        type = FinishedMovementType.PAN_COLABORADOR,
                        quantity = -staff,
                        unitPriceAtMovement = unitCost,
                        referenceType = FinishedMovementReference.COUNTER_REMOVAL,
                        referenceId = countId,
                        reason = "Pan de colaborador repartido en conteo $countId",
                        createdByUserId = currentUserId,
                        createdByUserName = currentUserName,
                        createdAt = now
                    ))
                }
                if (waste > 0.0) {
                    totalShortage += waste * unitCost
                    val reasonLabel = if (line.reason.isNotBlank())
                        BreadCountReason.label(line.reason) else BreadCountReason.label(BreadCountReason.DESCONOCIDO)
                    val reasonText = "Conteo $countId · $reasonLabel" +
                        if (line.reasonNote.isNotBlank()) " · ${line.reasonNote}" else ""
                    finishedStockRepo.applyMovement(companyId, FinishedStockMovement(
                        productId = line.productId,
                        productName = line.productName,
                        branchId = header.branchId,
                        branchName = header.branchName,
                        type = FinishedMovementType.MERMA,
                        quantity = -waste,
                        unitPriceAtMovement = unitCost,
                        referenceType = FinishedMovementReference.WASTAGE,
                        referenceId = countId,
                        reason = reasonText,
                        createdByUserId = currentUserId,
                        createdByUserName = currentUserName,
                        createdAt = now
                    ))
                }
                // El reparto queda escrito en la línea: el reporte de mermas suma solo la
                // merma real, no el pan que se vendió como grupo.
                linesCol(companyId, countId).document(line.id).set(
                    line.copy(
                        difference = diff,
                        valueImpact = diff * unitCost,
                        soldAsGroup = sold,
                        staffBread = staff,
                        wasteQuantity = waste
                    ).toMap()
                )
            } else {
                totalSurplus += absDiff * unitCost
                finishedStockRepo.applyMovement(companyId, FinishedStockMovement(
                    productId = line.productId,
                    productName = line.productName,
                    branchId = header.branchId,
                    branchName = header.branchName,
                    type = FinishedMovementType.AJUSTE,
                    quantity = absDiff,
                    unitPriceAtMovement = unitCost,
                    referenceType = FinishedMovementReference.ADJUSTMENT,
                    referenceId = countId,
                    reason = "Conteo $countId · Sobrante en conteo físico",
                    createdByUserId = currentUserId,
                    createdByUserName = currentUserName,
                    createdAt = now
                ))
            }
        }

        val closed = header.copy(
            status = BreadCountStatus.CLOSED,
            totalShortageValue = totalShortage,
            totalSurplusValue = totalSurplus,
            lineCount = lines.size,
            notes = notes?.trim()?.ifEmpty { null },
            groupSalesAllocated = allocation.totalAllocated,
            unexplainedGroupUnits = allocation.totalUnexplained,
            salesWindowFrom = window.fromMillis,
            salesWindowUntil = window.untilMillis,
            closedAt = now
        )
        countsCol(companyId).document(countId).set(closed.toMap())
        return closed
    }

    override suspend fun aggregateMermasForMonth(
        companyId: String,
        branchId: String,
        yearMonth: String
    ): MermaSummary = coroutineScope {
        val counts = countsCol(companyId).get().documents
            .map { it.toCount() }
            .filter {
                it.status == BreadCountStatus.CLOSED &&
                    it.countDate.startsWith(yearMonth) &&
                    (branchId.isEmpty() || it.branchId == branchId)
            }
        if (counts.isEmpty()) return@coroutineScope MermaSummary()

        val allLines = counts.map { c ->
            async { linesCol(companyId, c.id).get().documents.map { it.toLine() } }
        }.awaitAll().flatten()
            // Merma REAL: lo vendido como grupo de precio también faltó en el conteo, pero no
            // se perdió. Los conteos viejos no traen el reparto y caen al faltante entero.
            .filter { it.effectiveWaste > 0.0 }

        if (allLines.isEmpty()) return@coroutineScope MermaSummary()

        val totalUnits = allLines.sumOf { it.effectiveWaste }
        val totalValue = allLines.sumOf { it.effectiveWaste * it.unitCostSnapshot }
        val byReason = allLines.groupBy { it.reason.ifEmpty { BreadCountReason.DESCONOCIDO } }
            .mapValues { (_, lines) ->
                MermaReasonStat(
                    units = lines.sumOf { it.effectiveWaste },
                    valueImpact = lines.sumOf { it.effectiveWaste * it.unitCostSnapshot }
                )
            }
        MermaSummary(totalUnits = totalUnits, totalValue = totalValue, byReason = byReason)
    }

    // ── Serialización ────────────────────────────────────────────────────────

    private fun BreadCount.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "branchId" to branchId,
        "branchName" to branchName,
        "countDate" to countDate,
        "countedByUserId" to countedByUserId,
        "countedByUserName" to countedByUserName,
        "status" to status,
        "totalShortageValue" to totalShortageValue,
        "totalSurplusValue" to totalSurplusValue,
        "lineCount" to lineCount,
        "notes" to notes,
        "groupSalesAllocated" to groupSalesAllocated,
        "unexplainedGroupUnits" to unexplainedGroupUnits,
        "salesWindowFrom" to salesWindowFrom,
        "salesWindowUntil" to salesWindowUntil,
        "createdAt" to createdAt,
        "closedAt" to closedAt
    )

    private fun DocumentSnapshot.toCount() = BreadCount(
        id = id,
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        countDate = get<String?>("countDate") ?: "",
        countedByUserId = get<String?>("countedByUserId") ?: "",
        countedByUserName = get<String?>("countedByUserName") ?: "",
        status = get<String?>("status") ?: BreadCountStatus.DRAFT,
        totalShortageValue = get<Double?>("totalShortageValue") ?: 0.0,
        totalSurplusValue = get<Double?>("totalSurplusValue") ?: 0.0,
        lineCount = (get<Double?>("lineCount") ?: 0.0).toInt(),
        notes = get<String?>("notes"),
        groupSalesAllocated = get<Double?>("groupSalesAllocated") ?: 0.0,
        unexplainedGroupUnits = get<Double?>("unexplainedGroupUnits") ?: 0.0,
        salesWindowFrom = readMillis("salesWindowFrom"),
        salesWindowUntil = readMillis("salesWindowUntil"),
        createdAt = readMillis("createdAt"),
        closedAt = readMillisNullable("closedAt")
    )

    private fun BreadCountLine.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "productId" to productId,
        "productName" to productName,
        "expectedStock" to expectedStock,
        "countedStock" to countedStock,
        "difference" to difference,
        "reason" to reason,
        "reasonNote" to reasonNote,
        "unitCostSnapshot" to unitCostSnapshot,
        "valueImpact" to valueImpact,
        "soldAsGroup" to soldAsGroup,
        "staffBread" to staffBread,
        "wasteQuantity" to wasteQuantity
    )

    private fun DocumentSnapshot.toLine() = BreadCountLine(
        id = id,
        productId = get<String?>("productId") ?: "",
        productName = get<String?>("productName") ?: "",
        expectedStock = get<Double?>("expectedStock") ?: 0.0,
        countedStock = get<Double?>("countedStock") ?: 0.0,
        difference = get<Double?>("difference") ?: 0.0,
        reason = get<String?>("reason") ?: "",
        reasonNote = get<String?>("reasonNote") ?: "",
        unitCostSnapshot = get<Double?>("unitCostSnapshot") ?: 0.0,
        valueImpact = get<Double?>("valueImpact") ?: 0.0,
        soldAsGroup = get<Double?>("soldAsGroup") ?: 0.0,
        staffBread = runCatching { get<Double?>("staffBread") }.getOrNull()
            ?: runCatching { get<Long?>("staffBread")?.toDouble() }.getOrNull() ?: 0.0,
        wasteQuantity = get<Double?>("wasteQuantity")
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L

    private fun DocumentSnapshot.readMillisNullable(field: String): Long? =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
}
