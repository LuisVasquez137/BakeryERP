package com.bakery.erp.domain

import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.Employee
import com.bakery.erp.model.Permission
import com.bakery.erp.model.User

/**
 * La ficha vive en tres documentos (ver arquitectura/seguridad-accesos.md, "Ficha privada"):
 * - `employees/{id}`: lo que usa toda la app (nombre, foto, sucursal, horario, PIN...).
 * - `employees/{id}/private/profile`: DPI, NIT, dirección, emergencia, estado civil, notas.
 *   Lo lee quien tiene `MANAGE_EMPLOYEES`.
 * - `employees/{id}/private/pay`: salario, prestaciones, banco, cuenta, forma de pago. Lo lee
 *   quien tiene `VIEW_EMPLOYEE_SALARY`.
 *
 * [PrivatePart] dice qué partes leer o escribir. Sin permiso no se piden: las reglas las
 * negarían y, al guardar, se pisaría la parte privada con lo que la pantalla no mostró.
 */
interface EmployeeRepository {
    enum class PrivatePart { PROFILE, PAY }

    suspend fun getEmployees(
        companyId: String,
        privateParts: Set<PrivatePart> = emptySet()
    ): List<Employee>
    suspend fun getEmployee(
        companyId: String,
        employeeId: String,
        privateParts: Set<PrivatePart> = emptySet()
    ): Employee?
    /**
     * Devuelve el id de la ficha (el nuevo, si era alta). Solo escribe las partes privadas de
     * [privateParts]; las demás quedan como estaban.
     */
    suspend fun saveEmployee(
        companyId: String,
        employee: Employee,
        privateParts: Set<PrivatePart>
    ): String
    suspend fun deleteEmployee(companyId: String, employeeId: String)
    /** Escritura parcial: solo `permissions`. Las reglas exigen OWNER/ADMIN. */
    suspend fun updateEmployeePermissions(companyId: String, employeeId: String, permissions: List<String>)
    /**
     * Escritura parcial de lo que la persona corrige de sí misma desde el Home: nombre y
     * contacto. Nada de puesto, sucursal ni salario — eso sigue siendo del formulario.
     */
    /**
     * ¿Es la primera vez HOY ([day], `yyyy-MM-dd` local) que esta persona entra con su PIN?
     * Si lo es, deja el día anotado en su ficha (`lastWelcomeDay`) y devuelve true. Va en la
     * ficha y no en el equipo para que, si cambia de tablet el mismo día, no la saluden dos veces.
     */
    suspend fun markFirstEntryOfDay(companyId: String, employeeId: String, day: String): Boolean

    suspend fun updateEmployeeContact(
        companyId: String,
        employeeId: String,
        name: String,
        phone: String,
        phoneAux: String,
        email: String
    )
}

/** Las partes privadas de la ficha que esta persona puede leer y escribir (mismo corte que las reglas). */
fun User?.employeePrivateParts(): Set<EmployeeRepository.PrivatePart> = buildSet {
    val u = this@employeePrivateParts ?: return@buildSet
    if (u.hasPermission(Permission.MANAGE_EMPLOYEES)) add(EmployeeRepository.PrivatePart.PROFILE)
    if (u.hasPermission(Permission.VIEW_EMPLOYEE_SALARY)) add(EmployeeRepository.PrivatePart.PAY)
}
