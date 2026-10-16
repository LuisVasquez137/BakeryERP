package com.bakery.erp.presentation.preparationbatches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.PreparationBatchRepository
import com.bakery.erp.domain.PreparationRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Preparation
import com.bakery.erp.model.PreparationBatch
import com.bakery.erp.model.StockMovement
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.convertUnits
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Línea de requerimiento calculada para la vista previa antes de producir. */
data class BatchRequirementLine(
    val ingredientId: String,
    val ingredientName: String,
    val requiredQuantity: Double,   // en la unidad base del insumo
    val baseUnit: String,
    val availableStock: Double,     // en la unidad base del insumo
    val sufficient: Boolean
)

data class PreparationBatchUiState(
    // Lista
    val batches: List<PreparationBatch> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    // Formulario
    val accessibleBranches: List<Branch> = emptyList(),
    val selectedBranch: Branch? = null,
    val preparations: List<Preparation> = emptyList(),
    val selectedPreparation: Preparation? = null,
    val batchCount: Int = 1,
    val isLoadingForm: Boolean = false,
    val isCheckingStock: Boolean = false,
    val requirementLines: List<BatchRequirementLine> = emptyList(),
    val canProduce: Boolean = false
)

class PreparationBatchViewModel(
    private val batchRepository: PreparationBatchRepository,
    private val preparationRepository: PreparationRepository,
    private val ingredientRepository: IngredientRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreparationBatchUiState())
    val uiState: StateFlow<PreparationBatchUiState> = _uiState.asStateFlow()

    private var ingredientCache: List<Ingredient> = emptyList()

    fun loadBatches(companyId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { batchRepository.getBatches(companyId) }
                .onSuccess { all ->
                    val filtered = all
                        .filter { currentUser.canAccessBranch(it.branchId) }
                        .sortedByDescending { it.producedAt }
                    _uiState.update { it.copy(batches = filtered, isLoading = false) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun loadFormData(companyId: String, currentUser: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingForm = true, error = null) }
            val allBranches = runCatching { branchRepository.getBranches(companyId) }.getOrElse { emptyList() }
            val accessible = allBranches.filter { branch ->
                branch.active &&
                    currentUser.canAccessBranch(branch.id) &&
                    BranchCapability.CAN_PRODUCE in branch.capabilities
            }
            val preparations = runCatching { preparationRepository.getPreparations(companyId) }
                .getOrElse { emptyList() }
                .filter { it.active }
            ingredientCache = runCatching { ingredientRepository.getIngredients(companyId) }
                .getOrElse { emptyList() }
            _uiState.update {
                it.copy(
                    accessibleBranches = accessible,
                    selectedBranch = if (accessible.size == 1) accessible.first() else it.selectedBranch,
                    preparations = preparations,
                    isLoadingForm = false
                )
            }
        }
    }

    fun selectBranch(companyId: String, branch: Branch?) {
        _uiState.update { it.copy(selectedBranch = branch) }
        recomputeRequirements(companyId)
    }

    fun selectPreparation(companyId: String, preparation: Preparation?) {
        if (preparation == null) {
            _uiState.update { it.copy(selectedPreparation = null, requirementLines = emptyList(), canProduce = false) }
            return
        }
        viewModelScope.launch {
            // Hidratar insumos de la preparación (subcolección)
            val full = runCatching { preparationRepository.getPreparation(companyId, preparation.id) }
                .getOrElse { preparation }
            _uiState.update { it.copy(selectedPreparation = full) }
            recomputeRequirements(companyId)
        }
    }

    fun setBatchCount(companyId: String, count: Int) {
        _uiState.update { it.copy(batchCount = count.coerceAtLeast(1)) }
        recomputeRequirements(companyId)
    }

    private fun recomputeRequirements(companyId: String) {
        val state = _uiState.value
        val branch = state.selectedBranch
        val prep = state.selectedPreparation
        if (branch == null || prep == null || prep.ingredients.isEmpty()) {
            _uiState.update { it.copy(requirementLines = emptyList(), canProduce = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingStock = true) }
            val lines = prep.ingredients.map { pi ->
                val ingredient = ingredientCache.find { it.id == pi.ingredientId }
                val baseUnit = ingredient?.effectiveBaseUnit ?: pi.unit
                val required = convertUnits(
                    pi.quantityPerBatch * state.batchCount, pi.unit, baseUnit
                )
                val stocks = runCatching {
                    ingredientRepository.getBranchStocksForIngredient(
                        companyId, pi.ingredientId, listOf(branch.id)
                    )
                }.getOrElse { emptyList() }
                val available = stocks.find { it.branchId == branch.id }?.currentStock ?: 0.0
                BatchRequirementLine(
                    ingredientId = pi.ingredientId,
                    ingredientName = pi.ingredientName,
                    requiredQuantity = required,
                    baseUnit = baseUnit,
                    availableStock = available,
                    sufficient = available >= required
                )
            }
            _uiState.update {
                it.copy(
                    requirementLines = lines,
                    canProduce = lines.isNotEmpty() && lines.all { l -> l.sufficient },
                    isCheckingStock = false
                )
            }
        }
    }

    fun produceBatch(companyId: String, currentUser: User) {
        viewModelScope.launch {
            val state = _uiState.value
            val branch = state.selectedBranch ?: return@launch
            val prep = state.selectedPreparation ?: return@launch
            if (prep.ingredients.isEmpty()) {
                _uiState.update { it.copy(error = "La preparación no tiene insumos") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                // Revalidar stock por seguridad
                val shortages = mutableListOf<String>()
                data class Plan(val ingredient: Ingredient, val required: Double, val prev: Double)
                val plans = mutableListOf<Plan>()
                for (pi in prep.ingredients) {
                    val ingredient = ingredientCache.find { it.id == pi.ingredientId }
                        ?: throw IllegalStateException("Insumo no encontrado: ${pi.ingredientName}")
                    val baseUnit = ingredient.effectiveBaseUnit.ifEmpty { pi.unit }
                    val required = convertUnits(pi.quantityPerBatch * state.batchCount, pi.unit, baseUnit)
                    val stocks = ingredientRepository.getBranchStocksForIngredient(
                        companyId, pi.ingredientId, listOf(branch.id)
                    )
                    val prev = stocks.find { it.branchId == branch.id }?.currentStock ?: 0.0
                    if (prev < required) {
                        val falta = required - prev
                        shortages.add("${pi.ingredientName} (faltan ${fmt(falta)} $baseUnit)")
                    }
                    plans.add(Plan(ingredient, required, prev))
                }
                if (shortages.isNotEmpty()) {
                    throw IllegalStateException("Stock insuficiente: ${shortages.joinToString(", ")}")
                }

                val now = Clock.System.now().toEpochMilliseconds()
                val batchId = generateId()
                val producedGrams = prep.batchWeightGrams * state.batchCount

                plans.forEach { plan ->
                    val delta = -plan.required
                    ingredientRepository.updateBranchStock(
                        companyId, plan.ingredient.id, plan.ingredient.name,
                        branch.id, branch.name, delta
                    )
                    ingredientRepository.addStockMovement(
                        companyId, plan.ingredient.id, StockMovement(
                            type = "PRODUCCION",
                            quantity = delta,
                            balanceBefore = plan.prev,
                            balanceAfter = (plan.prev + delta).coerceAtLeast(0.0),
                            branchId = branch.id,
                            branchName = branch.name,
                            reference = batchId,
                            date = now,
                        createdByUserId = currentUser.id,
                        createdByUserName = currentUser.displayName.ifBlank { currentUser.email },
                        createdByEmployeeId = currentUser.operatorEmployeeId
                        )
                    )
                }

                batchRepository.addBatch(
                    companyId, PreparationBatch(
                        id = batchId,
                        preparationId = prep.id,
                        preparationName = prep.name,
                        branchId = branch.id,
                        branchName = branch.name,
                        batchCount = state.batchCount,
                        producedWeightGrams = producedGrams,
                        remainingWeightGrams = producedGrams,
                        producedByUserId = currentUser.id,
                        producedByUserName = currentUser.displayName.ifEmpty { currentUser.email },
                        producedAt = now
                    )
                )
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Error al producir el lote") }
            }
        }
    }

    /**
     * Borra un lote de preparación (típicamente uno de prueba). No revierte el
     * insumo descontado al producirlo: son lotes de prueba hechos antes de cargar
     * el stock inicial real, así que el stock ya estaba en 0 (clamp de `updateBranchStock`).
     */
    fun deleteBatch(companyId: String, batchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { batchRepository.deleteBatch(companyId, batchId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isDeleted = true,
                            batches = it.batches.filterNot { b -> b.id == batchId }
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun clearDeleted() { _uiState.update { it.copy(isDeleted = false) } }

    fun resetForm() {
        _uiState.update {
            it.copy(
                selectedBranch = if (it.accessibleBranches.size == 1) it.accessibleBranches.first() else null,
                selectedPreparation = null,
                batchCount = 1,
                requirementLines = emptyList(),
                canProduce = false,
                isSaved = false,
                error = null
            )
        }
    }

    fun clearSaved() { _uiState.update { it.copy(isSaved = false) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }

    private fun fmt(v: Double): String {
        val r = kotlin.math.round(v * 100) / 100.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }

    private fun generateId(): String {
        val hex = "0123456789abcdef"
        return Random.nextBytes(16).joinToString("") { b ->
            hex[(b.toInt() shr 4) and 0xF].toString() + hex[b.toInt() and 0xF]
        }
    }
}
