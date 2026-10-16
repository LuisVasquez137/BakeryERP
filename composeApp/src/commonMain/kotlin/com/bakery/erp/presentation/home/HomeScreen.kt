package com.bakery.erp.presentation.home

import com.bakery.erp.ui.BrandTopAppBar

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.bakery.erp.domain.report.HomeAlert
import com.bakery.erp.model.Employee
import com.bakery.erp.model.dayGreeting
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.bakery.erp.model.Permission
import com.bakery.erp.model.User
import com.bakery.erp.model.UserRole
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.notifications.NotificationViewModel
import com.bakery.erp.presentation.notifications.NotificationsSheet
import com.bakery.erp.presentation.reports.canSeeAnyReport
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.ui.shareText
import com.bakery.erp.util.fmtMoney
import androidx.compose.runtime.remember
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.TextSlate as SlateText
import com.bakery.erp.ui.theme.BrandColors

private val CoffeeBrown = Color(0xFF2E4A6E)
private val MutedText   = Color(0xFF607585)

/**
 * [keywords]: palabras con las que alguien buscaría el módulo sin saber cómo se llama
 * ("fiado" → Créditos, "empleados" → Colaboradores). Sin tildes; la búsqueda las quita igual.
 */
data class ModuleItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val keywords: String = ""
)

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
        ModuleItem("Recetas",         "Fórmulas y procesos",   Icons.AutoMirrored.Filled.MenuBook,
            "formula masa ingredientes porcentaje harina pasos hornear elaborar"),
        ModuleItem("Pedidos",         "Enviar a producción",   Icons.AutoMirrored.Filled.Assignment,
            "orden de produccion pedir pan sucursal encargar planta"),
        ModuleItem("Producción",      "Lotes por turno",       Icons.Default.Factory,
            "lote hornear panadero amasar turno planta fabricar"),
        ModuleItem("Preparaciones",   "Adornos y rellenos",    Icons.Default.Blender,
            "relleno adorno crema betun glaseado esponja"),
        ModuleItem("Lotes de preparación", "Preparar lotes",   Icons.Default.SoupKitchen,
            "preparar relleno crema betun esponja"),
        ModuleItem("Anotar material", "Harina de mesa, manteca, limpieza", Icons.Default.Scale,
            "harina de mesa manteca limpieza vale salida material planta saco"),
        ModuleItem("Asistencia", "Marcar entrada y salida",     Icons.Default.Fingerprint,
            "marcar entrada salida reloj kiosko selfie llegada horario"),
        ModuleItem("Anotar gasto",    "Compras y gastos del día", Icons.Default.Payments,
            "gasto compra rapida pagar caja chica mercado recibo"),
    )),
    // Inventario = el azul de estructura.
    ModuleSection("Inventario", Icons.Default.Inventory, BrandColors.Primary, BrandColors.PrimaryContainer, listOf(
        ModuleItem("Insumos",    "Costos y stock",       Icons.Default.Kitchen,
            "ingredientes materia prima harina azucar huevo levadura stock kardex existencias bodega"),
        ModuleItem("Compras",    "Facturas de proveedor",     Icons.Default.ShoppingCart,
            "factura proveedor pagar cuentas por pagar abonos recibir mercaderia"),
        ModuleItem("Despacho",   "Empaque de la planta", Icons.Default.AllInbox,
            "empacar enviar pan sucursales canastas notas de despacho"),
        ModuleItem("Reparto",    "Cómo va el pan del día", Icons.Default.LocalShipping,
            "entregas piloto camion ruta viaje llegada recibir pan"),
        ModuleItem("Inventario tienda", "Stock terminado y mermas", Icons.Default.Inventory,
            "existencias pan terminado stock mostrador merma"),
        ModuleItem("Traslados", "Entre sucursales", Icons.Default.SyncAlt,
            "mover enviar pasar producto otra sucursal transferencia"),
        ModuleItem("Conteo de pan", "Cierre de día con merma",   Icons.Default.Checklist,
            "contar sobrante merma cierre inventario fisico"),
    )),
    // Ventas = el teal que la app ya usa en las categorias del POS.
    ModuleSection("Ventas", Icons.Default.PointOfSale, Color(0xFF0D7A8C), Color(0xFFD4EFF3), listOf(
        ModuleItem("Productos",       "Catálogo y precios",   Icons.Default.BakeryDining,
            "pan catalogo precio foto articulo reventa pastel"),
        ModuleItem("Listas de precios", "Canales y mayoreo",  Icons.Default.Sell,
            "precio mayoreo menudeo revendedor tarifa"),
        ModuleItem("Pedidos de clientes", "Revendedores y encargos", Icons.Default.ShoppingBag,
            "encargo pastel cumpleanos revendedor anticipo cliente"),
        ModuleItem("Revendedores", "Validar nuevos registros", Icons.Default.Handshake,
            "tiendas clientes externos aprobar registro mayorista"),
        ModuleItem("Punto de venta",  "Caja y cobro rápido",  Icons.Default.PointOfSale,
            "vender cobrar caja pos ticket factura cajera mostrador"),
        ModuleItem("Historial de ventas", "Transacciones del día", Icons.Default.Receipt,
            "ventas tickets cobros anular venta transacciones"),
        ModuleItem("Cierre del día",      "Consolidado por sucursal", Icons.Default.LockClock,
            "cuadre corte de caja efectivo deposito banco"),
        ModuleItem("Cuadre sorpresa",     "Auditoría a cajas abiertas", Icons.Default.Policy,
            "arqueo auditoria revisar caja efectivo"),
        ModuleItem("Salidas del mostrador", "Merma, accidentes y regalos", Icons.Default.DeleteSweep,
            "merma accidente regalo botar se cayo quebrado"),
        ModuleItem("Créditos",            "Cuentas por cobrar y adelantos", Icons.Default.CreditScore,
            "fiado cuentas por cobrar deudas adelanto empleado cobrar"),
    )),
    // Administracion = se toca una vez al mes; que se apague es correcto.
    ModuleSection("Administración", Icons.Default.Settings, BrandColors.MutedSlate, BrandColors.Sand, listOf(
        // Antes eran una fila de botones debajo de la gráfica; ese lugar ahora es de los
        // accesos directos de cada persona, y los reportes viven juntos en su propio módulo.
        ModuleItem("Reportes",             "Ventas, producción y costos", Icons.Default.Assessment,
            "estadisticas graficas ganancias utilidad informe resultados costos"),
        ModuleItem("Cumpleaños",           "Los del equipo, mes a mes", Icons.Default.Cake,
            "cumpleanos cumpleaneros nacimiento fiesta celebrar equipo pastel"),
        ModuleItem("Configuración",        "Código de invitación y entregas", Icons.Default.Settings,
            "ajustes codigo invitacion empresa"),
        ModuleItem("Facturación",          "NIT y sucursales que facturan", Icons.AutoMirrored.Filled.ReceiptLong,
            "fel factura electronica sat nit digifact"),
        ModuleItem("Bitácora de cajas",    "Aperturas, cierres y fuera de horario", Icons.Default.History,
            "historial aperturas cierres cajas auditoria horario"),
        ModuleItem("Proveedores",          "Gestión externa",      Icons.Default.Business,
            "suplidor distribuidor contacto"),
        ModuleItem("Gastos",               "Lo gastado del mes",   Icons.Default.AccountBalanceWallet,
            "egresos pagos luz agua salidas de dinero"),
        ModuleItem("Costos fijos",         "Renta, luz, internet", Icons.Default.EventRepeat,
            "renta alquiler luz internet presupuesto mensual"),
        ModuleItem("Sucursales",           "Gestión de locales",   Icons.Default.Store,
            "tiendas locales direccion"),
        ModuleItem("Usuarios",             "Accesos y permisos",   Icons.Default.ManageAccounts,
            "cuentas permisos acceso roles login"),
        ModuleItem("Colaboradores",        "Equipo de trabajo",    Icons.Default.Badge,
            "empleados personal trabajadores planilla nomina salario pin"),
        ModuleItem("Puestos", "Perfiles y funciones", Icons.Default.WorkOutline,
            "cargos funciones perfiles"),
        ModuleItem("Turnos",               "Configurar jornadas",  Icons.Default.Schedule,
            "horarios jornadas manana tarde noche"),
        ModuleItem("Activos fijos",        "Patrimonio y mantenimiento", Icons.Default.Construction,
            "maquinas horno equipo mantenimiento patrimonio vehiculo"),
        ModuleItem("Cajas",  "Configurar cajas POS",       Icons.Default.Calculate,
            "caja registradora pos gaveta configurar"),
        ModuleItem("Categorías POS",       "Agrupar productos para venta", Icons.Default.Category,
            "botones grupos venta categorias"),
        ModuleItem("Empaques",             "Bolsa y cajas que se cobran", Icons.Default.LocalMall,
            "bolsa caja cobrar empaque"),
        ModuleItem("Pago con tarjeta",     "Mínimo y recargo",           Icons.Default.CreditCard,
            "tarjeta pos recargo comision visa minimo"),
    ))
)

/** Minúsculas y sin tildes: "producción", "Produccion" y "PRODUCCIÓN" buscan lo mismo. */
internal fun normalizeForSearch(text: String): String = buildString(text.length) {
    text.lowercase().forEach { c ->
        append(
            when (c) {
                'á', 'à', 'ä', 'â' -> 'a'
                'é', 'è', 'ë', 'ê' -> 'e'
                'í', 'ì', 'ï', 'î' -> 'i'
                'ó', 'ò', 'ö', 'ô' -> 'o'
                'ú', 'ù', 'ü', 'û' -> 'u'
                'ñ' -> 'n'
                else -> c
            }
        )
    }
}

/**
 * Cada palabra escrita tiene que aparecer en el nombre, la descripción o las palabras clave
 * del módulo ("pan merma" encuentra Conteo de pan). Null = no coincide; si coincide, el
 * número ordena: primero los que coinciden por nombre, después por descripción o clave.
 */
private fun moduleSearchRank(module: ModuleItem, sectionTitle: String, query: String): Int? {
    val words = normalizeForSearch(query).split(' ', ',')
        .filter { it.isNotBlank() && it !in SEARCH_STOPWORDS }
    if (words.isEmpty()) return null
    val title = normalizeForSearch(module.title)
    val haystack = "$title ${normalizeForSearch(module.subtitle)} ${normalizeForSearch(module.keywords)} " +
        normalizeForSearch(sectionTitle)
    if (!words.all { it in haystack }) return null
    return when {
        title.startsWith(words.first()) -> 0
        words.all { it in title } -> 1
        else -> 2
    }
}

/** "Pedidos de clientes": el "de" no debe hacer que coincida todo. */
private val SEARCH_STOPWORDS = setOf("de", "del", "la", "el", "los", "las", "y", "a", "en", "para", "por", "con", "al")

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
        "Empaques"          -> hasPermission(Permission.MANAGE_POS_CONFIG)
        "Pago con tarjeta"  -> hasPermission(Permission.MANAGE_POS_CONFIG)
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
        "Traslados" -> hasPermission(Permission.SEND_STOCK_TRANSFER) ||
                       hasPermission(Permission.RECEIVE_DISPATCH) ||
                       hasPermission(Permission.MANAGE_FINISHED_STOCK)
        "Salidas del mostrador" -> hasPermission(Permission.VIEW_CASH_AUDIT) ||
                                   hasPermission(Permission.FORGIVE_STAFF_ACCIDENT) ||
                                   hasPermission(Permission.MANAGE_FINISHED_STOCK)
        "Revendedores" -> hasPermission(Permission.MANAGE_EXTERNAL_CUSTOMERS)
        "Créditos"          -> hasPermission(Permission.MANAGE_CREDIT_ACCOUNTS)
        "Reportes"          -> canSeeAnyReport()
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
    onNavigateToPackagingAdmin: () -> Unit = {},
    onNavigateToCardRules: () -> Unit = {},
    onNavigateToCredit: () -> Unit = {},
    onNavigateToBreadCount: () -> Unit = {},
    onNavigateToDailyCashClose: () -> Unit = {},
    onNavigateToSpotCashCheck: () -> Unit = {},
    onNavigateToSalesReport: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToBirthdays: () -> Unit = {},
    /** (employeeId, nombre nuevo): la persona del equipo corrigió su nombre en su perfil. */
    onOperatorRenamed: (String, String) -> Unit = { _, _ -> },
    onNavigateToCustomerOrders: () -> Unit = {},
    onNavigateToOrganizationSettings: () -> Unit = {},
    onNavigateToFelSetup: () -> Unit = {},
    onNavigateToCashAudit: () -> Unit = {},
    onNavigateToCounterRemovals: () -> Unit = {},
    onNavigateToStockTransfers: () -> Unit = {},
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
    var showProfile by remember { mutableStateOf(false) }
    var showBirthdays by remember { mutableStateOf(false) }
    var showShortcutEditor by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // En un equipo de sucursal el Home ya está compuesto debajo de la pantalla de PIN y el
    // id es el de la cuenta: con solo `user?.id` de llave se quedaba con lo cargado SIN
    // persona (cero permisos, sin gráficas) aunque luego entrara alguien con su PIN.
    LaunchedEffect(user?.id, user?.operatorEmployeeId, user?.permissions) {
        user?.let { homeViewModel.ensureLoadedFor(it) }
    }
    // La campana se suscribe desde Home porque es la pantalla que siempre está
    // en el backstack: así el badge sigue vivo aunque el usuario ande en otro
    // módulo y vuelva.
    // Los avisos se filtran por los permisos de quien está usando el equipo: con solo el id
    // de llave, en un equipo de sucursal se quedaba el filtro de antes del PIN (cero
    // permisos) o el de la persona anterior.
    LaunchedEffect(user?.id, user?.orgId, user?.operatorEmployeeId, user?.permissions, user?.branchIds) {
        val u = user ?: return@LaunchedEffect
        notificationViewModel.observe(u.orgId, u)
    }
    // El token del teléfono es de la cuenta, no de la persona: basta con registrarlo una vez.
    LaunchedEffect(user?.id, user?.orgId) {
        val u = user ?: return@LaunchedEffect
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
            "Empaques"                -> onNavigateToPackagingAdmin()
            "Pago con tarjeta"        -> onNavigateToCardRules()
            "Conteo de pan"           -> onNavigateToBreadCount()
            "Cierre del día"          -> onNavigateToDailyCashClose()
            "Cuadre sorpresa"         -> onNavigateToSpotCashCheck()
            "Pedidos de clientes"     -> onNavigateToCustomerOrders()
            "Configuración"           -> onNavigateToOrganizationSettings()
            "Facturación"             -> onNavigateToFelSetup()
            "Bitácora de cajas"       -> onNavigateToCashAudit()
            "Salidas del mostrador"   -> onNavigateToCounterRemovals()
            "Traslados"               -> onNavigateToStockTransfers()
            "Revendedores"    -> onNavigateToResellerApproval()
            "Créditos"                -> onNavigateToCredit()
            "Reportes"                -> onNavigateToReports()
            "Cumpleaños"              -> onNavigateToBirthdays()
        }
    }

    val visibleSections = remember(user) {
        if (user == null) emptyList()
        else ALL_SECTIONS.mapNotNull { section ->
            val visible = section.modules.filter { hasModuleAccess(it.title, user) }
            if (visible.isNotEmpty()) section.copy(modules = visible) else null
        }
    }
    val isSearching = searchQuery.isNotBlank()
    // Mientras se busca, el lanzador se reduce a lo que coincide, en sus mismas secciones
    // (así el color sigue diciendo de dónde es cada módulo).
    val searchSections = remember(visibleSections, searchQuery) {
        if (!isSearching) emptyList()
        else visibleSections.mapNotNull { section ->
            val hits = section.modules
                .mapNotNull { m -> moduleSearchRank(m, section.title, searchQuery)?.let { m to it } }
                .sortedBy { it.second }
                .map { it.first }
            if (hits.isNotEmpty()) section.copy(modules = hits) else null
        }
    }
    val showDashboard = user?.hasPermission(Permission.VIEW_OPERATIONS_REPORT) == true
    // Los accesos directos solo pueden ser módulos que la persona ve hoy en el lanzador:
    // si le quitan un permiso, el acceso guardado deja de pintarse sin tocar lo guardado.
    val visibleTitles = remember(visibleSections) {
        visibleSections.flatMap { sec -> sec.modules.map { it.title } }.toSet()
    }
    val shortcutItems = remember(homeState.shortcuts, visibleTitles) {
        homeState.shortcuts.filter { it in visibleTitles }.mapNotNull { title -> shortcutFor(title) }
    }
    val avatarUrl = homeState.profileEmployee?.photoUrl?.takeIf { it.isNotBlank() }
        ?: state.operator?.photoUrl?.takeIf { it.isNotBlank() }
        ?: user?.photoUrl?.takeIf { it.isNotBlank() }

    if (showProfile && user != null) {
        ProfileDialog(
            user = user,
            orgName = state.orgName,
            employee = homeState.profileEmployee,
            photoUrl = avatarUrl,
            isSaving = homeState.isSavingProfile,
            error = homeState.profileError,
            savedVersion = homeState.profileSavedVersion,
            onSave = { name, phone, phoneAux, email ->
                val employeeId = homeState.profileEmployee?.id.orEmpty()
                homeViewModel.saveProfile(name, phone, phoneAux, email) { saved ->
                    onOperatorRenamed(employeeId, saved)
                }
            },
            onDismiss = { showProfile = false; homeViewModel.clearProfileError() }
        )
    }

    if (showShortcutEditor) {
        ShortcutEditorDialog(
            sections = visibleSections,
            selected = homeState.shortcuts.filter { it in visibleTitles },
            onSave = { homeViewModel.saveShortcuts(it); showShortcutEditor = false },
            onDismiss = { showShortcutEditor = false }
        )
    }

    val homeListState = rememberLazyListState()
    homeState.birthdays?.let { b ->
        if (showBirthdays) BirthdaysMonthDialog(
            b,
            onDismiss = { showBirthdays = false },
            onOpenYear = { showBirthdays = false; onNavigateToBirthdays() }
        )
    }
    Scaffold(
        containerColor = BrandColors.Ivory,
        topBar = {
            BrandTopAppBar(
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
        // Deslizar hacia abajo desde arriba vuelve a pedir todo: alertas, gráficas,
        // pendientes y cumpleaños. Sin esto el Home se quedaba con lo de la mañana.
        PullToRefreshBox(
            isRefreshing = homeState.isRefreshing,
            onRefresh = { user?.let { homeViewModel.pullToRefresh(it) } },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        LazyColumn(
            state = homeListState,
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)
        ) {
            item {
                GreetingHeader(
                    user = user,
                    orgName = state.orgName,
                    positionTitle = homeState.profileEmployee?.positionTitle.orEmpty(),
                    photoUrl = avatarUrl,
                    onAvatarClick = { showProfile = true }
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    UpdatedAgo(
                        lastUpdatedAt = homeState.lastUpdatedAt,
                        refreshing = homeState.isRefreshing,
                        onRefresh = { user?.let { homeViewModel.pullToRefresh(it) } }
                    )
                }
                Spacer(Modifier.height(10.dp))
                state.operator?.let { op ->
                    OperatorBar(
                        name = op.name,
                        device = state.accountUser?.displayName.orEmpty(),
                        onSwitch = viewModel::lockOperator
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (visibleSections.isNotEmpty()) {
                item(key = "search") {
                    ModuleSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onDone = { focusManager.clearFocus() }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (isSearching) {
                if (searchSections.isEmpty()) {
                    item(key = "search_empty") {
                        Text(
                            "Ningún módulo coincide con \"${searchQuery.trim()}\". Prueba con otra palabra, " +
                                "por ejemplo lo que quieres hacer: \"vender\", \"fiado\", \"horno\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedText,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
                searchSections.forEach { section ->
                    item(key = "s_hdr_${section.title}") {
                        SectionHeader(section)
                        Spacer(Modifier.height(12.dp))
                    }
                    item(key = "s_grid_${section.title}") {
                        ResponsiveGrid(
                            items = section.modules,
                            onClick = { focusManager.clearFocus(); searchQuery = ""; navigate(it.title) }
                        ) { module, modifier, onClick ->
                            ModuleCard(
                                module = module,
                                iconTint = section.iconColor,
                                iconBg = section.container,
                                modifier = modifier,
                                onClick = onClick
                            )
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
            if (!isSearching && homeState.pendingTasks.isNotEmpty()) {
                item(key = "pending") {
                    PendingTasksSection(
                        tasks = homeState.pendingTasks,
                        canOpen = { title -> visibleTitles.contains(title) },
                        onOpen = { navigate(it) }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (!isSearching && showDashboard) {
                item(key = "dashboard") {
                    HomeDashboardSection(
                        dashboard = homeState.dashboard,
                        loading = homeState.dashboardLoading,
                        onSelectPeriod = homeViewModel::selectDashboardPeriod,
                        onOpenSalesReport = onNavigateToSalesReport.takeIf {
                            user?.hasPermission(Permission.VIEW_SALES_REPORT) == true
                        },
                        isHomeScrolling = { homeListState.isScrollInProgress }
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
            // Los avisos van después de las gráficas: con varios avisos arriba, las gráficas
            // quedaban fuera de la primera pantalla.
            if (!isSearching && homeState.alerts.isNotEmpty()) {
                item(key = "alerts") {
                    AlertsBanner(
                        alerts = homeState.alerts,
                        onAlertClick = { alert -> navigateAlert(alert, ::navigate) }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (!isSearching && homeState.birthdays != null) {
                item(key = "birthdays") {
                    BirthdaysCard(
                        homeState.birthdays,
                        onOpenMonth = { showBirthdays = true },
                        onOpenYear = onNavigateToBirthdays
                    )
                    if (homeState.birthdays?.thisMonth?.isNotEmpty() == true) Spacer(Modifier.height(24.dp))
                }
            }
            // Hasta saber de quién son, no se pinta nada: si no, al cambiar de persona en el
            // equipo se verían un instante los accesos de la anterior.
            if (!isSearching && homeState.shortcutsLoaded && visibleTitles.isNotEmpty()) {
                item(key = "shortcuts") {
                    ShortcutsSection(
                        items = shortcutItems,
                        onEdit = { showShortcutEditor = true },
                        onOpen = { navigate(it) }
                    )
                    Spacer(Modifier.height(28.dp))
                }
            }
            if (!isSearching) visibleSections.forEach { section ->
                item(key = "hdr_${section.title}") {
                    SectionHeader(section)
                    Spacer(Modifier.height(12.dp))
                }
                item(key = "grid_${section.title}") {
                    ResponsiveGrid(
                        items = section.modules,
                        onClick = { navigate(it.title) }
                    ) { module, modifier, onClick ->
                        ModuleCard(
                            module = module,
                            iconTint = section.iconColor,
                            iconBg = section.container,
                            modifier = modifier,
                            onClick = onClick
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
        }
    }
}

@Composable
private fun GreetingHeader(
    user: User?,
    orgName: String?,
    positionTitle: String,
    photoUrl: String?,
    onAvatarClick: () -> Unit
) {
    // La foto va a la izquierda, antes del nombre: es lo primero que se lee y deja claro de
    // quién es el inicio en un equipo que comparten varias personas.
    val greeting = remember {
        dayGreeting(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            name = user?.displayName.orEmpty(),
            photoUrl = photoUrl,
            size = 54,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onAvatarClick)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "$greeting, ${user?.displayName?.split(" ")?.firstOrNull() ?: "Usuario"}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = PrimaryBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Con ficha, el puesto dice más que el rol: en un equipo de sucursal todas
            // las personas comparten el rol de la cuenta ("Empleado").
            val label = positionTitle.ifBlank { roleLabel(user?.role ?: UserRole.EMPLOYEE) }
            Text(
                text = if (!orgName.isNullOrBlank()) "$label · $orgName" else label,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText
            )
        }
    }
}

private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.OWNER    -> "Propietario"
    UserRole.ADMIN    -> "Administrador"
    UserRole.MANAGER  -> "Encargado"
    UserRole.EMPLOYEE -> "Empleado"
    UserRole.RESELLER -> "Revendedor"
}

/** Foto de la persona (la de su ficha de colaborador primero) o su inicial. */
@Composable
private fun Avatar(name: String, photoUrl: String?, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(PrimaryBlue)
            .border(2.dp, BrandColors.Accent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUrl.isNullOrBlank()) {
            ProductImage(
                imageUrl = photoUrl,
                contentDescription = "Foto de perfil",
                modifier = Modifier.size(size.dp)
            )
        } else {
            Text(
                text = (name.firstOrNull() ?: 'U').toString().uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = if (size >= 72) MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
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

/**
 * Buscador del lanzador. Va arriba, antes de alertas y gráficas: es lo primero que se ve y,
 * al escribir, todo lo demás se esconde y los resultados quedan pegados a la barra.
 */
@Composable
private fun ModuleSearchBar(query: String, onQueryChange: (String) -> Unit, onDone: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Buscar módulo: vender, fiado, horno…", color = MutedText) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = PrimaryBlue) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange(""); onDone() }) {
                    Icon(Icons.Default.Close, "Borrar búsqueda", tint = MutedText)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = PrimaryBlue,
            unfocusedBorderColor = Color(0xFFE0D6CC)
        )
    )
}

/** Un acceso directo ya resuelto: el módulo con los colores de su sección. */
private data class ShortcutItem(val module: ModuleItem, val iconTint: Color, val iconBg: Color)

private fun shortcutFor(title: String): ShortcutItem? {
    val section = ALL_SECTIONS.firstOrNull { sec -> sec.modules.any { it.title == title } } ?: return null
    val module = section.modules.first { it.title == title }
    return ShortcutItem(module, section.iconColor, section.container)
}

/**
 * Accesos directos de la PERSONA (no de la cuenta): en el equipo de una sucursal cada
 * colaborador ve los suyos al identificarse con su PIN. Solo se pueden escoger módulos que
 * ya le aparecen abajo en el lanzador.
 */
@Composable
private fun ShortcutsSection(
    items: List<ShortcutItem>,
    onEdit: () -> Unit,
    onOpen: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(BrandColors.PrimaryContainer, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Star, null, tint = PrimaryBlue, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Accesos directos",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = SlateText,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                Spacer(Modifier.width(4.dp))
                Text(
                    if (items.isEmpty()) "Agregar" else "Editar",
                    color = PrimaryBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (items.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.04f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.AddCircleOutline, null, tint = PrimaryBlue)
                    Text(
                        "Escoge los módulos que más usas para tenerlos aquí a un toque.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateText
                    )
                }
            }
        } else {
            ResponsiveGrid(
                items = items,
                onClick = { onOpen(it.module.title) }
            ) { item, modifier, onClick ->
                ModuleCard(
                    module = item.module,
                    iconTint = item.iconTint,
                    iconBg = item.iconBg,
                    modifier = modifier,
                    onClick = onClick
                )
            }
        }
    }
}

/**
 * Escoger accesos directos entre los módulos que la persona ve. El orden en el Home es el
 * orden en que los fue marcando: lo que ya tenía se queda en su lugar y lo nuevo va al final.
 */
@Composable
private fun ShortcutEditorDialog(
    sections: List<ModuleSection>,
    selected: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by remember { mutableStateOf(selected) }
    fun toggle(title: String) {
        chosen = if (title in chosen) chosen - title else chosen + title
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accesos directos") },
        text = {
            Column {
                Text(
                    "Solo aparecen los módulos a los que tienes acceso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                    sections.forEach { section ->
                        item(key = "h_${section.title}") {
                            Text(
                                section.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                color = SlateText,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }
                        section.modules.forEach { module ->
                            item(key = "m_${module.title}") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { toggle(module.title) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = module.title in chosen,
                                        onCheckedChange = { toggle(module.title) }
                                    )
                                    Icon(module.icon, null, tint = section.iconColor, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(module.title, color = SlateText, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(chosen) }) { Text("Guardar", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * Perfil de quien está en el Home. Con ficha de colaborador: nombre completo, puesto y
 * sucursal, y puede corregir su nombre y contacto (lo demás de la ficha es del encargado).
 * Sin ficha (cuenta propia no ligada): solo lo que se sabe de la cuenta.
 */
@Composable
private fun ProfileDialog(
    user: User,
    orgName: String?,
    employee: Employee?,
    photoUrl: String?,
    isSaving: Boolean,
    error: String?,
    savedVersion: Int,
    onSave: (name: String, phone: String, phoneAux: String, email: String) -> Unit,
    onDismiss: () -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(employee?.id) { mutableStateOf(employee?.name.orEmpty()) }
    var phone by rememberSaveable(employee?.id) { mutableStateOf(employee?.phone.orEmpty()) }
    var phoneAux by rememberSaveable(employee?.id) { mutableStateOf(employee?.phoneAux.orEmpty()) }
    var email by rememberSaveable(employee?.id) { mutableStateOf(employee?.email.orEmpty()) }
    // Guardado bien: vuelve a la vista de lectura, ya con los datos nuevos.
    val openedAtVersion = remember { savedVersion }
    LaunchedEffect(savedVersion) {
        if (savedVersion != openedAtVersion) editing = false
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Avatar(name = employee?.name ?: user.displayName, photoUrl = photoUrl, size = 84)
                Spacer(Modifier.height(12.dp))
                if (!editing) {
                    Text(
                        employee?.name?.ifBlank { null } ?: user.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SlateText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    if (employee != null) {
                        ProfileLine(Icons.Default.WorkOutline, "Puesto", employee.positionTitle.ifBlank { "Sin puesto asignado" })
                        ProfileLine(Icons.Default.Store, "Sucursal", employee.branchName.ifBlank { "Sin sucursal asignada" })
                        ProfileLine(Icons.Default.Phone, "Teléfono", employee.phone.ifBlank { "—" })
                        ProfileLine(Icons.Default.PhoneAndroid, "Teléfono adicional", employee.phoneAux.ifBlank { "—" })
                        ProfileLine(Icons.Default.Email, "Correo", employee.email.ifBlank { "—" })
                    } else {
                        ProfileLine(Icons.Default.Badge, "Rol", roleLabel(user.role))
                        if (!orgName.isNullOrBlank()) ProfileLine(Icons.Default.Store, "Panadería", orgName)
                        if (user.email.isNotBlank()) ProfileLine(Icons.Default.Email, "Correo", user.email)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tu usuario no está ligado a una ficha de colaborador, por eso no hay " +
                                "puesto ni sucursal que mostrar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Nombre completo") },
                        singleLine = true, enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it },
                        label = { Text("Teléfono") },
                        singleLine = true, enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phoneAux, onValueChange = { phoneAux = it },
                        label = { Text("Teléfono adicional") },
                        singleLine = true, enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email, onValueChange = { email = it },
                        label = { Text("Correo") },
                        singleLine = true, enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "El puesto, la sucursal y lo demás de tu ficha lo cambia tu encargado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            when {
                employee == null -> TextButton(onClick = onDismiss) { Text("Cerrar") }
                !editing -> TextButton(onClick = {
                    name = employee.name
                    phone = employee.phone
                    phoneAux = employee.phoneAux
                    email = employee.email
                    editing = true
                }) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Editar mis datos")
                }
                else -> TextButton(
                    enabled = !isSaving && name.isNotBlank(),
                    onClick = { onSave(name, phone, phoneAux, email) }
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Guardar", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            if (employee != null) {
                TextButton(
                    enabled = !isSaving,
                    onClick = { if (editing) editing = false else onDismiss() }
                ) { Text(if (editing) "Cancelar" else "Cerrar") }
            }
        }
    )
}

@Composable
private fun ProfileLine(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MutedText)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = SlateText)
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

/** Rejilla de fichas que se adapta al ancho; la usan el lanzador y los accesos directos. */
@Composable
private fun <T> ResponsiveGrid(
    items: List<T>,
    onClick: (T) -> Unit,
    cell: @Composable (item: T, modifier: Modifier, onClick: () -> Unit) -> Unit
) {
    BoxWithConstraints {
        val cols = when {
            maxWidth >= 720.dp -> 5
            maxWidth >= 560.dp -> 4
            maxWidth >= 400.dp -> 3
            else               -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(cols).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { item -> cell(item, Modifier.weight(1f)) { onClick(item) } }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
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
