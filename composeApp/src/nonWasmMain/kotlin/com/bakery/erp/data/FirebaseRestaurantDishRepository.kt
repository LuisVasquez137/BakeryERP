package com.bakery.erp.data

import com.bakery.erp.domain.RestaurantDishRepository
import com.bakery.erp.model.RestaurantDish
import com.bakery.erp.model.RestaurantDishBread
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock

class FirebaseRestaurantDishRepository(
    private val firestore: FirebaseFirestore
) : RestaurantDishRepository {

    private fun col(companyId: String) = firestore.collection("companies/$companyId/restaurantDishes")

    override suspend fun getDishes(companyId: String): List<RestaurantDish> =
        col(companyId).get().documents.map { it.toDish() }

    override suspend fun setBreads(companyId: String, externalItemId: String, breads: List<RestaurantDishBread>) {
        val kept = breads.filter { it.productId.isNotEmpty() && it.quantity > 0.0 }
        // update: el servidor ya creó el documento al llegar el platillo y es dueño de
        // dishName/lastSeenAt; aquí solo el pan.
        col(companyId).document(externalItemId).update(
            firestoreSafeOf(
                "breadProductIds" to kept.map { it.productId },
                "breadProductNames" to kept.map { it.productName },
                "breadQuantities" to kept.map { it.quantity },
                "updatedAt" to Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    private fun DocumentSnapshot.str(field: String): String =
        runCatching { get<String?>(field) }.getOrNull() ?: ""

    private fun DocumentSnapshot.strings(field: String): List<String> =
        runCatching { get<List<String>?>(field) }.getOrNull() ?: emptyList()

    /** El servidor escribe enteros (Long); la app, Double: se aceptan los dos. */
    private fun DocumentSnapshot.numbers(field: String): List<Double> =
        runCatching { get<List<Double>?>(field) }.getOrNull()
            ?: runCatching { get<List<Long>?>(field)?.map { it.toDouble() } }.getOrNull()
            ?: emptyList()

    private fun DocumentSnapshot.millis(field: String): Long =
        runCatching { get<Long?>(field) }.getOrNull()
            ?: runCatching { get<Double?>(field)?.toLong() }.getOrNull() ?: 0L

    private fun DocumentSnapshot.toDish(): RestaurantDish {
        val ids = strings("breadProductIds")
        val names = strings("breadProductNames")
        val qty = numbers("breadQuantities")
        return RestaurantDish(
            externalItemId = id,
            dishName = str("dishName"),
            breads = ids.indices.map { i ->
                RestaurantDishBread(ids[i], names.getOrElse(i) { "" }, qty.getOrElse(i) { 0.0 })
            },
            lastSeenAt = millis("lastSeenAt")
        )
    }
}
