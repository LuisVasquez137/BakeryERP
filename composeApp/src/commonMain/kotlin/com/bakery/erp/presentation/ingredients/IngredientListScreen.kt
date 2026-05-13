package com.bakery.erp.presentation.ingredients

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.INGREDIENT_CATEGORIES
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Permission
import com.bakery.erp.model.formatStockDisplay
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (ingredientId: String) -> Unit,
    onNavigateToForm: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: IngredientViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val currentUser = authState.user
    val canEdit = currentUser?.hasPermission(Permission.EDIT_INGREDIENTS) == true
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(companyId, currentUser) {
        if (companyId.isNotEmpty() && currentUser != null) {
            viewModel.loadIngredients(companyId)
            viewModel.loadAccessibleBranches(companyId, currentUser)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val displayed = remember(state.ingredientList, searchQuery, selectedCategory) {
        state.ingredientList
            .filter { selectedCategory == null || it.category == selectedCategory }
            .filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Materias Primas", fontWeight = FontWeight.SemiBold) },
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
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = onNavigateToForm,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Nuevo ingrediente",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.ingredientList.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    // â”€â”€ Barra de bÃºsqueda estilo Google â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .shadow(4.dp, RoundedCornerShape(28.dp)),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text("Buscar insumoâ€¦", color = MaterialTheme.colorScheme.outline)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Limpiar",
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // â”€â”€ Filtro de sucursal (solo si hay mÃ¡s de una accesible) â”€
                    if (state.accessibleBranches.size > 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(state.accessibleBranches) { branch ->
                                FilterChip(
                                    selected = state.selectedBranch?.id == branch.id,
                                    onClick = { viewModel.selectBranch(companyId, branch) },
                                    label = { Text(branch.name) }
                                )
                            }
                        }
                    }
                    if (state.isBranchStocksLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    // â”€â”€ Filtro de categorÃ­as â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            CategoryChip(
                                label = "Todos",
                                icon = Icons.Default.Apps,
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null }
                            )
                        }
                        items(INGREDIENT_CATEGORIES) { cat ->
                            CategoryChip(
                                label = cat,
                                icon = CATEGORY_ICONS[cat] ?: Icons.Default.Category,
                                selected = selectedCategory == cat,
                                onClick = {
                                    selectedCategory = if (selectedCategory == cat) null else cat
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    if (displayed.isEmpty() && !state.isLoading) {
                        EmptyIngredientsPlaceholder(
                            searching = searchQuery.isNotEmpty() || selectedCategory != null
                        )
                    } else {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val isWide = maxWidth >= 600.dp
                            if (isWide) {
                                LazyColumn(
                                    contentPadding = PaddingValues(bottom = 80.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    item { IngredientTableHeader() }
                                    items(displayed, key = { it.id }) { ingredient ->
                                        IngredientTableRow(
                                            ingredient = ingredient,
                                            branchStock = if (state.selectedBranch != null)
                                                state.selectedBranchStockMap[ingredient.id]
                                            else null,
                                            onClick = { onNavigateToDetail(ingredient.id) }
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(
                                        start = 12.dp, end = 12.dp, bottom = 80.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(displayed, key = { it.id }) { ingredient ->
                                        IngredientListCard(
                                            ingredient = ingredient,
                                            branchStock = if (state.selectedBranch != null)
                                                state.selectedBranchStockMap[ingredient.id]
                                            else null,
                                            onClick = { onNavigateToDetail(ingredient.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// â”€â”€ Category chip â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun CategoryChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    else
        MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurfaceVariant
    val borderMod = if (selected)
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
    else
        Modifier

    Surface(
        modifier = Modifier
            .then(borderMod)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

// â”€â”€ Ingredient card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun IngredientListCard(ingredient: Ingredient, branchStock: Double?, onClick: () -> Unit) {
    val group = CATEGORY_GROUPS[ingredient.category]
    val containerColor = groupContainerColor(group)
    val contentColor = groupContentColor(group)
    val icon = categoryIcon(ingredient.category)

    val defaultFormat = ingredient.purchaseFormats.firstOrNull { it.isDefault }
        ?: ingredient.purchaseFormats.firstOrNull()
    val status = stockStatus(branchStock, ingredient)

    val packageLabel = if (defaultFormat != null && defaultFormat.packageCost > 0) {
        val word = defaultFormat.description.split(" ").firstOrNull()?.take(10) ?: ""
        if (word.isNotEmpty()) "$word  Q${"%.0f".format(defaultFormat.packageCost)}" else ""
    } else ""

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(containerColor, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(26.dp))
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ingredient.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                    if (ingredient.category.isNotEmpty()) {
                        Spacer(Modifier.height(5.dp))
                        Surface(shape = RoundedCornerShape(50), color = containerColor) {
                            Text(
                                text = ingredient.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    if (ingredient.defaultUnitCost > 0) {
                        Text(
                            text = "Q ${"%.2f".format(ingredient.defaultUnitCost)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "/ ${ingredient.effectiveBaseUnit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (packageLabel.isNotEmpty()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = packageLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "Sin precio",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                StockBadge(status)
                Spacer(Modifier.weight(1f))
                if (branchStock != null) {
                    Text(
                        text = formatStockDisplay(branchStock, ingredient),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// â”€â”€ Empty placeholder â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

// ── Tabla de insumos (pantallas anchas) ──────────────────────────────────────

@Composable
private fun IngredientTableHeader() {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Insumo",
                modifier = Modifier.weight(2.5f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Precio unit.",
                modifier = Modifier.weight(1.2f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Presentación ref.",
                modifier = Modifier.weight(1.5f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Stock",
                modifier = Modifier.weight(1.3f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun IngredientTableRow(ingredient: Ingredient, branchStock: Double?, onClick: () -> Unit) {
    val group = CATEGORY_GROUPS[ingredient.category]
    val containerColor = groupContainerColor(group)
    val contentColor = groupContentColor(group)
    val icon = categoryIcon(ingredient.category)

    val defaultFormat = ingredient.purchaseFormats.firstOrNull { it.isDefault }
        ?: ingredient.purchaseFormats.firstOrNull()
    val status = stockStatus(branchStock, ingredient)

    val packageLabel = if (defaultFormat != null && defaultFormat.packageCost > 0) {
        val word = defaultFormat.description.split(" ").firstOrNull()?.take(10) ?: ""
        if (word.isNotEmpty()) "$word  Q${"%.0f".format(defaultFormat.packageCost)}" else "—"
    } else "—"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Insumo: ícono + nombre + categoría
        Row(
            modifier = Modifier.weight(2.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(containerColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(
                    ingredient.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (ingredient.category.isNotEmpty()) {
                    Text(
                        ingredient.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor
                    )
                }
            }
        }

        // Precio unitario
        Column(modifier = Modifier.weight(1.2f)) {
            if (ingredient.defaultUnitCost > 0) {
                Text(
                    "Q ${"%.2f".format(ingredient.defaultUnitCost)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "/ ${ingredient.effectiveBaseUnit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Text(
                    "Sin precio",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Presentación referencia
        Text(
            packageLabel,
            modifier = Modifier.weight(1.5f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Stock
        Row(
            modifier = Modifier.weight(1.3f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StockBadge(status)
            if (branchStock != null) {
                Text(
                    formatStockDisplay(branchStock, ingredient),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyIngredientsPlaceholder(searching: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Kitchen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (searching) "Sin resultados" else "Sin materias primas aÃºn",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (searching) "Intenta con otro nombre o categorÃ­a"
                   else "Toca + para agregar tu primer ingrediente",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
