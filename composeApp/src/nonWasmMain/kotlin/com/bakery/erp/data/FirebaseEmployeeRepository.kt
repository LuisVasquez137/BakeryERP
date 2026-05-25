package com.bakery.erp.data

import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.model.Employee
import com.bakery.erp.model.EmployeeStatus
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock

class FirebaseEmployeeRepository(
    private val firestore: FirebaseFirestore
) : EmployeeRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/employees")

    override suspend fun getEmployees(companyId: String): List<Employee> =
        col(companyId).get().documents
            .map { it.toEmployee() }
            .filter { it.status != EmployeeStatus.FIRED && it.status != EmployeeStatus.RESIGNED }
            .sortedBy { it.name }

    override suspend fun getEmployee(companyId: String, employeeId: String): Employee? =
        runCatching { col(companyId).document(employeeId).get().toEmployee() }.getOrNull()

    override suspend fun saveEmployee(companyId: String, employee: Employee) {
        val docRef = if (employee.id.isEmpty()) col(companyId).document
                     else col(companyId).document(employee.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val final = employee.copy(
            id = docRef.id,
            active = employee.status == EmployeeStatus.ACTIVE,
            updatedAt = now,
            createdAt = if (employee.createdAt == 0L) now else employee.createdAt
        )
        docRef.set(final.toMap())
    }

    override suspend fun deleteEmployee(companyId: String, employeeId: String) {
        col(companyId).document(employeeId).update(mapOf(
            "active" to false,
            "status" to EmployeeStatus.FIRED.name
        ))
    }

    private fun Employee.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "name" to name,
        "employeeCode" to employeeCode,
        "photoUrl" to photoUrl,
        "dpi" to dpi,
        "nit" to nit,
        "birthDate" to birthDate,
        "gender" to gender,
        "maritalStatus" to maritalStatus,
        "phone" to phone,
        "phoneAux" to phoneAux,
        "email" to email,
        "address" to address,
        "emergencyContact" to emergencyContact,
        "emergencyPhone" to emergencyPhone,
        "branchId" to branchId,
        "branchName" to branchName,
        "department" to department,
        "positionId" to positionId,
        "positionTitle" to positionTitle,
        "supervisorId" to supervisorId,
        "supervisorName" to supervisorName,
        "contractType" to contractType,
        "shiftId" to shiftId,
        "shiftName" to shiftName,
        "workDays" to workDays.joinToString(","),
        "startTime" to startTime,
        "endTime" to endTime,
        "paymentType" to paymentType,
        "bank" to bank,
        "bankAccount" to bankAccount,
        "baseSalary" to baseSalary,
        "status" to status.name,
        "hireDate" to hireDate,
        "endDate" to endDate,
        "notes" to notes,
        "active" to active,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "userId" to userId
    )

    private fun DocumentSnapshot.toEmployee(): Employee {
        val statusStr = runCatching { get<String?>("status") }.getOrNull()
        val status = statusStr?.let { runCatching { EmployeeStatus.valueOf(it) }.getOrNull() }
            ?: if (runCatching { get<Boolean?>("active") }.getOrNull() != false) EmployeeStatus.ACTIVE
               else EmployeeStatus.FIRED
        val workDaysStr = runCatching { get<String?>("workDays") }.getOrNull() ?: ""
        val workDays = if (workDaysStr.isBlank()) emptyList()
                       else workDaysStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        return Employee(
            id = id,
            name = get<String?>("name") ?: "",
            employeeCode = get<String?>("employeeCode") ?: "",
            photoUrl = get<String?>("photoUrl") ?: "",
            dpi = get<String?>("dpi") ?: "",
            nit = get<String?>("nit") ?: "",
            birthDate = readMillis("birthDate"),
            gender = get<String?>("gender") ?: "",
            maritalStatus = get<String?>("maritalStatus") ?: "",
            phone = get<String?>("phone") ?: "",
            phoneAux = get<String?>("phoneAux") ?: "",
            email = get<String?>("email") ?: "",
            address = get<String?>("address") ?: "",
            emergencyContact = get<String?>("emergencyContact") ?: "",
            emergencyPhone = get<String?>("emergencyPhone") ?: "",
            branchId = get<String?>("branchId") ?: "",
            branchName = get<String?>("branchName") ?: "",
            department = get<String?>("department") ?: "",
            positionId = get<String?>("positionId") ?: "",
            positionTitle = get<String?>("positionTitle") ?: (get<String?>("position") ?: ""),
            supervisorId = get<String?>("supervisorId") ?: "",
            supervisorName = get<String?>("supervisorName") ?: "",
            contractType = get<String?>("contractType") ?: "",
            shiftId = get<String?>("shiftId") ?: "",
            shiftName = get<String?>("shiftName") ?: "",
            workDays = workDays,
            startTime = get<String?>("startTime") ?: "",
            endTime = get<String?>("endTime") ?: "",
            paymentType = get<String?>("paymentType") ?: "",
            bank = get<String?>("bank") ?: "",
            bankAccount = get<String?>("bankAccount") ?: "",
            baseSalary = runCatching { get<Double?>("baseSalary") }.getOrNull() ?: 0.0,
            status = status,
            hireDate = readMillis("hireDate"),
            endDate = readMillis("endDate"),
            notes = get<String?>("notes") ?: "",
            active = status == EmployeeStatus.ACTIVE,
            createdAt = readMillis("createdAt"),
            updatedAt = readMillis("updatedAt"),
            userId = get<String?>("userId") ?: ""
        )
    }

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
