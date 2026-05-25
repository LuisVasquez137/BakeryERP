package com.bakery.erp.presentation.production

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.PreparationBatch
import com.bakery.erp.model.Permission
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.ProductionOrder
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.toBaseUnit
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.ui.KeepScreenOn
import com.bakery.erp.util.fmt

private const val GRAMS_PER_LB_DETAIL = 453.592
private fun Double.gramsToLbDetail(): Double = this / GRAMS_PER_LB_DETAIL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionLotDetailScreen(
    lotId: String,
    onNavigateBack: () -> Unit,
    onNavigateToComplete: (lotId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel,
    productViewModel: ProductViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val productState by productViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canManage = user?.hasPermission(Permission.MANAGE_PRODUCTION_LOTS) == true
    val snackbarHostState = remember { SnackbarHostState() }

    // Mantener pantalla encendida mientras los panaderos tengan la lista abierta.
    KeepScreenOn()

    LaunchedEffect(lotId) {
        if (companyId.isNotEmpty()) viewModel.observeLotDetail(companyId, lotId)
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopObservingLotDetail() }
    }
    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) productViewModel.loadProducts(companyId)
    }
    // Refrescar el caché de productos del ViewModel cada vez que cambie la lista cargada
    // (resuelve fallback de rawWeightGrams en líneas legacy con campo null).
    LaunchedEffect(productState.productList) {
        viewModel.updateProductsCache(productState.productList)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val lot = state.selectedLot
    // Usamos editableLotLines para que los cambios de esponja del panadero se reflejen
    // inmediatamente en el cálculo de la receta escalada.
    val lines = state.editableLotLines

    var selectedLineId by remember { mutableStateOf<String?>(null) }
    val selectedLine = lines.find { it.id == selectedLineId }
    val scaledView = selectedLine?.let { viewModel.buildScaledRecipeView(it) }
    val scaledIssue = selectedLine?.let { viewModel.diagnoseScaledRecipe(it) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canEditSponge = lot?.status == ProductionLotStatus.OPEN ||
        lot?.status == ProductionLotStatus.IN_PROGRESS

    // Pedido tardío pendiente de confirmación (cuando lote está IN_PROGRESS).
    var pendingLateOrderConfirm by remember { mutableStateOf<ProductionOrder?>(null) }

    pendingLateOrderConfirm?.let { lateOrder ->
        if (lot != null) {
            AlertDialog(
                onDismissRequest = { pendingLateOrderConfirm = null },
                title = { Text("Lote ya iniciado") },
                text = {
                    Text(
                        "Este lote ya está en producción. Antes de agregar el pedido de " +
                            "${lateOrder.sellingBranchName}, coordina con el panadero para que sepa " +
                            "que tiene que pesar un extra. ¿Continuar?"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.addLateOrderToCurrentLot(companyId, lot, lateOrder)
                        pendingLateOrderConfirm = null
                    }) { Text("Sí, agregar al lote", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingLateOrderConfirm = null }) { Text("Cancelar") }
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Lista de Producción", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF123456),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFFDFBF7)
    ) { padding ->
        if (state.isDetailLoading || lot == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val isWide = maxWidth >= 1000.dp

            @Composable
            fun LotBodyContent(modifier: Modifier) {
                LazyColumn(
                    modifier = modifier,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Encabezado del lote ──────────────────────────────────────
                    item {
                        LotHeaderCard(lot)
                    }

                    // ── Sección de productos ─────────────────────────────────────
                    item {
                        Text(
                            "Productos a producir (${lines.size})",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF123456)
                        )
                        if (isWide) {
                            Text(
                                "Selecciona un producto para ver su receta escalada en el panel derecho.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        } else {
                            Text(
                                "Toca un producto para ver la receta escalada.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }

                    if (isWide) {
                        // Tabla compacta en pantallas anchas
                        item {
                            ProductTableHeader(
                                showProducedColumn = lot.status == ProductionLotStatus.COMPLETED
                            )
                        }
                        items(lines, key = { it.id }) { line ->
                            ProductTableRow(
                                line = line,
                                showProducedColumn = lot.status == ProductionLotStatus.COMPLETED,
                                isSelected = selectedLineId == line.id,
                                onClick = { selectedLineId = line.id }
                            )
                        }
                    } else {
                        items(lines, key = { it.id }) { line ->
                            ProductCard(
                                line = line,
                                showProduced = lot.status == ProductionLotStatus.COMPLETED,
                                isSelected = selectedLineId == line.id,
                                onClick = { selectedLineId = line.id }
                            )
                        }
                    }
                    if (canManage && lot.status == ProductionLotStatus.OPEN) {
                        item {
                            Text(
                                "Para modificar las cantidades, edita el pedido fuente desde " +
                                    "\"Recibidos\". El lote se recalcula automáticamente al guardar.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    // ── Pedidos tardíos pendientes de incorporar ─────────────────
                    val lateOrders = state.lateOrdersForLot
                    if (canManage && lateOrders.isNotEmpty() &&
                        lot.status != ProductionLotStatus.COMPLETED &&
                        lot.status != ProductionLotStatus.CANCELLED) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            LateOrdersPanelHeader(
                                count = lateOrders.size,
                                isInProgress = lot.status == ProductionLotStatus.IN_PROGRESS
                            )
                        }
                        items(lateOrders, key = { it.id }) { lateOrder ->
                            LateOrderCard(
                                order = lateOrder,
                                lines = state.lateOrderLinesMap[lateOrder.id].orEmpty(),
                                lineCountFallback = lateOrder.lineCount,
                                isLoading = state.isAddingLateOrder,
                                onAdd = {
                                    if (lot.status == ProductionLotStatus.IN_PROGRESS) {
                                        pendingLateOrderConfirm = lateOrder
                                    } else {
                                        viewModel.addLateOrderToCurrentLot(companyId, lot, lateOrder)
                                    }
                                }
                            )
                        }
                    }

                    // ── Acciones ─────────────────────────────────────────────────
                    if (canManage) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            when (lot.status) {
                                ProductionLotStatus.OPEN -> {
                                    Button(
                                        onClick = { viewModel.startLot(companyId, lot.id) },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                                    ) { Text("Iniciar producción", fontWeight = FontWeight.Bold) }
                                }
                                ProductionLotStatus.IN_PROGRESS -> {
                                    Button(
                                        onClick = { onNavigateToComplete(lot.id) },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                    ) { Text("Completar lote y descontar stock", fontWeight = FontWeight.Bold) }
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }

            if (isWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Proporciones: panel de receta ~58% (1.4f) — necesita más espacio
                    // que la lista porque muestra tabla con ingredientes y conversiones.
                    LotBodyContent(modifier = Modifier.weight(1f).fillMaxHeight())
                    VerticalDivider(color = Color(0xFFE0E0E0))
                    Box(modifier = Modifier.weight(1.4f).fillMaxHeight()) {
                        when {
                            scaledView != null -> ScaledRecipePanel(
                                view = scaledView,
                                modifier = Modifier.fillMaxSize(),
                                onClose = { selectedLineId = null },
                                headerExtra = {
                                    if (canEditSponge && selectedLine != null) {
                                        SpongeControlsForLine(
                                            line = selectedLine,
                                            batches = state.availableSpongeBatches,
                                            onUpdate = { updated ->
                                                viewModel.updateEditableLotLine(updated)
                                            }
                                        )
                                    }
                                }
                            )
                            scaledIssue != null -> RecipeIssuePanel(
                                issue = scaledIssue,
                                modifier = Modifier.fillMaxSize(),
                                onClose = { selectedLineId = null }
                            )
                            else -> EmptyDetailRecipePlaceholder(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            } else {
                LotBodyContent(modifier = Modifier.fillMaxSize())
                if (selectedLine != null) {
                    ModalBottomSheet(
                        onDismissRequest = { selectedLineId = null },
                        sheetState = sheetState,
                        containerColor = Color(0xFFFDFBF7)
                    ) {
                        when {
                            scaledView != null -> ScaledRecipePanel(
                                view = scaledView,
                                onClose = { selectedLineId = null },
                                headerExtra = {
                                    if (canEditSponge && selectedLine != null) {
                                        SpongeControlsForLine(
                                            line = selectedLine,
                                            batches = state.availableSpongeBatches,
                                            onUpdate = { updated ->
                                                viewModel.updateEditableLotLine(updated)
                                            }
                                        )
                                    }
                                }
                            )
                            scaledIssue != null -> RecipeIssuePanel(
                                issue = scaledIssue,
                                onClose = { selectedLineId = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LotHeaderCard(lot: com.bakery.erp.model.ProductionLot) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(lot.producingBranchName, fontWeight = FontWeight.Bold, color = Color(0xFF123456), style = MaterialTheme.typography.titleMedium)
                    Text(lot.shiftName, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Text(formatProductionDate(lot.productionDate), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                LotStatusChip(lot.status)
            }
            if (!lot.notes.isNullOrBlank()) {
                HorizontalDivider()
                Text(lot.notes, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            if (!lot.stockDeducted && lot.status == ProductionLotStatus.COMPLETED) {
                HorizontalDivider()
                Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "⚠ Stock pendiente de descontar",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF57C00),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductTableHeader(showProducedColumn: Boolean) {
    Surface(
        color = Color(0xFFF6F4EE),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Producto", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF123456), modifier = Modifier.weight(2f))
            Text("Receta", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF123456), modifier = Modifier.weight(1.5f))
            Text(
                "Unidades",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF123456),
                modifier = Modifier.width(90.dp)
            )
            if (showProducedColumn) {
                Text(
                    "Reales",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.width(80.dp)
                )
            }
            Spacer(Modifier.width(24.dp))
        }
    }
}

@Composable
private fun ProductTableRow(
    line: ProductionLotLine,
    showProducedColumn: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) Color(0xFFE3F2FD) else Color.White
    val border = if (isSelected) Color(0xFF123456) else Color(0xFFE8E8E8)
    Surface(
        color = bg,
        border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, border),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(2f)) {
                Text(line.productName, fontWeight = FontWeight.SemiBold, color = Color(0xFF123456))
                if (line.productUnit.isNotEmpty()) {
                    Text(line.productUnit, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            Text(
                line.recipeName.ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = if (line.recipeName.isEmpty()) Color.Gray else Color(0xFF1565C0),
                modifier = Modifier.weight(1.5f)
            )
            Text(
                "${line.plannedQuantity.toInt()}",
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFCC5500),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(90.dp)
            )
            if (showProducedColumn) {
                Text(
                    "${line.producedQuantity.toInt()}",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(80.dp)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Ver receta",
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ProductCard(
    line: ProductionLotLine,
    showProduced: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val cardColor = if (isSelected) Color(0xFFE3F2FD) else Color.White
    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (isSelected) 2.dp else 0.dp, if (isSelected) Color(0xFF123456) else Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(line.productName, fontWeight = FontWeight.SemiBold, color = Color(0xFF123456))
                Text(line.productUnit, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                if (line.recipeId.isNotEmpty())
                    Text("Receta: ${line.recipeName}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0))
                Text("Toca para ver receta escalada →", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCC5500))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${line.plannedQuantity.toInt()}",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFCC5500),
                    style = MaterialTheme.typography.titleLarge
                )
                Text("unidades", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                if (showProduced && line.producedQuantity != line.plannedQuantity) {
                    Text("${line.producedQuantity.toInt()} reales", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                }
            }
        }
    }
}

@Composable
private fun RecipeIssuePanel(
    issue: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .background(Color(0xFFFFF8E1))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Receta no disponible",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFCC5500),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "No se puede mostrar la receta escalada",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF7A4500),
                    fontWeight = FontWeight.Bold
                )
            }
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Color(0xFF7A4500)
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                issue,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF333333)
            )
        }
    }
}

@Composable
private fun EmptyDetailRecipePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFFFDFBF7)).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Receta escalada",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFCC5500),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Selecciona un producto de la tabla para ver cuánto pesar de cada ingrediente.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpongeControlsForLine(
    line: ProductionLotLine,
    batches: List<PreparationBatch>,
    onUpdate: (ProductionLotLine) -> Unit
) {
    val useSponge = line.spongeBatchId.isNotEmpty()
    var lbText by remember(line.id, line.spongeGramsUsed) {
        mutableStateOf(if (line.spongeGramsUsed > 0.0) line.spongeGramsUsed.gramsToLbDetail().fmt(2) else "")
    }
    var dropdownExpanded by remember(line.id) { mutableStateOf(false) }
    val selectedBatch = batches.find { it.id == line.spongeBatchId }

    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = Color(0xFFFFF8E1),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = useSponge,
                    onCheckedChange = { checked ->
                        if (checked) {
                            val first = batches.firstOrNull() ?: return@Checkbox
                            onUpdate(
                                line.copy(
                                    spongeBatchId = first.id,
                                    spongeBatchName = first.preparationName
                                )
                            )
                        } else {
                            onUpdate(line.copy(spongeBatchId = "", spongeBatchName = "", spongeGramsUsed = 0.0))
                            lbText = ""
                        }
                    },
                    enabled = batches.isNotEmpty()
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "¿Le pondrás esponja a esta masa?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF7A4500)
                    )
                    if (batches.isEmpty()) {
                        Text(
                            "Sin lotes activos de esponja en esta sucursal. Produce uno en Lotes de Preparación.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD32F2F)
                        )
                    }
                }
            }
            if (useSponge && batches.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedBatch?.let {
                                "${it.preparationName} · saldo ${it.remainingWeightGrams.gramsToLbDetail().fmt(2)} lb"
                            } ?: line.spongeBatchName.ifEmpty { "Seleccionar lote" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Lote de esponja") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            batches.forEach { batch ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(batch.preparationName)
                                            Text(
                                                "Saldo: ${batch.remainingWeightGrams.gramsToLbDetail().fmt(2)} lb",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray
                                            )
                                        }
                                    },
                                    onClick = {
                                        onUpdate(
                                            line.copy(
                                                spongeBatchId = batch.id,
                                                spongeBatchName = batch.preparationName
                                            )
                                        )
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = lbText,
                        onValueChange = { text ->
                            lbText = text
                            val lb = text.toDoubleOrNull() ?: 0.0
                            onUpdate(line.copy(spongeGramsUsed = toBaseUnit(lb, "lb")))
                        },
                        label = { Text("Libras") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(110.dp),
                        singleLine = true,
                        suffix = { Text("lb") }
                    )
                }
            }
        }
    }
}

@Composable
private fun LateOrdersPanelHeader(count: Int, isInProgress: Boolean) {
    val bg = if (isInProgress) Color(0xFFFFF3E0) else Color(0xFFFFFDE7)
    val accent = if (isInProgress) Color(0xFFCC5500) else Color(0xFFF9A825)
    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = accent
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Pedidos tardíos pendientes de aprobación ($count)",
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (isInProgress)
                        "El lote ya está en producción. Habla con el panadero antes de aprobar."
                    else "Pedidos enviados después de creado el lote. Aprueba para añadirlos.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7A4500)
                )
            }
        }
    }
}

@Composable
private fun LateOrderCard(
    order: ProductionOrder,
    lines: List<ProductionOrderLine>,
    lineCountFallback: Int,
    isLoading: Boolean,
    onAdd: () -> Unit
) {
    val displayCount = if (lines.isNotEmpty()) lines.size else lineCountFallback
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFFFE0B2)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        order.sellingBranchName.ifBlank { "Sucursal —" },
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF123456)
                    )
                    Text(
                        "$displayCount productos · enviado ${formatHourMinute(order.createdAt)}" +
                            (if (order.createdByUserName.isNotBlank()) " por ${order.createdByUserName}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    if (!order.notes.isNullOrBlank()) {
                        Text(
                            "Nota: ${order.notes}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF555555)
                        )
                    }
                }
                Button(
                    onClick = onAdd,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Aprobar pedido tardío", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Desglose de productos del pedido tardío — el panadero debe ver qué se está
            // pidiendo extra antes de aprobar.
            if (lines.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Color(0xFFFFF8E1),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            "Productos pedidos:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF7A4500)
                        )
                        Spacer(Modifier.height(4.dp))
                        lines.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    line.productName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF333333),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${line.requestedQuantity.toInt()} ${line.productUnit}".trim(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCC5500)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LotStatusChip(status: String) {
    val (color, bg) = when (status) {
        ProductionLotStatus.OPEN        -> Color(0xFF1565C0) to Color(0xFFE3F2FD)
        ProductionLotStatus.IN_PROGRESS -> Color(0xFFF57C00) to Color(0xFFFFF3E0)
        ProductionLotStatus.COMPLETED   -> Color(0xFF2E7D32) to Color(0xFFE8F5E9)
        else                            -> Color.Gray to Color(0xFFF5F5F5)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(
            ProductionLotStatus.label(status),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
