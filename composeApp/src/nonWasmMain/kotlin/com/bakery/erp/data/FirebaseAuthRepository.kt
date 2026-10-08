package com.bakery.erp.data

import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.domain.AuthState
import com.bakery.erp.model.ExternalCustomer
import com.bakery.erp.model.ExternalCustomerType
import com.bakery.erp.model.Organization
import com.bakery.erp.model.ResellerDeliveryMode
import com.bakery.erp.model.ResellerRegistrationStatus
import com.bakery.erp.model.User
import com.bakery.erp.model.UserRole
import kotlin.time.Clock
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    private fun usersCol() = firestore.collection("users")
    private fun orgsCol() = firestore.collection("organizations")
    private fun inviteCodesCol() = firestore.collection("inviteCodes")

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    override val authState: Flow<AuthState> = auth.authStateChanged
        .flatMapLatest { firebaseUser ->
            if (firebaseUser == null) {
                flowOf(AuthState.Unauthenticated)
            } else {
                flow<AuthState> {
                    val uid = firebaseUser.uid
                    val doc = usersCol().document(uid).get()
                    if (doc.exists) {
                        emit(AuthState.Authenticated(backfillGooglePhoto(uid, doc.toDomain(), firebaseUser.photoURL)))
                    } else {
                        // Usuario autenticado en Firebase Auth pero sin perfil Firestore aún.
                        // Puede ser un nuevo usuario Google en mitad del onboarding, o una
                        // condición de carrera donde authStateChanged se disparó antes de
                        // que se escribiera el doc. Emitir Authenticated(minimal) para que
                        // needsOnboarding=true en lugar de desloguear al usuario.
                        emit(AuthState.Authenticated(User(
                            id = uid, orgId = "",
                            email = firebaseUser.email ?: "",
                            displayName = firebaseUser.displayName ?: "",
                            role = UserRole.OWNER, branchIds = emptyList(),
                            isActive = true, isApproved = true,
                            photoUrl = firebaseUser.photoURL
                        )))
                    }
                }.catch { e -> emit(AuthState.Error(e.message ?: "Error desconocido")) }
            }
        }

    override suspend fun signIn(email: String, password: String): Result<User> =
        runCatching {
            val result = auth.signInWithEmailAndPassword(email, password)
            val uid = result.user?.uid ?: error("UID nulo tras login exitoso")
            val doc = usersCol().document(uid).get()
            if (!doc.exists) error("Perfil de usuario no encontrado en Firestore")
            doc.toDomain()
        }

    override suspend fun registerUser(
        email: String,
        password: String,
        orgName: String,
        displayName: String,
        role: UserRole
    ): Result<User> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password)
        val uid = result.user?.uid ?: error("UID nulo tras registro")

        val orgRef = orgsCol().document
        val orgId = orgRef.id
        val inviteCode = generateInviteCode()

        val user = User(
            id = uid, orgId = orgId, email = email,
            displayName = displayName, role = role,
            branchIds = emptyList(), isActive = true
        )

        // 1. Perfil de usuario primero (callerOrgId() lo necesita en lecturas futuras)
        usersCol().document(uid).set(user.toMap())

        // 2. Organización (la regla permite create porque ownerEmail == token.email)
        orgRef.set(mapOf(
            "name" to orgName, "ownerEmail" to email,
            "subscriptionPlan" to "free", "inviteCode" to inviteCode,
            "createdAt" to FieldValue.serverTimestamp
        ))

        // 3. Índice de código de invitación para búsqueda rápida
        inviteCodesCol().document(inviteCode).set(mapOf(
            "orgId" to orgId, "orgName" to orgName,
            "createdAt" to FieldValue.serverTimestamp
        ))

        user
    }

    override suspend fun createOrgForExistingUser(
        orgName: String,
        displayName: String
    ): Result<User> = runCatching {
        val firebaseUser = auth.currentUser ?: error("No hay usuario autenticado")
        val uid = firebaseUser.uid
        val email = firebaseUser.email ?: ""

        val orgRef = orgsCol().document
        val orgId = orgRef.id
        val inviteCode = generateInviteCode()

        val user = User(
            id = uid, orgId = orgId, email = email,
            displayName = displayName, role = UserRole.OWNER,
            branchIds = emptyList(), isActive = true
        )

        usersCol().document(uid).set(user.toMap())
        orgRef.set(mapOf(
            "name" to orgName, "ownerEmail" to email,
            "subscriptionPlan" to "free", "inviteCode" to inviteCode,
            "createdAt" to FieldValue.serverTimestamp
        ))
        inviteCodesCol().document(inviteCode).set(mapOf(
            "orgId" to orgId, "orgName" to orgName,
            "createdAt" to FieldValue.serverTimestamp
        ))
        user
    }

    override suspend fun joinOrgForExistingUser(
        code: String,
        displayName: String
    ): Result<User> = runCatching {
        val firebaseUser = auth.currentUser ?: error("No hay usuario autenticado")
        val uid = firebaseUser.uid
        val email = firebaseUser.email ?: ""

        val codeDoc = inviteCodesCol().document(code.uppercase()).get()
        if (!codeDoc.exists) error("Código de invitación inválido: ${code.uppercase()}")

        val orgId = codeDoc.get<String>("orgId")
        val user = User(
            id = uid, orgId = orgId, email = email,
            displayName = displayName, role = UserRole.EMPLOYEE,
            branchIds = emptyList(), isActive = false, isApproved = false
        )
        usersCol().document(uid).set(user.toMap())
        user
    }

    override suspend fun joinWithInviteCode(
        code: String,
        displayName: String,
        email: String,
        password: String
    ): Result<User> = runCatching {
        // 1. Crear cuenta primero — las reglas de Firestore exigen auth != null para leer inviteCodes
        val authResult = auth.createUserWithEmailAndPassword(email, password)
        val uid = authResult.user?.uid ?: error("UID nulo tras registro")

        // 2. Leer el código (ahora autenticado)
        val codeDoc = inviteCodesCol().document(code.uppercase()).get()
        if (!codeDoc.exists) {
            // Rollback: eliminar cuenta recién creada si el código es inválido
            auth.currentUser?.delete()
            error("Código de invitación inválido: ${code.uppercase()}")
        }

        val orgId = codeDoc.get<String>("orgId")
        val user = User(
            id = uid, orgId = orgId, email = email,
            displayName = displayName, role = UserRole.EMPLOYEE,
            branchIds = emptyList(), isActive = false, isApproved = false
        )

        // 3. Escribir perfil
        usersCol().document(uid).set(user.toMap())
        user
    }

    override suspend fun signInWithGoogle(idToken: String): Result<User> = runCatching {
        val credential = GoogleAuthProvider.credential(idToken, null)
        val result = auth.signInWithCredential(credential)
        val firebaseUser = result.user ?: error("User nulo tras Google Sign-In")
        val uid = firebaseUser.uid
        val googlePhotoUrl = firebaseUser.photoURL

        val doc = usersCol().document(uid).get()
        if (doc.exists) {
            backfillGooglePhoto(uid, doc.toDomain(), googlePhotoUrl)
        } else {
            // Primera vez con Google: crear perfil mínimo sin org
            val user = User(
                id = uid, orgId = "", email = firebaseUser.email ?: "",
                displayName = firebaseUser.displayName ?: "",
                role = UserRole.OWNER, branchIds = emptyList(), isActive = true,
                photoUrl = googlePhotoUrl
            )
            usersCol().document(uid).set(user.toMap())
            user
        }
    }

    /**
     * Actualiza `photoUrl` en Firestore si la cuenta de Google trae una foto distinta
     * a la guardada (usuarios registrados antes de que existiera este campo, o que
     * cambiaron su avatar de Google). Se llama tanto desde `authStateChanged` — la
     * única ruta que se dispara en Web, donde `signInWithPopup` autentica sin pasar
     * por `signInWithGoogle` — como desde `signInWithGoogle` (Android/iOS) para que
     * el estado se actualice de inmediato sin esperar el siguiente evento del stream.
     */
    private suspend fun backfillGooglePhoto(uid: String, existing: User, googlePhotoUrl: String?): User =
        if (!googlePhotoUrl.isNullOrBlank() && googlePhotoUrl != existing.photoUrl) {
            usersCol().document(uid).update("photoUrl" to googlePhotoUrl)
            existing.copy(photoUrl = googlePhotoUrl)
        } else existing

    override suspend fun signOut() = auth.signOut()

    override suspend fun getOrgInviteCode(): Result<String> = runCatching {
        val uid = auth.currentUser?.uid ?: error("No autenticado")
        val userDoc = usersCol().document(uid).get()
        val orgId = userDoc.get<String>("orgId")
        val orgDoc = orgsCol().document(orgId).get()
        orgDoc.get<String>("inviteCode")
    }

    override suspend fun getOrganization(orgId: String): Result<Organization> = runCatching {
        val doc = orgsCol().document(orgId).get()
        if (!doc.exists) error("Organización no encontrada: $orgId")
        Organization(
            id = doc.id,
            name = runCatching { doc.get<String>("name") }.getOrNull() ?: "",
            ownerEmail = runCatching { doc.get<String>("ownerEmail") }.getOrNull() ?: "",
            subscriptionPlan = runCatching { doc.get<String>("subscriptionPlan") }.getOrNull() ?: "",
            createdAt = runCatching { doc.get<Long>("createdAt") }.getOrNull() ?: 0L,
            inviteCode = runCatching { doc.get<String>("inviteCode") }.getOrNull() ?: "",
            acceptsResellerDelivery = runCatching {
                doc.get<Boolean?>("acceptsResellerDelivery")
            }.getOrNull() ?: false,
            // Flujos de compra. Ausentes = apagados: una empresa ya operando no debe ver
            // secciones nuevas en la captura hasta que las pida.
            usesResalePurchases = runCatching { doc.get<Boolean?>("usesResalePurchases") }.getOrNull() ?: false,
            usesSupplyPurchases = runCatching { doc.get<Boolean?>("usesSupplyPurchases") }.getOrNull() ?: false,
            usesServicePurchases = runCatching { doc.get<Boolean?>("usesServicePurchases") }.getOrNull() ?: false
        )
    }

    override suspend fun getCurrentUser(): User? {
        val current = auth.currentUser ?: return null
        return runCatching {
            usersCol().document(current.uid).get().toDomain()
        }.getOrNull()
    }

    override suspend fun getUsersForOrg(orgId: String): List<User> =
        usersCol().where { "orgId" equalTo orgId }.get().documents.map { it.toDomain() }

    override suspend fun updateUserAccess(
        userId: String,
        branchIds: List<String>,
        permissions: List<String>,
        isDeviceAccount: Boolean
    ): Result<Unit> =
        runCatching {
            val doc = usersCol().document(userId).get()
            val current = doc.toDomain()
            val updated = current.copy(
                branchIds = branchIds,
                permissions = permissions,
                // Aprobar a un pendiente lo activa; guardar permisos de alguien desactivado
                // NO lo reactiva por accidente (eso es setUserActive).
                isActive = if (current.isApproved) current.isActive else true,
                isApproved = true,
                isDeviceAccount = isDeviceAccount
            )
            usersCol().document(userId).set(updated.toMap())
        }

    override suspend fun setUserActive(userId: String, active: Boolean): Result<Unit> = runCatching {
        usersCol().document(userId).update("isActive" to active)
    }

    // ── Registro de revendedor con código de invitación (en 2 pasos) ──────────

    override suspend fun signupResellerAccount(
        code: String,
        email: String,
        password: String,
        displayName: String
    ): Result<User> = runCatching {
        // 1. Crear cuenta primero (las reglas exigen auth para leer inviteCodes).
        val authResult = auth.createUserWithEmailAndPassword(email, password)
        val uid = authResult.user?.uid ?: error("UID nulo tras registro")

        // 2. Validar código de invitación.
        val codeDoc = inviteCodesCol().document(code.uppercase()).get()
        if (!codeDoc.exists) {
            auth.currentUser?.delete()
            error("Código de invitación inválido: ${code.uppercase()}")
        }
        val orgId = codeDoc.get<String>("orgId")

        // 3. Crear el User con rol RESELLER y aprobación pendiente.
        val user = User(
            id = uid,
            orgId = orgId,
            email = email,
            displayName = displayName,
            role = UserRole.RESELLER,
            branchIds = emptyList(),
            isActive = true,
            isApproved = false
        )
        usersCol().document(uid).set(user.toMap())
        user
    }

    override suspend fun completeResellerProfile(
        customerDraft: ExternalCustomer
    ): Result<ExternalCustomer> = runCatching {
        val firebaseUser = auth.currentUser ?: error("No hay usuario autenticado")
        val uid = firebaseUser.uid
        val userDoc = usersCol().document(uid).get()
        if (!userDoc.exists) error("Perfil de usuario no encontrado")
        val orgId = userDoc.get<String>("orgId")
        if (orgId.isEmpty()) error("Usuario sin organización asignada")

        // Validar modalidad vs config de org.
        val orgDoc = orgsCol().document(orgId).get()
        val acceptsDelivery = runCatching {
            orgDoc.get<Boolean?>("acceptsResellerDelivery")
        }.getOrNull() ?: false
        if (customerDraft.deliveryMode == ResellerDeliveryMode.DELIVERY && !acceptsDelivery) {
            error("Esta panadería no ofrece entregas a domicilio. Elige retirar en sucursal.")
        }

        val customersCol = firestore.collection("companies/$orgId/externalCustomers")
        val customerRef = customersCol.document
        val now = Clock.System.now().toEpochMilliseconds()
        val customer = customerDraft.copy(
            id = customerRef.id,
            type = ExternalCustomerType.RESELLER,
            linkedUserId = uid,
            email = firebaseUser.email ?: customerDraft.email,
            registrationStatus = ResellerRegistrationStatus.PENDING_APPROVAL,
            active = false,
            createdAt = now,
            updatedAt = now
        )
        customerRef.set(customer.toResellerMap())
        customer
    }

    override suspend fun setPurchaseFlows(
        orgId: String,
        usesResale: Boolean,
        usesSupplies: Boolean,
        usesServices: Boolean
    ): Result<Unit> = runCatching {
        orgsCol().document(orgId).update(
            "usesResalePurchases" to usesResale,
            "usesSupplyPurchases" to usesSupplies,
            "usesServicePurchases" to usesServices
        )
    }

    override suspend fun setAcceptsResellerDelivery(
        orgId: String,
        accepts: Boolean
    ): Result<Unit> = runCatching {
        orgsCol().document(orgId).update("acceptsResellerDelivery" to accepts)
    }

    override suspend fun regenerateInviteCode(orgId: String): Result<String> = runCatching {
        val orgDoc = orgsCol().document(orgId).get()
        if (!orgDoc.exists) error("Organización no encontrada: $orgId")
        val orgName = runCatching { orgDoc.get<String>("name") }.getOrNull() ?: ""
        val oldCode = runCatching { orgDoc.get<String>("inviteCode") }.getOrNull() ?: ""

        val newCode = generateInviteCode()
        orgsCol().document(orgId).update("inviteCode" to newCode)
        inviteCodesCol().document(newCode).set(
            mapOf(
                "orgId" to orgId,
                "orgName" to orgName,
                "createdAt" to FieldValue.serverTimestamp
            )
        )
        // Borrar el código viejo para invalidarlo (puede no existir si la org es vieja).
        if (oldCode.isNotEmpty()) {
            runCatching { inviteCodesCol().document(oldCode).delete() }
        }
        newCode
    }

    private fun ExternalCustomer.toResellerMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "type" to type,
        "name" to name,
        "businessName" to businessName,
        "phone" to phone,
        "phoneAlt" to phoneAlt,
        "email" to email,
        "address" to address,
        "taxId" to taxId,
        "priceListId" to priceListId,
        "priceListName" to priceListName,
        "linkedUserId" to linkedUserId,
        "active" to active,
        "deliveryMode" to deliveryMode,
        "preferredBranchId" to preferredBranchId,
        "preferredBranchName" to preferredBranchName,
        "preferredPickupTime" to preferredPickupTime,
        "deliveryLatitude" to deliveryLatitude,
        "deliveryLongitude" to deliveryLongitude,
        "deliveryAddressReference" to deliveryAddressReference,
        "assignedProducingBranchId" to assignedProducingBranchId,
        "assignedProducingBranchName" to assignedProducingBranchName,
        "assignedShiftId" to assignedShiftId,
        "assignedShiftName" to assignedShiftName,
        "registrationStatus" to registrationStatus,
        "notes" to notes,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    // Usar tipos explícitos en get<T>() para evitar que Kotlin infiera star projections (List<*>)
    // lo que causaría "Star projections in type arguments are not allowed" en gitlive SDK 2.x
    private fun DocumentSnapshot.toDomain(): User = User(
        id = id,
        orgId = get<String>("orgId"),
        email = get<String>("email"),
        displayName = get<String?>("displayName") ?: "",
        role = UserRole.valueOf(get<String>("role")),
        branchIds = get<List<String>?>("branchIds") ?: emptyList(),
        permissions = get<List<String>?>("permissions") ?: emptyList(),
        isActive = get<Boolean?>("isActive") ?: true,
        isApproved = get<Boolean?>("isApproved") ?: true,  // default true → usuarios existentes no se bloquean
        photoUrl = get<String?>("photoUrl"),
        isDeviceAccount = runCatching { get<Boolean?>("isDeviceAccount") }.getOrNull() ?: false
    )

    private fun User.toMap(): Map<String, Any?> = firestoreSafeOf(
        "orgId" to orgId, "email" to email, "displayName" to displayName,
        "role" to role.name, "branchIds" to ArrayList(branchIds),
        "permissions" to ArrayList(permissions),
        "isActive" to isActive, "isApproved" to isApproved,
        "photoUrl" to photoUrl, "isDeviceAccount" to isDeviceAccount
    )
}
