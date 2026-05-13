package com.bakery.erp.presentation.production

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.presentation.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionLotCompleteScreen(
    lotId: String,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    var showConfirmDialog by remember { mutableStateOf(false) }
    var showWarningsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(lotId) {
        if (companyId.isNotEmpty()) viewModel.loadLotDetail(companyId, lotId)
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    LaunchedEffect(state.isCompleted) {
        if (state.isCompleted) {
            if (state.stockWarnings.isEmpty()) {
                viewModel.clearCompleted()
                onNavigateBack()
            } else {
                showWarningsDialog = true
            }
        }
    }

    val lot = state.selectedLot
    val lines = state.editableLotLines

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
                    onNavigateBack()
                }) { Text("Entendido") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Completar Lote", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
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
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Resumen del lote ──────────────────────────────────────────
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                lot.producingBranchName,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF123456),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "${lot.shiftName} — ${formatProductionDate(lot.productionDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // ── Instrucción ───────────────────────────────────────────────
                item {
                    Text(
                        "Confirma las cantidades reales producidas. Luego el sistema descontará los insumos automáticamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Cantidades producidas (${lines.size} productos)",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF123456)
                    )
                }

                // ── Líneas editables ──────────────────────────────────────────
                items(lines) { line ->
                    EditableLotLineRow(
                        line = line,
                        onProducedQuantityChange = { qty ->
                            viewModel.updateEditableLotLine(line.copy(producedQuantity = qty))
                        }
                    )
                }

                // ── Botón confirmar ───────────────────────────────────────────
                item {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        enabled = !state.isLoading && lines.isNotEmpty()
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text("Completar y descontar stock", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableLotLineRow(
    line: ProductionLotLine,
    onProducedQuantityChange: (Double) -> Unit
) {
    var qtyText by remember(line.id) {
        mutableStateOf(line.producedQuantity.toInt().toString())
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(line.productName, fontWeight = FontWeight.SemiBold, color = Color(0xFF123456))
                Text(line.productUnit, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                if (line.recipeId.isNotEmpty()) {
                    Text(
                        "Receta: ${line.recipeName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1565C0)
                    )
                }
                Text(
                    "Planeadas: ${line.plannedQuantity.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            OutlinedTextField(
                value = qtyText,
                onValueChange = { text ->
                    qtyText = text
                    onProducedQuantityChange(text.toDoubleOrNull() ?: 0.0)
                },
                label = { Text("Producidas") },
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
}
