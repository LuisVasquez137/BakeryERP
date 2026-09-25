package com.bakery.erp.presentation.finishedStock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.BreadCountRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.BreadCount
import com.bakery.erp.model.BreadCountLine
import com.bakery.erp.model.GroupSalesWindow
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class BreadCountUiState(
    val branches: List<Branch> = emptyList(),
    val selectedBranchId: String = "",
    val monthlyCounts: List<BreadCount> = emptyList(),
    val currentCount: BreadCount? = null,
    val currentLines: List<BreadCountLine> = emptyList(),
    /**
     * Ventas por grupo de precio ("Pan de Q0.50") que le tocan al conteo abierto. La pantalla
     * las reparte en vivo para mostrar cuánto del faltante es venta y cuánto merma.
     */
    val groupWindow: GroupSalesWindow? = null,
    val isGroupWindowLoading: Boolean = false,
    val yearMonth: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val createdOrResumedId: String? = null,
    val isClosed: Boolean = false
)

class BreadCountViewModel(
    private val repo: BreadCountRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreadCountUiState())
    val uiState: StateFlow<BreadCountUiState> = _uiState.asStateFlow()

    private var headerJob: Job? = null
    private var linesJob: Job? = null

    init {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(tz).date
        val ym = "${now.year.toString().padStart(4, '0')}-${now.monthNumber.toString().padStart(2, '0')}"
        _uiState.update { it.copy(yearMonth = ym) }
    }

    fun loadBranches(companyId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                branchRepository.getBranches(companyId)
                    .filter { it.active }
                    .filter { currentUser.canAccessBranch(it.id) }
                    .filter { BranchCapability.CAN_SELL in it.capabilities }
                    .sortedBy { it.name }
            }.onSuccess { branches ->
                val selected = _uiState.value.selectedBranchId.takeIf { id -> branches.any { it.id == id } }
                    ?: branches.firstOrNull()?.id.orEmpty()
                _uiState.update { it.copy(isLoading = false, branches = branches, selectedBranchId = selected) }
                if (selected.isNotEmpty()) loadMonth(companyId, selected, _uiState.value.yearMonth)
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun selectBranch(companyId: String, branchId: String) {
        if (branchId == _uiState.value.selectedBranchId) return
        _uiState.update { it.copy(selectedBranchId = branchId) }
        loadMonth(companyId, branchId, _uiState.value.yearMonth)
    }

    fun setYearMonth(companyId: String, ym: String) {
        _uiState.update { it.copy(yearMonth = ym) }
        val branchId = _uiState.value.selectedBranchId
        if (branchId.isNotEmpty()) loadMonth(companyId, branchId, ym)
    }

    private fun loadMonth(companyId: String, branchId: String, ym: String) {
        viewModelScope.launch {
            runCatching { repo.getCountsForMonth(companyId, branchId, ym) }
                .onSuccess { list -> _uiState.update { it.copy(monthlyCounts = list) } }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    /** Abre o retoma el conteo del día actual de la sucursal seleccionada. */
    fun openOrResumeToday(companyId: String, branch: Branch, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, createdOrResumedId = null) }
            val tz = TimeZone.currentSystemDefault()
            val date = Clock.System.now().toLocalDateTime(tz).date
            val key = "${date.year.toString().padStart(4, '0')}-" +
                "${date.monthNumber.toString().padStart(2, '0')}-" +
                "${date.dayOfMonth.toString().padStart(2, '0')}"
            runCatching {
                repo.openOrResume(
                    companyId = companyId,
                    branchId = branch.id,
                    branchName = branch.name,
                    countDate = key,
                    countedByUserId = currentUser.id,
                    countedByUserName = currentUser.displayName
                )
            }.onSuccess { header ->
                _uiState.update { it.copy(isLoading = false, createdOrResumedId = header.id) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun observeCount(companyId: String, countId: String) {
        headerJob?.cancel()
        linesJob?.cancel()
        _uiState.update { it.copy(groupWindow = null) }
        refreshGroupSales(companyId, countId)
        headerJob = viewModelScope.launch {
            repo.observeCount(companyId, countId)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { c -> _uiState.update { it.copy(currentCount = c) } }
        }
        linesJob = viewModelScope.launch {
            repo.observeLines(companyId, countId)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { lines -> _uiState.update { it.copy(currentLines = lines) } }
        }
    }

    /**
     * Relee las ventas de grupo. Se ofrece a mano porque mientras se cuenta la caja sigue
     * vendiendo; el cierre las vuelve a leer de todas formas, así que esto es solo la vista.
     */
    fun refreshGroupSales(companyId: String, countId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGroupWindowLoading = true) }
            runCatching { repo.groupSalesWindow(companyId, countId) }
                .onSuccess { w -> _uiState.update { it.copy(groupWindow = w, isGroupWindowLoading = false) } }
                .onFailure { err -> _uiState.update { it.copy(isGroupWindowLoading = false, error = err.message) } }
        }
    }

    fun updateLine(companyId: String, countId: String, line: BreadCountLine) {
        viewModelScope.launch {
            runCatching { repo.updateLine(companyId, countId, line) }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun closeCount(companyId: String, countId: String, notes: String?, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isClosed = false) }
            runCatching {
                repo.closeCount(companyId, countId, notes, currentUser.id, currentUser.displayName)
            }.onSuccess { closed ->
                _uiState.update { it.copy(isLoading = false, currentCount = closed, isClosed = true) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun clearCreatedId() { _uiState.update { it.copy(createdOrResumedId = null) } }
    fun clearClosed() { _uiState.update { it.copy(isClosed = false) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
