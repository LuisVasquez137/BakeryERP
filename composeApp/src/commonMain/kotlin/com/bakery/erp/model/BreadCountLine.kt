package com.bakery.erp.model

/**
 * Línea del conteo de pan: un producto contado. La diferencia y el impacto
 * económico se persisten denormalizados para reporte sin recalcular.
 */
data class BreadCountLine(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    /** Stock esperado al momento de iniciar el conteo (snapshot de FinishedProductStock). */
    val expectedStock: Double = 0.0,
    /** Conteo físico real. */
    val countedStock: Double = 0.0,
    /** countedStock − expectedStock. Negativo = faltante, positivo = sobrante. */
    val difference: Double = 0.0,
    /** Solo requerido si difference != 0 (faltante: razón; sobrante: AJUSTE). */
    val reason: String = "",
    val reasonNote: String = "",
    /** Snapshot de avgUnitCost al iniciar conteo. Permite valorar la merma. */
    val unitCostSnapshot: Double = 0.0,
    /** difference × unitCostSnapshot. Negativo si faltante. */
    val valueImpact: Double = 0.0,
    /**
     * Del faltante, cuánto se vendió como grupo de precio ("Pan Q0.50"). Se fija al cerrar.
     */
    val soldAsGroup: Double = 0.0,
    /**
     * Del faltante, cuánto fue pan de colaborador anotado en caja (sin tipo, por grupo). Se fija
     * al cerrar. Ver `allocateGroupSales`.
     */
    val staffBread: Double = 0.0,
    /**
     * Merma real de la línea: faltante − [soldAsGroup] − [staffBread]. `null` en conteos anteriores al
     * reparto por grupo, donde todo faltante era merma ([effectiveWaste] cae a eso).
     */
    val wasteQuantity: Double? = null
)

/** Unidades de merma de la línea, para reportes. */
val BreadCountLine.effectiveWaste: Double
    get() = wasteQuantity ?: if (difference < 0) -difference else 0.0

/**
 * Razones para una línea con faltante. Se persisten como el string del enum (`name`)
 * para deserialización resiliente.
 */
object BreadCountReason {
    const val VENCIDO     = "VENCIDO"
    const val DANADO      = "DANADO"
    const val REGALO      = "REGALO"
    const val ROBO        = "ROBO"
    const val DESCONOCIDO = "DESCONOCIDO"
    const val OTRO        = "OTRO"
    /** Diferencia positiva → ajuste por sobrante. */
    const val SOBRANTE    = "SOBRANTE"

    val SHORTAGE_REASONS = listOf(VENCIDO, DANADO, REGALO, ROBO, DESCONOCIDO, OTRO)

    fun label(reason: String) = when (reason) {
        VENCIDO     -> "Vencido"
        DANADO      -> "Dañado"
        REGALO      -> "Regalo / cortesía"
        ROBO        -> "Robo / sustracción"
        DESCONOCIDO -> "Desconocido"
        OTRO        -> "Otro"
        SOBRANTE    -> "Sobrante"
        else        -> reason
    }
}
