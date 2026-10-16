package com.bakery.erp.presentation.assets

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.foundation.text.KeyboardOptions
import com.bakery.erp.model.ASSET_CATEGORIES
import com.bakery.erp.model.AssetStatus
import com.bakery.erp.model.Branch
import com.bakery.erp.model.FixedAsset
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.branches.BranchViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.ImagePickerButton
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedAssetFormScreen(
    assetId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    branchViewModel: BranchViewModel,
    viewModel: FixedAssetViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val branchState by branchViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""

    val needsLoad = !assetId.isNullOrEmpty() && (state.selectedAsset == null || state.selectedAsset?.id != assetId)

    LaunchedEffect(assetId, companyId) {
        if (companyId.isNotEmpty()) {
            branchViewModel.loadBranches(companyId)
            if (!assetId.isNullOrEmpty()) viewModel.loadAsset(companyId, assetId)
            else viewModel.selectAsset(null)
        }
    }

    if (needsLoad && state.isDetailLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val accessibleBranches = remember(branchState.branchList, user) {
        if (user == null) emptyList()
        else branchState.branchList.filter { it.active && user.canAccessBranch(it.id) }
    }

    FixedAssetFormContent(
        existing = if (assetId == null) null else state.selectedAsset,
        accessibleBranches = accessibleBranches,
        companyId = companyId,
        isSaving = state.isSaving,
        isSaved = state.isSaved,
        onNavigateBack = onNavigateBack,
        onSave = { asset, imageBytes -> viewModel.saveAsset(companyId, asset, imageBytes, user) },
        onClearSaved = { viewModel.clearSaved() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixedAssetFormContent(
    existing: FixedAsset?,
    accessibleBranches: List<Branch>,
    companyId: String,
    isSaving: Boolean,
    isSaved: Boolean,
    onNavigateBack: () -> Unit,
    onSave: (FixedAsset, ByteArray?) -> Unit,
    onClearSaved: () -> Unit
) {
    val isNew = existing == null
    val snackbarHostState = remember { SnackbarHostState() }

    // Básico
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var assetCode by rememberSaveable { mutableStateOf(existing?.assetCode ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "") }
    var branchId by rememberSaveable { mutableStateOf(existing?.branchId ?: "") }
    var branchName by rememberSaveable { mutableStateOf(existing?.branchName ?: "") }
    var location by rememberSaveable { mutableStateOf(existing?.location ?: "") }
    var responsible by rememberSaveable { mutableStateOf(existing?.responsible ?: "") }
    var status by rememberSaveable { mutableStateOf(existing?.status ?: AssetStatus.ACTIVE) }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }

    // Compra
    var purchaseDate by rememberSaveable { mutableStateOf(existing?.purchaseDate ?: 0L) }
    var purchaseCostText by rememberSaveable {
        mutableStateOf(existing?.purchaseCost?.takeIf { it > 0 }?.toString() ?: "")
    }
    var supplierName by rememberSaveable { mutableStateOf(existing?.supplierName ?: "") }
    var invoiceNumber by rememberSaveable { mutableStateOf(existing?.invoiceNumber ?: "") }
    var brand by rememberSaveable { mutableStateOf(existing?.brand ?: "") }
    var model by rememberSaveable { mutableStateOf(existing?.model ?: "") }
    var serialNumber by rememberSaveable { mutableStateOf(existing?.serialNumber ?: "") }
    var warrantyUntil by rememberSaveable { mutableStateOf(existing?.warrantyUntil ?: 0L) }

    // Depreciación
    var usefulLifeMonthsText by rememberSaveable {
        mutableStateOf(existing?.usefulLifeMonths?.toString() ?: "")
    }
    var residualValueText by rememberSaveable {
        mutableStateOf(existing?.residualValue?.takeIf { it > 0 }?.toString() ?: "")
    }

    // Mantenimiento
    var nextMaintenanceDate by rememberSaveable { mutableStateOf(existing?.nextMaintenanceDate ?: 0L) }
    var maintenanceIntervalDaysText by rememberSaveable {
        mutableStateOf(existing?.maintenanceIntervalDays?.toString() ?: "")
    }

    // Notas
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }

    // Diálogos
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var branchMenuExpanded by remember { mutableStateOf(false) }
    var statusMenuExpanded by remember { mutableStateOf(false) }
    var showPurchasePicker by remember { mutableStateOf(false) }
    var showWarrantyPicker by remember { mutableStateOf(false) }
    var showNextMaintenancePicker by remember { mutableStateOf(false) }

    var selectedTab by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(isSaved) {
        if (isSaved) { onClearSaved(); onNavigateBack() }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            BrandTopAppBar(
                title = { Text(if (isNew) "Nuevo activo" else "Editar activo", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AdaptiveContainer(maxContentWidth = 720.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                ) {
                    TabRow(selectedTabIndex = selectedTab, containerColor = WarmIvory, contentColor = PrimaryBlue) {
                        listOf("Básico", "Compra", "Depreciación", "Mantenimiento", "Notas").forEachIndexed { i, label ->
                            Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                                text = { Text(label, style = MaterialTheme.typography.labelMedium) })
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (selectedTab) {
                            0 -> {
                                SectionTitle("Foto del activo")
                                ImagePickerButton(
                                    currentImageUrl = existing?.imageUrl?.takeIf { it.isNotBlank() },
                                    selectedBytes = imageBytes,
                                    onImageSelected = { imageBytes = it }
                                )
                                SectionTitle("Identificación")
                                OutlinedTextField(
                                    value = name, onValueChange = { name = it },
                                    label = { Text("Nombre *") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                                OutlinedTextField(
                                    value = assetCode, onValueChange = { assetCode = it },
                                    label = { Text("Código interno (opcional)") },
                                    placeholder = { Text("HORN-001") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                                ExposedDropdownMenuBox(
                                    expanded = categoryMenuExpanded,
                                    onExpandedChange = { categoryMenuExpanded = !categoryMenuExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = category, onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Categoría *") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryMenuExpanded) },
                                        modifier = Modifier.fillMaxWidth().menuAnchor()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = categoryMenuExpanded,
                                        onDismissRequest = { categoryMenuExpanded = false }
                                    ) {
                                        ASSET_CATEGORIES.forEach { cat ->
                                            DropdownMenuItem(
                                                text = { Text(cat) },
                                                onClick = { category = cat; categoryMenuExpanded = false }
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = description, onValueChange = { description = it },
                                    label = { Text("Descripción") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2, maxLines = 4
                                )

                                SectionTitle("Ubicación")
                                ExposedDropdownMenuBox(
                                    expanded = branchMenuExpanded,
                                    onExpandedChange = { branchMenuExpanded = !branchMenuExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = branchName, onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Sucursal *") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(branchMenuExpanded) },
                                        modifier = Modifier.fillMaxWidth().menuAnchor()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = branchMenuExpanded,
                                        onDismissRequest = { branchMenuExpanded = false }
                                    ) {
                                        accessibleBranches.forEach { b ->
                                            DropdownMenuItem(
                                                text = { Text(b.name) },
                                                onClick = {
                                                    branchId = b.id; branchName = b.name
                                                    branchMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = location, onValueChange = { location = it },
                                    label = { Text("Lugar dentro de la sucursal") },
                                    placeholder = { Text("Área de producción, oficina…") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                                OutlinedTextField(
                                    value = responsible, onValueChange = { responsible = it },
                                    label = { Text("Responsable") },
                                    placeholder = { Text("Persona a cargo (texto libre)") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )

                                SectionTitle("Estado")
                                ExposedDropdownMenuBox(
                                    expanded = statusMenuExpanded,
                                    onExpandedChange = { statusMenuExpanded = !statusMenuExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = status.label, onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Estado") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusMenuExpanded) },
                                        modifier = Modifier.fillMaxWidth().menuAnchor()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = statusMenuExpanded,
                                        onDismissRequest = { statusMenuExpanded = false }
                                    ) {
                                        AssetStatus.entries.forEach { s ->
                                            DropdownMenuItem(
                                                text = { Text(s.label) },
                                                onClick = { status = s; statusMenuExpanded = false }
                                            )
                                        }
                                    }
                                }
                            }
                            1 -> {
                                SectionTitle("Datos de compra")
                                OutlinedTextField(
                                    value = if (purchaseDate > 0L) formatDate(purchaseDate) else "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Fecha de compra *") },
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        TextButton(onClick = { showPurchasePicker = true }) {
                                            Text("Elegir")
                                        }
                                    }
                                )
                                OutlinedTextField(
                                    value = purchaseCostText,
                                    onValueChange = { purchaseCostText = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("Costo original (Q) *") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                                OutlinedTextField(
                                    value = supplierName, onValueChange = { supplierName = it },
                                    label = { Text("Proveedor / Vendedor") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                                OutlinedTextField(
                                    value = invoiceNumber, onValueChange = { invoiceNumber = it },
                                    label = { Text("Factura / comprobante") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                                HorizontalDivider()
                                SectionTitle("Detalles del equipo")
                                OutlinedTextField(
                                    value = brand, onValueChange = { brand = it },
                                    label = { Text("Marca") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                                OutlinedTextField(
                                    value = model, onValueChange = { model = it },
                                    label = { Text("Modelo") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                                OutlinedTextField(
                                    value = serialNumber, onValueChange = { serialNumber = it },
                                    label = { Text("Número de serie") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                                OutlinedTextField(
                                    value = if (warrantyUntil > 0L) formatDate(warrantyUntil) else "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Garantía hasta") },
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        Row {
                                            if (warrantyUntil > 0L) TextButton(onClick = { warrantyUntil = 0L }) { Text("Quitar") }
                                            TextButton(onClick = { showWarrantyPicker = true }) { Text("Elegir") }
                                        }
                                    }
                                )
                            }
                            2 -> {
                                SectionTitle("Depreciación lineal")
                                Text(
                                    "Opcional. Si dejas la vida útil vacía, el activo no se deprecia (su valor en libros = costo original).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = usefulLifeMonthsText,
                                    onValueChange = { usefulLifeMonthsText = it.filter { c -> c.isDigit() } },
                                    label = { Text("Vida útil (meses)") },
                                    placeholder = { Text("Ej: 120 para 10 años") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                                OutlinedTextField(
                                    value = residualValueText,
                                    onValueChange = { residualValueText = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("Valor residual (Q)") },
                                    placeholder = { Text("Valor estimado al final de vida útil") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                            }
                            3 -> {
                                SectionTitle("Mantenimiento preventivo")
                                Text(
                                    "Si configuras una fecha próxima, aparecerá un aviso en la lista cuando se acerque (< 7 días).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = if (nextMaintenanceDate > 0L) formatDate(nextMaintenanceDate) else "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Próximo mantenimiento") },
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        Row {
                                            if (nextMaintenanceDate > 0L) TextButton(onClick = { nextMaintenanceDate = 0L }) { Text("Quitar") }
                                            TextButton(onClick = { showNextMaintenancePicker = true }) { Text("Elegir") }
                                        }
                                    }
                                )
                                OutlinedTextField(
                                    value = maintenanceIntervalDaysText,
                                    onValueChange = { maintenanceIntervalDaysText = it.filter { c -> c.isDigit() } },
                                    label = { Text("Intervalo entre mantenimientos (días)") },
                                    placeholder = { Text("Ej: 90") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                            }
                            4 -> {
                                SectionTitle("Notas")
                                OutlinedTextField(
                                    value = notes, onValueChange = { notes = it },
                                    label = { Text("Notas libres") },
                                    placeholder = { Text("Observaciones, historia, accesorios incluidos…") },
                                    modifier = Modifier.fillMaxWidth().height(180.dp),
                                    maxLines = 12
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val cost = purchaseCostText.toDoubleOrNull() ?: 0.0
                                if (name.isBlank() || category.isBlank() || branchId.isBlank() || purchaseDate <= 0L || cost <= 0.0) {
                                    return@Button
                                }
                                val asset = (existing ?: FixedAsset()).copy(
                                    name = name.trim(),
                                    assetCode = assetCode.trim(),
                                    description = description.trim(),
                                    category = category,
                                    branchId = branchId,
                                    branchName = branchName,
                                    location = location.trim(),
                                    responsible = responsible.trim().takeIf { it.isNotEmpty() },
                                    purchaseDate = purchaseDate,
                                    purchaseCost = cost,
                                    supplierName = supplierName.trim().takeIf { it.isNotEmpty() },
                                    invoiceNumber = invoiceNumber.trim().takeIf { it.isNotEmpty() },
                                    brand = brand.trim().takeIf { it.isNotEmpty() },
                                    model = model.trim().takeIf { it.isNotEmpty() },
                                    serialNumber = serialNumber.trim().takeIf { it.isNotEmpty() },
                                    warrantyUntil = warrantyUntil.takeIf { it > 0L },
                                    status = status,
                                    usefulLifeMonths = usefulLifeMonthsText.toIntOrNull()?.takeIf { it > 0 },
                                    residualValue = residualValueText.toDoubleOrNull() ?: 0.0,
                                    nextMaintenanceDate = nextMaintenanceDate.takeIf { it > 0L },
                                    maintenanceIntervalDays = maintenanceIntervalDaysText.toIntOrNull()?.takeIf { it > 0 },
                                    notes = notes.trim().takeIf { it.isNotEmpty() },
                                    active = true
                                )
                                onSave(asset, imageBytes)
                            },
                            enabled = !isSaving && name.isNotBlank() && category.isNotBlank() &&
                                branchId.isNotEmpty() && purchaseDate > 0L &&
                                (purchaseCostText.toDoubleOrNull() ?: 0.0) > 0.0,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            if (isSaving) CircularProgressIndicator(
                                color = Color.White, modifier = Modifier.height(20.dp)
                            )
                            else Text(if (isNew) "Crear activo" else "Guardar cambios",
                                color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
        }
    }
    LoadingOverlay(visible = isSaving)
    }

    if (showPurchasePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = purchaseDate.takeIf { it > 0L })
        DatePickerDialog(
            onDismissRequest = { showPurchasePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    purchaseDate = dpState.selectedDateMillis ?: purchaseDate
                    showPurchasePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showPurchasePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = dpState) }
    }
    if (showWarrantyPicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = warrantyUntil.takeIf { it > 0L })
        DatePickerDialog(
            onDismissRequest = { showWarrantyPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    warrantyUntil = dpState.selectedDateMillis ?: warrantyUntil
                    showWarrantyPicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showWarrantyPicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = dpState) }
    }
    if (showNextMaintenancePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = nextMaintenanceDate.takeIf { it > 0L })
        DatePickerDialog(
            onDismissRequest = { showNextMaintenancePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    nextMaintenanceDate = dpState.selectedDateMillis ?: nextMaintenanceDate
                    showNextMaintenancePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showNextMaintenancePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = dpState) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        color = PrimaryBlue,
        style = MaterialTheme.typography.titleSmall
    )
}
