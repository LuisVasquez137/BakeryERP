package com.bakery.erp.presentation.pos

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.CashDenominations
import com.bakery.erp.model.DailyCashCloseStatus
import com.bakery.erp.model.SpotCashCheck
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.pos.components.DenominationCounter
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.util.fmtMoney
import kotlin.math.abs
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyCashCloseScreen(
    branchIdArg: String?,
    closeDateArg: String?,
    onNavigateBack: () -> Unit,
    onClosed: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: DailyCashCloseViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val user = authState.user
    val snackbar = remember { SnackbarHostState() }
    var notes by rememberSaveable { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && user != null) viewModel.loadBranches(companyId, user)
    }
    LaunchedEffect(branchIdArg) {
        branchIdArg?.let { viewModel.selectBranch(it) }
    }
    LaunchedEffect(closeDateArg) {
        closeDateArg?.takeIf { it.isNotBlank() }?.let { viewModel.setCloseDate(it) }
    }
    LaunchedEffect(state.selectedBranchId, state.closeDate, companyId) {
        if (companyId.isNotEmpty() && state.selectedBranchId.isNotEmpty()
            && state.closeDate.isNotEmpty()
        ) {
            viewModel.loadDayContext(companyId)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.isClosed) {
        if (state.isClosed) { viewModel.clearClosed(); onClosed() }
    }

    val draft = state.draft
    val existing = state.existing
    val isClosed = existing?.status == DailyCashCloseStatus.CLOSED
    val leftTotal = state.leftDenoms.entries.sumOf { (code, qty) ->
        (CashDenominations.byCode(code)?.value ?: 0.0) * qty
    }
    val depositTotal = state.depositDenoms.entries.sumOf { (code, qty) ->
        (CashDenominations.byCode(code)?.value ?: 0.0) * qty
    }
    val target = draft?.totalCountedCash ?: 0.0
    val balanced = abs((leftTotal + depositTotal) - target) <= 0.05

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Cierre del día", fontWeight = FontWeight.SemiBold) },
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
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Selectors
                if (state.branches.size > 1) {
                    Text("Sucursal", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.branches.forEach { b ->
                            FilterChip(
                                selected = b.id == state.selectedBranchId,
                                onClick = { viewModel.selectBranch(b.id) },
                                label = { Text(b.name) }
                            )
                        }
                    }
                }
                Text("Fecha del cierre: ${state.closeDate}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (state.isLoading && draft == null) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@Column
                }
                if (draft == null) {
                    Text("Selecciona sucursal para ver el resumen.",
                        color = MaterialTheme.colorScheme.outline)
                    return@Column
                }

                if (draft.hasOpenSessions) {
                    WarningBanner(
                        title = "Hay cajas abiertas",
                        body = "Debes cerrar todas las sesiones de caja del día antes de hacer el cierre consolidado."
                    )
                }
                if (isClosed) {
                    SuccessBanner(
                        title = "Día cerrado",
                        body = "Cerrado por ${existing?.closedByUserName} · " +
                            "Q ${existing?.depositedAmount?.fmtMoney(2)} entregados al owner · " +
                            "Q ${existing?.leftForNextDay?.fmtMoney(2)} dejados como fondo."
                    )
                }

                // Resumen del día
                SummaryCard(draft)

                if (state.breadCount != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Conteo de pan del día",
                                fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
                            // El valor sale del costo del pan: solo con permiso de ver valores.
                            if (user?.hasPermission(com.bakery.erp.model.Permission.VIEW_DISPATCH_VALUE) == true)
                                Text("Mermas: Q ${state.breadCount!!.totalShortageValue.fmtMoney(2)} · " +
                                    "Sobrantes: Q ${state.breadCount!!.totalSurplusValue.fmtMoney(2)}",
                                    style = MaterialTheme.typography.bodySmall)
                            else Text("Mermas y sobrantes ya aplicados al stock.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (state.spotChecks.isNotEmpty()) {
                    SpotChecksCard(state.spotChecks)
                }

                if (!isClosed) {
                    Text("Sencillo a dejar para mañana", fontWeight = FontWeight.SemiBold)
                    DenominationCounter(
                        counts = state.leftDenoms,
                        onChange = { viewModel.updateLeftDenoms(it) },
                        title = "Queda en caja"
                    )

                    Text("Para el owner (depósito)", fontWeight = FontWeight.SemiBold)
                    DenominationCounter(
                        counts = state.depositDenoms,
                        onChange = { viewModel.updateDepositDenoms(it) },
                        title = "Se lleva el owner"
                    )

                    BalanceIndicator(target, leftTotal + depositTotal, balanced)

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notas del cierre (opcional)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { showConfirm = true },
                        enabled = !state.isLoading && !draft.hasOpenSessions && balanced,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Lock, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cerrar día", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    OwnerReportCards(
                        depositAmount = existing?.depositedAmount ?: 0.0,
                        leftAmount = existing?.leftForNextDay ?: 0.0,
                        depositDenoms = state.depositDenoms,
                        leftDenoms = state.leftDenoms
                    )
                }
            }
        }
    }

    if (showConfirm && user != null && draft != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("¿Cerrar el día?") },
            text = {
                Text("Esta acción es definitiva. Las sesiones del día quedarán bloqueadas " +
                    "y se generará el reporte del owner con los montos a depositar.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.closeDay(companyId, notes.trim().ifEmpty { null }, user)
                }) { Text("Sí, cerrar día") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SummaryCard(draft: com.bakery.erp.domain.DailyCashCloseDraft) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Resumen del día · ${draft.branchName}",
                fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
            Text("Sesiones cerradas: ${draft.sessions.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            kv("Ventas efectivo", "Q ${draft.totalCashSales.fmtMoney(2)}")
            kv("Ventas tarjeta", "Q ${draft.totalCardSales.fmtMoney(2)}")
            kv("Ventas transferencia", "Q ${draft.totalTransferSales.fmtMoney(2)}")
            if (draft.totalPedidosYaSales > 0.004) {
                kv("Ventas PedidosYa (las liquida PedidosYa)", "Q ${draft.totalPedidosYaSales.fmtMoney(2)}")
            }
            kv("Depósitos a caja", "Q ${draft.totalMovementsIn.fmtMoney(2)}")
            kv("Retiros / gastos", "Q ${draft.totalMovementsOut.fmtMoney(2)}")
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            kv("Efectivo esperado", "Q ${draft.totalExpectedCash.fmtMoney(2)}", emphasize = true)
            kv("Efectivo contado", "Q ${draft.totalCountedCash.fmtMoney(2)}", emphasize = true)
            val diffColor = when {
                draft.totalDifference > 0.01 -> Color(0xFF1B5E20)
                draft.totalDifference < -0.01 -> Color(0xFFB00020)
                else -> MaterialTheme.colorScheme.onSurface
            }
            kv(
                "Diferencia",
                "Q ${draft.totalDifference.fmtMoney(2)}",
                emphasize = true,
                valueColor = diffColor
            )
        }
    }
}

@Composable
private fun kv(label: String, value: String, emphasize: Boolean = false, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal)
        Text(value,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun WarningBanner(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color(0xFF8B4513))
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SuccessBanner(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color(0xFF1B5E20))
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BalanceIndicator(target: Double, current: Double, balanced: Boolean) {
    val color = if (balanced) Color(0xFF1B5E20) else Color(0xFFB00020)
    val delta = current - target
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (balanced) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total dejar + depositar", fontWeight = FontWeight.SemiBold)
                Text("Q ${current.fmtMoney(2)}", fontWeight = FontWeight.Bold, color = color)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Efectivo contado", style = MaterialTheme.typography.bodySmall)
                Text("Q ${target.fmtMoney(2)}", style = MaterialTheme.typography.bodySmall)
            }
            if (!balanced) {
                Text(
                    "Falta cuadrar Q ${kotlin.math.abs(delta).fmtMoney(2)} para coincidir con el efectivo contado.",
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun SpotChecksCard(checks: List<SpotCashCheck>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Cuadres sorpresa del día",
                fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            checks.forEach { c ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${c.registerName} · ${c.checkedByUserName}",
                        style = MaterialTheme.typography.bodySmall)
                    val color = when {
                        c.difference > 0.01 -> Color(0xFF1B5E20)
                        c.difference < -0.01 -> Color(0xFFB00020)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text("Q ${c.difference.fmtMoney(2)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = color, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun OwnerReportCards(
    depositAmount: Double,
    leftAmount: Double,
    depositDenoms: Map<String, Int>,
    leftDenoms: Map<String, Int>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalance, null, tint = DeepSeaBlue)
                Spacer(Modifier.width(8.dp))
                Text("Se lleva el owner", fontWeight = FontWeight.Bold, color = DeepSeaBlue)
                Spacer(Modifier.width(8.dp))
                Text("Q ${depositAmount.fmtMoney(2)}", fontWeight = FontWeight.Bold, color = BrandColors.AccentDeep)
            }
            denomList(depositDenoms)
        }
    }
    Spacer(Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Done, null, tint = Color(0xFF8B4513))
                Spacer(Modifier.width(8.dp))
                Text("Queda en caja para mañana",
                    fontWeight = FontWeight.Bold, color = Color(0xFF8B4513))
                Spacer(Modifier.width(8.dp))
                Text("Q ${leftAmount.fmtMoney(2)}",
                    fontWeight = FontWeight.Bold, color = BrandColors.AccentDeep)
            }
            denomList(leftDenoms)
        }
    }
}

@Composable
private fun denomList(denoms: Map<String, Int>) {
    if (denoms.isEmpty()) {
        Text("Sin desglose.", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline)
        return
    }
    Spacer(Modifier.height(6.dp))
    CashDenominations.ALL.forEach { d ->
        val qty = denoms[d.code] ?: 0
        if (qty <= 0) return@forEach
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${d.label} × $qty",
                style = MaterialTheme.typography.bodySmall)
            Text("Q ${(d.value * qty).fmtMoney(2)}",
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}
