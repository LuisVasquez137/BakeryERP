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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlin.time.Clock as KotlinClock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CustomerLineSpec
import com.bakery.erp.model.CustomerOrderType
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
    // Tarjetas "ya preparé" — al tocar una y abrir la receta, la marcamos en beige
    // para que el panadero recuerde qué ya trabajó. Solo en sesión.
    var visitedLineIds by remember { mutableStateOf(emptySet<String>()) }
    val scaledView = selectedLine?.let { viewModel.buildScaledRecipeView(it) }
    val scaledIssue = selectedLine?.let { viewModel.diagnoseScaledRecipe(it) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val registerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canEditSponge = lot?.status == ProductionLotStatus.OPEN ||
        lot?.status == ProductionLotStatus.IN_PROGRESS

    // Pedido tardío pendiente de confirmación (cuando lote está IN_PROGRESS).
    var pendingLateOrderConfirm by remember { mutableStateOf<ProductionOrder?>(null) }

    // Dialog de inicio: se muestra cuando el lote sigue OPEN. Bloquea el detalle
    // hasta que el panadero acuse el inicio del turno para que el cronómetro
    // arranque y quede registro auditable de la hora de inicio.
    var isStartingLot by remember { mutableStateOf(false) }

    // Confirmación de cierre temprano (<1h de trabajo) para evitar cierres accidentales.
    var pendingEarlyComplete by remember { mutableStateOf<Int?>(null) }

    // FAB "Registrar producción" — bottom sheet con cantidades reales por producto.
    var showRegisterSheet by remember { mutableStateOf(false) }

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
                title = {
                    // Compactamos toda la info del lote en el header (sucursal,
                    // turno y estado) para no gastar espacio en una card encima
                    // del listado de productos.
                    Column {
                        Text(
                            buildString {
                                append("Orden de producción")
                                if (lot != null && lot.producingBranchName.isNotBlank()) {
                                    append(" - ").append(lot.producingBranchName)
                                }
                            },
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        if (lot != null) {
                            val turnoTxt = if (lot.shiftName.isNotBlank()) "Turno: ${lot.shiftName}" else ""
                            val estadoTxt = ProductionLotStatus.label(lot.status)
                            Text(
                                listOf(turnoTxt, estadoTxt).filter { it.isNotEmpty() }
                                    .joinToString("  ·  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFD166),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    // "Registrar producción" sale del FAB (tapaba la receta escalada)
                    // y vive aquí, alineado a la derecha. Solo visible en producción.
                    if (canManage && lot?.status == ProductionLotStatus.IN_PROGRESS) {
                        TextButton(
                            onClick = { showRegisterSheet = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Registrar conteo", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF005B8D),
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

        // Antes de mostrar la orden, un panadero con lote OPEN debe pulsar
        // "Iniciar orden de producción". Bloqueamos el detalle con un fondo
        // neutro y un dialog centrado para que el acuse sea explícito y el
        // cronómetro arranque desde aquí.
        if (canManage && lot.status == ProductionLotStatus.OPEN) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFFDFBF7))
            ) {
                StartShiftDialog(
                    lot = lot,
                    isStarting = isStartingLot,
                    onStart = {
                        isStartingLot = true
                        viewModel.startLot(companyId, lot.id, user)
                    }
                )
            }
            // Al pasar a IN_PROGRESS el dialog se desmonta automáticamente.
            LaunchedEffect(lot.status) {
                if (lot.status != ProductionLotStatus.OPEN) isStartingLot = false
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Cronómetro del turno: visible mientras el lote esté en producción.
            if (lot.status == ProductionLotStatus.IN_PROGRESS && lot.startedAt != null) {
                ElapsedTimeBanner(startedAtMillis = lot.startedAt)
            }
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isWide = maxWidth >= 1000.dp

            @Composable
            fun LotBodyContent(modifier: Modifier) {
                LazyColumn(
                    modifier = modifier,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Sin header card ni texto introductorio — la info de sucursal/
                    // turno/estado ya vive en el TopAppBar. Maximiza el espacio para
                    // la orden de producción.

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
                                isVisited = line.id in visitedLineIds,
                                onClick = {
                                    selectedLineId = line.id
                                    visitedLineIds = visitedLineIds + line.id
                                }
                            )
                        }
                    } else {
                        items(lines, key = { it.id }) { line ->
                            ProductCard(
                                line = line,
                                showProduced = lot.status == ProductionLotStatus.COMPLETED,
                                isSelected = selectedLineId == line.id,
                                isVisited = line.id in visitedLineIds,
                                onClick = {
                                    selectedLineId = line.id
                                    visitedLineIds = visitedLineIds + line.id
                                }
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
                    if (canManage && lot.status == ProductionLotStatus.IN_PROGRESS) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            // Espacio extra al fondo para que el FAB no tape el botón.
                            Button(
                                onClick = {
                                    val started = lot.startedAt
                                    if (started != null) {
                                        val elapsedMin = ((KotlinClock.System.now()
                                            .toEpochMilliseconds() - started) / 60_000L).toInt()
                                        if (elapsedMin < 60) {
                                            pendingEarlyComplete = elapsedMin
                                            return@Button
                                        }
                                    }
                                    onNavigateToComplete(lot.id)
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) { Text("Terminar lote y descontar stock", fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.height(80.dp))
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
            }  // BoxWithConstraints
        }  // Column wrapping the timer banner + body

        // Bottom sheet "Registrar producción" — el panadero puede registrar
        // cantidades reales en cualquier momento del turno (no solo al final).
        if (showRegisterSheet && lot.status == ProductionLotStatus.IN_PROGRESS) {
            RegisterProductionSheet(
                lines = lines,
                sheetState = registerSheetState,
                onDismiss = { showRegisterSheet = false },
                onSave = { quantities ->
                    viewModel.saveProducedQuantities(companyId, lot.id, quantities)
                    showRegisterSheet = false
                }
            )
        }

        // Confirmación de cierre temprano (<1h de producción): evita cierres
        // accidentales que dañen el reporte y el descuento de stock.
        pendingEarlyComplete?.let { minutes ->
            AlertDialog(
                onDismissRequest = { pendingEarlyComplete = null },
                title = { Text("¿Terminar lote ahora?") },
                text = {
                    Text(
                        "Solo llevas $minutes minuto${if (minutes == 1) "" else "s"} de producción, " +
                            "parece muy poco tiempo para terminar este lote. ¿Estás seguro?"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingEarlyComplete = null
                            onNavigateToComplete(lot.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) { Text("Sí, completar producción", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingEarlyComplete = null }) { Text("Cancelar") }
                }
            )
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
                    Text(lot.producingBranchName, fontWeight = FontWeight.Bold, color = Color(0xFF005B8D), style = MaterialTheme.typography.titleMedium)
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
            Text("Producto", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF005B8D), modifier = Modifier.weight(2f))
            Text("Receta", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF005B8D), modifier = Modifier.weight(1.5f))
            Text(
                "Unidades",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF005B8D),
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
    isVisited: Boolean,
    onClick: () -> Unit
) {
    // Beige cuando ya se tocó (panadero ya vio la receta = "ya preparé").
    val bg = when {
        isVisited  -> Color(0xFFFFF8E1)
        isSelected -> Color(0xFFE3F2FD)
        else       -> Color.White
    }
    val border = if (isSelected) Color(0xFF005B8D) else Color(0xFFE8E8E8)
    Surface(
        color = bg,
        border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, border),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(2f)) {
                    Text(line.productName, fontWeight = FontWeight.SemiBold, color = Color(0xFF005B8D))
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
                    color = Color(0xFFD9A441),
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
            CustomerSpecsRow(line.customerSpecs)
        }
    }
}

@Composable
private fun ProductCard(
    line: ProductionLotLine,
    showProduced: Boolean,
    isSelected: Boolean,
    isVisited: Boolean,
    onClick: () -> Unit
) {
    val cardColor = when {
        isVisited  -> Color(0xFFFFF8E1)
        isSelected -> Color(0xFFE3F2FD)
        else       -> Color.White
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(if (isSelected) 1.5.dp else 0.dp, if (isSelected) Color(0xFF005B8D) else Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(line.productName, fontWeight = FontWeight.SemiBold, color = Color(0xFF005B8D))
                    if (line.recipeId.isNotEmpty())
                        Text("Receta: ${line.recipeName}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${line.plannedQuantity.toInt()}",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFD9A441),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("unidades", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    if (showProduced && line.producedQuantity != line.plannedQuantity) {
                        Text("${line.producedQuantity.toInt()} reales", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                    }
                }
            }
            CustomerSpecsRow(line.customerSpecs)
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
                    color = Color(0xFFD9A441),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "No se puede mostrar la receta escalada",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF8A6420),
                    fontWeight = FontWeight.Bold
                )
            }
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Color(0xFF8A6420)
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
                color = Color(0xFFD9A441),
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = Color(0xFFFFF8E1),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    enabled = batches.isNotEmpty(),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "¿Esponja para esta masa?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8A6420)
                    )
                    if (batches.isEmpty()) {
                        Text(
                            "Sin lotes activos de esponja.",
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
    val accent = if (isInProgress) Color(0xFFD9A441) else Color(0xFFF9A825)
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
                    color = Color(0xFF8A6420)
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
                        color = Color(0xFF005B8D)
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
                            color = Color(0xFF8A6420)
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
                                    color = Color(0xFFD9A441)
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
private fun StartShiftDialog(
    lot: ProductionLot,
    isStarting: Boolean,
    onStart: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* No se puede cerrar — solo iniciar o volver con back. */ },
        title = {
            Text(
                "Iniciar orden de producción",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF005B8D)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    lot.shiftName.ifBlank { "Turno no especificado" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF005B8D)
                )
                Text(
                    lot.producingBranchName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF555555)
                )
                Text(
                    formatProductionDate(lot.productionDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFFFF8E1),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFFD9A441)
                        )
                        Text(
                            "Al iniciar se registra la hora de inicio del turno trabajado. " +
                                "El tiempo se contará automáticamente hasta el cierre del lote.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8A6420)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStart,
                enabled = !isStarting,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) {
                if (isStarting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar orden de producción", fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
private fun ElapsedTimeBanner(startedAtMillis: Long) {
    var nowMillis by remember { mutableStateOf(KotlinClock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(startedAtMillis) {
        while (true) {
            nowMillis = KotlinClock.System.now().toEpochMilliseconds()
            delay(1000L)
        }
    }
    val totalSec = ((nowMillis - startedAtMillis) / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val text = "${h.toString().padStart(2, '0')}:" +
        "${m.toString().padStart(2, '0')}:" +
        s.toString().padStart(2, '0')

    Surface(
        color = Color(0xFFFFF3E0),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = Color(0xFFD9A441),
                modifier = Modifier.size(16.dp)
            )
            Text(
                "Tiempo trabajado",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8A6420),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFD9A441),
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterProductionSheet(
    lines: List<ProductionLotLine>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSave: (Map<String, Double>) -> Unit
) {
    // Pre-llenamos con el producedQuantity actual si > 0 (lo que ya se registró
    // durante el turno). En blanco si nada se ha registrado todavía para esa línea.
    var inputs by remember(lines.map { it.id }.joinToString(",")) {
        mutableStateOf(
            lines.associate { line ->
                line.id to (line.producedQuantity.takeIf { it > 0.0 }?.toInt()?.toString() ?: "")
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFFDFBF7)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(
                "Registrar producción",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF005B8D)
            )
            Text(
                "Anota las unidades reales que han salido. Lo que registres aquí se guarda " +
                    "al instante. Deja en blanco lo que aún no salga.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lines, key = { it.id }) { line ->
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    line.productName,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF005B8D)
                                )
                                Text(
                                    "Pedido: ${line.plannedQuantity.toInt()} ${line.productUnit}".trim(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                            OutlinedTextField(
                                value = inputs[line.id].orEmpty(),
                                onValueChange = { txt ->
                                    val filtered = txt.filter { it.isDigit() }
                                    inputs = inputs.toMutableMap().also { it[line.id] = filtered }
                                },
                                placeholder = { Text("0") },
                                suffix = { Text("/${line.plannedQuantity.toInt()}") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.width(130.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFD9A441),
                                    focusedLabelColor = Color(0xFFD9A441)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Cancelar") }
                Button(
                    onClick = {
                        // Solo enviamos los campos con valor numérico — los vacíos no
                        // se tocan (la línea conserva su producedQuantity actual).
                        val parsed = inputs.mapNotNull { (id, txt) ->
                            txt.toDoubleOrNull()?.let { id to it }
                        }.toMap()
                        onSave(parsed)
                    },
                    modifier = Modifier.weight(1.5f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD9A441))
                ) { Text("Guardar todo", fontWeight = FontWeight.Bold) }
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

/**
 * Lista horizontal con badges por cada [CustomerLineSpec] de la línea. Cada chip
 * muestra el tipo de cliente (Revendedor / Encargo / Pastel) y el nombre. Si la
 * línea tiene customSpec, lo agrega abajo en cursiva-ish para el panadero.
 *
 * Se renderiza solo si la línea tiene specs — no afecta líneas que vienen 100%
 * de sucursales.
 */
@Composable
private fun CustomerSpecsRow(specs: List<CustomerLineSpec>) {
    if (specs.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        FlowRowSimple(items = specs) { spec ->
            CustomerSpecChip(spec)
        }
        val customSpecs = specs.filter { it.customSpec.isNotEmpty() }
        if (customSpecs.isNotEmpty()) {
            customSpecs.forEach { spec ->
                Text(
                    "✱ ${spec.customerName}: ${spec.customSpec}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD9A441),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CustomerSpecChip(spec: CustomerLineSpec) {
    val (bg, fg, label) = when (spec.customerType) {
        CustomerOrderType.RESELLER -> Triple(
            Color(0xFFD9A441).copy(alpha = 0.18f),
            Color(0xFFD9A441),
            "Revendedor"
        )
        CustomerOrderType.CAKE -> Triple(
            Color(0xFFAD1457).copy(alpha = 0.18f),
            Color(0xFFAD1457),
            "Pastel"
        )
        CustomerOrderType.RETAIL_SPECIAL -> Triple(
            Color(0xFF1565C0).copy(alpha = 0.18f),
            Color(0xFF1565C0),
            "Encargo"
        )
        else -> Triple(Color(0x11000000), Color(0xFF424242), "Cliente")
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$label · ${spec.customerName} (${spec.quantity.toInt()})",
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * FlowRow simple (sin la API experimental de Material3). Lista horizontal que
 * salta a la siguiente línea cuando no cabe. Suficiente para pintar 1-5 chips.
 */
@Composable
private fun <T> FlowRowSimple(
    items: List<T>,
    item: @Composable (T) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item(it) }
    }
}
