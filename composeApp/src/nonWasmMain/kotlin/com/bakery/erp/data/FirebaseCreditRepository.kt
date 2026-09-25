package com.bakery.erp.data

import com.bakery.erp.domain.CreditRepository
import com.bakery.erp.model.AuthorizedPerson
import com.bakery.erp.model.CreditAccount
import com.bakery.erp.model.CreditAccountType
import com.bakery.erp.model.CreditMovement
import com.bakery.erp.model.Employee
import com.bakery.erp.model.creditBalanceOf
import com.bakery.erp.model.employeeCreditAccountId
import com.bakery.erp.model.openCharges
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlin.time.Clock

/**
 * `companies/{c}/creditAccounts/{id}` — las personas autorizadas van en arreglos paralelos
 * dentro del documento (son pocas y se leen siempre junto con la cuenta; una subcolección
 * costaría una lectura más por cuenta en el buscador del POS).
 *
 * `companies/{c}/creditMovements/{id}` — colección PLANA con `accountId`: el estado de cuenta
 * es una sola consulta de igualdad, sin índice compuesto.
 */
class FirebaseCreditRepository(
    private val firestore: FirebaseFirestore
) : CreditRepository {

    private fun accountsCol(companyId: String) =
        firestore.collection("companies/$companyId/creditAccounts")

    private fun movementsCol(companyId: String) =
        firestore.collection("companies/$companyId/creditMovements")

    override suspend fun getAccounts(companyId: String): List<CreditAccount> =
        accountsCol(companyId).get().documents.map { it.toAccount() }.sortedBy { it.name.lowercase() }

    override suspend fun getAccount(companyId: String, accountId: String): CreditAccount? =
        accountsCol(companyId).document(accountId).get().takeIf { it.exists }?.toAccount()

    override suspend fun saveAccount(companyId: String, account: CreditAccount): CreditAccount {
        require(account.name.isNotBlank()) { "El nombre no puede estar vacío" }
        val ref = if (account.id.isEmpty()) accountsCol(companyId).document
                  else accountsCol(companyId).document(account.id)
        val now = Clock.System.now().toEpochMilliseconds()
        // El saldo no lo decide quien edita la ficha: se toma el guardado, que es el que
        // mantienen los movimientos. Si no, editar el teléfono con una pantalla abierta desde
        // hace rato devolvería el saldo al de ese momento.
        val stored = if (account.id.isEmpty()) null else getAccount(companyId, account.id)
        val toSave = account.copy(
            id = ref.id,
            balance = stored?.balance ?: 0.0,
            lastMovementAt = stored?.lastMovementAt ?: 0L,
            openChargeDates = stored?.openChargeDates ?: emptyList(),
            openChargeAmounts = stored?.openChargeAmounts ?: emptyList(),
            persons = account.persons.mapIndexed { i, p ->
                if (p.id.isEmpty()) p.copy(id = "p${now.toString(36)}$i") else p
            },
            createdAt = stored?.createdAt?.takeIf { it > 0 } ?: now,
            updatedAt = now
        )
        ref.set(toSave.toMap())
        return toSave
    }

    override suspend fun ensureEmployeeAccount(companyId: String, employee: Employee): CreditAccount {
        val id = employeeCreditAccountId(employee.id)
        val existing = getAccount(companyId, id)
        if (existing != null && existing.name == employee.name) return existing
        val account = (existing ?: CreditAccount(
            id = id,
            type = CreditAccountType.EMPLOYEE,
            employeeId = employee.id
        )).copy(name = employee.name)
        return saveAccountWithFixedId(companyId, account)
    }

    private suspend fun saveAccountWithFixedId(companyId: String, account: CreditAccount): CreditAccount {
        val now = Clock.System.now().toEpochMilliseconds()
        val toSave = account.copy(
            createdAt = account.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now
        )
        accountsCol(companyId).document(account.id).set(toSave.toMap())
        return toSave
    }

    override suspend fun getMovements(companyId: String, accountId: String): List<CreditMovement> =
        movementsCol(companyId).where { "accountId" equalTo accountId }.get().documents
            .map { it.toMovement() }
            .sortedByDescending { it.createdAt }

    override suspend fun addMovement(companyId: String, movement: CreditMovement): CreditMovement {
        val ref = movementsCol(companyId).document
        return writeMovement(companyId, movement.copy(id = ref.id))
    }

    override suspend fun setMovement(companyId: String, movement: CreditMovement): CreditMovement {
        require(movement.id.isNotEmpty()) { "setMovement necesita un id fijo" }
        return writeMovement(companyId, movement)
    }

    private suspend fun writeMovement(companyId: String, movement: CreditMovement): CreditMovement {
        require(movement.accountId.isNotEmpty()) { "Movimiento sin cuenta" }
        val now = Clock.System.now().toEpochMilliseconds()
        val toSave = movement.copy(createdAt = movement.createdAt.takeIf { it > 0 } ?: now)
        movementsCol(companyId).document(toSave.id).set(toSave.toMap())
        recalculateBalance(companyId, movement.accountId)
        return toSave
    }

    override suspend fun deleteMovement(companyId: String, accountId: String, movementId: String) {
        val ref = movementsCol(companyId).document(movementId)
        if (!ref.get().exists) return
        ref.delete()
        recalculateBalance(companyId, accountId)
    }

    /**
     * Saldo = suma de TODOS los movimientos, releídos. No se suma sobre el valor guardado:
     * un reintento o dos cajas cobrando a la vez lo dejarían corrido para siempre.
     */
    override suspend fun recalculate(companyId: String, accountId: String) =
        recalculateBalance(companyId, accountId)

    private suspend fun recalculateBalance(companyId: String, accountId: String) {
        val movements = getMovements(companyId, accountId)
        val account = getAccount(companyId, accountId) ?: return
        val open = openCharges(movements)
        accountsCol(companyId).document(accountId).set(
            account.copy(
                balance = creditBalanceOf(movements),
                lastMovementAt = movements.maxOfOrNull { it.createdAt } ?: 0L,
                openChargeDates = open.map { it.at },
                openChargeAmounts = open.map { it.remaining }
            ).toMap()
        )
    }

    // ── Serialización ────────────────────────────────────────────────────────

    private fun CreditAccount.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "type" to type,
        "name" to name,
        "taxId" to taxId,
        "phone" to phone,
        "notes" to notes,
        "employeeId" to employeeId,
        "active" to active,
        "personIds" to persons.map { it.id },
        "personNames" to persons.map { it.name },
        "personDocuments" to persons.map { it.idDocument },
        "personPhones" to persons.map { it.phone },
        "personActive" to persons.map { it.active },
        "balance" to balance,
        "lastMovementAt" to lastMovementAt,
        "openChargeDates" to openChargeDates,
        "openChargeAmounts" to openChargeAmounts,
        "cashDiscountEligible" to cashDiscountEligible,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    private fun DocumentSnapshot.toAccount(): CreditAccount {
        fun strings(field: String) = runCatching { get<List<String>?>(field) }.getOrNull() ?: emptyList()
        val ids = strings("personIds")
        val names = strings("personNames")
        val docs = strings("personDocuments")
        val phones = strings("personPhones")
        val actives = runCatching { get<List<Boolean>?>("personActive") }.getOrNull() ?: emptyList()
        return CreditAccount(
            id = id,
            type = get<String?>("type") ?: CreditAccountType.PERSON,
            name = get<String?>("name") ?: "",
            taxId = get<String?>("taxId") ?: "",
            phone = get<String?>("phone") ?: "",
            notes = get<String?>("notes") ?: "",
            employeeId = get<String?>("employeeId") ?: "",
            active = get<Boolean?>("active") ?: true,
            persons = ids.indices.map { i ->
                AuthorizedPerson(
                    id = ids[i],
                    name = names.getOrElse(i) { "" },
                    idDocument = docs.getOrElse(i) { "" },
                    phone = phones.getOrElse(i) { "" },
                    active = actives.getOrElse(i) { true }
                )
            },
            balance = get<Double?>("balance") ?: 0.0,
            lastMovementAt = readMillis("lastMovementAt"),
            openChargeDates = runCatching { get<List<Double>?>("openChargeDates") }.getOrNull()
                ?.map { it.toLong() } ?: emptyList(),
            openChargeAmounts = runCatching { get<List<Double>?>("openChargeAmounts") }.getOrNull() ?: emptyList(),
            // Hubo una versión con % por persona; una cuenta que lo tenga queda con derecho.
            cashDiscountEligible = get<Boolean?>("cashDiscountEligible")
                ?: ((runCatching { get<Double?>("cashDiscountPercent") }.getOrNull() ?: 0.0) > 0.0),
            createdAt = readMillis("createdAt"),
            updatedAt = readMillis("updatedAt")
        )
    }

    private fun CreditMovement.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "accountId" to accountId,
        "accountName" to accountName,
        "type" to type,
        "amount" to amount,
        "description" to description,
        "personId" to personId,
        "personName" to personName,
        "saleId" to saleId,
        "payrollId" to payrollId,
        "sessionId" to sessionId,
        "registerName" to registerName,
        "branchId" to branchId,
        "branchName" to branchName,
        "method" to method,
        "createdByUserId" to createdByUserId,
        "createdByUserName" to createdByUserName,
        "createdAt" to createdAt
    )

    private fun DocumentSnapshot.toMovement() = CreditMovement(
        id = id,
        accountId = get<String?>("accountId") ?: "",
        accountName = get<String?>("accountName") ?: "",
        type = get<String?>("type") ?: "",
        amount = get<Double?>("amount") ?: 0.0,
        description = get<String?>("description") ?: "",
        personId = get<String?>("personId") ?: "",
        personName = get<String?>("personName") ?: "",
        saleId = get<String?>("saleId") ?: "",
        payrollId = get<String?>("payrollId") ?: "",
        sessionId = get<String?>("sessionId") ?: "",
        registerName = get<String?>("registerName") ?: "",
        branchId = get<String?>("branchId") ?: "",
        branchName = get<String?>("branchName") ?: "",
        method = get<String?>("method") ?: "",
        createdByUserId = get<String?>("createdByUserId") ?: "",
        createdByUserName = get<String?>("createdByUserName") ?: "",
        createdAt = readMillis("createdAt")
    )

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: runCatching { get<Double?>(field)?.toLong() }.getOrNull()
            ?: 0L
}
