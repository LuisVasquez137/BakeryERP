package com.bakery.erp.presentation.products

import com.bakery.erp.ui.BrandTopAppBar

import com.bakery.erp.util.fmt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.ALL_PRODUCT_CATEGORIES
import com.bakery.erp.model.Permission
import com.bakery.erp.model.Product
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.ui.theme.BrandColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (productId: String) -> Unit,
    onNavigateToForm: () -> Unit,
    onNavigateToWeights: () -> Unit = {},
    authViewModel: AuthViewModel,
    viewModel: ProductViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val canEdit = authState.user?.hasPermission(Permission.EDIT_PRODUCTS) == true
    val snackbarHostState = remember { SnackbarHostState() }

    // null = "Todos"
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) viewModel.loadProducts(companyId)
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // Solo mostramos chips de categorías que realmente tienen productos (+ "Todos"),
    // respetando el orden canónico de ALL_PRODUCT_CATEGORIES.
    val presentCategories = remember(state.productList) {
        val existing = state.productList.map { it.category }.toSet()
        ALL_PRODUCT_CATEGORIES.filter { it in existing }
    }

    val filteredProducts = remember(state.productList, selectedCategory, searchQuery) {
        state.productList
            .filter { selectedCategory == null || it.category == selectedCategory }
            .filter {
                searchQuery.isBlank() ||
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
            }
            .sortedBy { it.name.lowercase() }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Productos Terminados", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // Atajo a la carga masiva. El contador de faltantes se
                    // muestra en badge: sin peso en crudo no hay receta escalada
                    // ni descuento de insumos, así que conviene que salte a la
                    // vista desde acá.
                    if (canEdit) {
                        val missing = state.productList.count {
                            it.active && (it.rawWeightGrams ?: 0.0) <= 0.0
                        }
                        BadgedBox(
                            badge = {
                                if (missing > 0) {
                                    Badge(
                                        containerColor = BrandColors.Accent,
                                        contentColor = BrandColors.OnAccent
                                    ) { Text("$missing", fontWeight = FontWeight.Bold) }
                                }
                            }
                        ) {
                            IconButton(onClick = onNavigateToWeights) {
                                Icon(
                                    Icons.Default.Scale,
                                    contentDescription = "Pesos en crudo",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = onNavigateToForm,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo producto", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.productList.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.productList.isEmpty() ->
                    EmptyProductsPlaceholder()

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    // Buscador
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        placeholder = { Text("Buscar producto…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )

                    // Chips de categoría (scroll horizontal)
                    CategoryChipsRow(
                        categories = presentCategories,
                        selected = selectedCategory,
                        onSelect = { selectedCategory = it }
                    )

                    if (filteredProducts.isEmpty()) {
                        NoMatchesPlaceholder()
                    } else {
                        // Responsive: tabla en pantallas grandes (tablet vertical → PC),
                        // listado de cards horizontales en móvil.
                        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (maxWidth >= 720.dp) {
                                ProductTable(
                                    products = filteredProducts,
                                    onTap = { onNavigateToDetail(it.id) }
                                )
                            } else {
                                ProductCardList(
                                    products = filteredProducts,
                                    onTap = { onNavigateToDetail(it.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChipsRow(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("Todos") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        categories.forEach { cat ->
            FilterChip(
                selected = selected == cat,
                onClick = { onSelect(if (selected == cat) null else cat) },
                label = { Text(cat) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

// ── Móvil: lista de cards horizontales (imagen izq. / info der.) ─────────────

@Composable
private fun ProductCardList(products: List<Product>, onTap: (Product) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(products, key = { it.id }) { product ->
            ProductRowCard(product = product, onClick = { onTap(product) })
        }
    }
}

@Composable
private fun ProductRowCard(product: Product, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductImage(
                imageUrl = product.imageUrl,
                contentDescription = product.name,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (product.category.isNotEmpty()) {
                    Text(
                        text = product.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Q ${product.salePrice.fmt(2)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (product.unit.isNotBlank()) {
                        Text(
                            text = " · ${product.unit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ── Pantallas grandes: tabla con filas clicables ─────────────────────────────

@Composable
private fun ProductTable(products: List<Product>, onTap: (Product) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Encabezado
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(52.dp)) // columna miniatura
            TableHeaderCell("Producto", Modifier.weight(2.2f))
            TableHeaderCell("Categoría", Modifier.weight(1.3f))
            TableHeaderCell("Presentación", Modifier.weight(1.2f))
            TableHeaderCell("Precio", Modifier.weight(1f), TextAlign.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(products, key = { it.id }) { product ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTap(product) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProductImage(
                        imageUrl = product.imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    TableCell(product.name, Modifier.weight(2.2f), fontWeight = FontWeight.SemiBold)
                    TableCell(product.category.ifBlank { "—" }, Modifier.weight(1.3f))
                    TableCell(product.unit.ifBlank { "—" }, Modifier.weight(1.2f))
                    TableCell(
                        "Q ${product.salePrice.fmt(2)}",
                        Modifier.weight(1f),
                        textAlign = TextAlign.End,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun TableHeaderCell(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = textAlign
    )
}

@Composable
private fun TableCell(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = fontWeight,
        color = color,
        textAlign = textAlign,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun NoMatchesPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Sin resultados",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Prueba con otra búsqueda o categoría",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun EmptyProductsPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.BakeryDining,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Sin productos aún",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Toca + para agregar tu primer producto",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
