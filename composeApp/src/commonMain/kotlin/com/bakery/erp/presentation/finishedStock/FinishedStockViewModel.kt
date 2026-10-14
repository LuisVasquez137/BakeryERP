package com.bakery.erp.presentation.finishedStock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.model.Product
import com.bakery.erp.model.StoreInventoryRow
import com.bakery.erp.model.buildStoreInventoryRows
import com.bakery.erp.model.finishedStockDocId
import com.bakery.erp.model.isIngredientBacked
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
import com.bakery.erp.model.isPortion
import com.bakery.erp.model.PortioningRequest

data class FinishedStockUiState(
    val branches: List<Branch> = emptyList(),
    val selectedBranchId: String = "",
    /**
     * Documentos de stock de la sucursal MÁS un registro en 0 por cada producto de reventa que
     * todavía no tuvo movimiento. Los ceros van acá y no solo en [rows] porque el formulario de
     * ajuste elige producto de esta lista: sin ellos no habría forma de cargar el conteo inicial
     * de las gaseosas que ya estaban en el estante antes de usar el sistema.
     */
    val stocks: List<FinishedProductStock> = emptyList(),
    /** Lo que muestra la lista. Ver `buildStoreInventoryRows`. */
    val rows: List<StoreInventoryRow> = emptyList(),
    val movements: List<FinishedStockMovement> = emptyList(),
    val isLoading: Boolean = false,
    val isMovementsLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    /** Productos porción activos ("Porción de selva negra"), para "Porcionar pastel". */
    val portionProducts: List<Product> = emptyList(),
    val isPortioning: Boolean = false,
    /** Aviso de un corte que salió bien, para el snackbar. */
    val notice: String? = null
)

class FinishedStockViewModel(
    private val repository: FinishedStockRepository,
    private val branchRepository: BranchRepository,
    // El catálogo de reventa decide qué se lista aunque no tenga stock, y el insumo vinculado
    // (huevo por docena) da el número de los productos que no llevan inventario propio.
    private val productRepository: ProductRepository,
    private val ingredientRepository: IngredientRepository
) : ViewModel() {

    private var products: List<Product> = emptyList()
    private var productsCompanyId: String = ""

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
            // Una lectura por apertura, no un listener: el catálogo y el stock de insumos
            // cambian poco mientras se mira esta pantalla, y el número que sí se mueve en vivo
            // —el terminado— sigue llegando por el snapshot de abajo. Se relee en cada
            // apertura y no se cachea: el VM es singleton, y un producto de reventa dado de
            // alta después no aparecería hasta reiniciar la app.
            products = runCatching { productRepository.getProducts(companyId) }
                .getOrElse { if (productsCompanyId == companyId) products else emptyList() }
            productsCompanyId = companyId
            val ingredientStock = if (products.any { !it.isInHouseProduced && it.isIngredientBacked }) {
                runCatching { ingredientRepository.getBranchStocksForBranch(companyId, branchId) }
                    .getOrElse { emptyMap() }
            } else emptyMap()
            val branchName = _uiState.value.branches.firstOrNull { it.id == branchId }?.name.orEmpty()
            repository.observeStocksForBranch(companyId, branchId)
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { list ->
                    val withDoc = list.mapTo(HashSet()) { it.productId }
                    val zeros = products
                        .filter { !it.isInHouseProduced && it.active && !it.isIngredientBacked && it.id !in withDoc }
                        .map {
                            FinishedProductStock(
                                id = finishedStockDocId(branchId, it.id),
                                branchId = branchId,
                                branchName = branchName,
                                productId = it.id,
                                productName = it.name
                            )
                        }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            stocks = (list + zeros).sortedBy { s -> s.productName },
                            portionProducts = products.filter { p -> p.active && p.isPortion },
                            rows = buildStoreInventoryRows(list, products, ingredientStock)
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

    /** Corta enteros en porciones. Ver `FinishedStockRepository.applyPortioning`. */
    fun cutIntoPortions(
        companyId: String,
        portion: Product,
        wholes: Int,
        portionsPerWhole: Int,
        currentUser: User
    ) {
        val st = _uiState.value
        if (st.isPortioning) return
        val branch = st.branches.firstOrNull { it.id == st.selectedBranchId } ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isPortioning = true, error = null) }
            runCatching {
                repository.applyPortioning(
                    companyId,
                    PortioningRequest(
                        branchId = branch.id,
                        branchName = branch.name,
                        portion = portion,
                        wholes = wholes,
                        portionsPerWhole = portionsPerWhole,
                        userId = currentUser.id,
                        userName = currentUser.displayName
                    )
                )
            }.onSuccess { n ->
                _uiState.update {
                    it.copy(isPortioning = false, notice = "$n × ${portion.name} al inventario")
                }
            }.onFailure { err -> _uiState.update { it.copy(isPortioning = false, error = err.message) } }
        }
    }

    fun clearNotice() { _uiState.update { it.copy(notice = null) } }
    fun clearSaved() { _uiState.update { it.copy(isSaved = false) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
