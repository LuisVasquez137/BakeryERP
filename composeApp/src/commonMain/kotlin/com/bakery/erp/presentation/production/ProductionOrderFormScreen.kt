package com.bakery.erp.presentation.production

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionOrderFormScreen(
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

    var step by rememberSaveable { mutableIntStateOf(1) }
    var selectedProducingBranch by remember { mutableStateOf<Branch?>(null) }
    var selectedSellingBranch by remember { mutableStateOf<Branch?>(null) }
    var branchDropdownExpanded by remember { mutableStateOf(false) }
    var sellingBranchDropdownExpanded by remember { mutableStateOf(false) }
    var targetDate by remember { mutableStateOf(startOfNextDay()) }
    var notesText by rememberSaveable { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) {
            viewModel.loadOrderFormData(companyId, user)
            productViewModel.loadProducts(companyId)
        }
    }

    LaunchedEffect(state.producingBranches) {
        if (selectedProducingBranch == null && state.producingBranches.size == 1) {
            selectedProducingBranch = state.producingBranches.first()
        }
    }
    LaunchedEffect(state.sellingBranches) {
        if (selectedSellingBranch == null && state.sellingBranches.size == 1) {
            selectedSellingBranch = state.sellingBranches.first()
        }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.resetOrderForm(); onNavigateBack() }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val displayProducts = remember(productState.productList, searchQuery, selectedProducingBranch) {
        val base = selectedProducingBranch
            ?.assignedProductIds
            ?.takeIf { it.isNotEmpty() }
            ?.let { ids -> productState.productList.filter { it.id in ids } }
            ?: productState.productList
        base.filter {
            it.active && (searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true))
        }.sortedBy { it.name }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (step == 1) "Nuevo Pedido (1/2)" else "Agregar Productos (2/2)",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (step == 2) step = 1 else onNavigateBack() }) {
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
        if (step == 1) {
            OrderFormStep1(
                modifier = Modifier.padding(padding),
                producingBranches = state.producingBranches,
                selectedProducingBranch = selectedProducingBranch,
                branchDropdownExpanded = branchDropdownExpanded,
                onBranchDropdownToggle = { branchDropdownExpanded = it },
                onBranchSelected = { branch ->
                    if (branch.id != selectedProducingBranch?.id) viewModel.clearDraftOrderLines()
                    selectedProducingBranch = branch
                    branchDropdownExpanded = false
                },
                sellingBranches = state.sellingBranches,
                selectedSellingBranch = selectedSellingBranch,
                sellingBranchDropdownExpanded = sellingBranchDropdownExpanded,
                onSellingBranchDropdownToggle = { sellingBranchDropdownExpanded = it },
                onSellingBranchSelected = { selectedSellingBranch = it; sellingBranchDropdownExpanded = false },
                targetDate = targetDate,
                onDateChange = { targetDate = it },
                notes = notesText,
                onNotesChange = { notesText = it },
                onNext = {
                    if (selectedProducingBranch != null && selectedSellingBranch != null) step = 2
                },
                singleSellingBranch = state.sellingBranches.size == 1
            )
        } else {
            OrderFormStep2(
                modifier = Modifier.padding(padding),
                products = displayProducts,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                draftLines = state.draftOrderLines,
                onLineChanged = { viewModel.updateDraftOrderLine(it) },
                isLoading = state.isLoading,
                onConfirm = {
                    val producingBranch = selectedProducingBranch ?: return@OrderFormStep2
                    val sellingBranch = selectedSellingBranch ?: return@OrderFormStep2
                    val currentUser = user ?: return@OrderFormStep2
                    viewModel.saveOrder(companyId, currentUser, sellingBranch, producingBranch, targetDate, notesText)
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
    targetDate: Long,
    onDateChange: (Long) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    onNext: () -> Unit,
    singleSellingBranch: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
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
                        label = { Text("Mi sucursal *") },
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

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123456)),
            enabled = selectedProducingBranch != null && selectedSellingBranch != null
        ) { Text("Continuar → Agregar productos", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun OrderFormStep2(
    modifier: Modifier,
    products: List<Product>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    draftLines: List<ProductionOrderLine>,
    onLineChanged: (ProductionOrderLine) -> Unit,
    isLoading: Boolean,
    onConfirm: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = { Text("Buscar producto") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true
        )

        val totalItems = draftLines.sumOf { it.requestedQuantity }.toInt()
        if (totalItems > 0) {
            Text(
                "$totalItems unidades en el pedido",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFCC5500),
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123456)),
            enabled = draftLines.isNotEmpty() && !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
            else Text("Enviar pedido (${draftLines.size} productos)", fontWeight = FontWeight.Bold)
        }
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
