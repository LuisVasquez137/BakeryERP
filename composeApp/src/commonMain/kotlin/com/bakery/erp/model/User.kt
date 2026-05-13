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
    val isApproved: Boolean = true       // false hasta que el admin apruebe
)

fun User.hasPermission(permission: Permission): Boolean =
    role == UserRole.OWNER || permissions.contains(permission.name)

fun User.canAccessBranch(branchId: String): Boolean =
    role == UserRole.OWNER || branchIds.isEmpty() || branchIds.contains(branchId)
