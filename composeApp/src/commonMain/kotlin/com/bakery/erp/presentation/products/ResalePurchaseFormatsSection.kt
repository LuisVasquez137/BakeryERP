package com.bakery.erp.presentation.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.bakery.erp.model.PurchaseFormat
import com.bakery.erp.model.Supplier
import com.bakery.erp.model.normalizedDefault
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt
import kotlin.random.Random

/**
 * Presentaciones de compra de un producto de reventa, en su formulario.
 *
 * Reemplaza al "proveedor + costo" único: un mismo producto lo venden varios proveedores, y
 * cada uno lo entrega en su propio empaque. El margen se calcula contra la presentación
 * predeterminada, que es también el costo que el POS usa para la venta.
 */
@Composable
internal fun ResalePurchaseFormatsSection(
    formats: List<PurchaseFormat>,
    saleUnit: String,
    salePrice: String,
    suppliers: List<Supplier>,
    onFormatsChange: (List<PurchaseFormat>) -> Unit
) {
    var editing by remember { mutableStateOf<PurchaseFormat?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val unitLabel = saleUnit.ifBlank { "unidad" }

    Text(
        "Cómo te lo venden: la caja, el fardo, el six pack. Agregá una por cada proveedor que te " +
            "lo trae. Al capturar la factura anotás \"1 caja\" y entran las unidades que trae.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (formats.isEmpty()) {
        Text(
            "Sin presentaciones: se compra por $unitLabel suelta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }

    formats.sortedByDescending { it.isDefault }.forEach { format ->
        ResaleFormatCard(
            format = format,
            saleUnit = unitLabel,
            canSetDefault = formats.size > 1 && !format.isDefault,
            onEdit = { editing = format; showDialog = true },
            onDelete = { onFormatsChange(formats.filterNot { it.id == format.id }.normalizedDefault()) },
            onSetDefault = { onFormatsChange(formats.map { it.copy(isDefault = it.id == format.id) }) }
        )
    }

    OutlinedButton(
        onClick = { editing = null; showDialog = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Agregar presentación de compra")
    }

    // Margen contra la predeterminada: es el costo que el POS registra al vender.
    val reference = formats.firstOrNull { it.isDefault } ?: formats.firstOrNull()
    val cost = reference?.costPerUnit ?: 0.0
    val price = salePrice.toDoubleOrNull() ?: 0.0
    if (cost > 0.0 && price > 0.0) {
        val margin = price - cost
        Text(
            text = "Costo Q ${cost.fmt(2)} por $unitLabel · Margen Q ${margin.fmt(2)} (${(margin / price * 100).fmt(1)}%)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (margin >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }

    if (showDialog) {
        ResalePurchaseFormatDialog(
            format = editing,
            saleUnit = unitLabel,
            suppliers = suppliers,
            onDismiss = { showDialog = false },
            onConfirm = { saved ->
                showDialog = false
                val others = formats.filterNot { it.id == saved.id }
                onFormatsChange((others + saved.copy(isDefault = saved.isDefault || others.isEmpty())).normalizedDefault())
            }
        )
    }
}

@Composable
private fun ResaleFormatCard(
    format: PurchaseFormat,
    saleUnit: String,
    canSetDefault: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (format.isDefault) BrandColors.PrimaryContainer else BrandColors.Sand
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    buildString {
                        append(format.description.ifEmpty { "Sin descripción" })
                        if (format.isDefault) append("  ★")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    format.supplierName.ifEmpty { "Cualquier proveedor" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (format.supplierItemName.isNotBlank()) {
                    Text(
                        format.supplierItemName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${qtyLabel(format.unitsPerPackage)} $saleUnit · Q ${format.packageCost.fmt(2)} " +
                        "= Q ${format.costPerUnit.fmt(2)} c/u",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.Primary
                )
            }
            if (canSetDefault) {
                IconButton(onClick = onSetDefault, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Usar como costo de referencia",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Alta o edición de una presentación desde el formulario del producto (elige proveedor). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResalePurchaseFormatDialog(
    format: PurchaseFormat?,
    saleUnit: String,
    suppliers: List<Supplier>,
    onDismiss: () -> Unit,
    onConfirm: (PurchaseFormat) -> Unit
) {
    var supplierId by remember { mutableStateOf(format?.supplierId.orEmpty()) }
    var supplierName by remember { mutableStateOf(format?.supplierName.orEmpty()) }
    var supplierExpanded by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf(format?.description.orEmpty()) }
    var supplierItemName by remember { mutableStateOf(format?.supplierItemName.orEmpty()) }
    var unitsPerPackage by remember { mutableStateOf(format?.unitsPerPackage?.let { qtyLabel(it) } ?: "") }
    var packageCost by remember { mutableStateOf(format?.packageCost?.takeIf { it > 0 }?.fmt(2) ?: "") }

    val perPackage = unitsPerPackage.replace(",", ".").toDoubleOrNull() ?: 0.0
    val cost = packageCost.replace(",", ".").toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (format == null) "Nueva presentación" else "Editar presentación") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = supplierExpanded,
                    onExpandedChange = { supplierExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = supplierName.ifEmpty { "Cualquier proveedor" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Proveedor") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = supplierExpanded,
                        onDismissRequest = { supplierExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Cualquier proveedor") },
                            onClick = { supplierId = ""; supplierName = ""; supplierExpanded = false },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                        suppliers.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.company) },
                                onClick = { supplierId = s.id; supplierName = s.company; supplierExpanded = false },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
                ResaleFormatFields(
                    saleUnit = saleUnit,
                    description = description,
                    onDescriptionChange = { description = it },
                    unitsPerPackage = unitsPerPackage,
                    onUnitsPerPackageChange = { unitsPerPackage = it },
                    packageCost = packageCost,
                    onPackageCostChange = { packageCost = it },
                    supplierItemName = supplierItemName,
                    onSupplierItemNameChange = { supplierItemName = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        PurchaseFormat(
                            id = format?.id ?: newResaleFormatId(),
                            description = description.trim(),
                            unit = saleUnit,
                            supplierId = supplierId,
                            supplierName = supplierName,
                            unitsPerPackage = perPackage,
                            packageCost = cost,
                            isDefault = format?.isDefault ?: false,
                            supplierItemName = supplierItemName.trim()
                        )
                    )
                },
                enabled = description.isNotBlank() && perPackage > 0.0
            ) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Los tres datos de una presentación de reventa. Compartido con la captura de la factura,
 * donde el proveedor ya está elegido: las dos puntas tienen que pedir lo mismo con las mismas
 * palabras, o la caja de 24 se va a anotar distinto según desde dónde se dio de alta.
 */
@Composable
internal fun ResaleFormatFields(
    saleUnit: String,
    description: String,
    onDescriptionChange: (String) -> Unit,
    unitsPerPackage: String,
    onUnitsPerPackageChange: (String) -> Unit,
    packageCost: String,
    onPackageCostChange: (String) -> Unit,
    supplierItemName: String,
    onSupplierItemNameChange: (String) -> Unit
) {
    val perPackage = unitsPerPackage.replace(",", ".").toDoubleOrNull() ?: 0.0
    val cost = packageCost.replace(",", ".").toDoubleOrNull() ?: 0.0
    // Mismo campo que la presentación de un insumo: el nombre con que el proveedor lo pone en
    // su factura. Va aparte de "Presentación" porque en reventa ése es el EMPAQUE (caja, fardo)
    // y de su primera palabra sale "Precio por caja" en la captura.
    OutlinedTextField(
        value = supplierItemName,
        onValueChange = onSupplierItemNameChange,
        label = { Text("Descripción de la presentación") },
        placeholder = { Text("Ej: Coca Cola lata 354 ml") },
        supportingText = { Text("Como lo llama el proveedor en su factura") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text("Presentación") },
        placeholder = { Text("Ej: Caja de 24 latas") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = unitsPerPackage,
            onValueChange = { v -> onUnitsPerPackageChange(v.filter { it.isDigit() || it == '.' || it == ',' }) },
            label = { Text("¿Cuántas trae?") },
            suffix = { Text(saleUnit, style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = packageCost,
            onValueChange = { v -> onPackageCostChange(v.filter { it.isDigit() || it == '.' || it == ',' }) },
            label = { Text("Precio") },
            prefix = { Text("Q ") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
    if (perPackage > 0.0 && cost > 0.0) {
        Text(
            "Q ${cost.fmt(2)} la presentación → Q ${(cost / perPackage).fmt(2)} por $saleUnit",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = BrandColors.Primary
        )
    }
}

private fun qtyLabel(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.fmt(2)

internal fun newResaleFormatId(): String {
    val hex = "0123456789abcdef"
    return Random.nextBytes(12).joinToString("") { b ->
        hex[(b.toInt() shr 4) and 0xF].toString() + hex[b.toInt() and 0xF]
    }
}
