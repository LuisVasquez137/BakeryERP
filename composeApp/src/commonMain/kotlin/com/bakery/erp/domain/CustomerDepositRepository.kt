package com.bakery.erp.domain

import com.bakery.erp.model.CustomerDeposit

/**
 * Anticipos de clientes. Aplicar y devolver validan contra el documento recién leído, en
 * transacción: el mismo anticipo puede estar abierto en dos cajas a la vez, y sin eso se
 * aplicaría (o se devolvería) dos veces.
 */
interface CustomerDepositRepository {
    /** Guarda uno nuevo y le asigna el folio correlativo de la empresa. Devuelve el guardado. */
    suspend fun create(companyId: String, deposit: CustomerDeposit): CustomerDeposit

    /** Los que todavía tienen saldo, de todas las sucursales: el cliente recoge donde quiera. */
    suspend fun getOpen(companyId: String): List<CustomerDeposit>

    suspend fun get(companyId: String, id: String): CustomerDeposit?

    /**
     * Descuenta [amount] del saldo. Lanza si ya no alcanza (otra caja lo aplicó primero). Se
     * llama ANTES de escribir la venta; si la venta falla, [release] lo devuelve.
     */
    suspend fun apply(companyId: String, id: String, amount: Double, branchName: String): CustomerDeposit

    /** Deshace un [apply] cuya venta no llegó a escribirse. */
    suspend fun release(companyId: String, id: String, amount: Double)

    /** Deja constancia de qué ventas pagó. No crítico: la venta ya lleva el folio. */
    suspend fun linkSales(companyId: String, id: String, saleIds: List<String>)

    /** Devuelve lo que queda al cliente. Lanza si ya no hay saldo. Devuelve el monto devuelto. */
    suspend fun refund(companyId: String, id: String, byUserName: String, reason: String): Double
}
