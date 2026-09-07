package com.bakery.erp.presentation.reseller

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Storefront
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
import com.bakery.erp.model.CustomerOrder
import com.bakery.erp.model.CustomerOrderStatus
import com.bakery.erp.model.ResellerDeliveryMode
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors

private val MutedText = Color(0xFF6E6E6E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResellerHomeScreen(
    onNavigateToQuickOrder: () -> Unit,
    onNavigateToOrderDetail: (orderId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ResellerViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val userId = authState.user?.id ?: ""
    val displayName = authState.user?.displayName ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId, userId) {
        if (companyId.isNotEmpty() && userId.isNotEmpty()) {
            viewModel.loadForUser(companyId, userId)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            TopAppBar(
                title = {
                    Text("Mi cuenta", fontWeight = FontWeight.SemiBold, color = Color.White)
                },
                actions = {
                    IconButton(onClick = { authViewModel.signOut() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Salir",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading && state.customer == null -> Box(
                    Modifier.fillMaxSize(), Alignment.Center
                ) { CircularProgressIndicator(color = PrimaryBlue) }
                state.customer == null -> Box(
                    Modifier.fillMaxSize().padding(24.dp), Alignment.Center
                ) {
                    Text(
                        "No se encontró tu perfil de revendedor.",
                        color = MutedText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {
                    val customer = state.customer!!
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Saludo
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(BrandColors.AccentDeep, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    (displayName.firstOrNull() ?: 'R').toString().uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "¡Hola, ${displayName.split(" ").firstOrNull() ?: "Revendedor"}!",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PrimaryBlue,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    customer.businessName.ifEmpty { "Revendedor" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedText
                                )
                            }
                        }

                        // Botón gigante: Hacer pedido
                        Button(
                            onClick = onNavigateToQuickOrder,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandColors.AccentDeep),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                vertical = 22.dp
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Hacer pedido de hoy",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Card: tu configuración
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "Tu configuración",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PrimaryBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.size(10.dp))
                                if (customer.deliveryMode == ResellerDeliveryMode.PICKUP) {
                                    IconLine(
                                        Icons.Default.Storefront,
                                        "Recoges en",
                                        customer.preferredBranchName.ifEmpty { "—" }
                                    )
                                    if (customer.preferredPickupTime.isNotEmpty()) {
                                        IconLine(
                                            Icons.Default.AccessTime,
                                            "Hora habitual",
                                            customer.preferredPickupTime
                                        )
                                    }
                                } else {
                                    IconLine(
                                        Icons.Default.LocalShipping,
                                        "Te entregamos en",
                                        customer.address.ifEmpty { "—" }
                                    )
                                }
                                if (customer.assignedProducingBranchName.isNotEmpty()) {
                                    IconLine(
                                        Icons.Default.CalendarToday,
                                        "Tu turno",
                                        "${customer.assignedShiftName} · ${customer.assignedProducingBranchName}"
                                    )
                                }
                            }
                        }

                        // Pedidos recientes
                        Text(
                            "Tus pedidos recientes",
                            style = MaterialTheme.typography.titleSmall,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (state.recentOrders.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Receipt, null,
                                        tint = PrimaryBlue.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        "Aún no has hecho ningún pedido",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MutedText
                                    )
                                }
                            }
                        } else {
                            state.recentOrders.forEach { order ->
                                OrderRow(order) { onNavigateToOrderDetail(order.id) }
                            }
                        }
                    }
                }
            }
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
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
            modifier = Modifier.width(120.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = PrimaryBlue,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun OrderRow(order: CustomerOrder, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatDate(order.deliveryDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${order.lineCount} producto(s)" +
                        if (order.totalAmount > 0) " · Q ${formatMoney(order.totalAmount)}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText
                )
            }
            StatusPill(order.status)
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (bg, fg) = when (status) {
        CustomerOrderStatus.PENDING -> Color(0xFFFFE0B2) to Color(0xFFE65100)
        CustomerOrderStatus.CONFIRMED -> Color(0xFFBBDEFB) to Color(0xFF0D47A1)
        CustomerOrderStatus.IN_PRODUCTION -> Color(0xFFE1BEE7) to Color(0xFF4A148C)
        CustomerOrderStatus.READY -> Color(0xFFC8E6C9) to Color(0xFF1B5E20)
        CustomerOrderStatus.DELIVERED -> Color(0xFFB2DFDB) to Color(0xFF004D40)
        CustomerOrderStatus.CANCELLED -> Color(0xFFE0E0E0) to Color(0xFF424242)
        else -> Color(0xFFE0E0E0) to Color(0xFF424242)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            CustomerOrderStatus.label(status),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold
        )
    }
}

internal fun formatDate(millis: Long): String {
    if (millis <= 0L) return "—"
    val dt: LocalDateTime = Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val m = dt.monthNumber.toString().padStart(2, '0')
    val d = dt.dayOfMonth.toString().padStart(2, '0')
    return "$d/$m/${dt.year}"
}

internal fun formatMoney(value: Double): String {
    val rounded = (value * 100).toLong()
    val whole = rounded / 100
    val cents = (rounded % 100).let { if (it < 10) "0$it" else "$it" }
    return "$whole.$cents"
}
