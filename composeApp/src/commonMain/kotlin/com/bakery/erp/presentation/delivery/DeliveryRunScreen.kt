package com.bakery.erp.presentation.delivery

import com.bakery.erp.ui.BrandTopAppBar

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import com.bakery.erp.model.DispatchNote
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.model.Permission
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.util.fmt
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryRunScreen(
    runId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (runId: String) -> Unit,
    onNavigateToNote: (noteId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: DeliveryViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canSeeValue = user?.hasPermission(Permission.VIEW_DISPATCH_VALUE) == true
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(runId, companyId) {
        if (companyId.isNotEmpty()) viewModel.loadRunDetail(companyId, runId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val run = state.selectedRun
    val canManage = user?.hasPermission(Permission.MANAGE_DELIVERY_RUNS) == true
    val isOwnDriver = run?.driverUserId == user?.id

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Hoja de reparto", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (canManage && run != null && run.status == DeliveryRunStatus.PENDING) {
                        IconButton(onClick = { onNavigateToEdit(run.id) }) {
                            Icon(Icons.Default.Edit, "Editar", tint = Color.White)
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = WarmIvory
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isDetailLoading || run == null ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> AdaptiveContainer {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        RunHeader(run)
                        Spacer(Modifier.height(12.dp))

                        // Si hay más de un turno entre las paradas, mostramos el chip por card
                        // para que el repartidor sepa de qué turno viene cada pedido.
                        val distinctShifts = remember(state.notesForSelectedRun) {
                            state.notesForSelectedRun.map { it.shiftName }
                                .filter { it.isNotBlank() }
                                .distinct()
                        }
                        val showShiftChip = distinctShifts.size > 1

                        Text(
                            "Paradas (${state.notesForSelectedRun.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = DeepSeaBlue
                        )
                        Spacer(Modifier.height(6.dp))
                        state.notesForSelectedRun.forEach { note ->
                            StopCard(
                                note = note,
                                canSeeValue = canSeeValue,
                                showShiftChip = showShiftChip,
                                onClick = { onNavigateToNote(note.id) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        // Pan molde, tostado…: lo que se recoge en otra sucursal y lo que se lleva
                        // además de las notas. No es una parada de la hoja, pero va en el mismo camión.
                        if (state.transfersForSelectedRun.isNotEmpty()) {
                            TripTransfersCard(
                                transfers = state.transfersForSelectedRun,
                                carrierPlantId = run.originBranchId,
                                user = user,
                                supervision = user?.role == com.bakery.erp.model.UserRole.OWNER,
                                onOpen = null
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        Spacer(Modifier.height(8.dp))

                        when (run.status) {
                            DeliveryRunStatus.PENDING -> if (isOwnDriver || canManage) {
                                Button(
                                    onClick = { user?.let { viewModel.startRun(companyId, run.id, it) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.isLoading
                                ) {
                                    Icon(Icons.Default.PlayArrow, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Iniciar reparto", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            DeliveryRunStatus.IN_PROGRESS -> if (isOwnDriver || canManage) {
                                val allTerminal = state.notesForSelectedRun.isNotEmpty() &&
                                    state.notesForSelectedRun.all { DispatchStatus.isTerminal(it.status) }
                                Button(
                                    onClick = { user?.let { viewModel.completeRun(companyId, run.id, it) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.isLoading
                                ) {
                                    Icon(Icons.Default.CheckCircle, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (allTerminal) "Terminar reparto" else "Terminar reparto (forzar)",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (!allTerminal) {
                                    Text(
                                        "Aún hay paradas sin confirmar recepción. Al terminar manualmente quedarán igual.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandColors.AccentDeep,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                            else -> {} // COMPLETED / CANCELLED: sin acciones
                        }

                        if (canManage && !DeliveryRunStatus.isTerminal(run.status)) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { user?.let { viewModel.cancelRun(companyId, run.id, it) } },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Cancel, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Cancelar hoja")
                            }
                            Text(
                                "Al cancelar, las notas asignadas vuelven a estado 'Lista para envío'.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
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
private fun RunHeader(run: DeliveryRun) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSeaBlue)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalShipping, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Reparto del ${formatDay(run.plannedDate)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    label = { Text(DeliveryRunStatus.label(run.status), color = Color.White, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(alpha = 0.18f))
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("Desde: ${run.originBranchName}",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
            Text("Repartidor: ${run.driverName.ifEmpty { "—" }}",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
            if (!run.vehicleInfo.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DirectionsCar, null, tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(run.vehicleInfo, style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f))
                }
            }

            // Tiempos: inicio / fin / duración. Útiles para detectar retrasos a simple vista.
            val startedAt = run.startedAt
            val completedAt = run.completedAt
            if (startedAt != null && startedAt > 0L) {
                Spacer(Modifier.height(4.dp))
                val endLabel = if (completedAt != null && completedAt > 0L) formatTime(completedAt) else "en curso"
                val durationLabel = if (completedAt != null && completedAt > 0L)
                    formatDuration(completedAt - startedAt) else null
                Text(
                    buildString {
                        append("Inició ${formatTime(startedAt)} · Terminó $endLabel")
                        if (durationLabel != null) append(" · $durationLabel")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            if (!run.notes.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(run.notes, style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun StopCard(
    note: DispatchNote,
    canSeeValue: Boolean,
    showShiftChip: Boolean,
    onClick: () -> Unit
) {
    val (chipText, chipFg, chipBg) = when (note.status) {
        DispatchStatus.IN_TRANSIT -> Triple("Pendiente entrega", Color(0xFF6A1B9A), Color(0xFFF3E5F5))
        DispatchStatus.ARRIVED    -> Triple("Llegó · falta conteo", Color(0xFF1565C0), Color(0xFFE3F2FD))
        DispatchStatus.DELIVERED  -> Triple("Recibida", Color(0xFF2E7D32), Color(0xFFE8F5E9))
        DispatchStatus.DELIVERED_WITH_DISCREPANCY -> Triple("Recibida (con discrepancia)", Color(0xFFB00020), Color(0xFFFFEBEE))
        DispatchStatus.CANCELLED  -> Triple("Cancelada", Color(0xFF616161), Color(0xFFEEEEEE))
        else -> Triple(DispatchStatus.label(note.status), Color.Gray, Color.LightGray)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storefront, null, tint = DeepSeaBlue, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(note.destinationBranchName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepSeaBlue)
                    val priceTag = if (canSeeValue && note.totalValueWholesale > 0)
                        " · Q${note.totalValueWholesale.fmt(2)}" else ""
                    Text(
                        "${note.lineCount} producto(s)$priceTag",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = onClick,
                    label = { Text(chipText, style = MaterialTheme.typography.labelSmall, color = chipFg, fontWeight = FontWeight.Medium) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = chipBg)
                )
            }

            // Línea complementaria con turno y/o hora de llegada
            val arrivedAt = note.arrivedAt
            if (showShiftChip || (arrivedAt != null && arrivedAt > 0L)) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showShiftChip && note.shiftName.isNotBlank()) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    note.shiftName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DeepSeaBlue,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = DeepSeaBlue.copy(alpha = 0.08f)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (arrivedAt != null && arrivedAt > 0L) {
                        Text(
                            "Llegada: ${formatTime(arrivedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis == 0L) return "—"
    val tz = TimeZone.currentSystemDefault()
    val ld: LocalDateTime = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)
    val hh = ld.hour.toString().padStart(2, '0')
    val mi = ld.minute.toString().padStart(2, '0')
    return "$hh:$mi"
}

private fun formatDuration(millis: Long): String {
    val totalMin = (millis / 60_000L).coerceAtLeast(0L)
    return when {
        totalMin < 60 -> "${totalMin} min"
        else -> {
            val h = totalMin / 60
            val m = totalMin % 60
            if (m == 0L) "${h} h" else "${h} h ${m} min"
        }
    }
}
