package com.bakery.erp.presentation.production

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Branch
import com.bakery.erp.model.ProductionShift
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.TimePickerDialog
import com.bakery.erp.ui.formatHHmm
import com.bakery.erp.ui.isOvernightShift
import com.bakery.erp.ui.parseHHmm
import com.bakery.erp.ui.theme.BrandColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionShiftFormScreen(
    branchId: String?,
    shiftId: String?,
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
    val isNew = shiftId == null

    // Cargar sucursales si aún no están en estado
    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && state.producingBranches.isEmpty()) {
            viewModel.loadOrderFormData(companyId)
        }
        if (companyId.isNotEmpty()) {
            productViewModel.loadProducts(companyId)
        }
    }

    val existing = remember(shiftId, state.shifts) {
        shiftId?.let { id -> state.shifts.find { it.id == id } }
    }

    // Solo sucursales productoras accesibles para este usuario
    val accessibleBranches = remember(state.producingBranches, user) {
        if (user == null) emptyList()
        else state.producingBranches.filter { user.canAccessBranch(it.id) }
    }

    var selectedBranch by remember { mutableStateOf<Branch?>(null) }
    var branchDropdownExpanded by remember { mutableStateOf(false) }

    // Auto-seleccionar sucursal cuando carguen las opciones
    LaunchedEffect(accessibleBranches, branchId, existing) {
        if (selectedBranch != null) return@LaunchedEffect
        selectedBranch = when {
            existing != null -> accessibleBranches.find { it.id == existing.branchId }
            !branchId.isNullOrEmpty() -> accessibleBranches.find { it.id == branchId }
            accessibleBranches.size == 1 -> accessibleBranches.first()
            else -> null
        }
    }

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var startTime by rememberSaveable { mutableStateOf(existing?.startTime ?: "") }
    var endTime by rememberSaveable { mutableStateOf(existing?.endTime ?: "") }
    var dispatchOrder by rememberSaveable { mutableStateOf(existing?.dispatchOrder ?: 0) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var excludedProductIds by remember {
        mutableStateOf(existing?.excludedProductIds?.toSet() ?: emptySet<String>())
    }
    var excludedSearchQuery by rememberSaveable { mutableStateOf("") }

    if (showStartPicker) {
        val (h, m) = parseHHmm(startTime) ?: (6 to 0)
        TimePickerDialog(
            initialHour = h,
            initialMinute = m,
            title = "Hora de inicio",
            is24Hour = true,
            onDismiss = { showStartPicker = false },
            onConfirm = { hh, mm ->
                startTime = formatHHmm(hh, mm)
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        val (h, m) = parseHHmm(endTime) ?: (14 to 0)
        TimePickerDialog(
            initialHour = h,
            initialMinute = m,
            title = "Hora de fin",
            is24Hour = true,
            onDismiss = { showEndPicker = false },
            onConfirm = { hh, mm ->
                endTime = formatHHmm(hh, mm)
                showEndPicker = false
            }
        )
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNew) "Nuevo Turno" else "Editar Turno",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = BrandColors.Ivory
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Selector de sucursal ──────────────────────────────────────
                if (isNew) {
                    when {
                        // Una sola sucursal: campo deshabilitado (no hay qué elegir)
                        accessibleBranches.size == 1 -> {
                            OutlinedTextField(
                                value = accessibleBranches.first().name,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sucursal productora") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false
                            )
                        }
                        // Varias sucursales: dropdown selector obligatorio
                        accessibleBranches.size > 1 -> {
                            ExposedDropdownMenuBox(
                                expanded = branchDropdownExpanded,
                                onExpandedChange = { branchDropdownExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = selectedBranch?.name ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Sucursal productora *") },
                                    placeholder = { Text("Seleccionar sucursal") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(branchDropdownExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    isError = selectedBranch == null
                                )
                                ExposedDropdownMenu(
                                    expanded = branchDropdownExpanded,
                                    onDismissRequest = { branchDropdownExpanded = false }
                                ) {
                                    accessibleBranches.forEach { branch ->
                                        DropdownMenuItem(
                                            text = { Text(branch.name) },
                                            onClick = {
                                                selectedBranch = branch
                                                branchDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Editando: sucursal de solo lectura
                    val displayName = existing?.branchName
                        ?: accessibleBranches.find { it.id == (existing?.branchId ?: branchId) }?.name
                        ?: ""
                    if (displayName.isNotEmpty()) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Sucursal productora") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false
                        )
                    }
                }

                // ── Nombre del turno ─────────────────────────────────────────
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del turno *") },
                    placeholder = { Text("Ej: Turno Noche, Turno Mañana") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // ── Horario ──────────────────────────────────────────────────
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hora inicio") },
                        placeholder = { Text("22:00") },
                        trailingIcon = {
                            IconButton(onClick = { showStartPicker = true }) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = "Elegir hora de inicio",
                                    tint = BrandColors.Primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showStartPicker = true },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hora fin") },
                        placeholder = { Text("06:00") },
                        trailingIcon = {
                            IconButton(onClick = { showEndPicker = true }) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = "Elegir hora de fin",
                                    tint = BrandColors.Primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showEndPicker = true },
                        singleLine = true
                    )
                }

                // ── Indicador de turno nocturno ──────────────────────────────
                val overnight = remember(startTime, endTime) {
                    isOvernightShift(startTime, endTime)
                }
                if (overnight) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEFE7FF), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.NightsStay,
                            contentDescription = null,
                            tint = Color(0xFF5B3DBF),
                            modifier = Modifier.size(20.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Turno nocturno (+1 día)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF3A1D9E)
                            )
                            Text(
                                "Inicia $startTime hoy y termina $endTime del día siguiente.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF3A1D9E)
                            )
                        }
                    }
                } else if (startTime.isNotEmpty() && endTime.isNotEmpty() && startTime == endTime) {
                    Text(
                        "La hora de inicio y fin no pueden ser iguales.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // ── Viaje de despacho ────────────────────────────────────────
                Text(
                    "¿En qué viaje sale este pan?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary
                )
                Text(
                    text = "El pan del turno de noche sale temprano y el del turno de día a " +
                        "medio día, aunque la noche empiece más tarde en el reloj. Esto define " +
                        "el orden de los turnos al repartir los pedidos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1 to "Primer viaje", 2 to "Segundo viaje").forEach { (value, label) ->
                        FilterChip(
                            selected = dispatchOrder == value,
                            onClick = { dispatchOrder = if (dispatchOrder == value) 0 else value },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandColors.Accent,
                                selectedLabelColor = BrandColors.OnAccent
                            )
                        )
                    }
                }

                // ── Productos que este turno no produce ─────────────────────
                Text(
                    "Productos que este turno NO produce",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary
                )
                Text(
                    text = "Sin selección: este turno puede hornear cualquier producto. Lo que " +
                        "marques acá el reparto automático de pedidos lo manda entero al otro turno.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val filteredProducts = remember(productState.productList, excludedSearchQuery) {
                    productState.productList.filter {
                        it.active && (excludedSearchQuery.isEmpty() ||
                            it.name.contains(excludedSearchQuery, ignoreCase = true))
                    }.sortedBy { it.name }
                }
                if (productState.productList.isNotEmpty()) {
                    OutlinedTextField(
                        value = excludedSearchQuery,
                        onValueChange = { excludedSearchQuery = it },
                        label = { Text("Buscar producto") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    filteredProducts.forEach { product ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    excludedProductIds = if (product.id in excludedProductIds)
                                        excludedProductIds - product.id
                                    else excludedProductIds + product.id
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = product.id in excludedProductIds,
                                onCheckedChange = {
                                    excludedProductIds = if (it) excludedProductIds + product.id
                                    else excludedProductIds - product.id
                                }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(product.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (excludedProductIds.isNotEmpty()) {
                        Text(
                            "${excludedProductIds.size} producto(s) excluido(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        "No hay productos activos. Crea productos primero.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                // ── Guardar ──────────────────────────────────────────────────
                val branch = if (isNew) selectedBranch
                             else existing?.let { e -> Branch(id = e.branchId, name = e.branchName) }
                                 ?: selectedBranch

                Button(
                    onClick = {
                        val b = branch ?: return@Button
                        if (name.isBlank()) return@Button
                        val currentUser = authState.user
                        val shift = ProductionShift(
                            id = existing?.id ?: "",
                            branchId = b.id,
                            branchName = b.name,
                            name = name.trim(),
                            startTime = startTime.trim(),
                            endTime = endTime.trim(),
                            active = true,
                            excludedProductIds = excludedProductIds.toList(),
                            dispatchOrder = dispatchOrder,
                            createdByUserId = existing?.createdByUserId ?: (currentUser?.id ?: ""),
                            createdByUserName = existing?.createdByUserName
                                ?: (currentUser?.displayName?.ifEmpty { currentUser.email } ?: ""),
                            createdAt = existing?.createdAt ?: 0L
                        )
                        viewModel.saveShift(companyId, shift)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary),
                    enabled = name.isNotBlank() && branch != null && !state.isLoading
                ) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                    else Text("Guardar turno", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
