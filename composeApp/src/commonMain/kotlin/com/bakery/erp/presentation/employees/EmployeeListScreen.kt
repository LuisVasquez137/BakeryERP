package com.bakery.erp.presentation.employees

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.unit.Dp
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.model.canAccessBranch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Employee
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.ui.theme.BrandColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToForm: (String?) -> Unit,
    onNavigateToJobPositions: () -> Unit = {},
    authViewModel: AuthViewModel,
    viewModel: EmployeeViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) viewModel.loadEmployees(companyId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // Solo las sucursales del usuario: la encargada de una sucursal ve y edita a
    // su gente, no la planilla entera. El dueño (o un usuario sin sucursales
    // asignadas) sigue viendo todas — mismo criterio que canAccessBranch.
    val user = authState.user
    val myBranches = remember(state.branches, user) {
        state.branches.filter { user?.canAccessBranch(it.id) == true }
    }

    // Filtrado en memoria: sucursal + búsqueda
    val displayed = remember(state.employees, state.branchFilter, state.searchQuery, user) {
        val q = state.searchQuery.trim()
        state.employees
            .filter { user?.canAccessBranch(it.branchId) == true }
            .filter { state.branchFilter == null || it.branchId == state.branchFilter }
            .filter {
                q.isBlank() ||
                it.name.contains(q, ignoreCase = true) ||
                it.positionTitle.contains(q, ignoreCase = true) ||
                it.phone.contains(q, ignoreCase = true) ||
                it.branchName.contains(q, ignoreCase = true)
            }
    }

    val hasFilters = state.searchQuery.isNotBlank() || state.branchFilter != null

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Colaboradores", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToJobPositions) {
                        Text("Puestos", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToForm(null) },
                containerColor = BrandColors.Primary
            ) { Icon(Icons.Default.Add, "Nuevo colaborador", tint = Color.White) }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BrandColors.Ivory
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Barra de búsqueda ─────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .shadow(4.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                TextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = {
                        Text(
                            "Buscar por nombre, cargo o sucursal…",
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.outline)
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, "Limpiar", tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Chips de sucursal (usa state.branches del VM, no empleados) ─
            if (myBranches.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.branchFilter == null,
                            onClick = { viewModel.setBranchFilter(null) },
                            label = { Text("Todas") }
                        )
                    }
                    items(myBranches) { branch ->
                        FilterChip(
                            selected = state.branchFilter == branch.id,
                            onClick = {
                                viewModel.setBranchFilter(
                                    if (state.branchFilter == branch.id) null else branch.id
                                )
                            },
                            label = { Text(branch.name) }
                        )
                    }
                }
            }

            // ── Layout adaptativo ─────────────────────────────────────────
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 900.dp
                val isTablet = maxWidth >= 600.dp

                when {
                    // ── PC / tablet horizontal ────────────────────────────
                    isWideScreen -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.fillMaxHeight().weight(0.4f)) {
                                EmployeeTableHeader()
                                when {
                                    state.isLoading -> Box(
                                        Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) { CircularProgressIndicator() }
                                    displayed.isEmpty() -> Box(
                                        Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (hasFilters) "Sin resultados" else "Sin colaboradores registrados",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(displayed, key = { it.id }) { emp ->
                                            EmployeeTableRow(
                                                employee = emp,
                                                isSelected = state.selectedEmployee?.id == emp.id,
                                                onClick = { viewModel.selectEmployee(emp) }
                                            )
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                            Box(modifier = Modifier.fillMaxHeight().weight(0.6f)) {
                                when {
                                    state.isDetailLoading -> CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                    state.selectedEmployee != null -> EmployeeDetailContent(
                                        employee = state.selectedEmployee!!,
                                        onEdit = { onNavigateToForm(state.selectedEmployee!!.id) },
                                        onFullDetail = { onNavigateToDetail(state.selectedEmployee!!.id) }
                                    )
                                    else -> Text(
                                        "Selecciona un colaborador para ver el detalle",
                                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // ── Tablet vertical ───────────────────────────────────
                    isTablet -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            EmployeeTableHeader()
                            when {
                                state.isLoading -> Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) { CircularProgressIndicator() }
                                displayed.isEmpty() -> EmployeeEmptyState(
                                    hasFilter = hasFilters,
                                    modifier = Modifier.fillMaxSize()
                                )
                                else -> LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    items(displayed, key = { it.id }) { emp ->
                                        EmployeeTableRow(
                                            employee = emp,
                                            isSelected = false,
                                            onClick = { onNavigateToDetail(emp.id) }
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }

                    // ── Móvil: cards ──────────────────────────────────────
                    else -> {
                        when {
                            state.isLoading -> Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                            displayed.isEmpty() -> EmployeeEmptyState(
                                hasFilter = hasFilters,
                                modifier = Modifier.fillMaxSize()
                            )
                            else -> LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(displayed, key = { it.id }) { emp ->
                                    EmployeeCard(
                                        employee = emp,
                                        onClick = { onNavigateToDetail(emp.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }
}

// ── Detalle (reutilizado en panel ancho y en EmployeeDetailScreen) ────────────

@Composable
internal fun EmployeeDetailContent(
    employee: Employee,
    onEdit: () -> Unit,
    onFullDetail: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmployeeAvatar(employee, size = 88.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    employee.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary
                )
                if (employee.positionTitle.isNotEmpty()) {
                    Text(
                        employee.positionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onFullDetail != null) {
                    OutlinedButton(onClick = onFullDetail) { Text("Ver detalle") }
                }
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
                ) { Text("Editar") }
            }
        }

        ProfileCompletenessBar(employee)

        HorizontalDivider()

        if (employee.branchName.isNotEmpty()) {
            EmployeeDetailRow(Icons.Default.Store, "Sucursal", employee.branchName)
        }
        if (employee.phone.isNotEmpty()) {
            EmployeeDetailRow(Icons.Default.Phone, "Teléfono", employee.phone)
        }
        if (employee.phoneAux.isNotEmpty()) {
            EmployeeDetailRow(Icons.Default.Phone, "Teléfono auxiliar", employee.phoneAux)
        }
        if (employee.email.isNotEmpty()) {
            EmployeeDetailRow(Icons.Default.Email, "Correo electrónico", employee.email)
        }
        if (employee.address.isNotEmpty()) {
            EmployeeDetailRow(Icons.Default.Home, "Dirección", employee.address)
        }
        if (employee.hireDate > 0L) {
            EmployeeDetailRow(Icons.Default.CalendarToday, "Fecha de ingreso", formatEmployeeDate(employee.hireDate))
        }
        if (employee.notes.isNotEmpty()) {
            EmployeeDetailRow(Icons.Default.Notes, "Notas", employee.notes)
        }
    }
}

@Composable
private fun EmployeeAvatar(employee: Employee, size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(BrandColors.Primary.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center
    ) {
        if (employee.photoUrl.isNotBlank()) {
            ProductImage(imageUrl = employee.photoUrl, contentDescription = employee.name, modifier = Modifier.size(size))
        } else {
            Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(size * 0.66f), tint = BrandColors.Primary.copy(alpha = 0.5f))
        }
    }
}

/**
 * Qué tan llena está la ficha. Solo campos del documento del colaborador, que ya viene en la
 * lista: no cuesta ninguna lectura. El salario no cuenta a propósito (no todos pueden verlo).
 */
internal fun Employee.missingProfileFields(): Pair<Int, List<String>> {
    val checks = listOf(
        "Foto" to photoUrl.isNotBlank(),
        "DPI" to dpi.isNotBlank(),
        "NIT" to nit.isNotBlank(),
        "Fecha de nacimiento" to (birthDate > 0L),
        "Teléfono" to phone.isNotBlank(),
        "Dirección" to address.isNotBlank(),
        "Contacto de emergencia" to (emergencyContact.isNotBlank() && emergencyPhone.isNotBlank()),
        "Sucursal" to branchId.isNotBlank(),
        "Puesto" to positionId.isNotBlank(),
        "Tipo de contrato" to contractType.isNotBlank(),
        "Fecha de ingreso" to (hireDate > 0L),
        "Horario" to (workDays.isNotEmpty() && startTime.isNotBlank() && endTime.isNotBlank()),
        "Tipo de pago" to paymentType.isNotBlank()
    )
    val missing = checks.filterNot { it.second }.map { it.first }
    val percent = (checks.size - missing.size) * 100 / checks.size
    return percent to missing
}

@Composable
private fun ProfileCompletenessBar(employee: Employee) {
    val (percent, missing) = employee.missingProfileFields()
    val complete = missing.isEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Información al $percent%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (complete) BrandColors.Success else BrandColors.TextSlate
            )
            if (complete) BrandChip("Completa", ChipTone.SUCCESS)
        }
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = if (complete) BrandColors.Success else BrandColors.Primary,
            trackColor = BrandColors.PrimaryContainer
        )
        if (!complete) {
            Text(
                "Falta: ${missing.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.MutedSlate
            )
        }
    }
}

@Composable
private fun EmployeeDetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp).padding(top = 2.dp), tint = BrandColors.Primary)
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ── Componentes de lista ──────────────────────────────────────────────────────

@Composable
private fun EmployeeCard(employee: Employee, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Foto circular
            if (employee.photoUrl.isNotBlank()) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape)) {
                    ProductImage(
                        imageUrl = employee.photoUrl,
                        contentDescription = employee.name,
                        modifier = Modifier.size(48.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(BrandColors.Primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(32.dp), tint = BrandColors.Primary.copy(alpha = 0.5f))
                }
            }

            // Nombre, puesto y teléfono
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                        Text(
                            employee.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (employee.positionTitle.isNotEmpty()) {
                            Text(
                                employee.positionTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (employee.branchName.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                employee.branchName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (employee.phone.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Phone, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                        Text(employee.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmployeeTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text("Nombre", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.3f))
        Text("Teléfono", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.2f))
        Text("Cargo", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.25f))
        Text("Sucursal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.25f))
    }
    HorizontalDivider()
}

@Composable
private fun EmployeeTableRow(employee: Employee, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Foto + nombre en primera columna
        Row(
            modifier = Modifier.weight(0.3f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (employee.photoUrl.isNotBlank()) {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape)) {
                    ProductImage(imageUrl = employee.photoUrl, contentDescription = null, modifier = Modifier.size(32.dp))
                }
            } else {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(BrandColors.Primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(20.dp), tint = BrandColors.Primary.copy(alpha = 0.5f))
                }
            }
            Text(
                employee.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            employee.phone.ifEmpty { "—" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            employee.positionTitle.ifEmpty { "—" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.25f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            employee.branchName.ifEmpty { "—" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.25f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmployeeEmptyState(hasFilter: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Group, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(
            if (hasFilter) "Sin resultados" else "Sin colaboradores registrados",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (hasFilter) "Prueba con otro filtro o término de búsqueda"
            else "Toca + para registrar el primer colaborador",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ── Utilidades de fecha ───────────────────────────────────────────────────────

internal fun formatEmployeeDate(millis: Long): String {
    if (millis == 0L) return "Sin fecha"
    var days = millis / 86_400_000L
    var year = 1970
    var month = 1
    while (true) {
        val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
        val diy = if (leap) 366L else 365L
        if (days < diy) break
        days -= diy
        year++
    }
    val daysInMonth = intArrayOf(
        31, if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28,
        31, 30, 31, 30, 31, 31, 30, 31, 30, 31
    )
    while (month <= 12 && days >= daysInMonth[month - 1]) {
        days -= daysInMonth[month - 1]
        month++
    }
    val day = (days + 1).toInt()
    return "${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/$year"
}

internal fun parseDateToMillis(dateStr: String): Long {
    val parts = dateStr.trim().split("/")
    if (parts.size != 3) return 0L
    val day = parts[0].toIntOrNull()?.takeIf { it in 1..31 } ?: return 0L
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return 0L
    val year = parts[2].toIntOrNull()?.takeIf { it in 1900..2100 } ?: return 0L
    var totalDays = 0L
    for (y in 1970 until year) {
        val leap = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
        totalDays += if (leap) 366L else 365L
    }
    val daysInMonth = intArrayOf(
        31, if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28,
        31, 30, 31, 30, 31, 31, 30, 31, 30, 31
    )
    for (m in 1 until month) totalDays += daysInMonth[m - 1]
    totalDays += (day - 1)
    return totalDays * 86_400_000L
}
