package com.bakery.erp.data

import com.bakery.erp.domain.PaymentRulesRepository
import com.bakery.erp.model.CardPaymentRules
import com.bakery.erp.model.CardSurchargeMode
import com.bakery.erp.model.CardSurchargeType
import com.bakery.erp.model.cardRulesProblem
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebasePaymentRulesRepository(
    private val firestore: FirebaseFirestore
) : PaymentRulesRepository {

    private fun cardDoc(companyId: String) =
        firestore.collection("companies/$companyId/paymentRules").document("card")

    override suspend fun getCardRules(companyId: String): CardPaymentRules =
        cardDoc(companyId).get().let { if (it.exists) it.toRules() else CardPaymentRules() }

    override fun observeCardRules(companyId: String): Flow<CardPaymentRules> =
        cardDoc(companyId).snapshots.map { if (it.exists) it.toRules() else CardPaymentRules() }

    override suspend fun saveCardRules(companyId: String, rules: CardPaymentRules) {
        cardRulesProblem(rules)?.let { throw IllegalArgumentException(it) }
        val r = rules.copy(updatedAt = Clock.System.now().toEpochMilliseconds())
        cardDoc(companyId).set(firestoreSafeOf(
            "minimumAmount" to r.minimumAmount,
            "surchargeMode" to r.surchargeMode,
            "surchargeThreshold" to r.surchargeThreshold,
            "surchargeType" to r.surchargeType,
            "surchargeValue" to r.surchargeValue,
            "updatedAt" to r.updatedAt,
            "updatedByName" to r.updatedByName
        ))
    }

    private fun DocumentSnapshot.num(field: String): Double =
        runCatching { get<Double?>(field) }.getOrNull()
            ?: runCatching { get<Long?>(field)?.toDouble() }.getOrNull() ?: 0.0

    private fun DocumentSnapshot.toRules() = CardPaymentRules(
        minimumAmount = num("minimumAmount"),
        surchargeMode = get<String?>("surchargeMode") ?: CardSurchargeMode.NEVER,
        surchargeThreshold = num("surchargeThreshold"),
        surchargeType = get<String?>("surchargeType") ?: CardSurchargeType.PERCENT,
        surchargeValue = num("surchargeValue"),
        updatedAt = num("updatedAt").toLong(),
        updatedByName = get<String?>("updatedByName") ?: ""
    )
}
