package com.bakery.erp.model

/**
 * Ajustes que le pasan a un lote **ya andando**: se quemó una tanda, o a media
 * mañana llegó un encargo que hay que hornear con la masa del día.
 *
 * ## El problema que resuelven
 *
 * [ProductionLotLine.producedQuantity] cargaba dos significados a la vez: *cuánto se
 * amasó* (de ahí sale la receta escalada y el descuento de insumos de `completeLot`) y
 * *cuánto salió bueno* (lo que se entrega). Mientras todo sale bien son el mismo
 * número; los dos casos reales lo rompen desde lados opuestos:
 *
 * - **Se quemaron 40 de 100.** Se amasó para 140 y salieron 100 buenos. El sistema
 *   descontaba insumos para 100, y los 40 de harina que sí se gastaron aparecían
 *   después como un faltante sin explicación en el conteo físico.
 * - **Encargo de 100 a media mañana.** Se amasó 300 y salieron 300, pero solo 200 van
 *   a las sucursales: los 100 extra se horneaban y no entraban a ningún inventario.
 *
 * [ProductionLotLine.wasteQuantity] y [ProductionLotLine.extraQuantity] separan esos
 * números, y [ProductionLotLine.kneadedQuantity] los vuelve a juntar para el único
 * cálculo que necesita el total: el consumo de insumos.
 */
object ProductionWasteReason {
    const val BURNED = "BURNED"
    const val DROPPED = "DROPPED"
    const val MISSHAPEN = "MISSHAPEN"
    const val OVEN_FAILURE = "OVEN_FAILURE"
    const val UNDERPROOFED = "UNDERPROOFED"
    const val OTHER = "OTHER"

    /** Orden de los chips: lo más frecuente primero. */
    val ALL = listOf(BURNED, DROPPED, MISSHAPEN, OVEN_FAILURE, UNDERPROOFED, OTHER)

    fun label(raw: String): String = when (raw) {
        BURNED -> "Se quemó"
        DROPPED -> "Se cayó"
        MISSHAPEN -> "Mal formado"
        OVEN_FAILURE -> "Falló el horno"
        UNDERPROOFED -> "No levantó"
        OTHER -> "Otro"
        // Un motivo guardado que ya no está en la lista se muestra tal cual: el
        // reporte viejo debe seguir diciendo qué pasó. Mismo criterio que
        // [PlantIssueReason.label].
        else -> raw.ifBlank { "Otro" }
    }
}

/**
 * Por qué se amasó de más, fuera de lo que pidieron las sucursales.
 *
 * Se guarda aunque hoy las dos opciones terminen en el mismo lugar (el stock terminado
 * de la planta): es lo único que después contesta si el pan de más fue por un encargo
 * que alguien pidió o por una decisión de la planta, y esa diferencia es la que
 * distingue una venta ganada de una sobreproducción.
 */
object ProductionExtraReason {
    const val CUSTOMER_ORDER = "CUSTOMER_ORDER"
    const val BRANCH_REQUEST = "BRANCH_REQUEST"
    const val PLANT_DECISION = "PLANT_DECISION"
    /** Salió más de lo pedido y se vio al contar: no lo pidió nadie. */
    const val COUNTED_MORE = "COUNTED_MORE"
    const val OTHER = "OTHER"

    /** Lo que se ofrece al anotar "hacer más". [COUNTED_MORE] lo pone el conteo solo. */
    val ALL = listOf(CUSTOMER_ORDER, BRANCH_REQUEST, PLANT_DECISION, OTHER)

    fun label(raw: String): String = when (raw) {
        CUSTOMER_ORDER -> "Encargo de un cliente"
        BRANCH_REQUEST -> "Una sucursal pidió más"
        PLANT_DECISION -> "Decisión de la planta"
        COUNTED_MORE -> "Salió de más al contar"
        OTHER -> "Otro"
        else -> raw.ifBlank { "Otro" }
    }
}

/**
 * Lo que de verdad se amasó de una línea: lo bueno + lo que se perdió + lo que se hizo
 * de más. Es la base del consumo de insumos, y el único lugar donde los tres números
 * se suman.
 *
 * No usa `plannedQuantity` como fallback a propósito — para eso está
 * [effectiveProducedQuantity], que decide cuál de los dos vale.
 */
fun ProductionLotLine.kneadedQuantity(fallbackToPlanned: Boolean = true): Double =
    effectiveProducedQuantity(fallbackToPlanned) + wasteQuantity + extraQuantity

/**
 * Lo bueno: lo contado si ya se contó, y si no lo planeado.
 *
 * El fallback existe porque la receta escalada y el mínimo de amasada se miran ANTES
 * de que exista un conteo: sin él, la lista del panadero mostraría cero masa hasta el
 * final del turno, que es justo cuando ya no sirve.
 */
fun ProductionLotLine.effectiveProducedQuantity(fallbackToPlanned: Boolean = true): Double =
    if (producedQuantity > 0.0) producedQuantity
    else if (fallbackToPlanned) plannedQuantity
    else 0.0

/**
 * Masa que hay que amasar AHORA para esta línea, en unidades.
 *
 * Incluye [ProductionLotLine.extraQuantity] y **excluye** [ProductionLotLine.wasteQuantity],
 * y esa asimetría es deliberada: lo que se hace de más todavía no se amasó, así que
 * tiene que aparecer en la lista del panadero; lo que se quemó ya se amasó una vez, y
 * sumarlo mostraría "140" — una amasada que nunca se hace de un solo golpe. La
 * reposición de lo perdido se calcula aparte, en el momento de declararla.
 */
fun ProductionLotLine.quantityToKnead(): Double = effectiveProducedQuantity() + extraQuantity

/**
 * Unidades que esta línea aporta al stock terminado de la planta al cerrar el lote:
 * el excedente del mínimo de amasada más lo que se hizo de más por encargo.
 *
 * Son dos razones distintas con el mismo efecto —pan horneado que no va en ninguna
 * nota de despacho—, y se suman acá para que `completeLot` tenga un solo lugar que
 * ingresa a la planta en vez de dos caminos que pueden divergir.
 */
fun ProductionLotLine.plantIngestQuantity(): Double = plantRetainedQuantity + extraQuantity

/** Razón legible del ingreso a planta, para el Kardex de terminado. */
fun ProductionLotLine.plantIngestReason(): String = when {
    plantRetainedQuantity > 0.0 && extraQuantity > 0.0 ->
        "Excedente por mínimo de amasada + producción extra ($recipeName)"
    extraQuantity > 0.0 ->
        "Producción extra: ${ProductionExtraReason.label(extraReason)} ($recipeName)"
    else -> "Excedente por mínimo de amasada ($recipeName)"
}

/**
 * Resumen de merma de horno de un conjunto de líneas, para el reporte.
 *
 * Es un dato que no existía: había merma en tránsito (despacho) y merma de tienda
 * (`FinishedStockMovement`), pero lo que se quema en el horno no se registraba en
 * ningún lado.
 */
data class ProductionWasteSummary(
    val productId: String,
    val productName: String,
    val recipeName: String,
    val wasteUnits: Double,
    /**
     * Unidades buenas, para poder leer la merma como porcentaje. En un lote que
     * todavía no se contó cae a lo planeado (ver [effectiveProducedQuantity]): usar 0
     * ahí daría "100% de merma" sobre un lote que apenas va empezando, que es peor
     * que la aproximación.
     */
    val goodUnits: Double,
    val byReason: Map<String, Double>
) {
    /**
     * Merma sobre lo amasado. Se divide entre lo amasado y no entre lo bueno porque
     * la pregunta es "de cada 100 que metí al horno, cuántas perdí" — dividir entre lo
     * bueno da un número mayor que 100% cuando se pierde más de la mitad.
     */
    val wastePct: Double
        get() {
            val kneaded = goodUnits + wasteUnits
            return if (kneaded <= 0.0) 0.0 else wasteUnits * 100.0 / kneaded
        }
}

/** Agrupa la merma por producto. Las líneas sin merma no aparecen. */
fun summarizeProductionWaste(lines: List<ProductionLotLine>): List<ProductionWasteSummary> =
    lines.filter { it.wasteQuantity > 0.0 }
        .groupBy { it.productId }
        .map { (productId, group) ->
            ProductionWasteSummary(
                productId = productId,
                productName = group.first().productName,
                recipeName = group.first().recipeName,
                wasteUnits = group.sumOf { it.wasteQuantity },
                goodUnits = group.sumOf { it.effectiveProducedQuantity() },
                byReason = group
                    .filter { it.wasteReason.isNotBlank() }
                    .groupBy { it.wasteReason }
                    .mapValues { (_, byReason) -> byReason.sumOf { it.wasteQuantity } }
            )
        }
        .sortedByDescending { it.wasteUnits }

// ── El conteo final reparte lo que se contó ─────────────────────────────────
//
// En la práctica nadie abría "¿Qué pasó?": el panadero corregía el número en el
// conteo (388 pedidos → contó 400; 460 → 440) y ahí se perdía el dato. Los 12 de
// más no entraban a ninguna nota ni al stock de la planta, y los 20 de menos no
// decían si se quemaron o si simplemente se amasó menos. Por eso el conteo es el
// que ahora reparte: el panadero cuenta TODO el pan bueno que tiene enfrente, y
// estas funciones lo separan en lo que va a las sucursales y lo que queda en la
// planta, y detectan el faltante que hay que explicar.

/**
 * Lo que el panadero debería tener enfrente al contar: lo pedido (más el excedente
 * del mínimo, que ya va en `plannedQuantity`) y lo que se anotó para hacer de más.
 */
fun ProductionLotLine.expectedGoodCount(): Double = plannedQuantity + extraQuantity

/**
 * El pan bueno contado hasta ahora, tal como el panadero lo vería en la mesa: lo de
 * los pedidos más lo de más. Es el número con el que se prellena el conteo.
 */
fun ProductionLotLine.physicalGoodCount(): Double =
    if (producedQuantity > 0.0) producedQuantity + extraQuantity else 0.0

/**
 * Reparto de un conteo físico [physicalGood].
 *
 * @property produced lo que va a las sucursales: nunca más que lo pedido.
 * @property extra lo que pasa de lo pedido y queda en la planta (`plantIngestQuantity`).
 * @property surplus cuánto pasó de lo esperado (pedido + lo anotado de más): es lo
 *   que conviene decirle al panadero, porque lo anotado de más ya lo sabía.
 * @property shortfall lo que falta contra lo esperado y que ninguna merma anotada
 *   explica todavía. Es lo que hay que preguntar: ¿se perdió, o se amasó menos?
 */
data class CountReconciliation(
    val produced: Double,
    val extra: Double,
    val surplus: Double,
    val shortfall: Double
)

fun ProductionLotLine.reconcileCount(physicalGood: Double): CountReconciliation {
    val good = physicalGood.coerceAtLeast(0.0)
    val expected = expectedGoodCount()
    return CountReconciliation(
        produced = minOf(good, plannedQuantity),
        extra = (good - plannedQuantity).coerceAtLeast(0.0),
        surplus = (good - expected).coerceAtLeast(0.0),
        // La merma ya anotada cuenta como explicada: 40 quemadas sin reponer dejan
        // 60 buenas, y eso no hay que volver a preguntarlo.
        shortfall = (expected - good - wasteQuantity).coerceAtLeast(0.0)
    )
}

/**
 * Red de seguridad para el cierre: una línea con más pan para las sucursales que lo
 * pedido (conteo editado por otro camino) pasa el excedente a [ProductionLotLine.extraQuantity],
 * que es lo único que entra al stock de la planta. Sin esto ese pan desaparecía de
 * los libros: no va en ninguna nota y nadie lo ingresaba.
 */
fun ProductionLotLine.withSurplusAsExtra(): ProductionLotLine {
    val surplus = producedQuantity - plannedQuantity
    if (surplus <= 0.0) return this
    return copy(
        producedQuantity = plannedQuantity,
        extraQuantity = extraQuantity + surplus,
        extraReason = extraReason.ifBlank { ProductionExtraReason.COUNTED_MORE }
    )
}

/**
 * Respuesta al faltante del conteo que NO es merma: se amasó menos de lo pedido.
 * Ahí no hay masa perdida que descontar — la diferencia la absorben las notas de
 * despacho (las sucursales reciben menos). No se guarda como motivo de merma.
 */
const val SHORTFALL_LESS_DOUGH = "LESS_DOUGH"
