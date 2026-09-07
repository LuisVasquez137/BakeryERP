package com.bakery.erp.model

enum class AttendanceType {
    PRESENT, ABSENT, HALF_DAY, VACATION, SICK_LEAVE, HOLIDAY
}

data class AttendanceRecord(
    val id: String = "",
    val employeeId: String = "",
    val date: Long = 0L,
    val clockIn: Long = 0L,
    val clockOut: Long = 0L,
    val hoursWorked: Double = 0.0,
    val overtimeHours: Double = 0.0,
    val lateMinutes: Int = 0,
    val type: AttendanceType = AttendanceType.PRESENT,
    val verificationMethod: String = "",
    val notes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
