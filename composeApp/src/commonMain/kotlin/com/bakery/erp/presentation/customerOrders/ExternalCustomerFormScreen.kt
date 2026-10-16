package com.bakery.erp.presentation.customerOrders

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.bakery.erp.model.ExternalCustomer
import com.bakery.erp.model.Branch
import com.bakery.erp.model.ExternalCustomerType
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.ResellerDeliveryMode
import com.bakery.erp.model.PriceList
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalCustomerFormScreen(
    customerId: String?,
    resellerOnly: Boolean = false,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ExternalCustomerViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }
    val needsLoad = !customerId.isNullOrEmpty()
    val canSetPrices = authState.user?.hasPermission(com.bakery.erp.model.Permission.MANAGE_PRICE_LISTS) == true

    LaunchedEffect(customerId, companyId) {
        if (companyId.isNotEmpty()) {
            viewModel.loadPriceLists(companyId)
            viewModel.loadSellingBranches(companyId)
            if (needsLoad) viewModel.loadCustomerById(companyId, customerId!!)
            else viewModel.selectCustomer(null)
        }
    }
    LaunchedEffect(state.isSaved, state.isDeleted) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
        if (state.isDeleted) { viewModel.clearDeleted(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val title = when {
        resellerOnly -> if (needsLoad) "Editar revendedor" else "Nuevo revendedor"
        else -> if (needsLoad) "Editar cliente" else "Nuevo cliente"
    }

    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            BrandTopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
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
        AdaptiveContainer(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (needsLoad && state.isDetailLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else {
                FormContent(
                    existing = state.selectedCustomer,
                    priceLists = state.priceLists,
                    branches = state.sellingBranches,
                    resellerOnly = resellerOnly,
                    canSetPrices = canSetPrices,
                    isSaving = state.isLoading,
                    onSave = { viewModel.saveCustomer(companyId, it) },
                    onDelete = { id -> viewModel.deleteCustomer(companyId, id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormContent(
    existing: ExternalCustomer?,
    priceLists: List<PriceList>,
    branches: List<Branch>,
    resellerOnly: Boolean,
    /** Sin esto no se elige la lista de un revendedor (su precio) ni se le da de alta o se borra. */
    canSetPrices: Boolean,
    isSaving: Boolean,
    onSave: (ExternalCustomer) -> Unit,
    onDelete: (id: String) -> Unit
) {
    var type by rememberSaveable {
        mutableStateOf(if (resellerOnly) ExternalCustomerType.RESELLER else existing?.type ?: ExternalCustomerType.RESELLER)
    }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var businessName by rememberSaveable { mutableStateOf(existing?.businessName ?: "") }
    var phone by rememberSaveable { mutableStateOf(existing?.phone ?: "") }
    var phoneAlt by rememberSaveable { mutableStateOf(existing?.phoneAlt ?: "") }
    var email by rememberSaveable { mutableStateOf(existing?.email ?: "") }
    var address by rememberSaveable { mutableStateOf(existing?.address ?: "") }
    var taxId by rememberSaveable { mutableStateOf(existing?.taxId ?: "") }
    var priceListId by rememberSaveable { mutableStateOf(existing?.priceListId ?: "") }
    var active by rememberSaveable { mutableStateOf(existing?.active ?: true) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    // Cómo compra el revendedor: lo mismo que pide la app al registrarse (ResellerSetupScreen).
    var deliveryMode by rememberSaveable { mutableStateOf(existing?.deliveryMode ?: ResellerDeliveryMode.PICKUP) }
    var preferredBranchId by rememberSaveable { mutableStateOf(existing?.preferredBranchId ?: "") }
    var pickupTime by rememberSaveable { mutableStateOf(existing?.preferredPickupTime ?: "") }
    var addressReference by rememberSaveable { mutableStateOf(existing?.deliveryAddressReference ?: "") }
    val isReseller = type == ExternalCustomerType.RESELLER
    val priceLocked = isReseller && !canSetPrices
    var showDeleteDialog by remember { mutableStateOf(false) }

    val scroll = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Desde Revendedores todo es revendedor: un cliente recurrente guardado ahí desaparecería
        // de esa lista y parecería que no se guardó.
        if (!resellerOnly) Text("Tipo", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
        if (!resellerOnly) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ExternalCustomerType.RESELLER,
                ExternalCustomerType.RETAIL_RECURRING
            ).forEach { opt ->
                FilterChip(
                    selected = type == opt,
                    onClick = { type = opt },
                    label = { Text(ExternalCustomerType.label(opt)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryBlue,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Nombre del contacto *") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = businessName, onValueChange = { businessName = it },
            label = { Text("Nombre del negocio") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = phone.removePrefix("+502"),
                onValueChange = { phone = it.filter { c -> c.isDigit() }.take(8) },
                label = { Text("Teléfono (+502)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = phoneAlt.removePrefix("+502"),
                onValueChange = { phoneAlt = it.filter { c -> c.isDigit() }.take(8) },
                label = { Text("Teléfono adicional (+502)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true, modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = address, onValueChange = { address = it },
            label = { Text("Dirección") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = taxId,
            onValueChange = { taxId = it.filter { c -> c.isDigit() || c == '-' } },
            label = { Text("NIT") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        // Lista de precios asignada
        var listExpanded by remember { mutableStateOf(false) }
        val selectedList = priceLists.firstOrNull { it.id == priceListId }
        ExposedDropdownMenuBox(
            expanded = listExpanded && !priceLocked,
            onExpandedChange = { if (!priceLocked) listExpanded = !listExpanded }
        ) {
            OutlinedTextField(
                value = selectedList?.name ?: if (isReseller) "" else "Default por tipo",
                onValueChange = {},
                readOnly = true,
                label = { Text(if (isReseller) "Lista de precios *" else "Lista de precios") },
                enabled = !priceLocked,
                supportingText = when {
                    priceLocked -> { { Text("Solo quien maneja las listas de precios la puede elegir o cambiar.") } }
                    isReseller -> { { Text("Con estos precios se le cobra en caja. Sin lista no se le puede vender como revendedor.") } }
                    else -> null
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = listExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = listExpanded,
                onDismissRequest = { listExpanded = false }
            ) {
                if (!isReseller) {
                    DropdownMenuItem(
                        text = { Text("Default por tipo (sin asignar)") },
                        onClick = { priceListId = ""; listExpanded = false }
                    )
                }
                priceLists.forEach { list ->
                    DropdownMenuItem(
                        text = { Text(list.name) },
                        onClick = { priceListId = list.id; listExpanded = false }
                    )
                }
            }
        }

        if (isReseller) {
            Text("¿Cómo compra?", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ResellerDeliveryMode.PICKUP, ResellerDeliveryMode.DELIVERY).forEach { opt ->
                    FilterChip(
                        selected = deliveryMode == opt,
                        onClick = { deliveryMode = opt },
                        label = { Text(ResellerDeliveryMode.label(opt)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            if (deliveryMode == ResellerDeliveryMode.PICKUP) {
                var branchExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = branchExpanded,
                    onExpandedChange = { branchExpanded = !branchExpanded }
                ) {
                    OutlinedTextField(
                        value = branches.firstOrNull { it.id == preferredBranchId }?.name
                            ?: if (preferredBranchId.isEmpty()) "Cualquiera" else existing?.preferredBranchName.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sucursal donde compra") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = branchExpanded,
                        onDismissRequest = { branchExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Cualquiera") },
                            onClick = { preferredBranchId = ""; branchExpanded = false }
                        )
                        branches.forEach { b ->
                            DropdownMenuItem(
                                text = { Text(b.name) },
                                onClick = { preferredBranchId = b.id; branchExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = pickupTime,
                    onValueChange = { v -> pickupTime = v.filter { it.isDigit() || it == ':' }.take(5) },
                    label = { Text("Hora en que pasa (ej. 06:30)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = addressReference, onValueChange = { addressReference = it },
                    label = { Text("Referencia para llegar") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Activo", modifier = Modifier.weight(1f), color = PrimaryBlue)
            Switch(
                checked = active, onCheckedChange = { active = it },
                colors = SwitchDefaults.colors(checkedTrackColor = BrandColors.Primary)
            )
        }

        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            label = { Text("Notas") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.size(8.dp))
        Button(
            onClick = {
                val final = (existing ?: ExternalCustomer()).copy(
                    type = type,
                    name = name.trim(),
                    businessName = businessName.trim(),
                    phone = phone.trim().let { if (it.isEmpty()) "" else if (it.startsWith("+")) it else "+502$it" },
                    phoneAlt = phoneAlt.trim().let { if (it.isEmpty()) "" else if (it.startsWith("+")) it else "+502$it" },
                    email = email.trim(),
                    address = address.trim(),
                    taxId = taxId.trim(),
                    priceListId = priceListId,
                    priceListName = priceLists.firstOrNull { it.id == priceListId }?.name ?: "",
                    active = active,
                    notes = notes.trim(),
                    deliveryMode = if (isReseller) deliveryMode else existing?.deliveryMode ?: ResellerDeliveryMode.PICKUP,
                    preferredBranchId = when {
                        !isReseller -> existing?.preferredBranchId.orEmpty()
                        deliveryMode == ResellerDeliveryMode.PICKUP -> preferredBranchId
                        else -> ""
                    },
                    preferredBranchName = when {
                        !isReseller -> existing?.preferredBranchName.orEmpty()
                        deliveryMode == ResellerDeliveryMode.PICKUP ->
                            branches.firstOrNull { it.id == preferredBranchId }?.name
                                ?: existing?.preferredBranchName?.takeIf { existing.preferredBranchId == preferredBranchId }.orEmpty()
                        else -> ""
                    },
                    preferredPickupTime = if (isReseller) pickupTime.trim() else existing?.preferredPickupTime.orEmpty(),
                    deliveryAddressReference = if (isReseller) addressReference.trim()
                                               else existing?.deliveryAddressReference.orEmpty()
                )
                onSave(final)
            },
            enabled = !isSaving && name.isNotBlank() && (!isReseller || priceListId.isNotEmpty()),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            else Text(if (existing == null) (if (resellerOnly) "Crear revendedor" else "Crear cliente") else "Guardar cambios")
        }

        if (existing != null && existing.id.isNotEmpty() &&
            (existing.type != ExternalCustomerType.RESELLER || canSetPrices)) {
            TextButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                Spacer(Modifier.size(6.dp))
                Text("Eliminar cliente", color = Color.Red)
            }
        }
    }

    if (showDeleteDialog && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Eliminar cliente?") },
            text = { Text("Sus pedidos históricos seguirán visibles pero ya no podrá usarse en nuevos.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(existing.id)
                }) { Text("Eliminar", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
