package com.bakery.erp.domain

import com.bakery.erp.model.CashAuditEvent

/** Bitácora de cajas. Solo se agrega: un registro de control no se edita ni se borra. */
interface CashAuditRepository {
    suspend fun log(companyId: String, event: CashAuditEvent)

    /** Eventos de una sucursal en [fromMillis, untilMillis), el más reciente primero. */
    suspend fun getEvents(companyId: String, branchId: String, fromMillis: Long, untilMillis: Long): List<CashAuditEvent>
}
