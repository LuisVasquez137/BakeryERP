package com.bakery.erp.presentation.delivery

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakery.erp.model.BoardStage
import com.bakery.erp.model.BoardViewer
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.DispatchNote
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.model.Permission
import com.bakery.erp.model.StopProgress
import com.bakery.erp.model.User
import com.bakery.erp.model.UserRole
import com.bakery.erp.model.boardStage
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.etaLabel
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.isMyTurn
import com.bakery.erp.model.orderStopsByRoute
import com.bakery.erp.model.packedFraction
import com.bakery.erp.model.sortForBoard
import com.bakery.erp.model.tripTitle
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.dispatch.DayNavigator
import com.bakery.erp.presentation.dispatch.DispatchViewModel
import com.bakery.erp.ui.theme.BrandColors
import kotlinx.coroutines.delay
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Clock

/**
 * Reparto: el tablero de todos los pedidos del día, por viaje. Ver `model/DeliveryBoard.kt`.
 *
 * Lo ven la planta (qué falta cargar, despachar el viaje), las sucursales (cómo viene su
 * pan; firmar la llegada y contar), el repartidor (sus paradas) y el dueño (todo, sin que
 * nada le lata). Despacho queda solo para empacar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryBoardScreen(
    onNavigateBack: () -> Unit,
    onOpenReceive: (noteId: String) -> Unit,
    onOpenDetail: (noteId: String) -> Unit,
    onOpenPacking: () -> Unit,
    onOpenRun: (runId: String) -> Unit,
    onOpenHistory: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: DeliveryViewModel,
    dispatchViewModel: DispatchViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(companyId) { if (companyId.isNotEmpty()) viewModel.observeBoard(companyId) }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() } }
    LaunchedEffect(state.boardMessage) { state.boardMessage?.let { snackbar.showSnackbar(it); viewModel.clearBoardMessage() } }

    // El camión avanza con el reloj, no con Firestore: sin este tic quedaría quieto
    // entre cambio y cambio de las notas.
    var now by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = Clock.System.now().toEpochMilliseconds() } }

    val viewer = remember(user) {
        BoardViewer(
            supervision = user?.role == UserRole.OWNER,
            canManageRuns = user?.hasPermission(Permission.MANAGE_DELIVERY_RUNS) == true,
            canReceiveAt = { id -> user?.hasPermission(Permission.RECEIVE_DISPATCH) == true && user.canAccessBranch(id) }
        )
    }
    val visible = remember(state.boardNotes, state.runs, user) {
        if (user == null) emptyList() else state.boardNotes.filter { n ->
            user.canAccessBranch(n.originBranchId) || user.canAccessBranch(n.destinationBranchId) ||
                state.runs.any { r -> r.driverUserId == user.id && n.id in r.dispatchNoteIds }
        }
    }
    var stageFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showRouteDialog by remember { mutableStateOf(false) }
    val plants = remember(state.boardBranches, user) {
        state.boardBranches.values.filter {
            it.capabilities.contains(BranchCapability.CAN_PRODUCE) && user?.canAccessBranch(it.id) == true
        }.sortedBy { it.name }
    }
    val canManageRoutes = viewer.canManageRuns && plants.isNotEmpty()

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Reparto", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    if (canManageRoutes) {
                        IconButton(onClick = { showRouteDialog = true }) { Icon(Icons.Default.AltRoute, "Ruta del camión") }
                    }
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Default.History, "Historial de hojas") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = BrandColors.Ivory
    ) { padding ->
        val filtered = visible.filter { stageFilter == null || it.boardStage().name == stageFilter }
        val sections = remember(filtered, state.boardTripByShift, state.boardBranches) {
            filtered.groupBy { it.originBranchId to viewModel.tripOf(it) }
                .toList()
                .sortedWith(compareBy({ state.boardBranches[it.first.first]?.name.orEmpty() }, { if (it.first.second == 0) 9 else it.first.second }))
        }
        val multiPlant = sections.map { it.first.first }.distinct().size > 1
        var openReceived by rememberSaveable { mutableStateOf(setOf<String>()) }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DayNavigator(
                    dayMillis = state.dayAnchorMillis,
                    onPrev = { viewModel.shiftBoardDay(companyId, -1) },
                    onNext = { viewModel.shiftBoardDay(companyId, +1) },
                    onToday = { viewModel.boardToday(companyId) },
                    onPickDay = { viewModel.boardToDay(companyId, it) },
                    compact = true
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SummaryRow(visible, stageFilter) { stageFilter = if (stageFilter == it) null else it }
            }
            if (state.isBoardLoading && visible.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else if (sections.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (stageFilter != null) "Nada en este filtro." else "No hay pedidos para este día.",
                        color = BrandColors.MutedSlate,
                        modifier = Modifier.fillMaxWidth().padding(36.dp)
                    )
                }
            }
            sections.forEach { (key, notes) ->
                val (plantId, trip) = key
                val sectionKey = "$plantId-$trip"
                val sectionRuns = state.runs.filter { r ->
                    r.originBranchId == plantId && r.status != com.bakery.erp.model.DeliveryRunStatus.CANCELLED &&
                        r.dispatchNoteIds.any { id -> notes.any { it.id == id } || visible.any { v -> v.id == id && viewModel.tripOf(v) == trip } }
                }
                val readyToLoad = notes.filter {
                    it.boardStage() == BoardStage.READY && it.originBranchId != it.destinationBranchId
                }
                val canDispatch = viewer.canManageRuns && user?.canAccessBranch(plantId) == true && readyToLoad.isNotEmpty()
                item(key = "h-$sectionKey", span = { GridItemSpan(maxLineSpan) }) {
                    TripHeader(
                        title = tripTitle(trip),
                        plantName = if (multiPlant) state.boardBranches[plantId]?.name.orEmpty() else "",
                        runs = sectionRuns,
                        notesById = state.boardNotes.associateBy { it.id },
                        readyCount = readyToLoad.size,
                        showReadyHint = viewer.canManageRuns,
                        canDispatch = canDispatch,
                        onDispatch = { viewModel.openDispatchSheet(companyId, plantId, trip) },
                        onOpenRun = onOpenRun
                    )
                }
                val active = sortForBoard(notes.filter { it.boardStage() !in setOf(BoardStage.RECEIVED, BoardStage.CANCELLED) }, viewer)
                val closed = notes.filter { it.boardStage() in setOf(BoardStage.RECEIVED, BoardStage.CANCELLED) }
                    .sortedBy { if (it.status == DispatchStatus.CANCELLED) 1 else 0 }
                items(active, key = { it.id }) { note ->
                    BoardCard(
                        note = note,
                        mine = viewer.isMyTurn(note),
                        viewer = viewer,
                        held = isHeld(note, state, viewModel),
                        progress = viewModel.stopProgressOf(note, now),
                        nowMillis = now,
                        onClick = {
                            if (canSeeChain(user, note)) viewModel.openNoteChain(companyId, note.id)
                            else routeTap(note, user, dispatchViewModel, onOpenReceive, onOpenDetail, onOpenPacking)
                        }
                    )
                }
                if (closed.isNotEmpty()) {
                    val open = sectionKey in openReceived || stageFilter == BoardStage.RECEIVED.name
                    item(key = "c-$sectionKey", span = { GridItemSpan(maxLineSpan) }) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                openReceived = if (sectionKey in openReceived) openReceived - sectionKey else openReceived + sectionKey
                            }
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Recibidas y cerradas (${closed.size})",
                                    color = BrandColors.Success,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = BrandColors.Success)
                            }
                        }
                    }
                    if (open) {
                        items(closed, key = { it.id }) { note ->
                            BoardCard(
                                note = note, mine = false, viewer = viewer, held = false,
                                progress = null, nowMillis = now,
                                onClick = {
                                    if (canSeeChain(user, note)) viewModel.openNoteChain(companyId, note.id)
                                    else onOpenDetail(note.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    state.dispatchSheet?.let { sheet ->
        val candidates = remember(sheet, state.boardNotes, state.boardBranches) {
            val plant = state.boardBranches[sheet.plantId]
            orderStopsByRoute(
                visible.filter {
                    it.originBranchId == sheet.plantId && viewModel.tripOf(it) == sheet.trip &&
                        it.originBranchId != it.destinationBranchId &&
                        it.boardStage() in setOf(BoardStage.READY, BoardStage.PACKING)
                },
                plant?.deliveryRouteOrder.orEmpty()
            )
        }
        DispatchTripSheet(
            sheet = sheet,
            candidates = candidates,
            isHeld = { isHeld(it, state, viewModel) },
            isSaving = state.isDispatching,
            onDismiss = { viewModel.closeDispatchSheet() },
            onConfirm = { ids, driver, vehicle ->
                user?.let { viewModel.dispatchTrip(companyId, it, sheet.trip, ids, driver, vehicle) }
            }
        )
    }

    state.noteChain?.let { chain ->
        NoteChainSheet(
            chain = chain,
            onDismiss = { viewModel.closeNoteChain() },
            onOpenDetail = { viewModel.closeNoteChain(); onOpenDetail(chain.note.id) }
        )
    }

    if (showRouteDialog) {
        DeliveryRouteDialog(
            plants = plants,
            allBranches = state.boardBranches.values.filter { it.active }.sortedBy { it.name },
            onDismiss = { showRouteDialog = false },
            onSave = { plantId, order -> viewModel.saveDeliveryRoute(companyId, plantId, order); showRouteDialog = false }
        )
    }
}

/**
 * Quién ve la cadena de una entrega (ver `NoteChainSheet`): el dueño, y quien maneja la
 * planta que la mandó. No la encargada de la sucursal: ver quién perdió su pan genera
 * roces que no ayudan a encontrarlo. Una nota todavía en empaque no tiene cadena que
 * mirar: la planta va a la matriz, como siempre.
 */
private fun canSeeChain(user: User?, note: DispatchNote): Boolean {
    user ?: return false
    if (note.boardStage() == BoardStage.PACKING) return false
    return user.role == UserRole.OWNER ||
        (user.hasPermission(Permission.MANAGE_PRODUCTION_LOTS) && user.canAccessBranch(note.originBranchId))
}

/** La nota de un turno que sale con el otro viaje (Pacajá, pan de noche). */
private fun isHeld(note: DispatchNote, state: DeliveryUiState, viewModel: DeliveryViewModel): Boolean {
    val shiftTrip = state.boardTripByShift[note.shiftId] ?: 0
    return shiftTrip != 0 && viewModel.tripOf(note) != shiftTrip
}

private fun routeTap(
    note: DispatchNote,
    user: User?,
    dispatchViewModel: DispatchViewModel,
    onOpenReceive: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenPacking: () -> Unit
) {
    user ?: return
    when (note.boardStage()) {
        BoardStage.PACKING ->
            if (user.hasPermission(Permission.DISPATCH_PACK) && user.canAccessBranch(note.originBranchId)) {
                dispatchViewModel.focusPacking(note.originBranchId, note.shiftId, note.productionDate, note.destinationBranchId)
                onOpenPacking()
            } else onOpenDetail(note.id)
        BoardStage.IN_TRANSIT, BoardStage.TO_COUNT ->
            if (user.hasPermission(Permission.RECEIVE_DISPATCH) && user.canAccessBranch(note.destinationBranchId)) onOpenReceive(note.id)
            else onOpenDetail(note.id)
        else -> onOpenDetail(note.id)
    }
}

// ── Estilo por etapa ─────────────────────────────────────────────────────────

private data class StageStyle(
    val label: String,
    val chip: Color,
    val onChip: Color,
    val background: Color,
    val border: Color,
    val icon: ImageVector
)

/**
 * Chip siempre relleno. **Excepción de paleta aprobada por el dueño (oct 2026):** "Llegó ·
 * falta contar" lleva letra BLANCA sobre el naranja vivo. Contra la regla de BrandColors
 * (blanco sobre Accent da 2.6:1) se eligió porque es el único chip que tiene que gritar, y
 * se acotó a esta pantalla: el resto de la app sigue con OnAccent sobre Accent.
 */
private fun styleOf(stage: BoardStage, note: DispatchNote): StageStyle = when (stage) {
    BoardStage.PACKING -> StageStyle("En empaque", BrandColors.MutedSlate, Color.White,
        BrandColors.MutedSlate.copy(alpha = .07f), BrandColors.MutedSlate.copy(alpha = .25f), Icons.Default.Inventory2)
    BoardStage.READY -> StageStyle("Lista para salir", BrandColors.Primary, Color.White,
        BrandColors.PrimaryContainer, BrandColors.Primary.copy(alpha = .3f), Icons.Default.Verified)
    BoardStage.IN_TRANSIT -> StageStyle("En camino", BrandColors.Teal, Color.White,
        Color.White, BrandColors.Teal.copy(alpha = .4f), Icons.Default.LocalShipping)
    BoardStage.TO_COUNT -> StageStyle("Llegó · falta contar", BrandColors.Accent, Color.White,
        BrandColors.AccentSoft, BrandColors.Accent, Icons.Default.FactCheck)
    BoardStage.RECEIVED -> StageStyle(
        if (note.hasDiscrepancy) "Recibida" else "Recibida", BrandColors.Success, Color.White,
        BrandColors.SuccessSoft, BrandColors.Success.copy(alpha = .3f), Icons.Default.CheckCircle)
    BoardStage.CANCELLED -> StageStyle("Cancelada", BrandColors.MutedSlate.copy(alpha = .6f), Color.White,
        BrandColors.Sand, BrandColors.Outline.copy(alpha = .5f), Icons.Default.Cancel)
}

@Composable
private fun SummaryRow(notes: List<DispatchNote>, selected: String?, onSelect: (String) -> Unit) {
    val counts = notes.groupingBy { it.boardStage() }.eachCount()
    val order = listOf(BoardStage.TO_COUNT, BoardStage.READY, BoardStage.IN_TRANSIT, BoardStage.PACKING, BoardStage.RECEIVED)
    val labels = mapOf(
        BoardStage.TO_COUNT to "por contar", BoardStage.READY to "listas para salir",
        BoardStage.IN_TRANSIT to "en camino", BoardStage.PACKING to "en empaque", BoardStage.RECEIVED to "recibidas"
    )
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        order.filter { (counts[it] ?: 0) > 0 }.forEach { stage ->
            val st = styleOf(stage, DispatchNote())
            val isSel = selected == stage.name
            Surface(
                color = st.chip,
                shape = RoundedCornerShape(10.dp),
                border = if (isSel) BorderStroke(2.dp, BrandColors.TextSlate) else null,
                modifier = Modifier.clickable { onSelect(stage.name) }
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.Bottom) {
                    Text("${counts[stage]}", color = st.onChip, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(labels.getValue(stage), color = st.onChip, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ── Encabezado de viaje ──────────────────────────────────────────────────────

@Composable
private fun TripHeader(
    title: String,
    plantName: String,
    runs: List<com.bakery.erp.model.DeliveryRun>,
    notesById: Map<String, DispatchNote>,
    readyCount: Int,
    showReadyHint: Boolean,
    canDispatch: Boolean,
    onDispatch: () -> Unit,
    onOpenRun: (String) -> Unit
) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BrandColors.Sand)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        listOf(title, plantName).filter { it.isNotBlank() }.joinToString(" · "),
                        fontWeight = FontWeight.Bold, color = BrandColors.PrimaryDark,
                        style = MaterialTheme.typography.titleSmall
                    )
                    val meta = when {
                        showReadyHint && readyCount > 0 -> "$readyCount lista${if (readyCount > 1) "s" else ""} sin camión"
                        runs.isEmpty() -> "Todavía no sale"
                        else -> null
                    }
                    meta?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate) }
                }
                if (canDispatch) {
                    Button(
                        onClick = onDispatch,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.LocalShipping, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Despachar ${title.lowercase()}", fontWeight = FontWeight.Bold)
                    }
                }
            }
            runs.forEach { run ->
                val stops = run.dispatchNoteIds.mapNotNull { notesById[it] }
                val done = stops.count { it.boardStage() == BoardStage.RECEIVED || it.boardStage() == BoardStage.TO_COUNT }
                Surface(
                    color = BrandColors.Sand, shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenRun(run.id) }
                ) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalShipping, null, tint = BrandColors.Teal, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            listOfNotNull(
                                run.driverName.ifBlank { null },
                                run.vehicleInfo?.substringBefore(" · ")?.ifBlank { null },
                                run.startedAt?.let { "salió ${clockLabel(it)}" }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        val frac = if (stops.isEmpty()) 0f else done.toFloat() / stops.size
                        Box(Modifier.width(54.dp).height(6.dp).clip(CircleShape).background(BrandColors.RoadTrack)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(BrandColors.Success))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("$done/${stops.size}", style = MaterialTheme.typography.labelMedium, color = BrandColors.MutedSlate)
                    }
                }
            }
        }
    }
}

// ── Tarjeta ──────────────────────────────────────────────────────────────────

@Composable
private fun BoardCard(
    note: DispatchNote,
    mine: Boolean,
    viewer: BoardViewer,
    held: Boolean,
    progress: StopProgress?,
    nowMillis: Long,
    onClick: () -> Unit
) {
    val stage = note.boardStage()
    val st = styleOf(stage, note)
    val infinite = rememberInfiniteTransition(label = "card")
    val pulse by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1900, easing = LinearEasing)), label = "pulse")
    val ringColor = when (stage) { BoardStage.TO_COUNT -> BrandColors.Accent; else -> BrandColors.Primary }
    val shape = RoundedCornerShape(16.dp)

    Box(
        Modifier.fillMaxWidth().drawBehind {
            // Solo late lo que te toca: un anillo que crece y se apaga.
            if (mine) {
                val grow = 14.dp.toPx() * pulse
                drawRoundRect(
                    color = ringColor.copy(alpha = (1f - pulse) * .55f),
                    topLeft = Offset(-grow, -grow),
                    size = Size(size.width + grow * 2, size.height + grow * 2),
                    cornerRadius = CornerRadius(16.dp.toPx() + grow),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
    ) {
        Surface(
            color = st.background,
            shape = shape,
            border = BorderStroke(if (stage == BoardStage.TO_COUNT) 2.dp else 1.5.dp, st.border),
            modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick)
        ) {
            Box {
                if (mine && stage == BoardStage.TO_COUNT) Sweep(Modifier.matchParentSize())
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val wiggle = if (mine && stage == BoardStage.TO_COUNT) wiggleAngle(infinite) else 0f
                        Box(
                            Modifier.size(40.dp).graphicsLayer { rotationZ = wiggle }
                                .clip(RoundedCornerShape(12.dp)).background(st.chip),
                            contentAlignment = Alignment.Center
                        ) { Icon(st.icon, null, tint = st.onChip, modifier = Modifier.size(22.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(note.destinationBranchName, fontWeight = FontWeight.Bold, color = BrandColors.PrimaryDark,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                note.shiftName.ifBlank { "Sin turno" } + if (note.originBranchId == note.destinationBranchId) " · la planta" else "",
                                style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate
                            )
                        }
                        Surface(color = st.chip, shape = CircleShape) {
                            Text(st.label, color = st.onChip, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }

                    when (stage) {
                        BoardStage.PACKING -> PackingBar(note)
                        BoardStage.IN_TRANSIT -> MiniRoad(note, progress)
                        BoardStage.TO_COUNT -> ToCount(note, nowMillis, animate = mine)
                        else -> Steps(stage, st.chip)
                    }

                    detailFor(note, stage)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextSlate)
                    }
                    if (held) {
                        Surface(color = BrandColors.AccentSoft, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                "Pan de ${note.shiftName.lowercase()} guardado: sale con este viaje",
                                style = MaterialTheme.typography.labelMedium, color = BrandColors.AccentDeep,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                    if (stage == BoardStage.RECEIVED && note.hasDiscrepancy) {
                        Surface(color = BrandColors.DangerSoft, shape = RoundedCornerShape(8.dp)) {
                            Text("Con diferencias", color = BrandColors.Danger, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                        }
                    }
                    hintFor(note, stage, viewer)?.let {
                        Text("$it ›", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                            color = if (stage == BoardStage.TO_COUNT) BrandColors.AccentDeep else BrandColors.Primary)
                    }
                }
            }
        }
    }
}

private fun detailFor(note: DispatchNote, stage: BoardStage): String? = when (stage) {
    BoardStage.READY -> if (note.originBranchId == note.destinationBranchId) "Se queda en el mostrador de la planta"
        else listOfNotNull(
            "${note.lineCount} productos",
            note.basketsSent.takeIf { it > 0 }?.let { "$it canasta${if (it == 1) "" else "s"}" },
            "espera camión"
        ).joinToString(" · ")
    BoardStage.RECEIVED -> note.receivedAt?.let { "Recibida ${clockLabel(it)}" + if (note.hasDiscrepancy) "" else " · todo cuadró" }
    BoardStage.CANCELLED -> note.cancelReason?.takeIf { it.isNotBlank() } ?: "Cancelada"
    else -> null
}

private fun hintFor(note: DispatchNote, stage: BoardStage, viewer: BoardViewer): String? {
    val receives = viewer.canReceiveAt(note.destinationBranchId) && !viewer.supervision
    return when {
        stage == BoardStage.TO_COUNT && receives -> "Toca para contar"
        stage == BoardStage.IN_TRANSIT && receives -> "Cuando llegue el camión, toca para firmar"
        stage == BoardStage.PACKING && receives && !viewer.canManageRuns -> "La planta lo está preparando"
        stage == BoardStage.READY && viewer.canManageRuns && !viewer.supervision &&
            note.originBranchId != note.destinationBranchId -> "Falta cargarla en un camión"
        else -> null
    }
}

@Composable
private fun wiggleAngle(infinite: androidx.compose.animation.core.InfiniteTransition): Float {
    val t by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "wiggle")
    // Quieto el 70% del ciclo y una sacudida corta al final: llama la atención sin marear.
    if (t < .7f) return 0f
    val k = (t - .7f) / .3f
    return (sin(k * PI * 4).toFloat()) * 14f * (1f - k)
}

@Composable
private fun Sweep(modifier: Modifier) {
    val infinite = rememberInfiniteTransition(label = "sweep")
    val x by infinite.animateFloat(-0.5f, 1.6f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing)), label = "x")
    Canvas(modifier) {
        val w = size.width * .4f
        val left = size.width * x - w
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = .7f), Color.Transparent),
                startX = left, endX = left + w
            ),
            topLeft = Offset(left, 0f), size = Size(w, size.height)
        )
    }
}

@Composable
private fun Steps(stage: BoardStage, color: Color) {
    val reached = when (stage) {
        BoardStage.PACKING -> 0; BoardStage.READY -> 1; BoardStage.IN_TRANSIT -> 2
        BoardStage.TO_COUNT -> 3; BoardStage.RECEIVED -> 4; BoardStage.CANCELLED -> -1
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { i ->
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape)
                .background(if (i <= reached) color else BrandColors.Sand))
        }
    }
}

// ── En empaque: una barra que crece con los panes empacados ─────────────────

@Composable
private fun PackingBar(note: DispatchNote) {
    val fraction = note.packedFraction()
    val animated by animateFloatAsState(
        (fraction ?: 0.0).toFloat().coerceAtLeast(.03f), tween(900, easing = FastOutSlowInEasing), label = "pack"
    )
    val infinite = rememberInfiniteTransition(label = "stripes")
    val shift by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "shift")
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row {
            Text("Empacando en la planta", style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate,
                modifier = Modifier.weight(1f))
            if (fraction != null) {
                Text(
                    "${note.packedUnits.toInt()} de ${note.plannedUnits.toInt()} panes",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = BrandColors.TextSlate
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp))) {
            drawRect(BrandColors.MutedSlate.copy(alpha = .15f))
            val w = size.width * animated
            drawRoundRect(BrandColors.MutedSlate, size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2))
            clipRect(right = w) {
                val step = 12.dp.toPx()
                var x = -size.height + step * shift - step
                while (x < w + step) {
                    val p = Path().apply {
                        moveTo(x, size.height); lineTo(x + size.height, 0f)
                        lineTo(x + size.height + step / 2, 0f); lineTo(x + step / 2, size.height); close()
                    }
                    drawPath(p, Color.White.copy(alpha = .3f))
                    x += step
                }
            }
        }
    }
}

// ── En camino: carretera mínima, camión blanco y pin al fondo ───────────────

@Composable
private fun MiniRoad(note: DispatchNote, progress: StopProgress?) {
    val fraction = (progress?.fraction ?: 0.0).toFloat()
    val infinite = rememberInfiniteTransition(label = "road")
    val dash by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(600, easing = LinearEasing)), label = "dash")
    val wheel by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(500, easing = LinearEasing)), label = "wheel")
    val bob by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "bob")
    val ground by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "ground")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(note.originBranchName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = BrandColors.Teal, modifier = Modifier.weight(1f))
            Text(note.destinationBranchName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = BrandColors.Teal)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(54.dp)) {
            val travel = (maxWidth - 72.dp).coerceAtLeast(0.dp)
            val truckX by animateDpAsState(travel * fraction, tween(1200), label = "truckX")
            // Carretera: lo recorrido en teal, la línea punteada corriendo.
            Canvas(Modifier.fillMaxWidth().padding(end = 18.dp).height(10.dp).align(Alignment.BottomStart).offset(y = (-6).dp)
                .clip(RoundedCornerShape(10.dp))) {
                drawRect(BrandColors.RoadTrack)
                drawRect(BrandColors.Teal, size = Size(size.width * fraction.coerceAtLeast(.02f), size.height))
                val seg = 20.dp.toPx(); val on = 10.dp.toPx()
                var x = -seg * dash
                while (x < size.width) {
                    drawRect(Color.White.copy(alpha = .9f), topLeft = Offset(x, size.height / 2 - 1.dp.toPx()), size = Size(on, 2.dp.toPx()))
                    x += seg
                }
            }
            // Pin grande al fondo, con un pulso en la base: ahí espera la sucursal.
            Canvas(Modifier.size(width = 28.dp, height = 10.dp).align(Alignment.BottomEnd).offset(x = (-3).dp, y = (-1).dp)) {
                val s = .6f + ground * 1.1f
                drawOval(
                    BrandColors.Teal.copy(alpha = .35f * (1f - ground)),
                    topLeft = Offset(size.width / 2 - size.width * s / 2, size.height / 2 - size.height * s / 2),
                    size = Size(size.width * s, size.height * s)
                )
            }
            Icon(Icons.Default.Place, null, tint = BrandColors.Teal,
                modifier = Modifier.size(40.dp).align(Alignment.BottomEnd).offset(x = 3.dp, y = (-6).dp))
            Canvas(Modifier.size(width = 54.dp, height = 32.dp).align(Alignment.BottomStart)
                .offset(x = truckX, y = (-10).dp - (1.5f * bob).dp)) {
                drawWhiteTruck(wheel)
            }
        }
        progress?.let {
            Text(it.etaLabel(), style = MaterialTheme.typography.labelMedium, color = BrandColors.Teal, fontWeight = FontWeight.Medium)
        }
    }
}

/** Camión blanco de trazo simple, en coordenadas de 54×32 escaladas al tamaño del Canvas. */
private fun DrawScope.drawWhiteTruck(wheelDeg: Float) {
    val k = size.width / 54f
    fun o(x: Float, y: Float) = Offset(x * k, y * k)
    val ink = BrandColors.PrimaryDark
    val stroke = Stroke(width = 1.6f * k, join = StrokeJoin.Round)
    // Caja
    drawRoundRect(Color.White, topLeft = o(1.5f, 3f), size = Size(31f * k, 19f * k), cornerRadius = CornerRadius(3f * k))
    drawRoundRect(ink, topLeft = o(1.5f, 3f), size = Size(31f * k, 19f * k), cornerRadius = CornerRadius(3f * k), style = stroke)
    // Cabina
    val cab = Path().apply {
        moveTo(32.5f * k, 9f * k); lineTo(41f * k, 9f * k); lineTo(47.5f * k, 16f * k)
        lineTo(47.5f * k, 22f * k); lineTo(32.5f * k, 22f * k); close()
    }
    drawPath(cab, Color.White); drawPath(cab, ink, style = stroke)
    val window = Path().apply {
        moveTo(35f * k, 11.5f * k); lineTo(40f * k, 11.5f * k); lineTo(44.2f * k, 16f * k); lineTo(35f * k, 16f * k); close()
    }
    drawPath(window, BrandColors.PrimaryContainer)
    drawRoundRect(BrandColors.Accent, topLeft = o(45.5f, 18f), size = Size(2.4f * k, 2.2f * k), cornerRadius = CornerRadius(.8f * k))
    // Ruedas que giran
    listOf(10f, 40f).forEach { cx ->
        val c = o(cx, 25f)
        drawCircle(ink, radius = 4.6f * k, center = c)
        drawCircle(Color.White, radius = 1.7f * k, center = c)
        rotate(wheelDeg, pivot = c) {
            drawLine(Color.White.copy(alpha = .8f), Offset(c.x, c.y - 4f * k), Offset(c.x, c.y - 2.2f * k), strokeWidth = 1f * k)
        }
    }
}

// ── Llegó, falta contar ──────────────────────────────────────────────────────

@Composable
private fun ToCount(note: DispatchNote, nowMillis: Long, animate: Boolean) {
    val infinite = rememberInfiniteTransition(label = "count")
    val wave by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "wave")
    val blink by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "blink")
    val shown = note.lineCount.coerceIn(1, 6)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(34.dp)) {
        repeat(shown) { i ->
            // Ola: cada caja salta un poco después que la anterior.
            val phase = ((wave - i * .09f) % 1f + 1f) % 1f
            val lift = if (!animate) 0f else if (phase < .25f) sin(phase / .25f * PI).toFloat() * 9f else 0f
            Box(
                Modifier.offset(y = (-lift).dp).size(width = 26.dp, height = 22.dp)
                    .clip(RoundedCornerShape(5.dp)).background(BrandColors.Accent),
                contentAlignment = Alignment.Center
            ) { Text("?", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp) }
        }
        if (note.lineCount > 6) {
            Text("+${note.lineCount - 6}", color = BrandColors.AccentDeep, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape)
            .background(if (!animate || blink < .5f) BrandColors.Accent else Color.Transparent))
        Spacer(Modifier.width(6.dp))
        val ago = note.arrivedAt?.let { ((nowMillis - it) / 60_000).coerceAtLeast(0) }
        Text(
            "${note.lineCount} productos esperando conteo" + (ago?.let { " · llegó hace $it min" } ?: ""),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = BrandColors.AccentDeep
        )
    }
}

// ── Hoja "Despachar viaje" ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DispatchTripSheet(
    sheet: DispatchSheetState,
    candidates: List<DispatchNote>,
    isHeld: (DispatchNote) -> Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (noteIds: List<String>, driver: User, vehicle: com.bakery.erp.model.FixedAsset?) -> Unit
) {
    val ready = candidates.filter { it.boardStage() == BoardStage.READY }
    // De entrada va todo lo listo: el caso normal es un camión por viaje.
    var selected by remember(sheet) { mutableStateOf(ready.map { it.id }.toSet()) }
    var driverId by remember(sheet) { mutableStateOf(sheet.suggestedDriverId) }
    var vehicleId by remember(sheet) { mutableStateOf(sheet.suggestedVehicleId) }
    val driver = sheet.drivers.find { it.id == driverId }
    val vehicle = sheet.vehicles.find { it.id == vehicleId }
    val needsVehicle = sheet.vehicles.isNotEmpty()
    val canGo = selected.isNotEmpty() && driver != null && (!needsVehicle || vehicle != null) && !isSaving

    ModalBottomSheet(onDismissRequest = { if (!isSaving) onDismiss() }, containerColor = BrandColors.Ivory) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Hoja de reparto", style = MaterialTheme.typography.labelMedium, color = BrandColors.MutedSlate)
            Text("Despachar el ${tripTitle(sheet.trip)}", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = BrandColors.PrimaryDark)
            Text("Ya vienen marcadas todas las que están listas, en el orden de la ruta. Desmarcá las que no suben a este camión.",
                style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)

            Text("Repartidor", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (sheet.drivers.isEmpty()) Text("Nadie de esta planta tiene permiso de repartidor.", color = BrandColors.Danger,
                style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sheet.drivers.forEach { d ->
                    FilterChip(selected = d.id == driverId, onClick = { driverId = d.id }, label = { Text(d.displayName) })
                }
            }
            if (needsVehicle) {
                Text("Vehículo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sheet.vehicles.forEach { v ->
                        FilterChip(selected = v.id == vehicleId, onClick = { vehicleId = v.id }, label = { Text(vehicleDisplayName(v)) })
                    }
                }
            }

            Text("Paradas", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            candidates.forEachIndexed { i, n ->
                val isReady = n.boardStage() == BoardStage.READY
                val checked = n.id in selected
                Surface(
                    color = Color.White, shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, if (checked) BrandColors.Primary else Color.Transparent),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = isReady) {
                        selected = if (checked) selected - n.id else selected + n.id
                    }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null,
                            tint = if (isReady) BrandColors.Primary else BrandColors.MutedSlate.copy(alpha = .4f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${i + 1}. ${n.destinationBranchName}", fontWeight = FontWeight.Medium,
                                color = if (isReady) BrandColors.TextSlate else BrandColors.MutedSlate)
                            Text(
                                when {
                                    !isReady -> "Todavía en empaque: no puede subir"
                                    isHeld(n) -> "Pan de ${n.shiftName.lowercase()} guardado para este viaje"
                                    else -> listOfNotNull(
                                        "${n.lineCount} productos",
                                        n.basketsSent.takeIf { it > 0 }?.let { b -> "$b canasta${if (b == 1) "" else "s"}" }
                                    ).joinToString(" · ")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isReady && isHeld(n)) BrandColors.AccentDeep else BrandColors.MutedSlate
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { driver?.let { onConfirm(candidates.filter { it.id in selected }.map { it.id }, it, vehicle) } },
                enabled = canGo,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Salir con ${selected.size} parada${if (selected.size == 1) "" else "s"}", fontWeight = FontWeight.Bold)
            }
            Text("Si falta una sucursal por empacar, se despacha después en otra salida.",
                style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
        }
    }
}

// ── Ruta fija ────────────────────────────────────────────────────────────────

/**
 * Orden de entrega del camión de cada planta. Se configura una vez; de ahí sale el orden
 * de las paradas al despachar y cuánto le falta al repartidor a cada sucursal. Botones de
 * subir/bajar y no arrastre: se usa en tablet con las manos ocupadas, igual que el orden
 * de productos del consolidado.
 */
@Composable
private fun DeliveryRouteDialog(
    plants: List<com.bakery.erp.model.Branch>,
    allBranches: List<com.bakery.erp.model.Branch>,
    onDismiss: () -> Unit,
    onSave: (plantId: String, order: List<String>) -> Unit
) {
    var plantId by remember { mutableStateOf(plants.first().id) }
    val plant = plants.first { it.id == plantId }
    var order by remember(plantId) {
        val candidates = allBranches.filter { it.id != plantId && it.capabilities.contains(BranchCapability.CAN_SELL) }
        val idx = plant.deliveryRouteOrder.withIndex().associate { (i, id) -> id to i }
        mutableStateOf(candidates.sortedWith(compareBy({ idx[it.id] ?: Int.MAX_VALUE }, { it.name })).map { it.id })
    }
    val names = allBranches.associate { it.id to it.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ruta del camión") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (plants.size > 1) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        plants.forEach { p -> FilterChip(selected = p.id == plantId, onClick = { plantId = p.id }, label = { Text(p.name) }) }
                    }
                }
                Text("En el orden en que el camión entrega, saliendo de ${plant.name}.",
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                order.forEachIndexed { i, id ->
                    Surface(color = Color.White, shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}. ${names[id].orEmpty()}", modifier = Modifier.weight(1f))
                            IconButton(onClick = { if (i > 0) order = order.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) } }, enabled = i > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, "Subir")
                            }
                            IconButton(onClick = { if (i < order.lastIndex) order = order.toMutableList().also { l -> l.add(i + 1, l.removeAt(i)) } }, enabled = i < order.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, "Bajar")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(plantId, order) }) { Text("Guardar ruta") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun clockLabel(millis: Long): String {
    val t = kotlin.time.Instant.fromEpochMilliseconds(millis).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
    return "$h12:${t.minute.toString().padStart(2, '0')} ${if (t.hour < 12) "AM" else "PM"}"
}
