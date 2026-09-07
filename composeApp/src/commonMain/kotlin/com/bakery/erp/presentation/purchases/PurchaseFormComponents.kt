package com.bakery.erp.presentation.purchases

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseStatus
import com.bakery.erp.model.Supplier
import com.bakery.erp.model.TaxBreakdown
import com.bakery.erp.model.largestUnit
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney
import kotlin.math.abs

// ── Encabezado de la factura: número, fecha y crédito ────────────────────────

@Composable
internal fun InvoiceHeaderFields(
    invoiceNumber: String,
    onInvoiceNumberChange: (String) -> Unit,
    date: Long,
    onPickDate: () -> Unit,
    creditDaysStr: String,
    onCreditDaysChange: (String) -> Unit,
    dueDate: Long,
    supplierDefaultDays: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = invoiceNumber,
            onValueChange = onInvoiceNumberChange,
            label = { Text("Nº de factura (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPickDate,
                modifier = Modifier.weight(1.4f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(formatPurchaseDate(date), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = creditDaysStr,
                onValueChange = { v -> onCreditDaysChange(v.filter { it.isDigit() }.take(3)) },
                label = { Text("Crédito (días)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Text(
            text = when {
                dueDate > 0L -> "Vence el ${formatPurchaseDate(dueDate)}"
                supplierDefaultDays > 0 -> "Al contado — el proveedor tiene $supplierDefaultDays días configurados"
                else -> "Al contado — sin vencimiento que vigilar"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Barra de acciones del listado de insumos ─────────────────────────────────

@Composable
internal fun LineToolbar(
    lineCount: Int,
    showOnlySelected: Boolean,
    onToggleOnlySelected: () -> Unit,
    onAddIngredient: () -> Unit,
    /** `null` donde no hay cámara: el botón no se dibuja. */
    onScanBarcode: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // El catálogo del proveedor puede tener 40 renglones y la factura solo 3: sin este
        // filtro no hay forma de releer lo capturado sin recorrerlo entero.
        FilterChip(
            selected = showOnlySelected,
            onClick = onToggleOnlySelected,
            enabled = lineCount > 0,
            label = { Text(if (lineCount > 0) "Solo lo que llevo ($lineCount)" else "Solo lo que llevo") }
        )
        // Escanear encuentra el renglón sin teclear el nombre. Es un atajo: el código
        // también se puede escribir, y un lector físico lo teclea solo.
        if (onScanBarcode != null) {
            IconButton(onClick = onScanBarcode) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = "Escanear código de barras",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAddIngredient) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Agregar insumo externo")
        }
    }
}

// ── Vencimientos de los insumos perecederos capturados ───────────────────────

/**
 * Se pregunta acá y no en cada fila del catálogo a propósito: solo caducan unos pocos
 * insumos, y meterle un selector de fecha a los 40 renglones convertiría la pantalla más
 * sensible a la velocidad de captura en un formulario largo. Aparece cuando ya hay algo
 * perecedero anotado, que es cuando la pregunta tiene sentido.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpirySection(
    lines: List<PurchaseLine>,
    onLineChanged: (PurchaseLine) -> Unit
) {
    var pickingFor by remember { mutableStateOf<PurchaseLine?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = BrandColors.Sand),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Vencimientos",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = BrandColors.Primary
            )
            Text(
                "Estos insumos caducan. Anotá la fecha del empaque y el Home avisará antes.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            lines.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        line.ingredientName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = line.lotCode,
                        onValueChange = { onLineChanged(line.copy(lotCode = it)) },
                        label = { Text("Lote", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.width(110.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = { pickingFor = line },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                    ) {
                        Text(
                            if (line.expiryDate > 0L) formatPurchaseDate(line.expiryDate) else "Vence…",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

    pickingFor?.let { line ->
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = line.expiryDate.takeIf { it > 0L }
        )
        DatePickerDialog(
            onDismissRequest = { pickingFor = null },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { onLineChanged(line.copy(expiryDate = it)) }
                    pickingFor = null
                }) { Text("Aceptar") }
            },
            dismissButton = {
                Row {
                    if (line.expiryDate > 0L) {
                        TextButton(onClick = {
                            onLineChanged(line.copy(expiryDate = 0L))
                            pickingFor = null
                        }) { Text("Quitar") }
                    }
                    TextButton(onClick = { pickingFor = null }) { Text("Cancelar") }
                }
            }
        ) { DatePicker(state = dpState) }
    }
}

// ── Barra fija de totales ────────────────────────────────────────────────────

@Composable
internal fun PurchaseTotalsBar(
    lineCount: Int,
    breakdown: TaxBreakdown,
    applyTax: Boolean,
    onToggleTax: (Boolean) -> Unit,
    declaredTotalStr: String,
    onDeclaredTotalChange: (String) -> Unit,
    isLoading: Boolean,
    confirmLabel: String,
    onConfirm: () -> Unit
) {
    val declared = declaredTotalStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    val difference = if (declared > 0.0) breakdown.total - declared else null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Cotejo contra el papel. Es lo único que atrapa un cero de más ANTES de que la
            // cantidad entre al inventario y al Kardex, donde corregirla ya cuesta.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = declaredTotalStr,
                    onValueChange = onDeclaredTotalChange,
                    label = { Text("Total según la factura", style = MaterialTheme.typography.labelSmall) },
                    prefix = { Text("Q") },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                when {
                    difference == null -> Text(
                        "Opcional",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    abs(difference) <= 0.01 -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF1B5E20),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Cuadra",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                    }
                    else -> Text(
                        text = (if (difference > 0) "Sobra Q" else "Falta Q") + abs(difference).fmtMoney(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB3261E)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "TOTAL · $lineCount línea" + (if (lineCount != 1) "s" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Q${breakdown.total.fmtMoney()}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.Primary
                    )
                    if (applyTax && breakdown.tax > 0.0) {
                        Text(
                            "Base Q${breakdown.base.fmtMoney()} · IVA Q${breakdown.tax.fmtMoney()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Los precios se teclean CON IVA: el chip solo decide si se desglosa,
                    // nunca suma nada encima del total.
                    FilterChip(
                        selected = applyTax,
                        onClick = { onToggleTax(!applyTax) },
                        label = { Text("IVA incluido", style = MaterialTheme.typography.labelSmall) }
                    )
                    Button(
                        onClick = onConfirm,
                        enabled = lineCount > 0 && !isLoading,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(confirmLabel, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ── Alta de un insumo que el proveedor todavía no tiene asociado ─────────────

/**
 * Antes, si el proveedor te vendía algo nuevo había que salir del formulario, crearle la
 * presentación al insumo y volver a empezar la captura desde cero.
 *
 * La presentación SIEMPRE se guarda: si se lo compraste a este proveedor, eso es
 * exactamente lo que el modelo llama una presentación suya, y la próxima factura ya no
 * vuelve a preguntar.
 */
@Composable
internal fun AddSupplierIngredientDialog(
    supplierName: String,
    candidates: List<Ingredient>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Ingredient, String, String, Double, Double) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Ingredient?>(null) }
    var description by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var unitsPerPackage by remember { mutableStateOf("1") }
    var packageCost by remember { mutableStateOf("") }

    val shown = remember(candidates, search) {
        if (search.isBlank()) candidates.take(30)
        else candidates.filter { it.name.contains(search.trim(), ignoreCase = true) }.take(30)
    }
    val units = selected?.allowedUnits ?: emptyList()
    val cost = packageCost.replace(",", ".").toDoubleOrNull() ?: 0.0
    val perPackage = unitsPerPackage.replace(",", ".").toDoubleOrNull() ?: 0.0
    val canConfirm = selected != null && description.isNotBlank() && unit.isNotBlank() &&
        perPackage > 0.0 && cost > 0.0 && !isSaving

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(selected?.name ?: "Agregar insumo") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val current = selected
                if (current == null) {
                    Text(
                        "Elegí el insumo que te vendió $supplierName. Se guardará como " +
                            "presentación suya para las próximas facturas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Buscar insumo") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (shown.isEmpty()) {
                        Text(
                            "Todos los insumos activos ya están asociados a este proveedor, o " +
                                "ninguno coincide con la búsqueda.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    shown.forEach { ingredient ->
                        ListItem(
                            headlineContent = { Text(ingredient.name) },
                            supportingContent = if (ingredient.category.isNotEmpty()) {
                                { Text(ingredient.category, style = MaterialTheme.typography.labelSmall) }
                            } else null,
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                selected = ingredient
                                unit = largestUnit(ingredient.allowedUnits)
                                description = ""
                            }
                        )
                        HorizontalDivider()
                    }
                } else {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Presentación") },
                        placeholder = { Text("Ej: Saco de 50 lbs") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (units.size > 1) {
                        Text("Unidad de la presentación", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            units.forEach { u ->
                                FilterChip(
                                    selected = unit == u,
                                    onClick = { unit = u },
                                    label = { Text(u) }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = unitsPerPackage,
                            onValueChange = { v -> unitsPerPackage = v.filter { it.isDigit() || it == '.' } },
                            label = { Text("$unit por presentación") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = packageCost,
                            onValueChange = { v -> packageCost = v.filter { it.isDigit() || it == '.' } },
                            label = { Text("Precio") },
                            prefix = { Text("Q") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                    if (perPackage > 0.0 && cost > 0.0) {
                        Text(
                            "Q${(cost / perPackage).fmt(2)} por $unit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { selected = null }) { Text("Elegir otro insumo") }
                }
            }
        },
        confirmButton = {
            val current = selected
            if (current != null) {
                TextButton(
                    onClick = { onConfirm(current, description, unit, perPackage, cost) },
                    enabled = canConfirm
                ) { Text("Agregar") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ── Selector de proveedor ────────────────────────────────────────────────────

/**
 * Con 25 proveedores, una pila de tarjetas a todo el ancho obliga a hacer scroll para elegir
 * uno solo, y en tablet desperdicia media pantalla por renglón. Dos cambios:
 *
 * - **Buscador** en cuanto hay más de seis. Es la forma en que uno realmente busca a un
 *   proveedor: sabe el nombre, no la posición en la lista.
 * - **Rejilla en pantalla ancha**: fichas de ancho fijo que fluyen en varias columnas, así
 *   caben todos a la vista y elegir es un toque, no un scroll.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SupplierSelector(
    suppliers: List<Supplier>,
    onSelect: (Supplier) -> Unit,
    onNavigateToSuppliers: () -> Unit
) {
    if (suppliers.isEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = BrandColors.Sand),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Text(
                "No hay proveedores registrados. Registrá uno en el módulo Proveedores primero.",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onNavigateToSuppliers, modifier = Modifier.fillMaxWidth()) {
            Text("Ir a Proveedores")
        }
        return
    }

    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(suppliers, query) {
        val q = query.trim()
        if (q.isBlank()) suppliers
        else suppliers.filter {
            it.company.contains(q, ignoreCase = true) || it.salesperson.contains(q, ignoreCase = true)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (suppliers.size > 6) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar proveedor") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) ({
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar")
                    }
                }) else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (filtered.isEmpty()) {
            Text(
                "Ningún proveedor coincide con \"$query\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            BoxWithConstraints {
                if (maxWidth >= 600.dp) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filtered.forEach { supplier ->
                            SupplierTile(
                                supplier = supplier,
                                onClick = { onSelect(supplier) },
                                modifier = Modifier.width(220.dp)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filtered.forEach { supplier ->
                            SupplierTile(
                                supplier = supplier,
                                onClick = { onSelect(supplier) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        TextButton(onClick = onNavigateToSuppliers) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Registrar proveedor nuevo")
        }
    }
}

@Composable
private fun SupplierTile(
    supplier: Supplier,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                supplier.company,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val detail = listOfNotNull(
                supplier.salesperson.ifBlank { null },
                if (supplier.paymentTermsDays > 0) "${supplier.paymentTermsDays} días" else null
            ).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


// ── Aviso del modo edición ───────────────────────────────────────────────────

/**
 * Editar una compra ya recibida no reescribe el inventario: mueve la **diferencia** y deja un
 * movimiento de Kardex tipo AJUSTE. Decirlo acá evita la duda razonable de "¿si le cambio la
 * cantidad, me la va a sumar otra vez?".
 */
@Composable
internal fun EditPurchaseNotice(status: String, isLoading: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BrandColors.PrimaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                if (isLoading) "Cargando la compra…" else "Estás editando una compra registrada",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = BrandColors.Primary
            )
            Text(
                text = if (status == PurchaseStatus.PENDING_RECEIPT) {
                    "Todavía no entró al inventario, así que podés cambiar lo que sea sin " +
                        "consecuencias en el stock."
                } else {
                    "Ya entró al inventario: al guardar se ajusta solo la diferencia y queda " +
                        "un movimiento de corrección en el Kardex."
                },
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.Primary
            )
        }
    }
}
