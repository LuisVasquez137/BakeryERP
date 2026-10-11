package com.bakery.erp.domain

import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.Employee
import com.bakery.erp.model.EmployeeStatus
import com.bakery.erp.model.Expense
import com.bakery.erp.model.PayrollRecord
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.plantShare

/**
 * Costo estándar (paso 4 de `arquitectura/propuesta-rentabilidad-por-sucursal.md`).
 *
 * La planta le entrega el pan a las tiendas **a su costo**: materia prima de la receta + los
 * gastos de la planta repartidos por kilo de masa. Los gastos del mes solo se conocen al
 * cerrarlo, así que el costo de un mes sale de los datos del mes ANTERIOR y queda fijo: todos
 * los despachos del mes usan el mismo número. La diferencia contra lo que de verdad se gastó
 * se queda en la planta (su eficiencia).
 *
 * Por kilo y no por unidad: un pan de 120 g no gasta lo mismo que uno de 40 g.
 */

/**
 * Menos masa que esto en un mes no da una tasa confiable: unos cuantos panes con peso en un
 * mes de repostería sin pesos dividirían todos los gastos entre casi nada.
 */
const val MIN_DOUGH_GRAMS_FOR_RATE = 50_000.0

/** Gastos de una planta en el mes y los kilos de masa que salieron de ella. */
data class PlantMonthCost(
    val branchId: String,
    val branchName: String,
    /** Gastos anotados a la planta (incluida su parte del local compartido). */
    val expenses: Double,
    /** Planilla de los colaboradores cargados a la planta. */
    val payroll: Double,
    /** Colaboradores que entraron a [payroll] y cuántos con nómina registrada (el resto, salario base). */
    val payrollPeople: Int,
    val payrollFromRecords: Int,
    val doughGrams: Double,
    /** Líneas de lote producidas sin peso en crudo: su masa no se pudo contar. */
    val linesWithoutWeight: Int
) {
    val total: Double get() = expenses + payroll
    /** Q por gramo de masa; null si no hay masa suficiente para repartir. */
    val ratePerGram: Double?
        get() = if (doughGrams >= MIN_DOUGH_GRAMS_FOR_RATE) total / doughGrams else null
}

/**
 * Gastos del mes de cada planta. Lo anotado en una planta con tienda se parte con
 * [plantShare]: la cajera y las bolsas no son costo del pan, y la renta del local solo en la
 * parte que no ocupa la tienda. Generales y Reparto no son de ninguna planta.
 */
fun plantExpenses(expenses: List<Expense>, branchesById: Map<String, Branch>): Map<String, Double> =
    expenses.groupBy { it.branchId }
        .mapNotNull { (branchId, list) ->
            val branch = branchesById[branchId] ?: return@mapNotNull null
            val amount = list.sumOf { it.amount * plantShare(it.costCenter, branch) }
            if (amount == 0.0) null else branchId to amount
        }.toMap()

/** Planilla de una planta y de dónde salió. */
data class PlantPayroll(val amount: Double, val people: Int, val fromRecords: Int)

/**
 * Planilla del mes de cada planta. Por persona: si se registró su nómina del mes, su bruto; si
 * no y sigue trabajando, su salario base. A eso se suman las prestaciones al mes que el dueño
 * fijó en su ficha. Sin IGSS patronal (decisión del dueño). La nómina no se registra todos los
 * meses y sin la planilla el costo del pan saldría regalado.
 *
 * [payrollRecords] ya viene filtrado al mes.
 */
fun plantPayroll(
    employees: List<Employee>,
    payrollRecords: List<PayrollRecord>,
    branchesById: Map<String, Branch>
): Map<String, PlantPayroll> {
    val recordsByEmployee = payrollRecords.groupBy { it.employeeId }
    val acc = HashMap<String, PlantPayroll>()
    for (emp in employees) {
        val branch = branchesById[emp.branchId] ?: continue
        val share = plantShare(emp.costCenter, branch)
        if (share <= 0.0) continue
        val records = recordsByEmployee[emp.id].orEmpty()
        val monthly = emp.monthlyCost(records)
        if (monthly <= 0.0) continue
        val prev = acc[branch.id] ?: PlantPayroll(0.0, 0, 0)
        acc[branch.id] = PlantPayroll(
            amount = prev.amount + monthly * share,
            people = prev.people + 1,
            fromRecords = prev.fromRecords + if (records.isNotEmpty()) 1 else 0
        )
    }
    return acc
}

/**
 * Lo que cuesta una persona en el mes: su nómina registrada (bruto) o, si no hay y sigue
 * trabajando, su salario base; más las prestaciones al mes de su ficha. Sin IGSS.
 * [records] son las nóminas de ESA persona en el mes.
 */
fun Employee.monthlyCost(records: List<PayrollRecord>): Double {
    val pay = when {
        records.isNotEmpty() -> records.sumOf { it.totalGross }
        isWorking -> baseSalary
        else -> 0.0
    }
    return if (pay > 0.0) pay + monthlyBenefits else 0.0
}

private val Employee.isWorking: Boolean
    get() = active && status != EmployeeStatus.FIRED && status != EmployeeStatus.RESIGNED

/** Masa en crudo de una línea de lote: lo producido (o lo planeado si no se contó) × peso + rellenos. */
fun ProductionLotLine.doughGrams(): Double? {
    val raw = rawWeightGrams?.takeIf { it > 0.0 } ?: return null
    val qty = producedQuantity.takeIf { it > 0.0 } ?: plannedQuantity
    return qty * (raw + doughFillingsSnapshot.sumOf { it.rawWeightGrams })
}

/** Masa del mes por planta y cuántas líneas no se pudieron pesar. Recibe (planta, línea). */
data class PlantDough(val grams: Double, val linesWithoutWeight: Int)

fun plantDough(lines: List<Pair<String, ProductionLotLine>>): Map<String, PlantDough> =
    lines.filter { (_, l) -> !l.isSpecial && l.productId.isNotEmpty() }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, ls) ->
            val grams = ls.mapNotNull { it.doughGrams() }
            PlantDough(grams.sum(), ls.count { it.doughGrams() == null && (it.producedQuantity > 0.0 || it.plannedQuantity > 0.0) })
        }

/**
 * Qué planta hace cada producto: la que más masa le sacó en el mes. Si en el mes no se hizo,
 * la planta que lo tiene asignado (si es una sola).
 */
fun productPlants(
    lines: List<Pair<String, ProductionLotLine>>,
    branches: List<Branch>
): Map<String, String> {
    val fromLots = lines.filter { it.second.productId.isNotEmpty() }
        .groupBy { it.second.productId }
        .mapValues { (_, ls) ->
            ls.groupBy { it.first }
                .maxByOrNull { (_, byPlant) -> byPlant.sumOf { it.second.doughGrams() ?: it.second.producedQuantity } }!!.key
        }
    val plants = branches.filter { BranchCapability.CAN_PRODUCE in it.capabilities }
    val fromAssignment = plants.flatMap { b -> b.assignedProductIds.map { it to b.id } }
        .groupBy({ it.first }, { it.second })
        .filterValues { it.size == 1 }
        .mapValues { it.value.first() }
    return fromAssignment + fromLots
}

/** Por qué un producto queda sin costo estándar: se despacha con la lista "Sucursales". */
enum class StandardCostGap(val label: String) {
    NO_MATERIAL_COST("Sin costo de materia prima"),
    NO_PLANT("No se sabe qué planta lo hace"),
    NO_PLANT_RATE("Su planta no tiene kilos de masa suficientes")
}

data class ProductStandardCostDraft(
    val productId: String,
    val productName: String,
    val category: String,
    val salePrice: Double,
    val plantBranchId: String,
    val plantName: String,
    /** Gramos de masa que cargan gastos de planta (peso en crudo + rellenos). */
    val doughGrams: Double,
    val materialCost: Double,
    val overheadCost: Double,
    /** Materia prima con algún insumo sin precio. */
    val materialIncomplete: Boolean,
    val gap: StandardCostGap?
) {
    val unitCost: Double get() = materialCost + overheadCost
    val hasCost: Boolean get() = gap == null
}

/** Costo estándar de cada producto: materia prima + tasa de su planta × gramos de masa. */
fun standardCosts(
    materialCosts: List<ProductMaterialCost>,
    plantOf: Map<String, String>,
    plants: Map<String, PlantMonthCost>
): List<ProductStandardCostDraft> = materialCosts.map { mc ->
    val p = mc.product
    val plantId = plantOf[p.id].orEmpty()
    val plant = plants[plantId]
    val dough = mc.doughGrams + p.doughFillings.sumOf { it.rawWeightGrams }
    val rate = plant?.ratePerGram
    val gap = when {
        mc.total == null -> StandardCostGap.NO_MATERIAL_COST
        plantId.isEmpty() -> StandardCostGap.NO_PLANT
        rate == null -> StandardCostGap.NO_PLANT_RATE
        else -> null
    }
    ProductStandardCostDraft(
        productId = p.id,
        productName = p.name,
        category = p.category,
        salePrice = p.salePrice,
        plantBranchId = plantId,
        plantName = plant?.branchName.orEmpty(),
        doughGrams = dough,
        materialCost = mc.total ?: 0.0,
        overheadCost = if (rate != null) dough * rate else 0.0,
        materialIncomplete = mc.isIncomplete,
        gap = gap
    )
}
