package com.bakery.erp.presentation.production

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.ProductionShift
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.ui.AdaptiveContainer
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionOrderFormScreen(
    orderId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToEditOrder: (String) -> Unit = {},
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel,
    productViewModel: ProductViewModel
) {
    val isEdit = !orderId.isNullOrEmpty()
    val state by viewModel.uiState.collectAsState()
    val productState by productViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""

    // En creación: dos pasos. En edición saltamos directo al paso 2.
    var step by rememberSaveable(isEdit) { mutableIntStateOf(if (isEdit) 2 else 1) }
    // Persistimos solo los IDs (sobreviven rotación); las instancias Branch/Shift se
    // derivan del state para no perder selección al rotar.
    var selectedProducingBranchId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSellingBranchId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedShiftId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedProducingBranch: Branch? = remember(state.producingBranches, selectedProducingBranchId) {
        state.producingBranches.find { it.id == selectedProducingBranchId }
    }
    val selectedSellingBranch: Branch? = remember(state.sellingBranches, selectedSellingBranchId) {
        state.sellingBranches.find { it.id == selectedSellingBranchId }
    }
    val selectedShift: ProductionShift? =
        remember(state.shiftsForSelectedProducingBranch, selectedShiftId) {
            state.shiftsForSelectedProducingBranch.find { it.id == selectedShiftId }
        }
    var branchDropdownExpanded by remember { mutableStateOf(false) }
    var sellingBranchDropdownExpanded by remember { mutableStateOf(false) }
    var targetDate by rememberSaveable { mutableStateOf(startOfNextDay()) }
    var notesText by rememberSaveable { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId, orderId) {
        if (companyId.isEmpty()) return@LaunchedEffect
        if (isEdit) {
            viewModel.loadOrderForEdit(companyId, orderId!!)
            productViewModel.loadProducts(companyId)
        } else {
            viewModel.loadOrderFormData(companyId, user)
            productViewModel.loadProducts(companyId)
        }
    }

    // Prefijar selecciones desde el pedido cargado (modo edición).
    LaunchedEffect(state.selectedOrder, isEdit) {
        if (!isEdit) return@LaunchedEffect
        val order = state.selectedOrder ?: return@LaunchedEffect
        targetDate = order.requestedDate
        notesText = order.notes ?: ""
    }

    // Resolver el turno seleccionado contra los turnos cargados (puede llegar después).
    LaunchedEffect(state.shiftsForSelectedProducingBranch, state.selectedOrder, isEdit) {
        val shifts = state.shiftsForSelectedProducingBranch
        if (shifts.isEmpty()) {
            selectedShiftId = null
            return@LaunchedEffect
        }
        if (isEdit) {
            val orderShiftId = state.selectedOrder?.shiftId.orEmpty()
            selectedShiftId = shifts.find { it.id == orderShiftId }?.id ?: shifts.firstOrNull()?.id
        } else {
            // En creación auto-seleccionar el primero si solo hay uno o si aún no se eligió.
            if (selectedShiftId == null || shifts.none { it.id == selectedShiftId }) {
                selectedShiftId = shifts.firstOrNull()?.id
            }
        }
    }

    // Cuando el usuario elige la planta productora en creación: cargar sus turnos.
    LaunchedEffect(selectedProducingBranchId, isEdit) {
        if (isEdit) return@LaunchedEffect
        viewModel.loadShiftsForProducingBranch(companyId, selectedProducingBranchId ?: "")
    }

    LaunchedEffect(state.producingBranches, isEdit) {
        if (isEdit) return@LaunchedEffect
        if (selectedProducingBranchId == null && state.producingBranches.size == 1) {
            selectedProducingBranchId = state.producingBranches.first().id
        }
    }
    LaunchedEffect(state.sellingBranches, isEdit) {
        if (isEdit) return@LaunchedEffect
        if (selectedSellingBranchId == null && state.sellingBranches.size == 1) {
            selectedSellingBranchId = state.sellingBranches.first().id
        }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.resetOrderForm(); onNavigateBack() }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // Para mostrar la lista de productos en step 2, filtrar por productos asignados a la
    // planta productora (en edición resolvemos por id del pedido cargado).
    val producingBranchIdForFilter = if (isEdit) state.selectedOrder?.producingBranchId.orEmpty()
                                     else selectedProducingBranch?.id.orEmpty()
    val producingBranchForFilter = state.producingBranches.find { it.id == producingBranchIdForFilter }
        ?: selectedProducingBranch
    val displayProducts = remember(productState.productList, searchQuery, producingBranchForFilter) {
        val base = producingBranchForFilter
            ?.assignedProductIds
            ?.takeIf { it.isNotEmpty() }
            ?.let { ids -> productState.productList.filter { it.id in ids } }
            ?: productState.productList
        base.filter {
            it.active && (searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true))
        }.sortedBy { it.name }
    }

    // Dialog de conflicto de pedido pendiente duplicado.
    // No se permite crear duplicados; el usuario solo puede editar el existente o cancelar.
    state.pendingOrderConflict?.let { existing ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPendingOrderConflict() },
            title = { Text("Ya existe un pedido pendiente") },
            text = {
                Text(
                    "Ya tienes un pedido pendiente para ${existing.sellingBranchName} → " +
                        "${existing.producingBranchName} el ${formatDate(existing.requestedDate)}" +
                        (if (existing.shiftName.isNotBlank()) " (turno ${existing.shiftName})" else "") +
                        ". No puedes crear otro para la misma combinación: edita el existente o " +
                        "cancélalo primero."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = existing.id
                    viewModel.clearPendingOrderConflict()
                    viewModel.resetOrderForm()
                    onNavigateToEditOrder(id)
                }) { Text("Editar el existente", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearPendingOrderConflict() }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isEdit && step == 2 -> "Editar Pedido (1/2)"
                            isEdit && step == 3 -> "Revisar Cambios (2/2)"
                            step == 1 -> "Nuevo Pedido (1/3)"
                            step == 2 -> "Agregar Productos (2/3)"
                            else -> "Revisar Pedido (3/3)"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            step == 3 -> step = 2
                            !isEdit && step == 2 -> step = 1
                            else -> onNavigateBack()
                        }
                    }) {
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
        if (isEdit && state.selectedOrder == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        when {
            !isEdit && step == 1 -> OrderFormStep1(
                modifier = Modifier.padding(padding),
                producingBranches = state.producingBranches,
                selectedProducingBranch = selectedProducingBranch,
                branchDropdownExpanded = branchDropdownExpanded,
                onBranchDropdownToggle = { branchDropdownExpanded = it },
                onBranchSelected = { branch ->
                    if (branch.id != selectedProducingBranchId) {
                        viewModel.clearDraftOrderLines()
                        selectedShiftId = null
                    }
                    selectedProducingBranchId = branch.id
                    branchDropdownExpanded = false
                },
                sellingBranches = state.sellingBranches,
                selectedSellingBranch = selectedSellingBranch,
                sellingBranchDropdownExpanded = sellingBranchDropdownExpanded,
                onSellingBranchDropdownToggle = { sellingBranchDropdownExpanded = it },
                onSellingBranchSelected = { selectedSellingBranchId = it.id; sellingBranchDropdownExpanded = false },
                shifts = state.shiftsForSelectedProducingBranch,
                selectedShift = selectedShift,
                onShiftSelected = { selectedShiftId = it.id },
                targetDate = targetDate,
                onDateChange = { targetDate = it },
                notes = notesText,
                onNotesChange = { notesText = it },
                onNext = {
                    if (selectedProducingBranch == null || selectedSellingBranch == null) return@OrderFormStep1
                    if (state.shiftsForSelectedProducingBranch.isNotEmpty() && selectedShift == null) return@OrderFormStep1
                    step = 2
                },
                singleSellingBranch = state.sellingBranches.size == 1
            )
            step == 2 -> OrderFormStep2(
                modifier = Modifier.padding(padding),
                isEdit = isEdit,
                headerSellingBranchName = if (isEdit) state.selectedOrder?.sellingBranchName.orEmpty()
                                          else selectedSellingBranch?.name.orEmpty(),
                headerProducingBranchName = if (isEdit) state.selectedOrder?.producingBranchName.orEmpty()
                                            else selectedProducingBranch?.name.orEmpty(),
                headerDateMillis = targetDate,
                shifts = state.shiftsForSelectedProducingBranch,
                selectedShift = selectedShift,
                onShiftSelected = { selectedShiftId = it.id },
                notes = notesText,
                onNotesChange = { notesText = it },
                products = displayProducts,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                draftLines = state.draftOrderLines,
                onLineChanged = { viewModel.updateDraftOrderLine(it) },
                isLoading = state.isLoading,
                onConfirm = {
                    if (state.draftOrderLines.isEmpty()) return@OrderFormStep2
                    step = 3
                }
            )
            else -> OrderReviewStep(
                modifier = Modifier.padding(padding),
                isEdit = isEdit,
                sellingBranchName = if (isEdit) state.selectedOrder?.sellingBranchName.orEmpty()
                                    else selectedSellingBranch?.name.orEmpty(),
                producingBranchName = if (isEdit) state.selectedOrder?.producingBranchName.orEmpty()
                                      else selectedProducingBranch?.name.orEmpty(),
                shiftName = selectedShift?.name.orEmpty(),
                dateMillis = targetDate,
                notes = notesText,
                lines = state.draftOrderLines,
                isLoading = state.isLoading,
                onBack = { step = 2 },
                onSend = {
                    if (isEdit) {
                        val id = orderId ?: return@OrderReviewStep
                        viewModel.updateExistingOrder(companyId, id, selectedShift, notesText)
                    } else {
                        val producingBranch = selectedProducingBranch ?: return@OrderReviewStep
                        val sellingBranch = selectedSellingBranch ?: return@OrderReviewStep
                        val currentUser = user ?: return@OrderReviewStep
                        viewModel.saveOrder(
                            companyId, currentUser, sellingBranch, producingBranch,
                            selectedShift, targetDate, notesText
                        )
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderFormStep1(
    modifier: Modifier,
    producingBranches: List<Branch>,
    selectedProducingBranch: Branch?,
    branchDropdownExpanded: Boolean,
    onBranchDropdownToggle: (Boolean) -> Unit,
    onBranchSelected: (Branch) -> Unit,
    sellingBranches: List<Branch>,
    selectedSellingBranch: Branch?,
    sellingBranchDropdownExpanded: Boolean,
    onSellingBranchDropdownToggle: (Boolean) -> Unit,
    onSellingBranchSelected: (Branch) -> Unit,
    shifts: List<ProductionShift>,
    selectedShift: ProductionShift?,
    onShiftSelected: (ProductionShift) -> Unit,
    targetDate: Long,
    onDateChange: (Long) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    onNext: () -> Unit,
    singleSellingBranch: Boolean = false
) {
    AdaptiveContainer(modifier = modifier) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            singleSellingBranch -> {
                Text("Pedido desde", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF123456))
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            selectedSellingBranch?.name ?: sellingBranches.firstOrNull()?.name ?: "",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF123456)
                        )
                    }
                }
            }
            sellingBranches.size > 1 -> {
                Text("¿Desde qué sucursal?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF123456))
                ExposedDropdownMenuBox(
                    expanded = sellingBranchDropdownExpanded,
                    onExpandedChange = onSellingBranchDropdownToggle
                ) {
                    OutlinedTextField(
                        value = selectedSellingBranch?.name ?: "Seleccionar mi sucursal",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sucursal solicitante *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sellingBranchDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = sellingBranchDropdownExpanded,
                        onDismissRequest = { onSellingBranchDropdownToggle(false) }
                    ) {
                        sellingBranches.forEach { branch ->
                            DropdownMenuItem(
                                text = { Text(branch.name) },
                                onClick = { onSellingBranchSelected(branch) }
                            )
                        }
                    }
                }
            }
        }

        Text("¿A qué planta va el pedido?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF123456))

        ExposedDropdownMenuBox(
            expanded = branchDropdownExpanded,
            onExpandedChange = onBranchDropdownToggle
        ) {
            OutlinedTextField(
                value = selectedProducingBranch?.name ?: "Seleccionar planta productora",
                onValueChange = {},
                readOnly = true,
                label = { Text("Planta productora *") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(branchDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = branchDropdownExpanded,
                onDismissRequest = { onBranchDropdownToggle(false) }
            ) {
                producingBranches.forEach { branch ->
                    DropdownMenuItem(
                        text = { Text(branch.name) },
                        onClick = { onBranchSelected(branch) }
                    )
                }
            }
        }

        // Selector de turno cuando la planta productora tiene turnos configurados.
        ShiftSelector(
            shifts = shifts,
            selectedShift = selectedShift,
            onShiftSelected = onShiftSelected,
            visible = selectedProducingBranch != null
        )

        Spacer(Modifier.height(8.dp))
        Text("¿Para qué fecha?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF123456))

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onDateChange(targetDate - 86_400_000L) }) {
                    Icon(Icons.Default.ChevronLeft, "Día anterior")
                }
                Text(
                    formatDate(targetDate),
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF123456),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { onDateChange(targetDate + 86_400_000L) }) {
                    Icon(Icons.Default.ChevronRight, "Día siguiente")
                }
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text("Notas (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(Modifier.height(24.dp))

        val shiftRequired = shifts.isNotEmpty() && selectedShift == null
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123456)),
            enabled = selectedProducingBranch != null && selectedSellingBranch != null && !shiftRequired
        ) {
            Text(
                if (shiftRequired) "Selecciona un turno"
                else "Continuar → Agregar productos",
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
    }
    }
}

@Composable
private fun ShiftSelector(
    shifts: List<ProductionShift>,
    selectedShift: ProductionShift?,
    onShiftSelected: (ProductionShift) -> Unit,
    visible: Boolean
) {
    if (!visible) return
    if (shifts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("¿Para qué turno?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF123456))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shifts.forEach { shift ->
                val selected = selectedShift?.id == shift.id
                FilterChip(
                    selected = selected,
                    onClick = { onShiftSelected(shift) },
                    label = {
                        Column {
                            Text(shift.name, fontWeight = FontWeight.SemiBold)
                            if (shift.startTime.isNotBlank() && shift.endTime.isNotBlank()) {
                                Text(
                                    "${shift.startTime} – ${shift.endTime}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF123456),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

@Composable
private fun OrderFormStep2(
    modifier: Modifier,
    isEdit: Boolean,
    headerSellingBranchName: String,
    headerProducingBranchName: String,
    headerDateMillis: Long,
    shifts: List<ProductionShift>,
    selectedShift: ProductionShift?,
    onShiftSelected: (ProductionShift) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    products: List<Product>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    draftLines: List<ProductionOrderLine>,
    onLineChanged: (ProductionOrderLine) -> Unit,
    isLoading: Boolean,
    onConfirm: () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // En edición + pantalla ancha: dos columnas (info izquierda, productos derecha).
        // En crear o pantalla angosta: una sola columna con el container adaptativo.
        val useTwoColumns = isEdit && maxWidth >= 800.dp

        @Composable
        fun EditHeaderSection(modifier: Modifier = Modifier) {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "$headerSellingBranchName → $headerProducingBranchName",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF123456)
                        )
                        Text(
                            "Para el ${formatDate(headerDateMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Sucursal origen, planta y fecha no se editan. Si te equivocaste, " +
                                "cancela y crea uno nuevo.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCC5500)
                        )
                    }
                }
                if (shifts.isNotEmpty()) {
                    ShiftSelector(
                        shifts = shifts,
                        selectedShift = selectedShift,
                        onShiftSelected = onShiftSelected,
                        visible = true
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text("Notas (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        }

        @Composable
        fun ProductsSection(modifier: Modifier = Modifier) {
            Column(modifier = modifier) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text("Buscar producto") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )
                val totalItems = draftLines.sumOf { it.requestedQuantity }.toInt()
                if (totalItems > 0) {
                    Text(
                        "$totalItems unidades en el pedido",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFCC5500),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(products) { product ->
                        val existingLine = draftLines.find { it.productId == product.id }
                        ProductOrderRow(
                            product = product,
                            quantity = existingLine?.requestedQuantity ?: 0.0,
                            onQuantityChange = { qty ->
                                onLineChanged(
                                    ProductionOrderLine(
                                        id = existingLine?.id ?: "",
                                        productId = product.id,
                                        productName = product.name,
                                        requestedQuantity = qty,
                                        productUnit = product.unit
                                    )
                                )
                            }
                        )
                    }
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123456)),
                    enabled = draftLines.isNotEmpty() && !isLoading
                ) {
                    Text(
                        if (isEdit) "Revisar cambios (${draftLines.size} productos) →"
                        else "Revisar pedido (${draftLines.size} productos) →",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (useTwoColumns) {
            Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 12.dp)
                ) {
                    EditHeaderSection(modifier = Modifier.fillMaxWidth())
                }
                VerticalDivider(color = Color(0xFFE0E0E0))
                ProductsSection(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .padding(start = 12.dp)
                )
            }
        } else {
            AdaptiveContainer {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    if (isEdit) {
                        EditHeaderSection(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                    }
                    ProductsSection(modifier = Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun OrderReviewStep(
    modifier: Modifier,
    isEdit: Boolean,
    sellingBranchName: String,
    producingBranchName: String,
    shiftName: String,
    dateMillis: Long,
    notes: String,
    lines: List<ProductionOrderLine>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onSend: () -> Unit
) {
    AdaptiveContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Aviso de revisión.
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFFF8E1),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⚠",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFCC5500)
                    )
                    Text(
                        "Revisa bien tu pedido antes de enviarlo. Una vez enviado, " +
                            "la planta productora podrá tomarlo y modificarlo será limitado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7A4500),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Datos generales del pedido.
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Resumen del pedido",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF123456)
                    )
                    HorizontalDivider()
                    ReviewRow("Sucursal solicitante", sellingBranchName.ifBlank { "—" })
                    ReviewRow("Planta productora", producingBranchName.ifBlank { "—" })
                    if (shiftName.isNotBlank()) ReviewRow("Turno", shiftName)
                    ReviewRow("Para el día", formatDate(dateMillis))
                    if (notes.isNotBlank()) ReviewRow("Notas", notes)
                }
            }

            // Productos solicitados.
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Productos (${lines.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF123456)
                        )
                        val totalUnits = lines.sumOf { it.requestedQuantity }.toInt()
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                "$totalUnits unidades total",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF123456)
                            )
                        }
                    }
                    HorizontalDivider()
                    lines.forEachIndexed { index, line ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    line.productName,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF123456),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (line.productUnit.isNotBlank()) {
                                    Text(
                                        line.productUnit,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Text(
                                "${line.requestedQuantity.toInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFCC5500)
                            )
                        }
                        if (index < lines.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Botones: Editar (izquierda) + Enviar (derecha, azul tema).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF123456))
                ) {
                    Text(
                        if (isEdit) "Editar cambios" else "Editar pedido",
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = onSend,
                    enabled = !isLoading && lines.isNotEmpty(),
                    modifier = Modifier.weight(1.3f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123456))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            if (isEdit) "Enviar cambios" else "Enviar pedido",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF123456)
        )
    }
}

@Composable
private fun ProductOrderRow(
    product: Product,
    quantity: Double,
    onQuantityChange: (Double) -> Unit
) {
    var qtyText by remember(quantity) {
        mutableStateOf(if (quantity > 0) quantity.toInt().toString() else "")
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, color = Color(0xFF123456))
                Text(product.unit, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Spacer(Modifier.width(8.dp))
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

internal fun startOfNextDay(): Long {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val tomorrow = today.plus(1, DateTimeUnit.DAY)
    return kotlinx.datetime.LocalDateTime(tomorrow, kotlinx.datetime.LocalTime(0, 0, 0))
        .toInstant(tz).toEpochMilliseconds()
}

internal fun formatDate(millis: Long): String {
    if (millis == 0L) return "—"
    return runCatching {
        val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        val dayName = when (ldt.date.dayOfWeek) {
            kotlinx.datetime.DayOfWeek.MONDAY    -> "Lunes"
            kotlinx.datetime.DayOfWeek.TUESDAY   -> "Martes"
            kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Miércoles"
            kotlinx.datetime.DayOfWeek.THURSDAY  -> "Jueves"
            kotlinx.datetime.DayOfWeek.FRIDAY    -> "Viernes"
            kotlinx.datetime.DayOfWeek.SATURDAY  -> "Sábado"
            kotlinx.datetime.DayOfWeek.SUNDAY    -> "Domingo"
            else                                 -> ""
        }
        "$dayName ${ldt.dayOfMonth.toString().padStart(2,'0')}/${ldt.monthNumber.toString().padStart(2,'0')}/${ldt.year}"
    }.getOrElse { "—" }
}
