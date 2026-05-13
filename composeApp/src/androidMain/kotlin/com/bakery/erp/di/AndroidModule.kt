package com.bakery.erp.di

import com.bakery.erp.auth.AndroidGoogleAuthHandler
import com.bakery.erp.data.FirebaseProductRepository
import com.bakery.erp.domain.GoogleAuthHandler
import com.bakery.erp.domain.ProductRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.storage.storage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidModule = module {
    single<GoogleAuthHandler> { AndroidGoogleAuthHandler(androidContext()) }
    single { Firebase.storage }
    single<ProductRepository> { FirebaseProductRepository(get(), get()) }
}
