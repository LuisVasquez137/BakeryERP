package com.bakery.erp.presentation.fel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.FelRepository
import com.bakery.erp.model.Branch
import com.bakery.erp.model.FelBranchAssignment
import com.bakery.erp.model.FelCredentialsInput
import com.bakery.erp.model.FelProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FelUiState(
    val profiles: List<FelProfile> = emptyList(),
    val branches: List<Branch> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** Se guardó el perfil: la pantalla del formulario vuelve a la lista. */
    val isSaved: Boolean = false,
    val error: String? = null
) {
    fun profile(id: String?): FelProfile? = profiles.firstOrNull { it.id == id }

    /** Sucursales que facturan con [profileId]. */
    fun branchesOf(profileId: String): List<Branch> = branches.filter { it.felProfileId == profileId }
}

class FelViewModel(
    private val felRepository: FelRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FelUiState())
    val uiState: StateFlow<FelUiState> = _uiState.asStateFlow()

    fun load(companyId: String) {
        if (companyId.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val profiles = async { felRepository.getProfiles(companyId) }
                val branches = async { branchRepository.getBranches(companyId) }
                profiles.await().sortedBy { it.nombreComercial.lowercase() } to
                    branches.await().filter { it.active }.sortedBy { it.name.lowercase() }
            }.onSuccess { (profiles, branches) ->
                _uiState.update { it.copy(profiles = profiles, branches = branches, isLoading = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "No se pudo cargar la facturación.") }
            }
        }
    }

    /**
     * Guarda el perfil y después sus sucursales. En ese orden a propósito: si Digifact rechaza
     * las credenciales, la función lanza y las sucursales no se tocan — reasignarlas a un NIT
     * que no puede certificar dejaría la caja sin facturar.
     */
    fun save(
        companyId: String,
        profile: FelProfile,
        credentials: FelCredentialsInput?,
        assignments: List<FelBranchAssignment>
    ) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val profileId = felRepository.saveProfile(companyId, profile, credentials)
                val assignedIds = assignments.map { it.branchId }.toSet()
                val released = _uiState.value.branches
                    .filter { it.felProfileId == profileId && it.id !in assignedIds }
                    .map { it.id }
                felRepository.assignBranches(companyId, profileId, assignments, released)
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
                load(companyId)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = readableError(e)) }
            }
        }
    }

    fun consumeSaved() = _uiState.update { it.copy(isSaved = false) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    /** El SDK antepone el código ("FAILED_PRECONDITION: …"): a quien configura le sirve el motivo. */
    private fun readableError(e: Throwable): String {
        val msg = e.message ?: return "No se pudo guardar."
        return msg.replaceFirst(Regex("^[A-Z_]+:\\s*"), "").ifBlank { msg }
    }
}
