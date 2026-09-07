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
import com.bakery.erp.model.DailyCashClose
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

data class DailyCashCloseUiState(
    val branches: List<Branch> = emptyList(),
    val selectedBranchId: String = "",
    val closeDate: String = "",
    val draft: DailyCashCloseDraft? = null,
    val existing: DailyCashClose? = null,
    val leftDenoms: Map<String, Int> = emptyMap(),
    val depositDenoms: Map<String, Int> = emptyMap(),
    val breadCount: BreadCount? = null,
    val spotChecks: List<SpotCashCheck> = emptyList(),
    val monthlyCloses: List<DailyCashClose> = emptyList(),
    val yearMonth: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isClosed: Boolean = false
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
        val ym = "${today.year.toString().padStart(4, '0')}-${today.monthNumber.toString().padStart(2, '0')}"
        _uiState.update { it.copy(closeDate = key, yearMonth = ym) }
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
        _uiState.update { it.copy(selectedBranchId = branchId, draft = null, existing = null) }
    }

    fun setCloseDate(date: String) {
        _uiState.update { it.copy(closeDate = date, draft = null, existing = null) }
    }

    fun setYearMonth(ym: String) { _uiState.update { it.copy(yearMonth = ym) } }

    /**
     * Carga el draft (sesiones del día) + verifica si ya hay cierre + carga
     * conteo de pan y spot checks del día.
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
                val existing = closeRepo.getCloseForDate(companyId, branchId, date)
                val left = existing?.let { closeRepo.getLeftDenominations(companyId, it.id) } ?: emptyMap()
                val deposit = existing?.let { closeRepo.getDepositDenominations(companyId, it.id) } ?: emptyMap()
                val breadCount = breadCountRepo.getCountForDate(companyId, branchId, date)
                val (from, until) = dayBounds(date)
                val spots = spotCheckRepo.getForBranchInRange(companyId, branchId, from, until)
                BundleResult(draft, existing, left, deposit, breadCount, spots)
            }.onSuccess { r ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        draft = r.draft,
                        existing = r.existing,
                        leftDenoms = r.left,
                        depositDenoms = r.deposit,
                        breadCount = r.breadCount,
                        spotChecks = r.spots
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun updateLeftDenoms(denoms: Map<String, Int>) {
        _uiState.update { it.copy(leftDenoms = denoms) }
    }

    fun updateDepositDenoms(denoms: Map<String, Int>) {
        _uiState.update { it.copy(depositDenoms = denoms) }
    }

    fun closeDay(companyId: String, notes: String?, currentUser: User) {
        val draft = _uiState.value.draft ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isClosed = false) }
            runCatching {
                closeRepo.closeDay(
                    companyId,
                    draft,
                    _uiState.value.leftDenoms,
                    _uiState.value.depositDenoms,
                    notes,
                    currentUser.id,
                    currentUser.displayName
                )
            }.onSuccess { closed ->
                _uiState.update {
                    it.copy(isLoading = false, existing = closed, isClosed = true)
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun loadMonth(companyId: String) {
        val branchId = _uiState.value.selectedBranchId
        if (branchId.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                closeRepo.getClosesForMonth(companyId, branchId, _uiState.value.yearMonth)
            }.onSuccess { list ->
                _uiState.update { it.copy(monthlyCloses = list) }
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message) }
            }
        }
    }

    fun clearClosed() { _uiState.update { it.copy(isClosed = false) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }

    private data class BundleResult(
        val draft: DailyCashCloseDraft,
        val existing: DailyCashClose?,
        val left: Map<String, Int>,
        val deposit: Map<String, Int>,
        val breadCount: BreadCount?,
        val spots: List<SpotCashCheck>
    )

    private fun dayBounds(yyyyMmDd: String): Pair<Long, Long> {
        val tz = TimeZone.currentSystemDefault()
        val parts = yyyyMmDd.split("-")
        val date = LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        val start = date.atStartOfDayIn(tz).toEpochMilliseconds()
        val end = start + 24L * 60L * 60L * 1000L - 1L
        return start to end
    }
}
