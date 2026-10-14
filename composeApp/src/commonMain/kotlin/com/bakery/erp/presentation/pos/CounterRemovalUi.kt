package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bakery.erp.domain.PIN_LENGTH
import com.bakery.erp.model.CounterRemoval
import com.bakery.erp.model.CounterRemovalKind
import com.bakery.erp.model.CounterRemovalLine
import com.bakery.erp.model.EmployeePinStatus
import com.bakery.erp.model.Product
import com.bakery.erp.model.counterRemovalError
import com.bakery.erp.ui.ImagePickerButton
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtMoney
import com.bakery.erp.util.fmtTrim

/**
 * "Sacar del mostrador": primero ¿qué pasó?, después qué y cuánto, y el control propio de
 * cada caso (foto, responsable + PIN, cuota de regalos). Ver `model/CounterRemoval.kt`.
 *
 * [unitCostOf] = costo promedio del inventario de tienda (lo que se carga en un accidente).
 */
@Composable
fun CounterRemovalDialog(
    products: List<Product>,
    stockOf: (productId: String) -> Double,
    unitCostOf: (productId: String) -> Double,
    state: CounterRemovalUiState,
    selfEmployeeId: String,
    onDismiss: () -> Unit,
    onConfirm: (draft: CounterRemoval, photo: ByteArray?, responsiblePin: String) -> Unit
) {
    var kind by remember { mutableStateOf<String?>(null) }
    // productId → cantidad, en el orden en que se agregaron.
    var quantities by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var query by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var note by remember { mutableStateOf("") }
    var responsibleId by remember(selfEmployeeId) { mutableStateOf(selfEmployeeId) }
    var pin by remember { mutableStateOf("") }

    val byId = remember(products) { products.associateBy { it.id } }
    val lines = quantities.mapNotNull { (id, q) ->
        val p = byId[id] ?: return@mapNotNull null
        CounterRemovalLine(p.id, p.name, q.toDouble(), unitCostOf(p.id), p.salePrice)
    }
    val responsible = state.employees.firstOrNull { it.id == responsibleId }
    val draft = CounterRemoval(
        kind = kind ?: CounterRemovalKind.DAMAGED,
        lines = lines,
        note = note,
        responsibleEmployeeId = responsibleId
    )
    val needsPin = kind == CounterRemovalKind.ACCIDENT && responsible != null &&
        responsible.id != selfEmployeeId && responsible.pinStatus != EmployeePinStatus.NONE
    val problem = counterRemovalError(draft, photo != null, state.courtesyQuota, state.courtesyUsedToday)
        ?: if (needsPin && pin.length != PIN_LENGTH) "${responsible?.name} tiene que poner su PIN" else null

    Dialog(
        onDismissRequest = { if (!state.isBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp).fillMaxWidth().widthIn(max = 560.dp)
        ) {
            Column(
                Modifier.padding(20.dp).heightIn(max = 720.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Sacar del mostrador",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary
                )
                val k = kind
                if (k == null) {
                    KindChoice(
                        quota = state.courtesyQuota,
                        used = state.courtesyUsedToday,
                        isPreparing = state.isPreparing,
                        onPick = { kind = it }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancelar") }
                    }
                    return@Column
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        kindTitle(k),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.TextSlate,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { kind = null }, enabled = !state.isBusy) { Text("Cambiar") }
                }

                // ── Qué se saca ──
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar pan o producto") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val matches = remember(products, query, quantities) {
                    products
                        .filter { it.id !in quantities }
                        .filter { if (query.isBlank()) stockOf(it.id) > 0.0 else it.name.contains(query.trim(), ignoreCase = true) }
                        .sortedBy { it.name.lowercase() }
                        .take(8)
                }
                matches.forEach { p ->
                    Card(
                        onClick = { quantities = quantities + (p.id to 1); query = "" },
                        colors = CardDefaults.cardColors(containerColor = BrandColors.Sand),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, Modifier.weight(1f), color = BrandColors.TextSlate)
                            Text("${stockOf(p.id).fmtTrim()} en tienda", style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
                        }
                    }
                }
                if (quantities.isNotEmpty()) {
                    HorizontalDivider()
                    quantities.forEach { (id, q) ->
                        val p = byId[id] ?: return@forEach
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            IconButton(onClick = {
                                quantities = if (q <= 1) quantities - id else quantities + (id to q - 1)
                            }, enabled = !state.isBusy) { Icon(Icons.Default.Remove, "Uno menos") }
                            Text("$q", fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                            IconButton(onClick = { quantities = quantities + (id to q + 1) }, enabled = !state.isBusy) {
                                Icon(Icons.Default.Add, "Uno más")
                            }
                        }
                    }
                    HorizontalDivider()
                }

                // ── El control de cada caso ──
                when (k) {
                    CounterRemovalKind.DAMAGED -> {
                        Text("Foto del producto *", style = MaterialTheme.typography.labelLarge)
                        ImagePickerButton(
                            currentImageUrl = null,
                            selectedBytes = photo,
                            onImageSelected = { photo = it },
                            allowCamera = true
                        )
                    }
                    CounterRemovalKind.ACCIDENT -> AccidentBlock(
                        state = state,
                        selfEmployeeId = selfEmployeeId,
                        responsibleId = responsibleId,
                        onResponsibleChange = { responsibleId = it; pin = "" },
                        needsPin = needsPin,
                        pin = pin,
                        onPinChange = { pin = it.filter(Char::isDigit).take(PIN_LENGTH) },
                        charge = draft.totalCost,
                        enabled = !state.isBusy
                    )
                    CounterRemovalKind.COURTESY -> {
                        val left = (state.courtesyQuota - state.courtesyUsedToday).coerceAtLeast(0.0)
                        val value = draft.totalPrice
                        Text(
                            "Quedan Q ${left.fmtMoney(2)} de Q ${state.courtesyQuota.fmtMoney(2)} para regalar hoy" +
                                if (value > 0) " · esto vale Q ${value.fmtMoney(2)}" else "",
                            color = if (value > left + 0.004) BrandColors.Danger else BrandColors.TextSlate,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    label = {
                        Text(if (k == CounterRemovalKind.COURTESY) "A quién o por qué *" else "Nota (opcional)")
                    },
                    placeholder = {
                        Text(when (k) {
                            CounterRemovalKind.COURTESY -> "Ej: señor de la esquina"
                            CounterRemovalKind.ACCIDENT -> "Ej: se cayó la bandeja al reponer"
                            else -> "Ej: venían aplastados en la canasta"
                        })
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                val shown = state.error ?: problem
                if (shown != null && (state.error != null || quantities.isNotEmpty())) {
                    Text(
                        shown,
                        color = if (state.error != null) BrandColors.Danger else BrandColors.MutedSlate,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss, enabled = !state.isBusy) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(draft, photo, pin) },
                        enabled = !state.isBusy && problem == null
                    ) {
                        if (state.isBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Registrar")
                    }
                }
            }
        }
    }
}

private fun kindTitle(kind: String) = when (kind) {
    CounterRemovalKind.DAMAGED -> "Venía mal"
    CounterRemovalKind.ACCIDENT -> "Se cayó o se arruinó aquí"
    else -> "Lo regalé"
}

@Composable
private fun KindChoice(quota: Double, used: Double, isPreparing: Boolean, onPick: (String) -> Unit) {
    Text("¿Qué pasó?", style = MaterialTheme.typography.bodyLarge)
    KindOption(
        title = "Venía mal",
        body = "Quemado, crudo, aplastado o viejo. Se pide una foto.",
        onClick = { onPick(CounterRemovalKind.DAMAGED) }
    )
    KindOption(
        title = "Se cayó o se arruinó aquí",
        body = "Se le carga a costo a quien se le cayó, en su cuenta de colaborador.",
        onClick = { onPick(CounterRemovalKind.ACCIDENT) }
    )
    val left = (quota - used).coerceAtLeast(0.0)
    KindOption(
        title = "Lo regalé",
        body = when {
            isPreparing -> "Revisando la cuota de hoy…"
            quota <= 0.0 -> "Esta sucursal no tiene cuota de regalos."
            left <= 0.004 -> "Ya se usó la cuota de hoy (Q ${quota.fmtMoney(2)})."
            else -> "Quedan Q ${left.fmtMoney(2)} de Q ${quota.fmtMoney(2)} para hoy."
        },
        enabled = !isPreparing && quota > 0.0 && left > 0.004,
        onClick = { onPick(CounterRemovalKind.COURTESY) }
    )
}

@Composable
private fun KindOption(title: String, body: String, enabled: Boolean = true, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BrandColors.Sand),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) BrandColors.Primary else BrandColors.MutedSlate)
            Text(body, style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
        }
    }
}

@Composable
private fun AccidentBlock(
    state: CounterRemovalUiState,
    selfEmployeeId: String,
    responsibleId: String,
    onResponsibleChange: (String) -> Unit,
    needsPin: Boolean,
    pin: String,
    onPinChange: (String) -> Unit,
    charge: Double,
    enabled: Boolean
) {
    var open by remember { mutableStateOf(false) }
    val responsible = state.employees.firstOrNull { it.id == responsibleId }
    Text("¿A quién se le cayó? *", style = MaterialTheme.typography.labelLarge)
    Column {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(
                responsible?.let { if (it.id == selfEmployeeId) "${it.name} (yo)" else it.name }
                    ?: if (state.isPreparing) "Cargando colaboradores…" else "Elegir colaboradora"
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.employees.forEach { e ->
                DropdownMenuItem(
                    text = { Text(if (e.id == selfEmployeeId) "${e.name} (yo)" else e.name) },
                    onClick = { onResponsibleChange(e.id); open = false }
                )
            }
        }
    }
    when {
        responsible == null -> Unit
        needsPin -> OutlinedTextField(
            value = pin,
            onValueChange = onPinChange,
            label = { Text("PIN de ${responsible.name} *") },
            supportingText = { Text("Ella lo confirma: nadie le carga un accidente a otra.") },
            singleLine = true,
            enabled = enabled,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        responsible.id != selfEmployeeId -> Text(
            "${responsible.name} todavía no tiene PIN: se registra igual y le llega aviso al dueño.",
            style = MaterialTheme.typography.bodySmall,
            color = BrandColors.AccentDeep
        )
    }
    if (charge > 0.0) {
        Text(
            "Se le carga Q ${charge.fmtMoney(2)} (a costo) en su cuenta.",
            fontWeight = FontWeight.SemiBold,
            color = BrandColors.TextSlate
        )
    }
}
