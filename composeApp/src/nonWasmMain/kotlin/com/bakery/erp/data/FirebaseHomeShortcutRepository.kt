package com.bakery.erp.data

import com.bakery.erp.domain.HomeShortcutRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock

/** `companies/{c}/homeShortcuts/{ownerKey}` — un doc plano por persona. */
class FirebaseHomeShortcutRepository(
    private val firestore: FirebaseFirestore
) : HomeShortcutRepository {

    private fun doc(companyId: String, ownerKey: String) =
        firestore.collection("companies/$companyId/homeShortcuts").document(ownerKey)

    override suspend fun getShortcuts(companyId: String, ownerKey: String): List<String> =
        runCatching {
            val snap = doc(companyId, ownerKey).get()
            if (!snap.exists) emptyList()
            else runCatching { snap.get<List<String>?>("modules") }.getOrNull() ?: emptyList()
        }.getOrDefault(emptyList())

    override suspend fun saveShortcuts(companyId: String, ownerKey: String, modules: List<String>) {
        doc(companyId, ownerKey).set(
            firestoreSafeOf(
                "ownerKey" to ownerKey,
                "modules" to ArrayList(modules.distinct()),
                "updatedAt" to Clock.System.now().toEpochMilliseconds()
            )
        )
    }
}
