package com.bakery.erp.model

import kotlin.math.floor
import kotlin.math.round

/**
 * Resultado de repartir la demanda de un día entre dos turnos de la misma planta.
 *
 * [quantityA]/[quantityB] son mapas `productId → cantidad` que siempre suman
 * exactamente la cantidad total pedida de cada producto — es lo que permite que
 * el reajuste manual (mover uno mueve el otro) nunca pierda ni invente unidades.
 */
data class ShiftSplitOutcome(
    val quantityA: Map<String, Double>,
    val quantityB: Map<String, Double>,
    /** Productos sin `rawWeightGrams` configurado: no entraron al balanceo por peso, se repartieron 50/50. */
    val productsWithoutWeight: List<String>,
    /** Productos excluidos de AMBOS turnos (config inconsistente) — se mandaron a un turno igual, con aviso. */
    val conflictingExclusions: List<String>
)

/**
 * Calcula el reparto inicial de [lines] (cantidades totales del día, ya
 * consolidadas — ver `consolidateOrderLines`) entre [shiftA] y [shiftB].
 *
 * Por producto:
 *  - Excluido de un turno y no del otro → 100% al que sí lo produce.
 *  - Excluido de ambos → se manda a A de forma determinística, con aviso (nunca
 *    se pierden unidades en silencio por una config inconsistente).
 *  - No excluido de ninguno ("flexible") → participa del balanceo por peso de
 *    masa: se resuelve una fracción `r` de A tal que el peso total de cada turno
 *    (lo forzado + su parte de lo flexible) quede lo más parejo posible, y esa
 *    fracción se aplica a cada producto flexible. Los flexibles sin peso en
 *    crudo no tienen cómo entrar al balanceo — van 50/50 aparte.
 *
 * El redondeo final usa el método del resto mayor (Hamilton) sobre todo el
 * conjunto flexible de una sola pasada, para que el redondeo por producto no
 * descuadre el total del día en unidades (el pan se cuenta en piezas enteras).
 */
fun computeShiftSplit(
    lines: List<ProductionLotLine>,
    shiftA: ProductionShift,
    shiftB: ProductionShift,
    rawWeightOf: (ProductionLotLine) -> Double?
): ShiftSplitOutcome {
    val excludedA = shiftA.excludedProductIds.toSet()
    val excludedB = shiftB.excludedProductIds.toSet()

    val forcedA = mutableListOf<ProductionLotLine>()
    val forcedB = mutableListOf<ProductionLotLine>()
    val flexible = mutableListOf<ProductionLotLine>()
    val conflicts = mutableListOf<String>()

    for (line in lines) {
        val allowedInA = line.productId !in excludedA
        val allowedInB = line.productId !in excludedB
        when {
            allowedInA && allowedInB -> flexible += line
            allowedInA && !allowedInB -> forcedA += line
            !allowedInA && allowedInB -> forcedB += line
            else -> {
                // Ningún turno lo hace: config inconsistente. Se manda a A entero
                // para no perder unidades, pero se avisa explícitamente.
                forcedA += line
                conflicts += line.productName
            }
        }
    }

    fun weightOf(line: ProductionLotLine): Double {
        val raw = rawWeightOf(line) ?: return 0.0
        if (raw <= 0.0) return 0.0
        return line.plannedQuantity * raw
    }

    val forcedWeightA = forcedA.sumOf(::weightOf)
    val forcedWeightB = forcedB.sumOf(::weightOf)

    val weightedFlexible = flexible.filter { (rawWeightOf(it) ?: 0.0) > 0.0 }
    val unweightedFlexible = flexible.filter { (rawWeightOf(it) ?: 0.0) <= 0.0 }
    val flexibleWeightTotal = weightedFlexible.sumOf(::weightOf)

    // Fracción de A que hay que darle al peso flexible para igualar los totales.
    // Si no hay peso flexible que repartir, no hay nada que resolver.
    val r = if (flexibleWeightTotal <= 0.0) 0.5
        else (0.5 + (forcedWeightB - forcedWeightA) / (2.0 * flexibleWeightTotal)).coerceIn(0.0, 1.0)

    val ratioByProductId = buildMap {
        weightedFlexible.forEach { put(it.productId, r) }
        // Sin dato de peso no hay señal para balancear: 50/50 por defecto, editable
        // igual que cualquier producto flexible.
        unweightedFlexible.forEach { put(it.productId, 0.5) }
    }

    // Redondeo por resto mayor: se calcula la parte de A "cruda" para cada
    // flexible, se toma el piso, y las unidades que faltan para llegar al total
    // redondeado del grupo se reparten a quienes tengan el resto más grande.
    val rawA = flexible.associate { it.productId to it.plannedQuantity * (ratioByProductId[it.productId] ?: 0.5) }
    val flooredA = rawA.mapValues { (_, v) -> floor(v) }
    val targetSum = round(rawA.values.sum()).toLong()
    val flooredSum = flooredA.values.sumOf { it.toLong() }
    val deficit = (targetSum - flooredSum).toInt().coerceAtLeast(0)
    val remainderOf = flexible.associate { it.productId to (rawA[it.productId]!! - flooredA[it.productId]!!) }
    val bumpSet = flexible
        .sortedWith(compareByDescending<ProductionLotLine> { remainderOf[it.productId] ?: 0.0 }
            .thenBy { it.productId })
        .take(deficit)
        .map { it.productId }
        .toSet()

    val quantityA = mutableMapOf<String, Double>()
    val quantityB = mutableMapOf<String, Double>()
    forcedA.forEach { quantityA[it.productId] = it.plannedQuantity; quantityB[it.productId] = 0.0 }
    forcedB.forEach { quantityB[it.productId] = it.plannedQuantity; quantityA[it.productId] = 0.0 }
    flexible.forEach { line ->
        val floorQty = flooredA[line.productId] ?: 0.0
        val qtyA = (if (line.productId in bumpSet) floorQty + 1.0 else floorQty)
            .coerceIn(0.0, line.plannedQuantity)
        quantityA[line.productId] = qtyA
        quantityB[line.productId] = (line.plannedQuantity - qtyA).coerceAtLeast(0.0)
    }

    return ShiftSplitOutcome(
        quantityA = quantityA,
        quantityB = quantityB,
        productsWithoutWeight = unweightedFlexible.map { it.productId },
        conflictingExclusions = conflicts
    )
}

/**
 * Reajuste manual: fija la cantidad de [productId] en el turno editado al valor
 * pedido (acotado a `[0, total]`) y pone el resto en el otro turno — así los dos
 * siempre suman la cantidad total pedida, sin perder ni inventar unidades.
 */
fun rebalanceShiftSplit(
    quantityA: Map<String, Double>,
    quantityB: Map<String, Double>,
    productId: String,
    editedShiftIsA: Boolean,
    newValue: Double
): Pair<Map<String, Double>, Map<String, Double>> {
    val total = (quantityA[productId] ?: 0.0) + (quantityB[productId] ?: 0.0)
    val clamped = newValue.coerceIn(0.0, total)
    val newA = if (editedShiftIsA) clamped else total - clamped
    val newB = total - newA
    return (quantityA + (productId to newA)) to (quantityB + (productId to newB))
}

/**
 * Reparto por defecto de [shiftTotalA] de un producto entre las órdenes (sucursales)
 * que lo pidieron, proporcional a lo que pidió cada una. [orderRequested] es
 * `orderId → cantidad pedida` de ese producto por TODAS las órdenes (de ambos
 * turnos, antes de repartir). El turno B de cada orden se deriva por conservación:
 * `orderRequested[id] - resultado[id]`.
 *
 * Sin este reparto, la única forma de saber cuánto de un turno le toca a cada
 * sucursal sería asumir que todas reciben la misma proporción global — y esa
 * suposición es la que rompe el caso real de una sucursal con encargo especial
 * (ver [rebalanceOrderAllocation] para el override manual).
 */
fun allocateOrdersForShiftA(
    orderRequested: Map<String, Double>,
    shiftTotalA: Double
): Map<String, Double> =
    rebalanceOrderAllocation(orderRequested, shiftTotalA, emptyMap())

/** [Branch.receivesOnlyInTrip]: la sucursal recibe en los dos viajes (default). */
const val DISPATCH_TRIP_BOTH = 0
/** [Branch.receivesOnlyInTrip]: todo su pedido sale en el primer viaje. */
const val DISPATCH_TRIP_FIRST = 1
/** [Branch.receivesOnlyInTrip]: todo su pedido sale en el segundo viaje. */
const val DISPATCH_TRIP_SECOND = 2

/**
 * Traduce las sucursales que solo reciben en un viaje a cantidades ya fijadas del
 * turno A, en el mismo formato que los encargos declarados: la que recibe solo en
 * el primer viaje queda fijada en todo lo que pidió, la del segundo en cero.
 *
 * Se expresa como pin (y no como un caso aparte en cada función) para que la
 * restricción viaje por el mismo camino que un encargo: donde ya se respetaba
 * "esto no se mueve", la sucursal de horario restringido tampoco se mueve.
 *
 * [restrictedTrip] es `orderId → viaje` y solo trae las órdenes restringidas.
 */
fun tripRestrictionPins(
    orderRequested: Map<String, Double>,
    restrictedTrip: Map<String, Int>
): Map<String, Double> = orderRequested.mapNotNull { (orderId, requested) ->
    when (restrictedTrip[orderId]) {
        DISPATCH_TRIP_FIRST -> orderId to requested
        DISPATCH_TRIP_SECOND -> orderId to 0.0
        else -> null
    }
}.toMap()

/**
 * Órdenes cuya sucursal solo recibe en un viaje que ese turno no puede servir,
 * porque el producto está excluido de ese turno.
 *
 * No hay reparto que cumpla las dos cosas: la exclusión es física (ese turno no
 * hornea ese pan) y la restricción de la sucursal es de horario. Manda la
 * producción — el pan sale en el turno que lo hace — y esto devuelve las órdenes
 * afectadas para avisarlo en pantalla, en vez de dejar que la encargada descubra
 * el problema cuando el camión ya salió.
 */
fun tripRestrictionConflicts(
    orderRequested: Map<String, Double>,
    restrictedTrip: Map<String, Int>,
    allowedInA: Boolean,
    allowedInB: Boolean
): List<String> = orderRequested.keys.filter { orderId ->
    when (restrictedTrip[orderId]) {
        DISPATCH_TRIP_FIRST -> !allowedInA
        DISPATCH_TRIP_SECOND -> !allowedInB
        else -> false
    }
}

/**
 * Reparto por defecto del primer viaje, **por sucursal**: mitad y mitad de lo que
 * pidió cada una, salvo que haya declarado un encargo ([pinned]).
 *
 * Se eligió mitad y mitad en vez de balancear por peso de masa (lo que hace
 * [computeShiftSplit] a nivel producto) por una razón de operación, no de cálculo:
 * el balanceo por peso daba números que la encargada no puede auditar. Con un
 * encargo de 60 de una sucursal, a la otra le tocaban 90 y 110 de sus 200 — correcto
 * para igualar el peso de las dos amasadas, imposible de explicar en la práctica.
 * Mitad y mitad se verifica de cabeza y coincide con cómo la planta piensa el día.
 *
 * Lo que se pierde es la compensación automática cuando un producto no se hace en
 * un turno (antes el turno liviano recibía más de otros productos). Eso queda a
 * cargo de la encargada, que ve la harina de cada masa por turno en la pantalla de
 * reparto y puede mover las cantidades a mano.
 *
 * La exclusión de turno sí manda sobre todo lo demás: un producto que ese turno no
 * produce no se puede repartir, va entero al otro.
 *
 * [restrictedTrip] son las sucursales que solo reciben en un viaje
 * ([Branch.receivesOnlyInTrip]): no se les reparte nada, va todo al viaje que
 * pueden recibir. Manda sobre el encargo declarado — un encargo para un viaje en
 * el que la sucursal está cerrada no es un encargo, es un error de captura — pero
 * cede ante la exclusión del producto, que es física (ver [tripRestrictionConflicts]).
 *
 * @param allowedInA / [allowedInB] si el producto se produce en ese turno.
 */
fun defaultOrderAllocationsForShiftA(
    orderRequested: Map<String, Double>,
    pinned: Map<String, Double>,
    allowedInA: Boolean,
    allowedInB: Boolean,
    restrictedTrip: Map<String, Int> = emptyMap()
): Map<String, Double> = when {
    // Ningún turno lo produce (config inconsistente): va entero a A, igual que
    // computeShiftSplit — nunca se pierden unidades en silencio.
    !allowedInA && !allowedInB -> orderRequested
    !allowedInA -> orderRequested.mapValues { 0.0 }
    !allowedInB -> orderRequested
    else -> {
        val restrictionPins = tripRestrictionPins(orderRequested, restrictedTrip)
        orderRequested.mapValues { (orderId, requested) ->
            restrictionPins[orderId]
                ?: pinned[orderId]?.coerceIn(0.0, requested)
                ?: round(requested / 2.0)
        }
    }
}

/**
 * Fija cuánto del primer viaje le toca a UNA sucursal, **sin tocar a las demás**.
 *
 * Es lo contrario de [rebalanceOrderAllocation], y la diferencia es de negocio, no
 * de cálculo: allá el total del turno está clavado y las sucursales se reparten un
 * pastel fijo, así que darle más a una se lo quita a otra. Acá manda lo que cada
 * sucursal necesita y **el total del turno se deriva sumando** — si Democracia
 * tiene un encargo de 150 franceses para la mañana, el turno de la mañana hornea
 * más franceses; Almolonga no se entera.
 *
 * El valor se acota a lo que esa sucursal pidió: no se le puede mandar en el primer
 * viaje más pan del que encargó en total.
 *
 * @return el desglose completo del turno A, con [orderId] ya actualizado.
 */
fun pinOrderAllocation(
    orderRequested: Map<String, Double>,
    currentAllocationA: Map<String, Double>,
    orderId: String,
    newValue: Double
): Map<String, Double> {
    val max = orderRequested[orderId] ?: return currentAllocationA
    return currentAllocationA + (orderId to newValue.coerceIn(0.0, max))
}

/**
 * Reconstruye el desglose por sucursal de un producto en los DOS turnos después de
 * que cambió lo pedido (se editó un pedido fuente, se agregó uno, se quitó otro),
 * preservando en lo posible el reparto manual que ya hizo la encargada.
 *
 * Una sucursal cuyo pedido de este producto **no cambió** queda fijada en la
 * cantidad que ya tenía en el turno A: si tenía un encargo y le habían dado 125 de
 * mañana, que otra sucursal edite su propio pedido no debe moverle esas 125. Las
 * que sí cambiaron (y las nuevas) absorben la diferencia proporcionalmente vía
 * [rebalanceOrderAllocation].
 *
 * [previousA] + [previousB] reconstruye lo que cada sucursal pedía ANTES: una vez
 * editado el pedido, el desglose guardado en los dos lotes hermanos es el único
 * lugar donde queda ese dato.
 *
 * El turno B siempre se deriva restando (`pedido - turno A`), así los dos turnos
 * suman exactamente lo que pidió cada sucursal, sin perder ni inventar unidades.
 *
 * @param requestedNow `orderId → cantidad pedida ahora` de este producto.
 * @param shiftTotalA unidades de este producto que produce el turno A (sin contar
 *   `plantRetainedQuantity`, que no se despacha a nadie).
 * @return `(desglose del turno A, desglose del turno B)`, sin entradas en cero.
 */
fun splitOrderAllocations(
    requestedNow: Map<String, Double>,
    previousA: Map<String, Double>,
    previousB: Map<String, Double>,
    shiftTotalA: Double,
    declaredEarly: Map<String, Double> = emptyMap(),
    restrictedTrip: Map<String, Int> = emptyMap()
): Pair<Map<String, Double>, Map<String, Double>> {
    if (requestedNow.isEmpty() || shiftTotalA < 0.0) return emptyMap<String, Double>() to emptyMap()

    val previousRequested = (previousA.keys + previousB.keys).associateWith {
        (previousA[it] ?: 0.0) + (previousB[it] ?: 0.0)
    }
    val unchanged = requestedNow.filterKeys { orderId ->
        previousRequested[orderId]?.let { it > 0.0 && it == requestedNow[orderId] } == true
    }.keys
    val pinned = unchanged.associateWith { previousA[it] ?: 0.0 }
    // Si lo ya fijado no cabe en el turno A (la encargada había concentrado casi
    // todo ahí y ahora A produce menos), no hay reparto que respete ambas cosas:
    // manda la cantidad que realmente se hornea y se reparte proporcional.
    val effectivePinned = if (pinned.values.sum() > shiftTotalA) emptyMap() else pinned

    // Un encargo declarado por la sucursal se respeta SIEMPRE, incluso cuando el
    // resto tuvo que caer a reparto proporcional: es un compromiso con un cliente,
    // no una preferencia de reparto.
    val withDeclared = effectivePinned + declaredEarly.mapValues { (orderId, qty) ->
        qty.coerceIn(0.0, requestedNow[orderId] ?: 0.0)
    }.filterKeys { it in requestedNow } +
        // Una sucursal que solo recibe en un viaje no se reparte nunca, ni siquiera
        // cuando lo demás cayó a reparto proporcional: no es una preferencia, es que
        // a esa hora no hay quién reciba el pan. Va última para ganarle al encargo.
        tripRestrictionPins(requestedNow, restrictedTrip)
    // El total del turno nunca puede ser menor que lo que los encargos comprometen.
    val effectiveTotalA = maxOf(shiftTotalA, withDeclared.values.sum())

    val allocA = rebalanceOrderAllocation(requestedNow, effectiveTotalA, withDeclared)

    val resultA = requestedNow.keys.mapNotNull { orderId ->
        val qty = allocA[orderId] ?: 0.0
        if (qty <= 0.0) null else orderId to qty
    }.toMap()
    val resultB = requestedNow.mapNotNull { (orderId, requested) ->
        val qty = (requested - (allocA[orderId] ?: 0.0)).coerceAtLeast(0.0)
        if (qty <= 0.0) null else orderId to qty
    }.toMap()
    return resultA to resultB
}

/**
 * Reasignación manual por sucursal: fija la cantidad en turno A de las órdenes en
 * [pinnedValues] (acotada a `[0, lo que pidió esa orden]`) y reparte el resto de
 * [shiftTotalA] proporcionalmente entre las órdenes NO fijadas — mismo método de
 * resto mayor (Hamilton) que [computeShiftSplit], para no perder unidades por
 * redondeo. El total de A nunca cambia: esta función solo decide DE QUIÉN son las
 * unidades, no CUÁNTAS hay en cada turno (eso ya lo decidió el balanceo por peso
 * o el ajuste manual a nivel de producto).
 */
fun rebalanceOrderAllocation(
    orderRequested: Map<String, Double>,
    shiftTotalA: Double,
    pinnedValues: Map<String, Double>
): Map<String, Double> {
    val pinned = pinnedValues.mapValues { (orderId, value) ->
        value.coerceIn(0.0, orderRequested[orderId] ?: 0.0)
    }
    val pinnedSum = pinned.values.sum()
    val remaining = (shiftTotalA - pinnedSum).coerceAtLeast(0.0)

    val freeOrders = orderRequested.keys - pinned.keys
    val freeTotalRequested = freeOrders.sumOf { orderRequested[it] ?: 0.0 }
    if (freeOrders.isEmpty() || freeTotalRequested <= 0.0) return pinned

    // La suma libre no puede pasar de lo que esas órdenes pidieron en total.
    val freeTargetA = remaining.coerceAtMost(freeTotalRequested)
    val ratio = freeTargetA / freeTotalRequested

    val rawA = freeOrders.associateWith { (orderRequested[it] ?: 0.0) * ratio }
    val flooredA = rawA.mapValues { (_, v) -> floor(v) }
    val targetSum = round(rawA.values.sum()).toLong()
    val flooredSum = flooredA.values.sumOf { it.toLong() }
    val deficit = (targetSum - flooredSum).toInt().coerceAtLeast(0)
    val remainderOf = freeOrders.associateWith { (rawA[it]!! - flooredA[it]!!) }
    val bumpSet = freeOrders
        .sortedWith(compareByDescending<String> { remainderOf[it] ?: 0.0 }.thenBy { it })
        .take(deficit)
        .toSet()

    val freeResult = freeOrders.associateWith { orderId ->
        val floorQty = flooredA[orderId] ?: 0.0
        (if (orderId in bumpSet) floorQty + 1.0 else floorQty)
            .coerceIn(0.0, orderRequested[orderId] ?: 0.0)
    }

    return pinned + freeResult
}
