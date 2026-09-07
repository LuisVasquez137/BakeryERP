package com.bakery.erp.presentation.pos.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Branch
import com.bakery.erp.model.POSCategory
import com.bakery.erp.model.POS_CATEGORY_COLORS
import com.bakery.erp.model.POS_CATEGORY_NAME_MAX_CHARS
import com.bakery.erp.model.Product
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer

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
            TopAppBar(
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
                onSave = { c -> viewModel.saveCategory(companyId, c) }
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
    onSave: (POSCategory) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(existing?.color ?: POS_CATEGORY_COLORS.first()) }
    var sortOrderText by rememberSaveable { mutableStateOf(existing?.sortOrder?.toString() ?: "0") }
    var active by rememberSaveable { mutableStateOf(existing?.active ?: true) }
    var selectedProducts by remember {
        mutableStateOf(existing?.productIds?.toSet() ?: emptySet())
    }
    var selectedBranches by remember {
        mutableStateOf(existing?.branchIds?.toSet() ?: emptySet())
    }

    val nameTooLong = name.length > POS_CATEGORY_NAME_MAX_CHARS
    val nameEmpty = name.trim().isEmpty()

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre corto (máx $POS_CATEGORY_NAME_MAX_CHARS letras)") },
            placeholder = { Text("Ej. Peq., Esp., Bebi.") },
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

        Text(
            "Productos de la categoría (${selectedProducts.size})",
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(8.dp)) {
                products.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedProducts = if (p.id in selectedProducts)
                                selectedProducts - p.id else selectedProducts + p.id
                        }.padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = p.id in selectedProducts,
                            onCheckedChange = { checked ->
                                selectedProducts = if (checked) selectedProducts + p.id
                                                   else selectedProducts - p.id
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyMedium)
                            Text(p.category, style = MaterialTheme.typography.labelSmall,
                                 color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                if (products.isEmpty()) {
                    Text("Sin productos disponibles", style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         modifier = Modifier.padding(8.dp))
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
                        active = active
                    )
                )
            },
            enabled = !nameEmpty && !nameTooLong && selectedProducts.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar")
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
