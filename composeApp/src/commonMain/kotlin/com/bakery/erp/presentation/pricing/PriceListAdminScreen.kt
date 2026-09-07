package com.bakery.erp.presentation.pricing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Permission
import com.bakery.erp.model.PriceList
import com.bakery.erp.model.PriceListType
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceListAdminScreen(
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: PriceListViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val canManage = authState.user?.hasPermission(Permission.MANAGE_PRICE_LISTS) == true
    val snackbarHostState = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf<PriceList?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PriceList?>(null) }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) viewModel.loadLists(companyId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            showForm = false; editing = null
            viewModel.clearSaved()
        }
    }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) {
            pendingDelete = null
            viewModel.clearDeleted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Listas de precios", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        floatingActionButton = {
            if (canManage) {
                FloatingActionButton(
                    onClick = { editing = null; showForm = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Nueva lista", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.lists.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.lists.isEmpty() -> EmptyState(canManage) { editing = null; showForm = true }
                else -> AdaptiveContainer {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                "Define un precio por canal. El precio del cliente final es la lista RETAIL default; el de las sucursales es la WHOLESALE default.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(state.lists, key = { it.id }) { list ->
                            PriceListCard(
                                list = list,
                                canEdit = canManage,
                                onEdit = { editing = list; showForm = true },
                                onDelete = { pendingDelete = list }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        PriceListFormDialog(
            initial = editing,
            existing = state.lists,
            onDismiss = { showForm = false; editing = null },
            onSave = { viewModel.saveList(companyId, it) }
        )
    }
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar lista") },
            text = { Text("¿Eliminar la lista \"${target.name}\"? Los precios asociados quedarán sin lista y no podrán resolverse al despachar.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteList(companyId, target.id) }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun EmptyState(canManage: Boolean, onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Sell, contentDescription = null, modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        Text("Aún no hay listas de precios", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(8.dp))
        Text(
            "Las listas seed deberían crearse automáticamente.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (canManage) {
            Spacer(Modifier.size(12.dp))
            TextButton(onClick = onCreate) { Text("Crear lista manualmente") }
        }
    }
}

@Composable
private fun PriceListCard(
    list: PriceList,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(list.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (list.isDefault) {
                            Spacer(Modifier.size(8.dp))
                            Icon(Icons.Filled.Star, contentDescription = "Default",
                                tint = BrandColors.AccentDeep, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.size(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = {},
                            label = { Text(PriceListType.label(list.type), style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = when (list.type) {
                                    PriceListType.RETAIL    -> Color(0xFFE3F2FD)
                                    PriceListType.WHOLESALE -> Color(0xFFE8F5E9)
                                    else                    -> Color(0xFFFFF3E0)
                                }
                            )
                        )
                        list.priceFactor?.let { f ->
                            Spacer(Modifier.size(6.dp))
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text("${(f * 100).toInt()}% del retail", style = MaterialTheme.typography.labelSmall)
                                },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFFFE0B2))
                            )
                        }
                        if (!list.active) {
                            Spacer(Modifier.size(6.dp))
                            AssistChip(onClick = {}, label = { Text("Inactiva", style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    if (!list.notes.isNullOrBlank()) {
                        Spacer(Modifier.size(8.dp))
                        Text(list.notes, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (canEdit) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    if (!list.isDefault) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceListFormDialog(
    initial: PriceList?,
    existing: List<PriceList>,
    onDismiss: () -> Unit,
    onSave: (PriceList) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(initial?.type ?: PriceListType.WHOLESALE) }
    var isDefault by rememberSaveable { mutableStateOf(initial?.isDefault ?: false) }
    var active by rememberSaveable { mutableStateOf(initial?.active ?: true) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes ?: "") }
    var typeExpanded by remember { mutableStateOf(false) }
    // priceFactor en porcentaje (ej. 80 = 80% del precio retail). Vacío = sin auto.
    var factorPctInput by rememberSaveable {
        mutableStateOf(initial?.priceFactor?.let { (it * 100).toString() } ?: "")
    }

    val existingDefaultOtherType = existing
        .filter { it.id != initial?.id && it.type == type && it.isDefault }
        .firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva lista de precios" else "Editar lista") },
        text = {
            Column(modifier = Modifier.heightIn(max = 500.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(12.dp))
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = PriceListType.label(type),
                        onValueChange = {},
                        label = { Text("Tipo de canal") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        PriceListType.all.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(PriceListType.label(option)) },
                                onClick = { type = option; typeExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                    Text("Lista default para este canal", style = MaterialTheme.typography.bodyMedium)
                }
                if (isDefault && existingDefaultOtherType != null) {
                    Text(
                        "Reemplazará a \"${existingDefaultOtherType.name}\" como default ${PriceListType.label(type)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.AccentDeep,
                        modifier = Modifier.padding(start = 40.dp)
                    )
                }
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = active, onCheckedChange = { active = it })
                    Text("Activa", style = MaterialTheme.typography.bodyMedium)
                }

                // Factor de precio (cascada): solo aplica a WHOLESALE/CUSTOM.
                if (type == PriceListType.WHOLESALE || type == PriceListType.CUSTOM) {
                    Spacer(Modifier.size(12.dp))
                    OutlinedTextField(
                        value = factorPctInput,
                        onValueChange = { factorPctInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("% sobre el precio al cliente final") },
                        placeholder = { Text("Ej: 80 = 80% del retail") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            val previewPct = factorPctInput.toDoubleOrNull()
                            Text(
                                when {
                                    previewPct == null || previewPct <= 0 ->
                                        "Vacío = sin precio automático. Si no defines override por producto, el despacho marcará 'precio no definido'."
                                    previewPct < 100 ->
                                        "Cada producto se despachará a ${previewPct.toInt()}% de su precio al cliente final, salvo override."
                                    previewPct == 100.0 ->
                                        "Misma tarifa que el retail (¿estás seguro?)."
                                    else ->
                                        "${previewPct.toInt()}% es MAYOR al retail — recargo, no descuento."
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }

                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas (opcional)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val factor: Double? = if (type == PriceListType.RETAIL) null
                    else factorPctInput.toDoubleOrNull()
                        ?.takeIf { it > 0 }
                        ?.let { it / 100.0 }
                    onSave(
                        (initial ?: PriceList()).copy(
                            name = name.trim(),
                            type = type,
                            isDefault = isDefault,
                            active = active,
                            priceFactor = factor,
                            notes = notes.trim().ifEmpty { null }
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
