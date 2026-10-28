package com.bakery.erp.domain

import com.bakery.erp.model.RestaurantDish
import com.bakery.erp.model.RestaurantDishBread

/** Platillos del restaurante y el pan que llevan. Ver [RestaurantDish]. */
interface RestaurantDishRepository {
    /** Todos los que han llegado de ComandaYa. */
    suspend fun getDishes(companyId: String): List<RestaurantDish>

    /**
     * Escritura parcial: solo el pan. El nombre y la última vez que llegó los mantiene el
     * servidor, y un `set()` completo los pisaría.
     */
    suspend fun setBreads(companyId: String, externalItemId: String, breads: List<RestaurantDishBread>)
}
