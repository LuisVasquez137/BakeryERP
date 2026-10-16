package com.bakery.erp.presentation.reports.productcost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.domain.ExpenseRepository
import com.bakery.erp.domain.PayrollRepository
import com.bakery.erp.domain.PlantMonthCost
import com.bakery.erp.domain.ProductStandardCostDraft
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.domain.StandardCostRepository
import com.bakery.erp.domain.plantDough
import com.bakery.erp.domain.plantExpenses
import com.bakery.erp.domain.plantPayroll
import com.bakery.erp.domain.productPlants
import com.bakery.erp.domain.standardCosts
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.CostCenter
import com.bakery.erp.model.FrozenPlantRate
import com.bakery.erp.model.ProductStandardCost
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.StandardCostPeriod
import com.bakery.erp.model.User
import com.bakery.erp.model.nextPeriod
import com.bakery.erp.model.previousPeriod
import com.bakery.erp.model.splitsStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class StandardCostUiState(
    /** Mes cuyo costo se calcula ("YYYY-MM"). Sale de los datos del mes anterior. */
    val targetPeriod: String = "",
    val isLoading: Boolean = false,
    val isFreezing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val plants: List<PlantMonthCost> = emptyList(),
    val drafts: List<ProductStandardCostDraft> = emptyList(),
    /** Plantas con tienda que tienen gastos de local compartido pero 0% de tienda. */
    val splitBranchesWithoutShare: List<String> = emptyList(),
    /** Lo congelado para [targetPeriod], si ya se hizo. */
    val frozen: StandardCostPeriod? = null,
    val frozenCosts: List<ProductStandardCost> = emptyList()
) {
    val sourcePeriod: String get() = previousPeriod(targetPeriod)
    val withCost: List<ProductStandardCostDraft> get() = drafts.filter { it.hasCost }
    val withoutCost: List<ProductStandardCostDraft> get() = drafts.filter { !it.hasCost }
}

/**
 * Costo estándar de planta: calcula con los gastos y la masa del mes anterior y, cuando el
 * dueño lo revisa, lo congela para el mes. Desde ese momento los despachos del mes usan ese
 * costo (ver `FirebasePriceListRepository.standardCostFor`).
 */
class StandardCostViewModel(
    private val materialLoader: MaterialCostLoader,
    private val branchRepository: BranchRepository,
    private val expenseRepository: ExpenseRepository,
    private val employeeRepository: EmployeeRepository,
    private val payrollRepository: PayrollRepository,
    private val productionRepository: ProductionRepository,
    private val standardCostRepository: StandardCostRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StandardCostUiState(targetPeriod = currentPeriod()))
    val uiState: StateFlow<StandardCostUiState> = _uiState.asStateFlow()

    /** El mes actual y el siguiente: congelar el siguiente apenas cierra el mes. */
    val periodOptions: List<String> get() = currentPeriod().let { listOf(it, nextPeriod(it)) }

    /**
     * Si quien mira puede leer salarios (`VIEW_EMPLOYEE_SALARY`). Lo fija la pantalla; sin él,
     * las fichas se leen sin la parte de pago y el costo sale sin mano de obra.
     */
    var includePay: Boolean = false

    fun load(companyId: String, target: String = _uiState.value.targetPeriod) {
        viewModelScope.launch {
            _uiState.update { it.copy(targetPeriod = target, isLoading = true, error = null) }
            runCatching { compute(companyId, target) }
                .onSuccess { s -> _uiState.value = s.copy(message = _uiState.value.message) }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "No se pudo calcular") } }
        }
    }

    private suspend fun compute(companyId: String, target: String): StandardCostUiState = coroutineScope {
        val source = previousPeriod(target)
        val (from, until) = periodRange(source)

        val materialD = async { materialLoader.load(companyId) }
        val branchesD = async { branchRepository.getBranches(companyId) }
        val expensesD = async { expenseRepository.getExpensesForPeriod(companyId, source) }
        val employeesD = async { employeeRepository.getEmployees(
            companyId, if (includePay) setOf(EmployeeRepository.PrivatePart.PAY) else emptySet()
        ) }
        val payrollD = async { runCatching { payrollRepository.getPayrollForPeriod(companyId, from, until) }.getOrElse { emptyList() } }
        val frozenD = async { standardCostRepository.getPeriod(companyId, target) }
        val frozenCostsD = async { runCatching { standardCostRepository.getCosts(companyId, target) }.getOrElse { emptyList() } }
        val lots = productionRepository.getLotsForBranchInRange(companyId, "", from, until)
            .filter { it.status != ProductionLotStatus.CANCELLED }
        val lotLines = lots.map { lot ->
            async { productionRepository.getLotLines(companyId, lot.id).map { lot.producingBranchId to it } }
        }.awaitAll().flatten()

        val branches = branchesD.await()
        val branchesById = branches.associateBy { it.id }
        val expenses = expensesD.await()
        val expenseByPlant = plantExpenses(expenses, branchesById)
        val payrollByPlant = plantPayroll(employeesD.await(), payrollD.await(), branchesById)
        val doughByPlant = plantDough(lotLines)

        val plants = branches.filter { BranchCapability.CAN_PRODUCE in it.capabilities && it.active }
            .map { b ->
                val pay = payrollByPlant[b.id]
                val dough = doughByPlant[b.id]
                PlantMonthCost(
                    branchId = b.id,
                    branchName = b.name,
                    expenses = expenseByPlant[b.id] ?: 0.0,
                    payroll = pay?.amount ?: 0.0,
                    payrollPeople = pay?.people ?: 0,
                    payrollFromRecords = pay?.fromRecords ?: 0,
                    doughGrams = dough?.grams ?: 0.0,
                    linesWithoutWeight = dough?.linesWithoutWeight ?: 0
                )
            }
        val drafts = standardCosts(
            materialCosts = materialD.await().costs,
            plantOf = productPlants(lotLines, branches),
            plants = plants.associateBy { it.branchId }
        ).sortedWith(compareBy({ it.plantName }, { it.category }, { it.productName.lowercase() }))

        StandardCostUiState(
            targetPeriod = target,
            plants = plants,
            drafts = drafts,
            splitBranchesWithoutShare = splitWithoutShare(branches, expenses),
            frozen = frozenD.await(),
            frozenCosts = frozenCostsD.await()
        )
    }

    /** Congela lo calculado para el mes elegido. Solo los productos con costo. */
    fun freeze(companyId: String, user: User) {
        val s = _uiState.value
        if (s.isFreezing || s.isLoading) return
        // Congelar sin salarios dejaría todo el mes con un costo sin mano de obra.
        if (!includePay) {
            _uiState.update { it.copy(error = "Para congelar el costo hace falta el permiso \"Ver nómina y salario\": sin él el cálculo no lleva la mano de obra.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isFreezing = true, error = null) }
            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                val costs = s.withCost.map { d ->
                    ProductStandardCost(
                        period = s.targetPeriod,
                        productId = d.productId,
                        productName = d.productName,
                        plantBranchId = d.plantBranchId,
                        plantName = d.plantName,
                        doughGrams = d.doughGrams,
                        materialCost = d.materialCost,
                        overheadCost = d.overheadCost,
                        unitCost = d.unitCost,
                        frozenAt = now
                    )
                }
                val header = StandardCostPeriod(
                    period = s.targetPeriod,
                    sourcePeriod = s.sourcePeriod,
                    plants = s.plants.map {
                        FrozenPlantRate(it.branchId, it.branchName, it.expenses, it.payroll, it.doughGrams, it.ratePerGram ?: 0.0)
                    },
                    productCount = costs.size,
                    missingCount = s.withoutCost.size,
                    frozenAt = now,
                    frozenByName = user.displayName.ifBlank { user.email }
                )
                standardCostRepository.freeze(companyId, header, costs)
                costs.size
            }.onSuccess { n ->
                _uiState.update { it.copy(isFreezing = false, message = "Costo congelado: $n productos. Los despachos de este mes ya salen a costo.") }
                load(companyId)
            }.onFailure { e ->
                _uiState.update { it.copy(isFreezing = false, error = e.message ?: "No se pudo congelar") }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun splitWithoutShare(branches: List<Branch>, expenses: List<com.bakery.erp.model.Expense>): List<String> {
        val siteBranchIds = expenses.filter { it.costCenter == CostCenter.SITE }.map { it.branchId }.toSet()
        return branches.filter { it.splitsStore && it.storeSharePercent <= 0.0 && it.id in siteBranchIds }.map { it.name }
    }
}

private fun currentPeriod(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return "${now.year}-${now.monthNumber.toString().padStart(2, '0')}"
}

/** `[inicio del mes, inicio del siguiente)` en milisegundos, en la zona del dispositivo. */
private fun periodRange(period: String): Pair<Long, Long> {
    val tz = TimeZone.currentSystemDefault()
    fun start(p: String): Long {
        val y = p.substring(0, 4).toInt()
        val m = p.substring(5, 7).toInt()
        return LocalDate(y, m, 1).atStartOfDayIn(tz).toEpochMilliseconds()
    }
    return start(period) to start(nextPeriod(period))
}
