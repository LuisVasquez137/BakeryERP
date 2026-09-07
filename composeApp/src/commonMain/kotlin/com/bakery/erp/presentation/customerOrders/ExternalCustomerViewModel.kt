package com.bakery.erp.presentation.customerOrders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.ExternalCustomerRepository
import com.bakery.erp.domain.PriceListRepository
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.ExternalCustomer
import com.bakery.erp.model.PriceList
import com.bakery.erp.model.ProductionShift
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExternalCustomerUiState(
    val customers: List<ExternalCustomer> = emptyList(),
    val selectedCustomer: ExternalCustomer? = null,
    val priceLists: List<PriceList> = emptyList(),
    val producingBranches: List<Branch> = emptyList(),
    /** Mapa `producingBranchId → turnos` cacheado para no recargar al expandir cada card. */
    val shiftsByBranch: Map<String, List<ProductionShift>> = emptyMap(),
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val isApproved: Boolean = false,
    val isRejected: Boolean = false
)

class ExternalCustomerViewModel(
    private val customerRepo: ExternalCustomerRepository,
    private val priceListRepo: PriceListRepository,
    private val branchRepo: BranchRepository,
    private val productionRepo: ProductionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExternalCustomerUiState())
    val uiState: StateFlow<ExternalCustomerUiState> = _uiState.asStateFlow()

    fun loadCustomers(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { customerRepo.getCustomers(companyId) }
                .onSuccess { _uiState.value = _uiState.value.copy(customers = it, isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun loadPriceLists(companyId: String) {
        viewModelScope.launch {
            runCatching { priceListRepo.getLists(companyId) }
                .onSuccess { _uiState.value = _uiState.value.copy(priceLists = it) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun selectCustomer(customer: ExternalCustomer?) {
        _uiState.value = _uiState.value.copy(selectedCustomer = customer)
    }

    fun loadCustomerById(companyId: String, customerId: String) {
        if (customerId.isEmpty()) {
            _uiState.value = _uiState.value.copy(selectedCustomer = null)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDetailLoading = true)
            runCatching { customerRepo.getCustomer(companyId, customerId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        selectedCustomer = it,
                        isDetailLoading = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isDetailLoading = false,
                        error = it.message
                    )
                }
        }
    }

    fun saveCustomer(companyId: String, customer: ExternalCustomer) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { customerRepo.saveCustomer(companyId, customer) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
                    loadCustomers(companyId)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun deleteCustomer(companyId: String, customerId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { customerRepo.deleteCustomer(companyId, customerId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isDeleted = true)
                    loadCustomers(companyId)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun loadApprovalContext(companyId: String) {
        viewModelScope.launch {
            runCatching {
                val branches = branchRepo.getBranches(companyId)
                    .filter { it.active && BranchCapability.CAN_PRODUCE in it.capabilities }
                val lists = priceListRepo.getLists(companyId)
                branches to lists
            }.onSuccess { (branches, lists) ->
                _uiState.value = _uiState.value.copy(
                    producingBranches = branches,
                    priceLists = lists
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(error = it.message)
            }
        }
    }

    fun loadShiftsFor(companyId: String, producingBranchId: String) {
        if (producingBranchId.isEmpty()) return
        if (_uiState.value.shiftsByBranch.containsKey(producingBranchId)) return
        viewModelScope.launch {
            runCatching { productionRepo.getShiftsForBranch(companyId, producingBranchId) }
                .onSuccess { shifts ->
                    _uiState.value = _uiState.value.copy(
                        shiftsByBranch = _uiState.value.shiftsByBranch + (producingBranchId to shifts)
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun approveReseller(companyId: String, customer: ExternalCustomer) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { customerRepo.approveReseller(companyId, customer) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isApproved = true)
                    loadCustomers(companyId)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                }
        }
    }

    fun rejectReseller(companyId: String, customer: ExternalCustomer) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { customerRepo.rejectReseller(companyId, customer) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isRejected = true)
                    loadCustomers(companyId)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                }
        }
    }

    fun clearSaved() { _uiState.value = _uiState.value.copy(isSaved = false) }
    fun clearDeleted() { _uiState.value = _uiState.value.copy(isDeleted = false) }
    fun clearApproved() { _uiState.value = _uiState.value.copy(isApproved = false) }
    fun clearRejected() { _uiState.value = _uiState.value.copy(isRejected = false) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
