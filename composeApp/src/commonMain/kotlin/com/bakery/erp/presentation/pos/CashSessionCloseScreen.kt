package com.bakery.erp.presentation.pos

import com.bakery.erp.ui.BrandTopAppBar

import com.bakery.erp.domain.PIN_LENGTH
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.expectedCashNow
import com.bakery.erp.model.CashDenominations
import com.bakery.erp.model.CashSession
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.pos.components.DenominationCounter
import com.bakery.erp.presentation.pos.components.DenominationCountsSaver
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.util.fmtMoney
import com.bakery.erp.model.hasPermission

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashSessionCloseScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    onClosed: (sessionId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CashSessionViewModel,
    /** Se entregó la caja: el siguiente turno ya está abierto y sigue en el POS. */
    onHandedOver: (newSessionId: String) -> Unit = onClosed,
    /** Para mostrar lo que salió del mostrador sin venta en este turno. */
    counterRemovalViewModel: CounterRemovalViewModel? = null
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    var closingCounts by rememberSaveable(stateSaver = DenominationCountsSaver) {
        mutableStateOf<Map<String, Int>>(emptyMap())
    }
    var notes by rememberSaveable { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    val user = authState.user
    // Quién recibe la gaveta. "Nadie" (fin del día) también es una respuesta: por eso aparte.
    var receivedBy by remember { mutableStateOf<com.bakery.erp.model.Employee?>(null) }
    var receiverChosen by remember { mutableStateOf(false) }
    var outOfScheduleAccepted by remember { mutableStateOf(false) }
    val canWithdraw = user?.hasPermission(com.bakery.erp.model.Permission.WITHDRAW_AT_CLOSE) == true
    val canBankDeposit = user?.hasPermission(com.bakery.erp.model.Permission.BANK_DEPOSIT) == true
    var withdrawalText by rememberSaveable { mutableStateOf("") }
    // Lo que sale al cerrar va al banco por omisión (la meta es que todo termine allí); el
    // retiro queda para casos raros y con su propio permiso.
    var withdrawalToBank by rememberSaveable { mutableStateOf(true) }
    var depositBank by rememberSaveable { mutableStateOf("") }
    var depositCarrier by remember { mutableStateOf<com.bakery.erp.model.Employee?>(null) }
    var depositCarrierPin by remember { mutableStateOf("") }
    val carrierPinRequired = user?.let { carrierMustConfirm(it) } == true
    val slipActions = remember { BankSlipActions() }

    LaunchedEffect(companyId, sessionId) {
        if (companyId.isNotEmpty()) {
            viewModel.observeSession(companyId, sessionId)
            viewModel.loadSessionVoidedSales(companyId, sessionId)
            viewModel.loadSessionBankDeposits(companyId, sessionId)
            counterRemovalViewModel?.loadForSession(companyId, sessionId)
        }
    }
    val removals = counterRemovalViewModel?.uiState?.collectAsState()?.value?.sessionRemovals
        ?.filter { it.sessionId == sessionId }.orEmpty()
    // Entrar ya queda en la bitácora: la pantalla muestra cuánto debería haber, se cierre o no.
    LaunchedEffect(companyId, sessionId, user?.id) {
        if (companyId.isNotEmpty() && user != null) viewModel.enterCloseScreen(companyId, sessionId, user)
    }
    DisposableEffect(sessionId) { onDispose { viewModel.clearCloseGate() } }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.bankDepositNotice) {
        state.bankDepositNotice?.let { snackbar.showSnackbar(it); viewModel.clearBankDepositNotice() }
    }
    LaunchedEffect(state.isClosed) {
        if (state.isClosed) {
            val next = state.handedOverSessionId
            // Equipo compartido: quien recibió ya puso su PIN, así que el equipo pasa a ella
            // y entra directo a vender en su turno.
            val receiver = state.handedOverTo
            val account = authState.accountUser
            if (receiver != null && authState.operator != null && account != null && receiver.mustChangePin) {
                // PIN temporal: que pase por la pantalla de PIN y escoja el suyo.
                authViewModel.lockOperator()
            } else if (receiver != null && authState.operator != null && account != null) {
                authViewModel.startOperatorSession(
                    com.bakery.erp.model.OperatorSession(
                        employeeId = receiver.employeeId,
                        name = receiver.name,
                        photoUrl = receiver.photoUrl,
                        branchId = receiver.branchId,
                        permissions = receiver.permissions,
                        accountId = account.id
                    )
                )
            }
            viewModel.clearClosed()
            viewModel.clearHandedOver()
            if (next != null) onHandedOver(next) else onClosed(sessionId)
        }
    }

    val session = state.currentSession
    LaunchedEffect(companyId, session?.branchId) {
        if (companyId.isNotEmpty() && session != null) viewModel.loadEmployees(companyId, session.branchId)
    }
    LaunchedEffect(companyId, session?.registerId) {
        session?.let { viewModel.loadPendingBankDeposits(companyId, it.registerId) }
    }
    // Los del turno y los que la caja tiene sin boleta de turnos anteriores (bloquean).
    val pendingDeposits = state.pendingBankDeposits.filter { it.registerId == session?.registerId }
    val shownDeposits = (state.sessionBankDeposits.filter { it.sessionId == sessionId } + pendingDeposits)
        .distinctBy { it.id }.sortedBy { it.createdAt }
    val gate = state.closeGate?.takeIf { it.sessionId == sessionId }
    val expected = session?.expectedCashNow ?: 0.0
    val counted = closingCounts.entries.sumOf { (code, qty) ->
        (CashDenominations.byCode(code)?.value ?: 0.0) * qty
    }
    val diff = counted - expected
    val hasCount = closingCounts.values.any { it > 0 }
    val canTakeOut = canWithdraw || canBankDeposit
    // Sin uno de los dos permisos, el destino es el que sí tiene.
    val toBank = canBankDeposit && (withdrawalToBank || !canWithdraw)
    val withdrawal = if (canTakeOut) parseMoneyInput(withdrawalText) else 0.0
    val withdrawalProblem = com.bakery.erp.model.closingWithdrawalProblem(counted, withdrawal)
        ?: if (toBank && withdrawal > 0.004)
            com.bakery.erp.model.bankDepositProblem(withdrawal, counted, depositBank, depositCarrier?.id.orEmpty())
                ?: carrierPinProblem(user, BankDepositDraft(depositBank, depositCarrier, carrierPin = depositCarrierPin))
        else null
    val handover = session?.let { com.bakery.erp.model.shiftHandover(it, counted, withdrawal) }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Cerrar caja", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (session == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        AdaptiveContainer(modifier = Modifier.padding(padding), maxContentWidth = 600.dp) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SessionSummaryCard(session, expectedCash = expected)
                // No mueve dinero, pero el dueño lo lee junto al cuadre: lo que salió sin venderse.
                if (removals.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Salió del mostrador sin venta", fontWeight = FontWeight.SemiBold)
                            com.bakery.erp.model.counterRemovalSummary(removals).forEach {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                // El detalle va entre el resumen y el conteo: se revisa qué salió ANTES de
                // contar, que es cuando todavía se puede ir a buscar el comprobante que falta.
                CashCloseMovementsCard(
                    movements = state.movements,
                    purchasesById = state.movementPurchases
                )
                VoidedSalesCard(state.sessionVoidedSales)
                if (pendingDeposits.isNotEmpty()) {
                    Text("Hay ${pendingDeposits.size} depósito(s) al banco sin boleta: adjunta la boleta (o déjalo sin " +
                        "boleta con el motivo) para poder cerrar.",
                        color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium)
                }
                BankDepositsCard(
                    deposits = shownDeposits,
                    canAct = canBankDeposit,
                    onAttach = { slipActions.attachFor = it },
                    onNoSlip = { slipActions.noSlipFor = it }
                )

                Text("Conteo físico del efectivo · por denominaciones",
                    fontWeight = FontWeight.SemiBold)
                DenominationCounter(
                    counts = closingCounts,
                    onChange = { closingCounts = it }
                )

                if (hasCount) {
                    val diffColor = when {
                        diff > 0.01 -> Color(0xFF1B5E20)
                        diff < -0.01 -> Color(0xFFB00020)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Esperado"); Text("Q ${expected.fmtMoney(2)}")
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Contado"); Text("Q ${counted.fmtMoney(2)}")
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(when {
                                    diff > 0.01 -> "Sobrante"
                                    diff < -0.01 -> "Faltante"
                                    else -> "Cuadre exacto"
                                }, fontWeight = FontWeight.SemiBold, color = diffColor)
                                Text("Q ${diff.fmtMoney(2)}", fontWeight = FontWeight.Bold, color = diffColor)
                            }
                        }
                    }
                }

                if (canTakeOut && hasCount) {
                    if (canWithdraw && canBankDeposit) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = withdrawalToBank, onClick = { withdrawalToBank = true },
                                label = { Text("Depositar al banco") })
                            FilterChip(selected = !withdrawalToBank, onClick = { withdrawalToBank = false },
                                label = { Text("Retiro") })
                        }
                    }
                    OutlinedTextField(
                        value = withdrawalText,
                        onValueChange = { withdrawalText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(if (toBank) "Sacar para depositar al banco (opcional)" else "Retirar de la gaveta (opcional)") },
                        prefix = { Text("Q ") },
                        singleLine = true,
                        isError = withdrawalProblem != null,
                        supportingText = withdrawalProblem?.let { p -> { Text(p) } },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (toBank && withdrawal > 0.004) {
                        BankDepositFields(depositBank, { depositBank = it }, depositCarrier, { depositCarrier = it }, state.employees,
                            pinRequired = carrierPinRequired, pin = depositCarrierPin, onPin = { depositCarrierPin = it })
                        Text("Queda pendiente la boleta: el siguiente cierre de esta caja no se podrá hacer sin ella.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                EmployeePickerField(
                    label = "¿Quién recibe la caja?",
                    employees = state.employees,
                    selected = receivedBy,
                    chosen = receiverChosen,
                    onSelect = { receivedBy = it; receiverChosen = true },
                    noneLabel = "Nadie: es el cierre del día"
                )

                // Lo que sale de esta gaveta: al siguiente turno, o lo que queda para mañana.
                if (hasCount && receiverChosen && handover != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = com.bakery.erp.ui.theme.BrandColors.PrimaryContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                receivedBy?.let { "Recibe ${it.name}" } ?: "Se deja para mañana",
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Efectivo"); Text("Q ${handover.cash.fmtMoney(2)}", fontWeight = FontWeight.Bold)
                            }
                            if (receivedBy != null) {
                                // Los vouchers pasan de turno a turno; al final del día se recogen.
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Vouchers de tarjeta"); Text("Q ${handover.card.fmtMoney(2)}")
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Transferencias"); Text("Q ${handover.transfer.fmtMoney(2)}")
                                }
                            } else if (handover.card > 0.004 || handover.transfer > 0.004) {
                                Text("Vouchers del día para recoger: tarjeta Q ${handover.card.fmtMoney(2)} · " +
                                    "transferencias Q ${handover.transfer.fmtMoney(2)}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            if (withdrawal > 0.004) {
                                Text(if (toBank) "Van al banco Q ${withdrawal.fmtMoney(2)}" else "Se retiran Q ${withdrawal.fmtMoney(2)}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas (opcional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { showConfirm = true },
                    enabled = hasCount && receiverChosen && withdrawalProblem == null && !state.isLoading &&
                        pendingDeposits.isEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar caja")
                }
            }
        }
    }

    BankSlipActionDialogs(
        actions = slipActions,
        companyId = companyId,
        user = user,
        viewModel = viewModel,
        isBusy = state.isBankDepositBusy,
        notice = state.bankDepositNotice,
        viewingSessionId = sessionId
    )

    // Fuera del horario de cuadre de ESTA caja: ya quedó en la bitácora y ya se avisó; acá se
    // pregunta si de verdad se va a cerrar a esta hora.
    if (gate?.outOfSchedule == true && !outOfScheduleAccepted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("¿Cierre de caja fuera de horario?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿Estás seguro de que harás el cierre de caja en un horario que no es el habitual?")
                    if (gate.scheduleLabel.isNotBlank()) {
                        Text("Horario de cuadre de esta caja: ${gate.scheduleLabel}",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Quedó registrado en la bitácora y se avisó al administrador.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { outOfScheduleAccepted = true }) { Text("Sí, continuar") }
            },
            dismissButton = {
                Button(onClick = onNavigateBack) { Text("Volver") }
            }
        )
    }

    if (showConfirm) {
        val receiver = receivedBy
        val needsReceiverPin = receiver != null && user != null && receiverMustConfirm(user, receiver)
        // remember, no rememberSaveable: un PIN no se guarda en el estado de la actividad.
        var receiverPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("¿Cerrar la caja?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Una vez cerrada no se podrán registrar más ventas en esta sesión. " +
                         "La diferencia (Q ${diff.fmtMoney(2)}) quedará registrada.")
                    if (needsReceiverPin && receiver != null) {
                        Text(
                            "${receiver.name} recibe la caja: que escriba su PIN para confirmar que la acepta.",
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = receiverPin,
                            onValueChange = { v -> receiverPin = v.filter { it.isDigit() }.take(PIN_LENGTH) },
                            label = { Text("PIN de ${receiver.name}") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !needsReceiverPin || receiverPin.length == PIN_LENGTH,
                    onClick = {
                        showConfirm = false
                        viewModel.closeSession(
                            companyId, sessionId, counted, notes.trim().ifEmpty { null }, closingCounts,
                            receivedBy = receivedBy, currentUser = user, withdrawal = withdrawal,
                            receiverPin = receiverPin.takeIf { needsReceiverPin },
                            withdrawalToBank = if (toBank && withdrawal > 0.004)
                                BankDepositDraft(depositBank, depositCarrier, carrierPin = depositCarrierPin) else null
                        )
                    }
                ) { Text("Sí, cerrar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun SessionSummaryCard(session: CashSession, expectedCash: Double) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${session.branchName} · ${session.registerName}",
                 fontWeight = FontWeight.SemiBold)
            Text("Cajero: ${session.cashierName}",
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Fondo inicial"); Text("Q ${session.openingBalance.fmtMoney(2)}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ventas efectivo"); Text("Q ${session.totalCashSales.fmtMoney(2)}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ventas tarjeta"); Text("Q ${session.totalCardSales.fmtMoney(2)}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ventas transferencia"); Text("Q ${session.totalTransferSales.fmtMoney(2)}")
            }
            // Como el fiado: venta sin dinero en la gaveta, para que nadie lo busque al contar.
            if (session.totalPedidosYaSales > 0.004) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ventas PedidosYa (sin dinero en gaveta)"); Text("Q ${session.totalPedidosYaSales.fmtMoney(2)}")
                }
            }
            // Fiado: se vendió pero no hay dinero. Se muestra para que nadie lo busque en la gaveta.
            if (session.totalCreditSales > 0.0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ventas a crédito (sin dinero)"); Text("Q ${session.totalCreditSales.fmtMoney(2)}")
                }
            }
            // Ya no están en las ventas de arriba: se muestran para que nadie las busque.
            if (session.voidedSaleCount > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ventas anuladas (${session.voidedSaleCount})", color = MaterialTheme.colorScheme.error)
                    Text("Q ${session.totalVoidedSales.fmtMoney(2)}", color = MaterialTheme.colorScheme.error)
                }
                if (session.totalVoidedNonCash > 0.004) {
                    Text("De esas, Q ${session.totalVoidedNonCash.fmtMoney(2)} con tarjeta/transferencia: " +
                        "el voucher sigue aquí y se devuelve por banco.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ingresos extras"); Text("Q ${session.totalMovementsIn.fmtMoney(2)}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Gastos, retiros y depósitos al banco"); Text("Q ${session.totalMovementsOut.fmtMoney(2)}")
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Efectivo esperado", fontWeight = FontWeight.SemiBold)
                Text("Q ${expectedCash.fmtMoney(2)}", fontWeight = FontWeight.Bold)
            }
            Text("# ventas: ${session.saleCount}",
                 style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.outline)
        }
    }
}
