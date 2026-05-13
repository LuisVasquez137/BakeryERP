package com.bakery.erp.presentation.purchases

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseStatus
import com.bakery.erp.presentation.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToForm: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: PurchaseViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val currentUser = authState.user
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId, currentUser) {
        if (companyId.isNotEmpty() && currentUser != null) {
            viewModel.loadPurchases(companyId, currentUser)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // Filtrado en memoria
    val displayed = remember(state.purchases, state.searchQuery, state.branchFilter) {
        state.purchases
            .filter { state.branchFilter == null || it.branchId == state.branchFilter }
            .filter {
                state.searchQuery.isBlank() ||
                it.supplierName.contains(state.searchQuery.trim(), ignoreCase = true) ||
                (it.invoiceNumber?.contains(state.searchQuery.trim(), ignoreCase = true) == true) ||
                it.branchName.contains(state.searchQuery.trim(), ignoreCase = true)
            }
    }

    // Sucursales únicas (para los chips de filtro)
    val uniqueBranches = remember(state.purchases) {
        state.purchases.map { it.branchId to it.branchName }.distinct()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compras", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToForm) {
                Icon(Icons.Default.Add, contentDescription = "Nueva compra")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Barra de búsqueda ─────────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .shadow(4.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                TextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = {
                        Text("Buscar por proveedor, factura o sucursal…", color = MaterialTheme.colorScheme.outline)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Chips de sucursal (solo si hay más de una) ───────────────────
            if (uniqueBranches.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.branchFilter == null,
                            onClick = { viewModel.setBranchFilter(null) },
                            label = { Text("Todas") }
                        )
                    }
                    items(uniqueBranches) { (branchId, branchName) ->
                        FilterChip(
                            selected = state.branchFilter == branchId,
                            onClick = { viewModel.setBranchFilter(if (state.branchFilter == branchId) null else branchId) },
                            label = { Text(branchName) }
                        )
                    }
                }
            }

            // ── Contenido adaptativo ──────────────────────────────────────────
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 900.dp
                val isTablet = maxWidth >= 600.dp

                when {
                    // ── PC / tablet horizontal: tabla izquierda + panel detalle ──
                    isWideScreen -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.fillMaxHeight().weight(0.4f)) {
                                PurchaseTableHeader()
                                when {
                                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                    displayed.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            if (state.searchQuery.isNotEmpty() || state.branchFilter != null) "Sin resultados"
                                            else "Sin compras registradas",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(displayed, key = { it.id }) { purchase ->
                                            PurchaseTableRow(
                                                purchase = purchase,
                                                isSelected = state.selectedPurchase?.id == purchase.id,
                                                onClick = { viewModel.selectPurchase(purchase, companyId) }
                                            )
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier.fillMaxHeight().width(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                            Box(modifier = Modifier.fillMaxHeight().weight(0.6f)) {
                                when {
                                    state.isDetailLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                    state.selectedPurchase != null -> PurchaseDetailContent(
                                        purchase = state.selectedPurchase!!,
                                        lines = state.selectedPurchaseLines
                                    )
                                    else -> Text(
                                        text = "Selecciona una compra para ver el detalle",
                                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // ── Tablet vertical: tabla full-width, navega al detalle ──────
                    isTablet -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            PurchaseTableHeader()
                            when {
                                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                                displayed.isEmpty() -> PurchaseEmptyState(
                                    hasFilters = state.searchQuery.isNotEmpty() || state.branchFilter != null,
                                    modifier = Modifier.fillMaxSize()
                                )
                                else -> LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    items(displayed, key = { it.id }) { purchase ->
                                        PurchaseTableRow(
                                            purchase = purchase,
                                            isSelected = false,
                                            onClick = { onNavigateToDetail(purchase.id) }
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }

                    // ── Móvil: lista de cards ─────────────────────────────────────
                    else -> {
                        when {
                            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                            displayed.isEmpty() -> PurchaseEmptyState(
                                hasFilters = state.searchQuery.isNotEmpty() || state.branchFilter != null,
                                modifier = Modifier.fillMaxSize()
                            )
                            else -> LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(displayed, key = { it.id }) { purchase ->
                                    PurchaseCard(
                                        purchase = purchase,
                                        onClick = { onNavigateToDetail(purchase.id) }
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
private fun PurchaseCard(purchase: Purchase, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = purchase.supplierName.ifEmpty { "Sin proveedor" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = purchase.branchName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatPurchaseDate(purchase.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    if (!purchase.invoiceNumber.isNullOrEmpty()) {
                        Text(
                            text = purchase.invoiceNumber,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${purchase.lineCount} insumo${if (purchase.lineCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Chip de estado
            val (statusColor, statusOnColor) = when (purchase.status) {
                PurchaseStatus.PAID -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                PurchaseStatus.PENDING_RECEIPT -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(shape = RoundedCornerShape(50), color = statusColor) {
                Text(
                    text = PurchaseStatus.label(purchase.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusOnColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun PurchaseTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text("Proveedor", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.3f))
        Text("Sucursal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.2f))
        Text("Fecha", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.18f))
        Text("Factura", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.17f))
        Text("Estado", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.15f))
    }
    HorizontalDivider()
}

@Composable
private fun PurchaseTableRow(purchase: Purchase, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = purchase.supplierName.ifEmpty { "Sin proveedor" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(0.3f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = purchase.branchName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatPurchaseDate(purchase.date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.18f)
        )
        Text(
            text = purchase.invoiceNumber ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = if (!purchase.invoiceNumber.isNullOrEmpty()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(0.17f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Estado
        val statusLabel = when (purchase.status) {
            PurchaseStatus.PAID -> "Pagado"
            PurchaseStatus.PENDING_RECEIPT -> "Por recibir"
            else -> "Pte. pago"
        }
        val statusColor = when (purchase.status) {
            PurchaseStatus.PAID -> MaterialTheme.colorScheme.primary
            PurchaseStatus.PENDING_RECEIPT -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        }
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.15f)
        )
    }
}

@Composable
private fun PurchaseEmptyState(hasFilters: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Text(
            text = if (hasFilters) "Sin resultados" else "Sin compras registradas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (hasFilters) "Prueba con otro término de búsqueda o filtro"
                   else "Toca + para registrar el ingreso de materia prima",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

internal fun formatPurchaseDate(millis: Long): String {
    if (millis == 0L) return "Sin fecha"
    var days = millis / 86_400_000L
    var year = 1970; var month = 1
    while (true) {
        val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
        val diy = if (leap) 366L else 365L
        if (days < diy) break
        days -= diy; year++
    }
    val daysInMonth = intArrayOf(31, if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    while (month <= 12 && days >= daysInMonth[month - 1]) {
        days -= daysInMonth[month - 1]; month++
    }
    val day = (days + 1).toInt()
    return "${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/$year"
}
