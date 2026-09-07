package com.bakery.erp.domain

import com.bakery.erp.model.EmployeeDocument

interface EmployeeDocumentRepository {
    suspend fun getDocuments(companyId: String, employeeId: String): List<EmployeeDocument>
    suspend fun uploadDocument(companyId: String, employeeId: String, doc: EmployeeDocument, bytes: ByteArray): EmployeeDocument
    suspend fun deleteDocument(companyId: String, employeeId: String, documentId: String)
    suspend fun uploadPhoto(companyId: String, employeeId: String, bytes: ByteArray): String
}
