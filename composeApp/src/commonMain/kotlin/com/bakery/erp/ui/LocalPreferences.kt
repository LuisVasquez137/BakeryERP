package com.bakery.erp.ui

/**
 * Preferencias de interfaz que viven **en el dispositivo**, no en Firestore.
 *
 * Son gustos de quien opera el aparato (en qué unidad teclea, qué filtro deja
 * puesto), no datos de la panadería: no tiene sentido pagarles una lectura de
 * Firestore ni sincronizarlas entre sucursales. Si se pierden al reinstalar, no
 * pasa nada.
 *
 * Para configuración compartida por toda una sucursal —como el orden de
 * productos— usar el documento de la sucursal, no esto.
 */
expect object LocalPreferences {
    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)
}

/** Claves usadas. Centralizadas para no repetir literales sueltos. */
object PreferenceKeys {
    /** Unidad elegida en la carga rápida de pesos: "OUNCES" | "GRAMS". */
    const val WEIGHT_INPUT_UNIT = "weight_input_unit"
}
