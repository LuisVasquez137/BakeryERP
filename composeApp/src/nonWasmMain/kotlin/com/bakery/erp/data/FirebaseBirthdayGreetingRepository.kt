package com.bakery.erp.data

import com.bakery.erp.domain.BirthdayGreetingRepository
import com.bakery.erp.model.BirthdayGreeting
import com.bakery.erp.model.BirthdayGreetingStatus
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.where

class FirebaseBirthdayGreetingRepository(
    private val firestore: FirebaseFirestore
) : BirthdayGreetingRepository {

    private fun col(companyId: String) = firestore.collection("companies/$companyId/birthdayGreetings")

    // Una sola condición en el servidor (sin índice compuesto); el año se filtra en memoria.
    override suspend fun getSentBy(companyId: String, senderEmployeeId: String, year: Int): List<BirthdayGreeting> =
        col(companyId).where { "senderEmployeeId" equalTo senderEmployeeId }.get().documents
            .map { it.toGreeting() }.filter { it.year == year }

    override suspend fun getReceivedBy(companyId: String, birthdayEmployeeId: String, year: Int): List<BirthdayGreeting> =
        col(companyId).where { "birthdayEmployeeId" equalTo birthdayEmployeeId }.get().documents
            .map { it.toGreeting() }.filter { it.year == year }

    override suspend fun save(companyId: String, greeting: BirthdayGreeting) {
        col(companyId).document(greeting.id).set(firestoreSafeOf(
            "id" to greeting.id,
            "year" to greeting.year,
            "birthdayEmployeeId" to greeting.birthdayEmployeeId,
            "birthdayName" to greeting.birthdayName,
            "senderEmployeeId" to greeting.senderEmployeeId,
            "senderName" to greeting.senderName,
            "senderPhotoUrl" to greeting.senderPhotoUrl,
            "status" to greeting.status,
            "createdAt" to greeting.createdAt,
            "seenAt" to greeting.seenAt
        ))
    }

    override suspend fun markSeen(companyId: String, ids: List<String>, at: Long) {
        ids.forEach { id -> col(companyId).document(id).update(firestoreSafeOf("seenAt" to at)) }
    }

    private fun DocumentSnapshot.readLong(field: String): Long =
        runCatching { get<Long?>(field) }.getOrNull()
            ?: runCatching { get<Double?>(field)?.toLong() }.getOrNull() ?: 0L

    private fun DocumentSnapshot.toGreeting() = BirthdayGreeting(
        id = id,
        year = readLong("year").toInt(),
        birthdayEmployeeId = get<String?>("birthdayEmployeeId") ?: "",
        birthdayName = get<String?>("birthdayName") ?: "",
        senderEmployeeId = get<String?>("senderEmployeeId") ?: "",
        senderName = get<String?>("senderName") ?: "",
        senderPhotoUrl = get<String?>("senderPhotoUrl") ?: "",
        status = get<String?>("status") ?: BirthdayGreetingStatus.SENT,
        createdAt = readLong("createdAt"),
        seenAt = readLong("seenAt")
    )
}
