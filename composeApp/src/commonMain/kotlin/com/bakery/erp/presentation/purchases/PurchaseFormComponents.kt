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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Switch
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
import com.bakery.erp.model.INGREDIENT_CATEGORIES
import com.bakery.erp.model.INGREDIENT_UNITS
import com.bakery.erp.model.IngredientUsage
import com.bakery.erp.model.SUPPLY_CATEGORIES
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Product
import com.bakery.erp.presentation.products.ResaleFormatFields
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.itemName
import com.bakery.erp.model.PurchaseStatus
import com.bakery.erp.model.Supplier
import com.bakery.erp.model.TaxBreakdown
import com.bakery.erp.model.largestUnit
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney
import kotlin.math.abs
import com.bakery.erp.ui.ProductImage
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextDecoration

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
    supplierDefaultDays: Int,
    /**
     * `false` cuando la compra se paga en el momento desde una gaveta abierta. No hay crédito
     * que ofrecer sobre algo que ya se pagó, y dejar el campo invita a teclear días que
     * generarían una cuenta por pagar que nadie debe.
     */
    showCredit: Boolean = true
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
            if (showCredit) {
                OutlinedTextField(
                    value = creditDaysStr,
                    onValueChange = { v -> onCreditDaysChange(v.filter { it.isDigit() }.take(3)) },
                    label = { Text("Crédito (días)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
        if (showCredit) {
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
    onExpiryChanged: (line: PurchaseLine, expiryDate: Long, lotCode: String) -> Unit
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
                "Estos productos caducan. Anotá el lote y la fecha que trae el empaque.",
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
                        line.itemName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = line.lotCode,
                        onValueChange = { onExpiryChanged(line, line.expiryDate, it) },
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
                    dpState.selectedDateMillis?.let { onExpiryChanged(line, it, line.lotCode) }
                    pickingFor = null
                }) { Text("Aceptar") }
            },
            dismissButton = {
                Row {
                    if (line.expiryDate > 0L) {
                        TextButton(onClick = {
                            onExpiryChanged(line, 0L, line.lotCode)
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
    onConfirm: () -> Unit,
    /**
     * Aviso que bloquea el guardado, o `null` si se puede guardar. Hoy es el tope de efectivo
     * de la gaveta cuando la factura se paga desde una caja abierta: no se puede pagar con
     * dinero que no está en el cajón.
     */
    blockingNotice: String? = null
) {
    val declared = declaredTotalStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    val difference = if (declared > 0.0) breakdown.total - declared else null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Column(
            // Desde Android 15 la app dibuja de borde a borde: sin esto la barra queda debajo
            // de los botones del teléfono y el botón de confirmar no se puede tocar.
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
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
                    modifier = Modifier.weight(1f).widthIn(min = 150.dp).heightIn(min = 56.dp),
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

            // Aviso de efectivo insuficiente en su propio renglón, a todo el ancho. Vivía dentro
            // de la columna del botón con `weight(1f)`: en una columna sin alto fijo eso estiraba
            // la barra hasta ocupar la pantalla entera, el formulario de arriba quedaba sin
            // espacio y todo parecía trabado justo cuando el monto pasaba lo que había en caja.
            if (blockingNotice != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        blockingNotice,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
                    // El desglose ES el control: tocarlo lo prende y lo apaga. Un chip aparte
                    // ocupaba 32dp de una barra fija que en un teléfono ya se come el pie de
                    // la pantalla, para un ajuste que se toca una vez por factura. Los precios
                    // se teclean CON IVA: esto solo decide si se desglosa, nunca suma nada
                    // encima del total — por eso dice "incluido" y no "aplicar".
                    Text(
                        text = if (applyTax && breakdown.tax > 0.0)
                            "IVA incluido · base Q${breakdown.base.fmtMoney()} · IVA Q${breakdown.tax.fmtMoney()}"
                        else "Desglosar IVA incluido",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (applyTax) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { onToggleTax(!applyTax) }
                    )
                }
                Button(
                    onClick = onConfirm,
                        enabled = lineCount > 0 && !isLoading && blockingNotice == null,
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

// ── Alta de un insumo que el proveedor todavía no tiene asociado ─────────────

/**
 * Antes, si el proveedor te vendía algo nuevo había que salir del formulario, crearle la
 * presentación al insumo y volver a empezar la captura desde cero.
 *
 * La presentación SIEMPRE se guarda: si se lo compraste a este proveedor, eso es
 * exactamente lo que el modelo llama una presentación suya, y la próxima factura ya no
 * vuelve a preguntar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddSupplierIngredientDialog(
    supplierName: String,
    candidates: List<Ingredient>,
    isSaving: Boolean,
    /** Si la empresa usa suministros de operación: decide si se pregunta para qué es. */
    usesSupplies: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Ingredient, String, String, Double, Double) -> Unit,
    /** nombre, uso, unidad base, categoría, lleva conteo, presentación, cuántas, precio. */
    onCreate: (String, IngredientUsage, String, String, Boolean, String, Double, Double) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Ingredient?>(null) }
    var description by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var unitsPerPackage by remember { mutableStateOf("1") }
    var packageCost by remember { mutableStateOf("") }

    // ── Alta de uno que no existe en ningún catálogo ──────────────────────
    // Es un tercer estado del diálogo, no un diálogo aparte: se llega buscando algo que no
    // está, y el paso siguiente —decir en qué presentación viene— es el mismo de siempre.
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newUsage by remember { mutableStateOf(IngredientUsage.PRODUCTION) }
    var newCategory by remember { mutableStateOf("Otros") }
    var newTracksStock by remember { mutableStateOf(true) }
    var categoryExpanded by remember { mutableStateOf(false) }

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
        title = {
            Text(
                when {
                    selected != null -> selected!!.name
                    creating -> "Nuevo insumo"
                    else -> "Agregar insumo"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val current = selected
                if (creating) {
                    Text(
                        "Se crea en el catálogo y queda asociado a $supplierName. Lo demás " +
                            "(stock mínimo, código de barras, si caduca) se completa después en " +
                            "su ficha.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nombre del insumo") },
                        placeholder = { Text("Como lo vas a buscar vos, no como lo factura él") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (usesSupplies) {
                        Text("¿Para qué es?", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IngredientUsage.entries.forEach { opt ->
                                FilterChip(
                                    selected = newUsage == opt,
                                    onClick = {
                                        newUsage = opt
                                        // Las categorías son dos listas distintas: dejar puesta
                                        // "Harinas" en un cloro guardaría un dato equivocado.
                                        newCategory = "Otros"
                                    },
                                    label = { Text(opt.label) }
                                )
                            }
                        }
                    }
                    // De la unidad cuelgan el stock, el Kardex y la conversión de la
                    // presentación. Es lo único de acá que no se puede corregir después sin
                    // que los movimientos ya escritos queden en otra unidad.
                    Text("¿En qué se mide?", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        INGREDIENT_UNITS.forEach { u ->
                            FilterChip(
                                selected = unit == u,
                                onClick = { unit = u },
                                label = { Text(u) }
                            )
                        }
                    }
                    val categoryOptions = if (newUsage == IngredientUsage.SUPPLY) SUPPLY_CATEGORIES
                                          else INGREDIENT_CATEGORIES
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Categoría") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            categoryOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = { newCategory = opt; categoryExpanded = false }
                                )
                            }
                        }
                    }
                    // Solo el suministro puede no contarse: de la materia prima sale el
                    // descuento de producción. Ver `Ingredient.tracksStock`.
                    if (newUsage == IngredientUsage.SUPPLY) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Llevar conteo de existencias", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (newTracksStock) "Entra al inventario de la sucursal"
                                    else "Solo gasto del mes, sin inventario",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(checked = newTracksStock, onCheckedChange = { newTracksStock = it })
                        }
                    }

                    HorizontalDivider()
                    Text("¿Cómo te lo trae?", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Presentación") },
                        placeholder = { Text("Ej: Saco de 50 lbs") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = unitsPerPackage,
                            onValueChange = { v -> unitsPerPackage = v.filter { it.isDigit() || it == '.' } },
                            label = { Text(if (unit.isBlank()) "¿Cuántas trae?" else "$unit por presentación") },
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
                    if (perPackage > 0.0 && cost > 0.0 && unit.isNotBlank()) {
                        Text(
                            "Q${(cost / perPackage).fmt(2)} por $unit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { creating = false; unit = "" }) { Text("Buscar uno existente") }
                } else if (current == null) {
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
                    // La salida del callejón: antes, lo que no estaba en el catálogo no tenía
                    // más destino que "Servicios y gastos", que lo guarda como texto suelto.
                    OutlinedButton(
                        onClick = {
                            newName = search.trim()
                            newCategory = "Otros"
                            unit = ""
                            description = ""
                            creating = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (search.isBlank()) "No está en el catálogo: crearlo"
                            else "Crear \"${search.trim()}\" como insumo nuevo"
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
                        label = { Text("Descripción de la presentación") },
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
            when {
                creating -> {
                    val canCreate = newName.isNotBlank() && unit.isNotBlank() &&
                        description.isNotBlank() && perPackage > 0.0 && cost > 0.0 && !isSaving
                    TextButton(
                        onClick = {
                            onCreate(
                                newName, newUsage, unit, newCategory, newTracksStock,
                                description, perPackage, cost
                            )
                        },
                        enabled = canCreate
                    ) { Text("Crear y agregar") }
                }
                current != null -> TextButton(
                    onClick = { onConfirm(current, description, unit, perPackage, cost) },
                    enabled = canConfirm
                ) { Text("Agregar") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ── Producto de reventa que el proveedor todavía no tiene (o sin presentación) ─

/**
 * Mismo criterio que [AddSupplierIngredientDialog]: llegó el de gaseosas con algo que no estaba
 * en su catálogo, o con un producto que hasta hoy se anotaba por unidad. Se elige el producto
 * (si no viene [preselected]) y se dice en qué empaque lo trae; la presentación queda guardada
 * a nombre de este proveedor para las próximas facturas.
 *
 * Se puede dar de alta uno que no existe. Esto estuvo en contra a propósito —"pide precio de
 * venta, listas y código de barras, y eso no se hace con el repartidor esperando"—, y lo que
 * estaba mal no era la premisa sino la conclusión: el problema era PEDIR todo eso. Acá se pide
 * nombre, unidad de venta y, si lo saben, el precio; el resto se completa en su ficha. La
 * alternativa real no era "crearlo con calma después", era mandarlo a "Servicios y gastos" y
 * perderlo como texto suelto.
 */
@Composable
internal fun AddSupplierResaleProductDialog(
    supplierName: String,
    candidates: List<Product>,
    preselected: Product?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Product, String, Double, Double, String) -> Unit,
    /** nombre, unidad de venta, precio de venta, presentación, cuántas trae, precio, nombre en factura. */
    onCreate: (String, String, Double, String, Double, Double, String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(preselected) }
    var description by remember { mutableStateOf("") }
    var supplierItemName by remember { mutableStateOf("") }
    var unitsPerPackage by remember { mutableStateOf("") }
    var packageCost by remember { mutableStateOf("") }

    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newSaleUnit by remember { mutableStateOf("unidad") }
    var newSalePrice by remember { mutableStateOf("") }

    val shown = remember(candidates, search) {
        if (search.isBlank()) candidates.take(30)
        else candidates.filter { it.name.contains(search.trim(), ignoreCase = true) }.take(30)
    }
    val perPackage = unitsPerPackage.replace(",", ".").toDoubleOrNull() ?: 0.0
    val cost = packageCost.replace(",", ".").toDoubleOrNull() ?: 0.0
    val canConfirm = selected != null && description.isNotBlank() && perPackage > 0.0 && cost > 0.0 && !isSaving

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    selected != null -> selected!!.name
                    creating -> "Nuevo producto de reventa"
                    else -> "Agregar producto de reventa"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val current = selected
                if (creating) {
                    Text(
                        "Se crea en el catálogo de productos, marcado como reventa, y queda " +
                            "asociado a $supplierName.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nombre del producto") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newSaleUnit,
                            onValueChange = { newSaleUnit = it },
                            label = { Text("Se vende por") },
                            placeholder = { Text("unidad, lata, bolsa") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newSalePrice,
                            onValueChange = { v -> newSalePrice = v.filter { it.isDigit() || it == '.' } },
                            label = { Text("Precio de venta") },
                            prefix = { Text("Q") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                    Text(
                        "El precio de venta puede quedar en blanco y ponerse después; hasta " +
                            "entonces el producto no se puede cobrar en el punto de venta. La " +
                            "factura sí entra al inventario con su costo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    HorizontalDivider()
                    Text("¿Cómo te lo trae?", style = MaterialTheme.typography.labelMedium)
                    ResaleFormatFields(
                        saleUnit = newSaleUnit.ifBlank { "unidad" },
                        description = description,
                        onDescriptionChange = { description = it },
                        unitsPerPackage = unitsPerPackage,
                        onUnitsPerPackageChange = { unitsPerPackage = it },
                        packageCost = packageCost,
                        onPackageCostChange = { packageCost = it },
                        supplierItemName = supplierItemName,
                        onSupplierItemNameChange = { supplierItemName = it }
                    )
                    TextButton(onClick = { creating = false }) { Text("Buscar uno existente") }
                } else if (current == null) {
                    Text(
                        "Elegí el producto que te vende $supplierName. Se guarda como presentación " +
                            "suya para las próximas facturas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                            "Ningún producto de reventa coincide, o todos ya están en el catálogo de " +
                                "este proveedor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            newName = search.trim()
                            newSaleUnit = "unidad"
                            newSalePrice = ""
                            description = ""
                            creating = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (search.isBlank()) "No está en el catálogo: crearlo"
                            else "Crear \"${search.trim()}\" como producto nuevo"
                        )
                    }
                    shown.forEach { product ->
                        ListItem(
                            headlineContent = { Text(product.name) },
                            leadingContent = product.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                {
                                    ProductImage(
                                        imageUrl = url,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                                    )
                                }
                            },
                            supportingContent = product.unit.takeIf { it.isNotBlank() }?.let { u ->
                                { Text("Se vende por $u", style = MaterialTheme.typography.labelSmall) }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { selected = product }
                        )
                        HorizontalDivider()
                    }
                } else {
                    Text(
                        "¿En qué presentación te lo trae $supplierName?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ResaleFormatFields(
                        saleUnit = current.unit.ifBlank { "unidad" },
                        description = description,
                        onDescriptionChange = { description = it },
                        unitsPerPackage = unitsPerPackage,
                        onUnitsPerPackageChange = { unitsPerPackage = it },
                        packageCost = packageCost,
                        onPackageCostChange = { packageCost = it },
                        supplierItemName = supplierItemName,
                        onSupplierItemNameChange = { supplierItemName = it }
                    )
                    if (preselected == null) {
                        TextButton(onClick = { selected = null }) { Text("Elegir otro producto") }
                    }
                }
            }
        },
        confirmButton = {
            val current = selected
            when {
                creating -> {
                    val canCreate = newName.isNotBlank() && description.isNotBlank() &&
                        perPackage > 0.0 && cost > 0.0 && !isSaving
                    TextButton(
                        onClick = {
                            onCreate(
                                newName,
                                newSaleUnit,
                                newSalePrice.replace(",", ".").toDoubleOrNull() ?: 0.0,
                                description, perPackage, cost, supplierItemName.trim()
                            )
                        },
                        enabled = canCreate
                    ) { Text("Crear y agregar") }
                }
                current != null -> TextButton(
                    onClick = { onConfirm(current, description, perPackage, cost, supplierItemName.trim()) },
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
