package com.bakery.erp.presentation.pos

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashSessionCloseScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    onClosed: (sessionId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CashSessionViewModel
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

    LaunchedEffect(companyId, sessionId) {
        if (companyId.isNotEmpty()) viewModel.observeSession(companyId, sessionId)
    }
    // Entrar ya queda en la bitácora: la pantalla muestra cuánto debería haber, se cierre o no.
    LaunchedEffect(companyId, sessionId, user?.id) {
        if (companyId.isNotEmpty() && user != null) viewModel.enterCloseScreen(companyId, sessionId, user)
    }
    DisposableEffect(sessionId) { onDispose { viewModel.clearCloseGate() } }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.isClosed) {
        if (state.isClosed) {
            viewModel.clearClosed()
            onClosed(sessionId)
        }
    }

    val session = state.currentSession
    LaunchedEffect(companyId, session?.branchId) {
        if (companyId.isNotEmpty() && session != null) viewModel.loadEmployees(companyId, session.branchId)
    }
    val gate = state.closeGate?.takeIf { it.sessionId == sessionId }
    val expected = session?.expectedCashNow ?: 0.0
    val counted = closingCounts.entries.sumOf { (code, qty) ->
        (CashDenominations.byCode(code)?.value ?: 0.0) * qty
    }
    val diff = counted - expected
    val hasCount = closingCounts.values.any { it > 0 }

    Scaffold(
        topBar = {
            TopAppBar(
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
                // El detalle va entre el resumen y el conteo: se revisa qué salió ANTES de
                // contar, que es cuando todavía se puede ir a buscar el comprobante que falta.
                CashCloseMovementsCard(
                    movements = state.movements,
                    purchasesById = state.movementPurchases
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

                EmployeePickerField(
                    label = "¿Quién recibe la caja?",
                    employees = state.employees,
                    selected = receivedBy,
                    chosen = receiverChosen,
                    onSelect = { receivedBy = it; receiverChosen = true },
                    noneLabel = "Nadie: es el cierre del día"
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas (opcional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { showConfirm = true },
                    enabled = hasCount && receiverChosen && !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar caja")
                }
            }
        }
    }

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
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("¿Cerrar la caja?") },
            text = {
                Text("Una vez cerrada no se podrán registrar más ventas en esta sesión. " +
                     "La diferencia (Q ${diff.fmtMoney(2)}) quedará registrada.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.closeSession(
                        companyId, sessionId, counted, notes.trim().ifEmpty { null }, closingCounts,
                        receivedBy = receivedBy, currentUser = user
                    )
                }) { Text("Sí, cerrar") }
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
            // Fiado: se vendió pero no hay dinero. Se muestra para que nadie lo busque en la gaveta.
            if (session.totalCreditSales > 0.0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ventas a crédito (sin dinero)"); Text("Q ${session.totalCreditSales.fmtMoney(2)}")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ingresos extras"); Text("Q ${session.totalMovementsIn.fmtMoney(2)}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Gastos y retiros"); Text("Q ${session.totalMovementsOut.fmtMoney(2)}")
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
