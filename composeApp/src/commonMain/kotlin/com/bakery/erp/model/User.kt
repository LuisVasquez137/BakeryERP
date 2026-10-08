package com.bakery.erp.model

data class User(
    val id: String,
    val orgId: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val branchIds: List<String>,         // vacío = acceso a todas (OWNER)
    val permissions: List<String> = emptyList(),
    val isActive: Boolean,
    val isApproved: Boolean = true,      // false hasta que el admin apruebe
    val photoUrl: String? = null,        // foto de perfil de Google; null si se registró con email/password
    /**
     * Cuenta del EQUIPO de una sucursal (tablet/teléfono), no de una persona. Su contraseña
     * solo la sabe el dueño; quien usa el equipo se identifica con su PIN de colaborador, y
     * los permisos de esta cuenta son el techo de lo que se puede hacer en ese equipo.
     * Solo la marca un OWNER/ADMIN (firestore.rules).
     */
    val isDeviceAccount: Boolean = false,
    /**
     * Colaborador que está usando el equipo (identificado con PIN). NO se guarda en el doc
     * del usuario: lo pone `effectiveUser` en memoria. Vacío = la cuenta es de una persona.
     * Lo que se registre a nombre de alguien debe guardar este id además del de la cuenta.
     */
    val operatorEmployeeId: String = ""
)

fun User.hasPermission(permission: Permission): Boolean =
    role == UserRole.OWNER || permissions.contains(permission.name)

fun User.canAccessBranch(branchId: String): Boolean =
    role == UserRole.OWNER || branchIds.isEmpty() || branchIds.contains(branchId)
