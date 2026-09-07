package com.bakery.erp.data

import com.bakery.erp.domain.EmployeeDocumentRepository
import com.bakery.erp.model.EmployeeDocument

class NoOpEmployeeDocumentRepository : EmployeeDocumentRepository {
    override suspend fun getDocuments(companyId: String, employeeId: String): List<EmployeeDocument> = emptyList()
    override suspend fun uploadDocument(companyId: String, employeeId: String, doc: EmployeeDocument, bytes: ByteArray): EmployeeDocument = doc
    override suspend fun deleteDocument(companyId: String, employeeId: String, documentId: String) = Unit
    override suspend fun uploadPhoto(companyId: String, employeeId: String, bytes: ByteArray): String = ""
}
