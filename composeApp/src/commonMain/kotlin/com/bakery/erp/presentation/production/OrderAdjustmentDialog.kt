package com.bakery.erp.presentation.production

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.checkRecipeBatchMinimums
import com.bakery.erp.model.orderAdjustmentNeedsVerification
import com.bakery.erp.model.orderAdjustmentProblem
import com.bakery.erp.model.tripLabel
import com.bakery.erp.model.tripMismatchFor
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.model.isPortion

/** Producto que se puede ajustar: lo que la sucursal ya pidió, o cualquiera del catálogo. */
private data class AdjustableProduct(
    val id: String,
    val name: String,
    val unit: String,
    /** Lo que pidió la sucursal en total, 0 si no estaba en el pedido. */
    val requested: Double
)

/**
 * Ajuste del pedido de UNA sucursal con el reparto ya hecho: producto, cuánto se
 * suma o se quita, y en qué turno. Ver `model/OrderAdjustment.kt`.
 *
 * El turno se elige siempre, también cuando solo uno lo admite: lo que tiene que
 * quedar claro es en qué amasada cae el cambio, que es lo que la encargada le va a
 * decir al panadero.
 */
@Composable
fun OrderAdjustmentDialog(
    context: OrderAdjustmentContext,
    productsById: Map<String, Product>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (lotId: String, productId: String, productName: String, productUnit: String, delta: Double, verified: Boolean) -> Unit
) {
    val branchName = context.order.sellingBranchName
    val products = remember(context, productsById) {
        val inOrder = context.orderLines.filter { !it.isSpecial }.map {
            AdjustableProduct(it.productId, it.productName, it.productUnit, it.requestedQuantity)
        }
        val ids = inOrder.map { it.id }.toSet()
        val others = productsById.values
            .filter { it.active && it.isInHouseProduced && !it.isPortion && it.id !in ids }
            .map { AdjustableProduct(it.id, it.name, it.unit, 0.0) }
        inOrder.sortedBy { it.name.lowercase() } + others.sortedBy { it.name.lowercase() }
    }

    var query by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(true) }
    var qtyText by remember { mutableStateOf("") }
    var selectedLotId by remember { mutableStateOf<String?>(null) }
    var verified by remember { mutableStateOf(false) }

    val product = products.find { it.id == selectedProductId }
    val qty = qtyText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val delta = if (adding) qty else -qty
    val shifts = context.adjustableShifts
    val shift = shifts.find { it.lot.id == selectedLotId }
    // Verificar es por masa y por turno: cambiar cualquiera de las dos invalida la
    // confirmación anterior.
    LaunchedEffect(selectedLotId, selectedProductId) { verified = false }

    val target = if (shift != null && product != null) context.targetFor(shift, product.id) else null
    val problem = target?.let { orderAdjustmentProblem(it, delta) }
    val needsVerification = shift != null && orderAdjustmentNeedsVerification(shift.lot.status)
    val recipeName = if (shift != null && product != null)
        context.recipesById[context.recipeIdFor(shift, product.id)]?.name.orEmpty() else ""

    val warnings = buildList {
        if (shift != null && delta > 0.0) {
            tripMismatchFor(branchName, context.branchTrip, shift.shift?.dispatchOrder ?: 0)?.let {
                add("$branchName solo recibe en el ${tripLabel(it.restrictedTrip)}: este pan se va a guardar para ese viaje.")
            }
        }
        if (shift != null && product != null && delta < 0.0) {
            val recipeId = context.recipeIdFor(shift, product.id)
            val after = shift.lines.map {
                if (it.productId == product.id && !it.isSpecial)
                    it.copy(plannedQuantity = (it.plannedQuantity + delta).coerceAtLeast(0.0)) else it
            }
            val under = checkRecipeBatchMinimums(after, context.recipesById) {
                it.rawWeightGrams ?: productsById[it.productId]?.rawWeightGrams
            }.find { it.recipeId == recipeId && it.isUnderMinimum }
            if (under != null) add("La masa ${under.recipeName} queda bajo el mínimo de amasada en ${shift.label}.")
        }
    }

    val canSave = product != null && shift != null && qty > 0.0 && problem == null &&
        (!needsVerification || verified) && !isSaving

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Ajustar pedido de $branchName", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "El cambio es solo para $branchName y sale en su nota del turno que elijas. " +
                        "No entra al inventario de la planta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                // ── Producto ──
                Text("Producto", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (product != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BrandColors.PrimaryContainer,
                        modifier = Modifier.fillMaxWidth().clickable { selectedProductId = null }
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(product.name, fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
                                Text(
                                    if (product.requested > 0) "Pidió ${product.requested.qty()} en total"
                                    else "No estaba en su pedido",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text("Cambiar", style = MaterialTheme.typography.labelMedium, color = BrandColors.Primary)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar producto") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val filtered = products.filter { it.name.contains(query.trim(), ignoreCase = true) }.take(30)
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        filtered.forEach { p ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    selectedProductId = p.id
                                    // No se le puede quitar lo que no pidió.
                                    if (p.requested <= 0.0) adding = true
                                }.padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Text(p.name, modifier = Modifier.weight(1f))
                                if (p.requested > 0) Text(
                                    "pidió ${p.requested.qty()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }

                if (product != null) {
                    // ── Sumar / quitar + cantidad ──
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = adding, onClick = { adding = true }, label = { Text("Ponerle más") })
                        FilterChip(selected = !adding, onClick = { adding = false }, label = { Text("Quitarle") })
                    }
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { v -> qtyText = v.filter { it.isDigit() || it == '.' || it == ',' } },
                        label = { Text(if (adding) "¿Cuántos más?" else "¿Cuántos menos?") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ── Turno ──
                    Text("¿En qué turno?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    shifts.forEach { s ->
                        val t = context.targetFor(s, product.id)
                        val status = if (s.lot.status == ProductionLotStatus.IN_PROGRESS) "en producción" else "sin empezar"
                        val unavailable = (adding && !t.shiftProducesProduct)
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = !unavailable) { selectedLotId = s.lot.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLotId == s.lot.id,
                                onClick = { selectedLotId = s.lot.id },
                                enabled = !unavailable
                            )
                            Column {
                                Text("${s.label} · $status", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (unavailable) "Este turno no hace este pan"
                                    else "Tiene ${t.currentForBranch.qty()} para $branchName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (unavailable) Color.Gray else BrandColors.MutedSlate
                                )
                            }
                        }
                    }

                    if (needsVerification) {
                        Surface(color = BrandColors.AccentSoft, shape = RoundedCornerShape(8.dp)) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = verified, onCheckedChange = { verified = it })
                                Text(
                                    "Confirmé con el panadero que la masa " +
                                        "${recipeName.ifEmpty { "de este pan" }} todavía no se ha hecho.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrandColors.TextSlate
                                )
                            }
                        }
                    }

                    if (qty > 0.0 && problem != null) {
                        Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    warnings.forEach {
                        Text(it, color = BrandColors.AccentDeep, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = product ?: return@Button
                    val s = shift ?: return@Button
                    onConfirm(s.lot.id, p.id, p.name, p.unit, delta, verified)
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Guardar ajuste")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
        }
    )
}

private fun Double.qty(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
