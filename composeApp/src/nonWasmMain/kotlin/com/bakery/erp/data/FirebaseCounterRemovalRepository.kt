package com.bakery.erp.data

import com.bakery.erp.domain.CounterRemovalRepository
import com.bakery.erp.domain.CreditRepository
import com.bakery.erp.domain.FinishedStockRepository
import com.bakery.erp.model.CounterRemoval
import com.bakery.erp.model.CounterRemovalKind
import com.bakery.erp.model.CounterRemovalLine
import com.bakery.erp.model.CreditMovement
import com.bakery.erp.model.CreditMovementType
import com.bakery.erp.model.Employee
import com.bakery.erp.model.FinishedMovementReference
import com.bakery.erp.model.FinishedStockMovement
import com.bakery.erp.model.accidentForgiveBlocker
import com.bakery.erp.util.fmtMoney
import com.bakery.erp.util.fmtTrim
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlin.time.Clock

class FirebaseCounterRemovalRepository(
    private val firestore: FirebaseFirestore,
    private val finishedStockRepository: FinishedStockRepository,
    private val creditRepository: CreditRepository
) : CounterRemovalRepository {

    private fun col(companyId: String) = firestore.collection("companies/$companyId/counterRemovals")

    override fun newId(companyId: String): String = col(companyId).document.id

    override suspend fun register(companyId: String, removal: CounterRemoval, responsible: Employee?): CounterRemoval {
        require(removal.id.isNotEmpty()) { "La salida necesita id (newId)" }
        val now = Clock.System.now().toEpochMilliseconds()
        // El pan de colaborador no lleva líneas: no descuenta aquí, lo reparte el conteo de cierre.
        val lines = if (removal.isStaffBread) emptyList() else removal.lines.filter { it.quantity > 0.0 }
        val accident = removal.kind == CounterRemovalKind.ACCIDENT
        require(!accident || responsible != null) { "Accidente sin responsable" }
        val saved = removal.copy(
            lines = lines,
            chargeAmount = if (accident) removal.totalCost else 0.0,
            creditMovementId = if (accident) accidentMovementId(removal.id) else "",
            createdAt = removal.createdAt.takeIf { it > 0 } ?: now
        )
        // 1. Encabezado antes que los efectos.
        col(companyId).document(saved.id).set(saved.toMap())

        // 2. Inventario de tienda, en serie: applyMovement lee-modifica-escribe.
        val reason = buildString {
            append(CounterRemovalKind.label(saved.kind))
            if (accident) append(" — ${saved.responsibleEmployeeName}")
            saved.note.trim().takeIf { it.isNotEmpty() }?.let { append(": $it") }
        }
        lines.groupBy { it.productId }.forEach { (productId, group) ->
            finishedStockRepository.applyMovement(companyId, FinishedStockMovement(
                productId = productId,
                productName = group.first().productName,
                branchId = saved.branchId,
                branchName = saved.branchName,
                type = CounterRemovalKind.movementType(saved.kind),
                quantity = -group.sumOf { it.quantity },
                // A costo: es lo que suman los reportes de merma (cantidad × este valor).
                unitPriceAtMovement = group.first().unitCost,
                referenceType = FinishedMovementReference.COUNTER_REMOVAL,
                referenceId = saved.id,
                reason = reason,
                createdByUserId = saved.createdByUserId,
                createdByUserName = saved.createdByUserName,
                createdAt = saved.createdAt
            ))
        }

        // 3. Cargo a la colaboradora. Id fijo: un reintento reescribe el mismo renglón.
        if (accident && responsible != null && saved.chargeAmount > 0.0) {
            val account = creditRepository.ensureEmployeeAccount(companyId, responsible)
            creditRepository.setMovement(companyId, CreditMovement(
                id = saved.creditMovementId,
                accountId = account.id,
                accountName = account.name,
                type = CreditMovementType.ACCIDENT,
                amount = saved.chargeAmount,
                description = "Se le cayó/arruinó: " +
                    lines.joinToString { "${it.quantity.fmtTrim()} ${it.productName}" } + " (a costo)",
                sessionId = saved.sessionId,
                registerName = saved.registerName,
                branchId = saved.branchId,
                branchName = saved.branchName,
                createdByUserId = saved.createdByUserId,
                createdByUserName = saved.createdByUserName,
                createdByEmployeeId = saved.createdByEmployeeId,
                createdAt = saved.createdAt
            ))
        }
        return saved
    }

    override suspend fun getForSession(companyId: String, sessionId: String): List<CounterRemoval> =
        col(companyId).where { "sessionId" equalTo sessionId }.get().documents
            .map { it.toRemoval() }.sortedByDescending { it.createdAt }

    override suspend fun getInRange(
        companyId: String, branchId: String, fromMillis: Long, untilMillis: Long
    ): List<CounterRemoval> {
        val docs = if (branchId.isEmpty()) col(companyId).get().documents
                   else col(companyId).where { "branchId" equalTo branchId }.get().documents
        // Fechas en memoria (CLAUDE.md): una consulta por rango pediría índice compuesto.
        return docs.map { it.toRemoval() }
            .filter { it.createdAt in fromMillis until untilMillis }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun forgive(
        companyId: String, removalId: String,
        byUserId: String, byName: String, byEmployeeId: String, reason: String
    ): CounterRemoval {
        val ref = col(companyId).document(removalId)
        val doc = ref.get()
        check(doc.exists) { "No se encontró el registro" }
        val current = doc.toRemoval()
        accidentForgiveBlocker(current, byEmployeeId)?.let { throw IllegalStateException(it) }
        val now = Clock.System.now().toEpochMilliseconds()
        val forgiveId = "${accidentMovementId(removalId)}_forgiven"
        // Encabezado primero: un segundo intento choca con "ya se perdonó" en vez de abonar dos veces.
        ref.update(firestoreSafeOf(
            "forgiven" to true,
            "forgivenByUserId" to byUserId,
            "forgivenByName" to byName,
            "forgivenByEmployeeId" to byEmployeeId,
            "forgivenAt" to now,
            "forgiveReason" to reason.trim(),
            "forgiveCreditMovementId" to forgiveId
        ))
        if (current.chargeAmount > 0.0 && current.responsibleEmployeeId.isNotEmpty()) {
            val accountId = com.bakery.erp.model.employeeCreditAccountId(current.responsibleEmployeeId)
            // Corregir = escribir la diferencia: el cargo queda y el abono lo deja en cero.
            creditRepository.setMovement(companyId, CreditMovement(
                id = forgiveId,
                accountId = accountId,
                accountName = current.responsibleEmployeeName,
                type = CreditMovementType.ADJUSTMENT,
                amount = -current.chargeAmount,
                description = "Perdonado por $byName: accidente de Q ${current.chargeAmount.fmtMoney(2)}" +
                    reason.trim().takeIf { it.isNotEmpty() }?.let { " — $it" }.orEmpty(),
                branchId = current.branchId,
                branchName = current.branchName,
                createdByUserId = byUserId,
                createdByUserName = byName,
                createdByEmployeeId = byEmployeeId,
                createdAt = now
            ))
        }
        return current.copy(
            forgiven = true, forgivenByUserId = byUserId, forgivenByName = byName,
            forgivenByEmployeeId = byEmployeeId, forgivenAt = now, forgiveReason = reason.trim(),
            forgiveCreditMovementId = forgiveId
        )
    }

    private fun accidentMovementId(removalId: String) = "accident_$removalId"

    // ── Serialización ─────────────────────────────────────────────────────────

    private fun CounterRemoval.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "branchId" to branchId,
        "branchName" to branchName,
        "sessionId" to sessionId,
        "registerId" to registerId,
        "registerName" to registerName,
        "kind" to kind,
        "lineProductIds" to lines.map { it.productId },
        "lineProductNames" to lines.map { it.productName },
        "lineQuantities" to lines.map { it.quantity },
        "lineUnitCosts" to lines.map { it.unitCost },
        "lineUnitPrices" to lines.map { it.unitPrice },
        "totalCost" to totalCost,
        "totalPrice" to totalPrice,
        "note" to note,
        "photoUrl" to photoUrl,
        "responsibleEmployeeId" to responsibleEmployeeId,
        "responsibleEmployeeName" to responsibleEmployeeName,
        "responsibleConfirmation" to responsibleConfirmation,
        "chargeAmount" to chargeAmount,
        "creditMovementId" to creditMovementId,
        "forgiven" to forgiven,
        "forgivenByUserId" to forgivenByUserId,
        "forgivenByName" to forgivenByName,
        "forgivenByEmployeeId" to forgivenByEmployeeId,
        "forgivenAt" to forgivenAt,
        "forgiveReason" to forgiveReason,
        "forgiveCreditMovementId" to forgiveCreditMovementId,
        "groupId" to groupId,
        "groupName" to groupName,
        "groupUnits" to groupUnits,
        "groupUnitPrice" to groupUnitPrice,
        "createdByUserId" to createdByUserId,
        "createdByUserName" to createdByUserName,
        "createdByEmployeeId" to createdByEmployeeId,
        "createdAt" to createdAt
    )

    private fun DocumentSnapshot.str(field: String): String =
        runCatching { get<String?>(field) }.getOrNull() ?: ""

    private fun DocumentSnapshot.num(field: String): Double =
        runCatching { get<Double?>(field) }.getOrNull()
            ?: runCatching { get<Long?>(field)?.toDouble() }.getOrNull() ?: 0.0

    private fun DocumentSnapshot.strings(field: String): List<String> =
        runCatching { get<List<String>?>(field) }.getOrNull() ?: emptyList()

    /** Un número entero puede volver como Long desde la web: se aceptan los dos. */
    private fun DocumentSnapshot.numbers(field: String): List<Double> =
        runCatching { get<List<Double>?>(field) }.getOrNull()
            ?: runCatching { get<List<Long>?>(field)?.map { it.toDouble() } }.getOrNull()
            ?: emptyList()

    private fun DocumentSnapshot.millis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: runCatching { get<Double?>(field)?.toLong() }.getOrNull() ?: 0L

    private fun DocumentSnapshot.toRemoval(): CounterRemoval {
        val ids = strings("lineProductIds")
        val names = strings("lineProductNames")
        val qty = numbers("lineQuantities")
        val costs = numbers("lineUnitCosts")
        val prices = numbers("lineUnitPrices")
        return CounterRemoval(
            id = id,
            branchId = str("branchId"),
            branchName = str("branchName"),
            sessionId = str("sessionId"),
            registerId = str("registerId"),
            registerName = str("registerName"),
            kind = str("kind").ifEmpty { CounterRemovalKind.DAMAGED },
            lines = ids.indices.map { i ->
                CounterRemovalLine(
                    productId = ids[i],
                    productName = names.getOrElse(i) { "" },
                    quantity = qty.getOrElse(i) { 0.0 },
                    unitCost = costs.getOrElse(i) { 0.0 },
                    unitPrice = prices.getOrElse(i) { 0.0 }
                )
            },
            note = str("note"),
            photoUrl = str("photoUrl"),
            responsibleEmployeeId = str("responsibleEmployeeId"),
            responsibleEmployeeName = str("responsibleEmployeeName"),
            responsibleConfirmation = str("responsibleConfirmation"),
            chargeAmount = num("chargeAmount"),
            creditMovementId = str("creditMovementId"),
            forgiven = runCatching { get<Boolean?>("forgiven") }.getOrNull() ?: false,
            forgivenByUserId = str("forgivenByUserId"),
            forgivenByName = str("forgivenByName"),
            forgivenByEmployeeId = str("forgivenByEmployeeId"),
            forgivenAt = millis("forgivenAt"),
            forgiveReason = str("forgiveReason"),
            forgiveCreditMovementId = str("forgiveCreditMovementId"),
            groupId = str("groupId"),
            groupName = str("groupName"),
            groupUnits = num("groupUnits"),
            groupUnitPrice = num("groupUnitPrice"),
            createdByUserId = str("createdByUserId"),
            createdByUserName = str("createdByUserName"),
            createdByEmployeeId = str("createdByEmployeeId"),
            createdAt = millis("createdAt")
        )
    }
}
