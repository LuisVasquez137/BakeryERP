package com.bakery.erp.presentation.purchases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.AssetEventType
import com.bakery.erp.model.ExpenseCategory
import com.bakery.erp.model.FixedAsset
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseLineKind
import com.bakery.erp.model.lineTotal
import com.bakery.erp.util.fmt

/**
 * Conceptos de la factura que no entran a ningún inventario: mantenimiento del horno,
 * combustible, fletes, fumigación.
 *
 * Es el único tipo de línea SIN catálogo detrás, y por eso vive aparte de las otras tres:
 * la ferretería vende algo distinto cada vez, así que dar de alta un ítem por cada
 * reparación llenaría el catálogo de cosas que no se vuelven a comprar. Cada línea genera
 * un gasto del mes y, si se le imputa un activo, también su evento en el historial.
 */
@Composable
internal fun ServiceLinesSection(
    lines: List<PurchaseLine>,
    assets: List<FixedAsset>,
    onUpsert: (PurchaseLine) -> Unit,
    onRemove: (String) -> Unit
) {
    var editing by remember { mutableStateOf<PurchaseLine?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Spacer(Modifier.height(8.dp))
    Text(
        "Servicios y gastos",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        "Mantenimientos, combustible, fletes. No entran a inventario: van directo al gasto del mes.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    lines.forEach { line ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).clickable {
                        editing = line
                        showDialog = true
                    }
                ) {
                    Text(
                        line.conceptName.ifEmpty { "(sin descripción)" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        listOfNotNull(
                            runCatching { ExpenseCategory.valueOf(line.expenseCategory).label }
                                .getOrNull(),
                            line.assetName.takeIf { it.isNotEmpty() }
                        ).joinToString(" · ").ifEmpty { "Sin categoría" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Q ${line.lineTotal.fmt(2)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { onRemove(line.id) }) {
                    Icon(Icons.Default.Close, contentDescription = "Quitar concepto")
                }
            }
        }
    }

    OutlinedButton(
        onClick = { editing = null; showDialog = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Agregar concepto")
    }

    if (showDialog) {
        ServiceLineDialog(
            existing = editing,
            assets = assets,
            onConfirm = { onUpsert(it); showDialog = false; editing = null },
            onDismiss = { showDialog = false; editing = null }
        )
    }
}

/**
 * Alta o edición de un concepto de servicio.
 *
 * Pide categoría porque el reporte financiero desglosa por ella: "Q400 a la ferretería" no
 * dice si fue mantenimiento o transporte, y ésa es justo la pregunta que el reporte existe
 * para contestar. El activo es opcional — solo cuando el gasto es de una máquina concreta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceLineDialog(
    existing: PurchaseLine?,
    assets: List<FixedAsset>,
    onConfirm: (PurchaseLine) -> Unit,
    onDismiss: () -> Unit
) {
    var concept by remember { mutableStateOf(existing?.conceptName ?: "") }
    var amount by remember {
        mutableStateOf(existing?.lineTotal?.takeIf { it > 0.0 }?.toString() ?: "")
    }
    var category by remember {
        mutableStateOf(
            runCatching { ExpenseCategory.valueOf(existing?.expenseCategory ?: "") }
                .getOrElse { ExpenseCategory.MANTENIMIENTO }
        )
    }
    var assetId by remember { mutableStateOf(existing?.assetId ?: "") }
    var assetName by remember { mutableStateOf(existing?.assetName ?: "") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var assetExpanded by remember { mutableStateOf(false) }

    val parsedAmount = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
    val valid = concept.isNotBlank() && parsedAmount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Agregar concepto" else "Editar concepto") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = concept,
                    onValueChange = { concept = it },
                    label = { Text("Concepto *") },
                    placeholder = { Text("Ej: Mantenimiento del horno rotativo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monto (Q) *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = category.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de gasto") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded)
                        },
                        modifier = Modifier.fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        ExpenseCategory.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.label) },
                                onClick = { category = opt; categoryExpanded = false }
                            )
                        }
                    }
                }
                if (assets.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = assetExpanded,
                        onExpandedChange = { assetExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = assetName.ifEmpty { "Ninguno (gasto general)" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("¿A qué máquina o vehículo?") },
                            supportingText = {
                                Text(
                                    "Opcional. Si eliges uno, el gasto queda en su historial " +
                                        "y actualiza sus costos acumulados."
                                )
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(assetExpanded)
                            },
                            modifier = Modifier.fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = assetExpanded,
                            onDismissRequest = { assetExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ninguno (gasto general)") },
                                onClick = {
                                    assetId = ""; assetName = ""; assetExpanded = false
                                }
                            )
                            assets.forEach { asset ->
                                DropdownMenuItem(
                                    text = { Text(asset.name) },
                                    onClick = {
                                        assetId = asset.id
                                        assetName = asset.name
                                        assetExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(
                        (existing ?: PurchaseLine()).copy(
                            lineKind = PurchaseLineKind.SERVICE,
                            conceptName = concept.trim(),
                            expenseCategory = category.name,
                            assetId = assetId,
                            assetName = assetName,
                            // Mantenimiento por defecto: es para lo que se imputa un activo
                            // casi siempre, y equivocarse se corrige desde el propio activo.
                            assetEventType = if (assetId.isEmpty()) ""
                                             else AssetEventType.MAINTENANCE.name,
                            // Cantidad 1 y el monto en el costo unitario: así `lineTotal`
                            // —la fórmula única del importe— vale sin ningún caso especial.
                            quantityReceived = 1.0,
                            unitCost = parsedAmount,
                            packageCost = 0.0
                        )
                    )
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
