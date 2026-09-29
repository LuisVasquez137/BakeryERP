package com.bakery.erp.presentation.cashaudit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.CashAuditRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.CashAuditEvent
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class CashAuditUiState(
    val branches: List<Branch> = emptyList(),
    val selectedBranchId: String = "",
    val date: LocalDate? = null,
    val events: List<CashAuditEvent> = emptyList(),
    val onlyOutOfSchedule: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val visibleEvents: List<CashAuditEvent>
        get() = if (onlyOutOfSchedule) events.filter { it.outOfSchedule } else events
}

/** Bitácora de cajas por sucursal y por día. */
class CashAuditViewModel(
    private val auditRepository: CashAuditRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashAuditUiState())
    val uiState: StateFlow<CashAuditUiState> = _uiState.asStateFlow()

    private val tz = TimeZone.currentSystemDefault()

    /** [preferredBranchId]: la sucursal del aviso que trajo hasta acá, si vino de una notificación. */
    fun load(companyId: String, user: User, preferredBranchId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { branchRepository.getBranches(companyId) }
                .onSuccess { all ->
                    val branches = all.filter { it.active && user.canAccessBranch(it.id) }.sortedBy { it.name.lowercase() }
                    val selected = preferredBranchId?.takeIf { id -> branches.any { it.id == id } }
                        ?: _uiState.value.selectedBranchId.takeIf { id -> branches.any { it.id == id } }
                        ?: branches.firstOrNull()?.id.orEmpty()
                    val today = Clock.System.now().toLocalDateTime(tz).date
                    _uiState.update { it.copy(branches = branches, selectedBranchId = selected, date = it.date ?: today) }
                    refresh(companyId)
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun selectBranch(companyId: String, branchId: String) {
        _uiState.update { it.copy(selectedBranchId = branchId) }
        refresh(companyId)
    }

    fun shiftDay(companyId: String, days: Int) {
        val d = _uiState.value.date ?: return
        _uiState.update { it.copy(date = d.plus(DatePeriod(days = days))) }
        refresh(companyId)
    }

    fun goToday(companyId: String) {
        _uiState.update { it.copy(date = Clock.System.now().toLocalDateTime(tz).date) }
        refresh(companyId)
    }

    fun toggleOnlyOutOfSchedule() = _uiState.update { it.copy(onlyOutOfSchedule = !it.onlyOutOfSchedule) }

    private fun refresh(companyId: String) {
        val s = _uiState.value
        val date = s.date ?: return
        if (s.selectedBranchId.isEmpty()) { _uiState.update { it.copy(isLoading = false, events = emptyList()) }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val from = date.atStartOfDayIn(tz).toEpochMilliseconds()
            val until = date.plus(DatePeriod(days = 1)).atStartOfDayIn(tz).toEpochMilliseconds()
            runCatching { auditRepository.getEvents(companyId, s.selectedBranchId, from, until) }
                .onSuccess { events -> _uiState.update { it.copy(isLoading = false, events = events) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
