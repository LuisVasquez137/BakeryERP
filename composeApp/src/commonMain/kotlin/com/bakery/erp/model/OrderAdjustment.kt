package com.bakery.erp.model

/**
 * Corrección del pedido de UNA sucursal cuando la planta ya repartió los turnos.
 *
 * El caso: la sucursal se confundió (pidió 50 en vez de 30, u olvidó un pan) y se
 * entera cuando el lote ya existe. Editar el pedido entero no sirve: el recálculo
 * reparte el cambio solo, y con la noche ya en producción todo caía en el turno de
 * día — aunque fuera pan que solo se hace de noche. Acá la encargada de planta dice
 * exactamente qué pasa: este producto, tanto más o menos, a esta sucursal, en este
 * turno. Lo que ella dice es lo que se escribe, sin reparto automático de por medio.
 *
 * **La verificación de "¿ya se hizo esa masa?" es de ella, con el panadero**: el
 * sistema no lo sabe (abrir la receta no prueba que se amasó, ni no abrirla que no).
 * Por eso un turno en producción exige que confirme que lo verificó, y queda
 * registrado. Lo único que el sistema sí sabe con certeza —la masa ya se contó, la
 * nota ya se empacó— lo bloquea él.
 *
 * Nada de esto toca el inventario de la planta: el cambio es del pedido de la
 * sucursal y viaja en su nota de despacho.
 */
data class ProductionOrderAdjustment(
    val id: String = "",
    val orderId: String = "",
    val lotId: String = "",
    val shiftName: String = "",
    val sellingBranchName: String = "",
    val productId: String = "",
    val productName: String = "",
    /** Positivo = se le suma a la sucursal; negativo = se le quita. */
    val delta: Double = 0.0,
    /** Estado del lote al ajustar: OPEN no pide verificación, IN_PROGRESS sí. */
    val lotStatus: String = "",
    /** La encargada confirmó con el panadero que esa masa todavía no se hizo. */
    val verifiedNotMade: Boolean = false,
    val recipeName: String = "",
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val createdAt: Long = 0L
)

/**
 * Lo que hace falta saber de UN turno para decidir si el ajuste se puede aplicar ahí.
 * Lo arma el ViewModel con lo que leyó; la decisión es lógica pura.
 */
data class OrderAdjustmentTarget(
    val lotStatus: String,
    /** Falso si el producto está en `ProductionShift.excludedProductIds` de ese turno. */
    val shiftProducesProduct: Boolean,
    /** Alguna línea de esa masa en ese lote ya tiene el conteo confirmado: el pan ya salió. */
    val masaCounted: Boolean,
    /** Lo que esta sucursal tiene de este producto en este turno hoy. */
    val currentForBranch: Double,
    /** Estado de la nota de despacho de esta sucursal en este turno, si existe. */
    val noteStatus: String?,
    /** La línea de este producto en esa nota ya se marcó como empacada. */
    val noteLineChecked: Boolean
)

/**
 * Motivo por el que el ajuste NO se puede guardar, o null si se puede.
 *
 * Bloquea solo lo que el sistema sabe con certeza. Que la masa no se haya hecho
 * todavía lo verifica la encargada (ver [orderAdjustmentNeedsVerification]).
 */
fun orderAdjustmentProblem(target: OrderAdjustmentTarget, delta: Double): String? = when {
    delta == 0.0 -> "Indicá cuánto se suma o se quita."
    target.lotStatus == ProductionLotStatus.COMPLETED -> "Ese turno ya terminó: el pan ya salió."
    target.lotStatus == ProductionLotStatus.CANCELLED -> "Ese lote está cancelado."
    // Mismo error que se arregló en el reparto: un turno que no hornea ese pan no
    // puede recibirlo por ninguna puerta.
    delta > 0.0 && !target.shiftProducesProduct -> "Ese turno no hace este pan."
    target.masaCounted -> "Esa masa ya se contó en ese turno: el pan ya está hecho."
    delta < 0.0 && -delta > target.currentForBranch ->
        "En ese turno esta sucursal solo tiene ${target.currentForBranch.fmtQty()}: no se le puede quitar más."
    target.noteStatus != null && target.noteStatus != DispatchStatus.DRAFT ->
        "La nota de esta sucursal en ese turno ya se cerró o salió: ajustala desde despacho."
    target.noteLineChecked -> "Ese producto ya se empacó para esta sucursal: corregilo en el empaque."
    else -> null
}

/** Un turno ya en producción exige que la encargada haya verificado con el panadero. */
fun orderAdjustmentNeedsVerification(lotStatus: String): Boolean =
    lotStatus == ProductionLotStatus.IN_PROGRESS

/**
 * Desglose por sucursal de una línea de lote repartido después del ajuste. Las demás
 * sucursales no se tocan; la que se ajusta sale de la lista si queda en cero.
 */
fun List<SourceOrderAllocation>.adjustedFor(
    orderId: String,
    branchName: String,
    delta: Double
): List<SourceOrderAllocation> {
    val current = find { it.orderId == orderId }?.quantity ?: 0.0
    val next = (current + delta).coerceAtLeast(0.0)
    val others = filter { it.orderId != orderId }
    return if (next <= 0.0) others
    else if (any { it.orderId == orderId }) map { if (it.orderId == orderId) it.copy(quantity = next) else it }
    else others + SourceOrderAllocation(orderId = orderId, branchName = branchName, quantity = next)
}

/**
 * Línea del pedido después del ajuste. El encargo del primer viaje
 * (`earlyShiftQuantity`) se mueve junto con el turno A: el recálculo del reparto
 * respeta ese número siempre, y si no acompañara al ajuste la siguiente edición de
 * cualquier pedido devolvería las unidades al otro turno.
 */
fun ProductionOrderLine.adjustedBy(delta: Double, isFirstTripShift: Boolean): ProductionOrderLine {
    val newRequested = (requestedQuantity + delta).coerceAtLeast(0.0)
    val newEarly = earlyShiftQuantity?.let { early ->
        (if (isFirstTripShift) early + delta else early).coerceIn(0.0, newRequested)
    }
    return copy(requestedQuantity = newRequested, earlyShiftQuantity = newEarly)
}

/** Texto que ve el panadero en la línea: "+20 Democracia (1:20 AM)". */
fun orderAdjustmentLabel(delta: Double, branchName: String, timeLabel: String): String {
    val sign = if (delta > 0) "+" else "−"
    return "$sign${kotlin.math.abs(delta).fmtQty()} $branchName ($timeLabel)"
}

private fun Double.fmtQty(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()
