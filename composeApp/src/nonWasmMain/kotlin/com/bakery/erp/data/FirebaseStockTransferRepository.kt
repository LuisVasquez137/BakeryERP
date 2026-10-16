package com.bakery.erp.data

import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.NewStockLot
import com.bakery.erp.domain.StockTransferRepository
import com.bakery.erp.model.FinishedMovementReference
import com.bakery.erp.model.FinishedMovementType
import com.bakery.erp.model.FinishedStockMovement
import com.bakery.erp.model.STOCK_TRANSFER_MOVEMENT_TYPE
import com.bakery.erp.model.StockMovement
import com.bakery.erp.model.StockTransfer
import com.bakery.erp.model.StockTransferItemKind
import com.bakery.erp.model.StockTransferLine
import com.bakery.erp.model.StockTransferStatus
import com.bakery.erp.model.TransferLot
import com.bakery.erp.model.lotsForReceived
import com.bakery.erp.model.stockTransferReceiveError
import com.bakery.erp.model.stockTransferSendError
import com.bakery.erp.model.transferLotsFrom
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

class FirebaseStockTransferRepository(
    private val firestore: FirebaseFirestore,
    private val finishedStockRepository: FinishedStockRepository,
    private val ingredientRepository: IngredientRepository
) : StockTransferRepository {

    private fun col(companyId: String) = firestore.collection("companies/$companyId/stockTransfers")

    override suspend fun send(companyId: String, transfer: StockTransfer): StockTransfer {
        val lines = transfer.lines.filter { it.quantity > 0.0 }
        stockTransferSendError(transfer.copy(lines = lines))?.let { throw IllegalStateException(it) }
        val now = Clock.System.now().toEpochMilliseconds()

        // Costo y lotes con el stock ACTUAL del origen, no el de la pantalla.
        val priced = lines.map { line ->
            when (line.kind) {
                StockTransferItemKind.PRODUCT -> {
                    val avg = finishedStockRepository.getStock(companyId, transfer.originBranchId, line.itemId)?.avgUnitCost ?: 0.0
                    // Sin promedio (reventa que entró sin costo, stock en cero) vale lo que dio la pantalla.
                    line.copy(unitCost = if (avg > 0.0) avg else line.unitCost)
                }
                else -> {
                    val lots = ingredientRepository.getStockLots(companyId, transfer.originBranchId, line.itemId)
                    line.copy(lots = transferLotsFrom(lots, line.quantity))
                }
            }
        }
        val saved = transfer.copy(
            id = transfer.id.ifEmpty { col(companyId).document.id },
            status = StockTransferStatus.IN_TRANSIT,
            lines = priced,
            note = transfer.note.trim(),
            sentAt = transfer.sentAt.takeIf { it > 0 } ?: now
        )
        // 1. Encabezado antes que el stock.
        col(companyId).document(saved.id).set(saved.toMap())

        // 2. Sale del origen, en serie (applyMovement y updateBranchStock leen-modifican-escriben).
        val reason = "Traslado ${saved.folio} a ${saved.destinationBranchName}" + noteSuffix(saved.note)
        saved.lines.forEach { line ->
            moveLine(
                companyId, line, saved, saved.originBranchId, saved.originBranchName,
                delta = -line.quantity, lots = emptyList(), reason = reason,
                byUserId = saved.sentByUserId, byName = saved.sentByName, at = saved.sentAt
            )
        }
        return saved
    }

    override suspend fun receive(
        companyId: String, transferId: String, received: List<Double>,
        byUserId: String, byName: String, byEmployeeId: String, note: String
    ): StockTransfer {
        val current = get(companyId, transferId) ?: throw IllegalStateException("No se encontró el traslado")
        stockTransferReceiveError(current, received)?.let { throw IllegalStateException(it) }
        val now = Clock.System.now().toEpochMilliseconds()
        val updated = current.copy(
            status = StockTransferStatus.RECEIVED,
            lines = current.lines.mapIndexed { i, l -> l.copy(receivedQuantity = received[i]) },
            receivedByUserId = byUserId,
            receivedByName = byName,
            receivedByEmployeeId = byEmployeeId,
            receivedAt = now,
            receiveNote = note.trim()
        )
        // Encabezado primero: un segundo toque choca con "ya está recibido" en vez de sumar dos veces.
        col(companyId).document(transferId).update(firestoreSafeOf(
            "status" to updated.status,
            "lineReceivedQuantities" to received,
            "receivedByUserId" to byUserId,
            "receivedByName" to byName,
            "receivedByEmployeeId" to byEmployeeId,
            "receivedAt" to now,
            "receiveNote" to updated.receiveNote
        ))
        val reason = "Traslado ${updated.folio} de ${updated.originBranchName}" + noteSuffix(updated.receiveNote)
        updated.lines.forEach { line ->
            val qty = line.receivedQuantity ?: 0.0
            if (qty <= 0.0) return@forEach
            moveLine(
                companyId, line, updated, updated.destinationBranchId, updated.destinationBranchName,
                delta = qty, lots = lotsForReceived(line.lots, qty), reason = reason,
                byUserId = byUserId, byName = byName, at = now
            )
        }
        return updated
    }

    override suspend fun cancel(
        companyId: String, transferId: String, byUserId: String, byName: String, reason: String
    ): StockTransfer {
        val current = get(companyId, transferId) ?: throw IllegalStateException("No se encontró el traslado")
        check(current.isInTransit) { "Este traslado ya está ${StockTransferStatus.label(current.status).lowercase()}" }
        val now = Clock.System.now().toEpochMilliseconds()
        col(companyId).document(transferId).update(firestoreSafeOf(
            "status" to StockTransferStatus.CANCELLED,
            "cancelledByUserId" to byUserId,
            "cancelledByName" to byName,
            "cancelledAt" to now,
            "cancelReason" to reason.trim()
        ))
        // Corregir = escribir la diferencia: el stock vuelve con un movimiento de regreso, la
        // salida queda en el Kardex.
        val why = "Traslado ${current.folio} cancelado" + noteSuffix(reason)
        current.lines.forEach { line ->
            moveLine(
                companyId, line, current, current.originBranchId, current.originBranchName,
                delta = line.quantity, lots = line.lots, reason = why,
                byUserId = byUserId, byName = byName, at = now
            )
        }
        return current.copy(
            status = StockTransferStatus.CANCELLED, cancelledByUserId = byUserId,
            cancelledByName = byName, cancelledAt = now, cancelReason = reason.trim()
        )
    }

    /**
     * Mueve una línea en [branchId]. Entrada de un insumo con [lots]: un lote por cada uno (misma
     * fecha y costo que en el origen); lo que no venía en lote entra suelto y `updateBranchStock`
     * decide si lo anota como lote sin fecha.
     */
    private suspend fun moveLine(
        companyId: String, line: StockTransferLine, transfer: StockTransfer,
        branchId: String, branchName: String, delta: Double, lots: List<TransferLot>,
        reason: String, byUserId: String, byName: String, at: Long
    ) {
        if (line.kind == StockTransferItemKind.PRODUCT) {
            finishedStockRepository.applyMovement(companyId, FinishedStockMovement(
                productId = line.itemId,
                productName = line.itemName,
                branchId = branchId,
                branchName = branchName,
                type = FinishedMovementType.TRANSFERENCIA,
                quantity = delta,
                // La entrada lleva el costo del origen: se mezcla con el promedio del destino.
                unitPriceAtMovement = line.unitCost,
                referenceType = FinishedMovementReference.TRANSFER,
                referenceId = transfer.id,
                reason = reason,
                createdByUserId = byUserId,
                createdByUserName = byName,
                createdAt = at
            ))
            return
        }
        val prev = runCatching {
            ingredientRepository.getBranchStocksForIngredient(companyId, line.itemId, listOf(branchId))
                .find { it.branchId == branchId }?.currentStock
        }.getOrNull() ?: 0.0
        if (delta < 0.0 || lots.isEmpty()) {
            ingredientRepository.updateBranchStock(companyId, line.itemId, line.itemName, branchId, branchName, delta)
        } else {
            lots.forEach { lot ->
                ingredientRepository.updateBranchStock(
                    companyId, line.itemId, line.itemName, branchId, branchName, lot.quantity,
                    newLot = NewStockLot(
                        expiryDate = lot.expiryDate, lotCode = lot.lotCode,
                        unitCost = lot.unitCost, receivedAt = at
                    )
                )
            }
            val loose = delta - lots.sumOf { it.quantity }
            if (loose > 0.0) {
                ingredientRepository.updateBranchStock(companyId, line.itemId, line.itemName, branchId, branchName, loose)
            }
        }
        ingredientRepository.addStockMovement(companyId, line.itemId, StockMovement(
            type = STOCK_TRANSFER_MOVEMENT_TYPE,
            quantity = delta,
            balanceBefore = prev,
            balanceAfter = (prev + delta).coerceAtLeast(0.0),
            branchId = branchId,
            branchName = branchName,
            reference = transfer.id,
            date = at,
            notes = "$reason · $byName"
        ))
    }

    private fun noteSuffix(note: String) = note.trim().takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty()

    override suspend fun get(companyId: String, transferId: String): StockTransfer? {
        val doc = runCatching { col(companyId).document(transferId).get() }.getOrNull() ?: return null
        return if (doc.exists) doc.toTransfer() else null
    }

    override suspend fun getForBranches(
        companyId: String, branchIds: List<String>, fromMillis: Long, untilMillis: Long
    ): List<StockTransfer> {
        // Pocos documentos por empresa: se leen todos y se filtra en memoria (fechas y "origen O
        // destino" pedirían dos consultas e índices).
        return col(companyId).get().documents.map { it.toTransfer() }
            .forBranches(branchIds, fromMillis, untilMillis)
    }

    override fun observeForBranches(
        companyId: String, branchIds: List<String>, fromMillis: Long
    ): Flow<List<StockTransfer>> =
        col(companyId).snapshots.map { snap ->
            snap.documents.map { it.toTransfer() }.forBranches(branchIds, fromMillis, Long.MAX_VALUE)
        }

    private fun List<StockTransfer>.forBranches(
        branchIds: List<String>, fromMillis: Long, untilMillis: Long
    ): List<StockTransfer> =
        filter { branchIds.isEmpty() || it.originBranchId in branchIds || it.destinationBranchId in branchIds }
            .filter { it.isInTransit || it.sentAt in fromMillis until untilMillis }
            .sortedByDescending { it.sentAt }

    override suspend fun getReceivedInRange(companyId: String, fromMillis: Long, untilMillis: Long): List<StockTransfer> =
        col(companyId).where { "status" equalTo StockTransferStatus.RECEIVED }.get().documents
            .map { it.toTransfer() }
            .filter { it.receivedAt in fromMillis until untilMillis }

    // ── Serialización: líneas y lotes como arreglos paralelos (GitLive no guarda listas de objetos) ──

    private fun StockTransfer.toMap(): Map<String, Any?> {
        val lotLine = mutableListOf<Long>()
        val lotExpiry = mutableListOf<Long>()
        val lotCode = mutableListOf<String>()
        val lotQty = mutableListOf<Double>()
        val lotCost = mutableListOf<Double>()
        lines.forEachIndexed { i, l ->
            l.lots.forEach { lot ->
                lotLine += i.toLong(); lotExpiry += lot.expiryDate; lotCode += lot.lotCode
                lotQty += lot.quantity; lotCost += lot.unitCost
            }
        }
        return firestoreSafeOf(
            "id" to id,
            "originBranchId" to originBranchId,
            "originBranchName" to originBranchName,
            "destinationBranchId" to destinationBranchId,
            "destinationBranchName" to destinationBranchName,
            "status" to status,
            "lineKinds" to lines.map { it.kind },
            "lineItemIds" to lines.map { it.itemId },
            "lineItemNames" to lines.map { it.itemName },
            "lineUnits" to lines.map { it.unit },
            "lineQuantities" to lines.map { it.quantity },
            "lineDisplayQuantities" to lines.map { it.displayQuantity },
            "lineUnitCosts" to lines.map { it.unitCost },
            "lineReceivedQuantities" to (if (lines.all { it.receivedQuantity != null }) lines.map { it.receivedQuantity!! } else emptyList()),
            "lotLineIndexes" to lotLine.map { it.toDouble() },
            "lotExpiries" to lotExpiry.map { it.toDouble() },
            "lotCodes" to lotCode,
            "lotQuantities" to lotQty,
            "lotUnitCosts" to lotCost,
            "sentValue" to sentValue,
            "note" to note,
            "sentByUserId" to sentByUserId,
            "sentByName" to sentByName,
            "sentByEmployeeId" to sentByEmployeeId,
            "sentAt" to sentAt,
            "receivedByUserId" to receivedByUserId,
            "receivedByName" to receivedByName,
            "receivedByEmployeeId" to receivedByEmployeeId,
            "receivedAt" to receivedAt,
            "receiveNote" to receiveNote,
            "cancelledByUserId" to cancelledByUserId,
            "cancelledByName" to cancelledByName,
            "cancelledAt" to cancelledAt,
            "cancelReason" to cancelReason
        )
    }

    private fun DocumentSnapshot.str(field: String): String =
        runCatching { get<String?>(field) }.getOrNull() ?: ""

    private fun DocumentSnapshot.strings(field: String): List<String> =
        runCatching { get<List<String>?>(field) }.getOrNull() ?: emptyList()

    /** Un número entero puede volver como Long desde la web: se aceptan los dos. */
    private fun DocumentSnapshot.numbers(field: String): List<Double> =
        runCatching { get<List<Double>?>(field) }.getOrNull()
            ?: runCatching { get<List<Long>?>(field)?.map { it.toDouble() } }.getOrNull()
            ?: emptyList()

    private fun DocumentSnapshot.millis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: runCatching { get<Double?>(field)?.toLong() }.getOrNull() ?: 0L

    private fun DocumentSnapshot.toTransfer(): StockTransfer {
        val ids = strings("lineItemIds")
        val kinds = strings("lineKinds")
        val names = strings("lineItemNames")
        val units = strings("lineUnits")
        val qty = numbers("lineQuantities")
        val display = strings("lineDisplayQuantities")
        val costs = numbers("lineUnitCosts")
        val received = numbers("lineReceivedQuantities")
        val lotLine = numbers("lotLineIndexes").map { it.toInt() }
        val lotExpiry = numbers("lotExpiries")
        val lotCode = strings("lotCodes")
        val lotQty = numbers("lotQuantities")
        val lotCost = numbers("lotUnitCosts")
        val lotsByLine = lotLine.indices.groupBy({ lotLine[it] }) { j ->
            TransferLot(
                expiryDate = lotExpiry.getOrElse(j) { 0.0 }.toLong(),
                lotCode = lotCode.getOrElse(j) { "" },
                quantity = lotQty.getOrElse(j) { 0.0 },
                unitCost = lotCost.getOrElse(j) { 0.0 }
            )
        }
        return StockTransfer(
            id = id,
            originBranchId = str("originBranchId"),
            originBranchName = str("originBranchName"),
            destinationBranchId = str("destinationBranchId"),
            destinationBranchName = str("destinationBranchName"),
            status = str("status").ifEmpty { StockTransferStatus.IN_TRANSIT },
            lines = ids.indices.map { i ->
                StockTransferLine(
                    kind = kinds.getOrElse(i) { StockTransferItemKind.PRODUCT },
                    itemId = ids[i],
                    itemName = names.getOrElse(i) { "" },
                    unit = units.getOrElse(i) { "" },
                    quantity = qty.getOrElse(i) { 0.0 },
                    displayQuantity = display.getOrElse(i) { "" },
                    unitCost = costs.getOrElse(i) { 0.0 },
                    receivedQuantity = received.getOrNull(i),
                    lots = lotsByLine[i].orEmpty()
                )
            },
            note = str("note"),
            sentByUserId = str("sentByUserId"),
            sentByName = str("sentByName"),
            sentByEmployeeId = str("sentByEmployeeId"),
            sentAt = millis("sentAt"),
            receivedByUserId = str("receivedByUserId"),
            receivedByName = str("receivedByName"),
            receivedByEmployeeId = str("receivedByEmployeeId"),
            receivedAt = millis("receivedAt"),
            receiveNote = str("receiveNote"),
            cancelledByUserId = str("cancelledByUserId"),
            cancelledByName = str("cancelledByName"),
            cancelledAt = millis("cancelledAt"),
            cancelReason = str("cancelReason")
        )
    }
}
