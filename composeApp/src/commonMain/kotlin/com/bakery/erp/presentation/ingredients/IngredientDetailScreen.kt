package com.bakery.erp.presentation.ingredients

import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtPercentage
import com.bakery.erp.util.gramsToOz
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.domain.IngredientUsage
import com.bakery.erp.model.Branch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import com.bakery.erp.model.BranchStock
import com.bakery.erp.model.StockLot
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Permission
import com.bakery.erp.model.PurchaseFormat
import com.bakery.erp.model.StockMovement
import com.bakery.erp.model.User
import com.bakery.erp.model.convertUnits
import com.bakery.erp.model.formatStockDisplay
import com.bakery.erp.model.hasPermission
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
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            viewModel.clearSaved()
            snackbarHostState.showSnackbar("Stock actualizado")
        }
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
                    ingredient = ingredient,
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
        if (word.isNotEmpty()) "$word  Q${defaultFormat.packageCost.fmt(0)}" else ""
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
                                text = "Q ${ingredient.defaultUnitCost.fmt(2)} / ${ingredient.effectiveBaseUnit}",
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
    var showAdjustDialog by remember { mutableStateOf(false) }
    val canAdjustStock = currentUser?.hasPermission(Permission.RECEIVE_STOCK) == true

    LaunchedEffect(ingredient.id) {
        if (companyId.isNotEmpty() && currentUser != null) {
            viewModel.loadIngredientBranchStocks(companyId, ingredient.id, currentUser)
        }
    }
    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && currentUser != null && state.accessibleBranches.isEmpty()) {
            viewModel.loadAccessibleBranches(companyId, currentUser)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabSectionTitle("Stock por sucursal")
        if (canAdjustStock) {
            TextButton(onClick = { showAdjustDialog = true }, enabled = state.accessibleBranches.isNotEmpty()) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ajustar stock", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (showAdjustDialog && currentUser != null) {
        ManualStockAdjustDialog(
            ingredient = ingredient,
            branches = state.accessibleBranches,
            stocksByBranch = (state.selectedIngredientStocks ?: emptyList()).associate { it.branchId to it.currentStock },
            initialBranchId = state.selectedIngredientStocks?.firstOrNull()?.branchId
                ?: state.accessibleBranches.firstOrNull()?.id,
            onDismiss = { showAdjustDialog = false },
            onConfirm = { branchId, branchName, delta, notes ->
                showAdjustDialog = false
                viewModel.adjustStock(
                    companyId, ingredient, branchId, branchName, delta, notes, currentUser
                )
            }
        )
    }

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

    if (ingredient.isPerishable) {
        Spacer(Modifier.height(12.dp))
        TabSectionTitle("Lotes en existencia")
        if (state.selectedIngredientLots.isEmpty()) {
            Text(
                "Sin lotes registrados. Se crean al recibir una compra de este insumo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            Text(
                "Se consume siempre el primero de la lista (el que vence antes).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            state.selectedIngredientLots.forEach { lot ->
                StockLotRow(lot, ingredient)
            }
        }
    }

    if (ingredient.unitWeightGrams > 0.0) {
        Spacer(Modifier.height(8.dp))
        TabSectionTitle("Peso por unidad")
        DetailInfoRow(
            label = "Una unidad pesa",
            value = "${ingredient.unitWeightGrams.fmt(1).trimEnd('0').trimEnd('.')} g " +
                "(${ingredient.unitWeightGrams.gramsToOz().fmt(2)} oz)"
        )
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
            value = "${ingredient.stockAlert.fmt(2).trimEnd('0').trimEnd('.')} $alertUnit"
        )
    }
}

/**
 * Una fila por lote, en orden de consumo. Lo que importa de un vistazo es cuál sale primero
 * y cuánto le queda: por eso el saldo va al lado de la fecha y no en otra pestaña.
 */
@Composable
private fun StockLotRow(lot: StockLot, ingredient: Ingredient) {
    val now = remember { Clock.System.now().toEpochMilliseconds() }
    val days = if (lot.expiryDate > 0L) ((lot.expiryDate - now) / 86_400_000L).toInt() else null
    val urgent = days != null && days <= 7
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (urgent) Color(0xFFFBE0E0) else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        lot.expiryDate <= 0L -> "Sin fecha de vencimiento"
                        days != null && days < 0 -> "Vencido hace ${-days} días"
                        days == 0 -> "Vence hoy"
                        else -> "Vence en $days días"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (urgent) Color(0xFFB3261E) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = buildString {
                        if (lot.expiryDate > 0L) append(formatIngredientDate(lot.expiryDate))
                        if (lot.lotCode.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append("lote ${lot.lotCode}")
                        }
                        if (isNotEmpty()) append(" · ")
                        append(lot.branchName)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatStockDisplay(lot.remainingQuantity, ingredient),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** dd/MM/yyyy sin depender de APIs de plataforma (regla KMP/JS del proyecto). */
private fun formatIngredientDate(millis: Long): String {
    val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${d.dayOfMonth.toString().padStart(2, '0')}/" +
        "${d.monthNumber.toString().padStart(2, '0')}/${d.year}"
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
    ingredient: Ingredient,
    companyId: String,
    state: IngredientUiState,
    viewModel: IngredientViewModel
) {
    val ingredientId = ingredient.id
    LaunchedEffect(ingredientId) {
        if (companyId.isNotEmpty()) viewModel.loadRelatedRecipes(companyId, ingredientId)
    }
    var showSyncConfirm by remember { mutableStateOf(false) }

    when {
        state.isRelatedRecipesLoading ->
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        state.relatedRecipes.isNullOrEmpty() ->
            PlaceholderSection(
                icon = Icons.Default.Star,
                title = "Sin recetas vinculadas",
                subtitle = "Ninguna receta usa este insumo aún"
            )

        else -> {
            Text(
                text = "${state.relatedRecipes!!.size} receta(s) usan este insumo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            // El nombre que ve el panadero en cada receta (RecipeComponent.
            // ingredientName) es una copia guardada al agregar el insumo — no se
            // relee del catálogo. Si renombras el insumo, este botón actualiza
            // esa copia en las recetas que ya lo usan.
            Button(
                onClick = { showSyncConfirm = true },
                enabled = !state.isSyncingIngredientName,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (state.isSyncingIngredientName) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Actualizar nombre en estas recetas", fontWeight = FontWeight.Bold)
                }
            }
            state.syncedIngredientNameCount?.let { count ->
                Text(
                    if (count > 0) "Nombre actualizado en $count receta(s)." else "Las recetas ya tenían el nombre al día.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.height(8.dp))

            state.relatedRecipes.forEach { usage ->
                RecipeUsageCard(usage)
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showSyncConfirm) {
        AlertDialog(
            onDismissRequest = { showSyncConfirm = false },
            title = { Text("Actualizar nombre en recetas") },
            text = {
                Text(
                    "Esto actualiza el nombre guardado de \"${ingredient.name}\" en las " +
                        "${state.relatedRecipes?.size ?: 0} receta(s) que lo usan, para que el " +
                        "panadero vea el nombre actual en vez del que tenía cuando se agregó a la receta."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.syncIngredientNameInRecipes(companyId, ingredient)
                    showSyncConfirm = false
                }) { Text("Actualizar", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showSyncConfirm = false }) { Text("Cancelar") }
            }
        )
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
                        text = "${usage.percentage.fmtPercentage()}%",
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatPurchaseDate(mov.date), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f), maxLines = 1)
            Text(mov.branchName, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f), maxLines = 1)
            Text(mov.type, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.8f), maxLines = 1)
            Text(
                text = "${if (mov.quantity > 0) "+" else ""}${mov.quantity.fmt(1)} ${ingredient.effectiveBaseUnit}",
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
        if (!mov.notes.isNullOrBlank()) {
            Text(
                text = mov.notes,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
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
                        "${format.unitsPerPackage.fmt(2)} ${format.unit}"
                    Text(
                        "Paquete: $unitsLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Q ${format.packageCost.fmt(2)} / paquete",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Q ${format.costPerUnit.fmt(2)}",
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

private enum class StockAdjustMode { ENTRY, EXIT, EXACT }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualStockAdjustDialog(
    ingredient: Ingredient,
    branches: List<Branch>,
    stocksByBranch: Map<String, Double>,
    initialBranchId: String?,
    onDismiss: () -> Unit,
    onConfirm: (branchId: String, branchName: String, delta: Double, notes: String) -> Unit
) {
    var selectedBranch by remember {
        mutableStateOf(branches.find { it.id == initialBranchId } ?: branches.firstOrNull())
    }
    var branchMenuExpanded by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(StockAdjustMode.ENTRY) }
    var quantityText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    val enteredValue = quantityText.replace(",", ".").toDoubleOrNull()
    val currentStock = selectedBranch?.let { stocksByBranch[it.id] } ?: 0.0

    // Delta con signo listo para escribir en el Kardex: en modo "cantidad exacta"
    // el usuario teclea el total real y aquí se calcula cuánto sumar/restar,
    // sin que tenga que hacer esa resta mentalmente.
    val delta = when (mode) {
        StockAdjustMode.ENTRY -> enteredValue
        StockAdjustMode.EXIT -> enteredValue?.let { -it }
        StockAdjustMode.EXACT -> enteredValue?.let { it - currentStock }
    }
    val quantityValid = when (mode) {
        StockAdjustMode.EXACT -> enteredValue != null && enteredValue >= 0.0
        else -> enteredValue != null && enteredValue > 0.0
    }
    val canConfirm = selectedBranch != null && quantityValid && delta != null && delta != 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedTextField(
                        value = selectedBranch?.name ?: "Selecciona sucursal",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sucursal") },
                        trailingIcon = {
                            IconButton(onClick = { branchMenuExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { branchMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = branchMenuExpanded,
                        onDismissRequest = { branchMenuExpanded = false }
                    ) {
                        branches.forEach { b ->
                            DropdownMenuItem(
                                text = { Text(b.name) },
                                onClick = { selectedBranch = b; branchMenuExpanded = false }
                            )
                        }
                    }
                }
                Text(
                    text = "Stock actual: ${formatStockDisplay(currentStock, ingredient)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == StockAdjustMode.ENTRY,
                        onClick = { mode = StockAdjustMode.ENTRY },
                        label = { Text("Ingreso") }
                    )
                    FilterChip(
                        selected = mode == StockAdjustMode.EXIT,
                        onClick = { mode = StockAdjustMode.EXIT },
                        label = { Text("Egreso") }
                    )
                    FilterChip(
                        selected = mode == StockAdjustMode.EXACT,
                        onClick = { mode = StockAdjustMode.EXACT },
                        label = { Text("Cantidad exacta") }
                    )
                }
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { input -> quantityText = input.filter { it.isDigit() || it == '.' || it == ',' } },
                    label = {
                        Text(
                            if (mode == StockAdjustMode.EXACT) "Lo que hay ahora (${ingredient.effectiveBaseUnit})"
                            else "Cantidad (${ingredient.effectiveBaseUnit})"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (mode == StockAdjustMode.EXACT && delta != null && delta != 0.0) {
                    Text(
                        text = "Se registrará como ${if (delta > 0) "+" else ""}${formatStockDisplay(delta, ingredient)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo (opcional)") },
                    placeholder = { Text("Ej. conteo inicial, merma detectada...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    val branch = selectedBranch ?: return@TextButton
                    val d = delta ?: return@TextButton
                    val notes = buildString {
                        append(
                            when (mode) {
                                StockAdjustMode.ENTRY -> "Ingreso manual"
                                StockAdjustMode.EXIT -> "Egreso manual"
                                StockAdjustMode.EXACT -> "Conteo físico (ajustado a ${formatStockDisplay(enteredValue ?: 0.0, ingredient)})"
                            }
                        )
                        if (reason.isNotBlank()) append(" — $reason")
                    }
                    onConfirm(branch.id, branch.name, d, notes)
                }
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

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
