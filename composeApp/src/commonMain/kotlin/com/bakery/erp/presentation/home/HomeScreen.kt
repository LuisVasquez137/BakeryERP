package com.bakery.erp.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakery.erp.model.Permission
import com.bakery.erp.model.UserRole
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import org.koin.compose.viewmodel.koinViewModel

// --- MODELOS ---
data class ModuleItem(val title: String, val subtitle: String, val icon: ImageVector, val color: Color)
data class QuickAction(val label: String, val icon: ImageVector)

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
    viewModel: AuthViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val user = state.user
    var menuExpanded by remember { mutableStateOf(false) }

    val allModules = remember {
        listOf(
            ModuleItem("Productos", "Ventas y catálogo", Icons.Default.BakeryDining, Color(0xFFE9DCC9)),
            ModuleItem("Insumos", "Costos y stock", Icons.Default.Kitchen, Color(0xFFFDEFD9)),
            ModuleItem("Proveedores", "Gestión externa", Icons.Default.Business, Color(0xFFDFE6ED)),
            ModuleItem("Recetas", "Fórmulas y procesos", Icons.AutoMirrored.Filled.MenuBook, Color(0xFFE8F5E9)),
            ModuleItem("Compras", "Ingreso de insumos", Icons.Default.ShoppingCart, Color(0xFFE8F5E9)),
            ModuleItem("Inventario", "Mermas diarias", Icons.Default.Inventory, Color(0xFFE2E8F0)),
            ModuleItem("Sucursales", "Gestión de locales", Icons.Default.Store, Color(0xFFE8EAF6)),
            ModuleItem("Gastos", "Control por sucursal", Icons.Default.Receipt, Color(0xFFFCE4EC)),
            ModuleItem("Usuarios", "Accesos y permisos", Icons.Default.People, Color(0xFFEDE7F6)),
            ModuleItem("Pedidos", "Enviar a producción", Icons.Default.ReceiptLong, Color(0xFFE8EAF6)),
            ModuleItem("Producción", "Lotes por turno", Icons.Default.Factory, Color(0xFFE8F5E9)),
            ModuleItem("Turnos", "Configurar jornadas", Icons.Default.Schedule, Color(0xFFF3E5F5)),
            ModuleItem("Colaboradores", "Equipo de trabajo", Icons.Default.Badge, Color(0xFFE8EAF6))
        )
    }

    // Solo muestra los módulos para los que el usuario tiene permiso.
    // hasPermission() retorna true automáticamente para el OWNER.
    val modules = remember(user) {
        val u = user ?: return@remember emptyList()
        allModules.filter { module ->
            when (module.title) {
                "Productos"  -> u.hasPermission(Permission.VIEW_PRODUCTS)
                "Insumos"    -> u.hasPermission(Permission.VIEW_INGREDIENTS)
                "Proveedores"-> u.hasPermission(Permission.VIEW_SUPPLIERS)
                "Recetas"    -> u.hasPermission(Permission.VIEW_RECIPES)
                "Compras"    -> u.hasPermission(Permission.RECEIVE_STOCK)
                "Inventario" -> u.hasPermission(Permission.VIEW_INGREDIENTS)
                "Sucursales" -> u.hasPermission(Permission.VIEW_ALL_BRANCHES) || u.hasPermission(Permission.MANAGE_BRANCHES)
                "Gastos"     -> u.hasPermission(Permission.VIEW_EXPENSES)
                                || u.hasPermission(Permission.ADD_VARIABLE_EXPENSE)
                                || u.hasPermission(Permission.MANAGE_FIXED_BUDGET)
                "Usuarios"   -> u.hasPermission(Permission.MANAGE_USERS)
                "Pedidos"    -> u.hasPermission(Permission.SEND_PRODUCTION_ORDER) || u.hasPermission(Permission.VIEW_PRODUCTION)
                "Producción" -> u.hasPermission(Permission.MANAGE_PRODUCTION_LOTS) || u.hasPermission(Permission.VIEW_PRODUCTION)
                "Turnos"          -> u.hasPermission(Permission.MANAGE_PRODUCTION_SHIFTS)
                "Colaboradores"   -> u.hasPermission(Permission.MANAGE_EMPLOYEES)
                else              -> true
            }
        }
    }

    val quickActions = remember {
        listOf(
            QuickAction("Pedidos", Icons.Default.ReceiptLong),
            QuickAction("Recetas", Icons.AutoMirrored.Filled.MenuBook),
            QuickAction("Gastos", Icons.Default.Payments),
            QuickAction("Ventas", Icons.Default.PointOfSale)
        )
    }

    Scaffold(
        containerColor = Color(0xFFFDFBF7), // Warm Ivory
        topBar = {
            TopAppBar(
                title = { Text("Bagueto", fontWeight = FontWeight.ExtraBold, color = Color.White) },
                actions = {
                    // TOQUE 1: Icono de notificaciones naranja (Subtileza funcional)
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Notificaciones",
                            tint = Color(0xFFCC5500) // NARANJA SUTIL
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Cerrar sesión") },
                            leadingIcon = { Icon(Icons.Default.ExitToApp, null, tint = Color(0xFF123456)) },
                            onClick = { menuExpanded = false; viewModel.signOut() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF123456)) // Deep Sea Blue
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. CABECERA
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "¡Hola, ${user?.displayName?.split(" ")?.firstOrNull() ?: "Luis"}!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF123456)
                        )
                        // ROL DINÁMICO
                        Text(
                            text = when (user?.role ?: UserRole.EMPLOYEE) {
                                UserRole.OWNER -> "Propietario"
                                UserRole.ADMIN -> "Administrador"
                                UserRole.MANAGER -> "Encargado"
                                UserRole.EMPLOYEE -> "Empleado"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                    // TOQUE 2: Borde naranja sutil alrededor del avatar
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF123456))
                            .padding(2.dp), // Espacio para el borde
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = Color(0xFF123456),
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFCC5500)) // BORDE NARANJA SUTIL
                        ) {
                            Text(
                                text = (user?.displayName?.firstOrNull() ?: 'L').toString().uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.wrapContentSize(Alignment.Center)
                            )
                        }
                    }
                }
            }

            // 2. GRÁFICA DE BARRAS PROFESIONAL (Sobria, como pediste)
            item(span = { GridItemSpan(2) }) {
                ProfessionalSalesChart()
            }

            // 3. TÍTULO CON DETALLE (Ocupa 2 columnas)
            item(span = { GridItemSpan(2) }) {
                // TOQUE 3: Línea naranja sutil debajo del título de sección
                Column {
                    Text("Accesos Directos", fontWeight = FontWeight.Bold, color = Color(0xFF123456))
                    Spacer(Modifier.height(4.dp))
                    Box(modifier = Modifier.width(40.dp).height(2.dp).background(Color(0xFFCC5500))) // LÍNEA NARANJA SUTIL
                }
            }

            // 4. CARRUSEL
            item(span = { GridItemSpan(2) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(quickActions) { action ->
                        QuickActionCard(action)
                    }
                }
            }

            // 5. MÓDULOS GRID (Sin cambios en color para mantener sobriedad)
            items(modules) { modulo ->
                ModuleGridCard(modulo) {
                    when(modulo.title) {
                        "Productos"  -> onNavigateToProducts()
                        "Insumos"    -> onNavigateToIngredients()
                        "Proveedores"-> onNavigateToSuppliers()
                        "Recetas"    -> onNavigateToRecipes()
                        "Compras"    -> onNavigateToPurchases()
                        "Sucursales" -> onNavigateToBranches()
                        "Gastos"     -> onNavigateToExpenses()
                        "Usuarios"   -> onNavigateToUsers()
                        "Pedidos"    -> onNavigateToProductionOrders()
                        "Producción" -> onNavigateToProductionLots()
                        "Turnos"          -> onNavigateToProductionShifts()
                        "Colaboradores"   -> onNavigateToEmployees()
                    }
                }
            }
        }
    }
}

// --- COMPONENTES ---

@Composable
fun ProfessionalSalesChart() {
    val days = listOf("Lun", "Mar", "Mie", "Jue", "Vie", "Sab", "Dom")
    val sales = listOf(0.4f, 0.6f, 0.5f, 0.8f, 0.9f, 0.3f, 0.2f)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Ventas de la Semana", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF123456))
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                sales.forEachIndexed { index, value ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .fillMaxHeight(value)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                // BARRAS SOBRIAS (Navy suave)
                                .background(Color(0xFF123456).copy(alpha = 0.6f))
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(text = days[index], style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionCard(action: QuickAction) {
    Card(
        modifier = Modifier.size(width = 110.dp, height = 90.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF5E9DA))
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(action.icon, null, tint = Color(0xFF123456), modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(6.dp))
            Text(action.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF123456))
        }
    }
}

@Composable
fun ModuleGridCard(modulo: ModuleItem, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().aspectRatio(1.1f).clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.size(48.dp).background(modulo.color, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(modulo.icon, null, tint = Color(0xFF123456), modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(modulo.title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF123456))
            Text(modulo.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}