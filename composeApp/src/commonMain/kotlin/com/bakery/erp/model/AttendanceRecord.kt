package com.bakery.erp.model

enum class AttendanceType {
    PRESENT, ABSENT, HALF_DAY, VACATION, SICK_LEAVE, HOLIDAY
}

/**
 * Qué tan confiable es la selfie de una marca del kiosko. La selfie no bloquea la marca:
 * deja prueba para revisar en el historial, y lo que no se pudo verificar queda señalado.
 */
enum class SelfieCheck {
    /** Marca anterior a la selfie, o hecha a mano desde la ficha. */
    NONE,
    /** Una sola cara y parpadeó: no es una foto mostrada a la cámara. */
    LIVE,
    /** Una sola cara, pero no parpadeó a tiempo. */
    FACE,
    /** Foto tomada sin poder verificar la cara (el detector no estaba disponible). */
    PHOTO,
    /** Marcó sin foto (sin cámara, sin permiso o tocó "Marcar sin foto"). */
    SKIPPED
}

/**
 * Las selfies se borran solas a los 60 días (regla de ciclo de vida del bucket sobre
 * `attendanceSelfies/`, ver functions/tools/setup-attendance-selfie-lifecycle.js). Pasado
 * ese plazo la pantalla ni intenta abrir la URL.
 */
const val ATTENDANCE_SELFIE_RETENTION_DAYS = 60

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
    val updatedAt: Long = 0L,
    val clockInSelfieCheck: SelfieCheck = SelfieCheck.NONE,
    /** Vacía si no hubo foto o si la subida falló (con un check distinto de SKIPPED). */
    val clockInPhotoUrl: String = "",
    val clockOutSelfieCheck: SelfieCheck = SelfieCheck.NONE,
    val clockOutPhotoUrl: String = "",
    /** 1 = primer (o único) turno, 2 = segundo turno de un horario mixto. */
    val shiftNumber: Int = 1
)
