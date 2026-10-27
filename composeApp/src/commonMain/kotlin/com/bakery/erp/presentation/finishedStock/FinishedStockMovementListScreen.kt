package com.bakery.erp.presentation.finishedStock

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import com.bakery.erp.model.FinishedMovementType
import com.bakery.erp.model.FinishedStockMovement
import com.bakery.erp.model.Permission
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.util.fmt
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishedStockMovementListScreen(
    branchId: String,
    productId: String,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: FinishedStockViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canSeeValue = user?.hasPermission(Permission.VIEW_DISPATCH_VALUE) == true
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId, branchId, productId) {
        if (companyId.isNotEmpty()) viewModel.loadMovements(companyId, branchId, productId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val productName = remember(state.movements, state.stocks, productId) {
        state.stocks.firstOrNull { it.productId == productId && it.branchId == branchId }?.productName
            ?: state.movements.firstOrNull { it.productId == productId }?.productName
            ?: "(Producto)"
    }
    val currentStock = remember(state.stocks, productId, branchId) {
        state.stocks.firstOrNull { it.productId == productId && it.branchId == branchId }
    }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Kardex", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSeaBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = WarmIvory
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AdaptiveContainer {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {

                    // Header con producto + stock actual
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DeepSeaBlue)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(productName, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text(
                                "Stock actual: ${currentStock?.currentStock?.fmt(0) ?: "—"}" +
                                    if (canSeeValue && currentStock != null && currentStock.avgUnitCost > 0)
                                        " · costo Q${currentStock.avgUnitCost.fmt(2)} c/u" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    when {
                        state.isMovementsLoading ->
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        state.movements.isEmpty() ->
                            EmptyKardex()
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.movements, key = { it.id }) { m ->
                                MovementCard(movement = m, canSeeValue = canSeeValue)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovementCard(movement: FinishedStockMovement, canSeeValue: Boolean) {
    val isIngress = movement.quantity > 0
    val (icon, color, bg) = when (movement.type) {
        FinishedMovementType.RECEPCION     -> Triple(Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF2E7D32), Color(0xFFE8F5E9))
        FinishedMovementType.VENTA         -> Triple(Icons.AutoMirrored.Filled.TrendingDown, Color(0xFF1565C0), Color(0xFFE3F2FD))
        FinishedMovementType.MERMA         -> Triple(Icons.AutoMirrored.Filled.TrendingDown, Color(0xFFB00020), Color(0xFFFFEBEE))
        FinishedMovementType.AJUSTE        ->
            if (isIngress) Triple(Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF6A1B9A), Color(0xFFF3E5F5))
            else Triple(Icons.AutoMirrored.Filled.TrendingDown, Color(0xFF6A1B9A), Color(0xFFF3E5F5))
        FinishedMovementType.TRANSFERENCIA -> Triple(Icons.AutoMirrored.Filled.TrendingDown, Color(0xFFB45309), Color(0xFFFFF7E6))
        FinishedMovementType.ACCIDENTE     -> Triple(Icons.AutoMirrored.Filled.TrendingDown, BrandColors.Danger, BrandColors.DangerSoft)
        FinishedMovementType.CORTESIA      -> Triple(Icons.AutoMirrored.Filled.TrendingDown, BrandColors.Primary, BrandColors.PrimaryContainer)
        // Mismo color en las dos puntas: es una sola acción (el entero baja, la porción sube).
        FinishedMovementType.PORCIONADO, FinishedMovementType.TRANSFORMACION ->
            if (isIngress) Triple(Icons.AutoMirrored.Filled.TrendingUp, BrandColors.Teal, BrandColors.TealSoft)
            else Triple(Icons.AutoMirrored.Filled.TrendingDown, BrandColors.Teal, BrandColors.TealSoft)
        else -> Triple(Icons.AutoMirrored.Filled.TrendingUp, Color.Gray, Color.LightGray)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        FinishedMovementType.label(movement.type),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                "${if (isIngress) "+" else ""}${movement.quantity.fmt(0)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(containerColor = bg)
                    )
                }
                Text(
                    formatDateTime(movement.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (movement.createdByUserName.isNotEmpty()) {
                    Text(
                        "Por ${movement.createdByUserName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!movement.reason.isNullOrBlank()) {
                    Text(
                        movement.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (canSeeValue && movement.unitPriceAtMovement > 0) {
                    Text(
                        "Q${movement.unitPriceAtMovement.fmt(2)} c/u",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyKardex() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.History, null, modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(10.dp))
        Text("Sin movimientos", style = MaterialTheme.typography.titleSmall)
        Text(
            "Aparecerán cuando se reciba un despacho o registres mermas y ajustes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDateTime(millis: Long): String {
    if (millis == 0L) return "—"
    val tz = TimeZone.currentSystemDefault()
    val ld: LocalDateTime = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)
    val dd = ld.date.dayOfMonth.toString().padStart(2, '0')
    val mm = ld.date.monthNumber.toString().padStart(2, '0')
    val hh = ld.hour.toString().padStart(2, '0')
    val mi = ld.minute.toString().padStart(2, '0')
    return "$dd/$mm/${ld.date.year} $hh:$mi"
}
