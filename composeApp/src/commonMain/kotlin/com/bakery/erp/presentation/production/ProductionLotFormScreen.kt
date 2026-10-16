package com.bakery.erp.presentation.production

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.PreparationBatch
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductionLotDecoration
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.ProductionShift
import com.bakery.erp.model.RecipeBatchCheck
import com.bakery.erp.model.flourGramsAsPounds
import com.bakery.erp.model.flourGramsForLines
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.toBaseUnit
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.util.fmt
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import com.bakery.erp.ui.theme.BrandColors

private const val GRAMS_PER_LB = 453.592
private fun Double.gramsToLb(): Double = this / GRAMS_PER_LB

/** Banda de masa dentro de la lista consolidada del lote. */
@Composable
private fun MasaBandLabel(title: String, units: Double, flourLabel: String? = null) {
    Surface(
        color = Color(0xFFEAF3F8),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = BrandColors.Primary
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${units.toInt()} u.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary
                )
                if (flourLabel != null) {
                    Text(
                        flourLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.Primary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ── Tabla consolidada para pantallas anchas (tablet/PC) ────────────────────────

@Composable
private fun ConsolidatedTableHeader() {
    Surface(
        color = Color(0xFFF6F4EE),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("#", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = BrandColors.Primary, modifier = Modifier.width(36.dp))
            Text("Producto", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = BrandColors.Primary, modifier = Modifier.weight(2f))
            Text("Receta", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = BrandColors.Primary, modifier = Modifier.weight(1.5f))
            Text("Unidades", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = BrandColors.Primary, modifier = Modifier.width(110.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConsolidatedTableRow(
    index: Int,
    line: ProductionLotLine,
    decorationBatchesByPrep: Map<String, List<PreparationBatch>>,
    onUpdate: (ProductionLotLine) -> Unit
) {
    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$index",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.AccentDeep,
                    modifier = Modifier.width(36.dp)
                )
                Column(modifier = Modifier.weight(2f)) {
                    // El "★" delante marca el pedido especial: la encargada lo
                    // tiene que ver al consolidar, porque es el que puede tener
                    // una figura o un peso que nadie más va a revisar.
                    Text(
                        if (line.isSpecial) "★ ${line.productName}" else line.productName,
                        fontWeight = FontWeight.SemiBold,
                        color = if (line.isSpecial) BrandColors.AccentDeep else BrandColors.Primary
                    )
                    if (line.productUnit.isNotEmpty())
                        Text(line.productUnit, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Text(
                    line.recipeName.ifEmpty { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (line.recipeName.isEmpty()) Color.Gray else Color(0xFF1565C0),
                    modifier = Modifier.weight(1.5f)
                )
                Text(
                    "${line.plannedQuantity.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = BrandColors.AccentDeep,
                    modifier = Modifier.width(110.dp)
                )
            }
            // Adornos (si aplica) — debajo del row principal
            if (line.decorationsSnapshot.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 50.dp, end = 14.dp, top = 4.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    line.decorationsSnapshot.forEachIndexed { idx, deco ->
                        DecorationRow(
                            decoration = deco,
                            plannedQuantity = line.plannedQuantity,
                            candidateBatches = decorationBatchesByPrep[deco.preparationId] ?: emptyList(),
                            onBatchChange = { batch ->
                                val newSnapshot = line.decorationsSnapshot.toMutableList().apply {
                                    set(idx, deco.copy(batchId = batch.id, batchName = batch.preparationName))
                                }
                                onUpdate(line.copy(decorationsSnapshot = newSnapshot))
                            }
                        )
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFF0F0F0))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionLotFormScreen(
    presetBranchId: String? = null,
    presetShiftId: String? = null,
    presetDate: Long? = null,
    onNavigateBack: () -> Unit,
    onNavigateToShiftSplit: (branchId: String, date: Long) -> Unit = { _, _ -> },
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

    // Cuando vienen presets desde el consolidado, planta y turno se muestran como labels
    // (no editables). La fecha también queda fija al valor pasado.
    val hasPresets = !presetBranchId.isNullOrEmpty() && !presetShiftId.isNullOrEmpty()

    var selectedBranch by remember { mutableStateOf<Branch?>(null) }
    var branchDropdownExpanded by remember { mutableStateOf(false) }
    var selectedShift by remember { mutableStateOf<ProductionShift?>(null) }
    var shiftDropdownExpanded by remember { mutableStateOf(false) }
    // Default a "mañana" para coincidir con la fecha por defecto del form de pedido.
    var productionDate by remember { mutableStateOf(presetDate ?: startOfNextDayMillis()) }
    var notesText by rememberSaveable { mutableStateOf("") }

    // Recetas cuyo pedido no alcanza el mínimo de la amasadora. Se recalcula con
    // cada cambio del borrador porque resolver una masa puede afectar a otra que
    // comparta productos.
    val underMinimum = remember(state.draftLotLines, state.recipesById, state.productsById) {
        viewModel.underMinimumRecipes()
    }
    var pendingMinimumCheck by remember { mutableStateOf<RecipeBatchCheck?>(null) }
    // Masa que nadie pidió directamente (solo relleno de otro producto, ver
    // DoughFilling) y que además no llega al mínimo: no hay pedido/producto
    // propio al que subirle cantidad, así que MinimumBatchDecisionDialog no
    // aplica — solo se avisa para que la encargada decida a mano (agregar Pan
    // Suave al pedido de alguna sucursal, o ajustar el producto que rellena).
    var pendingFillingOnlyCheck by remember { mutableStateOf<RecipeBatchCheck?>(null) }
    var showDismissOtherDates by remember { mutableStateOf(false) }

    // La línea del lote ya trae recipeId/recipeName, así que el agrupado no
    // depende del catálogo de recetas — sale de la propia consolidación.
    val draftGroups = remember(state.draftLotLines, selectedBranch?.productSortOrder) {
        val order = selectedBranch?.productSortOrder.orEmpty()
        val itemIndex = order.withIndex().associate { (i, id) -> id to i }
        val recipeIndex = recipeOrderIndexFrom(order) { productId ->
            state.draftLotLines.find { it.productId == productId }?.recipeId
        }
        groupByMasa(
            items = state.draftLotLines,
            recipeIdOf = { it.recipeId },
            recipeNameOf = { it.recipeName },
            nameOf = { it.productName },
            groupOrderIndex = { recipeIndex[it] ?: Int.MAX_VALUE },
            itemOrderIndex = { itemIndex[it.productId] ?: Int.MAX_VALUE }
        )
    }

    if (showDismissOtherDates) {
        val total = state.otherDatePendingOrderIds.size
        AlertDialog(
            onDismissRequest = { showDismissOtherDates = false },
            title = { Text("¿Omitir estos pedidos?") },
            text = {
                Text(
                    "Se van a quitar $total pedido${if (total != 1) "s" else ""} de otras fechas " +
                        "de esta pantalla y no van a volver a aparecer acá.\n\n" +
                        "No se cancelan ni se borran: siguen pendientes y se pueden ver en la " +
                        "lista de pedidos, en la fecha a la que pertenecen. Solo dejan de " +
                        "estorbar al armar lotes nuevos."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissOtherDatePendingOrders(companyId)
                    showDismissOtherDates = false
                }) { Text("Sí, omitir", fontWeight = FontWeight.Bold, color = BrandColors.AccentDeep) }
            },
            dismissButton = {
                TextButton(onClick = { showDismissOtherDates = false }) { Text("Cancelar") }
            }
        )
    }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) {
            viewModel.loadOrderFormData(companyId, user)
            productViewModel.loadProducts(companyId)
        }
    }

    // Hidratar selección desde los presets una vez que llegan las branches y los shifts.
    LaunchedEffect(state.producingBranches, presetBranchId) {
        val targetId = presetBranchId
        if (selectedBranch == null && !targetId.isNullOrEmpty()) {
            selectedBranch = state.producingBranches.find { it.id == targetId }
        }
    }
    LaunchedEffect(state.shifts, presetShiftId) {
        val targetId = presetShiftId
        if (selectedShift == null && !targetId.isNullOrEmpty()) {
            selectedShift = state.shifts.find { it.id == targetId }
        }
    }

    LaunchedEffect(state.producingBranches, user) {
        if (selectedBranch == null && presetBranchId.isNullOrEmpty()) {
            val accessible = state.producingBranches.filter { user?.canAccessBranch(it.id) == true }
            if (accessible.size == 1) selectedBranch = accessible.first()
        }
    }

    pendingMinimumCheck?.let { check ->
        MinimumBatchDecisionDialog(
            check = check,
            orders = state.pendingOrdersForDate,
            linesByOrder = state.pendingOrderLinesMap,
            onDismiss = { pendingMinimumCheck = null },
            onBakeMinimum = { totals, retained, extras ->
                viewModel.resolveMinimumBatchByBaking(
                    companyId = companyId,
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
                viewModel.resolveMinimumBatchByCancelling(
                    companyId = companyId,
                    currentUser = user,
                    recipeName = check.recipeName,
                    productIds = productIds,
                    affectedOrderIds = orderIds
                )
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
                        "necesita como relleno de otro producto. Con eso se mojarían " +
                        check.currentFlourGrams.flourGramsAsPounds() +
                        " de harina, y la amasadora necesita al menos " +
                        check.minFlourGrams.flourGramsAsPounds() + ".\n\n" +
                        "No hay un pedido propio de ${check.recipeName} al que subirle cantidad, así " +
                        "que hay que decidir a mano: agrega ${check.recipeName} al pedido de alguna " +
                        "sucursal, o ajusta el producto que lleva el relleno."
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingFillingOnlyCheck = null }) {
                    Text("Entendido", fontWeight = FontWeight.Bold, color = BrandColors.Primary)
                }
            }
        )
    }

    // Llama loadLotFormData cuando hay branch seleccionada. Si todavía no hay turno
    // (flujo sin presets), el VM solo carga la lista de turnos para que el usuario elija.
    // Cuando ya hay turno, consolida.
    LaunchedEffect(
        selectedBranch?.id,
        productionDate,
        selectedShift?.id,
        productState.productList.isNotEmpty()
    ) {
        val branch = selectedBranch ?: return@LaunchedEffect
        if (companyId.isNotEmpty() && productState.productList.isNotEmpty()) {
            viewModel.loadLotFormData(
                companyId,
                branch.id,
                productionDate,
                selectedShift?.id.orEmpty(),
                productState.productList
            )
            viewModel.updateProductsCache(productState.productList)
        }
    }

    LaunchedEffect(state.shifts) {
        if (selectedShift == null && state.shifts.size == 1) selectedShift = state.shifts.first()
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.resetLotForm(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            BrandTopAppBar(
                title = { Text("Nuevo Lote de Producción", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = BrandColors.Ivory
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val isWide = maxWidth >= 1000.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Sucursal productora ──────────────────────────────────────────
            item {
                if (hasPresets) {
                    InfoLabelCard(
                        label = "Planta productora",
                        value = selectedBranch?.name ?: "—"
                    )
                } else if (state.producingBranches.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = branchDropdownExpanded,
                        onExpandedChange = { branchDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedBranch?.name ?: "Seleccionar planta productora",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Planta productora *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(branchDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = branchDropdownExpanded,
                            onDismissRequest = { branchDropdownExpanded = false }
                        ) {
                            state.producingBranches.filter { user?.canAccessBranch(it.id) == true }
                                .forEach { branch ->
                                    DropdownMenuItem(
                                        text = { Text(branch.name) },
                                        onClick = { selectedBranch = branch; branchDropdownExpanded = false }
                                    )
                                }
                        }
                    }
                } else if (selectedBranch != null) {
                    InfoLabelCard(
                        label = "Planta productora",
                        value = selectedBranch!!.name
                    )
                }
            }

            // ── Turno ────────────────────────────────────────────────────────
            item {
                if (hasPresets) {
                    InfoLabelCard(
                        label = "Turno",
                        value = selectedShift?.let {
                            it.name + if (it.startTime.isNotBlank() && it.endTime.isNotBlank())
                                " (${it.startTime} – ${it.endTime})" else ""
                        } ?: "—"
                    )
                } else if (state.shifts.isEmpty() && selectedBranch != null) {
                    Text(
                        "Sin turnos configurados. Configura turnos desde el menú de Producción.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF57C00)
                    )
                } else if (state.shifts.size == 2 && selectedBranch != null) {
                    // Planta de 2 turnos: no hay nada que elegir acá, el reparto es
                    // automático — reemplaza el dropdown por un botón hacia esa pantalla.
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3F8))) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "${selectedBranch!!.name} reparte automático entre sus 2 turnos",
                                fontWeight = FontWeight.Bold,
                                color = BrandColors.Primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Usá el reparto de turnos para ver cómo queda cada uno y crear " +
                                    "los dos lotes de una vez.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandColors.Primary
                            )
                            Button(
                                onClick = { onNavigateToShiftSplit(selectedBranch!!.id, productionDate) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
                            ) {
                                Text("Ir al reparto de turnos →", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = shiftDropdownExpanded,
                        onExpandedChange = { shiftDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedShift?.name ?: "Seleccionar turno *",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Turno *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(shiftDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = shiftDropdownExpanded,
                            onDismissRequest = { shiftDropdownExpanded = false }
                        ) {
                            state.shifts.forEach { shift ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(shift.name)
                                            if (shift.startTime.isNotEmpty())
                                                Text("${shift.startTime} – ${shift.endTime}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                    },
                                    onClick = { selectedShift = shift; shiftDropdownExpanded = false }
                                )
                            }
                        }
                    }
                }
            }

            // ── Fecha de producción ──────────────────────────────────────────
            item {
                if (hasPresets) {
                    InfoLabelCard(
                        label = "Fecha de producción",
                        value = formatDate(productionDate)
                    )
                } else {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { productionDate -= 86_400_000L }) {
                                Icon(Icons.Default.ChevronLeft, "Día anterior")
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Fecha de producción", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(formatDate(productionDate), fontWeight = FontWeight.SemiBold, color = BrandColors.Primary, style = MaterialTheme.typography.titleMedium)
                            }
                            IconButton(onClick = { productionDate += 86_400_000L }) {
                                Icon(Icons.Default.ChevronRight, "Día siguiente")
                            }
                        }
                    }
                }
            }

            // ── Aviso: pedidos pendientes en otras fechas ────────────────────
            if (state.pendingOrdersInOtherDates.isNotEmpty()) {
                item {
                    Surface(
                        color = Color(0xFFFFF8E1),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Hay pedidos pendientes en otras fechas para esta planta:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandColors.AccentDeep
                            )
                            state.pendingOrdersInOtherDates.entries
                                .sortedBy { it.key }
                                .forEach { (date, count) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            productionDate = date
                                        }.padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "• ${formatDate(date)} — $count pedido${if (count != 1) "s" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF333333)
                                        )
                                        Text(
                                            "Ver →",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF1565C0),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showDismissOtherDates = true }) {
                                    Text(
                                        "Omitir",
                                        color = BrandColors.AccentDeep,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Pedidos pendientes ────────────────────────────────────────────
            if (state.pendingOrdersForDate.isNotEmpty()) {
                item {
                    Text(
                        "Pedidos pendientes (${state.pendingOrdersForDate.size})",
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.Primary
                    )
                }
                items(state.pendingOrdersForDate) { order ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4FF)), shape = RoundedCornerShape(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(order.sellingBranchName, fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
                                Text(
                                    "${formatDate(order.requestedDate)} · ${order.lineCount} productos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            } else if (selectedBranch != null && !state.isLoading) {
                item {
                    Text(
                        "Sin pedidos pendientes para esta fecha. Puedes crear el lote de todas formas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            // ── Recetas bajo el mínimo de la amasadora ────────────────────────
            // Se resuelve ANTES de crear el lote: es el único momento en que la
            // encargada ve toda la demanda del día junta y todavía puede decidir.
            if (underMinimum.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                        border = BorderStroke(1.dp, Color(0xFFFFCC80)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "⚠  ${underMinimum.size} masa(s) no llegan al mínimo de la amasadora",
                                fontWeight = FontWeight.Bold,
                                color = BrandColors.AccentDeep
                            )
                            Text(
                                "Con lo que pidieron no hay suficiente harina para que la máquina " +
                                    "amase. Decide si horneas el mínimo o anulas el producto.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandColors.AccentDeep
                            )
                            underMinimum.forEach { check ->
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                check.recipeName,
                                                fontWeight = FontWeight.SemiBold,
                                                color = BrandColors.Primary
                                            )
                                            Text(
                                                check.currentFlourGrams.flourGramsAsPounds() +
                                                    " de " + check.minFlourGrams.flourGramsAsPounds() +
                                                    " · " + check.lines.joinToString(", ") { it.productName }
                                                        .ifEmpty { "nadie pidió esta masa directamente — solo relleno de otro producto" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                if (check.lines.isEmpty()) pendingFillingOnlyCheck = check
                                                else pendingMinimumCheck = check
                                            }
                                        ) {
                                            Text(
                                                "Resolver",
                                                fontWeight = FontWeight.Bold,
                                                color = BrandColors.Primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Lista consolidada ─────────────────────────────────────────────
            if (state.draftLotLines.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text("Lista consolidada", fontWeight = FontWeight.Bold, color = BrandColors.Primary)
                }
                if (isWide) item { ConsolidatedTableHeader() }
                // Agrupado por masa, con el mismo orden que el resto del flujo:
                // la secuencia configurada por la planta manda sobre las masas.
                var rowNumber = 0
                draftGroups.forEach { group ->
                    item(key = "lotgroup_${group.key}") {
                        val flourGrams = state.recipesById[group.key]?.flourGramsForLines(
                            lines = group.items,
                            quantityOf = { it.plannedQuantity },
                            rawWeightResolver = { it.rawWeightGrams ?: state.productsById[it.productId]?.rawWeightGrams }
                        )
                        MasaBandLabel(
                            title = group.title,
                            units = group.items.sumOf { it.plannedQuantity },
                            flourLabel = flourGrams?.let { "${it.flourGramsAsPounds()} harina" }
                        )
                    }
                    group.items.forEach { line ->
                        rowNumber += 1
                        val number = rowNumber
                        item(key = "lotline_${line.productId}") {
                            if (isWide) {
                                ConsolidatedTableRow(
                                    index = number,
                                    line = line,
                                    decorationBatchesByPrep = state.availableDecorationBatchesByPreparation,
                                    onUpdate = { updated -> viewModel.updateDraftLotLine(updated) }
                                )
                            } else {
                                LotLineEditRow(
                                    line = line,
                                    decorationBatchesByPrep = state.availableDecorationBatchesByPreparation,
                                    onUpdate = { updated -> viewModel.updateDraftLotLine(updated) }
                                )
                            }
                        }
                    }
                }
            }

            // ── Notas ─────────────────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notas (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }

            // ── Confirmar ─────────────────────────────────────────────────────
            item {
                Button(
                    onClick = {
                        val branch = selectedBranch ?: return@Button
                        val shift = selectedShift ?: return@Button
                        val currentUser = user ?: return@Button
                        viewModel.saveLot(companyId, currentUser, branch, shift, productionDate, notesText)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary),
                    enabled = selectedBranch != null && selectedShift != null && !state.isLoading
                ) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                    else Text("Crear lote de producción", fontWeight = FontWeight.Bold)
                }
            }
        }
        } // BoxWithConstraints
    }
    LoadingOverlay(visible = state.isLoading)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LotLineEditRow(
    line: ProductionLotLine,
    decorationBatchesByPrep: Map<String, List<PreparationBatch>>,
    onUpdate: (ProductionLotLine) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (line.isSpecial) "★ ${line.productName}" else line.productName,
                        fontWeight = FontWeight.SemiBold,
                        color = if (line.isSpecial) BrandColors.AccentDeep else BrandColors.Primary
                    )
                    Text(line.productUnit, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    if (line.recipeId.isNotEmpty())
                        Text(line.recipeName, style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0))
                }
                Text(
                    "${line.plannedQuantity.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = BrandColors.AccentDeep,
                    modifier = Modifier.width(90.dp)
                )
            }

            // ── Adornos (si el producto los declara) ───────────────────────────
            if (line.decorationsSnapshot.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFFE0E0E0))
                Text(
                    "Adornos del producto",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandColors.Primary
                )
                line.decorationsSnapshot.forEachIndexed { idx, deco ->
                    DecorationRow(
                        decoration = deco,
                        plannedQuantity = line.plannedQuantity,
                        candidateBatches = decorationBatchesByPrep[deco.preparationId] ?: emptyList(),
                        onBatchChange = { batch ->
                            val newSnapshot = line.decorationsSnapshot.toMutableList().apply {
                                set(idx, deco.copy(batchId = batch.id, batchName = batch.preparationName))
                            }
                            onUpdate(line.copy(decorationsSnapshot = newSnapshot))
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecorationRow(
    decoration: ProductionLotDecoration,
    plannedQuantity: Double,
    candidateBatches: List<PreparationBatch>,
    onBatchChange: (PreparationBatch) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val totalNeeded = decoration.gramsPerUnit * plannedQuantity
    val selectedBatch = candidateBatches.find { it.id == decoration.batchId }
    val insufficient = selectedBatch != null && selectedBatch.remainingWeightGrams < totalNeeded

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${decoration.preparationName.ifEmpty { decoration.ingredientName }} · " +
                    "${decoration.gramsPerUnit.fmt(1)} g/u",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Necesita: ${totalNeeded.fmt(0)} g",
                style = MaterialTheme.typography.labelSmall,
                color = if (insufficient) Color(0xFFD32F2F) else Color.Gray
            )
        }
        // Insumo comprado ya hecho: no hay batch que elegir, se descuenta del
        // BranchStock junto con la receta. El selector de lote solo aplica a lo
        // que preparamos nosotros.
        if (decoration.isIngredientSourced) {
            Text(
                "Insumo — se descuenta del stock junto con la receta",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        } else if (candidateBatches.isEmpty()) {
            Text(
                "Sin batches activos de ${decoration.preparationName} en esta sucursal — produce uno primero.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFD32F2F)
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedBatch?.let {
                        "${it.preparationName} · saldo ${it.remainingWeightGrams.fmt(0)} g"
                    } ?: "Seleccionar lote",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Lote de adorno") },
                    isError = insufficient,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    candidateBatches.forEach { batch ->
                        DropdownMenuItem(
                            text = {
                                Text("${batch.preparationName} · saldo ${batch.remainingWeightGrams.fmt(0)} g")
                            },
                            onClick = { onBatchChange(batch); expanded = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLabelCard(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.Primary
            )
        }
    }
}

private fun startOfTodayMillis(): Long {
    val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
    val today = kotlin.time.Clock.System.now().toLocalDateTime(tz).date
    return kotlinx.datetime.LocalDateTime(today, kotlinx.datetime.LocalTime(0, 0, 0))
        .toInstant(tz).toEpochMilliseconds()
}

private fun startOfNextDayMillis(): Long {
    // Mismo cálculo que ProductionOrderFormScreen.startOfNextDay() para asegurar igualdad
    // exacta del millis con los pedidos guardados (no usar +24h directo).
    val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
    val today = kotlin.time.Clock.System.now().toLocalDateTime(tz).date
    val tomorrow = today.plus(1, DateTimeUnit.DAY)
    return kotlinx.datetime.LocalDateTime(tomorrow, kotlinx.datetime.LocalTime(0, 0, 0))
        .toInstant(tz).toEpochMilliseconds()
}
