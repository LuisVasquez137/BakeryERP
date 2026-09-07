package com.bakery.erp.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla de configuración inicial del revendedor (post-signup,
 * pre-aprobación). Sirve para cargar las sucursales que el revendedor puede
 * elegir y la flag de la org que decide si se ofrece entrega a domicilio.
 */
data class ResellerSetupUiState(
    val branches: List<Branch> = emptyList(),
    val acceptsResellerDelivery: Boolean = false,
    val orgName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class ResellerSetupViewModel(
    private val authRepository: AuthRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResellerSetupUiState())
    val uiState: StateFlow<ResellerSetupUiState> = _uiState.asStateFlow()

    fun load(orgId: String) {
        if (orgId.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val org = authRepository.getOrganization(orgId).getOrThrow()
                // Solo sucursales con capacidad de vender (donde se puede pasar a recoger).
                val branches = branchRepository.getBranches(orgId)
                    .filter { it.active && BranchCapability.CAN_SELL in it.capabilities }
                Triple(org.acceptsResellerDelivery, org.name, branches)
            }.onSuccess { (acceptsDelivery, orgName, branches) ->
                _uiState.value = _uiState.value.copy(
                    branches = branches,
                    acceptsResellerDelivery = acceptsDelivery,
                    orgName = orgName,
                    isLoading = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error al cargar la configuración"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
