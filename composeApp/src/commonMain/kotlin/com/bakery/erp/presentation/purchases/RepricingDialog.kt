package com.bakery.erp.presentation.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.RepriceSuggestion
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt

/**
 * "Subió el costo de N productos" — el único momento en que alguien decide el precio nuevo.
 *
 * Aparece DESPUÉS de guardar la factura, no antes: la compra es un hecho y el precio de venta
 * es otra decisión. Si bloqueara el guardado, se cerraría sin leer para poder seguir.
 *
 * Cada fila dice de dónde sale el número —cuánto subió el costo y en qué queda el margen—
 * porque una sugerencia de precio que no se explica no se aplica, se cierra. Y el precio es
 * editable: la sugerencia conserva el margen, pero el que conoce a su clientela es el dueño.
 */
@Composable
internal fun RepricingDialog(
    suggestions: List<RepriceSuggestion>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onApply: (Map<String, Double>) -> Unit
) {
    if (suggestions.isEmpty()) return

    // Marcados por defecto: el caso normal es aceptar. Quien no quiera, desmarca.
    val checked = remember(suggestions) {
        mutableStateMapOf<String, Boolean>().apply {
            suggestions.forEach { put(it.productId, true) }
        }
    }
    val prices = remember(suggestions) {
        mutableStateMapOf<String, String>().apply {
            suggestions.forEach { put(it.productId, it.suggestedPrice.fmt(2)) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (suggestions.size == 1) "Subió el costo de un producto"
                else "Subió el costo de ${suggestions.size} productos"
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "El precio sugerido conserva el margen que tenía cada producto, redondeado " +
                        "al siguiente medio quetzal. Podés cambiarlo o desmarcar lo que no " +
                        "quieras tocar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                suggestions.forEach { s ->
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checked[s.productId] ?: true,
                            onCheckedChange = { checked[s.productId] = it }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.productName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Costo Q${s.oldCost.fmt(2)} → Q${s.newCost.fmt(2)} " +
                                    "(+${s.costIncreasePct.fmt(1)}%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Precio hoy Q${s.currentPrice.fmt(2)} · margen " +
                                    "${s.currentMarginPct.fmt(1)}% → ${s.suggestedMarginPct.fmt(1)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandColors.AccentDeep
                            )
                        }
                        OutlinedTextField(
                            value = prices[s.productId] ?: "",
                            onValueChange = { raw ->
                                prices[s.productId] = raw.filter { it.isDigit() || it == '.' || it == ',' }
                            },
                            prefix = { Text("Q") },
                            singleLine = true,
                            enabled = checked[s.productId] ?: true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(120.dp).padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving && checked.any { it.value },
                onClick = {
                    val accepted = suggestions.mapNotNull { s ->
                        if (checked[s.productId] != true) return@mapNotNull null
                        val value = prices[s.productId]?.replace(",", ".")?.toDoubleOrNull()
                        if (value == null || value <= 0.0) null else s.productId to value
                    }.toMap()
                    onApply(accepted)
                }
            ) { Text("Aplicar precios") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } }
    )
}
