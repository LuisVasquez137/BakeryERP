package com.bakery.erp.domain

import com.bakery.erp.model.CardPaymentRules
import kotlinx.coroutines.flow.Flow

/** Reglas de pago de la empresa. Hoy solo las de tarjeta (ver [CardPaymentRules]). */
interface PaymentRulesRepository {
    /** Sin documento = valores por defecto (sin mínimo ni recargo). */
    suspend fun getCardRules(companyId: String): CardPaymentRules

    /** En vivo: una regla cambiada llega a las cajas abiertas sin recargar. */
    fun observeCardRules(companyId: String): Flow<CardPaymentRules>

    suspend fun saveCardRules(companyId: String, rules: CardPaymentRules)
}
