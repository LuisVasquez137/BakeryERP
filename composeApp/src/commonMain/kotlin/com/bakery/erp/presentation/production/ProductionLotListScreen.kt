package com.bakery.erp.presentation.production

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Permission
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionLotListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (lotId: String) -> Unit,
    onNavigateToForm: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canManage = user?.hasPermission(Permission.MANAGE_PRODUCTION_LOTS) == true
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && user != null) viewModel.loadLots(companyId, user)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val filteredLots = remember(state.lots, state.lotDateFilter) {
        val filter = state.lotDateFilter
        if (filter == null) state.lots else state.lots.filter { it.productionDate == filter }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Lotes de Producción", fontWeight = FontWeight.SemiBold) },
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
        floatingActionButton = {
            if (canManage) {
                FloatingActionButton(
                    onClick = onNavigateToForm,
                    containerColor = Color(0xFF123456)
                ) { Icon(Icons.Default.Add, "Nuevo lote", tint = Color.White) }
            }
        },
        containerColor = Color(0xFFFDFBF7)
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredLots.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Factory, null, Modifier.size(48.dp), tint = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Text("Sin lotes de producción", color = Color.Gray)
                    if (canManage) {
                        Spacer(Modifier.height(4.dp))
                        Text("Crea un nuevo lote para comenzar", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredLots) { lot ->
                    ProductionLotCard(lot = lot, onClick = { onNavigateToDetail(lot.id) })
                }
            }
        }
    }
}

@Composable
fun ProductionLotCard(lot: ProductionLot, onClick: () -> Unit) {
    val (statusColor, statusBg) = when (lot.status) {
        ProductionLotStatus.OPEN        -> Color(0xFF1565C0) to Color(0xFFE3F2FD)
        ProductionLotStatus.IN_PROGRESS -> Color(0xFFF57C00) to Color(0xFFFFF3E0)
        ProductionLotStatus.COMPLETED   -> Color(0xFF2E7D32) to Color(0xFFE8F5E9)
        ProductionLotStatus.CANCELLED   -> Color.Gray to Color(0xFFF5F5F5)
        else                            -> Color.Gray to Color(0xFFF5F5F5)
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(lot.producingBranchName, fontWeight = FontWeight.SemiBold, color = Color(0xFF123456))
                Text(
                    "${lot.shiftName} — ${formatProductionDate(lot.productionDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    "${lot.lineCount} producto(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Surface(shape = RoundedCornerShape(20.dp), color = statusBg) {
                Text(
                    ProductionLotStatus.label(lot.status),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
