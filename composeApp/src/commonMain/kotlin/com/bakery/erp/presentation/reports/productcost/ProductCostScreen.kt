package com.bakery.erp.presentation.reports.productcost

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.domain.CostProblem
import com.bakery.erp.domain.IngredientPriceOrigin
import com.bakery.erp.domain.ProductMaterialCost
import com.bakery.erp.model.User
import com.bakery.erp.model.toGramsOrNull
import com.bakery.erp.presentation.reports.shared.ReportPrimary
import com.bakery.erp.presentation.reports.shared.ReportSurface
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney

/**
 * "Costo por producto": cuánto de cada precio se va en materia prima, y qué falta cargar
 * (recetas, pesos en crudo, precios de insumos) para que el costo salga completo. Es la base
 * del costo estándar de planta (paso 4), así que los huecos se muestran, no se esconden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductCostScreen(
    user: User?,
    onNavigateBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenIngredient: (String) -> Unit,
    viewModel: ProductCostViewModel
) {
    val state by viewModel.uiState.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(user?.orgId) { user?.orgId?.let(viewModel::ensureLoaded) }

    Scaffold(
        containerColor = ReportSurface,
        topBar = {
            TopAppBar(
                title = { Text("Costo por producto", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { user?.orgId?.let(viewModel::reload) }) {
                        Icon(Icons.Default.Refresh, "Actualizar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ReportPrimary)
            )
        }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            when {
                state.isLoading && state.costs.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null && state.costs.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                else -> Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = tab, containerColor = ReportSurface, contentColor = BrandColors.Primary) {
                        Tab(tab == 0, { tab = 0 }, text = { Text("Productos (${state.calculated.size})") })
                        Tab(tab == 1, { tab = 1 }, text = { Text("Pendientes (${state.notCalculated.size})") })
                        Tab(tab == 2, { tab = 2 }, text = {
                            Text("Insumos" + state.missingPriceIngredients.size.takeIf { it > 0 }?.let { " ($it ⚠)" }.orEmpty())
                        })
                    }
                    when (tab) {
                        0 -> CalculatedTab(state, onOpenProduct)
                        1 -> PendingTab(state, onOpenProduct)
                        else -> IngredientsTab(state, onOpenIngredient)
                    }
                }
            }
        }
    }
}

@Composable
private fun Intro(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CalculatedTab(state: ProductCostUiState, onOpenProduct: (String) -> Unit) {
    val byCategory = state.calculated.groupBy { it.product.category.ifBlank { "Sin categoría" } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Intro(
                "Cuánto cuesta en ingredientes cada pan, con su peso en crudo y su receta. El precio de " +
                    "cada insumo es el promedio de lo comprado en los últimos " +
                    "${MaterialCostLoader.PRICE_WINDOW_DAYS} días, o el de su presentación si no se compró. " +
                    "Todavía no incluye planilla, luz ni gas: eso entra en el costo de planta."
            )
        }
        if (state.incomplete.isNotEmpty()) item {
            BrandChip("${state.incomplete.size} con costo corto: les falta el precio de algún insumo", ChipTone.ATTENTION)
        }
        byCategory.forEach { (category, items) ->
            item(key = "cat_$category") {
                Text(category, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary, modifier = Modifier.padding(top = 6.dp))
            }
            items(items, key = { it.product.id }) { CostCard(it, onOpenProduct) }
        }
    }
}

@Composable
private fun CostCard(cost: ProductMaterialCost, onOpenProduct: (String) -> Unit) {
    var open by rememberSaveable(cost.product.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { open = !open },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cost.product.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${cost.recipeName} · ${cost.doughGrams.fmt(0)} g en crudo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Q ${(cost.total ?: 0.0).fmtMoney(cents(cost.total))}", fontWeight = FontWeight.Bold,
                        color = BrandColors.Primary)
                    Text(
                        cost.shareOfPrice?.let { "${(it * 100).fmt(0)}% de Q ${cost.product.salePrice.fmtMoney(2)}" }
                            ?: "sin precio de venta",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            cost.problems.filterIsInstance<CostProblem.MissingPrices>().firstOrNull()?.let {
                BrandChip("Falta precio: ${it.names.joinToString()}", ChipTone.ATTENTION)
            }
            if (open) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                BreakdownRow("Masa (${cost.doughGrams.fmt(0)} g)", cost.doughCost)
                if (cost.fillingsCost > 0.0) BreakdownRow("Relleno de otra masa", cost.fillingsCost)
                if (cost.decorationsCost > 0.0) BreakdownRow("Adornos y rellenos", cost.decorationsCost)
                Text(
                    "Editar producto",
                    color = BrandColors.Primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { onOpenProduct(cost.product.id) }.padding(top = 4.dp)
                )
            }
        }
    }
}

/** Centavos de más cuando el costo es de centavos: Q0.0832 se lee mejor que Q0.08. */
private fun cents(v: Double?): Int = if (v != null && v < 1.0) 3 else 2

@Composable
private fun BreakdownRow(label: String, value: Double) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text("Q ${value.fmtMoney(cents(value))}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PendingTab(state: ProductCostUiState, onOpenProduct: (String) -> Unit) {
    val noRecipe = state.notCalculated.filter { CostProblem.NoRecipe in it.problems }
    val noWeight = state.notCalculated.filter { CostProblem.NoRawWeight in it.problems }
    val empty = state.notCalculated.filter { CostProblem.EmptyRecipe in it.problems }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Intro(
                "Productos a los que todavía no se les puede sacar el costo. Toca uno para completarlo. " +
                    "Para la repostería, el peso en crudo es lo que pesa la pieza antes de hornear; " +
                    "la crema, el relleno o la cobertura van como adornos del producto."
            )
        }
        pendingGroup("Sin peso en crudo", "La receta existe, pero no se sabe cuánta masa lleva cada uno.", noWeight, onOpenProduct)
        pendingGroup("Sin receta", "Ninguna receta dice que produce este producto.", noRecipe, onOpenProduct)
        pendingGroup("Receta vacía", "La receta no tiene ingredientes con porcentaje.", empty, onOpenProduct)
        if (state.notCalculated.isEmpty()) item { Text("Todo calculado.", fontWeight = FontWeight.SemiBold) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.pendingGroup(
    title: String,
    hint: String,
    items: List<ProductMaterialCost>,
    onOpenProduct: (String) -> Unit
) {
    if (items.isEmpty()) return
    item(key = "pg_$title") {
        Column(Modifier.padding(top = 8.dp)) {
            Text("$title (${items.size})", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = BrandColors.Primary)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    items(items, key = { "p_${title}_${it.product.id}" }) { c ->
        Row(
            Modifier.fillMaxWidth().clickable { onOpenProduct(c.product.id) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(c.product.name, Modifier.weight(1f))
            Text(c.product.category, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
    }
}

@Composable
private fun IngredientsTab(state: ProductCostUiState, onOpenIngredient: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Intro(
                "Precio de cada materia prima. Los que no tienen precio cuentan Q0 y dejan corto el costo " +
                    "de los productos que los usan: ponles una presentación con su precio, o regístralos en una compra."
            )
        }
        items(state.ingredientPrices, key = { it.ingredient.id }) { row ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpenIngredient(row.ingredient.id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(row.ingredient.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (row.price?.origin) {
                            IngredientPriceOrigin.PURCHASES ->
                                "Promedio de ${row.price.purchaseCount} compra${if (row.price.purchaseCount == 1) "" else "s"}"
                            IngredientPriceOrigin.FORMAT -> "Precio de su presentación"
                            null -> if (row.affectedProducts > 0)
                                "Afecta ${row.affectedProducts} producto${if (row.affectedProducts == 1) "" else "s"}"
                            else "No lo usa ningún producto con costo"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val price = row.price
                if (price == null) BrandChip("Sin precio", if (row.affectedProducts > 0) ChipTone.DANGER else ChipTone.NEUTRAL)
                else Text(displayPrice(row.ingredient.effectiveBaseUnit, row.ingredient.unitWeightGrams, price.costPerGram),
                    color = BrandColors.Primary, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider()
        }
    }
}

/** El precio en la unidad en que se compra (Q/lb, Q/unidad); Q/kg si la unidad no se convierte. */
private fun displayPrice(unit: String, unitWeightGrams: Double, costPerGram: Double): String {
    val grams = toGramsOrNull(1.0, unit, unitWeightGrams)
    return if (grams != null && grams > 0.0) "Q ${(costPerGram * grams).fmtMoney(cents(costPerGram * grams))} / $unit"
    else "Q ${(costPerGram * 1000).fmtMoney(2)} / kg"
}
