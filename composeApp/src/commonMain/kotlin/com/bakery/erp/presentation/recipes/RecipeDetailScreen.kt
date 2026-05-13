package com.bakery.erp.presentation.recipes

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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.RecipeComponent
import com.bakery.erp.model.RecipeStage
import com.bakery.erp.model.RecipeStageType
import com.bakery.erp.model.RecipeYield
import com.bakery.erp.presentation.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (recipeId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: RecipeViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(recipeId, companyId) {
        if (companyId.isNotEmpty()) viewModel.loadRecipe(companyId, recipeId)
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

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = recipe.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (recipe.category.isNotEmpty()) DetailBadge(recipe.category)
                        DetailBadge(if (recipe.inputMode == "PERCENTAGE") "% panadero" else "Por peso")
                        StatusBadge(recipe.active)
                    }

                    if (!recipe.description.isNullOrEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = recipe.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (recipe.category == "Pan Principal") {
                        // ── Etapas ───────────────────────────────────────────
                        if (recipe.stages.isNotEmpty()) {
                            DetailSection("Etapas de Preparación")
                            recipe.stages.sortedBy { it.order }.forEach { stage ->
                                StageDetailCard(stage = stage)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        // ── Productos que produce ─────────────────────────────
                        if (recipe.yields.isNotEmpty()) {
                            DetailSection("Produce")
                            recipe.yields.forEach { yld ->
                                YieldDetailRow(yld)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    } else {
                        // ── Cubierta / Relleno: ingredientes y pasos directos ─
                        val singleStage = recipe.stages.firstOrNull()
                        if (singleStage != null && singleStage.components.isNotEmpty()) {
                            DetailSection("Ingredientes")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                        androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Ingrediente", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("Porcentaje", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            singleStage.components.sortedBy { it.order }.forEach { comp ->
                                ComponentDetailRow(comp)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        val instrucciones = singleStage?.instructions
                        if (!instrucciones.isNullOrEmpty()) {
                            DetailSection("Instrucciones")
                            val pasos = instrucciones.split("\n").filter { it.isNotEmpty() }
                            if (pasos.size <= 1) {
                                Text(instrucciones, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    pasos.forEachIndexed { idx, paso ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = androidx.compose.foundation.shape.CircleShape,
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("${idx + 1}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Text(paso,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Notas ────────────────────────────────────────────────
                    if (!recipe.notes.isNullOrEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        DetailSection("Notas")
                        Text(
                            text = recipe.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun StageDetailCard(stage: RecipeStage) {
    var expanded by remember { mutableStateOf(true) }
    val stageTypeDisplay = RecipeStageType.entries.firstOrNull { it.name == stage.type }?.displayName ?: stage.type

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stage.name.ifEmpty { stageTypeDisplay },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DetailBadge(stageTypeDisplay)
                        if (stage.restTimeMinutes != null && stage.restTimeMinutes > 0) {
                            DetailBadge("Reposo: ${stage.restTimeMinutes} min")
                        }
                    }
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Colapsar" else "Expandir"
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))

                if (stage.components.isNotEmpty()) {
                    // Tabla de ingredientes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ingrediente", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Porcentaje", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    stage.components.sortedBy { it.order }.forEach { comp ->
                        ComponentDetailRow(comp)
                    }
                } else {
                    Text(
                        text = "Sin ingredientes registrados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (!stage.instructions.isNullOrEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val pasos = stage.instructions.split("\n").filter { it.isNotEmpty() }
                    if (pasos.size <= 1) {
                        Text(
                            text = stage.instructions,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            pasos.forEachIndexed { idx, paso ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${idx + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = paso,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
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

@Composable
private fun ComponentDetailRow(component: RecipeComponent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (component.isBase) {
                Text("★ ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                text = component.ingredientName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (component.isBase) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        Text(
            text = "${"%.1f".format(component.percentage)}%",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (component.isBase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun YieldDetailRow(yld: RecipeYield) {
    Text(
        text = "• ${yld.productName}",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    )
}

// ── Helpers ──────────────────────────────────────────────────────────────────

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
