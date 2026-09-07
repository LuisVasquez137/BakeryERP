package com.bakery.erp.presentation.pos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bakery.erp.domain.BluetoothPrinterManager
import com.bakery.erp.model.POSCategory
import com.bakery.erp.model.Product
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.util.fmtMoney
import kotlinx.coroutines.launch
import com.bakery.erp.ui.theme.BrandColors

private const val FILTER_ALL = "__all__"

/** Naranja de aviso del sistema para sobreventa no bloqueante (ver BrandColors). */
private val POSWarningColor = BrandColors.AccentDeep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun POSScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    onNavigateToClose: (sessionId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: POSViewModel,
    cashSessionViewModel: CashSessionViewModel,
    bluetoothPrinterManager: BluetoothPrinterManager
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val user = authState.user
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(companyId, sessionId) {
        if (companyId.isNotEmpty()) viewModel.load(companyId, sessionId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    // El snackbar se elimina — ahora la confirmación de venta se muestra en SaleSuccessDialog
    // (ver más abajo). Mantenemos clearLastSaleSummary para no dejar el flag colgado.

    var selectedFilter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var quantityFor by remember { mutableStateOf<Product?>(null) }
    var pickerSearchOpen by remember { mutableStateOf(false) }
    var paymentOpen by remember { mutableStateOf(false) }
    var cartSheetOpen by remember { mutableStateOf(false) }
    var btPickerOpen by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }

    fun handlePrintRequest() {
        if (bluetoothPrinterManager.isPlatformSupported &&
            bluetoothPrinterManager.getSavedConfig() == null) {
            // Primera vez — mostrar picker. Tras configurar, dispara la impresión.
            btPickerOpen = true
        } else {
            viewModel.printLastReceipt()
        }
    }

    // Mapa productId -> CartLine (para tile selected state y tap-to-deselect)
    val cartByProductId = remember(state.cart) {
        state.cart.associateBy { it.productId }
    }
    val cartItemCount = state.cart.sumOf { it.quantity }.toInt()

    // Animación de "pulso" en el ícono del carrito cuando aumenta el contador
    val cartIconScale = remember { Animatable(1f) }
    var prevCartCount by remember { mutableStateOf(0) }
    LaunchedEffect(cartItemCount) {
        if (cartItemCount > prevCartCount && prevCartCount >= 0) {
            cartIconScale.snapTo(1.35f)
            cartIconScale.animateTo(1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        }
        prevCartCount = cartItemCount
    }

    // Productos filtrados por categoría seleccionada
    val productsToShow = remember(state.products, state.posCategories, selectedFilter) {
        if (selectedFilter == FILTER_ALL) state.products
        else {
            val cat = state.posCategories.find { it.id == selectedFilter }
            if (cat == null) state.products
            else state.products.filter { it.id in cat.productIds }
        }
    }

    fun handleProductTap(product: Product) {
        val existing = cartByProductId[product.id]
        if (existing != null) {
            // Ya está en el carrito → quitar (sin importar la cantidad)
            viewModel.removeLine(existing.key)
        } else {
            // No está → abrir diálogo de cantidad
            quantityFor = product
        }
    }

    BoxWithConstraints {
        val wide = maxWidth >= 1000.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(state.branch?.name ?: "Punto de Venta",
                                 fontWeight = FontWeight.SemiBold)
                            Text(
                                "${state.session?.registerName.orEmpty()} · ${state.session?.cashierName.orEmpty()} · " +
                                "Ventas: Q ${(state.session?.totalSales ?: 0.0).fmtMoney(2)} · " +
                                "#${state.session?.saleCount ?: 0}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    },
                    actions = {
                        IconButton(onClick = { pickerSearchOpen = true }) {
                            Icon(Icons.Default.Search, "Buscar producto")
                        }
                        // Carrito en TopAppBar — solo en modo angosto (en wide el panel siempre se ve)
                        if (!wide) {
                            BadgedBox(
                                badge = {
                                    if (cartItemCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = Color.White
                                        ) { Text("$cartItemCount") }
                                    }
                                },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                IconButton(
                                    onClick = { cartSheetOpen = true },
                                    modifier = Modifier.scale(cartIconScale.value)
                                ) {
                                    Icon(Icons.Default.ShoppingCart, "Ver carrito")
                                }
                            }
                        }
                        state.session?.let { sess ->
                            IconButton(onClick = { showTransfer = true }) {
                                Icon(Icons.Default.SwapHoriz, "Transferir efectivo a otra caja")
                            }
                            TextButton(onClick = { onNavigateToClose(sess.id) }) {
                                Text("Cerrar caja", color = MaterialTheme.colorScheme.onPrimary)
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
            if (state.isLoading && state.session == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            if (wide) {
                Row(Modifier.fillMaxSize().padding(padding)) {
                    Column(Modifier.weight(3f).fillMaxHeight()) {
                        CategoryFilterChips(
                            categories = state.posCategories,
                            selectedFilter = selectedFilter,
                            onSelect = { selectedFilter = it }
                        )
                        ProductGrid(
                            products = productsToShow,
                            cartByProductId = cartByProductId,
                            categoriesEmpty = state.posCategories.isEmpty(),
                            totalCategoriesCount = state.totalCategoriesCount,
                            branchName = state.branch?.name.orEmpty(),
                            onProductClick = ::handleProductTap,
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
                    CartPanel(
                        cart = state.cart,
                        total = viewModel.cartTotal(),
                        stockOf = { viewModel.stockOf(it) },
                        onIncrement = { line -> viewModel.updateLineQuantity(line.key, line.quantity + 1) },
                        onDecrement = { line -> viewModel.updateLineQuantity(line.key, line.quantity - 1) },
                        onRemoveLine = { key -> viewModel.removeLine(key) },
                        onCheckout = { paymentOpen = true },
                        modifier = Modifier.weight(0.95f).fillMaxHeight().padding(10.dp)
                    )
                }
            } else {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    CategoryFilterChips(
                        categories = state.posCategories,
                        selectedFilter = selectedFilter,
                        onSelect = { selectedFilter = it }
                    )
                    ProductGrid(
                        products = productsToShow,
                        cartByProductId = cartByProductId,
                        categoriesEmpty = state.posCategories.isEmpty(),
                        totalCategoriesCount = state.totalCategoriesCount,
                        branchName = state.branch?.name.orEmpty(),
                        onProductClick = ::handleProductTap,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp)
                    )
                    if (state.cart.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 8.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                                    .clickable { cartSheetOpen = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ShoppingCart, null, tint = Color.White)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "$cartItemCount artículo${if (cartItemCount == 1) "" else "s"}",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        "Q ${viewModel.cartTotal().fmtMoney(2)}",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                                Button(
                                    onClick = { paymentOpen = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Cobrar", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────

    if (pickerSearchOpen) {
        ProductPickerDialog(
            title = "Buscar producto",
            products = state.products,
            stockOf = { viewModel.stockOf(it) },
            showSearch = true,
            onDismiss = { pickerSearchOpen = false },
            onSelect = { p ->
                pickerSearchOpen = false
                handleProductTap(p)
            }
        )
    }

    quantityFor?.let { product ->
        QuantityDialog(
            title = product.name,
            subtitle = "Q ${product.salePrice.fmtMoney(2)} c/u",
            onDismiss = { quantityFor = null },
            onConfirm = { qty ->
                viewModel.addProductToCart(product, qty)
                quantityFor = null
            }
        )
    }

    if (paymentOpen) {
        PaymentDialog(
            total = viewModel.cartTotal(),
            onDismiss = { paymentOpen = false },
            onConfirm = { method, tendered ->
                paymentOpen = false
                cartSheetOpen = false
                user?.let { viewModel.confirmSale(companyId, it, method, tendered) }
            }
        )
    }

    // Diálogo de éxito tras venta cobrada — muestra resumen, vuelto e impresión de ticket.
    state.lastReceiptData?.let { receipt ->
        SaleSuccessDialog(
            total = receipt.total,
            change = receipt.changeAmount,
            paymentLabel = receipt.paymentMethodLabel,
            isPrinting = state.isPrinting,
            isPrintingAvailable = state.isPrintingAvailable,
            savedPrinterName = bluetoothPrinterManager.getSavedConfig()?.deviceName,
            onPrint = { handlePrintRequest() },
            onChangePrinter = {
                bluetoothPrinterManager.clearConfig()
                btPickerOpen = true
            },
            onDismiss = { viewModel.dismissLastSale() }
        )
    }

    if (btPickerOpen) {
        BluetoothPrinterPickerDialog(
            manager = bluetoothPrinterManager,
            onConfigured = {
                btPickerOpen = false
                viewModel.printLastReceipt()
            },
            onDismiss = { btPickerOpen = false }
        )
    }

    // Loading overlay durante el cobro (no durante carga inicial)
    if (state.isLoading && state.session != null) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp).widthIn(min = 220.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(56.dp))
                    Text(
                        "Procesando venta…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Descontando inventario y registrando la venta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (cartSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { cartSheetOpen = false },
            sheetState = sheetState
        ) {
            CartPanel(
                cart = state.cart,
                total = viewModel.cartTotal(),
                stockOf = { viewModel.stockOf(it) },
                onIncrement = { line -> viewModel.updateLineQuantity(line.key, line.quantity + 1) },
                onDecrement = { line -> viewModel.updateLineQuantity(line.key, line.quantity - 1) },
                onRemoveLine = { key -> viewModel.removeLine(key) },
                onCheckout = {
                    cartSheetOpen = false
                    paymentOpen = true
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .heightIn(min = 240.dp)
            )
        }
    }

    if (showTransfer && user != null) {
        val sess = state.session
        if (sess != null) {
            CashTransferDialog(
                companyId = companyId,
                currentSession = sess,
                currentUser = user,
                onDismiss = { showTransfer = false },
                onTransferred = {
                    showTransfer = false
                    viewModel.load(companyId, sessionId)
                },
                viewModel = cashSessionViewModel
            )
        }
    }
}

@Composable
private fun CategoryFilterChips(
    categories: List<POSCategory>,
    selectedFilter: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = FILTER_ALL) {
            FilterChipColored(
                selected = selectedFilter == FILTER_ALL,
                label = "Todos",
                color = MaterialTheme.colorScheme.primary,
                onClick = { onSelect(FILTER_ALL) }
            )
        }
        items(categories, key = { it.id }) { cat ->
            val color = runCatching { Color(("FF" + cat.color.removePrefix("#")).toLong(16)) }
                .getOrDefault(MaterialTheme.colorScheme.primary)
            FilterChipColored(
                selected = selectedFilter == cat.id,
                label = cat.name,
                color = color,
                onClick = { onSelect(cat.id) }
            )
        }
    }
}

@Composable
private fun FilterChipColored(
    selected: Boolean,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    val container = if (selected) color else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        tonalElevation = if (selected) 4.dp else 0.dp,
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ProductGrid(
    products: List<Product>,
    cartByProductId: Map<String, CartLine>,
    categoriesEmpty: Boolean,
    totalCategoriesCount: Int,
    branchName: String,
    onProductClick: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    if (products.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    if (categoriesEmpty && totalCategoriesCount > 0) Icons.Default.Info
                    else Icons.Default.ShoppingCart,
                    null, tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(8.dp))
                when {
                    categoriesEmpty && totalCategoriesCount > 0 -> {
                        Text(
                            "$totalCategoriesCount categoría(s) creada(s), pero ninguna aplica a esta sucursal (${branchName.ifEmpty { "—" }})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Revisa Admin > Categorías POS y verifica las sucursales asignadas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                    categoriesEmpty -> {
                        Text(
                            "Sin productos",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Crea categorías POS o usa la lupa para buscar productos por nombre.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        Text(
                            "Sin productos en esta categoría",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 118.dp),
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(products, key = { it.id }) { p ->
            val line = cartByProductId[p.id]
            ProductTile(
                product = p,
                quantityInCart = line?.quantity ?: 0.0,
                onClick = { onProductClick(p) }
            )
        }
    }
}

@Composable
private fun ProductTile(
    product: Product,
    quantityInCart: Double,
    onClick: () -> Unit
) {
    val selected = quantityInCart > 0
    val primary = MaterialTheme.colorScheme.primary
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .then(if (selected) Modifier.border(3.dp, primary, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 6.dp else 2.dp)
    ) {
        Box {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                ) {
                    ProductImage(
                        imageUrl = product.imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        product.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Q ${product.salePrice.fmtMoney(2)}",
                        color = primary,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
            if (selected) {
                Surface(
                    color = primary,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            quantityInCart.fmtMoney(0),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CartPanel(
    cart: List<CartLine>,
    total: Double,
    stockOf: (productId: String) -> Double,
    onIncrement: (CartLine) -> Unit,
    onDecrement: (CartLine) -> Unit,
    onRemoveLine: (key: String) -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val oversoldCount = cart.count { it.quantity > stockOf(it.productId) }
    Column(modifier) {
        Text("Carrito", fontWeight = FontWeight.SemiBold,
             style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        if (cart.isEmpty()) {
            Box(Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(min = 120.dp),
                contentAlignment = Alignment.Center) {
                Text("Toca un producto para agregar al carrito",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.outline,
                     textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 480.dp),
                       verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(cart, key = { it.key }) { line ->
                    CartLineRow(
                        line = line,
                        available = stockOf(line.productId),
                        onIncrement = { onIncrement(line) },
                        onDecrement = { onDecrement(line) },
                        onRemove = { onRemoveLine(line.key) }
                    )
                }
            }
        }
        if (oversoldCount > 0) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth()
                    .background(POSWarningColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, null, tint = POSWarningColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "$oversoldCount producto(s) sin stock suficiente. Puedes cobrar, pero el inventario quedará en negativo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = POSWarningColor
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", style = MaterialTheme.typography.titleMedium)
            Text("Q ${total.fmtMoney(2)}",
                 style = MaterialTheme.typography.headlineSmall,
                 fontWeight = FontWeight.ExtraBold,
                 color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onCheckout,
            enabled = cart.isNotEmpty() && total > 0,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Cobrar Q ${total.fmtMoney(2)}",
                 style = MaterialTheme.typography.titleMedium,
                 fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SaleSuccessDialog(
    total: Double,
    change: Double,
    paymentLabel: String,
    isPrinting: Boolean,
    isPrintingAvailable: Boolean,
    savedPrinterName: String?,
    onPrint: () -> Unit,
    onChangePrinter: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CheckCircle, null,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                "Venta cobrada",
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Q ${total.fmtMoney(2)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(paymentLabel, style = MaterialTheme.typography.labelMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (change > 0.0) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Vuelto", style = MaterialTheme.typography.labelMedium,
                                 color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(
                                "Q ${change.fmtMoney(2)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                if (isPrintingAvailable && savedPrinterName != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Impresora: $savedPrinterName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = onChangePrinter,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Cambiar", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Listo", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            if (isPrintingAvailable) {
                TextButton(
                    onClick = onPrint,
                    enabled = !isPrinting
                ) {
                    if (isPrinting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Imprimiendo…")
                    } else {
                        Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Imprimir ticket")
                    }
                }
            }
        }
    )
}

@Composable
private fun CartLineRow(
    line: CartLine,
    available: Double,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit
) {
    val oversold = line.quantity > available
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(10.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        line.productName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Q ${line.unitPrice.fmtMoney(2)} c/u",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (oversold) {
                        Text(
                            "Stock disponible: ${available.fmtMoney(0)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = POSWarningColor
                        )
                    }
                }
                Text(
                    "Q ${line.subtotal.fmtMoney(2)}",
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, "Quitar", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = onDecrement,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Remove, "Restar 1")
                }
                Text(
                    line.quantity.fmtMoney(0),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                FilledIconButton(
                    onClick = onIncrement,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, "Sumar 1")
                }
            }
        }
    }
}
