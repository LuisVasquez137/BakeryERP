package com.bakery.erp.data

import com.bakery.erp.domain.EmployeeDocumentRepository
import com.bakery.erp.model.DocumentType
import com.bakery.erp.model.EmployeeDocument
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage

class FirebaseEmployeeDocumentRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : EmployeeDocumentRepository {

    private fun col(companyId: String, employeeId: String) =
        firestore.collection("companies/$companyId/employees/$employeeId/documents")

    override suspend fun getDocuments(companyId: String, employeeId: String): List<EmployeeDocument> =
        col(companyId, employeeId).get().documents
            .map { it.toDocument() }
            .sortedByDescending { it.uploadedAt }

    override suspend fun uploadDocument(
        companyId: String,
        employeeId: String,
        doc: EmployeeDocument,
        bytes: ByteArray
    ): EmployeeDocument {
        val ext = if (doc.fileType.contains("pdf", ignoreCase = true)) "pdf" else "jpg"
        val filename = "${doc.type.name.lowercase()}_${System.currentTimeMillis()}.$ext"
        val storagePath = "companies/$companyId/employees/$employeeId/$filename"
        val ref = storage.reference.child(storagePath)
        ref.putData(Data(bytes))
        val url = ref.getDownloadUrl()

        val docRef = if (doc.id.isEmpty()) col(companyId, employeeId).document
                     else col(companyId, employeeId).document(doc.id)
        val now = System.currentTimeMillis()
        val final = doc.copy(id = docRef.id, employeeId = employeeId, fileUrl = url, uploadedAt = now)
        docRef.set(final.toMap())
        return final
    }

    override suspend fun deleteDocument(companyId: String, employeeId: String, documentId: String) {
        col(companyId, employeeId).document(documentId).delete()
    }

    override suspend fun uploadPhoto(companyId: String, employeeId: String, bytes: ByteArray): String {
        val storagePath = "companies/$companyId/employees/$employeeId/profile_photo.jpg"
        val ref = storage.reference.child(storagePath)
        ref.putData(Data(bytes))
        return ref.getDownloadUrl()
    }

    private fun EmployeeDocument.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "employeeId" to employeeId,
        "type" to type.name,
        "name" to name,
        "fileUrl" to fileUrl,
        "fileType" to fileType,
        "uploadedAt" to uploadedAt,
        "uploadedBy" to uploadedBy
    )

    private fun DocumentSnapshot.toDocument(): EmployeeDocument {
        val typeStr = runCatching { get<String?>("type") }.getOrNull()
        val type = typeStr?.let { runCatching { DocumentType.valueOf(it) }.getOrNull() }
            ?: DocumentType.OTHER
        return EmployeeDocument(
            id = id,
            employeeId = get<String?>("employeeId") ?: "",
            type = type,
            name = get<String?>("name") ?: "",
            fileUrl = get<String?>("fileUrl") ?: "",
            fileType = get<String?>("fileType") ?: "",
            uploadedAt = runCatching { get<Long?>("uploadedAt") }.getOrNull() ?: 0L,
            uploadedBy = get<String?>("uploadedBy") ?: ""
        )
    }
}
