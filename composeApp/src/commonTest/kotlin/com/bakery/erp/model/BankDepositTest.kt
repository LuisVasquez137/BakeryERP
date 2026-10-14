package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BankDepositTest {

    @Test
    fun bankDepositLowersExpectedCashLikeAnyOutflow() {
        assertEquals(-1, CashMovementType.sign(CashMovementType.BANK_DEPOSIT))
        val totals = cashMovementTotals(listOf(
            CashMovement(id = "1", type = CashMovementType.BANK_DEPOSIT, amount = 2000.0)
        ))
        assertEquals(2000.0, totals.gastos)
    }

    @Test
    fun cannotTakeMoreThanTheDrawerHasNorWithoutBankOrCarrier() {
        assertNull(bankDepositProblem(2000.0, 2500.0, "Banrural", "e1"))
        assertNotNull(bankDepositProblem(0.0, 2500.0, "Banrural", "e1"))
        assertNotNull(bankDepositProblem(3000.0, 2500.0, "Banrural", "e1"))
        assertNotNull(bankDepositProblem(2000.0, 2500.0, " ", "e1"))
        assertNotNull(bankDepositProblem(2000.0, 2500.0, "Banrural", ""))
    }

    @Test
    fun slipNeedsPhotoNumberAndAmount() {
        assertNull(bankSlipProblem(true, "12345", 2000.0))
        assertNotNull(bankSlipProblem(false, "12345", 2000.0))
        assertNotNull(bankSlipProblem(true, "", 2000.0))
        assertNotNull(bankSlipProblem(true, "12345", null))
    }

    @Test
    fun slipDifferenceOnlyCountsWithSlip() {
        val d = BankDeposit(amount = 2000.0)
        assertTrue(d.isPending)
        assertEquals(0.0, d.slipDifference)
        val short = d.copy(status = BankDepositStatus.SLIP_ATTACHED, slipAmount = 1900.0)
        assertFalse(short.isPending)
        assertEquals(-100.0, short.slipDifference)
        assertTrue(short.slipMismatch)
        assertFalse(d.copy(status = BankDepositStatus.SLIP_ATTACHED, slipAmount = 2000.0).slipMismatch)
        assertFalse(d.copy(status = BankDepositStatus.NO_SLIP).isPending)
    }
}
