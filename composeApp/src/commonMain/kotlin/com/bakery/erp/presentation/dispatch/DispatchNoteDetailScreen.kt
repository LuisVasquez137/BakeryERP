package com.bakery.erp.presentation.dispatch

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.DiscrepancyType
import com.bakery.erp.model.DispatchNoteLine
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.model.Permission
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.production.ProductionViewModel
import com.bakery.erp.presentation.production.RecipeRef
import com.bakery.erp.presentation.production.groupByMasa
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
fun DispatchNoteDetailScreen(
    noteId: String,
    onNavigateBack: () -> Unit,
    onNavigateToPack: (noteId: String) -> Unit = {},
    onNavigateToReceive: (noteId: String) -> Unit = {},
    authViewModel: AuthViewModel,
    viewModel: DispatchViewModel,
    productionViewModel: ProductionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val productionState by productionViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canSeeValue = user?.hasPermission(Permission.VIEW_DISPATCH_VALUE) == true
    val snackbarHostState = remember { SnackbarHostState() }
    var showCancelDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
    var cancelReason by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(noteId, companyId) {
        if (companyId.isNotEmpty()) viewModel.loadNoteDetail(companyId, noteId)
    }
    // Mapa producto → masa, para agrupar igual que la hoja de trabajo del panadero.
    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) productionViewModel.loadRecipeRefByProductIdIfNeeded(companyId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.lastReceptionSummary) {
        state.lastReceptionSummary?.let {
            snackbarHostState.showSnackbar(it); viewModel.clearLastReception()
        }
    }

    val note = state.selectedNote

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Detalle del despacho", fontWeight = FontWeight.SemiBold) },
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
            when {
                state.isDetailLoading || note == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> AdaptiveContainer {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        HeaderCard(note, canSeeValue)
                        Spacer(Modifier.height(12.dp))
                        Timeline(note)
                        Spacer(Modifier.height(12.dp))
                        LinesSection(note.lines, productionState.recipeRefByProductId, canSeeValue)
                        Spacer(Modifier.height(12.dp))

                        val isPlantStaff = user != null &&
                            user.canAccessBranch(note.originBranchId) &&
                            user.hasPermission(Permission.DISPATCH_PACK)
                        val canReceive = user != null &&
                            user.canAccessBranch(note.destinationBranchId) &&
                            user.hasPermission(Permission.RECEIVE_DISPATCH)

                        // DRAFT → ir a Empacar
                        if (note.status == DispatchStatus.DRAFT && isPlantStaff) {
                            Button(
                                onClick = { onNavigateToPack(note.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Inventory, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Empacar este despacho", fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        // IN_TRANSIT → ir a Recibir (paso 1: checklist de llegada)
                        // ARRIVED  → ir a Recibir (paso 2: conteo final pendiente)
                        if ((note.status == DispatchStatus.IN_TRANSIT ||
                                note.status == DispatchStatus.ARRIVED) && canReceive) {
                            Button(
                                onClick = { onNavigateToReceive(note.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Inventory, null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (note.status == DispatchStatus.ARRIVED) "Cerrar conteo pendiente"
                                    else "Recibir y verificar",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (note.status == DispatchStatus.READY && isPlantStaff) {
                            val isSelfDispatch = note.originBranchId == note.destinationBranchId
                            if (isSelfDispatch) {
                                // Atajo: planta = sucursal solicitante.
                                Button(
                                    onClick = { user?.let { viewModel.markAsLocalDispatch(companyId, note.id, it) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.isLoading
                                ) {
                                    Icon(Icons.Default.Inventory, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Marcar como recibida en mostrador", fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Como la sucursal solicitante es la misma planta, no requiere reparto. Esto suma al inventario de tienda directamente.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Button(
                                    onClick = { user?.let { viewModel.markInTransit(companyId, note.id, it) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.isLoading
                                ) {
                                    Icon(Icons.Default.Send, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Marcar como enviada", fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Mejor pásala por una Hoja de Reparto para registrar repartidor y vehículo. Este botón es un atajo.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if ((note.status == DispatchStatus.DRAFT || note.status == DispatchStatus.READY) && isPlantStaff) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showCancelDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.isLoading,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Cancel, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Cancelar nota")
                            }
                        }

                        // Cancelada: se puede deshacer. El botón vive acá y no en la
                        // lista porque restaurar mueve inventario, y quien lo hace
                        // tiene que estar viendo qué pan y cuánto.
                        if (note.status == DispatchStatus.CANCELLED && isPlantStaff) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { showRestoreDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.isLoading
                            ) {
                                Icon(Icons.Default.Restore, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Restaurar nota", fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (note.plantAbsorbedAtCancel)
                                    "Vuelve a ${restoreTargetLabel(note)} y descuenta del inventario de " +
                                        "${note.originBranchName} el pan que la cancelación le sumó."
                                else
                                    "Vuelve a ${restoreTargetLabel(note)}. Esta nota se canceló sin mover " +
                                        "inventario, así que no hay nada que descontar.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCancelDialog && note != null) {
        val units = note.lines.sumOf { it.packedQuantity }
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            icon = { Icon(Icons.Default.Cancel, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("¿Cancelar esta nota?") },
            text = {
                Column {
                    Text(
                        "La nota NO se borra: pasa a la pestaña \"Canceladas\" y desde ahí " +
                            "se puede restaurar."
                    )
                    if (units > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Se sumarán ${units.fmt(0)} unidades al inventario de " +
                                "${note.originBranchName}: ese pan ya está horneado y se queda ahí.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.AccentDeep
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = { Text("Motivo (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    user?.let { viewModel.cancelNote(companyId, note.id, it, cancelReason) }
                    cancelReason = ""
                }) {
                    Text("Cancelar nota", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Volver") }
            }
        )
    }

    if (showRestoreDialog && note != null) {
        val units = note.lines.sumOf { it.packedQuantity }
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            icon = { Icon(Icons.Default.Restore, null, tint = DeepSeaBlue) },
            title = { Text("¿Restaurar esta nota?") },
            text = {
                Text(
                    buildString {
                        append("Vuelve a ${restoreTargetLabel(note)}.")
                        if (note.plantAbsorbedAtCancel && units > 0) {
                            append(" Se descontarán ${units.fmt(0)} unidades del inventario de ")
                            append("${note.originBranchName}, porque ese pan vuelve a estar comprometido ")
                            append("con ${note.destinationBranchName}.")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreDialog = false
                    user?.let { viewModel.restoreNote(companyId, note.id, it) }
                }) { Text("Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Volver") }
            }
        )
    }
}

/** A dónde vuelve una nota restaurada, en las palabras de la pestaña que la recibe. */
private fun restoreTargetLabel(note: com.bakery.erp.model.DispatchNote): String =
    when (note.statusBeforeCancel) {
        DispatchStatus.READY -> "\"Listas\" para envío"
        else                 -> "\"Por empacar\""
    }

@Composable
private fun HeaderCard(note: com.bakery.erp.model.DispatchNote, canSeeValue: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSeaBlue)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalShipping, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${note.originBranchName} → ${note.destinationBranchName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    label = { Text(DispatchStatus.label(note.status), color = Color.White, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(alpha = 0.18f))
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Turno: ${note.shiftName.ifEmpty { "—" }} · Producción del ${formatDay(note.productionDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
            if (canSeeValue && note.priceListName.isNotEmpty()) {
                Text(
                    "Lista de precios: ${note.priceListName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            if (canSeeValue && note.totalValueWholesale > 0) {
                Text(
                    "Total: Q${note.totalValueWholesale.fmt(2)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun Timeline(note: com.bakery.erp.model.DispatchNote) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Historia", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
            Spacer(Modifier.height(6.dp))
            TimelineRow("Creada", note.createdAt, "—")
            note.packedAt?.let { TimelineRow("Empacada", it, note.packedByUserName) }
            note.dispatchedAt?.let { TimelineRow("En tránsito", it, "—") }
            note.arrivedAt?.let { TimelineRow("Llegó", it, note.arrivedByUserName) }
            note.receivedAt?.let { TimelineRow("Recibida", it, note.receivedByUserName) }
            note.cancelledAt?.let { TimelineRow("Cancelada", it, note.cancelledByUserName) }
            note.cancelReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(
                    "Motivo: $reason",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(label: String, millis: Long, by: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(110.dp))
        Text(formatDateTime(millis), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (by.isNotEmpty() && by != "—") Text(by, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LinesSection(
    lines: List<DispatchNoteLine>,
    recipeRefByProductId: Map<String, RecipeRef>,
    canSeeValue: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Productos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
            Spacer(Modifier.height(6.dp))
            // Header
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("Producto", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.6f))
                Text("Pedido", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f))
                Text("Empac.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f))
                Text("Recib.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f))
                if (canSeeValue) {
                    Text("Q c/u", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.8f))
                }
            }
            HorizontalDivider()
            val groupedLines = remember(lines, recipeRefByProductId) {
                groupByMasa(
                    items = lines,
                    recipeIdOf = { dispatchLineRecipeId(it, recipeRefByProductId) },
                    recipeNameOf = { dispatchLineRecipeName(it, recipeRefByProductId) },
                    nameOf = { it.productName }
                )
            }
            groupedLines.forEach { group ->
                Text(
                    group.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = DeepSeaBlue,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
                group.items.forEach { line ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1.6f)) {
                            Text(
                                dispatchLineLabel(line.productName, line.isSpecial, line.specialRawWeightGrams),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            val r1 = line.packedReason
                            val r2 = line.receivedReason
                            if (!r1.isNullOrBlank()) {
                                Text("Pack: $r1", style = MaterialTheme.typography.labelSmall,
                                    color = BrandColors.AccentDeep)
                            }
                            if (!r2.isNullOrBlank() || line.discrepancyType != null) {
                                val tag = line.discrepancyType?.let { "[${DiscrepancyType.label(it)}] " } ?: ""
                                Text("Recep: $tag${r2.orEmpty()}", style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB00020))
                            }
                        }
                        Text(line.requestedQuantity.fmt(0), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.7f))
                        Text(line.packedQuantity.fmt(0), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.7f))
                        Text(
                            if (line.receivedQuantity > 0) line.receivedQuantity.fmt(0) else "—",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.7f)
                        )
                        if (canSeeValue) {
                            Text(
                                if (line.unitWholesalePrice > 0) "Q${line.unitWholesalePrice.fmt(2)}" else "—",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.8f)
                            )
                        }
                    }
                }
            }
        }
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
