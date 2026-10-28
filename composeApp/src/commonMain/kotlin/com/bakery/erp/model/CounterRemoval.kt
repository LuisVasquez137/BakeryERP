package com.bakery.erp.model

import com.bakery.erp.util.fmtMoney
import com.bakery.erp.util.fmtTrim
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round
import kotlin.time.Instant

/**
 * Pan que sale del mostrador sin venderse, con la caja abierta. Antes solo se podía descontar
 * en el conteo de cierre, y ahí todo faltante parecía lo mismo.
 *
 * Tres casos que el dueño quiere ver por separado (oct 2026), cada uno con su control, porque
 * la cajera podría decir "estaba dañado" o "lo regalé" cuando fue ella quien lo botó:
 *
 * - [CounterRemovalKind.DAMAGED] — venía mal (quemado, crudo, aplastado, viejo). Es MERMA y no
 *   se le cobra a nadie, así que exige **foto**: un pan quemado se ve quemado y una bandeja en el
 *   piso se ve en el piso.
 * - [CounterRemovalKind.ACCIDENT] — se cayó o se arruinó aquí. Se le carga **a costo** a la
 *   colaboradora responsable en su cuenta. Si la responsable no es quien está en la caja,
 *   confirma con SU PIN (nadie le carga un accidente a otra). Quien tenga
 *   [Permission.FORGIVE_STAFF_ACCIDENT] puede perdonarlo, **nunca el suyo propio**.
 * - [CounterRemovalKind.COURTESY] — se regaló. Sin PIN, pero con **cuota diaria en quetzales por
 *   sucursal** ([Branch.dailyCourtesyQuota], la pone el dueño) que **bloquea** al pasarse. Se
 *   mide a precio de venta: es lo que la gente entiende por "regalé Q5 de pan".
 * - [CounterRemovalKind.STAFF_BREAD] — pan de cortesía de una colaboradora (oct 2026). Solo del
 *   pan por grupo (Q0.50) y **sin escoger el tipo**, como en la venta: se anotan unidades del
 *   grupo y el conteo de cierre las reparte por pan (`allocateGroupSales`). Para quién va en
 *   [CounterRemoval.responsibleEmployeeId], **sin PIN** (todas tienen derecho y no todas tienen
 *   PIN). Tope diario **por persona** ([Branch.dailyStaffBreadUnits]) que bloquea.
 *
 * Lo que no se registra igual aparece: en el conteo de cierre falta pan, y en el pan por grupo
 * ese faltante se le cuenta a la caja como venta — le falta efectivo.
 *
 * Firestore `companies/{c}/counterRemovals/{id}`; las líneas van en arreglos paralelos (GitLive
 * no guarda listas de objetos). Es historial: no se borra (regla `isHistory`).
 */
data class CounterRemoval(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val sessionId: String = "",
    val registerId: String = "",
    val registerName: String = "",
    val kind: String = CounterRemovalKind.DAMAGED,
    val lines: List<CounterRemovalLine> = emptyList(),
    val note: String = "",
    /** Foto del producto (obligatoria en [CounterRemovalKind.DAMAGED]). */
    val photoUrl: String = "",
    // ── Accidente ──
    val responsibleEmployeeId: String = "",
    val responsibleEmployeeName: String = "",
    /**
     * Cómo se supo que la responsable acepta: [ResponsibleConfirmation.SELF] (era ella quien
     * estaba en la caja), [ResponsibleConfirmation.PIN] (puso su PIN) o
     * [ResponsibleConfirmation.NONE] (no tiene PIN todavía: queda registrado y el dueño se entera).
     */
    val responsibleConfirmation: String = "",
    /** Lo que se le cargó: suma de cantidad × costo. */
    val chargeAmount: Double = 0.0,
    val creditMovementId: String = "",
    val forgiven: Boolean = false,
    val forgivenByUserId: String = "",
    val forgivenByName: String = "",
    val forgivenByEmployeeId: String = "",
    val forgivenAt: Long = 0L,
    val forgiveReason: String = "",
    val forgiveCreditMovementId: String = "",
    // ── Pan de colaborador: unidades de un grupo de precio, sin producto (lo reparte el conteo) ──
    val groupId: String = "",
    val groupName: String = "",
    val groupUnits: Double = 0.0,
    val groupUnitPrice: Double = 0.0,
    // ── Quién lo registró ──
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val createdByEmployeeId: String = "",
    val createdAt: Long = 0L
) {
    val totalUnits: Double get() = lines.sumOf { it.quantity } + groupUnits
    /** El pan de grupo no tiene costo hasta que el conteo de cierre lo reparte: aquí no entra. */
    val totalCost: Double get() = roundMoney(lines.sumOf { it.costValue })
    val totalPrice: Double get() = roundMoney(lines.sumOf { it.priceValue } + groupUnits * groupUnitPrice)
    val isAccident: Boolean get() = kind == CounterRemovalKind.ACCIDENT
    val isStaffBread: Boolean get() = kind == CounterRemovalKind.STAFF_BREAD
}

data class CounterRemovalLine(
    val productId: String = "",
    val productName: String = "",
    val quantity: Double = 0.0,
    /** Costo promedio del inventario de tienda al momento de sacarlo. */
    val unitCost: Double = 0.0,
    /** Precio de venta al público al momento de sacarlo. */
    val unitPrice: Double = 0.0
) {
    val costValue: Double get() = quantity * unitCost
    val priceValue: Double get() = quantity * unitPrice
}

object CounterRemovalKind {
    const val DAMAGED = "DAMAGED"
    const val ACCIDENT = "ACCIDENT"
    const val COURTESY = "COURTESY"
    const val STAFF_BREAD = "STAFF_BREAD"

    fun label(kind: String) = when (kind) {
        DAMAGED -> "Venía mal"
        ACCIDENT -> "Accidente"
        COURTESY -> "Regalo"
        STAFF_BREAD -> "Pan de colaborador"
        else -> kind
    }

    /**
     * Movimiento del inventario de tienda que escribe cada caso. El del pan de colaborador no
     * sale al registrarlo sino en el conteo de cierre, que es cuando se sabe de qué pan fue.
     */
    fun movementType(kind: String) = when (kind) {
        ACCIDENT -> FinishedMovementType.ACCIDENTE
        COURTESY -> FinishedMovementType.CORTESIA
        STAFF_BREAD -> FinishedMovementType.PAN_COLABORADOR
        else -> FinishedMovementType.MERMA
    }
}

object ResponsibleConfirmation {
    const val SELF = "SELF"
    const val PIN = "PIN"
    const val NONE = "NONE"
}

/** Lo mínimo para que la nota de un regalo diga algo ("a quién / por qué"). */
const val MIN_COURTESY_NOTE = 3

/** Regalos (a precio de venta) ya dados en la sucursal el día de [nowMillis], hora local. */
fun courtesyUsedOn(
    removals: List<CounterRemoval>,
    branchId: String,
    nowMillis: Long,
    tz: TimeZone = TimeZone.currentSystemDefault()
): Double {
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz).date
    return roundMoney(
        removals
            .filter { it.kind == CounterRemovalKind.COURTESY && it.branchId == branchId }
            .filter { Instant.fromEpochMilliseconds(it.createdAt).toLocalDateTime(tz).date == today }
            .sumOf { it.totalPrice }
    )
}

/**
 * Panes de colaborador que [employeeId] ya tomó el día de [nowMillis], en **cualquier**
 * sucursal: el tope es de la persona, no de la tienda.
 */
fun staffBreadUsedOn(
    removals: List<CounterRemoval>,
    employeeId: String,
    nowMillis: Long,
    tz: TimeZone = TimeZone.currentSystemDefault()
): Double {
    if (employeeId.isEmpty()) return 0.0
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz).date
    return removals
        .filter { it.isStaffBread && it.responsibleEmployeeId == employeeId }
        .filter { Instant.fromEpochMilliseconds(it.createdAt).toLocalDateTime(tz).date == today }
        .sumOf { it.groupUnits }
}

/**
 * Por qué no se puede registrar esta salida, o null si se puede.
 *
 * [hasPhoto]: ya se tomó la foto. [courtesyQuota]/[courtesyUsedToday]: cuota de la sucursal y
 * lo ya regalado hoy (solo cuentan en un regalo). Un costo que no se conoce en un accidente
 * bloquea: cargar Q0 sería perdonarlo sin que nadie lo decida. [staffBreadLimit]/
 * [staffBreadUsedToday]: tope diario por persona y lo que la elegida ya tomó hoy.
 */
fun counterRemovalError(
    removal: CounterRemoval,
    hasPhoto: Boolean,
    courtesyQuota: Double,
    courtesyUsedToday: Double,
    staffBreadLimit: Double = 0.0,
    staffBreadUsedToday: Double = 0.0
): String? {
    if (removal.isStaffBread) return staffBreadError(removal, staffBreadLimit, staffBreadUsedToday)
    val lines = removal.lines.filter { it.quantity > 0.0 }
    if (lines.isEmpty()) return "Agrega lo que se sacó del mostrador"
    return when (removal.kind) {
        CounterRemovalKind.DAMAGED ->
            if (!hasPhoto) "Toma una foto del producto: es lo que muestra que venía mal" else null
        CounterRemovalKind.ACCIDENT -> when {
            removal.responsibleEmployeeId.isBlank() -> "Indica a quién se le cayó"
            lines.any { it.unitCost <= 0.0 } ->
                "No hay costo registrado para ${lines.first { it.unitCost <= 0.0 }.productName}. Avisa al dueño."
            else -> null
        }
        CounterRemovalKind.COURTESY -> {
            val value = roundMoney(lines.sumOf { it.priceValue })
            val left = roundMoney(courtesyQuota - courtesyUsedToday)
            when {
                removal.note.trim().length < MIN_COURTESY_NOTE -> "Anota a quién o por qué se regaló"
                courtesyQuota <= 0.0 -> "Esta sucursal no tiene cuota de regalos. El dueño la configura en Sucursales."
                value > left + 0.004 ->
                    if (left <= 0.004) "Ya se usó toda la cuota de regalos de hoy (Q ${courtesyQuota.fmtMoney(2)})"
                    else "Solo quedan Q ${left.fmtMoney(2)} de la cuota de regalos de hoy; esto vale Q ${value.fmtMoney(2)}"
                else -> null
            }
        }
        else -> "Tipo de salida desconocido"
    }
}

private fun staffBreadError(removal: CounterRemoval, limit: Double, usedToday: Double): String? {
    val left = limit - usedToday
    val who = removal.responsibleEmployeeName.ifBlank { "Esta persona" }
    return when {
        removal.groupId.isBlank() -> "Esta caja no vende pan por grupo: no hay de dónde darlo"
        removal.responsibleEmployeeId.isBlank() -> "Indica para quién es el pan"
        removal.groupUnits <= 0.0 -> "Indica cuántos panes"
        limit <= 0.0 -> "Esta sucursal no da pan de colaborador. El dueño lo configura en Sucursales."
        removal.groupUnits > left + 1e-6 ->
            if (left <= 1e-6) "$who ya tomó sus ${limit.fmtTrim()} panes de hoy"
            else "A $who solo le quedan ${left.fmtTrim()} panes hoy"
        else -> null
    }
}

/**
 * Por qué [forgiverEmployeeId] NO puede perdonar este accidente, o null si puede. Nadie se
 * perdona a sí misma: el permiso no es la forma de que el accidente propio desaparezca.
 */
fun accidentForgiveBlocker(removal: CounterRemoval, forgiverEmployeeId: String): String? = when {
    !removal.isAccident -> "Solo se perdonan accidentes"
    removal.forgiven -> "Este accidente ya se perdonó"
    forgiverEmployeeId.isNotBlank() && forgiverEmployeeId == removal.responsibleEmployeeId ->
        "No puedes perdonar un accidente tuyo; lo decide el dueño"
    else -> null
}

/** Una línea por cada caso, para el cierre de turno: "3 venía mal · 1 accidente (Ana) · …". */
fun counterRemovalSummary(removals: List<CounterRemoval>): List<String> =
    listOf(CounterRemovalKind.DAMAGED, CounterRemovalKind.ACCIDENT, CounterRemovalKind.COURTESY, CounterRemovalKind.STAFF_BREAD)
        .mapNotNull { kind ->
            val ofKind = removals.filter { it.kind == kind }
            if (ofKind.isEmpty()) return@mapNotNull null
            val units = ofKind.sumOf { it.totalUnits }
            val who = if (kind == CounterRemovalKind.ACCIDENT || kind == CounterRemovalKind.STAFF_BREAD)
                ofKind.map { it.responsibleEmployeeName }.filter { it.isNotBlank() }.distinct()
                    .takeIf { it.isNotEmpty() }?.joinToString(prefix = " (", postfix = ")").orEmpty()
            else ""
            // El pan de colaborador todavía no tiene costo: se lo pone el conteo de cierre.
            val atPrice = kind == CounterRemovalKind.COURTESY || kind == CounterRemovalKind.STAFF_BREAD
            val value = if (atPrice) ofKind.sumOf { it.totalPrice } else ofKind.sumOf { it.totalCost }
            "${CounterRemovalKind.label(kind)}: ${units.fmtTrim()} u$who · Q ${roundMoney(value).fmtMoney(2)}" +
                if (atPrice) " a precio de venta" else " a costo"
        }

private fun roundMoney(v: Double): Double = round(v * 100) / 100.0

/**
 * Lo de cada persona en el periodo. El caso suelto siempre parece normal; el patrón no: la
 * cajera que declara el triple de "venía mal" que las demás con el mismo pan se ve aquí.
 *
 * Merma y regalo cuentan para quien los REGISTRÓ (es quien dice qué pasó); el accidente y el
 * pan de colaborador, para la RESPONSABLE (a quien se le cargó / quien se lo llevó).
 */
data class CounterRemovalPersonStat(
    val name: String,
    val damagedUnits: Double = 0.0,
    val damagedCost: Double = 0.0,
    val accidents: Int = 0,
    val accidentCharged: Double = 0.0,
    val accidentForgiven: Double = 0.0,
    val courtesyValue: Double = 0.0,
    val staffBreadUnits: Double = 0.0,
    /** A precio de venta, como los regalos: "se llevó Q2 de pan". */
    val staffBreadValue: Double = 0.0
)

fun counterRemovalByPerson(removals: List<CounterRemoval>): List<CounterRemovalPersonStat> {
    val acc = LinkedHashMap<String, CounterRemovalPersonStat>()
    fun upd(key: String, name: String, f: (CounterRemovalPersonStat) -> CounterRemovalPersonStat) {
        acc[key] = f(acc[key] ?: CounterRemovalPersonStat(name = name))
    }
    removals.forEach { r ->
        when (r.kind) {
            CounterRemovalKind.ACCIDENT -> upd("e:${r.responsibleEmployeeId}", r.responsibleEmployeeName) {
                it.copy(
                    accidents = it.accidents + 1,
                    accidentCharged = it.accidentCharged + r.chargeAmount,
                    accidentForgiven = it.accidentForgiven + if (r.forgiven) r.chargeAmount else 0.0
                )
            }
            CounterRemovalKind.STAFF_BREAD -> upd("e:${r.responsibleEmployeeId}", r.responsibleEmployeeName) {
                it.copy(staffBreadUnits = it.staffBreadUnits + r.groupUnits, staffBreadValue = it.staffBreadValue + r.totalPrice)
            }
            else -> {
                val key = r.createdByEmployeeId.takeIf { it.isNotEmpty() }?.let { "e:$it" } ?: "u:${r.createdByUserId}"
                upd(key, r.createdByUserName) {
                    if (r.kind == CounterRemovalKind.COURTESY) it.copy(courtesyValue = it.courtesyValue + r.totalPrice)
                    else it.copy(damagedUnits = it.damagedUnits + r.totalUnits, damagedCost = it.damagedCost + r.totalCost)
                }
            }
        }
    }
    return acc.values.sortedByDescending { it.damagedCost + it.accidentCharged + it.courtesyValue + it.staffBreadValue }
}
