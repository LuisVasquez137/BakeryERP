package com.bakery.erp.model

/**
 * Conteo físico del pan restante al cierre del día por sucursal. Detecta
 * mermas/pérdidas no contabilizadas: vencido, dañado, regalo, robo, desconocido.
 *
 * Al cerrar, por cada línea con diferencia se dispara automáticamente el
 * movimiento al [FinishedStockRepository]:
 *   - diff < 0 (faltante) → MERMA con la razón seleccionada
 *   - diff > 0 (sobrante) → AJUSTE positivo
 *
 * Una vez CLOSED es inmutable. Solo se permite un conteo CLOSED por sucursal
 * por día calendario.
 */
data class BreadCount(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    /** yyyy-MM-dd en TZ local. */
    val countDate: String = "",
    val countedByUserId: String = "",
    val countedByUserName: String = "",
    val status: String = BreadCountStatus.DRAFT,
    /** Suma de |valueImpact| de líneas con diff<0. Costo de la merma del día. */
    val totalShortageValue: Double = 0.0,
    /** Suma de valueImpact de líneas con diff>0. */
    val totalSurplusValue: Double = 0.0,
    val lineCount: Int = 0,
    val notes: String? = null,
    /**
     * Unidades de venta por grupo de precio ("Pan Q0.50") que este conteo repartió como VENTA
     * entre los panes del grupo. Ver `allocateGroupSales`.
     */
    val groupSalesAllocated: Double = 0.0,
    /** Vendido como grupo que el conteo no explica (se cobró más pan del que falta). */
    val unexplainedGroupUnits: Double = 0.0,
    /** Ventana de ventas de grupo que cubrió el cierre. 0 en conteos viejos. */
    val salesWindowFrom: Long = 0L,
    val salesWindowUntil: Long = 0L,
    val createdAt: Long = 0L,
    val closedAt: Long? = null
)

object BreadCountStatus {
    const val DRAFT  = "DRAFT"
    const val CLOSED = "CLOSED"

    fun label(status: String) = when (status) {
        DRAFT  -> "Borrador"
        CLOSED -> "Cerrado"
        else   -> "Desconocido"
    }
}
