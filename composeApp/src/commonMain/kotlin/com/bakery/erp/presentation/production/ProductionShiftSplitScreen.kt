package com.bakery.erp.presentation.production

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.ProductionOrder
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.ProductionShift
import com.bakery.erp.model.DISPATCH_TRIP_FIRST
import com.bakery.erp.model.RecipeBatchCheck
import com.bakery.erp.model.checkRecipeBatchMinimums
import com.bakery.erp.model.flourGramsAsPounds
import com.bakery.erp.model.flourGramsForLines
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.util.formatBakerWeight
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Accent as OrangeAccent
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionShiftSplitScreen(
    presetBranchId: String,
    presetDate: Long,
    onNavigateBack: () -> Unit,
    onNavigateToLotDetail: (lotId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel,
    productViewModel: ProductViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val productState by productViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }
    var notesText by rememberSaveable { mutableStateOf("") }
    // Boolean = true si el chequeo pendiente es del turno A.
    var pendingMinimumCheck by remember { mutableStateOf<Pair<RecipeBatchCheck, Boolean>?>(null) }
    // Masa que nadie pidió directamente en este turno (solo relleno de otro
    // producto) y que además no llega al mínimo: no hay línea propia a la que
    // subirle cantidad, así que MinimumBatchDecisionDialog no aplica.
    var pendingFillingOnlyCheck by remember { mutableStateOf<RecipeBatchCheck?>(null) }

    val branch = remember(state.producingBranches, presetBranchId) {
        state.producingBranches.find { it.id == presetBranchId }
    }
    val shifts = state.shiftsForSelectedProducingBranch

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) {
            if (state.producingBranches.isEmpty()) viewModel.loadOrderFormData(companyId)
            viewModel.loadShiftsForProducingBranch(companyId, presetBranchId)
            productViewModel.loadProducts(companyId)
        }
    }

    // Cuando ya tenemos exactamente 2 turnos activos y el catálogo de productos,
    // calculamos el reparto inicial. Si la config cambió (ya no son 2 turnos)
    // dejamos que la pantalla lo muestre como error en vez de calcular con datos
    // que no aplican.
    LaunchedEffect(shifts, productState.productList, companyId) {
        if (companyId.isEmpty() || shifts.size != 2 || productState.productList.isEmpty()) return@LaunchedEffect
        viewModel.loadShiftSplitData(
            companyId, presetBranchId, presetDate,
            shifts[0], shifts[1], productState.productList
        )
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    pendingMinimumCheck?.let { (check, isShiftA) ->
        MinimumBatchDecisionDialog(
            check = check,
            orders = state.shiftSplitPendingOrders,
            linesByOrder = state.shiftSplitOrderLinesMap,
            onDismiss = { pendingMinimumCheck = null },
            onBakeMinimum = { totals, retained, extras ->
                viewModel.resolveShiftSplitMinimumByBaking(
                    companyId = companyId,
                    editedShiftIsA = isShiftA,
                    totalByProduct = totals,
                    retainedByProduct = retained,
                    extraByOrder = extras.entries
                        .groupBy { it.key.orderId }
                        .mapValues { (_, entries) ->
                            entries.associate { it.key.productId to it.value }
                        }
                )
                pendingMinimumCheck = null
            },
            onCancelProducts = { productIds, orderIds ->
                viewModel.resolveShiftSplitMinimumByCancelling(companyId, productIds, orderIds)
                pendingMinimumCheck = null
            }
        )
    }

    pendingFillingOnlyCheck?.let { check ->
        AlertDialog(
            onDismissRequest = { pendingFillingOnlyCheck = null },
            title = { Text("«${check.recipeName}» no llega al mínimo") },
            text = {
                Text(
                    "Ninguna sucursal pidió ${check.recipeName} directamente — esta masa solo se " +
                        "necesita como relleno de otro producto en este turno. Con eso se mojarían " +
                        check.currentFlourGrams.flourGramsAsPounds() +
                        " de harina, y la amasadora necesita al menos " +
                        check.minFlourGrams.flourGramsAsPounds() + ".\n\n" +
                        "No hay un pedido propio de ${check.recipeName} al que subirle cantidad en " +
                        "este turno, así que hay que decidir a mano: agregá ${check.recipeName} al " +
                        "pedido de alguna sucursal, ajustá el producto que lleva el relleno, o movelo " +
                        "al otro turno con las cajas de cantidad."
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingFillingOnlyCheck = null }) { Text("Entendido") }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Repartir turnos", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = BrandColors.Ivory
    ) { padding ->
        when {
            shifts.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
            shifts.size != 2 -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    "Esta planta ya no tiene exactamente 2 turnos activos — el reparto " +
                        "automático solo aplica a ese caso. Volvé y usá el flujo normal de lotes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            state.shiftSplitExistingLots.isNotEmpty() -> ExistingLotsBanner(
                modifier = Modifier.padding(padding).padding(16.dp),
                lots = state.shiftSplitExistingLots,
                onNavigateToLotDetail = onNavigateToLotDetail
            )
            state.isShiftSplitLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
            state.shiftSplitLines.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    "No hay pedidos pendientes para ${branch?.name ?: "esta planta"} en esta fecha.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> ShiftSplitEditor(
                modifier = Modifier.padding(padding),
                state = state,
                shiftA = shifts[0],
                shiftB = shifts[1],
                notes = notesText,
                onNotesChange = { notesText = it },
                onRebalance = { productId, isA, value -> viewModel.rebalanceShiftSplitLine(productId, isA, value) },
                onReassignOrderAllocation = { productId, orderId, value ->
                    viewModel.reassignOrderAllocation(productId, orderId, value)
                },
                onResolveMinimum = { check, isShiftA ->
                    if (check.lines.isEmpty()) pendingFillingOnlyCheck = check
                    else pendingMinimumCheck = check to isShiftA
                },
                onConfirm = {
                    val b = branch ?: return@ShiftSplitEditor
                    val u = user ?: return@ShiftSplitEditor
                    viewModel.saveShiftSplitLots(
                        companyId, u, b, shifts[0], shifts[1], presetDate, notesText
                    )
                }
            )
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }
}

@Composable
private fun ExistingLotsBanner(
    modifier: Modifier = Modifier,
    lots: List<com.bakery.erp.model.ProductionLot>,
    onNavigateToLotDetail: (String) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Ya se repartió esta fecha",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue
        )
        lots.forEach { lot ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            lot.shiftName.ifEmpty { "Turno" },
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            com.bakery.erp.model.ProductionLotStatus.label(lot.status),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1B5E20)
                        )
                    }
                    TextButton(onClick = { onNavigateToLotDetail(lot.id) }) {
                        Text("Ver lote", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftSplitEditor(
    modifier: Modifier = Modifier,
    state: ProductionUiState,
    shiftA: ProductionShift,
    shiftB: ProductionShift,
    notes: String,
    onNotesChange: (String) -> Unit,
    onRebalance: (productId: String, isA: Boolean, value: Double) -> Unit,
    onReassignOrderAllocation: (productId: String, orderId: String, value: Double) -> Unit,
    onResolveMinimum: (check: RecipeBatchCheck, isShiftA: Boolean) -> Unit,
    onConfirm: () -> Unit
) {
    val groups = remember(state.shiftSplitLines) {
        groupByMasa(
            items = state.shiftSplitLines,
            recipeIdOf = { it.recipeId },
            recipeNameOf = { it.recipeName },
            nameOf = { it.productName }
        )
    }
    // Harina base, no peso de masa: es la unidad en la que la planta mide una
    // jornada ("hoy mojamos 8 sacos"). Se suma la harina de cada masa por separado
    // porque el % de harina sale de la receta de cada una.
    val totalFlourA = remember(state.shiftSplitQuantityA, groups, state.recipesById) {
        groups.sumOf { group ->
            state.recipesById[group.key]?.flourGramsForLines(
                lines = group.items,
                quantityOf = { state.shiftSplitQuantityA[it.productId] ?: 0.0 },
                rawWeightResolver = { it.rawWeightGrams }
            ) ?: 0.0
        }
    }
    val totalFlourB = remember(state.shiftSplitQuantityB, groups, state.recipesById) {
        groups.sumOf { group ->
            state.recipesById[group.key]?.flourGramsForLines(
                lines = group.items,
                quantityOf = { state.shiftSplitQuantityB[it.productId] ?: 0.0 },
                rawWeightResolver = { it.rawWeightGrams }
            ) ?: 0.0
        }
    }
    val totalUnitsA = state.shiftSplitQuantityA.values.sum()
    val totalUnitsB = state.shiftSplitQuantityB.values.sum()
    // Cuántas masas distintas amasa cada turno. Es la señal que la encargada usaba
    // para emparejar el trabajo ("de noche hacen menos variedad, les dejo más
    // tostada"): el peso mide mal el esfuerzo, porque las figuras y las masas
    // chicas llevan mucho tiempo con poca masa y pocas unidades.
    val masasA = remember(state.shiftSplitQuantityA, state.shiftSplitLines) {
        state.shiftSplitLines
            .filter { (state.shiftSplitQuantityA[it.productId] ?: 0.0) > 0.0 }
            .map { it.recipeId.ifEmpty { it.productId } }
            .distinct().size
    }
    val masasB = remember(state.shiftSplitQuantityB, state.shiftSplitLines) {
        state.shiftSplitLines
            .filter { (state.shiftSplitQuantityB[it.productId] ?: 0.0) > 0.0 }
            .map { it.recipeId.ifEmpty { it.productId } }
            .distinct().size
    }

    val underMinA = remember(state.shiftSplitQuantityA, state.recipesById) {
        // Nota: llamamos al VM desde acá no es posible (esta función no es
        // miembro), así que estas advertencias las calcula el caller y se pasan
        // por parámetro sería lo ideal; para mantenerlo simple, se recalculan acá
        // con la misma lógica pura ya que checkRecipeBatchMinimums es pública.
        com.bakery.erp.model.checkRecipeBatchMinimums(
            lines = state.shiftSplitLines.mapNotNull { line ->
                val qty = state.shiftSplitQuantityA[line.productId] ?: 0.0
                if (qty <= 0.0) null else line.copy(plannedQuantity = qty, producedQuantity = qty)
            },
            recipesById = state.recipesById,
            rawWeightResolver = { it.rawWeightGrams }
        ).filter { it.isUnderMinimum }
    }
    val underMinB = remember(state.shiftSplitQuantityB, state.recipesById) {
        com.bakery.erp.model.checkRecipeBatchMinimums(
            lines = state.shiftSplitLines.mapNotNull { line ->
                val qty = state.shiftSplitQuantityB[line.productId] ?: 0.0
                if (qty <= 0.0) null else line.copy(plannedQuantity = qty, producedQuantity = qty)
            },
            recipesById = state.recipesById,
            rawWeightResolver = { it.rawWeightGrams }
        ).filter { it.isUnderMinimum }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 900.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (state.shiftSplitConflictingExclusions.isNotEmpty()) {
                    WarningBanner(
                        "Nadie puede producir: ${state.shiftSplitConflictingExclusions.joinToString(", ")}. " +
                            "Se asignaron a ${shiftA.name} — revisá la configuración de turnos."
                    )
                }
                state.shiftSplitTripRestrictionWarnings.forEach { WarningBanner(it) }
                if (state.shiftSplitProductsWithoutWeight.isNotEmpty()) {
                    val names = state.shiftSplitLines
                        .filter { it.productId in state.shiftSplitProductsWithoutWeight }
                        .joinToString(", ") { it.productName }
                    WarningBanner(
                        "Sin peso en crudo — repartidos 50/50, ajustá a mano si hace falta: $names"
                    )
                }
                if (underMinA.isNotEmpty() || underMinB.isNotEmpty()) {
                    Spacer(Modifier.height(if (state.shiftSplitConflictingExclusions.isNotEmpty() || state.shiftSplitProductsWithoutWeight.isNotEmpty()) 4.dp else 0.dp))
                    (underMinA.map { Triple(shiftA.name, it, true) } + underMinB.map { Triple(shiftB.name, it, false) })
                        .forEach { (shiftName, check, isShiftA) ->
                            MinimumBatchWarningCard(
                                shiftName = shiftName,
                                check = check,
                                onResolve = { onResolveMinimum(check, isShiftA) }
                            )
                        }
                }
            }
            item {
                // Sin viaje configurado el orden de las columnas es solo alfabético, y
                // "más en la mañana" no tiene a qué turno agarrarse.
                if (shiftA.dispatchOrder <= 0 || shiftB.dispatchOrder <= 0) {
                    WarningBanner(
                        "Falta decir cuál turno sale en el primer viaje del camión. Configuralo " +
                            "en cada turno (Producción › Turnos) para que el orden de abajo sea el " +
                            "orden real de salida."
                    )
                }
                if (isWide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShiftTotalCard(shiftA, totalFlourA, totalUnitsA, masasA, Modifier.weight(1f))
                        ShiftTotalCard(shiftB, totalFlourB, totalUnitsB, masasB, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShiftTotalCard(shiftA, totalFlourA, totalUnitsA, masasA, Modifier.fillMaxWidth())
                        ShiftTotalCard(shiftB, totalFlourB, totalUnitsB, masasB, Modifier.fillMaxWidth())
                    }
                }
            }
            groups.forEach { group ->
                item {
                    val recipe = state.recipesById[group.key]
                    val minFlour = recipe?.minBatchFlourGrams?.takeIf { it > 0.0 }
                    val flourA = recipe?.flourGramsForLines(
                        lines = group.items,
                        quantityOf = { state.shiftSplitQuantityA[it.productId] ?: 0.0 },
                        rawWeightResolver = { it.rawWeightGrams }
                    )
                    val flourB = recipe?.flourGramsForLines(
                        lines = group.items,
                        quantityOf = { state.shiftSplitQuantityB[it.productId] ?: 0.0 },
                        rawWeightResolver = { it.rawWeightGrams }
                    )
                    MasaGroupHeader(
                        title = group.title,
                        shiftAName = shiftA.name,
                        shiftBName = shiftB.name,
                        flourA = flourA,
                        flourB = flourB,
                        minFlour = minFlour
                    )
                }
                items(group.items, key = { it.productId }) { line ->
                    val contributors = remember(line.productId, state.shiftSplitOrderLinesMap, state.shiftSplitPendingOrders) {
                        contributingOrdersFor(line.productId, state.shiftSplitOrderLinesMap, state.shiftSplitPendingOrders)
                    }
                    ShiftSplitRow(
                        line = line,
                        shiftAName = shiftA.name,
                        shiftBName = shiftB.name,
                        excludedFromA = line.productId in shiftA.excludedProductIds,
                        excludedFromB = line.productId in shiftB.excludedProductIds,
                        quantityA = state.shiftSplitQuantityA[line.productId] ?: 0.0,
                        quantityB = state.shiftSplitQuantityB[line.productId] ?: 0.0,
                        onChangeA = { onRebalance(line.productId, true, it) },
                        onChangeB = { onRebalance(line.productId, false, it) },
                        contributors = contributors,
                        allocationA = state.shiftSplitOrderAllocationA[line.productId] ?: emptyMap(),
                        earlyNotes = state.shiftSplitEarlyNotes[line.productId] ?: emptyMap(),
                        pinnedA = state.shiftSplitPinnedA[line.productId] ?: emptyMap(),
                        restrictedTrip = state.shiftSplitRestrictedTrip,
                        shiftATrip = shiftA.dispatchOrder,
                        onReassign = { orderId, value ->
                            onReassignOrderAllocation(line.productId, orderId, value)
                        }
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text("Notas (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
            item {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Crear ambos lotes →", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Encabezado de cada masa dentro del reparto: además del nombre, muestra cuánta
 * harina base moja cada turno con lo que lleva ahora mismo — es la cifra que
 * responde "¿me alcanza para amasar?" sin tener que sumar producto por producto.
 * Si un turno no llega al mínimo configurado en la receta, se marca en naranja
 * con cuánto falta, justo arriba de las cajas editables de esa masa.
 */
@Composable
private fun MasaGroupHeader(
    title: String,
    shiftAName: String,
    shiftBName: String,
    flourA: Double?,
    flourB: Double?,
    minFlour: Double?
) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue
        )
        if (flourA != null || flourB != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                FlourShiftLabel(shiftAName, flourA, minFlour)
                FlourShiftLabel(shiftBName, flourB, minFlour)
            }
        }
    }
}

@Composable
private fun FlourShiftLabel(shiftName: String, flour: Double?, minFlour: Double?) {
    if (flour == null) return
    val underMinimum = minFlour != null && flour < minFlour
    val text = if (underMinimum) {
        "$shiftName: ${flour.flourGramsAsPounds()} harina — faltan " +
            "${(minFlour!! - flour).flourGramsAsPounds()} para el mínimo (${minFlour.flourGramsAsPounds()})"
    } else {
        "$shiftName: ${flour.flourGramsAsPounds()} harina"
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (underMinimum) Color(0xFFB35B00) else Color.Gray,
        fontWeight = if (underMinimum) FontWeight.SemiBold else FontWeight.Normal
    )
}

/**
 * Aviso de mínimo de amasada no alcanzado en un turno, con botón para abrir
 * [MinimumBatchDecisionDialog] y decidir si se hornea el mínimo (repartiendo el
 * excedente a alguna sucursal) o se anula el producto — igual que la planta de
 * un solo turno, pero acotado a la masa de ESE turno específico.
 */
@Composable
private fun MinimumBatchWarningCard(shiftName: String, check: RecipeBatchCheck, onResolve: () -> Unit) {
    Surface(
        color = Color(0xFFFFF3E0),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$shiftName: ${check.recipeName} no alcanza el mínimo de amasada",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandColors.AccentDeep
                )
                Text(
                    "Hay ${check.currentFlourGrams.flourGramsAsPounds()}, faltan " +
                        "${(check.minFlourGrams - check.currentFlourGrams).flourGramsAsPounds()} " +
                        "para llegar a ${check.minFlourGrams.flourGramsAsPounds()}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.AccentDeep
                )
            }
            TextButton(onClick = onResolve) {
                Text("Resolver", fontWeight = FontWeight.Bold, color = BrandColors.Primary)
            }
        }
    }
}

@Composable
private fun WarningBanner(text: String) {
    Surface(
        color = Color(0xFFFFF3E0),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = BrandColors.AccentDeep
        )
    }
}

@Composable
private fun ShiftTotalCard(
    shift: ProductionShift,
    totalFlourGrams: Double,
    totalUnits: Double,
    masaCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = PrimaryBlue
    ) {
        Column(Modifier.padding(14.dp)) {
            when (shift.dispatchOrder) {
                1 -> "Primer viaje"
                2 -> "Segundo viaje"
                else -> null
            }?.let { trip ->
                Text(
                    trip,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.AccentDeep
                )
            }
            Text(shift.name, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
            Text(
                totalFlourGrams.flourGramsAsPounds(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "de harina",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f)
            )
            Text(
                "${totalUnits.toInt()} unidades · $masaCount " +
                    if (masaCount == 1) "masa" else "masas",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }
}

/** Una sucursal que pidió este producto — insumo para el reparto por sucursal. */
private data class OrderContribution(
    val orderId: String,
    val branchName: String,
    val requestedQuantity: Double
)

/** Sucursales (con lo que pidieron) que incluyeron [productId] en su pedido del día. */
private fun contributingOrdersFor(
    productId: String,
    orderLinesMap: Map<String, List<ProductionOrderLine>>,
    pendingOrders: List<ProductionOrder>
): List<OrderContribution> =
    pendingOrders.mapNotNull { order ->
        val qty = orderLinesMap[order.id]?.find { it.productId == productId }?.requestedQuantity ?: return@mapNotNull null
        if (qty <= 0.0) null else OrderContribution(order.id, order.sellingBranchName, qty)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftSplitRow(
    line: ProductionLotLine,
    shiftAName: String,
    shiftBName: String,
    excludedFromA: Boolean,
    excludedFromB: Boolean,
    quantityA: Double,
    quantityB: Double,
    onChangeA: (Double) -> Unit,
    onChangeB: (Double) -> Unit,
    contributors: List<OrderContribution>,
    allocationA: Map<String, Double>,
    earlyNotes: Map<String, String>,
    pinnedA: Map<String, Double>,
    /** `orderId → viaje` de las sucursales que solo reciben en uno de los dos. */
    restrictedTrip: Map<String, Int>,
    /** Viaje que cubre la columna izquierda ([ProductionShift.dispatchOrder] del turno A). */
    shiftATrip: Int,
    onReassign: (orderId: String, value: Double) -> Unit
) {
    var showReassignDialog by remember { mutableStateOf(false) }
    // Sin viaje configurado, la columna izquierda es la del primer viaje por
    // convención de la pantalla (ya hay un aviso arriba pidiendo configurarlo).
    val tripOfColumnA = if (shiftATrip > 0) shiftATrip else DISPATCH_TRIP_FIRST

    if (showReassignDialog) {
        OrderAllocationDialog(
            productName = line.productName,
            shiftAName = shiftAName,
            shiftBName = shiftBName,
            contributors = contributors,
            allocationA = allocationA,
            earlyNotes = earlyNotes,
            pinnedA = pinnedA,
            restrictedTrip = restrictedTrip,
            tripOfColumnA = tripOfColumnA,
            onChangeA = onReassign,
            onDismiss = { showReassignDialog = false }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF0F0F0)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(line.productName, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                if (contributors.size >= 2 && !excludedFromA && !excludedFromB) {
                    TextButton(onClick = { showReassignDialog = true }) {
                        Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Por sucursal", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            // Sucursales que solo reciben en un viaje: se ve sin abrir el diálogo,
            // porque explica por qué su número está en cero de un lado.
            contributors.filter { it.orderId in restrictedTrip }.forEach { contributor ->
                val shiftName = if (restrictedTrip[contributor.orderId] == tripOfColumnA) shiftAName else shiftBName
                Surface(
                    color = Color(0xFFEAF2F8),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${contributor.branchName} solo recibe en $shiftName — sus " +
                            "${contributor.requestedQuantity.toInt()} van completas ahí",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            // Encargos que la sucursal dejó por escrito en su pedido: se ven sin
            // tener que abrir el diálogo, que es el punto de no tener que llamarla.
            pinnedA.forEach { (orderId, qty) ->
                if (orderId in restrictedTrip) return@forEach
                val branch = contributors.find { it.orderId == orderId }?.branchName ?: return@forEach
                val note = earlyNotes[orderId]
                Surface(
                    color = Color(0xFFFBF1DC),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "$branch pidió ${qty.toInt()} en $shiftAName" +
                            if (!note.isNullOrBlank()) " — $note" else "",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.AccentDeep,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShiftQuantityField(
                    label = shiftAName,
                    value = quantityA,
                    excluded = excludedFromA,
                    onChange = onChangeA,
                    modifier = Modifier.weight(1f)
                )
                ShiftQuantityField(
                    label = shiftBName,
                    value = quantityB,
                    excluded = excludedFromB,
                    onChange = onChangeB,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Reasignación manual por sucursal: por defecto cada sucursal recibe una porción
 * proporcional a lo que pidió ([allocationA], calculada en el ViewModel), pero acá
 * se puede fijar una sucursal puntual (ej. tiene un encargo especial) sin afectar
 * el total ya decidido para el producto — las demás sucursales absorben la
 * diferencia proporcionalmente entre ellas (ver `rebalanceOrderAllocation`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderAllocationDialog(
    productName: String,
    shiftAName: String,
    shiftBName: String,
    contributors: List<OrderContribution>,
    allocationA: Map<String, Double>,
    earlyNotes: Map<String, String>,
    pinnedA: Map<String, Double>,
    restrictedTrip: Map<String, Int>,
    tripOfColumnA: Int,
    onChangeA: (orderId: String, value: Double) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repartir $productName por sucursal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Cada sucursal decide cuánto necesita en cada viaje. Lo que le pongas " +
                        "a una NO se le quita a las demás: el turno hornea más o menos según " +
                        "la suma.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                contributors.forEach { contributor ->
                    val qtyA = allocationA[contributor.orderId] ?: 0.0
                    val qtyB = (contributor.requestedQuantity - qtyA).coerceAtLeast(0.0)
                    val declared = pinnedA[contributor.orderId]
                    val restriction = restrictedTrip[contributor.orderId]
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${contributor.branchName} — pidió ${contributor.requestedQuantity.toInt()}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (restriction != null) {
                            // Sin campos editables: el viaje único es el horario de la
                            // sucursal, no una decisión de este día. Se cambia en la
                            // ficha de la sucursal, no acá.
                            val shiftName = if (restriction == tripOfColumnA) shiftAName else shiftBName
                            Text(
                                "Solo recibe en $shiftName — sus ${contributor.requestedQuantity.toInt()} " +
                                    "van completas en ese viaje",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            if (declared != null) {
                                val note = earlyNotes[contributor.orderId]
                                Text(
                                    "Pidió ${declared.toInt()} en el primer viaje" +
                                        if (!note.isNullOrBlank()) " · $note" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandColors.AccentDeep,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShiftQuantityField(
                                    label = shiftAName,
                                    value = qtyA,
                                    excluded = false,
                                    onChange = { onChangeA(contributor.orderId, it) },
                                    modifier = Modifier.weight(1f)
                                )
                                ShiftQuantityField(
                                    label = shiftBName,
                                    value = qtyB,
                                    excluded = false,
                                    onChange = { onChangeA(contributor.orderId, contributor.requestedQuantity - it) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Listo") }
        }
    )
}

@Composable
private fun ShiftQuantityField(
    label: String,
    value: Double,
    excluded: Boolean,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    if (excluded) {
        Surface(
            modifier = modifier.height(56.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF6F4EE)
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("No se hace en este turno", style = MaterialTheme.typography.labelSmall, color = BrandColors.AccentDeep)
            }
        }
        return
    }
    var text by remember(value) {
        mutableStateOf(if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString())
    }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.replace(',', '.').toDoubleOrNull()?.let(onChange)
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}
