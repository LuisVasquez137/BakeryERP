package com.bakery.erp.data

import com.bakery.erp.domain.StandardCostRepository
import com.bakery.erp.model.FrozenPlantRate
import com.bakery.erp.model.ProductStandardCost
import com.bakery.erp.model.StandardCostPeriod
import com.bakery.erp.model.standardCostDocId
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore

/**
 * `standardCostPeriods/{period}` + `standardCosts/{period}_{productId}`. Las plantas del
 * encabezado van como arreglos paralelos de primitivos: GitLive no serializa listas de objetos.
 */
class FirebaseStandardCostRepository(
    private val firestore: FirebaseFirestore
) : StandardCostRepository {

    private fun periodsCol(companyId: String) = firestore.collection("companies/$companyId/standardCostPeriods")
    private fun costsCol(companyId: String) = firestore.collection("companies/$companyId/standardCosts")

    override suspend fun getPeriod(companyId: String, period: String): StandardCostPeriod? {
        val snap = periodsCol(companyId).document(period).get()
        return if (snap.exists) snap.toPeriod() else null
    }

    override suspend fun getCosts(companyId: String, period: String): List<ProductStandardCost> =
        costsCol(companyId).where { "period" equalTo period }.get().documents.map { it.toCost() }

    override suspend fun freeze(companyId: String, header: StandardCostPeriod, costs: List<ProductStandardCost>) {
        val keep = costs.map { standardCostDocId(header.period, it.productId) }.toSet()
        costs.forEach { c ->
            val id = standardCostDocId(header.period, c.productId)
            costsCol(companyId).document(id).set(c.copy(id = id, period = header.period).toMap())
        }
        getCosts(companyId, header.period).filter { it.id !in keep }
            .forEach { costsCol(companyId).document(it.id).delete() }
        periodsCol(companyId).document(header.period).set(header.toMap())
    }

    private fun StandardCostPeriod.toMap(): Map<String, Any?> = firestoreSafeOf(
        "period" to period,
        "sourcePeriod" to sourcePeriod,
        "plantIds" to plants.map { it.branchId },
        "plantNames" to plants.map { it.branchName },
        "plantExpenses" to plants.map { it.expenses },
        "plantPayroll" to plants.map { it.payroll },
        "plantDoughGrams" to plants.map { it.doughGrams },
        "plantRatePerGram" to plants.map { it.ratePerGram },
        "productCount" to productCount,
        "missingCount" to missingCount,
        "frozenAt" to frozenAt,
        "frozenByName" to frozenByName
    )

    private fun DocumentSnapshot.toPeriod(): StandardCostPeriod {
        val ids = strings("plantIds")
        val names = strings("plantNames")
        val exp = numbers("plantExpenses")
        val pay = numbers("plantPayroll")
        val dough = numbers("plantDoughGrams")
        val rate = numbers("plantRatePerGram")
        return StandardCostPeriod(
            period = get<String?>("period") ?: id,
            sourcePeriod = get<String?>("sourcePeriod") ?: "",
            plants = ids.mapIndexed { i, bid ->
                FrozenPlantRate(
                    branchId = bid,
                    branchName = names.getOrElse(i) { "" },
                    expenses = exp.getOrElse(i) { 0.0 },
                    payroll = pay.getOrElse(i) { 0.0 },
                    doughGrams = dough.getOrElse(i) { 0.0 },
                    ratePerGram = rate.getOrElse(i) { 0.0 }
                )
            },
            productCount = number("productCount").toInt(),
            missingCount = number("missingCount").toInt(),
            frozenAt = number("frozenAt").toLong(),
            frozenByName = get<String?>("frozenByName") ?: ""
        )
    }

    private fun ProductStandardCost.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "period" to period,
        "productId" to productId,
        "productName" to productName,
        "plantBranchId" to plantBranchId,
        "plantName" to plantName,
        "doughGrams" to doughGrams,
        "materialCost" to materialCost,
        "overheadCost" to overheadCost,
        "unitCost" to unitCost,
        "frozenAt" to frozenAt
    )

    private fun DocumentSnapshot.toCost() = ProductStandardCost(
        id = id,
        period = get<String?>("period") ?: "",
        productId = get<String?>("productId") ?: "",
        productName = get<String?>("productName") ?: "",
        plantBranchId = get<String?>("plantBranchId") ?: "",
        plantName = get<String?>("plantName") ?: "",
        doughGrams = number("doughGrams"),
        materialCost = number("materialCost"),
        overheadCost = number("overheadCost"),
        unitCost = number("unitCost"),
        frozenAt = number("frozenAt").toLong()
    )

    // Desde la web un número entero vuelve como Long; desde Android, como Double.
    private fun DocumentSnapshot.number(field: String): Double =
        runCatching { get<Double?>(field) }.getOrNull()
            ?: runCatching { get<Long?>(field)?.toDouble() }.getOrNull() ?: 0.0

    private fun DocumentSnapshot.numbers(field: String): List<Double> =
        runCatching { get<List<Double>?>(field) }.getOrNull()
            ?: runCatching { get<List<Long>?>(field)?.map { it.toDouble() } }.getOrNull()
            ?: emptyList()

    private fun DocumentSnapshot.strings(field: String): List<String> =
        runCatching { get<List<String>?>(field) }.getOrNull() ?: emptyList()
}
