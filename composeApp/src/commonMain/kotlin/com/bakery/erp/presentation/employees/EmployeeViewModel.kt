package com.bakery.erp.presentation.employees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.Employee
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmployeeUiState(
    val employees: List<Employee> = emptyList(),
    val selectedEmployee: Employee? = null,
    val branches: List<Branch> = emptyList(),
    val branchFilter: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null
)

class EmployeeViewModel(
    private val employeeRepository: EmployeeRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmployeeUiState())
    val uiState: StateFlow<EmployeeUiState> = _uiState.asStateFlow()

    fun loadEmployees(companyId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val employees = runCatching { employeeRepository.getEmployees(companyId) }.getOrElse { emptyList() }
            val branches = runCatching { branchRepository.getBranches(companyId) }.getOrElse { emptyList() }
            _uiState.update {
                it.copy(
                    employees = employees,
                    branches = branches.filter { b -> b.active },
                    isLoading = false
                )
            }
        }
    }

    fun loadEmployeeDetail(companyId: String, employeeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true) }
            val employee = runCatching { employeeRepository.getEmployee(companyId, employeeId) }.getOrNull()
            _uiState.update { it.copy(selectedEmployee = employee, isDetailLoading = false) }
        }
    }

    fun selectEmployee(employee: Employee) {
        _uiState.update { it.copy(selectedEmployee = employee) }
    }

    fun clearSelected() {
        _uiState.update { it.copy(selectedEmployee = null) }
    }

    fun setBranchFilter(branchId: String?) {
        _uiState.update { it.copy(branchFilter = branchId) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun saveEmployee(companyId: String, employee: Employee) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { employeeRepository.saveEmployee(companyId, employee) }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isSaved = true) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
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

    fun clearSaved() { _uiState.update { it.copy(isSaved = false) } }
    fun clearDeleted() { _uiState.update { it.copy(isDeleted = false) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
