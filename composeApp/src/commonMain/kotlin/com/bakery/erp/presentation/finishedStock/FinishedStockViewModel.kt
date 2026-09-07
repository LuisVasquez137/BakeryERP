package com.bakery.erp.presentation.finishedStock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.FinishedMovementReference
import com.bakery.erp.model.FinishedMovementType
import com.bakery.erp.model.FinishedProductStock
import com.bakery.erp.model.FinishedStockMovement
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FinishedStockUiState(
    val branches: List<Branch> = emptyList(),
    val selectedBranchId: String = "",
    val stocks: List<FinishedProductStock> = emptyList(),
    val movements: List<FinishedStockMovement> = emptyList(),
    val isLoading: Boolean = false,
    val isMovementsLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

class FinishedStockViewModel(
    private val repository: FinishedStockRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinishedStockUiState())
    val uiState: StateFlow<FinishedStockUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    /** Carga sucursales accesibles para el usuario que tengan capacidad de venta o almacén. */
    fun loadBranches(companyId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { branchRepository.getBranches(companyId) }
                .onSuccess { all ->
                    val accessible = all
                        .filter { it.active }
                        .filter { currentUser.canAccessBranch(it.id) }
                        .filter {
                            BranchCapability.CAN_SELL in it.capabilities ||
                                BranchCapability.IS_WAREHOUSE in it.capabilities ||
                                BranchCapability.CAN_PRODUCE in it.capabilities
                        }
                        .sortedBy { it.name }
                    val current = _uiState.value.selectedBranchId
                    val nextSelected = if (current.isNotEmpty() && accessible.any { it.id == current }) current
                                       else accessible.firstOrNull()?.id ?: ""
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            branches = accessible,
                            selectedBranchId = nextSelected
                        )
                    }
                    if (nextSelected.isNotEmpty()) observeStocks(companyId, nextSelected)
                }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, error = err.message) } }
        }
    }

    fun selectBranch(companyId: String, branchId: String) {
        if (branchId == _uiState.value.selectedBranchId) return
        _uiState.update { it.copy(selectedBranchId = branchId, stocks = emptyList()) }
        observeStocks(companyId, branchId)
    }

    /** Listener en vivo del stock terminado en la sucursal seleccionada. */
    private fun observeStocks(companyId: String, branchId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.observeStocksForBranch(companyId, branchId)
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { list ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            stocks = list.sortedBy { s -> s.productName }
                        )
                    }
                }
        }
    }

    fun loadMovements(companyId: String, branchId: String, productId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMovementsLoading = true, movements = emptyList(), error = null) }
            runCatching { repository.getMovementsForProduct(companyId, branchId, productId) }
                .onSuccess { list -> _uiState.update { it.copy(isMovementsLoading = false, movements = list) } }
                .onFailure { err -> _uiState.update { it.copy(isMovementsLoading = false, error = err.message) } }
        }
    }

    /** Registra una merma o ajuste manual. `signedDelta` < 0 para salidas (merma, ajuste por menos),
     *  > 0 para ingresos puntuales (raros — ajuste por más, encontrado). */
    fun registerAdjustment(
        companyId: String,
        productId: String,
        productName: String,
        branchId: String,
        branchName: String,
        signedDelta: Double,
        type: String,
        reason: String,
        currentUser: User
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val refType = when (type) {
                    FinishedMovementType.MERMA -> FinishedMovementReference.WASTAGE
                    FinishedMovementType.AJUSTE -> FinishedMovementReference.ADJUSTMENT
                    FinishedMovementType.TRANSFERENCIA -> FinishedMovementReference.TRANSFER
                    FinishedMovementType.VENTA -> FinishedMovementReference.SALE
                    else -> FinishedMovementReference.ADJUSTMENT
                }
                repository.applyMovement(
                    companyId,
                    FinishedStockMovement(
                        productId = productId,
                        productName = productName,
                        branchId = branchId,
                        branchName = branchName,
                        type = type,
                        quantity = signedDelta,
                        unitPriceAtMovement = 0.0,
                        referenceType = refType,
                        referenceId = "",
                        reason = reason.trim().ifEmpty { null },
                        createdByUserId = currentUser.id,
                        createdByUserName = currentUser.displayName
                    )
                )
            }.onSuccess { _uiState.update { it.copy(isLoading = false, isSaved = true) } }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, error = err.message) } }
        }
    }

    fun clearSaved() { _uiState.update { it.copy(isSaved = false) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
