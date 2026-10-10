package com.bakery.erp.presentation.employees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.EmployeeAccessRepository
import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.domain.pinProblem
import com.bakery.erp.model.Employee
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmployeeAccessUiState(
    /** La ficha leída de nuevo tras cada cambio: el estado del PIN lo escribe el servidor. */
    val employee: Employee? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    /** Se cerró bien el diálogo de PIN: la pantalla lo usa para cerrarlo. */
    val pinSaved: Boolean = false
)

/**
 * Pestaña "Acceso" de la ficha del colaborador: su PIN y lo que puede hacer con él.
 * Separado de [EmployeeViewModel] porque solo OWNER/ADMIN lo usan y no comparte estado.
 */
class EmployeeAccessViewModel(
    private val employeeRepository: EmployeeRepository,
    private val accessRepository: EmployeeAccessRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmployeeAccessUiState())
    val uiState: StateFlow<EmployeeAccessUiState> = _uiState.asStateFlow()

    fun load(companyId: String, employeeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.employee?.id != employeeId, error = null) }
            runCatching { employeeRepository.getEmployee(companyId, employeeId) }
                .onSuccess { e -> _uiState.update { it.copy(employee = e, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun savePermissions(companyId: String, employeeId: String, permissions: Set<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching { employeeRepository.updateEmployeePermissions(companyId, employeeId, permissions.toList()) }
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, message = "Permisos guardados") }
                    load(companyId, employeeId)
                }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message ?: "No se pudieron guardar los permisos") } }
        }
    }

    fun setPin(companyId: String, employeeId: String, pin: String, confirm: String, typedByEmployee: Boolean) {
        pinProblem(pin)?.let { p -> _uiState.update { it.copy(error = p) }; return }
        if (pin != confirm) { _uiState.update { it.copy(error = "Los dos PIN no coinciden.") }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching { accessRepository.setPin(companyId, employeeId, pin, typedByEmployee) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            pinSaved = true,
                            message = if (typedByEmployee) "PIN guardado"
                                else "PIN temporal guardado: la primera vez que lo use, tendrá que cambiarlo"
                        )
                    }
                    load(companyId, employeeId)
                }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message ?: "No se pudo guardar el PIN") } }
        }
    }

    /**
     * Lo que se dejó listo en el alta: se aplica en cuanto la ficha existe (el PIN lo guarda la
     * Cloud Function, que necesita la ficha). Ya se validó el PIN en el formulario.
     */
    fun setupNewEmployee(companyId: String, employeeId: String, permissions: Set<String>, pin: String?, typedByEmployee: Boolean) {
        viewModelScope.launch {
            val problems = buildList {
                if (permissions.isNotEmpty()) runCatching {
                    employeeRepository.updateEmployeePermissions(companyId, employeeId, permissions.toList())
                }.onFailure { add("los permisos") }
                if (pin != null) runCatching {
                    accessRepository.setPin(companyId, employeeId, pin, typedByEmployee)
                }.onFailure { add("el PIN") }
            }
            if (problems.isNotEmpty()) _uiState.update {
                it.copy(error = "No se pudo guardar ${problems.joinToString(" ni ")}. Hazlo desde la pestaña Acceso.")
            }
            load(companyId, employeeId)
        }
    }

    fun clearPinSaved() = _uiState.update { it.copy(pinSaved = false) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }
}
