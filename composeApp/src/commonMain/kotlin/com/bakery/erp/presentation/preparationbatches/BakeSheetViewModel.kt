package com.bakery.erp.presentation.preparationbatches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.PreparationBatchRepository
import com.bakery.erp.domain.PreparationRepository
import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.model.BakeMold
import com.bakery.erp.model.BakeRow
import com.bakery.erp.model.BaseDemand
import com.bakery.erp.model.BatterMix
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Preparation
import com.bakery.erp.model.PreparationBatch
import com.bakery.erp.model.PreparationUsage
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.ProductionOrderStatus
import com.bakery.erp.model.StockMovement
import com.bakery.erp.model.User
import com.bakery.erp.model.baseDemand
import com.bakery.erp.model.baseStock
import com.bakery.erp.model.batterMixes
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.lotBaseDemand
import com.bakery.erp.model.gramsToUnitOrNull
import com.bakery.erp.model.toGramsOrNull
import com.bakery.erp.presentation.transfers.startOfDayMillis
import com.bakery.erp.util.fmtTrim
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

/** Un insumo de la masa a batir, ya en la unidad del inventario, contra lo que hay. */
data class BakeIngredientNeed(
    val ingredientId: String,
    val ingredientName: String,
    /** Null = no se pudo pasar a la unidad del inventario (falta el peso de una unidad). */
    val quantity: Double?,
    val unit: String,
    val available: Double
) {
    val short: Boolean get() = quantity != null && available + 1e-6 < quantity
}

data class BakeSheetUiState(
    val plants: List<Branch> = emptyList(),
    val plant: Branch? = null,
    /** Para cuándo son los pedidos que se miran: 0 hoy, 1 mañana (lo normal: hornear hoy, decorar frío mañana). */
    val dayOffset: Int = 1,
    val rows: List<BakeRow> = emptyList(),
    /** Molde → "voy a hacer" tal como se tecleó. Vive en el ViewModel: sobrevive a irse a hornear. */
    val toBake: Map<String, String> = emptyMap(),
    /** Masas con sus insumos (la lista de preparaciones no los trae). */
    val battersById: Map<String, Preparation> = emptyMap(),
    val ingredientsById: Map<String, Ingredient> = emptyMap(),
    /** Saldo de insumos de la planta, en la unidad de cada insumo. */
    val plantStock: Map<String, Double> = emptyMap(),
    /** Moldes sin masa o sin onzas: no se pueden hornear desde acá hasta configurarlos. */
    val unconfigured: List<String> = emptyList(),
    /** Une los lotes de esta horneada; también evita anotarla dos veces. */
    val bakeId: String = "",
    /**
     * Lote de producción desde el que se mira (vacío = la hoja por día). Dentro del lote lo
     * que piden sale de sus líneas, no de los pedidos del día.
     */
    val lotId: String = "",
    /** Moldes de la planta: dentro del lote dicen de qué masa es cada pastel. */
    val basesById: Map<String, Preparation> = emptyMap(),
    /** Masas cuyo "voy a hacer" ya decidió el repostero en el diálogo (dentro del lote). */
    val confirmedBatterIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null
) {
    val plannedMolds: List<BakeMold>
        get() = rows.mapNotNull { r ->
            val units = toBake[r.base.id]?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0.0 }
            units?.let { BakeMold(r.base, it) }
        }
    val mixes: List<BatterMix> get() = batterMixes(plannedMolds, battersById)

    /** Lo que se descuenta del inventario, sumando todas las masas (dos masas comparten huevo). */
    val ingredientNeeds: List<BakeIngredientNeed> get() = needsFor(mixes)

    /** Lo que se descuenta por [mixes]: dentro del lote cada masa se anota por su lado. */
    fun needsFor(mixes: List<BatterMix>): List<BakeIngredientNeed> =
        mixes.flatMap { it.scaledIngredients }
            .groupBy { it.first.ingredientId }
            .map { (id, entries) ->
                val ing = ingredientsById[id]
                val unit = ing?.effectiveBaseUnit?.ifEmpty { null } ?: entries.first().first.unit
                // Cada línea se pasa por gramos con su propio peso unitario: "6 unidad" y
                // "300 gr" de huevo se suman bien.
                val parts = entries.map { (pi, qty) ->
                    val uw = pi.unitWeightGrams.takeIf { it > 0.0 } ?: ing?.unitWeightGrams ?: 0.0
                    toGramsOrNull(qty, pi.unit, uw)?.let { gramsToUnitOrNull(it, unit, uw) }
                }
                BakeIngredientNeed(
                    ingredientId = id,
                    ingredientName = ing?.name ?: entries.first().first.ingredientName,
                    quantity = if (parts.any { it == null }) null else parts.sumOf { it ?: 0.0 },
                    unit = unit,
                    available = plantStock[id] ?: 0.0
                )
            }
            .sortedBy { it.ingredientName.lowercase() }
}

/**
 * Hoja de horneado de bizcochos: pedido vs. lo que hay frío → "voy a hacer" → masa a batir →
 * lo que salió. Ver "Bizcochos" en `arquitectura/catalogo.md`.
 */
class BakeSheetViewModel(
    private val preparationRepository: PreparationRepository,
    private val batchRepository: PreparationBatchRepository,
    private val ingredientRepository: IngredientRepository,
    private val branchRepository: BranchRepository,
    private val productionRepository: ProductionRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BakeSheetUiState())
    val uiState: StateFlow<BakeSheetUiState> = _uiState.asStateFlow()

    fun load(companyId: String, user: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val plants = runCatching { branchRepository.getBranches(companyId) }.getOrElse { emptyList() }
                .filter { it.active && user.canAccessBranch(it.id) && BranchCapability.CAN_PRODUCE in it.capabilities }
                .sortedBy { it.name.lowercase() }
            val current = _uiState.value.plant?.takeIf { p -> plants.any { it.id == p.id } }
            _uiState.update {
                it.copy(plants = plants, plant = current ?: plants.singleOrNull(), isLoading = false)
            }
            refresh(companyId, keepPlan = true)
        }
    }

    fun selectPlant(companyId: String, plant: Branch) {
        if (plant.id == _uiState.value.plant?.id) return
        _uiState.update { it.copy(plant = plant, toBake = emptyMap(), rows = emptyList()) }
        refresh(companyId, keepPlan = false)
    }

    fun selectDay(companyId: String, offset: Int) {
        if (offset == _uiState.value.dayOffset) return
        _uiState.update { it.copy(dayOffset = offset) }
        refresh(companyId, keepPlan = false)
    }

    fun setToBake(baseId: String, text: String) {
        val clean = text.filter { it.isDigit() || it == '.' || it == ',' }
        _uiState.update { it.copy(toBake = it.toBake + (baseId to clean)) }
    }

    /** El repostero decidió cuánto hornear de una masa (diálogo del lote). */
    fun confirmBatterPlan(batterId: String, values: Map<String, String>) {
        val clean = values.mapValues { (_, v) -> v.filter { it.isDigit() || it == '.' || it == ',' } }
        _uiState.update { it.copy(toBake = it.toBake + clean, confirmedBatterIds = it.confirmedBatterIds + batterId) }
    }

    // ── Dentro del lote de producción ────────────────────────────────────────
    // El repostero ve el pedido del día en su lote y decide ahí cuánto hornear, como la
    // esponja. Lo que piden sale de las líneas del lote; moldes y saldo se leen una vez y
    // se recalculan al cambiar las líneas (llegan en vivo).

    private var lotLines: List<ProductionLotLine> = emptyList()
    private var cachedBases: List<Preparation> = emptyList()
    private var cachedStock: Map<String, Pair<Double, Long>> = emptyMap()

    /** Abre el lote [lotId] de la planta [plantId]. Otro lote empieza sin lo tecleado. */
    fun loadForLot(companyId: String, plantId: String, lotId: String, lines: List<ProductionLotLine>) {
        lotLines = lines
        if (lotId == _uiState.value.lotId && _uiState.value.plant?.id == plantId) {
            recomputeRows(keepPlan = true)
            return
        }
        viewModelScope.launch {
            val plant = runCatching { branchRepository.getBranches(companyId) }.getOrElse { emptyList() }
                .firstOrNull { it.id == plantId } ?: return@launch
            cachedBases = emptyList()
            cachedStock = emptyMap()
            _uiState.update {
                BakeSheetUiState(plants = listOf(plant), plant = plant, lotId = lotId, dayOffset = 0)
            }
            refresh(companyId, keepPlan = false)
        }
    }

    /** Las líneas del lote cambiaron (conteo, pan de más, pedido tardío). */
    fun setLotLines(lines: List<ProductionLotLine>) {
        if (_uiState.value.lotId.isEmpty() || lines == lotLines) return
        lotLines = lines
        recomputeRows(keepPlan = true)
    }

    /**
     * Filas del lote: los moldes que el lote pide y los que quedaron debajo de lo que hay que
     * tener (para que la reserva se reponga aunque hoy no los pidan).
     */
    private fun lotRows(): List<BakeRow> {
        val demand = lotBaseDemand(lotLines)
        return cachedBases.map { b ->
            val (onHand, oldest) = cachedStock[b.id] ?: (0.0 to 0L)
            BakeRow(b, demand[b.id] ?: BaseDemand(), onHand, oldest)
        }.filter { it.demand.units > 0.0 || it.suggested > 0 }
            .sortedWith(compareBy({ it.base.batterPreparationName.lowercase() }, { it.base.name.lowercase() }))
    }

    private fun recomputeRows(keepPlan: Boolean) {
        if (cachedBases.isEmpty()) return
        val rows = lotRows()
        _uiState.update { it.copy(rows = rows, toBake = planFor(rows, if (keepPlan) it.toBake else emptyMap())) }
    }

    /** Lo tecleado se respeta; los moldes nuevos llegan con la sugerencia. */
    private fun planFor(rows: List<BakeRow>, previous: Map<String, String>): Map<String, String> =
        rows.associate { r ->
            r.base.id to (previous[r.base.id] ?: r.suggested.takeIf { it > 0 }?.toString().orEmpty())
        }

    /**
     * Relee pedidos, saldo de bizcochos e insumos. Con [keepPlan] lo que ya se tecleó se
     * respeta; los moldes nuevos llegan con la sugerencia.
     */
    fun refresh(companyId: String, keepPlan: Boolean) {
        val plant = _uiState.value.plant ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                coroutineScope {
                    val prepsD = async { preparationRepository.getPreparations(companyId) }
                    val productsD = async { productRepository.getProducts(companyId) }
                    val batchesD = async { batchRepository.listActiveBatches(companyId, plant.id) }
                    val ingredientsD = async { ingredientRepository.getIngredients(companyId) }
                    val stockD = async { ingredientRepository.getBranchStocksForBranch(companyId, plant.id) }
                    val from = startOfDayMillis(_uiState.value.dayOffset)
                    val until = startOfDayMillis(_uiState.value.dayOffset + 1)
                    val ordersD = async {
                        productionRepository.getOrdersForProducingBranchInRange(companyId, plant.id, from, until)
                            .filter { it.status != ProductionOrderStatus.CANCELLED }
                    }

                    val preps = prepsD.await()
                    val bases = preps.filter { it.active && it.usage == PreparationUsage.BASE && it.isMadeIn(plant.id) }
                    val batterIds = bases.map { it.batterPreparationId }.filter { it.isNotEmpty() }.distinct()
                    val batters = batterIds.map { id ->
                        async { runCatching { preparationRepository.getPreparation(companyId, id) }.getOrNull() }
                    }.awaitAll().filterNotNull().associateBy { it.id }

                    val stock = baseStock(batchesD.await())
                    val rows = if (_uiState.value.lotId.isNotEmpty()) {
                        cachedBases = bases
                        cachedStock = stock
                        lotRows()
                    } else {
                        val lines = ordersD.await().map { o ->
                            async { runCatching { productionRepository.getOrderLines(companyId, o.id) }.getOrElse { emptyList() } }
                        }.awaitAll().flatten().map { it.productId to it.requestedQuantity }
                        val demand = baseDemand(lines, productsD.await().associateBy { it.id })
                        bases.map { b ->
                            val (onHand, oldest) = stock[b.id] ?: (0.0 to 0L)
                            BakeRow(b, demand[b.id] ?: BaseDemand(), onHand, oldest)
                        }.sortedWith(compareBy({ it.base.batterPreparationName.lowercase() }, { it.base.name.lowercase() }))
                    }

                    val toBake = planFor(rows, if (keepPlan) _uiState.value.toBake else emptyMap())
                    _uiState.update {
                        it.copy(
                            rows = rows,
                            toBake = toBake,
                            basesById = bases.associateBy { b -> b.id },
                            battersById = batters,
                            ingredientsById = ingredientsD.await().associateBy { i -> i.id },
                            plantStock = stockD.await(),
                            unconfigured = bases.filter { b ->
                                b.batterPreparationId.isEmpty() || b.batterGramsPerUnit <= 0.0 ||
                                    batters[b.batterPreparationId]?.batchWeightGrams?.let { w -> w <= 0.0 } != false
                            }.map { b -> b.name },
                            bakeId = it.bakeId.ifEmpty { generateId() },
                            isLoading = false
                        )
                    }
                }
            }.onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    /**
     * Anota lo que salió del horno: entran los bizcochos (uno por molde con lo contado) y se
     * descuenta la masa que se batió (lo planeado: la mezcla se hizo completa aunque salieran
     * menos). Lotes primero, insumos después: un reintento choca con la guarda de [BakeSheetUiState.bakeId].
     */
    fun finishBake(
        companyId: String,
        user: User,
        produced: Map<String, Double>,
        /** Dentro del lote se anota una masa a la vez; null = todo lo planeado (la hoja). */
        batterId: String? = null
    ) {
        val state = _uiState.value
        val plant = state.plant ?: return
        val mixes = state.mixes.filter { batterId == null || it.batterId == batterId }
        val needs = state.needsFor(mixes)
        if (mixes.isEmpty() || produced.values.none { it > 0.0 }) {
            _uiState.update { it.copy(error = "Anota cuántos salieron") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val bakeId = state.bakeId.ifEmpty { generateId() }
                // Los lotes llevan id fijo (horneada + molde): si alguno ya existe, esta horneada
                // ya se anotó (doble toque, o un corte a medio camino).
                val molds = mixes.flatMap { it.molds }.filter { (produced[it.base.id] ?: 0.0) > 0.0 }
                // (`getBatch` de un doc que no existe puede devolver uno vacío: se mira el molde.)
                val already = molds.any {
                    batchRepository.getBatch(companyId, "${bakeId}_${it.base.id}")?.preparationId?.isNotEmpty() == true
                }
                if (already) throw IllegalStateException("Esta horneada ya se anotó")
                val now = Clock.System.now().toEpochMilliseconds()
                val userName = user.displayName.ifBlank { user.email }

                for (mold in mixes.flatMap { it.molds }) {
                    val units = produced[mold.base.id] ?: continue
                    if (units <= 0.0) continue
                    batchRepository.addBatch(
                        companyId, PreparationBatch(
                            id = "${bakeId}_${mold.base.id}",
                            preparationId = mold.base.id,
                            preparationName = mold.base.name,
                            branchId = plant.id,
                            branchName = plant.name,
                            batchCount = 1,
                            // Masa que se echó (referencia): el bizcocho se cuenta, no se pesa.
                            producedWeightGrams = mold.batterGrams,
                            remainingWeightGrams = 0.0,
                            producedByUserId = user.id,
                            producedByUserName = userName,
                            producedAt = now,
                            producedUnits = units,
                            remainingUnits = units,
                            batterGramsPerUnit = mold.base.batterGramsPerUnit,
                            bakeId = bakeId
                        )
                    )
                }

                val warnings = mutableListOf<String>()
                for (need in needs) {
                    val qty = need.quantity
                    if (qty == null) {
                        warnings += "${need.ingredientName}: no se pudo pasar a ${need.unit}, no se descontó"
                        continue
                    }
                    if (qty <= 0.0) continue
                    val delta = -qty
                    val before = ingredientRepository.updateBranchStock(
                        companyId, need.ingredientId, need.ingredientName, plant.id, plant.name, delta
                    )
                    ingredientRepository.addStockMovement(
                        companyId, need.ingredientId, StockMovement(
                            type = "PRODUCCION",
                            quantity = delta,
                            balanceBefore = before,
                            balanceAfter = (before + delta).coerceAtLeast(0.0),
                            branchId = plant.id,
                            branchName = plant.name,
                            reference = bakeId,
                            date = now,
                            createdByUserId = user.id,
                            createdByUserName = userName,
                            createdByEmployeeId = user.operatorEmployeeId
                        )
                    )
                }
                val total = produced.values.filter { it > 0.0 }.sum()
                "Entraron ${total.fmtTrim(2)} bizcochos" +
                    if (warnings.isEmpty()) "" else ". ${warnings.joinToString("; ")}"
            }.onSuccess { msg ->
                // Lo de las otras masas sigue tecleado: dentro del lote cada una se anota aparte.
                // Los moldes recién anotados vuelven con la sugerencia nueva (ya cuentan lo que entró).
                val done = mixes.flatMap { m -> m.molds.map { it.base.id } }.toSet()
                _uiState.update {
                    it.copy(
                        isSaving = false, savedMessage = msg, toBake = it.toBake - done, bakeId = generateId(),
                        // La próxima vez que la toque vuelve a decidir con lo que hay ahora.
                        confirmedBatterIds = it.confirmedBatterIds - mixes.map { m -> m.batterId }.toSet()
                    )
                }
                refresh(companyId, keepPlan = true)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "No se pudo anotar la horneada") }
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearSaved() { _uiState.update { it.copy(savedMessage = null) } }

    private fun generateId(): String {
        val hex = "0123456789abcdef"
        return Random.nextBytes(16).joinToString("") { b ->
            hex[(b.toInt() shr 4) and 0xF].toString() + hex[b.toInt() and 0xF]
        }
    }
}
