package com.bakery.erp.data

import com.bakery.erp.domain.DeliveryRepository
import com.bakery.erp.domain.DispatchRepository
import com.bakery.erp.model.DeliveryRun
import com.bakery.erp.model.DeliveryRunStatus
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.model.User
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebaseDeliveryRepository(
    private val firestore: FirebaseFirestore,
    private val dispatchRepository: DispatchRepository
) : DeliveryRepository {

    private fun runsCol(companyId: String) =
        firestore.collection("companies/$companyId/deliveryRuns")

    override suspend fun getRun(companyId: String, runId: String): DeliveryRun? {
        val doc = runsCol(companyId).document(runId).get()
        return if (doc.exists) doc.toRun() else null
    }

    override suspend fun getRunsInRange(
        companyId: String, fromMillis: Long, untilMillis: Long
    ): List<DeliveryRun> =
        runsCol(companyId).get().documents
            .map { it.toRun() }
            .filter { it.plannedDate in fromMillis..untilMillis }
            .sortedByDescending { it.plannedDate }

    override fun observeRuns(
        companyId: String, fromMillis: Long, untilMillis: Long
    ): Flow<List<DeliveryRun>> =
        runsCol(companyId).snapshots.map { snapshot ->
            snapshot.documents
                .map { it.toRun() }
                .filter { it.plannedDate in fromMillis..untilMillis }
                .sortedByDescending { it.plannedDate }
        }

    override suspend fun saveRun(
        companyId: String, run: DeliveryRun, noteIds: List<String>, currentUser: User
    ): DeliveryRun {
        val docRef = if (run.id.isEmpty()) runsCol(companyId).document
                     else runsCol(companyId).document(run.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val isNew = run.id.isEmpty()

        // Calcular diff de notas para asignar / desasignar.
        val previousNoteIds = if (isNew) emptySet() else (run.dispatchNoteIds).toSet()
        val newNoteIds = noteIds.toSet()
        val toAssign = newNoteIds - previousNoteIds
        val toUnassign = previousNoteIds - newNoteIds

        val finalRun = run.copy(
            id = docRef.id,
            dispatchNoteIds = noteIds,
            stopCount = noteIds.size,
            createdByUserId = if (isNew) currentUser.id else run.createdByUserId,
            createdByUserName = if (isNew) currentUser.displayName else run.createdByUserName,
            createdAt = if (isNew) now else run.createdAt,
            updatedAt = now
        )
        docRef.set(finalRun.toMap())

        toAssign.forEach { id ->
            runCatching {
                dispatchRepository.markInTransit(companyId, id, currentUser, finalRun.id)
            }
        }
        toUnassign.forEach { id ->
            runCatching { dispatchRepository.unassignFromRun(companyId, id) }
        }

        return finalRun
    }

    override suspend fun startRun(companyId: String, runId: String, currentUser: User) {
        val run = getRun(companyId, runId) ?: return
        if (run.status != DeliveryRunStatus.PENDING) error("La hoja no está pendiente")
        val now = Clock.System.now().toEpochMilliseconds()
        runsCol(companyId).document(runId).set(
            run.copy(
                status = DeliveryRunStatus.IN_PROGRESS,
                startedAt = now,
                updatedAt = now
            ).toMap()
        )
    }

    override suspend fun completeRun(companyId: String, runId: String, currentUser: User) {
        val run = getRun(companyId, runId) ?: return
        if (DeliveryRunStatus.isTerminal(run.status)) return
        val now = Clock.System.now().toEpochMilliseconds()
        runsCol(companyId).document(runId).set(
            run.copy(
                status = DeliveryRunStatus.COMPLETED,
                completedAt = now,
                updatedAt = now
            ).toMap()
        )
    }

    override suspend fun cancelRun(companyId: String, runId: String, currentUser: User) {
        val run = getRun(companyId, runId) ?: return
        if (DeliveryRunStatus.isTerminal(run.status)) return

        // Bloquear cancelación si alguna parada ya tuvo entrega (ARRIVED o DELIVERED).
        val arrivedCount = run.dispatchNoteIds.count { id ->
            val note = dispatchRepository.getNote(companyId, id)
            note != null && DispatchStatus.isDriverDone(note.status)
        }
        if (arrivedCount > 0) {
            error("No se puede cancelar: $arrivedCount parada(s) ya fueron entregadas o recibidas. Las entregas no se pueden revertir.")
        }

        val now = Clock.System.now().toEpochMilliseconds()
        // Desasignar todas las notas (las que aún estén IN_TRANSIT/ARRIVED vuelven a READY).
        run.dispatchNoteIds.forEach { id ->
            runCatching { dispatchRepository.unassignFromRun(companyId, id) }
        }
        runsCol(companyId).document(runId).set(
            run.copy(
                status = DeliveryRunStatus.CANCELLED,
                updatedAt = now
            ).toMap()
        )
    }

    override suspend fun tryAutoCompleteRun(companyId: String, runId: String) {
        if (runId.isEmpty()) return
        val run = getRun(companyId, runId) ?: return
        if (DeliveryRunStatus.isTerminal(run.status)) return
        if (run.status == DeliveryRunStatus.PENDING) return // aún no salió

        // El repartidor cumplió cuando todas las notas están al menos en ARRIVED.
        // El conteo final (DELIVERED) lo hace la encargada después.
        val allDriverDone = run.dispatchNoteIds.all { noteId ->
            val n = dispatchRepository.getNote(companyId, noteId)
            n != null && DispatchStatus.isDriverDone(n.status)
        }
        if (allDriverDone) {
            val now = Clock.System.now().toEpochMilliseconds()
            runsCol(companyId).document(runId).set(
                run.copy(
                    status = DeliveryRunStatus.COMPLETED,
                    completedAt = now,
                    updatedAt = now
                ).toMap()
            )
        }
    }

    // ── Serialización ─────────────────────────────────────────────────────────

    private fun DeliveryRun.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "originBranchId" to originBranchId,
        "originBranchName" to originBranchName,
        "driverUserId" to driverUserId,
        "driverName" to driverName,
        "plannedDate" to plannedDate,
        "status" to status,
        "vehicleAssetId" to vehicleAssetId,
        "vehicleInfo" to vehicleInfo,
        "startedAt" to startedAt,
        "completedAt" to completedAt,
        "dispatchNoteIds" to dispatchNoteIds,
        "stopCount" to stopCount,
        "notes" to notes,
        "createdByUserId" to createdByUserId,
        "createdByUserName" to createdByUserName,
        "createdAt" to if (createdAt == 0L) Clock.System.now().toEpochMilliseconds() else createdAt,
        "updatedAt" to Clock.System.now().toEpochMilliseconds()
    )

    private fun DocumentSnapshot.toRun() = DeliveryRun(
        id = id,
        originBranchId = get<String?>("originBranchId") ?: "",
        originBranchName = get<String?>("originBranchName") ?: "",
        driverUserId = get<String?>("driverUserId") ?: "",
        driverName = get<String?>("driverName") ?: "",
        plannedDate = readMillis("plannedDate"),
        status = get<String?>("status") ?: DeliveryRunStatus.PENDING,
        vehicleAssetId = get<String?>("vehicleAssetId") ?: "",
        vehicleInfo = get<String?>("vehicleInfo"),
        startedAt = readMillisOrNull("startedAt"),
        completedAt = readMillisOrNull("completedAt"),
        dispatchNoteIds = runCatching { get<List<String>?>("dispatchNoteIds") ?: emptyList() }.getOrElse { emptyList() },
        stopCount = (get<Double?>("stopCount") ?: 0.0).toInt(),
        notes = get<String?>("notes"),
        createdByUserId = get<String?>("createdByUserId") ?: "",
        createdByUserName = get<String?>("createdByUserName") ?: "",
        createdAt = readMillis("createdAt"),
        updatedAt = readMillis("updatedAt")
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L

    private fun DocumentSnapshot.readMillisOrNull(field: String): Long? =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
}
