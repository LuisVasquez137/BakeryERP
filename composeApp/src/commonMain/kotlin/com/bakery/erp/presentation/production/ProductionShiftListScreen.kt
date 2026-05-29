package com.bakery.erp.presentation.production

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Branch
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bakery.erp.model.ProductionShift
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.presentation.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionShiftListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToForm: (branchId: String, shiftId: String?) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedBranch by remember { mutableStateOf<Branch?>(null) }
    var branchDropdownExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<ProductionShift?>(null) }

    // Solo sucursales productoras a las que el usuario tiene acceso
    val accessibleBranches = remember(state.producingBranches, user) {
        if (user == null) emptyList()
        else state.producingBranches.filter { user.canAccessBranch(it.id) }
    }

    LaunchedEffect(companyId) {
        if (companyId.isEmpty()) return@LaunchedEffect
        viewModel.loadOrderFormData(companyId)
    }

    LaunchedEffect(accessibleBranches) {
        if (selectedBranch == null && accessibleBranches.isNotEmpty()) {
            selectedBranch = accessibleBranches.first()
        }
    }

    LaunchedEffect(selectedBranch) {
        val b = selectedBranch ?: return@LaunchedEffect
        if (companyId.isNotEmpty()) viewModel.loadShifts(companyId, b.id)
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            selectedBranch?.let { viewModel.loadShifts(companyId, it.id) }
            viewModel.clearSaved()
        }
    }

    showDeleteDialog?.let { shift ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Eliminar turno") },
            text = { Text("¿Eliminar el turno \"${shift.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteShift(companyId, shift.id)
                    showDeleteDialog = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Turnos de Producción", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF005B8D),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            selectedBranch?.let { branch ->
                FloatingActionButton(
                    onClick = { onNavigateToForm(branch.id, null) },
                    containerColor = Color(0xFF005B8D)
                ) { Icon(Icons.Default.Add, "Nuevo turno", tint = Color.White) }
            }
        },
        containerColor = Color(0xFFFDFBF7)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Selector de sucursal (solo si hay más de una accesible)
            if (accessibleBranches.size > 1) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = branchDropdownExpanded,
                        onExpandedChange = { branchDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedBranch?.name ?: "Seleccionar sucursal",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Sucursal productora") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(branchDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = branchDropdownExpanded,
                            onDismissRequest = { branchDropdownExpanded = false }
                        ) {
                            accessibleBranches.forEach { branch ->
                                DropdownMenuItem(
                                    text = { Text(branch.name) },
                                    onClick = { selectedBranch = branch; branchDropdownExpanded = false }
                                )
                            }
                        }
                    }
                }
            }

            // Contenido adaptativo: tabla en tablet/web, cards en móvil
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isTablet = maxWidth >= 600.dp

                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    state.shifts.isEmpty() -> ShiftEmptyState(modifier = Modifier.fillMaxSize())

                    isTablet -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            ShiftTableHeader()
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(state.shifts, key = { it.id }) { shift ->
                                    ShiftTableRow(
                                        shift = shift,
                                        onEdit = { onNavigateToForm(shift.branchId, shift.id) },
                                        onDelete = { showDeleteDialog = shift }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.shifts, key = { it.id }) { shift ->
                                ShiftCard(
                                    shift = shift,
                                    onEdit = { onNavigateToForm(shift.branchId, shift.id) },
                                    onDelete = { showDeleteDialog = shift }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftCard(shift: ProductionShift, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    shift.name,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF005B8D),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (shift.startTime.isNotEmpty() || shift.endTime.isNotEmpty()) {
                    Text(
                        "${shift.startTime} – ${shift.endTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (shift.createdByUserName.isNotEmpty()) {
                    Text(
                        "Creado por: ${shift.createdByUserName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onEdit) { Text("Editar") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ShiftTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            "Turno",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.3f)
        )
        Text(
            "Horario",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.25f)
        )
        Text(
            "Creado por",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.3f)
        )
        Spacer(Modifier.weight(0.15f))
    }
    HorizontalDivider()
}

@Composable
private fun ShiftTableRow(shift: ProductionShift, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            shift.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF005B8D),
            modifier = Modifier.weight(0.3f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (shift.startTime.isNotEmpty() || shift.endTime.isNotEmpty())
                "${shift.startTime} – ${shift.endTime}" else "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.25f)
        )
        Text(
            shift.createdByUserName.ifEmpty { "—" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.3f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.weight(0.15f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onEdit,
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text("Editar", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    "Eliminar",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ShiftEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AccessTime,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Sin turnos configurados",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Toca + para agregar el primer turno",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
