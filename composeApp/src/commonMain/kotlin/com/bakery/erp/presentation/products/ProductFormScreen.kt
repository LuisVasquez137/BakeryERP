package com.bakery.erp.presentation.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.BakingTechnicalSheet
import com.bakery.erp.model.PRODUCT_CATEGORIES
import com.bakery.erp.model.Preparation
import com.bakery.erp.model.PreparationUsage
import com.bakery.erp.model.PriceList
import com.bakery.erp.model.PriceListType
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductCharacteristics
import com.bakery.erp.model.ProductDecoration
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.pricing.PriceListViewModel
import com.bakery.erp.presentation.preparations.PreparationViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.ImagePickerButton
import com.bakery.erp.util.fmt
import com.bakery.erp.util.gramsToOz
import com.bakery.erp.util.ozToGrams

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    productId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductViewModel,
    preparationViewModel: PreparationViewModel,
    priceListViewModel: PriceListViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val preparationState by preparationViewModel.uiState.collectAsState()
    val priceState by priceListViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) {
            preparationViewModel.loadPreparations(companyId)
            priceListViewModel.loadLists(companyId)
        }
    }
    LaunchedEffect(companyId, productId) {
        if (companyId.isNotEmpty() && !productId.isNullOrEmpty()) {
            priceListViewModel.loadPricesForProduct(companyId, productId)
        }
    }

    val existingProduct = remember(productId, state.productList) {
        if (productId != null) state.productList.find { it.id == productId }
            ?: state.selectedProduct?.takeIf { it.id == productId }
        else null
    }
    var decorations by remember(existingProduct?.id) {
        mutableStateOf(existingProduct?.decorations ?: emptyList())
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

    // Peso en crudo: el usuario decide la unidad (g/oz) — internamente se almacena en gramos.
    val initialRawUnit = existingProduct?.rawWeightUnit ?: "g"
    val initialRawDisplay = existingProduct?.rawWeightGrams?.let { grams ->
        if (initialRawUnit == "oz") grams.gramsToOz().fmt(2) else grams.fmt(0)
    } ?: ""
    var rawWeightInput by rememberSaveable { mutableStateOf(initialRawDisplay) }
    var rawWeightUnit by rememberSaveable { mutableStateOf(initialRawUnit) }
    var rawWeightUnitExpanded by remember { mutableStateOf(false) }

    var shelfLife by rememberSaveable { mutableStateOf(existingProduct?.shelfLife?.toString() ?: "") }
    var storageType by rememberSaveable { mutableStateOf(existingProduct?.storageType ?: "") }
    var notes by rememberSaveable { mutableStateOf(existingProduct?.notes ?: "") }
    // ¿Producto in-house o de reventa? La asignación de plantas que pueden
    // producirlo se hace desde el módulo de Sucursales (Branch.assignedProductIds),
    // no aquí — única fuente de verdad.
    var isInHouseProduced by rememberSaveable {
        mutableStateOf(existingProduct?.isInHouseProduced ?: true)
    }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var nameError by remember { mutableStateOf(false) }
    var salePriceError by remember { mutableStateOf(false) }

    // Organoléptico
    val ch = existingProduct?.characteristics ?: ProductCharacteristics()
    var crustColor by rememberSaveable { mutableStateOf(ch.crustColor ?: "") }
    var crumbColor by rememberSaveable { mutableStateOf(ch.crumbColor ?: "") }
    var texture by rememberSaveable { mutableStateOf(ch.texture ?: "") }
    var shape by rememberSaveable { mutableStateOf(ch.shape ?: "") }
    var aroma by rememberSaveable { mutableStateOf(ch.aroma ?: "") }
    var flavorProfile by rememberSaveable { mutableStateOf(ch.flavorProfile ?: "") }

    // Ficha técnica
    val ts = existingProduct?.technicalSheet ?: BakingTechnicalSheet()
    var bakingTemperature by rememberSaveable { mutableStateOf(ts.bakingTemperatureCelsius?.toString() ?: "") }
    var bakingTime by rememberSaveable { mutableStateOf(ts.bakingTimeMinutes?.toString() ?: "") }
    var fermentationTime by rememberSaveable { mutableStateOf(ts.fermentationTimeHours?.toString() ?: "") }
    var fermentationTemperature by rememberSaveable { mutableStateOf(ts.fermentationTemperatureCelsius?.toString() ?: "") }
    var hydration by rememberSaveable { mutableStateOf(ts.hydrationPercentage?.toString() ?: "") }
    var shapingNotes by rememberSaveable { mutableStateOf(ts.shapingNotes ?: "") }
    var bakingNotes by rememberSaveable { mutableStateOf(ts.bakingNotes ?: "") }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // ── Precios por lista (Tab Precios) ─────────────────────────────────────
    val activeLists = remember(priceState.lists) { priceState.lists.filter { it.active } }
    val retailDefaultId = remember(activeLists) {
        activeLists.firstOrNull { it.type == PriceListType.RETAIL && it.isDefault }?.id ?: ""
    }
    // Inputs del usuario. "" = sin override (aplica factor o nada).
    var priceInputs by remember(productId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Listas que ANTES tenían override y AHORA están vacías → borrar el ProductPrice.
    var listsWithExistingOverride by remember(productId) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(activeLists, priceState.pricesForProduct, existingProduct?.id) {
        if (activeLists.isEmpty()) return@LaunchedEffect
        val pricesByList = priceState.pricesForProduct.associateBy { it.priceListId }
        priceInputs = activeLists.associate { list ->
            val current = pricesByList[list.id]?.unitPrice
            list.id to (current?.takeIf { it > 0 }?.fmt(2) ?: "")
        }
        listsWithExistingOverride = pricesByList.values
            .filter { it.unitPrice > 0 }
            .map { it.priceListId }
            .toSet()
    }

    // ── Side effects ────────────────────────────────────────────────────────
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    fun buildProduct(): Product {
        val rawNumeric = rawWeightInput.toDoubleOrNull()
        val rawGrams = rawNumeric?.let { if (rawWeightUnit == "oz") it.ozToGrams() else it }
        return Product(
            id = existingProduct?.id ?: "",
            name = name.trim().replaceFirstChar { it.uppercase() },
            description = description.trim(),
            category = category,
            active = active,
            imageUrl = existingProduct?.imageUrl,
            salePrice = salePrice.toDoubleOrNull() ?: 0.0,
            unit = unit.trim(),
            weightPerUnit = weightPerUnit.toDoubleOrNull(),
            rawWeightGrams = rawGrams,
            rawWeightUnit = if (rawGrams != null) rawWeightUnit else null,
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
            ),
            technicalSheet = BakingTechnicalSheet(
                bakingTemperatureCelsius = bakingTemperature.toIntOrNull(),
                bakingTimeMinutes = bakingTime.toIntOrNull(),
                fermentationTimeHours = fermentationTime.toDoubleOrNull(),
                fermentationTemperatureCelsius = fermentationTemperature.toIntOrNull(),
                hydrationPercentage = hydration.toDoubleOrNull(),
                shapingNotes = shapingNotes.trim().ifEmpty { null },
                bakingNotes = bakingNotes.trim().ifEmpty { null }
            ),
            decorations = decorations.filter { it.preparationId.isNotEmpty() && it.gramsPerUnit > 0.0 },
            isInHouseProduced = isInHouseProduced
        )
    }

    fun onSave() {
        var hasError = false
        if (name.isBlank()) { nameError = true; hasError = true }
        if (salePrice.isBlank() || salePrice.toDoubleOrNull() == null || salePrice.toDouble() <= 0) {
            salePriceError = true; hasError = true
        }
        if (hasError) {
            // Si el error está en la pestaña General, llevamos al usuario allí.
            selectedTab = 0
            return
        }
        // Construir mapa de precios por lista. El campo `salePrice` de General es
        // la única fuente de verdad para la lista RETAIL default — sobrescribe lo
        // que esté en `priceInputs` para esa lista.
        val pricesByList = mutableMapOf<String, Double>()
        val resets = mutableSetOf<String>()
        priceInputs.forEach { (listId, raw) ->
            val value = raw.toDoubleOrNull()
            if (value != null && value > 0) {
                pricesByList[listId] = value
            } else if (listId in listsWithExistingOverride && listId != retailDefaultId) {
                // El usuario vació un override que existía → borrar para que el factor aplique.
                resets.add(listId)
            }
        }
        if (retailDefaultId.isNotEmpty()) {
            val retailPrice = salePrice.toDoubleOrNull() ?: 0.0
            if (retailPrice > 0) pricesByList[retailDefaultId] = retailPrice
        }
        val listNames = activeLists.associate { it.id to it.name }
        viewModel.saveProduct(
            companyId, buildProduct(), selectedImageBytes,
            pricesByList, listNames, resets
        )
    }

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
        AdaptiveContainer(
            modifier = Modifier.padding(padding),
            maxContentWidth = 1100.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("General") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Precios") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Adornos") }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Organoléptico") }
                    )
                    Tab(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        text = { Text("Ficha técnica") }
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (selectedTab) {
                            0 -> {
                                GeneralTabContent(
                                name = name, onNameChange = { name = it; nameError = false },
                                nameError = nameError,
                                description = description, onDescriptionChange = { description = it },
                                category = category, onCategoryChange = { category = it },
                                categoryExpanded = categoryExpanded,
                                onCategoryExpandedChange = { categoryExpanded = it },
                                active = active, onActiveChange = { active = it },
                                existingImageUrl = existingProduct?.imageUrl,
                                selectedImageBytes = selectedImageBytes,
                                onImageSelected = { selectedImageBytes = it },
                                salePrice = salePrice, onSalePriceChange = { salePrice = it; salePriceError = false },
                                salePriceError = salePriceError,
                                unit = unit, onUnitChange = { unit = it },
                                weightPerUnit = weightPerUnit, onWeightPerUnitChange = { weightPerUnit = it },
                                shelfLife = shelfLife, onShelfLifeChange = { shelfLife = it },
                                rawWeightInput = rawWeightInput, onRawWeightInputChange = { rawWeightInput = it },
                                rawWeightUnit = rawWeightUnit, onRawWeightUnitChange = { rawWeightUnit = it },
                                rawWeightUnitExpanded = rawWeightUnitExpanded,
                                onRawWeightUnitExpandedChange = { rawWeightUnitExpanded = it },
                                storageType = storageType, onStorageTypeChange = { storageType = it },
                                notes = notes, onNotesChange = { notes = it }
                            )
                                ProductionRoutingSection(
                                    isInHouseProduced = isInHouseProduced,
                                    onIsInHouseChange = { isInHouseProduced = it }
                                )
                            }
                            1 -> PricesTabContent(
                                lists = activeLists,
                                retailDefaultId = retailDefaultId,
                                salePrice = salePrice,
                                priceInputs = priceInputs,
                                listsWithExistingOverride = listsWithExistingOverride,
                                onPriceChange = { listId, value ->
                                    priceInputs = priceInputs + (listId to value)
                                }
                            )
                            2 -> DecorationsTabContent(
                                decorations = decorations,
                                onDecorationsChange = { decorations = it },
                                availableDecorations = preparationState.preparationList.filter {
                                    it.active && it.usage == PreparationUsage.DECORATION
                                }
                            )
                            3 -> OrganolepticTabContent(
                                crustColor = crustColor, onCrustColorChange = { crustColor = it },
                                crumbColor = crumbColor, onCrumbColorChange = { crumbColor = it },
                                texture = texture, onTextureChange = { texture = it },
                                shape = shape, onShapeChange = { shape = it },
                                aroma = aroma, onAromaChange = { aroma = it },
                                flavorProfile = flavorProfile, onFlavorProfileChange = { flavorProfile = it }
                            )
                            else -> TechnicalSheetTabContent(
                                bakingTemperature = bakingTemperature,
                                onBakingTemperatureChange = { bakingTemperature = it },
                                bakingTime = bakingTime, onBakingTimeChange = { bakingTime = it },
                                fermentationTime = fermentationTime,
                                onFermentationTimeChange = { fermentationTime = it },
                                fermentationTemperature = fermentationTemperature,
                                onFermentationTemperatureChange = { fermentationTemperature = it },
                                hydration = hydration, onHydrationChange = { hydration = it },
                                shapingNotes = shapingNotes, onShapingNotesChange = { shapingNotes = it },
                                bakingNotes = bakingNotes, onBakingNotesChange = { bakingNotes = it }
                            )
                        }

                        Spacer(Modifier.padding(8.dp))
                    }
                }

                // Botón guardar siempre visible (fuera de la pestaña activa)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = ::onSave,
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
                }
            }
        }
    }
}

// ── Tab: General ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralTabContent(
    name: String, onNameChange: (String) -> Unit, nameError: Boolean,
    description: String, onDescriptionChange: (String) -> Unit,
    category: String, onCategoryChange: (String) -> Unit,
    categoryExpanded: Boolean, onCategoryExpandedChange: (Boolean) -> Unit,
    active: Boolean, onActiveChange: (Boolean) -> Unit,
    existingImageUrl: String?,
    selectedImageBytes: ByteArray?,
    onImageSelected: (ByteArray) -> Unit,
    salePrice: String, onSalePriceChange: (String) -> Unit, salePriceError: Boolean,
    unit: String, onUnitChange: (String) -> Unit,
    weightPerUnit: String, onWeightPerUnitChange: (String) -> Unit,
    shelfLife: String, onShelfLifeChange: (String) -> Unit,
    rawWeightInput: String, onRawWeightInputChange: (String) -> Unit,
    rawWeightUnit: String, onRawWeightUnitChange: (String) -> Unit,
    rawWeightUnitExpanded: Boolean, onRawWeightUnitExpandedChange: (Boolean) -> Unit,
    storageType: String, onStorageTypeChange: (String) -> Unit,
    notes: String, onNotesChange: (String) -> Unit
) {
    BoxWithConstraints {
        val twoColumns = maxWidth >= 800.dp

        if (twoColumns) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GeneralInfoBlock(
                        name, onNameChange, nameError,
                        description, onDescriptionChange,
                        category, onCategoryChange, categoryExpanded, onCategoryExpandedChange,
                        active, onActiveChange
                    )
                    SectionDivider()
                    ImageBlock(existingImageUrl, selectedImageBytes, onImageSelected)
                    SectionDivider()
                    NotesBlock(notes, onNotesChange)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CommercialBlock(salePrice, onSalePriceChange, salePriceError, unit, onUnitChange)
                    SectionDivider()
                    ProductDataBlock(
                        weightPerUnit, onWeightPerUnitChange,
                        shelfLife, onShelfLifeChange,
                        rawWeightInput, onRawWeightInputChange,
                        rawWeightUnit, onRawWeightUnitChange,
                        rawWeightUnitExpanded, onRawWeightUnitExpandedChange,
                        storageType, onStorageTypeChange
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GeneralInfoBlock(
                    name, onNameChange, nameError,
                    description, onDescriptionChange,
                    category, onCategoryChange, categoryExpanded, onCategoryExpandedChange,
                    active, onActiveChange
                )
                SectionDivider()
                ImageBlock(existingImageUrl, selectedImageBytes, onImageSelected)
                SectionDivider()
                CommercialBlock(salePrice, onSalePriceChange, salePriceError, unit, onUnitChange)
                SectionDivider()
                ProductDataBlock(
                    weightPerUnit, onWeightPerUnitChange,
                    shelfLife, onShelfLifeChange,
                    rawWeightInput, onRawWeightInputChange,
                    rawWeightUnit, onRawWeightUnitChange,
                    rawWeightUnitExpanded, onRawWeightUnitExpandedChange,
                    storageType, onStorageTypeChange
                )
                SectionDivider()
                NotesBlock(notes, onNotesChange)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralInfoBlock(
    name: String, onNameChange: (String) -> Unit, nameError: Boolean,
    description: String, onDescriptionChange: (String) -> Unit,
    category: String, onCategoryChange: (String) -> Unit,
    categoryExpanded: Boolean, onCategoryExpandedChange: (Boolean) -> Unit,
    active: Boolean, onActiveChange: (Boolean) -> Unit
) {
    SectionHeader("General")

    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text("Nombre *") },
        modifier = Modifier.fillMaxWidth(),
        isError = nameError,
        supportingText = if (nameError) ({ Text("El nombre es requerido") }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
    )

    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text("Descripción") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4
    )

    ExposedDropdownMenuBox(
        expanded = categoryExpanded,
        onExpandedChange = onCategoryExpandedChange,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = category,
            onValueChange = {},
            readOnly = true,
            label = { Text("Categoría") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = categoryExpanded,
            onDismissRequest = { onCategoryExpandedChange(false) }
        ) {
            PRODUCT_CATEGORIES.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onCategoryChange(option)
                        onCategoryExpandedChange(false)
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
        Switch(checked = active, onCheckedChange = onActiveChange)
    }
}

@Composable
private fun ImageBlock(
    existingImageUrl: String?,
    selectedImageBytes: ByteArray?,
    onImageSelected: (ByteArray) -> Unit
) {
    SectionHeader("Imagen")
    ImagePickerButton(
        currentImageUrl = existingImageUrl,
        selectedBytes = selectedImageBytes,
        onImageSelected = onImageSelected,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CommercialBlock(
    salePrice: String, onSalePriceChange: (String) -> Unit, salePriceError: Boolean,
    unit: String, onUnitChange: (String) -> Unit
) {
    SectionHeader("Datos Comerciales")

    OutlinedTextField(
        value = salePrice,
        onValueChange = onSalePriceChange,
        label = { Text("Precio venta (por presentación) *") },
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
        onValueChange = onUnitChange,
        label = { Text("Presentación") },
        placeholder = { Text("unidad, pieza, tira de 6…") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductDataBlock(
    weightPerUnit: String, onWeightPerUnitChange: (String) -> Unit,
    shelfLife: String, onShelfLifeChange: (String) -> Unit,
    rawWeightInput: String, onRawWeightInputChange: (String) -> Unit,
    rawWeightUnit: String, onRawWeightUnitChange: (String) -> Unit,
    rawWeightUnitExpanded: Boolean, onRawWeightUnitExpandedChange: (Boolean) -> Unit,
    storageType: String, onStorageTypeChange: (String) -> Unit
) {
    SectionHeader("Datos del Producto")

    TwoColumnRow(
        first = {
            OutlinedTextField(
                value = weightPerUnit,
                onValueChange = onWeightPerUnitChange,
                label = { Text("Peso por unidad (oz)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        second = {
            OutlinedTextField(
                value = shelfLife,
                onValueChange = onShelfLifeChange,
                label = { Text("Vida útil (días)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = rawWeightInput,
            onValueChange = onRawWeightInputChange,
            label = { Text("Peso en crudo") },
            modifier = Modifier.weight(2f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            supportingText = { Text("Sin relleno ni adorno — divide la masa") }
        )
        ExposedDropdownMenuBox(
            expanded = rawWeightUnitExpanded,
            onExpandedChange = onRawWeightUnitExpandedChange,
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = if (rawWeightUnit == "oz") "Onzas" else "Gramos",
                onValueChange = {},
                readOnly = true,
                label = { Text("Unidad") },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = rawWeightUnitExpanded,
                onDismissRequest = { onRawWeightUnitExpandedChange(false) }
            ) {
                DropdownMenuItem(
                    text = { Text("Gramos (g)") },
                    onClick = { onRawWeightUnitChange("g"); onRawWeightUnitExpandedChange(false) }
                )
                DropdownMenuItem(
                    text = { Text("Onzas (oz)") },
                    onClick = { onRawWeightUnitChange("oz"); onRawWeightUnitExpandedChange(false) }
                )
            }
        }
    }

    OutlinedTextField(
        value = storageType,
        onValueChange = onStorageTypeChange,
        label = { Text("Tipo de almacenamiento") },
        placeholder = { Text("temperatura ambiente, refrigerado…") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun NotesBlock(notes: String, onNotesChange: (String) -> Unit) {
    SectionHeader("Notas")
    OutlinedTextField(
        value = notes,
        onValueChange = onNotesChange,
        label = { Text("Notas adicionales") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 6
    )
}

// ── Tab: Organoléptico ──────────────────────────────────────────────────────

@Composable
private fun OrganolepticTabContent(
    crustColor: String, onCrustColorChange: (String) -> Unit,
    crumbColor: String, onCrumbColorChange: (String) -> Unit,
    texture: String, onTextureChange: (String) -> Unit,
    shape: String, onShapeChange: (String) -> Unit,
    aroma: String, onAromaChange: (String) -> Unit,
    flavorProfile: String, onFlavorProfileChange: (String) -> Unit
) {
    SectionHeader("Características Organolépticas")
    Text(
        "Define el estándar de calidad sensorial del producto.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    TwoColumnRow(
        first = { TextFieldInput("Color corteza", crustColor, onCrustColorChange) },
        second = { TextFieldInput("Color miga", crumbColor, onCrumbColorChange) }
    )
    TwoColumnRow(
        first = { TextFieldInput("Textura", texture, onTextureChange) },
        second = { TextFieldInput("Forma", shape, onShapeChange) }
    )
    TwoColumnRow(
        first = { TextFieldInput("Aroma", aroma, onAromaChange) },
        second = { TextFieldInput("Perfil de sabor", flavorProfile, onFlavorProfileChange) }
    )
}

// ── Tab: Ficha Técnica ──────────────────────────────────────────────────────

@Composable
private fun TechnicalSheetTabContent(
    bakingTemperature: String, onBakingTemperatureChange: (String) -> Unit,
    bakingTime: String, onBakingTimeChange: (String) -> Unit,
    fermentationTime: String, onFermentationTimeChange: (String) -> Unit,
    fermentationTemperature: String, onFermentationTemperatureChange: (String) -> Unit,
    hydration: String, onHydrationChange: (String) -> Unit,
    shapingNotes: String, onShapingNotesChange: (String) -> Unit,
    bakingNotes: String, onBakingNotesChange: (String) -> Unit
) {
    SectionHeader("Horneado")

    TwoColumnRow(
        first = {
            OutlinedTextField(
                value = bakingTemperature,
                onValueChange = onBakingTemperatureChange,
                label = { Text("Temperatura horneado (°C)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        },
        second = {
            OutlinedTextField(
                value = bakingTime,
                onValueChange = onBakingTimeChange,
                label = { Text("Tiempo horneado (min)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
    )

    SectionDivider()
    SectionHeader("Fermentación")

    TwoColumnRow(
        first = {
            OutlinedTextField(
                value = fermentationTime,
                onValueChange = onFermentationTimeChange,
                label = { Text("Tiempo fermentación (h)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        second = {
            OutlinedTextField(
                value = fermentationTemperature,
                onValueChange = onFermentationTemperatureChange,
                label = { Text("Temperatura fermentación (°C)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
    )

    SectionDivider()
    SectionHeader("Masa")

    OutlinedTextField(
        value = hydration,
        onValueChange = onHydrationChange,
        label = { Text("Hidratación (%)") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        suffix = { Text("%") },
        supportingText = { Text("Porcentaje de agua sobre harina") }
    )

    OutlinedTextField(
        value = shapingNotes,
        onValueChange = onShapingNotesChange,
        label = { Text("Notas de formado") },
        placeholder = { Text("forma, tamaño, técnica de boleado…") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4
    )

    OutlinedTextField(
        value = bakingNotes,
        onValueChange = onBakingNotesChange,
        label = { Text("Notas técnicas adicionales") },
        placeholder = { Text("vapor, posición en horno, acabado…") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4
    )
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

// ── Tab: Adornos ─────────────────────────────────────────────────────────────

@Composable
private fun DecorationsTabContent(
    decorations: List<ProductDecoration>,
    onDecorationsChange: (List<ProductDecoration>) -> Unit,
    availableDecorations: List<Preparation>
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        DecorationPickerDialog(
            preparations = availableDecorations.filter { p ->
                decorations.none { it.preparationId == p.id }
            },
            onPick = { prep ->
                onDecorationsChange(
                    decorations + ProductDecoration(
                        preparationId = prep.id,
                        preparationName = prep.name,
                        gramsPerUnit = 0.0
                    )
                )
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }

    SectionHeader("Adornos del producto")
    Text(
        "Preparaciones tipo \"adorno\" que se aplican al producto terminado (ej. concha). " +
            "Indica los gramos por unidad — al producir, el sistema descontará automáticamente del lote " +
            "correspondiente.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (availableDecorations.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "No hay preparaciones tipo \"adorno\" registradas. Crea una en el módulo Preparaciones " +
                "(eligiendo \"Para adorno\") para poder usarla aquí.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    Spacer(Modifier.height(8.dp))

    if (decorations.isEmpty()) {
        Text(
            "Sin adornos registrados.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    } else {
        decorations.forEachIndexed { idx, deco ->
            DecorationRow(
                decoration = deco,
                onGramsChange = { grams ->
                    onDecorationsChange(
                        decorations.mapIndexed { i, d ->
                            if (i == idx) d.copy(gramsPerUnit = grams.toDoubleOrNull() ?: 0.0) else d
                        }
                    )
                },
                onRemove = {
                    onDecorationsChange(decorations.filterIndexed { i, _ -> i != idx })
                }
            )
            Spacer(Modifier.height(6.dp))
        }
    }

    Spacer(Modifier.height(4.dp))

    OutlinedButton(
        onClick = { showPicker = true },
        enabled = availableDecorations.any { p -> decorations.none { it.preparationId == p.id } },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Agregar adorno")
    }
}

@Composable
private fun DecorationRow(
    decoration: ProductDecoration,
    onGramsChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    val gramsText = if (decoration.gramsPerUnit > 0.0)
        if (decoration.gramsPerUnit == decoration.gramsPerUnit.toLong().toDouble())
            decoration.gramsPerUnit.toLong().toString()
        else decoration.gramsPerUnit.toString()
    else ""
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = decoration.preparationName.ifEmpty { "Sin nombre" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = gramsText,
                onValueChange = onGramsChange,
                label = { Text("g / unidad") },
                modifier = Modifier.width(110.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Quitar",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DecorationPickerDialog(
    preparations: List<Preparation>,
    onPick: (Preparation) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val sorted = remember(preparations) { preparations.sortedBy { it.name.lowercase() } }
    val filtered = remember(searchQuery, sorted) {
        if (searchQuery.isBlank()) sorted
        else sorted.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Seleccionar adorno",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (preparations.isEmpty()) {
                    Text(
                        "Ya agregaste todos los adornos disponibles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Buscar") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(filtered, key = { it.id }) { prep ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(prep) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = prep.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (prep.description?.isNotBlank() == true) {
                                    Text(
                                        text = prep.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
            }
        }
    }
}

// ── Tab: Precios ────────────────────────────────────────────────────────────

@Composable
private fun PricesTabContent(
    lists: List<PriceList>,
    retailDefaultId: String,
    salePrice: String,
    priceInputs: Map<String, String>,
    listsWithExistingOverride: Set<String>,
    onPriceChange: (listId: String, value: String) -> Unit
) {
    if (lists.isEmpty()) {
        Text(
            "Aún no hay listas de precios configuradas. Crea al menos una en Administración → Listas de precios.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val retail = salePrice.toDoubleOrNull() ?: 0.0

    Text(
        "Cascada: si dejas un precio vacío y la lista tiene factor, se aplica automáticamente sobre el precio al cliente final. Escribir un valor lo fija como override de ese producto.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    lists.forEach { list ->
        val isRetailDefault = list.id == retailDefaultId
        val rawInput = priceInputs[list.id] ?: ""
        val value = if (isRetailDefault) salePrice else rawInput
        val hasOverride = !isRetailDefault && rawInput.toDoubleOrNull()?.let { it > 0 } == true
        val hadOverrideBefore = list.id in listsWithExistingOverride
        val autoFromFactor: Double? = if (!isRetailDefault && list.priceFactor != null && retail > 0)
            retail * list.priceFactor!! else null
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(list.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            buildString {
                                append(PriceListType.label(list.type))
                                if (list.isDefault) append(" · default")
                                list.priceFactor?.let { append(" · ${(it * 100).toInt()}% del retail") }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { raw ->
                            if (!isRetailDefault) onPriceChange(list.id, raw)
                        },
                        readOnly = isRetailDefault,
                        label = { Text("Precio (Q)") },
                        placeholder = {
                            autoFromFactor?.let { Text("auto: ${it.fmt(2)}") }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.widthIn(min = 140.dp, max = 200.dp)
                    )
                }
                val hint: String? = when {
                    isRetailDefault -> "Editar en la pestaña General"
                    hasOverride -> "Precio fijo · ignora el factor de la lista"
                    autoFromFactor != null -> "Aplica ${(list.priceFactor!! * 100).toInt()}% sobre Q${retail.fmt(2)} = Q${autoFromFactor.fmt(2)}"
                    list.priceFactor == null -> "Sin factor: si no defines precio, este producto saldrá como 'precio no definido' al despachar."
                    retail <= 0 -> "Define primero el precio al cliente final en General."
                    else -> null
                }
                if (hint != null) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hint.contains("no definido")) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (hadOverrideBefore && !hasOverride && !isRetailDefault) {
                    Text(
                        "Al guardar se borrará el precio fijo anterior y volverá a aplicarse el factor.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD9A441),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProductionRoutingSection(
    isInHouseProduced: Boolean,
    onIsInHouseChange: (Boolean) -> Unit
) {
    val PrimaryBlue = Color(0xFF005B8D)
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))

    Text(
        "Producción",
        style = MaterialTheme.typography.titleSmall,
        color = PrimaryBlue,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
    )

    // Switch: producto in-house vs reventa
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Lo producimos nosotros",
                color = PrimaryBlue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
            Text(
                if (isInHouseProduced)
                    "El producto entra al lote de producción de la planta. " +
                        "Para asignar qué plantas lo producen, ve a Sucursales → editar la planta → " +
                        "sección 'Productos que produce'."
                else "Reventa pura (ej. tostadas, chocolate): se sirve directo del stock terminado.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6E6E6E)
            )
        }
        Switch(
            checked = isInHouseProduced,
            onCheckedChange = onIsInHouseChange
        )
    }
}
