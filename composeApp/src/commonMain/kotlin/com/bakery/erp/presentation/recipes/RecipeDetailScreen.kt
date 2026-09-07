package com.bakery.erp.presentation.recipes

import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtPercentage
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Product
import com.bakery.erp.model.RecipeComponent
import com.bakery.erp.model.RecipeStep
import com.bakery.erp.model.RecipeYield
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.ProductImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (recipeId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: RecipeViewModel,
    productViewModel: ProductViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val productState by productViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(recipeId, companyId) {
        if (companyId.isNotEmpty()) {
            viewModel.loadRecipe(companyId, recipeId)
            productViewModel.loadProducts(companyId)
        }
    }

    val recipe = state.selectedRecipe?.takeIf { it.id == recipeId }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) { viewModel.clearDeleted(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar receta") },
            text = {
                Text("¿Estás seguro que deseas eliminar \"${recipe?.name}\"? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        recipe?.let { viewModel.deleteRecipe(companyId, it.id) }
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
                        text = recipe?.name ?: "Detalle",
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
                    IconButton(onClick = { recipe?.let { onNavigateToEdit(it.id) } }) {
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
                state.isDetailLoading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                recipe == null ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Receta no encontrada", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                else -> {
                    val density = LocalDensity.current
                    val windowInfo = LocalWindowInfo.current
                    val screenWidthDp: Dp = with(density) { windowInfo.containerSize.width.toDp() }
                    val isLarge = screenWidthDp >= 720.dp
                    val containerMax = if (isLarge) 760.dp else 640.dp
                    val nameColWidth = if (isLarge) 320.dp else 220.dp

                    AdaptiveContainer(maxContentWidth = containerMax) {
                    val components = recipe.allComponents.sortedBy { it.order }
                    val steps = recipe.allSteps.sortedBy { it.order }
                  Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(if (isLarge) 24.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isLarge) 8.dp else 4.dp)
                ) {
                    Text(
                        text = recipe.name,
                        style = if (isLarge) MaterialTheme.typography.headlineLarge
                                else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DetailBadge(if (recipe.inputMode == "PERCENTAGE") "% panadero" else "Por peso")
                        StatusBadge(recipe.active)
                    }

                    if (!recipe.description.isNullOrEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = recipe.description,
                            style = if (isLarge) MaterialTheme.typography.bodyLarge
                                    else MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(if (isLarge) 16.dp else 8.dp))

                    // ── Ingredientes ─────────────────────────────────────────
                    DetailSection("Ingredientes", isLarge)
                    if (components.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Ingrediente",
                                style = if (isLarge) MaterialTheme.typography.labelLarge
                                        else MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(nameColWidth)
                            )
                            Text(
                                "Porcentaje",
                                style = if (isLarge) MaterialTheme.typography.labelLarge
                                        else MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        components.forEach { ComponentDetailRow(it, isLarge, nameColWidth) }
                        BaseGroupCaption(components, isLarge)
                    } else {
                        Text(
                            text = "Sin ingredientes registrados",
                            style = if (isLarge) MaterialTheme.typography.bodyLarge
                                    else MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // ── Instrucciones ────────────────────────────────────────
                    if (steps.isNotEmpty()) {
                        Spacer(Modifier.height(if (isLarge) 16.dp else 8.dp))
                        DetailSection("Instrucciones", isLarge)
                        StepsDetailView(steps, isLarge)
                    }

                    // ── Productos que produce ────────────────────────────────
                    if (recipe.yields.isNotEmpty()) {
                        Spacer(Modifier.height(if (isLarge) 16.dp else 8.dp))
                        DetailSection("Produce", isLarge)
                        recipe.yields.forEach { yld ->
                            val product = productState.productList.find { it.id == yld.productId }
                            YieldDetailRow(yld, product, isLarge)
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    // ── Notas ────────────────────────────────────────────────
                    if (!recipe.notes.isNullOrEmpty()) {
                        Spacer(Modifier.height(if (isLarge) 16.dp else 4.dp))
                        DetailSection("Notas", isLarge)
                        Text(
                            text = recipe.notes,
                            style = if (isLarge) MaterialTheme.typography.bodyLarge
                                    else MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                  }
                }
                }
            }
        }
    }
}

@Composable
private fun ComponentDetailRow(component: RecipeComponent, isLarge: Boolean, nameColWidth: Dp) {
    val nameStyle = if (isLarge) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodyMedium
    val pctStyle = if (isLarge) MaterialTheme.typography.titleMedium
                   else MaterialTheme.typography.bodyMedium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = if (isLarge) 8.dp else 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.width(nameColWidth),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (component.isBase) {
                Text(
                    "★ ",
                    color = MaterialTheme.colorScheme.primary,
                    style = nameStyle
                )
            }
            Text(
                text = component.ingredientName,
                style = nameStyle,
                fontWeight = if (component.isBase) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        Text(
            text = "${component.percentage.fmtPercentage()}%",
            style = pctStyle,
            fontWeight = FontWeight.SemiBold,
            color = if (component.isBase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun YieldDetailRow(yld: RecipeYield, product: Product?, isLarge: Boolean) {
    val hasImage = !product?.imageUrl.isNullOrBlank()
    if (hasImage) {
        val thumbSize = if (isLarge) 56.dp else 44.dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            ProductImage(
                imageUrl = product?.imageUrl,
                contentDescription = yld.productName,
                modifier = Modifier
                    .size(thumbSize)
                    .clip(RoundedCornerShape(8.dp))
            )
            Text(
                text = yld.productName,
                style = if (isLarge) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        Text(
            text = "• ${yld.productName}",
            style = if (isLarge) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}

@Composable
private fun StepsDetailView(steps: List<RecipeStep>, isLarge: Boolean) {
    val bulletSize = if (isLarge) 32.dp else 20.dp
    val textStyle = if (isLarge) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodyMedium
    Column(verticalArrangement = Arrangement.spacedBy(if (isLarge) 16.dp else 8.dp)) {
        steps.forEachIndexed { idx, step ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (isLarge) 14.dp else 10.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.size(bulletSize)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${idx + 1}",
                            style = if (isLarge) MaterialTheme.typography.titleMedium
                                    else MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Column(
                    modifier = if (isLarge) Modifier.widthIn(max = 520.dp)
                               else Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (step.text.isNotBlank()) {
                        Text(
                            text = step.text,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (!step.photoUrl.isNullOrBlank()) {
                        ProductImage(
                            imageUrl = step.photoUrl,
                            contentDescription = "Foto del paso ${idx + 1}",
                            modifier = if (isLarge) {
                                Modifier
                                    .width(360.dp)
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BaseGroupCaption(components: List<RecipeComponent>, isLarge: Boolean) {
    val bases = components.filter { it.isBase }
    if (bases.size > 1) {
        val sum = bases.sumOf { it.percentage }
        Text(
            text = "★ ${bases.size} harinas base — suman ${sum.fmt(1)}% (= 100% panadero)",
            style = if (isLarge) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun DetailSection(title: String, isLarge: Boolean = false) {
    Column {
        Text(
            text = title,
            style = if (isLarge) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DetailBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StatusBadge(active: Boolean) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = if (active) "Activa" else "Inactiva",
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
