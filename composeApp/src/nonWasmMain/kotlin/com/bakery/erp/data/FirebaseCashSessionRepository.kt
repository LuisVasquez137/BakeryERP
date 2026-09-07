package com.bakery.erp.data

import com.bakery.erp.domain.CashDenominationStage
import com.bakery.erp.domain.CashSessionRepository
import com.bakery.erp.model.CashMovement
import com.bakery.erp.model.CashMovementType
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.CashSessionStatus
import com.bakery.erp.model.CashTransfer
import com.bakery.erp.model.CashDenominations
import com.bakery.erp.model.SalePaymentMethod
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun CashDenominationStage.subcollection(): String = when (this) {
    CashDenominationStage.OPENING -> "openingDenoms"
    CashDenominationStage.CLOSING -> "closingDenoms"
}

class FirebaseCashSessionRepository(
    private val firestore: FirebaseFirestore
) : CashSessionRepository {

    private fun sessionsCol(companyId: String) =
        firestore.collection("companies/$companyId/cashSessions")

    private fun movementsCol(companyId: String, sessionId: String) =
        firestore.collection("companies/$companyId/cashSessions/$sessionId/movements")

    private fun transfersCol(companyId: String) =
        firestore.collection("companies/$companyId/cashTransfers")

    private fun denomsCol(companyId: String, sessionId: String, stage: CashDenominationStage) =
        firestore.collection("companies/$companyId/cashSessions/$sessionId/${stage.subcollection()}")

    override suspend fun openSession(companyId: String, session: CashSession): CashSession {
        val existing = getOpenSessionForRegister(companyId, session.registerId)
        if (existing != null) {
            throw IllegalStateException("La caja ${session.registerName} ya tiene una sesión abierta por ${existing.cashierName}.")
        }
        val docRef = sessionsCol(companyId).document
        val now = Clock.System.now().toEpochMilliseconds()
        val toSave = session.copy(
            id = docRef.id,
            openedAt = if (session.openedAt == 0L) now else session.openedAt,
            status = CashSessionStatus.OPEN
        )
        docRef.set(toSave.toMap())
        return toSave
    }

    override suspend fun getSession(companyId: String, sessionId: String): CashSession? =
        sessionsCol(companyId).document(sessionId).get().takeIf { it.exists }?.toSession()

    override suspend fun getOpenSessionForRegister(
        companyId: String, registerId: String
    ): CashSession? = sessionsCol(companyId).where { "registerId" equalTo registerId }.get().documents
        .map { it.toSession() }
        .firstOrNull { it.status == CashSessionStatus.OPEN }

    override suspend fun getSessionsForRegister(
        companyId: String, registerId: String
    ): List<CashSession> = sessionsCol(companyId).where { "registerId" equalTo registerId }.get().documents
        .map { it.toSession() }
        .sortedByDescending { it.openedAt }

    override fun observeSession(companyId: String, sessionId: String): Flow<CashSession?> =
        sessionsCol(companyId).document(sessionId).snapshots.map { snap ->
            if (snap.exists) snap.toSession() else null
        }

    override suspend fun getSessionsForBranch(
        companyId: String, branchId: String, fromMillis: Long?, untilMillis: Long?
    ): List<CashSession> = sessionsCol(companyId).where { "branchId" equalTo branchId }.get().documents
        .map { it.toSession() }
        .filter { fromMillis == null || it.openedAt >= fromMillis }
        .filter { untilMillis == null || it.openedAt <= untilMillis }
        .sortedByDescending { it.openedAt }

    override fun observeOpenSessions(companyId: String): Flow<List<CashSession>> =
        sessionsCol(companyId).snapshots.map { snapshot ->
            snapshot.documents
                .map { it.toSession() }
                .filter { it.status == CashSessionStatus.OPEN }
                .sortedByDescending { it.openedAt }
        }

    override suspend fun incrementSaleTotals(
        companyId: String, sessionId: String, method: String, amount: Double
    ) {
        val current = getSession(companyId, sessionId) ?: return
        val updated = when (method) {
            SalePaymentMethod.CASH -> current.copy(
                totalCashSales = current.totalCashSales + amount,
                totalSales = current.totalSales + amount,
                saleCount = current.saleCount + 1
            )
            SalePaymentMethod.CARD -> current.copy(
                totalCardSales = current.totalCardSales + amount,
                totalSales = current.totalSales + amount,
                saleCount = current.saleCount + 1
            )
            SalePaymentMethod.TRANSFER -> current.copy(
                totalTransferSales = current.totalTransferSales + amount,
                totalSales = current.totalSales + amount,
                saleCount = current.saleCount + 1
            )
            else -> current.copy(
                totalSales = current.totalSales + amount,
                saleCount = current.saleCount + 1
            )
        }
        sessionsCol(companyId).document(sessionId).set(updated.toMap())
    }

    override suspend fun addMovement(companyId: String, movement: CashMovement) {
        val docRef = movementsCol(companyId, movement.sessionId).document
        val now = Clock.System.now().toEpochMilliseconds()
        val toSave = movement.copy(
            id = docRef.id,
            createdAt = if (movement.createdAt == 0L) now else movement.createdAt
        )
        docRef.set(toSave.toMap())

        // Acumular en denormalizado de la sesión.
        val session = getSession(companyId, movement.sessionId) ?: return
        val sign = CashMovementType.sign(movement.type)
        val updated = when {
            sign > 0 -> session.copy(totalMovementsIn = session.totalMovementsIn + movement.amount)
            sign < 0 -> session.copy(totalMovementsOut = session.totalMovementsOut + movement.amount)
            else     -> session
        }
        sessionsCol(companyId).document(movement.sessionId).set(updated.toMap())
    }

    override suspend fun listMovements(companyId: String, sessionId: String): List<CashMovement> =
        movementsCol(companyId, sessionId).get().documents
            .map { it.toMovement() }
            .sortedBy { it.createdAt }

    override suspend fun closeSession(
        companyId: String, sessionId: String, countedCash: Double, notes: String?
    ): CashSession {
        val current = getSession(companyId, sessionId)
            ?: throw IllegalStateException("Sesión no encontrada: $sessionId")
        if (current.status == CashSessionStatus.CLOSED) return current
        val expected = current.openingBalance + current.totalCashSales +
                       current.totalMovementsIn - current.totalMovementsOut
        val now = Clock.System.now().toEpochMilliseconds()
        val closed = current.copy(
            closedAt = now,
            expectedCash = expected,
            countedCash = countedCash,
            difference = countedCash - expected,
            status = CashSessionStatus.CLOSED,
            notes = notes?.trim()?.ifEmpty { null }
        )
        sessionsCol(companyId).document(sessionId).set(closed.toMap())
        return closed
    }

    override suspend fun saveDenominations(
        companyId: String,
        sessionId: String,
        stage: CashDenominationStage,
        denominations: Map<String, Int>
    ) {
        val col = denomsCol(companyId, sessionId, stage)
        // Borrar todos los docs existentes (reemplazo completo).
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

    override suspend fun getDenominations(
        companyId: String,
        sessionId: String,
        stage: CashDenominationStage
    ): Map<String, Int> = denomsCol(companyId, sessionId, stage).get().documents
        .associate { doc ->
            val code = doc.get<String?>("denomCode") ?: doc.id
            val qty  = (doc.get<Double?>("quantity") ?: 0.0).toInt()
            code to qty
        }

    override suspend fun transferBetweenSessions(
        companyId: String, transfer: CashTransfer
    ): CashTransfer {
        if (transfer.amount <= 0.0) throw IllegalStateException("Monto inválido.")
        if (transfer.fromSessionId == transfer.toSessionId)
            throw IllegalStateException("Caja origen y destino son la misma.")
        val from = getSession(companyId, transfer.fromSessionId)
            ?: throw IllegalStateException("Caja origen no encontrada.")
        val to = getSession(companyId, transfer.toSessionId)
            ?: throw IllegalStateException("Caja destino no encontrada.")
        if (from.status != CashSessionStatus.OPEN || to.status != CashSessionStatus.OPEN)
            throw IllegalStateException("Ambas cajas deben estar abiertas.")
        if (from.branchId != to.branchId)
            throw IllegalStateException("Las cajas deben estar en la misma sucursal.")

        val transferRef = transfersCol(companyId).document
        val now = Clock.System.now().toEpochMilliseconds()
        val finalTransfer = transfer.copy(
            id = transferRef.id,
            branchId = from.branchId,
            fromRegisterName = from.registerName,
            toRegisterName = to.registerName,
            createdAt = if (transfer.createdAt == 0L) now else transfer.createdAt
        )
        transferRef.set(finalTransfer.toMap())

        // Movimiento espejo en la sesión origen (TRANSFER_OUT).
        addMovement(companyId, CashMovement(
            sessionId = finalTransfer.fromSessionId,
            type = CashMovementType.TRANSFER_OUT,
            amount = finalTransfer.amount,
            reason = "Traslado a ${finalTransfer.toRegisterName}" +
                if (finalTransfer.reason.isNotBlank()) " · ${finalTransfer.reason}" else "",
            createdByUserId = finalTransfer.createdByUserId,
            createdByUserName = finalTransfer.createdByUserName,
            createdAt = finalTransfer.createdAt,
            transferId = finalTransfer.id,
            linkedSessionId = finalTransfer.toSessionId
        ))
        // Movimiento espejo en la sesión destino (TRANSFER_IN).
        addMovement(companyId, CashMovement(
            sessionId = finalTransfer.toSessionId,
            type = CashMovementType.TRANSFER_IN,
            amount = finalTransfer.amount,
            reason = "Recibido de ${finalTransfer.fromRegisterName}" +
                if (finalTransfer.reason.isNotBlank()) " · ${finalTransfer.reason}" else "",
            createdByUserId = finalTransfer.createdByUserId,
            createdByUserName = finalTransfer.createdByUserName,
            createdAt = finalTransfer.createdAt,
            transferId = finalTransfer.id,
            linkedSessionId = finalTransfer.fromSessionId
        ))
        return finalTransfer
    }

    override suspend fun getOpenSessionsForBranch(
        companyId: String, branchId: String
    ): List<CashSession> = sessionsCol(companyId).where { "branchId" equalTo branchId }.get().documents
        .map { it.toSession() }
        .filter { it.status == CashSessionStatus.OPEN }
        .sortedBy { it.openedAt }

    override suspend fun applySpotCheckAdjustment(
        companyId: String,
        sessionId: String,
        signedAmount: Double,
        spotCheckId: String,
        currentUserId: String,
        currentUserName: String
    ): String {
        if (signedAmount == 0.0) return ""
        val type = if (signedAmount > 0) CashMovementType.DEPOSIT else CashMovementType.WITHDRAWAL
        val moveRef = movementsCol(companyId, sessionId).document
        val now = Clock.System.now().toEpochMilliseconds()
        val move = CashMovement(
            id = moveRef.id,
            sessionId = sessionId,
            type = type,
            amount = kotlin.math.abs(signedAmount),
            reason = "Ajuste por cuadre sorpresa #$spotCheckId",
            createdByUserId = currentUserId,
            createdByUserName = currentUserName,
            createdAt = now,
            spotCheckId = spotCheckId
        )
        moveRef.set(move.toMap())
        // Acumular en denormalizado de la sesión.
        val session = getSession(companyId, sessionId) ?: return moveRef.id
        val sign = CashMovementType.sign(type)
        val updated = when {
            sign > 0 -> session.copy(totalMovementsIn = session.totalMovementsIn + move.amount)
            sign < 0 -> session.copy(totalMovementsOut = session.totalMovementsOut + move.amount)
            else     -> session
        }
        sessionsCol(companyId).document(sessionId).set(updated.toMap())
        return moveRef.id
    }

    override suspend fun markSessionDailyClose(
        companyId: String, sessionId: String, dailyCloseId: String?
    ) {
        val current = getSession(companyId, sessionId) ?: return
        sessionsCol(companyId).document(sessionId).set(
            current.copy(dailyCloseId = dailyCloseId).toMap()
        )
    }

    private fun CashTransfer.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "branchId" to branchId,
        "fromSessionId" to fromSessionId,
        "fromRegisterName" to fromRegisterName,
        "toSessionId" to toSessionId,
        "toRegisterName" to toRegisterName,
        "amount" to amount,
        "reason" to reason,
        "createdByUserId" to createdByUserId,
        "createdByUserName" to createdByUserName,
        "createdAt" to createdAt
    )

    // ── Serialización ────────────────────────────────────────────────────────

    private fun CashSession.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "branchId" to branchId,
        "branchName" to branchName,
        "registerId" to registerId,
        "registerName" to registerName,
        "cashierId" to cashierId,
        "cashierName" to cashierName,
        "openedAt" to openedAt,
        "closedAt" to closedAt,
        "openingBalance" to openingBalance,
        "totalCashSales" to totalCashSales,
        "totalCardSales" to totalCardSales,
        "totalTransferSales" to totalTransferSales,
        "totalSales" to totalSales,
        "totalMovementsIn" to totalMovementsIn,
        "totalMovementsOut" to totalMovementsOut,
        "expectedCash" to expectedCash,
        "countedCash" to countedCash,
        "difference" to difference,
        "status" to status,
        "saleCount" to saleCount,
        "notes" to notes,
        "dailyCloseId" to dailyCloseId
    )

    private fun DocumentSnapshot.toSession() = CashSession(
        id = id,
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        registerId = get<String?>("registerId") ?: "",
        registerName = get<String?>("registerName") ?: "",
        cashierId = get<String?>("cashierId") ?: "",
        cashierName = get<String?>("cashierName") ?: "",
        openedAt = readMillis("openedAt"),
        closedAt = readMillisNullable("closedAt"),
        openingBalance = get<Double?>("openingBalance") ?: 0.0,
        totalCashSales = get<Double?>("totalCashSales") ?: 0.0,
        totalCardSales = get<Double?>("totalCardSales") ?: 0.0,
        totalTransferSales = get<Double?>("totalTransferSales") ?: 0.0,
        totalSales = get<Double?>("totalSales") ?: 0.0,
        totalMovementsIn = get<Double?>("totalMovementsIn") ?: 0.0,
        totalMovementsOut = get<Double?>("totalMovementsOut") ?: 0.0,
        expectedCash = get<Double?>("expectedCash") ?: 0.0,
        countedCash = get<Double?>("countedCash") ?: 0.0,
        difference = get<Double?>("difference") ?: 0.0,
        status = get<String?>("status") ?: CashSessionStatus.OPEN,
        saleCount = (get<Double?>("saleCount") ?: 0.0).toInt(),
        notes = get<String?>("notes"),
        dailyCloseId = get<String?>("dailyCloseId")
    )

    private fun CashMovement.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "sessionId" to sessionId,
        "type" to type,
        "amount" to amount,
        "reason" to reason,
        "createdByUserId" to createdByUserId,
        "createdByUserName" to createdByUserName,
        "createdAt" to createdAt,
        "transferId" to transferId,
        "linkedSessionId" to linkedSessionId,
        "spotCheckId" to spotCheckId
    )

    private fun DocumentSnapshot.toMovement() = CashMovement(
        id = id,
        sessionId = get<String?>("sessionId") ?: "",
        type = get<String?>("type") ?: CashMovementType.WITHDRAWAL,
        amount = get<Double?>("amount") ?: 0.0,
        reason = get<String?>("reason") ?: "",
        createdByUserId = get<String?>("createdByUserId") ?: "",
        createdByUserName = get<String?>("createdByUserName") ?: "",
        createdAt = readMillis("createdAt"),
        transferId = get<String?>("transferId"),
        linkedSessionId = get<String?>("linkedSessionId"),
        spotCheckId = get<String?>("spotCheckId")
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L

    private fun DocumentSnapshot.readMillisNullable(field: String): Long? =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
}
