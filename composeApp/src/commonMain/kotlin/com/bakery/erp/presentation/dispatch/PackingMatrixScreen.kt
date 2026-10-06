package com.bakery.erp.presentation.dispatch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.DispatchNote
import com.bakery.erp.model.DispatchNoteLine
import com.bakery.erp.model.PriceSourceTag
import com.bakery.erp.model.User
import com.bakery.erp.presentation.production.ProductionViewModel
import com.bakery.erp.presentation.production.RecipeRef
import com.bakery.erp.presentation.production.groupByMasa
import com.bakery.erp.ui.SortableItem
import com.bakery.erp.ui.SortableItemsDialog
import com.bakery.erp.util.fmt
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors

private val AltRowBg    = Color(0xFFF7F5EF)
private val HeaderBg    = Color(0xFFEEF2F7)

/**
 * Estilo unificado para chips de filtro (turno, planta) que cuando se seleccionan
 * cambian a fondo Deep Sea Blue con texto blanco — mucho más visible que el default
 * gris claro de Material3.
 */
@Composable
internal fun strongFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = DeepSeaBlue,
    selectedLabelColor = Color.White
)

@Composable
internal fun strongFilterChipBorder(selected: Boolean) =
    FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = selected,
        selectedBorderColor = DeepSeaBlue,
        borderColor = Color(0xFFCED4DA),
        selectedBorderWidth = 1.5.dp
    )

/**
 * Pestaña "Por empacar" rediseñada como matriz por turno + planta + día.
 * En tablet (≥720dp) muestra una tabla productos × sucursales destino.
 * En móvil, tabs por sucursal destino con lista vertical de productos.
 */
@Composable
fun PackingMatrixContent(
    companyId: String,
    user: User,
    viewModel: DispatchViewModel,
    productionViewModel: ProductionViewModel
) {
    val state by viewModel.packingState.collectAsState()
    val recipeRefByProductId = productionViewModel.uiState.collectAsState().value.recipeRefByProductId
    var showBranchOrderDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(companyId, user.id) {
        if (companyId.isNotEmpty()) viewModel.setupPackingFor(companyId, user)
    }
    // Mapa producto → masa, para agrupar igual que la hoja de trabajo del panadero.
    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) productionViewModel.loadRecipeRefByProductIdIfNeeded(companyId)
    }
    LaunchedEffect(state.lastCloseResult) {
        // No hace nada por sí solo — el banner ya se muestra y se cierra cuando el usuario
        // confirma; aquí sería el lugar de un snackbar si se quiere en el futuro.
    }

    // En teléfono el encabezado (planta + turno + día) se comía cerca de un tercio
    // de la pantalla, y lo que se vino a hacer acá es contar pan. Los selectores se
    // tocan una vez al entrar y no se vuelven a mirar, así que se pliegan a una
    // línea de resumen en cuanto ya hay planta y turno elegidos. Si falta elegir
    // algo, se muestran igual — plegar lo que todavía hay que tocar sería esconder
    // el trabajo pendiente.
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val isPhone = with(density) { windowInfo.containerSize.width.toDp() < 720.dp }
    var headerExpanded by rememberSaveable { mutableStateOf(false) }
    val mustExpand = state.producingBranchId.isEmpty() || state.shiftId.isEmpty()
    val showSelectors = !isPhone || headerExpanded || mustExpand

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (isPhone && !mustExpand) {
            CompactHeaderSummary(
                plantName = state.producingBranches.find { it.id == state.producingBranchId }?.name.orEmpty(),
                shiftName = state.shifts.find { it.id == state.shiftId }?.name.orEmpty(),
                dayMillis = state.dayMillis,
                expanded = headerExpanded,
                showSortButton = state.notes.size > 1,
                onToggle = { headerExpanded = !headerExpanded },
                onSort = { showBranchOrderDialog = true }
            )
        }

        // Selector de planta — solo si hay más de una.
        if (showSelectors && state.producingBranches.size > 1) {
            Text(
                "Planta productora",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = DeepSeaBlue,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.producingBranches.forEach { b ->
                    val selected = b.id == state.producingBranchId
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectPackingBranch(companyId, b.id) },
                        label = {
                            Text(b.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        },
                        colors = strongFilterChipColors(),
                        border = strongFilterChipBorder(selected)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // Selector de turno
        if (showSelectors && state.shifts.isNotEmpty()) {
            // El botón de ordenar va en la misma fila que el rótulo "Turno", que
            // tenía toda la derecha vacía. En su propia fila costaba ~48dp, y en
            // teléfono el encabezado (planta + turno + día) ya deja poca lista
            // visible: cada fila que se agrega se la quita al conteo, que es a lo
            // que se vino la pantalla.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Turno",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DeepSeaBlue,
                    modifier = Modifier.weight(1f)
                )
                if (state.notes.size > 1) {
                    TextButton(
                        onClick = { showBranchOrderDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = null,
                            tint = BrandColors.Primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Ordenar sucursales",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandColors.Primary
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.shifts.forEach { s ->
                    val selected = s.id == state.shiftId
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectPackingShift(companyId, s.id) },
                        label = {
                            Text(s.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        },
                        colors = strongFilterChipColors(),
                        border = strongFilterChipBorder(selected)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        } else if (showSelectors && state.producingBranchId.isNotEmpty() && !state.isLoading) {
            Text(
                "La planta no tiene turnos activos configurados.",
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.AccentDeep,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        // Navegador de día. En teléfono va compacto (una línea en vez de tres) y solo
        // cuando el encabezado está desplegado: plegado, la fecha ya la dice el resumen.
        if (showSelectors) {
            DayNavigator(
                dayMillis = state.dayMillis,
                onPrev = { viewModel.shiftPackingDay(companyId, -1) },
                onNext = { viewModel.shiftPackingDay(companyId, +1) },
                onToday = { viewModel.goToTodayPacking(companyId) },
                onPickDay = { viewModel.goToPackingDay(companyId, it) },
                compact = isPhone
            )
        }

        if (showBranchOrderDialog) {
            SortableItemsDialog(
                title = "Orden de las sucursales",
                description = "El orden en que aparecen al empacar. Se guarda para " +
                    "esta planta, así que lo ve quien cuente el pan en cualquier " +
                    "dispositivo. Las sucursales nuevas se agregan al final.",
                items = state.notes.map {
                    SortableItem(it.destinationBranchId, it.destinationBranchName)
                },
                onDismiss = { showBranchOrderDialog = false },
                onSave = { ordered ->
                    viewModel.saveDispatchBranchOrder(companyId, ordered)
                    showBranchOrderDialog = false
                }
            )
        }

        // Contenido principal
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading && state.notes.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.producingBranchId.isEmpty() ->
                    EmptyHint("No tienes plantas productoras accesibles.")
                state.shiftId.isEmpty() ->
                    EmptyHint("Selecciona un turno para ver el empaque.")
                state.notes.isEmpty() ->
                    EmptyHint(
                        "No hay nada por empacar en este turno.\n" +
                            "Aparecerá automáticamente en cuanto los panaderos inicien producción."
                    )
                else -> MatrixOrTabs(
                    notes = state.notes,
                    recipeRefByProductId = recipeRefByProductId,
                    onToggleCheck = { noteId, lineId, currentlyChecked ->
                        viewModel.toggleLineChecked(companyId, noteId, lineId, currentlyChecked)
                    },
                    onEditQty = { noteId, lineId, qty, reason, donorNoteId, donorLineId ->
                        viewModel.updateLinePackedQuantity(
                            companyId, noteId, lineId, qty, reason, donorNoteId, donorLineId
                        )
                    },
                    shortfallByBranchProduct = state.shortfallFromEarlierShifts,
                    focusDestinationBranchId = state.focusDestinationBranchId
                )
            }
        }

        // Banner de resultado de cierre
        state.lastCloseResult?.let { res ->
            CloseResultBanner(res, onDismiss = { viewModel.clearPackingCloseResult() })
        }
        // Banner de error
        state.error?.let { err ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFB00020))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB00020),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearPackingError() }) { Text("OK") }
                }
            }
        }

        // Footer: botón global cerrar empaque
        if (state.notes.isNotEmpty()) {
            CloseTurnFooter(
                notes = state.notes,
                isClosing = state.isClosing,
                onClose = { viewModel.closePackingTurn(companyId, user) }
            )
        }
    }
}

// ── Encabezado plegado (teléfono) ────────────────────────────────────────────

/**
 * Resumen de una línea de lo que está seleccionado, para recuperar en teléfono el
 * espacio que se llevaban los tres selectores.
 *
 * Toda la fila abre y cierra, y lleva chevron: un resumen que no se ve tocable
 * dejaría a la encargada sin forma evidente de cambiar de turno. El botón de
 * ordenar viaja acá también — si viviera solo en la fila desplegada, plegar el
 * encabezado lo escondería justo mientras se cuenta, que es cuando se usa.
 */
@Composable
private fun CompactHeaderSummary(
    plantName: String,
    shiftName: String,
    dayMillis: Long,
    expanded: Boolean,
    showSortButton: Boolean,
    onToggle: () -> Unit,
    onSort: () -> Unit
) {
    val dayLabel = remember(dayMillis) { formatDayLabel(dayMillis) }
    val summary = listOf(plantName, shiftName, dayLabel).filter { it.isNotBlank() }.joinToString(" · ")
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() }
                .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = DeepSeaBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (showSortButton) {
                IconButton(onClick = onSort, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.SwapVert,
                        "Ordenar sucursales",
                        tint = BrandColors.Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                if (expanded) "Ocultar selectores" else "Cambiar planta, turno o día",
                tint = BrandColors.AccentDeep,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ── Navegador de día ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayNavigator(
    dayMillis: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onPickDay: (Long) -> Unit = {},
    /**
     * Una sola línea en vez de tres. En teléfono los rótulos "Para entregar el" y
     * "Tocá para elegir fecha" cuestan ~30dp de lista y solo se leen la primera vez:
     * el gesto de tocar la fecha sigue abriendo el calendario igual.
     */
    compact: Boolean = false
) {
    val label = remember(dayMillis) { formatDayLabel(dayMillis) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = dayMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let(onPickDay)
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = dpState)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Default.ChevronLeft, "Día anterior", tint = DeepSeaBlue)
            }
            // Tocar la fecha abre el calendario; "ir a hoy" tiene su propio botón al
            // lado, así que no se pierde nada.
            Column(
                modifier = Modifier.weight(1f).clickable { showDatePicker = true },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!compact) {
                    Text(
                        "Para entregar el",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    label,
                    style = if (compact) MaterialTheme.typography.bodyMedium
                            else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DeepSeaBlue
                )
                if (!compact) {
                    Text(
                        "Tocá para elegir fecha",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.AccentDeep
                    )
                }
            }
            IconButton(onClick = onToday) {
                Icon(Icons.Default.Today, "Hoy", tint = BrandColors.AccentDeep, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.ChevronRight, "Día siguiente", tint = DeepSeaBlue)
            }
        }
    }
}

// ── Layout responsive: matriz vs tabs ────────────────────────────────────────

@Composable
private fun MatrixOrTabs(
    notes: List<DispatchNote>,
    recipeRefByProductId: Map<String, RecipeRef>,
    onToggleCheck: (noteId: String, lineId: String, currentlyChecked: Boolean) -> Unit,
    onEditQty: (noteId: String, lineId: String, qty: Double, reason: String?, donorNoteId: String?, donorLineId: String?) -> Unit,
    shortfallByBranchProduct: Map<String, ShiftShortfall> = emptyMap(),
    focusDestinationBranchId: String = ""
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= 720.dp) {
            PackingMatrixTable(notes, recipeRefByProductId, onToggleCheck, onEditQty, shortfallByBranchProduct)
        } else {
            PackingBranchTabs(notes, recipeRefByProductId, onToggleCheck, onEditQty, shortfallByBranchProduct, focusDestinationBranchId)
        }
    }
}

// ── Matriz (tablet/desktop) ──────────────────────────────────────────────────

private data class MatrixRow(
    val productId: String,
    val productName: String,
    val productUnit: String,
    /** Pedido especial: pan de encargo, fuera de catálogo. Se marca con ★ para
     *  que quien empaca no lo confunda con el pan de todos los días. */
    val isSpecial: Boolean = false,
    /** Peso en crudo de una pieza del especial, en gramos. Se muestra en onzas. */
    val specialRawWeightGrams: Double = 0.0,
    /** Masa del especial: su productId sintético no está en el catálogo, así que
     *  sin esto la fila caería en "Otros productos" en vez de con su masa. */
    val specialRecipeId: String = "",
    val specialRecipeName: String = "",
    /** noteId -> (lineId, requested, packed, checked, priceMissing) */
    val cellsByNote: Map<String, Cell>
) {
    data class Cell(
        val lineId: String,
        val requested: Double,
        val packed: Double,
        val checked: Boolean,
        val noPrice: Boolean
    )
}

@Composable
private fun PackingMatrixTable(
    notes: List<DispatchNote>,
    recipeRefByProductId: Map<String, RecipeRef>,
    onToggleCheck: (noteId: String, lineId: String, currentlyChecked: Boolean) -> Unit,
    onEditQty: (noteId: String, lineId: String, qty: Double, reason: String?, donorNoteId: String?, donorLineId: String?) -> Unit,
    shortfallByBranchProduct: Map<String, ShiftShortfall> = emptyMap()
) {
    // Build rows: union de productos. Cada nota = una columna sucursal destino.
    val rows = remember(notes) { buildMatrixRows(notes) }
    // Agrupado por masa igual que la hoja de trabajo del panadero — los mismos
    // panes de la misma receta juntos, sin importar a cuántas sucursales van.
    val groupedRows = remember(rows, recipeRefByProductId) {
        groupByMasa(
            items = rows,
            recipeIdOf = { it.specialRecipeId.ifEmpty { recipeRefByProductId[it.productId]?.id.orEmpty() }.takeIf(String::isNotEmpty) },
            recipeNameOf = { it.specialRecipeName.ifEmpty { recipeRefByProductId[it.productId]?.name.orEmpty() }.takeIf(String::isNotEmpty) },
            nameOf = { it.productName }
        )
    }
    val productColWidth = 220.dp
    val cellWidth = 120.dp

    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var unmarkTarget by remember { mutableStateOf<UnmarkTarget?>(null) }

    Card(
        modifier = Modifier.fillMaxSize().padding(vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Row(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    // Header
                    Row(
                        modifier = Modifier.background(HeaderBg).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeaderCell("Producto", productColWidth, alignStart = true)
                        notes.forEach { n ->
                            HeaderCell(
                                text = n.destinationBranchName.ifEmpty { "(sin nombre)" },
                                width = cellWidth
                            )
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = Color(0xFFCED4DA))

                    var rowIndex = 0
                    groupedRows.forEach { group ->
                        Text(
                            group.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = DeepSeaBlue,
                            modifier = Modifier
                                .width(productColWidth + cellWidth * notes.size)
                                .background(HeaderBg)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        group.items.forEach { row ->
                        val i = rowIndex++
                        Row(
                            modifier = Modifier
                                .background(if (i % 2 == 1) AltRowBg else Color.Transparent)
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Producto
                            Column(modifier = Modifier.width(productColWidth).padding(horizontal = 8.dp)) {
                                Text(
                                    dispatchLineLabel(row.productName, row.isSpecial, row.specialRawWeightGrams),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DeepSeaBlue
                                )
                                if (row.productUnit.isNotEmpty()) {
                                    Text(
                                        row.productUnit,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            // Celdas por sucursal
                            notes.forEach { note ->
                                val cell = row.cellsByNote[note.id]
                                MatrixCell(
                                    cell = cell,
                                    width = cellWidth,
                                    onToggle = {
                                        if (cell != null) {
                                            if (cell.checked) {
                                                unmarkTarget = UnmarkTarget(
                                                    noteId = note.id,
                                                    lineId = cell.lineId,
                                                    productName = row.productName,
                                                    branchName = note.destinationBranchName
                                                )
                                            } else {
                                                onToggleCheck(note.id, cell.lineId, false)
                                            }
                                        }
                                    },
                                    onEdit = {
                                        if (cell != null) {
                                            editTarget = EditTarget(
                                                noteId = note.id,
                                                lineId = cell.lineId,
                                                productId = row.productId,
                                                productName = row.productName,
                                                branchName = note.destinationBranchName,
                                                requested = cell.requested,
                                                packed = cell.packed,
                                                existingReason = ""
                                            )
                                        }
                                    }
                                )
                            }
                        }
                        HorizontalDivider(thickness = 0.6.dp, color = Color(0xFFE9ECEF))
                        }
                    }
                }
            }
        }
    }

    editTarget?.let { target ->
        EditPackedQuantityDialog(
            target = target,
            donors = donorsFor(notes, target.productId, target.noteId),
            shortfall = shortfallByBranchProduct[
                "${notes.find { it.id == target.noteId }?.destinationBranchId}|${target.productId}"
            ],
            onCancel = { editTarget = null },
            onSave = { newQty, reason, donor ->
                onEditQty(target.noteId, target.lineId, newQty, reason, donor?.noteId, donor?.lineId)
                editTarget = null
            }
        )
    }
    unmarkTarget?.let { target ->
        UnmarkConfirmDialog(
            target = target,
            onCancel = { unmarkTarget = null },
            onConfirm = {
                onToggleCheck(target.noteId, target.lineId, true)
                unmarkTarget = null
            }
        )
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp, alignStart: Boolean = false) {
    Text(
        text,
        modifier = Modifier.width(width).padding(horizontal = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = DeepSeaBlue,
        textAlign = if (alignStart) TextAlign.Start else TextAlign.Center
    )
}

@Composable
private fun MatrixCell(
    cell: MatrixRow.Cell?,
    width: androidx.compose.ui.unit.Dp,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    Box(
        modifier = Modifier.width(width).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (cell == null) {
            Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onToggle, modifier = Modifier.size(34.dp)) {
                    if (cell.checked) {
                        Icon(
                            Icons.Default.CheckCircle,
                            "Contado",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(26.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.RadioButtonUnchecked,
                            "Marcar como contado",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                QuantityChip(
                    qty = cell.packed,
                    isAdjusted = cell.packed != cell.requested,
                    noPrice = cell.noPrice,
                    onClick = onEdit
                )
            }
        }
    }
}

@Composable
private fun QuantityChip(
    qty: Double,
    isAdjusted: Boolean,
    noPrice: Boolean,
    onClick: () -> Unit
) {
    val (bg, fg, border) = when {
        noPrice -> Triple(Color(0xFFFFEBEE), Color(0xFFB00020), Color(0xFFB00020))
        isAdjusted -> Triple(Color(0xFFFFF7E6), BrandColors.AccentDeep, BrandColors.AccentDeep)
        else -> Triple(Color(0xFFEEF2F7), DeepSeaBlue, Color(0xFFCED4DA))
    }
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(bg, shape = RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, border), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            qty.fmt(0),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}

// ── Tabs por sucursal (móvil) ────────────────────────────────────────────────

@Composable
private fun PackingBranchTabs(
    notes: List<DispatchNote>,
    recipeRefByProductId: Map<String, RecipeRef>,
    onToggleCheck: (noteId: String, lineId: String, currentlyChecked: Boolean) -> Unit,
    onEditQty: (noteId: String, lineId: String, qty: Double, reason: String?, donorNoteId: String?, donorLineId: String?) -> Unit,
    shortfallByBranchProduct: Map<String, ShiftShortfall> = emptyMap(),
    focusDestinationBranchId: String = ""
) {
    // Desde el tablero de Reparto se llega con una sucursal elegida: abrir en su pestaña.
    var selectedIdx by remember(notes.map { it.id }.joinToString()) {
        mutableIntStateOf(notes.indexOfFirst { it.destinationBranchId == focusDestinationBranchId }.coerceAtLeast(0))
    }
    val safeIdx = selectedIdx.coerceIn(0, (notes.size - 1).coerceAtLeast(0))

    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var unmarkTarget by remember { mutableStateOf<UnmarkTarget?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = safeIdx,
            containerColor = WarmIvory,
            contentColor = DeepSeaBlue,
            edgePadding = 4.dp
        ) {
            notes.forEachIndexed { i, note ->
                val pending = note.lines.count { it.lineCheckedAt == null }
                val total = note.lines.size
                val label = if (pending == 0) note.destinationBranchName + " ✓"
                            else "${note.destinationBranchName} ($pending/$total)"
                Tab(
                    selected = i == safeIdx,
                    onClick = { selectedIdx = i },
                    text = {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (i == safeIdx) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }
        val selectedNote = notes.getOrNull(safeIdx)
        if (selectedNote != null) {
            val groupedLines = remember(selectedNote.lines, recipeRefByProductId) {
                groupByMasa(
                    items = selectedNote.lines,
                    recipeIdOf = { dispatchLineRecipeId(it, recipeRefByProductId) },
                    recipeNameOf = { dispatchLineRecipeName(it, recipeRefByProductId) },
                    nameOf = { it.productName }
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp),
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
                    items(group.items, key = { it.id }) { line ->
                    MobileLineRow(
                        line = line,
                        onToggle = {
                            if (line.lineCheckedAt != null) {
                                unmarkTarget = UnmarkTarget(
                                    noteId = selectedNote.id,
                                    lineId = line.id,
                                    productName = line.productName,
                                    branchName = selectedNote.destinationBranchName
                                )
                            } else {
                                onToggleCheck(selectedNote.id, line.id, false)
                            }
                        },
                        onEdit = {
                            editTarget = EditTarget(
                                noteId = selectedNote.id,
                                lineId = line.id,
                                productId = line.productId,
                                productName = line.productName,
                                branchName = selectedNote.destinationBranchName,
                                requested = line.requestedQuantity,
                                packed = line.packedQuantity,
                                existingReason = line.packedReason ?: ""
                            )
                        }
                    )
                    }
                }
            }
        }
    }

    editTarget?.let { target ->
        EditPackedQuantityDialog(
            target = target,
            donors = donorsFor(notes, target.productId, target.noteId),
            shortfall = shortfallByBranchProduct[
                "${notes.find { it.id == target.noteId }?.destinationBranchId}|${target.productId}"
            ],
            onCancel = { editTarget = null },
            onSave = { newQty, reason, donor ->
                onEditQty(target.noteId, target.lineId, newQty, reason, donor?.noteId, donor?.lineId)
                editTarget = null
            }
        )
    }
    unmarkTarget?.let { target ->
        UnmarkConfirmDialog(
            target = target,
            onCancel = { unmarkTarget = null },
            onConfirm = {
                onToggleCheck(target.noteId, target.lineId, true)
                unmarkTarget = null
            }
        )
    }
}

@Composable
private fun MobileLineRow(
    line: DispatchNoteLine,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    val checked = line.lineCheckedAt != null
    val adjusted = line.packedQuantity != line.requestedQuantity
    val noPrice = line.priceSource == PriceSourceTag.NONE && line.packedQuantity > 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                noPrice -> Color(0xFFFFEBEE)
                checked -> Color(0xFFEAF7EC)
                else -> Color.White
            }
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                if (checked) Icon(Icons.Default.CheckCircle, "Contado",
                    tint = Color(0xFF2E7D32), modifier = Modifier.size(28.dp))
                else Icon(Icons.Default.RadioButtonUnchecked, "Marcar",
                    tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dispatchLineLabel(line.productName, line.isSpecial, line.specialRawWeightGrams),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (line.isSpecial) BrandColors.AccentDeep else DeepSeaBlue
                )
                Text(
                    "Pedido: ${line.requestedQuantity.fmt(0)} ${line.productUnit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (adjusted && !line.packedReason.isNullOrBlank()) {
                    Text(
                        "Razón: ${line.packedReason}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.AccentDeep
                    )
                }
                if (noPrice) {
                    Text(
                        "Sin precio definido — bloquea el cierre.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB00020)
                    )
                }
            }
            QuantityChip(
                qty = line.packedQuantity,
                isAdjusted = adjusted,
                noPrice = noPrice,
                onClick = onEdit
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Edit, null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp))
        }
    }
}

// ── Dialogs ──────────────────────────────────────────────────────────────────

private data class EditTarget(
    val noteId: String,
    val lineId: String,
    val productId: String,
    val productName: String,
    val branchName: String,
    val requested: Double,
    val packed: Double,
    val existingReason: String
)

/**
 * Sucursal a la que se le puede restar pan para dárselo a otra.
 *
 * En empaque el pan ya salió del horno: la cantidad total es fija, así que
 * mandarle de más a una sucursal solo puede salir del canasto de otra. Este es
 * el conjunto de canastos de los que se puede sacar.
 */
private data class DonorOption(
    val noteId: String,
    val lineId: String,
    val branchName: String,
    val available: Double
)

/** Las demás sucursales del turno que llevan [productId], con lo que tienen empacado. */
private fun donorsFor(
    notes: List<DispatchNote>,
    productId: String,
    excludeNoteId: String
): List<DonorOption> = notes.mapNotNull { note ->
    if (note.id == excludeNoteId) return@mapNotNull null
    val line = note.lines.find { it.productId == productId } ?: return@mapNotNull null
    if (line.packedQuantity <= 0.0) null
    else DonorOption(note.id, line.id, note.destinationBranchName, line.packedQuantity)
}

private data class UnmarkTarget(
    val noteId: String,
    val lineId: String,
    val productName: String,
    val branchName: String
)

@Composable
private fun EditPackedQuantityDialog(
    target: EditTarget,
    donors: List<DonorOption>,
    shortfall: ShiftShortfall?,
    onCancel: () -> Unit,
    onSave: (qty: Double, reason: String?, donor: DonorOption?) -> Unit
) {
    var qtyInput by remember(target.lineId) { mutableStateOf(target.packed.fmt(0)) }
    var reason by remember(target.lineId) { mutableStateOf(target.existingReason) }
    var donor by remember(target.lineId) { mutableStateOf<DonorOption?>(null) }

    val parsed = qtyInput.toDoubleOrNull() ?: -1.0
    val isValid = parsed >= 0.0
    val differs = parsed != target.requested
    val reasonRequired = differs
    // Solo hace falta donante para lo que pase de lo que a esta sucursal le TOCABA.
    // Volver a subir después de haber bajado no le quita nada a nadie: ese pan es
    // suyo y sigue reservado mientras el empaque del turno esté abierto — pedir
    // donante ahí obligaba a robarle a otra sucursal para corregir un tecleo.
    val extra = (parsed - target.requested).coerceAtLeast(0.0)
    val needsDonor = extra > 0.0
    val donorFits = donor != null && donor!!.available >= extra
    val canSave = isValid &&
        (!reasonRequired || reason.isNotBlank()) &&
        (!needsDonor || donorFits)

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Column {
                Text("Ajustar cantidad", fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
                Text(
                    "${target.productName} → ${target.branchName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Pedido original: ${target.requested.fmt(0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Deuda de un viaje anterior del mismo día: el segundo camión todavía
                // está a tiempo de reponerla.
                if (shortfall != null && shortfall.units > 0.0) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFBF1DC), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            "Le faltaron ${shortfall.units.fmt(0)} en ${shortfall.shiftName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.AccentDeep
                        )
                        if (shortfall.reason.isNotBlank()) {
                            Text(
                                shortfall.reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandColors.AccentDeep
                            )
                        }
                        TextButton(
                            onClick = {
                                qtyInput = (target.requested + shortfall.units).fmt(0)
                                if (reason.isBlank()) {
                                    reason = "Reposición de ${shortfall.shiftName}" +
                                        if (shortfall.reason.isNotBlank()) " — ${shortfall.reason}" else ""
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "Reponerlas ahora (+${shortfall.units.fmt(0)})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = DeepSeaBlue
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = qtyInput,
                    onValueChange = { qtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Cantidad a enviar") },
                    singleLine = true,
                    isError = !isValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (needsDonor) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Estás mandando ${extra.fmt(0)} de más",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.AccentDeep
                    )
                    Text(
                        "Ese pan ya salió del horno, así que tiene que salir del envío de " +
                            "otra sucursal. ¿De cuál?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    if (donors.isEmpty()) {
                        Text(
                            "Ninguna otra sucursal lleva este producto en este turno, así que " +
                                "no hay de dónde sacarlo. Bajá la cantidad.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    donors.forEach { option ->
                        val fits = option.available >= extra
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = fits) { donor = option }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = donor?.noteId == option.noteId,
                                onClick = { donor = option },
                                enabled = fits
                            )
                            Spacer(Modifier.width(4.dp))
                            Column {
                                Text(
                                    option.branchName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (fits) DeepSeaBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (fits) "lleva ${option.available.fmt(0)} → quedaría con ${(option.available - extra).fmt(0)}"
                                    else "solo lleva ${option.available.fmt(0)}, no alcanza",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (fits) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                if (reasonRequired) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Razón del ajuste *") },
                        placeholder = { Text("La verá la encargada que recibe") },
                        singleLine = false,
                        isError = reason.isBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Si la cantidad difiere de lo pedido, escribe el motivo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.AccentDeep,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (parsed in 0.0..<target.packed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Las ${(target.packed - parsed).fmt(0)} que no se envían quedan en el " +
                            "stock de la planta.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(parsed, if (reasonRequired) reason.trim() else null, donor)
                },
                enabled = canSave
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar") }
        }
    )
}

@Composable
private fun UnmarkConfirmDialog(
    target: UnmarkTarget,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text("¿Desmarcar?", fontWeight = FontWeight.SemiBold, color = BrandColors.AccentDeep)
        },
        text = {
            Text(
                "¿Estás seguro/a que deseas desmarcar que ya contaste " +
                    "“${target.productName}” para ${target.branchName}?"
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = BrandColors.AccentDeep
                )
            ) { Text("Sí, desmarcar") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar") }
        }
    )
}

// ── Footer: cierre de turno ──────────────────────────────────────────────────

@Composable
private fun CloseTurnFooter(
    notes: List<DispatchNote>,
    isClosing: Boolean,
    onClose: () -> Unit
) {
    val ready = notes.count { n ->
        n.lines.isNotEmpty() &&
            n.lines.all { it.lineCheckedAt != null } &&
            n.lines.none { it.priceSource == PriceSourceTag.NONE && it.packedQuantity > 0 }
    }
    val total = notes.size
    var showConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$ready / $total sucursales listas",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = DeepSeaBlue
                )
                Text(
                    if (ready < total) "Las que aún no estén completas quedarán aquí."
                    else "Todas las sucursales listas para enviar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { showConfirm = true },
                enabled = ready > 0 && !isClosing
            ) {
                if (isClosing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp),
                        color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.LocalShipping, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cerrar empaque del turno", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Cerrar empaque del turno") },
            text = {
                Text(
                    "Se enviarán $ready nota(s) a la pestaña \"Listas\". " +
                        if (ready < total) "Las sucursales con cantidades pendientes seguirán en \"Por empacar\"."
                        else "Todas las sucursales pasan a \"Listas\"."
                )
            },
            confirmButton = {
                Button(onClick = { showConfirm = false; onClose() }) {
                    Text("Cerrar empaque")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun CloseResultBanner(
    res: com.bakery.erp.domain.PackingCloseResult,
    onDismiss: () -> Unit
) {
    val anyPending = res.pendingChecks + res.pendingPrices > 0
    val bg = if (anyPending) Color(0xFFFFF7E6) else Color(0xFFEAF7EC)
    val fg = if (anyPending) BrandColors.AccentDeep else Color(0xFF2E7D32)
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        colors = CardDefaults.cardColors(containerColor = bg)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (anyPending) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                null, tint = fg
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Las de la propia planta no pasan por "Listas": van directo al
                // inventario de su tienda, así que decirlas juntas confundiría a
                // quien después las busque en esa pestaña.
                val toReady = (res.promoted - res.deliveredLocally).coerceAtLeast(0)
                Text(
                    listOfNotNull(
                        "$toReady nota(s) pasaron a Listas".takeIf { toReady > 0 || res.deliveredLocally == 0 },
                        "${res.deliveredLocally} quedó(aron) en la tienda de la planta"
                            .takeIf { res.deliveredLocally > 0 }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = fg
                )
                if (anyPending) {
                    val parts = buildList {
                        if (res.pendingChecks > 0) add("${res.pendingChecks} con conteo pendiente")
                        if (res.pendingPrices > 0) add("${res.pendingPrices} con precio faltante")
                    }
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = fg
                    )
                }
            }
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
private fun EmptyHint(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.LocalShipping, null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun buildMatrixRows(notes: List<DispatchNote>): List<MatrixRow> {
    val productOrder = linkedMapOf<String, Pair<String, String>>() // productId -> (name, unit)
    val isSpecialByProduct = mutableMapOf<String, Boolean>()
    val specialWeightByProduct = mutableMapOf<String, Double>()
    val specialRecipeByProduct = mutableMapOf<String, Pair<String, String>>()
    notes.forEach { n ->
        n.lines.forEach { l ->
            productOrder.getOrPut(l.productId) { l.productName to l.productUnit }
            if (l.isSpecial) {
                isSpecialByProduct[l.productId] = true
                // El productId de un especial es sintético y único por encargo, así que
                // todas las notas que lo traigan comparten el mismo peso.
                if (l.specialRawWeightGrams > 0.0) {
                    specialWeightByProduct[l.productId] = l.specialRawWeightGrams
                }
                if (l.specialRecipeId.isNotEmpty()) {
                    specialRecipeByProduct[l.productId] = l.specialRecipeId to l.specialRecipeName
                }
            }
        }
    }
    return productOrder.entries
        .map { (pid, nameUnit) ->
            val (name, unit) = nameUnit
            val cells = mutableMapOf<String, MatrixRow.Cell>()
            notes.forEach { note ->
                val match = note.lines.firstOrNull { it.productId == pid }
                if (match != null) {
                    cells[note.id] = MatrixRow.Cell(
                        lineId = match.id,
                        requested = match.requestedQuantity,
                        packed = match.packedQuantity,
                        checked = match.lineCheckedAt != null,
                        noPrice = match.priceSource == PriceSourceTag.NONE && match.packedQuantity > 0
                    )
                }
            }
            MatrixRow(
                productId = pid,
                productName = name,
                productUnit = unit,
                isSpecial = isSpecialByProduct[pid] == true,
                specialRawWeightGrams = specialWeightByProduct[pid] ?: 0.0,
                specialRecipeId = specialRecipeByProduct[pid]?.first.orEmpty(),
                specialRecipeName = specialRecipeByProduct[pid]?.second.orEmpty(),
                cellsByNote = cells
            )
        }
        .sortedBy { it.productName.lowercase() }
}

internal fun formatDayLabel(millis: Long): String {
    if (millis == 0L) return "—"
    val tz = TimeZone.currentSystemDefault()
    val ld: LocalDateTime = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)
    val wd = when (ld.date.dayOfWeek) {
        kotlinx.datetime.DayOfWeek.MONDAY    -> "lunes"
        kotlinx.datetime.DayOfWeek.TUESDAY   -> "martes"
        kotlinx.datetime.DayOfWeek.WEDNESDAY -> "miércoles"
        kotlinx.datetime.DayOfWeek.THURSDAY  -> "jueves"
        kotlinx.datetime.DayOfWeek.FRIDAY    -> "viernes"
        kotlinx.datetime.DayOfWeek.SATURDAY  -> "sábado"
        kotlinx.datetime.DayOfWeek.SUNDAY    -> "domingo"
        else -> ""
    }
    val monthNames = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
    val mn = monthNames[(ld.monthNumber - 1).coerceIn(0, 11)]
    return "$wd ${ld.dayOfMonth} de $mn"
}
