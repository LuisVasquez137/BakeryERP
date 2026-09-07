package com.bakery.erp.presentation.purchases

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
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
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Branch
import com.bakery.erp.model.ExpenseCategory
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Product
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.ingredients.IngredientPickerDialog
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.ImagePickerButton
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt
import kotlin.time.Clock

/**
 * La vuelta del mercado: una salida, muchas cosas chiquitas, muchos lugares, varias
 * sucursales.
 *
 * La pantalla está armada alrededor de una sola idea: **anotar tiene que ser más rápido que
 * clasificar**. Se escribe qué se compró y cuánto costó, y pegarle un insumo del catálogo es
 * opcional — es lo que decide si además entra al inventario. Obligar a elegir catálogo por
 * cada renglón haría que la vuelta no se registre nunca, que es peor que registrarla sin
 * inventario.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketRunFormScreen(
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: PurchaseViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val currentUser = authState.user
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId, currentUser) {
        if (companyId.isNotEmpty() && currentUser != null) {
            viewModel.loadFormData(companyId, currentUser)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.resetForm(); onNavigateBack() }
    }

    var lines by remember { mutableStateOf(listOf<MarketRunDraftLine>()) }
    var notes by remember { mutableStateOf("") }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var editing by remember { mutableStateOf<MarketRunDraftLine?>(null) }
    var showLineDialog by remember { mutableStateOf(false) }
    val date = remember { Clock.System.now().toEpochMilliseconds() }

    val branches = state.accessibleBranches
    var defaultBranch by remember(branches) {
        mutableStateOf(state.selectedBranch ?: branches.firstOrNull())
    }
    val total = lines.sumOf { it.amount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vuelta de compras", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Primary,
                    titleContentColor = androidx.compose.ui.graphics.Color.White,
                    navigationIconContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // El total va fijo abajo, como en la factura: es el número que se coteja contra
            // lo que se gastó de verdad, y arriba del pliegue no sirve.
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${lines.size} ${if (lines.size == 1) "cosa" else "cosas"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Q ${total.fmt(2)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val branch = defaultBranch ?: return@Button
                            val user = currentUser ?: return@Button
                            viewModel.saveMarketRun(
                                companyId = companyId,
                                currentUser = user,
                                defaultBranch = branch,
                                lines = lines,
                                date = date,
                                notes = notes.ifBlank { null },
                                expenseCategory = ExpenseCategory.OTROS.name,
                                photoBytes = photoBytes
                            )
                        },
                        enabled = lines.isNotEmpty() && defaultBranch != null && !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Guardar vuelta") }
                }
            }
        }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Anotá cada cosa con lo que costó. Si la pegás a un insumo del catálogo " +
                        "también entra al inventario; si no, queda como gasto y ya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // La sucursal del encabezado es solo el default. Con una sola accesible no
                // hay nada que elegir y el selector sobra.
                if (branches.size > 1) {
                    BranchDropdown(
                        label = "La mayoría va a",
                        branches = branches,
                        selected = defaultBranch,
                        onSelect = { defaultBranch = it }
                    )
                }

                lines.forEach { line ->
                    MarketRunLineCard(
                        line = line,
                        defaultBranchName = defaultBranch?.name.orEmpty(),
                        onEdit = { editing = line; showLineDialog = true },
                        onRemove = { lines = lines.filterNot { l -> l.id == line.id } }
                    )
                }

                OutlinedButton(
                    onClick = { editing = null; showLineDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar algo")
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas de la vuelta (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Text(
                    "Comprobantes (opcional)",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "Una foto de los recibos o de la hoja donde anotaste.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ImagePickerButton(
                    currentImageUrl = null,
                    selectedBytes = photoBytes,
                    onImageSelected = { photoBytes = it },
                    allowCamera = true
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showLineDialog) {
        MarketRunLineDialog(
            existing = editing,
            branches = branches,
            ingredients = state.allIngredients,
            products = state.supplierResaleProducts,
            onConfirm = { line ->
                lines = if (lines.any { it.id == line.id }) {
                    lines.map { if (it.id == line.id) line else it }
                } else lines + line
                showLineDialog = false
                editing = null
            },
            onDismiss = { showLineDialog = false; editing = null }
        )
    }
}

@Composable
private fun MarketRunLineCard(
    line: MarketRunDraftLine,
    defaultBranchName: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
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
            Column(modifier = Modifier.weight(1f).clickable(onClick = onEdit)) {
                Text(
                    line.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    buildString {
                        append(line.branch?.name ?: defaultBranchName)
                        if (line.placeName.isNotEmpty()) append(" · ${line.placeName}")
                        // Se dice explícitamente cuándo NO cuenta en inventario: es la
                        // diferencia que el usuario eligió al anotar, y no verla en la fila
                        // la vuelve invisible hasta que el conteo no cuadra.
                        append(if (line.touchesStock) " · entra al inventario" else " · solo gasto")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Q ${line.amount.fmt(2)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Quitar")
            }
        }
    }
}

/**
 * Alta de una cosa de la vuelta.
 *
 * El orden de los campos es el orden en que uno se acuerda: qué compré, cuánto me costó,
 * para dónde es, dónde lo compré. Pegarlo al catálogo va al final y es opcional a propósito
 * — es lo único que no se sabe de memoria.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketRunLineDialog(
    existing: MarketRunDraftLine?,
    branches: List<Branch>,
    ingredients: List<Ingredient>,
    products: List<Product>,
    onConfirm: (MarketRunDraftLine) -> Unit,
    onDismiss: () -> Unit
) {
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var amount by remember {
        mutableStateOf(existing?.amount?.takeIf { it > 0.0 }?.toString() ?: "")
    }
    var quantity by remember {
        mutableStateOf(existing?.quantity?.takeIf { it > 0.0 }?.toString() ?: "1")
    }
    var place by remember { mutableStateOf(existing?.placeName ?: "") }
    var branch by remember { mutableStateOf(existing?.branch) }
    var ingredient by remember { mutableStateOf(existing?.ingredient) }
    var showIngredientPicker by remember { mutableStateOf(false) }

    val parsedAmount = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
    val parsedQty = quantity.replace(',', '.').toDoubleOrNull() ?: 1.0
    val valid = (description.isNotBlank() || ingredient != null) && parsedAmount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "¿Qué compraste?" else "Editar") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Qué compraste *") },
                    placeholder = { Text("Ej: papas, trapos, brocha") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Cuánto costó (Q) *") },
                        modifier = Modifier.weight(2f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Cant.") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                if (branches.size > 1) {
                    BranchDropdown(
                        label = "Para qué sucursal",
                        branches = branches,
                        selected = branch,
                        onSelect = { branch = it },
                        allowNone = true
                    )
                }
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it },
                    label = { Text("Dónde lo compraste") },
                    placeholder = { Text("Ej: mercado, la tienda de la esquina") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                HorizontalDivider()

                // Pegarlo al catálogo es lo único que decide si además baja del inventario.
                // Va al final y es opcional: la vuelta tiene que poder anotarse completa sin
                // saber si "papas" ya existe como insumo.
                Text("¿Es un insumo que ya tenés?", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (ingredient != null)
                        "Entra al inventario como ${ingredient!!.name}."
                    else "Sin insumo: se registra como gasto y no toca el inventario.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showIngredientPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(ingredient?.name ?: "Elegir insumo")
                    }
                    if (ingredient != null) {
                        TextButton(onClick = { ingredient = null }) { Text("Quitar") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(
                        MarketRunDraftLine(
                            id = existing?.id ?: "mr_${Clock.System.now().toEpochMilliseconds()}",
                            description = description.trim(),
                            ingredient = ingredient,
                            product = null,
                            branch = branch,
                            placeName = place.trim(),
                            quantity = parsedQty,
                            amount = parsedAmount,
                            expiryDate = existing?.expiryDate ?: 0L
                        )
                    )
                }
            ) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )

    if (showIngredientPicker) {
        IngredientPickerDialog(
            ingredients = ingredients,
            title = "¿A qué insumo corresponde?",
            onPick = {
                ingredient = it
                if (description.isBlank()) description = it.name
                showIngredientPicker = false
            },
            onDismiss = { showIngredientPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchDropdown(
    label: String,
    branches: List<Branch>,
    selected: Branch?,
    onSelect: (Branch?) -> Unit,
    allowNone: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: if (allowNone) "La de siempre" else "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowNone) {
                DropdownMenuItem(
                    text = { Text("La de siempre") },
                    onClick = { onSelect(null); expanded = false }
                )
            }
            branches.forEach { b ->
                DropdownMenuItem(
                    text = { Text(b.name) },
                    onClick = { onSelect(b); expanded = false }
                )
            }
        }
    }
}
