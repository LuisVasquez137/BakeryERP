package com.bakery.erp.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * Patrón histórico de pedidos de una sucursal solicitante hacia una planta
 * productora, para un turno y un **día de la semana** concreto.
 *
 * ## Por qué existe (costo de lecturas)
 *
 * Calcular "¿cuánto suele pedir la sucursal A los lunes?" leyendo el historial
 * en cada apertura del formulario obligaría a escanear la colección
 * `productionOrders` + la subcolección `lines/` de cada pedido candidato —
 * decenas de lecturas por pedido nuevo, creciendo con la antigüedad de la
 * empresa. En vez de eso mantenemos este documento denormalizado:
 *
 * - **Lectura**: 1 documento al abrir el formulario.
 * - **Escritura**: 1 documento extra cada vez que se guarda/edita/cancela un
 *   pedido (el repositorio lo actualiza en el mismo flujo de `saveOrder`).
 * - **Backfill**: la primera vez que no existe el patrón se reconstruye desde
 *   el historial (ahí sí hay un escaneo) y se persiste. A partir de esa vez el
 *   costo vuelve a ser 1 lectura.
 *
 * Se guarda la cantidad **por pedido** (no un acumulado) para que editar un
 * pedido ya contabilizado reemplace su muestra en vez de duplicarla.
 */
data class OrderPattern(
    /** [orderPatternId] de la combinación. */
    val id: String = "",
    val sellingBranchId: String = "",
    val producingBranchId: String = "",
    /** Vacío = plantas sin turnos configurados. */
    val shiftId: String = "",
    /** ISO: 1 = lunes … 7 = domingo. */
    val weekday: Int = 1,
    /** IDs de los pedidos que alimentan el patrón, del más reciente al más viejo. */
    val recentOrderIds: List<String> = emptyList(),
    /** `orderId` → (`productId` → cantidad pedida en ese pedido). */
    val quantitiesByOrder: Map<String, Map<String, Double>> = emptyMap(),
    val updatedAt: Long = 0L
) {
    companion object {
        /** Cuántos pedidos pasados alimentan la sugerencia (≈ un mes de ese día). */
        const val MAX_SAMPLES = 4
    }
}

/** Cuántos pedidos pasados respaldan realmente el patrón. */
val OrderPattern.sampleCount: Int get() = recentOrderIds.size

/**
 * ID determinístico del patrón. Todos los componentes son IDs de Firestore
 * (alfanuméricos), así que la concatenación es un ID de documento válido.
 */
fun orderPatternId(
    sellingBranchId: String,
    producingBranchId: String,
    shiftId: String,
    weekday: Int
): String = "${sellingBranchId}_${producingBranchId}_${shiftId.ifEmpty { "sinturno" }}_$weekday"

/** Día de la semana ISO (1 = lunes … 7 = domingo) de una fecha en millis. */
fun isoWeekdayOf(
    millis: Long,
    tz: TimeZone = TimeZone.currentSystemDefault()
): Int = runCatching {
    when (Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date.dayOfWeek) {
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
        DayOfWeek.SUNDAY -> 7
        else -> 1
    }
}.getOrDefault(1)

fun isoWeekdayName(weekday: Int): String = when (weekday) {
    1 -> "lunes"
    2 -> "martes"
    3 -> "miércoles"
    4 -> "jueves"
    5 -> "viernes"
    6 -> "sábado"
    7 -> "domingo"
    else -> "día"
}

/**
 * Cantidades sugeridas por producto.
 *
 * Regla: un producto solo se sugiere si apareció en **al menos la mitad** de los
 * pedidos que alimentan el patrón — así los productos que la sucursal pide de
 * vez en cuando no se prellenan y la encargada no tiene que borrarlos. Para los
 * que sí califican se usa la **mediana** de las cantidades en que aparecieron
 * (no el promedio): un día de fiesta con el triple de pedido no arrastra la
 * sugerencia de todos los demás días.
 */
fun OrderPattern.suggestedQuantities(): Map<String, Double> {
    val orders = recentOrderIds.mapNotNull { quantitiesByOrder[it] }
    if (orders.isEmpty()) return emptyMap()
    val productIds = orders.flatMap { it.keys }.distinct()
    val result = mutableMapOf<String, Double>()
    for (productId in productIds) {
        val present = orders.mapNotNull { it[productId]?.takeIf { q -> q > 0.0 } }
        if (present.size * 2 < orders.size) continue
        val median = present.sorted().let { sorted ->
            val mid = sorted.size / 2
            if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
        }
        val rounded = median.roundToInt().toDouble()
        if (rounded > 0.0) result[productId] = rounded
    }
    return result
}

/**
 * Registra (o reemplaza, si el pedido ya estaba contabilizado) la muestra de
 * [orderId] y deja el pedido como el más reciente. Recorta a
 * [OrderPattern.MAX_SAMPLES] y descarta las cantidades de las muestras que salen.
 */
fun OrderPattern.withOrder(orderId: String, quantities: Map<String, Double>): OrderPattern {
    if (orderId.isEmpty()) return this
    val newOrderIds = (listOf(orderId) + recentOrderIds.filter { it != orderId })
        .take(OrderPattern.MAX_SAMPLES)
    val newQuantities = (quantitiesByOrder + (orderId to quantities.filter { it.value > 0.0 }))
        .filterKeys { it in newOrderIds }
    return copy(recentOrderIds = newOrderIds, quantitiesByOrder = newQuantities)
}

/** Saca un pedido del patrón (p. ej. al cancelarlo). */
fun OrderPattern.withoutOrder(orderId: String): OrderPattern = copy(
    recentOrderIds = recentOrderIds.filter { it != orderId },
    quantitiesByOrder = quantitiesByOrder.filterKeys { it != orderId }
)

/**
 * Umbral de probabilidad de lluvia (%) a partir del cual se considera "lluvia
 * probable" para efectos del ajuste de cantidades sugeridas. Punto de
 * partida ajustable, no un modelo preciso — igual que el resto de esta
 * sugerencia (ver [suggestedQuantities]).
 */
const val RAIN_PROBABILITY_THRESHOLD_PERCENT = 60

/** Cuánto se suben las cantidades ya sugeridas cuando hay lluvia probable.
 *  Ajustable: refleja la experiencia real del negocio, no un dato medido —
 *  revisar con la dueña después de unas semanas de uso real. */
const val RAIN_BUMP_FRACTION = 0.15

/**
 * Aplica el ajuste por lluvia sobre cantidades YA sugeridas por historial.
 * Nunca inventa una cantidad para un producto sin historial de pedidos —
 * eso sería decidir qué vender, no cuánto, y no le corresponde a este
 * ajuste automático.
 */
fun Map<String, Double>.withRainBump(rainProbabilityPercent: Int?): Map<String, Double> {
    if (rainProbabilityPercent == null || rainProbabilityPercent < RAIN_PROBABILITY_THRESHOLD_PERCENT) return this
    return mapValues { (_, qty) -> (qty * (1.0 + RAIN_BUMP_FRACTION)).roundToInt().toDouble() }
}
