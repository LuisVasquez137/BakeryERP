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
    /**
     * A quién se le carga su sueldo (ver [CostCenter]). Vacío = a [branchId], donde trabaja.
     * El piloto marca en la planta pero su sueldo es de Reparto; el contador, de Generales.
     */
    val costCenter: String = CostCenter.BRANCH,
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
    /**
     * Turno mixto: dos entradas al día (p. ej. 07:00–11:00 y 15:00–19:00). El kiosko pide una
     * marca por turno y la tardanza de cada una se mide contra su propia hora de entrada.
     */
    val splitShift: Boolean = false,
    val secondStartTime: String = "",
    val secondEndTime: String = "",
    // Financial
    val paymentType: String = "",
    val bank: String = "",
    val bankAccount: String = "",
    val baseSalary: Double = 0.0,
    /**
     * Prestaciones al mes que fija el dueño (bonificación, aguinaldo y bono 14 repartidos al mes,
     * alimentación...). Entra al costo de planta junto con el salario; no lleva IGSS.
     */
    val monthlyBenefits: Double = 0.0,
    // Status & Dates
    val status: EmployeeStatus = EmployeeStatus.ACTIVE,
    val hireDate: Long = 0L,
    val endDate: Long = 0L,
    val notes: String = "",
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val userId: String = "",
    // Acceso con PIN (ver arquitectura/seguridad-accesos.md). Ninguno de los tres lo
    // escribe el formulario: los permisos los pone un OWNER/ADMIN desde la pestaña
    // "Acceso" y el estado del PIN solo la Cloud Function.
    /** Lo que esta persona puede hacer al identificarse con su PIN (nombres de [Permission]). */
    val permissions: List<String> = emptyList(),
    val pinStatus: EmployeePinStatus = EmployeePinStatus.NONE,
    val pinUpdatedAt: Long = 0L
)

enum class EmployeePinStatus {
    /** Todavía no tiene PIN: no puede identificarse en los equipos. */
    NONE,
    /** Se lo puso el dueño: la primera vez que lo use, la app le pide cambiarlo. */
    TEMPORARY,
    /** Lo escogió la persona: solo ella lo conoce. */
    SET;

    companion object {
        fun fromStored(value: String?): EmployeePinStatus = when (value) {
            "TEMPORARY" -> TEMPORARY
            "SET" -> SET
            else -> NONE
        }
    }
}
