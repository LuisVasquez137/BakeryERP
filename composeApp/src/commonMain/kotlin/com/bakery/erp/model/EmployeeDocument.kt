package com.bakery.erp.model

enum class DocumentType {
    CONTRACT, DPI, BACKGROUND_CHECK, POLICE_RECORD, CV, CERTIFICATION, HEALTH_CARD, SIGNATURE, OTHER
}

data class EmployeeDocument(
    val id: String = "",
    val employeeId: String = "",
    val type: DocumentType = DocumentType.OTHER,
    val name: String = "",
    val fileUrl: String = "",
    val fileType: String = "",
    val uploadedAt: Long = 0L,
    val uploadedBy: String = ""
)
