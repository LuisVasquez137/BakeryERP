package com.bakery.erp.presentation.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.CashDenominationStage
import com.bakery.erp.domain.CashRegisterRepository
import com.bakery.erp.domain.CashSessionRepository
import com.bakery.erp.domain.DailyCashCloseRepository
import com.bakery.erp.model.CashMovementType
import com.bakery.erp.model.ADJUSTMENT_PREFIX_DELETED
import com.bakery.erp.model.ADJUSTMENT_PREFIX_CORRECTED
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.CashMovement
import com.bakery.erp.model.CashRegister
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.CashSessionStatus
import com.bakery.erp.model.CashTransfer
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import com.bakery.erp.domain.PurchaseRepository
import com.bakery.erp.model.Purchase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

data class CashSessionUiState(
    val branches: List<Branch> = emptyList(),
    val selectedBranchId: String = "",
    val registers: List<CashRegister> = emptyList(),
    val openSessionsByRegister: Map<String, CashSession> = emptyMap(),
    val currentSession: CashSession? = null,
    val movements: List<CashMovement> = emptyList(),
    /**
     * Compra detrás de cada gasto (`CashMovement.sourcePurchaseId`). El movimiento solo guarda un
     * texto; lo que dice QUÉ se compró y la foto del comprobante vive en la compra, y al cerrar
     * la caja es lo que alguien necesita ver para aceptar que el faltante está explicado.
     */
    val movementPurchases: Map<String, Purchase> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Aviso que NO es un error: hoy, las cajas que el sistema cerró solo. */
    val message: String? = null,
    val openedSessionId: String? = null,
    val isClosed: Boolean = false,
    /** Denominaciones esperadas para la apertura (heredadas del cierre del día anterior). */
    val expectedOpeningDenoms: Map<String, Int> = emptyMap(),
    /** Denominaciones ya persistidas para esta sesión (al observar). */
    val openingDenoms: Map<String, Int> = emptyMap(),
    val closingDenoms: Map<String, Int> = emptyMap(),
    /** Cajas abiertas en la sucursal del usuario actual — selector para transferencia. */
    val openSessionsForTransfer: List<CashSession> = emptyList(),
    val transferDone: Boolean = false
)

class CashSessionViewModel(
    private val sessionRepository: CashSessionRepository,
    private val registerRepository: CashRegisterRepository,
    private val branchRepository: BranchRepository,
    private val dailyCloseRepository: DailyCashCloseRepository,
    private val purchaseRepository: PurchaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashSessionUiState())
    val uiState: StateFlow<CashSessionUiState> = _uiState.asStateFlow()

    private var openSessionsJob: Job? = null
    private var sessionObserveJob: Job? = null

    fun loadBranches(companyId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                branchRepository.getBranches(companyId)
                    .filter { it.active }
                    .filter { currentUser.canAccessBranch(it.id) }
                    .filter { BranchCapability.CAN_SELL in it.capabilities }
                    .sortedBy { it.name }
            }.onSuccess { branches ->
                val current = _uiState.value.selectedBranchId
                val nextSelected = if (current.isNotEmpty() && branches.any { it.id == current }) current
                                   else branches.firstOrNull()?.id.orEmpty()
                _uiState.update {
                    it.copy(isLoading = false, branches = branches, selectedBranchId = nextSelected)
                }
                if (nextSelected.isNotEmpty()) loadRegistersForBranch(companyId, nextSelected)
                // ANTES de observar: una caja que nadie cerró ayer bloquea su registradora y
                // arrastra las ventas de ayer al cuadre de hoy. Si no se limpia acá, el
                // usuario ve la caja "ocupada" y no puede abrir la suya.
                autoCloseStaleSessions(companyId)
                observeOpenSessions(companyId)
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun selectBranch(companyId: String, branchId: String) {
        if (branchId == _uiState.value.selectedBranchId) return
        _uiState.update { it.copy(selectedBranchId = branchId, registers = emptyList()) }
        loadRegistersForBranch(companyId, branchId)
    }

    private fun loadRegistersForBranch(companyId: String, branchId: String) {
        viewModelScope.launch {
            runCatching {
                registerRepository.getForBranch(companyId, branchId).filter { it.active }
            }.onSuccess { regs ->
                _uiState.update { it.copy(registers = regs) }
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message) }
            }
        }
    }

    /**
     * Cierra lo que quedó abierto de días anteriores, asumiendo pago exacto.
     *
     * Se dispara al entrar al POS y no por reloj porque **no hay servidor que lo haga**: la
     * app es el único que corre. Eso implica que una caja abandonada sigue abierta hasta que
     * alguien entre al punto de venta — es tarde, pero es antes de que estorbe, que es el
     * momento en que importa. Un job en Cloud Functions lo haría a hora fija; queda pendiente.
     *
     * Nunca lanza: que falle no puede impedir abrir caja, que es a lo que el usuario vino.
     */
    private fun autoCloseStaleSessions(companyId: String) {
        viewModelScope.launch {
            val todayStart = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
                .atStartOfDayIn(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
            val closed = runCatching {
                sessionRepository.autoCloseStaleSessions(companyId, todayStart)
            }.getOrDefault(0)
            if (closed > 0) {
                _uiState.update {
                    it.copy(
                        message = if (closed == 1) {
                            "Se cerró 1 caja que quedó abierta de un día anterior. No se contó " +
                                "el efectivo: se asumió pago exacto."
                        } else {
                            "Se cerraron $closed cajas que quedaron abiertas de días anteriores. " +
                                "No se contó el efectivo: se asumió pago exacto."
                        }
                    )
                }
            }
        }
    }

    private fun observeOpenSessions(companyId: String) {
        openSessionsJob?.cancel()
        openSessionsJob = viewModelScope.launch {
            sessionRepository.observeOpenSessions(companyId)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { open ->
                    _uiState.update {
                        it.copy(openSessionsByRegister = open.associateBy { s -> s.registerId })
                    }
                }
        }
    }

    fun openSession(
        companyId: String,
        branch: Branch,
        register: CashRegister,
        currentUser: User,
        openingBalance: Double,
        openingDenominations: Map<String, Int> = emptyMap()
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, openedSessionId = null) }
            runCatching {
                val session = sessionRepository.openSession(
                    companyId,
                    CashSession(
                        branchId = branch.id,
                        branchName = branch.name,
                        registerId = register.id,
                        registerName = register.name,
                        cashierId = currentUser.id,
                        cashierName = currentUser.displayName,
                        openedAt = Clock.System.now().toEpochMilliseconds(),
                        openingBalance = openingBalance
                    )
                )
                if (openingDenominations.isNotEmpty()) {
                    sessionRepository.saveDenominations(
                        companyId, session.id, CashDenominationStage.OPENING, openingDenominations
                    )
                }
                session
            }.onSuccess { session ->
                _uiState.update { it.copy(isLoading = false, openedSessionId = session.id) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    /**
     * Carga el desglose por denominaciones esperado para la apertura: lee el último
     * `DailyCashClose` CLOSED de la sucursal y devuelve sus `leftDenoms`. Vacío si
     * la sucursal no tiene cierres previos.
     */
    fun loadExpectedOpeningDenoms(companyId: String, branchId: String) {
        viewModelScope.launch {
            runCatching {
                // Buscar el cierre más reciente: revisamos hasta 7 días atrás
                val tz = TimeZone.currentSystemDefault()
                val today = Clock.System.now().toLocalDateTime(tz).date
                var result: Map<String, Int> = emptyMap()
                for (offset in 1..7) {
                    val day = today.minus(DatePeriod(days = offset))
                    val key = "${day.year.toString().padStart(4, '0')}-" +
                        "${day.monthNumber.toString().padStart(2, '0')}-" +
                        "${day.dayOfMonth.toString().padStart(2, '0')}"
                    val close = dailyCloseRepository.getCloseForDate(companyId, branchId, key)
                    if (close != null) {
                        result = dailyCloseRepository.getLeftDenominations(companyId, close.id)
                        break
                    }
                }
                result
            }.onSuccess { denoms ->
                _uiState.update { it.copy(expectedOpeningDenoms = denoms) }
            }.onFailure { /* silencioso — no es bloqueante */ }
        }
    }

    /** Observe la sesión + movimientos para las pantallas de detalle/cierre. */
    fun observeSession(companyId: String, sessionId: String) {
        sessionObserveJob?.cancel()
        sessionObserveJob = viewModelScope.launch {
            sessionRepository.observeSession(companyId, sessionId)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { session ->
                    _uiState.update { it.copy(currentSession = session) }
                }
        }
        viewModelScope.launch {
            runCatching { sessionRepository.listMovements(companyId, sessionId) }
                .onSuccess { movs ->
                    _uiState.update { it.copy(movements = movs) }
                    loadMovementPurchases(companyId, movs)
                }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    /**
     * Una lectura por compra distinta, en paralelo. Son pocas por turno, y un fallo deja el
     * renglón con el texto del movimiento en vez de tumbar la pantalla de cierre.
     */
    private suspend fun loadMovementPurchases(companyId: String, movements: List<CashMovement>) {
        val ids = movements.map { it.sourcePurchaseId }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) {
            _uiState.update { it.copy(movementPurchases = emptyMap()) }
            return
        }
        val found = coroutineScope {
            ids.map { id -> async { runCatching { purchaseRepository.getPurchase(companyId, id) }.getOrNull() } }
                .awaitAll()
        }.filterNotNull().associateBy { it.id }
        _uiState.update { it.copy(movementPurchases = found) }
    }

    fun addMovement(
        companyId: String, sessionId: String, type: String, amount: Double, reason: String, currentUser: User
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                sessionRepository.addMovement(
                    companyId,
                    CashMovement(
                        sessionId = sessionId,
                        type = type,
                        amount = amount,
                        reason = reason.trim(),
                        createdByUserId = currentUser.id,
                        createdByUserName = currentUser.displayName
                    )
                )
                sessionRepository.listMovements(companyId, sessionId)
            }.onSuccess { movs ->
                _uiState.update { it.copy(isLoading = false, movements = movs) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    /**
     * Corrige el monto de un movimiento anotado a mano, o lo revierte.
     *
     * **No reescribe el original: escribe la diferencia**, igual que la corrección de un gasto
     * y que `applyStockDelta` con el inventario. Un movimiento reescrito borraría del historial
     * de la caja que el monto cambió, que es justo lo que alguien quiere ver cuando el cuadre
     * no da — y el repositorio no expone editar ni borrar movimientos, a propósito.
     *
     * [newAmount] en 0 revierte el movimiento entero.
     */
    fun adjustMovement(
        companyId: String,
        sessionId: String,
        original: CashMovement,
        newAmount: Double,
        currentUser: User
    ) {
        val delta = newAmount - original.amount
        // Centavos de redondeo no son una corrección.
        if (kotlin.math.abs(delta) < 0.01) return
        val isDeletion = newAmount <= 0.0
        // El ajuste va en el sentido CONTRARIO al original cuando baja, y en el mismo cuando
        // sube: así el neto termina siendo exactamente el monto nuevo.
        val originalSign = CashMovementType.sign(original.type)
        val adjustmentGoesUp = delta > 0
        val type = when {
            originalSign > 0 && adjustmentGoesUp -> CashMovementType.DEPOSIT
            originalSign > 0 -> CashMovementType.WITHDRAWAL
            adjustmentGoesUp -> CashMovementType.WITHDRAWAL
            else -> CashMovementType.DEPOSIT
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                sessionRepository.addMovement(
                    companyId,
                    CashMovement(
                        sessionId = sessionId,
                        type = type,
                        amount = kotlin.math.abs(delta),
                        reason = if (isDeletion) "$ADJUSTMENT_PREFIX_DELETED ${original.reason}"
                                 else "$ADJUSTMENT_PREFIX_CORRECTED ${original.reason}",
                        createdByUserId = currentUser.id,
                        createdByUserName = currentUser.displayName.ifEmpty { currentUser.email },
                        sourceMovementId = original.id
                    )
                )
                sessionRepository.listMovements(companyId, sessionId)
            }.onSuccess { movs ->
                _uiState.update { it.copy(isLoading = false, movements = movs) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun closeSession(
        companyId: String,
        sessionId: String,
        countedCash: Double,
        notes: String?,
        closingDenominations: Map<String, Int> = emptyMap()
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isClosed = false) }
            runCatching {
                val closed = sessionRepository.closeSession(companyId, sessionId, countedCash, notes)
                if (closingDenominations.isNotEmpty()) {
                    sessionRepository.saveDenominations(
                        companyId, sessionId, CashDenominationStage.CLOSING, closingDenominations
                    )
                }
                closed
            }.onSuccess { closed ->
                _uiState.update { it.copy(isLoading = false, currentSession = closed, isClosed = true) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    /** Persistencia parcial del desglose en una sesión en curso (apertura/cierre). */
    fun saveSessionDenominations(
        companyId: String,
        sessionId: String,
        stage: CashDenominationStage,
        denominations: Map<String, Int>
    ) {
        viewModelScope.launch {
            runCatching {
                sessionRepository.saveDenominations(companyId, sessionId, stage, denominations)
            }.onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun loadSessionDenominations(companyId: String, sessionId: String) {
        viewModelScope.launch {
            runCatching {
                val opening = sessionRepository.getDenominations(
                    companyId, sessionId, CashDenominationStage.OPENING
                )
                val closing = sessionRepository.getDenominations(
                    companyId, sessionId, CashDenominationStage.CLOSING
                )
                opening to closing
            }.onSuccess { (opening, closing) ->
                _uiState.update { it.copy(openingDenoms = opening, closingDenoms = closing) }
            }.onFailure { /* silencioso */ }
        }
    }

    /** Cajas abiertas en la misma sucursal (excluye la actual) para transferir efectivo. */
    fun loadOpenSessionsForTransfer(companyId: String, branchId: String, excludeSessionId: String) {
        viewModelScope.launch {
            runCatching {
                sessionRepository.getOpenSessionsForBranch(companyId, branchId)
                    .filter { it.id != excludeSessionId }
            }.onSuccess { sessions ->
                _uiState.update { it.copy(openSessionsForTransfer = sessions) }
            }.onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun transferToSession(
        companyId: String,
        fromSession: CashSession,
        toSession: CashSession,
        amount: Double,
        reason: String,
        currentUser: User
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, transferDone = false) }
            runCatching {
                sessionRepository.transferBetweenSessions(companyId, CashTransfer(
                    branchId = fromSession.branchId,
                    fromSessionId = fromSession.id,
                    fromRegisterName = fromSession.registerName,
                    toSessionId = toSession.id,
                    toRegisterName = toSession.registerName,
                    amount = amount,
                    reason = reason.trim(),
                    createdByUserId = currentUser.id,
                    createdByUserName = currentUser.displayName
                ))
                sessionRepository.listMovements(companyId, fromSession.id)
            }.onSuccess { movs ->
                _uiState.update { it.copy(isLoading = false, movements = movs, transferDone = true) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun clearOpenedSessionId() { _uiState.update { it.copy(openedSessionId = null) } }
    fun clearClosed() { _uiState.update { it.copy(isClosed = false) } }
    fun clearTransferDone() { _uiState.update { it.copy(transferDone = false) } }
    fun clearMessage() { _uiState.update { it.copy(message = null) } }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
