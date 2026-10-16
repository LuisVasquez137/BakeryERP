package com.bakery.erp.model

/**
 * La persona que está usando un equipo de sucursal (cuenta con `isDeviceAccount`), ya
 * identificada con su PIN. Vive solo en memoria: si la app se cierra, se vuelve a pedir.
 */
data class OperatorSession(
    val employeeId: String,
    val name: String,
    val photoUrl: String,
    val branchId: String,
    /** Permisos del colaborador (su ficha), antes de cruzarlos con los del equipo. */
    val permissions: List<String>,
    /** Cuenta del equipo en la que se identificó: si cambia la cuenta, la sesión no vale. */
    val accountId: String,
    /** Como está en la ficha ("Masculino", "Femenino", ...): solo para el saludo. */
    val gender: String = ""
)

/**
 * Saludo de la primera entrada del día. El género sale de la ficha; si no está o la
 * persona prefirió no decirlo, se usa una forma que no lo asume.
 */
fun welcomeHeadline(gender: String): String = when (gender.trim().lowercase()) {
    "femenino" -> "¡Bienvenida!"
    "masculino" -> "¡Bienvenido!"
    else -> "¡Te damos la bienvenida!"
}

/** Una cuenta de equipo pide PIN; OWNER/ADMIN son personas aunque alguien los marque. */
val User.requiresOperator: Boolean
    get() = isDeviceAccount && role != UserRole.OWNER && role != UserRole.ADMIN

/**
 * El usuario con el que trabaja la app mientras [operator] usa el equipo [account]:
 * puede hacer lo que permiten LOS DOS, y los registros llevan el nombre de la persona.
 * Sin persona identificada, ningún permiso (la pantalla de PIN tapa todo, pero si algo se
 * colara por debajo, tampoco podría hacer nada).
 *
 * Todos los chequeos de la app (`hasPermission`, `RequirePermission`) leen este usuario,
 * así que no hubo que tocar ninguno.
 */
fun effectiveUser(account: User, operator: OperatorSession?): User {
    if (!account.requiresOperator) return account
    if (operator == null || operator.accountId != account.id) return account.copy(permissions = emptyList())
    val allowed = operator.permissions.toSet()
    return account.copy(
        displayName = operator.name,
        permissions = account.permissions.filter { it in allowed },
        operatorEmployeeId = operator.employeeId
    )
}

/**
 * ¿Esta caja abierta la puede seguir operando [user]? Con cuenta propia, si la abrió esa
 * cuenta. En un equipo compartido la cuenta es la misma para todas, así que además tiene
 * que ser la misma PERSONA: si no, la cajera del turno siguiente vendería en la gaveta de
 * la anterior sin que nadie la entregara.
 */
fun CashSession.isOperatedBy(user: User): Boolean =
    cashierId == user.id &&
        (user.operatorEmployeeId.isEmpty() || openedByEmployeeId == user.operatorEmployeeId)

/** Saludo según la hora del día; lo usan la bienvenida y el encabezado del inicio. */
fun dayGreeting(hour: Int): String = when (hour) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}
