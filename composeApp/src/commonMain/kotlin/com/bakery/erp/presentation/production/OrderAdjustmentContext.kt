package com.bakery.erp.presentation.production

import com.bakery.erp.model.DISPATCH_TRIP_BOTH
import com.bakery.erp.model.DispatchNote
import com.bakery.erp.model.OrderAdjustmentTarget
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.ProductionOrder
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.ProductionShift
import com.bakery.erp.model.Recipe

/** Un turno (lote) al que se le puede aplicar el ajuste, con lo leído de Firestore. */
data class OrderAdjustmentShift(
    val lot: ProductionLot,
    val shift: ProductionShift?,
    /** Es el turno del primer viaje: el encargo `earlyShiftQuantity` se mueve con él. */
    val isFirstTrip: Boolean,
    val lines: List<ProductionLotLine>,
    /** Nota de despacho de ESTA sucursal en este lote, con líneas, si ya existe. */
    val note: DispatchNote?
) {
    val label: String get() = lot.shiftName.ifEmpty { "Lote" }
}

/**
 * Todo lo que el diálogo de ajuste necesita para decidir sin volver a leer: los
 * turnos del pedido, sus líneas y notas, y lo de la sucursal. El ViewModel lo
 * vuelve a leer igual antes de escribir — esto es para la pantalla.
 */
data class OrderAdjustmentContext(
    val order: ProductionOrder,
    val orderLines: List<ProductionOrderLine>,
    val shifts: List<OrderAdjustmentShift>,
    /** [com.bakery.erp.model.Branch.receivesOnlyInTrip] de la sucursal del pedido. */
    val branchTrip: Int = DISPATCH_TRIP_BOTH,
    val recipeIdByProductId: Map<String, String> = emptyMap(),
    val recipesById: Map<String, Recipe> = emptyMap()
)

/** Lo que esta sucursal tiene hoy de [productId] en [shift]. */
fun OrderAdjustmentContext.currentForBranch(shift: OrderAdjustmentShift, productId: String): Double {
    val line = shift.lines.find { it.productId == productId && !it.isSpecial }
    return if (shift.lot.isShiftSplit || shift.lines.any { it.sourceOrderAllocations.isNotEmpty() }) {
        line?.sourceOrderAllocations?.find { it.orderId == order.id }?.quantity ?: 0.0
    } else {
        // Lote de un solo turno: el pedido entero va en él.
        orderLines.find { it.productId == productId && !it.isSpecial }?.requestedQuantity ?: 0.0
    }
}

fun OrderAdjustmentContext.recipeIdFor(shift: OrderAdjustmentShift, productId: String): String =
    shift.lines.find { it.productId == productId }?.recipeId?.ifEmpty { null }
        ?: recipeIdByProductId[productId].orEmpty()

fun OrderAdjustmentContext.targetFor(shift: OrderAdjustmentShift, productId: String): OrderAdjustmentTarget {
    val recipeId = recipeIdFor(shift, productId)
    val ownLine = shift.lines.find { it.productId == productId && !it.isSpecial }
    val masaCounted = ownLine?.producedCountConfirmed == true ||
        (recipeId.isNotEmpty() && shift.lines.any { it.recipeId == recipeId && it.producedCountConfirmed })
    val noteLine = shift.note?.lines?.find { it.productId == productId && !it.isSpecial }
    return OrderAdjustmentTarget(
        lotStatus = shift.lot.status,
        shiftProducesProduct = productId !in shift.shift?.excludedProductIds.orEmpty(),
        masaCounted = masaCounted,
        currentForBranch = currentForBranch(shift, productId),
        noteStatus = shift.note?.status,
        noteLineChecked = noteLine?.lineCheckedAt != null
    )
}

/** Turnos que todavía admiten un ajuste: no terminados ni cancelados. */
val OrderAdjustmentContext.adjustableShifts: List<OrderAdjustmentShift>
    get() = shifts.filter {
        it.lot.status == ProductionLotStatus.OPEN || it.lot.status == ProductionLotStatus.IN_PROGRESS
    }
