package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.FEL_CF_LIMIT
import com.bakery.erp.model.FelInvoiceRequest
import com.bakery.erp.model.cleanNit
import com.bakery.erp.model.felReceptorProblem
import com.bakery.erp.model.felRequiresIdentifiedBuyer
import com.bakery.erp.model.isValidGuatemalaNit
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtMoney

/** Lo que el POS le pasa al cobro cuando la sucursal factura. */
class InvoiceOptions(
    /** "Facturar" sale marcado (configurado por sucursal). */
    val defaultOn: Boolean,
    /** Lo que cubre la factura antes del descuento: solo el pan, no la cuenta del restaurante. */
    val bakeryTotal: Double,
    /** El carrito trae cuentas del restaurante, que quedan fuera de esta factura. */
    val excludesRestaurant: Boolean,
    val lookupNit: suspend (String) -> Result<String?>
)

/** Estado del formulario de factura dentro del cobro. */
@Stable
class InvoiceFormState(defaultOn: Boolean) {
    var enabled by mutableStateOf(defaultOn)
    var withNit by mutableStateOf(false)
    var nit by mutableStateOf("")
    var name by mutableStateOf("")
    var looking by mutableStateOf(false)
    /** Aviso de la consulta a la SAT ("no aparece", "sin conexión"). */
    var lookupNote by mutableStateOf<String?>(null)

    fun request(): FelInvoiceRequest? = when {
        !enabled -> null
        withNit -> FelInvoiceRequest(cleanNit(nit), name.trim().uppercase())
        else -> FelInvoiceRequest()
    }

    fun problem(invoicedTotal: Double): String? = request()?.let { felReceptorProblem(it, invoicedTotal) }
}

@Composable
fun InvoiceSection(state: InvoiceFormState, options: InvoiceOptions, invoicedTotal: Double) {
    val mustIdentify = felRequiresIdentifiedBuyer(invoicedTotal)
    // Desde Q2,500 la SAT no acepta CF: se pasa solo a NIT en vez de dejar confirmar algo que
    // Digifact va a rechazar.
    LaunchedEffect(mustIdentify, state.enabled) {
        if (mustIdentify && state.enabled) state.withNit = true
    }
    val cleaned = cleanNit(state.nit)
    val nitOk = isValidGuatemalaNit(cleaned)
    LaunchedEffect(cleaned, nitOk) {
        if (!state.withNit || !nitOk) return@LaunchedEffect
        state.looking = true
        state.lookupNote = null
        options.lookupNit(cleaned)
            .onSuccess { found ->
                if (found != null) state.name = found
                else state.lookupNote = "La SAT no tiene registrado ese NIT. Revisalo con el cliente."
            }
            // Sin conexión con la SAT el nombre se escribe a mano: no se deja al cliente sin factura.
            .onFailure { state.lookupNote = "No se pudo consultar la SAT. Escribí el nombre como sale en su NIT." }
        state.looking = false
    }

    Surface(shape = RoundedCornerShape(12.dp), color = BrandColors.PrimaryContainer.copy(alpha = 0.45f)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Facturar", fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                    Text(
                        if (options.excludesRestaurant) "Factura el pan (Q ${invoicedTotal.fmtMoney(2)}). La cuenta del restaurante se factura en ComandaYa."
                        else "Factura electrónica por Q ${invoicedTotal.fmtMoney(2)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.MutedSlate
                    )
                }
                Switch(checked = state.enabled, onCheckedChange = { state.enabled = it })
            }
            if (!state.enabled) return@Column

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !state.withNit,
                    onClick = { if (!mustIdentify) state.withNit = false },
                    enabled = !mustIdentify,
                    label = { Text("Consumidor final") }
                )
                FilterChip(selected = state.withNit, onClick = { state.withNit = true }, label = { Text("Con NIT") })
            }
            if (mustIdentify) {
                Text(
                    "Desde Q ${FEL_CF_LIMIT.toInt()} la SAT pide el NIT del cliente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.AccentDeep
                )
            }
            if (state.withNit) {
                OutlinedTextField(
                    value = state.nit,
                    onValueChange = { v ->
                        state.nit = v.filter { it.isDigit() || it == 'k' || it == 'K' || it == '-' }
                        state.name = ""
                        state.lookupNote = null
                    },
                    label = { Text("NIT del cliente") },
                    singleLine = true,
                    isError = cleaned.length >= 2 && !nitOk,
                    supportingText = when {
                        cleaned.length >= 2 && !nitOk -> { { Text("El dígito verificador no cuadra") } }
                        else -> null
                    },
                    trailingIcon = if (state.looking) {
                        { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
                if (nitOk) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { state.name = it },
                        label = { Text("Nombre en la factura") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                state.lookupNote?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = BrandColors.AccentDeep)
                }
            }
        }
    }
}

/** Estado de la factura de una venta, para el historial. No dibuja nada si no se facturó. */
@Composable
fun SaleInvoiceChip(sale: com.bakery.erp.model.Sale) {
    val status = sale.invoiceStatus
    if (status.isBlank()) return
    val tone = when (status) {
        com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO -> com.bakery.erp.ui.theme.ChipTone.SUCCESS
        com.bakery.erp.model.FelInvoiceStatus.RECHAZADO -> com.bakery.erp.ui.theme.ChipTone.DANGER
        com.bakery.erp.model.FelInvoiceStatus.ANULADO -> com.bakery.erp.ui.theme.ChipTone.NEUTRAL
        // Pendiente o todavía en camino: pide ojos, pero se resuelve solo.
        else -> com.bakery.erp.ui.theme.ChipTone.ATTENTION
    }
    val text = if (status == com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO && sale.invoiceNumero.isNotBlank())
        "Factura ${sale.invoiceSerie}-${sale.invoiceNumero}"
    else com.bakery.erp.model.FelInvoiceStatus.label(status)
    com.bakery.erp.ui.theme.BrandChip(text, tone)
}
