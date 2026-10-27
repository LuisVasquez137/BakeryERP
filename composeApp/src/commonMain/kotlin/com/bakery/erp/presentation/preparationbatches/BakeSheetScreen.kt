package com.bakery.erp.presentation.preparationbatches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.BakeRow
import com.bakery.erp.model.BatterMix
import com.bakery.erp.model.convertUnits
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.transfers.BranchChip
import com.bakery.erp.presentation.transfers.ChipRow
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.BrandTopAppBar
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.util.fmtTrim
import com.bakery.erp.util.formatBakerWeight
import kotlin.time.Clock

/**
 * Hoja de horneado de bizcochos. El repostero ve lo que piden y lo que tiene frío, pone
 * cuánto va a hacer y le sale la masa a batir; al sacarlos del horno anota cuántos salieron.
 */
@Composable
fun BakeSheetScreen(
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: BakeSheetViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    var showFinish by remember { mutableStateOf(false) }

    LaunchedEffect(companyId) {
        if (user != null && companyId.isNotEmpty()) viewModel.load(companyId, user)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let { snackbar.showSnackbar(it); viewModel.clearSaved() }
    }

    val mixes = state.mixes
    val needs = state.ingredientNeeds

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = BrandColors.Ivory,
            topBar = {
                BrandTopAppBar(
                    title = { Text("Hornear bizcochos", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh(companyId, keepPlan = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BrandColors.Primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                if (mixes.isNotEmpty()) {
                    Surface(color = Color.White, shadowElevation = 6.dp) {
                        Button(
                            onClick = { showFinish = true },
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) { Text("Ya salieron del horno") }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            AdaptiveContainer(modifier = Modifier.padding(padding)) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (state.plants.size > 1) item {
                        ChipRow {
                            state.plants.forEach { p ->
                                BranchChip(p.name, state.plant?.id == p.id) { viewModel.selectPlant(companyId, p) }
                            }
                        }
                    }
                    item {
                        Column {
                            Text("Pedidos para", style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
                            ChipRow {
                                listOf(0 to "Hoy", 1 to "Mañana", 2 to "Pasado mañana").forEach { (offset, label) ->
                                    BranchChip(label, state.dayOffset == offset) { viewModel.selectDay(companyId, offset) }
                                }
                            }
                        }
                    }
                    when {
                        state.plant == null && !state.isLoading -> item {
                            Hint("No tienes una planta de producción asignada.")
                        }
                        state.isLoading && state.rows.isEmpty() -> item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        state.rows.isEmpty() -> item {
                            Hint(
                                "No hay bizcochos configurados. En Preparaciones crea la masa (\"Masa de bizcocho\") " +
                                    "y después cada molde (\"Bizcocho\"), con la masa y cuántas onzas lleva."
                            )
                        }
                        else -> {
                            if (state.unconfigured.isNotEmpty()) item {
                                Hint(
                                    "Sin masa o sin receta, no se pueden hornear desde acá: " +
                                        state.unconfigured.joinToString(", ")
                                )
                            }
                            items(state.rows, key = { it.base.id }) { row ->
                                BakeRowCard(
                                    row = row,
                                    toBake = state.toBake[row.base.id].orEmpty(),
                                    onToBake = { viewModel.setToBake(row.base.id, it) }
                                )
                            }
                            if (mixes.isNotEmpty()) {
                                item {
                                    Text("Masa a batir", style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold, color = BrandColors.Primary,
                                        modifier = Modifier.padding(top = 8.dp))
                                }
                                items(mixes, key = { "mix-" + it.batterId }) { mix -> MixCard(mix) }
                                if (needs.isNotEmpty()) item { IngredientsCard(needs) }
                            }
                        }
                    }
                }
            }
        }
        LoadingOverlay(visible = state.isSaving, message = "Anotando la horneada...")
    }

    if (showFinish) {
        FinishBakeDialog(
            molds = mixes.flatMap { it.molds }.map { it.base.id to (it.base.name to it.units) },
            onDismiss = { showFinish = false },
            onConfirm = { produced ->
                showFinish = false
                if (user != null) viewModel.finishBake(companyId, user, produced)
            }
        )
    }
}

@Composable
private fun Hint(text: String) {
    Surface(color = BrandColors.Sand, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextSlate,
            modifier = Modifier.padding(14.dp))
    }
}

@Composable
internal fun BakeRowCard(
    row: BakeRow,
    toBake: String,
    onToBake: (String) -> Unit,
    noDemandText: String = "Sin pedidos ese día"
) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.base.name, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                Text(
                    "${row.base.batterPreparationName.ifEmpty { "Sin masa" }} · " +
                        "${convertUnits(row.base.batterGramsPerUnit, "gr", "oz").fmtTrim(1)} oz",
                    style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate
                )
                Text(
                    if (row.demand.units > 0.0) "Piden ${row.demand.units.fmtTrim(2)}: " +
                        row.demand.byProduct.entries.joinToString(", ") { "${it.value.fmtTrim(0)} ${it.key}" }
                    else noDemandText,
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.TextSlate
                )
                Text(
                    "Hay ${row.onHand.fmtTrim(2)} fríos" +
                        (if (row.oldestProducedAt > 0L) " · el más viejo ${ageLabel(row.oldestProducedAt)}" else "") +
                        (if (row.base.stockMinimumUnits > 0.0) " · tener ${row.base.stockMinimumUnits.fmtTrim(1)}" else ""),
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate
                )
                Spacer(Modifier.height(2.dp))
                if (row.short > 0.0) BrandChip("Faltan ${row.short.fmtTrim(2)}", ChipTone.ATTENTION)
                else if (row.demand.units > 0.0) BrandChip("Alcanza", ChipTone.SUCCESS)
            }
            OutlinedTextField(
                value = toBake,
                onValueChange = onToBake,
                label = { Text("Voy a hacer") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(112.dp)
            )
        }
    }
}

@Composable
internal fun MixCard(mix: BatterMix) {
    Surface(color = BrandColors.PrimaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mix.batterName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary, modifier = Modifier.weight(1f))
                Text(formatBakerWeight(mix.totalGrams), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = BrandColors.Primary)
            }
            Text(
                mix.molds.joinToString(" + ") { "${it.units.fmtTrim(2)} ${it.base.name}" },
                style = MaterialTheme.typography.bodySmall, color = BrandColors.TextSlate
            )
            val scale = mix.scale
            if (scale == null) {
                Text("La masa no tiene insumos: no se puede escalar.", style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.Danger)
            } else {
                Text("${scale.fmtTrim(2)} veces la receta", style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.MutedSlate)
                HorizontalDivider(color = Color.White, modifier = Modifier.padding(vertical = 4.dp))
                mix.scaledIngredients.forEach { (pi, qty) ->
                    Row {
                        Text(pi.ingredientName, style = MaterialTheme.typography.bodyMedium,
                            color = BrandColors.TextSlate, modifier = Modifier.weight(1f))
                        Text("${qty.fmtTrim(2)} ${pi.unit}", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                    }
                }
            }
        }
    }
}

@Composable
internal fun IngredientsCard(needs: List<BakeIngredientNeed>) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Se descuenta del inventario", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
            needs.forEach { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.ingredientName, style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.TextSlate, modifier = Modifier.weight(1f))
                    Text(
                        n.quantity?.let { "${it.fmtTrim(2)} ${n.unit}" } ?: "sin convertir",
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                        color = if (n.quantity == null) BrandColors.Danger else BrandColors.TextSlate
                    )
                    Spacer(Modifier.width(8.dp))
                    if (n.short) BrandChip("Hay ${n.available.fmtTrim(2)}", ChipTone.DANGER)
                }
            }
            if (needs.any { it.short }) Text(
                "Lo que falta en el sistema se descuenta igual hasta cero: revisa que se haya anotado la compra.",
                style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate
            )
        }
    }
}

/** Cuántos salieron de cada molde. Arranca con lo planeado: si salió uno menos, se corrige. */
@Composable
internal fun FinishBakeDialog(
    molds: List<Pair<String, Pair<String, Double>>>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, Double>) -> Unit
) {
    var values by remember { mutableStateOf(molds.associate { (id, v) -> id to v.second.fmtTrim(2) }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Cuántos salieron?") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Entran al inventario de bizcochos y se descuenta la masa que se batió.",
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                molds.forEach { (id, v) ->
                    OutlinedTextField(
                        value = values[id].orEmpty(),
                        onValueChange = { t -> values = values + (id to t.filter { it.isDigit() || it == '.' || it == ',' }) },
                        label = { Text("${v.first} (hiciste ${v.second.fmtTrim(2)})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(values.mapValues { it.value.replace(',', '.').toDoubleOrNull() ?: 0.0 })
            }) { Text("Anotar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** "hoy", "ayer", "hace 3 días". */
private fun ageLabel(millis: Long): String {
    val days = ((Clock.System.now().toEpochMilliseconds() - millis) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "de hoy"
        days == 1 -> "de ayer"
        else -> "de hace $days días"
    }
}
