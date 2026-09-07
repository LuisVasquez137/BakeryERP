package com.bakery.erp.presentation.organization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.model.Organization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Los flujos de compra opcionales. La materia prima no está: es el flujo base. */
enum class PurchaseFlowSetting(val title: String, val on: String, val off: String) {
    RESALE(
        "Reventa",
        "La captura de compra ofrece los productos de reventa del proveedor.",
        "Solo compras de materia prima."
    ),
    SUPPLIES(
        "Suministros de operación",
        "Cloro, papel y desechables se cuentan en inventario y se registran como gasto del mes.",
        "No llevas inventario de suministros."
    ),
    SERVICES(
        "Servicios y gastos",
        "Mantenimientos, combustible y fletes entran por factura y van al estado de resultados.",
        "Esos gastos se capturan aparte, en el módulo de Gastos."
    )
}

data class OrganizationSettingsUiState(
    val organization: Organization? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val justRegeneratedCode: Boolean = false,
    val justSavedDelivery: Boolean = false,
    val error: String? = null
)

class OrganizationSettingsViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizationSettingsUiState())
    val uiState: StateFlow<OrganizationSettingsUiState> = _uiState.asStateFlow()

    fun loadOrganization(orgId: String) {
        if (orgId.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            authRepository.getOrganization(orgId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(organization = it, isLoading = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                }
        }
    }

    fun setAcceptsResellerDelivery(orgId: String, accepts: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            authRepository.setAcceptsResellerDelivery(orgId, accepts)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        organization = _uiState.value.organization?.copy(acceptsResellerDelivery = accepts),
                        isSaving = false,
                        justSavedDelivery = true
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = it.message)
                }
        }
    }

    /**
     * Enciende o apaga un flujo de compra. Se manda el trío completo porque el repositorio
     * escribe los tres campos: mandar solo el que cambió obligaría a un método por bandera.
     */
    fun setPurchaseFlow(orgId: String, flow: PurchaseFlowSetting, enabled: Boolean) {
        val org = _uiState.value.organization ?: return
        val updated = when (flow) {
            PurchaseFlowSetting.RESALE -> org.copy(usesResalePurchases = enabled)
            PurchaseFlowSetting.SUPPLIES -> org.copy(usesSupplyPurchases = enabled)
            PurchaseFlowSetting.SERVICES -> org.copy(usesServicePurchases = enabled)
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            authRepository.setPurchaseFlows(
                orgId,
                updated.usesResalePurchases,
                updated.usesSupplyPurchases,
                updated.usesServicePurchases
            ).onSuccess {
                _uiState.value = _uiState.value.copy(organization = updated, isSaving = false)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isSaving = false, error = it.message)
            }
        }
    }

    fun regenerateInviteCode(orgId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            authRepository.regenerateInviteCode(orgId)
                .onSuccess { newCode ->
                    _uiState.value = _uiState.value.copy(
                        organization = _uiState.value.organization?.copy(inviteCode = newCode),
                        isSaving = false,
                        justRegeneratedCode = true
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = it.message)
                }
        }
    }

    fun clearJustSavedDelivery() {
        _uiState.value = _uiState.value.copy(justSavedDelivery = false)
    }
    fun clearJustRegeneratedCode() {
        _uiState.value = _uiState.value.copy(justRegeneratedCode = false)
    }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
