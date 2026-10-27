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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bakery.erp.model.Product
import com.bakery.erp.model.suggestedTransformOutput
import com.bakery.erp.model.transformExtrasFor
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtTrim

/**
 * "Transformar": pan molde → pan tostado, pirujo de mantequilla → pirujo con ajo. Se elige qué
 * se hace, cuántos del original se usaron y cuántos salieron (llega con lo normal, se corrige a
 * lo real). La vista previa dice cómo queda el inventario y qué insumos se descuentan.
 * Generaliza [PortioningDialog]; ver `model/Transformation.kt`.
 */
@Composable
fun TransformDialog(
    products: List<Product>,
    stockOf: (productId: String) -> Double,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (product: Product, sourceUnits: Double, produced: Double, note: String?) -> Unit
) {
    val sorted = remember(products) { products.sortedBy { it.name.lowercase() } }
    var selected by remember { mutableStateOf(sorted.singleOrNull()) }

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).heightIn(max = 640.dp)
        ) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Transformar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary
                )
                val current = selected
                if (current == null) {
                    TransformPicker(sorted, stockOf, onPick = { selected = it })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancelar") }
                    }
                } else {
                    TransformAmounts(
                        product = current,
                        sourceStock = stockOf(current.transformFromProductId),
                        productStock = stockOf(current.id),
                        isBusy = isBusy,
                        onBack = if (sorted.size > 1) ({ selected = null }) else null,
                        onDismiss = onDismiss,
                        onConfirm = { used, out, note -> onConfirm(current, used, out, note) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransformPicker(products: List<Product>, stockOf: (String) -> Double, onPick: (Product) -> Unit) {
    Text("¿Qué vas a hacer?", style = MaterialTheme.typography.bodyMedium)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 400.dp)) {
        items(products, key = { it.id }) { p ->
            val have = stockOf(p.transformFromProductId)
            val available = have > 0.0
            Card(
                onClick = { onPick(p) },
                enabled = available,
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = BrandColors.Sand),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                        Text("de ${p.transformFromProductName}", style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.MutedSlate)
                    }
                    Text(
                        if (available) "${have.fmtTrim()} ${p.transformFromProductName}" else "No hay",
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
private fun TransformAmounts(
    product: Product,
    sourceStock: Double,
    productStock: Double,
    isBusy: Boolean,
    onBack: (() -> Unit)?,
    onDismiss: () -> Unit,
    onConfirm: (sourceUnits: Double, produced: Double, note: String?) -> Unit
) {
    val max = sourceStock.toInt().coerceAtLeast(0)
    var used by remember(product.id) { mutableStateOf(if (max >= 1) 1 else 0) }
    // Lo que salió arranca con lo normal y sigue a "usé" hasta que lo corrijan a mano.
    var producedText by remember(product.id) { mutableStateOf(product.suggestedTransformOutput(used.toDouble()).fmtTrim()) }
    var producedEdited by remember(product.id) { mutableStateOf(false) }
    var note by remember(product.id) { mutableStateOf("") }
    fun setUsed(n: Int) {
        used = n
        if (!producedEdited) producedText = product.suggestedTransformOutput(n.toDouble()).fmtTrim()
    }
    val produced = producedText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val src = product.transformFromProductName

    Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        color = BrandColors.TextSlate)
    if (max < 1) {
        Text(
            "Según el inventario no hay $src en esta sucursal. Si hay, primero ajusta el inventario.",
            color = BrandColors.Danger, style = MaterialTheme.typography.bodyMedium
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$src que usaste", Modifier.weight(1f))
            FilledTonalIconButton(onClick = { setUsed(used - 1) }, enabled = !isBusy && used > 1) {
                Icon(Icons.Default.Remove, "Uno menos")
            }
            Text("$used", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
            FilledTonalIconButton(onClick = { setUsed(used + 1) }, enabled = !isBusy && used < max) {
                Icon(Icons.Default.Add, "Uno más")
            }
        }
        OutlinedTextField(
            value = producedText,
            onValueChange = { v -> producedText = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6); producedEdited = true },
            label = { Text("${product.name} que salieron") },
            supportingText = { Text("Lo normal: ${product.transformYield.fmtTrim(2)} de cada $src") },
            singleLine = true,
            enabled = !isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(120) },
            label = { Text("Nota (opcional)") },
            singleLine = true,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = BrandColors.TealSoft),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$src: ${sourceStock.fmtTrim()} → ${(sourceStock - used).fmtTrim()}", color = BrandColors.TextSlate)
                Text(
                    "${product.name}: ${productStock.fmtTrim()} → ${(productStock + produced).fmtTrim()}",
                    color = BrandColors.TextSlate, fontWeight = FontWeight.SemiBold
                )
                val extras = product.transformExtrasFor(produced)
                if (extras.isNotEmpty()) {
                    Spacer(Modifier.size(2.dp))
                    Text("Se descuenta: " + extras.joinToString(", ") { (e, q) -> "${q.fmtTrim(2)} ${e.unit} ${e.ingredientName}" },
                        style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                }
            }
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) TextButton(onClick = onBack, enabled = !isBusy) { Text("Otro") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancelar") }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { onConfirm(used.toDouble(), produced, note.trim().ifEmpty { null }) },
            enabled = !isBusy && used >= 1 && produced > 0.0
        ) {
            if (isBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Hacer ${produced.fmtTrim()}")
        }
    }
}
