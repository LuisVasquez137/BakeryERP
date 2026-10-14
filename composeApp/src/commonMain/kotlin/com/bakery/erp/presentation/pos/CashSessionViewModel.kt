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
import com.bakery.erp.model.BankDeposit
import com.bakery.erp.model.bankDepositProblem
import com.bakery.erp.model.bankSlipProblem
import com.bakery.erp.model.expectedCashNow
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
import com.bakery.erp.util.fmtMoney
import com.bakery.erp.model.effectivePaymentBreakdown
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
    val transferDone: Boolean = false,
    /** Colaboradores activos: quién abre la caja y quién la recibe al cerrar. */
    val employees: List<com.bakery.erp.model.Employee> = emptyList(),
    /**
     * Resultado de entrar a la pantalla de cierre: si está fuera del horario de ESA caja, la
     * pantalla pregunta antes de seguir. Null = todavía no se evaluó.
     */
    val closeGate: CloseGate? = null,
    /** Turno que se abrió solo al entregar la caja: la pantalla lleva al POS de ése. */
    val handedOverSessionId: String? = null,
    /**
     * Quien recibió la gaveta en un equipo compartido, ya verificado con su PIN. La pantalla
     * le pasa el equipo (AuthViewModel.startOperatorSession): ya se identificó, no hace falta
     * pedirle el PIN otra vez.
     */
    val handedOverTo: com.bakery.erp.domain.VerifiedEmployee? = null,
    /**
     * Efectivo que la caja debería tener al abrir (lo que dejó el último cierre). Null = no hay
     * de dónde saberlo.
     */
    val openingExpectedCash: Double? = null,
    /** Quién y cuándo dejó ese efectivo, para mostrarlo al abrir. */
    val openingExpectedFrom: String = "",
    /** Cobros con tarjeta y transferencia del turno que se está viendo: de cuál turno es cada uno. */
    val sessionNonCashSales: List<com.bakery.erp.model.Sale> = emptyList(),
    /** Ventas anuladas del turno que se está viendo: van en el cuadre (ver `SaleVoid.kt`). */
    val sessionVoidedSales: List<com.bakery.erp.model.Sale> = emptyList(),
    /** Depósitos al banco sin boleta de la caja que se está operando: bloquean el cierre. */
    val pendingBankDeposits: List<BankDeposit> = emptyList(),
    /** Depósitos del turno que se está viendo (cuadre y detalle). */
    val sessionBankDeposits: List<BankDeposit> = emptyList(),
    val isBankDepositBusy: Boolean = false,
    /** Aviso tras crear un depósito o adjuntar la boleta (lo muestra la pantalla y lo limpia). */
    val bankDepositNotice: String? = null,
    /** Error al sacar el depósito (p. ej. PIN incorrecto): se muestra dentro del diálogo. */
    val bankDepositError: String? = null
)

/** Lo que se pide al sacar dinero para el banco (a medio turno o al cerrar). */
data class BankDepositDraft(
    val bankName: String,
    val carriedBy: com.bakery.erp.model.Employee?,
    val notes: String = "",
    /** PIN de [carriedBy]; ver [carrierMustConfirm]. No se guarda en ningún lado. */
    val carrierPin: String = ""
)

/**
 * En un equipo de sucursal con PIN, quien lleva el dinero al banco lo confirma con SU PIN,
 * aunque sea la misma persona que abrió la caja: así consta que esa persona salió con el
 * dinero (decisión del dueño). Sin PIN en la sucursal no hay con qué confirmarlo.
 */
fun carrierMustConfirm(currentUser: User): Boolean = currentUser.operatorEmployeeId.isNotEmpty()

/** Qué falta para el PIN de quien lo lleva, o null. Solo forma: el servidor lo verifica. */
fun carrierPinProblem(currentUser: User?, draft: BankDepositDraft): String? = when {
    currentUser == null || !carrierMustConfirm(currentUser) || draft.carriedBy == null -> null
    draft.carrierPin.length != com.bakery.erp.domain.PIN_LENGTH -> "Falta el PIN de ${draft.carriedBy.name}."
    else -> null
}

/** ¿Se entró al cierre en el horario de cuadre de esta caja? */
data class CloseGate(
    val sessionId: String,
    val outOfSchedule: Boolean,
    /** "2:00 PM, 9:00 PM (±30 min)". Vacío si la caja no tiene horarios configurados. */
    val scheduleLabel: String
)

/**
 * ¿Quien recibe la gaveta tiene que poner su PIN? Solo en un equipo compartido (hay
 * persona identificada) y cuando recibe otra persona: si se la entrega a sí misma (doble
 * turno), ya se identificó.
 */
fun receiverMustConfirm(currentUser: User, receivedBy: com.bakery.erp.model.Employee): Boolean =
    currentUser.operatorEmployeeId.isNotEmpty() && receivedBy.id != currentUser.operatorEmployeeId

class CashSessionViewModel(
    private val sessionRepository: CashSessionRepository,
    private val registerRepository: CashRegisterRepository,
    private val branchRepository: BranchRepository,
    private val dailyCloseRepository: DailyCashCloseRepository,
    private val purchaseRepository: PurchaseRepository,
    /** Bitácora de cajas: aperturas, entradas al cierre y cierres. */
    private val auditRepository: com.bakery.erp.domain.CashAuditRepository,
    /** Aviso al dueño cuando alguien entra al cierre fuera de horario. */
    private val notificationRepository: com.bakery.erp.domain.NotificationRepository,
    private val employeeRepository: com.bakery.erp.domain.EmployeeRepository,
    /** Ventas del turno: el detalle de vouchers y transferencias. */
    private val saleRepository: com.bakery.erp.domain.SaleRepository,
    /** PIN de quien recibe la gaveta en un equipo compartido. */
    private val accessRepository: com.bakery.erp.domain.EmployeeAccessRepository,
    /** No se cierra un turno con cuentas en espera (ver [com.bakery.erp.model.HeldCart]). */
    private val heldCartRepository: com.bakery.erp.domain.HeldCartRepository,
    /** Depósitos al banco desde la caja (ver [BankDeposit]). */
    private val bankDepositRepository: com.bakery.erp.domain.BankDepositRepository,
    private val bankSlipUploader: com.bakery.erp.domain.BankSlipImageUploader
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

    /** Colaboradores activos, los de la sucursal primero: el que cubre otro turno también cuenta. */
    fun loadEmployees(companyId: String, branchId: String) {
        viewModelScope.launch {
            runCatching { employeeRepository.getEmployees(companyId) }
                .onSuccess { list ->
                    val active = list.filter { it.active && it.status == com.bakery.erp.model.EmployeeStatus.ACTIVE }
                        .sortedWith(compareBy<com.bakery.erp.model.Employee> { it.branchId != branchId }.thenBy { it.name.lowercase() })
                    _uiState.update { it.copy(employees = active) }
                }
        }
    }

    /**
     * Se entró a la pantalla de cierre: queda en la bitácora EN ESE MOMENTO, se cierre o no,
     * porque la pantalla ya muestra cuánto debería haber. Fuera del horario de cuadre de esa
     * caja, además le llega el aviso al dueño y la pantalla pregunta antes de seguir.
     */
    fun enterCloseScreen(companyId: String, sessionId: String, currentUser: User) {
        if (_uiState.value.closeGate?.sessionId == sessionId) return
        viewModelScope.launch {
            runCatching {
                val session = sessionRepository.getSession(companyId, sessionId) ?: return@runCatching null
                val register = registerRepository.get(companyId, session.registerId)
                val schedules = register?.closeSchedules.orEmpty()
                val tolerance = register?.closeToleranceMinutes ?: com.bakery.erp.model.DEFAULT_CLOSE_TOLERANCE_MINUTES
                val now = Clock.System.now()
                val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
                val outOfSchedule = !com.bakery.erp.model.isWithinCloseSchedule(local.hour * 60 + local.minute, schedules, tolerance)
                val label = com.bakery.erp.model.closeScheduleLabel(schedules, tolerance)
                auditRepository.log(companyId, auditEvent(session, currentUser, com.bakery.erp.model.CashAuditEventType.CLOSE_SCREEN,
                    outOfSchedule, label, employeeName = session.openedByEmployeeName))
                if (outOfSchedule) {
                    runCatching {
                        notificationRepository.push(companyId, com.bakery.erp.model.AppNotification(
                            type = com.bakery.erp.model.NotificationType.CASH_CLOSE_OUT_OF_SCHEDULE,
                            title = "Cierre de caja fuera de horario",
                            body = "${session.branchName} · ${session.registerName}: entraron al cierre a las " +
                                "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}" +
                                (if (label.isNotBlank()) ". Horario: $label" else "") +
                                (if (session.openedByEmployeeName.isNotBlank()) ". Caja de ${session.openedByEmployeeName}" else ""),
                            targetBranchIds = listOf(session.branchId),
                            requiredPermission = com.bakery.erp.model.Permission.VIEW_CASH_AUDIT.name,
                            targetScreen = com.bakery.erp.model.NotificationTarget.CASH_AUDIT,
                            targetId = session.branchId,
                            createdByUserId = currentUser.id,
                            createdByUserName = currentUser.displayName
                        ))
                    }
                }
                CloseGate(sessionId, outOfSchedule, label)
            }.onSuccess { gate ->
                if (gate != null) _uiState.update { it.copy(closeGate = gate) }
            }
        }
    }

    fun clearCloseGate() = _uiState.update { it.copy(closeGate = null) }

    /**
     * Lo que la caja debería tener al abrir: lo que dejó su último cierre. Así el primer turno del
     * día comprueba que esté cabal lo que se dejó la noche anterior.
     */
    fun loadOpeningExpected(companyId: String, registerId: String) {
        viewModelScope.launch {
            runCatching { sessionRepository.getSessionsForRegister(companyId, registerId) }
                .onSuccess { sessions ->
                    val last = sessions.filter { it.status == com.bakery.erp.model.CashSessionStatus.CLOSED }
                        .maxByOrNull { it.closedAt ?: 0L }
                    val expected = com.bakery.erp.model.expectedOpeningCash(last)
                    val from = last?.let { s ->
                        val at = s.closedAt?.let { ms ->
                            val d = kotlin.time.Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
                            "${d.dayOfMonth.toString().padStart(2, '0')}/${d.monthNumber.toString().padStart(2, '0')} " +
                                "${d.hour.toString().padStart(2, '0')}:${d.minute.toString().padStart(2, '0')}"
                        }.orEmpty()
                        listOf(s.openedByEmployeeName, at).filter { it.isNotBlank() }.joinToString(" · ")
                    }.orEmpty()
                    _uiState.update { it.copy(openingExpectedCash = expected, openingExpectedFrom = from) }
                }
        }
    }

    fun clearHandedOver() = _uiState.update { it.copy(handedOverSessionId = null, handedOverTo = null) }

    /** Ventas anuladas del turno, para que el cuadre diga cuáles fueron. */
    fun loadSessionVoidedSales(companyId: String, sessionId: String) {
        _uiState.update { it.copy(sessionVoidedSales = emptyList()) }
        viewModelScope.launch {
            runCatching { saleRepository.getSalesForSession(companyId, sessionId) }
                .onSuccess { sales ->
                    val voided = sales.filter { it.status == com.bakery.erp.model.SaleStatus.VOIDED }
                    _uiState.update { it.copy(sessionVoidedSales = voided) }
                }
        }
    }

    /** Cobros con tarjeta, transferencia o PedidosYa del turno: de cuál turno es cada voucher. */
    fun loadSessionNonCashSales(companyId: String, sessionId: String) {
        viewModelScope.launch {
            runCatching { saleRepository.getSalesForSession(companyId, sessionId) }
                .onSuccess { sales ->
                    val nonCash = sales.filter { sale ->
                        sale.effectivePaymentBreakdown.keys.any { it in com.bakery.erp.model.SalePaymentMethod.nonCashSettled }
                    }.sortedBy { it.createdAt }
                    _uiState.update { it.copy(sessionNonCashSales = nonCash) }
                }
        }
    }

    private fun auditEvent(
        session: CashSession,
        user: User,
        type: String,
        outOfSchedule: Boolean,
        scheduleLabel: String,
        employeeName: String,
        expected: Double = 0.0,
        counted: Double = 0.0,
        difference: Double = 0.0,
        notes: String = ""
    ) = com.bakery.erp.model.CashAuditEvent(
        type = type,
        branchId = session.branchId,
        branchName = session.branchName,
        registerId = session.registerId,
        registerName = session.registerName,
        sessionId = session.id,
        userId = user.id,
        userName = user.displayName,
        employeeName = employeeName,
        outOfSchedule = outOfSchedule,
        scheduleLabel = scheduleLabel,
        expectedCash = expected,
        countedCash = counted,
        difference = difference,
        notes = notes
    )

    fun openSession(
        companyId: String,
        branch: Branch,
        register: CashRegister,
        currentUser: User,
        openingBalance: Double,
        openingDenominations: Map<String, Int> = emptyMap(),
        /** Colaborador que abre: en la tablet de usuario único, es lo que dice quién estaba. */
        openedBy: com.bakery.erp.model.Employee? = null,
        /** Lo que dejó el último cierre (si se sabe): con lo contado dice si faltaba algo. */
        openingExpected: Double = 0.0
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
                        openingBalance = openingBalance,
                        openedByEmployeeId = openedBy?.id.orEmpty(),
                        openedByEmployeeName = openedBy?.name.orEmpty(),
                        openingExpected = openingExpected
                    )
                )
                runCatching {
                    val diff = if (openingExpected > 0.004) openingBalance - openingExpected else 0.0
                    auditRepository.log(companyId, auditEvent(session, currentUser, com.bakery.erp.model.CashAuditEventType.OPEN,
                        outOfSchedule = false, scheduleLabel = "", employeeName = openedBy?.name.orEmpty(),
                        expected = openingExpected, counted = openingBalance, difference = diff,
                        notes = when {
                            openingExpected <= 0.004 -> ""
                            diff < -0.004 -> "Al abrir faltaban Q ${(-diff).fmtMoney(2)} de lo que se dejó"
                            diff > 0.004 -> "Al abrir sobraban Q ${diff.fmtMoney(2)} de lo que se dejó"
                            else -> "Estaba lo que se dejó"
                        }))
                }
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
                        createdByUserName = currentUser.displayName,
                        createdByEmployeeId = currentUser.operatorEmployeeId
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
                        createdByEmployeeId = currentUser.operatorEmployeeId,
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
        closingDenominations: Map<String, Int> = emptyMap(),
        /** Quién recibe la gaveta (siguiente turno). Null = nadie, fin del día. */
        receivedBy: com.bakery.erp.model.Employee? = null,
        currentUser: User? = null,
        /** Efectivo que se saca de la gaveta al cerrar (permiso aparte). */
        withdrawal: Double = 0.0,
        /** PIN de [receivedBy]; ver [receiverMustConfirm]. */
        receiverPin: String? = null,
        /** Lo retirado va al banco: queda un depósito pendiente de boleta. Null = retiro. */
        withdrawalToBank: BankDepositDraft? = null
    ) {
        var verifiedReceiver: com.bakery.erp.domain.VerifiedEmployee? = null
        viewModelScope.launch {
            // En un equipo compartido, quien recibe la gaveta confirma con SU PIN: así la
            // entrega queda aceptada por esa persona y no solo escogida de una lista.
            if (receivedBy != null && currentUser != null && receiverMustConfirm(currentUser, receivedBy)) {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val verified = runCatching { accessRepository.verifyPin(companyId, receivedBy.id, receiverPin.orEmpty()) }
                    .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "No se pudo verificar el PIN") } }
                    .getOrNull() ?: return@launch
                verifiedReceiver = verified
            }
            var carrierConfirmed = false
            if (withdrawalToBank != null && withdrawal > 0.004 && currentUser != null) {
                _uiState.update { it.copy(isLoading = true, error = null) }
                carrierConfirmed = runCatching { verifyCarrier(companyId, withdrawalToBank, currentUser) }
                    .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "No se pudo verificar el PIN") } }
                    .getOrNull() ?: return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null, isClosed = false, handedOverSessionId = null) }
            // Decisión del dueño: con cuentas en espera no se cierra. Se cobran o se descartan
            // (y el descarte queda en la bitácora); si no, pasarían escondidas al siguiente turno.
            val held = runCatching { heldCartRepository.getForSession(companyId, sessionId) }.getOrDefault(emptyList())
            if (held.isNotEmpty()) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Hay ${held.size} cuenta(s) en espera en esta caja " +
                        "(${held.joinToString { h -> h.label }}). Cobralas o descartalas en el punto de venta antes de cerrar.")
                }
                return@launch
            }
            // Decisión del dueño: no se cierra un turno con un depósito al banco sin boleta. Se
            // adjunta, o se deja sin boleta con motivo (y al dueño le llega el aviso).
            val pendingDeposits = runCatching {
                val reg = _uiState.value.currentSession?.takeIf { it.id == sessionId }?.registerId
                    ?: sessionRepository.getSession(companyId, sessionId)?.registerId.orEmpty()
                bankDepositRepository.getPendingForRegister(companyId, reg)
            }.getOrDefault(emptyList())
            if (pendingDeposits.isNotEmpty()) {
                _uiState.update {
                    it.copy(isLoading = false, pendingBankDeposits = pendingDeposits,
                        error = "Hay ${pendingDeposits.size} depósito(s) al banco sin boleta en esta caja. " +
                            "Adjunta la boleta (o déjalo sin boleta con el motivo) antes de cerrar.")
                }
                return@launch
            }
            if (withdrawalToBank != null && withdrawal > 0.004) {
                bankDepositProblem(withdrawal, countedCash, withdrawalToBank.bankName, withdrawalToBank.carriedBy?.id.orEmpty())
                    ?.let { problem -> _uiState.update { it.copy(isLoading = false, error = problem) }; return@launch }
            }
            com.bakery.erp.model.closingWithdrawalProblem(countedCash, withdrawal)?.let { problem ->
                _uiState.update { it.copy(isLoading = false, error = problem) }; return@launch
            }
            var nextSessionId: String? = null
            runCatching {
                val closed = sessionRepository.closeSession(
                    companyId, sessionId, countedCash, notes,
                    receivedByEmployeeId = receivedBy?.id.orEmpty(),
                    receivedByEmployeeName = receivedBy?.name.orEmpty(),
                    closedByUserId = currentUser?.id.orEmpty(),
                    closedByUserName = currentUser?.displayName.orEmpty(),
                    closedByEmployeeId = currentUser?.operatorEmployeeId.orEmpty()
                )
                if (currentUser != null) {
                    val gate = _uiState.value.closeGate?.takeIf { it.sessionId == sessionId }
                    runCatching {
                        auditRepository.log(companyId, auditEvent(closed, currentUser, com.bakery.erp.model.CashAuditEventType.CLOSE,
                            outOfSchedule = gate?.outOfSchedule == true, scheduleLabel = gate?.scheduleLabel.orEmpty(),
                            employeeName = listOfNotNull(
                                closed.openedByEmployeeName.takeIf { it.isNotBlank() },
                                receivedBy?.name?.let { "entrega a $it" }
                            ).joinToString(" → "),
                            expected = closed.expectedCash, counted = closed.countedCash, difference = closed.difference,
                            notes = listOfNotNull(
                                notes?.takeIf { it.isNotBlank() },
                                (if (withdrawalToBank != null) "Al banco Q ${withdrawal.fmtMoney(2)}" else "Retiró Q ${withdrawal.fmtMoney(2)}")
                                    .takeIf { withdrawal > 0.004 }
                            ).joinToString(" · ")))
                    }
                }
                // Lo retirado que va al banco: el depósito nace pendiente y la boleta se adjunta
                // al volver (bloquea el siguiente cierre de esta caja mientras no esté).
                val bankDeposit = if (withdrawalToBank != null && withdrawal > 0.004 && currentUser != null) {
                    runCatching {
                        createDepositRecord(companyId, closed, withdrawal, withdrawalToBank, currentUser,
                            atClose = true, carrierConfirmed = carrierConfirmed)
                    }.getOrNull()
                } else null
                // Lo que sale de esta gaveta: al siguiente turno o, si nadie la recibe, para mañana.
                val handover = com.bakery.erp.model.shiftHandover(closed, countedCash, withdrawal)
                sessionRepository.recordHandover(companyId, sessionId, withdrawal, handover, bankDeposit?.id.orEmpty())
                if (receivedBy != null && currentUser != null) {
                    // El siguiente turno sigue con la misma gaveta: no se vuelve a teclear el fondo.
                    val next = sessionRepository.openSession(companyId, CashSession(
                        branchId = closed.branchId,
                        branchName = closed.branchName,
                        registerId = closed.registerId,
                        registerName = closed.registerName,
                        cashierId = currentUser.id,
                        // En un equipo compartido el turno nuevo es de quien recibe, no de
                        // quien entrega (la cuenta es la misma para las dos).
                        cashierName = if (currentUser.operatorEmployeeId.isNotEmpty()) receivedBy.name
                                      else currentUser.displayName,
                        openedAt = Clock.System.now().toEpochMilliseconds(),
                        openingBalance = handover.cash,
                        openingExpected = handover.cash,
                        openedByEmployeeId = receivedBy.id,
                        openedByEmployeeName = receivedBy.name,
                        previousSessionId = closed.id,
                        carriedInCard = handover.card,
                        carriedInTransfer = handover.transfer
                    ))
                    nextSessionId = next.id
                    runCatching {
                        auditRepository.log(companyId, auditEvent(next, currentUser, com.bakery.erp.model.CashAuditEventType.OPEN,
                            outOfSchedule = false, scheduleLabel = "", employeeName = receivedBy.name,
                            expected = handover.cash, counted = handover.cash,
                            notes = "Recibe de ${closed.openedByEmployeeName.ifBlank { "el turno anterior" }}: " +
                                "efectivo Q ${handover.cash.fmtMoney(2)}, tarjeta Q ${handover.card.fmtMoney(2)}, " +
                                "transferencias Q ${handover.transfer.fmtMoney(2)}"))
                    }
                }
                if (closingDenominations.isNotEmpty()) {
                    sessionRepository.saveDenominations(
                        companyId, sessionId, CashDenominationStage.CLOSING, closingDenominations
                    )
                }
                closed
            }.onSuccess { closed ->
                _uiState.update {
                    it.copy(
                        isLoading = false, currentSession = closed, isClosed = true,
                        handedOverSessionId = nextSessionId,
                        handedOverTo = verifiedReceiver?.takeIf { nextSessionId != null }
                    )
                }
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

    // ── Depósitos al banco ───────────────────────────────────────────────────

    /** Los sin boleta de la caja: el POS los muestra y bloquean el cierre. */
    fun loadPendingBankDeposits(companyId: String, registerId: String) {
        if (companyId.isEmpty() || registerId.isEmpty()) return
        viewModelScope.launch {
            runCatching { bankDepositRepository.getPendingForRegister(companyId, registerId) }
                .onSuccess { list -> _uiState.update { it.copy(pendingBankDeposits = list) } }
        }
    }

    fun loadSessionBankDeposits(companyId: String, sessionId: String) {
        _uiState.update { it.copy(sessionBankDeposits = emptyList()) }
        viewModelScope.launch {
            runCatching { bankDepositRepository.getForSession(companyId, sessionId) }
                .onSuccess { list -> _uiState.update { it.copy(sessionBankDeposits = list) } }
        }
    }

    /**
     * Sacar efectivo de la gaveta a medio turno para llevarlo al banco. El esperado baja ya (el
     * dinero ya no está); el depósito queda pendiente hasta que se adjunte la boleta.
     *
     * Encabezado antes que efecto: primero el depósito, después el movimiento de caja. Si el
     * movimiento falla, el depósito se cancela (el dinero no salió).
     */
    fun createBankDeposit(companyId: String, sessionId: String, amount: Double, draft: BankDepositDraft, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBankDepositBusy = true, error = null, bankDepositError = null) }
            runCatching {
                val session = sessionRepository.getSession(companyId, sessionId)
                    ?: error("No se encontró la caja.")
                if (session.status != CashSessionStatus.OPEN) error("La caja ya está cerrada.")
                bankDepositProblem(amount, session.expectedCashNow, draft.bankName, draft.carriedBy?.id.orEmpty())
                    ?.let { error(it) }
                // Antes de tocar nada: con el PIN mal no sale ni un quetzal.
                val confirmed = verifyCarrier(companyId, draft, currentUser)
                val deposit = createDepositRecord(companyId, session, amount, draft, currentUser,
                    atClose = false, carrierConfirmed = confirmed)
                val movementId = runCatching {
                    sessionRepository.addMovement(companyId, CashMovement(
                        sessionId = session.id,
                        type = CashMovementType.BANK_DEPOSIT,
                        amount = amount,
                        reason = "${draft.bankName.trim()} · lo lleva ${draft.carriedBy?.name.orEmpty()}",
                        createdByUserId = currentUser.id,
                        createdByUserName = currentUser.displayName,
                        createdByEmployeeId = currentUser.operatorEmployeeId,
                        sourceBankDepositId = deposit.id
                    ))
                }.getOrElse { e ->
                    runCatching { bankDepositRepository.cancel(companyId, deposit.id, "No se registró la salida: ${e.message}") }
                    throw e
                }
                runCatching { bankDepositRepository.setMovement(companyId, deposit.id, movementId) }
                deposit
            }.onSuccess { d ->
                refreshAfterBankDeposit(companyId, sessionId, d.registerId)
                _uiState.update {
                    it.copy(isBankDepositBusy = false,
                        bankDepositNotice = "Salieron Q ${d.amount.fmtMoney(2)} para el banco. " +
                            "Al volver, adjunta la foto de la boleta: sin ella no se puede cerrar la caja.")
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isBankDepositBusy = false, bankDepositError = e.message ?: "No se pudo registrar el depósito") }
            }
        }
    }

    /**
     * Verifica el PIN de quien lleva el depósito si la sucursal usa PIN. Devuelve si quedó
     * confirmado con PIN; lanza si el PIN no es el correcto.
     */
    private suspend fun verifyCarrier(companyId: String, draft: BankDepositDraft, currentUser: User): Boolean {
        if (!carrierMustConfirm(currentUser)) return false
        val carrier = draft.carriedBy ?: error("Escoge quién lo lleva al banco.")
        carrierPinProblem(currentUser, draft)?.let { error(it) }
        runCatching { accessRepository.verifyPin(companyId, carrier.id, draft.carrierPin) }
            .getOrElse { e -> error("PIN de ${carrier.name}: ${e.message ?: "no se pudo verificar"}") }
        return true
    }

    /** Crea el depósito pendiente y deja el aviso y la bitácora. No toca la gaveta. */
    private suspend fun createDepositRecord(
        companyId: String, session: CashSession, amount: Double, draft: BankDepositDraft,
        currentUser: User, atClose: Boolean, carrierConfirmed: Boolean
    ): BankDeposit {
        val deposit = bankDepositRepository.create(companyId, BankDeposit(
            branchId = session.branchId,
            branchName = session.branchName,
            registerId = session.registerId,
            registerName = session.registerName,
            sessionId = session.id,
            atClose = atClose,
            amount = amount,
            bankName = draft.bankName.trim(),
            carriedByEmployeeId = draft.carriedBy?.id.orEmpty(),
            carriedByEmployeeName = draft.carriedBy?.name.orEmpty(),
            carrierConfirmedWithPin = carrierConfirmed,
            notes = draft.notes.trim(),
            createdByUserId = currentUser.id,
            createdByUserName = currentUser.displayName,
            createdByEmployeeId = currentUser.operatorEmployeeId
        ))
        val who = deposit.carriedByEmployeeName.ifBlank { currentUser.displayName }
        runCatching {
            auditRepository.log(companyId, auditEvent(session, currentUser, com.bakery.erp.model.CashAuditEventType.BANK_DEPOSIT_OUT,
                outOfSchedule = false, scheduleLabel = "", employeeName = who, expected = amount,
                notes = "${deposit.bankName}${if (atClose) " · al cerrar" else ""}" +
                    (deposit.notes.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")))
        }
        pushBankDeposit(companyId, session, currentUser,
            title = "Salieron Q ${amount.fmtMoney(2)} para el banco",
            body = "${session.branchName} · ${session.registerName}: lo lleva $who a ${deposit.bankName}" +
                (if (atClose) " (al cerrar el turno)" else "") + ". Queda pendiente la boleta.")
        return deposit
    }

    /** Adjunta la foto de la boleta (obligatoria), su número y el monto que dice. */
    fun attachBankSlip(
        companyId: String, deposit: BankDeposit, photo: ByteArray, slipNumber: String, slipAmount: Double,
        currentUser: User, viewingSessionId: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBankDepositBusy = true, error = null) }
            runCatching {
                bankSlipProblem(photo.isNotEmpty(), slipNumber, slipAmount)?.let { error(it) }
                val url = bankSlipUploader.uploadSlipPhoto(photo, companyId, deposit.id)
                if (url.isBlank()) error("No se pudo subir la foto de la boleta en este equipo.")
                bankDepositRepository.attachSlip(companyId, deposit.id, url, slipNumber.trim(), slipAmount,
                    currentUser.displayName, currentUser.operatorEmployeeId)
                val diff = slipAmount - deposit.amount
                val session = sessionRepository.getSession(companyId, deposit.sessionId)
                if (session != null) {
                    runCatching {
                        auditRepository.log(companyId, auditEvent(session, currentUser, com.bakery.erp.model.CashAuditEventType.BANK_DEPOSIT_SLIP,
                            outOfSchedule = false, scheduleLabel = "", employeeName = deposit.carriedByEmployeeName,
                            expected = deposit.amount, counted = slipAmount, difference = diff,
                            notes = "${deposit.bankName} · boleta ${slipNumber.trim()}"))
                    }
                    pushBankDeposit(companyId, session, currentUser,
                        title = if (kotlin.math.abs(diff) > 0.004) "Boleta con diferencia: Q ${diff.fmtMoney(2)}"
                                else "Boleta de depósito: Q ${slipAmount.fmtMoney(2)}",
                        body = "${session.branchName} · ${session.registerName}: salieron Q ${deposit.amount.fmtMoney(2)}, " +
                            "la boleta ${slipNumber.trim()} de ${deposit.bankName} dice Q ${slipAmount.fmtMoney(2)}" +
                            (if (kotlin.math.abs(diff) > 0.004) ". Revisar la diferencia." else "."))
                }
                diff
            }.onSuccess { diff ->
                refreshAfterBankDeposit(companyId, viewingSessionId ?: deposit.sessionId, deposit.registerId)
                _uiState.update {
                    it.copy(isBankDepositBusy = false,
                        bankDepositNotice = if (kotlin.math.abs(diff) > 0.004)
                            "Boleta adjunta. No cuadra con lo que salió (diferencia Q ${diff.fmtMoney(2)}): se avisó al administrador."
                        else "Boleta adjunta.")
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isBankDepositBusy = false, error = e.message ?: "No se pudo adjuntar la boleta") }
            }
        }
    }

    /** Dejar un depósito sin boleta, con motivo: ya no bloquea el cierre, pero el dueño se entera. */
    fun markBankDepositNoSlip(
        companyId: String, deposit: BankDeposit, reason: String, currentUser: User, viewingSessionId: String? = null
    ) {
        val clean = reason.trim()
        if (clean.length < com.bakery.erp.model.MIN_MISSING_SLIP_REASON) {
            _uiState.update { it.copy(error = "Escribe el motivo (mínimo ${com.bakery.erp.model.MIN_MISSING_SLIP_REASON} letras).") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBankDepositBusy = true, error = null) }
            runCatching {
                bankDepositRepository.markNoSlip(companyId, deposit.id, clean, currentUser.displayName)
                val session = sessionRepository.getSession(companyId, deposit.sessionId)
                if (session != null) {
                    runCatching {
                        auditRepository.log(companyId, auditEvent(session, currentUser, com.bakery.erp.model.CashAuditEventType.BANK_DEPOSIT_NO_SLIP,
                            outOfSchedule = false, scheduleLabel = "", employeeName = deposit.carriedByEmployeeName,
                            expected = deposit.amount, notes = "${deposit.bankName} · $clean"))
                    }
                    pushBankDeposit(companyId, session, currentUser,
                        title = "Depósito sin boleta: Q ${deposit.amount.fmtMoney(2)}",
                        body = "${session.branchName} · ${session.registerName}: ${currentUser.displayName} lo dejó sin boleta. " +
                            "Lo llevó ${deposit.carriedByEmployeeName.ifBlank { "—" }} a ${deposit.bankName}. Motivo: $clean")
                }
            }.onSuccess {
                refreshAfterBankDeposit(companyId, viewingSessionId ?: deposit.sessionId, deposit.registerId)
                _uiState.update { it.copy(isBankDepositBusy = false, bankDepositNotice = "Quedó sin boleta; se avisó al administrador.") }
            }.onFailure { e ->
                _uiState.update { it.copy(isBankDepositBusy = false, error = e.message ?: "No se pudo guardar") }
            }
        }
    }

    private suspend fun refreshAfterBankDeposit(companyId: String, sessionId: String, registerId: String) {
        runCatching { bankDepositRepository.getPendingForRegister(companyId, registerId) }
            .onSuccess { list -> _uiState.update { it.copy(pendingBankDeposits = list) } }
        runCatching { bankDepositRepository.getForSession(companyId, sessionId) }
            .onSuccess { list -> _uiState.update { it.copy(sessionBankDeposits = list) } }
        runCatching { sessionRepository.listMovements(companyId, sessionId) }
            .onSuccess { movs -> _uiState.update { it.copy(movements = movs) } }
    }

    private suspend fun pushBankDeposit(companyId: String, session: CashSession, currentUser: User, title: String, body: String) {
        runCatching {
            notificationRepository.push(companyId, com.bakery.erp.model.AppNotification(
                type = com.bakery.erp.model.NotificationType.BANK_DEPOSIT,
                title = title,
                body = body,
                targetBranchIds = listOf(session.branchId),
                requiredPermission = com.bakery.erp.model.Permission.VIEW_CASH_AUDIT.name,
                targetScreen = com.bakery.erp.model.NotificationTarget.CASH_AUDIT,
                targetId = session.branchId,
                createdByUserId = currentUser.id,
                createdByUserName = currentUser.displayName
            ))
        }
    }

    fun clearBankDepositNotice() = _uiState.update { it.copy(bankDepositNotice = null) }
    fun clearBankDepositError() = _uiState.update { it.copy(bankDepositError = null) }

    fun clearOpenedSessionId() { _uiState.update { it.copy(openedSessionId = null) } }
    fun clearClosed() { _uiState.update { it.copy(isClosed = false) } }
    fun clearTransferDone() { _uiState.update { it.copy(transferDone = false) } }
    fun clearMessage() { _uiState.update { it.copy(message = null) } }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
