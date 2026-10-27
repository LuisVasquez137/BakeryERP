package com.bakery.erp.presentation.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.BreadCountRepository
import com.bakery.erp.domain.DailyCashCloseDraft
import com.bakery.erp.domain.DailyCashCloseRepository
import com.bakery.erp.domain.SpotCashCheckRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.BreadCount
import com.bakery.erp.model.SpotCashCheck
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

data class DailyCashCloseUiState(
    val branches: List<Branch> = emptyList(),
    val selectedBranchId: String = "",
    val closeDate: String = "",
    val draft: DailyCashCloseDraft? = null,
    val breadCount: BreadCount? = null,
    val spotChecks: List<SpotCashCheck> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class DailyCashCloseViewModel(
    private val closeRepo: DailyCashCloseRepository,
    private val branchRepository: BranchRepository,
    private val breadCountRepo: BreadCountRepository,
    private val spotCheckRepo: SpotCashCheckRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyCashCloseUiState())
    val uiState: StateFlow<DailyCashCloseUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val key = "${today.year.toString().padStart(4, '0')}-" +
            "${today.monthNumber.toString().padStart(2, '0')}-" +
            "${today.dayOfMonth.toString().padStart(2, '0')}"
        _uiState.update { it.copy(closeDate = key) }
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
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun selectBranch(branchId: String) {
        if (branchId == _uiState.value.selectedBranchId) return
        _uiState.update { it.copy(selectedBranchId = branchId, draft = null) }
    }

    fun setCloseDate(date: String) {
        _uiState.update { it.copy(closeDate = date, draft = null) }
    }

    /** Día anterior (-1) o siguiente (+1); no pasa de hoy. */
    fun shiftDate(days: Int) {
        val current = parseDate(_uiState.value.closeDate) ?: return
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val next = current.plus(DatePeriod(days = days)).takeIf { it <= today } ?: return
        setCloseDate(next.toString())
    }

    /**
     * Resumen de cajas del día: los turnos de la sucursal (cerrados y abiertos), el conteo de
     * pan y los cuadres sorpresa. Solo lectura: el dinero se cuenta y se saca en "Cerrar caja".
     */
    fun loadDayContext(companyId: String) {
        val branchId = _uiState.value.selectedBranchId
        val date = _uiState.value.closeDate
        if (branchId.isEmpty() || date.isEmpty()) return
        val branchName = _uiState.value.branches.firstOrNull { it.id == branchId }?.name.orEmpty()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val draft = closeRepo.buildDraft(companyId, branchId, branchName, date)
                val breadCount = breadCountRepo.getCountForDate(companyId, branchId, date)
                val (from, until) = dayBounds(date)
                val spots = spotCheckRepo.getForBranchInRange(companyId, branchId, from, until)
                BundleResult(draft, breadCount, spots)
            }.onSuccess { r ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        draft = r.draft,
                        breadCount = r.breadCount,
                        spotChecks = r.spots
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    private data class BundleResult(
        val draft: DailyCashCloseDraft,
        val breadCount: BreadCount?,
        val spots: List<SpotCashCheck>
    )

    private fun parseDate(yyyyMmDd: String): LocalDate? = runCatching { LocalDate.parse(yyyyMmDd) }.getOrNull()

    private fun dayBounds(yyyyMmDd: String): Pair<Long, Long> {
        val tz = TimeZone.currentSystemDefault()
        val parts = yyyyMmDd.split("-")
        val date = LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        val start = date.atStartOfDayIn(tz).toEpochMilliseconds()
        val end = start + 24L * 60L * 60L * 1000L - 1L
        return start to end
    }
}
