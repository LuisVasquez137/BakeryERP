package com.bakery.erp.presentation.ingredients

import com.bakery.erp.util.fmt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.INGREDIENT_CATEGORIES
import com.bakery.erp.model.IngredientUsage
import com.bakery.erp.model.SUPPLY_CATEGORIES
import com.bakery.erp.ui.BarcodeField
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.PurchaseFormat
import com.bakery.erp.model.Supplier
import com.bakery.erp.model.countUnitsPer
import com.bakery.erp.model.largestUnit
import com.bakery.erp.util.gramsToOz
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.products.ResalePurchaseFormatDialog
import com.bakery.erp.presentation.suppliers.SupplierViewModel
import com.bakery.erp.ui.LoadingOverlay
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientFormScreen(
    ingredientId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToSuppliers: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: IngredientViewModel,
    supplierViewModel: SupplierViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val supplierState by supplierViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    val existing = remember(ingredientId, state.ingredientList) {
        if (ingredientId != null) state.ingredientList.find { it.id == ingredientId }
            ?: state.selectedIngredient?.takeIf { it.id == ingredientId }
        else null
    }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) supplierViewModel.loadSuppliers(companyId)
    }

    // ── Form state ────────────────────────────────────────────────────────────
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "") }
    // Materia prima o suministro de operación. El insumo se almacena igual en los dos
    // casos; lo que cambia es dónde se ofrece (las recetas solo ven materia prima).
    var usage by rememberSaveable { mutableStateOf(existing?.usage ?: IngredientUsage.PRODUCTION) }
    var barcode by rememberSaveable { mutableStateOf(existing?.barcode ?: "") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var allowedUnits by remember { mutableStateOf(existing?.allowedUnits ?: emptyList<String>()) }
    // Solo suministros: en qué se cuenta. Texto libre, igual que "Se vende por" en un
    // producto de reventa — no la rejilla de nueve chips de la materia prima.
    var supplyUnit by rememberSaveable {
        mutableStateOf(existing?.allowedUnits?.firstOrNull().orEmpty())
    }
    // Peso de UNA unidad, en gramos. Solo se pide a insumos que se cuentan (huevo).
    var unitWeightGrams by rememberSaveable {
        mutableStateOf(
            existing?.unitWeightGrams?.takeIf { it > 0.0 }
                ?.let { it.fmt(2).trimEnd('0').trimEnd('.') } ?: ""
        )
    }
    var stockAlert by rememberSaveable { mutableStateOf(existing?.stockAlert?.toString() ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var active by rememberSaveable { mutableStateOf(existing?.active ?: true) }
    var isPerishable by rememberSaveable { mutableStateOf(existing?.isPerishable ?: false) }
    // Solo aplica a suministros. La materia prima siempre lleva conteo: de ese stock sale el
    // descuento de producción, y sin él la receta no tendría de dónde restar.
    var tracksStock by rememberSaveable { mutableStateOf(existing?.tracksStock ?: true) }
    var purchaseFormats by remember { mutableStateOf(existing?.purchaseFormats ?: emptyList<PurchaseFormat>()) }

    var showFormatDialog by remember { mutableStateOf(false) }
    var editingFormat by remember { mutableStateOf<PurchaseFormat?>(null) }
    var nameError by remember { mutableStateOf(false) }
    var allowedUnitsError by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // Lo que se cuenta cuando el suministro no declara unidad: ya no elige familia de
    // medida, y "vacío" tiene que significar algo concreto en el stock y en el Kardex.
    val effectiveSupplyUnit = supplyUnit.trim().ifBlank { "unidad" }

    // ── Diálogo de formato de compra ──────────────────────────────────────────
    val onFormatConfirmed: (PurchaseFormat) -> Unit = { newFormat ->
        purchaseFormats = if (newFormat.isDefault) {
            // Solo uno puede ser predeterminado
            purchaseFormats
                .filter { it.id != newFormat.id }
                .map { it.copy(isDefault = false) } + newFormat
        } else {
            purchaseFormats.filter { it.id != newFormat.id } + newFormat
        }
        // Si solo hay uno, siempre es default
        if (purchaseFormats.size == 1) {
            purchaseFormats = listOf(purchaseFormats.first().copy(isDefault = true))
        }
        showFormatDialog = false
        editingFormat = null
    }

    if (showFormatDialog) {
        // Un suministro se captura igual que un producto de reventa: proveedor, cómo lo
        // llama él en su factura, el empaque, cuántas trae y el precio. Se reusa EL MISMO
        // diálogo y no una copia con las mismas etiquetas, porque las dos puntas tienen que
        // pedir lo mismo con las mismas palabras — es lo que ya dice el KDoc de
        // `ResaleFormatFields`, y dos formularios gemelos se despegan en cuanto uno cambie.
        if (usage == IngredientUsage.SUPPLY) {
            ResalePurchaseFormatDialog(
                format = editingFormat,
                saleUnit = effectiveSupplyUnit,
                suppliers = supplierState.supplierList,
                onDismiss = { showFormatDialog = false; editingFormat = null },
                onConfirm = onFormatConfirmed
            )
        } else {
            PurchaseFormatDialog(
                format = editingFormat,
                allowedUnits = allowedUnits,
                suppliers = supplierState.supplierList,
                onNavigateToSuppliers = {
                    showFormatDialog = false
                    editingFormat = null
                    onNavigateToSuppliers()
                },
                onConfirm = onFormatConfirmed,
                onDismiss = { showFormatDialog = false; editingFormat = null }
            )
        }
    }

    val categoryOptions = if (usage == IngredientUsage.SUPPLY) SUPPLY_CATEGORIES
                          else INGREDIENT_CATEGORIES

    fun buildIngredient() = Ingredient(
        id = existing?.id ?: "",
        name = name.trim().replaceFirstChar { it.uppercase() },
        category = category,
        usage = usage,
        barcode = barcode.trim(),
        // El suministro guarda UNA unidad: la que se cuenta. La materia prima conserva la
        // lista, que es la que después se le ofrece al escribirlo en una receta.
        allowedUnits = if (usage == IngredientUsage.SUPPLY) listOf(effectiveSupplyUnit)
                       else allowedUnits,
        unitWeightGrams = if (allowedUnits.any { countUnitsPer(it) != null })
            unitWeightGrams.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
        else 0.0,
        purchaseFormats = purchaseFormats,
        stockAlert = stockAlert.toDoubleOrNull(),
        isPerishable = isPerishable,
        // Un suministro sin conteo no puede quedar con alerta de mínimo: no hay stock contra
        // el cual dispararla, y guardada quedaría esperando un evento que nunca llega.
        tracksStock = usage != IngredientUsage.SUPPLY || tracksStock,
        notes = notes.trim().ifEmpty { null },
        active = active
    )

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        // El formulario habla el idioma de lo que se está dando de alta: un
                        // suministro no es un ingrediente y llamarlo así hace dudar de si se
                        // eligió bien el tipo. La palabra cambia al tocar los chips de arriba.
                        buildString {
                            append(if (ingredientId == null) "Nuevo " else "Editar ")
                            append(if (usage == IngredientUsage.SUPPLY) "suministro" else "insumo")
                        }.replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // ── General ───────────────────────────────────────────────────
                SectionHeader("General")

                // Materia prima vs. suministro. Va PRIMERO porque decide las categorías
                // que se ofrecen abajo y si el insumo aparecerá o no en las recetas.
                Column(Modifier.fillMaxWidth()) {
                    Text("¿Para qué es?", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IngredientUsage.entries.forEach { opt ->
                            FilterChip(
                                selected = usage == opt,
                                onClick = {
                                    if (usage != opt) {
                                        usage = opt
                                        // La categoría anterior pertenece al otro catálogo:
                                        // dejarla puesta guardaría "Harinas" en un cloro.
                                        category = ""
                                    }
                                },
                                label = { Text(opt.label) }
                            )
                        }
                    }
                    Text(
                        text = if (usage == IngredientUsage.PRODUCTION)
                            "Entra a recetas y se descuenta al producir."
                        else "Se cuenta y se repone, pero no entra a recetas. Su compra se registra como gasto del mes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Nombre *") },
                    placeholder = { Text("Ej: Harina de trigo") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError,
                    supportingText = if (nameError) ({ Text("El nombre es requerido") }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        // Las categorías siguen al tipo de uso: "Harinas" no le dice nada
                        // al cloro, y "Limpieza" no le dice nada a la masa.
                        categoryOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt) },
                                onClick = { category = opt; categoryExpanded = false },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                BarcodeField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    supportingText = "Opcional. Sirve para encontrarlo al capturar una factura."
                )

                // ── En qué se cuenta ─────────────────────────────────────────
                // Un suministro se cuenta en una sola unidad —la bolsa, el rollo, el
                // galón— y todo lo demás lo dice su presentación de compra ("Caja de 24").
                // Los nueve chips le hacían elegir una familia de medida que después nadie
                // lee (un suministro nunca entra a una receta) y la presentación volvía a
                // preguntar unidad, cantidad y descripción: el mismo dato, dos veces. Es el
                // campo "Se vende por" de un producto de reventa, con las mismas palabras.
                if (usage == IngredientUsage.SUPPLY) {
                    OutlinedTextField(
                        value = supplyUnit,
                        onValueChange = { supplyUnit = it },
                        label = { Text("Se cuenta por") },
                        placeholder = { Text("unidad, bolsa, rollo, galón…") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            Text(
                                "Es la unidad en que se cuenta el inventario y en que se arman " +
                                    "las presentaciones. Vacío = se cuenta por unidad."
                            )
                        }
                    )
                } else Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Unidades de medida *",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (allowedUnitsError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        // Un suministro NUNCA entra a una receta (los pickers filtran con
                        // `usableInRecipes`), así que prometerle que estas unidades van a
                        // aparecer ahí es mentira — y hace dudar de si se eligió bien el tipo.
                        text = if (usage == IngredientUsage.SUPPLY)
                            "Seleccioná en qué unidades lo comprás y lo contás."
                        else "Seleccioná en qué unidades se puede medir este insumo. " +
                            "Las mismas aparecerán al crear recetas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    // Grupo: Peso
                    Text("Peso", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("gr", "oz", "lb", "kg").forEach { unit ->
                            FilterChip(
                                selected = unit in allowedUnits,
                                onClick = {
                                    allowedUnits = if (unit in allowedUnits) allowedUnits - unit
                                              else allowedUnits + unit
                                    allowedUnitsError = false
                                },
                                label = { Text(unit) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // Grupo: Volumen
                    Text("Volumen", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ml", "lt").forEach { unit ->
                            FilterChip(
                                selected = unit in allowedUnits,
                                onClick = {
                                    allowedUnits = if (unit in allowedUnits) allowedUnits - unit
                                              else allowedUnits + unit
                                    allowedUnitsError = false
                                },
                                label = { Text(unit) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // Grupo: Conteo
                    Text("Conteo", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("unidad", "docena", "saco").forEach { unit ->
                            FilterChip(
                                selected = unit in allowedUnits,
                                onClick = {
                                    allowedUnits = if (unit in allowedUnits) allowedUnits - unit
                                              else allowedUnits + unit
                                    allowedUnitsError = false
                                },
                                label = { Text(unit) }
                            )
                        }
                    }
                    if (allowedUnitsError) {
                        Text(
                            text = "Seleccioná al menos una unidad",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // ── Peso por unidad (solo insumos que se cuentan) ─────────────
                // El stock del huevo se lleva en unidades, pero la receta lo pide
                // en peso. Este es el único puente entre ambos: sin él la receta
                // no puede calcular el % del panadero ni descontar bien el Kardex.
                // Solo materia prima: el peso de una unidad existe para que la receta pueda
                // calcular el % del panadero, que es una razón entre PESOS. A una bolsa no se
                // le pesa nada, y el campo solo invitaba a llenar un dato que nadie lee.
                if (usage != IngredientUsage.SUPPLY && allowedUnits.any { countUnitsPer(it) != null }) {
                    OutlinedTextField(
                        value = unitWeightGrams,
                        onValueChange = { txt ->
                            if (txt.isEmpty() || txt.matches(Regex("^\\d*[.,]?\\d*$"))) {
                                unitWeightGrams = txt
                            }
                        },
                        label = { Text("Peso de una unidad (g)") },
                        placeholder = { Text("Ej. 50 (un huevo)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            val g = unitWeightGrams.replace(',', '.').toDoubleOrNull()
                            if (g != null && g > 0.0) {
                                Text(
                                    "= ${g.gramsToOz().fmt(2)} oz por unidad · " +
                                        "una docena pesa ${(g * 12).gramsToOz().fmt(2)} oz"
                                )
                            } else {
                                Text(
                                    "Necesario para usar este insumo en recetas: el % del " +
                                        "panadero se calcula por peso. El stock sigue en unidades."
                                )
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Activo", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = active, onCheckedChange = { active = it })
                }

                SectionDivider()

                // ── Formatos de compra ─────────────────────────────────────────
                SectionHeader("Presentaciones de Compra")

                // Explicación
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp).padding(top = 1.dp)
                    )
                    Text(
                        text = if (usage == IngredientUsage.SUPPLY)
                            "Cómo te lo vende cada proveedor: la caja, el fardo, el bulto. " +
                                "Agregá una por cada uno que te lo trae. Al capturar la factura " +
                                "anotás \"1 caja\" y entran las unidades que trae."
                        else "Registra cómo comprás este ingrediente: en qué presentación (saco, caja, unidad), con qué unidad y a qué precio. El sistema calcula automáticamente el costo por unidad.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Spacer(Modifier.height(2.dp))

                if (purchaseFormats.isEmpty()) {
                    Text(
                        text = "Todavía no hay presentaciones registradas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    purchaseFormats.sortedByDescending { it.isDefault }.forEach { format ->
                        PurchaseFormatItem(
                            format = format,
                            isOnlyOne = purchaseFormats.size == 1,
                            onEdit = { editingFormat = format; showFormatDialog = true },
                            onDelete = {
                                val remaining = purchaseFormats.filter { it.id != format.id }
                                // Si se eliminó el default y quedan otros, el primero pasa a ser default
                                purchaseFormats = if (format.isDefault && remaining.isNotEmpty()) {
                                    listOf(remaining.first().copy(isDefault = true)) + remaining.drop(1)
                                } else {
                                    remaining
                                }
                            },
                            onSetDefault = {
                                purchaseFormats = purchaseFormats.map {
                                    it.copy(isDefault = it.id == format.id)
                                }
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }

                OutlinedButton(
                    onClick = { editingFormat = null; showFormatDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar presentación de compra")
                }

                SectionDivider()

                // ── Control de stock ───────────────────────────────────────────
                SectionHeader("Control de Stock")

                // El suministro que no se cuenta: la bolsa, la bandeja, el vaso desechable.
                // Se compran por fardo y se gastan de a uno con cada venta, y ese consumo no
                // lo va a registrar nadie — un stock que solo sube miente, y la alerta de
                // mínimo nunca suena. Sigue siendo un ítem del catálogo del proveedor: se
                // captura como fila en su factura, con su presentación y su precio.
                if (usage == IngredientUsage.SUPPLY) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Llevar conteo de existencias", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (tracksStock)
                                    "Su compra entra al inventario de la sucursal y lleva Kardex."
                                else "Solo gasto: se registra la compra pero no se cuentan existencias. " +
                                    "Para lo que se consume sin que nadie lo apunte (bolsas, bandejas).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = tracksStock, onCheckedChange = { tracksStock = it })
                    }
                }

                // Sin conteo no hay contra qué avisar, y un campo que no hace nada se llena
                // igual y después se espera la alerta que nunca llega.
                if (usage != IngredientUsage.SUPPLY || tracksStock) OutlinedTextField(
                    value = stockAlert,
                    onValueChange = { stockAlert = it },
                    label = { Text("Alerta de stock mínimo") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    suffix = {
                        val defaultFormat = purchaseFormats.firstOrNull { it.isDefault }
                            ?: purchaseFormats.firstOrNull()
                        val label = if (defaultFormat != null)
                            defaultFormat.description
                        else
                            largestUnit(allowedUnits)
                        if (label.isNotEmpty()) Text(label)
                    },
                    supportingText = { Text("Recibirás una alerta cuando el stock baje de este nivel (en presentaciones o unidad mayor)") }
                )

                // El campo de vencimiento en el formulario de compra solo aparece para estos:
                // pedírselo a la harina a granel o a la sal sería ruido donde la captura debe
                // ser rápida.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Caduca", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Al comprarlo se pedirá la fecha de vencimiento y el Home avisará antes de que caduque",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isPerishable, onCheckedChange = { isPerishable = it })
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(Modifier.padding(8.dp))

                Button(
                    onClick = {
                        var hasError = false
                        if (name.isBlank()) { nameError = true; hasError = true }
                        // El suministro no elige unidades: se cuenta por lo que diga
                        // "Se cuenta por", y vacío cae a "unidad".
                        if (usage != IngredientUsage.SUPPLY && allowedUnits.isEmpty()) {
                            allowedUnitsError = true; hasError = true
                        }
                        if (hasError) return@Button
                        viewModel.saveIngredient(companyId, buildIngredient())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (usage == IngredientUsage.SUPPLY) "Guardar suministro"
                            else "Guardar insumo",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.padding(16.dp))
            }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }
}

// ── Item de formato en la lista del formulario ────────────────────────────────

@Composable
private fun PurchaseFormatItem(
    format: PurchaseFormat,
    isOnlyOne: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (format.isDefault)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = format.description.ifEmpty { "Sin descripción" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (format.isDefault) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Predeterminado",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "predeterminado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (format.supplierName.isNotEmpty()) {
                        Text(
                            text = format.supplierName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Cómo le llama ESE proveedor en su factura. Es el dato con que se
                    // encuentra el renglón leyendo el papel, así que tiene que verse.
                    if (format.supplierItemName.isNotEmpty()) {
                        Text(
                            text = format.supplierItemName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                }

                // Acciones
                if (!format.isDefault && !isOnlyOne) {
                    IconButton(onClick = onSetDefault, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Marcar como predeterminado",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(6.dp))

            // Desglose de precio
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Paquete: ${format.unitsPerPackage.toInt().let { if (format.unitsPerPackage == it.toDouble()) it.toString() else format.unitsPerPackage.fmt(2) }} ${format.unit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Costo del paquete: Q ${format.packageCost.fmt(2)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "= Q ${format.costPerUnit.fmt(2)} / ${format.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ── Diálogo para agregar/editar una presentación de compra ────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseFormatDialog(
    format: PurchaseFormat?,
    allowedUnits: List<String>,
    suppliers: List<Supplier>,
    onNavigateToSuppliers: () -> Unit,
    onConfirm: (PurchaseFormat) -> Unit,
    onDismiss: () -> Unit
) {
    var description by remember { mutableStateOf(format?.description ?: "") }
    var supplierItemName by remember { mutableStateOf(format?.supplierItemName.orEmpty()) }
    var selectedSupplierId by remember { mutableStateOf(format?.supplierId ?: "") }
    var selectedSupplierName by remember { mutableStateOf(format?.supplierName ?: "") }
    var supplierExpanded by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf(format?.unit?.takeIf { it.isNotEmpty() } ?: allowedUnits.firstOrNull() ?: "") }
    var unitExpanded by remember { mutableStateOf(false) }
    var unitsPerPackage by remember { mutableStateOf(format?.unitsPerPackage?.takeIf { it != 1.0 || format.id.isNotEmpty() }?.let { it.fmt(2).trimEnd('0').trimEnd('.') } ?: "") }
    var packageCost by remember { mutableStateOf(format?.packageCost?.takeIf { it > 0 }?.let { it.fmt(2) } ?: "") }

    val units = unitsPerPackage.toDoubleOrNull() ?: 0.0
    val cost = packageCost.toDoubleOrNull() ?: 0.0
    val costPerUnit = if (units > 0) cost / units else 0.0

    // Si es el primer formato (nuevo sin ID), marcar como default automáticamente
    val isFirstFormat = format == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (format == null) "Agregar presentación de compra" else "Editar presentación") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Explicación
                Text(
                    text = "Una presentación es la forma en que comprás este ingrediente a un proveedor. Por ejemplo: \"Saco 50 lb\" de Distribuidora ABC a Q200.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Cómo lo factura el proveedor, aparte del empaque. Se pide con la MISMA
                // etiqueta y en el mismo orden que `ResaleFormatFields` —el diálogo que usan
                // reventa y suministros— porque las dos puntas tienen que pedir lo mismo con
                // las mismas palabras. Sin este campo, la fila de materia prima de la factura
                // no tenía qué mostrar y había que traducir cada renglón de memoria.
                OutlinedTextField(
                    value = supplierItemName,
                    onValueChange = { supplierItemName = it },
                    label = { Text("Descripción de la presentación") },
                    placeholder = { Text("Ej: Azúcar blanca Don Justo cabal") },
                    supportingText = { Text("Como lo llama el proveedor en su factura") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Presentación") },
                    placeholder = { Text("Ej: Saco 50 lb, Caja 12 unid.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Unidad de medida de esta presentación
                if (allowedUnits.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = unitExpanded,
                        onExpandedChange = { unitExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unidad de esta presentación") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                            allowedUnits.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = { selectedUnit = opt; unitExpanded = false },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                } else if (allowedUnits.size == 1) {
                    // Solo una unidad: mostrar como texto informativo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Unidad", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(allowedUnits.first(), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                }

                // Proveedor
                if (suppliers.isEmpty()) {
                    // Sin proveedores registrados → redirigir
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No tenés proveedores registrados. Para vincular un proveedor, primero agrégalo en el módulo de Proveedores.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(
                            onClick = onNavigateToSuppliers,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Ir a Proveedores →")
                        }
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = supplierExpanded,
                        onExpandedChange = { supplierExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedSupplierName.ifEmpty { "Sin especificar" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Proveedor") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = supplierExpanded, onDismissRequest = { supplierExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Sin especificar") },
                                onClick = { selectedSupplierId = ""; selectedSupplierName = ""; supplierExpanded = false },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                            suppliers.forEach { s ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(s.company, fontWeight = FontWeight.Medium)
                                            if (s.salesperson.isNotEmpty()) {
                                                Text(s.salesperson, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    },
                                    onClick = { selectedSupplierId = s.id; selectedSupplierName = s.company; supplierExpanded = false },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }

                // Cantidad y costo
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unitsPerPackage,
                        onValueChange = { unitsPerPackage = it },
                        label = { Text("Cantidad en paquete") },
                        placeholder = { Text("50") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        suffix = { if (selectedUnit.isNotEmpty()) Text(selectedUnit) }
                    )
                    OutlinedTextField(
                        value = packageCost,
                        onValueChange = { packageCost = it },
                        label = { Text("Costo del paquete") },
                        placeholder = { Text("200.00") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        prefix = { Text("Q ") }
                    )
                }

                // Resultado calculado
                if (costPerUnit > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Resumen:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Q ${cost.fmt(2)} / paquete  →  Q ${costPerUnit.fmt(2)} / $selectedUnit",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        PurchaseFormat(
                            id = format?.id ?: generateFormatId(),
                            description = description.trim(),
                            unit = selectedUnit,
                            supplierId = selectedSupplierId,
                            supplierName = selectedSupplierName.trim(),
                            unitsPerPackage = unitsPerPackage.toDoubleOrNull() ?: 1.0,
                            packageCost = packageCost.toDoubleOrNull() ?: 0.0,
                            isDefault = format?.isDefault ?: isFirstFormat,
                            supplierItemName = supplierItemName.trim()
                        )
                    )
                },
                enabled = description.isNotBlank()
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun generateFormatId(): String {
    val hex = "0123456789abcdef"
    return Random.nextBytes(8).joinToString("") { b ->
        hex[(b.toInt() shr 4) and 0xF].toString() + hex[b.toInt() and 0xF]
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
