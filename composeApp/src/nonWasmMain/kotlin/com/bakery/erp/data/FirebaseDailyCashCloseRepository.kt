package com.bakery.erp.data

import com.bakery.erp.domain.CashSessionRepository
import com.bakery.erp.domain.DailyCashCloseDraft
import com.bakery.erp.domain.DailyCashCloseRepository
import com.bakery.erp.model.CashDenominations
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.CashSessionStatus
import com.bakery.erp.model.DailyCashClose
import com.bakery.erp.model.DailyCashCloseStatus
import com.bakery.erp.model.dayCashTotals
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class FirebaseDailyCashCloseRepository(
    private val firestore: FirebaseFirestore,
    private val cashSessionRepo: CashSessionRepository
) : DailyCashCloseRepository {

    private fun closesCol(companyId: String) =
        firestore.collection("companies/$companyId/dailyCashCloses")

    private fun leftDenomsCol(companyId: String, closeId: String) =
        firestore.collection("companies/$companyId/dailyCashCloses/$closeId/leftDenoms")

    private fun depositDenomsCol(companyId: String, closeId: String) =
        firestore.collection("companies/$companyId/dailyCashCloses/$closeId/depositDenoms")

    override suspend fun buildDraft(
        companyId: String, branchId: String, branchName: String, closeDate: String
    ): DailyCashCloseDraft {
        val sessions = cashSessionRepo.getSessionsForBranch(companyId, branchId)
            .filter { it.matchesLocalDate(closeDate) }
        val closed = sessions.filter { it.status == CashSessionStatus.CLOSED }
        val hasOpen = sessions.any { it.status == CashSessionStatus.OPEN }
        // Lo contado y lo esperado salen del último turno de cada caja: sumarlos todos contaba
        // dos veces el efectivo que pasó de un turno al siguiente.
        val cash = dayCashTotals(closed, sessions)
        return DailyCashCloseDraft(
            branchId = branchId,
            branchName = branchName,
            closeDate = closeDate,
            sessions = closed.sortedBy { it.openedAt },
            totalCashSales = closed.sumOf { it.totalCashSales },
            totalCardSales = closed.sumOf { it.totalCardSales },
            totalTransferSales = closed.sumOf { it.totalTransferSales },
            totalPedidosYaSales = closed.sumOf { it.totalPedidosYaSales },
            totalSales = closed.sumOf { it.totalSales },
            totalMovementsIn = closed.sumOf { it.totalMovementsIn },
            totalMovementsOut = closed.sumOf { it.totalMovementsOut },
            totalExpectedCash = cash.expected,
            totalCountedCash = cash.counted,
            totalDifference = cash.difference,
            takenAtShiftClose = cash.takenAtShiftClose,
            hasOpenSessions = hasOpen,
            openSessions = sessions.filter { it.status == CashSessionStatus.OPEN }.sortedBy { it.openedAt }
        )
    }

    override suspend fun getClose(companyId: String, closeId: String): DailyCashClose? =
        closesCol(companyId).document(closeId).get().takeIf { it.exists }?.toClose()

    override suspend fun getCloseForDate(
        companyId: String, branchId: String, closeDate: String
    ): DailyCashClose? = closesCol(companyId).where { "branchId" equalTo branchId }.get().documents
        .map { it.toClose() }
        .firstOrNull { it.closeDate == closeDate }

    override fun observeCloseForDate(
        companyId: String, branchId: String, closeDate: String
    ): Flow<DailyCashClose?> = closesCol(companyId).snapshots.map { snap ->
        snap.documents
            .map { it.toClose() }
            .firstOrNull { it.branchId == branchId && it.closeDate == closeDate }
    }

    override suspend fun getClosesForMonth(
        companyId: String, branchId: String, yearMonth: String
    ): List<DailyCashClose> = closesCol(companyId).where { "branchId" equalTo branchId }.get().documents
        .map { it.toClose() }
        .filter { it.closeDate.startsWith(yearMonth) }
        .sortedByDescending { it.closeDate }

    override suspend fun getLeftDenominations(companyId: String, closeId: String): Map<String, Int> =
        leftDenomsCol(companyId, closeId).get().documents.associate { doc ->
            val code = doc.get<String?>("denomCode") ?: doc.id
            val qty  = (doc.get<Double?>("quantity") ?: 0.0).toInt()
            code to qty
        }

    override suspend fun getDepositDenominations(companyId: String, closeId: String): Map<String, Int> =
        depositDenomsCol(companyId, closeId).get().documents.associate { doc ->
            val code = doc.get<String?>("denomCode") ?: doc.id
            val qty  = (doc.get<Double?>("quantity") ?: 0.0).toInt()
            code to qty
        }

    override suspend fun closeDay(
        companyId: String,
        draft: DailyCashCloseDraft,
        leftDenominations: Map<String, Int>,
        depositDenominations: Map<String, Int>,
        notes: String?,
        currentUserId: String,
        currentUserName: String
    ): DailyCashClose {
        if (draft.hasOpenSessions) {
            throw IllegalStateException("Hay sesiones de caja abiertas en esta sucursal. Ciérralas antes del cierre del día.")
        }
        val existing = getCloseForDate(companyId, draft.branchId, draft.closeDate)
        if (existing != null && existing.status == DailyCashCloseStatus.CLOSED) {
            throw IllegalStateException("El día ya fue cerrado para esta sucursal.")
        }
        val leftTotal = leftDenominations.entries.sumOf { (code, qty) ->
            (CashDenominations.byCode(code)?.value ?: 0.0) * qty
        }
        val depositTotal = depositDenominations.entries.sumOf { (code, qty) ->
            (CashDenominations.byCode(code)?.value ?: 0.0) * qty
        }
        val tolerance = 0.05
        val sum = leftTotal + depositTotal
        if (kotlin.math.abs(sum - draft.totalCountedCash) > tolerance) {
            throw IllegalStateException(
                "El depósito (${depositTotal}) + lo que queda para mañana (${leftTotal}) " +
                "no cuadra con el efectivo en gavetas (${draft.totalCountedCash})."
            )
        }
        val docRef = (existing?.let { closesCol(companyId).document(it.id) }
            ?: closesCol(companyId).document)
        val now = Clock.System.now().toEpochMilliseconds()
        val close = DailyCashClose(
            id = docRef.id,
            branchId = draft.branchId,
            branchName = draft.branchName,
            closeDate = draft.closeDate,
            sessionIds = draft.sessions.map { it.id },
            totalCashSales = draft.totalCashSales,
            totalCardSales = draft.totalCardSales,
            totalTransferSales = draft.totalTransferSales,
            totalPedidosYaSales = draft.totalPedidosYaSales,
            totalSales = draft.totalSales,
            totalMovementsIn = draft.totalMovementsIn,
            totalMovementsOut = draft.totalMovementsOut,
            totalExpectedCash = draft.totalExpectedCash,
            totalCountedCash = draft.totalCountedCash,
            totalDifference = draft.totalDifference,
            leftForNextDay = leftTotal,
            depositedAmount = depositTotal,
            status = DailyCashCloseStatus.CLOSED,
            closedByUserId = currentUserId,
            closedByUserName = currentUserName,
            closedAt = now,
            notes = notes?.trim()?.ifEmpty { null }
        )
        docRef.set(close.toMap())
        // Reemplaza subcolecciones
        replaceDenominations(leftDenomsCol(companyId, close.id), leftDenominations)
        replaceDenominations(depositDenomsCol(companyId, close.id), depositDenominations)
        // Marca sesiones incluidas
        draft.sessions.forEach { s ->
            cashSessionRepo.markSessionDailyClose(companyId, s.id, close.id)
        }
        return close
    }

    private suspend fun replaceDenominations(
        col: dev.gitlive.firebase.firestore.CollectionReference,
        denominations: Map<String, Int>
    ) {
        col.get().documents.forEach { it.reference.delete() }
        denominations.filter { it.value > 0 }.forEach { (code, qty) ->
            val denom = CashDenominations.byCode(code) ?: return@forEach
            col.document(code).set(firestoreSafeOf(
                "denomCode" to code,
                "quantity" to qty,
                "subtotal" to qty * denom.value
            ))
        }
    }

    private fun CashSession.matchesLocalDate(yyyyMmDd: String): Boolean {
        val ms = openedAt
        if (ms == 0L) return false
        val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault()).date
        val formatted = "${dt.year.toString().padStart(4, '0')}-" +
            "${dt.monthNumber.toString().padStart(2, '0')}-" +
            "${dt.dayOfMonth.toString().padStart(2, '0')}"
        return formatted == yyyyMmDd
    }

    private fun DailyCashClose.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "branchId" to branchId,
        "branchName" to branchName,
        "closeDate" to closeDate,
        "sessionIds" to sessionIds,
        "totalCashSales" to totalCashSales,
        "totalCardSales" to totalCardSales,
        "totalTransferSales" to totalTransferSales,
        "totalPedidosYaSales" to totalPedidosYaSales,
        "totalSales" to totalSales,
        "totalMovementsIn" to totalMovementsIn,
        "totalMovementsOut" to totalMovementsOut,
        "totalExpectedCash" to totalExpectedCash,
        "totalCountedCash" to totalCountedCash,
        "totalDifference" to totalDifference,
        "leftForNextDay" to leftForNextDay,
        "depositedAmount" to depositedAmount,
        "status" to status,
        "closedByUserId" to closedByUserId,
        "closedByUserName" to closedByUserName,
        "closedAt" to closedAt,
        "notes" to notes
    )

    private fun DocumentSnapshot.toClose() = DailyCashClose(
        id = id,
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        closeDate = get<String?>("closeDate") ?: "",
        sessionIds = runCatching { get<List<String>?>("sessionIds") }.getOrNull().orEmpty(),
        totalCashSales = get<Double?>("totalCashSales") ?: 0.0,
        totalCardSales = get<Double?>("totalCardSales") ?: 0.0,
        totalTransferSales = get<Double?>("totalTransferSales") ?: 0.0,
        totalPedidosYaSales = get<Double?>("totalPedidosYaSales") ?: 0.0,
        totalSales = get<Double?>("totalSales") ?: 0.0,
        totalMovementsIn = get<Double?>("totalMovementsIn") ?: 0.0,
        totalMovementsOut = get<Double?>("totalMovementsOut") ?: 0.0,
        totalExpectedCash = get<Double?>("totalExpectedCash") ?: 0.0,
        totalCountedCash = get<Double?>("totalCountedCash") ?: 0.0,
        totalDifference = get<Double?>("totalDifference") ?: 0.0,
        leftForNextDay = get<Double?>("leftForNextDay") ?: 0.0,
        depositedAmount = get<Double?>("depositedAmount") ?: 0.0,
        status = get<String?>("status") ?: DailyCashCloseStatus.OPEN,
        closedByUserId = get<String?>("closedByUserId") ?: "",
        closedByUserName = get<String?>("closedByUserName") ?: "",
        closedAt = readMillisNullable("closedAt"),
        notes = get<String?>("notes")
    )

    private fun DocumentSnapshot.readMillisNullable(field: String): Long? =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
}
