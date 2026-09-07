package com.bakery.erp.presentation.purchases

import com.bakery.erp.model.AssetEventType
import com.bakery.erp.model.AssetHistoryEntry
import com.bakery.erp.model.Expense
import com.bakery.erp.model.ExpenseCategory
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseLineKind
import com.bakery.erp.model.User
import com.bakery.erp.model.lineTotal
import com.bakery.erp.model.effectiveBranchId
import com.bakery.erp.model.effectiveBranchName
import com.bakery.erp.model.itemName
import com.bakery.erp.model.purchaseLineIsExpense
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Lo que una factura de compra deja fuera del inventario: gastos del mes y eventos en el
 * historial de un activo.
 *
 * Es lógica pura a propósito —recibe la compra y devuelve documentos, sin tocar repos—
 * para que registrar, editar y borrar una compra puedan construir el mismo resultado y
 * compararlo, en vez de que cada camino escriba su propia versión. La misma razón por la
 * que la corrección de inventario se hace por diferencia contra `editingOriginalLines`.
 */

/** Período "YYYY-MM" al que pertenece la factura, en la zona del dispositivo. */
internal fun purchasePeriodOf(dateMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(dateMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
}

/**
 * Gastos que genera la factura: uno por cada línea de suministro o de servicio.
 *
 * Un gasto por línea y no uno por factura porque el reporte financiero desglosa por
 * categoría: "Q400 a la abarrotería" no dice si fue cloro o mantenimiento, y esa es
 * justamente la pregunta que el reporte existe para contestar.
 *
 * La materia prima y la reventa NO generan gasto: son inventario, y su costo entra al
 * resultado cuando se venden. Los suministros sí, aunque también entren a inventario —
 * ver [PurchaseLineKind.SUPPLY].
 */
internal fun expensesForPurchase(
    companyId: String,
    purchase: Purchase,
    lines: List<PurchaseLine>
): List<Expense> {
    val period = purchasePeriodOf(purchase.date)
    return lines.filter { purchaseLineIsExpense(it.lineKind) && it.lineTotal > 0.0 }
        .map { line ->
            Expense(
                orgId = companyId,
                // La sucursal es la de LA LÍNEA: en una vuelta al mercado los trapos del
                // restaurante no son gasto de la panadería, y el reporte financiero desglosa
                // por sucursal.
                branchId = line.effectiveBranchId(purchase),
                branchName = line.effectiveBranchName(purchase),
                name = line.itemName.ifEmpty {
                    line.placeName.ifEmpty { purchase.supplierName }
                        .let { if (it.isEmpty()) "Compra" else "Compra en $it" }
                },
                category = expenseCategoryOf(line),
                amount = line.lineTotal,
                period = period,
                notes = listOfNotNull(
                    // En una vuelta el lugar es de la línea; en una factura, del encabezado.
                    line.placeName.takeIf { it.isNotEmpty() } ?: purchase.supplierName.takeIf { it.isNotEmpty() },
                    purchase.invoiceNumber?.let { "Factura $it" }
                ).joinToString(" · ").ifEmpty { null },
                sourcePurchaseId = purchase.id,
                sourcePurchaseLineId = line.id,
                createdAt = purchase.date
            )
        }
}

/**
 * Categoría del gasto. Sin categoría declarada, un suministro es MANTENIMIENTO solo si
 * lo dijo el usuario; el default honesto es OTROS — adivinar por el nombre del insumo
 * pondría el cloro en "Servicios" y nadie entendería el reporte.
 */
private fun expenseCategoryOf(line: PurchaseLine): ExpenseCategory =
    runCatching { ExpenseCategory.valueOf(line.expenseCategory) }.getOrElse { ExpenseCategory.OTROS }

/** Evento de historial que una línea de servicio deja en su activo, si declaró uno. */
internal data class PurchaseAssetEntry(val assetId: String, val entry: AssetHistoryEntry)

/**
 * Eventos que la factura escribe en el historial de activos: el mantenimiento del horno,
 * el combustible de la camioneta.
 *
 * Solo las líneas de servicio con activo declarado. Es el mismo evento que se registraría
 * a mano en el módulo de Activos, con `sourcePurchaseId` puesto para poder rehacerlo si la
 * factura se corrige — un mantenimiento fantasma movería la próxima fecha del horno.
 */
internal fun assetEntriesForPurchase(
    purchase: Purchase,
    lines: List<PurchaseLine>,
    currentUser: User
): List<PurchaseAssetEntry> =
    lines.filter { it.lineKind == PurchaseLineKind.SERVICE && it.assetId.isNotEmpty() }
        .map { line ->
            PurchaseAssetEntry(
                assetId = line.assetId,
                entry = AssetHistoryEntry(
                    assetId = line.assetId,
                    type = runCatching { AssetEventType.valueOf(line.assetEventType) }
                        .getOrElse { AssetEventType.MAINTENANCE },
                    date = purchase.date,
                    description = line.conceptName.ifEmpty { "Servicio facturado" },
                    cost = line.lineTotal,
                    providerName = purchase.supplierName.ifEmpty { null },
                    invoiceNumber = purchase.invoiceNumber,
                    sourcePurchaseId = purchase.id,
                    createdBy = currentUser.id,
                    createdByName = currentUser.displayName.ifEmpty { currentUser.email },
                    createdAt = purchase.date
                )
            )
        }
