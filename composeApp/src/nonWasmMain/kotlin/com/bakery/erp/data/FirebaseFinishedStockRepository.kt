package com.bakery.erp.data

import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.report.MermaReasonStat
import com.bakery.erp.domain.report.ProductMovementStat
import com.bakery.erp.domain.report.StockMovementSummary
import com.bakery.erp.model.FinishedMovementType
import com.bakery.erp.model.FinishedProductStock
import com.bakery.erp.model.FinishedStockMovement
import com.bakery.erp.model.finishedStockDocId
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebaseFinishedStockRepository(
    private val firestore: FirebaseFirestore
) : FinishedStockRepository {

    private fun stocksCol(companyId: String) =
        firestore.collection("companies/$companyId/finishedStocks")

    private fun movementsCol(companyId: String) =
        firestore.collection("companies/$companyId/finishedMovements")

    override suspend fun getStock(
        companyId: String, branchId: String, productId: String
    ): FinishedProductStock? {
        val doc = stocksCol(companyId).document(finishedStockDocId(branchId, productId)).get()
        return if (doc.exists) doc.toStock() else null
    }

    override suspend fun getStocksForBranch(
        companyId: String, branchId: String
    ): List<FinishedProductStock> =
        stocksCol(companyId).where { "branchId" equalTo branchId }.get().documents
            .map { it.toStock() }

    override fun observeStocksForBranch(
        companyId: String, branchId: String
    ): Flow<List<FinishedProductStock>> =
        stocksCol(companyId).snapshots.map { snapshot ->
            snapshot.documents.map { it.toStock() }.filter { it.branchId == branchId }
        }

    override suspend fun getMovementsForProduct(
        companyId: String, branchId: String, productId: String,
        fromMillis: Long?, untilMillis: Long?
    ): List<FinishedStockMovement> {
        val all = movementsCol(companyId).get().documents
            .map { it.toMovement() }
            .filter { it.branchId == branchId && it.productId == productId }
        return all
            .filter { fromMillis == null || it.createdAt >= fromMillis }
            .filter { untilMillis == null || it.createdAt <= untilMillis }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun applyMovement(companyId: String, movement: FinishedStockMovement) {
        val docId = finishedStockDocId(movement.branchId, movement.productId)
        val current = getStock(companyId, movement.branchId, movement.productId)
        val now = Clock.System.now().toEpochMilliseconds()

        val newQty: Double
        val newAvgCost: Double
        if (movement.quantity > 0) {
            // Ingreso: recalcular costo promedio ponderado.
            val totalValueBefore = (current?.currentStock ?: 0.0) * (current?.avgUnitCost ?: 0.0)
            val totalValueIncoming = movement.quantity * movement.unitPriceAtMovement
            newQty = (current?.currentStock ?: 0.0) + movement.quantity
            newAvgCost = if (newQty > 0) (totalValueBefore + totalValueIncoming) / newQty
                         else movement.unitPriceAtMovement
        } else {
            // Salida: stock baja, costo promedio se mantiene.
            newQty = (current?.currentStock ?: 0.0) + movement.quantity
            newAvgCost = current?.avgUnitCost ?: 0.0
        }

        val stock = FinishedProductStock(
            id = docId,
            branchId = movement.branchId,
            branchName = movement.branchName,
            productId = movement.productId,
            productName = movement.productName,
            currentStock = newQty,
            avgUnitCost = newAvgCost,
            updatedAt = now
        )
        stocksCol(companyId).document(docId).set(stock.toMap())

        val moveRef = movementsCol(companyId).document
        val finalMove = movement.copy(id = moveRef.id, createdAt = if (movement.createdAt == 0L) now else movement.createdAt)
        moveRef.set(finalMove.toMap())
    }

    override suspend fun aggregateMovementsInRange(
        companyId: String,
        branchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): StockMovementSummary {
        val moves = movementsCol(companyId).get().documents
            .map { it.toMovement() }
            .filter {
                it.createdAt in fromMillis until untilMillis &&
                    (branchId.isEmpty() || it.branchId == branchId)
            }
        if (moves.isEmpty()) return StockMovementSummary()

        val received = moves.filter { it.type == FinishedMovementType.RECEPCION }
        val sold = moves.filter { it.type == FinishedMovementType.VENTA }
        val merma = moves.filter { it.type == FinishedMovementType.MERMA }
        val adjusts = moves.filter { it.type == FinishedMovementType.AJUSTE }

        val receivedUnits = received.sumOf { it.quantity }
        val soldUnits = sold.sumOf { -it.quantity }  // quantity es negativo
        val mermaUnits = merma.sumOf { -it.quantity }
        val mermaValue = merma.sumOf { -it.quantity * it.unitPriceAtMovement }
        val adjustmentUnits = adjusts.sumOf { it.quantity } // signed

        val productAccum = HashMap<String, Triple<String, Triple<Double, Double, Double>, Unit>>()
        moves.forEach { m ->
            if (m.productId.isEmpty()) return@forEach
            val (name, t, _) = productAccum[m.productId]
                ?: Triple(m.productName, Triple(0.0, 0.0, 0.0), Unit)
            val (r, s, mm) = t
            productAccum[m.productId] = Triple(
                name,
                Triple(
                    r + if (m.type == FinishedMovementType.RECEPCION) m.quantity else 0.0,
                    s + if (m.type == FinishedMovementType.VENTA) -m.quantity else 0.0,
                    mm + if (m.type == FinishedMovementType.MERMA) -m.quantity else 0.0
                ),
                Unit
            )
        }
        val byProduct = productAccum.entries
            .map { (pid, v) ->
                ProductMovementStat(pid, v.first, v.second.first, v.second.second, v.second.third)
            }
            .sortedByDescending { it.soldUnits + it.mermaUnits }
            .take(50)

        val byReason = merma.groupBy { it.reason ?: "Sin razón" }
            .mapValues { (_, list) ->
                MermaReasonStat(
                    units = list.sumOf { -it.quantity },
                    valueImpact = list.sumOf { -it.quantity * it.unitPriceAtMovement }
                )
            }

        return StockMovementSummary(
            receivedUnits = receivedUnits,
            soldUnits = soldUnits,
            soldRevenueAtCost = sold.sumOf { -it.quantity * it.unitPriceAtMovement },
            mermaUnits = mermaUnits,
            mermaValue = mermaValue,
            adjustmentUnits = adjustmentUnits,
            byProduct = byProduct,
            byMermaReason = byReason
        )
    }

    // ── Serialización ─────────────────────────────────────────────────────────

    private fun FinishedProductStock.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "branchId" to branchId,
        "branchName" to branchName,
        "productId" to productId,
        "productName" to productName,
        "currentStock" to currentStock,
        "avgUnitCost" to avgUnitCost,
        "updatedAt" to Clock.System.now().toEpochMilliseconds()
    )

    private fun DocumentSnapshot.toStock() = FinishedProductStock(
        id = id,
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        productId = get<String?>("productId") ?: "",
        productName = get<String?>("productName") ?: "",
        currentStock = get<Double?>("currentStock") ?: 0.0,
        avgUnitCost = get<Double?>("avgUnitCost") ?: 0.0,
        updatedAt = readMillis("updatedAt")
    )

    private fun FinishedStockMovement.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "productId" to productId,
        "productName" to productName,
        "branchId" to branchId,
        "branchName" to branchName,
        "type" to type,
        "quantity" to quantity,
        "unitPriceAtMovement" to unitPriceAtMovement,
        "referenceType" to referenceType,
        "referenceId" to referenceId,
        "reason" to reason,
        "createdByUserId" to createdByUserId,
        "createdByUserName" to createdByUserName,
        "createdAt" to if (createdAt == 0L) Clock.System.now().toEpochMilliseconds() else createdAt
    )

    private fun DocumentSnapshot.toMovement() = FinishedStockMovement(
        id = id,
        productId = get<String?>("productId") ?: "",
        productName = get<String?>("productName") ?: "",
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        type = get<String?>("type") ?: FinishedMovementType.AJUSTE,
        quantity = get<Double?>("quantity") ?: 0.0,
        unitPriceAtMovement = get<Double?>("unitPriceAtMovement") ?: 0.0,
        referenceType = get<String?>("referenceType") ?: "",
        referenceId = get<String?>("referenceId") ?: "",
        reason = get<String?>("reason"),
        createdByUserId = get<String?>("createdByUserId") ?: "",
        createdByUserName = get<String?>("createdByUserName") ?: "",
        createdAt = readMillis("createdAt")
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
