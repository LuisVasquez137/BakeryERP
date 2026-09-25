package com.bakery.erp.data

import com.bakery.erp.domain.PurchaseConceptRepository
import com.bakery.erp.model.PurchaseConcept
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock

/**
 * Catálogo de conceptos sin inventario. Colección plana `companies/{c}/purchaseConcepts`.
 *
 * Documento plano y no subcolección de proveedor porque un mismo concepto se le compra a
 * varios ("bolsas" las trae el de plásticos y a veces la ferretería), y colgarlo del proveedor
 * obligaría a duplicarlo — que es exactamente el nombre repetido que el catálogo evita.
 */
class FirebasePurchaseConceptRepository(
    private val firestore: FirebaseFirestore
) : PurchaseConceptRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/purchaseConcepts")

    override suspend fun getConcepts(companyId: String): List<PurchaseConcept> =
        col(companyId).get().documents.map { it.toConcept() }

    override suspend fun saveConcept(companyId: String, concept: PurchaseConcept): PurchaseConcept {
        val docRef = if (concept.id.isEmpty()) col(companyId).document
        else col(companyId).document(concept.id)
        val saved = concept.copy(id = docRef.id)
        docRef.set(saved.toMap())
        return saved
    }

    override suspend fun recordPurchase(
        companyId: String,
        conceptId: String,
        supplierIds: List<String>,
        lastUnitCost: Double,
        lastPurchaseDate: Long
    ) {
        if (conceptId.isEmpty()) return
        // `update` y no `set`: escribe solo los cuatro campos que la compra conoce. Ver el
        // porqué en `PurchaseConceptRepository.recordPurchase`.
        col(companyId).document(conceptId).update(
            firestoreSafeOf(
                "supplierIds" to supplierIds,
                "lastUnitCost" to lastUnitCost,
                "lastPurchaseDate" to lastPurchaseDate,
                "updatedAt" to Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    override suspend fun deleteConcept(companyId: String, conceptId: String) {
        col(companyId).document(conceptId).delete()
    }

    // ── Serialization ────────────────────────────────────────────────────────

    private fun PurchaseConcept.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "name" to name,
        "unit" to unit,
        "expenseCategory" to expenseCategory,
        "supplierIds" to supplierIds,
        "lastUnitCost" to lastUnitCost,
        "lastPurchaseDate" to lastPurchaseDate,
        "active" to active,
        "createdAt" to if (createdAt == 0L) Clock.System.now().toEpochMilliseconds() else createdAt,
        "updatedAt" to Clock.System.now().toEpochMilliseconds()
    )

    private fun DocumentSnapshot.toConcept() = PurchaseConcept(
        id = id,
        name = get<String?>("name") ?: "",
        unit = get<String?>("unit") ?: "unidad",
        expenseCategory = get<String?>("expenseCategory") ?: "",
        supplierIds = runCatching { get<List<String>?>("supplierIds") }.getOrNull().orEmpty(),
        lastUnitCost = runCatching { get<Double?>("lastUnitCost") }.getOrNull() ?: 0.0,
        lastPurchaseDate = readTimestampAsMillis("lastPurchaseDate"),
        active = get<Boolean?>("active") ?: true,
        createdAt = readTimestampAsMillis("createdAt"),
        updatedAt = readTimestampAsMillis("updatedAt")
    )

    private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
