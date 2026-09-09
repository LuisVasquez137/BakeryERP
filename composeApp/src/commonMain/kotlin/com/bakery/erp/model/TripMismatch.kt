package com.bakery.erp.model

/**
 * Avisos de "esta nota es de un viaje que su sucursal no recibe".
 *
 * El caso que lo motiva: hay panes que un solo turno hornea
 * ([ProductionShift.excludedProductIds] los excluye del otro), y una sucursal que
 * solo recibe en uno de los dos viajes ([Branch.receivesOnlyInTrip]). Cuando el
 * pan sale del turno equivocado no hay reparto que cumpla las dos cosas: manda la
 * producción —el pan se hornea cuando se puede hornear— y la nota queda emitida
 * para un viaje en el que esa sucursal no recibe.
 *
 * Eso es correcto y está previsto (ver `tripRestrictionConflicts`, que lo avisa al
 * repartir). Lo que faltaba es la otra mitad: esa nota chiquita queda esperando en
 * el empaque del turno que no le toca, y el que arma el camión no tiene por qué
 * acordarse de que hay que arrastrarla al otro viaje. El flujo ya lo permite
 * —`getNotesForRunForm` ofrece las notas del día sin filtrar por turno, y en la
 * práctica ya se hizo así— pero dependía de la memoria de una persona.
 *
 * Nada de esto bloquea: el pan se hornea igual y se puede despachar igual. Solo
 * pone la etiqueta donde se decide qué sube al camión.
 */

/**
 * Motivo por el que una nota está desalineada con el viaje de su sucursal.
 *
 * @param branchName sucursal destino, para el texto.
 * @param restrictedTrip viaje único que recibe ([DISPATCH_TRIP_FIRST] / [DISPATCH_TRIP_SECOND]).
 * @param noteTripOrder viaje al que pertenece el turno de la nota
 *        ([ProductionShift.dispatchOrder]).
 */
data class TripMismatch(
    val branchName: String,
    val restrictedTrip: Int,
    val noteTripOrder: Int
)

/** Nombre del viaje tal como lo dice la gente de la planta. */
fun tripLabel(trip: Int): String = when (trip) {
    DISPATCH_TRIP_FIRST -> "primer viaje"
    DISPATCH_TRIP_SECOND -> "segundo viaje"
    else -> "los dos viajes"
}

/**
 * Devuelve el desajuste si la nota pertenece a un viaje que su sucursal no recibe,
 * o null si todo está en orden.
 *
 * Devuelve null cuando falta el dato en vez de adivinar: una sucursal sin
 * restricción ([DISPATCH_TRIP_BOTH]) recibe en los dos, y un turno sin
 * `dispatchOrder` configurado (`0`) no dice a qué viaje pertenece, así que no hay
 * nada que afirmar. Un aviso inventado sobre datos incompletos se aprende a
 * ignorar, y entonces tampoco sirve para el caso real.
 */
fun tripMismatchFor(
    branchName: String,
    restrictedTrip: Int,
    noteTripOrder: Int
): TripMismatch? {
    if (restrictedTrip == DISPATCH_TRIP_BOTH) return null
    if (noteTripOrder == 0) return null
    if (noteTripOrder == restrictedTrip) return null
    return TripMismatch(branchName, restrictedTrip, noteTripOrder)
}

/**
 * Texto para quien arma la hoja de reparto. Sirve en los dos sentidos: al del viaje
 * que no le toca le dice que no la suba, y al del viaje correcto le dice que ésta
 * también va.
 */
fun TripMismatch.runFormLabel(): String =
    "$branchName solo recibe en el ${tripLabel(restrictedTrip)} — va en ese, no en éste"

/** Texto para el empaque: el pan se cuenta ahora pero viaja después. */
fun TripMismatch.packingLabel(): String =
    "Guardar para el ${tripLabel(restrictedTrip)} — $branchName no recibe en éste"
