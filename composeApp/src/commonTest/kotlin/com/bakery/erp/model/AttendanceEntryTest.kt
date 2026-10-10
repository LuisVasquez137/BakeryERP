package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AttendanceEntryTest {
    private val single = Employee(startTime = "07:00", endTime = "15:00")
    private val mixed = Employee(
        startTime = "07:00", endTime = "11:00",
        splitShift = true, secondStartTime = "15:00", secondEndTime = "19:00"
    )
    private fun at(h: Int, m: Int = 0) = h * 60 + m
    private fun entry(shift: Int) = AttendanceRecord(id = "r$shift", clockIn = 1000L * shift, shiftNumber = shift)

    @Test fun singleShiftMarksOnce() {
        assertEquals(EntryAvailability.Available(1, "07:00"), single.nextEntry(emptyList(), at(6, 55)))
        assertIs<EntryAvailability.Done>(single.nextEntry(listOf(entry(1)), at(16)))
    }

    @Test fun splitShiftSecondEntryOpensAfterFirstEnds() {
        assertEquals(EntryAvailability.Available(1, "07:00"), mixed.nextEntry(emptyList(), at(7, 5)))
        val wait = mixed.nextEntry(listOf(entry(1)), at(7, 6))
        assertIs<EntryAvailability.SecondOpensLater>(wait)
        assertEquals("11:00", wait.opensAt)
        assertEquals(EntryAvailability.Available(2, "15:00"), mixed.nextEntry(listOf(entry(1)), at(14, 58)))
        assertIs<EntryAvailability.Done>(mixed.nextEntry(listOf(entry(1), entry(2)), at(15, 1)))
    }

    @Test fun missedFirstEntryCountsAgainstSecond() {
        assertEquals(EntryAvailability.Available(2, "15:00"), mixed.nextEntry(emptyList(), at(15, 10)))
    }

    @Test fun withoutFirstEndOpensHourBeforeSecond() {
        val noEnd = mixed.copy(endTime = "")
        assertIs<EntryAvailability.SecondOpensLater>(noEnd.nextEntry(listOf(entry(1)), at(13, 59)))
        assertEquals(EntryAvailability.Available(2, "15:00"), noEnd.nextEntry(listOf(entry(1)), at(14)))
    }

    @Test fun splitWithoutSecondStartActsAsSingle() {
        val broken = mixed.copy(secondStartTime = "")
        assertIs<EntryAvailability.Done>(broken.nextEntry(listOf(entry(1)), at(16)))
    }

    @Test fun scheduleLabel() {
        assertEquals("07:00 – 15:00", single.scheduleLabel())
        assertEquals("07:00 – 11:00 · 15:00 – 19:00", mixed.scheduleLabel())
    }
}
