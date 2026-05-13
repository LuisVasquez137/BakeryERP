package com.bakery.erp.presentation.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.bakery.erp.model.PRODUCT_CATEGORIES
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductCharacteristics
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.ImagePickerButton
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    productId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    val existingProduct = remember(productId, state.productList) {
        if (productId != null) state.productList.find { it.id == productId }
            ?: state.selectedProduct?.takeIf { it.id == productId }
        else null
    }

    // ── Form state ──────────────────────────────────────────────────────────
    var name by rememberSaveable { mutableStateOf(existingProduct?.name ?: "") }
    var description by rememberSaveable { mutableStateOf(existingProduct?.description ?: "") }
    var category by rememberSaveable { mutableStateOf(existingProduct?.category ?: "") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var active by rememberSaveable { mutableStateOf(existingProduct?.active ?: true) }
    var salePrice by rememberSaveable { mutableStateOf(existingProduct?.salePrice?.takeIf { it > 0 }?.toString() ?: "") }
    var unit by rememberSaveable { mutableStateOf(existingProduct?.unit ?: "") }
    var weightPerUnit by rememberSaveable { mutableStateOf(existingProduct?.weightPerUnit?.toString() ?: "") }
    var rawWeightGrams by rememberSaveable { mutableStateOf(existingProduct?.rawWeightGrams?.toString() ?: "") }
    var shelfLife by rememberSaveable { mutableStateOf(existingProduct?.shelfLife?.toString() ?: "") }
    var storageType by rememberSaveable { mutableStateOf(existingProduct?.storageType ?: "") }
    var notes by rememberSaveable { mutableStateOf(existingProduct?.notes ?: "") }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var nameError by remember { mutableStateOf(false) }
    var salePriceError by remember { mutableStateOf(false) }

    val ch = existingProduct?.characteristics ?: ProductCharacteristics()
    var crustColor by rememberSaveable { mutableStateOf(ch.crustColor ?: "") }
    var crumbColor by rememberSaveable { mutableStateOf(ch.crumbColor ?: "") }
    var texture by rememberSaveable { mutableStateOf(ch.texture ?: "") }
    var shape by rememberSaveable { mutableStateOf(ch.shape ?: "") }
    var aroma by rememberSaveable { mutableStateOf(ch.aroma ?: "") }
    var flavorProfile by rememberSaveable { mutableStateOf(ch.flavorProfile ?: "") }

    // ── Side effects ────────────────────────────────────────────────────────
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    fun buildProduct() = Product(
        id = existingProduct?.id ?: "",
        name = name.trim().replaceFirstChar { it.uppercase() },
        description = description.trim(),
        category = category,
        active = active,
        imageUrl = existingProduct?.imageUrl,  // preservar imagen existente si no se selecciona nueva
        salePrice = salePrice.toDoubleOrNull() ?: 0.0,
        unit = unit.trim(),
        weightPerUnit = weightPerUnit.toDoubleOrNull(),
        rawWeightGrams = rawWeightGrams.toDoubleOrNull(),
        shelfLife = shelfLife.toIntOrNull(),
        storageType = storageType.trim().ifEmpty { null },
        notes = notes.trim().ifEmpty { null },
        characteristics = ProductCharacteristics(
            crustColor = crustColor.trim().ifEmpty { null },
            crumbColor = crumbColor.trim().ifEmpty { null },
            texture = texture.trim().ifEmpty { null },
            shape = shape.trim().ifEmpty { null },
            aroma = aroma.trim().ifEmpty { null },
            flavorProfile = flavorProfile.trim().ifEmpty { null }
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (productId == null) "Nuevo Producto" else "Editar Producto",
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
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // ── General ─────────────────────────────────────────────────
                SectionHeader("General")

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Nombre *") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError,
                    supportingText = if (nameError) ({ Text("El nombre es requerido") }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                // Categoría — dropdown
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
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        PRODUCT_CATEGORIES.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    category = option
                                    categoryExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
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

                // ── Imagen ───────────────────────────────────────────────────
                SectionHeader("Imagen")

                ImagePickerButton(
                    currentImageUrl = existingProduct?.imageUrl,
                    selectedBytes = selectedImageBytes,
                    onImageSelected = { selectedImageBytes = it },
                    modifier = Modifier.fillMaxWidth()
                )

                SectionDivider()

                // ── Comercial ────────────────────────────────────────────────
                SectionHeader("Datos Comerciales")

                OutlinedTextField(
                    value = salePrice,
                    onValueChange = { salePrice = it; salePriceError = false },
                    label = { Text("Precio venta (por unidad mínima) *") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    prefix = { Text("Q ") },
                    isError = salePriceError,
                    supportingText = {
                        if (salePriceError) Text("El precio de venta es requerido")
                        else Text("Ej: si vendes en tiras de 6, registra el precio de 1 unidad")
                    }
                )

                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unidad mínima") },
                    placeholder = { Text("unidad, pieza, tira de 6…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Costo — solo lectura, calculado por el sistema
                OutlinedTextField(
                    value = "Calculado por el sistema",
                    onValueChange = {},
                    label = { Text("Costo unitario") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    trailingIcon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "No editable",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    },
                    supportingText = { Text("Se calculará automáticamente con las recetas") },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledSupportingTextColor = MaterialTheme.colorScheme.outline
                    )
                )

                SectionDivider()

                // ── Datos del producto ───────────────────────────────────────
                SectionHeader("Datos del Producto")

                TwoColumnRow(
                    first = {
                        OutlinedTextField(
                            value = weightPerUnit,
                            onValueChange = { weightPerUnit = it },
                            label = { Text("Peso por unidad (oz)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    },
                    second = {
                        OutlinedTextField(
                            value = shelfLife,
                            onValueChange = { shelfLife = it },
                            label = { Text("Vida útil (días)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                )

                OutlinedTextField(
                    value = rawWeightGrams,
                    onValueChange = { rawWeightGrams = it },
                    label = { Text("Peso en crudo") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    suffix = { Text("gr") },
                    supportingText = { Text("Peso sin relleno ni adorno — para calcular rendimiento de la masa") }
                )

                OutlinedTextField(
                    value = storageType,
                    onValueChange = { storageType = it },
                    label = { Text("Tipo de almacenamiento") },
                    placeholder = { Text("temperatura ambiente, refrigerado…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                SectionDivider()

                // ── Características organolépticas ───────────────────────────
                SectionHeader("Características Organolépticas")

                TwoColumnRow(
                    first = { TextFieldInput("Color corteza", crustColor) { crustColor = it } },
                    second = { TextFieldInput("Color miga", crumbColor) { crumbColor = it } }
                )
                TwoColumnRow(
                    first = { TextFieldInput("Textura", texture) { texture = it } },
                    second = { TextFieldInput("Forma", shape) { shape = it } }
                )
                TwoColumnRow(
                    first = { TextFieldInput("Aroma", aroma) { aroma = it } },
                    second = { TextFieldInput("Perfil de sabor", flavorProfile) { flavorProfile = it } }
                )

                SectionDivider()

                // ── Notas ────────────────────────────────────────────────────
                SectionHeader("Notas")

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas adicionales") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )

                Spacer(Modifier.padding(8.dp))

                Button(
                    onClick = {
                        var hasError = false
                        if (name.isBlank()) { nameError = true; hasError = true }
                        if (salePrice.isBlank() || salePrice.toDoubleOrNull() == null || salePrice.toDouble() <= 0) {
                            salePriceError = true; hasError = true
                        }
                        if (hasError) return@Button
                        viewModel.saveProduct(companyId, buildProduct(), selectedImageBytes)
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
                        Text("Guardar Producto", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.padding(16.dp))
            }
        } // AdaptiveContainer
    }
}

// ── Reusable helpers ────────────────────────────────────────────────────────

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

@Composable
private fun TwoColumnRow(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) { first() }
        Box(modifier = Modifier.weight(1f)) { second() }
    }
}

@Composable
private fun TextFieldInput(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}
