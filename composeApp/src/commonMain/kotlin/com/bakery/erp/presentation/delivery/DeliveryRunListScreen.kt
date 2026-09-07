package com.bakery.erp.presentation.delivery

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import com.bakery.erp.model.DeliveryRun
import com.bakery.erp.model.DeliveryRunStatus
import com.bakery.erp.model.Permission
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryRunListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToForm: (runId: String?) -> Unit,
    onNavigateToRun: (runId: String) -> Unit,
    onNavigateToHistory: () -> Unit = {},
    authViewModel: AuthViewModel,
    viewModel: DeliveryViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }
    val canManage = user?.hasPermission(Permission.MANAGE_DELIVERY_RUNS) == true
    val isDriver = user?.hasPermission(Permission.DRIVE_DELIVERY) == true

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) viewModel.observeDay(companyId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val visible = remember(state.runs, user) {
        if (user == null) emptyList()
        else state.runs.filter { run ->
            // Repartidor → solo sus runs. Supervisor con MANAGE → todas las de su sucursal.
            if (run.driverUserId == user.id) true
            else canManage && user.canAccessBranch(run.originBranchId)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reparto", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (canManage) {
                        IconButton(onClick = onNavigateToHistory) {
                            Icon(Icons.Default.History, "Historial", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSeaBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (canManage) {
                FloatingActionButton(
                    onClick = { onNavigateToForm(null) },
                    containerColor = DeepSeaBlue
                ) { Icon(Icons.Default.Add, contentDescription = "Nueva hoja", tint = Color.White) }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = WarmIvory
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DayHeader(
                anchorMillis = state.dayAnchorMillis,
                onPrev = { viewModel.goToPreviousDay(companyId) },
                onNext = { viewModel.goToNextDay(companyId) },
                onResetCenter = { viewModel.resetToToday(companyId) }
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.runs.isEmpty() ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    visible.isEmpty() ->
                        EmptyState(canManage, isDriver, onCreate = { onNavigateToForm(null) })
                    else -> AdaptiveContainer {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(visible, key = { it.id }) { run ->
                                RunCard(run = run, onClick = { onNavigateToRun(run.id) })
                            }
                        }
                    }
                }
            }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }
}

@Composable
private fun DayHeader(
    anchorMillis: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onResetCenter: () -> Unit
) {
    val label = remember(anchorMillis) {
        if (anchorMillis == 0L) "" else formatDay(anchorMillis)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "Día anterior", tint = DeepSeaBlue) }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = DeepSeaBlue,
            modifier = Modifier.clickable { onResetCenter() }
        )
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "Día siguiente", tint = DeepSeaBlue) }
    }
}

@Composable
private fun RunCard(run: DeliveryRun, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalShipping, null, tint = DeepSeaBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        run.originBranchName.ifEmpty { "(sin sucursal)" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepSeaBlue
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            run.driverName.ifEmpty { "(sin repartidor)" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!run.vehicleInfo.isNullOrBlank()) {
                            Text(" · ${run.vehicleInfo}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                StatusChip(run.status)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${run.stopCount} parada(s) · ${formatDay(run.plannedDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (text, tone) = when (status) {
        DeliveryRunStatus.PENDING     -> "Por salir" to ChipTone.ATTENTION
        DeliveryRunStatus.IN_PROGRESS -> "En ruta" to ChipTone.PROGRESS
        DeliveryRunStatus.COMPLETED   -> "Completado" to ChipTone.SUCCESS
        DeliveryRunStatus.CANCELLED   -> "Cancelado" to ChipTone.NEUTRAL
        else -> status to ChipTone.NEUTRAL
    }
    BrandChip(text, tone)
}

@Composable
private fun EmptyState(canManage: Boolean, isDriver: Boolean, onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.LocalShipping, null, modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(10.dp))
        Text("Sin hojas de reparto este día", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                isDriver && !canManage -> "Pídele al supervisor que arme una hoja con tu nombre."
                canManage -> "Toca el botón + para armar una hoja a partir de las notas listas."
                else -> "Necesitas permisos de Repartidor o Supervisor para verlas."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun formatDay(millis: Long): String {
    if (millis == 0L) return "—"
    val tz = TimeZone.currentSystemDefault()
    val ld: LocalDateTime = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)
    val dd = ld.date.dayOfMonth.toString().padStart(2, '0')
    val mm = ld.date.monthNumber.toString().padStart(2, '0')
    return "$dd/$mm/${ld.date.year}"
}
