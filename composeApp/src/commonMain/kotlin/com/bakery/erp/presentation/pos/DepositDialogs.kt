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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CustomerDeposit
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.matches
import com.bakery.erp.model.depositDescription
import com.bakery.erp.model.depositLabel
import kotlinx.datetime.toLocalDateTime
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney

/** Lo que se teclea al recibir un anticipo. */
data class DepositInput(
    val customerName: String,
    val customerPhone: String,
    val description: String,
    val deliveryLabel: String,
    val amount: Double,
    val method: String,
    /** Pedido de cliente al que se liga (pastel, encargo especial). Vacío = anticipo suelto. */
    val orderId: String = "",
    val orderLabel: String = ""
)

/** "12/10 · 10:30 AM": la entrega del pedido tal como se ve en el anticipo. */
internal fun orderDeliveryLabel(order: com.bakery.erp.model.CustomerOrder): String {
    if (order.deliveryDate <= 0L) return order.deliveryTime
    val d = kotlin.time.Instant.fromEpochMilliseconds(order.deliveryDate)
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val date = "${d.dayOfMonth.toString().padStart(2, '0')}/${d.monthNumber.toString().padStart(2, '0')}"
    return listOf(date, order.deliveryTime).filter { it.isNotBlank() }.joinToString(" · ")
}

/**
 * Recibir un anticipo en la caja abierta. Lo mínimo para encontrarlo el día de la entrega —
 * quién, qué, cuándo— y el monto. El folio lo pone el sistema y va en el comprobante.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReceiveDepositDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (DepositInput) -> Unit,
    /** Pedidos con saldo para ligar el anticipo. Vacío = no se ofrece. */
    orders: List<com.bakery.erp.model.CustomerOrder> = emptyList()
) {
    var order by remember { mutableStateOf<com.bakery.erp.model.CustomerOrder?>(null) }
    var orderPickerOpen by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var delivery by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(SalePaymentMethod.CASH) }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val ready = name.isNotBlank() && description.isNotBlank() && amount > 0.0 && !isSaving

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Anticipo de cliente") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Entra a esta caja pero no cuenta como venta hasta que se entrega el encargo. " +
                        "Se imprime un comprobante con folio para el cliente.",
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate
                )
                val o = order
                if (o != null) {
                    Surface(shape = RoundedCornerShape(12.dp), color = BrandColors.PrimaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Pedido: ${o.depositLabel(orderDeliveryLabel(o))}", fontWeight = FontWeight.SemiBold,
                                    color = BrandColors.Primary)
                                Text("Total Q ${o.totalAmount.fmtMoney(2)} · debe Q ${o.balanceAmount.fmtMoney(2)}",
                                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                            }
                            TextButton(onClick = { order = null }) { Text("Quitar") }
                        }
                    }
                } else if (orders.isNotEmpty()) {
                    TextButton(onClick = { orderPickerOpen = true }) { Text("Ligar a un pedido de cliente (pastel, encargo)") }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Cliente") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '-' || c == ' ' } },
                    label = { Text("Teléfono (opcional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("¿Qué encargó?") },
                    placeholder = { Text("3 docenas de pirujo, almuerzo para 10…") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(delivery, { delivery = it }, label = { Text("¿Para cuándo? (opcional)") },
                    placeholder = { Text("Sábado 10 am") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monto del anticipo") }, prefix = { Text("Q ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SalePaymentMethod.all.forEach { m ->
                        FilterChip(selected = method == m, onClick = { method = m },
                            label = { Text(SalePaymentMethod.label(m)) })
                    }
                }
                if (method != SalePaymentMethod.CASH) {
                    Text(
                        "Con tarjeta o transferencia no entra efectivo a la gaveta: queda solo en el anticipo.",
                        style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val o = order
                    onConfirm(DepositInput(
                        name.trim(), phone.trim(), description.trim(), delivery.trim(), amount, method,
                        orderId = o?.id.orEmpty(),
                        orderLabel = o?.let { it.depositLabel(orderDeliveryLabel(it)) }.orEmpty()
                    ))
                },
                enabled = ready
            ) { Text(if (isSaving) "Guardando…" else "Recibir Q ${amount.fmtMoney(2)}") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") } }
    )

    if (orderPickerOpen) {
        var query by remember { mutableStateOf("") }
        val q = query.trim().lowercase()
        val found = orders.filter { q.isEmpty() || it.customerName.lowercase().contains(q) || it.customerPhone.contains(q) }
        AlertDialog(
            onDismissRequest = { orderPickerOpen = false },
            title = { Text("Ligar a un pedido") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it }, label = { Text("Cliente o teléfono") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(found, key = { it.id }) { o ->
                            Surface(shape = RoundedCornerShape(10.dp), color = BrandColors.Sand,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    // Lo que ya se sabe del pedido no se vuelve a teclear.
                                    order = o
                                    name = o.customerName
                                    phone = o.customerPhone
                                    description = o.depositDescription()
                                    delivery = orderDeliveryLabel(o)
                                    if (amountText.isBlank()) amountText = o.balanceAmount.fmt(2)
                                    orderPickerOpen = false
                                }) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(o.depositLabel(orderDeliveryLabel(o)), fontWeight = FontWeight.SemiBold,
                                        color = BrandColors.TextSlate)
                                    Text("Total Q ${o.totalAmount.fmtMoney(2)} · debe Q ${o.balanceAmount.fmtMoney(2)} · ${com.bakery.erp.model.CustomerOrderStatus.label(o.status)}",
                                        style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { orderPickerOpen = false }) { Text("Cancelar") } }
        )
    }
}

/** Elegir el anticipo del cliente al cobrar: se busca por folio, nombre o teléfono. */
@Composable
fun DepositPickerDialog(
    deposits: List<CustomerDeposit>,
    onDismiss: () -> Unit,
    onSelect: (CustomerDeposit) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val found = deposits.filter { it.isOpen && it.matches(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aplicar anticipo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Folio, nombre o teléfono") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (found.isEmpty()) {
                    Text(
                        if (deposits.isEmpty()) "No hay anticipos pendientes." else "Ninguno coincide.",
                        color = BrandColors.MutedSlate
                    )
                }
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(found, key = { it.id }) { d -> DepositRow(d, Modifier.clickable { onSelect(d) }) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Anticipos pendientes de aplicar, de todas las sucursales: cuánto dinero de clientes se está
 * guardando y de quién. Desde acá se reimprime el comprobante o se devuelve el anticipo.
 */
@Composable
fun DepositsListDialog(
    deposits: List<CustomerDeposit>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onReprint: (CustomerDeposit) -> Unit,
    onRefund: (CustomerDeposit, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var refunding by remember { mutableStateOf<CustomerDeposit?>(null) }
    val found = deposits.filter { it.isOpen && it.matches(query) }
    val held = deposits.filter { it.isOpen }.sumOf { it.remaining }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anticipos pendientes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Q ${held.fmtMoney(2)} de clientes por aplicar", fontWeight = FontWeight.SemiBold,
                    color = BrandColors.TextSlate)
                OutlinedTextField(query, { query = it }, label = { Text("Folio, nombre o teléfono") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(found, key = { it.id }) { d ->
                        Column {
                            DepositRow(d, Modifier)
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { onReprint(d) }, enabled = !isBusy) { Text("Reimprimir") }
                                TextButton(onClick = { refunding = d }, enabled = !isBusy) {
                                    Text("Devolver", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )

    refunding?.let { d ->
        var reason by remember(d.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { refunding = null },
            title = { Text("Devolver ${d.folio}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Salen Q ${d.remaining.fmtMoney(2)} en efectivo de esta caja para ${d.customerName}. " +
                            "El anticipo queda cerrado.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(reason, { reason = it }, label = { Text("Motivo") },
                        placeholder = { Text("Canceló el encargo") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = { onRefund(d, reason.trim()); refunding = null },
                    enabled = reason.isNotBlank() && !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Devolver Q ${d.remaining.fmtMoney(2)}") }
            },
            dismissButton = { TextButton(onClick = { refunding = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun DepositRow(d: CustomerDeposit, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = BrandColors.Sand, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${d.folio} · ${d.customerName}", fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                Text(d.description, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextSlate)
                if (d.customerOrderLabel.isNotBlank()) {
                    Text("Pedido: ${d.customerOrderLabel}", style = MaterialTheme.typography.bodySmall, color = BrandColors.Primary)
                }
                Text(
                    listOfNotNull(
                        d.deliveryLabel.takeIf { it.isNotBlank() }?.let { "Entrega: $it" },
                        d.customerPhone.takeIf { it.isNotBlank() },
                        d.branchName.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Q ${d.remaining.fmtMoney(2)}", fontWeight = FontWeight.Bold, color = BrandColors.Primary)
                if (d.appliedAmount > 0.004) {
                    Text("de Q ${d.amount.fmtMoney(2)}", style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.MutedSlate)
                }
            }
        }
    }
}
