package com.bakery.erp.model

enum class HistoryEventType {
    PAYMENT, ADVANCE, INFRACTION, WARNING, SUSPENSION,
    REINSTATEMENT, LEAVE_PERMIT, POSITION_CHANGE, NOTE
}

data class EmployeeHistoryEntry(
    val id: String = "",
    val employeeId: String = "",
    val type: HistoryEventType = HistoryEventType.NOTE,
    val description: String = "",
    val amount: Double? = null,
    val date: Long = 0L,
    val createdBy: String = "",
    val createdAt: Long = 0L
)
