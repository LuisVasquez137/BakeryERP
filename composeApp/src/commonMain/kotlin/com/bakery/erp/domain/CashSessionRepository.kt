package com.bakery.erp.domain

import com.bakery.erp.model.CashMovement
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.CashTransfer
import kotlinx.coroutines.flow.Flow

/** Tipo de conjunto de denominaciones asociado a una sesión. */
enum class CashDenominationStage { OPENING, CLOSING }

interface CashSessionRepository {

    /** Crea una sesión OPEN y devuelve la persistida con id. Si ya hay otra OPEN en la misma
     *  caja lanza [IllegalStateException]. */
    suspend fun openSession(companyId: String, session: CashSession): CashSession

    suspend fun getSession(companyId: String, sessionId: String): CashSession?

    /** Sesión OPEN actual de la caja [registerId], o null si no hay. */
    suspend fun getOpenSessionForRegister(companyId: String, registerId: String): CashSession?

    /** Todas las sesiones (abiertas + cerradas) de una caja específica. Para validar borrado. */
    suspend fun getSessionsForRegister(companyId: String, registerId: String): List<CashSession>

    fun observeSession(companyId: String, sessionId: String): Flow<CashSession?>

    /** Histórico de sesiones (cerradas + abiertas) por sucursal, ordenadas por openedAt desc. */
    suspend fun getSessionsForBranch(
        companyId: String,
        branchId: String,
        fromMillis: Long? = null,
        untilMillis: Long? = null
    ): List<CashSession>

    fun observeOpenSessions(companyId: String): Flow<List<CashSession>>

    /**
     * Incrementa los totales denormalizados de la sesión al confirmar una venta.
     * Lee la sesión y la re-escribe con totales sumados — no es atómico contra concurrencia,
     * pero es seguro porque cada sesión tiene un solo cajero a la vez.
     */
    suspend fun incrementSaleTotals(
        companyId: String,
        sessionId: String,
        method: String,
        amount: Double
    )

    suspend fun addMovement(companyId: String, movement: CashMovement)
    suspend fun listMovements(companyId: String, sessionId: String): List<CashMovement>

    /** Cierra la sesión: calcula expectedCash, persiste countedCash + difference + closedAt + status=CLOSED. */
    suspend fun closeSession(
        companyId: String,
        sessionId: String,
        countedCash: Double,
        notes: String?
    ): CashSession

    /**
     * Persiste el desglose por denominaciones (apertura o cierre).
     * Reemplaza completamente el conjunto existente para [stage].
     * `denominations` es `denomCode → quantity` (qty ≤ 0 se omiten).
     */
    suspend fun saveDenominations(
        companyId: String,
        sessionId: String,
        stage: CashDenominationStage,
        denominations: Map<String, Int>
    )

    /** Lee el desglose persistido (vacío si no se guardó nunca). */
    suspend fun getDenominations(
        companyId: String,
        sessionId: String,
        stage: CashDenominationStage
    ): Map<String, Int>

    /**
     * Crea un traslado de efectivo entre dos sesiones OPEN de la misma sucursal:
     * persiste el [CashTransfer] y los dos `CashMovement` espejo (TRANSFER_OUT en origen,
     * TRANSFER_IN en destino). Lanza [IllegalStateException] si las sesiones no aplican.
     */
    suspend fun transferBetweenSessions(
        companyId: String,
        transfer: CashTransfer
    ): CashTransfer

    /** Sesiones abiertas de una sucursal (helper para selector de transfer / dailyClose). */
    suspend fun getOpenSessionsForBranch(companyId: String, branchId: String): List<CashSession>

    /**
     * Crea un CashMovement de ajuste asociado a un cuadre sorpresa. Si `signedAmount > 0`
     * crea un DEPOSIT (entra efectivo), si `< 0` crea un WITHDRAWAL (sale efectivo).
     * Devuelve el id del movimiento creado.
     */
    suspend fun applySpotCheckAdjustment(
        companyId: String,
        sessionId: String,
        signedAmount: Double,
        spotCheckId: String,
        currentUserId: String,
        currentUserName: String
    ): String

    /** Marca una sesión como incluida en un cierre del día (no la modifica de otra forma). */
    suspend fun markSessionDailyClose(companyId: String, sessionId: String, dailyCloseId: String?)
}
