package com.bakery.erp.presentation.preparationbatches

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.preparations.formatBatchWeight
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.util.fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparationBatchFormScreen(
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: PreparationBatchViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    var branchMenu by remember { mutableStateOf(false) }
    var prepMenu by remember { mutableStateOf(false) }

    LaunchedEffect(companyId) {
        if (user != null && companyId.isNotEmpty()) {
            viewModel.resetForm()
            viewModel.loadFormData(companyId, user)
        }
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Producir lote", fontWeight = FontWeight.SemiBold) },
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
        if (state.isLoadingForm) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        AdaptiveContainer(modifier = Modifier.padding(padding), maxContentWidth = 640.dp) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Paso 1: Sucursal ─────────────────────────────────────────
                Text("1. Sucursal de producción", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (state.accessibleBranches.isEmpty()) {
                    Text(
                        "No tienes sucursales con capacidad de producción asignadas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Box {
                        OutlinedButton(
                            onClick = { branchMenu = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                state.selectedBranch?.name ?: "Seleccionar sucursal",
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = branchMenu, onDismissRequest = { branchMenu = false }) {
                            state.accessibleBranches.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text(b.name) },
                                    onClick = { viewModel.selectBranch(companyId, b); branchMenu = false }
                                )
                            }
                        }
                    }
                }

                // ── Paso 2: Preparación ──────────────────────────────────────
                if (state.selectedBranch != null) {
                    HorizontalDivider()
                    Text("2. Preparación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (state.preparations.isEmpty()) {
                        Text(
                            "No hay preparaciones activas. Crea una en el módulo Preparaciones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Box {
                            OutlinedButton(
                                onClick = { prepMenu = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    state.selectedPreparation?.name ?: "Seleccionar preparación",
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = prepMenu, onDismissRequest = { prepMenu = false }) {
                                state.preparations.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.name) },
                                        onClick = { viewModel.selectPreparation(companyId, p); prepMenu = false }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Paso 3: Cantidad de lotes ────────────────────────────────
                val prep = state.selectedPreparation
                if (prep != null) {
                    HorizontalDivider()
                    Text("3. Cantidad de lotes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.setBatchCount(companyId, state.batchCount - 1) },
                            enabled = state.batchCount > 1
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Menos")
                        }
                        Text(
                            "${state.batchCount}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.setBatchCount(companyId, state.batchCount + 1) }) {
                            Icon(Icons.Default.Add, contentDescription = "Más")
                        }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Producirá", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                formatBatchWeight(prep.batchWeightGrams * state.batchCount),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // ── Vista previa de insumos requeridos ───────────────────
                    HorizontalDivider()
                    Text("Insumos requeridos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (state.isCheckingStock) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            state.requirementLines.forEachIndexed { i, line ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (line.sufficient) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (line.sufficient) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(line.ingredientName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text(
                                            "Disponible: ${line.availableStock.fmt(2)} ${line.baseUnit}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (line.sufficient) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Text(
                                        "${line.requiredQuantity.fmt(2)} ${line.baseUnit}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (i < state.requirementLines.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }

                    if (!state.canProduce && state.requirementLines.isNotEmpty() && !state.isCheckingStock) {
                        Text(
                            "Stock insuficiente para producir esta cantidad de lotes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = { if (user != null) viewModel.produceBatch(companyId, user) },
                        enabled = state.canProduce && !state.isLoading && !state.isCheckingStock,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Producir lote y descontar inventario")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
