package com.bakery.erp.data

import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.model.Employee
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds

class FirebaseEmployeeRepository(
    private val firestore: FirebaseFirestore
) : EmployeeRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/employees")

    override suspend fun getEmployees(companyId: String): List<Employee> =
        col(companyId).get().documents
            .map { it.toEmployee() }
            .filter { it.active }
            .sortedBy { it.name }

    override suspend fun getEmployee(companyId: String, employeeId: String): Employee? =
        runCatching { col(companyId).document(employeeId).get().toEmployee() }.getOrNull()

    override suspend fun saveEmployee(companyId: String, employee: Employee) {
        val docRef = if (employee.id.isEmpty()) col(companyId).document
                     else col(companyId).document(employee.id)
        val now = System.currentTimeMillis()
        val final = employee.copy(
            id = docRef.id,
            updatedAt = now,
            createdAt = if (employee.createdAt == 0L) now else employee.createdAt
        )
        docRef.set(final.toMap())
    }

    override suspend fun deleteEmployee(companyId: String, employeeId: String) {
        col(companyId).document(employeeId).update(mapOf("active" to false))
    }

    private fun Employee.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "branchId" to branchId,
        "branchName" to branchName,
        "position" to position,
        "phone" to phone,
        "phoneAux" to phoneAux,
        "email" to email,
        "address" to address,
        "hireDate" to hireDate,
        "notes" to notes,
        "active" to active,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    private fun DocumentSnapshot.toEmployee() = Employee(
        id = id,
        name = get<String?>("name") ?: "",
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        position = get<String?>("position") ?: "",
        phone = get<String?>("phone") ?: "",
        phoneAux = get<String?>("phoneAux") ?: "",
        email = get<String?>("email") ?: "",
        address = get<String?>("address") ?: "",
        hireDate = readMillis("hireDate"),
        notes = get<String?>("notes") ?: "",
        active = runCatching { get<Boolean?>("active") }.getOrNull() ?: true,
        createdAt = readMillis("createdAt"),
        updatedAt = readMillis("updatedAt")
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
