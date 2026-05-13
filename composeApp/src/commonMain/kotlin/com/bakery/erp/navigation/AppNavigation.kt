package com.bakery.erp.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bakery.erp.model.Permission
import com.bakery.erp.model.User
import com.bakery.erp.model.hasPermission
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bakery.erp.presentation.auth.AuthChoiceScreen
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.auth.JoinOrgScreen
import com.bakery.erp.presentation.auth.LoginScreen
import com.bakery.erp.presentation.auth.PendingApprovalScreen
import com.bakery.erp.presentation.auth.RegisterScreen
import com.bakery.erp.presentation.users.UserListScreen
import com.bakery.erp.presentation.users.UserPermissionsScreen
import com.bakery.erp.presentation.users.UserViewModel
import com.bakery.erp.presentation.home.HomeScreen
import com.bakery.erp.presentation.ingredients.IngredientDetailScreen
import com.bakery.erp.presentation.ingredients.IngredientFormScreen
import com.bakery.erp.presentation.ingredients.IngredientListScreen
import com.bakery.erp.presentation.ingredients.IngredientViewModel
import com.bakery.erp.presentation.products.ProductDetailScreen
import com.bakery.erp.presentation.products.ProductFormScreen
import com.bakery.erp.presentation.products.ProductListScreen
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.presentation.recipes.RecipeDetailScreen
import com.bakery.erp.presentation.recipes.RecipeFormScreen
import com.bakery.erp.presentation.recipes.RecipeListScreen
import com.bakery.erp.presentation.recipes.RecipeViewModel
import com.bakery.erp.presentation.branches.BranchDetailScreen
import com.bakery.erp.presentation.branches.BranchFormScreen
import com.bakery.erp.presentation.branches.BranchListScreen
import com.bakery.erp.presentation.branches.BranchViewModel
import com.bakery.erp.presentation.expenses.ExpenseDetailScreen
import com.bakery.erp.presentation.expenses.ExpenseFormScreen
import com.bakery.erp.presentation.expenses.ExpenseListScreen
import com.bakery.erp.presentation.expenses.ExpenseViewModel
import com.bakery.erp.presentation.expenses.FixedBudgetFormScreen
import com.bakery.erp.presentation.expenses.FixedBudgetListScreen
import com.bakery.erp.presentation.expenses.FixedBudgetViewModel
import com.bakery.erp.presentation.production.ProductionLotCompleteScreen
import com.bakery.erp.presentation.production.ProductionLotDetailScreen
import com.bakery.erp.presentation.production.ProductionLotFormScreen
import com.bakery.erp.presentation.production.ProductionLotListScreen
import com.bakery.erp.presentation.production.ProductionOrderDetailScreen
import com.bakery.erp.presentation.production.ProductionOrderFormScreen
import com.bakery.erp.presentation.production.ProductionOrderListScreen
import com.bakery.erp.presentation.employees.EmployeeDetailScreen
import com.bakery.erp.presentation.employees.EmployeeFormScreen
import com.bakery.erp.presentation.employees.EmployeeListScreen
import com.bakery.erp.presentation.employees.EmployeeViewModel
import com.bakery.erp.presentation.production.ProductionShiftFormScreen
import com.bakery.erp.presentation.production.ProductionShiftListScreen
import com.bakery.erp.presentation.production.ProductionViewModel
import com.bakery.erp.presentation.purchases.PurchaseDetailScreen
import com.bakery.erp.presentation.purchases.PurchaseFormScreen
import com.bakery.erp.presentation.purchases.PurchaseListScreen
import com.bakery.erp.presentation.purchases.PurchaseViewModel
import com.bakery.erp.presentation.suppliers.SupplierDetailScreen
import com.bakery.erp.presentation.suppliers.SupplierFormScreen
import com.bakery.erp.presentation.suppliers.SupplierListScreen
import com.bakery.erp.presentation.suppliers.SupplierViewModel
import org.koin.compose.viewmodel.koinViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object AuthChoice : Screen("auth_choice")
    object Register : Screen("register")
    object JoinOrg : Screen("join_org")
    object Home : Screen("home")
    object ProductList : Screen("products")
    object ProductDetail : Screen("products/detail/{productId}") {
        fun route(productId: String) = "products/detail/$productId"
    }
    object ProductForm : Screen("products/form?productId={productId}") {
        fun route(productId: String? = null) =
            if (productId != null) "products/form?productId=$productId" else "products/form"
    }
    object IngredientList : Screen("ingredients")
    object IngredientDetail : Screen("ingredients/detail/{ingredientId}") {
        fun route(ingredientId: String) = "ingredients/detail/$ingredientId"
    }
    object IngredientForm : Screen("ingredients/form?ingredientId={ingredientId}") {
        fun route(ingredientId: String? = null) =
            if (ingredientId != null) "ingredients/form?ingredientId=$ingredientId" else "ingredients/form"
    }
    object SupplierList : Screen("suppliers")
    object SupplierDetail : Screen("suppliers/detail/{supplierId}") {
        fun route(supplierId: String) = "suppliers/detail/$supplierId"
    }
    object SupplierForm : Screen("suppliers/form?supplierId={supplierId}") {
        fun route(supplierId: String? = null) =
            if (supplierId != null) "suppliers/form?supplierId=$supplierId" else "suppliers/form"
    }
    object RecipeList : Screen("recipes")
    object RecipeDetail : Screen("recipes/detail/{recipeId}") {
        fun route(recipeId: String) = "recipes/detail/$recipeId"
    }
    object RecipeForm : Screen("recipes/form?recipeId={recipeId}") {
        fun route(recipeId: String? = null) =
            if (recipeId != null) "recipes/form?recipeId=$recipeId" else "recipes/form"
    }
    object ExpenseList : Screen("expenses")
    object ExpenseDetail : Screen("expenses/detail/{expenseId}") {
        fun route(expenseId: String) = "expenses/detail/$expenseId"
    }
    object ExpenseForm : Screen("expenses/form?expenseId={expenseId}") {
        fun route(expenseId: String? = null) =
            if (expenseId != null) "expenses/form?expenseId=$expenseId" else "expenses/form"
    }
    object BranchList : Screen("branches")
    object BranchDetail : Screen("branches/detail/{branchId}") {
        fun route(branchId: String) = "branches/detail/$branchId"
    }
    object BranchForm : Screen("branches/form?branchId={branchId}") {
        fun route(branchId: String? = null) =
            if (branchId != null) "branches/form?branchId=$branchId" else "branches/form"
    }
    object FixedBudgetList : Screen("fixedBudget")
    object FixedBudgetForm : Screen("fixedBudget/form?itemId={itemId}") {
        fun route(itemId: String? = null) =
            if (itemId != null) "fixedBudget/form?itemId=$itemId" else "fixedBudget/form"
    }
    object PurchaseList : Screen("purchases")
    object PurchaseForm : Screen("purchases/form")
    object PurchaseDetail : Screen("purchases/detail/{purchaseId}") {
        fun route(purchaseId: String) = "purchases/detail/$purchaseId"
    }
    object PendingApproval : Screen("pending_approval")
    object UserList : Screen("users")
    object UserPermissions : Screen("users/permissions/{userId}") {
        fun route(userId: String) = "users/permissions/$userId"
    }

    // Colaboradores
    object EmployeeList : Screen("employees")
    object EmployeeDetail : Screen("employees/detail/{employeeId}") {
        fun route(employeeId: String) = "employees/detail/$employeeId"
    }
    object EmployeeForm : Screen("employees/form?employeeId={employeeId}") {
        fun route(employeeId: String? = null) =
            if (employeeId != null) "employees/form?employeeId=$employeeId" else "employees/form"
    }

    // Producción
    object ProductionOrderList : Screen("production/orders")
    object ProductionOrderForm : Screen("production/orders/form")
    object ProductionOrderDetail : Screen("production/orders/detail/{orderId}") {
        fun route(orderId: String) = "production/orders/detail/$orderId"
    }
    object ProductionLotList : Screen("production/lots")
    object ProductionLotForm : Screen("production/lots/form")
    object ProductionLotDetail : Screen("production/lots/detail/{lotId}") {
        fun route(lotId: String) = "production/lots/detail/$lotId"
    }
    object ProductionLotComplete : Screen("production/lots/complete/{lotId}") {
        fun route(lotId: String) = "production/lots/complete/$lotId"
    }
    object ProductionShiftList : Screen("production/shifts")
    object ProductionShiftForm : Screen("production/shifts/form?branchId={branchId}&shiftId={shiftId}") {
        fun route(branchId: String? = null, shiftId: String? = null): String {
            val params = buildList {
                if (!branchId.isNullOrEmpty()) add("branchId=$branchId")
                if (shiftId != null) add("shiftId=$shiftId")
            }
            return if (params.isEmpty()) "production/shifts/form"
                   else "production/shifts/form?${params.joinToString("&")}"
        }
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: AuthViewModel = koinViewModel(),
    productViewModel: ProductViewModel = koinViewModel(),
    ingredientViewModel: IngredientViewModel = koinViewModel(),
    supplierViewModel: SupplierViewModel = koinViewModel(),
    recipeViewModel: RecipeViewModel = koinViewModel(),
    branchViewModel: BranchViewModel = koinViewModel(),
    expenseViewModel: ExpenseViewModel = koinViewModel(),
    fixedBudgetViewModel: FixedBudgetViewModel = koinViewModel(),
    userViewModel: UserViewModel = koinViewModel(),
    purchaseViewModel: PurchaseViewModel = koinViewModel(),
    productionViewModel: ProductionViewModel = koinViewModel(),
    employeeViewModel: EmployeeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val purchaseState by purchaseViewModel.uiState.collectAsState()

    LaunchedEffect(purchaseState.isSaved) {
        if (purchaseState.isSaved) {
            val companyId = state.user?.orgId ?: return@LaunchedEffect
            val currentUser = state.user ?: return@LaunchedEffect
            ingredientViewModel.clearIngredientDetailData()
            ingredientViewModel.loadAccessibleBranches(companyId, currentUser)
        }
    }

    // rememberSaveable sobrevive rotación de pantalla; evita que LaunchedEffect
    // re-navegue a Home cada vez que la Activity se recrea.
    var initialNavigationDone by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isAuthenticated, state.isLoading, state.needsOnboarding, state.isPendingApproval) {
        if (state.isLoading) return@LaunchedEffect
        if (!initialNavigationDone) {
            initialNavigationDone = true
            when {
                state.isAuthenticated -> navController.navigate(Screen.Home.route) { popUpTo(0) { inclusive = true } }
                state.isPendingApproval -> navController.navigate(Screen.PendingApproval.route) { popUpTo(0) { inclusive = true } }
                state.needsOnboarding -> navController.navigate(Screen.AuthChoice.route) { popUpTo(0) { inclusive = true } }
                else -> navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
            }
        } else if (!state.isAuthenticated && !state.needsOnboarding && !state.isPendingApproval) {
            // Sesión cerrada mientras la app estaba abierta
            initialNavigationDone = false
            navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(navController = navController, startDestination = Screen.Login.route) {

        composable(Screen.PendingApproval.route) {
            PendingApprovalScreen(viewModel = viewModel)
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToAuthChoice = { navController.navigate(Screen.AuthChoice.route) },
                viewModel = viewModel
            )
        }
        composable(Screen.AuthChoice.route) {
            AuthChoiceScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onNavigateToJoinOrg = { navController.navigate(Screen.JoinOrg.route) },
                onNavigateToLogin = { navController.popBackStack(Screen.Login.route, inclusive = false) }
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = { navController.popBackStack(Screen.Login.route, inclusive = false) },
                viewModel = viewModel
            )
        }
        composable(Screen.JoinOrg.route) {
            JoinOrgScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToProducts = { navController.navigate(Screen.ProductList.route) },
                onNavigateToIngredients = { navController.navigate(Screen.IngredientList.route) },
                onNavigateToSuppliers = { navController.navigate(Screen.SupplierList.route) },
                onNavigateToRecipes = { navController.navigate(Screen.RecipeList.route) },
                onNavigateToBranches = { navController.navigate(Screen.BranchList.route) },
                onNavigateToExpenses = { navController.navigate(Screen.ExpenseList.route) },
                onNavigateToUsers = { navController.navigate(Screen.UserList.route) },
                onNavigateToPurchases = { navController.navigate(Screen.PurchaseList.route) },
                onNavigateToProductionOrders = { navController.navigate(Screen.ProductionOrderList.route) },
                onNavigateToProductionLots = { navController.navigate(Screen.ProductionLotList.route) },
                onNavigateToProductionShifts = { navController.navigate(Screen.ProductionShiftList.route) },
                onNavigateToEmployees = { navController.navigate(Screen.EmployeeList.route) },
                viewModel = viewModel
            )
        }

        // ── Productos ────────────────────────────────────────────────────────
        composable(Screen.ProductList.route) {
            RequirePermission(state.user, Permission.VIEW_PRODUCTS, onBack = { navController.popBackStack() }) {
                ProductListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.ProductDetail.route(id)) },
                    onNavigateToForm = { navController.navigate(Screen.ProductForm.route()) },
                    authViewModel = viewModel,
                    viewModel = productViewModel
                )
            }
        }
        composable(
            route = Screen.ProductDetail.route,
            arguments = listOf(navArgument("productId") { type = NavType.StringType })
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId") ?: return@composable
            ProductDetailScreen(
                productId = productId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate(Screen.ProductForm.route(id)) },
                authViewModel = viewModel,
                viewModel = productViewModel
            )
        }
        composable(
            route = Screen.ProductForm.route,
            arguments = listOf(navArgument("productId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId")
            RequirePermission(state.user, Permission.EDIT_PRODUCTS, onBack = { navController.popBackStack() }) {
                ProductFormScreen(
                    productId = productId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = productViewModel
                )
            }
        }

        // ── Ingredientes ─────────────────────────────────────────────────────
        composable(Screen.IngredientList.route) {
            RequirePermission(state.user, Permission.VIEW_INGREDIENTS, onBack = { navController.popBackStack() }) {
                IngredientListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.IngredientDetail.route(id)) },
                    onNavigateToForm = { navController.navigate(Screen.IngredientForm.route()) },
                    authViewModel = viewModel,
                    viewModel = ingredientViewModel
                )
            }
        }
        composable(
            route = Screen.IngredientDetail.route,
            arguments = listOf(navArgument("ingredientId") { type = NavType.StringType })
        ) { backStackEntry ->
            val ingredientId = backStackEntry.arguments?.getString("ingredientId") ?: return@composable
            IngredientDetailScreen(
                ingredientId = ingredientId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate(Screen.IngredientForm.route(id)) },
                authViewModel = viewModel,
                viewModel = ingredientViewModel
            )
        }
        composable(
            route = Screen.IngredientForm.route,
            arguments = listOf(navArgument("ingredientId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val ingredientId = backStackEntry.arguments?.getString("ingredientId")
            RequirePermission(state.user, Permission.EDIT_INGREDIENTS, onBack = { navController.popBackStack() }) {
                IngredientFormScreen(
                    ingredientId = ingredientId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSuppliers = { navController.navigate(Screen.SupplierList.route) },
                    authViewModel = viewModel,
                    viewModel = ingredientViewModel,
                    supplierViewModel = supplierViewModel
                )
            }
        }

        // ── Recetas ──────────────────────────────────────────────────────────
        composable(Screen.RecipeList.route) {
            RequirePermission(state.user, Permission.VIEW_RECIPES, onBack = { navController.popBackStack() }) {
                RecipeListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.RecipeDetail.route(id)) },
                    onNavigateToForm = { navController.navigate(Screen.RecipeForm.route()) },
                    authViewModel = viewModel,
                    viewModel = recipeViewModel
                )
            }
        }
        composable(
            route = Screen.RecipeDetail.route,
            arguments = listOf(navArgument("recipeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId") ?: return@composable
            RecipeDetailScreen(
                recipeId = recipeId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate(Screen.RecipeForm.route(id)) },
                authViewModel = viewModel,
                viewModel = recipeViewModel
            )
        }
        composable(
            route = Screen.RecipeForm.route,
            arguments = listOf(navArgument("recipeId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId")
            RequirePermission(state.user, Permission.EDIT_RECIPES, onBack = { navController.popBackStack() }) {
                RecipeFormScreen(
                    recipeId = recipeId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = recipeViewModel,
                    ingredientViewModel = ingredientViewModel,
                    productViewModel = productViewModel
                )
            }
        }

        // ── Proveedores ──────────────────────────────────────────────────────
        composable(Screen.SupplierList.route) {
            RequirePermission(state.user, Permission.VIEW_SUPPLIERS, onBack = { navController.popBackStack() }) {
                SupplierListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.SupplierDetail.route(id)) },
                    onNavigateToForm = { navController.navigate(Screen.SupplierForm.route()) },
                    authViewModel = viewModel,
                    viewModel = supplierViewModel
                )
            }
        }
        composable(
            route = Screen.SupplierDetail.route,
            arguments = listOf(navArgument("supplierId") { type = NavType.StringType })
        ) { backStackEntry ->
            val supplierId = backStackEntry.arguments?.getString("supplierId") ?: return@composable
            SupplierDetailScreen(
                supplierId = supplierId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate(Screen.SupplierForm.route(id)) },
                authViewModel = viewModel,
                viewModel = supplierViewModel
            )
        }
        composable(
            route = Screen.SupplierForm.route,
            arguments = listOf(navArgument("supplierId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val supplierId = backStackEntry.arguments?.getString("supplierId")
            RequirePermission(state.user, Permission.EDIT_SUPPLIERS, onBack = { navController.popBackStack() }) {
                SupplierFormScreen(
                    supplierId = supplierId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = supplierViewModel
                )
            }
        }

        // ── Sucursales ───────────────────────────────────────────────────────
        composable(Screen.BranchList.route) {
            RequirePermission(state.user, Permission.VIEW_ALL_BRANCHES, Permission.MANAGE_BRANCHES, onBack = { navController.popBackStack() }) {
                BranchListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.BranchDetail.route(id)) },
                    onNavigateToForm = { navController.navigate(Screen.BranchForm.route()) },
                    authViewModel = viewModel,
                    viewModel = branchViewModel
                )
            }
        }
        composable(
            route = Screen.BranchDetail.route,
            arguments = listOf(navArgument("branchId") { type = NavType.StringType })
        ) { backStackEntry ->
            val branchId = backStackEntry.arguments?.getString("branchId") ?: return@composable
            BranchDetailScreen(
                branchId = branchId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate(Screen.BranchForm.route(id)) },
                productViewModel = productViewModel,
                authViewModel = viewModel,
                viewModel = branchViewModel
            )
        }
        composable(
            route = Screen.BranchForm.route,
            arguments = listOf(navArgument("branchId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val branchId = backStackEntry.arguments?.getString("branchId")
            RequirePermission(state.user, Permission.MANAGE_BRANCHES, onBack = { navController.popBackStack() }) {
                BranchFormScreen(
                    branchId = branchId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = branchViewModel,
                    productViewModel = productViewModel
                )
            }
        }

        // ── Gastos ───────────────────────────────────────────────────────────
        composable(Screen.ExpenseList.route) {
            RequirePermission(state.user, Permission.VIEW_EXPENSES, Permission.ADD_VARIABLE_EXPENSE, Permission.MANAGE_FIXED_BUDGET, onBack = { navController.popBackStack() }) {
                ExpenseListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.ExpenseDetail.route(id)) },
                    onNavigateToForm = { navController.navigate(Screen.ExpenseForm.route()) },
                    onNavigateToFixedBudget = { navController.navigate(Screen.FixedBudgetList.route) },
                    authViewModel = viewModel,
                    viewModel = expenseViewModel,
                    branchViewModel = branchViewModel,
                    fixedBudgetViewModel = fixedBudgetViewModel
                )
            }
        }
        composable(
            route = Screen.ExpenseDetail.route,
            arguments = listOf(navArgument("expenseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getString("expenseId") ?: return@composable
            ExpenseDetailScreen(
                expenseId = expenseId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate(Screen.ExpenseForm.route(id)) },
                authViewModel = viewModel,
                viewModel = expenseViewModel
            )
        }
        composable(
            route = Screen.ExpenseForm.route,
            arguments = listOf(navArgument("expenseId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getString("expenseId")
            RequirePermission(state.user, Permission.ADD_VARIABLE_EXPENSE, onBack = { navController.popBackStack() }) {
                ExpenseFormScreen(
                    expenseId = expenseId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = expenseViewModel,
                    branchViewModel = branchViewModel
                )
            }
        }

        // ── Costos Fijos ─────────────────────────────────────────────────────
        composable(Screen.FixedBudgetList.route) {
            RequirePermission(state.user, Permission.MANAGE_FIXED_BUDGET, onBack = { navController.popBackStack() }) {
                FixedBudgetListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToForm = { navController.navigate(Screen.FixedBudgetForm.route()) },
                    onNavigateToEdit = { id -> navController.navigate(Screen.FixedBudgetForm.route(id)) },
                    authViewModel = viewModel,
                    viewModel = fixedBudgetViewModel
                )
            }
        }
        composable(
            route = Screen.FixedBudgetForm.route,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")
            RequirePermission(state.user, Permission.MANAGE_FIXED_BUDGET, onBack = { navController.popBackStack() }) {
                FixedBudgetFormScreen(
                    itemId = itemId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = fixedBudgetViewModel,
                    branchViewModel = branchViewModel
                )
            }
        }

        // ── Compras ──────────────────────────────────────────────────────────
        composable(Screen.PurchaseList.route) {
            RequirePermission(state.user, Permission.RECEIVE_STOCK, onBack = { navController.popBackStack() }) {
                PurchaseListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToForm = { navController.navigate(Screen.PurchaseForm.route) },
                    onNavigateToDetail = { id -> navController.navigate(Screen.PurchaseDetail.route(id)) },
                    authViewModel = viewModel,
                    viewModel = purchaseViewModel
                )
            }
        }
        composable(Screen.PurchaseForm.route) {
            RequirePermission(state.user, Permission.RECEIVE_STOCK, onBack = { navController.popBackStack() }) {
                PurchaseFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSuppliers = { navController.navigate(Screen.SupplierList.route) },
                    authViewModel = viewModel,
                    viewModel = purchaseViewModel
                )
            }
        }
        composable(
            route = Screen.PurchaseDetail.route,
            arguments = listOf(navArgument("purchaseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val purchaseId = backStackEntry.arguments?.getString("purchaseId") ?: return@composable
            RequirePermission(state.user, Permission.RECEIVE_STOCK, onBack = { navController.popBackStack() }) {
                PurchaseDetailScreen(
                    purchaseId = purchaseId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = purchaseViewModel
                )
            }
        }

        // ── Producción — Pedidos ─────────────────────────────────────────────
        composable(Screen.ProductionOrderList.route) {
            RequirePermission(state.user, Permission.VIEW_PRODUCTION, Permission.SEND_PRODUCTION_ORDER, Permission.MANAGE_PRODUCTION_LOTS, onBack = { navController.popBackStack() }) {
                ProductionOrderListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.ProductionOrderDetail.route(id)) },
                    onNavigateToForm = { navController.navigate(Screen.ProductionOrderForm.route) },
                    onNavigateToLotForm = { navController.navigate(Screen.ProductionLotForm.route) },
                    authViewModel = viewModel,
                    viewModel = productionViewModel
                )
            }
        }
        composable(Screen.ProductionOrderForm.route) {
            RequirePermission(state.user, Permission.SEND_PRODUCTION_ORDER, onBack = { navController.popBackStack() }) {
                ProductionOrderFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = productionViewModel,
                    productViewModel = productViewModel
                )
            }
        }
        composable(
            route = Screen.ProductionOrderDetail.route,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId") ?: return@composable
            RequirePermission(state.user, Permission.VIEW_PRODUCTION, Permission.SEND_PRODUCTION_ORDER, Permission.MANAGE_PRODUCTION_LOTS, onBack = { navController.popBackStack() }) {
                ProductionOrderDetailScreen(
                    orderId = orderId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = productionViewModel
                )
            }
        }

        // ── Producción — Lotes ───────────────────────────────────────────────
        composable(Screen.ProductionLotList.route) {
            RequirePermission(state.user, Permission.MANAGE_PRODUCTION_LOTS, Permission.VIEW_PRODUCTION, onBack = { navController.popBackStack() }) {
                ProductionLotListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.ProductionLotDetail.route(id)) },
                    onNavigateToForm = { navController.navigate(Screen.ProductionLotForm.route) },
                    authViewModel = viewModel,
                    viewModel = productionViewModel
                )
            }
        }
        composable(Screen.ProductionLotForm.route) {
            RequirePermission(state.user, Permission.MANAGE_PRODUCTION_LOTS, onBack = { navController.popBackStack() }) {
                ProductionLotFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = productionViewModel,
                    productViewModel = productViewModel
                )
            }
        }
        composable(
            route = Screen.ProductionLotDetail.route,
            arguments = listOf(navArgument("lotId") { type = NavType.StringType })
        ) { backStackEntry ->
            val lotId = backStackEntry.arguments?.getString("lotId") ?: return@composable
            RequirePermission(state.user, Permission.MANAGE_PRODUCTION_LOTS, Permission.VIEW_PRODUCTION, onBack = { navController.popBackStack() }) {
                ProductionLotDetailScreen(
                    lotId = lotId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToComplete = { id -> navController.navigate(Screen.ProductionLotComplete.route(id)) },
                    authViewModel = viewModel,
                    viewModel = productionViewModel
                )
            }
        }
        composable(
            route = Screen.ProductionLotComplete.route,
            arguments = listOf(navArgument("lotId") { type = NavType.StringType })
        ) { backStackEntry ->
            val lotId = backStackEntry.arguments?.getString("lotId") ?: return@composable
            RequirePermission(state.user, Permission.MANAGE_PRODUCTION_LOTS, onBack = { navController.popBackStack() }) {
                ProductionLotCompleteScreen(
                    lotId = lotId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = productionViewModel
                )
            }
        }

        // ── Colaboradores ─────────────────────────────────────────────────────
        composable(Screen.EmployeeList.route) {
            RequirePermission(state.user, Permission.MANAGE_EMPLOYEES, onBack = { navController.popBackStack() }) {
                EmployeeListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.EmployeeDetail.route(id)) },
                    onNavigateToForm = { id -> navController.navigate(Screen.EmployeeForm.route(id)) },
                    authViewModel = viewModel,
                    viewModel = employeeViewModel
                )
            }
        }
        composable(
            route = Screen.EmployeeDetail.route,
            arguments = listOf(navArgument("employeeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val employeeId = backStackEntry.arguments?.getString("employeeId") ?: return@composable
            RequirePermission(state.user, Permission.MANAGE_EMPLOYEES, onBack = { navController.popBackStack() }) {
                EmployeeDetailScreen(
                    employeeId = employeeId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToForm = { id -> navController.navigate(Screen.EmployeeForm.route(id)) },
                    authViewModel = viewModel,
                    viewModel = employeeViewModel
                )
            }
        }
        composable(
            route = Screen.EmployeeForm.route,
            arguments = listOf(
                navArgument("employeeId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val employeeId = backStackEntry.arguments?.getString("employeeId")
            RequirePermission(state.user, Permission.MANAGE_EMPLOYEES, onBack = { navController.popBackStack() }) {
                EmployeeFormScreen(
                    employeeId = employeeId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = employeeViewModel
                )
            }
        }

        // ── Producción — Turnos ──────────────────────────────────────────────
        composable(Screen.ProductionShiftList.route) {
            RequirePermission(state.user, Permission.MANAGE_PRODUCTION_SHIFTS, onBack = { navController.popBackStack() }) {
                ProductionShiftListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToForm = { branchId, shiftId ->
                        navController.navigate(Screen.ProductionShiftForm.route(branchId, shiftId))
                    },
                    authViewModel = viewModel,
                    viewModel = productionViewModel
                )
            }
        }
        composable(
            route = Screen.ProductionShiftForm.route,
            arguments = listOf(
                navArgument("branchId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("shiftId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val branchId = backStackEntry.arguments?.getString("branchId")
            val shiftId = backStackEntry.arguments?.getString("shiftId")
            RequirePermission(state.user, Permission.MANAGE_PRODUCTION_SHIFTS, onBack = { navController.popBackStack() }) {
                ProductionShiftFormScreen(
                    branchId = branchId,
                    shiftId = shiftId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = productionViewModel
                )
            }
        }

        // ── Usuarios ─────────────────────────────────────────────────────────
        composable(Screen.UserList.route) {
            RequirePermission(state.user, Permission.MANAGE_USERS, onBack = { navController.popBackStack() }) {
                UserListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPermissions = { id -> navController.navigate(Screen.UserPermissions.route(id)) },
                    authViewModel = viewModel,
                    viewModel = userViewModel
                )
            }
        }
        composable(
            route = Screen.UserPermissions.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            UserPermissionsScreen(
                userId = userId,
                onNavigateBack = { navController.popBackStack() },
                authViewModel = viewModel,
                viewModel = userViewModel,
                branchViewModel = branchViewModel
            )
        }
    }
}

@Composable
private fun RequirePermission(
    user: User?,
    vararg permissions: Permission,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    when {
        user == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        permissions.any { user.hasPermission(it) } -> content()
        else -> NoAccessView(onBack)
    }
}

@Composable
private fun NoAccessView(onBack: () -> Unit) {
    Scaffold { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Text(
                    "Sin acceso",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "No tienes permiso para ver este módulo.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onBack) { Text("Volver") }
            }
        }
    }
}
