package com.bakery.erp.presentation.production

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Branch
import com.bakery.erp.model.ProductionShift
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionShiftFormScreen(
    branchId: String?,
    shiftId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val isNew = shiftId == null

    // Cargar sucursales si aún no están en estado
    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && state.producingBranches.isEmpty()) {
            viewModel.loadOrderFormData(companyId)
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
                    containerColor = Color(0xFF123456),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFFDFBF7)
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
                        onValueChange = { startTime = it },
                        label = { Text("Hora inicio") },
                        placeholder = { Text("22:00") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("Hora fin") },
                        placeholder = { Text("06:00") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
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
                            createdByUserId = existing?.createdByUserId ?: (currentUser?.id ?: ""),
                            createdByUserName = existing?.createdByUserName
                                ?: (currentUser?.displayName?.ifEmpty { currentUser.email } ?: ""),
                            createdAt = existing?.createdAt ?: 0L
                        )
                        viewModel.saveShift(companyId, shift)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123456)),
                    enabled = name.isNotBlank() && branch != null && !state.isLoading
                ) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                    else Text("Guardar turno", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
