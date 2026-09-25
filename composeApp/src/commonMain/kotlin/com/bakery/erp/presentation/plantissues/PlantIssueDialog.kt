package com.bakery.erp.presentation.plantissues

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bakery.erp.model.Branch
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.PlantIssueReason
import com.bakery.erp.model.PurchaseFormat
import com.bakery.erp.model.User
import com.bakery.erp.model.formatStockDisplay
import com.bakery.erp.model.looseToBaseUnitOrNull
import com.bakery.erp.model.looseUnitOptions
import com.bakery.erp.model.packagesToBaseUnitOrNull
import com.bakery.erp.presentation.ingredients.IngredientPickerDialog
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.util.fmt
import kotlin.math.roundToInt

/**
 * "Anotar material": el registro que deja el panadero cuando usa un saco de harina para
 * enharinar la mesa o manteca para engrasar las bandejas. Ver [com.bakery.erp.model.PlantIssue].
 *
 * **Está diseñado para tres toques**, porque se usa con las manos en la masa y la
 * tablet lejos: insumo → presentación (el empaque predeterminado ya viene elegido) →
 * motivo. La cantidad arranca en 1 empaque con `−`/`+`, que es el caso normal.
 *
 * **Empaque completo y cantidad suelta son dos opciones parejas**, con el empaque
 * preseleccionado. El empaque es el default porque es el dato verificable sin balanza y
 * del que sale el ritmo de rotación; pero quien agarra 8 oz del bote ya abierto tiene
 * que poder anotarlo tal cual — obligarlo a declarar "1 bote" lo haría mentirle al
 * inventario. La unidad de la cantidad suelta sale del insumo (`looseUnitOptions`), no
 * de una lista fija: la manteca se agarra en onzas y la harina en libras.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlantIssueDialog(
    companyId: String,
    user: User,
    viewModel: PlantIssueViewModel,
    onDismiss: () -> Unit,
    /** Planta del lote abierto, si el diálogo se abrió desde la Lista de Producción. */
    presetBranchId: String = "",
    lotId: String = "",
    shiftId: String = "",
    shiftName: String = ""
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(companyId, presetBranchId) {
        viewModel.load(companyId, user, presetBranchId)
    }

    var selectedIngredient by remember { mutableStateOf<Ingredient?>(null) }
    var selectedFormat by remember { mutableStateOf<PurchaseFormat?>(null) }
    var packageQty by remember { mutableStateOf(1.0) }
    var looseMode by remember { mutableStateOf(false) }
    var looseText by remember { mutableStateOf("") }
    var looseUnit by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf(PlantIssueReason.DUSTING) }
    var notes by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    // Al cambiar de insumo se re-elige la presentación predeterminada y se vuelve al
    // modo empaque: arrastrar el formato del insumo anterior descontaría un "saco de
    // 100 lb" de un bote de manteca.
    LaunchedEffect(selectedIngredient?.id) {
        val ing = selectedIngredient
        selectedFormat = ing?.purchaseFormats?.firstOrNull { it.isDefault }
            ?: ing?.purchaseFormats?.firstOrNull()
        packageQty = 1.0
        // Sin presentaciones no hay empaque que ofrecer, así que se arranca en suelto.
        looseMode = ing != null && ing.purchaseFormats.isEmpty()
        looseText = ""
        // La unidad arranca en la del stock: es la que la mayoría va a querer, y
        // arrastrar la del insumo anterior descontaría onzas donde se pensaba en libras.
        looseUnit = ing?.effectiveBaseUnit ?: ""
    }

    val branch = state.selectedBranch
    val ingredient = selectedIngredient
    val stock = ingredient?.let { viewModel.stockOf(it.id) } ?: 0.0
    val looseValue = looseText.replace(',', '.').toDoubleOrNull()
    val hasFormats = ingredient != null && ingredient.purchaseFormats.isNotEmpty()
    val looseUnitChoices = remember(ingredient?.id) { ingredient?.looseUnitOptions() ?: emptyList() }

    val baseQuantity: Double? = when {
        ingredient == null -> null
        // La cantidad suelta se convierte desde la unidad tecleada, no se asume que ya
        // viene en la del stock: sin esto, 8 oz de manteca descontaban 8 lb.
        looseMode -> looseValue?.let { looseToBaseUnitOrNull(it, looseUnit, ingredient) }
        selectedFormat != null && packageQty > 0.0 ->
            packagesToBaseUnitOrNull(packageQty, selectedFormat!!, ingredient)
        else -> null
    }
    val exceedsStock = baseQuantity != null && baseQuantity > stock + 0.0001
    val needsNote = reason == PlantIssueReason.OTHER && notes.isBlank()
    val canSave = branch != null && ingredient != null && baseQuantity != null &&
        !needsNote && !state.isSaving

    if (showPicker) {
        IngredientPickerDialog(
            // Se ofrecen materia prima Y suministros: el panadero también saca cloro
            // para limpiar la mesa, y mandarlo a otra pantalla para eso significa que
            // no lo va a anotar.
            ingredients = state.ingredients,
            onPick = { selectedIngredient = it; showPicker = false },
            onDismiss = { showPicker = false },
            title = "¿Qué material usaste?"
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Material usado en planta",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Lo que se gasta trabajando y no sale de una receta: harina para la " +
                        "mesa, manteca para las bandejas, espolvoreo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.MutedSlate
                )

                // ── Sucursal ───────────────────────────────────────────────────
                // Con una sola planta accesible no es una pregunta: se muestra como
                // hecho. Con varias hay que elegir, porque el descuento le pega a una
                // bodega concreta.
                if (state.accessibleBranches.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sucursal", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.accessibleBranches.forEach { b ->
                                FilterChip(
                                    selected = b.id == branch?.id,
                                    onClick = { viewModel.selectBranch(companyId, b) },
                                    label = { Text(b.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandColors.PrimaryContainer,
                                        selectedLabelColor = BrandColors.Primary
                                    )
                                )
                            }
                        }
                    }
                } else if (branch != null) {
                    Text(
                        "Sale del inventario de: ${branch.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                HorizontalDivider()

                // ── Insumo ─────────────────────────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (ingredient == null) BrandColors.Sand else BrandColors.PrimaryContainer,
                    modifier = Modifier.fillMaxWidth().clickable { showPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = BrandColors.Primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                ingredient?.name ?: "Elegir insumo",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (ingredient != null) {
                                Text(
                                    "Hay: ${formatStockDisplay(stock, ingredient)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrandColors.MutedSlate
                                )
                            }
                        }
                        TextButton(onClick = { showPicker = true }) {
                            Text(if (ingredient == null) "Elegir" else "Cambiar")
                        }
                    }
                }

                if (ingredient != null) {
                    // ── La guarda del saco abierto ─────────────────────────────
                    // El vale descuenta el empaque completo el día que se abre, pero
                    // el saco dura tres en la mesa. Decir cuándo se sacó el anterior
                    // es lo único que evita que se anote dos veces el mismo saco, y
                    // no cuesta un toque.
                    val days = viewModel.daysSinceLast(ingredient.id)
                    val last = viewModel.lastIssueFor(ingredient.id)
                    if (days != null && last != null) {
                        LastIssueNotice(
                            days = days,
                            detail = buildString {
                                append(last.displayQuantity)
                                if (last.issuedByUserName.isNotBlank()) append(" · ").append(last.issuedByUserName)
                                append(" · ").append(last.reasonLabel)
                            },
                            // "Puede que siga abierto" solo tiene sentido si lo último
                            // que salió fue un empaque. Sobre las onzas que alguien
                            // agarró del bote la advertencia no aplica, y una alerta
                            // que suena cuando no corresponde deja de avisar.
                            warnDuplicate = last.isFullPackage && !looseMode
                        )
                    }

                    // ── Empaque completo o cantidad suelta ─────────────────────
                    // Son dos opciones parejas, no una escondida detrás de la otra:
                    // quien agarra 8 oz de manteca del bote abierto tiene que poder
                    // anotarlo tal cual. El empaque sigue siendo el DEFAULT porque es
                    // el dato verificable sin balanza y del que sale el ritmo de
                    // rotación; obligar a todos a declarar empaques haría que la
                    // mitad de los vales mientan, y eso es peor que la estimación.
                    if (hasFormats) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "¿Cuánto usaste?",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !looseMode,
                                    onClick = { looseMode = false },
                                    label = { Text("Empaque completo") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandColors.PrimaryContainer,
                                        selectedLabelColor = BrandColors.Primary
                                    )
                                )
                                FilterChip(
                                    selected = looseMode,
                                    onClick = { looseMode = true },
                                    label = { Text("Cantidad suelta") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandColors.PrimaryContainer,
                                        selectedLabelColor = BrandColors.Primary
                                    )
                                )
                            }
                        }
                    }

                    if (!looseMode && hasFormats) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ingredient.purchaseFormats.forEach { fmt ->
                                FormatOptionRow(
                                    format = fmt,
                                    selected = selectedFormat?.id == fmt.id,
                                    convertible = packagesToBaseUnitOrNull(1.0, fmt, ingredient) != null,
                                    baseUnit = ingredient.effectiveBaseUnit,
                                    perPackage = packagesToBaseUnitOrNull(1.0, fmt, ingredient),
                                    onClick = { selectedFormat = fmt }
                                )
                            }
                            if (selectedFormat != null) {
                                PackageStepper(
                                    quantity = packageQty,
                                    formatDescription = selectedFormat!!.description,
                                    onChange = { packageQty = it }
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!hasFormats) {
                                // Sin presentaciones cargadas no hay empaque que ofrecer:
                                // se dice, en vez de mostrar una sección vacía que se lee
                                // como un error de la app.
                                Text(
                                    "${ingredient.name} no tiene presentaciones cargadas — anotá la cantidad.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrandColors.MutedSlate
                                )
                            }
                            OutlinedTextField(
                                value = looseText,
                                onValueChange = { looseText = it },
                                label = { Text("Cantidad") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Las unidades salen del insumo, no de una lista fija: la
                            // manteca se agarra en onzas y la harina en libras. Una
                            // unidad que no convierte a la del stock no se ofrece —
                            // ofrecerla y rechazar el vale después es peor.
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                looseUnitChoices.forEach { u ->
                                    FilterChip(
                                        selected = looseUnit == u,
                                        onClick = { looseUnit = u },
                                        label = { Text(u) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BrandColors.PrimaryContainer,
                                            selectedLabelColor = BrandColors.Primary
                                        )
                                    )
                                }
                            }
                            // Equivalencia en vivo: quien teclea 8 oz ve que son 0.5 lb
                            // antes de confirmar, que es la unidad en la que después va
                            // a leer su inventario.
                            if (baseQuantity != null && looseUnit != ingredient.effectiveBaseUnit) {
                                Text(
                                    "≈ ${baseQuantity.fmt(2)} ${ingredient.effectiveBaseUnit}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandColors.MutedSlate
                                )
                            }
                        }
                    }

                    // ── Motivo ─────────────────────────────────────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("¿Para qué?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PLANT_ISSUE_REASONS.forEach { option ->
                                FilterChip(
                                    selected = reason == option,
                                    onClick = { reason = option },
                                    label = { Text(PlantIssueReason.shortLabel(option)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandColors.PrimaryContainer,
                                        selectedLabelColor = BrandColors.Primary
                                    )
                                )
                            }
                        }
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = {
                                Text(if (reason == PlantIssueReason.OTHER) "Detalle (obligatorio)" else "Detalle (opcional)")
                            },
                            placeholder = { Text("Ej. para la sheca") },
                            isError = needsNote && notes.isNotEmpty(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (baseQuantity != null) {
                        SummaryLine(
                            text = "Se descuentan ${baseQuantity.fmt(1)} ${ingredient.effectiveBaseUnit} " +
                                "de ${branch?.name ?: "la planta"}",
                            warning = exceedsStock,
                            warningText = if (exceedsStock)
                                "Es más de lo que el sistema tiene registrado " +
                                    "(${formatStockDisplay(stock, ingredient)}). Se registra igual y el " +
                                    "saldo queda en cero — revisá el conteo después."
                            else null
                        )
                    }
                }

                state.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val b = branch ?: return@Button
                            val ing = ingredient ?: return@Button
                            viewModel.issueMaterial(
                                companyId = companyId,
                                branch = b,
                                ingredient = ing,
                                format = if (looseMode) null else selectedFormat,
                                quantity = if (looseMode) (looseValue ?: 0.0) else packageQty,
                                looseUnit = if (looseMode) looseUnit else "",
                                reason = reason,
                                notes = notes,
                                user = user,
                                lotId = lotId,
                                shiftId = shiftId,
                                shiftName = shiftName
                            )
                            onDismiss()
                        },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Anotar")
                    }
                }
            }
        }
    }
}

/**
 * "Último saco: hace 2 días". Va en `AccentSoft` con tinta `Primary` y no en naranja
 * pleno de texto: la regla de la paleta es que el naranja claro nunca es texto.
 */
@Composable
private fun LastIssueNotice(days: Double, detail: String, warnDuplicate: Boolean) {
    val whenText = when {
        days < 0.04 -> "hace un rato"
        days < 1.0 -> "hoy"
        days < 2.0 -> "ayer"
        else -> "hace ${days.roundToInt()} días"
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BrandColors.AccentSoft,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = BrandColors.AccentIcon,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Última salida: $whenText",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandColors.Primary
                )
                Text(detail, style = MaterialTheme.typography.bodySmall, color = BrandColors.Primary)
                if (warnDuplicate) {
                    Text(
                        "Si ese empaque sigue abierto, no lo anotes otra vez.",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.Primary
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatOptionRow(
    format: PurchaseFormat,
    selected: Boolean,
    convertible: Boolean,
    baseUnit: String,
    perPackage: Double?,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) BrandColors.PrimaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) BrandColors.Primary else BrandColors.Outline,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = convertible, onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    format.description.ifBlank { "Empaque" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (convertible) BrandColors.TextSlate else BrandColors.MutedSlate
                )
                Text(
                    if (convertible && perPackage != null)
                        "1 empaque = ${perPackage.fmt(1)} $baseUnit"
                    // Un formato que no se puede convertir a la unidad del stock no se
                    // puede descontar sin inventar un número: se muestra deshabilitado
                    // diciendo por qué, en vez de desaparecer sin explicación.
                    else "No se puede convertir a \"$baseUnit\" — revisá la presentación en Insumos",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.MutedSlate
                )
            }
            if (format.isDefault) BrandChip("El de siempre", ChipTone.INFO)
        }
    }
}

/**
 * Cantidad en EMPAQUES con `−`/`+`. Arranca en 1, que es el caso normal — un saco.
 * Teclear un número aquí sería un teclado abierto por cada vale, sobre una tablet
 * con harina.
 */
@Composable
private fun PackageStepper(quantity: Double, formatDescription: String, onChange: (Double) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = { onChange((quantity - 1).coerceAtLeast(1.0)) },
            enabled = quantity > 1.0
        ) { Icon(Icons.Default.Remove, contentDescription = "Menos") }
        Box(
            modifier = Modifier
                .background(BrandColors.Sand, RoundedCornerShape(12.dp))
                .heightIn(min = 56.dp)
                .width(84.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                quantity.roundToInt().toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = BrandColors.Primary
            )
        }
        IconButton(onClick = { onChange(quantity + 1) }) {
            Icon(Icons.Default.Add, contentDescription = "Más")
        }
        Text(
            if (quantity > 1.0) "× $formatDescription" else formatDescription,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryLine(text: String, warning: Boolean, warningText: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = BrandColors.Primary
        )
        if (warning && warningText != null) {
            Text(warningText, style = MaterialTheme.typography.labelSmall, color = BrandColors.AccentDeep)
        }
    }
}
