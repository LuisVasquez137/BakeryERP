package com.bakery.erp.data

import com.bakery.erp.domain.PreparationRepository
import com.bakery.erp.model.Preparation
import com.bakery.erp.model.PreparationIngredient
import com.bakery.erp.model.PreparationUsage
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock

/**
 * Estructura Firestore:
 *   companies/{orgId}/preparations/{preparationId}                       ← datos (flat)
 *   companies/{orgId}/preparations/{preparationId}/ingredients/{id}      ← insumos del lote
 */
class FirebasePreparationRepository(
    private val firestore: FirebaseFirestore
) : PreparationRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/preparations")

    private fun ingredientsCol(companyId: String, preparationId: String) =
        firestore.collection("companies/$companyId/preparations/$preparationId/ingredients")

    // ── Lectura ──────────────────────────────────────────────────────────────

    override suspend fun getPreparations(companyId: String): List<Preparation> =
        col(companyId).get().documents.map { it.toPreparation() }

    override suspend fun getPreparation(companyId: String, preparationId: String): Preparation {
        val doc = col(companyId).document(preparationId).get()
        val ingredients = runCatching {
            ingredientsCol(companyId, preparationId).get().documents.map { it.toPreparationIngredient() }
        }.getOrElse { emptyList() }
        return doc.toPreparation().copy(ingredients = ingredients)
    }

    // ── Escritura ────────────────────────────────────────────────────────────

    override suspend fun addPreparation(companyId: String, preparation: Preparation) {
        val docRef = if (preparation.id.isEmpty()) col(companyId).document
        else col(companyId).document(preparation.id)
        val final = preparation.copy(id = docRef.id)
        docRef.set(final.toMap())
        writeIngredients(companyId, docRef.id, final)
    }

    override suspend fun updatePreparation(companyId: String, preparation: Preparation) {
        col(companyId).document(preparation.id).set(preparation.toMap())
        deleteIngredients(companyId, preparation.id)
        writeIngredients(companyId, preparation.id, preparation)
    }

    override suspend fun deletePreparation(companyId: String, preparationId: String) {
        deleteIngredients(companyId, preparationId)
        col(companyId).document(preparationId).delete()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun writeIngredients(companyId: String, preparationId: String, preparation: Preparation) {
        preparation.ingredients.forEach { ing ->
            ingredientsCol(companyId, preparationId).document(ing.id).set(ing.toMap())
        }
    }

    private suspend fun deleteIngredients(companyId: String, preparationId: String) {
        runCatching {
            ingredientsCol(companyId, preparationId).get().documents.forEach { doc ->
                ingredientsCol(companyId, preparationId).document(doc.id).delete()
            }
        }
    }

    // ── Serialización → Firestore ────────────────────────────────────────────

    private fun Preparation.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "usage" to usage,
        "ingredientCount" to ingredients.size,
        "flourFractionPct" to flourFractionPct,
        "notes" to notes,
        "active" to active,
        "batterPreparationId" to batterPreparationId,
        "batterPreparationName" to batterPreparationName,
        "batterGramsPerUnit" to batterGramsPerUnit,
        "stockMinimumUnits" to stockMinimumUnits,
        "branchIds" to branchIds,
        "createdAt" to if (createdAt == 0L) Clock.System.now().toEpochMilliseconds() else createdAt,
        "updatedAt" to Clock.System.now().toEpochMilliseconds()
        // ingredients NO se guardan en el documento raíz (subcolección)
    )

    private fun PreparationIngredient.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "ingredientId" to ingredientId,
        "ingredientName" to ingredientName,
        "quantityPerBatch" to quantityPerBatch,
        "unit" to unit,
        "unitWeightGrams" to unitWeightGrams
    )

    // ── Deserialización ← Firestore ──────────────────────────────────────────

    private fun DocumentSnapshot.toPreparation() = Preparation(
        id = id,
        name = get<String?>("name") ?: "",
        description = get<String?>("description"),
        usage = get<String?>("usage") ?: PreparationUsage.DOUGH,
        ingredientCount = get<Long?>("ingredientCount")?.toInt() ?: 0,
        flourFractionPct = get<Double?>("flourFractionPct") ?: 0.0,
        notes = get<String?>("notes"),
        active = get<Boolean?>("active") ?: true,
        batterPreparationId = runCatching { get<String?>("batterPreparationId") }.getOrNull() ?: "",
        batterPreparationName = runCatching { get<String?>("batterPreparationName") }.getOrNull() ?: "",
        batterGramsPerUnit = readNumber("batterGramsPerUnit"),
        stockMinimumUnits = readNumber("stockMinimumUnits"),
        branchIds = runCatching { get<List<String>?>("branchIds") }.getOrNull() ?: emptyList(),
        createdAt = readTimestampAsMillis("createdAt"),
        updatedAt = readTimestampAsMillis("updatedAt")
    )

    private fun DocumentSnapshot.toPreparationIngredient() = PreparationIngredient(
        id = id,
        ingredientId = get<String?>("ingredientId") ?: "",
        ingredientName = get<String?>("ingredientName") ?: "",
        quantityPerBatch = get<Double?>("quantityPerBatch") ?: 0.0,
        unit = get<String?>("unit") ?: "",
        unitWeightGrams = runCatching { get<Double?>("unitWeightGrams") }.getOrNull() ?: 0.0
    )

    private fun DocumentSnapshot.readNumber(field: String): Double =
        runCatching { get<Double?>(field) }.getOrNull()
            ?: runCatching { get<Long?>(field)?.toDouble() }.getOrNull()
            ?: 0.0

    private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
