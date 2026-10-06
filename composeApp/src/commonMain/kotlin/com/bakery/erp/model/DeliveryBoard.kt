package com.bakery.erp.model

import kotlin.math.roundToInt

/**
 * Tablero de Reparto: cada nota del día como una tarjeta de sucursal, agrupada por
 * viaje, con la etapa en que va y si "te toca" a vos.
 *
 * Reemplaza las pestañas de Despacho (Listas / En tránsito / Pendiente conteo /
 * Recibidas): la pregunta de quien abre la pantalla no es "¿qué notas están en tal
 * estado?" sino "¿cómo va mi pan?" (sucursal) o "¿qué falta sacar?" (planta). Despacho
 * queda solo para empacar.
 *
 * Lógica pura, con tests en `DeliveryBoardTest`.
 */
enum class BoardStage { PACKING, READY, IN_TRANSIT, TO_COUNT, RECEIVED, CANCELLED }

fun DispatchNote.boardStage(): BoardStage = when (status) {
    DispatchStatus.DRAFT -> BoardStage.PACKING
    DispatchStatus.READY -> BoardStage.READY
    DispatchStatus.IN_TRANSIT -> BoardStage.IN_TRANSIT
    DispatchStatus.ARRIVED -> BoardStage.TO_COUNT
    DispatchStatus.CANCELLED -> BoardStage.CANCELLED
    else -> BoardStage.RECEIVED
}

/**
 * Quién mira el tablero. El dueño lo ve en modo supervisión: tiene todos los permisos,
 * y si le latiera todo lo que puede hacer, le latiría el tablero entero — y entonces
 * no le serviría para ver qué está atrasado.
 */
data class BoardViewer(
    val supervision: Boolean,
    val canManageRuns: Boolean,
    val canReceiveAt: (branchId: String) -> Boolean
)

fun BoardViewer.isMyTurn(note: DispatchNote): Boolean {
    if (supervision) return false
    return when (note.boardStage()) {
        // A la planta le toca lo empacado que todavía no tiene camión. La nota de la
        // propia planta no viaja: se cierra sola al empacar (`closeNoteAfterPacking`).
        BoardStage.READY -> canManageRuns && note.originBranchId != note.destinationBranchId
        BoardStage.TO_COUNT -> canReceiveAt(note.destinationBranchId)
        else -> false
    }
}

/** Orden dentro de una sección: lo que te toca primero, después por etapa. */
private val STAGE_ORDER = mapOf(
    BoardStage.TO_COUNT to 0, BoardStage.READY to 1, BoardStage.IN_TRANSIT to 2,
    BoardStage.PACKING to 3, BoardStage.RECEIVED to 4, BoardStage.CANCELLED to 5
)

fun sortForBoard(notes: List<DispatchNote>, viewer: BoardViewer): List<DispatchNote> =
    notes.sortedWith(
        compareByDescending<DispatchNote> { viewer.isMyTurn(it) }
            .thenBy { STAGE_ORDER[it.boardStage()] ?: 9 }
            .thenBy { it.destinationBranchName.lowercase() }
    )

/**
 * Viaje en que de verdad sale la nota. Normalmente el del turno que la horneó
 * ([ProductionShift.dispatchOrder]); pero si la sucursal solo recibe en el otro viaje
 * ([Branch.receivesOnlyInTrip]), el pan se guarda y sale con ése — el caso de Pacajá con
 * el pan que solo se hace de noche. Mostrarlo en el viaje en que se horneó haría que la
 * sucursal lo esperara en un camión que no lo trae.
 *
 * 0 = sin viaje (planta de un solo turno, o turno sin `dispatchOrder` configurado).
 */
fun tripOfNote(shiftTrip: Int, destinationOnlyTrip: Int): Int =
    if (destinationOnlyTrip == DISPATCH_TRIP_FIRST || destinationOnlyTrip == DISPATCH_TRIP_SECOND) destinationOnlyTrip
    else shiftTrip

fun tripTitle(trip: Int): String = when (trip) {
    DISPATCH_TRIP_FIRST -> "1er viaje"
    DISPATCH_TRIP_SECOND -> "2º viaje"
    else -> "Viaje del día"
}

// ── Avance de empaque ───────────────────────────────────────────────────────

/**
 * Fracción empacada de una nota, por panes. Notas anteriores al contador
 * (`plannedUnits = 0`) devuelven null y la tarjeta muestra la barra vacía.
 */
fun DispatchNote.packedFraction(): Double? =
    if (plannedUnits <= 0.0) null else (packedUnits / plannedUnits).coerceIn(0.0, 1.0)

// ── Ruta fija y estimado de llegada ─────────────────────────────────────────

/**
 * Ordena las paradas según la ruta fija de la planta ([Branch.deliveryRouteOrder]).
 * Las sucursales que no están en la ruta van al final, alfabéticas: una sucursal nueva
 * no puede quedarse fuera de la hoja por no estar configurada.
 */
fun orderStopsByRoute(notes: List<DispatchNote>, routeOrder: List<String>): List<DispatchNote> {
    val index = routeOrder.withIndex().associate { (i, id) -> id to i }
    return notes.sortedWith(
        compareBy<DispatchNote> { index[it.destinationBranchId] ?: Int.MAX_VALUE }
            .thenBy { it.destinationBranchName.lowercase() }
    )
}

/**
 * Lo que suele tardar el camión desde que sale de [originBranchId] hasta que firman la
 * llegada en [destinationBranchId]. Se actualiza en cada llegada con un promedio que
 * pesa más lo reciente (ver [updatedLegMinutes]).
 */
data class DeliveryLegStat(
    val originBranchId: String,
    val destinationBranchId: String,
    val avgMinutes: Double,
    val samples: Int
)

/** Muestras mínimas para mostrar un estimado en minutos. Con menos, se dice "salió hace X". */
const val MIN_LEG_SAMPLES = 3

/** Una llegada de más de 4 horas no es un viaje: es una firma olvidada. No entra al promedio. */
private const val MAX_LEG_MINUTES = 240.0

/** Por parada cuando no hay historial: solo sirve para que el camión no quede quieto. */
const val DEFAULT_LEG_MINUTES = 15.0

/**
 * Promedio nuevo con la llegada de [sampleMinutes]. Pesa 25% la última: un cambio de
 * ruta o de tráfico se nota en cuatro o cinco viajes, y un día raro no lo descompone.
 * Devuelve null si la muestra no sirve (negativa o absurda).
 */
fun updatedLegMinutes(current: DeliveryLegStat?, sampleMinutes: Double): Pair<Double, Int>? {
    if (sampleMinutes < 1.0 || sampleMinutes > MAX_LEG_MINUTES) return null
    if (current == null || current.samples == 0) return sampleMinutes to 1
    return (current.avgMinutes * 0.75 + sampleMinutes * 0.25) to (current.samples + 1)
}

/**
 * Dónde va el camión para una parada.
 *
 * @property fraction avance hacia ESTA parada (0 = recién salió, 1 = llegando). Nunca
 *   llega a 1 solo: el camión llega cuando la sucursal firma, no cuando el reloj dice.
 * @property minutesLeft minutos que faltan según lo que suele tardar; null si todavía no
 *   hay historial suficiente para afirmarlo.
 */
data class StopProgress(
    val stopNumber: Int,
    val stopCount: Int,
    val elapsedMinutes: Int,
    val fraction: Double,
    val minutesLeft: Int?
)

fun stopProgress(
    stopIndex: Int,
    stopCount: Int,
    dispatchedAt: Long,
    nowMillis: Long,
    leg: DeliveryLegStat?
): StopProgress {
    val elapsed = ((nowMillis - dispatchedAt).coerceAtLeast(0L) / 60_000.0)
    val reliable = leg != null && leg.samples >= MIN_LEG_SAMPLES
    val expected = if (reliable) leg!!.avgMinutes else DEFAULT_LEG_MINUTES * (stopIndex + 1)
    val fraction = if (expected <= 0.0) 0.97 else (elapsed / expected).coerceIn(0.0, 0.97)
    return StopProgress(
        stopNumber = stopIndex + 1,
        stopCount = stopCount,
        elapsedMinutes = elapsed.roundToInt(),
        fraction = fraction,
        minutesLeft = if (reliable) (expected - elapsed).roundToInt().coerceAtLeast(0) else null
    )
}

/** Texto bajo la carretera: lo que se sabe, sin inventar. */
fun StopProgress.etaLabel(): String {
    val order = "parada $stopNumber de $stopCount"
    return when {
        minutesLeft == null -> "Salió hace $elapsedMinutes min · $order"
        minutesLeft <= 1 -> "Está por llegar · $order"
        else -> "Llegaría en unos $minutesLeft min · $order"
    }
}
