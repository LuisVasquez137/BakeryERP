package com.bakery.erp.presentation.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.CashSessionStatus
import com.bakery.erp.model.Permission
import com.bakery.erp.model.SpotCashCheck
import com.bakery.erp.model.closeCountDifference
import com.bakery.erp.model.expectedTransferNow
import com.bakery.erp.model.expectedCardNow
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.pos.DailyCashCloseViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.BrandTopAppBar
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtMoney
import kotlin.math.abs
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Resumen de cajas del día por sucursal. Solo lectura.
 *
 * Antes era el "Cierre del día", que volvía a pedir repartir el efectivo entre lo que se deja y lo
 * que se lleva el dueño. Eso ya se hace en "Cerrar caja" (retiro/banco y lo que queda para mañana,
 * que es lo que la caja espera al abrir), así que repartirlo aquí era contar el dinero dos veces y
 * descuadraba la apertura del día siguiente. Ahora solo junta lo que pasó en cada caja.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CashDaySummaryScreen(
    branchIdArg: String?,
    dateArg: String?,
    onNavigateBack: () -> Unit,
    onOpenSession: (sessionId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: DailyCashCloseViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val user = authState.user
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && user != null) viewModel.loadBranches(companyId, user)
    }
    LaunchedEffect(branchIdArg) { branchIdArg?.let { viewModel.selectBranch(it) } }
    LaunchedEffect(dateArg) { dateArg?.takeIf { it.isNotBlank() }?.let { viewModel.setCloseDate(it) } }
    LaunchedEffect(state.selectedBranchId, state.closeDate, companyId) {
        if (companyId.isNotEmpty() && state.selectedBranchId.isNotEmpty() && state.closeDate.isNotEmpty()) {
            viewModel.loadDayContext(companyId)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    val draft = state.draft
    val today = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Resumen de cajas", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Primary,
                    titleContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = BrandColors.Ivory
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.branches.size > 1) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.shiftDate(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Día anterior")
                    }
                    Text(
                        dayLabel(state.closeDate) + if (state.closeDate == today) " · hoy" else "",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.shiftDate(1) }, enabled = state.closeDate < today) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Día siguiente")
                    }
                }

                if (state.isLoading && draft == null) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@Column
                }
                if (draft == null) return@Column
                val all = (draft.sessions + draft.openSessions).sortedBy { it.openedAt }
                if (all.isEmpty()) {
                    Text("No hubo cajas abiertas este día.", color = BrandColors.MutedSlate)
                    return@Column
                }

                if (draft.openSessions.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BrandColors.AccentSoft),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "${draft.openSessions.size} caja(s) siguen abiertas: lo que vendieron ya está en las ventas, " +
                                "pero su efectivo se cuenta al cerrarlas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.Primary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                BranchTotalsCard(draft.branchName, all, draft)

                Text("Por caja", fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                all.groupBy { it.registerName.ifBlank { it.registerId } }.forEach { (register, shifts) ->
                    Text(register, style = MaterialTheme.typography.titleSmall, color = BrandColors.Primary,
                        fontWeight = FontWeight.SemiBold)
                    shifts.forEach { ShiftCard(it, onClick = { onOpenSession(it.id) }) }
                }

                if (state.spotChecks.isNotEmpty()) SpotChecksCard(state.spotChecks)

                state.breadCount?.let { count ->
                    SummaryCard("Conteo de pan del día") {
                        if (user?.hasPermission(Permission.VIEW_DISPATCH_VALUE) == true) {
                            Text("Mermas: Q ${count.totalShortageValue.fmtMoney(2)} · " +
                                "Sobrantes: Q ${count.totalSurplusValue.fmtMoney(2)}",
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Mermas y sobrantes ya aplicados al stock.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchTotalsCard(
    branchName: String,
    all: List<CashSession>,
    draft: com.bakery.erp.domain.DailyCashCloseDraft
) {
    SummaryCard("Sucursal $branchName") {
        Line("Ventas totales", all.sumOf { it.totalSales }, bold = true)
        Line("Efectivo", all.sumOf { it.totalCashSales })
        Line("Tarjeta", all.sumOf { it.totalCardSales })
        Line("Transferencia", all.sumOf { it.totalTransferSales })
        all.sumOf { it.totalPedidosYaSales }.takeIf { it > 0.004 }?.let { Line("PedidosYa", it) }
        all.sumOf { it.totalCreditSales }.takeIf { it > 0.004 }?.let { Line("Crédito (sin dinero)", it) }
        Line("Gastos, traslados y depósitos", all.sumOf { it.totalMovementsOut })
        if (draft.sessions.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("Cajas cerradas", style = MaterialTheme.typography.labelMedium, color = BrandColors.MutedSlate)
            Line("Al banco / retirado al cerrar", draft.takenAtShiftClose)
            Line("Quedó en gavetas para mañana", draft.totalCountedCash, bold = true)
            DiffLine("Diferencia de efectivo", draft.totalDifference)
        }
    }
}

@Composable
private fun ShiftCard(s: CashSession, onClick: () -> Unit) {
    val open = s.status == CashSessionStatus.OPEN
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    s.openedByEmployeeName.ifBlank { s.cashierName } + " · " + time(s.openedAt) +
                        (s.closedAt?.let { " – ${time(it)}" } ?: ""),
                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    when {
                        open -> "Abierta"
                        s.receivedByEmployeeName.isNotBlank() -> "Entregó a ${s.receivedByEmployeeName}"
                        else -> "Cierre del día"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (open) BrandColors.AccentDeep else BrandColors.MutedSlate
                )
            }
            Line("Ventas", s.totalSales)
            Text("Efectivo Q ${s.totalCashSales.fmtMoney(2)} · tarjeta Q ${s.totalCardSales.fmtMoney(2)}" +
                (if (s.totalTransferSales > 0.004) " · transf. Q ${s.totalTransferSales.fmtMoney(2)}" else ""),
                style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
            if (open) return@Column
            if (s.autoClosed) {
                Text("La cerró el sistema: nadie contó el efectivo.", style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.Danger, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Line("Efectivo esperado", s.expectedCash)
            Line("Efectivo contado", s.countedCash)
            DiffLine("Diferencia", s.difference)
            val expectedCard = s.expectedCardVouchers.takeIf { it > 0.004 } ?: s.expectedCardNow
            s.countedCardVouchers?.let { DiffLine("Vouchers Q ${it.fmtMoney(2)} de Q ${expectedCard.fmtMoney(2)}",
                closeCountDifference(expectedCard, it) ?: 0.0) }
            s.countedTransfers?.let { DiffLine("Transferencias Q ${it.fmtMoney(2)} de Q ${s.expectedTransferNow.fmtMoney(2)}",
                closeCountDifference(s.expectedTransferNow, it) ?: 0.0) }
            s.countedPedidosYa?.let { DiffLine("PedidosYa Q ${it.fmtMoney(2)} de Q ${s.totalPedidosYaSales.fmtMoney(2)}",
                closeCountDifference(s.totalPedidosYaSales, it) ?: 0.0) }
            if (s.closingWithdrawal > 0.004) {
                Line(if (s.closingBankDepositId.isNotBlank()) "Al banco" else "Retirado", s.closingWithdrawal)
            }
            if (s.handoverRecorded) {
                Line(if (s.receivedByEmployeeName.isNotBlank()) "Entregó en efectivo" else "Quedó para mañana",
                    s.carriedOutCash, bold = true)
            }
        }
    }
}

@Composable
private fun SpotChecksCard(checks: List<SpotCashCheck>) {
    SummaryCard("Cuadres sorpresa del día") {
        checks.forEach { c -> DiffLine("${c.registerName} · ${c.checkedByUserName}", c.difference) }
    }
}

@Composable
private fun SummaryCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            content()
        }
    }
}

@Composable
private fun Line(label: String, amount: Double, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text("Q ${amount.fmtMoney(2)}", style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Diferencia en verde (sobra), rojo (falta) o "cuadra". */
@Composable
private fun DiffLine(label: String, diff: Double) {
    val color = when {
        diff > 0.01 -> BrandColors.Success
        diff < -0.01 -> BrandColors.Danger
        else -> BrandColors.TextSlate
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(if (abs(diff) <= 0.01) "Cuadra" else "Q ${diff.fmtMoney(2)}",
            style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

private fun time(ms: Long): String {
    val t = kotlin.time.Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}

/** "2026-10-26" → "26/10/2026". */
private fun dayLabel(yyyyMmDd: String): String =
    yyyyMmDd.split("-").takeIf { it.size == 3 }?.let { (y, m, d) -> "$d/$m/$y" } ?: yyyyMmDd
