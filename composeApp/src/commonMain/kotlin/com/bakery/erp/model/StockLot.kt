package com.bakery.erp.model

/**
 * Un lote físico de un insumo perecedero en una sucursal: lo que entró en una compra, con su
 * fecha de vencimiento y su saldo propio.
 *
 * ## Por qué existe, y qué NO cambia
 *
 * `BranchStock` sigue siendo la autoridad de "cuánto tengo": los lotes son una capa paralela
 * que se lleva **solo para los insumos marcados como perecederos** ([Ingredient.isPerishable]).
 * La harina y la sal rotan en días y llevarles lotes sería papeleo sin lector; lo que se queda
 * parado y se vence es la levadura, el mejorador, el ponche de huevo y los rellenos comprados.
 *
 * Esa separación es también lo que hace seguro el cambio: la matemática de consumo de
 * producción no se tocó. Si el libro de lotes se despega de la realidad, la producción sigue
 * descontando igual que siempre — se pierde la precisión del aviso de caducidad, no el
 * inventario.
 *
 * ## FEFO automático
 *
 * El panadero no declara de qué lote agarró: al bajar stock se descuenta del que vence
 * primero (ver [allocateFefo]). Se eligió así a sabiendas de que depende de una costumbre de
 * bodega —lo nuevo atrás, lo viejo adelante—, porque la alternativa, pedirle el lote en cada
 * amasada, es un impuesto diario que en una panadería artesanal no devuelve nada. Lo que se
 * desvíe se corrige con el conteo periódico, igual que el resto del inventario.
 */
data class StockLot(
    val id: String = "",
    val ingredientId: String = "",
    val ingredientName: String = "",
    val branchId: String = "",
    val branchName: String = "",
    /** `{branchId}_{ingredientId}` — permite traer los lotes con UNA consulta de igualdad. */
    val branchIngredientKey: String = "",
    /** 0 = el empaque no traía fecha; entonces el lote se ordena por antigüedad (FIFO). */
    val expiryDate: Long = 0L,
    val lotCode: String = "",
    val receivedAt: Long = 0L,
    /** Ambas en la unidad BASE del insumo, la misma en que vive `BranchStock`. */
    val initialQuantity: Double = 0.0,
    val remainingQuantity: Double = 0.0,
    /**
     * Costo por unidad base al recibirlo. Hoy nadie lo lee: queda porque es el dato que
     * convierte esto en costeo real ("este pan se hizo con harina de Q318, no de Q15,900")
     * cuando llegue el módulo de costo por producto, y capturarlo después es imposible.
     */
    val unitCost: Double = 0.0,
    val purchaseId: String = "",
    val status: String = StockLotStatus.ACTIVE
)

object StockLotStatus {
    const val ACTIVE = "ACTIVE"
    const val DEPLETED = "DEPLETED"
}

fun stockLotKey(branchId: String, ingredientId: String) = "${branchId}_$ingredientId"

/** Tolerancia de saldo: 5 g sobrantes en un lote son un lote agotado, no un lote vivo. */
const val STOCK_LOT_EPSILON = 0.005

/**
 * Orden de consumo. El lote que vence primero sale primero; los que no traen fecha van
 * **al final**, ordenados por antigüedad.
 *
 * Que los sin fecha vayan al final es deliberado: si hay un lote que vence la semana entrante
 * y otro sin fecha declarada, gastar primero el que tiene fecha es lo que evita la merma. Si
 * ningún lote trae fecha, la regla degrada sola a FIFO por fecha de recepción.
 */
internal fun StockLot.fefoOrder(): Pair<Long, Long> =
    (if (expiryDate > 0L) expiryDate else Long.MAX_VALUE) to receivedAt

/** Cuánto se toma de un lote concreto. */
data class LotAllocation(
    val lotId: String,
    val quantity: Double,
    val expiryDate: Long,
    val lotCode: String
)

/**
 * Resultado de repartir un consumo entre los lotes disponibles.
 *
 * [shortfall] es lo que se pidió y los lotes no alcanzaron a cubrir. NO es un error: pasa
 * cuando el libro de lotes se despegó de la bodega, o cuando un insumo empezó a llevar lotes
 * después de tener existencias. El consumo real ya ocurrió, así que se registra lo que se
 * pudo y el faltante queda a la vista en vez de reventar la producción.
 */
data class FefoResult(
    val allocations: List<LotAllocation> = emptyList(),
    val shortfall: Double = 0.0
)

/**
 * Reparte [quantity] entre [lots] siguiendo FEFO. Función pura: es el único lugar donde una
 * equivocación no se vería —el número total del inventario saldría igual y solo estaría mal
 * *cuál* lote se gastó—, así que vive acá y no dentro del repositorio.
 */
fun allocateFefo(lots: List<StockLot>, quantity: Double): FefoResult {
    if (quantity <= 0.0) return FefoResult()
    val available = lots
        .filter { it.status == StockLotStatus.ACTIVE && it.remainingQuantity > STOCK_LOT_EPSILON }
        .sortedWith(compareBy({ it.fefoOrder().first }, { it.fefoOrder().second }, { it.id }))

    var pending = quantity
    val allocations = mutableListOf<LotAllocation>()
    for (lot in available) {
        if (pending <= STOCK_LOT_EPSILON) break
        val take = minOf(pending, lot.remainingQuantity)
        if (take <= 0.0) continue
        allocations += LotAllocation(lot.id, take, lot.expiryDate, lot.lotCode)
        pending -= take
    }
    return FefoResult(allocations, if (pending > STOCK_LOT_EPSILON) pending else 0.0)
}

/** Lotes con saldo que caducan antes de [beforeMillis], del más urgente al menos. */
fun List<StockLot>.expiringBefore(beforeMillis: Long): List<StockLot> =
    filter {
        it.status == StockLotStatus.ACTIVE &&
            it.remainingQuantity > STOCK_LOT_EPSILON &&
            it.expiryDate > 0L &&
            it.expiryDate <= beforeMillis
    }.sortedBy { it.expiryDate }
