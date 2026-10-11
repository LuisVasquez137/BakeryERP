package com.bakery.erp.domain

import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.CostCenter
import com.bakery.erp.model.DispatchDestinationType
import com.bakery.erp.model.DispatchNote
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.model.Employee
import com.bakery.erp.model.Expense
import com.bakery.erp.model.PayrollRecord
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseLineKind
import com.bakery.erp.model.PurchaseStatus
import com.bakery.erp.model.effectiveBranchId
import com.bakery.erp.model.lineTotal
import com.bakery.erp.model.plantShare

/**
 * Resultado por sucursal (paso 6 de `arquitectura/propuesta-rentabilidad-por-sucursal.md`).
 *
 * Cada sucursal se mira como un negocio aparte que le compra el pan a la planta:
 * - **Tienda**: ventas de su caja − pan recibido (al valor del despacho) − reventa − sus gastos
 *   − su planilla − su parte del reparto = su **aporte** al saco común.
 * - **Planta**: lo que despachó (al mismo valor) − materia prima comprada − planilla − gastos.
 *   Con costo estándar congelado, lo que le queda es su eficiencia contra ese estándar.
 * - **Generales**: no se reparten (contador, sueldo del dueño...). Se restan al final.
 *
 * Una sucursal que produce y vende sale dos veces: su planta y su tienda, con la regla de
 * [plantShare] para gastos y personas, y su "pan recibido" es la nota que la planta se manda a
 * sí misma (no viaja, pero lleva su valor).
 *
 * **Regla de control**: el despacho es ingreso de la planta y costo de la tienda por el mismo
 * monto, así que se cancela, y la suma de todas las columnas tiene que dar lo mismo que la
 * empresa completa calculada sin sucursales ([BranchResultsMonth.companyCheck]).
 */

/** Compras que cuentan como costo del mes: ya recibidas. Lo pendiente de recibir no entró. */
private fun Purchase.counts(): Boolean = status != PurchaseStatus.PENDING_RECEIPT

/** Pan que ya salió de la planta (incluida la nota a su propia tienda, que se entrega sola). */
private val SHIPPED = setOf(
    DispatchStatus.IN_TRANSIT,
    DispatchStatus.ARRIVED,
    DispatchStatus.DELIVERED,
    DispatchStatus.DELIVERED_WITH_DISCREPANCY
)

data class StoreResult(
    val branchId: String,
    val name: String,
    /** La tienda dentro de una planta (comparte sucursal con su planta). */
    val isPlantStore: Boolean,
    val sales: Double,
    /** Pan recibido de las plantas, al valor del despacho. */
    val bread: Double,
    /** Productos de reventa comprados para la tienda. */
    val resale: Double,
    /** Insumos comprados por una tienda que no produce (raro: restaurante, huevos sueltos). */
    val ingredients: Double,
    val expenses: Double,
    val payroll: Double,
    val delivery: Double,
    val deliveries: Int
) {
    val grossMargin: Double get() = sales - bread - resale - ingredients
    val contribution: Double get() = grossMargin - expenses - payroll - delivery
}

data class PlantResult(
    val branchId: String,
    val name: String,
    /** Lo que despachó a las tiendas, al valor del despacho. */
    val shipped: Double,
    /** Insumos comprados para la planta en el mes (no lo que se consumió: ver doc). */
    val material: Double,
    val expenses: Double,
    val payroll: Double
) {
    val result: Double get() = shipped - material - expenses - payroll
}

data class GeneralResult(
    val expenses: Double,
    val payroll: Double,
    /** Costo de reparto de un mes sin entregas: no hay a quién cargárselo. */
    val undistributedDelivery: Double,
    /** Gastos y planilla de sucursales que ya no existen: no hay columna donde ponerlos. */
    val orphan: Double
) {
    val total: Double get() = expenses + payroll + undistributedDelivery + orphan
}

data class BranchResultsMonth(
    val stores: List<StoreResult>,
    val plants: List<PlantResult>,
    val generals: GeneralResult,
    /** La ganancia de la empresa calculada sin sucursales, para comprobar la suma. */
    val companyCheck: Double
) {
    val storesTotal: Double get() = stores.sumOf { it.contribution }
    val plantsTotal: Double get() = plants.sumOf { it.result }
    val total: Double get() = storesTotal + plantsTotal - generals.total
    val balances: Boolean get() = kotlin.math.abs(total - companyCheck) < 1.0
}

/** Parte de un gasto o persona que le toca a la tienda de [branch]; el resto es de la planta. */
fun storeShare(costCenter: String, branch: Branch): Double =
    if (CostCenter.isShared(costCenter)) 0.0 else 1.0 - plantShare(costCenter, branch)

private val Branch.produces get() = BranchCapability.CAN_PRODUCE in capabilities
/** Una planta sin mostrador no tiene tienda; todo lo demás sí (incluida una bodega con caja). */
private val Branch.hasStore get() = !(produces && BranchCapability.CAN_SELL !in capabilities)

fun branchResults(
    branches: List<Branch>,
    salesByBranch: Map<String, Double>,
    notes: List<DispatchNote>,
    purchases: List<Pair<Purchase, List<PurchaseLine>>>,
    expenses: List<Expense>,
    employees: List<Employee>,
    payrollRecords: List<PayrollRecord>,
    delivery: DeliveryMonthCost
): BranchResultsMonth {
    val byId = branches.associateBy { it.id }
    val recordsByEmployee = payrollRecords.groupBy { it.employeeId }

    val toBranches = notes.filter {
        it.status in SHIPPED && it.destinationType == DispatchDestinationType.BRANCH && it.destinationBranchId.isNotEmpty()
    }
    val shippedBy = toBranches.groupBy { it.originBranchId }.mapValues { (_, l) -> l.sumOf { it.totalValueWholesale } }
    val receivedBy = toBranches.groupBy { it.destinationBranchId }.mapValues { (_, l) -> l.sumOf { it.totalValueWholesale } }

    // Compras de inventario por sucursal y tipo. Suministros y servicios ya son gastos.
    val resaleBy = HashMap<String, Double>()
    val ingredientBy = HashMap<String, Double>()
    purchases.filter { it.first.counts() }.forEach { (p, lines) ->
        lines.forEach { l ->
            val target = when (l.lineKind) {
                PurchaseLineKind.RESALE_PRODUCT -> resaleBy
                PurchaseLineKind.INGREDIENT -> ingredientBy
                else -> null
            } ?: return@forEach
            val b = l.effectiveBranchId(p)
            target[b] = (target[b] ?: 0.0) + l.lineTotal
        }
    }

    var generalExpenses = 0.0
    var generalPayroll = 0.0
    var orphan = 0.0
    val storeExp = HashMap<String, Double>()
    val plantExp = HashMap<String, Double>()
    val storePay = HashMap<String, Double>()
    val plantPay = HashMap<String, Double>()
    fun add(m: HashMap<String, Double>, k: String, v: Double) { if (v != 0.0) m[k] = (m[k] ?: 0.0) + v }

    for (e in expenses) {
        when (e.costCenter) {
            CostCenter.GENERAL -> generalExpenses += e.amount
            CostCenter.DELIVERY -> Unit   // va por el reparto
            else -> {
                val b = byId[e.branchId]
                if (b == null) { orphan += e.amount; continue }
                add(plantExp, b.id, e.amount * plantShare(e.costCenter, b))
                add(storeExp, b.id, e.amount * storeShare(e.costCenter, b))
            }
        }
    }
    for (emp in employees) {
        val cost = emp.monthlyCost(recordsByEmployee[emp.id].orEmpty())
        if (cost <= 0.0) continue
        when (emp.costCenter) {
            CostCenter.GENERAL -> generalPayroll += cost
            CostCenter.DELIVERY -> Unit
            else -> {
                val b = byId[emp.branchId]
                if (b == null) { orphan += cost; continue }
                add(plantPay, b.id, cost * plantShare(emp.costCenter, b))
                add(storePay, b.id, cost * storeShare(emp.costCenter, b))
            }
        }
    }

    val deliveryBy = delivery.stores.associateBy { it.branchId }
    val ordered = branches.sortedBy { it.name.lowercase() }

    val plants = ordered.filter { it.produces }.map { b ->
        PlantResult(
            branchId = b.id,
            name = b.name,
            shipped = shippedBy[b.id] ?: 0.0,
            // Una planta usa todo el insumo que compra; su reventa la vende su tienda.
            material = (ingredientBy[b.id] ?: 0.0) + if (b.hasStore) 0.0 else resaleBy[b.id] ?: 0.0,
            expenses = plantExp[b.id] ?: 0.0,
            payroll = plantPay[b.id] ?: 0.0
        )
    }.filter { it.shipped != 0.0 || it.material != 0.0 || it.expenses != 0.0 || it.payroll != 0.0 || byId[it.branchId]?.active == true }

    val stores = ordered.filter { it.hasStore }.map { b ->
        StoreResult(
            branchId = b.id,
            name = b.name,
            isPlantStore = b.produces,
            sales = salesByBranch[b.id] ?: 0.0,
            bread = receivedBy[b.id] ?: 0.0,
            resale = resaleBy[b.id] ?: 0.0,
            ingredients = if (b.produces) 0.0 else ingredientBy[b.id] ?: 0.0,
            expenses = storeExp[b.id] ?: 0.0,
            payroll = storePay[b.id] ?: 0.0,
            delivery = deliveryBy[b.id]?.charge ?: 0.0,
            deliveries = deliveryBy[b.id]?.deliveries ?: 0
        )
    }.filter {
        it.sales != 0.0 || it.bread != 0.0 || it.resale != 0.0 || it.ingredients != 0.0 ||
            it.expenses != 0.0 || it.payroll != 0.0 || it.delivery != 0.0 || byId[it.branchId]?.active == true
    }

    // Lo que no cae en ninguna columna (sucursal borrada) también se resta de la empresa.
    val listedIds = (stores.map { it.branchId } + plants.map { it.branchId }).toSet()
    val unlistedInventory = (resaleBy + ingredientBy).keys.filter { it !in listedIds }
        .sumOf { (resaleBy[it] ?: 0.0) + (ingredientBy[it] ?: 0.0) }
    val unlistedSales = salesByBranch.filterKeys { it !in listedIds }.values.sum()
    // Despachos de o hacia una sucursal que no está listada: su lado no se cancela.
    val unlistedTransfers = toBranches.sumOf {
        (if (it.destinationBranchId !in listedIds) -it.totalValueWholesale else 0.0) +
            (if (it.originBranchId !in listedIds) it.totalValueWholesale else 0.0)
    }

    val unlistedDelivery = delivery.stores.filter { it.branchId !in listedIds }.sumOf { it.charge }

    val undistributed = if (delivery.deliveries == 0) delivery.total else 0.0
    val generals = GeneralResult(
        expenses = generalExpenses,
        payroll = generalPayroll,
        undistributedDelivery = undistributed,
        orphan = orphan + unlistedInventory - unlistedSales - unlistedTransfers + unlistedDelivery
    )

    // La empresa sin sucursales: todo lo vendido − todo lo comprado de inventario − todos los
    // gastos − toda la planilla (reparto incluido). Los despachos no aparecen: son internos.
    val companyCheck = salesByBranch.values.sum() -
        resaleBy.values.sum() - ingredientBy.values.sum() -
        expenses.filter { it.costCenter != CostCenter.DELIVERY }.sumOf { it.amount } -
        employees.filter { it.costCenter != CostCenter.DELIVERY }
            .sumOf { it.monthlyCost(recordsByEmployee[it.id].orEmpty()) } -
        delivery.total

    return BranchResultsMonth(stores, plants, generals, companyCheck)
}
