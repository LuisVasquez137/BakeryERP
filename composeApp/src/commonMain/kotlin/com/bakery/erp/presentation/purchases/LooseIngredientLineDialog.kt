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
                            lineKind = if (ing.usage == IngredientUsage.SUPPLY)
                                PurchaseLineKind.SUPPLY else PurchaseLineKind.INGREDIENT,
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
                        "${line.quantityReceived.fmt(2)} ${line.ingredientUnit}",
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
