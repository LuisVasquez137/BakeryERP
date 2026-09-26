package com.bakery.erp.di

import com.bakery.erp.auth.GoogleAuthHandlerStub
import com.bakery.erp.data.FirebaseAssetHistoryRepository
import com.bakery.erp.data.FirebaseAttendanceRepository
import com.bakery.erp.data.FirebaseFixedAssetRepository
import com.bakery.erp.data.FirebaseAuthRepository
import com.bakery.erp.data.FirebaseBranchRepository
import com.bakery.erp.data.OpenMeteoWeatherRepository
import com.bakery.erp.data.FirebaseEmployeeRepository
import com.bakery.erp.data.FirebaseEmployeeHistoryRepository
import com.bakery.erp.data.FirebaseExpenseRepository
import com.bakery.erp.data.FirebaseFixedBudgetRepository
import com.bakery.erp.data.FirebaseIngredientRepository
import com.bakery.erp.data.FirebasePlantIssueRepository
import com.bakery.erp.data.FirebaseJobPositionRepository
import com.bakery.erp.data.FirebasePayrollRepository
import com.bakery.erp.data.FirebaseCashRegisterRepository
import com.bakery.erp.data.FirebaseCashSessionRepository
import com.bakery.erp.data.FirebaseCustomerOrderRepository
import com.bakery.erp.data.FirebaseExternalCustomerRepository
import com.bakery.erp.data.FirebaseDeliveryRepository
import com.bakery.erp.data.FirebaseDispatchRepository
import com.bakery.erp.data.FirebaseBreadCountRepository
import com.bakery.erp.data.FirebaseDailyCashCloseRepository
import com.bakery.erp.data.FirebaseFinishedStockRepository
import com.bakery.erp.data.FirebaseNotificationRepository
import com.bakery.erp.data.FirebasePOSCategoryRepository
import com.bakery.erp.data.FirebaseSaleRepository
import com.bakery.erp.data.FirebaseSpotCashCheckRepository
import com.bakery.erp.data.FirebasePreparationBatchRepository
import com.bakery.erp.data.FirebasePreparationRepository
import com.bakery.erp.data.FirebasePriceListRepository
import com.bakery.erp.data.FirebaseProductionRepository
import com.bakery.erp.data.FirebasePurchaseConceptRepository
import com.bakery.erp.data.FirebasePurchaseRepository
import com.bakery.erp.data.FirebaseRecipeRepository
import com.bakery.erp.data.FirebaseSupplierRepository
import com.bakery.erp.data.NoOpEmployeeDocumentRepository
import com.bakery.erp.data.NoOpProductRepository
import com.bakery.erp.data.NoOpRecipeImageUploader
import com.bakery.erp.domain.BluetoothPrinterManager
import com.bakery.erp.domain.NoOpBluetoothPrinterManager
import com.bakery.erp.domain.DocumentPrinter
import com.bakery.erp.domain.NoOpDocumentPrinter
import com.bakery.erp.domain.NoOpReceiptPrinter
import com.bakery.erp.domain.ReceiptPrinter
import com.bakery.erp.domain.AssetHistoryRepository
import com.bakery.erp.domain.AssetImageUploader
import com.bakery.erp.domain.FixedAssetRepository
import com.bakery.erp.domain.NoOpAssetImageUploader
import com.bakery.erp.domain.NoOpSpecialOrderImageUploader
import com.bakery.erp.domain.RecipeImageUploader
import com.bakery.erp.domain.NoOpPurchaseInvoiceImageUploader
import com.bakery.erp.domain.PurchaseInvoiceImageUploader
import com.bakery.erp.domain.SpecialOrderImageUploader
import com.bakery.erp.domain.AttendanceRepository
import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.WeatherRepository
import com.bakery.erp.domain.EmployeeDocumentRepository
import com.bakery.erp.domain.EmployeeHistoryRepository
import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.domain.ExpenseRepository
import com.bakery.erp.domain.FixedBudgetRepository
import com.bakery.erp.domain.GoogleAuthHandler
import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.PlantIssueRepository
import com.bakery.erp.domain.JobPositionRepository
import com.bakery.erp.domain.PayrollRepository
import com.bakery.erp.domain.CashRegisterRepository
import com.bakery.erp.domain.CashSessionRepository
import com.bakery.erp.domain.CustomerOrderRepository
import com.bakery.erp.domain.ExternalCustomerRepository
import com.bakery.erp.domain.DeliveryRepository
import com.bakery.erp.domain.DispatchRepository
import com.bakery.erp.domain.BreadCountRepository
import com.bakery.erp.domain.DailyCashCloseRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.domain.NotificationRepository
import com.bakery.erp.domain.NoOpPushTokenProvider
import com.bakery.erp.domain.PushTokenProvider
import com.bakery.erp.domain.POSCategoryRepository
import com.bakery.erp.domain.SaleRepository
import com.bakery.erp.domain.SpotCashCheckRepository
import com.bakery.erp.domain.PreparationBatchRepository
import com.bakery.erp.domain.PreparationRepository
import com.bakery.erp.domain.PriceListRepository
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.domain.PurchaseConceptRepository
import com.bakery.erp.domain.PurchaseRepository
import com.bakery.erp.domain.RecipeRepository
import com.bakery.erp.domain.SupplierRepository
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
import com.bakery.erp.presentation.plantissues.PlantIssueViewModel
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
import com.bakery.erp.presentation.pos.admin.POSCategoryAdminViewModel
import com.bakery.erp.presentation.preparations.PreparationViewModel
import com.bakery.erp.presentation.pricing.PriceListViewModel
import com.bakery.erp.presentation.production.ProductionViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.presentation.purchases.PurchaseViewModel
import com.bakery.erp.presentation.recipes.RecipeViewModel
import com.bakery.erp.presentation.suppliers.SupplierViewModel
import com.bakery.erp.presentation.users.UserViewModel
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import org.koin.dsl.module

val appModule = module {
    single { Firebase.auth }
    single { Firebase.firestore }
    single<AuthRepository> { FirebaseAuthRepository(get(), get()) }
    single<IngredientRepository> { FirebaseIngredientRepository(get()) }
    single<PlantIssueRepository> { FirebasePlantIssueRepository(get()) }
    single<SupplierRepository> { FirebaseSupplierRepository(get()) }
    single<RecipeRepository> { FirebaseRecipeRepository(get()) }
    single<BranchRepository> { FirebaseBranchRepository(get()) }
    single<com.bakery.erp.domain.FelRepository> { com.bakery.erp.data.FirebaseFelRepository(get()) }
    single<WeatherRepository> { OpenMeteoWeatherRepository() }
    single<PurchaseRepository> { FirebasePurchaseRepository(get()) }
    single<PurchaseConceptRepository> { FirebasePurchaseConceptRepository(get()) }
    single<ProductionRepository> { FirebaseProductionRepository(get()) }
    single<PreparationRepository> { FirebasePreparationRepository(get()) }
    single<PreparationBatchRepository> { FirebasePreparationBatchRepository(get()) }
    single<EmployeeRepository> { FirebaseEmployeeRepository(get()) }
    single<AttendanceRepository> { FirebaseAttendanceRepository(get()) }
    single<PayrollRepository> { FirebasePayrollRepository(get()) }
    single<JobPositionRepository> { FirebaseJobPositionRepository(get()) }
    single<EmployeeHistoryRepository> { FirebaseEmployeeHistoryRepository(get()) }
    single<ExpenseRepository> { FirebaseExpenseRepository(get()) }
    single<FixedBudgetRepository> { FirebaseFixedBudgetRepository(get()) }
    single<FixedAssetRepository> { FirebaseFixedAssetRepository(get()) }
    single<AssetHistoryRepository> { FirebaseAssetHistoryRepository(get()) }
    single<PriceListRepository> { FirebasePriceListRepository(get()) }
    single<FinishedStockRepository> { FirebaseFinishedStockRepository(get()) }
    single<NotificationRepository> { FirebaseNotificationRepository(get()) }
    // Android lo sobreescribe con AndroidPushTokenProvider (FCM); iOS todavía no
    // tiene APNs configurado y se queda con el NoOp.
    single<PushTokenProvider> { NoOpPushTokenProvider() }
    single<DispatchRepository> { FirebaseDispatchRepository(get(), get(), get(), get()) }
    single<DeliveryRepository> { FirebaseDeliveryRepository(get(), get()) }
    single<POSCategoryRepository> { FirebasePOSCategoryRepository(get()) }
    single<CashRegisterRepository> { FirebaseCashRegisterRepository(get()) }
    single<CashSessionRepository> { FirebaseCashSessionRepository(get()) }
    single<SaleRepository> { FirebaseSaleRepository(get(), get(), get(), get()) }
    single<BreadCountRepository> { FirebaseBreadCountRepository(get(), get(), get(), get()) }
    single<com.bakery.erp.domain.CreditRepository> { com.bakery.erp.data.FirebaseCreditRepository(get()) }
    single { com.bakery.erp.presentation.credit.CreditViewModel(get(), get()) }
    single<DailyCashCloseRepository> { FirebaseDailyCashCloseRepository(get(), get()) }
    single<SpotCashCheckRepository> { FirebaseSpotCashCheckRepository(get()) }
    single<ExternalCustomerRepository> { FirebaseExternalCustomerRepository(get()) }
    single<CustomerOrderRepository> { FirebaseCustomerOrderRepository(get()) }
    single<GoogleAuthHandler> { GoogleAuthHandlerStub() }
    single<ProductRepository> { NoOpProductRepository() }
    single<RecipeImageUploader> { NoOpRecipeImageUploader() }
    single<AssetImageUploader> { NoOpAssetImageUploader() }
    single<SpecialOrderImageUploader> { NoOpSpecialOrderImageUploader() }
    single<PurchaseInvoiceImageUploader> { NoOpPurchaseInvoiceImageUploader() }
    single<EmployeeDocumentRepository> { NoOpEmployeeDocumentRepository() }
    single<ReceiptPrinter> { NoOpReceiptPrinter() }
    // Android lo sobreescribe con AndroidDocumentPrinter; iOS con IosDocumentPrinter.
    single<DocumentPrinter> { NoOpDocumentPrinter() }
    single<BluetoothPrinterManager> { NoOpBluetoothPrinterManager() }
    single { AuthViewModel(get(), get()) }
    single { ProductViewModel(get(), get()) }
    single { PriceListViewModel(get()) }
    single { IngredientViewModel(get(), get(), get()) }
    single { PlantIssueViewModel(get(), get(), get()) }
    single { PurchaseViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { ProductionViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { NotificationViewModel(get(), get()) }
    single { DispatchViewModel(get(), get(), get(), get(), get(), get()) }
    single { DeliveryViewModel(get(), get(), get(), get(), get(), get()) }
    single { FinishedStockViewModel(get(), get(), get(), get()) }
    single { SupplierViewModel(get(), get()) }
    single { RecipeViewModel(get(), get()) }
    single { BranchViewModel(get()) }
    single { ExpenseViewModel(get()) }
    single { FixedBudgetViewModel(get()) }
    single { EmployeeViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { JobPositionViewModel(get()) }
    single { UserViewModel(get()) }
    single { PreparationViewModel(get()) }
    single { PreparationBatchViewModel(get(), get(), get(), get()) }
    single { FixedAssetViewModel(get(), get(), get()) }
    single { POSViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { CashSessionViewModel(get(), get(), get(), get(), get()) }
    single { SaleHistoryViewModel(get(), get(), get(), get()) }
    single { CashRegisterAdminViewModel(get(), get(), get()) }
    single { POSCategoryAdminViewModel(get(), get(), get()) }
    single { BreadCountViewModel(get(), get()) }
    single { DailyCashCloseViewModel(get(), get(), get(), get()) }
    single { SpotCashCheckViewModel(get(), get(), get()) }
    single { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { SalesReportViewModel(get(), get()) }
    single { ProductionReportViewModel(get(), get(), get(), get()) }
    single { FinancialReportViewModel(get(), get(), get(), get(), get(), get(), get()) }
    single { BranchSupplyReportViewModel(get(), get(), get(), get()) }
    single { ExternalCustomerViewModel(get(), get(), get(), get()) }
    single { CustomerOrderViewModel(get(), get(), get(), get(), get(), get()) }
    single { OrganizationSettingsViewModel(get(), get()) }
    single { com.bakery.erp.presentation.fel.FelViewModel(get(), get()) }
    single { ResellerSetupViewModel(get(), get()) }
    single { ResellerViewModel(get(), get(), get(), get(), get()) }
}
