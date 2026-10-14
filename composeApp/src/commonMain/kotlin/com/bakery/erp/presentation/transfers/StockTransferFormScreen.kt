package com.bakery.erp.presentation.transfers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Permission
import com.bakery.erp.model.Product
import com.bakery.erp.model.StockTransfer
import com.bakery.erp.model.StockTransferItemKind
import com.bakery.erp.model.StockTransferLine
import com.bakery.erp.model.costPerStockUnit
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.looseToBaseUnitOrNull
import com.bakery.erp.model.looseUnitOptions
import com.bakery.erp.model.packagesToBaseUnitOrNull
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.ingredients.IngredientPickerDialog
import com.bakery.erp.presentation.pos.ProductPickerDialog
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtTrim

/** Línea en edición. La cantidad se convierte a la unidad del stock al enviar. */
private data class DraftLine(
    val kind: String,
    val itemId: String,
    val name: String,
    val text: String = "",
    /** Presentación elegida (solo insumos); "" = cantidad suelta en [looseUnit]. */
    val formatId: String = "",
    val looseUnit: String = ""
)

/**
 * Enviar un traslado: de qué sucursal sale, a cuál va y qué lleva. Al enviarlo sale del stock del
 * origen; entra al destino cuando allá lo reciben. Ver `model/StockTransfer.kt`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTransferFormScreen(
    onNavigateBack: () -> Unit,
    onSent: (transferId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: StockTransferViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    val canSeeValue = user?.hasPermission(Permission.VIEW_DISPATCH_VALUE) == true

    val origins = remember(state.branches, user?.id) { user?.let { viewModel.originsFor(it) }.orEmpty() }
    var originId by rememberSaveable { mutableStateOf("") }
    var destinationId by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var lines by remember { mutableStateOf(listOf<DraftLine>()) }
    var picking by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(companyId) { if (companyId.isNotEmpty()) viewModel.loadBranches(companyId) }
    // Con una sola sucursal propia no hay nada que elegir.
    LaunchedEffect(origins) { if (originId.isEmpty() && origins.size == 1) originId = origins.first().id }
    LaunchedEffect(companyId, originId) { if (companyId.isNotEmpty()) viewModel.prepareForm(companyId, originId) }
    LaunchedEffect(state.sentId) { state.sentId?.let { viewModel.consumeSentId(); onSent(it) } }
    LaunchedEffect(state.error) { state.error?.let { viewModel.clearError(); snackbar.showSnackbar(it) } }

    val productsById = remember(state.products) { state.products.associateBy { it.id } }
    val ingredientsById = remember(state.ingredients) { state.ingredients.associateBy { it.id } }
    val origin = state.branches.firstOrNull { it.id == originId }
    val destination = state.branches.firstOrNull { it.id == destinationId }

    /** Cantidad en la unidad del stock, o null si no se puede convertir (no se manda un número inventado). */
    fun baseQuantity(d: DraftLine): Double? {
        val q = d.text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        if (d.kind == StockTransferItemKind.PRODUCT) return q
        val ing = ingredientsById[d.itemId] ?: return null
        val fmt = ing.purchaseFormats.firstOrNull { it.id == d.formatId }
        return if (fmt != null) packagesToBaseUnitOrNull(q, fmt, ing) else looseToBaseUnitOrNull(q, d.looseUnit, ing)
    }
    fun available(d: DraftLine): Double =
        if (d.kind == StockTransferItemKind.PRODUCT) state.originProductStock[d.itemId]?.currentStock ?: 0.0
        else state.originIngredientStock[d.itemId] ?: 0.0
    fun stockUnit(d: DraftLine): String =
        if (d.kind == StockTransferItemKind.PRODUCT) productsById[d.itemId]?.unit?.ifBlank { "u" } ?: "u"
        else ingredientsById[d.itemId]?.effectiveBaseUnit.orEmpty()

    fun buildDraft(): StockTransfer = StockTransfer(
        originBranchId = originId,
        originBranchName = origin?.name.orEmpty(),
        destinationBranchId = destinationId,
        destinationBranchName = destination?.name.orEmpty(),
        note = note,
        lines = lines.mapNotNull { d ->
            val qty = baseQuantity(d) ?: return@mapNotNull null
            val unit = stockUnit(d)
            if (d.kind == StockTransferItemKind.PRODUCT) {
                val p = productsById[d.itemId]
                StockTransferLine(
                    kind = d.kind, itemId = d.itemId, itemName = d.name, unit = unit, quantity = qty,
                    // El repositorio usa el costo promedio del origen; éste solo cuenta si no hay.
                    unitCost = p?.purchaseCost ?: 0.0
                )
            } else {
                val ing = ingredientsById[d.itemId]
                val fmt = ing?.purchaseFormats?.firstOrNull { it.id == d.formatId }
                StockTransferLine(
                    kind = d.kind, itemId = d.itemId, itemName = d.name, unit = unit, quantity = qty,
                    displayQuantity = if (fmt != null) "${d.text.trim()} ${fmt.description}"
                                      else "${d.text.trim()} ${d.looseUnit}",
                    unitCost = ing?.costPerStockUnit() ?: 0.0
                )
            }
        }
    )

    val unresolved = lines.any { baseQuantity(it) == null }
    val canSend = originId.isNotEmpty() && destinationId.isNotEmpty() && originId != destinationId &&
        lines.isNotEmpty() && !unresolved && !state.isBusy

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Nuevo traslado", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BrandColors.Primary,
                        titleContentColor = BrandColors.Ivory,
                        navigationIconContentColor = BrandColors.Ivory
                    )
                )
            },
            bottomBar = {
                Surface(color = BrandColors.Ivory, shadowElevation = 8.dp) {
                    AdaptiveContainer {
                        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
                            if (unresolved) Text(
                                "Hay una cantidad vacía o que no se puede convertir a la unidad del inventario.",
                                color = BrandColors.Danger, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Button(
                                onClick = { user?.let { viewModel.send(companyId, buildDraft(), it) } },
                                enabled = canSend,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
                            ) {
                                Text(
                                    if (destination != null) "Enviar a ${destination.name}" else "Enviar traslado",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = BrandColors.Ivory
        ) { padding ->
            AdaptiveContainer(modifier = Modifier.padding(padding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionLabel("Sale de")
                            if (origins.isEmpty()) Text("No tienes sucursales asignadas.", color = BrandColors.MutedSlate)
                            ChipRow {
                                origins.forEach { b ->
                                    BranchChip(b.name, selected = originId == b.id) {
                                        if (originId != b.id) {
                                            originId = b.id
                                            if (destinationId == b.id) destinationId = ""
                                        }
                                    }
                                }
                            }
                            SectionLabel("Va para")
                            ChipRow {
                                state.branches.filter { it.id != originId }.forEach { b ->
                                    BranchChip(b.name, selected = destinationId == b.id) { destinationId = b.id }
                                }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("Qué lleva", Modifier.weight(1f))
                            OutlinedButton(onClick = { picking = StockTransferItemKind.PRODUCT }, enabled = originId.isNotEmpty()) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Producto")
                            }
                            OutlinedButton(onClick = { picking = StockTransferItemKind.INGREDIENT }, enabled = originId.isNotEmpty()) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Insumo")
                            }
                        }
                    }
                    if (lines.isEmpty()) item {
                        Text(
                            if (originId.isEmpty()) "Primero elige de qué sucursal sale."
                            else "Agrega pan, reventa o insumos. Solo aparece lo que lleva inventario.",
                            color = BrandColors.MutedSlate,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    itemsIndexed(lines, key = { _, d -> d.kind + d.itemId }) { index, d ->
                        val qty = baseQuantity(d)
                        LineCard(
                            draft = d,
                            ingredient = ingredientsById[d.itemId],
                            available = available(d),
                            stockUnit = stockUnit(d),
                            baseQuantity = qty,
                            valueText = if (canSeeValue && qty != null) {
                                val cost = if (d.kind == StockTransferItemKind.PRODUCT)
                                    state.originProductStock[d.itemId]?.avgUnitCost?.takeIf { it > 0 } ?: productsById[d.itemId]?.purchaseCost ?: 0.0
                                else ingredientsById[d.itemId]?.costPerStockUnit() ?: 0.0
                                if (cost > 0) "≈ Q ${(cost * qty).fmtTrim()}" else null
                            } else null,
                            onChange = { updated -> lines = lines.toMutableList().also { it[index] = updated } },
                            onRemove = { lines = lines.filterIndexed { i, _ -> i != index } }
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(300) },
                            label = { Text("Nota (opcional)") },
                            placeholder = { Text("Quién lo lleva, para qué…") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item { Spacer(Modifier.padding(12.dp)) }
                }
            }
        }
        LoadingOverlay(visible = state.isBusy || state.isFormLoading, message = if (state.isBusy) "Enviando…" else "Cargando…")
    }

    val added = remember(lines) { lines.map { it.itemId }.toSet() }
    when (picking) {
        StockTransferItemKind.PRODUCT -> ProductPickerDialog(
            title = "¿Qué producto mandas?",
            // Lo que hay en el origen primero: es lo que se manda casi siempre.
            products = state.products.filter { it.id !in added }
                .sortedByDescending { (state.originProductStock[it.id]?.currentStock ?: 0.0) > 0.0 },
            stockOf = { id -> state.originProductStock[id]?.currentStock ?: 0.0 },
            showSearch = true,
            onDismiss = { picking = null },
            onSelect = { p: Product ->
                lines = lines + DraftLine(StockTransferItemKind.PRODUCT, p.id, p.name)
                picking = null
            }
        )
        StockTransferItemKind.INGREDIENT -> IngredientPickerDialog(
            ingredients = state.ingredients,
            alreadyAddedIds = added,
            title = "¿Qué insumo mandas?",
            onDismiss = { picking = null },
            onPick = { ing: Ingredient ->
                val fmt = ing.purchaseFormats.firstOrNull { it.isDefault } ?: ing.purchaseFormats.firstOrNull()
                lines = lines + DraftLine(
                    StockTransferItemKind.INGREDIENT, ing.id, ing.name,
                    formatId = fmt?.id.orEmpty(), looseUnit = ing.effectiveBaseUnit
                )
                picking = null
            }
        )
    }
}

@Composable
private fun LineCard(
    draft: DraftLine,
    ingredient: Ingredient?,
    available: Double,
    stockUnit: String,
    baseQuantity: Double?,
    valueText: String?,
    onChange: (DraftLine) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(draft.name, fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                    Text(
                        "Hay ${available.fmtTrim()} $stockUnit en el origen",
                        style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate
                    )
                }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Quitar") }
            }
            if (ingredient != null) {
                // Presentaciones + suelto: "2 sacos" o "8 lb", según cómo se agarre.
                ChipRow {
                    ingredient.purchaseFormats.forEach { f ->
                        BranchChip(f.description, selected = draft.formatId == f.id) { onChange(draft.copy(formatId = f.id)) }
                    }
                    ingredient.looseUnitOptions().forEach { u ->
                        BranchChip(u, selected = draft.formatId.isEmpty() && draft.looseUnit == u) {
                            onChange(draft.copy(formatId = "", looseUnit = u))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft.text,
                    onValueChange = { v -> onChange(draft.copy(text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(9))) },
                    label = { Text("Cantidad") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.widthIn(max = 160.dp)
                )
                Column {
                    if (ingredient != null && baseQuantity != null && (draft.formatId.isNotEmpty() || draft.looseUnit != stockUnit)) {
                        Text("= ${baseQuantity.fmtTrim()} $stockUnit", style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                    }
                    valueText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate) }
                }
            }
            if (baseQuantity != null && baseQuantity > available + 0.0001) {
                // Aviso, no bloqueo: el inventario puede venir atrasado (el conteo es al cierre).
                Text(
                    "Según el inventario solo hay ${available.fmtTrim()} $stockUnit. Se envía igual; revisa la cantidad.",
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.AccentDeep
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
}

@Composable
internal fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
internal fun BranchChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = BrandColors.PrimaryContainer,
            selectedLabelColor = BrandColors.Primary
        )
    )
}
