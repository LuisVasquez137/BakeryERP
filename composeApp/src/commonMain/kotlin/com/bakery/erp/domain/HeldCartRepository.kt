package com.bakery.erp.domain

import com.bakery.erp.model.HeldCart
import kotlinx.coroutines.flow.Flow

/** Cuentas en espera de un turno de caja. Ver [HeldCart]. */
interface HeldCartRepository {
    /** Las del turno, la más vieja primero (es la que lleva más rato esperando). */
    fun observeForSession(companyId: String, sessionId: String): Flow<List<HeldCart>>

    suspend fun getForSession(companyId: String, sessionId: String): List<HeldCart>

    /** Devuelve el id asignado. */
    suspend fun save(companyId: String, cart: HeldCart): String

    suspend fun delete(companyId: String, cartId: String)
}
