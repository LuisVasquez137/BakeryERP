package com.bakery.erp.di

import com.bakery.erp.auth.GoogleAuthHandlerJs
import com.bakery.erp.data.FirebaseAssetHistoryRepository
import com.bakery.erp.data.FirebaseAttendanceRepository
import com.bakery.erp.data.FirebaseAuthRepository
import com.bakery.erp.data.FirebaseFixedAssetRepository
import com.bakery.erp.data.JsAssetImageUploader
import com.bakery.erp.data.FirebaseBranchRepository
import com.bakery.erp.data.OpenMeteoWeatherRepository
import com.bakery.erp.data.FirebaseEmployeeHistoryRepository
import com.bakery.erp.data.FirebaseEmployeeRepository
import com.bakery.erp.data.FirebaseExpenseRepository
import com.bakery.erp.data.FirebaseFixedBudgetRepository
import com.bakery.erp.data.FirebaseIngredientRepository
import com.bakery.erp.data.FirebasePlantIssueRepository
import com.bakery.erp.data.FirebaseJobPositionRepository
import com.bakery.erp.data.FirebasePayrollRepository
import com.bakery.erp.data.FirebasePreparationBatchRepository
import com.bakery.erp.data.FirebasePreparationRepository
import com.bakery.erp.data.FirebaseProductionRepository
import com.bakery.erp.data.FirebasePurchaseConceptRepository
import com.bakery.erp.data.FirebasePurchaseRepository
import com.bakery.erp.data.FirebaseRecipeRepository
import com.bakery.erp.data.FirebaseSupplierRepository
import com.bakery.erp.data.FirebaseCashRegisterRepository
import com.bakery.erp.data.FirebaseCashSessionRepository
import com.bakery.erp.data.FirebaseCustomerOrderRepository
import com.bakery.erp.data.FirebaseExternalCustomerRepository
import com.bakery.erp.data.FirebaseDeliveryRepository
import com.bakery.erp.data.FirebaseBreadCountRepository
import com.bakery.erp.data.FirebaseDailyCashCloseRepository
import com.bakery.erp.data.FirebaseDispatchRepository
import com.bakery.erp.data.FirebaseEmployeeDocumentRepositoryJs
import com.bakery.erp.data.FirebaseFinishedStockRepository
import com.bakery.erp.data.FirebaseNotificationRepository
import com.bakery.erp.data.JsPushTokenProvider
import com.bakery.erp.WEB_PUSH_VAPID_KEY
import com.bakery.erp.data.FirebasePriceListRepository
import com.bakery.erp.data.FirebaseSpotCashCheckRepository
import com.bakery.erp.data.FirebaseProductRepositoryJs
import com.bakery.erp.data.FirebasePOSCategoryRepository
import com.bakery.erp.data.FirebaseSaleRepository
import com.bakery.erp.data.JsDocumentPrinter
import com.bakery.erp.data.JsReceiptPrinter
import com.bakery.erp.data.JsPurchaseInvoiceImageUploader
import com.bakery.erp.data.JsRecipeImageUploader
import com.bakery.erp.data.JsSpecialOrderImageUploader
import com.bakery.erp.domain.NoOpBluetoothPrinterManager
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
import dev.gitlive.firebase.storage.storage

actual fun createAppViewModels(): AppViewModels {
    val firestore = Firebase.firestore
    val auth = Firebase.auth
    val storage = Firebase.storage

    val authRepo = FirebaseAuthRepository(auth, firestore)
    val ingredientRepo = FirebaseIngredientRepository(firestore)
    val supplierRepo = FirebaseSupplierRepository(firestore)
    val recipeRepo = FirebaseRecipeRepository(firestore)
    val branchRepo = FirebaseBranchRepository(firestore)
    val felRepo = com.bakery.erp.data.FirebaseFelRepository(firestore)
    val purchaseRepo = FirebasePurchaseRepository(firestore)
    val purchaseConceptRepo = FirebasePurchaseConceptRepository(firestore)
    val productionRepo = FirebaseProductionRepository(firestore)
    val employeeRepo = FirebaseEmployeeRepository(firestore)
    val employeeAccessRepo = com.bakery.erp.data.FirebaseEmployeeAccessRepository()
    val creditRepo = com.bakery.erp.data.FirebaseCreditRepository(firestore)
    val attendanceRepo = FirebaseAttendanceRepository(firestore)
    val payrollRepo = FirebasePayrollRepository(firestore)
    val jobPositionRepo = FirebaseJobPositionRepository(firestore)
    val historyRepo = FirebaseEmployeeHistoryRepository(firestore)
    val expenseRepo = FirebaseExpenseRepository(firestore)
    val fixedBudgetRepo = FirebaseFixedBudgetRepository(firestore)
    val preparationRepo = FirebasePreparationRepository(firestore)
    val preparationBatchRepo = FirebasePreparationBatchRepository(firestore)
    val fixedAssetRepo = FirebaseFixedAssetRepository(firestore)
    val assetHistoryRepo = FirebaseAssetHistoryRepository(firestore)
    val priceListRepo = FirebasePriceListRepository(firestore)
    val finishedStockRepo = FirebaseFinishedStockRepository(firestore)
    val notificationRepo = FirebaseNotificationRepository(firestore)
    val cashAuditRepo = com.bakery.erp.data.FirebaseCashAuditRepository(firestore)
    val heldCartRepo = com.bakery.erp.data.FirebaseHeldCartRepository(firestore)
    val dispatchRepo = FirebaseDispatchRepository(firestore, productionRepo, priceListRepo, finishedStockRepo)
    val deliveryRepo = FirebaseDeliveryRepository(firestore, dispatchRepo)
    val posCategoryRepo = FirebasePOSCategoryRepository(firestore)
    val packagingItemRepo = com.bakery.erp.data.FirebasePackagingItemRepository(firestore)
    val paymentRulesRepo = com.bakery.erp.data.FirebasePaymentRulesRepository(firestore)
    val cashRegisterRepo = FirebaseCashRegisterRepository(firestore)
    val cashSessionRepo = FirebaseCashSessionRepository(firestore)
    val productRepoJs = FirebaseProductRepositoryJs(firestore, storage)
    val materialCostLoader = com.bakery.erp.presentation.reports.productcost.MaterialCostLoader(
        productRepoJs, recipeRepo, ingredientRepo, preparationRepo, purchaseRepo
    )
    val saleRepo = FirebaseSaleRepository(firestore, finishedStockRepo, cashSessionRepo, ingredientRepo)
    val breadCountRepo = FirebaseBreadCountRepository(firestore, finishedStockRepo, saleRepo, posCategoryRepo)
    val dailyCloseRepo = FirebaseDailyCashCloseRepository(firestore, cashSessionRepo)
    val spotCheckRepo = FirebaseSpotCashCheckRepository(firestore)
    val externalCustomerRepo = FirebaseExternalCustomerRepository(firestore)
    val customerOrderRepo = FirebaseCustomerOrderRepository(firestore)
    val weatherRepo = OpenMeteoWeatherRepository()
    val plantIssueRepo = FirebasePlantIssueRepository(firestore)

    return AppViewModels(
        auth = AuthViewModel(authRepo, GoogleAuthHandlerJs()),
        product = ProductViewModel(productRepoJs, priceListRepo),
        ingredient = IngredientViewModel(ingredientRepo, recipeRepo, branchRepo),
        supplier = SupplierViewModel(supplierRepo, purchaseRepo),
        recipe = RecipeViewModel(recipeRepo, JsRecipeImageUploader(storage)),
        branch = BranchViewModel(branchRepo),
        expense = ExpenseViewModel(expenseRepo),
        fixedBudget = FixedBudgetViewModel(fixedBudgetRepo, expenseRepo),
        user = UserViewModel(authRepo),
        purchase = PurchaseViewModel(
            purchaseRepo, ingredientRepo, supplierRepo, branchRepo, finishedStockRepo,
            productRepoJs, JsPurchaseInvoiceImageUploader(storage),
            expenseRepo, fixedAssetRepo, assetHistoryRepo, authRepo, cashSessionRepo,
            purchaseConceptRepo, priceListRepo
        ),
        production = ProductionViewModel(
            productionRepo, ingredientRepo, recipeRepo, branchRepo,
            preparationRepo, preparationBatchRepo, dispatchRepo, customerOrderRepo,
            finishedStockRepo, notificationRepo, weatherRepo,
            JsSpecialOrderImageUploader(storage)
        ),
        plantIssue = PlantIssueViewModel(plantIssueRepo, ingredientRepo, branchRepo),
        employee = EmployeeViewModel(
            employeeRepo, branchRepo, attendanceRepo, payrollRepo,
            jobPositionRepo, FirebaseEmployeeDocumentRepositoryJs(firestore, storage), authRepo, historyRepo,
            creditRepo,
            // El kiosko web no toma selfie: sus marcas quedan como "sin foto".
            com.bakery.erp.domain.NoOpAttendanceSelfieUploader()
        ),
        employeeAccess = com.bakery.erp.presentation.employees.EmployeeAccessViewModel(
            employeeRepo, employeeAccessRepo
        ),
        operatorLock = com.bakery.erp.presentation.operator.OperatorLockViewModel(employeeRepo, employeeAccessRepo),
        jobPosition = JobPositionViewModel(jobPositionRepo),
        preparation = PreparationViewModel(preparationRepo),
        preparationBatch = PreparationBatchViewModel(
            preparationBatchRepo, preparationRepo, ingredientRepo, branchRepo
        ),
        fixedAsset = FixedAssetViewModel(fixedAssetRepo, assetHistoryRepo, JsAssetImageUploader(storage)),
        priceList = PriceListViewModel(priceListRepo),
        dispatch = DispatchViewModel(dispatchRepo, deliveryRepo, branchRepo, productionRepo, notificationRepo, finishedStockRepo),
        delivery = DeliveryViewModel(deliveryRepo, dispatchRepo, authRepo, fixedAssetRepo, branchRepo, productionRepo),
        finishedStock = FinishedStockViewModel(finishedStockRepo, branchRepo, productRepoJs, ingredientRepo),
        pos = POSViewModel(
            saleRepo, posCategoryRepo, cashSessionRepo, productRepoJs,
            finishedStockRepo, ingredientRepo, priceListRepo, branchRepo,
            JsReceiptPrinter(), authRepo, creditRepo, felRepo,
            com.bakery.erp.data.FirebaseCustomerDepositRepository(firestore), customerOrderRepo,
            externalCustomerRepo, packagingItemRepo, paymentRulesRepo, heldCartRepo, cashAuditRepo
        ),
        cashSession = CashSessionViewModel(
            cashSessionRepo, cashRegisterRepo, branchRepo, dailyCloseRepo, purchaseRepo,
            cashAuditRepo, notificationRepo, employeeRepo, saleRepo, employeeAccessRepo, heldCartRepo,
            com.bakery.erp.data.FirebaseBankDepositRepository(firestore),
            com.bakery.erp.data.JsBankSlipImageUploader(storage)
        ),
        saleHistory = SaleHistoryViewModel(saleRepo, cashSessionRepo, branchRepo, felRepo, JsReceiptPrinter(), authRepo, notificationRepo, cashAuditRepo),
        counterRemoval = com.bakery.erp.presentation.pos.CounterRemovalViewModel(
            com.bakery.erp.data.FirebaseCounterRemovalRepository(firestore, finishedStockRepo, creditRepo),
            com.bakery.erp.data.JsCounterRemovalPhotoUploader(storage),
            employeeRepo, employeeAccessRepo, notificationRepo, branchRepo
        ),
        stockTransfer = com.bakery.erp.presentation.transfers.StockTransferViewModel(
            com.bakery.erp.data.FirebaseStockTransferRepository(firestore, finishedStockRepo, ingredientRepo),
            branchRepo, productRepoJs, ingredientRepo, finishedStockRepo, notificationRepo
        ),
        cashRegisterAdmin = CashRegisterAdminViewModel(cashRegisterRepo, branchRepo, cashSessionRepo),
        posCategoryAdmin = POSCategoryAdminViewModel(posCategoryRepo, productRepoJs, branchRepo),
        packagingAdmin = com.bakery.erp.presentation.pos.admin.PackagingAdminViewModel(packagingItemRepo),
        cardRules = com.bakery.erp.presentation.pos.admin.CardRulesViewModel(paymentRulesRepo),
        credit = com.bakery.erp.presentation.credit.CreditViewModel(creditRepo, employeeRepo),
        breadCount = BreadCountViewModel(breadCountRepo, branchRepo),
        dailyCashClose = DailyCashCloseViewModel(dailyCloseRepo, branchRepo, breadCountRepo, spotCheckRepo),
        spotCashCheck = SpotCashCheckViewModel(spotCheckRepo, cashSessionRepo, branchRepo),
        home = HomeViewModel(
            authRepo, saleRepo, productionRepo, branchRepo,
            ingredientRepo, fixedAssetRepo, dispatchRepo, cashSessionRepo, purchaseRepo, creditRepo
        ),
        salesReport = SalesReportViewModel(saleRepo, branchRepo),
        productionReport = ProductionReportViewModel(productionRepo, dispatchRepo, branchRepo, plantIssueRepo),
        financialReport = FinancialReportViewModel(
            saleRepo, expenseRepo, payrollRepo, purchaseRepo,
            finishedStockRepo, breadCountRepo, branchRepo
        ),
        branchSupplyReport = BranchSupplyReportViewModel(productionRepo, dispatchRepo, saleRepo, branchRepo),
        productCost = com.bakery.erp.presentation.reports.productcost.ProductCostViewModel(materialCostLoader),
        standardCost = com.bakery.erp.presentation.reports.productcost.StandardCostViewModel(
            materialCostLoader, branchRepo, expenseRepo, employeeRepo, payrollRepo, productionRepo,
            com.bakery.erp.data.FirebaseStandardCostRepository(firestore)
        ),
        deliveryCost = com.bakery.erp.presentation.reports.productcost.DeliveryCostViewModel(
            expenseRepo, employeeRepo, payrollRepo, dispatchRepo
        ),
        branchResult = com.bakery.erp.presentation.reports.productcost.BranchResultViewModel(
            branchRepo, saleRepo, dispatchRepo, purchaseRepo, expenseRepo, employeeRepo, payrollRepo,
            com.bakery.erp.data.FirebaseStandardCostRepository(firestore),
            com.bakery.erp.data.FirebaseStockTransferRepository(firestore, finishedStockRepo, ingredientRepo)
        ),
        customerOrder = CustomerOrderViewModel(
            customerOrderRepo, externalCustomerRepo, productionRepo,
            productRepoJs, branchRepo, priceListRepo
        ),
        externalCustomer = ExternalCustomerViewModel(
            externalCustomerRepo, priceListRepo, branchRepo, productionRepo
        ),
        organizationSettings = OrganizationSettingsViewModel(authRepo, saleRepo),
        cashAudit = com.bakery.erp.presentation.cashaudit.CashAuditViewModel(cashAuditRepo, branchRepo),
        fel = com.bakery.erp.presentation.fel.FelViewModel(
            felRepo, branchRepo
        ),
        resellerSetup = ResellerSetupViewModel(authRepo, branchRepo),
        reseller = ResellerViewModel(
            externalCustomerRepo, customerOrderRepo, productRepoJs, priceListRepo, branchRepo
        ),
        // Con WEB_PUSH_VAPID_KEY vacía, JsPushTokenProvider no registra token y
        // la web se queda solo con la campana in-app.
        notification = NotificationViewModel(
            notificationRepo, JsPushTokenProvider(WEB_PUSH_VAPID_KEY)
        ),
        bluetoothPrinterManager = NoOpBluetoothPrinterManager(),
        documentPrinter = JsDocumentPrinter()
    )
}
