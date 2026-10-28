package com.bakery.erp.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Ventas por grupo de precio ("Pan Q0.50") que le tocan a un conteo de cierre.
 *
 * La venta de grupo no descuenta ningún producto al cobrarse — con cola en el mostrador, pedirle
 * a la cajera que distinga francés de sheca de pirujo es lento y además da un dato malo. El
 * reparto por tipo lo da el conteo físico, que sí es un hecho: lo que falta de cada pan del
 * grupo se explica primero con estas unidades vendidas, y solo el resto es merma.
 *
 * La ventana va desde el cierre del conteo anterior de la sucursal hasta ahora: si un día no se
 * contó, sus ventas se acumulan al siguiente en vez de perderse.
 */
data class GroupSalesWindow(
    val fromMillis: Long = 0L,
    val untilMillis: Long = 0L,
    /** groupId → unidades vendidas en la ventana. */
    val unitsByGroup: Map<String, Double> = emptyMap(),
    /** groupId → pan de colaborador anotado en caja en la ventana (`CounterRemovalKind.STAFF_BREAD`). */
    val staffUnitsByGroup: Map<String, Double> = emptyMap(),
    val groupNames: Map<String, String> = emptyMap(),
    /** groupId → precio unitario del grupo, para valorar el movimiento de VENTA. */
    val groupUnitPrices: Map<String, Double> = emptyMap(),
    /** groupId → productos que el grupo reparte (de la categoría POS actual). */
    val groupProductIds: Map<String, Set<String>> = emptyMap()
) {
    val hasGroupSales: Boolean
        get() = unitsByGroup.values.any { it > 0.0 } || staffUnitsByGroup.values.any { it > 0.0 }

    /** productId → groupId, para las filas del conteo. */
    val groupOfProduct: Map<String, String>
        get() = buildMap {
            groupProductIds.forEach { (gid, pids) -> pids.forEach { if (it !in this) put(it, gid) } }
        }
}

/** Resultado del reparto de un conteo. */
data class GroupAllocation(
    /** productId → unidades del grupo que se le asignan como venta. */
    val soldByProduct: Map<String, Double> = emptyMap(),
    /** groupId → unidades vendidas en la ventana. */
    val soldByGroup: Map<String, Double> = emptyMap(),
    /** groupId → faltante total de sus panes en el conteo. */
    val shortageByGroup: Map<String, Double> = emptyMap(),
    /**
     * groupId → unidades vendidas que el conteo NO explica: se cobró más pan del grupo del que
     * falta. Pasa si llegó pan sin registrar su recepción o si el conteo está mal. No se
     * inventa un movimiento para cubrirlo — se avisa.
     */
    val unexplainedByGroup: Map<String, Double> = emptyMap(),
    /** productId → pan de colaborador que se le asigna (después de la venta). */
    val staffByProduct: Map<String, Double> = emptyMap(),
    /** groupId → pan de colaborador anotado en la ventana. */
    val staffByGroup: Map<String, Double> = emptyMap()
) {
    fun soldOf(productId: String): Double = soldByProduct[productId] ?: 0.0
    fun staffOf(productId: String): Double = staffByProduct[productId] ?: 0.0
    val totalUnexplained: Double get() = unexplainedByGroup.values.sum()
    val totalAllocated: Double get() = soldByProduct.values.sum()
    val totalStaff: Double get() = staffByProduct.values.sum()
}

/** Faltante de una línea del conteo (0 si sobra o cuadra). */
val BreadCountLine.shortage: Double
    get() = max(0.0, expectedStock - countedStock)

/**
 * Reparte las unidades vendidas de cada grupo entre sus panes, **en proporción al faltante** de
 * cada uno, sin pasarse del faltante de ninguno. Con cantidades enteras redondea por mayor
 * residuo, así el reparto suma exacto y ningún pan queda con media pieza vendida.
 *
 * Lo que no alcanza a cubrir la venta queda como merma de esa línea (`faltante − vendido`).
 *
 * [staffUnitsByGroup] (pan de colaborador anotado en caja) se reparte **después** de la venta,
 * con el mismo criterio, sobre el faltante que la venta no explicó: la venta es dinero que ya
 * está en la gaveta y va primero. Lo que de las dos no cabe en el faltante sale en
 * [GroupAllocation.unexplainedByGroup].
 */
fun allocateGroupSales(
    unitsSoldByGroup: Map<String, Double>,
    groupProductIds: Map<String, Set<String>>,
    lines: List<BreadCountLine>,
    staffUnitsByGroup: Map<String, Double> = emptyMap()
): GroupAllocation {
    val sales = allocateDemand(unitsSoldByGroup, groupProductIds, lines)
    if (staffUnitsByGroup.values.none { it > EPS }) return sales
    // Lo que la venta ya explicó deja de faltar.
    val afterSales = lines.map { it.copy(countedStock = it.countedStock + sales.soldOf(it.productId)) }
    val staff = allocateDemand(staffUnitsByGroup, groupProductIds, afterSales)
    val unexplained = (sales.unexplainedByGroup.keys + staff.unexplainedByGroup.keys).associateWith {
        (sales.unexplainedByGroup[it] ?: 0.0) + (staff.unexplainedByGroup[it] ?: 0.0)
    }
    return sales.copy(
        unexplainedByGroup = unexplained,
        staffByProduct = staff.soldByProduct,
        staffByGroup = staff.soldByGroup
    )
}

private fun allocateDemand(
    unitsSoldByGroup: Map<String, Double>,
    groupProductIds: Map<String, Set<String>>,
    lines: List<BreadCountLine>
): GroupAllocation {
    val soldByProduct = mutableMapOf<String, Double>()
    val shortageByGroup = mutableMapOf<String, Double>()
    val unexplained = mutableMapOf<String, Double>()
    val alreadyAssigned = mutableSetOf<String>()

    unitsSoldByGroup.forEach { (groupId, soldRaw) ->
        val sold = max(0.0, soldRaw)
        val pids = groupProductIds[groupId].orEmpty()
        // Un producto solo se reparte una vez aunque, por datos viejos, esté en dos grupos.
        val groupLines = lines.filter { it.productId in pids && it.productId !in alreadyAssigned }
        alreadyAssigned += groupLines.map { it.productId }
        val shortages = groupLines.associate { it.productId to it.shortage }
            .filterValues { it > 0.0 }
        val totalShortage = shortages.values.sum()
        shortageByGroup[groupId] = totalShortage

        val toAllocate = min(sold, totalShortage)
        if (sold - toAllocate > EPS) unexplained[groupId] = sold - toAllocate
        if (toAllocate <= EPS) return@forEach

        soldByProduct += distribute(toAllocate, shortages)
    }

    return GroupAllocation(
        soldByProduct = soldByProduct.filterValues { it > EPS },
        soldByGroup = unitsSoldByGroup.mapValues { max(0.0, it.value) },
        shortageByGroup = shortageByGroup,
        unexplainedByGroup = unexplained
    )
}

private const val EPS = 1e-6

private fun isWhole(v: Double) = abs(v - kotlin.math.round(v)) < EPS

/** Reparte [amount] (≤ Σ caps) en proporción a [caps] sin pasar ningún tope. */
private fun distribute(amount: Double, caps: Map<String, Double>): Map<String, Double> {
    val total = caps.values.sum()
    if (amount >= total - EPS) return caps
    val exact = caps.mapValues { (_, cap) -> amount * cap / total }
    if (!isWhole(amount) || caps.values.any { !isWhole(it) }) return exact

    // Mayor residuo: piso de cada cuota y las unidades que faltan a los residuos más grandes.
    val floors = exact.mapValues { floor(it.value + EPS) }.toMutableMap()
    var remaining = kotlin.math.round(amount - floors.values.sum()).toInt()
    // Orden estable: residuo desc, luego faltante desc, luego id — mismo resultado siempre.
    val order = exact.keys.sortedWith(
        compareByDescending<String> { exact.getValue(it) - floors.getValue(it) }
            .thenByDescending { caps.getValue(it) }
            .thenBy { it }
    )
    for (pid in order) {
        if (remaining <= 0) break
        if (floors.getValue(pid) + 1 <= caps.getValue(pid) + EPS) {
            floors[pid] = floors.getValue(pid) + 1
            remaining--
        }
    }
    return floors
}
