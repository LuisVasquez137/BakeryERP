package com.bakery.erp.presentation.pos.admin

import com.bakery.erp.ui.BrandTopAppBar

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
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.PACKAGING_NAME_MAX_CHARS
import com.bakery.erp.model.PackagingItem
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney

/**
 * Empaques que se cobran en caja. Lo que se da de alta acá aparece como botón "+ Bolsa" junto
 * al total del POS en todas las sucursales, con este mismo precio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackagingAdminScreen(
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: PackagingAdminViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    // null = cerrado; item con id vacío = alta.
    var editing by remember { mutableStateOf<PackagingItem?>(null) }
    var openedAt by remember { mutableStateOf(0L) }

    LaunchedEffect(companyId) { if (companyId.isNotEmpty()) viewModel.load(companyId) }
    LaunchedEffect(state.error) {
        // Con el diálogo abierto el error se muestra en el diálogo, no detrás.
        if (editing == null) state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.savedAt) { if (state.savedAt > openedAt) editing = null }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Empaques que se cobran", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
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
            FloatingActionButton(
                onClick = { openedAt = state.savedAt; editing = PackagingItem() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Nuevo empaque", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            when {
                state.isLoading && state.items.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { PackagingIntro(empty = state.items.isEmpty()) }
                    items(state.items, key = { it.id }) { item ->
                        PackagingCard(item, onClick = { openedAt = state.savedAt; editing = item })
                    }
                    item { Spacer(Modifier.height(72.dp)) } // que el botón + no tape la última
                }
            }
        }
    }

    editing?.let { item ->
        PackagingItemDialog(
            initial = item,
            error = state.error,
            isSaving = state.isSaving,
            onDismiss = { editing = null; viewModel.clearError() },
            onSave = { viewModel.save(companyId, it) },
            onDelete = if (item.id.isNotEmpty()) ({ viewModel.delete(companyId, item.id) }) else null
        )
    }
    LoadingOverlay(visible = state.isSaving, message = "Guardando…")
}

@Composable
private fun PackagingIntro(empty: Boolean) {
    Surface(color = BrandColors.PrimaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "La bolsa, la caja de pastel… lo que se cobra aparte del pan.",
                fontWeight = FontWeight.SemiBold, color = BrandColors.Primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Cada empaque activo aparece como botón junto al total en todas las cajas, con el " +
                    "mismo precio en todas las sucursales. No lleva inventario: su compra se anota " +
                    "como gasto." + if (empty) " Si no cobran empaque, dejá esta lista vacía." else "",
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.TextSlate
            )
        }
    }
}

@Composable
private fun PackagingCard(item: PackagingItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(BrandColors.PrimaryContainer, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ShoppingBag, null, tint = BrandColors.Primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (item.active) "Se ofrece en caja" else "No aparece en caja",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.MutedSlate
                )
            }
            Text(
                "Q ${item.salePrice.fmtMoney(2)}",
                fontWeight = FontWeight.Bold,
                color = if (item.active) BrandColors.Primary else BrandColors.MutedSlate
            )
        }
    }
}

@Composable
private fun PackagingItemDialog(
    initial: PackagingItem,
    error: String?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (PackagingItem) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var priceText by rememberSaveable(initial.id) {
        mutableStateOf(if (initial.salePrice > 0) initial.salePrice.fmt(2) else "")
    }
    var active by rememberSaveable(initial.id) { mutableStateOf(initial.active) }
    var confirmDelete by remember { mutableStateOf(false) }
    val price = priceText.replace(',', '.').trim().toDoubleOrNull()

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (initial.id.isEmpty()) "Nuevo empaque" else "Editar empaque") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= PACKAGING_NAME_MAX_CHARS) name = it },
                    label = { Text("Nombre (ej. Bolsa)") },
                    singleLine = true,
                    supportingText = { Text("${name.length}/$PACKAGING_NAME_MAX_CHARS · así sale en el botón y el ticket") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Precio de venta") },
                    prefix = { Text("Q ") },
                    singleLine = true,
                    isError = priceText.isNotEmpty() && (price == null || price <= 0.0),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ofrecer en caja", fontWeight = FontWeight.Medium)
                        Text(
                            "Apagado deja de salir en el POS sin borrar las ventas que ya tiene.",
                            style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate
                        )
                    }
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (onDelete != null) {
                    if (!confirmDelete) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("Eliminar empaque", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Text(
                            "¿Eliminarlo? Las ventas ya cobradas conservan el nombre y el precio. " +
                                "Si solo querés que no salga en caja, apagá «Ofrecer en caja».",
                            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start
                        )
                        Row {
                            TextButton(onClick = { confirmDelete = false }) { Text("No") }
                            TextButton(onClick = onDelete, enabled = !isSaving) {
                                Text("Sí, eliminar", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(initial.copy(name = name.trim(), salePrice = price ?: 0.0, active = active)) },
                enabled = !isSaving && name.isNotBlank() && price != null && price > 0.0
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
        }
    )
}
