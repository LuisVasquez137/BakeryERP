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
        ModuleItem("Pedidos",         "Enviar a producción",   Icons.AutoMirrored.Filled.ReceiptLong),
        ModuleItem("Producción",      "Lotes por turno",       Icons.Default.Factory),
        ModuleItem("Preparaciones",   "Adornos y rellenos",    Icons.Default.BakeryDining),
        ModuleItem("Lotes de Preparación", "Preparar lotes",   Icons.Default.Factory),
        ModuleItem("Ingreso a Turno", "Marcar asistencia",     Icons.Default.HowToReg),
    )),
    // Inventario = el azul de estructura.
    ModuleSection("Inventario", Icons.Default.Inventory, BrandColors.Primary, BrandColors.PrimaryContainer, listOf(
        ModuleItem("Insumos",    "Costos y stock",       Icons.Default.Kitchen),
        ModuleItem("Compras",    "Ingreso de insumos",   Icons.Default.ShoppingCart),
        ModuleItem("Despacho",   "Empaque y recepción",  Icons.Default.LocalShipping),
        ModuleItem("Reparto",    "Hojas de ruta",        Icons.Default.LocalShipping),
        ModuleItem("Inventario tienda", "Stock terminado y mermas", Icons.Default.Inventory),
        ModuleItem("Conteo de pan", "Cierre de día con merma",   Icons.Default.Inventory),
    )),
    // Ventas = el teal que la app ya usa en las categorias del POS.
    ModuleSection("Ventas", Icons.Default.PointOfSale, Color(0xFF0D7A8C), Color(0xFFD4EFF3), listOf(
        ModuleItem("Productos",       "Catálogo y precios",   Icons.Default.BakeryDining),
        ModuleItem("Listas de Precios", "Canales y mayoreo",  Icons.Default.Sell),
        ModuleItem("Pedidos de Clientes", "Revendedores y encargos", Icons.Default.ShoppingCart),
        ModuleItem("Aprobar Revendedores", "Validar nuevos registros", Icons.Default.HowToReg),
        ModuleItem("Punto de Venta",  "Caja y cobro rápido",  Icons.Default.PointOfSale),
        ModuleItem("Historial de Ventas", "Transacciones del día", Icons.Default.Receipt),
        ModuleItem("Cierre del día",      "Consolidado por sucursal", Icons.Default.PointOfSale),
        ModuleItem("Cuadre sorpresa",     "Auditoría a cajas abiertas", Icons.Default.PointOfSale),
    )),
    // Administracion = se toca una vez al mes; que se apague es correcto.
    ModuleSection("Administración", Icons.Default.Settings, BrandColors.MutedSlate, BrandColors.Sand, listOf(
        ModuleItem("Configuración",        "Código de invitación y entregas", Icons.Default.Settings),
        ModuleItem("Proveedores",          "Gestión externa",      Icons.Default.Business),
        ModuleItem("Gastos",               "Control por sucursal", Icons.Default.Receipt),
        ModuleItem("Sucursales",           "Gestión de locales",   Icons.Default.Store),
        ModuleItem("Usuarios",             "Accesos y permisos",   Icons.Default.People),
        ModuleItem("Colaboradores",        "Equipo de trabajo",    Icons.Default.Badge),
        ModuleItem("Descriptores de Puestos", "Perfiles y funciones", Icons.Default.WorkOutline),
        ModuleItem("Turnos",               "Configurar jornadas",  Icons.Default.Schedule),
        ModuleItem("Activos Fijos",        "Patrimonio y mantenimiento", Icons.Default.Construction),
        ModuleItem("Cajas Registradoras",  "Configurar cajas POS",       Icons.Default.PointOfSale),
        ModuleItem("Categorías POS",       "Agrupar productos para venta", Icons.Default.Sell),
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
        "Gastos"         -> hasPermission(Permission.VIEW_EXPENSES) || hasPermission(Permission.ADD_VARIABLE_EXPENSE) || hasPermission(Permission.MANAGE_FIXED_BUDGET)
        "Usuarios"       -> hasPermission(Permission.MANAGE_USERS)
        "Pedidos"        -> hasPermission(Permission.SEND_PRODUCTION_ORDER) || hasPermission(Permission.VIEW_PRODUCTION)
        "Producción"     -> hasPermission(Permission.MANAGE_PRODUCTION_LOTS) || hasPermission(Permission.VIEW_PRODUCTION)
        "Turnos"         -> hasPermission(Permission.MANAGE_PRODUCTION_SHIFTS)
        "Preparaciones"        -> hasPermission(Permission.VIEW_PREPARATIONS)
        "Lotes de Preparación" -> hasPermission(Permission.MANAGE_PREPARATION_BATCHES)
        "Colaboradores"           -> hasPermission(Permission.MANAGE_EMPLOYEES)
        "Ingreso a Turno"         -> hasPermission(Permission.MANAGE_EMPLOYEES)
        "Descriptores de Puestos" -> hasPermission(Permission.MANAGE_EMPLOYEES)
        "Activos Fijos"  -> hasPermission(Permission.VIEW_ASSETS) ||
                            hasPermission(Permission.EDIT_ASSETS) ||
                            hasPermission(Permission.MANAGE_ASSETS)
        "Listas de Precios" -> hasPermission(Permission.MANAGE_PRICE_LISTS)
        "Despacho"          -> hasPermission(Permission.VIEW_PRODUCTION) ||
                               hasPermission(Permission.DISPATCH_PACK) ||
                               hasPermission(Permission.RECEIVE_DISPATCH)
        "Reparto"           -> hasPermission(Permission.MANAGE_DELIVERY_RUNS) ||
                               hasPermission(Permission.DRIVE_DELIVERY)
        "Punto de Venta"    -> hasPermission(Permission.OPERATE_POS)
        "Historial de Ventas" -> hasPermission(Permission.VIEW_SALES)
        "Cajas Registradoras" -> hasPermission(Permission.MANAGE_POS_CONFIG)
        "Categorías POS"    -> hasPermission(Permission.MANAGE_POS_CONFIG)
        "Cierre del día"    -> hasPermission(Permission.MANAGE_DAILY_CASH_CLOSE)
        "Cuadre sorpresa"   -> hasPermission(Permission.SPOT_CHECK_CASH)
        "Conteo de pan"     -> hasPermission(Permission.COUNT_FINISHED_STOCK) ||
                               hasPermission(Permission.VIEW_BREAD_COUNTS)
        "Pedidos de Clientes" -> hasPermission(Permission.VIEW_CUSTOMER_ORDERS) ||
                                 hasPermission(Permission.CREATE_RESELLER_ORDER) ||
                                 hasPermission(Permission.CREATE_RETAIL_SPECIAL_ORDER) ||
                                 hasPermission(Permission.CREATE_CAKE_ORDER) ||
                                 hasPermission(Permission.MANAGE_CUSTOMER_ORDERS)
        "Configuración"       -> hasPermission(Permission.MANAGE_USERS)
        "Aprobar Revendedores" -> hasPermission(Permission.MANAGE_EXTERNAL_CUSTOMERS)
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
    onNavigateToBreadCount: () -> Unit = {},
    onNavigateToDailyCashClose: () -> Unit = {},
    onNavigateToSpotCashCheck: () -> Unit = {},
    onNavigateToSalesReport: () -> Unit = {},
    onNavigateToProductionReport: () -> Unit = {},
    onNavigateToFinancialReport: () -> Unit = {},
    onNavigateToCustomerOrders: () -> Unit = {},
    onNavigateToOrganizationSettings: () -> Unit = {},
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
            "Usuarios"       -> onNavigateToUsers()
            "Pedidos"        -> onNavigateToProductionOrders()
            "Producción"     -> onNavigateToProductionLots()
            "Turnos"          -> onNavigateToProductionShifts()
            "Preparaciones"        -> onNavigateToPreparations()
            "Lotes de Preparación" -> onNavigateToPreparationBatches()
            "Colaboradores"           -> onNavigateToEmployees()
            "Ingreso a Turno"         -> onNavigateToShiftClockIn()
            "Descriptores de Puestos" -> onNavigateToJobPositions()
            "Activos Fijos"           -> onNavigateToAssets()
            "Listas de Precios"       -> onNavigateToPriceLists()
            "Despacho"                -> onNavigateToDispatch()
            "Reparto"                 -> onNavigateToDelivery()
            "Inventario tienda"       -> onNavigateToFinishedStock()
            "Punto de Venta"          -> onNavigateToPOS()
            "Historial de Ventas"     -> onNavigateToSalesHistory()
            "Cajas Registradoras"     -> onNavigateToCashRegisterAdmin()
            "Categorías POS"          -> onNavigateToQuickGroupAdmin()
            "Conteo de pan"           -> onNavigateToBreadCount()
            "Cierre del día"          -> onNavigateToDailyCashClose()
            "Cuadre sorpresa"         -> onNavigateToSpotCashCheck()
            "Pedidos de Clientes"     -> onNavigateToCustomerOrders()
            "Configuración"           -> onNavigateToOrganizationSettings()
            "Aprobar Revendedores"    -> onNavigateToResellerApproval()
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
        AlertTarget.UPCOMING_MAINTENANCE   -> "Activos Fijos"
        AlertTarget.IN_TRANSIT_DISPATCH    -> "Despacho"
        AlertTarget.STALE_PRODUCTION_LOT   -> "Producción"
        AlertTarget.LATE_PRODUCTION_ORDER  -> "Pedidos"
        AlertTarget.OVERDUE_CASH_SESSION   -> "Cierre del día"
        AlertTarget.DISPATCH_PRICE_MISSING -> "Listas de Precios"
        AlertTarget.OVERDUE_PURCHASE       -> "Compras"
        AlertTarget.EXPIRING_INGREDIENT    -> "Insumos"
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
