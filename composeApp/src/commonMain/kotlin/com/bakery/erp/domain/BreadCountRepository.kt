package com.bakery.erp.domain

import com.bakery.erp.domain.report.MermaSummary
import com.bakery.erp.model.BreadCount
import com.bakery.erp.model.BreadCountLine
import com.bakery.erp.model.GroupSalesWindow
import kotlinx.coroutines.flow.Flow

interface BreadCountRepository {

    /**
     * Crea (o retoma) el conteo del día para la sucursal. Si ya existe uno CLOSED
     * para esa fecha lanza [IllegalStateException]. Si existe DRAFT lo devuelve.
     * Si no existe, snapshotea las líneas desde `FinishedProductStock` con stock > 0.
     */
    suspend fun openOrResume(
        companyId: String,
        branchId: String,
        branchName: String,
        countDate: String,
        countedByUserId: String,
        countedByUserName: String
    ): BreadCount

    suspend fun getCount(companyId: String, countId: String): BreadCount?
    suspend fun getLines(companyId: String, countId: String): List<BreadCountLine>

    fun observeCount(companyId: String, countId: String): Flow<BreadCount?>
    fun observeLines(companyId: String, countId: String): Flow<List<BreadCountLine>>

    /** Conteo CLOSED del día (si existe) — para bloqueo de creación duplicada. */
    suspend fun getCountForDate(companyId: String, branchId: String, countDate: String): BreadCount?

    /** Histórico por mes (yyyy-MM). */
    suspend fun getCountsForMonth(companyId: String, branchId: String, yearMonth: String): List<BreadCount>

    /** Actualiza una sola línea (countedStock + reason). Recalcula diff y valueImpact. */
    suspend fun updateLine(companyId: String, countId: String, line: BreadCountLine)

    /**
     * Ventas por grupo de precio ("Pan Q0.50") que le tocan a este conteo: desde el cierre del
     * conteo anterior de la sucursal hasta ahora. Es lo que el cierre reparte entre los panes
     * del grupo; la pantalla lo pide para mostrar el reparto antes de cerrar.
     */
    suspend fun groupSalesWindow(companyId: String, countId: String): GroupSalesWindow

    /**
     * Cierra el conteo. El faltante de los panes de un grupo de precio se explica primero con
     * las ventas de ese grupo ([groupSalesWindow], movimiento VENTA) y solo el resto es MERMA;
     * el de los demás productos es MERMA entero. Sobrante → AJUSTE positivo.
     * Recalcula totales del header y lo marca CLOSED inmutable.
     */
    suspend fun closeCount(
        companyId: String,
        countId: String,
        notes: String?,
        currentUserId: String,
        currentUserName: String
    ): BreadCount

    /**
     * Suma la merma real (`effectiveWaste`, sin lo vendido como grupo) de todos los conteos
     * CLOSED del mes.
     * Devuelve unidades y valor agrupado por [BreadCountReason]. Si [branchId] está vacío,
     * agrega para toda la organización.
     */
    suspend fun aggregateMermasForMonth(
        companyId: String,
        branchId: String,
        yearMonth: String
    ): MermaSummary
}
