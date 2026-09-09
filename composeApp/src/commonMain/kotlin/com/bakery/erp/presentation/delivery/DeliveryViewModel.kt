package com.bakery.erp.presentation.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.DeliveryRepository
import com.bakery.erp.domain.DispatchRepository
import com.bakery.erp.domain.FixedAssetRepository
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.model.AssetStatus
import com.bakery.erp.model.DeliveryRun
import com.bakery.erp.model.DISPATCH_TRIP_BOTH
import com.bakery.erp.model.DispatchNote
import com.bakery.erp.model.FixedAsset
import com.bakery.erp.model.Permission
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.runFormLabel
import com.bakery.erp.model.tripMismatchFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

data class DeliveryUiState(
    val runs: List<DeliveryRun> = emptyList(),
    val selectedRun: DeliveryRun? = null,
    val notesForSelectedRun: List<DispatchNote> = emptyList(),
    val candidateNotes: List<DispatchNote> = emptyList(),
    /**
     * `noteId → aviso` de las notas cuyo turno pertenece a un viaje que su sucursal
     * no recibe. Ver [com.bakery.erp.model.tripMismatchFor]: el pan de una masa que
     * solo se hornea en un turno sale en ese turno, aunque la sucursal reciba en el
     * otro viaje, y entonces hay que arrastrar la nota al viaje correcto. El flujo
     * ya lo permitía; lo que faltaba era decirlo donde se arma el camión.
     */
    val candidateNoteTripWarnings: Map<String, String> = emptyMap(),
    val driverCandidates: List<User> = emptyList(),
    val vehicleCandidates: List<FixedAsset> = emptyList(),
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    /** Día visible (millis al inicio del día). Default = hoy. */
    val dayAnchorMillis: Long = 0L,
    /** Hojas del rango visible en el historial (suelen ser de un mes). */
    val historyRuns: List<DeliveryRun> = emptyList(),
    val isHistoryLoading: Boolean = false
)

class DeliveryViewModel(
    private val repository: DeliveryRepository,
    private val dispatchRepository: DispatchRepository,
    private val authRepository: AuthRepository,
    private val fixedAssetRepository: FixedAssetRepository,
    private val branchRepository: BranchRepository,
    private val productionRepository: ProductionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeliveryUiState())
    val uiState: StateFlow<DeliveryUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        _uiState.update { it.copy(dayAnchorMillis = startOfToday()) }
    }

    fun observeDay(companyId: String, anchorMillis: Long = _uiState.value.dayAnchorMillis) {
        observeJob?.cancel()
        if (companyId.isEmpty()) return
        val (from, until) = dayBounds(anchorMillis)
        _uiState.update { it.copy(isLoading = true, dayAnchorMillis = anchorMillis, error = null) }
        observeJob = viewModelScope.launch {
            repository.observeRuns(companyId, from, until)
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { runs ->
                    _uiState.update { it.copy(runs = runs, isLoading = false) }
                }
        }
    }

    fun goToPreviousDay(companyId: String) {
        val tz = TimeZone.currentSystemDefault()
        val current = Instant.fromEpochMilliseconds(_uiState.value.dayAnchorMillis).toLocalDateTime(tz).date
        val prev = current.minusDays(1)
        observeDay(companyId, LocalDateTime(prev, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds())
    }

    fun goToNextDay(companyId: String) {
        val tz = TimeZone.currentSystemDefault()
        val current = Instant.fromEpochMilliseconds(_uiState.value.dayAnchorMillis).toLocalDateTime(tz).date
        val next = current.plusDays(1)
        observeDay(companyId, LocalDateTime(next, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds())
    }

    fun resetToToday(companyId: String) {
        observeDay(companyId, startOfToday())
    }

    private data class RunFormData(
        val drivers: List<User>,
        val vehicles: List<FixedAsset>,
        val notes: List<DispatchNote>,
        val tripWarnings: Map<String, String>
    )

    /**
     * Marca las notas cuyo turno pertenece a un viaje que su sucursal destino no
     * recibe — ver [tripMismatchFor].
     *
     * Nunca lanza: es una etiqueta de ayuda, y si los turnos o las sucursales no se
     * pudieron leer, la hoja de reparto se tiene que poder armar igual.
     */
    private suspend fun tripWarningsFor(
        companyId: String,
        producingBranchId: String,
        notes: List<DispatchNote>
    ): Map<String, String> {
        if (notes.isEmpty()) return emptyMap()
        val branches = runCatching { branchRepository.getBranches(companyId) }.getOrNull()
            ?: return emptyMap()
        val restrictedByBranch = branches.associate { it.id to it.receivesOnlyInTrip }
        if (restrictedByBranch.values.all { it == DISPATCH_TRIP_BOTH }) return emptyMap()
        val shifts = runCatching { productionRepository.getShiftsForBranch(companyId, producingBranchId) }
            .getOrNull() ?: return emptyMap()
        val tripByShift = shifts.associate { it.id to it.dispatchOrder }
        return notes.mapNotNull { note ->
            tripMismatchFor(
                branchName = note.destinationBranchName,
                restrictedTrip = restrictedByBranch[note.destinationBranchId] ?: DISPATCH_TRIP_BOTH,
                noteTripOrder = tripByShift[note.shiftId] ?: 0
            )?.let { note.id to it.runFormLabel() }
        }.toMap()
    }

    /** Carga datos para el form: candidatos a repartidor + notas READY/IN_TRANSIT del run. */
    fun loadFormData(
        companyId: String,
        originBranchId: String,
        dayMillis: Long,
        currentRunId: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val users = authRepository.getUsersForOrg(companyId)
                    .filter { it.isApproved && it.isActive }
                    .filter { it.hasPermission(Permission.DRIVE_DELIVERY) }
                    .filter { it.canAccessBranch(originBranchId) }
                val vehicles = fixedAssetRepository.getAssets(companyId)
                    .filter { it.active && it.status == AssetStatus.ACTIVE }
                    .filter { it.category.contains("Vehículo", ignoreCase = true) ||
                              it.category.contains("Vehiculo", ignoreCase = true) ||
                              it.category.contains("Transporte", ignoreCase = true) }
                    .sortedBy { it.name }
                val notes = if (originBranchId.isNotEmpty() && dayMillis > 0)
                    dispatchRepository.getNotesForRunForm(companyId, originBranchId, dayMillis, currentRunId)
                else emptyList()
                val warnings = tripWarningsFor(companyId, originBranchId, notes)
                RunFormData(users, vehicles, notes, warnings)
            }.onSuccess { data ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        driverCandidates = data.drivers,
                        vehicleCandidates = data.vehicles,
                        candidateNotes = data.notes,
                        candidateNoteTripWarnings = data.tripWarnings
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun loadRunDetail(companyId: String, runId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, error = null) }
            runCatching {
                val run = repository.getRun(companyId, runId)
                val noteIds = run?.dispatchNoteIds ?: emptyList()
                val notes = noteIds.mapNotNull { dispatchRepository.getNote(companyId, it) }
                run to notes
            }.onSuccess { (run, notes) ->
                _uiState.update {
                    it.copy(isDetailLoading = false, selectedRun = run, notesForSelectedRun = notes)
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isDetailLoading = false, error = err.message) }
            }
        }
    }

    fun saveRun(
        companyId: String,
        run: DeliveryRun,
        noteIds: List<String>,
        currentUser: User
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                repository.saveRun(companyId, run, noteIds, currentUser)
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSaved = true, selectedRun = it.selectedRun) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun startRun(companyId: String, runId: String, currentUser: User) {
        viewModelScope.launch {
            runCatching { repository.startRun(companyId, runId, currentUser) }
                .onSuccess { loadRunDetail(companyId, runId) }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun completeRun(companyId: String, runId: String, currentUser: User) {
        viewModelScope.launch {
            runCatching { repository.completeRun(companyId, runId, currentUser) }
                .onSuccess { loadRunDetail(companyId, runId) }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun cancelRun(companyId: String, runId: String, currentUser: User) {
        viewModelScope.launch {
            runCatching { repository.cancelRun(companyId, runId, currentUser) }
                .onSuccess { loadRunDetail(companyId, runId) }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    /** Disparado por DispatchViewModel cuando se confirma recepción de una nota
     *  que está en un run — auto-cierra si ya todas están entregadas. */
    fun tryAutoComplete(companyId: String, runId: String) {
        if (runId.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.tryAutoCompleteRun(companyId, runId) }
        }
    }

    fun clearSaved() { _uiState.update { it.copy(isSaved = false) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }

    /** Carga hojas del rango (típicamente un mes) para la pantalla de Historial.
     *  Snapshot one-shot, no listener — el historial no necesita ser vivo. */
    fun loadHistoryRange(companyId: String, fromMillis: Long, untilMillis: Long) {
        if (companyId.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isHistoryLoading = true, error = null) }
            runCatching { repository.getRunsInRange(companyId, fromMillis, untilMillis) }
                .onSuccess { runs ->
                    _uiState.update { it.copy(isHistoryLoading = false, historyRuns = runs) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isHistoryLoading = false, error = err.message) }
                }
        }
    }

    // ── Helpers de fecha ──────────────────────────────────────────────────────

    private fun startOfToday(): Long {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        return LocalDateTime(today, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds()
    }

    private fun dayBounds(anchorMillis: Long): Pair<Long, Long> {
        val DAY_MS = 24L * 60 * 60 * 1000
        return anchorMillis to (anchorMillis + DAY_MS - 1)
    }

    private fun LocalDate.minusDays(n: Int): LocalDate {
        // kotlinx-datetime estable: usar atTime + Instant + minusDays sería ideal, pero para v1
        // basta restar 1 al día y manejar borde.
        var y = year; var m = monthNumber; var d = dayOfMonth - n
        while (d < 1) {
            m -= 1
            if (m < 1) { m = 12; y -= 1 }
            d += daysInMonth(y, m)
        }
        return LocalDate(y, m, d)
    }

    private fun LocalDate.plusDays(n: Int): LocalDate {
        var y = year; var m = monthNumber; var d = dayOfMonth + n
        while (d > daysInMonth(y, m)) {
            d -= daysInMonth(y, m)
            m += 1
            if (m > 12) { m = 1; y += 1 }
        }
        return LocalDate(y, m, d)
    }

    private fun daysInMonth(year: Int, month: Int) = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        else -> 30
    }
}
