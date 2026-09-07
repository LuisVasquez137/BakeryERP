package com.bakery.erp.model

/**
 * Traslado de efectivo entre dos sesiones de caja abiertas en la misma sucursal,
 * típicamente cuando una caja necesita "sencillo" y la otra le entrega billetes
 * más pequeños a cambio (o le presta efectivo sin contraparte).
 *
 * El traslado se materializa como dos [CashMovement] espejo:
 *   - en `fromSessionId` → `CashMovementType.TRANSFER_OUT`
 *   - en `toSessionId`   → `CashMovementType.TRANSFER_IN`
 * Ambos llevan el mismo `transferId` y se cruzan vía `linkedSessionId`.
 *
 * En el cierre consolidado del día (sucursal), los pares OUT/IN se cancelan
 * y el neto sobre el efectivo total de la sucursal es 0 — replica el flujo
 * manual de "sumadoras espejo" que ya hacían en papel.
 */
data class CashTransfer(
    val id: String = "",
    val branchId: String = "",
    val fromSessionId: String = "",
    val fromRegisterName: String = "",
    val toSessionId: String = "",
    val toRegisterName: String = "",
    val amount: Double = 0.0,
    val reason: String = "",
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val createdAt: Long = 0L
)
