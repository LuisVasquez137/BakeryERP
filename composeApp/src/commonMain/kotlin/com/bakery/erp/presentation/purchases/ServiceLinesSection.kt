package com.bakery.erp.presentation.purchases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.Switch
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
import com.bakery.erp.model.PurchaseConcept
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseLineKind
import com.bakery.erp.model.findByName
import com.bakery.erp.model.forSupplier
import com.bakery.erp.model.lineTotal
import com.bakery.erp.model.matchesSearch
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt

/**
 * Conceptos de la factura que no entran a ningún inventario: mantenimiento del horno,
 * combustible, fletes, fumigación, bolsas, cajas de cartón.
 *
 * Es el único tipo de línea sin catálogo OBLIGATORIO detrás, y por eso vive aparte de las
 * otras tres: la ferretería vende algo distinto cada vez, así que dar de alta un ítem por
 * cada reparación llenaría el catálogo de cosas que no se vuelven a comprar. Cada línea
 * genera un gasto del mes y, si se le imputa un activo, también su evento en el historial.
 *
 * **Lo que sí se repite puede quedar pegado a un [PurchaseConcept]**, y ahí está la
 * diferencia: con concepto, "¿cuántas bolsas le llevo compradas a Plásticos del Sur?" se
 * contesta sumando; sin él, el mismo renglón escrito de tres formas son tres cosas distintas.
 * Se ofrece, no se exige.
 */
@Composable
internal fun ServiceLinesSection(
    lines: List<PurchaseLine>,
    assets: List<FixedAsset>,
    concepts: List<PurchaseConcept>,
    supplierId: String,
    onUpsert: (line: PurchaseLine, newConcept: PurchaseConcept?) -> Unit,
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
        "Mantenimientos, combustible, fletes, bolsas. No entran a inventario: van directo al " +
            "gasto del mes, pero quedan en el historial de este proveedor.",
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
                            // La cantidad primero: es el dato nuevo y el que se revisa contra
                            // el papel. Con una sola unidad no se muestra — "1 × Q400" es ruido.
                            lineQuantityLabel(line),
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
            concepts = concepts,
            supplierId = supplierId,
            onConfirm = { line, concept ->
                onUpsert(line, concept); showDialog = false; editing = null
            },
            onDismiss = { showDialog = false; editing = null }
        )
    }
}

/** "200 bolsas · Q 2.00 c/u". `null` cuando es una sola cosa y no aporta nada. */
private fun lineQuantityLabel(line: PurchaseLine): String? {
    val qty = line.quantityReceived
    if (qty <= 1.0) return null
    val unit = line.conceptUnit.ifEmpty { "" }
    val qtyText = qty.fmt(2).trimEnd('0').trimEnd('.')
    return buildString {
        append(qtyText)
        if (unit.isNotEmpty()) append(" $unit")
        if (line.unitCost > 0) append(" · Q ${line.unitCost.fmt(2)} c/u")
    }
}

/**
 * Alta o edición de un concepto de servicio.
 *
 * Pide categoría porque el reporte financiero desglosa por ella: "Q400 a la ferretería" no
 * dice si fue mantenimiento o transporte, y ésa es justo la pregunta que el reporte existe
 * para contestar. El activo es opcional — solo cuando el gasto es de una máquina concreta.
 *
 * La **cantidad viene en 1**: para un flete o una reparación se deja así y el formulario se
 * comporta como siempre; para las 200 bolsas es el único campo que hay que tocar de más, y sin
 * él el historial del proveedor puede decir cuánto se gastó pero nunca cuánto se compró.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ServiceLineDialog(
    existing: PurchaseLine?,
    assets: List<FixedAsset>,
    concepts: List<PurchaseConcept>,
    supplierId: String,
    onConfirm: (line: PurchaseLine, newConcept: PurchaseConcept?) -> Unit,
    onDismiss: () -> Unit
) {
    var concept by remember { mutableStateOf(existing?.conceptName ?: "") }
    var conceptId by remember { mutableStateOf(existing?.conceptId ?: "") }
    var unit by remember { mutableStateOf(existing?.conceptUnit ?: "") }
    var quantity by remember {
        mutableStateOf(
            existing?.quantityReceived?.takeIf { it > 0.0 }
                ?.fmt(2)?.trimEnd('0')?.trimEnd('.') ?: "1"
        )
    }
    var amount by remember {
        mutableStateOf(existing?.lineTotal?.takeIf { it > 0.0 }?.fmt(2) ?: "")
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
    // Encendido por default: el punto de escribirlo es poder sumarlo después, y un catálogo
    // que hay que acordarse de alimentar no se alimenta. Se apaga para lo de una sola vez.
    var saveToCatalog by remember { mutableStateOf(true) }

    val parsedAmount = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
    val parsedQty = quantity.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0
    val valid = concept.isNotBlank() && parsedAmount > 0.0

    // Lo que ya se le compra a este proveedor, primero. Al teclear se filtra: son sugerencias
    // que ahorran escribir, no un selector obligatorio.
    val suggestions = remember(concepts, supplierId, concept, conceptId) {
        if (conceptId.isNotEmpty()) emptyList()
        else concepts.forSupplier(supplierId)
            .filter { concept.isBlank() || it.matchesSearch(concept) }
            .take(6)
    }
    // Si lo tecleado es un concepto que ya existe, se usa ÉSE en vez de crear otro igual: es
    // la guarda contra tener "Bolsas #5" dos veces con dos ids.
    val alreadyInCatalog = remember(concepts, concept) { concepts.findByName(concept) }

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
                    onValueChange = { concept = it; conceptId = "" },
                    label = { Text("Concepto *") },
                    placeholder = { Text("Ej: Bolsas #5, mantenimiento del horno") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (suggestions.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        suggestions.forEach { c ->
                            AssistChip(
                                onClick = {
                                    concept = c.name
                                    conceptId = c.id
                                    unit = c.unit
                                    runCatching { ExpenseCategory.valueOf(c.expenseCategory) }
                                        .getOrNull()?.let { category = it }
                                    // Se propone el último precio pagado, como hace la
                                    // presentación de un insumo. Se puede pisar.
                                    if (amount.isBlank() && c.lastUnitCost > 0.0) {
                                        amount = (c.lastUnitCost * parsedQty).fmt(2)
                                    }
                                },
                                label = { Text(c.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
                if (conceptId.isNotEmpty()) {
                    Text(
                        "Del catálogo: se suma al historial de este concepto.",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.Primary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { v -> quantity = v.filter { it.isDigit() || it == '.' || it == ',' } },
                        label = { Text("Cantidad") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("¿En qué?") },
                        placeholder = { Text("bolsas") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(if (parsedQty > 1.0) "Total de este renglón (Q) *" else "Monto (Q) *") },
                    supportingText = if (parsedQty > 1.0 && parsedAmount > 0.0) {
                        { Text("Q ${(parsedAmount / parsedQty).fmt(2)} cada ${unit.ifEmpty { "una" }}") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                if (conceptId.isEmpty() && alreadyInCatalog == null && concept.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Recordar para el historial", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Para poder sumar cuánto le compro a este proveedor. Apagalo si " +
                                    "es algo de una sola vez.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = saveToCatalog, onCheckedChange = { saveToCatalog = it })
                    }
                }

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
                    // Lo tecleado ya existe en el catálogo aunque no se haya tocado el chip:
                    // se engancha igual, o se tendrían dos ids para el mismo nombre.
                    val resolvedId = conceptId.ifEmpty { alreadyInCatalog?.id.orEmpty() }
                    val resolvedUnit = unit.trim().ifEmpty {
                        alreadyInCatalog?.unit.orEmpty()
                    }
                    val newConcept = if (resolvedId.isEmpty() && saveToCatalog) PurchaseConcept(
                        name = concept.trim(),
                        unit = resolvedUnit.ifEmpty { "unidad" },
                        expenseCategory = category.name,
                        supplierIds = listOfNotNull(supplierId.takeIf { it.isNotEmpty() }),
                        lastUnitCost = parsedAmount / parsedQty
                    ) else null
                    onConfirm(
                        (existing ?: PurchaseLine()).copy(
                            lineKind = PurchaseLineKind.SERVICE,
                            conceptName = concept.trim(),
                            conceptId = resolvedId,
                            conceptUnit = resolvedUnit,
                            expenseCategory = category.name,
                            assetId = assetId,
                            assetName = assetName,
                            // Mantenimiento por defecto: es para lo que se imputa un activo
                            // casi siempre, y equivocarse se corrige desde el propio activo.
                            assetEventType = if (assetId.isEmpty()) ""
                                             else AssetEventType.MAINTENANCE.name,
                            // El monto tecleado es el TOTAL del renglón; se guarda dividido
                            // para que `lineTotal` —la fórmula única del importe— lo devuelva
                            // exacto, con cantidad o sin ella.
                            quantityReceived = parsedQty,
                            unitCost = parsedAmount / parsedQty,
                            packageCost = 0.0
                        ),
                        newConcept
                    )
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
