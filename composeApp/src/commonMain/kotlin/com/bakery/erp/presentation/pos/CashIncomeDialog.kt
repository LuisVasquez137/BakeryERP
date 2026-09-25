package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CashMovementType
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.User
import com.bakery.erp.util.fmt

/**
 * Anotar que ENTRÓ efectivo a la caja abierta, sin que sea una venta.
 *
 * Existía el camino inverso —pagar un gasto de la gaveta— pero no éste, y el dinero que entra
 * pasa igual de seguido: la caja central manda cambio a media mañana, o alguien repone lo que
 * había sacado. Sin registrarlo, al cuadrar aparece un **sobrante** que nadie sabe explicar, y
 * un sobrante inexplicado se investiga igual que un faltante.
 *
 * **El origen no es decoración: son dos plata distintas.** Lo que baja de la caja central ya
 * era del negocio y solo cambió de bolsillo; lo que puso alguien de su cuenta todavía no. Se
 * guardan como dos tipos de movimiento que el modelo ya tenía (`FUND_ADDITION` y `DEPOSIT`) en
 * vez de un campo nuevo, así el cuadre y el cierre del día los suman sin enterarse del cambio.
 *
 * Lo que este diálogo NO hace es traslados entre dos cajas: eso es `CashTransferDialog`, que
 * escribe los dos movimientos espejo para que el dinero no aparezca duplicado. Acá el otro
 * lado no es una gaveta del sistema.
 */
@Composable
fun CashIncomeDialog(
    companyId: String,
    session: CashSession,
    currentUser: User,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CashSessionViewModel
) {
    var amountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    // El default es la caja central: es de lejos el caso más frecuente, y dejar sin marcar
    // obligaría a elegir siempre algo que casi siempre es lo mismo.
    var fromGeneral by remember { mutableStateOf(true) }

    val amount = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val valid = amount > 0.0 && reason.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anotar ingreso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Efectivo que entró a ${session.registerName.ifEmpty { "esta caja" }} y no " +
                        "es una venta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Cuánto (Q) *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("¿Por qué? *") },
                    placeholder = { Text("Ej: cambio que mandaron, reposición de lo que saqué") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("¿De dónde viene?", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = fromGeneral,
                        onClick = { fromGeneral = true },
                        label = { Text("De caja general") }
                    )
                    FilterChip(
                        selected = !fromGeneral,
                        onClick = { fromGeneral = false },
                        label = { Text("Otros") }
                    )
                }
                Text(
                    if (fromGeneral) {
                        "Dinero que baja de la caja central. Ya era del negocio; solo cambió " +
                            "de lugar."
                    } else {
                        "Cualquier otro origen: alguien repuso, un adelanto, un sobrante " +
                            "devuelto. Escribí en la razón de quién vino."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    viewModel.addMovement(
                        companyId = companyId,
                        sessionId = session.id,
                        type = if (fromGeneral) CashMovementType.FUND_ADDITION
                               else CashMovementType.DEPOSIT,
                        amount = amount,
                        reason = reason.trim(),
                        currentUser = currentUser
                    )
                    onSaved()
                }
            ) { Text(if (amount > 0.0) "Anotar Q ${amount.fmt(2)}" else "Anotar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
