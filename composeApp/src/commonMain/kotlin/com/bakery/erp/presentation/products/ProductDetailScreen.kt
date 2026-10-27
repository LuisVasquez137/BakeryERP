package com.bakery.erp.presentation.products

import com.bakery.erp.ui.BrandTopAppBar

import com.bakery.erp.util.fmt
import com.bakery.erp.util.gramsToOz
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.PriceList
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductPrice
import com.bakery.erp.model.PriceListType
import com.bakery.erp.model.samePrice
import com.bakery.erp.model.listRulePrice
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.pricing.PriceListViewModel
import com.bakery.erp.ui.ProductImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (productId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductViewModel,
    priceListViewModel: PriceListViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val priceListState by priceListViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val product = state.productList.find { it.id == productId }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) priceListViewModel.loadLists(companyId)
    }
    LaunchedEffect(companyId, productId) {
        if (companyId.isNotEmpty()) priceListViewModel.loadPricesForProduct(companyId, productId)
    }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) { viewModel.clearDeleted(); onNavigateBack() }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // Diálogo de confirmación de eliminación
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar producto") },
            text = {
                Text("¿Estás seguro que deseas eliminar \"${product?.name}\"? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        product?.let { viewModel.deleteProduct(companyId, it.id) }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = {
                    Text(
                        text = product?.name ?: "Detalle",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { product?.let { onNavigateToEdit(it.id) } }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                product == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Producto no encontrado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> ProductDetailContent(
                    product = product,
                    priceLists = priceListState.lists,
                    pricesForProduct = priceListState.pricesForProduct
                )
            }
        }
    }
}

@Composable
private fun ProductDetailContent(
    product: Product,
    priceLists: List<PriceList>,
    pricesForProduct: List<ProductPrice>
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 700.dp
        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Info izquierda (scrollable)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ProductInfoSection(product, priceLists, pricesForProduct)
                }
                // Imagen derecha (fija)
                Box(
                    modifier = Modifier
                        .width(400.dp)
                        .fillMaxSize()
                        .padding(start = 8.dp, end = 24.dp, top = 24.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    ProductImage(
                        imageUrl = product.imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                ProductImage(
                    imageUrl = product.imageUrl,
                    contentDescription = product.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ProductInfoSection(product, priceLists, pricesForProduct)
                }
            }
        }
    }
}

@Composable
private fun ProductInfoSection(
    product: Product,
    priceLists: List<PriceList>,
    pricesForProduct: List<ProductPrice>
) {
    // Nombre + badges
    Text(
        text = product.name,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (product.category.isNotEmpty()) {
            CategoryBadge(product.category)
        }
        StatusBadge(product.active)
    }

    Spacer(Modifier.height(8.dp))

    if (product.description.isNotEmpty()) {
        Text(
            text = product.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }

    // ── Comercial ────────────────────────────────────────────────────
    DetailSection("Datos Comerciales")

    DetailRow(label = "Precio venta", value = "Q ${product.salePrice.fmt(2)}")
    if (product.unit.isNotEmpty()) {
        DetailRow(label = "Presentación", value = product.unit)
    }
    DetailRow(
        label = "Costo unitario",
        value = "Calculado por el sistema",
        valueColor = MaterialTheme.colorScheme.outline
    )

    // ── Precios por lista ────────────────────────────────────────────
    // Misma cascada que la pestaña "Precios" del form (override > factor ×
    // retail > sin precio) — acá solo de lectura, para ver de un vistazo lo
    // que va a cobrar cada lista sin tener que entrar a editar.
    if (priceLists.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        DetailSection("Precios por Lista")
        priceLists.forEach { list ->
            val isRetailDefault = list.type == PriceListType.RETAIL && list.isDefault
            val override = pricesForProduct.find { it.priceListId == list.id }?.unitPrice?.takeIf { it > 0 }
            val autoFromRule = list.listRulePrice(product.salePrice)
            val stepHit = list.priceSteps.any { samePrice(it.publicPrice, product.salePrice) }
            val (value, sourceHint) = when {
                isRetailDefault -> product.salePrice to null
                override != null -> override to "fijo"
                autoFromRule != null && stepHit -> autoFromRule to "escalón de la lista"
                autoFromRule != null -> autoFromRule to "${(list.priceFactor!! * 100).toInt()}% del retail"
                else -> null to null
            }
            DetailRow(
                label = list.name + if (sourceHint != null) " ($sourceHint)" else "",
                value = value?.let { "Q ${it.fmt(2)}" } ?: "Sin precio definido",
                valueColor = if (value == null) MaterialTheme.colorScheme.error
                             else MaterialTheme.colorScheme.onSurface
            )
        }
    }

    // ── Datos del producto ───────────────────────────────────────────
    val hasPhysicalData = product.weightPerUnit != null || product.rawWeightGrams != null || product.shelfLife != null || !product.storageType.isNullOrEmpty()
    if (hasPhysicalData) {
        Spacer(Modifier.height(4.dp))
        DetailSection("Datos del Producto")
        product.weightPerUnit?.let { DetailRow("Peso por unidad", "${it.fmt(2)} oz") }
        product.rawWeightGrams?.let { grams ->
            val displayUnit = product.rawWeightUnit ?: "g"
            val displayValue =
                if (displayUnit == "oz") "${grams.gramsToOz().fmt(2)} oz"
                else "${grams.fmt(0)} g"
            DetailRow("Peso en crudo", displayValue)
        }
        product.shelfLife?.let { DetailRow("Vida útil", "$it días") }
        product.storageType?.let { if (it.isNotEmpty()) DetailRow("Almacenamiento", it) }
    }

    // ── Adornos ──────────────────────────────────────────────────────
    if (product.decorations.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        DetailSection("Adornos")
        product.decorations.forEach { deco ->
            DetailRow(
                label = deco.preparationName.ifEmpty { deco.ingredientName },
                value = if (deco.isCounted) {
                    val n = kotlin.math.round(1.0 / deco.unitsPerProduct).toLong()
                    if (n == 1L) "1 bizcocho" else "1/$n del molde"
                } else "${deco.gramsPerUnit.fmt(1)} g / unidad"
            )
        }
    }

    // ── Relleno de otra masa ────────────────────────────────────────────
    if (product.doughFillings.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        DetailSection("Relleno de otra masa")
        product.doughFillings.forEach { filling ->
            DetailRow(
                label = filling.recipeName,
                value = "${filling.rawWeightGrams.fmt(1)} g crudo"
            )
        }
    }

    // ── Características organolépticas ───────────────────────────────
    val ch = product.characteristics
    val hasCharacteristics = listOfNotNull(
        ch.crustColor, ch.crumbColor, ch.texture, ch.shape, ch.aroma, ch.flavorProfile
    ).any { it.isNotEmpty() }

    if (hasCharacteristics) {
        Spacer(Modifier.height(4.dp))
        DetailSection("Características Organolépticas")
        if (!ch.crustColor.isNullOrEmpty()) DetailRow("Color corteza", ch.crustColor)
        if (!ch.crumbColor.isNullOrEmpty()) DetailRow("Color miga", ch.crumbColor)
        if (!ch.texture.isNullOrEmpty()) DetailRow("Textura", ch.texture)
        if (!ch.shape.isNullOrEmpty()) DetailRow("Forma", ch.shape)
        if (!ch.aroma.isNullOrEmpty()) DetailRow("Aroma", ch.aroma)
        if (!ch.flavorProfile.isNullOrEmpty()) DetailRow("Perfil de sabor", ch.flavorProfile)
    }

    // ── Ficha técnica de producción ──────────────────────────────────
    val ts = product.technicalSheet
    val hasTechnicalSheet = listOfNotNull(
        ts.bakingTemperatureCelsius, ts.bakingTimeMinutes,
        ts.fermentationTimeHours, ts.fermentationTemperatureCelsius,
        ts.hydrationPercentage
    ).isNotEmpty() || !ts.shapingNotes.isNullOrEmpty() || !ts.bakingNotes.isNullOrEmpty()

    if (hasTechnicalSheet) {
        Spacer(Modifier.height(4.dp))
        DetailSection("Ficha Técnica de Producción")
        ts.bakingTemperatureCelsius?.let { DetailRow("Temperatura horneado", "$it °C") }
        ts.bakingTimeMinutes?.let { DetailRow("Tiempo horneado", "$it min") }
        ts.fermentationTimeHours?.let { DetailRow("Tiempo fermentación", "${it.fmt(1)} h") }
        ts.fermentationTemperatureCelsius?.let { DetailRow("Temperatura fermentación", "$it °C") }
        ts.hydrationPercentage?.let { DetailRow("Hidratación", "${it.fmt(1)} %") }
        if (!ts.shapingNotes.isNullOrEmpty()) DetailRow("Formado", ts.shapingNotes)
        if (!ts.bakingNotes.isNullOrEmpty()) DetailRow("Notas técnicas", ts.bakingNotes)
    }

    // ── Notas ────────────────────────────────────────────────────────
    if (!product.notes.isNullOrEmpty()) {
        Spacer(Modifier.height(4.dp))
        DetailSection("Notas")
        Text(
            text = product.notes,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(24.dp))
}

// ── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun DetailSection(title: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            modifier = Modifier.weight(0.55f)
        )
    }
}

@Composable
private fun CategoryBadge(category: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = category,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StatusBadge(active: Boolean) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = if (active) "Activo" else "Inactivo",
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
