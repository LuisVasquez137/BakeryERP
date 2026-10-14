package com.bakery.erp.presentation.products

import com.bakery.erp.util.fmtTrim
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
import com.bakery.erp.presentation.ingredients.IngredientPickerDialog
import com.bakery.erp.model.usableInRecipes
import com.bakery.erp.ui.BarcodeField
import com.bakery.erp.model.BakingTechnicalSheet
import com.bakery.erp.model.DoughFilling
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.PRODUCT_CATEGORIES
import com.bakery.erp.model.RESALE_CATEGORY
import com.bakery.erp.model.Preparation
import com.bakery.erp.model.PreparationUsage
import com.bakery.erp.model.PriceList
import com.bakery.erp.model.PriceListType
import com.bakery.erp.model.samePrice
import com.bakery.erp.model.listRulePrice
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductCharacteristics
import com.bakery.erp.model.ProductDecoration
import com.bakery.erp.model.Recipe
import com.bakery.erp.model.Supplier
import com.bakery.erp.model.PurchaseFormat
import com.bakery.erp.model.purchaseFormatsForEditing
import com.bakery.erp.model.withDerivedPurchaseReference
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.ingredients.IngredientViewModel
import com.bakery.erp.presentation.pricing.PriceListViewModel
import com.bakery.erp.presentation.preparations.PreparationViewModel
import com.bakery.erp.presentation.recipes.RecipeViewModel
import com.bakery.erp.presentation.suppliers.SupplierViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.ImagePickerButton
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.util.fmt
import com.bakery.erp.util.gramsToOz
import com.bakery.erp.util.ozToGrams
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.model.isPortion
import com.bakery.erp.presentation.pos.ProductPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    productId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductViewModel,
    preparationViewModel: PreparationViewModel,
    priceListViewModel: PriceListViewModel,
    supplierViewModel: SupplierViewModel,
    ingredientViewModel: IngredientViewModel,
    recipeViewModel: RecipeViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val preparationState by preparationViewModel.uiState.collectAsState()
    val priceState by priceListViewModel.uiState.collectAsState()
    val supplierState by supplierViewModel.uiState.collectAsState()
    val ingredientState by ingredientViewModel.uiState.collectAsState()
    val recipeState by recipeViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) {
            preparationViewModel.loadPreparations(companyId)
            priceListViewModel.loadLists(companyId)
            supplierViewModel.loadSuppliers(companyId)
            ingredientViewModel.loadIngredients(companyId)
            recipeViewModel.loadRecipes(companyId)
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
    var doughFillings by remember(existingProduct?.id) {
        mutableStateOf(existingProduct?.doughFillings ?: emptyList())
    }
    // Presentaciones de compra (reventa). Un producto viejo —un proveedor y un costo— llega
    // convertido en su primera presentación, así guardarlo migra el dato sin script.
    var purchaseFormats by remember(existingProduct?.id) {
        mutableStateOf(existingProduct?.purchaseFormatsForEditing { newResaleFormatId() } ?: emptyList())
    }
    // Receta que ya genera este producto (si alguna) — se excluye del picker de
    // "relleno de otra masa": no tiene sentido que un producto lleve de relleno
    // masa de su propia receta.
    val ownRecipeId = remember(existingProduct?.id, recipeState.recipeList) {
        existingProduct?.let { p -> recipeState.recipeList.find { r -> r.yields.any { it.productId == p.id } }?.id }
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
    var isPerishable by rememberSaveable { mutableStateOf(existingProduct?.isPerishable ?: false) }
    var storageType by rememberSaveable { mutableStateOf(existingProduct?.storageType ?: "") }
    var unitsPerTray by rememberSaveable {
        mutableStateOf(existingProduct?.unitsPerTray?.takeIf { it > 0 }?.toString() ?: "")
    }
    var notes by rememberSaveable { mutableStateOf(existingProduct?.notes ?: "") }
    // ¿Producto in-house o de reventa? La asignación de plantas que pueden
    // producirlo se hace desde el módulo de Sucursales (Branch.assignedProductIds),
    // no aquí — única fuente de verdad.
    var isInHouseProduced by rememberSaveable {
        mutableStateOf(existingProduct?.isInHouseProduced ?: true)
    }
    var barcode by rememberSaveable { mutableStateOf(existingProduct?.barcode ?: "") }
    var employeeDiscount by rememberSaveable {
        mutableStateOf(existingProduct?.employeeDiscountPercent?.takeIf { it > 0 }?.fmtTrim(2) ?: "")
    }
    // Producto que vende del inventario de un insumo (el huevo: entra a las recetas y
    // además se vende por docena). Vacío = producto normal, con su stock de terminado.
    var linkedIngredientId by rememberSaveable { mutableStateOf(existingProduct?.linkedIngredientId ?: "") }
    var linkedIngredientName by rememberSaveable { mutableStateOf(existingProduct?.linkedIngredientName ?: "") }
    var ingredientUnitsPerSale by rememberSaveable {
        mutableStateOf(existingProduct?.ingredientUnitsPerSale?.takeIf { it > 0.0 }?.toString() ?: "")
    }
    // Porción de un pastel entero (se corta en la tienda). Vacío = producto normal.
    var portionOfProductId by rememberSaveable { mutableStateOf(existingProduct?.portionOfProductId ?: "") }
    var portionOfProductName by rememberSaveable { mutableStateOf(existingProduct?.portionOfProductName ?: "") }
    var portionsPerWhole by rememberSaveable {
        mutableStateOf(existingProduct?.portionsPerWhole?.takeIf { it > 0 }?.toString() ?: "")
    }
    // Al crear (productId==null) se pide el tipo antes de mostrar el form.
    var typeChosen by rememberSaveable { mutableStateOf(productId != null) }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var nameError by remember { mutableStateOf(false) }
    var salePriceError by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

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
    LaunchedEffect(formError) {
        formError?.let { snackbarHostState.showSnackbar(it); formError = null }
    }

    fun buildProduct(): Product {
        val rawNumeric = rawWeightInput.toDoubleOrNull()
        val rawGrams = rawNumeric?.let { if (rawWeightUnit == "oz") it.ozToGrams() else it }
        // Los datos solo-producción (peso crudo, organoléptico, ficha, adornos) no aplican
        // a la reventa: se persisten vacíos para no arrastrar basura.
        return Product(
            id = existingProduct?.id ?: "",
            name = name.trim().replaceFirstChar { it.uppercase() },
            description = description.trim(),
            // Toda la reventa se normaliza a una sola categoría (incluye docs legacy
            // que se hayan guardado como Abarrotes/Bebidas/Otros).
            category = if (isInHouseProduced) category else RESALE_CATEGORY,
            active = active,
            imageUrl = existingProduct?.imageUrl,
            salePrice = salePrice.toDoubleOrNull() ?: 0.0,
            employeeDiscountPercent = (employeeDiscount.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0),
            unit = unit.trim(),
            weightPerUnit = weightPerUnit.toDoubleOrNull(),
            rawWeightGrams = if (isInHouseProduced) rawGrams else null,
            rawWeightUnit = if (isInHouseProduced && rawGrams != null) rawWeightUnit else null,
            shelfLife = shelfLife.toIntOrNull(),
            // Solo reventa: el pan propio no se compra, así que no hay factura donde pedir lote.
            isPerishable = !isInHouseProduced && isPerishable,
            storageType = storageType.trim().ifEmpty { null },
            unitsPerTray = if (isInHouseProduced) unitsPerTray.toIntOrNull()?.takeIf { it > 0 } ?: 0 else 0,
            notes = notes.trim().ifEmpty { null },
            characteristics = if (isInHouseProduced) ProductCharacteristics(
                crustColor = crustColor.trim().ifEmpty { null },
                crumbColor = crumbColor.trim().ifEmpty { null },
                texture = texture.trim().ifEmpty { null },
                shape = shape.trim().ifEmpty { null },
                aroma = aroma.trim().ifEmpty { null },
                flavorProfile = flavorProfile.trim().ifEmpty { null }
            ) else ProductCharacteristics(),
            technicalSheet = if (isInHouseProduced) BakingTechnicalSheet(
                bakingTemperatureCelsius = bakingTemperature.toIntOrNull(),
                bakingTimeMinutes = bakingTime.toIntOrNull(),
                fermentationTimeHours = fermentationTime.toDoubleOrNull(),
                fermentationTemperatureCelsius = fermentationTemperature.toIntOrNull(),
                hydrationPercentage = hydration.toDoubleOrNull(),
                shapingNotes = shapingNotes.trim().ifEmpty { null },
                bakingNotes = bakingNotes.trim().ifEmpty { null }
            ) else BakingTechnicalSheet(),
            decorations = if (isInHouseProduced)
                decorations.filter {
                    (it.preparationId.isNotEmpty() || it.ingredientId.isNotEmpty()) && it.gramsPerUnit > 0.0
                }
            else emptyList(),
            doughFillings = if (isInHouseProduced)
                doughFillings.filter { it.recipeId.isNotEmpty() && it.rawWeightGrams > 0.0 }
            else emptyList(),
            isInHouseProduced = isInHouseProduced,
            // Proveedor y costo de referencia se derivan de la presentación predeterminada
            // (`withDerivedPurchaseReference`, abajo). El dato viejo ya llegó convertido en
            // presentación al abrir, así que si el usuario las quitó todas, no queda nada.
            purchaseFormats = if (!isInHouseProduced) purchaseFormats else emptyList(),
            supplierId = null,
            supplierName = null,
            purchaseCost = null,
            barcode = barcode.trim(),
            // El vínculo con un insumo solo tiene sentido en reventa: lo que producimos
            // nosotros ya tiene su propio inventario de terminado.
            linkedIngredientId = if (!isInHouseProduced) linkedIngredientId else "",
            linkedIngredientName = if (!isInHouseProduced) linkedIngredientName else "",
            ingredientUnitsPerSale = if (!isInHouseProduced && linkedIngredientId.isNotEmpty())
                ingredientUnitsPerSale.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0
            else 1.0,
            // La porción sale de un pastel nuestro; la reventa no se corta.
            portionOfProductId = if (isInHouseProduced) portionOfProductId else "",
            portionOfProductName = if (isInHouseProduced && portionOfProductId.isNotEmpty()) portionOfProductName else "",
            portionsPerWhole = if (isInHouseProduced && portionOfProductId.isNotEmpty())
                portionsPerWhole.toIntOrNull()?.takeIf { it > 0 } ?: 0
            else 0
        ).withDerivedPurchaseReference()
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
        if (isInHouseProduced && portionOfProductId.isNotEmpty() &&
            (portionsPerWhole.toIntOrNull() ?: 0) < 2
        ) {
            formError = "Indica cuántas porciones salen de un $portionOfProductName"
            selectedTab = 0
            return
        }
        // Un adorno/relleno elegido sin gramos por unidad se descartaba en silencio
        // al guardar (parecía que "no guardaba" el adorno). Ahora bloquea el guardado
        // y avisa cuál falta, en vez de perderlo sin decir nada.
        if (isInHouseProduced) {
            val incomplete = decorations.filter {
                (it.preparationId.isNotEmpty() || it.ingredientId.isNotEmpty()) && it.gramsPerUnit <= 0.0
            }
            if (incomplete.isNotEmpty()) {
                val names = incomplete.joinToString(", ") {
                    it.preparationName.ifEmpty { it.ingredientName }.ifEmpty { "(sin nombre)" }
                }
                formError = "Falta el peso por unidad en: $names"
                selectedTab = 2
                return
            }
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

    Box(Modifier.fillMaxSize()) {
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
                // Tabs según el tipo: la reventa es simple (todo lo suyo —costo, proveedor,
                // precio de venta— vive en General) más la cascada de Precios; producido en
                // planta suma Adornos/Organoléptico/Ficha técnica.
                val tabTitles = if (isInHouseProduced)
                    listOf("General", "Precios", "Adornos", "Organoléptico", "Ficha técnica")
                else
                    listOf("General", "Precios")
                val currentTab = selectedTab.coerceIn(0, tabTitles.lastIndex)

                TabRow(selectedTabIndex = currentTab) {
                    tabTitles.forEachIndexed { i, title ->
                        Tab(
                            selected = currentTab == i,
                            onClick = { selectedTab = i },
                            text = { Text(title) }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (tabTitles[currentTab]) {
                            "General" -> {
                                GeneralTabContent(
                                isInHouseProduced = isInHouseProduced,
                                name = name, onNameChange = { name = it; nameError = false },
                                nameError = nameError,
                                description = description, onDescriptionChange = { description = it },
                                category = category, onCategoryChange = { category = it },
                                categoryOptions = PRODUCT_CATEGORIES,
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
                                isPerishable = isPerishable, onIsPerishableChange = { isPerishable = it },
                                unitsPerTray = unitsPerTray, onUnitsPerTrayChange = { unitsPerTray = it },
                                rawWeightInput = rawWeightInput, onRawWeightInputChange = { rawWeightInput = it },
                                rawWeightUnit = rawWeightUnit, onRawWeightUnitChange = { rawWeightUnit = it },
                                rawWeightUnitExpanded = rawWeightUnitExpanded,
                                onRawWeightUnitExpandedChange = { rawWeightUnitExpanded = it },
                                storageType = storageType, onStorageTypeChange = { storageType = it },
                                notes = notes, onNotesChange = { notes = it },
                                purchaseFormats = purchaseFormats,
                                onPurchaseFormatsChange = { purchaseFormats = it },
                                suppliers = supplierState.supplierList.filter { it.active },
                                barcode = barcode, onBarcodeChange = { barcode = it },
                                // Solo materia prima: vincular una venta a un suministro de
                                // limpieza no significa nada.
                                ingredients = ingredientState.ingredientList.usableInRecipes(),
                                linkedIngredientId = linkedIngredientId,
                                linkedIngredientName = linkedIngredientName,
                                onLinkedIngredientChange = { id, nm ->
                                    linkedIngredientId = id; linkedIngredientName = nm
                                },
                                ingredientUnitsPerSale = ingredientUnitsPerSale,
                                onIngredientUnitsPerSaleChange = { ingredientUnitsPerSale = it }
                            )
                                ProductionRoutingSection(
                                    isInHouseProduced = isInHouseProduced,
                                    onIsInHouseChange = { isInHouseProduced = it }
                                )
                                if (isInHouseProduced) {
                                    PortionOfSection(
                                        // Un entero nuestro, que no sea otra porción ni éste mismo.
                                        wholes = state.productList.filter {
                                            it.active && it.isInHouseProduced && !it.isPortion &&
                                                it.id != existingProduct?.id
                                        }.sortedBy { it.name.lowercase() },
                                        portionOfProductId = portionOfProductId,
                                        portionOfProductName = portionOfProductName,
                                        onPortionOfChange = { id, nm ->
                                            portionOfProductId = id; portionOfProductName = nm
                                        },
                                        portionsPerWhole = portionsPerWhole,
                                        onPortionsPerWholeChange = { portionsPerWhole = it.filter(Char::isDigit).take(3) }
                                    )
                                }
                            }
                            "Precios" -> {
                                PricesTabContent(
                                    lists = activeLists,
                                    retailDefaultId = retailDefaultId,
                                    salePrice = salePrice,
                                    priceInputs = priceInputs,
                                    listsWithExistingOverride = listsWithExistingOverride,
                                    onPriceChange = { listId, value ->
                                        priceInputs = priceInputs + (listId to value)
                                    }
                                )
                                SectionDivider()
                                OutlinedTextField(
                                    value = employeeDiscount,
                                    onValueChange = { employeeDiscount = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("Descuento a colaboradores (%)") },
                                    suffix = { Text("%") },
                                    singleLine = true,
                                    supportingText = {
                                        Text("Se aplica cuando un colaborador con derecho lo compra y paga en efectivo. Vacío = sin descuento.")
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                )
                            }
                            "Adornos" -> {
                                DecorationsTabContent(
                                    decorations = decorations,
                                    onDecorationsChange = { decorations = it },
                                    availableDecorations = preparationState.preparationList.filter {
                                        it.active && it.usage == PreparationUsage.DECORATION
                                    },
                                    availableIngredients = ingredientState.ingredientList.filter { it.active }
                                )
                                SectionDivider()
                                DoughFillingsTabContent(
                                    doughFillings = doughFillings,
                                    onDoughFillingsChange = { doughFillings = it },
                                    availableRecipes = recipeState.recipeList.filter {
                                        it.active && it.id != ownRecipeId
                                    }
                                )
                            }
                            "Organoléptico" -> OrganolepticTabContent(
                                crustColor = crustColor, onCrustColorChange = { crustColor = it },
                                crumbColor = crumbColor, onCrumbColorChange = { crumbColor = it },
                                texture = texture, onTextureChange = { texture = it },
                                shape = shape, onShapeChange = { shape = it },
                                aroma = aroma, onAromaChange = { aroma = it },
                                flavorProfile = flavorProfile, onFlavorProfileChange = { flavorProfile = it }
                            )
                            "Ficha técnica" -> TechnicalSheetTabContent(
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

    // Al crear un producto nuevo, primero se elige el tipo (producido vs reventa).
    if (!typeChosen) {
        ProductTypeChoiceDialog(
            onChoose = { inHouse ->
                isInHouseProduced = inHouse
                // La reventa tiene una sola categoría: no hay selector, se asigna aquí.
                if (!inHouse) category = RESALE_CATEGORY
                selectedTab = 0
                typeChosen = true
            },
            onDismiss = onNavigateBack
        )
    }

    LoadingOverlay(visible = state.isLoading)
    }
}

// ── Diálogo: tipo de producto (al crear) ─────────────────────────────────────

@Composable
private fun ProductTypeChoiceDialog(
    onChoose: (isInHouseProduced: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "¿Qué tipo de producto vas a crear?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                ProductTypeOption(
                    title = "Producido en planta",
                    description = "Lo horneamos/elaboramos nosotros. Lleva receta, adornos, características y ficha técnica.",
                    onClick = { onChoose(true) }
                )
                ProductTypeOption(
                    title = "Reventa",
                    description = "Lo compramos a un proveedor y lo vendemos sin transformar (huevos, café en bolsa, tostadas…).",
                    onClick = { onChoose(false) }
                )
                Spacer(Modifier.height(2.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancelar") }
            }
        }
    }
}

@Composable
private fun ProductTypeOption(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Tab: General ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralTabContent(
    isInHouseProduced: Boolean,
    name: String, onNameChange: (String) -> Unit, nameError: Boolean,
    description: String, onDescriptionChange: (String) -> Unit,
    category: String, onCategoryChange: (String) -> Unit,
    categoryOptions: List<String>,
    categoryExpanded: Boolean, onCategoryExpandedChange: (Boolean) -> Unit,
    active: Boolean, onActiveChange: (Boolean) -> Unit,
    existingImageUrl: String?,
    selectedImageBytes: ByteArray?,
    onImageSelected: (ByteArray) -> Unit,
    salePrice: String, onSalePriceChange: (String) -> Unit, salePriceError: Boolean,
    unit: String, onUnitChange: (String) -> Unit,
    weightPerUnit: String, onWeightPerUnitChange: (String) -> Unit,
    shelfLife: String, onShelfLifeChange: (String) -> Unit,
    isPerishable: Boolean, onIsPerishableChange: (Boolean) -> Unit,
    unitsPerTray: String, onUnitsPerTrayChange: (String) -> Unit,
    rawWeightInput: String, onRawWeightInputChange: (String) -> Unit,
    rawWeightUnit: String, onRawWeightUnitChange: (String) -> Unit,
    rawWeightUnitExpanded: Boolean, onRawWeightUnitExpandedChange: (Boolean) -> Unit,
    storageType: String, onStorageTypeChange: (String) -> Unit,
    notes: String, onNotesChange: (String) -> Unit,
    purchaseFormats: List<PurchaseFormat>,
    onPurchaseFormatsChange: (List<PurchaseFormat>) -> Unit,
    suppliers: List<Supplier>,
    barcode: String, onBarcodeChange: (String) -> Unit,
    ingredients: List<Ingredient>,
    linkedIngredientId: String, linkedIngredientName: String,
    onLinkedIngredientChange: (id: String, name: String) -> Unit,
    ingredientUnitsPerSale: String, onIngredientUnitsPerSaleChange: (String) -> Unit
) {
    // ── Reventa: layout dedicado y simple ──
    // Todo lo relevante (costo, precio, margen, proveedor) en una sola columna.
    // No hay categoría (siempre "Reventa"), ni peso en crudo, ni costo calculado.
    if (!isInHouseProduced) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GeneralInfoBlock(
                name, onNameChange, nameError,
                description, onDescriptionChange,
                category, onCategoryChange, categoryOptions,
                categoryExpanded, onCategoryExpandedChange,
                active, onActiveChange,
                showCategory = false
            )
            SectionDivider()
            ImageBlock(existingImageUrl, selectedImageBytes, onImageSelected)
            SectionDivider()
            ResaleCommercialBlock(
                salePrice, onSalePriceChange, salePriceError,
                unit, onUnitChange
            )
            SectionDivider()
            // De dónde sale el stock VA ANTES que las presentaciones, y no al revés: si el
            // producto vende del pozo de un insumo, la presentación de compra ya está en el
            // insumo y acá no hace falta ninguna. Al orden anterior se llenaba primero la
            // presentación y recién después se descubría el vínculo — o sea que se capturaba
            // dos veces lo mismo para que después la compra se redirigiera al insumo igual
            // (ver `resalePurchaseLine`).
            ResaleStockSourceBlock(
                barcode = barcode, onBarcodeChange = onBarcodeChange,
                ingredients = ingredients,
                linkedIngredientId = linkedIngredientId,
                linkedIngredientName = linkedIngredientName,
                onLinkedIngredientChange = onLinkedIngredientChange,
                unitsPerSale = ingredientUnitsPerSale,
                onUnitsPerSaleChange = onIngredientUnitsPerSaleChange
            )
            SectionDivider()
            if (linkedIngredientId.isEmpty()) {
                SectionHeader("Presentaciones de compra")
                ResalePurchaseFormatsSection(
                    formats = purchaseFormats,
                    saleUnit = unit.trim(),
                    salePrice = salePrice,
                    suppliers = suppliers,
                    onFormatsChange = onPurchaseFormatsChange
                )
            } else {
                SectionHeader("Presentaciones de compra")
                Text(
                    "No hace falta definirlas acá: este producto se compra como insumo, así que " +
                        "la factura se captura en la sección de materia prima con las " +
                        "presentaciones de «${linkedIngredientName.ifEmpty { "el insumo" }}». " +
                        "Aunque lo captures desde reventa, la compra entra al inventario del " +
                        "insumo igual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SectionDivider()
            ResaleDataBlock(
                shelfLife, onShelfLifeChange, storageType, onStorageTypeChange,
                isPerishable, onIsPerishableChange
            )
            SectionDivider()
            NotesBlock(notes, onNotesChange)
        }
        return
    }

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
                        category, onCategoryChange, categoryOptions,
                        categoryExpanded, onCategoryExpandedChange,
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
                        showRawWeight = true,
                        weightPerUnit, onWeightPerUnitChange,
                        shelfLife, onShelfLifeChange,
                        unitsPerTray, onUnitsPerTrayChange,
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
                    category, onCategoryChange, categoryOptions,
                    categoryExpanded, onCategoryExpandedChange,
                    active, onActiveChange
                )
                SectionDivider()
                ImageBlock(existingImageUrl, selectedImageBytes, onImageSelected)
                SectionDivider()
                CommercialBlock(salePrice, onSalePriceChange, salePriceError, unit, onUnitChange)
                SectionDivider()
                ProductDataBlock(
                    showRawWeight = true,
                    weightPerUnit, onWeightPerUnitChange,
                    shelfLife, onShelfLifeChange,
                    unitsPerTray, onUnitsPerTrayChange,
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
    categoryOptions: List<String>,
    categoryExpanded: Boolean, onCategoryExpandedChange: (Boolean) -> Unit,
    active: Boolean, onActiveChange: (Boolean) -> Unit,
    showCategory: Boolean = true
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

    if (showCategory) {
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
                categoryOptions.forEach { option ->
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

// ── Bloque comercial para reventa: precio + presentación de venta ─────────────
// El costo ya no se teclea acá: sale de las presentaciones de compra, que es donde se ve
// el margen (ver `ResalePurchaseFormatsSection`).
@Composable
private fun ResaleCommercialBlock(
    salePrice: String, onSalePriceChange: (String) -> Unit, salePriceError: Boolean,
    unit: String, onUnitChange: (String) -> Unit
) {
    SectionHeader("Precio de venta")

    OutlinedTextField(
        value = salePrice,
        onValueChange = onSalePriceChange,
        label = { Text("Precio de venta *") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        prefix = { Text("Q ") },
        isError = salePriceError,
        supportingText = {
            if (salePriceError) Text("El precio de venta es requerido")
            else Text("Precio al que lo vendes al cliente")
        }
    )

    OutlinedTextField(
        value = unit,
        onValueChange = onUnitChange,
        label = { Text("Se vende por") },
        placeholder = { Text("unidad, lata, bolsa…") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = { Text("Es la unidad en que se cuenta el inventario y en que se arman las presentaciones") }
    )
}

// ── Datos del producto para reventa: solo vida útil + almacenamiento ─────────
@Composable
private fun ResaleDataBlock(
    shelfLife: String, onShelfLifeChange: (String) -> Unit,
    storageType: String, onStorageTypeChange: (String) -> Unit,
    isPerishable: Boolean, onIsPerishableChange: (Boolean) -> Unit
) {
    SectionHeader("Datos del Producto")

    // Mismo switch y mismo texto que en insumos: son la misma decisión.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Caduca", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Al comprarlo se pedirá el lote y la fecha de vencimiento",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = isPerishable, onCheckedChange = onIsPerishableChange)
    }

    OutlinedTextField(
        value = shelfLife,
        onValueChange = onShelfLifeChange,
        label = { Text("Vida útil (días)") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )

    OutlinedTextField(
        value = storageType,
        onValueChange = onStorageTypeChange,
        label = { Text("Tipo de almacenamiento") },
        placeholder = { Text("temperatura ambiente, refrigerado…") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductDataBlock(
    showRawWeight: Boolean,
    weightPerUnit: String, onWeightPerUnitChange: (String) -> Unit,
    shelfLife: String, onShelfLifeChange: (String) -> Unit,
    unitsPerTray: String, onUnitsPerTrayChange: (String) -> Unit,
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

    OutlinedTextField(
        value = unitsPerTray,
        onValueChange = { txt -> if (txt.all { it.isDigit() }) onUnitsPerTrayChange(txt) },
        label = { Text("Unidades por bandeja") },
        placeholder = { Text("Ej. 40") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        supportingText = {
            Text(
                if (unitsPerTray.toIntOrNull()?.let { it > 0 } == true)
                    "\"Registrar conteo\" va a poder contar en bandejas + sueltos"
                else "Déjalo vacío si no aplica: el conteo se hará solo en unidades netas"
            )
        }
    )

    if (showRawWeight) {
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

/**
 * De dónde sale el stock de un producto de reventa, y con qué código se le encuentra.
 *
 * El caso que resuelve es el huevo: se compra una vez como insumo, entra a las recetas y
 * además se vende por docena en el mostrador. Vincularlo hace que sea UN solo inventario —
 * es una sola caja de huevos, y comprarla dos veces para tenerla en dos inventarios es lo
 * que dejaba los conteos sin cuadrar.
 */
@Composable
private fun ResaleStockSourceBlock(
    barcode: String, onBarcodeChange: (String) -> Unit,
    ingredients: List<Ingredient>,
    linkedIngredientId: String,
    linkedIngredientName: String,
    onLinkedIngredientChange: (id: String, name: String) -> Unit,
    unitsPerSale: String, onUnitsPerSaleChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val linked = linkedIngredientId.isNotEmpty()
    val ingredient = remember(linkedIngredientId, ingredients) {
        ingredients.find { it.id == linkedIngredientId }
    }
    val baseUnit = ingredient?.effectiveBaseUnit.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Identificación e inventario", style = MaterialTheme.typography.titleSmall)

        BarcodeField(
            value = barcode,
            onValueChange = onBarcodeChange,
            supportingText = "Opcional. El mismo código del empaque sirve para comprarlo y para cobrarlo."
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("También se compra como insumo", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (linked)
                        "Vende del stock de ${linkedIngredientName.ifEmpty { "un insumo" }}"
                    else "Actívalo si es algo que ya entra a tus recetas (huevo, azúcar por libra).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = linked,
                onCheckedChange = { on ->
                    if (on) showPicker = true else onLinkedIngredientChange("", "")
                }
            )
        }

        if (linked) {
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(linkedIngredientName.ifEmpty { "Elegir insumo" })
            }
            // Sin este número la venta no sabe cuánto bajar: una docena son 12 unidades del
            // insumo, y descontar 1 dejaría el inventario contando docenas que no existen.
            OutlinedTextField(
                value = unitsPerSale,
                onValueChange = onUnitsPerSaleChange,
                label = { Text("${baseUnit.ifEmpty { "unidades" }} por cada venta *") },
                placeholder = { Text("Ej: 12 si vendes por docena") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = {
                    Text(
                        "Cuánto se descuenta de «${linkedIngredientName.ifEmpty { "el insumo" }}» " +
                            "al vender una unidad de este producto."
                    )
                }
            )
            Text(
                text = "Este producto no lleva inventario de tienda propio: el mostrador y la " +
                    "producción salen del mismo stock.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPicker) {
        IngredientPickerDialog(
            ingredients = ingredients,
            onPick = { ing ->
                onLinkedIngredientChange(ing.id, ing.name)
                if (unitsPerSale.isBlank()) onUnitsPerSaleChange("1")
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * "Es porción de…": el producto que se vende cortado ("Porción de selva negra") apunta a su
 * pastel entero. Así la tienda puede cortar un entero que no se vendió y el inventario baja
 * 1 del pastel y sube N porciones (ver `model/Portioning.kt`).
 */
@Composable
private fun PortionOfSection(
    wholes: List<Product>,
    portionOfProductId: String,
    portionOfProductName: String,
    onPortionOfChange: (id: String, name: String) -> Unit,
    portionsPerWhole: String,
    onPortionsPerWholeChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val linked = portionOfProductId.isNotEmpty()

    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Es porción de un pastel",
                    color = BrandColors.Primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (linked)
                        "Se obtiene cortando un ${portionOfProductName.ifEmpty { "entero" }} en la tienda. " +
                            "No se le pide a planta."
                    else "Actívalo para «Porción de selva negra» y similares: la tienda corta el " +
                        "entero que no se vendió y el inventario pasa del pastel a las porciones.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = linked,
                onCheckedChange = { on -> if (on) showPicker = true else onPortionOfChange("", "") }
            )
        }
        if (linked) {
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(portionOfProductName.ifEmpty { "Elegir pastel entero" })
            }
            OutlinedTextField(
                value = portionsPerWhole,
                onValueChange = onPortionsPerWholeChange,
                label = { Text("Porciones por pastel *") },
                placeholder = { Text("Ej: 8") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Lo normal. Al cortar se puede cambiar si salieron más o menos.") }
            )
        }
    }

    if (showPicker) {
        ProductPickerDialog(
            title = "¿De qué pastel es la porción?",
            products = wholes,
            stockOf = null,
            showSearch = true,
            onDismiss = { showPicker = false },
            onSelect = { p ->
                onPortionOfChange(p.id, p.name)
                if (portionsPerWhole.isBlank()) onPortionsPerWholeChange("8")
                showPicker = false
            }
        )
    }
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
    availableDecorations: List<Preparation>,
    availableIngredients: List<Ingredient>
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        DecorationPickerDialog(
            preparations = availableDecorations.filter { p ->
                decorations.none { it.preparationId == p.id }
            },
            ingredients = availableIngredients.filter { i ->
                decorations.none { it.ingredientId == i.id }
            },
            onPickPreparation = { prep ->
                onDecorationsChange(
                    decorations + ProductDecoration(
                        preparationId = prep.id,
                        preparationName = prep.name,
                        gramsPerUnit = 0.0
                    )
                )
                showPicker = false
            },
            onPickIngredient = { ing ->
                onDecorationsChange(
                    decorations + ProductDecoration(
                        ingredientId = ing.id,
                        ingredientName = ing.name,
                        gramsPerUnit = 0.0
                    )
                )
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }

    SectionHeader("Adornos y rellenos del producto")
    Text(
        "Lo que se aplica al producto terminado, además de la masa. Puede ser algo que " +
            "preparamos nosotros (concha, relleno de esponja) o un insumo que ya compramos " +
            "hecho (relleno de higo en manga, mermelada). Indica los gramos por unidad — " +
            "al producir, el sistema descuenta automáticamente.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (availableDecorations.isEmpty() && availableIngredients.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "No hay preparaciones tipo \"adorno\" ni insumos registrados. Crea una preparación " +
                "(eligiendo \"Para adorno\") o un insumo para poder usarlo aquí.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    Spacer(Modifier.height(8.dp))

    if (decorations.isEmpty()) {
        Text(
            "Sin adornos ni rellenos registrados.",
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
                            // Coma o punto: el resto de los campos numéricos del formulario
                            // ya aceptan ambos. Sin este replace, escribir "40,5" resetea
                            // el campo a 0 y el adorno se descarta en silencio al guardar.
                            if (i == idx) d.copy(gramsPerUnit = grams.replace(',', '.').toDoubleOrNull() ?: 0.0)
                            else d
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
        enabled = availableDecorations.any { p -> decorations.none { it.preparationId == p.id } } ||
            availableIngredients.any { i -> decorations.none { it.ingredientId == i.id } },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Agregar adorno o relleno")
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = decoration.preparationName.ifEmpty { decoration.ingredientName }.ifEmpty { "Sin nombre" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (decoration.isIngredientSourced)
                        "Insumo — se compra ya hecho, se descuenta de Compras"
                    else "Preparación — hay que producir su lote antes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

/**
 * Selector combinado de adornos: preparaciones (las hacemos nosotros, requieren
 * producir un lote antes) e insumos (se compran ya hechos — relleno de higo,
 * queso crema, mermeladas). Ambas listas comparten un solo buscador.
 */
@Composable
private fun DecorationPickerDialog(
    preparations: List<Preparation>,
    ingredients: List<Ingredient>,
    onPickPreparation: (Preparation) -> Unit,
    onPickIngredient: (Ingredient) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val sortedPreps = remember(preparations) { preparations.sortedBy { it.name.lowercase() } }
    val sortedIngredients = remember(ingredients) { ingredients.sortedBy { it.name.lowercase() } }
    val filteredPreps = remember(searchQuery, sortedPreps) {
        if (searchQuery.isBlank()) sortedPreps
        else sortedPreps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val filteredIngredients = remember(searchQuery, sortedIngredients) {
        if (searchQuery.isBlank()) sortedIngredients
        else sortedIngredients.filter { it.name.contains(searchQuery, ignoreCase = true) }
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
                    text = "Seleccionar adorno o relleno",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (preparations.isEmpty() && ingredients.isEmpty()) {
                    Text(
                        "Ya agregaste todos los adornos e insumos disponibles.",
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
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        if (filteredPreps.isNotEmpty()) {
                            item {
                                Text(
                                    "Preparaciones — las hacemos nosotros",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            items(filteredPreps, key = { "p_" + it.id }) { prep ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPickPreparation(prep) }
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
                        if (filteredIngredients.isNotEmpty()) {
                            item {
                                Text(
                                    "Insumos — se compran ya hechos",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            items(filteredIngredients, key = { "i_" + it.id }) { ing ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPickIngredient(ing) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = ing.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (ing.category.isNotBlank()) {
                                        Text(
                                            text = ing.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                        if (filteredPreps.isEmpty() && filteredIngredients.isEmpty()) {
                            item {
                                Text(
                                    "Sin resultados para \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
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

// ── Tab: Relleno de otra masa ───────────────────────────────────────────────

/**
 * Distinto de [DecorationsTabContent]: acá el "relleno" no es una preparación
 * con su propio lote — es masa cruda de OTRA receta (ver [DoughFilling]), la
 * misma que también se usa para hornear los productos propios de esa receta.
 * Por eso se elige una RECETA, no una preparación, y el peso declarado se
 * suma a la demanda de esa masa en producción en vez de descontarse de un
 * batch aparte.
 */
@Composable
private fun DoughFillingsTabContent(
    doughFillings: List<DoughFilling>,
    onDoughFillingsChange: (List<DoughFilling>) -> Unit,
    availableRecipes: List<Recipe>
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        DoughFillingRecipePickerDialog(
            recipes = availableRecipes.filter { r -> doughFillings.none { it.recipeId == r.id } },
            onPick = { recipe ->
                onDoughFillingsChange(
                    doughFillings + DoughFilling(recipeId = recipe.id, recipeName = recipe.name, rawWeightGrams = 0.0)
                )
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }

    SectionHeader("Relleno de otra masa")
    Text(
        "Para productos como el Pan de Capa: una bolita de masa Suave va adentro, sacada de " +
            "la MISMA amasada que también da los Pan Suave normales — no un lote aparte. Elegí " +
            "la receta y el peso en crudo de esa porción; el sistema lo suma a la masa de esa " +
            "receta cuando se pida este producto (mínimo de amasada, receta escalada e insumos).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (availableRecipes.isEmpty() && doughFillings.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "No hay otras recetas activas todavía.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    Spacer(Modifier.height(8.dp))

    if (doughFillings.isEmpty()) {
        Text(
            "Sin relleno de otra masa.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    } else {
        doughFillings.forEachIndexed { idx, filling ->
            DoughFillingRow(
                filling = filling,
                onGramsChange = { grams ->
                    onDoughFillingsChange(
                        doughFillings.mapIndexed { i, f ->
                            if (i == idx) f.copy(rawWeightGrams = grams.replace(',', '.').toDoubleOrNull() ?: 0.0)
                            else f
                        }
                    )
                },
                onRemove = {
                    onDoughFillingsChange(doughFillings.filterIndexed { i, _ -> i != idx })
                }
            )
            Spacer(Modifier.height(6.dp))
        }
    }

    Spacer(Modifier.height(4.dp))

    OutlinedButton(
        onClick = { showPicker = true },
        enabled = availableRecipes.any { r -> doughFillings.none { it.recipeId == r.id } },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Agregar relleno de otra masa")
    }
}

@Composable
private fun DoughFillingRow(
    filling: DoughFilling,
    onGramsChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    val gramsText = if (filling.rawWeightGrams > 0.0)
        if (filling.rawWeightGrams == filling.rawWeightGrams.toLong().toDouble())
            filling.rawWeightGrams.toLong().toString()
        else filling.rawWeightGrams.toString()
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = filling.recipeName.ifEmpty { "Sin nombre" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Masa de otra receta — se suma a su total, no a un lote aparte",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = gramsText,
                onValueChange = onGramsChange,
                label = { Text("g crudo") },
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
private fun DoughFillingRecipePickerDialog(
    recipes: List<Recipe>,
    onPick: (Recipe) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val sorted = remember(recipes) { recipes.sortedBy { it.name.lowercase() } }
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
                    text = "Seleccionar receta",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (recipes.isEmpty()) {
                    Text(
                        "Ya agregaste todas las recetas disponibles, o no hay otras recetas activas.",
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
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(filtered, key = { it.id }) { recipe ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(recipe) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = recipe.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    "Sin resultados para \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
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
        val autoFromFactor: Double? = if (!isRetailDefault) list.listRulePrice(retail) else null
        val stepHit = list.priceSteps.any { samePrice(it.publicPrice, retail) }
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
                    autoFromFactor != null && stepHit ->
                        "Escalón de la lista: lo de Q${retail.fmt(2)} sale a Q${autoFromFactor.fmt(2)}"
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
                        color = BrandColors.AccentDeep,
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
    val PrimaryBlue = BrandColors.Primary
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
