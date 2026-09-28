package com.bakery.erp.presentation.customerOrders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.CustomerOrderRepository
import com.bakery.erp.domain.ExternalCustomerRepository
import com.bakery.erp.domain.PriceListRepository
import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.CustomerLineSpec
import com.bakery.erp.model.CustomerOrder
import com.bakery.erp.model.CustomerOrderLine
import com.bakery.erp.model.CustomerOrderStatus
import com.bakery.erp.model.CustomerOrderType
import com.bakery.erp.model.ExternalCustomer
import com.bakery.erp.model.PriceList
import com.bakery.erp.model.PriceListType
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.ProductionShift
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CustomerOrderUiState(
    val orders: List<CustomerOrder> = emptyList(),
    val selectedOrder: CustomerOrder? = null,
    val selectedOrderLines: List<CustomerOrderLine> = emptyList(),
    /** Pagos del pedido (anticipos recibidos en caja, saldo al entregar, devoluciones). */
    val selectedOrderPayments: List<com.bakery.erp.model.CustomerOrderPayment> = emptyList(),
    val lotForSelectedOrder: ProductionLot? = null,

    // Form dependencies
    val customers: List<ExternalCustomer> = emptyList(),
    val branches: List<Branch> = emptyList(),
    val producingBranches: List<Branch> = emptyList(),
    val shifts: List<ProductionShift> = emptyList(),
    val products: List<Product> = emptyList(),
    val priceLists: List<PriceList> = emptyList(),

    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isCancelled: Boolean = false,
    val isConfirmed: Boolean = false
)

class CustomerOrderViewModel(
    private val orderRepo: CustomerOrderRepository,
    private val customerRepo: ExternalCustomerRepository,
    private val productionRepo: ProductionRepository,
    private val productRepo: ProductRepository,
    private val branchRepo: BranchRepository,
    private val priceListRepo: PriceListRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerOrderUiState())
    val uiState: StateFlow<CustomerOrderUiState> = _uiState.asStateFlow()

    /** Listener vivo a la colección de pedidos. */
    fun observeOrders(companyId: String) {
        viewModelScope.launch {
            orderRepo.observeOrders(companyId)
                .stateIn(this, SharingStarted.Eagerly, emptyList<CustomerOrder>())
                .collect { list ->
                    _uiState.value = _uiState.value.copy(
                        orders = list.sortedByDescending { it.deliveryDate }
                    )
                }
        }
    }

    fun loadOrdersOnce(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { orderRepo.getOrdersInRange(companyId, 0L, Long.MAX_VALUE) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        orders = it.sortedByDescending { o -> o.deliveryDate },
                        isLoading = false
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun selectOrder(companyId: String, orderId: String) {
        if (orderId.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                selectedOrder = null,
                selectedOrderLines = emptyList(),
                lotForSelectedOrder = null
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDetailLoading = true)
            runCatching {
                val order = orderRepo.getOrder(companyId, orderId)
                val lines = orderRepo.getOrderLines(companyId, orderId)
                val payments = runCatching { orderRepo.getOrderPayments(companyId, orderId) }.getOrNull().orEmpty()
                    .sortedBy { it.receivedAt }
                _uiState.value = _uiState.value.copy(selectedOrderPayments = payments)
                val lot = order?.let {
                    if (it.producingBranchId.isNotEmpty() && it.deliveryDate > 0L) {
                        productionRepo.getActiveLotForBranchDateShift(
                            companyId,
                            it.producingBranchId,
                            it.deliveryDate,
                            it.shiftId
                        )?.takeIf { lot -> orderId in lot.customerOrderIds }
                    } else null
                }
                Triple(order, lines, lot)
            }.onSuccess { (order, lines, lot) ->
                _uiState.value = _uiState.value.copy(
                    selectedOrder = order,
                    selectedOrderLines = lines,
                    lotForSelectedOrder = lot,
                    isDetailLoading = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isDetailLoading = false, error = it.message)
            }
        }
    }

    /** Carga clientes, sucursales, productos y listas de precios para los formularios. */
    fun loadFormDependencies(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val customers = customerRepo.getCustomers(companyId)
                val branches = branchRepo.getBranches(companyId)
                val producing = branches.filter {
                    BranchCapability.CAN_PRODUCE in it.capabilities && it.active
                }
                val products = productRepo.getProducts(companyId).filter { it.active }
                val priceLists = priceListRepo.getLists(companyId)
                Quint(customers, branches, producing, products, priceLists)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    customers = it.customers,
                    branches = it.branches,
                    producingBranches = it.producing,
                    products = it.products,
                    priceLists = it.priceLists,
                    isLoading = false
                )
            }.onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    private data class Quint(
        val customers: List<ExternalCustomer>,
        val branches: List<Branch>,
        val producing: List<Branch>,
        val products: List<Product>,
        val priceLists: List<PriceList>,
    )

    fun loadShiftsForBranch(companyId: String, branchId: String) {
        if (branchId.isEmpty()) {
            _uiState.value = _uiState.value.copy(shifts = emptyList())
            return
        }
        viewModelScope.launch {
            runCatching { productionRepo.getShiftsForBranch(companyId, branchId) }
                .onSuccess { _uiState.value = _uiState.value.copy(shifts = it) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    /**
     * Resuelve la lista de precios a aplicar:
     *  - RESELLER: lista del [ExternalCustomer] (si vacía, default WHOLESALE).
     *  - RETAIL_SPECIAL / CAKE: lista default RETAIL.
     */
    fun resolveDefaultPriceList(
        type: String,
        customer: ExternalCustomer?
    ): PriceList? {
        val lists = _uiState.value.priceLists
        return when (type) {
            CustomerOrderType.RESELLER -> {
                customer?.priceListId
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { id -> lists.firstOrNull { it.id == id } }
                    ?: lists.firstOrNull { it.type == PriceListType.WHOLESALE && it.isDefault }
            }
            else -> lists.firstOrNull { it.type == PriceListType.RETAIL && it.isDefault }
        }
    }

    fun saveOrder(
        companyId: String,
        order: CustomerOrder,
        lines: List<CustomerOrderLine>
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                if (order.id.isEmpty()) orderRepo.saveOrder(companyId, order, lines)
                else orderRepo.updateOrder(companyId, order, lines)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
                loadOrdersOnce(companyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    /**
     * Confirma el pedido y, si tiene planta+fecha+turno definidos, intenta agregarlo
     * al lote activo. Si no hay lote, queda CONFIRMED esperando que el supervisor
     * lo arme. Productos con `requiresProduction=false` no se agregan al lote.
     */
    fun confirmOrder(companyId: String, orderId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val order = orderRepo.getOrder(companyId, orderId)
                    ?: error("Pedido no encontrado")
                val lines = orderRepo.getOrderLines(companyId, orderId)

                orderRepo.updateOrderStatus(companyId, orderId, CustomerOrderStatus.CONFIRMED)

                if (order.producingBranchId.isNotEmpty() && order.deliveryDate > 0L) {
                    val lot = productionRepo.getActiveLotForBranchDateShift(
                        companyId, order.producingBranchId, order.deliveryDate, order.shiftId
                    )
                    if (lot != null) {
                        val toAdd = lines
                            .filter { it.requiresProduction && it.productId.isNotEmpty() }
                            .map { l ->
                                ProductionLotLine(
                                    productId = l.productId,
                                    productName = l.productName,
                                    productUnit = l.productUnit,
                                    plannedQuantity = l.requestedQuantity,
                                    customerSpecs = listOf(
                                        CustomerLineSpec(
                                            customerOrderId = orderId,
                                            customerName = order.customerName,
                                            quantity = l.requestedQuantity,
                                            customSpec = l.customSpec,
                                            customerType = order.type
                                        )
                                    )
                                )
                            }
                        if (toAdd.isNotEmpty()) {
                            productionRepo.addCustomerOrderToLot(companyId, lot.id, orderId, toAdd)
                            orderRepo.updateOrderStatus(
                                companyId,
                                orderId,
                                CustomerOrderStatus.IN_PRODUCTION
                            )
                        }
                    }
                }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isConfirmed = true)
                loadOrdersOnce(companyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    /**
     * Cancela el pedido. Si está vinculado a un lote OPEN, primero lo quita del lote
     * (devuelve cantidades a planeado). Bloqueado si el lote está IN_PROGRESS/COMPLETED.
     */
    fun cancelOrder(companyId: String, orderId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val order = orderRepo.getOrder(companyId, orderId)
                    ?: error("Pedido no encontrado")

                if (order.producingBranchId.isNotEmpty() && order.deliveryDate > 0L) {
                    val lot = productionRepo.getActiveLotForBranchDateShift(
                        companyId, order.producingBranchId, order.deliveryDate, order.shiftId
                    )
                    if (lot != null && orderId in lot.customerOrderIds) {
                        productionRepo.removeCustomerOrderFromLot(companyId, lot.id, orderId)
                    }
                }
                orderRepo.cancelOrder(companyId, orderId)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isCancelled = true)
                loadOrdersOnce(companyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun clearSaved() { _uiState.value = _uiState.value.copy(isSaved = false) }
    fun clearCancelled() { _uiState.value = _uiState.value.copy(isCancelled = false) }
    fun clearConfirmed() { _uiState.value = _uiState.value.copy(isConfirmed = false) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
