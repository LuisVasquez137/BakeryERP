package com.bakery.erp.presentation.pos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch
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
    /**
     * Lo que cubre la factura antes del descuento de colaborador: el pan más las cuentas del
     * restaurante que ComandaYa no facturó.
     */
    val invoiceableTotal: Double,
    /** Alguna cuenta del carrito ya la facturó ComandaYa y queda fuera de esta factura. */
    val excludesBilledRestaurant: Boolean,
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
    var withoutDetail by mutableStateOf(false)

    fun request(): FelInvoiceRequest? = when {
        !enabled -> null
        withNit -> FelInvoiceRequest(cleanNit(nit), name.trim().uppercase(), withoutDetail)
        else -> FelInvoiceRequest(withoutDetail = withoutDetail)
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
    val scope = rememberCoroutineScope()
    // La SAT se consulta cuando la cajera lo pide, no a cada tecla: buscar a medio escribir
    // gastaba consultas y cambiaba el nombre mientras todavía se tecleaba.
    fun lookup() {
        if (!nitOk || state.looking) return
        scope.launch {
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
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (state.enabled) Color.White else BrandColors.PrimaryContainer.copy(alpha = 0.45f),
        border = if (state.enabled) BorderStroke(1.5.dp, BrandColors.Primary) else null
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = BrandColors.Primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Factura electrónica", fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                    Text(
                        if (options.excludesBilledRestaurant) "Q ${invoicedTotal.fmtMoney(2)} · lo ya facturado en ComandaYa queda fuera"
                        else "Q ${invoicedTotal.fmtMoney(2)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.MutedSlate
                    )
                }
                Switch(checked = state.enabled, onCheckedChange = { state.enabled = it })
            }
            AnimatedVisibility(visible = state.enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        // NIT corto con su botón al lado; el nombre, que es largo, en su propia línea.
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.nit,
                                onValueChange = { v ->
                                    state.nit = v.filter { it.isDigit() || it == 'k' || it == 'K' || it == '-' }.uppercase()
                                    state.name = ""
                                    state.lookupNote = null
                                },
                                label = { Text("NIT") },
                                placeholder = { Text("1234567-8") },
                                singleLine = true,
                                isError = cleaned.length >= 2 && !nitOk,
                                supportingText = if (cleaned.length >= 2 && !nitOk) {
                                    { Text("El dígito verificador no cuadra") }
                                } else null,
                                // Ascii: teclado con la fila de números y las letras (para la K).
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(onSearch = { lookup() }),
                                modifier = Modifier.widthIn(max = 220.dp).weight(1f, fill = false)
                            )
                            Button(
                                onClick = { lookup() },
                                enabled = nitOk && !state.looking,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary),
                                modifier = Modifier.height(52.dp)
                            ) {
                                if (state.looking) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Buscar")
                                }
                            }
                        }
                        if (nitOk) {
                            OutlinedTextField(
                                value = state.name,
                                onValueChange = { state.name = it },
                                label = { Text("Nombre en la factura") },
                                placeholder = { Text("Tocá Buscar, o escribilo") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        state.lookupNote?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = BrandColors.AccentDeep)
                        }
                    }
                    // El cliente no quiere que la factura liste lo que consumió.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Sin detalle", color = BrandColors.TextSlate)
                            Text(
                                "Una sola línea: \"Servicio de alimentos y/o bebidas\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandColors.MutedSlate
                            )
                        }
                        Switch(checked = state.withoutDetail, onCheckedChange = { state.withoutDetail = it })
                    }
                }
            }
        }
    }
}

/** Estado de la factura de una venta, para el historial. No dibuja nada si no se facturó. */
@Composable
fun SaleInvoiceChip(sale: com.bakery.erp.model.Sale) {
    val status = sale.invoiceStatus
    // Dos facturas del mismo consumo: es lo primero que tiene que verse, antes que el estado.
    if (sale.invoiceConflict) {
        com.bakery.erp.ui.theme.BrandChip("Doble factura: anular una", com.bakery.erp.ui.theme.ChipTone.DANGER)
        return
    }
    if (status.isBlank()) return
    val tone = when (status) {
        com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO -> com.bakery.erp.ui.theme.ChipTone.SUCCESS
        com.bakery.erp.model.FelInvoiceStatus.RECHAZADO -> com.bakery.erp.ui.theme.ChipTone.DANGER
        com.bakery.erp.model.FelInvoiceStatus.ANULADO -> com.bakery.erp.ui.theme.ChipTone.NEUTRAL
        com.bakery.erp.model.FelInvoiceStatus.EXTERNA -> com.bakery.erp.ui.theme.ChipTone.INFO
        // Pendiente o todavía en camino: pide ojos, pero se resuelve solo.
        else -> com.bakery.erp.ui.theme.ChipTone.ATTENTION
    }
    val text = if (status == com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO && sale.invoiceNumero.isNotBlank())
        "Factura ${sale.invoiceSerie}-${sale.invoiceNumero}"
    else com.bakery.erp.model.FelInvoiceStatus.label(status)
    com.bakery.erp.ui.theme.BrandChip(text, tone)
}
