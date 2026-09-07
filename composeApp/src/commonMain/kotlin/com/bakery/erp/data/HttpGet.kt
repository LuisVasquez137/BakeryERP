package com.bakery.erp.data

/**
 * GET simple, sin cliente HTTP pesado: cada plataforma usa lo que ya trae el
 * SDK/navegador (ver `ProductImage.js.kt` para el precedente de `window.fetch`
 * en este mismo proyecto). Pensado para una sola llamada ocasional a un
 * endpoint público — no reemplaza nada, es lo único que existe hoy.
 *
 * @return el body como texto, o null si falla (timeout, red, HTTP != 2xx).
 *  Nunca lanza.
 */
expect suspend fun httpGetText(url: String, timeoutMillis: Long = 8_000L): String?
