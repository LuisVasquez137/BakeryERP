package com.bakery.erp.di

import com.bakery.erp.auth.AndroidGoogleAuthHandler
import com.bakery.erp.data.AndroidAssetImageUploader
import com.bakery.erp.data.AndroidBluetoothPrinterManager
import com.bakery.erp.data.AndroidReceiptPrinter
import com.bakery.erp.data.AndroidRecipeImageUploader
import com.bakery.erp.data.FirebaseEmployeeDocumentRepository
import com.bakery.erp.data.FirebaseProductRepository
import com.bakery.erp.domain.AssetImageUploader
import com.bakery.erp.domain.BluetoothPrinterManager
import com.bakery.erp.domain.EmployeeDocumentRepository
import com.bakery.erp.domain.GoogleAuthHandler
import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.domain.ReceiptPrinter
import com.bakery.erp.domain.RecipeImageUploader
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.storage.storage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidModule = module {
    single<GoogleAuthHandler> { AndroidGoogleAuthHandler(androidContext()) }
    single { Firebase.storage }
    single<ProductRepository> { FirebaseProductRepository(get(), get()) }
    single<RecipeImageUploader> { AndroidRecipeImageUploader(get()) }
    single<AssetImageUploader> { AndroidAssetImageUploader(get()) }
    single<EmployeeDocumentRepository> { FirebaseEmployeeDocumentRepository(get(), get()) }
    single<BluetoothPrinterManager> { AndroidBluetoothPrinterManager(androidContext()) }
    single<ReceiptPrinter> { AndroidReceiptPrinter(androidContext(), get()) }
}
