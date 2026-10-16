package com.bakery.erp.domain

/**
 * Accesos directos del Home, guardados POR PERSONA: en un equipo de sucursal la cuenta es
 * la misma para todas, así que la llave es el colaborador (`emp_{employeeId}`); quien entra
 * con cuenta propia y no tiene ficha usa `user_{uid}`. Ver [homeShortcutOwnerKey].
 *
 * Se guardan los títulos de los módulos del lanzador. Al pintarlos se vuelven a filtrar por
 * los permisos de ese momento: si a alguien le quitan un módulo, su acceso directo desaparece
 * solo, sin que haya que limpiar nada.
 */
interface HomeShortcutRepository {
    suspend fun getShortcuts(companyId: String, ownerKey: String): List<String>
    suspend fun saveShortcuts(companyId: String, ownerKey: String, modules: List<String>)
}

fun homeShortcutOwnerKey(employeeId: String, userId: String): String =
    if (employeeId.isNotBlank()) "emp_$employeeId" else "user_$userId"
