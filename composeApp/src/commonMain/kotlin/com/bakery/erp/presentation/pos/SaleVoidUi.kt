package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.FelInvoiceStatus
import com.bakery.erp.model.MIN_SALE_VOID_REASON
import com.bakery.erp.model.SALE_VOID_REASONS
import com.bakery.erp.model.Sale
import com.bakery.erp.model.SaleOrder
import com.bakery.erp.model.bankRefundPending
import com.bakery.erp.model.voidBankAmount
import com.bakery.erp.model.voidCashAmount
import com.bakery.erp.util.fmtMoney

/**
 * Confirmar la anulación de un cobro. Dice qué va a pasar con el dinero (cuánto se devuelve de
 * la gaveta y cuánto por banco), con la factura y con el inventario, y pide el motivo: es lo que
 * le llega al dueño en el aviso.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoidSaleDialog(
    order: SaleOrder,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit
) {
    var reason by rememberSaveable { mutableStateOf("") }
    val reasonOk = reason.trim().length >= MIN_SALE_VOID_REASON
    val cash = order.sales.sumOf { it.voidCashAmount }
    val bank = order.sales.sumOf { it.voidBankAmount }
    val invoiced = order.sales.firstOrNull { it.invoiceStatus == FelInvoiceStatus.CERTIFICADO }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anular venta de Q ${order.total.fmtMoney(2)}") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (cash > 0.004) {
                    Text("Devuélvele al cliente Q ${cash.fmtMoney(2)} en efectivo de la gaveta.",
                        fontWeight = FontWeight.SemiBold)
                }
                if (bank > 0.004) {
                    Text("Q ${bank.fmtMoney(2)} se pagó con tarjeta o transferencia: queda pendiente de " +
                        "devolver por banco. El voucher se queda en la gaveta.",
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                }
                Text("El pan vuelve al inventario de la tienda." +
                    if (invoiced != null) " La factura ${invoiced.invoiceSerie}-${invoiced.invoiceNumero} se anula ante la SAT." else "",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Al dueño le llega un aviso y queda en el cuadre de la caja. No se puede deshacer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(200) },
                    label = { Text("Motivo (obligatorio)") },
                    isError = reason.isNotEmpty() && !reasonOk,
                    supportingText = if (reason.isNotEmpty() && !reasonOk) {
                        { Text("Mínimo $MIN_SALE_VOID_REASON letras") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SALE_VOID_REASONS.forEach { r ->
                        AssistChip(onClick = { reason = r }, label = { Text(r) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.trim()) },
                enabled = reasonOk,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Anular venta") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Volver") } }
    )
}

/**
 * Qué se anuló, por qué y quién, en el detalle del cobro. Con dinero de tarjeta pendiente, el
 * botón para marcar que el banco ya lo devolvió.
 */
@Composable
fun VoidedOrderCard(order: SaleOrder, canMarkRefund: Boolean, busy: Boolean, onMarkRefunded: () -> Unit) {
    val s: Sale = order.sales.firstOrNull { it.voidReason.isNotBlank() } ?: order.primary
    val bank = order.sales.sumOf { it.bankRefundAmount }
    val pending = order.sales.any { it.bankRefundPending }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cobro anulado", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            if (s.voidedAt > 0) {
                Text("${formatReceiptTimestamp(s.voidedAt)} · ${s.voidedByUserName}",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (s.voidReason.isNotBlank()) Text("Motivo: ${s.voidReason}", style = MaterialTheme.typography.bodyMedium)
            if (bank > 0.004) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                if (pending) {
                    Text("Pendiente de devolver por banco: Q ${bank.fmtMoney(2)}",
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    if (canMarkRefund) {
                        OutlinedButton(onClick = onMarkRefunded, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Ya se devolvió por banco")
                        }
                    }
                } else {
                    val done = order.sales.firstOrNull { it.bankRefundDoneAt > 0 }
                    Row {
                        Text(
                            "Devuelto por banco Q ${bank.fmtMoney(2)}" +
                                (done?.let { " · ${formatReceiptTimestamp(it.bankRefundDoneAt)} · ${it.bankRefundDoneByName}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ventas anuladas del turno, para el cuadre: cuáles fueron, por cuánto, quién y por qué. El
 * esperado ya no las cuenta; esto es para que el que cuadra (y el dueño) las vea.
 */
@Composable
fun VoidedSalesCard(sales: List<Sale>) {
    if (sales.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Ventas anuladas en este turno (${sales.size}) · Q ${sales.sumOf { it.total }.fmtMoney(2)}",
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            sales.sortedBy { it.voidedAt }.forEach { s ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cobro de ${formatReceiptTimestamp(s.createdAt)}", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f))
                    Text("Q ${s.total.fmtMoney(2)}", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    listOf(s.voidedByUserName, if (s.voidedAt > 0) "anuló ${formatReceiptTimestamp(s.voidedAt)}" else "")
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (s.voidReason.isNotBlank()) Text("Motivo: ${s.voidReason}", style = MaterialTheme.typography.bodySmall)
                if (s.voidCashAmount > 0.004) {
                    Text("Efectivo devuelto de la gaveta: Q ${s.voidCashAmount.fmtMoney(2)}",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (s.bankRefundAmount > 0.004) {
                    Text(
                        (if (s.bankRefundPending) "Por devolver por banco" else "Devuelto por banco") +
                            ": Q ${s.bankRefundAmount.fmtMoney(2)} (el voucher sigue en la gaveta)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
