package com.bakery.erp.data

/** No-op para wasmJs por ahora — mismo criterio que [com.bakery.erp.ui.rememberLocationRequester]. */
actual suspend fun httpGetText(url: String, timeoutMillis: Long): String? = null
