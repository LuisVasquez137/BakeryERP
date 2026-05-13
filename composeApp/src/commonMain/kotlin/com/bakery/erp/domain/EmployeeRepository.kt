package com.bakery.erp.domain

import com.bakery.erp.model.Employee

interface EmployeeRepository {
    suspend fun getEmployees(companyId: String): List<Employee>
    suspend fun getEmployee(companyId: String, employeeId: String): Employee?
    suspend fun saveEmployee(companyId: String, employee: Employee)
    suspend fun deleteEmployee(companyId: String, employeeId: String)
}
