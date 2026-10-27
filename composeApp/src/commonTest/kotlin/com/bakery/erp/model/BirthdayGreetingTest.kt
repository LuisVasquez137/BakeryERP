package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals

class BirthdayGreetingTest {
    @Test
    fun `no se ofrece felicitarse a si mismo ni a quien ya se felicito u omitio`() {
        val mine = listOf(
            BirthdayGreeting(birthdayEmployeeId = "b", status = BirthdayGreetingStatus.SENT),
            BirthdayGreeting(birthdayEmployeeId = "c", status = BirthdayGreetingStatus.SKIPPED)
        )
        assertEquals(listOf("d"), greetingPromptsFor(listOf("a", "b", "c", "d"), me = "a", mine = mine))
    }

    @Test
    fun `solo las enviadas y no vistas, en orden de llegada`() {
        val received = listOf(
            BirthdayGreeting(id = "2", createdAt = 20),
            BirthdayGreeting(id = "1", createdAt = 10),
            BirthdayGreeting(id = "3", createdAt = 5, seenAt = 99),
            BirthdayGreeting(id = "4", createdAt = 1, status = BirthdayGreetingStatus.SKIPPED)
        )
        assertEquals(listOf("1", "2"), unseenGreetings(received).map { it.id })
    }

    @Test
    fun `id unico por anio cumpleanero y quien felicita`() {
        assertEquals("2026_ana_luis", birthdayGreetingId(2026, "ana", "luis"))
    }
}
