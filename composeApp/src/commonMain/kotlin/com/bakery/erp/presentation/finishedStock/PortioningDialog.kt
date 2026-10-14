package com.bakery.erp.presentation.finishedStock

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bakery.erp.model.Product
import com.bakery.erp.model.isPortion
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtTrim

/**
 * "Porcionar pastel": la tienda corta enteros que no se vendieron. Primero se elige qué
 * pastel (la lista sale de los productos porción, con las existencias del entero), después
 * cuántos enteros y en cuántas porciones. La vista previa dice cómo queda el inventario de
 * los dos para que nadie confirme a ciegas.
 *
 * La usan el inventario de tienda y el POS (la cajera corta el pastel sin salir de la venta).
 * [onConfirm] recibe la porción, los enteros y las porciones por entero; el diálogo se cierra
 * cuando quien lo abrió lo decida (al terminar de guardar).
 */
@Composable
fun PortioningDialog(
    products: List<Product>,
    stockOf: (productId: String) -> Double,
    isBusy: Boolean,
    initialPortionId: String? = null,
    /** Por qué se abrió solo (al cobrar); null cuando la cajera lo pidió. */
    message: String? = null,
    dismissLabel: String = "Cancelar",
    onDismiss: () -> Unit,
    onConfirm: (portion: Product, wholes: Int, portionsPerWhole: Int) -> Unit
) {
    val portions = remember(products) {
        products.filter { it.active && it.isPortion }.sortedBy { it.portionOfProductName.lowercase() }
    }
    var selected by remember { mutableStateOf(portions.firstOrNull { it.id == initialPortionId }) }

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).heightIn(max = 620.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Porcionar pastel",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary
                )
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextSlate)
                }
                val current = selected
                if (current == null) {
                    PortionPicker(portions, stockOf, onPick = { selected = it })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancelar") }
                    }
                } else {
                    PortionAmounts(
                        portion = current,
                        wholeStock = stockOf(current.portionOfProductId),
                        portionStock = stockOf(current.id),
                        isBusy = isBusy,
                        // Sin opción de cambiar si se abrió ya con el pastel elegido (desde su fila).
                        onBack = if (initialPortionId == null) ({ selected = null }) else null,
                        dismissLabel = dismissLabel,
                        onDismiss = onDismiss,
                        onConfirm = { w, n -> onConfirm(current, w, n) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PortionPicker(
    portions: List<Product>,
    stockOf: (String) -> Double,
    onPick: (Product) -> Unit
) {
    if (portions.isEmpty()) {
        Text(
            "Todavía no hay porciones configuradas. En Productos, crea por ejemplo " +
                "«Porción de selva negra» con su precio y activa «Es porción de un pastel».",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Text("¿Qué pastel vas a cortar?", style = MaterialTheme.typography.bodyMedium)
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.heightIn(max = 400.dp)
    ) {
        items(portions, key = { it.id }) { p ->
            val wholes = stockOf(p.portionOfProductId)
            val available = wholes >= 1.0
            Card(
                onClick = { onPick(p) },
                enabled = available,
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = BrandColors.Sand),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.portionOfProductName, fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                        Text(
                            "→ ${p.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.MutedSlate
                        )
                    }
                    Text(
                        if (available) "${wholes.fmtTrim()} en tienda" else "No hay enteros",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (available) BrandColors.Primary else BrandColors.MutedSlate
                    )
                }
            }
        }
    }
}

@Composable
private fun PortionAmounts(
    portion: Product,
    wholeStock: Double,
    portionStock: Double,
    isBusy: Boolean,
    onBack: (() -> Unit)?,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (wholes: Int, portionsPerWhole: Int) -> Unit
) {
    val maxWholes = wholeStock.toInt().coerceAtLeast(0)
    var wholes by remember(portion.id) { mutableStateOf(if (maxWholes >= 1) 1 else 0) }
    var perWholeText by remember(portion.id) {
        mutableStateOf(portion.portionsPerWhole.takeIf { it > 0 }?.toString() ?: "")
    }
    val perWhole = perWholeText.toIntOrNull() ?: 0
    val total = wholes * perWhole

    Text(
        portion.portionOfProductName,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = BrandColors.TextSlate
    )
    if (maxWholes < 1) {
        Text(
            "Según el inventario no hay ${portion.portionOfProductName} en esta tienda. Si está " +
                "en el estante, primero ajusta el inventario del entero.",
            color = BrandColors.Danger,
            style = MaterialTheme.typography.bodyMedium
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enteros a cortar", Modifier.weight(1f))
            FilledTonalIconButton(onClick = { wholes-- }, enabled = !isBusy && wholes > 1) {
                Icon(Icons.Default.Remove, "Uno menos")
            }
            Text(
                "$wholes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(48.dp)
            )
            FilledTonalIconButton(onClick = { wholes++ }, enabled = !isBusy && wholes < maxWholes) {
                Icon(Icons.Default.Add, "Uno más")
            }
        }
        OutlinedTextField(
            value = perWholeText,
            onValueChange = { perWholeText = it.filter(Char::isDigit).take(3) },
            label = { Text("Porciones de cada uno") },
            singleLine = true,
            enabled = !isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        // Cómo queda el estante: es lo que la cajera puede comprobar con los ojos.
        Card(
            colors = CardDefaults.cardColors(containerColor = BrandColors.TealSoft),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${portion.portionOfProductName}: ${wholeStock.fmtTrim()} → ${(wholeStock - wholes).fmtTrim()}",
                    color = BrandColors.TextSlate
                )
                Text(
                    "${portion.name}: ${portionStock.fmtTrim()} → ${(portionStock + total).fmtTrim()}",
                    color = BrandColors.TextSlate,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) TextButton(onClick = onBack, enabled = !isBusy) { Text("Otro pastel") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss, enabled = !isBusy) { Text(dismissLabel) }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { onConfirm(wholes, perWhole) },
            enabled = !isBusy && wholes >= 1 && perWhole >= 1
        ) {
            if (isBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(if (total > 0) "Cortar en $total" else "Cortar")
        }
    }
}
