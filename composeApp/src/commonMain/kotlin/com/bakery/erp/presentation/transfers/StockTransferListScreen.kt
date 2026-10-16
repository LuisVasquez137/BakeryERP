package com.bakery.erp.presentation.transfers

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Permission
import com.bakery.erp.model.StockTransfer
import com.bakery.erp.model.StockTransferStatus
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.quantityLabel
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.util.fmtDateTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Traslados de las sucursales del usuario. Arriba lo que va en camino (con "Por recibir" en lo que
 * le toca contar), abajo lo ya recibido o cancelado del periodo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTransferListScreen(
    initialBranchId: String?,
    onNavigateBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (transferId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: StockTransferViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    val canSend = user?.hasPermission(Permission.SEND_STOCK_TRANSFER) == true

    var branchId by rememberSaveable { mutableStateOf(initialBranchId.orEmpty()) }
    var days by rememberSaveable { mutableStateOf(7) }

    LaunchedEffect(companyId) { if (companyId.isNotEmpty()) viewModel.loadBranches(companyId) }
    LaunchedEffect(companyId, branchId, days, user?.id) {
        if (companyId.isEmpty() || user == null) return@LaunchedEffect
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val from = now.toLocalDateTime(tz).date.minus(DatePeriod(days = days)).atStartOfDayIn(tz)
        viewModel.loadList(companyId, user, branchId, from.toEpochMilliseconds(), now.toEpochMilliseconds() + 60_000)
    }
    LaunchedEffect(state.error) { state.error?.let { viewModel.clearError(); snackbar.showSnackbar(it) } }

    val visibleBranches = remember(state.branches, user?.id) {
        state.branches.filter { user?.canAccessBranch(it.id) == true }
    }
    val inTransit = state.list.filter { it.isInTransit }
    val done = state.list.filter { !it.isInTransit }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Traslados", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Primary,
                    titleContentColor = BrandColors.Ivory,
                    navigationIconContentColor = BrandColors.Ivory
                )
            )
        },
        floatingActionButton = {
            if (canSend) ExtendedFloatingActionButton(
                onClick = onNew,
                containerColor = BrandColors.Accent,
                contentColor = BrandColors.OnAccent,
                modifier = Modifier.navigationBarsPadding(),
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nuevo traslado") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = BrandColors.Ivory
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (visibleBranches.size > 1) ChipRow {
                            BranchChip("Todas", selected = branchId.isEmpty()) { branchId = "" }
                            visibleBranches.forEach { b -> BranchChip(b.name, selected = branchId == b.id) { branchId = b.id } }
                        }
                        ChipRow {
                            listOf(0 to "Hoy", 7 to "7 días", 30 to "30 días").forEach { (d, label) ->
                                BranchChip(label, selected = days == d) { days = d }
                            }
                        }
                    }
                }
                if (state.isListLoading && state.list.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                } else if (state.list.isEmpty()) {
                    item {
                        Text(
                            "No hay traslados en este periodo." + if (canSend) " Para mandar algo a otra sucursal, toca \"Nuevo traslado\"." else "",
                            color = BrandColors.MutedSlate,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                if (inTransit.isNotEmpty()) {
                    item { Header("En camino") }
                    items(inTransit, key = { it.id }) { t -> TransferCard(t, user) { onOpen(t.id) } }
                }
                if (done.isNotEmpty()) {
                    item { Header("Historial") }
                    items(done, key = { it.id }) { t -> TransferCard(t, user) { onOpen(t.id) } }
                }
                item { Box(Modifier.padding(40.dp)) }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text, fontWeight = FontWeight.SemiBold, color = BrandColors.Primary,
        style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )
}

@Composable
private fun TransferCard(t: StockTransfer, user: User?, onClick: () -> Unit) {
    val toReceive = user?.canReceive(t) == true
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.originBranchName, fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = BrandColors.Primary, modifier = Modifier.padding(horizontal = 4.dp))
                Text(t.destinationBranchName, fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate, modifier = Modifier.weight(1f))
                if (toReceive) BrandChip("Por recibir", ChipTone.ATTENTION, com.bakery.erp.ui.theme.ChipEmphasis.SOLID)
                else BrandChip(StockTransferStatus.label(t.status), statusTone(t.status))
            }
            Text(
                t.lines.joinToString { "${it.quantityLabel()} ${it.itemName}" },
                style = MaterialTheme.typography.bodySmall, color = BrandColors.TextSlate,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${t.folio} · ${t.sentAt.fmtDateTime()} · ${t.sentByName}",
                style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate
            )
            if (t.shortages.isNotEmpty()) BrandChip("Llegó incompleto", ChipTone.DANGER)
        }
    }
}
