package com.bakery.erp.presentation.production

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakery.erp.model.Permission
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.LoadingOverlay
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import com.bakery.erp.ui.theme.BrandColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionLotListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (lotId: String) -> Unit,
    onNavigateToForm: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canManage = user?.hasPermission(Permission.MANAGE_PRODUCTION_LOTS) == true
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && user != null) viewModel.loadLots(companyId, user)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // Día seleccionado para filtrar lotes — default = hoy. Persistido en
    // rememberSaveable para sobrevivir rotación.
    var selectedDateMillis by rememberSaveable { mutableStateOf(startOfTodayLotListMillis()) }
    val tz = remember { TimeZone.currentSystemDefault() }
    val selectedDay = remember(selectedDateMillis) {
        Instant.fromEpochMilliseconds(selectedDateMillis).toLocalDateTime(tz).date
    }

    // Filtrado por día calendario (no por millis exactos, tolera drifts).
    val filteredLots = remember(state.lots, selectedDay) {
        state.lots.filter {
            Instant.fromEpochMilliseconds(it.productionDate).toLocalDateTime(tz).date == selectedDay
        }.sortedBy { it.shiftName }
    }

    // Agrupado por turno para mostrar encabezados de sección — un panadero con
    // dos turnos por día necesita verlos claramente separados.
    // No usamos toSortedMap() porque Kotlin/JS no lo soporta.
    val groupedByShift = remember(filteredLots) {
        filteredLots.groupBy { it.shiftName.ifBlank { "Sin turno" } }
            .entries
            .sortedBy { it.key }
    }

    // Si el usuario solo tiene acceso a una planta productora, el título incluye
    // su nombre. Con más de una se mantiene plano (no hay selector aquí).
    val headerTitle = remember(state.accessibleProducingBranches) {
        val branches = state.accessibleProducingBranches
        if (branches.size == 1) "Lotes - ${branches.first().name}"
        else "Lotes de Producción"
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(headerTitle, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (canManage) {
                FloatingActionButton(
                    onClick = onNavigateToForm,
                    containerColor = BrandColors.Primary
                ) { Icon(Icons.Default.Add, "Nuevo lote", tint = Color.White) }
            }
        },
        containerColor = BrandColors.Ivory
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DateHeader(
                dateMillis = selectedDateMillis,
                onPrev = { selectedDateMillis -= 86_400_000L },
                onNext = { selectedDateMillis += 86_400_000L },
                onResetToday = { selectedDateMillis = startOfTodayLotListMillis() }
            )

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                filteredLots.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Factory, null, Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("Sin lotes para este día", color = Color.Gray)
                        if (canManage) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Crea uno nuevo o navega a otro día",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedByShift.forEach { entry ->
                        val shiftLabel = entry.key
                        val lotsInShift = entry.value
                        item(key = "header_$shiftLabel") {
                            ShiftSectionHeader(
                                shiftName = shiftLabel,
                                lotCount = lotsInShift.size
                            )
                        }
                        items(lotsInShift, key = { it.id }) { lot ->
                            ProductionLotCard(lot = lot, onClick = { onNavigateToDetail(lot.id) })
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
private fun DateHeader(
    dateMillis: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onResetToday: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Default.ChevronLeft, "Día anterior", tint = BrandColors.Primary)
            }
            Row(
                modifier = Modifier.weight(1f).clickable(onClick = onResetToday),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = BrandColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    formatProductionDate(dateMillis),
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "(toca para hoy)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.ChevronRight, "Día siguiente", tint = BrandColors.Primary)
            }
        }
    }
}

private fun startOfTodayLotListMillis(): Long {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    return LocalDateTime(today, LocalTime(0, 0, 0)).toInstant(tz).toEpochMilliseconds()
}

/** Heurística por nombre — el turno no tiene un campo "tipo" estructurado,
 *  y `startTime` obligaría a cargar `ProductionShift` aparte solo para esto.
 *  "Turno noche" / "Turno mañana" / "Turno dia (calvario)" ya cubren los
 *  nombres reales en uso; default = día si no dice "noche". */
private fun isNightShift(shiftName: String): Boolean = shiftName.contains("noche", ignoreCase = true)

/** Color de acento del turno — mismo tono en el badge, la etiqueta y la
 *  franja de la tarjeta, para que la asociación día/noche sea inmediata. */
private fun shiftAccentColor(shiftName: String): Color =
    if (isNightShift(shiftName)) Color(0xFF2A2D7C) else Color(0xFFFF8F00)

/** Distinto de un vistazo aunque no se distinga el color (forma + ícono +
 *  etiqueta, no solo tinte): de día un círculo dorado con sol, de noche un
 *  cuadrado redondeado azul-violeta oscuro con luna — silueta distinta,
 *  no solo cambia el tono. */
@Composable
private fun ShiftIconBadge(
    shiftName: String,
    size: androidx.compose.ui.unit.Dp = 26.dp,
    showLabel: Boolean = false
) {
    val isNight = isNightShift(shiftName)
    val accent = shiftAccentColor(shiftName)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            shape = if (isNight) RoundedCornerShape(size * 0.3f) else CircleShape,
            color = accent,
            modifier = Modifier.size(size)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isNight) Icons.Default.NightsStay else Icons.Default.WbSunny,
                    contentDescription = if (isNight) "Turno de noche" else "Turno de día",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.62f)
                )
            }
        }
        if (showLabel) {
            Surface(shape = RoundedCornerShape(4.dp), color = accent) {
                Text(
                    if (isNight) "NOCHE" else "DÍA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ShiftSectionHeader(shiftName: String, lotCount: Int) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = BrandColors.Primary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShiftIconBadge(shiftName, size = 30.dp, showLabel = true)
                Text(
                    shiftName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Surface(shape = RoundedCornerShape(12.dp), color = BrandColors.AccentDeep) {
                Text(
                    "$lotCount lote${if (lotCount == 1) "" else "s"}",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun ProductionLotCard(lot: ProductionLot, onClick: () -> Unit) {
    val (statusColor, statusBg) = when (lot.status) {
        ProductionLotStatus.OPEN        -> Color(0xFF1565C0) to Color(0xFFE3F2FD)
        ProductionLotStatus.IN_PROGRESS -> Color(0xFFF57C00) to Color(0xFFFFF3E0)
        ProductionLotStatus.COMPLETED   -> Color(0xFF2E7D32) to Color(0xFFE8F5E9)
        ProductionLotStatus.CANCELLED   -> Color.Gray to Color(0xFFF5F5F5)
        else                            -> Color.Gray to Color(0xFFF5F5F5)
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        // Franja de color a la izquierda con el mismo acento del turno — se
        // distingue a simple vista al hacer scroll, sin depender de leer el
        // ícono pequeño.
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(shiftAccentColor(lot.shiftName))
            )
            Row(
                modifier = Modifier.weight(1f).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(lot.producingBranchName, fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ShiftIconBadge(lot.shiftName, size = 18.dp)
                        Text(
                            "${lot.shiftName} — ${formatProductionDate(lot.productionDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Text(
                        "${lot.lineCount} producto(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Surface(shape = RoundedCornerShape(20.dp), color = statusBg) {
                    Text(
                        ProductionLotStatus.label(lot.status),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
