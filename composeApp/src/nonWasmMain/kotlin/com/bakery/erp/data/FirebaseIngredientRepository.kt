package com.bakery.erp.data

import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.NewStockLot
import com.bakery.erp.domain.PurchaseLotEntry
import com.bakery.erp.model.BranchStock
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.IngredientUsage
import com.bakery.erp.model.PurchaseFormat
import com.bakery.erp.model.STOCK_LOT_EPSILON
import com.bakery.erp.model.StockLot
import com.bakery.erp.model.StockLotStatus
import com.bakery.erp.model.StockMovement
import com.bakery.erp.model.allocateFefo
import com.bakery.erp.model.stockLotKey
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.Clock

/**
 * Cuántas subcolecciones de formatos se piden a la vez. Suficientemente alto para que el
 * catálogo entero entre en dos o tres tandas, suficientemente bajo para no inundar la red.
 */
private const val FORMAT_FETCH_CHUNK = 25

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

    private fun lotsCol(companyId: String) =
        firestore.collection("companies/$companyId/stockLots")

    private fun movementsCol(companyId: String, ingredientId: String) =
        firestore.collection("companies/$companyId/ingredients/$ingredientId/movements")

    // ── Operaciones principales ──────────────────────────────────────────────

    /**
     * Los formatos viven en una subcolección por insumo, así que esto es 1 + N consultas y no
     * hay forma de bajarlo sin `collectionGroup` —que pediría un índice nuevo y un `companyId`
     * en cada formato—. Lo que SÍ se puede es no hacerlas en fila: en un `map { }` dentro de una
     * función suspend cada `get()` espera a que vuelva el anterior, y con 60 insumos eso son 60
     * viajes de ida y vuelta encadenados. Lanzadas en paralelo el costo pasa a ser el del viaje
     * más lento, no la suma de todos: es la diferencia entre varios segundos y medio.
     *
     * En tandas de [FORMAT_FETCH_CHUNK] y no todas de un golpe, para no abrir cien conexiones
     * simultáneas contra Firestore en un catálogo grande.
     */
    override suspend fun getIngredients(companyId: String): List<Ingredient> = coroutineScope {
        col(companyId).get().documents
            .chunked(FORMAT_FETCH_CHUNK)
            .flatMap { chunk ->
                chunk.map { doc ->
                    async {
                        // Fallback para formatos sin "unit": usar la primera unidad del ingrediente
                        // o baseUnit legacy
                        val fallbackUnit = runCatching { doc.get<List<String>?>("allowedUnits") }.getOrNull()
                            ?.firstOrNull()
                            ?: doc.get<String?>("baseUnit") ?: ""
                        val formats = runCatching {
                            formatCol(companyId, doc.id).get().documents.map { it.toPurchaseFormat(fallbackUnit) }
                        }.getOrElse { emptyList() }
                        doc.toIngredient(formats)
                    }
                }.awaitAll()
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

    override suspend fun addPurchaseFormat(
        companyId: String,
        ingredientId: String,
        format: PurchaseFormat
    ): PurchaseFormat {
        val doc = if (format.id.isEmpty()) formatCol(companyId, ingredientId).document
                  else formatCol(companyId, ingredientId).document(format.id)
        val saved = format.copy(id = doc.id)
        doc.set(saved.toMap())
        return saved
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
        branchId: String, branchName: String, delta: Double,
        newLot: NewStockLot?, skipLots: Boolean
    ): Double {
        val docId = "${branchId}_${ingredientId}"
        val stockDoc = branchStocksCol(companyId).document(docId)
        // Transacción: producción, preparación y venta (el huevo, la leche) bajan el mismo
        // insumo a la vez, y con leer-y-escribir suelto el segundo pisaba al primero.
        val existing = firestore.runTransaction {
            val snap = get(stockDoc)
            val before = if (snap.exists) runCatching { snap.get<Double?>("currentStock") }.getOrNull() ?: 0.0
                         else 0.0
            set(stockDoc, firestoreSafeOf(
                "id" to docId,
                "branchId" to branchId,
                "branchName" to branchName,
                "ingredientId" to ingredientId,
                "ingredientName" to ingredientName,
                "currentStock" to (before + delta).coerceAtLeast(0.0),
                "updatedAt" to Clock.System.now().toEpochMilliseconds()
            ))
            before
        }

        // El libro de lotes va DESPUÉS del stock: si algo falla acá, el inventario ya quedó
        // correcto y solo se pierde precisión en el aviso de caducidad. Queda fuera de la
        // transacción porque necesita una consulta, y las transacciones de la app no consultan.
        if (skipLots) return existing
        runCatching {
            when {
                delta < 0.0 -> consumeLotsFefo(companyId, branchId, ingredientId, -delta)
                newLot != null -> createLot(
                    companyId, ingredientId, ingredientName, branchId, branchName, delta, newLot
                )
                delta > 0.0 -> {
                    // Un ingreso manual sobre un insumo que YA lleva lotes entra como lote sin
                    // fecha; si no llevaba, no se le empieza a llevar por un ajuste suelto.
                    if (getStockLots(companyId, branchId, ingredientId).isNotEmpty()) {
                        createLot(
                            companyId, ingredientId, ingredientName, branchId, branchName,
                            delta, NewStockLot(receivedAt = Clock.System.now().toEpochMilliseconds())
                        )
                    }
                }
            }
        }
        return existing
    }

    // ── Lotes de insumos perecederos (FEFO) ────────────────────────────

    override suspend fun getStockLots(
        companyId: String, branchId: String, ingredientId: String
    ): List<StockLot> =
        runCatching {
            lotsCol(companyId)
                .where { "branchIngredientKey" equalTo stockLotKey(branchId, ingredientId) }
                .get().documents.map { it.toStockLot() }
        }.getOrElse { emptyList() }

    override suspend fun getActiveStockLots(
        companyId: String, branchIds: List<String>
    ): List<StockLot> =
        runCatching {
            lotsCol(companyId)
                .where { "status" equalTo StockLotStatus.ACTIVE }
                .get().documents.map { it.toStockLot() }
                .filter { branchIds.isEmpty() || it.branchId in branchIds }
        }.getOrElse { emptyList() }

    override suspend fun reconcilePurchaseLots(
        companyId: String,
        purchaseId: String,
        branchId: String,
        branchName: String,
        lines: List<PurchaseLotEntry>
    ) {
        val existing = runCatching {
            lotsCol(companyId).where { "purchaseId" equalTo purchaseId }
                .get().documents.map { it.toStockLot() }
        }.getOrElse { return }
        val byIngredient = existing.associateBy { it.ingredientId }

        lines.forEach { entry ->
            val lot = byIngredient[entry.ingredientId]
            if (lot == null) {
                createLot(
                    companyId, entry.ingredientId, entry.ingredientName, branchId, branchName,
                    entry.quantity,
                    NewStockLot(entry.expiryDate, entry.lotCode, entry.unitCost, purchaseId, entry.receivedAt)
                )
            } else {
                // El saldo se mueve por DIFERENCIA contra la cantidad original: lo que ya se
                // consumió de este lote sigue consumido.
                val diff = entry.quantity - lot.initialQuantity
                val remaining = (lot.remainingQuantity + diff).coerceAtLeast(0.0)
                lotsCol(companyId).document(lot.id).update(
                    firestoreSafeOf(
                        "initialQuantity" to entry.quantity,
                        "remainingQuantity" to remaining,
                        "expiryDate" to entry.expiryDate,
                        "lotCode" to entry.lotCode,
                        "unitCost" to entry.unitCost,
                        "status" to if (remaining <= STOCK_LOT_EPSILON) StockLotStatus.DEPLETED
                                    else StockLotStatus.ACTIVE
                    )
                )
            }
        }

        val keep = lines.mapTo(HashSet()) { it.ingredientId }
        existing.filter { it.ingredientId !in keep }.forEach { lot ->
            lotsCol(companyId).document(lot.id).update(
                firestoreSafeOf(
                    "initialQuantity" to 0.0,
                    "remainingQuantity" to 0.0,
                    "status" to StockLotStatus.DEPLETED
                )
            )
        }
    }

    private suspend fun createLot(
        companyId: String, ingredientId: String, ingredientName: String,
        branchId: String, branchName: String, quantity: Double, info: NewStockLot
    ) {
        if (quantity <= 0.0) return
        val doc = lotsCol(companyId).document
        val lot = StockLot(
            id = doc.id,
            ingredientId = ingredientId,
            ingredientName = ingredientName,
            branchId = branchId,
            branchName = branchName,
            branchIngredientKey = stockLotKey(branchId, ingredientId),
            expiryDate = info.expiryDate,
            lotCode = info.lotCode,
            receivedAt = if (info.receivedAt > 0L) info.receivedAt
                         else Clock.System.now().toEpochMilliseconds(),
            initialQuantity = quantity,
            remainingQuantity = quantity,
            unitCost = info.unitCost,
            purchaseId = info.purchaseId
        )
        doc.set(lot.toLotMap())
    }

    /**
     * Descuenta [quantity] repartiendola con [allocateFefo]. Si no hay lotes no hace nada:
     * los insumos que no son perecederos nunca los llevan, y su stock ya quedó bien arriba.
     */
    private suspend fun consumeLotsFefo(
        companyId: String, branchId: String, ingredientId: String, quantity: Double
    ) {
        val lots = getStockLots(companyId, branchId, ingredientId)
        if (lots.isEmpty()) return
        val byId = lots.associateBy { it.id }
        allocateFefo(lots, quantity).allocations.forEach { alloc ->
            val lot = byId[alloc.lotId] ?: return@forEach
            val remaining = (lot.remainingQuantity - alloc.quantity).coerceAtLeast(0.0)
            lotsCol(companyId).document(alloc.lotId).update(
                firestoreSafeOf(
                    "remainingQuantity" to remaining,
                    "status" to if (remaining <= STOCK_LOT_EPSILON) StockLotStatus.DEPLETED
                                else StockLotStatus.ACTIVE
                )
            )
        }
    }

    private fun StockLot.toLotMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "ingredientId" to ingredientId,
        "ingredientName" to ingredientName,
        "branchId" to branchId,
        "branchName" to branchName,
        "branchIngredientKey" to branchIngredientKey,
        "expiryDate" to expiryDate,
        "lotCode" to lotCode,
        "receivedAt" to receivedAt,
        "initialQuantity" to initialQuantity,
        "remainingQuantity" to remainingQuantity,
        "unitCost" to unitCost,
        "purchaseId" to purchaseId,
        "status" to status
    )

    private fun DocumentSnapshot.toStockLot() = StockLot(
        id = id,
        ingredientId = get<String?>("ingredientId") ?: "",
        ingredientName = get<String?>("ingredientName") ?: "",
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        branchIngredientKey = get<String?>("branchIngredientKey") ?: "",
        expiryDate = readLotMillis("expiryDate"),
        lotCode = get<String?>("lotCode") ?: "",
        receivedAt = readLotMillis("receivedAt"),
        initialQuantity = runCatching { get<Double?>("initialQuantity") }.getOrNull() ?: 0.0,
        remainingQuantity = runCatching { get<Double?>("remainingQuantity") }.getOrNull() ?: 0.0,
        unitCost = runCatching { get<Double?>("unitCost") }.getOrNull() ?: 0.0,
        purchaseId = get<String?>("purchaseId") ?: "",
        status = get<String?>("status") ?: StockLotStatus.ACTIVE
    )

    private fun DocumentSnapshot.readLotMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L

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
            "isDefault" to (runCatching { snap.get<Boolean?>("isDefault") }.getOrNull() ?: false),
            // Se arrastra: este `set()` reescribe el documento, y sin esto registrar un precio
            // nuevo borraría el nombre de factura que alguien cargó.
            "supplierItemName" to (runCatching { snap.get<String?>("supplierItemName") }.getOrNull() ?: "")
        ))
    }

    // ── Serialización ────────────────────────────────────────────────────────

    private fun Ingredient.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "name" to name,
        "category" to category,
        "usage" to usage.name,
        "barcode" to barcode,
        "allowedUnits" to allowedUnits,
        "unitWeightGrams" to unitWeightGrams,
        "stockAlert" to stockAlert,
        "isPerishable" to isPerishable,
        "tracksStock" to tracksStock,
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
        "isDefault" to isDefault,
        "supplierItemName" to supplierItemName
    )

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toIngredient(formats: List<PurchaseFormat>) = Ingredient(
        id = id,
        name = get<String?>("name") ?: "",
        category = get<String?>("category") ?: "",
        // Sin el campo, el insumo es materia prima: era lo único que existía antes.
        usage = IngredientUsage.from(runCatching { get<String?>("usage") }.getOrNull()),
        barcode = runCatching { get<String?>("barcode") }.getOrNull() ?: "",
        // Retrocompatibilidad: docs viejos tienen "baseUnit" en lugar de "allowedUnits"
        allowedUnits = runCatching { get<List<String>?>("allowedUnits") }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(get<String?>("baseUnit")?.takeIf { it.isNotEmpty() }),
        unitWeightGrams = runCatching { get<Double?>("unitWeightGrams") }.getOrNull() ?: 0.0,
        purchaseFormats = formats,
        stockAlert = get<Double?>("stockAlert"),
        isPerishable = runCatching { get<Boolean?>("isPerishable") }.getOrNull() ?: false,
        // Ausente en los insumos ya cargados: todos llevaban conteo.
        tracksStock = runCatching { get<Boolean?>("tracksStock") }.getOrNull() ?: true,
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
        isDefault = get<Boolean?>("isDefault") ?: false,
        supplierItemName = runCatching { get<String?>("supplierItemName") }.getOrNull() ?: ""
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
        "notes" to notes,
        "createdByUserId" to createdByUserId,
        "createdByUserName" to createdByUserName,
        "createdByEmployeeId" to createdByEmployeeId
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
        notes = get<String?>("notes"),
        createdByUserId = runCatching { get<String?>("createdByUserId") }.getOrNull() ?: "",
        createdByUserName = runCatching { get<String?>("createdByUserName") }.getOrNull() ?: "",
        createdByEmployeeId = runCatching { get<String?>("createdByEmployeeId") }.getOrNull() ?: ""
    )
}
