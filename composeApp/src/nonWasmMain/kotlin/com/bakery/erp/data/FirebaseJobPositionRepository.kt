package com.bakery.erp.data

import com.bakery.erp.domain.JobPositionRepository
import com.bakery.erp.model.JobPosition
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock

class FirebaseJobPositionRepository(
    private val firestore: FirebaseFirestore
) : JobPositionRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/jobPositions")

    override suspend fun getJobPositions(companyId: String): List<JobPosition> =
        col(companyId).get().documents
            .map { it.toPosition() }
            .sortedWith(compareBy({ it.department }, { it.title }))

    override suspend fun getJobPosition(companyId: String, positionId: String): JobPosition? =
        runCatching { col(companyId).document(positionId).get().toPosition() }.getOrNull()

    override suspend fun saveJobPosition(companyId: String, position: JobPosition) {
        val docRef = if (position.id.isEmpty()) col(companyId).document
                     else col(companyId).document(position.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val final = position.copy(
            id = docRef.id,
            updatedAt = now,
            createdAt = if (position.createdAt == 0L) now else position.createdAt
        )
        docRef.set(final.toMap())
    }

    override suspend fun deleteJobPosition(companyId: String, positionId: String) {
        col(companyId).document(positionId).update(mapOf("active" to false))
    }

    private fun JobPosition.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "title" to title,
        "department" to department,
        "responsibilities" to responsibilities.joinToString("\n"),
        "requirements" to requirements.joinToString("\n"),
        "minSalary" to minSalary,
        "maxSalary" to maxSalary,
        "active" to active,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    private fun DocumentSnapshot.toPosition(): JobPosition {
        fun strList(field: String): List<String> {
            val raw = runCatching { get<String?>(field) }.getOrNull() ?: ""
            return if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
        }
        return JobPosition(
            id = id,
            title = get<String?>("title") ?: "",
            department = get<String?>("department") ?: "",
            responsibilities = strList("responsibilities"),
            requirements = strList("requirements"),
            minSalary = runCatching { get<Double?>("minSalary") }.getOrNull() ?: 0.0,
            maxSalary = runCatching { get<Double?>("maxSalary") }.getOrNull() ?: 0.0,
            active = runCatching { get<Boolean?>("active") }.getOrNull() ?: true,
            createdAt = readMillis("createdAt"),
            updatedAt = readMillis("updatedAt")
        )
    }

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
