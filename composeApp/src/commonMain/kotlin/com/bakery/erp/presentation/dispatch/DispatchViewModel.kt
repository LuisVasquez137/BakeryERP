package com.bakery.erp.presentation.dispatch

import com.bakery.erp.model.PackingReason

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.DeliveryRepository
import com.bakery.erp.domain.DispatchRepository
import com.bakery.erp.domain.PackingCloseResult
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.NotificationRepository
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.model.AppNotification
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.DispatchNote
import com.bakery.erp.model.DispatchNoteLine
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.model.FinishedMovementReference
import com.bakery.erp.model.FinishedMovementType
import com.bakery.erp.model.FinishedStockMovement
import com.bakery.erp.model.NotificationTarget
import com.bakery.erp.model.NotificationType
import com.bakery.erp.model.Permission
import com.bakery.erp.model.ProductionShift
import com.bakery.erp.model.User
import com.bakery.erp.model.applyBranchOrder
import com.bakery.erp.model.canAccessBranch
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

data class DispatchUiState(
    val notes: List<DispatchNote> = emptyList(),
    val selectedNote: DispatchNote? = null,
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    /** Día visible en las pestañas Listas/Tránsito/Recibidas (millis al inicio del día). */
    val dayAnchorMillis: Long = 0L,
    /** Banner luego de confirmar recepción. */
    val lastReceptionSummary: String? = null,
    /**
     * Resultado del conteo que acaba de cerrarse, para acusarlo con un diálogo antes
     * de salir de la pantalla.
     *
     * No alcanza con `isSaved`: al cerrar el conteo la nota pasa a DELIVERED y la
     * pantalla de recepción vuelve a dibujar exactamente el mismo formulario, así que
     * quien contó no tenía forma de saber si el ingreso quedó registrado — el único
     * aviso era un `lastReceptionSummary` que ninguna pantalla mostraba. Esto lleva el
     * dato que importa (cuántas unidades, a qué sucursal) hasta el acuse.
     */
    val receptionResult: ReceptionResult? = null
)

/** Lo que entró al inventario al cerrar un conteo de recepción. */
data class ReceptionResult(
    val noteId: String,
    val destinationBranchName: String,
    val unitsReceived: Double,
    val productCount: Int,
    val discrepancyCount: Int
)

/**
 * Estado de la matriz "Por empacar". Vive en paralelo a [DispatchUiState] porque la
 * pestaña Por empacar tiene su propio listener (por día/turno/planta), distinto del
 * listener mensual que alimenta a las demás pestañas.
 */
data class PackingMatrixUiState(
    /**
     * Sucursal destino que la matriz abre seleccionada (pestaña en teléfono). La pone
     * el tablero de Reparto al tocar una tarjeta "En empaque": la planta llega
     * directo a esa sucursal en vez de buscarla entre las pestañas.
     */
    val focusDestinationBranchId: String = "",
    val producingBranches: List<Branch> = emptyList(),
    val producingBranchId: String = "",
    val shifts: List<ProductionShift> = emptyList(),
    val shiftId: String = "",
    val dayMillis: Long = 0L,
    val notes: List<DispatchNote> = emptyList(),
    val isLoading: Boolean = false,
    val isClosing: Boolean = false,
    val error: String? = null,
    val lastCloseResult: PackingCloseResult? = null,
    /**
     * Lo que a cada sucursal le quedó debiendo un viaje ANTERIOR del mismo día,
     * por `"destinationBranchId|productId"`.
     *
     * Es la deuda que el segundo viaje todavía puede saldar: si de noche se quemó
     * pan y Calvario recibió 55 de 60, al empacar el turno de día hay que poder
     * verlo y reponerlo. No se arrastra a mañana a propósito — nuevo día, nuevo
     * pedido — pero dentro del mismo día el pan todavía se puede mandar.
     */
    val shortfallFromEarlierShifts: Map<String, ShiftShortfall> = emptyMap(),
    /**
     * Orden en que la planta quiere ver las sucursales al empacar
     * ([Branch.dispatchBranchOrder]). Vacío = alfabético. Se guarda en la planta y
     * no en el dispositivo: es el orden físico en que se arman las canastas.
     */
    val branchOrder: List<String> = emptyList()
)

/** Unidades que un viaje anterior del día dejó debiendo, con el motivo que se anotó. */
data class ShiftShortfall(
    val units: Double,
    val shiftName: String,
    val reason: String
)

class DispatchViewModel(
    private val repository: DispatchRepository,
    private val deliveryRepository: DeliveryRepository,
    private val branchRepository: BranchRepository,
    private val productionRepository: ProductionRepository,
    private val notificationRepository: NotificationRepository,
    private val finishedStockRepository: FinishedStockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DispatchUiState())
    val uiState: StateFlow<DispatchUiState> = _uiState.asStateFlow()

    private val _packingState = MutableStateFlow(PackingMatrixUiState())
    val packingState: StateFlow<PackingMatrixUiState> = _packingState.asStateFlow()

    private var observeJob: Job? = null
    private var packingObserveJob: Job? = null

    /** Usuario de la sesión, sembrado por [setupPackingFor]. Ver su comentario. */
    private var packingUser: User? = null

    init {
        _uiState.update { it.copy(dayAnchorMillis = startOfToday()) }
        _packingState.update { it.copy(dayMillis = startOfToday()) }
    }

    /** Listener en vivo de notas del día en curso. Alimenta Listas/Tránsito/Recibidas. */
    fun observeDay(companyId: String, anchorMillis: Long = _uiState.value.dayAnchorMillis) {
        observeJob?.cancel()
        if (companyId.isEmpty()) return
        val (from, until) = dayBounds(anchorMillis)
        _uiState.update { it.copy(isLoading = true, dayAnchorMillis = anchorMillis, error = null) }
        observeJob = viewModelScope.launch {
            repository.observeNotes(companyId, from, until)
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { notes ->
                    _uiState.update { it.copy(notes = notes, isLoading = false) }
                }
        }
    }

    fun shiftListDay(companyId: String, deltaDays: Int) {
        val tz = TimeZone.currentSystemDefault()
        val current = Instant.fromEpochMilliseconds(_uiState.value.dayAnchorMillis).toLocalDateTime(tz).date
        val next = current.plus(deltaDays, DateTimeUnit.DAY)
        val anchor = LocalDateTime(next, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds()
        observeDay(companyId, anchor)
    }

    fun goToTodayList(companyId: String) {
        val today = startOfToday()
        if (today == _uiState.value.dayAnchorMillis) return
        observeDay(companyId, today)
    }

    fun loadNoteDetail(companyId: String, noteId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, error = null) }
            runCatching { repository.getNote(companyId, noteId) }
                .onSuccess { note ->
                    _uiState.update { it.copy(isDetailLoading = false, selectedNote = note) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isDetailLoading = false, error = err.message) }
                }
        }
    }

    fun confirmPacking(
        companyId: String,
        noteId: String,
        lines: List<DispatchNoteLine>,
        currentUser: User,
        /** Canastas que salen; 0 = no se pidió (la nota de la propia planta no viaja). */
        baskets: Int = 0
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                repository.savePackedLines(companyId, noteId, lines, currentUser)
                if (baskets > 0) repository.setBasketsSent(companyId, noteId, baskets)
                // Cierra con la regla de "pan que no viaja" adentro: la nota de la
                // propia planta no se queda en Listas esperando un reparto que no
                // existe, entra al inventario de su tienda.
                repository.closeNoteAfterPacking(companyId, noteId, currentUser)
            }.onSuccess { deliveredLocally ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSaved = true,
                        lastReceptionSummary = if (deliveredLocally)
                            "Contado y recibido en la tienda de la planta" else null
                    )
                }
                loadNoteDetail(companyId, noteId)
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun acknowledgeArrival(
        companyId: String,
        noteId: String,
        lines: List<DispatchNoteLine>,
        currentUser: User,
        basketsReceived: Int? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                repository.acknowledgeArrival(companyId, noteId, lines, currentUser, basketsReceived)
                // Auto-cierre del run: el repartidor cumplió su parte al reportar llegada.
                val noteFresh = repository.getNote(companyId, noteId)
                noteFresh?.deliveryRunId
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { runCatching { deliveryRepository.tryAutoCompleteRun(companyId, it) } }
            }.onSuccess {
                // No marcamos isSaved para que la pantalla siga abierta y muestre el
                // paso 2 (conteo) en lugar de navegar atrás.
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lastReceptionSummary = "Llegada reportada. Cuenta el pan con calma y cierra el conteo después."
                    )
                }
                loadNoteDetail(companyId, noteId)
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun markAsLocalDispatch(companyId: String, noteId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.markAsLocalDispatch(companyId, noteId, currentUser) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSaved = true,
                            lastReceptionSummary = "Producción confirmada en mostrador"
                        )
                    }
                    loadNoteDetail(companyId, noteId)
                }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, error = err.message) } }
        }
    }

    fun markInTransit(companyId: String, noteId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.markInTransit(companyId, noteId, currentUser) }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isSaved = true) }
                    loadNoteDetail(companyId, noteId)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun confirmReception(
        companyId: String,
        noteId: String,
        lines: List<DispatchNoteLine>,
        currentUser: User
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                repository.confirmReception(companyId, noteId, lines, currentUser)
                // Auto-cierre del run si todas sus notas ya están terminales.
                val noteFresh = repository.getNote(companyId, noteId)
                noteFresh?.deliveryRunId
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { runCatching { deliveryRepository.tryAutoCompleteRun(companyId, it) } }
                noteFresh
            }.onSuccess { noteFresh ->
                val short = lines.count { it.discrepancyType != null }
                val counted = lines.filter { it.receivedQuantity > 0 }
                // La nota recargada se devuelve DESDE el bloque protegido y se aplica en
                // el mismo `update` que apaga el overlay. Antes acá se llamaba
                // `loadNoteDetail`, que es un segundo viaje a Firestore lanzado DESPUÉS
                // de poner `isLoading = false`: el overlay se iba, la pantalla volvía a
                // dibujar el formulario de conteo un par de segundos y recién entonces
                // aparecía el acuse. Quien acababa de contar veía su lista otra vez y
                // leía que no había pasado nada. El viaje además ya estaba hecho —
                // `noteFresh` se lee arriba para decidir el auto-cierre de la hoja.
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSaved = true,
                        selectedNote = noteFresh ?: it.selectedNote,
                        lastReceptionSummary = if (short == 0) "Recepción confirmada sin discrepancias"
                                                else "Recepción confirmada con $short discrepancia(s)",
                        receptionResult = ReceptionResult(
                            noteId = noteId,
                            destinationBranchName = (noteFresh ?: it.selectedNote)
                                ?.destinationBranchName.orEmpty(),
                            unitsReceived = counted.sumOf { l -> l.receivedQuantity },
                            productCount = counted.size,
                            discrepancyCount = short
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun cancelNote(
        companyId: String,
        noteId: String,
        currentUser: User,
        reason: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.cancelNote(companyId, noteId, currentUser, reason) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSaved = true,
                            lastReceptionSummary = "Nota cancelada. El pan quedó en el inventario de la planta."
                        )
                    }
                    // Recarga: la pantalla se queda abierta y tiene que pasar a mostrar
                    // el estado Cancelada con el botón de restaurar.
                    loadNoteDetail(companyId, noteId)
                }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, error = err.message) } }
        }
    }

    fun restoreNote(companyId: String, noteId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.restoreNote(companyId, noteId, currentUser) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSaved = true,
                            lastReceptionSummary = "Nota restaurada. El pan se descontó del inventario de la planta."
                        )
                    }
                    loadNoteDetail(companyId, noteId)
                }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, error = err.message) } }
        }
    }

    fun clearSaved() { _uiState.update { it.copy(isSaved = false) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearLastReception() { _uiState.update { it.copy(lastReceptionSummary = null) } }
    fun clearReceptionResult() { _uiState.update { it.copy(receptionResult = null) } }

    fun selectNote(note: DispatchNote?) { _uiState.update { it.copy(selectedNote = note) } }

    // ── Helpers de filtrado para las pantallas ────────────────────────────────

    /** Devuelve solo las notas que el usuario puede ver según sus sucursales asignadas. */
    fun visibleFor(user: User, notes: List<DispatchNote>): List<DispatchNote> =
        notes.filter { user.canAccessBranch(it.originBranchId) || user.canAccessBranch(it.destinationBranchId) }

    private fun startOfToday(): Long {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        return LocalDateTime(today, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds()
    }

    /** Arranque del día local que contiene [millis]. El picker devuelve mediodía UTC. */
    private fun startOfDay(millis: Long): Long {
        val tz = TimeZone.currentSystemDefault()
        val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date
        return LocalDateTime(date, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds()
    }

    // ── Matriz de empaque ─────────────────────────────────────────────────────

    /**
     * Inicializa la matriz para el usuario actual. Carga sucursales productoras accesibles
     * y, si todavía no hay selección, autoselecciona la primera. Carga turnos de esa planta
     * y empieza el listener vivo.
     */
    fun setupPackingFor(companyId: String, user: User) {
        if (companyId.isEmpty()) return
        // Lo guardamos para `ensureNotesForStartedLots`: la reparación de notas
        // faltantes se dispara desde `restartPackingListener`, al que se llega
        // también por los cambios de día y de turno, que no reciben el usuario.
        packingUser = user
        viewModelScope.launch {
            _packingState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val all = branchRepository.getBranches(companyId)
                val producing = all.filter {
                    it.capabilities.contains(BranchCapability.CAN_PRODUCE) &&
                        user.canAccessBranch(it.id)
                }
                val current = _packingState.value
                val selected = current.producingBranchId.takeIf { id -> producing.any { it.id == id } }
                    ?: producing.firstOrNull()?.id ?: ""
                _packingState.update {
                    it.copy(
                        producingBranches = producing,
                        producingBranchId = selected,
                        branchOrder = producing.find { b -> b.id == selected }?.dispatchBranchOrder
                            ?: emptyList()
                    )
                }
                if (selected.isNotEmpty()) loadShiftsAndStartListener(companyId, selected)
                else _packingState.update { it.copy(shifts = emptyList(), shiftId = "", notes = emptyList(), isLoading = false) }
            }.onFailure { err ->
                _packingState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    /**
     * Deja la matriz lista en la planta, el turno, el día y la sucursal de una tarjeta
     * del tablero de Reparto. Se llama antes de navegar: `setupPackingFor` conserva la
     * planta y `loadShiftsAndStartListener` el turno si siguen siendo válidos.
     */
    fun focusPacking(producingBranchId: String, shiftId: String, dayMillis: Long, destinationBranchId: String) {
        _packingState.update {
            it.copy(
                producingBranchId = producingBranchId,
                shiftId = shiftId,
                dayMillis = startOfDay(dayMillis),
                focusDestinationBranchId = destinationBranchId,
                notes = if (it.producingBranchId == producingBranchId && it.shiftId == shiftId) it.notes else emptyList()
            )
        }
    }

    fun selectPackingBranch(companyId: String, branchId: String) {
        if (branchId == _packingState.value.producingBranchId) return
        _packingState.update {
            it.copy(
                producingBranchId = branchId,
                shiftId = "",
                notes = emptyList(),
                // Cada planta tiene su propio orden de empaque.
                branchOrder = it.producingBranches.find { b -> b.id == branchId }?.dispatchBranchOrder
                    ?: emptyList()
            )
        }
        viewModelScope.launch { loadShiftsAndStartListener(companyId, branchId) }
    }

    /**
     * Guarda el orden en que esta planta quiere ver las sucursales al empacar.
     *
     * Escritura parcial ([BranchRepository.updateDispatchBranchOrder]): un `set()`
     * desde acá sobreescribiría la ficha entera de la sucursal. Se aplica en
     * memoria de inmediato para que la matriz se reacomode sin esperar al servidor.
     */
    fun saveDispatchBranchOrder(companyId: String, newOrder: List<String>) {
        val plantId = _packingState.value.producingBranchId
        if (companyId.isEmpty() || plantId.isEmpty()) return
        // El diálogo solo lista las sucursales que HOY tienen nota. Las que no
        // pidieron hoy no pueden perder la posición que ya tenían configurada: se
        // conservan detrás, y quedan recolocables el día que vuelvan a aparecer.
        // Sin esto, ordenar un domingo flojo borraría del orden a media empresa.
        val orderedBranchIds =
            if (newOrder.isEmpty()) emptyList() // volver a alfabético limpia todo, a propósito
            else newOrder + _packingState.value.branchOrder.filterNot { it in newOrder }
        _packingState.update { state ->
            state.copy(
                branchOrder = orderedBranchIds,
                notes = applyBranchOrder(
                    state.notes, orderedBranchIds,
                    { it.destinationBranchId }, { it.destinationBranchName }
                ),
                producingBranches = state.producingBranches.map {
                    if (it.id == plantId) it.copy(dispatchBranchOrder = orderedBranchIds) else it
                }
            )
        }
        viewModelScope.launch {
            runCatching {
                branchRepository.updateDispatchBranchOrder(companyId, plantId, orderedBranchIds)
            }.onFailure { err ->
                _packingState.update {
                    it.copy(error = "No se pudo guardar el orden — ${err.message}")
                }
            }
        }
    }

    fun selectPackingShift(companyId: String, shiftId: String) {
        if (shiftId == _packingState.value.shiftId) return
        _packingState.update { it.copy(shiftId = shiftId) }
        restartPackingListener(companyId)
    }

    fun shiftPackingDay(companyId: String, deltaDays: Int) {
        val tz = TimeZone.currentSystemDefault()
        val current = Instant.fromEpochMilliseconds(_packingState.value.dayMillis).toLocalDateTime(tz).date
        val next = current.plus(deltaDays, DateTimeUnit.DAY)
        val nextMillis = LocalDateTime(next, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds()
        _packingState.update { it.copy(dayMillis = nextMillis) }
        restartPackingListener(companyId)
    }

    fun goToTodayPacking(companyId: String) {
        val today = startOfToday()
        if (today == _packingState.value.dayMillis) return
        _packingState.update { it.copy(dayMillis = today) }
        restartPackingListener(companyId)
    }

    /**
     * Salto directo a un día cualquiera. Sin esto, moverse a una fecha lejana eran
     * tantos taps en las flechitas como días de distancia — imposible revisar el
     * despacho de la semana pasada o de un día futuro ya planificado.
     *
     * Normaliza al arranque del día en la zona local: el listener filtra por rango,
     * y el picker devuelve UTC.
     */
    fun goToPackingDay(companyId: String, dayMillis: Long) {
        val normalized = startOfDay(dayMillis)
        if (normalized == _packingState.value.dayMillis) return
        _packingState.update { it.copy(dayMillis = normalized) }
        restartPackingListener(companyId)
    }

    /** Igual que [goToPackingDay] pero para la lista de notas (Listas/Tránsito/Recibidas). */
    fun goToListDay(companyId: String, dayMillis: Long) {
        observeDay(companyId, startOfDay(dayMillis))
    }

    /**
     * Marcas hechas en pantalla cuya escritura todavía no se ve reflejada en lo que
     * devuelve el servidor, por `noteId to lineId`.
     *
     * El update optimista solo no alcanzaba: marcar un pan toca `updatedAt` de la
     * nota, eso despierta al listener, el listener recarga las notas y **reemplaza
     * el estado entero**. Si esa recarga sale antes de que la escritura se propague,
     * devuelve el valor viejo y el check se apaga por un instante — la intermitencia
     * de ~1s que se veía al ir marcando. Reaplicar lo pendiente encima de cada
     * snapshot lo elimina; la entrada se descarta recién cuando el servidor confirma
     * el mismo valor.
     */
    private val pendingLineChecks = mutableMapOf<Pair<String, String>, Long?>()

    /** Cantidad empacada + razón escritas en pantalla y todavía sin confirmar. */
    private val pendingPackedQuantities = mutableMapOf<Pair<String, String>, Pair<Double, String?>>()

    private fun List<DispatchNote>.withPendingEdits(): List<DispatchNote> {
        if (pendingLineChecks.isEmpty() && pendingPackedQuantities.isEmpty()) return this
        return map { note ->
            note.copy(lines = note.lines.map { line ->
                val key = note.id to line.id
                var result = line
                if (pendingLineChecks.containsKey(key)) {
                    val expected = pendingLineChecks.getValue(key)
                    if (line.lineCheckedAt == expected) pendingLineChecks.remove(key)
                    else result = result.copy(lineCheckedAt = expected)
                }
                pendingPackedQuantities[key]?.let { (qty, reason) ->
                    if (line.packedQuantity == qty && line.packedReason == reason) {
                        pendingPackedQuantities.remove(key)
                    } else {
                        result = result.copy(packedQuantity = qty, packedReason = reason)
                    }
                }
                result
            })
        }
    }

    fun toggleLineChecked(companyId: String, noteId: String, lineId: String, currentlyChecked: Boolean) {
        viewModelScope.launch {
            val newValue: Long? = if (currentlyChecked) null else Clock.System.now().toEpochMilliseconds()
            pendingLineChecks[noteId to lineId] = newValue
            // Update optimista para que el check sienta instantáneo.
            _packingState.update { st ->
                st.copy(notes = st.notes.map { n ->
                    if (n.id != noteId) n
                    else n.copy(lines = n.lines.map { l ->
                        if (l.id == lineId) l.copy(lineCheckedAt = newValue) else l
                    })
                })
            }
            runCatching { repository.setLineChecked(companyId, noteId, lineId, newValue) }
                .onFailure { err ->
                    // La escritura falló: soltar la marca pendiente y volver a lo real,
                    // en vez de dejar un check que no existe en la base.
                    pendingLineChecks.remove(noteId to lineId)
                    _packingState.update { st ->
                        st.copy(
                            error = err.message,
                            notes = st.notes.map { n ->
                                if (n.id != noteId) n
                                else n.copy(lines = n.lines.map { l ->
                                    if (l.id == lineId) l.copy(lineCheckedAt = if (currentlyChecked) l.lineCheckedAt else null)
                                    else l
                                })
                            }
                        )
                    }
                }
        }
    }

    /**
     * Ajusta la cantidad empacada de una línea. Si [donorNoteId] viene informado, el
     * aumento se le **resta a esa otra sucursal** en la misma operación.
     *
     * En empaque el pan ya salió del horno: el total del turno es fijo, así que
     * mandarle de más a una sucursal solo puede salir del envío de otra. Antes se
     * podía subir una cantidad sin más y el descuadre aparecía recién cuando la
     * sucursal perjudicada llamaba preguntando por su pan.
     */
    fun updateLinePackedQuantity(
        companyId: String,
        noteId: String,
        lineId: String,
        qty: Double,
        reason: String?,
        donorNoteId: String? = null,
        donorLineId: String? = null
    ) {
        if (donorNoteId != null && donorLineId != null) {
            // Solo se le descuenta a otra sucursal lo que pase de lo que a ésta le
            // tocaba. Recuperar lo que uno mismo bajó hace un momento no es pan de
            // nadie más — misma regla que aplica el diálogo.
            val requested = _packingState.value.notes
                .find { it.id == noteId }?.lines?.find { it.id == lineId }?.requestedQuantity ?: 0.0
            val extra = (qty - requested).coerceAtLeast(0.0)
            val donorLine = _packingState.value.notes
                .find { it.id == donorNoteId }?.lines?.find { it.id == donorLineId }
            if (extra > 0.0 && donorLine != null) {
                val donorNewQty = (donorLine.packedQuantity - extra).coerceAtLeast(0.0)
                // Dice a QUIÉN: con "reasignadas a otra sucursal" la encargada que recibe
                // corto no sabía a dónde fue su pan, y el dueño no podía seguirlo.
                val receiverName = _packingState.value.notes.find { it.id == noteId }?.destinationBranchName.orEmpty()
                val donorReason = listOfNotNull(
                    donorLine.packedReason?.takeIf { it.isNotBlank() },
                    "${PackingReason.givenTo(receiverName)} (${extra.toInt()})"
                ).joinToString(" · ")
                applyPackedQuantity(companyId, donorNoteId, donorLineId, donorNewQty, donorReason)
            }
        }
        applyPackedQuantity(companyId, noteId, lineId, qty, reason)
    }

    private fun applyPackedQuantity(
        companyId: String, noteId: String, lineId: String, qty: Double, reason: String?
    ) {
        viewModelScope.launch {
            val normalizedReason = reason?.takeIf { r -> r.isNotBlank() }
            // Valor anterior, para poder revertir si la escritura falla.
            val previous = _packingState.value.notes
                .find { it.id == noteId }?.lines?.find { it.id == lineId }
                ?.let { it.packedQuantity to it.packedReason }
            pendingPackedQuantities[noteId to lineId] = qty to normalizedReason
            // Update optimista.
            _packingState.update { st ->
                st.copy(notes = st.notes.map { n ->
                    if (n.id != noteId) n
                    else n.copy(lines = n.lines.map { l ->
                        if (l.id == lineId) l.copy(
                            packedQuantity = qty,
                            packedReason = normalizedReason
                        ) else l
                    })
                })
            }
            runCatching { repository.setLinePackedQuantity(companyId, noteId, lineId, qty, reason) }
                .onFailure { err ->
                    pendingPackedQuantities.remove(noteId to lineId)
                    _packingState.update { st ->
                        st.copy(
                            error = err.message,
                            notes = st.notes.map { n ->
                                if (n.id != noteId || previous == null) n
                                else n.copy(lines = n.lines.map { l ->
                                    if (l.id == lineId) l.copy(
                                        packedQuantity = previous.first,
                                        packedReason = previous.second
                                    ) else l
                                })
                            }
                        )
                    }
                }
        }
    }

    /**
     * Cierra el empaque del turno. [baskets] es `noteId → canastas` de cada sucursal que
     * sale: se guarda ANTES de cerrar, así ninguna nota llega a Reparto sin su número de
     * canastas (ver `DispatchNote.basketsSent`).
     */
    fun closePackingTurn(companyId: String, currentUser: User, baskets: Map<String, Int> = emptyMap()) {
        val s = _packingState.value
        if (s.producingBranchId.isEmpty() || s.shiftId.isEmpty()) return
        viewModelScope.launch {
            _packingState.update { it.copy(isClosing = true, error = null, lastCloseResult = null) }
            // Foto de las notas ANTES de cerrar: después pasan a READY y el listener
            // del turno deja de traerlas.
            val notesAtClose = s.notes
            val shiftName = s.shifts.find { it.id == s.shiftId }?.name.orEmpty()
            runCatching {
                baskets.filterValues { it > 0 }.forEach { (noteId, count) ->
                    repository.setBasketsSent(companyId, noteId, count)
                }
                repository.closePackingForBranchDateShift(
                    companyId, s.producingBranchId, s.dayMillis, s.shiftId, currentUser
                )
            }.onSuccess { result ->
                _packingState.update { it.copy(isClosing = false, lastCloseResult = result) }
                // Efectos que no deben tumbar el cierre si fallan: el turno ya cerró.
                runCatching {
                    absorbLeftoverIntoPlant(companyId, notesAtClose, currentUser, shiftName)
                }
                runCatching {
                    notifyQuantityChanges(companyId, notesAtClose, currentUser, shiftName)
                }
            }.onFailure { err ->
                _packingState.update { it.copy(isClosing = false, error = err.message) }
            }
        }
    }

    /**
     * El pan que no se envió queda físicamente en la planta, así que entra a su
     * inventario de terminado como `AJUSTE`. Antes simplemente desaparecía del
     * conteo: se horneaba, no se despachaba y no aparecía en ningún lado.
     *
     * Solo cuenta lo que se dejó de enviar de lo que estaba pedido. El pan que se
     * quemó no se registra acá — ése nunca llegó a existir y se descuenta en el
     * conteo real de producción.
     */
    private suspend fun absorbLeftoverIntoPlant(
        companyId: String,
        notes: List<DispatchNote>,
        currentUser: User,
        shiftName: String
    ) {
        val plantId = _packingState.value.producingBranchId
        val plantName = _packingState.value.producingBranches.find { it.id == plantId }?.name.orEmpty()
        val leftoverByProduct = mutableMapOf<String, Pair<String, Double>>()
        for (note in notes) {
            for (line in note.lines) {
                val leftover = line.requestedQuantity - line.packedQuantity
                if (leftover <= 0.0) continue
                val previous = leftoverByProduct[line.productId]
                leftoverByProduct[line.productId] =
                    line.productName to ((previous?.second ?: 0.0) + leftover)
            }
        }
        leftoverByProduct.forEach { (productId, entry) ->
            val (productName, units) = entry
            runCatching {
                finishedStockRepository.applyMovement(
                    companyId,
                    FinishedStockMovement(
                        productId = productId,
                        productName = productName,
                        branchId = plantId,
                        branchName = plantName,
                        type = FinishedMovementType.AJUSTE,
                        quantity = units,
                        referenceType = FinishedMovementReference.DISPATCH,
                        reason = "No enviado en $shiftName — queda en planta",
                        createdByUserId = currentUser.id,
                        createdByUserName = currentUser.displayName.ifEmpty { currentUser.email },
                        createdAt = Clock.System.now().toEpochMilliseconds()
                    )
                )
            }
        }
    }

    /**
     * Un aviso por sucursal con TODO lo que le cambió respecto de lo que pidió, al
     * cerrar el empaque del turno. Va con las razones que escribió quien empacó, para
     * que la sucursal no tenga que llamar a preguntar por qué le llegó de menos.
     *
     * El destinatario se expresa por sucursal + permiso, así que el dueño —que tiene
     * acceso a todas— lo recibe también sin necesidad de un aviso aparte.
     */
    private suspend fun notifyQuantityChanges(
        companyId: String,
        notes: List<DispatchNote>,
        currentUser: User,
        shiftName: String
    ) {
        for (note in notes) {
            val changed = note.lines.filter { it.packedQuantity != it.requestedQuantity }
            if (changed.isEmpty()) continue
            val detail = changed.joinToString("\n") { line ->
                val diff = line.packedQuantity - line.requestedQuantity
                val sign = if (diff > 0) "+" else "−"
                val reason = line.packedReason?.takeIf { r -> r.isNotBlank() }
                "• ${line.productName}: $sign${kotlin.math.abs(diff).toInt()} " +
                    "(van ${line.packedQuantity.toInt()} de ${line.requestedQuantity.toInt()})" +
                    if (reason != null) " — $reason" else ""
            }
            val anyShort = changed.any { it.packedQuantity < it.requestedQuantity }
            runCatching {
                notificationRepository.push(
                    companyId,
                    AppNotification(
                        type = NotificationType.DISPATCH_QUANTITY_CHANGED,
                        title = if (anyShort) "⚠ Tu envío de $shiftName va incompleto"
                                else "⚠ Tu envío de $shiftName lleva de más",
                        body = detail,
                        targetBranchIds = listOf(note.destinationBranchId),
                        requiredPermission = Permission.RECEIVE_DISPATCH.name,
                        targetScreen = NotificationTarget.DISPATCH_NOTE_DETAIL,
                        targetId = note.id,
                        createdByUserId = currentUser.id,
                        createdByUserName = currentUser.displayName.ifEmpty { currentUser.email },
                        createdAt = Clock.System.now().toEpochMilliseconds()
                    )
                )
            }
        }
    }

    fun clearPackingError() { _packingState.update { it.copy(error = null) } }
    fun clearPackingCloseResult() { _packingState.update { it.copy(lastCloseResult = null) } }

    private suspend fun loadShiftsAndStartListener(companyId: String, branchId: String) {
        runCatching { productionRepository.getShiftsForBranch(companyId, branchId) }
            .onSuccess { shifts ->
                val active = shifts.filter { it.active }
                val current = _packingState.value.shiftId
                val selected = current.takeIf { id -> active.any { it.id == id } }
                    ?: active.firstOrNull()?.id ?: ""
                _packingState.update { it.copy(shifts = active, shiftId = selected) }
                restartPackingListener(companyId)
            }
            .onFailure { err ->
                _packingState.update { it.copy(isLoading = false, error = err.message) }
            }
    }

    private fun restartPackingListener(companyId: String) {
        packingObserveJob?.cancel()
        // Cambió el día, la planta o el turno: lo pendiente era de otras líneas y
        // reaplicarlo sobre las nuevas notas mostraría marcas que no corresponden.
        pendingLineChecks.clear()
        pendingPackedQuantities.clear()
        val s = _packingState.value
        if (companyId.isEmpty() || s.producingBranchId.isEmpty() || s.shiftId.isEmpty()) {
            _packingState.update { it.copy(notes = emptyList(), isLoading = false) }
            return
        }
        _packingState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch { loadShortfallFromEarlierShifts(companyId) }
        // Rellenar las notas que `startLot` no alcanzó a crear. Corre aparte del
        // listener: si falla, la matriz igual muestra lo que sí existe. Lo que no
        // puede hacer es fallar callado, que es lo que dejó a una sucursal entera
        // fuera del despacho sin que nadie se enterara hasta el mediodía.
        packingUser?.let { user ->
            viewModelScope.launch {
                runCatching {
                    repository.ensureNotesForStartedLots(
                        companyId, s.producingBranchId, s.dayMillis, s.shiftId, user
                    )
                }.onSuccess { repaired ->
                    if (repaired.isNotEmpty()) {
                        val names = repaired.map { it.destinationBranchName }.distinct().sorted()
                        _packingState.update {
                            it.copy(
                                error = "Faltaba el listado de ${names.joinToString(", ")} " +
                                    "en este turno. Ya se generó."
                            )
                        }
                    }
                }.onFailure { err ->
                    _packingState.update {
                        it.copy(error = "No se pudo verificar si faltan listados — ${err.message}")
                    }
                }
            }
        }
        packingObserveJob = viewModelScope.launch {
            // El observer del listener vivo trae las notas HEADER. Necesitamos también las
            // líneas, así que las hidratamos por cada cambio — en paralelo, porque
            // marcar un solo pan dispara la recarga de TODAS las notas del turno y en
            // serie eso se sentía como un segundo de espera.
            repository.observePackingViewForBranchDateShift(
                companyId, s.producingBranchId, s.dayMillis, s.shiftId
            ).catch { e -> _packingState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { headers ->
                    val withLines = coroutineScope {
                        headers.map { header ->
                            async {
                                runCatching { repository.getNote(companyId, header.id) ?: header }
                                    .getOrElse { header }
                            }
                        }.awaitAll()
                    }
                    // El repositorio las trae alfabéticas; el orden que manda es el
                    // que configuró la planta (ver `dispatchBranchOrder`). Se aplica
                    // acá y no en cada pantalla para que la matriz de tablet y las
                    // pestañas de teléfono no puedan divergir.
                    val ordered = applyBranchOrder(
                        withLines.withPendingEdits(),
                        _packingState.value.branchOrder,
                        { it.destinationBranchId },
                        { it.destinationBranchName }
                    )
                    _packingState.update { it.copy(notes = ordered, isLoading = false) }
                }
        }
    }

    /**
     * Cuánto le quedó debiendo a cada sucursal un viaje anterior del mismo día.
     *
     * Solo mira los turnos que salen ANTES que el seleccionado (`shifts` viene
     * ordenado por `ProductionShift.dispatchOrder`).
     *
     * Una nota ya cerrada cuenta entera. En una que sigue en empaque cuenta la línea
     * que ya se contó (`lineCheckedAt`) o que se ajustó a propósito (tiene razón
     * escrita): un número bajo en una línea que nadie tocó no es una deuda, es que
     * aún no terminaron de contar. Exigir la nota cerrada dejaba la sugerencia
     * invisible justo cuando los dos turnos se empacan casi a la vez, que es lo
     * normal.
     */
    private suspend fun loadShortfallFromEarlierShifts(companyId: String) {
        val s = _packingState.value
        val currentIndex = s.shifts.indexOfFirst { it.id == s.shiftId }
        if (currentIndex <= 0) {
            _packingState.update { it.copy(shortfallFromEarlierShifts = emptyMap()) }
            return
        }
        val earlierShiftIds = s.shifts.take(currentIndex).map { it.id }.toSet()
        val shiftNameById = s.shifts.associate { it.id to it.name }
        val (from, until) = dayBounds(s.dayMillis)
        val notes = runCatching { repository.getNotesInRange(companyId, from, until) }
            .getOrElse { emptyList() }
            .filter {
                it.originBranchId == s.producingBranchId &&
                    it.shiftId in earlierShiftIds &&
                    it.status != DispatchStatus.CANCELLED
            }

        val result = mutableMapOf<String, ShiftShortfall>()
        for (header in notes) {
            val full = runCatching { repository.getNote(companyId, header.id) }.getOrNull() ?: continue
            val stillPacking = full.status == DispatchStatus.DRAFT
            for (line in full.lines) {
                val deliberate = line.lineCheckedAt != null || !line.packedReason.isNullOrBlank()
                if (stillPacking && !deliberate) continue
                val missing = line.requestedQuantity - line.packedQuantity
                if (missing <= 0.0) continue
                val key = "${full.destinationBranchId}|${line.productId}"
                val previous = result[key]
                result[key] = ShiftShortfall(
                    units = (previous?.units ?: 0.0) + missing,
                    shiftName = shiftNameById[full.shiftId] ?: full.shiftName,
                    reason = line.packedReason?.takeIf { it.isNotBlank() }
                        ?: previous?.reason.orEmpty()
                )
            }
        }
        _packingState.update { it.copy(shortfallFromEarlierShifts = result) }
    }

    private fun dayBounds(anchorMillis: Long): Pair<Long, Long> {
        val tz = TimeZone.currentSystemDefault()
        val anchor = Instant.fromEpochMilliseconds(anchorMillis).toLocalDateTime(tz).date
        val nextDay = anchor.plus(1, DateTimeUnit.DAY)
        val from = LocalDateTime(anchor, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds()
        val until = LocalDateTime(nextDay, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds() - 1
        return from to until
    }
}
