package com.bakery.erp.presentation.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Branch
import com.bakery.erp.model.CashRegister
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.isOperatedBy
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun POSHomeScreen(
    onNavigateBack: () -> Unit,
    onOpenSession: (registerId: String) -> Unit,
    onContinueSession: (sessionId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CashSessionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val user = authState.user
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && user != null) viewModel.loadBranches(companyId, user)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    // Aviso de las cajas que el sistema cerró solo. Va como snackbar y no en silencio: quien
    // llega a abrir caja tiene que enterarse de que la de ayer se cerró sin contarse.
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Punto de Venta", fontWeight = FontWeight.SemiBold) },
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
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                if (state.branches.isEmpty() && state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@Column
                }
                if (state.branches.isEmpty()) {
                    Text("No tienes acceso a ninguna sucursal con capacidad de venta.",
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    return@Column
                }

                // Selector de sucursal (chips). Auto-selección si solo hay una.
                if (state.branches.size > 1) {
                    Text("Sucursal", style = MaterialTheme.typography.labelMedium,
                         fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.branches.forEach { b ->
                            FilterChip(
                                selected = b.id == state.selectedBranchId,
                                onClick = { viewModel.selectBranch(companyId, b.id) },
                                label = { Text(b.name) }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                val branch = state.branches.firstOrNull { it.id == state.selectedBranchId }
                if (branch != null) {
                    Text("Cajas registradoras de ${branch.name}",
                         style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    if (state.registers.isEmpty()) {
                        Text("Esta sucursal no tiene cajas registradoras configuradas.",
                             color = MaterialTheme.colorScheme.onSurfaceVariant,
                             style = MaterialTheme.typography.bodySmall)
                        Text("Pídele a un administrador que cree una desde el módulo \"Cajas Registradoras\".",
                             color = MaterialTheme.colorScheme.outline,
                             style = MaterialTheme.typography.labelSmall)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.registers, key = { it.id }) { reg ->
                                val openSess = state.openSessionsByRegister[reg.id]
                                RegisterCard(
                                    register = reg,
                                    openSession = openSess,
                                    currentUser = user,
                                    onOpen = { onOpenSession(reg.id) },
                                    onContinue = { sid -> onContinueSession(sid) }
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
private fun RegisterCard(
    register: CashRegister,
    openSession: CashSession?,
    currentUser: com.bakery.erp.model.User?,
    onOpen: () -> Unit,
    onContinue: (sessionId: String) -> Unit
) {
    // En un equipo compartido "mía" es de la PERSONA, no de la cuenta (ver isOperatedBy).
    val isMine = openSession != null && currentUser != null &&
        openSession.isOperatedBy(currentUser)
    val isOpen = openSession != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PointOfSale, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(register.name, fontWeight = FontWeight.SemiBold)
                if (openSession != null) {
                    Text("Abierta por ${openSession.openedByEmployeeName.ifBlank { openSession.cashierName }}",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Lista para abrir", style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.outline)
                }
            }
            when {
                openSession != null && isMine ->
                    Button(onClick = { onContinue(openSession.id) }) { Text("Continuar") }
                openSession != null && !isMine ->
                    AssistChip(
                        onClick = {},
                        label = { Text("En uso") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) }
                    )
                else -> Button(onClick = onOpen) { Text("Abrir caja") }
            }
        }
    }
}
