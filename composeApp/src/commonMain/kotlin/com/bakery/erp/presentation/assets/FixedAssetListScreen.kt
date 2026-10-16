package com.bakery.erp.presentation.assets

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Branch
import com.bakery.erp.model.FixedAsset
import com.bakery.erp.model.Permission
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.branches.BranchViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.util.calculateBookValue
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedAssetListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (assetId: String) -> Unit,
    onNavigateToForm: () -> Unit,
    authViewModel: AuthViewModel,
    branchViewModel: BranchViewModel,
    viewModel: FixedAssetViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val branchState by branchViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canEdit = user?.hasPermission(Permission.EDIT_ASSETS) == true
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedBranchId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) {
            viewModel.loadAssets(companyId)
            branchViewModel.loadBranches(companyId)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val accessibleBranches = remember(branchState.branchList, user) {
        if (user == null) emptyList()
        else branchState.branchList.filter { it.active && user.canAccessBranch(it.id) }
    }

    val filteredAssets = remember(state.assets, selectedBranchId, selectedCategory, searchText) {
        state.assets.filter { asset ->
            val branchOk = selectedBranchId == null || asset.branchId == selectedBranchId
            val catOk = selectedCategory == null || asset.category == selectedCategory
            val search = searchText.trim().lowercase()
            val searchOk = search.isEmpty() ||
                asset.name.lowercase().contains(search) ||
                asset.assetCode.lowercase().contains(search) ||
                (asset.brand?.lowercase()?.contains(search) ?: false) ||
                (asset.model?.lowercase()?.contains(search) ?: false)
            branchOk && catOk && searchOk
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            BrandTopAppBar(
                title = { Text("Activos fijos", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = onNavigateToForm,
                    containerColor = PrimaryBlue
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo activo", tint = Color.White)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AdaptiveContainer(maxContentWidth = 1100.dp) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        AssetKpiRow(state.assets)
                    }
                    item {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = { Text("Buscar por nombre, código, marca…") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (accessibleBranches.size > 1) {
                        item {
                            BranchChipsRow(
                                branches = accessibleBranches,
                                selectedId = selectedBranchId,
                                onSelect = { selectedBranchId = it }
                            )
                        }
                    }
                    item {
                        Box {
                            FilterChip(
                                selected = selectedCategory != null,
                                onClick = { categoryMenuExpanded = true },
                                label = { Text(selectedCategory ?: "Todas las categorías") },
                                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            DropdownMenu(
                                expanded = categoryMenuExpanded,
                                onDismissRequest = { categoryMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Todas") },
                                    onClick = { selectedCategory = null; categoryMenuExpanded = false }
                                )
                                com.bakery.erp.model.ASSET_CATEGORIES.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = { selectedCategory = cat; categoryMenuExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    when {
                        state.isLoading && state.assets.isEmpty() -> item {
                            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        filteredAssets.isEmpty() -> item {
                            EmptyAssetsPlaceholder(hasAny = state.assets.isNotEmpty())
                        }
                        else -> items(filteredAssets, key = { it.id }) { asset ->
                            AssetRowCard(asset = asset, onClick = { onNavigateToDetail(asset.id) })
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }
}

@Composable
private fun AssetKpiRow(assets: List<FixedAsset>) {
    val now = remember { Clock.System.now().toEpochMilliseconds() }
    val totalCost = assets.sumOf { it.purchaseCost }
    val totalBook = assets.sumOf {
        if ((it.usefulLifeMonths ?: 0) > 0)
            calculateBookValue(it.purchaseCost, it.residualValue, it.usefulLifeMonths!!, it.purchaseDate, now).bookValue
        else it.purchaseCost
    }
    val totalMaintenance = assets.sumOf { it.totalMaintenanceCost + it.totalRepairCost + it.totalUpgradeCost }
    val upcoming = assets.count {
        val d = it.nextMaintenanceDate ?: return@count false
        d in now..(now + 30L * 24 * 60 * 60 * 1000)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KpiSmallCard("Activos", assets.size.toString(), Modifier.weight(1f))
        KpiSmallCard("Inversión", "Q ${totalCost.fmtMoney(0)}", Modifier.weight(1f))
        KpiSmallCard("En libros", "Q ${totalBook.fmtMoney(0)}", Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KpiSmallCard("Invertido en mantenimiento", "Q ${totalMaintenance.fmtMoney(0)}", Modifier.weight(1f))
        KpiSmallCard(
            label = "Mantenimientos próximos (30 d)",
            value = upcoming.toString(),
            modifier = Modifier.weight(1f),
            accent = if (upcoming > 0) BrandColors.AccentDeep else null
        )
    }
}

@Composable
private fun KpiSmallCard(label: String, value: String, modifier: Modifier = Modifier, accent: Color? = null) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall,
                color = accent ?: PrimaryBlue)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BranchChipsRow(branches: List<Branch>, selectedId: String?, onSelect: (String?) -> Unit) {
    val listState = rememberLazyListState()
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("Todas") }
            )
        }
        items(branches, key = { it.id }) { b ->
            FilterChip(
                selected = selectedId == b.id,
                onClick = { onSelect(b.id) },
                label = { Text(b.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryBlue.copy(alpha = 0.15f),
                    selectedLabelColor = PrimaryBlue
                )
            )
        }
    }
}

@Composable
private fun AssetRowCard(asset: FixedAsset, onClick: () -> Unit) {
    val now = remember { Clock.System.now().toEpochMilliseconds() }
    val maintenanceSoon = asset.nextMaintenanceDate?.let {
        it in now..(now + 7L * 24 * 60 * 60 * 1000)
    } ?: false
    val bookValue = if ((asset.usefulLifeMonths ?: 0) > 0)
        calculateBookValue(asset.purchaseCost, asset.residualValue, asset.usefulLifeMonths!!, asset.purchaseDate, now).bookValue
    else asset.purchaseCost

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                if (!asset.imageUrl.isNullOrEmpty()) {
                    ProductImage(
                        imageUrl = asset.imageUrl,
                        contentDescription = asset.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = PrimaryBlue.copy(alpha = 0.08f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Construction, contentDescription = null, tint = PrimaryBlue)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        asset.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (maintenanceSoon) {
                        Surface(shape = CircleShape, color = BrandColors.AccentDeep) {
                            Box(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning, null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        "Mant.",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    buildString {
                        if (asset.assetCode.isNotEmpty()) { append(asset.assetCode); append(" · ") }
                        append(asset.category.ifEmpty { "Sin categoría" })
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (asset.branchName.isNotEmpty()) {
                    Text(
                        "📍 ${asset.branchName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text("Costo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text("Q ${asset.purchaseCost.fmtMoney(2)}", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column {
                        Text("En libros", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text("Q ${bookValue.fmtMoney(2)}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = PrimaryBlue)
                    }
                }
            }
            StatusBadge(asset)
        }
    }
}

@Composable
private fun StatusBadge(asset: FixedAsset) {
    val color = when (asset.status) {
        com.bakery.erp.model.AssetStatus.ACTIVE -> Color(0xFF1B7F3A)
        com.bakery.erp.model.AssetStatus.IN_REPAIR -> BrandColors.AccentDeep
        com.bakery.erp.model.AssetStatus.DISPOSED -> Color(0xFF7A7A7A)
        com.bakery.erp.model.AssetStatus.SOLD -> Color(0xFF7A4D9F)
    }
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            asset.status.label,
            color = color,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyAssetsPlaceholder(hasAny: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Inventory2, contentDescription = null,
                tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(64.dp))
            Text(
                if (hasAny) "Sin resultados con esos filtros" else "Sin activos registrados",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasAny) Text(
                "Toca + para registrar tu primer activo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

internal fun formatDate(millis: Long): String {
    if (millis <= 0L) return "—"
    val tz = TimeZone.currentSystemDefault()
    val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)
    return "${d.dayOfMonth.toString().padStart(2, '0')}/${d.monthNumber.toString().padStart(2, '0')}/${d.year}"
}
