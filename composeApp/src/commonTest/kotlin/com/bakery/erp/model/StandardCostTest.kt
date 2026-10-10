package com.bakery.erp.model

import com.bakery.erp.domain.PlantMonthCost
import com.bakery.erp.domain.ProductMaterialCost
import com.bakery.erp.domain.StandardCostGap
import com.bakery.erp.domain.doughGrams
import com.bakery.erp.domain.plantDough
import com.bakery.erp.domain.plantExpenses
import com.bakery.erp.domain.plantPayroll
import com.bakery.erp.domain.productPlants
import com.bakery.erp.domain.standardCosts
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StandardCostTest {

    private fun near(expected: Double, actual: Double?, tol: Double = 1e-6) =
        assertTrue(actual != null && abs(expected - actual) < tol, "esperado $expected, salió $actual")

    private val plantaPan = Branch(
        id = "pan", name = "Planta pan",
        capabilities = listOf(BranchCapability.CAN_PRODUCE, BranchCapability.CAN_SELL),
        storeSharePercent = 20.0
    )
    private val tienda = Branch(id = "t1", name = "Pacajá", capabilities = listOf(BranchCapability.CAN_SELL))
    private val soloPlanta = Branch(id = "rep", name = "Repostería", capabilities = listOf(BranchCapability.CAN_PRODUCE))
    private val branches = listOf(plantaPan, tienda, soloPlanta).associateBy { it.id }

    @Test
    fun plantaConTiendaParteElGasto() {
        assertEquals(1.0, plantShare(CostCenter.BRANCH, plantaPan))
        assertEquals(0.0, plantShare(CostCenter.STORE, plantaPan))
        near(0.8, plantShare(CostCenter.SITE, plantaPan))
        assertEquals(0.0, plantShare(CostCenter.GENERAL, plantaPan))
        assertEquals(0.0, plantShare(CostCenter.DELIVERY, plantaPan))
        // Una tienda no tiene planta; una planta sin mostrador se lleva todo.
        assertEquals(0.0, plantShare(CostCenter.BRANCH, tienda))
        assertEquals(1.0, plantShare(CostCenter.STORE, soloPlanta))
    }

    @Test
    fun losValoresNuevosSobrevivenAlLeer() {
        assertEquals(CostCenter.STORE, CostCenter.normalize("STORE"))
        assertEquals(CostCenter.SITE, CostCenter.normalize("SITE"))
    }

    @Test
    fun gastosDePlantaSinCajeraYConSuParteDeRenta() {
        val expenses = listOf(
            Expense(branchId = "pan", amount = 1000.0),                                 // gas del horno
            Expense(branchId = "pan", amount = 5000.0, costCenter = CostCenter.SITE),   // renta: 80%
            Expense(branchId = "pan", amount = 300.0, costCenter = CostCenter.STORE),   // bolsas
            Expense(branchId = "pan", amount = 900.0, costCenter = CostCenter.GENERAL), // contador
            Expense(branchId = "t1", amount = 2000.0)                                   // renta tienda
        )
        val byPlant = plantExpenses(expenses, branches)
        near(1000.0 + 4000.0, byPlant["pan"])
        assertNull(byPlant["t1"])
    }

    @Test
    fun planillaUsaLaNominaSiExisteYSiNoElSalarioBase() {
        val employees = listOf(
            Employee(id = "a", branchId = "pan", baseSalary = 4000.0, monthlyBenefits = 600.0),      // panadero, sin nómina
            Employee(id = "b", branchId = "pan", baseSalary = 3500.0),                               // panadero, con nómina
            Employee(id = "c", branchId = "pan", baseSalary = 3200.0, costCenter = CostCenter.STORE), // cajera
            Employee(id = "d", branchId = "pan", baseSalary = 9000.0, status = EmployeeStatus.RESIGNED),
            Employee(id = "e", branchId = "pan", baseSalary = 5000.0, costCenter = CostCenter.DELIVERY) // piloto
        )
        val records = listOf(PayrollRecord(employeeId = "b", totalGross = 3800.0, igssEmployer = 443.0))
        val p = plantPayroll(employees, records, branches).getValue("pan")
        near(4000.0 + 600.0 + 3800.0, p.amount)
        assertEquals(2, p.people)
        assertEquals(1, p.fromRecords)
    }

    @Test
    fun laMasaEsLoProducidoPorElPesoMasLosRellenos() {
        val line = ProductionLotLine(
            productId = "x", rawWeightGrams = 60.0, plannedQuantity = 120.0, producedQuantity = 100.0,
            doughFillingsSnapshot = listOf(DoughFilling(recipeId = "r", rawWeightGrams = 10.0))
        )
        near(100 * 70.0, line.doughGrams())
        assertNull(ProductionLotLine(productId = "y", producedQuantity = 10.0).doughGrams())

        val dough = plantDough(listOf("pan" to line, "pan" to ProductionLotLine(productId = "y", producedQuantity = 10.0)))
        near(7000.0, dough.getValue("pan").grams)
        assertEquals(1, dough.getValue("pan").linesWithoutWeight)
    }

    @Test
    fun laPlantaDeUnProductoEsLaQueMasMasaLeSaco() {
        val lines = listOf(
            "pan" to ProductionLotLine(productId = "x", rawWeightGrams = 60.0, producedQuantity = 100.0),
            "rep" to ProductionLotLine(productId = "x", rawWeightGrams = 60.0, producedQuantity = 10.0)
        )
        val assigned = soloPlanta.copy(assignedProductIds = listOf("pastel"))
        val plants = productPlants(lines, listOf(plantaPan, assigned))
        assertEquals("pan", plants["x"])
        assertEquals("rep", plants["pastel"])
    }

    @Test
    fun costoEstandarEsMateriaPrimaMasTasaPorGramo() {
        val product = Product(id = "x", name = "Francés", salePrice = 0.5)
        val mc = ProductMaterialCost(product, "Francés", 60.0, 0.10, 0.0, 0.0, 0.10, emptyList())
        // Q60,000 de planta entre 2,000 kg de masa = Q0.03 por gramo
        val plant = PlantMonthCost("pan", "Planta pan", 40_000.0, 20_000.0, 5, 0, 2_000_000.0, 0)
        val d = standardCosts(listOf(mc), mapOf("x" to "pan"), mapOf("pan" to plant)).single()
        near(60.0 * 0.03, d.overheadCost)
        near(0.10 + 1.8, d.unitCost)
        assertNull(d.gap)
    }

    @Test
    fun sinMasaSuficienteLaPlantaNoTieneTasaYElProductoQuedaSinCosto() {
        val product = Product(id = "p", name = "Pastel")
        val mc = ProductMaterialCost(product, "Pastel", 500.0, 10.0, 0.0, 0.0, 10.0, emptyList())
        val plant = PlantMonthCost("rep", "Repostería", 30_000.0, 0.0, 0, 0, 2_000.0, 40)
        assertNull(plant.ratePerGram)
        val d = standardCosts(listOf(mc), mapOf("p" to "rep"), mapOf("rep" to plant)).single()
        assertEquals(StandardCostGap.NO_PLANT_RATE, d.gap)
    }

    @Test
    fun elGastoFijoTieneIdFijoPorMes() {
        val item = FixedBudgetItem(id = "renta", branchId = "pan", name = "Renta", amount = 5000.0, costCenter = CostCenter.SITE)
        val e = item.toMonthlyExpense("2026-11", 1L)
        assertEquals("fijo_renta_2026-11", e.id)
        assertEquals(CostCenter.SITE, e.costCenter)
        assertEquals("2026-11", e.period)
    }
}
