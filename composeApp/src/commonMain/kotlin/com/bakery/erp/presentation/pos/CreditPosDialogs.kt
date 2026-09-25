package com.bakery.erp.presentation.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CreditAccount
import com.bakery.erp.model.CreditAccountType
import com.bakery.erp.model.CreditMovement
import com.bakery.erp.model.CreditMovementType
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtDate
import com.bakery.erp.util.fmtMoney

private val BILLS = listOf(1, 5, 10, 20, 50, 100, 200)

/**
 * Buscador de cuentas de crédito. Busca también por persona autorizada: la cajera oye "vengo
 * de parte del colegio, soy Ana" y puede teclear cualquiera de los dos nombres.
 */
@Composable
fun CreditAccountPicker(
    accounts: List<CreditAccount>,
    selected: CreditAccount?,
    onSelect: (CreditAccount) -> Unit,
    placeholder: String = "Buscar cuenta, persona o colaborador",
    maxHeight: Int = 240
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val matches = accounts
        .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.persons.any { p -> p.active && p.name.lowercase().contains(q) } }
        .sortedWith(compareBy<CreditAccount> { it.isEmployee }.thenBy { it.name.lowercase() })
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(Modifier.heightIn(max = maxHeight.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(matches, key = { it.id }) { acc ->
                val isSel = acc.id == selected?.id
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(acc) },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSel) BrandColors.PrimaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(acc.name, fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(CreditAccountType.label(acc.type), style = MaterialTheme.typography.labelSmall,
                                color = BrandColors.MutedSlate)
                        }
                        Text("Debe Q ${acc.balance.fmtMoney(2)}", style = MaterialTheme.typography.labelMedium,
                            color = if (acc.balance > 0.005) BrandColors.AccentDeep else BrandColors.MutedSlate)
                    }
                }
            }
        }
        if (matches.isEmpty()) {
            Text(
                if (accounts.isEmpty()) "No hay cuentas de crédito. Se crean en Inicio → Créditos."
                else "Ninguna cuenta coincide. Si la persona no está autorizada, no se le puede fiar.",
                style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate
            )
        }
    }
}

/**
 * La parte "a crédito" del cobro: cuenta + quién se lo lleva. Una institución exige elegir a
 * una persona autorizada; una persona o un colaborador se lo llevan ellos mismos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreditChargeSelector(
    accounts: List<CreditAccount>,
    selection: CreditCharge?,
    onChange: (CreditCharge?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CreditAccountPicker(
            accounts = accounts.filter { it.active },
            selected = selection?.account,
            onSelect = { acc ->
                onChange(
                    if (acc.requiresPerson) {
                        // Si solo hay una persona autorizada no hay nada que elegir.
                        val only = acc.activePersons.singleOrNull()
                        CreditCharge(acc, only?.id.orEmpty(), only?.name.orEmpty())
                    } else CreditCharge(acc, personName = acc.name)
                )
            },
            maxHeight = 200
        )
        val acc = selection?.account
        if (acc != null && acc.requiresPerson) {
            Text("¿Quién se lo lleva?", fontWeight = FontWeight.SemiBold)
            if (acc.activePersons.isEmpty()) {
                Text("Esta institución no tiene personas autorizadas.", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                acc.activePersons.forEach { p ->
                    FilterChip(
                        selected = selection.personId == p.id,
                        onClick = { onChange(selection.copy(personId = p.id, personName = p.name)) },
                        label = { Text(p.name) }
                    )
                }
            }
        }
    }
}

/**
 * "Cobrar crédito": el cliente llega a pagar lo que debe. Se elige la cuenta, se ve el saldo y
 * las últimas compras, y se cobra todo o una parte.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreditCollectDialog(
    accounts: List<CreditAccount>,
    movementsFor: (CreditAccount) -> List<CreditMovement>?,
    onSelectAccount: (CreditAccount) -> Unit,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (account: CreditAccount, amount: Double, method: String, tendered: Double, bills: Map<Int, Int>) -> Unit
) {
    var account by remember { mutableStateOf<CreditAccount?>(null) }
    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(SalePaymentMethod.CASH) }
    var tenderedText by remember { mutableStateOf("") }
    var bills by remember { mutableStateOf(emptyMap<Int, Int>()) }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val tendered = tenderedText.toDoubleOrNull() ?: 0.0
    val isCash = method == SalePaymentMethod.CASH
    val change = if (isCash && tenderedText.isNotEmpty()) (tendered - amount).coerceAtLeast(0.0) else 0.0
    val short = isCash && tenderedText.isNotEmpty() && tendered < amount

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Cobrar crédito", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val acc = account
                if (acc == null) {
                    CreditAccountPicker(
                        accounts = accounts.sortedByDescending { it.balance },
                        selected = null,
                        onSelect = {
                            account = it
                            amountText = it.balance.coerceAtLeast(0.0).fmt(2)
                            onSelectAccount(it)
                        }
                    )
                } else {
                    Card(colors = CardDefaults.cardColors(containerColor = BrandColors.PrimaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(acc.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { account = null }, enabled = !isBusy) { Text("Cambiar") }
                            }
                            Text("Debe Q ${acc.balance.fmtMoney(2)}", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge, color = BrandColors.AccentDeep)
                        }
                    }
                    val recent = movementsFor(acc)
                    if (recent == null) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else if (recent.isNotEmpty()) {
                        Text("Últimos movimientos", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        recent.take(6).forEach { m ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    "${m.createdAt.fmtDate()} · ${CreditMovementType.label(m.type)}" +
                                        (if (m.personName.isNotBlank() && m.type == CreditMovementType.SALE) " · ${m.personName}" else ""),
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text((if (m.amount > 0) "+" else "−") + "Q ${kotlin.math.abs(m.amount).fmtMoney(2)}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider()
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Monto que paga") }, prefix = { Text("Q ") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = {
                            if (amount > acc.balance + 0.004 && acc.balance > 0)
                                Text("Es más de lo que debe: le quedará saldo a favor.")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SalePaymentMethod.all.forEach { m ->
                            FilterChip(selected = method == m, onClick = { method = m },
                                label = { Text(SalePaymentMethod.label(m)) })
                        }
                    }
                    if (isCash) {
                        OutlinedTextField(
                            value = tenderedText,
                            onValueChange = { tenderedText = it.filter { c -> c.isDigit() || c == '.' }; bills = emptyMap() },
                            label = { Text("Le dieron") }, prefix = { Text("Q ") }, singleLine = true,
                            isError = short,
                            modifier = Modifier.fillMaxWidth()
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BILLS.forEach { v ->
                                AssistChip(
                                    onClick = {
                                        val trackable = bills.isNotEmpty() || tenderedText.isEmpty()
                                        tenderedText = (tendered + v).fmt(2)
                                        bills = if (trackable) bills + (v to ((bills[v] ?: 0) + 1)) else emptyMap()
                                    },
                                    label = { val n = bills[v] ?: 0; Text(if (n > 0) "Q$v ×$n" else "Q$v") }
                                )
                            }
                            AssistChip(onClick = { tenderedText = ""; bills = emptyMap() }, label = { Text("Limpiar") })
                        }
                        if (tenderedText.isNotEmpty()) {
                            Text("Vuelto: Q ${change.fmtMoney(2)}", fontWeight = FontWeight.Bold,
                                color = if (short) MaterialTheme.colorScheme.error else BrandColors.Primary)
                        }
                    } else {
                        Text("Con tarjeta o transferencia no entra efectivo a la caja; solo baja la deuda.",
                            style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
                    }
                }
            }
        },
        confirmButton = {
            val acc = account
            Button(
                onClick = { if (acc != null) onConfirm(acc, amount, method, if (tenderedText.isEmpty()) amount else tendered, bills) },
                enabled = !isBusy && acc != null && amount > 0 && !short
            ) {
                if (isBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Cobrar Q ${amount.fmtMoney(2)}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancelar") } }
    )
}

/** Adelanto de salario desde la gaveta. Solo colaboradores; se descuenta luego en planilla. */
@Composable
fun EmployeeAdvanceDialog(
    accounts: List<CreditAccount>,
    cashAvailable: Double,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (account: CreditAccount, amount: Double, note: String) -> Unit
) {
    var account by remember { mutableStateOf<CreditAccount?>(null) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val tooMuch = amount > cashAvailable + 0.004

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Adelanto a colaborador", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sale de esta caja y se carga a la cuenta del colaborador; se descuenta en su planilla.",
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                CreditAccountPicker(
                    accounts = accounts.filter { it.isEmployee },
                    selected = account,
                    onSelect = { account = it },
                    placeholder = "Buscar colaborador",
                    maxHeight = 200
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monto") }, prefix = { Text("Q ") }, singleLine = true,
                    isError = tooMuch,
                    supportingText = { Text("En la caja hay Q ${cashAvailable.fmtMoney(2)}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Nota (opcional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                account?.let {
                    Spacer(Modifier.size(2.dp))
                    Text("Ya debe Q ${it.balance.fmtMoney(2)} · quedará en Q ${(it.balance + amount).fmtMoney(2)}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val acc = account
            Button(
                onClick = { if (acc != null) onConfirm(acc, amount, note.trim()) },
                enabled = !isBusy && acc != null && amount > 0 && !tooMuch
            ) {
                if (isBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Entregar Q ${amount.fmtMoney(2)}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancelar") } }
    )
}
