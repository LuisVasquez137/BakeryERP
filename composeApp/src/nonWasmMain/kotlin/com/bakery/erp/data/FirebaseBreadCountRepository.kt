package com.bakery.erp.data

import com.bakery.erp.domain.BreadCountRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.report.MermaReasonStat
import com.bakery.erp.domain.report.MermaSummary
import com.bakery.erp.model.BreadCount
import com.bakery.erp.model.BreadCountLine
import com.bakery.erp.model.BreadCountReason
import com.bakery.erp.model.BreadCountStatus
import com.bakery.erp.model.FinishedMovementReference
import com.bakery.erp.model.FinishedMovementType
import com.bakery.erp.model.FinishedStockMovement
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
    private val finishedStockRepo: FinishedStockRepository
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
        val now = Clock.System.now().toEpochMilliseconds()

        var totalShortage = 0.0
        var totalSurplus = 0.0

        lines.forEach { line ->
            val diff = line.countedStock - line.expectedStock
            if (diff == 0.0) return@forEach
            val absDiff = abs(diff)
            val unitCost = line.unitCostSnapshot
            if (diff < 0) {
                totalShortage += absDiff * unitCost
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
                    quantity = -absDiff,
                    unitPriceAtMovement = unitCost,
                    referenceType = FinishedMovementReference.WASTAGE,
                    referenceId = countId,
                    reason = reasonText,
                    createdByUserId = currentUserId,
                    createdByUserName = currentUserName,
                    createdAt = now
                ))
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
            .filter { it.difference < 0 }

        if (allLines.isEmpty()) return@coroutineScope MermaSummary()

        val totalUnits = allLines.sumOf { -it.difference }
        val totalValue = allLines.sumOf { -it.valueImpact }
        val byReason = allLines.groupBy { it.reason.ifEmpty { BreadCountReason.DESCONOCIDO } }
            .mapValues { (_, lines) ->
                MermaReasonStat(
                    units = lines.sumOf { -it.difference },
                    valueImpact = lines.sumOf { -it.valueImpact }
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
        "valueImpact" to valueImpact
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
        valueImpact = get<Double?>("valueImpact") ?: 0.0
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L

    private fun DocumentSnapshot.readMillisNullable(field: String): Long? =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
}
