package com.bakery.erp.model

/**
 * Cosa que se compra seguido y **no lleva inventario de ninguna clase**: bolsas, cajas de
 * cartón, servilletas, el flete del lunes.
 *
 * Existe por una sola razón: que el historial se pueda SUMAR. La línea de servicio nació de
 * texto libre a propósito —la ferretería vende algo distinto cada vez— y para lo que no se
 * repite sigue siendo lo correcto. Pero lo que sí se repite escrito a mano son tres renglones
 * a los tres meses ("Bolsas #5", "bolsas 5", "bolsa # 5"), y entonces "¿cuántas bolsas le
 * compré este año a Plásticos del Sur?" no tiene respuesta. El concepto le pone un id estable
 * al nombre, que es lo único que faltaba.
 *
 * **No es un `Ingredient` con otra bandera.** Un insumo —aunque sea de operación— trae consigo
 * `BranchStock`, Kardex, alerta de mínimo y pantalla de ajuste: justo lo que acá se pidió NO
 * tener. Y tampoco es un `Product`: no se vende. Es un catálogo de nombres con precio de
 * referencia, y por eso su documento es plano y minúsculo.
 *
 * La línea que lo usa sigue siendo `PurchaseLineKind.SERVICE`: no mueve inventario, genera su
 * `Expense` del mes y no cambia en nada el resto del módulo. Lo único que agrega es
 * `PurchaseLine.conceptId`, que es lo que permite agrupar.
 */
data class PurchaseConcept(
    val id: String = "",
    val name: String = "",
    /**
     * En qué se cuenta: "unidad", "bolsa", "caja", "viaje". Es display y agregación, nunca
     * conversión — acá no hay stock al que convertir.
     */
    val unit: String = "unidad",
    /** Nombre de `ExpenseCategory`. Se propone al capturar; la línea guarda la suya. */
    val expenseCategory: String = "",
    /**
     * Proveedores a los que se le ha comprado. Se va llenando solo al registrar compras — no
     * es un campo que alguien mantenga. Sirve para ofrecer primero lo que ESTE proveedor
     * vende, que es el orden en que se lee una factura.
     */
    val supplierIds: List<String> = emptyList(),
    /** Último precio pagado por unidad. Se propone en la próxima captura, como el formato. */
    val lastUnitCost: Double = 0.0,
    val lastPurchaseDate: Long = 0L,
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** `true` si a [supplierId] ya se le ha comprado este concepto. */
fun PurchaseConcept.boughtFrom(supplierId: String): Boolean =
    supplierId.isNotEmpty() && supplierId in supplierIds

/**
 * Conceptos para ofrecer al capturar la factura de [supplierId]: primero los que ya se le
 * compran, después el resto, alfabéticos dentro de cada grupo.
 *
 * No se filtran los de otros proveedores: la primera vez que la ferretería trae las bolsas que
 * siempre traía el otro, el concepto tiene que estar a mano o se va a teclear de nuevo — y ahí
 * vuelve el nombre duplicado que todo esto existe para evitar.
 */
fun List<PurchaseConcept>.forSupplier(supplierId: String): List<PurchaseConcept> =
    filter { it.active }
        .sortedWith(
            compareByDescending<PurchaseConcept> { it.boughtFrom(supplierId) }
                .thenBy { it.name.lowercase() }
        )

/** Coincidencia por palabras sueltas, sin tildes ni mayúsculas. Mismo criterio que el catálogo. */
fun PurchaseConcept.matchesSearch(query: String): Boolean {
    val words = normalizeForSearch(query).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    val haystack = normalizeForSearch(name)
    return words.all { haystack.contains(it) }
}

/**
 * `true` si [name] ya existe en el catálogo (ignorando mayúsculas, tildes y espacios de más).
 *
 * Es la guarda que impide que "Bolsas #5" entre dos veces por haberla tecleado distinto: al
 * capturar se ofrece el concepto existente en vez de crear otro.
 */
fun List<PurchaseConcept>.findByName(name: String): PurchaseConcept? {
    val target = normalizeForSearch(name).trim()
    if (target.isEmpty()) return null
    return firstOrNull { normalizeForSearch(it.name).trim() == target }
}

/**
 * El concepto con lo que dejó la compra recién registrada: el proveedor al que se le compró y
 * el precio que se pagó.
 *
 * Devuelve `null` cuando nada cambió, para no escribir un documento por línea capturada. Mismo
 * criterio que `formatsWithPaidPrices`.
 */
fun PurchaseConcept.afterPurchase(
    supplierId: String,
    unitCost: Double,
    date: Long
): PurchaseConcept? {
    val addsSupplier = supplierId.isNotEmpty() && supplierId !in supplierIds
    val newCost = unitCost > 0.0 && kotlin.math.abs(unitCost - lastUnitCost) > 0.001
    val newerDate = date > lastPurchaseDate
    if (!addsSupplier && !newCost && !newerDate) return null
    return copy(
        supplierIds = if (addsSupplier) supplierIds + supplierId else supplierIds,
        lastUnitCost = if (newCost) unitCost else lastUnitCost,
        lastPurchaseDate = if (newerDate) date else lastPurchaseDate
    )
}
