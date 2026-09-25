package com.bakery.erp.model

/**
 * Cómo se reparte el monto de una compra entre lo que es INVENTARIO y lo que ya es GASTO.
 *
 * Existe por una razón muy concreta: el reporte financiero no puede sumar las compras enteras
 * a los egresos sin contar dos veces. Las líneas de suministro y de servicio **ya generaron un
 * `Expense`** (ver `expensesForPurchase`), así que ese pedazo del total ya está adentro de
 * `ExpenseSummary`. Lo que falta en el reporte —y es la mayor salida de efectivo de una
 * panadería— es el otro pedazo: la harina y el azúcar, que hoy no aparecen en ningún lado
 * porque son inventario hasta que se venden.
 *
 * Se guarda denormalizado en el encabezado en vez de calcularse leyendo las líneas de cada
 * compra: el reporte recorre un mes entero y abrir la subcolección de cada factura sería una
 * lectura por fila. Mismo criterio que `totalAmount` e `itemNames`.
 */
data class PurchaseAmountSplit(
    /** Materia prima y reventa: entra a inventario, su costo pega al vender. */
    val inventory: Double = 0.0,
    /** Suministros y servicios: ya generaron un `Expense` del mes. */
    val expensed: Double = 0.0,
    /**
     * Monto que no se pudo repartir. Solo aparece en compras registradas ANTES de que el
     * desglose existiera y que mezclan inventario con gasto en el mismo documento.
     *
     * Se deja aparte en vez de mandarlo a uno de los dos lados porque adivinar acá significa
     * mentir en el número que el reporte existe para contestar. Es preferible que el usuario
     * vea "Q80 sin desglosar" a que vea un total redondo que está mal.
     */
    val unclassified: Double = 0.0
) {
    val total: Double get() = inventory + expensed + unclassified

    operator fun plus(other: PurchaseAmountSplit) = PurchaseAmountSplit(
        inventory = inventory + other.inventory,
        expensed = expensed + other.expensed,
        unclassified = unclassified + other.unclassified
    )
}

/** `true` si el tipo de línea entra a algún inventario en vez de gastarse de una vez. */
fun purchaseLineIsInventory(kind: String): Boolean =
    kind == PurchaseLineKind.INGREDIENT || kind == PurchaseLineKind.RESALE_PRODUCT

/** Desglose exacto, calculado desde las líneas. Es el que se denormaliza al guardar. */
fun List<PurchaseLine>.amountSplit(): PurchaseAmountSplit {
    var inventory = 0.0
    var expensed = 0.0
    forEach { line ->
        if (purchaseLineIsInventory(line.lineKind)) inventory += line.lineTotal
        else expensed += line.lineTotal
    }
    return PurchaseAmountSplit(inventory = inventory, expensed = expensed)
}

/**
 * Desglose de una compra ya guardada, sin abrir sus líneas.
 *
 * Usa los campos denormalizados cuando están; si no, deduce del encabezado:
 *
 * - **Compra vieja sin `lineKinds`**: es materia prima por definición — era lo único que el
 *   módulo sabía registrar antes de los cuatro tipos de línea. Ésa es la mayoría del
 *   histórico y queda bien clasificada sin necesidad de migrar nada.
 * - **Un solo grupo de tipos**: si todos los tipos presentes son de inventario (o todos de
 *   gasto), el total entero va a ese lado.
 * - **Mezclada y sin desglose guardado**: cae en [PurchaseAmountSplit.unclassified].
 */
fun Purchase.amountSplit(): PurchaseAmountSplit {
    val denormalized = inventoryAmount + expensedAmount
    // Se compara contra el total con tolerancia de centavos: el desglose se guarda al mismo
    // tiempo que `totalAmount` y de la misma fuente, así que si no cuadra es que la compra es
    // anterior al campo y lo que hay guardado son ceros.
    if (denormalized > PURCHASE_BALANCE_EPSILON) {
        return PurchaseAmountSplit(inventory = inventoryAmount, expensed = expensedAmount)
    }
    if (totalAmount <= 0.0) return PurchaseAmountSplit()

    val kinds = effectiveLineKinds
    val hasInventory = kinds.any { purchaseLineIsInventory(it) }
    val hasExpensed = kinds.any { !purchaseLineIsInventory(it) }
    return when {
        hasInventory && !hasExpensed -> PurchaseAmountSplit(inventory = totalAmount)
        hasExpensed && !hasInventory -> PurchaseAmountSplit(expensed = totalAmount)
        else -> PurchaseAmountSplit(unclassified = totalAmount)
    }
}
