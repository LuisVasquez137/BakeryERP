package com.bakery.erp.presentation.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CashMovement
import com.bakery.erp.model.CashMovementType
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.CashSessionStatus
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.util.fmtMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashSessionDetailScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    onNavigateToClose: (sessionId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CashSessionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = authState.user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    var showTransfer by remember { mutableStateOf(false) }

    LaunchedEffect(companyId, sessionId) {
        if (companyId.isNotEmpty()) viewModel.observeSession(companyId, sessionId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    val session = state.currentSession

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de sesión", fontWeight = FontWeight.SemiBold) },
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
        if (session == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { SessionHeaderCard(session) }
                item {
                    Text("Movimientos de caja", fontWeight = FontWeight.SemiBold,
                         modifier = Modifier.padding(top = 4.dp))
                }
                if (state.movements.isEmpty()) {
                    item {
                        Text("Sin movimientos.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                             style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    items(state.movements, key = { it.id }) { m -> MovementRow(m) }
                }
                if (session.status == CashSessionStatus.OPEN) {
                    item {
                        OutlinedButton(
                            onClick = { showTransfer = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("Transferir efectivo a otra caja") }
                    }
                    item {
                        Button(
                            onClick = { onNavigateToClose(session.id) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("Cerrar sesión") }
                    }
                }
            }
        }
        if (showTransfer && user != null) {
            CashTransferDialog(
                companyId = companyId,
                currentSession = session,
                currentUser = user,
                onDismiss = { showTransfer = false },
                onTransferred = {
                    showTransfer = false
                    viewModel.observeSession(companyId, sessionId)
                },
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun SessionHeaderCard(session: CashSession) {
    val statusColor = if (session.status == CashSessionStatus.OPEN)
        Color(0xFF1B5E20) else MaterialTheme.colorScheme.outline
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${session.branchName} · ${session.registerName}",
                     fontWeight = FontWeight.SemiBold)
                AssistChip(
                    onClick = {},
                    label = { Text(CashSessionStatus.label(session.status)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = statusColor)
                )
            }
            Text("Cajero: ${session.cashierName}",
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Fondo inicial"); Text("Q ${session.openingBalance.fmtMoney(2)}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ventas totales"); Text("Q ${session.totalSales.fmtMoney(2)}",
                                              fontWeight = FontWeight.SemiBold)
            }
            Text("Efectivo: Q ${session.totalCashSales.fmtMoney(2)} · " +
                 "Tarjeta: Q ${session.totalCardSales.fmtMoney(2)} · " +
                 "Transf: Q ${session.totalTransferSales.fmtMoney(2)}",
                 style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.outline)
            Text("# ventas: ${session.saleCount}",
                 style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.outline)
            if (session.status == CashSessionStatus.CLOSED) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Esperado"); Text("Q ${session.expectedCash.fmtMoney(2)}")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Contado"); Text("Q ${session.countedCash.fmtMoney(2)}")
                }
                val diffColor = when {
                    session.difference > 0.01 -> Color(0xFF1B5E20)
                    session.difference < -0.01 -> Color(0xFFB00020)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Diferencia", fontWeight = FontWeight.SemiBold, color = diffColor)
                    Text("Q ${session.difference.fmtMoney(2)}",
                         fontWeight = FontWeight.Bold, color = diffColor)
                }
                session.notes?.let { Text("Notas: $it",
                                          style = MaterialTheme.typography.bodySmall,
                                          color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun MovementRow(m: CashMovement) {
    val sign = CashMovementType.sign(m.type)
    val signColor = when {
        sign > 0 -> Color(0xFF1B5E20)
        sign < 0 -> Color(0xFFB00020)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(CashMovementType.label(m.type), fontWeight = FontWeight.SemiBold)
                Text(m.reason, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Por ${m.createdByUserName}", style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.outline)
            }
            Text("${if (sign >= 0) "+" else "-"}Q ${m.amount.fmtMoney(2)}",
                 fontWeight = FontWeight.Bold, color = signColor)
        }
    }
}
