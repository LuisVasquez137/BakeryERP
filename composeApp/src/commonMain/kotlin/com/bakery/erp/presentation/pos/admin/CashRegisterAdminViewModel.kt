package com.bakery.erp.presentation.pos.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.CashRegisterRepository
import com.bakery.erp.domain.CashSessionRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.CashRegister
import com.bakery.erp.model.CashSessionStatus
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CashRegisterAdminUiState(
    val branches: List<Branch> = emptyList(),
    val registers: List<CashRegister> = emptyList(),
    val selectedRegister: CashRegister? = null,
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    /** Mensaje de éxito tras eliminar/archivar (para snackbar informativo). */
    val deleteMessage: String? = null
)

class CashRegisterAdminViewModel(
    private val registerRepository: CashRegisterRepository,
    private val branchRepository: BranchRepository,
    private val sessionRepository: CashSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashRegisterAdminUiState())
    val uiState: StateFlow<CashRegisterAdminUiState> = _uiState.asStateFlow()

    fun load(companyId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val allBranches = branchRepository.getBranches(companyId)
                val accessibleSellingBranches = allBranches
                    .filter { it.active }
                    .filter { currentUser.canAccessBranch(it.id) }
                    .filter { BranchCapability.CAN_SELL in it.capabilities }
                    .sortedBy { it.name }
                val registers = registerRepository.getAll(companyId)
                    .filter { reg -> accessibleSellingBranches.any { it.id == reg.branchId } }
                    .sortedBy { it.branchName + " " + it.name }
                accessibleSellingBranches to registers
            }.onSuccess { (branches, registers) ->
                _uiState.update { it.copy(isLoading = false, branches = branches, registers = registers) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun loadRegister(companyId: String, registerId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, error = null) }
            runCatching { registerRepository.get(companyId, registerId) }
                .onSuccess { reg ->
                    _uiState.update { it.copy(isDetailLoading = false, selectedRegister = reg) }
                }.onFailure { err ->
                    _uiState.update { it.copy(isDetailLoading = false, error = err.message) }
                }
        }
    }

    fun clearSelected() { _uiState.update { it.copy(selectedRegister = null) } }

    fun saveRegister(companyId: String, register: CashRegister) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { registerRepository.upsert(companyId, register) }
                .onSuccess { _uiState.update { it.copy(isLoading = false, isSaved = true) } }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, error = err.message) } }
        }
    }

    /**
     * Borrado seguro híbrido. Bloquea si hay sesión abierta o cuadre pendiente.
     * Si la caja nunca tuvo sesiones → borrado físico. Si tiene historial → se archiva
     * (soft-delete) para preservar las ventas/sesiones que la referencian.
     */
    fun attemptDeleteRegister(companyId: String, registerId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                // 1. Sesión abierta → bloqueo.
                val open = sessionRepository.getOpenSessionForRegister(companyId, registerId)
                if (open != null) {
                    error("Esta caja tiene una sesión abierta por ${open.cashierName}. Ciérrala antes de eliminarla.")
                }
                // 2. Sesiones cerradas sin cuadre del día → bloqueo.
                val sessions = sessionRepository.getSessionsForRegister(companyId, registerId)
                val pendingClose = sessions.any {
                    it.status == CashSessionStatus.CLOSED && it.dailyCloseId == null
                }
                if (pendingClose) {
                    error("Esta caja tiene sesiones sin cuadre del día. Realiza el cuadre antes de eliminarla.")
                }
                // 3. Decidir borrado físico vs archivar.
                if (sessions.isEmpty()) {
                    registerRepository.delete(companyId, registerId)
                    "Caja eliminada."
                } else {
                    registerRepository.archive(companyId, registerId)
                    "Caja archivada (se conserva su historial de ventas)."
                }
            }.onSuccess { message ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isDeleted = true,
                        deleteMessage = message,
                        registers = it.registers.filterNot { reg -> reg.id == registerId },
                        selectedRegister = null
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun clearSaved() { _uiState.update { it.copy(isSaved = false) } }
    /** Resetea solo la bandera de navegación; conserva [deleteMessage] para el snackbar de la lista. */
    fun clearDeleted() { _uiState.update { it.copy(isDeleted = false) } }
    fun clearDeleteMessage() { _uiState.update { it.copy(deleteMessage = null) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
