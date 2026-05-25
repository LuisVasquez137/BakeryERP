package com.bakery.erp.presentation.branches

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.PriceList
import com.bakery.erp.model.PriceListType
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.pricing.PriceListViewModel
import com.bakery.erp.presentation.products.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchFormScreen(
    branchId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: BranchViewModel,
    productViewModel: ProductViewModel,
    priceListViewModel: PriceListViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val productState by productViewModel.uiState.collectAsState()
    val priceState by priceListViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    val existing = remember(branchId, state.branchList) {
        if (branchId != null) state.branchList.find { it.id == branchId }
            ?: state.selectedBranch?.takeIf { it.id == branchId }
        else null
    }

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var location by rememberSaveable { mutableStateOf(existing?.location ?: "") }
    var phone by rememberSaveable { mutableStateOf(existing?.phone ?: "") }
    var managerName by rememberSaveable { mutableStateOf(existing?.managerName ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var active by rememberSaveable { mutableStateOf(existing?.active ?: true) }

    // capabilities como Set mutable — no Parcelable, ok con remember
    var capabilities by remember {
        mutableStateOf(existing?.capabilities?.toSet() ?: emptySet<BranchCapability>())
    }
    var assignedProductIds by remember {
        mutableStateOf(existing?.assignedProductIds?.toSet() ?: emptySet<String>())
    }
    var productSearchQuery by rememberSaveable { mutableStateOf("") }

    var nameError by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf(false) }

    // Lista de precios por defecto para despachos hacia esta sucursal.
    var defaultPriceListId by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.defaultPriceListId ?: "")
    }
    var priceListExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) {
            productViewModel.loadProducts(companyId)
            priceListViewModel.loadLists(companyId)
        }
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    fun build() = Branch(
        id = existing?.id ?: "",
        name = name.trim().replaceFirstChar { it.uppercase() },
        location = location.trim(),
        phone = phone.trim(),
        managerName = managerName.trim().replaceFirstChar { it.uppercase() },
        notes = notes.trim().ifEmpty { null },
        capabilities = capabilities.toList(),
        assignedProductIds = assignedProductIds.toList(),
        defaultPriceListId = defaultPriceListId,
        active = active
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (branchId == null) "Nueva Sucursal" else "Editar Sucursal",
                        fontWeight = FontWeight.SemiBold
                    )
                },
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // ── Datos generales ───────────────────────────────────────────
                SectionHeader("Sucursal")

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Nombre de la sucursal *") },
                    placeholder = { Text("Ej: Sucursal Centro") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError,
                    supportingText = if (nameError) ({ Text("El nombre es requerido") }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it; locationError = false },
                    label = { Text("Dirección *") },
                    placeholder = { Text("Ej: 5a Avenida 12-34, Zona 1") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = locationError,
                    supportingText = if (locationError) ({ Text("La dirección es requerida") }) else null,
                    minLines = 2,
                    maxLines = 3
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono") },
                    placeholder = { Text("Ej: 2222-3333") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                OutlinedTextField(
                    value = managerName,
                    onValueChange = { managerName = it },
                    label = { Text("Encargado / Gerente") },
                    placeholder = { Text("Ej: María García") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Activa", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = active, onCheckedChange = { active = it })
                }

                SectionDivider()

                // ── Capacidades ───────────────────────────────────────────────
                SectionHeader("Capacidades")
                Text(
                    text = "Selecciona todo lo que aplique a esta sucursal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                CapabilityCheckRow(
                    label = "Punto de producción",
                    description = "Produce pan, pasteles u otros productos",
                    checked = BranchCapability.CAN_PRODUCE in capabilities,
                    onCheckedChange = { checked ->
                        capabilities = if (checked) capabilities + BranchCapability.CAN_PRODUCE
                        else capabilities - BranchCapability.CAN_PRODUCE
                    }
                )
                CapabilityCheckRow(
                    label = "Punto de venta",
                    description = "Vende productos directamente al cliente",
                    checked = BranchCapability.CAN_SELL in capabilities,
                    onCheckedChange = { checked ->
                        capabilities = if (checked) capabilities + BranchCapability.CAN_SELL
                        else capabilities - BranchCapability.CAN_SELL
                    }
                )
                CapabilityCheckRow(
                    label = "Almacén / Bodega",
                    description = "Almacena insumos o producto terminado",
                    checked = BranchCapability.IS_WAREHOUSE in capabilities,
                    onCheckedChange = { checked ->
                        capabilities = if (checked) capabilities + BranchCapability.IS_WAREHOUSE
                        else capabilities - BranchCapability.IS_WAREHOUSE
                    }
                )

                // ── Productos asignados (solo para plantas productoras) ───────
                if (BranchCapability.CAN_PRODUCE in capabilities) {
                    SectionDivider()
                    SectionHeader("Productos que produce esta planta")
                    Text(
                        text = "Sin selección: aparecen todos los productos en los pedidos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val filteredProducts = remember(productState.productList, productSearchQuery) {
                        productState.productList.filter {
                            it.active && (productSearchQuery.isEmpty() || it.name.contains(productSearchQuery, ignoreCase = true))
                        }.sortedBy { it.name }
                    }

                    if (productState.productList.isNotEmpty()) {
                        OutlinedTextField(
                            value = productSearchQuery,
                            onValueChange = { productSearchQuery = it },
                            label = { Text("Buscar producto") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        filteredProducts.forEach { product ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        assignedProductIds = if (product.id in assignedProductIds)
                                            assignedProductIds - product.id
                                        else
                                            assignedProductIds + product.id
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = product.id in assignedProductIds,
                                    onCheckedChange = {
                                        assignedProductIds = if (it) assignedProductIds + product.id
                                        else assignedProductIds - product.id
                                    }
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(product.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (assignedProductIds.isNotEmpty()) {
                            Text(
                                "${assignedProductIds.size} producto(s) asignado(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (productState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            "No hay productos activos. Crea productos primero.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── Lista de precios para despacho ────────────────────────────
                if (BranchCapability.CAN_SELL in capabilities) {
                    SectionDivider()
                    SectionHeader("Lista de precios para despacho")
                    Text(
                        text = "Precio al que la planta productora despacha producto a esta sucursal. Vacío = usar la lista mayorista default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val candidateLists = remember(priceState.lists) {
                        priceState.lists.filter {
                            it.active && (it.type == PriceListType.WHOLESALE || it.type == PriceListType.CUSTOM)
                        }
                    }
                    val selectedList: PriceList? = remember(candidateLists, defaultPriceListId) {
                        candidateLists.firstOrNull { it.id == defaultPriceListId }
                    }
                    ExposedDropdownMenuBox(
                        expanded = priceListExpanded,
                        onExpandedChange = { priceListExpanded = !priceListExpanded }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = selectedList?.let {
                                "${it.name} · ${PriceListType.label(it.type)}${if (it.isDefault) " (default)" else ""}"
                            } ?: "Usar lista mayorista default",
                            onValueChange = {},
                            label = { Text("Lista asignada") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priceListExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = priceListExpanded,
                            onDismissRequest = { priceListExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Usar lista mayorista default") },
                                onClick = { defaultPriceListId = ""; priceListExpanded = false }
                            )
                            candidateLists.forEach { list ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${list.name} · ${PriceListType.label(list.type)}" +
                                                if (list.isDefault) " (default)" else ""
                                        )
                                    },
                                    onClick = { defaultPriceListId = list.id; priceListExpanded = false }
                                )
                            }
                        }
                    }
                }

                SectionDivider()

                // ── Notas ─────────────────────────────────────────────────────
                SectionHeader("Notas")

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(Modifier.padding(8.dp))

                Button(
                    onClick = {
                        var hasError = false
                        if (name.isBlank()) { nameError = true; hasError = true }
                        if (location.isBlank()) { locationError = true; hasError = true }
                        if (hasError) return@Button
                        viewModel.saveBranch(companyId, build())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Guardar Sucursal", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun CapabilityCheckRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
