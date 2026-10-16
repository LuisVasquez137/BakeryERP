package com.bakery.erp.model

/**
 * En qué renglón del reporte cae un gasto.
 *
 * Es UNA lista para que el reporte financiero sume por un solo eje, pero **no se ofrece
 * entera en ningún formulario**: las de arriba son compromisos del mes y las de abajo son
 * lo que se compra en el día, y quien anota una escoba no tiene nada que hacer eligiendo
 * entre "Depreciación de equipo" y "Seguridad". Ver [FIXED_EXPENSE_CATEGORIES] y
 * [OPERATIONAL_EXPENSE_CATEGORIES] — mismo criterio que `INGREDIENT_CATEGORIES` vs
 * `SUPPLY_CATEGORIES` en el catálogo de insumos.
 *
 * Las entradas viejas se conservan aunque hayan dejado de ofrecerse en algún formulario:
 * los gastos ya guardados las traen y quitarlas los haría caer todos en OTROS.
 */
enum class ExpenseCategory {
    // ── Compromisos del mes (Costos Fijos) ──────────────────────────────────
    ALQUILER, PLANILLA, SERVICIOS, SEGURIDAD, DEPRECIACION,
    // ── Van en los dos lados ────────────────────────────────────────────────
    MANTENIMIENTO, TRANSPORTE,
    // ── Lo que se compra en el día (anotar gasto / compra rápida) ───────────
    LIMPIEZA, EMPAQUE, HERRAMIENTAS, COCINA, PAPELERIA, UNIFORMES,
    OTROS;

    val label: String get() = when (this) {
        ALQUILER -> "Alquiler / Local"
        PLANILLA -> "Planilla / Salarios"
        SERVICIOS -> "Servicios (luz, agua, internet)"
        SEGURIDAD -> "Seguridad"
        DEPRECIACION -> "Depreciación de equipo"
        MANTENIMIENTO -> "Mantenimiento y reparaciones"
        TRANSPORTE -> "Transporte / Combustible"
        LIMPIEZA -> "Limpieza e higiene"
        EMPAQUE -> "Empaque y desechables"
        HERRAMIENTAS -> "Herramientas y ferretería"
        COCINA -> "Cocina / Cafetería"
        PAPELERIA -> "Papelería y oficina"
        UNIFORMES -> "Uniformes y protección"
        OTROS -> "Otros"
    }
}

/**
 * Las que se ofrecen en Costos Fijos: el compromiso que se repite todos los meses y que
 * revisa el dueño, no quien anota el gasto del día.
 */
val FIXED_EXPENSE_CATEGORIES: List<ExpenseCategory> = listOf(
    ExpenseCategory.ALQUILER,
    ExpenseCategory.PLANILLA,
    ExpenseCategory.SERVICIOS,
    ExpenseCategory.SEGURIDAD,
    ExpenseCategory.DEPRECIACION,
    ExpenseCategory.MANTENIMIENTO,
    ExpenseCategory.TRANSPORTE,
    ExpenseCategory.OTROS
)

/**
 * Las que se ofrecen al anotar un gasto del día.
 *
 * Están calcadas de `SUPPLY_CATEGORIES` a propósito: quien anota "cloro" y quien da de alta
 * el insumo cloro están clasificando la misma cosa, y dos vocabularios distintos para lo
 * mismo dejan el reporte partido. No incluyen las categorías de materia prima —harinas,
 * lácteos— porque eso no es un gasto: entra a inventario y su costo aparece cuando se vende.
 *
 * Tampoco hay una categoría "restaurante": el restaurante o la cafetería entran como
 * sucursal, así que sus gastos ya se separan por sucursal y lo que falta es solo el renglón
 * de en qué se fueron — para eso está COCINA.
 */
val OPERATIONAL_EXPENSE_CATEGORIES: List<ExpenseCategory> = listOf(
    ExpenseCategory.LIMPIEZA,
    ExpenseCategory.EMPAQUE,
    ExpenseCategory.HERRAMIENTAS,
    ExpenseCategory.COCINA,
    ExpenseCategory.PAPELERIA,
    ExpenseCategory.UNIFORMES,
    ExpenseCategory.MANTENIMIENTO,
    ExpenseCategory.TRANSPORTE,
    ExpenseCategory.OTROS
)

data class Expense(
    val id: String = "",
    val orgId: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val name: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTROS,
    val amount: Double = 0.0,
    val period: String = "",         // "YYYY-MM"
    val notes: String? = null,
    /**
     * Compra que originó el gasto, cuando entró por una factura de proveedor en vez de
     * capturarse a mano. Sin este vínculo, editar o borrar esa factura dejaría gastos
     * huérfanos inflando el mes — el mismo motivo por el que el inventario se corrige
     * por diferencia y no reescribiendo. Vacío = gasto capturado directamente.
     */
    val sourcePurchaseId: String = "",
    val sourcePurchaseLineId: String = "",
    // ── De dónde salió la plata ──────────────────────────────────────────────
    /**
     * Ver [ExpensePaidFrom]. Vacío = gasto anterior a este campo, o capturado sin declararlo.
     */
    val paidFrom: String = "",
    /**
     * Sesión de caja de la que salió el efectivo, cuando [paidFrom] es
     * [ExpensePaidFrom.CASH_DRAWER]. Es el vínculo con el `CashMovement` que hace que el
     * gasto ya esté descontado cuando se cuadra esa caja.
     */
    val cashSessionId: String = "",
    /** Nombre de la caja, para poder decir "salió de Caja 2" sin leer la sesión. */
    val cashRegisterName: String = "",
    /** Foto del recibo o del vale. Vacío = no se adjuntó. */
    val photoUrl: String = "",
    /**
     * A quién le corresponde, aparte de qué caja lo pagó. Ver [CostCenter]. Vacío = la
     * sucursal de [branchId], que es lo que significaban todos los gastos anteriores al campo.
     */
    val costCenter: String = CostCenter.BRANCH,
    /**
     * Quién lo anotó. En un equipo de sucursal el nombre es el del colaborador que entró
     * con su PIN (la cuenta es la misma para todas). Vacío = gasto anterior al campo o
     * generado por el sistema (costos fijos del mes).
     */
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val createdByEmployeeId: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/**
 * De qué bolsa salió el dinero de un gasto.
 *
 * La distinción no es contable sino operativa, y es la que evita el reclamo de la cajera:
 * si el gasto salió de su gaveta, a la hora del cuadre le tiene que faltar ese efectivo y el
 * sistema tiene que saberlo. Si lo puso el dueño de su bolsa, la gaveta no se toca y cuadrar
 * contra un faltante inventado sería acusarla de algo que no pasó.
 */
object ExpensePaidFrom {
    /**
     * Salió de una gaveta abierta. Genera además un `CashMovement` de tipo `EXPENSE` en esa
     * sesión, que es lo que lo descuenta del efectivo esperado al cerrar.
     */
    const val CASH_DRAWER = "CASH_DRAWER"

    /**
     * No salió de ninguna caja: lo pagó el dueño, o salió de la caja general. El gasto del
     * mes existe igual —es plata del negocio— pero ninguna gaveta cambia.
     */
    const val GENERAL = "GENERAL"

    fun label(value: String) = when (value) {
        CASH_DRAWER -> "De caja"
        GENERAL -> "Caja general / dueño"
        else -> "Sin declarar"
    }
}

/** `true` si el gasto lo generó una factura de compra y no debe editarse a mano. */
val Expense.isFromPurchase: Boolean
    get() = sourcePurchaseId.isNotEmpty()

private val MONTH_NAMES = listOf(
    "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

fun formatPeriodLabel(period: String): String {
    if (period.length != 7) return period
    val year = period.substring(0, 4).toIntOrNull() ?: return period
    val month = period.substring(5, 7).toIntOrNull() ?: return period
    return "${MONTH_NAMES.getOrElse(month) { "?" }} $year"
}

fun nextPeriod(current: String): String {
    if (current.length != 7) return current
    val year = current.substring(0, 4).toIntOrNull() ?: return current
    val month = current.substring(5, 7).toIntOrNull() ?: return current
    return if (month == 12) "${year + 1}-01"
    else "$year-${(month + 1).toString().padStart(2, '0')}"
}

fun previousPeriod(current: String): String {
    if (current.length != 7) return current
    val year = current.substring(0, 4).toIntOrNull() ?: return current
    val month = current.substring(5, 7).toIntOrNull() ?: return current
    return if (month == 1) "${year - 1}-12"
    else "$year-${(month - 1).toString().padStart(2, '0')}"
}
