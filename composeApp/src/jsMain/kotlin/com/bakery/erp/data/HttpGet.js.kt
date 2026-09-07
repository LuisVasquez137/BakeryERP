package com.bakery.erp.data

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.withTimeoutOrNull

actual suspend fun httpGetText(url: String, timeoutMillis: Long): String? =
    withTimeoutOrNull(timeoutMillis) {
        try {
            val response = window.fetch(url).await()
            if (!response.ok) null else response.text().await()
        } catch (e: Throwable) {
            console.error("httpGetText: fetch error para $url — ${e.message}")
            null
        }
    }
