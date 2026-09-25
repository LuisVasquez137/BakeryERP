package com.bakery.erp.model

/**
 * Lo que salió y lo que entró a una caja, sin contar las ventas.
 *
 * Nada se borra en el historial de una gaveta: eliminar un gasto o corregirle el monto escribe
 * un movimiento nuevo que lo compensa. Eso es correcto para auditar —se ve qué pasó y cuándo—
 * pero rompe el conteo ingenuo: un gasto de Q100 eliminado deja DOS renglones, y sumar los
 * negativos por un lado y los positivos por el otro daría "gastos Q100, ingresos Q100" cuando
 * en realidad no pasó nada.
 *
 * Por eso cada ajuste se suma **contra el movimiento que corrige**, no como movimiento propio.
 */
data class CashMovementTotals(
    /** Salidas de efectivo, ya netas de correcciones y eliminaciones. Siempre ≥ 0. */
    val gastos: Double = 0.0,
    /** Entradas que no son ventas, ya netas. Siempre ≥ 0. */
    val ingresos: Double = 0.0
)

/**
 * `true` si el movimiento existe para corregir o revertir a otro, en vez de por sí mismo.
 *
 * Se reconoce por el prefijo del motivo, que es el que escriben `syncPurchaseCashMovement` y
 * las correcciones de ingreso. Se eligió el motivo y no un campo booleano porque el texto ya
 * tenía que decirlo —es lo que lee quien revisa la caja— y un flag aparte podría contradecirlo.
 */
fun CashMovement.isAdjustment(): Boolean =
    reason.startsWith(ADJUSTMENT_PREFIX_DELETED) || reason.startsWith(ADJUSTMENT_PREFIX_CORRECTED)

const val ADJUSTMENT_PREFIX_DELETED = "Eliminado:"
const val ADJUSTMENT_PREFIX_CORRECTED = "Corrección:"

/** A qué movimiento apunta un ajuste, dentro de [all]. `null` si es un movimiento original. */
fun CashMovement.adjustmentTarget(all: List<CashMovement>): CashMovement? {
    if (!isAdjustment()) return null
    if (sourceMovementId.isNotEmpty()) return all.firstOrNull { it.id == sourceMovementId }
    // Los ajustes de gasto se ligan por la compra: cuando se escribieron, el id del movimiento
    // original no estaba a mano. El original es el EXPENSE de esa compra.
    if (sourcePurchaseId.isNotEmpty()) {
        return all.firstOrNull {
            it.sourcePurchaseId == sourcePurchaseId &&
                it.type == CashMovementType.EXPENSE &&
                !it.isAdjustment()
        }
    }
    return null
}

/**
 * Efecto neto de [movement] sobre el efectivo, ya aplicados sus ajustes. Negativo = salió.
 *
 * Un gasto de Q50 corregido a Q60 da −60; ese mismo gasto eliminado da 0.
 */
fun netCashEffect(movement: CashMovement, all: List<CashMovement>): Double {
    val own = CashMovementType.sign(movement.type) * movement.amount
    val adjustments = all.filter { it.adjustmentTarget(all)?.id == movement.id }
        .sumOf { CashMovementType.sign(it.type) * it.amount }
    return own + adjustments
}

/** `true` si el movimiento ya fue revertido y no debería poder tocarse otra vez. */
fun CashMovement.isReverted(all: List<CashMovement>): Boolean =
    all.any {
        it.reason.startsWith(ADJUSTMENT_PREFIX_DELETED) && it.adjustmentTarget(all)?.id == id
    }

/**
 * Totales de la caja. Los ajustes no cuentan como movimientos propios: ya están dentro del neto
 * del movimiento que corrigen.
 */
fun cashMovementTotals(all: List<CashMovement>): CashMovementTotals {
    var gastos = 0.0
    var ingresos = 0.0
    // Se recorre lo que NO tiene a quién compensar: los movimientos originales y también los
    // ajustes huérfanos, cuyo original ya no está (dato viejo, o borrado a mano). Descartar un
    // ajuste huérfano escondería efectivo que de verdad se movió; filtrar por `isAdjustment()`
    // hacía exactamente eso.
    all.filter { it.adjustmentTarget(all) == null }.forEach { movement ->
        val net = netCashEffect(movement, all)
        if (net < 0) gastos += -net else ingresos += net
    }
    return CashMovementTotals(gastos = gastos, ingresos = ingresos)
}

/**
 * Un renglón del detalle de la caja al cerrarla: lo que de verdad movió efectivo.
 *
 * [amount] ya es neto de sus ajustes y siempre positivo; [isOutflow] dice la dirección.
 * [wasCorrected] = el monto anotado no es el que quedó (se ve "corregido" en vez de dos
 * renglones que se contradicen).
 */
data class CashSessionDetailItem(
    val movement: CashMovement,
    val amount: Double,
    val isOutflow: Boolean,
    val wasCorrected: Boolean
)

/**
 * Detalle de lo que entró y salió de la caja, para el cierre.
 *
 * Mismo recorrido que [cashMovementTotals] —cada ajuste se pega a lo que corrige— para que la
 * suma de los renglones sea EXACTAMENTE el total de arriba. Un gasto eliminado neto 0 no se
 * lista: al contar el cajón no explica nada, y ya está en "Movimientos de caja" para quien lo
 * quiera auditar. Orden cronológico: es como se reconstruye el turno.
 */
fun cashSessionDetailItems(all: List<CashMovement>): List<CashSessionDetailItem> =
    all.filter { it.adjustmentTarget(all) == null }
        .mapNotNull { movement ->
            val net = netCashEffect(movement, all)
            if (kotlin.math.abs(net) < 0.005) return@mapNotNull null
            CashSessionDetailItem(
                movement = movement,
                amount = kotlin.math.abs(net),
                isOutflow = net < 0,
                wasCorrected = kotlin.math.abs(kotlin.math.abs(net) - movement.amount) > 0.005
            )
        }
        .sortedBy { it.movement.createdAt }
