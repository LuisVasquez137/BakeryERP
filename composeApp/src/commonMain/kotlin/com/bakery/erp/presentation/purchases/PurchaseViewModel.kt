package com.bakery.erp.presentation.purchases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.PurchaseRepository
import com.bakery.erp.domain.SupplierRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseStatus
import com.bakery.erp.model.StockMovement
import com.bakery.erp.model.Supplier
import com.bakery.erp.model.User
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PurchaseUiState(
    // Lista
    val purchases: List<Purchase> = emptyList(),
    val searchQuery: String = "",
    val branchFilter: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    // Detalle
    val selectedPurchase: Purchase? = null,
    val selectedPurchaseLines: List<PurchaseLine> = emptyList(),
    val isDetailLoading: Boolean = false,
    // Formulario — paso 1: sucursal
    val accessibleBranches: List<Branch> = emptyList(),
    val selectedBranch: Branch? = null,
    val isLoadingBranches: Boolean = false,
    // Formulario — paso 2: proveedor
    val suppliers: List<Supplier> = emptyList(),
    val selectedSupplier: Supplier? = null,
    // Formulario — paso 3: líneas de insumos
    val supplierIngredients: List<Ingredient> = emptyList(),
    val isLoadingIngredients: Boolean = false,
    val draftLines: List<PurchaseLine> = emptyList()
)

class PurchaseViewModel(
    private val purchaseRepository: PurchaseRepository,
    private val ingredientRepository: IngredientRepository,
    private val supplierRepository: SupplierRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseUiState())
    val uiState: StateFlow<PurchaseUiState> = _uiState.asStateFlow()

    fun loadPurchases(companyId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { purchaseRepository.getPurchases(companyId) }
                .onSuccess { all ->
                    val filtered = all.filter { currentUser.canAccessBranch(it.branchId) }
                    _uiState.update { it.copy(purchases = filtered, isLoading = false) }
                }
                .onFailure { _uiState.update { s -> s.copy(isLoading = false, error = it.message) } }
        }
    }

    fun loadPurchaseDetail(companyId: String, purchaseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true) }
            val purchase = runCatching { purchaseRepository.getPurchase(companyId, purchaseId) }.getOrNull()
            val lines = if (purchase != null)
                runCatching { purchaseRepository.getLines(companyId, purchaseId) }.getOrElse { emptyList() }
            else emptyList()
            _uiState.update { it.copy(selectedPurchase = purchase, selectedPurchaseLines = lines, isDetailLoading = false) }
        }
    }

    fun selectPurchase(purchase: Purchase, companyId: String) {
        _uiState.update { it.copy(selectedPurchase = purchase, selectedPurchaseLines = emptyList(), isDetailLoading = true) }
        viewModelScope.launch {
            val lines = runCatching { purchaseRepository.getLines(companyId, purchase.id) }.getOrElse { emptyList() }
            _uiState.update { it.copy(selectedPurchaseLines = lines, isDetailLoading = false) }
        }
    }

    fun clearSelectedPurchase() {
        _uiState.update { it.copy(selectedPurchase = null, selectedPurchaseLines = emptyList()) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setBranchFilter(branchId: String?) {
        _uiState.update { it.copy(branchFilter = branchId) }
    }

    fun loadFormData(companyId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBranches = true) }
            val allBranches = runCatching { branchRepository.getBranches(companyId) }.getOrElse { emptyList() }
            val accessible = allBranches.filter { branch ->
                branch.active &&
                currentUser.canAccessBranch(branch.id) &&
                (BranchCapability.CAN_PRODUCE in branch.capabilities ||
                 BranchCapability.IS_WAREHOUSE in branch.capabilities)
            }
            val suppliers = runCatching { supplierRepository.getSuppliers(companyId) }.getOrElse { emptyList() }
                .filter { it.active }
            _uiState.update {
                it.copy(
                    accessibleBranches = accessible,
                    selectedBranch = if (accessible.size == 1) accessible.first() else it.selectedBranch,
                    suppliers = suppliers,
                    isLoadingBranches = false
                )
            }
        }
    }

    fun selectBranch(branch: Branch?) {
        _uiState.update { it.copy(selectedBranch = branch) }
    }

    fun selectSupplier(companyId: String, supplier: Supplier?) {
        _uiState.update { it.copy(selectedSupplier = supplier, draftLines = emptyList(), supplierIngredients = emptyList()) }
        if (supplier != null) loadIngredientsBySupplier(companyId, supplier.id)
    }

    private fun loadIngredientsBySupplier(companyId: String, supplierId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingIngredients = true) }
            val all = runCatching { ingredientRepository.getIngredients(companyId) }.getOrElse { emptyList() }
            val filtered = all.filter { ing ->
                ing.active && ing.purchaseFormats.any { fmt -> fmt.supplierId == supplierId }
            }
            _uiState.update { it.copy(supplierIngredients = filtered, isLoadingIngredients = false) }
        }
    }

    fun updateDraftLine(line: PurchaseLine) {
        _uiState.update { state ->
            val existing = state.draftLines.find { it.ingredientId == line.ingredientId }
            val updated = if (existing != null) {
                state.draftLines.map { if (it.ingredientId == line.ingredientId) line else it }
            } else {
                state.draftLines + line
            }
            state.copy(draftLines = updated.filter { it.quantityReceived > 0.0 })
        }
    }

    fun savePurchase(companyId: String, currentUser: User, invoiceNumber: String?, notes: String?, date: Long, status: String = PurchaseStatus.RECEIVED_PENDING_PAYMENT) {
        viewModelScope.launch {
            val branch = _uiState.value.selectedBranch ?: return@launch
            val supplier = _uiState.value.selectedSupplier ?: return@launch
            val lines = _uiState.value.draftLines
            if (lines.isEmpty()) return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val purchaseId = generateId()
                val purchase = Purchase(
                    id = purchaseId,
                    branchId = branch.id,
                    branchName = branch.name,
                    supplierId = supplier.id,
                    supplierName = supplier.company,
                    date = date,
                    notes = notes?.trim()?.ifEmpty { null },
                    invoiceNumber = invoiceNumber?.trim()?.ifEmpty { null },
                    receivedById = currentUser.id,
                    receivedByName = currentUser.displayName.ifEmpty { currentUser.email },
                    status = status
                )
                purchaseRepository.savePurchase(companyId, purchase, lines)
                lines.forEach { line ->
                    // Actualizar precio del formato si el usuario lo modificó
                    if (line.formatId.isNotEmpty() && line.packageCost > 0) {
                        val ingredient = _uiState.value.supplierIngredients.find { it.id == line.ingredientId }
                        val originalFormat = ingredient?.purchaseFormats?.find { it.id == line.formatId }
                        if (originalFormat != null && originalFormat.packageCost != line.packageCost) {
                            runCatching {
                                ingredientRepository.updatePurchaseFormatCost(
                                    companyId, line.ingredientId, line.formatId, line.packageCost
                                )
                            }
                        }
                    }
                    // Actualizar stock
                    val stocks = ingredientRepository.getBranchStocksForIngredient(
                        companyId, line.ingredientId, listOf(branch.id)
                    )
                    val prevStock = stocks.find { it.branchId == branch.id }?.currentStock ?: 0.0
                    ingredientRepository.updateBranchStock(
                        companyId, line.ingredientId, line.ingredientName,
                        branch.id, branch.name, line.quantityReceived
                    )
                    ingredientRepository.addStockMovement(
                        companyId, line.ingredientId, StockMovement(
                            type = "COMPRA",
                            quantity = line.quantityReceived,
                            balanceBefore = prevStock,
                            balanceAfter = prevStock + line.quantityReceived,
                            branchId = branch.id,
                            branchName = branch.name,
                            reference = purchaseId,
                            date = date
                        )
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSaved = true, draftLines = emptyList()) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message ?: "Error al guardar la compra") }
            }
        }
    }

    private fun generateId(): String {
        val hex = "0123456789abcdef"
        return Random.nextBytes(16).joinToString("") { b ->
            hex[(b.toInt() shr 4) and 0xF].toString() + hex[b.toInt() and 0xF]
        }
    }

    fun resetForm() {
        _uiState.update {
            it.copy(
                selectedBranch = if (it.accessibleBranches.size == 1) it.accessibleBranches.first() else null,
                selectedSupplier = null,
                supplierIngredients = emptyList(),
                draftLines = emptyList(),
                isSaved = false,
                error = null
            )
        }
    }

    fun updatePurchaseStatus(companyId: String, purchaseId: String, newStatus: String) {
        viewModelScope.launch {
            runCatching { purchaseRepository.updatePurchaseStatus(companyId, purchaseId, newStatus) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            selectedPurchase = state.selectedPurchase?.takeIf { it.id == purchaseId }
                                ?.copy(status = newStatus) ?: state.selectedPurchase,
                            purchases = state.purchases.map { if (it.id == purchaseId) it.copy(status = newStatus) else it }
                        )
                    }
                }
                .onFailure { err -> _uiState.update { s -> s.copy(error = err.message ?: "Error al actualizar el estado") } }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
