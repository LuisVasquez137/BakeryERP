package com.bakery.erp.presentation.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CounterRemoval
import com.bakery.erp.model.CounterRemovalKind
import com.bakery.erp.model.Permission
import com.bakery.erp.model.ResponsibleConfirmation
import com.bakery.erp.model.accidentForgiveBlocker
import com.bakery.erp.model.counterRemovalByPerson
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.ui.ZoomableImageDialog
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtDateTime
import com.bakery.erp.util.fmtMoney
import com.bakery.erp.util.fmtTrim
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * "Salidas del mostrador": lo que salió sin venderse, por sucursal y periodo, con el resumen
 * por persona (donde se ven los patrones) y "Perdonar" en los accidentes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterRemovalListScreen(
    initialBranchId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CounterRemovalViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    val canForgive = user?.hasPermission(Permission.FORGIVE_STAFF_ACCIDENT) == true

    var branchId by rememberSaveable { mutableStateOf(initialBranchId.orEmpty()) }
    // 0 = hoy, 7 = últimos 7 días, 30 = últimos 30 días.
    var days by rememberSaveable { mutableStateOf(0) }
    var kindFilter by rememberSaveable { mutableStateOf("") }
    var photoToShow by remember { mutableStateOf<String?>(null) }
    var forgiveTarget by remember { mutableStateOf<CounterRemoval?>(null) }

    LaunchedEffect(companyId, user?.id) {
        if (companyId.isNotEmpty() && user != null) viewModel.loadBranches(companyId, user)
    }
    LaunchedEffect(companyId, branchId, days) {
        if (companyId.isEmpty()) return@LaunchedEffect
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val from = now.toLocalDateTime(tz).date.minus(DatePeriod(days = days)).atStartOfDayIn(tz)
        viewModel.loadList(companyId, branchId, from.toEpochMilliseconds(), now.toEpochMilliseconds() + 60_000)
    }
    LaunchedEffect(state.notice) {
        state.notice?.let { forgiveTarget = null; viewModel.clearNotice(); snackbar.showSnackbar(it) }
    }
    LaunchedEffect(state.error) {
        state.error?.let { if (forgiveTarget == null) { viewModel.clearError(); snackbar.showSnackbar(it) } }
    }

    val shown = remember(state.list, kindFilter) {
        if (kindFilter.isEmpty()) state.list else state.list.filter { it.kind == kindFilter }
    }
    val byPerson = remember(state.list) { counterRemovalByPerson(state.list) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Salidas del mostrador", fontWeight = FontWeight.SemiBold) },
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
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = BrandColors.Ivory
        ) { padding ->
            AdaptiveContainer(modifier = Modifier.padding(padding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                            ChipRow {
                                FilterChip(selected = branchId.isEmpty(), onClick = { branchId = "" }, label = { Text("Todas") })
                                state.branches.forEach { b ->
                                    FilterChip(selected = branchId == b.id, onClick = { branchId = b.id }, label = { Text(b.name) })
                                }
                            }
                            ChipRow {
                                listOf(0 to "Hoy", 7 to "7 días", 30 to "30 días").forEach { (d, label) ->
                                    FilterChip(selected = days == d, onClick = { days = d }, label = { Text(label) })
                                }
                            }
                            ChipRow {
                                listOf("" to "Todo", CounterRemovalKind.DAMAGED to "Venía mal",
                                    CounterRemovalKind.ACCIDENT to "Accidentes", CounterRemovalKind.COURTESY to "Regalos"
                                ).forEach { (k, label) ->
                                    FilterChip(selected = kindFilter == k, onClick = { kindFilter = k }, label = { Text(label) })
                                }
                            }
                        }
                    }
                    if (byPerson.isNotEmpty()) item { PersonSummary(byPerson) }
                    if (state.isListLoading && state.list.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (shown.isEmpty()) {
                        item {
                            Text(
                                "No salió nada del mostrador sin venta en este periodo.",
                                color = BrandColors.MutedSlate,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                    items(shown, key = { it.id }) { r ->
                        val blocker = user?.let { accidentForgiveBlocker(r, viewModel.selfEmployeeId(it)) }
                        RemovalCard(
                            r = r,
                            onPhoto = { photoToShow = r.photoUrl },
                            forgiveHint = if (r.isAccident && !r.forgiven && canForgive) blocker else null,
                            onForgive = if (r.isAccident && canForgive && blocker == null) ({ forgiveTarget = r }) else null
                        )
                    }
                    item { Box(Modifier.padding(16.dp)) }
                }
            }
        }
        LoadingOverlay(visible = state.isBusy)
    }

    photoToShow?.let { url -> ZoomableImageDialog(imageUrl = url, onDismiss = { photoToShow = null }) }

    forgiveTarget?.let { r ->
        var reason by remember(r.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!state.isBusy) { forgiveTarget = null; viewModel.clearError() } },
            title = { Text("¿Perdonar el accidente de ${r.responsibleEmployeeName}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se le abonan Q ${r.chargeAmount.fmtMoney(2)} en su cuenta. El cargo queda en el historial y al dueño le llega aviso.")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(200) },
                        label = { Text("Por qué (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.error?.let { Text(it, color = BrandColors.Danger, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(onClick = { user?.let { viewModel.forgive(companyId, r, reason, it) } }, enabled = !state.isBusy) {
                    Text("Perdonar")
                }
            },
            dismissButton = {
                TextButton(onClick = { forgiveTarget = null; viewModel.clearError() }, enabled = !state.isBusy) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
private fun PersonSummary(stats: List<com.bakery.erp.model.CounterRemovalPersonStat>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BrandColors.Sand),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Por persona", fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
            stats.forEach { s ->
                val parts = buildList {
                    if (s.damagedUnits > 0) add("venía mal ${s.damagedUnits.fmtTrim()} u (Q ${s.damagedCost.fmtMoney(2)})")
                    if (s.accidents > 0) add(
                        "${s.accidents} accidente${if (s.accidents > 1) "s" else ""} Q ${s.accidentCharged.fmtMoney(2)}" +
                            if (s.accidentForgiven > 0) " (perdonado Q ${s.accidentForgiven.fmtMoney(2)})" else ""
                    )
                    if (s.courtesyValue > 0) add("regaló Q ${s.courtesyValue.fmtMoney(2)}")
                }
                Column {
                    Text(s.name.ifEmpty { "—" }, fontWeight = FontWeight.Medium, color = BrandColors.TextSlate)
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                }
            }
            Text(
                "Venía mal y regalos cuentan para quien los registró; los accidentes, para la responsable.",
                style = MaterialTheme.typography.labelSmall,
                color = BrandColors.MutedSlate
            )
        }
    }
}

@Composable
private fun RemovalCard(
    r: CounterRemoval,
    onPhoto: () -> Unit,
    forgiveHint: String?,
    onForgive: (() -> Unit)?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BrandColors.Ivory),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (r.photoUrl.isNotBlank()) {
                ProductImage(
                    imageUrl = r.photoUrl,
                    contentDescription = "Foto",
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onPhoto)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrandChip(
                        CounterRemovalKind.label(r.kind),
                        when (r.kind) {
                            CounterRemovalKind.ACCIDENT -> if (r.forgiven) ChipTone.NEUTRAL else ChipTone.DANGER
                            CounterRemovalKind.COURTESY -> ChipTone.BRAND
                            else -> ChipTone.ATTENTION
                        }
                    )
                    Text(r.createdAt.fmtDateTime(), style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
                }
                Text(
                    r.lines.joinToString { "${it.quantity.fmtTrim()} ${it.productName}" },
                    fontWeight = FontWeight.Medium,
                    color = BrandColors.TextSlate
                )
                Text(
                    if (r.kind == CounterRemovalKind.COURTESY) "Q ${r.totalPrice.fmtMoney(2)} a precio de venta"
                    else "Q ${r.totalCost.fmtMoney(2)} a costo",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.TextSlate
                )
                if (r.isAccident) {
                    val how = when (r.responsibleConfirmation) {
                        ResponsibleConfirmation.SELF -> "lo registró ella"
                        ResponsibleConfirmation.PIN -> "confirmó con su PIN"
                        else -> "sin confirmar (no tiene PIN)"
                    }
                    Text("Responsable: ${r.responsibleEmployeeName} · $how", style = MaterialTheme.typography.bodySmall)
                    if (r.forgiven) {
                        Text(
                            "Perdonado por ${r.forgivenByName}" + r.forgiveReason.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.Success
                        )
                    } else {
                        Text("Cargado a su cuenta", style = MaterialTheme.typography.bodySmall, color = BrandColors.Danger)
                    }
                }
                if (r.note.isNotBlank()) Text("“${r.note}”", style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                Text(
                    "${r.branchName} · ${r.registerName} · registró ${r.createdByUserName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.MutedSlate
                )
                if (onForgive != null) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    OutlinedButton(onClick = onForgive) { Text("Perdonar") }
                } else if (forgiveHint != null) {
                    Text(forgiveHint, style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
                }
            }
        }
    }
}
