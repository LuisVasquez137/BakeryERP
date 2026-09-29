package com.bakery.erp.data

import com.bakery.erp.domain.CashAuditRepository
import com.bakery.erp.model.CashAuditEvent
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.where
import kotlin.time.Clock

class FirebaseCashAuditRepository(
    private val firestore: FirebaseFirestore
) : CashAuditRepository {

    private fun col(companyId: String) = firestore.collection("companies/$companyId/cashAuditEvents")

    override suspend fun log(companyId: String, event: CashAuditEvent) {
        val ref = col(companyId).document
        val e = event.copy(id = ref.id, at = if (event.at > 0) event.at else Clock.System.now().toEpochMilliseconds())
        ref.set(firestoreSafeOf(
            "id" to e.id,
            "type" to e.type,
            "at" to e.at,
            "branchId" to e.branchId,
            "branchName" to e.branchName,
            "registerId" to e.registerId,
            "registerName" to e.registerName,
            "sessionId" to e.sessionId,
            "userId" to e.userId,
            "userName" to e.userName,
            "employeeName" to e.employeeName,
            "outOfSchedule" to e.outOfSchedule,
            "scheduleLabel" to e.scheduleLabel,
            "expectedCash" to e.expectedCash,
            "countedCash" to e.countedCash,
            "difference" to e.difference,
            "notes" to e.notes
        ))
    }

    // Igualdad por sucursal (índice automático) y el rango de fechas en memoria: una sucursal
    // genera unos pocos eventos al día, y así no hace falta declarar un índice compuesto.
    override suspend fun getEvents(companyId: String, branchId: String, fromMillis: Long, untilMillis: Long): List<CashAuditEvent> =
        col(companyId).where { "branchId" equalTo branchId }.get().documents
            .map { it.toEvent() }
            .filter { it.at in fromMillis until untilMillis }
            .sortedByDescending { it.at }

    private fun DocumentSnapshot.toEvent(): CashAuditEvent {
        fun str(f: String) = runCatching { get<String?>(f) }.getOrNull().orEmpty()
        fun num(f: String) = runCatching { get<Double?>(f) }.getOrNull()
            ?: runCatching { get<Long?>(f)?.toDouble() }.getOrNull() ?: 0.0
        return CashAuditEvent(
            id = id,
            type = str("type"),
            at = num("at").toLong(),
            branchId = str("branchId"),
            branchName = str("branchName"),
            registerId = str("registerId"),
            registerName = str("registerName"),
            sessionId = str("sessionId"),
            userId = str("userId"),
            userName = str("userName"),
            employeeName = str("employeeName"),
            outOfSchedule = runCatching { get<Boolean?>("outOfSchedule") }.getOrNull() ?: false,
            scheduleLabel = str("scheduleLabel"),
            expectedCash = num("expectedCash"),
            countedCash = num("countedCash"),
            difference = num("difference"),
            notes = str("notes")
        )
    }
}
