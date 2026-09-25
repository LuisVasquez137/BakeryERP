package com.bakery.erp.presentation.suppliers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.PurchaseRepository
import com.bakery.erp.domain.SupplierRepository
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.Supplier
import com.bakery.erp.model.SupplierItemHistory
import com.bakery.erp.model.SupplierPurchaseTotals
import com.bakery.erp.model.buildSupplierItemHistory
import com.bakery.erp.model.sortedForList
import com.bakery.erp.model.supplierTotals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class SupplierUiState(
    val supplierList: List<Supplier> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedSupplier: Supplier? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    // ── Historial del proveedor ──────────────────────────────────────────────
    /** Compras de este proveedor, lo más reciente primero. Sale de los ENCABEZADOS. */
    val supplierPurchases: List<Purchase> = emptyList(),
    val supplierTotals: SupplierPurchaseTotals = SupplierPurchaseTotals(),
    val isLoadingHistory: Boolean = false,
    /** Proveedor cuyo historial está cargado, para no releerlo al recomponer. */
    val historyFor: String = "",
    /**
     * Qué se le compra, ya sumado. Se carga APARTE y a pedido: las cantidades viven en la
     * subcolección de líneas, y leerlas es una consulta por factura. La lista de compras, en
     * cambio, sale gratis del encabezado, que ya denormaliza total y nombres.
     */
    val itemHistory: List<SupplierItemHistory> = emptyList(),
    val isLoadingItems: Boolean = false,
    val itemsFor: String = ""
)

/**
 * Cuántas facturas se abren para sumar cantidades. Es un tope de lecturas, no de historia: con
 * más de esto la respuesta a "qué le compro" ya está clara y seguir leyendo cuesta una consulta
 * por factura. La pantalla dice cuántas se miraron.
 */
private const val MAX_HISTORY_PURCHASES = 40

class SupplierViewModel(
    private val repository: SupplierRepository,
    // Solo de lectura: la ficha del proveedor contesta "qué le compro y cuándo", que hasta
    // ahora había que ir a buscar a Compras filtrando a mano.
    private val purchaseRepository: PurchaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupplierUiState())
    val uiState: StateFlow<SupplierUiState> = _uiState.asStateFlow()

    fun loadSuppliers(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.getSuppliers(companyId) }
                // Alfabético acá y no en la pantalla: Firestore devuelve en orden arbitrario, y
                // el orden es del catálogo, no de quien lo dibuja.
                .onSuccess { _uiState.value = _uiState.value.copy(supplierList = it.sortedForList(), isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun saveSupplier(companyId: String, supplier: Supplier) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val isNew = supplier.id.isEmpty()
                val final = if (isNew) supplier.copy(id = generateId()) else supplier
                if (isNew) repository.addSupplier(companyId, final)
                else repository.updateSupplier(companyId, final)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
                loadSuppliers(companyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun deleteSupplier(companyId: String, supplierId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.deleteSupplier(companyId, supplierId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isDeleted = true)
                    loadSuppliers(companyId)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    /**
     * Compras de un proveedor. Se filtra EN MEMORIA sobre la colección completa, igual que la
     * lista de Compras: `date` puede venir como `Timestamp` en las compras viejas y como
     * número en las nuevas, y Firestore ordena por tipo antes que por valor — un `where` en el
     * servidor dejaría fuera justo las más antiguas, que son las que uno va a buscar.
     */
    fun loadSupplierHistory(companyId: String, supplierId: String) {
        if (companyId.isEmpty() || supplierId.isEmpty()) return
        if (_uiState.value.historyFor == supplierId && !_uiState.value.isLoadingHistory) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingHistory = true, historyFor = supplierId)
            val purchases = runCatching { purchaseRepository.getPurchases(companyId) }
                .getOrElse { emptyList() }
                .filter { it.supplierId == supplierId }
                .sortedByDescending { it.date }
            _uiState.value = _uiState.value.copy(
                supplierPurchases = purchases,
                supplierTotals = purchases.supplierTotals(),
                isLoadingHistory = false
            )
        }
    }

    /** Abre las líneas de las últimas facturas y suma qué se le compra. Ver [MAX_HISTORY_PURCHASES]. */
    fun loadSupplierItems(companyId: String, supplierId: String) {
        if (companyId.isEmpty() || supplierId.isEmpty()) return
        if (_uiState.value.itemsFor == supplierId && !_uiState.value.isLoadingItems) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingItems = true, itemsFor = supplierId)
            val purchases = _uiState.value.supplierPurchases.take(MAX_HISTORY_PURCHASES)
            val linesByPurchase = mutableMapOf<String, List<PurchaseLine>>()
            purchases.forEach { p ->
                linesByPurchase[p.id] =
                    runCatching { purchaseRepository.getLines(companyId, p.id) }.getOrElse { emptyList() }
            }
            _uiState.value = _uiState.value.copy(
                itemHistory = buildSupplierItemHistory(purchases, linesByPurchase),
                isLoadingItems = false
            )
        }
    }

    fun selectSupplier(supplier: Supplier?) {
        _uiState.value = _uiState.value.copy(selectedSupplier = supplier)
    }

    fun clearSaved() { _uiState.value = _uiState.value.copy(isSaved = false) }
    fun clearDeleted() { _uiState.value = _uiState.value.copy(isDeleted = false) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    private fun generateId(): String {
        val hex = "0123456789abcdef"
        return Random.nextBytes(16).joinToString("") { b ->
            hex[(b.toInt() shr 4) and 0xF].toString() + hex[b.toInt() and 0xF]
        }
    }
}
