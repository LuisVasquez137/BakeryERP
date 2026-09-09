package com.bakery.erp.presentation.organization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.domain.SaleRepository
import com.bakery.erp.model.Organization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

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
    val isRebuildingRollups: Boolean = false,
    val rebuildMessage: String? = null,
    val error: String? = null
)

class OrganizationSettingsViewModel(
    private val authRepository: AuthRepository,
    private val saleRepository: SaleRepository
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

    /**
     * Recalcula los resúmenes diarios de ventas de los últimos [monthsBack] meses.
     *
     * Hace falta una sola vez, al estrenar los resúmenes: las ventas cobradas antes de que
     * existieran no aportaron a ningún resumen, y los reportes leen de ahí — sin esto el
     * histórico se vería vacío. Después queda como reparación, por si un día quedó torcido.
     *
     * Es la operación más cara de la app (lee cada venta del período y sus líneas), por eso
     * es un botón explícito y no algo que corra solo al abrir una pantalla.
     */
    fun rebuildSalesRollups(orgId: String, monthsBack: Int = 24) {
        if (orgId.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRebuildingRollups = true, rebuildMessage = null, error = null)
            val tz = TimeZone.currentSystemDefault()
            val today = Clock.System.now().toLocalDateTime(tz).date
            val from = today.minus(DatePeriod(months = monthsBack)).atStartOfDayIn(tz).toEpochMilliseconds()
            // Hasta el final de hoy: el día en curso también tiene que quedar bien.
            val until = today.plus(DatePeriod(days = 1)).atStartOfDayIn(tz).toEpochMilliseconds()
            runCatching { saleRepository.rebuildDailyRollups(orgId, from, until) }
                .onSuccess { days ->
                    _uiState.value = _uiState.value.copy(
                        isRebuildingRollups = false,
                        rebuildMessage = if (days == 0) "No había ventas que recalcular."
                                         else "Listo: se recalcularon $days días de ventas."
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isRebuildingRollups = false,
                        error = it.message ?: "No se pudo recalcular"
                    )
                }
        }
    }

    fun clearRebuildMessage() { _uiState.value = _uiState.value.copy(rebuildMessage = null) }

    fun clearJustSavedDelivery() {
        _uiState.value = _uiState.value.copy(justSavedDelivery = false)
    }
    fun clearJustRegeneratedCode() {
        _uiState.value = _uiState.value.copy(justRegeneratedCode = false)
    }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
