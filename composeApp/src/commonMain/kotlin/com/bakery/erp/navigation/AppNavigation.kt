package com.bakery.erp.navigation

import com.bakery.erp.presentation.credit.CreditAccountDetailScreen
import com.bakery.erp.presentation.credit.CreditAccountFormScreen
import com.bakery.erp.presentation.credit.CreditAccountListScreen
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
import com.bakery.erp.model.NotificationTarget
import com.bakery.erp.model.Permission
import com.bakery.erp.model.User
import com.bakery.erp.model.hasPermission
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bakery.erp.presentation.assets.AssetHistoryEntryFormScreen
import com.bakery.erp.presentation.assets.FixedAssetDetailScreen
import com.bakery.erp.presentation.assets.FixedAssetFormScreen
import com.bakery.erp.presentation.assets.FixedAssetListScreen
import com.bakery.erp.presentation.auth.AuthChoiceScreen
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.auth.JoinOrgScreen
import com.bakery.erp.presentation.auth.LoginScreen
import com.bakery.erp.presentation.auth.PendingApprovalScreen
import com.bakery.erp.presentation.auth.RegisterScreen
import com.bakery.erp.presentation.auth.ResellerSetupScreen
import com.bakery.erp.presentation.reports.financial.FinancialReportScreen
import com.bakery.erp.presentation.reports.production.ProductionReportScreen
import com.bakery.erp.presentation.reports.sales.SalesReportScreen
import com.bakery.erp.presentation.reports.branchsupply.BranchSupplyReportScreen
import com.bakery.erp.presentation.customerOrders.CustomerOrderDetailScreen
import com.bakery.erp.presentation.customerOrders.CustomerOrderFormScreen
import com.bakery.erp.presentation.customerOrders.CustomerOrderListScreen
import com.bakery.erp.presentation.customerOrders.ExternalCustomerFormScreen
import com.bakery.erp.presentation.customerOrders.ExternalCustomerListScreen
import com.bakery.erp.presentation.customerOrders.ResellerApprovalScreen
import com.bakery.erp.presentation.organization.OrganizationSettingsScreen
import com.bakery.erp.presentation.fel.FelProfileFormScreen
import com.bakery.erp.presentation.fel.FelSetupScreen
import com.bakery.erp.presentation.reseller.ResellerHomeScreen
import com.bakery.erp.presentation.splash.SplashScreen
import com.bakery.erp.presentation.reseller.ResellerOrderDetailScreen
import com.bakery.erp.presentation.reseller.ResellerQuickOrderScreen
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
import com.bakery.erp.presentation.products.ProductWeightsScreen
import com.bakery.erp.presentation.preparationbatches.PreparationBatchFormScreen
import com.bakery.erp.presentation.preparationbatches.PreparationBatchListScreen
import com.bakery.erp.presentation.preparations.PreparationDetailScreen
import com.bakery.erp.presentation.preparations.PreparationFormScreen
import com.bakery.erp.presentation.preparations.PreparationListScreen
import com.bakery.erp.presentation.pricing.PriceListAdminScreen
import com.bakery.erp.presentation.pricing.PriceListViewModel
import com.bakery.erp.presentation.dispatch.DispatchNoteDetailScreen
import com.bakery.erp.presentation.dispatch.DispatchNoteListScreen
import com.bakery.erp.presentation.dispatch.DispatchNotePackScreen
import com.bakery.erp.presentation.dispatch.DispatchNoteReceiveScreen
import com.bakery.erp.presentation.dispatch.DispatchViewModel
import com.bakery.erp.presentation.delivery.DeliveryHistoryScreen
import com.bakery.erp.presentation.delivery.DeliveryRunFormScreen
import com.bakery.erp.presentation.delivery.DeliveryBoardScreen
import com.bakery.erp.presentation.delivery.DeliveryRunScreen
import com.bakery.erp.presentation.delivery.DeliveryViewModel
import com.bakery.erp.presentation.finishedStock.BreadCountFormScreen
import com.bakery.erp.presentation.finishedStock.BreadCountListScreen
import com.bakery.erp.presentation.finishedStock.FinishedProductStockListScreen
import com.bakery.erp.presentation.finishedStock.FinishedStockAdjustScreen
import com.bakery.erp.presentation.finishedStock.FinishedStockMovementListScreen
import com.bakery.erp.presentation.finishedStock.FinishedStockViewModel
import com.bakery.erp.presentation.pos.CashSessionCloseScreen
import com.bakery.erp.presentation.pos.CashSessionDetailScreen
import com.bakery.erp.presentation.pos.CashSessionOpenScreen
import com.bakery.erp.presentation.pos.DailyCashCloseListScreen
import com.bakery.erp.presentation.pos.DailyCashCloseScreen
import com.bakery.erp.presentation.pos.POSHomeScreen
import com.bakery.erp.presentation.pos.POSScreen
import com.bakery.erp.presentation.pos.SaleDetailScreen
import com.bakery.erp.presentation.pos.SaleHistoryListScreen
import com.bakery.erp.presentation.pos.SpotCashCheckListScreen
import com.bakery.erp.presentation.pos.SpotCashCheckScreen
import com.bakery.erp.presentation.pos.admin.CashRegisterAdminScreen
import com.bakery.erp.presentation.pos.admin.CashRegisterFormScreen
import com.bakery.erp.presentation.pos.admin.POSCategoryAdminScreen
import com.bakery.erp.presentation.pos.admin.POSCategoryFormScreen
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
import com.bakery.erp.presentation.plantissues.PlantIssueReportScreen
import com.bakery.erp.presentation.production.ProductionLotDetailScreen
import com.bakery.erp.presentation.production.ProductionLotFormScreen
import com.bakery.erp.presentation.production.ProductionShiftSplitScreen
import com.bakery.erp.presentation.production.ProductionLotListScreen
import com.bakery.erp.presentation.production.ProductionOrderDetailScreen
import com.bakery.erp.presentation.production.ProductionOrderFormScreen
import com.bakery.erp.presentation.production.ProductionOrderListScreen
import com.bakery.erp.presentation.employees.EmployeeDetailScreen
import com.bakery.erp.presentation.employees.EmployeeFormScreen
import com.bakery.erp.presentation.employees.EmployeeListScreen
import com.bakery.erp.presentation.employees.EmployeeViewModel
import com.bakery.erp.presentation.employees.JobPositionDetailScreen
import com.bakery.erp.presentation.employees.JobPositionFormScreen
import com.bakery.erp.presentation.employees.JobPositionListScreen
import com.bakery.erp.presentation.employees.JobPositionViewModel
import com.bakery.erp.presentation.employees.PayrollFormScreen
import com.bakery.erp.presentation.employees.ShiftClockInScreen
import com.bakery.erp.presentation.production.ProductionShiftFormScreen
import com.bakery.erp.presentation.production.ProductionShiftListScreen
import com.bakery.erp.presentation.production.ProductionViewModel
import com.bakery.erp.presentation.purchases.PurchaseDetailScreen
import com.bakery.erp.presentation.purchases.QuickPurchaseFormScreen
import com.bakery.erp.presentation.purchases.PurchaseFormScreen
import com.bakery.erp.presentation.purchases.PurchaseListScreen
import com.bakery.erp.presentation.purchases.PurchaseViewModel
import com.bakery.erp.presentation.suppliers.SupplierDetailScreen
import com.bakery.erp.presentation.suppliers.SupplierFormScreen
import com.bakery.erp.presentation.suppliers.SupplierListScreen
import com.bakery.erp.presentation.suppliers.SupplierViewModel
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import com.bakery.erp.di.AppViewModels
import com.bakery.erp.di.createAppViewModels

private fun NavBackStackEntry.stringArg(key: String): String? =
    savedStateHandle.get<String>(key)

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object AuthChoice : Screen("auth_choice")
    object Register : Screen("register")
    object JoinOrg : Screen("join_org")
    object Home : Screen("home")
    object ProductList : Screen("products")
    object ProductWeights : Screen("products/weights")
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
    /**
     * Anotar un gasto o una compra rápida. Es el ÚNICO destino de captura: lo abren el acceso
     * de Operaciones, el FAB de Gastos y la caja abierta del POS.
     *
     * La ruta conserva el nombre `expenses/quick` a propósito, aunque la pantalla que renderiza
     * viva en `presentation/purchases`: es la puerta de "anotar un gasto" desde el punto de
     * vista del usuario, y el hecho de que por debajo escriba un `Purchase` cuando el renglón
     * entra a inventario es un detalle de implementación.
     */
    object CashExpenseForm : Screen("expenses/quick?sessionId={sessionId}") {
        /** Sin caja = gasto general. Con caja = se descuenta de esa gaveta al cuadrarla. */
        fun route(sessionId: String? = null) =
            if (sessionId.isNullOrEmpty()) "expenses/quick?sessionId="
            else "expenses/quick?sessionId=$sessionId"
    }
    object PurchaseList : Screen("purchases")
    /**
     * Alta de una factura de compra. Con `sessionId` es la misma captura pero **pagada de esa
     * gaveta**: el proveedor que llegó a la puerta y se le pagó del cajón. Va como query param
     * y no como segmento para no chocar con `PurchaseEdit` (`purchases/form/{purchaseId}`).
     */
    object PurchaseForm : Screen("purchases/form?sessionId={sessionId}") {
        fun route(sessionId: String? = null) =
            if (sessionId.isNullOrEmpty()) "purchases/form?sessionId="
            else "purchases/form?sessionId=$sessionId"
    }
    object QuickPurchaseEdit : Screen("purchases/quick/{purchaseId}") {
        fun route(purchaseId: String) = "purchases/quick/$purchaseId"
    }
    object PurchaseEdit : Screen("purchases/form/{purchaseId}") {
        fun route(purchaseId: String) = "purchases/form/$purchaseId"
    }
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
    object JobPositionList : Screen("employees/positions")
    object JobPositionDetail : Screen("employees/positions/detail/{positionId}") {
        fun route(positionId: String) = "employees/positions/detail/$positionId"
    }
    object JobPositionForm : Screen("employees/positions/form?positionId={positionId}") {
        fun route(positionId: String? = null) =
            if (positionId != null) "employees/positions/form?positionId=$positionId" else "employees/positions/form"
    }
    object PayrollForm : Screen("employees/payroll/form?employeeId={employeeId}&payrollId={payrollId}") {
        fun route(employeeId: String, payrollId: String? = null) =
            if (payrollId != null) "employees/payroll/form?employeeId=$employeeId&payrollId=$payrollId"
            else "employees/payroll/form?employeeId=$employeeId"
    }
    object ShiftClockIn : Screen("shift_clock_in")

    // Producción
    object ProductionOrderList : Screen("production/orders")
    object ProductionOrderForm : Screen("production/orders/form?orderId={orderId}") {
        fun route(orderId: String? = null) =
            if (orderId != null) "production/orders/form?orderId=$orderId" else "production/orders/form"
    }
    object ProductionOrderDetail : Screen("production/orders/detail/{orderId}") {
        fun route(orderId: String) = "production/orders/detail/$orderId"
    }
    object ProductionLotList : Screen("production/lots")
    object ProductionLotForm : Screen("production/lots/form?branchId={branchId}&shiftId={shiftId}&date={date}") {
        fun route(branchId: String? = null, shiftId: String? = null, date: Long? = null): String {
            val params = buildList {
                if (!branchId.isNullOrEmpty()) add("branchId=$branchId")
                if (!shiftId.isNullOrEmpty()) add("shiftId=$shiftId")
                if (date != null) add("date=$date")
            }
            return if (params.isEmpty()) "production/lots/form"
                   else "production/lots/form?${params.joinToString("&")}"
        }
    }
    /**
     * Consumo de planta: material que salió de bodega sin pasar por una receta
     * (ver `PlantIssue`). Es a la vez el reporte y el historial de vales.
     */
    object PlantIssueReport : Screen("production/plant-issues")
    object ProductionLotDetail : Screen("production/lots/detail/{lotId}") {
        fun route(lotId: String) = "production/lots/detail/$lotId"
    }
    object ProductionLotComplete : Screen("production/lots/complete/{lotId}") {
        fun route(lotId: String) = "production/lots/complete/$lotId"
    }
    object ProductionShiftSplit : Screen("production/lots/split?branchId={branchId}&date={date}") {
        fun route(branchId: String, date: Long) = "production/lots/split?branchId=$branchId&date=$date"
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

    // Activos Fijos
    object AssetList : Screen("assets")
    object AssetDetail : Screen("assets/detail/{assetId}") {
        fun route(assetId: String) = "assets/detail/$assetId"
    }
    object AssetForm : Screen("assets/form?assetId={assetId}") {
        fun route(assetId: String? = null) =
            if (assetId != null) "assets/form?assetId=$assetId" else "assets/form"
    }
    object AssetHistoryEntryForm : Screen("assets/{assetId}/history/form?entryId={entryId}") {
        fun route(assetId: String, entryId: String? = null) =
            if (entryId != null) "assets/$assetId/history/form?entryId=$entryId"
            else "assets/$assetId/history/form"
    }

    // Preparaciones
    object PreparationList : Screen("preparations")
    object PreparationDetail : Screen("preparations/detail/{preparationId}") {
        fun route(preparationId: String) = "preparations/detail/$preparationId"
    }
    object PreparationForm : Screen("preparations/form?preparationId={preparationId}") {
        fun route(preparationId: String? = null) =
            if (preparationId != null) "preparations/form?preparationId=$preparationId" else "preparations/form"
    }
    object PreparationBatchList : Screen("preparations/batches")
    object PreparationBatchForm : Screen("preparations/batches/form")

    // Precios
    object PriceListAdmin : Screen("prices/lists")

    // Distribución
    object DispatchList : Screen("dispatch")
    object DispatchPack : Screen("dispatch/pack/{noteId}") {
        fun route(noteId: String) = "dispatch/pack/$noteId"
    }
    object DispatchReceive : Screen("dispatch/receive/{noteId}") {
        fun route(noteId: String) = "dispatch/receive/$noteId"
    }
    object DispatchDetail : Screen("dispatch/detail/{noteId}") {
        fun route(noteId: String) = "dispatch/detail/$noteId"
    }
    object DeliveryList : Screen("delivery")
    object DeliveryForm : Screen("delivery/form?runId={runId}") {
        fun route(runId: String? = null) =
            if (runId != null) "delivery/form?runId=$runId" else "delivery/form"
    }
    object DeliveryRunDetail : Screen("delivery/run/{runId}") {
        fun route(runId: String) = "delivery/run/$runId"
    }
    object DeliveryHistory : Screen("delivery/history")

    // Punto de venta (POS)
    object POSHome : Screen("pos")
    object POSScreen : Screen("pos/session/{sessionId}") {
        fun route(sessionId: String) = "pos/session/$sessionId"
    }
    object CashSessionOpen : Screen("pos/open/{registerId}") {
        fun route(registerId: String) = "pos/open/$registerId"
    }
    object CashSessionClose : Screen("pos/close/{sessionId}") {
        fun route(sessionId: String) = "pos/close/$sessionId"
    }
    object CashSessionDetail : Screen("pos/session/{sessionId}/detail") {
        fun route(sessionId: String) = "pos/session/$sessionId/detail"
    }
    object SaleHistoryList : Screen("sales")
    object SaleDetail : Screen("sales/{saleId}") {
        fun route(saleId: String) = "sales/$saleId"
    }
    object POSCategoryAdminList : Screen("admin/posCategories")
    // Créditos
    object CreditAccountList : Screen("credit/accounts")
    object CreditAgingReport : Screen("credit/aging")
    object CreditAccountForm : Screen("credit/accounts/form?id={id}") {
        fun route(id: String? = null) =
            if (id != null) "credit/accounts/form?id=$id" else "credit/accounts/form"
    }
    object CreditAccountDetail : Screen("credit/accounts/{accountId}") {
        fun route(accountId: String) = "credit/accounts/$accountId"
    }
    object POSCategoryForm : Screen("admin/posCategories/form?id={id}") {
        fun route(id: String? = null) =
            if (id != null) "admin/posCategories/form?id=$id" else "admin/posCategories/form"
    }
    object CashRegisterAdminList : Screen("admin/cashRegisters")
    object CashRegisterForm : Screen("admin/cashRegisters/form?id={id}") {
        fun route(id: String? = null) =
            if (id != null) "admin/cashRegisters/form?id=$id" else "admin/cashRegisters/form"
    }

    // Inventario de tienda
    object FinishedStockList : Screen("finishedStock")
    object FinishedStockMovements : Screen("finishedStock/movements/{branchId}/{productId}") {
        fun route(branchId: String, productId: String) = "finishedStock/movements/$branchId/$productId"
    }
    object FinishedStockAdjust : Screen("finishedStock/adjust/{branchId}?productId={productId}") {
        fun route(branchId: String, productId: String? = null) =
            if (productId != null) "finishedStock/adjust/$branchId?productId=$productId"
            else "finishedStock/adjust/$branchId"
    }

    // Conteo de pan (cierre del día)
    object BreadCountList : Screen("breadCount")
    object BreadCountForm : Screen("breadCount/form/{countId}") {
        fun route(countId: String) = "breadCount/form/$countId"
    }

    // Cierre del día por sucursal
    object DailyCashCloseList : Screen("dailyCashClose")
    object DailyCashClose : Screen("dailyCashClose/form?branchId={branchId}&date={date}") {
        fun route(branchId: String? = null, date: String? = null): String {
            val params = buildList {
                if (!branchId.isNullOrEmpty()) add("branchId=$branchId")
                if (!date.isNullOrEmpty()) add("date=$date")
            }
            return if (params.isEmpty()) "dailyCashClose/form"
                   else "dailyCashClose/form?${params.joinToString("&")}"
        }
    }

    // Cuadre sorpresa
    object SpotCashCheckList : Screen("spotCheck")
    object SpotCashCheck : Screen("spotCheck/session/{sessionId}") {
        fun route(sessionId: String) = "spotCheck/session/$sessionId"
    }

    // Reportes
    object SalesReport      : Screen("reports/sales")
    object ProductionReport : Screen("reports/production")
    object FinancialReport  : Screen("reports/financial")
    object BranchSupplyReport : Screen("reports/branch-supply")

    // Pedidos de clientes
    object CustomerOrderList : Screen("customerOrders")
    object CustomerOrderForm : Screen("customerOrders/form?orderId={orderId}") {
        fun route(orderId: String?) =
            if (orderId.isNullOrEmpty()) "customerOrders/form"
            else "customerOrders/form?orderId=$orderId"
    }
    object CustomerOrderDetail : Screen("customerOrders/detail/{orderId}") {
        fun route(orderId: String) = "customerOrders/detail/$orderId"
    }
    object ExternalCustomerList : Screen("externalCustomers")
    object ExternalCustomerForm : Screen("externalCustomers/form?customerId={customerId}") {
        fun route(customerId: String?) =
            if (customerId.isNullOrEmpty()) "externalCustomers/form"
            else "externalCustomers/form?customerId=$customerId"
    }
    object OrganizationSettings : Screen("organization/settings")
    object FelSetup : Screen("fel/setup")
    object CashAudit : Screen("cash/audit?branchId={branchId}") {
        fun route(branchId: String?) = if (branchId.isNullOrEmpty()) "cash/audit" else "cash/audit?branchId=$branchId"
    }
    object FelProfileForm : Screen("fel/profile?profileId={profileId}") {
        fun route(profileId: String?) =
            if (profileId.isNullOrEmpty()) "fel/profile" else "fel/profile?profileId=$profileId"
    }
    /** Pantalla post-signup donde el revendedor configura sucursal, modalidad y hora. */
    object ResellerSetup : Screen("auth/reseller_setup")
    /** Pantalla donde la encargada aprueba revendedores pendientes y asigna planta+turno. */
    object ResellerApproval : Screen("resellers/approval")
    /** Landing del revendedor tras login: perfil + botón pedido + pedidos recientes. */
    object ResellerHome : Screen("reseller/home")
    /** Pantalla del pedido diario del revendedor: lista de productos con cantidad.
     *  Acepta editDate+editTime para modo edición de un slot existente. */
    object ResellerQuickOrder : Screen("reseller/quickOrder?editDate={editDate}&editTime={editTime}") {
        fun route(editDate: Long? = null, editTime: String? = null) =
            if (editDate == null) "reseller/quickOrder"
            else "reseller/quickOrder?editDate=$editDate&editTime=${editTime ?: ""}"
    }
    object ResellerOrderDetail : Screen("reseller/order/{orderId}") {
        fun route(orderId: String) = "reseller/order/$orderId"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    vms: AppViewModels = remember { createAppViewModels() }
) {
    val viewModel = vms.auth
    val productViewModel = vms.product
    val ingredientViewModel = vms.ingredient
    val supplierViewModel = vms.supplier
    val recipeViewModel = vms.recipe
    val branchViewModel = vms.branch
    val expenseViewModel = vms.expense
    val fixedBudgetViewModel = vms.fixedBudget
    val userViewModel = vms.user
    val purchaseViewModel = vms.purchase
    val productionViewModel = vms.production
    val plantIssueViewModel = vms.plantIssue
    val employeeViewModel = vms.employee
    val jobPositionViewModel = vms.jobPosition
    val preparationViewModel = vms.preparation
    val preparationBatchViewModel = vms.preparationBatch
    val fixedAssetViewModel = vms.fixedAsset
    val priceListViewModel = vms.priceList
    val dispatchViewModel = vms.dispatch
    val deliveryViewModel = vms.delivery
    val finishedStockViewModel = vms.finishedStock
    val posViewModel = vms.pos
    val cashSessionViewModel = vms.cashSession
    val saleHistoryViewModel = vms.saleHistory
    val cashRegisterAdminViewModel = vms.cashRegisterAdmin
    val posCategoryAdminViewModel = vms.posCategoryAdmin
    val creditViewModel = vms.credit
    val breadCountViewModel = vms.breadCount
    val dailyCashCloseViewModel = vms.dailyCashClose
    val spotCashCheckViewModel = vms.spotCashCheck
    val homeViewModel = vms.home
    val notificationViewModel = vms.notification
    val salesReportViewModel = vms.salesReport
    val productionReportViewModel = vms.productionReport
    val financialReportViewModel = vms.financialReport
    val branchSupplyReportViewModel = vms.branchSupplyReport
    val customerOrderViewModel = vms.customerOrder
    val externalCustomerViewModel = vms.externalCustomer
    val organizationSettingsViewModel = vms.organizationSettings
    val felViewModel = vms.fel
    val cashAuditViewModel = vms.cashAudit
    val bluetoothPrinterManager = vms.bluetoothPrinterManager
    val documentPrinter = vms.documentPrinter

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

    /** Home destino según el rol del usuario autenticado. */
    fun authenticatedHomeRoute() =
        if (state.user?.role == com.bakery.erp.model.UserRole.RESELLER)
            Screen.ResellerHome.route
        else Screen.Home.route

    LaunchedEffect(
        state.isAuthenticated,
        state.isLoading,
        state.needsOnboarding,
        state.isPendingApproval,
        state.needsResellerSetup
    ) {
        if (state.isLoading) return@LaunchedEffect
        if (!initialNavigationDone) {
            initialNavigationDone = true
            when {
                state.needsResellerSetup -> navController.navigate(Screen.ResellerSetup.route) { popUpTo(0) { inclusive = true } }
                state.isAuthenticated -> navController.navigate(authenticatedHomeRoute()) { popUpTo(0) { inclusive = true } }
                state.isPendingApproval -> navController.navigate(Screen.PendingApproval.route) { popUpTo(0) { inclusive = true } }
                // Usuario autenticado (p.ej. Google) pero sin organización → pantalla de
                // elección: crear nueva panadería o unirse a una existente.
                state.needsOnboarding -> navController.navigate(Screen.AuthChoice.route) { popUpTo(0) { inclusive = true } }
                else -> navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
            }
        } else if (state.needsResellerSetup) {
            // Transición JoinOrg → ResellerSetup tras signupAsReseller exitoso.
            navController.navigate(Screen.ResellerSetup.route) { popUpTo(0) { inclusive = true } }
        } else if (state.isAuthenticated) {
            // Transición de aprobación: revendedor que estaba en PendingApproval
            // ahora ve isAuthenticated=true tras refresh — enviarlo a su home.
            navController.navigate(authenticatedHomeRoute()) { popUpTo(0) { inclusive = true } }
        } else if (state.isPendingApproval && !state.isAuthenticated) {
            // Transición ResellerSetup → PendingApproval tras completar perfil.
            navController.navigate(Screen.PendingApproval.route) { popUpTo(0) { inclusive = true } }
        } else if (state.needsOnboarding) {
            // Google Sign-In de un usuario NO registrado: authState/onSuccess dispara
            // needsOnboarding después de la navegación inicial. Sin esta rama el usuario
            // se quedaba varado en el Login. Lo llevamos a la pantalla de elección.
            navController.navigate(Screen.AuthChoice.route) { popUpTo(0) { inclusive = true } }
        } else if (!state.isAuthenticated && !state.needsOnboarding && !state.isPendingApproval) {
            // Sesión cerrada mientras la app estaba abierta
            initialNavigationDone = false
            navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
        }
    }

    /**
     * Resuelve el destino simbólico de un aviso a una ruta real. Vive acá y no en
     * `model/` para no acoplar el modelo con la navegación (mismo criterio que
     * `AlertTarget`). Lo usan tanto la campana in-app como el tap sobre el push.
     */
    fun navigateToNotificationTarget(targetScreen: String, targetId: String) {
        when (targetScreen) {
            NotificationTarget.PRODUCTION_ORDER_DETAIL ->
                if (targetId.isNotEmpty())
                    navController.navigate(Screen.ProductionOrderDetail.route(targetId))
                else navController.navigate(Screen.ProductionOrderList.route)
            NotificationTarget.PRODUCTION_ORDER_LIST ->
                navController.navigate(Screen.ProductionOrderList.route)
            NotificationTarget.PRODUCTION_LOT_DETAIL ->
                if (targetId.isNotEmpty())
                    navController.navigate(Screen.ProductionLotDetail.route(targetId))
            NotificationTarget.DISPATCH_NOTE_DETAIL ->
                if (targetId.isNotEmpty())
                    navController.navigate(Screen.DispatchDetail.route(targetId))
                else navController.navigate(Screen.DeliveryList.route)
            NotificationTarget.CASH_AUDIT ->
                navController.navigate(Screen.CashAudit.route(targetId))
            else -> Unit
        }
    }

    // Tap sobre un push del sistema. Se espera a que haya sesión y a que la
    // navegación inicial haya corrido: si se navegara antes, el redirect al home
    // que hace el efecto de arriba (con popUpTo(0)) se llevaría la pantalla por
    // delante y el usuario terminaría en el home sin entender por qué.
    val pendingDeepLink by PendingDeepLink.target.collectAsState()
    LaunchedEffect(pendingDeepLink, state.isAuthenticated, initialNavigationDone) {
        val target = pendingDeepLink ?: return@LaunchedEffect
        if (!state.isAuthenticated || !initialNavigationDone) return@LaunchedEffect
        navigateToNotificationTarget(target.targetScreen, target.targetId)
        PendingDeepLink.consume()
    }

    var showSplash by rememberSaveable { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
    NavHost(navController = navController, startDestination = Screen.Login.route) {

        composable(Screen.PendingApproval.route) {
            PendingApprovalScreen(viewModel = viewModel)
        }
        composable(Screen.ResellerSetup.route) {
            ResellerSetupScreen(
                authViewModel = viewModel,
                setupViewModel = vms.resellerSetup
            )
        }
        composable(Screen.ResellerHome.route) {
            ResellerHomeScreen(
                onNavigateToQuickOrder = { navController.navigate(Screen.ResellerQuickOrder.route()) },
                onNavigateToOrderDetail = { id ->
                    navController.navigate(Screen.ResellerOrderDetail.route(id))
                },
                authViewModel = viewModel,
                viewModel = vms.reseller
            )
        }
        composable(
            route = Screen.ResellerQuickOrder.route,
            arguments = listOf(
                navArgument("editDate") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
                navArgument("editTime") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                }
            )
        ) { backStackEntry ->
            val editDateStr = backStackEntry.savedStateHandle.get<String>("editDate")
            val editTime = backStackEntry.savedStateHandle.get<String>("editTime")
            ResellerQuickOrderScreen(
                onNavigateBack = { navController.popBackStack() },
                authViewModel = viewModel,
                viewModel = vms.reseller,
                editDateMillis = editDateStr?.toLongOrNull(),
                editTime = editTime?.takeIf { it.isNotEmpty() }
            )
        }
        composable(
            route = Screen.ResellerOrderDetail.route,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orderId = backStackEntry.savedStateHandle.get<String>("orderId") ?: ""
            ResellerOrderDetailScreen(
                orderId = orderId,
                onNavigateBack = { navController.popBackStack() },
                onEditOrder = { date, time ->
                    navController.navigate(Screen.ResellerQuickOrder.route(date, time))
                },
                authViewModel = viewModel,
                viewModel = vms.reseller
            )
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
                onNavigateToLogin = { navController.popBackStack(Screen.Login.route, inclusive = false) },
                viewModel = viewModel
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = { navController.popBackStack(Screen.Login.route, inclusive = false) },
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
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
                onNavigateToFixedBudget = { navController.navigate(Screen.FixedBudgetList.route) },
                onNavigateToQuickPurchase = { navController.navigate(Screen.CashExpenseForm.route()) },
                onNavigateToPlantIssues = { navController.navigate(Screen.PlantIssueReport.route) },
                onNavigateToUsers = { navController.navigate(Screen.UserList.route) },
                onNavigateToPurchases = { navController.navigate(Screen.PurchaseList.route) },
                onNavigateToProductionOrders = { navController.navigate(Screen.ProductionOrderList.route) },
                onNavigateToProductionLots = { navController.navigate(Screen.ProductionLotList.route) },
                onNavigateToProductionShifts = { navController.navigate(Screen.ProductionShiftList.route) },
                onNavigateToEmployees = { navController.navigate(Screen.EmployeeList.route) },
                onNavigateToShiftClockIn = { navController.navigate(Screen.ShiftClockIn.route) },
                onNavigateToJobPositions = { navController.navigate(Screen.JobPositionList.route) },
                onNavigateToPreparations = { navController.navigate(Screen.PreparationList.route) },
                onNavigateToPreparationBatches = { navController.navigate(Screen.PreparationBatchList.route) },
                onNavigateToAssets = { navController.navigate(Screen.AssetList.route) },
                onNavigateToPriceLists = { navController.navigate(Screen.PriceListAdmin.route) },
                onNavigateToDispatch = { navController.navigate(Screen.DispatchList.route) },
                onNavigateToDelivery = { navController.navigate(Screen.DeliveryList.route) },
                onNavigateToFinishedStock = { navController.navigate(Screen.FinishedStockList.route) },
                onNavigateToPOS = { navController.navigate(Screen.POSHome.route) },
                onNavigateToSalesHistory = { navController.navigate(Screen.SaleHistoryList.route) },
                onNavigateToCashRegisterAdmin = { navController.navigate(Screen.CashRegisterAdminList.route) },
                onNavigateToQuickGroupAdmin = { navController.navigate(Screen.POSCategoryAdminList.route) },
                onNavigateToCredit = { navController.navigate(Screen.CreditAccountList.route) },
                onNavigateToBreadCount = { navController.navigate(Screen.BreadCountList.route) },
                onNavigateToDailyCashClose = { navController.navigate(Screen.DailyCashCloseList.route) },
                onNavigateToSpotCashCheck = { navController.navigate(Screen.SpotCashCheckList.route) },
                onNavigateToSalesReport = { navController.navigate(Screen.SalesReport.route) },
                onNavigateToProductionReport = { navController.navigate(Screen.ProductionReport.route) },
                onNavigateToFinancialReport = { navController.navigate(Screen.FinancialReport.route) },
                onNavigateToBranchSupplyReport = { navController.navigate(Screen.BranchSupplyReport.route) },
                onNavigateToCustomerOrders = { navController.navigate(Screen.CustomerOrderList.route) },
                onNavigateToOrganizationSettings = { navController.navigate(Screen.OrganizationSettings.route) },
                onNavigateToFelSetup = { navController.navigate(Screen.FelSetup.route) },
                onNavigateToCashAudit = { navController.navigate(Screen.CashAudit.route(null)) },
                onNavigateToResellerApproval = { navController.navigate(Screen.ResellerApproval.route) },
                onNavigateToNotificationTarget = ::navigateToNotificationTarget,
                viewModel = viewModel,
                homeViewModel = homeViewModel,
                notificationViewModel = notificationViewModel
            )
        }

        // ── Productos ────────────────────────────────────────────────────────
        composable(Screen.ProductList.route) {
            RequirePermission(state.user, Permission.VIEW_PRODUCTS, onBack = { navController.popBackStack() }) {
                ProductListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.ProductDetail.route(id)) },
                    onNavigateToForm = { navController.navigate(Screen.ProductForm.route()) },
                    onNavigateToWeights = { navController.navigate(Screen.ProductWeights.route) },
                    authViewModel = viewModel,
                    viewModel = productViewModel
                )
            }
        }
        composable(Screen.ProductWeights.route) {
            RequirePermission(state.user, Permission.EDIT_PRODUCTS, onBack = { navController.popBackStack() }) {
                ProductWeightsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = productViewModel,
                    productionViewModel = productionViewModel
                )
            }
        }
        composable(
            route = Screen.ProductDetail.route,
            arguments = listOf(navArgument("productId") { type = NavType.StringType })
        ) { backStackEntry ->
            val productId = backStackEntry.stringArg("productId") ?: return@composable
            ProductDetailScreen(
                productId = productId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate(Screen.ProductForm.route(id)) },
                authViewModel = viewModel,
                viewModel = productViewModel,
                priceListViewModel = priceListViewModel
            )
        }
        composable(
            route = Screen.ProductForm.route,
            arguments = listOf(navArgument("productId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val productId = backStackEntry.stringArg("productId")
            RequirePermission(state.user, Permission.EDIT_PRODUCTS, onBack = { navController.popBackStack() }) {
                ProductFormScreen(
                    productId = productId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = productViewModel,
                    preparationViewModel = preparationViewModel,
                    priceListViewModel = priceListViewModel,
                    supplierViewModel = supplierViewModel,
                    ingredientViewModel = ingredientViewModel,
                    recipeViewModel = recipeViewModel
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
            val ingredientId = backStackEntry.stringArg("ingredientId") ?: return@composable
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
            val ingredientId = backStackEntry.stringArg("ingredientId")
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
            val recipeId = backStackEntry.stringArg("recipeId") ?: return@composable
            RecipeDetailScreen(
                recipeId = recipeId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate(Screen.RecipeForm.route(id)) },
                authViewModel = viewModel,
                viewModel = recipeViewModel,
                productViewModel = productViewModel
            )
        }
        composable(
            route = Screen.RecipeForm.route,
            arguments = listOf(navArgument("recipeId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val recipeId = backStackEntry.stringArg("recipeId")
            RequirePermission(state.user, Permission.EDIT_RECIPES, onBack = { navController.popBackStack() }) {
                RecipeFormScreen(
                    recipeId = recipeId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = recipeViewModel,
                    ingredientViewModel = ingredientViewModel,
                    productViewModel = productViewModel,
                    onNavigateToProducts = {
                        // Va directo al form de creación de un nuevo producto (no a la lista vacía).
                        navController.navigate(Screen.ProductForm.route()) {
                            popUpTo(Screen.RecipeList.route) { inclusive = false }
                        }
                    }
                )
            }
        }

        // ── Preparaciones ────────────────────────────────────────────────────
        composable(Screen.PreparationList.route) {
            RequirePermission(state.user, Permission.VIEW_PREPARATIONS, onBack = { navController.popBackStack() }) {
                PreparationListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.PreparationDetail.route(id)) },
                    onNavigateToForm = { navController.navigate(Screen.PreparationForm.route()) },
                    authViewModel = viewModel,
                    viewModel = preparationViewModel
                )
            }
        }
        composable(
            route = Screen.PreparationDetail.route,
            arguments = listOf(navArgument("preparationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val preparationId = backStackEntry.stringArg("preparationId") ?: return@composable
            PreparationDetailScreen(
                preparationId = preparationId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate(Screen.PreparationForm.route(id)) },
                authViewModel = viewModel,
                viewModel = preparationViewModel
            )
        }
        composable(
            route = Screen.PreparationForm.route,
            arguments = listOf(navArgument("preparationId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val preparationId = backStackEntry.stringArg("preparationId")
            RequirePermission(state.user, Permission.EDIT_PREPARATIONS, onBack = { navController.popBackStack() }) {
                PreparationFormScreen(
                    preparationId = preparationId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = preparationViewModel,
                    ingredientViewModel = ingredientViewModel
                )
            }
        }
        composable(Screen.PreparationBatchList.route) {
            RequirePermission(state.user, Permission.MANAGE_PREPARATION_BATCHES, onBack = { navController.popBackStack() }) {
                PreparationBatchListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToForm = { navController.navigate(Screen.PreparationBatchForm.route) },
                    authViewModel = viewModel,
                    viewModel = preparationBatchViewModel
                )
            }
        }
        composable(Screen.PreparationBatchForm.route) {
            RequirePermission(state.user, Permission.MANAGE_PREPARATION_BATCHES, onBack = { navController.popBackStack() }) {
                PreparationBatchFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = preparationBatchViewModel
                )
            }
        }

        // ── Listas de Precios ────────────────────────────────────────────────
        composable(Screen.PriceListAdmin.route) {
            RequirePermission(state.user, Permission.MANAGE_PRICE_LISTS, onBack = { navController.popBackStack() }) {
                PriceListAdminScreen(
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = priceListViewModel
                )
            }
        }

        // ── Despacho ─────────────────────────────────────────────────────────
        composable(Screen.DispatchList.route) {
            // Misma condición con la que HomeScreen muestra el módulo: Despacho es solo
            // de la planta. Las sucursales siguen su pan en Reparto.
            RequirePermission(
                state.user,
                Permission.DISPATCH_PACK,
                onBack = { navController.popBackStack() }
            ) {
                DispatchNoteListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToBoard = { navController.navigate(Screen.DeliveryList.route) },
                    authViewModel = viewModel,
                    viewModel = dispatchViewModel,
                    productionViewModel = productionViewModel
                )
            }
        }
        composable(
            route = Screen.DispatchPack.route,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) { backStackEntry ->
            val noteId = backStackEntry.stringArg("noteId") ?: return@composable
            RequirePermission(state.user, Permission.DISPATCH_PACK, onBack = { navController.popBackStack() }) {
                DispatchNotePackScreen(
                    noteId = noteId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = dispatchViewModel,
                    productionViewModel = productionViewModel
                )
            }
        }
        composable(
            route = Screen.DispatchReceive.route,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) { backStackEntry ->
            val noteId = backStackEntry.stringArg("noteId") ?: return@composable
            RequirePermission(state.user, Permission.RECEIVE_DISPATCH, onBack = { navController.popBackStack() }) {
                DispatchNoteReceiveScreen(
                    noteId = noteId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = dispatchViewModel,
                    productionViewModel = productionViewModel
                )
            }
        }
        composable(
            route = Screen.DispatchDetail.route,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) { backStackEntry ->
            val noteId = backStackEntry.stringArg("noteId") ?: return@composable
            DispatchNoteDetailScreen(
                noteId = noteId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPack = { id -> navController.navigate(Screen.DispatchPack.route(id)) },
                onNavigateToReceive = { id -> navController.navigate(Screen.DispatchReceive.route(id)) },
                authViewModel = viewModel,
                viewModel = dispatchViewModel,
                productionViewModel = productionViewModel
            )
        }

        // ── Hojas de Reparto ─────────────────────────────────────────────────
        composable(Screen.DeliveryList.route) {
            // Reparto es el tablero del día de todos: la planta, las sucursales (que firman
            // y cuentan desde acá), el repartidor y quien sigue la producción.
            RequirePermission(
                state.user, Permission.MANAGE_DELIVERY_RUNS, Permission.DRIVE_DELIVERY,
                Permission.RECEIVE_DISPATCH, Permission.VIEW_PRODUCTION,
                onBack = { navController.popBackStack() }
            ) {
                DeliveryBoardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenReceive = { noteId -> navController.navigate(Screen.DispatchReceive.route(noteId)) },
                    onOpenDetail = { noteId -> navController.navigate(Screen.DispatchDetail.route(noteId)) },
                    onOpenPacking = { navController.navigate(Screen.DispatchList.route) },
                    onOpenRun = { runId -> navController.navigate(Screen.DeliveryRunDetail.route(runId)) },
                    onOpenHistory = { navController.navigate(Screen.DeliveryHistory.route) },
                    authViewModel = viewModel,
                    viewModel = deliveryViewModel,
                    dispatchViewModel = dispatchViewModel
                )
            }
        }
        composable(
            route = Screen.DeliveryForm.route,
            arguments = listOf(navArgument("runId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val runId = backStackEntry.stringArg("runId")
            RequirePermission(state.user, Permission.MANAGE_DELIVERY_RUNS, onBack = { navController.popBackStack() }) {
                DeliveryRunFormScreen(
                    runId = runId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = deliveryViewModel,
                    branchViewModel = branchViewModel
                )
            }
        }
        composable(
            route = Screen.DeliveryRunDetail.route,
            arguments = listOf(navArgument("runId") { type = NavType.StringType })
        ) { backStackEntry ->
            val runId = backStackEntry.stringArg("runId") ?: return@composable
            DeliveryRunScreen(
                runId = runId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate(Screen.DeliveryForm.route(id)) },
                onNavigateToNote = { id -> navController.navigate(Screen.DispatchDetail.route(id)) },
                authViewModel = viewModel,
                viewModel = deliveryViewModel
            )
        }
        composable(Screen.DeliveryHistory.route) {
            RequirePermission(state.user, Permission.MANAGE_DELIVERY_RUNS, onBack = { navController.popBackStack() }) {
                DeliveryHistoryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToRun = { id -> navController.navigate(Screen.DeliveryRunDetail.route(id)) },
                    authViewModel = viewModel,
                    viewModel = deliveryViewModel
                )
            }
        }

        // ── Inventario de tienda ─────────────────────────────────────────────
        composable(Screen.FinishedStockList.route) {
            RequirePermission(state.user, Permission.VIEW_PRODUCTS, onBack = { navController.popBackStack() }) {
                FinishedProductStockListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMovements = { bId, pId ->
                        navController.navigate(Screen.FinishedStockMovements.route(bId, pId))
                    },
                    onNavigateToAdjust = { bId, pId ->
                        navController.navigate(Screen.FinishedStockAdjust.route(bId, pId))
                    },
                    authViewModel = viewModel,
                    viewModel = finishedStockViewModel
                )
            }
        }
        composable(
            route = Screen.FinishedStockMovements.route,
            arguments = listOf(
                navArgument("branchId") { type = NavType.StringType },
                navArgument("productId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bId = backStackEntry.stringArg("branchId") ?: return@composable
            val pId = backStackEntry.stringArg("productId") ?: return@composable
            FinishedStockMovementListScreen(
                branchId = bId,
                productId = pId,
                onNavigateBack = { navController.popBackStack() },
                authViewModel = viewModel,
                viewModel = finishedStockViewModel
            )
        }
        composable(
            route = Screen.FinishedStockAdjust.route,
            arguments = listOf(
                navArgument("branchId") { type = NavType.StringType },
                navArgument("productId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val bId = backStackEntry.stringArg("branchId") ?: return@composable
            val pId = backStackEntry.stringArg("productId")
            RequirePermission(state.user, Permission.MANAGE_FINISHED_STOCK, onBack = { navController.popBackStack() }) {
                FinishedStockAdjustScreen(
                    branchId = bId,
                    productId = pId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = finishedStockViewModel
                )
            }
        }

        // ── Punto de Venta (POS) ─────────────────────────────────────────────
        composable(Screen.POSHome.route) {
            RequirePermission(state.user, Permission.OPERATE_POS, onBack = { navController.popBackStack() }) {
                POSHomeScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenSession = { regId -> navController.navigate(Screen.CashSessionOpen.route(regId)) },
                    onContinueSession = { sessionId ->
                        navController.navigate(Screen.POSScreen.route(sessionId)) {
                            popUpTo(Screen.POSHome.route) { inclusive = false }
                        }
                    },
                    authViewModel = viewModel,
                    viewModel = cashSessionViewModel
                )
            }
        }
        composable(
            route = Screen.CashSessionOpen.route,
            arguments = listOf(navArgument("registerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val registerId = backStackEntry.stringArg("registerId") ?: return@composable
            RequirePermission(state.user, Permission.OPERATE_POS, onBack = { navController.popBackStack() }) {
                CashSessionOpenScreen(
                    registerId = registerId,
                    onNavigateBack = { navController.popBackStack() },
                    onSessionOpened = { sessionId ->
                        navController.navigate(Screen.POSScreen.route(sessionId)) {
                            popUpTo(Screen.POSHome.route) { inclusive = false }
                        }
                    },
                    authViewModel = viewModel,
                    viewModel = cashSessionViewModel
                )
            }
        }
        composable(
            route = Screen.POSScreen.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.stringArg("sessionId") ?: return@composable
            RequirePermission(state.user, Permission.OPERATE_POS, onBack = { navController.popBackStack() }) {
                POSScreen(
                    sessionId = sessionId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToClose = { sid -> navController.navigate(Screen.CashSessionClose.route(sid)) },
                    onNavigateToCashExpense = { sid -> navController.navigate(Screen.CashExpenseForm.route(sid)) },
                    onNavigateToSessionDetail = { sid -> navController.navigate(Screen.CashSessionDetail.route(sid)) },
                    authViewModel = viewModel,
                    viewModel = posViewModel,
                    cashSessionViewModel = cashSessionViewModel,
                    bluetoothPrinterManager = bluetoothPrinterManager,
                    creditViewModel = creditViewModel,
                    documentPrinter = vms.documentPrinter
                )
            }
        }
        composable(
            route = Screen.CashSessionClose.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.stringArg("sessionId") ?: return@composable
            RequirePermission(state.user, Permission.OPERATE_POS, onBack = { navController.popBackStack() }) {
                CashSessionCloseScreen(
                    sessionId = sessionId,
                    onNavigateBack = { navController.popBackStack() },
                    onClosed = { sid ->
                        navController.navigate(Screen.CashSessionDetail.route(sid)) {
                            popUpTo(Screen.POSHome.route) { inclusive = false }
                        }
                    },
                    authViewModel = viewModel,
                    viewModel = cashSessionViewModel,
                    // Entregó la caja: el siguiente turno sigue vendiendo en la misma gaveta.
                    onHandedOver = { next ->
                        navController.navigate(Screen.POSScreen.route(next)) {
                            popUpTo(Screen.POSHome.route) { inclusive = false }
                        }
                    }
                )
            }
        }
        composable(
            route = Screen.CashSessionDetail.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.stringArg("sessionId") ?: return@composable
            RequirePermission(state.user, Permission.OPERATE_POS, onBack = { navController.popBackStack() }) {
                CashSessionDetailScreen(
                    sessionId = sessionId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = cashSessionViewModel,
                    purchaseViewModel = purchaseViewModel,
                    // Corregir reabre la MISMA captura que usó quien lo anotó. El permiso
                    // amplio de Compras no hace falta para lo que salió de la propia gaveta
                    // mientras siga abierta: la guarda está en `saveQuickPurchase`.
                    onNavigateToPurchase = { id -> navController.navigate(Screen.QuickPurchaseEdit.route(id)) }
                )
            }
        }

        // ── Historial de ventas ──────────────────────────────────────────────
        composable(Screen.SaleHistoryList.route) {
            RequirePermission(state.user, Permission.VIEW_SALES, onBack = { navController.popBackStack() }) {
                SaleHistoryListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.SaleDetail.route(id)) },
                    authViewModel = viewModel,
                    viewModel = saleHistoryViewModel
                )
            }
        }
        composable(
            route = Screen.SaleDetail.route,
            arguments = listOf(navArgument("saleId") { type = NavType.StringType })
        ) { backStackEntry ->
            val saleId = backStackEntry.stringArg("saleId") ?: return@composable
            RequirePermission(state.user, Permission.VIEW_SALES, onBack = { navController.popBackStack() }) {
                SaleDetailScreen(
                    saleId = saleId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = saleHistoryViewModel,
                    bluetoothPrinterManager = bluetoothPrinterManager
                )
            }
        }

        // ── Admin POS: cajas registradoras ───────────────────────────────────
        composable(Screen.CashRegisterAdminList.route) {
            RequirePermission(state.user, Permission.MANAGE_POS_CONFIG, onBack = { navController.popBackStack() }) {
                CashRegisterAdminScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToForm = { id -> navController.navigate(Screen.CashRegisterForm.route(id)) },
                    authViewModel = viewModel,
                    viewModel = cashRegisterAdminViewModel
                )
            }
        }
        composable(
            route = Screen.CashRegisterForm.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val id = backStackEntry.stringArg("id")
            RequirePermission(state.user, Permission.MANAGE_POS_CONFIG, onBack = { navController.popBackStack() }) {
                CashRegisterFormScreen(
                    registerId = id,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = cashRegisterAdminViewModel
                )
            }
        }

        // ── Admin POS: categorías ────────────────────────────────────────────
        composable(Screen.POSCategoryAdminList.route) {
            RequirePermission(state.user, Permission.MANAGE_POS_CONFIG, onBack = { navController.popBackStack() }) {
                POSCategoryAdminScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToForm = { id -> navController.navigate(Screen.POSCategoryForm.route(id)) },
                    authViewModel = viewModel,
                    viewModel = posCategoryAdminViewModel
                )
            }
        }
        composable(
            route = Screen.POSCategoryForm.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val id = backStackEntry.stringArg("id")
            RequirePermission(state.user, Permission.MANAGE_POS_CONFIG, onBack = { navController.popBackStack() }) {
                POSCategoryFormScreen(
                    categoryId = id,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = posCategoryAdminViewModel
                )
            }
        }

        // ── Créditos ─────────────────────────────────────────────────────────
        composable(Screen.CreditAccountList.route) {
            RequirePermission(state.user, Permission.MANAGE_CREDIT_ACCOUNTS, onBack = { navController.popBackStack() }) {
                CreditAccountListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.CreditAccountDetail.route(id)) },
                    onNavigateToForm = { navController.navigate(Screen.CreditAccountForm.route()) },
                    onNavigateToAging = { navController.navigate(Screen.CreditAgingReport.route) },
                    authViewModel = viewModel,
                    viewModel = creditViewModel
                )
            }
        }
        composable(Screen.CreditAgingReport.route) {
            RequirePermission(state.user, Permission.MANAGE_CREDIT_ACCOUNTS, onBack = { navController.popBackStack() }) {
                com.bakery.erp.presentation.credit.CreditAgingReportScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.CreditAccountDetail.route(id)) },
                    authViewModel = viewModel,
                    viewModel = creditViewModel,
                    documentPrinter = documentPrinter,
                    bluetoothPrinterManager = bluetoothPrinterManager
                )
            }
        }
        composable(
            route = Screen.CreditAccountForm.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val id = backStackEntry.stringArg("id")
            RequirePermission(state.user, Permission.MANAGE_CREDIT_ACCOUNTS, onBack = { navController.popBackStack() }) {
                CreditAccountFormScreen(
                    accountId = id,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = creditViewModel
                )
            }
        }
        composable(
            route = Screen.CreditAccountDetail.route,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.stringArg("accountId") ?: return@composable
            RequirePermission(state.user, Permission.MANAGE_CREDIT_ACCOUNTS, onBack = { navController.popBackStack() }) {
                CreditAccountDetailScreen(
                    accountId = accountId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id -> navController.navigate(Screen.CreditAccountForm.route(id)) },
                    authViewModel = viewModel,
                    viewModel = creditViewModel,
                    documentPrinter = documentPrinter,
                    bluetoothPrinterManager = bluetoothPrinterManager
                )
            }
        }

        // ── Activos Fijos ────────────────────────────────────────────────────
        composable(Screen.AssetList.route) {
            RequirePermission(state.user, Permission.VIEW_ASSETS, Permission.EDIT_ASSETS, Permission.MANAGE_ASSETS, onBack = { navController.popBackStack() }) {
                FixedAssetListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.AssetDetail.route(id)) },
                    onNavigateToForm = { navController.navigate(Screen.AssetForm.route()) },
                    authViewModel = viewModel,
                    branchViewModel = branchViewModel,
                    viewModel = fixedAssetViewModel
                )
            }
        }
        composable(
            route = Screen.AssetDetail.route,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType })
        ) { backStackEntry ->
            val assetId = backStackEntry.stringArg("assetId") ?: return@composable
            RequirePermission(state.user, Permission.VIEW_ASSETS, Permission.EDIT_ASSETS, Permission.MANAGE_ASSETS, onBack = { navController.popBackStack() }) {
                FixedAssetDetailScreen(
                    assetId = assetId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id -> navController.navigate(Screen.AssetForm.route(id)) },
                    onNavigateToHistoryForm = { aid, eid -> navController.navigate(Screen.AssetHistoryEntryForm.route(aid, eid)) },
                    authViewModel = viewModel,
                    viewModel = fixedAssetViewModel
                )
            }
        }
        composable(
            route = Screen.AssetForm.route,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val assetId = backStackEntry.stringArg("assetId")
            RequirePermission(state.user, Permission.EDIT_ASSETS, onBack = { navController.popBackStack() }) {
                FixedAssetFormScreen(
                    assetId = assetId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    branchViewModel = branchViewModel,
                    viewModel = fixedAssetViewModel
                )
            }
        }
        composable(
            route = Screen.AssetHistoryEntryForm.route,
            arguments = listOf(
                navArgument("assetId") { type = NavType.StringType },
                navArgument("entryId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val assetId = backStackEntry.stringArg("assetId") ?: return@composable
            val entryId = backStackEntry.stringArg("entryId")
            RequirePermission(state.user, Permission.ADD_ASSET_HISTORY, Permission.EDIT_ASSETS, onBack = { navController.popBackStack() }) {
                AssetHistoryEntryFormScreen(
                    assetId = assetId,
                    entryId = entryId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    branchViewModel = branchViewModel,
                    viewModel = fixedAssetViewModel
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
            val supplierId = backStackEntry.stringArg("supplierId") ?: return@composable
            SupplierDetailScreen(
                supplierId = supplierId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate(Screen.SupplierForm.route(id)) },
                onNavigateToPurchase = { id -> navController.navigate(Screen.PurchaseDetail.route(id)) },
                authViewModel = viewModel,
                viewModel = supplierViewModel
            )
        }
        composable(
            route = Screen.SupplierForm.route,
            arguments = listOf(navArgument("supplierId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val supplierId = backStackEntry.stringArg("supplierId")
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
            val branchId = backStackEntry.stringArg("branchId") ?: return@composable
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
            val branchId = backStackEntry.stringArg("branchId")
            RequirePermission(state.user, Permission.MANAGE_BRANCHES, onBack = { navController.popBackStack() }) {
                BranchFormScreen(
                    branchId = branchId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = branchViewModel,
                    productViewModel = productViewModel,
                    priceListViewModel = priceListViewModel
                )
            }
        }

        // ── Gastos ───────────────────────────────────────────────────────────
        composable(Screen.ExpenseList.route) {
            // Ya no pide `MANAGE_FIXED_BUDGET`: los costos fijos salieron de esta pantalla y
            // tienen su propia ruta, así que quien solo los administra no necesita entrar acá.
            RequirePermission(state.user, Permission.VIEW_EXPENSES, Permission.ADD_VARIABLE_EXPENSE, onBack = { navController.popBackStack() }) {
                ExpenseListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.ExpenseDetail.route(id)) },
                    onNavigateToQuickPurchase = { navController.navigate(Screen.CashExpenseForm.route()) },
                    authViewModel = viewModel,
                    viewModel = expenseViewModel,
                    branchViewModel = branchViewModel
                )
            }
        }
        // Anotar un gasto: UNA pantalla para los tres accesos (Operaciones, Gastos y la caja
        // abierta del POS). Lo único que cambia entre ellos es si viene `sessionId`, que es
        // lo que decide si el efectivo sale de una gaveta o de la caja general.
        //
        // El permiso depende justo de eso. Sin caja el desembolso no le falta a nadie al
        // cuadrar, así que anotarlo es una decisión de administración (`ADD_GENERAL_EXPENSE`);
        // con caja lo anota quien la está operando (`ADD_VARIABLE_EXPENSE`). Un solo permiso
        // para ambos dejaba a una cajera pudiendo cargar a "caja general" un gasto que sí
        // salió de su gaveta, y ahí el faltante del cierre no lo explica nada.
        composable(
            route = Screen.CashExpenseForm.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val sessionArg = backStackEntry.stringArg("sessionId")?.ifEmpty { null }
            val needed = if (sessionArg == null) Permission.ADD_GENERAL_EXPENSE
                         else Permission.ADD_VARIABLE_EXPENSE
            RequirePermission(state.user, needed, onBack = { navController.popBackStack() }) {
                QuickPurchaseFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = purchaseViewModel,
                    sessionId = sessionArg,
                    // Comprarle a un proveedor es la MISMA captura que una factura; lo único
                    // distinto es de qué bolsa salió el dinero, y eso viaja en el sessionId.
                    onNavigateToSupplierPurchase = {
                        navController.navigate(Screen.PurchaseForm.route(sessionArg))
                    }
                )
            }
        }
        composable(
            route = Screen.ExpenseDetail.route,
            arguments = listOf(navArgument("expenseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val expenseId = backStackEntry.stringArg("expenseId") ?: return@composable
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
            val expenseId = backStackEntry.stringArg("expenseId")
            // Corregir un gasto ya registrado es trabajo de administración, no de caja: los
            // que nacen de una compra ni siquiera se editan acá, así que lo único que queda
            // son los capturados a mano antes de que la captura se unificara.
            RequirePermission(state.user, Permission.ADD_GENERAL_EXPENSE, onBack = { navController.popBackStack() }) {
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
            val itemId = backStackEntry.stringArg("itemId")
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
                    onNavigateToForm = { navController.navigate(Screen.PurchaseForm.route()) },
                    onNavigateToDetail = { id -> navController.navigate(Screen.PurchaseDetail.route(id)) },
                    onNavigateToEdit = { id, quick ->
                        navController.navigate(
                            if (quick) Screen.QuickPurchaseEdit.route(id)
                            else Screen.PurchaseEdit.route(id)
                        )
                    },
                    authViewModel = viewModel,
                    viewModel = purchaseViewModel
                )
            }
        }
        // Corregir una compra rápida tiene su propia ruta y NO cae en `PurchaseEdit`: aquel
        // formulario es el de la factura y no sabe nada de lugar ni de sucursal por línea,
        // así que reabrir ahí una compra rápida pedía proveedor y escondía sus datos.
        composable(
            route = Screen.QuickPurchaseEdit.route,
            arguments = listOf(navArgument("purchaseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val editId = backStackEntry.stringArg("purchaseId") ?: return@composable
            // `ADD_VARIABLE_EXPENSE` alcanza porque el ViewModel revalida: quien no tenga
            // `EDIT_PURCHASES` solo puede guardar la corrección de una compra rápida atribuida
            // a una gaveta que siga abierta. Sin esto, la cajera que teclea 325 en vez de
            // 32.50 tiene que llamar a alguien antes de cerrar, y el desvío termina
            // cuadrándose a mano.
            RequirePermission(state.user, Permission.EDIT_PURCHASES, Permission.ADD_VARIABLE_EXPENSE, onBack = { navController.popBackStack() }) {
                QuickPurchaseFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = purchaseViewModel,
                    purchaseId = editId
                )
            }
        }
        composable(
            route = Screen.PurchaseEdit.route,
            arguments = listOf(navArgument("purchaseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val editId = backStackEntry.stringArg("purchaseId") ?: return@composable
            RequirePermission(state.user, Permission.EDIT_PURCHASES, onBack = { navController.popBackStack() }) {
                PurchaseFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSuppliers = { navController.navigate(Screen.SupplierList.route) },
                    authViewModel = viewModel,
                    viewModel = purchaseViewModel,
                    purchaseId = editId
                )
            }
        }
        composable(
            route = Screen.PurchaseForm.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            RequirePermission(state.user, Permission.RECEIVE_STOCK, onBack = { navController.popBackStack() }) {
                PurchaseFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSuppliers = { navController.navigate(Screen.SupplierList.route) },
                    authViewModel = viewModel,
                    viewModel = purchaseViewModel,
                    // Con caja, la factura se paga de esa gaveta y nace saldada. Sin ella es
                    // la captura de siempre, con crédito y vencimiento.
                    sessionId = backStackEntry.stringArg("sessionId")?.ifEmpty { null }
                )
            }
        }
        composable(
            route = Screen.PurchaseDetail.route,
            arguments = listOf(navArgument("purchaseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val purchaseId = backStackEntry.stringArg("purchaseId") ?: return@composable
            RequirePermission(state.user, Permission.RECEIVE_STOCK, onBack = { navController.popBackStack() }) {
                PurchaseDetailScreen(
                    purchaseId = purchaseId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id, quick ->
                        navController.navigate(
                            if (quick) Screen.QuickPurchaseEdit.route(id)
                            else Screen.PurchaseEdit.route(id)
                        )
                    },
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
                    onNavigateToForm = { navController.navigate(Screen.ProductionOrderForm.route()) },
                    onNavigateToEditOrder = { id -> navController.navigate(Screen.ProductionOrderForm.route(id)) },
                    onNavigateToLotForm = { branchId, shiftId, date ->
                        navController.navigate(Screen.ProductionLotForm.route(branchId, shiftId, date))
                    },
                    onNavigateToLotDetail = { id -> navController.navigate(Screen.ProductionLotDetail.route(id)) },
                    onNavigateToShiftSplit = { branchId, date ->
                        navController.navigate(Screen.ProductionShiftSplit.route(branchId, date))
                    },
                    authViewModel = viewModel,
                    viewModel = productionViewModel,
                    productViewModel = productViewModel
                )
            }
        }
        composable(
            route = Screen.ProductionOrderForm.route,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val orderId = backStackEntry.stringArg("orderId")
            RequirePermission(state.user, Permission.SEND_PRODUCTION_ORDER, onBack = { navController.popBackStack() }) {
                ProductionOrderFormScreen(
                    orderId = orderId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEditOrder = { id ->
                        // Reemplaza la instancia actual de form para que el botón "Volver"
                        // regrese a la lista, no al form de creación.
                        navController.navigate(Screen.ProductionOrderForm.route(id)) {
                            popUpTo(Screen.ProductionOrderForm.route) { inclusive = true }
                        }
                    },
                    onLocationDenied = {
                        // Sin ubicación no se puede armar el pedido para esta
                        // sucursal — reset duro a Home, no un simple "volver".
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    },
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
            val orderId = backStackEntry.stringArg("orderId") ?: return@composable
            RequirePermission(state.user, Permission.VIEW_PRODUCTION, Permission.SEND_PRODUCTION_ORDER, Permission.MANAGE_PRODUCTION_LOTS, onBack = { navController.popBackStack() }) {
                ProductionOrderDetailScreen(
                    orderId = orderId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id ->
                        navController.navigate(Screen.ProductionOrderForm.route(id))
                    },
                    authViewModel = viewModel,
                    viewModel = productionViewModel,
                    productViewModel = productViewModel
                )
            }
        }

        // ── Producción — Lotes ───────────────────────────────────────────────
        composable(Screen.ProductionLotList.route) {
            RequirePermission(state.user, Permission.MANAGE_PRODUCTION_LOTS, Permission.VIEW_PRODUCTION, onBack = { navController.popBackStack() }) {
                ProductionLotListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.ProductionLotDetail.route(id)) },
                    onNavigateToForm = { navController.navigate(Screen.ProductionLotForm.route()) },
                    authViewModel = viewModel,
                    viewModel = productionViewModel
                )
            }
        }
        composable(
            route = Screen.ProductionLotForm.route,
            arguments = listOf(
                navArgument("branchId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("shiftId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val branchId = backStackEntry.stringArg("branchId")
            val shiftId = backStackEntry.stringArg("shiftId")
            val date = backStackEntry.stringArg("date")?.toLongOrNull()
            RequirePermission(state.user, Permission.MANAGE_PRODUCTION_LOTS, onBack = { navController.popBackStack() }) {
                ProductionLotFormScreen(
                    presetBranchId = branchId,
                    presetShiftId = shiftId,
                    presetDate = date,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToShiftSplit = { splitBranchId, splitDate ->
                        // Reemplaza la entrada actual: "volver" desde el reparto no debe
                        // regresar a un form de lote a medio configurar sin turno posible.
                        navController.navigate(Screen.ProductionShiftSplit.route(splitBranchId, splitDate)) {
                            popUpTo(Screen.ProductionLotForm.route) { inclusive = true }
                        }
                    },
                    authViewModel = viewModel,
                    viewModel = productionViewModel,
                    productViewModel = productViewModel
                )
            }
        }
        composable(
            route = Screen.ProductionShiftSplit.route,
            arguments = listOf(
                navArgument("branchId") { type = NavType.StringType },
                navArgument("date") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val branchId = backStackEntry.stringArg("branchId") ?: return@composable
            val date = backStackEntry.stringArg("date")?.toLongOrNull() ?: return@composable
            RequirePermission(state.user, Permission.MANAGE_PRODUCTION_LOTS, onBack = { navController.popBackStack() }) {
                ProductionShiftSplitScreen(
                    presetBranchId = branchId,
                    presetDate = date,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLotDetail = { id -> navController.navigate(Screen.ProductionLotDetail.route(id)) },
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
            val lotId = backStackEntry.stringArg("lotId") ?: return@composable
            RequirePermission(state.user, Permission.MANAGE_PRODUCTION_LOTS, Permission.VIEW_PRODUCTION, onBack = { navController.popBackStack() }) {
                ProductionLotDetailScreen(
                    lotId = lotId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToComplete = { id -> navController.navigate(Screen.ProductionLotComplete.route(id)) },
                    authViewModel = viewModel,
                    viewModel = productionViewModel,
                    productViewModel = productViewModel,
                    documentPrinter = documentPrinter,
                    bluetoothPrinterManager = bluetoothPrinterManager,
                    plantIssueViewModel = plantIssueViewModel
                )
            }
        }
        composable(Screen.PlantIssueReport.route) {
            // Con VIEW_INVENTORY_REPORT alcanza para MIRAR: el reporte de a cada
            // cuánto sacan un saco es del dueño, que no necesariamente saca material.
            RequirePermission(
                state.user,
                Permission.ISSUE_PLANT_MATERIAL,
                Permission.VIEW_INVENTORY_REPORT,
                onBack = { navController.popBackStack() }
            ) {
                PlantIssueReportScreen(
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = plantIssueViewModel
                )
            }
        }
        composable(
            route = Screen.ProductionLotComplete.route,
            arguments = listOf(navArgument("lotId") { type = NavType.StringType })
        ) { backStackEntry ->
            val lotId = backStackEntry.stringArg("lotId") ?: return@composable
            RequirePermission(state.user, Permission.MANAGE_PRODUCTION_LOTS, onBack = { navController.popBackStack() }) {
                ProductionLotCompleteScreen(
                    lotId = lotId,
                    onNavigateBack = { navController.popBackStack() },
                    onCompleted = {
                        // Tras completar exitosamente, volvemos a Home en vez de
                        // dejar al panadero en el detalle del lote ya cerrado.
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    authViewModel = viewModel,
                    viewModel = productionViewModel,
                    productViewModel = productViewModel
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
                    onNavigateToJobPositions = { navController.navigate(Screen.JobPositionList.route) },
                    authViewModel = viewModel,
                    viewModel = employeeViewModel
                )
            }
        }
        composable(
            route = Screen.EmployeeDetail.route,
            arguments = listOf(navArgument("employeeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val employeeId = backStackEntry.stringArg("employeeId") ?: return@composable
            RequirePermission(state.user, Permission.MANAGE_EMPLOYEES, onBack = { navController.popBackStack() }) {
                EmployeeDetailScreen(
                    employeeId = employeeId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToForm = { id -> navController.navigate(Screen.EmployeeForm.route(id)) },
                    onNavigateToPayrollForm = { empId, payrollId ->
                        navController.navigate(Screen.PayrollForm.route(empId, payrollId))
                    },
                    authViewModel = viewModel,
                    viewModel = employeeViewModel,
                    creditViewModel = creditViewModel
                )
            }
        }
        composable(
            route = Screen.EmployeeForm.route,
            arguments = listOf(
                navArgument("employeeId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val employeeId = backStackEntry.stringArg("employeeId")
            RequirePermission(state.user, Permission.MANAGE_EMPLOYEES, onBack = { navController.popBackStack() }) {
                EmployeeFormScreen(
                    employeeId = employeeId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = employeeViewModel
                )
            }
        }
        composable(Screen.JobPositionList.route) {
            RequirePermission(state.user, Permission.MANAGE_EMPLOYEES, onBack = { navController.popBackStack() }) {
                JobPositionListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate(Screen.JobPositionDetail.route(id)) },
                    onNavigateToForm = { id -> navController.navigate(Screen.JobPositionForm.route(id)) },
                    authViewModel = viewModel,
                    viewModel = jobPositionViewModel
                )
            }
        }
        composable(
            route = Screen.JobPositionDetail.route,
            arguments = listOf(navArgument("positionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val positionId = backStackEntry.stringArg("positionId") ?: return@composable
            RequirePermission(state.user, Permission.MANAGE_EMPLOYEES, onBack = { navController.popBackStack() }) {
                JobPositionDetailScreen(
                    positionId = positionId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToForm = { id -> navController.navigate(Screen.JobPositionForm.route(id)) },
                    authViewModel = viewModel,
                    jobPositionViewModel = jobPositionViewModel,
                    employeeViewModel = employeeViewModel
                )
            }
        }
        composable(
            route = Screen.JobPositionForm.route,
            arguments = listOf(navArgument("positionId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val positionId = backStackEntry.stringArg("positionId")
            RequirePermission(state.user, Permission.MANAGE_EMPLOYEES, onBack = { navController.popBackStack() }) {
                JobPositionFormScreen(
                    positionId = positionId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = jobPositionViewModel
                )
            }
        }
        composable(
            route = Screen.PayrollForm.route,
            arguments = listOf(
                navArgument("employeeId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("payrollId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val employeeId = backStackEntry.stringArg("employeeId") ?: return@composable
            val payrollId = backStackEntry.stringArg("payrollId")
            RequirePermission(state.user, Permission.MANAGE_EMPLOYEES, onBack = { navController.popBackStack() }) {
                PayrollFormScreen(
                    employeeId = employeeId,
                    payrollId = payrollId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = employeeViewModel
                )
            }
        }

        // ── Ingreso a Turno ───────────────────────────────────────────────────
        composable(Screen.ShiftClockIn.route) {
            RequirePermission(state.user, Permission.MANAGE_EMPLOYEES, onBack = { navController.popBackStack() }) {
                ShiftClockInScreen(
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
            val branchId = backStackEntry.stringArg("branchId")
            val shiftId = backStackEntry.stringArg("shiftId")
            RequirePermission(state.user, Permission.MANAGE_PRODUCTION_SHIFTS, onBack = { navController.popBackStack() }) {
                ProductionShiftFormScreen(
                    branchId = branchId,
                    shiftId = shiftId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = productionViewModel,
                    productViewModel = productViewModel
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
            val userId = backStackEntry.stringArg("userId") ?: return@composable
            UserPermissionsScreen(
                userId = userId,
                onNavigateBack = { navController.popBackStack() },
                authViewModel = viewModel,
                viewModel = userViewModel,
                branchViewModel = branchViewModel
            )
        }

        // ── Conteo de pan (cierre del día) ────────────────────────────────────
        composable(Screen.BreadCountList.route) {
            RequirePermission(state.user, Permission.VIEW_BREAD_COUNTS, Permission.COUNT_FINISHED_STOCK,
                onBack = { navController.popBackStack() }) {
                BreadCountListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCount = { id ->
                        navController.navigate(Screen.BreadCountForm.route(id))
                    },
                    onOpenNewForBranch = {
                        val s = breadCountViewModel.uiState.value
                        val branch = s.branches.firstOrNull { it.id == s.selectedBranchId }
                        val user = state.user
                        if (branch != null && user != null) {
                            breadCountViewModel.openOrResumeToday(user.orgId, branch, user)
                        }
                    },
                    authViewModel = viewModel,
                    viewModel = breadCountViewModel
                )
            }
            // Navega cuando ya hay un id creado/reanudado.
            val s by breadCountViewModel.uiState.collectAsState()
            LaunchedEffect(s.createdOrResumedId) {
                s.createdOrResumedId?.let { id ->
                    breadCountViewModel.clearCreatedId()
                    navController.navigate(Screen.BreadCountForm.route(id))
                }
            }
        }
        composable(
            route = Screen.BreadCountForm.route,
            arguments = listOf(navArgument("countId") { type = NavType.StringType })
        ) { backStackEntry ->
            val countId = backStackEntry.stringArg("countId") ?: return@composable
            RequirePermission(state.user, Permission.COUNT_FINISHED_STOCK,
                onBack = { navController.popBackStack() }) {
                BreadCountFormScreen(
                    countId = countId,
                    onNavigateBack = { navController.popBackStack() },
                    onClosed = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = breadCountViewModel
                )
            }
        }

        // ── Cierre del día por sucursal ───────────────────────────────────────
        composable(Screen.DailyCashCloseList.route) {
            RequirePermission(state.user, Permission.MANAGE_DAILY_CASH_CLOSE,
                onBack = { navController.popBackStack() }) {
                DailyCashCloseListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenClose = { bId, date ->
                        navController.navigate(Screen.DailyCashClose.route(bId, date))
                    },
                    onOpenToday = { bId ->
                        navController.navigate(Screen.DailyCashClose.route(bId))
                    },
                    authViewModel = viewModel,
                    viewModel = dailyCashCloseViewModel
                )
            }
        }
        composable(
            route = Screen.DailyCashClose.route,
            arguments = listOf(
                navArgument("branchId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val bId = backStackEntry.stringArg("branchId")
            val date = backStackEntry.stringArg("date")
            RequirePermission(state.user, Permission.MANAGE_DAILY_CASH_CLOSE,
                onBack = { navController.popBackStack() }) {
                DailyCashCloseScreen(
                    branchIdArg = bId,
                    closeDateArg = date,
                    onNavigateBack = { navController.popBackStack() },
                    onClosed = { /* permanece en pantalla mostrando reporte */ },
                    authViewModel = viewModel,
                    viewModel = dailyCashCloseViewModel
                )
            }
        }

        // ── Cuadre sorpresa (owner) ──────────────────────────────────────────
        composable(Screen.SpotCashCheckList.route) {
            RequirePermission(state.user, Permission.SPOT_CHECK_CASH,
                onBack = { navController.popBackStack() }) {
                SpotCashCheckListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onStartCheckForSession = { sid ->
                        navController.navigate(Screen.SpotCashCheck.route(sid))
                    },
                    authViewModel = viewModel,
                    viewModel = spotCashCheckViewModel
                )
            }
        }
        composable(
            route = Screen.SpotCashCheck.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.stringArg("sessionId") ?: return@composable
            RequirePermission(state.user, Permission.SPOT_CHECK_CASH,
                onBack = { navController.popBackStack() }) {
                SpotCashCheckScreen(
                    sessionId = sessionId,
                    onNavigateBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = spotCashCheckViewModel
                )
            }
        }

        // ── Reportes ─────────────────────────────────────────────────────────
        composable(Screen.SalesReport.route) {
            RequirePermission(state.user, Permission.VIEW_SALES_REPORT,
                onBack = { navController.popBackStack() }) {
                SalesReportScreen(
                    user = state.user,
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = salesReportViewModel
                )
            }
        }
        composable(Screen.ProductionReport.route) {
            RequirePermission(state.user, Permission.VIEW_PRODUCTION_REPORT,
                onBack = { navController.popBackStack() }) {
                ProductionReportScreen(
                    user = state.user,
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = productionReportViewModel
                )
            }
        }
        composable(Screen.FinancialReport.route) {
            RequirePermission(state.user, Permission.VIEW_FINANCIAL_REPORT,
                onBack = { navController.popBackStack() }) {
                FinancialReportScreen(
                    user = state.user,
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = financialReportViewModel
                )
            }
        }
        composable(Screen.BranchSupplyReport.route) {
            RequirePermission(state.user, Permission.VIEW_BRANCH_SUPPLY_REPORT,
                onBack = { navController.popBackStack() }) {
                BranchSupplyReportScreen(
                    user = state.user,
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = branchSupplyReportViewModel,
                    productionViewModel = productionViewModel
                )
            }
        }

        // ── Pedidos de clientes ──────────────────────────────────────────────
        composable(Screen.CustomerOrderList.route) {
            RequirePermission(state.user, Permission.VIEW_CUSTOMER_ORDERS,
                onBack = { navController.popBackStack() }) {
                CustomerOrderListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToForm = { id ->
                        navController.navigate(Screen.CustomerOrderForm.route(id))
                    },
                    onNavigateToDetail = { id ->
                        navController.navigate(Screen.CustomerOrderDetail.route(id))
                    },
                    onNavigateToCustomers = {
                        navController.navigate(Screen.ExternalCustomerList.route)
                    },
                    authViewModel = viewModel,
                    viewModel = customerOrderViewModel
                )
            }
        }
        composable(
            route = Screen.CustomerOrderForm.route,
            arguments = listOf(navArgument("orderId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) { backStackEntry ->
            val orderId = backStackEntry.savedStateHandle.get<String>("orderId")
            // RESELLER intencionalmente fuera: los revendedores crean sus pedidos
            // desde su propia app (ResellerQuickOrderScreen). Este form solo permite
            // encargos de cliente final.
            RequirePermission(state.user,
                Permission.CREATE_RETAIL_SPECIAL_ORDER, Permission.CREATE_CAKE_ORDER,
                Permission.MANAGE_CUSTOMER_ORDERS,
                onBack = { navController.popBackStack() }) {
                CustomerOrderFormScreen(
                    orderId = orderId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = customerOrderViewModel
                )
            }
        }
        composable(
            route = Screen.CustomerOrderDetail.route,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orderId = backStackEntry.savedStateHandle.get<String>("orderId") ?: ""
            RequirePermission(state.user, Permission.VIEW_CUSTOMER_ORDERS,
                onBack = { navController.popBackStack() }) {
                CustomerOrderDetailScreen(
                    orderId = orderId,
                    onNavigateBack = { navController.popBackStack() },
                    onEdit = { id ->
                        navController.navigate(Screen.CustomerOrderForm.route(id))
                    },
                    authViewModel = viewModel,
                    viewModel = customerOrderViewModel
                )
            }
        }
        composable(Screen.ResellerApproval.route) {
            RequirePermission(state.user, Permission.MANAGE_EXTERNAL_CUSTOMERS,
                onBack = { navController.popBackStack() }) {
                ResellerApprovalScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPriceListAdmin = {
                        navController.navigate(Screen.PriceListAdmin.route)
                    },
                    authViewModel = viewModel,
                    viewModel = externalCustomerViewModel
                )
            }
        }
        composable(Screen.ExternalCustomerList.route) {
            RequirePermission(state.user, Permission.MANAGE_EXTERNAL_CUSTOMERS,
                onBack = { navController.popBackStack() }) {
                ExternalCustomerListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToForm = { id ->
                        navController.navigate(Screen.ExternalCustomerForm.route(id))
                    },
                    authViewModel = viewModel,
                    viewModel = externalCustomerViewModel
                )
            }
        }
        composable(Screen.OrganizationSettings.route) {
            // OWNER es implícitamente permitido; gate por MANAGE_USERS para que
            // un admin con permisos amplios también pueda configurar.
            RequirePermission(state.user, Permission.MANAGE_USERS,
                onBack = { navController.popBackStack() }) {
                OrganizationSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = organizationSettingsViewModel
                )
            }
        }
        // Solo el dueño: el NIT y las credenciales de Digifact son del negocio, no de la
        // operación diaria. La Cloud Function exige lo mismo al guardar.
        composable(
            route = Screen.CashAudit.route,
            arguments = listOf(navArgument("branchId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { entry ->
            RequirePermission(state.user, Permission.VIEW_CASH_AUDIT, onBack = { navController.popBackStack() }) {
                com.bakery.erp.presentation.cashaudit.CashAuditScreen(
                    initialBranchId = entry.savedStateHandle.get<String>("branchId"),
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = cashAuditViewModel
                )
            }
        }
        composable(Screen.FelSetup.route) {
            RequireOwner(state.user, onBack = { navController.popBackStack() }) {
                FelSetupScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenProfile = { id -> navController.navigate(Screen.FelProfileForm.route(id)) },
                    authViewModel = viewModel,
                    viewModel = felViewModel
                )
            }
        }
        composable(
            route = Screen.FelProfileForm.route,
            arguments = listOf(navArgument("profileId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) { backStackEntry ->
            val profileId = backStackEntry.savedStateHandle.get<String>("profileId")
            RequireOwner(state.user, onBack = { navController.popBackStack() }) {
                FelProfileFormScreen(
                    profileId = profileId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = felViewModel
                )
            }
        }
        composable(
            route = Screen.ExternalCustomerForm.route,
            arguments = listOf(navArgument("customerId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) { backStackEntry ->
            val customerId = backStackEntry.savedStateHandle.get<String>("customerId")
            RequirePermission(state.user, Permission.MANAGE_EXTERNAL_CUSTOMERS,
                onBack = { navController.popBackStack() }) {
                ExternalCustomerFormScreen(
                    customerId = customerId,
                    onNavigateBack = { navController.popBackStack() },
                    authViewModel = viewModel,
                    viewModel = externalCustomerViewModel
                )
            }
        }
    }

        if (showSplash) {
            SplashScreen(onFinished = { showSplash = false })
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
private fun RequireOwner(
    user: User?,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    when {
        user == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        user.role == com.bakery.erp.model.UserRole.OWNER -> content()
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
