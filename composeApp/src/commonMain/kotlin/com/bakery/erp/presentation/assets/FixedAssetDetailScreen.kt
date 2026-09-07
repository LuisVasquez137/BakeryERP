package com.bakery.erp.presentation.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoveDown
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.AssetEventType
import com.bakery.erp.model.AssetHistoryEntry
import com.bakery.erp.model.AssetStatus
import com.bakery.erp.model.FixedAsset
import com.bakery.erp.model.Permission
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.util.calculateBookValue
import com.bakery.erp.util.fmtMoney
import kotlin.time.Clock
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedAssetDetailScreen(
    assetId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToHistoryForm: (assetId: String, entryId: String?) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: FixedAssetViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val canEdit = authState.user?.hasPermission(Permission.EDIT_ASSETS) == true
    val canManage = authState.user?.hasPermission(Permission.MANAGE_ASSETS) == true
    val canAddHistory = authState.user?.hasPermission(Permission.ADD_ASSET_HISTORY) == true ||
        authState.user?.hasPermission(Permission.EDIT_ASSETS) == true

    var selectedTab by remember { mutableStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(assetId, companyId) {
        if (companyId.isNotEmpty()) viewModel.loadAsset(companyId, assetId)
    }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) {
            viewModel.clearDeleted()
            onNavigateBack()
        }
    }

    val asset = state.selectedAsset

    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            TopAppBar(
                title = { Text(asset?.name ?: "Activo", fontWeight = FontWeight.SemiBold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (canEdit && asset != null) {
                        IconButton(onClick = { onNavigateToEdit(asset.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.White)
                        }
                    }
                    if (canManage && asset != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.White)
                        }
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
            if (selectedTab == 1 && canAddHistory && asset != null) {
                FloatingActionButton(
                    onClick = { onNavigateToHistoryForm(asset.id, null) },
                    containerColor = PrimaryBlue
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar evento", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isDetailLoading && asset == null ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                asset == null ->
                    Text(
                        "Activo no encontrado",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                else -> AdaptiveContainer(maxContentWidth = 1200.dp) {
                    Column(Modifier.fillMaxSize()) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = WarmIvory,
                            contentColor = PrimaryBlue
                        ) {
                            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                                text = { Text("General") })
                            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                                text = { Text("Historial (${state.historyEntries.size})") })
                            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                                text = { Text("Costos") })
                        }
                        when (selectedTab) {
                            0 -> GeneralTab(asset)
                            1 -> HistoryTab(
                                entries = state.historyEntries,
                                loading = state.isHistoryLoading,
                                onClickEntry = { entry ->
                                    if (canAddHistory) onNavigateToHistoryForm(asset.id, entry.id)
                                },
                                onDelete = { entry ->
                                    viewModel.deleteHistoryEntry(companyId, asset.id, entry.id)
                                },
                                canManage = canManage
                            )
                            2 -> CostsTab(asset, state.historyEntries)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog && asset != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar activo") },
            text = { Text("Esta acción dejará el activo inactivo. El historial se conserva. ¿Continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteAsset(companyId, asset.id)
                }) { Text("Eliminar", color = BrandColors.AccentDeep) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

private enum class HeaderImageMode { HERO, THUMBNAIL, NONE }

@Composable
private fun GeneralTab(asset: FixedAsset) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 900.dp
        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Columna izquierda — TODOS los datos (header + ubicación + adquisición +
                // valor en libros + mantenimiento + notas). Cada card ocupa el ancho completo
                // de esta columna.
                Column(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AssetHeaderCard(asset, mode = HeaderImageMode.NONE)
                    LocationCard(asset)
                    AcquisitionCard(asset)
                    if ((asset.usefulLifeMonths ?: 0) > 0) BookValueCard(asset)
                    if (asset.nextMaintenanceDate != null && asset.nextMaintenanceDate > 0L)
                        MaintenanceCard(asset)
                    if (!asset.notes.isNullOrEmpty()) NotesCard(asset.notes)
                    Spacer(Modifier.height(48.dp))
                }
                // Columna derecha — solo la imagen completa (Fit, sin recortar).
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    AssetImagePanel(asset)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { AssetHeaderCard(asset, mode = HeaderImageMode.THUMBNAIL) }
                item { LocationCard(asset) }
                item { AcquisitionCard(asset) }
                if ((asset.usefulLifeMonths ?: 0) > 0) item { BookValueCard(asset) }
                if (asset.nextMaintenanceDate != null && asset.nextMaintenanceDate > 0L)
                    item { MaintenanceCard(asset) }
                if (!asset.notes.isNullOrEmpty()) item { NotesCard(asset.notes) }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun AssetImagePanel(asset: FixedAsset) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 520.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!asset.imageUrl.isNullOrEmpty()) {
                ProductImage(
                    imageUrl = asset.imageUrl,
                    contentDescription = asset.name,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(120.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = PrimaryBlue.copy(alpha = 0.08f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Construction, contentDescription = null,
                                tint = PrimaryBlue, modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                    Text(
                        "Sin imagen registrada",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetHeaderCard(asset: FixedAsset, mode: HeaderImageMode) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mode == HeaderImageMode.HERO && !asset.imageUrl.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                ) {
                    ProductImage(
                        imageUrl = asset.imageUrl,
                        contentDescription = asset.name,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (mode == HeaderImageMode.THUMBNAIL) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
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
                                        Icon(
                                            Icons.Default.Construction, contentDescription = null,
                                            tint = PrimaryBlue, modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(asset.name, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        if (asset.assetCode.isNotEmpty()) Text(
                            asset.assetCode,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            asset.category.ifEmpty { "Sin categoría" },
                            style = MaterialTheme.typography.labelMedium,
                            color = PrimaryBlue
                        )
                    }
                    AssetStatusChip(asset.status)
                }
                if (asset.description.isNotEmpty()) {
                    HorizontalDivider()
                    Text(asset.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun LocationCard(asset: FixedAsset) {
    InfoCard("Ubicación") {
        InfoRow("Sucursal", asset.branchName.ifEmpty { "—" })
        if (asset.location.isNotEmpty()) InfoRow("Lugar dentro de la sucursal", asset.location)
        if (!asset.responsible.isNullOrEmpty()) InfoRow("Responsable", asset.responsible)
    }
}

@Composable
private fun AcquisitionCard(asset: FixedAsset) {
    InfoCard("Adquisición") {
        InfoRow("Fecha de compra", formatDate(asset.purchaseDate))
        InfoRow("Costo original", "Q ${asset.purchaseCost.fmtMoney(2)}")
        if (!asset.supplierName.isNullOrEmpty()) InfoRow("Proveedor", asset.supplierName)
        if (!asset.invoiceNumber.isNullOrEmpty()) InfoRow("Factura", asset.invoiceNumber)
        if (!asset.brand.isNullOrEmpty()) InfoRow("Marca", asset.brand)
        if (!asset.model.isNullOrEmpty()) InfoRow("Modelo", asset.model)
        if (!asset.serialNumber.isNullOrEmpty()) InfoRow("Número de serie", asset.serialNumber)
        if (asset.warrantyUntil != null && asset.warrantyUntil > 0L)
            InfoRow("Garantía hasta", formatDate(asset.warrantyUntil))
    }
}

@Composable
private fun BookValueCard(asset: FixedAsset) {
    val now = remember { Clock.System.now().toEpochMilliseconds() }
    val bv = calculateBookValue(
        asset.purchaseCost, asset.residualValue,
        asset.usefulLifeMonths!!, asset.purchaseDate, now
    )
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Valor en libros", color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium)
            Text("Q ${bv.bookValue.fmtMoney(2)}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall)
            Text(
                "Depreciación acumulada: Q ${bv.accumulatedDepreciation.fmtMoney(2)}",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "${bv.monthsElapsed} / ${asset.usefulLifeMonths} meses transcurridos",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall
            )
            if (asset.residualValue > 0.0)
                Text(
                    "Valor residual: Q ${asset.residualValue.fmtMoney(2)}",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall
                )
        }
    }
}

@Composable
private fun MaintenanceCard(asset: FixedAsset) {
    val now = remember { Clock.System.now().toEpochMilliseconds() }
    val nextDate = asset.nextMaintenanceDate ?: return
    val soon = nextDate in now..(now + 7L * 24 * 60 * 60 * 1000)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (soon) BurntOrange.copy(alpha = 0.10f) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Build, contentDescription = null,
                    tint = if (soon) BrandColors.AccentDeep else PrimaryBlue)
                Text("Próximo mantenimiento", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall)
            }
            Text(formatDate(nextDate),
                color = if (soon) BrandColors.AccentDeep else PrimaryBlue,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium)
            asset.maintenanceIntervalDays?.let {
                Text("Cada $it días", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NotesCard(notes: String) {
    InfoCard("Notas") {
        Text(notes, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HistoryTab(
    entries: List<AssetHistoryEntry>,
    loading: Boolean,
    onClickEntry: (AssetHistoryEntry) -> Unit,
    onDelete: (AssetHistoryEntry) -> Unit,
    canManage: Boolean
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 900.dp
        when {
            loading && entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Notes, contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(64.dp))
                    Text("Sin eventos registrados", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Toca + para registrar un mantenimiento, reparación, etc.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
            isWide -> HistoryTable(entries, onClickEntry, onDelete, canManage)
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    HistoryEntryCard(entry = entry, onClick = { onClickEntry(entry) },
                        onDelete = { onDelete(entry) }, canDelete = canManage)
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun HistoryTable(
    entries: List<AssetHistoryEntry>,
    onClickEntry: (AssetHistoryEntry) -> Unit,
    onDelete: (AssetHistoryEntry) -> Unit,
    canManage: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Surface(
            color = Color(0xFFF6F4EE),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tipo", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = PrimaryBlue,
                    modifier = Modifier.width(140.dp))
                Text("Fecha", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = PrimaryBlue,
                    modifier = Modifier.width(110.dp))
                Text("Descripción", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = PrimaryBlue,
                    modifier = Modifier.weight(1f))
                Text("Proveedor", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = PrimaryBlue,
                    modifier = Modifier.weight(0.7f))
                Text("Costo", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = PrimaryBlue,
                    modifier = Modifier.width(110.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End)
                Spacer(Modifier.width(if (canManage) 48.dp else 0.dp))
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                HistoryTableRow(
                    entry = entry,
                    onClick = { onClickEntry(entry) },
                    onDelete = { onDelete(entry) },
                    canDelete = canManage
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun HistoryTableRow(
    entry: AssetHistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    val canDeleteThis = canDelete && entry.type != AssetEventType.PURCHASE
    Surface(
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.width(140.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EventTypeIcon(entry.type, small = true)
                    Text(
                        entry.type.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Text(
                    formatDate(entry.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(110.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.description.ifEmpty { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (entry.type == AssetEventType.TRANSFER && !entry.toBranchName.isNullOrEmpty()) {
                        Text(
                            "${entry.fromBranchName ?: "—"} → ${entry.toBranchName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    entry.providerName ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.7f)
                )
                Text(
                    if (entry.type.hasCost && entry.cost > 0.0) "Q ${entry.cost.fmtMoney(2)}" else "—",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        !entry.type.hasCost || entry.cost <= 0.0 -> MaterialTheme.colorScheme.outline
                        entry.type == AssetEventType.SALE -> Color(0xFF1B7F3A)
                        else -> BrandColors.AccentDeep
                    },
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.width(110.dp)
                )
                if (canDelete) {
                    if (canDeleteThis) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.Delete, contentDescription = "Eliminar evento",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFF0F0F0))
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: AssetHistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (canDelete) it else it },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            EventTypeIcon(entry.type)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.type.label, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall, color = PrimaryBlue)
                    Text("·", color = MaterialTheme.colorScheme.outline)
                    Text(formatDate(entry.date), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (entry.description.isNotEmpty()) Text(
                    entry.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3
                )
                if (entry.type == AssetEventType.TRANSFER && !entry.toBranchName.isNullOrEmpty()) {
                    Text(
                        "${entry.fromBranchName ?: "—"} → ${entry.toBranchName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!entry.providerName.isNullOrEmpty()) Text(
                    "Proveedor: ${entry.providerName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entry.type.hasCost && entry.cost > 0.0) {
                Text(
                    "Q ${entry.cost.fmtMoney(2)}",
                    fontWeight = FontWeight.Bold,
                    color = if (entry.type == AssetEventType.SALE) Color(0xFF1B7F3A) else BrandColors.AccentDeep,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (canDelete && entry.type != AssetEventType.PURCHASE) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar evento",
                        tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun CostsTab(asset: FixedAsset, entries: List<AssetHistoryEntry>) {
    val totalMaint = entries.filter { it.type == AssetEventType.MAINTENANCE }.sumOf { it.cost }
    val totalRepair = entries.filter { it.type == AssetEventType.REPAIR }.sumOf { it.cost }
    val totalUpgrade = entries.filter { it.type == AssetEventType.UPGRADE }.sumOf { it.cost }
    val totalAcc = asset.purchaseCost + totalMaint + totalRepair + totalUpgrade

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Costo total acumulado", color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium)
                    Text("Q ${totalAcc.fmtMoney(2)}", color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Compra original + mantenimiento + reparaciones + mejoras",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        item {
            CostBreakdownCard("Compra original", asset.purchaseCost, Icons.Default.ShoppingCart, Color(0xFF1B7F3A))
        }
        item {
            CostBreakdownCard("Mantenimiento", totalMaint, Icons.Default.Build, PrimaryBlue)
        }
        item {
            CostBreakdownCard("Reparaciones", totalRepair, Icons.Default.Construction, BrandColors.AccentDeep)
        }
        item {
            CostBreakdownCard("Mejoras", totalUpgrade, Icons.Default.Upgrade, Color(0xFF7A4D9F))
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
}

@Composable
private fun CostBreakdownCard(label: String, amount: Double, icon: ImageVector, color: Color) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = color.copy(alpha = 0.12f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color)
                }
            }
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text("Q ${amount.fmtMoney(2)}", fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = PrimaryBlue,
                style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AssetStatusChip(status: AssetStatus) {
    val color = when (status) {
        AssetStatus.ACTIVE -> Color(0xFF1B7F3A)
        AssetStatus.IN_REPAIR -> BrandColors.AccentDeep
        AssetStatus.DISPOSED -> Color(0xFF7A7A7A)
        AssetStatus.SOLD -> Color(0xFF7A4D9F)
    }
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            status.label,
            color = color,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EventTypeIcon(type: AssetEventType, small: Boolean = false) {
    val (icon, color) = when (type) {
        AssetEventType.PURCHASE -> Icons.Default.ShoppingCart to Color(0xFF1B7F3A)
        AssetEventType.MAINTENANCE -> Icons.Default.Build to PrimaryBlue
        AssetEventType.REPAIR -> Icons.Default.Construction to BrandColors.AccentDeep
        AssetEventType.UPGRADE -> Icons.Default.Upgrade to Color(0xFF7A4D9F)
        AssetEventType.INSPECTION -> Icons.Default.Verified to Color(0xFF0D7A8C)
        AssetEventType.TRANSFER -> Icons.Default.MoveDown to Color(0xFF1A56A0)
        AssetEventType.DISPOSAL -> Icons.Default.RemoveCircle to Color(0xFF7A7A7A)
        AssetEventType.SALE -> Icons.Default.AttachMoney to Color(0xFF1B7F3A)
        AssetEventType.NOTE -> Icons.Default.Note to MaterialTheme.colorScheme.outline
    }
    val size = if (small) 28.dp else 40.dp
    val iconSize = if (small) 16.dp else 24.dp
    Surface(shape = CircleShape, color = color.copy(alpha = 0.12f), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
        }
    }
}

