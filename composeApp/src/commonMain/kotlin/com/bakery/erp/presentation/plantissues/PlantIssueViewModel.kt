package com.bakery.erp.presentation.plantissues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.PlantIssueRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.PlantIssue
import com.bakery.erp.model.PlantIssueIngredientSummary
import com.bakery.erp.model.PlantIssueReason
import com.bakery.erp.model.STOCK_TRANSFER_MOVEMENT_TYPE
import com.bakery.erp.model.PurchaseFormat
import com.bakery.erp.model.StockMovement
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.daysSinceLastIssue
import com.bakery.erp.model.looseToBaseUnitOrNull
import com.bakery.erp.model.packagesToBaseUnitOrNull
import com.bakery.erp.model.summarizePlantIssues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class PlantIssueUiState(
    val ingredients: List<Ingredient> = emptyList(),
    val accessibleBranches: List<Branch> = emptyList(),
    val selectedBranch: Branch? = null,
    /** Stock actual de la sucursal elegida, en unidad base, por ingredientId. */
    val stockByIngredient: Map<String, Double> = emptyMap(),
    /**
     * Vales recientes de la sucursal. Alimentan el "Último saco: hace 2 días" del
     * diálogo, que es la guarda contra anotar dos veces el mismo saco.
     */
    val recentIssues: List<PlantIssue> = emptyList(),
    /** Vales del período consultado en el historial/reporte. */
    val periodIssues: List<PlantIssue> = emptyList(),
    val summaries: List<PlantIssueIngredientSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    /** Texto del snackbar tras guardar o borrar. Se limpia con [consumeMessage]. */
    val message: String? = null,
    val lastSavedIssue: PlantIssue? = null
)

/**
 * Vales de salida de material de planta. Ver [PlantIssue] para el porqué.
 *
 * La orquestación vive acá y no en el repositorio a propósito: guardar el vale son
 * TRES escrituras que tienen que ir juntas —documento, stock y Kardex— y el stock lo
 * mueve `IngredientRepository.updateBranchStock`, que además mantiene el libro de
 * lotes FEFO. Meter eso dentro de `PlantIssueRepository` habría creado un quinto
 * camino que baja inventario por su cuenta, que es exactamente lo que ese método
 * existe para evitar.
 */
class PlantIssueViewModel(
    private val plantIssueRepository: PlantIssueRepository,
    private val ingredientRepository: IngredientRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlantIssueUiState())
    val uiState: StateFlow<PlantIssueUiState> = _uiState.asStateFlow()

    /**
     * Carga catálogo, sucursales y —si ya hay una elegida— su stock y sus vales
     * recientes. [preferredBranchId] llega del lote cuando el diálogo se abre desde
     * la Lista de Producción: ahí la planta ya está decidida y no es una pregunta.
     */
    fun load(companyId: String, user: User, preferredBranchId: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val ingredients = runCatching { ingredientRepository.getIngredients(companyId) }
                .getOrElse { emptyList() }
                .filter { it.active }
                .sortedBy { it.name.lowercase() }
            val branches = runCatching { branchRepository.getBranches(companyId) }
                .getOrElse { emptyList() }
                .filter { it.active && user.canAccessBranch(it.id) }
            // El material sale de donde se produce o se almacena. Una sucursal que
            // solo vende no tiene mesa que enharinar, y ofrecerla convierte el
            // selector en una forma de descontarle a la bodega equivocada.
            val plants = branches.filter {
                BranchCapability.CAN_PRODUCE in it.capabilities ||
                    BranchCapability.IS_WAREHOUSE in it.capabilities
            }.ifEmpty { branches }
            val selected = plants.firstOrNull { it.id == preferredBranchId }
                ?: _uiState.value.selectedBranch?.let { prev -> plants.firstOrNull { it.id == prev.id } }
                ?: plants.singleOrNull()
                ?: plants.firstOrNull()
            _uiState.update {
                it.copy(
                    ingredients = ingredients,
                    accessibleBranches = plants,
                    selectedBranch = selected,
                    isLoading = false
                )
            }
            if (selected != null) refreshBranchContext(companyId, selected)
        }
    }

    fun selectBranch(companyId: String, branch: Branch) {
        _uiState.update { it.copy(selectedBranch = branch) }
        viewModelScope.launch { refreshBranchContext(companyId, branch) }
    }

    private suspend fun refreshBranchContext(companyId: String, branch: Branch) {
        val stocks = runCatching { ingredientRepository.getBranchStocksForBranch(companyId, branch.id) }
            .getOrElse { emptyMap() }
        val recent = runCatching { plantIssueRepository.getRecentIssues(companyId, branch.id) }
            .getOrElse { emptyList() }
        _uiState.update { it.copy(stockByIngredient = stocks, recentIssues = recent) }
    }

    /** Días desde el último vale de este insumo en la sucursal elegida. */
    fun daysSinceLast(ingredientId: String): Double? = daysSinceLastIssue(
        issues = _uiState.value.recentIssues,
        ingredientId = ingredientId,
        now = Clock.System.now().toEpochMilliseconds()
    )

    /** El vale más reciente de este insumo, para decir quién lo sacó y para qué. */
    fun lastIssueFor(ingredientId: String): PlantIssue? =
        _uiState.value.recentIssues.filter { it.ingredientId == ingredientId }
            .maxByOrNull { it.issuedAt }

    fun stockOf(ingredientId: String): Double = _uiState.value.stockByIngredient[ingredientId] ?: 0.0

    /**
     * Registra el vale: documento + descuento de stock + Kardex.
     *
     * [format] null significa cantidad suelta: [quantity] va en [looseUnit] (las onzas
     * que alguien agarró del bote), y la conversión la hace [looseToBaseUnitOrNull].
     * Con formato, [quantity] son EMPAQUES y convierte [packagesToBaseUnitOrNull].
     * Las dos devuelven null cuando no hay equivalencia posible, y ahí se rechaza el
     * vale con un mensaje en vez de descontar un número inventado.
     *
     * El documento se escribe ANTES de mover el stock, igual que `receivePurchase`: si
     * el descuento falla a media aplicación queda el vale para saber qué pasó, en vez
     * de un movimiento de inventario sin nada que lo explique.
     */
    fun issueMaterial(
        companyId: String,
        branch: Branch,
        ingredient: Ingredient,
        format: PurchaseFormat?,
        quantity: Double,
        /** Unidad de [quantity] cuando es cantidad suelta. Vacío = usar la del stock. */
        looseUnit: String = "",
        reason: String,
        notes: String,
        user: User,
        lotId: String = "",
        shiftId: String = "",
        shiftName: String = ""
    ) {
        if (quantity <= 0.0) return
        val baseUnit = ingredient.effectiveBaseUnit
        if (baseUnit.isBlank()) {
            _uiState.update {
                it.copy(error = "${ingredient.name} no tiene unidad de medida configurada.")
            }
            return
        }
        val effectiveLooseUnit = looseUnit.ifBlank { baseUnit }
        val baseQuantity = if (format != null) {
            packagesToBaseUnitOrNull(quantity, format, ingredient) ?: run {
                _uiState.update {
                    it.copy(
                        error = "No se puede pasar \"${format.description}\" (${format.unit}) a " +
                            "\"$baseUnit\". Revisá la presentación en Insumos."
                    )
                }
                return
            }
        } else {
            looseToBaseUnitOrNull(quantity, effectiveLooseUnit, ingredient) ?: run {
                _uiState.update {
                    it.copy(
                        error = "No se puede pasar \"$effectiveLooseUnit\" a \"$baseUnit\" en " +
                            "${ingredient.name}. Revisá las unidades del insumo."
                    )
                }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, message = null) }
            runCatching {
                val prevStock = runCatching {
                    ingredientRepository.getBranchStocksForIngredient(companyId, ingredient.id, listOf(branch.id))
                        .find { it.branchId == branch.id }?.currentStock
                }.getOrNull() ?: (_uiState.value.stockByIngredient[ingredient.id] ?: 0.0)

                val issue = plantIssueRepository.createIssue(
                    companyId,
                    PlantIssue(
                        branchId = branch.id,
                        branchName = branch.name,
                        ingredientId = ingredient.id,
                        ingredientName = ingredient.name,
                        formatId = format?.id ?: "",
                        formatDescription = format?.description ?: "",
                        unitsPerPackage = format?.unitsPerPackage ?: 0.0,
                        formatUnit = format?.unit ?: "",
                        packageQuantity = if (format != null) quantity else 0.0,
                        // Snapshot de lo tecleado: sin esto un vale de "8 oz" se relee
                        // como "0.5 lb" y deja de parecerse a lo que la persona anotó.
                        looseQuantity = if (format == null) quantity else 0.0,
                        looseUnit = if (format == null) effectiveLooseUnit else "",
                        quantityInBaseUnit = baseQuantity,
                        baseUnit = baseUnit,
                        reason = reason,
                        notes = notes.trim(),
                        lotId = lotId,
                        shiftId = shiftId,
                        shiftName = shiftName,
                        issuedByUserId = user.id,
                        issuedByUserName = user.displayName,
                        issuedAt = Clock.System.now().toEpochMilliseconds()
                    )
                )

                applyStock(companyId, ingredient, branch, -baseQuantity, prevStock, issue, isReversal = false)
                issue
            }.onSuccess { issue ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        lastSavedIssue = issue,
                        message = "Anotado: ${issue.displayQuantity} de ${ingredient.name}"
                    )
                }
                _uiState.value.selectedBranch?.let { refreshBranchContext(companyId, it) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isSaving = false, error = e.message ?: "No se pudo registrar la salida")
                }
            }
        }
    }

    /**
     * Deshace un vale: devuelve el material al stock y borra el documento.
     *
     * El Kardex NO se limpia — se le escribe el reverso, igual que la corrección de un
     * gasto de caja o `applyStockDelta`. Un movimiento borrado deja el saldo cuadrando
     * contra una historia que ya no dice qué pasó, que es justo lo que alguien busca
     * cuando el conteo no da.
     */
    fun undoIssue(companyId: String, issue: PlantIssue, user: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, message = null) }
            runCatching {
                val ingredient = _uiState.value.ingredients.find { it.id == issue.ingredientId }
                    ?: Ingredient(id = issue.ingredientId, name = issue.ingredientName)
                val branch = _uiState.value.accessibleBranches.find { it.id == issue.branchId }
                    ?: Branch(id = issue.branchId, name = issue.branchName)
                val prevStock = runCatching {
                    ingredientRepository.getBranchStocksForIngredient(companyId, issue.ingredientId, listOf(issue.branchId))
                        .find { it.branchId == issue.branchId }?.currentStock
                }.getOrNull() ?: 0.0

                applyStock(
                    companyId, ingredient, branch,
                    delta = issue.quantityInBaseUnit,
                    prevStock = prevStock,
                    issue = issue,
                    isReversal = true,
                    actorName = user.displayName
                )
                plantIssueRepository.deleteIssue(companyId, issue.id)
            }.onSuccess {
                _uiState.update {
                    it.copy(isSaving = false, message = "Devuelto: ${issue.displayQuantity} de ${issue.ingredientName}")
                }
                _uiState.value.selectedBranch?.let { refreshBranchContext(companyId, it) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "No se pudo deshacer la salida") }
            }
        }
    }

    private suspend fun applyStock(
        companyId: String,
        ingredient: Ingredient,
        branch: Branch,
        delta: Double,
        prevStock: Double,
        issue: PlantIssue,
        isReversal: Boolean,
        actorName: String = ""
    ) {
        ingredientRepository.updateBranchStock(
            companyId, ingredient.id, ingredient.name, branch.id, branch.name, delta
        )
        ingredientRepository.addStockMovement(
            companyId, ingredient.id,
            StockMovement(
                type = if (isReversal) "AJUSTE" else PLANT_ISSUE_MOVEMENT_TYPE,
                quantity = delta,
                balanceBefore = prevStock,
                balanceAfter = (prevStock + delta).coerceAtLeast(0.0),
                branchId = branch.id,
                branchName = branch.name,
                reference = issue.id,
                date = Clock.System.now().toEpochMilliseconds(),
                notes = if (isReversal)
                    "Salida anulada: ${issue.kardexNote}" +
                        (if (actorName.isNotBlank()) " · $actorName" else "")
                else issue.kardexNote +
                    (if (issue.issuedByUserName.isNotBlank()) " · ${issue.issuedByUserName}" else "")
            )
        )
    }

    /**
     * Carga los vales de un período para el historial y el reporte. El rango se
     * filtra en memoria en el repositorio (ver [PlantIssueRepository.getIssuesInRange]).
     */
    fun loadPeriod(companyId: String, user: User, fromMillis: Long, untilMillis: Long, branchId: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val branchIds = if (branchId.isNotBlank()) listOf(branchId)
                else _uiState.value.accessibleBranches.map { it.id }
            val issues = runCatching {
                plantIssueRepository.getIssuesInRange(companyId, branchIds, fromMillis, untilMillis)
            }.getOrElse { emptyList() }
            _uiState.update {
                it.copy(
                    periodIssues = issues,
                    summaries = summarizePlantIssues(issues),
                    isLoading = false
                )
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null, lastSavedIssue = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }
}

/**
 * Tipo de movimiento del Kardex para las salidas de planta.
 *
 * Es un tipo propio y no "AJUSTE" porque el ajuste documenta una CORRECCIÓN (el conteo
 * no cuadraba, alguien tecleó mal), mientras que esto documenta un CONSUMO real que la
 * receta no explica. Mezclarlos haría imposible el reporte que originó el módulo: no se
 * podría separar "la harina que se fue en la mesa" de "el saldo que estaba mal".
 */
const val PLANT_ISSUE_MOVEMENT_TYPE = "CONSUMO"

/** Etiqueta legible de un tipo de movimiento del Kardex. */
fun stockMovementTypeLabel(type: String): String = when (type) {
    "COMPRA" -> "Compra"
    "PRODUCCION" -> "Producción"
    "AJUSTE" -> "Ajuste"
    "VENTA" -> "Venta"
    PLANT_ISSUE_MOVEMENT_TYPE -> "Consumo de planta"
    STOCK_TRANSFER_MOVEMENT_TYPE -> "Traslado"
    else -> type
}

/** Motivos ofrecidos en el diálogo, en orden. */
val PLANT_ISSUE_REASONS: List<String> = PlantIssueReason.ALL
