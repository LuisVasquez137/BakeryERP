package com.bakery.erp.domain

import com.bakery.erp.domain.report.DispatchSummary
import com.bakery.erp.model.DispatchNote
import com.bakery.erp.model.DispatchNoteLine
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.User
import kotlinx.coroutines.flow.Flow

interface DispatchRepository {

    suspend fun getNote(companyId: String, noteId: String): DispatchNote?

    /** Lista (todas las notas + sus líneas) por rango de fechas (`productionDate`). */
    suspend fun getNotesInRange(companyId: String, fromMillis: Long, untilMillis: Long): List<DispatchNote>

    /** Listener en vivo: notas con `productionDate` dentro de la ventana. Las líneas
     *  se cargan al abrir el detalle, no aquí. */
    fun observeNotes(companyId: String, fromMillis: Long, untilMillis: Long): Flow<List<DispatchNote>>

    /**
     * Hook llamado tras `completeLot`/`startLot`. Crea una `DispatchNote` DRAFT por cada `sourceOrderId`.
     * Cada línea se rellena con la cantidad que le corresponde a esa sucursal EN ESTE LOTE —
     * normalmente `requestedQuantity` (del pedido fuente) completo, salvo que [lines] traiga
     * `ProductionLotLine.sourceOrderAllocations` (lotes de reparto por turnos, donde dos lotes
     * distintos comparten `sourceOrderIds` y cada uno solo produjo una porción del pedido de cada
     * sucursal) — en ese caso se usa la porción de esa orden en esa línea. `packedQuantity` se
     * prellena igual a esa cantidad y `unitWholesalePrice` se resuelve vía
     * [PriceListRepository.resolveWholesalePrice].
     *
     * Idempotente por `(lotId, sourceOrderId)`: si ya existe una nota para ese par, no se duplica.
     */
    suspend fun createDispatchNotesForLot(
        companyId: String,
        lot: ProductionLot,
        lines: List<ProductionLotLine>,
        currentUser: User
    ): List<DispatchNote>

    /** Guarda las cantidades empacadas de una nota. No cambia el status (lo hace markReady). */
    suspend fun savePackedLines(
        companyId: String,
        noteId: String,
        lines: List<DispatchNoteLine>,
        currentUser: User
    )

    /**
     * Marca una línea como contada (o desmarcada) desde la matriz de empaque. Update puntual
     * de `lineCheckedAt`. `checkedAtMillis = null` desmarca la línea. Sólo aplica a notas DRAFT.
     */
    suspend fun setLineChecked(
        companyId: String,
        noteId: String,
        lineId: String,
        checkedAtMillis: Long?
    )

    /**
     * Actualiza la cantidad empacada (y razón) de una sola línea desde la matriz de empaque.
     * Si `qty != requestedQuantity`, `reason` debería venir no-vacío (el caller lo valida).
     * Sólo aplica a notas DRAFT.
     */
    suspend fun setLinePackedQuantity(
        companyId: String,
        noteId: String,
        lineId: String,
        qty: Double,
        reason: String?
    )

    /**
     * Crea las notas que falten para los lotes ya iniciados de `(planta, día, turno)`.
     *
     * Existe porque las notas nacen en `startLot`, que las escribe **una por pedido fuente y
     * en serie**: si ese recorrido muere a media pasada —la tablet se durmió, se cerró la app,
     * se cayó la red— las sucursales que quedaban del arreglo se quedan sin nota y nada
     * reintenta hasta `completeLot`, que es justo lo que el empaque no puede esperar (la
     * encargada cuenta mientras los panaderos siguen produciendo). El fallo además es
     * invisible: el pedido está completo y el lote trae las cantidades, pero la sucursal
     * simplemente no aparece en la matriz.
     *
     * Se llama al abrir el empaque, que es donde el hueco se notaría. Apoyado en la
     * idempotencia por `(lotId, sourceOrderId)` de [createDispatchNotesForLot]: si no falta
     * nada, no escribe. Devuelve las notas creadas (vacío en el caso normal).
     */
    suspend fun ensureNotesForStartedLots(
        companyId: String,
        producingBranchId: String,
        dayMillis: Long,
        shiftId: String,
        currentUser: User
    ): List<DispatchNote>

    /**
     * Listener en vivo de las notas DRAFT de una planta productora para un día calendario y
     * turno específicos. Sirve a la matriz de empaque, donde una sola vista combina todas las
     * sucursales destino de ese turno.
     */
    fun observePackingViewForBranchDateShift(
        companyId: String,
        producingBranchId: String,
        dayMillis: Long,
        shiftId: String
    ): Flow<List<DispatchNote>>

    /**
     * Cierra el empaque del turno: itera todas las notas DRAFT de (planta, día, turno) y para
     * las que tengan TODAS sus líneas con `lineCheckedAt != null` (y sin líneas sin precio con
     * qty>0) llama internamente a `markReady`. Las notas con líneas pendientes quedan en DRAFT.
     */
    suspend fun closePackingForBranchDateShift(
        companyId: String,
        producingBranchId: String,
        dayMillis: Long,
        shiftId: String,
        currentUser: User
    ): PackingCloseResult

    /** Cierra el empaque: status DRAFT → READY. Calcula `totalValueWholesale` y `hasPriceMissing`. */
    suspend fun markReady(companyId: String, noteId: String, currentUser: User)

    /**
     * Cierre del empaque de una nota, con la regla de "pan que no viaja" adentro:
     * DRAFT → READY y, **cuando la planta es su propia sucursal de destino**, sigue
     * de largo hasta DELIVERED con [markAsLocalDispatch].
     *
     * La planta también vende en mostrador, así que su propia nota nunca se sube a
     * un camión: dejarla en "Listas" esperando un reparto que no existe la dejaba
     * estancada ahí para siempre, y el pan que ya estaba en la tienda no entraba a
     * su inventario. Quien empaca cuenta una sola vez, y ese conteo es también la
     * recepción — no hay transporte de por medio que pueda cambiar la cantidad.
     *
     * Es el único lugar donde vive esta decisión: la usan tanto el cierre del turno
     * completo ([closePackingForBranchDateShift]) como el de una nota suelta.
     *
     * @return true si quedó entregada en mostrador (no solo lista).
     */
    suspend fun closeNoteAfterPacking(
        companyId: String,
        noteId: String,
        currentUser: User
    ): Boolean

    /** Marca como en tránsito. Si `deliveryRunId` se provee, se guarda en la nota; útil al
     *  asignar la nota a una `DeliveryRun`. */
    suspend fun markInTransit(
        companyId: String,
        noteId: String,
        currentUser: User,
        deliveryRunId: String = ""
    )

    /** Notas en estado READY (o IN_TRANSIT del mismo run, para edición) que pertenecen a una
     *  planta productora en un día calendario. Usado por el form de DeliveryRun. */
    suspend fun getNotesForRunForm(
        companyId: String,
        originBranchId: String,
        dayMillis: Long,
        currentRunId: String = ""
    ): List<DispatchNote>

    /** Reverso de markInTransit: vuelve la nota a READY y limpia `deliveryRunId/dispatchedAt`.
     *  Usado cuando se desasigna una nota de una hoja de reparto. */
    suspend fun unassignFromRun(companyId: String, noteId: String)

    /**
     * Lleva a la nota DRAFT el ajuste que la planta le hizo al pedido de su sucursal
     * con el lote ya armado (ver `model/OrderAdjustment.kt`): fija lo pedido de
     * [orderLine] en [newRequested]. Crea la línea con su precio si la nota no la
     * tenía, la borra si queda en cero, y borra la nota entera si se queda sin
     * líneas. Falla sin escribir si la nota ya no es DRAFT o si esa línea ya se
     * marcó como empacada.
     */
    suspend fun adjustNoteLineForOrder(
        companyId: String,
        noteId: String,
        orderLine: com.bakery.erp.model.ProductionOrderLine,
        newRequested: Double
    )

    /**
     * Lo que suele tardar cada tramo de [originBranchId] (`destinationBranchId → stat`),
     * de `companies/{c}/deliveryLegStats/{origin}_{destination}`. Se actualiza solo al
     * firmar cada llegada; ver `model/DeliveryBoard.kt`.
     */
    suspend fun getDeliveryLegStats(
        companyId: String,
        originBranchId: String
    ): Map<String, com.bakery.erp.model.DeliveryLegStat>

    /** Encabezados de las notas creadas para ese pedido (una por lote/turno que lo produjo). */
    suspend fun getNotesForOrder(companyId: String, orderId: String): List<DispatchNote>

    /**
     * Borrado real de la nota y su subcolección `lines/`. Solo para el borrado de
     * pedidos de prueba del OWNER: una nota que ya salió a reparto o se recibió
     * documenta un hecho físico y se cancela, no se borra (ver [cancelNote]).
     */
    suspend fun deleteNote(companyId: String, noteId: String)

    /** Canastas que salen para la sucursal de esta nota. Escritura parcial, al cerrar el empaque. */
    suspend fun setBasketsSent(companyId: String, noteId: String, count: Int)

    /** Reporta la llegada del repartidor sin contar cantidades. Pasa de IN_TRANSIT → ARRIVED
     *  con timestamp `arrivedAt`. Persiste el checklist de llegada (lineArrivedConfirmedAt
     *  por producto) que la encargada marcó visualmente al recibir al repartidor. El conteo
     *  final con cantidades se cierra después con `confirmReception`. */
    suspend fun acknowledgeArrival(
        companyId: String,
        noteId: String,
        lines: List<DispatchNoteLine>,
        currentUser: User,
        /** Canastas que llegaron, contadas al firmar. null = no se preguntó (nota sin canastas). */
        basketsReceived: Int? = null
    )

    /** Confirma recepción: actualiza líneas con receivedQty + reason, ajusta status, dispara
     *  movimientos de stock terminado a través del [FinishedStockRepository] inyectado.
     *  Acepta IN_TRANSIT o ARRIVED como estado de entrada. */
    suspend fun confirmReception(
        companyId: String,
        noteId: String,
        lines: List<DispatchNoteLine>,
        currentUser: User
    )

    /**
     * Cancela una nota DRAFT o READY sin borrarla: queda en `CANCELLED`, visible en su
     * pestaña y recuperable con [restoreNote].
     *
     * **El pan cancelado entra al inventario de terminado de la planta** (`AJUSTE` por
     * `packedQuantity`): ese pan existe, está horneado y se queda físicamente ahí. Es
     * el mismo criterio de `absorbLeftoverIntoPlant` al cerrar el empaque, y no hay
     * doble conteo con él: aquél ingresa lo NO empacado (`requested − packed`) y éste
     * lo empacado.
     */
    suspend fun cancelNote(
        companyId: String,
        noteId: String,
        currentUser: User,
        reason: String? = null
    )

    /**
     * Reverso exacto de [cancelNote]: devuelve la nota al estado que tenía antes de
     * cancelarse y **descuenta de la planta** el pan que la cancelación le sumó.
     *
     * Existe porque cancelar es un botón a un toque y equivocarse es normal; sin
     * reverso, la única salida era rehacer el pedido y el lote. Solo descuenta si la
     * cancelación realmente ingresó stock (`plantAbsorbedAtCancel`).
     */
    suspend fun restoreNote(companyId: String, noteId: String, currentUser: User)

    /**
     * Atajo cuando la planta productora es también la sucursal solicitante: la nota pasa de
     * READY directamente a DELIVERED en un solo paso (no hay reparto ni recepción remota).
     * Suma packedQuantity al FinishedProductStock de la sucursal y registra el Kardex RECEPCION.
     * Valida que `originBranchId == destinationBranchId` y que no haya líneas sin precio.
     */
    suspend fun markAsLocalDispatch(
        companyId: String,
        noteId: String,
        currentUser: User
    )

    /**
     * Agrega despachos en rango: cuenta por estado, mide pérdidas en tránsito
     * (packedQuantity − receivedQuantity para líneas con discrepancia SHORT/DAMAGED),
     * agrupa por sucursal destino y por tipo de discrepancia.
     */
    suspend fun aggregateDispatchInRange(
        companyId: String,
        fromMillis: Long,
        untilMillis: Long
    ): DispatchSummary
}

/**
 * Resultado de cerrar el empaque de un turno: cuántas notas pasaron a READY y cuáles
 * quedaron bloqueadas (no estaban totalmente contadas o tenían precios faltantes).
 */
data class PackingCloseResult(
    val promoted: Int = 0,
    val pendingChecks: Int = 0,
    val pendingPrices: Int = 0,
    /**
     * De las [promoted], cuántas eran de la propia planta y quedaron entregadas en
     * mostrador (ver [DispatchRepository.closeNoteAfterPacking]). Se cuenta aparte
     * porque no aparecen en "Listas": quien cerró el turno tiene que poder ver que
     * ese pan ya entró al inventario de la tienda y no está esperando reparto.
     */
    val deliveredLocally: Int = 0
)
