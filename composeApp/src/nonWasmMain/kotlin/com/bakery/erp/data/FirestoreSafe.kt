package com.bakery.erp.data

/**
 * El SDK de Firestore Web no serializa el `Long` de Kotlin/JS (es un objeto
 * boxed custom, no un número JS nativo) y lanza:
 *   "Unsupported field value: a custom Long object (found in field X)".
 * El SDK de Android sí lo acepta, por lo que el bug solo aparece en web.
 *
 * Aplicar [firestoreSafe] al map justo antes de `.set()` / `.update()`
 * convierte todo `Long` a `Double`. Epoch millis cabe en Double (< 2^53)
 * sin pérdida de precisión, y al leer con `get<Long?>(...)` Firestore
 * devuelve el número convertido de vuelta a Long. Inofensivo en Android/iOS.
 */
fun Map<String, Any?>.firestoreSafe(): Map<String, Any?> =
    mapValues { (_, v) -> v.firestoreSafeValue() }

/** Como [mapOf] pero el resultado ya pasó por [firestoreSafe]. */
fun firestoreSafeOf(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
    pairs.toMap().firestoreSafe()

private fun Any?.firestoreSafeValue(): Any? = when (this) {
    is Long -> this.toDouble()
    is Map<*, *> -> this.entries.associate { (k, v) -> k to v.firestoreSafeValue() }
    is List<*> -> this.map { it.firestoreSafeValue() }
    else -> this
}
