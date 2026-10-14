package com.bakery.erp.presentation.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.CounterRemovalPhotoUploader
import com.bakery.erp.domain.CounterRemovalRepository
import com.bakery.erp.domain.EmployeeAccessRepository
import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.domain.NotificationRepository
import com.bakery.erp.model.AppNotification
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.CounterRemoval
import com.bakery.erp.model.CounterRemovalKind
import com.bakery.erp.model.Employee
import com.bakery.erp.model.EmployeePinStatus
import com.bakery.erp.model.EmployeeStatus
import com.bakery.erp.model.NotificationTarget
import com.bakery.erp.model.NotificationType
import com.bakery.erp.model.Permission
import com.bakery.erp.model.ResponsibleConfirmation
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.counterRemovalError
import com.bakery.erp.model.courtesyUsedOn
import com.bakery.erp.util.fmtMoney
import com.bakery.erp.util.fmtTrim
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class CounterRemovalUiState(
    /** Colaboradores activos, los de la sucursal primero: a quién se le cayó. */
    val employees: List<Employee> = emptyList(),
    /** Cuota diaria de la sucursal, leída fresca al abrir (el dueño la puede haber cambiado). */
    val courtesyQuota: Double = 0.0,
    val courtesyUsedToday: Double = 0.0,
    val isPreparing: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    /** Registro guardado bien: cierra el diálogo y va al snackbar. */
    val notice: String? = null,
    /** Lista de la pantalla de salidas (sucursal + rango). */
    val list: List<CounterRemoval> = emptyList(),
    val isListLoading: Boolean = false,
    /** Salidas del turno, para el cierre de caja. */
    val sessionRemovals: List<CounterRemoval> = emptyList(),
    /** Sucursales que venden y el usuario puede ver (filtro de la lista). */
    val branches: List<com.bakery.erp.model.Branch> = emptyList()
)

/**
 * "Sacar del mostrador" (merma, accidente, regalo) y la pantalla que las lista. Ver
 * `model/CounterRemoval.kt` para el porqué de cada control.
 */
class CounterRemovalViewModel(
    private val repository: CounterRemovalRepository,
    private val photoUploader: CounterRemovalPhotoUploader,
    private val employeeRepository: EmployeeRepository,
    private val employeeAccessRepository: EmployeeAccessRepository,
    private val notificationRepository: NotificationRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CounterRemovalUiState())
    val uiState: StateFlow<CounterRemovalUiState> = _uiState.asStateFlow()

    /** Al abrir el diálogo: colaboradores, cuota de la sucursal y lo regalado hoy. */
    fun prepare(companyId: String, branchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPreparing = true, error = null) }
            runCatching {
                val employees = employeeRepository.getEmployees(companyId)
                    .filter { it.active && it.status == EmployeeStatus.ACTIVE }
                    .sortedWith(compareBy<Employee> { it.branchId != branchId }.thenBy { it.name.lowercase() })
                val (quota, used) = courtesyState(companyId, branchId)
                Triple(employees, quota, used)
            }.onSuccess { (employees, quota, used) ->
                _uiState.update {
                    it.copy(isPreparing = false, employees = employees, courtesyQuota = quota, courtesyUsedToday = used)
                }
            }.onFailure { e -> _uiState.update { it.copy(isPreparing = false, error = e.message) } }
        }
    }

    private suspend fun courtesyState(companyId: String, branchId: String): Pair<Double, Double> {
        val quota = branchRepository.getBranch(companyId, branchId)?.dailyCourtesyQuota ?: 0.0
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val startOfDay = now.toLocalDateTime(tz).date.atStartOfDayIn(tz).toEpochMilliseconds()
        val today = repository.getInRange(companyId, branchId, startOfDay, now.toEpochMilliseconds() + 60_000)
        return quota to courtesyUsedOn(today, branchId, now.toEpochMilliseconds(), tz)
    }

    /**
     * Registra la salida. [draft] trae tipo, líneas (con costo y precio ya resueltos), nota y
     * responsable; aquí se completa caja, sucursal y quién la registra.
     *
     * [responsiblePin]: el PIN de la responsable cuando no es quien está en la caja.
     */
    fun register(
        companyId: String,
        session: CashSession,
        draft: CounterRemoval,
        photo: ByteArray?,
        responsiblePin: String,
        currentUser: User
    ) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                // Cuota releída justo antes: dos cajas regalando a la vez no deben pasarla.
                val (quota, used) = if (draft.kind == CounterRemovalKind.COURTESY)
                    courtesyState(companyId, session.branchId)
                else 0.0 to 0.0
                counterRemovalError(draft, photo != null, quota, used)?.let { throw IllegalStateException(it) }

                var responsible: Employee? = null
                var confirmation = ""
                if (draft.kind == CounterRemovalKind.ACCIDENT) {
                    val r = _uiState.value.employees.firstOrNull { it.id == draft.responsibleEmployeeId }
                        ?: employeeRepository.getEmployee(companyId, draft.responsibleEmployeeId)
                        ?: throw IllegalStateException("No se encontró a la colaboradora")
                    responsible = r
                    confirmation = when {
                        r.id == selfEmployeeId(currentUser) -> ResponsibleConfirmation.SELF
                        r.pinStatus == EmployeePinStatus.NONE -> ResponsibleConfirmation.NONE
                        responsiblePin.isBlank() -> throw IllegalStateException("${r.name} tiene que poner su PIN")
                        else -> {
                            // Lanza con el mensaje del servidor si el PIN no es suyo (o está bloqueado).
                            employeeAccessRepository.verifyPin(companyId, r.id, responsiblePin)
                            ResponsibleConfirmation.PIN
                        }
                    }
                }

                val id = repository.newId(companyId)
                val photoUrl = photo?.let { photoUploader.upload(it, companyId, id) }.orEmpty()
                if (draft.kind == CounterRemovalKind.DAMAGED && photoUrl.isBlank())
                    throw IllegalStateException("No se pudo subir la foto. Intenta de nuevo.")

                val saved = repository.register(companyId, draft.copy(
                    id = id,
                    branchId = session.branchId,
                    branchName = session.branchName,
                    sessionId = session.id,
                    registerId = session.registerId,
                    registerName = session.registerName,
                    note = draft.note.trim(),
                    photoUrl = photoUrl,
                    responsibleEmployeeName = responsible?.name.orEmpty(),
                    responsibleConfirmation = confirmation,
                    createdByUserId = currentUser.id,
                    createdByUserName = currentUser.displayName,
                    createdByEmployeeId = currentUser.operatorEmployeeId
                ), responsible)

                if (saved.isAccident && responsible != null) notifyAccidentIfNeeded(companyId, saved, responsible, currentUser)
                saved
            }.onSuccess { saved ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        notice = noticeFor(saved),
                        sessionRemovals = listOf(saved) + it.sessionRemovals.filter { r -> r.id != saved.id },
                        courtesyUsedToday = if (saved.kind == CounterRemovalKind.COURTESY)
                            it.courtesyUsedToday + saved.totalPrice else it.courtesyUsedToday
                    )
                }
            }.onFailure { e -> _uiState.update { it.copy(isBusy = false, error = e.message ?: "No se pudo registrar") } }
        }
    }

    private fun noticeFor(r: CounterRemoval): String {
        val units = "${r.totalUnits.fmtTrim()} u"
        return when (r.kind) {
            CounterRemovalKind.ACCIDENT ->
                "Accidente registrado: $units, Q ${r.chargeAmount.fmtMoney(2)} a la cuenta de ${r.responsibleEmployeeName}"
            CounterRemovalKind.COURTESY -> "Regalo registrado: $units (Q ${r.totalPrice.fmtMoney(2)})"
            else -> "Merma registrada: $units"
        }
    }

    /**
     * Al dueño, solo lo que tiene que decidir o saber: la responsable no pudo confirmar (sin
     * PIN), o es alguien que puede perdonar y no se puede perdonar a sí misma.
     */
    private suspend fun notifyAccidentIfNeeded(companyId: String, r: CounterRemoval, responsible: Employee, by: User) {
        val unconfirmed = r.responsibleConfirmation == ResponsibleConfirmation.NONE
        val canForgive = Permission.FORGIVE_STAFF_ACCIDENT.name in responsible.permissions
        if (!unconfirmed && !canForgive) return
        val why = if (unconfirmed) "${responsible.name} no tiene PIN: no confirmó" else "es de quien puede perdonar; lo decides tú"
        runCatching {
            notificationRepository.push(companyId, AppNotification(
                type = NotificationType.COUNTER_ACCIDENT,
                title = "Accidente en mostrador: Q ${r.chargeAmount.fmtMoney(2)}",
                body = "${r.branchName} · ${r.registerName} · ${responsible.name}: " +
                    r.lines.joinToString { "${it.quantity.fmtTrim()} ${it.productName}" } + ". $why.",
                targetBranchIds = listOf(r.branchId),
                requiredPermission = Permission.VIEW_CASH_AUDIT.name,
                targetScreen = NotificationTarget.COUNTER_REMOVALS,
                targetId = r.branchId,
                createdByUserId = by.id,
                createdByUserName = by.displayName
            ))
        }
    }

    /** Perdonar un accidente (no el propio). Avisa al dueño: perdonar es dar dinero de la empresa. */
    fun forgive(companyId: String, removal: CounterRemoval, reason: String, currentUser: User) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                if (_uiState.value.employees.isEmpty()) {
                    val all = employeeRepository.getEmployees(companyId)
                    _uiState.update { it.copy(employees = all) }
                }
                val updated = repository.forgive(
                    companyId, removal.id, currentUser.id, currentUser.displayName,
                    selfEmployeeId(currentUser), reason
                )
                runCatching {
                    notificationRepository.push(companyId, AppNotification(
                        type = NotificationType.COUNTER_ACCIDENT,
                        title = "Accidente perdonado: Q ${updated.chargeAmount.fmtMoney(2)}",
                        body = "${updated.branchName} · ${currentUser.displayName} perdonó el accidente de " +
                            "${updated.responsibleEmployeeName}" + reason.trim().takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty(),
                        targetBranchIds = listOf(updated.branchId),
                        requiredPermission = Permission.VIEW_CASH_AUDIT.name,
                        targetScreen = NotificationTarget.COUNTER_REMOVALS,
                        targetId = updated.branchId,
                        createdByUserId = currentUser.id,
                        createdByUserName = currentUser.displayName
                    ))
                }
                updated
            }.onSuccess { updated ->
                _uiState.update { s ->
                    s.copy(
                        isBusy = false,
                        notice = "Accidente de ${updated.responsibleEmployeeName} perdonado",
                        list = s.list.map { if (it.id == updated.id) updated else it },
                        sessionRemovals = s.sessionRemovals.map { if (it.id == updated.id) updated else it }
                    )
                }
            }.onFailure { e -> _uiState.update { it.copy(isBusy = false, error = e.message ?: "No se pudo perdonar") } }
        }
    }

    /**
     * La ficha de colaborador de quien está usando la app: la persona identificada con PIN en
     * un equipo, o la ficha vinculada a su cuenta propia. Vacío = no tiene ficha.
     */
    fun selfEmployeeId(user: User): String =
        user.operatorEmployeeId.ifEmpty { _uiState.value.employees.firstOrNull { it.userId == user.id }?.id.orEmpty() }

    fun loadForSession(companyId: String, sessionId: String) {
        viewModelScope.launch {
            runCatching { repository.getForSession(companyId, sessionId) }
                .onSuccess { list -> _uiState.update { it.copy(sessionRemovals = list) } }
        }
    }

    fun loadList(companyId: String, branchId: String, fromMillis: Long, untilMillis: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isListLoading = true, error = null) }
            runCatching {
                if (_uiState.value.employees.isEmpty()) {
                    _uiState.update { it.copy(employees = employeeRepository.getEmployees(companyId)) }
                }
                repository.getInRange(companyId, branchId, fromMillis, untilMillis)
            }.onSuccess { list -> _uiState.update { it.copy(isListLoading = false, list = list) } }
                .onFailure { e -> _uiState.update { it.copy(isListLoading = false, error = e.message) } }
        }
    }

    fun loadBranches(companyId: String, user: User) {
        viewModelScope.launch {
            runCatching { branchRepository.getBranches(companyId) }
                .onSuccess { all ->
                    val visible = all.filter {
                        it.active && com.bakery.erp.model.BranchCapability.CAN_SELL in it.capabilities &&
                            user.canAccessBranch(it.id)
                    }.sortedBy { it.name }
                    _uiState.update { it.copy(branches = visible) }
                }
        }
    }

    fun clearNotice() { _uiState.update { it.copy(notice = null) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
