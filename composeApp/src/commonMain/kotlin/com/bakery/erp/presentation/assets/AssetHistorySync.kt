package com.bakery.erp.presentation.assets

import com.bakery.erp.domain.AssetHistoryRepository
import com.bakery.erp.domain.FixedAssetRepository
import com.bakery.erp.model.AssetEventType
import com.bakery.erp.model.AssetHistoryEntry
import com.bakery.erp.model.AssetStatus
import kotlin.time.Clock

/**
 * Lo que le pasa a un activo cuando su historial cambia.
 *
 * Vive fuera de `FixedAssetViewModel` porque ya hay dos caminos que escriben historial:
 * el formulario del activo y la línea de servicio de una factura de compra. Al que se le
 * olvidara recalcular dejaría el costo acumulado del horno mintiendo, o la próxima fecha
 * de mantenimiento sin mover. `internal` alcanza para los dos — en Kotlin es de módulo,
 * no de paquete.
 */

/** Efectos del evento sobre el activo: traslado, baja, venta, próxima fecha de servicio. */
internal suspend fun applyAssetEntrySideEffects(
    assetRepository: FixedAssetRepository,
    companyId: String,
    assetId: String,
    entry: AssetHistoryEntry
) {
    val asset = assetRepository.getAsset(companyId, assetId) ?: return
    var updated = asset
    when (entry.type) {
        AssetEventType.TRANSFER -> {
            if (!entry.toBranchId.isNullOrEmpty()) {
                updated = updated.copy(
                    branchId = entry.toBranchId,
                    branchName = entry.toBranchName ?: ""
                )
            }
        }
        AssetEventType.DISPOSAL -> updated = updated.copy(status = AssetStatus.DISPOSED)
        AssetEventType.SALE -> updated = updated.copy(status = AssetStatus.SOLD)
        AssetEventType.MAINTENANCE -> {
            if (entry.nextMaintenanceDate != null) {
                updated = updated.copy(nextMaintenanceDate = entry.nextMaintenanceDate)
            }
        }
        else -> { /* sin efecto */ }
    }
    if (updated != asset) assetRepository.saveAsset(companyId, updated)
}

/**
 * Recalcula los contadores denormalizados del activo leyendo su historial completo.
 *
 * Se recalcula desde la subcolección en vez de sumar el evento nuevo sobre el valor
 * guardado, por el mismo motivo que `recalculateBalance` en compras: un borrado o un
 * doble toque dejarían el acumulado corrido para siempre.
 */
internal suspend fun recalcAndPersistAssetCounters(
    assetRepository: FixedAssetRepository,
    historyRepository: AssetHistoryRepository,
    companyId: String,
    assetId: String
) {
    val asset = assetRepository.getAsset(companyId, assetId) ?: return
    val history = historyRepository.getHistory(companyId, assetId)
    assetRepository.saveAsset(
        companyId,
        asset.copy(
            totalMaintenanceCost = history.filter { it.type == AssetEventType.MAINTENANCE }.sumOf { it.cost },
            totalRepairCost = history.filter { it.type == AssetEventType.REPAIR }.sumOf { it.cost },
            totalUpgradeCost = history.filter { it.type == AssetEventType.UPGRADE }.sumOf { it.cost },
            historyCount = history.size,
            updatedAt = Clock.System.now().toEpochMilliseconds()
        )
    )
}
