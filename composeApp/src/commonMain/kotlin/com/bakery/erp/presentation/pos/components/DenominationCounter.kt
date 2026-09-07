package com.bakery.erp.presentation.pos.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CashDenomination
import com.bakery.erp.model.CashDenominationType
import com.bakery.erp.model.CashDenominations
import com.bakery.erp.util.fmtMoney
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


/**
 * Saver KMP-safe para conteos `denomCode → quantity`. Serializa a `"code:qty;code:qty"`
 * para sobrevivir rotación / muerte de proceso vía `rememberSaveable(stateSaver = ...)`.
 * Los códigos de denominación no contienen `:` ni `;`.
 */
val DenominationCountsSaver: Saver<Map<String, Int>, String> = Saver(
    save = { map -> map.entries.joinToString(";") { "${it.key}:${it.value}" } },
    restore = { encoded ->
        if (encoded.isBlank()) emptyMap()
        else encoded.split(";").mapNotNull { pair ->
            val parts = pair.split(":")
            val code = parts.getOrNull(0)?.takeIf { it.isNotEmpty() }
            val qty = parts.getOrNull(1)?.toIntOrNull()
            if (code != null && qty != null) code to qty else null
        }.toMap()
    }
)

/**
 * Tabla de denominaciones del Quetzal con input por fila y suma automática.
 * - `counts`: estado actual `denomCode → quantity` (controlled).
 * - `onChange`: invocado con el mapa nuevo cuando el usuario cambia un valor.
 * - `expectedCounts` (opcional): pre-llenado guía (ej. fondo dejado el día anterior).
 *   Si se pasa, se muestra una columna "Esperado" para comparar.
 * - `readOnly`: cuando true, los inputs son no editables (vista de detalle).
 */
@Composable
fun DenominationCounter(
    counts: Map<String, Int>,
    onChange: (Map<String, Int>) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Conteo por denominaciones",
    expectedCounts: Map<String, Int>? = null,
    readOnly: Boolean = false
) {
    val total = remember(counts) {
        counts.entries.sumOf { (code, qty) ->
            (CashDenominations.byCode(code)?.value ?: 0.0) * qty
        }
    }
    val expectedTotal = remember(expectedCounts) {
        expectedCounts?.entries?.sumOf { (code, qty) ->
            (CashDenominations.byCode(code)?.value ?: 0.0) * qty
        } ?: 0.0
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
            HorizontalDivider()

            Text("Billetes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            CashDenominations.BILLS.forEach { denom ->
                DenominationRow(
                    denom = denom,
                    quantity = counts[denom.code] ?: 0,
                    expected = expectedCounts?.get(denom.code),
                    readOnly = readOnly,
                    onChange = { newQty ->
                        val updated = counts.toMutableMap()
                        if (newQty <= 0) updated.remove(denom.code) else updated[denom.code] = newQty
                        onChange(updated)
                    }
                )
            }

            Spacer(Modifier.height(4.dp))
            Text("Monedas", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            CashDenominations.COINS.forEach { denom ->
                DenominationRow(
                    denom = denom,
                    quantity = counts[denom.code] ?: 0,
                    expected = expectedCounts?.get(denom.code),
                    readOnly = readOnly,
                    onChange = { newQty ->
                        val updated = counts.toMutableMap()
                        if (newQty <= 0) updated.remove(denom.code) else updated[denom.code] = newQty
                        onChange(updated)
                    }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (expectedCounts != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Esperado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Q ${expectedTotal.fmtMoney(2)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total contado", fontWeight = FontWeight.Bold, color = DeepSeaBlue)
                Text("Q ${total.fmtMoney(2)}", fontWeight = FontWeight.Bold, color = BrandColors.AccentDeep)
            }
        }
    }
}

@Composable
private fun DenominationRow(
    denom: CashDenomination,
    quantity: Int,
    expected: Int?,
    readOnly: Boolean,
    onChange: (Int) -> Unit
) {
    val subtotal = denom.value * quantity
    val emphasized = denom.type == CashDenominationType.BILL && denom.value >= 50.0
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // La etiqueta es el elemento flexible (weight): absorbe la compresión en pantallas
        // angostas y se recorta con ellipsis en vez de desbordar la fila.
        Text(
            denom.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(4.dp))
        if (!readOnly) {
            IconButton(
                onClick = { if (quantity > 0) onChange(quantity - 1) },
                enabled = quantity > 0,
                modifier = Modifier.size(32.dp)
            ) { Icon(Icons.Default.Remove, "−") }
        }
        OutlinedTextField(
            value = if (quantity == 0) "" else quantity.toString(),
            onValueChange = { text ->
                val sanitized = text.filter { it.isDigit() }.take(5)
                onChange(sanitized.toIntOrNull() ?: 0)
            },
            singleLine = true,
            readOnly = readOnly,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(76.dp),
            placeholder = { Text("0") }
        )
        if (!readOnly) {
            IconButton(
                onClick = { onChange(quantity + 1) },
                modifier = Modifier.size(32.dp)
            ) { Icon(Icons.Default.Add, "+") }
        }
        if (expected != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                "esp. $expected",
                style = MaterialTheme.typography.labelSmall,
                color = if (expected == quantity) MaterialTheme.colorScheme.outline
                        else Color(0xFFB00020)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "Q ${subtotal.fmtMoney(2)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (subtotal > 0) FontWeight.SemiBold else FontWeight.Normal,
            color = if (subtotal > 0) DeepSeaBlue else MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 64.dp)
        )
    }
}

