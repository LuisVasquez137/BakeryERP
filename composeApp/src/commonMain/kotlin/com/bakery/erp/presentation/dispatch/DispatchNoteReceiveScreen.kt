package com.bakery.erp.presentation.dispatch

import com.bakery.erp.ui.BrandTopAppBar

import com.bakery.erp.model.isCompleteReason

import com.bakery.erp.model.ReceptionReason

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import kotlin.time.Clock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.DiscrepancyType
import com.bakery.erp.model.DispatchNoteLine
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.production.ProductionViewModel
import com.bakery.erp.presentation.production.RecipeRef
import com.bakery.erp.presentation.production.groupByMasa
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.util.fmt
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


private data class ReceiveLineState(
    val original: DispatchNoteLine,
    val receivedInput: String,
    val discrepancyType: String?,
    val reason: String
) {
    fun toLine(): DispatchNoteLine {
        val qty = receivedInput.toDoubleOrNull() ?: 0.0
        return original.copy(
            receivedQuantity = qty,
            discrepancyType = if (qty != original.packedQuantity) discrepancyType else null,
            receivedReason = if (qty != original.packedQuantity && reason.isNotBlank()) reason.trim() else null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchNoteReceiveScreen(
    noteId: String,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: DispatchViewModel,
    productionViewModel: ProductionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val productionState by productionViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

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
    // El acuse de llegada (paso 1) sí sale solo: no hay nada que reportar más allá de
    // "el repartidor se puede ir". El cierre del conteo (paso 2) mueve inventario y se
    // acusa con diálogo, así que ahí la navegación la dispara el botón del diálogo.
    LaunchedEffect(state.isSaved, state.receptionResult) {
        if (state.isSaved && state.receptionResult == null) {
            viewModel.clearSaved(); onNavigateBack()
        }
    }

    val note = state.selectedNote
    var lineStates by remember(note?.id) { mutableStateOf<List<ReceiveLineState>>(emptyList()) }
    // Estado del checklist de llegada (paso 1): qué productos confirmó visualmente la encargada.
    // Mapea lineId → timestamp (null = no marcado).
    var arrivalChecks by remember(note?.id) { mutableStateOf<Map<String, Long?>>(emptyMap()) }
    LaunchedEffect(note?.id, note?.lines) {
        if (note != null) {
            lineStates = note.lines.map { line ->
                ReceiveLineState(
                    original = line,
                    receivedInput = (if (line.receivedQuantity > 0) line.receivedQuantity else line.packedQuantity).fmt(0),
                    discrepancyType = line.discrepancyType,
                    reason = line.receivedReason ?: ""
                )
            }
            arrivalChecks = note.lines.associate { it.id to it.lineArrivedConfirmedAt }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Recibir despacho", fontWeight = FontWeight.SemiBold) },
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
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        // En el paso 1 el encabezado va dentro de la lista que se desliza
                        // (ver ArrivalChecklistSection).
                        if (note.status != DispatchStatus.IN_TRANSIT) {
                            HeaderCard(note)
                            Spacer(Modifier.height(12.dp))
                        }

                        // PASO 1: Acuse de llegada (IN_TRANSIT → ARRIVED) con checklist
                        if (DispatchStatus.isTerminal(note.status)) {
                            // La nota ya cerró. Sin esto la pantalla volvía a dibujar el
                            // formulario de conteo editable sobre una nota recibida:
                            // detrás del acuse se leía como si nada hubiera pasado, y
                            // volver acá por el historial ofrecía contar de nuevo algo
                            // que ya movió inventario.
                            ClosedNoteSection(note)
                        } else if (note.status == DispatchStatus.IN_TRANSIT) {
                            var basketsArrived by remember(note.id) { mutableStateOf("") }
                            ArrivalChecklistSection(
                                header = { HeaderCard(note) },
                                lines = note.lines,
                                recipeRefByProductId = productionState.recipeRefByProductId,
                                arrivalChecks = arrivalChecks,
                                isLoading = state.isLoading,
                                basketsSent = note.basketsSent,
                                basketsArrived = basketsArrived,
                                onBasketsArrivedChange = { basketsArrived = it },
                                onToggle = { lineId, checked ->
                                    val now = if (checked) Clock.System.now().toEpochMilliseconds() else null
                                    arrivalChecks = arrivalChecks + (lineId to now)
                                },
                                onAcknowledge = {
                                    user ?: return@ArrivalChecklistSection
                                    val updated = note.lines.map { it.copy(lineArrivedConfirmedAt = arrivalChecks[it.id]) }
                                    viewModel.acknowledgeArrival(
                                        companyId, noteId, updated, user,
                                        basketsReceived = if (note.basketsSent > 0) basketsArrived.toIntOrNull() else null
                                    )
                                }
                            )
                        } else {
                            // PASO 2: Conteo final (ARRIVED → DELIVERED / DELIVERED_WITH_DISCREPANCY)
                            CountSection(
                                lineStates = lineStates,
                                recipeRefByProductId = productionState.recipeRefByProductId,
                                isLoading = state.isLoading,
                                onResetAll = {
                                    lineStates = lineStates.map {
                                        it.copy(
                                            receivedInput = it.original.packedQuantity.fmt(0),
                                            discrepancyType = null,
                                            reason = ""
                                        )
                                    }
                                },
                                onLineChange = { updated ->
                                    lineStates = lineStates.map {
                                        if (it.original.id == updated.original.id) updated else it
                                    }
                                },
                                onSubmit = {
                                    user ?: return@CountSection
                                    viewModel.confirmReception(
                                        companyId, noteId,
                                        lineStates.map { it.toLine() },
                                        user
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }

    val result = state.receptionResult
    if (result != null && result.noteId == noteId) {
        val close = {
            viewModel.clearReceptionResult()
            viewModel.clearSaved()
            onNavigateBack()
        }
        AlertDialog(
            // Sin descarte por fuera: es un acuse de que el pan entró al inventario,
            // y tocarlo al lado no puede leerse como haberlo visto.
            onDismissRequest = {},
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32)
                )
            },
            title = { Text("Conteo registrado") },
            text = {
                Column {
                    Text(
                        "Entraron ${result.unitsReceived.fmt(0)} unidades de " +
                            "${result.productCount} producto(s) al inventario de " +
                            result.destinationBranchName.ifEmpty { "la sucursal" } + "."
                    )
                    if (result.discrepancyCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Se anotaron ${result.discrepancyCount} discrepancia(s). Lo que faltó " +
                                "queda como pérdida en tránsito, no vuelve al stock de la planta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.AccentDeep
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = close) { Text("Listo") } }
        )
    }
}

@Composable
private fun ClosedNoteSection(note: com.bakery.erp.model.DispatchNote) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32))
                Spacer(Modifier.width(8.dp))
                Text(
                    DispatchStatus.label(note.status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1B5E20)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "El conteo de esta nota ya se cerró y el pan entró al inventario de " +
                    note.destinationBranchName.ifEmpty { "la sucursal" } + ". " +
                    "Los ajustes posteriores se hacen desde Inventario de tienda.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiveLineCard(
    state: ReceiveLineState,
    onChange: (ReceiveLineState) -> Unit
) {
    val received = state.receivedInput.toDoubleOrNull() ?: 0.0
    val diff = received != state.original.packedQuantity
    var typeExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Inventory, null, tint = DeepSeaBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(dispatchLineLabel(
                            state.original.productName,
                            state.original.isSpecial,
                            state.original.specialRawWeightGrams
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepSeaBlue)
                    Text(
                        "Empacado: ${state.original.packedQuantity.fmt(0)} ${state.original.productUnit} · " +
                            "Pedido: ${state.original.requestedQuantity.fmt(0)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = state.receivedInput,
                    onValueChange = { onChange(state.copy(receivedInput = it.filter { c -> c.isDigit() || c == '.' })) },
                    label = { Text("Recibido") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.widthIn(min = 110.dp, max = 140.dp)
                )
            }
            if (diff) {
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = state.discrepancyType?.let { DiscrepancyType.label(it) } ?: "Tipo de discrepancia *",
                        onValueChange = {},
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        isError = state.discrepancyType == null,
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        DiscrepancyType.all.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(DiscrepancyType.label(t)) },
                                onClick = {
                                    // Los motivos son por tipo: "No llegó la canasta" no explica algo quebrado.
                                    val keep = state.reason.takeIf { it in ReceptionReason.optionsFor(t) || it.startsWith("Otro") }
                                    onChange(state.copy(discrepancyType = t, reason = keep.orEmpty()))
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                if (state.discrepancyType != null) {
                    Spacer(Modifier.height(8.dp))
                    ReasonPicker(
                        title = "¿Qué pasó? *",
                        options = ReceptionReason.optionsFor(state.discrepancyType),
                        value = state.reason,
                        onValueChange = { onChange(state.copy(reason = it)) }
                    )
                }
                val deltaQty = received - state.original.packedQuantity
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (deltaQty < 0) "Faltan ${(-deltaQty).fmt(0)} · pérdida en tránsito"
                            else "Sobran ${deltaQty.fmt(0)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (deltaQty < 0) Color(0xFFFFEBEE) else Color(0xFFFFF8E1)
                    ),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun HeaderCard(note: com.bakery.erp.model.DispatchNote) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSeaBlue)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Desde ${note.originBranchName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                "${note.shiftName.ifEmpty { "—" }} · Producción del ${formatDay(note.productionDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
            if (note.status == DispatchStatus.ARRIVED && note.arrivedAt != null) {
                Text(
                    "Llegada reportada por ${note.arrivedByUserName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Paso 1 de la recepción: marcar producto por producto lo que el repartidor va
 * descargando.
 *
 * Todo —encabezado, instrucciones, casillas, canastas y aviso— va dentro de UN
 * [LazyColumn] con `weight(1f)`, y solo el botón queda fijo abajo. Antes las tarjetas
 * de arriba y la de canastas estaban fijas y solo se deslizaba la lista: en el
 * teléfono de Pacajá, con la letra del sistema grande, esas tarjetas ocupaban toda
 * la pantalla y la lista quedaba con altura cero. La encargada no tenía ninguna
 * casilla que marcar (0/29), el botón nunca se habilitaba y el dueño terminaba
 * firmando la llegada por ella. Con un solo scroll cabe en cualquier pantalla y con
 * cualquier tamaño de letra.
 */
@Composable
private fun ColumnScope.ArrivalChecklistSection(
    header: @Composable () -> Unit,
    lines: List<DispatchNoteLine>,
    recipeRefByProductId: Map<String, RecipeRef>,
    arrivalChecks: Map<String, Long?>,
    isLoading: Boolean,
    /** Canastas que salieron de la planta; 0 en notas anteriores (no se pregunta). */
    basketsSent: Int = 0,
    basketsArrived: String = "",
    onBasketsArrivedChange: (String) -> Unit = {},
    onToggle: (lineId: String, checked: Boolean) -> Unit,
    onAcknowledge: () -> Unit
) {
    val total = lines.size
    val checkedCount = lines.count { arrivalChecks[it.id] != null }
    // Con canastas, la llegada se firma también con cuántas llegaron: es el único dato
    // que separa "se perdió en el camión" de "faltó dentro de la canasta".
    val basketsAnswered = basketsSent <= 0 || basketsArrived.toIntOrNull() != null
    val allChecked = total > 0 && checkedCount == total && basketsAnswered

    val groupedLines = remember(lines, recipeRefByProductId) {
        groupByMasa(
            items = lines,
            recipeIdOf = { dispatchLineRecipeId(it, recipeRefByProductId) },
            recipeNameOf = { dispatchLineRecipeName(it, recipeRefByProductId) },
            nameOf = { it.productName }
        )
    }

    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "note_header") {
            Column {
                header()
                Spacer(Modifier.height(6.dp))
            }
        }
        item(key = "arrival_intro") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Llegó el repartidor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = DeepSeaBlue
                        )
                        Text(
                            "Marca cada producto a medida que lo descargas. El conteo final lo haces después.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                "$checkedCount/$total",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (allChecked) Color(0xFF2E7D32) else BrandColors.AccentDeep
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (allChecked) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                        )
                    )
                }
            }
        }
        // Las canastas arriba de los productos: es lo primero que baja del camión.
        if (basketsSent > 0) {
            item(key = "baskets") {
                BasketsArrivedCard(basketsSent, basketsArrived, onBasketsArrivedChange)
            }
        }
        groupedLines.forEach { group ->
            item(key = "header_${group.key}") {
                Text(
                    group.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = DeepSeaBlue,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }
            items(group.items, key = { it.id }) { line ->
                ArrivalCheckRow(
                    line = line,
                    checked = arrivalChecks[line.id] != null,
                    onCheckedChange = { onToggle(line.id, it) }
                )
            }
        }
        if (!allChecked) {
            item(key = "arrival_hint") {
                Text(
                    "Marca todos los productos para habilitar el reporte de llegada. Si algo no descargó, no firmes hasta que esté completo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.AccentDeep,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Button(
        onClick = onAcknowledge,
        modifier = Modifier.fillMaxWidth(),
        enabled = allChecked && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp),
                color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.HowToReg, null)
            Spacer(Modifier.width(8.dp))
            Text("Reportar llegada del repartidor", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ArrivalCheckRow(
    line: DispatchNoteLine,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(Modifier.width(4.dp))
        Text(
            dispatchLineLabel(line.productName, line.isSpecial, line.specialRawWeightGrams),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            color = if (checked) DeepSeaBlue else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            line.productUnit.ifEmpty { "—" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ColumnScope.CountSection(
    lineStates: List<ReceiveLineState>,
    recipeRefByProductId: Map<String, RecipeRef>,
    isLoading: Boolean,
    onResetAll: () -> Unit,
    onLineChange: (ReceiveLineState) -> Unit,
    onSubmit: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Cuenta lo que llegó",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = DeepSeaBlue,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = onResetAll) {
            Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Todo cabal")
        }
    }

    Spacer(Modifier.height(6.dp))

    val groupedLines = remember(lineStates, recipeRefByProductId) {
        groupByMasa(
            items = lineStates,
            recipeIdOf = { dispatchLineRecipeId(it.original, recipeRefByProductId) },
            recipeNameOf = { dispatchLineRecipeName(it.original, recipeRefByProductId) },
            nameOf = { it.original.productName }
        )
    }

    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groupedLines.forEach { group ->
            item(key = "header_${group.key}") {
                Text(
                    group.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = DeepSeaBlue,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }
            items(group.items, key = { it.original.id }) { ls ->
                ReceiveLineCard(state = ls, onChange = onLineChange)
            }
        }
    }

    val needsReason = lineStates.filter {
        val v = it.receivedInput.toDoubleOrNull() ?: 0.0
        v != it.original.packedQuantity && (it.discrepancyType == null ||
            !isCompleteReason(it.reason, ReceptionReason.optionsFor(it.discrepancyType)))
    }
    val canSubmit = needsReason.isEmpty()
    if (needsReason.isNotEmpty()) {
        Text(
            "Falta tipo + razón en ${needsReason.size} línea(s) con cantidad distinta a lo empacado.",
            style = MaterialTheme.typography.bodySmall,
            color = BrandColors.AccentDeep,
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    // El botón queda donde cae el pulgar al deslizar la lista: se tocaba sin haber
    // terminado de contar. Cerrar mueve inventario y no se deshace, así que pregunta.
    var showConfirm by remember { mutableStateOf(false) }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { showConfirm = true },
        modifier = Modifier.fillMaxWidth(),
        enabled = canSubmit && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp),
                color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.CheckCircle, null)
            Spacer(Modifier.width(8.dp))
            Text("Confirmar conteo final", fontWeight = FontWeight.SemiBold)
        }
    }

    if (showConfirm) {
        val units = lineStates.sumOf { it.receivedInput.toDoubleOrNull() ?: 0.0 }
        val changed = lineStates.count {
            (it.receivedInput.toDoubleOrNull() ?: 0.0) != it.original.packedQuantity
        }
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("¿Contaste bien todo?") },
            text = {
                Text(
                    "Van a entrar ${units.fmt(0)} unidades de ${lineStates.size} producto(s) al " +
                        "inventario de la tienda y el conteo ya no se puede cambiar.\n\n" +
                        if (changed == 0) "Todo llegó cabal, igual a lo empacado."
                        else "$changed producto(s) con cantidad distinta a lo empacado."
                )
            },
            confirmButton = {
                Button(onClick = { showConfirm = false; onSubmit() }) {
                    Text("Sí, ya conté todo", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Revisar otra vez") }
            }
        )
    }
}


/**
 * "Salieron 6 canastas. ¿Cuántas llegaron?" — sin prellenar el número, para que no se firme
 * sin contar. El botón "Llegaron las 6" es el caso normal y cuesta un toque.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BasketsArrivedCard(sent: Int, value: String, onChange: (String) -> Unit) {
    val n = value.toIntOrNull()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Salieron $sent canasta${if (sent == 1) "" else "s"}. ¿Cuántas llegaron?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = DeepSeaBlue
            )
            // FlowRow: con letra grande el campo baja a la línea siguiente en vez de
            // quedar aplastado a tres renglones ("Otra / cantid / ad").
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = n == sent,
                    onClick = { onChange(sent.toString()) },
                    label = { Text("Llegaron las $sent") }
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(3)) },
                    placeholder = { Text("Otra cantidad") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(150.dp)
                )
            }
            if (n != null && n < sent) {
                Text(
                    "Faltan ${sent - n} canasta${if (sent - n == 1) "" else "s"}: queda anotado que se perdió en el camino.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandColors.AccentDeep
                )
            }
        }
    }
}
