package com.bakery.erp.presentation.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import com.bakery.erp.model.itemName
import com.bakery.erp.model.lineTotal
import com.bakery.erp.util.fmt
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.IngredientUsage
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseLineKind
import com.bakery.erp.presentation.ingredients.IngredientPickerDialog
import com.bakery.erp.model.Product
import com.bakery.erp.model.ingredientPurchaseLineKind
import com.bakery.erp.model.isIngredientBacked
import com.bakery.erp.model.isRedirectedToIngredient
import com.bakery.erp.model.resalePurchaseLine
import com.bakery.erp.ui.theme.BrandColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * Línea de insumo capturada **sin dar de alta una presentación de compra**.
 *
 * Es la salida para la compra que no se repite: cinco sacos de harina en otra tienda porque
 * el molino no tenía. Registrar una `PurchaseFormat` ahí llenaría el catálogo del insumo de
 * presentaciones de proveedores a los que nunca se les vuelve a comprar, y ensuciaría el
 * costeo — el formato es el precio de referencia del insumo, no el de una compra suelta.
 *
 * Por eso pide lo mínimo que el inventario necesita: qué insumo, cuánto entró y cuánto
 * costó en total. Igual que la línea de servicio, no deja nada permanente detrás.
 */
@Composable
internal fun LooseIngredientLineDialog(
    candidates: List<Ingredient>,
    onDismiss: () -> Unit,
    onConfirm: (PurchaseLine) -> Unit
) {
    var ingredient by remember { mutableStateOf<Ingredient?>(null) }
    var quantity by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    val qty = quantity.replace(',', '.').toDoubleOrNull() ?: 0.0
    val total = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
    val valid = ingredient != null && qty > 0.0 && total > 0.0
    val unit = ingredient?.effectiveBaseUnit.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar insumo") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(ingredient?.name ?: "Elegir insumo *") }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text(if (unit.isEmpty()) "Cantidad *" else "Cantidad ($unit) *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Total (Q) *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                Text(
                    "Entra al inventario de la sucursal, pero no se guarda como presentación " +
                        "de compra: es para lo que no se repite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val ing = ingredient ?: return@TextButton
                    onConfirm(
                        PurchaseLine(
                            // Un suministro se gasta además de entrar al inventario; la
                            // materia prima no. Lo decide el insumo, no quien captura.
                            lineKind = ingredientPurchaseLineKind(ing),
                            ingredientId = ing.id,
                            ingredientName = ing.name,
                            ingredientUnit = ing.effectiveBaseUnit,
                            quantityReceived = qty,
                            // El monto tecleado es el total de la línea; se divide para que
                            // `lineTotal` —la fórmula única del importe— devuelva lo mismo.
                            unitCost = total / qty,
                            packageCost = 0.0,
                            formatId = ""
                        )
                    )
                }
            ) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )

    if (showPicker) {
        IngredientPickerDialog(
            ingredients = candidates,
            onPick = { ingredient = it; showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * Producto de reventa comprado a un proveedor ocasional: las gaseosas que se acabaron y se
 * fueron a traer a la tienda de enfrente.
 *
 * Mismo criterio que [LooseIngredientLineDialog]: **no da de alta una presentación**, porque
 * a esa tienda no se le vuelve a comprar. Lo que sí hace es ofrecer las presentaciones que el
 * producto YA tiene —de cualquier proveedor—: una caja de 24 es una caja de 24 venga de donde
 * venga, y obligar a teclear 24 unidades es justo el error que las presentaciones evitan.
 * "Por unidad" queda siempre, para lo que se compra suelto.
 *
 * Se teclea el TOTAL que se pagó, igual que en el insumo suelto: en una compra de la esquina el
 * papel (si hay) dice cuánto se pagó, no el precio unitario.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LooseResaleLineDialog(
    candidates: List<Product>,
    ingredients: List<Ingredient>,
    onDismiss: () -> Unit,
    onConfirm: (PurchaseLine) -> Unit
) {
    var product by remember { mutableStateOf<Product?>(null) }
    var search by remember { mutableStateOf("") }
    var formatId by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    val current = product
    val formats = current?.purchaseFormats.orEmpty().sortedByDescending { it.isDefault }
    val format = formats.firstOrNull { it.id == formatId }
    // Un producto que vende del stock de un insumo (la leche) no entra al inventario de
    // tienda: la línea se redirige al insumo. Ver `towardsLinkedIngredient`.
    val linkedIngredient = remember(current?.linkedIngredientId, ingredients) {
        current?.linkedIngredientId?.takeIf { it.isNotEmpty() }
            ?.let { id -> ingredients.find { it.id == id } }
    }
    val qty = quantity.replace(',', '.').toDoubleOrNull() ?: 0.0
    val total = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
    val saleUnit = current?.unit?.ifBlank { null } ?: "unidad"
    val packageWord = format?.description?.split(" ")?.firstOrNull()?.lowercase() ?: saleUnit
    val valid = current != null && qty > 0.0 && total > 0.0
    val shown = remember(candidates, search) {
        if (search.isBlank()) candidates.take(30)
        else candidates.filter { it.name.contains(search.trim(), ignoreCase = true) }.take(30)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(current?.name ?: "Agregar producto de reventa") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (current == null) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Buscar producto") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (shown.isEmpty()) {
                        Text(
                            "Ningún producto de reventa coincide. Los productos nuevos se crean en Productos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    shown.forEach { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = p.unit.takeIf { it.isNotBlank() }?.let { u ->
                                { Text("Se vende por $u", style = MaterialTheme.typography.labelSmall) }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                product = p
                                // Arranca en la presentación predeterminada: si el producto se
                                // compra en cajas, lo normal es que también hoy sea una caja.
                                formatId = (p.purchaseFormats.firstOrNull { it.isDefault }
                                    ?: p.purchaseFormats.firstOrNull())?.id.orEmpty()
                            }
                        )
                        HorizontalDivider()
                    }
                } else {
                    if (formats.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            (formats.map { it.id to it.description } + ("" to "Por $saleUnit")).forEach { (id, label) ->
                                FilterChip(
                                    selected = formatId == id,
                                    onClick = { formatId = id },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { v -> quantity = v.filter { it.isDigit() || it == '.' || it == ',' } },
                            label = { Text("Cantidad *") },
                            suffix = { Text(packageWord, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' || it == ',' } },
                            label = { Text("Total (Q) *") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                    if (valid) {
                        val preview = resalePurchaseLine(current, format, qty, total / qty, linkedIngredient)
                        val destUnit = if (preview.isRedirectedToIngredient)
                            preview.ingredientUnit.ifEmpty { "unidad" } else saleUnit
                        Text(
                            "= ${preview.quantityReceived.fmt(2).trimEnd('0').trimEnd('.')} $destUnit al inventario · " +
                                "Q ${preview.unitCost.fmt(2)} c/u",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandColors.Primary
                        )
                    }
                    Text(
                        if (linkedIngredient != null || current.isIngredientBacked)
                            "Entra al inventario de ${linkedIngredient?.name ?: current.linkedIngredientName}, " +
                                "que es de donde este producto vende. No se guarda como presentación de este lugar."
                        else "Entra al inventario de tienda. No se guarda como presentación de este lugar: " +
                            "es para lo que no se repite.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { product = null; formatId = "" }) { Text("Elegir otro producto") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val p = product ?: return@TextButton
                    // El total se divide entre los empaques para que `lineTotal` —packageCost ×
                    // paquetes— devuelva exactamente lo tecleado.
                    onConfirm(resalePurchaseLine(p, format, qty, total / qty, linkedIngredient))
                }
            ) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Lo ya capturado en una compra sin catálogo.
 *
 * Existe porque en el flujo normal las líneas se ven dentro de la fila de su insumo en el
 * catálogo del proveedor; sin catálogo no hay dónde verlas, y una lista de la que no se ve
 * lo que lleva no se puede revisar antes de guardar.
 */
@Composable
internal fun LooseLinesList(
    lines: List<PurchaseLine>,
    onRemove: (PurchaseLine) -> Unit
) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(line.itemName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        // Reventa por presentación: se lee como en la factura ("2 × Caja 24")
                        // y entre paréntesis lo que entra al inventario.
                        if (line.formatId.isNotEmpty() && line.packagesReceived > 0)
                            "${formatPurchaseQty(line.packagesReceived)} × ${line.packageDescription} " +
                                "(${formatPurchaseQty(line.quantityReceived)} ${line.ingredientUnit})"
                        else "${line.quantityReceived.fmt(2)} ${line.ingredientUnit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Q ${line.lineTotal.fmt(2)}", style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { onRemove(line) }) {
                    Icon(Icons.Default.Close, contentDescription = "Quitar")
                }
            }
        }
    }
}
