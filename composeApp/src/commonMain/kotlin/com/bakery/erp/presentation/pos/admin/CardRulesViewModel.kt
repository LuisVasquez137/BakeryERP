package com.bakery.erp.presentation.pos.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.PaymentRulesRepository
import com.bakery.erp.model.CardPaymentRules
import com.bakery.erp.model.cardRulesProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CardRulesUiState(
    val rules: CardPaymentRules? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedAt: Long = 0L
)

/** Reglas de pago con tarjeta de la empresa: mínimo y recargo. */
class CardRulesViewModel(
    private val repository: PaymentRulesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardRulesUiState())
    val uiState: StateFlow<CardRulesUiState> = _uiState.asStateFlow()

    fun load(companyId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.getCardRules(companyId) }
                .onSuccess { r -> _uiState.update { it.copy(isLoading = false, rules = r) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun save(companyId: String, rules: CardPaymentRules, userName: String) {
        cardRulesProblem(rules)?.let { p -> _uiState.update { it.copy(error = p) }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                repository.saveCardRules(companyId, rules.copy(updatedByName = userName))
                repository.getCardRules(companyId)
            }.onSuccess { r ->
                _uiState.update {
                    it.copy(isSaving = false, rules = r, savedAt = kotlin.time.Clock.System.now().toEpochMilliseconds())
                }
            }.onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
