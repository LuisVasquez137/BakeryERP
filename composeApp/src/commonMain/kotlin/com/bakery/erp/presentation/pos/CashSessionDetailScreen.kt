package com.bakery.erp.presentation.pos

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CashMovementTotals
import com.bakery.erp.model.netCashEffect
import com.bakery.erp.model.isReverted
import com.bakery.erp.model.isAdjustment
import com.bakery.erp.model.cashMovementTotals
import com.bakery.erp.model.CashMovement
import com.bakery.erp.model.CashMovementType
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.CashSessionStatus
import com.bakery.erp.model.Permission
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.util.fmtMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashSessionDetailScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CashSessionViewModel,
    /**
     * Necesario para eliminar el gasto que salió de esta gaveta. Es el MISMO `deletePurchase`
     * del módulo de Compras —revierte inventario, gastos y efectivo con las guardas ya
     * probadas—; un borrado propio de esta pantalla sería un cuarto camino que mueve
     * inventario.
     */
    purchaseViewModel: com.bakery.erp.presentation.purchases.PurchaseViewModel,
    /**
     * Abre la compra que provocó un movimiento de gasto. Es el camino para corregirle el
     * monto o borrarla cuando resulta que no salió de esta caja: sin él, la cajera veía el
     * faltante en su gaveta y no tenía a dónde ir a arreglarlo.
     */
    onNavigateToPurchase: (purchaseId: String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = authState.user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    var showTransfer by remember { mutableStateOf(false) }
    // Gasto que se está por eliminar. El diálogo dice qué y cuánto antes de confirmar: esto
    // devuelve efectivo a la caja y revierte inventario, no es un "deshacer" cualquiera.
    var deleting by remember { mutableStateOf<CashMovement?>(null) }
    // Movimiento que se está corrigiendo: se teclea el monto nuevo y la diferencia queda como
    // un ajuste, sin reescribir el original.
    var correcting by remember { mutableStateOf<CashMovement?>(null) }
    val totals = remember(state.movements) { cashMovementTotals(state.movements) }

    LaunchedEffect(companyId, sessionId) {
        if (companyId.isNotEmpty()) viewModel.observeSession(companyId, sessionId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    // El borrado lo hace el VM de compras, que no sabe de esta lista: sin recargar acá, el
    // movimiento de devolución aparecía solo al salir y volver a entrar, y parecía que
    // eliminar no había hecho nada.
    val purchaseState by purchaseViewModel.uiState.collectAsState()
    LaunchedEffect(purchaseState.isDeleted) {
        if (purchaseState.isDeleted) {
            purchaseViewModel.clearDeleted()
            viewModel.observeSession(companyId, sessionId)
        }
    }
    LaunchedEffect(purchaseState.error) {
        purchaseState.error?.let { snackbar.showSnackbar(it); purchaseViewModel.clearError() }
    }

    val session = state.currentSession

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.currentSession?.status == CashSessionStatus.OPEN)
                            "Movimientos de caja" else "Detalle de sesión",
                        fontWeight = FontWeight.SemiBold
                    )
                },
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
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // El fondo inicial y las ventas del turno NO van acá: eso es el cuadre, y
                // mezclarlo convierte la pantalla en un resumen a medias que compite con el
                // cierre. Mientras la caja está abierta, esto contesta una sola pregunta —qué
                // entró y qué salió que no fue una venta— y esa es la que se hace quien
                // anotó algo mal. Cerrada, el encabezado sí sirve: es el comprobante del cierre.
                if (session.status != CashSessionStatus.OPEN) {
                    item { SessionHeaderCard(session) }
                }
                item { MovementTotalsCard(totals) }
                item {
                    Text(
                        "Entradas y salidas de efectivo",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (state.movements.isEmpty()) {
                    item {
                        Text(
                            "Todavía no se anotó ningún gasto ni ingreso en esta caja.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    items(state.movements, key = { it.id }) { m ->
                        MovementRow(
                            m = m,
                            // Corregir y eliminar solo mientras la caja siga abierta: cerrada,
                            // su cuadre ya lo firmó alguien. Ver `canManageOwnDrawerPurchase`.
                            reverted = m.isReverted(state.movements),
                            netAmount = netCashEffect(m, state.movements),
                            onCorrect = if (canAct(session, m, state.movements)) {
                                // El gasto se corrige donde se anotó —tiene renglones,
                                // catálogo y foto—; un ingreso es un monto y una razón, así que
                                // se corrige acá mismo sin cambiar de pantalla.
                                if (m.sourcePurchaseId.isNotEmpty()) {
                                    { onNavigateToPurchase(m.sourcePurchaseId) }
                                } else {
                                    { correcting = m }
                                }
                            } else null,
                            onDelete = if (canAct(session, m, state.movements)) {
                                { deleting = m }
                            } else null,
                        )
                    }
                }
                if (session.status == CashSessionStatus.OPEN) {
                    item {
                        OutlinedButton(
                            onClick = { showTransfer = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("Transferir efectivo a otra caja") }
                    }
                }
            }
        }
        deleting?.let { mov ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("¿Eliminar este gasto?") },
                text = {
                    Text(
                        if (mov.sourcePurchaseId.isNotEmpty()) {
                            "\"${mov.reason}\" por Q ${mov.amount.fmtMoney(2)}. " +
                                "Se devuelve ese efectivo a la caja y se revierte lo que haya " +
                                "entrado al inventario. Queda registrado en los movimientos."
                        } else {
                            "\"${mov.reason}\" por Q ${mov.amount.fmtMoney(2)}. " +
                                "Se anula del efectivo de la caja. Queda registrado en los " +
                                "movimientos: el renglón no desaparece."
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        user?.let { u ->
                            // El gasto se borra por su COMPRA, para que se revierta también el
                            // inventario y el gasto del mes; el ingreso no tiene documento
                            // detrás y se revierte con un movimiento contrario.
                            if (mov.sourcePurchaseId.isNotEmpty()) {
                                purchaseViewModel.deletePurchase(companyId, mov.sourcePurchaseId, u)
                            } else {
                                viewModel.adjustMovement(companyId, sessionId, mov, 0.0, u)
                            }
                        }
                        deleting = null
                    }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { deleting = null }) { Text("Cancelar") }
                }
            )
        }

        correcting?.let { mov ->
            var newAmountText by remember(mov.id) { mutableStateOf(mov.amount.fmtMoney(2)) }
            val parsed = newAmountText.replace(DECIMAL_COMMA, DECIMAL_DOT).toDoubleOrNull() ?: 0.0
            AlertDialog(
                onDismissRequest = { correcting = null },
                title = { Text("Corregir monto") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("\"${mov.reason}\", anotado por Q ${mov.amount.fmtMoney(2)}.")
                        OutlinedTextField(
                            value = newAmountText,
                            onValueChange = { newAmountText = it },
                            label = { Text("Monto correcto (Q)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        Text(
                            "El renglón original no se borra: queda la diferencia anotada " +
                                "aparte, para que se vea qué cambió.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = parsed > 0.0,
                        onClick = {
                            user?.let { u ->
                                viewModel.adjustMovement(companyId, sessionId, mov, parsed, u)
                            }
                            correcting = null
                        }
                    ) { Text("Guardar") }
                },
                dismissButton = {
                    TextButton(onClick = { correcting = null }) { Text("Cancelar") }
                }
            )
        }

        // Borrar una compra revierte inventario, gastos del mes y efectivo, y eso tarda
        // varios segundos contra Firestore. Sin overlay, el diálogo se cerraba y la pantalla
        // quedaba idéntica un buen rato: se lee como que el botón no hizo nada, y el segundo
        // toque es lo que produce el borrado doble que las guardas después tienen que atajar.
        LoadingOverlay(
            visible = purchaseState.isProcessing || state.isLoading,
            message = if (purchaseState.isProcessing) "Eliminando el gasto…" else "Guardando…"
        )

        if (showTransfer && user != null) {
            CashTransferDialog(
                companyId = companyId,
                currentSession = session,
                currentUser = user,
                onDismiss = { showTransfer = false },
                onTransferred = {
                    showTransfer = false
                    viewModel.observeSession(companyId, sessionId)
                },
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun SessionHeaderCard(session: CashSession) {
    val statusColor = if (session.status == CashSessionStatus.OPEN)
        Color(0xFF1B5E20) else MaterialTheme.colorScheme.outline
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${session.branchName} · ${session.registerName}",
                     fontWeight = FontWeight.SemiBold)
                AssistChip(
                    onClick = {},
                    label = { Text(CashSessionStatus.label(session.status)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = statusColor)
                )
            }
            Text("Cajero: ${session.cashierName}",
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Fondo inicial"); Text("Q ${session.openingBalance.fmtMoney(2)}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ventas totales"); Text("Q ${session.totalSales.fmtMoney(2)}",
                                              fontWeight = FontWeight.SemiBold)
            }
            Text("Efectivo: Q ${session.totalCashSales.fmtMoney(2)} · " +
                 "Tarjeta: Q ${session.totalCardSales.fmtMoney(2)} · " +
                 "Transf: Q ${session.totalTransferSales.fmtMoney(2)}",
                 style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.outline)
            Text("# ventas: ${session.saleCount}",
                 style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.outline)
            if (session.status == CashSessionStatus.CLOSED) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Esperado"); Text("Q ${session.expectedCash.fmtMoney(2)}")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Contado"); Text("Q ${session.countedCash.fmtMoney(2)}")
                }
                val diffColor = when {
                    session.difference > 0.01 -> Color(0xFF1B5E20)
                    session.difference < -0.01 -> Color(0xFFB00020)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Diferencia", fontWeight = FontWeight.SemiBold, color = diffColor)
                    Text("Q ${session.difference.fmtMoney(2)}",
                         fontWeight = FontWeight.Bold, color = diffColor)
                }
                session.notes?.let { Text("Notas: $it",
                                          style = MaterialTheme.typography.bodySmall,
                                          color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** Coma y punto decimal, sacados de la expresión para no pelear con el escapado. */
private const val DECIMAL_COMMA = ","
private const val DECIMAL_DOT = "."

/**
 * Lo que salió y lo que entró, sin las ventas.
 *
 * Va arriba de la lista porque es la pregunta con la que uno llega —"¿cuánto se gastó de mi
 * caja hoy?"— y contarlo a mano sobre los renglones es justo lo que se equivoca, sobre todo con
 * correcciones de por medio. Los números salen de `cashMovementTotals`, que agrupa cada ajuste
 * con el movimiento que corrige en vez de contarlo como un movimiento más.
 */
@Composable
private fun MovementTotalsCard(totals: CashMovementTotals) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Gastos", style = MaterialTheme.typography.labelMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Q ${totals.gastos.fmtMoney(2)}", fontWeight = FontWeight.Bold,
                     color = Color(0xFFB00020))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Ingresos", style = MaterialTheme.typography.labelMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Q ${totals.ingresos.fmtMoney(2)}", fontWeight = FontWeight.Bold,
                     color = Color(0xFF1B5E20))
            }
        }
    }
}

/**
 * Qué se puede hacer con un movimiento: solo con el gasto de una caja todavía abierta, que
 * tenga compra detrás y que no se haya revertido ya.
 */
private fun canAct(
    session: CashSession,
    m: CashMovement,
    all: List<CashMovement>
): Boolean {
    if (session.status != CashSessionStatus.OPEN) return false
    // Un ajuste no se ajusta: se corrige el original, que es lo que la gente busca.
    if (m.isAdjustment() || m.isReverted(all)) return false
    // Un traslado NO se toca desde acá: su espejo vive en la otra gaveta, y compensarlo solo
    // de este lado haría aparecer dinero de la nada en la otra caja. Se deshace con otro
    // traslado. Un ajuste de cuadre sorpresa tampoco: documenta un conteo que ya se firmó.
    if (m.transferId != null || m.spotCheckId != null) return false
    // Un abono a crédito o un adelanto se corrige en la cuenta de crédito, donde tiene que
    // cuadrar el saldo; compensarlo solo acá dejaría la deuda diciendo otra cosa.
    if (m.sourceCreditMovementId.isNotEmpty()) return false
    if (m.sourceDepositId.isNotEmpty()) return false
    return m.type == CashMovementType.EXPENSE && m.sourcePurchaseId.isNotEmpty() ||
        m.type == CashMovementType.DEPOSIT ||
        m.type == CashMovementType.FUND_ADDITION ||
        m.type == CashMovementType.WITHDRAWAL
}

@Composable
private fun MovementRow(
    m: CashMovement,
    reverted: Boolean = false,
    /** Efecto real sobre el efectivo, ya aplicadas las correcciones. Ver `netCashEffect`. */
    netAmount: Double = 0.0,
    onCorrect: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val sign = CashMovementType.sign(m.type)
    val signColor = when {
        sign > 0 -> Color(0xFF1B5E20)
        sign < 0 -> Color(0xFFB00020)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(CashMovementType.listLabel(m), fontWeight = FontWeight.SemiBold)
                    if (reverted) {
                        Text("Ya eliminado — el efectivo volvió a la caja",
                             style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.error)
                    } else if (!m.isAdjustment() &&
                        kotlin.math.abs(kotlin.math.abs(netAmount) - m.amount) > 0.01
                    ) {
                        // El original no se reescribe, así que sin esta línea la fila seguiría
                        // mostrando el monto viejo y nadie entendería por qué no cuadra.
                        Text("Corregido a Q ${kotlin.math.abs(netAmount).fmtMoney(2)}",
                             style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.primary)
                    }
                    Text(m.reason, style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Por ${m.createdByUserName}", style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.outline)
                }
                Text("${if (sign >= 0) "+" else "-"}Q ${m.amount.fmtMoney(2)}",
                     fontWeight = FontWeight.Bold, color = signColor)
            }
            // Botones explícitos y no una fila tocable: son dos acciones distintas y una de
            // ellas devuelve efectivo y revierte inventario. Un toque ambiguo sobre la fila
            // deja al usuario adivinando cuál de las dos va a pasar.
            if (onCorrect != null || onDelete != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onCorrect != null) {
                        TextButton(onClick = onCorrect) { Text("Corregir") }
                    }
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text("Eliminar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
