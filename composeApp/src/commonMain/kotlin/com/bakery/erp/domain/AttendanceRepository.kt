package com.bakery.erp.domain

import com.bakery.erp.model.AttendanceRecord

interface AttendanceRepository {
    suspend fun getAttendance(companyId: String, employeeId: String, month: Int, year: Int): List<AttendanceRecord>
    suspend fun getTodayRecord(companyId: String, employeeId: String): AttendanceRecord?
    suspend fun clockIn(companyId: String, employeeId: String, startTime: String): AttendanceRecord
    suspend fun clockOut(companyId: String, employeeId: String, recordId: String): AttendanceRecord
    suspend fun saveAttendance(companyId: String, employeeId: String, record: AttendanceRecord)
    suspend fun deleteAttendance(companyId: String, employeeId: String, recordId: String)
}
