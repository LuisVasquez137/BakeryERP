package com.bakery.erp.domain

import com.bakery.erp.model.AttendanceRecord
import com.bakery.erp.model.SelfieCheck

interface AttendanceRepository {
    suspend fun getAttendance(companyId: String, employeeId: String, month: Int, year: Int): List<AttendanceRecord>
    suspend fun getTodayRecord(companyId: String, employeeId: String): AttendanceRecord?
    /** Todas las marcas de hoy: un turno mixto tiene dos. */
    suspend fun getTodayRecords(companyId: String, employeeId: String): List<AttendanceRecord>
    suspend fun clockIn(
        companyId: String, employeeId: String, startTime: String,
        selfieCheck: SelfieCheck = SelfieCheck.NONE,
        shiftNumber: Int = 1
    ): AttendanceRecord
    suspend fun clockOut(
        companyId: String, employeeId: String, recordId: String,
        selfieCheck: SelfieCheck = SelfieCheck.NONE
    ): AttendanceRecord
    /**
     * Escritura parcial: la foto se sube después de registrar la marca (la marca no espera
     * a la red), y un `set()` aquí pisaría una salida marcada mientras tanto.
     */
    suspend fun attachSelfiePhoto(
        companyId: String, employeeId: String, recordId: String, isClockIn: Boolean, photoUrl: String
    )
    suspend fun saveAttendance(companyId: String, employeeId: String, record: AttendanceRecord)
    suspend fun deleteAttendance(companyId: String, employeeId: String, recordId: String)
}
