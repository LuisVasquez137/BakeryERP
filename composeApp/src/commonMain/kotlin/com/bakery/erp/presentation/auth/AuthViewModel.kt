package com.bakery.erp.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.domain.AuthState
import com.bakery.erp.domain.GoogleAuthHandler
import com.bakery.erp.model.User
import com.bakery.erp.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val error: String? = null,
    val isAuthenticated: Boolean = false,
    val needsOnboarding: Boolean = false,  // true cuando aún no tiene organización
    val isPendingApproval: Boolean = false, // true cuando tiene org pero admin aún no aprobó
    val inviteCode: String? = null
)

class AuthViewModel(
    private val repository: AuthRepository,
    private val googleAuthHandler: GoogleAuthHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    val isGoogleAvailable: Boolean = googleAuthHandler.isAvailable

    init {
        repository.authState
            .onEach { state ->
                when (state) {
                    is AuthState.Loading -> _uiState.update {
                        it.copy(isLoading = true, error = null)
                    }
                    is AuthState.Authenticated -> {
                        val hasOrg = state.user.orgId.isNotEmpty()
                        val approved = state.user.isApproved
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                user = state.user,
                                isAuthenticated = hasOrg && approved,
                                isPendingApproval = hasOrg && !approved,
                                needsOnboarding = !hasOrg,
                                error = null
                            )
                        }
                        if (hasOrg && approved) loadInviteCode()
                    }
                    is AuthState.Unauthenticated -> _uiState.update {
                        it.copy(isLoading = false, user = null, isAuthenticated = false, needsOnboarding = false, error = null)
                    }
                    is AuthState.Error -> _uiState.update {
                        it.copy(isLoading = false, error = state.message)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.signIn(email, password)
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Error al iniciar sesión") }
                }
        }
    }

    fun register(email: String, password: String, orgName: String, displayName: String, role: UserRole = UserRole.OWNER) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.registerUser(email, password, orgName, displayName, role)
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Error al registrar") }
                }
        }
    }

    fun createOrg(orgName: String, displayName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.createOrgForExistingUser(orgName, displayName)
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false, user = user,
                            isAuthenticated = true, needsOnboarding = false, error = null
                        )
                    }
                    loadInviteCode()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Error al crear la panadería")
                    }
                }
        }
    }

    fun joinOrg(code: String, displayName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.joinOrgForExistingUser(code, displayName)
                .onSuccess { user ->
                    // isApproved=false → PendingApprovalScreen, no a Home
                    _uiState.update {
                        it.copy(
                            isLoading = false, user = user,
                            isAuthenticated = false,
                            isPendingApproval = user.orgId.isNotEmpty(),
                            needsOnboarding = user.orgId.isEmpty(),
                            error = null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Error al unirse a la organización")
                    }
                }
        }
    }

    fun joinWithInviteCode(code: String, displayName: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.joinWithInviteCode(code, displayName, email, password)
                .onSuccess { user ->
                    // Siempre pendiente de aprobación — el authState flow puede dispararse
                    // antes de que el doc de Firestore esté escrito, así que lo seteamos explícitamente
                    _uiState.update {
                        it.copy(
                            isLoading = false, user = user,
                            isAuthenticated = false,
                            isPendingApproval = user.orgId.isNotEmpty(),
                            needsOnboarding = user.orgId.isEmpty(),
                            error = null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Error al unirse a la organización") }
                }
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val idToken = try {
                googleAuthHandler.getIdToken()
            } catch (e: Exception) {
                // Popup cancelled or error (e.g. auth/popup-closed-by-user)
                _uiState.update { it.copy(isLoading = false, error = null) }
                return@launch
            }
            if (idToken == null) {
                // Web: signInWithPopup already authenticated — authState flow handles navigation.
                // Keep isLoading=true until the flow emits.
                return@launch
            }
            repository.signInWithGoogle(idToken)
                .onSuccess { user ->
                    // Actualiza el estado directamente para evitar la race condition
                    // entre signInWithCredential y authStateChanged de Firebase
                    val hasOrg = user.orgId.isNotEmpty()
                    val approved = user.isApproved
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            user = user,
                            isAuthenticated = hasOrg && approved,
                            isPendingApproval = hasOrg && !approved,
                            needsOnboarding = !hasOrg,
                            error = null
                        )
                    }
                    if (hasOrg && approved) loadInviteCode()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Error con Google Sign-In") }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch { repository.signOut() }
    }

    fun refreshUser() {
        viewModelScope.launch {
            val user = repository.getCurrentUser() ?: return@launch
            val hasOrg = user.orgId.isNotEmpty()
            val approved = user.isApproved
            _uiState.update {
                it.copy(
                    user = user,
                    isAuthenticated = hasOrg && approved,
                    isPendingApproval = hasOrg && !approved,
                    needsOnboarding = !hasOrg
                )
            }
            if (hasOrg && approved) loadInviteCode()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadInviteCode() {
        viewModelScope.launch {
            repository.getOrgInviteCode()
                .onSuccess { code -> _uiState.update { it.copy(inviteCode = code) } }
        }
    }
}
