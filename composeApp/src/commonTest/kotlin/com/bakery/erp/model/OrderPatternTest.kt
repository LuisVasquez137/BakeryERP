package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals

class OrderPatternTest {

    @Test
    fun withRainBump_belowThreshold_leavesQuantitiesUnchanged() {
        val expected = mapOf("pan" to 100.0, "concha" to 40.0)
        val result = expected.withRainBump(RAIN_PROBABILITY_THRESHOLD_PERCENT - 1)
        assertEquals(expected, result)
    }

    @Test
    fun withRainBump_nullProbability_leavesQuantitiesUnchanged() {
        val expected = mapOf("pan" to 100.0)
        assertEquals(expected, expected.withRainBump(null))
    }

    @Test
    fun withRainBump_atOrAboveThreshold_bumpsByConfiguredFraction() {
        val expected = mapOf("pan" to 100.0, "concha" to 40.0)
        val result = expected.withRainBump(RAIN_PROBABILITY_THRESHOLD_PERCENT)
        assertEquals(115.0, result.getValue("pan"))
        assertEquals(46.0, result.getValue("concha"))
    }

    @Test
    fun withRainBump_neverAddsProductsNotAlreadySuggested() {
        val expected = mapOf("pan" to 100.0)
        val result = expected.withRainBump(90)
        assertEquals(setOf("pan"), result.keys)
    }

    @Test
    fun withRainBump_emptyMap_staysEmpty() {
        assertEquals(emptyMap(), emptyMap<String, Double>().withRainBump(90))
    }
}
