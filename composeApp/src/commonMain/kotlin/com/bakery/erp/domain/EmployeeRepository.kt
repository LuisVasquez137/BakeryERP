package com.bakery.erp.domain

import com.bakery.erp.model.Employee

interface EmployeeRepository {
    suspend fun getEmployees(companyId: String): List<Employee>
    suspend fun getEmployee(companyId: String, employeeId: String): Employee?
    /** Devuelve el id de la ficha (el nuevo, si era alta). */
    suspend fun saveEmployee(companyId: String, employee: Employee): String
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
