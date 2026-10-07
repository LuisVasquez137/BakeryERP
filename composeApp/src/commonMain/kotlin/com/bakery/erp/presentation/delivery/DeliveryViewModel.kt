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
import com.bakery.erp.model.ChainLine
import com.bakery.erp.model.ChainSummary
import com.bakery.erp.model.chainLineOf
import com.bakery.erp.model.summarizeChain
import com.bakery.erp.model.Branch
import com.bakery.erp.model.DeliveryLegStat
import com.bakery.erp.model.DeliveryRunStatus
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.model.StopProgress
import com.bakery.erp.model.orderStopsByRoute
import com.bakery.erp.model.stopProgress
import com.bakery.erp.model.tripOfNote
import com.bakery.erp.model.tripTitle
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
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
    val isHistoryLoading: Boolean = false,

    // ── Tablero del día (ver model/DeliveryBoard.kt) ─────────────────────────
    val boardNotes: List<DispatchNote> = emptyList(),
    val boardBranches: Map<String, Branch> = emptyMap(),
    /** `shiftId → viaje` ([ProductionShift.dispatchOrder]) de las plantas del día. */
    val boardTripByShift: Map<String, Int> = emptyMap(),
    /** `planta → (sucursal → tramo)`. */
    val boardLegStats: Map<String, Map<String, DeliveryLegStat>> = emptyMap(),
    val isBoardLoading: Boolean = false,
    /** Hoja de despacho abierta: planta + viaje, con lo que propone. */
    val dispatchSheet: DispatchSheetState? = null,
    val isDispatching: Boolean = false,
    /** Acuse del último despacho, para el snackbar. */
    val boardMessage: String? = null,
    /** La cadena de una entrega abierta por el dueño (ver model/UnitChain.kt). */
    val noteChain: NoteChain? = null,
    val isChainLoading: Boolean = false
)

/** Una entrega con sus eslabones y quién estaba en cada uno. */
data class NoteChain(
    val note: DispatchNote,
    val lines: List<ChainLine>,
    val summary: ChainSummary,
    val driverName: String,
    val vehicleInfo: String
)

/** Lo que la hoja "Despachar viaje" necesita: candidatos y lo que propone de entrada. */
data class DispatchSheetState(
    val plantId: String,
    val trip: Int,
    val drivers: List<User>,
    val vehicles: List<FixedAsset>,
    val suggestedDriverId: String,
    val suggestedVehicleId: String
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

    // ── Tablero del día ─────────────────────────────────────────────────────

    private var boardJob: Job? = null

    /**
     * Escucha las notas y las hojas del día. Las notas son las mismas que veía Despacho
     * en sus pestañas; lo nuevo es cómo se muestran. Turnos, sucursales y tiempos de
     * cada tramo se leen una vez por planta: cambian poco y no justifican un listener.
     */
    fun observeBoard(companyId: String, anchorMillis: Long = _uiState.value.dayAnchorMillis) {
        boardJob?.cancel()
        if (companyId.isEmpty()) return
        val (from, until) = dayBounds(anchorMillis)
        _uiState.update { it.copy(isBoardLoading = true, dayAnchorMillis = anchorMillis, error = null) }
        boardJob = viewModelScope.launch {
            if (_uiState.value.boardBranches.isEmpty()) {
                val branches = runCatching { branchRepository.getBranches(companyId) }.getOrElse { emptyList() }
                _uiState.update { it.copy(boardBranches = branches.associateBy { b -> b.id }) }
            }
            combine(
                dispatchRepository.observeNotes(companyId, from, until),
                repository.observeRuns(companyId, from, until)
            ) { notes, runs -> notes to runs }
                .catch { e -> _uiState.update { it.copy(isBoardLoading = false, error = e.message) } }
                .collect { (notes, runs) ->
                    _uiState.update { it.copy(boardNotes = notes, runs = runs, isBoardLoading = false) }
                    loadPlantDataIfNeeded(companyId, notes.map { it.originBranchId }.toSet())
                }
        }
    }

    private val plantsLoaded = mutableSetOf<String>()

    private suspend fun loadPlantDataIfNeeded(companyId: String, plantIds: Set<String>) {
        val missing = plantIds.filter { it.isNotEmpty() && it !in plantsLoaded }
        if (missing.isEmpty()) return
        plantsLoaded += missing
        val trips = mutableMapOf<String, Int>()
        val legs = mutableMapOf<String, Map<String, DeliveryLegStat>>()
        coroutineScope {
            missing.map { plantId ->
                async {
                    val shifts = runCatching { productionRepository.getShiftsForBranch(companyId, plantId) }
                        .getOrElse { emptyList() }
                    val stats = dispatchRepository.getDeliveryLegStats(companyId, plantId)
                    Triple(plantId, shifts, stats)
                }
            }.awaitAll()
        }.forEach { (plantId, shifts, stats) ->
            shifts.forEach { trips[it.id] = it.dispatchOrder }
            legs[plantId] = stats
        }
        _uiState.update {
            it.copy(boardTripByShift = it.boardTripByShift + trips, boardLegStats = it.boardLegStats + legs)
        }
    }

    fun shiftBoardDay(companyId: String, deltaDays: Int) {
        val tz = TimeZone.currentSystemDefault()
        val current = Instant.fromEpochMilliseconds(_uiState.value.dayAnchorMillis).toLocalDateTime(tz).date
        val target = if (deltaDays < 0) current.minusDays(-deltaDays) else current.plusDays(deltaDays)
        observeBoard(companyId, LocalDateTime(target, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds())
    }

    fun boardToday(companyId: String) = observeBoard(companyId, startOfToday())

    fun boardToDay(companyId: String, millis: Long) {
        val tz = TimeZone.currentSystemDefault()
        val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date
        observeBoard(companyId, LocalDateTime(date, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds())
    }

    /** Viaje en que de verdad sale la nota (ver [tripOfNote]). */
    fun tripOf(note: DispatchNote): Int {
        val state = _uiState.value
        return tripOfNote(
            shiftTrip = state.boardTripByShift[note.shiftId] ?: 0,
            destinationOnlyTrip = state.boardBranches[note.destinationBranchId]?.receivesOnlyInTrip ?: DISPATCH_TRIP_BOTH
        )
    }

    /** Dónde va el camión para esta nota, o null si no está en camino. */
    fun stopProgressOf(note: DispatchNote, nowMillis: Long): StopProgress? {
        if (note.status != DispatchStatus.IN_TRANSIT) return null
        val state = _uiState.value
        val run = state.runs.find { note.id in it.dispatchNoteIds } ?: return null
        val idx = run.dispatchNoteIds.indexOf(note.id).coerceAtLeast(0)
        val leg = state.boardLegStats[note.originBranchId]?.get(note.destinationBranchId)
        return stopProgress(idx, run.dispatchNoteIds.size, note.dispatchedAt ?: run.startedAt ?: nowMillis, nowMillis, leg)
    }

    /**
     * Abre la hoja "Despachar viaje": repartidores y vehículos de la planta, y propone
     * los del último viaje igual — normalmente el de la noche lo hace siempre el mismo.
     */
    fun openDispatchSheet(companyId: String, plantId: String, trip: Int) {
        viewModelScope.launch {
            runCatching {
                val drivers = authRepository.getUsersForOrg(companyId)
                    .filter { it.isApproved && it.isActive }
                    .filter { it.hasPermission(Permission.DRIVE_DELIVERY) && it.canAccessBranch(plantId) }
                    .sortedBy { it.displayName.lowercase() }
                val vehicles = fixedAssetRepository.getAssets(companyId)
                    .filter { it.active && it.status == AssetStatus.ACTIVE }
                    .filter { it.category.contains("Vehículo", ignoreCase = true) ||
                              it.category.contains("Vehiculo", ignoreCase = true) ||
                              it.category.contains("Transporte", ignoreCase = true) }
                    .sortedBy { it.name }
                val now = Clock.System.now().toEpochMilliseconds()
                val last = runCatching { repository.getRunsInRange(companyId, now - 21L * 24 * 60 * 60 * 1000, now + 24L * 60 * 60 * 1000) }
                    .getOrElse { emptyList() }
                    .filter { it.originBranchId == plantId && it.status != DeliveryRunStatus.CANCELLED }
                    .sortedByDescending { it.createdAt }
                val previous = last.firstOrNull { it.trip == trip } ?: last.firstOrNull()
                DispatchSheetState(
                    plantId = plantId,
                    trip = trip,
                    drivers = drivers,
                    vehicles = vehicles,
                    suggestedDriverId = previous?.driverUserId?.takeIf { id -> drivers.any { it.id == id } }
                        ?: drivers.singleOrNull()?.id.orEmpty(),
                    suggestedVehicleId = previous?.vehicleAssetId?.takeIf { id -> vehicles.any { it.id == id } }
                        ?: vehicles.singleOrNull()?.id.orEmpty()
                )
            }.onSuccess { sheet -> _uiState.update { it.copy(dispatchSheet = sheet) } }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun closeDispatchSheet() { _uiState.update { it.copy(dispatchSheet = null) } }
    fun clearBoardMessage() { _uiState.update { it.copy(boardMessage = null) } }

    /**
     * "Salir con N paradas": arma la hoja con las paradas en el orden de la ruta fija y
     * la inicia de una vez. Antes eran dos pasos —guardar y "Iniciar reparto"— y el
     * segundo se olvidaba: la hoja quedaba pendiente con el camión ya en la calle.
     */
    fun dispatchTrip(
        companyId: String,
        currentUser: User,
        trip: Int,
        noteIds: List<String>,
        driver: User,
        vehicle: FixedAsset?
    ) {
        val sheet = _uiState.value.dispatchSheet ?: return
        if (noteIds.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDispatching = true, error = null) }
            runCatching {
                val state = _uiState.value
                val plant = state.boardBranches[sheet.plantId]
                val notes = state.boardNotes.filter { it.id in noteIds }
                val ordered = orderStopsByRoute(notes, plant?.deliveryRouteOrder.orEmpty()).map { it.id }
                val run = repository.saveRun(
                    companyId,
                    DeliveryRun(
                        originBranchId = sheet.plantId,
                        originBranchName = plant?.name.orEmpty(),
                        driverUserId = driver.id,
                        driverName = driver.displayName,
                        plannedDate = state.dayAnchorMillis,
                        vehicleAssetId = vehicle?.id.orEmpty(),
                        vehicleInfo = vehicle?.let { vehicleDisplayName(it) },
                        trip = trip
                    ),
                    ordered,
                    currentUser
                )
                repository.startRun(companyId, run.id, currentUser)
                "${tripTitle(trip)} salió con ${ordered.size} parada${if (ordered.size == 1) "" else "s"} · ${driver.displayName}"
            }.onSuccess { msg ->
                _uiState.update { it.copy(isDispatching = false, dispatchSheet = null, boardMessage = msg) }
            }.onFailure { err ->
                _uiState.update { it.copy(isDispatching = false, error = err.message ?: "No se pudo despachar") }
            }
        }
    }

    /**
     * Arma la cadena de una entrega: la nota con sus líneas, el lote que la horneó (para
     * saber si el turno salió corto de ese pan) y la hoja de reparto (quién la llevó).
     * Tres lecturas puntuales, solo cuando el dueño la abre.
     */
    fun openNoteChain(companyId: String, noteId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isChainLoading = true) }
            runCatching {
                val note = dispatchRepository.getNote(companyId, noteId) ?: error("No se encontró la entrega")
                val (lotLines, run) = coroutineScope {
                    val lots = async {
                        if (note.lotId.isEmpty()) emptyList()
                        else runCatching { productionRepository.getLotLines(companyId, note.lotId) }.getOrElse { emptyList() }
                    }
                    val r = async {
                        if (note.deliveryRunId.isEmpty()) null
                        else runCatching { repository.getRun(companyId, note.deliveryRunId) }.getOrNull()
                    }
                    lots.await() to r.await()
                }
                val byProduct = lotLines.filter { !it.isSpecial }.associateBy { it.productId }
                val counted = note.status == DispatchStatus.DELIVERED || note.status == DispatchStatus.DELIVERED_WITH_DISCREPANCY
                val lines = note.lines.map { chainLineOf(it, byProduct[it.productId], counted) }
                    // Lo que tiene diferencia primero: es lo que se vino a ver.
                    .sortedWith(compareByDescending<ChainLine> { it.hasDifference }.thenBy { it.productName.lowercase() })
                NoteChain(
                    note = note,
                    lines = lines,
                    summary = summarizeChain(lines),
                    driverName = run?.driverName.orEmpty(),
                    vehicleInfo = run?.vehicleInfo.orEmpty()
                )
            }.onSuccess { chain -> _uiState.update { it.copy(noteChain = chain, isChainLoading = false) } }
                .onFailure { err -> _uiState.update { it.copy(isChainLoading = false, error = err.message) } }
        }
    }

    fun closeNoteChain() { _uiState.update { it.copy(noteChain = null) } }

    /** Guarda la ruta fija del camión de [plantId] (escritura parcial). */
    fun saveDeliveryRoute(companyId: String, plantId: String, destinationIds: List<String>) {
        _uiState.update { s ->
            s.copy(boardBranches = s.boardBranches.mapValues { (id, b) ->
                if (id == plantId) b.copy(deliveryRouteOrder = destinationIds) else b
            })
        }
        viewModelScope.launch {
            runCatching { branchRepository.updateDeliveryRouteOrder(companyId, plantId, destinationIds) }
                .onSuccess { _uiState.update { it.copy(boardMessage = "Ruta guardada") } }
                .onFailure { err -> _uiState.update { it.copy(error = "No se pudo guardar la ruta — ${err.message}") } }
        }
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
