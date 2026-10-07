package com.bakery.erp.model

/**
 * La cadena de un pan, de lo que pidió la sucursal a lo que contó: dónde empezó a faltar
 * (o a sobrar) y quién estaba en ese paso. Es lo que el dueño ve al tocar una entrega en
 * el tablero de Reparto.
 *
 * Antes los motivos se escribían a mano y no se podían sumar: en dos semanas de datos
 * reales el empaque decía "no se iso", "nose iso", "f" y "no salio" para lo mismo. Por eso
 * los motivos son ahora de lista fija ([PackingReason], [ReceptionReason]) con "Otro" +
 * detalle para lo raro, y se guardan como texto legible ("No alcanzó la producción", o
 * "Otro: se cayó la bandeja") para que las notas viejas y las nuevas se lean igual.
 *
 * Lógica pura, con tests en `UnitChainTest`.
 */
object PackingReason {
    const val NOT_PRODUCED = "No alcanzó la producción"
    const val DAMAGED = "Se dañó al empacar"
    const val REPLENISH = "Reposición de viaje anterior"
    /** Automáticos: los escribe el traspaso entre sucursales, no la persona. */
    const val GIVEN_PREFIX = "Se pasó a "
    const val TAKEN_PREFIX = "Se tomó de "

    /** Lo que se ofrece al mandar MENOS de lo pedido. */
    val WHEN_LESS = listOf(NOT_PRODUCED, DAMAGED)
    /** Lo que se ofrece al mandar MÁS sin sacárselo a otra sucursal. */
    val WHEN_MORE = listOf(REPLENISH)

    fun givenTo(branchName: String) = "$GIVEN_PREFIX$branchName"
    fun takenFrom(branchName: String) = "$TAKEN_PREFIX$branchName"
}

object ReceptionReason {
    const val SHORT_IN_BASKET = "Faltó en la canasta"
    const val BASKET_MISSING = "No llegó la canasta"
    const val BROKEN = "Quebrado o aplastado"
    const val WET_DIRTY = "Mojado o sucio"
    const val EXTRA = "Llegó de más"

    /** Motivos según el tipo de diferencia ([DiscrepancyType]). */
    fun optionsFor(discrepancyType: String?): List<String> = when (discrepancyType) {
        DiscrepancyType.SHORT -> listOf(SHORT_IN_BASKET, BASKET_MISSING)
        DiscrepancyType.DAMAGED -> listOf(BROKEN, WET_DIRTY)
        DiscrepancyType.EXTRA -> listOf(EXTRA)
        else -> emptyList()
    }
}

/** "Otro" con su detalle: lo que la lista no cubre, sin perder lo que pasó. */
const val OTHER_REASON = "Otro"
fun otherReason(detail: String) = "$OTHER_REASON: ${detail.trim()}"

/**
 * Un motivo está completo si es una opción de la lista, uno automático, o "Otro" con
 * detalle. "Otro" a secas no explica nada y no se acepta.
 */
fun isCompleteReason(value: String, options: List<String>): Boolean {
    val v = value.trim()
    if (v.isEmpty()) return false
    if (v in options) return true
    if (v.startsWith(PackingReason.GIVEN_PREFIX) || v.startsWith(PackingReason.TAKEN_PREFIX)) return true
    return v.startsWith("$OTHER_REASON:") && v.removePrefix("$OTHER_REASON:").isNotBlank()
}

/** En qué eslabón empezó la diferencia. */
enum class ChainStage { PRODUCTION, PACKING, TRANSIT_OR_BRANCH }

fun ChainStage.label(): String = when (this) {
    ChainStage.PRODUCTION -> "Producción"
    ChainStage.PACKING -> "Empaque"
    ChainStage.TRANSIT_OR_BRANCH -> "Camino o recepción"
}

/**
 * Un producto de una entrega, con lo que se sabe de cada eslabón.
 *
 * @property producedInShift / [plannedInShift] lo que horneó el turno de ESE producto (para
 *   todas las sucursales): el horno no sabe para quién es cada pan, así que este dato es de
 *   contexto — explica un empaque corto, no lo reparte.
 */
data class ChainLine(
    val productId: String,
    val productName: String,
    val requested: Double,
    val packed: Double,
    val packedReason: String?,
    /** null mientras la sucursal no cuenta. */
    val received: Double?,
    val discrepancyType: String?,
    val receivedReason: String?,
    val plannedInShift: Double? = null,
    val producedInShift: Double? = null,
    val wasteInShift: Double = 0.0,
    val wasteReasonInShift: String = ""
) {
    /** Empacado − pedido: negativo = salió corto de la planta. */
    val packingDiff: Double get() = packed - requested
    /** Contado − empacado: negativo = se perdió entre que salió y se contó. */
    val receptionDiff: Double? get() = received?.let { it - packed }
    val hasDifference: Boolean get() = packingDiff != 0.0 || (receptionDiff ?: 0.0) != 0.0

    /**
     * Eslabón donde empezó la diferencia. Un empaque corto se atribuye a producción
     * cuando quien empacó dijo que no alcanzó, o cuando el turno horneó menos de lo
     * planeado de ese producto: el empaque no puede mandar pan que no salió del horno.
     */
    fun firstBreak(): ChainStage? = when {
        packingDiff < 0.0 && !isTransfer() &&
            (packedReason == PackingReason.NOT_PRODUCED || shiftCameShort()) -> ChainStage.PRODUCTION
        packingDiff != 0.0 && !isTransfer() -> ChainStage.PACKING
        (receptionDiff ?: 0.0) != 0.0 -> ChainStage.TRANSIT_OR_BRANCH
        // Un traspaso entre sucursales no es quiebre: el pan existe, fue a otra.
        else -> null
    }

    /** Un traspaso entre sucursales no es pérdida: el pan existe, fue a otra. */
    fun isTransfer(): Boolean = packedReason?.let {
        it.contains(PackingReason.GIVEN_PREFIX) || it.contains(PackingReason.TAKEN_PREFIX) ||
            it.contains("reasignadas a otra sucursal")
    } == true

    private fun shiftCameShort(): Boolean {
        val planned = plannedInShift ?: return false
        val produced = producedInShift ?: return false
        return produced in 0.0..<planned
    }
}

/**
 * Lo que dicen las canastas sobre el tramo camino/recepción.
 *
 * - [MISSING_BASKETS]: llegaron menos de las que salieron — el pan se perdió en el camión.
 * - [ALL_ARRIVED]: llegaron todas — lo que falte se perdió DENTRO de la canasta (se empacó
 *   menos de lo que se marcó, o se contó mal al recibir), no en el camino.
 * - [UNKNOWN]: nota sin canastas (anterior) o todavía sin firmar.
 */
enum class BasketCheck { MISSING_BASKETS, ALL_ARRIVED, UNKNOWN }

fun basketCheck(sent: Int, received: Int?): BasketCheck = when {
    sent <= 0 || received == null -> BasketCheck.UNKNOWN
    received < sent -> BasketCheck.MISSING_BASKETS
    else -> BasketCheck.ALL_ARRIVED
}

/** Nombre del tramo camino/recepción según las canastas. */
fun transitStageLabel(check: BasketCheck): String = when (check) {
    BasketCheck.MISSING_BASKETS -> "Camión"
    BasketCheck.ALL_ARRIVED -> "Dentro de la canasta"
    BasketCheck.UNKNOWN -> ChainStage.TRANSIT_OR_BRANCH.label()
}

/** Resumen de una entrega: cuánto se perdió en cada eslabón, sin contar traspasos. */
data class ChainSummary(
    val production: Double,
    val packing: Double,
    val transitOrBranch: Double,
    val transferred: Double
) {
    val total: Double get() = production + packing + transitOrBranch
}

/** Sumas con signo: negativo = faltó, positivo = sobró. */
fun summarizeChain(lines: List<ChainLine>): ChainSummary {
    var production = 0.0; var packing = 0.0; var transit = 0.0; var transferred = 0.0
    for (l in lines) {
        when {
            l.packingDiff == 0.0 -> Unit
            l.isTransfer() -> transferred += l.packingDiff
            l.firstBreak() == ChainStage.PRODUCTION -> production += l.packingDiff
            else -> packing += l.packingDiff
        }
        transit += l.receptionDiff ?: 0.0
    }
    return ChainSummary(production, packing, transit, transferred)
}

/** Línea de despacho + línea de lote → eslabones de la cadena. */
fun chainLineOf(line: DispatchNoteLine, lotLine: ProductionLotLine?, counted: Boolean): ChainLine = ChainLine(
    productId = line.productId,
    productName = line.productName,
    requested = line.requestedQuantity,
    packed = line.packedQuantity,
    packedReason = line.packedReason,
    received = if (counted) line.receivedQuantity else null,
    discrepancyType = line.discrepancyType,
    receivedReason = line.receivedReason,
    plannedInShift = lotLine?.plannedQuantity,
    producedInShift = lotLine?.takeIf { it.producedCountConfirmed }?.producedQuantity,
    wasteInShift = lotLine?.wasteQuantity ?: 0.0,
    wasteReasonInShift = lotLine?.wasteReason.orEmpty()
)
