package com.bakery.erp.presentation.customerOrders

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CustomerOrder
import com.bakery.erp.model.CustomerOrderDeliveryType
import com.bakery.erp.model.CustomerOrderLine
import com.bakery.erp.model.CustomerOrderStatus
import com.bakery.erp.model.CustomerOrderType
import com.bakery.erp.model.Permission
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors

private val MutedText = Color(0xFF6E6E6E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerOrderDetailScreen(
    orderId: String,
    onNavigateBack: () -> Unit,
    onEdit: (orderId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CustomerOrderViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val canManage = authState.user?.hasPermission(Permission.MANAGE_CUSTOMER_ORDERS) == true
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(orderId, companyId) {
        if (orderId.isNotEmpty() && companyId.isNotEmpty()) {
            viewModel.selectOrder(companyId, orderId)
        }
    }
    LaunchedEffect(state.isConfirmed, state.isCancelled) {
        if (state.isConfirmed) {
            viewModel.clearConfirmed()
            viewModel.selectOrder(companyId, orderId)
            snackbarHostState.showSnackbar("Pedido confirmado")
        }
        if (state.isCancelled) {
            viewModel.clearCancelled()
            viewModel.selectOrder(companyId, orderId)
            snackbarHostState.showSnackbar("Pedido cancelado")
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val order = state.selectedOrder
    val lines = state.selectedOrderLines
    val lot = state.lotForSelectedOrder
    var showConfirm by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            BrandTopAppBar(
                title = { Text("Detalle de pedido", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (order != null && canManage && order.status == CustomerOrderStatus.PENDING) {
                        IconButton(onClick = { onEdit(order.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isDetailLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
                order == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Pedido no encontrado", color = MutedText)
                }
                else -> DetailContent(
                    order = order,
                    lines = lines,
                    lot = lot,
                    payments = state.selectedOrderPayments,
                    canManage = canManage,
                    onConfirm = { showConfirm = true },
                    onCancel = { showCancel = true }
                )
            }
        }
    }

    if (showConfirm && order != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("¿Confirmar manualmente?") },
            text = {
                Column {
                    Text(
                        "Confirmar manualmente este pedido lo marca como confirmado " +
                            "SIN asignarlo a ninguna planta. Los productos NO entrarán " +
                            "automáticamente a ningún lote de producción y deberás " +
                            "coordinar la producción por fuera.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Lo recomendado es incluirlo desde Home → Pedidos de producción → " +
                            "Nuevo pedido, donde el sistema lo consolidará con el pedido " +
                            "a la planta correspondiente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.confirmOrder(companyId, order.id)
                }) {
                    Text("Confirmar de todos modos", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancelar") }
            }
        )
    }
    if (showCancel && order != null) {
        AlertDialog(
            onDismissRequest = { showCancel = false },
            title = { Text("¿Cancelar pedido?") },
            text = {
                val warn = if (lot != null && lot.status != ProductionLotStatus.OPEN)
                    "El lote ya está en producción o cerrado — esta acción no se puede ejecutar."
                else "Si el pedido está vinculado a un lote abierto, las cantidades se restarán del lote."
                Text(warn)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancel = false
                        viewModel.cancelOrder(companyId, order.id)
                    },
                    enabled = lot == null || lot.status == ProductionLotStatus.OPEN
                ) { Text("Sí, cancelar", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showCancel = false }) { Text("Volver") }
            }
        )
    }
}

@Composable
private fun DetailContent(
    order: CustomerOrder,
    lines: List<CustomerOrderLine>,
    lot: ProductionLot?,
    payments: List<com.bakery.erp.model.CustomerOrderPayment>,
    canManage: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(BurntOrange.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            CustomerOrderType.label(order.type),
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandColors.AccentDeep,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        CustomerOrderStatus.label(order.status),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    order.customerName.ifEmpty { "Cliente sin registrar" },
                    style = MaterialTheme.typography.titleLarge,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.SemiBold
                )
                if (order.customerPhone.isNotEmpty()) {
                    Text(order.customerPhone, style = MaterialTheme.typography.bodySmall, color = MutedText)
                }
                if (order.recipientName.isNotEmpty() &&
                    order.recipientName != order.customerName
                ) {
                    Text(
                        "Recibe: ${order.recipientName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
            }
        }

        // Entrega y producción
        InfoCard("Entrega") {
            DetailRow(
                "Modalidad",
                CustomerOrderDeliveryType.label(order.deliveryType)
            )
            DetailRow("Sucursal", order.deliveryBranchName.ifEmpty { "—" })
            if (order.deliveryType == CustomerOrderDeliveryType.DELIVERY) {
                DetailRow("Dirección", order.deliveryAddress.ifEmpty { "—" })
                if (order.deliveryAddressReference.isNotEmpty()) {
                    DetailRow("Referencia", order.deliveryAddressReference)
                }
            }
            DetailRow(
                "Fecha",
                "${formatDate(order.deliveryDate)}${if (order.deliveryTime.isNotEmpty()) " · ${order.deliveryTime}" else ""}"
            )
            // Quién lo tomó: en un equipo de sucursal es el colaborador que entró con su PIN.
            DetailRow("Tomó el pedido", order.createdByUserName.ifBlank { "—" })
        }

        if (order.producingBranchName.isNotEmpty()) {
            InfoCard("Producción") {
                DetailRow("Planta", order.producingBranchName)
                if (order.shiftName.isNotEmpty()) DetailRow("Turno", order.shiftName)
                if (lot != null) {
                    DetailRow("Lote", "${lot.id.take(6)} · ${ProductionLotStatus.label(lot.status)}")
                }
            }
        }

        // Líneas
        InfoCard("Productos (${lines.size})") {
            lines.forEachIndexed { i, line ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            line.productName.ifEmpty { "Producto" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Medium
                        )
                        if (line.customSpec.isNotEmpty()) {
                            Text(
                                "✱ ${line.customSpec}",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandColors.AccentDeep
                            )
                        }
                        if (!line.requiresProduction) {
                            Text(
                                "Reventa — del stock terminado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${trimZero(line.requestedQuantity)} ${line.productUnit}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PrimaryBlue
                        )
                        if (line.unitPrice > 0.0) {
                            Text(
                                "Q ${formatMoney(line.unitPrice)} c/u",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText
                            )
                        }
                    }
                }
                if (i < lines.lastIndex) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .size(0.dp, 1.dp)
                                .background(Color.LightGray.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }

        // Totales
        InfoCard("Totales") {
            if (order.subtotalAmount > 0.0)
                DetailRow("Subtotal", "Q ${formatMoney(order.subtotalAmount)}")
            if (order.riceSheetAmount > 0.0)
                DetailRow("Hoja de arroz", "Q ${formatMoney(order.riceSheetAmount)}")
            if (order.fountainRentAmount > 0.0)
                DetailRow("Alquiler de fuente", "Q ${formatMoney(order.fountainRentAmount)}")
            if (order.extrasAmount > 0.0)
                DetailRow("Extras", "Q ${formatMoney(order.extrasAmount)}")
            DetailRow("Total", "Q ${formatMoney(order.totalAmount)}", emphasize = true)
            if (order.advancePaidAmount > 0.0)
                DetailRow("Anticipos cobrados", "Q ${formatMoney(order.advancePaidAmount)}")
            if (order.balanceAmount > 0.0)
                DetailRow("Saldo pendiente", "Q ${formatMoney(order.balanceAmount)}", emphasize = true)
            if (order.depositBaseAmount > 0.0)
                DetailRow("Depósito de base", "Q ${formatMoney(order.depositBaseAmount)}")
        }

        // Pagos: de dónde sale "Anticipos cobrados". Cada anticipo recibido en caja lleva su
        // folio, que es lo que el cliente trae en su comprobante.
        if (payments.isNotEmpty() ||
            (order.balanceAmount > 0.0 && order.status != CustomerOrderStatus.CANCELLED)
        ) {
            InfoCard("Pagos") {
                payments.forEach { p ->
                    DetailRow(
                        listOf(
                            com.bakery.erp.model.CustomerOrderPaymentType.label(p.type),
                            p.depositFolio,
                            com.bakery.erp.model.CustomerOrderPaymentMethod.label(p.method)
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        (if (p.amount < 0) "−" else "") + "Q ${formatMoney(kotlin.math.abs(p.amount))}"
                    )
                    if (p.receivedByUserName.isNotBlank()) {
                        Text("Recibió: ${p.receivedByUserName}", style = MaterialTheme.typography.bodySmall, color = MutedText)
                    }
                    if (p.notes.isNotBlank() && p.depositFolio.isBlank()) {
                        Text(p.notes, style = MaterialTheme.typography.bodySmall, color = MutedText)
                    }
                }
                if (order.balanceAmount > 0.0 && order.status != CustomerOrderStatus.CANCELLED) {
                    Text(
                        "Para cobrar un anticipo: Punto de Venta → Anticipo de cliente → Ligar a un pedido. " +
                            "Al entregar, se aplica el anticipo en el cobro y el pedido queda entregado.",
                        style = MaterialTheme.typography.bodySmall, color = MutedText
                    )
                }
            }
        }

        if (order.notes.isNotEmpty()) {
            InfoCard("Notas") {
                Text(order.notes, style = MaterialTheme.typography.bodyMedium, color = MutedText)
            }
        }

        // Acciones
        if (canManage && order.status == CustomerOrderStatus.PENDING) {
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Confirmar pedido")
            }
        }
        if (canManage && order.status in listOf(
                CustomerOrderStatus.PENDING,
                CustomerOrderStatus.CONFIRMED,
                CustomerOrderStatus.IN_PRODUCTION,
                CustomerOrderStatus.READY
            )
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Cancel, contentDescription = null, tint = Color.Red)
                Spacer(Modifier.size(6.dp))
                Text("Cancelar pedido", color = Color.Red)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = PrimaryBlue,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.size(8.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MutedText)
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.bodyMedium
            else MaterialTheme.typography.bodySmall,
            color = if (emphasize) BrandColors.AccentDeep else PrimaryBlue,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

internal fun trimZero(value: Double): String {
    val intPart = value.toLong()
    return if (value == intPart.toDouble()) "$intPart"
    else value.toString().trimEnd('0').trimEnd('.')
}
