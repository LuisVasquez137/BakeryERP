package com.bakery.erp.di

import org.koin.core.context.GlobalContext

actual fun createAppViewModels(): AppViewModels {
    val koin = GlobalContext.get()
    return AppViewModels(
        auth = koin.get(),
        product = koin.get(),
        ingredient = koin.get(),
        supplier = koin.get(),
        recipe = koin.get(),
        branch = koin.get(),
        expense = koin.get(),
        fixedBudget = koin.get(),
        user = koin.get(),
        purchase = koin.get(),
        production = koin.get(),
        plantIssue = koin.get(),
        employee = koin.get(),
        jobPosition = koin.get(),
        preparation = koin.get(),
        preparationBatch = koin.get(),
        fixedAsset = koin.get(),
        priceList = koin.get(),
        dispatch = koin.get(),
        delivery = koin.get(),
        finishedStock = koin.get(),
        pos = koin.get(),
        cashSession = koin.get(),
        saleHistory = koin.get(),
        cashRegisterAdmin = koin.get(),
        posCategoryAdmin = koin.get(),
        credit = koin.get(),
        breadCount = koin.get(),
        dailyCashClose = koin.get(),
        spotCashCheck = koin.get(),
        home = koin.get(),
        salesReport = koin.get(),
        productionReport = koin.get(),
        financialReport = koin.get(),
        branchSupplyReport = koin.get(),
        customerOrder = koin.get(),
        externalCustomer = koin.get(),
        organizationSettings = koin.get(),
        resellerSetup = koin.get(),
        reseller = koin.get(),
        notification = koin.get(),
        bluetoothPrinterManager = koin.get(),
        documentPrinter = koin.get()
    )
}
