package com.bakery.erp.presentation.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserUiState(
    val userList: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

class UserViewModel(
    private val repository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserUiState())
    val uiState: StateFlow<UserUiState> = _uiState.asStateFlow()

    fun loadUsers(orgId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.getUsersForOrg(orgId) }
                .onSuccess { _uiState.update { s -> s.copy(userList = it, isLoading = false) } }
                .onFailure { _uiState.update { s -> s.copy(isLoading = false, error = it.message) } }
        }
    }

    fun updateUserAccess(
        orgId: String,
        userId: String,
        branchIds: List<String>,
        permissions: List<String>,
        isDeviceAccount: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.updateUserAccess(userId, branchIds, permissions, isDeviceAccount)
                .onSuccess {
                    _uiState.update { s -> s.copy(isLoading = false, isSaved = true) }
                    loadUsers(orgId)
                }
                .onFailure { _uiState.update { s -> s.copy(isLoading = false, error = it.message) } }
        }
    }

    fun setUserActive(orgId: String, userId: String, active: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.setUserActive(userId, active)
                .onSuccess {
                    _uiState.update { s -> s.copy(isLoading = false) }
                    loadUsers(orgId)
                }
                .onFailure { _uiState.update { s -> s.copy(isLoading = false, error = it.message) } }
        }
    }

    fun clearSaved() { _uiState.update { it.copy(isSaved = false) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
