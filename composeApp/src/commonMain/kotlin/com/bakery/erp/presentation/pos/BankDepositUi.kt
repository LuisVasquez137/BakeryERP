package com.bakery.erp.presentation.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.BankDeposit
import com.bakery.erp.model.BankDepositStatus
import com.bakery.erp.model.COMMON_BANKS
import com.bakery.erp.model.Employee
import com.bakery.erp.model.MIN_MISSING_SLIP_REASON
import com.bakery.erp.model.bankDepositProblem
import com.bakery.erp.model.bankSlipProblem
import com.bakery.erp.model.isPending
import com.bakery.erp.model.slipDifference
import com.bakery.erp.model.slipMismatch
import com.bakery.erp.ui.ImagePickerButton
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.ui.ZoomableImageDialog
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtMoney

/** Monto tecleado → número, o 0 si no se entiende. */
internal fun parseMoneyInput(text: String): Double = text.replace(',', '.').toDoubleOrNull() ?: 0.0

/**
 * Banco y quién lleva el dinero: lo mismo a medio turno y en el cierre. Los bancos de siempre
 * van como botones, para no teclearlo cada vez.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BankDepositFields(
    bankName: String,
    onBankName: (String) -> Unit,
    carriedBy: Employee?,
    onCarriedBy: (Employee?) -> Unit,
    employees: List<Employee>,
    /** La sucursal usa PIN: quien lo lleva lo escribe (ver [carrierMustConfirm]). */
    pinRequired: Boolean = false,
    pin: String = "",
    onPin: (String) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("¿A qué banco?", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            COMMON_BANKS.forEach { b ->
                FilterChip(selected = bankName == b, onClick = { onBankName(b) }, label = { Text(b) })
            }
        }
        OutlinedTextField(
            value = bankName,
            onValueChange = { onBankName(it.take(60)) },
            label = { Text("Banco") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        EmployeePickerField(
            label = "¿Quién lo lleva al banco?",
            employees = employees,
            selected = carriedBy,
            chosen = carriedBy != null,
            // Otra persona, otro PIN: el escrito no le sirve a la nueva.
            onSelect = { onCarriedBy(it); onPin("") }
        )
        if (pinRequired && carriedBy != null) {
            Text("${carriedBy.name} lleva el dinero: que escriba su PIN para confirmarlo.",
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> onPin(v.filter { it.isDigit() }.take(com.bakery.erp.domain.PIN_LENGTH)) },
                label = { Text("PIN de ${carriedBy.name}") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Sacar efectivo a medio turno para depositarlo. Se descuenta de la gaveta en ese momento; la
 * boleta se adjunta al volver.
 */
@Composable
fun BankDepositDialog(
    cashInDrawer: Double,
    employees: List<Employee>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, draft: BankDepositDraft) -> Unit,
    pinRequired: Boolean = false,
    /** Lo que respondió el servidor (PIN incorrecto…): se ve aquí, no detrás del diálogo. */
    error: String? = null
) {
    var amountText by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var carriedBy by remember { mutableStateOf<Employee?>(null) }
    var notes by remember { mutableStateOf("") }
    // remember, no rememberSaveable: un PIN no se guarda en el estado de la actividad.
    var pin by remember { mutableStateOf("") }
    val amount = parseMoneyInput(amountText)
    val pinMissing = pinRequired && carriedBy != null && pin.length != com.bakery.erp.domain.PIN_LENGTH
    val problem = bankDepositProblem(amount, cashInDrawer, bankName, carriedBy?.id.orEmpty())
        ?: if (pinMissing) "Falta el PIN de ${carriedBy?.name.orEmpty()}." else null

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Depositar al banco") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Efectivo que sale de la gaveta para llevarlo al banco. Se descuenta ya; " +
                    "al volver se adjunta la foto de la boleta.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("En la gaveta debería haber Q ${cashInDrawer.fmtMoney(2)}",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("¿Cuánto se lleva? (Q)") },
                    singleLine = true,
                    isError = amount > cashInDrawer + 0.004,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                BankDepositFields(bankName, { bankName = it }, carriedBy, { carriedBy = it }, employees,
                    pinRequired = pinRequired, pin = pin, onPin = { pin = it })
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(200) },
                    label = { Text("Nota (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Mientras no se adjunte la boleta, esta caja no se puede cerrar. Al administrador le llega un aviso.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Guardando…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (error != null && !isBusy) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold)
                }
                if (problem != null && (amountText.isNotEmpty() || bankName.isNotEmpty())) {
                    Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = problem == null && !isBusy,
                onClick = { onConfirm(amount, BankDepositDraft(bankName, carriedBy, notes, carrierPin = pin)) }
            ) { Text(if (amount > 0.004) "Sacar Q ${amount.fmtMoney(2)}" else "Sacar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancelar") } }
    )
}

/** Foto de la boleta (obligatoria), número y el monto que dice. */
@Composable
fun AttachBankSlipDialog(
    deposit: BankDeposit,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (photo: ByteArray, slipNumber: String, slipAmount: Double) -> Unit
) {
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var number by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf(deposit.amount.fmtMoney(2).replace(",", "")) }
    val slipAmount = amountText.replace(',', '.').toDoubleOrNull()
    val problem = bankSlipProblem(photo != null, number, slipAmount)
    val diff = (slipAmount ?: 0.0) - deposit.amount

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Boleta del depósito") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Salieron Q ${deposit.amount.fmtMoney(2)} · ${deposit.bankName}" +
                    (deposit.carriedByEmployeeName.takeIf { it.isNotBlank() }?.let { " · lo llevó $it" } ?: ""),
                    fontWeight = FontWeight.SemiBold)
                Text("Foto de la boleta *", style = MaterialTheme.typography.labelLarge)
                ImagePickerButton(
                    currentImageUrl = null,
                    selectedBytes = photo,
                    onImageSelected = { photo = it },
                    allowCamera = true
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it.take(40) },
                    label = { Text("Número de boleta *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Monto que dice la boleta (Q) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Subiendo la boleta…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (slipAmount != null && kotlin.math.abs(diff) > 0.004) {
                    Text(
                        (if (diff < 0) "La boleta dice Q ${(-diff).fmtMoney(2)} menos" else "La boleta dice Q ${diff.fmtMoney(2)} más") +
                            " de lo que salió de la gaveta. Se le avisará al administrador.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = problem == null && !isBusy,
                onClick = { photo?.let { onConfirm(it, number, slipAmount ?: 0.0) } }
            ) { Text("Adjuntar boleta") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancelar") } }
    )
}

/** Dejarlo sin boleta: con motivo, y el dueño se entera. */
@Composable
fun NoBankSlipDialog(
    deposit: BankDeposit,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val ok = reason.trim().length >= MIN_MISSING_SLIP_REASON
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Dejar sin boleta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Depósito de Q ${deposit.amount.fmtMoney(2)} a ${deposit.bankName}. " +
                    "Ya no bloqueará el cierre, pero al administrador le llega un aviso con el motivo.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(200) },
                    label = { Text("Motivo (obligatorio)") },
                    placeholder = { Text("Ej: se perdió la boleta, el banco no la dio") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = ok && !isBusy,
                onClick = { onConfirm(reason.trim()) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Dejar sin boleta") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Volver") } }
    )
}

/**
 * Depósitos sin boleta de la caja. Desde el POS (botón del banco) y al intentar cerrar: en ese
 * caso [blockingClose] lo dice arriba.
 */
@Composable
fun PendingBankDepositsDialog(
    pending: List<BankDeposit>,
    blockingClose: Boolean,
    canAct: Boolean,
    onAttach: (BankDeposit) -> Unit,
    onNoSlip: (BankDeposit) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (blockingClose) "No se puede cerrar todavía" else "Depósitos sin boleta") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (blockingClose) {
                    Text("Hay ${pending.size} depósito(s) al banco sin boleta en esta caja. Adjunta la foto de la boleta " +
                        "o, si no la hay, déjalo sin boleta con el motivo.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (pending.isEmpty()) Text("No hay depósitos pendientes.")
                pending.forEach { d ->
                    HorizontalDivider()
                    Text("Q ${d.amount.fmtMoney(2)} · ${d.bankName}", fontWeight = FontWeight.SemiBold)
                    Text(
                        listOf(
                            formatReceiptTimestamp(d.createdAt),
                            d.carriedByEmployeeName.takeIf { it.isNotBlank() }
                                ?.let { "lo lleva $it" + if (d.carrierConfirmedWithPin) " (con PIN)" else "" },
                            "al cerrar el turno".takeIf { d.atClose }
                        ).filterNotNull().joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (canAct) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onAttach(d) }) { Text("Adjuntar boleta") }
                            TextButton(onClick = { onNoSlip(d) }) { Text("No tengo la boleta") }
                        }
                    }
                }
                if (!canAct && pending.isNotEmpty()) {
                    Text("Necesitas el permiso \"Depositar al banco desde la caja\" para adjuntar la boleta.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/**
 * Depósitos al banco del turno, para el cuadre y el detalle de la caja: cuánto, quién, a qué
 * banco y la boleta (se toca para verla en grande). Los pendientes se pueden completar aquí.
 */
@Composable
fun BankDepositsCard(
    deposits: List<BankDeposit>,
    canAct: Boolean,
    onAttach: (BankDeposit) -> Unit,
    onNoSlip: (BankDeposit) -> Unit
) {
    if (deposits.isEmpty()) return
    var zoom by remember { mutableStateOf<BankDeposit?>(null) }
    Card(
        colors = CardDefaults.cardColors(containerColor = BrandColors.PrimaryContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Depósitos al banco (${deposits.size}) · Q ${deposits.sumOf { it.amount }.fmtMoney(2)}",
                fontWeight = FontWeight.SemiBold)
            deposits.forEach { d ->
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (d.slipPhotoUrl.isNotBlank()) {
                        ProductImage(
                            imageUrl = d.slipPhotoUrl,
                            contentDescription = "Boleta",
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).clickable { zoom = d }
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${d.bankName}${if (d.atClose) " · al cerrar" else ""}",
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("Q ${d.amount.fmtMoney(2)}", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            listOfNotNull(
                                formatReceiptTimestamp(d.createdAt),
                                d.carriedByEmployeeName.takeIf { it.isNotBlank() }
                                    ?.let { "lo llevó $it" + if (d.carrierConfirmedWithPin) " (con PIN)" else "" }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        when (d.status) {
                            BankDepositStatus.SLIP_ATTACHED -> Text(
                                "Boleta ${d.slipNumber} · Q ${d.slipAmount.fmtMoney(2)}" +
                                    if (d.slipMismatch) " · diferencia Q ${d.slipDifference.fmtMoney(2)}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (d.slipMismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (d.slipMismatch) FontWeight.SemiBold else FontWeight.Normal
                            )
                            BankDepositStatus.NO_SLIP -> Text("Sin boleta: ${d.missingSlipReason}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold)
                            else -> Text("Falta la boleta", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (d.isPending && canAct) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onAttach(d) }) { Text("Adjuntar boleta") }
                        TextButton(onClick = { onNoSlip(d) }) { Text("No tengo la boleta") }
                    }
                }
            }
        }
    }
    zoom?.let { d ->
        ZoomableImageDialog(imageUrl = d.slipPhotoUrl, title = "Boleta ${d.slipNumber}", onDismiss = { zoom = null })
    }
}

/**
 * Los diálogos de adjuntar boleta / dejar sin boleta, para no repetir el estado en cada pantalla
 * que los usa (POS, cierre, detalle de la caja).
 */
class BankSlipActions {
    var attachFor by mutableStateOf<BankDeposit?>(null)
    var noSlipFor by mutableStateOf<BankDeposit?>(null)
}

@Composable
fun BankSlipActionDialogs(
    actions: BankSlipActions,
    companyId: String,
    user: com.bakery.erp.model.User?,
    viewModel: CashSessionViewModel,
    isBusy: Boolean,
    /** Al llegar el aviso de que se guardó, se cierran los diálogos (si falla, quedan abiertos). */
    notice: String?,
    viewingSessionId: String? = null
) {
    androidx.compose.runtime.LaunchedEffect(notice) {
        if (notice != null) { actions.attachFor = null; actions.noSlipFor = null }
    }
    actions.attachFor?.let { d ->
        AttachBankSlipDialog(
            deposit = d,
            isBusy = isBusy,
            onDismiss = { actions.attachFor = null },
            onConfirm = { photo, number, amount ->
                user?.let { viewModel.attachBankSlip(companyId, d, photo, number, amount, it, viewingSessionId) }
            }
        )
    }
    actions.noSlipFor?.let { d ->
        NoBankSlipDialog(
            deposit = d,
            isBusy = isBusy,
            onDismiss = { actions.noSlipFor = null },
            onConfirm = { reason ->
                user?.let { viewModel.markBankDepositNoSlip(companyId, d, reason, it, viewingSessionId) }
            }
        )
    }
}
