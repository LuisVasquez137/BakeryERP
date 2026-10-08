package com.bakery.erp.domain

import com.bakery.erp.model.ExternalCustomer
import com.bakery.erp.model.Organization
import com.bakery.erp.model.User
import com.bakery.erp.model.UserRole
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>

    suspend fun signIn(email: String, password: String): Result<User>

    suspend fun registerUser(
        email: String,
        password: String,
        orgName: String,
        displayName: String,
        role: UserRole = UserRole.OWNER
    ): Result<User>

    suspend fun createOrgForExistingUser(
        orgName: String,
        displayName: String
    ): Result<User>

    suspend fun joinOrgForExistingUser(
        code: String,
        displayName: String
    ): Result<User>

    suspend fun joinWithInviteCode(
        code: String,
        displayName: String,
        email: String,
        password: String
    ): Result<User>

    suspend fun signInWithGoogle(idToken: String): Result<User>

    suspend fun signOut()
    suspend fun getCurrentUser(): User?
    suspend fun getOrgInviteCode(): Result<String>
    /** Devuelve el documento Organization (nombre comercial, plan, etc.) por id. */
    suspend fun getOrganization(orgId: String): Result<Organization>
    suspend fun getUsersForOrg(orgId: String): List<User>
    suspend fun updateUserAccess(
        userId: String,
        branchIds: List<String>,
        permissions: List<String>,
        isDeviceAccount: Boolean
    ): Result<Unit>

    /**
     * Paso 1 de alta de revendedor: crea la cuenta Firebase y el User con rol
     * RESELLER y `isApproved=false`. El User queda vinculado a la organización
     * pero todavía sin ExternalCustomer asociado — eso se completa en una
     * segunda pantalla donde el revendedor elige sucursal, modalidad y hora.
     *
     * Dividir en dos pasos permite leer las sucursales de la org (requiere auth)
     * antes de pedirle al revendedor que elija una.
     */
    suspend fun signupResellerAccount(
        code: String,
        email: String,
        password: String,
        displayName: String
    ): Result<User>

    /**
     * Paso 2 de alta de revendedor: crea el [com.bakery.erp.model.ExternalCustomer]
     * vinculado al User actual (debe estar autenticado). Estado inicial =
     * `PENDING_APPROVAL`. Valida que la modalidad de entrega sea compatible con la
     * configuración de la organización.
     */
    suspend fun completeResellerProfile(
        customerDraft: ExternalCustomer
    ): Result<ExternalCustomer>

    /** Actualiza la flag de aceptar entregas a domicilio para revendedores. */
    suspend fun setAcceptsResellerDelivery(orgId: String, accepts: Boolean): Result<Unit>

    /**
     * Enciende o apaga los flujos de compra opcionales de la empresa (reventa, suministros,
     * servicios). La materia prima no se configura — es el flujo base.
     */
    suspend fun setPurchaseFlows(
        orgId: String,
        usesResale: Boolean,
        usesSupplies: Boolean,
        usesServices: Boolean
    ): Result<Unit>

    /** Regenera el código de invitación de la organización. Invalida el código viejo. */
    suspend fun regenerateInviteCode(orgId: String): Result<String>
}
