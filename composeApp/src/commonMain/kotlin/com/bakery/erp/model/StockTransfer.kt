package com.bakery.erp.model

import com.bakery.erp.util.fmtTrim

/**
 * Traslado de inventario entre sucursales (planta incluida): pan, pasteles, porciones, reventa,
 * materia prima y suministros que SÍ se cuentan. Lo que no lleva existencias (cloro sin conteo,
 * conceptos de compra) no se traslada: no hay stock que mover.
 *
 * Dos pasos, como el despacho de planta:
 * 1. **Enviar** — sale del stock del origen y queda [StockTransferStatus.IN_TRANSIT].
 * 2. **Recibir** — el destino cuenta lo que llegó y solo eso entra a su stock. Lo que falte queda
 *    escrito como faltante en el traslado (no se le cobra a nadie: desde la hoja no se sabe si se
 *    perdió en el camino o salió incompleto). Antes de recibirlo, el origen puede cancelarlo y el
 *    stock vuelve.
 *
 * Mientras va en camino no está en ninguna de las dos sucursales: es lo que pasa de verdad, y así
 * el conteo de cierre del origen no se descuadra (el pan ya salió de su stock al enviarlo).
 *
 * **El costo viaja con la mercadería** ([StockTransferLine.unitCost]): el pan sale al costo
 * promedio del origen y se mezcla con el del destino; un insumo perecedero lleva sus lotes
 * ([StockTransferLine.lots]) para que el aviso de caducidad siga funcionando del otro lado.
 *
 * **Pedido de la sucursal** (pan molde, rosca, pan tostado: lo que se hace para existencia y no
 * entra en el pedido de producción). La sucursal que lo necesita lo pide para un día y un viaje
 * ([StockTransferStatus.REQUESTED], sin mover stock); quien lo tiene lo envía con lo que de
 * verdad manda y desde ahí es un traslado como cualquier otro. El pedido viaja en el camión del
 * reparto: sale en el tablero de Reparto y en la hoja del repartidor, en su viaje
 * ([carrierPlantIdOf]: si sale de Calvario, el camión de Bombero pasa a recogerlo).
 */
data class StockTransfer(
    val id: String = "",
    val originBranchId: String = "",
    val originBranchName: String = "",
    val destinationBranchId: String = "",
    val destinationBranchName: String = "",
    val status: String = StockTransferStatus.IN_TRANSIT,
    val lines: List<StockTransferLine> = emptyList(),
    val note: String = "",
    val sentByUserId: String = "",
    val sentByName: String = "",
    val sentByEmployeeId: String = "",
    val sentAt: Long = 0L,
    val receivedByUserId: String = "",
    val receivedByName: String = "",
    val receivedByEmployeeId: String = "",
    val receivedAt: Long = 0L,
    val receiveNote: String = "",
    val cancelledByUserId: String = "",
    val cancelledByName: String = "",
    val cancelledAt: Long = 0L,
    val cancelReason: String = "",
    /** Viaje del camión en que va ([DISPATCH_TRIP_FIRST]/[DISPATCH_TRIP_SECOND]); 0 = no va en el reparto. */
    val trip: Int = 0,
    /** Día en que se necesita (millis al inicio del día). 0 = traslado directo, sin pedido. */
    val forDate: Long = 0L,
    val requestedByUserId: String = "",
    val requestedByName: String = "",
    /** Cuándo lo pidió la sucursal. 0 = no nació de un pedido. */
    val requestedAt: Long = 0L
) {
    /** Número corto para hablar del traslado por teléfono ("el traslado 7KQ2XA"). */
    val folio: String get() = id.takeLast(6).uppercase()
    val isInTransit: Boolean get() = status == StockTransferStatus.IN_TRANSIT
    val isReceived: Boolean get() = status == StockTransferStatus.RECEIVED
    /** Pedido por la sucursal y todavía no enviado: no ha movido stock. */
    val isRequested: Boolean get() = status == StockTransferStatus.REQUESTED
    val wasRequested: Boolean get() = requestedAt > 0L
    /** Pedido o en camino: lo que alguien todavía tiene que hacer. */
    val isOpen: Boolean get() = isRequested || isInTransit
    /** Va en el camión del reparto (tiene día y viaje). */
    val ridesDelivery: Boolean get() = forDate > 0L && (trip == DISPATCH_TRIP_FIRST || trip == DISPATCH_TRIP_SECOND)
    /** Fecha para ordenar y filtrar: el envío, o el pedido si todavía no se envía. */
    val activityAt: Long get() = sentAt.takeIf { it > 0L } ?: requestedAt
    val sentValue: Double get() = lines.sumOf { it.quantity * it.unitCost }
    /** Lo que entró al destino, a costo. Es lo que mueve el reporte por sucursal. */
    val receivedValue: Double get() = if (isReceived) lines.sumOf { (it.receivedQuantity ?: 0.0) * it.unitCost } else 0.0
    val shortages: List<StockTransferLine> get() = if (isReceived) lines.filter { it.shortage > STOCK_LOT_EPSILON } else emptyList()
    val shortageValue: Double get() = shortages.sumOf { it.shortage * it.unitCost }
}

object StockTransferStatus {
    /** Pedido por la sucursal que lo necesita; quien lo tiene todavía no lo envía. */
    const val REQUESTED = "REQUESTED"
    const val IN_TRANSIT = "IN_TRANSIT"
    const val RECEIVED = "RECEIVED"
    const val CANCELLED = "CANCELLED"

    fun label(status: String) = when (status) {
        REQUESTED -> "Pedido"
        IN_TRANSIT -> "En camino"
        RECEIVED -> "Recibido"
        CANCELLED -> "Cancelado"
        else -> status
    }
}

/** Qué inventario mueve la línea: el de tienda (producto) o el de insumos. */
object StockTransferItemKind {
    /** Pan, pastel, porción, reventa: `finishedStocks` + Kardex de terminado. */
    const val PRODUCT = "PRODUCT"
    /** Materia prima o suministro con conteo: `branchStocks` + lotes + Kardex del insumo. */
    const val INGREDIENT = "INGREDIENT"
}

data class StockTransferLine(
    val kind: String = StockTransferItemKind.PRODUCT,
    val itemId: String = "",
    val itemName: String = "",
    /** Unidad del stock: la base del insumo ("lb", "unidad") o la presentación del producto. */
    val unit: String = "",
    /** Cantidad enviada, en la unidad del stock. */
    val quantity: Double = 0.0,
    /** Cómo lo dijo quien envió ("2 Saco 100 lb"), para que quien recibe cuente lo mismo. */
    val displayQuantity: String = "",
    /** Costo por unidad del stock al salir del origen. */
    val unitCost: Double = 0.0,
    /** Lo que contó el destino. null = todavía no se recibe. */
    val receivedQuantity: Double? = null,
    /** Lotes que salieron del origen (solo insumos perecederos), en orden de vencimiento. */
    val lots: List<TransferLot> = emptyList(),
    /** Lo que pidió la sucursal (0 = no nació de un pedido). [quantity] es lo que se mandó. */
    val requestedQuantity: Double = 0.0
) {
    val shortage: Double get() = receivedQuantity?.let { (quantity - it).coerceAtLeast(0.0) } ?: 0.0
    val surplus: Double get() = receivedQuantity?.let { (it - quantity).coerceAtLeast(0.0) } ?: 0.0
}

/** Pedazo de un lote del origen que viajó en el traslado. */
data class TransferLot(
    val expiryDate: Long = 0L,
    val lotCode: String = "",
    val quantity: Double = 0.0,
    val unitCost: Double = 0.0
)

/** Insumos que se pueden trasladar: los que llevan existencias. */
fun Ingredient.isTransferable(): Boolean =
    active && (usage == IngredientUsage.PRODUCTION || tracksStock)

/** Motivo por el que no se puede enviar, o null. */
fun stockTransferSendError(transfer: StockTransfer): String? = when {
    transfer.originBranchId.isEmpty() -> "Elige de qué sucursal sale"
    transfer.destinationBranchId.isEmpty() -> "Elige a qué sucursal va"
    transfer.originBranchId == transfer.destinationBranchId -> "El origen y el destino son la misma sucursal"
    transfer.lines.none { it.quantity > 0.0 } -> "Agrega al menos un producto o insumo"
    transfer.lines.any { it.quantity < 0.0 } -> "Hay una cantidad negativa"
    transfer.lines.groupBy { it.kind to it.itemId }.any { it.value.size > 1 } ->
        "Un mismo producto está dos veces: júntalo en una línea"
    else -> null
}

/** Motivo por el que no se puede pedir, o null. */
fun stockTransferRequestError(transfer: StockTransfer): String? = when {
    transfer.destinationBranchId.isEmpty() -> "Elige para qué sucursal es"
    transfer.originBranchId.isEmpty() -> "Elige a qué sucursal se lo pides"
    transfer.originBranchId == transfer.destinationBranchId -> "No te lo puedes pedir a ti misma"
    !transfer.ridesDelivery -> "Elige el día y el viaje del camión"
    transfer.lines.none { it.requestedQuantity > 0.0 } -> "Agrega al menos un producto"
    transfer.lines.any { it.requestedQuantity < 0.0 } -> "Hay una cantidad negativa"
    transfer.lines.groupBy { it.kind to it.itemId }.any { it.value.size > 1 } ->
        "Un mismo producto está dos veces: júntalo en una línea"
    else -> null
}

/**
 * El pedido listo para enviar con [sent] (una cantidad por línea, lo que de verdad se manda).
 * Se quedan las líneas pedidas aunque se manden en 0: así la sucursal ve qué no le llegó.
 */
fun StockTransfer.sendingRequest(sent: List<Double>): StockTransfer =
    copy(lines = lines.mapIndexed { i, l -> l.copy(quantity = sent.getOrElse(i) { 0.0 }.coerceAtLeast(0.0)) })

/** Líneas pedidas que salieron con menos de lo pedido. */
val StockTransfer.shortOfRequest: List<StockTransferLine>
    get() = if (!wasRequested || isRequested) emptyList()
    else lines.filter { it.requestedQuantity > it.quantity + STOCK_LOT_EPSILON }

/**
 * Planta cuyo camión lleva el pedido. Si el origen tiene ruta propia, es él (Bombero). Si no
 * (Calvario guarda repostería pero no tiene camión), la planta que lo tiene en su ruta pasa a
 * recogerlo; si nadie lo tiene en ruta, la que tiene en ruta al destino. Sin nada de eso, el
 * mismo origen.
 */
fun carrierPlantIdOf(transfer: StockTransfer, branches: Collection<Branch>): String {
    val origin = branches.firstOrNull { it.id == transfer.originBranchId }
    if (origin != null && origin.deliveryRouteOrder.isNotEmpty()) return origin.id
    val withRoute = branches.filter { it.deliveryRouteOrder.isNotEmpty() }.sortedBy { it.name.lowercase() }
    return withRoute.firstOrNull { transfer.originBranchId in it.deliveryRouteOrder }?.id
        ?: withRoute.firstOrNull { transfer.destinationBranchId in it.deliveryRouteOrder }?.id
        ?: transfer.originBranchId
}

/** El camión de [carrierPlantId] tiene que pasar a recogerlo al origen (no sale de la planta). */
fun StockTransfer.isPickupFor(carrierPlantId: String): Boolean = originBranchId != carrierPlantId

/** Pedidos y traslados que van en el camión de [carrierPlantId] en el viaje [trip] del día. */
fun transfersForTrip(
    transfers: List<StockTransfer>,
    branches: Collection<Branch>,
    carrierPlantId: String,
    trip: Int,
    dayStart: Long,
    dayEnd: Long
): List<StockTransfer> =
    transfers.filter {
        it.ridesDelivery && it.trip == trip && it.forDate in dayStart until dayEnd &&
            it.status != StockTransferStatus.CANCELLED && carrierPlantIdOf(it, branches) == carrierPlantId
    }

/** Motivo por el que no se puede recibir con [received] (una cantidad por línea), o null. */
fun stockTransferReceiveError(transfer: StockTransfer, received: List<Double>): String? = when {
    !transfer.isInTransit -> "Este traslado ya está ${StockTransferStatus.label(transfer.status).lowercase()}"
    received.size != transfer.lines.size -> "Falta contar alguna línea"
    received.any { it < 0.0 } -> "Hay una cantidad negativa"
    else -> null
}

/**
 * Lotes que se crean en el destino cuando llegó [received] de una línea que traía [lots].
 *
 * Los lotes vienen en orden de vencimiento; si llegó menos, se recortan los ÚLTIMOS (los que
 * vencen más tarde), igual que FEFO habría descontado primero los viejos. Lo que llegó de más
 * —o lo que salió del origen sin lote— no se inventa como lote: lo cubre [updateBranchStock].
 */
fun lotsForReceived(lots: List<TransferLot>, received: Double): List<TransferLot> {
    var pending = received
    val out = mutableListOf<TransferLot>()
    for (lot in lots) {
        if (pending <= STOCK_LOT_EPSILON) break
        val take = minOf(lot.quantity, pending)
        if (take > STOCK_LOT_EPSILON) out += lot.copy(quantity = take)
        pending -= take
    }
    return out
}

/**
 * Lotes que salen al enviar [quantity] desde [lots] del origen, con el mismo reparto FEFO que
 * aplica `IngredientRepository.updateBranchStock` al descontar.
 */
fun transferLotsFrom(lots: List<StockLot>, quantity: Double): List<TransferLot> {
    val byId = lots.associateBy { it.id }
    return allocateFefo(lots, quantity).allocations.mapNotNull { a ->
        val lot = byId[a.lotId] ?: return@mapNotNull null
        TransferLot(lot.expiryDate, lot.lotCode, a.quantity, lot.unitCost)
    }
}

/**
 * Costo de un insumo por unidad de SU STOCK, desde la presentación predeterminada. No es
 * `defaultUnitCost`: ése va por la unidad de la presentación (un saco declarado en "lb" sobre un
 * insumo que se guarda en "gr"). Sin presentación o sin conversión, 0: el traslado se hace igual y
 * solo queda sin valor.
 */
fun Ingredient.costPerStockUnit(): Double {
    val fmt = purchaseFormats.firstOrNull { it.isDefault } ?: purchaseFormats.firstOrNull() ?: return 0.0
    if (fmt.packageCost <= 0.0) return 0.0
    val perPackage = packagesToBaseUnitOrNull(1.0, fmt, this) ?: return 0.0
    return if (perPackage > 0.0) fmt.packageCost / perPackage else 0.0
}

/**
 * Lo que un traslado RECIBIDO le mueve a cada sucursal en el reporte, a costo y por lo que llegó:
 * el destino suma el costo y el origen lo descuenta. El faltante lo absorbe el origen (se le
 * acredita solo lo que llegó), así las dos puntas se cancelan y la suma de la empresa no cambia.
 */
data class TransferEffect(
    val branchId: String,
    val kind: String,
    /** Positivo = costo que entró a la sucursal; negativo = costo que salió. */
    val value: Double
)

fun stockTransferEffects(transfers: List<StockTransfer>): List<TransferEffect> =
    transfers.filter { it.isReceived }.flatMap { t ->
        t.lines.flatMap { l ->
            val v = (l.receivedQuantity ?: 0.0) * l.unitCost
            if (v == 0.0) emptyList()
            else listOf(
                TransferEffect(t.destinationBranchId, l.kind, v),
                TransferEffect(t.originBranchId, l.kind, -v)
            )
        }
    }

/** Tipo del movimiento en el Kardex de un insumo trasladado (sale − en el origen, entra + en el destino). */
const val STOCK_TRANSFER_MOVEMENT_TYPE = "TRASLADO"

/** "2 Saco 100 lb" o "12 unidad": cómo se muestra lo enviado. */
fun StockTransferLine.quantityLabel(): String =
    displayQuantity.ifBlank { "${quantity.fmtTrim()} $unit".trim() }

/** "10 Pan molde": lo pedido si todavía no se envía, lo enviado si ya salió. */
fun StockTransfer.linesSummary(): String = lines.joinToString {
    if (isRequested) "${it.requestedQuantity.fmtTrim()} ${it.itemName}" else "${it.quantityLabel()} ${it.itemName}"
}
