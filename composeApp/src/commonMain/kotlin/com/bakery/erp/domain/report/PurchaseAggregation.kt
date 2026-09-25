package com.bakery.erp.domain.report

import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseAmountSplit
import com.bakery.erp.model.PurchaseStatus
import com.bakery.erp.model.amountSplit
import com.bakery.erp.model.balanceDue
import com.bakery.erp.model.isQuick

/**
 * Agrega las compras de un rango para el reporte financiero.
 *
 * Es lógica pura —recibe la lista ya leída y devuelve el resumen— porque el criterio que
 * encierra es el que hay que poder probar sin Firestore de por medio: qué se cuenta como
 * salida de dinero y, sobre todo, **qué no se cuenta dos veces**.
 *
 * Se filtra en memoria y no en el servidor a propósito, igual que `PurchaseFilter`: las
 * compras viejas pueden traer `date` como `Timestamp` y las nuevas lo escriben como número,
 * y Firestore ordena por tipo antes que por valor — un `where date >= x` numérico dejaría
 * fuera en silencio justo las facturas más antiguas.
 */
fun aggregatePurchases(
    purchases: List<Purchase>,
    fromMillis: Long,
    untilExclusiveMillis: Long
): PurchaseSummary {
    val inRange = purchases.filter { it.date in fromMillis until untilExclusiveMillis }
    if (inRange.isEmpty()) return PurchaseSummary()

    // Una compra "por recibir" no ha entrado a la bodega ni generó gasto todavía: contarla
    // como salida del mes mostraría dinero que no se movió. Misma regla que sigue el
    // inventario, que tampoco la aplica hasta recibirla.
    val effective = inRange.filter { it.status != PurchaseStatus.PENDING_RECEIPT }

    val split = effective.fold(PurchaseAmountSplit()) { acc, p -> acc + p.amountSplit() }

    return PurchaseSummary(
        purchasesCount = effective.size,
        invoiceCount = effective.count { !it.isQuick },
        quickCount = effective.count { it.isQuick },
        total = effective.sumOf { it.totalAmount },
        inventoryTotal = split.inventory,
        expensedTotal = split.expensed,
        unclassifiedTotal = split.unclassified,
        paidTotal = effective.sumOf { it.paidAmount },
        pendingTotal = effective.sumOf { it.balanceDue }
    )
}
