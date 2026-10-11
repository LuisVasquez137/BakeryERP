package com.bakery.erp.model

import com.bakery.erp.domain.DeliveryMonthCost
import com.bakery.erp.domain.StoreDeliveryCharge
import com.bakery.erp.domain.branchResults
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BranchResultTest {

    private fun near(expected: Double, actual: Double) =
        assertTrue(abs(expected - actual) < 1e-6, "esperado $expected, salió $actual")

    private val pan = Branch(id = "pan", name = "Bombero",
        capabilities = listOf(BranchCapability.CAN_PRODUCE, BranchCapability.CAN_SELL), storeSharePercent = 20.0)
    private val t1 = Branch(id = "t1", name = "Pacajá", capabilities = listOf(BranchCapability.CAN_SELL))
    private val branches = listOf(pan, t1)

    private fun note(dest: String, value: Double) = DispatchNote(
        originBranchId = "pan", destinationBranchId = dest, status = DispatchStatus.DELIVERED, totalValueWholesale = value
    )

    private fun compute() = branchResults(
        branches = branches,
        salesByBranch = mapOf("pan" to 30_000.0, "t1" to 20_000.0),
        notes = listOf(note("pan", 15_000.0), note("t1", 12_000.0),
            note("t1", 999.0).copy(status = DispatchStatus.CANCELLED)),
        purchases = listOf(
            Purchase(branchId = "pan") to listOf(
                PurchaseLine(lineKind = PurchaseLineKind.INGREDIENT, unitCost = 1.0, quantityReceived = 9_000.0),
                PurchaseLine(lineKind = PurchaseLineKind.RESALE_PRODUCT, unitCost = 1.0, quantityReceived = 500.0),
                PurchaseLine(lineKind = PurchaseLineKind.RESALE_PRODUCT, unitCost = 1.0, quantityReceived = 300.0, branchId = "t1")
            ),
            Purchase(branchId = "t1", status = PurchaseStatus.PENDING_RECEIPT) to listOf(
                PurchaseLine(lineKind = PurchaseLineKind.RESALE_PRODUCT, unitCost = 1.0, quantityReceived = 777.0)
            )
        ),
        expenses = listOf(
            Expense(branchId = "pan", amount = 2_000.0),                               // gas: planta
            Expense(branchId = "pan", amount = 5_000.0, costCenter = CostCenter.SITE), // renta 80/20
            Expense(branchId = "pan", amount = 400.0, costCenter = CostCenter.STORE),  // bolsas
            Expense(branchId = "t1", amount = 3_000.0),                                // renta tienda
            Expense(branchId = "t1", amount = 1_500.0, costCenter = CostCenter.GENERAL), // contador
            Expense(branchId = "pan", amount = 600.0, costCenter = CostCenter.DELIVERY)  // gasolina
        ),
        employees = listOf(
            Employee(id = "a", branchId = "pan", baseSalary = 4_000.0),
            Employee(id = "b", branchId = "pan", baseSalary = 3_000.0, costCenter = CostCenter.STORE),
            Employee(id = "c", branchId = "t1", baseSalary = 3_200.0),
            Employee(id = "d", branchId = "t1", baseSalary = 6_000.0, costCenter = CostCenter.GENERAL),
            Employee(id = "e", branchId = "pan", baseSalary = 3_400.0, costCenter = CostCenter.DELIVERY)
        ),
        payrollRecords = emptyList(),
        delivery = DeliveryMonthCost(600.0, 3_400.0, 1, listOf(StoreDeliveryCharge("t1", "Pacajá", 40, 4_000.0)))
    )

    @Test
    fun laPlantaYSuTiendaSeParten() {
        val r = compute()
        val plant = r.plants.single()
        near(27_000.0, plant.shipped)
        near(9_000.0, plant.material)
        near(2_000.0 + 4_000.0, plant.expenses)
        near(4_000.0, plant.payroll)

        val store = r.stores.first { it.branchId == "pan" }
        assertTrue(store.isPlantStore)
        near(15_000.0, store.bread)
        near(500.0, store.resale)
        near(1_000.0 + 400.0, store.expenses)
        near(3_000.0, store.payroll)
        near(0.0, store.delivery)
    }

    @Test
    fun laTiendaPagaSuRepartoYNoLosGenerales() {
        val r = compute()
        val t = r.stores.first { it.branchId == "t1" }
        near(12_000.0, t.bread)
        near(300.0, t.resale)          // la compra pendiente de recibir no cuenta
        near(3_000.0, t.expenses)
        near(3_200.0, t.payroll)
        near(4_000.0, t.delivery)
        near(20_000.0 - 12_000.0 - 300.0 - 3_000.0 - 3_200.0 - 4_000.0, t.contribution)
        near(1_500.0 + 6_000.0, r.generals.total)
    }

    @Test
    fun laSumaDeLasColumnasEsLaEmpresa() {
        val r = compute()
        assertTrue(r.balances, "columnas ${r.total} vs empresa ${r.companyCheck}")
        // Empresa: ventas − inventario comprado − gastos − planilla.
        near(50_000.0 - 9_800.0 - 12_500.0 - 19_600.0, r.companyCheck)
        assertEquals(2, r.stores.size)
    }
}
