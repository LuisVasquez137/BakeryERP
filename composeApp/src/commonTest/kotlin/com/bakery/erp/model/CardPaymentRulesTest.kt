package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CardPaymentRulesTest {

    private val below50pct5 = CardPaymentRules(
        surchargeMode = CardSurchargeMode.BELOW, surchargeThreshold = 50.0,
        surchargeType = CardSurchargeType.PERCENT, surchargeValue = 5.0
    )

    @Test
    fun byDefaultNothingChanges() {
        val r = CardPaymentRules()
        assertEquals(0.0, r.surchargeFor(10.0))
        assertNull(r.minimumProblem(1.0))
        assertNull(cardRulesProblem(r))
    }

    @Test
    fun surchargeOnlyBelowTheThreshold() {
        assertEquals(1.5, below50pct5.surchargeFor(30.0))
        assertEquals(0.0, below50pct5.surchargeFor(50.0))   // "menos de" 50: 50 ya no paga
        assertEquals(0.0, below50pct5.surchargeFor(80.0))
        assertEquals(0.62, below50pct5.surchargeFor(12.35)) // 0.6175 → al centavo
    }

    @Test
    fun alwaysAndFixed() {
        val fixed = CardPaymentRules(surchargeMode = CardSurchargeMode.ALWAYS,
            surchargeType = CardSurchargeType.FIXED, surchargeValue = 2.0)
        assertEquals(2.0, fixed.surchargeFor(500.0))
        assertEquals(0.0, fixed.surchargeFor(0.0))
    }

    @Test
    fun minimum() {
        val r = below50pct5.copy(minimumAmount = 20.0)
        assertNotNull(r.minimumProblem(19.99))
        assertNull(r.minimumProblem(20.0))
        assertNull(r.minimumProblem(0.0)) // sin tarjeta no hay mínimo que revisar
    }

    @Test
    fun invalidConfigurations() {
        assertNotNull(cardRulesProblem(below50pct5.copy(surchargeValue = 0.0)))
        assertNotNull(cardRulesProblem(below50pct5.copy(surchargeThreshold = 0.0)))
        assertNotNull(cardRulesProblem(below50pct5.copy(surchargeValue = 150.0)))
        assertNotNull(cardRulesProblem(below50pct5.copy(minimumAmount = 60.0)))
        assertNull(cardRulesProblem(below50pct5.copy(minimumAmount = 20.0)))
    }

    private fun totalCents(ls: List<ReceiptLine>) = ls.sumOf { kotlin.math.round(it.quantity * it.unitPrice * 100).toLong() }

    // Mismos casos que nucXmlBuilder.test.js: ticket y factura reparten igual.
    @Test
    fun absorbedSurchargeMatchesTheInvoiceRule() {
        val ls = absorbSurcharge(listOf(
            ReceiptLine("Champurrada", 10.0, 1.0, 10.0),
            ReceiptLine("Pastel", 1.0, 20.0, 20.0)
        ), 1.5)
        assertEquals(3150L, totalCents(ls))
        assertEquals(listOf("Champurrada" to 1.05, "Pastel" to 21.0), ls.map { it.name to it.unitPrice })
    }

    @Test
    fun withoutASingleUnitLineOneUnitIsSplitOff() {
        val ls = absorbSurcharge(listOf(ReceiptLine("Pan", 3.0, 1.0, 3.0)), 0.1)
        assertEquals(310L, totalCents(ls))
        assertEquals(listOf(2.0 to 1.03, 1.0 to 1.04), ls.map { it.quantity to it.unitPrice })
    }

    @Test
    fun ticketShowsTheSurchargeOnlyWithoutInvoice() {
        val lines = listOf(ReceiptLine("Pastel", 1.0, 20.0, 20.0))
        val ticket = receiptLinesNoInvoice(lines)
        assertEquals(CARD_SURCHARGE_LINE_NAME, ticket.last().name)
        val invoiced = absorbSurcharge(lines, 1.0)
        assertEquals(listOf("Pastel"), invoiced.map { it.name })
        assertEquals(21.0, invoiced.single().unitPrice)
    }

    private fun receiptLinesNoInvoice(lines: List<ReceiptLine>) =
        lines + ReceiptLine(CARD_SURCHARGE_LINE_NAME, 1.0, 1.0, 1.0)
}
