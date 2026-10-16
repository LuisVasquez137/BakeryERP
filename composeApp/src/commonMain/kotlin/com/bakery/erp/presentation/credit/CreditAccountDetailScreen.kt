package com.bakery.erp.presentation.credit

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CreditAccount
import com.bakery.erp.model.CreditAccountType
import com.bakery.erp.model.CreditMovement
import com.bakery.erp.model.CreditMovementType
import com.bakery.erp.model.Permission
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.User
import com.bakery.erp.model.creditStatement
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtDateTime
import com.bakery.erp.util.fmtMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditAccountDetailScreen(
    accountId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CreditViewModel,
    documentPrinter: com.bakery.erp.domain.DocumentPrinter? = null,
    bluetoothPrinterManager: com.bakery.erp.domain.BluetoothPrinterManager? = null
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val user = authState.user
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(companyId, accountId) {
        if (companyId.isNotEmpty()) viewModel.loadAccount(companyId, accountId)
    }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() } }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }

    val account = state.selected?.takeIf { it.id == accountId }
    val canManage = user?.hasPermission(Permission.MANAGE_CREDIT_ACCOUNTS) == true

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text(account?.name ?: "Cuenta de crédito", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    if (account != null && documentPrinter != null && bluetoothPrinterManager != null) {
                        com.bakery.erp.presentation.production.DocumentPrintButton(
                            documentPrinter = documentPrinter,
                            bluetoothPrinterManager = bluetoothPrinterManager,
                            snackbarHostState = snackbar,
                            contentDescription = "Imprimir estado de cuenta",
                            buildDocument = {
                                com.bakery.erp.util.creditStatementDocument(
                                    account, state.movements,
                                    kotlin.time.Clock.System.now().toEpochMilliseconds()
                                )
                            }
                        )
                    }
                    if (canManage && account != null) {
                        IconButton(onClick = { onNavigateToEdit(account.id) }) { Icon(Icons.Default.Edit, "Editar") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (account == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isDetailLoading) CircularProgressIndicator() else Text("Cuenta no encontrada")
            }
            return@Scaffold
        }
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                CreditAccountPanel(
                    account = account,
                    movements = state.movements,
                    canManage = canManage,
                    isSaving = state.isSaving,
                    companyId = companyId,
                    user = user,
                    viewModel = viewModel
                )
            }
        }
    }
}

/**
 * Saldo, acciones y estado de cuenta. Lo usan la pantalla de la cuenta y la pestaña
 * "Créditos" de la ficha del colaborador, para que las dos digan exactamente lo mismo.
 */
@Composable
fun CreditAccountPanel(
    account: CreditAccount,
    movements: List<CreditMovement>,
    canManage: Boolean,
    isSaving: Boolean,
    companyId: String,
    user: User?,
    viewModel: CreditViewModel
) {
    var dialog by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = BrandColors.PrimaryContainer),
            shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    listOfNotNull(
                        CreditAccountType.label(account.type),
                        account.taxId.ifBlank { null }?.let { "NIT $it" },
                        account.phone.ifBlank { null },
                        if (!account.active) "Inactiva" else null
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate
                )
                Spacer(Modifier.height(6.dp))
                Text("Debe", color = BrandColors.Primary)
                Text("Q ${account.balance.fmtMoney(2)}", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (account.balance > 0.005) BrandColors.AccentDeep else BrandColors.Primary)
                if (account.balance < -0.005) {
                    Text("Saldo a favor: pagó de más.", style = MaterialTheme.typography.labelSmall)
                }
                if (account.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(account.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (canManage) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { dialog = "pay" }, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                    Text("Registrar pago")
                }
                if (account.isEmployee) {
                    OutlinedButton(onClick = { dialog = "advance" }, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                        Text("Adelanto")
                    }
                }
                OutlinedButton(onClick = { dialog = "adjust" }, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                    Text("Ajuste")
                }
            }
            Text(
                "Los pagos en efectivo en tienda se cobran desde la caja abierta (POS → Cobrar crédito), " +
                    "para que entren al cuadre. Aquí se registran transferencias y lo que recibe administración.",
                style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate
            )
        }

        if (account.requiresPerson) {
            Text("Personas autorizadas", fontWeight = FontWeight.SemiBold)
            if (account.persons.isEmpty()) Text("Ninguna.", color = BrandColors.MutedSlate)
            account.persons.forEach { p ->
                Text(
                    "• ${p.name}" + (if (p.idDocument.isNotBlank()) " · DPI ${p.idDocument}" else "") +
                        (if (!p.active) " (inactiva)" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (p.active) MaterialTheme.colorScheme.onSurface else BrandColors.MutedSlate
                )
            }
        }

        Text("Estado de cuenta", fontWeight = FontWeight.SemiBold)
        if (movements.isEmpty()) {
            Text("Sin movimientos todavía.", color = BrandColors.MutedSlate)
        } else {
            // Del más nuevo al más viejo, con el saldo que quedó después de cada renglón.
            creditStatement(movements).reversed().forEach { (m, running) ->
                StatementRow(m, running)
                HorizontalDivider()
            }
        }
    }

    val u = user ?: return
    when (dialog) {
        "pay" -> AmountDialog(
            title = "Registrar pago",
            help = "Pago recibido fuera de la caja: transferencia, depósito o efectivo que recibió administración.",
            showMethod = true,
            suggested = account.balance.coerceAtLeast(0.0),
            onDismiss = { dialog = null },
            onConfirm = { amount, method, note ->
                viewModel.registerPayment(companyId, account, amount, method, note, u); dialog = null
            }
        )
        "advance" -> AmountDialog(
            title = "Adelanto de caja general",
            help = "Dinero entregado por administración. Si sale de una caja del POS, dalo desde el POS " +
                "(menú → Adelanto a colaborador) para que la gaveta lo descuente.",
            onDismiss = { dialog = null },
            onConfirm = { amount, _, note ->
                viewModel.registerAdvance(companyId, account, amount, note, u); dialog = null
            }
        )
        "adjust" -> AdjustDialog(
            onDismiss = { dialog = null },
            onConfirm = { signed, reason ->
                viewModel.registerAdjustment(companyId, account, signed, reason, u); dialog = null
            }
        )
    }
}

@Composable
private fun StatementRow(m: CreditMovement, runningBalance: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(CreditMovementType.label(m.type), fontWeight = FontWeight.SemiBold)
            val detail = listOfNotNull(
                m.description.ifBlank { null },
                m.personName.ifBlank { null }?.let { if (m.isCharge && m.type == CreditMovementType.SALE) "Llevó: $it" else null },
                m.method.ifBlank { null }?.let { SalePaymentMethod.label(it) },
                m.registerName.ifBlank { null }
            ).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall)
            Text(
                listOfNotNull(m.createdAt.fmtDateTime().ifBlank { null }, m.createdByUserName.ifBlank { null })
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (m.amount > 0) "+ " else "− ") + "Q ${kotlin.math.abs(m.amount).fmtMoney(2)}",
                fontWeight = FontWeight.Bold,
                color = if (m.amount > 0) BrandColors.AccentDeep else androidx.compose.ui.graphics.Color(0xFF1B5E20)
            )
            Text("Saldo Q ${runningBalance.fmtMoney(2)}", style = MaterialTheme.typography.labelSmall,
                color = BrandColors.MutedSlate)
        }
    }
}

@Composable
private fun AmountDialog(
    title: String,
    help: String,
    showMethod: Boolean = false,
    suggested: Double = 0.0,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, method: String, note: String) -> Unit
) {
    var amountText by remember { mutableStateOf(if (suggested > 0) suggested.fmtMoney(2).replace(",", "") else "") }
    var method by remember { mutableStateOf(SalePaymentMethod.TRANSFER) }
    var note by remember { mutableStateOf("") }
    val amount = amountText.replace(",", "").toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(help, style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monto") }, prefix = { Text("Q ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (showMethod) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SalePaymentMethod.all.forEach { m ->
                            FilterChip(selected = method == m, onClick = { method = m },
                                label = { Text(SalePaymentMethod.label(m)) })
                        }
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note = it },
                    label = { Text("Nota (boleta, cheque, motivo)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(amount, method, note.trim()) }, enabled = amount > 0) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Ajuste con dirección explícita. Se usa sobre todo al arrancar: pasar lo que cada quien ya
 * debía en el cuaderno como "saldo anterior".
 */
@Composable
private fun AdjustDialog(onDismiss: () -> Unit, onConfirm: (signedAmount: Double, reason: String) -> Unit) {
    var increase by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("Saldo anterior (cuaderno)") }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajuste") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = increase, onClick = { increase = true; if (reason.isBlank()) reason = "Saldo anterior (cuaderno)" },
                        label = { Text("Aumenta lo que debe") })
                    FilterChip(selected = !increase, onClick = { increase = false; if (reason == "Saldo anterior (cuaderno)") reason = "" },
                        label = { Text("Baja lo que debe") })
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monto") }, prefix = { Text("Q ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = reason, onValueChange = { reason = it },
                    label = { Text("Motivo *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Queda en el estado de cuenta con este motivo; no se puede borrar.",
                    style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(if (increase) amount else -amount, reason.trim()) },
                enabled = amount > 0 && reason.isNotBlank()) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
