package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.SpotCashCheck
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.util.fmtMoney
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotCashCheckListScreen(
    onNavigateBack: () -> Unit,
    onStartCheckForSession: (sessionId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: SpotCashCheckViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val user = authState.user
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && user != null) viewModel.loadBranches(companyId, user)
    }
    LaunchedEffect(state.selectedBranchId) {
        if (companyId.isNotEmpty() && state.selectedBranchId.isNotEmpty()) {
            viewModel.loadRecentForBranch(companyId, state.selectedBranchId)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cuadre sorpresa", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSeaBlue,
                    titleContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = WarmIvory
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (state.branches.isEmpty() && state.isLoading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@Column
                }
                if (state.branches.isEmpty()) {
                    Text("No tienes acceso a sucursales con capacidad de venta.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    return@Column
                }
                if (state.branches.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.branches.forEach { b ->
                            FilterChip(
                                selected = b.id == state.selectedBranchId,
                                onClick = { viewModel.selectBranch(companyId, b.id) },
                                label = { Text(b.name) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Text("Cajas abiertas en esta sucursal",
                    fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
                Spacer(Modifier.height(6.dp))
                if (state.openSessions.isEmpty()) {
                    Text("No hay cajas abiertas en este momento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.openSessions.forEach { s ->
                        OpenSessionRow(s) { onStartCheckForSession(s.id) }
                        Spacer(Modifier.height(6.dp))
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                Text("Cuadres recientes (últimos 7 días)",
                    fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
                Spacer(Modifier.height(6.dp))
                if (state.recentChecksForBranch.isEmpty()) {
                    Text("Sin cuadres en el período.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.recentChecksForBranch.forEach { check ->
                            CheckRow(check)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenSessionRow(s: CashSession, onCheck: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.registerName, fontWeight = FontWeight.SemiBold)
                Text("Cajero: ${s.cashierName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Ventas: Q ${s.totalSales.fmtMoney(2)} · # ${s.saleCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
            Button(onClick = onCheck) {
                Icon(Icons.Default.Visibility, null)
                Spacer(Modifier.width(6.dp))
                Text("Cuadrar")
            }
        }
    }
}

@Composable
private fun CheckRow(check: SpotCashCheck) {
    val diffColor = when {
        check.difference > 0.01 -> Color(0xFF1B5E20)
        check.difference < -0.01 -> Color(0xFFB00020)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${check.registerName} · ${check.cashierName}",
                    fontWeight = FontWeight.SemiBold)
                Text("Q ${check.difference.fmtMoney(2)}",
                    fontWeight = FontWeight.Bold, color = diffColor)
            }
            Text("Por ${check.checkedByUserName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline)
            if (check.notes.isNotBlank()) {
                Text(check.notes, style = MaterialTheme.typography.bodySmall)
            }
            AssistChip(
                onClick = {},
                label = { Text(com.bakery.erp.model.SpotCashCheckResolution.label(check.resolution)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = when (check.resolution) {
                        com.bakery.erp.model.SpotCashCheckResolution.FORCE_ADJUST ->
                            Color(0xFFE3F2FD)
                        com.bakery.erp.model.SpotCashCheckResolution.REQUIRES_REVIEW ->
                            Color(0xFFFFEBEE)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            )
        }
    }
}
