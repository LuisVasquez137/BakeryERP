package com.bakery.erp.domain

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
    suspend fun getUsersForOrg(orgId: String): List<User>
    suspend fun updateUserAccess(userId: String, branchIds: List<String>, permissions: List<String>): Result<Unit>
}
