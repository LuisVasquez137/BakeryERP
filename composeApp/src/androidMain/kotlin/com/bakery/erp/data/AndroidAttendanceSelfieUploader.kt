package com.bakery.erp.data

import com.bakery.erp.domain.AttendanceSelfieUploader
import com.bakery.erp.domain.attendanceSelfiePath
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage

class AndroidAttendanceSelfieUploader(
    private val storage: FirebaseStorage
) : AttendanceSelfieUploader {
    override suspend fun uploadSelfie(
        bytes: ByteArray, companyId: String, employeeId: String, recordId: String, isClockIn: Boolean
    ): String {
        val ref = storage.reference.child(attendanceSelfiePath(companyId, employeeId, recordId, isClockIn))
        ref.putData(Data(bytes))
        return ref.getDownloadUrl()
    }
}
