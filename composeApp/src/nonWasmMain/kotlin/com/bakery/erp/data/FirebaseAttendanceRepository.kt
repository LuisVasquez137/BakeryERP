package com.bakery.erp.data

import com.bakery.erp.domain.AttendanceRepository
import com.bakery.erp.model.AttendanceRecord
import com.bakery.erp.model.AttendanceType
import com.bakery.erp.model.SelfieCheck
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class FirebaseAttendanceRepository(
    private val firestore: FirebaseFirestore
) : AttendanceRepository {

    private fun col(companyId: String, employeeId: String) =
        firestore.collection("companies/$companyId/employees/$employeeId/attendance")

    override suspend fun getAttendance(
        companyId: String, employeeId: String, month: Int, year: Int
    ): List<AttendanceRecord> {
        val all = col(companyId, employeeId).get().documents.map { it.toRecord() }
        val tz = TimeZone.currentSystemDefault()
        return all.filter {
            val localDT = Instant.fromEpochMilliseconds(it.date).toLocalDateTime(tz)
            localDT.monthNumber == month && localDT.year == year
        }.sortedByDescending { it.date }
    }

    override suspend fun getTodayRecord(companyId: String, employeeId: String): AttendanceRecord? {
        val today = startOfDay(Clock.System.now().toEpochMilliseconds())
        return runCatching {
            col(companyId, employeeId).get().documents
                .map { it.toRecord() }
                .filter { it.date >= today }
                .maxByOrNull { it.clockIn }
        }.getOrNull()
    }

    override suspend fun getTodayRecords(companyId: String, employeeId: String): List<AttendanceRecord> {
        val today = startOfDay(Clock.System.now().toEpochMilliseconds())
        return col(companyId, employeeId).get().documents
            .map { it.toRecord() }
            .filter { it.date >= today }
            .sortedBy { it.clockIn }
    }

    override suspend fun clockIn(
        companyId: String, employeeId: String, startTime: String, selfieCheck: SelfieCheck, shiftNumber: Int
    ): AttendanceRecord {
        val now = Clock.System.now().toEpochMilliseconds()
        val docRef = col(companyId, employeeId).document
        val lateMinutes = calculateLateMinutes(now, startTime)
        val record = AttendanceRecord(
            id = docRef.id,
            employeeId = employeeId,
            date = startOfDay(now),
            clockIn = now,
            clockOut = 0L,
            type = AttendanceType.PRESENT,
            lateMinutes = lateMinutes,
            verificationMethod = verificationMethodFor(selfieCheck),
            createdAt = now,
            updatedAt = now,
            clockInSelfieCheck = selfieCheck,
            shiftNumber = shiftNumber
        )
        docRef.set(record.toMap())
        return record
    }

    override suspend fun clockOut(
        companyId: String, employeeId: String, recordId: String, selfieCheck: SelfieCheck
    ): AttendanceRecord {
        val doc = col(companyId, employeeId).document(recordId)
        val existing = doc.get().toRecord()
        val now = Clock.System.now().toEpochMilliseconds()
        val worked = (now - existing.clockIn) / 3_600_000.0
        val overtime = maxOf(0.0, worked - 8.0)
        val updated = existing.copy(
            clockOut = now,
            hoursWorked = worked,
            overtimeHours = overtime,
            updatedAt = now,
            clockOutSelfieCheck = selfieCheck
        )
        doc.set(updated.toMap())
        return updated
    }

    override suspend fun attachSelfiePhoto(
        companyId: String, employeeId: String, recordId: String, isClockIn: Boolean, photoUrl: String
    ) {
        val field = if (isClockIn) "clockInPhotoUrl" else "clockOutPhotoUrl"
        col(companyId, employeeId).document(recordId).update(field to photoUrl)
    }

    private fun verificationMethodFor(check: SelfieCheck): String = when (check) {
        SelfieCheck.NONE -> "manual"
        SelfieCheck.SKIPPED -> "sin_foto"
        else -> "selfie"
    }

    override suspend fun saveAttendance(
        companyId: String, employeeId: String, record: AttendanceRecord
    ) {
        val docRef = if (record.id.isEmpty()) col(companyId, employeeId).document
                     else col(companyId, employeeId).document(record.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val final = record.copy(
            id = docRef.id,
            employeeId = employeeId,
            updatedAt = now,
            createdAt = if (record.createdAt == 0L) now else record.createdAt
        )
        docRef.set(final.toMap())
    }

    override suspend fun deleteAttendance(
        companyId: String, employeeId: String, recordId: String
    ) {
        col(companyId, employeeId).document(recordId).delete()
    }

    private fun startOfDay(millis: Long): Long {
        val tz = TimeZone.currentSystemDefault()
        val localDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date
        return localDate.atStartOfDayIn(tz).toEpochMilliseconds()
    }

    private fun calculateLateMinutes(clockInMillis: Long, startTime: String): Int {
        if (startTime.isBlank()) return 0
        val parts = startTime.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return 0
        val tz = TimeZone.currentSystemDefault()
        val localDT = Instant.fromEpochMilliseconds(clockInMillis).toLocalDateTime(tz)
        val expectedDT = LocalDateTime(
            localDT.year, localDT.monthNumber, localDT.dayOfMonth,
            parts[0], parts[1], 0, 0
        )
        val diff = clockInMillis - expectedDT.toInstant(tz).toEpochMilliseconds()
        return if (diff > 0) (diff / 60_000).toInt() else 0
    }

    private fun AttendanceRecord.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "employeeId" to employeeId,
        "date" to date,
        "clockIn" to clockIn,
        "clockOut" to clockOut,
        "hoursWorked" to hoursWorked,
        "overtimeHours" to overtimeHours,
        "lateMinutes" to lateMinutes,
        "type" to type.name,
        "verificationMethod" to verificationMethod,
        "notes" to notes,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "clockInSelfieCheck" to clockInSelfieCheck.name,
        "clockInPhotoUrl" to clockInPhotoUrl,
        "clockOutSelfieCheck" to clockOutSelfieCheck.name,
        "clockOutPhotoUrl" to clockOutPhotoUrl,
        "shiftNumber" to shiftNumber
    )

    private fun DocumentSnapshot.toRecord(): AttendanceRecord {
        val typeStr = runCatching { get<String?>("type") }.getOrNull()
        val type = typeStr?.let { runCatching { AttendanceType.valueOf(it) }.getOrNull() }
            ?: AttendanceType.PRESENT
        return AttendanceRecord(
            id = id,
            employeeId = get<String?>("employeeId") ?: "",
            date = readMillis("date"),
            clockIn = readMillis("clockIn"),
            clockOut = readMillis("clockOut"),
            hoursWorked = runCatching { get<Double?>("hoursWorked") }.getOrNull() ?: 0.0,
            overtimeHours = runCatching { get<Double?>("overtimeHours") }.getOrNull() ?: 0.0,
            lateMinutes = runCatching { get<Long?>("lateMinutes")?.toInt() }.getOrNull() ?: 0,
            type = type,
            verificationMethod = get<String?>("verificationMethod") ?: "",
            notes = get<String?>("notes") ?: "",
            createdAt = readMillis("createdAt"),
            updatedAt = readMillis("updatedAt"),
            clockInSelfieCheck = readSelfieCheck("clockInSelfieCheck"),
            clockInPhotoUrl = runCatching { get<String?>("clockInPhotoUrl") }.getOrNull() ?: "",
            clockOutSelfieCheck = readSelfieCheck("clockOutSelfieCheck"),
            clockOutPhotoUrl = runCatching { get<String?>("clockOutPhotoUrl") }.getOrNull() ?: "",
            shiftNumber = (runCatching { get<Long?>("shiftNumber")?.toInt() }.getOrNull()
                ?: runCatching { get<Double?>("shiftNumber")?.toInt() }.getOrNull()
                ?: 1)
        )
    }

    private fun DocumentSnapshot.readSelfieCheck(field: String): SelfieCheck =
        runCatching { get<String?>(field) }.getOrNull()
            ?.let { runCatching { SelfieCheck.valueOf(it) }.getOrNull() }
            ?: SelfieCheck.NONE

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
