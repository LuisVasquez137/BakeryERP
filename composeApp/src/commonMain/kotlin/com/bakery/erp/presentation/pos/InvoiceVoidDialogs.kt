package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.FelInvoiceRequest
import com.bakery.erp.model.Sale
import com.bakery.erp.ui.theme.BrandColors

/** Motivos frecuentes: tocarlos llena el campo. El texto libre sigue disponible. */
private val VOID_REASONS = listOf("NIT o nombre equivocado", "Error en el cobro", "Devolución del cliente")

/** Mínimo que exige la función para el motivo de anulación. */
private const val MIN_REASON = 5

/**
 * Anular la factura de una venta. Deja claro lo que NO hace —la venta sigue, el dinero y el
 * inventario no se mueven— y ofrece emitir la correcta en el mismo paso, que es el caso común
 * ("me dio mal el NIT").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoidInvoiceDialog(
    sale: Sale,
    lookupNit: suspend (String) -> Result<String?>,
    onDismiss: () -> Unit,
    onConfirm: (reason: String, reissueTo: FelInvoiceRequest?) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val reissue = remember { InvoiceFormState(defaultOn = false) }
    val options = remember(sale.id) { InvoiceOptions(false, sale.total, false, lookupNit) }
    val reasonOk = reason.trim().length >= MIN_REASON
    val reissueOk = reissue.problem(sale.total) == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anular factura ${sale.invoiceSerie}-${sale.invoiceNumero}") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Se anula ante la SAT y no se puede deshacer. La venta no cambia: no devuelve " +
                        "dinero ni inventario.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo") },
                    isError = reason.isNotEmpty() && !reasonOk,
                    supportingText = if (reason.isNotEmpty() && !reasonOk) {
                        { Text("Mínimo $MIN_REASON letras") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VOID_REASONS.forEach { r ->
                        AssistChip(onClick = {
                            reason = r
                            // El NIT equivocado es justo el caso de emitir la correcta enseguida.
                            if (r == VOID_REASONS.first()) { reissue.enabled = true; reissue.withNit = true }
                        }, label = { Text(r) })
                    }
                }
                Text("¿Emitir la correcta?", fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                InvoiceSection(reissue, options, sale.total)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.trim(), reissue.request()) },
                enabled = reasonOk && reissueOk,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(if (reissue.enabled) "Anular y facturar" else "Anular factura") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Facturar otra vez una venta cuya factura se anuló o fue rechazada. */
@Composable
fun ReissueInvoiceDialog(
    sale: Sale,
    lookupNit: suspend (String) -> Result<String?>,
    onDismiss: () -> Unit,
    onConfirm: (FelInvoiceRequest) -> Unit
) {
    val form = remember { InvoiceFormState(defaultOn = true) }
    val options = remember(sale.id) { InvoiceOptions(true, sale.total, false, lookupNit) }
    val request = form.request()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Facturar de nuevo") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Se emite una factura nueva por esta venta. La anterior queda como está.",
                    style = MaterialTheme.typography.bodyMedium
                )
                InvoiceSection(form, options, sale.total)
            }
        },
        confirmButton = {
            Button(
                onClick = { request?.let(onConfirm) },
                enabled = request != null && form.problem(sale.total) == null
            ) { Text("Facturar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
