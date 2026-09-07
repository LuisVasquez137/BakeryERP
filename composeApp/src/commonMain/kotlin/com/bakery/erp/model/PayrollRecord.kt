package com.bakery.erp.model

enum class PayrollStatus { DRAFT, APPROVED, PAID }

data class PayrollRecord(
    val id: String = "",
    val employeeId: String = "",
    val employeeName: String = "",
    val periodStart: Long = 0L,
    val periodEnd: Long = 0L,
    // Ingresos
    val baseSalary: Double = 0.0,
    val overtimePay: Double = 0.0,
    val tips: Double = 0.0,
    val advances: Double = 0.0,
    val bonuses: Double = 0.0,
    // Deducciones
    val igssEmployee: Double = 0.0,
    val igssEmployer: Double = 0.0,
    val isr: Double = 0.0,
    val otherDeductions: Double = 0.0,
    // Vacaciones / Indemnización
    val vacationDays: Double = 0.0,
    val vacationPay: Double = 0.0,
    val severancePay: Double = 0.0,
    // Totales
    val totalGross: Double = 0.0,
    val totalNet: Double = 0.0,
    // Pago
    val paymentDate: Long = 0L,
    val paymentMethod: String = "",
    val status: PayrollStatus = PayrollStatus.DRAFT,
    val notes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
