package com.bakery.erp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.bakery.erp.data.CurrentActivityHolder
import com.bakery.erp.data.createNotificationChannels
import com.bakery.erp.di.androidModule
import com.bakery.erp.di.appModule
import com.bakery.erp.di.initializePlatform
import com.bakery.erp.ui.LocalPreferences
import com.bakery.erp.ui.initKneadingAlarmSound
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BakeryApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        LocalPreferences.init(this)
        initKneadingAlarmSound(this)
        initializePlatform()
        // Antes que nada: si el canal no existe cuando llega un push, Android lo
        // descarta sin avisar.
        createNotificationChannels(this)
        CurrentActivityHolder.registerWith(this)
        startKoin {
            androidContext(this@BakeryApp)
            // androidModule va DESPUÉS para sobreescribir el GoogleAuthHandler stub
            modules(appModule, androidModule)
        }
    }

    /**
     * Cargador de imágenes de toda la app (fotos de productos, recetas, activos).
     *
     * `respectCacheHeaders(false)` es lo que hace que la segunda vez sea instantánea: las URLs
     * de Firebase Storage responden `Cache-Control: private, max-age=0`, y con el valor por
     * defecto Coil volvía a preguntarle al servidor por CADA foto cada vez que se abría el POS,
     * aunque ya la tuviera en disco. La URL lleva un token que cambia si la foto se reemplaza
     * (las rutas son deterministas y re-subir genera token nuevo), así que ignorar los
     * encabezados no deja fotos viejas pegadas.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(120)
        .build()
}
