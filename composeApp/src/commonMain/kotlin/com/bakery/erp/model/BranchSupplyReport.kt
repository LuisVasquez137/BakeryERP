package com.bakery.erp.model

import kotlin.math.abs

/**
 * Reporte "Pedido vs. recibido": la cadena completa de una sucursal por producto —
 * cuánto pidió, cuánto le asignó la planta en cada turno, cuánto le empacaron, cuánto
 * contó al recibir y cuánto vendió.
 *
 * No hay colección nueva: todo sale de los pedidos (`ProductionOrderLine`), de las notas
 * de despacho (una por pedido y por lote, o sea una por turno en una planta de dos) y de
 * los resúmenes de venta. La nota es el puente, porque es lo único que sabe cuánto de un
 * pedido le tocó a cada turno.
 */

/** Una columna de turno del reporte. `shiftId` vacío = planta sin turnos. */
data class SupplyShiftColumn(val shiftId: String, val shiftName: String)

/** Lo de un producto en un turno. */
data class SupplyShiftQty(
    /** Lo que la planta le asignó a la sucursal en ese turno (línea de la nota). */
    val assigned: Double = 0.0,
    /** Lo empacado. Solo cuenta cuando el empaque ya se cerró (nota fuera de DRAFT). */
    val packed: Double = 0.0,
    /** Hay alguna nota de este turno que todavía no se empacó. */
    val packingPending: Boolean = false
)

data class BranchSupplyRow(
    val productId: String,
    val productName: String,
    val isSpecial: Boolean = false,
    /** Masa del especial (snapshot); los de catálogo se resuelven en la pantalla. */
    val specialRecipeId: String = "",
    val specialRecipeName: String = "",
    val ordered: Double = 0.0,
    val byShift: Map<String, SupplyShiftQty> = emptyMap(),
    /** Σ asignado en todos los turnos, notas canceladas incluidas: es lo que se planeó. */
    val assignedTotal: Double = 0.0,
    /** Σ empacado de las notas que salieron (READY en adelante, sin canceladas). */
    val sent: Double = 0.0,
    /** Empacado de las notas ya contadas — contra esto se compara lo recibido. */
    val sentCounted: Double = 0.0,
    val received: Double = 0.0,
    /** Empacado que salió pero todavía no se cuenta en la sucursal. */
    val awaitingCount: Double = 0.0,
    /** Empacado de notas canceladas: se quedó en la planta. */
    val cancelled: Double = 0.0,
    /** Lo pedido en pedidos que todavía no entran a ningún lote (sin nota). */
    val notYetPlanned: Double = 0.0,
    /**
     * Algún pedido ya repartido cuyos turnos no suman lo que pidió. Pasa si el pedido se
     * editó después de repartir, o si la planta anuló el producto por mínimo de amasada.
     */
    val splitMismatch: Boolean = false,
    val discrepancyTypes: Set<String> = emptySet(),
    /** null = sin dato de ventas (p. ej. un especial, que no pasa por el POS). */
    val sold: Double? = null
) {
    /** Recibido − enviado, solo sobre lo ya contado. Negativo = faltó. */
    val receptionDiff: Double get() = received - sentCounted
}

data class BranchSupplyReport(
    val columns: List<SupplyShiftColumn> = emptyList(),
    val rows: List<BranchSupplyRow> = emptyList()
) {
    val orderedTotal: Double get() = rows.sumOf { it.ordered }
    val sentTotal: Double get() = rows.sumOf { it.sent }
    val receivedTotal: Double get() = rows.sumOf { it.received }
    val awaitingCountTotal: Double get() = rows.sumOf { it.awaitingCount }
    /** Lo que faltó al contar (solo diferencias negativas). */
    val shortTotal: Double get() = rows.sumOf { (-it.receptionDiff).coerceAtLeast(0.0) }
    /** Recibido ÷ pedido. null sin pedidos. */
    val fulfillmentPct: Double?
        get() = orderedTotal.takeIf { it > 0 }?.let { receivedTotal * 100.0 / it }
}

private const val EPS = 0.001

private fun isCounted(status: String) =
    status == DispatchStatus.DELIVERED || status == DispatchStatus.DELIVERED_WITH_DISCREPANCY

private fun isShipped(status: String) =
    status != DispatchStatus.DRAFT && status != DispatchStatus.CANCELLED

/**
 * Arma el reporte.
 *
 * @param orderLines líneas de cada pedido **no cancelado**, por `orderId`.
 * @param notes notas de despacho **con sus líneas**; se ignoran las que no salen de
 *   alguno de esos pedidos (un cliente externo, o de otra sucursal).
 * @param soldByProduct unidades vendidas por `productId` en el período; null = no se
 *   pidieron ventas.
 * @param shiftOrder orden de los turnos (`ProductionShift.dispatchOrder`, 0 = sin
 *   configurar, va al final). Sin él, alfabético.
 * @param expectedShiftsForOrder cuántas notas (turnos) debe llegar a tener cada pedido:
 *   2 para un pedido sin turno a una planta de dos turnos, 1 en el resto. Mientras el
 *   segundo lote no arranca su nota no existe, y lo que le toca no es un reparto roto:
 *   es pan que todavía no se planificó.
 */
fun buildBranchSupplyReport(
    orderLines: Map<String, List<ProductionOrderLine>>,
    notes: List<DispatchNote>,
    soldByProduct: Map<String, Double>? = null,
    shiftOrder: Map<String, Int> = emptyMap(),
    expectedShiftsForOrder: (orderId: String) -> Int = { 1 }
): BranchSupplyReport {
    val relevantNotes = notes.filter { it.sourceOrderId in orderLines }
    val notesByOrder = relevantNotes.groupBy { it.sourceOrderId }

    val columns = relevantNotes
        .map { SupplyShiftColumn(it.shiftId, it.shiftName) }
        .distinctBy { it.shiftId }
        .sortedWith(
            compareBy<SupplyShiftColumn>(
                { shiftOrder[it.shiftId]?.takeIf { o -> o > 0 } ?: Int.MAX_VALUE },
                { it.shiftName }
            )
        )

    val rows = linkedMapOf<String, BranchSupplyRow>()
    fun row(productId: String, seed: () -> BranchSupplyRow): BranchSupplyRow =
        rows.getOrPut(productId, seed)

    orderLines.forEach { (orderId, lines) ->
        val orderNotes = notesByOrder[orderId].orEmpty()
        val orderedHere = mutableMapOf<String, Double>()
        lines.forEach { line ->
            orderedHere[line.productId] = (orderedHere[line.productId] ?: 0.0) + line.requestedQuantity
            val current = row(line.productId) {
                BranchSupplyRow(
                    productId = line.productId,
                    productName = line.productName,
                    isSpecial = line.isSpecial,
                    specialRecipeId = line.specialRecipeId,
                    specialRecipeName = line.specialRecipeName
                )
            }
            rows[line.productId] = current.copy(
                ordered = current.ordered + line.requestedQuantity,
                notYetPlanned = current.notYetPlanned +
                    if (orderNotes.isEmpty()) line.requestedQuantity else 0.0
            )
        }

        if (orderNotes.isEmpty()) return@forEach

        val assignedHere = mutableMapOf<String, Double>()
        orderNotes.forEach { note ->
            val counted = isCounted(note.status)
            val shipped = isShipped(note.status)
            val cancelled = note.status == DispatchStatus.CANCELLED
            note.lines.forEach { nl ->
                assignedHere[nl.productId] = (assignedHere[nl.productId] ?: 0.0) + nl.requestedQuantity
                val current = row(nl.productId) {
                    // Producto que la nota trae y el pedido no (dato viejo o pedido
                    // editado después de despachar): igual se muestra, en 0 pedido.
                    BranchSupplyRow(
                        productId = nl.productId,
                        productName = nl.productName,
                        isSpecial = nl.isSpecial,
                        specialRecipeId = nl.specialRecipeId,
                        specialRecipeName = nl.specialRecipeName
                    )
                }
                val shift = current.byShift[note.shiftId] ?: SupplyShiftQty()
                val newShift = shift.copy(
                    assigned = shift.assigned + nl.requestedQuantity,
                    packed = shift.packed + if (shipped) nl.packedQuantity else 0.0,
                    packingPending = shift.packingPending || note.status == DispatchStatus.DRAFT
                )
                rows[nl.productId] = current.copy(
                    byShift = current.byShift + (note.shiftId to newShift),
                    assignedTotal = current.assignedTotal + nl.requestedQuantity,
                    sent = current.sent + if (shipped) nl.packedQuantity else 0.0,
                    sentCounted = current.sentCounted + if (counted) nl.packedQuantity else 0.0,
                    received = current.received + if (counted) nl.receivedQuantity else 0.0,
                    awaitingCount = current.awaitingCount +
                        if (shipped && !counted) nl.packedQuantity else 0.0,
                    cancelled = current.cancelled + if (cancelled) nl.packedQuantity else 0.0,
                    discrepancyTypes = current.discrepancyTypes +
                        listOfNotNull(nl.discrepancyType.takeIf { counted })
                )
            }
        }

        val shiftsMissing = orderNotes.map { it.shiftId }.distinct().size < expectedShiftsForOrder(orderId)
        (orderedHere.keys + assignedHere.keys).forEach { productId ->
            val ordered = orderedHere[productId] ?: 0.0
            val assigned = assignedHere[productId] ?: 0.0
            val gap = ordered - assigned
            if (abs(gap) <= EPS) return@forEach
            val r = rows[productId] ?: return@forEach
            rows[productId] = if (shiftsMissing && gap > 0) {
                r.copy(notYetPlanned = r.notYetPlanned + gap)
            } else {
                r.copy(splitMismatch = true)
            }
        }
    }

    val finalRows = rows.values.map { r ->
        r.copy(sold = if (soldByProduct == null || r.isSpecial) null else soldByProduct[r.productId] ?: 0.0)
    }
    return BranchSupplyReport(columns = columns, rows = finalRows)
}
