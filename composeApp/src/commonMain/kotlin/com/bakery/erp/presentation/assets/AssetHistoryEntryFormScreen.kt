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
import androidx.compose.foundation.text.KeyboardOptions
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
import com.bakery.erp.model.AssetEventType
import com.bakery.erp.model.AssetHistoryEntry
import com.bakery.erp.model.Branch
import com.bakery.erp.model.FixedAsset
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.branches.BranchViewModel
import com.bakery.erp.ui.AdaptiveContainer
import kotlin.time.Clock
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetHistoryEntryFormScreen(
    assetId: String,
    entryId: String?,
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

    LaunchedEffect(assetId, companyId) {
        if (companyId.isNotEmpty()) {
            if (state.selectedAsset == null || state.selectedAsset?.id != assetId) {
                viewModel.loadAsset(companyId, assetId)
            }
            branchViewModel.loadBranches(companyId)
        }
    }

    LaunchedEffect(state.isEntrySaved) {
        if (state.isEntrySaved) {
            viewModel.clearEntrySaved()
            onNavigateBack()
        }
    }

    val asset = state.selectedAsset
    val existing = remember(entryId, state.historyEntries) {
        if (entryId.isNullOrEmpty()) null
        else state.historyEntries.firstOrNull { it.id == entryId }
    }

    val accessibleBranches = remember(branchState.branchList, user) {
        if (user == null) emptyList()
        else branchState.branchList.filter { it.active && user.canAccessBranch(it.id) }
    }

    if (asset == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    EntryFormContent(
        asset = asset,
        existing = existing,
        branches = accessibleBranches,
        isSaving = state.isSaving,
        onNavigateBack = onNavigateBack,
        onSave = { entry -> viewModel.saveHistoryEntry(companyId, assetId, entry, user) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryFormContent(
    asset: FixedAsset,
    existing: AssetHistoryEntry?,
    branches: List<Branch>,
    isSaving: Boolean,
    onNavigateBack: () -> Unit,
    onSave: (AssetHistoryEntry) -> Unit
) {
    val isNew = existing == null
    var type by rememberSaveable {
        mutableStateOf(existing?.type ?: AssetEventType.MAINTENANCE)
    }
    var date by rememberSaveable {
        mutableStateOf(existing?.date ?: Clock.System.now().toEpochMilliseconds())
    }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var costText by rememberSaveable {
        mutableStateOf(existing?.cost?.takeIf { it > 0 }?.toString() ?: "")
    }
    var providerName by rememberSaveable { mutableStateOf(existing?.providerName ?: "") }
    var invoiceNumber by rememberSaveable { mutableStateOf(existing?.invoiceNumber ?: "") }
    var nextMaintenanceDate by rememberSaveable { mutableStateOf(existing?.nextMaintenanceDate ?: 0L) }
    var toBranchId by rememberSaveable { mutableStateOf(existing?.toBranchId ?: "") }
    var toBranchName by rememberSaveable { mutableStateOf(existing?.toBranchName ?: "") }

    var typeMenuExpanded by remember { mutableStateOf(false) }
    var branchMenuExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNextDatePicker by remember { mutableStateOf(false) }

    // Auto-sugerir próxima fecha cuando se selecciona MAINTENANCE y hay intervalo
    LaunchedEffect(type, asset.maintenanceIntervalDays) {
        if (type == AssetEventType.MAINTENANCE && nextMaintenanceDate == 0L) {
            val interval = asset.maintenanceIntervalDays
            if (interval != null && interval > 0) {
                nextMaintenanceDate = date + interval.toLong() * 24L * 60L * 60L * 1000L
            }
        }
    }

    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            BrandTopAppBar(
                title = { Text(if (isNew) "Nuevo evento" else "Editar evento", fontWeight = FontWeight.SemiBold) },
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
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AdaptiveContainer(maxContentWidth = 720.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(asset.name, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium, color = PrimaryBlue)
                    if (asset.assetCode.isNotEmpty()) Text(
                        asset.assetCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()

                    ExposedDropdownMenuBox(
                        expanded = typeMenuExpanded,
                        onExpandedChange = { typeMenuExpanded = !typeMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = type.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo de evento *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = typeMenuExpanded,
                            onDismissRequest = { typeMenuExpanded = false }
                        ) {
                            // PURCHASE se gestiona automáticamente al crear el activo
                            AssetEventType.entries
                                .filter { it != AssetEventType.PURCHASE || existing?.type == AssetEventType.PURCHASE }
                                .forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t.label) },
                                        onClick = { type = t; typeMenuExpanded = false }
                                    )
                                }
                        }
                    }

                    OutlinedTextField(
                        value = formatDate(date),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha del evento *") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { showDatePicker = true }) { Text("Elegir") }
                        }
                    )

                    OutlinedTextField(
                        value = description, onValueChange = { description = it },
                        label = { Text("Descripción *") },
                        placeholder = { Text("Ej: cambio de banda, ajuste de termostato…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2, maxLines = 4
                    )

                    if (type.hasCost) {
                        OutlinedTextField(
                            value = costText,
                            onValueChange = { costText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Costo (Q)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = providerName, onValueChange = { providerName = it },
                            label = { Text("¿Quién realizó el servicio?") },
                            placeholder = { Text("Nombre del técnico, taller…") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = invoiceNumber, onValueChange = { invoiceNumber = it },
                            label = { Text("Factura / comprobante") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                    }

                    if (type == AssetEventType.MAINTENANCE) {
                        HorizontalDivider()
                        Text(
                            "Si quieres, programa el próximo mantenimiento:",
                            style = MaterialTheme.typography.labelMedium,
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
                                    TextButton(onClick = { showNextDatePicker = true }) { Text("Elegir") }
                                }
                            }
                        )
                    }

                    if (type == AssetEventType.TRANSFER) {
                        HorizontalDivider()
                        Text(
                            "Sucursal destino — el activo se reasignará automáticamente al guardar.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ExposedDropdownMenuBox(
                            expanded = branchMenuExpanded,
                            onExpandedChange = { branchMenuExpanded = !branchMenuExpanded }
                        ) {
                            OutlinedTextField(
                                value = toBranchName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sucursal destino *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(branchMenuExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = branchMenuExpanded,
                                onDismissRequest = { branchMenuExpanded = false }
                            ) {
                                branches
                                    .filter { it.id != asset.branchId }
                                    .forEach { b ->
                                        DropdownMenuItem(
                                            text = { Text(b.name) },
                                            onClick = {
                                                toBranchId = b.id; toBranchName = b.name
                                                branchMenuExpanded = false
                                            }
                                        )
                                    }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val transferOk = type != AssetEventType.TRANSFER || toBranchId.isNotEmpty()
                            if (description.isBlank() || date <= 0L || !transferOk) return@Button
                            val cost = if (type.hasCost) (costText.toDoubleOrNull() ?: 0.0) else 0.0
                            val entry = (existing ?: AssetHistoryEntry()).copy(
                                assetId = asset.id,
                                type = type,
                                date = date,
                                description = description.trim(),
                                cost = cost,
                                providerName = providerName.trim().takeIf { it.isNotEmpty() },
                                invoiceNumber = invoiceNumber.trim().takeIf { it.isNotEmpty() },
                                nextMaintenanceDate = if (type == AssetEventType.MAINTENANCE)
                                    nextMaintenanceDate.takeIf { it > 0L } else null,
                                fromBranchId = if (type == AssetEventType.TRANSFER) asset.branchId else null,
                                fromBranchName = if (type == AssetEventType.TRANSFER) asset.branchName else null,
                                toBranchId = if (type == AssetEventType.TRANSFER) toBranchId else null,
                                toBranchName = if (type == AssetEventType.TRANSFER) toBranchName else null
                            )
                            onSave(entry)
                        },
                        enabled = !isSaving && description.isNotBlank() && date > 0L &&
                            (type != AssetEventType.TRANSFER || toBranchId.isNotEmpty()),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        if (isSaving) CircularProgressIndicator(
                            color = Color.White, modifier = Modifier.height(20.dp)
                        )
                        else Text(if (isNew) "Registrar evento" else "Guardar cambios",
                            color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    date = dpState.selectedDateMillis ?: date
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = dpState) }
    }
    if (showNextDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = nextMaintenanceDate.takeIf { it > 0L })
        DatePickerDialog(
            onDismissRequest = { showNextDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    nextMaintenanceDate = dpState.selectedDateMillis ?: nextMaintenanceDate
                    showNextDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showNextDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = dpState) }
    }
}
