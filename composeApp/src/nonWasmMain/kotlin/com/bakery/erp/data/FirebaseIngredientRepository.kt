package com.bakery.erp.data

import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.model.BranchStock
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.PurchaseFormat
import com.bakery.erp.model.StockMovement
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock

/**
 * Los formatos de compra se guardan en una subcollección separada para evitar
 * el problema de serialización de listas de mapas anidados con el SDK de GitLive.
 *
 * Estructura Firestore:
 *   companies/{orgId}/ingredients/{ingredientId}          ← datos del ingrediente
 *   companies/{orgId}/ingredients/{ingredientId}/purchaseFormats/{formatId}  ← formatos
 */
class FirebaseIngredientRepository(
    private val firestore: FirebaseFirestore
) : IngredientRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/ingredients")

    private fun formatCol(companyId: String, ingredientId: String) =
        firestore.collection("companies/$companyId/ingredients/$ingredientId/purchaseFormats")

    private fun branchStocksCol(companyId: String) =
        firestore.collection("companies/$companyId/branchStocks")

    private fun movementsCol(companyId: String, ingredientId: String) =
        firestore.collection("companies/$companyId/ingredients/$ingredientId/movements")

    // ── Operaciones principales ──────────────────────────────────────────────

    override suspend fun getIngredients(companyId: String): List<Ingredient> {
        return col(companyId).get().documents.map { doc ->
            // Fallback para formatos sin "unit": usar la primera unidad del ingrediente o baseUnit legacy
            @Suppress("UNCHECKED_CAST")
            val fallbackUnit = runCatching { doc.get<List<String>?>("allowedUnits") }.getOrNull()
                ?.firstOrNull()
                ?: doc.get<String?>("baseUnit") ?: ""
            val formats = runCatching {
                formatCol(companyId, doc.id).get().documents.map { it.toPurchaseFormat(fallbackUnit) }
            }.getOrElse { emptyList() }
            doc.toIngredient(formats)
        }
    }

    override suspend fun addIngredient(companyId: String, ingredient: Ingredient) {
        val docRef = if (ingredient.id.isEmpty()) col(companyId).document
        else col(companyId).document(ingredient.id)
        val ingredientId = docRef.id
        docRef.set(ingredient.copy(id = ingredientId).toMap())
        ingredient.purchaseFormats.forEach { fmt ->
            formatCol(companyId, ingredientId).document(fmt.id).set(fmt.toMap())
        }
    }

    override suspend fun updateIngredient(companyId: String, ingredient: Ingredient) {
        col(companyId).document(ingredient.id).set(ingredient.toMap())
        // Borrar todos los formatos existentes y volver a guardar
        val existing = runCatching {
            formatCol(companyId, ingredient.id).get().documents
        }.getOrElse { emptyList() }
        existing.forEach { doc ->
            formatCol(companyId, ingredient.id).document(doc.id).delete()
        }
        ingredient.purchaseFormats.forEach { fmt ->
            formatCol(companyId, ingredient.id).document(fmt.id).set(fmt.toMap())
        }
    }

    override suspend fun deleteIngredient(companyId: String, ingredientId: String) {
        // Borrar formatos primero (Firestore no elimina subcollecciones automáticamente)
        val formats = runCatching {
            formatCol(companyId, ingredientId).get().documents
        }.getOrElse { emptyList() }
        formats.forEach { doc ->
            formatCol(companyId, ingredientId).document(doc.id).delete()
        }
        col(companyId).document(ingredientId).delete()
    }

    // ── Stock por sucursal ───────────────────────────────────────────────────

    override suspend fun getBranchStocksForBranch(companyId: String, branchId: String): Map<String, Double> =
        branchStocksCol(companyId).get().documents
            .filter { it.get<String?>("branchId") == branchId }
            .associate { doc ->
                (doc.get<String?>("ingredientId") ?: "") to (runCatching { doc.get<Double?>("currentStock") }.getOrNull() ?: 0.0)
            }

    override suspend fun getBranchStocksForIngredient(
        companyId: String, ingredientId: String, branchIds: List<String>
    ): List<BranchStock> {
        val all = branchStocksCol(companyId).get().documents
            .filter { it.get<String?>("ingredientId") == ingredientId }
            .map { it.toBranchStock() }
        return if (branchIds.isEmpty()) all else all.filter { it.branchId in branchIds }
    }

    override suspend fun updateBranchStock(
        companyId: String, ingredientId: String, ingredientName: String,
        branchId: String, branchName: String, delta: Double
    ) {
        val docId = "${branchId}_${ingredientId}"
        val stockDoc = branchStocksCol(companyId).document(docId)
        val existing = runCatching { stockDoc.get().get<Double?>("currentStock") }.getOrNull() ?: 0.0
        val newStock = (existing + delta).coerceAtLeast(0.0)
        stockDoc.set(firestoreSafeOf(
            "id" to docId,
            "branchId" to branchId,
            "branchName" to branchName,
            "ingredientId" to ingredientId,
            "ingredientName" to ingredientName,
            "currentStock" to newStock,
            "updatedAt" to Clock.System.now().toEpochMilliseconds()
        ))
    }

    // ── Kardex ───────────────────────────────────────────────────────────────

    override suspend fun addStockMovement(companyId: String, ingredientId: String, movement: StockMovement) {
        val col = movementsCol(companyId, ingredientId)
        val doc = col.document
        doc.set(movement.copy(id = doc.id).toMovementMap())
    }

    override suspend fun getStockMovements(companyId: String, ingredientId: String): List<StockMovement> =
        movementsCol(companyId, ingredientId).get().documents
            .map { it.toStockMovement() }
            .sortedByDescending { it.date }

    override suspend fun updatePurchaseFormatCost(
        companyId: String, ingredientId: String, formatId: String, newPackageCost: Double
    ) {
        val docRef = formatCol(companyId, ingredientId).document(formatId)
        val snap = runCatching { docRef.get() }.getOrNull() ?: return
        docRef.set(mapOf(
            "id" to formatId,
            "description" to (runCatching { snap.get<String?>("description") }.getOrNull() ?: ""),
            "unit" to (runCatching { snap.get<String?>("unit") }.getOrNull() ?: ""),
            "supplierId" to (runCatching { snap.get<String?>("supplierId") }.getOrNull() ?: ""),
            "supplierName" to (runCatching { snap.get<String?>("supplierName") }.getOrNull() ?: ""),
            "unitsPerPackage" to (runCatching { snap.get<Double?>("unitsPerPackage") }.getOrNull() ?: 1.0),
            "packageCost" to newPackageCost,
            "isDefault" to (runCatching { snap.get<Boolean?>("isDefault") }.getOrNull() ?: false)
        ))
    }

    // ── Serialización ────────────────────────────────────────────────────────

    private fun Ingredient.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "name" to name,
        "category" to category,
        "allowedUnits" to allowedUnits,
        "stockAlert" to stockAlert,
        "notes" to notes,
        "active" to active,
        "createdAt" to if (createdAt == 0L) Clock.System.now().toEpochMilliseconds() else createdAt,
        "updatedAt" to Clock.System.now().toEpochMilliseconds()
        // purchaseFormats NO se guarda aquí — usa subcollección
    )

    private fun PurchaseFormat.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "description" to description,
        "unit" to unit,
        "supplierId" to supplierId,
        "supplierName" to supplierName,
        "unitsPerPackage" to unitsPerPackage,
        "packageCost" to packageCost,
        "isDefault" to isDefault
    )

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toIngredient(formats: List<PurchaseFormat>) = Ingredient(
        id = id,
        name = get<String?>("name") ?: "",
        category = get<String?>("category") ?: "",
        // Retrocompatibilidad: docs viejos tienen "baseUnit" en lugar de "allowedUnits"
        allowedUnits = runCatching { get<List<String>?>("allowedUnits") }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(get<String?>("baseUnit")?.takeIf { it.isNotEmpty() }),
        purchaseFormats = formats,
        stockAlert = get<Double?>("stockAlert"),
        notes = get<String?>("notes"),
        active = get<Boolean?>("active") ?: true,
        createdAt = readTimestampAsMillis("createdAt"),
        updatedAt = readTimestampAsMillis("updatedAt")
    )

    private fun DocumentSnapshot.toPurchaseFormat(fallbackUnit: String = "") = PurchaseFormat(
        id = id,
        description = get<String?>("description") ?: "",
        // Retrocompatibilidad: docs viejos no tienen "unit"
        unit = get<String?>("unit")?.takeIf { it.isNotEmpty() } ?: fallbackUnit,
        supplierId = get<String?>("supplierId") ?: "",
        supplierName = get<String?>("supplierName") ?: "",
        unitsPerPackage = get<Double?>("unitsPerPackage") ?: 1.0,
        packageCost = get<Double?>("packageCost") ?: 0.0,
        isDefault = get<Boolean?>("isDefault") ?: false
    )

    private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L

    private fun DocumentSnapshot.toBranchStock() = BranchStock(
        id = id,
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        ingredientId = get<String?>("ingredientId") ?: "",
        ingredientName = get<String?>("ingredientName") ?: "",
        currentStock = runCatching { get<Double?>("currentStock") }.getOrNull() ?: 0.0,
        updatedAt = readTimestampAsMillis("updatedAt")
    )

    private fun StockMovement.toMovementMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "type" to type,
        "quantity" to quantity,
        "balanceBefore" to balanceBefore,
        "balanceAfter" to balanceAfter,
        "branchId" to branchId,
        "branchName" to branchName,
        "reference" to reference,
        "date" to date,
        "notes" to notes
    )

    private fun DocumentSnapshot.toStockMovement() = StockMovement(
        id = id,
        type = get<String?>("type") ?: "COMPRA",
        quantity = runCatching { get<Double?>("quantity") }.getOrNull() ?: 0.0,
        balanceBefore = runCatching { get<Double?>("balanceBefore") }.getOrNull() ?: 0.0,
        balanceAfter = runCatching { get<Double?>("balanceAfter") }.getOrNull() ?: 0.0,
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        reference = get<String?>("reference") ?: "",
        date = readTimestampAsMillis("date"),
        notes = get<String?>("notes")
    )
}
