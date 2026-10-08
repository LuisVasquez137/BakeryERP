package com.bakery.erp.model

import com.bakery.erp.domain.pinProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperatorSessionTest {

    private val device = User(
        id = "dev1", orgId = "org", email = "sucursal@x.com", displayName = "Sabropan Bombero",
        role = UserRole.EMPLOYEE, branchIds = listOf("b1"),
        permissions = listOf("OPERATE_POS", "VOID_SALE", "RECEIVE_DISPATCH"),
        isActive = true, isDeviceAccount = true
    )

    private fun op(vararg perms: String, accountId: String = "dev1") = OperatorSession(
        employeeId = "e1", name = "Gladys Calderón", photoUrl = "", branchId = "b1",
        permissions = perms.toList(), accountId = accountId
    )

    @Test
    fun `equipo sin persona no puede hacer nada`() {
        val u = effectiveUser(device, null)
        assertTrue(u.permissions.isEmpty())
        assertTrue(!u.hasPermission(Permission.OPERATE_POS))
    }

    @Test
    fun `persona en el equipo puede lo que permiten los dos`() {
        val u = effectiveUser(device, op("OPERATE_POS", "VIEW_FINANCIAL_REPORT"))
        assertEquals(listOf("OPERATE_POS"), u.permissions)
        assertTrue(u.hasPermission(Permission.OPERATE_POS))
        // La persona lo tiene, pero el equipo no: no.
        assertTrue(!u.hasPermission(Permission.VIEW_FINANCIAL_REPORT))
        // El equipo lo tiene, pero la persona no: no.
        assertTrue(!u.hasPermission(Permission.VOID_SALE))
    }

    @Test
    fun `los registros llevan el nombre de la persona y la cuenta sigue siendo la del equipo`() {
        val u = effectiveUser(device, op("OPERATE_POS"))
        assertEquals("Gladys Calderón", u.displayName)
        assertEquals("dev1", u.id)
        assertEquals(listOf("b1"), u.branchIds)
    }

    @Test
    fun `una sesion de otra cuenta no vale`() {
        val u = effectiveUser(device, op("OPERATE_POS", accountId = "otra"))
        assertTrue(u.permissions.isEmpty())
    }

    @Test
    fun `cuentas de personas no cambian`() {
        val person = device.copy(isDeviceAccount = false)
        assertEquals(person, effectiveUser(person, op("VIEW_FINANCIAL_REPORT")))
        // OWNER/ADMIN son personas aunque alguien los marque como equipo.
        val owner = device.copy(role = UserRole.OWNER)
        assertEquals(owner, effectiveUser(owner, null))
    }

    @Test
    fun `reglas del PIN iguales a las del servidor`() {
        listOf("1357", "2580", "907153", "1243").forEach { assertNull(pinProblem(it), it) }
        listOf("123", "1234567", "12a4", "1111", "000000", "1234", "4321", "345678").forEach {
            assertNotNull(pinProblem(it), it)
        }
    }
}
