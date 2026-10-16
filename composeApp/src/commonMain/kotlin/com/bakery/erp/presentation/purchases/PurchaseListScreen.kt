package com.bakery.erp.presentation.purchases

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.PurchaseKind
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.isQuick
import com.bakery.erp.model.listTitle
import com.bakery.erp.model.PurchaseDisplayStatus
import com.bakery.erp.model.PurchasePeriod
import com.bakery.erp.model.applyPurchaseFilters
import com.bakery.erp.model.balanceDue
import com.bakery.erp.model.daysUntilDue
import com.bakery.erp.model.displayStatus
import com.bakery.erp.model.purchaseKpis
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.ZoomableImageDialog
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtMoney
import kotlin.time.Clock
import com.bakery.erp.model.Product

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToForm: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToEdit: (String, Boolean) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: PurchaseViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val currentUser = authState.user
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    // Foto que se está viendo en grande desde la lista, sin entrar al detalle.
    var photoPreview by rememberSaveable { mutableStateOf<String?>(null) }
    // Facturas y compras rápidas se ven en pestañas separadas: son dos formas de gastar que
    // se revisan por preguntas distintas ("¿a quién le debo?" vs "¿en qué se fue la semana?"),
    // y mezcladas en una sola lista los KPIs de cuentas por pagar dejan de significar algo.
    var showQuickPurchases by rememberSaveable { mutableStateOf(false) }

    // Escucha en vivo mientras la lista está en pantalla; al salir se suelta.
    DisposableEffect(companyId, currentUser) {
        if (companyId.isNotEmpty() && currentUser != null) {
            viewModel.loadPurchases(companyId, currentUser)
        }
        onDispose { viewModel.stopObservingPurchases() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }

    // `now` se fija una vez por composición de la pantalla: "vencida" no puede cambiar de
    // valor a mitad de un scroll, y recalcularlo por fila haría inestable el `remember`.
    val now = remember { Clock.System.now().toEpochMilliseconds() }

    val displayed = remember(state.purchases, state.filters, now, showQuickPurchases) {
        state.purchases
            .filter { it.isQuick == showQuickPurchases }
            .applyPurchaseFilters(state.filters, now)
    }
    val kpis = remember(displayed, now) { displayed.purchaseKpis(now) }

    // Las sucursales y proveedores del filtro salen de lo que REALMENTE hay en las compras
    // del usuario: ofrecer un proveedor sin facturas solo lleva a una lista vacía.
    val uniqueBranches = remember(state.purchases) {
        state.purchases.map { it.branchId to it.branchName }
            .filter { it.first.isNotEmpty() }
            .distinct()
            .sortedBy { it.second.lowercase() }
    }
    val uniqueSuppliers = remember(state.purchases) {
        state.purchases.map { it.supplierId to it.supplierName }
            .filter { it.first.isNotEmpty() }
            .distinct()
            .sortedBy { it.second.lowercase() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Compras", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        FilterIconWithBadge(state.filters.activeCount) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filtros")
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
        floatingActionButton = {
            // Solo en Facturas. Una compra rápida ya no se registra desde acá: se anota
            // donde salió el dinero —el acceso de Operaciones, o la caja abierta del POS—
            // porque eso es lo que decide de qué gaveta se descuenta. Un alta desde Compras
            // no tiene forma de saberlo y nacía siempre como caja general, que es
            // exactamente el faltante de caja que nadie podía explicar.
            if (!showQuickPurchases) {
                FloatingActionButton(onClick = onNavigateToForm) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva factura")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Facturas / Compras rápidas ────────────────────────────────────
            // Las dos pestañas están SIEMPRE, aunque no haya ninguna compra rápida todavía.
            //
            // La segunda es de CONSULTA Y CORRECCIÓN, no de alta: es el único lugar donde se
            // ven completas. Las que entran a inventario —materia prima, reventa— no generan
            // `Expense`, así que no aparecen en Gastos, y sin esta pestaña un monto mal
            // tecleado en una compra de tostadas no tendría dónde corregirse.
            TabRow(selectedTabIndex = if (showQuickPurchases) 1 else 0) {
                Tab(
                    selected = !showQuickPurchases,
                    onClick = { showQuickPurchases = false },
                    text = { Text("Facturas") }
                )
                Tab(
                    selected = showQuickPurchases,
                    onClick = { showQuickPurchases = true },
                    text = { Text("Compras rápidas") }
                )
            }

            // Sin esto la pestaña se lee como rota: no tiene botón de crear y no dice dónde
            // está el que sí funciona.
            if (showQuickPurchases) {
                Text(
                    "Se anotan desde \"Anotar gasto\" o desde la caja abierta, que es lo que " +
                        "decide de qué gaveta salió el dinero. Acá se consultan y se corrigen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

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
                    value = state.filters.query,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = {
                        Text("Buscar proveedor, factura o insumo…", color = MaterialTheme.colorScheme.outline)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    },
                    trailingIcon = {
                        if (state.filters.query.isNotEmpty()) {
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

            PurchaseKpiRow(
                kpis = kpis,
                canSeeAmounts = true,
                onOverdueClick = { viewModel.showOverdue() },
                onPendingReceiptClick = { viewModel.toggleStatusFilter(PurchaseDisplayStatus.PENDING_RECEIPT) }
            )

            Spacer(Modifier.height(8.dp))

            PurchaseQuickFilters(
                filters = state.filters,
                onPeriodSelected = { period, from, until -> viewModel.setPeriod(period, from, until) },
                onToggleStatus = { viewModel.toggleStatusFilter(it) }
            )

            Spacer(Modifier.height(8.dp))

            // Deuda vencida que NO está en pantalla. Es el aviso que no puede depender del
            // período: una factura de mayo sin pagar es exactamente la que hay que cobrar en
            // septiembre, y con la lista en "Este mes" no aparecería por ningún lado.
            val hiddenOverdue = remember(state.purchases, displayed, now) {
                val shown = displayed.mapTo(HashSet()) { it.id }
                state.purchases.filter {
                    it.id !in shown && it.displayStatus(now) == PurchaseDisplayStatus.OVERDUE
                }
            }
            if (hiddenOverdue.isNotEmpty()) {
                HiddenOverdueNotice(
                    count = hiddenOverdue.size,
                    amount = hiddenOverdue.sumOf { it.balanceDue },
                    onShow = { viewModel.showOverdue() }
                )
            }

            // Resultados fuera del período mostrado: el caso típico de "¿cuándo compré
            // aceite?". Vale para cualquier filtro, no solo para la búsqueda escrita — elegir
            // el chip "Vencida" en un mes sin compras también parecía decir que no hay ninguna.
            val outsidePeriod = remember(state.purchases, state.filters, displayed, now) {
                if (state.filters.query.isBlank() && state.filters.activeCount == 0) 0
                else state.purchases.applyPurchaseFilters(
                    state.filters.copy(period = PurchasePeriod.ALL), now
                ).size - displayed.size
            }
            if (outsidePeriod > 0) {
                WiderSearchNotice(
                    count = outsidePeriod,
                    onExpand = { viewModel.setPeriod(PurchasePeriod.ALL) }
                )
            }

            // ── Contenido adaptativo ──────────────────────────────────────────
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 900.dp
                val isTablet = maxWidth >= 600.dp
                val hasFilters = state.filters.hasAny

                when {
                    // ── PC / tablet horizontal: tabla izquierda + panel detalle ──
                    isWideScreen -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.fillMaxHeight().weight(0.45f)) {
                                PurchaseTableHeader()
                                when {
                                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                    displayed.isEmpty() -> PurchaseEmptyState(hasFilters, Modifier.fillMaxSize())
                                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(displayed, key = { it.id }) { purchase ->
                                            PurchaseTableRow(
                                                purchase = purchase,
                                                now = now,
                                                isSelected = state.selectedPurchase?.id == purchase.id,
                                                onClick = { viewModel.selectPurchase(purchase, companyId) },
                                                onPhotoClick = { photoPreview = purchase.invoicePhotoUrl }
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
                            Box(modifier = Modifier.fillMaxHeight().weight(0.55f)) {
                                when {
                                    state.isDetailLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                    state.selectedPurchase != null -> PurchaseDetailContent(
                                        imageByProductId = remember(state.allResaleProducts) {
                                            resaleImagesByProductId(state.allResaleProducts)
                                        },
                                        purchase = state.selectedPurchase!!,
                                        lines = state.selectedPurchaseLines,
                                        payments = state.selectedPurchasePayments,
                                        currentUser = currentUser,
                                        onStatusChange = null,
                                        onReceive = {
                                            currentUser?.let {
                                                viewModel.receivePurchase(companyId, state.selectedPurchase!!.id, it)
                                            }
                                        },
                                        onAddPayment = { amount, date, method, reference, note ->
                                            currentUser?.let {
                                                viewModel.addPayment(
                                                    companyId, state.selectedPurchase!!.id,
                                                    amount, date, method, reference, note, it
                                                )
                                            }
                                        },
                                        onDeletePayment = { paymentId ->
                                            viewModel.deletePayment(companyId, state.selectedPurchase!!.id, paymentId)
                                        },
                                        onDueDateChange = { due ->
                                            viewModel.updateDueDate(companyId, state.selectedPurchase!!.id, due)
                                        },
                                        onSettleInFull = { d, m, r ->
                                            currentUser?.let {
                                                viewModel.settleInFull(
                                                    companyId, state.selectedPurchase!!.id, d, m, r, it
                                                )
                                            }
                                        },
                                        onEdit = {
                                            val p = state.selectedPurchase!!
                                            onNavigateToEdit(p.id, p.isQuick)
                                        }
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
                                displayed.isEmpty() -> PurchaseEmptyState(hasFilters, Modifier.fillMaxSize())
                                else -> LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    items(displayed, key = { it.id }) { purchase ->
                                        PurchaseTableRow(
                                            purchase = purchase,
                                            now = now,
                                            isSelected = false,
                                            onClick = { onNavigateToDetail(purchase.id) },
                                            onPhotoClick = { photoPreview = purchase.invoicePhotoUrl }
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
                            displayed.isEmpty() -> PurchaseEmptyState(hasFilters, Modifier.fillMaxSize())
                            else -> LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(displayed, key = { it.id }) { purchase ->
                                    PurchaseCard(
                                        purchase = purchase,
                                        now = now,
                                        onClick = { onNavigateToDetail(purchase.id) },
                                        onPhotoClick = { photoPreview = purchase.invoicePhotoUrl }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }

    photoPreview?.takeIf { it.isNotEmpty() }?.let { url ->
        ZoomableImageDialog(
            imageUrl = url,
            title = "Factura",
            onDismiss = { photoPreview = null }
        )
    }

    if (showFilterSheet) {
        PurchaseFilterSheet(
            filters = state.filters,
            branches = uniqueBranches,
            suppliers = uniqueSuppliers,
            onToggleBranch = { viewModel.toggleBranchFilter(it) },
            onToggleSupplier = { viewModel.toggleSupplierFilter(it) },
            onSortSelected = { viewModel.setSort(it) },
            onClear = { viewModel.clearFilters() },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun HiddenOverdueNotice(count: Int, amount: Double, onShow: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFFBE0E0)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$count factura${if (count != 1) "s" else ""} vencida${if (count != 1) "s" else ""} " +
                    "por Q${amount.fmtMoney()}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFB3261E),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Ver",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB3261E),
                modifier = Modifier.clickable(onClick = onShow).padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun WiderSearchNotice(count: Int, onExpand: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = BrandColors.PrimaryContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hay $count factura${if (count != 1) "s" else ""} más fuera de este período",
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.Primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Ver todo",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = BrandColors.Primary,
                modifier = Modifier.clickable(onClick = onExpand).padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun PurchaseCard(purchase: Purchase, now: Long, onClick: () -> Unit, onPhotoClick: () -> Unit) {
    val status = purchase.displayStatus(now)
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
                        text = purchase.listTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.TextSlate,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = purchase.branchName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatPurchaseDate(purchase.date) +
                            purchase.receivedByName.takeIf { it.isNotBlank() }?.let { " · Registró $it" }.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Q${purchase.totalAmount.fmtMoney()}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.Primary
                    )
                    if (!purchase.invoiceNumber.isNullOrEmpty()) {
                        Text(
                            text = "Fact. ${purchase.invoiceNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${purchase.lineCount} línea${if (purchase.lineCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PurchaseStatusPill(status)
                if (purchase.invoicePhotoUrl.isNotEmpty()) {
                    IconButton(onClick = onPhotoClick, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = "Ver la factura",
                            modifier = Modifier.size(18.dp),
                            tint = BrandColors.Primary
                        )
                    }
                }
                val note = purchase.dueNote(now)
                if (note != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status == PurchaseDisplayStatus.OVERDUE) Color(0xFFB3261E)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (purchase.balanceDue > 0.01 && status != PurchaseDisplayStatus.PENDING_RECEIPT) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Saldo Q${purchase.balanceDue.fmtMoney()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.AccentDeep
                    )
                }
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
        HeaderCell("Proveedor", 0.25f)
        HeaderCell("Sucursal", 0.15f)
        HeaderCell("Fecha", 0.14f)
        HeaderCell("Factura", 0.13f)
        HeaderCell("Total", 0.14f, TextAlign.End)
        HeaderCell("Estado", 0.19f)
    }
    HorizontalDivider()
}

@Composable
private fun RowScope.HeaderCell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.Start
) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        textAlign = align,
        modifier = Modifier.weight(weight)
    )
}

@Composable
private fun PurchaseTableRow(
    purchase: Purchase,
    now: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPhotoClick: () -> Unit
) {
    val status = purchase.displayStatus(now)
    // El estado se lee de un vistazo por el color de la fila, no solo por el chip del final:
    // una barra de acento a la izquierda más un fondo muy tenue del mismo tono. Se eligió un
    // alpha bajo (0.14) a propósito — un fondo saturado convertiría la tabla en un
    // resaltador y volvería ilegible el texto en la columna de montos.
    val (statusBg, statusFg) = purchaseStatusColors(status)
    val neutral = status == PurchaseDisplayStatus.PENDING_PAYMENT
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    neutral -> Color.Transparent
                    else -> statusBg.copy(alpha = 0.35f)
                }
            )
            .clickable(onClick = onClick)
            .padding(start = 4.dp)
            .drawStatusAccent(if (neutral) Color.Transparent else statusFg)
            .padding(start = 8.dp, end = 12.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // El proveedor es el dato por el que uno busca una factura en la lista; iba con el
        // mismo peso y tamaño que la sucursal y la fecha, así que no había dónde apoyar la
        // vista al recorrerla.
        Text(
            text = purchase.listTitle(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = BrandColors.TextSlate,
            modifier = Modifier.weight(0.25f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = purchase.branchName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.15f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Column(modifier = Modifier.weight(0.14f)) {
            Text(
                text = formatPurchaseDate(purchase.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            purchase.dueNote(now)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status == PurchaseDisplayStatus.OVERDUE) Color(0xFFB3261E)
                            else MaterialTheme.colorScheme.outline
                )
            }
        }
        Text(
            text = purchase.invoiceNumber ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = if (!purchase.invoiceNumber.isNullOrEmpty()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(0.13f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Column(
            modifier = Modifier.weight(0.14f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "Q${purchase.totalAmount.fmtMoney()}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            if (purchase.balanceDue > 0.01 && status != PurchaseDisplayStatus.PENDING_RECEIPT &&
                purchase.paidAmount > 0.01
            ) {
                Text(
                    text = "saldo ${purchase.balanceDue.fmtMoney()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.AccentDeep
                )
            }
        }
        Row(
            modifier = Modifier.weight(0.19f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            PurchaseStatusPill(status, short = true)
            // Atajo a la foto sin entrar al detalle: al cotejar una tanda de facturas, el
            // papel se consulta muchas más veces de las que se abre la compra completa.
            if (purchase.invoicePhotoUrl.isNotEmpty()) {
                IconButton(onClick = onPhotoClick, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = "Ver la factura",
                        modifier = Modifier.size(16.dp),
                        tint = BrandColors.Primary
                    )
                }
            }
        }
    }
}

/** Barra de 3dp pegada al borde izquierdo de la fila, del color del estado. */
private fun Modifier.drawStatusAccent(color: Color): Modifier = drawBehind {
    if (color == Color.Transparent) return@drawBehind
    drawRect(
        color = color,
        topLeft = Offset(0f, 0f),
        size = Size(3.dp.toPx(), size.height)
    )
}

/** "Vence en 3 días" / "Vencida hace 5 días". Null cuando la factura no tiene crédito. */
internal fun Purchase.dueNote(now: Long): String? {
    if (dueDate <= 0L || balanceDue <= 0.01) return null
    val days = daysUntilDue(now) ?: return null
    return when {
        days < 0 -> "Vencida hace ${-days} d"
        days == 0 -> "Vence hoy"
        days <= 7 -> "Vence en $days d"
        else -> null
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
            text = if (hasFilters) "Sin resultados" else "Sin compras en este período",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (hasFilters) "Prueba con otro término, otro período o quita los filtros"
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
