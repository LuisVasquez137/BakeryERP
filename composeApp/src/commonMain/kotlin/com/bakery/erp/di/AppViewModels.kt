package com.bakery.erp.di

import com.bakery.erp.presentation.assets.FixedAssetViewModel
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.auth.ResellerSetupViewModel
import com.bakery.erp.presentation.branches.BranchViewModel
import com.bakery.erp.presentation.customerOrders.CustomerOrderViewModel
import com.bakery.erp.presentation.customerOrders.ExternalCustomerViewModel
import com.bakery.erp.presentation.employees.EmployeeViewModel
import com.bakery.erp.presentation.organization.OrganizationSettingsViewModel
import com.bakery.erp.presentation.reseller.ResellerViewModel
import com.bakery.erp.presentation.employees.JobPositionViewModel
import com.bakery.erp.presentation.expenses.ExpenseViewModel
import com.bakery.erp.presentation.expenses.FixedBudgetViewModel
import com.bakery.erp.presentation.ingredients.IngredientViewModel
import com.bakery.erp.presentation.preparationbatches.PreparationBatchViewModel
import com.bakery.erp.presentation.delivery.DeliveryViewModel
import com.bakery.erp.presentation.dispatch.DispatchViewModel
import com.bakery.erp.presentation.finishedStock.BreadCountViewModel
import com.bakery.erp.presentation.finishedStock.FinishedStockViewModel
import com.bakery.erp.presentation.home.HomeViewModel
import com.bakery.erp.presentation.notifications.NotificationViewModel
import com.bakery.erp.presentation.pos.CashSessionViewModel
import com.bakery.erp.presentation.reports.financial.FinancialReportViewModel
import com.bakery.erp.presentation.reports.production.ProductionReportViewModel
import com.bakery.erp.presentation.reports.sales.SalesReportViewModel
import com.bakery.erp.presentation.reports.branchsupply.BranchSupplyReportViewModel
import com.bakery.erp.presentation.pos.DailyCashCloseViewModel
import com.bakery.erp.presentation.pos.POSViewModel
import com.bakery.erp.presentation.pos.SaleHistoryViewModel
import com.bakery.erp.presentation.pos.SpotCashCheckViewModel
import com.bakery.erp.presentation.pos.admin.CashRegisterAdminViewModel
import com.bakery.erp.domain.BluetoothPrinterManager
import com.bakery.erp.domain.DocumentPrinter
import com.bakery.erp.presentation.pos.admin.POSCategoryAdminViewModel
import com.bakery.erp.presentation.preparations.PreparationViewModel
import com.bakery.erp.presentation.plantissues.PlantIssueViewModel
import com.bakery.erp.presentation.pricing.PriceListViewModel
import com.bakery.erp.presentation.production.ProductionViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.presentation.purchases.PurchaseViewModel
import com.bakery.erp.presentation.recipes.RecipeViewModel
import com.bakery.erp.presentation.suppliers.SupplierViewModel
import com.bakery.erp.presentation.users.UserViewModel

data class AppViewModels(
    val auth: AuthViewModel,
    val product: ProductViewModel,
    val ingredient: IngredientViewModel,
    val supplier: SupplierViewModel,
    val recipe: RecipeViewModel,
    val branch: BranchViewModel,
    val expense: ExpenseViewModel,
    val fixedBudget: FixedBudgetViewModel,
    val user: UserViewModel,
    val purchase: PurchaseViewModel,
    val production: ProductionViewModel,
    /** Vales de salida de material de planta (ver PlantIssue). */
    val plantIssue: PlantIssueViewModel,
    val employee: EmployeeViewModel,
    /** Pestaña "Acceso" del colaborador: PIN y permisos. */
    val employeeAccess: com.bakery.erp.presentation.employees.EmployeeAccessViewModel,
    val jobPosition: JobPositionViewModel,
    val preparation: PreparationViewModel,
    val preparationBatch: PreparationBatchViewModel,
    val fixedAsset: FixedAssetViewModel,
    val priceList: PriceListViewModel,
    val dispatch: DispatchViewModel,
    val delivery: DeliveryViewModel,
    val finishedStock: FinishedStockViewModel,
    val pos: POSViewModel,
    val cashSession: CashSessionViewModel,
    val saleHistory: SaleHistoryViewModel,
    val cashRegisterAdmin: CashRegisterAdminViewModel,
    val posCategoryAdmin: POSCategoryAdminViewModel,
    val credit: com.bakery.erp.presentation.credit.CreditViewModel,
    val breadCount: BreadCountViewModel,
    val dailyCashClose: DailyCashCloseViewModel,
    val spotCashCheck: SpotCashCheckViewModel,
    val home: HomeViewModel,
    val salesReport: SalesReportViewModel,
    val productionReport: ProductionReportViewModel,
    val financialReport: FinancialReportViewModel,
    val branchSupplyReport: BranchSupplyReportViewModel,
    val customerOrder: CustomerOrderViewModel,
    val externalCustomer: ExternalCustomerViewModel,
    val organizationSettings: OrganizationSettingsViewModel,
    /** Bitácora de cajas por sucursal. */
    val cashAudit: com.bakery.erp.presentation.cashaudit.CashAuditViewModel,
    /** Facturación electrónica: NITs emisores y sucursales que facturan. */
    val fel: com.bakery.erp.presentation.fel.FelViewModel,
    val resellerSetup: ResellerSetupViewModel,
    val reseller: ResellerViewModel,
    val notification: NotificationViewModel,
    val bluetoothPrinterManager: BluetoothPrinterManager,
    /** Impresión de documentos (orden de producción, tickets). */
    val documentPrinter: DocumentPrinter
)

expect fun createAppViewModels(): AppViewModels
