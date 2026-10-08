package com.bakery.erp.presentation.credit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.CreditRepository
import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.model.CreditAccount
import com.bakery.erp.model.CreditAccountType
import com.bakery.erp.model.CreditMovement
import com.bakery.erp.model.CreditMovementType
import com.bakery.erp.model.EmployeeStatus
import com.bakery.erp.model.User
import com.bakery.erp.model.employeeCreditAccountId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreditUiState(
    val accounts: List<CreditAccount> = emptyList(),
    val selected: CreditAccount? = null,
    /** Estado de cuenta de [selected], del más nuevo al más viejo. */
    val movements: List<CreditMovement> = emptyList(),
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

/**
 * Módulo de créditos: cuentas, personas autorizadas y lo que se registra fuera de una caja
 * (pagos por transferencia, saldo anterior del cuaderno, adelantos de caja general).
 *
 * Lo que pasa EN una caja —vender a crédito, cobrar un abono, dar un adelanto desde la
 * gaveta— lo hace `POSViewModel`, que es quien conoce la sesión abierta. Este ViewModel lo
 * usa además el POS para buscar cuentas.
 */
class CreditViewModel(
    private val creditRepository: CreditRepository,
    private val employeeRepository: EmployeeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreditUiState())
    val uiState: StateFlow<CreditUiState> = _uiState.asStateFlow()

    /**
     * Cuentas de clientes e instituciones + una por colaborador activo. Las de colaborador se
     * crean acá la primera vez: salen de Colaboradores y nadie tiene que darlas de alta.
     */
    fun load(companyId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val accounts = creditRepository.getAccounts(companyId).associateBy { it.id }.toMutableMap()
                employeeRepository.getEmployees(companyId)
                    .filter { it.active && it.status != EmployeeStatus.FIRED && it.status != EmployeeStatus.RESIGNED }
                    .forEach { emp ->
                        val existing = accounts[employeeCreditAccountId(emp.id)]
                        if (existing == null || existing.name != emp.name) {
                            accounts[employeeCreditAccountId(emp.id)] =
                                creditRepository.ensureEmployeeAccount(companyId, emp)
                        }
                    }
                // Cuentas con saldo de antes de que existiera el cálculo de antigüedad: se
                // rehacen una vez, si no el reporte las mostraría sin antigüedad.
                accounts.values.filter { it.balance > 0.005 && it.openChargeDates.isEmpty() }.forEach { acc ->
                    creditRepository.recalculate(companyId, acc.id)
                    creditRepository.getAccount(companyId, acc.id)?.let { accounts[acc.id] = it }
                }
                accounts.values.sortedBy { it.name.lowercase() }
            }.onSuccess { list ->
                _uiState.update { st ->
                    st.copy(
                        isLoading = false,
                        accounts = list,
                        selected = st.selected?.let { sel -> list.firstOrNull { it.id == sel.id } ?: sel }
                    )
                }
            }.onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun loadAccount(companyId: String, accountId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, error = null) }
            runCatching {
                creditRepository.getAccount(companyId, accountId) to
                    creditRepository.getMovements(companyId, accountId)
            }.onSuccess { (account, movements) ->
                _uiState.update {
                    it.copy(isDetailLoading = false, selected = account, movements = movements,
                        accounts = if (account == null) it.accounts
                                   else it.accounts.map { a -> if (a.id == account.id) account else a })
                }
            }.onFailure { e -> _uiState.update { it.copy(isDetailLoading = false, error = e.message) } }
        }
    }

    /** Abre la cuenta del colaborador (creándola si hace falta) — la usa su ficha. */
    fun loadEmployeeAccount(companyId: String, employeeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, error = null) }
            runCatching {
                val employee = employeeRepository.getEmployee(companyId, employeeId)
                    ?: throw IllegalStateException("Colaborador no encontrado")
                val account = creditRepository.ensureEmployeeAccount(companyId, employee)
                account to creditRepository.getMovements(companyId, account.id)
            }.onSuccess { (account, movements) ->
                _uiState.update { it.copy(isDetailLoading = false, selected = account, movements = movements) }
            }.onFailure { e -> _uiState.update { it.copy(isDetailLoading = false, error = e.message) } }
        }
    }

    fun clearSelected() { _uiState.update { it.copy(selected = null, movements = emptyList()) } }

    fun saveAccount(companyId: String, account: CreditAccount) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching { creditRepository.saveAccount(companyId, account) }
                .onSuccess { saved ->
                    _uiState.update { st ->
                        st.copy(
                            isSaving = false,
                            isSaved = true,
                            selected = saved,
                            accounts = (st.accounts.filterNot { it.id == saved.id } + saved)
                                .sortedBy { it.name.lowercase() }
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    /**
     * Pago registrado fuera de una caja: una transferencia al banco, un cheque, o efectivo que
     * recibió administración. No toca ninguna gaveta — para eso está "Cobrar crédito" en el POS.
     */
    fun registerPayment(
        companyId: String, account: CreditAccount, amount: Double, method: String,
        note: String, user: User
    ) = write(companyId, account, CreditMovement(
        accountId = account.id,
        accountName = account.name,
        type = CreditMovementType.PAYMENT,
        amount = -amount,
        method = method,
        description = note.ifBlank { "Pago registrado por administración" },
        createdByUserId = user.id,
        createdByUserName = user.displayName,
        createdByEmployeeId = user.operatorEmployeeId
    ), "Pago registrado")

    /**
     * Ajuste con signo: positivo aumenta la deuda (el saldo que traía del cuaderno), negativo
     * la baja (una corrección, una condonación). La razón es obligatoria: es lo único que
     * explica el renglón en el estado de cuenta.
     */
    fun registerAdjustment(
        companyId: String, account: CreditAccount, signedAmount: Double, reason: String, user: User
    ) = write(companyId, account, CreditMovement(
        accountId = account.id,
        accountName = account.name,
        type = CreditMovementType.ADJUSTMENT,
        amount = signedAmount,
        description = reason,
        createdByUserId = user.id,
        createdByUserName = user.displayName,
        createdByEmployeeId = user.operatorEmployeeId
    ), "Ajuste registrado")

    /** Adelanto entregado por administración, de caja general (no de una gaveta del POS). */
    fun registerAdvance(
        companyId: String, account: CreditAccount, amount: Double, note: String, user: User
    ) = write(companyId, account, CreditMovement(
        accountId = account.id,
        accountName = account.name,
        type = CreditMovementType.ADVANCE,
        amount = amount,
        description = note.ifBlank { "Adelanto de caja general" },
        personName = account.name,
        createdByUserId = user.id,
        createdByUserName = user.displayName,
        createdByEmployeeId = user.operatorEmployeeId
    ), "Adelanto registrado")

    private fun write(companyId: String, account: CreditAccount, movement: CreditMovement, okMessage: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                creditRepository.addMovement(companyId, movement)
                creditRepository.getAccount(companyId, account.id) to
                    creditRepository.getMovements(companyId, account.id)
            }.onSuccess { (fresh, movements) ->
                _uiState.update { st ->
                    st.copy(
                        isSaving = false,
                        message = okMessage,
                        selected = fresh ?: st.selected,
                        movements = movements,
                        accounts = if (fresh == null) st.accounts
                                   else st.accounts.map { if (it.id == fresh.id) fresh else it }
                    )
                }
            }.onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun clearSaved() { _uiState.update { it.copy(isSaved = false) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearMessage() { _uiState.update { it.copy(message = null) } }

    companion object {
        /** Tipos que se crean a mano; el de colaborador sale solo de Colaboradores. */
        val MANUAL_TYPES = listOf(CreditAccountType.PERSON, CreditAccountType.ORGANIZATION)
    }
}
