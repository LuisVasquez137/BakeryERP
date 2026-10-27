package com.bakery.erp.domain

import com.bakery.erp.model.CashSession
import com.bakery.erp.model.DailyCashClose
import kotlinx.coroutines.flow.Flow

/**
 * Snapshot calculado del cierre del día (no persistido aún).
 * Útil para que la UI muestre totales antes de elegir leftForNextDay/depositedAmount.
 */
data class DailyCashCloseDraft(
    val branchId: String,
    val branchName: String,
    val closeDate: String,
    val sessions: List<CashSession>,
    val totalCashSales: Double,
    val totalCardSales: Double,
    val totalTransferSales: Double,
    val totalPedidosYaSales: Double = 0.0,
    val totalSales: Double,
    val totalMovementsIn: Double,
    val totalMovementsOut: Double,
    /** Ver [com.bakery.erp.model.dayCashTotals]: esperado/contado al final del día, no suma de turnos. */
    val totalExpectedCash: Double,
    val totalCountedCash: Double,
    val totalDifference: Double,
    val hasOpenSessions: Boolean,
    /** Salió al cerrar turnos (al banco o retiro): ya no está en las gavetas. */
    val takenAtShiftClose: Double = 0.0,
    /** Turnos del día que siguen abiertos: el resumen los muestra, pero sus números aún cambian. */
    val openSessions: List<CashSession> = emptyList()
)

interface DailyCashCloseRepository {

    /**
     * Construye el draft consolidado a partir de las sesiones del día y sucursal.
     * No persiste nada. Si hay sesiones OPEN en el día marca [hasOpenSessions] = true.
     */
    suspend fun buildDraft(
        companyId: String,
        branchId: String,
        branchName: String,
        closeDate: String
    ): DailyCashCloseDraft

    suspend fun getClose(companyId: String, closeId: String): DailyCashClose?

    /** Cierre del día (si existe) — usado para bloqueo y para reporte del owner. */
    suspend fun getCloseForDate(companyId: String, branchId: String, closeDate: String): DailyCashClose?

    fun observeCloseForDate(companyId: String, branchId: String, closeDate: String): Flow<DailyCashClose?>

    suspend fun getClosesForMonth(companyId: String, branchId: String, yearMonth: String): List<DailyCashClose>

    /** Denominaciones persistidas en el cierre (lectura). */
    suspend fun getLeftDenominations(companyId: String, closeId: String): Map<String, Int>
    suspend fun getDepositDenominations(companyId: String, closeId: String): Map<String, Int>

    /**
     * Cierra el día por sucursal:
     *  - Bloquea si hay sesiones OPEN.
     *  - Bloquea si ya hay un DailyCashClose CLOSED para esa fecha.
     *  - Persiste el header + ambas subcolecciones de denominaciones.
     *  - Marca todas las sesiones incluidas con `dailyCloseId`.
     */
    suspend fun closeDay(
        companyId: String,
        draft: DailyCashCloseDraft,
        leftDenominations: Map<String, Int>,
        depositDenominations: Map<String, Int>,
        notes: String?,
        currentUserId: String,
        currentUserName: String
    ): DailyCashClose
}
