package com.bakery.erp.domain

/**
 * Sube la selfie de una marca de asistencia y devuelve el downloadUrl.
 *
 * Ruta fija `attendanceSelfies/{companyId}/{employeeId}/{recordId}_{in|out}.jpg`: el prefijo
 * de primer nivel es el que la regla de ciclo de vida del bucket borra a los 60 días, por
 * eso NO va bajo `companies/` junto con lo que sí se guarda para siempre.
 *
 * Implementación por plataforma, igual que [SpecialOrderImageUploader].
 */
interface AttendanceSelfieUploader {
    suspend fun uploadSelfie(
        bytes: ByteArray,
        companyId: String,
        employeeId: String,
        recordId: String,
        isClockIn: Boolean
    ): String
}

fun attendanceSelfiePath(companyId: String, employeeId: String, recordId: String, isClockIn: Boolean) =
    "attendanceSelfies/$companyId/$employeeId/${recordId}_${if (isClockIn) "in" else "out"}.jpg"

/** Plataformas sin cámara en el kiosko (web, iOS por ahora): nunca llega una foto. */
class NoOpAttendanceSelfieUploader : AttendanceSelfieUploader {
    override suspend fun uploadSelfie(
        bytes: ByteArray, companyId: String, employeeId: String, recordId: String, isClockIn: Boolean
    ): String = ""
}
