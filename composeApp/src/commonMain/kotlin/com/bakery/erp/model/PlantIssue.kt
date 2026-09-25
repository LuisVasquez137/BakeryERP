package com.bakery.erp.model

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Salida de materia prima que la receta no puede explicar: la harina que se le pone
 * a la mesa para bolear, la manteca con la que se engrasan las bandejas, el azúcar
 * del espolvoreo.
 *
 * Existe porque `completeLot` solo descuenta lo que el % del panadero justifica
 * (gramos por pieza × piezas producidas). Todo lo demás se iba en silencio y el
 * faltante recién aparecía en el conteo físico, cuando ya no había forma de saber
 * si fue la mesa, un mal pesado o un robo.
 *
 * **Se anota por EMPAQUE COMPLETO, no por libras**, y eso no es una concesión a la
 * pereza: "saqué un saco" es un hecho que el panadero puede verificar sin balanza,
 * mientras que "gasté 3.5 lb enharinando" es una estimación que a las 4am nadie
 * hace bien. Se acepta a sabiendas que el saco sale del sistema el día que se abre
 * y en la mesa dura tres — el descuadre físico queda acotado a UN empaque abierto
 * por insumo, que es un número conocido, y el hueco entre un vale y el siguiente es
 * justamente el dato que se quería ("¿cada cuánto toman otro saco?"): no hace falta
 * que nadie declare cuándo se terminó.
 *
 * Es documento propio y no solo un [StockMovement] por la misma razón que
 * `Purchase` lo es: el Kardex vive en subcolección por insumo, y contestar
 * "cada cuánto sacan un saco de harina suave" leyendo N subcolecciones no escala.
 * La colección es plana (`companies/{c}/plantIssues/{id}`) para traer el período
 * completo con una consulta, igual que `branchStocks` y `stockLots`.
 */
data class PlantIssue(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val ingredientId: String = "",
    val ingredientName: String = "",
    /**
     * Presentación en la que se sacó, snapshot al momento del vale. Se guarda el
     * texto y la equivalencia y no solo el `formatId` porque el formato puede
     * cambiar de tamaño o borrarse, y un vale de agosto tiene que seguir diciendo
     * "1 × Saco de 100 lb" en septiembre. Vacío = se sacó cantidad suelta.
     */
    val formatId: String = "",
    val formatDescription: String = "",
    /** Cuántas unidades de [formatUnit] trae un empaque. 0 = cantidad suelta. */
    val unitsPerPackage: Double = 0.0,
    val formatUnit: String = "",
    /** Empaques sacados. 0 cuando fue cantidad suelta. */
    val packageQuantity: Double = 0.0,
    /**
     * Cantidad suelta TAL COMO SE TECLEÓ, con su unidad: "8 oz" no se guarda solo
     * como 0.5 lb. Mismo criterio que `RecipeComponent.inputQuantity` — el dato
     * canónico es [quantityInBaseUnit], pero sin el original el vale que decía
     * "8 oz de manteca" se relee como "0.5 lb" y deja de parecerse a lo que pasó.
     * 0 / vacío cuando se sacó por empaque.
     */
    val looseQuantity: Double = 0.0,
    val looseUnit: String = "",
    /** Lo que de verdad se descontó del stock, en la unidad base del insumo. */
    val quantityInBaseUnit: Double = 0.0,
    val baseUnit: String = "",
    val reason: String = PlantIssueReason.OTHER,
    /** Detalle libre. Obligatorio solo cuando [reason] es OTHER. */
    val notes: String = "",
    /**
     * Lote de producción del que salió, si lo hubo. Opcional a propósito: la harina
     * de la mesa se pone antes de que exista el lote, y la limpieza del turno no
     * pertenece a ninguno. Exigirlo dejaría sin registrar justo esos casos.
     */
    val lotId: String = "",
    val shiftId: String = "",
    val shiftName: String = "",
    val issuedByUserId: String = "",
    val issuedByUserName: String = "",
    val issuedAt: Long = 0L
) {
    /** true si se sacó un empaque completo; false si fue cantidad suelta. */
    val isFullPackage: Boolean get() = unitsPerPackage > 0.0 && packageQuantity > 0.0

    /** "1 × Saco de 100 lb", o "8 oz" con la unidad en que se tecleó. */
    val displayQuantity: String
        get() = when {
            isFullPackage -> "${formatCount(packageQuantity)} × ${formatDescription.ifBlank { "empaque" }}"
            looseQuantity > 0.0 && looseUnit.isNotBlank() -> "${formatCount(looseQuantity)} $looseUnit"
            // Vales anteriores a `looseQuantity`: solo queda la unidad base.
            else -> "${formatCount(quantityInBaseUnit)} $baseUnit"
        }

    val reasonLabel: String get() = PlantIssueReason.label(reason)

    /**
     * Lo que se lee en el Kardex: el motivo, el detalle y la cantidad.
     *
     * La cantidad va SIEMPRE, también en las salidas sueltas: la columna del Kardex
     * muestra la unidad base, así que sin esto un vale de "8 oz" aparecía como
     * "0.5 lb" y no se parecía a lo que la persona anotó.
     */
    val kardexNote: String
        get() = buildString {
            append(reasonLabel)
            if (notes.isNotBlank()) append(" — ").append(notes)
            append(" (").append(displayQuantity).append(")")
        }
}

/**
 * Motivos por los que sale material de bodega sin pasar por una receta.
 *
 * Lista fija con salida a texto libre, y no texto libre puro, porque el reporte es
 * el punto entero de esto: con motivos escritos a mano, a los tres meses "enharinar
 * mesa", "harina mesa" y "p/ mesa" son tres renglones distintos y no se puede sumar
 * nada. Se guarda el nombre y no el ordinal para que reordenar la lista no reescriba
 * el historial.
 */
object PlantIssueReason {
    const val DUSTING = "DUSTING"
    const val GREASING = "GREASING"
    const val TOPPING = "TOPPING"
    const val TESTING = "TESTING"
    const val SPILLAGE = "SPILLAGE"
    const val CLEANING = "CLEANING"
    const val OTHER = "OTHER"

    /** Orden de los chips del diálogo: lo más frecuente primero. */
    val ALL = listOf(DUSTING, GREASING, TOPPING, TESTING, SPILLAGE, CLEANING, OTHER)

    fun label(raw: String): String = when (raw) {
        DUSTING -> "Enharinar mesa / boleado"
        GREASING -> "Engrasar bandejas y moldes"
        TOPPING -> "Espolvorear o decorar"
        TESTING -> "Prueba o ajuste de masa"
        SPILLAGE -> "Derrame o desperdicio"
        CLEANING -> "Limpieza"
        OTHER -> "Otro"
        // Un motivo guardado que ya no está en la lista se muestra tal cual en vez
        // de caer en "Otro": el reporte viejo debe seguir diciendo qué pasó.
        else -> raw.ifBlank { "Otro" }
    }

    /** Etiqueta corta para chips y tablas, donde el nombre completo no cabe. */
    fun shortLabel(raw: String): String = when (raw) {
        DUSTING -> "Enharinar"
        GREASING -> "Engrasar"
        TOPPING -> "Espolvorear"
        TESTING -> "Prueba"
        SPILLAGE -> "Derrame"
        CLEANING -> "Limpieza"
        OTHER -> "Otro"
        else -> raw
    }
}

/**
 * Cuánto stock descuenta sacar [packages] empaques de [format], en la unidad base
 * del insumo.
 *
 * El formato puede estar declarado en otra unidad que el stock (un saco medido en
 * "lb" sobre un insumo cuya base es "gr"), así que la conversión no se puede saltar.
 * Devuelve null cuando no hay conversión posible — un empaque en "unidad" sobre un
 * insumo que se guarda en "lb" sin peso unitario configurado — para que el vale se
 * rechace con un mensaje en vez de descontar un número inventado. Mismo criterio que
 * [toGramsOrNull]: antes `toBaseUnit` devolvía el valor tal cual y 6 huevos entraban
 * como 6 gramos.
 */
fun packagesToBaseUnitOrNull(
    packages: Double,
    format: PurchaseFormat,
    ingredient: Ingredient
): Double? {
    if (packages <= 0.0 || format.unitsPerPackage <= 0.0) return null
    val baseUnit = ingredient.effectiveBaseUnit
    if (baseUnit.isBlank()) return null
    val totalInFormatUnit = packages * format.unitsPerPackage
    if (format.unit == baseUnit) return totalInFormatUnit
    // Misma familia (lb → gr): conversión directa. Distinta familia (unidad → lb):
    // hay que pasar por el peso de una unidad, que es el único puente que existe.
    return if (unitFamily(format.unit) != null && unitFamily(format.unit) == unitFamily(baseUnit)) {
        convertUnits(totalInFormatUnit, format.unit, baseUnit)
    } else {
        val grams = toGramsOrNull(totalInFormatUnit, format.unit, ingredient.unitWeightGrams)
            ?: return null
        gramsToUnitOrNull(grams, baseUnit, ingredient.unitWeightGrams)
    }
}

/**
 * Cuánto stock descuenta sacar [quantity] [unit] sueltos, en la unidad base del insumo.
 *
 * Existe porque no todo lo que sale de bodega es un empaque entero: el dueño agarra
 * 8 oz de manteca del bote ya abierto, y obligarlo a declarar "1 bote" para poder
 * anotarlo lo haría mentirle al inventario por un dato que él mismo quiere correcto.
 *
 * Devuelve null cuando no hay conversión —una unidad de conteo sin peso unitario, o
 * unidades de familias distintas sin puente— por el mismo motivo que
 * [packagesToBaseUnitOrNull]: rechazar el vale es mejor que descontar un número
 * inventado.
 */
fun looseToBaseUnitOrNull(
    quantity: Double,
    unit: String,
    ingredient: Ingredient
): Double? {
    if (quantity <= 0.0) return null
    val baseUnit = ingredient.effectiveBaseUnit
    if (baseUnit.isBlank()) return null
    if (unit == baseUnit) return quantity
    return if (unitFamily(unit) != null && unitFamily(unit) == unitFamily(baseUnit)) {
        convertUnits(quantity, unit, baseUnit)
    } else {
        val grams = toGramsOrNull(quantity, unit, ingredient.unitWeightGrams) ?: return null
        gramsToUnitOrNull(grams, baseUnit, ingredient.unitWeightGrams)
    }
}

/**
 * Unidades en que tiene sentido teclear una cantidad suelta de este insumo.
 *
 * Sale de la unidad base y su familia —una harina en `lb` acepta también `oz`, `kg`
 * y `gr`— más las que el insumo tenga declaradas. Es lo que hace que "unas libras o
 * unas onzas, según el insumo" no sea una lista fija: la manteca se agarra en onzas
 * y la harina en libras, y ninguna de las dos debería ver la unidad de la otra
 * cuando no convierte.
 *
 * Se filtran las que no se pueden convertir a la base (un `saco` sin equivalencia,
 * un `unidad` sin peso unitario): ofrecer una unidad que después rechaza el vale es
 * peor que no ofrecerla.
 */
fun Ingredient.looseUnitOptions(): List<String> {
    val base = effectiveBaseUnit
    if (base.isBlank()) return emptyList()
    return (listOf(base) + compatibleUnits(base) + allowedUnits)
        .distinct()
        .filter { looseToBaseUnitOrNull(1.0, it, this) != null }
}

/** Inverso de [packagesToBaseUnitOrNull]: cuántos empaques representa [baseQuantity]. */
fun baseUnitToPackagesOrNull(
    baseQuantity: Double,
    format: PurchaseFormat,
    ingredient: Ingredient
): Double? {
    val perPackage = packagesToBaseUnitOrNull(1.0, format, ingredient) ?: return null
    return if (perPackage <= 0.0) null else baseQuantity / perPackage
}

/**
 * Resumen de consumo de un insumo en un período. Es lo que contesta la pregunta
 * que originó el módulo: "¿cada cuánto toman otro saco?".
 */
data class PlantIssueIngredientSummary(
    val ingredientId: String,
    val ingredientName: String,
    /** Empaques sacados en el período. Suma solo los vales que trajeron empaque. */
    val packages: Double,
    /** Total descontado, en la unidad base — incluye las salidas sueltas. */
    val quantityInBaseUnit: Double,
    val baseUnit: String,
    val issueCount: Int,
    /** De [issueCount], cuántas fueron un empaque completo. El resto son sueltas. */
    val packageIssueCount: Int,
    /**
     * Días promedio entre que se abre un empaque y el siguiente. null con menos de
     * DOS salidas por empaque: con una sola no hay intervalo que medir, y devolver 0
     * se leería como "abren uno diario", que es lo contrario de la verdad.
     *
     * **Se miden solo las salidas por empaque, no todas.** La pregunta es "¿cada
     * cuánto se abre otro saco?", y agarrar 8 oz del bote ya abierto no abre nada:
     * contarlas acortaría el intervalo y haría ver un consumo que no ocurrió.
     */
    val avgDaysBetween: Double?,
    val lastIssuedAt: Long,
    /** Empaques por motivo, para ver en qué se va el saco. */
    val packagesByReason: Map<String, Double>
)

/**
 * Agrupa los vales por insumo y calcula cada cuánto se abre otro empaque.
 *
 * Dos decisiones sobre el intervalo, y las dos cambian el número:
 *
 * 1. **Se mide entre vales consecutivos**, no dividiendo el período entre el número
 *    de salidas: con tres sacos la primera semana y ninguno las otras tres, el
 *    promedio simple diría "uno cada 7 días" y perdería el dato real, que el consumo
 *    se concentró.
 * 2. **Solo cuentan las salidas por EMPAQUE.** Agarrar 8 oz del bote ya abierto no
 *    abre un empaque nuevo; contarlas acortaría el intervalo y haría ver una rotación
 *    que no ocurrió. Los totales de cantidad sí las incluyen — ese material salió.
 */
fun summarizePlantIssues(issues: List<PlantIssue>): List<PlantIssueIngredientSummary> =
    issues.groupBy { it.ingredientId }.map { (ingredientId, forIngredient) ->
        val ordered = forIngredient.sortedBy { it.issuedAt }
        val packageIssues = ordered.filter { it.isFullPackage }
        val gaps = packageIssues
            .zipWithNext { a, b -> (b.issuedAt - a.issuedAt).toDouble() / MILLIS_PER_DAY }
            .filter { it >= 0.0 }
        PlantIssueIngredientSummary(
            ingredientId = ingredientId,
            ingredientName = ordered.last().ingredientName,
            packages = ordered.sumOf { it.packageQuantity },
            quantityInBaseUnit = ordered.sumOf { it.quantityInBaseUnit },
            baseUnit = ordered.last().baseUnit,
            issueCount = ordered.size,
            packageIssueCount = packageIssues.size,
            avgDaysBetween = if (gaps.isEmpty()) null else gaps.sum() / gaps.size,
            lastIssuedAt = ordered.last().issuedAt,
            packagesByReason = ordered
                .groupBy { it.reason }
                .mapValues { (_, group) -> group.sumOf { it.packageQuantity } }
        )
    }.sortedByDescending { it.issueCount }

/**
 * Texto del ritmo de consumo de un insumo, ya resuelto para los tres casos.
 *
 * Vive acá y no en la pantalla porque lo usan el reporte propio y el de Producción, y
 * el caso "solo cantidades sueltas" es fácil de olvidar en uno de los dos — ahí la
 * fila decía "una sola salida" sobre un insumo del que hubo diez.
 */
fun PlantIssueIngredientSummary.rhythmLabel(): String = when {
    avgDaysBetween != null -> "1 empaque cada ${formatCount(avgDaysBetween)} días"
    packageIssueCount == 1 -> "un solo empaque en el período"
    else -> "solo cantidades sueltas"
}

/**
 * Días desde la última salida de [ingredientId], para el aviso del diálogo
 * ("Último saco: hace 2 días"). Es la guarda contra registrar dos veces el mismo
 * saco, y no cuesta un solo toque extra al panadero.
 *
 * [onlyPackages] la acota a las salidas por empaque: la advertencia de "puede que
 * ese saco siga abierto" solo aplica a un empaque, no a las onzas que alguien agarró
 * del bote.
 */
fun daysSinceLastIssue(
    issues: List<PlantIssue>,
    ingredientId: String,
    now: Long,
    onlyPackages: Boolean = false
): Double? =
    issues.filter { it.ingredientId == ingredientId && (!onlyPackages || it.isFullPackage) }
        .maxByOrNull { it.issuedAt }
        ?.let { (now - it.issuedAt).toDouble() / MILLIS_PER_DAY }
        ?.takeIf { it >= 0.0 }

const val MILLIS_PER_DAY = 86_400_000.0

private fun formatCount(value: Double): String {
    if (abs(value - value.roundToInt()) < 0.01) return value.roundToInt().toString()
    val tenths = (value * 10).roundToInt()
    return "${tenths / 10}.${abs(tenths % 10)}"
}
