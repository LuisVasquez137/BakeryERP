package com.bakery.erp.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import bakeryerp.composeapp.generated.resources.Res
import bakeryerp.composeapp.generated.resources.bakeos_logo_light
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.domain.report.AlertTarget
import com.bakery.erp.domain.report.DailySalesPoint
import com.bakery.erp.domain.report.HomeAlert
import com.bakery.erp.model.Permission
import com.bakery.erp.model.User
import com.bakery.erp.model.UserRole
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.notifications.NotificationViewModel
import com.bakery.erp.presentation.notifications.NotificationsSheet
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.ui.shareText
import com.bakery.erp.util.fmtMoney
import androidx.compose.runtime.remember
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.TextSlate as SlateText
import com.bakery.erp.ui.theme.BrandColors

private val CoffeeBrown = Color(0xFF2E4A6E)
private val MutedText   = Color(0xFF607585)

data class ModuleItem(val title: String, val subtitle: String, val icon: ImageVector)

/**
 * El color vive en la SECCION, no en el modulo: son 26 fichas y leerlas una por una para
 * encontrar "Compras" no deja formar memoria muscular. Cuatro bloques distinguibles si.
 *
 * Cuatro tonos y no uno por modulo (estilo Odoo): recetas -> produccion -> despacho -> reparto
 * son una sola cadena, y pintarlos de colores distintos afirmaria que no tienen relacion.
 * Ademas el naranja significa "necesita tus ojos" en toda la app; si cada modulo tuviera su
 * hue decorativo, esa senal se pierde. Por eso el color por seccion vive SOLO en este
 * lanzador — dentro de las pantallas el icono sigue diciendo estado.
 */
private data class ModuleSection(
    val title: String,
    val icon: ImageVector,
    /** Tinte del icono (de la ficha y del encabezado). Nunca color de texto. */
    val iconColor: Color,
    /**
     * Fondo del cuadrito del icono, en pálido. Se probó rellenarlo de color sólido — daba
     * más contraste y el #FF9800 exacto — pero el lanzador entero ganaba demasiado peso
     * visual y perdía sobriedad, que es el registro que se quiere para un ERP.
     */
    val container: Color,
    val modules: List<ModuleItem>
)

private val ALL_SECTIONS = listOf(
    // Operaciones = donde pasa el dia -> naranja.
    ModuleSection("Operaciones", Icons.Default.Factory, BrandColors.AccentIcon, BrandColors.AccentSoft, listOf(
        ModuleItem("Recetas",         "Fórmulas y procesos",   Icons.AutoMirrored.Filled.MenuBook),
        ModuleItem("Pedidos",         "Enviar a producción",   Icons.AutoMirrored.Filled.Assignment),
        ModuleItem("Producción",      "Lotes por turno",       Icons.Default.Factory),
        ModuleItem("Preparaciones",   "Adornos y rellenos",    Icons.Default.Blender),
        ModuleItem("Lotes de preparación", "Preparar lotes",   Icons.Default.SoupKitchen),
        ModuleItem("Anotar material", "Harina de mesa, manteca, limpieza", Icons.Default.Scale),
        ModuleItem("Asistencia", "Marcar entrada y salida",     Icons.Default.Fingerprint),
        ModuleItem("Anotar gasto",    "Compras y gastos del día", Icons.Default.Payments),
    )),
    // Inventario = el azul de estructura.
    ModuleSection("Inventario", Icons.Default.Inventory, BrandColors.Primary, BrandColors.PrimaryContainer, listOf(
        ModuleItem("Insumos",    "Costos y stock",       Icons.Default.Kitchen),
        ModuleItem("Compras",    "Facturas de proveedor",     Icons.Default.ShoppingCart),
        ModuleItem("Despacho",   "Empaque de la planta", Icons.Default.AllInbox),
        ModuleItem("Reparto",    "Cómo va el pan del día", Icons.Default.LocalShipping),
        ModuleItem("Inventario tienda", "Stock terminado y mermas", Icons.Default.Inventory),
        ModuleItem("Conteo de pan", "Cierre de día con merma",   Icons.Default.Checklist),
    )),
    // Ventas = el teal que la app ya usa en las categorias del POS.
    ModuleSection("Ventas", Icons.Default.PointOfSale, Color(0xFF0D7A8C), Color(0xFFD4EFF3), listOf(
        ModuleItem("Productos",       "Catálogo y precios",   Icons.Default.BakeryDining),
        ModuleItem("Listas de precios", "Canales y mayoreo",  Icons.Default.Sell),
        ModuleItem("Pedidos de clientes", "Revendedores y encargos", Icons.Default.ShoppingBag),
        ModuleItem("Revendedores", "Validar nuevos registros", Icons.Default.Handshake),
        ModuleItem("Punto de venta",  "Caja y cobro rápido",  Icons.Default.PointOfSale),
        ModuleItem("Historial de ventas", "Transacciones del día", Icons.Default.Receipt),
        ModuleItem("Cierre del día",      "Consolidado por sucursal", Icons.Default.LockClock),
        ModuleItem("Cuadre sorpresa",     "Auditoría a cajas abiertas", Icons.Default.Policy),
        ModuleItem("Créditos",            "Cuentas por cobrar y adelantos", Icons.Default.CreditScore),
    )),
    // Administracion = se toca una vez al mes; que se apague es correcto.
    ModuleSection("Administración", Icons.Default.Settings, BrandColors.MutedSlate, BrandColors.Sand, listOf(
        ModuleItem("Configuración",        "Código de invitación y entregas", Icons.Default.Settings),
        ModuleItem("Facturación",          "NIT y sucursales que facturan", Icons.AutoMirrored.Filled.ReceiptLong),
        ModuleItem("Bitácora de cajas",    "Aperturas, cierres y fuera de horario", Icons.Default.History),
        ModuleItem("Proveedores",          "Gestión externa",      Icons.Default.Business),
        ModuleItem("Gastos",               "Lo gastado del mes",   Icons.Default.AccountBalanceWallet),
        ModuleItem("Costos fijos",         "Renta, luz, internet", Icons.Default.EventRepeat),
        ModuleItem("Sucursales",           "Gestión de locales",   Icons.Default.Store),
        ModuleItem("Usuarios",             "Accesos y permisos",   Icons.Default.ManageAccounts),
        ModuleItem("Colaboradores",        "Equipo de trabajo",    Icons.Default.Badge),
        ModuleItem("Puestos", "Perfiles y funciones", Icons.Default.WorkOutline),
        ModuleItem("Turnos",               "Configurar jornadas",  Icons.Default.Schedule),
        ModuleItem("Activos fijos",        "Patrimonio y mantenimiento", Icons.Default.Construction),
        ModuleItem("Cajas",  "Configurar cajas POS",       Icons.Default.Calculate),
        ModuleItem("Categorías POS",       "Agrupar productos para venta", Icons.Default.Category),
    ))
)

private fun hasModuleAccess(title: String, user: User) = with(user) {
    when (title) {
        "Productos"      -> hasPermission(Permission.VIEW_PRODUCTS)
        "Insumos"        -> hasPermission(Permission.VIEW_INGREDIENTS)
        "Proveedores"    -> hasPermission(Permission.VIEW_SUPPLIERS)
        "Recetas"        -> hasPermission(Permission.VIEW_RECIPES)
        "Compras"        -> hasPermission(Permission.RECEIVE_STOCK)
        "Inventario tienda" -> hasPermission(Permission.VIEW_PRODUCTS)
        "Sucursales"     -> hasPermission(Permission.VIEW_ALL_BRANCHES) || hasPermission(Permission.MANAGE_BRANCHES)
        "Gastos"         -> hasPermission(Permission.VIEW_EXPENSES) || hasPermission(Permission.ADD_VARIABLE_EXPENSE)
        // Los costos fijos los revisa el dueño, no quien anota el gasto del día: son el
        // compromiso mensual del negocio y no cambian de una semana a otra.
        "Costos fijos"   -> hasPermission(Permission.MANAGE_FIXED_BUDGET)
        // El acceso directo anota de CAJA GENERAL: no le descuenta a ninguna gaveta. Por eso
        // pide su propio permiso y no el de anotar desde la caja propia — quien cobra tiene
        // que anotar lo suyo desde el Punto de Venta, que es donde el faltante queda
        // explicado al cuadrar.
        "Anotar gasto"   -> hasPermission(Permission.ADD_GENERAL_EXPENSE)
        "Usuarios"       -> hasPermission(Permission.MANAGE_USERS)
        "Pedidos"        -> hasPermission(Permission.SEND_PRODUCTION_ORDER) || hasPermission(Permission.VIEW_PRODUCTION)
        "Producción"     -> hasPermission(Permission.MANAGE_PRODUCTION_LOTS) || hasPermission(Permission.VIEW_PRODUCTION)
        "Turnos"         -> hasPermission(Permission.MANAGE_PRODUCTION_SHIFTS)
        "Preparaciones"        -> hasPermission(Permission.VIEW_PREPARATIONS)
        "Lotes de preparación" -> hasPermission(Permission.MANAGE_PREPARATION_BATCHES)
        // Se ve tanto para el panadero que saca el saco como para el dueño que solo
        // quiere el reporte de cada cuánto lo sacan: son las dos caras del módulo.
        "Anotar material"      -> hasPermission(Permission.ISSUE_PLANT_MATERIAL) ||
                                  hasPermission(Permission.VIEW_INVENTORY_REPORT)
        "Colaboradores"           -> hasPermission(Permission.MANAGE_EMPLOYEES)
        "Asistencia"         -> hasPermission(Permission.MANAGE_EMPLOYEES)
        "Puestos" -> hasPermission(Permission.MANAGE_EMPLOYEES)
        "Activos fijos"  -> hasPermission(Permission.VIEW_ASSETS) ||
                            hasPermission(Permission.EDIT_ASSETS) ||
                            hasPermission(Permission.MANAGE_ASSETS)
        "Listas de precios" -> hasPermission(Permission.MANAGE_PRICE_LISTS)
        // Despacho es solo de la planta: empacar. Lo que antes eran sus pestañas
        // (listas, en tránsito, por contar, recibidas) es ahora el tablero de Reparto,
        // que es donde la sucursal firma la llegada y cuenta.
        "Despacho"          -> hasPermission(Permission.DISPATCH_PACK)
        "Reparto"           -> hasPermission(Permission.MANAGE_DELIVERY_RUNS) ||
                               hasPermission(Permission.DRIVE_DELIVERY) ||
                               hasPermission(Permission.RECEIVE_DISPATCH) ||
                               hasPermission(Permission.VIEW_PRODUCTION)
        "Punto de venta"    -> hasPermission(Permission.OPERATE_POS)
        "Historial de ventas" -> hasPermission(Permission.VIEW_SALES)
        "Cajas" -> hasPermission(Permission.MANAGE_POS_CONFIG)
        "Categorías POS"    -> hasPermission(Permission.MANAGE_POS_CONFIG)
        "Cierre del día"    -> hasPermission(Permission.MANAGE_DAILY_CASH_CLOSE)
        "Cuadre sorpresa"   -> hasPermission(Permission.SPOT_CHECK_CASH)
        "Conteo de pan"     -> hasPermission(Permission.COUNT_FINISHED_STOCK) ||
                               hasPermission(Permission.VIEW_BREAD_COUNTS)
        "Pedidos de clientes" -> hasPermission(Permission.VIEW_CUSTOMER_ORDERS) ||
                                 hasPermission(Permission.CREATE_RESELLER_ORDER) ||
                                 hasPermission(Permission.CREATE_RETAIL_SPECIAL_ORDER) ||
                                 hasPermission(Permission.CREATE_CAKE_ORDER) ||
                                 hasPermission(Permission.MANAGE_CUSTOMER_ORDERS)
        "Configuración"       -> hasPermission(Permission.MANAGE_USERS)
        "Facturación"         -> role == UserRole.OWNER
        "Bitácora de cajas"   -> hasPermission(Permission.VIEW_CASH_AUDIT)
        "Revendedores" -> hasPermission(Permission.MANAGE_EXTERNAL_CUSTOMERS)
        "Créditos"          -> hasPermission(Permission.MANAGE_CREDIT_ACCOUNTS)
        else                      -> true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToProducts: () -> Unit,
    onNavigateToIngredients: () -> Unit,
    onNavigateToSuppliers: () -> Unit,
    onNavigateToRecipes: () -> Unit,
    onNavigateToBranches: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToFixedBudget: () -> Unit,
    onNavigateToQuickPurchase: () -> Unit,
    onNavigateToPlantIssues: () -> Unit,
    onNavigateToUsers: () -> Unit,
    onNavigateToPurchases: () -> Unit,
    onNavigateToProductionOrders: () -> Unit = {},
    onNavigateToProductionLots: () -> Unit = {},
    onNavigateToProductionShifts: () -> Unit = {},
    onNavigateToEmployees: () -> Unit = {},
    onNavigateToShiftClockIn: () -> Unit = {},
    onNavigateToJobPositions: () -> Unit = {},
    onNavigateToPreparations: () -> Unit = {},
    onNavigateToPreparationBatches: () -> Unit = {},
    onNavigateToAssets: () -> Unit = {},
    onNavigateToPriceLists: () -> Unit = {},
    onNavigateToDispatch: () -> Unit = {},
    onNavigateToDelivery: () -> Unit = {},
    onNavigateToFinishedStock: () -> Unit = {},
    onNavigateToPOS: () -> Unit = {},
    onNavigateToSalesHistory: () -> Unit = {},
    onNavigateToCashRegisterAdmin: () -> Unit = {},
    onNavigateToQuickGroupAdmin: () -> Unit = {},
    onNavigateToCredit: () -> Unit = {},
    onNavigateToBreadCount: () -> Unit = {},
    onNavigateToDailyCashClose: () -> Unit = {},
    onNavigateToSpotCashCheck: () -> Unit = {},
    onNavigateToSalesReport: () -> Unit = {},
    onNavigateToProductionReport: () -> Unit = {},
    onNavigateToFinancialReport: () -> Unit = {},
    onNavigateToBranchSupplyReport: () -> Unit = {},
    onNavigateToProductCostReport: () -> Unit = {},
    onNavigateToStandardCostReport: () -> Unit = {},
    onNavigateToCustomerOrders: () -> Unit = {},
    onNavigateToOrganizationSettings: () -> Unit = {},
    onNavigateToFelSetup: () -> Unit = {},
    onNavigateToCashAudit: () -> Unit = {},
    onNavigateToResellerApproval: () -> Unit = {},
    onNavigateToNotificationTarget: (targetScreen: String, targetId: String) -> Unit = { _, _ -> },
    viewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    notificationViewModel: NotificationViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val homeState by homeViewModel.uiState.collectAsState()
    val notificationState by notificationViewModel.uiState.collectAsState()
    val user = state.user
    var menuExpanded by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showInviteCodeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(user?.id) {
        user?.let { homeViewModel.ensureLoadedFor(it) }
    }
    // La campana se suscribe desde Home porque es la pantalla que siempre está
    // en el backstack: así el badge sigue vivo aunque el usuario ande en otro
    // módulo y vuelva.
    LaunchedEffect(user?.id, user?.orgId) {
        val u = user ?: return@LaunchedEffect
        notificationViewModel.observe(u.orgId, u)
        notificationViewModel.registerCurrentDevice(u.orgId, u.id)
    }

    if (showInviteCodeDialog) {
        InviteCodeDialog(
            code = state.inviteCode,
            onDismiss = { showInviteCodeDialog = false }
        )
    }

    if (showNotifications && user != null) {
        NotificationsSheet(
            notifications = notificationState.notifications,
            currentUserId = user.id,
            onDismiss = { showNotifications = false },
            onMarkAllRead = { notificationViewModel.markAllAsRead(user.orgId, user.id) },
            onOpen = { notification ->
                notificationViewModel.markAsRead(user.orgId, notification.id, user.id)
                showNotifications = false
                onNavigateToNotificationTarget(notification.targetScreen, notification.targetId)
            }
        )
    }

    fun navigate(title: String) {
        when (title) {
            "Productos"      -> onNavigateToProducts()
            "Insumos"        -> onNavigateToIngredients()
            "Proveedores"    -> onNavigateToSuppliers()
            "Recetas"        -> onNavigateToRecipes()
            "Compras"        -> onNavigateToPurchases()
            "Sucursales"     -> onNavigateToBranches()
            "Gastos"         -> onNavigateToExpenses()
            "Costos fijos"   -> onNavigateToFixedBudget()
            "Anotar gasto"   -> onNavigateToQuickPurchase()
            "Usuarios"       -> onNavigateToUsers()
            "Pedidos"        -> onNavigateToProductionOrders()
            "Producción"     -> onNavigateToProductionLots()
            "Turnos"          -> onNavigateToProductionShifts()
            "Preparaciones"        -> onNavigateToPreparations()
            "Lotes de preparación" -> onNavigateToPreparationBatches()
            "Anotar material"      -> onNavigateToPlantIssues()
            "Colaboradores"           -> onNavigateToEmployees()
            "Asistencia"         -> onNavigateToShiftClockIn()
            "Puestos" -> onNavigateToJobPositions()
            "Activos fijos"           -> onNavigateToAssets()
            "Listas de precios"       -> onNavigateToPriceLists()
            "Despacho"                -> onNavigateToDispatch()
            "Reparto"                 -> onNavigateToDelivery()
            "Inventario tienda"       -> onNavigateToFinishedStock()
            "Punto de venta"          -> onNavigateToPOS()
            "Historial de ventas"     -> onNavigateToSalesHistory()
            "Cajas"     -> onNavigateToCashRegisterAdmin()
            "Categorías POS"          -> onNavigateToQuickGroupAdmin()
            "Conteo de pan"           -> onNavigateToBreadCount()
            "Cierre del día"          -> onNavigateToDailyCashClose()
            "Cuadre sorpresa"         -> onNavigateToSpotCashCheck()
            "Pedidos de clientes"     -> onNavigateToCustomerOrders()
            "Configuración"           -> onNavigateToOrganizationSettings()
            "Facturación"             -> onNavigateToFelSetup()
            "Bitácora de cajas"       -> onNavigateToCashAudit()
            "Revendedores"    -> onNavigateToResellerApproval()
            "Créditos"                -> onNavigateToCredit()
        }
    }

    val visibleSections = remember(user) {
        if (user == null) emptyList()
        else ALL_SECTIONS.mapNotNull { section ->
            val visible = section.modules.filter { hasModuleAccess(it.title, user) }
            if (visible.isNotEmpty()) section.copy(modules = visible) else null
        }
    }

    Scaffold(
        containerColor = BrandColors.Ivory,
        topBar = {
            TopAppBar(
                title = {
                    // Variante invertida del wordmark: el azul va en blanco para resaltar sobre el
                    // header azul, pero el naranja del croissant se conserva. Un ColorFilter.tint
                    // sobre el logo normal teñía TODO de blanco y se perdía ese acento.
                    Image(
                        painter = painterResource(Res.drawable.bakeos_logo_light),
                        contentDescription = "BakeOS",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(32.dp)
                    )
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (notificationState.unreadCount > 0) {
                                Badge(containerColor = BrandColors.Accent, contentColor = BrandColors.OnAccent) {
                                    Text(
                                        if (notificationState.unreadCount > 9) "9+"
                                        else "${notificationState.unreadCount}",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    ) {
                        IconButton(onClick = { showNotifications = true }) {
                            Icon(
                                Icons.Default.Notifications,
                                "Avisos",
                                tint = if (notificationState.unreadCount > 0) Color.White
                                       else Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = Color.White)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Compartir código de la panadería") },
                            leadingIcon = { Icon(Icons.Default.Share, null, tint = PrimaryBlue) },
                            onClick = { menuExpanded = false; showInviteCodeDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Cerrar sesión") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = PrimaryBlue) },
                            onClick = { menuExpanded = false; viewModel.signOut() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)
        ) {
            item {
                GreetingHeader(user, state.orgName)
                Spacer(Modifier.height(16.dp))
                state.operator?.let { op ->
                    OperatorBar(
                        name = op.name,
                        device = state.accountUser?.displayName.orEmpty(),
                        onSwitch = viewModel::lockOperator
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (homeState.alerts.isNotEmpty()) {
                item {
                    AlertsBanner(
                        alerts = homeState.alerts,
                        onAlertClick = { alert -> navigateAlert(alert, ::navigate) }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            item {
                KpiRow(
                    salesToday = homeState.salesTodayTotal,
                    salesCount = homeState.salesTodayCount,
                    lotsToday = homeState.lotsTodayCount,
                    lowStockCount = homeState.lowStockCount,
                    showSalesValue = user?.let { it.hasPermission(Permission.VIEW_SALES_VALUE) } == true ||
                        user?.role == UserRole.OWNER
                )
                Spacer(Modifier.height(20.dp))
            }
            item {
                WeeklySalesChart(
                    points = homeState.weeklySales,
                    showValue = user?.let { it.hasPermission(Permission.VIEW_SALES_VALUE) } == true ||
                        user?.role == UserRole.OWNER,
                    onSeeDetail = onNavigateToSalesReport
                )
                Spacer(Modifier.height(20.dp))
            }
            user?.let { u ->
                val visibleReports = buildList {
                    if (u.hasPermission(Permission.VIEW_SALES_REPORT))
                        add(Triple("Ventas", Icons.AutoMirrored.Filled.TrendingUp, onNavigateToSalesReport))
                    if (u.hasPermission(Permission.VIEW_PRODUCTION_REPORT))
                        add(Triple("Producción", Icons.Default.Factory, onNavigateToProductionReport))
                    if (u.hasPermission(Permission.VIEW_FINANCIAL_REPORT))
                        add(Triple("Financiero", Icons.Default.PointOfSale, onNavigateToFinancialReport))
                    if (u.hasPermission(Permission.VIEW_FINANCIAL_REPORT))
                        add(Triple("Costo por producto", Icons.Default.Calculate, onNavigateToProductCostReport))
                    if (u.hasPermission(Permission.VIEW_FINANCIAL_REPORT))
                        add(Triple("Costo estándar", Icons.Default.Factory, onNavigateToStandardCostReport))
                    if (u.hasPermission(Permission.VIEW_BRANCH_SUPPLY_REPORT))
                        add(Triple("Pedido vs. recibido", Icons.Default.LocalShipping, onNavigateToBranchSupplyReport))
                }
                if (visibleReports.isNotEmpty()) {
                    item {
                        ReportsRow(visibleReports)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
            visibleSections.forEach { section ->
                item(key = "hdr_${section.title}") {
                    SectionHeader(section)
                    Spacer(Modifier.height(12.dp))
                }
                item(key = "grid_${section.title}") {
                    ResponsiveGrid(
                        modules = section.modules,
                        iconTint = section.iconColor,
                        iconBg = section.container,
                        onModuleClick = { navigate(it.title) }
                    )
                    Spacer(Modifier.height(28.dp))
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun GreetingHeader(user: User?, orgName: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "¡Hola, ${user?.displayName?.split(" ")?.firstOrNull() ?: "Usuario"}!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = PrimaryBlue
            )
            val roleLabel = when (user?.role ?: UserRole.EMPLOYEE) {
                UserRole.OWNER    -> "Propietario"
                UserRole.ADMIN    -> "Administrador"
                UserRole.MANAGER  -> "Encargado"
                UserRole.EMPLOYEE -> "Empleado"
                UserRole.RESELLER -> "Revendedor"
            }
            Text(
                text = if (!orgName.isNullOrBlank()) "$roleLabel · $orgName" else roleLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText
            )
        }
        if (!user?.photoUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .border(2.dp, CoffeeBrown, CircleShape)
            ) {
                ProductImage(
                    imageUrl = user?.photoUrl,
                    contentDescription = "Foto de perfil",
                    modifier = Modifier.size(50.dp)
                )
            }
        } else {
            Surface(
                shape = CircleShape,
                color = PrimaryBlue,
                border = BorderStroke(2.dp, CoffeeBrown),
                modifier = Modifier.size(50.dp)
            ) {
                Text(
                    text = (user?.displayName?.firstOrNull() ?: 'U').toString().uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.wrapContentSize(Alignment.Center)
                )
            }
        }
    }
}

/**
 * Equipo de sucursal: quién lo está usando y el botón para dejarlo a otra persona. Va
 * en el Home porque es adonde se vuelve al terminar algo; el bloqueo por inactividad
 * cubre a quien se va sin tocarlo.
 */
@Composable
private fun OperatorBar(name: String, device: String, onSwitch: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = com.bakery.erp.ui.theme.BrandColors.PrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Usando el equipo: $name",
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (device.isNotBlank()) {
                    Text(device, style = MaterialTheme.typography.bodySmall, color = MutedText, maxLines = 1)
                }
            }
            TextButton(onClick = onSwitch) { Text("Cambiar de persona", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun InviteCodeDialog(code: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir código de la panadería") },
        text = {
            Column {
                Text(
                    "Este código sirve para invitar a otros colaboradores a unirse a esta " +
                        "panadería. Después de que se registren con él, el administrador " +
                        "deberá otorgarles los permisos correspondientes — no quedan con " +
                        "acceso automático.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryBlue.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = code?.ifEmpty { "—" } ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !code.isNullOrEmpty(),
                onClick = {
                    shareText(
                        text = "Únete a mi panadería en BakeOS con este código: $code",
                        subject = "Código de invitación"
                    )
                }
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Compartir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun KpiRow(
    salesToday: Double,
    salesCount: Int,
    lotsToday: Int,
    lowStockCount: Int,
    showSalesValue: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KpiCard(
            label = "Ventas hoy",
            value = if (showSalesValue) "Q ${salesToday.fmtMoney()}" else "$salesCount tickets",
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            color = PrimaryBlue,
            modifier = Modifier.weight(1f)
        )
        KpiCard(
            label = "Producción",
            value = "$lotsToday ${if (lotsToday == 1) "lote" else "lotes"}",
            icon = Icons.Default.Factory,
            color = Color(0xFF0D7A8C),
            modifier = Modifier.weight(1f)
        )
        KpiCard(
            label = "Stock bajo",
            value = "$lowStockCount ${if (lowStockCount == 1) "alerta" else "alertas"}",
            icon = Icons.Default.Warning,
            color = CoffeeBrown,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun KpiCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = SlateText)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MutedText)
        }
    }
}

@Composable
private fun WeeklySalesChart(
    points: List<DailySalesPoint>,
    showValue: Boolean,
    onSeeDetail: () -> Unit
) {
    val dayLabels = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
    val maxVal = points.maxOfOrNull { it.revenue } ?: 0.0
    val totalWeek = points.sumOf { it.revenue }

    Card(
        modifier = Modifier.fillMaxWidth().height(210.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Ventas de la semana",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                        color = SlateText
                    )
                    if (showValue) {
                        Text(
                            text = "Total Q ${totalWeek.fmtMoney()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                    }
                }
                TextButton(onClick = onSeeDetail) {
                    Text("Ver detalle", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom
            ) {
                points.forEach { point ->
                    val fraction = if (maxVal > 0) (point.revenue / maxVal).toFloat().coerceAtLeast(0.04f)
                                   else 0.04f
                    // Día de la semana derivado del label yyyy-MM-dd: usamos su orden y modulamos.
                    // points viene ya ordenado por fecha en HomeViewModel.fillMissingDays.
                    val labelIdx = (point.dateMillis / (24L * 60 * 60 * 1000) % 7).toInt()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(26.dp)
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(PrimaryBlue.copy(alpha = 0.75f))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            dayLabels[(labelIdx + 4) % 7], // epoch day 0 = Jueves; ajuste para Dom..Sab
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsRow(
    items: List<Triple<String, ImageVector, () -> Unit>>
) {
    Column {
        Text(
            text = "Reportes",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
            color = SlateText
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            items.forEach { (label, icon, action) ->
                Card(
                    modifier = Modifier.weight(1f).clickable { action() },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.06f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.18f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(icon, null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                        Text(
                            label,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                            color = PrimaryBlue
                        )
                    }
                }
            }
        }
    }
}

private fun navigateAlert(alert: HomeAlert, navigate: (String) -> Unit) {
    val title = when (alert.target) {
        AlertTarget.LOW_STOCK_INGREDIENTS  -> "Insumos"
        AlertTarget.UPCOMING_MAINTENANCE   -> "Activos fijos"
        AlertTarget.IN_TRANSIT_DISPATCH    -> "Reparto"
        AlertTarget.STALE_PRODUCTION_LOT   -> "Producción"
        AlertTarget.LATE_PRODUCTION_ORDER  -> "Pedidos"
        AlertTarget.OVERDUE_CASH_SESSION   -> "Cierre del día"
        AlertTarget.DISPATCH_PRICE_MISSING -> "Listas de precios"
        AlertTarget.OVERDUE_PURCHASE       -> "Compras"
        AlertTarget.EXPIRING_INGREDIENT    -> "Insumos"
        AlertTarget.CREDIT_OVERDUE         -> "Créditos"
    }
    navigate(title)
}

@Composable
private fun SectionHeader(section: ModuleSection) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(section.container, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(section.icon, null, tint = section.iconColor, modifier = Modifier.size(17.dp))
        }


        Text(
            text = section.title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
            color = SlateText
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE0D6CC), thickness = 1.dp)
    }
}

@Composable
private fun ResponsiveGrid(
    modules: List<ModuleItem>,
    iconTint: Color,
    iconBg: Color,
    onModuleClick: (ModuleItem) -> Unit
) {
    BoxWithConstraints {
        val cols = when {
            maxWidth >= 720.dp -> 5
            maxWidth >= 560.dp -> 4
            maxWidth >= 400.dp -> 3
            else               -> 2
        }
        val rows = (modules.size + cols - 1) / cols
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(rows) { rowIdx ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(cols) { colIdx ->
                        val idx = rowIdx * cols + colIdx
                        if (idx < modules.size) {
                            ModuleCard(
                                module = modules[idx],
                                iconTint = iconTint,
                                iconBg = iconBg,
                                modifier = Modifier.weight(1f),
                                onClick = { onModuleClick(modules[idx]) }
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(
    module: ModuleItem,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color(0xFFEDE7E1))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconBg, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(module.icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Text(
                module.title,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                color = SlateText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                module.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
