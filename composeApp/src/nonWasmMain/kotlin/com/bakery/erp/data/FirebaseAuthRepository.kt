package com.bakery.erp.data

import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.domain.AuthState
import com.bakery.erp.model.Organization
import com.bakery.erp.model.User
import com.bakery.erp.model.UserRole
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
                        emit(AuthState.Authenticated(doc.toDomain()))
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
                            isActive = true, isApproved = true
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

        val doc = usersCol().document(uid).get()
        if (doc.exists) {
            doc.toDomain()
        } else {
            // Primera vez con Google: crear perfil mínimo sin org
            val user = User(
                id = uid, orgId = "", email = firebaseUser.email ?: "",
                displayName = firebaseUser.displayName ?: "",
                role = UserRole.OWNER, branchIds = emptyList(), isActive = true
            )
            usersCol().document(uid).set(user.toMap())
            user
        }
    }

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
            inviteCode = runCatching { doc.get<String>("inviteCode") }.getOrNull() ?: ""
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

    override suspend fun updateUserAccess(userId: String, branchIds: List<String>, permissions: List<String>): Result<Unit> =
        runCatching {
            val doc = usersCol().document(userId).get()
            val current = doc.toDomain()
            val updated = current.copy(
                branchIds = branchIds,
                permissions = permissions,
                isActive = true,
                isApproved = true
            )
            usersCol().document(userId).set(updated.toMap())
        }

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
        isApproved = get<Boolean?>("isApproved") ?: true  // default true → usuarios existentes no se bloquean
    )

    private fun User.toMap(): Map<String, Any?> = firestoreSafeOf(
        "orgId" to orgId, "email" to email, "displayName" to displayName,
        "role" to role.name, "branchIds" to ArrayList(branchIds),
        "permissions" to ArrayList(permissions),
        "isActive" to isActive, "isApproved" to isApproved
    )
}
