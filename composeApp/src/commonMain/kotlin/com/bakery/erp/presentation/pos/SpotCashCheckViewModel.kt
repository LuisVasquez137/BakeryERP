package com.bakery.erp.presentation.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.CashSessionRepository
import com.bakery.erp.domain.SpotCashCheckRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.SpotCashCheck
import com.bakery.erp.model.SpotCashCheckResolution
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

data class SpotCashCheckUiState(
    val branches: List<Branch> = emptyList(),
    val selectedBranchId: String = "",
    val openSessions: List<CashSession> = emptyList(),
    val activeSession: CashSession? = null,
    val recentChecksForBranch: List<SpotCashCheck> = emptyList(),
    val currentCheck: SpotCashCheck? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val savedCheckId: String? = null
)

class SpotCashCheckViewModel(
    private val spotRepo: SpotCashCheckRepository,
    private val sessionRepo: CashSessionRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpotCashCheckUiState())
    val uiState: StateFlow<SpotCashCheckUiState> = _uiState.asStateFlow()

    private var openSessionsJob: Job? = null

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
                if (selected.isNotEmpty()) observeOpenSessions(companyId, selected)
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun selectBranch(companyId: String, branchId: String) {
        if (branchId == _uiState.value.selectedBranchId) return
        _uiState.update { it.copy(selectedBranchId = branchId, openSessions = emptyList()) }
        observeOpenSessions(companyId, branchId)
    }

    private fun observeOpenSessions(companyId: String, branchId: String) {
        openSessionsJob?.cancel()
        openSessionsJob = viewModelScope.launch {
            runCatching { sessionRepo.getOpenSessionsForBranch(companyId, branchId) }
                .onSuccess { list -> _uiState.update { it.copy(openSessions = list) } }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun loadActiveSession(companyId: String, sessionId: String) {
        viewModelScope.launch {
            runCatching { sessionRepo.getSession(companyId, sessionId) }
                .onSuccess { s -> _uiState.update { it.copy(activeSession = s) } }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    /**
     * Persiste el cuadre sorpresa. Si `resolution=FORCE_ADJUST` y hay diferencia,
     * crea el `CashMovement` espejo antes de persistir el check.
     */
    fun submitSpotCheck(
        companyId: String,
        session: CashSession,
        countedCash: Double,
        denominations: Map<String, Int>,
        resolution: String,
        notes: String,
        currentUser: User
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, savedCheckId = null) }
            val now = Clock.System.now().toEpochMilliseconds()
            val expected = session.openingBalance + session.totalCashSales +
                session.totalMovementsIn - session.totalMovementsOut
            val diff = countedCash - expected
            runCatching {
                val draftCheck = SpotCashCheck(
                    branchId = session.branchId,
                    branchName = session.branchName,
                    registerId = session.registerId,
                    registerName = session.registerName,
                    sessionId = session.id,
                    cashierId = session.cashierId,
                    cashierName = session.cashierName,
                    checkedByUserId = currentUser.id,
                    checkedByUserName = currentUser.displayName,
                    checkedAt = now,
                    expectedCashAtCheck = expected,
                    countedCash = countedCash,
                    difference = diff,
                    resolution = resolution,
                    notes = notes.trim()
                )
                val created = spotRepo.create(companyId, draftCheck, denominations)
                if (resolution == SpotCashCheckResolution.FORCE_ADJUST && diff != 0.0) {
                    val movId = sessionRepo.applySpotCheckAdjustment(
                        companyId, session.id, diff, created.id, currentUser.id, currentUser.displayName
                    )
                    spotRepo.attachAdjustmentMovement(companyId, created.id, movId)
                    created.copy(adjustmentMovementId = movId)
                } else {
                    created
                }
            }.onSuccess { check ->
                _uiState.update { it.copy(isLoading = false, savedCheckId = check.id) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun loadRecentForBranch(companyId: String, branchId: String) {
        viewModelScope.launch {
            // Últimos 7 días
            val end = Clock.System.now().toEpochMilliseconds()
            val start = end - 7L * 24L * 60L * 60L * 1000L
            runCatching { spotRepo.getForBranchInRange(companyId, branchId, start, end) }
                .onSuccess { list -> _uiState.update { it.copy(recentChecksForBranch = list) } }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun loadCheck(companyId: String, checkId: String) {
        viewModelScope.launch {
            runCatching { spotRepo.get(companyId, checkId) }
                .onSuccess { c -> _uiState.update { it.copy(currentCheck = c) } }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun clearSaved() { _uiState.update { it.copy(savedCheckId = null) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
