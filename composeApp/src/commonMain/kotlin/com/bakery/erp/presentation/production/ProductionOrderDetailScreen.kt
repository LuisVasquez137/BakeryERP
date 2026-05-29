package com.bakery.erp.presentation.production

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Permission
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.ProductionOrderStatus
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionOrderDetailScreen(
    orderId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit = {},
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel
) {
    val snackbarHostState = remember { SnackbarHostState() }
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
                    containerColor = Color(0xFF005B8D),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFFDFBF7)
    ) { padding ->
        ProductionOrderDetailContent(
            orderId = orderId,
            authViewModel = authViewModel,
            viewModel = viewModel,
            onNavigateToEdit = onNavigateToEdit,
            onAfterCancel = onNavigateBack,
            snackbarHostState = snackbarHostState,
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}

/**
 * Cuerpo del detalle de pedido — sin Scaffold ni TopAppBar — para reutilizar como panel
 * embebido en pantallas anchas (al lado de la lista) o dentro del Scaffold de su screen
 * propia en pantallas pequeñas.
 */
@Composable
fun ProductionOrderDetailContent(
    orderId: String,
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel,
    onNavigateToEdit: (String) -> Unit,
    onAfterCancel: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onClosePanel: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canSend = user?.hasPermission(Permission.SEND_PRODUCTION_ORDER) == true
    val canManageLots = user?.hasPermission(Permission.MANAGE_PRODUCTION_LOTS) == true
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
                    onAfterCancel()
                }) { Text("Cancelar pedido", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showCancelDialog = false }) { Text("No, mantener") } }
        )
    }

    val order = state.selectedOrder
    val lines = state.selectedOrderLines

    if (state.isDetailLoading || order == null || order.id != orderId) {
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier,
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
                        Text(
                            "Pedido",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF005B8D)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusChip(order.status, state.lotForSelectedOrder?.status, order.isLateOrder)
                            if (onClosePanel != null) {
                                Spacer(Modifier.width(6.dp))
                                IconButton(onClick = onClosePanel) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cerrar",
                                        tint = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    InfoRow("Sucursal solicitante", order.sellingBranchName)
                    InfoRow("Planta destino", order.producingBranchName)
                    InfoRow("Turno", order.shiftName.ifBlank { "—" })
                    InfoRow("Para el día", formatProductionDate(order.requestedDate))
                    InfoRow(
                        "Creado por",
                        "${order.createdByUserName.ifBlank { "—" }} · ${formatHourMinute(order.createdAt)}"
                    )
                    if (!order.notes.isNullOrBlank()) InfoRow("Notas", order.notes)
                }
            }
        }

        // Aviso si el pedido ya está en un lote y el usuario no es de la planta productora.
        val lotForOrder = state.lotForSelectedOrder
        val isProducingStaff = user?.canAccessBranch(order.producingBranchId) == true
        if (lotForOrder != null && order.status == ProductionOrderStatus.PENDING && !isProducingStaff) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFD9A441)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Este pedido ya está en un lote (${ProductionLotStatus.label(lotForOrder.status)}).",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFD9A441),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Contacta al supervisor de sucursal de la planta productora " +
                                    "(${order.producingBranchName}) para modificar o cancelar este pedido.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7A3300)
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Productos solicitados (${lines.size})",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF005B8D)
            )
        }

        items(lines) { line ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(line.productName, fontWeight = FontWeight.SemiBold, color = Color(0xFF005B8D))
                        Text(line.productUnit, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Text(
                        "${line.requestedQuantity.toInt()} uds.",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD9A441)
                    )
                }
            }
        }

        if (order.status == ProductionOrderStatus.PENDING) {
            val lotStatus = state.lotForSelectedOrder?.status
            // Editar permitido si:
            //   - sin lote: el usuario solicitante con permiso SEND_PRODUCTION_ORDER.
            //   - lote OPEN: solo la planta productora (acceso a la sucursal productora
            //     + permiso MANAGE_PRODUCTION_LOTS). Esto evita que la sucursal origen
            //     cambie unilateralmente algo que la planta ya está consolidando.
            //   - lote IN_PROGRESS o COMPLETED: nadie edita.
            val editable = when (lotStatus) {
                null -> canSend
                ProductionLotStatus.OPEN -> isProducingStaff && canManageLots
                else -> false
            }
            // Cancelar permitido si: no hay lote (la sucursal solicitante puede), o el
            // lote está OPEN (solo la planta productora puede; la sucursal origen ya no
            // tiene control unilateral una vez consolidado). Bloqueado en IN_PROGRESS /
            // COMPLETED.
            val cancellable = when (lotStatus) {
                null -> canSend
                ProductionLotStatus.OPEN -> isProducingStaff && canManageLots
                else -> false
            }
            if (editable || canSend) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onNavigateToEdit(order.id) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = editable,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005B8D))
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Editar pedido", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (cancellable || canSend) {
                item {
                    OutlinedButton(
                        onClick = { showCancelDialog = true },
                        enabled = cancellable,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Cancelar pedido") }
                    if (!cancellable && lotStatus != null && lotStatus != ProductionLotStatus.OPEN) {
                        Text(
                            "El lote ya está en producción o cerrado. La cancelación queda " +
                                "bloqueada para no descuadrar el stock.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD9A441),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String, lotStatus: String? = null, isLateOrder: Boolean = false) {
    val (label, color, bg) = effectiveOrderDisplay(status, lotStatus, isLateOrder)
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(
            label,
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
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF005B8D))
    }
}
