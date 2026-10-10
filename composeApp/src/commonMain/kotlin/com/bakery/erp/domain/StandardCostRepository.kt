package com.bakery.erp.domain

import com.bakery.erp.model.ProductStandardCost
import com.bakery.erp.model.StandardCostPeriod

interface StandardCostRepository {
    suspend fun getPeriod(companyId: String, period: String): StandardCostPeriod?
    suspend fun getCosts(companyId: String, period: String): List<ProductStandardCost>

    /**
     * Congela el costo de [StandardCostPeriod.period]: escribe cada producto, borra los del mes
     * que ya no están (volver a congelar reemplaza, no suma) y deja el encabezado al final, que
     * es lo que marca el mes como congelado. Los despachos ya hechos guardaron su precio y no
     * cambian.
     */
    suspend fun freeze(companyId: String, header: StandardCostPeriod, costs: List<ProductStandardCost>)
}
