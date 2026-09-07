package com.bakery.erp.di

import com.bakery.erp.data.IosDocumentPrinter
import com.bakery.erp.domain.DocumentPrinter
import org.koin.dsl.module

/**
 * Overrides específicos de iOS sobre [appModule] — mismo patrón que `androidModule`
 * en Android. Hoy solo la impresión: el resto del grafo es compartido.
 */
val iosModule = module {
    single<DocumentPrinter> { IosDocumentPrinter() }
}
