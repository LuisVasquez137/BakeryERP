package com.bakery.erp.presentation.purchases

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Branch
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseStatus
import com.bakery.erp.model.Supplier
import com.bakery.erp.model.convertUnits
import com.bakery.erp.model.largestUnit
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseFormScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSuppliers: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: PurchaseViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val currentUser = authState.user
    val snackbarHostState = remember { SnackbarHostState() }
    var invoiceNumber by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var purchaseStatus by rememberSaveable { mutableStateOf(PurchaseStatus.RECEIVED_PENDING_PAYMENT) }
    var ingredientSearch by rememberSaveable { mutableStateOf("") }
    var showReview by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(companyId, currentUser) {
        if (companyId.isNotEmpty() && currentUser != null) viewModel.loadFormData(companyId, currentUser)
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.resetForm(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.selectedSupplier) { ingredientSearch = "" }

    val filteredIngredients = remember(state.supplierIngredients, ingredientSearch) {
        if (ingredientSearch.isBlank()) state.supplierIngredients
        else state.supplierIngredients.filter { it.name.contains(ingredientSearch.trim(), ignoreCase = true) }
    }

    fun doSave() {
        if (currentUser != null) {
            viewModel.savePurchase(
                companyId = companyId,
                currentUser = currentUser,
                invoiceNumber = invoiceNumber.trim().ifEmpty { null },
                notes = notes.trim().ifEmpty { null },
                date = System.currentTimeMillis(),
                status = purchaseStatus
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (showReview) "Revisar pedido" else "Nueva Compra",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (showReview) showReview = false else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth >= 600.dp

                if (showReview && state.selectedBranch != null && state.selectedSupplier != null) {
                    PurchaseReviewContent(
                        branch = state.selectedBranch!!,
                        supplier = state.selectedSupplier!!,
                        invoiceNumber = invoiceNumber,
                        notes = notes,
                        purchaseStatus = purchaseStatus,
                        draftLines = state.draftLines,
                        isLoading = state.isLoading,
                        onBack = { showReview = false },
                        onConfirm = ::doSave
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ── PASO 1: Sucursal ──────────────────────────────────────────
                        StepHeader("1. Sucursal destino")
                        when {
                            state.isLoadingBranches -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            state.accessibleBranches.isEmpty() -> InfoCard(
                                "No tenés acceso a ninguna sucursal productora o de almacén. " +
                                "Contactá al administrador para que te asigne una sucursal."
                            )
                            state.selectedBranch == null -> BranchSelector(
                                branches = state.accessibleBranches,
                                onSelect = { viewModel.selectBranch(it) }
                            )
                            else -> SelectedItem(
                                label = state.selectedBranch!!.name,
                                onClear = if (state.accessibleBranches.size > 1) ({ viewModel.selectBranch(null) }) else null
                            )
                        }

                        // ── PASO 2: Proveedor ─────────────────────────────────────────
                        if (state.selectedBranch != null) {
                            HorizontalDivider()
                            StepHeader("2. Proveedor")
                            when {
                                state.selectedSupplier == null -> SupplierSelector(
                                    suppliers = state.suppliers,
                                    onSelect = { viewModel.selectSupplier(companyId, it) },
                                    onNavigateToSuppliers = onNavigateToSuppliers
                                )
                                else -> {
                                    SelectedItem(
                                        label = state.selectedSupplier!!.company,
                                        onClear = { viewModel.selectSupplier(companyId, null) }
                                    )

                                    OutlinedTextField(
                                        value = invoiceNumber,
                                        onValueChange = { invoiceNumber = it },
                                        label = { Text("Nº de factura (opcional)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    // ── PASO 3: Insumos ───────────────────────────────
                                    HorizontalDivider()
                                    StepHeader("3. Insumos recibidos")

                                    if (currentUser != null) {
                                        Text(
                                            text = "Registrando como: ${currentUser.displayName.ifEmpty { currentUser.email }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    if (state.isLoadingIngredients) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    } else if (state.supplierIngredients.isEmpty()) {
                                        InfoCard(
                                            "Este proveedor no tiene insumos asociados. " +
                                            "Agregá una presentación de compra en el detalle de un insumo."
                                        )
                                    } else {
                                        if (state.supplierIngredients.size > 4) {
                                            OutlinedTextField(
                                                value = ingredientSearch,
                                                onValueChange = { ingredientSearch = it },
                                                label = { Text("Buscar insumo") },
                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                                trailingIcon = if (ingredientSearch.isNotEmpty()) ({
                                                    IconButton(onClick = { ingredientSearch = "" }) {
                                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    }
                                                }) else null,
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                        }

                                        if (filteredIngredients.isEmpty()) {
                                            Text(
                                                "Sin resultados para \"$ingredientSearch\"",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        } else if (isWide) {
                                            IngredientTable(
                                                ingredients = filteredIngredients,
                                                supplierId = state.selectedSupplier!!.id,
                                                draftLines = state.draftLines,
                                                onLineChanged = { viewModel.updateDraftLine(it) }
                                            )
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                filteredIngredients.forEach { ingredient ->
                                                    val existingLine = state.draftLines.find { it.ingredientId == ingredient.id }
                                                    IngredientPurchaseRow(
                                                        ingredient = ingredient,
                                                        supplierId = state.selectedSupplier!!.id,
                                                        existingLine = existingLine,
                                                        onLineChanged = { viewModel.updateDraftLine(it) }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(4.dp))

                                        Text(
                                            text = "Estado de la compra",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(
                                                PurchaseStatus.RECEIVED_PENDING_PAYMENT to "Recibido",
                                                PurchaseStatus.PAID to "Pagado",
                                                PurchaseStatus.PENDING_RECEIPT to "Por recibir"
                                            ).forEach { (status, label) ->
                                                if (purchaseStatus == status) {
                                                    Button(
                                                        onClick = { purchaseStatus = status },
                                                        modifier = Modifier.weight(1f),
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                    }
                                                } else {
                                                    OutlinedButton(
                                                        onClick = { purchaseStatus = status },
                                                        modifier = Modifier.weight(1f),
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(label, style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = notes,
                                            onValueChange = { notes = it },
                                            label = { Text("Observaciones (opcional)") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 2,
                                            maxLines = 4
                                        )

                                        if (isWide) {
                                            Button(
                                                onClick = ::doSave,
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled = state.draftLines.isNotEmpty() && !state.isLoading
                                            ) {
                                                if (state.isLoading) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        strokeWidth = 2.dp
                                                    )
                                                } else {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("Confirmar compra", fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        } else {
                                            Button(
                                                onClick = { showReview = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled = state.draftLines.isNotEmpty()
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Revisar pedido", fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

// ── Tabla de insumos (pantallas anchas) ──────────────────────────────────────

@Composable
private fun IngredientTable(
    ingredients: List<Ingredient>,
    supplierId: String,
    draftLines: List<PurchaseLine>,
    onLineChanged: (PurchaseLine) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Insumo",
                        modifier = Modifier.weight(2.5f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Precio / pres.",
                        modifier = Modifier.weight(1.5f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End
                    )
                    Text(
                        "Cantidad",
                        modifier = Modifier.weight(1.5f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Subtotal",
                        modifier = Modifier.weight(1.2f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End
                    )
                }
            }
            HorizontalDivider()
            ingredients.forEachIndexed { index, ingredient ->
                val existingLine = draftLines.find { it.ingredientId == ingredient.id }
                IngredientTableRow(
                    ingredient = ingredient,
                    supplierId = supplierId,
                    existingLine = existingLine,
                    onLineChanged = onLineChanged
                )
                if (index < ingredients.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun IngredientTableRow(
    ingredient: Ingredient,
    supplierId: String,
    existingLine: PurchaseLine?,
    onLineChanged: (PurchaseLine) -> Unit
) {
    val defaultFormat = ingredient.purchaseFormats.firstOrNull { it.isDefault && it.supplierId == supplierId }
        ?: ingredient.purchaseFormats.firstOrNull { it.supplierId == supplierId }
        ?: ingredient.purchaseFormats.firstOrNull()

    val toBaseUnitFactor = if (defaultFormat != null) {
        convertUnits(defaultFormat.unitsPerPackage, defaultFormat.unit, ingredient.effectiveBaseUnit)
    } else {
        convertUnits(1.0, largestUnit(ingredient.allowedUnits), ingredient.effectiveBaseUnit)
    }

    val originalPackageCost = defaultFormat?.packageCost ?: 0.0
    val unitLabel = defaultFormat?.description?.split(" ")?.firstOrNull()?.lowercase()
        ?: ingredient.effectiveBaseUnit

    var editingPrice by remember(ingredient.id) { mutableStateOf(false) }
    var packageCostStr by rememberSaveable(ingredient.id) {
        mutableStateOf(if (originalPackageCost > 0) "%.2f".format(originalPackageCost) else "")
    }
    var showQtyDialog by remember { mutableStateOf(false) }

    val currentPackageCost = packageCostStr.toDoubleOrNull() ?: originalPackageCost
    val currentQty = existingLine?.packagesReceived?.takeIf { it > 0 }
        ?: existingLine?.let { if (toBaseUnitFactor > 0) it.quantityReceived / toBaseUnitFactor else it.quantityReceived }
        ?: 0.0
    val lineTotal = currentQty * currentPackageCost
    val isSelected = existingLine != null && currentQty > 0

    fun buildLine(packages: Double): PurchaseLine {
        val cost = packageCostStr.toDoubleOrNull() ?: originalPackageCost
        return PurchaseLine(
            ingredientId = ingredient.id,
            ingredientName = ingredient.name,
            ingredientUnit = ingredient.effectiveBaseUnit,
            quantityReceived = packages * toBaseUnitFactor,
            packagesReceived = if (defaultFormat != null) packages else 0.0,
            packageDescription = defaultFormat?.description ?: largestUnit(ingredient.allowedUnits),
            unitCost = if (defaultFormat != null && defaultFormat.unitsPerPackage > 0)
                cost / defaultFormat.unitsPerPackage else 0.0,
            packageCost = cost,
            formatId = defaultFormat?.id ?: ""
        )
    }

    Surface(color = if (isSelected) Color(0xFFFFF3DC) else MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Insumo
            Column(modifier = Modifier.weight(2.5f)) {
                Text(ingredient.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                if (defaultFormat != null) {
                    Text(
                        defaultFormat.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Precio
            if (editingPrice) {
                Row(
                    modifier = Modifier.weight(1.5f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedTextField(
                        value = packageCostStr,
                        onValueChange = { packageCostStr = it },
                        prefix = { Text("Q", style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    IconButton(
                        onClick = {
                            editingPrice = false
                            if (existingLine != null) onLineChanged(buildLine(currentQty))
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Listo",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.weight(1.5f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = if (currentPackageCost > 0) "Q ${"%.2f".format(currentPackageCost)}" else "—",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                    IconButton(
                        onClick = { editingPrice = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar precio",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Cantidad
            Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
                if (isSelected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "${formatPurchaseQty(currentQty)} $unitLabel",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = { showQtyDialog = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar cantidad",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showQtyDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Agregar", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Subtotal
            Text(
                text = if (lineTotal > 0) "Q ${"%.2f".format(lineTotal)}" else "—",
                modifier = Modifier.weight(1.2f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (lineTotal > 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (lineTotal > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End
            )
        }
    }

    if (showQtyDialog) {
        QuantityInputDialog(
            ingredientName = ingredient.name,
            unitLabel = unitLabel,
            initialQty = if (currentQty > 0) formatPurchaseQty(currentQty) else "",
            onDismiss = { showQtyDialog = false },
            onConfirm = { qtyStr ->
                showQtyDialog = false
                onLineChanged(buildLine(qtyStr.toDoubleOrNull() ?: 0.0))
            }
        )
    }
}

@Composable
private fun QuantityInputDialog(
    ingredientName: String,
    unitLabel: String,
    initialQty: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var qty by remember { mutableStateOf(initialQty) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(ingredientName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        },
        text = {
            OutlinedTextField(
                value = qty,
                onValueChange = { qty = it },
                label = { Text("Cantidad") },
                suffix = { Text(unitLabel, style = MaterialTheme.typography.labelSmall) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(qty) },
                enabled = qty.toDoubleOrNull()?.let { it > 0 } == true
            ) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ── Pantalla de revisión (móvil) ─────────────────────────────────────────────

@Composable
private fun PurchaseReviewContent(
    branch: Branch,
    supplier: Supplier,
    invoiceNumber: String,
    notes: String,
    purchaseStatus: String,
    draftLines: List<PurchaseLine>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    val grandTotal = draftLines
        .filter { it.packageCost > 0 }
        .sumOf { it.packageCost * it.packagesReceived.coerceAtLeast(1.0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Encabezado del pedido
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReviewDetailRow("Sucursal", branch.name)
                ReviewDetailRow("Proveedor", supplier.company)
                if (invoiceNumber.isNotBlank()) ReviewDetailRow("Nº Factura", invoiceNumber)
                ReviewDetailRow("Estado", PurchaseStatus.label(purchaseStatus))
            }
        }

        // Lista de insumos
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "INSUMOS A RECIBIR",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            "${draftLines.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Insumo", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text("Cant.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.End)
                    Text("Total", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.End)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                draftLines.forEachIndexed { index, line ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(2f)) {
                            Text(line.ingredientName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            if (line.packageDescription.isNotEmpty()) {
                                Text(line.packageDescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val qtyText = if (line.packagesReceived > 0 && line.packageDescription.isNotEmpty()) {
                            val u = line.packageDescription.split(" ").firstOrNull()?.lowercase() ?: ""
                            "${formatPurchaseQty(line.packagesReceived)} $u"
                        } else {
                            "${formatPurchaseQty(line.quantityReceived)} ${line.ingredientUnit}"
                        }
                        Text(
                            qtyText,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End
                        )
                        val lineTotal = if (line.packageCost > 0) line.packageCost * line.packagesReceived.coerceAtLeast(1.0) else 0.0
                        Text(
                            if (lineTotal > 0) "Q ${"%.2f".format(lineTotal)}" else "—",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (lineTotal > 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (lineTotal > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.End
                        )
                    }
                    if (index < draftLines.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        // Total
        if (grandTotal > 0) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TOTAL", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Q ${"%.2f".format(grandTotal)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Observaciones
        if (notes.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("OBSERVACIONES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("Editar")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar compra", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ReviewDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(90.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun StepHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun InfoCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp).padding(top = 1.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun SelectedItem(label: String, onClear: (() -> Unit)?) {
    FilterChip(
        selected = true,
        onClick = {},
        label = { Text(label) },
        trailingIcon = if (onClear != null) ({
            IconButton(onClick = onClear, modifier = Modifier.size(18.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Limpiar", modifier = Modifier.size(16.dp))
            }
        }) else null
    )
}

@Composable
private fun BranchSelector(branches: List<Branch>, onSelect: (Branch) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        branches.forEach { branch ->
            Card(
                onClick = { onSelect(branch) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                ListItem(
                    headlineContent = { Text(branch.name, fontWeight = FontWeight.Medium) },
                    supportingContent = if (branch.location.isNotEmpty()) ({ Text(branch.location) }) else null,
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    }
}

@Composable
private fun SupplierSelector(
    suppliers: List<Supplier>,
    onSelect: (Supplier) -> Unit,
    onNavigateToSuppliers: () -> Unit
) {
    if (suppliers.isEmpty()) {
        InfoCard("No hay proveedores registrados. Registrá uno en el módulo Proveedores primero.")
        Spacer(Modifier.height(4.dp))
        Button(onClick = onNavigateToSuppliers, modifier = Modifier.fillMaxWidth()) {
            Text("Ir a Proveedores")
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suppliers.forEach { supplier ->
                Card(
                    onClick = { onSelect(supplier) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    ListItem(
                        headlineContent = { Text(supplier.company, fontWeight = FontWeight.Medium) },
                        supportingContent = if (supplier.salesperson.isNotEmpty()) ({ Text(supplier.salesperson) }) else null,
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }
        }
    }
}

@Composable
private fun IngredientPurchaseRow(
    ingredient: Ingredient,
    supplierId: String,
    existingLine: PurchaseLine?,
    onLineChanged: (PurchaseLine) -> Unit
) {
    val defaultFormat = ingredient.purchaseFormats.firstOrNull { it.isDefault && it.supplierId == supplierId }
        ?: ingredient.purchaseFormats.firstOrNull { it.supplierId == supplierId }
        ?: ingredient.purchaseFormats.firstOrNull()

    val toBaseUnitFactor = if (defaultFormat != null) {
        convertUnits(defaultFormat.unitsPerPackage, defaultFormat.unit, ingredient.effectiveBaseUnit)
    } else {
        convertUnits(1.0, largestUnit(ingredient.allowedUnits), ingredient.effectiveBaseUnit)
    }

    val originalPackageCost = defaultFormat?.packageCost ?: 0.0

    var qty by rememberSaveable(ingredient.id) {
        mutableStateOf(
            existingLine?.let {
                val displayed = if (it.packagesReceived > 0) it.packagesReceived
                               else if (toBaseUnitFactor > 0) it.quantityReceived / toBaseUnitFactor
                               else it.quantityReceived
                if (displayed > 0) "%.2f".format(displayed).trimEnd('0').trimEnd('.') else ""
            } ?: ""
        )
    }

    var editingPrice by remember(ingredient.id) { mutableStateOf(false) }
    var packageCostStr by rememberSaveable(ingredient.id) {
        mutableStateOf(if (originalPackageCost > 0) "%.2f".format(originalPackageCost) else "")
    }

    val currentPackageCost = packageCostStr.toDoubleOrNull() ?: originalPackageCost
    val currentQty = qty.toDoubleOrNull() ?: 0.0
    val lineTotal = currentQty * currentPackageCost
    val priceChanged = currentPackageCost > 0 && currentPackageCost != originalPackageCost

    fun buildLine(): PurchaseLine {
        val packages = qty.toDoubleOrNull() ?: 0.0
        return PurchaseLine(
            ingredientId = ingredient.id,
            ingredientName = ingredient.name,
            ingredientUnit = ingredient.effectiveBaseUnit,
            quantityReceived = packages * toBaseUnitFactor,
            packagesReceived = if (defaultFormat != null) packages else 0.0,
            packageDescription = defaultFormat?.description ?: largestUnit(ingredient.allowedUnits),
            unitCost = if (defaultFormat != null && defaultFormat.unitsPerPackage > 0)
                currentPackageCost / defaultFormat.unitsPerPackage else 0.0,
            packageCost = currentPackageCost,
            formatId = defaultFormat?.id ?: ""
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (existingLine != null) Color(0xFFFFF3DC) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(ingredient.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (defaultFormat != null) {
                        Text(
                            text = defaultFormat.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedTextField(
                    value = qty,
                    onValueChange = { raw -> qty = raw; onLineChanged(buildLine()) },
                    label = { Text("Cantidad") },
                    suffix = { Text(
                        defaultFormat?.description?.split(" ")?.firstOrNull()?.lowercase()
                            ?: ingredient.effectiveBaseUnit,
                        style = MaterialTheme.typography.labelSmall
                    ) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.width(115.dp)
                )
            }

            if (defaultFormat != null || originalPackageCost > 0 || currentQty > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (editingPrice) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = packageCostStr,
                            onValueChange = { raw -> packageCostStr = raw; onLineChanged(buildLine()) },
                            label = { Text("Precio / presentación (Q)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { editingPrice = false }) {
                            Icon(Icons.Default.Check, contentDescription = "Listo", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (currentPackageCost > 0)
                                    "Q ${"%.2f".format(currentPackageCost)} / ${defaultFormat?.description ?: "paquete"}"
                                else "Sin precio",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (priceChanged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                fontWeight = if (priceChanged) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (priceChanged) {
                                Text(
                                    text = "Antes: Q ${"%.2f".format(originalPackageCost)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        if (currentQty > 0 && currentPackageCost > 0) {
                            Text(
                                text = "Q ${"%.2f".format(lineTotal)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        IconButton(
                            onClick = { editingPrice = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar precio",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}
