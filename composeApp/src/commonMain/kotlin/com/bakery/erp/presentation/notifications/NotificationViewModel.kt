package com.bakery.erp.presentation.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.NotificationRepository
import com.bakery.erp.domain.PushTokenProvider
import com.bakery.erp.model.AppNotification
import com.bakery.erp.model.User
import com.bakery.erp.model.isReadBy
import com.bakery.erp.model.isVisibleTo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationUiState(
    /** Ya filtrados por sucursal y permiso del usuario actual. */
    val notifications: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

class NotificationViewModel(
    private val notificationRepository: NotificationRepository,
    private val pushTokenProvider: PushTokenProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null

    /**
     * Suscribe la campana. Un solo listener por organización: el filtro por
     * sucursal/permiso es en memoria porque un aviso puede apuntar a varias
     * sucursales y Firestore no cruza array-contains con permiso en una consulta.
     */
    fun observe(companyId: String, user: User) {
        if (companyId.isEmpty()) return
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            notificationRepository.observeNotifications(companyId)
                .catch { err -> _uiState.update { it.copy(error = err.message, isLoading = false) } }
                .collect { all ->
                    // El propio autor no recibe su aviso: ya sabe lo que hizo.
                    val mine = all
                        .filter { it.createdByUserId != user.id }
                        .filter { it.isVisibleTo(user) }
                    _uiState.update {
                        it.copy(
                            notifications = mine,
                            unreadCount = mine.count { n -> !n.isReadBy(user.id) },
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun stopObserving() {
        observationJob?.cancel()
        observationJob = null
    }

    fun markAsRead(companyId: String, notificationId: String, userId: String) {
        viewModelScope.launch {
            runCatching { notificationRepository.markAsRead(companyId, notificationId, userId) }
        }
    }

    fun markAllAsRead(companyId: String, userId: String) {
        val unread = _uiState.value.notifications
            .filter { !it.isReadBy(userId) }
            .map { it.id }
        if (unread.isEmpty()) return
        viewModelScope.launch {
            runCatching { notificationRepository.markAllAsRead(companyId, unread, userId) }
        }
    }

    /**
     * Asocia este dispositivo al usuario para que le lleguen los push. Se llama
     * en cada login: el token de FCM puede rotar (reinstalación, limpieza de
     * datos) y re-registrarlo sobreescribe el doc en vez de duplicarlo.
     *
     * Silencioso a propósito — que el push no esté disponible no es un error que
     * el usuario deba ver; los avisos siguen llegando a la campana.
     */
    fun registerCurrentDevice(companyId: String, userId: String) {
        if (companyId.isEmpty() || userId.isEmpty()) return
        viewModelScope.launch {
            val token = runCatching { pushTokenProvider.currentToken() }.getOrNull() ?: return@launch
            runCatching {
                notificationRepository.registerDeviceToken(
                    companyId, userId, token, pushTokenProvider.platform
                )
            }
        }
    }

    /** Al cerrar sesión: deja de recibir push de esa empresa en este aparato. */
    fun unregisterCurrentDevice(companyId: String) {
        viewModelScope.launch {
            val token = runCatching { pushTokenProvider.currentToken() }.getOrNull() ?: return@launch
            runCatching { notificationRepository.unregisterDeviceToken(companyId, token) }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
