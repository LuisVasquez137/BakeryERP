package com.bakery.erp.presentation.customerOrders

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.ExternalCustomer
import com.bakery.erp.model.ExternalCustomerType
import com.bakery.erp.model.Permission
import com.bakery.erp.model.PriceList
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.ProductionShift
import com.bakery.erp.model.ResellerDeliveryMode
import com.bakery.erp.model.ResellerRegistrationStatus
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors

private val MutedText = Color(0xFF6E6E6E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResellerApprovalScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPriceListAdmin: () -> Unit,
    onNavigateToForm: (customerId: String?) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ExternalCustomerViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    // La lista de precios ES el precio al que se le vende: darla de alta, cambiarla o aprobar a
    // alguien (que también la asigna) es de quien maneja precios, no de la cajera.
    val canSetPrices = authState.user?.hasPermission(Permission.MANAGE_PRICE_LISTS) == true
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) {
            viewModel.loadCustomers(companyId)
            viewModel.loadApprovalContext(companyId)
        }
    }
    LaunchedEffect(state.isApproved) {
        if (state.isApproved) {
            snackbarHostState.showSnackbar("Revendedor aprobado. Ya puede entrar a la app.")
            viewModel.clearApproved()
        }
    }
    LaunchedEffect(state.isRejected) {
        if (state.isRejected) {
            snackbarHostState.showSnackbar("Revendedor rechazado.")
            viewModel.clearRejected()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        "Pendientes" to ResellerRegistrationStatus.PENDING_APPROVAL,
        "Activos" to ResellerRegistrationStatus.ACTIVE,
        "Rechazados" to ResellerRegistrationStatus.REJECTED
    )
    // Solo revendedores: los clientes recurrentes se manejan desde Pedidos de clientes.
    val resellers = state.customers.filter { it.type == ExternalCustomerType.RESELLER }
    val filtered = resellers.filter { it.registrationStatus == tabs[tabIndex].second }

    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            BrandTopAppBar(
                title = { Text("Revendedores", fontWeight = FontWeight.SemiBold) },
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
        floatingActionButton = {
            // Para los que no tienen la app: el encargado los da de alta aquí, ya activos.
            if (canSetPrices) ExtendedFloatingActionButton(
                onClick = { onNavigateToForm(null) },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Agregar revendedor") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = tabIndex,
                    containerColor = WarmIvory,
                    contentColor = PrimaryBlue
                ) {
                    tabs.forEachIndexed { idx, (label, status) ->
                        val count = resellers.count { it.registrationStatus == status }
                        Tab(
                            selected = tabIndex == idx,
                            onClick = { tabIndex = idx },
                            text = {
                                Text(
                                    if (count > 0) "$label ($count)" else label,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        )
                    }
                }
                when {
                    state.isLoading && resellers.isEmpty() -> Box(
                        Modifier.fillMaxSize(), Alignment.Center
                    ) { CircularProgressIndicator(color = PrimaryBlue) }
                    filtered.isEmpty() -> EmptyState(tabs[tabIndex].second)
                    else -> LazyColumn(
                        // Abajo deja lugar para el botón flotante.
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filtered, key = { it.id }) { reseller ->
                            ResellerCard(
                                reseller = reseller,
                                state = state,
                                isPending = reseller.registrationStatus == ResellerRegistrationStatus.PENDING_APPROVAL,
                                canManage = canSetPrices,
                                onCreatePriceList = onNavigateToPriceListAdmin,
                                onEdit = { onNavigateToForm(reseller.id) },
                                onApprove = { viewModel.approveReseller(companyId, it) },
                                onReject = { viewModel.rejectReseller(companyId, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(status: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Inbox,
            contentDescription = null,
            tint = PrimaryBlue.copy(alpha = 0.4f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.size(12.dp))
        Text(
            when (status) {
                ResellerRegistrationStatus.PENDING_APPROVAL ->
                    "Sin revendedores pendientes"
                ResellerRegistrationStatus.ACTIVE ->
                    "Aún no hay revendedores activos"
                else -> "Sin revendedores rechazados"
            },
            style = MaterialTheme.typography.titleMedium,
            color = PrimaryBlue,
            fontWeight = FontWeight.SemiBold
        )
        if (status != ResellerRegistrationStatus.REJECTED) {
            Spacer(Modifier.size(6.dp))
            Text(
                if (status == ResellerRegistrationStatus.PENDING_APPROVAL)
                    "Cuando alguien se registre con tu código de invitación marcándose como revendedor, aparecerá aquí."
                else "Si el revendedor no tiene la app, agregalo con el botón de abajo.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResellerCard(
    reseller: ExternalCustomer,
    state: ExternalCustomerUiState,
    isPending: Boolean,
    canManage: Boolean,
    onCreatePriceList: () -> Unit,
    onEdit: () -> Unit,
    onApprove: (ExternalCustomer) -> Unit,
    onReject: (ExternalCustomer) -> Unit
) {
    var expanded by rememberSaveable(reseller.id) { mutableStateOf(false) }
    var priceListId by rememberSaveable(reseller.id) {
        mutableStateOf(reseller.priceListId)
    }
    var showRejectDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            BurntOrange.copy(alpha = 0.15f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Storefront, null, tint = BrandColors.AccentDeep)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        reseller.name.ifEmpty { "Sin nombre" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (reseller.businessName.isNotEmpty()) {
                        Text(
                            reseller.businessName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText
                        )
                    }
                    Spacer(Modifier.size(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (reseller.phone.isNotEmpty()) {
                            Icon(
                                Icons.Default.Phone, null,
                                tint = MutedText,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                reseller.phone,
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        ModalityBadge(reseller.deliveryMode)
                        if (!reseller.active && !isPending) {
                            Spacer(Modifier.width(6.dp))
                            com.bakery.erp.ui.theme.BrandChip("Inactivo", com.bakery.erp.ui.theme.ChipTone.NEUTRAL)
                        }
                    }
                }
                if (canManage) IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = PrimaryBlue)
                }
            }

            if (expanded) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    // Sección: lo que el revendedor dejó dicho
                    Text(
                        if (reseller.linkedUserId.isEmpty()) "Cómo compra" else "Solicitud del revendedor",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.size(4.dp))
                    if (reseller.deliveryMode == ResellerDeliveryMode.PICKUP) {
                        InfoRow(
                            "Quiere retirar en",
                            reseller.preferredBranchName.ifEmpty { "—" }
                        )
                        InfoRow(
                            "Hora habitual",
                            if (reseller.preferredPickupTime.isEmpty()) "—"
                            else reseller.preferredPickupTime,
                            icon = Icons.Default.AccessTime
                        )
                    } else {
                        InfoRow(
                            "Dirección",
                            reseller.address.ifEmpty { "—" },
                            icon = Icons.Default.LocalShipping
                        )
                        if (reseller.deliveryAddressReference.isNotEmpty()) {
                            InfoRow("Referencia", reseller.deliveryAddressReference)
                        }
                    }
                    if (reseller.notes.isNotEmpty()) {
                        InfoRow("Notas", reseller.notes)
                    }

                    Spacer(Modifier.size(12.dp))

                    if (isPending && !canManage) {
                        Text(
                            "Lo aprueba quien maneja las listas de precios.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText
                        )
                        Spacer(Modifier.size(12.dp))
                    } else if (isPending) {
                        Text(
                            "Asígnale una lista de precios",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.size(6.dp))

                        PriceListDropdown(
                            lists = state.priceLists,
                            currentId = priceListId,
                            onSelect = { priceListId = it },
                            onCreateNew = onCreatePriceList
                        )

                        Spacer(Modifier.size(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showRejectDialog = true },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isLoading,
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Rechazar", color = Color.Red) }
                            Button(
                                onClick = {
                                    val list = state.priceLists.firstOrNull { it.id == priceListId }
                                    onApprove(
                                        reseller.copy(
                                            priceListId = priceListId,
                                            priceListName = list?.name ?: ""
                                        )
                                    )
                                },
                                enabled = !state.isLoading && priceListId.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Aprobar")
                            }
                        }
                        Spacer(Modifier.size(12.dp))
                    } else {
                        // Visualización para activos/rechazados
                        Text(
                            "Asignación actual",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.size(4.dp))
                        InfoRow("Lista de precios", reseller.priceListName.ifEmpty { "—" })
                        Spacer(Modifier.size(12.dp))
                    }
                }
            }
        }
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("¿Rechazar revendedor?") },
            text = {
                Text(
                    "${reseller.name} no podrá entrar a la app. Puede volver a registrarse con un código nuevo si cambias de opinión."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRejectDialog = false
                    onReject(reseller)
                }) { Text("Sí, rechazar", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ModalityBadge(mode: String) {
    val (label, color) = when (mode) {
        ResellerDeliveryMode.DELIVERY -> "A domicilio" to Color(0xFF6A1B9A)
        else -> "Retira" to PrimaryBlue
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MutedText, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
            modifier = Modifier.width(120.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = PrimaryBlue, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchDropdown(
    label: String,
    branches: List<com.bakery.erp.model.Branch>,
    currentId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = branches.firstOrNull { it.id == currentId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = current?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (branches.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Sin plantas productoras configuradas") },
                    onClick = {}, enabled = false
                )
            }
            branches.forEach { b ->
                DropdownMenuItem(
                    text = { Text(b.name) },
                    onClick = { onSelect(b.id); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftDropdown(
    shifts: List<ProductionShift>,
    currentId: String,
    onSelect: (String) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val current = shifts.firstOrNull { it.id == currentId }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = current?.name ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Turno asignado *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (shifts.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Esta planta no tiene turnos configurados") },
                    onClick = {}, enabled = false
                )
            }
            shifts.forEach { s ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(s.name)
                            Text(
                                "${s.startTime} – ${s.endTime}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText
                            )
                        }
                    },
                    onClick = { onSelect(s.id); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceListDropdown(
    lists: List<PriceList>,
    currentId: String,
    onSelect: (String) -> Unit,
    onCreateNew: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val current = lists.firstOrNull { it.id == currentId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = current?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Lista de precios *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (lists.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Sin listas de precios configuradas") },
                    onClick = {}, enabled = false
                )
            }
            lists.forEach { l ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(l.name)
                            Text(
                                "${l.type}${if (l.isDefault) " · default" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText
                            )
                        }
                    },
                    onClick = { onSelect(l.id); expanded = false }
                )
            }
            androidx.compose.material3.HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        "+ Crear nueva lista de precios",
                        color = BrandColors.AccentDeep,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                onClick = { expanded = false; onCreateNew() }
            )
        }
    }
}
