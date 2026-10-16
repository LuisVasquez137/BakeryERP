package com.bakery.erp.presentation.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.NotificationRepository
import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.domain.StockTransferRepository
import com.bakery.erp.model.AppNotification
import com.bakery.erp.model.Branch
import com.bakery.erp.model.FinishedProductStock
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.NotificationTarget
import com.bakery.erp.model.NotificationType
import com.bakery.erp.model.Permission
import com.bakery.erp.model.Product
import com.bakery.erp.model.StockTransfer
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.isTransferable
import com.bakery.erp.model.quantityLabel
import com.bakery.erp.model.stockTransferSendError
import com.bakery.erp.util.fmtTrim
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class StockTransferUiState(
    /** Sucursales activas: cualquiera puede ser destino. */
    val branches: List<Branch> = emptyList(),
    /** Productos activos (pan, pasteles, porciones, reventa). */
    val products: List<Product> = emptyList(),
    /** Insumos que llevan existencias ([isTransferable]). */
    val ingredients: List<Ingredient> = emptyList(),
    /** Stock del origen elegido, para mostrar cuánto hay y avisar si se manda de más. */
    val originProductStock: Map<String, FinishedProductStock> = emptyMap(),
    val originIngredientStock: Map<String, Double> = emptyMap(),
    val isFormLoading: Boolean = false,
    val isBusy: Boolean = false,
    val list: List<StockTransfer> = emptyList(),
    val isListLoading: Boolean = false,
    val selected: StockTransfer? = null,
    val isDetailLoading: Boolean = false,
    /** Id del traslado recién enviado: el formulario navega a su detalle. */
    val sentId: String? = null,
    val error: String? = null,
    val notice: String? = null
)

/** Traslados de inventario entre sucursales. Ver `model/StockTransfer.kt`. */
class StockTransferViewModel(
    private val repository: StockTransferRepository,
    private val branchRepository: BranchRepository,
    private val productRepository: ProductRepository,
    private val ingredientRepository: IngredientRepository,
    private val finishedStockRepository: FinishedStockRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockTransferUiState())
    val uiState: StateFlow<StockTransferUiState> = _uiState.asStateFlow()

    fun loadBranches(companyId: String) {
        if (_uiState.value.branches.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { branchRepository.getBranches(companyId) }
                .onSuccess { all -> _uiState.update { it.copy(branches = all.filter { b -> b.active }.sortedBy { b -> b.name }) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    /** Sucursales desde las que [user] puede enviar. */
    fun originsFor(user: User): List<Branch> =
        _uiState.value.branches.filter { user.canAccessBranch(it.id) }

    /** Catálogo (una vez) y stock del origen (cada vez que cambia). */
    fun prepareForm(companyId: String, originBranchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFormLoading = true, error = null) }
            runCatching {
                if (_uiState.value.branches.isEmpty()) {
                    val all = branchRepository.getBranches(companyId).filter { it.active }.sortedBy { it.name }
                    _uiState.update { it.copy(branches = all) }
                }
                if (_uiState.value.products.isEmpty() || _uiState.value.ingredients.isEmpty()) {
                    val products = productRepository.getProducts(companyId).filter { it.active }.sortedBy { it.name.lowercase() }
                    val ingredients = ingredientRepository.getIngredients(companyId).filter { it.isTransferable() }
                        .sortedBy { it.name.lowercase() }
                    _uiState.update { it.copy(products = products, ingredients = ingredients) }
                }
                if (originBranchId.isEmpty()) emptyMap<String, FinishedProductStock>() to emptyMap()
                else finishedStockRepository.getStocksForBranch(companyId, originBranchId).associateBy { it.productId } to
                    ingredientRepository.getBranchStocksForBranch(companyId, originBranchId)
            }.onSuccess { (products, ingredients) ->
                _uiState.update {
                    it.copy(isFormLoading = false, originProductStock = products, originIngredientStock = ingredients)
                }
            }.onFailure { e -> _uiState.update { it.copy(isFormLoading = false, error = e.message) } }
        }
    }

    fun send(companyId: String, draft: StockTransfer, user: User) {
        if (_uiState.value.isBusy) return
        stockTransferSendError(draft)?.let { msg -> _uiState.update { it.copy(error = msg) }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                val saved = repository.send(companyId, draft.copy(
                    sentByUserId = user.id,
                    sentByName = user.displayName,
                    sentByEmployeeId = user.operatorEmployeeId
                ))
                notify(companyId, user, saved,
                    title = "Traslado en camino de ${saved.originBranchName}",
                    body = summary(saved) + saved.note.takeIf { it.isNotBlank() }?.let { ". $it" }.orEmpty(),
                    branchId = saved.destinationBranchId,
                    permission = Permission.RECEIVE_DISPATCH)
                saved
            }.onSuccess { saved ->
                _uiState.update {
                    it.copy(
                        isBusy = false, sentId = saved.id, selected = saved,
                        list = listOf(saved) + it.list.filter { t -> t.id != saved.id },
                        notice = "Traslado ${saved.folio} enviado a ${saved.destinationBranchName}"
                    )
                }
            }.onFailure { e -> _uiState.update { it.copy(isBusy = false, error = e.message ?: "No se pudo enviar") } }
        }
    }

    private var listJob: Job? = null

    /**
     * Escucha la lista mientras la pantalla está abierta: la sucursal que recibe ve aparecer
     * el envío, y el origen ve cuándo lo recibieron, sin salir y volver a entrar. Cambiar de
     * sucursal o de período reemplaza la escucha anterior; [stopObservingList] la suelta.
     */
    fun loadList(companyId: String, user: User, branchId: String, fromMillis: Long) {
        listJob?.cancel()
        val ids = if (branchId.isNotEmpty()) listOf(branchId)
                  else if (user.branchIds.isEmpty()) emptyList()
                  else user.branchIds
        _uiState.update { it.copy(isListLoading = true, error = null) }
        listJob = viewModelScope.launch {
            repository.observeForBranches(companyId, ids, fromMillis)
                .catch { e -> _uiState.update { it.copy(isListLoading = false, error = e.message) } }
                .collect { all ->
                    val list = all.filter {
                        user.canAccessBranch(it.originBranchId) || user.canAccessBranch(it.destinationBranchId)
                    }
                    _uiState.update { it.copy(isListLoading = false, list = list) }
                }
        }
    }

    fun stopObservingList() {
        listJob?.cancel()
        listJob = null
    }

    fun loadDetail(companyId: String, transferId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = it.selected?.id != transferId, error = null) }
            runCatching { repository.get(companyId, transferId) }
                .onSuccess { t ->
                    _uiState.update {
                        it.copy(isDetailLoading = false, selected = t, error = if (t == null) "No se encontró el traslado" else null)
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isDetailLoading = false, error = e.message) } }
        }
    }

    fun receive(companyId: String, transfer: StockTransfer, received: List<Double>, note: String, user: User) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                val updated = repository.receive(
                    companyId, transfer.id, received, user.id, user.displayName, user.operatorEmployeeId, note
                )
                // Al origen (y al dueño) solo si faltó algo: si llegó completo no hay nada que decidir.
                if (updated.shortages.isNotEmpty()) {
                    notify(companyId, user, updated,
                        title = "Traslado ${updated.folio} llegó incompleto",
                        body = "${updated.destinationBranchName} recibió menos: " +
                            updated.shortages.joinToString { "faltó ${it.shortage.fmtTrim()} ${it.unit} de ${it.itemName}" } +
                            updated.receiveNote.takeIf { it.isNotBlank() }?.let { ". $it" }.orEmpty(),
                        branchId = updated.originBranchId,
                        permission = Permission.SEND_STOCK_TRANSFER)
                }
                updated
            }.onSuccess { updated -> replace(updated, "Traslado ${updated.folio} recibido") }
                .onFailure { e -> _uiState.update { it.copy(isBusy = false, error = e.message ?: "No se pudo recibir") } }
        }
    }

    fun cancel(companyId: String, transfer: StockTransfer, reason: String, user: User) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                val updated = repository.cancel(companyId, transfer.id, user.id, user.displayName, reason)
                // Al destino, para que no se quede esperando algo que no va a llegar.
                notify(companyId, user, updated,
                    title = "Traslado ${updated.folio} cancelado",
                    body = "${updated.originBranchName} canceló el envío: " + summary(updated) +
                        reason.trim().takeIf { it.isNotEmpty() }?.let { ". $it" }.orEmpty(),
                    branchId = updated.destinationBranchId,
                    permission = Permission.RECEIVE_DISPATCH)
                updated
            }.onSuccess { updated -> replace(updated, "Traslado ${updated.folio} cancelado: el inventario volvió a ${updated.originBranchName}") }
                .onFailure { e -> _uiState.update { it.copy(isBusy = false, error = e.message ?: "No se pudo cancelar") } }
        }
    }

    private fun replace(updated: StockTransfer, notice: String) {
        _uiState.update {
            it.copy(
                isBusy = false, selected = updated, notice = notice,
                list = it.list.map { t -> if (t.id == updated.id) updated else t }
            )
        }
    }

    private fun summary(t: StockTransfer): String =
        t.lines.take(4).joinToString { "${it.quantityLabel()} ${it.itemName}" } +
            if (t.lines.size > 4) " y ${t.lines.size - 4} más" else ""

    private suspend fun notify(
        companyId: String, by: User, t: StockTransfer,
        title: String, body: String, branchId: String, permission: Permission
    ) {
        runCatching {
            notificationRepository.push(companyId, AppNotification(
                type = NotificationType.STOCK_TRANSFER,
                title = title,
                body = body,
                targetBranchIds = listOf(branchId),
                requiredPermission = permission.name,
                targetScreen = NotificationTarget.STOCK_TRANSFER_DETAIL,
                targetId = t.id,
                createdByUserId = by.id,
                createdByUserName = by.displayName
            ))
        }
    }

    fun consumeSentId() { _uiState.update { it.copy(sentId = null) } }
    fun clearNotice() { _uiState.update { it.copy(notice = null) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
