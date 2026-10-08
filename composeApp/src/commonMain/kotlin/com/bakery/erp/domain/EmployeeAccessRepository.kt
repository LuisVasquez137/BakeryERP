package com.bakery.erp.domain

/**
 * PIN personal de los colaboradores. Todo pasa por Cloud Functions
 * (functions/access/employeeAccess.js): el hash del PIN nunca llega al teléfono.
 */
interface EmployeeAccessRepository {
    /**
     * OWNER/ADMIN asigna o restablece el PIN. Si [typedByEmployee] es false, el PIN nace
     * temporal y la persona tendrá que cambiarlo la primera vez que lo use.
     */
    suspend fun setPin(companyId: String, employeeId: String, pin: String, typedByEmployee: Boolean)

    /** La persona cambia su propio PIN (pide el actual). */
    suspend fun changePin(companyId: String, employeeId: String, currentPin: String, newPin: String)

    /** ¿Este PIN es de esta persona? Lanza con el mensaje para mostrar si no. */
    suspend fun verifyPin(companyId: String, employeeId: String, pin: String): VerifiedEmployee
}

data class VerifiedEmployee(
    val employeeId: String,
    val name: String,
    val branchId: String,
    val photoUrl: String,
    val permissions: List<String>,
    /** El PIN es temporal: antes de seguir, la persona tiene que escoger uno propio. */
    val mustChangePin: Boolean
)

/**
 * Reglas del PIN, iguales a las del servidor (`motivoPinInvalido`), para avisar antes de
 * llamar. El servidor vuelve a validar: esto es solo para no hacer esperar a nadie.
 */
fun pinProblem(pin: String): String? {
    if (pin.length !in 4..6 || !pin.all { it.isDigit() }) return "El PIN debe tener de 4 a 6 números."
    if (pin.all { it == pin[0] }) return "El PIN no puede ser un mismo número repetido."
    val steps = pin.zipWithNext { a, b -> b.digitToInt() - a.digitToInt() }
    if (steps.all { it == 1 } || steps.all { it == -1 }) return "El PIN no puede ser una serie seguida (1234, 4321…)."
    return null
}
