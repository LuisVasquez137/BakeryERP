package com.bakery.erp.presentation.production

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.products.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionLotCompleteScreen(
    lotId: String,
    onNavigateBack: () -> Unit,
    onCompleted: () -> Unit,
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

    var showConfirmDialog by remember { mutableStateOf(false) }
    var showWarningsDialog by remember { mutableStateOf(false) }
    // Modo edición global: por defecto se muestran etiquetas read-only con la
    // comparación pedido/real/diferencia. Al pulsar "Editar cantidades" se cambia
    // a campos editables; "Listo" vuelve a labels.
    var editMode by rememberSaveable { mutableStateOf(false) }
    // Marca de prefill: una vez que cargan las líneas, rellenamos producedQuantity
    // con plannedQuantity para las líneas que aún están en 0 (panadero no registró
    // nada durante el turno). El usuario puede editarlas si difiere.
    var prefilled by remember(lotId) { mutableStateOf(false) }

    LaunchedEffect(lotId) {
        if (companyId.isNotEmpty()) viewModel.loadLotDetail(companyId, lotId)
    }
    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) productViewModel.loadProducts(companyId)
    }
    LaunchedEffect(productState.productList) {
        viewModel.updateProductsCache(productState.productList)
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    LaunchedEffect(state.isCompleted) {
        if (state.isCompleted) {
            if (state.stockWarnings.isEmpty()) {
                viewModel.clearCompleted()
                onCompleted()
            } else {
                showWarningsDialog = true
            }
        }
    }

    val lot = state.selectedLot
    val lines = state.editableLotLines

    // Prefill una sola vez por carga: si una línea tiene producedQuantity = 0 y
    // plannedQuantity > 0, asumimos "salió lo mismo que se pidió" (el panadero
    // no registró durante el turno). Quedan editables si difiere.
    LaunchedEffect(lines.size, prefilled) {
        if (!prefilled && lines.isNotEmpty()) {
            lines.forEach { line ->
                if (line.producedQuantity == 0.0 && line.plannedQuantity > 0.0) {
                    viewModel.updateEditableLotLine(
                        line.copy(producedQuantity = line.plannedQuantity)
                    )
                }
            }
            prefilled = true
        }
    }

    if (showConfirmDialog && lot != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Completar lote") },
            text = {
                Text("Se marcarán las cantidades producidas y se descontarán los insumos del stock de la planta. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        val currentUser = user ?: return@Button
                        viewModel.completeLot(companyId, lot, currentUser)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) { Text("Confirmar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showWarningsDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Completado con advertencias") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "El lote fue completado, pero algunos productos no pudieron descontar stock:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(4.dp))
                    state.stockWarnings.forEach { warning ->
                        Text("• $warning", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF57C00))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showWarningsDialog = false
                    viewModel.clearCompleted()
                    onCompleted()
                }) { Text("Entendido") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Completar Lote",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        if (lot != null) {
                            Text(
                                listOfNotNull(
                                    lot.producingBranchName.takeIf { it.isNotBlank() },
                                    lot.shiftName.takeIf { it.isNotBlank() }?.let { "Turno: $it" },
                                    formatProductionDate(lot.productionDate)
                                ).joinToString("  ·  "),
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
                    TextButton(onClick = { editMode = !editMode }) {
                        Icon(
                            if (editMode) Icons.Default.Done else Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (editMode) "Listo" else "Editar cantidades",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2E7D32),
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
            // ≥800dp: tabla compacta para tablets/escritorio; <800dp: cards apiladas.
            val isWide = maxWidth >= 800.dp

            Column(modifier = Modifier.fillMaxSize()) {
                // Subtítulo único arriba (instrucción + contador).
                Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "Cantidades producidas (${lines.size} productos)",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF005B8D),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            if (editMode)
                                "Ajusta los valores en la columna \"Real\". Pulsa \"Listo\" cuando termines."
                            else
                                "Estas son las cantidades registradas durante el turno. Si difiere, " +
                                    "pulsa \"Editar cantidades\" arriba.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }

                if (isWide) {
                    CompleteTable(
                        lines = lines,
                        editMode = editMode,
                        onProducedQuantityChange = { line, qty ->
                            viewModel.updateEditableLotLine(line.copy(producedQuantity = qty))
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(lines, key = { it.id }) { line ->
                            LotLineSummaryCard(
                                line = line,
                                editMode = editMode,
                                onProducedQuantityChange = { qty ->
                                    viewModel.updateEditableLotLine(line.copy(producedQuantity = qty))
                                }
                            )
                        }
                    }
                }

                // Botón confirmar fijo abajo.
                Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        enabled = !state.isLoading && lines.isNotEmpty()
                    ) {
                        Text("Terminar lote y descontar stock", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Overlay full-screen mientras corre la operación. Bloquea taps y
            // comunica claramente al panadero que está pasando algo (no que la
            // app se congeló).
            if (state.isLoading) {
                CompletingLotOverlay()
            }
        }
    }
}

@Composable
private fun CompletingLotOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC123456)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF2E7D32),
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    "Estamos descontando el stock del inventario",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF005B8D)
                )
                Text(
                    "No cierres la aplicación. Esto puede tardar unos segundos…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompleteTable(
    lines: List<ProductionLotLine>,
    editMode: Boolean,
    onProducedQuantityChange: (ProductionLotLine, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ── Encabezado ───────────────────────────────────────────────────────
        stickyHeader {
            Surface(
                color = Color(0xFFF6F4EE),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Producto", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = Color(0xFF005B8D),
                        modifier = Modifier.weight(2.2f))
                    Text("Receta", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = Color(0xFF005B8D),
                        modifier = Modifier.weight(1.6f))
                    Text("Pedido", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = Color(0xFF555555),
                        modifier = Modifier.width(80.dp))
                    Text("Real", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32),
                        modifier = Modifier.width(if (editMode) 130.dp else 80.dp))
                    Text("Diferencia", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = Color(0xFF8A6420),
                        modifier = Modifier.width(110.dp))
                }
            }
        }

        // ── Filas ────────────────────────────────────────────────────────────
        itemsIndexed(lines) { index, line ->
            CompleteTableRow(
                line = line,
                editMode = editMode,
                rowBg = if (index % 2 == 0) Color.White else Color(0xFFFBF9F3),
                onProducedQuantityChange = { qty -> onProducedQuantityChange(line, qty) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompleteTableRow(
    line: ProductionLotLine,
    editMode: Boolean,
    rowBg: Color,
    onProducedQuantityChange: (Double) -> Unit
) {
    val planned = line.plannedQuantity.toInt()
    val produced = line.producedQuantity.toInt()
    val diff = produced - planned
    val diffColor = when {
        diff > 0 -> Color(0xFF2E7D32)
        diff < 0 -> Color(0xFFD9A441)
        else     -> Color.Gray
    }
    val diffText = when {
        diff > 0 -> "+$diff"
        else     -> diff.toString()
    }

    Surface(
        color = rowBg,
        border = BorderStroke(0.5.dp, Color(0xFFE8E8E8)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(2.2f)) {
                Text(line.productName, fontWeight = FontWeight.SemiBold, color = Color(0xFF005B8D))
                if (line.productUnit.isNotEmpty()) {
                    Text(line.productUnit, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            Text(
                line.recipeName.ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = if (line.recipeName.isEmpty()) Color.Gray else Color(0xFF1565C0),
                modifier = Modifier.weight(1.6f)
            )
            Text(
                planned.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF555555),
                modifier = Modifier.width(80.dp)
            )
            if (editMode) {
                var qtyText by remember(line.id) { mutableStateOf(produced.toString()) }
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { text ->
                        val filtered = text.filter { it.isDigit() }
                        qtyText = filtered
                        onProducedQuantityChange(filtered.toDoubleOrNull() ?: 0.0)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2E7D32),
                        focusedLabelColor = Color(0xFF2E7D32)
                    )
                )
                Spacer(Modifier.width(10.dp))
            } else {
                Text(
                    produced.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.width(80.dp)
                )
            }
            Text(
                diffText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = diffColor,
                modifier = Modifier.width(110.dp)
            )
        }
    }
}

@Composable
private fun LotLineSummaryCard(
    line: ProductionLotLine,
    editMode: Boolean,
    onProducedQuantityChange: (Double) -> Unit
) {
    val planned = line.plannedQuantity.toInt()
    val produced = line.producedQuantity.toInt()
    val diff = produced - planned
    val diffColor = when {
        diff > 0 -> Color(0xFF2E7D32)
        diff < 0 -> Color(0xFFD9A441)
        else     -> Color.Gray
    }
    val diffText = when {
        diff > 0 -> "+$diff"
        else     -> diff.toString()
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(line.productName, fontWeight = FontWeight.SemiBold, color = Color(0xFF005B8D))
                    if (line.recipeId.isNotEmpty()) {
                        Text(
                            "Receta: ${line.recipeName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1565C0)
                        )
                    }
                }
                if (editMode) {
                    var qtyText by remember(line.id) { mutableStateOf(produced.toString()) }
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { text ->
                            val filtered = text.filter { it.isDigit() }
                            qtyText = filtered
                            onProducedQuantityChange(filtered.toDoubleOrNull() ?: 0.0)
                        },
                        label = { Text("Real") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2E7D32),
                            focusedLabelColor = Color(0xFF2E7D32)
                        )
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SummaryStat(label = "Pedido", value = planned.toString(), color = Color(0xFF555555))
                SummaryStat(label = "Real", value = produced.toString(), color = Color(0xFF2E7D32))
                SummaryStat(label = "Diferencia", value = diffText, color = diffColor)
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
