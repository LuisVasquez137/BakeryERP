package com.bakery.erp.data

import com.bakery.erp.domain.DispatchRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.PackingCloseResult
import com.bakery.erp.domain.PriceListRepository
import com.bakery.erp.domain.PriceSource
import com.bakery.erp.domain.ResolvedPrice
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.domain.report.DiscrepancyStat
import com.bakery.erp.domain.report.DispatchBranchStat
import com.bakery.erp.domain.report.DispatchSummary
import com.bakery.erp.model.DiscrepancyType
import com.bakery.erp.model.DispatchNote
import com.bakery.erp.model.DispatchNoteLine
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.model.FinishedMovementReference
import com.bakery.erp.model.FinishedMovementType
import com.bakery.erp.model.FinishedStockMovement
import com.bakery.erp.model.PriceSourceTag
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.User
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class FirebaseDispatchRepository(
    private val firestore: FirebaseFirestore,
    private val productionRepository: ProductionRepository,
    private val priceListRepository: PriceListRepository,
    private val finishedStockRepository: FinishedStockRepository
) : DispatchRepository {

    private fun notesCol(companyId: String) =
        firestore.collection("companies/$companyId/dispatchNotes")

    private fun linesCol(companyId: String, noteId: String) =
        firestore.collection("companies/$companyId/dispatchNotes/$noteId/lines")

    // ── Lecturas ──────────────────────────────────────────────────────────────

    override suspend fun getNote(companyId: String, noteId: String): DispatchNote? {
        val doc = notesCol(companyId).document(noteId).get()
        if (!doc.exists) return null
        val header = doc.toNote()
        val lines = linesCol(companyId, noteId).get().documents.map { it.toLine() }
        return header.copy(lines = lines, lineCount = lines.size)
    }

    override suspend fun getNotesInRange(
        companyId: String, fromMillis: Long, untilMillis: Long
    ): List<DispatchNote> =
        notesCol(companyId).get().documents
            .map { it.toNote() }
            .filter { it.productionDate in fromMillis..untilMillis }
            .sortedByDescending { it.productionDate }

    override fun observeNotes(
        companyId: String, fromMillis: Long, untilMillis: Long
    ): Flow<List<DispatchNote>> =
        notesCol(companyId).snapshots.map { snapshot ->
            snapshot.documents
                .map { it.toNote() }
                .filter { it.productionDate in fromMillis..untilMillis }
                .sortedByDescending { it.productionDate }
        }

    // ── Creación automática desde lote completado ─────────────────────────────

    override suspend fun createDispatchNotesForLot(
        companyId: String, lot: ProductionLot, lines: List<ProductionLotLine>, currentUser: User
    ): List<DispatchNote> {
        if (lot.sourceOrderIds.isEmpty()) return emptyList()
        // Idempotencia: no duplicar notas si ya existen para este lote.
        val existing = notesCol(companyId).where { "lotId" equalTo lot.id }.get().documents
            .map { it.toNote() }
        val existingByOrder = existing.associateBy { it.sourceOrderId }
        val lotLinesByProduct = lines.associateBy { it.productId }
        // Lote nacido de un reparto por turnos: dos lotes comparten sourceOrderIds y
        // cada uno produce solo una porción de cada pedido, así que hay que despachar
        // el desglose de la línea y no la cantidad pedida completa.
        //
        // El dato vive en el lote (`isShiftSplit`). El `any { ... }` es solo el
        // fallback para lotes repartidos ANTES de que existiera el flag: sin él
        // esos lotes caerían al camino de lote normal y cada turno despacharía el
        // pedido entero. Nunca al revés — un lote marcado como repartido cuyas
        // líneas perdieron el desglose debe despachar 0, no duplicar.
        val isSplitLot = lot.isShiftSplit || lines.any { it.sourceOrderAllocations.isNotEmpty() }

        val created = mutableListOf<DispatchNote>()
        for (orderId in lot.sourceOrderIds) {
            if (existingByOrder.containsKey(orderId)) continue
            val order = productionRepository.getOrder(companyId, orderId) ?: continue
            val orderLines = productionRepository.getOrderLines(companyId, orderId)
            if (orderLines.isEmpty()) continue

            // Cantidad que le corresponde a ESTA orden en ESTE lote/turno:
            // - Línea con desglose (sourceOrderAllocations no vacío): usar la entrada de
            //   esta orden, o 0 si no aparece (a este turno no le tocó nada del pedido de
            //   esta sucursal para este producto — puede estar 100% en el turno hermano).
            // - Lote de reparto por turnos pero sin línea para este producto (excluido de
            //   este turno por completo, ver `ProductionShift.excludedProductIds`): 0.
            // - Lote normal (sin reparto por turnos): el pedido completo, como siempre.
            fun dispatchQuantityFor(productId: String, requestedQuantity: Double): Double {
                val allocations = lotLinesByProduct[productId]?.sourceOrderAllocations
                return when {
                    !allocations.isNullOrEmpty() -> allocations.find { it.orderId == orderId }?.quantity ?: 0.0
                    isSplitLot -> 0.0
                    else -> requestedQuantity
                }
            }
            val orderLinesForNote = orderLines.map { line ->
                line.copy(requestedQuantity = dispatchQuantityFor(line.productId, line.requestedQuantity))
            }.filter { it.requestedQuantity > 0.0 }
            if (orderLinesForNote.isEmpty()) continue

            val noteRef = notesCol(companyId).document
            val now = Clock.System.now().toEpochMilliseconds()

            // Resolver precios por producto. Todas las líneas comparten la misma lista
            // (resuelta a partir de la sucursal solicitante).
            // Los pedidos especiales no están en ninguna lista de precios (no son
            // productos de catálogo): no se les resuelve precio, llevan el que
            // declaró la encargada al pedirlos —o 0— y NO cuentan para
            // `hasPriceMissing`. Si contaran, un encargo de 5 panes dejaría toda
            // la nota atorada en DRAFT y con ella el reparto del día.
            val resolutions: List<Pair<com.bakery.erp.model.ProductionOrderLine, ResolvedPrice?>> =
                orderLinesForNote.map { line ->
                    if (line.isSpecial) line to null
                    else line to priceListRepository.resolveWholesalePrice(
                        companyId, order.sellingBranchId, line.productId
                    )
                }
            val firstResolved = resolutions.firstNotNullOfOrNull { it.second }
            val listId = firstResolved?.priceListId.orEmpty()
            val listName = firstResolved?.priceListName.orEmpty()
            val hasPriceMissing = resolutions.any { (_, resolved) ->
                resolved != null && !resolved.found
            }

            val note = DispatchNote(
                id = noteRef.id,
                lotId = lot.id,
                sourceOrderId = orderId,
                originBranchId = lot.producingBranchId,
                originBranchName = lot.producingBranchName,
                destinationBranchId = order.sellingBranchId,
                destinationBranchName = order.sellingBranchName,
                productionDate = lot.productionDate,
                shiftId = lot.shiftId,
                shiftName = lot.shiftName,
                status = DispatchStatus.DRAFT,
                priceListId = listId,
                priceListName = listName,
                hasPriceMissing = hasPriceMissing,
                lineCount = orderLinesForNote.size,
                createdAt = now,
                updatedAt = now
            )
            noteRef.set(note.toMap())

            resolutions.forEach { (line, resolved) ->
                val lineRef = linesCol(companyId, noteRef.id).document
                val sourceTag = when {
                    resolved == null             -> PriceSourceTag.SPECIAL_ORDER
                    resolved.source == PriceSource.PRODUCT_OVERRIDE -> PriceSourceTag.PRODUCT_OVERRIDE
                    resolved.source == PriceSource.LIST_FACTOR      -> PriceSourceTag.LIST_FACTOR
                    else                         -> PriceSourceTag.NONE
                }
                val dispatchLine = DispatchNoteLine(
                    id = lineRef.id,
                    productId = line.productId,
                    productName = line.productName,
                    productUnit = line.productUnit,
                    requestedQuantity = line.requestedQuantity,
                    packedQuantity = line.requestedQuantity, // pre-llenado con lo pedido
                    unitWholesalePrice = resolved?.unitPrice ?: line.specialUnitPrice,
                    priceSource = sourceTag,
                    isSpecial = line.isSpecial,
                    specialRawWeightGrams = line.specialRawWeightGrams,
                    specialRecipeId = line.specialRecipeId,
                    specialRecipeName = line.specialRecipeName
                )
                lineRef.set(dispatchLine.toMap())
            }
            created.add(
                note.copy(
                    lines = resolutions.map { (line, resolved) ->
                        line.toDispatchLine(resolved?.unitPrice ?: line.specialUnitPrice)
                    }
                )
            )
        }
        return created
    }

    private fun com.bakery.erp.model.ProductionOrderLine.toDispatchLine(unitPrice: Double) =
        DispatchNoteLine(
            productId = productId,
            productName = productName,
            productUnit = productUnit,
            requestedQuantity = requestedQuantity,
            packedQuantity = requestedQuantity,
            unitWholesalePrice = unitPrice,
            isSpecial = isSpecial,
            specialRawWeightGrams = specialRawWeightGrams,
            specialRecipeId = specialRecipeId,
            specialRecipeName = specialRecipeName
        )

    // ── Empaque ───────────────────────────────────────────────────────────────

    override suspend fun savePackedLines(
        companyId: String, noteId: String, lines: List<DispatchNoteLine>, currentUser: User
    ) {
        val note = getNoteHeader(companyId, noteId) ?: return
        if (note.status != DispatchStatus.DRAFT) error("Solo se pueden editar notas en estado DRAFT")
        val now = Clock.System.now().toEpochMilliseconds()
        lines.forEach { line ->
            if (line.id.isNotEmpty()) {
                linesCol(companyId, noteId).document(line.id).set(line.toMap())
            }
        }
        notesCol(companyId).document(noteId).set(
            note.copy(
                packedByUserId = currentUser.id,
                packedByUserName = currentUser.displayName,
                updatedAt = now
            ).toMap()
        )
    }

    override suspend fun setLineChecked(
        companyId: String, noteId: String, lineId: String, checkedAtMillis: Long?
    ) {
        val note = getNoteHeader(companyId, noteId) ?: return
        if (note.status != DispatchStatus.DRAFT)
            error("Solo se puede marcar/desmarcar líneas en notas DRAFT")
        // Update puntual — no se reescribe la línea entera, sólo el campo lineCheckedAt
        // (firestoreSafe convierte Long->Double para Web).
        linesCol(companyId, noteId).document(lineId).update(
            mapOf("lineCheckedAt" to checkedAtMillis).firestoreSafe()
        )
        // Bump updatedAt en la nota para que el listener vivo refresque a otros usuarios.
        notesCol(companyId).document(noteId).update(
            mapOf("updatedAt" to Clock.System.now().toEpochMilliseconds()).firestoreSafe()
        )
    }

    override suspend fun setLinePackedQuantity(
        companyId: String, noteId: String, lineId: String, qty: Double, reason: String?
    ) {
        val note = getNoteHeader(companyId, noteId) ?: return
        if (note.status != DispatchStatus.DRAFT)
            error("Solo se puede editar cantidades en notas DRAFT")
        linesCol(companyId, noteId).document(lineId).update(
            mapOf(
                "packedQuantity" to qty,
                "packedReason" to reason?.takeIf { it.isNotBlank() }
            ).firestoreSafe()
        )
        notesCol(companyId).document(noteId).update(
            mapOf("updatedAt" to Clock.System.now().toEpochMilliseconds()).firestoreSafe()
        )
    }

    override fun observePackingViewForBranchDateShift(
        companyId: String,
        producingBranchId: String,
        dayMillis: Long,
        shiftId: String
    ): Flow<List<DispatchNote>> {
        val (from, until) = calendarDayBounds(dayMillis)
        return notesCol(companyId).snapshots.map { snapshot ->
            snapshot.documents
                .map { it.toNote() }
                .filter {
                    it.status == DispatchStatus.DRAFT &&
                        it.originBranchId == producingBranchId &&
                        it.shiftId == shiftId &&
                        it.productionDate in from..until
                }
                .sortedBy { it.destinationBranchName }
        }
    }

    override suspend fun closePackingForBranchDateShift(
        companyId: String,
        producingBranchId: String,
        dayMillis: Long,
        shiftId: String,
        currentUser: User
    ): PackingCloseResult {
        val (from, until) = calendarDayBounds(dayMillis)
        val notes = notesCol(companyId).get().documents
            .map { it.toNote() }
            .filter {
                it.status == DispatchStatus.DRAFT &&
                    it.originBranchId == producingBranchId &&
                    it.shiftId == shiftId &&
                    it.productionDate in from..until
            }
        var promoted = 0
        var pendingChecks = 0
        var pendingPrices = 0
        var deliveredLocally = 0
        for (note in notes) {
            val lines = linesCol(companyId, note.id).get().documents.map { it.toLine() }
            if (lines.isEmpty()) continue
            val allChecked = lines.all { it.lineCheckedAt != null }
            val hasUnpriced = lines.any { it.priceSource == PriceSourceTag.NONE && it.packedQuantity > 0 }
            when {
                !allChecked -> pendingChecks++
                hasUnpriced -> pendingPrices++
                else -> {
                    runCatching { closeNoteAfterPacking(companyId, note.id, currentUser) }
                        .onSuccess { local -> promoted++; if (local) deliveredLocally++ }
                        .onFailure { pendingPrices++ }
                }
            }
        }
        return PackingCloseResult(promoted, pendingChecks, pendingPrices, deliveredLocally)
    }

    override suspend fun closeNoteAfterPacking(
        companyId: String,
        noteId: String,
        currentUser: User
    ): Boolean {
        val note = getNoteHeader(companyId, noteId) ?: return false
        markReady(companyId, noteId, currentUser)
        // El pan de la propia planta no viaja: el conteo del empaque ES la recepción.
        if (note.originBranchId != note.destinationBranchId) return false
        markAsLocalDispatch(companyId, noteId, currentUser)
        return true
    }

    override suspend fun markReady(companyId: String, noteId: String, currentUser: User) {
        val note = getNoteHeader(companyId, noteId) ?: return
        if (note.status != DispatchStatus.DRAFT) error("La nota no está en estado DRAFT")
        val lines = linesCol(companyId, noteId).get().documents.map { it.toLine() }
        if (lines.any { it.priceSource == PriceSourceTag.NONE && it.packedQuantity > 0 }) {
            error("Hay líneas sin precio definido. Configura el precio en la lista o un override en el producto antes de cerrar la nota.")
        }
        val total = lines.sumOf { it.packedQuantity * it.unitWholesalePrice }
        val now = Clock.System.now().toEpochMilliseconds()
        notesCol(companyId).document(noteId).set(
            note.copy(
                status = DispatchStatus.READY,
                totalValueWholesale = total,
                hasPriceMissing = false,
                packedByUserId = currentUser.id,
                packedByUserName = currentUser.displayName,
                packedAt = now,
                updatedAt = now
            ).toMap()
        )
    }

    override suspend fun markInTransit(
        companyId: String, noteId: String, currentUser: User, deliveryRunId: String
    ) {
        val note = getNoteHeader(companyId, noteId) ?: return
        if (note.status != DispatchStatus.READY) error("La nota no está lista para enviar")
        val now = Clock.System.now().toEpochMilliseconds()
        notesCol(companyId).document(noteId).set(
            note.copy(
                status = DispatchStatus.IN_TRANSIT,
                dispatchedAt = now,
                deliveryRunId = deliveryRunId,
                updatedAt = now
            ).toMap()
        )
    }

    override suspend fun getNotesForRunForm(
        companyId: String,
        originBranchId: String,
        dayMillis: Long,
        currentRunId: String
    ): List<DispatchNote> {
        val (from, until) = calendarDayBounds(dayMillis)
        return notesCol(companyId).where { "originBranchId" equalTo originBranchId }.get()
            .documents
            .map { it.toNote() }
            .filter { it.productionDate in from..until }
            .filter {
                it.status == DispatchStatus.READY ||
                    (it.status == DispatchStatus.IN_TRANSIT && it.deliveryRunId == currentRunId)
            }
            // El pan de la propia planta no sube al camión: se queda en su mostrador.
            // Ofrecerlo como parada era una invitación a un viaje que no existe, y la
            // nota quedaba IN_TRANSIT para siempre esperando un acuse de llegada que
            // nadie iba a firmar — de paso trabando el auto-cierre de la hoja, que
            // exige que TODAS las paradas estén entregadas. Sigue apareciendo si ya
            // está en ESTA hoja, para que se pueda quitar de ahí.
            .filter {
                it.originBranchId != it.destinationBranchId ||
                    (it.status == DispatchStatus.IN_TRANSIT && it.deliveryRunId == currentRunId)
            }
            .sortedBy { it.destinationBranchName }
    }

    override suspend fun unassignFromRun(companyId: String, noteId: String) {
        val note = getNoteHeader(companyId, noteId) ?: return
        // Solo se puede desasignar si aún no se recibió. ARRIVED no se permite revertir
        // porque ya hay un acuse de llegada en la sucursal.
        if (note.status != DispatchStatus.IN_TRANSIT) return
        val now = Clock.System.now().toEpochMilliseconds()
        notesCol(companyId).document(noteId).set(
            note.copy(
                status = DispatchStatus.READY,
                deliveryRunId = "",
                dispatchedAt = null,
                updatedAt = now
            ).toMap()
        )
    }

    override suspend fun getNotesForOrder(companyId: String, orderId: String): List<DispatchNote> {
        if (orderId.isEmpty()) return emptyList()
        return notesCol(companyId).where { "sourceOrderId" equalTo orderId }
            .get().documents.map { it.toNote() }
    }

    override suspend fun deleteNote(companyId: String, noteId: String) {
        linesCol(companyId, noteId).get().documents.forEach { line ->
            runCatching { linesCol(companyId, noteId).document(line.id).delete() }
        }
        notesCol(companyId).document(noteId).delete()
    }

    private fun calendarDayBounds(dayMillis: Long): Pair<Long, Long> {
        // Acepta cualquier millis dentro del día y devuelve [00:00, 23:59:59.999] en TZ local.
        val tz = TimeZone.currentSystemDefault()
        val date: LocalDate = Instant.fromEpochMilliseconds(dayMillis).toLocalDateTime(tz).date
        val from = LocalDateTime(date, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds()
        val nextDay = date.plus(1, DateTimeUnit.DAY)
        val until = LocalDateTime(nextDay, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds() - 1
        return from to until
    }

    // ── Llegada y recepción ───────────────────────────────────────────────────

    override suspend fun acknowledgeArrival(
        companyId: String, noteId: String, lines: List<DispatchNoteLine>, currentUser: User
    ) {
        val note = getNoteHeader(companyId, noteId) ?: return
        if (note.status != DispatchStatus.IN_TRANSIT) {
            error("Solo se puede reportar llegada de notas en tránsito")
        }
        val now = Clock.System.now().toEpochMilliseconds()

        // Persistir el checklist de llegada (lineArrivedConfirmedAt) por producto.
        // Si una línea viene sin id (defensivo, no debería) se omite.
        lines.forEach { line ->
            if (line.id.isNotEmpty()) {
                linesCol(companyId, noteId).document(line.id).update(
                    mapOf("lineArrivedConfirmedAt" to line.lineArrivedConfirmedAt).firestoreSafe()
                )
            }
        }

        notesCol(companyId).document(noteId).set(
            note.copy(
                status = DispatchStatus.ARRIVED,
                arrivedAt = now,
                arrivedByUserId = currentUser.id,
                arrivedByUserName = currentUser.displayName,
                updatedAt = now
            ).toMap()
        )
    }

    override suspend fun confirmReception(
        companyId: String, noteId: String, lines: List<DispatchNoteLine>, currentUser: User
    ) {
        val note = getNoteHeader(companyId, noteId) ?: return
        if (note.status != DispatchStatus.IN_TRANSIT && note.status != DispatchStatus.ARRIVED) {
            error("La nota no está en tránsito ni en estado de llegada")
        }
        val now = Clock.System.now().toEpochMilliseconds()

        // Persistir líneas con receivedQuantity + razones.
        lines.forEach { line ->
            if (line.id.isNotEmpty()) linesCol(companyId, noteId).document(line.id).set(line.toMap())
        }

        val hasDiscrepancy = lines.any { it.receivedQuantity != it.packedQuantity }
        val newStatus = if (hasDiscrepancy) DispatchStatus.DELIVERED_WITH_DISCREPANCY
                        else DispatchStatus.DELIVERED
        notesCol(companyId).document(noteId).set(
            note.copy(
                status = newStatus,
                hasDiscrepancy = hasDiscrepancy,
                receivedByUserId = currentUser.id,
                receivedByUserName = currentUser.displayName,
                receivedAt = now,
                updatedAt = now
            ).toMap()
        )

        // Aplicar movimientos al stock terminado de la sucursal destino.
        lines.filter { it.receivedQuantity > 0 }.forEach { line ->
            finishedStockRepository.applyMovement(
                companyId = companyId,
                movement = FinishedStockMovement(
                    productId = line.productId,
                    productName = line.productName,
                    branchId = note.destinationBranchId,
                    branchName = note.destinationBranchName,
                    type = FinishedMovementType.RECEPCION,
                    quantity = line.receivedQuantity,
                    unitPriceAtMovement = line.unitWholesalePrice,
                    referenceType = FinishedMovementReference.DISPATCH,
                    referenceId = noteId,
                    reason = null,
                    createdByUserId = currentUser.id,
                    createdByUserName = currentUser.displayName
                )
            )
        }
    }

    override suspend fun cancelNote(
        companyId: String,
        noteId: String,
        currentUser: User,
        reason: String?
    ) {
        val note = getNoteHeader(companyId, noteId) ?: return
        if (note.status == DispatchStatus.IN_TRANSIT ||
            DispatchStatus.isTerminal(note.status)) {
            error("No se puede cancelar una nota en tránsito o ya cerrada")
        }
        val lines = linesCol(companyId, noteId).get().documents.map { it.toLine() }
        val hasBread = lines.any { it.packedQuantity > 0 }
        val now = Clock.System.now().toEpochMilliseconds()

        // El encabezado se escribe ANTES de mover stock, igual que `receivePurchase`:
        // si el ingreso falla a media aplicación, la nota ya está CANCELLED y un
        // reintento choca con la guarda de arriba en vez de sumar el pan dos veces.
        notesCol(companyId).document(noteId).set(
            note.copy(
                status = DispatchStatus.CANCELLED,
                statusBeforeCancel = note.status,
                cancelledAt = now,
                cancelledByUserId = currentUser.id,
                cancelledByUserName = currentUser.displayName.ifEmpty { currentUser.email },
                cancelReason = reason?.takeIf { it.isNotBlank() },
                plantAbsorbedAtCancel = hasBread,
                updatedAt = now
            ).toMap()
        )
        if (hasBread) {
            applyPlantAbsorption(
                companyId = companyId,
                note = note,
                lines = lines,
                currentUser = currentUser,
                sign = 1.0,
                reason = listOfNotNull(
                    "Nota cancelada (${note.destinationBranchName}) — el pan queda en planta",
                    reason?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
            )
        }
    }

    override suspend fun restoreNote(companyId: String, noteId: String, currentUser: User) {
        val note = getNoteHeader(companyId, noteId) ?: return
        if (note.status != DispatchStatus.CANCELLED) {
            error("Solo se puede restaurar una nota cancelada")
        }
        // Notas canceladas antes de que existiera `statusBeforeCancel` no dicen de
        // dónde venían: vuelven a DRAFT, que es el punto del ciclo donde todo se
        // puede volver a decidir (empacar, cantidades, precios).
        val target = note.statusBeforeCancel
            .takeIf { it == DispatchStatus.DRAFT || it == DispatchStatus.READY }
            ?: DispatchStatus.DRAFT
        val lines = linesCol(companyId, noteId).get().documents.map { it.toLine() }
        val now = Clock.System.now().toEpochMilliseconds()

        notesCol(companyId).document(noteId).set(
            note.copy(
                status = target,
                statusBeforeCancel = "",
                cancelledAt = null,
                cancelledByUserId = "",
                cancelledByUserName = "",
                cancelReason = null,
                plantAbsorbedAtCancel = false,
                updatedAt = now
            ).toMap()
        )
        if (note.plantAbsorbedAtCancel) {
            applyPlantAbsorption(
                companyId = companyId,
                note = note,
                lines = lines,
                currentUser = currentUser,
                sign = -1.0,
                reason = "Nota restaurada (${note.destinationBranchName}) — el pan vuelve al despacho"
            )
        }
    }

    /**
     * Mueve el pan de la nota contra el inventario de terminado de la PLANTA
     * ([DispatchNote.originBranchId]), con `sign = +1` al cancelar y `sign = -1` al
     * restaurar. Un solo helper para los dos sentidos: si el ingreso y el egreso se
     * escribieran por separado terminarían usando cantidades distintas —el bug clásico
     * de todo reverso escrito aparte— y la planta quedaría con pan fantasma.
     *
     * Se agrupa por producto porque una nota puede traer el mismo `productId` en dos
     * líneas (especiales consolidados), y `applyMovement` lee-modifica-escribe el stock:
     * dos llamadas seguidas sobre el mismo doc pueden pisarse.
     */
    private suspend fun applyPlantAbsorption(
        companyId: String,
        note: DispatchNote,
        lines: List<DispatchNoteLine>,
        currentUser: User,
        sign: Double,
        reason: String
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val userName = currentUser.displayName.ifEmpty { currentUser.email }
        lines.filter { it.packedQuantity > 0 }
            .groupBy { it.productId }
            .forEach { (productId, group) ->
                val units = group.sumOf { it.packedQuantity }
                runCatching {
                    finishedStockRepository.applyMovement(
                        companyId,
                        FinishedStockMovement(
                            productId = productId,
                            productName = group.first().productName,
                            branchId = note.originBranchId,
                            branchName = note.originBranchName,
                            type = FinishedMovementType.AJUSTE,
                            quantity = sign * units,
                            unitPriceAtMovement = group.first().unitWholesalePrice,
                            referenceType = FinishedMovementReference.DISPATCH,
                            referenceId = note.id,
                            reason = reason,
                            createdByUserId = currentUser.id,
                            createdByUserName = userName,
                            createdAt = now
                        )
                    )
                }
            }
    }

    override suspend fun markAsLocalDispatch(
        companyId: String, noteId: String, currentUser: User
    ) {
        val note = getNoteHeader(companyId, noteId) ?: return
        if (note.originBranchId != note.destinationBranchId) {
            error("Solo aplica cuando la planta productora es la misma sucursal de destino")
        }
        if (note.status != DispatchStatus.READY) {
            error("La nota no está lista para mostrador (debe estar en estado Lista)")
        }
        val lines = linesCol(companyId, noteId).get().documents.map { it.toLine() }
        if (lines.any { it.priceSource == PriceSourceTag.NONE && it.packedQuantity > 0 }) {
            error("Hay líneas sin precio definido. Configura el precio antes de cerrar.")
        }
        val now = Clock.System.now().toEpochMilliseconds()

        // Salto: READY → DELIVERED (sin pasar por IN_TRANSIT/ARRIVED)
        notesCol(companyId).document(noteId).set(
            note.copy(
                status = DispatchStatus.DELIVERED,
                dispatchedAt = now,
                arrivedAt = now,
                arrivedByUserId = currentUser.id,
                arrivedByUserName = currentUser.displayName,
                receivedByUserId = currentUser.id,
                receivedByUserName = currentUser.displayName,
                receivedAt = now,
                hasDiscrepancy = false,
                updatedAt = now
            ).toMap()
        )

        // Suma directa a FinishedProductStock + Kardex.
        lines.filter { it.packedQuantity > 0 }.forEach { line ->
            // Para self-dispatch, receivedQuantity = packedQuantity (no hay merma en tránsito).
            linesCol(companyId, noteId).document(line.id).set(
                line.copy(receivedQuantity = line.packedQuantity).toMap()
            )
            finishedStockRepository.applyMovement(
                companyId = companyId,
                movement = FinishedStockMovement(
                    productId = line.productId,
                    productName = line.productName,
                    branchId = note.destinationBranchId,
                    branchName = note.destinationBranchName,
                    type = FinishedMovementType.RECEPCION,
                    quantity = line.packedQuantity,
                    unitPriceAtMovement = line.unitWholesalePrice,
                    referenceType = FinishedMovementReference.DISPATCH,
                    referenceId = noteId,
                    reason = "Producción propia (mostrador)",
                    createdByUserId = currentUser.id,
                    createdByUserName = currentUser.displayName
                )
            )
        }
    }

    override suspend fun aggregateDispatchInRange(
        companyId: String,
        fromMillis: Long,
        untilMillis: Long
    ): DispatchSummary = coroutineScope {
        val notes = notesCol(companyId).get().documents
            .map { it.toNote() }
            .filter { it.productionDate in fromMillis until untilMillis }
        if (notes.isEmpty()) return@coroutineScope DispatchSummary()

        // Solo cargamos líneas para notas DELIVERED/DELIVERED_WITH_DISCREPANCY/IN_TRANSIT/ARRIVED
        // (las que pueden tener pérdidas en tránsito). DRAFT/READY no entran al cálculo de loss.
        val notesWithLines = notes.map { note ->
            async {
                val needsLines = note.status in listOf(
                    DispatchStatus.DELIVERED,
                    DispatchStatus.DELIVERED_WITH_DISCREPANCY,
                    DispatchStatus.ARRIVED,
                    DispatchStatus.IN_TRANSIT
                )
                if (needsLines) note to linesCol(companyId, note.id).get().documents.map { it.toLine() }
                else note to emptyList()
            }
        }.awaitAll()

        val deliveredCount = notes.count {
            it.status == DispatchStatus.DELIVERED ||
                it.status == DispatchStatus.DELIVERED_WITH_DISCREPANCY
        }
        val withDiscrepancyCount = notes.count { it.hasDiscrepancy }
        val cancelledCount = notes.count { it.status == DispatchStatus.CANCELLED }

        var totalLossUnits = 0.0
        var totalLossValue = 0.0
        val byDiscrepancy = HashMap<String, Pair<Int, Double>>() // type -> (count, units)
        notesWithLines.forEach { (_, lines) ->
            lines.forEach { line ->
                val type = line.discrepancyType ?: return@forEach
                val loss = (line.packedQuantity - line.receivedQuantity).coerceAtLeast(0.0)
                if (type == DiscrepancyType.SHORT || type == DiscrepancyType.DAMAGED) {
                    totalLossUnits += loss
                    totalLossValue += loss * line.unitWholesalePrice
                }
                val (count, units) = byDiscrepancy[type] ?: (0 to 0.0)
                byDiscrepancy[type] = (count + 1) to (units + loss)
            }
        }

        val byBranch = notesWithLines
            .groupBy { it.first.destinationBranchId to it.first.destinationBranchName }
            .map { (key, list) ->
                val lossUnits = list.sumOf { (_, lines) ->
                    lines.sumOf { l ->
                        if (l.discrepancyType == DiscrepancyType.SHORT ||
                            l.discrepancyType == DiscrepancyType.DAMAGED)
                            (l.packedQuantity - l.receivedQuantity).coerceAtLeast(0.0)
                        else 0.0
                    }
                }
                DispatchBranchStat(
                    branchId = key.first,
                    branchName = key.second,
                    notesCount = list.size,
                    discrepancyCount = list.count { it.first.hasDiscrepancy },
                    lossUnits = lossUnits
                )
            }
            .sortedByDescending { it.notesCount }

        DispatchSummary(
            notesCount = notes.size,
            deliveredCount = deliveredCount,
            withDiscrepancyCount = withDiscrepancyCount,
            cancelledCount = cancelledCount,
            inTransitLossUnits = totalLossUnits,
            inTransitLossValue = totalLossValue,
            byDestinationBranch = byBranch,
            byDiscrepancyType = byDiscrepancy.mapValues { (_, v) -> DiscrepancyStat(v.first, v.second) }
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun getNoteHeader(companyId: String, noteId: String): DispatchNote? {
        val doc = notesCol(companyId).document(noteId).get()
        return if (doc.exists) doc.toNote() else null
    }

    // ── Serialización ─────────────────────────────────────────────────────────

    private fun DispatchNote.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "lotId" to lotId,
        "sourceOrderId" to sourceOrderId,
        "originBranchId" to originBranchId,
        "originBranchName" to originBranchName,
        "destinationType" to destinationType,
        "destinationBranchId" to destinationBranchId,
        "destinationBranchName" to destinationBranchName,
        "destinationCustomerId" to destinationCustomerId,
        "destinationCustomerName" to destinationCustomerName,
        "productionDate" to productionDate,
        "shiftId" to shiftId,
        "shiftName" to shiftName,
        "status" to status,
        "deliveryRunId" to deliveryRunId,
        "packedByUserId" to packedByUserId,
        "packedByUserName" to packedByUserName,
        "packedAt" to packedAt,
        "dispatchedAt" to dispatchedAt,
        "arrivedAt" to arrivedAt,
        "arrivedByUserId" to arrivedByUserId,
        "arrivedByUserName" to arrivedByUserName,
        "receivedByUserId" to receivedByUserId,
        "receivedByUserName" to receivedByUserName,
        "receivedAt" to receivedAt,
        "totalValueWholesale" to totalValueWholesale,
        "priceListId" to priceListId,
        "priceListName" to priceListName,
        "hasPriceMissing" to hasPriceMissing,
        "hasDiscrepancy" to hasDiscrepancy,
        "notes" to notes,
        "statusBeforeCancel" to statusBeforeCancel,
        "cancelledAt" to cancelledAt,
        "cancelledByUserId" to cancelledByUserId,
        "cancelledByUserName" to cancelledByUserName,
        "cancelReason" to cancelReason,
        "plantAbsorbedAtCancel" to plantAbsorbedAtCancel,
        "createdAt" to if (createdAt == 0L) Clock.System.now().toEpochMilliseconds() else createdAt,
        "updatedAt" to Clock.System.now().toEpochMilliseconds(),
        "lineCount" to lineCount
    )

    private fun DocumentSnapshot.toNote() = DispatchNote(
        id = id,
        lotId = get<String?>("lotId") ?: "",
        sourceOrderId = get<String?>("sourceOrderId") ?: "",
        originBranchId = get<String?>("originBranchId") ?: "",
        originBranchName = get<String?>("originBranchName") ?: "",
        destinationType = get<String?>("destinationType") ?: com.bakery.erp.model.DispatchDestinationType.BRANCH,
        destinationBranchId = get<String?>("destinationBranchId") ?: "",
        destinationBranchName = get<String?>("destinationBranchName") ?: "",
        destinationCustomerId = get<String?>("destinationCustomerId") ?: "",
        destinationCustomerName = get<String?>("destinationCustomerName") ?: "",
        productionDate = readMillis("productionDate"),
        shiftId = get<String?>("shiftId") ?: "",
        shiftName = get<String?>("shiftName") ?: "",
        status = get<String?>("status") ?: DispatchStatus.DRAFT,
        deliveryRunId = get<String?>("deliveryRunId") ?: "",
        packedByUserId = get<String?>("packedByUserId") ?: "",
        packedByUserName = get<String?>("packedByUserName") ?: "",
        packedAt = readMillisOrNull("packedAt"),
        dispatchedAt = readMillisOrNull("dispatchedAt"),
        arrivedAt = readMillisOrNull("arrivedAt"),
        arrivedByUserId = get<String?>("arrivedByUserId") ?: "",
        arrivedByUserName = get<String?>("arrivedByUserName") ?: "",
        receivedByUserId = get<String?>("receivedByUserId") ?: "",
        receivedByUserName = get<String?>("receivedByUserName") ?: "",
        receivedAt = readMillisOrNull("receivedAt"),
        totalValueWholesale = get<Double?>("totalValueWholesale") ?: 0.0,
        priceListId = get<String?>("priceListId") ?: "",
        priceListName = get<String?>("priceListName") ?: "",
        hasPriceMissing = get<Boolean?>("hasPriceMissing") ?: false,
        hasDiscrepancy = get<Boolean?>("hasDiscrepancy") ?: false,
        notes = get<String?>("notes"),
        statusBeforeCancel = get<String?>("statusBeforeCancel") ?: "",
        cancelledAt = readMillisOrNull("cancelledAt"),
        cancelledByUserId = get<String?>("cancelledByUserId") ?: "",
        cancelledByUserName = get<String?>("cancelledByUserName") ?: "",
        cancelReason = get<String?>("cancelReason"),
        plantAbsorbedAtCancel = get<Boolean?>("plantAbsorbedAtCancel") ?: false,
        createdAt = readMillis("createdAt"),
        updatedAt = readMillis("updatedAt"),
        lineCount = (get<Double?>("lineCount") ?: 0.0).toInt()
    )

    private fun DispatchNoteLine.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "productId" to productId,
        "productName" to productName,
        "productUnit" to productUnit,
        "requestedQuantity" to requestedQuantity,
        "packedQuantity" to packedQuantity,
        "packedReason" to packedReason,
        "receivedQuantity" to receivedQuantity,
        "receivedReason" to receivedReason,
        "discrepancyType" to discrepancyType,
        "unitWholesalePrice" to unitWholesalePrice,
        "priceSource" to priceSource,
        "lineCheckedAt" to lineCheckedAt,
        "lineArrivedConfirmedAt" to lineArrivedConfirmedAt,
        "isSpecial" to isSpecial,
        "specialRawWeightGrams" to specialRawWeightGrams,
        "specialRecipeId" to specialRecipeId,
        "specialRecipeName" to specialRecipeName
    )

    private fun DocumentSnapshot.toLine() = DispatchNoteLine(
        id = id,
        productId = get<String?>("productId") ?: "",
        productName = get<String?>("productName") ?: "",
        productUnit = get<String?>("productUnit") ?: "",
        requestedQuantity = get<Double?>("requestedQuantity") ?: 0.0,
        packedQuantity = get<Double?>("packedQuantity") ?: 0.0,
        packedReason = get<String?>("packedReason"),
        receivedQuantity = get<Double?>("receivedQuantity") ?: 0.0,
        receivedReason = get<String?>("receivedReason"),
        discrepancyType = get<String?>("discrepancyType"),
        unitWholesalePrice = get<Double?>("unitWholesalePrice") ?: 0.0,
        priceSource = get<String?>("priceSource") ?: PriceSourceTag.NONE,
        lineCheckedAt = readMillisOrNull("lineCheckedAt"),
        lineArrivedConfirmedAt = readMillisOrNull("lineArrivedConfirmedAt"),
        isSpecial = runCatching { get<Boolean?>("isSpecial") }.getOrNull() ?: false,
        specialRawWeightGrams = runCatching { get<Double?>("specialRawWeightGrams") }.getOrNull() ?: 0.0,
        specialRecipeId = get<String?>("specialRecipeId") ?: "",
        specialRecipeName = get<String?>("specialRecipeName") ?: ""
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L

    private fun DocumentSnapshot.readMillisOrNull(field: String): Long? =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
}
