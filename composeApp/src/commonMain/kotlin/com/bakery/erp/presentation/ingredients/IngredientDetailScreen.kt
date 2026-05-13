package com.bakery.erp.presentation.ingredients

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.unit.dp
import com.bakery.erp.domain.IngredientUsage
import com.bakery.erp.model.BranchStock
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.PurchaseFormat
import com.bakery.erp.model.StockMovement
import com.bakery.erp.model.User
import com.bakery.erp.model.convertUnits
import com.bakery.erp.model.formatStockDisplay
import com.bakery.erp.model.largestUnit
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.purchases.formatPurchaseDate

private val TABS = listOf("Inventario", "Presentaciones", "Recetas", "Kardex")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientDetailScreen(
    ingredientId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (ingredientId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: IngredientViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val currentUser = authState.user
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    val ingredient = state.ingredientList.find { it.id == ingredientId }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) { viewModel.clearDeleted(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar ingrediente") },
            text = {
                Text("¿Estás seguro que deseas eliminar \"${ingredient?.name}\"? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        ingredient?.let { viewModel.deleteIngredient(companyId, it.id) }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = ingredient?.name ?: "Detalle",
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
                    IconButton(onClick = { ingredient?.let { onNavigateToEdit(it.id) } }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error
                        )
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
                state.isLoading && ingredient == null ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                ingredient == null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Ingrediente no encontrado",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                else -> IngredientDetailContent(
                    ingredient = ingredient,
                    state = state,
                    companyId = companyId,
                    currentUser = currentUser,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    viewModel = viewModel
                )
            }
        }
    }
}

// â”€â”€ Layout principal: header fijo + tabs + contenido scrolleable â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun IngredientDetailContent(
    ingredient: Ingredient,
    state: IngredientUiState,
    companyId: String,
    currentUser: User?,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    viewModel: IngredientViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header (no scrollea)
        IngredientHeader(ingredient)

        // Tab row (sticky)
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp
        ) {
            TABS.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Contenido del tab (scrolleable)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                0 -> InventarioTab(
                    ingredient = ingredient,
                    companyId = companyId,
                    currentUser = currentUser,
                    state = state,
                    viewModel = viewModel
                )
                1 -> PresentacionesTab(ingredient)
                2 -> RecetasTab(
                    ingredientId = ingredient.id,
                    companyId = companyId,
                    state = state,
                    viewModel = viewModel
                )
                3 -> KardexTab(
                    ingredient = ingredient,
                    companyId = companyId,
                    currentUser = currentUser,
                    state = state,
                    viewModel = viewModel
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// â”€â”€ Header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun IngredientHeader(ingredient: Ingredient) {
    val group = CATEGORY_GROUPS[ingredient.category]
    val containerColor = groupContainerColor(group)
    val contentColor = groupContentColor(group)
    val icon = categoryIcon(ingredient.category)

    val defaultFormat = ingredient.purchaseFormats.firstOrNull { it.isDefault }
        ?: ingredient.purchaseFormats.firstOrNull()
    val packageLabel = if (defaultFormat != null && defaultFormat.packageCost > 0) {
        val word = defaultFormat.description.split(" ").firstOrNull()?.take(10) ?: ""
        if (word.isNotEmpty()) "$word  Q${"%.0f".format(defaultFormat.packageCost)}" else ""
    } else ""

    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
                Text(
                    text = ingredient.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ingredient.category.isNotEmpty()) {
                        Surface(color = containerColor, shape = RoundedCornerShape(50)) {
                            Text(
                                text = ingredient.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    StatusBadge(ingredient.active)
                }
                if (ingredient.defaultUnitCost > 0 || packageLabel.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (ingredient.defaultUnitCost > 0) {
                            Text(
                                text = "Q ${"%.2f".format(ingredient.defaultUnitCost)} / ${ingredient.effectiveBaseUnit}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (packageLabel.isNotEmpty()) {
                            Text(
                                text = "Â·",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = packageLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
        }

        if (!ingredient.notes.isNullOrEmpty()) {
            Text(
                text = ingredient.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// â”€â”€ Tab 0: Inventario â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun InventarioTab(
    ingredient: Ingredient,
    companyId: String,
    currentUser: User?,
    state: IngredientUiState,
    viewModel: IngredientViewModel
) {
    LaunchedEffect(ingredient.id) {
        if (companyId.isNotEmpty() && currentUser != null) {
            viewModel.loadIngredientBranchStocks(companyId, ingredient.id, currentUser)
        }
    }

    TabSectionTitle("Stock por sucursal")

    when {
        state.selectedIngredientStocks == null ->
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        state.selectedIngredientStocks!!.isEmpty() ->
            PlaceholderSection(
                icon = Icons.Default.EventNote,
                title = "Sin stock registrado",
                subtitle = "AparecerÃ¡ aquÃ­ cuando registres la primera compra de este insumo"
            )
        else -> {
            state.selectedIngredientStocks!!.forEach { bs ->
                BranchStockRow(bs, ingredient)
            }
        }
    }

    if (ingredient.stockAlert != null) {
        Spacer(Modifier.height(8.dp))
        TabSectionTitle("Alerta de stock mínimo")
        val defaultFormat = ingredient.purchaseFormats.firstOrNull { it.isDefault }
            ?: ingredient.purchaseFormats.firstOrNull()
        val alertUnit = if (defaultFormat != null) defaultFormat.description
                        else largestUnit(ingredient.allowedUnits)
        DetailInfoRow(
            label = "Nivel de alerta",
            value = "${"%.2f".format(ingredient.stockAlert).trimEnd('0').trimEnd('.')} $alertUnit"
        )
    }
}

@Composable
private fun BranchStockRow(bs: BranchStock, ingredient: Ingredient) {
    val status = stockStatus(bs.currentStock, ingredient)
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(bs.branchName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                StockBadge(status)
            }
            Text(
                text = formatStockDisplay(bs.currentStock, ingredient),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// â”€â”€ Tab 1: Presentaciones de compra â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun PresentacionesTab(ingredient: Ingredient) {
    if (ingredient.purchaseFormats.isEmpty()) {
        PlaceholderSection(
            icon = Icons.Default.Star,
            title = "Sin presentaciones",
            subtitle = "Edita este insumo para agregar sus presentaciones de compra"
        )
    } else {
        val sorted = ingredient.purchaseFormats.sortedByDescending { it.isDefault }
        sorted.forEach { format ->
            PurchaseFormatCard(format = format)
        }
    }
}

// â”€â”€ Tab 2: Recetas â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun RecetasTab(
    ingredientId: String,
    companyId: String,
    state: IngredientUiState,
    viewModel: IngredientViewModel
) {
    LaunchedEffect(ingredientId) {
        if (companyId.isNotEmpty()) viewModel.loadRelatedRecipes(companyId, ingredientId)
    }

    when {
        state.isRelatedRecipesLoading ->
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        state.relatedRecipes.isNullOrEmpty() ->
            PlaceholderSection(
                icon = Icons.Default.Star,
                title = "Sin recetas vinculadas",
                subtitle = "Ninguna receta usa este insumo aÃºn"
            )

        else -> {
            Text(
                text = "${state.relatedRecipes!!.size} receta(s) usan este insumo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            state.relatedRecipes.forEach { usage ->
                RecipeUsageCard(usage)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RecipeUsageCard(usage: IngredientUsage) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = usage.recipeName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (usage.recipeCategory.isNotEmpty()) {
                    Text(
                        text = usage.recipeCategory,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (usage.percentage > 0) {
                    Text(
                        text = "${"%.1f".format(usage.percentage)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (usage.isBase) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Base",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// â”€â”€ Tab 3: Kardex â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun KardexTab(
    ingredient: Ingredient,
    companyId: String,
    currentUser: User?,
    state: IngredientUiState,
    viewModel: IngredientViewModel
) {
    LaunchedEffect(ingredient.id) {
        if (companyId.isNotEmpty()) viewModel.loadStockMovements(companyId, ingredient.id)
    }

    val visibleMovements = state.stockMovements?.let { all ->
        if (currentUser == null || currentUser.branchIds.isEmpty()) all
        else all.filter { it.branchId in currentUser.branchIds }
    }

    when {
        state.isMovementsLoading ->
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        visibleMovements.isNullOrEmpty() ->
            PlaceholderSection(
                icon = Icons.Default.SwapVert,
                title = "Sin movimientos",
                subtitle = "Los ingresos aparecerÃ¡n aquÃ­ al registrar compras"
            )
        else -> {
            // Tabla de movimientos
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Header
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Text("Fecha", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1.2f))
                        Text("Sucursal", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1.2f))
                        Text("Tipo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(0.8f))
                        Text("Cantidad", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1.0f))
                        Text("Saldo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1.0f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    visibleMovements.forEach { mov ->
                        KardexRow(mov, ingredient)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun KardexRow(mov: StockMovement, ingredient: Ingredient) {
    val qtyColor = if (mov.quantity >= 0) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(formatPurchaseDate(mov.date), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f), maxLines = 1)
        Text(mov.branchName, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f), maxLines = 1)
        Text(mov.type, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.8f), maxLines = 1)
        Text(
            text = "${if (mov.quantity > 0) "+" else ""}${"%.1f".format(mov.quantity)} ${ingredient.effectiveBaseUnit}",
            style = MaterialTheme.typography.labelSmall,
            color = qtyColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.0f)
        )
        Text(
            formatStockDisplay(mov.balanceAfter, ingredient),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1.0f)
        )
    }
}

// â”€â”€ Purchase format card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun PurchaseFormatCard(format: PurchaseFormat) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (format.isDefault)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = format.description.ifEmpty { "Sin descripciÃ³n" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (format.isDefault) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "predeterminado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (format.supplierName.isNotEmpty()) {
                        Text(
                            format.supplierName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val unitsLabel = if (format.unitsPerPackage == format.unitsPerPackage.toLong().toDouble())
                        "${format.unitsPerPackage.toLong()} ${format.unit}"
                    else
                        "${"%.2f".format(format.unitsPerPackage)} ${format.unit}"
                    Text(
                        "Paquete: $unitsLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Q ${"%.2f".format(format.packageCost)} / paquete",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Q ${"%.2f".format(format.costPerUnit)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "/ ${format.unit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun TabSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
            modifier = Modifier.weight(0.55f)
        )
    }
}

@Composable
private fun PlaceholderSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
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
