package com.bakery.erp.data

import com.bakery.erp.domain.PurchaseRepository
import com.bakery.erp.model.PURCHASE_BALANCE_EPSILON
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseKind
import com.bakery.erp.model.PurchaseLineKind
import com.bakery.erp.model.PurchasePayment
import com.bakery.erp.model.PurchasePaymentMethod
import com.bakery.erp.model.PurchaseStatus
import com.bakery.erp.model.itemId
import com.bakery.erp.model.itemName
import com.bakery.erp.model.nearestExpiry
import com.bakery.erp.model.nearestExpiryLine
import com.bakery.erp.model.kindsPresent
import com.bakery.erp.model.purchaseTotal
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock

class FirebasePurchaseRepository(
    private val firestore: FirebaseFirestore
) : PurchaseRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/purchases")

    private fun linesCol(companyId: String, purchaseId: String) =
        firestore.collection("companies/$companyId/purchases/$purchaseId/lines")

    private fun paymentsCol(companyId: String, purchaseId: String) =
        firestore.collection("companies/$companyId/purchases/$purchaseId/payments")

    /**
     * Trae la colección completa y el filtro por período se aplica en memoria.
     *
     * Se evitó a propósito una consulta con desigualdad sobre `date`: las compras viejas
     * pueden tener ese campo como `Timestamp` y las nuevas lo escriben como número
     * (`firestoreSafeOf` convierte Long a Double), y Firestore ordena por tipo antes que por
     * valor — un `where date >= x` numérico dejaría fuera, en silencio, justo las facturas
     * más antiguas, que son las que uno busca cuando pregunta "¿cuándo compré aceite?".
     * Cuando el volumen lo pida, la salida es normalizar el campo con un script y recién ahí
     * mover el filtro al servidor.
     */
    override suspend fun getPurchases(companyId: String): List<Purchase> =
        col(companyId).get().documents.map { it.toPurchase() }.sortedByDescending { it.date }

    override suspend fun getPurchase(companyId: String, purchaseId: String): Purchase? =
        runCatching { col(companyId).document(purchaseId).get().toPurchase() }.getOrNull()

    override suspend fun savePurchase(companyId: String, purchase: Purchase, lines: List<PurchaseLine>) {
        val docRef = if (purchase.id.isEmpty()) col(companyId).document
                     else col(companyId).document(purchase.id)
        val finalPurchase = purchase.copy(
            id = docRef.id,
            lineCount = lines.size,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            // Denormalizados desde las líneas: el monto alimenta los KPIs de la lista y los
            // nombres permiten buscar por contenido sin abrir la subcolección de cada factura.
            totalAmount = lines.purchaseTotal(),
            itemIds = lines.map { it.itemId }.filter { it.isNotEmpty() }.distinct(),
            itemNames = lines.map { it.itemName }.filter { it.isNotEmpty() }.distinct(),
            lineKinds = lines.kindsPresent(),
            nearestExpiryDate = lines.nearestExpiry(),
            nearestExpiryItemName = lines.nearestExpiryLine()?.itemName.orEmpty()
        )
        docRef.set(finalPurchase.toPurchaseMap())
        lines.forEach { line ->
            val lineDoc = linesCol(companyId, docRef.id).document
            lineDoc.set(line.copy(id = lineDoc.id).toLineMap())
        }
    }

    override suspend fun updatePurchase(companyId: String, purchase: Purchase, lines: List<PurchaseLine>) {
        val docRef = col(companyId).document(purchase.id)
        docRef.set(
            purchase.copy(
                lineCount = lines.size,
                totalAmount = lines.purchaseTotal(),
                itemIds = lines.map { it.itemId }.filter { it.isNotEmpty() }.distinct(),
                itemNames = lines.map { it.itemName }.filter { it.isNotEmpty() }.distinct(),
                lineKinds = lines.kindsPresent(),
                nearestExpiryDate = lines.nearestExpiry(),
                nearestExpiryItemName = lines.nearestExpiryLine()?.itemName.orEmpty()
            ).toPurchaseMap()
        )
        // Se borran y se reescriben: una línea quitada tiene que desaparecer, y conservar el
        // id de las que siguen no aporta nada — nadie referencia una línea de compra.
        runCatching { linesCol(companyId, purchase.id).get().documents }.getOrElse { emptyList() }
            .forEach { linesCol(companyId, purchase.id).document(it.id).delete() }
        lines.forEach { line ->
            val lineDoc = linesCol(companyId, purchase.id).document
            lineDoc.set(line.copy(id = lineDoc.id).toLineMap())
        }
    }

    override suspend fun getLines(companyId: String, purchaseId: String): List<PurchaseLine> =
        linesCol(companyId, purchaseId).get().documents.map { it.toPurchaseLine() }

    override suspend fun updatePurchaseStatus(companyId: String, purchaseId: String, newStatus: String) {
        col(companyId).document(purchaseId).update(firestoreSafeOf("status" to newStatus))
    }

    override suspend fun deletePurchase(companyId: String, purchaseId: String) {
        // Las subcolecciones primero: Firestore no las borra en cascada, y quedarían
        // huérfanas bajo un documento que ya no existe.
        runCatching { linesCol(companyId, purchaseId).get().documents }.getOrElse { emptyList() }
            .forEach { linesCol(companyId, purchaseId).document(it.id).delete() }
        runCatching { paymentsCol(companyId, purchaseId).get().documents }.getOrElse { emptyList() }
            .forEach { paymentsCol(companyId, purchaseId).document(it.id).delete() }
        col(companyId).document(purchaseId).delete()
    }

    override suspend fun markReceived(companyId: String, purchaseId: String, receivedAt: Long) {
        col(companyId).document(purchaseId).update(
            firestoreSafeOf(
                "status" to PurchaseStatus.RECEIVED_PENDING_PAYMENT,
                "receivedAt" to receivedAt
            )
        )
    }

    override suspend fun updateDueDate(companyId: String, purchaseId: String, dueDate: Long) {
        col(companyId).document(purchaseId).update(firestoreSafeOf("dueDate" to dueDate))
    }

    // ── Cuentas por pagar ────────────────────────────────────────────────────

    override suspend fun getPayments(companyId: String, purchaseId: String): List<PurchasePayment> =
        paymentsCol(companyId, purchaseId).get().documents
            .map { it.toPayment() }
            .sortedByDescending { it.date }

    override suspend fun addPayment(
        companyId: String,
        purchaseId: String,
        payment: PurchasePayment
    ): Purchase? {
        // El tope se comprueba contra el saldo recién leído y no contra el que tenía la
        // pantalla: entre que se abrió el detalle y se confirmó el abono, alguien más pudo
        // haber pagado desde otro dispositivo. Sin esto, "no exceder el saldo" es un aviso;
        // con esto, es una regla.
        val current = getPurchase(companyId, purchaseId)
            ?: error("No se encontró la compra")
        val paid = getPayments(companyId, purchaseId).sumOf { it.amount }
        val balance = (current.totalAmount - paid).coerceAtLeast(0.0)
        if (payment.amount > balance + PURCHASE_BALANCE_EPSILON) {
            error("El abono excede el saldo pendiente de la factura")
        }
        val doc = paymentsCol(companyId, purchaseId).document
        doc.set(payment.copy(id = doc.id).toPaymentMap())
        return recalculateBalance(companyId, purchaseId)
    }

    override suspend fun deletePayment(
        companyId: String,
        purchaseId: String,
        paymentId: String
    ): Purchase? {
        paymentsCol(companyId, purchaseId).document(paymentId).delete()
        return recalculateBalance(companyId, purchaseId)
    }

    /**
     * Vuelve a sumar los abonos y persiste `paidAmount` + el estado que corresponde.
     *
     * Se recalcula desde la subcolección en vez de sumar/restar sobre el valor guardado
     * para que un abono duplicado por un doble toque, o un borrado, no dejen el saldo
     * corrido para siempre — la subcolección es la fuente de verdad, el campo es la caché.
     */
    private suspend fun recalculateBalance(companyId: String, purchaseId: String): Purchase? {
        val purchase = getPurchase(companyId, purchaseId) ?: return null
        val paid = getPayments(companyId, purchaseId).sumOf { it.amount }
        val settled = purchase.totalAmount - paid <= PURCHASE_BALANCE_EPSILON
        // Una compra que aún no se recibe no pasa a "pagada" por abonarla: pagar por
        // adelantado no mete el pan a la bodega.
        val newStatus = when {
            purchase.status == PurchaseStatus.PENDING_RECEIPT -> purchase.status
            settled -> PurchaseStatus.PAID
            else -> PurchaseStatus.RECEIVED_PENDING_PAYMENT
        }
        col(companyId).document(purchaseId).update(
            firestoreSafeOf("paidAmount" to paid, "status" to newStatus)
        )
        return purchase.copy(paidAmount = paid, status = newStatus)
    }

    // ── Serialización ────────────────────────────────────────────────────────

    private fun Purchase.toPurchaseMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "branchId" to branchId,
        "branchName" to branchName,
        "supplierId" to supplierId,
        "supplierName" to supplierName,
        "date" to date,
        "notes" to notes,
        "lineCount" to lineCount,
        "createdAt" to createdAt,
        "invoiceNumber" to invoiceNumber,
        "receivedById" to receivedById,
        "receivedByName" to receivedByName,
        "status" to status,
        "totalAmount" to totalAmount,
        "paidAmount" to paidAmount,
        "taxAmount" to taxAmount,
        "taxRatePercent" to taxRatePercent,
        "declaredTotal" to declaredTotal,
        "invoicePhotoUrl" to invoicePhotoUrl,
        "nearestExpiryDate" to nearestExpiryDate,
        "nearestExpiryItemName" to nearestExpiryItemName,
        "paymentTermsDays" to paymentTermsDays,
        "dueDate" to dueDate,
        "receivedAt" to receivedAt,
        "itemIds" to itemIds,
        "itemNames" to itemNames,
        "lineKinds" to lineKinds,
        "kind" to kind
    )

    private fun PurchaseLine.toLineMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "ingredientId" to ingredientId,
        "ingredientName" to ingredientName,
        "ingredientUnit" to ingredientUnit,
        "quantityReceived" to quantityReceived,
        "packagesReceived" to packagesReceived,
        "packageDescription" to packageDescription,
        "unitCost" to unitCost,
        "packageCost" to packageCost,
        "formatId" to formatId,
        "lineKind" to lineKind,
        "productId" to productId,
        "productName" to productName,
        "expiryDate" to expiryDate,
        "lotCode" to lotCode,
        // Línea de servicio / gasto (sin catálogo detrás)
        "conceptName" to conceptName,
        "expenseCategory" to expenseCategory,
        "assetId" to assetId,
        "assetName" to assetName,
        "assetEventType" to assetEventType,
        // Vuelta de compras: sucursal y lugar propios de la línea
        "branchId" to branchId,
        "branchName" to branchName,
        "placeName" to placeName
    )

    private fun PurchasePayment.toPaymentMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "amount" to amount,
        "date" to date,
        "method" to method,
        "reference" to reference,
        "notes" to notes,
        "createdById" to createdById,
        "createdByName" to createdByName,
        "createdAt" to if (createdAt == 0L) Clock.System.now().toEpochMilliseconds() else createdAt
    )

    private fun DocumentSnapshot.toPurchase() = Purchase(
        id = id,
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        supplierId = get<String?>("supplierId") ?: "",
        supplierName = get<String?>("supplierName") ?: "",
        date = readMillis("date"),
        notes = get<String?>("notes"),
        lineCount = runCatching { get<Long?>("lineCount")?.toInt() }.getOrNull() ?: 0,
        createdAt = readMillis("createdAt"),
        invoiceNumber = get<String?>("invoiceNumber"),
        receivedById = get<String?>("receivedById") ?: "",
        receivedByName = get<String?>("receivedByName") ?: "",
        status = get<String?>("status") ?: PurchaseStatus.RECEIVED_PENDING_PAYMENT,
        totalAmount = readDouble("totalAmount"),
        paidAmount = readDouble("paidAmount"),
        taxAmount = readDouble("taxAmount"),
        taxRatePercent = readDouble("taxRatePercent"),
        declaredTotal = readDouble("declaredTotal"),
        invoicePhotoUrl = get<String?>("invoicePhotoUrl") ?: "",
        nearestExpiryDate = readMillis("nearestExpiryDate"),
        nearestExpiryItemName = get<String?>("nearestExpiryItemName") ?: "",
        paymentTermsDays = runCatching { get<Long?>("paymentTermsDays")?.toInt() }.getOrNull() ?: 0,
        dueDate = readMillis("dueDate"),
        receivedAt = readMillis("receivedAt"),
        itemIds = runCatching { get<List<String>?>("itemIds") }.getOrNull().orEmpty(),
        itemNames = runCatching { get<List<String>?>("itemNames") }.getOrNull().orEmpty(),
        lineKinds = runCatching { get<List<String>?>("lineKinds") }.getOrNull().orEmpty(),
        // Las compras anteriores a la vuelta de compras son todas facturas.
        kind = runCatching { get<String?>("kind") }.getOrNull() ?: PurchaseKind.INVOICE
    )

    private fun DocumentSnapshot.toPurchaseLine() = PurchaseLine(
        id = id,
        ingredientId = get<String?>("ingredientId") ?: "",
        ingredientName = get<String?>("ingredientName") ?: "",
        ingredientUnit = get<String?>("ingredientUnit") ?: "",
        quantityReceived = readDouble("quantityReceived"),
        packagesReceived = readDouble("packagesReceived"),
        packageDescription = get<String?>("packageDescription") ?: "",
        unitCost = readDouble("unitCost"),
        packageCost = readDouble("packageCost"),
        formatId = get<String?>("formatId") ?: "",
        lineKind = get<String?>("lineKind") ?: PurchaseLineKind.INGREDIENT,
        productId = get<String?>("productId") ?: "",
        productName = get<String?>("productName") ?: "",
        expiryDate = readMillis("expiryDate"),
        lotCode = get<String?>("lotCode") ?: "",
        conceptName = runCatching { get<String?>("conceptName") }.getOrNull() ?: "",
        expenseCategory = runCatching { get<String?>("expenseCategory") }.getOrNull() ?: "",
        assetId = runCatching { get<String?>("assetId") }.getOrNull() ?: "",
        assetName = runCatching { get<String?>("assetName") }.getOrNull() ?: "",
        assetEventType = runCatching { get<String?>("assetEventType") }.getOrNull() ?: "",
        branchId = runCatching { get<String?>("branchId") }.getOrNull() ?: "",
        branchName = runCatching { get<String?>("branchName") }.getOrNull() ?: "",
        placeName = runCatching { get<String?>("placeName") }.getOrNull() ?: ""
    )

    private fun DocumentSnapshot.toPayment() = PurchasePayment(
        id = id,
        amount = readDouble("amount"),
        date = readMillis("date"),
        method = get<String?>("method") ?: PurchasePaymentMethod.CASH,
        reference = get<String?>("reference") ?: "",
        notes = get<String?>("notes"),
        createdById = get<String?>("createdById") ?: "",
        createdByName = get<String?>("createdByName") ?: "",
        createdAt = readMillis("createdAt")
    )

    private fun DocumentSnapshot.readDouble(field: String): Double =
        runCatching { get<Double?>(field) }.getOrNull() ?: 0.0

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
