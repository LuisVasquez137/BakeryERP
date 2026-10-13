package com.bakery.erp.presentation.cashaudit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CashAuditEvent
import com.bakery.erp.model.CashAuditEventType
import com.bakery.erp.model.longDateLabel
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.util.fmtMoney
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Bitácora de cajas por sucursal. Aparte del POS a propósito: la ve el dueño, no quien opera
 * la caja. Lo que importa encontrar rápido son los eventos fuera del horario de cuadre de cada
 * caja —entrar al cierre a esa hora es contar dinero sin autorización—, por eso van en rojo y
 * se pueden filtrar solos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashAuditScreen(
    initialBranchId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CashAuditViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(companyId, initialBranchId) {
        if (companyId.isNotEmpty() && user != null) viewModel.load(companyId, user, initialBranchId)
    }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() } }

    Scaffold(
        containerColor = BrandColors.Ivory,
        topBar = {
            TopAppBar(
                title = { Text("Bitácora de cajas", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        AdaptiveContainer(Modifier.padding(padding).fillMaxSize(), maxContentWidth = 900.dp) {
            Column(Modifier.fillMaxSize()) {
                // Sucursal
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.branches, key = { it.id }) { b ->
                        FilterChip(
                            selected = b.id == state.selectedBranchId,
                            onClick = { viewModel.selectBranch(companyId, b.id) },
                            label = { Text(b.name) }
                        )
                    }
                }
                // Día
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.shiftDay(companyId, -1) }) { Icon(Icons.Default.ChevronLeft, "Día anterior") }
                    Text(
                        state.date?.let { longDateLabel(it) }.orEmpty(),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).clickable { viewModel.goToday(companyId) },
                        color = BrandColors.TextSlate
                    )
                    IconButton(onClick = { viewModel.shiftDay(companyId, 1) }) { Icon(Icons.Default.ChevronRight, "Día siguiente") }
                }
                val outCount = state.events.count { it.outOfSchedule }
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = state.onlyOutOfSchedule,
                        onClick = { viewModel.toggleOnlyOutOfSchedule() },
                        label = { Text("Solo fuera de horario ($outCount)") },
                        leadingIcon = { Icon(Icons.Default.Warning, null, Modifier.size(18.dp)) }
                    )
                }

                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = BrandColors.Primary)
                    }
                    state.visibleEvents.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                        Text(
                            if (state.onlyOutOfSchedule) "Nada fuera de horario este día." else "Sin movimientos de caja este día.",
                            color = BrandColors.MutedSlate
                        )
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.visibleEvents, key = { it.id }) { e -> AuditRow(e) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditRow(e: CashAuditEvent) {
    val at = kotlin.time.Instant.fromEpochMilliseconds(e.at).toLocalDateTime(TimeZone.currentSystemDefault())
    val h12 = when { at.hour == 0 -> 12; at.hour > 12 -> at.hour - 12; else -> at.hour }
    val time = "$h12:${at.minute.toString().padStart(2, '0')} ${if (at.hour < 12) "AM" else "PM"}"
    val icon: ImageVector = when (e.type) {
        CashAuditEventType.OPEN -> Icons.Default.LockOpen
        CashAuditEventType.CLOSE -> Icons.Default.LockClock
        CashAuditEventType.CLOSE_SCREEN -> Icons.Default.Visibility
        CashAuditEventType.HELD_CART_DISCARDED -> Icons.Default.RemoveShoppingCart
        else -> Icons.Default.PointOfSale
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (e.outOfSchedule) MaterialTheme.colorScheme.errorContainer else Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = if (e.outOfSchedule) MaterialTheme.colorScheme.error else BrandColors.Primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$time · ${CashAuditEventType.label(e.type)}", fontWeight = FontWeight.SemiBold,
                        color = BrandColors.TextSlate)
                    if (e.outOfSchedule) BrandChip("Fuera de horario", ChipTone.DANGER)
                }
                Text(
                    listOf(e.registerName, e.employeeName, "usuario ${e.userName}").filter { it.isNotBlank() && it != "usuario " }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.TextSlate
                )
                if (e.outOfSchedule && e.scheduleLabel.isNotBlank()) {
                    Text("Horario de la caja: ${e.scheduleLabel}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                when (e.type) {
                    CashAuditEventType.OPEN -> Text("Fondo inicial Q ${e.expectedCash.fmtMoney(2)}",
                        style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                    CashAuditEventType.CLOSE -> {
                        val tone = when {
                            e.difference < -0.01 -> MaterialTheme.colorScheme.error
                            e.difference > 0.01 -> BrandColors.AccentDeep
                            else -> BrandColors.MutedSlate
                        }
                        Text(
                            "Esperado Q ${e.expectedCash.fmtMoney(2)} · Contado Q ${e.countedCash.fmtMoney(2)} · " +
                                when {
                                    e.difference < -0.01 -> "Faltan Q ${(-e.difference).fmtMoney(2)}"
                                    e.difference > 0.01 -> "Sobran Q ${e.difference.fmtMoney(2)}"
                                    else -> "Cuadró"
                                },
                            style = MaterialTheme.typography.bodySmall, color = tone
                        )
                    }
                    CashAuditEventType.CLOSE_SCREEN -> Text("Vio el cierre (esperado en caja)",
                        style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                    CashAuditEventType.HELD_CART_DISCARDED -> Text("Cuenta de Q ${e.expectedCash.fmtMoney(2)} sin cobrar",
                        style = MaterialTheme.typography.bodySmall, color = BrandColors.AccentDeep, fontWeight = FontWeight.SemiBold)
                }
                if (e.notes.isNotBlank()) {
                    Text("Nota: ${e.notes}", style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                }
            }
        }
    }
}
