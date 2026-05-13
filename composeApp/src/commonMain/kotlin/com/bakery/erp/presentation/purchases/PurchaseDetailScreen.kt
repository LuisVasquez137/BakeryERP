package com.bakery.erp.presentation.purchases

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseStatus
import com.bakery.erp.presentation.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDetailScreen(
    purchaseId: String,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: PurchaseViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""

    LaunchedEffect(purchaseId, companyId) {
        if (companyId.isNotEmpty()) viewModel.loadPurchaseDetail(companyId, purchaseId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Compra", fontWeight = FontWeight.SemiBold) },
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
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isDetailLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.selectedPurchase != null -> PurchaseDetailContent(
                    purchase = state.selectedPurchase!!,
                    lines = state.selectedPurchaseLines,
                    onStatusChange = { newStatus ->
                        viewModel.updatePurchaseStatus(companyId, state.selectedPurchase!!.id, newStatus)
                    }
                )
                else -> Text(
                    "No se encontró la compra",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
internal fun PurchaseDetailContent(
    purchase: Purchase,
    lines: List<PurchaseLine>,
    onStatusChange: ((String) -> Unit)? = null
) {
    val hasPackageCost = lines.any { it.packageCost > 0 }
    val total = if (hasPackageCost) lines.sumOf { it.packageCost * it.packagesReceived.coerceAtLeast(1.0) } else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header chips: factura + sucursal ─────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!purchase.invoiceNumber.isNullOrEmpty()) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        text = "Factura: ${purchase.invoiceNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = purchase.branchName.ifEmpty { "Sin sucursal" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // ── Estado (con selector de cambio) ──────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ESTADO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        PurchaseStatus.PENDING_RECEIPT to "Pendiente",
                        PurchaseStatus.RECEIVED_PENDING_PAYMENT to "Pte. pago",
                        PurchaseStatus.PAID to "Pagado"
                    ).forEach { (status, label) ->
                        val isSelected = purchase.status == status
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onStatusChange?.invoke(status) },
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // ── Info general ─────────────────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailRow("Proveedor", purchase.supplierName.ifEmpty { "Sin proveedor" })
                DetailRow(
                    "Recibido por",
                    purchase.receivedByName.ifEmpty { "No registrado" }
                )
                DetailRow("Fecha", formatPurchaseDate(purchase.date))
                if (!purchase.notes.isNullOrEmpty()) {
                    DetailRow("Observaciones", purchase.notes)
                }
            }
        }

        // ── Líneas de insumos ────────────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INSUMOS RECIBIDOS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            text = "${lines.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                lines.forEachIndexed { index, line ->
                    PurchaseLineRow(line)
                    if (index < lines.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }

        // ── Total ────────────────────────────────────────────────────────────
        if (hasPackageCost && total > 0) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TOTAL", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Q ${"%.2f".format(total)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(110.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PurchaseLineRow(line: PurchaseLine) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(line.ingredientName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (line.packageDescription.isNotEmpty()) {
            Text(line.packageDescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            val qtyText = if (line.packagesReceived > 0 && line.packageDescription.isNotEmpty()) {
                "${formatPurchaseQty(line.packagesReceived)} × ${line.packageDescription}"
            } else {
                "${formatPurchaseQty(line.quantityReceived)} ${line.ingredientUnit}"
            }
            Text(qtyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (line.packageCost > 0) {
                val lineTotal = line.packageCost * line.packagesReceived.coerceAtLeast(1.0)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Q ${"%.2f".format(line.packageCost)} / u.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "Q ${"%.2f".format(lineTotal)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

internal fun formatPurchaseQty(value: Double): String =
    if (value == value.toLong().toDouble()) "${value.toLong()}" else "%.2f".format(value)
