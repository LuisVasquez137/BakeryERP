package com.bakery.erp.presentation.branches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.bakery.erp.model.Branch
import com.bakery.erp.model.DISPATCH_TRIP_FIRST
import com.bakery.erp.model.DISPATCH_TRIP_SECOND
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.isoWeekdayName
import com.bakery.erp.util.fmt
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.products.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchDetailScreen(
    branchId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (branchId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: BranchViewModel,
    productViewModel: ProductViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val productState by productViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val branch = state.branchList.find { it.id == branchId }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) productViewModel.loadProducts(companyId)
    }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) { viewModel.clearDeleted(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar sucursal") },
            text = {
                Text("¿Estás seguro que deseas eliminar \"${branch?.name}\"? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        branch?.let { viewModel.deleteBranch(companyId, it.id) }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = branch?.name ?: "Detalle",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { branch?.let { onNavigateToEdit(it.id) } }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                branch == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sucursal no encontrada", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> BranchDetailContent(
                    branch = branch,
                    assignedProductNames = productState.productList
                        .filter { it.id in branch.assignedProductIds }
                        .map { it.name }
                        .sorted()
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BranchDetailContent(
    branch: Branch,
    assignedProductNames: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = branch.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Surface(
            color = if (branch.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = if (branch.active) "Activa" else "Inactiva",
                style = MaterialTheme.typography.labelSmall,
                color = if (branch.active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── Información general ───────────────────────────────────────────────
        DetailSection("Información General")
        if (branch.location.isNotEmpty()) DetailRow("Dirección", branch.location)
        if (branch.latitude != null && branch.longitude != null) {
            // Capturada automáticamente (GPS) al crear el primer pedido de esta
            // sucursal — ver Branch.latitude. Se puede corregir desde "Editar"
            // si la capturó alguien que no estaba físicamente en la sucursal.
            DetailRow("Ubicación (GPS)", "${branch.latitude.fmt(5)}, ${branch.longitude.fmt(5)}")
        }
        if (branch.phone.isNotEmpty()) DetailRow("Teléfono", branch.phone)
        if (branch.managerName.isNotEmpty()) DetailRow("Encargado", branch.managerName)

        // ── Capacidades ───────────────────────────────────────────────────────
        if (branch.capabilities.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            DetailSection("Capacidades")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                branch.capabilities.forEach { cap ->
                    CapabilityChip(cap)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // ── Productos que produce (solo para plantas productoras) ─────────────
        if (BranchCapability.CAN_PRODUCE in branch.capabilities) {
            Spacer(Modifier.height(4.dp))
            DetailSection("Productos que produce")
            if (assignedProductNames.isEmpty()) {
                Text(
                    text = "Todos los productos (sin restricción configurada)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    assignedProductNames.forEach { name ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            if (branch.closedWeekdays.isNotEmpty()) {
                DetailRow(
                    "Día(s) de descanso",
                    branch.closedWeekdays.sorted()
                        .joinToString(", ") { isoWeekdayName(it).replaceFirstChar { c -> c.uppercase() } }
                )
            }

            when (branch.receivesOnlyInTrip) {
                DISPATCH_TRIP_FIRST -> "Solo el primer viaje"
                DISPATCH_TRIP_SECOND -> "Solo el segundo viaje"
                else -> null
            }?.let { DetailRow("Recibe despacho en", it) }
        }

        // ── Notas ────────────────────────────────────────────────────────────
        if (!branch.notes.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            DetailSection("Notas")
            Text(
                text = branch.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DetailSection(title: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f)
        )
    }
}
