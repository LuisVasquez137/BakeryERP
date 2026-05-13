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
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionLotDetailScreen(
    lotId: String,
    onNavigateBack: () -> Unit,
    onNavigateToComplete: (lotId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canManage = user?.hasPermission(Permission.MANAGE_PRODUCTION_LOTS) == true
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(lotId) {
        if (companyId.isNotEmpty()) viewModel.loadLotDetail(companyId, lotId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val lot = state.selectedLot
    val lines = state.selectedLotLines

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Lista de Producción", fontWeight = FontWeight.SemiBold) },
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
        if (state.isDetailLoading || lot == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Encabezado del lote ──────────────────────────────────────
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(lot.producingBranchName, fontWeight = FontWeight.Bold, color = Color(0xFF123456), style = MaterialTheme.typography.titleMedium)
                                    Text(lot.shiftName, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                    Text(formatProductionDate(lot.productionDate), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                LotStatusChip(lot.status)
                            }
                            if (!lot.notes.isNullOrBlank()) {
                                HorizontalDivider()
                                Text(lot.notes, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            if (!lot.stockDeducted && lot.status == ProductionLotStatus.COMPLETED) {
                                HorizontalDivider()
                                Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        "⚠ Stock pendiente de descontar",
                                        modifier = Modifier.padding(8.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFF57C00),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Lista de productos ───────────────────────────────────────
                item {
                    Text(
                        "Productos a producir (${lines.size})",
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
                                if (line.recipeId.isNotEmpty())
                                    Text("Receta: ${line.recipeName}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${line.plannedQuantity.toInt()}",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFCC5500),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text("planeadas", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                if (lot.status == ProductionLotStatus.COMPLETED && line.producedQuantity != line.plannedQuantity) {
                                    Text("${line.producedQuantity.toInt()} reales", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                                }
                            }
                        }
                    }
                }

                // ── Acciones ─────────────────────────────────────────────────
                if (canManage) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        when (lot.status) {
                            ProductionLotStatus.OPEN -> {
                                Button(
                                    onClick = { viewModel.startLot(companyId, lot.id) },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                                ) { Text("Iniciar producción", fontWeight = FontWeight.Bold) }
                            }
                            ProductionLotStatus.IN_PROGRESS -> {
                                Button(
                                    onClick = { onNavigateToComplete(lot.id) },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                ) { Text("Completar lote y descontar stock", fontWeight = FontWeight.Bold) }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LotStatusChip(status: String) {
    val (color, bg) = when (status) {
        ProductionLotStatus.OPEN        -> Color(0xFF1565C0) to Color(0xFFE3F2FD)
        ProductionLotStatus.IN_PROGRESS -> Color(0xFFF57C00) to Color(0xFFFFF3E0)
        ProductionLotStatus.COMPLETED   -> Color(0xFF2E7D32) to Color(0xFFE8F5E9)
        else                            -> Color.Gray to Color(0xFFF5F5F5)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(
            ProductionLotStatus.label(status),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
