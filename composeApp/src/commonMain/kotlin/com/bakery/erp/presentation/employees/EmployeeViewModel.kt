package com.bakery.erp.presentation.employees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.AttendanceRepository
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
    private val historyRepository: EmployeeHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmployeeUiState())
    val uiState: StateFlow<EmployeeUiState> = _uiState.asStateFlow()

    // ── Empleados ────────────────────────────────────────────────────────────

    fun loadEmployees(companyId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val employees = runCatching { employeeRepository.getEmployees(companyId) }.getOrElse { emptyList() }
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

    fun loadEmployeeDetail(companyId: String, employeeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true) }
            val employee = runCatching { employeeRepository.getEmployee(companyId, employeeId) }.getOrNull()
            if (_uiState.value.jobPositions.isEmpty()) {
                val positions = runCatching { jobPositionRepository.getJobPositions(companyId) }.getOrElse { emptyList() }
                _uiState.update { it.copy(jobPositions = positions.filter { p -> p.active }) }
            }
            if (_uiState.value.users.isEmpty()) {
                val users = runCatching { authRepository.getUsersForOrg(companyId) }.getOrElse { emptyList() }
                _uiState.update { it.copy(users = users) }
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

    fun saveEmployee(companyId: String, employee: Employee, photoBytes: ByteArray? = null) {
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
            runCatching { employeeRepository.saveEmployee(companyId, finalEmployee) }
                .onSuccess { _uiState.update { it.copy(isLoading = false, isSaved = true) } }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, error = err.message) } }
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
            _uiState.update { it.copy(shiftClockInEmployee = employee, isShiftClockInLoading = true, shiftTodayRecord = null) }
            val today = runCatching { attendanceRepository.getTodayRecord(companyId, employee.id) }.getOrNull()
            _uiState.update { it.copy(shiftTodayRecord = today, isShiftClockInLoading = false) }
        }
    }

    fun clearShiftEmployee() {
        _uiState.update { it.copy(shiftClockInEmployee = null, shiftTodayRecord = null, isShiftClockInLoading = false) }
    }

    fun clockInShiftEmployee(companyId: String) {
        val employee = _uiState.value.shiftClockInEmployee ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isShiftClockInLoading = true, error = null) }
            runCatching { attendanceRepository.clockIn(companyId, employee.id, employee.startTime) }
                .onSuccess { record ->
                    _uiState.update { it.copy(shiftTodayRecord = record, isShiftClockInLoading = false, shiftClockInSuccess = true) }
                }
                .onFailure { err -> _uiState.update { it.copy(isShiftClockInLoading = false, error = err.message) } }
        }
    }

    fun clockOutShiftEmployee(companyId: String) {
        val employee = _uiState.value.shiftClockInEmployee ?: return
        val recordId = _uiState.value.shiftTodayRecord?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isShiftClockInLoading = true, error = null) }
            runCatching { attendanceRepository.clockOut(companyId, employee.id, recordId) }
                .onSuccess { record ->
                    _uiState.update { it.copy(shiftTodayRecord = record, isShiftClockInLoading = false, shiftClockInSuccess = true) }
                }
                .onFailure { err -> _uiState.update { it.copy(isShiftClockInLoading = false, error = err.message) } }
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

    fun savePayroll(companyId: String, employeeId: String, record: PayrollRecord) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPayrollLoading = true, error = null) }
            runCatching { payrollRepository.savePayroll(companyId, employeeId, record) }
                .onSuccess { _uiState.update { it.copy(isPayrollLoading = false, isPayrollSaved = true) } }
                .onFailure { err -> _uiState.update { it.copy(isPayrollLoading = false, error = err.message) } }
        }
    }

    fun deletePayroll(companyId: String, employeeId: String, payrollId: String) {
        viewModelScope.launch {
            runCatching { payrollRepository.deletePayroll(companyId, employeeId, payrollId) }
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
    fun clearSaved() { _uiState.update { it.copy(isSaved = false) } }
    fun clearDeleted() { _uiState.update { it.copy(isDeleted = false) } }
    fun clearPayrollSaved() { _uiState.update { it.copy(isPayrollSaved = false) } }
    fun clearPayrollDeleted() { _uiState.update { it.copy(isPayrollDeleted = false) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
