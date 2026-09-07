package com.bakery.erp.data

import com.bakery.erp.domain.NotificationRepository
import com.bakery.erp.model.AppNotification
import com.bakery.erp.model.NotificationTarget
import com.bakery.erp.model.NotificationType
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebaseNotificationRepository(
    private val firestore: FirebaseFirestore
) : NotificationRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/notifications")

    private fun tokensCol(companyId: String) =
        firestore.collection("companies/$companyId/deviceTokens")

    override fun observeNotifications(companyId: String, limit: Int): Flow<List<AppNotification>> =
        col(companyId).snapshots.map { snapshot ->
            snapshot.documents
                .map { it.toNotification() }
                .sortedByDescending { it.createdAt }
                .take(limit)
        }

    override suspend fun push(companyId: String, notification: AppNotification) {
        val docRef = if (notification.id.isEmpty()) col(companyId).document
                     else col(companyId).document(notification.id)
        val now = Clock.System.now().toEpochMilliseconds()
        docRef.set(
            notification.copy(
                id = docRef.id,
                createdAt = if (notification.createdAt == 0L) now else notification.createdAt
            ).toMap()
        )
    }

    override suspend fun deleteForTargets(companyId: String, targetIds: List<String>) {
        targetIds.filter { it.isNotEmpty() }.distinct().forEach { targetId ->
            runCatching {
                col(companyId).where { "targetId" equalTo targetId }.get().documents.forEach { doc ->
                    col(companyId).document(doc.id).delete()
                }
            }
        }
    }

    override suspend fun markAsRead(companyId: String, notificationId: String, userId: String) {
        val doc = col(companyId).document(notificationId).get()
        if (!doc.exists) return
        val current = doc.toNotification()
        if (userId in current.readByUserIds) return
        col(companyId).document(notificationId).update(
            firestoreSafeOf("readByUserIds" to (current.readByUserIds + userId))
        )
    }

    override suspend fun markAllAsRead(
        companyId: String,
        notificationIds: List<String>,
        userId: String
    ) {
        notificationIds.forEach { id ->
            runCatching { markAsRead(companyId, id, userId) }
        }
    }

    // ── Tokens de dispositivo ────────────────────────────────────────────────

    override suspend fun registerDeviceToken(
        companyId: String,
        userId: String,
        token: String,
        platform: String
    ) {
        if (token.isEmpty() || userId.isEmpty()) return
        // El id del doc es el propio token: re-registrar el mismo dispositivo
        // sobreescribe en vez de duplicar.
        tokensCol(companyId).document(token).set(
            firestoreSafeOf(
                "token" to token,
                "userId" to userId,
                "platform" to platform,
                "updatedAt" to Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    override suspend fun unregisterDeviceToken(companyId: String, token: String) {
        if (token.isEmpty()) return
        runCatching { tokensCol(companyId).document(token).delete() }
    }

    // ── Serialización ────────────────────────────────────────────────────────

    private fun AppNotification.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "type" to type,
        "title" to title,
        "body" to body,
        "targetBranchIds" to targetBranchIds,
        "requiredPermission" to requiredPermission,
        "targetScreen" to targetScreen,
        "targetId" to targetId,
        "createdByUserId" to createdByUserId,
        "createdByUserName" to createdByUserName,
        "readByUserIds" to readByUserIds,
        "createdAt" to createdAt
    )

    private fun DocumentSnapshot.toNotification() = AppNotification(
        id = id,
        type = get<String?>("type") ?: NotificationType.ORDER_RECEIVED,
        title = get<String?>("title") ?: "",
        body = get<String?>("body") ?: "",
        targetBranchIds = runCatching {
            get<List<String>?>("targetBranchIds")
        }.getOrNull() ?: emptyList(),
        requiredPermission = get<String?>("requiredPermission") ?: "",
        targetScreen = get<String?>("targetScreen") ?: NotificationTarget.NONE,
        targetId = get<String?>("targetId") ?: "",
        createdByUserId = get<String?>("createdByUserId") ?: "",
        createdByUserName = get<String?>("createdByUserName") ?: "",
        readByUserIds = runCatching {
            get<List<String>?>("readByUserIds")
        }.getOrNull() ?: emptyList(),
        createdAt = readMillis("createdAt")
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
