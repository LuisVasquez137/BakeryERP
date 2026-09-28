package com.bakery.erp.data

import com.bakery.erp.domain.CustomerDepositRepository
import com.bakery.erp.model.CustomerDeposit
import com.bakery.erp.model.CustomerDepositStatus
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.customerDepositFolio
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.where
import kotlin.math.round
import kotlin.time.Clock

class FirebaseCustomerDepositRepository(
    private val firestore: FirebaseFirestore
) : CustomerDepositRepository {

    private fun col(companyId: String) = firestore.collection("companies/$companyId/customerDeposits")
    private fun counter(companyId: String) = firestore.collection("companies/$companyId/counters").document("customerDeposits")

    override suspend fun create(companyId: String, deposit: CustomerDeposit): CustomerDeposit {
        val ref = col(companyId).document
        val now = Clock.System.now().toEpochMilliseconds()
        // Folio y documento en la misma transacción: dos cajas recibiendo a la vez no pueden
        // quedar con el mismo número, que es lo que el cliente trae en la mano.
        return firestore.runTransaction {
            val snap = get(counter(companyId))
            val next = (if (snap.exists) readLong(snap, "next") else 0L).coerceAtLeast(0L) + 1
            val saved = deposit.copy(
                id = ref.id,
                folio = customerDepositFolio(next),
                status = CustomerDepositStatus.OPEN,
                receivedAt = if (deposit.receivedAt > 0) deposit.receivedAt else now
            )
            set(counter(companyId), firestoreSafeOf("next" to next, "updatedAt" to now))
            set(ref, saved.toMap())
            saved
        }
    }

    override suspend fun getOpen(companyId: String): List<CustomerDeposit> =
        col(companyId).where { "status" equalTo CustomerDepositStatus.OPEN }.get().documents
            .map { it.toDeposit() }
            .filter { it.isOpen }
            // Lo que se entrega primero, primero: es lo que hay que preparar. Sin fecha, al final.
            .sortedWith(compareBy<CustomerDeposit> { if (it.deliveryAt > 0) it.deliveryAt else Long.MAX_VALUE }
                .thenByDescending { it.receivedAt })

    override suspend fun get(companyId: String, id: String): CustomerDeposit? =
        runCatching { col(companyId).document(id).get() }.getOrNull()
            ?.takeIf { it.exists }?.toDeposit()

    override suspend fun apply(companyId: String, id: String, amount: Double, branchName: String): CustomerDeposit {
        val ref = col(companyId).document(id)
        return firestore.runTransaction {
            val current = get(ref).takeIf { it.exists }?.toDeposit() ?: error("El anticipo ya no existe.")
            if (current.status != CustomerDepositStatus.OPEN || current.remaining + 0.004 < amount) {
                error("El anticipo ${current.folio} ya se aplicó o se devolvió. Le quedan Q ${money(current.remaining)}.")
            }
            val applied = money(current.appliedAmount + amount)
            val updated = current.copy(
                appliedAmount = applied,
                status = if (money(current.amount - applied - current.refundedAmount) <= 0.004)
                    CustomerDepositStatus.APPLIED else CustomerDepositStatus.OPEN,
                lastAppliedAt = Clock.System.now().toEpochMilliseconds(),
                lastAppliedBranchName = branchName
            )
            set(ref, updated.toMap())
            updated
        }
    }

    override suspend fun release(companyId: String, id: String, amount: Double) {
        val ref = col(companyId).document(id)
        firestore.runTransaction {
            val current = get(ref).takeIf { it.exists }?.toDeposit() ?: return@runTransaction
            val applied = money((current.appliedAmount - amount).coerceAtLeast(0.0))
            set(ref, current.copy(appliedAmount = applied, status = CustomerDepositStatus.OPEN).toMap())
        }
    }

    override suspend fun linkSales(companyId: String, id: String, saleIds: List<String>) {
        val ref = col(companyId).document(id)
        firestore.runTransaction {
            val current = get(ref).takeIf { it.exists }?.toDeposit() ?: return@runTransaction
            set(ref, current.copy(appliedSaleIds = (current.appliedSaleIds + saleIds).distinct()).toMap())
        }
    }

    override suspend fun refund(companyId: String, id: String, byUserName: String, reason: String): Double {
        val ref = col(companyId).document(id)
        return firestore.runTransaction {
            val current = get(ref).takeIf { it.exists }?.toDeposit() ?: error("El anticipo ya no existe.")
            val amount = current.remaining
            if (current.status != CustomerDepositStatus.OPEN || amount <= 0.004) {
                error("El anticipo ${current.folio} ya no tiene saldo que devolver.")
            }
            set(ref, current.copy(
                refundedAmount = money(current.refundedAmount + amount),
                status = CustomerDepositStatus.REFUNDED,
                refundedAt = Clock.System.now().toEpochMilliseconds(),
                refundedByUserName = byUserName,
                refundReason = reason
            ).toMap())
            amount
        }
    }

    // ── Serialización ────────────────────────────────────────────────────────

    private fun CustomerDeposit.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "folio" to folio,
        "status" to status,
        "customerName" to customerName,
        "customerPhone" to customerPhone,
        "description" to description,
        "deliveryLabel" to deliveryLabel,
        "deliveryAt" to deliveryAt,
        "isRestaurant" to isRestaurant,
        // Arreglos paralelos: GitLive no serializa listas de objetos.
        "itemProductIds" to items.map { it.productId },
        "itemNames" to items.map { it.productName },
        "itemQuantities" to items.map { it.quantity },
        "itemUnitPrices" to items.map { it.unitPrice },
        "amount" to amount,
        "method" to method,
        "branchId" to branchId,
        "branchName" to branchName,
        "sessionId" to sessionId,
        "registerName" to registerName,
        "receivedByUserId" to receivedByUserId,
        "receivedByUserName" to receivedByUserName,
        "receivedAt" to receivedAt,
        "appliedAmount" to appliedAmount,
        "appliedSaleIds" to appliedSaleIds,
        "lastAppliedAt" to lastAppliedAt,
        "lastAppliedBranchName" to lastAppliedBranchName,
        "refundedAmount" to refundedAmount,
        "refundedAt" to refundedAt,
        "refundedByUserName" to refundedByUserName,
        "refundReason" to refundReason,
        "notes" to notes,
        "customerOrderId" to customerOrderId,
        "customerOrderLabel" to customerOrderLabel
    )

    private fun DocumentSnapshot.toDeposit(): CustomerDeposit {
        fun str(f: String) = runCatching { get<String?>(f) }.getOrNull().orEmpty()
        fun num(f: String) = runCatching { get<Double?>(f) }.getOrNull()
            ?: runCatching { get<Long?>(f)?.toDouble() }.getOrNull() ?: 0.0
        return CustomerDeposit(
            id = id,
            folio = str("folio"),
            status = str("status").ifBlank { CustomerDepositStatus.OPEN },
            customerName = str("customerName"),
            customerPhone = str("customerPhone"),
            description = str("description"),
            deliveryLabel = str("deliveryLabel"),
            deliveryAt = num("deliveryAt").toLong(),
            isRestaurant = runCatching { get<Boolean?>("isRestaurant") }.getOrNull() ?: false,
            items = run {
                val ids = runCatching { get<List<String>?>("itemProductIds") }.getOrNull().orEmpty()
                val names = runCatching { get<List<String>?>("itemNames") }.getOrNull().orEmpty()
                val qtys = runCatching { get<List<Double>?>("itemQuantities") }.getOrNull().orEmpty()
                val prices = runCatching { get<List<Double>?>("itemUnitPrices") }.getOrNull().orEmpty()
                names.indices.map { i ->
                    com.bakery.erp.model.CustomerDepositItem(
                        productId = ids.getOrElse(i) { "" },
                        productName = names[i],
                        quantity = qtys.getOrElse(i) { 0.0 },
                        unitPrice = prices.getOrElse(i) { 0.0 }
                    )
                }
            },
            amount = num("amount"),
            method = str("method").ifBlank { SalePaymentMethod.CASH },
            branchId = str("branchId"),
            branchName = str("branchName"),
            sessionId = str("sessionId"),
            registerName = str("registerName"),
            receivedByUserId = str("receivedByUserId"),
            receivedByUserName = str("receivedByUserName"),
            receivedAt = num("receivedAt").toLong(),
            appliedAmount = num("appliedAmount"),
            appliedSaleIds = runCatching { get<List<String>?>("appliedSaleIds") }.getOrNull().orEmpty(),
            lastAppliedAt = num("lastAppliedAt").toLong(),
            lastAppliedBranchName = str("lastAppliedBranchName"),
            refundedAmount = num("refundedAmount"),
            refundedAt = num("refundedAt").toLong(),
            refundedByUserName = str("refundedByUserName"),
            refundReason = str("refundReason"),
            notes = str("notes"),
            customerOrderId = str("customerOrderId"),
            customerOrderLabel = str("customerOrderLabel")
        )
    }

    private fun readLong(snap: DocumentSnapshot, field: String): Long =
        runCatching { snap.get<Long?>(field) }.getOrNull()
            ?: runCatching { snap.get<Double?>(field)?.toLong() }.getOrNull() ?: 0L

    private fun money(v: Double) = round(v * 100) / 100
}
