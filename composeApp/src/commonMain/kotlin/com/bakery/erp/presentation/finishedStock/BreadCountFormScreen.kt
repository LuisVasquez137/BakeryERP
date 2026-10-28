package com.bakery.erp.presentation.finishedStock

import com.bakery.erp.ui.BrandTopAppBar

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.BreadCountLine
import com.bakery.erp.model.Permission
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.BreadCountReason
import com.bakery.erp.model.BreadCountStatus
import com.bakery.erp.model.GroupAllocation
import com.bakery.erp.model.GroupSalesWindow
import com.bakery.erp.model.allocateGroupSales
import com.bakery.erp.model.shortage
import androidx.compose.material.icons.filled.Refresh
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.ProductImage
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
    // El valor de la merma sale del costo del pan: solo quien puede ver valores de despacho.
    val canSeeValue = user?.hasPermission(Permission.VIEW_DISPATCH_VALUE) == true
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
        else allocateGroupSales(window.unitsByGroup, window.groupProductIds, lines, window.staffUnitsByGroup)
    }
    fun soldOf(line: BreadCountLine) =
        if (isClosed) line.soldAsGroup else allocation.soldOf(line.productId)
    // Pan de colaborador anotado en caja: tampoco es merma.
    fun staffOf(line: BreadCountLine) =
        if (isClosed) line.staffBread else allocation.staffOf(line.productId)
    fun wasteOf(line: BreadCountLine) = (line.shortage - soldOf(line) - staffOf(line)).coerceAtLeast(0.0)
    val totalShortage = lines.sumOf { wasteOf(it) * it.unitCostSnapshot }
    val totalSurplus = lines.filter { it.difference > 0 }
        .sumOf { it.difference * it.unitCostSnapshot }
    // La razón se pide solo sobre la merma: el pan que se vendió como grupo no se perdió.
    val missingReasons = lines.filter { wasteOf(it) > 0 && it.reason.isBlank() }

    Scaffold(
        topBar = {
            BrandTopAppBar(
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
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                // Lo de arriba va dentro de la lista y se va con el scroll: fijo, en celular solo
                // dejaba ver tres productos a la vez.
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item(key = "header") {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DeepSeaBlue)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(header.branchName, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text("Fecha: ${header.countDate} · Por ${header.countedByUserName} · ${lines.size} productos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f))
                            }
                        }
                    }
                    if (isClosed) item(key = "closed") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.12f))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("Conteo cerrado el ${formatTimestamp(header.closedAt)}",
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1B5E20))
                                Text("Las mermas y ajustes ya se aplicaron al stock.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (canSeeValue) item(key = "kpis") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            KpiBox(label = "Mermas (valor)",
                                value = "Q ${totalShortage.fmtMoney(2)}",
                                color = if (totalShortage > 0) Color(0xFFB00020) else MaterialTheme.colorScheme.onSurface)
                            KpiBox(label = "Sobrante (valor)",
                                value = "Q ${totalSurplus.fmtMoney(2)}",
                                color = if (totalSurplus > 0) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (window != null && window.groupProductIds.isNotEmpty()) item(key = "group") {
                        GroupSalesCard(
                            window = window,
                            allocation = allocation,
                            isClosed = isClosed,
                            closedUnexplained = header.unexplainedGroupUnits,
                            isRefreshing = state.isGroupWindowLoading,
                            onRefresh = { viewModel.refreshGroupSales(companyId, countId) }
                        )
                    }
                    items(lines, key = { it.id }) { line ->
                        LineRow(
                            line = line,
                            imageUrl = state.productImages[line.productId],
                            groupName = groupOfProduct[line.productId]?.let { window?.groupNames?.get(it) },
                            soldAsGroup = soldOf(line),
                            staffBread = staffOf(line),
                            readOnly = isClosed,
                            onChange = { updated -> viewModel.updateLine(companyId, countId, updated) }
                        )
                    }
                    if (!isClosed) item(key = "notes") {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notas (opcional)") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (!isClosed) {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
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
    }

    if (showConfirm && user != null && header != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("¿Contaste bien todo?") },
            text = {
                val changed = lines.count { it.countedStock != it.expectedStock }
                Text(
                    "Al cerrar, el inventario de la tienda queda con estas cantidades y el conteo ya " +
                        "no se puede cambiar. Revisá que no te falte ningún producto: " +
                        "$changed de ${lines.size} tienen diferencia.\n\n" +
                    (if (allocation.totalAllocated > 0)
                        "Se registrarán como venta ${allocation.totalAllocated.fmt(0)} panes vendidos por grupo, " +
                            "repartidos según lo que falta de cada uno. " else "") +
                    (if (canSeeValue) "Se aplicarán las mermas (Q ${totalShortage.fmtMoney(2)}) y " +
                        "los sobrantes (Q ${totalSurplus.fmtMoney(2)}) al stock terminado. "
                    else "Se aplicarán las mermas y los sobrantes al stock terminado. ") +
                    "Las ventas de grupo se vuelven a leer al cerrar."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.closeCount(companyId, countId, notes.trim().ifEmpty { null }, user)
                }) { Text("Sí, estoy segura", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Revisar otra vez") } }
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
                val staff = allocation.staffByGroup[gid] ?: window.staffUnitsByGroup[gid] ?: 0.0
                val missing = allocation.shortageByGroup[gid] ?: 0.0
                val waste = (missing - sold - staff).coerceAtLeast(0.0)
                Text(
                    "${window.groupNames[gid].orEmpty()}: vendidos ${sold.fmt(0)} · " +
                        (if (staff > 0) "colaboradores ${staff.fmt(0)} · " else "") +
                        "faltan ${missing.fmt(0)} en el conteo" +
                        if (waste > 0) " → ${waste.fmt(0)} de merma" else "",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val unexplained = if (isClosed) closedUnexplained else allocation.totalUnexplained
            if (unexplained > 0) {
                Text(
                    "Se vendieron o dieron a colaboradores ${unexplained.fmt(0)} panes de grupo más de los que faltan. " +
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
    imageUrl: String?,
    groupName: String?,
    soldAsGroup: Double,
    staffBread: Double,
    readOnly: Boolean,
    onChange: (BreadCountLine) -> Unit
) {
    var countedText by rememberSaveable(line.id) {
        mutableStateOf(if (line.countedStock == 0.0) "" else line.countedStock.fmt(0))
    }
    val parsed = countedText.toDoubleOrNull() ?: 0.0
    val diff = parsed - line.expectedStock
    // Solo lo que no explican la venta de grupo ni el pan de colaborador es merma y pide razón.
    val sold = soldAsGroup.coerceAtMost((-diff).coerceAtLeast(0.0))
    val staff = staffBread.coerceAtMost((-diff - sold).coerceAtLeast(0.0))
    val waste = (-diff - sold - staff).coerceAtLeast(0.0)
    val diffColor = when {
        diff < 0 -> Color(0xFFB00020)
        diff > 0 -> Color(0xFF1B5E20)
        else     -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            // Una sola fila con la foto: se reconoce el pan sin leer el nombre.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ProductImage(
                    imageUrl = imageUrl,
                    contentDescription = line.productName,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(line.productName, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    Text(
                        "Esperado ${line.expectedStock.fmt(0)}" +
                            if (diff != 0.0) " · Dif. ${if (diff > 0) "+" else ""}${diff.fmt(0)}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (diff != 0.0) FontWeight.SemiBold else FontWeight.Normal,
                        color = diffColor
                    )
                    if (groupName != null && diff < 0) {
                        Text("Vendido ($groupName) ${sold.fmt(0)}" +
                                (if (staff > 0) " · colaboradores ${staff.fmt(0)}" else "") +
                                " · merma ${waste.fmt(0)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (waste > 0) Color(0xFFB00020) else BrandColors.Primary)
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = countedText,
                    onValueChange = {
                        if (readOnly) return@OutlinedTextField
                        countedText = it.filter { c -> c.isDigit() || c == '.' }
                        val newParsed = countedText.toDoubleOrNull() ?: 0.0
                        onChange(line.copy(countedStock = newParsed))
                    },
                    placeholder = { Text("0") },
                    singleLine = true,
                    readOnly = readOnly,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(84.dp)
                )
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
