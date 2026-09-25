package com.bakery.erp.domain

import com.bakery.erp.domain.report.PayrollSummary
import com.bakery.erp.model.PayrollRecord

interface PayrollRepository {
    suspend fun getPayrollHistory(companyId: String, employeeId: String): List<PayrollRecord>
    suspend fun getPayrollRecord(companyId: String, employeeId: String, payrollId: String): PayrollRecord?
    /** Devuelve la planilla guardada, con su id (el descuento de créditos se identifica por él). */
    suspend fun savePayroll(companyId: String, employeeId: String, record: PayrollRecord): PayrollRecord
    suspend fun deletePayroll(companyId: String, employeeId: String, payrollId: String)

    /**
     * Records con [paymentDate] dentro de `[fromMillis, untilMillis)` (a través de todos los
     * empleados de la organización). Solo cuenta los APPROVED y PAID.
     */
    suspend fun getPayrollForPeriod(
        companyId: String,
        fromMillis: Long,
        untilMillis: Long
    ): List<PayrollRecord>

    suspend fun aggregatePayrollForPeriod(
        companyId: String,
        fromMillis: Long,
        untilMillis: Long
    ): PayrollSummary
}
