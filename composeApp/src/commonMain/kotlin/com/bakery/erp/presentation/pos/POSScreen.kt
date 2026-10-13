package com.bakery.erp.presentation.pos

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.material.icons.filled.People
import com.bakery.erp.model.expectedCashNow
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.Permission
import com.bakery.erp.model.groupDisplayName
import com.bakery.erp.model.groupSaleKey
import com.bakery.erp.model.PackagingItem
import com.bakery.erp.model.Product
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.util.fmt
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
    /**
     * Anotar un gasto pagado con el efectivo de ESTA caja. Se ofrece desde acá —y no solo
     * desde el módulo de Gastos— porque es donde está quien tiene la gaveta abierta, y así
     * la caja no hay que elegirla: sale de la que se está operando.
     */
    onNavigateToCashExpense: (sessionId: String) -> Unit,
    /**
     * Movimientos de la caja abierta. Es donde la cajera ve lo que se pagó de su gaveta y
     * puede ir a corregirlo: hasta que existió, anotaba un gasto y no tenía forma de revisarlo
     * ni de deshacerlo sin cerrar la caja.
     */
    onNavigateToSessionDetail: (sessionId: String) -> Unit = {},
    authViewModel: AuthViewModel,
    viewModel: POSViewModel,
    cashSessionViewModel: CashSessionViewModel,
    bluetoothPrinterManager: BluetoothPrinterManager,
    /** Cuentas de crédito: vender fiado, cobrar abonos y dar adelantos desde la caja. */
    creditViewModel: com.bakery.erp.presentation.credit.CreditViewModel? = null,
    /** Comprobantes que no son ticket de venta (anticipos de clientes). */
    documentPrinter: com.bakery.erp.domain.DocumentPrinter? = null
) {
    val state by viewModel.uiState.collectAsState()
    val creditState = creditViewModel?.uiState?.collectAsState()?.value
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
    val canSellOnCredit = user?.hasPermission(Permission.SELL_ON_CREDIT) == true
    val canCollectCredit = user?.hasPermission(Permission.COLLECT_CREDIT_PAYMENT) == true
    val canGiveAdvance = user?.hasPermission(Permission.GIVE_EMPLOYEE_ADVANCE) == true
    val usesCredit = creditViewModel != null && (canSellOnCredit || canCollectCredit || canGiveAdvance)
    // Las cuentas se recargan tras cada abono o venta fiada: el saldo que muestra el buscador
    // es lo que la cajera le dice al cliente.
    // Se cargan aunque la cajera no maneje créditos: el descuento de colaborador por pagar en
    // efectivo sale de esas mismas cuentas.
    LaunchedEffect(companyId, state.notice, state.lastReceiptData) {
        if (creditViewModel != null && companyId.isNotEmpty()) creditViewModel.load(companyId)
    }
    var paymentDiscountAccount by remember { mutableStateOf<com.bakery.erp.model.CreditAccount?>(null) }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() }
    }
    var creditCollectOpen by remember { mutableStateOf(false) }
    var advanceOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.notice, state.isCreditBusy) {
        // Cerrar el diálogo cuando terminó bien (hay aviso) — si falló, queda abierto con el error.
        if (!state.isCreditBusy && state.notice != null) { creditCollectOpen = false; advanceOpen = false }
    }
    LaunchedEffect(state.justCollected) {
        state.justCollected?.let { collected ->
            snackbar.showSnackbar(
                "Recibido: ${collected.externalTableLabel.ifEmpty { "cuenta" }} · Q ${collected.total.fmt(2)}"
            )
            viewModel.clearJustCollected()
        }
    }
    // El snackbar se elimina — ahora la confirmación de venta se muestra en SaleSuccessDialog
    // (ver más abajo). Mantenemos clearLastSaleSummary para no dejar el flag colgado.

    var selectedFilter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var quantityFor by remember { mutableStateOf<Product?>(null) }
    var groupQuantityFor by remember { mutableStateOf<POSCategory?>(null) }
    var pickerSearchOpen by remember { mutableStateOf(false) }
    var paymentOpen by remember { mutableStateOf(false) }
    // Anticipos de clientes: recibir, ver los pendientes y el elegido en el cobro.
    var depositReceiveOpen by remember { mutableStateOf(false) }
    var depositsListOpen by remember { mutableStateOf(false) }
    var paymentDeposit by remember { mutableStateOf<com.bakery.erp.model.CustomerDeposit?>(null) }
    // Al abrir el cobro o la lista se traen los anticipos al día: otra caja pudo aplicar uno.
    LaunchedEffect(paymentOpen, depositsListOpen) {
        if (paymentOpen || depositsListOpen) viewModel.loadOpenDeposits(companyId)
    }
    // Pedidos con saldo, para ligarles el anticipo (pasteles, encargos especiales).
    LaunchedEffect(depositReceiveOpen) {
        if (depositReceiveOpen) {
            viewModel.loadDepositOrders(companyId)
            viewModel.loadPickupBranches(companyId)
        }
    }
    // Anticipo guardado (con o sin impresión): se cierra el diálogo y se recarga la caja, porque
    // el efectivo esperado cambió.
    LaunchedEffect(state.depositSavedAt) {
        if (state.depositSavedAt > 0L && depositReceiveOpen) {
            depositReceiveOpen = false
            viewModel.load(companyId, sessionId)
        }
    }
    // Comprobante por imprimir: recién recibido con "Guardar e imprimir", o una reimpresión.
    LaunchedEffect(state.depositToPrint) {
        val d = state.depositToPrint ?: return@LaunchedEffect
        documentPrinter?.takeIf { it.isAvailable }?.print(
            com.bakery.erp.util.buildCustomerDepositDocument(d, viewModel.receiptCompanyName(), formatReceiptTimestamp(d.receivedAt))
        )?.onFailure { e -> snackbar.showSnackbar("No se pudo imprimir el comprobante: ${e.message}") }
        viewModel.consumeDepositToPrint()
    }
    var cartSheetOpen by remember { mutableStateOf(false) }
    var btPickerOpen by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var showIncome by remember { mutableStateOf(false) }
    var pendingSheetOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var lastCheckoutOpen by remember { mutableStateOf(false) }
    var resellerPickerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(resellerPickerOpen) {
        if (resellerPickerOpen) viewModel.loadResellers(companyId)
    }
    val resale = state.resellerSale
    /** Un toque: entra a venta a revendedor, o vuelve a mostrador si ya está en una. */
    fun toggleResellerSale() {
        if (resale != null) viewModel.endResellerSale() else resellerPickerOpen = true
    }

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
        state.cart.filter { it.hasProductStock }.associateBy { it.productId }
    }
    val cartItemCount = state.cart.sumOf { it.quantity }.toInt()

    // Animación de "pulso" en el ícono del carrito cuando aumenta el contador
    val cartIconScale = remember { Animatable(1f) }
    val cartFlight = remember { CartFlightState() }
    // El ícono late cuando LLEGA el vuelo, no cuando se toca: así el ojo sigue a la miniatura
    // y el latido le confirma que entró.
    LaunchedEffect(cartFlight.arrivals) {
        if (cartFlight.arrivals > 0) {
            cartIconScale.snapTo(1.3f)
            cartIconScale.animateTo(1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        }
    }
    val highlightItem = cartFlight.lastArrivedItem
    val highlightAt = cartFlight.lastArrivedAt
    // Las fotos del catálogo quedan decodificadas a tamaño de ficha antes de dibujarse.
    val productImageUrls = remember(state.products) {
        state.products.mapNotNull { it.imageUrl?.takeIf { u -> u.isNotBlank() } }
    }
    com.bakery.erp.ui.PrefetchImages(productImageUrls)

    val selectedGroup = state.posCategories.firstOrNull { it.id == selectedFilter && it.sellAsGroup }

    // Productos filtrados por categoría seleccionada
    val productsToShow = remember(state.products, state.posCategories, selectedFilter) {
        if (selectedFilter == FILTER_ALL) {
            // Los panes de un grupo de precio no salen sueltos en "Todos": se cobran por su
            // botón de grupo. Vendidos uno por uno funcionarían, pero invitan a la cajera a
            // buscar el tipo de pan, que es justo lo que el grupo viene a evitar.
            val grouped = state.posCategories.filter { it.sellAsGroup }
                .flatMap { it.productIds }.toSet()
            state.products.filterNot { it.id in grouped }
                .sortedWith(
                    compareByDescending<Product> { state.popularityByKey[it.id] ?: 0.0 }
                        .thenBy { it.name }
                )
        } else {
            val cat = state.posCategories.find { it.id == selectedFilter }
            if (cat == null) state.products
            else state.products.filter { it.id in cat.productIds }
        }
    }

    // En "Todos" cada grupo de precio es una ficha más, ordenada junto a los productos por lo
    // que más se pide. Tocarla pregunta cuántos panes.
    val groupTilesInAll = if (selectedFilter == FILTER_ALL)
        state.posCategories.filter { it.sellAsGroup } else emptyList()
    val groupQtyInCart = state.cart.filter { it.isGroup }
        .associate { it.quickGroupId to it.quantity }

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

    CompositionLocalProvider(LocalCartFlight provides cartFlight) {
    BoxWithConstraints {
        val wide = maxWidth >= 1000.dp
        // Teléfono (sobre todo en vertical): no caben cinco acciones en la barra. Las dos que
        // no son de uso continuo se van a un menú.
        val compact = maxWidth < 700.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        // `maxLines = 1` no es cosmético: el TopAppBar le cede el ancho a las
                        // acciones, y en teléfono vertical el título quedaba con espacio para
                        // una letra — el nombre de la sucursal se dibujaba en vertical, una
                        // letra por renglón. Con una sola línea se recorta con puntos suspensivos.
                        Column {
                            Text(
                                state.branch?.name ?: "Punto de Venta",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                // Solo cuántas ventas, nunca el monto: la caja la ve cualquiera que pase
                                // por el mostrador, y cuánto se lleva vendido se ve al cierre. En vez del
                                // usuario (la tablet tiene uno solo), el colaborador que abrió la caja.
                                if (compact)
                                    "${state.session?.saleCount ?: 0} ventas"
                                else
                                    listOf(
                                        state.session?.registerName.orEmpty(),
                                        state.session?.openedByEmployeeName.orEmpty().ifBlank { state.session?.cashierName.orEmpty() },
                                        "${state.session?.saleCount ?: 0} ventas"
                                    ).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    },
                    actions = {
                        // Cuentas del restaurante pendientes de entregar a caja. El botón
                        // solo existe cuando hay algo: en una sucursal sin restaurante nunca
                        // aparece, sin necesidad de configurar nada.
                        if (state.pendingCollections.isNotEmpty()) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = BrandColors.Accent,
                                        contentColor = BrandColors.OnAccent
                                    ) { Text("${state.pendingCollections.size}") }
                                },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                IconButton(onClick = { pendingSheetOpen = true }) {
                                    Icon(Icons.Default.Restaurant, "Cuentas del restaurante")
                                }
                            }
                        }
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
                                modifier = Modifier.padding(end = 4.dp).cartFlightTarget()
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
                            if (compact) {
                                IconButton(onClick = {
                                    viewModel.loadRecentCheckouts(companyId)
                                    lastCheckoutOpen = true
                                }) {
                                    Icon(Icons.Default.History, "Último cobro")
                                }
                                Box {
                                    IconButton(onClick = { overflowOpen = true }) {
                                        Icon(Icons.Default.MoreVert, "Más opciones")
                                    }
                                    DropdownMenu(
                                        expanded = overflowOpen,
                                        onDismissRequest = { overflowOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (resale != null) "Volver a mostrador" else "Venta a revendedor") },
                                            onClick = { overflowOpen = false; toggleResellerSale() },
                                            leadingIcon = { Icon(Icons.Default.Storefront, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Anotar gasto") },
                                            onClick = { overflowOpen = false; onNavigateToCashExpense(sess.id) },
                                            leadingIcon = { Icon(Icons.Default.Receipt, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Anotar ingreso") },
                                            onClick = { overflowOpen = false; showIncome = true },
                                            leadingIcon = { Icon(Icons.Default.Receipt, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Anticipo de cliente") },
                                            onClick = { overflowOpen = false; depositReceiveOpen = true },
                                            leadingIcon = { Icon(Icons.Default.People, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Anticipos pendientes") },
                                            onClick = { overflowOpen = false; depositsListOpen = true },
                                            leadingIcon = { Icon(Icons.Default.People, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Movimientos de la caja") },
                                            onClick = { overflowOpen = false; onNavigateToSessionDetail(sess.id) },
                                            leadingIcon = { Icon(Icons.Default.Receipt, null) }
                                        )
                                        if (usesCredit && canCollectCredit) DropdownMenuItem(
                                            text = { Text("Cobrar crédito") },
                                            onClick = { overflowOpen = false; creditCollectOpen = true },
                                            leadingIcon = { Icon(Icons.Default.People, null) }
                                        )
                                        if (usesCredit && canGiveAdvance) DropdownMenuItem(
                                            text = { Text("Adelanto a colaborador") },
                                            onClick = { overflowOpen = false; advanceOpen = true },
                                            leadingIcon = { Icon(Icons.Default.People, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Transferir efectivo") },
                                            onClick = { overflowOpen = false; showTransfer = true },
                                            leadingIcon = { Icon(Icons.Default.SwapHoriz, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Cerrar caja") },
                                            onClick = { overflowOpen = false; onNavigateToClose(sess.id) }
                                        )
                                    }
                                }
                            } else {
                                IconButton(onClick = {
                                    viewModel.loadRecentCheckouts(companyId)
                                    lastCheckoutOpen = true
                                }) {
                                    Icon(Icons.Default.History, "Último cobro")
                                }
                                TextButton(onClick = { toggleResellerSale() }) {
                                    Text(
                                        if (resale != null) "Mostrador" else "Revendedor",
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                IconButton(onClick = { onNavigateToCashExpense(sess.id) }) {
                                    Icon(Icons.Default.Receipt, "Anotar un gasto de esta caja")
                                }
                                TextButton(onClick = { showIncome = true }) {
                                    Text("Ingreso", color = MaterialTheme.colorScheme.onPrimary)
                                }
                                var depositMenu by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { depositMenu = true }) {
                                        Text("Anticipos", color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                    DropdownMenu(expanded = depositMenu, onDismissRequest = { depositMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Recibir anticipo") },
                                            onClick = { depositMenu = false; depositReceiveOpen = true }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Anticipos pendientes") },
                                            onClick = { depositMenu = false; depositsListOpen = true }
                                        )
                                    }
                                }
                                TextButton(onClick = { onNavigateToSessionDetail(sess.id) }) {
                                    Text("Movimientos", color = MaterialTheme.colorScheme.onPrimary)
                                }
                                if (usesCredit && (canCollectCredit || canGiveAdvance)) {
                                    var creditMenu by remember { mutableStateOf(false) }
                                    Box {
                                        TextButton(onClick = { creditMenu = true }) {
                                            Text("Créditos", color = MaterialTheme.colorScheme.onPrimary)
                                        }
                                        DropdownMenu(expanded = creditMenu, onDismissRequest = { creditMenu = false }) {
                                            if (canCollectCredit) DropdownMenuItem(
                                                text = { Text("Cobrar crédito") },
                                                onClick = { creditMenu = false; creditCollectOpen = true }
                                            )
                                            if (canGiveAdvance) DropdownMenuItem(
                                                text = { Text("Adelanto a colaborador") },
                                                onClick = { creditMenu = false; advanceOpen = true }
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = { showTransfer = true }) {
                                    Icon(Icons.Default.SwapHoriz, "Transferir efectivo a otra caja")
                                }
                                TextButton(onClick = { onNavigateToClose(sess.id) }) {
                                    Text("Cerrar caja", color = MaterialTheme.colorScheme.onPrimary)
                                }
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
                        resale?.let { ResellerSaleBanner(it, onEnd = { viewModel.endResellerSale() }) }
                        CategoryFilterChips(
                            categories = state.posCategories,
                            selectedFilter = selectedFilter,
                            onSelect = { selectedFilter = it }
                        )
                        if (selectedGroup != null) {
                            GroupSellPanel(
                                group = selectedGroup,
                                unitPrice = viewModel.groupPriceFor(selectedGroup),
                                inCart = state.cart.firstOrNull { it.quickGroupId == selectedGroup.id }?.quantity ?: 0.0,
                                onAdd = { qty ->
                                viewModel.addGroupToCart(selectedGroup, qty)
                                cartFlight.launch("grpq_${qty.toInt()}", "grp_${selectedGroup.id}", null, "+${qty.toInt()}")
                            },
                                onOtherQuantity = { groupQuantityFor = selectedGroup },
                                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        } else {
                            ProductGrid(
                                products = productsToShow,
                                cartByProductId = cartByProductId,
                                categoriesEmpty = state.posCategories.isEmpty(),
                                totalCategoriesCount = state.totalCategoriesCount,
                                branchName = state.branch?.name.orEmpty(),
                                onProductClick = ::handleProductTap,
                                groups = groupTilesInAll,
                                groupQtyInCart = groupQtyInCart,
                                popularity = state.popularityByKey,
                                onGroupClick = { groupQuantityFor = it },
                                priceOf = { viewModel.unitPriceFor(it) },
                                groupPriceOf = { viewModel.groupPriceFor(it) },
                                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
                    CartPanel(
                        cart = state.cart,
                        total = viewModel.cartTotal(),
                        stockOf = { viewModel.stockOf(it) },
                        onIncrement = { line -> viewModel.updateLineQuantity(line.key, line.quantity + 1) },
                        onDecrement = { line -> viewModel.updateLineQuantity(line.key, line.quantity - 1) },
                        onRemoveLine = { key -> viewModel.removeLine(key) },
                        packagingItems = state.packagingItems,
                        onAddPackaging = { viewModel.addPackagingToCart(it) },
                        onCheckout = { paymentOpen = true },
                        highlightItem = highlightItem,
                        highlightAt = highlightAt,
                        // Sin `cartFlightTarget`: en horizontal el carrito ya está a la vista y
                        // el vuelo cruzando la pantalla distrae. Basta con que el renglón aparezca.
                        modifier = Modifier.weight(0.95f).fillMaxHeight().padding(10.dp)
                    )
                }
            } else {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    resale?.let { ResellerSaleBanner(it, onEnd = { viewModel.endResellerSale() }) }
                    CategoryFilterChips(
                        categories = state.posCategories,
                        selectedFilter = selectedFilter,
                        onSelect = { selectedFilter = it }
                    )
                    if (selectedGroup != null) {
                        GroupSellPanel(
                            group = selectedGroup,
                            unitPrice = viewModel.groupPriceFor(selectedGroup),
                            inCart = state.cart.firstOrNull { it.quickGroupId == selectedGroup.id }?.quantity ?: 0.0,
                            onAdd = { qty ->
                                viewModel.addGroupToCart(selectedGroup, qty)
                                cartFlight.launch("grpq_${qty.toInt()}", "grp_${selectedGroup.id}", null, "+${qty.toInt()}")
                            },
                            onOtherQuantity = { groupQuantityFor = selectedGroup },
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp)
                        )
                    } else {
                        ProductGrid(
                            products = productsToShow,
                            cartByProductId = cartByProductId,
                            categoriesEmpty = state.posCategories.isEmpty(),
                            totalCategoriesCount = state.totalCategoriesCount,
                            branchName = state.branch?.name.orEmpty(),
                            onProductClick = ::handleProductTap,
                            groups = groupTilesInAll,
                            groupQtyInCart = groupQtyInCart,
                            popularity = state.popularityByKey,
                            onGroupClick = { groupQuantityFor = it },
                            priceOf = { viewModel.unitPriceFor(it) },
                            groupPriceOf = { viewModel.groupPriceFor(it) },
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp)
                        )
                    }
                    if (state.cart.isNotEmpty() && state.packagingItems.isNotEmpty()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            PackagingQuickBar(
                                items = state.packagingItems,
                                inCart = state.cart.packagingCounts(),
                                onAdd = { viewModel.addPackagingToCart(it) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
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
        CartFlightOverlay(cartFlight)
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
            subtitle = viewModel.unitPriceFor(product)?.let { "Q ${it.fmtMoney(2)} c/u" } ?: "Sin precio para este revendedor",
            onDismiss = { quantityFor = null },
            onConfirm = { qty ->
                viewModel.addProductToCart(product, qty)
                quantityFor = null
                cartFlight.launch(product.id, product.id, product.imageUrl, "+${qty.fmtMoney(0)}")
            }
        )
    }

    groupQuantityFor?.let { group ->
        QuantityDialog(
            title = group.groupDisplayName,
            subtitle = viewModel.groupPriceFor(group)?.let { "Q ${it.fmtMoney(2)} c/u" } ?: "Sin precio único para este revendedor",
            onDismiss = { groupQuantityFor = null },
            onConfirm = { qty ->
                viewModel.addGroupToCart(group, qty)
                groupQuantityFor = null
                // Desde la ficha del grupo en "Todos" o desde "Otra cantidad" del panel.
                val src = if (cartFlight.sources.containsKey("grp_${group.id}")) "grp_${group.id}" else "grpq_other"
                cartFlight.launch(src, "grp_${group.id}", null, "+${qty.fmtMoney(0)}")
            }
        )
    }

    if (resellerPickerOpen) {
        ResellerPickerDialog(
            resellers = state.resellers,
            isLoading = state.isLoadingResellers,
            onDismiss = { resellerPickerOpen = false },
            onSelect = { r ->
                resellerPickerOpen = false
                viewModel.startResellerSale(companyId, r)
            }
        )
    }

    if (paymentOpen) {
        PaymentDialog(
            onDismiss = { paymentOpen = false; paymentDiscountAccount = null },
            total = viewModel.cartTotal(),
            onConfirm = { method, tendered, bills, invoice, split ->
                paymentOpen = false
                cartSheetOpen = false
                val discount = paymentDiscountAccount
                paymentDiscountAccount = null
                val deposit = paymentDeposit
                paymentDeposit = null
                user?.let {
                    viewModel.confirmSale(
                        companyId, it, method, tendered, bills,
                        discountAccount = discount, invoice = invoice, deposit = deposit, split = split
                    )
                }
            },
            deposits = state.openDeposits,
            onDepositChange = { paymentDeposit = it },
            invoiceOptions = if (viewModel.canInvoice()) {
                val billable = viewModel.invoiceableRestaurantIds()
                val restaurant = state.cart.filter { l -> l.isRestaurant }
                InvoiceOptions(
                    defaultOn = viewModel.invoiceByDefault(),
                    invoiceableTotal = state.cart.filter { l -> l.isBakery }.sumOf { l -> l.subtotal } +
                        restaurant.filter { l -> l.pendingSaleId in billable }.sumOf { l -> l.subtotal },
                    excludesBilledRestaurant = restaurant.any { l -> l.pendingSaleId !in billable },
                    lookupNit = { nit -> viewModel.lookupNit(companyId, nit) }
                )
            } else null,
            // Al revendedor: su precio ya es el rebajado y se le vende de contado.
            discountAccounts = if (resale != null) emptyList() else creditState?.accounts.orEmpty()
                .filter { it.isEmployee && it.active && it.cashDiscountEligible },
            employeeDiscount = viewModel.employeeDiscountAmount(),
            onDiscountChange = { paymentDiscountAccount = it },
            // Una cuenta del restaurante en el carrito ya es venta cobrada: no se fía.
            creditAccounts = creditState?.accounts
                ?.takeIf { canSellOnCredit && resale == null && state.cart.none { l -> l.isRestaurant } },
            onConfirmCredit = { charge, invoice ->
                paymentDiscountAccount = null
                paymentOpen = false
                cartSheetOpen = false
                user?.let {
                    viewModel.confirmSale(companyId, it, SalePaymentMethod.CREDIT, 0.0, emptyMap(), charge, invoice = invoice)
                }
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
            invoice = state.invoiceProgress,
            printedAt = state.printedAt,
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

    // Solo durante el cobro. `isLoading` sube también al recargar la pantalla —al volver de
    // anotar un gasto, por ejemplo— y entonces este diálogo mentía diciendo que se estaba
    // procesando una venta.
    if (state.isCheckingOut) {
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
                packagingItems = state.packagingItems,
                onAddPackaging = { viewModel.addPackagingToCart(it) },
                onCheckout = {
                    cartSheetOpen = false
                    paymentOpen = true
                },
                highlightItem = highlightItem,
                highlightAt = highlightAt,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .heightIn(min = 240.dp)
            )
        }
    }

    if (pendingSheetOpen) {
        PendingCollectionsSheet(
            pending = state.pendingCollections,
            isCollecting = state.isCollecting,
            onCollect = { sale ->
                user?.let { viewModel.collectPendingSale(companyId, sale, it) }
            },
            onDismiss = { pendingSheetOpen = false },
            inCartIds = state.cart.filter { it.isRestaurant }.map { it.pendingSaleId }.toSet(),
            onAddToCart = { sale ->
                viewModel.addPendingToCart(sale)
                pendingSheetOpen = false
            }
        )
    }

    if (creditCollectOpen && creditViewModel != null && creditState != null) {
        CreditCollectDialog(
            accounts = creditState.accounts,
            movementsFor = { acc ->
                creditState.movements.takeIf { creditState.selected?.id == acc.id && !creditState.isDetailLoading }
            },
            onSelectAccount = { acc -> creditViewModel.loadAccount(companyId, acc.id) },
            isBusy = state.isCreditBusy,
            onDismiss = { creditCollectOpen = false },
            onConfirm = { acc, amount, method, tendered, bills ->
                user?.let { viewModel.collectCreditPayment(companyId, it, acc, amount, method, tendered, bills) }
            }
        )
    }
    if (advanceOpen && creditState != null) {
        EmployeeAdvanceDialog(
            accounts = creditState.accounts,
            cashAvailable = state.session?.expectedCashNow ?: 0.0,
            isBusy = state.isCreditBusy,
            onDismiss = { advanceOpen = false },
            onConfirm = { acc, amount, note ->
                user?.let { viewModel.giveEmployeeAdvance(companyId, it, acc, amount, note) }
            }
        )
    }

    if (lastCheckoutOpen) {
        LastCheckoutDialog(
            checkouts = state.recentCheckouts,
            isLoading = state.isLoadingCheckouts,
            onDismiss = { lastCheckoutOpen = false }
        )
    }

    if (depositReceiveOpen) {
        ReceiveDepositDialog(
            isSaving = state.isDepositBusy,
            onDismiss = { depositReceiveOpen = false },
            onConfirm = { input, print -> user?.let { viewModel.receiveDeposit(companyId, it, input, print) } },
            orders = state.depositOrders,
            products = state.products,
            branches = state.pickupBranches,
            currentBranchId = state.branch?.id.orEmpty()
        )
    }

    if (depositsListOpen) {
        DepositsListDialog(
            deposits = state.openDeposits,
            isBusy = state.isDepositBusy,
            onDismiss = { depositsListOpen = false },
            onReprint = { viewModel.reprintDeposit(it) },
            onRefund = { d, reason -> user?.let { viewModel.refundDeposit(companyId, it, d, reason) } }
        )
    }

    if (showIncome) {
        state.session?.let { sess ->
            user?.let { u ->
                CashIncomeDialog(
                    companyId = companyId,
                    session = sess,
                    currentUser = u,
                    onDismiss = { showIncome = false },
                    onSaved = {
                        showIncome = false
                        // Se recarga el POS porque el efectivo esperado de la sesión cambió,
                        // y de ese número cuelga el tope de lo que se puede pagar de la caja.
                        viewModel.load(companyId, sessionId)
                    },
                    viewModel = cashSessionViewModel
                )
            }
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

/** Cantidades de un toque para el grupo de precio: lo que más se pide en el mostrador. */
private val GROUP_QUICK_QUANTITIES = listOf(1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25)

/**
 * Cobro de un grupo de precio ("Pan Q0.50"): la cajera toca cuántos panes y no cuáles. Cada
 * toque SUMA a la línea del carrito, así "10 + 5" se arma con dos toques; el ajuste fino va
 * con los botones +/- del carrito.
 */
@Composable
private fun GroupSellPanel(
    group: POSCategory,
    /** Precio por pan: el público, o el del revendedor. Null = el revendedor no tiene uno. */
    unitPrice: Double?,
    inCart: Double,
    onAdd: (Double) -> Unit,
    onOtherQuantity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = runCatching { Color(("FF" + group.color.removePrefix("#")).toLong(16)) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = color),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(group.groupDisplayName, color = Color.White, fontWeight = FontWeight.ExtraBold,
                         style = MaterialTheme.typography.titleLarge)
                    Text((unitPrice?.let { "Q ${it.fmtMoney(2)} c/u" } ?: "Sin precio para este revendedor") +
                            " · ${group.productIds.size} tipos de pan",
                         color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                if (inCart > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("En el carrito", color = Color.White,
                             style = MaterialTheme.typography.labelSmall)
                        Text("${inCart.fmtMoney(0)} · Q ${(inCart * (unitPrice ?: 0.0)).fmtMoney(2)}",
                             color = Color.White, fontWeight = FontWeight.Bold,
                             style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Text("¿Cuántos panes?", style = MaterialTheme.typography.titleSmall,
             fontWeight = FontWeight.SemiBold)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(GROUP_QUICK_QUANTITIES, key = { it }) { qty ->
                Surface(
                    onClick = { onAdd(qty.toDouble()) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(2.dp, color),
                    tonalElevation = 2.dp,
                    modifier = Modifier.height(84.dp).cartFlightSource("grpq_$qty")
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("$qty", fontWeight = FontWeight.ExtraBold,
                             style = MaterialTheme.typography.headlineMedium, color = color)
                        Text(unitPrice?.let { "Q ${(qty * it).fmtMoney(2)}" } ?: "—",
                             style = MaterialTheme.typography.labelMedium,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "other") {
                Surface(
                    onClick = onOtherQuantity,
                    shape = RoundedCornerShape(12.dp),
                    color = color,
                    modifier = Modifier.height(84.dp).cartFlightSource("grpq_other")
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Otra cantidad", color = Color.White, fontWeight = FontWeight.Bold,
                             textAlign = TextAlign.Center, modifier = Modifier.padding(6.dp))
                    }
                }
            }
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
    groups: List<POSCategory> = emptyList(),
    groupQtyInCart: Map<String, Double> = emptyMap(),
    popularity: Map<String, Double> = emptyMap(),
    onGroupClick: (POSCategory) -> Unit = {},
    /** Precio que muestra cada ficha: el público o el del revendedor. Null = sin precio. */
    priceOf: (Product) -> Double? = { it.salePrice },
    groupPriceOf: (POSCategory) -> Double? = { it.groupUnitPrice },
    modifier: Modifier = Modifier
) {
    if (products.isEmpty() && groups.isEmpty()) {
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
        // Cada grupo entra en el orden por lo que se vende: su ficha va delante del primer
        // producto que vende menos que él. `products` ya viene ordenado.
        val sortedGroups = groups.sortedByDescending { popularity[groupSaleKey(it.id)] ?: 0.0 }
        val tiles = buildList<Any> {
            var gi = 0
            products.forEach { p ->
                val pRank = popularity[p.id] ?: 0.0
                while (gi < sortedGroups.size &&
                    (popularity[groupSaleKey(sortedGroups[gi].id)] ?: 0.0) >= pRank) {
                    add(sortedGroups[gi++])
                }
                add(p)
            }
            while (gi < sortedGroups.size) add(sortedGroups[gi++])
        }
        items(tiles, key = { t -> if (t is Product) t.id else "grp_" + (t as POSCategory).id }) { t ->
            if (t is POSCategory) {
                GroupTile(
                    group = t,
                    unitPrice = groupPriceOf(t),
                    quantityInCart = groupQtyInCart[t.id] ?: 0.0,
                    onClick = { onGroupClick(t) }
                )
            } else {
                val p = t as Product
                val line = cartByProductId[p.id]
                ProductTile(
                    product = p,
                    unitPrice = priceOf(p),
                    quantityInCart = line?.quantity ?: 0.0,
                    onClick = { onProductClick(p) }
                )
            }
        }
    }
}

/** Ficha de un grupo de precio en "Todos": mismo tamaño que un producto, en su color. */
@Composable
private fun GroupTile(
    group: POSCategory,
    unitPrice: Double?,
    quantityInCart: Double,
    onClick: () -> Unit
) {
    val color = runCatching { Color(("FF" + group.color.removePrefix("#")).toLong(16)) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    val selected = quantityInCart > 0
    Card(
        modifier = Modifier
            .cartFlightSource("grp_${group.id}")
            .fillMaxWidth()
            .height(148.dp)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 6.dp else 2.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    unitPrice?.let { "Q ${it.fmtMoney(2)}" } ?: "Sin precio",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    group.groupDisplayName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Surface(
                    color = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            quantityInCart.fmtMoney(0),
                            color = color,
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
private fun ProductTile(
    product: Product,
    unitPrice: Double?,
    quantityInCart: Double,
    onClick: () -> Unit
) {
    val selected = quantityInCart > 0
    val primary = MaterialTheme.colorScheme.primary
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .cartFlightSource(product.id)
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
                        unitPrice?.let { "Q ${it.fmtMoney(2)}" } ?: "Sin precio",
                        color = if (unitPrice != null) primary else MaterialTheme.colorScheme.error,
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
    /** Empaques que se cobran (la bolsa); vacío = la empresa no cobra empaque. */
    packagingItems: List<PackagingItem> = emptyList(),
    onAddPackaging: (PackagingItem) -> Unit = {},
    /** Renglón al que acaba de llegar el vuelo ("productId" o "grp_<id>") y cuándo. */
    highlightItem: String? = null,
    highlightAt: Long = 0L,
    modifier: Modifier = Modifier
) {
    // El grupo de precio no tiene producto contra el cual comparar stock.
    val oversoldCount = cart.count { it.hasProductStock && it.quantity > stockOf(it.productId) }
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
            val listState = rememberLazyListState()
            // Siempre a la vista lo último que se pidió: el renglón que se acaba de agregar o
            // de sumar, y si no se sabe cuál, el final de la lista.
            LaunchedEffect(highlightAt, cart.size) {
                val target = cart.indexOfFirst {
                    highlightItem != null && (it.productId == highlightItem || "grp_${it.quickGroupId}" == highlightItem)
                }.takeIf { it >= 0 && kotlin.time.Clock.System.now().toEpochMilliseconds() - highlightAt < 1500 }
                    ?: cart.lastIndex
                if (target >= 0) listState.animateScrollToItem(target)
            }
            LazyColumn(state = listState,
                       modifier = Modifier.weight(1f, fill = false).heightIn(max = 480.dp),
                       verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(cart, key = { it.key }) { line ->
                    CartLineRow(
                        modifier = Modifier.animateItem(),
                        line = line,
                        highlightAt = if (highlightItem != null &&
                            (line.productId == highlightItem || "grp_${line.quickGroupId}" == highlightItem)) highlightAt else 0L,
                        available = if (!line.hasProductStock) Double.MAX_VALUE else stockOf(line.productId),
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
        if (packagingItems.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            PackagingQuickBar(
                items = packagingItems,
                inCart = cart.packagingCounts(),
                onAdd = onAddPackaging,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
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


/** Cómo va la factura en el acuse del cobro. La venta ya está guardada pase lo que pase. */
@Composable
internal fun InvoiceStatusBanner(invoice: InvoiceProgress) {
    val status = invoice.status
    val (bg, ink) = com.bakery.erp.ui.theme.chipColors(
        when (status) {
            com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO -> com.bakery.erp.ui.theme.ChipTone.SUCCESS
            com.bakery.erp.model.FelInvoiceStatus.RECHAZADO -> com.bakery.erp.ui.theme.ChipTone.DANGER
            com.bakery.erp.model.FelInvoiceStatus.PENDIENTE -> com.bakery.erp.ui.theme.ChipTone.ATTENTION
            else -> com.bakery.erp.ui.theme.ChipTone.INFO
        },
        com.bakery.erp.ui.theme.ChipEmphasis.SOFT
    )
    Surface(color = bg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (com.bakery.erp.model.FelInvoiceStatus.isInFlight(status) && status != com.bakery.erp.model.FelInvoiceStatus.PENDIENTE) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ink)
                Spacer(Modifier.width(10.dp))
            }
            Column {
                val r = invoice.result
                Text(
                    when (status) {
                        com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO ->
                            "Factura ${r?.docType.orEmpty()} serie ${r?.serie.orEmpty()} · núm. ${r?.numero.orEmpty()}"
                        com.bakery.erp.model.FelInvoiceStatus.PENDIENTE -> "Factura pendiente"
                        com.bakery.erp.model.FelInvoiceStatus.RECHAZADO -> "La factura no se emitió"
                        else -> "Certificando factura…"
                    },
                    color = ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium
                )
                val detail = when (status) {
                    com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO -> r?.receptorName?.let { "A nombre de $it" }
                    else -> invoice.message.takeIf { it.isNotBlank() }
                }
                detail?.let { Text(it, color = ink, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun CartLineRow(
    line: CartLine,
    available: Double,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    highlightAt: Long = 0L,
    modifier: Modifier = Modifier
) {
    val oversold = line.quantity > available
    // Solo cuenta si llegó recién: abrir el carrito más tarde no debe iluminar nada.
    val recent = highlightAt > 0 && kotlin.time.Clock.System.now().toEpochMilliseconds() - highlightAt < 1500

    // Entrada: un renglón NUEVO entra deslizándose desde la derecha y creciendo. Se decide en
    // la primera composición — si el renglón ya existía, esto no corre y solo late.
    val enter = remember { Animatable(if (recent) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enter.value < 1f) {
            enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    // Latido: si el producto ya estaba y se le sumó cantidad, el renglón crece un instante.
    val pulse = remember { Animatable(1f) }
    // Destello naranja, marcado y con una pausa antes de apagarse, para que se note.
    val flash = remember { Animatable(0f) }
    LaunchedEffect(highlightAt) {
        if (!recent) return@LaunchedEffect
        flash.snapTo(1f)
        if (enter.value >= 1f) {
            pulse.snapTo(1.05f)
            pulse.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        }
        kotlinx.coroutines.delay(350)
        flash.animateTo(0f, tween(durationMillis = 1100))
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val slide = with(density) { 48.dp.toPx() }
    val base = MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = modifier.graphicsLayer {
            val e = enter.value
            alpha = e.coerceIn(0f, 1f)
            translationX = (1f - e) * slide
            val sc = (0.9f + 0.1f * e) * pulse.value
            scaleX = sc; scaleY = sc
        },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.lerp(base, CartFlashColor, flash.value)
        ),
        border = if (flash.value > 0.01f)
            androidx.compose.foundation.BorderStroke(2.dp, BrandColors.Accent.copy(alpha = flash.value)) else null
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
            // Una cuenta del restaurante se cobra entera: no tiene cantidad que subir o bajar.
            if (!line.isRestaurant) Spacer(Modifier.height(4.dp))
            if (!line.isRestaurant) Row(verticalAlignment = Alignment.CenterVertically) {
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

/** Cuántos de cada empaque van en el carrito, por id: el botón muestra "×2". */
private fun List<CartLine>.packagingCounts(): Map<String, Double> =
    filter { it.isPackaging }.associate { it.packagingItemId to it.quantity }

/** Naranja pálido del destello: más marcado que `AccentSoft` para que el renglón nuevo se note. */
private val CartFlashColor = Color(0xFFFFE0B2)
