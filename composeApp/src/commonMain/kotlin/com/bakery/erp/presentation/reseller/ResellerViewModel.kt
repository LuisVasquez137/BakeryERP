package com.bakery.erp.presentation.reseller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.CustomerOrderRepository
import com.bakery.erp.domain.ExternalCustomerRepository
import com.bakery.erp.domain.PriceListRepository
import com.bakery.erp.domain.resolveListPrices
import com.bakery.erp.domain.PriceSource
import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.domain.ResolvedPrice
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.CustomerOrder
import com.bakery.erp.model.CustomerOrderDeliveryType
import com.bakery.erp.model.CustomerOrderLine
import com.bakery.erp.model.CustomerOrderStatus
import com.bakery.erp.model.CustomerOrderType
import com.bakery.erp.model.ExternalCustomer
import com.bakery.erp.model.Product
import com.bakery.erp.model.ResellerDeliveryMode
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.bakery.erp.model.isPortion

data class ResellerUiState(
    /** Perfil del revendedor cargado del User actual. */
    val customer: ExternalCustomer? = null,
    val products: List<Product> = emptyList(),
    /** Sucursales con CAN_PRODUCE activas — fallback para productos sin asignación específica. */
    val producingBranches: List<Branch> = emptyList(),
    /** Precio resuelto por producto contra la lista del revendedor. */
    val prices: Map<String, ResolvedPrice> = emptyMap(),
    val recentOrders: List<CustomerOrder> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOrderSent: Boolean = false,
    val lastOrderId: String = "",
    /** Mensaje específico cuando ya hay un pedido para la misma fecha+hora. */
    val duplicateOrderWarning: String = "",
    /** Pedido individual cargado para mostrar en el detalle. */
    val selectedOrder: CustomerOrder? = null,
    val selectedOrderLines: List<CustomerOrderLine> = emptyList(),
    /** Pedidos del slot (fecha+hora) cargados para editar. Puede ser más de uno
     *  si los productos del slot se rutearon a varias plantas. */
    val editSliceOrders: List<CustomerOrder> = emptyList(),
    /** Mapa productId → cantidad combinada del slot que estamos editando. */
    val editSliceQuantities: Map<String, Double> = emptyMap()
)

class ResellerViewModel(
    private val customerRepo: ExternalCustomerRepository,
    private val orderRepo: CustomerOrderRepository,
    private val productRepo: ProductRepository,
    private val priceListRepo: PriceListRepository,
    private val branchRepo: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResellerUiState())
    val uiState: StateFlow<ResellerUiState> = _uiState.asStateFlow()

    /** Carga perfil + catálogo + precios + pedidos recientes. */
    fun loadForUser(companyId: String, userId: String) {
        if (companyId.isEmpty() || userId.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val customer = customerRepo.getCustomerByLinkedUser(companyId, userId)
                    ?: error("Tu perfil de revendedor no se encontró. Pídele a la encargada que te apruebe.")
                // La porción de pastel solo existe cortada en la tienda: no se pide a planta.
                val products = productRepo.getProducts(companyId)
                    .filter { it.active && !it.isPortion }
                    .sortedBy { it.name.lowercase() }
                val prices = resolvePrices(companyId, customer.priceListId, products)
                val recentOrders = orderRepo.getOrdersForCustomer(companyId, customer.id)
                    .sortedByDescending { it.createdAt }
                    .take(10)
                val producingBranches = branchRepo.getBranches(companyId)
                    .filter { it.active && BranchCapability.CAN_PRODUCE in it.capabilities }
                LoadResult(customer, products, prices, recentOrders, producingBranches)
            }.onSuccess { r ->
                _uiState.value = _uiState.value.copy(
                    customer = r.customer, products = r.products, prices = r.prices,
                    recentOrders = r.recentOrders, producingBranches = r.producingBranches,
                    isLoading = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error al cargar"
                )
            }
        }
    }

    private data class LoadResult(
        val customer: ExternalCustomer,
        val products: List<Product>,
        val prices: Map<String, ResolvedPrice>,
        val recentOrders: List<CustomerOrder>,
        val producingBranches: List<Branch>
    )

    private suspend fun resolvePrices(
        companyId: String,
        priceListId: String,
        products: List<Product>
    ): Map<String, ResolvedPrice> {
        if (priceListId.isEmpty()) return emptyMap()
        val list = priceListRepo.getList(companyId, priceListId) ?: return emptyMap()
        // La misma cascada que usa la caja al venderle: lo que ve en su pedido es lo que paga.
        return resolveListPrices(list, priceListRepo.getPricesForList(companyId, priceListId), products)
    }

    /**
     * Crea UN solo CustomerOrder destinado a la sucursal de retiro del revendedor
     * (no a una planta). La encargada de la sucursal vendedora será quien lo consolide
     * a su pedido a la(s) planta(s) productora(s) cuando ella arme su ProductionOrder.
     *
     * Detecta duplicados: si ya hay un pedido PENDING/CONFIRMED del mismo revendedor
     * para la misma fecha+hora, retorna error.
     *
     * Si [overrideExistingOrderIds] no está vacío, el pedido se trata como edición:
     * se cancelan los pedidos antiguos y se crea el nuevo.
     */
    fun submitOrder(
        companyId: String,
        quantities: Map<String, Double>,
        deliveryDate: Long,
        deliveryTime: String,
        notes: String,
        userId: String,
        userName: String,
        overrideExistingOrderIds: List<String> = emptyList()
    ) {
        val customer = _uiState.value.customer ?: run {
            _uiState.value = _uiState.value.copy(error = "Perfil no cargado")
            return
        }
        val products = _uiState.value.products
        val prices = _uiState.value.prices

        val lines = quantities.mapNotNull { (productId, qty) ->
            if (qty <= 0.0) return@mapNotNull null
            val product = products.firstOrNull { it.id == productId } ?: return@mapNotNull null
            CustomerOrderLine(
                productId = product.id,
                productName = product.name,
                productUnit = product.unit,
                requestedQuantity = qty,
                unitPrice = prices[productId]?.unitPrice ?: 0.0,
                priceSource = prices[productId]?.source ?: PriceSource.NONE,
                customSpec = "",
                requiresProduction = product.isInHouseProduced
            )
        }

        if (lines.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Indica al menos un producto con cantidad")
            return
        }

        // Detección de duplicado.
        val existingForSlot = _uiState.value.recentOrders
            .filter { it.customerId == customer.id }
            .filter { it.deliveryDate == deliveryDate && it.deliveryTime == deliveryTime }
            .filter { it.status !in listOf(CustomerOrderStatus.CANCELLED, CustomerOrderStatus.DELIVERED) }
            .filter { it.id !in overrideExistingOrderIds }

        if (existingForSlot.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                duplicateOrderWarning = "Ya tienes un pedido para esta fecha y hora. " +
                    "Puedes editar tu pedido si deseas, o crear uno nuevo para otra hora."
            )
            return
        }

        val deliveryType = if (customer.deliveryMode == ResellerDeliveryMode.DELIVERY)
            CustomerOrderDeliveryType.DELIVERY else CustomerOrderDeliveryType.PICKUP

        val subtotal = lines.sumOf { it.unitPrice * it.requestedQuantity }
        val order = CustomerOrder(
            type = CustomerOrderType.RESELLER,
            status = CustomerOrderStatus.PENDING,
            customerId = customer.id,
            customerName = customer.name,
            customerPhone = customer.phone,
            customerPhoneAlt = customer.phoneAlt,
            recipientName = customer.name,
            deliveryType = deliveryType,
            // La sucursal de retiro del revendedor es la "tienda vendedora" que va a
            // consolidar este pedido. Esa es la planta DESTINO desde el punto de vista
            // de despacho. La planta de producción la decide ella al armar su PO.
            deliveryBranchId = customer.preferredBranchId,
            deliveryBranchName = customer.preferredBranchName,
            deliveryAddress = if (deliveryType == CustomerOrderDeliveryType.DELIVERY)
                customer.address else "",
            deliveryAddressReference = customer.deliveryAddressReference,
            deliveryDate = deliveryDate,
            deliveryTime = deliveryTime,
            // producingBranchId/shiftId vacíos por diseño — la encargada los decide.
            producingBranchId = "",
            producingBranchName = "",
            shiftId = "",
            shiftName = "",
            priceListId = customer.priceListId,
            priceListName = customer.priceListName,
            subtotalAmount = subtotal,
            totalAmount = subtotal,
            balanceAmount = subtotal,
            notes = notes,
            createdByUserId = userId,
            createdByUserName = userName,
            lineCount = lines.size
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                // Si es edición, cancelar los anteriores.
                overrideExistingOrderIds.forEach { id ->
                    runCatching { orderRepo.cancelOrder(companyId, id) }
                }
                orderRepo.saveOrder(companyId, order, lines)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isOrderSent = true
                )
                loadForUser(companyId, userId)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error al enviar el pedido"
                )
            }
        }
    }

    /**
     * Carga TODOS los pedidos del revendedor para un (fecha, hora) específicos.
     * Útil para editar: si el revendedor tenía panes en Planta A + pastel en Planta B
     * para el mismo slot, devuelve ambos.
     */
    fun loadOrdersForSlice(
        companyId: String,
        userId: String,
        deliveryDate: Long,
        deliveryTime: String
    ) {
        viewModelScope.launch {
            runCatching {
                val customer = _uiState.value.customer
                    ?: customerRepo.getCustomerByLinkedUser(companyId, userId)
                    ?: error("Perfil no cargado")
                val all = orderRepo.getOrdersForCustomer(companyId, customer.id)
                all.filter {
                    it.deliveryDate == deliveryDate &&
                        it.deliveryTime == deliveryTime &&
                        it.status !in listOf(CustomerOrderStatus.CANCELLED, CustomerOrderStatus.DELIVERED)
                }
            }.onSuccess { slice ->
                _uiState.value = _uiState.value.copy(editSliceOrders = slice)
                // Cargar líneas combinadas.
                val combined = mutableMapOf<String, Double>()
                slice.forEach { order ->
                    runCatching { orderRepo.getOrderLines(companyId, order.id) }
                        .getOrNull().orEmpty()
                        .forEach { line ->
                            val previous = combined[line.productId] ?: 0.0
                            combined[line.productId] = previous + line.requestedQuantity
                        }
                }
                _uiState.value = _uiState.value.copy(editSliceQuantities = combined)
            }.onFailure {
                _uiState.value = _uiState.value.copy(error = it.message)
            }
        }
    }

    /** Carga UN pedido individual + sus líneas para mostrar en el detalle. */
    fun loadOrderDetail(companyId: String, orderId: String) {
        if (orderId.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching {
                val o = orderRepo.getOrder(companyId, orderId) ?: error("Pedido no encontrado")
                val l = orderRepo.getOrderLines(companyId, orderId)
                o to l
            }.onSuccess { (o, l) ->
                _uiState.value = _uiState.value.copy(
                    selectedOrder = o,
                    selectedOrderLines = l,
                    isLoading = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun clearEditSlice() {
        _uiState.value = _uiState.value.copy(
            editSliceOrders = emptyList(),
            editSliceQuantities = emptyMap()
        )
    }

    fun clearOrderSent() { _uiState.value = _uiState.value.copy(isOrderSent = false) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun clearDuplicateWarning() {
        _uiState.value = _uiState.value.copy(duplicateOrderWarning = "")
    }
}
