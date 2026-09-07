package com.bakery.erp.model

/**
 * Cuadre express ("visita sorpresa") sobre una sesión de caja OPEN.
 *
 * El owner cuenta el efectivo físico en la caja, el sistema lo compara contra el
 * efectivo esperado en ese instante y persiste la diferencia + el conteo por
 * denominaciones como evidencia inmutable de auditoría. La sesión sigue OPEN —
 * el cajero no recibe alerta visible (factor sorpresa preservado).
 *
 * Subcolección `denominations/` bajo cada doc, mismo patrón que las sesiones.
 */
data class SpotCashCheck(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val registerId: String = "",
    val registerName: String = "",
    val sessionId: String = "",
    val cashierId: String = "",
    val cashierName: String = "",
    val checkedByUserId: String = "",
    val checkedByUserName: String = "",
    val checkedAt: Long = 0L,
    /** Snapshot del expectedCash de la sesión en el momento del cuadre. */
    val expectedCashAtCheck: Double = 0.0,
    val countedCash: Double = 0.0,
    /** countedCash − expectedCashAtCheck. Negativo = faltante, positivo = sobrante. */
    val difference: Double = 0.0,
    val resolution: String = SpotCashCheckResolution.NONE,
    /** Si resolution=FORCE_ADJUST, id del CashMovement creado para alinear el expectedCash. */
    val adjustmentMovementId: String? = null,
    val notes: String = ""
)

object SpotCashCheckResolution {
    /** Solo deja constancia. No se ajusta la sesión. */
    const val NONE             = "NONE"
    /** Crea un CashMovement (DEPOSIT/WITHDRAWAL) por la diferencia para alinear expectedCash. */
    const val FORCE_ADJUST     = "FORCE_ADJUST"
    /** Marca para revisión posterior. El reporte del día lo destacará. */
    const val REQUIRES_REVIEW  = "REQUIRES_REVIEW"

    fun label(value: String) = when (value) {
        NONE            -> "Sin acción (solo constancia)"
        FORCE_ADJUST    -> "Ajustar efectivo esperado"
        REQUIRES_REVIEW -> "Marcar para revisión"
        else            -> value
    }
}
