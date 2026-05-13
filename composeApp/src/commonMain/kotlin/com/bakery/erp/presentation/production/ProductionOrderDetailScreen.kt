package com.bakery.erp.presentation.production

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
import com.bakery.erp.model.Permission
import com.bakery.erp.model.ProductionOrderStatus
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionOrderDetailScreen(
    orderId: String,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canSend = user?.hasPermission(Permission.SEND_PRODUCTION_ORDER) == true
    val snackbarHostState = remember { SnackbarHostState() }
    var showCancelDialog by remember { mutableStateOf(false) }

    LaunchedEffect(orderId) {
        if (companyId.isNotEmpty()) viewModel.loadOrderDetail(companyId, orderId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancelar pedido") },
            text = { Text("¿Estás seguro de que deseas cancelar este pedido?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelOrder(companyId, orderId)
                    showCancelDialog = false
                    onNavigateBack()
                }) { Text("Cancelar pedido", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showCancelDialog = false }) { Text("No, mantener") } }
        )
    }

    val order = state.selectedOrder
    val lines = state.selectedOrderLines

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Detalle del Pedido", fontWeight = FontWeight.SemiBold) },
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
        if (state.isDetailLoading || order == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Pedido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF123456))
                                StatusChip(order.status)
                            }
                            HorizontalDivider()
                            InfoRow("Sucursal origen", order.sellingBranchName)
                            InfoRow("Planta destino", order.producingBranchName)
                            InfoRow("Para el día", formatProductionDate(order.requestedDate))
                            InfoRow("Creado por", order.createdByUserName)
                            if (!order.notes.isNullOrBlank()) InfoRow("Notas", order.notes)
                        }
                    }
                }

                item {
                    Text(
                        "Productos solicitados (${lines.size})",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF123456)
                    )
                }

                items(lines) { line ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(line.productName, fontWeight = FontWeight.SemiBold, color = Color(0xFF123456))
                                Text(line.productUnit, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Text(
                                "${line.requestedQuantity.toInt()} uds.",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCC5500)
                            )
                        }
                    }
                }

                if (canSend && order.status == ProductionOrderStatus.PENDING) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showCancelDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Cancelar pedido") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (color, bg) = when (status) {
        ProductionOrderStatus.PENDING   -> Color(0xFFF57C00) to Color(0xFFFFF3E0)
        ProductionOrderStatus.CONFIRMED -> Color(0xFF2E7D32) to Color(0xFFE8F5E9)
        ProductionOrderStatus.CANCELLED -> Color.Gray to Color(0xFFF5F5F5)
        else                            -> Color.Gray to Color(0xFFF5F5F5)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(
            ProductionOrderStatus.label(status),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF123456))
    }
}
