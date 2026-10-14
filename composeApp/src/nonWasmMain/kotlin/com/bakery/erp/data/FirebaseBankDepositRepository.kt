package com.bakery.erp.data

import com.bakery.erp.domain.BankDepositRepository
import com.bakery.erp.model.BankDeposit
import com.bakery.erp.model.BankDepositStatus
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlin.time.Clock

/**
 * `companies/{c}/bankDeposits`, plana. Se crea con `set()` una sola vez; lo que pasa después
 * (movimiento, boleta, sin boleta) son escrituras parciales para no pisar lo que ya hay.
 */
class FirebaseBankDepositRepository(
    private val firestore: FirebaseFirestore
) : BankDepositRepository {

    private fun col(companyId: String) = firestore.collection("companies/$companyId/bankDeposits")
    private fun now() = Clock.System.now().toEpochMilliseconds()

    override suspend fun create(companyId: String, deposit: BankDeposit): BankDeposit {
        val ref = col(companyId).document
        val d = deposit.copy(id = ref.id, createdAt = now(), status = BankDepositStatus.PENDING)
        ref.set(d.toMap())
        return d
    }

    override suspend fun get(companyId: String, depositId: String): BankDeposit? =
        col(companyId).document(depositId).get().takeIf { it.exists }?.toDeposit()

    override suspend fun getPendingForRegister(companyId: String, registerId: String): List<BankDeposit> =
        col(companyId).where { "registerId" equalTo registerId }.get().documents
            .map { it.toDeposit() }
            .filter { it.status == BankDepositStatus.PENDING }
            .sortedBy { it.createdAt }

    override suspend fun getForSession(companyId: String, sessionId: String): List<BankDeposit> =
        col(companyId).where { "sessionId" equalTo sessionId }.get().documents
            .map { it.toDeposit() }
            .filter { it.status != BankDepositStatus.CANCELLED }
            .sortedBy { it.createdAt }

    override suspend fun setMovement(companyId: String, depositId: String, movementId: String) {
        col(companyId).document(depositId).update(firestoreSafeOf("movementId" to movementId))
    }

    override suspend fun cancel(companyId: String, depositId: String, reason: String) {
        col(companyId).document(depositId).update(firestoreSafeOf(
            "status" to BankDepositStatus.CANCELLED,
            "missingSlipReason" to reason
        ))
    }

    override suspend fun attachSlip(
        companyId: String, depositId: String, photoUrl: String, slipNumber: String, slipAmount: Double,
        userName: String, employeeId: String
    ) {
        col(companyId).document(depositId).update(firestoreSafeOf(
            "status" to BankDepositStatus.SLIP_ATTACHED,
            "slipPhotoUrl" to photoUrl,
            "slipNumber" to slipNumber,
            "slipAmount" to slipAmount,
            "slipAttachedAt" to now(),
            "slipAttachedByUserName" to userName,
            "slipAttachedByEmployeeId" to employeeId
        ))
    }

    override suspend fun markNoSlip(companyId: String, depositId: String, reason: String, userName: String) {
        col(companyId).document(depositId).update(firestoreSafeOf(
            "status" to BankDepositStatus.NO_SLIP,
            "missingSlipReason" to reason,
            "missingSlipAt" to now(),
            "missingSlipByUserName" to userName
        ))
    }

    private fun BankDeposit.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "branchId" to branchId,
        "branchName" to branchName,
        "registerId" to registerId,
        "registerName" to registerName,
        "sessionId" to sessionId,
        "movementId" to movementId,
        "atClose" to atClose,
        "amount" to amount,
        "bankName" to bankName,
        "carriedByEmployeeId" to carriedByEmployeeId,
        "carriedByEmployeeName" to carriedByEmployeeName,
        "carrierConfirmedWithPin" to carrierConfirmedWithPin,
        "notes" to notes,
        "createdByUserId" to createdByUserId,
        "createdByUserName" to createdByUserName,
        "createdByEmployeeId" to createdByEmployeeId,
        "createdAt" to createdAt,
        "status" to status,
        "slipPhotoUrl" to slipPhotoUrl,
        "slipNumber" to slipNumber,
        "slipAmount" to slipAmount,
        "slipAttachedAt" to slipAttachedAt,
        "slipAttachedByUserName" to slipAttachedByUserName,
        "slipAttachedByEmployeeId" to slipAttachedByEmployeeId,
        "missingSlipReason" to missingSlipReason,
        "missingSlipAt" to missingSlipAt,
        "missingSlipByUserName" to missingSlipByUserName
    )

    private fun DocumentSnapshot.str(field: String): String =
        runCatching { get<String?>(field) }.getOrNull() ?: ""

    private fun DocumentSnapshot.money(field: String): Double =
        runCatching { get<Double?>(field) }.getOrNull()
            ?: runCatching { get<Long?>(field)?.toDouble() }.getOrNull() ?: 0.0

    private fun DocumentSnapshot.millis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: runCatching { get<Double?>(field)?.toLong() }.getOrNull() ?: 0L

    private fun DocumentSnapshot.toDeposit() = BankDeposit(
        id = id,
        branchId = str("branchId"),
        branchName = str("branchName"),
        registerId = str("registerId"),
        registerName = str("registerName"),
        sessionId = str("sessionId"),
        movementId = str("movementId"),
        atClose = runCatching { get<Boolean?>("atClose") }.getOrNull() ?: false,
        amount = money("amount"),
        bankName = str("bankName"),
        carriedByEmployeeId = str("carriedByEmployeeId"),
        carriedByEmployeeName = str("carriedByEmployeeName"),
        carrierConfirmedWithPin = runCatching { get<Boolean?>("carrierConfirmedWithPin") }.getOrNull() ?: false,
        notes = str("notes"),
        createdByUserId = str("createdByUserId"),
        createdByUserName = str("createdByUserName"),
        createdByEmployeeId = str("createdByEmployeeId"),
        createdAt = millis("createdAt"),
        status = str("status").ifEmpty { BankDepositStatus.PENDING },
        slipPhotoUrl = str("slipPhotoUrl"),
        slipNumber = str("slipNumber"),
        slipAmount = money("slipAmount"),
        slipAttachedAt = millis("slipAttachedAt"),
        slipAttachedByUserName = str("slipAttachedByUserName"),
        slipAttachedByEmployeeId = str("slipAttachedByEmployeeId"),
        missingSlipReason = str("missingSlipReason"),
        missingSlipAt = millis("missingSlipAt"),
        missingSlipByUserName = str("missingSlipByUserName")
    )
}
