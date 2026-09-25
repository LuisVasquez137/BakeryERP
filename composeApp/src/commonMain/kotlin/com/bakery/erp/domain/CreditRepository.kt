package com.bakery.erp.domain

import com.bakery.erp.model.CreditAccount
import com.bakery.erp.model.CreditMovement
import com.bakery.erp.model.Employee

/**
 * Cuentas de crédito (clientes, instituciones y colaboradores) y su estado de cuenta.
 *
 * El saldo de la cuenta es una caché: todo lo que escribe o borra un movimiento lo recalcula
 * sumando la colección, nunca sumando o restando sobre el valor guardado.
 */
interface CreditRepository {
    suspend fun getAccounts(companyId: String): List<CreditAccount>
    suspend fun getAccount(companyId: String, accountId: String): CreditAccount?

    /** Crea o edita la cuenta. Conserva el saldo guardado: ése solo lo mueven los movimientos. */
    suspend fun saveAccount(companyId: String, account: CreditAccount): CreditAccount

    /**
     * Cuenta del colaborador, creándola si no existe. Id fijo (`emp_<id>`), así dos pantallas
     * que la pidan a la vez terminan en el mismo documento. Mantiene el nombre al día.
     */
    suspend fun ensureEmployeeAccount(companyId: String, employee: Employee): CreditAccount

    suspend fun getMovements(companyId: String, accountId: String): List<CreditMovement>

    /** Agrega un movimiento (id nuevo) y recalcula el saldo. */
    suspend fun addMovement(companyId: String, movement: CreditMovement): CreditMovement

    /** Escribe un movimiento con id fijo (el descuento de una planilla) y recalcula el saldo. */
    suspend fun setMovement(companyId: String, movement: CreditMovement): CreditMovement

    /** Rehace saldo y cargos abiertos desde los movimientos (cuentas anteriores al cálculo FIFO). */
    suspend fun recalculate(companyId: String, accountId: String)

    /** Borra un movimiento de id fijo si existe y recalcula el saldo. */
    suspend fun deleteMovement(companyId: String, accountId: String, movementId: String)
}
