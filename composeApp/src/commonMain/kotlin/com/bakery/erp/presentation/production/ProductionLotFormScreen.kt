package com.bakery.erp.presentation.production

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.ProductionShift
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionLotFormScreen(
    onNavigateBack: () -> Unit,
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

    var selectedBranch by remember { mutableStateOf<Branch?>(null) }
    var branchDropdownExpanded by remember { mutableStateOf(false) }
    var selectedShift by remember { mutableStateOf<ProductionShift?>(null) }
    var shiftDropdownExpanded by remember { mutableStateOf(false) }
    var productionDate by remember { mutableStateOf(startOfTodayMillis()) }
    var notesText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) {
            viewModel.loadOrderFormData(companyId, user)
            productViewModel.loadProducts(companyId)
        }
    }

    LaunchedEffect(state.producingBranches, user) {
        if (selectedBranch == null) {
            val accessible = state.producingBranches.filter { user?.canAccessBranch(it.id) == true }
            if (accessible.size == 1) selectedBranch = accessible.first()
        }
    }

    LaunchedEffect(selectedBranch, productionDate) {
        val branch = selectedBranch ?: return@LaunchedEffect
        if (companyId.isNotEmpty()) {
            viewModel.loadLotFormData(companyId, branch.id, productionDate, productState.productList)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Nuevo Lote de Producción", fontWeight = FontWeight.SemiBold) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Sucursal productora ──────────────────────────────────────────
            item {
                if (state.producingBranches.size > 1) {
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
                    Text(
                        "Planta: ${selectedBranch!!.name}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Gray
                    )
                }
            }

            // ── Turno ────────────────────────────────────────────────────────
            item {
                if (state.shifts.isEmpty() && selectedBranch != null) {
                    Text(
                        "Sin turnos configurados. Configura turnos desde el menú de Producción.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF57C00)
                    )
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
                            Text(formatDate(productionDate), fontWeight = FontWeight.SemiBold, color = Color(0xFF123456), style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { productionDate += 86_400_000L }) {
                            Icon(Icons.Default.ChevronRight, "Día siguiente")
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
                        color = Color(0xFF123456)
                    )
                }
                items(state.pendingOrdersForDate) { order ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4FF)), shape = RoundedCornerShape(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(order.sellingBranchName, fontWeight = FontWeight.SemiBold, color = Color(0xFF123456))
                            Text("${order.lineCount} productos", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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

            // ── Lista consolidada ─────────────────────────────────────────────
            if (state.draftLotLines.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text("Lista consolidada", fontWeight = FontWeight.Bold, color = Color(0xFF123456))
                }
                items(state.draftLotLines) { line ->
                    LotLineEditRow(
                        line = line,
                        onQuantityChange = { qty ->
                            viewModel.updateDraftLotLine(line.copy(plannedQuantity = qty, producedQuantity = qty))
                        }
                    )
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123456)),
                    enabled = selectedBranch != null && selectedShift != null && !state.isLoading
                ) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                    else Text("Crear lote de producción", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LotLineEditRow(line: ProductionLotLine, onQuantityChange: (Double) -> Unit) {
    var qtyText by remember(line.plannedQuantity) {
        mutableStateOf(line.plannedQuantity.toInt().toString())
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
                if (line.recipeId.isNotEmpty())
                    Text(line.recipeName, style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0))
            }
            OutlinedTextField(
                value = qtyText,
                onValueChange = { text ->
                    qtyText = text
                    onQuantityChange(text.toDoubleOrNull() ?: 0.0)
                },
                label = { Text("Cant.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(90.dp),
                singleLine = true
            )
        }
    }
}

private fun startOfTodayMillis(): Long {
    val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
    val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(tz).date
    return kotlinx.datetime.LocalDateTime(today, kotlinx.datetime.LocalTime(0, 0, 0))
        .toInstant(tz).toEpochMilliseconds()
}
