package com.bakery.erp.model

/**
 * "¿Qué le compro a este proveedor, cada cuánto y cuánto?"
 *
 * Es la pregunta que el módulo no contestaba: las compras existían y se podían filtrar por
 * proveedor, pero para saber cuántas bolsas se le llevan compradas había que abrir factura por
 * factura y sumar a mano.
 *
 * Lógica pura, con tests en `SupplierPurchaseHistoryTest`.
 */

/** Un ítem comprado a un proveedor, con todo lo que se le ha comprado ya sumado. */
data class SupplierItemHistory(
    /** Clave de agrupación. Ver [supplierHistoryKey]. */
    val key: String,
    val name: String,
    /** Unidad en la que se contó. Vacía cuando la línea no declaró ninguna. */
    val unit: String,
    val kind: String,
    val totalQuantity: Double,
    val totalAmount: Double,
    /** Cuántas compras distintas lo trajeron, no cuántas líneas. */
    val timesBought: Int,
    val lastDate: Long,
    val lastUnitCost: Double
)

/**
 * Cómo se agrupan dos líneas como "la misma cosa".
 *
 * El concepto del catálogo manda sobre el texto: es justo para eso que existe. Un gasto de
 * texto libre se agrupa por su nombre normalizado, que junta "Bolsas #5" con "bolsas #5" pero
 * no con "bolsa #5" — no es magia, es lo mejor que se puede hacer sin un id, y es el argumento
 * para pegarle un concepto a lo que se repite.
 */
fun supplierHistoryKey(line: PurchaseLine): String = when {
    line.conceptId.isNotEmpty() -> "C:${line.conceptId}"
    line.lineKind == PurchaseLineKind.SERVICE -> "T:${normalizeForSearch(line.conceptName).trim()}"
    // El producto manda sobre el insumo para que la línea redirigida (la leche) se agrupe con
    // las otras compras de ese mismo renglón del catálogo. Ver `towardsLinkedIngredient`.
    line.productId.isNotEmpty() -> "P:${line.productId}"
    else -> "I:${line.ingredientId}"
}

/**
 * Historial agregado por ítem, lo más reciente primero.
 *
 * [linesByPurchase] se pasa aparte porque las líneas viven en subcolección: la lista de
 * compras del proveedor se arma sin leerlas (el encabezado ya denormaliza total y nombres) y
 * solo este resumen las necesita. Por eso en pantalla se carga cuando se pide, y no al abrir.
 */
fun buildSupplierItemHistory(
    purchases: List<Purchase>,
    linesByPurchase: Map<String, List<PurchaseLine>>
): List<SupplierItemHistory> {
    val acc = LinkedHashMap<String, SupplierItemHistory>()
    val purchaseIdsByKey = HashMap<String, MutableSet<String>>()

    purchases.sortedBy { it.date }.forEach { purchase ->
        linesByPurchase[purchase.id].orEmpty().forEach { line ->
            val key = supplierHistoryKey(line)
            if (key.endsWith(":")) return@forEach   // línea sin catálogo ni texto: nada que sumar
            val amount = line.lineTotal
            val qty = line.quantityReceived
            val previous = acc[key]
            val ids = purchaseIdsByKey.getOrPut(key) { mutableSetOf() }
            ids += purchase.id
            acc[key] = SupplierItemHistory(
                key = key,
                // El nombre y la unidad son los de la compra MÁS RECIENTE: si el concepto se
                // renombró, lo útil es cómo se llama hoy.
                name = line.itemName.ifEmpty { previous?.name.orEmpty() },
                unit = line.conceptUnit.ifEmpty { line.ingredientUnit }.ifEmpty { previous?.unit.orEmpty() },
                kind = line.lineKind,
                totalQuantity = (previous?.totalQuantity ?: 0.0) + qty,
                totalAmount = (previous?.totalAmount ?: 0.0) + amount,
                timesBought = ids.size,
                lastDate = maxOf(previous?.lastDate ?: 0L, purchase.date),
                // Se recalcula del importe y no se toma `unitCost` tal cual: una línea con
                // presentación guarda el costo por unidad ya dividido, y así las dos formas
                // de capturar dan el mismo número.
                lastUnitCost = if (qty > 0.0) amount / qty else previous?.lastUnitCost ?: 0.0
            )
        }
    }
    return acc.values.sortedWith(
        compareByDescending<SupplierItemHistory> { it.lastDate }.thenBy { it.name.lowercase() }
    )
}

/** Total comprado y saldo pendiente de un proveedor, para el encabezado de su ficha. */
data class SupplierPurchaseTotals(
    val purchaseCount: Int = 0,
    val totalAmount: Double = 0.0,
    val balanceDue: Double = 0.0,
    val lastPurchaseDate: Long = 0L
)

fun List<Purchase>.supplierTotals(): SupplierPurchaseTotals = SupplierPurchaseTotals(
    purchaseCount = size,
    totalAmount = sumOf { it.totalAmount },
    balanceDue = sumOf { it.balanceDue },
    lastPurchaseDate = maxOfOrNull { it.date } ?: 0L
)
