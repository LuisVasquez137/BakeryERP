package com.bakery.erp.presentation.pos.admin

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Branch
import com.bakery.erp.model.POSCategory
import com.bakery.erp.model.POS_CATEGORY_COLORS
import com.bakery.erp.model.POS_CATEGORY_NAME_MAX_CHARS
import com.bakery.erp.model.Product
import com.bakery.erp.model.groupDisplayName
import com.bakery.erp.model.matchesGroupPrice
import com.bakery.erp.model.productCategoryConflicts
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun POSCategoryFormScreen(
    categoryId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: POSCategoryAdminViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val user = authState.user
    val snackbar = remember { SnackbarHostState() }
    val isEdit = categoryId != null
    val needsLoad = isEdit && (state.selectedCategory == null || state.selectedCategory?.id != categoryId)

    LaunchedEffect(companyId, categoryId) {
        if (companyId.isNotEmpty() && user != null) {
            if (state.products.isEmpty() || state.branches.isEmpty()) viewModel.load(companyId, user)
            // Las categorías hay que tenerlas al día: de ellas sale qué productos ya están
            // usados y no se ofrecen acá.
            else viewModel.refreshCategories(companyId)
            if (categoryId != null) viewModel.loadCategory(companyId, categoryId) else viewModel.clearSelected()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.isSaved, state.isDeleted) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
        if (state.isDeleted) { viewModel.clearDeleted(); onNavigateBack() }
    }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text(if (isEdit) "Editar categoría" else "Nueva categoría", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    if (isEdit && state.selectedCategory != null) {
                        IconButton(onClick = { viewModel.deleteCategory(companyId, categoryId!!) }) {
                            Icon(Icons.Default.Delete, "Eliminar")
                        }
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
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (needsLoad && state.isDetailLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            POSCategoryFormContent(
                existing = if (isEdit) state.selectedCategory else null,
                products = state.products,
                branches = state.branches,
                allCategories = state.categories,
                isSaving = state.isLoading,
                onSave = { c, moved -> viewModel.saveCategory(companyId, c, moved) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun POSCategoryFormContent(
    existing: POSCategory?,
    products: List<Product>,
    branches: List<Branch>,
    allCategories: List<POSCategory>,
    isSaving: Boolean,
    onSave: (POSCategory, Set<String>) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(existing?.color ?: POS_CATEGORY_COLORS.first()) }
    var sortOrderText by rememberSaveable { mutableStateOf(existing?.sortOrder?.toString() ?: "0") }
    var active by rememberSaveable { mutableStateOf(existing?.active ?: true) }
    var sellAsGroup by rememberSaveable { mutableStateOf(existing?.sellAsGroup ?: false) }
    var groupPriceText by rememberSaveable {
        mutableStateOf(existing?.groupUnitPrice?.takeIf { it > 0 }?.fmt(2) ?: "")
    }
    var groupLabel by rememberSaveable { mutableStateOf(existing?.groupLabel.orEmpty()) }
    var search by rememberSaveable { mutableStateOf("") }
    var showOthers by rememberSaveable { mutableStateOf(false) }
    var selectedProducts by remember {
        mutableStateOf(existing?.productIds?.toSet() ?: emptySet())
    }
    var selectedBranches by remember {
        mutableStateOf(existing?.branchIds?.toSet() ?: emptySet())
    }
    // Productos traídos a propósito desde otra categoría: al guardar se quitan de aquélla.
    var movedProducts by remember { mutableStateOf(emptySet<String>()) }

    val nameTooLong = name.length > POS_CATEGORY_NAME_MAX_CHARS
    val nameEmpty = name.trim().isEmpty()
    val groupPrice = groupPriceText.replace(',', '.').toDoubleOrNull() ?: 0.0

    // Qué productos ya tiene OTRA categoría que se muestra en alguna sucursal en común.
    val draftForConflicts = (existing ?: POSCategory()).copy(branchIds = selectedBranches.toList())
    val conflicts = remember(allCategories, selectedBranches, existing?.id) {
        productCategoryConflicts(draftForConflicts, allCategories)
    }
    val normalizedSearch = search.trim().lowercase()
    fun matchesSearch(p: Product) = normalizedSearch.isEmpty() || p.name.lowercase().contains(normalizedSearch)

    // Libres o ya de esta categoría (incluidos los que se trajeron de otra).
    val available = products.filter { it.id !in conflicts || it.id in selectedProducts }
        .filter(::matchesSearch)
    val inOthers = products.filter { it.id in conflicts && it.id !in selectedProducts }
        .filter(::matchesSearch)

    fun priceMismatch(p: Product) = sellAsGroup && groupPrice > 0 && !matchesGroupPrice(p.salePrice, groupPrice)
    val selectedMismatched = products.filter { it.id in selectedProducts && priceMismatch(it) }
    val groupInvalid = sellAsGroup && (groupPrice <= 0.0 || selectedMismatched.isNotEmpty())

    fun toggle(p: Product) {
        selectedProducts = if (p.id in selectedProducts) selectedProducts - p.id else selectedProducts + p.id
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre (máx $POS_CATEGORY_NAME_MAX_CHARS letras)") },
            placeholder = { Text("Ej. Pan dulce, Bebidas, Q0.50") },
            singleLine = true,
            isError = nameTooLong,
            supportingText = {
                Text(
                    "${name.length}/$POS_CATEGORY_NAME_MAX_CHARS",
                    color = if (nameTooLong) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Color del chip", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            POS_CATEGORY_COLORS.forEach { hex ->
                val colorValue = runCatching { Color(("FF" + hex.removePrefix("#")).toLong(16)) }
                    .getOrDefault(MaterialTheme.colorScheme.primary)
                Box(
                    modifier = Modifier.size(36.dp)
                        .background(colorValue, CircleShape)
                        .border(
                            width = if (color == hex) 3.dp else 1.dp,
                            color = if (color == hex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { color = hex },
                    contentAlignment = Alignment.Center
                ) {
                    if (color == hex) Icon(Icons.Default.Check, null, tint = Color.White)
                }
            }
        }

        GroupSellSection(
            sellAsGroup = sellAsGroup,
            onSellAsGroupChange = {
                sellAsGroup = it
                // El caso de uso que lo motivó: la mayoría del pan cuesta Q0.50.
                if (it && groupPriceText.isBlank()) groupPriceText = "0.50"
            },
            priceText = groupPriceText,
            onPriceChange = { groupPriceText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
            label = groupLabel,
            onLabelChange = { groupLabel = it },
            previewName = POSCategory(groupUnitPrice = groupPrice, groupLabel = groupLabel).groupDisplayName,
            mismatchedNames = selectedMismatched.map { it.name }
        )

        Text(
            "Productos de la categoría (${selectedProducts.size})",
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Buscar producto") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (search.isNotEmpty()) IconButton(onClick = { search = "" }) {
                    Icon(Icons.Default.Clear, "Limpiar")
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(8.dp)) {
                // Los ya elegidos arriba: con una lista larga, lo que interesa ver primero es
                // qué tiene la categoría.
                available.sortedBy { if (it.id in selectedProducts) 0 else 1 }.forEach { p ->
                    val mismatch = priceMismatch(p)
                    val checked = p.id in selectedProducts
                    // Un producto con otro precio no se puede agregar a un grupo, pero si ya
                    // estaba se deja destildar para corregirlo.
                    val enabled = !mismatch || checked
                    ProductCheckRow(
                        product = p,
                        checked = checked,
                        enabled = enabled,
                        note = when {
                            mismatch -> "Cuesta Q ${p.salePrice.fmt(2)} — no es del grupo"
                            p.id in movedProducts -> "Se quitará de ${conflicts[p.id]?.name.orEmpty()}"
                            else -> null
                        },
                        noteIsWarning = mismatch,
                        onToggle = {
                            toggle(p)
                            if (checked && p.id in movedProducts) movedProducts = movedProducts - p.id
                        }
                    )
                }
                if (available.isEmpty()) {
                    Text(
                        if (normalizedSearch.isEmpty()) "Todos los productos ya están en otra categoría"
                        else "Ningún producto libre coincide con \"$search\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        if (inOthers.isNotEmpty()) {
            Surface(
                onClick = { showOthers = !showOthers },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${inOthers.size} producto(s) ya están en otras categorías",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(if (showOthers) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            if (showOthers) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(8.dp)) {
                        inOthers.forEach { p ->
                            val owner = conflicts[p.id]
                            val mismatch = priceMismatch(p)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        if (mismatch) "En ${owner?.name.orEmpty()} · cuesta Q ${p.salePrice.fmt(2)}"
                                        else "En ${owner?.name.orEmpty()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        selectedProducts = selectedProducts + p.id
                                        movedProducts = movedProducts + p.id
                                    },
                                    enabled = !mismatch
                                ) { Text("Mover aquí") }
                            }
                        }
                    }
                }
            }
        }

        Text("Sucursales donde aplica", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        BranchScopeBanner(
            branches = branches,
            selectedBranches = selectedBranches
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            branches.forEach { b ->
                FilterChip(
                    selected = b.id in selectedBranches,
                    onClick = {
                        selectedBranches = if (b.id in selectedBranches)
                            selectedBranches - b.id else selectedBranches + b.id
                    },
                    label = { Text(b.name) }
                )
            }
        }

        OutlinedTextField(
            value = sortOrderText,
            onValueChange = { sortOrderText = it.filter { c -> c.isDigit() } },
            label = { Text("Orden (menor = aparece primero)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = active, onCheckedChange = { active = it })
            Spacer(Modifier.width(8.dp))
            Text(if (active) "Activa" else "Inactiva")
        }

        Button(
            onClick = {
                val order = sortOrderText.toIntOrNull() ?: 0
                onSave(
                    (existing ?: POSCategory()).copy(
                        id = existing?.id.orEmpty(),
                        name = name.trim(),
                        productIds = selectedProducts.toList(),
                        branchIds = selectedBranches.toList(),
                        color = color,
                        sortOrder = order,
                        active = active,
                        sellAsGroup = sellAsGroup,
                        groupUnitPrice = if (sellAsGroup) groupPrice else 0.0,
                        groupLabel = if (sellAsGroup) groupLabel.trim() else ""
                    ),
                    movedProducts.intersect(selectedProducts)
                )
            },
            enabled = !isSaving && !nameEmpty && !nameTooLong && selectedProducts.isNotEmpty() && !groupInvalid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar")
        }
    }
}

@Composable
private fun ProductCheckRow(
    product: Product,
    checked: Boolean,
    enabled: Boolean,
    note: String?,
    noteIsWarning: Boolean,
    onToggle: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 4.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                product.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
            Text(
                note ?: "${product.category} · Q ${product.salePrice.fmt(2)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (noteIsWarning) BrandColors.AccentDeep else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun GroupSellSection(
    sellAsGroup: Boolean,
    onSellAsGroupChange: (Boolean) -> Unit,
    priceText: String,
    onPriceChange: (String) -> Unit,
    label: String,
    onLabelChange: (String) -> Unit,
    previewName: String,
    mismatchedNames: List<String>
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (sellAsGroup) BrandColors.AccentSoft else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cobrar como un solo botón", fontWeight = FontWeight.SemiBold)
                    Text(
                        "La cajera teclea cuántos panes, no cuáles. El conteo de cierre reparte " +
                            "lo vendido entre estos panes según lo que falta de cada uno.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = sellAsGroup, onCheckedChange = onSellAsGroupChange)
            }
            if (sellAsGroup) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = onPriceChange,
                        label = { Text("Precio c/u (Q)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = label,
                        onValueChange = onLabelChange,
                        label = { Text("Nombre en el ticket") },
                        placeholder = { Text(previewName) },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                }
                Text(
                    "Solo se pueden elegir productos que cuesten exactamente ese precio.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (mismatchedNames.isNotEmpty()) {
                    Text(
                        "Tienen otro precio y hay que quitarlos: ${mismatchedNames.take(4).joinToString()}" +
                            if (mismatchedNames.size > 4) " y ${mismatchedNames.size - 4} más" else "",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun BranchScopeBanner(
    branches: List<Branch>,
    selectedBranches: Set<String>
) {
    val applyToAll = selectedBranches.isEmpty()
    val container = if (applyToAll) Color(0xFFE6F4EA) else Color(0xFFFFF4D6)
    val onContainer = if (applyToAll) Color(0xFF1B5E20) else Color(0xFF8C6D00)
    val text = if (applyToAll) {
        "Aplica a TODAS las sucursales."
    } else {
        val names = branches.filter { it.id in selectedBranches }.joinToString { it.name }
        "Solo se verá en: $names. Si tu caja activa no está en esa lista, no podrás venderla."
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (applyToAll) Icons.Default.Check else Icons.Default.Info,
                contentDescription = null,
                tint = onContainer
            )
            Spacer(Modifier.width(10.dp))
            Text(text, color = onContainer, style = MaterialTheme.typography.bodySmall)
        }
    }
}
