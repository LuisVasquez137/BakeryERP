package com.bakery.erp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

actual suspend fun httpGetText(url: String, timeoutMillis: Long): String? =
    withTimeoutOrNull(timeoutMillis) {
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = timeoutMillis.toInt()
                connection.readTimeout = timeoutMillis.toInt()
                try {
                    if (connection.responseCode in 200..299) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else null
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }
