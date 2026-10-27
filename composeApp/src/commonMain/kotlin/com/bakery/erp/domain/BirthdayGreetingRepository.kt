package com.bakery.erp.domain

import com.bakery.erp.model.BirthdayGreeting

/** Felicitaciones de cumpleaños (`companies/{c}/birthdayGreetings`, plana). */
interface BirthdayGreetingRepository {
    /** Las que [senderEmployeeId] envió u omitió en [year]. */
    suspend fun getSentBy(companyId: String, senderEmployeeId: String, year: Int): List<BirthdayGreeting>
    /** Las que recibió [birthdayEmployeeId] en [year] (enviadas y omitidas). */
    suspend fun getReceivedBy(companyId: String, birthdayEmployeeId: String, year: Int): List<BirthdayGreeting>
    /** Crea o reemplaza (omitida → enviada). El id sale de [com.bakery.erp.model.birthdayGreetingId]. */
    suspend fun save(companyId: String, greeting: BirthdayGreeting)
    suspend fun markSeen(companyId: String, ids: List<String>, at: Long)
}
