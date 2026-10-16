package com.bakery.erp.presentation.reseller

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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CustomerOrderStatus
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors

private val MutedText = Color(0xFF6E6E6E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResellerOrderDetailScreen(
    orderId: String,
    onNavigateBack: () -> Unit,
    onEditOrder: (deliveryDate: Long, deliveryTime: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ResellerViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(orderId, companyId) {
        if (orderId.isNotEmpty() && companyId.isNotEmpty()) {
            viewModel.loadOrderDetail(companyId, orderId)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val order = state.selectedOrder
    val lines = state.selectedOrderLines
    val canEdit = order?.status == CustomerOrderStatus.PENDING

    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            BrandTopAppBar(
                title = {
                    Text("Detalle de pedido", fontWeight = FontWeight.SemiBold, color = Color.White)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
                order == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Pedido no encontrado", color = MutedText)
                }
                else -> Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Estado
                    StatusBanner(order.status)

                    // Datos de entrega
                    InfoCard("Entrega") {
                        IconLine(
                            Icons.Default.CalendarToday,
                            "Fecha",
                            formatDate(order.deliveryDate)
                        )
                        if (order.deliveryTime.isNotEmpty()) {
                            IconLine(Icons.Default.AccessTime, "Hora", order.deliveryTime)
                        }
                        if (order.producingBranchName.isNotEmpty()) {
                            IconLine(
                                Icons.Default.Factory,
                                "Producido en",
                                order.producingBranchName +
                                    (order.shiftName.takeIf { it.isNotEmpty() }?.let { " · $it" }
                                        ?: "")
                            )
                        }
                    }

                    // Productos
                    InfoCard("Productos (${lines.size})") {
                        lines.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        line.productName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PrimaryBlue,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (!line.requiresProduction) {
                                        Text(
                                            "Reventa",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedText
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "${trimTrailing(line.requestedQuantity)} ${line.productUnit}",
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
                        }
                    }

                    // Total
                    if (order.totalAmount > 0) {
                        InfoCard("Total") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "A pagar",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedText
                                )
                                Text(
                                    "Q ${formatMoney(order.totalAmount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = BrandColors.AccentDeep,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (order.notes.isNotEmpty()) {
                        InfoCard("Notas") {
                            Text(
                                order.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedText
                            )
                        }
                    }

                    Spacer(Modifier.size(8.dp))

                    // Botón editar
                    if (canEdit) {
                        Button(
                            onClick = { onEditOrder(order.deliveryDate, order.deliveryTime) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandColors.AccentDeep),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Edit, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Editar pedido", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Solo puedes editar tu pedido mientras esté pendiente. " +
                                "Una vez confirmado por la encargada y enviado a producción, " +
                                "ya no se podrá cambiar.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                    } else {
                        Text(
                            "Este pedido ya no se puede editar — la encargada lo confirmó " +
                                "y entró al lote de producción.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(status: String) {
    val (bg, fg, label) = when (status) {
        CustomerOrderStatus.PENDING -> Triple(
            Color(0xFFFFE0B2), Color(0xFFE65100), "Pendiente de confirmación"
        )
        CustomerOrderStatus.CONFIRMED -> Triple(
            Color(0xFFBBDEFB), Color(0xFF0D47A1), "Confirmado por la planta"
        )
        CustomerOrderStatus.IN_PRODUCTION -> Triple(
            Color(0xFFE1BEE7), Color(0xFF4A148C), "En producción"
        )
        CustomerOrderStatus.READY -> Triple(
            Color(0xFFC8E6C9), Color(0xFF1B5E20), "Listo para retirar"
        )
        CustomerOrderStatus.DELIVERED -> Triple(
            Color(0xFFB2DFDB), Color(0xFF004D40), "Entregado"
        )
        CustomerOrderStatus.CANCELLED -> Triple(
            Color(0xFFE0E0E0), Color(0xFF424242), "Cancelado"
        )
        else -> Triple(Color(0xFFE0E0E0), Color(0xFF424242), CustomerOrderStatus.label(status))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = fg, fontWeight = FontWeight.Bold)
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
            Spacer(Modifier.size(6.dp))
            content()
        }
    }
}

@Composable
private fun IconLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MutedText, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MutedText, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = PrimaryBlue, fontWeight = FontWeight.Medium)
    }
}

private fun trimTrailing(v: Double): String {
    val l = v.toLong()
    return if (v == l.toDouble()) "$l" else v.toString().trimEnd('0').trimEnd('.')
}
