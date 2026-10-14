package com.bakery.erp.presentation.pos

import com.bakery.erp.model.Employee
import com.bakery.erp.model.User
import com.bakery.erp.model.UserRole
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CarrierPinTest {

    private fun user(id: String) = User(id = id, orgId = "c", email = "", displayName = "", role = UserRole.EMPLOYEE,
        branchIds = emptyList(), isActive = true)
    private val ana = Employee(id = "e1", name = "Ana")
    private val branchTablet = user("u1").copy(operatorEmployeeId = "e1")
    private val owner = user("u2")

    @Test
    fun branchWithPinAsksEvenWhenCarrierIsWhoOpened() {
        assertTrue(carrierMustConfirm(branchTablet))
        assertNotNull(carrierPinProblem(branchTablet, BankDepositDraft("Banrural", ana)))
        assertNull(carrierPinProblem(branchTablet, BankDepositDraft("Banrural", ana, carrierPin = "2580")))
    }

    @Test
    fun withoutPinAtTheBranchNothingIsAsked() {
        assertFalse(carrierMustConfirm(owner))
        assertNull(carrierPinProblem(owner, BankDepositDraft("Banrural", ana)))
    }
}
