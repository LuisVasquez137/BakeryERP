package com.bakery.erp.presentation.employees

import com.bakery.erp.domain.employeePrivateParts
import com.bakery.erp.util.fmtDate
import com.bakery.erp.model.PayrollStatus
import com.bakery.erp.model.payrollCreditMovementId
import com.bakery.erp.model.employeeCreditAccountId
import com.bakery.erp.model.CreditMovementType
import com.bakery.erp.model.CreditMovement
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.AttendanceRepository
import com.bakery.erp.domain.AttendanceSelfieUploader
import com.bakery.erp.model.SelfieCheck
import com.bakery.erp.model.nextEntry
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.EmployeeDocumentRepository
import com.bakery.erp.domain.EmployeeHistoryRepository
import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.domain.JobPositionRepository
import com.bakery.erp.domain.PayrollRepository
import com.bakery.erp.model.AttendanceRecord
import com.bakery.erp.model.Branch
import com.bakery.erp.model.Employee
import com.bakery.erp.model.EmployeeDocument
import com.bakery.erp.model.EmployeeHistoryEntry
import com.bakery.erp.model.JobPosition
import com.bakery.erp.model.PayrollRecord
import com.bakery.erp.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class EmployeeUiState(
    /**
     * Lo que el colaborador debe hoy en su cuenta de crédito (compras fiadas + adelantos).
     * La planilla nueva lo propone como descuento. `null` = todavía no se leyó.
     */
    val employeeCreditBalance: Double? = null,
    val employees: List<Employee> = emptyList(),
    val selectedEmployee: Employee? = null,
    val branches: List<Branch> = emptyList(),
    val jobPositions: List<JobPosition> = emptyList(),
    val users: List<User> = emptyList(),
    val branchFilter: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val isSaved: Boolean = false,
    /** Id de la ficha recién guardada: tras un alta el formulario abre la ficha completa. */
    val savedEmployeeId: String? = null,
    val isDeleted: Boolean = false,
    val error: String? = null,

    // Asistencia (tab detalle)
    val attendanceToday: AttendanceRecord? = null,
    val attendanceMonth: List<AttendanceRecord> = emptyList(),
    val isAttendanceLoading: Boolean = false,
    val isClockingIn: Boolean = false,

    // Ingreso a Turno (pantalla externa)
    val shiftClockInEmployee: Employee? = null,
    val shiftTodayRecord: AttendanceRecord? = null,
    /** Qué entrada le toca hoy a la persona elegida en el kiosko (null = cargando). */
    val shiftEntry: com.bakery.erp.model.EntryAvailability? = null,
    val isShiftClockInLoading: Boolean = false,
    val shiftClockInSuccess: Boolean = false,

    // Nómina
    val payrollHistory: List<PayrollRecord> = emptyList(),
    val selectedPayroll: PayrollRecord? = null,
    val isPayrollLoading: Boolean = false,
    val isPayrollSaved: Boolean = false,
    val isPayrollDeleted: Boolean = false,

    // Documentos
    val documents: List<EmployeeDocument> = emptyList(),
    val isDocumentsLoading: Boolean = false,
    val isUploadingDocument: Boolean = false,

    // Historial
    val historyEntries: List<EmployeeHistoryEntry> = emptyList(),
    val isHistoryLoading: Boolean = false
)

class EmployeeViewModel(
    private val employeeRepository: EmployeeRepository,
    private val branchRepository: BranchRepository,
    private val attendanceRepository: AttendanceRepository,
    private val payrollRepository: PayrollRepository,
    private val jobPositionRepository: JobPositionRepository,
    private val documentRepository: EmployeeDocumentRepository,
    private val authRepository: AuthRepository,
    private val historyRepository: EmployeeHistoryRepository,
    /** El descuento de créditos y adelantos en la planilla se escribe en la cuenta del colaborador. */
    private val creditRepository: com.bakery.erp.domain.CreditRepository,
    private val selfieUploader: AttendanceSelfieUploader
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmployeeUiState())
    val uiState: StateFlow<EmployeeUiState> = _uiState.asStateFlow()

    // ── Empleados ────────────────────────────────────────────────────────────

    /**
     * [viewer]: quien mira. Define qué partes privadas de la ficha se leen (DPI y dirección con
     * MANAGE_EMPLOYEES, salario y banco con VIEW_EMPLOYEE_SALARY). Null = solo lo público
     * (el kiosko). Ver `EmployeeRepository`.
     */
    fun loadEmployees(companyId: String, viewer: User? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val employees = runCatching {
                employeeRepository.getEmployees(companyId, viewer.employeePrivateParts())
            }.getOrElse { emptyList() }
            val branches = runCatching { branchRepository.getBranches(companyId) }.getOrElse { emptyList() }
            val positions = runCatching { jobPositionRepository.getJobPositions(companyId) }.getOrElse { emptyList() }
            val users = runCatching { authRepository.getUsersForOrg(companyId) }.getOrElse { emptyList() }
            _uiState.update {
                it.copy(
                    employees = employees,
                    branches = branches.filter { b -> b.active },
                    jobPositions = positions.filter { p -> p.active },
                    users = users,
                    isLoading = false
                )
            }
        }
    }

    fun loadEmployeeDetail(companyId: String, employeeId: String, viewer: User? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true) }
            val employee = runCatching {
                employeeRepository.getEmployee(companyId, employeeId, viewer.employeePrivateParts())
            }.getOrNull()
            if (_uiState.value.jobPositions.isEmpty()) {
                val positions = runCatching { jobPositionRepository.getJobPositions(companyId) }.getOrElse { emptyList() }
                _uiState.update { it.copy(jobPositions = positions.filter { p -> p.active }) }
            }
            if (_uiState.value.users.isEmpty()) {
                val users = runCatching { authRepository.getUsersForOrg(companyId) }.getOrElse { emptyList() }
                _uiState.update { it.copy(users = users) }
            }
            // El "Jefe inmediato" del formulario se escoge entre colaboradores.
            if (_uiState.value.employees.isEmpty()) {
                // Con las mismas partes que la lista: la lista abre el formulario con estos objetos.
                val employees = runCatching {
                    employeeRepository.getEmployees(companyId, viewer.employeePrivateParts())
                }.getOrElse { emptyList() }
                _uiState.update { it.copy(employees = employees) }
            }
            if (_uiState.value.branches.isEmpty()) {
                val branches = runCatching { branchRepository.getBranches(companyId) }.getOrElse { emptyList() }
                _uiState.update { it.copy(branches = branches.filter { b -> b.active }) }
            }
            _uiState.update { it.copy(selectedEmployee = employee, isDetailLoading = false) }
        }
    }

    fun selectEmployee(employee: Employee) {
        _uiState.update { it.copy(selectedEmployee = employee) }
    }

    fun clearSelected() {
        _uiState.update {
            it.copy(
                selectedEmployee = null,
                attendanceToday = null,
                attendanceMonth = emptyList(),
                payrollHistory = emptyList(),
                selectedPayroll = null,
                documents = emptyList()
            )
        }
    }

    fun setBranchFilter(branchId: String?) {
        _uiState.update { it.copy(branchFilter = branchId) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * [pendingDocuments]: los que se escogieron en el alta, antes de que la ficha tuviera id.
     * Se suben después de crearla; si alguno falla la ficha queda igual y se avisa cuál.
     */
    fun saveEmployee(
        companyId: String,
        employee: Employee,
        photoBytes: ByteArray? = null,
        pendingDocuments: List<Pair<EmployeeDocument, ByteArray>> = emptyList(),
        viewer: User? = null
    ) {
        // Solo las partes privadas que esta persona puede tocar Y que la ficha trajo leídas:
        // guardar una parte que no se cargó la dejaría en blanco. En el alta, todas las suyas.
        val allowed = viewer.employeePrivateParts()
        val parts = if (employee.id.isEmpty()) allowed
                    else allowed.filter { it.name in employee.privatePartsLoaded }.toSet()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val finalEmployee = if (photoBytes != null) {
                val url = runCatching {
                    documentRepository.uploadPhoto(companyId, employee.id.ifEmpty { "tmp_${Clock.System.now().toEpochMilliseconds()}" }, photoBytes)
                }.getOrElse { employee.photoUrl }
                employee.copy(photoUrl = url)
            } else {
                employee
            }
            val id = runCatching { employeeRepository.saveEmployee(companyId, finalEmployee, parts) }
                .getOrElse { err -> _uiState.update { it.copy(isLoading = false, error = err.message) }; return@launch }
            // En serie: son pocos y así un corte deja subidos los primeros, no una mezcla.
            val failed = pendingDocuments.filter { (doc, bytes) ->
                runCatching { documentRepository.uploadDocument(companyId, id, doc.copy(employeeId = id), bytes) }.isFailure
            }.map { it.first.name }
            _uiState.update {
                it.copy(
                    isLoading = false, isSaved = true, savedEmployeeId = id,
                    error = if (failed.isEmpty()) null
                        else "Colaborador guardado, pero no se subió: ${failed.joinToString()}. Súbelo desde Documentos."
                )
            }
        }
    }

    fun deleteEmployee(companyId: String, employeeId: String) {
        viewModelScope.launch {
            runCatching { employeeRepository.deleteEmployee(companyId, employeeId) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            employees = state.employees.filter { it.id != employeeId },
                            selectedEmployee = if (state.selectedEmployee?.id == employeeId) null
                                               else state.selectedEmployee,
                            isDeleted = true
                        )
                    }
                }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    // ── Asistencia (tab detalle) ──────────────────────────────────────────────

    fun loadAttendance(companyId: String, employeeId: String, month: Int, year: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAttendanceLoading = true) }
            val records = runCatching {
                attendanceRepository.getAttendance(companyId, employeeId, month, year)
            }.getOrElse { emptyList() }
            val today = runCatching {
                attendanceRepository.getTodayRecord(companyId, employeeId)
            }.getOrNull()
            _uiState.update {
                it.copy(
                    attendanceMonth = records,
                    attendanceToday = today,
                    isAttendanceLoading = false
                )
            }
        }
    }

    fun clockIn(companyId: String, employeeId: String) {
        val startTime = _uiState.value.selectedEmployee?.startTime ?: ""
        viewModelScope.launch {
            _uiState.update { it.copy(isClockingIn = true, error = null) }
            runCatching { attendanceRepository.clockIn(companyId, employeeId, startTime) }
                .onSuccess { record ->
                    _uiState.update { it.copy(attendanceToday = record, isClockingIn = false) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isClockingIn = false, error = err.message) }
                }
        }
    }

    fun clockOut(companyId: String, employeeId: String) {
        val recordId = _uiState.value.attendanceToday?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isClockingIn = true, error = null) }
            runCatching { attendanceRepository.clockOut(companyId, employeeId, recordId) }
                .onSuccess { record ->
                    _uiState.update { state ->
                        state.copy(
                            attendanceToday = record,
                            attendanceMonth = state.attendanceMonth.map {
                                if (it.id == record.id) record else it
                            },
                            isClockingIn = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isClockingIn = false, error = err.message) }
                }
        }
    }

    fun saveAttendance(companyId: String, employeeId: String, record: AttendanceRecord) {
        viewModelScope.launch {
            runCatching { attendanceRepository.saveAttendance(companyId, employeeId, record) }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun deleteAttendance(companyId: String, employeeId: String, recordId: String) {
        viewModelScope.launch {
            runCatching { attendanceRepository.deleteAttendance(companyId, employeeId, recordId) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(attendanceMonth = state.attendanceMonth.filter { it.id != recordId })
                    }
                }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    // ── Ingreso a Turno (pantalla externa) ───────────────────────────────────

    fun selectShiftEmployee(companyId: String, employee: Employee) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(shiftClockInEmployee = employee, isShiftClockInLoading = true, shiftTodayRecord = null, shiftEntry = null)
            }
            val today = runCatching { attendanceRepository.getTodayRecords(companyId, employee.id) }.getOrElse { emptyList() }
            _uiState.update {
                it.copy(shiftEntry = employee.nextEntry(today, minutesOfDayNow()), isShiftClockInLoading = false)
            }
        }
    }

    fun clearShiftEmployee() {
        _uiState.update {
            it.copy(shiftClockInEmployee = null, shiftTodayRecord = null, shiftEntry = null, isShiftClockInLoading = false)
        }
    }

    private fun minutesOfDayNow(): Int {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return now.hour * 60 + now.minute
    }

    /**
     * La marca se registra primero y la selfie se sube después, sin hacer esperar a la
     * persona: si la subida falla la marca queda con su check pero sin URL, y el historial
     * la muestra como "foto no subida".
     */
    fun clockInShiftEmployee(companyId: String, selfie: ByteArray?, check: SelfieCheck) {
        val employee = _uiState.value.shiftClockInEmployee ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isShiftClockInLoading = true, error = null) }
            runCatching {
                // Se vuelve a decidir con la hora y las marcas de AHORA (la ficha pudo quedar
                // abierta un rato): en turno mixto define qué entrada es y contra qué hora se
                // mide la tardanza.
                val today = attendanceRepository.getTodayRecords(companyId, employee.id)
                val entry = employee.nextEntry(today, minutesOfDayNow())
                if (entry !is com.bakery.erp.model.EntryAvailability.Available) {
                    _uiState.update { it.copy(shiftEntry = entry) }
                    error("Ya está marcada la entrada de este turno")
                }
                attendanceRepository.clockIn(companyId, employee.id, entry.startTime, check, entry.shiftNumber)
            }
                .onSuccess { record ->
                    _uiState.update { it.copy(shiftTodayRecord = record, isShiftClockInLoading = false, shiftClockInSuccess = true) }
                    if (selfie != null) uploadSelfie(companyId, employee.id, record.id, isClockIn = true, selfie)
                }
                .onFailure { err -> _uiState.update { it.copy(isShiftClockInLoading = false, error = err.message) } }
        }
    }

    private fun uploadSelfie(
        companyId: String, employeeId: String, recordId: String, isClockIn: Boolean, bytes: ByteArray
    ) {
        viewModelScope.launch {
            runCatching {
                val url = selfieUploader.uploadSelfie(bytes, companyId, employeeId, recordId, isClockIn)
                if (url.isNotEmpty()) {
                    attendanceRepository.attachSelfiePhoto(companyId, employeeId, recordId, isClockIn, url)
                }
            }
        }
    }

    // ── Nómina ───────────────────────────────────────────────────────────────

    fun loadPayrollHistory(companyId: String, employeeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPayrollLoading = true) }
            val records = runCatching {
                payrollRepository.getPayrollHistory(companyId, employeeId)
            }.getOrElse { emptyList() }
            _uiState.update { it.copy(payrollHistory = records, isPayrollLoading = false) }
        }
    }

    fun selectPayroll(record: PayrollRecord?) {
        _uiState.update { it.copy(selectedPayroll = record) }
    }

    fun loadEmployeeCreditBalance(companyId: String, employeeId: String) {
        viewModelScope.launch {
            val balance = runCatching {
                creditRepository.getAccount(companyId, employeeCreditAccountId(employeeId))?.balance ?: 0.0
            }.getOrNull()
            _uiState.update { it.copy(employeeCreditBalance = balance) }
        }
    }

    fun savePayroll(companyId: String, employeeId: String, record: PayrollRecord) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPayrollLoading = true, error = null) }
            runCatching {
                val saved = payrollRepository.savePayroll(companyId, employeeId, record)
                syncPayrollCreditDeduction(companyId, employeeId, saved)
                saved
            }
                .onSuccess { _uiState.update { it.copy(isPayrollLoading = false, isPayrollSaved = true) } }
                .onFailure { err -> _uiState.update { it.copy(isPayrollLoading = false, error = err.message) } }
        }
    }

    /**
     * El descuento de "Créditos y adelantos" de una planilla PAGADA baja la deuda de la cuenta.
     * Se escribe con id fijo por planilla: corregir la planilla corrige el mismo renglón en vez
     * de duplicarlo, y devolverla a borrador o borrarla lo quita. Mientras no esté pagada no
     * se descuenta — el dinero todavía no se le retuvo.
     */
    private suspend fun syncPayrollCreditDeduction(companyId: String, employeeId: String, payroll: PayrollRecord) {
        val accountId = employeeCreditAccountId(employeeId)
        val movementId = payrollCreditMovementId(payroll.id)
        if (payroll.status == PayrollStatus.PAID && payroll.advances > 0.0) {
            val employee = _uiState.value.selectedEmployee?.takeIf { it.id == employeeId }
                ?: employeeRepository.getEmployee(companyId, employeeId)
                ?: return
            val account = creditRepository.ensureEmployeeAccount(companyId, employee)
            creditRepository.setMovement(companyId, CreditMovement(
                id = movementId,
                accountId = account.id,
                accountName = account.name,
                type = CreditMovementType.PAYROLL_DEDUCTION,
                amount = -payroll.advances,
                payrollId = payroll.id,
                description = "Planilla ${payroll.periodStart.fmtDate()} – ${payroll.periodEnd.fmtDate()}",
                createdAt = payroll.paymentDate.takeIf { it > 0 } ?: 0L
            ))
        } else {
            creditRepository.deleteMovement(companyId, accountId, movementId)
        }
    }

    fun deletePayroll(companyId: String, employeeId: String, payrollId: String) {
        viewModelScope.launch {
            runCatching {
                payrollRepository.deletePayroll(companyId, employeeId, payrollId)
                // Sin planilla no hubo descuento: la deuda vuelve.
                creditRepository.deleteMovement(companyId, employeeCreditAccountId(employeeId),
                    payrollCreditMovementId(payrollId))
            }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            payrollHistory = state.payrollHistory.filter { it.id != payrollId },
                            selectedPayroll = if (state.selectedPayroll?.id == payrollId) null
                                              else state.selectedPayroll,
                            isPayrollDeleted = true
                        )
                    }
                }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    // ── Documentos ───────────────────────────────────────────────────────────

    fun loadDocuments(companyId: String, employeeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDocumentsLoading = true) }
            val docs = runCatching { documentRepository.getDocuments(companyId, employeeId) }.getOrElse { emptyList() }
            _uiState.update { it.copy(documents = docs, isDocumentsLoading = false) }
        }
    }

    fun uploadDocument(companyId: String, employeeId: String, doc: EmployeeDocument, bytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingDocument = true, error = null) }
            runCatching { documentRepository.uploadDocument(companyId, employeeId, doc, bytes) }
                .onSuccess { uploaded ->
                    _uiState.update { state ->
                        state.copy(
                            documents = state.documents + uploaded,
                            isUploadingDocument = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isUploadingDocument = false, error = err.message) }
                }
        }
    }

    fun deleteDocument(companyId: String, employeeId: String, documentId: String) {
        viewModelScope.launch {
            runCatching { documentRepository.deleteDocument(companyId, employeeId, documentId) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(documents = state.documents.filter { it.id != documentId })
                    }
                }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    // ── Historial ────────────────────────────────────────────────────────────

    fun loadHistory(companyId: String, employeeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isHistoryLoading = true) }
            val entries = runCatching {
                historyRepository.getHistory(companyId, employeeId)
            }.getOrElse { emptyList() }
            _uiState.update { it.copy(historyEntries = entries, isHistoryLoading = false) }
        }
    }

    fun addHistoryEntry(companyId: String, employeeId: String, entry: EmployeeHistoryEntry) {
        viewModelScope.launch {
            runCatching { historyRepository.addEntry(companyId, employeeId, entry) }
                .onSuccess { added ->
                    _uiState.update { state ->
                        state.copy(historyEntries = (listOf(added) + state.historyEntries))
                    }
                }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun deleteHistoryEntry(companyId: String, employeeId: String, entryId: String) {
        viewModelScope.launch {
            runCatching { historyRepository.deleteEntry(companyId, employeeId, entryId) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(historyEntries = state.historyEntries.filter { it.id != entryId })
                    }
                }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    fun clearShiftSuccess() { _uiState.update { it.copy(shiftClockInSuccess = false) } }
    fun clearSaved() { _uiState.update { it.copy(isSaved = false, savedEmployeeId = null) } }
    fun clearDeleted() { _uiState.update { it.copy(isDeleted = false) } }
    fun clearPayrollSaved() { _uiState.update { it.copy(isPayrollSaved = false) } }
    fun clearPayrollDeleted() { _uiState.update { it.copy(isPayrollDeleted = false) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
