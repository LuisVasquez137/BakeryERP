package com.bakery.erp.presentation.pos

import kotlin.test.Test
import kotlin.test.assertEquals

class SuggestedAmountsTest {
    @Test
    fun billetesRedondosQueAlcanzan() {
        assertEquals(listOf(40, 50, 100, 200), suggestedAmounts(37.5))
        assertEquals(listOf(50, 60, 100, 200), suggestedAmounts(45.0))
        assertEquals(emptyList(), suggestedAmounts(0.0))
    }
}
