package com.bakery.erp.model

enum class EmployeeStatus {
    ACTIVE, SUSPENDED, ON_VACATION, FIRED, RESIGNED
}

data class Employee(
    val id: String = "",
    // Personal
    val name: String = "",
    val employeeCode: String = "",
    val photoUrl: String = "",
    val dpi: String = "",
    val nit: String = "",
    val birthDate: Long = 0L,
    val gender: String = "",
    val maritalStatus: String = "",
    // Contact
    val phone: String = "",
    val phoneAux: String = "",
    val email: String = "",
    val address: String = "",
    val emergencyContact: String = "",
    val emergencyPhone: String = "",
    // Job
    val branchId: String = "",
    val branchName: String = "",
    val department: String = "",
    val positionId: String = "",
    val positionTitle: String = "",
    val supervisorId: String = "",
    val supervisorName: String = "",
    val contractType: String = "",
    // Schedule
    val shiftId: String = "",
    val shiftName: String = "",
    val workDays: List<Int> = emptyList(),
    val startTime: String = "",
    val endTime: String = "",
    // Financial
    val paymentType: String = "",
    val bank: String = "",
    val bankAccount: String = "",
    val baseSalary: Double = 0.0,
    // Status & Dates
    val status: EmployeeStatus = EmployeeStatus.ACTIVE,
    val hireDate: Long = 0L,
    val endDate: Long = 0L,
    val notes: String = "",
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val userId: String = ""
)
