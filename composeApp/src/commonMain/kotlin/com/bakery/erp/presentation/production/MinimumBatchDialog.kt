package com.bakery.erp.presentation.production

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.ProductionOrder
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.RecipeBatchCheck
import com.bakery.erp.model.flourGramsAsPounds
import kotlin.math.roundToInt
import com.bakery.erp.ui.theme.BrandColors

/** Clave de una asignación de excedente: a qué pedido y de qué producto. */
internal data class ExtraAssignmentKey(val orderId: String, val productId: String)

/**
 * Decisión de la encargada cuando una receta no llega al mínimo de la amasadora.
 *
 * El reparto es solo del **excedente**: lo que cada sucursal pidió no se toca
 * (esa parte ya está comprometida). Lo que se decide es a dónde va el pan de más
 * que hay que hornear sí o sí para que la máquina pueda amasar. Lo que no se
 * asigne a ninguna sucursal se queda en la planta como stock terminado —
 * deliberadamente ese es el default, porque repartirle a una sucursal cinco
 * veces lo que pidió suele terminar en merma.
 */
@Composable
internal fun MinimumBatchDecisionDialog(
    check: RecipeBatchCheck,
    orders: List<ProductionOrder>,
    linesByOrder: Map<String, List<ProductionOrderLine>>,
    onDismiss: () -> Unit,
    onBakeMinimum: (
        totalByProduct: Map<String, Double>,
        retainedByProduct: Map<String, Double>,
        extraByOrder: Map<ExtraAssignmentKey, Double>
    ) -> Unit,
    onCancelProducts: (productIds: Set<String>, affectedOrderIds: Set<String>) -> Unit
) {
    val minimums = remember(check) { check.minimumQuantitiesByProduct() }
    val extras = remember(check) { check.extraUnitsByProduct() }

    // Qué pedidos solicitaron cada producto de esta receta.
    val requestersByProduct = remember(check, orders, linesByOrder) {
        check.lines.associate { line ->
            line.productId to orders.mapNotNull { order ->
                val qty = linesByOrder[order.id].orEmpty()
                    .filter { it.productId == line.productId }
                    .sumOf { it.requestedQuantity }
                if (qty > 0.0) Triple(order.id, order.sellingBranchName, qty) else null
            }
        }
    }
    val affectedOrderIds = remember(requestersByProduct) {
        requestersByProduct.values.flatten().map { it.first }.toSet()
    }

    // Excedente asignado a cada (pedido, producto). Vacío = todo queda en planta.
    var assigned by remember(check) { mutableStateOf(emptyMap<ExtraAssignmentKey, String>()) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    fun assignedFor(productId: String): Double = assigned
        .filterKeys { it.productId == productId }
        .values
        .sumOf { it.toDoubleOrNull() ?: 0.0 }

    val overAssigned = check.lines.any { line ->
        assignedFor(line.productId) > (extras[line.productId] ?: 0.0)
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("¿Anular estos productos?") },
            text = {
                Text(
                    "Se sacan del lote y no se van a hornear hoy. Se avisa a " +
                        (requestersByProduct.values.flatten()
                            .map { it.second }.distinct()
                            .joinToString(", ").ifEmpty { "las sucursales que los pidieron" }) +
                        " que ese producto no les va a llegar."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onCancelProducts(check.lines.map { it.productId }.toSet(), affectedOrderIds)
                    showCancelConfirm = false
                }) {
                    Text("Sí, anular", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Volver") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("«${check.recipeName}» no llega al mínimo") },
        text = {
            Column(modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFF8E1),
                    border = BorderStroke(1.dp, Color(0xFFFFE0B2)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Con lo que pidieron se mojarían solo " +
                                check.currentFlourGrams.flourGramsAsPounds() +
                                " de harina, y la amasadora necesita al menos " +
                                check.minFlourGrams.flourGramsAsPounds() + ".",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.AccentDeep
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                check.lines.forEach { line ->
                    val minimum = minimums[line.productId] ?: line.plannedQuantity
                    val extra = extras[line.productId] ?: 0.0
                    val assignedQty = assignedFor(line.productId)
                    val retained = (extra - assignedQty).coerceAtLeast(0.0)

                    Text(
                        line.productName,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.Primary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Pedido: ${line.plannedQuantity.asUnits()}  ·  " +
                            "Hay que hornear: ${minimum.asUnits()}  ·  " +
                            "De más: ${extra.asUnits()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))

                    if (extra > 0.0) {
                        Text(
                            "¿A quién le mandas el excedente?",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandColors.AccentDeep
                        )
                        requestersByProduct[line.productId].orEmpty()
                            .forEach { (orderId, branchName, requested) ->
                                val key = ExtraAssignmentKey(orderId, line.productId)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            branchName.ifBlank { "Sucursal" },
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "pidió ${requested.asUnits()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedTextField(
                                        value = assigned[key] ?: "",
                                        onValueChange = { txt ->
                                            if (txt.isEmpty() || txt.all { c -> c.isDigit() }) {
                                                assigned = assigned + (key to txt)
                                            }
                                        },
                                        placeholder = { Text("0") },
                                        label = { Text("+") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number
                                        ),
                                        modifier = Modifier.width(96.dp)
                                    )
                                }
                            }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Queda en la planta",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                retained.asUnits(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = BrandColors.Primary
                            )
                        }
                        if (assignedQty > extra) {
                            Text(
                                "Estás repartiendo ${assignedQty.asUnits()} pero solo sobran " +
                                    "${extra.asUnits()}.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFC62828)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }

                TextButton(onClick = { showCancelConfirm = true }) {
                    Text(
                        "No hornear: anular este producto",
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !overAssigned,
                onClick = {
                    val totals = mutableMapOf<String, Double>()
                    val retained = mutableMapOf<String, Double>()
                    val extraByOrder = mutableMapOf<ExtraAssignmentKey, Double>()
                    check.lines.forEach { line ->
                        val minimum = minimums[line.productId] ?: line.plannedQuantity
                        val extra = extras[line.productId] ?: 0.0
                        totals[line.productId] = minimum
                        assigned.filterKeys { it.productId == line.productId }.forEach { (key, txt) ->
                            val qty = txt.toDoubleOrNull() ?: 0.0
                            if (qty > 0.0) extraByOrder[key] = qty
                        }
                        retained[line.productId] =
                            (extra - assignedFor(line.productId)).coerceAtLeast(0.0)
                    }
                    onBakeMinimum(totals, retained, extraByOrder)
                }
            ) { Text("Hornear el mínimo", fontWeight = FontWeight.Bold, color = BrandColors.Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Decidir después") }
        }
    )
}

private fun Double.asUnits(): String {
    val rounded = this.roundToInt()
    return if (rounded == 1) "1 unidad" else "$rounded unidades"
}
