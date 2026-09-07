package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.User
import com.bakery.erp.util.fmtMoney

/** Deja solo dígitos y un único punto decimal. */
private fun sanitizeAmount(raw: String): String {
    val filtered = raw.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    if (firstDot == -1) return filtered
    // Conservar el primer punto; quitar los siguientes.
    return filtered.substring(0, firstDot + 1) +
        filtered.substring(firstDot + 1).replace(".", "")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashTransferDialog(
    companyId: String,
    currentSession: CashSession,
    currentUser: User,
    onDismiss: () -> Unit,
    onTransferred: () -> Unit,
    viewModel: CashSessionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTargetId by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    LaunchedEffect(companyId, currentSession.id) {
        viewModel.loadOpenSessionsForTransfer(companyId, currentSession.branchId, currentSession.id)
    }
    LaunchedEffect(state.transferDone) {
        if (state.transferDone) {
            viewModel.clearTransferDone()
            onTransferred()
        }
    }

    val target = state.openSessionsForTransfer.firstOrNull { it.id == selectedTargetId }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    // Efectivo disponible en la caja origen (sesión abierta).
    val availableCash = currentSession.openingBalance + currentSession.totalCashSales +
        currentSession.totalMovementsIn - currentSession.totalMovementsOut
    val exceedsAvailable = amount > availableCash
    val canSubmit = target != null && amount > 0 && !exceedsAvailable && !state.isLoading

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transferir efectivo a otra caja") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Desde: ${currentSession.registerName}",
                    style = MaterialTheme.typography.bodySmall)
                if (state.openSessionsForTransfer.isEmpty()) {
                    Text("No hay otras cajas abiertas en esta sucursal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                } else {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = target?.let { "${it.registerName} · ${it.cashierName}" } ?: "Selecciona caja destino",
                            onValueChange = {},
                            label = { Text("Caja destino *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            state.openSessionsForTransfer.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s.registerName} · ${s.cashierName}") },
                                    onClick = { selectedTargetId = s.id; expanded = false }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = sanitizeAmount(it) },
                    prefix = { Text("Q ") },
                    label = { Text("Monto *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = exceedsAvailable,
                    supportingText = {
                        if (exceedsAvailable) {
                            Text(
                                "Máximo disponible en tu caja: Q ${availableCash.fmtMoney(2)}",
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text("Efectivo disponible: Q ${availableCash.fmtMoney(2)}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo (opcional)") },
                    placeholder = { Text("Ej. cambio para vuelto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "El monto sale de tu caja y entra a la caja destino. Ambas cajas reflejan " +
                        "el traslado al cuadrar — en el cierre del día se cancelan entre sí (neto = 0).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val t = target ?: return@TextButton
                    viewModel.transferToSession(
                        companyId, currentSession, t, amount, reason, currentUser
                    )
                },
                enabled = canSubmit
            ) { Text("Confirmar traslado") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
