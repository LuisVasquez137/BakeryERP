package com.bakery.erp.presentation.dispatch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.DispatchNote
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.model.Permission
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.production.ProductionViewModel
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
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone


private data class TabSpec(val label: String, val statuses: Set<String>)

private val DISPATCH_TABS = listOf(
    TabSpec("Por empacar",     setOf(DispatchStatus.DRAFT)),
    TabSpec("Listas",          setOf(DispatchStatus.READY)),
    TabSpec("En tránsito",     setOf(DispatchStatus.IN_TRANSIT)),
    TabSpec("Pendiente conteo", setOf(DispatchStatus.ARRIVED)),
    TabSpec("Recibidas",       setOf(DispatchStatus.DELIVERED, DispatchStatus.DELIVERED_WITH_DISCREPANCY))
)

/**
 * Cancelar no borra. Sin esta pestaña la nota cancelada no aparecía en ninguna de las
 * otras cinco y se leía como borrada: no había forma de ver qué se canceló ni de
 * restaurarlo cuando alguien le dio por equivocación.
 *
 * Solo se agrega cuando el día visible tiene alguna cancelada — mismo criterio que las
 * pestañas Facturas/Compras rápidas de Compras: una sexta pestaña permanente aprieta a las
 * otras cinco todos los días para un caso que casi nunca ocurre.
 */
private val CANCELLED_TAB = TabSpec("Canceladas", setOf(DispatchStatus.CANCELLED))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchNoteListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPack: (noteId: String) -> Unit,
    onNavigateToReceive: (noteId: String) -> Unit,
    onNavigateToDetail: (noteId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: DispatchViewModel,
    productionViewModel: ProductionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) viewModel.observeDay(companyId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val visibleNotes = remember(state.notes, user) {
        if (user == null) emptyList() else viewModel.visibleFor(user, state.notes)
    }
    val canSeeValue = user?.hasPermission(Permission.VIEW_DISPATCH_VALUE) == true

    val hasCancelled = visibleNotes.any { it.status == DispatchStatus.CANCELLED }
    val tabs = remember(hasCancelled) {
        if (hasCancelled) DISPATCH_TABS + CANCELLED_TAB else DISPATCH_TABS
    }
    // Restaurar la última cancelada quita la pestaña de abajo de los pies: sin esto
    // el índice guardado apunta fuera de la lista.
    LaunchedEffect(tabs.size) {
        if (selectedTab > tabs.lastIndex) selectedTab = tabs.lastIndex
    }
    val activeTab = selectedTab.coerceIn(0, tabs.lastIndex)

    val byStatus = remember(visibleNotes, activeTab, tabs) {
        val statuses = tabs[activeTab].statuses
        visibleNotes.filter { it.status in statuses }
    }

    val isPackingTab = activeTab == 0

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Despacho", fontWeight = FontWeight.SemiBold) },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Las pestañas Listas / En tránsito / Recibidas usan el navegador de día compartido.
            // Por empacar tiene el suyo propio adentro de la matriz.
            if (!isPackingTab) {
                DayNavigator(
                    dayMillis = state.dayAnchorMillis,
                    onPrev = { viewModel.shiftListDay(companyId, -1) },
                    onNext = { viewModel.shiftListDay(companyId, +1) },
                    onToday = { viewModel.goToTodayList(companyId) },
                    onPickDay = { viewModel.goToListDay(companyId, it) }
                )
            }

            val tabContent: @Composable () -> Unit = {
                tabs.forEachIndexed { index, spec ->
                    val count = visibleNotes.count { it.status in spec.statuses }
                    Tab(
                        selected = activeTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            // En "Por empacar" el listado mensual no representa lo que ve
                            // la matriz (que es por día/turno/planta), así que no mostramos
                            // el contador para evitar confusión.
                            val label = if (index == 0 || count <= 0) spec.label
                                        else "${spec.label} ($count)"
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (activeTab == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
            // Con seis pestañas el reparto fijo deja "Pendiente conteo" ilegible en
            // teléfono; con cinco cabe y se mantiene el aspecto de siempre.
            if (tabs.size > DISPATCH_TABS.size) {
                ScrollableTabRow(
                    selectedTabIndex = activeTab,
                    containerColor = WarmIvory,
                    contentColor = DeepSeaBlue,
                    edgePadding = 0.dp,
                    tabs = tabContent
                )
            } else {
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = WarmIvory,
                    contentColor = DeepSeaBlue,
                    tabs = tabContent
                )
            }

            // `weight(1f)` y no `fillMaxSize()`: acá el alto que queda es el de la
            // pantalla menos el navegador de día y las pestañas, y pedirlo con un
            // peso es la única forma que no depende de que la Column reste sola lo
            // ya consumido. Con `fillMaxSize()` el contenido se puede medir más
            // alto que el espacio visible, y entonces las últimas tarjetas quedan
            // debajo del borde sin scroll que las alcance. Es el mismo patrón que
            // ya usa la matriz de "Por empacar".
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isPackingTab -> {
                        if (user != null) PackingMatrixContent(companyId, user, viewModel, productionViewModel)
                        else EmptyTabState("Por empacar")
                    }
                    state.isLoading && state.notes.isEmpty() ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    byStatus.isEmpty() ->
                        EmptyTabState(tabs[activeTab].label)
                    else -> AdaptiveContainer {
                        val grouped = remember(byStatus) {
                            // Mantiene el orden de inserción (notas vienen ya ordenadas por fecha desc).
                            byStatus.groupBy { it.shiftId to it.shiftName }
                                .toList()
                                .sortedBy { (key, _) -> key.second.lowercase() }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            // `contentPadding` y no `padding`: con `padding` el margen
                            // queda FUERA del área que scrollea, así que la última
                            // tarjeta se corta contra el borde en vez de poder subir.
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            grouped.forEach { (shiftKey, notes) ->
                                // El id del turno no alcanza como clave: dos grupos
                                // pueden compartirlo (turno sin id, o un nombre que
                                // cambió), y una clave repetida revienta el LazyColumn.
                                item(key = "header-${shiftKey.first}-${shiftKey.second}") {
                                    ShiftSectionHeader(
                                        shiftName = shiftKey.second.ifEmpty { "Sin turno" },
                                        count = notes.size
                                    )
                                }
                                items(notes, key = { it.id }) { note ->
                                    DispatchNoteCard(
                                        note = note,
                                        canSeeValue = canSeeValue,
                                        onClick = {
                                            when (note.status) {
                                                DispatchStatus.DRAFT -> {
                                                    if (user?.hasPermission(Permission.DISPATCH_PACK) == true &&
                                                        user.canAccessBranch(note.originBranchId)
                                                    ) onNavigateToPack(note.id)
                                                    else onNavigateToDetail(note.id)
                                                }
                                                DispatchStatus.IN_TRANSIT, DispatchStatus.ARRIVED -> {
                                                    // ARRIVED entra al mismo Receive pero muestra paso 2 (conteo).
                                                    if (user?.hasPermission(Permission.RECEIVE_DISPATCH) == true &&
                                                        user.canAccessBranch(note.destinationBranchId)
                                                    ) onNavigateToReceive(note.id)
                                                    else onNavigateToDetail(note.id)
                                                }
                                                else -> onNavigateToDetail(note.id)
                                            }
                                        }
                                    )
                                }
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
private fun ShiftSectionHeader(shiftName: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepSeaBlue, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Turno: $shiftName",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (count == 1) "1 nota" else "$count notas",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun DispatchNoteCard(note: DispatchNote, canSeeValue: Boolean, onClick: () -> Unit) {
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
                        text = note.destinationBranchName.ifEmpty { "(sin sucursal)" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepSeaBlue
                    )
                    Text(
                        text = "Desde ${note.originBranchName} · ${note.shiftName.ifEmpty { "sin turno" }} · ${formatDay(note.productionDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(note.status)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${note.lineCount} producto(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canSeeValue && note.totalValueWholesale > 0) {
                    Text(" · Q${note.totalValueWholesale.fmt(2)}", style = MaterialTheme.typography.bodySmall)
                }
                if (note.hasPriceMissing) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.WarningAmber, null, tint = BrandColors.AccentDeep, modifier = Modifier.size(16.dp))
                    Text(" Precio faltante", style = MaterialTheme.typography.labelSmall, color = BrandColors.AccentDeep)
                }
                if (note.hasDiscrepancy) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFB00020), modifier = Modifier.size(16.dp))
                    Text(" Discrepancia", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB00020))
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (text, tone) = when (status) {
        DispatchStatus.DRAFT                      -> "Por empacar" to ChipTone.ATTENTION
        DispatchStatus.READY                      -> "Lista" to ChipTone.INFO
        DispatchStatus.IN_TRANSIT                 -> "En tránsito" to ChipTone.PROGRESS
        DispatchStatus.ARRIVED                    -> "Llegó · falta conteo" to ChipTone.ATTENTION
        DispatchStatus.DELIVERED                  -> "Recibida" to ChipTone.SUCCESS
        DispatchStatus.DELIVERED_WITH_DISCREPANCY -> "Con discrepancia" to ChipTone.DANGER
        DispatchStatus.CANCELLED                  -> "Cancelada" to ChipTone.NEUTRAL
        else                                      -> status to ChipTone.NEUTRAL
    }
    BrandChip(text, tone)
}

@Composable
private fun EmptyTabState(tabLabel: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.LocalShipping, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(10.dp))
        Text("Sin notas en \"$tabLabel\"", style = MaterialTheme.typography.titleSmall)
        Text(
            "Las notas se crean automáticamente cuando un panadero completa un lote.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun formatDay(millis: Long): String {
    if (millis == 0L) return "—"
    val tz = TimeZone.currentSystemDefault()
    val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date
    val mm = d.monthNumber.toString().padStart(2, '0')
    val dd = d.dayOfMonth.toString().padStart(2, '0')
    return "$dd/$mm/${d.year}"
}
