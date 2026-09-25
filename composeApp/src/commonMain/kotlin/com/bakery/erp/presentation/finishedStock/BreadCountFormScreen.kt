package com.bakery.erp.presentation.finishedStock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.BreadCountLine
import com.bakery.erp.model.BreadCountReason
import com.bakery.erp.model.BreadCountStatus
import com.bakery.erp.model.GroupAllocation
import com.bakery.erp.model.GroupSalesWindow
import com.bakery.erp.model.allocateGroupSales
import com.bakery.erp.model.shortage
import androidx.compose.material.icons.filled.Refresh
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreadCountFormScreen(
    countId: String,
    onNavigateBack: () -> Unit,
    onClosed: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: BreadCountViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val user = authState.user
    val snackbar = remember { SnackbarHostState() }
    var notes by rememberSaveable { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(companyId, countId) {
        if (companyId.isNotEmpty()) viewModel.observeCount(companyId, countId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.isClosed) {
        if (state.isClosed) { viewModel.clearClosed(); onClosed() }
    }

    val header = state.currentCount
    val lines = state.currentLines
    val isClosed = header?.status == BreadCountStatus.CLOSED
    val window = state.groupWindow
    val groupOfProduct = window?.groupOfProduct.orEmpty()
    // Del faltante de un pan del grupo, lo primero que se descuenta es lo vendido como grupo;
    // solo el resto es merma. Cerrado, manda lo que quedó escrito en la línea.
    val allocation = remember(lines, window) {
        if (window == null) GroupAllocation()
        else allocateGroupSales(window.unitsByGroup, window.groupProductIds, lines)
    }
    fun soldOf(line: BreadCountLine) =
        if (isClosed) line.soldAsGroup else allocation.soldOf(line.productId)
    fun wasteOf(line: BreadCountLine) = (line.shortage - soldOf(line)).coerceAtLeast(0.0)
    val totalShortage = lines.sumOf { wasteOf(it) * it.unitCostSnapshot }
    val totalSurplus = lines.filter { it.difference > 0 }
        .sumOf { it.difference * it.unitCostSnapshot }
    // La razón se pide solo sobre la merma: el pan que se vendió como grupo no se perdió.
    val missingReasons = lines.filter { wasteOf(it) > 0 && it.reason.isBlank() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conteo de pan", fontWeight = FontWeight.SemiBold) },
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
        if (header == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().imePadding().padding(16.dp)) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepSeaBlue)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(header.branchName, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Fecha: ${header.countDate} · Por ${header.countedByUserName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f))
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (isClosed) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.12f))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Conteo cerrado el ${formatTimestamp(header.closedAt)}",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1B5E20))
                            Text("Las mermas y ajustes ya se aplicaron al stock.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    KpiBox(label = "Mermas (valor)",
                        value = "Q ${totalShortage.fmtMoney(2)}",
                        color = if (totalShortage > 0) Color(0xFFB00020) else MaterialTheme.colorScheme.onSurface)
                    KpiBox(label = "Sobrante (valor)",
                        value = "Q ${totalSurplus.fmtMoney(2)}",
                        color = if (totalSurplus > 0) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface)
                }
                if (window != null && window.groupProductIds.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    GroupSalesCard(
                        window = window,
                        allocation = allocation,
                        isClosed = isClosed,
                        closedUnexplained = header.unexplainedGroupUnits,
                        isRefreshing = state.isGroupWindowLoading,
                        onRefresh = { viewModel.refreshGroupSales(companyId, countId) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(lines, key = { it.id }) { line ->
                        LineRow(
                            line = line,
                            groupName = groupOfProduct[line.productId]?.let { window?.groupNames?.get(it) },
                            soldAsGroup = soldOf(line),
                            readOnly = isClosed,
                            onChange = { updated -> viewModel.updateLine(companyId, countId, updated) }
                        )
                    }
                }

                if (!isClosed) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notas (opcional)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showConfirm = true },
                        enabled = !state.isLoading && missingReasons.isEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (window?.hasGroupSales == true) "Cerrar conteo y repartir ventas"
                            else "Cerrar conteo y aplicar mermas",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (missingReasons.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Falta razón en ${missingReasons.size} producto(s).",
                            color = Color(0xFFB00020),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (showConfirm && user != null && header != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("¿Cerrar conteo?") },
            text = {
                Text(
                    (if (allocation.totalAllocated > 0)
                        "Se registrarán como venta ${allocation.totalAllocated.fmt(0)} panes vendidos por grupo, " +
                            "repartidos según lo que falta de cada uno. " else "") +
                    "Se aplicarán las mermas (Q ${totalShortage.fmtMoney(2)}) y " +
                    "los sobrantes (Q ${totalSurplus.fmtMoney(2)}) al stock terminado. " +
                    "Las ventas de grupo se vuelven a leer al cerrar. El conteo quedará inmutable."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.closeCount(companyId, countId, notes.trim().ifEmpty { null }, user)
                }) { Text("Sí, cerrar") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun KpiBox(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline)
        Text(value, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun GroupSalesCard(
    window: GroupSalesWindow,
    allocation: GroupAllocation,
    isClosed: Boolean,
    closedUnexplained: Double,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = BrandColors.PrimaryContainer)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vendido por grupo de precio", fontWeight = FontWeight.SemiBold,
                        color = BrandColors.Primary)
                    Text("Desde ${formatTimestamp(window.fromMillis)}" +
                            if (isClosed) " hasta ${formatTimestamp(window.untilMillis)}" else " hasta ahora",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!isClosed) {
                    if (isRefreshing) CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, "Actualizar ventas", tint = BrandColors.Primary)
                    }
                }
            }
            window.groupProductIds.keys.forEach { gid ->
                val sold = allocation.soldByGroup[gid] ?: window.unitsByGroup[gid] ?: 0.0
                val missing = allocation.shortageByGroup[gid] ?: 0.0
                val waste = (missing - sold).coerceAtLeast(0.0)
                Text(
                    "${window.groupNames[gid].orEmpty()}: vendidos ${sold.fmt(0)} · " +
                        "faltan ${missing.fmt(0)} en el conteo" +
                        if (waste > 0) " → ${waste.fmt(0)} de merma" else "",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val unexplained = if (isClosed) closedUnexplained else allocation.totalUnexplained
            if (unexplained > 0) {
                Text(
                    "Se vendieron ${unexplained.fmt(0)} panes de grupo más de los que faltan. " +
                        "Revisá el conteo, o si entró pan sin registrar su recepción.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandColors.AccentDeep
                )
            }
            if (!isClosed) {
                Text(
                    "Contá solo el pan que está en el estante: lo vendido se descuenta solo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LineRow(
    line: BreadCountLine,
    groupName: String?,
    soldAsGroup: Double,
    readOnly: Boolean,
    onChange: (BreadCountLine) -> Unit
) {
    var countedText by rememberSaveable(line.id) {
        mutableStateOf(if (line.countedStock == 0.0) "" else line.countedStock.fmt(0))
    }
    val parsed = countedText.toDoubleOrNull() ?: 0.0
    val diff = parsed - line.expectedStock
    // Solo lo que no explica la venta de grupo es merma y pide razón.
    val waste = (-diff - soldAsGroup).coerceAtLeast(0.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(line.productName, modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold)
                Text("Esperado: ${line.expectedStock.fmt(0)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = countedText,
                    onValueChange = {
                        if (readOnly) return@OutlinedTextField
                        countedText = it.filter { c -> c.isDigit() || c == '.' }
                        val newParsed = countedText.toDoubleOrNull() ?: 0.0
                        onChange(line.copy(countedStock = newParsed))
                    },
                    label = { Text("Contado") },
                    singleLine = true,
                    readOnly = readOnly,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.widthIn(min = 110.dp, max = 140.dp)
                )
                Spacer(Modifier.width(12.dp))
                val diffColor = when {
                    diff < 0 -> Color(0xFFB00020)
                    diff > 0 -> Color(0xFF1B5E20)
                    else     -> MaterialTheme.colorScheme.onSurface
                }
                Column {
                    Text("Diferencia", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    Text(diff.fmt(0), fontWeight = FontWeight.SemiBold, color = diffColor)
                }
                if (groupName != null && diff < 0) {
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Vendido ($groupName)", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                        Text("${soldAsGroup.coerceAtMost(-diff).fmt(0)} · merma ${waste.fmt(0)}",
                            fontWeight = FontWeight.SemiBold,
                            color = if (waste > 0) Color(0xFFB00020) else BrandColors.Primary)
                    }
                }
            }
            if (waste > 0) {
                Spacer(Modifier.height(6.dp))
                ReasonPicker(
                    selected = line.reason,
                    onSelect = { reason -> onChange(line.copy(reason = reason)) },
                    enabled = !readOnly
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = line.reasonNote,
                    onValueChange = { onChange(line.copy(reasonNote = it)) },
                    label = { Text("Nota (opcional)") },
                    singleLine = true,
                    readOnly = readOnly,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun formatTimestamp(millis: Long?): String {
    if (millis == null || millis == 0L) return ""
    val dt = Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val d  = dt.dayOfMonth.toString().padStart(2, '0')
    val m  = dt.monthNumber.toString().padStart(2, '0')
    val y  = dt.year.toString().padStart(4, '0')
    val h  = dt.hour.toString().padStart(2, '0')
    val mi = dt.minute.toString().padStart(2, '0')
    return "$d/$m/$y $h:$mi"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReasonPicker(selected: String, onSelect: (String) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = if (selected.isBlank()) "" else BreadCountReason.label(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text("Razón *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = selected.isBlank(),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BreadCountReason.SHORTAGE_REASONS.forEach { code ->
                DropdownMenuItem(
                    text = { Text(BreadCountReason.label(code)) },
                    onClick = { onSelect(code); expanded = false }
                )
            }
        }
    }
}
