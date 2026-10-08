package com.bakery.erp.presentation.operator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.EmployeeAccessRepository
import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.domain.VerifiedEmployee
import com.bakery.erp.domain.pinProblem
import com.bakery.erp.model.Employee
import com.bakery.erp.model.EmployeePinStatus
import com.bakery.erp.model.EmployeeStatus
import com.bakery.erp.model.OperatorSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LockStep {
    /** Escoger quién es. */
    PICK,
    /** Escribir su PIN. */
    PIN,
    /** PIN temporal: escoger uno propio... */
    NEW_PIN,
    /** ...y repetirlo. */
    CONFIRM_NEW_PIN
}

data class OperatorLockUiState(
    val employees: List<Employee> = emptyList(),
    val isLoading: Boolean = false,
    val selected: Employee? = null,
    val step: LockStep = LockStep.PICK,
    val isWorking: Boolean = false,
    val error: String? = null,
    /** Lista para entrar: la pantalla la pasa a AuthViewModel y llama [OperatorLockViewModel.reset]. */
    val session: OperatorSession? = null
)

/**
 * Pantalla de PIN de un equipo de sucursal: quién es, su PIN y, si el PIN era temporal,
 * escoger uno propio. La verificación es del servidor (empleadoVerificarPin); aquí solo
 * se arma el flujo.
 */
class OperatorLockViewModel(
    private val employeeRepository: EmployeeRepository,
    private val accessRepository: EmployeeAccessRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OperatorLockUiState())
    val uiState: StateFlow<OperatorLockUiState> = _uiState.asStateFlow()

    // El PIN temporal ya verificado y el nuevo escogido: solo en memoria, mientras dura
    // el cambio. No van al estado para que nada los pinte ni los guarde.
    private var verified: VerifiedEmployee? = null
    private var currentPin: String = ""
    private var newPin: String = ""

    fun load(companyId: String) {
        if (companyId.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.employees.isEmpty(), error = null) }
            runCatching { employeeRepository.getEmployees(companyId) }
                .onSuccess { list ->
                    // Solo quien puede entrar: activo y con PIN.
                    val usable = list.filter { it.status == EmployeeStatus.ACTIVE && it.pinStatus != EmployeePinStatus.NONE }
                    _uiState.update { it.copy(employees = usable, isLoading = false) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "No se pudo cargar la lista") } }
        }
    }

    fun select(employee: Employee) {
        clearSecrets()
        _uiState.update { it.copy(selected = employee, step = LockStep.PIN, error = null) }
    }

    fun back() {
        clearSecrets()
        _uiState.update { it.copy(selected = null, step = LockStep.PICK, error = null, isWorking = false) }
    }

    fun submit(companyId: String, accountId: String, pin: String) {
        val employee = _uiState.value.selected ?: return
        when (_uiState.value.step) {
            LockStep.PICK -> Unit
            LockStep.PIN -> verify(companyId, accountId, employee, pin)
            LockStep.NEW_PIN -> {
                val problem = pinProblem(pin) ?: if (pin == currentPin) "Escoge un PIN distinto del temporal." else null
                if (problem != null) { _uiState.update { it.copy(error = problem) }; return }
                newPin = pin
                _uiState.update { it.copy(step = LockStep.CONFIRM_NEW_PIN, error = null) }
            }
            LockStep.CONFIRM_NEW_PIN -> {
                if (pin != newPin) {
                    newPin = ""
                    _uiState.update { it.copy(step = LockStep.NEW_PIN, error = "No coinciden. Escríbelo de nuevo.") }
                    return
                }
                changeTemporaryPin(companyId, accountId, employee)
            }
        }
    }

    private fun verify(companyId: String, accountId: String, employee: Employee, pin: String) {
        if (pin.length < 4) { _uiState.update { it.copy(error = "El PIN tiene al menos 4 números.") }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            runCatching { accessRepository.verifyPin(companyId, employee.id, pin) }
                .onSuccess { v ->
                    if (v.mustChangePin) {
                        verified = v
                        currentPin = pin
                        _uiState.update { it.copy(isWorking = false, step = LockStep.NEW_PIN) }
                    } else {
                        _uiState.update { it.copy(isWorking = false, session = v.toSession(accountId)) }
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isWorking = false, error = e.message ?: "No se pudo verificar el PIN") } }
        }
    }

    private fun changeTemporaryPin(companyId: String, accountId: String, employee: Employee) {
        val v = verified ?: return back()
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            runCatching { accessRepository.changePin(companyId, employee.id, currentPin, newPin) }
                .onSuccess {
                    clearSecrets()
                    _uiState.update { it.copy(isWorking = false, session = v.toSession(accountId)) }
                }
                .onFailure { e ->
                    newPin = ""
                    _uiState.update { it.copy(isWorking = false, step = LockStep.NEW_PIN, error = e.message ?: "No se pudo cambiar el PIN") }
                }
        }
    }

    /** La sesión ya pasó a AuthViewModel: la próxima vez la pantalla arranca de cero. */
    fun reset() {
        clearSecrets()
        _uiState.update { it.copy(selected = null, step = LockStep.PICK, session = null, error = null, isWorking = false) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    /**
     * Confirmar con PIN a la persona ya identificada antes de una acción que la deja a su
     * nombre (p. ej. iniciar un lote). No toca el estado de la pantalla de bloqueo.
     */
    suspend fun confirmPin(companyId: String, employeeId: String, pin: String): Result<Unit> =
        runCatching { accessRepository.verifyPin(companyId, employeeId, pin) }.map { }

    private fun clearSecrets() {
        verified = null
        currentPin = ""
        newPin = ""
    }

    private fun VerifiedEmployee.toSession(accountId: String) = OperatorSession(
        employeeId = employeeId,
        name = name,
        photoUrl = photoUrl,
        branchId = branchId,
        permissions = permissions,
        accountId = accountId
    )
}
